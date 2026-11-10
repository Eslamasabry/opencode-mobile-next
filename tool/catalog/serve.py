#!/usr/bin/env python3
"""Serves the published catalog: /home/eslam/Storage2/catalog/current.

`current` is a symlink that rebuild.sh switches atomically; the path is
opened afresh on every request, so a new release shows up with no restart.
Layout: / is the catalog, /map/ the app map, /shots/ the screenshot gallery,
/status.json the last build's status. Nothing is cached, so a reload always
shows the newest release.
"""
import functools
import http.server
import os
import sys

ROOT = os.environ.get("CATALOG_CURRENT", "/home/eslam/Storage2/catalog/current")
HOST = os.environ.get("CATALOG_HOST", "127.0.0.1")
PORT = int(os.environ.get("CATALOG_PORT", "8766"))


class Handler(http.server.SimpleHTTPRequestHandler):
    def end_headers(self):
        self.send_header("Cache-Control", "no-store")
        super().end_headers()

    def log_message(self, fmt, *args):  # keep the journal quiet
        if not args or not str(args[1]).startswith(("2", "3")):
            super().log_message(fmt, *args)


def main():
    handler = functools.partial(Handler, directory=ROOT)
    server = http.server.ThreadingHTTPServer((HOST, PORT), handler)
    print(f"serving {ROOT} on {HOST}:{PORT}", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    return 0


if __name__ == "__main__":
    sys.exit(main())
