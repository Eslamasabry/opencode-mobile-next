#!/usr/bin/env python3
"""Reads the JUnit XML under the current directory (native/) and writes report-<name>.json plus a markdown table.

    python3 -I tool/ci/test_report.py <name> <out-dir>      (markdown goes to stdout)

report JSON: {"name", "tests", "failed", "skipped", "failures": [{"id", "module", "message"}], "modules": {mod: [tests, failed]}}
A failure id is "<module> :: <class> > <test>"; the summary job compares ids between two runs.
"""
import glob, json, os, sys, xml.etree.ElementTree as ET

name, out = sys.argv[1], sys.argv[2]
mods, failures, skipped = {}, [], 0
for p in glob.glob('**/build/test-results/**/TEST-*.xml', recursive=True):
    mod = p.split('/build/')[0]
    for tc in ET.parse(p).getroot().iter('testcase'):
        a = mods.setdefault(mod, [0, 0])
        a[0] += 1
        bad = tc.find('failure') if tc.find('failure') is not None else tc.find('error')
        if tc.find('skipped') is not None:
            skipped += 1
        if bad is not None:
            a[1] += 1
            msg = (bad.get('message') or (bad.text or '')).strip().splitlines()
            failures.append({'id': f'{mod} :: {tc.get("classname")} > {tc.get("name")}', 'module': mod,
                             'message': (msg[0] if msg else '')[:200]})
tests = sum(v[0] for v in mods.values())
report = {'name': name, 'tests': tests, 'failed': len(failures), 'skipped': skipped, 'failures': failures, 'modules': mods,
          'build_failed': os.environ.get('GRADLE_OUTCOME') == 'failure'}
os.makedirs(out, exist_ok=True)
json.dump(report, open(os.path.join(out, f'report-{name}.json'), 'w'))
print(f'**{name}**: {tests} tests, {len(failures)} failed, {skipped} skipped\n')
if mods:
    print('| module | tests | failed |\n|---|---|---|')
    for m, (t, f) in sorted(mods.items(), key=lambda kv: (-kv[1][1], kv[0])):
        print(f'| {m} | {t} | {f} |')
for f in failures[:25]:
    print(f'\n- `{f["id"]}`: {f["message"]}')
if len(failures) > 25:
    print(f'\n... and {len(failures) - 25} more (see the report artifact)')
