// Family "oc2_events": the events an OpenCode 2 server streams to a chat
// (GET /api/event and the durable session log). The beta-18600 contract types
// the stream only as an opaque JSON string, so the event list and each
// event's payload keys are read from docs/opencode2-protocol-notes.md section
// 3.2 (the table, plus the prose lists for status, requests, forms, pty,
// shell, catalog and tui events, which are written out below from the same
// section). Conversation events run through the app's real adapter, event
// handler and conversation screen; every other event type has one ledger
// line saying why it never reaches a conversation.
import fs from 'node:fs';
import { buildFamily } from '../paseo_cases_lib.mjs';

const DOC = 'docs/opencode2-protocol-notes.md';

function tableEvents() {
  const doc = fs.readFileSync(DOC, 'utf8');
  const sec = doc.slice(doc.indexOf('### 3.2'), doc.indexOf('### 3.3'));
  const out = {};
  for (const line of sec.split('\n')) {
    const m = line.match(/^\| (?:⚡ )?`([a-z.\-]+)` \| (.*) \|$/);
    if (!m) continue;
    const braces = m[2].match(/`\{(.*)\}`/);
    out[m[1]] = braces ? topKeys(braces[1]) : [];
  }
  return out;
}

function topKeys(inner) {
  const keys = [];
  let depth = 0;
  let cur = '';
  for (const ch of inner) {
    if ('{[('.includes(ch)) depth += 1;
    if ('}])'.includes(ch)) depth -= 1;
    if (ch === ',' && depth === 0) { keys.push(cur); cur = ''; } else cur += ch;
  }
  if (cur.trim()) keys.push(cur);
  return keys.map((s) => s.trim().replace(/^\.\.\./, '').split(':')[0].replace('?', '').trim());
}

// The events section 3.2 lists in prose rather than in its table.
const PROSE = {
  'session.status': ['sessionID', 'status'], 'session.idle': ['sessionID'], 'session.compacted': ['sessionID'],
  'permission.asked': ['id', 'sessionID', 'action', 'resources', 'save', 'metadata', 'source', 'message'],
  'permission.replied': ['sessionID', 'requestID', 'reply'],
  'form.created': ['form'], 'form.replied': ['id', 'sessionID', 'answer'], 'form.cancelled': ['id', 'sessionID'],
  'pty.created': ['info'], 'pty.updated': ['info'], 'pty.exited': ['id', 'exitCode'], 'pty.deleted': ['id'],
  'persistent-pty.added': ['sessionID', 'terminal'], 'persistent-pty.removed': ['sessionID', 'ptyID'],
  'shell.created': ['info'], 'shell.exited': ['id', 'exit', 'status'], 'shell.deleted': ['id'],
  'agent.updated': [], 'command.updated': [], 'config.updated': [], 'skill.updated': [], 'catalog.updated': [], 'models-dev.refreshed': [],
  'integration.updated': [], 'credential.updated': [], 'credential.switched': ['integrationID', 'credentialID'], 'plugin.added': ['id'], 'plugin.updated': [],
  'reference.updated': [], 'websearch.updated': [], 'project.updated': [], 'worktree.updated': ['projectID'], 'worktree.resolved': ['projectID', 'directory', 'previous', 'adopted'],
  'filesystem.changed': ['file', 'event'], 'vcs.branch.updated': ['branch'], 'mcp.status.changed': ['server'], 'mcp.resources.changed': ['server'], 'mcp.tools.changed': ['server'],
  'installation.updated': ['version'], 'installation.update-available': ['version'], 'workspace.ready': ['name'], 'workspace.failed': ['message'], 'workspace.status': ['workspaceID', 'status'],
  'worktree.ready': ['name', 'branch'], 'worktree.failed': ['message'], 'server.connected': [], 'global.disposed': [], 'lsp.updated': [],
  'tui.prompt.append': ['text'], 'tui.command.execute': ['command'], 'tui.toast.show': ['title', 'message', 'variant', 'duration'], 'tui.session.select': ['sessionID'],
};

// Events with field-level cases (they put something in a conversation).
export const FIELD_TYPES = new Set([
  'session.renamed', 'session.execution.started', 'session.execution.succeeded', 'session.execution.failed', 'session.execution.interrupted',
  'session.inbox.enqueued', 'session.inbox.delivered', 'session.inbox.cancelled', 'session.inbox.delivery.changed', 'session.instructions.updated',
  'session.synthetic', 'session.step.started', 'session.step.streamed', 'session.step.ended', 'session.step.failed',
  'session.text.started', 'session.text.delta', 'session.text.ended', 'session.reasoning.started', 'session.reasoning.delta', 'session.reasoning.ended',
  'session.tool.input.started', 'session.tool.input.delta', 'session.tool.input.ended', 'session.tool.called', 'session.tool.progress', 'session.tool.success', 'session.tool.failed',
  'session.retry.scheduled', 'session.compaction.started', 'session.compaction.delta', 'session.compaction.ended', 'session.compaction.failed',
  'session.revert.staged', 'session.revert.cleared', 'session.revert.committed', 'session.message.content.updated',
  'session.status', 'session.idle', 'session.compacted', 'permission.asked', 'permission.replied', 'form.created', 'form.replied', 'form.cancelled',
]);

export function oc2EventsFamily() {
  const known = { ...tableEvents(), ...PROSE };
  const schema = {
    envelope: { id: { kind: 'string' }, created: { kind: 'number' }, 'location.directory': { kind: 'string' }, metadata: { kind: 'any' }, 'durable.aggregateID': { kind: 'string' }, 'durable.seq': { kind: 'number' }, 'durable.version': { kind: 'number' } },
    eventTypes: Object.fromEntries(Object.keys(known).map((t) => [t, { kind: 'event' }])),
  };
  const excluded = {};
  for (const t of Object.keys(known)) excluded[`eventTypes.${t}`] = FIELD_TYPES.has(t) ? 'field-level' : 'type-level';
  for (const t of FIELD_TYPES) {
    if (!known[t]) throw new Error(`field-level event ${t} is not in the protocol notes`);
    schema[`event:${t}`] = Object.fromEntries(known[t].map((k) => [`data.${k}`, { kind: 'any' }]));
  }

  const SES = 'ses_checkout';
  const A1 = 'msg_asst01';
  const T0 = 1788960000000;
  let n = 0;
  let seq = 0;
  const cases = [];
  const groupsOf = (type) => `event:${type}`;
  /** One case: a list of envelopes (type, data) with their probes. */
  const kase = (id, build, meta = {}) => {
    const parts = [];
    const events = [];
    const send = (type, data, probes = {}, withEnvelope = false) => {
      n += 1;
      seq += 1;
      const envelopeFields = withEnvelope
        ? { id: `evt_${String(n).padStart(4, '0')}`, created: T0 + n, location: { directory: '/work/app' }, metadata: { source: 'tui' }, durable: { aggregateID: SES, seq, version: 1 } }
        : { id: `evt_${String(n).padStart(4, '0')}`, created: T0 + n };
      parts.push({ group: 'envelope', value: envelopeFields, probes: Object.fromEntries(['id', 'created', ...(withEnvelope ? ['location.directory', 'metadata', 'durable.aggregateID', 'durable.seq', 'durable.version'] : [])].map((k) => [k, []])) });
      const dataFields = {};
      for (const [k, v] of Object.entries(data)) dataFields[`data.${k}`] = v;
      parts.push({ group: groupsOf(type), value: { data }, probes: { ...Object.fromEntries(Object.keys(data).filter((k) => ['sessionID', 'assistantMessageID', 'inboxID', 'ordinal', 'id'].includes(k)).map((k) => [`data.${k}`, []])), ...probes } });
      events.push({ ...envelopeFields, type, data });
    };
    build(send);
    cases.push({ id, payload: events, parts, ...meta });
  };
  const prompt = (send, text = 'Add a retry button to the upload screen', delivered = true) => {
    send('session.inbox.enqueued', { sessionID: SES, inboxID: 'msg_inbox01', item: { type: 'user', payload: { text }, delivery: 'steer' } }, { 'data.item': [text] });
    if (delivered) send('session.inbox.delivered', { sessionID: SES, inboxID: 'msg_inbox01' });
  };

  kase('turn_done', (send) => {
    send('session.execution.started', { sessionID: SES }, {}, true);
    prompt(send, undefined, false);
    send('session.instructions.updated', { sessionID: SES, delta: { 'core/environment': '1adeb0c' }, text: 'Prefer short answers' }, { 'data.delta': [], 'data.text': [] });
    send('session.inbox.delivered', { sessionID: SES, inboxID: 'msg_inbox01' });
    send('session.step.started', { sessionID: SES, assistantMessageID: A1, agent: 'build', model: { id: 'claude-opus-5-5', providerID: 'anthropic', variant: 'high' }, snapshot: 'snap_c5104f' }, { 'data.agent': [], 'data.model': ['claude-opus-5-5'], 'data.snapshot': [] });
    send('session.reasoning.started', { sessionID: SES, assistantMessageID: A1, ordinal: 0, state: { signature: 'sig-r0' } }, { 'data.state': [] });
    send('session.reasoning.delta', { sessionID: SES, assistantMessageID: A1, ordinal: 0, delta: 'Thinking about the queue first' }, { 'data.delta': [] });
    send('session.reasoning.ended', { sessionID: SES, assistantMessageID: A1, ordinal: 0, text: 'The retry button must keep the failed file name, so I will read the queue first.', state: { signature: 'sig-r0-end' } }, { 'data.state': [] });
    send('session.text.started', { sessionID: SES, assistantMessageID: A1, ordinal: 0 });
    send('session.text.delta', { sessionID: SES, assistantMessageID: A1, ordinal: 0, delta: 'I added a ' }, { 'data.delta': [] });
    send('session.text.ended', { sessionID: SES, assistantMessageID: A1, ordinal: 0, text: 'I added a Retry button next to every failed upload.', state: { itemId: 'msg_item', phase: 'final_answer' } }, { 'data.state': [] });
    send('session.step.streamed', { sessionID: SES, assistantMessageID: A1 });
    send('session.step.ended', { sessionID: SES, assistantMessageID: A1, finish: 'stop', rawFinish: 'end_turn', providerState: { responseId: 'resp_1' }, cost: 0.0421, tokens: { input: 9100, output: 1210, reasoning: 400, cache: { read: 4000, write: 500 } }, snapshot: 'snap_d4e5f6', files: ['lib/upload/upload_screen.dart'] }, { 'data.finish': [], 'data.rawFinish': [], 'data.providerState': [], 'data.cost': [], 'data.tokens': [], 'data.snapshot': [], 'data.files': [] });
  });
  kase('live_streaming', (send) => {
    send('session.execution.started', { sessionID: SES });
    prompt(send, 'Explain the retry loop');
    send('session.step.started', { sessionID: SES, assistantMessageID: A1, agent: 'build', model: { id: 'claude-opus-5-5', providerID: 'anthropic' } }, { 'data.agent': [], 'data.model': [] });
    send('session.reasoning.started', { sessionID: SES, assistantMessageID: A1, ordinal: 0 });
    send('session.reasoning.delta', { sessionID: SES, assistantMessageID: A1, ordinal: 0, delta: 'Looking at how the queue retries' }, { 'data.delta': ['Looking at how the queue retries'] });
    send('session.text.started', { sessionID: SES, assistantMessageID: A1, ordinal: 0 });
    send('session.text.delta', { sessionID: SES, assistantMessageID: A1, ordinal: 0, delta: 'The queue retries three times' }, { 'data.delta': ['The queue retries three times'] });
  });
  kase('tool_run', (send) => {
    send('session.execution.started', { sessionID: SES });
    prompt(send, 'Push the branch');
    send('session.step.started', { sessionID: SES, assistantMessageID: A1, agent: 'build', model: { id: 'claude-opus-5-5', providerID: 'anthropic' } }, { 'data.agent': [], 'data.model': [] });
    send('session.tool.input.started', { sessionID: SES, assistantMessageID: A1, id: 'call_t1', name: 'bash' }, { 'data.name': ['Shell'] });
    send('session.tool.input.delta', { sessionID: SES, assistantMessageID: A1, id: 'call_t1', delta: '{"command":"git pu' }, { 'data.delta': [] });
    send('session.tool.input.ended', { sessionID: SES, assistantMessageID: A1, id: 'call_t1', text: '{"command":"git push origin main"}' }, { 'data.text': [] });
    send('session.tool.called', { sessionID: SES, assistantMessageID: A1, id: 'call_t1', input: { command: 'git push origin main', description: 'Publish the branch' }, executed: true, state: { provider: 'anthropic' } }, { 'data.input': ['git push origin main'], 'data.state': [] });
    send('session.tool.progress', { sessionID: SES, assistantMessageID: A1, id: 'call_t1', metadata: { output: 'Enumerating objects: 12, done.' } }, { 'data.metadata': [] });
    send('session.tool.success', { sessionID: SES, assistantMessageID: A1, id: 'call_t1', content: [{ type: 'text', text: 'Everything up-to-date' }], metadata: { exit: 0 }, executed: true, resultState: { provider: 'anthropic' } }, { 'data.content': ['Everything up-to-date'], 'data.metadata': ['exit code 0'], 'data.resultState': [] });
    send('session.tool.called', { sessionID: SES, assistantMessageID: A1, id: 'call_t2', input: { command: 'rm -rf build/cache' }, executed: false }, { 'data.input': [] });
    send('session.tool.failed', { sessionID: SES, assistantMessageID: A1, id: 'call_t2', error: { type: 'tool.failed', message: 'Permission denied while removing build/cache', status: 126 }, content: [{ type: 'text', text: 'rm: cannot remove build/cache' }], metadata: { exit: 126 }, executed: true, resultState: { provider: 'anthropic' } }, { 'data.error': ['Permission denied while removing build/cache'], 'data.content': [], 'data.metadata': [], 'data.resultState': [] });
  });
  kase('step_failed_and_retry', (send) => {
    send('session.execution.started', { sessionID: SES });
    prompt(send);
    send('session.step.started', { sessionID: SES, assistantMessageID: A1, agent: 'build', model: { id: 'claude-opus-5-5', providerID: 'anthropic' } }, { 'data.agent': [], 'data.model': [] });
    send('session.step.failed', { sessionID: SES, assistantMessageID: A1, error: { type: 'api', message: 'The provider is overloaded, try again in a minute', status: 529 }, finish: 'content-filter', rawFinish: 'blocked', providerState: { responseId: 'resp_2' }, cost: 0.002, tokens: { input: 500, output: 0, reasoning: 0, cache: { read: 0, write: 0 } }, snapshot: 'snap_f1', files: ['lib/a.dart'] }, { 'data.error': ['The provider is overloaded, try again in a minute'], 'data.finish': [], 'data.rawFinish': [], 'data.providerState': [], 'data.cost': [], 'data.tokens': [], 'data.snapshot': [], 'data.files': [] });
    send('session.execution.failed', { sessionID: SES, error: { type: 'provider.auth', message: '', status: 401 } }, { 'data.error': ['credentials'] });
  });
  kase('retry_only', (send) => {
    send('session.execution.started', { sessionID: SES });
    prompt(send);
    send('session.step.started', { sessionID: SES, assistantMessageID: A1, agent: 'build', model: { id: 'claude-opus-5-5', providerID: 'anthropic' } }, { 'data.agent': [], 'data.model': [] });
    send('session.retry.scheduled', { sessionID: SES, assistantMessageID: A1, attempt: 3, at: T0 + 20000, error: { type: 'api', message: 'Rate limit reached for the model', status: 429 } }, { 'data.attempt': ['Retrying 3'], 'data.at': ['in 0:'], 'data.error': ['Rate limited'] });
  });
  kase('tool_progress_only', (send) => {
    send('session.execution.started', { sessionID: SES });
    prompt(send, 'Push the branch');
    send('session.step.started', { sessionID: SES, assistantMessageID: A1, agent: 'build', model: { id: 'claude-opus-5-5', providerID: 'anthropic' } }, { 'data.agent': [], 'data.model': [] });
    send('session.tool.input.started', { sessionID: SES, assistantMessageID: A1, id: 'call_t9', name: 'bash' }, { 'data.name': [] });
    send('session.tool.called', { sessionID: SES, assistantMessageID: A1, id: 'call_t9', input: { command: 'git push origin main' }, executed: true }, { 'data.input': [] });
    send('session.tool.progress', { sessionID: SES, assistantMessageID: A1, id: 'call_t9', metadata: { output: 'Enumerating objects: 12, done.' } }, { 'data.metadata': ['Enumerating objects: 12, done.'] });
  });
  kase('interrupted_and_inbox', (send) => {
    send('session.execution.started', { sessionID: SES });
    prompt(send, 'First message');
    send('session.inbox.enqueued', { sessionID: SES, inboxID: 'msg_inbox02', item: { type: 'user', payload: { text: 'Second message that gets cancelled' }, delivery: 'queue' } }, { 'data.item': [] });
    send('session.inbox.delivery.changed', { sessionID: SES, inboxID: 'msg_inbox02', delivery: 'steer' }, { 'data.delivery': [] });
    send('session.inbox.cancelled', { sessionID: SES, inboxID: 'msg_inbox02' });
    send('session.execution.interrupted', { sessionID: SES, reason: 'user' }, { 'data.reason': [] });
  });
  kase('compaction_run', (send) => {
    send('session.compaction.started', { sessionID: SES, reason: 'auto', recent: 'Last two turns kept', inputID: 'msg_inbox03' }, { 'data.reason': [], 'data.recent': [], 'data.inputID': [] });
    send('session.compaction.delta', { sessionID: SES, text: 'Summarising the upload work' }, { 'data.text': [] });
    send('session.compaction.ended', { sessionID: SES, reason: 'auto', text: 'Summary: the retry button was added and tested.', recent: 'Last two turns kept' }, { 'data.reason': [], 'data.recent': [], 'data.text': [] });
    send('session.compacted', { sessionID: SES });
  });
  kase('compaction_failed', (send) => {
    send('session.compaction.failed', { sessionID: SES, reason: 'manual', error: { type: 'compaction', message: 'The summary model refused the request', status: 400 }, inputID: 'msg_inbox04' }, { 'data.reason': [], 'data.error': [], 'data.inputID': [] });
  });
  kase('notices_and_revert', (send) => {
    prompt(send);
    send('session.synthetic', { sessionID: SES, text: 'The explore agent finished: two loops can retry forever.', description: 'Helper agent finished', metadata: { source: 'subagent', childID: 'ses_child1', agent: 'explore', state: 'completed' } }, { 'data.metadata': [], 'data.text': [], 'data.description': [] });
    send('session.revert.staged', { sessionID: SES, revert: { messageID: 'msg_inbox01', diff: '@@ -1 +1 @@' } }, { 'data.revert': [] });
    send('session.revert.cleared', { sessionID: SES });
    send('session.revert.committed', { sessionID: SES, to: 'msg_inbox01' }, { 'data.to': [] });
    send('session.message.content.updated', { sessionID: SES, messageID: A1, content: [{ type: 'text', text: 'Edited answer from a PATCH' }] }, { 'data.content': [], 'data.messageID': [] });
    send('session.renamed', { sessionID: SES, title: 'Retry upload button' }, {});
  });
  kase('status_busy_idle', (send) => {
    prompt(send);
    send('session.status', { sessionID: SES, status: { type: 'busy' } }, { 'data.status': [] });
    send('session.execution.succeeded', { sessionID: SES });
    send('session.idle', { sessionID: SES });
  });
  kase('status_retry_with_action', (send) => {
    prompt(send);
    send('session.status', { sessionID: SES, status: { type: 'retry', attempt: 3, message: 'Rate limit reached for the model', next: T0 + 20000, action: { reason: 'rate_limit', provider: 'anthropic', title: 'Upgrade your plan', message: 'Your plan allows 20 requests a minute', label: 'See plans', link: 'https://console.anthropic.com/settings/plans' } } }, { 'data.status': ['Retrying 3', 'Upgrade your plan', 'See plans'] });
  });
  kase('permission_asked', (send) => {
    send('permission.asked', { id: 'per_ev1', sessionID: SES, action: 'bash', resources: ['git push origin main'], save: ['git push *'], metadata: { command: 'git push origin main' }, source: { type: 'tool', messageID: A1, id: 'call_t1' }, message: 'The agent wants to publish the branch' }, { 'data.action': ['bash'], 'data.resources': ['git push origin main'], 'data.save': ['git push *'], 'data.metadata': [], 'data.source': [], 'data.message': ['The agent wants to publish the branch'] });
  });
  kase('permission_replied', (send) => {
    send('permission.asked', { id: 'per_ev2', sessionID: SES, action: 'bash', resources: ['rm -rf build/cache'], save: ['rm *'], metadata: {}, source: { type: 'tool', messageID: A1, id: 'call_t2' }, message: 'The agent wants to clear the cache' }, { 'data.action': [], 'data.resources': [], 'data.save': [], 'data.metadata': [], 'data.source': [], 'data.message': [] });
    send('permission.replied', { sessionID: SES, requestID: 'per_ev2', reply: 'once' }, { 'data.reply': [], 'data.requestID': [] });
  }, { hidden: ['rm -rf build/cache'] });
  kase('form_created', (send) => {
    send('form.created', { form: { id: 'frm_ev1', sessionID: SES, title: 'Connect to Sentry', fields: [{ key: 'env', type: 'string', title: 'Environment', required: true, options: [{ value: 'prod', label: 'Production' }] }] } }, { 'data.form': ['Connect to Sentry'] });
  });
  kase('form_replied_cancelled', (send) => {
    send('form.created', { form: { id: 'frm_ev2', sessionID: SES, title: 'Pick a branch for the push', fields: [{ key: 'b', type: 'boolean', title: 'Create it' }] } }, { 'data.form': [] });
    send('form.replied', { id: 'frm_ev2', sessionID: SES, answer: { b: true } }, { 'data.answer': [] });
    send('form.created', { form: { id: 'frm_ev3', sessionID: SES, title: 'Choose a cache policy', fields: [{ key: 'c', type: 'boolean', title: 'Clear it' }] } }, { 'data.form': [] });
    send('form.cancelled', { id: 'frm_ev3', sessionID: SES });
  }, { hidden: ['Pick a branch for the push', 'Choose a cache policy'] });

  // The built family: field groups run through buildFamily, event types are excluded.
  return { schema, cases, excluded };
}
