package io.datahub.platform.iamprovisioning.infrastructure.messaging;

import io.datahub.platform.iamprovisioning.application.port.out.repository.OutBoxEvent;
import io.datahub.platform.iamprovisioning.infrastructure.persistence.OutboxEventRow;
import io.datahub.platform.iamprovisioning.infrastructure.persistence.PostgreOutboxRepository;
import io.datahub.platform.iamprovisioning.infrastructure.persistence.mapper.OutboxEventDomainMapper;
import io.datahub.platform.iamprovisioning.infrastructure.persistence.mapper.OutboxEventRowMapper;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

// notion: https://www.notion.so/KafkaOutBoxPublisherIT-372cb22c4335804d993adcfefff760da?source=copy_link
@Testcontainers
class KafkaOutBoxPublisherIT {

    @Container
    static PostgreSQLContainer<?> postgreSQLContainer =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("iam_provisioning_outbox_publisher_test")
                    .withUsername("test")
                    .withPassword("test");

    @Container
    static KafkaContainer kafkaContainer =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.7.1"));

    private static JdbcTemplate jdbcTemplate;
    private static DefaultKafkaProducerFactory<String, String> producerFactory;
    private static KafkaTemplate<String, String> kafkaTemplate;

    private final OutboxEventRowMapper rowMapper = new OutboxEventRowMapper();
    private final OutboxEventDomainMapper domainMapper = new OutboxEventDomainMapper();

    private PostgreOutboxRepository repository;
    private KafkaOutBoxPublisher publisher;

    @BeforeAll
    static void startInfrastructure() {
        DataSource dataSource = new DriverManagerDataSource(
                postgreSQLContainer.getJdbcUrl(),
                postgreSQLContainer.getUsername(),
                postgreSQLContainer.getPassword()
        );

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        jdbcTemplate = new JdbcTemplate(dataSource);

        producerFactory = new DefaultKafkaProducerFactory<>(producerProperties());
        kafkaTemplate = new KafkaTemplate<>(producerFactory);
    }

    @AfterAll
    static void closeKafkaProducer() {
        if (producerFactory != null) {
            producerFactory.destroy();
        }
    }

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("TRUNCATE TABLE outbox_events");
        repository = new PostgreOutboxRepository(jdbcTemplate);
        publisher = new KafkaOutBoxPublisher(repository, kafkaTemplate);
    }

    @Test
    void pollAndPublish_publishes_pending_outbox_event_to_kafka_and_marks_database_row_published() throws Exception {
        String topic = "cdp.iam.tenant.provisioned.it." + UUID.randomUUID();

        // AdminClient 创建 topic
        createTopic(topic);
        OutBoxEvent event = pendingEvent(topic, "tenant-alpha");
        repository.appendAll(List.of(event));
        OutBoxEvent persisted = loadByAggregateId("tenant-alpha");

        publisher.pollAndPublish();

        ConsumerRecord<String, String> kafkaRecord = consumeOne(topic);
        OutBoxEvent saved = load(persisted.eventId());

        assertThat(kafkaRecord.topic()).isEqualTo(topic);
        assertThat(kafkaRecord.key()).isEqualTo("tenant-alpha");
        assertThat(kafkaRecord.value()).isEqualTo("{\"tenantId\":\"tenant-alpha\"}");
        assertHeader(kafkaRecord, "event_id", persisted.eventId().toString());
        assertHeader(kafkaRecord, "event_type", "TenantIamProvisionedEvent");
        assertHeader(kafkaRecord, "correlation_id", "correlation-tenant-alpha");
        assertHeader(kafkaRecord, "causation_id", "causation-tenant-alpha");

        assertThat(saved.status()).isEqualTo(OutBoxEvent.Status.PUBLISHED);
        assertThat(saved.publishedAt()).isNotNull();
        assertThat(saved.nextRetryAt()).isNull();
        assertThat(saved.lastError()).isNull();
    }




    private static void createTopic(String topic) throws Exception {
        // 连接 testcontainer 中的 kafka
        // 创建 client
        try (AdminClient adminClient = AdminClient.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers()
        ))) {

            // client 创建 topic
            adminClient.createTopics(List.of(new NewTopic(topic, 1, (short) 1)))
                    .all()
                    .get(10, TimeUnit.SECONDS);
        }
    }

    // 消费 topic 中的 一条消息, 确认 topic 中的确保存了 1 条消息, 否则 fail
    private static ConsumerRecord<String, String> consumeOne(String topic) {
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProperties())) {
            consumer.subscribe(List.of(topic));

            Instant deadline = Instant.now().plusSeconds(10);
            while (Instant.now().isBefore(deadline)) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(250))) {
                    if (topic.equals(record.topic())) {
                        return record;
                    }
                }
            }
        }

        fail("Expected one Kafka record on topic %s".formatted(topic));
        throw new AssertionError("unreachable");
    }

    private static Map<String, Object> producerProperties() {
        Map<String, Object> properties = new HashMap<>();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers());
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.ACKS_CONFIG, "all");
        return properties;
    }

    private static Properties consumerProperties() {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "outbox-publisher-it-" + UUID.randomUUID());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return properties;
    }

    private OutBoxEvent load(UUID eventId) {
        OutboxEventRow row = jdbcTemplate.queryForObject(
                "SELECT * FROM outbox_events WHERE event_id = ?",
                rowMapper,
                eventId
        );
        return domainMapper.toDomain(row);
    }

    private OutBoxEvent loadByAggregateId(String aggregateId) {
        OutboxEventRow row = jdbcTemplate.queryForObject(
                "SELECT * FROM outbox_events WHERE aggregate_id = ?",
                rowMapper,
                aggregateId
        );
        return domainMapper.toDomain(row);
    }

    private static void assertHeader(ConsumerRecord<String, String> record, String name, String expectedValue) {
        Header header = record.headers().lastHeader(name);
        assertThat(header).as(name).isNotNull();
        assertThat(new String(header.value(), StandardCharsets.UTF_8)).isEqualTo(expectedValue);
    }

    private static OutBoxEvent pendingEvent(String topic, String tenantId) {
        return OutBoxEvent.pending(
                "TenantIamProvisioningState",
                tenantId,
                "TenantIamProvisionedEvent",
                1,
                topic,
                "{\"tenantId\":\"%s\"}".formatted(tenantId),
                "{\"contentType\":\"application/json\"}",
                "correlation-" + tenantId,
                "causation-" + tenantId,
                Instant.parse("2026-06-01T10:15:30Z"),
                Instant.parse("2026-06-01T10:15:31Z")
        );
    }
}
