import { pickTime } from '../ui/PickTime';
import { Palette, userAnswer, friendlyNotice } from '../ui/Presentation';
import { HumanHelp } from '../ui/HumanHelp';
import { TrustedTools } from '../ui/TrustedTools';
import { ToolHint, validHint } from '../domain/ToolModels';
import { AiEvent, AiText, AiNotice, AiBudget } from '../data/AiStream';
import { AiProposal } from '../domain/NeedModels';
import { CardSchema, CardNode, emptySchema, buildCards, decodeCards, allowsAction } from '../ui/CardProtocol';
import { http } from '@kit.NetworkKit';
import { BusinessError } from '@kit.BasicServicesKit';
import { NeedApi } from '../data/NeedApi';
import { NeedCategory, NeedSession, NeedDraft, NeedField, DomainCatalog, EditRequest, CategoryRequest,
  VersionRequest, MessageRequest, NeedMessage, ContextOption, contextOptions, activeDraft, emptySession, copyFields, domainName, draftStatus } from '../domain/NeedModels';

@Entry
@Component
struct Needs {
  @State private cardSchema: CardSchema = emptySchema();
  @State private cardError: string = '';
  @State private fixedForm: boolean = false;
  @State private aiText: string = '';
  @State private aiRunning: boolean = false;
  @State private aiProposal: AiProposal | undefined = undefined;
  @State private toolBusy: boolean = false;
  @State private toolHint: ToolHint | undefined = undefined;
  private aiNotice: string = '';
  @State private budgetNotice: string = '';
  private api: NeedApi = new NeedApi();
  private categories: NeedCategory[] = [NeedCategory.RUNNING, NeedCategory.STUDY, NeedCategory.BORROW, NeedCategory.OTHER];
  @State private username: string = '';
  @State private password: string = '';
  @State private loggedIn: boolean = false;
  @State private busy: boolean = false;
  @State private notice: string = '';
  @State private session: NeedSession = emptySession();
  @State private sessions: NeedSession[] = [];
  @State private catalog: DomainCatalog[] = [];
  @State private edits: Record<string, string> = {};
  @State private dirty: boolean = false;
  @State private contexts: string[] = [];
  @State private messageInput: string = '';
  @State private routeInput: string = '';
  @State private tab: number = 0;
  @State private editing: boolean = false;
  @State private showCategories: boolean = false;
  @State private allHistory: boolean = false;
  @State private publishRequest: number = 0;
  private chatScroll: Scroller = new Scroller();

  private accept(session: NeedSession): void {
    if (this.session.id !== session.id || this.session.activeCategory !== session.activeCategory) { this.aiText = ''; }
    this.session = session;
    this.aiProposal = undefined; this.toolHint = undefined;
    const draft = activeDraft(session);
    this.edits = draft === undefined ? {} : copyFields(draft.fields);
    this.contexts = draft === undefined ? [] : draft.contexts.slice();
    this.dirty = false;
    const decoded = decodeCards(JSON.stringify(buildCards(session)), session, this.catalog);
    this.cardSchema = decoded.schema; this.cardError = decoded.error;
    this.sessions = [session].concat(this.sessions.filter((s: NeedSession) => s.id !== session.id));
  }
  private async login(): Promise<void> {
    this.busy = true;
    try {
      await this.api.login(this.username, this.password);
      this.password = '';
      this.catalog = await this.api.send<DomainCatalog[]>('/need-sessions/catalog');
      this.sessions = await this.api.send<NeedSession[]>('/need-sessions');
      if (this.sessions.length > 0) { this.accept(this.sessions[0]); }
      else { this.accept(await this.api.send<NeedSession>('/need-sessions', http.RequestMethod.POST)); }
      this.loggedIn = true;
      this.notice = '';
    } catch (error) { this.notice = (error as BusinessError).message; }
    finally { this.busy = false; }
  }
  private async perform(path: string, method: http.RequestMethod, body: string): Promise<boolean> {
    this.busy = true;
    try {
      this.accept(await this.api.send<NeedSession>('/need-sessions/' + this.session.id + path, method, body));
      this.notice = '已保存';
      return true;
    } catch (error) {
      this.notice = friendlyNotice((error as BusinessError).message);
      return false;
    } finally { this.busy = false; }
  }
  private async select(category: NeedCategory): Promise<void> {
    if (this.dirty || this.messageInput.trim().length > 0) { this.notice = '请先保存字段、发送上下文或清空未发送消息，再切换业务。'; return; }
    const body: CategoryRequest = { revision: this.session.revision, category: category };
    const pending = this.routeInput;
    if (await this.perform('/category', http.RequestMethod.PUT, JSON.stringify(body))) { this.showCategories = false; this.routeInput = ''; this.messageInput = pending; }
  }
  private async save(): Promise<void> {
    const body: EditRequest = { revision: this.session.revision, fields: this.edits, contexts: this.contexts.slice() };
    await this.perform('/draft', http.RequestMethod.PUT, JSON.stringify(body));
  }
  private async message(intake: boolean): Promise<void> {
    if (this.dirty) { this.notice = '请先保存当前字段修改，再发送消息。'; return; }
    const text = intake ? this.routeInput : this.messageInput;
    if (text.trim().length === 0) { return; }
    const body: MessageRequest = { revision: this.session.revision, text: text };
    const before = this.session.revision;
    await this.perform(intake ? '/intake' : '/messages', http.RequestMethod.POST, JSON.stringify(body));
    if (this.session.revision !== before) {
      if (intake) { this.routeInput = ''; } else { this.messageInput = ''; }
    }
  }
  private async askAi(intake: boolean): Promise<void> {
    if (this.busy || this.dirty) { this.notice = '请先保存字段修改再对话。'; return; }
    const text = intake ? this.routeInput : this.messageInput;
    if (text.trim().length === 0) { return; }
    const body: MessageRequest = { revision: this.session.revision, text: text };
    this.busy = true; this.aiRunning = true; this.aiText = ''; this.aiProposal = undefined; this.toolHint = undefined; this.aiNotice = '';
    this.notice = '';
    try {
      await this.api.stream('/need-sessions/' + this.session.id + '/ai-stream', JSON.stringify(body), (event: AiEvent) => {
        if (event.kind === 'session') {
          const remote = JSON.parse(event.data) as NeedSession;
          this.accept(remote);
          if (intake) { this.routeInput = ''; } else { this.messageInput = ''; }
        } else if (event.kind === 'text_delta') { this.aiText += (JSON.parse(event.data) as AiText).text; }
        else if (event.kind === 'proposal') {
          const proposal = JSON.parse(event.data) as AiProposal;
          if (proposal.sessionId === this.session.id && proposal.revision === this.session.revision) { this.aiProposal = proposal; }
        } else if (event.kind === 'action_hint') {
          const hint = JSON.parse(event.data) as ToolHint;
          if (validHint(hint)) { this.toolHint = hint; }
        } else if (event.kind === 'notice' || event.kind === 'failure') {
          this.aiNotice = (JSON.parse(event.data) as AiNotice).message; this.notice = this.aiNotice;
        } else if (event.kind === 'done') { this.aiText = ''; this.notice = this.aiNotice || ''; }
      });
    } catch (error) { this.aiProposal = undefined; this.toolHint = undefined; this.notice = (this.aiNotice || (error as BusinessError).message) + '；已有草稿保留，请重新加载核对。'; }
    finally { this.busy = false; this.aiRunning = false; }
  }
  private async loadBudget(): Promise<void> {
    this.busy = true;
    try {
      const value = await this.api.send<AiBudget>('/need-sessions/ai-budget');
      this.budgetNotice = '今天已使用 ' + value.calls + ' / ' + value.callLimit + ' 次助手服务。';
    } catch (error) { this.budgetNotice = (error as BusinessError).message; }
    finally { this.busy = false; }
  }
  private async adoptProposal(proposal: AiProposal): Promise<void> {
    if (this.busy || this.dirty || this.messageInput.trim().length > 0 || proposal.sessionId !== this.session.id || proposal.revision !== this.session.revision) {
      this.notice = '建议已过期或仍有未保存修改，请重新对话核对。'; return;
    }
    const body: VersionRequest = { revision: proposal.revision };
    await this.perform('/ai-proposals/' + proposal.token + '/apply', http.RequestMethod.POST, JSON.stringify(body));
  }
  aboutToDisappear(): void { this.api.cancelStream(); }

  private async reload(discard: boolean = false): Promise<void> {
    if ((this.dirty || this.messageInput.trim().length > 0) && !discard) { this.notice = '当前有未保存修改，请先保存；切勿直接丢弃输入。'; return; }
    if (await this.perform('', http.RequestMethod.GET, '')) { this.aiText = ''; if (discard) { this.messageInput = ''; } }
  }
  private definitions(): NeedField[] {
    const entry = this.catalog.find((c: DomainCatalog) => c.category === this.session.activeCategory);
    return entry === undefined ? [] : entry.fields;
  }

  private fieldLabel(key: string): string {
    const field = this.definitions().find((entry: NeedField) => entry.key === key);
    return field === undefined ? '待确认条件' : field.label;
  }
  private readable(text: string): string {
    let result = text;
    this.definitions().slice().sort((a: NeedField, b: NeedField) => b.key.length - a.key.length)
      .forEach((field: NeedField) => { result = result.split(field.key).join(field.label); });
    return result;
  }
  private async cardAction(schema: CardSchema, id: string, action: string): Promise<void> {
    if (this.busy || !allowsAction(schema, id, action, this.session, this.catalog, this.dirty || this.messageInput.trim().length > 0)) {
      this.notice = '卡片已更新或仍有未保存输入，请重新核对。'; return;
    }
    if (action === 'form') { this.fixedForm = true; }
    else if (action === 'save') { await this.save(); }
    else if (action === 'review') {
      const body: VersionRequest = { revision: schema.sessionRevision };
      await this.perform('/confirm', http.RequestMethod.POST, JSON.stringify(body));
    }
  }
  private intakeMode(): boolean { return this.session.activeCategory === null || this.session.routePending; }
  private description(category: NeedCategory): string {
    if (category === NeedCategory.RUNNING) { return '取件、代办，搭把手'; }
    if (category === NeedCategory.STUDY) { return '从卡住的那一步开始'; }
    if (category === NeedCategory.BORROW) { return '借得到，也还得清楚'; }
    return '校园里的其他小事';
  }
  private symbol(category: NeedCategory): string { return category === NeedCategory.RUNNING ? '↗' : category === NeedCategory.STUDY ? '∑' : category === NeedCategory.BORROW ? '⇄' : '+'; }
  private historyTitle(s: NeedSession): string {
    const question = s.messages.find((m: NeedMessage) => m.role !== 'ASSISTANT');
    return question === undefined ? (s.activeCategory === null ? '新的对话' : domainName(s.activeCategory) + '的需求') : question.text.substring(0,24);
  }
  private async newConversation(): Promise<void> {
    if (this.busy || this.toolBusy || this.dirty || this.messageInput.trim().length > 0 || this.routeInput.trim().length > 0) { this.notice = '先保存或发出正在编辑的内容，再开始新对话。'; return; }
    this.busy = true;
    try { this.accept(await this.api.send<NeedSession>('/need-sessions', http.RequestMethod.POST)); this.tab = 0; this.editing = false; this.notice = ''; }
    catch (error) { this.notice = friendlyNotice((error as BusinessError).message); }
    finally { this.busy = false; }
  }
  private async continuePublish(): Promise<void> {
    const draft = activeDraft(this.session);
    if (draft === undefined || !draft.assessment.ready || this.dirty || this.messageInput.trim().length > 0) { this.editing = true; this.notice = '请先补齐并保存需求。'; return; }
    if (draft.confirmedRevision !== draft.revision) {
      const body: VersionRequest = { revision: this.session.revision };
      if (!await this.perform('/confirm', http.RequestMethod.POST, JSON.stringify(body))) { return; }
    }
    this.publishRequest++; this.tab = 1; this.notice = '';
  }
  @Builder
  service(category: NeedCategory) {
    Column({ space: 9 }) {
      Text(this.symbol(category)).fontSize(25).fontColor(Palette.green).width(40).height(38)
        .backgroundColor('#EAF0E5').borderRadius(12).textAlign(TextAlign.Center)
      Text(domainName(category)).fontSize(16).fontWeight(FontWeight.Medium).fontColor(Palette.ink)
      Text(this.description(category)).fontSize(11).fontColor(Palette.muted)
    }.alignItems(HorizontalAlign.Start).width('48%').padding(15).backgroundColor('#FFFFFF').borderRadius(18)
      .border({ width: 1, color: Palette.line }).onClick(() => { if (!this.busy) { this.select(category); } })
      .id('service-' + category).accessibilityText(domainName(category))
  }
  @Builder
  services() {
    Column({ space: 12 }) {
      Row({ space: 12 }) { this.service(NeedCategory.RUNNING); this.service(NeedCategory.STUDY); }.width('100%')
      Row({ space: 12 }) { this.service(NeedCategory.BORROW); this.service(NeedCategory.OTHER); }.width('100%')
    }.width('100%')
  }
  @Builder
  bubble(message: NeedMessage) {
    Row() {
      if (message.role !== 'ASSISTANT') { Blank().width(28) }
      Column({ space: 5 }) {
        Text(message.role === 'ASSISTANT' ? 'campus 助手' : '你').fontSize(11).fontColor(message.role === 'ASSISTANT' ? Palette.muted : '#D7E5DD')
        Text(message.role === 'ASSISTANT' ? userAnswer(message.text) : message.text).fontSize(15).lineHeight(24)
          .fontColor(message.role === 'ASSISTANT' ? Palette.ink : '#FFFFFF').width('100%').copyOption(CopyOptions.InApp)
      }.alignItems(HorizontalAlign.Start).layoutWeight(1).padding(15).borderRadius(18)
        .backgroundColor(message.role === 'ASSISTANT' ? '#FFFFFF' : Palette.green)
      if (message.role === 'ASSISTANT') { Blank().width(15) }
    }.width('100%')
  }
  @Builder
  editor(field: NeedField) {
    Column({ space: 7 }) {
      Text(field.label).fontSize(13).fontColor(Palette.ink).fontWeight(FontWeight.Medium)
      if (field.type === 'select') {
        Select(field.options.map((s: string): SelectOption => { return { value: s }; }))
          .value(this.edits[field.key] || '请选择').font({ size: 14 }).fontColor(Palette.ink).height(44)
          .backgroundColor(Palette.canvas).borderRadius(10).enabled(!this.busy).id('draft-' + field.key).accessibilityText(field.label)
          .onSelect((_index: number, value: string) => { if ((this.edits[field.key] || '') !== value) { this.edits[field.key] = value; this.dirty = true; } })
      } else {
        TextInput({ text: (this.edits[field.key] || '').replace('T', ' '), placeholder: field.type === 'datetime-local' ? '选择日期与时间' : '填写' + field.label })
          .fontSize(14).height(46).borderRadius(10).backgroundColor(Palette.canvas).fontColor(Palette.ink)
          .enabled(!this.busy).maxLength(6000).id('draft-' + field.key).accessibilityText(field.label)
          .onChange((value: string) => {
            const next = field.type === 'datetime-local' ? value.replace(' ', 'T') : value;
            if ((this.edits[field.key] || '') !== next) { this.edits[field.key] = next; this.dirty = true; }
          })
      }
      if (field.type === 'datetime-local') {
        Text('选择时间  ›').fontSize(12).fontColor(Palette.green).padding(6).onClick(() => { if (!this.busy) { pickTime(this.edits[field.key] || '', (value: string): void => { this.edits[field.key] = value; this.dirty = true; }); } })
      }
    }.alignItems(HorizontalAlign.Start).width('100%')
  }
  @Builder
  proposalCard(proposal: AiProposal) {
    Column({ space: 12 }) {
      Row() { Text('帮你整理好了').fontSize(17).fontWeight(FontWeight.Bold).fontColor(Palette.ink); Blank(); Text('待核对').fontSize(11).fontColor(Palette.green) }.width('100%')
      ForEach(Object.keys(proposal.fields), (key: string) => {
        Row({ space: 10 }) {
          Text(this.fieldLabel(key)).fontSize(12).fontColor(Palette.muted).width(80)
          Text(proposal.fields[key]).fontSize(14).fontColor(Palette.ink).layoutWeight(1)
        }.width('100%').alignItems(VerticalAlign.Top)
      }, (key: string) => key)
      ForEach(proposal.warnings, (warning: string) => { Text(friendlyNotice(warning)).fontSize(12).fontColor('#90643F') })
      if (proposal.candidates.length > 0) {
        ForEach(proposal.candidates, (category: NeedCategory) => {
          Button(domainName(category)).height(40).fontSize(13).fontColor(Palette.green).backgroundColor('#FFFFFF')
            .enabled(!this.busy && !this.dirty).onClick(() => { this.select(category); })
        }, (category: NeedCategory) => category)
      }
      Row({ space: 10 }) {
        Button('保存到需求').height(42).fontSize(14).backgroundColor(Palette.green).borderRadius(12).layoutWeight(1).id('adopt-proposal')
          .enabled(!this.busy && !this.dirty && this.messageInput.trim().length === 0 && Object.keys(proposal.fields).length > 0)
          .onClick(() => { this.adoptProposal(proposal); })
        Button('暂不采用').height(42).fontSize(13).backgroundColor('#FFFFFF').fontColor(Palette.muted).borderRadius(12)
          .enabled(!this.busy).onClick(() => { this.aiProposal = undefined; })
      }.width('100%')
      Text('保存后仍可修改，发布前会请你再次确认。').fontSize(11).fontColor(Palette.muted)
    }.width('100%').padding(17).backgroundColor('#EBF1E4').borderRadius(18).alignItems(HorizontalAlign.Start)
  }
  @Builder
  draftCard(draft: NeedDraft) {
    Column({ space: 12 }) {
      Row() {
        Text('我的需求').fontSize(17).fontWeight(FontWeight.Bold).fontColor(Palette.ink)
        Blank()
        Text(this.editing ? '收起' : '修改').fontSize(13).fontColor(Palette.green).padding(8).onClick(() => { this.editing = !this.editing; })
      }.width('100%')
      if (!this.editing) {
        ForEach(Object.keys(draft.fields).slice(0,5), (key: string) => {
          Row({ space: 10 }) { Text(this.fieldLabel(key)).fontSize(12).fontColor(Palette.muted).width(78); Text(draft.fields[key]).fontSize(14).fontColor(Palette.ink).layoutWeight(1) }.width('100%')
        }, (key: string) => key)
        if (Object.keys(draft.fields).length > 5) { Text('另有 ' + (Object.keys(draft.fields).length - 5) + ' 项约定 · 点击修改查看').fontSize(11).fontColor(Palette.muted) }
      } else {
        Row() {
          Text(this.fixedForm ? '完整需求' : '当前需要补充').fontSize(12).fontColor(Palette.muted); Blank()
          Text(this.fixedForm ? '只看必要项' : '全部信息').fontSize(12).fontColor(Palette.green).onClick(() => { this.fixedForm = !this.fixedForm; })
        }.width('100%')
        ForEach(this.definitions().filter((f: NeedField) => this.fixedForm || this.cardSchema.cards.some((c: CardNode) => c.fields.includes(f.key))), (field: NeedField) => { this.editor(field); }, (field: NeedField) => field.key)
        Text('有特别的要求吗？').fontSize(13).fontColor(Palette.ink)
        Flex({ wrap: FlexWrap.Wrap }) {
          ForEach(contextOptions(draft.category), (option: ContextOption) => {
            Row({ space: 3 }) {
              Checkbox({ name: option.key }).width(18).height(18).selectedColor(Palette.green).select(this.contexts.includes(option.key)).enabled(!this.busy)
                .accessibilityText(option.label).onChange((selected: boolean) => {
                  if (this.contexts.includes(option.key) !== selected) { this.contexts = selected ? this.contexts.concat([option.key]) : this.contexts.filter((key: string) => key !== option.key); this.dirty = true; }
                })
              Text(option.label).fontSize(12).fontColor(Palette.muted)
            }.padding({ right: 9, bottom: 7 })
          }, (option: ContextOption) => option.key)
        }.width('100%')
        Button(this.busy ? '保存中…' : '保存需求').height(42).fontSize(14).borderRadius(12).backgroundColor(Palette.green).width('100%').enabled(!this.busy)
          .onClick(async () => { await this.save(); if (!this.dirty) { this.editing = false; } })
      }
      if (draft.assessment.missing.length > 0) { Text('还需要：' + draft.assessment.missing.map((key: string) => this.fieldLabel(key)).join('、')).fontSize(12).fontColor('#947047').lineHeight(20) }
      ForEach(draft.assessment.conflicts, (risk: string) => { Text(this.readable(risk)).fontSize(12).fontColor('#A64E42') })
      Button(draft.assessment.ready ? '核对需求，继续发布' : '补充需求信息').height(44).fontSize(14).borderRadius(12)
        .backgroundColor(Palette.soft).fontColor(Palette.green).width('100%').enabled(!this.busy && !this.dirty)
        .onClick(() => { if (draft.assessment.ready) { this.continuePublish(); } else { this.editing = true; } })
    }.width('100%').padding(17).backgroundColor('#FFFFFF').borderRadius(18).alignItems(HorizontalAlign.Start)
  }
  @Builder
  conversation() {
    Column() {
      Scroll(this.chatScroll) {
        Column({ space: 17 }) {
          if (this.session.activeCategory === null || this.showCategories) {
            Column({ space: 9 }) {
              Text('校园里的小事，\n一起想办法。').fontSize(29).fontWeight(FontWeight.Bold).fontColor(Palette.ink).lineHeight(39)
              Text('说说你需要什么，也可以先选一种帮助。').fontSize(13).fontColor(Palette.muted)
            }.alignItems(HorizontalAlign.Start).width('100%').padding({ top: 10, bottom: 8 })
            this.services()
          } else {
            Row({ space: 7 }) {
              Text(this.symbol(this.session.activeCategory)).fontSize(19).fontColor(Palette.green)
              Text(domainName(this.session.activeCategory)).fontSize(17).fontColor(Palette.ink).fontWeight(FontWeight.Medium)
              Blank()
              Text('换一种').fontSize(12).fontColor(Palette.muted).padding(8).onClick(() => { this.showCategories = true; })
            }.width('100%')
            if (this.session.messages.length === 0) {
              Text(this.session.activeCategory === NeedCategory.STUDY ? '把没看懂的那一步发来，我们从这里开始。' : '先说说你的想法，细节可以慢慢补齐。')
                .fontSize(14).lineHeight(23).fontColor(Palette.muted).width('100%').padding({ bottom: 10 })
            }
          }
          ForEach(this.session.messages.filter((m: NeedMessage) => m.category === null || m.category === this.session.activeCategory), (m: NeedMessage) => { this.bubble(m); }, (m: NeedMessage) => m.id.toString())
          if (this.aiRunning) {
            Column({ space: 8 }) {
              Text('campus 助手').fontSize(11).fontColor(Palette.muted)
              Text(this.aiText.length > 0 ? userAnswer(this.aiText) : '正在回复…').fontSize(15).lineHeight(24).fontColor(Palette.ink).width('100%')
            }.width('100%').padding(15).backgroundColor('#FFFFFF').borderRadius(18).alignItems(HorizontalAlign.Start)
          }
          if (this.aiProposal !== undefined) { this.proposalCard(this.aiProposal) }
          ForEach(this.session.drafts.filter((draft: NeedDraft) => draft.category === this.session.activeCategory && (Object.keys(draft.fields).length > 0 || this.editing)), (draft: NeedDraft) => { this.draftCard(draft); }, (draft: NeedDraft) => draft.id.toString() + ':' + draft.revision)
          if (this.session.activeCategory !== null && !this.editing) {
            Text('手动填写需求 →').fontSize(13).fontColor(Palette.green).padding(10).onClick(() => { this.editing = true; })
          }
          Blank().height(6)
        }.width('100%').constraintSize({ maxWidth: 640 }).padding({ left: 20, right: 20, bottom: 20 }).alignItems(HorizontalAlign.Start)
      }.layoutWeight(1).width('100%').scrollBar(BarState.Off)
      Column({ space: 8 }) {
        Row({ space: 9 }) {
          TextArea({ text: this.intakeMode() ? this.routeInput : this.messageInput, placeholder: this.session.activeCategory === NeedCategory.STUDY ? '哪里没想明白？' : '说说你需要什么帮助…' })
            .fontSize(14).fontColor(Palette.ink).backgroundColor('#FFFFFF').borderRadius(15).height(62).layoutWeight(1).maxLength(4000)
            .enabled(!this.busy && !this.dirty).id('chat-input').accessibilityText('给助手的消息')
            .onChange((v: string) => { if (this.intakeMode()) { this.routeInput = v; } else { this.messageInput = v; } })
          Button(this.aiRunning ? '停止' : '发送').width(58).height(46).fontSize(13).borderRadius(14).backgroundColor(Palette.green).id('send-chat')
            .enabled(this.aiRunning || (!this.busy && !this.dirty && (this.intakeMode() ? this.routeInput : this.messageInput).trim().length > 0))
            .onClick(() => { if (this.aiRunning) { this.api.cancelStream(); } else { this.askAi(this.intakeMode()); } })
        }.width('100%')
        Row() {
          Text('由 DeepSeek 回复 · 勿填密码或取件码').fontSize(10).fontColor(Palette.muted); Blank()
          Text('只记下来').fontSize(11).fontColor(Palette.green).onClick(() => { if (!this.busy) { this.message(this.intakeMode()); } })
        }.width('100%')
      }.padding({ left: 20, right: 20, top: 10, bottom: 12 }).backgroundColor('#EFF2EB')
    }.width('100%').height('100%')
  }
  @Builder
  account() {
    Column({ space: 18 }) {
      Row({ space: 14 }) {
        Text(this.username.substring(0,1).toUpperCase()).width(54).height(54).textAlign(TextAlign.Center).fontSize(22).fontColor(Palette.green).backgroundColor('#DDE9D6').borderRadius(18)
        Column({ space: 5 }) { Text(this.username).fontSize(18).fontColor(Palette.ink).fontWeight(FontWeight.Bold); Text('你的校园互助空间').fontSize(12).fontColor(Palette.muted) }.alignItems(HorizontalAlign.Start)
      }.width('100%').padding({ top: 12, bottom: 12 })
      Column({ space: 10 }) {
        Text('助手使用情况').fontSize(16).fontWeight(FontWeight.Medium).fontColor(Palette.ink)
        Text(this.budgetNotice || '查看今天的助手使用次数').fontSize(13).fontColor(Palette.muted)
        Button('查看使用情况').fontSize(13).height(40).backgroundColor(Palette.soft).fontColor(Palette.green).borderRadius(11).onClick(() => { this.loadBudget(); })
      }.width('100%').padding(17).backgroundColor('#FFFFFF').borderRadius(18).alignItems(HorizontalAlign.Start)
      Row() { Text('最近的对话').fontSize(17).fontWeight(FontWeight.Bold).fontColor(Palette.ink); Blank(); Text(this.allHistory ? '收起' : '全部').fontSize(12).fontColor(Palette.green).onClick(() => { this.allHistory = !this.allHistory; }) }.width('100%')
      ForEach(this.sessions.filter((s: NeedSession) => s.id !== this.session.id).slice(0,this.allHistory ? 20 : 5), (s: NeedSession) => {
        Row({ space: 10 }) {
          Text('↗').fontSize(18).fontColor(Palette.green)
          Column({ space: 4 }) { Text(this.historyTitle(s)).fontSize(14).fontColor(Palette.ink).maxLines(1); Text(s.activeCategory === null ? '未分类' : domainName(s.activeCategory)).fontSize(11).fontColor(Palette.muted) }.alignItems(HorizontalAlign.Start).layoutWeight(1)
          Text('›').fontSize(20).fontColor(Palette.muted)
        }.width('100%').padding(15).backgroundColor('#FFFFFF').borderRadius(14).onClick(async () => {
          if (this.busy || this.dirty || this.messageInput.trim().length > 0 || this.routeInput.trim().length > 0) { this.notice = '请先保存或发送当前输入。'; return; }
          this.busy = true;try { this.accept(await this.api.send<NeedSession>('/need-sessions/' + s.id)); this.tab = 0; this.notice = ''; } catch (error) { this.notice = friendlyNotice((error as BusinessError).message); } finally { this.busy = false; }
        })
      }, (s: NeedSession) => s.id.toString())
      Button('同步当前需求').height(42).fontSize(13).fontColor(Palette.green).backgroundColor(Palette.soft).borderRadius(12).width('100%').onClick(() => { this.reload(); })
      if (this.dirty || this.messageInput.trim().length > 0 || this.routeInput.trim().length > 0) {
        Button('放弃未保存输入并刷新').height(42).fontSize(13).fontColor('#A15341').backgroundColor('#FFFFFF').onClick(() => { this.routeInput = ''; this.reload(true); })
      }
      Button('退出登录').height(42).fontSize(13).fontColor(Palette.muted).backgroundColor('#FFFFFF').borderRadius(12).width('100%').onClick(() => {
        if (this.dirty || this.messageInput.trim().length > 0 || this.routeInput.trim().length > 0) { this.notice = '请先保存当前输入。'; return; }
        this.api.clear(); this.loggedIn = false; this.budgetNotice = ''; this.accept(emptySession()); this.sessions = []; this.notice = ''; this.tab = 0;
      })
      Text('当前业务草稿和相关对话会发送至 DeepSeek。\n发布和接单前，都会由你亲自确认。').fontSize(11).lineHeight(19).fontColor(Palette.muted)
    }.width('100%').constraintSize({ maxWidth: 640 }).padding(20).alignItems(HorizontalAlign.Start)
  }
  @Builder
  loginPanel() {
    Column({ space: 19 }) {
      Text('欢迎回来。').fontSize(31).fontWeight(FontWeight.Bold).fontColor(Palette.ink).width('100%')
      Text('登录后，把校园里的小事交给我们一起想办法。').fontSize(14).lineHeight(24).fontColor(Palette.muted).width('100%')
      Column({ space: 15 }) {
        Text('校园账号').fontSize(13).fontColor(Palette.ink).width('100%')
        TextInput({ text: this.username, placeholder: '用户名' }).fontSize(15).height(48).backgroundColor(Palette.canvas).borderRadius(12).id('login-username').onChange((v: string) => { this.username = v; })
        Text('密码').fontSize(13).fontColor(Palette.ink).width('100%')
        TextInput({ text: this.password, placeholder: '输入密码' }).type(InputType.Password).fontSize(15).height(48).backgroundColor(Palette.canvas).borderRadius(12).id('login-password').onChange((v: string) => { this.password = v; })
        Button(this.busy ? '登录中…' : '登录').height(48).fontSize(15).fontWeight(FontWeight.Medium).borderRadius(13).backgroundColor(Palette.green).width('100%').id('login-submit')
          .enabled(!this.busy && this.username.length > 0 && this.password.length > 0).onClick(() => { this.login(); })
      }.width('100%').padding(22).backgroundColor('#FFFFFF').borderRadius(22).alignItems(HorizontalAlign.Start)
      Text('使用已有校园互助账号登录').fontSize(12).fontColor(Palette.muted)
    }.width('100%').constraintSize({ maxWidth: 460 }).padding(24).alignItems(HorizontalAlign.Start)
  }
  build() {
    Column() {
      Row() {
        Row({ space: 5 }) { Text('✦').fontSize(21).fontColor(Palette.green); Text('campus').fontSize(23).fontWeight(FontWeight.Bold).letterSpacing(-0.5).fontColor(Palette.ink) }
        Blank()
        if (this.loggedIn && this.tab === 0) { Button('＋ 新对话').fontSize(12).height(36).borderRadius(11).backgroundColor('#E8EDE3').fontColor(Palette.green).enabled(!this.busy && !this.toolBusy).onClick(() => { this.newConversation(); }) }
      }.width('100%').padding({ left: 22, right: 22, top: 14, bottom: 12 })
      if (this.notice.length > 0) { Text(friendlyNotice(this.notice)).fontSize(12).lineHeight(18).fontColor(Palette.green).width('100%').padding({ left: 22, right: 22, top: 6, bottom: 8 }).accessibilityLevel('yes') }
      if (!this.loggedIn) {
        Scroll() { this.loginPanel() }.layoutWeight(1).width('100%')
      } else {
        Stack() {
          Column() { this.conversation() }.width('100%').height('100%').visibility(this.tab === 0 ? Visibility.Visible : Visibility.None)
          Scroll() {
            TrustedTools({ api: this.api, session: this.session, publishRequest: this.publishRequest,
              blocked: this.busy || this.dirty || this.messageInput.trim().length > 0 || this.routeInput.trim().length > 0,
              hint: this.toolHint, onWorking: (working: boolean): void => { this.toolBusy = working; } })
          }.width('100%').height('100%').visibility(this.tab === 1 ? Visibility.Visible : Visibility.None)
          Scroll() { HumanHelp({ api: this.api, blocked: this.busy, onWorking: (working: boolean): void => { this.toolBusy = working; } }) }
            .width('100%').height('100%').visibility(this.tab === 2 ? Visibility.Visible : Visibility.None)
          Scroll() { this.account() }.width('100%').height('100%').visibility(this.tab === 3 ? Visibility.Visible : Visibility.None)
        }.layoutWeight(1).width('100%')
        Row() {
          ForEach([0,1,2,3], (index: number) => {
            Column({ space: 3 }) {
              if (index === 2) { Image($rawfile('icon-chat.svg')).width(23).height(23).opacity(this.tab === index ? 1 : 0.45) }
              else { Text(['✦','▤','','○'][index]).fontSize(22).fontColor(this.tab === index ? Palette.green : '#A1ADA3') }
              Text(['对话','任务','消息','我的'][index]).fontSize(10).fontColor(this.tab === index ? Palette.green : Palette.muted).fontWeight(this.tab === index ? FontWeight.Bold : FontWeight.Normal)
            }.layoutWeight(1).height(56).justifyContent(FlexAlign.Center).id('nav-' + index).onClick(() => { if (!this.busy && !this.toolBusy) { this.tab = index; this.notice = ''; } })
          }, (index: number) => index.toString())
        }.padding({ top: 5, bottom: 6 }).width('100%').backgroundColor('#FFFFFF').border({ width: { top: 1 }, color: Palette.line })
      }
    }.width('100%').height('100%').backgroundColor(Palette.canvas)
  }
}
