import { NeedSession, activeDraft } from './NeedModels';

export interface ToolHint { action: string; demandId: number | null; explanation: string; }
export interface Publication { title: string; reward: number; location: string; expectedTime: string; }
export interface ToolPrepare { action: string; requestKey: string; sessionId?: number; revision?: number; demandId?: number; reason?: string; publication?: Publication; }
export interface ToolConfirm { confirmed: boolean; }
export interface TaskResult { id: number; title: string; status: string; }
export interface TaskSummary { id: number; title: string; category: string; status: string; reward: number; location: string; publisherId: number; acceptorId: number | null; }
export interface ToolPreview { action: string; title: string; description: string; category: string; location: string; reward: number; expectedTime: string; demandId: number | null; beforeStatus: string | null; consequences: string[]; }
export interface ToolReceipt { id: string; action: string; state: string; expiresAt: string; preview: ToolPreview; result: TaskResult | null; errorCode: number | null; errorMessage: string | null; sourceSessionId: number | null; sourceRevision: number | null; }
export interface ToolAudit { id: number; intentId: string | null; action: string; outcome: string; message: string; createdAt: string; }

export function actionName(action: string): string {
  return action === 'PUBLISH' ? '发布任务' : action === 'ACCEPT' ? '接单' : action === 'CANCEL' ? '取消任务' : '未知操作';
}
export function validHint(hint: ToolHint): boolean {
  return ['PUBLISH','ACCEPT','CANCEL'].includes(hint.action) &&
    (hint.demandId === null || (Number.isSafeInteger(hint.demandId) && hint.demandId > 0)) &&
    !(hint.action === 'PUBLISH' && hint.demandId !== null);
}
export function publishReady(session: NeedSession): boolean {
  const draft = activeDraft(session);
  return session.id > 0 && !session.routePending && draft !== undefined &&
    draft.assessment.ready && draft.confirmedRevision === draft.revision;
}
export function canExecute(receipt: ToolReceipt, session: NeedSession, blocked: boolean): boolean {
  if (blocked || receipt.state !== 'PREPARED' || !['PUBLISH','ACCEPT','CANCEL'].includes(receipt.action)) { return false; }
  return receipt.action !== 'PUBLISH' || (receipt.sourceSessionId === session.id &&
    receipt.sourceRevision === session.revision && publishReady(session));
}
