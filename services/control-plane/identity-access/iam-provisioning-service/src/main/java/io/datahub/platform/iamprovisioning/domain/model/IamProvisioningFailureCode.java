package io.datahub.platform.iamprovisioning.domain.model;

// 失败原因必须枚举化，禁止自由文本
// 原因：自由文本无法在监控告警中做条件过滤，枚举可以
public enum IamProvisioningFailureCode{
    KEYCLOAK_UNAVAILABLE,  // Keycloak 连接超时或不可用
    KEYCLOAK_API_ERROR,    // Keycloak API 返回错误响应
    ADMIN_USER_EXISTS,     // 该邮箱的用户已存在于 Keycloak 中（冲突）
    UNKNOWN_ERROR          // 未预期的异常
}
