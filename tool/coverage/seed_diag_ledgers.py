#!/usr/bin/env python3
"""Writes the diag area's ledgers (one decision per field). Run after
tool/coverage/diag_samples.mjs; edit the decisions here."""
import json

OUT = 'test/fixtures/coverage'
def keys(name):
    s = json.load(open(f'{OUT}/{name}_samples.json'))
    return [f"{g}.{f['path']}" for g, fs in s['schema'].items() for f in fs]
def write(name, d):
    ks = keys(name)
    assert set(d) == set(ks), (name, set(d) ^ set(ks))
    json.dump(d, open(f'{OUT}/{name}_ledger.json', 'w'), indent=2, sort_keys=True)

NEVER = 'ignored: free text from Android or an error; never read or stored, so it can never show or be shared'
write('diag_exit', {
    'wire.supported': 'shown', 'wire.error': 'shown',
    'entry.reason': 'shown', 'entry.importance': 'shown', 'entry.timestamp': 'shown',
    'entry.subReason': 'ignored: only decides which plain category an exit gets (updated, stopped, needed memory); the category is what shows',
    'entry.status': 'ignored: Android\'s exit status number says nothing a person can act on; not read',
    'entry.description': NEVER,
})
write('diag_error', {
    'entry.source': 'shown', 'entry.message': 'shown', 'entry.stack': 'shown', 'entry.occurrences': 'shown', 'entry.timestamp': 'shown',
})
write('diag_crash', {
    'record.source': 'shown', 'record.category': 'shown', 'record.time': 'shown',
    'input.errorMessage': NEVER, 'input.errorStack': NEVER, 'input.nativeText': NEVER,
})
write('diag_perf', {
    'span.name': 'shown', 'span.durationMs': 'shown', 'span.attrs.status': 'shown',
    'span.parent': 'shown', 'span.outcome': 'shown', 'span.isMark': 'shown', 'span.startMicros': 'shown',
    'span.wallStart': 'ignored: the wall-clock start is used for ordering only; the list shows how long each step took',
    'span.id': 'ignored: an internal number that keeps rows apart; nothing a person reads',
})

# ---------------------------------------------------------------- actions
WIRE = [
    'oc2 GET /api/session/{sessionID}/export', 'oc1 GET /session/{sessionID}/export',
    'device share sheet', 'device clipboard copy', 'device file save', 'github issue link',
    'device crash store write', 'device crash store delete',
]
json.dump({'source': 'lib/ui/screens/session_export_screen.dart, lib/diagnostics/*, lib/platform/share_intent.dart', 'wire': WIRE},
          open(f'{OUT}/diag_actions_samples.json', 'w'), indent=2)

def proof(file, name):
    return f'; proof: test/{file} :: {name}'
ADS = 'app_diagnostics_screen_test.dart'
CRS = 'crash_reports_section_test.dart'
DXS = 'diagnostics_exit_share_test.dart'
SEX = 'session_export_test.dart'
SIM = 'session_import_test.dart'
SNO = 'session_note_test.dart'
DEM = 'demo_isolation_test.dart'
PRF = 'perf_trace_test.dart'
A = {
    # Report a problem
    'app report.review': 'reachable: Report a problem > Review report' + proof(ADS, 'describe, review exactly what is sent, then open the'),
    'app report.github': 'reachable: Report a problem > Review report > Open on GitHub (through the link check)' + proof(ADS, 'describe, review exactly what is sent, then open the'),
    'app report.copy': 'reachable: Report a problem > Review report > Copy' + proof(ADS, 'diagnostics too long for the link are copied before the form'),
    'app report.share': 'reachable: Report a problem > Review report > Share' + proof(ADS, 'Share hands the preview to the share sheet'),
    'app report.include': 'reachable: Report a problem > Include recent diagnostics' + proof(ADS, 'an attached error needs no description; diagnostics can be'),
    'app report.clear-errors': 'reachable: Report a problem > recent errors > Delete (asks first, names the count)' + proof(ADS, 'errors saved before a restart are listed, and Clear asks with'),
    'app report.copy-details': 'reachable: Report a problem > an error > Details > Copy all (masked)' + proof('redaction_test.dart', 'diagnostics: the kept errors list and an opened entry are'),
    # crash reports
    'app crash.switch-on': 'reachable: Report a problem > Save crash reports on this phone (on)' + proof(CRS, 'off by default: the switch says what is kept'),
    'app crash.switch-off': 'reachable: Report a problem > Save crash reports on this phone (off; deletes them)' + proof(CRS, 'turning it off asks nothing and the saved reports go'),
    'app crash.preview-row': 'reachable: Report a problem > a saved crash report' + proof(CRS, 'saved reports show as plain rows; the preview keeps the'),
    'app crash.delete': 'reachable: Report a problem > Delete saved crash reports (asks first, names the count)' + proof(CRS, 'Delete saved crash reports confirms, deletes and keeps'),
    'app crash.share': 'reachable: Report a problem > Share crash report (shows exactly what is sent first)' + proof(DXS, 'the preview shows exactly what is shared, and only Share'),
    # exits
    'app exit.retry': 'reachable: Report a problem > Recent app exits > Try again' + proof(DXS, 'a failed read says so and Try again reads again'),
    'app exit.show-all': 'reachable: Report a problem > Recent app exits > Show all',
    # perf
    'app perf.copy': 'reachable: Report a problem > Details > Performance > Copy timing report' + proof(PRF, 'shows grouped stats and recent spans, copies a safe report,'),
    'app perf.clear': 'reachable: Report a problem > Details > Performance > Clear timings' + proof(PRF, 'shows grouped stats and recent spans, copies a safe report,'),
    # export
    'app export.format': 'reachable: Export conversation > Format > Complete conversation / Readable transcript' + proof(SEX, 'a server that turns out to lack the copy moves to the'),
    'app export.redact': 'reachable: Export conversation > Redact sensitive data' + proof(SEX, 'redaction says what it keeps and belongs to JSON only'),
    'app export.save-json': 'reachable: Export conversation > Save complete conversation (redacted unless switched off)' + proof(SEX, 'JSON defaults to redaction and passes an isolated buffer'),
    'app export.save-markdown': 'reachable: Export conversation > Save readable transcript (says it is not redacted)',
    'app export.cancel': 'reachable: Export conversation > Cancel download' + proof(SEX, 'cancel and changed connection never open a save dialog'),
    # import (data gate: lane A)
    'app import.choose-file': 'reachable: Import conversation > Choose file' + proof(SIM, 'an unreadable file says so by the file and keeps Import off'),
    'app import.destination': 'reachable: Import conversation > Destination' + proof(SIM, 'one place to import into: chosen, then nothing to change'),
    'app import.import': 'reachable: Import conversation > Import (after the review)' + proof(SIM, 'review precedes import, conflicts retain file and chosen destination'),
    'app import.open': 'reachable: Import conversation > Open imported conversation' + proof(SIM, 'successful import cannot be submitted twice and opens returned location'),
    # note
    'app note.save': 'reachable: Note for the agent > Save note' + proof(SNO, 'authorization failure keeps the draft and successful retry returns to chat'),
    'app note.delete': 'reachable: Note for the agent > Delete saved note' + proof(SNO, 'removeSessionNote'),
    'app note.refresh': 'reachable: Note for the agent > Refresh saved note' + proof(SNO, 'failed save retains draft and refresh shows remote note before replacing'),
    'app note.discard': 'reachable: Note for the agent > Back > Discard changes (asks first)' + proof(SNO, 'compact large text scrolls, oversize save disabled and back protects draft'),
    # demo
    'app demo.leave': 'reachable: Try it offline > Leave demo',
    'app demo.reset': 'reachable: Try it offline > Reset demo',
    'app demo.set-up-server': 'reachable: Try it offline > Set up your own server (after the loop)' + proof(DEM, 'reset and exit dispose streaming gateways and keep a fresh prompt'),
    'app demo.send': 'reachable: Try it offline > send the sample prompt (simulated; nothing real changes)' + proof(DEM, 'compact demo keeps send and exit reachable with large text and keyboard'),
    # wire
    'oc2 GET /api/session/{sessionID}/export': 'reachable: Export conversation > Save complete conversation' + proof(SEX, 'exports complete response bytes with default redaction and no location'),
    'oc1 GET /session/{sessionID}/export': 'not offered: OpenCode 1 has no complete-copy export; its conversations are saved as the readable transcript from the loaded messages',
    'device share sheet': 'reachable: Report a problem > Review report > Share' + proof(ADS, 'Share hands the preview to the share sheet'),
    'device clipboard copy': 'reachable: Report a problem > Review report > Copy' + proof(ADS, 'diagnostics too long for the link are copied before the form'),
    'device file save': 'reachable: Export conversation > Save (the system file picker)' + proof(SEX, 'a file the device cannot write says so and allows retry'),
    'github issue link': 'reachable: Report a problem > Review report > Open on GitHub (through the link check)' + proof(ADS, 'describe, review exactly what is sent, then open the'),
    'device crash store write': 'reachable: Report a problem > Save crash reports on this phone (on)' + proof(CRS, 'off by default: the switch says what is kept'),
    'device crash store delete': 'reachable: Report a problem > Delete saved crash reports (asks first)' + proof(CRS, 'Delete saved crash reports confirms, deletes and keeps'),
    # not offered
    'app crash.send-automatically': 'not offered: crash reports are kept on the phone and sent only when the person includes them in a report themselves; nothing uploads by itself',
    'app export.markdown-redact': 'not offered: the transcript is saved exactly as loaded, so there is no redaction switch; the screen says plainly that it is not redacted',
}
json.dump(A, open(f'{OUT}/diag_actions_ledger.json', 'w'), indent=2, sort_keys=True)
