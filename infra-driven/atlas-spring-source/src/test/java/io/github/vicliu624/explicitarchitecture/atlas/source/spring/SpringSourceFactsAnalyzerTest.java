package io.github.vicliu624.explicitarchitecture.atlas.source.spring;

import io.github.vicliu624.explicitarchitecture.atlas.analysis.spi.AnalysisRequest;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasId;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasValue;
import io.github.vicliu624.explicitarchitecture.atlas.domain.FactKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpringSourceFactsAnalyzerTest {

    @TempDir
    Path project;

    @Test
    void extractsConditionalBeansInjectionPointsConfigurationAndAdvisorsWithoutSpringClasspath() throws IOException {
        Path source = project.resolve("src/main/java/example/SampleConfiguration.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
                package example;

                @Configuration
                class SampleConfiguration {
                    @Bean
                    @Profile("prod")
                    @ConditionalOnProperty(name = "payment.gateway", havingValue = "remote")
                    @Primary
                    PaymentGateway paymentGateway(PaymentClient client) {
                        return new RemotePaymentGateway(client);
                    }
                }

                @Service
                class PaymentService {
                    @Autowired(required = false)
                    @Qualifier("paymentGateway")
                    PaymentGateway gateway;

                    @Value("${payment.retry.count:3}")
                    int retryCount;

                    @Transactional
                    void pay() { }
                }
                """);

        var contribution = new SpringSourceFactsAnalyzer().analyze(
                new AnalysisRequest(AtlasId.of("project:spring-conditions"), project, "revision:test"));

        assertTrue(contribution.facts().stream().anyMatch(fact -> fact.kind() == FactKind.BEAN_DEFINITION
                && "paymentGateway".equals(((AtlasValue.Text) fact.attributes().get("beanName")).value())));
        assertTrue(contribution.facts().stream().anyMatch(fact -> fact.kind() == FactKind.BEAN_CONDITION
                && "PROFILE".equals(((AtlasValue.Text) fact.attributes().get("conditionKind")).value())));
        assertTrue(contribution.facts().stream().anyMatch(fact -> fact.kind() == FactKind.BEAN_CONDITION
                && "payment.gateway".equals(((AtlasValue.Text) fact.attributes().get("conditionSubject")).value())));
        var injection = contribution.facts().stream().filter(fact -> fact.kind() == FactKind.BEAN_INJECTION)
                .filter(fact -> fact.subject().endsWith("#gateway")).findFirst().orElseThrow();
        assertEquals(false, ((AtlasValue.Bool) injection.attributes().get("required")).value());
        assertTrue(contribution.facts().stream().anyMatch(fact -> fact.kind() == FactKind.CONFIGURATION_PROPERTY
                && "payment.retry.count".equals(fact.subject())));
        assertTrue(contribution.facts().stream().anyMatch(fact -> fact.kind() == FactKind.AOP_ADVISOR
                && "Transactional".equals(((AtlasValue.Text) fact.attributes().get("advisor")).value())));
    }

    @Test
    void inheritsConfigurationConditionsAndFindsSingleConstructorAndResourceNameInjection() throws IOException {
        Path source = project.resolve("src/main/java/example/ConditionalConfiguration.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
                package example;

                @Configuration
                @Profile({"prod", "staging"})
                @ConditionalOnProperty(prefix = "payment.", name = "enabled", matchIfMissing = true)
                class ConditionalConfiguration {
                    @Bean PaymentGateway paymentGateway() { return null; }
                }

                @Service
                class PaymentService {
                    PaymentService(@Resource(name = "paymentGateway") PaymentGateway gateway) { }
                }
                """);

        var contribution = new SpringSourceFactsAnalyzer().analyze(
                new AnalysisRequest(AtlasId.of("project:spring-inheritance"), project, "revision:test"));

        assertTrue(contribution.facts().stream().filter(fact -> fact.kind() == FactKind.BEAN_CONDITION)
                .filter(fact -> "paymentGateway".equals(((AtlasValue.Text) fact.attributes().get("beanName")).value()))
                .anyMatch(fact -> "MATCHES_ANY".equals(((AtlasValue.Text) fact.attributes().get("operator")).value())
                        && "prod|staging".equals(((AtlasValue.Text) fact.attributes().get("expectedValue")).value())));
        assertTrue(contribution.facts().stream().filter(fact -> fact.kind() == FactKind.BEAN_CONDITION)
                .filter(fact -> "paymentGateway".equals(((AtlasValue.Text) fact.attributes().get("beanName")).value()))
                .anyMatch(fact -> "payment.enabled".equals(((AtlasValue.Text) fact.attributes().get("conditionSubject")).value())
                        && "NOT_EQUALS_IGNORE_CASE_OR_MISSING".equals(((AtlasValue.Text) fact.attributes().get("operator")).value())));
        var injection = contribution.facts().stream().filter(fact -> fact.kind() == FactKind.BEAN_INJECTION)
                .filter(fact -> fact.subject().contains("implicit-constructor-parameter[0]:gateway")).findFirst().orElseThrow();
        AtlasValue.Values qualifiers = (AtlasValue.Values) injection.attributes().get("qualifiers");
        assertEquals("paymentGateway", ((AtlasValue.Text) qualifiers.values().getFirst()).value());
    }

    @Test
    void expandsTypeLevelConfigurationPropertiesIntoCanonicalFieldKeysWithoutLeakingCredentialDefaults() throws IOException {
        Path source = project.resolve("src/main/java/example/PaymentRetryProperties.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
                package example;

                @ConfigurationProperties(prefix = "payment.retry")
                class PaymentRetryProperties {
                    int maxAttempts = 3;
                    String accessToken = "must-not-be-exported";
                }
                """);

        var contribution = new SpringSourceFactsAnalyzer().analyze(
                new AnalysisRequest(AtlasId.of("project:configuration-properties"), project, "revision:test"));

        var attempts = contribution.facts().stream().filter(fact -> fact.kind() == FactKind.CONFIGURATION_PROPERTY)
                .filter(fact -> fact.subject().equals("payment.retry.max-attempts")).findFirst().orElseThrow();
        assertEquals("3", ((AtlasValue.Text) attempts.attributes().get("declaredDefault")).value());
        var token = contribution.facts().stream().filter(fact -> fact.kind() == FactKind.CONFIGURATION_PROPERTY)
                .filter(fact -> fact.subject().equals("payment.retry.access-token")).findFirst().orElseThrow();
        assertEquals("", ((AtlasValue.Text) token.attributes().get("declaredDefault")).value());
        assertTrue(((AtlasValue.Bool) token.attributes().get("declaredDefaultRedacted")).value());
        assertTrue(contribution.facts().stream().anyMatch(fact -> fact.kind() == FactKind.CONFIGURATION_PROPERTY
                && fact.subject().equals("payment.retry")));
    }

    @Test
    void normalizesClassLiteralsInBeanAndClasspathConditionsBeforeScenarioResolution() throws IOException {
        Path source = project.resolve("src/main/java/example/ClassLiteralConditions.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
                package example;

                @Configuration
                @ConditionalOnClass(PaymentGateway.class)
                class GatewayConfiguration {
                    @Bean
                    @ConditionalOnBean(PaymentGateway.class)
                    @ConditionalOnSingleCandidate(PaymentGateway.class)
                    PaymentClient paymentClient() { return null; }
                }
                """);

        var contribution = new SpringSourceFactsAnalyzer().analyze(
                new AnalysisRequest(AtlasId.of("project:class-literals"), project, "revision:test"));

        var subjects = contribution.facts().stream().filter(fact -> fact.kind() == FactKind.BEAN_CONDITION)
                .map(fact -> ((AtlasValue.Text) fact.attributes().get("conditionSubject")).value()).toList();
        assertTrue(subjects.contains("PaymentGateway"));
        assertTrue(subjects.stream().noneMatch(subject -> subject.endsWith(".class")));
    }

    @Test
    void extractsSpringBootCompositionAndTheDefinitionTypeOfFactoryBeans() throws IOException {
        Path source = project.resolve("src/main/java/example/ReadApplication.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
                package example;

                import other.QueryController;
                import other.QueryConfiguration;

                @SpringBootApplication(scanBasePackages = {"example.read", "example.security"})
                @Import({QueryController.class, QueryConfiguration.class})
                class ReadApplication { }

                @Configuration
                class QueryConfiguration {
                    @Bean Object queryClient() { return new Object(); }
                }
                """);

        var contribution = new SpringSourceFactsAnalyzer().analyze(
                new AnalysisRequest(AtlasId.of("project:composition"), project, "revision:test"));

        var composition = contribution.facts().stream().filter(fact -> fact.kind() == FactKind.APPLICATION_COMPOSITION)
                .findFirst().orElseThrow();
        AtlasValue.Values scans = (AtlasValue.Values) composition.attributes().get("componentScanPackages");
        AtlasValue.Values imports = (AtlasValue.Values) composition.attributes().get("importedTypes");
        assertEquals(List.of("example.read", "example.security"), scans.values().stream().map(AtlasValue.Text.class::cast)
                .map(AtlasValue.Text::value).toList());
        assertTrue(imports.values().stream().map(AtlasValue.Text.class::cast).map(AtlasValue.Text::value)
                .anyMatch(type -> type.equals("other.QueryController")));
        var factory = contribution.facts().stream().filter(fact -> fact.kind() == FactKind.BEAN_DEFINITION)
                .filter(fact -> ((AtlasValue.Text) fact.attributes().get("beanName")).value().equals("queryClient"))
                .findFirst().orElseThrow();
        assertEquals("example.QueryConfiguration", ((AtlasValue.Text) factory.attributes().get("definitionType")).value());
    }
}
