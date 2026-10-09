// Family "lists": what Paseo sends the chats list: one page of
// fetch_agents_response (an agent snapshot and its project placement per
// entry, and the page info). The request cards of pendingPermissions are the
// permissions family's; here they only make a row say "Needs you".
import { unwrap, objectFields } from '../paseo_cases_lib.mjs';

export function listSchema(messages) {
  return {
    agent: objectFields(unwrap(messages.AgentSnapshotPayloadSchema), []),
    project: objectFields(unwrap(messages.ProjectPlacementPayloadSchema), []),
    page: { nextCursor: { kind: 'string' }, prevCursor: { kind: 'string' }, hasMore: { kind: 'boolean' } },
  };
}

const sameAs = 'ignored: the same request card is drawn by the conversation and the list; its fields are decided in paseo_permissions_ledger.json';
export const listExcluded = Object.fromEntries([
  'pendingPermissions[].id', 'pendingPermissions[].provider', 'pendingPermissions[].name', 'pendingPermissions[].kind',
  'pendingPermissions[].title', 'pendingPermissions[].description', 'pendingPermissions[].input', 'pendingPermissions[].detail',
  'pendingPermissions[].suggestions[]', 'pendingPermissions[].actions[].id', 'pendingPermissions[].actions[].label',
  'pendingPermissions[].actions[].behavior', 'pendingPermissions[].actions[].variant', 'pendingPermissions[].actions[].intent',
  'pendingPermissions[].metadata',
].map((p) => [`agent.${p}`, sameAs]));

const none = (...paths) => Object.fromEntries(paths.map((p) => [p, []]));
const T = '2026-09-30T09:00:00.000Z';
const caps = { supportsStreaming: true, supportsSessionPersistence: true, supportsSessionListing: true, supportsDynamicModes: true, supportsMcpServers: true, supportsReasoningStream: true, supportsToolInvocations: true, supportsRewindConversation: true, supportsRewindFiles: false, supportsRewindBoth: false };
const agent = (n, extra = {}) => Object.fromEntries(Object.entries({
  id: `agt_lists${n}`,
  provider: 'claude',
  cwd: '/root/projects/shopfront',
  workspaceId: `wsp_lists${n}`,
  model: 'claude-sonnet-5-5',
  features: [{ type: 'toggle', id: 'fast_mode', label: 'Fast mode', value: false }],
  thinkingOptionId: 'high',
  effectiveThinkingOptionId: 'high',
  createdAt: T,
  updatedAt: T,
  lastUserMessageAt: T,
  status: 'running',
  activeTurn: { turnId: `turn_lists${n}`, startedAt: T },
  capabilities: caps,
  currentModeId: 'default',
  availableModes: [{ id: 'default', label: 'Ask first', description: 'Asks before it changes files', icon: 'shield', colorTier: 'safe' }],
  pendingPermissions: [],
  persistence: { provider: 'claude', sessionId: `claude_sess_${n}`, nativeHandle: `native_${n}`, metadata: { resumeFrom: 'transcript' } },
  runtimeInfo: { provider: 'claude', sessionId: `claude_sess_${n}`, model: 'claude-sonnet-5-5', thinkingOptionId: 'high', modeId: 'default', extra: { effort: 'high' } },
  lastUsage: { inputTokens: 15200, cachedInputTokens: 8800, outputTokens: 2310, totalCostUsd: 0.4271, contextWindowMaxTokens: 200000, contextWindowUsedTokens: 61400 },
  title: 'Add a retry button to uploads',
  labels: { source: 'mobile' },
  ...extra,
}).filter(([, v]) => v !== null && v !== undefined));
const place = (n) => ({ projectKey: `shopfront-${n}`, projectName: 'Shopfront', workspaceName: 'main', checkout: { cwd: '/root/projects/shopfront', isGit: true, currentBranch: 'main' } });

const has = (obj, path) => path.split('.').reduce((o, seg) => (o == null ? undefined : seg.endsWith('[]') ? o[seg.slice(0, -2)]?.[0] : o[seg]), obj) !== undefined;
const only = (obj, probes) => Object.fromEntries(Object.entries(probes).filter(([k]) => has(obj, k)));
const agentProbes = {
  ...none('id', 'workspaceId', 'model', 'thinkingOptionId', 'effectiveThinkingOptionId', 'features[]', 'activeTurn.turnId', 'activeTurn.startedAt', 'currentModeId', 'availableModes[].id', 'availableModes[].label', 'availableModes[].description', 'availableModes[].icon', 'availableModes[].colorTier', 'persistence.provider', 'persistence.sessionId', 'persistence.nativeHandle', 'persistence.metadata', 'runtimeInfo.provider', 'runtimeInfo.sessionId', 'runtimeInfo.model', 'runtimeInfo.thinkingOptionId', 'runtimeInfo.modeId', 'runtimeInfo.extra', 'labels', 'attentionTimestamp', 'lastUserMessageAt', 'createdAt'),
  ...none('capabilities.supportsStreaming', 'capabilities.supportsSessionPersistence', 'capabilities.supportsSessionListing', 'capabilities.supportsDynamicModes', 'capabilities.supportsMcpServers', 'capabilities.supportsReasoningStream', 'capabilities.supportsToolInvocations', 'capabilities.supportsRewindConversation', 'capabilities.supportsRewindFiles', 'capabilities.supportsRewindBoth'),
  ...none('lastUsage.inputTokens', 'lastUsage.cachedInputTokens', 'lastUsage.outputTokens', 'lastUsage.contextWindowMaxTokens', 'lastUsage.contextWindowUsedTokens'),
  'lastUsage.totalCostUsd': ['$0.4271'],
  provider: ['Claude Code'],
  cwd: ['shopfront'],
  updatedAt: ['1h ago'],
  status: ['Running'],
  title: ['Add a retry button to uploads'],
  lastError: [],
  requiresAttention: [],
  attentionReason: [],
  archivedAt: [],
  providerUnavailable: [],
};

const entry = (a, n, over = {}) => ({ agent: a, project: place(n), probes: over });
const fullCase = (id, entries, page, meta = {}) => ({
  id,
  family: 'lists',
  payload: { entries: entries.map((e) => ({ agent: e.agent, project: e.project })), pageInfo: page },
  parts: [
    ...entries.map((e) => ({ group: 'agent', value: e.agent, probes: only(e.agent, { ...agentProbes, title: [e.agent.title], ...e.probes }) })),
    ...entries.map((e) => ({ group: 'project', value: e.project, probes: { projectKey: [], projectName: [], workspaceName: [], checkout: [] } })),
    { group: 'page', value: page, probes: { nextCursor: [], prevCursor: [], hasMore: [] } },
  ],
  ...meta,
});

export const listCases = [
  // Two agents, so every row names its agent; the first is working, the
  // second ended in an error.
  fullCase('page_two_agents', [
    // Both rows carry a tag (Running, Failed) where the age would be.
    entry(agent('01'), '01', { updatedAt: [] }),
    entry(agent('02', {
      provider: 'codex', title: 'Fix the flaky checkout test', status: 'error', lastError: 'Codex stopped: the provider returned an overload error',
      activeTurn: null, requiresAttention: true, attentionReason: 'error', attentionTimestamp: T,
      persistence: { provider: 'codex', sessionId: 'codex_sess_02' }, runtimeInfo: { provider: 'codex', sessionId: 'codex_sess_02' },
    }), '02', { provider: ['Codex'], status: ['Failed'], updatedAt: [], 'lastUsage.totalCostUsd': [] }),
  ], { nextCursor: 'cur_agents_p2', prevCursor: 'cur_agents_p0', hasMore: true }),
  // Finished and waiting to be read, and one the person archived on the computer.
  fullCase('agent_idle_finished', [
    entry(agent('03', { title: 'Explain the receipt layout', status: 'idle', activeTurn: null, requiresAttention: true, attentionReason: 'finished', attentionTimestamp: T }), '03', { status: [], provider: [] }),
    entry(agent('04', { title: 'Old refactor nobody needs', status: 'closed', activeTurn: null, archivedAt: T, providerUnavailable: true }), '04', { status: [], archivedAt: ['Old refactor nobody needs'], title: [], provider: [], cwd: [], updatedAt: [], 'lastUsage.totalCostUsd': [] }),
  ], { nextCursor: null, prevCursor: null, hasMore: false }),
];
