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
  @State private expanded: boolean = false;
  @State private working: boolean = false;
  @State private tasks: TaskSummary[] = [];
  @State private selected: TaskSummary | undefined = undefined;
  @State private messages: HumanMessage[] = [];
  @State private input: string = '';
  @State private notice: string = '';
  private lock(value: boolean): void { this.working = value; this.onWorking(value); }
  private async load(): Promise<void> {
    this.lock(true);
    try { this.tasks = await this.api.send<TaskSummary[]>('/trusted-tools/tasks?scope=mine'); this.notice = '请选择本人已被接单的任务，消息只发给该任务另一方。'; }
    catch (error) { this.notice = (error as BusinessError).message + '。网络不可用时请使用你已有的线下联系渠道。'; }
    finally { this.lock(false); }
  }
  private async read(task: TaskSummary): Promise<void> {
    if (this.input.trim().length > 0 && this.selected?.id !== task.id) { this.notice = '请先发送或清空未发送消息，再切换协商任务。'; return; }
    this.selected = task; this.lock(true);
    try { this.messages = await this.api.send<HumanMessage[]>('/messages/demand/' + task.id); this.notice = '已读取真人消息；不会自动刷新或发送。'; }
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
  build() {
    Column({ space: 10 }) {
      Button(this.expanded ? '收起真人协商' : '需要真人协商 / AI不可用').enabled(!this.blocked && !this.working)
        .onClick(() => { this.expanded = !this.expanded; })
      if (this.expanded) {
        Text('真人协商与固定流程回退').fontSize(20).fontWeight(FontWeight.Bold)
        Text('发布前：可继续使用完整表单，或直接联系你认识的同学核实规格、地点和授权。平台未接入客服队列，不能代替真人同意。').fontSize(14)
        Text('已接单后：在下方选择本人任务，向另一方发送消息。此处不是AI答疑，不会自动发消息或改任务状态。').fontSize(14)
        Button('加载可协商的本人任务').enabled(!this.blocked && !this.working).onClick(() => { this.load(); })
        ForEach(this.tasks.filter((task: TaskSummary) => partner(task,this.api.currentUserId()) !== null), (task: TaskSummary) => {
          Button('协商 #' + task.id + ' ' + task.title).enabled(!this.blocked && !this.working).onClick(() => { this.read(task); })
        },(task: TaskSummary) => task.id.toString() + task.status)
        if (this.selected !== undefined) {
          Text('当前真人协商：任务 #' + this.selected.id).fontWeight(FontWeight.Bold)
          ForEach(this.messages,(message: HumanMessage) => { Text((message.senderId === this.api.currentUserId() ? '我' : '任务另一方') + '：' + message.content).width('100%'); },(message: HumanMessage) => message.id.toString())
          TextInput({ text: this.input, placeholder: '明确发给任务另一方的消息（最多500字）' }).maxLength(500).accessibilityText('真人协商消息')
            .enabled(!this.blocked && !this.working).onChange((value: string) => { this.input = value; })
          Button('发送给任务另一方（真人）').enabled(!this.blocked && !this.working && this.input.trim().length > 0).onClick(() => { this.send(); })
          Button('刷新真人消息').enabled(!this.blocked && !this.working).onClick(() => { if (this.selected !== undefined) { this.read(this.selected); } })
        }
        if (this.working) { Text('正在处理真人消息…') }
        Text(this.notice).fontColor('#285F45')
      }
    }.width('100%').padding(14).backgroundColor('#EEF3FA').alignItems(HorizontalAlign.Start)
  }
}
