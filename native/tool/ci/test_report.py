#!/usr/bin/env python3
"""Reads the JUnit XML under the current directory (native/) and writes report-<name>.json plus a markdown table.

    python3 -I tool/ci/test_report.py <name> <out-dir>      (markdown goes to stdout)

report JSON: {"name", "tests", "failed", "skipped", "failures": [{"id", "module", "message"}], "modules": {mod: [tests, failed]}}
A failure id is "<module> :: <class> > <test>"; the summary job compares ids between two runs.

Gradle log (env GRADLE_LOG, the shard's tee'd output) and the shard's task list (env TASKS, space separated) add
"real_failures": strings the gate never treats as noise:
  * a failed task that is not a test task reporting "There were failing tests" (test compile, resources, strings
    generation, drift checks, detekt, a custom check ...), or a test task that failed with no failing test in any report;
  * a selected test task that never ran ("module X: no test results"), for instance because its test sources did not compile;
  * `FAILURE: Build failed` / a build that failed with no failed task named at all.
"""
import glob, json, os, re, sys, xml.etree.ElementTree as ET

name, out = sys.argv[1], sys.argv[2]
mods, failures, skipped = {}, [], 0
selected = {'/'.join(t.strip(':').split(':')[:-1]) for t in os.environ.get('TASKS', '').split()}
for p in glob.glob('**/build/test-results/**/TEST-*.xml', recursive=True):
    mod = p.split('/build/')[0]
    if selected and mod not in selected:
        continue  # a warm runner keeps other shards' results in build/: only this shard's modules count
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

def log_lines():
    path = os.environ.get('GRADLE_LOG', '')
    try:
        return open(path, errors='replace').read().splitlines() if path else []
    except OSError:
        return []


def clean(line):
    return re.sub(r'^\S+Z\s+', '', re.sub(r'\x1b\[[0-9;]*m', '', line)).rstrip()  # drop a timestamp and colours


def analyse(lines, mods, expected):
    """-> real failure strings from the Gradle log; see the module docstring."""
    real, lines = [], [clean(l) for l in lines]
    task_status = {}
    for l in lines:
        m = re.match(r'^> Task (:\S+?)(?: ([A-Z-]+))?$', l)
        if m:
            task_status[m.group(1)] = m.group(2) or 'RAN'
    failed, covered = set(), set()
    for i, l in enumerate(lines):
        m = re.search(r"Execution failed for task '(:[^']+)'", l)
        if m:
            failed.add(m.group(1))
            if any('There were failing tests' in x for x in lines[i + 1:i + 4]):
                covered.add(m.group(1))
    failed |= {t for t, st in task_status.items() if st == 'FAILED'}
    for t in sorted(failed):
        mod = '/'.join(t.strip(':').split(':')[:-1])
        if t in covered and mods.get(mod, [0, 0])[1] > 0:
            continue  # a test task whose failures are in the reports: the known-noise list may judge them
        real.append(f'task {t} failed' + (' with no failing test in any report' if t in covered else ' (not a test failure)'))
    for t in expected:
        mod = '/'.join(t.strip(':').split(':')[:-1])
        if t not in task_status and t not in failed:
            real.append(f'module {mod}: no test results ({t} never ran)')
    if not failed and any(l.startswith('FAILURE: Build failed') for l in lines):
        real.append('the build failed without naming a task (configuration or script error)')
    return real


def gradle_ran(lines):
    return any(re.match(r'^(\S+Z\s+)?(\x1b\[[0-9;]*m)*(> Task :|BUILD (SUCCESSFUL|FAILED))', l) for l in lines)


real_failures = analyse(log_lines(), mods, os.environ.get('TASKS', '').split())
if os.environ.get('GRADLE_OUTCOME') == 'failure' and not failures and not real_failures:
    real_failures.append('the Gradle run failed and left no failing test and no readable task failure')
tests = sum(v[0] for v in mods.values())
report = {'name': name, 'tests': tests, 'failed': len(failures), 'skipped': skipped, 'failures': failures, 'modules': mods,
          'build_failed': os.environ.get('GRADLE_OUTCOME') == 'failure', 'real_failures': real_failures,
          # integrity facts for summary.py: did this shard plan work, and did Gradle really start?
          'planned': os.environ.get('TASKS', '').split(), 'gradle_ran': gradle_ran(log_lines()),
          'job_status': os.environ.get('JOB_STATUS', ''), 'pick_outcome': os.environ.get('PICK_OUTCOME', '')}
os.makedirs(out, exist_ok=True)
json.dump(report, open(os.path.join(out, f'report-{name}.json'), 'w'))
print(f'**{name}**: {tests} tests, {len(failures)} failed, {skipped} skipped\n')
if mods:
    print('| module | tests | failed |\n|---|---|---|')
    for m, (t, f) in sorted(mods.items(), key=lambda kv: (-kv[1][1], kv[0])):
        print(f'| {m} | {t} | {f} |')
for r in real_failures:
    print(f'\n- **REAL**: {r}')
for f in failures[:25]:
    print(f'\n- `{f["id"]}`: {f["message"]}')
if len(failures) > 25:
    print(f'\n... and {len(failures) - 25} more (see the report artifact)')
