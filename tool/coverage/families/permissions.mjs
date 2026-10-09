// Family "permissions": what an agent sends when it needs the person to
// decide (AgentPermissionRequestPayloadSchema). Native questions and plans
// travel over the same wire, so they are cases of this family too.
//
// Group "request" holds the request's own fields; "detail.<type>" holds the
// fields of the tool-call detail the request carries. A request is sent
// BEFORE the tool runs, so the result fields of a detail (output, exit code,
// counts, ...) can never be in one: they are listed in `excluded` with the
// reason, and the ledger must say "ignored" for them too.
import { def, unwrap, members, literalOf, objectFields } from '../paseo_cases_lib.mjs';

export function permissionSchema(requestSchema, timelineRoot) {
  const schema = { request: objectFields(unwrap(requestSchema), ['detail']) };
  const toolCall = members(timelineRoot).find((o) => literalOf(o, 'type') === 'tool_call');
  for (const o of members(def(toolCall).shape.detail)) {
    schema[`detail.${literalOf(o, 'type')}`] = objectFields(o);
  }
  return schema;
}

const resultOnly = 'ignored: this is what a tool answered; a request is sent before the tool runs, so it never has one';
export const permissionExcluded = {
  'detail.shell.output': resultOnly,
  'detail.shell.exitCode': resultOnly,
  'detail.read.content': resultOnly,
  'detail.search.content': resultOnly,
  'detail.search.filePaths[]': resultOnly,
  'detail.search.webResults[].title': resultOnly,
  'detail.search.webResults[].url': resultOnly,
  'detail.search.annotations[]': resultOnly,
  'detail.search.numFiles': resultOnly,
  'detail.search.numMatches': resultOnly,
  'detail.search.durationMs': resultOnly,
  'detail.search.durationSeconds': resultOnly,
  'detail.search.truncated': resultOnly,
  'detail.fetch.result': resultOnly,
  'detail.fetch.code': resultOnly,
  'detail.fetch.codeText': resultOnly,
  'detail.fetch.bytes': resultOnly,
  'detail.fetch.durationMs': resultOnly,
  'detail.sub_agent.childSessionId': resultOnly,
  'detail.sub_agent.log': resultOnly,
  'detail.sub_agent.actions[].index': resultOnly,
  'detail.sub_agent.actions[].toolName': resultOnly,
  'detail.sub_agent.actions[].summary': resultOnly,
  'detail.unknown.output': resultOnly,
};
const notAsk = 'ignored: Paseo prepares a new work copy on its own and never asks the person first';
for (const k of ['worktreePath', 'branchName', 'log', 'truncated', 'commands[].index', 'commands[].command', 'commands[].cwd', 'commands[].log', 'commands[].status', 'commands[].exitCode', 'commands[].durationMs']) {
  permissionExcluded[`detail.worktree_setup.${k}`] = notAsk;
}

const CWD = '/home/dev/app';
// primary: the fields the person must be able to read WITHOUT opening a
// fold (what is being asked for); `requestPrimary` are the request's own.
const req = (id, fields, detail, probes = {}, detailProbes = {}, primary = [], requestPrimary = []) => ({
  id,
  family: 'permissions',
  payload: { ...fields, ...(detail ? { detail } : {}) },
  parts: [
    { group: 'request', value: fields, probes, primary: requestPrimary },
    ...(detail ? [{ group: `detail.${detail.type}`, value: (({ type, ...rest }) => rest)(detail), probes: detailProbes, primary }] : []),
  ],
});
const rule = (tool, content) => ({ type: 'addRules', rules: [{ toolName: tool, ruleContent: content }], behavior: 'allow', destination: 'localSettings' });

export const permissionCases = [
  req('bash', {
    id: 'perm_req_8f2a41', provider: 'claude', name: 'Bash', kind: 'tool',
    title: 'Run flutter test on the upload screen',
    description: 'Runs the widget tests for the upload screen',
    input: { command: 'flutter test test/upload_screen_test.dart', description: 'Run the upload tests' },
    suggestions: [rule('Bash', 'flutter test:*')],
    metadata: { toolUseId: 'toolu_01UploadTests' },
  }, { type: 'shell', command: 'flutter test test/upload_screen_test.dart', cwd: CWD },
  { 'suggestions[]': ['Always allow'], name: ['Run a shell command'] }, {}, ['command'], ['title', 'description']),
  req('edit_strings', {
    id: 'perm_req_c19d02', provider: 'claude', name: 'Edit', kind: 'tool',
    input: { file_path: `${CWD}/lib/upload/retry_button.dart`, old_string: 'onPressed: null', new_string: 'onPressed: queue.requeue' },
    suggestions: [{ type: 'setMode', mode: 'acceptEdits', destination: 'session' }],
    metadata: { toolUseId: 'toolu_01EditRetry' },
  }, { type: 'edit', filePath: `${CWD}/lib/upload/retry_button.dart`, oldString: 'onPressed: null,', newString: 'onPressed: queue.requeue,' },
  { 'suggestions[]': ['Always allow'], name: ['Edit a file'] }, {}, ['filePath', 'oldString', 'newString']),
  req('edit_diff', {
    id: 'perm_req_77ab90', provider: 'codex', name: 'apply_patch', kind: 'tool',
    description: 'Change the retry limit',
  }, { type: 'edit', filePath: `${CWD}/lib/upload/limits.dart`, unifiedDiff: '--- a/lib/upload/limits.dart\n+++ b/lib/upload/limits.dart\n@@ -1 +1 @@\n-const retryLimit = 1;\n+const retryLimit = 3;' },
  { name: ['Edit a file'] }, { unifiedDiff: ['const retryLimit = 3;'] }, ['filePath', 'unifiedDiff'], ['description']),
  req('write', {
    id: 'perm_req_5e03d7', provider: 'claude', name: 'Write', kind: 'tool',
    input: { file_path: `${CWD}/lib/upload/upload_status.dart`, content: 'enum UploadStatus { queued, failed }' },
    suggestions: [{ type: 'setMode', mode: 'acceptEdits', destination: 'session' }],
  }, { type: 'write', filePath: `${CWD}/lib/upload/upload_status.dart`, content: 'enum UploadStatus { queued, failed }' },
  { 'suggestions[]': ['Always allow'], name: ['Edit a file'] }, {}, ['filePath', 'content']),
  req('read', {
    id: 'perm_req_a4410b', provider: 'claude', name: 'Read', kind: 'tool',
    input: { file_path: '/home/dev/.config/app/settings.json' },
  }, { type: 'read', filePath: '/home/dev/.config/app/settings.json', offset: 20, limit: 80 },
  { name: ['Read a file'] }, { offset: ['from line 20'], limit: ['80 lines'] }, ['filePath']),
  req('fetch', {
    id: 'perm_req_0b7e66', provider: 'claude', name: 'WebFetch', kind: 'tool',
    input: { url: 'https://docs.flutter.dev/release/breaking-changes', prompt: 'List the breaking changes in 3.47' },
    suggestions: [rule('WebFetch', 'domain:docs.flutter.dev')],
  }, { type: 'fetch', url: 'https://docs.flutter.dev/release/breaking-changes', prompt: 'List the breaking changes in 3.47' },
  { 'suggestions[]': ['Always allow'], name: ['Open a web page'] }, {}, ['url']),
  req('web_search', {
    id: 'perm_req_d83f15', provider: 'claude', name: 'WebSearch', kind: 'tool',
    input: { query: 'flutter 3.47 impeller android regression' },
  }, { type: 'search', query: 'flutter 3.47 impeller android regression', toolName: 'web_search' },
  { name: ['Search the web'] }, { toolName: ['Search the web'] }, ['query']),
  req('grep', {
    id: 'perm_req_19c2ee', provider: 'claude', name: 'Grep', kind: 'tool',
    input: { pattern: 'requeue', path: '/home/dev/other-project' },
  }, { type: 'search', query: 'requeue', toolName: 'grep', mode: 'files_with_matches' },
  { name: ['Search the project'] }, { mode: ['file names'] }, ['query']),
  req('sub_agent', {
    id: 'perm_req_62aa07', provider: 'claude', name: 'Task', kind: 'tool',
    input: { subagent_type: 'Explore', description: 'Find where uploads are retried', prompt: 'Look through lib/upload' },
  }, { type: 'sub_agent', subAgentType: 'Explore', description: 'Find where uploads are retried' },
  { name: ['Start a helper agent'] }, {}, ['description']),
  req('skill', {
    id: 'perm_req_b90c3d', provider: 'claude', name: 'Skill', kind: 'tool',
    input: { skill: 'frontend-design' },
  }, { type: 'plain_text', label: 'frontend-design', text: 'Use a clear visual hierarchy.', icon: 'sparkles' },
  { name: ['Use a skill'] }, { text: [] }, ['label']),
  req('mcp', {
    id: 'perm_req_e47a28', provider: 'claude', name: 'mcp__drive__search', kind: 'tool',
    title: 'Search Google Drive',
    input: { query: 'quarterly report', maxResults: 5 },
    suggestions: [rule('mcp__drive__search', '')],
  }, { type: 'unknown', input: { query: 'quarterly report', maxResults: 5 } },
  { 'suggestions[]': ['Always allow'], name: ['Use search · drive'] }, { input: ['quarterly report'] }, ['input'], ['title']),
  req('plan', {
    id: 'perm_req_3c58f1', provider: 'claude', name: 'ExitPlanMode', kind: 'plan',
    title: 'Plan ready',
    input: { plan: '# Add the retry button\n\n1. Read the upload queue.\n2. Add the button.\n3. Test it.' },
    actions: [
      { id: 'reject', label: 'Keep planning', behavior: 'deny', variant: 'secondary', intent: 'dismiss' },
      { id: 'implement', label: 'Implement', behavior: 'allow', variant: 'primary', intent: 'implement' },
      { id: 'implement_resume', label: 'Implement without asking', behavior: 'allow', variant: 'danger', intent: 'implement_resume' },
    ],
    metadata: { planText: '# Add the retry button\n\n1. Read the upload queue.\n2. Add the button.\n3. Test it.', toolUseId: 'toolu_01Plan' },
  }, { type: 'plan', text: '# Add the retry button\n\n1. Read the upload queue.\n2. Add the button.\n3. Test it.' }, {
    name: [],
    input: ['Add the retry button'],
    title: [],
    metadata: ['toolu_01Plan'],
    'actions[].label': ['Implement without asking'],
    'actions[].id': [], 'actions[].behavior': [], 'actions[].variant': [], 'actions[].intent': [],
  }, {}, ['text'], ['input']),
  req('question', {
    id: 'perm_req_f1d6a9', provider: 'claude', name: 'AskUserQuestion', kind: 'question',
    input: { questions: [
      { header: 'Retry style', question: 'Should the retry button retry once or keep retrying?', options: [{ label: 'Retry once', description: 'One more attempt, then stop' }, { label: 'Keep retrying', description: 'Retry until it works' }], multiSelect: false, allowOther: true },
    ] },
    metadata: { toolUseId: 'toolu_01Question' },
  }, null, { name: [], input: ['Should the retry button retry once or keep retrying?', 'Retry once', 'One more attempt, then stop'] }, {}, [], ['input']),
  req('mode', {
    id: 'perm_req_2ad740', provider: 'claude', name: 'EnterPlanMode', kind: 'mode',
    title: 'Switch to plan mode',
    description: 'The agent wants to plan before it changes anything',
  }, null, { name: [] }, {}, [], ['title', 'description']),
  req('other', {
    id: 'perm_req_9e0b35', provider: 'claude', name: 'NotebookEdit', kind: 'other',
    description: 'Change a cell in analysis.ipynb',
    input: { notebook_path: `${CWD}/analysis.ipynb`, new_source: 'print(total)' },
  }, null, { name: ['Notebookedit'], input: ['analysis.ipynb'] }, {}, [], ['description', 'input']),
];
