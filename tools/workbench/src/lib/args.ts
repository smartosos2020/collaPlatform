export type CliOptions = Record<string, string | boolean | string[]>

function optionName(value: string): string {
  return value
    .replace(/^-+/, '')
    .replace(/([a-z0-9])([A-Z])/g, '$1-$2')
    .toLowerCase()
}

export function parseCliArgs(values: string[]): { positionals: string[]; options: CliOptions } {
  const positionals: string[] = []
  const options: CliOptions = {}
  for (let index = 0; index < values.length; index += 1) {
    const value = values[index]
    if (!value.startsWith('-')) {
      positionals.push(value)
      continue
    }
    const [rawName, inlineValue] = value.split('=', 2)
    const name = optionName(rawName)
    const next = values[index + 1]
    const parsedValue = inlineValue ?? (next && !next.startsWith('-') ? values[++index] : true)
    const existing = options[name]
    options[name] = existing === undefined
      ? parsedValue
      : Array.isArray(existing) ? [...existing, String(parsedValue)] : [String(existing), String(parsedValue)]
  }
  return { positionals, options }
}

export function optionString(options: CliOptions, name: string, fallback = ''): string {
  const value = options[name]
  return Array.isArray(value) ? value.at(-1) ?? fallback : typeof value === 'string' ? value : fallback
}

export function optionBoolean(options: CliOptions, name: string): boolean {
  const value = options[name]
  return value === true || value === 'true' || value === '1'
}
