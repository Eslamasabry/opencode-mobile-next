#!/usr/bin/env python3
"""Tally scoreboard results: tally.py <worktree> <score dir> -> TSV on stdout + json dump.
Per golden: order, part/gallery id, module, tier, status, why, box, blob, ink, shift."""
import glob, json, os, re, sys

wt, score = sys.argv[1:3]
logdir = sys.argv[3] if len(sys.argv) > 3 else score
out_name = sys.argv[4] if len(sys.argv) > 4 else 'tally.json'
orders_dir = os.path.join(wt, 'docs/design/native-ui-rebuild/orders')
out = []
for man in sorted(glob.glob(os.path.join(orders_dir, '*.gate.tsv'))):
    nn = os.path.basename(man)[:-9]
    goldens = {}
    for line in open(man, encoding='utf-8'):
        c = line.rstrip('\n').split('\t')
        if c[0] == 'golden':
            goldens[os.path.basename(c[3])[:-4]] = (c[1], c[2], c[4], c[5], c[3])
    if not goldens:
        continue
    log = os.path.join(logdir, f'{nn}.log')
    res = {}
    ran = os.path.exists(log)
    gradle_rc = None
    if ran:
        for line in open(log, encoding='utf-8', errors='replace'):
            m = re.match(r'^(PASS|FAIL|WAIVED|DEFER)\s+(\S+)\s+(.*)$', line.rstrip('\n'))
            if m and m.group(2) in goldens:
                res[m.group(2)] = (m.group(1), m.group(3))
            m2 = re.search(r'evaluate after gate exit (\d+)', line)
            if m2:
                gradle_rc = int(m2.group(1))
    for shot, (pid, ref, tier, mod, path) in goldens.items():
        st, why = res.get(shot, ('NOTRUN' if not ran else 'FAIL', 'no evaluation line'))
        g = lambda rx: (float(re.search(rx, why).group(1)) if re.search(rx, why) else None)
        nat = os.path.exists(os.path.join(score, nn, f'{shot}-native.png'))
        out.append(dict(order=nn, id=pid, ref=ref, tier=tier, module=mod, shot=shot, status=st, why=why,
                        box=g(r'box ([\d.]+)%'), blob=g(r'blob (\d+) px'), ink=g(r'ink ([\d.]+)x'),
                        moved=g(r'moved ([\d.]+) px'), rendered=nat, gradle_rc=gradle_rc,
                        unrendered='no gate row' in why))
json.dump(out, open(os.path.join(score, out_name), 'w'), indent=0)
print(len(out), 'goldens tallied')
