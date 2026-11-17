#!/usr/bin/env python3
"""Reads the JUnit XML under the current directory (native/) and writes report-<name>.json plus a markdown table.

    python3 -I tool/ci/test_report.py <name> <out-dir>      (markdown goes to stdout)

report JSON: {"name", "tests", "failed", "skipped", "guards_skipped", "failures": [{"id", "module", "message"}], "modules": {mod: [tests, failed]}}
A failure id is "<module> :: <class> > <test>"; the summary job compares ids between two runs.

Gradle log (env GRADLE_LOG, the shard's tee'd output) and the shard's task list (env TASKS, space separated) add
"real_failures": strings the gate never treats as noise:
  * a failed task that is not a test task reporting "There were failing tests" (test compile, resources, strings
    generation, drift checks, detekt, a custom check ...), or a test task that failed with no failing test in any report;
  * a selected test task that never ran ("module X: no test results"), for instance because its test sources did not compile;
  * `FAILURE: Build failed` / a build that failed with no failed task named at all.
"""
import glob, json, os, re, shlex, sys, xml.etree.ElementTree as ET

GUARD_MARKER = 'OC-GUARD-SKIPPED'


def log_lines():
    path = os.environ.get('GRADLE_LOG', '')
    try:
        return open(path, errors='replace').read().splitlines() if path else []
    except OSError:
        return []


def clean(line):
    return re.sub(r'^\S+Z\s+', '', re.sub(r'\x1b\[[0-9;]*m', '', line)).rstrip()  # drop a timestamp and colours


lines = log_lines()
task_status = {}
for line in lines:
    match = re.match(r'^> Task (:\S+?)(?: ([A-Z-]+))?$', clean(line))
    if match:
        task_status[match[1]] = match[2] or 'RAN'

name, out = sys.argv[1], sys.argv[2]
mods, failures, skipped = {}, [], 0
guard_skips = []  # `OC-GUARD-SKIPPED <what>` lines the guard tests print (ReferenceGuard): a guard that could not run
suites, picture_tests = {}, 0
arguments = shlex.split(os.environ.get('TASKS', ''))
expected_tasks = [t for t in arguments if t.startswith(':')]
selected = {'/'.join(t.strip(':').split(':')[:-1]) for t in expected_tasks}
security_counts = [0, 0, 0]
quality_counts = [0, 0, 0]
for p in glob.glob('**/build/test-results/**/TEST-*.xml', recursive=True):
    mod = p.split('/build/')[0]
    if selected and mod not in selected:
        continue  # a warm runner keeps other shards' results in build/: only this shard's modules count
    suite = p.split('/test-results/', 1)[1].split('/')[0]
    task = ':' + mod.replace('/', ':') + ':' + suite
    if os.environ.get('LANE') == 'candidate' and task not in task_status:
        continue  # other suites in this module may be stale outputs on a warm runner
    count = suites.setdefault(task, {'tests': 0, 'failed': 0, 'skipped': 0})
    root = ET.parse(p).getroot()
    for out_el in root.iter('system-out'):
        guard_skips += [l.strip() for l in (out_el.text or '').splitlines() if l.strip().startswith(GUARD_MARKER)]
    for tc in root.iter('testcase'):
        count['tests'] += 1
        is_quality = mod == 'quality' and suite == 'test'
        if is_quality:
            quality_counts[0] += 1
        if tc.find('skipped') is None and re.search(r'Golden|\.rb\.|LooksCapture', tc.get('classname', '')):
            picture_tests += 1
        a = mods.setdefault(mod, [0, 0])
        a[0] += 1
        is_security = '/quality/' in '/' + p and '/test-results/secTest/' in p.replace(os.sep, '/')
        if is_security:
            security_counts[0] += 1
        bad = tc.find('failure') if tc.find('failure') is not None else tc.find('error')
        if tc.find('skipped') is not None:
            skipped += 1
            count['skipped'] += 1
            if is_quality:
                quality_counts[2] += 1
            if is_security:
                security_counts[2] += 1
        if bad is not None:
            a[1] += 1
            count['failed'] += 1
            if is_quality:
                quality_counts[1] += 1
            if is_security:
                security_counts[1] += 1
            msg = (bad.get('message') or (bad.text or '')).strip().splitlines()
            failures.append({'id': f'{mod} :: {tc.get("classname")} > {tc.get("name")}', 'module': mod,
                             'message': (msg[0] if msg else '')[:200]})

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
            dependency = t.replace(':verifyRoborazziAndroidHostTest', ':testAndroidHostTest')
            if dependency != t and task_status.get(dependency) == 'FAILED' and dependency in covered and mods.get(mod, [0, 0])[1] > 0:
                continue  # verification's Test ran and failed; classify its assertions, not the blocked lifecycle task
            real.append(f'module {mod}: no test results ({t} never ran)')
    if not failed and any(l.startswith('FAILURE: Build failed') for l in lines):
        real.append('the build failed without naming a task (configuration or script error)')
    return real


def gradle_ran(lines):
    return any(re.match(r'^(\S+Z\s+)?(\x1b\[[0-9;]*m)*(> Task :|BUILD (SUCCESSFUL|FAILED))', l) for l in lines)


real_failures = analyse(lines, mods, expected_tasks)
security_status = next((m.group(1) or 'RAN' for line in reversed(lines)
                        if (m := re.match(r'^> Task :quality:secTest(?: ([A-Z-]+))?$', clean(line)))), '')
current_task, filtered_security = '', False
for argument in arguments:
    if argument.startswith(':'):
        current_task = argument
    elif argument == '--tests' and current_task == ':quality:secTest':
        filtered_security = True
security = {'executed': security_status in ('RAN', 'FAILED'), 'full': not filtered_security,
            'tests': security_counts[0], 'failed': security_counts[1], 'skipped': security_counts[2]}

quality = {'executed': task_status.get(':quality:test') in ('RAN', 'FAILED'),
           'full': '--tests' not in arguments, 'tests': quality_counts[0],
           'failed': quality_counts[1], 'skipped': quality_counts[2]}
flags = shlex.split(os.environ.get('TEST_FLAGS', ''))
candidate = {
    'fresh': all(task_status.get(task) in ('RAN', 'FAILED') for task in suites),
    'unfiltered': not any(a in ('--tests', '-x', '--exclude-task') or a.startswith(('--tests=', '--exclude-task=', '-x'))
                          for a in arguments + flags),
    'pictures': all(a in flags for a in ('-PskipGoldens=false', '-PonlyGoldens=false',
                                        '-Poc.recordGoldens=false', '-PrunQuarantined=true')) and
                not any(a.startswith(('-PskipGoldens=', '-PonlyGoldens=', '-Poc.recordGoldens=')) and
                        not a.endswith('=false') for a in arguments + flags),
    'picture_tests': picture_tests,
}

if os.environ.get('GRADLE_OUTCOME') == 'failure' and not failures and not real_failures:
    real_failures.append('the Gradle run failed and left no failing test and no readable task failure')
tests = sum(v[0] for v in mods.values())
report = {'name': name, 'lane': os.environ.get('LANE', ''), 'sha': os.environ.get('GITHUB_SHA', ''),
          'candidate': candidate, 'quality': quality, 'task_status': task_status, 'suites': suites, 'tests': tests, 'failed': len(failures), 'skipped': skipped, 'guards_skipped': len(guard_skips), 'guards_skipped_names': sorted(set(guard_skips))[:50], 'failures': failures, 'modules': mods,
          'build_failed': os.environ.get('GRADLE_OUTCOME') == 'failure', 'real_failures': real_failures,
          # integrity facts for summary.py: did this shard plan work, and did Gradle really start?
          'security': security, 'planned': expected_tasks, 'gradle_ran': gradle_ran(log_lines()),
          'job_status': os.environ.get('JOB_STATUS', ''), 'pick_outcome': os.environ.get('PICK_OUTCOME', '')}
os.makedirs(out, exist_ok=True)
json.dump(report, open(os.path.join(out, f'report-{name}.json'), 'w'))
print(f'**{name}**: {tests} tests, {len(failures)} failed, {skipped} skipped\n')
if guard_skips:
    print(f'{len(guard_skips)} guards skipped (references not fetched)\n')
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
