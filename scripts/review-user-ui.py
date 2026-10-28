#!/usr/bin/env python3
"""Non-paid native UI review. Requires private local fixture accounts; never logs credentials.
Creates a BORROW draft, checks conflict/edit/confirmation invalidation and revocation.
Only loopback 8081 and the installed com.pbpromax.campus app are used. No task is published.
"""
import argparse
from datetime import datetime, timedelta
import json
from pathlib import Path
import secrets
import sys
import time
import urllib.request
import urllib.error
from m2_ui_driver import NativeUI

ROOT = Path(__file__).resolve().parents[1]

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--credentials',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--state',type=Path,required=True,help='Private resumable review state; not credentials')
    args=parser.parse_args()
    out=args.output.resolve()
    out.mkdir(parents=True,exist_ok=True)
    if (out/'review.json').exists():raise SystemExit('Review already exists; inspect it instead of silently replacing evidence.')
    user=json.loads(args.credentials.read_text())['users'][0]
    token=None
    def api(path,method='GET',body=None,expected=200):
        headers={'Content-Type':'application/json'}
        if token:headers['Authorization']='Bearer '+token
        req=urllib.request.Request('http://127.0.0.1:8081/api'+path,
            data=None if body is None else json.dumps(body,ensure_ascii=False).encode(),headers=headers,method=method)
        try:r=urllib.request.urlopen(req,timeout=8)
        except urllib.error.HTTPError as error:r=error
        with r:status=r.status;value=json.loads(r.read())
        assert status==expected,(path,status,expected)
        return value.get('data')
    token=api('/users/login','POST',{k:user[k] for k in ('username','password')})['token']
    def save_state(state):
        args.state.write_text(json.dumps(state));args.state.chmod(0o600)
    if args.state.exists():
        state=json.loads(args.state.read_text())
        sid=state['sessionId'];day=state['day'];before=state['beforeTasks'];calls=state['beforeCalls']
        old={'id':state['oldReceipt']}
    else:
        before=len(api('/demands'))
        calls=api('/need-sessions/ai-budget')['calls']
        session=api('/need-sessions','POST')
        sid=session['id']
        session=api(f'/need-sessions/{sid}/category','PUT',{'revision':session['revision'],'category':'BORROW'})
        day=(datetime.now()+timedelta(days=2)).strftime('%Y-%m-%d')
        fields={'item':'转接器','purpose':'本地验收演示','spec':'USB-C转HDMI','quantity':'1',
            'needBy':day+'T09:00','useStart':day+'T09:30','useEnd':day+'T11:00','returnAt':day+'T12:00',
            'pickupPlace':'图书馆一楼服务台','returnPlace':'图书馆一楼服务台','handoff':'当面交接'}
        session=api(f'/need-sessions/{sid}/draft','PUT',{'revision':session['revision'],'fields':fields,'contexts':[]})
        assert session['drafts'][0]['assessment']['ready']
        session=api(f'/need-sessions/{sid}/confirm','POST',{'revision':session['revision']})
        old=api('/trusted-tools/prepare','POST',{'action':'PUBLISH','requestKey':'ui_review_'+secrets.token_hex(8),
            'sessionId':sid,'revision':session['revision'],'publication':{'title':'本地验收转接器借用','reward':1,
            'location':'图书馆一楼服务台','expectedTime':day+'T12:00'}})
        assert old['state']=='PREPARED'
        state={'sessionId':sid,'day':day,'beforeTasks':before,'beforeCalls':calls,'oldReceipt':old['id'],'phase':'seeded'}
        save_state(state)
    def remote():return api('/need-sessions/'+str(sid))
    u=NativeUI();u.login(user)
    def click_text(text):
        node=u.find(lambda n:n.get('text')==text)
        u.shell('uitest','uiInput','click',*u.center(node));time.sleep(.2)
    def wait_for(predicate):
        end=time.monotonic()+4
        while time.monotonic()<end:
            value=remote()
            if predicate(value):return value
            time.sleep(.1)
        raise AssertionError('Expected saved state did not arrive; no blind retry')
    if state['phase']=='seeded':
        u.screenshot(out/'02-borrow-before.png')
        click_text('修改');click_text('全部信息')
        u.field('returnAt',day+'T10:00')
        click_text('保存需求')
        invalid=wait_for(lambda s:s['drafts'][0]['fields'].get('returnAt')==day+'T10:00')
        draft=invalid['drafts'][0]
        assert not draft['assessment']['ready'] and draft['confirmedRevision']!=draft['revision']
        assert any('时间约束冲突' in v for v in draft['assessment']['conflicts'])
        # Native UI renders Chinese field names and a conflict; stale operation is rejected server-side.
        u.find(lambda n:'时间约束冲突' in n.get('text',''))
        u.screenshot(out/'03-time-conflict.png')
        api('/trusted-tools/'+old['id']+'/execute','POST',{'confirmed':True},expected=409)
        state['phase']='conflict-checked';save_state(state)
    # Reopen the same draft; the full-form toggle is local state, not a fresh record.
    u.login(user)
    click_text('修改');click_text('全部信息')
    u.field('returnAt',day+'T12:30');u.field('returnPlace','图书馆北门服务台')
    click_text('保存需求')
    fixed=wait_for(lambda s:s['drafts'][0]['fields'].get('returnPlace')=='图书馆北门服务台')
    assert fixed['drafts'][0]['assessment']['ready']
    assert fixed['drafts'][0]['confirmedRevision']!=fixed['drafts'][0]['revision']
    click_text('核对需求，继续发布')
    confirmed=wait_for(lambda s:s['drafts'][0]['confirmedRevision']==s['drafts'][0]['revision'])
    for key,val in {'title':'本地验收转接器借用','reward':'1','location':'图书馆北门服务台','time':day+'T12:30'}.items():
        u.fill(lambda n,k=key:n.get('id')=='publish-'+k,val)
    u.tap_id('prepare-publish')
    u.find(lambda n:n.get('id')=='confirm-operation')
    u.screenshot(out/'04-updated-confirmation.png')
    receipt=next(r for r in api('/trusted-tools') if r['sourceSessionId']==sid and r['state']=='PREPARED')
    assert '图书馆北门服务台' in receipt['preview']['description']
    assert day+'T12:30' in receipt['preview']['description']
    click_text('返回修改')
    assert api('/trusted-tools/'+receipt['id'])['state']=='REVOKED'
    u.screenshot(out/'05-revoked.png')
    assert len(api('/demands'))==before
    assert api('/need-sessions/ai-budget')['calls']==calls
    result={'at':datetime.now().astimezone().isoformat(),'scope':'API-seeded synthetic draft; native editing, conflict, confirmation and revoke',
        'sessionId':sid,'paidModelCalls':0,'modelBudgetUnchanged':True,'tasksUnchanged':True,
        'conflictDetected':True,'oldConfirmationInvalidated':True,'staleExecutionHttpStatus':409,
        'updatedReturnPlace':'图书馆北门服务台','updatedReturnAt':day+'T12:30','confirmedAfterEdit':True,
        'newReceiptRevoked':True,'screenshots':['02-borrow-before.png','03-time-conflict.png','04-updated-confirmation.png','05-revoked.png']}
    (out/'review.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
    print('Native borrow edits, time conflict, stale confirmation rejection and revoke passed; 0 model calls, 0 tasks published.')
    u.tap_id('nav-0')

if __name__=='__main__':main()
