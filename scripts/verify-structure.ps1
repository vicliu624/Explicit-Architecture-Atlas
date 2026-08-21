[CmdletBinding()]
param()

$repoRoot = Split-Path -Parent $PSScriptRoot
$requiredPaths = @(
    "pom.xml",
    "LICENSE",
    "CHANGELOG.md",
    "CONTRIBUTING.md",
    "SECURITY.md",
    ".github/workflows/ci.yml",
    ".github/workflows/release.yml",
    ".github/workflows/publish-central.yml",
    "atlas-parent/pom.xml",
    "atlas-bom/pom.xml",
    "atlas-contract/pom.xml",
    "atlas-contract/src/main/java/io/github/vicliu624/explicitarchitecture/atlas/contract/package-info.java",
    "atlas-contract/src/main/java/io/github/vicliu624/explicitarchitecture/atlas/contract/AtlasContractModule.java",
    "domain-layer/atlas-domain-model/pom.xml",
    "application-layer/atlas-application-api/pom.xml",
    "infra-driven/atlas-analysis-spi/pom.xml",
    "infra-driving/atlas-annotations/pom.xml",
    "infra-driving/atlas-cli-full/src/main/java/io/github/vicliu624/explicitarchitecture/atlas/clifull/package-info.java",
    "infra-driving/atlas-cli-full/src/main/java/io/github/vicliu624/explicitarchitecture/atlas/clifull/AtlasCliFullModule.java",
    "ui-layer/atlas-web/package.json",
    "integration-test/fixture-projects/strict-explicit-java-sample/pom.xml",
    "workspace/atlas-workspace.example.yaml",
    "docs/architecture/01-工程结构与Maven发布.md",
    "docs/maven/consumer-catalog.json",
    "docs/maven/01-Maven-Central-Consumer-Guide.md",
    "scripts/verify-maven-consumer-catalog.ps1"
)

$missing = $requiredPaths | Where-Object {
    -not (Test-Path -LiteralPath (Join-Path $repoRoot $_))
}
if ($missing) {
    throw "工程结构不完整，缺少：$($missing -join ', ')"
}

[xml]$reactor = Get-Content -Raw -LiteralPath (Join-Path $repoRoot "pom.xml")
$namespace = New-Object System.Xml.XmlNamespaceManager($reactor.NameTable)
$namespace.AddNamespace("m", "http://maven.apache.org/POM/4.0.0")
$modules = @($reactor.SelectNodes("/m:project/m:modules/m:module", $namespace) | ForEach-Object { $_.'#text' })
foreach ($requiredModule in @("atlas-contract", "infra-driving/atlas-annotations", "ui-layer/atlas-server-bootstrap")) {
    if ($modules -notcontains $requiredModule) {
        throw "Reactor 未声明必要模块：$requiredModule"
    }
}

$deploySkip = $reactor.SelectSingleNode("/m:project/m:properties/m:maven.deploy.skip", $namespace)
if ($null -eq $deploySkip -or $deploySkip.InnerText -ne "true") {
    throw "根 Reactor 必须显式跳过 deploy，避免将聚合 POM 作为公共父项发布"
}

[xml]$publishedParent = Get-Content -Raw -LiteralPath (Join-Path $repoRoot "atlas-parent/pom.xml")
$parentNamespace = New-Object System.Xml.XmlNamespaceManager($publishedParent.NameTable)
$parentNamespace.AddNamespace("m", "http://maven.apache.org/POM/4.0.0")
if ($null -ne $publishedParent.SelectSingleNode("/m:project/m:parent", $parentNamespace)) {
    throw "atlas-parent 必须是可独立发布的 Parent POM，不能继承仅用于聚合的根 POM"
}

foreach ($requiredMetadata in @("name", "description", "url", "licenses", "developers", "scm")) {
    if ($null -eq $publishedParent.SelectSingleNode("/m:project/m:$requiredMetadata", $parentNamespace)) {
        throw "atlas-parent 缺少 Maven Central 要求的公开元数据：$requiredMetadata"
    }
}

$githubRepository = $publishedParent.SelectSingleNode("/m:project/m:distributionManagement/m:repository", $parentNamespace)
if ($null -eq $githubRepository -or
    $githubRepository.SelectSingleNode("m:id", $parentNamespace).InnerText -ne "github" -or
    $githubRepository.SelectSingleNode("m:url", $parentNamespace).InnerText -ne "https://maven.pkg.github.com/vicliu624/Explicit-Architecture-Atlas") {
    throw "atlas-parent 必须将 GitHub Packages Maven Registry 声明为默认发布目标"
}

$profileIds = @($publishedParent.SelectNodes("/m:project/m:profiles/m:profile/m:id", $parentNamespace) | ForEach-Object { $_.'#text' })
foreach ($requiredProfile in @("release", "central-release")) {
    if ($profileIds -notcontains $requiredProfile) {
        throw "atlas-parent 缺少发布 Profile：$requiredProfile"
    }
}

$centralPlugin = $publishedParent.SelectSingleNode("/m:project/m:profiles/m:profile[m:id='central-release']/m:build/m:plugins/m:plugin[m:artifactId='central-publishing-maven-plugin']", $parentNamespace)
if ($null -eq $centralPlugin) {
    throw "central-release Profile 必须声明 Central Portal Maven Plugin"
}

foreach ($testOnlyModule in @(
        "integration-test/atlas-architecture-conformance/pom.xml",
        "integration-test/atlas-integration-tests/pom.xml",
        "integration-test/fixture-projects/strict-explicit-java-sample/pom.xml")) {
    [xml]$testOnlyPom = Get-Content -Raw -LiteralPath (Join-Path $repoRoot $testOnlyModule)
    $testOnlyNamespace = New-Object System.Xml.XmlNamespaceManager($testOnlyPom.NameTable)
    $testOnlyNamespace.AddNamespace("m", "http://maven.apache.org/POM/4.0.0")
    $testOnlyDeploySkip = $testOnlyPom.SelectSingleNode("/m:project/m:properties/m:maven.deploy.skip", $testOnlyNamespace)
    if ($null -eq $testOnlyDeploySkip -or $testOnlyDeploySkip.InnerText -ne "true") {
        throw "测试专用模块必须跳过 deploy，不能发布没有公开 API 的空 JAR：$testOnlyModule"
    }
}

foreach ($executableModule in @(
        "infra-driving/atlas-cli/pom.xml",
        "infra-driving/atlas-cli-full/pom.xml",
        "ui-layer/atlas-server-bootstrap/pom.xml")) {
    $executablePomContent = Get-Content -Raw -LiteralPath (Join-Path $repoRoot $executableModule)
    if ($executablePomContent -notmatch "<shadedArtifactAttached>false</shadedArtifactAttached>") {
        throw "可执行发布模块必须以 shaded 主 JAR 替换普通 JAR，不能额外发布没有匹配文档的 classifier：$executableModule"
    }
}

[xml]$bom = Get-Content -Raw -LiteralPath (Join-Path $repoRoot "atlas-bom/pom.xml")
$bomNamespace = New-Object System.Xml.XmlNamespaceManager($bom.NameTable)
$bomNamespace.AddNamespace("m", "http://maven.apache.org/POM/4.0.0")
$bomArtifacts = @($bom.SelectNodes("/m:project/m:dependencyManagement/m:dependencies/m:dependency/m:artifactId", $bomNamespace) | ForEach-Object { $_.'#text' })
foreach ($expectedArtifact in @(
        "explicit-architecture-atlas-domain-model",
        "explicit-architecture-atlas-analysis-spi",
        "explicit-architecture-atlas-annotations",
        "explicit-architecture-atlas-server-bootstrap")) {
    if ($bomArtifacts -notcontains $expectedArtifact) {
        throw "BOM 未管理必要的可发布 JAR：$expectedArtifact"
    }
}

& (Join-Path $PSScriptRoot "verify-maven-consumer-catalog.ps1") -RepositoryRoot $repoRoot

Write-Host "工程结构校验通过：$($modules.Count) 个 Maven Reactor 模块，Web 与 Workspace 边界均已存在。"
