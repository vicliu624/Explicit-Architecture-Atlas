package io.github.vicliu624.explicitarchitecture.atlas.source.jpa;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ParameterizedTypeTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePathScanner;
import io.github.vicliu624.explicitarchitecture.atlas.analysis.spi.AnalysisRequest;
import io.github.vicliu624.explicitarchitecture.atlas.analysis.spi.AnalyzerContribution;
import io.github.vicliu624.explicitarchitecture.atlas.analysis.spi.AnalyzerDescriptor;
import io.github.vicliu624.explicitarchitecture.atlas.analysis.spi.SourceFactsAnalyzer;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasDiagnostic;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasId;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasValue;
import io.github.vicliu624.explicitarchitecture.atlas.domain.CapabilityStatus;
import io.github.vicliu624.explicitarchitecture.atlas.domain.CoverageReport;
import io.github.vicliu624.explicitarchitecture.atlas.domain.FactKind;
import io.github.vicliu624.explicitarchitecture.atlas.domain.SourceFact;
import io.github.vicliu624.explicitarchitecture.atlas.source.jvm.JavaSourceAnalysis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 提取 JPA 实体、显式物理映射与投影写入目标的静态事实。
 *
 * <p>该适配器只读取 Java 源码语法树：它不连接数据库、不启动 Spring，也不依赖业务工程运行时 classpath。
 * 只有源码中明确出现的 {@code @Entity}、{@code @Table}、{@code @Column}、Spring Data Repository 泛型和
 * {@code save}/{@code persist}/{@code merge} 调用才会进入事实包。特别地，省略 {@code @Table} 时 Atlas 只展示
 * 已证实的持久化实体，绝不会按照命名策略臆造物理表名。</p>
 */
public final class JpaProjectionTargetResolver implements SourceFactsAnalyzer {

    private static final AnalyzerDescriptor DESCRIPTOR = new AnalyzerDescriptor(
            AtlasId.of("analyzer:jpa-target-resolver"), "JPA 目标解析器", "投影、实体、表与列写入目标事实");
    private static final Set<String> REPOSITORY_TYPES = Set.of(
            "JpaRepository", "CrudRepository", "ListCrudRepository", "PagingAndSortingRepository",
            "ListPagingAndSortingRepository");
    private static final Set<String> REPOSITORY_WRITE_METHODS = Set.of("save", "saveAll", "saveAndFlush", "delete", "deleteById");
    private static final Set<String> ENTITY_MANAGER_WRITE_METHODS = Set.of("persist", "merge", "remove");

    @Override
    public AnalyzerDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public AnalyzerContribution analyze(AnalysisRequest request) {
        Objects.requireNonNull(request, "request 不能为空");
        List<SourceFact> facts = new ArrayList<>();
        JpaModel model = new JpaModel();

        JavaSourceAnalysis.ParseOutcome mappingOutcome = JavaSourceAnalysis.inspectProductionSources(
                request,
                DESCRIPTOR.id().value(),
                unit -> new JpaMappingScanner(unit, model, facts).scan(unit.compilationUnit(), null));
        JavaSourceAnalysis.ParseOutcome writeOutcome = mappingOutcome.compilerAvailable()
                ? JavaSourceAnalysis.inspectProductionSources(
                request,
                DESCRIPTOR.id().value(),
                unit -> new ProjectionWriteScanner(unit, model, facts).scan(unit.compilationUnit(), null))
                : new JavaSourceAnalysis.ParseOutcome(mappingOutcome.sourceFileCount(), false, List.of());

        List<AtlasDiagnostic> diagnostics = Stream.concat(mappingOutcome.diagnostics().stream(), writeOutcome.diagnostics().stream())
                .collect(java.util.stream.Collectors.toMap(
                        AtlasDiagnostic::id,
                        diagnostic -> diagnostic,
                        (first, ignored) -> first,
                        LinkedHashMap::new))
                .values().stream()
                .sorted(Comparator.comparing(AtlasDiagnostic::id))
                .toList();
        CapabilityStatus status = statusFor(mappingOutcome, writeOutcome, facts);
        String summary = summaryFor(status, facts);
        return new AnalyzerContribution(
                new CoverageReport(DESCRIPTOR.id().value(), status, request.scopeUri(), summary),
                facts.stream().sorted(Comparator.comparing(SourceFact::id)).toList(),
                List.of(),
                diagnostics);
    }

    private static CapabilityStatus statusFor(
            JavaSourceAnalysis.ParseOutcome mappingOutcome,
            JavaSourceAnalysis.ParseOutcome writeOutcome,
            List<SourceFact> facts) {
        if (!mappingOutcome.compilerAvailable() || !writeOutcome.compilerAvailable()) {
            return CapabilityStatus.FAILED;
        }
        if (facts.isEmpty()) {
            return CapabilityStatus.NOT_APPLICABLE;
        }
        return mappingOutcome.hasErrors() || writeOutcome.hasErrors() ? CapabilityStatus.PARTIAL : CapabilityStatus.COMPLETE;
    }

    private static String summaryFor(CapabilityStatus status, List<SourceFact> facts) {
        return switch (status) {
            case COMPLETE -> "已提取 " + count(facts, FactKind.PERSISTENCE_ENTITY) + " 个持久化实体、"
                    + count(facts, FactKind.PERSISTENCE_TABLE) + " 个显式表映射、"
                    + count(facts, FactKind.PERSISTENCE_COLUMN) + " 个字段映射和 "
                    + count(facts, FactKind.PROJECTION_WRITE) + " 个投影写入点";
            case NOT_APPLICABLE -> "项目范围内没有可证明的 JPA 实体、Spring Data Repository 或投影写入调用";
            case PARTIAL -> "已部分提取 JPA 投影目标；请查看解析诊断";
            case FAILED -> "当前 JVM 未提供 JavaCompiler，无法安全解析 JPA 源码";
            default -> throw new IllegalStateException("JPA 分析器不应产生状态: " + status);
        };
    }

    private static long count(List<SourceFact> facts, FactKind kind) {
        return facts.stream().filter(fact -> fact.kind() == kind).count();
    }

    private static final class JpaMappingScanner extends TreePathScanner<Void, Void> {

        private final JavaSourceAnalysis.JavaSourceUnit unit;
        private final JpaModel model;
        private final List<SourceFact> facts;
        private final List<String> typeStack = new ArrayList<>();
        private final List<EntityDescriptor> entityStack = new ArrayList<>();
        private int methodDepth;

        private JpaMappingScanner(JavaSourceAnalysis.JavaSourceUnit unit, JpaModel model, List<SourceFact> facts) {
            this.unit = unit;
            this.model = model;
            this.facts = facts;
        }

        @Override
        public Void visitClass(ClassTree node, Void ignored) {
            typeStack.add(node.getSimpleName().toString());
            try {
                String typeName = qualifiedTypeName();
                EntityDescriptor entity = entityOf(node, typeName).orElse(null);
                entityStack.add(entity);
                if (entity != null) {
                    model.entities.putIfAbsent(entity.entityType(), entity);
                    emitEntityFacts(node, entity);
                }
                repositoryOf(node, typeName).ifPresent(repository -> {
                    model.repositories.putIfAbsent(repository.repositoryType(), repository);
                    facts.add(unit.fact(
                            FactKind.REPOSITORY,
                            repository.repositoryType(),
                            Map.of(
                                    "repositoryType", AtlasValue.text(repository.repositoryType()),
                                    "repositoryKind", AtlasValue.text(repository.repositoryKind()),
                                    "entityType", AtlasValue.text(repository.entityType())),
                            node,
                            "Spring Data Repository 的实体类型绑定"));
                });
                return super.visitClass(node, ignored);
            } finally {
                entityStack.removeLast();
                typeStack.removeLast();
            }
        }

        @Override
        public Void visitMethod(MethodTree node, Void ignored) {
            methodDepth++;
            try {
                return super.visitMethod(node, ignored);
            } finally {
                methodDepth--;
            }
        }

        @Override
        public Void visitVariable(VariableTree node, Void ignored) {
            EntityDescriptor entity = currentEntity();
            if (methodDepth == 0 && entity != null && !isJpaAnnotation(node.getModifiers().getAnnotations(), "Transient")) {
                emitColumnFact(node, entity);
            }
            return super.visitVariable(node, ignored);
        }

        private void emitEntityFacts(ClassTree node, EntityDescriptor entity) {
            Map<String, AtlasValue> attributes = new LinkedHashMap<>();
            attributes.put("entityType", AtlasValue.text(entity.entityType()));
            attributes.put("entityName", AtlasValue.text(entity.entityName()));
            attributes.put("hasExplicitTable", AtlasValue.bool(entity.table().isPresent()));
            facts.add(unit.fact(
                    FactKind.PERSISTENCE_ENTITY,
                    entity.entityType(),
                    attributes,
                    node,
                    "由 JPA @Entity 明确声明的持久化实体"));
            entity.table().ifPresent(table -> {
                Map<String, AtlasValue> tableAttributes = new LinkedHashMap<>();
                tableAttributes.put("entityType", AtlasValue.text(entity.entityType()));
                tableAttributes.put("tableName", AtlasValue.text(table.name()));
                table.schema().ifPresent(schema -> tableAttributes.put("schema", AtlasValue.text(schema)));
                facts.add(unit.fact(
                        FactKind.PERSISTENCE_TABLE,
                        entity.entityType() + " -> " + table.qualifiedName(),
                        tableAttributes,
                        table.annotation(),
                        "由 JPA @Table 显式声明的物理表映射"));
            });
        }

        private void emitColumnFact(VariableTree node, EntityDescriptor entity) {
            List<? extends AnnotationTree> annotations = node.getModifiers().getAnnotations();
            AnnotationTree column = findJpaAnnotation(annotations, "Column").orElse(null);
            boolean identity = isJpaAnnotation(annotations, "Id") || isJpaAnnotation(annotations, "EmbeddedId");
            if (column == null && !identity) {
                return;
            }
            Map<String, AtlasValue> attributes = new LinkedHashMap<>();
            attributes.put("entityType", AtlasValue.text(entity.entityType()));
            attributes.put("propertyName", AtlasValue.text(node.getName().toString()));
            attributes.put("propertyType", AtlasValue.text(unit.normalizeType(node.getType())));
            attributes.put("identity", AtlasValue.bool(identity));
            if (column != null) {
                annotationString(column, "name").ifPresent(name -> attributes.put("columnName", AtlasValue.text(name)));
            }
            facts.add(unit.fact(
                    FactKind.PERSISTENCE_COLUMN,
                    entity.entityType() + "#" + node.getName(),
                    attributes,
                    column == null ? node : column,
                    column == null
                            ? "由 JPA 标识注解声明的实体身份属性；未声明物理列名"
                            : "由 JPA @Column 声明的实体字段映射"));
        }

        private Optional<EntityDescriptor> entityOf(ClassTree node, String typeName) {
            AnnotationTree annotation = findJpaAnnotation(node.getModifiers().getAnnotations(), "Entity").orElse(null);
            if (annotation == null) {
                return Optional.empty();
            }
            String entityName = annotationString(annotation, "name").orElse(unit.simpleName(node));
            Optional<TableDescriptor> table = findJpaAnnotation(node.getModifiers().getAnnotations(), "Table")
                    .flatMap(tableAnnotation -> annotationString(tableAnnotation, "name")
                            .filter(name -> !name.isBlank())
                            .map(name -> new TableDescriptor(
                                    name,
                                    annotationString(tableAnnotation, "schema").filter(schema -> !schema.isBlank()),
                                    tableAnnotation)));
            return Optional.of(new EntityDescriptor(typeName, entityName, table));
        }

        private Optional<RepositoryDescriptor> repositoryOf(ClassTree node, String repositoryType) {
            List<Tree> parents = new ArrayList<>();
            if (node.getExtendsClause() != null) {
                parents.add(node.getExtendsClause());
            }
            parents.addAll(node.getImplementsClause());
            return parents.stream()
                    .filter(ParameterizedTypeTree.class::isInstance)
                    .map(ParameterizedTypeTree.class::cast)
                    .filter(parent -> REPOSITORY_TYPES.contains(unit.simpleName(parent.getType())))
                    .filter(parent -> !parent.getTypeArguments().isEmpty())
                    .findFirst()
                    .map(parent -> new RepositoryDescriptor(
                            repositoryType,
                            unit.normalizeType(parent.getTypeArguments().getFirst()),
                            unit.simpleName(parent.getType())));
        }

        private EntityDescriptor currentEntity() {
            return entityStack.isEmpty() ? null : entityStack.getLast();
        }

        private String qualifiedTypeName() {
            String nesting = String.join("$", typeStack);
            return unit.packageName().isEmpty() ? nesting : unit.packageName() + "." + nesting;
        }

        private boolean isJpaAnnotation(List<? extends AnnotationTree> annotations, String simpleName) {
            return findJpaAnnotation(annotations, simpleName).isPresent();
        }

        private Optional<AnnotationTree> findJpaAnnotation(List<? extends AnnotationTree> annotations, String simpleName) {
            return annotations.stream()
                    .filter(annotation -> isJpaAnnotationType(unit.annotationType(annotation), simpleName))
                    .map(annotation -> (AnnotationTree) annotation)
                    .findFirst();
        }
    }

    private static final class ProjectionWriteScanner extends TreePathScanner<Void, Void> {

        private final JavaSourceAnalysis.JavaSourceUnit unit;
        private final JpaModel model;
        private final List<SourceFact> facts;
        private final List<String> typeStack = new ArrayList<>();
        private final List<Boolean> projectionStack = new ArrayList<>();
        private final Map<String, String> fieldTypes = new LinkedHashMap<>();
        private String currentMethodSubject;

        private ProjectionWriteScanner(JavaSourceAnalysis.JavaSourceUnit unit, JpaModel model, List<SourceFact> facts) {
            this.unit = unit;
            this.model = model;
            this.facts = facts;
        }

        @Override
        public Void visitClass(ClassTree node, Void ignored) {
            typeStack.add(node.getSimpleName().toString());
            projectionStack.add(hasAtlasProjection(node.getModifiers().getAnnotations()));
            try {
                return super.visitClass(node, ignored);
            } finally {
                projectionStack.removeLast();
                typeStack.removeLast();
            }
        }

        @Override
        public Void visitVariable(VariableTree node, Void ignored) {
            if (currentMethodSubject == null && !typeStack.isEmpty()) {
                fieldTypes.put(node.getName().toString(), unit.normalizeType(node.getType()));
            }
            return super.visitVariable(node, ignored);
        }

        @Override
        public Void visitMethod(MethodTree node, Void ignored) {
            String previousMethod = currentMethodSubject;
            currentMethodSubject = qualifiedTypeName() + "#" + methodDescriptor(node);
            try {
                return super.visitMethod(node, ignored);
            } finally {
                currentMethodSubject = previousMethod;
            }
        }

        @Override
        public Void visitMethodInvocation(MethodInvocationTree node, Void ignored) {
            if (isProjection() && currentMethodSubject != null) {
                projectionWrite(node).ifPresent(write -> facts.add(unit.fact(
                        FactKind.PROJECTION_WRITE,
                        currentMethodSubject + " writes " + write.entityType(),
                        write.attributes(currentMethodSubject, qualifiedTypeName()),
                        node,
                        write.summary())));
            }
            return super.visitMethodInvocation(node, ignored);
        }

        private Optional<ProjectionWrite> projectionWrite(MethodInvocationTree invocation) {
            if (!(invocation.getMethodSelect() instanceof MemberSelectTree select)) {
                return Optional.empty();
            }
            String methodName = select.getIdentifier().toString();
            String receiver = select.getExpression().toString();
            String receiverType = receiverType(select.getExpression()).orElse("");
            RepositoryDescriptor repository = model.repositories.get(receiverType);
            if (repository != null && REPOSITORY_WRITE_METHODS.contains(methodName)) {
                return Optional.of(ProjectionWrite.repository(repository, methodName, receiver));
            }
            if (!isEntityManager(receiverType) || !ENTITY_MANAGER_WRITE_METHODS.contains(methodName)
                    || invocation.getArguments().isEmpty()
                    || !(invocation.getArguments().getFirst() instanceof NewClassTree entity)) {
                return Optional.empty();
            }
            String entityType = unit.normalizeType(entity.getIdentifier());
            if (!model.entities.containsKey(entityType)) {
                return Optional.empty();
            }
            return Optional.of(ProjectionWrite.entityManager(entityType, methodName, receiver, receiverType));
        }

        private Optional<String> receiverType(ExpressionTree expression) {
            if (expression instanceof IdentifierTree identifier) {
                return Optional.ofNullable(fieldTypes.get(identifier.getName().toString()));
            }
            if (expression instanceof MemberSelectTree memberSelect) {
                return Optional.ofNullable(fieldTypes.get(memberSelect.getIdentifier().toString()));
            }
            return Optional.empty();
        }

        private boolean isProjection() {
            return !projectionStack.isEmpty() && projectionStack.getLast();
        }

        private boolean hasAtlasProjection(List<? extends AnnotationTree> annotations) {
            return annotations.stream().map(unit::annotationType).anyMatch(annotation -> annotation.equals(
                    "io.github.vicliu624.explicitarchitecture.atlas.annotation.AtlasProjection")
                    || annotation.equals("AtlasProjection"));
        }

        private String qualifiedTypeName() {
            String nesting = String.join("$", typeStack);
            return unit.packageName().isEmpty() ? nesting : unit.packageName() + "." + nesting;
        }

        private String methodDescriptor(MethodTree node) {
            String methodName = node.getName().contentEquals("<init>") ? "<init>" : node.getName().toString();
            String parameters = node.getParameters().stream()
                    .map(VariableTree::getType)
                    .map(unit::normalizeType)
                    .reduce((left, right) -> left + "," + right)
                    .orElse("");
            return methodName + "(" + parameters + ")";
        }
    }

    private record JpaModel(
            Map<String, EntityDescriptor> entities,
            Map<String, RepositoryDescriptor> repositories) {
        private JpaModel() {
            this(new LinkedHashMap<>(), new LinkedHashMap<>());
        }
    }

    private record EntityDescriptor(String entityType, String entityName, Optional<TableDescriptor> table) {
        private EntityDescriptor {
            Objects.requireNonNull(entityType, "entityType 不能为空");
            Objects.requireNonNull(entityName, "entityName 不能为空");
            table = Objects.requireNonNull(table, "table 不能为空");
        }
    }

    private record TableDescriptor(String name, Optional<String> schema, AnnotationTree annotation) {
        private TableDescriptor {
            Objects.requireNonNull(name, "name 不能为空");
            schema = Objects.requireNonNull(schema, "schema 不能为空");
            Objects.requireNonNull(annotation, "annotation 不能为空");
        }

        private String qualifiedName() {
            return schema.map(value -> value + "." + name).orElse(name);
        }
    }

    private record RepositoryDescriptor(String repositoryType, String entityType, String repositoryKind) {
        private RepositoryDescriptor {
            Objects.requireNonNull(repositoryType, "repositoryType 不能为空");
            Objects.requireNonNull(entityType, "entityType 不能为空");
            Objects.requireNonNull(repositoryKind, "repositoryKind 不能为空");
        }
    }

    private record ProjectionWrite(
            String entityType,
            String writeKind,
            String writeMethod,
            String receiver,
            Optional<String> repositoryType,
            Optional<String> entityManagerType) {

        private ProjectionWrite {
            Objects.requireNonNull(entityType, "entityType 不能为空");
            Objects.requireNonNull(writeKind, "writeKind 不能为空");
            Objects.requireNonNull(writeMethod, "writeMethod 不能为空");
            Objects.requireNonNull(receiver, "receiver 不能为空");
            repositoryType = Objects.requireNonNull(repositoryType, "repositoryType 不能为空");
            entityManagerType = Objects.requireNonNull(entityManagerType, "entityManagerType 不能为空");
        }

        static ProjectionWrite repository(RepositoryDescriptor repository, String writeMethod, String receiver) {
            return new ProjectionWrite(repository.entityType(), "SPRING_DATA_REPOSITORY", writeMethod, receiver,
                    Optional.of(repository.repositoryType()), Optional.empty());
        }

        static ProjectionWrite entityManager(String entityType, String writeMethod, String receiver, String entityManagerType) {
            return new ProjectionWrite(entityType, "JPA_ENTITY_MANAGER", writeMethod, receiver,
                    Optional.empty(), Optional.of(entityManagerType));
        }

        Map<String, AtlasValue> attributes(String projectionHandler, String projectionType) {
            Map<String, AtlasValue> attributes = new LinkedHashMap<>();
            attributes.put("projectionHandler", AtlasValue.text(projectionHandler));
            attributes.put("projectionType", AtlasValue.text(projectionType));
            attributes.put("entityType", AtlasValue.text(entityType));
            attributes.put("writeKind", AtlasValue.text(writeKind));
            attributes.put("writeMethod", AtlasValue.text(writeMethod));
            attributes.put("receiver", AtlasValue.text(receiver));
            repositoryType.ifPresent(type -> attributes.put("repositoryType", AtlasValue.text(type)));
            entityManagerType.ifPresent(type -> attributes.put("entityManagerType", AtlasValue.text(type)));
            return Map.copyOf(attributes);
        }

        String summary() {
            return writeKind.equals("SPRING_DATA_REPOSITORY")
                    ? "投影通过 Spring Data Repository " + writeMethod + " 写入持久化实体"
                    : "投影通过 JPA EntityManager " + writeMethod + " 写入持久化实体";
        }
    }

    private static boolean isJpaAnnotationType(String annotationType, String simpleName) {
        return annotationType.equals("jakarta.persistence." + simpleName)
                || annotationType.equals("javax.persistence." + simpleName);
    }

    private static Optional<String> annotationString(AnnotationTree annotation, String name) {
        for (ExpressionTree argument : annotation.getArguments()) {
            if (argument instanceof AssignmentTree assignment && assignment.getVariable().toString().equals(name)) {
                return stringLiteral(assignment.getExpression());
            }
            if (name.equals("value") && !(argument instanceof AssignmentTree)) {
                return stringLiteral(argument);
            }
        }
        return Optional.empty();
    }

    private static Optional<String> stringLiteral(ExpressionTree expression) {
        if (expression instanceof LiteralTree literal && literal.getValue() instanceof String value) {
            return Optional.of(value);
        }
        return Optional.empty();
    }

    private static boolean isEntityManager(String type) {
        return type.equals("jakarta.persistence.EntityManager") || type.equals("javax.persistence.EntityManager");
    }
}
