#!/usr/bin/env python3
"""Verdicts for the hidden runs PR-01 (idle) and PR-04 (offline) from the proxy log, the run's marks and the device
readings (plan 13 §3.5, §12; S2-BACKOFF). Budgets owned here: PB-40, PB-51, PB-52, PB-53.

    hidden_checks.py pr01 --run <dir> [--grace-s 30]
    hidden_checks.py pr04 --run <dir> [--tolerance-ms 1500] [--return-s 3]

<dir> holds `proxy.jsonl` (conn_log_proxy.py), `marks.jsonl` ({"t": ms, "mark": name}, written by the run script)
and, for PR-01, `readings.json` ({"cpu_ticks": {"start": n, "end": n}, "clk_tck": 100,
"wakeups": {"start": n, "end": n}}). Writes `<dir>/PR-01.json` or `<dir>/PR-04.json` in the result schema of
plan 13 §3.7 ("budgets": {"PB-nn": "pass" | "fail"}).

Exit: 0 every budget passes, 1 a budget fails, 2 an input or a mark is missing (never reported as a pass).

A "stream attempt" is a connection whose first request path starts with --stream-path (default `/api/event`, the
OpenCode 2 event stream). REST refetches open their own connections and are counted as requests, not attempts.
"""
from __future__ import annotations

import argparse
import json
import os
import sys
from dataclasses import dataclass, field


class MissingInput(Exception):
    pass


@dataclass
class Run:
    accepts: dict[int, int]  # conn -> accept time
    closes: dict[int, int]  # conn -> close time
    requests: list[tuple[int, int, str]]  # (t, conn, path)
    marks: dict[str, int]
    stream_path: str = "/api/event"

    def mark(self, name: str) -> int:
        if name not in self.marks:
            raise MissingInput(f"mark '{name}' missing from marks.jsonl")
        return self.marks[name]

    def stream_attempts(self, start: int, end: int) -> list[int]:
        """Times of stream requests in [start, end)."""
        return sorted(t for t, _, p in self.requests if p.startswith(self.stream_path) and start <= t < end)

    def requests_in(self, start: int, end: int) -> list[tuple[int, int, str]]:
        return [r for r in self.requests if start <= r[0] < end]

    def open_streams_at(self, t: int) -> int:
        stream_conns = {c for _, c, p in self.requests if p.startswith(self.stream_path)}
        return sum(
            1
            for c in stream_conns
            if self.accepts.get(c, t + 1) <= t and self.closes.get(c, t + 1) > t
        )


@dataclass
class Verdict:
    id: str
    budgets: dict[str, str] = field(default_factory=dict)
    metrics: dict[str, object] = field(default_factory=dict)
    notes: list[str] = field(default_factory=list)

    def judge(self, budget: str, ok: bool, note: str) -> None:
        # One failing check fails the budget; a later pass never clears it.
        if self.budgets.get(budget) != "fail":
            self.budgets[budget] = "pass" if ok else "fail"
        self.notes.append(f"{budget} {'ok' if ok else 'FAIL'}: {note}")

    def exit_code(self) -> int:
        return 1 if any(v != "pass" for v in self.budgets.values()) else 0

    def to_json(self) -> dict[str, object]:
        return {"schema": 1, "id": self.id, "budgets": self.budgets, "metrics": self.metrics, "notes": self.notes}


def read_jsonl(path: str) -> list[dict]:
    if not os.path.exists(path):
        raise MissingInput(f"{os.path.basename(path)} missing")
    with open(path, encoding="utf-8") as f:
        return [json.loads(line) for line in f if line.strip()]


def load_run(run_dir: str, stream_path: str) -> Run:
    accepts: dict[int, int] = {}
    closes: dict[int, int] = {}
    requests: list[tuple[int, int, str]] = []
    for e in read_jsonl(os.path.join(run_dir, "proxy.jsonl")):
        if e["ev"] == "accept":
            accepts[e["conn"]] = e["t"]
        elif e["ev"] == "close":
            closes[e["conn"]] = e["t"]
        elif e["ev"] == "request":
            requests.append((e["t"], e["conn"], e.get("path", "")))
    marks = {m["mark"]: m["t"] for m in read_jsonl(os.path.join(run_dir, "marks.jsonl"))}
    return Run(accepts, closes, requests, marks, stream_path)


def check_pr01(run: Run, readings: dict, grace_ms: int) -> Verdict:
    """PB-53: 0 open streams and 0 requests while hidden and idle. PB-40: CPU ≤ 1 s/h and 0 wake-up alarms."""
    v = Verdict("PR-01")
    start = run.mark("hidden") + grace_ms
    end = run.mark("end")
    if end <= start:
        raise MissingInput("the idle window is empty (end ≤ hidden + grace)")
    hours = (end - start) / 3_600_000
    v.metrics["idleWindowMin"] = round((end - start) / 60_000, 2)

    open_streams = run.open_streams_at(start)
    reqs = run.requests_in(start, end)
    v.metrics["openStreamsAtIdle"] = open_streams
    v.metrics["requestsWhileIdle"] = len(reqs)
    v.judge("PB-53", open_streams == 0, f"{open_streams} event stream(s) open {grace_ms // 1000} s after going hidden")
    v.judge("PB-53", not reqs, f"{len(reqs)} request(s) while hidden and idle: {sorted({p for _, _, p in reqs})[:5]}")

    try:
        ticks = readings["cpu_ticks"]["end"] - readings["cpu_ticks"]["start"]
        cpu_s = ticks / readings.get("clk_tck", 100)
        wakeups = readings["wakeups"]["end"] - readings["wakeups"]["start"]
    except (KeyError, TypeError) as e:
        raise MissingInput(f"readings.json lacks {e}") from e
    per_hour = cpu_s / hours
    v.metrics["cpuSecondsPerHour"] = round(per_hour, 3)
    v.metrics["wakeupAlarms"] = wakeups
    v.judge("PB-40", per_hour <= 1.0, f"app CPU {per_hour:.3f} s per hour (budget ≤ 1 s)")
    v.judge("PB-40", wakeups == 0, f"{wakeups} wake-up alarm(s) fired (budget 0; the bench seeds no schedules)")
    return v


def check_pr04(run: Run, tolerance_ms: int, return_ms: int) -> Verdict:
    """PB-52 airplane mode and PB-51 hidden pace with the fixture server down, then the return to the foreground."""
    v = Verdict("PR-04")
    tol = tolerance_ms

    # PB-52: nothing reaches the fixture while Android has no network; on return the stream connects with the
    # count reset (one try, plus at most one reconnect for the new default network, 04 N4-D8).
    on, off = run.mark("airplane_on"), run.mark("airplane_off")
    during = run.requests_in(on + tol, off)
    v.metrics["requestsInAirplaneMode"] = len(during)
    v.judge("PB-52", not during, f"{len(during)} request(s) reached the fixture in airplane mode")
    back = run.stream_attempts(off, off + 60_000)
    v.metrics["streamAttemptsFirstMinuteBack"] = len(back)
    v.judge("PB-52", 1 <= len(back) <= 2, f"{len(back)} stream attempt(s) in the first minute back (want 1–2)")

    # PB-51: hidden with the server down: tries after 1 s, 2 s, then every 60–120 s; ≤ 1 per 60 s after the second.
    down, fg = run.mark("server_down"), run.mark("foreground")
    tries = run.stream_attempts(down, fg)
    gaps = [b - a for a, b in zip(tries, tries[1:])]
    v.metrics["hiddenAttempts"] = len(tries)
    v.metrics["hiddenGapsMs"] = gaps
    if len(tries) < 4:
        v.judge("PB-51", False, f"only {len(tries)} stream attempt(s) in {(fg - down) // 1000} s hidden (want ≥ 4)")
    else:
        first = tries[0] - down
        v.judge("PB-51", first <= 1_000 + tol, f"first retry {first} ms after the drop (want 1 s)")
        v.judge("PB-51", abs(gaps[0] - 2_000) <= tol, f"second retry {gaps[0]} ms after the first (want 2 s)")
        slow = gaps[1:]
        bad = [g for g in slow if not (60_000 - tol <= g <= 120_000 + tol)]
        v.judge("PB-51", not bad, f"later gaps {slow} ms (want 60–120 s each); outside: {bad}")
        later = tries[2:]
        crowded = [t for t in later if sum(1 for u in later if t <= u < t + 60_000 - tol) > 1]
        v.judge("PB-51", not crowded, f"{len(crowded)} 60 s window(s) with more than one attempt after the second")

    after = run.stream_attempts(fg, fg + 60_000)
    at_once = (after[0] - fg) if after else None
    v.metrics["returnRetryMs"] = at_once
    v.judge("PB-51", at_once is not None and at_once <= return_ms,
            f"on return to the foreground the waiting retry fired after {at_once} ms (want ≤ {return_ms} ms)")
    return v


def main(argv: list[str]) -> int:
    p = argparse.ArgumentParser(description="PR-01 / PR-04 verdicts (S2-BACKOFF)")
    p.add_argument("scenario", choices=["pr01", "pr04"])
    p.add_argument("--run", required=True)
    p.add_argument("--stream-path", default="/api/event")
    p.add_argument("--grace-s", type=int, default=30)
    p.add_argument("--tolerance-ms", type=int, default=1_500)
    p.add_argument("--return-s", type=float, default=3.0)
    a = p.parse_args(argv)
    try:
        run = load_run(a.run, a.stream_path)
        if a.scenario == "pr01":
            path = os.path.join(a.run, "readings.json")
            if not os.path.exists(path):
                raise MissingInput("readings.json missing")
            with open(path, encoding="utf-8") as f:
                verdict = check_pr01(run, json.load(f), a.grace_s * 1000)
        else:
            verdict = check_pr04(run, a.tolerance_ms, int(a.return_s * 1000))
    except MissingInput as e:
        print(f"hidden_checks: could not judge: {e}", file=sys.stderr)
        return 2
    out = os.path.join(a.run, f"{verdict.id}.json")
    with open(out, "w", encoding="utf-8") as f:
        json.dump(verdict.to_json(), f, indent=2)
    for n in verdict.notes:
        print(n)
    print(f"wrote {out}")
    return verdict.exit_code()


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
