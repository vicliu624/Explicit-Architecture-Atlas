package io.github.vicliu624.explicitarchitecture.atlas.source.maven;

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
import io.github.vicliu624.explicitarchitecture.atlas.domain.Evidence;
import io.github.vicliu624.explicitarchitecture.atlas.domain.EvidenceClaim;
import io.github.vicliu624.explicitarchitecture.atlas.domain.EvidenceOrigin;
import io.github.vicliu624.explicitarchitecture.atlas.domain.EvidenceStatus;
import io.github.vicliu624.explicitarchitecture.atlas.domain.EvidenceVisibility;
import io.github.vicliu624.explicitarchitecture.atlas.domain.FactKind;
import io.github.vicliu624.explicitarchitecture.atlas.domain.FactRelation;
import io.github.vicliu624.explicitarchitecture.atlas.domain.RelationKind;
import io.github.vicliu624.explicitarchitecture.atlas.domain.SourceFact;
import io.github.vicliu624.explicitarchitecture.atlas.domain.SourceLocator;
import io.github.vicliu624.explicitarchitecture.atlas.domain.SourceRange;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 以安全、无网络的 XML 方式读取 Maven Reactor 声明和直接依赖。
 *
 * <p>它不执行 Maven，不解析私有仓库，也不声称已经得到 effective POM；属性插值、父 POM 继承和激活 profile
 * 会明确保留为后续 Maven Model 适配器能力，而非被误报为源码事实。</p>
 */
public final class MavenSourceFactsAnalyzer implements SourceFactsAnalyzer {

    private static final AnalyzerDescriptor DESCRIPTOR = new AnalyzerDescriptor(
            AtlasId.of("analyzer:maven-source"), "Maven 源码分析器", "Maven Reactor、依赖与编译配置事实");

    @Override
    public AnalyzerDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public AnalyzerContribution analyze(AnalysisRequest request) {
        Objects.requireNonNull(request, "request 不能为空");
        Path rootPom = request.projectRoot().resolve("pom.xml");
        if (!Files.isRegularFile(rootPom)) {
            return new AnalyzerContribution(
                    new CoverageReport(DESCRIPTOR.id().value(), CapabilityStatus.NOT_APPLICABLE, request.scopeUri(),
                            "项目根目录不存在 pom.xml，Maven 分析不适用"),
                    List.of(), List.of(), List.of());
        }

        Extraction extraction = new Extraction(request);
        extraction.scan(rootPom, Optional.empty());
        CapabilityStatus status = extraction.diagnostics.stream().anyMatch(diagnostic -> diagnostic.severity() == DiagnosticSeverity.ERROR)
                ? CapabilityStatus.PARTIAL
                : CapabilityStatus.COMPLETE;
        String summary = status == CapabilityStatus.COMPLETE
                ? "已读取 " + extraction.visitedPoms.size() + " 个 Maven POM 的声明模型"
                : "已部分读取 Maven POM 声明模型；请查看解析诊断";
        return new AnalyzerContribution(
                new CoverageReport(DESCRIPTOR.id().value(), status, request.scopeUri(), summary),
                extraction.facts.stream().sorted(Comparator.comparing(SourceFact::id)).toList(),
                extraction.relations.stream().sorted(Comparator.comparing(FactRelation::id)).toList(),
                extraction.diagnostics.stream().sorted(Comparator.comparing(AtlasDiagnostic::id)).toList());
    }

    private static final class Extraction {

        private final AnalysisRequest request;
        private final List<SourceFact> facts = new ArrayList<>();
        private final List<FactRelation> relations = new ArrayList<>();
        private final List<AtlasDiagnostic> diagnostics = new ArrayList<>();
        private final Set<Path> visitedPoms = new HashSet<>();

        private Extraction(AnalysisRequest request) {
            this.request = request;
        }

        private void scan(Path pom, Optional<AtlasId> parentModuleFactId) {
            Path normalizedPom = pom.toAbsolutePath().normalize();
            if (!visitedPoms.add(normalizedPom)) {
                return;
            }
            Document document;
            try {
                document = secureDocumentBuilder().parse(normalizedPom.toFile());
            } catch (ParserConfigurationException | SAXException | IOException exception) {
                diagnostics.add(diagnostic("MAVEN_POM_UNREADABLE", "无法读取 Maven POM: " + normalizedPom.getFileName() + " — " + exception.getMessage(), normalizedPom));
                return;
            }
            Element project = document.getDocumentElement();
            String relativePom = relativePath(normalizedPom);
            String groupId = textOfDirectChild(project, "groupId").or(() -> parentText(project, "groupId")).orElse("<inherited>");
            String artifactId = textOfDirectChild(project, "artifactId").orElse("<missing-artifactId>");
            String version = textOfDirectChild(project, "version").or(() -> parentText(project, "version")).orElse("<inherited>");
            String packaging = textOfDirectChild(project, "packaging").orElse("jar");
            String gav = groupId + ":" + artifactId + ":" + version;
            SourceFact module = fact(
                    FactKind.MAVEN_MODULE,
                    gav,
                    Map.of(
                            "pom", AtlasValue.text(relativePom),
                            "groupId", AtlasValue.text(groupId),
                            "artifactId", AtlasValue.text(artifactId),
                            "version", AtlasValue.text(version),
                            "packaging", AtlasValue.text(packaging)),
                    normalizedPom,
                    "module:" + relativePom);
            facts.add(module);
            parentModuleFactId.ifPresent(parentId -> relations.add(relation(
                    RelationKind.CONTAINS, parentId, module.id(), normalizedPom, "contains:" + parentId + ":" + module.id())));

            directDependencies(project).forEach(dependency -> {
                String dependencyGroupId = textOfDirectChild(dependency, "groupId").orElse("<missing-groupId>");
                String dependencyArtifactId = textOfDirectChild(dependency, "artifactId").orElse("<missing-artifactId>");
                String dependencyVersion = textOfDirectChild(dependency, "version").orElse("<managed>");
                String scope = textOfDirectChild(dependency, "scope").orElse("compile");
                String coordinate = dependencyGroupId + ":" + dependencyArtifactId + ":" + dependencyVersion;
                SourceFact dependencyFact = fact(
                        FactKind.MAVEN_DEPENDENCY,
                        coordinate,
                        Map.of(
                                "scope", AtlasValue.text(scope),
                                "optional", AtlasValue.text(textOfDirectChild(dependency, "optional").orElse("false")),
                                "declaredByPom", AtlasValue.text(relativePom)),
                        normalizedPom,
                        "dependency:" + relativePom + ":" + coordinate + ":" + facts.size());
                facts.add(dependencyFact);
                relations.add(relation(
                        RelationKind.DEPENDS_ON, module.id(), dependencyFact.id(), normalizedPom,
                        "depends-on:" + module.id() + ":" + dependencyFact.id()));
            });

            directModules(project).forEach(modulePath -> {
                Path childPom = normalizedPom.getParent().resolve(modulePath).resolve("pom.xml").normalize();
                if (!Files.isRegularFile(childPom)) {
                    diagnostics.add(diagnostic("MAVEN_DECLARED_MODULE_MISSING",
                            "Maven module 声明未找到 pom.xml: " + modulePath, normalizedPom));
                    return;
                }
                scan(childPom, Optional.of(module.id()));
            });
        }

        private SourceFact fact(
                FactKind kind,
                String subject,
                Map<String, AtlasValue> attributes,
                Path pom,
                String identitySuffix) {
            String identity = kind + ":" + identitySuffix;
            Evidence evidence = evidence(pom, identity, kind + " 由 Maven POM 声明提取");
            return new SourceFact(
                    AtlasId.of("fact:maven-source:" + encode(identity)),
                    DESCRIPTOR.id().value(),
                    kind,
                    subject,
                    attributes,
                    List.of(evidence));
        }

        private FactRelation relation(RelationKind kind, AtlasId source, AtlasId target, Path pom, String identitySuffix) {
            String identity = kind + ":" + identitySuffix;
            return new FactRelation(
                    AtlasId.of("fact-relation:maven-source:" + encode(identity)),
                    kind,
                    source,
                    target,
                    Map.of(),
                    List.of(evidence(pom, identity, kind + " 由 Maven POM 声明提取")));
        }

        private Evidence evidence(Path pom, String identity, String summary) {
            return new Evidence(
                    AtlasId.of("evidence:maven-source:" + encode(identity)),
                    EvidenceOrigin.MAVEN_MODEL,
                    EvidenceClaim.DERIVED,
                    EvidenceStatus.VERIFIED,
                    new SourceLocator(projectUri(relativePath(pom)), "pom.xml", SourceRange.unknown()),
                    request.sourceRevision(),
                    EvidenceVisibility.EXPERT_ONLY,
                    DESCRIPTOR.id().value(),
                    summary);
        }

        private AtlasDiagnostic diagnostic(String code, String message, Path pom) {
            return new AtlasDiagnostic(
                    AtlasId.of("diagnostic:maven-source:" + encode(code + ":" + relativePath(pom) + ":" + message)),
                    DiagnosticSeverity.WARNING,
                    code,
                    message,
                    Optional.of(new SourceLocator(projectUri(relativePath(pom)), "pom.xml", SourceRange.unknown())));
        }

        private String relativePath(Path path) {
            return request.projectRoot().relativize(path.toAbsolutePath().normalize()).toString().replace('\\', '/');
        }

        private String projectUri(String relativePath) {
            String encodedPath = Stream.of(relativePath.split("/"))
                    .map(segment -> URLEncoder.encode(segment, StandardCharsets.UTF_8))
                    .reduce((left, right) -> left + "/" + right)
                    .orElse("");
            return "project://" + request.projectId().value() + "/" + encodedPath;
        }
    }

    private static DocumentBuilder secureDocumentBuilder() throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder();
    }

    private static Optional<String> textOfDirectChild(Element parent, String name) {
        return directChildren(parent, name).stream().findFirst().map(Element::getTextContent).map(String::trim).filter(value -> !value.isEmpty());
    }

    private static Optional<String> parentText(Element project, String name) {
        return directChildren(project, "parent").stream().findFirst().flatMap(parent -> textOfDirectChild(parent, name));
    }

    private static List<Element> directDependencies(Element project) {
        return directChildren(project, "dependencies").stream()
                .flatMap(dependencies -> directChildren(dependencies, "dependency").stream())
                .toList();
    }

    private static List<String> directModules(Element project) {
        return directChildren(project, "modules").stream()
                .flatMap(modules -> directChildren(modules, "module").stream())
                .map(Element::getTextContent)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    private static List<Element> directChildren(Element parent, String name) {
        List<Element> matches = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child instanceof Element element && name.equals(element.getLocalName() == null ? element.getNodeName() : element.getLocalName())) {
                matches.add(element);
            }
        }
        return matches;
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
