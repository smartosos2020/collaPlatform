#!/usr/bin/env node

import path from "node:path";
import process from "node:process";
import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import {
  defaultConfigPaths,
  readJson,
  validateCapacityConfig
} from "./contract.mjs";
import {
  capturePreflight,
  comparePreflight,
  redactSecrets
} from "./preflight.mjs";
import {
  applySeed,
  cleanCheckSeed,
  cleanupSeed,
  createSeedPlan,
  generateApplySql,
  planSeed,
  verifySeed
} from "./seed.mjs";
import { verifyEvidenceBundle } from "./evidence.mjs";
import { loadScenarioConfig, runCapacityScenario } from "./scenario.mjs";
import { resolveRuntimeEnvironment } from "./runtime.mjs";
import { bootstrapCapacityRuntime } from "./bootstrap.mjs";
import { validateCapacityRunManifest } from "./provenance.mjs";

function parseArgs(tokens) {
  const positional = [];
  const options = {};
  for (let index = 0; index < tokens.length; index += 1) {
    const token = tokens[index];
    if (!token.startsWith("--")) {
      positional.push(token);
      continue;
    }
    const equals = token.indexOf("=");
    if (equals !== -1) {
      options[token.slice(2, equals)] = token.slice(equals + 1);
      continue;
    }
    const key = token.slice(2);
    const next = tokens[index + 1];
    if (!next || next.startsWith("--")) {
      options[key] = true;
    } else {
      options[key] = next;
      index += 1;
    }
  }
  return { positional, options };
}

function printJson(value) {
  process.stdout.write(`${JSON.stringify(value, null, 2)}\n`);
}

function required(options, name) {
  const value = options[name];
  if (typeof value !== "string" || value.length === 0) {
    throw new Error(`--${name} is required`);
  }
  return value;
}

function databaseOption(options) {
  if (options["database-env"]) {
    const value = process.env[options["database-env"]];
    if (!value) {
      throw new Error(`environment variable ${options["database-env"]} is not set`);
    }
    return value;
  }
  return typeof options.database === "string" ? options.database : undefined;
}

function usage() {
  return `Usage:
  colla-capacity contract validate [--contract FILE] [--topology FILE]
  colla-capacity preflight capture --output FILE [--repo-root DIR] [--key-files FILE] [--docker-data-path DIR]
  colla-capacity preflight compare --baseline FILE [--current FILE] [--repo-root DIR]
  colla-capacity seed plan --seed-id ID --output FILE [--sql FILE] [--config FILE]
  colla-capacity seed clean-check --plan FILE [--sql FILE] [--database URL|--database-env NAME]
  colla-capacity seed apply --plan FILE [--sql FILE] [--database URL|--database-env NAME]
  colla-capacity seed verify --plan FILE [--sql FILE] [--database URL|--database-env NAME]
  colla-capacity seed cleanup --plan FILE [--sql FILE] [--database URL|--database-env NAME]
  colla-capacity evidence verify --directory DIR
  colla-capacity scenario run --config FILE --runtime FILE --evidence-dir DIR [--manifest FILE] [--env-file FILE]
  colla-capacity load http|websocket|worker|collaboration --config FILE [--env-file FILE]
`;
}

async function contractCommand(action, options) {
  if (action !== "validate") {
    throw new Error("contract command must be validate");
  }
  const result = await validateCapacityConfig({
    ...(options.contract ? { contract: path.resolve(options.contract) } : {}),
    ...(options.topology ? { topology: path.resolve(options.topology) } : {}),
    ...(options.seed ? { seed: path.resolve(options.seed) } : {})
  });
  printJson(result);
  if (!result.ok) {
    process.exitCode = 2;
  }
}

async function preflightCommand(action, options) {
  if (action === "capture") {
    const manifest = await capturePreflight({
      repoRoot: options["repo-root"],
      output: required(options, "output"),
      keyFiles: options["key-files"],
      dockerDataPath: options["docker-data-path"]
    });
    printJson({
      ok: true,
      output: path.resolve(options.output),
      criticalFingerprint: manifest.criticalFingerprint
    });
    return;
  }
  if (action === "compare") {
    const baseline = await readJson(required(options, "baseline"));
    const current = options.current
      ? await readJson(options.current)
      : await capturePreflight({
        repoRoot: options["repo-root"],
        keyFiles: options["key-files"]
      });
    const result = comparePreflight(baseline, current);
    printJson(result);
    if (!result.ok) {
      process.exitCode = 3;
    }
    return;
  }
  throw new Error("preflight command must be capture or compare");
}

async function resolvePlan(options) {
  if (options.plan) {
    return readJson(options.plan);
  }
  const seedId = required(options, "seed-id");
  const config = await readJson(options.config ?? defaultConfigPaths.seed);
  return createSeedPlan(seedId, config);
}

async function seedCommand(action, options) {
  if (action === "plan") {
    const plan = await planSeed(required(options, "seed-id"), {
      configPath: options.config,
      output: required(options, "output"),
      sqlOutput: options.sql
    });
    printJson({
      ok: true,
      output: path.resolve(options.output),
      sqlOutput: options.sql ? path.resolve(options.sql) : null,
      seedId: plan.seedId,
      checksum: plan.checksum,
      expectedRecordCount: plan.expectedRecordCount
    });
    return;
  }

  const plan = await resolvePlan(options);
  const executionOptions = {
    sqlOutput: options.sql,
    database: databaseOption(options)
  };
  let result;
  if (action === "clean-check") {
    result = await cleanCheckSeed(plan, executionOptions);
  } else if (action === "apply") {
    result = await applySeed(plan, executionOptions);
  } else if (action === "verify") {
    result = await verifySeed(plan, executionOptions);
  } else if (action === "cleanup") {
    result = await cleanupSeed(plan, executionOptions);
  } else {
    throw new Error("seed command must be plan, clean-check, apply, verify, or cleanup");
  }

  const checkedAction = action === "verify" || action === "clean-check";
  if (!result.executed && !options.sql) {
    process.stdout.write(result.sql ?? generateApplySql(plan));
  } else {
    printJson({
      ok: !result.executed || !checkedAction || result.ok === true,
      executed: result.executed,
      ...(!result.executed && action === "verify" ? { verificationPending: true } : {}),
      ...(!result.executed && action === "clean-check" ? { cleanCheckPending: true } : {}),
      sqlOutput: result.sqlOutput,
      ...(result.verification ? { verification: result.verification } : {}),
      ...(result.cleanCheck ? { cleanCheck: result.cleanCheck } : {})
    });
  }
  if (checkedAction && result.executed && !result.ok) {
    process.exitCode = 4;
  }
}

async function evidenceCommand(action, options) {
  if (action !== "verify") {
    throw new Error("evidence command must be verify");
  }
  const result = await verifyEvidenceBundle(required(options, "directory"));
  printJson(result);
  if (!result.ok) {
    process.exitCode = 5;
  }
}

async function scenarioCommand(action, options) {
  if (action !== "run") {
    throw new Error("scenario command must be run");
  }
  if (options["env-file"]) {
    await loadEnvironmentFile(options["env-file"]);
  }
  const config = await loadScenarioConfig(required(options, "config"));
  const runtime = resolveRuntimeEnvironment(
    await readJson(required(options, "runtime")),
    process.env
  );
  const manifestPath = options.manifest ? path.resolve(options.manifest) : null;
  const manifest = manifestPath ? await readJson(manifestPath) : {};
  let manifestVerified = false;
  if (manifestPath) {
    const manifestRoot = path.dirname(manifestPath);
    const evidenceFiles = {};
    for (const check of Object.values(manifest.seedExecution?.checks ?? {})) {
      if (typeof check?.path !== "string") continue;
      const evidencePath = path.resolve(manifestRoot, check.path);
      const relativePath = path.relative(manifestRoot, evidencePath);
      if (!relativePath || relativePath.startsWith("..") || path.isAbsolute(relativePath)) {
        throw new Error(`scenario manifest evidence path escapes its run directory: ${check.path}`);
      }
      evidenceFiles[check.path] = await readFile(evidencePath);
    }
    const validation = validateCapacityRunManifest(manifest, {
      evidenceFiles,
      requireEvidenceFiles: true
    });
    if (!validation.ok) {
      throw new Error(`scenario manifest validation failed: ${validation.errors.join("; ")}`);
    }
    manifestVerified = true;
  }
  const bootstrapped = await bootstrapCapacityRuntime(runtime);
  const result = await runCapacityScenario(config, {
    evidenceDirectory: required(options, "evidence-dir"),
    loaderOptions: bootstrapped.loaders,
    bootstrapSummary: bootstrapped.summary,
    manifest,
    manifestVerified
  });
  printJson(redactSecrets({
    ok: result.run.status === "COMPLETED" && result.summary.conclusion === "Pass",
    run: result.run,
    summary: result.summary,
    errors: result.errors,
    bundle: result.bundle,
    verification: result.verification
  }));
  if (result.run.status !== "COMPLETED" || result.summary.conclusion !== "Pass") {
    process.exitCode = 7;
  }
}

async function loadEnvironmentFile(file) {
  const content = await readFile(path.resolve(file), "utf8");
  for (const rawLine of content.split(/\r?\n/)) {
    const line = rawLine.trim();
    if (!line || line.startsWith("#") || !line.includes("=")) continue;
    const separator = line.indexOf("=");
    const key = line.slice(0, separator).trim();
    const value = line.slice(separator + 1);
    if (key) process.env[key] = value;
  }
}

async function loadCommand(tokens) {
  const validation = await validateCapacityConfig();
  if (!validation.ok) {
    printJson(validation);
    process.exitCode = 2;
    return;
  }
  const [scenario, ...scenarioArgs] = tokens;
  const modules = {
    http: ["./load/http.mjs", "runHttpScenario"],
    websocket: ["./load/websocket.mjs", "runWebSocketScenario"],
    worker: ["./load/worker.mjs", "runWorkerScenario"],
    collaboration: ["./load/collaboration.mjs", "runCollaborationScenario"]
  };
  if (!(scenario in modules)) {
    throw new Error("load scenario must be http, websocket, worker, or collaboration");
  }
  const parsed = parseArgs(scenarioArgs);
  if (parsed.options["env-file"]) {
    await loadEnvironmentFile(parsed.options["env-file"]);
  }
  const config = resolveRuntimeEnvironment(
    await readJson(required(parsed.options, "config")),
    process.env
  );
  const [modulePath, exportName] = modules[scenario];
  const loadModule = await import(modulePath);
  const handler = loadModule[exportName];
  if (typeof handler !== "function") {
    throw new Error(`${modulePath} must export ${exportName}`);
  }
  const result = await handler(config);
  printJson(redactSecrets(result));
  if (result?.ok === false) {
    process.exitCode = 6;
  }
}

export async function main(argv = process.argv.slice(2)) {
  const [group, action, ...rest] = argv;
  if (!group || group === "help" || group === "--help") {
    process.stdout.write(usage());
    return;
  }
  const parsed = parseArgs(rest);
  if (parsed.options.help) {
    process.stdout.write(usage());
    return;
  }
  if (group === "contract") {
    await contractCommand(action, parsed.options);
  } else if (group === "preflight") {
    await preflightCommand(action, parsed.options);
  } else if (group === "seed") {
    await seedCommand(action, parsed.options);
  } else if (group === "evidence") {
    await evidenceCommand(action, parsed.options);
  } else if (group === "scenario") {
    await scenarioCommand(action, parsed.options);
  } else if (group === "load") {
    await loadCommand([action, ...rest].filter((value) => value !== undefined));
  } else {
    throw new Error(`unknown command group: ${group}`);
  }
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main().catch((error) => {
    process.stderr.write(`capacity: ${error.message}\n`);
    process.exitCode = 1;
  });
}
