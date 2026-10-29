#!/usr/bin/env python3
"""Fixed TCP relay for host access to an internal-only drill. No credentials or configurable destinations."""
import selectors
import socket
import socketserver
import threading
import time

SLOTS=threading.BoundedSemaphore(32)
class Relay(socketserver.BaseRequestHandler):
    def handle(self):
        if not SLOTS.acquire(blocking=False):return
        try:
            with socket.create_connection(self.server.destination,timeout=3) as upstream,selectors.DefaultSelector() as selector:
                self.request.setblocking(False);upstream.setblocking(False)
                selector.register(self.request,selectors.EVENT_READ,upstream)
                selector.register(upstream,selectors.EVENT_READ,self.request)
                deadline=time.monotonic()+70
                while time.monotonic()<deadline:
                    for key,_ in selector.select(timeout=1):
                        data=key.fileobj.recv(65536)
                        if not data:return
                        # Response sizes are bounded by the application. Temporarily block only this worker.
                        key.data.setblocking(True);key.data.settimeout(3);key.data.sendall(data);key.data.setblocking(False)
        except (OSError,TimeoutError):pass
        finally:SLOTS.release()
class Server(socketserver.ThreadingTCPServer):
    allow_reuse_address=True;daemon_threads=True;request_queue_size=32
if __name__=='__main__':
    for port,destination in [(18084,('backend',8080)),(18766,('fixture',18766))]:
        server=Server(('0.0.0.0',port),Relay);server.destination=destination
        threading.Thread(target=server.serve_forever,daemon=True).start()
    print('Fixed test relays started; no outbound destination input accepted.',flush=True)
    threading.Event().wait()
