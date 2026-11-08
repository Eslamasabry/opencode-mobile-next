#!/usr/bin/env python3
"""Summarise the alignment gate's JVM findings (looks-out/align/<module>/<shot>.tsv) as Markdown.

  python3 -I native/tool/design/align_report.py [--dir looks-out/align] [--top 30] > report.md

Counts per rule and per module, the shots ranked by findings, the top N findings with their numbers (worst first:
the furthest off a column or step), and each screen's distinct start edges. Standard library only.
"""
import argparse, glob, os, re, sys

RULES = ['start-edge', 'gap-step', 'icon-label', 'end-edge', 'target', 'ellipsis-frame', 'clip', 'icon-size',
         'icon-squeezed', 'row-height']
# How bad a finding is, from its detail: the dp it is off by (start edges: distance to the nearest column).
COLUMNS = [16.0, 20.0, 48.0, 52.0, 56.0, 60.0]


def severity(rule, detail):
    nums = [float(x) for x in re.findall(r'-?\d+\.\d', detail)]
    if not nums:
        return 0.0
    if rule == 'start-edge':
        return min(abs(nums[0] - c) for c in COLUMNS)
    if rule == 'gap-step':
        g = nums[0]
        return min(abs(g - round(g / 4) * 4), abs(g - 2))
    if rule in ('icon-label',):
        return nums[-1]
    if rule in ('end-edge', 'row-height'):
        return abs(nums[0] - nums[1]) if len(nums) > 1 else 0.0
    if rule == 'target':
        return 48 - min(nums[0], nums[1]) if len(nums) > 1 else 0.0
    if rule == 'icon-squeezed':
        return nums[0] - min(nums[1], nums[2]) if len(nums) > 2 else 0.0
    if rule == 'icon-size':
        return min(abs(nums[0] - s) for s in (16, 20, 22, 24, 28))
    if rule == 'clip':
        return nums[4] if len(nums) > 4 else 0.0
    return 1.0


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--dir', default='looks-out/align')
    ap.add_argument('--top', type=int, default=30)
    a = ap.parse_args()
    rows = []
    edges = {}
    for path in sorted(glob.glob(os.path.join(a.dir, '*', '*.tsv'))):
        module = os.path.basename(os.path.dirname(path))
        if path.endswith('.edges.tsv'):
            shot = os.path.basename(path)[:-len('.edges.tsv')]
            with open(path, encoding='utf-8') as f:
                e = [l.rstrip('\n').split('\t') for l in f if l.strip()]
            if e:
                edges[(module, shot)] = [(float(x), int(n)) for x, n in e]
            continue
        with open(path, encoding='utf-8') as f:
            for line in f:
                p = line.rstrip('\n').split('\t')
                if len(p) >= 4:
                    rows.append((module, p[0], p[1], p[2], p[3]))
    shots = {(m, s) for m, s, *_ in rows} | set(edges)
    out = [f'{len(rows)} findings in {len({(m, s) for m, s, *_ in rows})} of {len(shots)} shots with findings or edges.', '']
    out += ['## Counts per rule', '', '| Rule | Findings | Shots |', '|---|---:|---:|']
    for r in RULES:
        rr = [x for x in rows if x[2] == r]
        out.append(f'| `{r}` | {len(rr)} | {len({(x[0], x[1]) for x in rr})} |')
    out += ['', '## Per module', '', '| Module | Findings | ' + ' | '.join(f'`{r}`' for r in RULES) + ' |',
            '|---|---:|' + '---:|' * len(RULES)]
    mods = sorted({x[0] for x in rows}, key=lambda m: -sum(1 for x in rows if x[0] == m))
    for m in mods:
        mr = [x for x in rows if x[0] == m]
        out.append(f'| {m} | {len(mr)} | ' + ' | '.join(str(sum(1 for x in mr if x[2] == r)) for r in RULES) + ' |')
    out += ['', '## Shots ranked by findings', '', '| # | Module | Shot | Findings | Rules |', '|---:|---|---|---:|---|']
    per = {}
    for x in rows:
        per.setdefault((x[0], x[1]), []).append(x[2])
    for i, ((m, s), rs) in enumerate(sorted(per.items(), key=lambda kv: -len(kv[1])), 1):
        counts = ', '.join(f'{r} {rs.count(r)}' for r in RULES if r in rs)
        out.append(f'| {i} | {m} | `{s}` | {len(rs)} | {counts} |')
    out += ['', f'## Top {a.top} findings (furthest off first)', '', '| # | Module | Shot | Rule | Subject | Measured |',
            '|---:|---|---|---|---|---|']
    ranked = sorted(rows, key=lambda x: (-severity(x[2], x[4]), x[0], x[1]))
    for i, (m, s, r, subj, det) in enumerate(ranked[:a.top], 1):
        out.append(f'| {i} | {m} | `{s}` | `{r}` | {subj.replace("|", "/")} | {det.replace("|", "/")} |')
    out += ['', '## Start edges per screen (dp: count)', '', '| Module | Shot | Edges |', '|---|---|---|']
    for (m, s), e in sorted(edges.items()):
        out.append(f'| {m} | `{s}` | ' + ', '.join(f'{x:g}: {n}' for x, n in e) + ' |')
    print('\n'.join(out))
    return 0


if __name__ == '__main__':
    sys.exit(main())
