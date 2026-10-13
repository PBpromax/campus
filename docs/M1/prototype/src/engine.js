// M1 finite rules, in-memory only. No model, inventory, payment or business API.
const f = (key, label, type = 'text', options = []) => ({ key, label, type, options })
export const DEFINITIONS = {
  BORROW: [f('item','物品'),f('purpose','用途'),f('spec','规格 / 兼容要求'),f('quantity','数量','number'),
    f('needBy','最晚拿到','datetime-local'),f('useStart','开始使用','datetime-local'),f('useEnd','结束使用','datetime-local'),f('returnAt','归还时间','datetime-local'),f('returnDeadline','出借方最晚归还约束','datetime-local'),
    f('pickupPlace','取件 / 接收位置'),f('returnPlace','归还位置'),f('handoff','交接方式','select',['当面交接','需要送达']),
    f('deliveryConsent','送达条件','select',['待与出借方协商','已获同意（设计输入）']),
    f('support','使用说明','select',['不需要说明','需要简短说明']),f('accessories','必要配件'),f('restrictions','使用限制 / 待协商事项')],
  STUDY: [f('context','问题上下文'),f('topic','知识点'),f('stuckStep','卡住的步骤'),
    f('depth','讲解起点','select',['从基础讲','只看当前一步','先给思路']),f('priority','优先问题'),f('availableTime','可用时间 / 时长'),
    f('communication','沟通方式','select',['短消息','线上异步','线上同步','线下协助']),f('language','解释语言','select',['中文','英文','中英双语'])],
  publication: [f('title','发布标题'),f('description','发布描述'),f('expectedTime','期望完成时间','datetime-local'),f('reward','报酬','number'),f('location','服务位置')]
}
const borrowBase = {
  item:'USB-C 转接器', purpose:'课堂展示连接 HDMI 投影', spec:'USB-C 输入、HDMI 输出；电脑是否支持视频输出待核实', quantity:'1',
  needBy:'2026-10-14T18:30',useStart:'2026-10-14T19:00',useEnd:'2026-10-14T20:00',returnAt:'2026-10-14T20:30',
  pickupPlace:'示例校区图书馆东门服务台',returnPlace:'示例校区图书馆东门服务台',handoff:'当面交接',support:'不需要说明'
}
const studyBase = {context:'第二步 x = -2，第三步 -x = 2，我不懂为什么要取负号。',topic:'代数符号',stuckStep:'第二步到第三步',depth:'只看当前一步',communication:'短消息',language:'中文'}
const scenario = (id, domain, name, text, special, values, missing, question) => ({id,domain,name,text,special,values,missing,question,hypothesis:true})
export const SCENARIOS = [
  scenario('B01','BORROW','规格不确定','明天展示要借一个转接器，我不知道接口怎么选。','compatibility',{...borrowBase,spec:''},'spec','电脑是什么接口？投影设备的接口是什么？不知道的部分保留待核实。'),
  scenario('B02','BORROW','时间紧张','19点就要展示，得提前拿到转接器。','urgent',{...borrowBase,needBy:''},'needBy','最晚何时拿到？开始使用、结束使用和归还分别是什么时间？'),
  scenario('B03','BORROW','取还受限','我行动不方便，想请对方送到宿舍门厅。','handoff',{...borrowBase,handoff:'需要送达',pickupPlace:'示例校区6号宿舍一层门厅',deliveryConsent:''},'deliveryConsent','送达是你的请求，还是已经获得出借方同意？没有同意不能当作既定承诺。'),
  scenario('B04','BORROW','不熟悉使用','想借相机拍活动，但我第一次用，需要简单说明。','beginner',{...borrowBase,item:'相机',purpose:'拍摄校园活动',spec:'可拍照片，型号与使用限制待双方核实',support:'需要简短说明',accessories:''},'accessories','需要哪些配件？例如充电器、存储卡；使用方法与限制须向出借方核实。'),
  scenario('B05','BORROW','跨天归还','周末要借两天，归还时图书馆可能关门。','multiDay',{...borrowBase,needBy:'2026-10-17T08:30',useStart:'2026-10-17T09:00',useEnd:'2026-10-18T18:00',returnAt:'2026-10-18T18:30',returnPlace:''},'returnPlace','归还时在哪里交接？原地点不可用时，请明确替代位置和需协商的约定。'),
  scenario('L01','STUDY','基础薄弱','我连导数定义都没理解，不知道该从哪里问。','foundation',{...studyBase,context:'我不理解导数定义为什么要取极限。',topic:'导数定义',stuckStep:'不知道起点',depth:''},'depth','希望先从极限概念还是导数定义开始？可以选择从基础讲。'),
  scenario('L02','STUDY','时间紧张','我只有20分钟，想先解决最关键的一步。','limited',{...studyBase,availableTime:'20分钟',priority:''},'priority','20分钟内最想弄懂哪一个问题？先缩小范围，再决定讲解方式。'),
  scenario('L03','STUDY','时间地点受限','今晚不方便见面，想发消息慢慢问。','remote',{...studyBase,communication:''},'communication','需要线上异步、线上同步，还是线下协助？普通短消息不要求预约时段。'),
  scenario('L04','STUDY','跨语言解释','英文材料里的 derivative 我不太懂，想看双语解释。','language',{...studyBase,context:'英文教材用 derivative 表示导数，我想理解术语及定义。',topic:'derivative / 导数',language:''},'language','解释用中文、英文，还是中英双语术语？'),
  scenario('L05','STUDY','碎片化小问题','第二步到第三步怎么来的？','fragment',{...studyBase,context:''},'context','请贴第二步和第三步，或补一小段题目；不需要重新填写整份资料。')
]
const clone = value => JSON.parse(JSON.stringify(value))
const terminal = s => ['cancelled','handed_off','resolved'].includes(s.status)
export function createSession(domain = 'BORROW', id = '') {
  if (!['BORROW','STUDY'].includes(domain)) throw new Error('Unsupported domain')
  const sample = SCENARIOS.find(x => x.id === id && x.domain === domain)
  const fields = sample ? clone(sample.values) : {}
  return {domain,scenarioId:sample?.id || '',special:sample?.special || '',action:domain === 'BORROW' ? 'draft' : 'chat',fields,
    provenance:Object.fromEntries(Object.keys(fields).map(k=>[k,{kind:'design_sample',confirmed:false}])),
    revision:1,status:'clarifying',confirmation:null,history:[],messages:[{role:'assistant',text:sample?.question || (domain === 'BORROW' ? '说说你想借什么、用于什么。未知规格和资源是否可借都需要核实。' : '可以直接问某一步。先补充当前问题需要的上下文。')}],outcome:''}
}
export function fieldDefs(s, all = false) {
  const fields = DEFINITIONS[s.domain].filter(x => {
    if (s.domain === 'BORROW') {
      if(all)return true
      if(x.key==='deliveryConsent')return s.fields.handoff==='需要送达'
      if(x.key==='accessories')return s.special==='beginner' || s.fields.support==='需要简短说明'
      if(x.key==='returnDeadline')return s.special==='multiDay' || !!s.fields.returnDeadline
      return true
    }
    return !(['priority','availableTime'].includes(x.key) && s.special !== 'limited')
  })
  return s.action === 'publish' ? [...fields,...DEFINITIONS.publication] : fields
}
const definitePlace = value => !!String(value || '').trim() && !/某个|随便|附近|不知道|不确定|待定|哪里都行/.test(value)
const parseTime = value => {
  const m=String(value || '').match(/^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})$/)
  if(!m)return null
  const d=new Date(value)
  if(Number.isNaN(d.getTime()) || d.getFullYear()!==Number(m[1]) || d.getMonth()+1!==Number(m[2]) || d.getDate()!==Number(m[3]) || d.getHours()!==Number(m[4]) || d.getMinutes()!==Number(m[5]))return null
  return d.getTime()
}
export function assess(s) {
  const required = s.domain === 'BORROW' ? ['item','purpose','spec','quantity','needBy','useStart','useEnd','returnAt','pickupPlace','returnPlace','handoff'] : ['context']
  if (s.domain === 'BORROW' && s.fields.handoff === '需要送达') required.push('deliveryConsent')
  if (s.domain === 'BORROW' && (s.special === 'beginner' || s.fields.support === '需要简短说明')) required.push('support','accessories')
  if (s.domain === 'STUDY') {
    if (s.special === 'foundation') required.push('topic','depth')
    if (s.special === 'limited') required.push('priority','availableTime')
    if (s.special === 'remote') required.push('communication')
    if (s.special === 'language') required.push('language')
  }
  if (s.action === 'publish') {required.push(...DEFINITIONS.publication.map(x=>x.key));if(s.domain==='STUDY')required.push('communication')}
  const missing = [...new Set(required.filter(Boolean))].filter(k => !String(s.fields[k] ?? '').trim())
  const risks = []
  const defs = [...DEFINITIONS[s.domain],...DEFINITIONS.publication]
  for (const def of defs) {
    const v=s.fields[def.key]
    if (!String(v ?? '').trim())continue
    if (def.type==='select' && !def.options.includes(v)) risks.push(`${def.label}不在允许选项中`)
  }
  if (s.domain === 'BORROW') {
    for (const key of ['pickupPlace','returnPlace']) if (s.fields[key] && !definitePlace(s.fields[key])) risks.push(`${defs.find(x=>x.key===key).label}不明确，请填写可识别的位置`)
    if (s.fields.spec && /^(不知道|不确定|随便|待定)$/.test(s.fields.spec.trim())) risks.push('规格仍未知，请说明可核实的用途、接口或尺寸')
    const q=Number(s.fields.quantity)
    if (s.fields.quantity && (!Number.isInteger(q) || q<1 || q>99)) risks.push('数量必须是1—99的整数')
    const times={}
    for (const key of ['needBy','useStart','useEnd','returnAt','returnDeadline']) {
      times[key]=parseTime(s.fields[key])
      if (s.fields[key] && times[key]===null) risks.push(`${defs.find(x=>x.key===key).label}需要具体日期和时间`)
    }
    if (times.needBy!==null && times.useStart!==null && times.needBy>times.useStart) risks.push('最晚拿到晚于开始使用，请调整时间')
    if (times.useStart!==null && times.useEnd!==null && times.useStart>=times.useEnd) risks.push('结束使用必须晚于开始使用')
    if (times.returnAt!==null && times.useEnd!==null && times.returnAt<times.useEnd) risks.push('归还早于结束使用，请调整借期或归还时间')
    if(times.returnAt!==null && times.returnDeadline!==null && times.returnAt>times.returnDeadline)risks.push('归还晚于出借方明确的最晚约束；需重新协商，不能自行延长')
  }
  if (s.action === 'publish') {
    if (s.fields.expectedTime && parseTime(s.fields.expectedTime)===null) risks.push('期望完成时间需要具体日期和时间')
    if (s.fields.reward!==undefined && s.fields.reward!=='' && (!Number.isFinite(Number(s.fields.reward)) || Number(s.fields.reward)<=0)) risks.push('原系统发布报酬必须大于0；无偿需求属于待升级缺口')
    if (s.fields.location && !definitePlace(s.fields.location)) risks.push('发布位置不明确，请补充')
    if (s.fields.title?.length>100) risks.push('标题超过原系统100字符限制')
    if (s.fields.description?.length>500) risks.push('描述超过原系统500字符限制')
  }
  const labels = Object.fromEntries(defs.map(x=>[x.key,x.label]))
  return {missing,risks,ready:!missing.length && !risks.length && !terminal(s),question:risks[0] || (missing.length ? `还需要确认：${missing.map(k=>labels[k]||k).join('、')}。` : s.domain==='STUDY' && s.action==='chat' ? '上下文已保留，可以继续问当前一步，或标记已解决。示例回应不代表真实答疑。' : '草稿已具备必要信息，请核对后确认；这不会执行真实业务。')}
}
function revise(s) {
  s.history.push({fields:clone(s.fields),provenance:clone(s.provenance),action:s.action})
  if (s.history.length>20)s.history.shift()
  s.revision++;s.confirmation=null;s.status='clarifying';s.outcome=''
}
export function updateFields(s, patch, kind='user_declared') {
  if(terminal(s))return false
  const allowed=new Set([...DEFINITIONS[s.domain],...DEFINITIONS.publication].map(x=>x.key))
  const changed=Object.fromEntries(Object.entries(patch).filter(([k,v])=>allowed.has(k) && String(s.fields[k]??'')!==String(v)))
  if(!Object.keys(changed).length)return false
  revise(s)
  for(const [k,v] of Object.entries(changed)) {s.fields[k]=String(v);s.provenance[k]={kind,confirmed:false}}
  if(changed.handoff && s.domain==='BORROW') {
    // A change of handoff requires a fresh position and consent, not invented delivery.
    if(!Object.hasOwn(changed,'pickupPlace'))delete s.fields.pickupPlace
    if(!Object.hasOwn(changed,'deliveryConsent'))delete s.fields.deliveryConsent
  }
  s.status=assess(s).ready?'ready':'clarifying'
  return true
}
export function preparePublish(s) {
  if(terminal(s) || s.action==='publish')return false
  revise(s);s.action='publish'
  if(!s.fields.title) {s.fields.title=s.domain==='BORROW'?`借用：${s.fields.item||'待定物品'}`:`学习求助：${s.fields.topic||'当前问题'}`;s.provenance.title={kind:'derived_draft',confirmed:false}}
  if(!s.fields.description) {s.fields.description=s.domain==='STUDY'?(s.fields.context||''):Object.entries(s.fields).filter(([k])=>!['title','description'].includes(k)).map(([k,v])=>`${DEFINITIONS.BORROW.find(x=>x.key===k)?.label||k}：${v}`).join('；');s.provenance.description={kind:'derived_draft',confirmed:false}}
  s.messages.push({role:'assistant',text:'准备模拟发布。标题和描述是可修改的派生草稿；报酬、位置、完成时间需要你明确填写，系统不会默认补齐。'})
  return true
}
export function resumeDraft(s) {
  if(terminal(s) || s.action!=='publish')return false
  revise(s);s.action=s.domain==='STUDY'?'chat':'draft';return true
}
export function sendMessage(s, text) {
  if(terminal(s) || !text.trim())return false
  s.messages.push({role:'user',text})
  const patch={}
  const defs=[...DEFINITIONS[s.domain],...DEFINITIONS.publication]
  for(const part of text.split(/[；;\n]/)) {
    const match=part.match(/^\s*([^：:]+)[：:]\s*(.+)$/)
    if(match) {
      const def=defs.find(d=>d.label===match[1].trim() || d.key===match[1].trim())
      if(def)patch[def.key]=match[2].trim()
    }
  }
  if(s.domain==='STUDY') {
    if(/上下文[：:]/.test(text))patch.context=text.replace(/^.*?上下文[：:]\s*/,'').trim()
    else if(/[=→]/.test(text) && !Object.keys(patch).length)patch.context=text
    const steps=[...new Set(text.match(/第[一二三四五\d]+步/g)||[])]
    if(steps.length)patch.stuckStep=steps.join('到')
    if(/从基础讲/.test(text))patch.depth='从基础讲'
    if(/线上异步/.test(text))patch.communication='线上异步'
    if(/中英双语/.test(text))patch.language='中英双语'
    if(/只有\s*\d+\s*分钟/.test(text)) {patch.availableTime=text.match(/\d+\s*分钟/)[0];s.special='limited'}
  } else {
    if(/(?:改成|改为|希望|需要)送达/.test(text))patch.handoff='需要送达'
    if(/(?:改成|改为)当面/.test(text))patch.handoff='当面交接'
    const item=text.match(/(?:想借|借一个|借一台)\s*([^，。；\n]+)/)
    if(item)patch.item=item[1]
  }
  updateFields(s,patch)
  const a=assess(s)
  s.messages.push({role:'assistant',text:s.domain==='STUDY' && a.ready && s.action==='chat' ? `保留的上下文：${s.fields.context}\n${s.fields.stuckStep?'当前线索：'+s.fields.stuckStep+'。':''}你可以补一句追问；本原型只演示线索延续，不生成数学答案。` : a.question})
  return true
}
export function confirm(s) {
  if(terminal(s) || s.domain==='STUDY' && s.action==='chat' || !assess(s).ready)return false
  if(s.confirmation?.revision===s.revision)return true
  s.confirmation={revision:s.revision,snapshot:clone(s.fields),action:s.action}
  s.status='confirmed';s.outcome=s.action==='publish'?'模拟发布完成：只确认本地草稿，未产生真实需求ID。':'借用草稿已模拟确认：资源可用性和出借方承诺仍须核实。'
  return true
}
export function undo(s) {
  if(terminal(s) || !s.history.length)return false
  const old=s.history.pop();s.fields=old.fields;s.provenance=old.provenance;s.action=old.action
  s.revision++;s.confirmation=null;s.status='clarifying';s.outcome='已撤销上次修改，需按当前草稿重新确认。';return true
}
export function cancel(s) {if(terminal(s))return false;s.status='cancelled';s.confirmation=null;s.outcome='本地草稿已撤销；未调用真实取消接口。';return true}
export function handoff(s) {if(terminal(s))return false;s.status='handed_off';s.confirmation=null;s.outcome='模拟人工接管：显示上下文交接摘要，没有发送给真实人员。';return true}
export function resolve(s) {if(terminal(s)||s.domain!=='STUDY'||s.action!=='chat'||!assess(s).ready)return false;s.status='resolved';s.outcome='你已标记问题解决。这是用户声明，不是答疑效果评测。';return true}
export function publicationPreview(s) {
  if(s.action!=='publish')return null
  return {category:s.domain,...Object.fromEntries(DEFINITIONS.publication.map(x=>[x.key,s.fields[x.key]??'']))}
}
