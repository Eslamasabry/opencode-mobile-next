// Area "tools" (Settings > Tools: MCP, Commands, Tools, Skills, References,
// External agents; the MCP catalogue). Families:
//
//   tools_wire      what a server answers for the Tools tabs: OpenCode's
//                   /api/command, /api/skill, /api/reference, /mcp status,
//                   /experimental/resource, /experimental/tool (schemas from
//                   the contract, read by the app's real repository).
//   tools_registry  one listing of the public MCP registry (what the app
//                   reads of it; header and variable VALUES are credentials
//                   and are never kept).
//   tools_external  an external (A2A) agent's saved card and a task result.
//   tools_paseo     Paseo's list_commands answer; its skills and agent-config
//                   messages, which the app never asks for (excluded).
import fs from 'node:fs';
import { loadContract, walker } from '../openapi_walk.mjs';
import { def, unwrap, objectFields } from '../paseo_cases_lib.mjs';

const LOC = { directory: '/work/shop', workspaceID: 'wrk_main', project: { id: 'prj_shop', directory: '/work/shop' } };

export function toolsFamilies({ oc1File, protocolMessages }) {
  const S = loadContract(oc1File);
  const w = walker(S);
  const doc = JSON.parse(fs.readFileSync(oc1File, 'utf8'));
  const out = {};
  const flatOf = (path) => {
    const sch = doc.paths[path].get.responses['200'].content['application/json'].schema;
    const o = {};
    w.flatten(sch, '', o, new Set());
    return o;
  };
  const under = (flat, prefix) => Object.fromEntries(Object.entries(flat).filter(([k]) => k.startsWith(prefix)).map(([k, v]) => [k.slice(prefix.length), v]));

  // ------------------------------------------------------------------ wire
  const cmd = flatOf('/api/command');
  const schema = {
    location: under(cmd, 'location.'),
    command: under(cmd, 'data[].'),
    skill: under(flatOf('/api/skill'), 'data[].'),
    reference: under(flatOf('/api/reference'), 'data[].'),
    tool: under(flatOf('/experimental/tool'), '[].'),
    tool_id: { '[]': { kind: 'string' } },
    experimental: w.group('ExperimentalCapabilities'),
    mcp_status: w.group('MCPStatus'),
    mcp_resource: w.group('McpResource'),
  };
  const envelope = (data) => ({ data, location: LOC });
  const wc = (id, endpoint, payload, parts) => ({ id, kind: endpoint, endpoint, payload, parts });
  const locPart = { group: 'location', value: LOC, probes: Object.fromEntries(Object.keys(schema.location).map((k) => [k, []])) };
  const none = (group, keys) => Object.fromEntries(keys.map((k) => [k, []]));
  const cmdA = { name: 'review-pr', template: 'Review $ARGUMENTS carefully', description: 'Review a pull request', agent: 'plan', model: { id: 'opus', providerID: 'anthropic', variant: 'high' }, subtask: true };
  const cmdB = { name: 'ship-it', template: 'Run the release checklist' };
  const skillA = { name: 'frontend-design', description: 'Make a clear visual hierarchy', slash: true, location: '/home/dev/.claude/skills/frontend-design/SKILL.md', content: '# Frontend design\nUse a clear hierarchy.' };
  const skillB = { name: 'plain-notes', description: 'Keep short notes', slash: false, location: '/work/shop/.opencode/skills/plain-notes/SKILL.md', content: 'Keep notes short.' };
  const refA = { name: 'shop-docs', path: '/work/shop/docs', description: 'The shop design docs', hidden: false, source: { type: 'git', repository: 'https://github.com/example/shop-docs', branch: 'main', description: 'docs checkout', hidden: false } };
  const refLocal = { name: 'style-guide', path: '/work/shop/style', description: 'The style guide', source: { type: 'local', path: '/work/shop/style', description: 'local folder', hidden: false } };
  const refHidden = { name: 'secret-notes', path: '/work/shop/notes', hidden: true, source: { type: 'local', path: '/work/shop/notes' } };
  const tool = { id: 'bash_run', description: 'Run a shell command in the project', parameters: { type: 'object', properties: { command: { type: 'string' } } } };
  out.tools_wire = {
    schema,
    source: oc1File,
    excluded: {},
    cases: [
      wc('command_full_and_minimal', '/api/command', envelope([cmdA, cmdB]), [
        locPart,
        { group: 'command', value: cmdA, probes: { name: ['review-pr'], template: [], description: ['Review a pull request'], agent: ['plan'], 'model.id': [], 'model.providerID': [], 'model.variant': [], subtask: ['Runs as a helper'] } },
        { group: 'command', value: cmdB, probes: { name: ['ship-it'], template: [] } },
      ]),
      wc('skill_slash_and_plain', '/api/skill', envelope([skillA, skillB]), [
        locPart,
        { group: 'skill', value: skillA, probes: { name: ['frontend-design'], description: ['Make a clear visual hierarchy'], slash: [], location: ['SKILL.md'], content: ['Use a clear hierarchy'] } },
        { group: 'skill', value: skillB, probes: { name: ['plain-notes'], description: ['Keep short notes'], slash: [], location: [], content: [] } },
      ]),
      wc('reference_visible_and_hidden', '/api/reference', envelope([refA, refLocal, refHidden]), [
        locPart,
        { group: 'reference', value: refA, probes: { name: ['shop-docs'], path: ['/work/shop/docs'], description: ['The shop design docs'], hidden: [], 'source.type': [], 'source.repository': [], 'source.branch': [], 'source.description': [], 'source.hidden': [] } },
        { group: 'reference', value: refLocal, probes: { name: ['style-guide'], path: ['/work/shop/style'], description: ['The style guide'], 'source.type': [], 'source.path': [], 'source.description': [], 'source.hidden': [] } },
        { group: 'reference', value: refHidden, probes: { name: [], path: [], hidden: [], 'source.type': [], 'source.path': [] } },
      ]),
      wc('tool_inventory', '/experimental/tool', { tools: [tool], ids: ['bash_run', 'read_file'], capabilities: { backgroundSubagents: true } }, [
        { group: 'tool', value: tool, probes: { id: ['bash_run'], description: ['Run a shell command in the project'], parameters: [] } },
        { group: 'tool_id', value: ['bash_run', 'read_file'], probes: { '[]': ['read_file'] } },
        { group: 'experimental', value: { backgroundSubagents: true }, probes: { backgroundSubagents: [] } },
      ]),
      wc('mcp_every_status', '/mcp', {
        github: { status: 'connected' },
        sentry: { status: 'needs_auth' },
        linear: { status: 'failed', error: 'connection refused by linear.example' },
        notion: { status: 'disabled' },
        figma: { status: 'needs_client_registration', error: 'client registration is required' },
      }, [
        { group: 'mcp_status', value: { status: 'connected' }, probes: { status: ['github'] } },
        { group: 'mcp_status', value: { status: 'needs_auth' }, probes: { status: ['sentry'] } },
        { group: 'mcp_status', value: { status: 'failed', error: 'connection refused by linear.example' }, probes: { status: ['linear'], error: ['connection refused by linear.example'] } },
        { group: 'mcp_status', value: { status: 'disabled' }, probes: { status: ['notion'] } },
        { group: 'mcp_status', value: { status: 'needs_client_registration', error: 'client registration is required' }, probes: { status: ['figma'], error: ['client registration is required'] } },
      ]),
      wc('mcp_resource_one', '/experimental/resource', {
        'github:readme': { name: 'readme', uri: 'file:///work/shop/README.md', description: 'The project readme', mimeType: 'text/markdown', client: 'github' },
      }, [
        { group: 'mcp_resource', value: { name: 'readme', uri: 'file:///work/shop/README.md', description: 'The project readme', mimeType: 'text/markdown', client: 'github' }, probes: { name: ['readme'], uri: ['README.md'], description: ['The project readme'], mimeType: ['text/markdown'], client: ['github'] } },
      ]),
    ],
  };

  // -------------------------------------------------------------- registry
  const unretained = 'ignored: registry text is untrusted and a value or default can carry a credential, so the app never keeps it';
  const regSchema = {
    server: { name: { kind: 'string' }, version: { kind: 'string' }, title: { kind: 'string' }, description: { kind: 'string' } },
    meta: { status: { kind: 'enum', values: ['active', 'deprecated', 'deleted'] } },
    remote: {
      type: { kind: 'enum', values: ['streamable-http', 'sse'] }, url: { kind: 'string' },
      'headers[].name': { kind: 'string' }, 'headers[].isRequired': { kind: 'boolean' }, 'headers[].isSecret': { kind: 'boolean' },
      'headers[].value': { kind: 'string' }, 'headers[].default': { kind: 'string' }, 'headers[].description': { kind: 'string' },
      'variables': { kind: 'any' },
    },
    package: {
      registryType: { kind: 'string' }, identifier: { kind: 'string' }, version: { kind: 'string' },
      'transport.type': { kind: 'enum', values: ['stdio', 'streamable-http', 'sse'] }, runtimeHint: { kind: 'string' },
      'environmentVariables[].name': { kind: 'string' }, 'environmentVariables[].isRequired': { kind: 'boolean' }, 'environmentVariables[].isSecret': { kind: 'boolean' },
      'environmentVariables[].value': { kind: 'string' }, 'environmentVariables[].default': { kind: 'string' }, 'environmentVariables[].description': { kind: 'string' },
      'runtimeArguments[].isRequired': { kind: 'boolean' }, 'runtimeArguments[].value': { kind: 'string' },
      'packageArguments[].isRequired': { kind: 'boolean' }, 'packageArguments[].value': { kind: 'string' },
    },
  };
  const k = (...ks) => Object.fromEntries(ks.map((x) => [x, []]));
  const entryA = {
    server: { name: 'io.example/weather', version: '1.2.0', title: 'Weather lookups', description: 'Forecasts and alerts for any city' },
    meta: { status: 'active' },
    remote: { type: 'streamable-http', url: 'https://mcp.weather.example/mcp', headers: [{ name: 'X-Api-Key', isRequired: true, isSecret: true, value: 'sk-test-0000000000000000', default: 'sk-default-000000000000', description: 'Your weather key' }], variables: { region: { description: 'Region', default: 'eu' } } },
  };
  const entryB = {
    server: { name: 'io.example/files', version: '0.4.1', description: 'Read files in a folder' },
    meta: { status: 'active' },
    pkg: { registryType: 'npm', identifier: '@example/files-mcp', version: '0.4.1', transport: { type: 'stdio' }, runtimeHint: 'npx', environmentVariables: [{ name: 'FILES_ROOT', isRequired: true, isSecret: false, value: '/srv/files', default: '/srv/default', description: 'Root folder' }], runtimeArguments: [{ isRequired: true, value: '--root' }], packageArguments: [{ isRequired: false, value: '--quiet' }] },
  };
  const entryOld = { server: { name: 'io.example/old', version: '0.1.0', description: 'Retired listing' }, meta: { status: 'deprecated' }, remote: { type: 'sse', url: 'https://mcp.old.example/sse' } };
  const wrap = (e) => ({ server: { name: e.server.name, version: e.server.version, description: e.server.description, ...(e.server.title ? { title: e.server.title } : {}), ...(e.remote ? { remotes: [e.remote] } : {}), ...(e.pkg ? { packages: [e.pkg] } : {}) }, _meta: { 'io.modelcontextprotocol.registry/official': { status: e.meta.status } } });
  const rc = (id, entries, parts) => ({ id, kind: 'registry', payload: { servers: entries.map(wrap) }, parts });
  out.tools_registry = {
    schema: regSchema,
    source: 'lib/domain/setup_registry.dart (registry.modelcontextprotocol.io /v0.1/servers)',
    excluded: {},
    cases: [
      rc('registry_remote_with_header', [entryA], [
        { group: 'server', value: entryA.server, probes: { name: [], version: [], title: ['Weather lookups'], description: ['Forecasts and alerts for any city'] } },
        { group: 'meta', value: entryA.meta, probes: { status: [] } },
        { group: 'remote', value: entryA.remote, probes: { type: [], url: ['mcp.weather.example'], 'headers[].name': ['X-Api-Key'], 'headers[].isRequired': ['The registry listing needs this one'], 'headers[].isSecret': [], 'headers[].value': [], 'headers[].default': [], 'headers[].description': [], variables: [] } },
      ]),
      rc('registry_package_and_old', [entryB, entryOld], [
        { group: 'server', value: entryB.server, probes: { name: ['files'], version: [], description: ['Read files in a folder'] } },
        { group: 'meta', value: entryB.meta, probes: { status: [] } },
        { group: 'package', value: entryB.pkg, probes: { registryType: ['Needs Node on the server'], identifier: ['@example/files-mcp'], version: ['0.4.1'], 'transport.type': [], runtimeHint: ['npx'], 'environmentVariables[].name': ['FILES_ROOT'], 'environmentVariables[].isRequired': ['The registry listing needs this one'], 'environmentVariables[].isSecret': [], 'environmentVariables[].value': [], 'environmentVariables[].default': [], 'environmentVariables[].description': [], 'runtimeArguments[].isRequired': ['Needs extra settings'], 'runtimeArguments[].value': [], 'packageArguments[].isRequired': [], 'packageArguments[].value': [] } },
        { group: 'meta', value: entryOld.meta, probes: { status: ['Retired listing'] } },
      ]),
    ],
  };
  // The registry's `title` is absent in entryB; entryOld contributes meta only.
  out.tools_registry._unretained = unretained;

  // -------------------------------------------------------------- external
  const extSchema = {
    card: {
      name: { kind: 'string' }, description: { kind: 'string' }, cardUrl: { kind: 'string' }, endpoint: { kind: 'string' }, version: { kind: 'string' },
      auth: { kind: 'enum', values: ['none', 'bearer'] }, supported: { kind: 'boolean' },
      'skills[].name': { kind: 'string' }, 'skills[].description': { kind: 'string' },
    },
    task: {
      id: { kind: 'string' }, contextId: { kind: 'string' },
      state: { kind: 'enum', values: ['submitted', 'working', 'inputRequired', 'authRequired', 'completed', 'failed', 'canceled', 'rejected', 'unknown'] },
      statusMessageId: { kind: 'string' }, 'parts[].text': { kind: 'string' }, 'parts[].url': { kind: 'string' }, 'parts[].name': { kind: 'string' }, omittedContent: { kind: 'boolean' },
    },
  };
  const card = { name: 'Colour agent', description: 'Picks colours for a screen', cardUrl: 'https://agent.example/.well-known/agent-card.json', endpoint: 'https://agent.example/rpc', version: '3.1', auth: 'bearer', supported: true, skills: [{ name: 'Palette', description: 'Suggest a palette' }] };
  const task = { id: 'task-77', contextId: 'ctx-77', state: 'completed', statusMessageId: 'msg-77', parts: [{ text: 'Blue result' }, { url: 'https://agent.example/files/palette.png', name: 'palette.png' }], omittedContent: true };
  out.tools_external = {
    schema: extSchema,
    source: 'lib/domain/external_agent.dart',
    excluded: {},
    cases: [
      { id: 'external_card_and_task', kind: 'external', payload: { card, task }, parts: [
        { group: 'card', value: card, probes: { name: ['Colour agent'], description: ['Picks colours for a screen'], cardUrl: ['agent.example/.well-known/agent-card.json'], endpoint: ['agent.example/rpc'], version: ['3.1'], auth: ['Key'], supported: [], 'skills[].name': ['Palette'], 'skills[].description': ['Suggest a palette'] } },
        { group: 'task', value: task, probes: { id: [], contextId: [], state: ['Completed'], statusMessageId: [], 'parts[].text': ['Blue result'], 'parts[].url': ['Review external link'], 'parts[].name': ['palette.png'], omittedContent: ['Some output is omitted'] } },
      ] },
    ],
  };

  // ----------------------------------------------------------------- paseo
  const m = protocolMessages;
  const payloadFields = (s) => objectFields(unwrap(def(s).shape.payload), ['requestId']);
  const pcmd = payloadFields(m.ListCommandsResponseSchema);
  const paseoSchema = {
    command: pcmd,
    skills_status: payloadFields(m.AgentSkillsGetStatusResponseSchema),
    config_apply: payloadFields(m.AgentConfigApplyResponseMessageSchema),
  };
  const paseoExcluded = {};
  for (const g of ['skills_status', 'config_apply']) for (const key of Object.keys(paseoSchema[g])) paseoExcluded[`${g}.${key}`] = 'ignored: the app never asks the daemon for this message, so no page shows it';
  const pkind = pcmd['commands[].kind']?.values ?? [];
  const cmds = pkind.slice(0, 3).map((kind, i) => ({ name: `cmd${i}`, description: `Command ${i} does a thing`, argumentHint: `<hint${i}>`, kind }));
  out.tools_paseo = {
    schema: paseoSchema,
    source: '@getpaseo/protocol messages.js',
    excluded: paseoExcluded,
    cases: [
      { id: 'paseo_commands', kind: 'list_commands', payload: { agentId: 'agent_1', commands: cmds }, parts: [
        { group: 'command', value: { agentId: 'agent_1', commands: cmds }, probes: { agentId: [], 'commands[].name': ['cmd0'], 'commands[].description': ['Command 0 does a thing'], 'commands[].argumentHint': ['<hint0>'], 'commands[].kind': [] } },
      ] },
      { id: 'paseo_commands_error', kind: 'list_commands', payload: { agentId: 'agent_1', commands: [], error: 'The agent could not list its commands' }, parts: [
        { group: 'command', value: { agentId: 'agent_1', commands: [], error: 'The agent could not list its commands' }, probes: { agentId: [], error: [] } },
      ] },
    ],
  };
  return out;
}
