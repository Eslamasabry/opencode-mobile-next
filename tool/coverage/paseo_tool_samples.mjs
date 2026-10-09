// Gate 1 inventory: reads Paseo's own protocol schema, lists every field of
// every tool-step type, and writes realistic samples (one per real-world case)
// that a test runs through the app's mapper and tool card.
//
// The field list comes from the schema, never from this file. The cases below
// only say which fields travel together in a real call (how Paseo's producer
// code fills them: providers/tool-call-detail-primitives.js and
// providers/claude/*). The build FAILS when
//   - a schema field appears in no case (a new Paseo field must get a case), or
//   - a case uses a field the schema does not have.
//
//   node tool/coverage/paseo_tool_samples.mjs <paseo protocol dist/messages.js> <out.json>
import { pathToFileURL } from 'node:url';
import { writeFileSync } from 'node:fs';

const [, , protocolPath, outPath] = process.argv;
const { AgentTimelineItemPayloadSchema } = await import(pathToFileURL(protocolPath).href);

const def = (s) => s._zod.def;
function unwrap(s) {
  for (;;) {
    const d = def(s);
    if (['optional', 'nullable', 'default', 'prefault', 'readonly', 'catch'].includes(d.type)) s = d.innerType;
    else if (d.type === 'lazy') s = d.getter();
    else if (d.type === 'pipe') s = d.in;
    else return s;
  }
}
const toolCall = def(AgentTimelineItemPayloadSchema).options
  .map(unwrap)
  .flatMap((o) => (def(o).type === 'union' ? def(o).options.map(unwrap) : [o]))
  .find((o) => def(o).type === 'object' && def(def(o).shape.type).values?.includes('tool_call'));
const detailUnion = unwrap(def(toolCall).shape.detail);

// ---- 1. every field of every step type, from the schema -------------------
const schema = {}; // type -> { path: {kind, values?} }
function walk(s, path, out) {
  const u = unwrap(s);
  const d = def(u);
  switch (d.type) {
    case 'object':
      for (const [k, v] of Object.entries(d.shape)) walk(v, path ? `${path}.${k}` : k, out);
      return;
    case 'array': walk(d.element, `${path}[]`, out); return;
    case 'literal': return;
    case 'enum': out[path] = { kind: 'enum', values: Object.values(d.entries) }; return;
    case 'union': out[path] = { kind: 'any' }; return;
    case 'int': out[path] = { kind: 'number' }; return;
    default: out[path] = { kind: d.type };
  }
}
for (const o of def(detailUnion).options) {
  const type = def(def(o).shape.type).values[0];
  const fields = {};
  for (const [k, v] of Object.entries(def(o).shape)) if (k !== 'type') walk(v, k, fields);
  schema[type] = fields;
}

// ---- 2. the real-world cases ----------------------------------------------
// tool: the name the provider gives the call. probes: what a person should
// read on screen for a field whose value is reworded (counts, enums).
const cases = [
  {
    id: 'worktree_setup', type: 'worktree_setup', tool: 'paseo_worktree_setup', status: 'completed',
    detail: {
      worktreePath: '/home/dev/.paseo/worktrees/app/feat-login',
      branchName: 'feat/login-form',
      log: 'Preparing worktree for feat/login-form',
      commands: [
        { index: 14, command: 'npm install', cwd: 'packages/web', log: 'added 212 packages', status: 'completed', exitCode: 0, durationMs: 48213 },
        { index: 15, command: 'npm run build', cwd: 'packages/web', log: 'Build failed: missing env file', status: 'failed', exitCode: 2, durationMs: 9120 },
      ],
      truncated: true,
    },
    probes: { 'commands[].status': ['Passed', 'Failed'], 'commands[].exitCode': ['exit code 0', 'exit code 2'], truncated: ['Truncated'] },
  },
  {
    id: 'shell_ok', type: 'shell', tool: 'Bash', status: 'completed',
    detail: { command: 'flutter test test/login_test.dart', cwd: '/home/dev/app', output: '00:04 +12: All tests passed!', exitCode: 0 },
    probes: { exitCode: ['Passed · exit code 0'] },
  },
  {
    id: 'shell_exit_failed', type: 'shell', tool: 'Bash', status: 'completed',
    detail: { command: 'flutter analyze', cwd: '/home/dev/app', output: '2 issues found.', exitCode: 3 },
    probes: { exitCode: ['Failed · exit code 3'] },
  },
  { id: 'shell_failed', type: 'shell', tool: 'Bash', status: 'failed', error: { message: 'Command failed' }, detail: { command: 'git push origin main' } },
  { id: 'shell_running', type: 'shell', tool: 'Bash', status: 'running', detail: { command: 'npm run dev' } },
  {
    id: 'read_ok', type: 'read', tool: 'Read', status: 'completed',
    detail: { filePath: 'lib/main.dart', content: 'void main() {\n  runApp(const App());\n}', offset: 12, limit: 40 },
    probes: { filePath: ['main.dart'], offset: ['from 12'], limit: ['40 lines'] },
  },
  {
    id: 'edit_old_new', type: 'edit', tool: 'Edit', status: 'completed',
    detail: { filePath: 'lib/ui/login_form.dart', oldString: 'final label = "Sign in";', newString: 'final label = "Log in";' },
    probes: { filePath: ['login_form.dart'] },
  },
  {
    id: 'edit_diff', type: 'edit', tool: 'Edit', status: 'completed',
    detail: {
      filePath: 'lib/ui/login_form.dart',
      unifiedDiff: '--- a/lib/ui/login_form.dart\n+++ b/lib/ui/login_form.dart\n@@ -1,2 +1,2 @@\n-final retries = 1;\n+final retries = 3;',
    },
    probes: { filePath: ['login_form.dart'], unifiedDiff: ['final retries = 3;'] },
  },
  { id: 'write_ok', type: 'write', tool: 'Write', status: 'completed', detail: { filePath: 'lib/ui/new_page.dart', content: 'class NewPage {}' }, probes: { filePath: ['new_page.dart'] } },
  {
    id: 'grep_content', type: 'search', tool: 'Grep', status: 'completed',
    detail: {
      query: 'TODO', toolName: 'grep', mode: 'content',
      content: 'lib/a.dart:12: // TODO tidy\nlib/b.dart:40: // TODO remove',
      numFiles: 2, numMatches: 5,
    },
    probes: { toolName: ['Search text'], numMatches: ['5 matches'], numFiles: ['2 files'] },
  },
  {
    id: 'grep_files', type: 'search', tool: 'Grep', status: 'completed',
    detail: {
      query: 'LoginForm', toolName: 'grep', mode: 'files_with_matches',
      filePaths: ['lib/ui/login_form.dart', 'test/login_form_test.dart'], numFiles: 2,
    },
    probes: { toolName: ['Search text'], numFiles: ['2 files'] },
  },
  {
    id: 'glob_files', type: 'search', tool: 'Glob', status: 'completed',
    detail: {
      query: '**/*_test.dart', toolName: 'glob',
      filePaths: ['test/a_test.dart', 'test/b_test.dart', 'test/c_test.dart'],
      numFiles: 3, durationMs: 174, truncated: true,
    },
    probes: { toolName: ['Find files'], numFiles: ['3 found'], truncated: ['Truncated'] },
  },
  {
    id: 'web_search', type: 'search', tool: 'WebSearch', status: 'completed',
    detail: {
      query: 'shorebird patch size limits', toolName: 'web_search',
      webResults: [
        { title: 'Shorebird patching guide', url: 'https://docs.shorebird.dev/patch' },
        { title: 'Release sizes explained', url: 'https://docs.shorebird.dev/sizes' },
      ],
      annotations: ['Sources: official documentation'],
      durationSeconds: 7.31,
    },
    probes: { toolName: ['Web search'] },
  },
  {
    id: 'fetch_ok', type: 'fetch', tool: 'WebFetch', status: 'completed',
    detail: {
      url: 'https://docs.shorebird.dev/patch', prompt: 'List the patch size limits',
      result: 'Patches may be up to 2 MB.', code: 200, codeText: 'OK', bytes: 15432, durationMs: 830,
    },
    probes: { code: ['200'], codeText: ['OK'] },
  },
  { id: 'fetch_missing', type: 'fetch', tool: 'WebFetch', status: 'completed', detail: { url: 'https://example.com/missing', code: 404, codeText: 'Not Found' }, probes: { code: ['404'], codeText: ['Not Found'] } },
  {
    id: 'sub_agent_done', type: 'sub_agent', tool: 'Task', status: 'completed',
    detail: {
      subAgentType: 'Explore', description: 'Find the login handler',
      childSessionId: '9f2c6e1a-5b7d-4c0e-8a31-2d4f6b8c0e11',
      log: 'The sign-in check lives in the auth module.',
      actions: [{ index: 1, toolName: 'Grep', summary: 'handleLogin' }, { index: 2, toolName: 'Read', summary: 'lib/auth/login.dart' }],
    },
    probes: { 'actions[].toolName': ['Search text', 'Read'] },
  },
  { id: 'sub_agent_running', type: 'sub_agent', tool: 'Task', status: 'running', detail: { subAgentType: 'Explore', description: 'Map the routing', log: '', actions: [] } },
  {
    id: 'skill', type: 'plain_text', tool: 'Skill', status: 'completed',
    detail: { label: 'frontend-design', text: 'Use a clear visual hierarchy.', icon: 'sparkles' },
  },
  {
    id: 'plan', type: 'plan', tool: 'ExitPlanMode', status: 'completed',
    detail: { text: '# Add the login form\n\n- Build the form\n- Wire it to the API' },
    probes: { text: ['Add the login form', 'Build the form'] },
  },
  {
    id: 'mcp_flat', type: 'unknown', tool: 'mcp__drive__search', status: 'completed',
    detail: { input: { query: 'quarterly report', maxResults: 5 }, output: { output: 'Found two files: Q3 report and Q4 report' } },
    probes: { input: ['Query', 'quarterly report', 'Max results'] },
  },
  {
    id: 'mcp_nested', type: 'unknown', tool: 'mcp__drive__search', status: 'completed',
    detail: { input: { query: 'budget', filters: { owner: 'me' } }, output: { output: [{ name: 'Budget 2027' }] } },
    probes: { input: ['budget', 'Technical details'] },
  },
  { id: 'speak_text', type: 'unknown', tool: 'speak', status: 'completed', detail: { input: 'The build finished without errors.', output: null } },
  { id: 'mcp_failed', type: 'unknown', tool: 'mcp__drive__search', status: 'failed', error: { message: 'timeout' }, detail: { input: { query: 'lost file' }, output: null }, probes: { input: ['lost file'] } },
];

// ---- 3. validate and flatten -----------------------------------------------
const problems = [];
function leaves(value, path, out) {
  if (Array.isArray(value)) { for (const v of value) leaves(v, `${path}[]`, out); return; }
  if (value !== null && typeof value === 'object') {
    for (const [k, v] of Object.entries(value)) leaves(v, path ? `${path}.${k}` : k, out);
    return;
  }
  out.push({ path, value });
}
function probeOf(v) {
  if (typeof v === 'number') return [String(v)];
  if (typeof v !== 'string') return [];
  const line = v.split('\n').map((l) => l.trim()).find((l) => l) ?? '';
  return line ? [line] : [];
}
const covered = {};
const outCases = cases.map((c) => {
  const fields = schema[c.type];
  if (!fields) { problems.push(`case ${c.id}: Paseo has no step type "${c.type}"`); return c; }
  // A union field is a leaf whatever shape its value has.
  const flat = [];
  const visit = (value, path) => {
    if (fields[path]?.kind === 'any') { flat.push({ path, value }); return; }
    if (Array.isArray(value)) { for (const v of value) visit(v, `${path}[]`); return; }
    if (value !== null && typeof value === 'object') {
      for (const [k, v] of Object.entries(value)) visit(v, path ? `${path}.${k}` : k);
      return;
    }
    flat.push({ path, value });
  };
  visit(c.detail, '');
  const byPath = new Map();
  for (const { path, value } of flat) {
    if (!(path in fields)) { problems.push(`case ${c.id}: "${c.type}.${path}" is not in Paseo's schema`); continue; }
    const kind = fields[path].kind;
    if (kind === 'enum' && !fields[path].values.includes(value)) problems.push(`case ${c.id}: ${path}=${value} is not one of ${fields[path].values}`);
    covered[`${c.type}.${path}`] = true;
    const entry = byPath.get(path) ?? { path, kind, probes: [] };
    if (kind === 'any' && value !== null && typeof value === 'object') {
      // The scalar values a person should be able to read.
      for (const [, v] of Object.entries(value)) if (typeof v === 'string') entry.probes.push(...probeOf(v));
    } else if (kind === 'any' && typeof value === 'string') entry.probes.push(...probeOf(value));
    else if (kind !== 'enum' && kind !== 'boolean') entry.probes.push(...probeOf(value));
    byPath.set(path, entry);
  }
  for (const [path, probes] of Object.entries(c.probes ?? {})) {
    const entry = byPath.get(path);
    if (!entry) { problems.push(`case ${c.id}: probes for "${path}", which the case does not set`); continue; }
    entry.probes = probes;
  }
  return { id: c.id, type: c.type, tool: c.tool, status: c.status, error: c.error ?? null, detail: { type: c.type, ...c.detail }, fields: [...byPath.values()] };
});
for (const [type, fields] of Object.entries(schema)) {
  for (const path of Object.keys(fields)) {
    if (!covered[`${type}.${path}`]) problems.push(`schema field "${type}.${path}" is in no case: add it to a case in tool/coverage/paseo_tool_samples.mjs`);
  }
}
if (problems.length) {
  console.error(problems.join('\n'));
  process.exit(1);
}
writeFileSync(
  outPath,
  JSON.stringify({ source: protocolPath, schema: Object.fromEntries(Object.entries(schema).map(([t, f]) => [t, Object.entries(f).map(([path, v]) => ({ path, kind: v.kind }))])), cases: outCases }, null, 2),
);
console.log(outCases.map((c) => `${c.id}: ${c.fields.length} fields`).join('\n'));
