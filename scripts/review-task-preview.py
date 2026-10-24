#!/usr/bin/env python3
"""Explicit local fixtures for the read-only Cangjie bridge. No model calls; secrets only in /tmp."""
import argparse,json,os,secrets,subprocess,sys,uuid
from pathlib import Path
from datetime import datetime,timedelta
import urllib.request,urllib.error
from private_env import read_env
ROOT=Path(__file__).resolve().parents[1];STATE=Path('/tmp/campus-m3-task-preview-private.json');OUT=ROOT/'docs/M3/evidence/02-live-preview.json'
BINARY=ROOT/'cangjie/task-preview/build/task-preview'
TRACE=str(uuid.uuid4())
def request(path,method='GET',body=None,token=None,expected=200):
 headers={'Content-Type':'application/json','X-Campus-Trace-Id':TRACE}
 if token:headers['Authorization']='Bearer '+token
 req=urllib.request.Request('http://127.0.0.1:8081/api'+path,data=None if body is None else json.dumps(body).encode(),headers=headers,method=method)
 try:response=urllib.request.urlopen(req,timeout=8)
 except urllib.error.HTTPError as e:response=e
 with response:
  status=response.status;data=json.loads(response.read());span=response.headers.get('X-Request-Id')
 assert status==expected,(path,status)
 assert data['code']==expected,(path,data['code'])
 return data.get('data'),span

def save(state):
 fd=os.open(STATE,os.O_WRONLY|os.O_CREAT|os.O_TRUNC,0o600)
 with os.fdopen(fd,'w') as f:json.dump(state,f)
 os.chmod(STATE,0o600)
def login(user):return request('/users/login','POST',{k:user[k] for k in ['username','password']})[0]['token']
def db_snapshot(task_id):
 config=read_env(ROOT/'.env.m2.local');assert ':3307/campus_m2_dev' in config['DB_URL'];task_id=int(task_id)
 query=f'SELECT status,row_version,IFNULL(acceptor_id,0) FROM demands WHERE id={task_id};'
 result=subprocess.run(['/usr/local/mysql/bin/mysql','--host=127.0.0.1','--port=3307','--user='+config['DB_USERNAME'],'--batch','--skip-column-names','campus_m2_dev'],input=query,text=True,capture_output=True,check=True,env=dict(os.environ,MYSQL_PWD=config['DB_PASSWORD']))
 fields=result.stdout.strip().split('\t');return {'status':fields[0],'rowVersion':int(fields[1]),'acceptorId':int(fields[2])}

def setup(outage):
 assert not STATE.exists(),'Private fixture already exists. Use check-ui/cleanup; do not reseed silently.'
 config=read_env(ROOT/'.env.m2.local');assert ':3307/campus_m2_dev' in config['DB_URL']
 state={'users':[],'createdAt':datetime.now().astimezone().isoformat()};save(state)
 for role in ['owner','helper']:
  user={'username':'m3_preview_'+role+'_'+secrets.token_hex(3),'password':secrets.token_urlsafe(12)}
  response,_=request('/users/register','POST',user);user['id']=response['user']['id'];state['users'].append(user);save(state)
 owner,helper=map(login,state['users'])
 configured,_=request('/task-transition-preview/status',token=owner);assert configured['configured'] is True
 task,_=request('/demands','POST',{'title':'M3-仓颉状态预览测试','description':'本地fixture，只测试状态预测；不是校园真实委托。','category':'STUDY','location':'测试线上','reward':1,'expectedTime':(datetime.now()+timedelta(days=2)).isoformat(timespec='minutes')},owner)
 state['taskId']=task['id'];save(state);before=db_snapshot(task['id'])
 projections=[]
 for token,event,role,next_status,outcome in [(helper,'ACCEPT','OTHER','IN_PROGRESS','APPLIED'),(owner,'CANCEL','PUBLISHER','CANCELLED','APPLIED'),(owner,'ACCEPT','PUBLISHER','PENDING','STATE_OR_ROLE_REJECTED')]:
  result,span=request('/task-transition-preview','POST',{'demandId':task['id'],'event':event},token)
  assert result['participant']==role and result['engine']=='Cangjie 1.2.0 / transition-v1'
  assert result['decision']['status']==next_status and result['decision']['outcome']==outcome
  assert db_snapshot(task['id'])==before
  projections.append({'event':event,'role':role,'requestId':span,'result':result,'databaseUnchanged':True})
 report={'at':datetime.now().astimezone().isoformat(),'traceId':TRACE,'fixtureTaskId':task['id'],'fixtureSetup':'Legacy task-create API, explicitly test data; not an AI publication flow','sourceDatabase':before,'projections':projections,'paidModelCalls':0}
 if outage:
  offline=BINARY.with_name('task-preview.offline');assert not offline.exists()
  BINARY.rename(offline)
  try:
   request('/task-transition-preview','POST',{'demandId':task['id'],'event':'ACCEPT'},helper,503)
   receipt,_=request('/trusted-tools/prepare','POST',{'action':'ACCEPT','requestKey':'m3_offline_'+uuid.uuid4().hex,'demandId':task['id']},helper)
   revoked,_=request('/trusted-tools/'+receipt['id']+'/revoke','POST',token=helper)
   assert revoked['state']=='REVOKED' and db_snapshot(task['id'])==before
   report['componentOutage']={'previewStatus':503,'normalToolPrepare':'PREPARED','revoke':'REVOKED','databaseTaskUnchanged':True}
  finally:offline.rename(BINARY)
  request('/task-transition-preview','POST',{'demandId':task['id'],'event':'ACCEPT'},helper)
  report['componentRestored']=True
 OUT.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n');print('Real Cangjie HTTP previews passed; task unchanged; no model calls. Private fixture retained for UI review.')

def check_ui():
 state=json.loads(STATE.read_text());snapshot=db_snapshot(state['taskId']);assert snapshot['status']=='PENDING' and snapshot['rowVersion']==0 and snapshot['acceptorId']==0
 report=json.loads(OUT.read_text());report['afterUiPreview']=snapshot;report['uiReviewedAt']=datetime.now().astimezone().isoformat();OUT.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
 print('After UI preview, real task remains PENDING / version 0 / no acceptor.')
def cleanup():
 state=json.loads(STATE.read_text());owner=login(state['users'][0]);task_id=state['taskId'];before=db_snapshot(task_id)
 if before['status']=='PENDING':
  receipt,_=request('/trusted-tools/prepare','POST',{'action':'CANCEL','requestKey':'m3_end_'+uuid.uuid4().hex,'demandId':task_id,'reason':'本地预览测试结束'},owner)
  request('/trusted-tools/'+receipt['id']+'/execute','POST',{'confirmed':True},owner)
 after=db_snapshot(task_id);assert after['status']=='CANCELLED'
 report=json.loads(OUT.read_text());report['cleanup']={'method':'separate explicitly confirmed trusted CANCEL, after preview checks','database':after};OUT.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
 state['finishedAt']=datetime.now().astimezone().isoformat();save(state);print('Own fixture cancelled through a separate confirmed tool; historical audit retained.')
if __name__=='__main__':
 parser=argparse.ArgumentParser();parser.add_argument('phase',choices=['setup','check-ui','cleanup']);parser.add_argument('--exercise-outage',action='store_true');args=parser.parse_args()
 if args.phase=='setup':setup(args.exercise_outage)
 elif args.phase=='check-ui':check_ui()
 else:cleanup()
