#!/usr/bin/env python3
"""Bounded local SSE fixture. Fake credentials only; no request content is logged or retained."""
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import threading
import time

LOCK = threading.Lock()
RELEASE = threading.Event()
STATE = {'mode':'healthy','requests':0,'active':0,'peakActive':0,'completed':0,'disconnected':0}
MODES = {'healthy','hold','stall','drop','error429','missing_usage'}
CAPACITY = threading.BoundedSemaphore(12)

class Handler(BaseHTTPRequestHandler):
    protocol_version='HTTP/1.1'
    def log_message(self,*args): pass
    def reply(self,status,value):
        data=json.dumps(value).encode(); self.send_response(status)
        self.send_header('Content-Type','application/json');self.send_header('Content-Length',str(len(data)))
        self.end_headers();self.wfile.write(data)
    def do_GET(self):
        if self.path!='/stats':return self.reply(404,{})
        with LOCK:value=dict(STATE)
        self.reply(200,value)
    def event(self,value):
        data=value if isinstance(value,str) else json.dumps(value,ensure_ascii=False)
        self.wfile.write(('data: '+data+'\n\n').encode());self.wfile.flush()
    def do_POST(self):
        size=int(self.headers.get('Content-Length','0'))
        if size<0 or size>48000:return self.reply(413,{})
        body=self.rfile.read(size)
        if self.path=='/control':
            try:mode=json.loads(body)['mode']
            except (ValueError,KeyError):return self.reply(400,{})
            if mode not in MODES:return self.reply(400,{})
            with LOCK:STATE['mode']=mode
            if mode=='hold':RELEASE.clear()
            else:RELEASE.set()
            return self.reply(200,{'mode':mode})
        if self.path!='/chat/completions':return self.reply(404,{})
        if self.headers.get('Authorization')!='Bearer fixture-only-key':return self.reply(401,{'error':'fake key required'})
        if not CAPACITY.acquire(blocking=False):return self.reply(503,{'error':'fixture capacity exceeded'})
        with LOCK:
            mode=STATE['mode'];STATE['requests']+=1;STATE['active']+=1
            STATE['peakActive']=max(STATE['peakActive'],STATE['active'])
        try:
            if mode=='error429':return self.reply(429,{'error':'injected rate limit'})
            request=json.loads(body)
            self.send_response(200);self.send_header('Content-Type','text/event-stream')
            self.send_header('Connection','close');self.end_headers();self.close_connection=True
            self.event({'choices':[{'delta':{'content':'本地模拟：'}}]})
            if mode in ('hold','stall'):
                end=time.monotonic()+(10 if mode=='hold' else 8)
                while time.monotonic()<end and (mode=='stall' or not RELEASE.is_set()):
                    self.wfile.write(b': fixture keepalive\n\n');self.wfile.flush();time.sleep(.05)
                if mode=='stall':return
            if mode=='drop':return
            for _ in range(19):
                time.sleep(.025)
                self.event({'choices':[{'delta':{'content':'仅测试传输。'}}]})
            # Exercise delayed task summaries separately from STUDY streaming.
            proposal='BORROW' in request['messages'][0]['content'] and '"activeCategory":"BORROW"' in request['messages'][0]['content']
            if proposal:
                self.event({'choices':[{'delta':{'tool_calls':[{'index':0,'function':{'name':'propose_draft',
                    'arguments':json.dumps({'fields':{'item':'转接器','quantity':'1'},'candidates':[]},ensure_ascii=False)}}]}}]})
            final={'choices':[{'delta':{},'finish_reason':'tool_calls' if proposal else 'stop'}]}
            if mode!='missing_usage':
                final['usage']={'prompt_tokens':20,'completion_tokens':40,'total_tokens':60,
                    'prompt_cache_hit_tokens':15,'prompt_cache_miss_tokens':5}
            self.event(final);self.event('[DONE]')
            with LOCK:STATE['completed']+=1
        except (BrokenPipeError,ConnectionResetError):
            with LOCK:STATE['disconnected']+=1
        finally:
            with LOCK:STATE['active']-=1
            CAPACITY.release()

if __name__=='__main__':
    ThreadingHTTPServer.request_queue_size=16
    print('Isolated capacity fixture :18766; fake key only; no external calls.',flush=True)
    ThreadingHTTPServer(('0.0.0.0',18766),Handler).serve_forever()
