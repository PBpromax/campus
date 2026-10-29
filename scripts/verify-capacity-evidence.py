#!/usr/bin/env python3
"""Verify recorded drill semantics and derive a summary. Does not call services or models."""
import argparse
from decimal import Decimal
import hashlib
import json
from pathlib import Path
import re
from capacity_metrics import distribution

def verify(directory):
    report=json.loads((directory/'report.json').read_text())
    rows=[json.loads(line) for line in (directory/'samples.jsonl').read_text().splitlines() if line]
    live=json.loads((directory/'live-model.json').read_text())
    assert report['status']=='COMPLETE' and report['paidModelCalls']==0
    assert report['environment']['networkInternal']
    expected=0
    for stage in report['stages']['steady']:
        samples=[r for r in rows if r['label']=='steady-'+str(stage['concurrency'])]
        assert len(samples)==stage['samples'] and all(r['success'] and r['done'] and r['failureKind'] is None for r in samples)
        assert distribution([r['durationMs'] for r in samples])==stage['completion']
        assert distribution([r['firstVisibleTextMs'] for r in samples])==stage['firstVisibleText']
        expected+=len(samples)
    assert expected==52
    streams=[r for r in rows if 'traceId' in r]
    assert len({r['traceId'] for r in streams})==len(streams)
    dispatched=[r for r in streams if r['httpStatus']==200]
    assert len(dispatched)==report['providerStats']['requests']==report['costEvidence']['dispatches']==79
    assert report['providerStats']['peakActive']==4
    rejected=[r for r in streams if r['httpStatus'] in (429,503)]
    assert len(rejected)==6
    faults=report['stages']['faults']
    assert sum(r.get('failureKind')=='TIMEOUT' for r in faults['timeline'])==3
    assert faults['allFourSlotsRecovered'] and faults['unknownUsageNotFree'] and faults['automaticRetries']==0
    tool=report['stages']['saturation']['toolsWhileFull']
    assert tool['requests']==tool['successCount']==48 and tool['singleSucceededAudit']==1 and tool['helperCreditDelta']==-5
    assert report['stages']['restart']['budgetRetained'] and report['stages']['restart']['creditUnchanged']
    assert Decimal(report['costEvidence']['aggregateReservedUsd'])==Decimal('0.02158080')*79
    assert report['costEvidence']['usageUnknownRows']==8
    assert live['status']=='COMPLETE' and live['sampleCount']==live['dispatchedAttempts']==2 and live['automaticRetries']==0
    assert live['tasksUnchanged'] and all(s['success'] and s['usage']['state']=='SUCCEEDED' for s in live['samples'])
    real_estimate=sum(Decimal(str(s['usage']['estimatedUsd'])) for s in live['samples'])
    memory=[];cpu=[];pids=[]
    scale={'B':1,'KiB':1024,'MiB':1024**2,'GiB':1024**3}
    for row in report['resourceSamples']:
        if row.get('sampleUnavailable'):continue
        match=re.match(r'([\d.]+)(B|KiB|MiB|GiB)',row['memory'])
        if match:memory.append(float(match[1])*scale[match[2]])
        cpu.append(float(row['cpuPercent'].rstrip('%')));pids.append(int(row['pids']))
    summary={'verified':True,'steadySamples':52,'steadyFailures':0,'steady':report['stages']['steady'],
        'fixtureDispatches':79,'explicitAdmissionRejections':6,'fixturePeakActive':4,'duplicateCancelCalls':48,
        'successfulCancelEffects':1,'unknownUsageRows':8,'resourceSampleCount':len(report['resourceSamples']),
        'sampledMaxMemoryMiB':round(max(memory)/1024**2,2),'sampledMaxCpuPercent':max(cpu),'sampledMaxPids':max(pids),
        'realModelSamples':2,'realModelPeakPriceEstimateUsd':str(real_estimate),
        'realModelActualBillUnknown':True,'configuredPlatformBudgetUsdPerDay':'2.00',
        'configuredCallCapPerDay':100,'effectiveDispatchCapUnderConservativeReserve':int(Decimal('2.00')/Decimal('0.02158080')),
        'sourceSha256':{name:hashlib.sha256((directory/name).read_bytes()).hexdigest() for name in ('report.json','samples.jsonl','live-model.json')}}
    (directory/'summary.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2)+'\n')
    print('Evidence verified: 52 healthy, 79 fixture dispatches, 6 admission rejections, 48 duplicate confirmations with 1 effect, 2 real samples.')
    return summary
if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('directory',type=Path);a=p.parse_args();verify(a.directory)
