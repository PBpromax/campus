import importlib.util
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest
import urllib.error

SCRIPTS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPTS))
from private_env import read_env, DEEPSEEK_KEYS
spec = importlib.util.spec_from_file_location('check_deepseek', SCRIPTS / 'check-deepseek.py')
checker = importlib.util.module_from_spec(spec)
spec.loader.exec_module(checker)


class DeepSeekSetupTest(unittest.TestCase):
    def read(self, text):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'private.env'
            path.write_text(text)
            return read_env(path, DEEPSEEK_KEYS)

    def test_literal_secret_preserves_equals_and_shell_text(self):
        values = self.read("DEEPSEEK_API_KEY='$(never_run)=abc'\nDEEPSEEK_MODEL=deepseek-flash\n")
        self.assertEqual('$(never_run)=abc', values['DEEPSEEK_API_KEY'])

    def test_model_file_cannot_override_database_settings(self):
        with self.assertRaisesRegex(ValueError, 'Unexpected configuration key'):
            self.read('DB_PASSWORD=do-not-load')

    def test_duplicate_or_broken_quotes_rejected_without_secret_in_error(self):
        for text in ['DEEPSEEK_API_KEY=private-value\nDEEPSEEK_API_KEY=private-value',
                     'DEEPSEEK_API_KEY="private-value']:
            with self.assertRaises(ValueError) as raised:
                self.read(text)
            self.assertNotIn('private-value', str(raised.exception))

    def test_missing_key_never_makes_a_request(self):
        def forbidden(*args, **kwargs):
            self.fail('Network request without a key')
        self.assertEqual('missing_api_key', checker.probe({}, forbidden)['reason'])

    def test_probe_sends_only_minimal_fixture_to_official_endpoint(self):
        def opener(request, timeout):
            self.assertEqual('https://api.deepseek.com/chat/completions', request.full_url)
            body = json.loads(request.data)
            self.assertEqual('deepseek-flash', body['model'])
            self.assertEqual(16, body['max_tokens'])
            self.assertEqual('disabled', body['thinking']['type'])
            response = io.BytesIO(b'{"choices":[{"message":{"content":"OK"}}]}')
            response.status = 200
            return response
        result = checker.probe({'DEEPSEEK_API_KEY': 'fake-test-secret'}, opener)
        self.assertTrue(result['ok'])
        self.assertNotIn('fake-test-secret', json.dumps(result))

    def test_provider_error_does_not_echo_sensitive_body(self):
        def opener(request, timeout):
            raise urllib.error.HTTPError(request.full_url, 401, 'fake-test-secret', {}, io.BytesIO(b'fake-test-secret'))
        result = checker.probe({'DEEPSEEK_API_KEY': 'fake-test-secret'}, opener)
        self.assertEqual('invalid_api_key', result['reason'])
        self.assertNotIn('fake-test-secret', json.dumps(result))

    def test_empty_and_malformed_success_are_rejected(self):
        for payload in [b'{}', b'not-json', b'{"choices":[{"message":{"content":""}}]}']:
            def opener(request, timeout):
                response = io.BytesIO(payload)
                response.status = 200
                return response
            self.assertFalse(checker.probe({'DEEPSEEK_API_KEY':'fake-test-secret'}, opener)['ok'])


if __name__ == '__main__':
    unittest.main()
