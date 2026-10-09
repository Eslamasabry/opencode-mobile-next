#!/usr/bin/env python3
"""Writes the tools area's ledgers (one decision per field). Run after
tool/coverage/tools_samples.mjs; edit the decisions here."""
import json

OUT = 'test/fixtures/coverage'
def keys(name):
    s = json.load(open(f'{OUT}/{name}_samples.json'))
    return s, [f"{g}.{f['path']}" for g, fs in s['schema'].items() for f in fs]
def write(name, d):
    s, ks = keys(name)
    assert set(d) == set(ks), (name, set(d) ^ set(ks))
    json.dump(d, open(f'{OUT}/{name}_ledger.json', 'w'), indent=2, sort_keys=True)

ID = 'ignored: an internal id the app uses to match records; nothing a person reads'
NEVER = 'ignored: registry text is untrusted and a value or default can carry a credential, so the app never keeps it and never shows it'
LISTED = 'ignored: where this entry came from; the page shows what it is and what it holds, not how the server found it'

# ------------------------------------------------------------------ wire
W = {}
for k in ['directory', 'workspaceID', 'project.id', 'project.directory']:
    W[f'location.{k}'] = 'ignored: which folder the list is for; the person is already in that project'
W.update({
    'command.name': 'shown', 'command.description': 'shown', 'command.agent': 'shown',
    'command.template': 'ignored: the prompt text the server fills in; the list shows the command\'s name and what it does',
    'command.model.id': 'ignored: the model a command runs with; the conversation it starts shows what runs',
    'command.model.providerID': 'ignored: the model a command runs with; the conversation it starts shows what runs',
    'command.model.variant': 'ignored: the model a command runs with; the conversation it starts shows what runs',
    'command.subtask': 'ignored: decides whether the server starts the command as a helper; the conversation it starts shows what ran',
    'skill.name': 'shown', 'skill.description': 'shown', 'skill.location': 'shown', 'skill.content': 'shown',
    'skill.slash': 'ignored: decides whether a "/name" shortcut can be copied; its value is never printed',
    'reference.name': 'shown', 'reference.path': 'shown', 'reference.description': 'shown',
    'reference.hidden': 'ignored: a hidden reference is left out of the list; its value is never printed',
    'tool.id': 'shown', 'tool.description': 'shown',
    'tool.parameters': 'ignored: the technical schema the model fills in; nothing a person acts on',
    'tool_id.[]': 'shown',
    'experimental.backgroundSubagents': 'ignored: decides whether the Tools tab says background helpers are missing; its value is never printed',
    'mcp_status.status': 'shown', 'mcp_status.error': 'shown',
    'mcp_resource.name': 'shown', 'mcp_resource.uri': 'shown', 'mcp_resource.description': 'shown', 'mcp_resource.client': 'shown',
    'mcp_resource.mimeType': 'ignored: the technical content type; the name, address and description say what it is',
})
for k in ['type', 'repository', 'branch', 'path', 'description', 'hidden']:
    W[f'reference.source.{k}'] = LISTED
write('tools_wire', W)

# -------------------------------------------------------------- registry
R = {
    'server.name': 'shown', 'server.title': 'shown', 'server.description': 'shown',
    'server.version': 'ignored: the catalogue lists the latest release of a listing; the package version shows in the form',
    'meta.status': 'ignored: a deprecated or deleted listing is left out of the list; its value is never printed',
    'remote.type': 'ignored: both kinds of hosted endpoint connect the same way from the form',
    'remote.url': 'shown',
    'remote.headers[].name': 'shown', 'remote.headers[].isRequired': 'shown',
    'remote.headers[].isSecret': 'ignored: decides that the value is typed into a hidden field; its value is never printed',
    'remote.headers[].value': NEVER, 'remote.headers[].default': NEVER,
    'remote.headers[].description': NEVER,
    'remote.variables': 'ignored: address templates and their defaults are untrusted; a listing that needs them is set up by hand',
    'package.registryType': 'shown', 'package.identifier': 'shown', 'package.version': 'shown',
    'package.transport.type': 'ignored: only a command that talks over standard input and output is offered; the form says how it runs',
    'package.runtimeHint': 'shown',
    'package.environmentVariables[].name': 'shown', 'package.environmentVariables[].isRequired': 'shown',
    'package.environmentVariables[].isSecret': 'ignored: decides that the value is typed into a hidden field; its value is never printed',
    'package.environmentVariables[].value': NEVER, 'package.environmentVariables[].default': NEVER,
    'package.environmentVariables[].description': NEVER,
    'package.runtimeArguments[].isRequired': 'shown',
    'package.runtimeArguments[].value': NEVER,
    'package.packageArguments[].isRequired': 'ignored: a required argument is flagged as "Needs extra settings", the same as a runtime one; the line is shown for the listing as a whole',
    'package.packageArguments[].value': NEVER,
}
write('tools_registry', R)

# -------------------------------------------------------------- external
E = {
    'card.name': 'shown', 'card.description': 'shown', 'card.cardUrl': 'shown', 'card.endpoint': 'shown', 'card.version': 'shown',
    'card.auth': 'ignored: decides whether a key is asked for when the agent is added; the saved key itself is never shown',
    'card.supported': 'ignored: an agent the app cannot talk to cannot be saved, so a saved card is always supported',
    'card.skills[].name': 'shown', 'card.skills[].description': 'shown',
    'task.id': ID, 'task.contextId': ID, 'task.statusMessageId': ID,
    'task.state': 'shown', 'task.parts[].text': 'shown', 'task.parts[].url': 'shown', 'task.parts[].name': 'shown',
    'task.omittedContent': 'shown',
}
write('tools_external', E)

# ----------------------------------------------------------------- paseo
P = {
    'command.agentId': ID,
    'command.commands[].name': 'shown', 'command.commands[].description': 'shown',
    'command.commands[].argumentHint': 'ignored: the sheet starts a command by its name and the person types the rest in the message; the hint is not asked for',
    'command.commands[].kind': 'ignored: tells commands from skills inside the daemon; the sheet lists both the same way',
    'command.error': "ignored: when the daemon cannot list commands the sheet shows none; the daemon's own words are technical",
}
NOTASKED = 'ignored: the app never asks the daemon for this message, so no page shows it'
s, ks = keys('tools_paseo')
for k in ks:
    if k.startswith('skills_status.') or k.startswith('config_apply.'):
        P[k] = NOTASKED
write('tools_paseo', P)

# ------------------------------------------------------------------- web
Wb = {f'location.{k}': 'ignored: which folder the list is for; the person is already in that project' for k in ['directory', 'workspaceID', 'project.id', 'project.directory']}
Wb.update({
    'provider.id': ID, 'provider.name': 'shown', 'response.providerID': ID,
    'result.url': 'shown', 'result.title': 'shown', 'result.content': 'shown',
    'result.time.published': 'ignored: when the page was published; the title and excerpt say what it is',
})
write('tools_web', Wb)

# --------------------------------------------------------------- actions
# "reachable: ..." is proved by a tap path in
# test/coverage/tools_actions_coverage_test.dart, or by the named existing test
# ("; proof: <file> :: <test name>"), which the ratchet checks is still there.
# "not offered: <reason>" says why no control calls it.
P_MCP = 'test/library_integrations_mcp_test.dart'
P_CAT = 'test/revamp/slice_p2_4_5_test.dart'
A = {
    'oc1 POST /mcp': "reachable: Tools > MCP > Add MCP server (runtime add); proof: test/mcp_setup_screen_test.dart :: v2 shows its location and adds without configuration reload",
    'oc1 PATCH /config': "reachable: Tools > MCP > Add MCP server > This project; proof: test/mcp_setup_screen_test.dart :: saves a project remote MCP with exact advanced fields",
    'oc1 PATCH /global/config': "reachable: Tools > MCP > Add MCP server > All projects; proof: test/mcp_setup_screen_test.dart :: saves a global local MCP on a compact large-text phone",
    'oc1 POST /mcp/{name}/connect': 'reachable: Tools > MCP > Connect <server>',
    'oc1 POST /mcp/{name}/disconnect': f"reachable: Tools > MCP > press and hold a connected server > Disconnect (confirm); proof: {P_MCP} :: MCP Disconnect waits for the confirm sheet",
    'oc1 POST /mcp/{name}/auth': f"reachable: Tools > MCP > Sign in to <server>; proof: {P_MCP} :: MCP authentication shows the validated destination host",
    'oc1 POST /mcp/{name}/auth/authenticate': f"reachable: Tools > MCP > Sign in to <server>; proof: {P_MCP} :: MCP authentication shows the validated destination host",
    'oc1 POST /mcp/{name}/auth/callback': f"reachable: Tools > MCP > finish sign-in with the callback; proof: {P_MCP} :: MCP authorization completes from a state-validated callback URL",
    'oc1 DELETE /mcp/{name}/auth': f"reachable: Tools > MCP > cancel a sign-in in progress; proof: {P_MCP} :: MCP authorization completes from a state-validated callback URL",
    'oc1 POST /session/{sessionID}/command': "reachable: Tools > Commands > a command > Run; proof: test/library_commands_test.dart :: new workspace creates a chat and runs without duplicate submission",
    'paseo agent.config.apply.request': 'not offered: owner decision needed - changes how an agent on the computer is set up (profiles, models, modes); no screen offers agent settings for Claude Code or Pi yet',
}
for k in ['plugin.directory.install', 'plugin.disable', 'plugin.enable', 'plugin.reload', 'plugin.remove', 'plugin.rpc.invoke', 'plugin.source.install', 'plugin.source.update.apply', 'plugin.source.update']:
    A[f'paseo {k}.request'] = 'not offered: admin-only - installing, enabling or removing plugins changes software on the owner\'s computer; the app lists OpenCode plugins read-only'
A.update({
    'app tools.tabs': 'reachable: Tools > MCP, Commands, Tools, Skills, References, External agents',
    'app mcp.add': 'reachable: Tools > MCP > Add MCP server',
    'app mcp.connect': 'reachable: Tools > MCP > Connect <server>',
    'app mcp.error-details': 'reachable: Tools > MCP > Details for <server> (a server that failed)',
    'app mcp.disconnect': f"reachable: Tools > MCP > press and hold a connected server > Disconnect (confirm); proof: {P_MCP} :: MCP Disconnect waits for the confirm sheet",
    'app mcp.sign-in': f"reachable: Tools > MCP > Sign in to <server>; proof: {P_MCP} :: MCP authentication shows the validated destination host",
    'app mcp.remove': f"reachable: Tools > MCP > press and hold a server > Remove until restart; proof: {P_CAT} :: turning one off runs the MCP page removal",
    'app mcp.copy-resource': f"reachable: Tools > MCP > press and hold a resource > Copy address; proof: {P_MCP} :: MCP resources remain available when integrations fail",
    'app catalog.browse': f"reachable: Tools > MCP > Add MCP server > Browse the catalogue; proof: {P_CAT} :: offers the catalogue and the manual form, nothing dead",
    'app catalog.load': f"reachable: MCP catalogue > Load the list; proof: {P_CAT} :: asks before the first request, then lists as switches",
    'app catalog.switch': f"reachable: MCP catalogue > switch a listing on or off; proof: {P_CAT} :: turning one off runs the MCP page removal",
    'app catalog.stop': f"reachable: MCP catalogue > menu > Stop using the registry; proof: {P_CAT} :: Stop using the registry forgets consent and the list",
    'app mcp.form-save': 'reachable: Add MCP server > Save; proof: test/mcp_setup_screen_test.dart :: saves a project remote MCP with exact advanced fields',
    'app commands.run': "reachable: Tools > Commands > a command > Run; proof: test/library_commands_test.dart :: new workspace creates a chat and runs without duplicate submission",
    'app commands.refresh': 'reachable: Tools > Commands > pull down to refresh; proof: test/library_refresh_test.dart :: older command refresh cannot clear a newer refresh error',
    'app skills.open': 'reachable: Tools > Skills > a skill; proof: test/library_skills_test.dart :: skill content uses the shared Markdown and code renderer',
    'app references.copy': 'reachable: Tools > References > a reference > Copy; proof: test/library_skills_test.dart :: standalone references copy their exact OpenCode mention',
    'app tools.open-tool': 'reachable: Tools > Tools > a tool > Copy parameter schema; proof: test/tools_screen_test.dart :: the tool sheet copies the schema the server returned',
    'app external.add-and-ask': 'reachable: Tools > External agents > Add agent, Check, Save, New task, Send, Reply, Forget, Remove; proof: test/external_agent_widget_test.dart :: check save draft task input result reopen and remove journey',
    'app web.search-and-add': 'reachable: Add web source > search or paste a link > Add; proof: test/web_search_test.dart :: search review returns to the real editable composer without sending',
    'app context.open-and-copy': 'reachable: Active context > a message > open or copy; proof: test/active_context_test.dart :: search, type filter, full preview and copy act on the selected content',
    'app local-agent.connect': 'reachable: Claude Code on this phone > Connect; proof: test/local_agent_onboarding_test.dart :: ready -> Connect saves exactly one Paseo server on loopback',
})
json.dump(A, open(f'{OUT}/tools_actions_ledger.json', 'w'), indent=2, sort_keys=True)
