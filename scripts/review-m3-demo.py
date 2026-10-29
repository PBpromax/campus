#!/usr/bin/env python3
"""Current-UI rehearsal in local 3308/campus_m2_dev. Synthetic task, read-only preview, explicit cleanup.
No model request, account registration, privilege change, real user message or payment.
"""
import argparse
from datetime import datetime,timedelta
import json
import os
from pathlib import Path
import secrets
import time
import urllib.request,urllib.error
from zoneinfo import ZoneInfo
from m2_ui_driver import NativeUI
from private_env import read_env

ROOT=Path(__file__).resolve().parents[1]
HTTP=urllib.request.build_opener(urllib.request.ProxyHandler({}))
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('phase',choices=['prepare','ui','cleanup','home'])
    parser.add_argument('--credentials',required=True,type=Path);parser.add_argument('--config',required=True,type=Path)
    parser.add_argument('--state',required=True,type=Path);parser.add_argument('--output',required=True,type=Path);a=parser.parse_args()
    config=read_env(a.config);assert ':3308/campus_m2_dev' in config['DB_URL'] and config['SERVER_PORT']=='8081'
    users=json.loads(a.credentials.read_text())['users'];a.output.mkdir(parents=True,exist_ok=True)
    def api(path,method='GET',body=None,token=None):
        headers={'Content-Type':'application/json'}
        if token:headers['Authorization']='Bearer '+token
        req=urllib.request.Request('http://127.0.0.1:8081/api'+path,data=None if body is None else json.dumps(body,ensure_ascii=False).encode(),headers=headers,method=method)
        try:r=HTTP.open(req,timeout=8)
        except urllib.error.HTTPError as error:r=error
        with r:status=r.status;v=json.loads(r.read())
        assert status==200,(path,status);return v['data']
    def login(user):return api('/users/login','POST',{k:user[k] for k in ('username','password')})['token']
    def save(state):
        fd=os.open(a.state,os.O_WRONLY|os.O_CREAT|os.O_TRUNC,0o600)
        with os.fdopen(fd,'w') as f:json.dump(state,f)
        a.state.chmod(0o600)
    def report(**updates):
        path=a.output/'preview-review.json'
        value=json.loads(path.read_text()) if path.exists() else {'at':datetime.now(ZoneInfo('Asia/Shanghai')).isoformat(),
            'scope':'Current native UI rehearsal; synthetic task seeded with legacy API, not AI publication','paidModelCalls':0}
        value.update(updates);path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n')
    if a.phase=='home':
        u=NativeUI();u.login(users[0]);u.tap_id('nav-0');u.click('＋ 新对话')
        u.find(lambda n:n.get('id')=='service-BORROW');u.screenshot(a.output/'07-ready-home.png')
        print('App ready at new home conversation.');return
    owner,helper=map(login,users[:2])
    if a.phase=='prepare':
        assert not a.state.exists(),'Review state exists; resume UI/cleanup instead of reseeding'
        assert api('/task-transition-preview/status',token=helper)['configured'] is True
        state={'requestKey':'m3_review_'+secrets.token_hex(8),'modelCallsBefore':api('/need-sessions/ai-budget',token=owner)['calls']};save(state)
        task=api('/demands','POST',{'title':'M3评审-借用转接器状态预览','description':'本地合成夹具，仅演示状态预测，不是真实校园委托。',
            'category':'BORROW','location':'图书馆服务台（测试）','reward':1,
            'expectedTime':(datetime.now()+timedelta(days=2)).isoformat(timespec='minutes')},owner)
        state['taskId']=task['id'];save(state)
        result=api('/task-transition-preview','POST',{'demandId':task['id'],'event':'ACCEPT'},helper)
        assert result['source']['status']=='PENDING' and result['decision']['status']=='IN_PROGRESS'
        assert api('/demands/'+str(task['id']),token=owner)['status']=='PENDING'
        report(taskId=task['id'],engine=result['engine'],sourceBefore=result['source'],predicted=result['decision'],fixturePrepared=True)
        print('Synthetic pending task prepared; Cangjie preview did not accept it.');return
    state=json.loads(a.state.read_text());tid=state['taskId']
    if a.phase=='ui':
        assert api('/demands/'+str(tid),token=owner)['status']=='PENDING','Use an active fixture, never fake a completed task as pending'
        u=NativeUI();u.login(users[1]);u.tap_id('nav-1');u.tap_id('task-scope-market');u.tap_id('task-'+str(tid));u.click('查看影响')
        u.find(lambda n:'确认后，任务将从' in n.get('text','') and '进行中' in n.get('text',''))
        u.screenshot(a.output/'06-state-preview.png')
        after=api('/task-transition-preview','POST',{'demandId':tid,'event':'ACCEPT'},helper)
        task=api('/demands/'+str(tid),token=owner)
        assert after['source']['status']=='PENDING' and after['source']['version']==0 and task['acceptorId'] is None
        report(currentUiButton='查看影响',uiStatePreviewVerified=True,afterUiSource=after['source'],previewDidNotChangeTask=True)
        print('Current UI state preview verified; real task still PENDING/version0/no acceptor.');return
    task=api('/demands/'+str(tid),token=owner)
    if task['status']=='PENDING':
        if 'cleanupReceipt' not in state:
            receipt=api('/trusted-tools/prepare','POST',{'action':'CANCEL','requestKey':state['requestKey'],
                'demandId':tid,'reason':'本地评审预览演练结束'},owner)
            state['cleanupReceipt']=receipt['id'];save(state)
        receipt=api('/trusted-tools/'+state['cleanupReceipt'],token=owner)
        if receipt['state']=='PREPARED':receipt=api('/trusted-tools/'+receipt['id']+'/execute','POST',{'confirmed':True},owner)
        assert receipt['state']=='SUCCEEDED'
    task=api('/demands/'+str(tid),token=owner);assert task['status']=='CANCELLED'
    assert api('/need-sessions/ai-budget',token=owner)['calls']==state['modelCallsBefore']
    state['finishedAt']=datetime.now(ZoneInfo('Asia/Shanghai')).isoformat();save(state)
    report(cleanup={'explicitSeparateConfirmation':True,'finalStatus':'CANCELLED','historicalAuditRetained':True},modelCallsUnchanged=True)
    print('Synthetic task explicitly cancelled; no model request or real payment.')
if __name__=='__main__':main()
