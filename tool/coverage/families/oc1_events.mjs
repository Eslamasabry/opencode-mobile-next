// Family "oc1_events": the events an OpenCode 1 server streams to a chat
// (/event, contracts/opencode-openapi-f12e14cf.json). Events that carry a
// conversation (messages, parts, deltas, status, errors, permission and
// question asks and answers) are run field by field through the app's real
// event handler and conversation screen. Every other event type gets one
// ledger line saying why it never reaches a conversation.
import { familyBuilder } from '../oc_cases_lib.mjs';

const ERRORS = ['ProviderAuthError', 'UnknownError', 'MessageOutputLengthError', 'MessageAbortedError', 'StructuredOutputError', 'ContextOverflowError', 'ContentFilterError', 'APIError'];

// Event types that have field-level groups (everything else is type-level).
export const FIELD_EVENTS = {
  EventMessageUpdated: 'message.updated', EventMessageRemoved: 'message.removed', EventMessagePartUpdated: 'message.part.updated',
  EventMessagePartRemoved: 'message.part.removed', EventMessagePartDelta: 'message.part.delta', EventSessionError: 'session.error',
  EventSessionStatus: 'session.status', EventSessionIdle: 'session.idle', EventSessionCompacted: 'session.compacted',
  EventPermissionAsked: 'permission.asked', EventPermissionReplied: 'permission.replied', EventQuestionAsked: 'question.asked',
  EventQuestionReplied: 'question.replied', EventQuestionRejected: 'question.rejected', EventTodoUpdated: 'todo.updated',
  EventSessionDiff: 'session.diff', EventCommandExecuted: 'command.executed',
};

export function oc1EventsFamily(contract) {
  const { schema, kase, S: schemas } = familyBuilder(contract, [
    { name: 'EventMessageUpdated', stop: ['Message'] },
    { name: 'EventMessageRemoved' },
    { name: 'EventMessagePartUpdated', stop: ['Part'] },
    { name: 'EventMessagePartRemoved' },
    { name: 'EventMessagePartDelta' },
    { name: 'EventSessionError', stop: ERRORS },
    { name: 'EventSessionStatus' },
    { name: 'EventSessionIdle' },
    { name: 'EventSessionCompacted' },
    { name: 'EventPermissionAsked' },
    { name: 'EventPermissionReplied' },
    { name: 'EventQuestionAsked' },
    { name: 'EventQuestionReplied' },
    { name: 'EventQuestionRejected' },
    { name: 'EventTodoUpdated' },
    { name: 'EventSessionDiff' },
    { name: 'EventCommandExecuted' },
  ]);
  // Every event type in the contract; those without a field group are excluded
  // from the field ratchet and decided one by one.
  const types = schemas.Event.anyOf.map((r) => schemas[r.$ref.split('/').pop()].properties.type.enum[0]);
  const fieldTypes = new Set(Object.values(FIELD_EVENTS));
  schema.eventTypes = Object.fromEntries(types.map((t) => [t, { kind: 'event' }]));
  const excluded = Object.fromEntries(types.filter((t) => !fieldTypes.has(t)).map((t) => [`eventTypes.${t}`, 'type-level']));
  for (const t of fieldTypes) excluded[`eventTypes.${t}`] = 'field-level';

  const SES = 'ses_checkout';
  let n = 0;
  const evt = () => `evt_${String(++n).padStart(4, '0')}`;
  const T0 = 1788960000000;
  const cases = [];
  const add = (id, build, meta = {}) => cases.push(kase(id, build, meta));
  const user = (use, text = 'Add a retry button to the upload screen') => {
    const info = { id: 'msg_u1', sessionID: SES, role: 'user', time: { created: T0 }, agent: 'build', model: { providerID: 'anthropic', modelID: 'claude-opus-5-5' } };
    return [
      use('EventMessageUpdated', { id: evt(), properties: { sessionID: SES, info } }, { probes: { 'properties.info': [] } }),
      use('EventMessagePartUpdated', { id: evt(), properties: { sessionID: SES, part: { id: 'prt_u1', sessionID: SES, messageID: 'msg_u1', type: 'text', text }, time: T0 + 5 } }, { probes: { 'properties.part': [], 'properties.time': [] } }),
    ];
  };
  const assistant = (use, id = 'msg_a1') => use('EventMessageUpdated', { id: evt(), properties: { sessionID: SES, info: { id, sessionID: SES, role: 'assistant', time: { created: T0 + 1000 }, parentID: 'msg_u1', modelID: 'claude-opus-5-5', providerID: 'anthropic', mode: 'build', agent: 'build', path: { cwd: '/w', root: '/w' }, cost: 0, tokens: { input: 0, output: 0, reasoning: 0, cache: { read: 0, write: 0 } } } } }, { probes: { 'properties.info': [] } });

  add('live_text_stream', (use) => [
    ...user(use),
    assistant(use),
    use('EventMessagePartUpdated', { id: evt(), properties: { sessionID: SES, part: { id: 'prt_a1', sessionID: SES, messageID: 'msg_a1', type: 'text', text: '' }, time: T0 + 1100 } }, { probes: { 'properties.part': [], 'properties.time': [] } }),
    use('EventMessagePartDelta', { id: evt(), properties: { sessionID: SES, messageID: 'msg_a1', partID: 'prt_a1', field: 'text', delta: 'I am adding a Retry button next to every failed upload' } }, { probes: { 'properties.field': [] } }),
  ]);
  add('live_removal', (use) => [
    ...user(use),
    assistant(use),
    use('EventMessagePartUpdated', { id: evt(), properties: { sessionID: SES, part: { id: 'prt_a1', sessionID: SES, messageID: 'msg_a1', type: 'text', text: 'Draft answer that the server then discards' }, time: T0 + 1100 } }, { probes: { 'properties.part': [], 'properties.time': [] } }),
    assistant(use, 'msg_a2'),
    use('EventMessagePartUpdated', { id: evt(), properties: { sessionID: SES, part: { id: 'prt_a2', sessionID: SES, messageID: 'msg_a2', type: 'text', text: 'Reverted answer that is taken back' }, time: T0 + 1200 } }, { probes: { 'properties.part': [], 'properties.time': [] } }),
    use('EventMessagePartRemoved', { id: evt(), properties: { sessionID: SES, messageID: 'msg_a1', partID: 'prt_a1' } }),
    use('EventMessageRemoved', { id: evt(), properties: { sessionID: SES, messageID: 'msg_a2' } }),
  ], { hidden: ['Draft answer that the server then discards', 'Reverted answer that is taken back'] });
  add('session_error', (use) => [
    ...user(use),
    use('EventSessionError', { id: evt(), properties: { sessionID: SES, error: { name: 'UnknownError', data: { message: 'The connection to the model dropped in the middle of the reply' } } } }, { probes: { 'properties.error': ['The connection to the model dropped in the middle of the reply'] } }),
  ]);
  add('status_retry', (use) => [
    ...user(use),
    use('EventSessionStatus', { id: evt(), properties: { sessionID: SES, status: { type: 'retry', attempt: 3, message: 'Rate limit reached for the model', action: { reason: 'rate_limit', provider: 'anthropic', title: 'Upgrade your plan', message: 'Your plan allows 20 requests a minute', label: 'See plans', link: 'https://console.anthropic.com/settings/plans' }, next: T0 + 20_000 } } }, { probes: { 'properties.status.type': [], 'properties.status.attempt': ['Retrying 3'], 'properties.status.message': ['Rate limited'], 'properties.status.next': ['in 0:'] } }),
  ]);
  add('status_busy_idle_compacted', (use) => [
    ...user(use),
    use('EventSessionStatus', { id: evt(), properties: { sessionID: SES, status: { type: 'busy' } } }, { probes: { 'properties.status.type': [] } }),
    use('EventSessionCompacted', { id: evt(), properties: { sessionID: SES } }),
    use('EventSessionIdle', { id: evt(), properties: { sessionID: SES } }),
  ]);
  add('permission_asked', (use) => [
    use('EventPermissionAsked', { id: evt(), properties: { id: 'per_ev1', sessionID: SES, permission: 'bash', patterns: ['git push origin main'], metadata: {}, always: ['git push *'], tool: { messageID: 'msg_a1', callID: 'call_ev1' } } }, { probes: { 'properties.metadata': [] } }),
  ]);
  add('permission_replied', (use) => [
    use('EventPermissionAsked', { id: evt(), properties: { id: 'per_ev2', sessionID: SES, permission: 'bash', patterns: ['rm -rf build/cache'], metadata: {}, always: ['rm *'], tool: { messageID: 'msg_a1', callID: 'call_ev2' } } }, { probes: { 'properties.metadata': [], 'properties.patterns[]': [], 'properties.always[]': [], 'properties.permission': [] } }),
    use('EventPermissionReplied', { id: evt(), properties: { sessionID: SES, requestID: 'per_ev2', reply: 'once' } }),
  ], { hidden: ['rm -rf build/cache'] });
  add('question_asked', (use) => [
    use('EventQuestionAsked', { id: evt(), properties: { id: 'que_ev1', sessionID: SES, questions: [{ question: 'Which branch should I push to?', header: 'Branch', options: [{ label: 'main', description: 'The shared branch' }, { label: 'retry-button', description: 'A new feature branch' }], multiple: true, custom: true }], tool: { messageID: 'msg_a1', callID: 'call_q1' } } }, { probes: { 'properties.questions[].multiple': ['None selected'], 'properties.questions[].custom': ['Or write your own answer'] } }),
  ]);
  add('question_replied', (use) => [
    use('EventQuestionAsked', { id: evt(), properties: { id: 'que_ev2', sessionID: SES, questions: [{ question: 'Should the old screen stay?', header: 'Old screen', options: [{ label: 'Keep it', description: 'Leave it as is' }], multiple: false, custom: false }], tool: { messageID: 'msg_a1', callID: 'call_q2' } } }, { probes: { 'properties.questions[].question': [], 'properties.questions[].header': [], 'properties.questions[].options[].label': [], 'properties.questions[].options[].description': [], 'properties.questions[].multiple': [], 'properties.questions[].custom': [] } }),
    use('EventQuestionReplied', { id: evt(), properties: { sessionID: SES, requestID: 'que_ev2', answers: [['Keep it']] } }, { probes: { 'properties.answers[][]': [] } }),
  ], { hidden: ['Should the old screen stay?'] });
  add('question_rejected', (use) => [
    use('EventQuestionAsked', { id: evt(), properties: { id: 'que_ev3', sessionID: SES, questions: [{ question: 'Delete the cache folder first?', header: 'Cache', options: [{ label: 'Delete', description: 'Remove it' }], multiple: false, custom: false }], tool: { messageID: 'msg_a1', callID: 'call_q3' } } }, { probes: { 'properties.questions[].question': [], 'properties.questions[].header': [], 'properties.questions[].options[].label': [], 'properties.questions[].options[].description': [], 'properties.questions[].multiple': [], 'properties.questions[].custom': [] } }),
    use('EventQuestionRejected', { id: evt(), properties: { sessionID: SES, requestID: 'que_ev3' } }),
  ], { hidden: ['Delete the cache folder first?'] });
  add('side_events', (use) => [
    ...user(use),
    use('EventTodoUpdated', { id: evt(), properties: { sessionID: SES, todos: [{ content: 'Sketch the retry flow on paper', status: 'in_progress', priority: 'high' }] } }),
    use('EventSessionDiff', { id: evt(), properties: { sessionID: SES, diff: [{ file: 'lib/side/never_shown.dart', patch: '@@ -1 +1 @@\n-a\n+b', additions: 13, deletions: 8, status: 'modified' }] } }),
    use('EventCommandExecuted', { id: evt(), properties: { name: 'compact-notes', sessionID: SES, arguments: 'quarterly', messageID: 'msg_cmd1' } }),
  ]);
  return { schema, cases, excluded };
}
