// Family for AI Team: what a Gas City supervisor sends the team screens. Groups
// follow the pinned contract's schemas for the calls the app makes
// (agents, sessions, beads, convoys, runs, pending, waits, usage, status).
// One case is one whole city, served route by route to the app's real gateway.
import { familyBuilder } from '../oc_cases_lib.mjs';
import { only, none } from './models.mjs';

const f = (kind, values) => ({ kind, ...(values ? { values } : {}) });
const u = (use, g, v, o = {}) => use(g, v, { ...o, probes: only(v, o.probes ?? {}) });
const T0 = '2026-10-09T08:00:00Z';
const T1 = '2026-10-09T09:30:00Z';
const T2 = '2026-10-09T09:55:00Z';
const SKIP = (use, g, v, ...paths) => u(use, g, v, { probes: none(...paths) });

export function gascityTeamFamily(contract) {
  const { schema, kase } = familyBuilder(contract, [
    { name: 'AgentResponse' },
    { name: 'SessionResponse' },
    { name: 'Bead' },
    { name: 'Run' },
    { name: 'RunStatusCounts' },
    { name: 'CityPendingEntry' },
    { name: 'PendingInteraction' },
    { name: 'WaitView' },
    { name: 'UsageBody' },
    { name: 'UsageSessionRecent' },
    { name: 'StatusBody' },
    { name: 'StatusRigDetail' },
    { name: 'StatusAgentDetail' },
    { name: 'HealthOutputBody' },
    { name: 'SupervisorHealthOutputBody' },
    { name: 'CityInfo' },
  ]);
  for (const [g, path] of [['UsageBody', 'recent_by_session'], ['StatusBody', 'agent_details'], ['StatusBody', 'rig_details'], ['StatusBody', 'named_session_details'], ['StatusBody', 'conditional_writes.stores'], ['StatusBody', 'conditional_writes.notices'], ['Bead', 'dependencies'], ['Bead', 'metadata'], ['SessionResponse', 'metadata'], ['SessionResponse', 'options'], ['SupervisorHealthOutputBody', 'startup.phases_completed'], ['CityInfo', 'phases_completed'], ['Bead', 'labels'], ['Bead', 'needs'], ['PendingInteraction', 'options'], ['WaitView', 'dep_ids'], ['WaitView', 'labels'], ['UsageBody', 'partial_reasons'], ['StatusBody', 'partial_errors']]) {
    if (!schema[g][path]) throw new Error(`no ${g}.${path}`);
    schema[g][path] = f('any');
  }
  const cases = [];
  cases.push(kase('city_at_work', (use) => {
    const agentWorker = { name: 'shopfront/gastown.furiosa', state: 'active', running: true, suspended: false, available: true, display_name: 'OpenCode', provider: 'opencode', model: 'gpt-6-sol', pack: 'gastown', pack_derived: true, pool: 'gastown.polecat', rig: 'shopfront', session: { name: 'shopfront--polecat--furiosa', last_activity: T2, attached: true }, active_bead: 'sf-12', activity: 'Editing checkout_form.dart', last_output: 'Wrote three files for the checkout form', context_pct: 63, context_window: 200000, description: 'Builds one task at a time' };
    const agentIdle = { name: 'gastown.mayor', state: 'idle', running: true, suspended: false, available: true, display_name: 'Claude Code', provider: 'claude', model: 'opus-5-5', pack: 'gastown', rig: 'shopfront', session: { name: 'shopfront--mayor', last_activity: T1, attached: false } };
    const agentOff = { name: 'gastown.witness', state: 'stopped', running: false, suspended: true, available: false, unavailable_reason: 'No model is signed in for this agent', display_name: 'Claude Code', provider: 'claude', pack: 'gastown' };
    const mayorSession = { id: 'sf-s2', kind: 'agent', template: 'gastown.mayor', state: 'active', session_name: 'shopfront--mayor', provider: 'claude', display_name: 'Claude Code', model: 'opus-5-5', running: true, rig: 'shopfront', created_at: T0, last_active: T1, attached: false, configured_named_session: true };
    const session = { id: 'sf-s1', kind: 'agent', template: 'shopfront/gastown.polecat', state: 'active', title: 'Checkout form', alias: 'furiosa', provider: 'opencode', display_name: 'OpenCode', model: 'gpt-6-sol', session_name: 'shopfront--polecat--furiosa', work_dir: '/work/shopfront/.gc/worktrees/furiosa', created_at: T0, last_active: T2, last_nudge_delivered_at: T1, attached: true, rig: 'shopfront', pool: 'gastown.polecat', agent_kind: 'polecat', running: true, reason: 'Waiting for the checkout form to compile', active_bead: 'sf-12', activity: 'Editing checkout_form.dart', last_output: 'Wrote three files for the checkout form', context_pct: 63, context_window: 200000, configured_named_session: false, metadata: { branch: 'polecat/sf-12' }, options: { effort: 'high' }, submission_capabilities: { supports_follow_up: true, supports_interrupt_now: false } };
    const task = { id: 'sf-12', title: 'Build the checkout form', description: 'A form with card, address and a Pay button', status: 'in_progress', issue_type: 'task', priority: 1, assignee: 'shopfront/gastown.furiosa', parent: 'sf-c1', ref: 'sf-12', from: 'sf-c1', labels: ['opencode-mobile', 'ui'], is_blocked: false, ephemeral: false, no_history: false, needs: ['sf-11'], dependencies: [{ issue_id: 'sf-12', depends_on_id: 'sf-11', type: 'blocks' }], metadata: { 'gc.session_id': 'sf-s1', branch: 'polecat/sf-12', 'gc.routed_to': 'shopfront/gastown.polecat' }, created_at: T0, updated_at: T2, defer_until: '2026-10-10T08:00:00Z' };
    const blocked = { id: 'sf-13', title: 'Wire the payment provider', status: 'open', issue_type: 'task', priority: 2, is_blocked: true, labels: [], created_at: T0, updated_at: T1, dependencies: [{ issue_id: 'sf-13', depends_on_id: 'sf-12', type: 'blocks' }] };
    const convoy = { id: 'sf-c1', title: 'Checkout flow', status: 'open', issue_type: 'convoy', priority: 2, labels: [], created_at: T0, updated_at: T2, dependencies: [{ issue_id: 'sf-c1', depends_on_id: 'sf-12', type: 'tracks' }, { issue_id: 'sf-c1', depends_on_id: 'sf-13', type: 'tracks' }] };
    const run = { run_id: 'run-formula-1', title: 'Nightly check', formula: 'nightly-check', target: 'shopfront/gastown.refinery', status: 'active', scope: { kind: 'rig', ref: 'shopfront' }, started_at: T0, updated_at: T2, last_error: { code: 'tests_failed', message: 'Two tests failed in checkout_test.dart' } };
    const pending = { kind: 'tool-approval', request_id: 'req-1', session_id: 'sf-s1' };
    const detail = { request_id: 'req-1', kind: 'tool-approval', prompt: 'Allow git push to origin?', options: ['Allow', 'Deny'], metadata: { tool: 'bash' } };
    const pending2 = { kind: 'choice', request_id: 'req-2', session_id: 'sf-s2' };
    const detail2 = { request_id: 'req-2', kind: 'choice', prompt: 'Replace the old checkout or keep both?', options: ['Replace', 'Keep both'], metadata: { rig: 'shopfront' } };
    const wait = { id: 'wait-1', kind: 'nudge', session_id: 'sf-s1', session_name: 'shopfront--polecat--furiosa', state: 'waiting', status: 'pending', note: 'Waiting for the reviewer', dep_ids: ['sf-11'], dep_mode: 'all', labels: ['review'], nudge_id: 'nudge-1', delivery_attempt: '1', registered_epoch: '4', created_at: T1, expires_at: '2026-10-09T12:00:00Z' };
    const totals = { invocations: 7921, compute_facts: 6011, input_tokens: 120000, output_tokens: 8000, cache_read_tokens: 50000, cache_creation_tokens: 4000, wall_seconds: 900, cost_usd_estimate: 1.25, unpriced: 2 };
    const recentTotals = { invocations: 7919, compute_facts: 6007, input_tokens: 61000, output_tokens: 3100, cache_read_tokens: 21000, cache_creation_tokens: 1900, wall_seconds: 410, cost_usd_estimate: 0.7733, unpriced: 4177 };
    const sessionRecent = { session: 'shopfront--polecat--furiosa', session_id: 'sf-s1', input_tokens: 90000, output_tokens: 6000, cache_read_tokens: 40000, cache_creation_tokens: 3000, cost_usd_estimate: 0.9, unpriced: 1 };
    const usage = { available: true, recording: true, source: 'local_estimate', observed_from: T0, updated_at: T2, partial: false, partial_reasons: ['History before Tuesday was trimmed'], recent_window_secs: 3600, recent: recentTotals, today: totals, recent_by_session: [sessionRecent] };
    const rigDetail = { name: 'shopfront', path: '/work/shopfront', suspended: false };
    const agentDetail = { name: 'furiosa', qualified_name: 'shopfront/gastown.furiosa', running: true, suspended: false, draining: false, expanded: true, group_name: 'polecats', scale_label: '1 of 3', scope: 'rig', session_name: 'shopfront--polecat--furiosa' };
    const status = { name: 'phone', path: '/home/user/city', version: '1.4.1', uptime_sec: 7200, suspended: false, agent_count: 2, running: 1, agents: { total: 2, running: 1, suspended: 1, quarantined: 0 }, rig_count: 1, rigs: { total: 1, suspended: 0 }, rig_details: [rigDetail], agent_details: [agentDetail], work: { in_progress: 1, open: 2, ready: 1 }, mail: { total: 4, unread: 1 }, session_counts_detail: { active: 1, suspended: 1 }, partial: false, partial_errors: ['One rig did not answer'], beads_version: '1.0.2', dolt_version: '1.50', beads: { beads_store: 'dolt', native_store_eligible: true, preflight_gate: 'ok', preflight_reason: 'ready' }, conditional_writes: { effective: 'active', mode: 'auto', origin: 'builtin', notices: ['Restart to finish switching stores'], stores: [{ kind: 'bd', capable: true }] }, named_session_details: [{ identity: 'mayor', mode: 'always', status: 'running' }], store_health: { last_gc_at: T0, last_gc_status: 'ok', live_rows: 1200, path: '/home/user/city/.beads', ratio_mb_per_row: 0.01, size_bytes: 12000000, threshold_mb_per_row: 0.5, warning: false } };
    const health = { status: 'ok', city: 'phone', uptime_sec: 7200, version: '1.4.1' };
    const supervisor = { status: 'ok', version: '1.4.1', build_id: 'abc1234', uptime_sec: 86400, cities_total: 1, cities_running: 1, packs_lock_sha256: 'deadbeef', startup: { phase: 'ready', ready: true, phases_completed: ['config', 'cities'] } };
    const cityInfo = { name: 'phone', path: '/home/user/city', running: true, status: 'running', error: 'Last restart was slow', phases_completed: ['bootstrap-config', 'bootstrap-rigs'] };
    return {
      supervisor: u(use, 'SupervisorHealthOutputBody', supervisor),
      cities: [u(use, 'CityInfo', cityInfo)],
      agents: [
        u(use, 'AgentResponse', agentWorker),
        u(use, 'AgentResponse', agentIdle, { probes: { display_name: ['Claude Code'] } }),
        u(use, 'AgentResponse', agentOff, { probes: { state: ['Crashed'], display_name: ['Claude Code'] } }),
      ],
      sessions: [u(use, 'SessionResponse', session), SKIP(use, 'SessionResponse', mayorSession, 'id', 'kind', 'template', 'state', 'session_name', 'provider', 'display_name', 'model', 'running', 'rig', 'created_at', 'last_active', 'attached', 'configured_named_session')],
      beads: [u(use, 'Bead', task, { probes: { dependencies: ['sf-11'] } }), u(use, 'Bead', blocked, { probes: { is_blocked: ['Blocked'], dependencies: ['sf-12'] } })],
      convoys: [u(use, 'Bead', convoy, { probes: { dependencies: ['sf-12', 'sf-13'] } })],
      runs: { runs: [u(use, 'Run', run, { probes: { status: ['Working'] } })], status_counts: u(use, 'RunStatusCounts', { pending: 0, active: 1, waiting: 0, canceling: 0, completed: 3, failed: 1, canceled: 0, skipped: 0 }) },
      pending: [u(use, 'CityPendingEntry', pending), u(use, 'CityPendingEntry', pending2)],
      pendingDetail: { 'sf-s1': u(use, 'PendingInteraction', detail), 'sf-s2': u(use, 'PendingInteraction', detail2) },
      waits: [u(use, 'WaitView', wait)],
      usage: u(use, 'UsageBody', usage, { probes: { 'today.cost_usd_estimate': ['$1.25'], 'today.input_tokens': ['128.0k tokens'], 'today.output_tokens': ['128.0k tokens'], 'today.unpriced': ['has no price yet'] } }),
      status: u(use, 'StatusBody', status, { probes: { rig_details: ['shopfront'] } }),
      _rigDetail: u(use, 'StatusRigDetail', rigDetail),
      _agentDetail: u(use, 'StatusAgentDetail', agentDetail),
      _sessionRecent: u(use, 'UsageSessionRecent', sessionRecent),
      health: u(use, 'HealthOutputBody', health),
    };
  }, { kind: 'city' }));
  cases.push(kase('city_quiet', (use) => {
    const usage = { available: true, recording: false, source: 'local_estimate', observed_from: T0, updated_at: T2, partial: true, partial_reasons: ['History before Tuesday was trimmed'], recent_window_secs: 3600, recent: { input_tokens: 10, output_tokens: 5, cost_usd_estimate: 0.1, unpriced: 0 }, today: { input_tokens: 1000, output_tokens: 500, cost_usd_estimate: 0.2, unpriced: 0 }, recent_by_session: [] };
    return {
      supervisor: { status: 'ok', version: '1.4.1' },
      cities: [{ name: 'phone', running: true }],
      agents: [], sessions: [], beads: [], convoys: [], pending: [], pendingDetail: {}, waits: [],
      runs: { runs: [], status_counts: { pending: 0, active: 0, waiting: 0, canceling: 0, completed: 0, failed: 0, canceled: 0, skipped: 0 } },
      usage: u(use, 'UsageBody', usage, { probes: { ...none('available', 'recent_window_secs', 'recent_by_session', 'observed_from', 'updated_at', 'source', 'recent.input_tokens', 'recent.output_tokens', 'recent.cost_usd_estimate', 'recent.unpriced', 'partial_reasons', 'today.input_tokens', 'today.output_tokens'), recording: ['isn’t counting new use'], partial: ['Part of today’s history is missing'], 'today.cost_usd_estimate': ['$0.20'] } }),
      status: { name: 'phone', version: '1.4.1', rig_details: [{ name: 'shopfront', path: '/work/shopfront' }], agents: { total: 0, running: 0 }, work: { in_progress: 0, open: 0, ready: 0 } },
      health: { status: 'ok', city: 'phone', version: '1.4.1', uptime_sec: 60 },
    };
  }, { kind: 'city' }));
  return { schema, cases };
}
