#!/usr/bin/env python3
"""Local fake-provider drills only; refuses a backend without the explicit fixture price marker."""
import json,os,secrets,sys,time,uuid,argparse
from datetime import datetime
from pathlib import Path
import urllib.request,urllib.error
from private_env import read_env
ROOT=Path(__file__).resolve().parents[1];STATE=Path('/tmp/campus-m3-reliability-private.json');OUT=ROOT/'docs/M3/evidence/03-local-faults.json';TRACE=str(uuid.uuid4())
def save(state):
 fd=os.open(STATE,os.O_WRONLY|os.O_CREAT|os.O_TRUNC,0o600)
 with os.fdopen(fd,'w') as out:json.dump(state,out)
def request(path,method='GET',body=None,token=None,expected=200):
 headers={'Content-Type':'application/json','X-Campus-Trace-Id':TRACE}
 if token:headers['Authorization']='Bearer '+token
 req=urllib.request.Request('http://127.0.0.1:8081/api'+path,data=None if body is None else json.dumps(body).encode(),headers=headers,method=method)
 try:response=urllib.request.urlopen(req,timeout=8)
 except urllib.error.HTTPError as error:response=error
 with response:status=response.status;data=json.loads(response.read())
 assert status==expected,(path,status);assert data['code']==expected,(path,data['code']);return data.get('data')
def fixture(mode=None):
 req=urllib.request.Request('http://127.0.0.1:18765/'+('control' if mode else 'stats'),data=None if mode is None else json.dumps({'mode':mode}).encode(),headers={'Content-Type':'application/json'},method='GET' if mode is None else 'POST')
 with urllib.request.urlopen(req,timeout=3) as response:return json.loads(response.read())
def login(user):return request('/users/login','POST',{k:user[k] for k in ['username','password']})['token']
def session(token):
 s=request('/need-sessions','POST',token=token)
 return request('/need-sessions/'+str(s['id'])+'/category','PUT',{'revision':s['revision'],'category':'STUDY'},token)
def start_stream(token,s):
 req=urllib.request.Request('http://127.0.0.1:8081/api/need-sessions/'+str(s['id'])+'/ai-stream',data=json.dumps({'revision':s['revision'],'text':'本地故障测试：为什么y=-x的导数是-1？'}).encode(),headers={'Authorization':'Bearer '+token,'Content-Type':'application/json','Accept':'text/event-stream','X-Campus-Trace-Id':TRACE},method='POST')
 try:return urllib.request.urlopen(req,timeout=8)
 except urllib.error.HTTPError as error:return error
def streamed(token,s,expected=200):
 start=time.monotonic()
 with start_stream(token,s) as response:
  assert response.status==expected,response.status;body=response.read().decode();span=response.headers.get('X-Request-Id')
 return {'sessionId':s['id'],'httpStatus':expected,'durationMs':round((time.monotonic()-start)*1000,2),'body':body,'requestId':span}
def settled(token,calls):
 limit=time.monotonic()+4
 while time.monotonic()<limit:
  b=request('/need-sessions/ai-budget',token=token)
  if b['calls']==calls and all(r['state']!='RESERVED' for r in b['recent']):return b
  time.sleep(.05)
 raise TimeoutError('Model ledger did not settle; no request retried')
def setup():
 assert ':3307/campus_m2_dev' in read_env(ROOT/'.env.m2.local')['DB_URL'];assert not STATE.exists(),'Inspect existing fixture state instead of automatically reseeding'
 state={'users':[],'stage':'setup'};save(state)
 for label in ['owner','other']:
  user={'username':'m3_model_'+label+'_'+secrets.token_hex(3),'password':secrets.token_urlsafe(12)};user['id']=request('/users/register','POST',user)['user']['id'];state['users'].append(user);save(state)
 token=login(state['users'][0]);other=login(state['users'][1]);policy=request('/need-sessions/ai-budget',token=token)
 assert policy['priceVersion']=='LOCAL-FIXTURE-NO-REAL-CHARGE' and policy['callLimit']==6,'Backend must explicitly use fake provider and fixture pricing'
 report={'at':datetime.now().astimezone().isoformat(),'traceId':TRACE,'provider':'127.0.0.1 local fixture; no real DeepSeek request','paidModelCalls':0,'cases':[]}
 task_count=len(request('/demands'));fixture('stall')
 for number in [1,2]:
  s=session(token);r=streamed(token,s);assert 'TIMEOUT' in r['body'] and 'event:done' not in r['body'];assert r['durationMs']<2500
  remote=request('/need-sessions/'+str(s['id']),token=token);assert len(remote['messages'])==1 and remote['messages'][0]['role']=='USER'
  r['case']='timeout-'+str(number);report['cases'].append(r);settled(token,number)
 count=fixture()['requests'];s=session(token);blocked=streamed(token,s,503);assert fixture()['requests']==count
 assert request('/need-sessions/'+str(s['id']),token=token)['messages']==[];blocked['case']='circuit-open-no-upstream';report['cases'].append(blocked)
 fixture('healthy');time.sleep(2.1);r=streamed(token,session(token));assert 'event:done' in r['body'];r['case']='half-open-recovery';report['cases'].append(r);settled(token,3)
 fixture('error429');count=fixture()['requests'];r=streamed(token,session(token));assert 'UPSTREAM_RATE_LIMIT' in r['body'];assert fixture()['requests']==count+1;r['case']='provider-429-no-retry';report['cases'].append(r);settled(token,4)
 fixture('healthy');r=streamed(token,session(token));assert 'event:done' in r['body'];settled(token,5)
 fixture('stall');s=session(token);start=time.monotonic()
 with start_stream(token,s) as response:
  assert response.status==200
  while True:
   line=response.readline()
   assert line,'No text before cancellation'
   if b'event:text_delta' in line:break
  ack=request('/need-sessions/'+str(s['id'])+'/ai-cancel','POST',token=token);assert ack['cancelled'] is True
 b=settled(token,6);assert b['recent'][0]['state']=='CANCELLED' and b['recent'][0]['failureKind']=='CLIENT_CANCELLED'
 report['cases'].append({'case':'explicit-cancel','durationMs':round((time.monotonic()-start)*1000,2),'sessionId':s['id'],'ledgerState':b['recent'][0]['state']})
 count=fixture()['requests'];fixture('healthy');r=streamed(token,session(token));assert '今日模型额度' in r['body'];assert fixture()['requests']==count;r['case']='quota-no-upstream';report['cases'].append(r)
 assert request('/need-sessions/ai-budget',token=token)['calls']==6
 r=streamed(other,session(other));assert 'event:done' in r['body'];report['otherAccountCalls']=settled(other,1)['calls']
 assert len(request('/demands'))==task_count;report['tasksUnchanged']=True
 report['ownerBudgetBeforeRestart']=request('/need-sessions/ai-budget',token=token);report['fakeRequests']=fixture()['requests'];report['cases']=[{k:v for k,v in c.items() if k!='body'}|({'streamEvidence':c['body']} if 'body' in c else {}) for c in report['cases']]
 state['stage']='verified-before-restart';save(state);OUT.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n');print('Timeout, circuit, recovery, cancellation, quota and other-account isolation passed; fake calls only.')
def after_restart():
 state=json.loads(STATE.read_text());token=login(state['users'][0]);b=request('/need-sessions/ai-budget',token=token);assert b['callLimit']==6 and b['calls']==6
 count=fixture()['requests'];r=streamed(token,session(token));assert '今日模型额度' in r['body'] and fixture()['requests']==count
 report=json.loads(OUT.read_text());report['restartQuotaPreserved']={'calls':b['calls'],'callLimit':b['callLimit'],'fakeRequestsUnchanged':True,'streamEvidence':r['body']};OUT.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n');print('Same quota remains exhausted after actual backend restart; no upstream call.')
def restored():
 state=json.loads(STATE.read_text());token=login(state['users'][0]);b=request('/need-sessions/ai-budget',token=token);assert b['calls']==6 and b['callLimit']==20 and b['priceVersion']!='LOCAL-FIXTURE-NO-REAL-CHARGE'
 assert request('/need-sessions/ai-status',token=token)['configured'] is True
 report=json.loads(OUT.read_text());report['normalConfigurationRestored']={'configured':True,'callLimit':b['callLimit'],'previousReservationsRetained':b['calls'],'fixturePricesRemainMarkedOnHistoricalRows':all(r['priceVersion']=='LOCAL-FIXTURE-NO-REAL-CHARGE' for r in b['recent'])};OUT.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n');state['stage']='restored';save(state);print('Normal private provider configuration restored; historical fixture reservations retained and marked.')
if __name__=='__main__':
 parser=argparse.ArgumentParser();parser.add_argument('phase',choices=['setup','after-restart','restored']);args=parser.parse_args()
 {'setup':setup,'after-restart':after_restart,'restored':restored}[args.phase]()
