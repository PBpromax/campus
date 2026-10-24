#!/usr/bin/env python3
"""Explicit local-only smoke in campus_m2_dev. Seed, restart backend, then verify.
--live-action makes exactly one paid DeepSeek request; never retries it automatically.
Credentials stay in a mode-600 state file. Test audit/receipts are deliberately retained.
"""
import argparse
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timedelta
import json
import os
from pathlib import Path
import secrets
import urllib.request
import urllib.error
from private_env import read_env
from importlib.machinery import SourceFileLoader

root = Path(__file__).resolve().parents[1]
assert ':3307/campus_m2_dev' in read_env(root / '.env.m2.local')['DB_URL'], 'Local isolated DB only'
base = 'http://127.0.0.1:8081/api'
samples = SourceFileLoader('draft_smoke', str(root/'scripts/verify-m2-drafts.py')).load_module()

def call(path, method='GET', body=None, token='', expected=200):
    headers={'Content-Type':'application/json'}
    if token: headers['Authorization']='Bearer '+token
    req=urllib.request.Request(base+path, data=None if body is None else json.dumps(body).encode(),headers=headers,method=method)
    try: response=urllib.request.urlopen(req,timeout=20)
    except urllib.error.HTTPError as error: response=error
    with response: status=response.status; payload=response.read()
    assert status==expected, f'{method} {path}: HTTP {status}, expected {expected}'
    if status!=200: return None
    envelope=json.loads(payload)
    assert envelope['code']==200, f'{method} {path}: business error {envelope["code"]}'
    return envelope['data']

def save(path,state):
    path.touch(mode=0o600,exist_ok=True);os.chmod(path,0o600)
    path.write_text(json.dumps(state,ensure_ascii=False))

def key(): return 'smoke_'+secrets.token_hex(12)

def prepare(action, token, **kw):
    return call('/trusted-tools/prepare','POST',dict(action=action,requestKey=key(),**kw),token)

def execute(receipt,token): return call('/trusted-tools/'+receipt['id']+'/execute','POST',{'confirmed':True},token)

def seed(path,live):
    assert not path.exists(), 'Existing smoke state: verify it before reseeding'
    state={'users':[],'sessions':[],'tasks':[],'receipts':[],'liveCalls':0,'seededAt':datetime.now().astimezone().isoformat()}
    save(path,state)
    tokens=[]
    for _ in range(3):
        user={'username':'m2_tool_'+secrets.token_hex(6),'password':secrets.token_urlsafe(12)}
        result=call('/users/register','POST',user)
        user['id']=result['user']['id']; state['users'].append(user);tokens.append(result['token']);save(path,state)
    owner,helper,other=tokens
    # Unknown action and missing explicit confirmation must fail at the HTTP boundary.
    call('/trusted-tools/prepare','POST',{'action':'SQL','requestKey':key()},owner,400)
    call('/trusted-tools',token='',expected=403)
    day=(datetime.now()+timedelta(days=7)).date().isoformat()
    for category,values in samples.fields().items():
        values={k:(day+v[10:] if v.startswith('2026-11-01T') else v) for k,v in values.items()}
        session=call('/need-sessions','POST',token=owner);state['sessions'].append(session['id']);save(path,state)
        endpoint='/need-sessions/'+str(session['id'])
        session=call(endpoint+'/category','PUT',{'revision':session['revision'],'category':category},owner)
        session=call(endpoint+'/draft','PUT',{'revision':session['revision'],'fields':values,'contexts':[]},owner)
        if live and category=='STUDY':
            before=call('/trusted-tools/tasks?scope=mine',token=owner)
            request=urllib.request.Request(base+endpoint+'/ai-stream', data=json.dumps({'revision':session['revision'],
                'text':'请发布这个学习求助任务，先给我操作建议，具体发布信息由我填写并确认执行。'}).encode(),
                headers={'Authorization':'Bearer '+owner,'Content-Type':'application/json'},method='POST')
            state['liveCalls']+=1;save(path,state)
            with urllib.request.urlopen(request,timeout=70) as response: raw=response.read().decode()
            events=[];hint=None
            for frame in raw.replace('\r\n','\n').split('\n\n'):
                kind='';data=[]
                for line in frame.splitlines():
                    if line.startswith('event:'):kind=line[6:].strip()
                    if line.startswith('data:'):data.append(line[5:].strip())
                if kind: events.append(kind)
                if kind=='action_hint':hint=json.loads('\n'.join(data))
            assert 'done' in events and 'failure' not in events
            assert hint and hint['action']=='PUBLISH' and hint['demandId'] is None, 'Model did not return expected action hint; no automatic retry'
            assert call('/trusted-tools/tasks?scope=mine',token=owner)==before, 'AI must not execute'
            state['aiEvidence']={'events':events,'action':hint['action'],'noTaskWriteByModel':True};save(path,state)
            session=call(endpoint,token=owner)
        session=call(endpoint+'/confirm','POST',{'revision':session['revision']},owner)
        publication={'title':'[M2自动验证] '+category,'reward':5,'location':'测试地点（非真实委托）','expectedTime':(datetime.now()+timedelta(days=30)).isoformat(timespec='seconds')}
        payload=dict(action='PUBLISH',requestKey=key(),sessionId=session['id'],revision=session['revision'],publication=publication)
        prepared=call('/trusted-tools/prepare','POST',payload,owner)
        assert prepared['state']=='PREPARED' and prepared['result'] is None
        assert call('/trusted-tools/prepare','POST',payload,owner)['id']==prepared['id']
        call('/trusted-tools/'+prepared['id'],token=helper,expected=404)
        call('/trusted-tools/'+prepared['id']+'/execute','POST',{'confirmed':False},owner,422)
        assert call('/trusted-tools/'+prepared['id'],token=owner)['state']=='PREPARED'
        result=execute(prepared,owner);task=result['result'];state['tasks'].append(task['id']);state['receipts'].append(result);save(path,state)
        assert task['status']=='PENDING' and task['category']==category
        assert execute(prepared,owner)==result
        payload['requestKey']=key();call('/trusted-tools/prepare','POST',payload,owner,409)
    a,b,c,d=state['tasks']
    pubcancel=execute(prepare('CANCEL',owner,demandId=a),owner)
    assert pubcancel['result']['status']=='CANCELLED'
    accepted=execute(prepare('ACCEPT',helper,demandId=b),helper)
    assert accepted['result']['status']=='IN_PROGRESS'
    call('/trusted-tools/prepare','POST',dict(action='CANCEL',requestKey=key(),demandId=b),helper,422)
    cancel=prepare('CANCEL',helper,demandId=b,reason='自动验证结束，撤销测试协助')
    result=execute(cancel,helper);assert result['result']['status']=='CANCELLED'
    score=call('/users/'+str(state['users'][1]['id']),token=helper)['creditScore']
    assert score==95;execute(cancel,helper)
    assert call('/users/'+str(state['users'][1]['id']),token=helper)['creditScore']==score
    contenders=[prepare('ACCEPT',t,demandId=c) for t in [helper,other]]
    def compete(i):
        try: return execute(contenders[i],tokens[i+1])['state']=='SUCCEEDED'
        except AssertionError: return False
    with ThreadPoolExecutor(max_workers=2) as pool: wins=list(pool.map(compete,[0,1]))
    assert sum(wins)==1
    winner=tokens[1 if wins[0] else 2]
    current=call('/demands/'+str(c),token=owner);assert current['status']=='IN_PROGRESS'
    call('/demands/'+str(c)+'/complete','PUT',{'proof':'仅用于自动化测试的完成说明'},winner)
    assert call('/demands/'+str(c)+'/confirm','PUT',token=owner)['status']=='COMPLETED'
    revocable=prepare('ACCEPT',helper,demandId=d)
    assert call('/trusted-tools/'+revocable['id']+'/revoke','POST',token=helper)['state']=='REVOKED'
    call('/trusted-tools/'+revocable['id']+'/execute','POST',{'confirmed':True},helper,409)
    assert call('/demands/'+str(d),token=owner)['status']=='PENDING'
    audits=call('/trusted-tools/audits',token=owner)
    assert sum(a['outcome']=='SUCCEEDED' and a['action']=='PUBLISH' for a in audits)==4
    state['seedEvidence']={'fourDomainsPublished':True,'prepareNoResult':True,'duplicatePrepareSameId':True,
        'duplicateExecutionSameReceipt':True,'sourceVersionDeduplicated':True,'ownership404':True,'missingConfirmation422':True,
        'publisherCancel':True,'acceptorCancelDeductsOnlyOnce':True,'concurrentAcceptSingleWinner':True,
        'legacyCompleteAndConfirm':True,'revokeDoesNotAccept':True,'auditPublishSuccessCount':4}
    save(path,state);print(json.dumps({'phase':'seed',**state['seedEvidence'],'liveCalls':state['liveCalls']}))

def verify(path,report):
    state=json.loads(path.read_text());owner=call('/users/login','POST',{k:state['users'][0][k] for k in ['username','password']})['token']
    for receipt in state['receipts']:
        remote=call('/trusted-tools/'+receipt['id'],token=owner)
        assert remote==receipt, 'Receipt must survive backend restart unchanged'
        assert execute(receipt,owner)==receipt, 'Replay must not write a second task'
    assert len(call('/trusted-tools/tasks?scope=mine',token=owner))==4
    # Keep fixture tasks/receipts/audits for inspection; mark final pending fixture cancelled.
    pending=state['tasks'][-1]
    assert execute(prepare('CANCEL',owner,demandId=pending),owner)['result']['status']=='CANCELLED'
    evidence={'recordedAt':datetime.now().astimezone().isoformat(),'database':'campus_m2_dev','phase':'verified-after-backend-restart',
        **state['seedEvidence'],'persistentReplayAfterRestart':True,'liveCalls':state['liveCalls'],
        'ai':state.get('aiEvidence'),'fixtures':'3 disposable test users and 4 cancelled/completed tasks retained with receipts/audits; credentials remain only in ignored private state file'}
    report.write_text(json.dumps(evidence,ensure_ascii=False,indent=2)+'\n');state['verified']=True;save(path,state)
    print(json.dumps({'phase':'verify','persistentReplayAfterRestart':True,'report':str(report)}))

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('phase',choices=['seed','verify'])
    parser.add_argument('--state',type=Path,default=Path('/tmp/campus-step5-private-state.json'))
    parser.add_argument('--report',type=Path,default=root/'docs/M2/evidence/07-mysql-and-ai-smoke.json')
    parser.add_argument('--live-action',action='store_true');args=parser.parse_args()
    (seed(args.state,args.live_action) if args.phase=='seed' else verify(args.state,args.report))
