import { util } from '@kit.ArkTS';

export interface AiEvent { kind: string; data: string; }
export interface AiText { text: string; }
export interface AiNotice { message: string; }
export class AiStreamDecoder {
  private decoder: util.TextDecoder = new util.TextDecoder('utf-8', { fatal: true });
  private pending: string = '';
  private total: number = 0;
  completed: boolean = false;
  feed(bytes: ArrayBuffer, emit: (event: AiEvent) => void): void {
    this.total += bytes.byteLength;
    if (this.total > 2500000) { throw new Error('响应过大，请缩小会话范围'); }
    this.pending = (this.pending + this.decoder.decodeToString(new Uint8Array(bytes), { stream: true })).replace(/\r\n/g, '\n');
    if (this.pending.length > 1000000) { throw new Error('响应片段过大'); }
    let index = this.pending.indexOf('\n\n');
    while (index >= 0) {
      const frame = this.pending.substring(0,index); this.pending = this.pending.substring(index+2);
      let kind = ''; const data: string[] = [];
      frame.split('\n').forEach((line: string) => {
        if (line.startsWith('event:')) { kind = line.substring(6).trim(); }
        else if (line.startsWith('data:')) { data.push(line.substring(5).trimStart()); }
      });
      if (kind.length > 0) {
        if (this.completed || !['session','text_delta','proposal','notice','failure','done'].includes(kind)) { throw new Error('对话事件无效'); }
        const payload = data.join('\n'); JSON.parse(payload) as Object;
        if (kind === 'done') { this.completed = true; }
        emit({ kind: kind, data: payload });
      }
      index = this.pending.indexOf('\n\n');
    }
  }
  finish(): void {
    const tail = this.decoder.decodeToString(new Uint8Array(0), { stream: false });
    if (tail.length > 0 || this.pending.trim().length > 0 || !this.completed) { throw new Error('回复中断，重新加载后可继续使用表单'); }
  }
}
