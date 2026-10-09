#!/usr/bin/env python3
"""Writes the phone area's ledgers (one decision per field). Run after
tool/coverage/phone_samples.mjs; edit the decisions here."""
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
J = {
    'job.jobId': ID,
    'job.host': 'ignored: which Linux the job runs in; the page reads the same on both',
    'job.state': 'shown', 'job.errorCode': 'shown', 'job.logTail': 'shown', 'job.order[]': 'shown',
    'job.params._job.first': 'shown', 'job.params._job.adding': 'shown',
    'job.current': 'ignored: the running row shows its own state; this only says which one',
    'job.startedAt': 'ignored: used to work out the time left, which the page shows as "min left"',
    'job.updatedAt': 'ignored: used to tell a stalled job from a running one; nothing is printed',
    'job.error': 'ignored: technical; the page says what failed in plain words and keeps the log under Details',
    'job.params.opencode.runtime': 'ignored: which OpenCode to install; the finished row shows the version that went in',
    'job.params.opencode.version': 'ignored: which OpenCode to install; the finished row shows the version that went in',
    'component.state': 'shown', 'component.stage': 'shown', 'component.done': 'shown', 'component.total': 'shown',
    'component.percent': 'shown', 'component.version': 'shown', 'component.error': 'shown',
    'component.weight': 'ignored: how much of the bar a step is worth; the bar shows the result',
    'component.startedAt': 'ignored: used for the time left; nothing is printed',
    'component.endedAt': 'ignored: used for the time left; nothing is printed',
    'component.data': 'ignored: bookkeeping a step keeps for itself; nothing a person acts on',
}
write('phone_job', J)

D = {
    'device.availableStorageBytes': 'shown', 'device.totalMemoryMb': 'shown', 'device.supportedAbis[]': 'shown',
    'device.memoryClassMb': 'ignored: the app heap size, which says nothing about what the phone can run; setup uses total memory',
    'device.lowRamDevice': 'ignored: total memory already decides this; the phone is told its memory, not a label',
    'device.hasMicrophone': 'ignored: only voice typing cares about the microphone; setting up OpenCode does not',
}
write('phone_device', D)

CAPS = 'ignored: the new-conversation screen uses this (model list, attach, stop); the agents list does not'
write('phone_agent_row', {
    'runtime.installed': 'shown', 'runtime.architectureQualified': 'shown', 'runtime.stoppedInBackground': 'shown',
    'runtime.hostAvailable': 'shown', 'runtime.signInPhase': 'shown', 'runtime.resetAt': 'shown',
    'runtime.capabilities.resumeVerified': 'shown',
    'runtime.payloadPresent': 'ignored: only decides whether Remove is offered in the agent sheet; the row itself reads Not installed',
    'runtime.capabilities.modelList': CAPS, 'runtime.capabilities.permissions': CAPS,
    'runtime.capabilities.images': CAPS, 'runtime.capabilities.cancel': CAPS,
    'account.state': 'shown', 'account.accountDisplayName': 'shown',
    'account.error': 'ignored: why the sign-in check failed is technical; the row says Sign in needed and the sheet offers Sign in',
})
write('phone_agent_install', {
    'progress.phase': 'shown', 'progress.fraction': 'shown', 'progress.failure': 'shown',
    'progress.componentId': 'ignored: which internal piece is downloading; the bar covers the whole agent',
})
write('phone_agent_check', {
    'check.passed': 'shown', 'check.completed[]': 'shown', 'check.failure': 'shown',
    'check.agentId': 'ignored: names the agent the check ran for; the sentence already names it',
    'check.architecture': 'ignored: the processor kind is used to qualify the agent; a failure says so in words',
})
write('phone_agent_removal', {
    'removal.freedBytes': 'shown', 'removal.alreadyAbsent': 'shown',
    'removal.agentId': 'ignored: an internal id the app uses to match records; nothing a person reads',
})
GONE = 'ignored: the older in-app sign-in used this; sign-in now runs on its own terminal and the sheet does not read it'
write('phone_agent_signin', {
    'state.phase': 'shown', 'state.method': 'shown', 'state.inspected': 'shown',
    'state.failure': GONE, 'state.authorizationUrl': GONE, 'state.codeSubmitted': GONE,
    'state.resetAt': 'ignored: the row carries the reset time the host gave; the sheet reads it from the row',
})
write('phone_agent_signin_output', {'output.page': 'shown', 'output.code': 'shown'})

TXI = 'ignored: internal bookkeeping of the setup manager; nothing a person reads or acts on'
write('phone_termux', {
    'status.phase': 'shown', 'status.message': 'shown', 'status.version': 'shown', 'status.started_at': 'shown',
    'status.runtime': 'shown', 'status.switch_target': 'shown',
    'status.port': TXI, 'status.runner': TXI, 'status.pid': TXI, 'status.operation': TXI, 'status.operation_result': TXI,
    'status.failure_kind': 'ignored: lets the app restart a crashed server by itself in the background; this page says "Setup didn\'t finish" either way',
    'status.switch_previous': TXI, 'status.switch_phase': TXI, 'status.switch_return': TXI,
    'log.output': 'shown',
})

BK = 'ignored: code and engine bookkeeping; never a value a person reads'
write('phone_component', {
    'component.title': 'shown', 'component.shortTitle': 'shown', 'component.why': 'shown', 'component.summary': 'shown',
    'component.required': 'shown', 'component.defaultOn': 'shown', 'component.estimatedSeconds': 'shown', 'component.downloadBytes': 'shown',
    'component.id': ID,
    'component.dependsOn[]': 'ignored: the totals already count what a choice pulls in; the names of the pieces are not listed',
    'component.app': 'ignored: says the app installs it itself (voice typing); the row looks the same',
    **{f'component.{k}': BK for k in ['installedBytes', 'downloadSize', 'native', 'agentUser', 'jobStep', 'checkScript', 'installScript', 'removeScript', 'presenceScript', 'sizeScript']},
})

write('phone_storage', {
    'gate.server': 'shown', 'gate.accessGranted': 'shown', 'gate.accessAfterAllow': 'shown', 'gate.termuxCanRead': 'shown',
    'gate.confinedRunning': 'shown', 'gate.restartBack': 'shown', 'gate.workRunning': 'shown',
})

CATALOG = {
    'name': 'shown', 'recipe.artifacts.arm64.downloadBytes': 'shown',
    'id': ID, 'providerId': ID,
    'iconKey': 'ignored: picks one of two glyphs beside the name; it carries no information of its own',
    'route': 'ignored: decides which agents are listed here (the OpenCode servers are not phone agents); never printed',
    'availability': 'ignored: hides an agent that cannot be offered; a hidden one simply is not listed',
    'signInMethod': 'ignored: which sign-in the agent uses; the sheet words it through the sign-in page (see phone_agent_signin)',
    'limitation': 'ignored: an authored developer note that names the host; the list says each state in its own plain words',
    'resumeReason': 'ignored: an authored reason; a row says "Can\'t reopen old conversations" itself',
    'recipe.version': 'ignored: the pinned version the app installs; the Check this phone result does not print it',
    'recipe.executable': 'ignored: the program name the app runs; code, not copy',
    'recipe.launchArgs[]': 'ignored: command-line arguments the app passes; code, not copy',
    'recipe.signInArgs[]': 'ignored: command-line arguments the app passes; code, not copy',
}
for arch in ('arm64', 'x64'):
    for f in ('url', 'sha256', 'format', 'archiveMember', 'installedBytes'):
        CATALOG[f'recipe.artifacts.{arch}.{f}'] = 'ignored: where the pinned download comes from and how it is checked; the person sees only its size'
CATALOG['recipe.artifacts.x64.downloadBytes'] = 'ignored: the size for a PC-type processor; a phone shows the 64-bit Arm size'
for c in ('resumeVerified', 'modelList', 'permissions', 'images', 'cancel'):
    CATALOG[f'capabilities.{c}'] = 'ignored: the catalog proves nothing about what works; the phone check and the certification matrix decide'
write('phone_agent_catalog', {f'agent.{k}': v for k, v in CATALOG.items()})

CERT = {
    'cert.cells.install.state': 'shown', 'cert.cells.smoke.state': 'shown',
    'cert.agentVersion': 'ignored: only compared with the version this app installs; the picker says "Not certified on this version yet" in words',
    'cert.helperVersion': 'ignored: only compared with the helper the phone runs; it never prints',
    'cert.architecture': 'ignored: which processor the proof ran on; it narrows the match and is never printed',
}
CERT_NOTE = {
    'version': 'the version check runs when the phone is checked, with its own pass or fail in plain words',
    'signedOut': 'the sign-in page reads the live state of the phone, not the matrix',
    'signIn': 'the sign-in page reads the live state of the phone, not the matrix',
    'models': 'it only unlocks the model chip on the new-conversation screen',
    'tools': 'a team record; no screen reads it',
    'permission': 'it only unlocks approval requests on the new-conversation screen',
    'images': 'it only unlocks sending photos on the new-conversation screen',
    'abort': 'it only unlocks Stop on the new-conversation screen',
    'resume': 'it only decides whether a row adds "Can\'t reopen old conversations" (see phone_agent_row)',
    'network': 'a team record; no screen reads it',
    'cards': 'a team record; no screen reads it',
}
for cell, note in CERT_NOTE.items():
    CERT[f'cert.cells.{cell}.state'] = f'ignored: test result kept for the team; {note}'
for cell in ['install', 'version', 'signedOut', 'signIn', 'models', 'smoke', 'tools', 'permission', 'images', 'abort', 'resume', 'network', 'cards']:
    CERT[f'cert.cells.{cell}.evidence'] = 'ignored: the document that backs the result, kept in the repository; not for the person using the app'
write('phone_agent_certification', CERT)

# ---------------------------------------------------------------- actions
WIRE = [
    'native startSetup', 'native cancelSetup', 'native completeSetupStep', 'native installUbuntu',
    'native openStorageSettings', 'native runAgentSetupCheck', 'native remove',
    'termux startSetup', 'termux completeSetupStep', 'termux restart',
    'engine run', 'engine cancel', 'engine restore',
    'agents installAgent', 'agents cancelAgentInstall', 'agents runAgentPhoneCheck', 'agents resumeAgentHost',
    'agents recheckAgentSignIn', 'agents confirmAgentSignIn', 'agents cancelAgentSignIn', 'agents signOutAgent',
    'agents removeAgent', 'agents selectChatAgent', 'agents selectAgentModel', 'agents startNewChatReplacing',
    'agents closePhoneAgentsForSignInReset',
]
json.dump({'source': 'lib/builtin/builtin_linux.dart (oc/builtin), lib/termux/bridge.dart, lib/builtin/setup/setup_contract.dart (SetupEngine), lib/domain/phone_agents_source.dart', 'wire': WIRE},
          open(f'{OUT}/phone_actions_samples.json', 'w'), indent=2)

def proof(file, name):
    return f'; proof: test/{file} :: {name}'

START = 'phone_setup_start_screen_test.dart'
PROG = 'phone_setup_progress_screen_test.dart'
TERM = 'phone_setup_termux_screen_test.dart'
TJOB = 'phone_setup_termux_job_screen_test.dart'
AUI = 'agents_ui_test.dart'
AREM = 'agents_remove_ui_test.dart'
AACC = 'agents_account_ui_test.dart'
ACT = {
    # screens and controls
    'app setup.run': 'reachable: Phone setup > Set up OpenCode on this phone' + proof(START, 'Set up runs the default selection, then opens the progress'),
    'app setup.customize': 'reachable: Phone setup > Choose what to install > Done' + proof(START, 'Customize totals follow the switches and shape Set up'),
    'app setup.add-tools': 'reachable: This phone > Add tools > Add' + proof(START, 'returns the chosen ids for first setup'),
    'app setup.cancel': 'reachable: Setup progress > Cancel > Stop setup (asks first)' + proof(PROG, 'Cancel confirms before stopping'),
    'app setup.continue': 'reachable: Setup progress > Continue setup (after a failure, an interruption or a stop)' + proof(PROG, 'interrupted and cancelled jobs offer Continue setup'),
    'app setup.open-ready': 'reachable: Setup progress > finishing hands over to the ready page' + proof(PROG, 'done hands over to screen C, replacing this screen'),
    'app setup.storage-settings': 'reachable: Phone setup > Open Storage settings (low space)' + proof(START, 'low space names how much to free and offers Storage'),
    'app setup.report-failure': 'reachable: Setup progress > Report this failure' + proof('setup_progress_report_test.dart', "a failed row offers Report with the job"),
    'app setup.voice-remove': 'reachable: Choose what to install > Voice typing > Remove (asks first)' + proof('phone_setup_voice_item_test.dart', 'Add tools shows it installed with its removal'),
    'app setup.termux-get': 'reachable: Setup with Termux > Get Termux' + proof(TERM, 'without Termux the first row waits on the person'),
    'app setup.termux-allow': 'reachable: Setup with Termux > Allow Termux' + proof(TERM, 'Allow copies the unlock line and opens Termux'),
    'app setup.termux-update': 'reachable: This phone > Update OpenCode (Termux)' + proof(TERM, 'an update ends back where it was opened'),
    'app storage.allow-files': 'reachable: Opening a shared folder > Allow access to files (asks first)' + proof('shared_storage_access_flow_test.dart', 'explains first, then opens the system page, then proceeds'),
    'app storage.restart-open': 'reachable: Opening a new shared folder > Restart and open (asks first)' + proof('shared_storage_access_flow_test.dart', 'asks, restarts, then opens'),
    'app agent.install': 'reachable: Agents > a not-installed agent > Install > Install {agent}',
    'app agent.cancel-install': 'reachable: Agent sheet > Cancel setup',
    'app agent.check': 'reachable: Agent sheet > Check {agent}' + proof(AUI, 'a failed phone check says which step and what to do'),
    'app agent.check-all': 'reachable: Agents > Check this phone' + proof(AUI, 'Check this phone shows each step in plain words'),
    'app agent.resume': 'reachable: Agents > Resume (an agent stopped in the background)' + proof(AUI, 'Stopped in the background resumes the host'),
    'app agent.sign-in': 'reachable: Agents > Sign in > Sign in with {agent} (opens the agent\'s own terminal)' + proof(AUI, 'each agent signs in with its own login command'),
    'app agent.sign-in-again': 'reachable: Agent sheet (signed in) > Sign in again' + proof(AUI, 'signed in, it still offers to sign in again'),
    'app agent.sign-out': 'reachable: Agent sheet (signed in) > Sign out of {agent} (asks first, names the agent)',
    'app agent.remove': 'reachable: Agent sheet > Remove {agent} (asks first, names the agent, says accounts and conversations stay)',
    'app agent.choose': 'reachable: New conversation > agent chip > an agent' + proof(AUI, 'choosing a ready agent selects it and closes'),
    'app agent.model': 'reachable: New conversation > agent model chip > a model' + proof(AUI, 'lists the chosen agent'),
    'app agent.terminal-open-page': 'reachable: Agent sign-in > Open sign-in page (through the app\'s link check)' + proof('agent_sign_in_terminal_test.dart', 'fx: its Vercel device page opens and its code copies'),
    'app agent.terminal-copy-code': 'reachable: Agent sign-in > Copy code' + proof('agent_sign_in_terminal_test.dart', 'Codex: its device page and the code on the next line'),
    'app agent.terminal-again': 'reachable: Agent sign-in > Sign in with {agent} (start again after a stop)' + proof(AUI, 'ending without signing in says so and starts again'),
    'app agent.terminal-leave': 'reachable: Agent sign-in > Back (ends the sign-in)' + proof(AUI, 'leaving the terminal ends Claude'),
    'app agent.close-app': 'reachable: Agents > Close the app (after clearing sign-ins)' + proof(AUI, 'one restart offer closes the app'),
    'app agent.switch-builtin': 'reachable: Agents (on a server that cannot run them) > Switch to the built-in server' + proof('agents_settings_placement_test.dart', 'with a built-in server saved, the page offers the switch'),
    'app agent.new-chat-replace': 'reachable: A conversation an agent cannot reopen > Start new conversation (asks first)' + proof(AUI, 'a row that cannot reopen asks before starting a new one'),
    # not offered
    'app agent.remove-claude': 'not offered: Claude Code is not installed or removed by this app, so its sheet has no Remove (agents_remove_ui_test "Claude Code never offers Remove")',
    'app agent.sign-out-unqualified': 'not offered: an agent whose own logout the app has not verified shows no Sign out, so nothing is claimed that cannot be confirmed',
    'app agent.cancel-check': 'not offered: the phone check takes a few seconds and ends by itself; the sheet cannot be closed while it runs so the host is never left half-checked',
    'app setup.uninstall-linux': 'not offered: removing the phone\'s Linux and every tool at once is on the This phone page (owned by the settings lane), not on the setup pages',
    'app agent.cancel-sign-in-button': 'not offered: sign-in runs in the agent\'s own terminal; leaving the terminal is the way to stop it, so there is no separate Cancel button',
}
# wire entries point at the same decisions
WIREMAP = {
    'native startSetup': ('reachable: Phone setup > Set up OpenCode on this phone' + proof(START, 'Set up runs the default selection, then opens the progress')),
    'native cancelSetup': ('reachable: Setup progress > Cancel > Stop setup (asks first)' + proof(PROG, 'Cancel confirms before stopping')),
    'native completeSetupStep': 'not offered: the app marks a step it finished itself (voice typing\'s model) as done; no control exists or is needed',
    'native installUbuntu': 'not offered: an older one-step install that setup now replaces; no screen calls it',
    'native openStorageSettings': ('reachable: Phone setup > Open Storage settings (low space)' + proof(START, 'low space names how much to free and offers Storage')),
    'native runAgentSetupCheck': ('reachable: Agents > Check this phone' + proof(AUI, 'Check this phone shows each step in plain words')),
    'native remove': 'not offered: removing the phone\'s Linux is on the This phone page (owned by the settings lane), not on the setup or agents pages',
    'termux startSetup': ('reachable: Setup with Termux > Allow Termux, then the install starts' + proof(TJOB, 'Allow copies the unlock line and opens Termux')),
    'termux completeSetupStep': 'not offered: the app marks a step it finished itself as done; no control exists or is needed',
    'termux restart': ('reachable: This phone > Update OpenCode (Termux)' + proof(TERM, 'an update ends back where it was opened')),
    'engine run': ('reachable: Phone setup > Set up OpenCode on this phone' + proof(START, 'Set up runs the default selection, then opens the progress')),
    'engine cancel': ('reachable: Setup progress > Cancel > Stop setup (asks first)' + proof(PROG, 'Cancel confirms before stopping')),
    'engine restore': ('reachable: Reopening setup shows the job again' + proof(PROG, 'restores on open and shows the title and note')),
    'agents installAgent': 'reachable: Agents > a not-installed agent > Install > Install {agent}',
    'agents cancelAgentInstall': 'reachable: Agent sheet > Cancel setup',
    'agents runAgentPhoneCheck': ('reachable: Agent sheet > Check {agent}; Agents > Check this phone' + proof(AUI, 'Check this phone shows each step in plain words')),
    'agents resumeAgentHost': ('reachable: Agents > Resume' + proof(AUI, 'Stopped in the background resumes the host')),
    'agents recheckAgentSignIn': ('reachable: Agent sheet > Sign in (reads the sign-in when it opens)' + proof(AACC, 'it ends once the status check says signed in')),
    'agents confirmAgentSignIn': ('reachable: Agent sign-in terminal (checks when the sign-in ends)' + proof(AACC, 'it ends once the status check says signed in')),
    'agents cancelAgentSignIn': ('reachable: Agent sign-in > Back (ends the sign-in)' + proof(AUI, 'leaving the terminal ends Claude')),
    'agents signOutAgent': 'reachable: Agent sheet (signed in) > Sign out of {agent} (asks first, names the agent)',
    'agents removeAgent': 'reachable: Agent sheet > Remove {agent} (asks first, names the agent, says accounts and conversations stay)',
    'agents selectChatAgent': ('reachable: New conversation > agent chip > an agent' + proof(AUI, 'choosing a ready agent selects it and closes')),
    'agents selectAgentModel': ('reachable: New conversation > agent model chip > a model' + proof(AUI, 'lists the chosen agent')),
    'agents startNewChatReplacing': ('reachable: A conversation an agent cannot reopen > Start new conversation (asks first)' + proof(AUI, 'a row that cannot reopen asks before starting a new one')),
    'agents closePhoneAgentsForSignInReset': ('reachable: Agents > Close the app' + proof(AUI, 'one restart offer closes the app')),
}
A = {**ACT, **WIREMAP}
json.dump(A, open(f'{OUT}/phone_actions_ledger.json', 'w'), indent=2, sort_keys=True)
