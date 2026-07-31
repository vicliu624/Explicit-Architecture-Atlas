[CmdletBinding()]
param()

$repoRoot = Split-Path -Parent $PSScriptRoot
$requiredPaths = @(
    "pom.xml",
    "atlas-parent/pom.xml",
    "atlas-bom/pom.xml",
    "atlas-contract/pom.xml",
    "domain-layer/atlas-domain-model/pom.xml",
    "application-layer/atlas-application-api/pom.xml",
    "infra-driven/atlas-analysis-spi/pom.xml",
    "infra-driving/atlas-annotations/pom.xml",
    "ui-layer/atlas-web/package.json",
    "integration-test/fixture-projects/strict-explicit-java-sample/pom.xml",
    "workspace/atlas-workspace.example.yaml",
    "docs/architecture/01-工程结构与Maven发布.md"
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

Write-Host "工程结构校验通过：$($modules.Count) 个 Maven Reactor 模块，Web 与 Workspace 边界均已存在。"
