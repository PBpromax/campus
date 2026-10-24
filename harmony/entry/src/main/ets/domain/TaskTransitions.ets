// A read-only state projection. Permissions, credit, cancellation windows and effects stay on the server.
export enum TaskStatus { PENDING = 'PENDING', IN_PROGRESS = 'IN_PROGRESS', PENDING_CONFIRMATION = 'PENDING_CONFIRMATION', COMPLETED = 'COMPLETED', CANCELLED = 'CANCELLED' }
export enum TaskEventKind { ACCEPT = 'ACCEPT', SUBMIT = 'SUBMIT', CONFIRM = 'CONFIRM', CANCEL = 'CANCEL' }
export enum TaskParticipant { PUBLISHER = 'PUBLISHER', ACCEPTOR = 'ACCEPTOR', OTHER = 'OTHER' }
export interface TaskSnapshot { id: number; category: string; status: TaskStatus; version: number; }
export interface TaskEvent { taskId: number; id: string; expectedVersion: number; kind: TaskEventKind; participant: TaskParticipant; }
export interface TransitionDecision { outcome: string; status: TaskStatus; version: number; }
export interface TransitionRequest { demandId: number; event: TaskEventKind; }
export interface TransitionPreview { engine: string; source: TaskSnapshot; participant: TaskParticipant; event: TaskEventKind; decision: TransitionDecision; notice: string; }
interface EventMemo { fingerprint: string; result: TransitionDecision; }
function copySnapshot(v: TaskSnapshot): TaskSnapshot { return { id: v.id, category: v.category, status: v.status, version: v.version }; }
function copyDecision(v: TransitionDecision): TransitionDecision { return { outcome: v.outcome, status: v.status, version: v.version }; }
function validInteger(v: number, positive: boolean = false): boolean { return Number.isSafeInteger(v) && v >= (positive ? 1 : 0); }
export function validSnapshot(v: TaskSnapshot): boolean {
  return validInteger(v.id,true) && validInteger(v.version) && ['RUNNING','STUDY','BORROW','OTHER'].includes(v.category) &&
    ['PENDING','IN_PROGRESS','PENDING_CONFIRMATION','COMPLETED','CANCELLED'].includes(v.status);
}
function validEvent(v: TaskEvent): boolean {
  return validInteger(v.taskId,true) && validInteger(v.expectedVersion) && /^[A-Za-z0-9_-]{1,64}$/.test(v.id) &&
    ['ACCEPT','SUBMIT','CONFIRM','CANCEL'].includes(v.kind) && ['PUBLISHER','ACCEPTOR','OTHER'].includes(v.participant);
}
export function projectTransition(task: TaskSnapshot, event: TaskEvent): TransitionDecision {
  if (!validSnapshot(task) || !validEvent(event)) { throw new Error('INVALID_INPUT'); }
  const rejected: TransitionDecision = { outcome: 'STATE_OR_ROLE_REJECTED', status: task.status, version: task.version };
  if (task.id !== event.taskId) { rejected.outcome = 'TASK_MISMATCH'; return rejected; }
  if (task.version !== event.expectedVersion) { rejected.outcome = 'VERSION_CONFLICT'; return rejected; }
  if (task.version === Number.MAX_SAFE_INTEGER) { rejected.outcome = 'VERSION_EXHAUSTED'; return rejected; }
  let next: TaskStatus | undefined = undefined;
  if (task.status === TaskStatus.PENDING && event.kind === TaskEventKind.ACCEPT && event.participant === TaskParticipant.OTHER) { next = TaskStatus.IN_PROGRESS; }
  else if (task.status === TaskStatus.IN_PROGRESS && event.kind === TaskEventKind.SUBMIT && event.participant === TaskParticipant.ACCEPTOR) { next = TaskStatus.PENDING_CONFIRMATION; }
  else if (task.status === TaskStatus.PENDING_CONFIRMATION && event.kind === TaskEventKind.CONFIRM && event.participant === TaskParticipant.PUBLISHER) { next = TaskStatus.COMPLETED; }
  else if (event.kind === TaskEventKind.CANCEL && ((task.status === TaskStatus.PENDING && event.participant === TaskParticipant.PUBLISHER) ||
    ([TaskStatus.IN_PROGRESS,TaskStatus.PENDING_CONFIRMATION].includes(task.status) && event.participant === TaskParticipant.ACCEPTOR))) { next = TaskStatus.CANCELLED; }
  return next === undefined ? rejected : { outcome: 'APPLIED', status: next, version: task.version + 1 };
}
// Owned by one ArkTS event loop. This Map is not a shared TaskPool/Worker object.
export class TaskEventProjection {
  private tasks: Map<number,TaskSnapshot> = new Map<number,TaskSnapshot>();
  private seen: Map<string,EventMemo> = new Map<string,EventMemo>();
  constructor(tasks: TaskSnapshot[]) {
    tasks.forEach((task: TaskSnapshot) => {
      if (!validSnapshot(task) || this.tasks.has(task.id)) { throw new Error('INVALID_TASK'); }
      this.tasks.set(task.id,copySnapshot(task));
    });
  }
  apply(event: TaskEvent): TransitionDecision {
    if (!validEvent(event)) { throw new Error('INVALID_EVENT'); }
    const task = this.tasks.get(event.taskId); if (task === undefined) { throw new Error('TASK_MISSING'); }
    const key = event.taskId + ':' + event.id;
    const fingerprint = event.taskId + ':' + event.expectedVersion + ':' + event.kind + ':' + event.participant;
    const previous = this.seen.get(key);
    if (previous !== undefined) {
      if (previous.fingerprint !== fingerprint) { return { outcome: 'EVENT_ID_CONFLICT', status: task.status, version: task.version }; }
      return { outcome: 'REPLAY', status: previous.result.status, version: previous.result.version };
    }
    const result = projectTransition(task,event);
    this.seen.set(key,{ fingerprint: fingerprint, result: copyDecision(result) });
    if (result.outcome === 'APPLIED') { this.tasks.set(task.id,{ id: task.id, category: task.category, status: result.status, version: result.version }); }
    return copyDecision(result);
  }
}
