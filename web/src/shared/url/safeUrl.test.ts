import assert from 'node:assert/strict'
import { describe, it } from 'node:test'

import { isSafeExternalUrl, safeExternalHref, safeHref, safeInternalPath } from './safeUrl.ts'

describe('safeExternalHref', () => {
  it('接受 http/https/mailto/tel 链接', () => {
    assert.equal(safeExternalHref('https://example.com/x?y=1'), 'https://example.com/x?y=1')
    assert.equal(safeExternalHref('http://example.com'), 'http://example.com')
    assert.equal(safeExternalHref('mailto:a@b.com'), 'mailto:a@b.com')
    assert.equal(safeExternalHref('  https://example.com  '), 'https://example.com')
  })

  it('拒绝 javascript:/data:/file: 伪协议', () => {
    assert.equal(safeExternalHref('javascript:alert(1)'), null)
    assert.equal(safeExternalHref('JavaScript:alert(1)'), null)
    assert.equal(safeExternalHref('data:text/html,<script>alert(1)</script>'), null)
    assert.equal(safeExternalHref('file:///etc/passwd'), null)
    assert.equal(safeExternalHref('vbscript:msgbox(1)'), null)
  })

  it('拒绝内嵌空白/控制字符的协议绕过', () => {
    assert.equal(safeExternalHref('java\tscript:alert(1)'), null)
    assert.equal(safeExternalHref('java\nscript:alert(1)'), null)
    assert.equal(safeExternalHref('  javascript:alert(1)'), null)
    assert.equal(safeExternalHref('jav ascript:alert(1)'), null)
  })

  it('拒绝相对地址与非字符串输入', () => {
    assert.equal(safeExternalHref('/inside/path'), null)
    assert.equal(safeExternalHref('example.com'), null)
    assert.equal(safeExternalHref(''), null)
    assert.equal(safeExternalHref(null), null)
    assert.equal(safeExternalHref(42), null)
  })
})

describe('isSafeExternalUrl', () => {
  it('与 safeExternalHref 判定一致', () => {
    assert.equal(isSafeExternalUrl('https://example.com'), true)
    assert.equal(isSafeExternalUrl('javascript:alert(1)'), false)
  })
})

describe('safeInternalPath', () => {
  it('接受单斜杠开头的站内路径', () => {
    assert.equal(safeInternalPath('/project-spaces/abc'), '/project-spaces/abc')
    assert.equal(safeInternalPath('/knowledge-bases/s1/items/i1?panel=comments#doc-block-9'), '/knowledge-bases/s1/items/i1?panel=comments#doc-block-9')
  })

  it('拒绝协议相对、反斜杠、绝对 URL 与空串', () => {
    assert.equal(safeInternalPath('//evil.com/x'), null)
    assert.equal(safeInternalPath('\\\\server\\share'), null)
    assert.equal(safeInternalPath('https://example.com'), null)
    assert.equal(safeInternalPath('javascript:alert(1)'), null)
    assert.equal(safeInternalPath(''), null)
    assert.equal(safeInternalPath(undefined), null)
  })
})

describe('safeHref', () => {
  it('站内路径与白名单外链均可用', () => {
    assert.equal(safeHref('/search?q=x'), '/search?q=x')
    assert.equal(safeHref('https://example.com'), 'https://example.com')
  })

  it('不安全输入返回 null', () => {
    assert.equal(safeHref('javascript:alert(1)'), null)
    assert.equal(safeHref('//evil.com'), null)
    assert.equal(safeHref('data:text/html,x'), null)
  })
})
