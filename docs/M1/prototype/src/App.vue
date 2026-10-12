<script setup>
import { computed, ref } from 'vue'
import { scenarios, statusLabels } from './scenarios.js'
import { createSession, sendMessage, editField, chooseLearningAction, missingFields, requirements, canConfirm, confirm, cancel, handoff, resolve, reopen, expressFields, learningFields } from './engine.js'

const session = ref(createSession())
const draft = ref('')
const activeScenario = ref('')
const fixedForm = ref(false)
const historyPane = ref(false)
const sessionScenes = computed(() => scenarios.filter(s => s.business === session.value.business))
const learning = computed(() => session.value.business === 'TUTORING')
const terminal = computed(() => ['cancelled', 'handed_off', 'resolved'].includes(session.value.status))
const fields = computed(() => (learning.value ? learningFields : expressFields).filter(f => {
  if (learning.value) return fixedForm.value || ['context', 'topic', 'stuckStep'].includes(f.key) || requirements(session.value).includes(f.key) || session.value.values[f.key]
  if (f.key === 'contact') return session.value.values.handoffMode !== 'drop'
  if (f.key === 'completion') return session.value.values.handoffMode === 'drop'
  if (f.key === 'timingPlan') return session.value.conflicts.length || session.value.values.timingPlan
  if (f.key === 'storagePlace') return session.value.values.timingPlan === 'early'
  return true
}))
const pending = computed(() => missingFields(session.value))
const showConfirm = computed(() => !learning.value || session.value.learningAction !== 'conversation')
const selectedScene = computed(() => scenarios.find(s => s.id === activeScenario.value))
const activeFacts = computed(() => (learning.value ? learningFields : expressFields).filter(f => session.value.values[f.key]))

function selectBusiness(business) {
  if (session.value.business === business) return
  if (session.value.messages.length && !window.confirm('切换业务会清空本次临时对话。继续吗？')) return
  session.value = createSession(business); draft.value = ''; activeScenario.value = ''; fixedForm.value = false
}
function loadScene(scene) {
  if (session.value.messages.length && !window.confirm('载入样本会清空本次临时对话。继续吗？')) return
  session.value = createSession(scene.business); activeScenario.value = scene.id; draft.value = ''; fixedForm.value = false
  sendMessage(session.value, scene.input)
}
function submit() {
  if (!draft.value.trim()) return
  sendMessage(session.value, draft.value); draft.value = ''
}
function update(key, value) { editField(session.value, key, value) }
function labelValue(field) {
  const value = session.value.values[field.key]
  return field.options?.find(option => option[0] === value)?.[1] || value
}
function reset() {
  if (session.value.messages.length && !window.confirm('重置后临时对话和约定将清空。继续吗？')) return
  session.value = createSession(session.value.business); draft.value = ''; activeScenario.value = ''; fixedForm.value = false
}
</script>

<template>
  <div class="app" :class="{ learning }">
    <header class="topbar">
      <a class="brand" href="#"><span class="brand-mark">C</span><span>CampusHub<small>为这一次需求，换一种交互</small></span></a>
      <div class="top-note"><span class="dot"></span>M1 · 个性化需求与 HAI</div>
      <span class="simulation">规则原型 · 全部操作为模拟</span>
    </header>
    <main>
      <section class="intro">
        <div><p class="eyebrow">不同情境，相同目标</p><h1>先理解你，再一起确认。</h1><p class="intro-copy">快递重在交接约定；学习求助可以从一句小问题开始。</p></div>
        <div class="business-tabs" aria-label="业务选择">
          <button :class="{ active: !learning }" @click="selectBusiness('EXPRESS')">快递跑腿<span>取件与交接</span></button>
          <button :class="{ active: learning }" @click="selectBusiness('TUTORING')">学习求助<span>理解与沟通</span></button>
        </div>
      </section>
      <div class="workspace">
        <aside class="scenario-panel">
          <div class="section-head"><h2>五种特殊情境</h2><span>设计样本</span></div>
          <p class="muted small">样本是待验证的设计假设。点击载入后，可以自由修改。</p>
          <button v-for="scene in sessionScenes" :key="scene.id" class="scene" :class="{ selected: activeScenario === scene.id }" @click="loadScene(scene)">
            <span class="scene-id">{{ scene.id }}</span><strong>{{ scene.title }}</strong><small>{{ scene.subtitle }}</small><span class="scene-arrow">↗</span>
          </button>
          <div v-if="selectedScene" class="scene-detail"><span>本次交互变化</span><p>{{ selectedScene.change }}</p></div>
          <div class="scope-note"><strong>明确的边界</strong><p>有限规则识别，不是真实模型。输入无法识别时可手动补充。没有真实订单、通知、答疑或人工服务。</p></div>
        </aside>

        <section class="dialogue-panel">
          <div class="dialogue-header"><div><p class="eyebrow">{{ learning ? '一个小问题，也值得被理解' : '把交接方式说清楚' }}</p><h2>{{ learning ? '学习对话' : '需求对话' }}</h2></div><span class="state" :data-state="session.status">{{ statusLabels[session.status] }}</span></div>
          <div class="messages" aria-label="对话记录" aria-live="polite">
            <div v-if="!session.messages.length" class="empty-state"><div class="empty-icon">{{ learning ? '?' : '↗' }}</div><h3>{{ learning ? '卡在哪一步？直接说就好。' : '从你的要求开始。' }}</h3><p>{{ learning ? '可以贴两步表达式，也可以说希望怎样沟通。' : '告诉我取件和交接要求，缺失信息会一起补齐。' }}</p><p class="small muted">也可以从左侧五个样本开始体验。</p></div>
            <article v-for="(message, i) in session.messages" :key="i" class="message" :class="message.role"><span class="speaker">{{ message.role === 'user' ? '你' : '助手 · 模拟' }}</span><p>{{ message.text }}</p></article>
          </div>
          <div v-if="session.conflicts.length" class="conflict" role="alert"><strong>约束需要一起确认</strong><p v-for="item in session.conflicts" :key="item">{{ item }}</p><button v-if="!learning" @click="update('timingPlan', 'early')">选择提前取件，再补充临时存放点</button></div>
          <div v-if="session.result" class="result" role="status"><strong>{{ session.status === 'handed_off' ? '模拟接管摘要' : '本次结果' }}</strong><p>{{ session.result }}</p><p v-if="session.status === 'handed_off'" class="small">业务：{{ learning ? '学习求助' : '快递跑腿' }}；待补充字段：{{ pending.length }}；约束冲突：{{ session.conflicts.length }}。人工队列尚未接入。</p><button v-if="session.status === 'confirmed'" @click="reopen(session)">修改方案并重新确认</button></div>
          <form class="composer" @submit.prevent="submit"><label for="message-input">{{ learning ? '继续说你的问题' : '继续补充或纠正要求' }}</label><textarea id="message-input" v-model="draft" :disabled="terminal" :placeholder="learning ? '例如：第二步是 y = -cos x，第三步是 y′ = sin x，为什么符号变了？' : '例如：改成当面交付，21:00—21:30在5号宿舍楼门口交接。'" rows="3" @keydown.ctrl.enter.prevent="submit" @keydown.meta.enter.prevent="submit"></textarea><div class="composer-bottom"><span>上下文仅保留在本次页面内</span><button class="primary" type="submit" :disabled="terminal || !draft.trim()">发送消息 ↗</button></div></form>
          <div class="secondary-actions"><button @click="fixedForm = !fixedForm">{{ fixedForm ? '收起固定表单' : '使用固定表单' }}</button><button :disabled="terminal" @click="handoff(session)">模拟人工接管</button><button :disabled="terminal" @click="cancel(session)">撤销本次任务</button><button @click="reset">重置对话</button></div>
        </section>

        <aside class="context-panel">
          <div class="section-head"><h2>{{ fixedForm ? '固定表单' : learning ? '按需补充' : '交接约定' }}</h2><span>由你确认</span></div>
          <p class="muted small">{{ learning ? '普通问答只补充必要上下文。发布或预约才出现确认卡。' : '空白保持未知，具体地点与时间由你填写。' }}</p>
          <div v-if="!learning" class="choice-card"><p>选择交接方式<span v-if="session.values.language === 'bilingual'"> / Handover</span></p><div class="option-row"><button :class="{ chosen: session.values.handoffMode === 'face' }" :disabled="terminal" @click="update('handoffMode', 'face')">当面交付<span v-if="session.values.language === 'bilingual'"> · In person</span></button><button :class="{ chosen: session.values.handoffMode === 'drop' }" :disabled="terminal" @click="update('handoffMode', 'drop')">指定点放置<span v-if="session.values.language === 'bilingual'"> · Drop-off</span></button></div><p class="small muted">{{ session.values.handoffMode === 'drop' ? '需要具体地点与放置后的通知 / 确认方式。' : '需要交接时间、地点，以及到达后的联络方式。' }}</p></div>
          <div v-else class="choice-card"><p>本次怎样继续？</p><div class="option-row vertical"><button :class="{ chosen: session.learningAction === 'conversation' }" :disabled="terminal" @click="chooseLearningAction(session, 'conversation')">继续短消息</button><button :class="{ chosen: session.learningAction === 'publish' }" :disabled="terminal" @click="chooseLearningAction(session, 'publish')">模拟发布求助</button><button :class="{ chosen: session.learningAction === 'book' }" :disabled="terminal" @click="chooseLearningAction(session, 'book')">模拟预约协助</button></div></div>
          <div class="fields"><label v-for="field in fields" :key="field.key" :for="'field-' + field.key"><span>{{ field.label }}<small v-if="pending.includes(field.key)" class="pending">待补充</small></span><select v-if="field.options" :id="'field-' + field.key" :value="session.values[field.key] || ''" :disabled="terminal" @change="update(field.key, $event.target.value)"><option value="">尚未确认</option><option v-for="option in field.options" :key="option[0]" :value="option[0]">{{ option[1] }}</option></select><textarea v-else-if="field.key === 'context'" :id="'field-' + field.key" :value="session.values[field.key] || ''" :disabled="terminal" :placeholder="field.placeholder" rows="3" @change="update(field.key, $event.target.value)"></textarea><input v-else :id="'field-' + field.key" :type="field.type || 'text'" :value="session.values[field.key] || ''" :disabled="terminal" :placeholder="field.placeholder || '由你补充'" @change="update(field.key, $event.target.value)" /><small v-if="session.facts[field.key]" class="source">{{ session.facts[field.key].source === 'user-message' ? '来自本次消息，规则识别后可纠正' : '你已明确填写 / 选择' }}</small></label></div>
          <section v-if="showConfirm" class="confirmation-card" aria-label="确认卡"><div class="section-head"><h3>确认卡<span v-if="session.values.language === 'bilingual'"> / Confirmation</span></h3><span>v{{ session.revision }}</span></div><dl><template v-for="field in activeFacts" :key="field.key"><dt>{{ field.label }}</dt><dd>{{ labelValue(field) }}</dd></template></dl><p v-if="pending.length" class="small">还有 {{ pending.length }} 项必要信息需要明确。</p><p class="small muted">确认范围是当前版本的约定。修改后原确认失效。</p><button class="primary full" :disabled="!canConfirm(session)" @click="confirm(session)">确认并模拟执行</button></section>
          <section v-else class="fragment-card"><strong>让问题保持轻量</strong><p>继续补一句、问一步。当前对话不要求填写预算、校区或预约时间。</p><button class="primary full" :disabled="terminal || session.status !== 'ready' || !session.messages.length" @click="resolve(session)">标记已解决</button></section>
          <button class="event-toggle" @click="historyPane = !historyPane">{{ historyPane ? '收起' : '查看' }}本次模拟事件（{{ session.events.length }}）</button><ol v-if="historyPane" class="events"><li v-for="event in session.events" :key="event.sequence">{{ event.sequence }} · {{ event.type }} · v{{ event.revision }} {{ event.detail }}</li></ol>
        </aside>
      </div>
      <footer>M1 演示原型 · 不发送真实业务请求 · 刷新或重置清空临时上下文 · 用户试用反馈待采集</footer>
    </main>
  </div>
</template>
