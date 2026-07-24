import { execFileSync } from "node:child_process";

const composeFile = process.env.COLLA_COMPOSE_FILE ?? "deploy/docker-compose.prod.yml";
const composeProject = process.env.COLLA_COMPOSE_PROJECT ?? process.env.COMPOSE_PROJECT_NAME;
const baseUrl = process.env.COLLA_BASE_URL ?? "http://127.0.0.1";
const requestCount = Number(process.env.COLLA_DUAL_API_REQUESTS ?? "20");
const readinessTimeoutMs = Number(process.env.COLLA_READINESS_TIMEOUT_MS ?? "90000");

function composeArgs(...args) {
  const command = ["compose"];
  if (composeProject) command.push("-p", composeProject);
  command.push("-f", composeFile, ...args);
  return command;
}

function inspect(...args) {
  return execFileSync("docker", ["inspect", ...args], { encoding: "utf8" }).trim();
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
  return execFileSync(
    "docker",
    composeArgs("ps", "-aq", service),
    { encoding: "utf8" },
  ).trim();
}

function startService(service) {
  execFileSync("docker", ["start", containerId(service)], { stdio: "inherit" });
}

function stopService(service) {
  execFileSync("docker", ["stop", "--timeout", "30", containerId(service)], { stdio: "inherit" });
}

function killService(service) {
  execFileSync("docker", ["kill", containerId(service)], { stdio: "inherit" });
}

function serviceLogs(service, since) {
  return execFileSync("docker", ["logs", "--since", since, containerId(service)], {
    encoding: "utf8",
    stdio: "pipe",
  });
}

function sleep(milliseconds) {
  return new Promise((resolve) => setTimeout(resolve, milliseconds));
}

async function healthy(requests = 1) {
  for (let index = 0; index < requests; index += 1) {
    const response = await fetch(`${baseUrl}/api/health`, {
      headers: { Connection: "close", "X-Colla-Request-Id": crypto.randomUUID() },
    });
    if (!response.ok) throw new Error(`health request failed: ${response.status}`);
  }
}

async function waitForHealthy(requests = 1) {
  const startedAt = Date.now();
  let consecutiveSuccesses = 0;
  let lastFailure = "no response";

  while (Date.now() - startedAt < readinessTimeoutMs) {
    try {
      const response = await fetch(`${baseUrl}/api/health`, {
        headers: { Connection: "close", "X-Colla-Request-Id": crypto.randomUUID() },
      });
      if (response.ok) {
        consecutiveSuccesses += 1;
        if (consecutiveSuccesses >= requests) return;
      } else {
        consecutiveSuccesses = 0;
        lastFailure = `HTTP ${response.status}`;
      }
    } catch (error) {
      consecutiveSuccesses = 0;
      lastFailure = error instanceof Error ? error.message : String(error);
    }
    await sleep(500);
  }

  throw new Error(
    `entry health did not reach ${requests} consecutive successes within ${readinessTimeoutMs}ms; last failure: ${lastFailure}`,
  );
}

async function waitForService(service) {
  const startedAt = Date.now();
  while (Date.now() - startedAt < readinessTimeoutMs) {
    const id = containerId(service);
    if (id) {
      const status = inspect("--format", "{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}", id);
      if (status === "healthy") return;
      if (status === "exited" || status === "dead") {
        throw new Error(`${service} exited before becoming ready`);
      }
    }
    await sleep(1000);
  }
  throw new Error(`${service} did not become ready within ${readinessTimeoutMs}ms`);
}

function assertDistribution(logs) {
  for (const service of ["api-a", "api-b"]) {
    const id = containerId(service);
    const address = inspect(
      "--format",
      "{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}:8080",
      id,
    );
    if (!logs.includes(address)) throw new Error(`request distribution missing ${service} at ${address}`);
  }
}

async function restoreApiServices() {
  for (const service of ["api-a", "api-b"]) {
    const status = inspect("--format", "{{.State.Status}}", containerId(service));
    if (status !== "running") startService(service);
    await waitForService(service);
  }
}

try {
  await healthy(requestCount);
  assertDistribution(serviceLogs("nginx", "2m"));

  stopService("api-a");
  await waitForHealthy(5);
  startService("api-a");
  await waitForService("api-a");
  await waitForHealthy(5);

  killService("api-b");
  await waitForHealthy(5);
  startService("api-b");
  await waitForService("api-b");
  await waitForHealthy(requestCount);
  assertDistribution(serviceLogs("nginx", "5m"));

  stopService("api-b");
  await waitForHealthy(5);
  startService("api-b");
  await waitForService("api-b");

  console.log("dual API distribution, graceful exit, forced exit, recovery, and single-node rollback passed");
} finally {
  await restoreApiServices();
}
