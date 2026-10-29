#!/usr/bin/env python3
"""Bounded capacity drill in the campus-m3-capacity[-suffix] project family ONLY. Internal network, fake model key, no paid calls.
The runner preserves samples, partial failures, accounts and database; down never removes volumes.
"""
import argparse
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timedelta
import hashlib
import json
import os
import re
from pathlib import Path
import secrets
import subprocess
import threading
import time
import urllib.error
import urllib.request
import uuid
from zoneinfo import ZoneInfo
import release
from capacity_metrics import distribution, loopback, StreamResult

ROOT=release.ROOT;PROJECT='campus-m3-capacity';PORT=18084
BASE=loopback('http://127.0.0.1:18084');PROVIDER=loopback('http://127.0.0.1:18766')
FIXTURE_IMAGE='public.ecr.aws/docker/library/python@sha256:8bcfa47b04b853a17aaf909362b847b2112fd6ec4105ea553ef9a43bafbb5916'
DIRECTORY=release.folder(PROJECT);PRIVATE=DIRECTORY/'capacity-fixtures.json'
OUT=ROOT/'docs/M3/evidence/05-capacity';REPORT=OUT/'report.json'
HTTP=urllib.request.build_opener(urllib.request.ProxyHandler({})) # No proxy for local endpoints.
WRITE_LOCK=threading.Lock()
def now():return datetime.now(ZoneInfo('Asia/Shanghai')).isoformat()
def compose(args,**kw):
    state=release.load(PROJECT);env=os.environ.copy()
    env.update(CAMPUS_IMAGE=state['currentImage'],CAMPUS_MYSQL_IMAGE=state['mysqlImage'],CAMPUS_PORT=str(PORT),
        CAMPUS_SECRETS_DIR=str(DIRECTORY/'secrets'),CAMPUS_FIXTURE_IMAGE=FIXTURE_IMAGE)
    return subprocess.run(['docker','compose','--env-file','/dev/null','-p',PROJECT,
        '-f',str(ROOT/'deploy/compose.yml'),'-f',str(ROOT/'deploy/capacity.compose.yml'),*args],env=env,check=True,**kw)
def cid(service):return compose(['ps','-q',service],capture_output=True,text=True).stdout.strip()
def sql(query):
    return subprocess.check_output(['docker','exec',cid('database'),'sh','-c',
        'MYSQL_PWD="$(cat /run/secrets/DB_PASSWORD)" exec mysql -N -u campus_release campus_release -e "$1"','query',query],text=True).strip()
def request(url,method='GET',body=None,token=None,trace=None,timeout=9):
    headers={'Content-Type':'application/json'}
    if token:headers['Authorization']='Bearer '+token
    if trace:headers['X-Campus-Trace-Id']=trace
    req=urllib.request.Request(url,data=None if body is None else json.dumps(body,ensure_ascii=False).encode(),method=method,headers=headers)
    try:return HTTP.open(req,timeout=timeout)
    except urllib.error.HTTPError as error:return error

def api(path,method='GET',body=None,token=None,expected=200):
    with request(BASE+'/api'+path,method,body,token) as response:
        status=response.status;value=json.loads(response.read())
    assert status==expected,(path,status,expected)
    assert value['code']==expected,(path,value.get('code'))
    return value.get('data')
def provider(mode=None):
    with request(PROVIDER+('/control' if mode else '/stats'),'POST' if mode else 'GET',None if mode is None else {'mode':mode}) as r:
        assert r.status==200;return json.loads(r.read())
def wait_until(fn,limit=7):
    end=time.monotonic()+limit
    while time.monotonic()<end:
        value=fn()
        if value:return value
        time.sleep(.05)
    raise AssertionError('Bounded condition did not settle; no automatic upstream retry')
def persist(report):
    OUT.mkdir(parents=True,exist_ok=True)
    (OUT/'report.tmp').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    (OUT/'report.tmp').replace(REPORT)
def raw(value):
    with WRITE_LOCK:
        OUT.mkdir(parents=True,exist_ok=True)
        with (OUT/'samples.jsonl').open('a') as f:f.write(json.dumps(value,ensure_ascii=False)+'\n')
def session(token,category='STUDY'):
    s=api('/need-sessions','POST',token=token)
    return api('/need-sessions/'+str(s['id'])+'/category','PUT',{'revision':s['revision'],'category':category},token)
def prepare_stream(token,category='STUDY'):
    return {'token':token,'session':session(token,category),'traceId':str(uuid.uuid4())}
def streaming(job,label,close_early=False):
    trace=job['traceId'];s=job['session'];start=time.monotonic();result=StreamResult()
    with request(BASE+'/api/need-sessions/'+str(s['id'])+'/ai-stream','POST',
        {'revision':s['revision'],'text':'本地合成容量实验：'+('想借1个转接器。' if s['activeCategory']=='BORROW' else '为什么y=-x的导数是-1？')},job['token'],trace) as response:
        status=response.status
        if status==200:
            while line:=response.readline():
                result.line(line.decode('utf-8'),round((time.monotonic()-start)*1000,3))
                if close_early and result.first_text_ms is not None:break
        else:response.read() # Do not log response bodies or auth data.
    record=result.record(status,round((time.monotonic()-start)*1000,3))
    record.update(at=now(),label=label,traceId=trace,sessionId=s['id'])
    raw(record);return record

def ledger(job):
    def finished():
        rows=api('/need-sessions/ai-budget',token=job['token'])['recent']
        return next((r for r in rows if r['traceId']==job['traceId'] and r['state']!='RESERVED'),None)
    return wait_until(finished,7)
def healthy(job,label):
    row=streaming(job,label);assert row['success'],(label,row)
    stored=api('/need-sessions/'+str(job['session']['id']),token=job['token'])
    assert [m['role'] for m in stored['messages']]==['USER','ASSISTANT']
    return row

def initialize():
    release.init(PROJECT,PORT)
    state=release.load(PROJECT)
    manifest=json.loads((ROOT/'deploy/build/manifest.json').read_text())
    state.update(currentImage=manifest['image'],fixtureImage=FIXTURE_IMAGE)
    release.save(DIRECTORY/'state.json',state)
    key=DIRECTORY/'secrets/DEEPSEEK_API_KEY';key.chmod(0o600);key.write_text('fixture-only-key');key.chmod(0o444)
    print('Initialized isolated capacity fixtures; real model credentials were not loaded.')

def ready():
    try:
        with request(BASE+'/actuator/health/readiness',timeout=2) as r:return r.status==200 and json.loads(r.read())=={'status':'UP'}
    except OSError:return False
def guard():
    assert (DIRECTORY/'secrets/DEEPSEEK_API_KEY').read_text()=='fixture-only-key'
    config=json.loads(subprocess.check_output(['docker','inspect',cid('backend')],text=True))[0]
    env=dict(x.split('=',1) for x in config['Config']['Env'] if '=' in x)
    assert env.get('DEEPSEEK_BASE_URL')=='http://fixture:18766'
    assert env.get('MODEL_PRICE_VERSION')=='LOCAL-FIXTURE-NO-REAL-CHARGE'
    relay=json.loads(subprocess.check_output(['docker','inspect',cid('relay')],text=True))[0]
    assert relay['State']['Running']
    for port in ('18084/tcp','18766/tcp'):
        assert relay['HostConfig']['PortBindings'][port]==[{'HostIp':'127.0.0.1','HostPort':port.split('/')[0]}]
    net=json.loads(subprocess.check_output(['docker','network','inspect',PROJECT+'_default'],text=True))[0]
    assert net['Internal'] is True
    assert list(config['NetworkSettings']['Networks'])==[PROJECT+'_default']
    assert not any(config['NetworkSettings']['Ports'].values())
    assert config['HostConfig']['Memory']==1073741824 and config['HostConfig']['NanoCpus']==2000000000
    assert ready()
    return {'backendImage':config['Image'],'fixtureImage':FIXTURE_IMAGE,'networkInternal':True,'modelKey':'fake fixture only',
        'backendMemoryBytes':config['HostConfig']['Memory'],'backendNanoCpus':config['HostConfig']['NanoCpus'],
        'databaseMemoryBytes':536870912,'databaseCpuLimit':1,'modelConcurrency':4,'perUserConcurrency':1,
        'testDeadlineMs':5000,'normalDeadlineMs':45000,'testCircuitCooldownMs':1000,'normalCircuitCooldownMs':30000,
        'sourceCommit':subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip(),
        'fixtureSourceSha256':hashlib.sha256((ROOT/'scripts/capacity_fixture.py').read_bytes()).hexdigest(),
        'ingress':'fixed loopback relay; no TLS, proxy buffering or native rendering measured'}

def accounts():
    state=json.loads(PRIVATE.read_text()) if PRIVATE.exists() else {'users':[]}
    if not state['users']:assert sql('SELECT COUNT(*) FROM users')=='0','Never reseed another database'
    while len(state['users'])<10:
        user={'username':'capacity_'+secrets.token_hex(5),'password':secrets.token_urlsafe(12)}
        user['id']=api('/users/register','POST',user)['user']['id'];state['users'].append(user);release.save(PRIVATE,state)
    # Synthetic operator in this isolated database ONLY; no developer/production user role changes.
    sql('UPDATE users SET role=\'ADMIN\' WHERE id='+str(int(state['users'][9]['id'])))
    return [api('/users/login','POST',{k:u[k] for k in ('username','password')})['token'] for u in state['users']]

class Sampler:
    def __init__(self):self.stop=threading.Event();self.rows=[];self.thread=None
    def sample(self):
        backend=cid('backend')
        while not self.stop.is_set():
            try:
                data=subprocess.check_output(['docker','stats','--no-stream','--format','{{json .}}',backend],text=True,timeout=7)
                value=json.loads(data);self.rows.append({'at':now(),'cpuPercent':value['CPUPerc'],'memory':value['MemUsage'],'pids':value['PIDs']})
            except (subprocess.SubprocessError,ValueError):self.rows.append({'at':now(),'sampleUnavailable':True})
            self.stop.wait(.5)
    def start(self):self.thread=threading.Thread(target=self.sample,daemon=True);self.thread.start()
    def close(self):self.stop.set();self.thread.join(8)

def warm(tokens):
    rows=[healthy(prepare_stream(tokens[8]),'warmup') for _ in range(3)]
    return {'excludedFromSteadyStatistics':True,'samples':rows}
def steady(tokens):
    results=[]
    for concurrency,count,offset in [(1,12,0),(2,16,1),(4,24,3)]:
        jobs=[prepare_stream(tokens[offset+i%concurrency]) for i in range(count)]
        rows=[];start=time.monotonic()
        with ThreadPoolExecutor(max_workers=concurrency) as pool:
            for i in range(0,count,concurrency):
                rows.extend(list(pool.map(lambda job:healthy(job,'steady-'+str(concurrency)),jobs[i:i+concurrency])))
        elapsed=time.monotonic()-start
        first=distribution([r['firstVisibleTextMs'] for r in rows]);complete=distribution([r['durationMs'] for r in rows])
        # Wall includes post-stream persistence GET validation; request distributions time only the SSE exchange.
        result={'concurrency':concurrency,'samples':count,'wallSecondsIncludingValidation':round(elapsed,4),
            'validatedOperationsPerSecond':round(count/elapsed,3),'firstVisibleText':first,'completion':complete,
            'successCount':sum(r['success'] for r in rows),'failureCount':sum(not r['success'] for r in rows)}
        result['localTargetPassed']=first['p95Ms']<1000 and complete['p95Ms']<3000 and result['failureCount']==0
        results.append(result)
    return results

def duplicate_cancel(tokens):
    task=api('/demands','POST',{'title':'隔离容量实验','description':'合成测试，不是真实校园委托','category':'BORROW',
        'location':'测试地点','reward':1,'expectedTime':(datetime.now()+timedelta(days=2)).isoformat(timespec='minutes')},tokens[8])
    helper=tokens[7]
    accepted=api('/trusted-tools/prepare','POST',{'action':'ACCEPT','requestKey':'capacity_'+secrets.token_hex(8),'demandId':task['id']},helper)
    assert api('/trusted-tools/'+accepted['id']+'/execute','POST',{'confirmed':True},helper)['state']=='SUCCEEDED'
    cancel=api('/trusted-tools/prepare','POST',{'action':'CANCEL','requestKey':'capacity_'+secrets.token_hex(8),
        'demandId':task['id'],'reason':'隔离实验结束'},helper)
    helper_id=json.loads(PRIVATE.read_text())['users'][7]['id']
    before=int(sql('SELECT credit_score FROM users WHERE id='+str(helper_id)))
    def execute(i):
        start=time.monotonic();r=api('/trusted-tools/'+cancel['id']+'/execute','POST',{'confirmed':True},helper)
        row={'at':now(),'label':'duplicate-cancel','sequence':i,'durationMs':round((time.monotonic()-start)*1000,3),
            'success':r['state']=='SUCCEEDED','taskId':r['result']['id']};raw(row);return row
    with ThreadPoolExecutor(max_workers=8) as pool:rows=list(pool.map(execute,range(48)))
    after=int(sql('SELECT credit_score FROM users WHERE id='+str(helper_id)))
    successes=int(sql("SELECT COUNT(*) FROM tool_audits WHERE intent_id='"+cancel['id']+"' AND outcome='SUCCEEDED'"))
    assert after-before==-5 and successes==1 and len(api('/demands'))==1
    assert all(r['success'] and r['taskId']==task['id'] for r in rows)
    assert api('/demands/'+str(task['id']),token=helper)['status']=='CANCELLED'
    api('/trusted-tools/'+cancel['id']+'/execute','POST',{'confirmed':True},tokens[6],expected=404)
    state=json.loads(PRIVATE.read_text());state.update(taskId=task['id'],cancelId=cancel['id'],helperCredit=after);release.save(PRIVATE,state)
    return {'parallelism':8,'requests':48,'completion':distribution([r['durationMs'] for r in rows]),'successCount':48,
        'singleSucceededAudit':successes,'helperCreditDelta':after-before,'taskCount':1,'foreignOwnerHttpStatus':404}

def saturation(tokens):
    jobs=[prepare_stream(t) for t in tokens[:8]];form=session(tokens[9]);provider('hold')
    before=provider()['requests']
    with ThreadPoolExecutor(max_workers=4) as pool:
        futures=[pool.submit(streaming,job,'held-stream') for job in jobs[:4]]
        try:
            wait_until(lambda:provider()['active']==4,3)
            with ThreadPoolExecutor(max_workers=4) as excess:
                denied=list(excess.map(lambda j:streaming(j,'overload-rejection'),jobs[4:]))
            assert all(r['httpStatus']==429 for r in denied)
            assert provider()['requests']==before+4
            for job in jobs[4:]:assert api('/need-sessions/'+str(job['session']['id']),token=job['token'])['messages']==[]
            health=[]
            for _ in range(20):
                start=time.monotonic();assert ready();health.append(round((time.monotonic()-start)*1000,3))
            start=time.monotonic()
            form=api('/need-sessions/'+str(form['id'])+'/draft','PUT',{'revision':form['revision'],
                'fields':{'context':'模型满载时，仍可手动填写当前问题。'},'contexts':[]},tokens[9])
            form_ms=round((time.monotonic()-start)*1000,3);assert form['drafts'][0]['assessment']['ready']
            tools=duplicate_cancel(tokens)
        finally:provider('healthy')
        held=[f.result(timeout=8) for f in futures]
    assert all(r['success'] for r in held),'Held streams timed out; do not call saturation an all-success case'
    return {'acceptedStreams':4,'explicit429':4,'rejectionLatency':distribution([r['durationMs'] for r in denied]),
        'noRejectedUserMessages':True,'upstreamCalls':4,'healthWhileFull':distribution(health),'formWhileFullMs':form_ms,
        'toolsWhileFull':tools,'heldStreamCompletion':distribution([r['durationMs'] for r in held])}

def faults(tokens):
    token=tokens[8];timeline=[]
    def one(mode,label,expected=None):
        provider(mode);before=provider()['requests'];job=prepare_stream(token);r=streaming(job,label);row=ledger(job)
        assert provider()['requests']==before+1,'No automatic retry expected'
        if expected:assert r['failureKind']==expected and not r['success'],r
        r.update(ledgerState=row['state'],usageKnown=row['promptTokens'] is not None,estimatedUsd=row['estimatedUsd'])
        timeline.append(r);return job,r,row
    # The same user has only one permitted request even with a different session.
    provider('hold');job=prepare_stream(token);other=prepare_stream(token)
    with ThreadPoolExecutor(max_workers=1) as pool:
        pending=pool.submit(streaming,job,'one-per-user-held')
        wait_until(lambda:provider()['active']==1,3)
        denied=streaming(other,'one-per-user-rejection');assert denied['httpStatus']==429
        api('/need-sessions/'+str(job['session']['id'])+'/ai-cancel','POST',token=token)
        ledger_row=ledger(job);assert ledger_row['state']=='CANCELLED'
        provider('healthy');pending.result(timeout=7)
    timeline.append({'case':'one-per-user-and-explicit-cancel','rejectionMs':denied['durationMs'],'ledgerState':ledger_row['state']})
    wait_until(lambda:provider()['active']==0,3)
    healthy(prepare_stream(token),'after-explicit-cancel')
    for i in range(3):
        job,r,row=one('stall','timeout-'+str(i+1),'TIMEOUT')
        assert r['durationMs']<6500 and row['state']=='FAILED'
        assert len(api('/need-sessions/'+str(job['session']['id']),token=token)['messages'])==1
    before=provider()['requests'];denied=streaming(prepare_stream(token),'circuit-open')
    assert denied['httpStatus']==503 and provider()['requests']==before
    timeline.append({'case':'circuit-open','httpStatus':503,'upstreamCalls':0,'durationMs':denied['durationMs']})
    provider('healthy');time.sleep(1.1);healthy(prepare_stream(token),'after-circuit-cooldown')
    one('error429','provider429','UPSTREAM_RATE_LIMIT')
    provider('healthy');healthy(prepare_stream(token),'reset-breaker-after429')
    one('drop','provider-body-drop','INVALID_RESPONSE')
    provider('healthy');healthy(prepare_stream(token),'after-body-drop')
    # Close only the client HTTP stream, without the explicit cancel API.
    job=prepare_stream(token);start=time.monotonic();streaming(job,'client-disconnect',True)
    settled=ledger(job)
    assert settled['state'] in ('CANCELLED','FAILED')
    timeline.append({'case':'client-disconnect-settled','durationMs':round((time.monotonic()-start)*1000,3),
        'ledgerState':settled['state'],'failureKind':settled['failureKind']})
    healthy(prepare_stream(token),'after-client-disconnect')
    job,r,row=one('missing_usage','missing-usage')
    assert r['success'] and row['estimatedUsd'] is None and row['estimateBasis']=='USAGE_UNKNOWN' and row['reservedUsd']>0
    provider('healthy')
    recovery=[prepare_stream(t) for t in tokens[3:7]]
    with ThreadPoolExecutor(max_workers=4) as pool:rows=list(pool.map(lambda j:healthy(j,'four-slots-recovered'),recovery))
    assert all(r['success'] for r in rows)
    return {'timeline':timeline,'allFourSlotsRecovered':True,'unknownUsageNotFree':True,'automaticRetries':0}

def presentation(tokens):
    provider('healthy');rows=[]
    for category in ('STUDY','BORROW'):
        job=prepare_stream(tokens[9],category);r=healthy(job,'presentation-'+category)
        row=ledger(job);r.update(category=category,fixtureReportedCompletionUnits=row['completionTokens'])
        if category=='BORROW':assert r['proposals']==1 and r['events'].get('text_delta')==1
        rows.append(r)
    return {'scope':'HTTP first visible text; not native render time or provider first token','samples':rows,
        'fixtureTokensAreSynthetic':True,'notRealModelGenerationThroughput':True}

def restart(tokens):
    state=json.loads(PRIVATE.read_text());helper=tokens[7];before=api('/need-sessions/ai-budget',token=helper)['calls']
    subprocess.run(['docker','restart',cid('backend')],check=True,stdout=subprocess.DEVNULL)
    wait_until(lambda:ready(),65)
    assert api('/need-sessions/ai-budget',token=helper)['calls']==before
    replay=api('/trusted-tools/'+state['cancelId']+'/execute','POST',{'confirmed':True},helper)
    assert replay['state']=='SUCCEEDED' and int(sql('SELECT credit_score FROM users WHERE id='+str(state['users'][7]['id'])))==state['helperCredit']
    healthy(prepare_stream(tokens[9]),'after-process-restart')
    return {'sameJwtStillValid':True,'budgetRetained':True,'receiptReplayRetained':True,'creditUnchanged':True,'modelUsable':True}

def run(resume):
    if REPORT.exists():
        report=json.loads(REPORT.read_text())
        assert report['status']!='COMPLETE','Completed evidence is immutable; no automatic rerun'
        assert resume,'Partial report exists; inspect it before using --resume'
    guard_info=guard()
    if not REPORT.exists():report={'at':now(),'status':'RUNNING','scope':'single-instance local Docker; fake provider only','paidModelCalls':0,
        'environment':guard_info,'stages':{},'attemptFailures':[],'targets':{'steadyFirstVisibleP95Ms':1000,'steadyCompletionP95Ms':3000,
        'steadyFailureCount':0,'overloadRejectP95Ms':1000,'testFaultCompletionMs':6500,'healthWhileFullP95Ms':1000}}
    report['status']='RUNNING'
    persist(report)
    try:tokens=accounts()
    except Exception as error:
        report['attemptFailures'].append({'at':now(),'stage':'account-setup','exception':type(error).__name__,'detail':str(error)[:600]});report['status']='FAILED';persist(report);raise
    provider('healthy');wait_until(lambda:provider()['active']==0,9)
    sampler=Sampler();sampler.start()
    try:
        for name,fn in [('warmup',warm),('steady',steady),('saturation',saturation),('faults',faults),('presentation',presentation),('restart',restart)]:
            if name in report['stages']:continue
            report['currentStage']=name;persist(report);print('Running',name,flush=True)
            try:value=fn(tokens)
            except Exception as error:
                report['attemptFailures'].append({'at':now(),'stage':name,'exception':type(error).__name__,'detail':str(error)[:600]})
                report['status']='FAILED';persist(report);raise
            report['stages'][name]=value
            if name=='presentation':report['modelObservationsBeforeRestart']=api('/ops/model-observations',token=tokens[9])
            persist(report)
        report['providerStats']=provider()
        obs=api('/ops/model-observations',token=tokens[9]);report['modelObservations']=obs
        # All usage values here originate from this fixture, never a real tokenizer or bill.
        report['costEvidence']={'usageOrigin':'synthetic fixture counters','actualProviderChargeUsd':0,
            'reservationPerDispatchUsd':'0.02158080','pricedSnapshot':'historical configured 20261024; not asserted as current pricing',
            'aggregateReservedUsd':sql('SELECT SUM(reserved_usd) FROM model_invocations'),
            'aggregateEstimatedUsd':sql('SELECT SUM(estimated_usd) FROM model_invocations'),
            'usageUnknownRows':int(sql('SELECT COUNT(*) FROM model_invocations WHERE estimated_usd IS NULL')),
            'dispatches':int(sql('SELECT COUNT(*) FROM model_invocations'))}
        report['stages']['steadyTargetsPassed']=all(r['localTargetPassed'] for r in report['stages']['steady'])
        assert report['stages']['steadyTargetsPassed'],'Steady target failed; report actual failure instead of altering target'
        assert report['stages']['saturation']['rejectionLatency']['p95Ms']<1000
        assert report['stages']['saturation']['healthWhileFull']['p95Ms']<1000
        report['status']='COMPLETE';report['finishedAt']=now();report.pop('currentStage',None)
    except Exception as error:
        if report['status']!='FAILED':
            report['attemptFailures'].append({'at':now(),'stage':report.get('currentStage','final-target-check'),'exception':type(error).__name__,'detail':str(error)[:600]})
        report['status']='FAILED';raise
    finally:
        sampler.close();report.setdefault('resourceSamples',[]).extend(sampler.rows);persist(report)
    print('Bounded drill complete. Raw samples and partial failures preserved; no real provider calls.',flush=True)

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('phase',choices=['init','up','run','down']);p.add_argument('--resume',action='store_true');p.add_argument('--project',default=PROJECT);p.add_argument('--output',type=Path,default=OUT);a=p.parse_args()
    if not re.fullmatch(r'campus-m3-capacity(?:-[a-z0-9]{1,12})?',a.project):raise SystemExit('Only named capacity drill projects are accepted')
    PROJECT=a.project;DIRECTORY=release.folder(PROJECT);PRIVATE=DIRECTORY/'capacity-fixtures.json';OUT=a.output.resolve();REPORT=OUT/'report.json'
    if a.phase=='init':initialize()
    elif a.phase=='up':compose(['up','-d','--wait','--wait-timeout','150'])
    elif a.phase=='down':compose(['down'])
    else:run(a.resume)
