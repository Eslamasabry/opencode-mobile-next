#!/usr/bin/env python3
"""Count catalog rows proven by passing tests: a row is proven when a passing JUnit test case's name starts with
its id (desktopTest / testDebugUnitTest XML under native/**/build/test-results). Usage:
  python3 -I native/tool/parity/rows_proven.py [ROW-ID ...]   (no ids: every row seen)"""
import glob, os, re, sys
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
ID = re.compile(r"^(B-[A-Z]+-\d{3})\b")
passed, failed = {}, {}
for path in glob.glob(os.path.join(ROOT, "**", "build", "test-results", "**", "TEST-*.xml"), recursive=True):
    try:
        tree = ET.parse(path)
    except ET.ParseError:
        continue
    for case in tree.iter("testcase"):
        m = ID.match(case.get("name", "").strip())
        if not m:
            continue
        bad = case.find("failure") is not None or case.find("error") is not None
        skipped = case.find("skipped") is not None
        target = failed if bad else (None if skipped else passed)
        if target is not None:
            target.setdefault(m.group(1), []).append(case.get("classname", "") + "." + case.get("name"))
wanted = sys.argv[1:] or sorted(set(passed) | set(failed))
proven = [r for r in wanted if r in passed and r not in failed]
print(f"proven {len(proven)}/{len(wanted)}")
for r in wanted:
    state = "proven" if r in proven else ("FAILING" if r in failed else "missing")
    print(f"{r}\t{state}\t{len(passed.get(r, []))} passing")
