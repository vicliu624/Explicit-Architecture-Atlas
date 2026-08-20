const app = document.querySelector("#app");
const roles = {
  business: { label: "业务地图", hint: "规则、事件、数据影响与业务接口" },
  operations: { label: "运行态", hint: "处理进度、影响范围与处置建议" },
  expert: { label: "专家模式", hint: "证据、调用路径与受控源码" }
};

const initialApiBase = new URLSearchParams(window.location.search).get("api")
  ?? localStorage.getItem("atlas.apiBase")
  ?? (window.location.protocol.startsWith("http") ? window.location.origin : "");
const initialPackageId = new URLSearchParams(window.location.search).get("package")
  ?? localStorage.getItem("atlas.packageId")
  ?? "";
const initialScenarioId = new URLSearchParams(window.location.search).get("scenario")
  ?? localStorage.getItem("atlas.scenarioId")
  ?? "";

const state = {
  activeRole: "business",
  apiBase: initialApiBase,
  packageId: initialPackageId,
  loading: false,
  overview: null,
  views: { business: null, operations: null, expert: null },
  error: null,
  detail: null,
  source: null,
  scenario: { scenarioId: initialScenarioId, loading: false, data: null, error: null },
  endpointTrace: { endpointId: "", loading: false, data: null, error: null }
};

class AtlasApiError extends Error {
  constructor(message, code = "REQUEST_FAILED") {
    super(message);
    this.code = code;
  }
}

function apiBase(value) {
  return (value ?? "").trim().replace(/\/$/, "");
}

function apiPath(path) {
  return `${apiBase(state.apiBase)}${path}`;
}

function packagePath(suffix) {
  return `/api/v1/packages/${encodeURIComponent(state.packageId)}${suffix}`;
}

async function fetchJson(path) {
  let response;
  try {
    response = await fetch(apiPath(path), { headers: { Accept: "application/json" } });
  } catch (error) {
    throw new AtlasApiError(
      "无法连接 Atlas 服务。请先启动 atlas-server，并通过 npm run dev 打开此页面，或填写可访问的 API 地址。",
      "NETWORK_ERROR"
    );
  }
  let body;
  try {
    body = await response.json();
  } catch (error) {
    throw new AtlasApiError("Atlas 服务返回的不是可读取的 JSON。", "INVALID_RESPONSE");
  }
  if (!response.ok) {
    throw new AtlasApiError(body.message || "Atlas 服务无法完成本次请求。", body.code || "REQUEST_FAILED");
  }
  return body;
}

async function loadAtlas() {
  const packageId = state.packageId.trim();
  if (!packageId) {
    state.error = new AtlasApiError("请输入要打开的 Atlas Package ID，例如 package:etc:current。", "PACKAGE_REQUIRED");
    render();
    return;
  }
  state.loading = true;
  state.error = null;
  state.detail = null;
  state.source = null;
  state.scenario = { ...state.scenario, loading: false, data: null, error: null };
  state.endpointTrace = { endpointId: "", loading: false, data: null, error: null };
  render();
  try {
    const [overview, business, operations, expert] = await Promise.all([
      fetchJson(packagePath("/overview")),
      fetchJson(packagePath("/business")),
      fetchJson(packagePath("/operations")),
      fetchJson(packagePath("/expert"))
    ]);
    state.overview = overview;
    state.views = { business, operations, expert };
    state.apiBase = apiBase(state.apiBase);
    localStorage.setItem("atlas.apiBase", state.apiBase);
    localStorage.setItem("atlas.packageId", packageId);
  } catch (error) {
    state.overview = null;
    state.views = { business: null, operations: null, expert: null };
    state.error = error instanceof AtlasApiError ? error : new AtlasApiError("载入 Atlas 时发生未知错误。", "UNKNOWN_ERROR");
  } finally {
    state.loading = false;
    render();
  }
}

async function openScenario(scenarioId) {
  const normalized = String(scenarioId ?? "").trim();
  if (!normalized) {
    state.scenario = { scenarioId: "", loading: false, data: null, error: new AtlasApiError("请输入 Package 中已声明的场景 ID。", "SCENARIO_REQUIRED") };
    render();
    return;
  }
  state.scenario = { scenarioId: normalized, loading: true, data: null, error: null };
  render();
  try {
    const data = await fetchJson(packagePath(`/scenarios/${encodeURIComponent(normalized)}`));
    state.scenario = { scenarioId: normalized, loading: false, data, error: null };
    localStorage.setItem("atlas.scenarioId", normalized);
  } catch (error) {
    state.scenario = { scenarioId: normalized, loading: false, data: null, error: error instanceof AtlasApiError ? error : new AtlasApiError("无法解析命名场景。") };
  }
  render();
}

async function openAsset(assetId) {
  state.detail = { loading: true, assetId, data: null, error: null };
  state.source = null;
  render();
  try {
    const data = await fetchJson(packagePath(`/assets/${encodeURIComponent(assetId)}`));
    state.detail = { loading: false, assetId, data, error: null };
  } catch (error) {
    state.detail = { loading: false, assetId, data: null, error: error instanceof AtlasApiError ? error : new AtlasApiError("无法读取资产详情。") };
  }
  render();
}

async function openSource(assetId) {
  state.source = { loading: true, data: null, error: null };
  render();
  try {
    const data = await fetchJson(packagePath(`/assets/${encodeURIComponent(assetId)}/source`));
    state.source = { loading: false, data, error: null };
  } catch (error) {
    state.source = { loading: false, data: null, error: error instanceof AtlasApiError ? error : new AtlasApiError("无法读取受控源码。") };
  }
  render();
}

async function openEndpointTrace(endpointId) {
  const normalized = String(endpointId ?? "").trim();
  if (!normalized) return;
  state.endpointTrace = { endpointId: normalized, loading: true, data: null, error: null };
  render();
  try {
    const data = await fetchJson(packagePath(`/endpoints/${encodeURIComponent(normalized)}/trace`));
    state.endpointTrace = { endpointId: normalized, loading: false, data, error: null };
  } catch (error) {
    state.endpointTrace = { endpointId: normalized, loading: false, data: null,
      error: error instanceof AtlasApiError ? error : new AtlasApiError("无法读取端点的完整执行图。") };
  }
  render();
}

function render() {
  app.innerHTML = `
    <header class="site-header">
      <a class="brand" href="#top" aria-label="Explicit Architecture Atlas 首页">
        <span class="brand-mark" aria-hidden="true">EA</span>
        <span><strong>Architecture Atlas</strong><small>显式架构知识地图</small></span>
      </a>
      <p class="header-note">把已证明的架构事实，解释成每个角色都能直接使用的信息。</p>
    </header>
    <main id="top" class="page-shell">
      ${connectionPanel()}
      ${state.error ? errorPanel(state.error) : ""}
      ${state.loading ? loadingPage() : ""}
      ${state.overview && !state.loading ? atlasPage() : ""}
    </main>
    ${state.detail ? detailDrawer() : ""}
  `;
}

function connectionPanel() {
  const loaded = Boolean(state.overview);
  return `
    <section class="connection-panel ${loaded ? "connection-panel--compact" : ""}" aria-labelledby="connection-title">
      <div>
        <p class="eyebrow">本地、安全、只读</p>
        <h1 id="connection-title">${loaded ? escapeHtml(state.overview.projectId) : "打开一份 Atlas Package"}</h1>
        <p>${loaded
          ? `正在阅读 <strong>${escapeHtml(state.overview.packageId)}</strong>。切换角色不会重新推导源码，只会改变解释方式。`
          : "输入 Atlas 服务地址和 Package ID。页面只读取已经编译好的角色化 JSON，不读取业务代码仓库。"}</p>
      </div>
      <form id="connection-form" class="connection-form">
        <label>Atlas API 地址
          <input name="apiBase" value="${escapeAttr(state.apiBase)}" placeholder="默认：由 npm run dev 提供同源代理" autocomplete="url" />
        </label>
        <label>Package ID
          <input name="packageId" value="${escapeAttr(state.packageId)}" placeholder="例如 package:etc:current" required autocomplete="off" />
        </label>
        <button class="button button--primary" type="submit" ${state.loading ? "disabled" : ""}>
          ${state.loading ? "正在读取…" : loaded ? "重新读取" : "打开 Atlas"}
        </button>
      </form>
    </section>`;
}

function errorPanel(error) {
  return `<section class="notice notice--error" role="alert">
    <strong>${escapeHtml(error.code)}</strong>
    <span>${escapeHtml(error.message)}</span>
    <button class="text-button" data-action="dismiss-error">知道了</button>
  </section>`;
}

function loadingPage() {
  return `<section class="loading-page" aria-label="正在读取 Atlas">
    <div class="loading-orbit" aria-hidden="true"></div>
    <h2>正在整理可读的架构地图</h2>
    <p>正在读取业务、运维和专家三种解释视图。</p>
  </section>`;
}

function atlasPage() {
  const summary = state.overview;
  return `
    <section class="atlas-hero" aria-labelledby="atlas-title">
      <div>
        <p class="eyebrow">${escapeHtml(summary.projectId)}</p>
        <h2 id="atlas-title">${escapeHtml(summary.packageId)}</h2>
        <p>来自 <strong>${escapeHtml(summary.sourceRevision)}</strong>，生成于 ${formatDate(summary.generatedAt)}。信息的范围与证据均可追溯。</p>
      </div>
      <div class="summary-stats" aria-label="Atlas 摘要">
        ${statCard(summary.assetCount, "项已解释资产")}
        ${statCard(summary.relationCount, "条已证明关系")}
        ${statCard(summary.diagnosticCount, "条需留意诊断")}
      </div>
    </section>
    ${coverageStrip(summary.coverage)}
    <nav class="role-tabs" aria-label="选择阅读角色">
      ${Object.entries(roles).map(([id, role]) => `
        <button class="role-tab ${state.activeRole === id ? "is-active" : ""}" data-role="${id}" aria-pressed="${state.activeRole === id}">
          <strong>${role.label}</strong><span>${role.hint}</span>
        </button>`).join("")}
    </nav>
    <section class="role-content" aria-label="${roles[state.activeRole].label}">
      ${state.activeRole === "business" ? businessPage(state.views.business) : ""}
      ${state.activeRole === "operations" ? operationsPage(state.views.operations) : ""}
      ${state.activeRole === "expert" ? expertPage(state.views.expert) : ""}
    </section>`;
}

function businessPage(view) {
  return `
    <section class="role-intro role-intro--business">
      <p class="eyebrow">业务地图</p>
      <h2>从业务规则到可见结果</h2>
      <p>这里解释系统必须遵守什么、事实如何发生，以及发生后客户或运营人员最终能看到什么；不要求理解 Java、CQRS 或 Axon。</p>
    </section>
    <section class="section-block" aria-labelledby="rules-title">
      ${sectionHeading("rules-title", "领域规则", `${view.rules.length} 条已明确约束`)}
      <div class="card-grid card-grid--rules">${view.rules.length ? view.rules.map(ruleCard).join("") : emptyState("还没有可展示的领域规则。")}</div>
    </section>
    <section class="section-block" aria-labelledby="events-title">
      ${sectionHeading("events-title", "业务事实如何流动", `${view.events.length} 个领域事件`)}
      <div class="event-stack">${view.events.length ? view.events.map(eventCard).join("") : emptyState("还没有可展示的业务事件。")}</div>
    </section>
    <section class="split-layout">
      <section class="section-block" aria-labelledby="projection-title">
        ${sectionHeading("projection-title", "投影带来的可见结果", `${view.projections.length} 个读模型`)}
        <div class="stack-list">${view.projections.length ? view.projections.map(projectionCard).join("") : emptyState("还没有可展示的投影。")}</div>
      </section>
      <section class="section-block" aria-labelledby="process-title">
        ${sectionHeading("process-title", "跨业务自动流程", `${view.processes.length} 个 Process Manager`)}
        <div class="stack-list">${view.processes.length ? view.processes.map(processCard).join("") : emptyState("还没有可展示的自动流程。")}</div>
      </section>
    </section>
    <section class="section-block" aria-labelledby="endpoint-title">
      ${sectionHeading("endpoint-title", "外部入口", `${view.endpoints.length} 个业务接口`)}
      <div class="endpoint-grid">${view.endpoints.length ? view.endpoints.map(endpointCard).join("") : emptyState("还没有可展示的外部接口。")}</div>
    </section>`;
}

function ruleCard(rule) {
  return `<article class="atlas-card rule-card">
    <div class="card-topline"><span class="kind-chip">${escapeHtml(rule.constraintKind)}</span><span>${escapeHtml(rule.enforcementLevel)}</span></div>
    ${assetTitle(rule.asset, true)}
    <p class="card-description">${escapeHtml(rule.statement)}</p>
    ${rule.expression ? `<code class="constraint-expression">${escapeHtml(rule.expression)}</code>` : ""}
    <p class="subject-line">约束对象：${assetInline(rule.subject)}</p>
    ${narratives(rule.narratives)}
  </article>`;
}

function eventCard(event) {
  const consumerNames = event.consumers.map((consumer) => `${consumer.asset.name} · ${consumer.role}`);
  return `<article class="event-card">
    <div class="event-main">
      <div><p class="eyebrow">业务事实</p>${assetTitle(event.asset, true)}<p>${escapeHtml(event.businessFact)}</p></div>
      <div class="event-flow" aria-label="事件影响流程">
        ${flowGroup("由谁产生", event.producers.map((item) => item.name))}
        <span class="flow-arrow" aria-hidden="true">→</span>
        ${flowGroup("谁会处理", consumerNames)}
        <span class="flow-arrow" aria-hidden="true">→</span>
        ${flowGroup("会更新什么", event.projectionOutputs.flatMap((item) => item.dataTargets.map((target) => target.name)))}
      </div>
    </div>
    <details class="event-details">
      <summary>查看事件携带的信息与可见影响</summary>
      <div class="detail-columns">
        <div><h4>事件携带的信息</h4>${event.payload.length ? `<ul class="plain-list">${event.payload.map((field) => `<li><strong>${escapeHtml(field.name)}</strong><span>${escapeHtml(field.businessMeaning || field.role)}</span></li>`).join("")}</ul>` : "<p class=\"muted\">未声明可展示字段。</p>"}</div>
        <div><h4>产生的读模型</h4>${event.projectionOutputs.length ? `<ul class="plain-list">${event.projectionOutputs.map((output) => `<li><strong>${escapeHtml(output.projection.name)}</strong><span>${escapeHtml(output.purpose)}</span>${assetPills(output.dataTargets)}</li>`).join("")}</ul>` : "<p class=\"muted\">没有直接可见落点。</p>"}</div>
      </div>
      ${narratives(event.narratives)}
    </details>
  </article>`;
}

function projectionCard(projection) {
  return `<article class="atlas-card compact-card">
    ${assetTitle(projection.asset, true)}
    <p>${escapeHtml(projection.purpose)}</p>
    ${labeledAssets("由这些事件驱动", projection.consumedEvents)}
    ${labeledAssets("会更新这些数据", projection.dataTargets)}
    ${narratives(projection.narratives)}
  </article>`;
}

function processCard(process) {
  return `<article class="atlas-card compact-card process-card">
    ${assetTitle(process.asset, true)}
    <p>${escapeHtml(process.responsibility)}</p>
    ${processTimeline(process)}
    ${process.completionCondition ? `<p class="completion-note">完成条件：${escapeHtml(process.completionCondition)}</p>` : ""}
    ${labeledAssets("完成后会发出", process.dispatchedCommands)}
    ${narratives(process.narratives)}
  </article>`;
}

function endpointCard(endpoint) {
  return `<article class="atlas-card endpoint-card">
    <div class="route-line">${endpoint.httpMethods.map((method) => `<span class="http-method">${escapeHtml(method)}</span>`).join("")}<code>${escapeHtml(endpoint.paths.join(" · "))}</code></div>
    ${assetTitle(endpoint.asset, true)}
    <p>${escapeHtml(endpoint.purpose)}</p>
    ${labeledAssets("会触发", endpoint.dispatchedCommands)}
    ${narratives(endpoint.narratives)}
  </article>`;
}

function operationsPage(view) {
  const available = view.runtimeAvailability === "AVAILABLE";
  return `
    <section class="role-intro role-intro--operations">
      <p class="eyebrow">运行态</p>
      <h2>${available ? "把运行结论转成处置动作" : "尚未采集运行态，不做猜测"}</h2>
      <p>${escapeHtml(view.message)}</p>
      <span class="availability-pill ${available ? "availability-pill--available" : "availability-pill--missing"}">${available ? "已接入受控离线观测" : "运行态未采集"}</span>
    </section>
    ${available ? `<section class="health-summary">${healthSummary(view.assets)}</section>` : ""}
    <section class="section-block" aria-labelledby="health-title">
      ${sectionHeading("health-title", "投影与自动流程的最近状态", available ? `${view.assets.length} 个可观测资产` : "等待受控采集")}
      <div class="health-grid">${view.assets.length ? view.assets.map(healthCard).join("") : emptyState("当前 Package 没有 Runtime Overlay。请由运维人员在私有 Workspace 导入脱敏的 Axon 观测后再查看。")}</div>
    </section>
    ${diagnosticsPanel(view.diagnostics, "运行态诊断")}`;
}

function healthSummary(assets) {
  const statuses = ["SUCCEEDED", "FAILED", "LAGGING", "RETRYING", "UNKNOWN"];
  return statuses.map((status) => `<div class="health-stat health-stat--${status.toLowerCase()}"><strong>${assets.filter((asset) => asset.status === status).length}</strong><span>${healthLabel(status)}</span></div>`).join("");
}

function healthCard(health) {
  return `<article class="health-card health-card--${escapeAttr(health.status.toLowerCase())}">
    <div class="card-topline"><span class="status-badge status-badge--${escapeAttr(health.status.toLowerCase())}">${healthLabel(health.status)}</span><span>${health.observedAt ? formatDate(health.observedAt) : "未记录时间"}</span></div>
    ${assetTitle(health.asset, true)}
    <p class="card-description">${escapeHtml(health.summary)}</p>
    ${health.observationType ? `<p class="muted">观测类型：${escapeHtml(health.observationType)}</p>` : ""}
    ${labeledProperties("已脱敏运行信息", health.safeAttributes)}
    ${labeledAssets("可能影响的数据", health.impactedDataTargets)}
    <div class="suggested-action"><strong>建议处理</strong><span>${escapeHtml(health.suggestedAction)}</span></div>
    ${narratives(health.narratives)}
  </article>`;
}

function expertPage(view) {
  return `
    <section class="role-intro role-intro--expert">
      <p class="eyebrow">专家模式</p>
      <h2>回到每一条已证明的关系</h2>
      <p>这里展示静态调用、事件关系和受控定位符。它不是“猜测的架构图”；不存在的边不会被补出来。</p>
    </section>
    <section class="section-block" aria-labelledby="trace-title">
      ${sectionHeading("trace-title", "事件生产、消费与投影落点", `${view.eventTraces.length} 条事件链路`)}
      <div class="trace-stack">${view.eventTraces.length ? view.eventTraces.map(eventTraceCard).join("") : emptyState("还没有可展示的事件追踪关系。")}</div>
    </section>
    <section class="section-block" aria-labelledby="call-title">
      ${sectionHeading("call-title", "接口调用路径", `${view.endpointTraces.length} 个入口`)}
      <div class="trace-stack">${view.endpointTraces.length ? view.endpointTraces.map(endpointTraceCard).join("") : emptyState("还没有可展示的接口调用路径。")}</div>
    </section>
    ${scenarioPanel()}
    <section class="section-block" aria-labelledby="source-title">
      ${sectionHeading("source-title", "可受控查看的源码资产", `${view.sourceBackedAssets.length} 项`)}
      <p class="section-help">源码正文不会随视图传输。点击资产后，只有在本地服务显式开启 source JAR 查看时才会请求正文。</p>
      <div class="source-grid">${view.sourceBackedAssets.length ? view.sourceBackedAssets.map(sourceAssetCard).join("") : emptyState("当前 Package 没有可从受控 source JAR 定位的资产。")}</div>
    </section>
    ${diagnosticsPanel(view.diagnostics, "专家诊断")}`;
}

function scenarioPanel() {
  const scenario = state.scenario;
  return `<section class="section-block scenario-section" aria-labelledby="scenario-title">
    <div class="scenario-heading">
      <div>${sectionHeading("scenario-title", "场景化 Bean 与调用分派", "只读取 Package 内已声明场景")}</div>
      <form id="scenario-form" class="scenario-form">
        <label>场景 ID
          <input name="scenarioId" value="${escapeAttr(scenario.scenarioId)}" placeholder="例如 scenario:production" autocomplete="off" />
        </label>
        <button class="button button--secondary" type="submit" ${scenario.loading ? "disabled" : ""}>${scenario.loading ? "正在解析…" : "解析场景"}</button>
      </form>
    </div>
    <p class="section-help">不会上传 Profile、配置值或路径。场景必须已随 Atlas Package 编译并经过脱敏。</p>
    ${scenario.loading ? `<div class="scenario-panel"><div class="loading-orbit" aria-hidden="true"></div><p>正在按该场景解析组件、注入和调用分派…</p></div>` : ""}
    ${scenario.error ? `<div class="scenario-panel scenario-panel--error"><strong>${escapeHtml(scenario.error.code)}</strong><p>${escapeHtml(scenario.error.message)}</p></div>` : ""}
    ${scenario.data ? scenarioResult(scenario.data) : ""}
  </section>`;
}

function scenarioResult(result) {
  const components = result.components || [];
  const invocations = result.invocations || [];
  const injections = result.injections || [];
  const scenario = result.scenario || {};
  const scenarioName = scenario.name || scenario.metadata?.name || "未命名场景";
  const scenarioId = scenario.id?.value || scenario.id || scenario.metadata?.id?.value || "";
  const active = components.filter((component) => component.status === "ACTIVE").length;
  const unresolved = components.filter((component) => component.status === "UNRESOLVED").length;
  return `<article class="scenario-panel">
    <div class="scenario-result-heading">
      <div><p class="eyebrow">已选择场景</p><h4>${escapeHtml(scenarioName)}</h4><p>${escapeHtml(scenarioId)} · Profile：${escapeHtml((scenario.activeProfiles || []).join("、") || "无")}</p></div>
      <div class="scenario-stats"><span><strong>${active}</strong> 活动 Bean</span><span><strong>${unresolved}</strong> 未决 Bean</span><span><strong>${invocations.length}</strong> 调用边</span></div>
    </div>
    <details open>
      <summary>查看有效调用分派与未决原因</summary>
      <div class="scenario-invocations">${invocations.length ? invocations.map(scenarioInvocationCard).join("") : "<p class=\"muted\">当前 Package 没有可场景化的静态调用边。</p>"}</div>
    </details>
    <details>
      <summary>查看 Bean 注入选择</summary>
      <div class="scenario-injections">${injections.length ? injections.map(scenarioInjectionCard).join("") : "<p class=\"muted\">当前场景没有已识别的注入点。</p>"}</div>
    </details>
    ${result.diagnostics?.length ? diagnosticsPanel(result.diagnostics, "场景诊断") : ""}
  </article>`;
}

function scenarioInvocationCard(invocation) {
  return `<article class="scenario-invocation scenario-invocation--${escapeAttr(invocation.status.toLowerCase())}">
    <div><span class="status-badge scenario-status">${escapeHtml(invocation.status)}</span><strong>${escapeHtml(atlasIdValue(invocation.callerCodeId))}</strong><span aria-hidden="true">→</span><code>${escapeHtml(atlasIdValue(invocation.staticTargetCodeId))}</code></div>
    ${labeledIds("接收者注入", invocation.receiverInjectionPointIds)}
    ${labeledIds("Bean 候选", invocation.candidateComponents)}
    ${labeledIds("选中 Bean", invocation.selectedComponents)}
    ${labeledIds("本地有效实现", invocation.effectiveTargetCodeIds)}
    ${labeledIds("源码声明 Advisor", invocation.declaredAdvisorIds)}
    ${invocation.reasons?.length ? `<p class="scenario-reasons">${invocation.reasons.map(escapeHtml).join("；")}</p>` : ""}
  </article>`;
}

function scenarioInjectionCard(injection) {
  return `<article class="scenario-injection"><div><span class="status-badge scenario-status">${escapeHtml(injection.status)}</span><strong>${escapeHtml(atlasIdValue(injection.injectionPointId))}</strong></div>
    ${labeledIds("候选", injection.candidates)}${labeledIds("选择", injection.selected)}
    ${injection.reasons?.length ? `<p class="scenario-reasons">${injection.reasons.map(escapeHtml).join("；")}</p>` : ""}
  </article>`;
}

function labeledIds(label, ids) {
  return ids?.length ? `<div class="scenario-id-row"><strong>${escapeHtml(label)}</strong><code>${ids.map(atlasIdValue).map(escapeHtml).join(" · ")}</code></div>` : "";
}

function atlasIdValue(value) {
  if (value && typeof value === "object") return String(value.value ?? value.id ?? "");
  return String(value ?? "");
}

function eventTraceCard(trace) {
  return `<article class="trace-card">
    <div class="trace-heading">${assetTitle(trace.event, true)}<p>${escapeHtml(trace.businessFact)}</p></div>
    <details>
      <summary>展开关系证据与投影目的地</summary>
      ${relationGroup("生产关系", trace.productionRelations)}
      ${relationGroup("消费关系", trace.consumptionRelations)}
      <div class="destination-list"><h4>投影目的地</h4>${trace.projectionDestinations.length ? trace.projectionDestinations.map((destination) => `<div>${assetInline(destination.projection)} ${assetPills(destination.dataTargets)} ${relationEvidence(destination.targetRelations)}</div>`).join("") : "<p class=\"muted\">未证明直接投影目的地。</p>"}</div>
    </details>
  </article>`;
}

function endpointTraceCard(trace) {
  const traceState = state.endpointTrace;
  const selected = traceState.endpointId === trace.endpoint.id;
  return `<article class="trace-card">
    <div class="route-line">${trace.httpMethods.map((method) => `<span class="http-method">${escapeHtml(method)}</span>`).join("")}<code>${escapeHtml(trace.paths.join(" · "))}</code></div>
    ${assetTitle(trace.endpoint, true)}
    <details open>
      <summary>查看已证明的调用路径</summary>
      <div class="call-paths">${trace.callPaths.length ? trace.callPaths.map(callPath).join("") : "<p class=\"muted\">没有可安全展示的调用路径。</p>"}</div>
      ${relationGroup("入口的直接效果", trace.directEffects)}
    </details>
    <div class="execution-graph-action">
      <button class="button button--secondary" data-action="endpoint-trace" data-endpoint-id="${escapeAttr(trace.endpoint.id)}" ${selected && traceState.loading ? "disabled" : ""}>${selected && traceState.loading ? "正在展开完整执行图…" : "展开完整执行图"}</button>
      <small>按需读取此入口的所有已证明可达节点与关系；首页不会预先展开其它入口。</small>
    </div>
    ${selected && traceState.error ? `<div class="inline-error"><strong>${escapeHtml(traceState.error.code)}</strong><span>${escapeHtml(traceState.error.message)}</span></div>` : ""}
    ${selected && traceState.data ? executionGraphPanel(traceState.data) : ""}
  </article>`;
}

function executionGraphPanel(trace) {
  const graph = trace.executionGraph || { nodes: [], relations: [] };
  const nodes = graph.nodes || [];
  const relations = graph.relations || [];
  return `<section class="execution-graph-panel">
    <div class="execution-graph-heading"><div><p class="eyebrow">完整执行图</p><h4>${escapeHtml(trace.endpoint?.name || "HTTP 入口")}</h4><p>图中的每一条关系都保留原始事实方向、执行遍历方向和证据；循环不会被路径预览上限截断。</p></div><div class="execution-graph-stats"><span><strong>${nodes.length}</strong> 节点</span><span><strong>${relations.length}</strong> 关系</span></div></div>
    <details open><summary>按实际执行方向查看全部关系</summary>
      <ol class="execution-relation-list">${relations.length ? relations.map(executionRelation).join("") : "<li class=\"muted\">此入口当前没有额外的可达关系。</li>"}</ol>
    </details>
    <details><summary>查看全部节点与源码定位</summary>
      <ol class="execution-node-list">${nodes.map(executionNode).join("")}</ol>
    </details>
  </section>`;
}

function executionRelation(item) {
  const relation = item.relation || {};
  const reverse = item.traversalDirection === "REVERSE";
  const from = reverse ? relation.target : relation.source;
  const to = reverse ? relation.source : relation.target;
  const declared = `${relation.source?.name || "?"} → ${relation.target?.name || "?"}`;
  return `<li><span class="relation-kind">${escapeHtml(relation.kind || "RELATION")}</span>${interactiveAssetInline(from)}<span aria-hidden="true">→</span>${interactiveAssetInline(to)}<small class="execution-direction" title="原始关系：${escapeAttr(declared)}">${reverse ? "反向遍历（用于运行语义）" : "正向遍历"}</small>${relationEvidence([relation])}</li>`;
}

function executionNode(node) {
  return `<li>${interactiveAssetInline(node.asset)}<code>${escapeHtml(node.qualifiedName || node.asset?.kind || "")}</code>${locationHint(node.location)}</li>`;
}

function callPath(path) {
  return `<ol class="call-path">${path.steps.map((step, index) => `<li><span class="call-connector">${index ? escapeHtml(step.relationFromPrevious) : "入口"}</span><strong>${escapeHtml(step.asset.name)}</strong><small>${escapeHtml(step.qualifiedName || step.asset.kind)}</small>${locationHint(step.location)}</li>`).join("")}</ol>`;
}

function sourceAssetCard(source) {
  return `<article class="atlas-card source-card">
    ${assetTitle(source.asset, true)}
    <p class="qualified-name">${escapeHtml(source.qualifiedName)}</p>
    ${locationHint(source.location)}
    <button class="button button--secondary" data-action="asset" data-asset-id="${escapeAttr(source.asset.id)}">查看关系与源码</button>
  </article>`;
}

function detailDrawer() {
  const detail = state.detail;
  return `<div class="drawer-backdrop"><aside class="detail-drawer" role="dialog" aria-modal="true" aria-label="资产详情">
    <button class="drawer-close" data-action="close-detail" aria-label="关闭资产详情">×</button>
    ${detail.loading ? `<div class="drawer-loading"><div class="loading-orbit" aria-hidden="true"></div><p>正在读取资产详情…</p></div>` : ""}
    ${detail.error ? errorPanel(detail.error) : ""}
    ${detail.data ? assetDetail(detail.data) : ""}
  </aside></div>`;
}

function assetDetail(detail) {
  return `<div class="asset-detail">
    <p class="eyebrow">专家资产详情</p>
    ${assetTitle(detail.asset, false)}
    <p>${escapeHtml(detail.asset.summary || "该资产没有额外摘要。")}</p>
    ${detail.source.available ? `<button class="button button--primary" data-action="source" data-asset-id="${escapeAttr(detail.asset.id)}">查看受控源码</button>` : `<p class="source-unavailable">此资产没有可用的受控 source JAR 定位。</p>`}
    ${state.source ? sourcePanel(state.source) : ""}
    ${labeledProperties("资产属性", detail.properties)}
    ${functionContracts(detail.functionContracts)}
    ${narratives(detail.narratives)}
    ${evidenceList(detail.evidence)}
    ${relationGroup("指向此资产的关系", detail.incomingRelations)}
    ${relationGroup("从此资产发出的关系", detail.outgoingRelations)}
    ${runtimeObservationList(detail.runtimeObservations)}
  </div>`;
}

function functionContracts(contracts) {
  if (!contracts?.length) return "";
  return `<section class="function-contracts"><h3>函数契约与值流</h3>${contracts.map(functionContract).join("")}</section>`;
}

function functionContract(contract) {
  return `<article class="function-contract">
    ${interactiveAssetInline(contract.operation)}
    <code>${escapeHtml(contract.javaSignature)}</code>
    ${functionValues("入参", contract.inputs)}
    ${functionValues("返回值", contract.outputs)}
  </article>`;
}

function functionValues(title, values) {
  if (!values?.length) return `<section class="function-value-group"><h4>${escapeHtml(title)}</h4><p class="muted">无。</p></section>`;
  return `<section class="function-value-group"><h4>${escapeHtml(title)}</h4>${values.map(functionValue).join("")}</section>`;
}

function functionValue(value) {
  return `<article class="function-value">
    <div><span class="relation-kind">${escapeHtml(value.role)}</span>${interactiveAssetInline(value.value)}</div>
    ${labeledProperties("值属性", value.properties)}
    ${interactiveRelationGroup("已证明的跨函数值关联", value.associations)}
  </article>`;
}

function sourcePanel(source) {
  if (source.loading) return `<div class="source-panel"><p>正在从受控 source JAR 读取源码…</p></div>`;
  if (source.error) return `<div class="source-panel source-panel--error"><strong>${escapeHtml(source.error.code)}</strong><p>${escapeHtml(source.error.message)}</p></div>`;
  return `<div class="source-panel"><div class="source-meta"><strong>${escapeHtml(source.data.qualifiedName)}</strong><span>${escapeHtml(source.data.archiveEntry)}</span></div><pre><code>${escapeHtml(source.data.content)}</code></pre></div>`;
}

function diagnosticsPanel(diagnostics, title) {
  if (!diagnostics.length) return "";
  return `<section class="diagnostics-panel"><h3>${escapeHtml(title)}</h3><ul>${diagnostics.map((diagnostic) => `<li><strong>${escapeHtml(diagnostic.severity)}</strong><span>${escapeHtml(diagnostic.message)}</span>${locationHint(diagnostic.location)}</li>`).join("")}</ul></section>`;
}

function relationGroup(title, relations) {
  return `<div class="relation-group"><h4>${escapeHtml(title)}</h4>${relations.length ? `<ul class="relation-list">${relations.map((relation) => `<li><span class="relation-kind">${escapeHtml(relation.kind)}</span>${assetInline(relation.source)}<span aria-hidden="true">→</span>${assetInline(relation.target)}${relationEvidence([relation])}</li>`).join("")}</ul>` : "<p class=\"muted\">没有可展示的已证明关系。</p>"}</div>`;
}

function interactiveRelationGroup(title, relations) {
  if (!relations?.length) return "";
  return `<div class="relation-group"><h4>${escapeHtml(title)}</h4><ul class="relation-list">${relations.map((relation) => `<li><span class="relation-kind">${escapeHtml(relation.kind)}</span>${interactiveAssetInline(relation.source)}<span aria-hidden="true">→</span>${interactiveAssetInline(relation.target)}${relationEvidence([relation])}</li>`).join("")}</ul></div>`;
}

function relationEvidence(relations) {
  const count = relations.flatMap((relation) => relation.evidence || []).length;
  return count ? `<span class="evidence-count">${count} 条证据</span>` : "";
}

function evidenceList(evidence) {
  if (!evidence.length) return "";
  return `<section class="evidence-list"><h3>可见证据</h3><ul>${evidence.map((item) => `<li><strong>${escapeHtml(item.claim)}</strong><span>${escapeHtml(item.summary)}</span>${locationHint(item.location)}</li>`).join("")}</ul></section>`;
}

function runtimeObservationList(observations) {
  if (!observations.length) return "";
  return `<section class="runtime-observations"><h3>已脱敏运行态记录</h3>${observations.map((observation) => `<article><div><strong>${escapeHtml(observation.observationType)}</strong><span>${formatDate(observation.observedAt)}</span></div>${labeledProperties("安全属性", observation.safeAttributes)}</article>`).join("")}</section>`;
}

function sectionHeading(id, title, count) {
  return `<div class="section-heading"><div><p class="eyebrow">${escapeHtml(count)}</p><h3 id="${id}">${escapeHtml(title)}</h3></div></div>`;
}

function statCard(value, label) {
  return `<div><strong>${escapeHtml(String(value))}</strong><span>${escapeHtml(label)}</span></div>`;
}

function coverageStrip(coverage) {
  if (!coverage.length) return "";
  return `<section class="coverage-strip" aria-label="当前信息覆盖范围"><strong>信息覆盖范围</strong>${coverage.map((item) => `<span title="${escapeAttr(item.summary)}">${escapeHtml(item.analyzerId)} · ${escapeHtml(item.status)}</span>`).join("")}</section>`;
}

function processTimeline(process) {
  return `<ol class="process-timeline">
    <li><span>开始</span>${assetPills(process.startEvents)}</li>
    <li><span>等待</span>${assetPills(process.waitingEvents)}</li>
    <li><span>完成</span>${assetPills(process.completionEvents)}</li>
  </ol>`;
}

function flowGroup(label, names) {
  return `<div class="flow-group"><small>${escapeHtml(label)}</small>${names.length ? names.map((name) => `<span>${escapeHtml(name)}</span>`).join("") : "<em>尚未明确</em>"}</div>`;
}

function labeledAssets(label, assets) {
  return assets?.length ? `<div class="labeled-row"><strong>${escapeHtml(label)}</strong>${assetPills(assets)}</div>` : "";
}

function assetPills(assets) {
  return assets?.length ? `<span class="asset-pills">${assets.map((asset) => `<span title="${escapeAttr(asset.summary || asset.kind)}">${escapeHtml(asset.name)}</span>`).join("")}</span>` : "<span class=\"muted\">未明确</span>";
}

function labeledProperties(label, properties) {
  return properties?.length ? `<div class="property-list"><strong>${escapeHtml(label)}</strong><dl>${properties.map((property) => `<div><dt>${escapeHtml(property.name)}</dt><dd>${escapeHtml(property.value)}</dd></div>`).join("")}</dl></div>` : "";
}

function narratives(items) {
  if (!items?.length) return "";
  return `<div class="narrative-list">${items.map((item) => `<details><summary>${escapeHtml(item.title)}</summary><p>${escapeHtml(item.explanation)}</p>${item.actionWhenViolated ? `<p><strong>需要处理时：</strong>${escapeHtml(item.actionWhenViolated)}</p>` : ""}</details>`).join("")}</div>`;
}

function assetTitle(asset, compact) {
  return `<div class="asset-title"><span class="asset-kind">${escapeHtml(asset.kind)}</span><${compact ? "h4" : "h2"}>${escapeHtml(asset.name)}</${compact ? "h4" : "h2"}>${asset.summary ? `<p>${escapeHtml(asset.summary)}</p>` : ""}</div>`;
}

function assetInline(asset) {
  return `<span class="asset-inline" title="${escapeAttr(asset.summary || asset.id)}">${escapeHtml(asset.name)}</span>`;
}

function interactiveAssetInline(asset) {
  if (!asset) return `<span class="asset-inline">未解析资产</span>`;
  return `<button class="asset-inline asset-inline--button" data-action="asset" data-asset-id="${escapeAttr(asset.id)}" title="${escapeAttr(asset.summary || asset.id)}">${escapeHtml(asset.name)}</button>`;
}

function locationHint(location) {
  if (!location || !location.uri) return "";
  const range = location.startLine ? `:${location.startLine}${location.endLine && location.endLine !== location.startLine ? `–${location.endLine}` : ""}` : "";
  return `<small class="location-hint" title="${escapeAttr(location.uri)}">${escapeHtml(location.symbol || location.uri)}${escapeHtml(range)}</small>`;
}

function emptyState(message) {
  return `<div class="empty-state"><strong>暂无内容</strong><span>${escapeHtml(message)}</span></div>`;
}

function healthLabel(status) {
  return ({ SUCCEEDED: "已处理", FAILED: "处理失败", LAGGING: "存在积压", RETRYING: "正在重试", UNKNOWN: "无法判断" })[status] || status;
}

function formatDate(value) {
  if (!value) return "未记录";
  const date = new Date(value);
  return Number.isNaN(date.valueOf()) ? escapeHtml(value) : new Intl.DateTimeFormat("zh-CN", { dateStyle: "medium", timeStyle: "short" }).format(date);
}

function escapeHtml(value) {
  return String(value ?? "").replace(/[&<>'"]/g, (character) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", "'": "&#39;", '"': "&quot;" })[character]);
}

function escapeAttr(value) {
  return escapeHtml(value);
}

document.addEventListener("submit", (event) => {
  if (event.target.id === "connection-form") {
    event.preventDefault();
    const values = new FormData(event.target);
    state.apiBase = apiBase(values.get("apiBase"));
    state.packageId = String(values.get("packageId") ?? "").trim();
    loadAtlas();
  }
  if (event.target.id === "scenario-form") {
    event.preventDefault();
    const values = new FormData(event.target);
    openScenario(values.get("scenarioId"));
  }
});

document.addEventListener("click", (event) => {
  const button = event.target.closest("button");
  if (!button) return;
  const role = button.dataset.role;
  const action = button.dataset.action;
  if (role) {
    state.activeRole = role;
    state.detail = null;
    state.source = null;
    state.endpointTrace = { endpointId: "", loading: false, data: null, error: null };
    render();
  }
  if (action === "dismiss-error") {
    state.error = null;
    render();
  }
  if (action === "asset") openAsset(button.dataset.assetId);
  if (action === "source") openSource(button.dataset.assetId);
  if (action === "endpoint-trace") openEndpointTrace(button.dataset.endpointId);
  if (action === "close-detail") {
    state.detail = null;
    state.source = null;
    render();
  }
});

render();
