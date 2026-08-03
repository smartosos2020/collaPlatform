/**
 * URL 安全工具。
 *
 * 所有来自用户输入或服务端数据、即将进入 href / src / window.open 的 URL，
 * 必须先经过这里的白名单校验，防止 javascript: / data: 伪协议注入（存储型 XSS）。
 */

const ALLOWED_EXTERNAL_PROTOCOLS = new Set(['http:', 'https:', 'mailto:', 'tel:'])

// 浏览器解析协议前会剥离 ASCII 空白与控制字符（例如 "java\tscript:" 会被当作
// "javascript:" 执行），NBSP 同理。校验前必须做同样的归一化，
// 否则任何前缀检查都能被绕过。这里按码点过滤，避免在源码中写入控制字符。
function normalizeForCheck(value: string): string {
  let normalized = ''
  for (const char of value) {
    const code = char.codePointAt(0) ?? 0
    if (code <= 0x20 || code === 0xa0) {
      continue
    }
    normalized += char
  }
  return normalized
}

/**
 * 校验可用于站外跳转的 URL（http/https/mailto/tel）。
 * 通过时返回原始（trim 后）字符串，否则返回 null。
 */
export function safeExternalHref(value: unknown): string | null {
  if (typeof value !== 'string') {
    return null
  }
  const trimmed = value.trim()
  if (!trimmed) {
    return null
  }
  const normalized = normalizeForCheck(trimmed)
  try {
    const url = new URL(normalized)
    return ALLOWED_EXTERNAL_PROTOCOLS.has(url.protocol) ? trimmed : null
  } catch {
    return null
  }
}

export function isSafeExternalUrl(value: unknown): boolean {
  return safeExternalHref(value) !== null
}

/**
 * 校验站内路径：仅允许以单个 '/' 开头的相对路径
 * （拒绝 '//' 协议相对地址、反斜杠与空串）。通过时返回原路径，否则返回 null。
 */
export function safeInternalPath(value: unknown): string | null {
  if (typeof value !== 'string') {
    return null
  }
  const trimmed = value.trim()
  if (!trimmed.startsWith('/') || trimmed.startsWith('//') || trimmed.includes('\\')) {
    return null
  }
  return trimmed
}

/**
 * 渲染辅助：站内路径或白名单外链返回可用 href，其余返回 null。
 * 调用方在拿到 null 时应按纯文本渲染或禁用跳转。
 */
export function safeHref(value: unknown): string | null {
  return safeInternalPath(value) ?? safeExternalHref(value)
}
