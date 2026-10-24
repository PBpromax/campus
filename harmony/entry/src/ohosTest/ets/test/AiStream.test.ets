import { util } from '@kit.ArkTS';
import { describe, it, expect } from '@ohos/hypium';
import { AiStreamDecoder, AiEvent, AiText } from '../../../main/ets/data/AiStream';

function bytes(text: string): ArrayBuffer { return new util.TextEncoder().encodeInto(text).buffer as ArrayBuffer; }
export default function aiStreamTest(): void {
  describe('AiStreamTest', () => {
    it('preservesChineseAcrossEveryByteBoundary', 0, () => {
      const raw = bytes('event:text_delta\ndata:{"text":"中文负号−"}\n\nevent:done\ndata:{"complete":true}\n\n');
      const decoder = new AiStreamDecoder(); let result = '';
      for (let i = 0; i < raw.byteLength; i++) {
        decoder.feed(raw.slice(i,i+1), (event: AiEvent) => {
          if (event.kind === 'text_delta') { result += (JSON.parse(event.data) as AiText).text; }
        });
      }
      decoder.finish(); expect(result).assertEqual('中文负号−');
    });
    it('supportsCrLfAndMultipleEventsInOnePacket', 0, () => {
      const decoder = new AiStreamDecoder(); const kinds: string[] = [];
      decoder.feed(bytes(':heartbeat\r\n\r\nevent:notice\r\ndata:{"message":"x"}\r\n\r\nevent:done\r\ndata:{}\r\n\r\n'), (e: AiEvent) => { kinds.push(e.kind); });
      decoder.finish(); expect(kinds.join(',')).assertEqual('notice,done');
    });
    it('truncatedStreamIsNotSuccess', 0, () => {
      const decoder = new AiStreamDecoder(); let rejected = false;
      decoder.feed(bytes('event:text_delta\ndata:{"text":"部分"}\n\n'), (_e: AiEvent) => {});
      try { decoder.finish(); } catch (error) { rejected = true; }
      expect(rejected).assertTrue();
    });
    it('unknownEventsAndMalformedJsonAreRejected', 0, () => {
      ['event:publish\ndata:{}\n\n','event:notice\ndata:{broken}\n\n'].forEach((raw: string) => {
        let rejected = false;
        try { new AiStreamDecoder().feed(bytes(raw), (_e: AiEvent) => {}); } catch (error) { rejected = true; }
        expect(rejected).assertTrue();
      });
    });
    it('actionSuggestionIsAnEventAndStillNeedsDone', 0, () => {
      const decoder = new AiStreamDecoder(); const kinds: string[] = [];
      decoder.feed(bytes('event:action_hint\ndata:{"action":"PUBLISH","demandId":null}\n\n'), (e: AiEvent) => { kinds.push(e.kind); });
      expect(decoder.completed).assertFalse();
      decoder.feed(bytes('event:done\ndata:{}\n\n'), (e: AiEvent) => { kinds.push(e.kind); });
      decoder.finish(); expect(kinds.join(',')).assertEqual('action_hint,done');
    });
    it('eventsAfterDoneAreRejected', 0, () => {
      let rejected = false;
      try { new AiStreamDecoder().feed(bytes('event:done\ndata:{}\n\nevent:text_delta\ndata:{"text":"x"}\n\n'), (_e: AiEvent) => {}); }
      catch (error) { rejected = true; }
      expect(rejected).assertTrue();
    });
  });
}
