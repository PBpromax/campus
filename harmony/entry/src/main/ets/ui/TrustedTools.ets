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
  @State private action: string = 'PUBLISH';
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
    if (this.action === 'PUBLISH' && !publishReady(this.session)) { this.notice = '请先补齐并确认当前草稿。'; return; }
    if (this.action === 'PUBLISH' && (this.title.trim().length === 0 || this.location.trim().length === 0 ||
      !/^\d+(\.\d{1,2})?$/.test(this.reward) || Number(this.reward) <= 0 ||
      !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2})?$/.test(this.time))) {
      this.notice = '请明确填写标题、正数报酬、地点和完成时间。服务端还会校验时间与金额范围。'; return;
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
    try { this.receipt = await this.api.send<ToolReceipt>('/trusted-tools/prepare',http.RequestMethod.POST,JSON.stringify(body)); this.notice = '已生成确认卡，尚未执行。请核对以下服务端内容。'; }
    catch (error) { this.notice = (error as BusinessError).message + '。未自动重试；保留同一请求编号。'; }
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
      this.transitionNotice = '状态预览（未执行）：' + result.source.status + ' → ' + result.decision.status + ' · ' + (result.decision.outcome === 'APPLIED' ? '状态规则匹配' : '状态规则未匹配：' + result.decision.outcome) + '\n' + result.notice;
    } catch (error) { this.transitionNotice = (error as BusinessError).message + '。预览不影响原有确认流程。'; }
    finally { this.setWorking(false); }
  }
  private async execute(receipt: ToolReceipt): Promise<void> {
    if (this.working || !canExecute(receipt,this.session,this.blocked)) { this.notice = '确认卡已失效或有未保存输入，请重新核对。'; return; }
    this.setWorking(true);
    try {
      const body: ToolConfirm = { confirmed: true };
      this.receipt = await this.api.send<ToolReceipt>('/trusted-tools/' + receipt.id + '/execute',http.RequestMethod.POST,JSON.stringify(body));
      this.notice = '服务端已执行；任务编号 #' + this.receipt.result?.id + '。请查看回执。';
    } catch (error) { this.notice = (error as BusinessError).message + '。请先查询此回执，不要重新准备另一个请求。'; }
    finally { this.setWorking(false); }
  }
  private async query(id: string, revoke: boolean = false): Promise<void> {
    if (this.working || this.blocked) { return; }
    this.setWorking(true);
    try {
      this.receipt = await this.api.send<ToolReceipt>('/trusted-tools/' + id + (revoke ? '/revoke' : ''),revoke ? http.RequestMethod.POST : http.RequestMethod.GET);
      this.notice = revoke ? '已撤销本次待执行操作，不等于取消已发布任务。' : '已读取持久化回执。成功回执记录当时结果；任务现状请刷新列表。';
    } catch (error) { this.notice = (error as BusinessError).message; }
    finally { this.setWorking(false); }
  }
  private async history(): Promise<void> {
    this.setWorking(true);
    try {
      this.receipts = await this.api.send<ToolReceipt[]>('/trusted-tools');
      this.audits = await this.api.send<ToolAudit[]>('/trusted-tools/audits');
      this.notice = '已加载本人最近20份回执与50条审计。';
    } catch (error) { this.notice = (error as BusinessError).message; }
    finally { this.setWorking(false); }
  }
  private async loadTasks(scope: string): Promise<void> {
    this.setWorking(true);
    try { this.tasks = await this.api.send<TaskSummary[]>('/trusted-tools/tasks?scope=' + scope); this.notice = '已刷新任务列表（最多20项）；执行前仍会校验权限和状态。'; }
    catch (error) { this.notice = (error as BusinessError).message; }
    finally { this.setWorking(false); }
  }
  @Builder
  preview(receipt: ToolReceipt) {
    Column({ space: 8 }) {
      Text('执行确认卡 · ' + actionName(receipt.action)).fontSize(20).fontWeight(FontWeight.Bold)
      Text('状态：' + receipt.state + '；有效至 ' + receipt.expiresAt).fontSize(13)
      Text('标题：' + receipt.preview.title)
      Text('业务：' + receipt.preview.category + '；任务：' + (receipt.result !== null ? '#' + receipt.result.id : receipt.preview.demandId === null ? '待创建' : '#' + receipt.preview.demandId))
      Text('地点：' + receipt.preview.location + '；报酬：' + receipt.preview.reward + '元')
      Text('完成时间：' + receipt.preview.expectedTime)
      Text(receipt.preview.description).width('100%')
      ForEach(receipt.preview.consequences,(value: string) => { Text(value).fontColor('#9B4A13'); })
      if (receipt.state === 'PREPARED') {
        Button('我已核对，确认执行' + actionName(receipt.action))
          .enabled(!this.working && canExecute(receipt,this.session,this.blocked)).onClick(() => { this.execute(receipt); })
        Button('撤销这张待执行卡').enabled(!this.working && !this.blocked).onClick(() => { this.query(receipt.id,true); })
      }
      if (receipt.result !== null) { Text('执行时结果：任务 #' + receipt.result.id + ' · ' + receipt.result.status).fontColor('#285F45') }
      if (receipt.errorMessage !== null) { Text(receipt.errorMessage).fontColor('#AA3030') }
      Button('查询同一回执（不重复执行）').enabled(!this.working && !this.blocked).onClick(() => { this.query(receipt.id); })
      Text('回执编号：' + receipt.id).fontSize(11)
    }.alignItems(HorizontalAlign.Start).padding(14).backgroundColor('#FFF4E2').width('100%')
  }
  build() {
    Column({ space: 12 }) {
      Text('可信操作 · 发布 / 接单 / 取消').fontSize(22).fontWeight(FontWeight.Bold)
      Text('先准备确认卡，再由你明确执行。支付仅更新业务状态，未接入真实支付。').fontSize(13)
      if (this.hint !== undefined && validHint(this.hint)) {
        Text('AI操作建议：' + actionName(this.hint.action) + (this.hint.demandId === null ? '' : ' #' + this.hint.demandId))
        Text(this.hint.explanation).fontSize(13)
        Button('核对这条建议（仅填入操作）').enabled(!this.working && !this.blocked).onClick(() => {
          if (this.hint !== undefined && validHint(this.hint)) { this.changed(); this.action = this.hint.action; this.target = this.hint.demandId === null ? '' : this.hint.demandId.toString(); }
        })
      }
      Select([{ value: '发布任务' },{ value: '接单' },{ value: '取消任务' }]).value(actionName(this.action))
        .enabled(!this.working && !this.blocked).onSelect((index: number) => { this.changed(); this.action = ['PUBLISH','ACCEPT','CANCEL'][index]; })
      if (this.action === 'PUBLISH') {
        Text(publishReady(this.session) ? '当前草稿已确认；请补充发布所需信息。' : '请先补齐并确认当前业务草稿。').fontSize(13)
        TextInput({ text: this.title, placeholder: '任务标题（最多100字）' }).maxLength(100).enabled(!this.working && !this.blocked).onChange((v: string) => { this.changed(); this.title = v; })
        TextInput({ text: this.reward, placeholder: '明确的报酬金额，例如 5.00（必须大于0）' }).maxLength(11).enabled(!this.working && !this.blocked).onChange((v: string) => { this.changed(); this.reward = v; })
        TextInput({ text: this.location, placeholder: '任务地点（线上也需明确写线上）' }).maxLength(200).enabled(!this.working && !this.blocked).onChange((v: string) => { this.changed(); this.location = v; })
        TextInput({ text: this.time, placeholder: '完成时间 YYYY-MM-DDTHH:mm（本地时间）' }).maxLength(19).enabled(!this.working && !this.blocked).onChange((v: string) => { this.changed(); this.time = v; })
      } else {
        Button('刷新可接任务').enabled(!this.working && !this.blocked).onClick(() => { this.loadTasks('available'); })
        Button('刷新我发布 / 接下的任务').enabled(!this.working && !this.blocked).onClick(() => { this.loadTasks('mine'); })
        ForEach(this.tasks,(task: TaskSummary) => {
          Button('#' + task.id + ' ' + task.title + ' · ' + task.status).enabled(!this.working && !this.blocked)
            .onClick(() => { this.changed(); this.target = task.id.toString(); })
        },(task: TaskSummary) => task.id.toString() + task.status)
        TextInput({ text: this.target, placeholder: '准确的任务编号' }).maxLength(16).enabled(!this.working && !this.blocked).onChange((v: string) => { this.changed(); this.target = v; })
        if (this.action === 'CANCEL') {
          TextInput({ text: this.reason, placeholder: '取消原因（接单方必须填写，信用将扣5分）' }).maxLength(500).enabled(!this.working && !this.blocked).onChange((v: string) => { this.changed(); this.reason = v; })
        }
      }
      if (this.action !== 'PUBLISH') {
        Button('预览状态变化（不执行）').enabled(!this.working && !this.blocked).onClick(() => { this.previewTransition(); })
        if (this.transitionNotice.length > 0) { Text(this.transitionNotice).fontSize(14).accessibilityLevel('yes') }
      }
      Button('准备执行确认卡（尚不执行）').enabled(!this.working && !this.blocked).onClick(() => { this.prepare(); })
      // Plain DTOs passed to @Builder are value parameters. Recreate the card on
      // terminal transitions so an old PREPARED view cannot remain on screen.
      ForEach(this.receipt === undefined ? [] : [this.receipt],(receipt: ToolReceipt) => {
        this.preview(receipt);
      },(receipt: ToolReceipt) => receipt.id + ':' + receipt.state)
      Button('加载我的回执与审计').enabled(!this.working && !this.blocked).onClick(() => { this.history(); })
      ForEach(this.receipts,(receipt: ToolReceipt) => {
        Button(actionName(receipt.action) + ' · ' + receipt.state + ' · ' + receipt.preview.title)
          .enabled(!this.working && !this.blocked).onClick(() => { this.query(receipt.id); })
      },(receipt: ToolReceipt) => receipt.id + ':' + receipt.state)
      ForEach(this.audits,(audit: ToolAudit) => { Text(audit.createdAt + ' ' + actionName(audit.action) + ' ' + audit.outcome + '：' + audit.message).fontSize(12); },(audit: ToolAudit) => audit.id.toString())
      if (this.working) { Text('正在校验操作与处理回执，请稍候…').accessibilityLevel('yes') }
      Text(this.notice).fontSize(14).fontColor('#285F45')
    }.alignItems(HorizontalAlign.Start).width('100%').padding(14).backgroundColor('#F1F5F2')
  }
}
