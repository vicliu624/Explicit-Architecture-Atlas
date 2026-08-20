import { createReadStream, promises as files } from "node:fs";
import { createServer } from "node:http";
import { dirname, extname, normalize, resolve, sep } from "node:path";
import { fileURLToPath } from "node:url";

const scriptDirectory = dirname(fileURLToPath(import.meta.url));
const webRoot = resolve(scriptDirectory, "..");
const upstream = new URL(process.env.ATLAS_API_BASE ?? "http://127.0.0.1:8080");
const port = Number.parseInt(process.env.ATLAS_WEB_PORT ?? "4173", 10);
const host = process.env.ATLAS_WEB_HOST ?? "127.0.0.1";

if (!Number.isInteger(port) || port < 0 || port > 65535) {
  throw new Error("ATLAS_WEB_PORT 必须是 0 到 65535 之间的端口。");
}

const contentTypes = new Map([
  [".html", "text/html; charset=utf-8"],
  [".css", "text/css; charset=utf-8"],
  [".mjs", "text/javascript; charset=utf-8"],
  [".js", "text/javascript; charset=utf-8"],
  [".json", "application/json; charset=utf-8"],
  [".png", "image/png"],
  [".ico", "image/x-icon"]
]);

const server = createServer(async (request, response) => {
  if (request.method !== "GET" && request.method !== "HEAD") {
    respond(response, 405, "text/plain; charset=utf-8", "仅支持 GET 和 HEAD。", request.method === "HEAD");
    return;
  }
  const url = new URL(request.url ?? "/", `http://${request.headers.host ?? `${host}:${port}`}`);
  if (url.pathname === "/health" || url.pathname.startsWith("/api/")) {
    await proxyApi(request, response, url);
    return;
  }
  await serveStatic(request, response, url.pathname);
});

server.listen(port, host, () => {
  const address = server.address();
  const actualPort = typeof address === "object" && address ? address.port : port;
  console.log(`Atlas Web 已启动：http://${host}:${actualPort}`);
  console.log(`API 代理目标：${upstream.origin}`);
});

async function proxyApi(request, response, requestUrl) {
  const target = new URL(`${requestUrl.pathname}${requestUrl.search}`, upstream);
  try {
    const upstreamResponse = await fetch(target, { method: request.method, headers: { Accept: "application/json" } });
    const body = Buffer.from(await upstreamResponse.arrayBuffer());
    response.statusCode = upstreamResponse.status;
    response.setHeader("Content-Type", upstreamResponse.headers.get("content-type") ?? "application/json; charset=utf-8");
    response.setHeader("X-Content-Type-Options", "nosniff");
    response.setHeader("Cache-Control", "no-store");
    response.end(request.method === "HEAD" ? undefined : body);
  } catch {
    respond(response, 502, "application/json; charset=utf-8", JSON.stringify({
      code: "ATLAS_SERVER_UNAVAILABLE",
      message: `无法连接 Atlas API：${upstream.origin}`
    }), request.method === "HEAD");
  }
}

async function serveStatic(request, response, pathname) {
  let decodedPath;
  try {
    decodedPath = decodeURIComponent(pathname);
  } catch {
    respond(response, 400, "text/plain; charset=utf-8", "请求路径无法解析。", request.method === "HEAD");
    return;
  }
  const relativePath = decodedPath === "/" ? "index.html" : decodedPath.replace(/^[/\\]+/, "");
  const candidate = resolve(webRoot, normalize(relativePath));
  if (!candidate.startsWith(`${webRoot}${sep}`) && candidate !== webRoot) {
    respond(response, 403, "text/plain; charset=utf-8", "不允许访问此路径。", request.method === "HEAD");
    return;
  }
  try {
    const information = await files.stat(candidate);
    if (!information.isFile()) {
      respond(response, 404, "text/plain; charset=utf-8", "未找到页面。", request.method === "HEAD");
      return;
    }
    response.statusCode = 200;
    response.setHeader("Content-Type", contentTypes.get(extname(candidate)) ?? "application/octet-stream");
    response.setHeader("X-Content-Type-Options", "nosniff");
    response.setHeader("Content-Security-Policy", "default-src 'self'; connect-src 'self'; style-src 'self'; script-src 'self'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'");
    response.setHeader("Cache-Control", "no-store");
    if (request.method === "HEAD") {
      response.end();
      return;
    }
    createReadStream(candidate).pipe(response);
  } catch (error) {
    if (error?.code === "ENOENT") {
      respond(response, 404, "text/plain; charset=utf-8", "未找到页面。", request.method === "HEAD");
      return;
    }
    respond(response, 500, "text/plain; charset=utf-8", "无法读取页面资源。", request.method === "HEAD");
  }
}

function respond(response, status, contentType, body, headOnly) {
  response.statusCode = status;
  response.setHeader("Content-Type", contentType);
  response.setHeader("X-Content-Type-Options", "nosniff");
  response.setHeader("Cache-Control", "no-store");
  response.end(headOnly ? undefined : body);
}
