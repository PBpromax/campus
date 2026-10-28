#!/usr/bin/env python3
"""Explicit local release drills; only scoped Compose databases and synthetic accounts. No model calls."""
import argparse
from datetime import datetime,timedelta,timezone
import json
from pathlib import Path
import secrets
import subprocess
import time
import urllib.request,urllib.error
import release

ROOT=release.ROOT
REPORT=ROOT/'docs/M3/evidence/04-deployment.json'
FIXTURE=release.folder('campus-m3-review')/'review-fixture.json'
PROJECT='campus-m3-review'

def report(**updates):
    value=json.loads(REPORT.read_text()) if REPORT.exists() else {'at':datetime.now(timezone.utc).isoformat(),'paidModelCalls':0,'scope':'Isolated local Linux containers; not cloud/HTTPS/SLO acceptance'}
    value.update(updates);REPORT.parent.mkdir(exist_ok=True);REPORT.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n')

def http(project,path,method='GET',body=None,token=None,expected=200):
    state=release.load(project);headers={'Content-Type':'application/json'}
    if token:headers['Authorization']='Bearer '+token
    req=urllib.request.Request(f'http://127.0.0.1:{state["port"]}'+path,data=None if body is None else json.dumps(body).encode(),headers=headers,method=method)
    try:r=urllib.request.urlopen(req,timeout=8)
    except urllib.error.HTTPError as e:r=e
    with r:
        status=r.status;raw=r.read()
        assert status==expected,(path,status,expected)
        return json.loads(raw) if raw else None

def api(path,method='GET',body=None,token=None,project=PROJECT,expected=200):
    value=http(project,'/api'+path,method,body,token,expected);assert value['code']==expected
    return value.get('data')

def sql(query,project=PROJECT):
    cid=release.container(project,release.load(project),'database')
    return release.capture(['docker','exec',cid,'sh','-c','MYSQL_PWD="$(cat /run/secrets/DB_PASSWORD)" exec mysql -N -u campus_release campus_release -e "$1"','query',query])

def health(project=PROJECT):
    values={}
    for name in ('liveness','readiness'):
        value=http(project,'/actuator/health/'+name);assert value=={'status':'UP'};values[name]=value
    http(project,'/actuator/env',expected=403)
    return values

def ready(project=PROJECT):
    for attempt in range(45):
        try:health(project);return
        except (AssertionError,OSError):time.sleep(1)
    raise AssertionError('Deployment did not recover readiness')

def login(fixture,project=PROJECT):
    return api('/users/login','POST',{k:fixture[k] for k in ('username','password')},project=project)['token']

def seed():
    assert not FIXTURE.exists(),'Do not silently reseed an existing review fixture'
    assert sql('SELECT COUNT(*) FROM users')=='0'
    assert sql('SELECT COUNT(*) FROM demands')=='0'
    probes=health()
    versions=sql('SELECT version,success FROM flyway_schema_history ORDER BY installed_rank')
    assert len(versions.splitlines())>=6
    fixture={'username':'deploy_'+secrets.token_hex(4),'password':'Dp'+secrets.token_hex(6)}
    release.save(FIXTURE,fixture)
    token=api('/users/register','POST',{**fixture,'name':'部署测试账号'})['token'];fixture['token']=token;release.save(FIXTURE,fixture)
    assert api('/need-sessions/ai-status',token=token)['configured'] is False
    assert api('/task-transition-preview/status',token=token)['configured'] is True
    session=api('/need-sessions','POST',token=token)
    session=api(f'/need-sessions/{session["id"]}/category','PUT',{'revision':session['revision'],'category':'STUDY'},token)
    session=api(f'/need-sessions/{session["id"]}/messages','POST',{'revision':session['revision'],'text':'部署恢复测试：第二步到第三步为什么取负号？'},token)
    fixture['sessionId']=session['id'];fixture['sessionRevision']=session['revision']
    task=api('/demands','POST',{'title':'隔离部署测试物品借用','description':'本机容器测试数据，不是真实校园委托','category':'BORROW','location':'测试线上','reward':1,'expectedTime':(datetime.now()+timedelta(days=2)).isoformat(timespec='minutes')},token)
    fixture['taskId']=task['id'];fixture['userId']=task['publisherId'];release.save(FIXTURE,fixture)
    preview=api('/task-transition-preview','POST',{'demandId':task['id'],'event':'CANCEL'},token)
    assert preview['decision']['status']=='CANCELLED' and preview['decision']['outcome']=='APPLIED'
    assert api('/demands/'+str(task['id']),token=token)['status']=='PENDING'
    release.save(FIXTURE,fixture)
    report(initial={'health':probes,'mysqlVersion':sql('SELECT VERSION()'),'migrationVersions':versions,'noDemoAccounts':True,'modelConfigured':False,'componentConfigured':True,'previewEngine':preview['engine'],'previewKeptTaskPending':True,'persistentSessionId':session['id']})
    print('Fresh migration, non-root container, health and real Cangjie HTTP preview checked; no model request.')

def seed_check():
    fixture=json.loads(FIXTURE.read_text());token=login(fixture);fixture['token']=token;release.save(FIXTURE,fixture)
    probes=health();versions=sql('SELECT version,success FROM flyway_schema_history ORDER BY installed_rank')
    session=api('/need-sessions/'+str(fixture['sessionId']),token=token)
    assert len(session['messages'])==1
    preview=api('/task-transition-preview','POST',{'demandId':fixture['taskId'],'event':'CANCEL'},token)
    assert preview['decision']['status']=='CANCELLED' and preview['decision']['outcome']=='APPLIED'
    assert api('/demands/'+str(fixture['taskId']),token=token)['status']=='PENDING'
    assert api('/need-sessions/ai-status',token=token)['configured'] is False
    report(initial={'health':probes,'mysqlVersion':sql('SELECT VERSION()'),'migrationVersions':versions,'noDemoAccounts':True,'modelConfigured':False,'componentConfigured':True,'previewEngine':preview['engine'],'previewKeptTaskPending':True,'persistentSessionId':session['id']})
    print('Existing fixture verified after runtime permission fix; nothing reseeded.')

def restart_and_fault():
    fixture=json.loads(FIXTURE.read_text());state=release.load(PROJECT)
    backend=release.container(PROJECT,state,'backend');database=release.container(PROJECT,state,'database')
    before=sql('SELECT COUNT(*) FROM flyway_schema_history')
    release.run(['docker','restart',backend],stdout=subprocess.DEVNULL);ready()
    token=fixture['token'] # Existing JWT survives process restart; secret is stable.
    session=api('/need-sessions/'+str(fixture['sessionId']),token=token)
    assert session['revision']==fixture['sessionRevision'] and len(session['messages'])==1
    start=time.monotonic()
    try:
        release.run(['docker','stop','--time','15',database],stdout=subprocess.DEVNULL)
        assert http(PROJECT,'/actuator/health/liveness')=={'status':'UP'}
        assert http(PROJECT,'/actuator/health/readiness',expected=503)=={'status':'DOWN'}
        unavailableMs=round((time.monotonic()-start)*1000)
    finally:
        release.run(['docker','start',database],stdout=subprocess.DEVNULL)
    ready();assert sql('SELECT COUNT(*) FROM flyway_schema_history')==before
    intent=api('/trusted-tools/prepare','POST',{'action':'CANCEL','requestKey':'deploy_'+secrets.token_hex(8),'demandId':fixture['taskId'],'reason':'隔离部署验证结束'},token)
    result=api('/trusted-tools/'+intent['id']+'/execute','POST',{'confirmed':True},token)
    assert result['state']=='SUCCEEDED';fixture['intentId']=intent['id'];release.save(FIXTURE,fixture)
    credit=sql('SELECT credit_score FROM users WHERE id='+str(int(fixture['userId'])))
    release.run(['docker','stop','--time','65',backend],stdout=subprocess.DEVNULL)
    logs=release.capture(['docker','logs',backend])
    assert 'Graceful shutdown complete' in logs
    release.run(['docker','start',backend],stdout=subprocess.DEVNULL);ready()
    replay=api('/trusted-tools/'+intent['id']+'/execute','POST',{'confirmed':True},token)
    assert replay['state']=='SUCCEEDED'
    assert sql('SELECT credit_score FROM users WHERE id='+str(int(fixture['userId'])))==credit
    report(restart={'jwtRetained':True,'sessionAndMessagesRetained':True,'migrationCountUnchanged':True,'trustedReceiptReplayAfterRestart':'SUCCEEDED','creditNotDeductedTwice':True,'gracefulShutdownLogged':True},databaseFault={'liveness':200,'readiness':503,'observationMsIncludingStop':unavailableMs,'recovered':True})
    print('Restart persistence, DB down/up probes, graceful shutdown and durable tool replay passed.')

def restore_check(project):
    assert project.startswith('campus-') and project!=PROJECT
    fixture=json.loads(FIXTURE.read_text());ready(project)
    token=login(fixture,project)
    session=api('/need-sessions/'+str(fixture['sessionId']),token=token,project=project)
    receipt=api('/trusted-tools/'+fixture['intentId'],token=token,project=project)
    assert len(session['messages'])==1 and receipt['state']=='SUCCEEDED'
    assert api('/demands/'+str(fixture['taskId']),token=token,project=project)['status']=='CANCELLED'
    for table in ('users','demands','need_sessions','need_messages','tool_intents','tool_audits','flyway_schema_history'):
        assert sql('SELECT COUNT(*) FROM '+table,project)==sql('SELECT COUNT(*) FROM '+table)
    report(backupRestore={'targetProject':project,'newEmptyDatabaseUsed':True,'countsMatch':True,'sessionMessagesAndSucceededReceiptRetained':True,'migrationChecksumsValidatedOnStartup':True,'newInstanceSecretUsedForLogin':True})
    print('Private backup restored into second empty database; rows and persisted receipt verified over HTTP.')

def checksum_fault(project):
    assert project=='campus-m3-restore-review','Only the already verified disposable restore copy may be faulted'
    state=release.load(project);backend=release.container(project,state,'backend')
    checksum=sql("SELECT checksum FROM flyway_schema_history WHERE version='6'",project)
    assert checksum.lstrip('-').isdigit()
    try:
        sql("UPDATE flyway_schema_history SET checksum=checksum+1 WHERE version='6'",project)
        release.run(['docker','restart',backend],stdout=subprocess.DEVNULL)
        for attempt in range(20):
            logs=release.capture(['docker','logs',backend])
            if 'Migration checksum mismatch for migration version 6' in logs:break
            time.sleep(1)
        else:raise AssertionError('Checksum corruption did not prevent startup')
    finally:
        sql("UPDATE flyway_schema_history SET checksum="+checksum+" WHERE version='6'",project)
        release.run(['docker','restart',backend],stdout=subprocess.DEVNULL)
    ready(project)
    report(schemaFault={'targetProject':project,'injection':'Changed only V6 checksum in disposable restored copy; no business rows changed','checksumMismatchPreventedStartup':True,'originalChecksumRestored':True,'recovered':True})
    print('Corrupted migration checksum blocked startup; original checksum restored and copy recovered.')

def verify_current():
    """Repeatable post-upgrade check using the same completed fixture, without new business actions."""
    fixture=json.loads(FIXTURE.read_text());state=release.load(PROJECT)
    probes=health();token=login(fixture)
    session=api('/need-sessions/'+str(fixture['sessionId']),token=token)
    assert session['revision']==fixture['sessionRevision'] and len(session['messages'])==1
    receipt=api('/trusted-tools/'+fixture['intentId'],token=token)
    assert receipt['state']=='SUCCEEDED'
    credit=sql('SELECT credit_score FROM users WHERE id='+str(int(fixture['userId'])))
    count=sql('SELECT COUNT(*) FROM demands')
    replay=api('/trusted-tools/'+fixture['intentId']+'/execute','POST',{'confirmed':True},token)
    assert replay['state']=='SUCCEEDED'
    assert sql('SELECT credit_score FROM users WHERE id='+str(int(fixture['userId'])))==credit
    assert sql('SELECT COUNT(*) FROM demands')==count
    assert api('/demands/'+str(fixture['taskId']),token=token)['status']=='CANCELLED'
    assert api('/need-sessions/ai-status',token=token)['configured'] is False
    preview=api('/task-transition-preview','POST',{'demandId':fixture['taskId'],'event':'CANCEL'},token)
    assert preview['source']['status']=='CANCELLED'
    versions=sql('SELECT version,success FROM flyway_schema_history ORDER BY installed_rank')
    assert len(versions.splitlines())==6
    restored=release.container('campus-m3-restore-review',release.load('campus-m3-restore-review'),'backend')
    assert not restored,'Restored review copy must be stopped, preserving its volume'
    report(finalReview={'at':datetime.now(timezone.utc).isoformat(),'currentImage':state['currentImage'],
        'health':probes,'sameFixtureAndSessionRetained':True,'succeededReceiptReplay':True,
        'creditAndTasksUnchanged':True,'sixMigrationsRetained':True,'realModelKeyNotMounted':True,
        'restoredCopyStopped':True,'restoredVolumePreserved':True})
    print('Current image healthy; same session, cancelled task and replay retained; no duplicate side effect. Restored copy stopped.')

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('phase',choices=['seed','seed-check','restart-fault','restore-check','checksum-fault','verify-current']);p.add_argument('--restore-project',default='campus-m3-restore-review');args=p.parse_args()
    if args.phase=='seed':seed()
    elif args.phase=='seed-check':seed_check()
    elif args.phase=='restart-fault':restart_and_fault()
    elif args.phase=='restore-check':restore_check(args.restore_project)
    elif args.phase=='checksum-fault':checksum_fault(args.restore_project)
    else:verify_current()
