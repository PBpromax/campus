// Display helpers only; permissions, versions and confirmation remain server enforced.
export class Palette {
  static ink: string = '#1A332E';
  static green: string = '#246B53';
  static canvas: string = '#F6F7F2';
  static soft: string = '#EAF1E9';
  static muted: string = '#7B8981';
  static line: string = '#E2E8DF';
}
export function userAnswer(text: string): string {
  return text.replace(/<(think|analysis|reasoning)>[\s\S]*?<\/(think|analysis|reasoning)>/gi, '')
    .replace(/<(think|analysis|reasoning)>[\s\S]*$/gi, '').trim();
}
export function statusLabel(status: string): string {
  if (status === 'PENDING') { return '待接单'; }
  if (status === 'IN_PROGRESS') { return '进行中'; }
  if (status === 'PENDING_CONFIRMATION') { return '待确认完成'; }
  if (status === 'COMPLETED') { return '已完成'; }
  if (status === 'CANCELLED') { return '已取消'; }
  if (status === 'PREPARED') { return '待你确认'; }
  if (status === 'SUCCEEDED') { return '已完成操作'; }
  if (status === 'REVOKED') { return '已撤回'; }
  return '需要重新确认';
}
export function shortDate(value: string): string { return value.replace('T', ' ').substring(0,16); }
export function friendlyNotice(value: string): string {
  if (value.includes('409') || value.includes('版本') || value.includes('过期')) { return '内容已有更新，请刷新后重新确认。你的输入仍保留。'; }
  if (value.includes('401') || value.includes('登录已')) { return '登录已过期，请重新登录。'; }
  if (value.includes('模型密钥') || value.includes('尚未配置')) { return '助手暂时无法回复，你可以手动补充需求，或稍后再试。'; }
  if (value.includes('503')) { return '服务暂时不可用，请保留输入，稍后再试。'; }
  if (value.includes('429') || value.includes('额度')) { return '助手使用已达上限，你仍可以手动发布和联系同学。'; }
  if (value.includes('网络') || value.includes('HTTP') || value.includes('request') || value.includes('超时')) { return '暂时连接不上，请稍后重试。你的输入仍保留。'; }
  return value;
}
