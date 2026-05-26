package io.datahub.platform.iamprovisioning.application.pipeline.step;

//ensureOrganization(...) 已经包含属性校正语义，所以这个 step 暂时不需要。除非未来把“创建
//  organization”和“同步 organization attributes”拆成两个明确的 Keycloak API，否则可以删掉或先不纳入 pipeline。
public class EnsureOrganizationAttributesStep {
}
