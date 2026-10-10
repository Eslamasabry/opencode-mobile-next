#!/usr/bin/env python3
"""Tiny TCP forwarder: forward.py LISTEN_HOST:PORT TARGET_HOST:PORT (stdlib only)."""
import socket
import sys
import threading


def pipe(a, b):
    try:
        while (d := a.recv(65536)):
            b.sendall(d)
    except OSError:
        pass
    for s in (a, b):
        try:
            s.shutdown(socket.SHUT_RDWR)
        except OSError:
            pass


def hp(s):
    h, p = s.rsplit(":", 1)
    return h, int(p)


listen, target = hp(sys.argv[1]), hp(sys.argv[2])
srv = socket.socket()
srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
srv.bind(listen)
srv.listen(16)
while True:
    c, _ = srv.accept()
    try:
        u = socket.create_connection(target, timeout=10)
    except OSError:
        c.close()
        continue
    u.settimeout(None)
    threading.Thread(target=pipe, args=(c, u), daemon=True).start()
    threading.Thread(target=pipe, args=(u, c), daemon=True).start()
