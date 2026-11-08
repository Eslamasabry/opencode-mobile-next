#!/usr/bin/env python3
"""Final job of Native CI: one short table in the run summary.

    python3 -I tool/ci/summary.py <reports-dir> <base-reports-dir> <lane>   (markdown to stdout)

This job is the run's gate: it exits 1 (the only red the owner gets mailed about) only for a real problem:
a compile error, a failed APK / record / custom job, a test shard whose build broke without any test failing, or a
failing test that is NOT known noise. A failing test is known noise when it also fails in the base reports, matches
tool/ci/known_failures.txt (fnmatch on "<module> :: <class>", plus an optional "#test" suffix on the class), or is
listed in tool/ci/quarantine.txt. Test jobs themselves never fail the run (continue-on-error).

Reads <reports-dir>/**/report-*.json (test shards), compile.json, apk.json and record.json written by the jobs, and
diffs the failing test ids against <base-reports-dir> (the newest run on the base branch that kept reports), when any.
Job results come from the environment: RESULT_COMPILE, RESULT_TEST, RESULT_APK, RESULT_RECORD, RESULT_CUSTOM.
"""
import fnmatch, glob, json, os, sys

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


def is_known(f):
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


new = [f for f in failed if f['id'] not in base_failed and not is_known(f)] if base_shards else [f for f in failed if not is_known(f)]
known = [f for f in failed if f not in new]
broken = [s['name'] for s in shards if s.get('build_failed') and not s['failures']]
compile_ = (load('compile.json', reports) or [{}])[0]
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
    if broken:
        line += f'; build broke in shard {", ".join(broken)}'
    rows.append(('desktop tests', line))
if env('RESULT_RECORD') not in (None, '', 'skipped'):
    rows.append(('goldens', f'{mark(env("RESULT_RECORD"))}: {record.get("files", 0)} images recorded'))
if env('RESULT_APK') not in (None, '', 'skipped'):
    rows.append(('apk', mark(env('RESULT_APK')) + (f' - artifact `{apk["artifact"]}`: {apk["file"]} ({apk["mb"]} MB)' if apk.get('file') else '')))
if env('RESULT_CUSTOM') not in (None, '', 'skipped'):
    rows.append(('custom tasks', mark(env('RESULT_CUSTOM'))))

print('## Native CI result\n\n| step | result |\n|---|---|')
for k, v in rows:
    print(f'| {k} | {v} |')

red = any(env(k) in ('failure', 'cancelled') for k in ('RESULT_COMPILE', 'RESULT_APK', 'RESULT_RECORD', 'RESULT_CUSTOM')) or bool(new) or bool(broken)
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
    print(f'\n{len(failed)} failing tests are all known noise (also failing on the base, or on tool/ci/known_failures.txt): the run stays green.')
if not red:
    print('\nGreen: nothing here needs anyone.' )
sys.exit(1 if red else 0)
