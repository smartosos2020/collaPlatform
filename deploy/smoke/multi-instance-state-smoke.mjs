import { execFile, execFileSync } from "node:child_process";
import { promisify } from "node:util";

const execFileAsync = promisify(execFile);
const composeFile = process.env.COLLA_COMPOSE_FILE ?? "deploy/docker-compose.prod.yml";
const composeProject = process.env.COLLA_COMPOSE_PROJECT ?? process.env.COMPOSE_PROJECT_NAME;
const database = process.env.POSTGRES_DB ?? "colla_s02";
const databaseUser = process.env.POSTGRES_USER ?? "colla";
const adminUsername = process.env.COLLA_E2E_ADMIN_USERNAME ?? "admin";
const adminPassword = process.env.COLLA_E2E_ADMIN_PASSWORD ?? "admin123456";
const nonce = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;

function composeContainer(service, includeStopped = false) {
  if (composeProject) {
    return execFileSync(
      "docker",
      [
        "ps",
        includeStopped ? "-aq" : "-q",
        "--filter",
        `label=com.docker.compose.project=${composeProject}`,
        "--filter",
        `label=com.docker.compose.service=${service}`,
      ],
      { encoding: "utf8" },
    ).trim();
  }
  const composeArgs = ["compose"];
  if (composeProject) composeArgs.push("-p", composeProject);
  composeArgs.push("-f", composeFile, "ps", includeStopped ? "-aq" : "-q", service);
  return execFileSync(
    "docker",
    composeArgs,
    { encoding: "utf8" },
  ).trim();
}

function docker(...args) {
  return execFileSync("docker", args, { encoding: "utf8" }).trim();
}

function parseHttp(output) {
  const separator = output.lastIndexOf("\n");
  return {
    body: output.slice(0, separator),
    status: Number(output.slice(separator + 1).trim()),
  };
}

function apiArgs(service, method, path, { token, body, requestId } = {}) {
  const args = [
    "exec",
    composeContainer(service),
    "curl",
    "-sS",
    "--max-time",
    "15",
    "-X",
    method,
    "-w",
    "\n%{http_code}",
    `http://localhost:8080${path}`,
    "-H",
    "Accept: application/json",
  ];
  if (token) args.push("-H", `Authorization: Bearer ${token}`);
  if (requestId) args.push("-H", `X-Colla-Request-Id: ${requestId}`);
  if (body !== undefined) {
    args.push("-H", "Content-Type: application/json", "--data-binary", JSON.stringify(body));
  }
  return args;
}

function api(service, method, path, options = {}) {
  return parseHttp(docker(...apiArgs(service, method, path, options)));
}

async function apiConcurrent(service, method, path, options = {}) {
  const { stdout } = await execFileAsync("docker", apiArgs(service, method, path, options), {
    encoding: "utf8",
    maxBuffer: 1024 * 1024,
  });
  return parseHttp(stdout);
}

function expectStatus(response, expected, label) {
  const statuses = Array.isArray(expected) ? expected : [expected];
  if (!statuses.includes(response.status)) {
    throw new Error(`${label} expected ${statuses.join("/")} but got ${response.status}: ${response.body}`);
  }
}

function json(response) {
  return JSON.parse(response.body || "{}");
}

function login(service, fingerprint) {
  const response = api(service, "POST", "/api/auth/login", {
    requestId: `login-${fingerprint}`,
    body: {
      username: adminUsername,
      password: adminPassword,
      deviceType: "web",
      deviceFingerprint: fingerprint,
      deviceName: "S02 M4 state smoke",
      appVersion: "platform-scale-s02",
    },
  });
  expectStatus(response, 200, `${service} login`);
  return json(response);
}

function sqlScalar(query) {
  return docker(
    "exec",
    composeContainer("postgres"),
    "psql",
    "-U",
    databaseUser,
    "-d",
    database,
    "-tAc",
    query,
  );
}

function sqlLiteral(value) {
  return `'${String(value).replaceAll("'", "''")}'`;
}

function waitForHealth(service, expected = "healthy", timeoutMs = 90000) {
  const id = composeContainer(service, true);
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const status = docker(
      "inspect",
      "--format",
      "{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}",
      id,
    );
    if (status === expected) return;
    Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, 1000);
  }
  throw new Error(`${service} did not become ${expected}`);
}

function stopService(service) {
  docker("stop", composeContainer(service, true));
}

function startService(service) {
  docker("start", composeContainer(service, true));
  waitForHealth(service);
}

for (const service of ["postgres", "redis", "minio", "api-a", "api-b", "worker", "event-gateway"]) {
  waitForHealth(service);
}

const firstSession = login("api-a", `s02-m4-revoke-${nonce}`);
const crossNodeMe = api("api-b", "GET", "/api/auth/me", { token: firstSession.accessToken });
expectStatus(crossNodeMe, 200, "cross-node access token");
const devicesResponse = api("api-b", "GET", "/api/devices", { token: firstSession.accessToken });
expectStatus(devicesResponse, 200, "cross-node devices");
const currentDevice = json(devicesResponse).find((device) => device.current === true);
if (!currentDevice) throw new Error("current device not found");
expectStatus(
  api("api-a", "DELETE", `/api/devices/${currentDevice.id}`, { token: firstSession.accessToken }),
  200,
  "device revocation",
);
expectStatus(
  api("api-b", "GET", "/api/auth/me", { token: firstSession.accessToken }),
  403,
  "cross-node revoked access token",
);
expectStatus(
  api("api-b", "POST", "/api/auth/refresh", { body: { refreshToken: firstSession.refreshToken } }),
  401,
  "cross-node revoked refresh token",
);

const session = login("api-a", `s02-m4-state-${nonce}`);
const token = session.accessToken;
const spaceResponse = api("api-a", "POST", "/api/project-spaces", {
  token,
  requestId: `space-${nonce}`,
  body: {
    spaceKey: `M4${nonce.replaceAll(/[^a-zA-Z0-9]/g, "").slice(-12)}`.toUpperCase(),
    name: `S02 M4 ${nonce}`,
    description: "Multi-instance idempotency fixture",
    visibility: "private",
  },
});
expectStatus(spaceResponse, 200, "project space creation");
const space = json(spaceResponse);
const commandRequestId = `wit-cross-node-${nonce}`;
const typePath = `/api/project-spaces/${space.id}/configuration/types`;
const typeBody = {
  typeKey: `risk_${nonce.replaceAll(/[^a-zA-Z0-9]/g, "").slice(-10).toLowerCase()}`,
  name: "Cross-node risk",
  icon: "shield",
  description: "S02 M4 idempotency fixture",
  sortOrder: 90,
};
const firstType = api("api-a", "POST", typePath, {
  token,
  requestId: commandRequestId,
  body: typeBody,
});
expectStatus(firstType, 200, "first idempotent command");
const replayType = api("api-b", "POST", typePath, {
  token,
  requestId: commandRequestId,
  body: typeBody,
});
expectStatus(replayType, 200, "cross-node idempotent replay");
if (json(firstType).id !== json(replayType).id) {
  throw new Error("cross-node replay returned a different work item type");
}

const concurrentRequestId = `wit-concurrent-${nonce}`;
const concurrentBody = {
  ...typeBody,
  typeKey: `race_${nonce.replaceAll(/[^a-zA-Z0-9]/g, "").slice(-10).toLowerCase()}`,
  name: "Concurrent risk",
  sortOrder: 91,
};
const concurrent = await Promise.all([
  apiConcurrent("api-a", "POST", typePath, {
    token,
    requestId: concurrentRequestId,
    body: concurrentBody,
  }),
  apiConcurrent("api-b", "POST", typePath, {
    token,
    requestId: concurrentRequestId,
    body: concurrentBody,
  }),
]);
if (!concurrent.some((response) => response.status === 200)
    || concurrent.some((response) => ![200, 409].includes(response.status))) {
  throw new Error(`unexpected concurrent idempotency statuses: ${concurrent.map((item) => item.status).join(",")}`);
}
const concurrentReplay = api("api-b", "POST", typePath, {
  token,
  requestId: concurrentRequestId,
  body: concurrentBody,
});
expectStatus(concurrentReplay, 200, "concurrent command replay");

for (const requestId of [commandRequestId, concurrentRequestId]) {
  const literal = sqlLiteral(requestId);
  if (sqlScalar(`select count(*) from project_work_item_type_commands where request_id=${literal}`) !== "1") {
    throw new Error(`command receipt is not unique for ${requestId}`);
  }
  if (sqlScalar(`select count(*) from audit_logs where metadata->>'requestId'=${literal} and action='work_item_type.created'`) !== "1") {
    throw new Error(`audit result is not unique for ${requestId}`);
  }
  if (sqlScalar(`select count(*) from domain_events where payload->>'requestId'=${literal} and event_type='work_item_type.created'`) !== "1") {
    throw new Error(`outbox result is not unique for ${requestId}`);
  }
}

const uploadBytes = Buffer.from(`S02 M4 upload ${nonce}`, "utf8");
const uploadRequest = api("api-a", "POST", "/api/files/upload-url", {
  token,
  requestId: `upload-create-${nonce}`,
  body: {
    fileName: "s02-m4.txt",
    contentType: "text/plain",
    sizeBytes: uploadBytes.length,
  },
});
expectStatus(uploadRequest, 200, "upload URL creation");
const upload = json(uploadRequest);
execFileSync(
  "docker",
  [
    "exec",
    composeContainer("api-a"),
    "curl",
    "-sS",
    "--fail-with-body",
    "-X",
    "PUT",
    upload.uploadUrl,
    "-H",
    "Content-Type: text/plain",
    "--data-binary",
    uploadBytes.toString("utf8"),
  ],
  { stdio: "inherit" },
);
const completeBody = { fileId: upload.uploadId };
const completed = api("api-b", "POST", "/api/files/complete", {
  token,
  requestId: `upload-complete-${nonce}`,
  body: completeBody,
});
expectStatus(completed, 200, "cross-node upload completion");
expectStatus(api("api-a", "GET", `/api/files/${upload.uploadId}`, { token }), 200, "cross-node file metadata");
const duplicateCompletion = api("api-a", "POST", "/api/files/complete", {
  token,
  requestId: `upload-complete-replay-${nonce}`,
  body: completeBody,
});
expectStatus(duplicateCompletion, 200, "duplicate upload completion");
if (json(completed).completedAt !== json(duplicateCompletion).completedAt) {
  throw new Error("duplicate completion changed the completed timestamp");
}

const missingUpload = api("api-a", "POST", "/api/files/upload-url", {
  token,
  requestId: `upload-missing-${nonce}`,
  body: {
    fileName: "missing.bin",
    contentType: "application/octet-stream",
    sizeBytes: 4,
  },
});
expectStatus(missingUpload, 200, "missing-object fixture creation");
expectStatus(
  api("api-b", "POST", "/api/files/complete", {
    token,
    requestId: `upload-missing-complete-${nonce}`,
    body: { fileId: json(missingUpload).uploadId },
  }),
  409,
  "missing uploaded object",
);

const minioFailureUpload = api("api-a", "POST", "/api/files/upload-url", {
  token,
  requestId: `upload-minio-failure-${nonce}`,
  body: {
    fileName: "minio-down.bin",
    contentType: "application/octet-stream",
    sizeBytes: 4,
  },
});
expectStatus(minioFailureUpload, 200, "MinIO failure fixture creation");

stopService("postgres");
try {
  expectStatus(
    api("api-a", "GET", "/actuator/health/readiness"),
    503,
    "PostgreSQL failure readiness",
  );
} finally {
  startService("postgres");
  waitForHealth("api-a");
  waitForHealth("api-b");
}

stopService("redis");
try {
  expectStatus(api("api-a", "GET", "/api/health"), 200, "Redis degraded non-cache API");
} finally {
  startService("redis");
}

stopService("minio");
try {
  expectStatus(api("api-b", "GET", "/api/health"), 200, "MinIO degraded non-file API");
  expectStatus(
    api("api-b", "POST", "/api/files/complete", {
      token,
      requestId: `upload-minio-down-complete-${nonce}`,
      body: { fileId: json(minioFailureUpload).uploadId },
    }),
    503,
    "MinIO unavailable file completion",
  );
} finally {
  startService("minio");
}

expectStatus(api("api-a", "GET", "/api/auth/me", { token }), 200, "post-recovery api-a");
expectStatus(api("api-b", "GET", "/api/auth/me", { token }), 200, "post-recovery api-b");

const logs = [
  docker("logs", "--since", "15m", composeContainer("api-a")),
  docker("logs", "--since", "15m", composeContainer("api-b")),
].join("\n");
if (!logs.includes(commandRequestId) || !logs.includes(concurrentRequestId)) {
  throw new Error("request ids are missing from instance logs");
}
if (logs.includes(session.accessToken) || logs.includes(session.refreshToken)) {
  throw new Error("sensitive token was written to logs");
}

console.log("multi-instance auth, revocation, idempotency, upload, dependency failure, recovery, and audit correlation passed");
