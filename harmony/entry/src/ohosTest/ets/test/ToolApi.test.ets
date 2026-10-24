import { describe, it, expect } from '@ohos/hypium';
import { http } from '@kit.NetworkKit';
import { NeedApi } from '../../../main/ets/data/NeedApi';
import { LoginRequest, LoginResult, NeedSession, CategoryRequest, EditRequest, VersionRequest, NeedCategory, emptySession } from '../../../main/ets/domain/NeedModels';
import { ToolReceipt, ToolPrepare, ToolConfirm, ToolAudit } from '../../../main/ets/domain/ToolModels';

export default function toolApiTest(): void {
  describe('ToolApiTest', () => {
    it('devicePreparesExecutesReplaysAndCancelsRealFixture', 0, async () => {
      const api = new NeedApi();
      const stamp = Date.now();
      const credentials: LoginRequest = { username: 'arkts_tool_' + stamp, password: 'T_' + stamp };
      const account = await api.send<LoginResult>('/users/register',http.RequestMethod.POST,JSON.stringify(credentials));
      let session: NeedSession = emptySession(); let taskId = 0;
      try {
        await api.login(credentials.username,credentials.password);
        session = await api.send<NeedSession>('/need-sessions',http.RequestMethod.POST);
        const category: CategoryRequest = { revision: session.revision, category: NeedCategory.STUDY };
        session = await api.send<NeedSession>('/need-sessions/' + session.id + '/category',http.RequestMethod.PUT,JSON.stringify(category));
        const fields: Record<string,string> = {}; fields['context'] = '测试求助：y=-x中负号的含义';
        const edit: EditRequest = { revision: session.revision, fields: fields, contexts: [] };
        session = await api.send<NeedSession>('/need-sessions/' + session.id + '/draft',http.RequestMethod.PUT,JSON.stringify(edit));
        const version: VersionRequest = { revision: session.revision };
        session = await api.send<NeedSession>('/need-sessions/' + session.id + '/confirm',http.RequestMethod.POST,JSON.stringify(version));
        const future = new Date(Date.now() + 86400000 * 30);
        const date = future.getFullYear() + '-' + (future.getMonth() + 1).toString().padStart(2,'0') + '-' + future.getDate().toString().padStart(2,'0') + 'T12:00';
        const body: ToolPrepare = { action: 'PUBLISH', requestKey: 'native_' + stamp, sessionId: session.id, revision: session.revision,
          publication: { title: '[M2设备测试] 学习求助', reward: 5, location: '测试线上', expectedTime: date } };
        const prepared = await api.send<ToolReceipt>('/trusted-tools/prepare',http.RequestMethod.POST,JSON.stringify(body));
        expect(prepared.state).assertEqual('PREPARED'); expect(prepared.result === null).assertTrue();
        const confirm: ToolConfirm = { confirmed: true };
        const receipt = await api.send<ToolReceipt>('/trusted-tools/' + prepared.id + '/execute',http.RequestMethod.POST,JSON.stringify(confirm));
        expect(receipt.state).assertEqual('SUCCEEDED');
        if (receipt.result !== null) { taskId = receipt.result.id; }
        expect(taskId > 0).assertTrue();
        const replay = await api.send<ToolReceipt>('/trusted-tools/' + prepared.id + '/execute',http.RequestMethod.POST,JSON.stringify(confirm));
        expect(replay.result?.id).assertEqual(taskId);
        const forbidden: ToolPrepare = { action: 'ACCEPT', requestKey: 'own_' + stamp, demandId: taskId };
        let rejected = false;
        try { await api.send<ToolReceipt>('/trusted-tools/prepare',http.RequestMethod.POST,JSON.stringify(forbidden)); }
        catch (error) { rejected = true; }
        expect(rejected).assertTrue();
        const afterForbidden = await api.send<ToolReceipt>('/trusted-tools/' + prepared.id);
        expect(afterForbidden.state).assertEqual('SUCCEEDED');
        const cancel: ToolPrepare = { action: 'CANCEL', requestKey: 'cancel_' + stamp, demandId: taskId, reason: '设备自动测试结束' };
        const cancellation = await api.send<ToolReceipt>('/trusted-tools/prepare',http.RequestMethod.POST,JSON.stringify(cancel));
        const cancelled = await api.send<ToolReceipt>('/trusted-tools/' + cancellation.id + '/execute',http.RequestMethod.POST,JSON.stringify(confirm));
        expect(cancelled.result?.status).assertEqual('CANCELLED');
        const audits = await api.send<ToolAudit[]>('/trusted-tools/audits');
        expect(audits.filter((a: ToolAudit) => a.outcome === 'SUCCEEDED').length).assertEqual(2);
      } finally {
        // Delete only this disposable fixture. Historical tool receipts/audits remain for inspection.
        if (taskId > 0) { await api.send<string>('/demands/' + taskId,http.RequestMethod.DELETE); }
        if (session.id > 0) {
          const version: VersionRequest = { revision: session.revision };
          await api.send<string>('/need-sessions/' + session.id,http.RequestMethod.DELETE,JSON.stringify(version));
        }
        await api.send<string>('/users/' + account.user.id,http.RequestMethod.DELETE); api.clear();
      }
    });
  });
}
