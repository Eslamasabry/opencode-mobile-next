// Family "oc1_lists": what an OpenCode 1 server sends the chats, projects and
// sessions lists: Session (list, get, create, update, fork, children, share),
// GlobalSession (the server-wide finder), SessionStatus, Project and
// ProjectDirectories (GET /session/{id}/todo is not read by any list screen:
// the plan reaches the person as the agent's own todo step in the chat). A case is the wire body of one endpoint; `route` names
// it for the test's loopback server.
import { familyBuilder } from '../oc_cases_lib.mjs';

const T = 1788960000000;

export function oc1ListsFamily(contract) {
  const { schema, kase } = familyBuilder(contract, [
    { name: 'Session' },
    { name: 'GlobalSession' },
    { name: 'SessionStatus' },
    { name: 'Project' },
    { name: 'ProjectDirectories' },
  ]);
  const cases = [];
  const diffs = [
    { file: 'lib/checkout/cart_total.dart', patch: '@@ -10,3 +10,4 @@\n-final total = sum;\n+final total = sum.roundToDouble();', additions: 17, deletions: 5, status: 'modified' },
    { file: 'lib/checkout/receipt.dart', patch: '@@ -0,0 +1,2 @@\n+class Receipt {}', additions: 23, deletions: 0, status: 'added' },
    { file: 'lib/checkout/old_total.dart', patch: '@@ -1,2 +0,0 @@\n-class OldTotal {}', additions: 0, deletions: 19, status: 'deleted' },
  ];
  const body = (n, extra = {}) => ({
    id: `ses_lists${n}`,
    slug: `swift-river-${n}`,
    projectID: 'prj_shopfront',
    workspaceID: 'wrk_review',
    directory: '/work/shopfront',
    path: 'src/checkout',
    summary: { additions: 32, deletions: 22, files: 3, diffs },
    cost: 0.4271,
    tokens: { input: 15200, output: 2310, reasoning: 640, cache: { read: 8800, write: 1200 } },
    share: { url: 'https://opncd.ai/s/cartTotal7' },
    title: 'Fix the cart total rounding',
    agent: 'build',
    model: { id: 'claude-sonnet-5-5', providerID: 'openrouter', variant: 'high' },
    version: '1.4.2',
    metadata: { ticket: 'SHOP-412' },
    time: { created: T, updated: T + 3600000, compacting: T + 3700000 },
    permission: [{ permission: 'bash', pattern: 'git push *', action: 'ask' }],
    revert: { messageID: 'msg_lists_r1', partID: 'prt_lists_r1', snapshot: 'snap_lists1', diff: '--- a/lib/checkout/cart_total.dart\n+++ b/lib/checkout/cart_total.dart' },
    ...extra,
  });
  // What a person reads, where a value is reworded or is not the field alone.
  const none = (...paths) => Object.fromEntries(paths.map((p) => [p, []]));
  const idsAndInternals = none('id', 'slug', 'projectID', 'workspaceID', 'version', 'metadata', 'revert.messageID', 'revert.partID', 'revert.snapshot', 'revert.diff', 'permission[].permission', 'permission[].pattern', 'permission[].action', 'summary.diffs[].patch', 'summary.diffs[].status');
  const sessionProbes = {
    ...idsAndInternals,
    path: ['src/checkout'],
    'summary.diffs[].additions': ['+17', '+23'], 'summary.diffs[].deletions': ['−5', '−19'], 'time.compacting': ['Compacting'],
    'summary.additions': ['+32'], 'summary.deletions': ['−22'], 'summary.files': ['3 files'],
    cost: ['$0.4271'],
    'tokens.input': ['28,150'], 'tokens.output': ['28,150'], 'tokens.reasoning': ['28,150'], 'tokens.cache.read': ['28,150'], 'tokens.cache.write': ['28,150'],
    'time.created': ['2h ago'], 'time.updated': ['1h ago'], 'time.archived': ['Archived ·'],
    parentID: ['Started from'],
  };
  const globalProbes = {
    ...idsAndInternals,
    path: ['src/checkout'],
    'time.updated': ['1h ago'], 'time.archived': ['Archived ·'],
    parentID: [], 'time.compacting': ['Compacting'],
    'project.id': [], 'project.worktree': [],
    ...none('cost', 'tokens.input', 'tokens.output', 'tokens.reasoning', 'tokens.cache.read', 'tokens.cache.write', 'summary.additions', 'summary.deletions', 'summary.files', 'summary.diffs[].file', 'summary.diffs[].additions', 'summary.diffs[].deletions', 'share.url', 'agent', 'model.id', 'model.providerID', 'model.variant', 'time.created', 'directory'),
  };
  const has = (obj, path) => path.split('.').reduce((o, seg) => (o == null ? undefined : seg.endsWith('[]') ? o[seg.slice(0, -2)]?.[0] : o[seg]), obj) !== undefined;
  const only = (obj, probes) => Object.fromEntries(Object.entries(probes).filter(([k]) => has(obj, k)));
  const session = (id, b, probes = {}, meta = {}) => cases.push(kase(id, (use) => use('Session', b, { probes: only(b, { ...sessionProbes, ...probes }), primary: ['title'] }), { kind: 'session', route: 'session', ...meta }));
  session('session_full', body('01'), {});
  session('session_child', (() => { const b = body('02', { title: 'Audit the cart tests (@explore subagent)', parentID: 'ses_lists01' }); delete b.share; delete b.revert; delete b.permission; delete b.summary; delete b.metadata; return b; })());
  session('session_archived', (() => { const b = body('03', { title: 'Old checkout experiment', time: { created: T - 86400000 * 9, updated: T - 86400000 * 8, archived: T - 86400000 * 7 } }); return b; })(), { 'time.created': [], 'time.updated': [] }, { archived: true });

  const g = body('11', { title: 'Server-wide checkout review', directory: '/work/storefront', project: { id: 'prj_storefront', name: 'Storefront', worktree: '/work/storefront' } });
  const global = (id, b, probes = {}, meta = {}) => cases.push(kase(id, (use) => use('GlobalSession', b, { probes: only(b, { ...globalProbes, ...probes }), primary: ['title'] }), { kind: 'global', route: 'global', ...meta }));
  global('global_full', g);
  global('global_child_archived', { id: 'ses_lists12', slug: 'quiet-pine', projectID: 'prj_storefront', version: '1.4.2', parentID: 'ses_lists11', title: 'Archived subagent run', directory: '/work/storefront', project: { id: 'prj_storefront', name: 'Storefront', worktree: '/work/storefront' }, time: { created: T - 86400000 * 3, updated: T - 86400000 * 2, archived: T - 86400000 } }, { 'time.updated': [] }, { archived: true });

  const status = (id, b, probes = {}) => cases.push(kase(id, (use) => use('SessionStatus', b, { probes: { type: [], ...probes } }), { kind: 'status', route: 'status' }));
  status('status_busy', { type: 'busy' }, { type: ['Running'] });
  status('status_idle', { type: 'idle' });
  status('status_retry', {
    type: 'retry', attempt: 3, message: 'The provider is overloaded', next: T + 30000,
    action: { reason: 'quota', provider: 'anthropic', title: 'Quota reached', message: 'Add credit to keep going', label: 'Open billing', link: 'https://console.anthropic.com/billing' },
  }, { type: ['Running'], attempt: ['3'], next: [] });

  cases.push(kase('project_full', (use) => use('Project', {
    id: 'prj_shopfront', worktree: '/work/shopfront', name: 'Shopfront',
    icon: { url: 'https://example.com/shop.png', override: 'cart', color: 'blue' },
    commands: { start: 'npm install && npm run dev' },
    time: { created: T - 86400000 * 30, updated: T, initialized: T - 86400000 * 29 },
    sandboxes: ['/work/shopfront-review'],
  }, { probes: { id: [], 'icon.url': [], 'icon.override': [], 'icon.color': [], 'commands.start': [], 'time.created': [], 'time.updated': [], 'time.initialized': [], 'sandboxes[]': ['1 worktree'] }, primary: ['name'] }), { kind: 'project', route: 'project' }));

  cases.push(kase('directories', (use) => {
    const list = [
      { directory: '/work/shopfront-review', strategy: 'worktree' },
      { directory: '/work/shopfront-spike', strategy: 'copy' },
    ];
    use('ProjectDirectories', list, { probes: { '[].directory': ['shopfront-review', 'shopfront-spike'], '[].strategy': [] } });
    return list;
  }, { kind: 'directories', route: 'directories' }));
  return { schema, cases };
}
