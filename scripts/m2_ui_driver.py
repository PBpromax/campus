"""Bounded automation of the local Harmony emulator via its official uitest CLI.
Coordinates come from current dumpLayout bounds, never from guessed screenshots.
No personal credentials, model keys or full UI trees are logged.
"""
import json
from pathlib import Path
import re
import shlex
import subprocess
import time


class NativeUI:
    def __init__(self, target='127.0.0.1:5555'):
        self.hdc='/Applications/DevEco-Studio.app/Contents/sdk/default/openharmony/toolchains/hdc'
        self.target=target
        self.local=Path('/tmp/campus-m2-review-layout.json')

    def run(self,*args):
        p=subprocess.run([self.hdc,'-t',self.target,*args],capture_output=True,text=True,timeout=25,check=True)
        if 'Error:' in p.stdout or 'error:' in p.stdout: raise RuntimeError('Device command failed')
        return p.stdout

    def shell(self,*args): return self.run('shell',*args)

    def nodes(self):
        self.shell('uitest','dumpLayout','-p','/data/local/tmp/campus-review-layout.json','-b','com.pbpromax.campus')
        self.run('file','recv','/data/local/tmp/campus-review-layout.json',str(self.local))
        result=[]
        def walk(n):
            a=n.get('attributes',{}); b=list(map(int,re.findall(r'-?\d+',a.get('bounds',''))))
            if len(b)==4 and b[2]>b[0] and b[3]-b[1]>28:result.append(a)
            for c in n.get('children',[]):walk(c)
        walk(json.loads(self.local.read_text()));return result

    def swipe(self,down=True):
        self.shell('uitest','uiInput','swipe','1100', '2400' if down else '700','1100','700' if down else '2400','2200')
        time.sleep(.25)

    def find(self,predicate,down=True,scroll=True):
        for direction in ([down,not down] if scroll else [down]):
            previous=''
            for _ in range(18 if scroll else 1):
                nodes=self.nodes()
                n=next((a for a in nodes if predicate(a)),None)
                if n:return n
                signature=json.dumps([(a.get('text'),a.get('bounds')) for a in nodes])
                if not scroll or signature==previous:break
                previous=signature;self.swipe(direction)
        raise LookupError('Required UI control not found; no guessed click performed')

    @staticmethod
    def center(node):
        v=list(map(int,re.findall(r'-?\d+',node['bounds'])));return str((v[0]+v[2])//2),str((v[1]+v[3])//2)

    def click(self,text,down=True):
        n=self.find(lambda a:a.get('text')==text and a.get('clickable')=='true',down)
        assert n.get('enabled')=='true', 'Required control is disabled'
        self.shell('uitest','uiInput','click',*self.center(n));time.sleep(.3)

    def fill(self,predicate,text):
        n=self.find(lambda a:a.get('type')=='TextInput' and predicate(a))
        assert n.get('enabled')=='true','Required input is disabled'
        if n.get('text'):
            # inputText inserts at the caret; it does not replace an existing value.
            # Key codes are from this SDK's @ohos.multimodalInput.keyCode declarations.
            self.shell('uitest','uiInput','click',*self.center(n))
            self.shell('uitest','uiInput','keyEvent','2072','2017') # Ctrl+A
            self.shell('uitest','uiInput','keyEvent','2055') # delete selection
            if text:self.shell('uitest','uiInput','text',shlex.quote(text))
        else:self.shell('uitest','uiInput','inputText',*self.center(n),shlex.quote(text))
        self.shell('uitest','uiInput','keyEvent','Back');time.sleep(.15)

    def field(self,key,value,kind='text'):
        if kind!='select':self.fill(lambda a:a.get('id')=='draft-'+key,value);return
        n=self.find(lambda a:a.get('id')=='draft-'+key)
        self.shell('uitest','uiInput','click',*self.center(n));time.sleep(.2)
        n=self.find(lambda a:a.get('type')=='Option' and a.get('text')==value,scroll=False)
        self.shell('uitest','uiInput','click',*self.center(n));time.sleep(.2)

    def login(self,user):
        self.shell('aa','force-stop','com.pbpromax.campus')
        self.shell('aa','start','-a','EntryAbility','-b','com.pbpromax.campus');time.sleep(.7)
        self.click('进入统一需求与草稿')
        self.fill(lambda a:a.get('hint')=='已有平台用户名',user['username'])
        self.fill(lambda a:a.get('hint')=='密码（不保存）',user['password'])
        self.click('登录并恢复草稿');time.sleep(.5)

    def screenshot(self,path):
        path=Path(path);path.parent.mkdir(parents=True,exist_ok=True)
        self.shell('uitest','screenCap','-p','/data/local/tmp/campus-review-shot.png')
        self.run('file','recv','/data/local/tmp/campus-review-shot.png',str(path.resolve()))
