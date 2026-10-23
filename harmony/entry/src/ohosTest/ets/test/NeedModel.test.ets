import { buildCards, decodeCards } from '../../../main/ets/ui/CardProtocol';
import { describe, it, expect } from '@ohos/hypium';
import { http } from '@kit.NetworkKit';
import { NeedCategory, NeedDraft, NeedSession, DomainCatalog, LoginResult, LoginRequest, CategoryRequest, EditRequest,
  MessageRequest, VersionRequest, emptySession, activeDraft, copyFields, draftStatus, contextOptions } from '../../../main/ets/domain/NeedModels';
import { NeedApi } from '../../../main/ets/data/NeedApi';

export default function needModelTest(): void {
  describe('NeedModelTest', () => {
    it('unselectedIntakeIsNotAProductionCategory', 0, () => {
      expect(emptySession().activeCategory === null).assertTrue();
      expect(emptySession().routePending).assertTrue();
      expect(emptySession().drafts.length).assertEqual(0);
    });
    it('editingDoesNotMutateServerSnapshot', 0, () => {
      const source: Record<string, string> = {}; source['context'] = 'y=-x';
      const copy = copyFields(source); copy['context'] = 'y=x';
      expect(source['context']).assertEqual('y=-x');
    });
    it('confirmedVersionMustMatchCurrentVersion', 0, () => {
      const draft: NeedDraft = { id: 1, category: NeedCategory.STUDY, revision: 3, confirmedRevision: 2,
        fields: {}, provenance: {}, contexts: [], assessment: { ready: true, missing: [], conflicts: [] } };
      expect(draftStatus(draft)).assertEqual('可核对草稿');
      draft.confirmedRevision = 3;
      expect(draftStatus(draft)).assertEqual('草稿已确认（未发布）');
    });
    it('allFourDomainsExposeFiveContexts', 0, () => {
      const categories: NeedCategory[] = [NeedCategory.BORROW, NeedCategory.STUDY, NeedCategory.RUNNING, NeedCategory.OTHER];
      categories.forEach((category: NeedCategory) => { expect(contextOptions(category).length).assertEqual(5); });
    });
    it('realServerPersistsAndRestoresAllFourDrafts', 0, async () => {
      // Disposable account in the isolated local development database; never uses a personal password.
      const api = new NeedApi();
      const credentials: LoginRequest = { username: 'arkts_step2_' + Date.now(), password: 'T_' + Date.now() };
      const account = await api.send<LoginResult>('/users/register', http.RequestMethod.POST, JSON.stringify(credentials));
      let session: NeedSession = emptySession();
      try {
        await api.login(credentials.username, credentials.password);
        session = await api.send<NeedSession>('/need-sessions', http.RequestMethod.POST);
        const definitions = await api.send<DomainCatalog[]>('/need-sessions/catalog');
        const categories: NeedCategory[] = [NeedCategory.RUNNING, NeedCategory.STUDY, NeedCategory.BORROW, NeedCategory.OTHER];
        for (let i = 0; i < categories.length; i++) {
          const choice: CategoryRequest = { revision: session.revision, category: categories[i] };
          session = await api.send<NeedSession>('/need-sessions/' + session.id + '/category', http.RequestMethod.PUT, JSON.stringify(choice));
          const fields: Record<string, string> = {};
          if (categories[i] === NeedCategory.STUDY) { fields['context'] = '第二步为什么取负号？y=-x'; }
          else if (categories[i] === NeedCategory.OTHER) { fields['serviceGoal'] = '协助活动布置'; }
          else { fields['item'] = categories[i] === NeedCategory.RUNNING ? '快递' : '相机'; }
          const edit: EditRequest = { revision: session.revision, fields: fields, contexts: [] };
          session = await api.send<NeedSession>('/need-sessions/' + session.id + '/draft', http.RequestMethod.PUT, JSON.stringify(edit));
          expect(decodeCards(JSON.stringify(buildCards(session)), session, definitions).error).assertEqual('');
        }
        const restored = await api.send<NeedSession>('/need-sessions/' + session.id);
        expect(restored.drafts.length).assertEqual(4);
        const choice: CategoryRequest = { revision: restored.revision, category: NeedCategory.STUDY };
        session = await api.send<NeedSession>('/need-sessions/' + session.id + '/category', http.RequestMethod.PUT, JSON.stringify(choice));
        const study = activeDraft(session);
        expect(study === undefined).assertFalse();
        if (study !== undefined) {
          expect(study.fields['context']).assertEqual('第二步为什么取负号？y=-x');
          expect(study.fields['item'] === undefined).assertTrue();
          expect(study.assessment.ready).assertTrue();
        }
        const confirm: VersionRequest = { revision: session.revision };
        session = await api.send<NeedSession>('/need-sessions/' + session.id + '/confirm', http.RequestMethod.POST, JSON.stringify(confirm));
        const message: MessageRequest = { revision: session.revision, text: '这里为什么有负号？' };
        session = await api.send<NeedSession>('/need-sessions/' + session.id + '/messages', http.RequestMethod.POST, JSON.stringify(message));
        expect(activeDraft(session)?.confirmedRevision === null).assertTrue();
        const remote = await api.send<NeedSession>('/need-sessions/' + session.id);
        expect(remote.messages[0].text).assertEqual('这里为什么有负号？');
      } finally {
        if (session.id > 0) {
          const version: VersionRequest = { revision: session.revision };
          await api.send<string>('/need-sessions/' + session.id, http.RequestMethod.DELETE, JSON.stringify(version));
        }
        await api.send<string>('/users/' + account.user.id, http.RequestMethod.DELETE);
        api.clear();
      }
    });
  });
}
