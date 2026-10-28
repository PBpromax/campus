import { Palette, friendlyNotice, statusLabel } from './Presentation';
import { http } from '@kit.NetworkKit';
import { BusinessError } from '@kit.BasicServicesKit';
import { NeedApi } from '../data/NeedApi';
import { TaskSummary } from '../domain/ToolModels';

export interface HumanMessage { id: number; demandId: number; senderId: number; senderName: string; receiverId: number; content: string; createdAt: string; }
export interface HumanSend { demandId: number; receiverId: number; content: string; }
export function partner(task: TaskSummary, userId: number): number | null {
  if (task.publisherId === userId) { return task.acceptorId; }
  return task.acceptorId === userId ? task.publisherId : null;
}

@Component
export struct HumanHelp {
  api: NeedApi = new NeedApi();
  @Prop blocked: boolean = false;
  onWorking: (working: boolean) => void = (_working: boolean): void => {};

  @State private working: boolean = false;
  @State private tasks: TaskSummary[] = [];
  @State private selected: TaskSummary | undefined = undefined;
  @State private messages: HumanMessage[] = [];
  @State private input: string = '';
  @State private notice: string = '';
  private lock(value: boolean): void { this.working = value; this.onWorking(value); }
  private async load(): Promise<void> {
    this.lock(true);
    try { this.tasks = await this.api.send<TaskSummary[]>('/trusted-tools/tasks?scope=mine'); this.notice = '';  }
    catch (error) { this.notice = (error as BusinessError).message + '。网络不可用时请使用你已有的线下联系渠道。'; }
    finally { this.lock(false); }
  }
  private async read(task: TaskSummary): Promise<void> {
    if (this.input.trim().length > 0 && this.selected?.id !== task.id) { this.notice = '请先发送或清空未发送消息，再切换协商任务。'; return; }
    this.selected = task; this.lock(true);
    try { this.messages = await this.api.send<HumanMessage[]>('/messages/demand/' + task.id); this.notice = '';  }
    catch (error) { this.messages = []; this.notice = (error as BusinessError).message; }
    finally { this.lock(false); }
  }
  private async send(): Promise<void> {
    const task = this.selected;
    if (task === undefined || this.input.trim().length === 0 || this.blocked || this.working) { return; }
    const receiver = partner(task,this.api.currentUserId());
    if (receiver === null) { this.notice = '没有可联系的任务另一方。'; return; }
    this.lock(true);
    try {
      const body: HumanSend = { demandId: task.id, receiverId: receiver, content: this.input.trim() };
      await this.api.send<HumanMessage>('/messages',http.RequestMethod.POST,JSON.stringify(body));
      this.input = ''; this.notice = '消息已发送给任务另一方，等待对方回应。';
      this.messages = await this.api.send<HumanMessage[]>('/messages/demand/' + task.id);
    } catch (error) { this.notice = (error as BusinessError).message + '。未自动重发；请先刷新记录，核对是否已经发送。'; }
    finally { this.lock(false); }
  }
  aboutToAppear(): void { this.load(); }
  build() {
    Column({ space: 17 }) {
      Row() {
        Text('消息').fontSize(28).fontWeight(FontWeight.Bold).fontColor(Palette.ink); Blank()
        Text(this.working ? '更新中' : '刷新').fontSize(13).fontColor(Palette.green).padding(8).onClick(() => { if (!this.working && !this.blocked) { this.load(); } })
      }.width('100%')
      Text('把约定说清楚，互助更安心。').fontSize(13).fontColor(Palette.muted).width('100%')
      if (this.tasks.filter((task: TaskSummary) => partner(task,this.api.currentUserId()) !== null).length === 0) {
        Column({ space: 15 }) {
          Image($rawfile('icon-chat.svg')).width(38).height(38)
          Text('还没有互助消息').fontSize(18).fontWeight(FontWeight.Medium).fontColor(Palette.ink)
          Text('接单后，就能在这里联系对方，\n确认时间、地点和其他约定。').fontSize(13).fontColor(Palette.muted).textAlign(TextAlign.Center).lineHeight(23)
        }.width('100%').padding({ top: 60, bottom: 60 }).backgroundColor('#FFFFFF').borderRadius(22)
      }
      ForEach(this.tasks.filter((task: TaskSummary) => partner(task,this.api.currentUserId()) !== null), (task: TaskSummary) => {
        Row({ space: 12 }) {
          Text('↗').fontSize(22).fontColor(Palette.green).width(42).height(42).textAlign(TextAlign.Center).backgroundColor(Palette.soft).borderRadius(13)
          Column({ space: 5 }) { Text(task.title).fontSize(15).fontWeight(FontWeight.Medium).fontColor(Palette.ink).maxLines(1); Text(statusLabel(task.status)).fontSize(11).fontColor(Palette.muted) }.layoutWeight(1).alignItems(HorizontalAlign.Start)
          Text('›').fontSize(20).fontColor(Palette.muted)
        }.width('100%').padding(15).backgroundColor(this.selected?.id === task.id ? '#E6EDDF' : '#FFFFFF').borderRadius(16)
          .id('conversation-' + task.id).onClick(() => { if (!this.blocked && !this.working) { this.read(task); } })
      }, (task: TaskSummary) => task.id.toString() + task.status)
      if (this.selected !== undefined) {
        Column({ space: 14 }) {
          Row() { Text('与对方聊聊').fontSize(17).fontWeight(FontWeight.Bold).fontColor(Palette.ink); Blank(); Text('刷新消息').fontSize(12).fontColor(Palette.green).onClick(() => { if (!this.blocked && !this.working && this.selected !== undefined) { this.read(this.selected); } }) }.width('100%')
          if (this.messages.length === 0) { Text('还没有消息，先打个招呼吧。').fontSize(13).fontColor(Palette.muted) }
          ForEach(this.messages, (message: HumanMessage) => {
            Column({ space: 5 }) {
              Text(message.senderId === this.api.currentUserId() ? '我' : '对方').fontSize(11).fontColor(Palette.muted)
              Text(message.content).fontSize(14).fontColor(Palette.ink).lineHeight(22).width('100%')
            }.width('100%').padding(13).borderRadius(13).backgroundColor(message.senderId === this.api.currentUserId() ? '#EAF0E4' : Palette.canvas).alignItems(HorizontalAlign.Start)
          }, (message: HumanMessage) => message.id.toString())
          Row({ space: 8 }) {
            TextInput({ text: this.input, placeholder: '给对方发条消息…' }).maxLength(500).fontSize(14).height(46).backgroundColor(Palette.canvas).borderRadius(12)
              .layoutWeight(1).id('human-message').accessibilityText('发给任务另一方的消息').enabled(!this.blocked && !this.working).onChange((value: string) => { this.input = value; })
            Button('发送').height(44).fontSize(13).borderRadius(12).backgroundColor(Palette.green).id('send-human-message')
              .enabled(!this.blocked && !this.working && this.input.trim().length > 0).onClick(() => { this.send(); })
          }.width('100%')
        }.width('100%').padding(17).backgroundColor('#FFFFFF').borderRadius(18).alignItems(HorizontalAlign.Start)
      }
      if (this.notice.length > 0) { Text(friendlyNotice(this.notice)).fontSize(12).fontColor(Palette.green).lineHeight(20).accessibilityLevel('yes') }
    }.width('100%').constraintSize({ maxWidth: 640 }).padding(20).alignItems(HorizontalAlign.Start)
  }
}
