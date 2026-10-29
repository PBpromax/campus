#!/usr/bin/env python3
"""At most TWO sequential paid samples on the existing local development service; never a load test.
Requires private synthetic accounts and the explicit 3308/campus_m2_dev configuration.
A started report cannot be rerun; no paid retry, publication or change of provider keys.
"""
import argparse
from datetime import datetime,timedelta
import json
from pathlib import Path
import time
import urllib.request,urllib.error
import uuid
from zoneinfo import ZoneInfo
from capacity_metrics import StreamResult
from private_env import read_env

BASE='http://127.0.0.1:8081/api'
CLIENT=urllib.request.build_opener(urllib.request.ProxyHandler({}))
def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--credentials',type=Path,required=True);p.add_argument('--config',type=Path,required=True)
    p.add_argument('--output',type=Path,required=True);a=p.parse_args()
    assert not a.output.exists(),'Started paid sample report already exists; no automatic retry'
    config=read_env(a.config)
    assert ':3308/campus_m2_dev' in config['DB_URL'] and config['SERVER_PORT']=='8081'
    user=json.loads(a.credentials.read_text())['users'][0];token=None
    def open_request(path,method='GET',body=None,trace=None):
        h={'Content-Type':'application/json'}
        if token:h['Authorization']='Bearer '+token
        if trace:h['X-Campus-Trace-Id']=trace
        r=urllib.request.Request(BASE+path,data=None if body is None else json.dumps(body,ensure_ascii=False).encode(),headers=h,method=method)
        try:return CLIENT.open(r,timeout=55)
        except urllib.error.HTTPError as e:return e
    def api(path,method='GET',body=None):
        with open_request(path,method,body) as r:
            value=json.loads(r.read());assert r.status==200,(path,r.status)
        return value['data']
    token=api('/users/login','POST',{k:user[k] for k in ('username','password')})['token']
    assert api('/need-sessions/ai-status')['configured'] is True
    budget=api('/need-sessions/ai-budget');assert budget['priceVersion']!='LOCAL-FIXTURE-NO-REAL-CHARGE'
    task_count=len(api('/demands'));report={'at':datetime.now(ZoneInfo('Asia/Shanghai')).isoformat(),
        'scope':'TWO real model observations, sequential; native dev backend, not Docker capacity acceptance',
        'automaticRetries':0,'maxDispatchedAttempts':2,'dispatchedAttempts':0,'priceVersion':budget['priceVersion'],
        'priceCheckedSource':'https://api-docs.deepseek.com/quick_start/pricing/','samples':[]}
    def save():
        a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    save();day=(datetime.now(ZoneInfo('Asia/Shanghai'))+timedelta(days=2)).strftime('%Y-%m-%d')
    cases=[('STUDY','用一句话解释：y=-x的导数为什么是-1？只解释负号，不需要发布求助。'),
        ('BORROW',f'这是本地合成演示，只整理需求，不发布。想借1个USB-C转HDMI转接器用于课堂展示，{day}上午9点前在图书馆一楼服务台当面交接，9点半开始用，11点结束，12点在同一服务台归还。')]
    for category,text in cases:
        s=api('/need-sessions','POST');s=api('/need-sessions/'+str(s['id'])+'/category','PUT',{'revision':s['revision'],'category':category})
        trace=str(uuid.uuid4());record={'category':category,'input':text,'sessionId':s['id'],'traceId':trace,'state':'DISPATCHED'}
        report['samples'].append(record);report['dispatchedAttempts']+=1;save()
        result=StreamResult();start=time.monotonic()
        try:
            with open_request('/need-sessions/'+str(s['id'])+'/ai-stream','POST',{'revision':s['revision'],'text':text},trace) as r:
                status=r.status
                if status==200:
                    while line:=r.readline():result.line(line.decode(),round((time.monotonic()-start)*1000,3))
                else:r.read()
            record.update(result.record(status,round((time.monotonic()-start)*1000,3)))
            # The ledger may finish shortly after the final SSE; bounded polling never dispatches another model call.
            end=time.monotonic()+4;entry=None
            while time.monotonic()<end:
                entry=next((v for v in api('/need-sessions/ai-budget')['recent'] if v['traceId']==trace and v['state']!='RESERVED'),None)
                if entry:break
                time.sleep(.1)
            if entry:
                record['usage']={k:entry[k] for k in ('model','state','failureKind','promptTokens','completionTokens','estimatedUsd','estimateBasis','reservedUsd','priceVersion')}
                count=entry['completionTokens']
                record['outputTokensPerSecondIncludingFullRequest']=None if count is None else round(count*1000/record['durationMs'],3)
            saved=api('/need-sessions/'+str(s['id']));record['persistedAssistantReplies']=[m['text'] for m in saved['messages'] if m['role']=='ASSISTANT']
            record['state']='OBSERVED'
        except Exception as error:
            record.update(state='FAILED',exception=type(error).__name__)
            save();raise
        save()
    report['tasksUnchanged']=len(api('/demands'))==task_count
    assert report['tasksUnchanged']
    report['sampleCount']=2;report['status']='COMPLETE';report['notStatisticalSloAcceptance']=True
    report['tokenRateDefinition']='Provider-reported completion tokens / complete HTTP request duration; includes network, prompt and tool generation. Not decoder speed.'
    report['nativeRenderingMeasured']=False
    save();print('Two real model observations saved; no retry, publication or payment operation.')
if __name__=='__main__':main()
