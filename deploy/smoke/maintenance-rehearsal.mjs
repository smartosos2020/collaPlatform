import { execFile, execFileSync } from "node:child_process";
import { promisify } from "node:util";

const execFileAsync = promisify(execFile);
const composeFile = process.env.COLLA_COMPOSE_FILE ?? "deploy/docker-compose.prod.yml";
const composeProject = process.env.COLLA_COMPOSE_PROJECT ?? process.env.COMPOSE_PROJECT_NAME;
const databaseUser = process.env.POSTGRES_USER ?? "colla";
const freshDatabase = `colla_s02_m4_${Date.now()}`;

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

function postgres(...args) {
  return docker("exec", composeContainer("postgres"), ...args);
}

function psql(database, query) {
  return postgres("psql", "-U", databaseUser, "-d", database, "-tAc", query);
}

function maintenanceArgs(database) {
  const sourceContainer = composeContainer("maintenance", true);
  const image = docker("inspect", "--format", "{{.Config.Image}}", sourceContainer);
  const network = docker(
    "inspect",
    "--format",
    "{{range $name, $_ := .NetworkSettings.Networks}}{{$name}}{{end}}",
    sourceContainer,
  );
  const environment = JSON.parse(
    docker("inspect", "--format", "{{json .Config.Env}}", sourceContainer),
  ).filter(
    (entry) => !entry.startsWith("COLLA_DATASOURCE_URL=") && !entry.startsWith("COLLA_INSTANCE_ID="),
  );
  const args = [
    "run",
    "--rm",
    "--network",
    network,
  ];
  for (const entry of environment) args.push("-e", entry);
  args.push(
    "-e",
    `COLLA_DATASOURCE_URL=jdbc:postgresql://postgres:5432/${database}`,
    "-e",
    "COLLA_INSTANCE_ID=maintenance-rehearsal",
    image,
  );
  return args;
}

postgres("createdb", "-U", databaseUser, freshDatabase);
try {
  const runs = await Promise.allSettled([
    execFileAsync("docker", maintenanceArgs(freshDatabase), { encoding: "utf8", maxBuffer: 8 * 1024 * 1024 }),
    execFileAsync("docker", maintenanceArgs(freshDatabase), { encoding: "utf8", maxBuffer: 8 * 1024 * 1024 }),
  ]);
  const failures = runs.filter((run) => run.status === "rejected");
  if (failures.length > 0) {
    throw new Error(`concurrent maintenance failed: ${failures.map((failure) => failure.reason?.stderr ?? failure.reason).join("\n")}`);
  }

  const migrationCount = Number(psql(freshDatabase, "select count(*) from flyway_schema_history where success"));
  if (migrationCount < 65) throw new Error(`expected at least 65 successful migrations, found ${migrationCount}`);
  if (psql(freshDatabase, "select count(*) from users where deleted_at is null") !== "1") {
    throw new Error("concurrent fresh initialization did not create exactly one administrator");
  }
  if (psql(
    freshDatabase,
    "select count(*) from role_assignments ra join roles r on r.id=ra.role_id where r.code='admin' and ra.subject_type='user' and ra.status='active' and (ra.expires_at is null or ra.expires_at > now())",
  ) !== "1") {
    throw new Error("concurrent fresh initialization did not create exactly one active admin assignment");
  }

  execFileSync("docker", maintenanceArgs(freshDatabase), { stdio: "inherit" });
  if (psql(freshDatabase, "select count(*) from users where deleted_at is null") !== "1") {
    throw new Error("maintenance replay created a duplicate administrator");
  }
  console.log(`fresh and concurrent maintenance rehearsal passed with ${migrationCount} migrations`);
} finally {
  postgres(
    "psql",
    "-U",
    databaseUser,
    "-d",
    "postgres",
    "-c",
    `select pg_terminate_backend(pid) from pg_stat_activity where datname='${freshDatabase}' and pid <> pg_backend_pid();`,
  );
  postgres("dropdb", "-U", databaseUser, "--if-exists", freshDatabase);
}
