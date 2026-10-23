import { NeedCategory, NeedSession, NeedDraft, NeedField, DomainCatalog, activeDraft } from '../domain/NeedModels';

export interface CardNode {
  id: string;
  kind: string;
  title: string;
  fields: string[];
  message: string;
  actions: string[];
}
export interface CardSchema {
  version: number;
  source: string;
  sessionId: number;
  sessionRevision: number;
  category: NeedCategory | null;
  draftRevision: number;
  cards: CardNode[];
}
export interface CardGroup { id: string; title: string; fields: string[]; }
export interface CardDecode { schema: CardSchema; error: string; }

export function allowedKeys(category: NeedCategory): string[] {
  if (category === NeedCategory.RUNNING) { return ["taskKind", "item", "quantity", "pickupPlace", "destination", "earliestPickup", "latestDelivery", "handoff", "dropPlace", "notice", "completionProof", "language", "handling", "costLimit"]; }
  if (category === NeedCategory.STUDY) { return ["context", "topic", "stuckStep", "depth", "priority", "availableTime", "communication", "language"]; }
  if (category === NeedCategory.BORROW) { return ["item", "purpose", "spec", "quantity", "needBy", "useStart", "useEnd", "returnAt", "returnDeadline", "pickupPlace", "returnPlace", "handoff", "deliveryConsent", "support", "accessories", "restrictions"]; }
  if (category === NeedCategory.OTHER) { return ["serviceType", "serviceGoal", "resourceRequirements", "serviceStart", "serviceEnd", "servicePlace", "communication", "completionCriteria", "consent", "helperCount", "splitMode"]; }
  return [];
}
export function emptySchema(): CardSchema {
  return { version: 1, source: 'local_rules', sessionId: 0, sessionRevision: 0,
    category: null, draftRevision: 0, cards: [] };
}
function groups(category: NeedCategory): CardGroup[] {
  if (category === NeedCategory.BORROW) {
    return [{ id: 'specification', title: '借什么，是否适用', fields: ['item','purpose','spec','quantity','support','accessories','restrictions'] },
      { id: 'period', title: '使用与归还时间', fields: ['needBy','useStart','useEnd','returnAt','returnDeadline'] },
      { id: 'handoff', title: '取还与送达约定', fields: ['pickupPlace','returnPlace','handoff','deliveryConsent'] }];
  }
  if (category === NeedCategory.STUDY) {
    return [{ id: 'learning', title: '从卡住的地方继续', fields: allowedKeys(category) }];
  }
  if (category === NeedCategory.RUNNING) {
    return [{ id: 'running', title: '代办内容与时间', fields: ['taskKind','item','quantity','pickupPlace','destination','earliestPickup','latestDelivery','costLimit'] },
      { id: 'handoff', title: '交接与通知', fields: ['handoff','dropPlace','notice','completionProof','language','handling'] }];
  }
  return [{ id: 'scope', title: '目标、资源与协助范围', fields: ['serviceType','serviceGoal','resourceRequirements','helperCount','splitMode','consent','completionCriteria'] },
    { id: 'service', title: '时间、位置与沟通', fields: ['serviceStart','serviceEnd','servicePlace','communication'] }];
}
export function buildCards(session: NeedSession): CardSchema {
  const schema = emptySchema();
  const draft = activeDraft(session);
  schema.sessionId = session.id; schema.sessionRevision = session.revision;
  schema.category = session.activeCategory;
  if (draft === undefined) { return schema; }
  schema.draftRevision = draft.revision;
  groups(draft.category).forEach((group: CardGroup) => {
    const keys = group.fields.filter((key: string) => draft.assessment.missing.includes(key) || (draft.fields[key] || '').length > 0);
    if (keys.length > 0) {
      schema.cards.push({ id: group.id, kind: 'fields', title: group.title, fields: keys,
        message: '只补当前需要的信息；其他条件可在完整表单中补充。', actions: ['save','form'] });
    }
  });
  if (draft.assessment.conflicts.length > 0) {
    schema.cards.push({ id: 'conflict', kind: 'notice', title: '这些约定需要调整', fields: [],
      message: draft.assessment.conflicts.join('\n'), actions: ['form'] });
  }
  if (draft.assessment.ready && !session.routePending) {
    schema.cards.push({ id: 'review', kind: 'review', title: '核对本次草稿', fields: [],
      message: '只确认当前草稿，不会发布任务或取得对方授权。', actions: ['review','form'] });
  }
  return schema;
}
function exactKeys(value: Object, allowed: string[]): boolean {
  const keys = Object.keys(value);
  return keys.length === allowed.length && keys.every((key: string) => allowed.includes(key));
}
export function validateCards(schema: CardSchema, session: NeedSession, catalog: DomainCatalog[]): string {
  if (schema === null || typeof schema !== 'object' || Array.isArray(schema)) { return '卡片格式错误'; }
  if (!exactKeys(schema, ['version','source','sessionId','sessionRevision','category','draftRevision','cards'])) { return '卡片含未知属性'; }
  const draft = activeDraft(session);
  if (draft === undefined) { return '请先选择业务'; }
  if (schema.version !== 1 || schema.source !== 'local_rules') { return '卡片协议版本或来源不支持'; }
  if (schema.sessionId !== session.id || schema.sessionRevision !== session.revision ||
    schema.category !== draft.category || schema.draftRevision !== draft.revision) { return '卡片版本已过期'; }
  if (!Array.isArray(schema.cards) || schema.cards.length < 1 || schema.cards.length > 8) { return '卡片数量超出范围'; }
  const definition = catalog.find((entry: DomainCatalog) => entry.category === draft.category);
  if (definition === undefined) { return '业务字段定义不可用'; }
  const ids: string[] = [];
  for (let i = 0; i < schema.cards.length; i++) {
    const card = schema.cards[i];
    if (card === null || typeof card !== 'object' || Array.isArray(card) ||
      !exactKeys(card, ['id','kind','title','fields','message','actions'])) { return '卡片结构错误'; }
    if (typeof card.id !== 'string' || !/^[a-z_]{1,32}$/.test(card.id) || ids.includes(card.id)) { return '卡片标识错误'; }
    ids.push(card.id);
    if (!['fields','notice','review'].includes(card.kind) || typeof card.title !== 'string' || card.title.length > 80 ||
      typeof card.message !== 'string' || card.message.length > 4000) { return '不支持的卡片组件或文本'; }
    if (!Array.isArray(card.fields) || card.fields.length > 16 || !Array.isArray(card.actions) || card.actions.length > 3) { return '卡片字段或操作格式错误'; }
    const keys: string[] = [];
    for (let j = 0; j < card.fields.length; j++) {
      const key = card.fields[j];
      const field = definition.fields.find((f: NeedField) => f.key === key);
      if (!allowedKeys(draft.category).includes(key) || keys.includes(key) || field === undefined ||
        !['text','number','datetime-local','select'].includes(field.type) ||
        !Array.isArray(field.options) || field.options.length > 20 ||
        !field.options.every((option: string) => typeof option === 'string' && option.length <= 100)) { return '字段不在当前业务组件白名单'; }
      keys.push(key);
    }
    if ((card.kind === 'fields' && keys.length === 0) || (card.kind !== 'fields' && keys.length !== 0)) { return '组件与字段不匹配'; }
    if (new Set(card.actions).size !== card.actions.length) { return '重复操作'; }
    for (let j = 0; j < card.actions.length; j++) {
      const action = card.actions[j];
      if (!['save','review','form'].includes(action) ||
        (action === 'save' && card.kind !== 'fields') || (action === 'review' && card.kind !== 'review')) { return '操作不在组件白名单'; }
    }
    if (card.kind === 'review' && (!draft.assessment.ready || session.routePending)) { return '未满足核对条件'; }
  }
  return '';
}
export function decodeCards(raw: string, session: NeedSession, catalog: DomainCatalog[]): CardDecode {
  if (raw.length > 24000) { return { schema: emptySchema(), error: '卡片数据过大' }; }
  try {
    const schema = JSON.parse(raw) as CardSchema;
    const error = validateCards(schema, session, catalog);
    return { schema: error.length === 0 ? schema : emptySchema(), error: error };
  } catch (error) { return { schema: emptySchema(), error: '卡片无法解析' }; }
}
export function allowsAction(schema: CardSchema, id: string, action: string, session: NeedSession,
  catalog: DomainCatalog[], dirty: boolean): boolean {
  if (validateCards(schema, session, catalog).length > 0) { return false; }
  const card = schema.cards.find((node: CardNode) => node.id === id);
  return card !== undefined && card.actions.includes(action) && !(action === 'review' && dirty);
}
