#!/usr/bin/env python3
"""tables.py <strict tally.json> <as-gated tally.json>: markdown tables for the scoreboard."""
import json, sys
from collections import defaultdict

strict = json.load(open(sys.argv[1]))
gated = {(r['order'], r['shot']): r for r in json.load(open(sys.argv[2]))}
DAILY = {'41': 10, '42': 10, '43': 10, '14': 9, '15': 9, '16': 8, '17': 8, '11': 8, '12': 8, '53': 8, '44': 7,
         '13': 7, '01': 7, '08': 6, '09': 6, '10': 6, '05': 6, '06': 6, '07': 6, '45': 5, '40': 5, '24': 5, '22': 5}
SURFACE = {'41': 'home', '42': 'chat', '43': 'composer', '14': 'chat kit', '15': 'chat kit', '16': 'chat kit',
           '17': 'chat kit', '11': 'sheets', '12': 'sheets', '53': 'shell', '44': 'settings', '13': 'frame/nav',
           '01': 'foundation', '45': 'servers', '40': 'setup', '05': 'controls', '06': 'controls', '07': 'controls', '08': 'rows', '09': 'rows', '10': 'rows'}


def c(rs):
    k = defaultdict(int)
    for r in rs:
        st = r['status']
        if st == 'FAIL' and r['unrendered']:
            k['unrendered'] += 1
        elif st == 'FAIL':
            k['fail'] += 1
        else:
            k[st] += 1
    for r in rs:
        g = gated.get((r['order'], r['shot']))
        if g and g['status'] == 'DEFER':
            k['defer_gated'] += 1
    return k


def row(name, rs):
    k = c(rs)
    n = len(rs)
    rendered = n - k['unrendered']
    return (f"| {name} | {n} | {k['PASS']} | {k['fail']} | {k['unrendered']} | {k['WAIVED']} | {k['defer_gated']} | "
            f"{100 * k['PASS'] / n:.1f}% | {(100 * k['PASS'] / rendered) if rendered else 0:.1f}% |")


H = ('| {} | goldens | pass | fail (rendered) | fail (no gate case rendered it) | waived | deferred as gated¹ | '
     'pass / total | pass / rendered |\n|---|---:|---:|---:|---:|---:|---:|---:|---:|')
by = defaultdict(list)
for r in strict:
    by[r['order']].append(r)
print('## Per order\n')
print(H.format('order'))
for o in sorted(by):
    print(row(o, by[o]))
print(row('**all orders**', strict))
print('\n## Per Gradle module\n')
bm = defaultdict(list)
for r in strict:
    bm[r['module']].append(r)
print(H.format('module'))
for m in sorted(bm, key=lambda m: -len(bm[m])):
    print(row(f'`{m}`', bm[m]))
print('\n## Per part / screen gallery\n')
bp = defaultdict(list)
for r in strict:
    bp[(r['order'], r['id'])].append(r)
print(H.format('order · tracker id'))
for k in sorted(bp):
    print(row(f'{k[0]} · `{k[1]}`', bp[k]))
print('\n## Worst measured diffs, ranked by daily-path visibility\n')
print('Rendered failing shots only. Order: visibility weight of the surface (home, chat, composer, sheets, settings first), '
      'then box %. One line per golden stem (the worse mode), at most three per part or gallery.\n')
print('| rank | surface | order | golden (worse mode) | tier | box % | blob px | ink ratio | line moved px |\n|---:|---|---|---|---|---:|---:|---:|---:|')
stems = {}
for r in strict:
    if r['status'] != 'FAIL' or r['unrendered'] or not r['rendered']:
        continue
    stem = r['shot'].rsplit('_', 1)[0]
    key = (r['order'], stem)
    if key not in stems or (r['box'] or 0) > (stems[key]['box'] or 0):
        stems[key] = r
ranked = sorted(stems.values(), key=lambda r: (-DAILY.get(r['order'], 1), -(r['box'] or 0)))
per, kept = defaultdict(int), []
for r in ranked:
    per[(r['order'], r['id'])] += 1
    if per[(r['order'], r['id'])] <= 3:
        kept.append(r)
for i, r in enumerate(kept[:70], 1):
    ink = '' if r['ink'] is None else (f"{r['ink']:.2f}" if r['ink'] < 100 else 'n/a²')
    print(f"| {i} | {SURFACE.get(r['order'], 'other')} | {r['order']} | `{r['shot']}` | {r['tier']} | {(r['box'] or 0):.2f} | "
          f"{int(r['blob']) if r['blob'] is not None else ''} | {ink} | {r['moved'] if r['moved'] is not None else ''} |")

# RC1 gate G2: daily path >= 90 % of goldens rendered and passing, everything >= 75 %.
print('\n## RC1 gate G2 (daily path ≥ 90 % rendered and passing; all ≥ 75 %)\n')
print('| surface | goldens | rendered | pass | pass / total | G2 |\n|---|---:|---:|---:|---:|---|')
DP = [('home (41)', lambda r: r['order'] == '41'), ('chat (42)', lambda r: r['order'] == '42'),
      ('composer (43)', lambda r: r['order'] == '43'), ('sheets (11, 12)', lambda r: r['order'] in ('11', '12')),
      ('settings hub (44 `settings_hub_*`)', lambda r: r['order'] == '44' and r['shot'].startswith('settings_hub'))]
allday = []
for name, f in DP:
    rs = [r for r in strict if f(r)]
    allday += rs
    p = sum(r['status'] == 'PASS' for r in rs); rd = sum(not r['unrendered'] for r in rs)
    print(f"| {name} | {len(rs)} | {rd} | {p} | {100 * p / len(rs):.1f}% | {'pass' if p >= .9 * len(rs) else 'FAIL'} |")
p = sum(r['status'] == 'PASS' for r in allday); rd = sum(not r['unrendered'] for r in allday)
print(f"| **daily path** | {len(allday)} | {rd} | {p} | {100 * p / len(allday):.1f}% | {'pass' if p >= .9 * len(allday) else 'FAIL'} (≥ 90 %) |")
p = sum(r['status'] == 'PASS' for r in strict); rd = sum(not r['unrendered'] for r in strict)
print(f"| **all goldens** | {len(strict)} | {rd} | {p} | {100 * p / len(strict):.1f}% | {'pass' if p >= .75 * len(strict) else 'FAIL'} (≥ 75 %) |")
