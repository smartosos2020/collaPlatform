import assert from 'node:assert/strict'
import { describe, it } from 'node:test'

import { resolveNavigationPath } from './deepLinks.ts'

describe('resolveNavigationPath', () => {
  it('只返回安全站内路径', () => {
    assert.equal(resolveNavigationPath({ webPath: '/search?q=x' }), '/search?q=x')
    assert.equal(resolveNavigationPath({ mobileFallbackPath: '/project-spaces' }), '/project-spaces')
  })

  it('拒绝服务端提供的不安全地址且不回退到另一未校验字段', () => {
    assert.equal(resolveNavigationPath({ webPath: 'javascript:alert(1)', mobileFallbackPath: '/safe' }), null)
    assert.equal(resolveNavigationPath({ mobileFallbackPath: '//evil.example/path' }), null)
    assert.equal(resolveNavigationPath({ webPath: 'https://evil.example/path' }), null)
  })

  it('将受支持的 colla 深链转换为站内路径', () => {
    assert.equal(resolveNavigationPath({ deepLink: 'colla://project-space/space-1' }), '/project-spaces/space-1')
    assert.equal(resolveNavigationPath({ deepLink: 'colla://unknown/id' }), null)
  })
})
