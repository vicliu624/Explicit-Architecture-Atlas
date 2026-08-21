[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$RepositoryRoot
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$catalogPath = Join-Path $RepositoryRoot "docs/maven/consumer-catalog.json"
$guidePath = Join-Path $RepositoryRoot "docs/maven/01-Maven-Central-Consumer-Guide.md"
if (-not (Test-Path -LiteralPath $catalogPath)) {
    throw "缺少 Maven 消费者目录：$catalogPath"
}
if (-not (Test-Path -LiteralPath $guidePath)) {
    throw "缺少 Maven Central 消费者指南：$guidePath"
}

$catalog = Get-Content -Raw -LiteralPath $catalogPath | ConvertFrom-Json
if ($catalog.schemaVersion -ne 1) {
    throw "不支持的 Maven 消费者目录版本：$($catalog.schemaVersion)"
}
if ([string]::IsNullOrWhiteSpace($catalog.groupId) -or [string]::IsNullOrWhiteSpace($catalog.version)) {
    throw "Maven 消费者目录必须声明 groupId 与 version"
}

$catalogArtifacts = @($catalog.artifacts)
if ($catalogArtifacts.Count -eq 0) {
    throw "Maven 消费者目录不能为空"
}
$catalogIds = @($catalogArtifacts | ForEach-Object { [string]$_.artifactId })
$duplicateCatalogIds = @($catalogIds | Group-Object | Where-Object { $_.Count -gt 1 } | ForEach-Object Name)
if ($duplicateCatalogIds) {
    throw "Maven 消费者目录包含重复 artifactId：$($duplicateCatalogIds -join ', ')"
}

foreach ($entry in $catalogArtifacts) {
    if ([string]::IsNullOrWhiteSpace($entry.artifactId) -or
        [string]::IsNullOrWhiteSpace($entry.packaging) -or
        [string]::IsNullOrWhiteSpace($entry.role) -or
        @($entry.consumerModes).Count -eq 0) {
        throw "每个 Maven 消费者目录项必须声明 artifactId、packaging、role 和 consumerModes：$($entry | ConvertTo-Json -Compress)"
    }
}

[xml]$reactor = Get-Content -Raw -LiteralPath (Join-Path $RepositoryRoot "pom.xml")
$reactorNamespace = New-Object System.Xml.XmlNamespaceManager($reactor.NameTable)
$reactorNamespace.AddNamespace("m", "http://maven.apache.org/POM/4.0.0")
$reactorGroupId = $reactor.SelectSingleNode("/m:project/m:groupId", $reactorNamespace).InnerText
$reactorVersion = $reactor.SelectSingleNode("/m:project/m:version", $reactorNamespace).InnerText
if ($catalog.groupId -ne $reactorGroupId -or $catalog.version -ne $reactorVersion) {
    throw "Maven 消费者目录坐标必须与根 POM 一致：catalog=$($catalog.groupId):$($catalog.version), reactor=$reactorGroupId`:$reactorVersion"
}

$reactorModules = @($reactor.SelectNodes("/m:project/m:modules/m:module", $reactorNamespace) | ForEach-Object { $_.'#text' })
$publishedPoms = @()
foreach ($modulePath in $reactorModules) {
    $modulePomPath = Join-Path $RepositoryRoot "$modulePath/pom.xml"
    if (-not (Test-Path -LiteralPath $modulePomPath)) {
        throw "Reactor 模块缺少 POM：$modulePath"
    }

    [xml]$modulePom = Get-Content -Raw -LiteralPath $modulePomPath
    $moduleNamespace = New-Object System.Xml.XmlNamespaceManager($modulePom.NameTable)
    $moduleNamespace.AddNamespace("m", "http://maven.apache.org/POM/4.0.0")
    $skipDeployNode = $modulePom.SelectSingleNode("/m:project/m:properties/m:maven.deploy.skip", $moduleNamespace)
    if ($null -ne $skipDeployNode -and $skipDeployNode.InnerText -eq "true") {
        continue
    }

    $artifactNode = $modulePom.SelectSingleNode("/m:project/m:artifactId", $moduleNamespace)
    if ($null -eq $artifactNode) {
        throw "可发布 Reactor 模块缺少 artifactId：$modulePath"
    }
    $packagingNode = $modulePom.SelectSingleNode("/m:project/m:packaging", $moduleNamespace)
    $packaging = if ($null -eq $packagingNode) { "jar" } else { $packagingNode.InnerText }
    $publishedPoms += [pscustomobject]@{
        ArtifactId = $artifactNode.InnerText
        Packaging = $packaging
        ModulePath = $modulePath
    }
}

$publishedIds = @($publishedPoms | ForEach-Object ArtifactId)
$catalogOnly = Compare-Object -ReferenceObject $publishedIds -DifferenceObject $catalogIds -PassThru | Where-Object { $_ -in $catalogIds }
$pomOnly = Compare-Object -ReferenceObject $publishedIds -DifferenceObject $catalogIds -PassThru | Where-Object { $_ -in $publishedIds }
if ($catalogOnly -or $pomOnly) {
    throw "Maven 消费者目录必须与可发布 Reactor 工件一一对应。仅目录：$($catalogOnly -join ', ')；仅 POM：$($pomOnly -join ', ')"
}

foreach ($publishedPom in $publishedPoms) {
    $catalogEntry = $catalogArtifacts | Where-Object { $_.artifactId -eq $publishedPom.ArtifactId }
    if ($catalogEntry.packaging -ne $publishedPom.Packaging) {
        throw "Maven 消费者目录 packaging 与 POM 不一致：$($publishedPom.ArtifactId) catalog=$($catalogEntry.packaging), pom=$($publishedPom.Packaging)"
    }
}

[xml]$bom = Get-Content -Raw -LiteralPath (Join-Path $RepositoryRoot "atlas-bom/pom.xml")
$bomNamespace = New-Object System.Xml.XmlNamespaceManager($bom.NameTable)
$bomNamespace.AddNamespace("m", "http://maven.apache.org/POM/4.0.0")
$bomIds = @($bom.SelectNodes("/m:project/m:dependencyManagement/m:dependencies/m:dependency/m:artifactId", $bomNamespace) | ForEach-Object { $_.'#text' })
$catalogBomIds = @($catalogArtifacts | Where-Object { $_.bomManaged } | ForEach-Object { $_.artifactId })
$catalogBomOnly = Compare-Object -ReferenceObject $bomIds -DifferenceObject $catalogBomIds -PassThru | Where-Object { $_ -in $catalogBomIds }
$pomBomOnly = Compare-Object -ReferenceObject $bomIds -DifferenceObject $catalogBomIds -PassThru | Where-Object { $_ -in $bomIds }
if ($catalogBomOnly -or $pomBomOnly) {
    throw "BOM 管理范围必须与消费者目录一致。仅目录：$($catalogBomOnly -join ', ')；仅 BOM：$($pomBomOnly -join ', ')"
}

foreach ($specialCase in @(
        @{ ArtifactId = "explicit-architecture-atlas-parent"; Mode = "parent"; BomManaged = $false },
        @{ ArtifactId = "explicit-architecture-atlas-bom"; Mode = "bom-import"; BomManaged = $false },
        @{ ArtifactId = "explicit-architecture-atlas-maven-plugin"; Mode = "maven-build-plugin"; BomManaged = $false })) {
    $entry = $catalogArtifacts | Where-Object { $_.artifactId -eq $specialCase.ArtifactId }
    if ($entry.bomManaged -ne $specialCase.BomManaged -or @($entry.consumerModes) -notcontains $specialCase.Mode) {
        throw "消费者目录必须显式记录 $($specialCase.ArtifactId) 的特殊 Maven 使用方式"
    }
}

$guide = Get-Content -Raw -LiteralPath $guidePath
foreach ($artifactId in $catalogIds) {
    if ($guide -notmatch [regex]::Escape($artifactId)) {
        throw "消费者指南没有覆盖已发布工件：$artifactId"
    }
}
foreach ($requiredGuideConcept in @("<parent>", "dependencyManagement", "annotationProcessorPaths", "maven-build-plugin", "plugin dependency", "java -jar")) {
    if ($guide -notmatch [regex]::Escape($requiredGuideConcept)) {
        throw "消费者指南缺少关键 Maven 使用概念：$requiredGuideConcept"
    }
}

Write-Host "Maven 消费者目录校验通过：$($publishedIds.Count) 个可发布工件，$($bomIds.Count) 个 BOM 管理依赖，指南覆盖完整。"
