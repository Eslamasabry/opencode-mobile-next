#!/usr/bin/env python3
"""Require device-tested Claude Code and Paseo pins before cutting a release."""
import json
import sys
from pathlib import Path


def main():
    source = Path(__file__).resolve().parents[2] / "engine/agents/pins/agent-pins.json"
    pins = json.loads(source.read_text(encoding="utf-8"))
    claude, = (agent for agent in pins["agents"] if agent["id"] == "claude")
    pairs = (
        ("Claude Code", claude["recipe"]["version"], pins["claudeCodeTestedVersions"]),
        ("Paseo", pins["paseo"]["version"], pins["paseoTestedVersions"]),
    )
    failed = False
    for name, version, tested in pairs:
        if version not in tested:
            print(f"{name} {version} is pinned but not in the tested list: prove it on a device, then add it", file=sys.stderr)
            failed = True
    return int(failed)


if __name__ == "__main__":
    sys.exit(main())
