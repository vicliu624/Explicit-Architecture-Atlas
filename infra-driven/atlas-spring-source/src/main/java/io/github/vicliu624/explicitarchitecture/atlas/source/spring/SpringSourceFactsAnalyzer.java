package io.github.vicliu624.explicitarchitecture.atlas.source.spring;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewArrayTree;
import com.sun.source.tree.NewClassTree;
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
import io.github.vicliu624.explicitarchitecture.atlas.domain.DiagnosticSeverity;
import io.github.vicliu624.explicitarchitecture.atlas.domain.FactKind;
import io.github.vicliu624.explicitarchitecture.atlas.domain.SourceFact;
import io.github.vicliu624.explicitarchitecture.atlas.source.jvm.JavaSourceAnalysis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.lang.model.element.Modifier;

/**
 * 从 Spring MVC 源码提取可证明的 HTTP 端点和 Command Gateway 派发事实。
 *
 * <p>它只识别源码中实际出现的 Spring mapping 和显式 CommandGateway / CommandBus 调用；不启动 Spring、
 * 不依赖业务工程 classpath，也不会把命名相近的方法猜成 HTTP 接口或命令派发。</p>
 */
public final class SpringSourceFactsAnalyzer implements SourceFactsAnalyzer {

    private static final AnalyzerDescriptor DESCRIPTOR = new AnalyzerDescriptor(
            AtlasId.of("analyzer:spring-source"), "Spring 源码分析器", "Spring MVC Endpoint、调用与命令派发事实");
    private static final Set<String> COMPONENT_ANNOTATIONS = Set.of(
            "Component", "Service", "Repository", "Controller", "RestController", "Configuration");
    private static final Set<String> INJECTION_ANNOTATIONS = Set.of("Autowired", "Inject", "Resource");
    private static final Set<String> ADVISOR_ANNOTATIONS = Set.of(
            "Transactional", "Async", "Retryable", "Cacheable", "CachePut", "CacheEvict", "PreAuthorize", "Secured",
            "Validated", "CircuitBreaker", "RateLimiter");

    @Override
    public AnalyzerDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public AnalyzerContribution analyze(AnalysisRequest request) {
        Objects.requireNonNull(request, "request 不能为空");
        List<SourceFact> facts = new ArrayList<>();
        JavaSourceAnalysis.ParseOutcome outcome = JavaSourceAnalysis.inspectProductionSources(
                request, DESCRIPTOR.id().value(), unit -> new SpringScanner(unit).scan(unit.compilationUnit(), facts));
        CapabilityStatus status = statusFor(outcome, facts);
        String summary = switch (status) {
            case COMPLETE -> "已提取 " + count(facts, FactKind.HTTP_ENDPOINT) + " 个 Spring HTTP 端点和 "
                    + count(facts, FactKind.COMMAND_DISPATCH) + " 个命令派发点";
            case NOT_APPLICABLE -> "项目范围内没有可证明的 Spring MVC 端点或 Command Gateway / Command Bus 派发";
            case PARTIAL -> "已部分提取 Spring 静态事实；请查看解析诊断";
            case FAILED -> "当前 JVM 未提供 JavaCompiler，无法安全解析 Spring 源码";
            default -> throw new IllegalStateException("Spring 分析器不应产生状态: " + status);
        };
        return new AnalyzerContribution(
                new CoverageReport(DESCRIPTOR.id().value(), status, request.scopeUri(), summary),
                facts.stream().sorted(Comparator.comparing(SourceFact::id)).toList(),
                List.of(),
                outcome.diagnostics());
    }

    private static CapabilityStatus statusFor(JavaSourceAnalysis.ParseOutcome outcome, List<SourceFact> facts) {
        if (!outcome.compilerAvailable()) {
            return CapabilityStatus.FAILED;
        }
        if (facts.isEmpty()) {
            return CapabilityStatus.NOT_APPLICABLE;
        }
        return outcome.hasErrors() ? CapabilityStatus.PARTIAL : CapabilityStatus.COMPLETE;
    }

    private static long count(List<SourceFact> facts, FactKind kind) {
        return facts.stream().filter(fact -> fact.kind() == kind).count();
    }

    private static final class SpringScanner extends TreePathScanner<Void, List<SourceFact>> {

        private final JavaSourceAnalysis.JavaSourceUnit unit;
        private final List<String> typeStack = new ArrayList<>();
        private final List<ControllerContext> controllerStack = new ArrayList<>();
        private final List<List<ConditionDetail>> typeConditionStack = new ArrayList<>();
        private final List<Optional<String>> configurationPropertiesPrefixStack = new ArrayList<>();
        private final Map<String, String> fieldTypes = new HashMap<>();
        private final Map<String, ComponentContext> componentsByType = new HashMap<>();
        private String currentMethodSubject;

        private SpringScanner(JavaSourceAnalysis.JavaSourceUnit unit) {
            this.unit = unit;
        }

        @Override
        public Void visitClass(ClassTree node, List<SourceFact> facts) {
            typeStack.add(node.getSimpleName().toString());
            List<? extends AnnotationTree> annotations = node.getModifiers().getAnnotations();
            List<ConditionDetail> effectiveConditions = mergeConditions(enclosingConditions(), conditions(annotations));
            typeConditionStack.add(effectiveConditions);
            Optional<String> configurationPropertiesPrefix = configurationPropertiesPrefix(annotations);
            configurationPropertiesPrefixStack.add(configurationPropertiesPrefix);
            try {
                String typeName = qualifiedTypeName();
                configurationPropertiesPrefix.filter(prefix -> !prefix.isBlank()).ifPresent(prefix -> facts.add(configurationPropertyFact(
                        prefix, "", typeName, typeName, node, "Spring @ConfigurationProperties 类型前缀")));
                componentOf(annotations).ifPresent(component -> {
                    componentsByType.put(typeName, component);
                    facts.add(beanFact(
                            component.beanName(),
                            typeName,
                            typeName,
                            component.kind(),
                            component.scope(),
                            component.primary(),
                            component.qualifiers(),
                            effectiveConditions,
                            node,
                            "Spring stereotype 注解声明的 Bean"));
                    addConditionFacts(facts, component.beanName(), typeName, effectiveConditions, node);
                    addImplicitConstructorInjections(node, component, facts);
                });
                applicationComposition(annotations).ifPresent(composition -> facts.add(unit.fact(
                        FactKind.APPLICATION_COMPOSITION,
                        typeName,
                        Map.of(
                                "entryPointType", AtlasValue.text(typeName),
                                "componentScanPackages", values(composition.componentScanPackages()),
                                "importedTypes", values(composition.importedTypes())),
                        node,
                        "Spring 应用组成根：组件扫描范围与显式 @Import")));
                addAdvisorFacts(facts, typeName, annotations, node);
                boolean controller = hasAnnotation(annotations, "RestController", "Controller");
                List<Mapping> mappings = mappings(annotations);
                controllerStack.add(new ControllerContext(controller, pathsOf(mappings), typeName));
                return super.visitClass(node, facts);
            } finally {
                configurationPropertiesPrefixStack.removeLast();
                typeConditionStack.removeLast();
                controllerStack.removeLast();
                typeStack.removeLast();
            }
        }

        @Override
        public Void visitVariable(VariableTree node, List<SourceFact> facts) {
            if (currentMethodSubject == null && !typeStack.isEmpty()) {
                fieldTypes.put(node.getName().toString(), unit.normalizeType(node.getType()));
                ComponentContext owner = componentsByType.get(qualifiedTypeName());
                List<? extends AnnotationTree> annotations = node.getModifiers().getAnnotations();
                if (owner != null && hasAnyAnnotation(annotations, INJECTION_ANNOTATIONS)) {
                    facts.add(unit.fact(
                            FactKind.BEAN_INJECTION,
                            qualifiedTypeName() + "#" + node.getName(),
                            Map.of(
                                    "ownerType", AtlasValue.text(qualifiedTypeName()),
                                    "ownerBeanName", AtlasValue.text(owner.beanName()),
                                    "memberName", AtlasValue.text(node.getName().toString()),
                                    "requestedType", AtlasValue.text(unit.normalizeType(node.getType())),
                                    "injectionKind", AtlasValue.text("FIELD"),
                                    "cardinality", AtlasValue.text(injectionCardinality(unit.normalizeType(node.getType()))),
                                    "required", AtlasValue.bool(annotationBoolean(annotations, "Autowired", "required").orElse(true)),
                                    "qualifiers", values(injectionQualifiers(annotations))),
                            node,
                            "Spring 字段注入点"));
                }
                configurationValue(node, annotations, facts);
            }
            return super.visitVariable(node, facts);
        }

        @Override
        public Void visitMethod(MethodTree node, List<SourceFact> facts) {
            String previousMethod = currentMethodSubject;
            String descriptor = methodDescriptor(node);
            currentMethodSubject = qualifiedTypeName() + "#" + descriptor;
            try {
                ControllerContext controller = controllerStack.isEmpty() ? ControllerContext.notController() : controllerStack.getLast();
                List<Mapping> mappings = mappings(node.getModifiers().getAnnotations());
                if (controller.isController() && !mappings.isEmpty()) {
                    for (Mapping mapping : mappings) {
                        List<String> paths = combinePaths(controller.basePaths(), mapping.paths());
                        facts.add(unit.fact(
                                FactKind.HTTP_ENDPOINT,
                                currentMethodSubject,
                                Map.of(
                                        "handler", AtlasValue.text(currentMethodSubject),
                                        "controller", AtlasValue.text(controller.typeName()),
                                        "httpMethods", values(mapping.httpMethods()),
                                        "paths", values(paths)),
                                mapping.annotation(),
                                "Spring " + String.join(",", mapping.httpMethods()) + " endpoint 由映射注解提取"));
                    }
                }
                List<? extends AnnotationTree> annotations = node.getModifiers().getAnnotations();
                List<ConditionDetail> effectiveConditions = mergeConditions(enclosingConditions(), conditions(annotations));
                ComponentContext owner = componentsByType.get(qualifiedTypeName());
                if (owner != null && node.getName().contentEquals("<init>") && hasAnyAnnotation(annotations, INJECTION_ANNOTATIONS)) {
                    for (int index = 0; index < node.getParameters().size(); index++) {
                        VariableTree parameter = node.getParameters().get(index);
                        facts.add(unit.fact(
                                FactKind.BEAN_INJECTION,
                                currentMethodSubject + "::parameter[" + index + "]:" + parameter.getName(),
                                Map.of(
                                        "ownerType", AtlasValue.text(qualifiedTypeName()),
                                        "ownerBeanName", AtlasValue.text(owner.beanName()),
                                        "memberName", AtlasValue.text(parameter.getName().toString()),
                                        "requestedType", AtlasValue.text(unit.normalizeType(parameter.getType())),
                                        "injectionKind", AtlasValue.text("CONSTRUCTOR"),
                                        "cardinality", AtlasValue.text(injectionCardinality(unit.normalizeType(parameter.getType()))),
                                        "required", AtlasValue.bool(annotationBoolean(annotations, "Autowired", "required").orElse(true)),
                                        "qualifiers", values(injectionQualifiers(parameter.getModifiers().getAnnotations()))),
                                parameter,
                                "Spring 构造器注入点"));
                    }
                }
                if (hasAnnotation(annotations, "Bean")) {
                    String returnType = unit.normalizeType(node.getReturnType());
                    List<String> names = annotationStrings(annotationNamed(annotations, "Bean").orElseThrow(), "name", "value");
                    String beanName = names.isEmpty() ? node.getName().toString() : names.getFirst();
                    ComponentContext bean = new ComponentContext(beanName, "FACTORY_METHOD", scopeOf(annotations),
                            hasAnnotation(annotations, "Primary"), qualifiers(annotations));
                    facts.add(beanFact(beanName, returnType, returnType, bean.kind(), bean.scope(), bean.primary(), bean.qualifiers(),
                            effectiveConditions, node, "@Bean 工厂方法声明的 Bean（包括配置类条件）"));
                    addConditionFacts(facts, beanName, returnType, effectiveConditions, node);
                    for (int index = 0; index < node.getParameters().size(); index++) {
                        VariableTree parameter = node.getParameters().get(index);
                        facts.add(unit.fact(
                                FactKind.BEAN_INJECTION,
                                currentMethodSubject + "::factory-parameter[" + index + "]:" + parameter.getName(),
                                Map.of(
                                        "ownerType", AtlasValue.text(qualifiedTypeName()),
                                        "ownerBeanName", AtlasValue.text(beanName),
                                        "memberName", AtlasValue.text(parameter.getName().toString()),
                                        "requestedType", AtlasValue.text(unit.normalizeType(parameter.getType())),
                                        "injectionKind", AtlasValue.text("METHOD_PARAMETER"),
                                        "cardinality", AtlasValue.text(injectionCardinality(unit.normalizeType(parameter.getType()))),
                                        "required", AtlasValue.bool(true),
                                        "qualifiers", values(injectionQualifiers(parameter.getModifiers().getAnnotations()))),
                                parameter,
                                "@Bean 工厂方法依赖"));
                    }
                }
                addAdvisorFacts(facts, currentMethodSubject, annotations, node);
                return super.visitMethod(node, facts);
            } finally {
                currentMethodSubject = previousMethod;
            }
        }

        @Override
        public Void visitMethodInvocation(MethodInvocationTree node, List<SourceFact> facts) {
            if (currentMethodSubject != null) {
                commandDispatch(node).ifPresent(dispatch -> facts.add(unit.fact(
                        FactKind.COMMAND_DISPATCH,
                        currentMethodSubject + " -> " + dispatch.commandType(),
                        Map.of(
                                "dispatcher", AtlasValue.text(dispatch.dispatcher()),
                                "dispatchMethod", AtlasValue.text(dispatch.methodName()),
                                "caller", AtlasValue.text(currentMethodSubject),
                                "commandType", AtlasValue.text(dispatch.commandType()),
                                // 对参数/局部变量派发不能把变量名伪装成命令类型。保留表达式，交由图归一化器在
                                // 已证明的 ARGUMENT_BINDS_TO 值流存在时追溯具体命令；否则明确保持未决。
                                "commandExpression", AtlasValue.text(dispatch.commandExpression())),
                        node,
                        "通过 Spring CommandGateway / CommandBus 派发命令")));
            }
            return super.visitMethodInvocation(node, facts);
        }

        private Optional<CommandDispatch> commandDispatch(MethodInvocationTree invocation) {
            if (!(invocation.getMethodSelect() instanceof MemberSelectTree select)) {
                return Optional.empty();
            }
            String methodName = select.getIdentifier().toString();
            if (!List.of("send", "sendAndWait", "dispatch", "sendCommand", "sendCommandAndWaitForAResult").contains(methodName)
                    || invocation.getArguments().isEmpty()) {
                return Optional.empty();
            }
            String receiver = select.getExpression().toString();
            String receiverType = receiverType(select.getExpression()).orElse("");
            if (!receiverType.endsWith("CommandGateway") && !receiverType.endsWith("CommandBus")
                    && !receiverType.contains("CommandGateway")) {
                return Optional.empty();
            }
            ExpressionTree command = invocation.getArguments().getFirst();
            String commandType = command instanceof NewClassTree newCommand
                    ? unit.normalizeType(newCommand.getIdentifier())
                    : fieldTypes.getOrDefault(command.toString(), "<unresolved:" + command + ">");
            return Optional.of(new CommandDispatch(receiver, methodName, commandType, command.toString()));
        }

        private Optional<ComponentContext> componentOf(List<? extends AnnotationTree> annotations) {
            for (AnnotationTree annotation : annotations) {
                String simpleName = unit.annotationSimpleName(annotation);
                if (!COMPONENT_ANNOTATIONS.contains(simpleName)) {
                    continue;
                }
                String typeName = qualifiedTypeName();
                List<String> names = annotationStrings(annotation, "value");
                String beanName = names.isEmpty() ? defaultBeanName(typeName) : names.getFirst();
                return Optional.of(new ComponentContext(beanName, simpleName.toUpperCase(Locale.ROOT), scopeOf(annotations),
                        hasAnnotation(annotations, "Primary"), qualifiers(annotations)));
            }
            return Optional.empty();
        }

        private SourceFact beanFact(
                String beanName,
                String declaredType,
                String candidateType,
                String componentKind,
                String scope,
                boolean primary,
                List<String> qualifiers,
                List<ConditionDetail> conditions,
                Tree node,
                String summary) {
            Map<String, AtlasValue> attributes = new HashMap<>();
            attributes.put("beanName", AtlasValue.text(beanName));
            attributes.put("declaredType", AtlasValue.text(declaredType));
            attributes.put("candidateType", AtlasValue.text(candidateType));
            attributes.put("definitionType", AtlasValue.text(qualifiedTypeName()));
            if (currentMethodSubject != null) {
                attributes.put("factoryMethod", AtlasValue.text(currentMethodSubject));
            }
            attributes.put("componentKind", AtlasValue.text(componentKind));
            attributes.put("scope", AtlasValue.text(scope));
            attributes.put("primary", AtlasValue.bool(primary));
            attributes.put("qualifiers", values(qualifiers));
            attributes.put("activationCondition", AtlasValue.text(conditions.isEmpty() ? "true" : conditions.stream()
                    .map(ConditionDetail::canonical).collect(Collectors.joining(" AND "))));
            attributes.put("conditionStatus", AtlasValue.text(conditions.isEmpty() ? "ACTIVE" : "POSSIBLE"));
            return unit.fact(FactKind.BEAN_DEFINITION, beanName + " : " + candidateType, attributes, node, summary);
        }

        private Optional<ApplicationComposition> applicationComposition(List<? extends AnnotationTree> annotations) {
            Optional<AnnotationTree> application = annotationNamed(annotations, "SpringBootApplication");
            if (application.isEmpty()) {
                return Optional.empty();
            }
            List<String> scanPackages = annotationStrings(application.get(), "scanBasePackages").stream()
                    .filter(value -> !value.endsWith(".class")).toList();
            if (scanPackages.isEmpty()) {
                String typeName = qualifiedTypeName();
                int separator = typeName.lastIndexOf('.');
                scanPackages = separator < 0 ? List.of() : List.of(typeName.substring(0, separator));
            }
            List<String> imported = annotationNamed(annotations, "Import")
                    .map(annotation -> annotationStrings(annotation, "value").stream().map(unit::normalizeTypeText).toList())
                    .orElse(List.of());
            return Optional.of(new ApplicationComposition(scanPackages, imported));
        }

        private void addConditionFacts(
                List<SourceFact> facts,
                String beanName,
                String beanType,
                List<ConditionDetail> conditions,
                Tree node) {
            for (ConditionDetail condition : conditions) {
                facts.add(unit.fact(FactKind.BEAN_CONDITION, beanName + " :: " + condition.canonical(),
                        Map.of(
                                "beanName", AtlasValue.text(beanName),
                                "beanType", AtlasValue.text(beanType),
                                "conditionKind", AtlasValue.text(condition.kind()),
                                "conditionSubject", AtlasValue.text(condition.subject()),
                                "operator", AtlasValue.text(condition.operator()),
                                "expectedValue", AtlasValue.text(condition.expectedValue()),
                                "canonical", AtlasValue.text(condition.canonical())),
                        node,
                        "Spring Bean 激活条件"));
            }
        }

        private List<ConditionDetail> conditions(List<? extends AnnotationTree> annotations) {
            List<ConditionDetail> conditions = new ArrayList<>();
            for (AnnotationTree annotation : annotations) {
                String name = unit.annotationSimpleName(annotation);
                switch (name) {
                    case "Profile" -> {
                        List<String> profiles = annotationStrings(annotation, "value");
                        if (!profiles.isEmpty()) {
                            conditions.add(new ConditionDetail("PROFILE", "activeProfiles", "MATCHES_ANY", String.join("|", profiles)));
                        }
                    }
                    case "ConditionalOnProperty" -> {
                        String prefix = annotationString(annotation, "prefix").map(SpringScanner::trimTrailingDots).orElse("");
                        String expected = annotationString(annotation, "havingValue").orElse("");
                        boolean matchIfMissing = annotationBoolean(annotation, "matchIfMissing").orElse(false);
                        String operator = expected.isBlank() ? "NOT_EQUALS_IGNORE_CASE" : "EQUALS";
                        String expectedValue = expected.isBlank() ? "false" : expected;
                        if (matchIfMissing) {
                            operator += "_OR_MISSING";
                        }
                        String conditionOperator = operator;
                        annotationStrings(annotation, "name", "value").forEach(property ->
                                conditions.add(new ConditionDetail("PROPERTY", prefix.isBlank() ? property : prefix + "." + property,
                                        conditionOperator, expectedValue)));
                    }
                    case "ConditionalOnClass" -> annotationStrings(annotation, "value", "name").forEach(type ->
                            conditions.add(new ConditionDetail("CLASSPATH", type, "PRESENT", "")));
                    case "ConditionalOnMissingClass" -> annotationStrings(annotation, "value", "name").forEach(type ->
                            conditions.add(new ConditionDetail("CLASSPATH", type, "ABSENT", "")));
                    case "ConditionalOnBean" -> annotationStrings(annotation, "value", "type", "name").forEach(type ->
                            conditions.add(new ConditionDetail("BEAN", type, "PRESENT", "")));
                    case "ConditionalOnMissingBean" -> annotationStrings(annotation, "value", "type", "name").forEach(type ->
                            conditions.add(new ConditionDetail("BEAN", type, "ABSENT", "")));
                    case "ConditionalOnSingleCandidate" -> annotationStrings(annotation, "value", "type").forEach(type ->
                            conditions.add(new ConditionDetail("BEAN", type, "SINGLE_CANDIDATE", "")));
                    case "Conditional" -> conditions.add(new ConditionDetail("CUSTOM", annotation.toString(), "MATCHES", ""));
                    default -> {
                    }
                }
            }
            return List.copyOf(conditions);
        }

        private List<ConditionDetail> enclosingConditions() {
            return typeConditionStack.isEmpty() ? List.of() : typeConditionStack.getLast();
        }

        private static List<ConditionDetail> mergeConditions(List<ConditionDetail> inherited, List<ConditionDetail> declared) {
            return Stream.concat(inherited.stream(), declared.stream()).distinct().toList();
        }

        private void addImplicitConstructorInjections(ClassTree node, ComponentContext component, List<SourceFact> facts) {
            List<MethodTree> constructors = node.getMembers().stream().filter(MethodTree.class::isInstance).map(MethodTree.class::cast)
                    .filter(member -> member.getName().contentEquals("<init>")).toList();
            if (constructors.size() != 1 || hasAnyAnnotation(constructors.getFirst().getModifiers().getAnnotations(), INJECTION_ANNOTATIONS)) {
                return;
            }
            MethodTree constructor = constructors.getFirst();
            String subject = qualifiedTypeName() + "#" + methodDescriptor(constructor);
            for (int index = 0; index < constructor.getParameters().size(); index++) {
                VariableTree parameter = constructor.getParameters().get(index);
                List<? extends AnnotationTree> parameterAnnotations = parameter.getModifiers().getAnnotations();
                facts.add(unit.fact(
                        FactKind.BEAN_INJECTION,
                        subject + "::implicit-constructor-parameter[" + index + "]:" + parameter.getName(),
                        Map.of(
                                "ownerType", AtlasValue.text(qualifiedTypeName()),
                                "ownerBeanName", AtlasValue.text(component.beanName()),
                                "memberName", AtlasValue.text(parameter.getName().toString()),
                                "requestedType", AtlasValue.text(unit.normalizeType(parameter.getType())),
                                "injectionKind", AtlasValue.text("CONSTRUCTOR"),
                                "cardinality", AtlasValue.text(injectionCardinality(unit.normalizeType(parameter.getType()))),
                                "required", AtlasValue.bool(true),
                                "qualifiers", values(injectionQualifiers(parameterAnnotations))),
                        parameter,
                        "Spring 单构造器隐式注入点"));
            }
        }

        private void configurationValue(VariableTree node, List<? extends AnnotationTree> annotations, List<SourceFact> facts) {
            annotationNamed(annotations, "Value").flatMap(annotation -> annotationString(annotation, "value")).ifPresent(value -> {
                String placeholder = value.startsWith("${") && value.endsWith("}") ? value.substring(2, value.length() - 1) : value;
                String[] parts = placeholder.split(":", 2);
                facts.add(configurationPropertyFact(parts[0], parts.length == 2 ? parts[1] : "",
                        qualifiedTypeName() + "#" + node.getName(), unit.normalizeType(node.getType()), node, "Spring @Value 配置引用"));
            });
            configurationPropertiesPrefix().filter(prefix -> !node.getModifiers().getFlags().contains(Modifier.STATIC)).ifPresent(prefix -> {
                String propertyKey = prefix.isBlank() ? canonicalPropertySegment(node.getName().toString())
                        : prefix + "." + canonicalPropertySegment(node.getName().toString());
                facts.add(configurationPropertyFact(propertyKey, literalDefault(node), qualifiedTypeName() + "#" + node.getName(),
                        unit.normalizeType(node.getType()), node, "Spring @ConfigurationProperties 字段绑定"));
            });
        }

        private SourceFact configurationPropertyFact(
                String propertyKey,
                String declaredDefault,
                String owner,
                String javaType,
                Tree node,
                String summary) {
            boolean defaultRedacted = sensitiveConfigurationKey(propertyKey) && !declaredDefault.isBlank();
            return unit.fact(FactKind.CONFIGURATION_PROPERTY, propertyKey,
                    Map.of(
                            "propertyKey", AtlasValue.text(propertyKey),
                            "declaredDefault", AtlasValue.text(defaultRedacted ? "" : declaredDefault),
                            "declaredDefaultRedacted", AtlasValue.bool(defaultRedacted),
                            "owner", AtlasValue.text(owner),
                            "javaType", AtlasValue.text(javaType)),
                    node, summary);
        }

        private Optional<String> configurationPropertiesPrefix(List<? extends AnnotationTree> annotations) {
            return annotationNamed(annotations, "ConfigurationProperties")
                    .flatMap(annotation -> annotationString(annotation, "prefix").or(() -> annotationString(annotation, "value")))
                    .map(SpringScanner::trimTrailingDots);
        }

        private Optional<String> configurationPropertiesPrefix() {
            return configurationPropertiesPrefixStack.isEmpty() ? Optional.empty() : configurationPropertiesPrefixStack.getLast();
        }

        private static String canonicalPropertySegment(String fieldName) {
            StringBuilder result = new StringBuilder();
            for (int index = 0; index < fieldName.length(); index++) {
                char character = fieldName.charAt(index);
                if (Character.isUpperCase(character)) {
                    if (index > 0) {
                        result.append('-');
                    }
                    result.append(Character.toLowerCase(character));
                } else if (character == '_') {
                    result.append('-');
                } else {
                    result.append(character);
                }
            }
            return result.toString();
        }

        private static String literalDefault(VariableTree node) {
            ExpressionTree initializer = node.getInitializer();
            if (initializer instanceof LiteralTree literal && literal.getValue() != null) {
                return String.valueOf(literal.getValue());
            }
            return "";
        }

        /** 配置默认值也可能是凭据；源码事实不应成为将其重新打包、经 HTTP 暴露的途径。 */
        private static boolean sensitiveConfigurationKey(String propertyKey) {
            String normalized = propertyKey == null ? "" : propertyKey.toLowerCase(Locale.ROOT).replace('_', '-');
            return normalized.contains("password") || normalized.contains("secret") || normalized.contains("token")
                    || normalized.contains("credential") || normalized.contains("api-key") || normalized.contains("apikey")
                    || normalized.contains("private-key") || normalized.contains("access-key");
        }

        private void addAdvisorFacts(List<SourceFact> facts, String target, List<? extends AnnotationTree> annotations, Tree node) {
            for (AnnotationTree annotation : annotations) {
                String advisor = unit.annotationSimpleName(annotation);
                if (ADVISOR_ANNOTATIONS.contains(advisor)) {
                    facts.add(unit.fact(FactKind.AOP_ADVISOR, target + " :: " + advisor,
                            Map.of("target", AtlasValue.text(target), "advisor", AtlasValue.text(advisor)), node,
                            "Spring/AOP Advisor 声明"));
                }
            }
        }

        private Optional<AnnotationTree> annotationNamed(List<? extends AnnotationTree> annotations, String name) {
            return annotations.stream().filter(annotation -> unit.annotationSimpleName(annotation).equals(name))
                    .<AnnotationTree>map(annotation -> annotation)
                    .findFirst();
        }

        private Optional<String> annotationString(AnnotationTree annotation, String name) {
            return annotationStrings(annotation, name).stream().findFirst();
        }

        private Optional<Boolean> annotationBoolean(
                List<? extends AnnotationTree> annotations,
                String annotationName,
                String attributeName) {
            return annotationNamed(annotations, annotationName).flatMap(annotation -> annotationBoolean(annotation, attributeName));
        }

        private Optional<Boolean> annotationBoolean(AnnotationTree annotation, String attributeName) {
            for (ExpressionTree argument : annotation.getArguments()) {
                if (argument instanceof AssignmentTree assignment
                        && assignment.getVariable().toString().equals(attributeName)
                        && assignment.getExpression() instanceof LiteralTree literal
                        && literal.getValue() instanceof Boolean value) {
                    return Optional.of(value);
                }
            }
            return Optional.empty();
        }

        private boolean hasAnyAnnotation(List<? extends AnnotationTree> annotations, Set<String> names) {
            return annotations.stream().map(unit::annotationSimpleName).anyMatch(names::contains);
        }

        private String scopeOf(List<? extends AnnotationTree> annotations) {
            return annotationNamed(annotations, "Scope").flatMap(annotation -> annotationString(annotation, "value"))
                    .map(value -> value.replace("\"", "").toUpperCase(Locale.ROOT))
                    .orElse("SINGLETON");
        }

        private List<String> qualifiers(List<? extends AnnotationTree> annotations) {
            return annotationNamed(annotations, "Qualifier").map(annotation -> annotationStrings(annotation, "value"))
                    .orElse(List.of());
        }

        private List<String> injectionQualifiers(List<? extends AnnotationTree> annotations) {
            return Stream.of(
                            qualifiers(annotations),
                            annotationNamed(annotations, "Resource").map(annotation -> annotationStrings(annotation, "name")).orElse(List.of()),
                            annotationNamed(annotations, "Named").map(annotation -> annotationStrings(annotation, "value")).orElse(List.of()))
                    .flatMap(List::stream).filter(value -> !value.isBlank()).distinct().sorted().toList();
        }

        private static String trimTrailingDots(String value) {
            String normalized = value == null ? "" : value.trim();
            while (normalized.endsWith(".")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
            return normalized;
        }

        private static String injectionCardinality(String type) {
            if (type.startsWith("java.util.Optional")) {
                return "OPTIONAL";
            }
            if (type.startsWith("java.util.List") || type.startsWith("java.util.Set")) {
                return "COLLECTION";
            }
            if (type.startsWith("java.util.Map")) {
                return "MAP";
            }
            if (type.contains("ObjectProvider") || type.contains("ObjectFactory")) {
                return "PROVIDER";
            }
            return "SINGLE";
        }

        private static String defaultBeanName(String typeName) {
            String simpleName = typeName.substring(typeName.lastIndexOf('.') + 1);
            return simpleName.isEmpty() ? typeName : Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
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

        private boolean hasAnnotation(List<? extends AnnotationTree> annotations, String... names) {
            return Stream.of(names).anyMatch(name -> annotations.stream()
                    .map(unit::annotationSimpleName)
                    .anyMatch(name::equals));
        }

        private List<Mapping> mappings(List<? extends AnnotationTree> annotations) {
            return annotations.stream()
                    .map(this::mapping)
                    .flatMap(Optional::stream)
                    .toList();
        }

        private Optional<Mapping> mapping(AnnotationTree annotation) {
            String simpleName = unit.annotationSimpleName(annotation);
            List<String> methods = switch (simpleName) {
                case "GetMapping" -> List.of("GET");
                case "PostMapping" -> List.of("POST");
                case "PutMapping" -> List.of("PUT");
                case "PatchMapping" -> List.of("PATCH");
                case "DeleteMapping" -> List.of("DELETE");
                case "RequestMapping" -> requestMethods(annotation);
                default -> List.of();
            };
            if (methods.isEmpty()) {
                return Optional.empty();
            }
            List<String> paths = annotationStrings(annotation, "path", "value");
            return Optional.of(new Mapping(methods, paths.isEmpty() ? List.of("") : paths, annotation));
        }

        private List<String> requestMethods(AnnotationTree annotation) {
            List<String> values = annotationStrings(annotation, "method");
            if (values.isEmpty()) {
                return List.of("ANY");
            }
            return values.stream()
                    .map(value -> value.substring(value.lastIndexOf('.') + 1).toUpperCase(Locale.ROOT))
                    .toList();
        }

        private List<String> annotationStrings(AnnotationTree annotation, String... names) {
            List<String> values = new ArrayList<>();
            for (ExpressionTree argument : annotation.getArguments()) {
                if (argument instanceof AssignmentTree assignment) {
                    String name = assignment.getVariable().toString();
                    if (List.of(names).contains(name)) {
                        values.addAll(stringsOf(assignment.getExpression()));
                    }
                } else if (List.of(names).contains("value")) {
                    values.addAll(stringsOf(argument));
                }
            }
            return values.stream().filter(value -> !value.isBlank()).distinct().toList();
        }

        private List<String> stringsOf(ExpressionTree expression) {
            if (expression instanceof LiteralTree literal && literal.getValue() instanceof String value) {
                return List.of(value);
            }
            if (expression instanceof NewArrayTree array && array.getInitializers() != null) {
                return array.getInitializers().stream().flatMap(value -> stringsOf(value).stream()).toList();
            }
            String raw = expression.toString();
            if (raw.isBlank()) {
                return List.of();
            }
            // ConditionalOnBean/ConditionalOnClass 等大量使用 Foo.class。条件模型以类型名比较，
            // 保留 ".class" 会使源码中的真实条件永远无法与 Bean 或 classpath 能力匹配。
            return List.of(raw.endsWith(".class") ? raw.substring(0, raw.length() - ".class".length()) : raw);
        }

        private List<String> pathsOf(List<Mapping> mappings) {
            if (mappings.isEmpty()) {
                return List.of("");
            }
            return mappings.stream().flatMap(mapping -> mapping.paths().stream()).distinct().toList();
        }

        private List<String> combinePaths(List<String> basePaths, List<String> methodPaths) {
            return basePaths.stream().flatMap(base -> methodPaths.stream().map(method -> normalizePath(base, method)))
                    .distinct().toList();
        }

        private String normalizePath(String base, String method) {
            String joined = (base == null ? "" : base.trim()) + "/" + (method == null ? "" : method.trim());
            String normalized = joined.replaceAll("/{2,}", "/");
            if (!normalized.startsWith("/")) {
                normalized = "/" + normalized;
            }
            return normalized.length() > 1 && normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
        }

        private AtlasValue.Values values(List<String> entries) {
            return AtlasValue.values(entries.stream().map(AtlasValue::text).map(AtlasValue.class::cast).toList());
        }
    }

    private record ComponentContext(String beanName, String kind, String scope, boolean primary, List<String> qualifiers) {
        private ComponentContext {
            qualifiers = List.copyOf(qualifiers);
        }
    }

    private record ConditionDetail(String kind, String subject, String operator, String expectedValue) {
        String canonical() {
            return kind + "(" + subject + ") " + operator + (expectedValue.isBlank() ? "" : " " + expectedValue);
        }
    }

    private record ControllerContext(boolean isController, List<String> basePaths, String typeName) {
        private ControllerContext {
            basePaths = List.copyOf(basePaths);
        }

        static ControllerContext notController() {
            return new ControllerContext(false, List.of(""), "");
        }
    }

    private record Mapping(List<String> httpMethods, List<String> paths, AnnotationTree annotation) {
        private Mapping {
            httpMethods = List.copyOf(httpMethods);
            paths = List.copyOf(paths);
            Objects.requireNonNull(annotation, "annotation 不能为空");
        }
    }

    private record CommandDispatch(String dispatcher, String methodName, String commandType, String commandExpression) {
    }

    private record ApplicationComposition(List<String> componentScanPackages, List<String> importedTypes) {
        private ApplicationComposition {
            componentScanPackages = List.copyOf(componentScanPackages);
            importedTypes = List.copyOf(importedTypes);
        }
    }
}
