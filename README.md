# Explicit Architecture Atlas

Explicit Architecture Atlas turns the explicit design of a domain-driven, event-driven system into role-aware, traceable knowledge for business users, operations staff, and developers.

It is intentionally generic: Atlas is not tied to any particular business repository or industry. The Maven-published modules provide contracts, domain models, analyzers, annotations, processors, plugins, CLI, and server composition; the Web explorer and private runtime workspace remain separate.

- [中文产品说明](readme_cn.md)
- [中文工程与发布文档](docs/README.md)

The current repository has its first executable extraction slice: annotations emit a typed intent manifest; Maven and JDK AST adapters emit verifiable facts; the resolver compiles them into a versioned Atlas Package with coverage/diagnostics; and the shaded CLI persists it into a private Workspace. Spring, Axon, JPA destinations, runtime overlays, and the role-aware Web explorer remain separate forthcoming adapters rather than being represented as false negatives.
