package io.datahub.platform.iamprovisioning.infrastructure.persistence;

import io.datahub.platform.iamprovisioning.application.exception.TenantIamProvisioningStateConcurrencyException;
import io.datahub.platform.iamprovisioning.domain.model.IamProvisioningStatus;
import io.datahub.platform.iamprovisioning.domain.model.TenantIamProvisioningCheckpoint;
import io.datahub.platform.iamprovisioning.domain.model.TenantIamProvisioningState;
import io.datahub.platform.iamprovisioning.domain.valueobject.CorrelationId;
import io.datahub.platform.iamprovisioning.domain.valueobject.TenantId;
import io.datahub.platform.iamprovisioning.infrastructure.persistence.mapper.TenantIamProvisioningStateRowMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

@Testcontainers
@SpringBootTest(properties = {
        "spring.datasource.hikari.maximum-pool-size=5",
        "cdp.keycloak.adapter.real.enabled=false",
        "cdp.kafka.consumer.enabled=false",
        "cdp.kafka.producer.enabled=false",
        "cdp.vault.adapter.real.enabled=false",
        "cdp.persistence.type=postgresql"           // 使用真实 PostgreSQL
})
class PostgreTenantIamProvisioningRepositoryIT {

    @Autowired
    private PostgreTenantIamProvisioningRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager  transactionManager;

    private TenantIamProvisioningStateRowMapper mapper = new TenantIamProvisioningStateRowMapper();


    @Container
    static PostgreSQLContainer<?> postgreSQLContainer =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("iam_provisioning_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgreSQLContainer::getJdbcUrl);
        registry.add("spring.datasource.password", postgreSQLContainer::getPassword);
        registry.add("spring.datasource.username", postgreSQLContainer::getUsername);
    }

    @BeforeEach
    void setUp() {
    }

    @Test
    @DisplayName("findOrInitById: 首次调用, 应该创建 IAM_PENDING record")
    void findOrInitById_first_call_shouldCreatesPendingRecord() {
        TenantId tenantId = uniqueTenantId();
        CorrelationId correlationId = uniqueCorrelationId();
        TenantIamProvisioningState state = repository.findOrInitById(tenantId, correlationId);

        assertThat(state).isNotNull();

        assertThat(state.getOverallStatus()).isEqualTo(IamProvisioningStatus.IAM_PENDING);

        assertThat(state.getTenantId()).isEqualTo(tenantId);
        assertThat(state.getWorkflowCorrelationId()).isEqualTo(correlationId);

        assertThat(state.getRetryCount()).isEqualTo(0);
        assertThat(state.getVersion()).isEqualTo(0);

        assertThat(state.getNextRetryAt()).isNull();

    }


    @Test
    @DisplayName("findOrInitById: 重复调用, 应该返回相同的 IAM_PENDING record")
    void findOrInitById_second_call_shouldReturnsTheSamePendingRecord() {
        TenantId tenantId = uniqueTenantId();
        CorrelationId correlationId = uniqueCorrelationId();
        TenantIamProvisioningState state1 = repository.findOrInitById(tenantId, correlationId);


        // 修改 IAM status
        state1.markInProgress(Instant.now());
        repository.save(state1);

        TenantIamProvisioningState state2 = repository.findOrInitById(tenantId, correlationId);

        assertThat(state1.getTenantId()).isEqualTo(state2.getTenantId());
        assertThat(state1.getWorkflowCorrelationId()).isEqualTo(state2.getWorkflowCorrelationId());
        assertThat(state1.getRetryCount()).isEqualTo(state2.getRetryCount());

        assertThat(state2.getOverallStatus()).isEqualTo(IamProvisioningStatus.IAM_IN_PROGRESS);

    }

    @Test
    @DisplayName("findByTenantId: 已存在 record, 应该返回当前 provisioning state")
    void findByTenantId_existingRecord_shouldReturnCurrentState() {
        TenantId tenantId = uniqueTenantId();
        CorrelationId correlationId = uniqueCorrelationId();
        repository.findOrInitById(tenantId, correlationId);

        assertThat(repository.findByTenantId(tenantId))
                .isPresent()
                .get()
                .satisfies(state -> {
                    assertThat(state.getTenantId()).isEqualTo(tenantId);
                    assertThat(state.getWorkflowCorrelationId()).isEqualTo(correlationId);
                    assertThat(state.getOverallStatus()).isEqualTo(IamProvisioningStatus.IAM_PENDING);
                });
    }


    @Test
    void findReadyForRetry_skip_locked_prevents_double_claim() throws InterruptedException {
        Instant now = Instant.now();

        // 创建 3 条到期的记录
        TenantId id1 = createAwaitingRetryState(now.minusSeconds(60));
        TenantId id2 = createAwaitingRetryState(now.minusSeconds(120));
        TenantId id3 = createAwaitingRetryState(now.minusSeconds(180));

        List<String> podAClaim = new ArrayList<>();
        List<String> podBClaim = new ArrayList<>();

        CountDownLatch podALocked = new CountDownLatch(1);
        CountDownLatch podBDone = new CountDownLatch(1);

        Thread t1 = new Thread(() -> {
            TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
            txTemplate.execute(status -> {

                List<TenantIamProvisioningState> retry = repository.findReadyForRetry(now, 2);
                retry.forEach(state -> podAClaim.add(state.getTenantId().toString()));
                podALocked.countDown();

                try {
                    podBDone.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                return null;
            });
        });

        Thread t2 = new Thread(() -> {
            try {
                podALocked.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
            txTemplate.execute(status -> {

                List<TenantIamProvisioningState> retry = repository.findReadyForRetry(now, 2);
                retry.forEach(state -> podBClaim.add(state.getTenantId().value().toString()));
                return null;
            });
            podBDone.countDown();
        });

        t1.start();
        t2.start();
        t1.join(15_000);
        t2.join(15_000);

        assertThat(podAClaim).doesNotContainAnyElementsOf(podBClaim);

        Set<String> set = new HashSet<>();
        set.addAll(podAClaim);
        set.addAll(podBClaim);

        assertThat(set).containsExactlyInAnyOrderElementsOf(set);
    }

    @Test
    void timestamps_survive_roundtrip_without_timezone_drift(){
        TenantId tenantId = uniqueTenantId();
        CorrelationId correlationId = uniqueCorrelationId();
        TenantIamProvisioningState state = repository.findOrInitById(tenantId, correlationId);

        Instant specificTime = Instant.parse("2026-05-31T14:00:00.00Z");
        state.markInProgress(specificTime);

        repository.save(state);

        TenantIamProvisioningState stateCmp = repository.findOrInitById(tenantId, correlationId);

        assertThat(stateCmp.getLastAttemptAt()).isEqualTo(state.getLastAttemptAt());

    }

    private TenantId createAwaitingRetryState(Instant instant) {
        TenantId tenantId = uniqueTenantId();
        jdbcTemplate.update(
                """
                    INSERT INTO tenant_iam_provisioning_state(tenant_id, workflow_correlation_id, iam_status, next_retry_at)
                    VALUES (?, ?, ?, ?)
                    """,
                tenantId.value(),
                UUID.randomUUID().toString(),
                IamProvisioningStatus.IAM_AWAITING_RETRY.name(),
                Timestamp.from(instant)
        );
        return tenantId;
    }

    @Test
    @DisplayName("findOrInitById: 并发插入同一个 tenantId, 不报错, 最终应该只有 1 条记录")
    void findOrInitById_concurrent_calls_create_exactly_one_record() throws InterruptedException {
        TenantId tenantId = uniqueTenantId();

        int threads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threads);

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threads);

        List<TenantIamProvisioningState> results = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threads; i++) {
            CorrelationId correlationId = CorrelationId.of("correlation-" + i);
            executor.submit(() -> {

                try {
                    startLatch.countDown();
                    TenantIamProvisioningState state = repository.findOrInitById(tenantId, correlationId);
                    results.add(state);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        finishLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();
        // 所有线程都应该成功返回（没有异常）
        assertThat(results).hasSize(threads);


        // 数据库中只有 1 条记录
        int count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tenant_iam_provisioning_state WHERE tenant_id = ?",
                Integer.class, tenantId.value());
        assertThat(count).isEqualTo(1);

        results.forEach(it -> assertThat(it.getTenantId()).isEqualTo(tenantId));




    }


    @Test
    @DisplayName("save:  正常保存, 乐观锁版本应该递增")
    void save_increments_version_on_each_call(){
        TenantId tenantId = uniqueTenantId();
        CorrelationId correlationId = uniqueCorrelationId();
        TenantIamProvisioningState state = repository.findOrInitById(tenantId, correlationId);

        state.markInProgress(Instant.now());
        repository.save(state);

        Optional<TenantIamProvisioningState> state2 = repository.findByTenantId(tenantId);

        assertThat(state2).isPresent();
        assertThat(state2.get().getVersion()).isEqualTo(1);

        state2.get().markOrganizationCreated(Instant.now());
        repository.save(state2.get());

        state2 = repository.findByTenantId(tenantId);
        assertThat(state2).isPresent();
        assertThat(state2.get().getVersion()).isEqualTo(2);
    }

    @Test
    @DisplayName("save: 测试乐观锁")
    void save_detects_optimistic_lock_conflict() {
        TenantId tenantId = uniqueTenantId();

        // 模拟两个"请求"同时加载了同一条记录
        TenantIamProvisioningState loadByPodA =
                repository.findOrInitById(tenantId, CorrelationId.newCorrelationId());
        TenantIamProvisioningState loadByPodB =
                repository.findByTenantId(tenantId).orElseThrow();

        // 两者的 version 相同（都是 0）
        assertThat(loadByPodA.getVersion()).isEqualTo(loadByPodB.getVersion());

        // Pod A 先写成功
        loadByPodA.markInProgress(Instant.now());
        repository.save(loadByPodA);  // version: 0 → 1，成功

        // Pod B 试图用 stale version 写入
        loadByPodB.markInProgress(Instant.now());
        assertThatThrownBy(() -> repository.save(loadByPodB))
                .isInstanceOf(TenantIamProvisioningStateConcurrencyException.class);
    }


    @Test
    void full_provisioning_lifecycle_persisted_correctly() {
        TenantId tenantId = uniqueTenantId();
        TenantIamProvisioningState state =
                repository.findOrInitById(tenantId, CorrelationId.newCorrelationId());

        // PENDING → IN_PROGRESS
        state.markInProgress(Instant.now());
        repository.save(state);

        state = repository.findByTenantId(tenantId).orElseThrow();

        // 完成所有 checkpoint
        state.markStepCompleted(TenantIamProvisioningCheckpoint.ORGANIZATION_CREATED, Instant.now());
        state.markStepCompleted(TenantIamProvisioningCheckpoint.ADMIN_USER_CREATED, Instant.now());
        state.markStepCompleted(TenantIamProvisioningCheckpoint.TENANT_ADMIN_ROLE_ASSIGNED, Instant.now());
        state.markStepCompleted(TenantIamProvisioningCheckpoint.ORGANIZATION_MEMBERSHIP_CREATED, Instant.now());
        repository.save(state);

        state = repository.findByTenantId(tenantId).orElseThrow();

        // IN_PROGRESS → COMPLETED
        state.markCompleted(Instant.now());
        repository.save(state);

        // 从数据库重新加载，验证所有字段
        TenantIamProvisioningState reloaded = repository.findByTenantId(tenantId).orElseThrow();

        assertThat(reloaded.getOverallStatus()).isEqualTo(IamProvisioningStatus.IAM_COMPLETED);
        assertThat(reloaded.isKeycloakOrganizationCreated()).isTrue();
        assertThat(reloaded.isAdminUserCreated()).isTrue();
        assertThat(reloaded.isDefaultRolesAssigned()).isTrue();
        assertThat(reloaded.isAdminUserMembershipCreated()).isTrue();
        assertThat(reloaded.getProvisionedAt()).isNotNull();
        assertThat(reloaded.getRetryCount()).isZero();
    }

    private CorrelationId uniqueCorrelationId() {
        return CorrelationId.newCorrelationId();
    }

    private TenantId uniqueTenantId() {
        return TenantId.of("TENANT-" + UUID.randomUUID().toString());
    }
}
