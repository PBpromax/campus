import { describe, it, expect } from '@ohos/hypium';
import { ToolReceipt, canExecute, validHint } from '../../../main/ets/domain/ToolModels';
import { emptySession, NeedCategory, NeedDraft } from '../../../main/ets/domain/NeedModels';

export default function trustedToolsTest(): void {
  describe('TrustedToolsTest', () => {
    it('onlyKnownExplicitHintsCanFillAnOperation', 0, () => {
      expect(validHint({ action: 'PUBLISH', demandId: null, explanation: '' })).assertTrue();
      expect(validHint({ action: 'ACCEPT', demandId: 42, explanation: '' })).assertTrue();
      expect(validHint({ action: 'PUBLISH', demandId: 42, explanation: '' })).assertFalse();
      expect(validHint({ action: 'SQL', demandId: null, explanation: '' })).assertFalse();
      expect(validHint({ action: 'CANCEL', demandId: -1, explanation: '' })).assertFalse();
    });
    it('publishNeedsSameConfirmedSourceAndNoUnsavedInput', 0, () => {
      const s = emptySession(); s.id = 1; s.revision = 7; s.routePending = false; s.activeCategory = NeedCategory.STUDY;
      const fields: Record<string,string> = {}; fields['context'] = '某一步为什么取负号';
      const draft: NeedDraft = { id: 2, category: NeedCategory.STUDY, revision: 3, confirmedRevision: 3,
        fields: fields, provenance: {}, contexts: [], assessment: { ready: true, missing: [], conflicts: [] } };
      s.drafts = [draft];
      const r = JSON.parse('{"id":"x","action":"PUBLISH","state":"PREPARED","sourceSessionId":1,"sourceRevision":7}') as ToolReceipt;
      expect(canExecute(r,s,false)).assertTrue(); expect(canExecute(r,s,true)).assertFalse();
      s.revision++; expect(canExecute(r,s,false)).assertFalse();
      s.revision--; s.drafts[0].confirmedRevision = null; expect(canExecute(r,s,false)).assertFalse();
    });
    it('terminalReceiptCannotExecuteAgainFromUi', 0, () => {
      const r = JSON.parse('{"id":"x","action":"ACCEPT","state":"SUCCEEDED"}') as ToolReceipt;
      expect(canExecute(r,emptySession(),false)).assertFalse();
      r.state = 'REVOKED'; expect(canExecute(r,emptySession(),false)).assertFalse();
    });
  });
}
