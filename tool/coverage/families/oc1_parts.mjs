// Family "oc1_parts": everything an OpenCode 1 server puts in a conversation
// through messages and parts (contracts/opencode-openapi-f12e14cf.json):
// user and assistant message fields, every part type, every tool state, every
// assistant error, and the per-tool input/metadata keys OpenCode's own tools
// send (the contract types a tool's input and metadata as free objects, so
// those keys come from the tool definitions: groups "tool:<name>").
import { familyBuilder } from '../oc_cases_lib.mjs';
import { toolGroupSpecs } from './tool_groups.mjs';

const ERRORS = ['ProviderAuthError', 'UnknownError', 'MessageOutputLengthError', 'MessageAbortedError', 'StructuredOutputError', 'ContextOverflowError', 'ContentFilterError', 'APIError'];

export function oc1PartsFamily(contract) {
  const { schema, kase } = familyBuilder(contract, [
    { name: 'UserMessage', stop: ['OutputFormat'] },
    { name: 'OutputFormatJsonSchema' },
    { name: 'AssistantMessage', stop: ERRORS },
    ...ERRORS.map((name) => ({ name })),
    { name: 'TextPart' },
    { name: 'ReasoningPart' },
    { name: 'FilePart' },
    { name: 'SubtaskPart' },
    { name: 'AgentPart' },
    { name: 'StepStartPart' },
    { name: 'StepFinishPart' },
    { name: 'SnapshotPart' },
    { name: 'PatchPart' },
    { name: 'RetryPart', stop: ['APIError'] },
    { name: 'CompactionPart' },
    { name: 'ToolPart', stop: ['ToolState'] },
    { name: 'ToolStatePending' },
    { name: 'ToolStateRunning' },
    { name: 'ToolStateCompleted', stop: ['FilePart'] },
    { name: 'ToolStateError' },
    ...toolGroupSpecs,
  ]);

  const S = 'ses_checkout';
  let n = 0;
  const id = (p) => `${p}_${String(++n).padStart(3, '0')}`;
  const T0 = 1788960000000;
  const userInfo = (use, extra = {}, group = {}) => use('UserMessage', {
    id: id('msg'), sessionID: S, time: { created: T0 + 1000 },
    agent: 'plan', model: { providerID: 'openai', modelID: 'gpt-6-sol' }, ...extra,
  });
  const asst = (use, extra = {}) => { const v = {
    id: id('msg'), sessionID: S, time: { created: T0 + 2000, completed: T0 + 9000 },
    parentID: 'msg_001', modelID: 'claude-opus-5-5', providerID: 'anthropic', mode: 'build', agent: 'build',
    path: { cwd: '/srv/runner/app', root: '/srv/runner' }, cost: 0.0421,
    tokens: { total: 15210, input: 9100, output: 1210, reasoning: 400, cache: { read: 4000, write: 500 } }, finish: 'stop', ...extra,
  }; return use('AssistantMessage', v, { probes: onlySet(v, { finish: [], error: [], structured: [] }) }); };
  const bundle = (info, ...parts) => ({ info, parts });
  const base = (mid) => ({ id: id('prt'), sessionID: S, messageID: mid });
  const prompt = (use, text = 'Add a retry button to the upload screen') => {
    const info = userInfo(use);
    return bundle(info, { ...base(info.id), type: 'text', text });
  };

  const present = (obj, path) => path.split('.').reduce((o, seg) => {
    if (o === undefined || o === null) return undefined;
    if (seg.endsWith('[]')) return o[seg.slice(0, -2)]?.[0];
    return o[seg];
  }, obj) !== undefined;
  const onlySet = (obj, probes) => Object.fromEntries(Object.entries(probes).filter(([k]) => present(obj, k)));
  const cases = [];
  const add = (c) => cases.push(c);

  add(kase('user_prompt_all_fields', (use) => {
    const info = userInfo(use, {
      format: use('OutputFormatJsonSchema', { schema: { type: 'object', properties: { verdict: { type: 'string' } } }, retryCount: 3 }),
      summary: { title: 'Retry upload', body: 'Added a retry button to the upload screen.', diffs: [{ file: 'lib/upload/upload_screen.dart', patch: '@@ -1 +1 @@\n-old\n+new', additions: 7, deletions: 2, status: 'modified' }] },
      system: 'Reply in Spanish and keep answers short.',
      tools: { bash: false, webfetch: true },
      model: { providerID: 'openai', modelID: 'gpt-6-sol', variant: 'xhigh' },
    });
    const text = use('TextPart', { ...base(info.id), text: 'Add a retry button to the upload screen', synthetic: false, ignored: false, time: { start: T0 + 1000, end: T0 + 1500 }, metadata: { source: 'composer' } });
    return [bundle(info, text)];
  }));

  add(kase('text_synthetic_and_ignored', (use) => {
    const info = userInfo(use);
    const visible = { ...base(info.id), type: 'text', text: 'Please check the build output' };
    const hidden = use('TextPart', { ...base(info.id), text: 'Called the Read tool with the arguments: {"filePath":"/work/app/pubspec.yaml"}', synthetic: true }, { probes: { text: [] } });
    const ignored = use('TextPart', { ...base(info.id), text: 'Use the staging server for this run', ignored: true });
    return [bundle(info, visible, hidden, ignored)];
  }, { hidden: ['Called the Read tool with the arguments'] }));

  add(kase('files_three_sources', (use) => {
    const info = userInfo(use);
    const file = use('FilePart', { ...base(info.id), mime: 'text/x-dart', filename: 'upload_screen.dart', url: 'file:///work/app/lib/upload/upload_screen.dart', source: { type: 'file', path: 'lib/upload/upload_screen.dart', text: { value: '@lib/upload/upload_screen.dart', start: 4, end: 34 } } });
    const symbol = use('FilePart', { ...base(info.id), mime: 'text/x-dart', filename: 'UploadQueue', url: 'file:///work/app/lib/upload/upload_queue.dart', source: { type: 'symbol', path: 'lib/upload/upload_queue.dart', name: 'UploadQueue.retryAll', kind: 6, range: { start: { line: 41, character: 2 }, end: { line: 58, character: 3 } }, text: { value: '@UploadQueue.retryAll', start: 40, end: 61 } } });
    const resource = use('FilePart', { ...base(info.id), mime: 'text/markdown', filename: 'release-notes.md', url: 'mcp://notion/pages/release-notes', source: { type: 'resource', clientName: 'notion', uri: 'mcp://notion/pages/release-notes', text: { value: '@notion:release-notes', start: 70, end: 91 } } });
    const image = use('FilePart', { ...base(info.id), mime: 'image/png', filename: 'screenshot.png', url: 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==' }, { probes: { filename: [] } });
    return [bundle(info, { ...base(info.id), type: 'text', text: 'Compare these three' }, file, symbol, resource, image)];
  }));

  add(kase('agent_mention', (use) => {
    const info = userInfo(use);
    const agent = use('AgentPart', { ...base(info.id), name: 'security-reviewer', source: { value: '@security-reviewer', start: 8, end: 26 } });
    return [bundle(info, { ...base(info.id), type: 'text', text: 'Ask @security-reviewer to look at the login change' }, agent)];
  }));

  add(kase('subtask_request', (use) => {
    const info = userInfo(use);
    const sub = use('SubtaskPart', { ...base(info.id), prompt: 'Read every test under test/upload and list the flaky ones', description: 'Find flaky upload tests', agent: 'explore', model: { providerID: 'anthropic', modelID: 'claude-sonnet-5-5' }, command: 'flaky-tests' });
    return [bundle(info, sub)];
  }));

  add(kase('assistant_answer_all_fields', (use) => {
    const u = prompt(use);
    const info = asst(use, { summary: true, structured: { verdict: 'ship it' }, variant: 'high', finish: 'stop' });
    const reasoning = use('ReasoningPart', { ...base(info.id), text: 'The retry button must keep the failed file name, so I will read the queue first.', metadata: { signature: 'sig-abc' }, time: { start: T0 + 2100, end: T0 + 2900 } });
    const start = use('StepStartPart', { ...base(info.id), snapshot: 'snap_a1b2c3' });
    const text = { ...base(info.id), type: 'text', text: 'I added a Retry button next to every failed upload.' };
    const patch = use('PatchPart', { ...base(info.id), hash: 'c0ffee12', files: ['lib/upload/upload_screen.dart', 'test/upload_screen_test.dart'] });
    const snap = use('SnapshotPart', { ...base(info.id), snapshot: 'snap_d4e5f6' });
    const finish = use('StepFinishPart', { ...base(info.id), reason: 'stop', snapshot: 'snap_d4e5f6', cost: 0.0421, tokens: { total: 15210, input: 9100, output: 1210, reasoning: 400, cache: { read: 4000, write: 500 } } });
    return [u, bundle(info, start, reasoning, text, patch, snap, finish)];
  }));

  add(kase('retry_part', (use) => {
    const u = prompt(use);
    const info = asst(use, { finish: 'error' });
    const apierr = use('APIError', { data: { message: 'Rate limit reached for claude-opus-5-5, retrying shortly', statusCode: 429, isRetryable: true, responseHeaders: { 'retry-after': '20' }, responseBody: '{"error":"rate_limited"}', metadata: { url: 'https://api.anthropic.com/v1/messages' } } }, { probes: Object.fromEntries(['data.message', 'data.statusCode', 'data.isRetryable', 'data.responseHeaders', 'data.responseBody', 'data.metadata'].map((k) => [k, []])) });
    const retry = use('RetryPart', { ...base(info.id), attempt: 3, error: apierr, time: { created: T0 + 3000 } }, { probes: { error: [] } });
    return [u, bundle(info, retry, { ...base(info.id), type: 'text', text: 'Sorry for the wait; here is the answer.' })];
  }));

  add(kase('compaction_part', (use) => {
    const u = prompt(use);
    const info = asst(use);
    const comp = use('CompactionPart', { ...base(info.id), auto: true, overflow: true, tail_start_id: 'msg_099' });
    return [u, bundle(info, comp, { ...base(info.id), type: 'text', text: 'Context was compacted; continuing.' })];
  }));

  // Assistant failures, one per error shape.
  const errorCase = (idc, group, value, text) => add(kase(idc, (use) => {
    const u = prompt(use);
    const err = use(group, { data: value }, group === 'APIError' ? { probes: { 'data.isRetryable': ['It retries by itself'] } } : {});
    const info = asst(use, { error: err, finish: 'error' });
    return [u, bundle(info)];
  }, { expect: text }));
  errorCase('error_provider_auth', 'ProviderAuthError', { providerID: 'mistral', message: 'Incorrect API key provided: sk-live-REDACTED' });
  errorCase('error_unknown', 'UnknownError', { message: 'Unexpected failure while streaming the reply', ref: 'ref_77Zk3' });
  errorCase('error_output_length', 'MessageOutputLengthError', {});
  errorCase('error_aborted', 'MessageAbortedError', { message: 'The operation was aborted by the person' });
  errorCase('error_structured_output', 'StructuredOutputError', { message: 'The reply did not match the requested format', retries: 4 });
  errorCase('error_context_overflow', 'ContextOverflowError', { message: 'The prompt is longer than the model can read', responseBody: '{"type":"invalid_request","detail":"prompt is too long: 211000 tokens"}' });
  errorCase('error_content_filter', 'ContentFilterError', { message: 'The reply was blocked by the provider filter' });
  errorCase('error_api', 'APIError', { message: 'The provider is overloaded, try again in a minute', statusCode: 529, isRetryable: true, responseHeaders: { 'x-request-id': 'req_9f8e7d' }, responseBody: '{"type":"overloaded_error"}', metadata: { url: 'https://api.anthropic.com/v1/messages' } });

  // ---- tools ----
  const toolCase = (idc, name, status, st, tg, extra = {}) => { const meta = { ...META[idc], ...extra, stateProbes: { ...META[idc]?.stateProbes, ...extra.stateProbes } }; add(kase(idc, (use) => {
    const u = prompt(use);
    const info = asst(use);
    const stateGroup = { pending: 'ToolStatePending', running: 'ToolStateRunning', completed: 'ToolStateCompleted', error: 'ToolStateError' }[status];
    const stateProbes = onlySet(st, { input: [], metadata: [], output: [], ...(meta.stateProbes ?? {}) });
    const state = use(stateGroup, st, { probes: stateProbes, ...(meta.stateOpts ?? {}) });
    if (tg) use(`tool:${name}`, tg, { probes: onlySet(tg, meta.toolOpts?.probes ?? {}) });
    const part = use('ToolPart', { ...base(info.id), callID: id('call'), tool: name, state, ...(meta.partMetadata ? { metadata: meta.partMetadata } : {}) }, { probes: { state: [], ...(meta.partMetadata ? { metadata: [] } : {}), tool: [meta.label ?? name] } });
    return [u, bundle(info, part)];
  }, meta.case ?? {})); };
  const T = (s, e) => ({ start: T0 + s, end: T0 + e });
  // Per case: the row's label, and what a person reads for a field whose
  // value is reworded (a path shown by its file name, a status in words).
  const noTime = { title: [], 'time.start': [], 'time.end': [], 'time.compacted': [] };
  const META = {
    tool_bash_ok: { label: 'Shell', stateProbes: { ...noTime, 'time.start': ['4 seconds'], 'time.end': ['4 seconds'], 'time.compacted': ['Output pruned'] }, toolOpts: { probes: { 'metadata.exit': ['exit code 0'] } } },
    tool_bash_failed: { label: 'Shell', stateProbes: noTime },
    tool_bash_running: { label: 'Shell', stateProbes: noTime },
    tool_pending_raw: { label: 'Shell', stateProbes: { raw: [] } },
    tool_read: { label: 'Read', stateProbes: noTime, toolOpts: { probes: { 'input.filePath': ['main.dart'], 'metadata.truncated': ['Truncated'] } } },
    tool_edit: { label: 'Edit', stateProbes: noTime, toolOpts: { probes: { 'input.filePath': ['upload_screen.dart'], 'input.replaceAll': [], 'metadata.diff': [], 'metadata.filediff.file': ['upload_screen.dart'], 'metadata.diagnostics': [] } } },
    tool_write: { label: 'Write', stateProbes: noTime, toolOpts: { probes: { 'input.filePath': ['retry_button.dart'], 'metadata.filepath': ['retry_button.dart'], 'metadata.exists': ['new file'] } } },
    tool_grep: { label: 'Search text', stateProbes: noTime, toolOpts: { probes: { 'metadata.truncated': ['Truncated'] } } },
    tool_glob: { label: 'Find files', stateProbes: noTime, toolOpts: { probes: { 'metadata.truncated': ['Truncated'] } } },
    tool_webfetch: { label: 'Fetch page', stateProbes: noTime },
    tool_task: { label: 'Delegated to explore', stateProbes: noTime },
    tool_todowrite: { label: 'Tasks', stateProbes: noTime, toolOpts: { probes: { 'input.todos[].status': ['Completed', 'In progress', 'Pending'], 'input.todos[].priority': ['High priority', 'Medium priority', 'Low priority'] } } },
    tool_attachments: { label: 'Read', stateProbes: { ...noTime, 'attachments[]': ['logo.png'] }, toolOpts: { probes: { 'input.filePath': ['logo.png'] } } },
  };
  toolCase('tool_bash_ok', 'bash', 'completed', { input: { command: 'flutter test test/upload_screen_test.dart', description: 'Run the upload screen tests', workdir: '/work/app', timeout: 120000 }, output: '00:04 +12: All tests passed!', title: 'Run the upload screen tests', metadata: { output: '00:04 +12: All tests passed!', exit: 0, description: 'Run the upload screen tests' }, time: { ...T(100, 4100), compacted: T0 + 99000 } }, { input: { command: 'flutter test test/upload_screen_test.dart', description: 'Run the upload screen tests', workdir: '/work/app', timeout: 120000 }, output: '00:04 +12: All tests passed!', metadata: { output: '00:04 +12: All tests passed!', exit: 0 } },
    { partMetadata: { providerExecuted: false } });
  toolCase('tool_bash_failed', 'bash', 'error', { input: { command: 'git push origin main' }, error: 'Command exited with code 128: fatal: could not read Username', metadata: { exit: 128 }, time: T(100, 900) });
  toolCase('tool_bash_running', 'bash', 'running', { input: { command: 'npm run dev' }, title: 'Start the dev server', metadata: { output: 'ready on port 5173' }, time: { start: T0 + 100 } });
  toolCase('tool_pending_raw', 'bash', 'pending', { input: {}, raw: '{"command":"ls -la lib/' });
  toolCase('tool_read', 'read', 'completed', { input: { filePath: '/work/app/lib/main.dart', offset: 12, limit: 40 }, output: '<file>\n00012| void main() {\n00013|   runApp(const App());\n</file>', title: 'lib/main.dart', metadata: { preview: 'void main() {', truncated: true }, time: T(100, 200) }, { input: { filePath: '/work/app/lib/main.dart', offset: 12, limit: 40 }, output: '<file>\n00012| void main() {\n00013|   runApp(const App());\n</file>', metadata: { truncated: true } });
  toolCase('tool_edit', 'edit', 'completed', { input: { filePath: '/work/app/lib/upload/upload_screen.dart', oldString: 'final retries = 1;', newString: 'final retries = 3;', replaceAll: true }, output: 'Edit applied successfully.', title: 'lib/upload/upload_screen.dart', metadata: { diff: '--- a/lib/upload/upload_screen.dart\n+++ b/lib/upload/upload_screen.dart\n@@ -1 +1 @@\n-final retries = 1;\n+final retries = 3;', filediff: { file: '/work/app/lib/upload/upload_screen.dart', before: 'final retries = 1;', after: 'final retries = 3;', additions: 6, deletions: 4 }, diagnostics: { '/work/app/lib/upload/upload_screen.dart': [{ message: 'Unused import', severity: 2 }] } }, time: T(100, 300) }, { input: { filePath: '/work/app/lib/upload/upload_screen.dart', oldString: 'final retries = 1;', newString: 'final retries = 3;', replaceAll: true }, metadata: { diff: '--- a/lib/upload/upload_screen.dart\n+++ b/lib/upload/upload_screen.dart\n@@ -1 +1 @@\n-final retries = 1;\n+final retries = 3;', filediff: { file: '/work/app/lib/upload/upload_screen.dart', before: 'final retries = 1;', after: 'final retries = 3;', additions: 6, deletions: 4 }, diagnostics: { '/work/app/lib/upload/upload_screen.dart': [{ message: 'Unused import', severity: 2 }] } } });
  toolCase('tool_write', 'write', 'completed', { input: { filePath: '/work/app/lib/upload/retry_button.dart', content: 'class RetryButton extends StatelessWidget {}' }, output: 'Wrote file successfully.', title: 'lib/upload/retry_button.dart', metadata: { filepath: '/work/app/lib/upload/retry_button.dart', exists: false }, time: T(100, 250) }, { input: { filePath: '/work/app/lib/upload/retry_button.dart', content: 'class RetryButton extends StatelessWidget {}' }, metadata: { filepath: '/work/app/lib/upload/retry_button.dart', exists: false } });
  toolCase('tool_grep', 'grep', 'completed', { input: { pattern: 'TODO', path: 'lib', include: '*.dart' }, output: 'Found 5 matches\nlib/a.dart:\n  Line 12: // TODO tidy\nlib/b.dart:\n  Line 40: // TODO remove', title: 'TODO', metadata: { matches: 5, truncated: true }, time: T(100, 140) }, { input: { pattern: 'TODO', path: 'lib', include: '*.dart' }, output: 'Found 5 matches\nlib/a.dart:\n  Line 12: // TODO tidy\nlib/b.dart:\n  Line 40: // TODO remove', metadata: { matches: 5, truncated: true } });
  toolCase('tool_glob', 'glob', 'completed', { input: { pattern: '**/*_test.dart', path: 'test' }, output: 'test/a_test.dart\ntest/b_test.dart', title: 'test', metadata: { count: 2, truncated: true }, time: T(100, 130) }, { input: { pattern: '**/*_test.dart', path: 'test' }, output: 'test/a_test.dart\ntest/b_test.dart', metadata: { count: 2, truncated: true } });
  toolCase('tool_webfetch', 'webfetch', 'completed', { input: { url: 'https://docs.flutter.dev/cookbook/networking', format: 'markdown', timeout: 30 }, output: 'Networking cookbook: fetch data from the internet', title: 'https://docs.flutter.dev/cookbook/networking (text/html)', metadata: {}, time: T(100, 900) }, { input: { url: 'https://docs.flutter.dev/cookbook/networking', format: 'markdown', timeout: 30 }, output: 'Networking cookbook: fetch data from the internet' });
  toolCase('tool_task', 'task', 'completed', { input: { description: 'Audit the upload retry code', prompt: 'Read lib/upload and list every place a retry can loop forever', subagent_type: 'explore', command: 'audit-retries' }, output: 'task_id: ses_child1 (for resuming)\n\n<task_result>\nTwo loops can retry forever.\n</task_result>', title: 'Audit the upload retry code', metadata: { sessionId: 'ses_child1', model: { providerID: 'openrouter', modelID: 'claude-sonnet-5-5' } }, time: T(100, 6100) }, { input: { description: 'Audit the upload retry code', prompt: 'Read lib/upload and list every place a retry can loop forever', subagent_type: 'explore', command: 'audit-retries' }, output: 'task_id: ses_child1 (for resuming)\n\n<task_result>\nTwo loops can retry forever.\n</task_result>', metadata: { sessionId: 'ses_child1', model: { providerID: 'openrouter', modelID: 'claude-sonnet-5-5' } } });
  toolCase('tool_todowrite', 'todowrite', 'completed', { input: { todos: [{ content: 'Read the upload queue', status: 'completed', priority: 'high' }, { content: 'Add the retry button', status: 'in_progress', priority: 'medium' }, { content: 'Write the widget test', status: 'pending', priority: 'low' }] }, output: '[{"content":"Read the upload queue"}]', title: '2 todos', metadata: { todos: [{ content: 'Read the upload queue', status: 'completed', priority: 'high' }, { content: 'Add the retry button', status: 'in_progress', priority: 'medium' }, { content: 'Write the widget test', status: 'pending', priority: 'low' }] }, time: T(100, 130) }, { input: { todos: [{ content: 'Read the upload queue', status: 'completed', priority: 'high' }, { content: 'Add the retry button', status: 'in_progress', priority: 'medium' }, { content: 'Write the widget test', status: 'pending', priority: 'low' }] }, output: '[{"content":"Read the upload queue"}]' });
  toolCase('tool_attachments', 'read', 'completed', { input: { filePath: '/work/app/assets/logo.png' }, output: 'Image read successfully', title: 'assets/logo.png', metadata: {}, time: T(100, 150), attachments: [{ id: id('prt'), sessionID: S, messageID: 'msg_x', type: 'file', mime: 'image/png', filename: 'logo.png', url: 'data:image/png;base64,iVBORw0KGgo=', source: { type: 'file', path: 'assets/logo.png', text: { value: 'assets/logo.png', start: 0, end: 15 } } }] }, { input: { filePath: '/work/app/assets/logo.png' }, output: 'Image read successfully' });

  return { schema, cases };
}
