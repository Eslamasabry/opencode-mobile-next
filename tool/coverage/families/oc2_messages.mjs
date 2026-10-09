// Family "oc2_messages": everything an OpenCode 2 server puts in a
// conversation through messages (Session.Message.Info and its content, tool
// states, errors and attachments; contracts/opencode2-openapi-beta-18600.json).
// The same messages arrive from the durable log and from GET /session/{id}/
// message, so this is the history path; oc2_events covers the live stream.
import { familyBuilder } from '../oc_cases_lib.mjs';
import { toolGroupSpecs } from './tool_groups.mjs';

const M = 'Session.Message.';
export function oc2MessagesFamily(contract) {
  const { schema, kase } = familyBuilder(contract, [
    { name: `${M}User`, stop: ['Prompt.FileAttachment', 'Prompt.AgentAttachment', 'Prompt.SkillAttachment'] },
    { name: 'Prompt.FileAttachment' },
    { name: 'Prompt.AgentAttachment' },
    { name: 'Prompt.SkillAttachment' },
    { name: `${M}Synthetic` },
    { name: `${M}System` },
    { name: `${M}Skill` },
    { name: `${M}Shell` },
    { name: `${M}Assistant`, stop: [`${M}Assistant.Text`, `${M}Assistant.Reasoning`, `${M}Assistant.Tool`, 'Session.StructuredError'] },
    { name: `${M}Assistant.Text` },
    { name: `${M}Assistant.Reasoning` },
    { name: `${M}Assistant.Tool`, stop: [`${M}ToolState.Streaming`, `${M}ToolState.Running`, `${M}ToolState.Completed`, `${M}ToolState.Error`] },
    { name: `${M}ToolState.Streaming` },
    { name: `${M}ToolState.Running` },
    { name: `${M}ToolState.Completed`, stop: ['Tool.Content'] },
    { name: `${M}ToolState.Error`, stop: ['Tool.Content', 'Session.StructuredError'] },
    { name: 'Tool.TextContent' },
    { name: 'Tool.FileContent' },
    { name: 'Session.StructuredError' },
    { name: `${M}Compaction.Running` },
    { name: `${M}Compaction.Completed` },
    { name: `${M}Compaction.Failed`, stop: ['Session.StructuredError'] },
    { name: `${M}AgentSelected` },
    { name: `${M}ModelSelected` },
    { name: `${M}LocationSwitched` },
    ...toolGroupSpecs,
  ]);
  const T0 = 1788960000000;
  const onlySet = (obj, probes) => Object.fromEntries(Object.entries(probes).filter(([k]) => k.split('.').reduce((o, seg) => (o == null ? undefined : seg.endsWith('[]') ? o[seg.slice(0, -2)]?.[0] : o[seg]), obj) !== undefined));
  let n = 0;
  const id = (p) => `${p}_${String(++n).padStart(3, '0')}`;
  const cases = [];
  const add = (cid, build, meta = {}) => cases.push(kase(cid, build, meta));
  const time = (extra = {}) => ({ created: T0 + 1000, ...extra });
  const prompt = (use, text = 'Add a retry button to the upload screen') => use(`${M}User`, { id: id('msg'), time: time(), text });
  const asst = (use, v = {}, probes = {}) => { const value = { id: id('msg'), time: time({ streamed: T0 + 5000, completed: T0 + 9000 }), agent: 'build', model: { id: 'claude-opus-5-5', providerID: 'anthropic', variant: 'high' }, content: [], finish: 'stop', ...v }; return use(`${M}Assistant`, value, { probes: onlySet(value, { 'content[]': [], finish: [], ...probes }) }); };
  const noTimes = (v, base = {}) => ({ ...base });

  add('user_prompt_all_fields', (use) => {
    const file = use('Prompt.FileAttachment', { data: 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==', mime: 'image/png', source: { type: 'inline' }, name: 'screenshot.png', description: 'The upload screen as it looks today', mention: { start: 0, end: 15, text: '@screenshot.png' } }, { probes: { data: [], description: [], 'source.type': [], 'mention.start': [], 'mention.end': [], 'mention.text': [], mime: [] } });
    const uri = use('Prompt.FileAttachment', { data: '', mime: 'text/x-dart', source: { type: 'uri', uri: 'file:///work/app/lib/upload/upload_screen.dart' }, name: 'upload_screen.dart', mention: { start: 20, end: 40, text: '@upload_screen.dart' } }, { probes: { data: [], mime: [], 'source.uri': [], 'mention.start': [], 'mention.end': [], 'mention.text': [] } });
    const agent = use('Prompt.AgentAttachment', { name: 'security-reviewer', mention: { start: 50, end: 68, text: '@security-reviewer' } });
    const skill = use('Prompt.SkillAttachment', { id: 'skill_flutter', name: 'flutter-testing', text: 'Always run the widget tests with --concurrency=1.', mention: { start: 70, end: 86, text: '/flutter-testing' } });
    return [use(`${M}User`, { id: id('msg'), metadata: { client: 'mobile' }, time: time(), text: 'Compare @screenshot.png and @upload_screen.dart', files: [file, uri], agents: [agent], skills: [skill] }, { probes: { metadata: [], 'files[]': [], 'agents[]': [], 'skills[]': [] } })];
  });

  add('assistant_all_fields', (use) => {
    const text = use(`${M}Assistant.Text`, { text: 'I added a Retry button next to every failed upload.', state: { openai: { itemId: 'msg_abc' } } }, { probes: { state: [] } });
    const reasoning = use(`${M}Assistant.Reasoning`, { text: 'The retry button must keep the failed file name, so I will read the queue first.', state: { anthropic: { signature: 'sig-abc' } }, time: { created: T0 + 2000, completed: T0 + 2900 } }, { probes: { state: [], 'time.created': [], 'time.completed': [] } });
    return [prompt(use), asst(use, {
      metadata: { traceId: 'tr-9' }, snapshot: { start: 'snap_a1', end: 'snap_b2', files: ['lib/upload/upload_screen.dart'] }, rawFinish: 'end_turn', providerState: { anthropic: { cacheKey: 'ck-1' } },
      cost: 0.0421, tokens: { input: 9100, output: 1210, reasoning: 400, cache: { read: 4000, write: 500 } },
      content: [reasoning, text],
    }, { metadata: [], 'snapshot.start': [], 'snapshot.end': [], 'snapshot.files[]': [], rawFinish: [], providerState: [], cost: [], 'tokens.input': [], 'tokens.output': [], 'tokens.reasoning': [], 'tokens.cache.read': [], 'tokens.cache.write': [] })];
  });

  // ---- tool steps ----
  const tm = {
    bash: { label: 'Shell', input: { command: 'flutter test test/upload_screen_test.dart', description: 'Run the upload screen tests', workdir: '/work/app', timeout: 120000 }, text: '00:04 +12: All tests passed!', metadata: { output: '00:04 +12: All tests passed!', exit: 0 }, tp: { 'metadata.exit': ['exit code 0'] } },
    read: { label: 'Read', input: { filePath: '/work/app/lib/main.dart', offset: 12, limit: 40 }, text: '00012| void main() {\n00013|   runApp(const App());', metadata: { truncated: true }, tp: { 'input.filePath': ['main.dart'], 'metadata.truncated': ['Truncated'] } },
    edit: { label: 'Edit', input: { filePath: '/work/app/lib/upload/upload_screen.dart', oldString: 'final retries = 1;', newString: 'final retries = 3;', replaceAll: true }, text: 'Edit applied successfully.', metadata: { diff: '--- a/lib/upload/upload_screen.dart\n+++ b/lib/upload/upload_screen.dart\n@@ -1 +1 @@\n-final retries = 1;\n+final retries = 3;', filediff: { file: '/work/app/lib/upload/upload_screen.dart', before: 'final retries = 1;', after: 'final retries = 3;', additions: 6, deletions: 4 }, diagnostics: { '/work/app/lib/upload/upload_screen.dart': [{ message: 'Unused import', severity: 2 }] } }, tp: { 'input.filePath': ['upload_screen.dart'], 'input.replaceAll': [], 'metadata.diff': [], 'metadata.filediff.file': ['upload_screen.dart'], 'metadata.diagnostics': [] }, noOutput: true },
    write: { label: 'Write', input: { filePath: '/work/app/lib/upload/retry_button.dart', content: 'class RetryButton extends StatelessWidget {}' }, text: 'Wrote file successfully.', metadata: { filepath: '/work/app/lib/upload/retry_button.dart', exists: false }, tp: { 'input.filePath': ['retry_button.dart'], 'metadata.filepath': ['retry_button.dart'], 'metadata.exists': ['new file'] }, noOutput: true },
    grep: { label: 'Search text', input: { pattern: 'TODO', path: 'lib', include: '*.dart' }, text: 'Found 5 matches\nlib/a.dart:\n  Line 12: // TODO tidy', metadata: { matches: 5, truncated: true }, tp: { 'metadata.truncated': ['Truncated'] } },
    glob: { label: 'Find files', input: { pattern: '**/*_test.dart', path: 'test' }, text: 'test/a_test.dart\ntest/b_test.dart', metadata: { count: 2, truncated: true }, tp: { 'metadata.truncated': ['Truncated'] } },
    webfetch: { label: 'Fetch page', input: { url: 'https://docs.flutter.dev/cookbook/networking', format: 'markdown', timeout: 30 }, text: 'Networking cookbook: fetch data from the internet', metadata: {}, tp: {} },
    task: { label: 'Delegated to explore', input: { description: 'Audit the upload retry code', prompt: 'Read lib/upload and list every place a retry can loop forever', subagent_type: 'explore', command: 'audit-retries' }, text: 'task_id: ses_child1 (for resuming)\n\n<task_result>\nTwo loops can retry forever.\n</task_result>', metadata: { sessionId: 'ses_child1', model: { providerID: 'openrouter', modelID: 'claude-sonnet-5-5' } }, tp: { output: [] }, hideOutput: true },
    todowrite: { label: 'Tasks', input: { todos: [{ content: 'Read the upload queue', status: 'completed', priority: 'high' }, { content: 'Add the retry button', status: 'in_progress', priority: 'medium' }, { content: 'Write the widget test', status: 'pending', priority: 'low' }] }, text: '[{"content":"Read the upload queue"}]', metadata: {}, tp: { 'input.todos[].status': ['Completed', 'In progress', 'Pending'], 'input.todos[].priority': ['High priority', 'Medium priority', 'Low priority'], output: [] }, hideOutput: true },
  };
  const inert = { input: [], metadata: [], 'content[]': [], error: [] };
  const toolCase = (cid, name, status, state, stateGroup, tg, opts = {}) => add(cid, (use) => {
    const m = tm[name];
    const stateV = use(stateGroup, state, { probes: onlySet(state, inert) });
    if (tg) use(`tool:${name}`, tg, { probes: onlySet(tg, { ...m.tp, ...(opts.toolProbes ?? {}) }) });
    const part = use(`${M}Assistant.Tool`, { id: id('call'), name, executed: true, providerState: { anthropic: { toolUse: 'tu_1' } }, providerResultState: { anthropic: { cache: 'c_1' } }, state: stateV, time: opts.time ?? { created: T0 + 3000, ran: T0 + 3100, completed: T0 + 7100 } }, { probes: { state: [], providerState: [], providerResultState: [], executed: [], 'time.created': [], 'time.ran': opts.ran ? ['4 seconds'] : [], 'time.completed': opts.ran ? ['4 seconds'] : [], ...(opts.partProbes ?? {}) } });
    return [prompt(use), asst(use, { content: [part], finish: 'tool-calls' })];
  });
  const completed = (name, extra = {}) => {
    const m = tm[name];
    const content = [use0('Tool.TextContent', { text: m.text })];
    return { input: m.input, content, metadata: m.metadata, ...extra };
  };
  // `use` is only available inside a case; text content items are plain values here.
  function use0(group, value) { return { type: group === 'Tool.TextContent' ? 'text' : 'file', ...value }; }
  for (const name of Object.keys(tm)) {
    const m = tm[name];
    add(`tool_${name}`, (use) => {
      const text = use('Tool.TextContent', { text: m.text }, { probes: m.noOutput || m.hideOutput ? { text: [] } : {} });
      const state = use(`${M}ToolState.Completed`, { input: m.input, content: [text], metadata: m.metadata }, { probes: { input: [], 'content[]': [], metadata: [] } });
      const tg = { input: m.input, ...(m.noOutput ? {} : { output: m.text }), metadata: m.metadata };
      use(`tool:${name}`, tg, { probes: onlySet(tg, m.tp) });
      const part = use(`${M}Assistant.Tool`, { id: id('call'), name, executed: true, providerState: { anthropic: { toolUse: 'tu_1' } }, providerResultState: { anthropic: { cache: 'c_1' } }, state, time: { created: T0 + 3000, ran: T0 + 3100, completed: T0 + 7100 } }, { probes: { name: [m.label], state: [], providerState: [], providerResultState: [], executed: [], 'time.created': name === 'bash' ? ['4 seconds'] : [], 'time.ran': name === 'bash' ? ['4 seconds'] : [], 'time.completed': name === 'bash' ? ['4 seconds'] : [] } });
      return [prompt(use), asst(use, { content: [part], finish: 'tool-calls' })];
    });
  }
  add('tool_running_streaming', (use) => {
    const streaming = use(`${M}ToolState.Streaming`, { input: '{"command":"npm run dev' }, { probes: { input: [] } });
    const running = use(`${M}ToolState.Running`, { input: { command: 'npm run dev' }, metadata: { output: 'ready on port 5173' } }, { probes: { input: [], metadata: [] } });
    const p1 = use(`${M}Assistant.Tool`, { id: id('call'), name: 'bash', state: streaming, time: { created: T0 + 3000 } }, { probes: { name: [], state: [], 'time.created': [] } });
    const p2 = use(`${M}Assistant.Tool`, { id: id('call'), name: 'bash', executed: true, state: running, time: { created: T0 + 3000, ran: T0 + 3100 } }, { probes: { name: ['Shell'], state: [], executed: [], 'time.created': [], 'time.ran': [] } });
    return [prompt(use), asst(use, { content: [p1, p2], finish: 'tool-calls', time: { created: T0 + 1000 } })];
  });
  add('tool_failed_with_file', (use) => {
    const err = use('Session.StructuredError', { type: 'tool.failed', message: 'Command exited with code 128: fatal: could not read Username', status: 128 }, { probes: { type: [], status: [] } });
    const file = use('Tool.FileContent', { uri: 'file:///work/app/build/push.log', mime: 'text/plain', name: 'push.log' });
    const text = use('Tool.TextContent', { text: 'remote: Invalid username or password.' }, { probes: { text: [] } });
    const state = use(`${M}ToolState.Error`, { input: { command: 'git push origin main' }, error: err, content: [text, file], metadata: { exit: 128 } }, { probes: { input: [], error: [], 'content[]': [], metadata: [] } });
    const part = use(`${M}Assistant.Tool`, { id: id('call'), name: 'bash', executed: true, state, time: { created: T0 + 3000, ran: T0 + 3100, completed: T0 + 3900 } }, { probes: { name: ['Shell'], state: [], executed: [], 'time.created': [], 'time.ran': [], 'time.completed': [] } });
    return [prompt(use), asst(use, { content: [part], finish: 'tool-calls' })];
  });

  // ---- failures and retries ----
  const errCase = (cid, type, message, status, extra = {}) => add(cid, (use) => {
    const err = use('Session.StructuredError', { type, message, status }, { probes: { type: [], status: [] } });
    return [prompt(use), asst(use, { error: err, finish: 'error', ...extra }, { error: [] })];
  });
  errCase('error_api_overloaded', 'api', 'The provider is overloaded, try again in a minute', 529);
  add('error_provider_auth_empty', (use) => {
    const err = use('Session.StructuredError', { type: 'provider.auth', message: '', status: 401 }, { probes: { type: [], message: [], status: [] } });
    return [prompt(use), asst(use, { error: err, finish: 'error' }, { error: [] })];
  });
  add('retry_scheduled', (use) => {
    const err = use('Session.StructuredError', { type: 'api', message: 'Rate limit reached for the model', status: 429 }, { probes: { type: [], message: [], status: [] } });
    return [prompt(use), asst(use, { retry: { attempt: 3, at: T0 + 20000, error: err }, finish: 'unknown', time: { created: T0 + 1000 } }, { 'retry.error': [], 'retry.attempt': [], 'retry.at': [] })];
  });

  // ---- notices ----
  add('synthetic_system_skill', (use) => [
    prompt(use),
    use(`${M}Synthetic`, { id: id('msg'), metadata: { source: 'subagent', childID: 'ses_child1', agent: 'explore', state: 'completed' }, time: time(), text: 'The explore agent finished: two loops can retry forever.', description: 'Helper agent finished' }, { probes: { metadata: [] } }),
    use(`${M}System`, { id: id('msg'), metadata: { origin: 'server' }, time: time(), text: 'The project rules file changed on disk.', description: 'Rules updated' }, { probes: { metadata: [] } }),
    use(`${M}Skill`, { id: id('msg'), metadata: { origin: 'slash' }, time: time(), skill: 'skill_flutter', name: 'flutter-testing', text: 'Always run the widget tests with --concurrency=1.' }, { probes: { metadata: [], skill: [] } }),
  ]);
  add('shell_messages', (use) => [
    use(`${M}Shell`, { id: id('msg'), metadata: { origin: 'composer' }, time: { created: T0 + 1000, completed: T0 + 3000 }, shellID: 'shl_1', command: 'ls -la lib/upload', status: 'exited', exit: 0, output: { output: 'upload_screen.dart\nupload_queue.dart', cursor: 40, size: 40, truncated: true } }, { probes: { metadata: [], shellID: [], 'time.created': [], 'time.completed': [], 'output.cursor': [], 'output.size': [], exit: ['exit code 0'], status: [], 'output.truncated': ['Truncated'] } }),
    use(`${M}Shell`, { id: id('msg'), time: { created: T0 + 4000, completed: T0 + 9000 }, shellID: 'shl_2', command: 'sleep 600', status: 'timeout', output: { output: 'partial output before the limit', cursor: 0, size: 0, truncated: false } }, { probes: { shellID: [], 'time.created': [], 'time.completed': [], 'output.cursor': [], 'output.size': [], status: ['Failed'], 'output.truncated': [] } }),
  ]);
  add('compaction_states', (use) => [
    prompt(use),
    use(`${M}Compaction.Running`, { id: id('msg'), metadata: { by: 'auto' }, time: time(), reason: 'auto', summary: 'Working on the upload retry button.', recent: 'The last turns are kept as they are.' }, { probes: { metadata: [], reason: [], recent: [], summary: [] } }),
    use(`${M}Compaction.Completed`, { id: id('msg'), metadata: { by: 'manual' }, time: time(), reason: 'manual', summary: 'Summary: the retry button was added and tested.', recent: 'Kept the last two turns.' }, { probes: { metadata: [], reason: [], recent: [] } }),
  ]);
  add('compaction_failed', (use) => {
    const err = use('Session.StructuredError', { type: 'compaction', message: 'The summary model refused the request', status: 400 }, { probes: { type: [], message: [], status: [] } });
    return [prompt(use), use(`${M}Compaction.Failed`, { id: id('msg'), metadata: { by: 'auto' }, time: time(), reason: 'auto', error: err }, { probes: { metadata: [], reason: [], error: [] } })];
  });
  add('switch_notices', (use) => [
    prompt(use),
    use(`${M}AgentSelected`, { id: id('msg'), metadata: { by: 'person' }, time: time(), agent: 'plan', previous: 'build' }, { probes: { metadata: [] } }),
    use(`${M}ModelSelected`, { id: id('msg'), metadata: { by: 'person' }, time: time(), model: { id: 'gpt-6-sol', providerID: 'openai', variant: 'high' }, previous: { id: 'claude-opus-5-5', providerID: 'anthropic', variant: 'max' } }, { probes: { metadata: [], 'model.providerID': [], 'previous.providerID': [], 'model.variant': ['gpt-6-sol · high'], 'previous.variant': ['claude-opus-5-5 · max'] } }),
    use(`${M}LocationSwitched`, { id: id('msg'), metadata: { by: 'person' }, time: time(), location: { directory: '/work/shopfront-web', workspaceID: 'wrk_1' }, projectID: 'prj_1', subpath: 'packages/web', previous: { location: { directory: '/work/shopfront', workspaceID: 'wrk_0' }, projectID: 'prj_0', subpath: 'packages/app' } }, { probes: { metadata: [], 'location.directory': ['shopfront-web'], 'location.workspaceID': [], projectID: [], subpath: [], 'previous.location.directory': [], 'previous.location.workspaceID': [], 'previous.projectID': [], 'previous.subpath': [] } }),
  ]);
  return { schema, cases };
}
