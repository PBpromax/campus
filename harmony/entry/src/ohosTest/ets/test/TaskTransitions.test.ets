import { describe, it, expect } from '@ohos/hypium';
import { abilityDelegatorRegistry } from '@kit.TestKit';
import { util } from '@kit.ArkTS';
import { TaskSnapshot, TaskEvent, TaskStatus, TaskEventKind, TaskParticipant, TaskEventProjection, projectTransition } from '../../../main/ets/domain/TaskTransitions';

export default function taskTransitionsTest(): void {
  describe('TaskTransitionsTest', () => {
    it('matchesTheSame240GoldenCasesAsCangjie',0,async () => {
      const context = abilityDelegatorRegistry.getAbilityDelegator().getAppContext().createModuleContext('entry');
      const data = await context.resourceManager.getRawFileContent('task-transition-cases.tsv');
      const text = new util.TextDecoder('utf-8',{ fatal: true }).decodeToString(data);
      const rows = text.trim().split('\n'); expect(rows.length).assertEqual(240);
      rows.forEach((row: string) => {
        const cells = row.split('\t');
        const task: TaskSnapshot = { id: 1, category: cells[0], status: cells[1] as TaskStatus, version: Number(cells[2]) };
        const event: TaskEvent = { taskId: 1, id: 'case', expectedVersion: Number(cells[2]), kind: cells[3] as TaskEventKind, participant: cells[4] as TaskParticipant };
        const result = projectTransition(task,event);
        expect(result.outcome).assertEqual(cells[5]); expect(result.status).assertEqual(cells[6]); expect(result.version).assertEqual(Number(cells[7]));
      });
    });
    it('keepsVersionedLifecycleAndReplaySeparateFromCurrentState',0,() => {
      const task: TaskSnapshot = { id: 1, category: 'BORROW', status: TaskStatus.PENDING, version: 0 };
      const ledger = new TaskEventProjection([task]);
      const accept: TaskEvent = { taskId: 1, id: 'accept', expectedVersion: 0, kind: TaskEventKind.ACCEPT, participant: TaskParticipant.OTHER };
      expect(ledger.apply(accept).status).assertEqual(TaskStatus.IN_PROGRESS);
      const submit: TaskEvent = { taskId: 1, id: 'submit', expectedVersion: 1, kind: TaskEventKind.SUBMIT, participant: TaskParticipant.ACCEPTOR };
      expect(ledger.apply(submit).status).assertEqual(TaskStatus.PENDING_CONFIRMATION);
      const confirm: TaskEvent = { taskId: 1, id: 'confirm', expectedVersion: 2, kind: TaskEventKind.CONFIRM, participant: TaskParticipant.PUBLISHER };
      expect(ledger.apply(confirm).status).assertEqual(TaskStatus.COMPLETED);
      const replay = ledger.apply(accept);expect(replay.outcome).assertEqual('REPLAY');expect(replay.status).assertEqual(TaskStatus.IN_PROGRESS);
      accept.kind = TaskEventKind.CANCEL;expect(ledger.apply(accept).outcome).assertEqual('EVENT_ID_CONFLICT');
      expect(task.status).assertEqual(TaskStatus.PENDING);
    });
    it('rejectsStaleVersionsAndNeverWrapsTheSharedIntegerRange',0,() => {
      const task: TaskSnapshot = { id: 1, category: 'OTHER', status: TaskStatus.PENDING, version: 1 };
      const event: TaskEvent = { taskId: 1, id: 'a', expectedVersion: 0, kind: TaskEventKind.ACCEPT, participant: TaskParticipant.OTHER };
      expect(projectTransition(task,event).outcome).assertEqual('VERSION_CONFLICT');
      task.version = Number.MAX_SAFE_INTEGER;event.expectedVersion = task.version;
      expect(projectTransition(task,event).outcome).assertEqual('VERSION_EXHAUSTED');
      task.version = -1;let rejected = false;
      try { projectTransition(task,event); } catch (error) { rejected = true; }
      expect(rejected).assertTrue();
    });
    it('protectsStoredResultsAgainstCallerMutationAndScopesEventIdsToTask',0,() => {
      const task: TaskSnapshot = { id: 1, category: 'STUDY', status: TaskStatus.PENDING, version: 0 };
      const other: TaskSnapshot = { id: 2, category: 'RUNNING', status: TaskStatus.PENDING, version: 0 };
      const ledger = new TaskEventProjection([task,other]);
      const event: TaskEvent = { taskId: 1, id: 'same', expectedVersion: 0, kind: TaskEventKind.ACCEPT, participant: TaskParticipant.OTHER };
      const result = ledger.apply(event);result.status = TaskStatus.CANCELLED;
      expect(ledger.apply(event).status).assertEqual(TaskStatus.IN_PROGRESS);
      event.taskId = 2;expect(ledger.apply(event).outcome).assertEqual('APPLIED');
    });
  });
}
