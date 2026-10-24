#!/usr/bin/env python3
"""Local M2 review fixtures and explicit UI domain runs. --live sends ONE paid AI call.
No automatic paid retry. Private state remains mode 600 outside the repository.
"""
import argparse
from datetime import datetime,timedelta
import importlib.util
import json
from pathlib import Path
import secrets
import subprocess
import time
from m2_ui_driver import NativeUI
from private_env import read_env

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('trusted_smoke',ROOT/'scripts/verify-trusted-tools.py')
api=importlib.util.module_from_spec(spec);spec.loader.exec_module(api)
STATE=Path('/tmp/campus-step6-private-state.json')
OUT=ROOT/'docs/M2/evidence'
NAMES={'RUNNING':'跑腿代办','STUDY':'学习辅导','BORROW':'物品借用','OTHER':'其他互助'}

def load():return json.loads(STATE.read_text())
def save(s):api.save(STATE,s)
def token(s,index=0):return api.call('/users/login','POST',{k:s['users'][index][k] for k in ['username','password']})['token']

def setup():
    assert not STATE.exists(),'Use existing private fixture state; do not silently reseed'
    assert ':3307/campus_m2_dev' in read_env(ROOT/'.env.m2.local')['DB_URL']
    s={'users':[],'cases':{},'paidCalls':0,'setupAt':datetime.now().astimezone().isoformat()};save(s)
    for label in ['a','b','outsider']:
        u={'username':'m2_review_'+label+'_'+secrets.token_hex(3),'password':secrets.token_urlsafe(12)}
        r=api.call('/users/register','POST',u);u['id']=r['user']['id'];s['users'].append(u);save(s)
    print('Created three disposable review users in campus_m2_dev; credentials not logged.',flush=True)

def samples():
    day=(datetime.now()+timedelta(days=7)).date().isoformat()
    return {
      'RUNNING':dict(taskKind='代取快递',item='书籍',quantity='1',pickupPlace='菜鸟驿站A区',destination='一号宿舍楼门口',earliestPickup=day+'T09:00',latestDelivery=day+'T10:00',handoff='指定点放置',dropPlace='一号宿舍楼值班室A架',notice='静默消息',completionProof='放置照片与通知'),
      'STUDY':dict(context='y=-x的导数为什么是-1？只解释负号这一步。',topic='导数与负号',depth='只看当前一步',communication='短消息'),
      'BORROW':dict(item='相机',purpose='拍校园活动',spec='索尼E卡口',quantity='1',needBy=day+'T09:00',useStart=day+'T10:00',useEnd=day+'T12:00',returnAt=day+'T13:00',pickupPlace='图书馆东门',returnPlace='图书馆东门',handoff='当面交接',support='需要简短说明',accessories='电池和充电器'),
      'OTHER':dict(serviceType='活动布置',serviceGoal='布置社团展台',resourceRequirements='自带胶带',serviceStart=day+'T09:00',serviceEnd=day+'T10:00',servicePlace='学生活动中心101',communication='线下协助',completionCriteria='摆放两张桌子并张贴三张海报',consent='无需额外授权',helperCount='2',splitMode='希望由一人协调')}

def recent(t):return api.call('/need-sessions',token=t)[0]
def wait_for(test,seconds=65):
    limit=time.monotonic()+seconds
    while time.monotonic()<limit:
        try:result=test()
        except OSError:result=None
        if result:return result
        time.sleep(.6)
    raise TimeoutError('Review did not reach expected state; no automatic retry')

def domain(category,live):
    assert live,'Explicit --live required: one paid model call per domain run'
    s=load();assert category not in s['cases'],'Case already started; inspect it rather than automatically making another paid request'
    ui=NativeUI();ui.login(s['users'][0]);t=token(s)
    ui.click('新建会话',down=False);ui.click(NAMES[category]+' · 选择')
    session=recent(t);assert session['activeCategory']==category
    record={'sessionId':session['id'],'category':category,'startedAt':datetime.now().astimezone().isoformat(),'stage':'selected'}
    s['cases'][category]=record;save(s)
    values=samples()[category]
    catalog=api.call('/need-sessions/catalog',token=t);defs=next(c['fields'] for c in catalog if c['category']==category)
    labels={f['key']:f['label'] for f in defs}
    text='这是课程本地演示，不是真实委托。'+('我只想弄懂一个小知识点，请先简短解释。' if category=='STUDY' else '请按我的具体情境澄清和整理。')
    text+='已明确的条件：'+'；'.join(labels[k]+'='+v for k,v in values.items() if k!='consent')
    if category=='OTHER':text+='。确实需要2位同学，希望由一人协调，不是创建两个任务。'
    text+='。请为我已明确的条件提出草稿字段建议；不要发布，不要猜授权。'
    ui.fill(lambda a:a.get('hint')=='继续补充当前问题或约束',text)
    s['paidCalls']+=1;record['input']=text;record['stage']='paid-request-started';save(s)
    ui.click('发送给DeepSeek');print(category+': real model request sent once',flush=True)
    session=wait_for(lambda: (r if (r:=api.call('/need-sessions/'+str(session['id']),token=t))['messages'] and r['messages'][-1]['role']=='ASSISTANT' else None))
    record['reply']=session['messages'][-1]['text'];record['stage']='reply-saved';save(s)
    ui.find(lambda a:a.get('text')=='采用字段建议（不确认、不发布）')
    ui.screenshot(OUT/('08-'+category.lower()+'-proposal.png'))
    ui.click('采用字段建议（不确认、不发布）');time.sleep(.5)
    session=api.call('/need-sessions/'+str(session['id']),token=t);draft=next(d for d in session['drafts'] if d['category']==category)
    record['adoptedFields']=list(draft['fields']);assert draft['fields'],'No adopted model fields'
    assert draft['confirmedRevision'] is None
    record['stage']='adopted';save(s)
    ui.click('使用完整表单',down=False)
    for field in defs:
        k=field['key']
        if k in values and draft['fields'].get(k)!=values[k]:ui.field(k,values[k],field['type'])
    ui.click('保存并校验草稿');time.sleep(.4)
    session=api.call('/need-sessions/'+str(session['id']),token=t);draft=next(d for d in session['drafts'] if d['category']==category)
    assert draft['assessment']['ready'],draft['assessment']
    ui.click('确认当前草稿（不发布）');time.sleep(.4)
    session=api.call('/need-sessions/'+str(session['id']),token=t);record['confirmedRevision']=session['revision'];record['stage']='confirmed';save(s)
    for prefix,value in [('任务标题','M2-REVIEW-'+category),('明确的报酬','5.00'),('任务地点',values.get('servicePlace',values.get('destination',values.get('pickupPlace','线上')))),('完成时间',(datetime.now()+timedelta(days=8)).isoformat(timespec='minutes'))]:
        ui.fill(lambda a,p=prefix:a.get('hint','').startswith(p),value)
    ui.click('准备执行确认卡（尚不执行）');time.sleep(.5)
    r=next(r for r in api.call('/trusted-tools',token=t) if r['preview']['title']=='M2-REVIEW-'+category)
    assert r['state']=='PREPARED' and r['result'] is None
    record['receiptId']=r['id'];record['stage']='prepared';save(s)
    ui.find(lambda a:a.get('text')=='我已核对，确认执行发布任务')
    ui.screenshot(OUT/('08-'+category.lower()+'-confirmation.png'))
    ui.click('我已核对，确认执行发布任务');time.sleep(.6)
    r=api.call('/trusted-tools/'+r['id'],token=t);assert r['state']=='SUCCEEDED' and r['result']['category']==category
    record['taskId']=r['result']['id'];record['stage']='published';save(s)
    ui.find(lambda a:a.get('text','').startswith('执行时结果：任务 #'+str(record['taskId'])+' · PENDING'))
    ui.screenshot(OUT/('08-'+category.lower()+'-published.png'))
    record['finishedAt']=datetime.now().astimezone().isoformat();save(s)
    report={'recordedAt':record['finishedAt'],'fixture':'disposable local test; real MySQL writes; no actual campus service or payment','case':record,'paidCallsForCase':1,'automaticRetries':0}
    (OUT/('08-'+category.lower()+'-flow.json')).write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    print(category+': UI dialogue, adopt, manual review, confirmation and real publish passed',flush=True)

def action(ui,name):
    node=ui.find(lambda a:a.get('type')=='Select' and a.get('text') in ['发布任务','接单','取消任务'])
    ui.shell('uitest','uiInput','click',*ui.center(node));time.sleep(.2)
    option=ui.find(lambda a:a.get('type')=='Option' and a.get('text')==name,scroll=False)
    ui.shell('uitest','uiInput','click',*ui.center(option));time.sleep(.2)

def conversation(ui,task,text):
    ui.click('需要真人协商 / AI不可用')
    ui.click('加载可协商的本人任务')
    ui.click('协商 #'+str(task)+' M2-REVIEW-RUNNING')
    ui.fill(lambda a:a.get('hint','').startswith('明确发给任务另一方的消息'),text)
    ui.click('发送给任务另一方（真人）');time.sleep(.5)

def collaboration():
    s=load();assert 'collaboration' not in s,'Inspect existing collaboration state before repeating'
    task=s['cases']['RUNNING']['taskId'];ui=NativeUI();ui.login(s['users'][1]);helper=token(s,1);owner=token(s)
    action(ui,'接单');ui.click('刷新可接任务')
    ui.click('#'+str(task)+' M2-REVIEW-RUNNING · PENDING')
    ui.click('准备执行确认卡（尚不执行）');time.sleep(.4)
    r=next(r for r in api.call('/trusted-tools',token=helper) if r['action']=='ACCEPT' and r['preview']['demandId']==task)
    assert r['state']=='PREPARED'
    ui.find(lambda a:a.get('text')=='我已核对，确认执行接单');ui.screenshot(OUT/'08-helper-accept-confirmation.png')
    ui.click('我已核对，确认执行接单');time.sleep(.6)
    result=api.call('/demands/'+str(task),token=owner);assert result['status']=='IN_PROGRESS' and result['acceptorId']==s['users'][1]['id']
    ui.find(lambda a:a.get('text','').endswith('· IN_PROGRESS'));ui.screenshot(OUT/'08-helper-accepted.png')
    s['collaboration']={'taskId':task,'acceptReceiptId':r['id'],'uiAccepted':True,'messages':[]};save(s)
    msg='我已接单，请确认是否仍放在值班室A架。'
    conversation(ui,task,msg)
    records=api.call('/messages/demand/'+str(task),token=owner)
    assert records[-1]['content']==msg and records[-1]['receiverId']==s['users'][0]['id']
    s['collaboration']['messages'].append({'sender':'helper','content':msg});save(s)
    ui.find(lambda a:a.get('text')=='我：'+msg);ui.screenshot(OUT/'08-helper-human-message.png')
    ui.login(s['users'][0]);msg='是的，请放在A架后用静默消息通知。'
    conversation(ui,task,msg)
    records=api.call('/messages/demand/'+str(task),token=helper)
    assert records[-1]['content']==msg and records[-1]['receiverId']==s['users'][1]['id']
    assert api.call('/demands/'+str(task),token=owner)['status']=='IN_PROGRESS'
    s['collaboration']['messages'].append({'sender':'publisher','content':msg});save(s)
    ui.find(lambda a:a.get('text')=='我：'+msg);ui.screenshot(OUT/'08-publisher-human-reply.png')
    print('Two-account UI accept and real human conversation passed; task remains IN_PROGRESS',flush=True)

def cancel_collaboration():
    s=load();task=s['collaboration']['taskId'];ui=NativeUI();ui.login(s['users'][1]);t=token(s,1)
    action(ui,'取消任务');ui.fill(lambda a:a.get('hint')=='准确的任务编号',str(task))
    ui.fill(lambda a:a.get('hint','').startswith('取消原因'),'课程本地演示结束，取消测试任务')
    ui.click('准备执行确认卡（尚不执行）');time.sleep(.4)
    ui.find(lambda a:a.get('text')=='我已核对，确认执行取消任务');ui.screenshot(OUT/'08-helper-cancel-confirmation.png')
    ui.click('我已核对，确认执行取消任务');time.sleep(.5)
    assert api.call('/demands/'+str(task),token=t)['status']=='CANCELLED'
    assert api.call('/users/'+str(s['users'][1]['id']),token=t)['creditScore']==95
    s['collaboration']['uiCancelled']=True;s['collaboration']['helperFinalCredit']=95;save(s)
    ui.find(lambda a:a.get('text','').endswith('· CANCELLED'));ui.screenshot(OUT/'08-helper-cancelled.png')
    print('Helper UI cancellation passed; disclosed credit consequence is exactly -5',flush=True)

def network_fault():
    s=load();assert 'fault' not in s,'Inspect saved fault state before repeating'
    ui=NativeUI();ui.login(s['users'][0]);t=token(s)
    ui.click('新建会话',down=False);ui.click('学习辅导 · 选择')
    session=recent(t);ui.click('使用完整表单')
    text='断网时保留的上下文：只问负号这一步。';ui.field('context',text)
    s['fault']={'sessionId':session['id'],'offlineInput':text};save(s)
    pids=subprocess.check_output(['/usr/sbin/lsof','-tiTCP:8081','-sTCP:LISTEN'],text=True).split();assert len(pids)==1
    assert 'CampusHelpApplication' in subprocess.check_output(['ps','-p',pids[0],'-o','args='],text=True)
    subprocess.run(['kill','-TERM',pids[0]],check=True)
    wait_for(lambda:not subprocess.run(['/usr/sbin/lsof','-tiTCP:8081','-sTCP:LISTEN'],capture_output=True,text=True).stdout.strip(),30)
    ui.click('保存并校验草稿');time.sleep(1)
    n=ui.find(lambda a:a.get('id')=='draft-context');assert n['text']==text
    ui.screenshot(OUT/'08-network-input-preserved.png')
    ui.find(lambda a:'本地输入仍保留' in a.get('text',''),down=False);ui.screenshot(OUT/'08-network-error.png')
    s['fault']['offlineInputPreserved']=True;save(s)
    print('Owned backend stopped. Failed UI save preserves input. Restart backend before recover-fault.',flush=True)

def recover_fault():
    s=load();ui=NativeUI();t=token(s);session_id=s['fault']['sessionId']
    assert not api.call('/need-sessions/ai-status',token=t)['configured'],'This drill requires an explicitly model-disabled local backend'
    ui.click('保存并校验草稿');time.sleep(.5)
    session=api.call('/need-sessions/'+str(session_id),token=t)
    assert session['drafts'][0]['fields']['context']==s['fault']['offlineInput']
    s['fault']['networkRecoverySavedSameInput']=True;save(s)
    toggle=ui.find(lambda a:a.get('text') in ['使用完整表单','返回需求卡片'],down=False)
    if toggle['text']=='使用完整表单':ui.click('使用完整表单')
    text='请只解释当前负号，不要发布任务。'
    ui.fill(lambda a:a.get('hint')=='继续补充当前问题或约束',text)
    ui.click('发送给DeepSeek');time.sleep(1)
    assert ui.find(lambda a:a.get('hint')=='继续补充当前问题或约束')['text']==text
    ui.find(lambda a:'HTTP 503' in a.get('text',''),down=False);ui.screenshot(OUT/'08-model-unavailable.png')
    remote=api.call('/need-sessions/'+str(session_id),token=t)
    assert len(remote['messages'])==len(session['messages']),'Missing configuration must not silently save or run a model request'
    ui.click('仅保存上下文，不调用AI');time.sleep(.4)
    ui.field('context','模型关闭后用固定表单补充：y=-x，解释负号。')
    ui.click('保存并校验草稿');time.sleep(.5)
    remote=api.call('/need-sessions/'+str(session_id),token=t)
    assert remote['drafts'][0]['fields']['context']=='模型关闭后用固定表单补充：y=-x，解释负号。'
    s['fault'].update({'model503PreservedUnsentInput':True,'noUserMessageWrittenBefore503':True,'manualFormWorksWithModelOff':True,'paidCallsForFaults':0});save(s)
    ui.find(lambda a:a.get('id')=='draft-context');ui.screenshot(OUT/'08-model-off-fixed-form.png')
    recover_tail()

def recover_tail():
    s=load();ui=NativeUI();t=token(s);session_id=s['fault']['sessionId']
    task=s['collaboration']['taskId'];text='AI不可用，我改用真人消息核对放置点。'
    conversation(ui,task,text)
    assert api.call('/messages/demand/'+str(task),token=t)[-1]['content']==text
    s['fault']['humanMessageWorksWithModelOff']=True;save(s)
    ui.find(lambda a:a.get('text')=='我：'+text);ui.screenshot(OUT/'08-model-off-human-fallback.png')
    # Simulate a second signed-in client changing the source after a card was prepared.
    ui.click('确认当前草稿（不发布）',down=False)
    for prefix,value in [('任务标题','M2-REVIEW-CONFLICT'),('明确的报酬','5.00'),('任务地点','测试线上'),('完成时间',(datetime.now()+timedelta(days=1)).isoformat(timespec='minutes'))]:
        ui.fill(lambda a,p=prefix:a.get('hint','').startswith(p),value)
    ui.click('准备执行确认卡（尚不执行）');time.sleep(.4)
    receipt=next(r for r in api.call('/trusted-tools',token=t) if r['preview']['title']=='M2-REVIEW-CONFLICT')
    source=api.call('/need-sessions/'+str(session_id),token=t)
    api.call('/need-sessions/'+str(session_id)+'/draft','PUT',{'revision':source['revision'],'fields':{'context':'第二个客户端已纠正问题，旧确认必须失效。'},'contexts':[]},t)
    ui.click('我已核对，确认执行发布任务');time.sleep(.6)
    receipt=api.call('/trusted-tools/'+receipt['id'],token=t)
    assert receipt['state']=='FAILED' and receipt['errorCode']==409 and receipt['result'] is None
    assert not any(d['title']=='M2-REVIEW-CONFLICT' for d in api.call('/trusted-tools/tasks?scope=mine',token=t))
    ui.click('查询同一回执（不重复执行）')
    ui.find(lambda a:'草稿已更新' in a.get('text',''));ui.screenshot(OUT/'08-stale-confirmation-rejected.png')
    s['fault']['concurrentSourceEditRejectsOldConfirmation']=True;s['fault']['staleConfirmationNoTaskWrite']=True;save(s)
    print('Network recovery, model503 input preservation, fixed form and real human fallback passed with zero provider calls.',flush=True)

def finish():
    s=load();t=token(s)
    assert api.call('/need-sessions/ai-status',token=t)['configured'],'Restore normal model configuration first'
    assert len(s['cases'])==4 and all(r['stage']=='published' for r in s['cases'].values())
    assert s['collaboration'].get('uiCancelled') and s['fault'].get('humanMessageWorksWithModelOff')
    for case in s['cases'].values():
        task=api.call('/demands/'+str(case['taskId']),token=t)
        if task['status']=='PENDING':api.execute(api.prepare('CANCEL',t,demandId=task['id'],reason='课程综合演示结束，取消测试委托'),t)
        assert api.call('/demands/'+str(case['taskId']),token=t)['status']=='CANCELLED'
    s['finishedAt']=datetime.now().astimezone().isoformat();save(s)
    report={k:s[k] for k in ['setupAt','finishedAt','paidCalls','collaboration','fault']}
    report['cases']={k:{x:v for x,v in r.items() if x not in ['input','reply']} for k,r in s['cases'].items()}
    report.update({'automaticRetries':0,'normalModelConfigurationRestored':True,'allFourFixtureTasksCancelled':True,'credentials':'Only in mode600 /tmp state, not in evidence','scope':'Local emulator + MySQL; no real users or payments'})
    (OUT/'08-integrated-review.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    print('Four-domain review complete; fixture tasks cancelled and normal model configuration restored.',flush=True)

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('phase',choices=['setup','domain','collaboration','cancel-collaboration','network-fault','recover-fault','recover-tail','finish']);p.add_argument('--category',choices=list(NAMES));p.add_argument('--live',action='store_true');a=p.parse_args()
    if a.phase=='setup':setup()
    elif a.phase=='domain':domain(a.category,a.live)
    elif a.phase=='collaboration':collaboration()
    elif a.phase=='cancel-collaboration':cancel_collaboration()
    elif a.phase=='network-fault':network_fault()
    elif a.phase=='recover-fault':recover_fault()
    elif a.phase=='recover-tail':recover_tail()
    else:finish()
