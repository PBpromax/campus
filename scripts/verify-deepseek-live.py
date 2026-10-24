#!/usr/bin/env python3
"""Explicit paid live check: 4 domain turns + 1 study follow-up, no automatic retries.
Only disposable fixtures in the local development backend; never reads model API keys.
"""
import argparse
from datetime import datetime
import json
from pathlib import Path
import secrets
import urllib.request

BASE = 'http://127.0.0.1:8081/api'

def call(path, method='GET', body=None, token=''):
    headers = {'Content-Type':'application/json'}
    if token: headers['Authorization'] = 'Bearer '+token
    request = urllib.request.Request(BASE+path, headers=headers, method=method,
        data=None if body is None else json.dumps(body).encode())
    with urllib.request.urlopen(request,timeout=75) as response:
        envelope=json.load(response)
    assert envelope['code']==200, 'Business API failed'
    return envelope['data']

def stream(session,text,token):
    request = urllib.request.Request(BASE+'/need-sessions/'+str(session['id'])+'/ai-stream',method='POST',
        headers={'Content-Type':'application/json','Accept':'text/event-stream','Authorization':'Bearer '+token},
        data=json.dumps({'revision':session['revision'],'text':text}).encode())
    events=[];kind='';data=[]
    with urllib.request.urlopen(request,timeout=75) as response:
        for raw in response:
            line=raw.decode('utf-8').rstrip('\r\n')
            if not line:
                if kind: events.append({'event':kind,'data':json.loads('\n'.join(data))})
                kind='';data=[]
            elif line.startswith('event:'):kind=line[6:].strip()
            elif line.startswith('data:'):data.append(line[5:].lstrip())
    assert events and events[-1]['event']=='done', 'Stream did not complete; no automatic retry'
    final=next(e['data'] for e in reversed(events) if e['event']=='session')
    return final,events

def run(output):
    credentials={'username':'ds_live_'+secrets.token_hex(5),'password':secrets.token_urlsafe(12)}
    account=call('/users/register','POST',credentials)
    token=account['token'];sessions=[];records=[]
    samples={
      'RUNNING':'帮我代取1本书，在菜鸟驿站A区取，送到一号宿舍楼门口，当面交付，用短消息通知，当面确认。取送时间还没定，请不要猜。请追问缺失信息，并为已明确的物品、数量和交接条件提出草稿字段建议。',
      'STUDY':'我只问一个小问题：y=-x的导数为什么是-1？请用两句话中文从基础解释，不需要发布任务，同时将问题上下文和讲解起点作为字段草稿建议。',
      'BORROW':'我想借1台索尼E卡口相机，用于拍校园活动，规格要求索尼E卡口；借还时间和地点还没确定，请不要猜。请为已明确的物品、规格、数量和用途提出草稿字段建议，再追问缺失信息。',
      'OTHER':'我需要活动布置协助，目标是在学生活动中心101摆放两张桌子和三张海报，对方需要自带胶带。请将已说明的协助类型、目标、资源、地点和完成标准提出字段草稿建议；时间未定，不要猜测授权。'
    }
    try:
        assert call('/need-sessions/ai-status',token=token)['configured']
        for category,text in samples.items():
            session=call('/need-sessions','POST',token=token);sessions.append(session['id'])
            path='/need-sessions/'+str(session['id'])
            session=call(path+'/category','PUT',{'revision':session['revision'],'category':category},token)
            session,events=stream(session,text,token)
            assert session['messages'][-1]['role']=='ASSISTANT'
            proposal=next((e['data'] for e in events if e['event']=='proposal'),None)
            record={'category':category,'input':text,'eventTypes':[e['event'] for e in events],
                'textDeltaCount':sum(e['event']=='text_delta' for e in events),
                'reply':session['messages'][-1]['text'],'assistantPersisted':True,'proposalFields':[],'adopted':False}
            if proposal and proposal['fields']:
                assert proposal['revision']==session['revision']
                session=call(path+'/ai-proposals/'+proposal['token']+'/apply','POST',{'revision':proposal['revision']},token)
                record['proposalFields']=list(proposal['fields'])
                record['adopted']=True
                record['confirmationInvalidated']=all(d['confirmedRevision'] is None for d in session['drafts'])
            if category=='STUDY':
                follow,follow_events=stream(session,'我指的是上一条的负号，可以只再解释这个符号吗？不要发布任务。',token)
                record['followUpReply']=follow['messages'][-1]['text']
                record['followUpPersisted']=len(follow['messages'])==4 and follow_events[-1]['event']=='done'
            records.append(record)
    finally:
        for identity in sessions:
            remote=call('/need-sessions/'+str(identity),token=token)
            call('/need-sessions/'+str(identity),'DELETE',{'revision':remote['revision']},token)
        call('/users/'+str(account['user']['id']),'DELETE',token=token)
    report={'timestamp':datetime.now().astimezone().isoformat(),'provider':'DeepSeek','fixtureData':True,
        'calls':5,'automaticRetries':0,'records':records,'disposableFixturesCleaned':True,
        'scope':'Real API/network and persistence verification; no task publication or human feedback.'}
    output.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps({'calls':5,'completedDomains':len(records),'adoptedDomains':sum(r['adopted'] for r in records),'fixtureCleanup':True,'report':str(output)}))

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--run',action='store_true',required=True,help='Authorize five real model calls through local backend.')
    parser.add_argument('--output',type=Path,default=Path('docs/M2/evidence/06-deepseek-live.json'))
    args=parser.parse_args();run(args.output)
