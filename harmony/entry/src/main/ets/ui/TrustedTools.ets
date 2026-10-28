import { pickTime } from './PickTime';
import { Palette, friendlyNotice, statusLabel, shortDate } from './Presentation';
import { TransitionRequest, TransitionPreview, TaskEventKind, TaskEvent, projectTransition } from '../domain/TaskTransitions';
import { http } from '@kit.NetworkKit';
import { BusinessError } from '@kit.BasicServicesKit';
import { NeedApi } from '../data/NeedApi';
import { NeedSession, emptySession } from '../domain/NeedModels';
import { ToolHint, ToolPrepare, ToolConfirm, ToolReceipt, ToolAudit, TaskSummary,
  actionName, validHint, publishReady, canExecute } from '../domain/ToolModels';

@Component
export struct TrustedTools {
  api: NeedApi = new NeedApi();
  @Prop session: NeedSession = emptySession();
  @Prop blocked: boolean = false;
  @Prop hint: ToolHint | undefined = undefined;
  onWorking: (working: boolean) => void = (_working: boolean): void => {};
  @State private working: boolean = false;
  @State private action: string = 'ACCEPT';
  @State private scope: string = 'market';
  @State private details: boolean = false;
  @State private historyOpen: boolean = false;
  @State private lookup: boolean = false;
  @Prop @Watch('openPublish') publishRequest: number = 0;
  @State private title: string = '';
  @State private reward: string = '';
  @State private location: string = '';
  @State private time: string = '';
  @State private target: string = '';
  @State private reason: string = '';
  @State private notice: string = '';
  @State private transitionNotice: string = '';
  @State private receipt: ToolReceipt | undefined = undefined;
  @State private receipts: ToolReceipt[] = [];
  @State private audits: ToolAudit[] = [];
  @State private tasks: TaskSummary[] = [];
  private requestKey: string = '';
  private requestSource: string = '';

  private changed(): void { this.transitionNotice = ''; this.requestKey = ''; this.requestSource = ''; this.receipt = undefined; }
  private setWorking(value: boolean): void { this.working = value; this.onWorking(value); }
  private async prepare(): Promise<void> {
    if (this.blocked || this.working) { return; }
    if (this.action === 'PUBLISH' && !publishReady(this.session)) { this.notice = '先在对话中整理并核对需求，再继续发布。'; return; }
    if (this.action === 'PUBLISH' && (this.title.trim().length === 0 || this.location.trim().length === 0 ||
      !/^\d+(\.\d{1,2})?$/.test(this.reward) || Number(this.reward) <= 0 ||
      !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2})?$/.test(this.time))) {
      this.notice = '请填写标题、报酬、地点和完成时间。报酬需大于0。'; return;
    }
    if (this.action !== 'PUBLISH' && (!/^\d+$/.test(this.target) || !Number.isSafeInteger(Number(this.target)) || Number(this.target) <= 0)) {
      this.notice = '请从列表选择任务，或填写准确的任务编号。'; return;
    }
    const source = this.action === 'PUBLISH' ? this.session.id + ':' + this.session.revision : this.action + ':' + this.target;
    if (this.requestKey.length === 0 || this.requestSource !== source) {
      this.requestKey = 'hm_' + Date.now() + '_' + Math.random().toString(36).substring(2,14); this.requestSource = source;
    }
    const body: ToolPrepare = { action: this.action, requestKey: this.requestKey };
    if (this.action === 'PUBLISH') {
      body.sessionId = this.session.id; body.revision = this.session.revision;
      body.publication = { title: this.title.trim(), reward: Number(this.reward), location: this.location.trim(), expectedTime: this.time };
    } else { body.demandId = Number(this.target); if (this.action === 'CANCEL') { body.reason = this.reason.trim(); } }
    this.setWorking(true);
    try { this.receipt = await this.api.send<ToolReceipt>('/trusted-tools/prepare',http.RequestMethod.POST,JSON.stringify(body)); this.notice = ''; this.details = false; }
    catch (error) { this.notice = friendlyNotice((error as BusinessError).message); }
    finally { this.setWorking(false); }
  }
  private async previewTransition(): Promise<void> {
    if (this.blocked || this.working) { return; }
    if (!/^\d+$/.test(this.target) || !Number.isSafeInteger(Number(this.target)) || Number(this.target) <= 0) {
      this.transitionNotice = '请先选择准确的任务编号。'; return;
    }
    this.setWorking(true);
    try {
      const body: TransitionRequest = { demandId: Number(this.target), event: this.action === 'ACCEPT' ? TaskEventKind.ACCEPT : TaskEventKind.CANCEL };
      const result = await this.api.send<TransitionPreview>('/task-transition-preview',http.RequestMethod.POST,JSON.stringify(body));
      const event: TaskEvent = { taskId: result.source.id, id: 'preview', expectedVersion: result.source.version, kind: result.event, participant: result.participant };
      const local = projectTransition(result.source,event);
      if (local.outcome !== result.decision.outcome || local.status !== result.decision.status || local.version !== result.decision.version) {
        throw new Error('两种语言的预览结果不一致，请重新加载核对');
      }
      this.transitionNotice = result.decision.outcome === 'APPLIED' ? '确认后，任务将从“' + statusLabel(result.source.status) + '”变为“' + statusLabel(result.decision.status) + '”。' : '当前暂时无法进行这项操作，请刷新任务状态。';
    } catch (error) { this.transitionNotice = (error as BusinessError).message + '。预览不影响原有确认流程。'; }
    finally { this.setWorking(false); }
  }
  private async execute(receipt: ToolReceipt): Promise<void> {
    if (this.working || !canExecute(receipt,this.session,this.blocked)) { this.notice = '确认卡已失效或有未保存输入，请重新核对。'; return; }
    this.setWorking(true);
    try {
      const body: ToolConfirm = { confirmed: true };
      this.receipt = await this.api.send<ToolReceipt>('/trusted-tools/' + receipt.id + '/execute',http.RequestMethod.POST,JSON.stringify(body));
      this.notice = '';
    } catch (error) { this.notice = friendlyNotice((error as BusinessError).message) + ' 请先刷新操作结果，再决定下一步。'; }
    finally { this.setWorking(false); }
  }
  private async query(id: string, revoke: boolean = false): Promise<void> {
    if (this.working || this.blocked) { return; }
    this.setWorking(true);
    try {
      this.receipt = await this.api.send<ToolReceipt>('/trusted-tools/' + id + (revoke ? '/revoke' : ''),revoke ? http.RequestMethod.POST : http.RequestMethod.GET);
      this.notice = revoke ? '已撤回这次操作，任务没有改变。' : '';
    } catch (error) { this.notice = (error as BusinessError).message; }
    finally { this.setWorking(false); }
  }
  private async history(): Promise<void> {
    this.setWorking(true);
    try {
      this.receipts = await this.api.send<ToolReceipt[]>('/trusted-tools');
      this.audits = await this.api.send<ToolAudit[]>('/trusted-tools/audits');
      this.notice = '';
    } catch (error) { this.notice = (error as BusinessError).message; }
    finally { this.setWorking(false); }
  }
  private async loadTasks(scope: string): Promise<void> {
    this.setWorking(true);
    try { this.tasks = await this.api.send<TaskSummary[]>('/trusted-tools/tasks?scope=' + scope); this.notice = '';  }
    catch (error) { this.notice = (error as BusinessError).message; }
    finally { this.setWorking(false); }
  }
  aboutToAppear(): void { if (this.publishRequest > 0) { this.openPublish(); } else { this.loadTasks('available'); } }
  private openPublish(): void { this.changed(); this.action = 'PUBLISH'; this.scope = 'publish'; this.notice = ''; }
  private selectScope(value: string): void {
    if (this.working || this.blocked) { return; }
    this.changed(); this.scope = value;
    if (value === 'publish') { this.action = 'PUBLISH'; }
    else { this.action = value === 'market' ? 'ACCEPT' : 'CANCEL'; this.loadTasks(value === 'market' ? 'available' : 'mine'); }
  }
  private successTitle(receipt: ToolReceipt): string { return receipt.action === 'PUBLISH' ? '求助已发布' : receipt.action === 'ACCEPT' ? '接单成功' : '已取消这次互助'; }
  private consequence(value: string): string {
    if (value.includes('尚未接入真实支付')) { return '报酬由双方线下结算，本平台暂不收付款。'; }
    if (value.includes('30分钟')) { return '发布后30分钟内且尚未接单时，可以取消。'; }
    if (value.includes('等待其他同学')) { return '发布后等待同学接单，请确保需求描述准确。'; }
    return value.replace('原系统','平台').replace('不会发生真实资金退款','本平台不办理资金退款');
  }
  @Builder
  input(label: string, value: string, placeholder: string, key: string) {
    Column({ space: 7 }) {
      Text(label).fontSize(13).fontColor(Palette.ink).fontWeight(FontWeight.Medium)
      TextInput({ text: key === 'time' ? value.replace('T',' ') : value, placeholder: placeholder }).height(46).fontSize(14).backgroundColor(Palette.canvas).borderRadius(11)
        .maxLength(key === 'reason' ? 500 : 200).enabled(!this.working && !this.blocked).id('publish-' + key).accessibilityText(label)
        .onChange((v: string) => {
          const next = key === 'time' ? v.replace(' ','T') : v;
          const before = key === 'title' ? this.title : key === 'reward' ? this.reward : key === 'location' ? this.location : key === 'time' ? this.time : key === 'target' ? this.target : this.reason;
          if (before !== next) {
            this.changed();
            if (key === 'title') { this.title = next; } else if (key === 'reward') { this.reward = next; } else if (key === 'location') { this.location = next; } else if (key === 'time') { this.time = next; } else if (key === 'target') { this.target = next; } else { this.reason = next; }
          }
        })
    }.width('100%').alignItems(HorizontalAlign.Start)
  }
  private manageable(task: TaskSummary): boolean { return this.scope === 'market' || (task.status === 'PENDING' && task.publisherId === this.api.currentUserId()) || (['IN_PROGRESS','PENDING_CONFIRMATION'].includes(task.status) && task.acceptorId === this.api.currentUserId()); }
  @Builder
  taskCard(task: TaskSummary) {
    Column({ space: 10 }) {
      Row() { Text(task.category === 'RUNNING' ? '跑腿代办' : task.category === 'STUDY' ? '学习辅导' : task.category === 'BORROW' ? '物品借用' : '其他互助').fontSize(11).fontColor(Palette.green); Blank(); Text(statusLabel(task.status)).fontSize(11).fontColor(Palette.muted) }.width('100%')
      Text(task.title).fontSize(17).fontWeight(FontWeight.Medium).fontColor(Palette.ink).maxLines(2)
      Row() { Text(task.location).fontSize(12).fontColor(Palette.muted).layoutWeight(1); Text('¥ ' + task.reward).fontSize(17).fontWeight(FontWeight.Bold).fontColor(Palette.green) }.width('100%')
      if (this.target === task.id.toString()) {
        if (this.action === 'CANCEL') { this.input('取消原因', this.reason, '告诉对方为什么取消', 'reason') }
        Row({ space: 8 }) {
          Button(this.action === 'ACCEPT' ? '核对接单' : '核对取消').height(41).fontSize(13).borderRadius(11).backgroundColor(Palette.green).layoutWeight(1)
            .enabled(!this.working && !this.blocked).id('prepare-task').onClick(() => { this.prepare(); })
          Button('查看影响').height(41).fontSize(12).borderRadius(11).backgroundColor(Palette.soft).fontColor(Palette.green)
            .enabled(!this.working && !this.blocked).onClick(() => { this.previewTransition(); })
        }.width('100%')
      } else if (this.manageable(task)) {
        Button(this.scope === 'market' ? '了解并接单' : '管理这次互助').height(39).fontSize(13).borderRadius(11).backgroundColor(Palette.soft).fontColor(Palette.green).width('100%')
          .enabled(!this.working && !this.blocked).id('task-' + task.id).onClick(() => { this.changed(); this.target = task.id.toString(); })
      }
    }.width('100%').padding(17).backgroundColor(this.target === task.id.toString() ? '#EEF3E8' : '#FFFFFF').borderRadius(18).alignItems(HorizontalAlign.Start)
  }
  @Builder
  preview(receipt: ToolReceipt) {
    Column({ space: 13 }) {
      Row() {
        Text(receipt.state === 'SUCCEEDED' ? '✓' : '↗').fontSize(23).fontColor(Palette.green); Blank()
        Text(statusLabel(receipt.state)).fontSize(11).fontColor(Palette.green)
      }.width('100%')
      Text(receipt.state === 'SUCCEEDED' ? this.successTitle(receipt) : receipt.state === 'PREPARED' ? '最后确认一下' : statusLabel(receipt.state))
        .fontSize(23).fontWeight(FontWeight.Bold).fontColor(Palette.ink)
      Text(receipt.preview.title).fontSize(16).fontWeight(FontWeight.Medium).fontColor(Palette.ink)
      Row() { Text(receipt.preview.location).fontSize(13).fontColor(Palette.muted).layoutWeight(1); Text('¥ ' + receipt.preview.reward).fontSize(20).fontColor(Palette.green).fontWeight(FontWeight.Bold) }.width('100%')
      Text('完成时间  ' + shortDate(receipt.preview.expectedTime)).fontSize(12).fontColor(Palette.muted)
      Text(this.details ? '收起详细约定 −' : '查看详细约定 ＋').fontSize(12).fontColor(Palette.green).padding({ top: 5, bottom: 5 }).onClick(() => { this.details = !this.details; })
      if (this.details) { Text(receipt.preview.description).fontSize(13).lineHeight(22).fontColor(Palette.ink).width('100%') }
      if (receipt.state === 'PREPARED') {
        ForEach(receipt.preview.consequences, (value: string) => { Text(this.consequence(value)).fontSize(12).lineHeight(20).fontColor('#8A6443') })
        Button(receipt.action === 'PUBLISH' ? '确认发布' : receipt.action === 'ACCEPT' ? '确认接单' : '确认取消')
          .height(47).width('100%').fontSize(15).fontWeight(FontWeight.Medium).borderRadius(13).backgroundColor(receipt.action === 'CANCEL' ? '#955F4F' : Palette.green)
          .enabled(!this.working && canExecute(receipt,this.session,this.blocked)).id('confirm-operation').onClick(() => { this.execute(receipt); })
        Text('返回修改').fontSize(13).fontColor(Palette.muted).padding(8).onClick(() => { if (!this.working && !this.blocked) { this.query(receipt.id,true); } })
      }
      if (receipt.result !== null) { Text('操作时的状态：' + statusLabel(receipt.result.status) + ' · 任务 ' + receipt.result.id).fontSize(12).fontColor(Palette.muted) }
      if (receipt.errorMessage !== null) { Text(friendlyNotice(receipt.errorMessage)).fontSize(13).fontColor('#A15342') }
      Text('刷新操作结果').fontSize(12).fontColor(Palette.green).padding(7).onClick(() => { if (!this.working && !this.blocked) { this.query(receipt.id); } })
    }.alignItems(HorizontalAlign.Start).padding(20).backgroundColor('#E7EEDC').borderRadius(21).width('100%').id('operation-card')
  }
  build() {
    Column({ space: 17 }) {
      Row() { Text(this.scope === 'publish' ? '发布求助' : '校园互助').fontSize(28).fontWeight(FontWeight.Bold).fontColor(Palette.ink); Blank(); Text('刷新').fontSize(12).fontColor(Palette.green).padding(8).onClick(() => { if (!this.working && !this.blocked) { this.loadTasks(this.scope === 'market' ? 'available' : 'mine'); } }) }.width('100%')
      Text(this.scope === 'publish' ? '把需求说清楚，让合适的同学看见。' : '每一件小事，都可以有人搭把手。').fontSize(13).fontColor(Palette.muted).width('100%')
      Row({ space: 8 }) {
        ForEach(['market','mine','publish'], (scope: string) => {
          Button(scope === 'market' ? '找互助' : scope === 'mine' ? '我的互助' : '发布求助').height(38).fontSize(12).borderRadius(11).layoutWeight(1)
            .backgroundColor(this.scope === scope ? Palette.green : '#E9EEE4').fontColor(this.scope === scope ? '#FFFFFF' : Palette.green)
            .id('task-scope-' + scope).enabled(!this.working && !this.blocked).onClick(() => { this.selectScope(scope); })
        }, (scope: string) => scope)
      }.width('100%')
      if (this.hint !== undefined && validHint(this.hint)) {
        Row({ space: 8 }) {
          Text('可以继续' + (this.hint.action === 'PUBLISH' ? '发布求助' : this.hint.action === 'ACCEPT' ? '接单' : '取消互助')).fontSize(13).fontColor(Palette.ink).layoutWeight(1)
          Text('去核对 →').fontSize(12).fontColor(Palette.green).padding(8).onClick(() => {
            if (this.hint !== undefined && validHint(this.hint) && !this.working && !this.blocked) { this.changed(); this.action = this.hint.action; this.scope = this.action === 'PUBLISH' ? 'publish' : 'mine'; this.target = this.hint.demandId === null ? '' : this.hint.demandId.toString(); this.lookup = this.action !== 'PUBLISH'; }
          })
        }.width('100%').padding(14).backgroundColor(Palette.soft).borderRadius(14)
      }
      if (this.scope === 'publish') {
        if (!publishReady(this.session)) {
          Column({ space: 11 }) { Text('先整理一下需求').fontSize(18).fontWeight(FontWeight.Medium).fontColor(Palette.ink); Text('回到“对话”，说说你需要的帮助。核对需求后，就可以继续发布。').fontSize(13).lineHeight(22).fontColor(Palette.muted) }.width('100%').padding(20).backgroundColor('#FFFFFF').borderRadius(18).alignItems(HorizontalAlign.Start)
        } else {
          Column({ space: 15 }) {
            this.input('求助标题',this.title,'一句话介绍需要的帮助','title')
            this.input('报酬（元）',this.reward,'例如 5.00','reward')
            this.input('地点',this.location,'约定地点，线上请填写“线上”','location')
            this.input('完成时间',this.time,'选择日期与时间','time')
            Text('选择时间  ›').fontSize(12).fontColor(Palette.green).padding(6).onClick(() => { if (!this.working && !this.blocked) { pickTime(this.time, (value: string): void => { this.changed(); this.time = value; }); } })
            Text('报酬由双方线下结算，平台暂不收付款。').fontSize(11).fontColor(Palette.muted).lineHeight(18)
            Button(this.working ? '请稍候…' : '下一步，核对发布').height(46).fontSize(14).borderRadius(12).backgroundColor(Palette.green).width('100%').id('prepare-publish')
              .enabled(!this.working && !this.blocked).onClick(() => { this.prepare(); })
          }.width('100%').padding(18).backgroundColor('#FFFFFF').borderRadius(18).alignItems(HorizontalAlign.Start)
        }
      } else {
        if (this.tasks.length === 0 && !this.working) {
          Column({ space: 12 }) { Text('↗').fontSize(36).fontColor(Palette.green); Text(this.scope === 'market' ? '暂时没有新的求助' : '还没有参与的互助').fontSize(17).fontColor(Palette.ink); Text('稍后再来看看，或发起你自己的求助。').fontSize(12).fontColor(Palette.muted) }.width('100%').padding({ top: 42, bottom: 42 }).backgroundColor('#FFFFFF').borderRadius(20)
        }
        ForEach(this.tasks, (task: TaskSummary) => { this.taskCard(task); }, (task: TaskSummary) => task.id.toString() + task.status)
        Text(this.lookup ? '收起编号查找' : '通过任务编号查找').fontSize(12).fontColor(Palette.muted).padding(7).onClick(() => { this.lookup = !this.lookup; })
        if (this.lookup) {
          this.input('任务编号',this.target,'输入任务编号','target')
          if (this.action === 'CANCEL') { this.input('取消原因',this.reason,'告诉对方为什么取消','reason') }
          Button('核对操作').height(42).fontSize(13).backgroundColor(Palette.green).borderRadius(12).enabled(!this.working && !this.blocked).onClick(() => { this.prepare(); })
        }
      }
      if (this.transitionNotice.length > 0) { Text(friendlyNotice(this.transitionNotice)).fontSize(13).fontColor(Palette.green).lineHeight(21) }
      ForEach(this.receipt === undefined ? [] : [this.receipt], (receipt: ToolReceipt) => { this.preview(receipt); }, (receipt: ToolReceipt) => receipt.id + ':' + receipt.state)
      Text(this.historyOpen ? '收起操作记录' : '查看操作记录').fontSize(12).fontColor(Palette.muted).padding(8).onClick(() => { this.historyOpen = !this.historyOpen; if (this.historyOpen && !this.working && !this.blocked) { this.history(); } })
      if (this.historyOpen) {
        ForEach(this.receipts, (receipt: ToolReceipt) => {
          Row() { Text(receipt.preview.title).fontSize(13).fontColor(Palette.ink).layoutWeight(1).maxLines(1); Text(statusLabel(receipt.state) + ' ›').fontSize(11).fontColor(Palette.muted) }.width('100%').padding(13).backgroundColor('#FFFFFF').borderRadius(12).onClick(() => { if (!this.working && !this.blocked) { this.query(receipt.id); } })
        }, (receipt: ToolReceipt) => receipt.id + ':' + receipt.state)
      }
      if (this.working) { Text('正在处理，请稍候…').fontSize(12).fontColor(Palette.muted).accessibilityLevel('yes') }
      if (this.notice.length > 0) { Text(friendlyNotice(this.notice)).fontSize(13).lineHeight(21).fontColor(Palette.green).accessibilityLevel('yes') }
    }.alignItems(HorizontalAlign.Start).width('100%').constraintSize({ maxWidth: 640 }).padding(20)
  }
}
