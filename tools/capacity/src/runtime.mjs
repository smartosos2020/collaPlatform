const SECRET_VALUE_KEY = /(?:password|passwd|secret|token|apiKey|accessKey|privateKey|clientSecret|credential|credentials|authorization|cookie)$/i

export function resolveRuntimeEnvironment(input, environment = process.env) {
  if (environment === null || typeof environment !== 'object') {
    throw new TypeError('Runtime environment must be an object')
  }

  return resolveValue(input, environment, new WeakMap(), '$')
}

function resolveValue(value, environment, seen, path) {
  if (Array.isArray(value)) {
    if (seen.has(value)) return seen.get(value)

    const resolved = new Array(value.length)
    seen.set(value, resolved)
    for (let index = 0; index < value.length; index += 1) {
      if (Object.hasOwn(value, index)) {
        resolved[index] = resolveValue(value[index], environment, seen, `${path}[${index}]`)
      }
    }
    return resolved
  }

  if (!isPlainObject(value)) return value
  if (seen.has(value)) return seen.get(value)

  const output = Object.create(Object.getPrototypeOf(value))
  seen.set(value, output)

  const entries = Object.entries(value)
  const references = entries
    .map(([key, reference]) => describeReference(key, reference))
    .filter(Boolean)

  assertSafeKeys(entries, references, path)

  for (const [key, nestedValue] of entries) {
    const reference = references.find((candidate) => candidate.referenceKey === key)
    if (reference) {
      output[reference.resolvedKey] = resolveReference(reference, environment, path)
    } else {
      output[key] = resolveValue(nestedValue, environment, seen, propertyPath(path, key))
    }
  }

  for (const symbol of Object.getOwnPropertySymbols(value)) {
    if (Object.prototype.propertyIsEnumerable.call(value, symbol)) {
      output[symbol] = resolveValue(value[symbol], environment, seen, path)
    }
  }

  return output
}

function describeReference(referenceKey, reference) {
  if (referenceKey.endsWith('Envs')) {
    return {
      kind: 'many',
      reference,
      referenceKey,
      resolvedKey: referenceKey.slice(0, -4),
    }
  }

  if (referenceKey.endsWith('Env')) {
    return {
      kind: 'one',
      reference,
      referenceKey,
      resolvedKey: referenceKey.slice(0, -3),
    }
  }

  return null
}

function assertSafeKeys(entries, references, path) {
  const keys = new Set(entries.map(([key]) => key))
  const resolvedKeys = new Map()

  for (const { referenceKey, resolvedKey } of references) {
    if (!resolvedKey) {
      throw new Error(`Invalid runtime environment reference key at ${path}`)
    }
    if (keys.has(resolvedKey) || resolvedKeys.has(resolvedKey)) {
      throw new Error(`Runtime environment reference collision at ${propertyPath(path, resolvedKey)}`)
    }
    resolvedKeys.set(resolvedKey, referenceKey)
  }

  for (const [key, value] of entries) {
    if (!references.some((reference) => reference.referenceKey === key)
      && SECRET_VALUE_KEY.test(normalizeKey(key))
      && !isPlainObject(value)) {
      throw new Error(`Raw secret value field is not allowed at ${propertyPath(path, key)}`)
    }
  }
}

function resolveReference(reference, environment, path) {
  const referencePath = propertyPath(path, reference.referenceKey)

  if (reference.kind === 'one') {
    if (typeof reference.reference !== 'string' || reference.reference.length === 0) {
      throw new TypeError(`Runtime environment reference must be a non-empty string at ${referencePath}`)
    }
    return readEnvironmentValue(environment, reference.reference, referencePath)
  }

  if (!Array.isArray(reference.reference)
    || reference.reference.some((name) => typeof name !== 'string' || name.length === 0)) {
    throw new TypeError(`Runtime environment references must be non-empty strings at ${referencePath}`)
  }

  return reference.reference.map((name) => readEnvironmentValue(environment, name, referencePath))
}

function readEnvironmentValue(environment, name, path) {
  const value = environment[name]
  if (typeof value !== 'string' || value.trim().length === 0) {
    throw new Error(`Required runtime environment variable is missing or empty at ${path} (${name})`)
  }
  return value
}

function normalizeKey(key) {
  return key.replace(/[-_\s]/g, '')
}

function propertyPath(path, key) {
  return /^[A-Za-z_$][\w$]*$/.test(key)
    ? `${path}.${key}`
    : `${path}[${JSON.stringify(key)}]`
}

function isPlainObject(value) {
  if (value === null || typeof value !== 'object') return false
  const prototype = Object.getPrototypeOf(value)
  return prototype === Object.prototype || prototype === null
}
