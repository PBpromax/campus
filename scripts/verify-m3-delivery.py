#!/usr/bin/env python3
"""Offline M3 evidence/link/version verification; optional read-only local demonstration preflight.
--write explicitly refreshes the delivery manifest after review. It never creates test results.
"""
import argparse
from datetime import datetime
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import urllib.request
from urllib.parse import unquote
from zoneinfo import ZoneInfo

ROOT=Path(__file__).resolve().parents[1];M3=ROOT/'docs/M3';REVIEW=M3/'evidence/06-review'
MANIFEST=REVIEW/'delivery-manifest.json'
def load(path):return json.loads(path.read_text())
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def version(file,name):
    text=(ROOT/file).read_text();match=re.search(r'\b'+name+r'\s*=\s*"([^"]+)"',text)
    assert match,(file,name);return match.group(1)
def source_versions():
    base='backend/src/main/java/com/example/campushelp/ai/'
    return {'prompt':version(base+'ModelGateway.java','PROMPT_VERSION'),'context':version(base+'PromptContext.java','VERSION'),
        'sharedTaskFixtureSha256':sha(ROOT/'harmony/entry/src/main/resources/rawfile/task-transition-cases.tsv')}

def semantic_checks():
    assert load(REVIEW/'cangjie-recheck.json')['goldenCases']==240
    assert load(REVIEW/'cangjie-recheck.json')['concurrentSameIdReplay']=={'events':128,'applied':1,'replay':127}
    assert load(M3/'evidence/04-cangjie-linux.json')['passed']==240
    versions=source_versions()
    for file in [REVIEW/'cangjie-recheck.json',M3/'evidence/04-cangjie-linux.json',M3/'evidence/02-cangjie-verification.json']:
        assert load(file)['fixtureSha256']==versions['sharedTaskFixtureSha256'],file
    current=(M3/'08-评审总览.md').read_text()
    assert versions['prompt'] in current and versions['context'] in current
    assert '查看影响' in (M3/'09-现场演示与变化挑战.md').read_text()
    assert "Button('查看影响')" in (ROOT/'harmony/entry/src/main/ets/ui/TrustedTools.ets').read_text()
    borrow=load(REVIEW/'borrow-ui-serial/review.json');assert borrow['paidModelCalls']==0 and borrow['tasksUnchanged']
    assert borrow['conflictDetected'] and borrow['oldConfirmationInvalidated'] and borrow['newReceiptRevoked']
    preview=load(REVIEW/'preview-review.json')
    assert preview['paidModelCalls']==0 and preview['previewDidNotChangeTask'] and preview['modelCallsUnchanged']
    assert preview['afterUiSource']['status']=='PENDING' and preview['afterUiSource']['version']==0
    assert preview['cleanup']['finalStatus']=='CANCELLED' and preview['cleanup']['explicitSeparateConfirmation']
    notes=load(REVIEW/'rehearsal-notes.json');assert notes['initialBorrowAttempt']['status']=='FAILED'
    assert notes['serialBorrowRehearsal']['status']=='PASSED'
    capacity=M3/'evidence/05-capacity';summary=load(capacity/'summary.json')
    for name,digest in summary['sourceSha256'].items():assert sha(capacity/name)==digest,name
    assert summary['verified'] and summary['steadySamples']==52 and summary['fixtureDispatches']==79
    assert summary['duplicateCancelCalls']==48 and summary['successfulCancelEffects']==1 and summary['realModelSamples']==2
    deployment=load(M3/'evidence/04-deployment.json')
    assert deployment['finalReview']['sameFixtureAndSessionRetained'] and deployment['backupRestore']['countsMatch']
    assert deployment['schemaFault']['checksumMismatchPreventedStartup']
    # No dependency on private runtime files or device access for offline CI verification.
    tracked=subprocess.check_output(['git','-C',str(ROOT),'ls-files','-z']).decode().split('\0')
    assert not any(p.startswith('deploy/private/') or re.search(r'(^|/)\.env.*\.local$',p) for p in tracked if p)
    return versions

def local_links(writing):
    count=0
    for file in M3.rglob('*.md'):
        for target in re.findall(r'\]\(([^)]+)\)',file.read_text()):
            target=target.strip('<>')
            if re.match(r'^[a-zA-Z][a-zA-Z0-9+.-]*:',target) or target.startswith('#'):continue
            dest=(file.parent/unquote(target.split('#')[0])).resolve()
            assert dest.is_relative_to(ROOT),('Outside workspace',str(file),target)
            if writing and dest==MANIFEST:count+=1;continue
            assert dest.exists(),('Broken link',str(file.relative_to(ROOT)),target);count+=1
    return count

def verify(writing):
    versions=semantic_checks();links=local_links(writing)
    files={str(p.relative_to(ROOT)):sha(p) for p in sorted(M3.rglob('*')) if p.is_file() and p!=MANIFEST}
    if writing:
        value={'preparedAt':datetime.now(ZoneInfo('Asia/Shanghai')).isoformat(),
            'preparedFromHead':subprocess.check_output(['git','-C',str(ROOT),'rev-parse','HEAD'],text=True).strip(),
            'workingTreeDirtyAtPreparation':bool(subprocess.check_output(['git','-C',str(ROOT),'status','--porcelain'],text=True).strip()),
            'scope':'M3 local review documents and evidence; not a signed production release or teacher approval',
            'versions':versions,'localLinksChecked':links,'files':files}
        MANIFEST.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n')
    manifest=load(MANIFEST);assert manifest['versions']==versions
    assert manifest['localLinksChecked']==links
    assert manifest['files']==files,'Delivery contents drifted; review actual changes before explicitly refreshing the manifest'
    local_links(False)
    print(f'M3 delivery verified: {len(files)} files, {links} local links, current versions and actual rehearsal evidence. No model calls.')

def preflight(output):
    client=urllib.request.build_opener(urllib.request.ProxyHandler({}))
    def ready(port):
        try:
            with client.open(f'http://127.0.0.1:{port}/actuator/health/readiness',timeout=3) as r:
                return r.status==200 and json.load(r)=={'status':'UP'}
        except (OSError,ValueError):return False
    hdc=Path('/Applications/DevEco-Studio.app/Contents/sdk/default/openharmony/toolchains/hdc')
    target=False
    if hdc.exists():
        result=subprocess.run([str(hdc),'list','targets'],capture_output=True,text=True,timeout=6)
        target=result.returncode==0 and '127.0.0.1:5555' in result.stdout.splitlines()
    config=ROOT/'.env.m3-demo.local';credentials=ROOT/'deploy/private/classroom-demo/credentials.json'
    result={'at':datetime.now(ZoneInfo('Asia/Shanghai')).isoformat(),'readOnly':True,'modelCalls':0,
        'development8081Ready':ready(8081),'isolatedDeployment18082Ready':ready(18082),'expectedEmulatorConnected':target,
        'cangjieBinaryPresent':os.access(ROOT/'cangjie/task-preview/build/task-preview',os.X_OK),
        'privateDemoConfigPresent':config.exists(),'privateDemoCredentialsPresent':credentials.exists(),
        'privateDemoConfigMode600':config.exists() and config.stat().st_mode&0o777==0o600,
        'privateDemoCredentialsMode600':credentials.exists() and credentials.stat().st_mode&0o777==0o600}
    result['readyForLocalDemo']=all(result[k] for k in ['development8081Ready','isolatedDeployment18082Ready','expectedEmulatorConnected',
        'cangjieBinaryPresent','privateDemoConfigMode600','privateDemoCredentialsMode600'])
    if output:output.parent.mkdir(parents=True,exist_ok=True);output.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps(result,ensure_ascii=False,indent=2))
    if not result['readyForLocalDemo']:raise SystemExit('Some local demo prerequisites are not ready; no service/configuration was changed.')

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--write',action='store_true');parser.add_argument('--preflight',action='store_true')
    parser.add_argument('--output',type=Path);args=parser.parse_args()
    if args.preflight:
        assert not args.write,'Choose either offline manifest refresh or runtime preflight';preflight(args.output)
    else:verify(args.write)
