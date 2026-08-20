package io.github.vicliu624.explicitarchitecture.atlas.annotation;

/**
 * 注解所声明的 Atlas 资产类别。
 *
 * <p>它独立于核心领域模型，确保业务工程只引入注解 API 时不携带 Atlas Server、Spring、Axon 或分析器依赖。</p>
 */
public enum AtlasIntentKind {
    BUSINESS_CONCEPT,
    DOMAIN_CONSTRAINT,
    DOMAIN_EVENT,
    APPLICATION_USE_CASE,
    PROJECTION,
    PROCESS_MANAGER,
    ENDPOINT_OPERATION,
    CODE_ELEMENT,
    DATA_TARGET,
    EXTERNAL_BOUNDARY,
    SEMANTIC_VALUE,
    OPERATION_CONTRACT,
    COMPONENT_DEFINITION,
    INJECTION_POINT,
    CONFIGURATION_PROPERTY,
    CONFIGURATION_SCENARIO
}
