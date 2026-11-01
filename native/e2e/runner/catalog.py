"""Reads the catalog: the 39 flows of e2e-flows.md and the portable_e2e rows of behaviors.jsonl.

Standard library only. Paths resolve from this file, so the module works from any cwd.
"""
from __future__ import annotations

import json
import pathlib
import re

E2E_DIR = pathlib.Path(__file__).resolve().parent.parent
NATIVE_DIR = E2E_DIR.parent
REPO = NATIVE_DIR.parent
CATALOG_DIR = REPO / "docs/design/native-rewrite-2026-11-01/catalog"
FLOWS_MD = CATALOG_DIR / "e2e-flows.md"
BEHAVIORS = CATALOG_DIR / "behaviors.jsonl"
FLOWS_DIR = E2E_DIR / "flows"

# Plan 14 §4.8: writing order, slice, AVD roles and fixtures per group.
GROUPS = {
    "G2": {"slice": "2", "flows": ["E2E-01", "E2E-03", "E2E-04", "E2E-05", "E2E-10", "E2E-21", "E2E-35"]},
    "G3": {"slice": "3", "flows": ["E2E-02", "E2E-06", "E2E-11", "E2E-17", "E2E-18", "E2E-19", "E2E-30", "E2E-31"]},
    "G4": {"slice": "4", "flows": ["E2E-07", "E2E-08", "E2E-09", "E2E-22", "E2E-23", "E2E-25", "E2E-26", "E2E-27",
                                   "E2E-34", "E2E-36", "E2E-38"]},
    "G5": {"slice": "5", "flows": ["E2E-12", "E2E-13", "E2E-14", "E2E-15", "E2E-16", "E2E-20", "E2E-24", "E2E-29",
                                   "E2E-33", "E2E-37"]},
    "G6": {"slice": "6", "flows": ["E2E-39"]},
    "D6": {"slice": "after-switch", "flows": ["E2E-28", "E2E-32"]},
}

_FLOW_HEAD = re.compile(r"^### (E2E-\d\d) (.+)$")
_PROVES = re.compile(r"^\*\*Proves\.\*\* (.+?) \(\d+\)\.\s*$")
_NEEDS = re.compile(r"^\*\*Needs\.\*\* (.+)$")
_ROW = re.compile(r"B-[A-Z0-9]+-\d{3}")


def load_flows(path: pathlib.Path = FLOWS_MD) -> dict[str, dict]:
    """Returns {flow id: {title, rows (catalog order), needs}} from the '## Flows' section."""
    flows: dict[str, dict] = {}
    current = None
    for line in path.read_text(encoding="utf-8").splitlines():
        m = _FLOW_HEAD.match(line)
        if m:
            current = {"id": m.group(1), "title": m.group(2).strip(), "rows": [], "needs": ""}
            flows[m.group(1)] = current
            continue
        if current is None:
            continue
        m = _PROVES.match(line)
        if m:
            current["rows"] = _ROW.findall(m.group(1))
            continue
        m = _NEEDS.match(line)
        if m:
            current["needs"] = m.group(1).strip()
    return flows


def portable_rows(path: pathlib.Path = BEHAVIORS) -> set[str]:
    rows = set()
    with path.open(encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if line:
                r = json.loads(line)
                if r.get("portable_e2e"):
                    rows.add(r["id"])
    return rows


def group_of(flow_id: str) -> str:
    for name, g in GROUPS.items():
        if flow_id in g["flows"]:
            return name
    raise KeyError(flow_id)
