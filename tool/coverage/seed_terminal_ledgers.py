#!/usr/bin/env python3
"""Writes the terminal area's ledgers (one decision per field). Run after
tool/coverage/terminal_samples.mjs; edit the decisions here."""
import json

OUT = 'test/fixtures/coverage'
def keys(name):
    s = json.load(open(f'{OUT}/{name}_samples.json'))
    return [f"{g}.{f['path']}" for g, fs in s['schema'].items() for f in fs]
def write(name, d):
    ks = keys(name)
    assert set(d) == set(ks), (name, set(d) ^ set(ks))
    json.dump(d, open(f'{OUT}/{name}_ledger.json', 'w'), indent=2, sort_keys=True)

ID = 'ignored: an internal id the app uses to match records; nothing a person reads'
FOLDER = 'ignored: which folder the list is for; the person is already in that project'

W = {f'location.{k}': FOLDER for k in ['directory', 'workspaceID', 'project.id', 'project.directory']}
W.update({
    'pty.id': ID, 'pty.title': 'shown', 'pty.command': 'shown', 'pty.status': 'shown', 'pty.exitCode': 'shown',
    'pty.args[]': 'shown', 'pty.cwd': 'shown', 'pty.pid': 'shown',
    'shell.name': 'shown', 'shell.acceptable': 'shown',
    'shell.path': 'ignored: shown only to tell two shells with the same name apart',
    'config.shell': 'shown',
})
for k, v in json.load(open(f'{OUT}/terminal_wire_samples.json'))['excluded'].items():
    W[k] = v
write('terminal_wire', W)

T = {
    'process.pid': 'shown', 'process.name': 'shown', 'process.cmd': 'shown', 'process.group': 'shown',
    'process.rss_kb': 'shown', 'process.cwd': 'shown', 'process.orphan_reason': 'shown', 'process.protected': 'shown',
    'process.elapsed_s': 'shown',
    'process.ppid': ID,
    'process.cpu_pct': "ignored: a lifetime average that misleads; Busy or Idle is measured between two readings instead",
    'process.cpu_seconds': 'ignored: only the difference between two readings is used, to say Busy or Idle',
    'stop.stopped[]': 'shown', 'stop.killed[]': 'shown', 'stop.remaining[].pid': 'shown', 'stop.refused[].reason': 'shown',
    'stop.remaining[].name': 'ignored: the list below shows what still runs, by name',
    'stop.refused[].pid': 'ignored: the line counts what was protected; the list shows which',
    'scan_state.state': 'shown',
    'storage.scanned_at': 'shown', 'storage.total_bytes': 'shown',
    'storage.stale': 'ignored: a stale report loses its Clean buttons and the page says to scan again',
    'storage.cleanup_policy': 'ignored: a version guard; an old report is treated as stale',
    'category.key': 'shown', 'category.bytes': 'shown', 'category.deletable': 'shown',
    'category.paths[].path': 'shown', 'category.paths[].bytes': 'shown',
    'category.label_key': "ignored: the app uses its own words for each kind of storage",
    'category.note_key': "ignored: the app uses its own words for each kind of storage",
    'project.name': 'shown', 'project.bytes': 'shown', 'project.build_bytes': 'shown',
    'project.path': 'ignored: the project\'s name says which it is; its path is long and technical',
}
write('terminal_termux', T)

P = {k: v for k, v in json.load(open(f'{OUT}/terminal_paseo_samples.json'))['excluded'].items()}
write('terminal_paseo', P)

# --------------------------------------------------------------- actions
P_PROC = 'test/termux_processes_test.dart'
P_STO = 'test/termux_storage_test.dart'
P_MIG = 'test/termux_migration_ui_test.dart'
P_LOC = 'test/local_terminal_screen_test.dart'
P_ACC = 'test/terminal_accessibility_test.dart'
A = {
    'oc1 POST /pty': 'reachable: Terminal > New terminal',
    'oc1 POST /api/pty': 'reachable: Terminal > New terminal',
    'oc1 PUT /pty/{ptyID}': 'reachable: Terminal > a terminal > Rename',
    'oc1 PUT /api/pty/{ptyID}': 'reachable: Terminal > a terminal > Rename',
    'oc1 DELETE /pty/{ptyID}': 'reachable: Terminal > menu > Remove ended terminals; proof: test/terminal_close_gone_test.dart :: OpenCode 2: closing a terminal that is already gone succeeds',
    'oc1 DELETE /api/pty/{ptyID}': 'reachable: Terminal > menu > Remove ended terminals; proof: test/terminal_close_gone_test.dart :: OpenCode 2: closing a terminal that is already gone succeeds',
    'oc1 POST /pty/{ptyID}/connect-token': f'reachable: Terminal > open a terminal; proof: {P_ACC} :: terminal reconnect resumes from its cursor without replaying transcript',
    'oc1 POST /api/pty/{ptyID}/connect-token': f'reachable: Terminal > open a terminal; proof: {P_ACC} :: terminal reconnect resumes from its cursor without replaying transcript',
    'paseo create_terminal_request': 'not offered: a Claude Code or Pi server has no terminal tab; the app has no Paseo terminal screen',
    'paseo kill_terminal_request': 'not offered: a Claude Code or Pi server has no terminal tab; the app has no Paseo terminal screen',
    'paseo terminal.rename.request': 'not offered: a Claude Code or Pi server has no terminal tab; the app has no Paseo terminal screen',
    # ---- the terminal page and surface
    'app terminal.new': 'reachable: Terminal > New terminal',
    'app terminal.rename': 'reachable: Terminal > a terminal > Rename',
    'app terminal.remove-ended': 'reachable: Terminal > menu > Remove ended terminals',
    'app terminal.open': f'reachable: Terminal > a terminal; proof: {P_ACC} :: terminal reconnect resumes from its cursor without replaying transcript',
    'app terminal.reconnect': f'reachable: a closed terminal > Reconnect; proof: {P_ACC} :: closed terminal disables writes and announces why',
    'app terminal.keys': 'reachable: a terminal > the key bar (Esc, Ctrl, Alt, arrows); proof: test/terminal_key_bar_test.dart :: Ctrl turns letters into control bytes',
    'app terminal.source': f'reachable: Terminal > This phone / OpenCode server; proof: {P_LOC} :: installed: a shell opens at once, with the key bar',
    'app terminal.local-restart': f'reachable: This phone terminal > Restart; proof: {P_LOC} :: the shell ended: its output stays, with Restart',
    'app shell.choose': 'reachable: Settings > Default shell > a shell',
    # ---- Termux processes
    'app procs.details': f'reachable: Running on this phone > a process; proof: {P_PROC} :: details say what the process is and copy its command',
    'app procs.stop-one': f'reachable: Running on this phone > a process > Stop; proof: {P_PROC} :: one process stops from its details sheet, after asking',
    'app procs.stop-kind': f'reachable: Running on this phone > a kind > Stop all; proof: {P_PROC} :: a kind with several processes stops them together',
    'app procs.stop-orphans': f'reachable: Running on this phone > Stop orphaned helpers; proof: {P_PROC} :: the one bulk stop names the orphans, asks first',
    'app procs.retry': f'reachable: Running on this phone > Try again; proof: {P_PROC} :: a list that cannot be read offers Try again',
    # ---- Termux storage
    'app storage.scan': f'reachable: Storage on this phone > Scan storage; proof: {P_STO} :: scans with the live panel, cancels, then shows the report',
    'app storage.stop-scan': f'reachable: Storage on this phone > Stop scan; proof: {P_STO} :: scans with the live panel, cancels, then shows the report',
    'app storage.clean': f'reachable: Storage on this phone > a category > Clean; proof: {P_STO} :: cleaning over a gigabyte is two-step and shows freed bytes',
    # ---- migration
    'app migration.review-and-copy': f'reachable: This phone > Move to the in-app server > Start; proof: {P_MIG} :: review: projects move and are on, private copies are off,',
    'app migration.stop': f'reachable: Move > Stop; proof: {P_MIG} :: copying shows the real stage per item; Stop asks, keeps the',
    'app migration.resume': f'reachable: Move > Resume; proof: {P_MIG} :: leaving the app stops the copy, says Android stopped it, and',
}
json.dump(A, open(f'{OUT}/terminal_actions_ledger.json', 'w'), indent=2, sort_keys=True)
