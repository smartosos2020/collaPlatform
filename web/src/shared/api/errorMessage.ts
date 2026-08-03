/** 从 Error 提取面向用户的提示文案；无法识别时回退到 fallback。 */
export function errorMessage(error: unknown, fallback: string) {
  return error instanceof Error ? error.message.replace(/\s*\(\d{3}\)$/, '') : fallback
}
