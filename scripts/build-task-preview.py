#!/usr/bin/env python3
"""Build the pinned Cangjie tool. SDK lives outside Git; no shell rc changes or model calls."""
import argparse
import os
from pathlib import Path
import subprocess

ROOT=Path(__file__).resolve().parents[1]
DEFAULT=Path.home()/'.cache/campus-toolchains/cangjie-1.2.0/cangjie'

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--sdk',type=Path,default=Path(os.environ.get('CANGJIE_HOME',DEFAULT)))
    args=parser.parse_args();sdk=args.sdk.expanduser().resolve();compiler=sdk/'bin/cjc'
    if not compiler.is_file():raise SystemExit('Cangjie 1.2.0 SDK missing. See cangjie/task-preview/README.md')
    env=os.environ.copy();env['CANGJIE_HOME']=str(sdk);env['PATH']=str(sdk/'bin')+os.pathsep+str(sdk/'tools/bin')+os.pathsep+env.get('PATH','')
    runtime=sdk/'runtime/lib/darwin_aarch64_cjnative'
    env['DYLD_FALLBACK_LIBRARY_PATH']=str(runtime)+os.pathsep+str(sdk/'tools/lib')
    env['SDKROOT']=subprocess.check_output(['xcrun','--sdk','macosx','--show-sdk-path'],text=True).strip()
    version=subprocess.check_output([str(compiler),'-v'],text=True,env=env)
    if '1.2.0 (cjnative)' not in version:raise SystemExit('Expected Cangjie 1.2.0 cjnative; refuse silently changing toolchain')
    folder=ROOT/'cangjie/task-preview';output=folder/'build/task-preview';output.parent.mkdir(exist_ok=True)
    subprocess.run([str(compiler),'--diagnostic-format','noColor',*[str(p) for p in sorted((folder/'src').glob('*.cj'))],'-o',str(output)],env=env,check=True)
    print(version.strip());print('Built '+str(output))
if __name__=='__main__':main()
