#!/usr/bin/env python3
"""Writes the servers area's ledgers (one decision per field) from the
generated samples. Run after tool/coverage/servers_samples.mjs; edit the
decisions here, not the JSON by hand."""
import json, re, sys

OUT = 'test/fixtures/coverage'
def load(name):
    return json.load(open(f'{OUT}/{name}_samples.json'))

def keys(sample):
    return [f"{g}.{f['path']}" for g, fs in sample['schema'].items() for f in fs]

ID = 'ignored: an internal id the app uses to match records; nothing a person reads'
PROC = "ignored: the server's process number; nothing a person acts on"

# ------------------------------------------------------------------- wire
wire = load('servers_wire')
L = {}
for k in keys(wire):
    L[k] = None
def put(k, v):
    assert k in L, k
    L[k] = v
put('oc1.health.version', 'shown')
put('oc2.health.version', 'shown')
put('oc2.health.pid', PROC)
put('oc2.info.version', 'shown')
put('oc2.info.pid', PROC)
put('oc2.info.urls[]', 'ignored: the addresses the server listens on; the app connects with the address the person saved')
put('paseo.server_info.version', 'shown')
put('paseo.server_info.serverId', ID)
put('paseo.server_info.hostname', "ignored: the machine's own name; the person already named this server and typed its address")
put('paseo.server_info.permissions[]', 'ignored: what the app may do on the daemon; the app only reads and chats, and never offers to manage it')
put('paseo.server_info.desktopManaged', 'ignored: whether a desktop app manages the daemon; nothing in the app depends on it')
put('paseo.server_info.capabilities', 'ignored: voice settings of the daemon; voice runs on the phone')
for k in list(L):
    if k.startswith('paseo.server_info.features.'):
        L[k] = "ignored: a daemon feature switch the app does not read; it already knows what Claude Code and Pi support"
put('paseo.provider.provider', 'shown')
DECIDES = 'ignored: decides whether the agent is listed as ready; its own value is never printed'
for f in ['status', 'enabled', 'source', 'error']:
    put(f'paseo.provider.{f}', DECIDES)
MODEL = 'ignored: lives in the conversation\'s model and mode pickers, not on a server page'
for k in list(L):
    if k.startswith('paseo.provider.models[]') or k.startswith('paseo.provider.modes[]') or k == 'paseo.provider.defaultModeId':
        L[k] = MODEL
put('paseo.provider.fetchedAt', 'ignored: when the daemon last asked the agent; nothing a person acts on')
for f in ['label', 'description', 'iconSvg']:
    put(f'paseo.provider.{f}', "ignored: the app uses its own agent names and icons")
for k in list(L):
    if k.startswith('oc1.path.'):
        L[k] = 'ignored: the app never asks for the server paths; the folder a person works in comes from the project they pick'
    if k == 'oc1.experimental_capabilities.backgroundSubagents':
        L[k] = 'ignored: read for the Tools page, not a server page; that page says in words when background helpers are missing'
    if re.match(r'paseo\.(daemon_status|pairing_offer|daemon_config|config_reload|available_providers|daemon_update)\.', k):
        L[k] = 'ignored: the app never asks the server for this message, so no page shows it'
missing = [k for k, v in L.items() if v is None]
assert not missing, missing
json.dump(L, open(f'{OUT}/servers_wire_ledger.json', 'w'), indent=2, sort_keys=True)

# ---------------------------------------------------------------- profile
prof = load('servers_profile')
P = {
    'profile.id': ID,
    'profile.name': 'shown',
    'profile.baseUrl': 'shown',
    'profile.backend': 'shown',
    'profile.username': 'shown',
    'profile.codexDirectory': 'shown',
    'profile.flavor': 'shown',
    'profile.serverVersion': 'shown',
    'profile.orchestration': 'ignored: AI Team settings are shown on the AI Team pages, not on the server row',
}
assert set(P) == set(keys(prof)), set(P) ^ set(keys(prof))
json.dump(P, open(f'{OUT}/servers_profile_ledger.json', 'w'), indent=2, sort_keys=True)

# ----------------------------------------------------------- capabilities
cap = load('servers_capabilities')
flags = [f['path'] for f in cap['schema']['capability']]
PLUMB = 'ignored: plumbing the app handles itself; it changes how the app talks to the server, not what a person can do'
SHOWN = ['fileBrowsing', 'terminal', 'shellSettings', 'projectManagement', 'managedWorkspaces', 'promptAttachments',
         'promptAgentMentions', 'sessionCompact', 'sessionShare', 'sessionFork', 'sessionRevert', 'sessionArchive',
         'sessionTodos', 'sessionNotes', 'sessionImportExport', 'globalSessionSearch', 'persistentPermissionGrants',
         'offlinePromptQueue', 'cliSessionResume', 'serverCatalog', 'pluginInventory', 'remoteUpgrade', 'sessionDiff',
         'developmentServices', 'webSearch', 'messageDelete', 'savedPermissionList', 'permissionRequests', 'inbox',
         'mcpConfigWrites', 'mcpRuntimeAdds']
IGN = {
    'genUi': "ignored: turns on Agent cards only on the phone's own server; not a server ability a person could ask about",
    'sessionAddressHandoff': 'ignored: off for every server until portable conversation links are released',
    'setupConfigRead': 'ignored: decides whether Settings offers AI setup, which needs a server that shares its settings',
    'setupConfigWrite': 'ignored: a step of the AI setup helper, which only reviews and never changes the server',
    'setupMcpInventory': 'ignored: a step of the AI setup helper, which only reviews and never changes the server',
    'setupAssistantSession': 'ignored: a step of the AI setup helper, which only reviews and never changes the server',
    'clientPromptMessageID': PLUMB,
    'commandReceipts': PLUMB,
    'agentAccount': 'ignored: decides whether an agent has an Account page; the page exists only for agents that sign in',
    'hostAgentProviders': PLUMB,
    'hostAgentPermissionActions': PLUMB,
    'promptImagesOnly': 'ignored: narrows Attachments to pictures for an agent on this phone; the Attachments row covers it',
    'promptEchoTextOnly': PLUMB,
    'subagentReplies': "ignored: a helper's conversation that cannot take replies says so in place of the message box",
    'subagentSessions': "ignored: lets a helper's conversation open from the chat menu",
    'slashCommands': "ignored: the command sheet itself says when this server lists no commands",
    'profileAttentionPolling': 'ignored: whether the app can check this server for waiting requests while another is open; the server rows say "Needs you" when it can',
    'messageCompletionEndsRun': PLUMB,
    'sessionModelProviderSwitching': 'ignored: lets a conversation pick a model from another provider; the picker lists only what it can use',
    'agentSelection': 'ignored: shows the Agent choice in the model picker; a runtime without agents has nothing to choose',
    'workspaceWarp': 'ignored: adds an advanced entry to the command sheet only on servers that can move a conversation',
    'sessionSteal': 'ignored: adds Take over to the all-conversations list only where the server can do it',
    'consoleOrganizations': 'ignored: adds an advanced entry to the command sheet only on servers that have organizations',
    'mcpOAuth': 'ignored: a sign-in button on the connections page that appears only where the server supports it',
    'mcpChatConnect': PLUMB,
    'mcpChatOAuth': PLUMB,
    'mcpChatToolRefresh': PLUMB,
    'mcpRuntimeRemovals': 'ignored: a Remove button on the connections page that appears only where the server supports it',
    'integrationCredentials': 'ignored: a key-entry step on the connections page that appears only where the server supports it',
    'integrationCommandAuth': 'ignored: a sign-in step on the connections page that appears only where the server supports it',
    'workspaceSymbols': 'ignored: adds symbol search to file search where the server has it',
    'textSearch': PLUMB,
    'languageServiceStatus': "ignored: a check on the project's health page, which hides what the server cannot do",
    'formatterStatus': "ignored: a check on the project's health page, which hides what the server cannot do",
    'gitInit': "ignored: a button on the project's health page, which hides what the server cannot do",
    'toolInventory': 'ignored: the Tools tab says in words when the server does not list its tools',
    'experimentalCapabilities': PLUMB,
    'clientDiagnostics': PLUMB,
    'providerRuntimeRefresh': PLUMB,
    'configuredProviderFallback': PLUMB,
    'globalEventStream': PLUMB,
    'worktreeReset': 'ignored: adds Reset to a worktree row where the server can; the Worktrees row covers the page',
    'worktreeCreate': 'ignored: adds New worktree to the Worktrees page where the server can create one',
    'legacyQuestionRequests': PLUMB,
    'forms': 'ignored: which kind of question card the server sends; the person answers it in the conversation either way',
    'projectsAreGitRepositories': PLUMB,
}
C = {}
for f in flags:
    C[f'capability.{f}'] = 'shown' if f in SHOWN else IGN[f]
assert set(SHOWN) <= set(flags) and set(IGN) | set(SHOWN) == set(flags), set(flags) ^ (set(IGN) | set(SHOWN))
json.dump(C, open(f'{OUT}/servers_capabilities_ledger.json', 'w'), indent=2, sort_keys=True)

# ------------------------------------------------------------------ state
state = load('servers_state')
FOLDER = 'ignored: where the session runs; the row names the conversation and the server, not its folder'
S = {
    'connection.phase': 'shown',
    'connection.profileId': ID,
    'connection.serverName': 'shown',
    'connection.since': 'ignored: when the attempt began; it only keeps the progress line from restarting its clock, nothing prints it',
    'connection.usesToken': 'shown',
    'connection.retrying': 'ignored: only decides whether the Reconnect button is offered; its value is never printed',
    'connection.quiet': 'ignored: hides the line for a moment while a healthy link recovers; its value is never printed',
    'connection.attemptRevision': 'ignored: a counter the app uses to tell one try from the next; nothing a person reads',
    'attention.profileID': ID,
    'attention.status': 'ignored: decides whether the row speaks at all; a check that is not current says nothing rather than something old',
    'attention.checkedAt': 'shown',
    'attention.nextCheckAt': 'ignored: when the app will check again; nothing a person acts on',
    'attention.directory': FOLDER,
    'attention.workspace': FOLDER,
    'attention.requests[].id': ID,
    'attention.requests[].sessionID': ID,
    'attention.requests[].kind': 'shown',
    'attention.requests[].title': 'shown',
    'attention.requests[].directory': FOLDER,
    'attention.requests[].workspace': FOLDER,
    'attention.attention': "ignored: failed runs and team gates are listed in the Inbox's attention feed, not on a server row",
    'attention.attentionComplete': 'ignored: whether the failed-run list is complete; it only decides if older entries are marked stale',
    'attention.busyIntervals[].sessionID': ID,
    'attention.busyIntervals[].firstObservedBusyAt': 'ignored: decides whether a check-in is due; the app only samples, so it cannot say how long a run really lasted',
    'attention.busyIntervals[].lastObservedBusyAt': 'ignored: decides whether a check-in is due; the app only samples, so it cannot say how long a run really lasted',
    'attention.busyIntervals[].title': 'shown',
    'attention.busyIntervals[].directory': FOLDER,
    'attention.busyIntervals[].workspace': FOLDER,
    'attention.busyIntervals[].reminderClaimed': 'ignored: records that a reminder was already sent so it is not sent twice',
    'attention.runningCount': 'shown',
    'attention.complete': 'ignored: whether the check read everything; an incomplete one says nothing',
}
assert set(S) == set(keys(state)), set(S) ^ set(keys(state))
json.dump(S, open(f'{OUT}/servers_state_ledger.json', 'w'), indent=2, sort_keys=True)

# ---------------------------------------------------------------- actions
# "reachable: <screen> > <control>" is proved by a tap path in
# test/coverage/servers_actions_coverage_test.dart (or, where it says
# "proof:", by the named existing test, which the ratchet checks exists);
# "not offered: <reason>" says why no control on the area's screens calls it.
A = {
    # ---- OpenCode 1
    'oc1 POST /global/upgrade': 'reachable: Server settings > Update OpenCode to <version> (confirm); proof: test/settings_server_updates_test.dart :: remote update event offers the exact generated upgrade',
    'oc1 POST /global/dispose': 'not offered: it closes every open conversation on the server; a server is restarted on its own computer, which Server settings explains',
    'oc1 POST /instance/dispose': 'not offered: the app asks for it by itself right after it creates a project folder; there is nothing for a person to tap',
    'oc1 PATCH /config': "not offered: covered elsewhere - changed from Tools > MCP (add a server to the project), not from a server page",
    'oc1 PATCH /global/config': "not offered: covered elsewhere - changed from Tools > MCP and from the default shell row, not from a server page",
    'oc1 POST /log': "not offered: covered elsewhere - sent by Settings > Report a problem, not from a server page",
    'oc1 PUT /auth/{providerID}': 'not offered: covered elsewhere - provider sign-in belongs to Models and providers',
    'oc1 DELETE /auth/{providerID}': 'not offered: covered elsewhere - provider sign-out belongs to Models and providers',
    # ---- Paseo
    'paseo daemon.update.request': 'not offered: owner decision needed - the daemon can update itself, but the app has no Update button for it; a phone could run it after a confirmation',
    'paseo set_daemon_config_request': "not offered: admin-only - changes the daemon's own relay, hostnames and access rules; that is done on the computer that runs it",
    'paseo daemon.config.reload.request': 'not offered: admin-only - applies edited daemon settings; that is done on the computer that runs it',
    'paseo restart_server_request': 'not offered: dangerous - it stops every running agent; the daemon is restarted on its own computer',
    'paseo shutdown_server_request': 'not offered: dangerous - it stops the daemon and every running agent; done on its own computer',
    'paseo hub.management.daemon.connect.request': 'not offered: admin-only - links the daemon to a relay hub, which this app never uses',
    'paseo hub.management.daemon.disconnect.request': 'not offered: admin-only - links the daemon to a relay hub, which this app never uses',
    'paseo hub.management.daemon.permissions.update.request': 'not offered: admin-only - hub permissions of the daemon, which this app never uses',
    'paseo refresh_providers_snapshot_request': 'not offered: owner decision needed - no screen asks the daemon to look for agents again, so signing in to Claude Code on the computer is only noticed on the next connection',
    # ---- servers list
    'app servers.add': 'reachable: Servers > Add server',
    'app servers.connect': 'reachable: Servers > tap a server',
    'app servers.edit': 'reachable: Servers > press and hold a server > Edit',
    'app servers.details': 'reachable: Servers > press and hold a server > Details',
    'app servers.remove': 'reachable: Servers > press and hold a server > Remove (confirm)',
    'app servers.about': 'reachable: Servers > About',
    'app servers.account': "reachable: Servers > press and hold the connected server > Account; proof: test/agent_account_widget_test.dart :: Servers account entry reaches the panel through its capability",
    'app servers.move-queued': "reachable: Servers > press and hold a server with waiting prompts > Move; proof: test/phone_server_card_queued_prompts_test.dart :: says how many prompts wait and moves them to the connected",
    'app servers.this-phone': "reachable: Servers > This phone card (Open, Start, Stop, Remove); proof: test/phone_server_card_test.dart :: running: This phone and its version; Stop and Show log in",
    # ---- editor
    'app editor.test-connection': 'reachable: Add server > Test connection',
    'app editor.save-connect': 'reachable: Add server > Save & connect',
    'app editor.save-anyway': 'reachable: Add server > Save anyway (after a failed check)',
    'app editor.pairing-paste': "reachable: Add server > Paste code; proof: test/server_pairing_paste_test.dart :: pasting a pairing code fills url, username and password",
    'app editor.pairing-scan': "reachable: Add server > Scan code; proof: test/pairing_scanner_test.dart :: a permanent denial deep-links to app settings",
    'app editor.tailscale': "reachable: Add server > Tailscale; proof: test/tailscale_setup_test.dart :: failed launch gives recovery and review never probes or saves",
    # ---- connection status line
    'app banner.reconnect': 'reachable: status line > Reconnect to <server>',
    'app banner.details': 'reachable: status line > Details',
    'app banner.change-server': 'reachable: status line > Switch server',
    'app banner.update-password': 'reachable: status line > Update password',
    'app banner.update-token': 'reachable: status line > Update token',
    'app banner.restart-phone-server': "reachable: status line > Restart (the server on this phone); proof: test/revamp/shared_shell_1_test.dart :: own server offers Restart after asking",
    # ---- server settings and capabilities
    'app server-settings.check-again': 'reachable: Server settings > Check again',
    'app server-settings.change-sign-in': 'reachable: Server settings > Change sign-in',
    'app server-settings.host-management': 'reachable: Server settings > Run as a Linux service',
    'app server-settings.disconnect': 'reachable: Server settings > Disconnect (confirm)',
    'app host.copy-command': "reachable: Run as a Linux service > Copy; proof: test/host_management_screen_test.dart :: copy buttons place the exact command on the clipboard",
    'app capabilities.add-server': 'reachable: Available on this server > Add a server that has these',
}
json.dump(A, open(f'{OUT}/servers_actions_ledger.json', 'w'), indent=2, sort_keys=True)
