import { NeedApi } from '../../../main/ets/data/NeedApi';
import { describe, it, expect } from '@ohos/hypium';
import { partner } from '../../../main/ets/ui/HumanHelp';
import { TaskSummary } from '../../../main/ets/domain/ToolModels';
export default function humanHelpTest(): void {
  describe('HumanHelpTest', () => {
    it('onlyTheOtherParticipantCanBeTheHumanRecipient',0,() => {
      const task: TaskSummary = { id: 1, title: 'fixture', category: 'STUDY', status: 'IN_PROGRESS', reward: 5, location: '线上', publisherId: 10, acceptorId: 20 };
      expect(partner(task,10)).assertEqual(20); expect(partner(task,20)).assertEqual(10);
      expect(partner(task,99) === null).assertTrue();
      task.acceptorId = null; expect(partner(task,10) === null).assertTrue();
    });
    it('logoutClearsTheMemoryOnlyParticipantIdentity',0,() => {
      const api = new NeedApi(); api.clear(); expect(api.currentUserId()).assertEqual(0);
    });
  });
}
