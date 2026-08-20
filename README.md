# Explicit Architecture Atlas

Explicit Architecture Atlas turns the explicit design of a domain-driven, event-driven system into role-aware, traceable knowledge for business users, operations staff, and developers.

It is intentionally generic: Atlas is not tied to any particular business repository or industry. The Maven-published modules provide contracts, domain models, analyzers, annotations, processors, plugins, CLI, and server composition; the Web explorer and private runtime workspace remain separate.

- [中文产品说明](readme_cn.md)
- [中文使用 Wiki](docs/wiki/README.md)
- [中文工程与发布文档](docs/README.md)

The repository now provides an executable end-to-end Atlas path: annotations emit a typed intent manifest; Maven, JVM, Spring, Axon, and JPA adapters emit verifiable facts; the resolver compiles them into a versioned Atlas Package with evidence, coverage, and diagnostics; and the shaded CLI persists it into a private Workspace. A controlled Axon runtime overlay supplies projection and process-manager health, while the local HTTP runner and independent Web explorer render the same Package for business, operations, and expert readers. Source archive access remains opt-in, package-scoped, and local-only.
