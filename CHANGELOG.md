# Changelog

All notable changes to Explicit Architecture Atlas are documented in this file.

The project follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and uses [Semantic Versioning](https://semver.org/spec/v2.0.0.html). A release tag is always `v<project.version>`.

## [0.0.1] - 2026-08-21

### Added

- The first Maven-published Atlas module family: parent POM, BOM, contracts, domain model, analysis SPI, JVM/Maven/Spring/Axon/JPA analyzers, annotations, annotation processor, Maven plugin, CLI, starters, HTTP boundary, and server bootstrap.
- Evidence-backed extraction of Java structure, functions, parameters, return values, argument and return-value flows, guard-style business constraints, framework declarations, source anchors, and diagnostics.
- Scenario-aware Spring bean and call resolution. Profiles, common conditional annotations, qualifiers, `@Primary`, collection/optional/provider injection, inheritance, configuration properties, and declared advisors are represented as facts; dynamic cases remain explicitly unresolved.
- Endpoint execution graphs that preserve HTTP routes, ordinary calls, command dispatch/handling, events, projections, persistence/external boundaries, function contracts, and project-owned source retrieval.
- Typed Atlas annotations and an annotation processor for explicit business intent, including domain primitives and domain constraints.
- CLI, HTTP API, Web Explorer, fixture projects, structure validation, and end-to-end integration coverage.
- A complete public Chinese Wiki, including annotations, domain primitives, function contracts, source facts, value flows, Spring scenarios, and Web Explorer usage.

### Verification

- Validated all 21 agreed `etc-business` endpoint traces: 10 Mobile App Authentication endpoints, 10 New Application endpoints, and 1 Available Product query endpoint.
- Verified that the extracted endpoint graphs expose 818 nodes, 840 relationships, 89 constraints, 33 dispatches, 33 command handlers, 169 function/constructor contracts, and 193 readable project-owned source assets.
- Confirmed that the target `etc-business` workspace was analyzed but never modified.

### Release infrastructure

- Added GitHub Packages Maven publication through a tag-driven GitHub Actions workflow.
- Added an environment-protected Maven Central Portal publishing entry point, PGP signing, release input validation, and an empty-local-repository Central smoke test.
- Corrected the publishable Contract and executable CLI/server artifacts so every deployed JAR has a non-empty source/Javadoc surface; shaded executables are their primary Maven artifacts rather than undocumented `all` or `runner` classifiers.
- Added GitHub Release creation, a named CI workflow, Apache-2.0 licensing, release metadata, contribution and security policies, and consumer documentation.

[0.0.1]: https://github.com/vicliu624/Explicit-Architecture-Atlas/releases/tag/v0.0.1
