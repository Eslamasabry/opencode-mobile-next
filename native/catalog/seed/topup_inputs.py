"""List the Flutter logic tests the catalog top-up must read (plan 02 §3.5; S0-SCOPE).

Usage (from the repo root): python3 -I native/catalog/seed/topup_inputs.py <out-dir>
Writes three TSV files:
  uncited.tsv  tests in the catalog input (taken at 65e3f70a3) that no behaviors.jsonl row cites;
  new.tsv      logic tests in today's test/ tree whose name is not in the catalog input (new file or new name);
  changed.tsv  tests with an unchanged name whose body differs from the public tag e44b581aa.
65e3f70a3 (batch1) is not in this repository, so `changed` is measured against e44b581aa, the closest ancestor here
(over-approximate: it also lists changes made between the tag and batch1). Widget tests (`testWidgets(`) are out
of scope (NG12).
"""
import collections
import glob
import json
import os
import re
import subprocess
import sys

PLAN = 'docs/design/native-rewrite-2026-11-01'
TEST = re.compile(r'(?<![\w.])test\(\s*(?:\n\s*)?(r?)([\'"])((?:\\.|(?!\2).)*)\2', re.S)
BASE_TAG = 'e44b581aa'


def norm(name):
    return ' '.join(name.split())[:58]  # the catalog input truncates long names


def catalog_input():
    tests, cur = [], None
    for path in sorted(glob.glob(f'{PLAN}/catalog-input/chunk-*.txt')):
        for line in open(path, encoding='utf-8'):
            line = line.rstrip('\n')
            if line.startswith('## '):
                cur = line[3:]
                continue
            parts = line.split('\t')
            if len(parts) >= 3 and parts[1] == 'test':
                tests.append((cur, int(parts[0]), parts[2]))
    return tests


def bodies(src):
    ms = list(TEST.finditer(src))
    out = {}
    for i, m in enumerate(ms):
        end = ms[i + 1].start() if i + 1 < len(ms) else len(src)
        out[norm(m.group(3))] = (src.count('\n', 0, m.start()) + 1, ' '.join(src[m.start():end].split()))
    return out


def head_tests():
    out = []
    for d, _, files in os.walk('test'):
        for f in files:
            if f.endswith('_test.dart'):
                p = os.path.join(d, f)
                for name, (line, _) in bodies(open(p, encoding='utf-8', errors='replace').read()).items():
                    out.append((p, line, name))
    return out


def main(out_dir):
    os.makedirs(out_dir, exist_ok=True)
    rows = [json.loads(l) for l in open(f'{PLAN}/catalog/behaviors.jsonl', encoding='utf-8')]
    cited = {s for r in rows for s in r['sources']}
    cites = collections.defaultdict(list)
    for r in rows:
        for s in r['sources']:
            cites[s].append(r['id'])
    tests = catalog_input()
    old = collections.defaultdict(set)
    for f, _, n in tests:
        old[f].add(norm(n))
    head = head_tests()
    by_head = {(f, n): l for f, l, n in head}

    with open(os.path.join(out_dir, 'uncited.tsv'), 'w', encoding='utf-8') as w:
        w.write('# file\tbatch1_line\thead_line\tname\n')
        for f, l, n in tests:
            if f'{f}:{l}' not in cited:
                w.write(f"{f}\t{l}\t{by_head.get((f, norm(n)), '?')}\t{n}\n")

    def known(f, n):
        return any(n.startswith(o) or o.startswith(n) for o in old.get(f, ()))

    with open(os.path.join(out_dir, 'new.tsv'), 'w', encoding='utf-8') as w:
        w.write('# file\thead_line\tname\tkind\n')
        for f, l, n in sorted(head):
            if f not in old:
                w.write(f'{f}\t{l}\t{n}\tnew-file\n')
            elif not known(f, n):
                w.write(f'{f}\t{l}\t{n}\tnew-in-old-file\n')

    batch1 = {(f, norm(n)): l for f, l, n in tests}
    with open(os.path.join(out_dir, 'changed.tsv'), 'w', encoding='utf-8') as w:
        w.write('# file\thead_line\tbatch1_line\tciting_rows\tname\n')
        for f in sorted({f for f, _, _ in head}):
            r = subprocess.run(['git', 'show', f'{BASE_TAG}:{f}'], capture_output=True, text=True)
            if r.returncode != 0:
                continue
            new_src = open(f, encoding='utf-8', errors='replace').read()
            if r.stdout == new_src:
                continue
            before, after = bodies(r.stdout), bodies(new_src)
            for n, (line, body) in after.items():
                if n in before and before[n][1] != body:
                    bl = batch1.get((f, n))
                    w.write(f"{f}\t{line}\t{bl}\t{','.join(cites.get(f'{f}:{bl}', [])) or '-'}\t{n}\n")


if __name__ == '__main__':
    main(sys.argv[1])
