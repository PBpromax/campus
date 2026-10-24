// Explicit manual runner only: makes ONE real paid DeepSeek request through the development backend.
import { describe, it, expect } from '@ohos/hypium';
import { http } from '@kit.NetworkKit';
import { NeedApi } from '../../../main/ets/data/NeedApi';
import { AiEvent, AiText } from '../../../main/ets/data/AiStream';
import { NeedCategory, NeedSession, LoginRequest, LoginResult, CategoryRequest, MessageRequest, VersionRequest, emptySession } from '../../../main/ets/domain/NeedModels';

export default function deepSeekSmoke(): void {
  describe('DeepSeekRealDeviceSmoke', () => {
    it('realStreamingRoundTripPersistsChineseReply', 0, async () => {
      const api = new NeedApi();
      const credentials: LoginRequest = { username: 'arkts_ai_' + Date.now(), password: 'T_' + Date.now() };
      const account = await api.send<LoginResult>('/users/register', http.RequestMethod.POST, JSON.stringify(credentials));
      let session = emptySession();
      try {
        await api.login(credentials.username, credentials.password);
        session = await api.send<NeedSession>('/need-sessions', http.RequestMethod.POST);
        const category: CategoryRequest = { revision: session.revision, category: NeedCategory.STUDY };
        session = await api.send<NeedSession>('/need-sessions/' + session.id + '/category', http.RequestMethod.PUT, JSON.stringify(category));
        const message: MessageRequest = { revision: session.revision, text: '只问一个小问题：y=-x的导数为什么是-1？请用两句中文解释，不需要发布任务。' };
        let text = ''; let complete = false;
        await api.stream('/need-sessions/' + session.id + '/ai-stream', JSON.stringify(message), (event: AiEvent) => {
          if (event.kind === 'text_delta') { text += (JSON.parse(event.data) as AiText).text; }
          else if (event.kind === 'session') { session = JSON.parse(event.data) as NeedSession; }
          else if (event.kind === 'done') { complete = true; }
        });
        expect(complete).assertTrue(); expect(text.length > 0).assertTrue();
        const restored = await api.send<NeedSession>('/need-sessions/' + session.id);
        expect(restored.messages[restored.messages.length-1].role).assertEqual('ASSISTANT');
        expect(restored.messages[restored.messages.length-1].text.length > 0).assertTrue();
      } finally {
        if (session.id > 0) {
          // Reload after interruptions so cleanup uses the latest server version.
          session = await api.send<NeedSession>('/need-sessions/' + session.id);
          const revision: VersionRequest = { revision: session.revision };
          await api.send<string>('/need-sessions/' + session.id, http.RequestMethod.DELETE, JSON.stringify(revision));
        }
        await api.send<string>('/users/' + account.user.id, http.RequestMethod.DELETE); api.clear();
      }
    });
  });
}
