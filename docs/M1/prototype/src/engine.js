// 确定性规则原型：仅识别有限表达；不调用模型或生产业务接口。
export const expressFields = [
  { key: 'campus', label: '校区', placeholder: '例如：仙林校区', required: true },
  { key: 'pickup', label: '取件点', placeholder: '完整名称或可识别位置', required: true },
  { key: 'parcelCount', label: '快递件数', type: 'number', required: true },
  { key: 'handoffMode', label: '交接方式', options: [['face', '当面交付'], ['drop', '指定点放置']], required: true },
  { key: 'destination', label: '交接地点', placeholder: '请写明校区内可识别的位置', required: true },
  { key: 'timeWindow', label: '交接时间', placeholder: '例如：21:00—21:30', required: true },
  { key: 'contact', label: '联络方式', placeholder: '例如：站内消息，到达后联系' },
  { key: 'notice', label: '通知方式', options: [['normal', '普通站内通知'], ['silent', '静默通知']], required: true },
  { key: 'completion', label: '放置完成确认', options: [['message', '位置文字说明后由我确认'], ['photo', '放置照片后由我确认']] },
  { key: 'pickupDeadline', label: '最晚取件时间', placeholder: '例如：20:30 或暂不确定' },
  { key: 'timingPlan', label: '时间冲突处理', options: [['early', '提前取件并约定临时存放']] },
  { key: 'storagePlace', label: '临时存放地点', placeholder: '提前取件时由你确认可识别的位置' },
  { key: 'language', label: '沟通语言', options: [['zh', '中文'], ['bilingual', '中英双语']] },
  { key: 'mobility', label: '本次搬运 / 接收限制', placeholder: '只记录本次任务需要的限制' },
]
export const learningFields = [
  { key: 'context', label: '问题上下文', placeholder: '贴出当前问题或相关两步表达式' },
  { key: 'topic', label: '课程 / 知识点', placeholder: '知道的话补充即可' },
  { key: 'stuckStep', label: '卡住的位置', placeholder: '例如：第二步到第三步' },
  { key: 'helpDepth', label: '希望讲到哪一层', options: [['step', '只讲当前一步'], ['foundation', '从必要基础开始'], ['example', '用一个例子说明']] },
  { key: 'communication', label: '沟通方式', options: [['chat', '连续短消息'], ['async', '在线异步'], ['live', '预约实时协助']] },
  { key: 'availableTime', label: '可用时间 / 预约时段', placeholder: '有时间限制或预约时再填写' },
  { key: 'priority', label: '最优先解决的问题', placeholder: '时间紧张时，选一个问题先解决' },
  { key: 'language', label: '讲解语言', options: [['zh', '中文'], ['bilingual', '中英双语']] },
]

export function createSession(business = 'EXPRESS') {
  return {
    business, values: {}, facts: {}, messages: [], revision: 0, confirmation: null,
    status: 'clarifying', learningAction: 'conversation', conflicts: [], events: [],
    result: '', fragment: false, needsFoundation: false, timeLimited: false,
  }
}

function addEvent(s, type, detail = '') {
  // 不记录输入全文或精确地点；不发送到网络，也不写 localStorage。
  s.events.push({ sequence: s.events.length + 1, type, revision: s.revision, detail })
}
function setFact(s, key, value, source = 'user-message') {
  if (value !== '' && value !== undefined && value !== null) {
    s.values[key] = String(value).trim()
    s.facts[key] = { source, status: 'explicit', lifetime: 'session' }
  }
}
function invalidate(s) {
  s.revision += 1
  if (s.confirmation) addEvent(s, 'confirmation.invalidated', '用户修改后需重新确认')
  s.confirmation = null
  s.result = ''
  s.status = 'clarifying'
}

export function isSpecificPlace(value = '') {
  return value.trim().length > 1 && !/某个|随便|不知道|待定|附近|那边|这里|那里|一个地方/.test(value)
}
export function isSpecificTime(value = '') {
  const match = String(value).match(/(\d{1,2})\s*[:：点](\d{2})?/)
  return Boolean(match) && Number(match[1]) < 24 && Number(match[2] || 0) < 60 && !/不知道|待定|随便/.test(value)
}

function hours(value) {
  const match = String(value || '').match(/(\d{1,2})\s*(?::|：|点)(\d{2})?/)
  return match ? Number(match[1]) * 60 + Number(match[2] || 0) : null
}
export function detectConflicts(s) {
  const conflicts = []
  if (s.business === 'EXPRESS') {
    const time = hours(s.values.timeWindow), deadline = hours(s.values.pickupDeadline)
    if (time !== null && deadline !== null && time > deadline && !(s.values.timingPlan === 'early' && isSpecificPlace(s.values.storagePlace))) {
      conflicts.push('交接窗口晚于最晚取件时间。请确认能否提前取件暂存，或调整窗口；原型不会自动选择。')
    }
    if (s.values.parcelCount && (!/^\d+$/.test(s.values.parcelCount) || Number(s.values.parcelCount) < 1 || Number(s.values.parcelCount) > 99)) {
      conflicts.push('件数应为1—99的整数，请纠正件数。')
    }
    const window = String(s.values.timeWindow || '').match(/(\d{1,2})[:：](\d{2})\s*[—\-至到]\s*(\d{1,2})[:：](\d{2})/)
    if (window && (Number(window[3]) > 23 || Number(window[4]) > 59 || Number(window[1]) * 60 + Number(window[2]) >= Number(window[3]) * 60 + Number(window[4]))) {
      conflicts.push('请填写同一天内起止顺序正确的时间窗口；跨天安排需额外澄清。')
    }
  }
  return conflicts
}
export function requirements(s) {
  if (s.business === 'EXPRESS') {
    const keys = ['campus', 'pickup', 'parcelCount', 'handoffMode', 'destination', 'timeWindow', 'notice']
    keys.push(s.values.handoffMode === 'drop' ? 'completion' : 'contact')
    if (s.values.timingPlan === 'early') keys.push('storagePlace')
    return keys
  }
  const keys = []
  if (!s.values.context && !s.values.topic) keys.push('context')
  if (s.fragment && !s.values.context) keys.push('context')
  if (s.needsFoundation && !s.values.helpDepth) keys.push('helpDepth')
  if (s.timeLimited && !s.values.priority) keys.push('priority')
  if (s.learningAction === 'publish' || s.learningAction === 'book') {
    keys.push('communication')
    if (s.learningAction === 'book') keys.push('availableTime')
  }
  return [...new Set(keys)]
}
export function missingFields(s) {
  return requirements(s).filter(key => {
    const value = s.values[key]
    if (!value) return true
    if (s.business === 'EXPRESS' && ['campus', 'pickup', 'destination', 'storagePlace'].includes(key)) return !isSpecificPlace(value)
    if (key === 'timeWindow' || (key === 'availableTime' && s.learningAction === 'book')) return !isSpecificTime(value)
    return false
  })
}
export function updateState(s) {
  s.conflicts = detectConflicts(s)
  if (!['confirmed', 'cancelled', 'handed_off', 'resolved'].includes(s.status)) {
    s.status = missingFields(s).length || s.conflicts.length ? 'clarifying' : 'ready'
  }
  return s
}

function expressExtract(s, text) {
  const campus = text.match(/(仙林|鼓楼|浦口)校区/)
  if (campus) setFact(s, 'campus', campus[0])
  const pickup = text.match(/(?:北门|南门|东门|西门)(?:快递)?驿站|(?:菜鸟|中通|圆通|顺丰)(?:快递)?(?:驿站|点)/)
  if (pickup) setFact(s, 'pickup', pickup[0])
  const count = text.match(/([一二两三四五六七八九十]|\d+)\s*(?:个|件)?快递/)
  if (count) {
    const map = { 一: 1, 二: 2, 两: 2, 三: 3, 四: 4, 五: 5, 六: 6, 七: 7, 八: 8, 九: 9, 十: 10 }
    setFact(s, 'parcelCount', map[count[1]] || count[1])
  }
  const face = /当面|面对面/.test(text), drop = /放在|放到|放置交付/.test(text)
  if (face && drop) { delete s.values.handoffMode; delete s.facts.handoffMode }
  else if (face) setFact(s, 'handoffMode', 'face')
  else if (drop) setFact(s, 'handoffMode', 'drop')
  const destination = text.match(/(?:放在|放到|送到|交接地点[是为：:]|交接点[是为：:])([^，。；\n]+)/)
  if (destination) setFact(s, 'destination', destination[1].replace(/就行.*$|，.*$/, '').trim())
  const time = text.match(/\d{1,2}\s*[:：]\d{2}(?:\s*[—\-至到]\s*\d{1,2}\s*[:：]\d{2})?|\d{1,2}点(?:以后|之后|前|后)?|今晚|明晚|明天(?:上午|下午)?/)
  if (time) setFact(s, 'timeWindow', time[0])
  if (/静默|别打扰|不要打扰|不想被打扰/.test(text)) setFact(s, 'notice', 'silent')
  if (/普通通知|正常通知/.test(text)) setFact(s, 'notice', 'normal')
  if (/站内消息/.test(text)) setFact(s, 'contact', '站内消息，到达后联系')
  if (/照片|拍照/.test(text)) setFact(s, 'completion', 'photo')
  if (/位置文字|文字说明/.test(text)) setFact(s, 'completion', 'message')
  if (/脚受伤|不能下楼|无法下楼|搬不动/.test(text)) setFact(s, 'mobility', '本次无法下楼或搬运，需要约定可接收位置')
}
function learningExtract(s, text) {
  if (/高数|数学分析/.test(text)) setFact(s, 'topic', /链式法则/.test(text) ? '高数 · 链式法则' : /导数|极限/.test(text) ? '高数 · 导数与极限' : '高数')
  else if (/链式法则/.test(text)) setFact(s, 'topic', '链式法则')
  if (/基础薄弱|从哪里开始|基础不太好/.test(text)) s.needsFoundation = true
  if (/从基础|基础开始/.test(text)) setFact(s, 'helpDepth', 'foundation')
  if (/只讲当前一步|只讲这一小步/.test(text)) setFact(s, 'helpDepth', 'step')
  if (/一个例子|举例/.test(text)) setFact(s, 'helpDepth', 'example')
  if (/只卡|这一小步|某一步|第[一二三四五六七八九十\d]+步|取负号/.test(text)) {
    s.fragment = true
    const step = text.match(/第[一二三四五六七八九十\d]+步(?:到第[一二三四五六七八九十\d]+步)?/)
    if (step) setFact(s, 'stuckStep', step[0])
    else if (!s.values.stuckStep) setFact(s, 'stuckStep', '当前小问题')
  }
  if (/分钟|时间很紧|时间紧张|只有一点时间/.test(text)) s.timeLimited = true
  const duration = text.match(/\d+分钟/)
  if (duration) setFact(s, 'availableTime', duration[0])
  if (/异步/.test(text)) setFact(s, 'communication', 'async')
  else if (/在线|短消息/.test(text)) setFact(s, 'communication', 'chat')
  if (/优先|先解决/.test(text)) setFact(s, 'priority', text)
  // 只将用户提供的表达式/上下文记作事实，不自动生成或判断数学答案。
  if (/上下文[：:]|表达式[：:]|第二步[：:]|第三步[：:]|=|因为|题目是/.test(text)) setFact(s, 'context', text)
}

function nextReply(s) {
  if (s.conflicts.length) return s.conflicts[0]
  const missing = missingFields(s)
  if (s.business === 'EXPRESS') {
    if (missing.includes('campus')) return '你说的是哪个校区？位置含糊时，我会等你确认，不会猜测。'
    if (missing.includes('destination')) return s.values.handoffMode === 'drop'
      ? '可以放置交付。请告诉我具体放在哪里，以及放好后如何确认；“某个地点”还不足以形成交接约定。'
      : '请确认一个你能接收快递、对方也能识别的交接位置。'
    if (missing.length) {
      const labels = expressFields.filter(f => missing.includes(f.key)).map(f => f.label)
      return `还需要确认：${labels.join('、')}。你可以继续说，也可以在右侧补充。`
    }
    return '交接信息已齐。请检查确认卡；修改交接方式或时间后，需要重新确认。当前只会模拟执行。'
  }
  if (missing.includes('context')) return s.fragment
    ? '把你卡住的这两步表达式贴出来就好，我们接着这条消息聊。'
    : '你现在想弄懂哪一个问题？一句话或贴出题目即可。'
  if (missing.includes('helpDepth')) return '你目前对哪一部分有印象？可以选择从必要基础开始，或用一个例子说明。'
  if (missing.includes('priority')) return '时间有限，你想先解决哪一个问题？先选一个，我们按这个范围沟通。'
  if (missing.includes('availableTime')) return '预约实时协助需要一个可确认的时段。你什么时候方便？'
  if (missing.length) return '请补充本次协作需要的沟通方式，再检查确认卡。'
  if (s.learningAction !== 'conversation') return '协作信息已齐，请检查确认卡。发布或预约前需要你的确认。'
  return s.values.language === 'bilingual'
    ? '已保留当前问题上下文。可以继续追问一个步骤 / one step at a time。这里展示沟通过程，不生成真实模型答案。'
    : '已保留当前问题上下文。你可以接着说哪一步仍不清楚，理解后标记已解决。这里不生成真实模型答案。'
}

export function sendMessage(s, input) {
  const text = String(input).trim()
  if (!text) return s
  if (['cancelled', 'handed_off', 'resolved'].includes(s.status)) return s
  invalidate(s)
  s.messages.push({ role: 'user', text })
  if (/中英|双语|bilingual/i.test(text)) setFact(s, 'language', 'bilingual')
  if (/只用中文|用中文就好/.test(text)) setFact(s, 'language', 'zh')
  if (s.business === 'EXPRESS') expressExtract(s, text)
  else learningExtract(s, text)
  updateState(s)
  s.messages.push({ role: 'assistant', text: nextReply(s) })
  addEvent(s, 'dialogue.updated')
  return s
}
export function editField(s, key, value) {
  const fields = s.business === 'EXPRESS' ? expressFields : learningFields
  const field = fields.find(f => f.key === key)
  if (!field || (field.options && value && !field.options.some(option => option[0] === value))) return s
  if (['cancelled', 'handed_off', 'resolved'].includes(s.status)) return s
  if ((s.values[key] || '') === String(value).trim()) return s
  invalidate(s)
  if (!String(value).trim()) { delete s.values[key]; delete s.facts[key] }
  else setFact(s, key, value, 'user-field')
  if (key === 'handoffMode') {
    // 交接方式改变后重新明确位置，避免沿用已经不适用的位置。
    delete s.values.destination; delete s.facts.destination
  }
  updateState(s)
  addEvent(s, 'field.corrected', key)
  return s
}
export function chooseLearningAction(s, action) {
  if (s.business !== 'TUTORING' || !['conversation', 'publish', 'book'].includes(action)) return s
  if (['cancelled', 'handed_off', 'resolved'].includes(s.status)) return s
  if (s.learningAction === action) return s
  invalidate(s)
  s.learningAction = action
  if (action === 'book') setFact(s, 'communication', 'live', 'user-choice')
  updateState(s)
  s.messages.push({ role: 'assistant', text: nextReply(s) })
  addEvent(s, 'action.proposed', action)
  return s
}
export function canConfirm(s) {
  return (s.business === 'EXPRESS' || s.learningAction !== 'conversation')
    && s.status === 'ready' && missingFields(s).length === 0 && detectConflicts(s).length === 0
}
export function confirm(s) {
  if (!canConfirm(s)) return false
  s.confirmation = { revision: s.revision, action: s.business === 'EXPRESS' ? 'publish-express' : s.learningAction, snapshot: JSON.stringify(s.values) }
  s.status = 'confirmed'
  s.result = '已模拟确认；未发布真实需求、未创建订单或预约。'
  addEvent(s, 'operation.simulated', s.confirmation.action)
  return true
}
export function cancel(s) {
  if (['cancelled', 'handed_off', 'resolved'].includes(s.status)) return false
  s.status = 'cancelled'; s.confirmation = null
  s.result = '已撤销本次模拟任务。没有真实业务需要取消。'
  addEvent(s, 'task.cancelled')
  return true
}
export function handoff(s) {
  if (['cancelled', 'handed_off', 'resolved'].includes(s.status)) return false
  s.status = 'handed_off'; s.confirmation = null
  s.result = '模拟接管摘要已生成。生产人工服务尚未接入。'
  addEvent(s, 'handoff.simulated')
  return true
}
export function resolve(s) {
  if (s.business !== 'TUTORING' || s.learningAction !== 'conversation' || !s.messages.some(m => m.role === 'user') || missingFields(s).length || ['cancelled', 'handed_off', 'resolved', 'confirmed'].includes(s.status)) return false
  s.status = 'resolved'; s.result = '你已标记这次问题解决。这是用户反馈，不是模型自动判断。'
  addEvent(s, 'learning.user-resolved')
  return true
}
export function reopen(s) {
  if (s.status !== 'confirmed') return s
  invalidate(s); updateState(s); addEvent(s, 'task.reopened')
  return s
}
