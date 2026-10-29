"""Measurement helpers: HTTP 200 is not SSE success; unknown usage is never inferred."""
import json
import math
from urllib.parse import urlsplit

def loopback(url):
    parsed=urlsplit(url)
    if parsed.scheme!='http' or parsed.hostname not in ('127.0.0.1','localhost') or parsed.username or parsed.password or parsed.query or parsed.fragment:
        raise ValueError('Only explicit local HTTP experiment endpoints are allowed')
    return url.rstrip('/')

def percentile(values,quantile):
    if not values:return None
    if not 0<quantile<=1:raise ValueError('Quantile must be in (0,1]')
    return sorted(values)[max(0,math.ceil(len(values)*quantile)-1)]

def distribution(values):
    return {'count':len(values),'p50Ms':percentile(values,.5),'p95Ms':percentile(values,.95),
        'maxMs':max(values) if values else None,'quantileMethod':'nearest-rank; no interpolation'}

class StreamResult:
    def __init__(self):
        self.done=False;self.failure=None;self.first_text_ms=None;self.last_text_ms=None
        self.characters=0;self.events={};self.buffer=[];self.proposals=0
    def line(self,line,elapsed_ms):
        if line.strip():self.buffer.append(line.rstrip('\r\n'));return
        event='message';parts=[]
        for entry in self.buffer:
            if entry.startswith('event:'):event=entry[6:].strip()
            elif entry.startswith('data:'):parts.append(entry[5:].lstrip())
        self.buffer=[]
        if not parts:return
        data=json.loads('\n'.join(parts));self.events[event]=self.events.get(event,0)+1
        if event=='text_delta' and data.get('text'):
            if self.first_text_ms is None:self.first_text_ms=elapsed_ms
            self.last_text_ms=elapsed_ms;self.characters+=len(data['text'])
        elif event=='failure':self.failure=data.get('kind','UNKNOWN')
        elif event=='done':self.done=data.get('complete') is True
        elif event=='proposal':self.proposals+=1
    def record(self,status,duration_ms):
        return {'httpStatus':status,'done':self.done,'failureKind':self.failure,
            'success':status==200 and self.done and self.failure is None,
            'firstVisibleTextMs':self.first_text_ms,'lastVisibleTextMs':self.last_text_ms,
            'durationMs':duration_ms,'visibleCharacters':self.characters,'events':self.events,'proposals':self.proposals}
