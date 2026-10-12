import test from 'node:test'
import assert from 'node:assert/strict'
import { createSession, sendMessage, missingFields } from '../src/engine.js'

test('统一完整发布表单候选被碎片问题的必要信息约束推翻', () => {
  const candidateRequired = ['topic', 'campus', 'budget', 'availableTime']
  const fragment = createSession('TUTORING')
  sendMessage(fragment, '第二步到第三步怎么来的？只卡在这一小步')
  const acceptedRequired = missingFields(fragment)
  assert.deepEqual(acceptedRequired, ['context'])
  const candidateMissing = candidateRequired.filter(key => !fragment.values[key])
  assert.equal(candidateMissing.length, 4)
  assert.ok(candidateMissing.every(key => !acceptedRequired.includes(key)), '候选索取四个非必要字段，不符合当前需求')
  sendMessage(fragment, '第二步：y = -cos x；第三步：y′ = sin x')
  assert.deepEqual(missingFields(fragment), [])
  assert.equal(fragment.status, 'ready')
})
