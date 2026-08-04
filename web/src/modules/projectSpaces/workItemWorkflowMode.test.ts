import assert from 'node:assert/strict'
import { describe, it } from 'node:test'

import {
  getWorkItemWorkflowMode,
  switchWorkItemWorkflowMode,
} from './workItemWorkflowMode.ts'

describe('work-item workflow mode', () => {
  it('uses node mode only when a node flow is configured', () => {
    assert.equal(getWorkItemWorkflowMode({ nodeFlow: { nodes: [] } }), 'node')
    assert.equal(getWorkItemWorkflowMode({ stateFlow: { states: [] } }), 'state')
    assert.equal(getWorkItemWorkflowMode({}), 'state')
  })

  it('switches to a valid default state flow without mutating the source', () => {
    const source = { snapshotSchemaVersion: 3, nodeFlow: { nodes: ['existing'] }, fields: [] }
    const result = switchWorkItemWorkflowMode(source, 'state')

    assert.equal('nodeFlow' in result, false)
    assert.equal(Array.isArray((result.stateFlow as { states: unknown[] }).states), true)
    assert.equal(result.snapshotSchemaVersion, 3)
    assert.deepEqual(source.nodeFlow, { nodes: ['existing'] })
  })

  it('switches to a valid default node flow and removes state flow authority', () => {
    const result = switchWorkItemWorkflowMode(
      { snapshotSchemaVersion: 2, stateFlow: { states: [] } },
      'node',
    )
    const nodeFlow = result.nodeFlow as { nodes: Array<{ kind: string }>; edges: unknown[] }

    assert.equal('stateFlow' in result, false)
    assert.equal(result.snapshotSchemaVersion, 3)
    assert.deepEqual(nodeFlow.nodes.map((node) => node.kind), ['start', 'manual', 'end'])
    assert.equal(nodeFlow.edges.length, 2)
  })
})
