import { http } from '@kit.NetworkKit';
import { BusinessError } from '@kit.BasicServicesKit';
import { NeedApi } from '../data/NeedApi';
import { NeedCategory, NeedSession, NeedDraft, NeedField, DomainCatalog, EditRequest, CategoryRequest,
  VersionRequest, MessageRequest, NeedMessage, ContextOption, contextOptions, activeDraft, emptySession, copyFields, domainName, draftStatus } from '../domain/NeedModels';

@Entry
@Component
struct Needs {
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

  private accept(session: NeedSession): void {
    this.session = session;
    const draft = activeDraft(session);
    this.edits = draft === undefined ? {} : copyFields(draft.fields);
    this.contexts = draft === undefined ? [] : draft.contexts.slice();
    this.dirty = false;
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
      this.notice = '服务端草稿已加载。分类候选来自有限规则，尚未接入模型。';
    } catch (error) { this.notice = (error as BusinessError).message; }
    finally { this.busy = false; }
  }
  private async perform(path: string, method: http.RequestMethod, body: string): Promise<boolean> {
    this.busy = true;
    try {
      this.accept(await this.api.send<NeedSession>('/need-sessions/' + this.session.id + path, method, body));
      this.notice = '已保存到服务器；会话版本 ' + this.session.revision;
      return true;
    } catch (error) {
      this.notice = (error as BusinessError).message + '。本地输入仍保留；请核对后重新加载，不会自动覆盖。';
      return false;
    } finally { this.busy = false; }
  }
  private async select(category: NeedCategory): Promise<void> {
    if (this.dirty || this.messageInput.trim().length > 0) { this.notice = '请先保存字段、发送上下文或清空未发送消息，再切换业务。'; return; }
    const body: CategoryRequest = { revision: this.session.revision, category: category };
    await this.perform('/category', http.RequestMethod.PUT, JSON.stringify(body));
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
  private async reload(discard: boolean = false): Promise<void> {
    if ((this.dirty || this.messageInput.trim().length > 0) && !discard) { this.notice = '当前有未保存修改，请先保存；切勿直接丢弃输入。'; return; }
    if (await this.perform('', http.RequestMethod.GET, '') && discard) { this.messageInput = ''; }
  }
  private definitions(): NeedField[] {
    const entry = this.catalog.find((c: DomainCatalog) => c.category === this.session.activeCategory);
    return entry === undefined ? [] : entry.fields;
  }

  @Builder
  editor(field: NeedField) {
    Column({ space: 6 }) {
      Text(field.label).fontSize(15).fontWeight(FontWeight.Medium)
      if (field.type === 'select') {
        Select(field.options.map((s: string): SelectOption => { return { value: s }; }))
          .enabled(!this.busy)
          .value(this.edits[field.key] || '尚未填写')
          .onSelect((_index: number, value: string) => { this.edits[field.key] = value; this.dirty = true; })
      } else {
        TextInput({ text: this.edits[field.key] || '', placeholder: field.type === 'datetime-local' ? 'YYYY-MM-DDTHH:mm' : '未知可留空，系统会继续澄清' })
          .enabled(!this.busy).maxLength(6000)
          .onChange((value: string) => { this.edits[field.key] = value; this.dirty = true; })
      }
      Text('信息性质：用户声明；未知、资源可用性及对方授权不能由系统猜测').fontSize(11).fontColor('#66756C')
    }.alignItems(HorizontalAlign.Start).width('100%').padding(10).backgroundColor('#F2F6F3').borderRadius(10)
  }
  @Builder
  draftPanel(draft: NeedDraft) {
    Column({ space: 12 }) {
      Text(domainName(draft.category) + ' · ' + draftStatus(draft)).fontSize(21).fontWeight(FontWeight.Bold)
      Text('草稿版本 ' + draft.revision + '；草稿确认仅用于核对，不会发布任务。').fontSize(13)
      if (draft.category === NeedCategory.STUDY) {
        Text('可以先发送当前知识点或卡住的步骤；普通交流不要求报酬、地点或完成时间。').fontSize(14)
      }
      ForEach(this.session.messages.filter((m: NeedMessage) => m.category === draft.category), (m: NeedMessage) => {
        Text(m.text).width('100%').padding(10).backgroundColor('#EEF3FA')
      }, (m: NeedMessage) => m.id.toString())
      TextInput({ text: this.messageInput, placeholder: '继续补充当前问题或约束' }).enabled(!this.busy).onChange((v: string) => { this.messageInput = v; })
      Button('保存这条上下文').enabled(!this.busy).onClick(() => { this.message(false); })
      Text('当前情境（可多选）').fontWeight(FontWeight.Medium)
      ForEach(contextOptions(draft.category), (option: ContextOption) => {
        Row() {
          Checkbox({ name: option.key }).enabled(!this.busy).select(this.contexts.includes(option.key))
            .onChange((selected: boolean) => {
              this.contexts = selected ? this.contexts.concat([option.key]) : this.contexts.filter((key: string) => key !== option.key);
              this.dirty = true;
            })
          Text(option.label).fontSize(14)
        }
      }, (option: ContextOption) => option.key)
      ForEach(this.definitions(), (field: NeedField) => { this.editor(field); }, (field: NeedField) => field.key)
      if (draft.assessment.missing.length > 0) {
        Text('待补充：' + draft.assessment.missing.join('、')).fontColor('#875500').width('100%')
      }
      ForEach(draft.assessment.conflicts, (risk: string) => { Text(risk).fontColor('#AA3030'); })
      if (this.dirty) { Text('有未保存修改；保存后重新校验，旧确认将失效。').fontColor('#AA5500') }
      Button('保存并校验草稿').enabled(!this.busy).onClick(() => { this.save(); })
      Button('确认当前草稿（不发布）')
        .enabled(!this.busy && !this.dirty && !this.session.routePending && draft.assessment.ready)
        .onClick(() => {
          const body: VersionRequest = { revision: this.session.revision };
          this.perform('/confirm', http.RequestMethod.POST, JSON.stringify(body));
        })
    }.width('100%').alignItems(HorizontalAlign.Start)
  }

  build() {
    Scroll() {
      Column({ space: 16 }) {
        Text('Campus · 四业务需求草稿').fontSize(26).fontWeight(FontWeight.Bold)
        Text('第二步：领域模型与真实持久化；尚未接入模型或真实发布工具。').fontSize(13)
        if (!this.loggedIn) {
          TextInput({ text: this.username, placeholder: '已有平台用户名' }).onChange((v: string) => { this.username = v; })
          TextInput({ text: this.password, placeholder: '密码（不保存）' }).type(InputType.Password).onChange((v: string) => { this.password = v; })
          Button('登录并恢复草稿').enabled(!this.busy && this.username.length > 0 && this.password.length > 0).onClick(() => { this.login(); })
        } else {
          Text('会话 #' + this.session.id + ' · 版本 ' + this.session.revision).fontSize(13)
          Row({ space: 10 }) {
            Button('重新加载').enabled(!this.busy).onClick(() => { this.reload(); })
            Button('退出登录').onClick(() => {
              if (this.dirty || this.messageInput.trim().length > 0) { this.notice = '请先保存输入，或使用放弃输入的退出按钮。'; return; }
              this.api.clear(); this.loggedIn = false; this.accept(emptySession()); this.sessions = []; this.notice = '';
            })
          }
          if (this.dirty || this.messageInput.trim().length > 0) {
            Button('放弃未保存输入并重新加载')
              .enabled(!this.busy).onClick(() => { this.reload(true); })
            Button('退出并放弃未保存输入').enabled(!this.busy).onClick(() => {
              this.api.clear(); this.loggedIn = false; this.accept(emptySession());
              this.sessions = []; this.messageInput = ''; this.routeInput = ''; this.notice = '';
            })
          }
          Button('新建会话').enabled(!this.busy && !this.dirty && this.messageInput.trim().length === 0).onClick(async () => {
            this.busy = true;
            try { this.accept(await this.api.send<NeedSession>('/need-sessions', http.RequestMethod.POST)); }
            catch (error) { this.notice = (error as BusinessError).message; }
            finally { this.busy = false; }
          })
          ForEach(this.sessions.filter((s: NeedSession) => s.id !== this.session.id), (s: NeedSession) => {
            Button('恢复会话 #' + s.id).enabled(!this.busy && !this.dirty && this.messageInput.trim().length === 0).onClick(async () => {
              this.busy = true;
              try { this.accept(await this.api.send<NeedSession>('/need-sessions/' + s.id)); }
              catch (error) { this.notice = (error as BusinessError).message; }
              finally { this.busy = false; }
            })
          }, (s: NeedSession) => s.id.toString())
          TextInput({ text: this.routeInput, placeholder: '先说想完成什么，例如想借相机并代取快递' }).enabled(!this.busy).onChange((v: string) => { this.routeInput = v; })
          Button('保存目标并提出分类候选（规则）').enabled(!this.busy).onClick(() => { this.message(true); })
          if (this.session.routePending) {
            Text(this.session.candidates.length === 0 ? '类别未知：请补充目标或手动选择；不会自动归入其他。' : '规则候选：' + this.session.candidates.map((c: NeedCategory) => domainName(c)).join('、') + '。请明确选择一项。')
          }
          ForEach(this.categories, (category: NeedCategory) => {
            Button(domainName(category) + (this.session.drafts.some((d: NeedDraft) => d.category === category) ? ' · 选择 / 恢复草稿' : ' · 选择'))
              .enabled(!this.busy).onClick(() => { this.select(category); })
          }, (category: NeedCategory) => category)
          ForEach(this.session.messages.filter((m: NeedMessage) => m.category === null), (m: NeedMessage) => { Text('原始目标：' + m.text).fontSize(13); }, (m: NeedMessage) => m.id.toString())
          ForEach(this.session.drafts.filter((draft: NeedDraft) => draft.category === this.session.activeCategory), (draft: NeedDraft) => { this.draftPanel(draft); }, (draft: NeedDraft) => draft.id.toString() + ':' + draft.revision.toString())
        }
        Text(this.notice).fontSize(14).fontColor('#285F45')
      }.width('100%').padding(20).alignItems(HorizontalAlign.Start)
    }.width('100%').height('100%')
  }
}
