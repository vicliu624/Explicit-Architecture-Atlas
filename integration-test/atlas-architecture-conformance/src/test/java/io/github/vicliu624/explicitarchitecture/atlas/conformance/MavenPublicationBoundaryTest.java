package io.github.vicliu624.explicitarchitecture.atlas.conformance;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 发布 POM 的依赖泄漏门禁。
 *
 * <p>这不是对 Java import 的检查：消费者实际解析的是 POM。测试递归读取仓库内公开 artifact 的非 test 依赖，
 * 因而一旦 core/CLI/starter 通过任一内部模块间接拉入未选择框架，构建会失败。</p>
 */
class MavenPublicationBoundaryTest {

    private static final String GROUP = "io.github.vicliu624.explicitarchitecture";

    @Test
    void coreAnnotationsAndJvmAnalyzerHaveNoExternalRuntimeDependency() throws Exception {
        PublicationGraph graph = PublicationGraph.load(repositoryRoot());

        assertOnlyAtlasArtifacts(graph.dependenciesOf("explicit-architecture-atlas-core"));
        assertOnlyAtlasArtifacts(graph.dependenciesOf("explicit-architecture-atlas-annotations"));
        assertOnlyAtlasArtifacts(graph.dependenciesOf("explicit-architecture-atlas-analysis-spi"));
        assertOnlyAtlasArtifacts(graph.dependenciesOf("explicit-architecture-atlas-source-jvm"));
    }

    @Test
    void ordinaryCliAndFocusedStartersDoNotLeakUnselectedFrameworkAdapters() throws Exception {
        PublicationGraph graph = PublicationGraph.load(repositoryRoot());

        assertNoneMatch(graph.dependenciesOf("explicit-architecture-atlas-cli"), MavenPublicationBoundaryTest::isFrameworkSpecific);
        assertNoneMatch(graph.dependenciesOf("explicit-architecture-atlas-maven-plugin"), MavenPublicationBoundaryTest::isFrameworkSpecific);
        assertNoneMatch(graph.dependenciesOf("explicit-architecture-atlas-starter-jvm"), MavenPublicationBoundaryTest::isFrameworkSpecific);
        assertNoneMatch(graph.dependenciesOf("explicit-architecture-atlas-starter-spring"), dependency -> isAxonOrJpa(dependency));
        assertNoneMatch(graph.dependenciesOf("explicit-architecture-atlas-starter-axon"), dependency -> isSpringOrJpa(dependency));
        assertNoneMatch(graph.dependenciesOf("explicit-architecture-atlas-starter-jpa"), dependency -> isSpringOrAxon(dependency));
        assertTrue(graph.dependenciesOf("explicit-architecture-atlas-cli-full").stream()
                .anyMatch(dependency -> dependency.artifactId().equals("explicit-architecture-atlas-starter-full")),
                "完整 CLI 必须显式选择 starter-full，不能把完整适配器集藏入普通 CLI");
    }

    private static void assertOnlyAtlasArtifacts(Set<Dependency> dependencies) {
        assertTrue(dependencies.stream().allMatch(dependency -> dependency.groupId().equals(GROUP)),
                () -> "框架无关 artifact 不能携带外部运行时依赖: " + dependencies);
    }

    private static void assertNoneMatch(Set<Dependency> dependencies, Predicate<Dependency> forbidden) {
        assertFalse(dependencies.stream().anyMatch(forbidden), () -> "发现未选择框架泄漏: " + dependencies);
    }

    private static boolean isFrameworkSpecific(Dependency dependency) {
        return isSpringOrAxon(dependency) || isJpa(dependency);
    }

    private static boolean isSpringOrAxon(Dependency dependency) {
        return isSpring(dependency) || isAxon(dependency);
    }

    private static boolean isAxonOrJpa(Dependency dependency) {
        return isAxon(dependency) || isJpa(dependency);
    }

    private static boolean isSpringOrJpa(Dependency dependency) {
        return isSpring(dependency) || isJpa(dependency);
    }

    private static boolean isSpring(Dependency dependency) {
        return dependency.groupId().startsWith("org.springframework") || dependency.artifactId().contains("-spring-");
    }

    private static boolean isAxon(Dependency dependency) {
        return dependency.groupId().startsWith("org.axonframework") || dependency.artifactId().contains("-axon-");
    }

    private static boolean isJpa(Dependency dependency) {
        return dependency.groupId().startsWith("jakarta.persistence") || dependency.groupId().startsWith("javax.persistence")
                || dependency.groupId().startsWith("org.hibernate") || dependency.artifactId().contains("-jpa-");
    }

    private static Path repositoryRoot() {
        // Surefire 在不同 Maven/IDE 组合下可能把 maven.multiModuleProjectDirectory 设为当前模块，
        // 而不是聚合根；从 Maven 目录与 JVM 当前目录同时向上搜寻，避免发布门禁在真实 Reactor 中失效。
        List<Path> startingPoints = List.of(
                Path.of(System.getProperty("maven.multiModuleProjectDirectory", ".")).toAbsolutePath().normalize(),
                Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize());
        for (Path startingPoint : startingPoints) {
            for (Path candidate = startingPoint; candidate != null; candidate = candidate.getParent()) {
                if (Files.isRegularFile(candidate.resolve("pom.xml"))
                        && Files.isDirectory(candidate.resolve("core").resolve("atlas-core"))
                        && Files.isDirectory(candidate.resolve("infra-driving").resolve("atlas-cli"))) {
                    return candidate;
                }
            }
        }
        throw new IllegalStateException("无法定位 Maven 多模块工程根；检查 maven.multiModuleProjectDirectory 与 user.dir");
    }

    private record Dependency(String groupId, String artifactId) {
        @Override
        public String toString() {
            return groupId + ":" + artifactId;
        }
    }

    private static final class PublicationGraph {

        private final Map<String, List<Dependency>> directDependencies;

        private PublicationGraph(Map<String, List<Dependency>> directDependencies) {
            this.directDependencies = Map.copyOf(directDependencies);
        }

        static PublicationGraph load(Path root) throws Exception {
            Map<String, List<Dependency>> dependencies = new LinkedHashMap<>();
            try (var paths = Files.walk(root)) {
                for (Path pom : paths.filter(path -> path.getFileName().toString().equals("pom.xml")).toList()) {
                    ParsedPom parsed = parse(pom);
                    if (parsed.groupId().equals(GROUP) && !parsed.artifactId().equals("explicit-architecture-atlas")) {
                        dependencies.put(parsed.artifactId(), parsed.dependencies());
                    }
                }
            }
            return new PublicationGraph(dependencies);
        }

        Set<Dependency> dependenciesOf(String artifactId) {
            if (!directDependencies.containsKey(artifactId)) {
                throw new IllegalArgumentException("找不到公开 artifact POM: " + artifactId);
            }
            Set<Dependency> result = new LinkedHashSet<>();
            Set<String> visited = new LinkedHashSet<>();
            ArrayDeque<String> queue = new ArrayDeque<>();
            queue.add(artifactId);
            while (!queue.isEmpty()) {
                String current = queue.removeFirst();
                if (!visited.add(current)) {
                    continue;
                }
                for (Dependency dependency : directDependencies.getOrDefault(current, List.of())) {
                    if (result.add(dependency) && dependency.groupId().equals(GROUP) && directDependencies.containsKey(dependency.artifactId())) {
                        queue.addLast(dependency.artifactId());
                    }
                }
            }
            return Set.copyOf(result);
        }

        private static ParsedPom parse(Path file) throws Exception {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            Document document = factory.newDocumentBuilder().parse(file.toFile());
            Element project = document.getDocumentElement();
            String groupId = child(project, "groupId").orElse(GROUP);
            String artifactId = child(project, "artifactId").orElseThrow(() -> new IllegalArgumentException("POM 缺少 artifactId: " + file));
            Element dependencies = directChild(project, "dependencies");
            if (dependencies == null) {
                return new ParsedPom(groupId, artifactId, List.of());
            }
            List<Dependency> result = new java.util.ArrayList<>();
            NodeList entries = dependencies.getElementsByTagName("dependency");
            for (int index = 0; index < entries.getLength(); index++) {
                Element dependency = (Element) entries.item(index);
                String scope = child(dependency, "scope").orElse("compile");
                if (scope.equals("test")) {
                    continue;
                }
                String dependencyGroup = child(dependency, "groupId").orElse("").replace("${project.groupId}", GROUP);
                String dependencyArtifact = child(dependency, "artifactId").orElse("");
                if (!dependencyGroup.isBlank() && !dependencyArtifact.isBlank()) {
                    result.add(new Dependency(dependencyGroup, dependencyArtifact));
                }
            }
            return new ParsedPom(groupId.replace("${project.groupId}", GROUP), artifactId, List.copyOf(result));
        }

        private static java.util.Optional<String> child(Element parent, String name) {
            Element value = directChild(parent, name);
            return value == null ? java.util.Optional.empty() : java.util.Optional.of(value.getTextContent().trim());
        }

        private static Element directChild(Element parent, String name) {
            for (org.w3c.dom.Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
                if (child instanceof Element element && element.getTagName().equals(name)) {
                    return element;
                }
            }
            return null;
        }
    }

    private record ParsedPom(String groupId, String artifactId, List<Dependency> dependencies) {
    }
}
