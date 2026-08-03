// 单独抽出 API base URL，让 httpClient 与 sessionRefresh 都能使用而不产生循环依赖。
// import.meta.env 在 Node（单测）环境下不存在，必须防御性读取。
const viteEnv = import.meta.env as { VITE_API_BASE_URL?: string; PROD?: boolean } | undefined

export const API_BASE_URL = viteEnv?.VITE_API_BASE_URL ?? (viteEnv?.PROD ? '/api' : 'http://localhost:8080/api')
