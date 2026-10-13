export const DOMAINS = {
  AUTO: {name:'统一需求入口', title:'先说你想完成什么。', description:'四类业务都可以从这里开始。分类不清楚就追问，切换前先由你选择。', intro:'可以说“帮我代取快递”“想借相机”“这一步不懂”或“找人协助活动”。有限规则先提出分类建议，不替你决定。'},
  BORROW: {name:'物品借用', title:'把借还的细节，先说清楚。', description:'用途、规格、时间与交接，按你的情境逐步明确。', intro:'说说你想借什么、用于什么。未知规格和资源是否可借都需要核实。', icon:'↔', sample:'B01'},
  STUDY: {name:'学习辅导', title:'一个小问题，也可以好好问。', description:'不用整理成一份完整申请。贴出当前问题，再继续追问。', intro:'可以直接问某一步。先补充当前问题需要的上下文。', icon:'✦', sample:'L05'},
  RUNNING: {name:'跑腿代办', title:'把取送与交接，约定清楚。', description:'取什么、何时送到、怎样交接；放置点不明确就继续问。', intro:'需要代取、代买还是送东西？先明确物品、取送地点和时间，交接方式可以修改。', icon:'➜', sample:'R05'},
  OTHER: {name:'其他互助', title:'先把协助范围，说明白。', description:'设备排查、活动协助等未归入前三类的目标，逐步明确边界与完成标准。', intro:'想请别人帮助完成什么？请说明服务范围、时间地点和怎样判断完成；不确定类别可以重新选择。', icon:'◇', sample:'O01'}
}
const runBase={taskKind:'代取快递',item:'示例普通快递',quantity:'1',pickupPlace:'示例校区北门快递站柜台',destination:'示例校区6号宿舍一层门厅',earliestPickup:'2026-10-14T16:00',latestDelivery:'2026-10-14T17:00',handoff:'当面交付',notice:'短消息',completionProof:'当面确认',language:'中文'}
const otherBase={serviceType:'设备排查',serviceGoal:'排查电脑无法连接校园网络的问题',resourceRequirements:'用户自己的电脑与系统版本；不提供账号密码',serviceStart:'2026-10-14T19:00',serviceEnd:'2026-10-14T20:00',servicePlace:'示例校区图书馆东门公共讨论区',communication:'线下协助',completionCriteria:'检查连接设置并说明原因与下一步；不保证一定修好',consent:'待相关方授权'}
const scene=(id,domain,name,text,special,values,missing,question)=>({id,domain,name,text,special,values,missing,question,hypothesis:true})
export const EXTRA_SCENARIOS=[
  scene('R01','RUNNING','行动或搬运受限','我脚受伤了下不了楼，大件快递想送到宿舍门厅。','mobility',{...runBase,item:'示例大件快递',handling:''},'handling','大约多重、几件？门厅具体在哪，有哪些搬运限制？不能替接单者承诺能搬。'),
  scene('R02','RUNNING','上课静默交接','我16点在上课，17点后才能接快递，请别打电话。','quiet',{...runBase,earliestPickup:'2026-10-14T17:00',latestDelivery:'2026-10-14T17:30',notice:''},'notice','可以交接的具体时间窗口是什么？上课期间选择静默消息，不替你决定等待方式。'),
  scene('R03','RUNNING','新生地点不熟','我是新生，想代取快递，但不知道在哪个快递点。','newcomer',{...runBase,pickupPlace:''},'pickupPlace','先确认校区和快递点名称或可识别地标；不能按“附近”自行选站。'),
  scene('R04','RUNNING','跨语言确认','Please help pick up my parcel. 我想用熟悉的语言确认地点和时间。','language',{...runBase,language:''},'language','地点、时间和交接要求用中文、英文还是中英双语确认？本原型不提供真实翻译。'),
  scene('R05','RUNNING','指定点放置','快递不用当面交，放在指定地点后给我发消息。','dropoff',{...runBase,handoff:'指定点放置',dropPlace:'',completionProof:'放置照片与通知'},'dropPlace','具体放在哪里？位置必须可识别；确认通知方式和放置凭证，不自行选择位置。'),
  scene('O01','OTHER','设备信息不清','电脑连不上校园网，想找人帮忙看看。','diagnosis',{...otherBase,resourceRequirements:''},'resourceRequirements','什么设备和系统，出现什么现象？只补必要信息，不索要账号密码，也不承诺一定修好。'),
  scene('O02','OTHER','多人活动协助','活动布置需要几个人帮忙，但我还没确定人数。','team',{...otherBase,serviceType:'活动协助',serviceGoal:'协助校园活动场地布置',resourceRequirements:'搬动普通桌椅与布置物料，具体分工待协商',completionCriteria:'按明确分工完成桌椅摆放并由发布者确认',helperCount:'',consent:'待相关方授权'},'helperCount','需要几人、各自做什么？当前系统一个任务只有一个接单者；多人需求要说明分工并由用户选择拆分。'),
  scene('O03','OTHER','线上受限协助','我不方便见面，想线上请人帮忙检查软件设置。','remote',{...otherBase,serviceType:'软件设置协助',serviceGoal:'线上指导检查软件设置',servicePlace:'',communication:'线上异步',completionCriteria:'提供检查步骤与设置建议，不要求远程控制'},'servicePlace','通过什么具体渠道联系？不把线上交流默认理解为可以远程控制设备。'),
  scene('O04','OTHER','短时窗口','只有19点到19点半能请人帮忙整理活动资料。','window',{...otherBase,serviceType:'资料整理',serviceGoal:'协助整理校园活动资料',resourceRequirements:'发布者有权分享的活动资料',serviceStart:'',serviceEnd:'2026-10-14T19:30',completionCriteria:'将指定资料分类整理为清单',consent:'无需额外授权'},'serviceStart','具体哪天、几点开始？半小时内完成哪一部分？结束必须晚于开始。'),
  scene('O05','OTHER','协助范围含糊','想找人协助活动，具体做到什么程度还没想好。','scope',{...otherBase,serviceType:'活动协助',serviceGoal:'协助校园活动',resourceRequirements:'具体物料和分工待协商',completionCriteria:''},'completionCriteria','是布置场地、整理物料还是其他工作？怎样算完成？范围不明确不能确认，也不能把“其他”当万能承诺。')
]
