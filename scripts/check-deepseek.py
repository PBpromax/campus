#!/usr/bin/env python3
"""Check local DeepSeek configuration. --probe makes one small paid API request."""
import argparse
import json
from pathlib import Path
import sys
import urllib.error
import urllib.request
from private_env import read_env, DEEPSEEK_KEYS

ENDPOINT = 'https://api.deepseek.com/chat/completions'
DEFAULT_MODEL = 'deepseek-flash'


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, msg, headers, new_url):
        return None


def probe(values, opener=None):
    key = values.get('DEEPSEEK_API_KEY', '').strip()
    if not key:
        return {'ok': False, 'reason': 'missing_api_key'}
    model = values.get('DEEPSEEK_MODEL', '').strip() or DEFAULT_MODEL
    request = urllib.request.Request(ENDPOINT, method='POST',
        headers={'Authorization': 'Bearer ' + key, 'Content-Type': 'application/json'},
        data=json.dumps({'model': model, 'messages': [{'role': 'user', 'content': 'Reply with OK.'}],
                         'thinking': {'type': 'disabled'}, 'stream': False, 'max_tokens': 16}).encode())
    try:
        with (opener or urllib.request.build_opener(NoRedirect()).open)(request, timeout=30) as response:
            raw = response.read(65537)
            if len(raw) > 65536:
                return {'ok': False, 'reason': 'oversized_response'}
            body = json.loads(raw)
            content = body['choices'][0]['message']['content']
            if not isinstance(content, str) or not content.strip():
                return {'ok': False, 'reason': 'empty_response'}
            return {'ok': True, 'model': model, 'http': response.status}
    except urllib.error.HTTPError as error:
        # Do not print provider response bodies or request headers.
        status = error.code
        error.close()
        return {'ok': False, 'http': status, 'reason': {401: 'invalid_api_key', 402: 'insufficient_balance',
                429: 'rate_limited'}.get(status, 'provider_error')}
    except (urllib.error.URLError, TimeoutError, OSError):
        return {'ok': False, 'reason': 'connection_failed'}
    except (ValueError, KeyError, IndexError, TypeError):
        return {'ok': False, 'reason': 'invalid_response'}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--config', type=Path, default=Path(__file__).resolve().parents[1] / '.env.deepseek.local')
    parser.add_argument('--probe', action='store_true', help='Make one small real API call; may incur API charges.')
    args = parser.parse_args()
    try:
        values = read_env(args.config, DEEPSEEK_KEYS)
    except (OSError, ValueError):
        print(json.dumps({'ok': False, 'reason': 'invalid_or_missing_local_config'}))
        return 1
    if not args.probe:
        print(json.dumps({'configured': bool(values.get('DEEPSEEK_API_KEY', '').strip()),
                          'model': values.get('DEEPSEEK_MODEL') or DEFAULT_MODEL,
                          'endpoint': ENDPOINT, 'networkRequestMade': False}))
        return 0
    result = probe(values)
    print(json.dumps(result))
    return 0 if result['ok'] else 1


if __name__ == '__main__':
    sys.exit(main())
