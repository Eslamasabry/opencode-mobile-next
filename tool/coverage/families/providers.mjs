// Family "providers": what Paseo sends the model picker: the entries of
// get_providers_snapshot_response (one per agent runtime, with its models,
// thinking options and modes).
import { unwrap, objectFields } from '../paseo_cases_lib.mjs';

export function providerSchema(messages) {
  return { entry: objectFields(unwrap(messages.ProviderSnapshotEntrySchema), []) };
}

const none = (...paths) => Object.fromEntries(paths.map((p) => [p, []]));
const has = (obj, path) => path.split('.').reduce((o, seg) => (o == null ? undefined : seg.endsWith('[]') ? o[seg.slice(0, -2)]?.[0] : o[seg]), obj) !== undefined;
const only = (obj, probes) => Object.fromEntries(Object.entries(probes).filter(([k]) => has(obj, k)));
const T = '2026-10-08T09:00:00.000Z';

const claude = {
  provider: 'claude', status: 'ready', enabled: true, source: 'builtin', fetchedAt: T,
  label: 'Claude Code (work laptop)', description: 'Anthropic\'s coding agent', iconSvg: '<svg xmlns="http://www.w3.org/2000/svg"></svg>', defaultModeId: 'default',
  models: [
    {
      provider: 'claude', id: 'claude-sonnet-5-5', aliases: ['sonnet'], isSelectable: true, label: 'Claude Sonnet 5.5', description: 'Fast and capable', isDefault: true,
      metadata: { family: 'sonnet' }, contextWindowMaxTokens: 200000,
      thinkingOptions: [{ id: 'high', label: 'High', description: 'Think longer', isDefault: true, metadata: { budget: 8000 } }, { id: 'low', label: 'Low' }],
      defaultThinkingOptionId: 'high',
    },
  ],
  modes: [{ id: 'default', label: 'Ask first', description: 'Asks before it changes files', icon: 'shield', colorTier: 'safe' }],
};
const broken = {
  provider: 'codex', status: 'error', enabled: true, source: 'builtin', error: 'Codex CLI exited with status 3 (token refresh failed)',
  fetchedAt: T, label: 'Codex', defaultModeId: null,
};

const probes = {
  ...none('source', 'fetchedAt', 'iconSvg', 'defaultModeId', 'models[].provider', 'models[].aliases[]', 'models[].isSelectable', 'models[].metadata', 'models[].thinkingOptions[].metadata', 'models[].thinkingOptions[].isDefault', 'models[].defaultThinkingOptionId', 'modes[].icon', 'modes[].colorTier', 'models[].isDefault', 'enabled'),
  provider: ['Claude Code'],
  label: ['Claude Code (work laptop)'], status: [], error: [],
};

export const providerCases = [
  {
    id: 'snapshot_ready_and_broken',
    family: 'providers',
    payload: { entries: [claude, broken] },
    parts: [
      { group: 'entry', value: claude, probes: only(claude, { ...probes, 'models[].id': [], 'models[].label': ['Claude Sonnet 5.5'], 'models[].description': ['Fast and capable'], 'models[].contextWindowMaxTokens': ['200K context'], 'models[].thinkingOptions[].id': [], 'models[].thinkingOptions[].label': ['High'], 'models[].thinkingOptions[].description': ['Think longer'], 'modes[].id': [], 'modes[].label': ['Ask first'], 'modes[].description': ['Asks before it changes files'], description: [] }) },
      { group: 'entry', value: broken, probes: only(broken, { ...probes, provider: [], label: [], error: [] }) },
    ],
  },
];
