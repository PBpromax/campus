#!/usr/bin/env python3
"""Loopback-only controllable provider fixture. Never logs/stores request bodies or credentials."""
from http.server import ThreadingHTTPServer,BaseHTTPRequestHandler
import json,threading,time
state={'mode':'healthy','requests':0};lock=threading.Lock()
class Handler(BaseHTTPRequestHandler):
 def log_message(self,*args):pass
 def reply(self,status,value):
  body=json.dumps(value).encode();self.send_response(status);self.send_header('Content-Type','application/json');self.send_header('Content-Length',str(len(body)));self.end_headers();self.wfile.write(body)
 def do_GET(self):
  if self.path!='/stats':return self.reply(404,{})
  with lock:value=dict(state)
  self.reply(200,value)
 def do_POST(self):
  size=int(self.headers.get('Content-Length','0'))
  if size>48000:return self.reply(413,{})
  data=self.rfile.read(size)
  if self.path=='/control':
   mode=json.loads(data)['mode'];assert mode in ['healthy','stall','error429','error500']
   with lock:state['mode']=mode
   return self.reply(200,{'mode':mode})
  if self.path!='/chat/completions':return self.reply(404,{})
  # This fixture accepts only a fake key, never a real provider credential.
  if self.headers.get('Authorization')!='Bearer fixture-only-key':return self.reply(401,{'error':'fixture key required'})
  with lock:state['requests']+=1;mode=state['mode']
  if mode.startswith('error'):return self.reply(int(mode[5:]),{'error':'fixture failure'})
  self.send_response(200);self.send_header('Content-Type','text/event-stream');self.end_headers()
  try:
   self.wfile.write(('data: '+json.dumps({'choices':[{'delta':{'content':'本地模拟答复，仅用于验证链路。'}}]})+'\n\n').encode());self.wfile.flush()
   if mode=='stall':time.sleep(5);return
   self.wfile.write(('data: '+json.dumps({'choices':[{'delta':{},'finish_reason':'stop'}],'usage':{'prompt_tokens':20,'completion_tokens':3,'total_tokens':23,'prompt_cache_hit_tokens':15,'prompt_cache_miss_tokens':5}})+'\n\ndata: [DONE]\n\n').encode());self.wfile.flush()
  except (BrokenPipeError,ConnectionResetError):pass
if __name__=='__main__':
 print('Local fixture listening at 127.0.0.1:18765; only fake key accepted; no provider calls.',flush=True)
 ThreadingHTTPServer(('127.0.0.1',18765),Handler).serve_forever()
