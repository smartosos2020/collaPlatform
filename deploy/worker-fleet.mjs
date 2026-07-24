#!/usr/bin/env node
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import path from "node:path";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const compose = path.join(root, "deploy", "docker-compose.prod.yml");
const args = process.argv.slice(2);
const action = value("--action", "scale");
const target = Number(value("--target", "2"));
const dryRun = args.includes("--dry-run");

if (!["scale", "rollout", "status"].includes(action)) {
  fail("--action must be scale, rollout or status");
}
if (![1, 2].includes(target)) {
  fail("--target must be 1 or 2; capacity above two requires a reviewed connection budget");
}

const commands = [];
if (action === "status") {
  commands.push(["ps", "worker-a", "worker-b"]);
} else if (action === "scale") {
  commands.push(["up", "-d", "worker-a"]);
  commands.push(target === 2 ? ["up", "-d", "worker-b"] : ["stop", "-t", "30", "worker-b"]);
} else {
  commands.push(["up", "-d", "--no-deps", "--force-recreate", "worker-a"]);
  if (target === 2) commands.push(["up", "-d", "--no-deps", "--force-recreate", "worker-b"]);
}

for (const command of commands) {
  const rendered = ["docker", "compose", "-f", compose, ...command];
  console.log(rendered.join(" "));
  if (dryRun) continue;
  const result = spawnSync(rendered[0], rendered.slice(1), { cwd: root, stdio: "inherit" });
  if (result.status !== 0) process.exit(result.status ?? 1);
}

function value(name, fallback) {
  const index = args.indexOf(name);
  return index === -1 ? fallback : args[index + 1];
}

function fail(message) {
  console.error(message);
  process.exit(2);
}
