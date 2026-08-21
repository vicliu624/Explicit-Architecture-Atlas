# Explicit Architecture Atlas

[![CI](https://github.com/vicliu624/Explicit-Architecture-Atlas/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/vicliu624/Explicit-Architecture-Atlas/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/vicliu624/Explicit-Architecture-Atlas?display_name=tag)](https://github.com/vicliu624/Explicit-Architecture-Atlas/releases)
[![License](https://img.shields.io/github/license/vicliu624/Explicit-Architecture-Atlas)](LICENSE)

Explicit Architecture Atlas turns the explicit design of a domain-driven, event-driven system into role-aware, traceable knowledge for business users, operations staff, and developers.

It is intentionally generic: Atlas is not tied to any particular business repository or industry. The Maven-published modules provide contracts, domain models, analyzers, annotations, processors, plugins, CLI, and server composition; the Web explorer and private runtime workspace remain separate.

- [中文产品说明](readme_cn.md)
- [中文使用 Wiki](docs/wiki/README.md)
- [中文工程与发布文档](docs/README.md)

The repository now provides an executable end-to-end Atlas path: annotations emit a typed intent manifest; Maven, JVM, Spring, Axon, and JPA adapters emit verifiable facts; the resolver compiles them into a versioned Atlas Package with evidence, coverage, and diagnostics; and the shaded CLI persists it into a private Workspace. A controlled Axon runtime overlay supplies projection and process-manager health, while the local HTTP runner and independent Web explorer render the same Package for business, operations, and expert readers. Source archive access remains opt-in, package-scoped, and local-only.

## Release and Maven Central

`0.0.1` is the first released version and is publicly available from [Maven Central](https://search.maven.org/search?q=g%3Aio.github.vicliu624.explicitarchitecture). Standard Maven and Gradle consumers do not need to add a repository or configure credentials.

Import the BOM and select the Atlas modules your project needs:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.github.vicliu624.explicitarchitecture</groupId>
            <artifactId>explicit-architecture-atlas-bom</artifactId>
            <version>0.0.1</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependency>
    <groupId>io.github.vicliu624.explicitarchitecture</groupId>
    <artifactId>explicit-architecture-atlas-annotations</artifactId>
</dependency>
```

For a build-time integration, add `explicit-architecture-atlas-maven-plugin` as a Maven build plugin; for framework-aware extraction, select the corresponding Atlas adapter or starter. The complete module boundaries and examples are documented in [the publishing and consumption guide](deploy/README.md).

GitHub Packages remains a parallel package channel for GitHub-specific workflows, but it requires credentials and is not needed for normal Central consumers.

See [the release notes](CHANGELOG.md), [the complete publishing instructions](deploy/README.md), [contribution rules](CONTRIBUTING.md), and [security reporting policy](SECURITY.md). The project is licensed under the [Apache License 2.0](LICENSE).

Future Maven Central releases remain manual and environment-protected: the `Publish to Maven Central` workflow requires an approved `io.github.vicliu624` Central Portal namespace, a Portal token, and a PGP signing key. The `0.0.1` workflow validates and publishes immutable Central bundles, then resolves a released artifact from a clean local Maven repository.
