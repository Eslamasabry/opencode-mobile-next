#!/usr/bin/env python3
"""fxctl.py - start, stop and inspect the FX-* fixture servers by exact PID.

    python3 -I native/e2e/fixtures/fxctl.py start  --run-dir DIR FX-OC2 FX-GH ...
    python3 -I native/e2e/fixtures/fxctl.py status --run-dir DIR
    python3 -I native/e2e/fixtures/fxctl.py stop   --run-dir DIR [FX-GH ...]   (all when none named)

Layout of DIR (one per run, the runner's ``<out>``):

    pids/<ID>.json      {"pid", "starttime", "script", "run_dir"}: exactly what was started
    logs/<ID>.log       the server's stdout/stderr, scrubbed of per-run secrets by the server
    <ID>/ready.json     written by the server once it listens (ports, extra facts)
    <ID>/requests.jsonl metadata-only request log
    <ID>/children.json  exact PIDs of the processes the server started itself
    secrets.env         per-run passwords (mode 0600). Never printed; the runner
                        passes them to Maestro with -e and scrubs them from outputs.

Stopping never matches by pattern (the PC runs the owner's live server): the
PID file's PID is checked against /proc/<pid>/cmdline and its start time before
it gets SIGTERM, then SIGKILL after a grace period. Exit codes: 0 ok, 1 a
fixture failed to start or stop, 2 usage.
"""

from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

from fxlib.core import SCRUB, new_password, stop_pid  # noqa: E402

SERVERS = HERE / "servers"

# Per-run secrets: generated once per run dir, handed to the servers in the
# environment. The names are the contract with the runner (run.py) and flows.
SECRET_NAMES = ("FX_OC2_PASSWORD", "FX_OC2_B_PASSWORD", "FX_GIT_PASSWORD")

READY_TIMEOUT = {"FX-OC2": 120.0, "FX-OC2-B": 150.0, "FX-PASEO": 60.0, "FX-TLS": 60.0}


def script_for(fixture_id: str) -> Path:
    return SERVERS / (fixture_id.lower().replace("-", "_") + ".py")


def known_ids() -> list[str]:
    table = json.loads((HERE / "ports.json").read_text(encoding="utf-8"))
    return [k for k in table if not k.startswith("_") and script_for(k).exists()]


def load_secrets(run_dir: Path) -> dict[str, str]:
    path = run_dir / "secrets.env"
    values: dict[str, str] = {}
    if path.exists():
        for line in path.read_text(encoding="utf-8").splitlines():
            key, sep, value = line.partition("=")
            if sep:
                values[key.strip()] = value.strip()
    changed = False
    for name in SECRET_NAMES:
        if name not in values:
            # A caller-provided value wins over a generated one.
            values[name] = os.environ.get(name) or new_password()
            changed = True
    if changed:
        run_dir.mkdir(parents=True, exist_ok=True)
        fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
        with os.fdopen(fd, "w", encoding="utf-8") as handle:
            handle.writelines(f"{k}={v}\n" for k, v in sorted(values.items()))
    return values


def proc_starttime(pid: int) -> str | None:
    try:
        stat = Path(f"/proc/{pid}/stat").read_text()
    except OSError:
        return None
    return stat.rsplit(")", 1)[1].split()[19]


def proc_cmdline(pid: int) -> list[str]:
    try:
        raw = Path(f"/proc/{pid}/cmdline").read_bytes()
    except OSError:
        return []
    return [part.decode("utf-8", "replace") for part in raw.split(b"\0") if part]


def is_ours(record: dict) -> bool:
    """The PID still names the process we started (not a reused PID)."""
    pid = int(record["pid"])
    cmd = proc_cmdline(pid)
    if not cmd:
        return False
    if record.get("starttime") and proc_starttime(pid) != record["starttime"]:
        return False
    return record["script"] in cmd and record["run_dir"] in cmd


def start(run_dir: Path, ids: list[str], overrides: list[str], timeout: float | None) -> int:
    secrets_env = load_secrets(run_dir)
    for name, value in secrets_env.items():
        SCRUB.add(value)
    (run_dir / "pids").mkdir(parents=True, exist_ok=True)
    (run_dir / "logs").mkdir(parents=True, exist_ok=True)
    failed = 0
    for fixture_id in ids:
        script = script_for(fixture_id)
        if not script.exists():
            print(f"{fixture_id}: no server script {script.name}", file=sys.stderr)
            failed += 1
            continue
        pid_file = run_dir / "pids" / f"{fixture_id}.json"
        if pid_file.exists():
            record = json.loads(pid_file.read_text(encoding="utf-8"))
            if is_ours(record):
                print(f"{fixture_id}: already running pid={record['pid']}")
                continue
            pid_file.unlink()
        ready = run_dir / fixture_id / "ready.json"
        ready.unlink(missing_ok=True)
        argv = [sys.executable, "-I", str(script), "--run-dir", str(run_dir)]
        for item in overrides:
            target, _, port = item.partition(":")
            if target == fixture_id:
                argv += ["--port", port]
        env = dict(os.environ)
        env.update(secrets_env)
        log = (run_dir / "logs" / f"{fixture_id}.log").open("ab")
        proc = subprocess.Popen(argv, stdout=log, stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL,
                                env=env, cwd=str(run_dir), start_new_session=True)
        record = {"pid": proc.pid, "starttime": None, "script": str(script), "run_dir": str(run_dir)}
        for _ in range(50):
            record["starttime"] = proc_starttime(proc.pid)
            if record["starttime"]:
                break
            time.sleep(0.01)
        pid_file.write_text(json.dumps(record), encoding="utf-8")
        limit = timeout or READY_TIMEOUT.get(fixture_id, 30.0)
        deadline = time.monotonic() + limit
        ok = False
        while time.monotonic() < deadline:
            if proc.poll() is not None:
                break
            if ready.exists():
                try:
                    if json.loads(ready.read_text(encoding="utf-8")).get("pid") == proc.pid:
                        ok = True
                        break
                except json.JSONDecodeError:
                    pass
            time.sleep(0.1)
        if ok:
            print(f"{fixture_id}: ready pid={proc.pid}")
            continue
        failed += 1
        code = proc.poll()
        reason = f"exited with {code}" if code is not None else f"not ready after {limit:.0f}s"
        print(f"{fixture_id}: failed to start ({reason}); see logs/{fixture_id}.log", file=sys.stderr)
        tail = (run_dir / "logs" / f"{fixture_id}.log").read_text(encoding="utf-8", errors="replace").splitlines()[-15:]
        for line in tail:
            print("  " + SCRUB(line), file=sys.stderr)
        stop_one(run_dir, fixture_id)
    return 1 if failed else 0


def stop_one(run_dir: Path, fixture_id: str) -> bool:
    pid_file = run_dir / "pids" / f"{fixture_id}.json"
    if not pid_file.exists():
        return True
    record = json.loads(pid_file.read_text(encoding="utf-8"))
    ok = True
    if is_ours(record):
        ok = stop_pid(int(record["pid"]), timeout=15.0)
    # Children the server started itself: normally stopped by the server; stop
    # any that outlived it, by exact PID, if the PID still names that program.
    children = run_dir / fixture_id / "children.json"
    if children.exists():
        try:
            rows = json.loads(children.read_text(encoding="utf-8"))
        except json.JSONDecodeError:
            rows = []
        for row in rows:
            cmd = proc_cmdline(int(row["pid"]))
            if cmd and Path(cmd[0]).name == row.get("argv0"):
                ok = stop_pid(int(row["pid"])) and ok
        children.unlink(missing_ok=True)
    if ok:
        pid_file.unlink(missing_ok=True)
        (run_dir / fixture_id / "ready.json").unlink(missing_ok=True)
    print(f"{fixture_id}: {'stopped' if ok else 'STILL RUNNING'}")
    return ok


def stop(run_dir: Path, ids: list[str]) -> int:
    if not ids:
        ids = sorted(p.stem for p in (run_dir / "pids").glob("*.json"))
    results = [stop_one(run_dir, fixture_id) for fixture_id in ids]
    return 0 if all(results) else 1


def status(run_dir: Path) -> int:
    rows = {}
    for pid_file in sorted((run_dir / "pids").glob("*.json")):
        record = json.loads(pid_file.read_text(encoding="utf-8"))
        ready = run_dir / pid_file.stem / "ready.json"
        rows[pid_file.stem] = {
            "pid": record["pid"],
            "running": is_ours(record),
            "ready": json.loads(ready.read_text(encoding="utf-8")) if ready.exists() else None,
        }
    print(json.dumps(rows, indent=1, sort_keys=True))
    return 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("command", choices=["start", "stop", "status", "list"])
    parser.add_argument("ids", nargs="*")
    parser.add_argument("--run-dir", help="per-run directory")
    parser.add_argument("--port", action="append", default=[],
                        help="override a port: ID:name=NUMBER (repeatable)")
    parser.add_argument("--timeout", type=float, help="seconds to wait for ready.json")
    args = parser.parse_args(argv)
    if args.command == "list":
        print("\n".join(known_ids()))
        return 0
    if not args.run_dir:
        parser.error("--run-dir is required")
    run_dir = Path(args.run_dir).resolve()
    unknown = [i for i in args.ids if not script_for(i).exists()]
    if unknown:
        print(f"unknown fixture(s): {', '.join(unknown)}; known: {', '.join(known_ids())}", file=sys.stderr)
        return 2
    if args.command == "start":
        if not args.ids:
            parser.error("name the fixtures to start")
        return start(run_dir, args.ids, args.port, args.timeout)
    if args.command == "stop":
        return stop(run_dir, args.ids)
    return status(run_dir)


if __name__ == "__main__":
    sys.exit(main())
