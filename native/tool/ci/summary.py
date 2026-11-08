#!/usr/bin/env python3
"""Final job of Native CI: one short table in the run summary.

    python3 -I tool/ci/summary.py <reports-dir> <base-reports-dir> <lane>   (markdown to stdout; always exits 0, the jobs it reports on carry the red)

Reads <reports-dir>/**/report-*.json (test shards), compile.json, apk.json and record.json written by the jobs, and
diffs the failing test ids against <base-reports-dir> (the newest run on the base branch that kept reports), when any.
Job results come from the environment: RESULT_COMPILE, RESULT_TEST, RESULT_APK, RESULT_RECORD, RESULT_CUSTOM.
"""
import glob, json, os, sys

reports, base_dir, lane = sys.argv[1], sys.argv[2], sys.argv[3]
env = os.environ.get


def load(pattern, root):
    return [json.load(open(p)) for p in sorted(glob.glob(os.path.join(root, '**', pattern), recursive=True))]


shards = load('report-*.json', reports)
base_shards = load('report-*.json', base_dir)
tests = sum(s['tests'] for s in shards)
failed = [f for s in shards for f in s['failures']]
base_failed = {f['id'] for s in base_shards for f in s['failures']}
new = [f for f in failed if f['id'] not in base_failed] if base_shards else None
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
    if new is not None:
        line += f', {len(new)} new vs base ({len(base_failed)} failing on base)'
    else:
        line += ', base: no reports to compare with'
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

red = any(env(k) in ('failure', 'cancelled') for k in ('RESULT_COMPILE', 'RESULT_TEST', 'RESULT_APK', 'RESULT_RECORD', 'RESULT_CUSTOM'))
if compile_.get('first_errors'):
    print('\n### Compile errors (first lines)\n\n```')
    print('\n'.join(compile_['first_errors'][:15]))
    print('```')
if failed:
    show = new if new else failed
    title = 'New failing tests (not failing on the base)' if new else 'Failing tests'
    print(f'\n### {title}: {len(show)}\n')
    for f in show[:40]:
        print(f'- `{f["id"]}`: {f["message"]}')
    if len(show) > 40:
        print(f'- ... {len(show) - 40} more')
    if new is not None and not new:
        print('\nEvery failure also fails on the base: nothing here is new.')
if not red and not failed:
    print('\nAll green.')
sys.exit(0)
