#!/usr/bin/env python3
"""fx.py - the runner's fixture contract (S0-FX): start what a flow set needs, plus the FX-CTL dispatcher.

    python3 -I native/e2e/fixtures/fx.py start --ids FX-OC2,FX-TLS,fx-proxy --state STATE.json [--run-dir DIR]
    python3 -I native/e2e/fixtures/fx.py stop  --state STATE.json

start: starts each named fixture through fxctl.py (exact PIDs, per-run secrets in DIR/secrets.env) and FX-CTL,
the address flows' `_fx/fx.js` posts to, then writes STATE (mode 0600):

    {"run_dir": DIR, "ids": [...], "pids": [...],
     "env": {"SERVER_PASSWORD": ..., "SERVER_B_PASSWORD": ..., "FX_CTL": "http://127.0.0.1:8898"}}

`env` is what run.py hands to Maestro (passwords with -e, scrubbed from every output). `fx-proxy` names the
fixtures' own front proxies (FX-CTL serves its actions), so it starts nothing by itself. DIR defaults to a
`fx` folder next to STATE. Exit 0 when everything is ready; otherwise what did start is stopped again and the
exit code is 1.

stop: stops every fixture started in DIR (FX-CTL included), each by exact PID (fxctl.py's checks).
"""

from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import fxctl  # noqa: E402

DISPATCHER = "FX-CTL"
NO_SCRIPT = {"fx-proxy"}  # served by FX-CTL


def write_state(path: Path, data: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(fd, "w", encoding="utf-8") as handle:
        json.dump(data, handle, indent=1)


def start(ids: list[str], state: Path, run_dir: Path) -> int:
    known = set(fxctl.known_ids())
    unknown = [i for i in ids if i not in known and i not in NO_SCRIPT]
    if unknown:
        print(f"fx.py: no fixture script for {', '.join(unknown)}", file=sys.stderr)
        return 1
    wanted = [i for i in ids if i in known and i != DISPATCHER] + [DISPATCHER]
    rc = fxctl.start(run_dir, wanted, [], None)
    if rc != 0:
        fxctl.stop(run_dir, [])
        return 1
    secrets = fxctl.load_secrets(run_dir)
    ctl = json.loads((run_dir / DISPATCHER / "ready.json").read_text(encoding="utf-8"))
    pids = []
    for fixture_id in wanted:
        record = json.loads((run_dir / "pids" / f"{fixture_id}.json").read_text(encoding="utf-8"))
        pids.append(int(record["pid"]))
    write_state(state, {
        "run_dir": str(run_dir),
        "ids": wanted,
        "pids": pids,
        "env": {"SERVER_PASSWORD": secrets["FX_OC2_PASSWORD"], "SERVER_B_PASSWORD": secrets["FX_OC2_B_PASSWORD"],
                "GIT_PASSWORD": secrets["FX_GIT_PASSWORD"], "FX_CTL": ctl["url"]},
    })
    return 0


def stop(state: Path) -> int:
    try:
        data = json.loads(state.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        print(f"fx.py: no readable state at {state}", file=sys.stderr)
        return 1
    return fxctl.stop(Path(data["run_dir"]), [])


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("command", choices=["start", "stop"])
    parser.add_argument("--ids", default="", help="comma list of fixture ids (flow header names)")
    parser.add_argument("--state", required=True, type=Path)
    parser.add_argument("--run-dir", type=Path, help="fixture run dir (default: <state dir>/fx)")
    args = parser.parse_args(argv)
    if args.command == "stop":
        return stop(args.state)
    ids = [i.strip() for i in args.ids.split(",") if i.strip() and i.strip() != "none"]
    run_dir = (args.run_dir or args.state.parent / "fx").resolve()
    return start(ids, args.state.resolve(), run_dir)


if __name__ == "__main__":
    sys.exit(main())
