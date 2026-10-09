// Families for files, changes, review, worktrees and undo:
//   oc1_files - what an OpenCode 1 server sends the Files page, the Changes
//               review, the worktree and workspace pages: /file, /file/content,
//               /find, /find/file, /find/symbol, /vcs, /vcs/status, /vcs/diff,
//               /session/{id}/diff, /experimental/worktree, /experimental/workspace.
//   oc2_files - the OpenCode 2 routes the same pages read: /api/fs/*, /api/vcs*,
//               /api/worktree and the staged undo's FileDiff.Info.
// Paseo's checkout, file and worktree messages are not read by the app at all
// (see lists_actions-style ledger files_actions_ledger.json).
import { familyBuilder } from '../oc_cases_lib.mjs';
import { only, none } from './models.mjs';

const f = (kind, values) => ({ kind, ...(values ? { values } : {}) });
const PATCH = '@@ -10,3 +10,4 @@\n-final total = sum;\n+final total = sum.roundToDouble();\n+log(total);';

export function oc1FilesFamily(contract) {
  const { schema, kase } = familyBuilder(contract, [
    { name: 'FileNode' },
    { name: 'FileContent' },
    { name: 'find:match', extra: { 'path.text': f('string'), 'lines.text': f('string'), line_number: f('number'), absolute_offset: f('number'), 'submatches[].match.text': f('string'), 'submatches[].start': f('number'), 'submatches[].end': f('number') } },
    { name: 'Symbol' },
    { name: 'VcsInfo' },
    { name: 'VcsFileStatus' },
    { name: 'VcsFileDiff' },
    { name: 'SnapshotFileDiff' },
    { name: 'Worktree' },
    { name: 'Workspace' },
    { name: 'WorkspaceEventConnectionStatus' },
    { name: 'workspace:adapter', extra: { type: f('string'), name: f('string'), description: f('string') } },
  ]);
  const u = (use, g, v, o = {}) => use(g, v, { ...o, probes: only(v, o.probes ?? {}) });
  const cases = [];
  cases.push(kase('tree', (use) => [
    u(use, 'FileNode', { name: 'checkout', path: 'lib/checkout', absolute: '/work/shopfront/lib/checkout', type: 'directory', ignored: false }, { probes: { ...none('path', 'absolute', 'ignored', 'type'), name: ['checkout'] } }),
    u(use, 'FileNode', { name: 'cart_total.dart', path: 'lib/cart_total.dart', absolute: '/work/shopfront/lib/cart_total.dart', type: 'file', ignored: false }, { probes: { ...none('path', 'absolute', 'ignored', 'type'), name: ['cart_total.dart'] } }),
    u(use, 'FileNode', { name: 'build', path: 'lib/build', absolute: '/work/shopfront/lib/build', type: 'directory', ignored: true }, { probes: { ...none('path', 'absolute', 'type', 'ignored'), name: ['build'] } }),
  ], { kind: 'tree' }));
  const textContent = { type: 'text', content: 'double total(List<double> items) {\n  return items.fold(0, (a, b) => a + b);\n}\n', diff: PATCH, patch: { oldFileName: 'a/lib/cart_total.dart', newFileName: 'b/lib/cart_total.dart', oldHeader: 'cart total before', newHeader: 'cart total after', hunks: [{ oldStart: 10, oldLines: 3, newStart: 10, newLines: 4, lines: ['-final total = sum;', '+final total = sum.roundToDouble();', '+log(total);'] }], index: 'abc123..def456' }, mimeType: 'text/x-dart' };
  cases.push(kase('file_text', (use) => {
    const out = u(use, 'FileContent', textContent, { probes: { ...none('diff', 'patch.oldFileName', 'patch.newFileName', 'patch.oldHeader', 'patch.newHeader', 'patch.hunks[].oldStart', 'patch.hunks[].oldLines', 'patch.hunks[].newStart', 'patch.hunks[].newLines', 'patch.hunks[].lines[]', 'patch.index', 'mimeType', 'type'), content: ['double total(List<double> items)'] } });
    delete out.encoding;
    return out;
  }, { kind: 'file', path: 'lib/cart_total.dart' }));
  cases.push(kase('file_binary', (use) => u(use, 'FileContent', { type: 'binary', content: 'iVBORw0KGgo=', mimeType: 'image/png' }, { probes: { ...none('content'), type: ['show this image'], mimeType: ['image'] } }), { kind: 'file', path: 'assets/logo.png' }));
  cases.push(kase('find', (use) => ({
    files: ['lib/cart_total.dart', 'test/cart_total_test.dart'],
    text: [u(use, 'find:match', { path: { text: 'lib/cart_total.dart' }, lines: { text: '  final total = sum.roundToDouble();\\n' }, line_number: 42, absolute_offset: 1280, submatches: [{ match: { text: 'roundToDouble' }, start: 18, end: 31 }] }, { probes: { 'path.text': [], 'lines.text': ['final total = sum.roundToDouble();'], line_number: [], absolute_offset: [], 'submatches[].match.text': [], 'submatches[].start': [], 'submatches[].end': [] } })],
    symbols: [u(use, 'Symbol', { name: 'CartTotal', kind: 5, location: { uri: 'file:///work/shopfront/lib/cart_total.dart', range: { start: { line: 3, character: 0 }, end: { line: 20, character: 1 } } } }, { probes: { name: ['CartTotal'], kind: ['Class'], 'location.uri': ['cart_total.dart'], 'location.range.start.line': ['cart_total.dart:4'], 'location.range.start.character': [':1'], 'location.range.end.line': [], 'location.range.end.character': [] } })],
  }), { kind: 'find' }));
  cases.push(kase('changes', (use) => ({
    info: u(use, 'VcsInfo', { branch: 'fix/cart-total', default_branch: 'main' }, { probes: { branch: ['fix/cart-total'], default_branch: ['Default branch: main'] } }),
    status: [u(use, 'VcsFileStatus', { file: 'lib/cart_total.dart', additions: 17, deletions: 5, status: 'modified' }, { probes: { file: ['lib/cart_total.dart'], additions: ['+17'], deletions: ['-5'], status: ['Modified'] } })],
    working: [u(use, 'VcsFileDiff', { file: 'lib/cart_total.dart', patch: PATCH, additions: 17, deletions: 5, status: 'modified' }, { probes: { file: ['cart_total.dart'], patch: ['sum.roundToDouble()'], additions: ['+17'], deletions: ['−5'], status: ['Modified'] } })],
    session: [u(use, 'SnapshotFileDiff', { file: 'lib/receipt.dart', patch: PATCH, additions: 23, deletions: 0, status: 'added' }, { probes: { file: ['receipt.dart'], patch: ['sum.roundToDouble()'], additions: ['+23'], deletions: ['−0'], status: ['New file'] } })],
  }), { kind: 'changes' }));
  cases.push(kase('worktrees', (use) => ({
    list: ['/work/shopfront-review', '/work/shopfront-spike'],
    created: u(use, 'Worktree', { name: 'shopfront-retry', branch: 'opencode/shopfront-retry', directory: '/work/shopfront-retry' }, { probes: { name: ['shopfront-retry'], branch: [], directory: [] } }),
  }), { kind: 'worktrees' }));
  cases.push(kase('workspaces', (use) => ({
    statuses: [u(use, 'WorkspaceEventConnectionStatus', { workspaceID: 'wrk_review', status: 'connected' }, { probes: { workspaceID: [], status: ['Connected'] } })],
    list: [u(use, 'Workspace', { id: 'wrk_review', type: 'worktree', name: 'Review box', branch: 'review/cart', directory: '/work/shopfront-review', extra: { region: 'eu' }, projectID: 'prj_shopfront', timeUsed: 1788960000000 }, { probes: { ...none('id', 'extra', 'projectID'), type: [], name: ['Review box'], branch: ['review/cart'], directory: ['shopfront-review'], timeUsed: [] } })],
    adapters: [u(use, 'workspace:adapter', { type: 'worktree', name: 'Git worktree', description: 'A separate checkout on this server' }, { probes: { type: [], name: ['Git worktree'], description: ['A separate checkout on this server'] } })],
  }), { kind: 'workspaces' }));
  return { schema, cases };
}

export function oc2FilesFamily(contract) {
  const { schema, kase } = familyBuilder(contract, [
    { name: 'FileSystem.Entry' },
    { name: 'Vcs.Info' },
    { name: 'Vcs.FileStatus' },
    { name: 'FileDiff.Info' },
    { name: 'Worktree.Info' },
    { name: 'Session.Revert' },
  ]);
  const u = (use, g, v, o = {}) => use(g, v, { ...o, probes: only(v, o.probes ?? {}) });
  const cases = [];
  cases.push(kase('fs', (use) => [
    u(use, 'FileSystem.Entry', { path: 'lib/checkout', type: 'directory' }, { probes: { path: ['checkout'], type: [] } }),
    u(use, 'FileSystem.Entry', { path: 'lib/cart_total.dart', type: 'file' }, { probes: { path: ['cart_total.dart'], type: [] } }),
  ], { kind: 'fs' }));
  cases.push(kase('changes', (use) => ({
    info: u(use, 'Vcs.Info', { branch: { current: 'fix/cart-total', default: 'main' } }, { probes: { 'branch.current': ['fix/cart-total'], 'branch.default': ['main'] } }),
    status: [u(use, 'Vcs.FileStatus', { file: 'lib/cart_total.dart', additions: 17, deletions: 5, status: 'modified' }, { probes: { file: ['cart_total.dart'], additions: ['+17'], deletions: ['−5'], status: ['Modified'] } })],
    diff: [u(use, 'FileDiff.Info', { file: 'lib/cart_total.dart', patch: PATCH, additions: 17, deletions: 5, status: 'modified' }, { probes: { file: ['cart_total.dart'], patch: ['sum.roundToDouble()'], additions: ['+17'], deletions: ['−5'], status: ['Modified'] } })],
  }), { kind: 'changes' }));
  // The undo staged from a prompt: what the Review-the-undo page lists.
  cases.push(kase('undo_staged', (use) => u(use, 'Session.Revert', {
    messageID: 'msg_files_r1', partID: 'prt_files_r1', snapshot: 'snap_files1',
    files: [{ file: 'lib/cart_total.dart', patch: PATCH, additions: 17, deletions: 5, status: 'modified' }],
  }, { probes: { ...none('messageID', 'partID', 'snapshot', 'files[].status'), 'files[].file': ['cart_total.dart'], 'files[].patch': ['sum.roundToDouble()'], 'files[].additions': ['+17'], 'files[].deletions': ['−5'] } }), { kind: 'undo' }));
  cases.push(kase('worktree_created', (use) => u(use, 'Worktree.Info', { directory: '/work/shopfront-retry' }, { probes: { directory: ['shopfront-retry'] } }), { kind: 'worktree' }));
  return { schema, cases };
}
