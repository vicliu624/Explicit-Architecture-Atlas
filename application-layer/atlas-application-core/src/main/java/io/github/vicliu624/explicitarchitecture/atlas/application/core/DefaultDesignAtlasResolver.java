package io.github.vicliu624.explicitarchitecture.atlas.application.core;

import io.github.vicliu624.explicitarchitecture.atlas.domain.ApplicationUseCase;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ArchitectureAsset;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AssetKind;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AssetMetadata;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasDiagnostic;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasGraph;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasId;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasRelation;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasValue;
import io.github.vicliu624.explicitarchitecture.atlas.domain.BusinessConcept;
import io.github.vicliu624.explicitarchitecture.atlas.domain.CodeElement;
import io.github.vicliu624.explicitarchitecture.atlas.domain.CodeElementKind;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ContentBundle;
import io.github.vicliu624.explicitarchitecture.atlas.domain.DataTarget;
import io.github.vicliu624.explicitarchitecture.atlas.domain.DataTargetKind;
import io.github.vicliu624.explicitarchitecture.atlas.domain.DiagnosticSeverity;
import io.github.vicliu624.explicitarchitecture.atlas.domain.DomainConstraint;
import io.github.vicliu624.explicitarchitecture.atlas.domain.DomainConstraintKind;
import io.github.vicliu624.explicitarchitecture.atlas.domain.DomainEvent;
import io.github.vicliu624.explicitarchitecture.atlas.domain.EndpointOperation;
import io.github.vicliu624.explicitarchitecture.atlas.domain.EnforcementLevel;
import io.github.vicliu624.explicitarchitecture.atlas.domain.EventField;
import io.github.vicliu624.explicitarchitecture.atlas.domain.EventFieldRole;
import io.github.vicliu624.explicitarchitecture.atlas.domain.Evidence;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ExternalBoundary;
import io.github.vicliu624.explicitarchitecture.atlas.domain.BoundaryKind;
import io.github.vicliu624.explicitarchitecture.atlas.domain.FactBundle;
import io.github.vicliu624.explicitarchitecture.atlas.domain.FactKind;
import io.github.vicliu624.explicitarchitecture.atlas.domain.IntentBundle;
import io.github.vicliu624.explicitarchitecture.atlas.domain.IntentDeclaration;
import io.github.vicliu624.explicitarchitecture.atlas.domain.IntentTarget;
import io.github.vicliu624.explicitarchitecture.atlas.domain.ProcessManager;
import io.github.vicliu624.explicitarchitecture.atlas.domain.Projection;
import io.github.vicliu624.explicitarchitecture.atlas.domain.RelationKind;
import io.github.vicliu624.explicitarchitecture.atlas.domain.SourceFact;
import io.github.vicliu624.explicitarchitecture.atlas.domain.SourceLocator;
import io.github.vicliu624.explicitarchitecture.atlas.domain.SourceRange;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 默认的语义归一化器：显式意图决定业务名称和职责，源码事实决定可证明的实现与调用证据。
 *
 * <p>它绝不把一个未安装分析器的空结果解读为“不存在事件、投影或 Saga”；覆盖状态由 Package Manifest 单独表达。
 * 无法可靠匹配的调用同样不会臆造图边。</p>
 */
public final class DefaultDesignAtlasResolver implements DesignAtlasResolver {

    @Override
    public DesignAtlasResolution resolve(FactBundle facts, IntentBundle intents, ContentBundle content) {
        Objects.requireNonNull(facts, "facts 不能为空");
        Objects.requireNonNull(intents, "intents 不能为空");
        Objects.requireNonNull(content, "content 不能为空");
        ResolutionState state = new ResolutionState(facts);
        state.addSourceElements();
        state.addIntentAssets(intents.declarations());
        state.addDerivedValidationConstraints();
        state.addResolvedCalls();
        return new DesignAtlasResolution(
                new AtlasGraph(List.copyOf(state.nodes.values()), List.copyOf(state.relations.values())),
                state.diagnostics.stream().sorted(Comparator.comparing(AtlasDiagnostic::id)).toList());
    }

    private static final class ResolutionState {

        private final FactBundle facts;
        private final Map<AtlasId, ArchitectureAsset> nodes = new LinkedHashMap<>();
        private final Map<AtlasId, AtlasRelation> relations = new LinkedHashMap<>();
        private final List<AtlasDiagnostic> diagnostics = new ArrayList<>();
        private final Map<String, List<SourceFact>> factsBySubject;

        private ResolutionState(FactBundle facts) {
            this.facts = facts;
            this.factsBySubject = facts.facts().stream().collect(Collectors.groupingBy(SourceFact::subject));
        }

        private void addSourceElements() {
            for (SourceFact fact : facts.facts()) {
                CodeElementKind kind = switch (fact.kind()) {
                    case SOURCE_TYPE -> CodeElementKind.TYPE;
                    case SOURCE_FIELD -> CodeElementKind.FIELD;
                    case SOURCE_METHOD -> CodeElementKind.METHOD;
                    case MAVEN_MODULE, MAVEN_DEPENDENCY -> CodeElementKind.CONFIGURATION;
                    default -> null;
                };
                if (kind != null) {
                    upsertCodeElement(fact.subject(), kind, fact.evidence(), fact.evidence().getFirst().locator());
                }
            }
        }

        private void addIntentAssets(List<IntentDeclaration> declarations) {
            for (IntentDeclaration declaration : declarations) {
                AtlasId assetId = assetIdOf(declaration);
                String codeKey = sourceKey(declaration.target());
                AtlasId codeId = resolvedCodeId(declaration.target(), codeKey);
                List<Evidence> evidence = mergeEvidence(
                        List.of(declaration.evidence()),
                        sourceEvidenceFor(declaration.target()));
                upsertCodeElement(codeKey, codeKind(declaration.target()), evidence, evidence.getFirst().locator());
                ArchitectureAsset asset = createIntentAsset(declaration, assetId, codeId, evidence);
                if (!putNode(asset)) {
                    diagnostics.add(diagnostic("DUPLICATE_ASSET_ID",
                            "多个显式意图声明了同一个 Atlas 资产身份: " + assetId, declaration.evidence().locator()));
                    continue;
                }
                addRelation(RelationKind.REPRESENTS, asset.id(), codeId, evidence, "represents:" + asset.id() + ":" + codeId);
                if (asset instanceof DomainConstraint) {
                    addRelation(RelationKind.CONSTRAINS, asset.id(), codeId, evidence, "constrains:" + asset.id() + ":" + codeId);
                }
            }
        }

        private void addDerivedValidationConstraints() {
            for (SourceFact fact : facts.facts()) {
                if (fact.kind() != FactKind.VALIDATION_RULE) {
                    continue;
                }
                String owner = text(fact.attributes(), "owner").orElse(fact.subject());
                AtlasId subjectId = codeId(owner);
                if (!nodes.containsKey(subjectId)) {
                    upsertCodeElement(owner, CodeElementKind.METHOD, fact.evidence(), fact.evidence().getFirst().locator());
                }
                boolean alreadyDescribed = nodes.values().stream()
                        .filter(DomainConstraint.class::isInstance)
                        .map(DomainConstraint.class::cast)
                        .anyMatch(constraint -> constraint.subjectId().equals(subjectId));
                if (alreadyDescribed) {
                    continue;
                }
                String expression = text(fact.attributes(), "expression").orElse("未提取表达式");
                String enforcingCall = text(fact.attributes(), "enforcingCall").orElse("未识别校验调用");
                DomainConstraint constraint = new DomainConstraint(
                        new AssetMetadata(
                                AtlasId.of("derived:constraint:" + encode(fact.id().value())),
                                "代码校验规则",
                                Map.of("derivedFromFact", AtlasValue.text(fact.id().value())),
                                fact.evidence()),
                        subjectId,
                        constraintKindFor(fact),
                        EnforcementLevel.HARD,
                        "在 " + owner + " 中通过 " + enforcingCall + " 强制校验：" + expression);
                putNode(constraint);
                addRelation(RelationKind.CONSTRAINS, constraint.id(), subjectId, fact.evidence(),
                        "derived-constrains:" + constraint.id() + ":" + subjectId);
            }
        }

        private void addResolvedCalls() {
            List<CodeElement> codeElements = nodes.values().stream()
                    .filter(CodeElement.class::isInstance)
                    .map(CodeElement.class::cast)
                    .toList();
            for (SourceFact fact : facts.facts()) {
                if (fact.kind() != FactKind.METHOD_CALL) {
                    continue;
                }
                Optional<String> caller = text(fact.attributes(), "caller");
                Optional<String> ownerHint = text(fact.attributes(), "calleeOwnerHint");
                Optional<String> methodName = text(fact.attributes(), "calleeMethodName");
                if (caller.isEmpty() || methodName.isEmpty()) {
                    continue;
                }
                AtlasId sourceId = codeId(caller.get());
                if (!nodes.containsKey(sourceId)) {
                    continue;
                }
                List<CodeElement> candidates = codeElements.stream()
                        .filter(candidate -> candidate.elementKind() == CodeElementKind.METHOD)
                        .filter(candidate -> candidate.qualifiedName().contains("#" + methodName.get() + "("))
                        .filter(candidate -> ownerHint.isEmpty() || ownerHint.get().isBlank()
                                || candidate.qualifiedName().contains("." + ownerHint.get() + "#"))
                        .toList();
                if (candidates.size() == 1) {
                    CodeElement target = candidates.getFirst();
                    addRelation(RelationKind.CALLS, sourceId, target.id(), fact.evidence(),
                            "calls:" + sourceId + ":" + target.id() + ":" + fact.id());
                } else if (candidates.size() > 1) {
                    diagnostics.add(diagnostic("AMBIGUOUS_STATIC_CALL",
                            "无法唯一解析静态调用目标: " + fact.subject(), fact.evidence().getFirst().locator()));
                }
            }
        }

        private ArchitectureAsset createIntentAsset(
                IntentDeclaration declaration,
                AtlasId assetId,
                AtlasId codeId,
                List<Evidence> evidence) {
            Map<String, AtlasValue> attributes = declaration.attributes();
            AssetMetadata metadata = new AssetMetadata(
                    assetId,
                    requiredText(attributes, "name", declaration.annotationType()),
                    attributes,
                    evidence);
            return switch (declaration.assetKind()) {
                case BUSINESS_CONCEPT -> new BusinessConcept(metadata,
                        firstNonBlank(text(attributes, "responsibility"), text(attributes, "description"),
                                "未提供职责说明"));
                case DOMAIN_CONSTRAINT -> new DomainConstraint(
                        metadata,
                        codeId,
                        constraintKind(firstNonBlank(text(attributes, "kind"), Optional.empty(), "CUSTOM")),
                        EnforcementLevel.HARD,
                        firstNonBlank(text(attributes, "businessMeaning"), text(attributes, "expression"), "未提供规则说明"));
                case DOMAIN_EVENT -> new DomainEvent(metadata,
                        firstNonBlank(text(attributes, "fact"), Optional.empty(), "未提供业务事实说明"),
                        values(attributes, "payload").stream()
                                .map(value -> new EventField(value, EventFieldRole.UNSPECIFIED, ""))
                                .toList());
                case APPLICATION_USE_CASE -> new ApplicationUseCase(metadata,
                        firstNonBlank(text(attributes, "purpose"), Optional.empty(), "未提供用例目的"));
                case PROJECTION -> new Projection(metadata,
                        firstNonBlank(text(attributes, "purpose"), Optional.empty(), "未提供投影目的"));
                case PROCESS_MANAGER -> new ProcessManager(metadata,
                        firstNonBlank(text(attributes, "responsibility"), Optional.empty(), "未提供流程职责"),
                        text(attributes, "completionCondition").orElse(""));
                case ENDPOINT_OPERATION -> new EndpointOperation(metadata,
                        firstNonBlank(text(attributes, "purpose"), Optional.empty(), "未提供接口目的"));
                case DATA_TARGET -> new DataTarget(metadata, DataTargetKind.UNSPECIFIED);
                case EXTERNAL_BOUNDARY -> new ExternalBoundary(metadata, BoundaryKind.UNSPECIFIED,
                        firstNonBlank(text(attributes, "responsibility"), Optional.empty(), "未提供边界职责"));
                case CODE_ELEMENT -> new CodeElement(metadata, codeKind(declaration.target()), sourceKey(declaration.target()),
                        declaration.evidence().locator());
            };
        }

        private AtlasId resolvedCodeId(IntentTarget target, String defaultKey) {
            String exactKey = sourceKey(target);
            if (nodes.containsKey(codeId(exactKey))) {
                return codeId(exactKey);
            }
            if (!target.memberDescriptor().isBlank()) {
                String methodName = target.memberDescriptor().substring(0, target.memberDescriptor().indexOf('('));
                List<AtlasId> sameMethod = nodes.values().stream()
                        .filter(CodeElement.class::isInstance)
                        .map(CodeElement.class::cast)
                        .filter(element -> element.elementKind() == CodeElementKind.METHOD)
                        .filter(element -> element.qualifiedName().startsWith(target.binaryName() + "#" + methodName + "("))
                        .map(CodeElement::id)
                        .toList();
                if (sameMethod.size() == 1) {
                    return sameMethod.getFirst();
                }
            }
            return codeId(defaultKey);
        }

        private List<Evidence> sourceEvidenceFor(IntentTarget target) {
            String key = sourceKey(target);
            List<SourceFact> exact = factsBySubject.getOrDefault(key, List.of());
            if (!exact.isEmpty()) {
                return exact.stream().flatMap(fact -> fact.evidence().stream()).toList();
            }
            if (!target.memberDescriptor().isBlank()) {
                String methodName = target.memberDescriptor().substring(0, target.memberDescriptor().indexOf('('));
                return facts.facts().stream()
                        .filter(fact -> fact.kind() == FactKind.SOURCE_METHOD)
                        .filter(fact -> fact.subject().startsWith(target.binaryName() + "#" + methodName + "("))
                        .flatMap(fact -> fact.evidence().stream())
                        .toList();
            }
            return List.of();
        }

        private void upsertCodeElement(String sourceKey, CodeElementKind kind, List<Evidence> evidence, SourceLocator locator) {
            AtlasId id = codeId(sourceKey);
            ArchitectureAsset existing = nodes.get(id);
            if (existing == null) {
                nodes.put(id, new CodeElement(
                        new AssetMetadata(id, sourceKey, Map.of(), evidence),
                        kind,
                        sourceKey,
                        locator));
                return;
            }
            if (existing instanceof CodeElement codeElement) {
                nodes.put(id, new CodeElement(
                        new AssetMetadata(id, codeElement.name(), codeElement.attributes(), mergeEvidence(codeElement.evidence(), evidence)),
                        codeElement.elementKind(),
                        codeElement.qualifiedName(),
                        codeElement.locator()));
            }
        }

        private boolean putNode(ArchitectureAsset node) {
            return nodes.putIfAbsent(node.id(), node) == null;
        }

        private void addRelation(RelationKind kind, AtlasId source, AtlasId target, List<Evidence> evidence, String identity) {
            AtlasId id = AtlasId.of("relation:" + encode(identity));
            relations.putIfAbsent(id, new AtlasRelation(id, kind, source, target, Map.of(), evidence));
        }

        private static AtlasId assetIdOf(IntentDeclaration declaration) {
            Optional<String> explicitId = text(declaration.attributes(), "id");
            if (explicitId.isPresent() && !explicitId.get().chars().anyMatch(Character::isWhitespace)) {
                return AtlasId.of(explicitId.get());
            }
            return AtlasId.of("intent-asset:" + encode(declaration.id().value()));
        }

        private static AtlasId codeId(String sourceKey) {
            return AtlasId.of("code:" + encode(sourceKey));
        }

        private static String sourceKey(IntentTarget target) {
            return target.memberDescriptor().isBlank()
                    ? target.binaryName()
                    : target.binaryName() + "#" + target.memberDescriptor();
        }

        private static CodeElementKind codeKind(IntentTarget target) {
            return switch (target.kind()) {
                case "METHOD" -> CodeElementKind.METHOD;
                case "CONSTRUCTOR" -> CodeElementKind.CONSTRUCTOR;
                case "FIELD", "ENUM_CONSTANT" -> CodeElementKind.FIELD;
                case "RECORD_COMPONENT" -> CodeElementKind.RECORD_COMPONENT;
                case "PARAMETER" -> CodeElementKind.PARAMETER;
                case "PACKAGE" -> CodeElementKind.PACKAGE;
                case "ANNOTATION_TYPE" -> CodeElementKind.ANNOTATION;
                default -> CodeElementKind.TYPE;
            };
        }

        private static DomainConstraintKind constraintKind(String annotationKind) {
            return switch (annotationKind) {
                case "NUMERIC_RANGE" -> DomainConstraintKind.VALUE_RANGE;
                default -> {
                    try {
                        yield DomainConstraintKind.valueOf(annotationKind);
                    } catch (IllegalArgumentException ignored) {
                        yield DomainConstraintKind.CUSTOM;
                    }
                }
            };
        }

        private static DomainConstraintKind constraintKindFor(SourceFact fact) {
            return text(fact.attributes(), "ruleType").filter("PATTERN"::equals)
                    .map(ignored -> DomainConstraintKind.IDENTITY_FORMAT)
                    .orElse(DomainConstraintKind.EVENT_PRECONDITION);
        }

        private static Optional<String> text(Map<String, AtlasValue> attributes, String name) {
            AtlasValue value = attributes.get(name);
            return value instanceof AtlasValue.Text text && !text.value().isBlank()
                    ? Optional.of(text.value().trim())
                    : Optional.empty();
        }

        private static List<String> values(Map<String, AtlasValue> attributes, String name) {
            AtlasValue value = attributes.get(name);
            if (value instanceof AtlasValue.Values values) {
                return values.values().stream()
                        .filter(AtlasValue.Text.class::isInstance)
                        .map(AtlasValue.Text.class::cast)
                        .map(AtlasValue.Text::value)
                        .filter(entry -> !entry.isBlank())
                        .toList();
            }
            return text(attributes, name).map(List::of).orElse(List.of());
        }

        private static String requiredText(Map<String, AtlasValue> attributes, String name, String fallback) {
            return text(attributes, name).orElse(fallback);
        }

        private static String firstNonBlank(Optional<String> first, Optional<String> second, String fallback) {
            return first.or(() -> second).orElse(fallback);
        }

        private static List<Evidence> mergeEvidence(List<Evidence> first, List<Evidence> second) {
            Map<AtlasId, Evidence> byId = new LinkedHashMap<>();
            Stream.concat(first.stream(), second.stream()).forEach(evidence -> byId.putIfAbsent(evidence.id(), evidence));
            return byId.values().stream().sorted(Comparator.comparing(Evidence::id)).toList();
        }

        private static AtlasDiagnostic diagnostic(String code, String message, SourceLocator locator) {
            return new AtlasDiagnostic(
                    AtlasId.of("diagnostic:resolver:" + encode(code + ":" + message)),
                    DiagnosticSeverity.WARNING,
                    code,
                    message,
                    Optional.of(locator));
        }

        private static String encode(String value) {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
        }
    }
}
