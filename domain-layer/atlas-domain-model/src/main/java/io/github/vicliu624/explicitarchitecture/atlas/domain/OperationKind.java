package io.github.vicliu624.explicitarchitecture.atlas.domain;

/** 可被解释和追踪的执行入口类型。 */
public enum OperationKind {
    METHOD,
    CONSTRUCTOR,
    HTTP_ENDPOINT,
    MESSAGE_CONSUMER,
    EVENT_HANDLER,
    COMMAND_HANDLER,
    QUERY_HANDLER,
    SCHEDULED_TASK,
    LAMBDA,
    FRAMEWORK_CALLBACK,
    FACTORY_METHOD,
    CUSTOM
}
