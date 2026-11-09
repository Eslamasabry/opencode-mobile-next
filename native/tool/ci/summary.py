#!/usr/bin/env python3
"""Final job of Native CI: one short table in the run summary, and the run's gate.

    python3 -I tool/ci/summary.py <reports-dir> <base-reports-dir> <lane>   (markdown to stdout; exit 1 = FAIL)

FAIL (exit 1) means a real problem, and the table starts with FAIL and the reasons. The workflow decides whether that
turns the run red: on work branches the summary job has continue-on-error, so the run stays green and agents read the
FAIL in the table; on native/next, native/screens-merge* and master the run goes red.

Always real, never noise:
  * a compile error, a failed APK / record / custom job (the job's own report says failed);
  * any Gradle task failure that is not a test task reporting failing tests: test compile, resource or strings
    generation, a drift check, detekt, a custom check (test_report.py reads the shard's Gradle log);
  * a selected module that produced no test results;
  * a failing test in a gate module (HARD_MODULES) or a drift / Konsist / detekt / lint class (HARD_CLASS), whatever
    the lists or the base say.
Noise (does not fail): an individual failing test class that also fails in the base reports, matches
tool/ci/known_failures.txt (fnmatch on "<module> :: <class>") or is listed in tool/ci/quarantine.txt. Only test-report
failures can be absorbed this way.

Reads <reports-dir>/**/report-*.json (test shards), compile.json, apk.json, custom.json and record.json written by the
jobs, and diffs failing test ids against <base-reports-dir> (the newest run on the base branch that kept reports).
PLANNED_SHARDS (comma list of shard ids, from the plan job) makes a missing shard report an integrity failure (exit 2).
Job results come from the environment: RESULT_COMPILE, RESULT_TEST, RESULT_APK, RESULT_RECORD, RESULT_CUSTOM.
Tests for this file: tool/ci/test_gate.py.
"""
import fnmatch, glob, json, os, re, sys

reports, base_dir, lane = sys.argv[1], sys.argv[2], sys.argv[3]
env = os.environ.get


def load(pattern, root):
    return [json.load(open(p)) for p in sorted(glob.glob(os.path.join(root, '**', pattern), recursive=True))]


shards = load('report-*.json', reports)
base_shards = load('report-*.json', base_dir)
tests = sum(s['tests'] for s in shards)
failed = [f for s in shards for f in s['failures']]
base_failed = {f['id'] for s in base_shards for f in s['failures']}
here = os.path.dirname(os.path.abspath(__file__))


def patterns(name, quarantine=False):
    out = []
    try:
        for line in open(os.path.join(here, name)):
            line = line.strip()
            if not line or line.startswith('#'):
                continue
            head = line.split('|')[0].strip()
            out.append(head.replace('#', ' > ') if quarantine else head)
    except OSError:
        pass
    return out


known_pats = patterns('known_failures.txt')
quar_pats = patterns('quarantine.txt', quarantine=True)


HARD_MODULES = {'quality', 'tools/strings', 'ui/words', 'core/security', 'ui/gate'}
HARD_CLASS = re.compile(r'Drift|Konsist|Detekt|Lint|SpecFile|Glossary')


def is_hard(f):
    mod, rest = f['id'].split(' :: ', 1)
    return mod in HARD_MODULES or bool(HARD_CLASS.search(rest.split(' > ')[0].split('.')[-1]))


def is_known(f):
    if is_hard(f):
        return False
    mod, rest = f['id'].split(' :: ', 1)
    full = f'{mod} :: {rest}'
    cls = rest.split(' > ')[0]
    if any(fnmatch.fnmatchcase(full, p) or fnmatch.fnmatchcase(f'{mod} :: {cls}', p) for p in known_pats):
        return True
    test = rest.split(' > ', 1)[1] if ' > ' in rest else ''
    for q in quar_pats:  # "<FQCN>" or "<FQCN> > <test name prefix>"
        qc, _, qt = q.partition(' > ')
        if cls == qc and (not qt or test.startswith(qt)):
            return True
    return False


new = [f for f in failed if is_hard(f) or (f['id'] not in base_failed and not is_known(f))]
known = [f for f in failed if f not in new]
real = [f'{s["name"]}: {r}' for s in shards for r in s.get('real_failures', [])]
broken = [s['name'] for s in shards if s.get('build_failed') and not s['failures'] and not s.get('real_failures')]
real += [f'{n}: the Gradle run failed with no readable cause' for n in broken]
compile_ = (load('compile.json', reports) or [{}])[0]
custom = (load('custom.json', reports) or [{}])[0]
apk = (load('apk.json', reports) or [{}])[0]
record = (load('record.json', reports) or [{}])[0]


def mark(result):
    return {'success': 'ok', 'failure': 'FAILED', 'cancelled': 'cancelled', 'skipped': 'not run'}.get(result or '', result or 'not run')


rows = [('lane', lane)]
if env('RESULT_COMPILE') not in (None, '', 'skipped'):
    rows.append(('compile', mark(env('RESULT_COMPILE')) + (f' ({compile_["errors"]} errors)' if compile_.get('errors') else '')))
if env('RESULT_TEST') not in (None, '', 'skipped'):
    line = f'{tests} run, {len(failed)} failed'
    line += f', {len(new)} new ({len(known)} known noise: on base {len(base_failed)}, known list/quarantine)' if base_shards else \
        f', {len(new)} new ({len(known)} on the known list/quarantine; no base reports to compare with)'
    if real:
        line += f'; {len(real)} real build/task problem(s), listed below'
    rows.append(('desktop tests', line))
if env('RESULT_RECORD') not in (None, '', 'skipped'):
    rows.append(('goldens', f'{mark(env("RESULT_RECORD"))}: {record.get("files", 0)} images recorded'))
if env('RESULT_APK') not in (None, '', 'skipped'):
    rows.append(('apk', mark(env('RESULT_APK')) + (f' - artifact `{apk["artifact"]}`: {apk["file"]} ({apk["mb"]} MB)' if apk.get('file') else '')))
if env('RESULT_CUSTOM') not in (None, '', 'skipped'):
    rows.append(('custom tasks', mark(env('RESULT_CUSTOM'))))

# Integrity: a run must not look green when Gradle never ran (a failed distribution download, a dead setup step, a missing shard).
# Exit code 2 = integrity failure; the workflow reds the run on every branch for it, unlike the ordinary FAIL (1).
integrity = []
planned = [x for x in env('PLANNED_SHARDS', '').split(',') if x != '']
if env('RESULT_TEST') not in (None, '', 'skipped') and planned:
    seen = {s['name'].rsplit('-', 1)[-1] for s in shards}
    missing = [x for x in planned if x not in seen]
    if missing:
        integrity.append(f'{len(missing)} of {len(planned)} planned test shard(s) left no report (shard {", ".join(missing)})')
for s in shards:
    if s.get('job_status') == 'failure' or s.get('pick_outcome') in ('failure', 'skipped', 'cancelled'):
        integrity.append(f'{s["name"]}: the shard job did not get as far as running Gradle (setup {s.get("pick_outcome") or "unknown"}, job {s.get("job_status") or "unknown"})')
    elif s.get('planned') and not s.get('gradle_ran', True):
        integrity.append(f'{s["name"]}: Gradle never ran although {len(s["planned"])} task(s) were planned (no task or BUILD line in the log)')
for key, label, rep in (('RESULT_COMPILE', 'compile', compile_), ('RESULT_APK', 'apk', apk), ('RESULT_CUSTOM', 'custom tasks', custom)):
    if env(key) not in (None, '', 'skipped'):
        if not rep:
            integrity.append(f'{label}: the job left no report (it died before its report step)')
        elif rep.get('failed') and rep.get('gradle_ran') is False:
            integrity.append(f'{label}: Gradle never ran (nothing started, for instance a failed distribution download)')

reasons = []
for key, label, rep in (('RESULT_COMPILE', 'compile', compile_), ('RESULT_APK', 'apk', apk), ('RESULT_RECORD', 'goldens', record), ('RESULT_CUSTOM', 'custom tasks', custom)):
    if env(key) in ('failure', 'cancelled') or rep.get('failed'):
        reasons.append(f'{label} job failed')
if env('RESULT_TEST') not in (None, '', 'skipped') and not shards:
    reasons.append('the test shards left no reports at all')
reasons += [f'real failure, {r}' for r in real]
if new:
    reasons.append(f'{len(new)} failing test(s) that are not known noise')
reasons = [f'INTEGRITY, {r}' for r in integrity] + reasons
red = bool(reasons)

print(f'## Native CI: {"FAIL" if red else "PASS"}\n')
for r in reasons[:12]:
    print(f'- **FAIL**: {r}')
if len(reasons) > 12:
    print(f'- ... {len(reasons) - 12} more reasons')
print('\n| step | result |\n|---|---|')
for k, v in rows:
    print(f'| {k} | {v} |')
if compile_.get('first_errors'):
    print('\n### Compile errors (first lines)\n\n```')
    print('\n'.join(compile_['first_errors'][:15]))
    print('```')
if new:
    print(f'\n### New failing tests: {len(new)}\n')
    for f in new[:40]:
        print(f'- `{f["id"]}`: {f["message"]}')
    if len(new) > 40:
        print(f'- ... {len(new) - 40} more')
elif failed:
    print(f'\n{len(failed)} failing test(s) are all known noise (also failing on the base, or on tool/ci/known_failures.txt).')
if not red:
    print('\nPASS: nothing here needs anyone.')
sys.exit(2 if integrity else (1 if red else 0))
