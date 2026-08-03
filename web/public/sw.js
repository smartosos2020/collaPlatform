const CACHE_NAME = 'colla-pwa-v1'
const APP_SHELL = ['/', '/offline.html', '/manifest.webmanifest', '/pwa-icon.svg', '/favicon.svg']
// 运行时缓存上限：超出时按 cache.keys() 返回顺序（近似 FIFO，最早 put 在前）淘汰最旧的条目。
const RUNTIME_CACHE_LIMIT = 100
// 运行时缓存只覆盖静态资产：/assets/ 下的构建产物或常见静态扩展名文件。
// 导航请求与 /api/ 响应不进入运行时缓存，错误响应（非 response.ok）也绝不写入，
// 避免 404/500 错误页被持久缓存。
const STATIC_FILE_PATTERN = /\.(?:js|css|png|jpe?g|gif|svg|webp|avif|ico|woff2?|ttf|otf|eot|map|json|webmanifest)$/i

function isCacheableAsset(url, request) {
  if (request.mode === 'navigate') {
    return false
  }
  if (url.pathname.startsWith('/api/')) {
    return false
  }
  return url.pathname.startsWith('/assets/') || STATIC_FILE_PATTERN.test(url.pathname)
}

async function putWithLimit(cache, request, response) {
  await cache.put(request, response)
  const keys = await cache.keys()
  if (keys.length > RUNTIME_CACHE_LIMIT) {
    // keys() 按插入顺序返回，删除超出上限的最旧条目。
    await Promise.all(keys.slice(0, keys.length - RUNTIME_CACHE_LIMIT).map((key) => cache.delete(key)))
  }
}

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME)
      .then((cache) => cache.addAll(APP_SHELL))
      .then(() => self.skipWaiting()),
  )
})

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((key) => key !== CACHE_NAME).map((key) => caches.delete(key))))
      .then(() => self.clients.claim()),
  )
})

self.addEventListener('fetch', (event) => {
  const request = event.request
  if (request.method !== 'GET') {
    return
  }

  const url = new URL(request.url)
  if (url.origin !== self.location.origin || url.pathname.startsWith('/api/')) {
    return
  }

  if (request.mode === 'navigate') {
    event.respondWith(fetch(request).catch(() => caches.match('/offline.html')))
    return
  }

  if (!isCacheableAsset(url, request)) {
    return
  }

  // 网络优先：成功且 response.ok 才写入缓存（异步，不阻塞响应）；网络失败时回退到缓存。
  event.respondWith(
    fetch(request)
      .then((response) => {
        if (response.ok) {
          const copy = response.clone()
          caches.open(CACHE_NAME).then((cache) => putWithLimit(cache, request, copy))
        }
        return response
      })
      .catch(() => caches.match(request)),
  )
})
