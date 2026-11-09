#!/usr/bin/env python3
"""bench_summary.py <out-dir>: print a markdown table of the Macrobenchmark *-benchmarkData.json files under <out-dir>."""
import glob, json, os, sys

out = sys.argv[1]
rows = []
for path in sorted(glob.glob(os.path.join(out, "**", "*benchmarkData.json"), recursive=True)):
    data = json.load(open(path))
    dev = data.get("context", {}).get("build", {})
    for b in data.get("benchmarks", []):
        for metric, m in sorted(b.get("metrics", {}).items()):
            rows.append((b["name"], metric, m.get("minimum"), m.get("median"), m.get("maximum"), dev.get("model", "")))
print("| test | metric | min | p50 | max | device |")
print("|---|---|---|---|---|---|")
for r in rows:
    print("| %s | %s | %s | %s | %s | %s |" % tuple("-" if v is None else (round(v, 1) if isinstance(v, float) else v) for v in r))
if not rows:
    print("| (no benchmarkData.json) | | | | | |")
