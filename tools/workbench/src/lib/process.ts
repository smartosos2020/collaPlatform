import { spawn, spawnSync, type ChildProcess, type SpawnOptions } from 'node:child_process'

export interface RunOptions {
  cwd?: string
  env?: NodeJS.ProcessEnv
  capture?: boolean
  allowFailure?: boolean
  stdin?: string
  trimOutput?: boolean
}

interface Invocation { executable: string; args: string[] }
function invocation(command: string, args: string[]): Invocation {
  if (process.platform !== 'win32' || !['pnpm', 'npm', 'npx', 'mvn'].includes(command)) return { executable: command, args }
  for (const value of args) if (/[\r\n%!\"]/.test(value)) throw new Error(`Unsafe Windows batch argument for ${command}: ${value}`)
  const quoted = [`${command}.cmd`, ...args.map((value) => `"${value}"`)].join(' ')
  return { executable: process.env.ComSpec || 'cmd.exe', args: ['/d', '/s', '/c', quoted] }
}

export function runSync(command: string, args: string[], options: RunOptions = {}): string {
  const target = invocation(command, args)
  const result = spawnSync(target.executable, target.args, {
    cwd: options.cwd,
    env: { ...process.env, ...options.env },
    encoding: 'utf8',
    input: options.stdin,
    stdio: options.capture === false ? 'inherit' : ['pipe', 'pipe', 'pipe'],
    windowsHide: true,
    windowsVerbatimArguments: process.platform === 'win32' && target.executable === (process.env.ComSpec || 'cmd.exe'),
    maxBuffer: 20 * 1024 * 1024,
  })
  const output = `${result.stdout ?? ''}${result.stderr ?? ''}`
  if ((result.error || result.status !== 0) && !options.allowFailure) {
    throw new Error(`${command} ${args.join(' ')} failed (${result.error?.message ?? result.status ?? result.signal ?? 'unknown'})\n${output.trim()}`)
  }
  return options.trimOutput === false ? output : output.trim()
}

export function run(command: string, args: string[], options: RunOptions = {}): Promise<string> {
  return new Promise((resolve, reject) => {
    const target = invocation(command, args)
    const spawnOptions: SpawnOptions = {
      cwd: options.cwd,
      env: { ...process.env, ...options.env },
      stdio: options.capture ? ['pipe', 'pipe', 'pipe'] : 'inherit',
      windowsHide: true,
      windowsVerbatimArguments: process.platform === 'win32' && target.executable === (process.env.ComSpec || 'cmd.exe'),
    }
    const child = spawn(target.executable, target.args, spawnOptions)
    let output = ''
    child.stdout?.on('data', (chunk) => { output += chunk })
    child.stderr?.on('data', (chunk) => { output += chunk })
    if (options.stdin) child.stdin?.end(options.stdin)
    child.on('error', reject)
    child.on('close', (code) => {
      if (code === 0 || options.allowFailure) resolve(options.trimOutput === false ? output : output.trim())
      else reject(new Error(`${command} ${args.join(' ')} failed (${code})\n${output.trim()}`))
    })
  })
}

export function spawnManaged(command: string, args: string[], options: SpawnOptions = {}): ChildProcess {
  const target = invocation(command, args)
  return spawn(target.executable, target.args, {
    ...options,
    env: { ...process.env, ...options.env },
    windowsHide: options.windowsHide ?? true,
    windowsVerbatimArguments: process.platform === 'win32' && target.executable === (process.env.ComSpec || 'cmd.exe'),
  })
}
