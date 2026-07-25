import { performance } from 'node:perf_hooks'
import { mkdir, writeFile } from 'node:fs/promises'
import { basename, resolve } from 'node:path'

const DANGEROUS_TEMPLATE_KEYS = new Set(['__proto__', 'prototype', 'constructor'])
const TEMPLATE_PATH = '[A-Za-z0-9_$-]+(?:\\.[A-Za-z0-9_$-]+)*'
const WHOLE_TEMPLATE_PATTERN = new RegExp(`^{{\\s*(${TEMPLATE_PATH})\\s*}}$`)
const EMBEDDED_TEMPLATE_PATTERN = new RegExp(`{{\\s*(${TEMPLATE_PATH})\\s*}}`, 'g')

export function resolveTemplate(value, context = {}) {
  try {
    return resolveTemplateValue(value, context, new WeakSet(), true)
  } catch (error) {
    if (error instanceof TemplateResolutionError) throw error
    throw templateError('invalid input')
  }
}

function resolveTemplateValue(value, context, active, allowFunctions) {
  if (typeof value === 'function') {
    if (!allowFunctions) throw templateError('unsupported value')
    if (active.has(value)) throw templateError('circular value')
    active.add(value)
    try {
      const produced = value(context)
      return resolveTemplateValue(produced, context, active, true)
    } catch {
      throw templateError('function failed')
    } finally {
      active.delete(value)
    }
  }
  if (typeof value === 'string') return resolveTemplateString(value, context, active)
  if (value === null || typeof value !== 'object') return value
  if (active.has(value)) throw templateError('circular value')

  if (Array.isArray(value)) {
    validateTemplateArray(value)
    active.add(value)
    try {
      const output = new Array(value.length)
      for (let index = 0; index < value.length; index += 1) {
        if (Object.hasOwn(value, index)) {
          output[index] = resolveTemplateValue(value[index], context, active, true)
        }
      }
      return output
    } finally {
      active.delete(value)
    }
  }

  const prototype = Object.getPrototypeOf(value)
  if (prototype !== Object.prototype && prototype !== null) {
    throw templateError('non-plain object')
  }
  validateTemplateObject(value)
  active.add(value)
  try {
    const output = Object.create(prototype)
    for (const key of Object.keys(value)) {
      output[key] = resolveTemplateValue(value[key], context, active, true)
    }
    return output
  } finally {
    active.delete(value)
  }
}

function resolveTemplateString(value, context, active) {
  const whole = WHOLE_TEMPLATE_PATTERN.exec(value)
  if (whole) return resolveTemplatePath(whole[1], context, active)

  EMBEDDED_TEMPLATE_PATTERN.lastIndex = 0
  let cursor = 0
  let output = ''
  let matched = false
  for (const match of value.matchAll(EMBEDDED_TEMPLATE_PATTERN)) {
    matched = true
    const literal = value.slice(cursor, match.index)
    if (hasTemplateDelimiter(literal)) throw templateError('malformed placeholder')
    output += literal
    output += templateString(resolveTemplatePath(match[1], context, active))
    cursor = match.index + match[0].length
  }
  const remainder = value.slice(cursor)
  if (hasTemplateDelimiter(remainder)) throw templateError('malformed placeholder')
  return matched ? output + remainder : value
}

function resolveTemplatePath(path, context, active) {
  const segments = path.split('.')
  let current = context
  for (const segment of segments) {
    if (DANGEROUS_TEMPLATE_KEYS.has(segment)) throw templateError('dangerous path')
    if ((typeof current !== 'object' || current === null) && typeof current !== 'function') {
      throw templateError('unknown path')
    }
    const descriptor = Object.getOwnPropertyDescriptor(current, segment)
    if (!descriptor) throw templateError('unknown path')
    if (!Object.hasOwn(descriptor, 'value')) throw templateError('unsupported value')
    current = descriptor.value
  }
  return resolveTemplateValue(current, context, active, false)
}

function templateString(value) {
  if (value === null) return 'null'
  if (value === undefined) return 'undefined'
  if (typeof value === 'string') return value
  if (typeof value === 'number' || typeof value === 'boolean' || typeof value === 'bigint') {
    return String(value)
  }
  if (typeof value === 'object') {
    try {
      const serialized = JSON.stringify(value)
      if (serialized !== undefined) return serialized
    } catch {
      // The fixed error below avoids exposing values from serialization failures.
    }
  }
  throw templateError('unsupported interpolation')
}

function validateTemplateObject(value) {
  for (const key of Reflect.ownKeys(value)) {
    if (typeof key !== 'string') throw templateError('unsupported object key')
    if (DANGEROUS_TEMPLATE_KEYS.has(key)) throw templateError('dangerous object key')
    const descriptor = Object.getOwnPropertyDescriptor(value, key)
    if (!descriptor || !Object.hasOwn(descriptor, 'value')) throw templateError('unsupported value')
  }
}

function validateTemplateArray(value) {
  for (const key of Reflect.ownKeys(value)) {
    if (key === 'length') continue
    if (typeof key !== 'string' || !isArrayIndex(key, value.length)) {
      throw templateError('unsupported array key')
    }
    const descriptor = Object.getOwnPropertyDescriptor(value, key)
    if (!descriptor || !Object.hasOwn(descriptor, 'value')) throw templateError('unsupported value')
  }
}

function isArrayIndex(key, length) {
  const index = Number(key)
  return Number.isSafeInteger(index) && index >= 0 && index < length && String(index) === key
}

function hasTemplateDelimiter(value) {
  return value.includes('{{') || value.includes('}}')
}

function templateError(reason) {
  return new TemplateResolutionError(`template resolution failed: ${reason}`)
}

class TemplateResolutionError extends TypeError {}

export function quantile(values, percentile) {
  const numbers = values
    .map(Number)
    .filter(Number.isFinite)
    .sort((left, right) => left - right)
  if (numbers.length === 0) return null

  const probability = percentile > 1 ? percentile / 100 : percentile
  const bounded = Math.min(1, Math.max(0, probability))
  const index = (numbers.length - 1) * bounded
  const lower = Math.floor(index)
  const upper = Math.ceil(index)
  if (lower === upper) return numbers[lower]
  return numbers[lower] + (numbers[upper] - numbers[lower]) * (index - lower)
}

export const percentile = quantile

export function summarizeSamples(samples, selector = 'latencyMs') {
  const select = typeof selector === 'function'
    ? selector
    : (sample) => sample?.[selector]
  const values = samples.map(select).map(Number).filter(Number.isFinite)
  const failures = samples.filter((sample) => sample?.ok === false).length
  const total = values.reduce((sum, value) => sum + value, 0)
  return {
    count: samples.length,
    success: samples.length - failures,
    failure: failures,
    min: values.length ? Math.min(...values) : null,
    max: values.length ? Math.max(...values) : null,
    mean: values.length ? total / values.length : null,
    p50: quantile(values, 0.5),
    p95: quantile(values, 0.95),
    p99: quantile(values, 0.99),
  }
}

export const summarize = summarizeSamples

export function summarizeByOperation(samples) {
  const groups = new Map()
  for (const sample of samples) {
    const key = sample.operation ?? sample.phase ?? 'unknown'
    if (!groups.has(key)) groups.set(key, [])
    groups.get(key).push(sample)
  }
  return Object.fromEntries(
    [...groups.entries()].map(([key, values]) => [key, summarizeSamples(values)]),
  )
}

export function getPath(value, path) {
  if (!path) return value
  return String(path)
    .split('.')
    .reduce((current, key) => current == null ? undefined : current[key], value)
}

export function validateSemantics(body, rules = {}, context = {}) {
  const errors = []
  for (const path of list(rules.requiredPaths ?? rules.required)) {
    const value = getPath(body, path)
    if (value === undefined || value === null) {
      errors.push(`missing required response field: ${path}`)
    }
  }
  for (const path of list(rules.forbiddenPaths ?? rules.forbidden)) {
    if (getPath(body, path) !== undefined) {
      errors.push(`forbidden response field is present: ${path}`)
    }
  }
  for (const [path, expected] of Object.entries(rules.equals ?? {})) {
    if (!deepEqual(getPath(body, path), expected)) {
      errors.push(`response field ${path} did not equal the expected value`)
    }
  }

  const validator = rules.validate ?? rules.predicate
  if (validator) {
    try {
      const result = validator(body, context)
      errors.push(...normalizeValidationResult(result))
    } catch (error) {
      errors.push(`semantic validator threw: ${errorMessage(error)}`)
    }
  }
  return errors
}

export const semanticCheck = validateSemantics

export function validateHttpResponse(response, body, rules = {}, context = {}) {
  const errors = []
  const status = Number(response?.status)
  const permission = rules.permission ?? 'allow'
  const expectedStatus = rules.expectedStatus ?? rules.status

  if (expectedStatus !== undefined) {
    const accepted = typeof expectedStatus === 'function'
      ? expectedStatus(status, body)
      : (Array.isArray(expectedStatus) ? expectedStatus : [expectedStatus]).includes(status)
    if (!accepted) errors.push(`unexpected HTTP status ${status}`)
  } else if (permission === 'deny') {
    if (status !== 401 && status !== 403) {
      errors.push(`permission denial expected HTTP 401 or 403, received ${status}`)
    }
    if (!hasDenialSemantics(body)) {
      errors.push('permission denial response has no error semantics')
    }
  } else if (!(status >= 200 && status < 300)) {
    errors.push(`successful HTTP response expected, received ${status}`)
  }

  errors.push(...validateSemantics(body, rules, { ...context, response, status }))
  return { ok: errors.length === 0, errors }
}

export async function executeHttpRequest(fetchImpl, url, init = {}, rules = {}, context = {}) {
  const clock = context.clock ?? defaultClock
  const started = clock()
  try {
    const response = await fetchImpl(url, init)
    const body = await readResponseBody(response)
    const latencyMs = Math.max(0, clock() - started)
    const validation = validateHttpResponse(response, body, rules, context)
    return {
      ok: validation.ok,
      status: Number(response.status),
      latencyMs,
      body,
      errors: validation.errors,
    }
  } catch (error) {
    const aborted = init.signal?.aborted === true || isAbortError(error)
    return {
      ok: false,
      aborted,
      status: null,
      latencyMs: Math.max(0, clock() - started),
      body: undefined,
      errors: [aborted ? 'request aborted' : `request failed: ${errorMessage(error)}`],
    }
  }
}

export function createError(code, message, details = {}) {
  return { code, message, ...details }
}

export function addErrors(target, messages, details = {}) {
  for (const message of messages) {
    target.push(createError(details.code ?? 'semantic_failure', message, details))
  }
}

export function createScenarioResult(name, startedAt, samples, errors, metrics = {}, clock = defaultClock) {
  const finishedAt = clock()
  const durationMs = Math.max(0, finishedAt - startedAt)
  const wallFinishedAt = Date.now()
  const aborted = metrics.aborted === true ||
    samples.some((sample) => sample?.aborted === true) ||
    errors.some((error) => error?.code === 'aborted')
  return {
    scenario: name,
    ok: !aborted && errors.length === 0 && samples.every((sample) => sample.ok !== false),
    aborted,
    startedAt: new Date(wallFinishedAt - durationMs).toISOString(),
    finishedAt: new Date(wallFinishedAt).toISOString(),
    durationMs,
    samples,
    summary: {
      overall: summarizeSamples(samples),
      operations: summarizeByOperation(samples),
    },
    metrics,
    errors,
  }
}

export async function finalizeScenarioResult(result, outputDir, fileName) {
  if (!outputDir) return result
  const safeName = basename(fileName ?? `${result.scenario}-result.json`)
  const outputPath = resolve(outputDir, safeName)
  try {
    await mkdir(resolve(outputDir), { recursive: true })
    result.outputPath = outputPath
    await writeFile(outputPath, `${JSON.stringify(result, null, 2)}\n`, 'utf8')
  } catch (error) {
    delete result.outputPath
    result.ok = false
    result.errors.push(createError('output_failure', `failed to write scenario output: ${errorMessage(error)}`))
  }
  return result
}

export async function runWithConcurrency(items, concurrency, handler, options = {}) {
  const values = Array.from(items)
  const width = Math.max(1, Math.min(values.length || 1, Number(concurrency) || 1))
  const signal = options.signal
  let cursor = 0
  const results = new Array(values.length)
  await Promise.all(Array.from({ length: width }, async () => {
    while (cursor < values.length && !signal?.aborted) {
      const index = cursor
      cursor += 1
      results[index] = await handler(values[index], index)
    }
  }))
  return results
}

export function defaultClock() {
  return performance.now()
}

export function delay(milliseconds, signal) {
  if (signal?.aborted) return Promise.reject(createAbortError(signal.reason))
  return new Promise((resolve, reject) => {
    const timer = setTimeout(finish, Math.max(0, milliseconds))
    const onAbort = () => finish(createAbortError(signal.reason))
    signal?.addEventListener('abort', onAbort, { once: true })

    function finish(error) {
      clearTimeout(timer)
      signal?.removeEventListener('abort', onAbort)
      if (error) reject(error)
      else resolve()
    }
  })
}

export async function waitForDelay(milliseconds, options = {}) {
  const signal = options.signal
  const sleep = options.sleep ?? delay
  if (sleep === delay) return delay(milliseconds, signal)
  if (signal?.aborted) throw createAbortError(signal.reason)

  let onAbort
  const sleeping = Promise.resolve().then(() => sleep(Math.max(0, milliseconds), signal))
  const aborted = new Promise((_, reject) => {
    onAbort = () => reject(createAbortError(signal.reason))
    signal?.addEventListener('abort', onAbort, { once: true })
  })
  try {
    return await Promise.race([sleeping, aborted])
  } finally {
    signal?.removeEventListener('abort', onAbort)
  }
}

export async function runRateSchedule(options) {
  const clock = options.clock ?? defaultClock
  const signal = options.signal
  const ratePerSecond = Number(options.ratePerSecond)
  if (!(ratePerSecond > 0)) throw new TypeError('ratePerSecond must be greater than zero')

  const durationMs = Math.max(0, Number(options.durationMs) || 0)
  const maxItems = options.maxItems === undefined ? Infinity : Math.max(0, Number(options.maxItems) || 0)
  const concurrency = Math.max(1, Number(options.concurrency) || 1)
  const intervalMs = 1_000 / ratePerSecond
  const startedAt = clock()
  const deadline = durationMs > 0 ? startedAt + durationMs : Infinity
  const inFlight = new Set()
  let nextAt = startedAt
  let scheduled = 0

  while (scheduled < maxItems && nextAt < deadline && !signal?.aborted) {
    const waitMs = Math.max(0, nextAt - clock())
    if (waitMs > 0) {
      try {
        await waitForDelay(waitMs, { signal, sleep: options.sleep })
      } catch (error) {
        if (isAbortError(error)) break
        throw error
      }
    }
    while (inFlight.size >= concurrency && !signal?.aborted) {
      await Promise.race(inFlight)
    }
    if (signal?.aborted || clock() >= deadline) break

    const index = scheduled
    const launchedAt = clock()
    const task = Promise.resolve()
      .then(() => options.handler(index, { launchedAt, startedAt, signal }))
      .finally(() => inFlight.delete(task))
    inFlight.add(task)
    scheduled += 1
    nextAt = Math.max(startedAt + scheduled * intervalMs, launchedAt + intervalMs)
  }

  await Promise.allSettled(inFlight)
  if (durationMs > 0 && scheduled < maxItems && !signal?.aborted && clock() < deadline) {
    try {
      await waitForDelay(deadline - clock(), { signal, sleep: options.sleep })
    } catch (error) {
      if (!isAbortError(error)) throw error
    }
  }
  return {
    scheduled,
    durationMs: Math.max(0, clock() - startedAt),
    targetRatePerSecond: ratePerSecond,
    aborted: signal?.aborted === true,
  }
}

export function createAbortError(reason) {
  const error = new Error(reason == null ? 'operation aborted' : String(reason))
  error.name = 'AbortError'
  return error
}

export function isAbortError(error) {
  return error?.name === 'AbortError' || error?.code === 'ABORT_ERR'
}

export function addAbortError(errors, signal, details = {}) {
  if (!signal?.aborted || errors.some((error) => error?.code === 'aborted')) return
  errors.push(createError('aborted', 'scenario aborted', {
    ...details,
    reason: signal.reason == null ? undefined : String(signal.reason),
  }))
}

export function stableStringify(value) {
  if (value === undefined) return 'undefined'
  if (value === null || typeof value !== 'object') return JSON.stringify(value)
  if (Array.isArray(value)) return `[${value.map(stableStringify).join(',')}]`
  return `{${Object.keys(value).sort().map((key) => `${JSON.stringify(key)}:${stableStringify(value[key])}`).join(',')}}`
}

export function errorMessage(error) {
  return error instanceof Error ? error.message : String(error)
}

async function readResponseBody(response) {
  if (response?.status === 204 || response?.status === 205) return undefined
  if (typeof response?.text === 'function') {
    const text = await response.text()
    if (!text) return undefined
    try {
      return JSON.parse(text)
    } catch {
      return text
    }
  }
  if (typeof response?.json === 'function') return response.json()
  return response?.body
}

function normalizeValidationResult(result) {
  if (result === undefined || result === true) return []
  if (result === false) return ['custom semantic validation failed']
  if (typeof result === 'string') return [result]
  if (Array.isArray(result)) return result.filter(Boolean).map(String)
  if (result && typeof result === 'object') {
    if (result.ok === true) return []
    if (Array.isArray(result.errors)) return result.errors.filter(Boolean).map(String)
    if (result.message) return [String(result.message)]
  }
  return ['custom semantic validation returned an invalid result']
}

function hasDenialSemantics(body) {
  if (typeof body === 'string') return body.trim().length > 0
  return body && typeof body === 'object' &&
    ['code', 'error', 'message', 'type'].some((key) => typeof body[key] === 'string' && body[key].length > 0)
}

function deepEqual(left, right) {
  return stableStringify(left) === stableStringify(right)
}

function list(value) {
  if (value === undefined || value === null) return []
  return Array.isArray(value) ? value : [value]
}
