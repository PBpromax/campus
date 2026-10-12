import test from 'node:test'
import assert from 'node:assert/strict'
import { scenarios } from '../src/scenarios.js'
import { createSession, sendMessage, editField, chooseLearningAction, missingFields, canConfirm, confirm, cancel, resolve, handoff, reopen, isSpecificTime } from '../src/engine.js'

function readyExpress(mode = 'face') {
  const s = createSession()
  const values = { campus: '仙林校区', pickup: '北门驿站', parcelCount: '2', handoffMode: mode, destination: '5号宿舍楼门口', timeWindow: '21:00—21:30', notice: 'silent', contact: '站内消息，到达后联系', completion: 'photo' }
  Object.entries(values).forEach(([key, value]) => editField(s, key, value))
  return s
}

test('两个业务各五种样本且不自动产生副作用', () => {
  for (const business of ['EXPRESS', 'TUTORING']) assert.equal(scenarios.filter(s => s.business === business).length, 5)
  for (const scene of scenarios) {
    const s = createSession(scene.business); sendMessage(s, scene.input)
    assert.equal(s.messages.length, 2); assert.equal(s.confirmation, null)
    assert.ok(!s.events.some(e => e.type === 'operation.simulated'))
  }
})
test('未明确校区的北门保持未知', () => {
  const s = createSession(); sendMessage(s, scenarios.find(s => s.id === 'E03').input)
  assert.equal(s.values.campus, undefined); assert.ok(missingFields(s).includes('campus'))
  assert.match(s.messages.at(-1).text, /哪个校区/)
})
test('某个地点不能作为放置交付约定', () => {
  const s = createSession(); sendMessage(s, scenarios.find(s => s.id === 'E05').input)
  assert.equal(s.values.handoffMode, 'drop'); assert.ok(missingFields(s).includes('destination')); assert.equal(confirm(s), false)
})
test('行动限制仅为本次上下文，不形成长期偏好', () => {
  const s = createSession(); sendMessage(s, scenarios[0].input)
  assert.equal(s.values.parcelCount, '3'); assert.equal(s.facts.mobility.lifetime, 'session')
  assert.ok(!Object.keys(s.values).some(k => /history|personality/i.test(k)))
})
test('上课静默通知可识别并可由用户纠正', () => {
  const s = createSession(); sendMessage(s, scenarios[1].input)
  assert.equal(s.values.notice, 'silent'); editField(s, 'notice', 'normal'); assert.equal(s.values.notice, 'normal')
})
test('双语选择保留原地点，不伪造翻译后的业务位置', () => {
  const s = createSession(); sendMessage(s, scenarios[3].input)
  assert.equal(s.values.language, 'bilingual'); assert.equal(s.values.pickup, '北门驿站')
})
test('当面交付需要联络方式，放置交付需要完成确认', () => {
  const face = readyExpress(); editField(face, 'contact', ''); assert.ok(missingFields(face).includes('contact'))
  const drop = readyExpress('drop'); editField(drop, 'completion', ''); assert.ok(missingFields(drop).includes('completion'))
  assert.ok(!missingFields(drop).includes('contact'))
})
test('确认只执行一次，修改后旧确认失效', () => {
  const s = readyExpress(); assert.ok(canConfirm(s)); assert.ok(confirm(s)); assert.equal(confirm(s), false)
  const revision = s.confirmation.revision; editField(s, 'timeWindow', '22:00—22:30')
  assert.equal(s.confirmation, null); assert.ok(s.revision > revision); assert.ok(confirm(s))
  assert.equal(s.events.filter(e => e.type === 'operation.simulated').length, 2)
})
test('交接方式切换清空原地点并要求重新确认', () => {
  const s = readyExpress(); confirm(s); editField(s, 'handoffMode', 'drop')
  assert.equal(s.values.destination, undefined); assert.equal(s.confirmation, null); assert.equal(canConfirm(s), false)
  editField(s, 'destination', '5号宿舍楼一楼指定置物架'); assert.ok(confirm(s))
})
test('时间冲突阻止执行，用户选择提前取件还要明确暂存点', () => {
  const s = readyExpress(); editField(s, 'pickupDeadline', '20:30')
  assert.equal(s.conflicts.length, 1); assert.equal(confirm(s), false)
  editField(s, 'timingPlan', 'early'); assert.equal(confirm(s), false)
  editField(s, 'storagePlace', '北门驿站工作人员确认的寄存柜'); assert.equal(s.conflicts.length, 0); assert.ok(confirm(s))
})
test('今晚和时长不能自动当作具体预约时段', () => {
  assert.equal(isSpecificTime('今晚'), false); assert.equal(isSpecificTime('20分钟'), false)
  assert.equal(isSpecificTime('25:00'), false); assert.equal(isSpecificTime('21:90'), false)
  assert.equal(isSpecificTime('21:00—21:30'), true)
})
test('非法时间范围与件数不可确认', () => {
  const s = readyExpress(); editField(s, 'timeWindow', '21:00—20:00'); assert.equal(confirm(s), false)
  editField(s, 'timeWindow', '21:00—21:30'); editField(s, 'parcelCount', '0'); assert.equal(confirm(s), false)
  editField(s, 'parcelCount', '2'); assert.ok(confirm(s))
})
test('两个交接候选同句出现时不代替用户选择', () => {
  const s = createSession(); sendMessage(s, '当面交付或者放在宿舍楼门口都可能，先别决定')
  assert.equal(s.values.handoffMode, undefined); assert.ok(missingFields(s).includes('handoffMode'))
})
test('碎片问题只追问表达式，不要求预算校区或时间', () => {
  const s = createSession('TUTORING'); sendMessage(s, scenarios.at(-1).input)
  assert.deepEqual(missingFields(s), ['context']); assert.match(s.messages.at(-1).text, /两步表达式/)
  assert.equal(s.learningAction, 'conversation'); assert.equal(canConfirm(s), false)
})
test('短消息补齐后保留上下文并允许用户标记已解决', () => {
  const s = createSession('TUTORING'); sendMessage(s, scenarios.at(-1).input)
  sendMessage(s, '第二步：y = -cos x；第三步：y′ = sin x')
  const context = s.values.context; sendMessage(s, '再解释慢一点，我只想知道符号变化')
  assert.equal(s.values.context, context); assert.equal(s.messages.length, 6); assert.equal(s.status, 'ready')
  assert.ok(resolve(s)); assert.equal(s.status, 'resolved'); assert.equal(confirm(s), false)
})
test('基础薄弱样本需要讲解起点', () => {
  const s = createSession('TUTORING'); sendMessage(s, scenarios.find(s => s.id === 'L01').input)
  assert.ok(missingFields(s).includes('helpDepth')); editField(s, 'helpDepth', 'foundation'); assert.equal(s.status, 'ready')
})
test('时间紧张需要用户确定优先问题', () => {
  const s = createSession('TUTORING'); sendMessage(s, scenarios.find(s => s.id === 'L02').input)
  assert.equal(s.values.availableTime, '20分钟'); assert.ok(missingFields(s).includes('priority'))
  editField(s, 'priority', '先理解复合函数求导的乘法关系'); assert.equal(s.status, 'ready')
})
test('在线异步沟通不强迫指定预约时间', () => {
  const s = createSession('TUTORING'); sendMessage(s, scenarios.find(s => s.id === 'L03').input)
  assert.equal(s.values.communication, 'async'); assert.ok(!missingFields(s).includes('availableTime'))
})
test('普通学习对话无确认卡动作，发布需要明确沟通方式', () => {
  const s = createSession('TUTORING'); sendMessage(s, '链式法则没明白')
  assert.equal(canConfirm(s), false); chooseLearningAction(s, 'publish'); assert.ok(missingFields(s).includes('communication'))
  editField(s, 'communication', 'async'); assert.ok(confirm(s)); assert.equal(s.confirmation.action, 'publish')
})
test('预约需要具体时段，返回短消息后解除预约字段要求', () => {
  const s = createSession('TUTORING'); sendMessage(s, '链式法则没明白'); chooseLearningAction(s, 'book')
  editField(s, 'availableTime', '20分钟'); assert.equal(confirm(s), false)
  editField(s, 'availableTime', '今晚20:00—20:20'); assert.ok(confirm(s))
  chooseLearningAction(s, 'conversation'); assert.equal(s.confirmation, null); assert.equal(canConfirm(s), false)
})
test('撤销、接管、解决之后不能再产生操作', () => {
  const s = readyExpress(); assert.ok(cancel(s)); assert.equal(confirm(s), false)
  const old = JSON.stringify(s.values); editField(s, 'campus', '鼓楼校区'); assert.equal(JSON.stringify(s.values), old)
  const other = readyExpress(); assert.ok(handoff(other)); assert.equal(confirm(other), false)
})
test('原型事件不记录精确位置和用户消息全文', () => {
  const s = readyExpress(); sendMessage(s, '请送到5号楼私人房间123456')
  assert.ok(!JSON.stringify(s.events).includes('123456')); confirm(s)
  assert.ok(!JSON.stringify(s.events).includes('私人房间'))
})
test('只允许本体定义的字段和选项', () => {
  const s = createSession(); editField(s, 'executeSql', 'DROP TABLE'); editField(s, 'handoffMode', 'dangerous')
  assert.equal(s.values.executeSql, undefined); assert.equal(s.values.handoffMode, undefined)
})
test('重新编辑已确认方案需要重新确认', () => {
  const s = readyExpress(); confirm(s); reopen(s); assert.equal(s.confirmation, null); assert.equal(s.status, 'ready')
  assert.ok(confirm(s))
})
test('重复选择当前交接方式不会清除位置或撤销确认', () => {
  const s = readyExpress(); confirm(s)
  const revision = s.revision; editField(s, 'handoffMode', 'face')
  assert.equal(s.values.destination, '5号宿舍楼门口'); assert.equal(s.revision, revision)
  assert.equal(s.confirmation.revision, revision)
})
test('继续追问同一小步时保持已定位的步骤', () => {
  const s = createSession('TUTORING'); sendMessage(s, '第二步到第三步怎么来的？')
  const step = s.values.stuckStep; sendMessage(s, '第二步：y = -cos x；第三步：y′ = sin x')
  const providedStep = s.values.stuckStep; sendMessage(s, '再慢一点，我只想弄懂这一小步')
  assert.equal(s.values.stuckStep, providedStep); assert.ok(step)
})
