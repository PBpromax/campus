#!/usr/bin/env python3
"""Shared golden fixtures, actual Cangjie protocol tests and small local concurrency experiment. No model/DB calls."""
import argparse
from collections import Counter
import hashlib,json,os,statistics,subprocess,time
from pathlib import Path
from datetime import datetime
ROOT=Path(__file__).resolve().parents[1]
SDK=Path(os.environ.get('CANGJIE_HOME',Path.home()/'.cache/campus-toolchains/cangjie-1.2.0/cangjie'))
BIN=ROOT/'cangjie/task-preview/build/task-preview'
FIXTURE=ROOT/'harmony/entry/src/main/resources/rawfile/task-transition-cases.tsv'
STATES=['PENDING','IN_PROGRESS','PENDING_CONFIRMATION','COMPLETED','CANCELLED']
KINDS=['ACCEPT','SUBMIT','CONFIRM','CANCEL'];ROLES=['PUBLISHER','ACCEPTOR','OTHER'];CATEGORIES=['RUNNING','STUDY','BORROW','OTHER']
# Contract table reviewed against existing DemandService state transitions; independent of both implementations.
ALLOWED={('PENDING','ACCEPT','OTHER'):'IN_PROGRESS',('IN_PROGRESS','SUBMIT','ACCEPTOR'):'PENDING_CONFIRMATION',
 ('PENDING_CONFIRMATION','CONFIRM','PUBLISHER'):'COMPLETED',('PENDING','CANCEL','PUBLISHER'):'CANCELLED',
 ('IN_PROGRESS','CANCEL','ACCEPTOR'):'CANCELLED',('PENDING_CONFIRMATION','CANCEL','ACCEPTOR'):'CANCELLED'}
def golden():
 rows=[]
 for category in CATEGORIES:
  for state in STATES:
   for event in KINDS:
    for role in ROLES:
     after=ALLOWED.get((state,event,role));rows.append([category,state,'7',event,role,'APPLIED' if after else 'STATE_OR_ROLE_REJECTED',after or state,'8' if after else '7'])
 return '\n'.join('\t'.join(row) for row in rows)+'\n'
def run(payload,success=True):
 env={'PATH':'/usr/bin:/bin','DYLD_FALLBACK_LIBRARY_PATH':str(SDK/'runtime/lib/darwin_aarch64_cjnative')}
 started=time.perf_counter_ns()
 p=subprocess.run([str(BIN)],input=payload,text=True,capture_output=True,env=env,timeout=5)
 elapsed=(time.perf_counter_ns()-started)/1e6
 if not success:
  assert p.returncode!=0 and p.stdout=='ERROR\tINVALID_INPUT_OR_EXECUTION\n' and 'RESULT' not in p.stdout
  return elapsed
 assert p.returncode==0,(p.returncode,p.stderr)
 lines=p.stdout.splitlines();assert lines[0]=='CAMPUS_TRANSITION_V1'
 results=[line.split('\t') for line in lines[1:-1]]
 assert all(len(row)==7 and row[0]=='RESULT' and int(row[1])==i for i,row in enumerate(results))
 summary=lines[-1].split('\t');assert summary[0]=='SUMMARY' and int(summary[2])==len(results)
 return results,int(summary[1]),elapsed

def main():
 parser=argparse.ArgumentParser();parser.add_argument('--write-fixtures',action='store_true');parser.add_argument('--out',type=Path,default=ROOT/'docs/M3/evidence/02-cangjie-verification.json');args=parser.parse_args()
 if args.write_fixtures:FIXTURE.write_text(golden())
 assert FIXTURE.read_text()==golden(),'Golden fixture drift; review contract before regeneration'
 cases=[row.split('\t') for row in FIXTURE.read_text().splitlines()]
 tasks=[f'TASK\t{i}\t{r[0]}\t{r[1]}\t{r[2]}' for i,r in enumerate(cases,1)]
 events=[f'EVENT\t{i}\tcase{i}\t{r[2]}\t{r[3]}\t{r[4]}' for i,r in enumerate(cases,1)]
 # Protocol cap is 128 tasks, so send two independent deterministic batches.
 for start in range(0,len(cases),120):
  payload='CAMPUS_TRANSITION_V1\tPARALLEL\n'+'\n'.join(tasks[start:start+120]+events[start:start+120])+'\nEND\n'
  results,workers,_=run(payload);assert workers==4
  for actual,expected in zip(results,cases[start:start+120],strict=True):assert actual[4:]==expected[5:]
 transition='CAMPUS_TRANSITION_V1\tSERIAL\nTASK\t1\tSTUDY\tPENDING\t0\n'
 transition+='EVENT\t1\taccept\t0\tACCEPT\tOTHER\nEVENT\t1\tsubmit\t1\tSUBMIT\tACCEPTOR\nEVENT\t1\tconfirm\t2\tCONFIRM\tPUBLISHER\n'
 transition+='EVENT\t1\taccept\t0\tACCEPT\tOTHER\nEVENT\t1\taccept\t0\tCANCEL\tPUBLISHER\nEND\n'
 seq,_,_=run(transition);assert [r[4] for r in seq]==['APPLIED','APPLIED','APPLIED','REPLAY','EVENT_ID_CONFLICT'];assert seq[3][5:]==['IN_PROGRESS','1']
 duplicate='CAMPUS_TRANSITION_V1\tPARALLEL\nTASK\t1\tSTUDY\tPENDING\t0\n'+('EVENT\t1\tsame\t0\tACCEPT\tOTHER\n'*128)+'END\n'
 assert Counter(row[4] for row in run(duplicate)[0])=={'APPLIED':1,'REPLAY':127}
 isolated='CAMPUS_TRANSITION_V1\tPARALLEL\nTASK\t1\tSTUDY\tPENDING\t0\nTASK\t2\tBORROW\tPENDING\t0\nEVENT\t1\tsame\t0\tACCEPT\tOTHER\nEVENT\t2\tsame\t0\tACCEPT\tOTHER\nEND\n'
 assert [row[4] for row in run(isolated)[0]]==['APPLIED','APPLIED']
 bounds='CAMPUS_TRANSITION_V1\tSERIAL\nTASK\t1\tBORROW\tPENDING\t9007199254740991\nEVENT\t1\tcap\t9007199254740991\tACCEPT\tOTHER\nEND\n'
 assert run(bounds)[0][0][4]=='VERSION_EXHAUSTED'
 valid='CAMPUS_TRANSITION_V1\tSERIAL\nTASK\t1\tSTUDY\tPENDING\t0\nEVENT\t1\tx\t0\tACCEPT\tOTHER\nEND\n'
 invalid=[valid.replace('STUDY','EXPRESS'),valid.replace('\tACCEPT\t','\tDELETE\t'),valid.replace('TASK\t1','TASK\t9007199254740992'),valid.replace('PENDING\t0','PENDING\t-1'),valid.replace('EVENT\t1\tx','EVENT\t2\tx'),valid.replace('END\n',''),valid.replace('EVENT\t1\tx','EVENT\t1\tbad space'),valid.replace('TASK\t1\tSTUDY\tPENDING\t0\n','TASK\t1\tSTUDY\tPENDING\t0\n'*2)]
 invalid.extend([valid+'EVENT\t1\ttrailing\t0\tACCEPT\tOTHER\n',valid.replace('\tACCEPT\tOTHER','\tACCEPT\tADMIN')])
 for text in invalid:run(text,success=False)
 experiments={};raw=[]
 for mode in ['SERIAL','PARALLEL']:
  times=[]
  for round_id in range(12):
   payload=f'CAMPUS_TRANSITION_V1\t{mode}\nTASK\t1\tRUNNING\tPENDING\t0\n'+'\n'.join(f'EVENT\t1\trace{i}\t0\tACCEPT\tOTHER' for i in range(512))+'\nEND\n'
   results,workers,ms=run(payload);counts=Counter(row[4] for row in results)
   assert counts=={'APPLIED':1,'VERSION_CONFLICT':511};assert workers==(1 if mode=='SERIAL' else 4)
   times.append(ms);raw.append({'mode':mode,'round':round_id,'workers':workers,'events':512,'applied':counts['APPLIED'],'versionConflicts':counts['VERSION_CONFLICT'],'processElapsedMs':round(ms,3)})
  experiments[mode]={'rounds':len(times),'p50ProcessMs':round(statistics.median(times),3),'minProcessMs':round(min(times),3),'maxProcessMs':round(max(times),3)}
 report={'at':datetime.now().astimezone().isoformat(),'sdk':'Cangjie 1.2.0 cjnative / macOS arm64','goldenCases':len(cases),'fixtureSha256':hashlib.sha256(FIXTURE.read_bytes()).hexdigest(),'invalidProtocolsRejected':len(invalid),'sequentialLifecycleReplayConflict':'passed','versionCap':'passed','concurrentSameIdReplay':{'events':128,'applied':1,'replay':127},'crossTaskEventIdIsolation':'passed','contention':experiments,'samples':raw,'interpretation':'Process time includes startup; bounded local experiment, not system SLO or a claim of parallel speedup. In-memory preview resets every invocation.','paidModelCalls':0}
 args.out.parent.mkdir(parents=True,exist_ok=True);args.out.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n');print(json.dumps({k:report[k] for k in ['goldenCases','invalidProtocolsRejected','contention','paidModelCalls']}))
if __name__=='__main__':main()
