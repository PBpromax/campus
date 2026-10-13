<script setup>
import { computed, ref } from 'vue'
import { SCENARIOS, createSession, fieldDefs, assess, updateFields, sendMessage, preparePublish, resumeDraft, confirm, undo, cancel, handoff, resolve, publicationPreview } from './engine.js'
const session = ref(createSession('BORROW','B01'))
const text = ref('')
const fixed = ref(false)
const active = computed(()=>SCENARIOS.find(x=>x.id===session.value.scenarioId))
const samples = computed(()=>SCENARIOS.filter(x=>x.domain===session.value.domain))
const assessment = computed(()=>assess(session.value))
const fields = computed(()=>fieldDefs(session.value,fixed.value))
const closed = computed(()=>['cancelled','handed_off','resolved'].includes(session.value.status))
const showCard = computed(()=>session.value.domain==='BORROW' || session.value.action==='publish')
const preview = computed(()=>publicationPreview(session.value))
const statusLabel = computed(()=>session.value.status==='ready' && session.value.domain==='STUDY' && session.value.action==='chat'?'可继续交流':({clarifying:'继续澄清',ready:'待你确认',confirmed:'模拟已确认',cancelled:'草稿已撤销',handed_off:'模拟接管',resolved:'用户标记已解决'}[session.value.status]))
function load(domain,id='') {session.value=createSession(domain,id);text.value='';fixed.value=false}
function send() {if(sendMessage(session.value,text.value))text.value=''}
function edit(key,value){updateFields(session.value,{[key]:value})}
function reset(){load(session.value.domain,session.value.scenarioId)}
</script>

<template>
  <div class="app" :class="session.domain==='BORROW'?'borrow':'study'">
    <header class="top"><a class="brand" href="#">campus<span> / 每一种需要</span></a><span class="stage">M1 · 交互实验室</span></header>
    <div class="notice">规则模拟 · 设计样本待验证 · 不调用模型、不发布任务、不连接物品库存或真实人员</div>
    <div class="layout">
      <aside class="sidebar">
        <div class="eyebrow">从一个具体目标开始</div>
        <nav class="domains" aria-label="业务入口">
          <button data-testid="domain-borrow" :class="{selected:session.domain==='BORROW'}" @click="load('BORROW','B01')"><span>↔</span><div>物品借用<small>借得合适，也还得清楚</small></div></button>
          <button data-testid="domain-study" :class="{selected:session.domain==='STUDY'}" @click="load('STUDY','L05')"><span>✦</span><div>学习辅导<small>从卡住的那一步开始</small></div></button>
        </nav>
        <div class="side-heading">五种特殊情境 <span>设计假设</span></div>
        <div class="samples"><button v-for="sample in samples" :key="sample.id" :data-testid="sample.id" :class="{selected:session.scenarioId===sample.id}" @click="load(sample.domain,sample.id)"><span>{{ sample.id }}</span>{{ sample.name }}<b>›</b></button></div>
        <div class="side-note"><strong>你的修改优先</strong><p>地点不明就追问，约定变了就重新确认。资源是否可用、对方是否同意都不能猜。</p><button class="text-button" data-testid="blank" @click="load(session.domain)">从空白需求开始 →</button></div>
      </aside>

      <main>
        <section class="hero"><div class="eyebrow">{{ session.domain }} / PERSONALIZED HELP</div><h1>{{ session.domain==='BORROW'?'把借还的细节，先说清楚。':'一个小问题，也可以好好问。' }}</h1><p>{{ session.domain==='BORROW'?'用途、规格、时间与交接，按你的情境逐步明确。':'不用整理成一份完整申请。贴出当前问题，再继续追问。' }}</p><div class="status"><span class="dot"></span>{{ statusLabel }}<span class="revision">草稿版本 {{ session.revision }}</span></div></section>

        <div v-if="active" class="sample-note"><strong>{{ active.name }} · 用户原话</strong><p>“{{ active.text }}”</p><small>场景预填值都是设计数据，包含原话之外的示例约定；不代表真实访谈或已获同意。</small></div>

        <div class="workspace">
          <section class="conversation panel" aria-label="连续对话"><div class="panel-head"><h2>先聊聊你的需要</h2><span class="tag">模拟澄清</span></div>
            <div class="messages" aria-live="polite"><div v-for="(message,i) in session.messages" :key="i" class="message" :class="message.role"><span class="avatar">{{message.role==='user'?'你':'✦'}}</span><div><small>{{message.role==='user'?'你的表达':'规则助手 · 模拟'}}</small><p>{{message.text}}</p></div></div></div>
            <form class="composer" @submit.prevent="send"><label for="message-input">继续表达或纠正</label><textarea id="message-input" data-testid="message" v-model="text" :disabled="closed" rows="3" :placeholder="session.domain==='BORROW'?'例如：物品：相机；用途：拍校园活动。\n也可以在右侧补充具体约定。':'例如：第二步 x = -2，第三步 -x = 2。\n或者继续问：这里为什么要取负号？'"></textarea><div><small>有限规则识别；无法识别时用结构化补充。</small><button class="primary" data-testid="send" :disabled="closed||!text.trim()">发送 ↗</button></div></form>
            <div class="conversation-actions"><button data-testid="prepare-publish" :disabled="closed||session.action==='publish'" @click="preparePublish(session)">准备发布求助 / 借用</button><button v-if="session.action==='publish'" data-testid="resume" :disabled="closed" @click="resumeDraft(session)">返回{{ session.domain==='STUDY'?'普通对话':'借用草稿' }}</button><button v-if="session.domain==='STUDY' && session.action==='chat'" data-testid="resolve" :disabled="closed||!assessment.ready" @click="resolve(session)">标记已解决</button></div>
          </section>

          <section class="details panel" aria-label="结构化补充"><div class="panel-head"><h2>{{ fixed?'固定表单回退':'按需补充' }}</h2><button class="text-button" data-testid="fixed-form" @click="fixed=!fixed">{{fixed?'返回动态补充':'固定表单 →'}}</button></div><p class="hint">{{assessment.question}}</p>
            <div class="field-list" :class="{'fixed-form':fixed}"><div v-for="field in fields" :key="field.key" class="field" :class="{missing:assessment.missing.includes(field.key)}">
              <label :for="'field-'+field.key">{{field.label}}<span v-if="assessment.missing.includes(field.key)">待确认</span></label>
              <select v-if="field.type==='select'" :id="'field-'+field.key" :data-testid="'field-'+field.key" :value="session.fields[field.key]||''" :disabled="closed" @change="edit(field.key,$event.target.value)"><option value="">请选择 / 保留未知</option><option v-for="option in field.options" :key="option">{{option}}</option></select>
              <textarea v-else-if="['context','description','restrictions'].includes(field.key)" :id="'field-'+field.key" :data-testid="'field-'+field.key" :value="session.fields[field.key]||''" :disabled="closed" rows="2" @change="edit(field.key,$event.target.value)"></textarea>
              <input v-else :id="'field-'+field.key" :data-testid="'field-'+field.key" :type="field.type" :step="field.key==='reward'?'0.01':field.type==='number'?'1':undefined" :value="session.fields[field.key]||''" :disabled="closed" @change="edit(field.key,$event.target.value)">
              <small v-if="session.provenance[field.key]">{{session.provenance[field.key].kind==='design_sample'?'设计样本输入':session.provenance[field.key].kind==='derived_draft'?'派生草稿 · 可修改':'用户明确输入'}}</small>
            </div></div>
          </section>
        </div>

        <section v-if="assessment.risks.length" class="risk" data-testid="risks" role="alert"><strong>约定还有冲突</strong><p v-for="risk in assessment.risks" :key="risk">{{risk}}</p></section>
        <section v-if="showCard" class="confirmation panel" data-testid="confirmation-card"><div class="panel-head"><h2>{{session.action==='publish'?'发布确认卡':'借用约定卡'}}</h2><span class="tag">版本 {{session.revision}}</span></div><p v-if="session.domain==='BORROW'" class="hint">这是借用请求草稿，不表示物品真实可用，也不表示出借方已承诺。送达与使用限制须双方核实。</p><p v-else class="hint">只有准备发布时才出现确认卡；确认对象是这里展示的当前草稿。</p><dl><template v-for="field in fields.filter(f=>String(session.fields[f.key]||'').trim())" :key="field.key"><dt>{{field.label}}</dt><dd>{{session.fields[field.key]}}</dd></template></dl><p v-if="!assessment.ready" class="hint">必要信息或冲突解决之前不能确认。</p><button class="primary" data-testid="confirm" :disabled="closed||!assessment.ready" @click="confirm(session)">{{session.action==='publish'?'确认并模拟发布':'确认借用草稿（模拟）'}}</button><details v-if="preview"><summary>查看原系统发布参数预览（不会发送）</summary><pre>{{JSON.stringify(preview,null,2)}}</pre></details></section>
        <section v-else class="context-summary" data-testid="context-summary"><strong>问题线索会延续</strong><p>{{session.fields.context||'补充当前题目或两步表达式后，就可以继续短消息追问。'}}</p><small>不要求预算、地点或预约时间；用户标记解决之前，持续保留当前上下文。</small></section>

        <div v-if="session.outcome" class="outcome" data-testid="outcome" aria-live="polite">{{session.outcome}}</div>
        <footer class="controls"><button data-testid="undo" :disabled="closed||!session.history.length" @click="undo(session)">撤销上次修改</button><button data-testid="cancel" :disabled="closed" @click="cancel(session)">撤销本次草稿</button><button data-testid="handoff" :disabled="closed" @click="handoff(session)">模拟人工接管</button><button data-testid="reset" @click="reset">重置当前情境</button></footer>
        <p class="footnote">基于老师项目 sec-ii-2026 · BORROW / STUDY · 本页没有生产网络请求和持久化存储，刷新会重置。原平台状态由后端保存；这里仅为 M1 对话状态。</p>
      </main>
    </div>
  </div>
</template>
