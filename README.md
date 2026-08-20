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

## Release and Maven packages

`0.0.1` is the first released version. Public Maven modules are published by the tag-driven `Release` workflow to GitHub Packages:

```xml
<repository>
    <id>github</id>
    <url>https://maven.pkg.github.com/vicliu624/Explicit-Architecture-Atlas</url>
</repository>
```

GitHub Packages' Maven registry requires a classic GitHub personal access token with `read:packages`, including for public packages. Add it to your Maven settings without committing it:

```xml
<server>
    <id>github</id>
    <username>YOUR_GITHUB_USERNAME</username>
    <password>${env.GITHUB_PACKAGES_TOKEN}</password>
</server>
```

Then import the BOM or depend on an individual module:

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
    <artifactId>explicit-architecture-atlas-maven-plugin</artifactId>
    <version>0.0.1</version>
</dependency>
```

See [the release notes](CHANGELOG.md), [the complete publishing instructions](deploy/README.md), [contribution rules](CONTRIBUTING.md), and [security reporting policy](SECURITY.md). The project is licensed under the [Apache License 2.0](LICENSE).

The project is also prepared for Maven Central, but Central publishing is intentionally manual and environment-protected: it requires an approved `io.github.vicliu624` Central Portal namespace, Central token credentials, and a PGP primary key. See the Central section in [the publishing instructions](deploy/README.md) before enabling the `Publish to Maven Central` workflow.
