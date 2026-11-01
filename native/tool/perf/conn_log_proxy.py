#!/usr/bin/env python3
"""Counting TCP proxy in front of the perf fixture server (plan 13 §12.3: "the fixture server's request log and
connect-attempt counts (PR-01, PR-04) prove the rules"). S2-BACKOFF, PB-51/52/53.

The app dials this proxy instead of the fixture server. Every accepted connection and the first request line on it
are written as one JSON object per line to --log:

    {"t": 1730000000123, "ev": "accept",  "conn": 7}
    {"t": 1730000000125, "ev": "request", "conn": 7, "method": "GET", "path": "/api/event"}
    {"t": 1730000000990, "ev": "close",   "conn": 7}

Only the method and the path before "?" are logged: never headers (HTTP Basic credentials), query strings or bodies
(native/AGENTS.md rule 7). Times are host wall-clock milliseconds, the same clock the run scripts use for their marks.

"Server down" (PR-04 second half) without losing the count: while the file --down-flag exists, every live connection
is cut and each new one is accepted, its request line logged, and closed at once. A stopped server would refuse the
connection and the attempt would be invisible.

    conn_log_proxy.py --listen-port 4190 --upstream-port 4191 --log run/proxy.jsonl --down-flag run/down [--pid-file f]

Binds 127.0.0.1 unless --listen-host says otherwise (the emulator reaches it through `adb reverse` or its host alias).
Exits 2 when it cannot bind.
"""
from __future__ import annotations

import argparse
import asyncio
import json
import os
import sys
import time


class Proxy:
    def __init__(self, upstream_host: str, upstream_port: int, log_path: str, down_flag: str | None) -> None:
        self.upstream = (upstream_host, upstream_port)
        self.log = open(log_path, "a", buffering=1, encoding="utf-8")
        self.down_flag = down_flag
        self.next_id = 0
        self.live: dict[int, list[asyncio.StreamWriter]] = {}

    def is_down(self) -> bool:
        return bool(self.down_flag) and os.path.exists(self.down_flag)

    def write(self, ev: str, conn: int, **extra: str) -> None:
        self.log.write(json.dumps({"t": int(time.time() * 1000), "ev": ev, "conn": conn, **extra}) + "\n")

    async def watch_down(self) -> None:
        while True:
            if self.is_down():
                for writers in list(self.live.values()):
                    for w in writers:
                        w.close()
            await asyncio.sleep(0.2)

    async def handle(self, reader: asyncio.StreamReader, writer: asyncio.StreamWriter) -> None:
        self.next_id += 1
        conn = self.next_id
        self.write("accept", conn)
        try:
            first = await asyncio.wait_for(reader.readline(), timeout=30)
            if first:
                parts = first.decode("latin-1").split(" ")
                if len(parts) >= 2:
                    self.write("request", conn, method=parts[0][:16], path=parts[1].split("?", 1)[0][:200])
            if self.is_down() or not first:
                return
            try:
                up_reader, up_writer = await asyncio.open_connection(*self.upstream)
            except OSError:
                return  # the fixture server itself is gone: the attempt is logged, the app sees a reset
            self.live[conn] = [writer, up_writer]
            up_writer.write(first)
            await asyncio.gather(pipe(reader, up_writer), pipe(up_reader, writer), return_exceptions=True)
        except (asyncio.TimeoutError, OSError):
            pass
        finally:
            for w in self.live.pop(conn, [writer]):
                w.close()
            writer.close()
            self.write("close", conn)


async def pipe(src: asyncio.StreamReader, dst: asyncio.StreamWriter) -> None:
    try:
        while True:
            chunk = await src.read(16 * 1024)
            if not chunk:
                break
            dst.write(chunk)
            await dst.drain()
    finally:
        dst.close()


async def serve(args: argparse.Namespace) -> None:
    proxy = Proxy(args.upstream_host, args.upstream_port, args.log, args.down_flag)
    server = await asyncio.start_server(proxy.handle, args.listen_host, args.listen_port)
    if args.pid_file:
        with open(args.pid_file, "w", encoding="utf-8") as f:
            f.write(f"{os.getpid()}\n")
    asyncio.get_running_loop().create_task(proxy.watch_down())
    async with server:
        await server.serve_forever()


def main(argv: list[str]) -> int:
    p = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    p.add_argument("--listen-host", default="127.0.0.1")
    p.add_argument("--listen-port", type=int, required=True)
    p.add_argument("--upstream-host", default="127.0.0.1")
    p.add_argument("--upstream-port", type=int, required=True)
    p.add_argument("--log", required=True)
    p.add_argument("--down-flag")
    p.add_argument("--pid-file")
    args = p.parse_args(argv)
    try:
        asyncio.run(serve(args))
    except OSError as e:
        print(f"conn_log_proxy: cannot listen: {e}", file=sys.stderr)
        return 2
    except KeyboardInterrupt:
        return 0
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
