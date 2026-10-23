#!/usr/bin/env python3
"""Local-only M2 draft smoke test. Run seed, restart backend, then verify.
Uses disposable accounts in campus_m2_dev; no credentials are printed.
"""
import argparse
from datetime import datetime
import json
from pathlib import Path
import secrets
import urllib.request
import urllib.error

BASE = 'http://127.0.0.1:8081/api'

def call(path, method='GET', body=None, token='', expected=200):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    req = urllib.request.Request(BASE + path, data=None if body is None else json.dumps(body).encode(), headers=headers, method=method)
    try:
        response = urllib.request.urlopen(req, timeout=15)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        status = response.status
        raw = response.read()
    assert status == expected, f'{method} {path}: expected HTTP {expected}, got {status}'
    if status != 200:
        return None
    envelope = json.loads(raw)
    assert envelope['code'] == 200, f'{method} {path}: business error'
    return envelope['data']

def credentials():
    return {'username': 'm2_smoke_' + secrets.token_hex(5), 'password': secrets.token_urlsafe(12)}

def fields():
    return {
        'RUNNING': dict(taskKind='代取快递', item='书籍', quantity='1', pickupPlace='菜鸟驿站A区', destination='一号宿舍楼门口', earliestPickup='2026-11-01T09:00', latestDelivery='2026-11-01T10:00', handoff='当面交付', notice='短消息', completionProof='当面确认'),
        'STUDY': dict(context='y=-x，第二步到第三步为什么取负号？'),
        'BORROW': dict(item='相机', purpose='拍摄活动', spec='索尼E卡口', quantity='1', needBy='2026-11-01T09:00', useStart='2026-11-01T10:00', useEnd='2026-11-01T12:00', returnAt='2026-11-01T13:00', pickupPlace='图书馆东门', returnPlace='图书馆东门', handoff='当面交接'),
        'OTHER': dict(serviceType='活动布置', serviceGoal='布置社团展台', resourceRequirements='自带胶带', serviceStart='2026-11-01T09:00', serviceEnd='2026-11-01T10:00', servicePlace='学生活动中心101', communication='线下协助', completionCriteria='摆放两张桌子并张贴三张海报', consent='无需额外授权')
    }

def seed(state_file):
    assert not state_file.exists(), 'State file already exists; verify/clean it before seeding again.'
    users = [credentials(), credentials()]
    tokens = []
    for user in users:
        registered = call('/users/register', 'POST', user)
        user['id'] = registered['user']['id']
        tokens.append(registered['token'])
    state = {'users': users, 'session': None, 'seededAt': datetime.now().astimezone().isoformat()}
    state_file.touch(mode=0o600)
    state_file.write_text(json.dumps(state))
    catalog = call('/need-sessions/catalog', token=tokens[0])
    assert {x['category'] for x in catalog} == set(fields())
    assert sum(len(x['fields']) for x in catalog) == 49
    session = call('/need-sessions', 'POST', token=tokens[0])
    state['session'] = session['id']
    state_file.write_text(json.dumps(state))
    path = '/need-sessions/' + str(session['id'])
    for category, values in fields().items():
        session = call(path + '/category', 'PUT', dict(revision=session['revision'], category=category), tokens[0])
        session = call(path + '/draft', 'PUT', dict(revision=session['revision'], fields=values, contexts=[]), tokens[0])
        draft = next(x for x in session['drafts'] if x['category'] == category)
        assert draft['assessment']['ready'], category
        session = call(path + '/confirm', 'POST', dict(revision=session['revision']), tokens[0])
    call(path, token=tokens[1], expected=404)
    call(path + '/category', 'PUT', dict(revision=0, category='STUDY'), tokens[0], expected=409)
    call(path + '/category', 'PUT', dict(revision=session['revision'], category='AUTO'), tokens[0], expected=400)
    state['snapshot'] = session
    state_file.write_text(json.dumps(state, ensure_ascii=False))
    print(json.dumps({'phase': 'seed', 'domains': 4, 'fieldDefinitions': 49, 'allFourReadyAndConfirmed': True, 'ownership404': True, 'staleWrite409': True, 'invalidAuto400': True}))

def verify(state_file):
    state = json.loads(state_file.read_text())
    user = state['users'][0]
    token = call('/users/login', 'POST', {k: user[k] for k in ('username','password')})['token']
    path = '/need-sessions/' + str(state['session'])
    remote = call(path, token=token)
    assert remote == state['snapshot'], 'Draft/session changed across backend restart'
    assert {d['category']: d['fields'] for d in remote['drafts']} == fields()
    remote = call(path + '/category', 'PUT', dict(revision=remote['revision'], category='STUDY'), token)
    study = next(d for d in remote['drafts'] if d['category'] == 'STUDY')
    assert study['confirmedRevision'] is None
    assert 'item' not in study['fields']
    remote = call(path + '/intake', 'POST', dict(revision=remote['revision'], text='想借相机，也想代取快递'), token)
    assert set(remote['candidates']) == {'BORROW', 'RUNNING'}
    assert remote['activeCategory'] == 'STUDY' and remote['routePending']
    call(path + '/confirm', 'POST', dict(revision=remote['revision']), token, expected=409)
    remote = call(path + '/intake', 'POST', dict(revision=remote['revision'], text='帮我弄一下那个事'), token)
    assert remote['candidates'] == [] and remote['routePending']
    call(path, 'DELETE', dict(revision=remote['revision']), token)
    for user in state['users']:
        token = call('/users/login', 'POST', {k: user[k] for k in ('username','password')})['token']
        call('/users/' + str(user['id']), 'DELETE', token=token)
    state_file.unlink()
    print(json.dumps({'phase': 'verify', 'backendRestartRestoredExactSnapshot': True, 'draftCount': 4, 'restoreInvalidatedConfirmation': True, 'noCrossDomainFields': True, 'mixedRequiresChoice': True, 'unknownNotAutoOther': True, 'disposableFixturesCleaned': True}))

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('phase', choices=['seed','verify'])
    parser.add_argument('--state', type=Path, default=Path('/tmp/campus-m2-step2-smoke-state.json'))
    args = parser.parse_args()
    (seed if args.phase == 'seed' else verify)(args.state)
