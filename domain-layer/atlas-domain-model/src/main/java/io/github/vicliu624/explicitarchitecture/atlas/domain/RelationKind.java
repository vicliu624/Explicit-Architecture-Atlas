package io.github.vicliu624.explicitarchitecture.atlas.domain;

/** Atlas 图谱中具备明确业务和技术语义的边类型。 */
public enum RelationKind {
    REPRESENTS,
    CONTAINS,
    DEPENDS_ON,
    EXPOSES_TO,
    CONSTRAINS,
    CALLS,
    HANDLES,
    PRODUCES,
    CONSUMES,
    PUBLISHES,
    PROJECTS_TO,
    WRITES_TO,
    STARTS,
    WAITS_FOR,
    DISPATCHES,
    COMPLETES,
    COMPENSATES,
    EVIDENCED_BY
}
