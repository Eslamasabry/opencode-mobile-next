// Area "servers" (servers list and pages, connection, capabilities). Four
// families, each a schema of fields and the cases that cover them:
//
//   servers_wire          what a server answers when the app checks it:
//                         OpenCode 1 and 2 health/info, Paseo's server_info
//                         and provider snapshot; plus the daemon status,
//                         pairing, config and update messages the app never
//                         asks for (excluded, so the ledger must say why).
//   servers_profile       the saved server record the list, its Details and
//                         the editor show.
//   servers_capabilities  every ServerCapabilities switch against the
//                         "Available on this server" page.
//   servers_state         the connection status and the monitor's per-server
//                         snapshot the banner, the rows and the Inbox show.
//
// Wire fields come from the contracts / Paseo's zod schema; the Dart-side
// families read the field lists out of the Dart source, so a new field there
// fails this build until it has a case and a ledger decision.
import fs from 'node:fs';
import { loadContract, walker } from '../openapi_walk.mjs';
import { def, unwrap, objectFields } from '../paseo_cases_lib.mjs';

/** Field names of `final T name;` lines in a Dart class body. */
export function dartFields(file, className, { only } = {}) {
  const text = fs.readFileSync(file, 'utf8');
  const start = text.search(new RegExp(`^class ${className}\\b`, 'm'));
  if (start < 0) throw new Error(`no class ${className} in ${file}`);
  const rest = text.slice(start);
  const end = rest.search(/^}/m);
  const body = rest.slice(0, end);
  const out = [];
  for (const m of body.matchAll(/^\s{2}final\s+([\w<>?, ]+?)\s+(\w+);/gm)) out.push({ name: m[2], type: m[1].trim() });
  for (const m of body.matchAll(/^\s{2}final\s+([\w<>?, ]+?)\s+(\w+),\s*(\w+)(?:,\s*(\w+))?;/gm)) {
    out.push({ name: m[2], type: m[1].trim() }, { name: m[3], type: m[1].trim() });
    if (m[4]) out.push({ name: m[4], type: m[1].trim() });
  }
  return only ? out.filter((f) => only(f)) : out;
}

const kindOf = (type) => (/^bool/.test(type) ? 'boolean' : /^(int|num|double)/.test(type) ? 'number' : /^List/.test(type) ? 'any' : /^[A-Z]/.test(type) && !/^String/.test(type) ? 'enum' : 'string');

const NOT_ASKED = 'ignored: the app never asks the server for this message, so no page shows it';

export function serversFamilies({ oc1File, oc2File, protocolMessages }) {
  const S1 = loadContract(oc1File);
  const S2 = loadContract(oc2File);
  const w1 = walker(S1);
  const w2 = walker(S2);
  const out = {};

  // ---------------------------------------------------------------- wire
  const inline = (doc, path) => {
    const o = JSON.parse(fs.readFileSync(doc, 'utf8'));
    return o.paths[path].get.responses['200'].content['application/json'].schema;
  };
  const flat = (w, node) => {
    const fields = {};
    w.flatten(node, '', fields, new Set());
    return fields;
  };
  const m = protocolMessages;
  const payloadFields = (schema, skip = ['requestId']) => objectFields(unwrap(def(schema).shape.payload), skip);
  const prefixed = (fields, prefix) => Object.fromEntries(Object.entries(fields).map(([k, v]) => [`${prefix}${k}`, v]));
  const serverInfo = objectFields(def(m.ServerInfoStatusPayloadSchema).in, ['type', 'status']);
  const providerEntry = objectFields(m.ProviderSnapshotEntrySchema, []);
  const wireSchema = {
    'oc1.health': flat(w1, inline(oc1File, '/global/health')),
    'oc1.path': w1.group('Path'),
    'oc1.experimental_capabilities': w1.group('ExperimentalCapabilities'),
    'oc2.health': w2.group('ServiceHealth'),
    'oc2.info': {
      version: { kind: 'string' },
      pid: { kind: 'number' },
      'urls[]': { kind: 'string' },
    },
    'paseo.server_info': serverInfo,
    'paseo.provider': providerEntry,
    'paseo.daemon_status': payloadFields(m.DaemonGetStatusResponseSchema),
    'paseo.pairing_offer': payloadFields(m.DaemonGetPairingOfferResponseSchema),
    'paseo.daemon_config': payloadFields(m.GetDaemonConfigResponseMessageSchema),
    'paseo.config_reload': payloadFields(m.DaemonConfigReloadResponseSchema),
    'paseo.available_providers': payloadFields(m.ListAvailableProvidersResponseSchema),
    'paseo.daemon_update': payloadFields(m.DaemonUpdateResponseSchema),
  };
  const wireExcluded = {};
  const notAsked = (group, reason = NOT_ASKED) => {
    for (const p of Object.keys(wireSchema[group])) wireExcluded[`${group}.${p}`] = reason;
  };
  notAsked('oc1.path', 'ignored: the app never asks for the server paths; the folder a person works in comes from the project they pick');
  wireExcluded['oc1.experimental_capabilities.backgroundSubagents'] = 'ignored: read for the Tools page, not a server page; that page says in words when background helpers are missing';
  for (const g of ['paseo.daemon_status', 'paseo.pairing_offer', 'paseo.daemon_config', 'paseo.config_reload', 'paseo.available_providers', 'paseo.daemon_update']) notAsked(g);

  const allTrue = (prefix) => Object.fromEntries(Object.keys(serverInfo).filter((k) => k.startsWith(prefix)).map((k) => [k.slice(prefix.length), true]));
  const wireCases = [
    {
      id: 'oc1_health', kind: 'probe', flavor: 'oc1',
      payload: { healthy: true, version: '1.14.52' },
      parts: [{ group: 'oc1.health', value: { version: '1.14.52' }, probes: { version: ['1.14.52'] } }],
    },
    {
      id: 'oc2_beta_health', kind: 'probe', flavor: 'oc2',
      payload: { healthy: true, version: '0.0.0-beta-18600', pid: 41871 },
      parts: [{ group: 'oc2.health', value: { version: '0.0.0-beta-18600', pid: 41871 }, probes: { version: ['0.0.0-beta-18600'], pid: ['41871'] } }],
    },
    {
      id: 'oc2_stable_info', kind: 'probe', flavor: 'oc2',
      payload: { version: '2.0.10', pid: 52713, urls: ['http://127.0.0.1:4096'] },
      parts: [{ group: 'oc2.info', value: { version: '2.0.10', pid: 52713, urls: ['http://127.0.0.1:4096'] }, probes: { version: ['2.0.10'], pid: ['52713'], 'urls[]': ['127.0.0.1:4096'] } }],
    },
    {
      id: 'paseo_daemon', kind: 'probe', flavor: 'paseo',
      payload: {
        status: 'server_info',
        serverId: 'srv_9d41c0', hostname: 'build-box', version: '0.9.2', permissions: ['daemon.read'], desktopManaged: false,
        capabilities: { voice: { enabled: false } }, features: allTrue('features.'),
      },
      providers: [
        { provider: 'claude', status: 'ready', enabled: true, source: 'builtin', error: null, models: [{ provider: 'claude', id: 'opus', aliases: ['best'], isSelectable: true, label: 'Opus', description: 'Most capable', isDefault: true, metadata: { family: 'opus' }, contextWindowMaxTokens: 200000, thinkingOptions: [{ id: 'high', label: 'High', description: 'Think longer', isDefault: true, metadata: {} }], defaultThinkingOptionId: 'high' }], modes: [{ id: 'default', label: 'Always Ask', description: 'Asks before it acts', icon: 'shield', colorTier: 'safe' }], fetchedAt: '2026-10-09T08:00:00.000Z', label: 'Claude Code', description: 'Anthropic coding agent', iconSvg: '<svg/>', defaultModeId: 'default' },
        { provider: 'pi', status: 'ready', enabled: true, source: 'builtin', models: [], modes: [] },
        { provider: 'codex', status: 'error', enabled: true, source: 'builtin', error: 'codex is not installed on this machine', models: [], modes: [] },
        { provider: 'copilot', status: 'unavailable', enabled: false, source: 'custom', models: [], modes: [] },
        { provider: 'opencode', status: 'loading', enabled: true, source: 'builtin', models: [], modes: [] },
      ],
      parts: [],
    },
  ];
  // Wire the paseo case's parts: server_info once, one provider part per entry.
  {
    const c = wireCases[3];
    const { status, ...info } = c.payload;
    c.parts.push({
      group: 'paseo.server_info',
      value: info,
      probes: {
        version: ['0.9.2'],
        serverId: [], hostname: [], 'permissions[]': [], desktopManaged: [], capabilities: [],
        ...Object.fromEntries(Object.keys(serverInfo).filter((k) => k.startsWith('features.')).map((k) => [k, []])),
      },
    });
    const names = { claude: 'Claude Code', pi: 'Pi' };
    for (const entry of c.providers) {
      const value = { ...entry };
      if (value.error === null) delete value.error;
      const probes = {};
      if (names[entry.provider]) probes.provider = [names[entry.provider]];
      else probes.provider = [];
      for (const k of ['status', 'enabled', 'source', 'error']) if (k in value) probes[k] = [];
      if (entry.provider === 'claude') {
        for (const k of Object.keys(providerEntry)) if (!(k in probes) && k.split('.')[0].replace('[]', '') in value) probes[k] = [];
      }
      c.parts.push({ group: 'paseo.provider', value, probes });
    }
  }

  out.servers_wire = { schema: wireSchema, cases: wireCases, excluded: wireExcluded, source: `${oc1File}, ${oc2File}, docs/opencode2-protocol-notes.md, @getpaseo/protocol messages.js` };

  // ------------------------------------------------------------- profile
  const profileJson = fs.readFileSync('lib/state/profiles/models.dart', 'utf8');
  const classAt = profileJson.indexOf('class ServerProfile {');
  const toJson = profileJson.slice(profileJson.indexOf('Map<String, dynamic> toJson() => {', classAt));
  const toJsonKeys = [...toJson.slice(0, toJson.indexOf('};')).matchAll(/'(\w+)':/g)].map((x) => x[1]);
  const expectedKeys = ['id', 'name', 'baseUrl', 'backend', 'username', 'codexDirectory', 'flavor', 'serverVersion', 'orchestration'];
  if (JSON.stringify(toJsonKeys) !== JSON.stringify(expectedKeys)) {
    throw new Error(`ServerProfile.toJson keys changed (${toJsonKeys}); add the new field to tool/coverage/families/servers.mjs`);
  }
  const profileSchema = {
    'profile': {
      id: { kind: 'string' }, name: { kind: 'string' }, baseUrl: { kind: 'string' },
      backend: { kind: 'enum', values: ['openCode', 'codex', 'paseo'] },
      username: { kind: 'string' }, codexDirectory: { kind: 'string' },
      flavor: { kind: 'enum', values: ['v1', 'v2'] }, serverVersion: { kind: 'string' }, orchestration: { kind: 'any' },
    },
  };
  const oc1 = { id: 'srv_oc1', name: 'Studio OpenCode', baseUrl: 'https://studio.example.net:4096', backend: 'openCode', username: 'devuser', flavor: 'v1', serverVersion: '1.14.52' };
  const oc2 = { id: 'srv_oc2', name: 'Lab OpenCode', baseUrl: 'https://lab.example.net:4097', backend: 'openCode', username: 'opencode', flavor: 'v2', serverVersion: '2.0.10' };
  const paseo = { id: 'srv_paseo', name: 'Build box', baseUrl: 'ws://build.example.net:6767', backend: 'paseo', username: '', codexDirectory: '/home/dev/work/shop', flavor: 'v1', serverVersion: 'Paseo daemon 0.8.0 (experimental)' };
  const codex = { id: 'srv_codex', name: 'Codex desk', baseUrl: 'wss://codex.example.net', backend: 'codex', codexDirectory: '/home/dev/work/api', username: '', flavor: 'v1' };
  const team = { ...oc1, id: 'srv_team', name: 'Team host', baseUrl: 'https://team.example.net:4096', orchestration: { enabled: true } };
  const pcase = (id, rec, probes, extra = {}) => ({ id, kind: 'profile', payload: rec, parts: [{ group: 'profile', value: rec, probes }], ...extra });
  out.servers_profile = {
    schema: profileSchema,
    source: 'lib/state/profiles/models.dart ServerProfile.toJson',
    cases: [
      pcase('profile_opencode1', oc1, { id: [], name: ['Studio OpenCode'], baseUrl: ['studio.example.net:4096'], backend: ['OpenCode'], username: ['devuser'], flavor: ['OpenCode 1'], serverVersion: ['1.14.52'] }),
      pcase('profile_opencode2', oc2, { id: [], name: ['Lab OpenCode'], baseUrl: ['lab.example.net:4097'], backend: ['OpenCode'], username: ['opencode'], flavor: ['OpenCode 2'], serverVersion: ['2.0.10'] }),
      pcase('profile_paseo', paseo, { id: [], name: ['Build box'], baseUrl: ['build.example.net:6767'], backend: ['Claude Code or Pi'], codexDirectory: ['/home/dev/work/shop'], serverVersion: ['0.8.0'] }),
      pcase('profile_codex', codex, { id: [], name: ['Codex desk'], baseUrl: ['codex.example.net'], backend: ['Codex'], codexDirectory: ['/home/dev/work/api'] }),
      pcase('profile_team', team, { id: [], name: ['Team host'], baseUrl: ['team.example.net:4096'], backend: ['OpenCode'], username: ['devuser'], flavor: ['OpenCode 1'], serverVersion: ['1.14.52'], orchestration: [] }),
    ],
  };

  // -------------------------------------------------------- capabilities
  const caps = dartFields('lib/domain/server_gateway/capabilities.dart', 'ServerCapabilities').filter((f) => f.type === 'bool');
  out.servers_capabilities = {
    schema: { capability: Object.fromEntries(caps.map((f) => [f.name, { kind: 'boolean' }])) },
    source: 'lib/domain/server_gateway/capabilities.dart ServerCapabilities',
    cases: [],
    custom: true,
  };

  // --------------------------------------------------------------- state
  const status = dartFields('lib/domain/connection_status.dart', 'ConnectionStatusSnapshot');
  const attention = dartFields('lib/domain/profile_monitor.dart', 'ProfileAttentionSnapshot');
  const wantStatus = ['phase', 'profileId', 'serverName', 'since', 'usesToken', 'retrying', 'quiet', 'attemptRevision'];
  const wantAttention = ['profileID', 'status', 'checkedAt', 'nextCheckAt', 'directory', 'workspace', 'requests', 'attention', 'attentionComplete', 'busyIntervals', 'runningCount', 'complete'];
  const same = (a, b) => JSON.stringify([...a].sort()) === JSON.stringify([...b].sort());
  if (!same(status.map((f) => f.name), wantStatus)) throw new Error(`ConnectionStatusSnapshot fields changed (${status.map((f) => f.name)}); update tool/coverage/families/servers.mjs`);
  if (!same(attention.map((f) => f.name), wantAttention)) throw new Error(`ProfileAttentionSnapshot fields changed (${attention.map((f) => f.name)}); update tool/coverage/families/servers.mjs`);
  const stateSchema = {
    connection: {
      phase: { kind: 'enum', values: ['hidden', 'connecting', 'reconnecting', 'connected', 'credentialsRequired', 'credentialsUnreadable', 'notAnswering'] },
      profileId: { kind: 'string' }, serverName: { kind: 'string' }, since: { kind: 'string' },
      usesToken: { kind: 'boolean' }, retrying: { kind: 'boolean' }, quiet: { kind: 'boolean' }, attemptRevision: { kind: 'number' },
    },
    attention: {
      profileID: { kind: 'string' },
      status: { kind: 'enum', values: ['disabled', 'waiting', 'checking', 'current', 'unavailable', 'wifiRequired', 'paused'] },
      checkedAt: { kind: 'string' }, nextCheckAt: { kind: 'string' }, directory: { kind: 'string' }, workspace: { kind: 'string' },
      'requests[].id': { kind: 'string' }, 'requests[].sessionID': { kind: 'string' },
      'requests[].kind': { kind: 'enum', values: ['permission', 'question', 'form', 'checkIn'] },
      'requests[].title': { kind: 'string' }, 'requests[].directory': { kind: 'string' }, 'requests[].workspace': { kind: 'string' },
      attention: { kind: 'any' }, attentionComplete: { kind: 'boolean' },
      'busyIntervals[].sessionID': { kind: 'string' }, 'busyIntervals[].firstObservedBusyAt': { kind: 'string' },
      'busyIntervals[].lastObservedBusyAt': { kind: 'string' }, 'busyIntervals[].title': { kind: 'string' },
      'busyIntervals[].directory': { kind: 'string' }, 'busyIntervals[].workspace': { kind: 'string' },
      'busyIntervals[].reminderClaimed': { kind: 'boolean' },
      runningCount: { kind: 'number' }, complete: { kind: 'boolean' },
    },
  };
  const conn = (id, value, probes) => ({ id, kind: 'connection', payload: value, parts: [{ group: 'connection', value, probes }] });
  const base = { profileId: 'srv_unlisted', serverName: 'Studio OpenCode', since: '2026-10-09T09:00:00.000Z', usesToken: false, retrying: false, quiet: false, attemptRevision: 3 };
  const att = (id, rec, probes, extra = {}) => ({
    id, kind: 'attention', payload: rec,
    parts: [{ group: 'attention', value: rec, probes }],
    ...extra,
  });
  const attBase = { status: 'current', checkedAt: '2026-10-09T09:30:00.000Z', nextCheckAt: '2026-10-09T09:31:00.000Z', directory: '/work/shop', attentionComplete: true, complete: true };
  out.servers_state = {
    schema: stateSchema,
    source: 'lib/domain/connection_status.dart, lib/domain/profile_monitor.dart',
    cases: [
      conn('connection_reconnecting', { ...base, phase: 'reconnecting' }, { phase: ['Reconnecting to Studio OpenCode'], profileId: [], serverName: ['Studio OpenCode'], since: [], usesToken: [], retrying: [], quiet: [], attemptRevision: [] }),
      conn('connection_connecting', { ...base, phase: 'connecting' }, { phase: ['Connecting to Studio OpenCode'], profileId: [], serverName: ['Studio OpenCode'], since: [], usesToken: [], retrying: [], quiet: [], attemptRevision: [] }),
      conn('connection_token_rejected', { ...base, phase: 'credentialsRequired', usesToken: true }, { phase: ['rejected the connection token'], profileId: [], serverName: [], since: [], usesToken: ['Update token'], retrying: [], quiet: [], attemptRevision: [] }),
      conn('connection_password_unreadable', { ...base, phase: 'credentialsUnreadable' }, { phase: ["Can't read the saved password for Studio OpenCode"], profileId: [], serverName: ['Studio OpenCode'], since: [], usesToken: [], retrying: [], quiet: [], attemptRevision: [] }),
      conn('connection_not_answering', { ...base, phase: 'notAnswering', retrying: true }, { phase: ["Studio OpenCode isn't answering"], profileId: [], serverName: ['Studio OpenCode'], since: [], usesToken: [], retrying: [], quiet: [], attemptRevision: [] }),
      conn('connection_quiet', { ...base, phase: 'hidden', quiet: true }, { phase: [], profileId: [], serverName: [], since: [], usesToken: [], retrying: [], quiet: [], attemptRevision: [] }),
      conn('connection_connected', { ...base, phase: 'connected' }, { phase: [], profileId: [], serverName: [], since: [], usesToken: [], retrying: [], quiet: [], attemptRevision: [] }),
      att('attention_waiting_and_working', {
        ...attBase, profileID: 'srv_oc2', workspace: 'wrk_main', runningCount: 2,
        requests: [{ id: 'per_up1', sessionID: 'ses_up1', kind: 'permission', title: 'Fix the upload retry', directory: '/work/shop', workspace: 'wrk_main' }],
        attention: [],
        busyIntervals: [],
      }, {
        profileID: [], status: [], checkedAt: [], nextCheckAt: [], directory: [], workspace: [], runningCount: ['2 working'], attention: [], attentionComplete: [], complete: [],
        'requests[].id': [], 'requests[].sessionID': [], 'requests[].kind': ['Needs your OK'], 'requests[].title': ['Fix the upload retry'], 'requests[].directory': [], 'requests[].workspace': [],
      }),
      att('attention_question_and_form', {
        ...attBase, profileID: 'srv_oc2', runningCount: 0,
        requests: [
          { id: 'que_up2', sessionID: 'ses_up2', kind: 'question', title: 'Pick the retry limit', directory: '/work/shop' },
          { id: 'frm_up3', sessionID: 'ses_up3', kind: 'form', title: 'Connect Sentry', directory: '/work/shop' },
        ],
        attention: [], busyIntervals: [],
      }, { 'requests[].id': [], 'requests[].sessionID': [], 'requests[].kind': ['Needs your decision'], 'requests[].title': ['Pick the retry limit', 'Connect Sentry'], 'requests[].directory': [], runningCount: [], profileID: [], status: [], checkedAt: [], nextCheckAt: [], directory: [], attention: [], attentionComplete: [], complete: [] }),
      att('attention_check_in', {
        ...attBase, profileID: 'srv_oc2', runningCount: 1, requests: [], attention: [],
        busyIntervals: [{ sessionID: 'ses_long', firstObservedBusyAt: '2026-10-09T08:40:00.000Z', lastObservedBusyAt: '2026-10-09T09:29:00.000Z', title: 'Migrate the billing tables', directory: '/work/billing', workspace: 'wrk_bill', reminderClaimed: false }],
      }, { 'busyIntervals[].title': ['Migrate the billing tables'], 'busyIntervals[].sessionID': [], 'busyIntervals[].firstObservedBusyAt': [], 'busyIntervals[].lastObservedBusyAt': [], 'busyIntervals[].directory': [], 'busyIntervals[].workspace': [], 'busyIntervals[].reminderClaimed': [], runningCount: [], profileID: [], status: [], checkedAt: ['Last checked'], nextCheckAt: [], directory: [], attention: [], attentionComplete: [], complete: [] }),
      att('attention_waiting_to_check', { profileID: 'srv_oc2', status: 'waiting', requests: [], attention: [], busyIntervals: [], attentionComplete: false, complete: false }, { profileID: [], status: [], attention: [], attentionComplete: [], complete: [] }),
      att('attention_unavailable', { profileID: 'srv_oc2', status: 'unavailable', requests: [], attention: [], busyIntervals: [], attentionComplete: false, complete: false }, { profileID: [], status: [], attention: [], attentionComplete: [], complete: [] }),
    ],
  };
  return out;
}
