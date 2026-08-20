# Contributing to Explicit Architecture Atlas

## Development baseline

- JDK 21 or newer
- Maven 3.9 or newer
- Node.js 22 or newer for the standalone Web shell check
- PowerShell 7 or newer is recommended for the repository-boundary check

Run the same checks as CI before opening a pull request:

```powershell
mvn -B -ntp clean verify
npm --prefix ui-layer/atlas-web run check
pwsh ./scripts/verify-structure.ps1
```

Keep facts, explicit intent, runtime observations, and role-specific presentation separate. New analyzers must emit evidence, coverage, and diagnostics; they must not silently turn an unavailable capability into a negative architectural conclusion. Keep framework-specific dependencies outside the domain model and annotations modules.

## Pull requests

Use focused commits and explain externally visible changes in `CHANGELOG.md` when they are release-worthy. Include tests for new extraction, resolution, or rendering behavior. Do not commit generated `target/` content, private workspaces, source archives, credentials, or production data.

## Releases

1. Ensure the root Maven version is the intended non-SNAPSHOT version.
2. Add the release entry to `CHANGELOG.md` and run the complete verification set.
3. Merge the release commit into `main`; the `CI` workflow must pass.
4. Create and push an annotated `v<version>` tag that points at the verified release commit.
5. The `Release` workflow verifies that the tag matches Maven's version, publishes public modules to GitHub Packages, then creates the GitHub Release.

The workflow publishes with its short-lived `GITHUB_TOKEN`; no publishing password belongs in the repository. Maven Central uses a separate, environment-protected manual workflow. It requires a verified `io.github.vicliu624` Central Portal namespace, a Central publishing token, and a PGP primary signing key; see [`deploy/README.md`](deploy/README.md) before running it.
