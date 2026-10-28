import { describe, it, expect } from '@ohos/hypium';
import { userAnswer, statusLabel, friendlyNotice } from '../../../main/ets/ui/Presentation';
export default function presentationTest(): void {
  describe('PresentationTest', () => {
    it('removesKnownReasoningFromHistoricalAnswersButPreservesTeaching',0,() => {
      expect(userAnswer('<think>private</think>求导：-x 的导数是 -1。')).assertEqual('求导：-x 的导数是 -1。');
      expect(userAnswer('当 x < 0 时，-x > 0。')).assertEqual('当 x < 0 时，-x > 0。');
      expect(userAnswer('<analysis>unclosed')).assertEqual('');
    });
    it('showsTaskMeaningInsteadOfMachineStates',0,() => {
      expect(statusLabel('PENDING')).assertEqual('待接单');
      expect(statusLabel('CANCELLED')).assertEqual('已取消');
      expect(statusLabel('PREPARED')).assertEqual('待你确认');
    });
    it('explainsFailuresWithoutHttpOrVersionJargon',0,() => {
      expect(friendlyNotice('HTTP 503')).assertContain('保留输入');
      expect(friendlyNotice('409 版本冲突')).assertContain('输入仍保留');
      expect(friendlyNotice('HTTP 401')).assertContain('重新登录');
    });
  });
}
