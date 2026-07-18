import assert from 'node:assert/strict'
import { mkdtempSync, mkdirSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import test from 'node:test'
import { changedSinceBaseline, fileSignatures, gitHead, gitStatusPaths } from '../src/lib/git.js'
import { runSync } from '../src/lib/process.js'

test('tracks required docs, baseline-dirty edits and committed cycle changes', () => {
  const root = mkdtempSync(join(tmpdir(), 'colla-git-state-'))
  mkdirSync(join(root, 'docs'), { recursive: true }); mkdirSync(join(root, 'src'), { recursive: true })
  writeFileSync(join(root, 'docs', 'roadmap.md'), 'baseline'); writeFileSync(join(root, 'src', 'app.txt'), 'baseline'); writeFileSync(join(root, 'dirty.txt'), 'baseline')
  runSync('git', ['init', '-q'], { cwd: root }); runSync('git', ['config', 'user.email', 'test@example.invalid'], { cwd: root }); runSync('git', ['config', 'user.name', 'Test'], { cwd: root }); runSync('git', ['add', '.'], { cwd: root }); runSync('git', ['commit', '-q', '-m', 'baseline'], { cwd: root })
  writeFileSync(join(root, 'dirty.txt'), 'dirty before cycle')
  const baselineChangedPaths = gitStatusPaths(root)
  const context = { baselineCommit: gitHead(root), baselineChangedPaths, baselineFileSignatures: fileSignatures(root, [...baselineChangedPaths, 'docs/roadmap.md']) }
  assert.deepEqual(changedSinceBaseline(root, context), [])
  writeFileSync(join(root, 'docs', 'roadmap.md'), 'updated')
  assert(changedSinceBaseline(root, context).includes('docs/roadmap.md'))
  runSync('git', ['restore', 'docs/roadmap.md'], { cwd: root }); writeFileSync(join(root, 'src', 'app.txt'), 'committed'); runSync('git', ['add', 'src/app.txt'], { cwd: root }); runSync('git', ['commit', '-q', '-m', 'cycle'], { cwd: root })
  assert(changedSinceBaseline(root, context).includes('src/app.txt'))
  assert(!changedSinceBaseline(root, context).includes('dirty.txt'))
  writeFileSync(join(root, 'dirty.txt'), 'changed again')
  assert(changedSinceBaseline(root, context).includes('dirty.txt'))
})

test('parses both exact paths from porcelain rename records without ghost paths', () => {
  const root = mkdtempSync(join(tmpdir(), 'colla-git-rename-'))
  writeFileSync(join(root, 'before-name.txt'), 'baseline')
  runSync('git', ['init', '-q'], { cwd: root }); runSync('git', ['config', 'user.email', 'test@example.invalid'], { cwd: root }); runSync('git', ['config', 'user.name', 'Test'], { cwd: root }); runSync('git', ['add', '.'], { cwd: root }); runSync('git', ['commit', '-q', '-m', 'baseline'], { cwd: root })
  runSync('git', ['mv', 'before-name.txt', 'after-name.txt'], { cwd: root })
  assert.deepEqual(gitStatusPaths(root).sort(), ['after-name.txt', 'before-name.txt'])
})
