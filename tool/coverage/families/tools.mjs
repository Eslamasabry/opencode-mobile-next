// Family "tools": every field of every tool-step type (the `detail` of a
// `tool_call` timeline item), with realistic cases that fill those fields
// the way Paseo's producers do (providers/tool-call-detail-primitives.js and
// providers/claude/*).
import { def, unwrap, members, literalOf, objectFields } from '../paseo_cases_lib.mjs';

export function toolSchema(root) {
  const toolCall = members(root).find((o) => literalOf(o, 'type') === 'tool_call');
  const detailUnion = unwrap(def(toolCall).shape.detail);
  const schema = {};
  for (const o of members(detailUnion)) schema[literalOf(o, 'type')] = objectFields(o);
  return schema;
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
    probes: { input: ['budget', 'What was sent', 'What came back'] },
  },
  { id: 'speak_text', type: 'unknown', tool: 'speak', status: 'completed', detail: { input: 'The build finished without errors.', output: null } },
  { id: 'mcp_failed', type: 'unknown', tool: 'mcp__drive__search', status: 'failed', error: { message: 'timeout' }, detail: { input: { query: 'lost file' }, output: null }, probes: { input: ['lost file'] } },
];

// tool: the name the provider gives the call. probes: what a person should
// read on screen for a field whose value is reworded (counts, enums).
export const toolCases = cases.map((c) => ({
  id: c.id,
  family: 'tools',
  payload: {
    type: 'tool_call',
    callId: `call-${c.id}`,
    name: c.tool,
    status: c.status,
    error: c.error ?? null,
    detail: { type: c.type, ...c.detail },
  },
  parts: [{ group: c.type, value: c.detail, probes: c.probes }],
}));
