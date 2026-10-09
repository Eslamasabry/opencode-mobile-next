// Family "oc2_lists": what an OpenCode 2 server sends the chats, projects and
// sessions lists: Session.Info (list, get, create, fork, import), the page
// cursor of GET /session, SessionActive (GET /session/active), Project,
// Worktree.List and SessionTransfer.Data (export and import files).
// A case is the wire body of one endpoint (`route`); GET /session/stats is
// the usage screens' and is covered with them.
import { familyBuilder } from '../oc_cases_lib.mjs';

const T = 1788960000000;
const has = (obj, path) => path.split('.').reduce((o, seg) => (o == null ? undefined : seg.endsWith('[]') ? o[seg.slice(0, -2)]?.[0] : o[seg]), obj) !== undefined;
const only = (obj, probes) => Object.fromEntries(Object.entries(probes).filter(([k]) => has(obj, k)));
const none = (...paths) => Object.fromEntries(paths.map((p) => [p, []]));

export function oc2ListsFamily(contract) {
  const { schema, kase } = familyBuilder(contract, [
    { name: 'Session.Info' },
    { name: 'SessionsResponse', stop: ['Session.Info'] },
    { name: 'Project' },
    { name: 'Worktree.List' },
    { name: 'SessionTransfer.Data' },
    { name: 'InstructionEntry.Info' },
  ]);
  const cases = [];
  const info = (n, extra = {}) => ({
    id: `ses_lists${n}`,
    projectID: 'prj_shopfront',
    agent: 'build',
    model: { id: 'claude-sonnet-5-5', providerID: 'openrouter', variant: 'high' },
    cost: 0.4271,
    tokens: { input: 15200, output: 2310, reasoning: 640, cache: { read: 8800, write: 1200 } },
    outcome: 'failed',
    time: { created: T, updated: T + 3600000, idle: T + 3500000, viewed: T + 3000000 },
    title: 'Fix the cart total rounding',
    location: { directory: '/work/shopfront', workspaceID: 'wrk_review' },
    subpath: 'src/checkout',
    metadata: { 'mobile.note': 'Rerun the cart tests before merging' },
    revert: {
      messageID: 'msg_lists_r1', partID: 'prt_lists_r1', snapshot: 'snap_lists1',
      files: [{ file: 'lib/checkout/cart_total.dart', patch: '@@ -10,3 +10,4 @@\n-final total = sum;\n+final total = sum.roundToDouble();', additions: 17, deletions: 5, status: 'modified' }],
    },
    ...extra,
  });
  const internals = none('id', 'projectID', 'revert.messageID', 'revert.partID', 'revert.snapshot', 'revert.files[].file', 'revert.files[].patch', 'revert.files[].additions', 'revert.files[].deletions', 'revert.files[].status', 'fork.sessionID', 'fork.boundary.messageID', 'fork.boundary.type');
  const probes = {
    ...internals,
    cost: ['$0.4271'],
    'tokens.input': ['28,150'], 'tokens.output': ['28,150'], 'tokens.reasoning': ['28,150'], 'tokens.cache.read': ['28,150'], 'tokens.cache.write': ['28,150'],
    'time.created': ['2h ago'], 'time.updated': ['1h ago'], 'time.archived': ['Archived ·'],
    'time.idle': [], 'time.viewed': [],
    'location.directory': ['/work/shopfront'], 'location.workspaceID': [],
    subpath: ['src/checkout'],
    parentID: ['Started from'],
    metadata: ['Rerun the cart tests before merging'],
    outcome: ['Failed'],
  };
  const session = (id, b, over = {}, meta = {}) => cases.push(kase(id, (use) => use('Session.Info', b, { probes: only(b, { ...probes, ...over }), primary: ['title'] }), { kind: 'session', route: 'session', ...meta }));
  session('session_full', info('01', { fork: { sessionID: 'ses_lists00', boundary: { type: 'before', messageID: 'msg_lists_f1' } } }));
  session('session_child', (() => { const b = info('02', { title: 'Audit the cart tests (@explore subagent)', parentID: 'ses_lists01', outcome: 'succeeded' }); delete b.revert; delete b.metadata; return b; })(), { outcome: [] });
  session('session_archived', info('03', { title: 'Old checkout experiment', outcome: 'interrupted', time: { created: T - 86400000 * 9, updated: T - 86400000 * 8, archived: T - 86400000 * 7 } }), { 'time.created': [], 'time.updated': [], outcome: [] }, { archived: true });
  // A finished run nobody has opened since: the list says Done.
  session('session_done', (() => { const b = info('08', { title: 'Explain the receipt layout', outcome: 'succeeded' }); delete b.revert; delete b.metadata; return b; })(), { outcome: [], 'time.idle': ['Done'], 'location.workspaceID': [] });
  // The fork boundary's other variant.
  session('session_fork_through', info('04', { title: 'Cart total, second try', fork: { sessionID: 'ses_lists01', boundary: { type: 'through', messageID: 'msg_lists_f2' } } }));

  cases.push(kase('page_cursor', (use) => {
    const first = info('05', { title: 'Page one conversation' });
    return { data: [first], ...use('SessionsResponse', { data: [first], cursor: { previous: 'cur_prev_a1', next: 'cur_next_b2' } }, { probes: { 'data[]': ['Page one conversation'], 'cursor.previous': [], 'cursor.next': [] } }) };
  }, { kind: 'page', route: 'page' }));

  // SessionActive has one field, its fixed `type`: no ledger entry, the test
  // asserts the Running tag.
  cases.push(kase('active_running', () => ({ type: 'running' }), { kind: 'active', route: 'active' }));

  cases.push(kase('project_full', (use) => use('Project', {
    id: 'prj_shopfront', canonical: '/work/shopfront', vcs: 'git', name: 'Shopfront',
    icon: { url: 'https://example.com/shop.png', override: 'cart', color: 'blue' },
    commands: { start: 'npm install && npm run dev' },
    time: { created: T - 86400000 * 30, updated: T, initialized: T - 86400000 * 29 },
    sandboxes: ['/work/shopfront-review'],
  }, { probes: { vcs: ['Git'], ...none('id', 'icon.url', 'icon.override', 'icon.color', 'commands.start', 'time.created', 'time.updated', 'time.initialized'), 'sandboxes[]': ['1 worktree'], canonical: ['/work/shopfront'] }, primary: ['name'] }), { kind: 'project', route: 'project' }));

  cases.push(kase('worktrees', (use) => {
    const list = [{ directory: '/work/shopfront-review', strategy: 'worktree' }, { directory: '/work/shopfront-spike', strategy: 'copy' }];
    use('Worktree.List', list, { probes: { '[].directory': ['shopfront-review', 'shopfront-spike'], '[].strategy': [] } });
    return list;
  }, { kind: 'worktrees', route: 'worktrees' }));

  // The note editor reads only its own entry; the other entries never leave
  // the gateway.
  cases.push(kase('note_entries', (use) => {
    const entries = [
      { key: 'mobile.note', value: 'Rerun the cart tests before merging' },
      { key: 'team.style', value: 'Prefer tabs in generated code' },
    ];
    use('InstructionEntry.Info', entries[0], { probes: { key: [], value: ['Rerun the cart tests before merging'] } });
    // entries[1] is another tool's: the test asserts it never shows.
    return { data: entries };
  }, { kind: 'notes', route: 'notes' }));

  const messages = [
    { id: 'msg_lists_m1', type: 'user', time: { created: T }, text: 'Round the cart total' },
    { id: 'msg_lists_m2', type: 'assistant', time: { created: T + 1000 }, content: [] },
  ];
  const transfer = (id, b, over = {}, meta = {}) => cases.push(kase(id, (use) => use('SessionTransfer.Data', b, { probes: only(b, { ...Object.fromEntries(Object.entries(probes).map(([k, v]) => [`info.${k}`, v])), 'info.time.created': [], 'info.time.updated': [], 'info.time.archived': [], 'info.cost': [], 'info.tokens.input': [], 'info.tokens.output': [], 'info.tokens.reasoning': [], 'info.tokens.cache.read': [], 'info.tokens.cache.write': [], 'info.location.directory': [], 'info.subpath': [], 'info.metadata': [], 'info.parentID': ['started by another one'], 'info.title': ['Fix the cart total rounding'], 'messages[]': ['2 messages'], ...over }), primary: ['info.title'] }), { kind: 'transfer', route: 'transfer', ...meta }));
  transfer('transfer_file', { info: info('06', { fork: { sessionID: 'ses_lists00', boundary: { type: 'before', messageID: 'msg_lists_f1' } } }), messages }, { 'info.fork.sessionID': [], 'info.fork.boundary.messageID': [], 'info.fork.boundary.type': [] });
  transfer('transfer_child_archived', { info: (() => { const b = info('07', { title: 'Archived subagent run', parentID: 'ses_lists06', time: { created: T - 86400000 * 3, updated: T - 86400000 * 2, archived: T - 86400000 } }); delete b.revert; return b; })(), messages: [messages[0]] }, { 'info.title': ['Archived subagent run'], 'messages[]': ['1 message'], 'info.time.archived': ['This conversation is archived'] });
  return { schema, cases };
}
