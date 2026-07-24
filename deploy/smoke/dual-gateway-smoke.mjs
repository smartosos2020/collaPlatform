import { execFileSync } from "node:child_process";

const composeFile = process.env.COLLA_COMPOSE_FILE ?? "deploy/docker-compose.prod.yml";
const composeProject = process.env.COLLA_COMPOSE_PROJECT ?? process.env.COMPOSE_PROJECT_NAME;
const baseUrl = (process.env.COLLA_BASE_URL ?? "http://127.0.0.1").replace(/\/$/, "");
const adminUsername = process.env.COLLA_E2E_ADMIN_USERNAME ?? "admin";
const adminPassword = process.env.COLLA_E2E_ADMIN_PASSWORD ?? "admin123456";
const readinessTimeoutMs = Number(process.env.COLLA_READINESS_TIMEOUT_MS ?? "90000");
const connectionTimeoutMs = Number(process.env.COLLA_GATEWAY_CONNECTION_TIMEOUT_MS ?? "15000");
const gatewayServices = ["event-gateway-a", "event-gateway-b"];

function composeArgs(...args) {
  const command = ["compose"];
  if (composeProject) command.push("-p", composeProject);
  command.push("-f", composeFile, ...args);
  return command;
}

function containerId(service) {
  if (composeProject) {
    return execFileSync(
      "docker",
      [
        "ps",
        "-aq",
        "--filter",
        `label=com.docker.compose.project=${composeProject}`,
        "--filter",
        `label=com.docker.compose.service=${service}`,
      ],
      { encoding: "utf8" },
    ).trim();
  }
  return execFileSync("docker", composeArgs("ps", "-aq", service), { encoding: "utf8" }).trim();
}

function inspect(...args) {
  return execFileSync("docker", ["inspect", ...args], { encoding: "utf8" }).trim();
}

function stopService(service, forced = false) {
  const command = forced ? ["kill", containerId(service)] : ["stop", "--timeout", "30", containerId(service)];
  execFileSync("docker", command, { stdio: "inherit" });
}

function startService(service) {
  execFileSync("docker", ["start", containerId(service)], { stdio: "inherit" });
}

function serviceStatus(service) {
  return inspect(
    "--format",
    "{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}",
    containerId(service),
  );
}

async function waitForService(service) {
  const deadline = Date.now() + readinessTimeoutMs;
  while (Date.now() < deadline) {
    const status = serviceStatus(service);
    if (status === "healthy") return;
    if (status === "exited" || status === "dead") throw new Error(`${service} exited before becoming ready`);
    await sleep(1000);
  }
  throw new Error(`${service} did not become ready within ${readinessTimeoutMs}ms`);
}

async function login() {
  const response = await fetch(`${baseUrl}/api/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json", "X-Colla-Request-Id": `gateway-smoke-${crypto.randomUUID()}` },
    body: JSON.stringify({
      username: adminUsername,
      password: adminPassword,
      deviceType: "web",
      deviceFingerprint: `gateway-smoke-${crypto.randomUUID()}`,
      deviceName: "S04 dual Gateway smoke",
      appVersion: "platform-scale-s04",
    }),
    signal: AbortSignal.timeout(connectionTimeoutMs),
  });
  if (!response.ok) throw new Error(`Gateway smoke login failed: HTTP ${response.status}`);
  const body = await response.json();
  if (!body.accessToken) throw new Error("Gateway smoke login returned no access token");
  return body.accessToken;
}

function websocketUrl(token) {
  const url = new URL(baseUrl);
  url.protocol = url.protocol === "https:" ? "wss:" : "ws:";
  url.pathname = "/ws/events";
  url.search = new URLSearchParams({ token }).toString();
  return url.toString();
}

function openSocket(token) {
  return new Promise((resolve, reject) => {
    const socket = new WebSocket(websocketUrl(token));
    const timer = setTimeout(() => {
      socket.close();
      reject(new Error("WebSocket connection.ready timed out"));
    }, connectionTimeoutMs);
    socket.addEventListener("message", (event) => {
      try {
        const frame = JSON.parse(String(event.data));
        if (frame.type !== "connection.ready") return;
        if (!frame.instanceId) throw new Error("connection.ready returned no Gateway instanceId");
        clearTimeout(timer);
        resolve({ socket, instanceId: frame.instanceId });
      } catch {
        // Ignore unrelated frames until the Gateway readiness frame arrives.
      }
    });
    socket.addEventListener("error", () => {
      clearTimeout(timer);
      reject(new Error("WebSocket handshake failed"));
    }, { once: true });
  });
}

function closeSocket(socket) {
  return new Promise((resolve) => {
    if (socket.readyState === WebSocket.CLOSED) {
      resolve();
      return;
    }
    const timer = setTimeout(resolve, 1000);
    socket.addEventListener("close", () => {
      clearTimeout(timer);
      resolve();
    }, { once: true });
    socket.close();
  });
}

function assertInstances(connections, expected) {
  const actual = new Set(connections.map((connection) => connection.instanceId));
  for (const instanceId of expected) {
    if (!actual.has(instanceId)) {
      throw new Error(`WebSocket distribution did not reach ${instanceId}; observed ${[...actual].join(", ")}`);
    }
  }
}

async function restoreGateways() {
  for (const service of gatewayServices) {
    if (inspect("--format", "{{.State.Status}}", containerId(service)) !== "running") startService(service);
    await waitForService(service);
  }
}

function sleep(milliseconds) {
  return new Promise((resolve) => setTimeout(resolve, milliseconds));
}

try {
  await Promise.all(gatewayServices.map(waitForService));
  const token = await login();

  const distributedSockets = await Promise.all(Array.from({ length: 4 }, () => openSocket(token)));
  assertInstances(distributedSockets, gatewayServices);
  await Promise.all(distributedSockets.map(({ socket }) => closeSocket(socket)));

  stopService("event-gateway-a");
  const gatewayBFallback = await openSocket(token);
  assertInstances([gatewayBFallback], ["event-gateway-b"]);
  await closeSocket(gatewayBFallback.socket);
  startService("event-gateway-a");
  await waitForService("event-gateway-a");

  stopService("event-gateway-b", true);
  const gatewayAFallback = await openSocket(token);
  assertInstances([gatewayAFallback], ["event-gateway-a"]);
  await closeSocket(gatewayAFallback.socket);
  startService("event-gateway-b");
  await waitForService("event-gateway-b");

  console.log("dual Gateway distribution, graceful exit, forced exit, recovery, and single-node fallback passed");
} finally {
  await restoreGateways();
}
