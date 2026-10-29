import sys
from pathlib import Path
import unittest
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from capacity_metrics import distribution,loopback,StreamResult

class MeasurementCorrectnessTest(unittest.TestCase):
    def test_http_200_with_partial_answer_is_not_success(self):
        s=StreamResult()
        for line in ['event:text_delta','data:{"text":"部分"}',''] :s.line(line,30)
        self.assertFalse(s.record(200,500)['success'])
        for line in ['event:failure','data:{"kind":"TIMEOUT"}','']:s.line(line,500)
        self.assertEqual('TIMEOUT',s.record(200,500)['failureKind'])
    def test_task_summary_first_text_is_separate_from_session_and_proposal(self):
        s=StreamResult()
        for event,data,t in [('session',{},10),('text_delta',{'text':'结果摘要'},650),('proposal',{'fields':{}},660),('done',{'complete':True},670)]:
            for line in ['event:'+event,'data:'+__import__('json').dumps(data),'']:s.line(line,t)
        r=s.record(200,680);self.assertTrue(r['success']);self.assertEqual(650,r['firstVisibleTextMs']);self.assertEqual(1,r['proposals'])
    def test_percentiles_keep_tail_and_do_not_treat_empty_as_zero(self):
        r=distribution([1]*19+[1000]);self.assertEqual(1,r['p95Ms']);self.assertEqual(1000,r['maxMs'])
        self.assertIsNone(distribution([])['p95Ms'])
    def test_loopback_guard_rejects_remote_or_embedded_credentials(self):
        self.assertEqual('http://127.0.0.1:18084',loopback('http://127.0.0.1:18084/'))
        for url in ['https://api.deepseek.com','http://example.com','http://key@localhost','http://localhost?token=key']:
            with self.assertRaises(ValueError):loopback(url)

if __name__=='__main__':unittest.main()
