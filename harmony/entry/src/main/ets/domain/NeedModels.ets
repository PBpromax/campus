// Four production categories. AUTO is represented by an unselected category, never sent as a category.
export enum NeedCategory {
  RUNNING = 'RUNNING', STUDY = 'STUDY', BORROW = 'BORROW', OTHER = 'OTHER'
}

export interface NeedField {
  key: string;
  label: string;
  type: string;
  options: string[];
}
export interface DomainCatalog {
  category: NeedCategory;
  fields: NeedField[];
}
export interface Assessment {
  ready: boolean;
  missing: string[];
  conflicts: string[];
}
export interface NeedDraft {
  id: number;
  category: NeedCategory;
  revision: number;
  confirmedRevision: number | null;
  fields: Record<string, string>;
  provenance: Record<string, string>;
  contexts: string[];
  assessment: Assessment;
}
export interface NeedMessage {
  id: number;
  category: NeedCategory | null;
  role: string;
  text: string;
  createdAt: string;
}
export interface NeedSession {
  id: number;
  revision: number;
  activeCategory: NeedCategory | null;
  routePending: boolean;
  intakeText: string;
  candidates: NeedCategory[];
  drafts: NeedDraft[];
  messages: NeedMessage[];
  updatedAt: string;
}
export interface ApiEnvelope<T> {
  code: number;
  message: string;
  data: T;
}
export interface LoginUser { id: number; username: string; }
export interface LoginResult { token: string; user: LoginUser; }
export interface LoginRequest { username: string; password: string; }
export interface VersionRequest { revision: number; }
export interface CategoryRequest { revision: number; category: NeedCategory; }
export interface MessageRequest { revision: number; text: string; }
export interface EditRequest { revision: number; fields: Record<string, string>; contexts: string[]; }
export interface ContextOption { key: string; label: string; }

export function contextOptions(category: NeedCategory): ContextOption[] {
  if (category === NeedCategory.BORROW) {
    return [{ key: 'compatibility', label: '规格或兼容性不确定' }, { key: 'urgent', label: '急需使用' }, { key: 'handoff', label: '取还受限' }, { key: 'beginner', label: '不熟悉使用' }, { key: 'multiDay', label: '跨天借用或归还受限' }];
  }
  if (category === NeedCategory.STUDY) {
    return [{ key: 'foundation', label: '基础薄弱' }, { key: 'limited', label: '时间紧张' }, { key: 'remote', label: '时间或地点受限' }, { key: 'language', label: '跨语言解释' }, { key: 'fragment', label: '只问某个知识点或步骤' }];
  }
  if (category === NeedCategory.RUNNING) {
    return [{ key: 'mobility', label: '行动或搬运受限' }, { key: 'quiet', label: '上课中，需要静默通知' }, { key: 'newcomer', label: '不熟悉取送地点' }, { key: 'language', label: '跨语言确认' }, { key: 'dropoff', label: '指定位置放置' }];
  }
  return [{ key: 'diagnosis', label: '设备情况不清' }, { key: 'team', label: '需要多人协助' }, { key: 'remote', label: '需要线上协助' }, { key: 'window', label: '只有短时窗口' }, { key: 'scope', label: '协助范围还不明确' }];
}

export function domainName(category: NeedCategory): string {
  if (category === NeedCategory.RUNNING) { return '跑腿代办'; }
  if (category === NeedCategory.STUDY) { return '学习辅导'; }
  if (category === NeedCategory.BORROW) { return '物品借用'; }
  return '其他互助';
}
export function emptySession(): NeedSession {
  return { id: 0, revision: 0, activeCategory: null, routePending: true, intakeText: '', candidates: [], drafts: [], messages: [], updatedAt: '' };
}
export function copyFields(input: Record<string, string>): Record<string, string> {
  const result: Record<string, string> = {};
  Object.keys(input).forEach((key: string) => { result[key] = input[key]; });
  return result;
}
export function activeDraft(session: NeedSession): NeedDraft | undefined {
  return session.drafts.find((draft: NeedDraft) => draft.category === session.activeCategory);
}
export function draftStatus(draft: NeedDraft): string {
  if (draft.confirmedRevision === draft.revision) { return '草稿已确认（未发布）'; }
  return draft.assessment.ready ? '可核对草稿' : '继续澄清';
}

export interface AiProposal {
  token: string;
  sessionId: number;
  revision: number;
  category: NeedCategory | null;
  fields: Record<string,string>;
  candidates: NeedCategory[];
  assessment: Assessment;
  warnings: string[];
}
