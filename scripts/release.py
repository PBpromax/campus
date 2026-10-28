#!/usr/bin/env python3
"""Scoped local Compose releases. No shell eval, secret CLI args, production adoption or database deletion."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import subprocess
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
PRIVATE = ROOT / 'deploy/private'
JAVA_IMAGE = 'public.ecr.aws/docker/library/eclipse-temurin@sha256:affcab5356e1270ac35d2361ee83135af2f03e8d580eca78a6d5d6fdbf048db0'
MYSQL = 'mysql@sha256:7dcddc01f13bab2f15cde676d44d01f61fc9f99fe7785e86196dfc07d358ae2b'
SDK = {
    'arm64': ('aarch64', '6ab1e26eece1f87ff5befa72', '40e3962472d6d1b181e685198b193b904a385ac8dbbdff8aa9c52c4bb6f6e0f1'),
    'amd64': ('x64', '6ab1e26eece1f87ff5befa73', '8c5fd944cd9cc51ee63d4a240e3ecc4f540345dae1468e8b47c927ecf2ba998f'),
}

def run(args, **kwargs):
    return subprocess.run(args, check=True, **kwargs)

def capture(args):
    return subprocess.check_output(args, text=True).strip()

def save(path, value):
    path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    tmp = path.with_suffix('.tmp')
    with open(tmp,'w', opener=lambda n,f: os.open(n,f,0o600)) as h:
        json.dump(value,h,ensure_ascii=False,indent=2); h.write('\n')
    os.replace(tmp,path)

def folder(project):
    if not re.fullmatch(r'campus-[a-z0-9-]{1,40}', project):
        raise ValueError('Project must start campus- and contain lowercase letters, digits and hyphens')
    return PRIVATE/project

def load(project):
    return json.loads((folder(project)/'state.json').read_text())

def compose(project, state, args, **kwargs):
    env=os.environ.copy()
    # Compose interpolation receives only paths/non-secret values; credentials remain file mounts.
    env.update(CAMPUS_IMAGE=state.get('currentImage') or 'campus-unbuilt:missing',
        CAMPUS_MYSQL_IMAGE=state['mysqlImage'], CAMPUS_PORT=str(state['port']),
        CAMPUS_SECRETS_DIR=str(folder(project)/'secrets'))
    # Disable implicit .env loading by explicitly choosing an empty file.
    return run(['docker','compose','--env-file','/dev/null','-p',project,'-f',str(ROOT/'deploy/compose.yml'),*args],env=env,**kwargs)

def container(project,state,service):
    return compose(project,state,['ps','-a','-q',service],capture_output=True,text=True).stdout.strip()

def init(project, port):
    d=folder(project)
    if d.exists(): raise ValueError('Release directory exists; inspect it instead of replacing secrets/state')
    if not 1024 <= port <= 65535: raise ValueError('Use a non-privileged port')
    d.mkdir(parents=True,mode=0o700); (d/'secrets').mkdir(mode=0o700)
    for key,value in {'DB_PASSWORD':secrets.token_urlsafe(32),'ROOT_PASSWORD':secrets.token_urlsafe(40),
        'JWT_SECRET':secrets.token_urlsafe(64),'DEEPSEEK_API_KEY':''}.items():
        p=d/'secrets'/key
        p.write_text(value)
        # Parent is 0700; 0444 allows the non-root container UID to read its specific bind-mounted file.
        p.chmod(0o444)
    state={'project':project,'port':port,'mysqlImage':MYSQL,'currentImage':None,'previousImage':None,'status':'INITIALIZED'}
    save(d/'state.json',state)
    print('Initialized private release directory; model is disabled until its private secret is filled.')

def backup(project,state):
    cid=container(project,state,'database')
    if not cid:raise ValueError('Database container is not running')
    name=datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')+'-'+secrets.token_hex(3)+'.sql'
    p=folder(project)/'backups'/name; p.parent.mkdir(mode=0o700,exist_ok=True)
    with open(p,'wb',opener=lambda n,f:os.open(n,f,0o600)) as h:
        run(['docker','exec',cid,'sh','-c','MYSQL_PWD="$(cat /run/secrets/DB_PASSWORD)" exec mysqldump -u campus_release --single-transaction --skip-lock-tables --no-tablespaces --set-gtid-purged=OFF --skip-add-drop-table campus_release'],stdout=h)
    print('Private logical backup saved:',p)
    return p

def restore(project,state,path):
    path=Path(path).expanduser().resolve()
    if not path.is_file():raise ValueError('Backup file not found')
    compose(project,state,['up','-d','--wait','--wait-timeout','150','database'])
    cid=container(project,state,'database')
    count=capture(['docker','exec',cid,'sh','-c','MYSQL_PWD="$(cat /run/secrets/DB_PASSWORD)" exec mysql -N -u campus_release campus_release -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()"'])
    if count!='0':raise ValueError('Restore requires an empty isolated database; no data was deleted')
    with path.open('rb') as h:
        run(['docker','exec','-i',cid,'sh','-c','MYSQL_PWD="$(cat /run/secrets/DB_PASSWORD)" exec mysql -u campus_release campus_release'],stdin=h)
    print('Restored into empty release database; start backend to validate migration checksums and schema.')

def deploy(project,image,compatible=False):
    state=load(project); previous=state.get('currentImage')
    # Resolve a local image to its immutable ID before changing anything.
    resolved=capture(['docker','image','inspect',image,'--format','{{.Id}}'])
    if not re.fullmatch('sha256:[a-f0-9]{64}',resolved):raise ValueError('Immutable local image ID required')
    if previous and not compatible:raise ValueError('Review schema compatibility, then supply --compatible; no rollout performed')
    if previous:backup(project,state)
    candidate={**state,'currentImage':resolved,'status':'DEPLOYING'}
    save(folder(project)/'pending.json',{'previous':previous,'candidate':resolved,'schemaCompatibilityDeclared':compatible})
    try:
        compose(project,candidate,['up','-d','--wait','--wait-timeout','150'])
    except subprocess.CalledProcessError:
        if previous and compatible:
            compose(project,state,['up','-d','--wait','--wait-timeout','150'])
            save(folder(project)/'last-failure.json',{'candidate':resolved,'previousRestored':previous,'at':datetime.now(timezone.utc).isoformat()})
            (folder(project)/'pending.json').unlink(missing_ok=True)
            print('Candidate failed health/startup; previous compatible application restored. Schema was not reversed.')
        raise
    candidate.update(previousImage=previous,status='HEALTHY')
    save(folder(project)/'state.json',candidate)
    (folder(project)/'pending.json').unlink(missing_ok=True)
    print('Release healthy:',resolved)

def build(tag):
    if not re.fullmatch(r'campus-[a-z0-9-]+:[a-zA-Z0-9_.-]+',tag):raise ValueError('Use a local campus-...:tag image name')
    arch=capture(['docker','info','--format','{{.Architecture}}'])
    target={'aarch64':'arm64','x86_64':'amd64'}.get(arch,arch)
    if target not in SDK:raise ValueError('Only native Linux arm64/amd64 builds are supported')
    platform,key,digest=SDK[target]
    cache=Path.home()/'.cache/campus-toolchains'/f'cangjie-linux-{platform}-1.2.0.tar.gz';cache.parent.mkdir(parents=True,exist_ok=True)
    if not cache.exists():
        url=f'https://cangjie-lang.cn/v1/files/auth/downLoad?nsId=142267&fileName=cangjie-sdk-linux-{platform}-1.2.0.tar.gz&objectKey={key}'
        temp=cache.with_suffix('.download');urllib.request.urlretrieve(url,temp);temp.rename(cache)
    if hashlib.file_digest(cache.open('rb'),'sha256').hexdigest()!=digest:raise ValueError('SDK SHA256 mismatch')
    java=Path(os.environ.get('JAVA_HOME',Path.home()/'.cache/campus-toolchains/jdk-21.0.12.1+1/Contents/Home'))
    version=subprocess.run([str(java/'bin/java'),'-version'],capture_output=True,text=True,check=True)
    if not re.search(r'version "21(?:\.|\")',version.stderr+version.stdout):raise ValueError('Java 21 required')
    env=os.environ.copy();env.update(JAVA_HOME=str(java),PATH=str(java/'bin')+os.pathsep+env.get('PATH',''))
    run(['mvn','-B','-Dspring.profiles.active=test','package'],cwd=ROOT/'backend',env=env)
    build_dir=ROOT/'deploy/build';build_dir.mkdir(exist_ok=True)
    shutil.copyfile(cache,build_dir/'cangjie-sdk.tar.gz')
    (build_dir/'cangjie-sdk.sha256').write_text(digest+'  sdk.tar.gz\n')
    revision=capture(['git','-C',str(ROOT),'rev-parse','HEAD'])
    dirty=bool(capture(['git','-C',str(ROOT),'status','--porcelain']))
    java_image=JAVA_IMAGE
    if subprocess.run(['docker','image','inspect',java_image],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL).returncode:
        run(['docker','pull',java_image])
    run(['docker','build','-f',str(ROOT/'deploy/Dockerfile'),'--build-arg','JAVA_IMAGE='+java_image,
        '--build-arg','REVISION='+revision+('-dirty' if dirty else ''),'-t',tag,str(ROOT)])
    manifest={'image':capture(['docker','image','inspect',tag,'--format','{{.Id}}']),'tag':tag,'javaBase':java_image,
        'sourceCommit':revision,'workingTreeDirty':dirty,'jarSha256':hashlib.file_digest((ROOT/'backend/target/campus-help-1.0.0.jar').open('rb'),'sha256').hexdigest(),
        'sdkSha256':digest,'sdkVersion':'1.2.0','platform':'linux/'+target,'builtAt':datetime.now(timezone.utc).isoformat()}
    save(build_dir/'manifest.json',manifest);print('Release manifest saved (no secrets).')

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--project',default='campus-m3-review')
    sub=p.add_subparsers(dest='cmd',required=True)
    a=sub.add_parser('init');a.add_argument('--port',type=int,default=18082)
    a=sub.add_parser('build');a.add_argument('--tag',required=True)
    a=sub.add_parser('up');a.add_argument('--image',required=True);a.add_argument('--compatible',action='store_true')
    a=sub.add_parser('rollback');a.add_argument('--compatible',action='store_true',required=True)
    sub.add_parser('status');sub.add_parser('backup');sub.add_parser('down')
    a=sub.add_parser('restore');a.add_argument('--backup',required=True)
    a=p.parse_args()
    if a.cmd=='init':init(a.project,a.port)
    elif a.cmd=='build':build(a.tag)
    elif a.cmd=='up':deploy(a.project,a.image,a.compatible)
    elif a.cmd=='rollback':
        previous=load(a.project).get('previousImage')
        if not previous:raise ValueError('No previous healthy release recorded')
        deploy(a.project,previous,a.compatible)
    else:
        state=load(a.project)
        if a.cmd=='backup':backup(a.project,state)
        elif a.cmd=='restore':restore(a.project,state,a.backup)
        elif a.cmd=='status':compose(a.project,state,['ps'])
        elif a.cmd=='down':compose(a.project,state,['down']) # Keep data volume and all backups.

if __name__=='__main__':
    try:main()
    except (ValueError,OSError,subprocess.CalledProcessError) as error:
        raise SystemExit(str(error)) from None
