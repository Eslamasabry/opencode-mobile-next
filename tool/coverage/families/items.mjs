// Family "items": the timeline items of a chat that are not tool steps
// (user and assistant messages, reasoning, todo lists, errors, notifications,
// compaction, plugin items), as Paseo's AgentTimelineItemPayloadSchema
// defines them. Each case is one timeline item the way Paseo emits it.
import { members, literalOf, objectFields } from '../paseo_cases_lib.mjs';

export function itemSchema(root) {
  const schema = {};
  for (const o of members(root)) {
    const type = literalOf(o, 'type');
    if (type && type !== 'tool_call') schema[type] = objectFields(o);
  }
  return schema;
}

const item = (id, type, value, probes) => ({
  id,
  family: 'items',
  payload: { type, ...value },
  parts: [{ group: type, value, probes }],
});

export const itemCases = [
  item('user_message', 'user_message', {
    text: 'Add a retry button to the upload screen',
    messageId: 'msg_01HZX8Q3N5V2',
    clientMessageId: 'c0a8e1f2-77b1-4d0e-9a52-3f1d6a8b9c10',
  }),
  item('assistant_message', 'assistant_message', {
    text: 'I added the retry button and wired it to the upload queue.\n\n- It shows only after a failed upload\n- It keeps the file you picked',
    messageId: 'msg_01HZX8R7K2T9',
  }, { text: ['I added the retry button and wired it to the upload queue.', 'It shows only after a failed upload'] }),
  item('reasoning', 'reasoning', {
    text: 'The upload screen keeps its queue in UploadQueue, so the retry button can call requeue() without touching the picker.',
  }),
  item('todo_mixed', 'todo', {
    items: [
      { id: 'todo-1', text: 'Read the upload queue', completed: true, status: 'completed', activeForm: 'Reading the upload queue' },
      { id: 'todo-2', text: 'Add the retry button', completed: false, status: 'in_progress', activeForm: 'Adding the retry button' },
      { id: 'todo-3', text: 'Write the widget test', completed: false, status: 'pending', activeForm: 'Writing the widget test' },
    ],
  }, {
    'items[].completed': ['1 of 3 done'],
    'items[].status': ['In progress'],
  }),
  item('todo_plain', 'todo', {
    items: [
      { text: 'Update the changelog', completed: true },
      { text: 'Tag the release', completed: false },
    ],
  }, { 'items[].completed': ['1 of 2 done'] }),
  item('error', 'error', { message: 'Rate limit reached for claude-opus-5. Try again in 4 minutes.' }),
  item('notification_info', 'notification', { level: 'info', message: 'Switched to the faster model for this step.' }, { level: ['Note'] }),
  item('notification_warning', 'notification', { level: 'warning', message: 'This conversation is close to its context limit.' }, { level: ['Warning'] }),
  item('notification_error', 'notification', { level: 'error', message: 'The agent lost its connection to the language server.' }, { level: ['Problem'] }),
  item('compaction_running', 'compaction', { status: 'loading', trigger: 'auto' }, { status: ['Compacting conversation'], trigger: [] }),
  item('compaction_auto', 'compaction', { status: 'completed', trigger: 'auto', preTokens: 168400 }, {
    status: ['Earlier messages were summarized'],
    trigger: ['automatically'],
    preTokens: ['168k tokens'],
  }),
  item('compaction_manual', 'compaction', { status: 'completed', trigger: 'manual', preTokens: 91250 }, {
    status: ['Earlier messages were summarized'],
    trigger: ['when you asked'],
    preTokens: ['91k tokens'],
  }),
  item('plugin', 'plugin', {
    id: 'plugin-item-7f3a',
    pluginId: 'release-notes',
    kind: 'summary-card',
    version: 2,
    data: { title: 'Release notes draft', sections: 3 },
  }),
];
