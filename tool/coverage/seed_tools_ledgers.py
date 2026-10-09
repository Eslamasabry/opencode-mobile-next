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
