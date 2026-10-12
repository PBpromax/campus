export const scenarios = [
  { id: 'E01', business: 'EXPRESS', title: '行动不便', subtitle: '交接位置适应接收能力', input: '我脚受伤不能下楼，仙林校区有三个快递在北门驿站，希望今晚送到宿舍楼门口。', change: '澄清件数、搬运限制与可接收位置，不推断长期身体状况。' },
  { id: 'E02', business: 'EXPRESS', title: '上课中', subtitle: '时间窗口与静默通知', input: '仙林校区北门驿站有一个快递，我上课到21点，21点以后才能当面交付，上课期间请静默通知。', change: '确认取件截止时间能否覆盖21点后的交接，必要时比较调整方案。' },
  { id: 'E03', business: 'EXPRESS', title: '新生认路', subtitle: '地点含糊时先确认', input: '我是新生，想取一个快递，不知道北门是哪一个校区，也不知道在哪里交接。', change: '校区和可识别地点都保持待确认，不自动补成仙林或鼓楼。' },
  { id: 'E04', business: 'EXPRESS', title: '跨语言沟通', subtitle: '业务字段保持同一含义', input: '我在仙林校区，北门驿站有一个快递，需要中英双语确认，今晚当面交付。', change: '使用双语交接标签，地点名称仍需由用户确认。' },
  { id: 'E05', business: 'EXPRESS', title: '交接方式', subtitle: '当面交付 / 指定点放置', input: '仙林校区北门驿站有两个快递，今晚放在某个地点就行，放好通知我。', change: '某个地点不是有效交接位置；补齐地点和放置后的完成确认。' },
  { id: 'L01', business: 'TUTORING', title: '基础薄弱', subtitle: '先找讲解起点', input: '高数求助，我基础薄弱，导数和极限的关系不太明白，不知道该从哪里开始。', change: '用一个起点问题定位缺口，不一次索取整套学习档案。' },
  { id: 'L02', business: 'TUTORING', title: '时间紧张', subtitle: '先解决最关键的一点', input: '我今晚只有20分钟复习高数，时间很紧，链式法则一直没明白。', change: '明确优先问题与20分钟范围，避免默认安排长期辅导。' },
  { id: 'L03', business: 'TUTORING', title: '时空受限', subtitle: '在线 / 异步 / 短时', input: '我想请同学讲高数里的链式法则，只能在线异步交流，今晚没有整块时间。', change: '异步沟通只澄清回复节奏；预约实时协助时才要求时段。' },
  { id: 'L04', business: 'TUTORING', title: '跨语言解释', subtitle: '双语术语与讲解偏好', input: '我在学高数，想问链式法则，希望用中英双语解释相关术语。', change: '对齐 chain rule / 链式法则等术语，并询问讲解深度。' },
  { id: 'L05', business: 'TUTORING', title: '碎片小问题', subtitle: '聊一句，也能接着问', input: '第二步到第三步怎么来的？我只卡在这一小步。', change: '只追问这两步的表达式，继续消息保留上下文；不要求预算、校区或预约时间。' },
]

export const statusLabels = {
  clarifying: '需要补充', ready: '可以确认', confirmed: '已模拟确认',
  cancelled: '已撤销', handed_off: '已模拟接管', resolved: '问题已解决',
}
