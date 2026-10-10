// Area "terminal": the server's terminals (OpenCode pty endpoints), this
// phone's Termux process list and storage report, and the Paseo terminal
// messages the Terminal page sends. Families:
//
//   terminal_wire     OpenCode 1/2 GET /pty, GET /pty/shells and the default
//                     shell, plus what the app never shows (connect ticket,
//                     pty events: the list simply reloads).
//   terminal_termux   the Termux scanner's process JSON, the stop result and
//                     the storage report the screens read off oc/termux.
//   terminal_paseo    Paseo's terminal messages (excluded: plumbing, the page
//                     shows the result).
import fs from 'node:fs';
import { loadContract, walker } from '../openapi_walk.mjs';
import { def, unwrap, objectFields } from '../paseo_cases_lib.mjs';

const LOC = { directory: '/work/shop', workspaceID: 'wrk_main', project: { id: 'prj_shop', directory: '/work/shop' } };
const none = (obj) => Object.fromEntries(Object.keys(obj).map((k) => [k, []]));

// Paseo terminals are on the Terminal page: the fields below are plumbing.
const paseoTerminalReason = (k) =>
  k.startsWith('capture_terminal')
    ? "ignored: the app reads a terminal's output live while it is open, not as a saved capture, so no page shows this"
    : k.includes('activity') || k.startsWith('terminal_attention')
      ? "ignored: the daemon's activity and attention hints are not shown; the list only says a terminal is running"
      : /^(un)?subscribe_terminals|^terminals_changed/.test(k)
        ? "ignored: the list is read again when the Terminal page opens or is pulled down; the daemon's live list updates are not used"
        : "ignored: this only carries the Terminal page's own list, open, type, rename and close to the daemon; the page shows the result, not the field";

export function terminalFamilies({ oc1File, protocolMessages }) {
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
  const locFields = under(flatOf('/api/pty'), 'location.');
  const excluded = {};
  const never = (group, reason) => { for (const k of Object.keys(schema[group])) excluded[`${group}.${k}`] = reason; };
  const schema = {
    location: locFields,
    pty: w.group('Pty'),
    shell: under(flatOf('/pty/shells'), '[].'),
    config: { shell: { kind: 'string' } },
    ticket: w.group('PtyTicketConnectToken'),
    event_created: w.group('EventPtyCreated'),
    event_updated: w.group('EventPtyUpdated'),
    event_exited: w.group('EventPtyExited'),
    event_deleted: w.group('EventPtyDeleted'),
  };
  never('ticket', 'ignored: a one-time pass the app uses to open the live connection; it is never shown');
  for (const g of ['event_created', 'event_updated', 'event_exited', 'event_deleted']) {
    never(g, 'ignored: the event only tells the app the list changed; it reads the list again and shows that');
  }
  const running = { id: 'pty_run1', title: 'Build watcher', command: 'bash', args: ['-l'], cwd: '/work/shop', status: 'running', pid: 4242 };
  const exited = { id: 'pty_done1', title: 'Test run', command: 'flutter', args: ['test', 'test/upload_test.dart'], cwd: '/work/shop/app', status: 'exited', pid: 4343, exitCode: 1 };
  const shells = [{ path: '/bin/bash', name: 'bash', acceptable: true }, { path: '/usr/bin/fish', name: 'fish', acceptable: false }];
  const ptyProbes = (v, extra) => ({ id: [], title: [v.title], command: [], 'args[]': [], cwd: [], status: [], pid: [], ...('exitCode' in v ? { exitCode: [] } : {}), ...extra });
  const locPart = { group: 'location', value: LOC, probes: none(locFields) };
  out.terminal_wire = {
    schema, excluded, source: oc1File,
    cases: [
      { id: 'pty_running_and_exited', kind: 'pty_list', payload: { list: [running, exited] }, parts: [
        locPart,
        { group: 'pty', value: running, probes: ptyProbes(running, { command: ['bash'], cwd: ['/work/shop'], status: ['Running'], 'args[]': ['bash -l'], pid: ['4242'] }) },
        { group: 'pty', value: exited, probes: ptyProbes(exited, { command: ['flutter'], 'args[]': ['test/upload_test.dart'], cwd: ['/work/shop/app'], status: ['Ended'], exitCode: ['1'], pid: ['4343'] }) },
      ] },
      { id: 'shells_and_default', kind: 'shells', payload: { shells, config: { shell: 'fish' } }, parts: [
        { group: 'shell', value: shells[0], probes: { path: [], name: ['bash'], acceptable: [] } },
        { group: 'shell', value: shells[1], probes: { path: [], name: ['fish'], acceptable: ['terminal'] } },
        { group: 'config', value: { shell: 'fish' }, probes: { shell: ['fish'] } },
      ] },
    ],
  };

  // ---------------------------------------------------------------- termux
  const tSchema = {
    process: {
      pid: { kind: 'number' }, ppid: { kind: 'number' },
      group: { kind: 'enum', values: ['orphans', 'opencode_server', 'ai_team', 'build_daemons', 'other'] },
      name: { kind: 'string' }, cmd: { kind: 'string' }, cpu_pct: { kind: 'number' }, cpu_seconds: { kind: 'number' },
      rss_kb: { kind: 'number' }, elapsed_s: { kind: 'number' }, cwd: { kind: 'string' },
      orphan_reason: { kind: 'enum', values: ['parent_gone', 'cpu_no_owner'] }, protected: { kind: 'boolean' },
    },
    stop: {
      'stopped[]': { kind: 'number' }, 'killed[]': { kind: 'number' },
      'remaining[].pid': { kind: 'number' }, 'remaining[].name': { kind: 'string' },
      'refused[].pid': { kind: 'number' }, 'refused[].reason': { kind: 'string' },
    },
    scan_state: { state: { kind: 'enum', values: ['idle', 'running', 'done', 'cancelled', 'failed', 'stale'] } },
    storage: { scanned_at: { kind: 'number' }, total_bytes: { kind: 'number' }, stale: { kind: 'boolean' }, cleanup_policy: { kind: 'number' } },
    category: {
      key: { kind: 'enum', values: ['build_caches', 'agent_scratch', 'project_build_outputs', 'toolchains', 'ai_team', 'opencode', 'projects', 'shared_caches'] },
      label_key: { kind: 'string' }, note_key: { kind: 'string' }, bytes: { kind: 'number' }, deletable: { kind: 'boolean' },
      'paths[].path': { kind: 'string' }, 'paths[].bytes': { kind: 'number' },
    },
    project: { name: { kind: 'string' }, path: { kind: 'string' }, bytes: { kind: 'number' }, build_bytes: { kind: 'number' } },
  };
  const procOrphan = { pid: 5001, ppid: 1, group: 'orphans', name: 'stray.py', cmd: 'python3 /root/stray.py', cpu_pct: 97.5, cpu_seconds: 3700, rss_kb: 91136, elapsed_s: 3725, cwd: '/root/scripts', orphan_reason: 'parent_gone', protected: false };
  const procOrphan2 = { ...procOrphan, pid: 5002, name: 'spin.js', cmd: 'node /root/spin.js', orphan_reason: 'cpu_no_owner', cwd: '/root/js' };
  const procServer = { pid: 5100, ppid: 1, group: 'opencode_server', name: 'opencode', cmd: 'opencode serve --port 4096', cpu_pct: 1.5, cpu_seconds: 120, rss_kb: 240640, elapsed_s: 7300, cwd: '', orphan_reason: null, protected: true };
  const procTeam = { pid: 5200, ppid: 5100, group: 'ai_team', name: 'gc', cmd: 'gc start', cpu_pct: 0, cpu_seconds: 5, rss_kb: 20480, elapsed_s: 600, cwd: '', orphan_reason: null, protected: false };
  const procBuild = { pid: 5300, ppid: 1, group: 'build_daemons', name: 'java', cmd: 'java GradleDaemon 8.5', cpu_pct: 0.5, cpu_seconds: 90, rss_kb: 300032, elapsed_s: 4000, cwd: '/work/shop', orphan_reason: null, protected: false };
  const procOther = { pid: 5400, ppid: 1, group: 'other', name: 'bash', cmd: 'bash', cpu_pct: 0, cpu_seconds: 1, rss_kb: 4096, elapsed_s: 900, cwd: '/root', orphan_reason: null, protected: false };
  const strip = (v) => Object.fromEntries(Object.entries(v).filter(([, x]) => x !== null));
  const kindWord = { orphans: 'Helper', opencode_server: 'OpenCode server', ai_team: 'AI Team', build_daemons: 'Dev service', other: 'Terminal' };
  const span = (sec) => { const h = Math.floor(sec / 3600); const m = Math.floor((sec % 3600) / 60); return h ? `${h} h ${m} min` : `${m} min`; };
  const mb = (kb) => `${Math.round(kb / 1024)} MB`;
  const pv = (v) => ({
    pid: [String(v.pid)], ppid: [], group: [kindWord[v.group]], name: [v.name], cmd: v.protected ? [] : [v.cmd], cpu_pct: [], cpu_seconds: [],
    rss_kb: [mb(v.rss_kb)], elapsed_s: [span(v.elapsed_s)], cwd: v.cwd ? [v.cwd] : [],
    ...('orphan_reason' in strip(v) ? { orphan_reason: [v.orphan_reason === 'parent_gone' ? 'Parent gone' : 'No owner'] } : {}),
    protected: v.protected ? ['Protected'] : [],
  });
  const stop = { stopped: [5002], killed: [5001], remaining: [{ pid: 5300, name: 'java' }], refused: [{ pid: 5100, reason: 'protected' }] };
  const cats = [
    { key: 'build_caches', label_key: 'build_caches', note_key: 'build_caches_note', bytes: 734003200, deletable: true, paths: [{ path: '/root/.gradle/caches', bytes: 500000000 }, { path: '/root/.pub-cache/hosted', bytes: 234003200 }] },
    { key: 'projects', label_key: 'projects', note_key: 'projects_note', bytes: 52428800, deletable: false, paths: [] },
  ];
  const projects = [{ name: 'shop', path: '/root/work/shop', bytes: 41943040, build_bytes: 10485760 }];
  const report = { scanned_at: 1788950000, total_bytes: 1073741824, stale: false, cleanup_policy: 2, categories: cats, projects };
  out.terminal_termux = {
    schema: tSchema, excluded: {}, source: 'lib/termux/processes.dart, lib/termux/storage.dart (the scripts\' JSON)',
    cases: [
      { id: 'processes_all_groups', kind: 'processes', payload: [procOrphan, procOrphan2, procServer, procTeam, procBuild, procOther], parts: [
        { group: 'process', value: strip(procOrphan), probes: pv(procOrphan) },
        { group: 'process', value: strip(procOrphan2), probes: pv(procOrphan2) },
        { group: 'process', value: strip(procServer), probes: pv(procServer) },
        { group: 'process', value: strip(procTeam), probes: pv(procTeam) },
        { group: 'process', value: strip(procBuild), probes: pv(procBuild) },
        { group: 'process', value: strip(procOther), probes: pv(procOther) },
      ] },
      { id: 'stop_result', kind: 'stop', payload: stop, parts: [
        { group: 'stop', value: stop, probes: { 'stopped[]': ['Stopped 2'], 'killed[]': ['1 needed a forced stop'], 'remaining[].pid': ['1 would not stop'], 'remaining[].name': [], 'refused[].pid': [], 'refused[].reason': ['1 protected, not stopped'] } },
      ] },
      { id: 'storage_report', kind: 'storage', payload: { state: 'done', report }, parts: [
        { group: 'scan_state', value: { state: 'done' }, probes: { state: ['Scanned 2 min ago'] } },
        { group: 'storage', value: { scanned_at: report.scanned_at, total_bytes: report.total_bytes, stale: false, cleanup_policy: 2 }, probes: { scanned_at: ['Scanned 2 min ago'], total_bytes: ['1 GB'], stale: [], cleanup_policy: [] } },
        { group: 'category', value: cats[0], probes: { key: ['Build caches'], label_key: [], note_key: [], bytes: ['700 MB'], deletable: ['Clean build caches'], 'paths[].path': ['/root/.gradle/caches'], 'paths[].bytes': ['476 MB'] } },
        { group: 'category', value: cats[1], probes: { key: ['Projects (your files)'], label_key: [], note_key: [], bytes: [], deletable: [] } },
        { group: 'project', value: projects[0], probes: { name: ['shop'], path: [], bytes: ['40 MB'], build_bytes: ['10 MB in build-related folders'] } },
      ] },
      ...['idle', 'running', 'cancelled', 'failed', 'stale'].map((state) => ({ id: `storage_${state}`, kind: 'storage', payload: { state }, parts: [{ group: 'scan_state', value: { state }, probes: { state: [{ idle: 'Not scanned yet', running: 'Measuring storage', cancelled: 'Scan stopped', failed: 'The scan did not finish', stale: 'Not scanned yet' }[state]] } }] })),
    ],
  };

  // ----------------------------------------------------------------- paseo
  const m = protocolMessages;
  const terms = Object.entries(m).filter(([k]) => /Terminal/.test(k) && /(Request|Response|Message)Schema$|Schema$/.test(k)).map(([k, v]) => [k, v]);
  const pSchema = {};
  const pExcluded = {};
  for (const [k, v] of terms) {
    try {
      const d = def(v);
      if (d.type !== 'object' || !d.shape?.type) continue;
      const type = def(d.shape.type)?.values?.[0];
      if (typeof type !== 'string' || !/terminal/i.test(type)) continue;
      const fields = objectFields(unwrap(v), ['type']);
      pSchema[type] = fields;
      for (const f of Object.keys(fields)) pExcluded[`${type}.${f}`] = paseoTerminalReason(`${type}.${f}`);
    } catch { /* not a message */ }
  }
  out.terminal_paseo = { schema: pSchema, excluded: pExcluded, source: '@getpaseo/protocol messages.js', cases: [] };
  return out;
}
