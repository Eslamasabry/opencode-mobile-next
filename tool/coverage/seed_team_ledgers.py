#!/usr/bin/env python3
"""Writes the AI Team ledger (one decision per field). Run after
tool/coverage/oc_samples.mjs; fields not listed here must be on screen."""
import json

OUT = 'test/fixtures/coverage'
s = json.load(open(f'{OUT}/gascity_team_samples.json'))
keys = [f"{g}.{f['path']}" for g, fs in s['schema'].items() for f in fs]

STATE = 'ignored: folded into the one state word the page shows (Working, Idle, Waiting, Stopped), never a flag of its own'
TERMINAL = 'ignored: whether a terminal is attached on the computer; nothing a person can act on from the phone'
HOST = "ignored: the host's own maintenance detail (storage, versions, start-up); the page shows what the team is doing, not how the host runs"
HOSTRUN = "ignored: how long or how the host has been running; the page says whether the host answers, and which version"
SUMMARY = "ignored: the page shows today's cost and tokens; the host's wider window and breakdown are not drawn"
WAIT = "ignored: only which worker is parked decides the Blocked word; why it waits is the host's own note and is not drawn"
CITYDECIDE = "ignored: decides only whether the team can be read; when it cannot, the page says the host is not answering"
SESSIONFLAG = "ignored: how the host runs this worker's session; the page shows the worker, its state and its work"
BOOK = "ignored: the host's own bookkeeping flag; nothing a person reads or acts on"
COUNTS = "ignored: the page counts the team's work from the lists themselves, not from the host's totals"

D = {k: 'shown' for k in keys}
def ign(reason, *ks):
    for k in ks:
        assert k in D, k
        D[k] = reason

ign(STATE, 'AgentResponse.available', 'AgentResponse.running', 'AgentResponse.suspended', 'StatusAgentDetail.running', 'StatusAgentDetail.suspended', 'StatusAgentDetail.draining', 'StatusAgentDetail.expanded', 'StatusRigDetail.suspended', 'StatusBody.suspended')
ign('ignored: how the host chose the agent\'s pack; nothing to act on', 'AgentResponse.pack_derived')
ign(TERMINAL, 'AgentResponse.session.attached', 'SessionResponse.attached')
ign(BOOK, 'Bead.ephemeral', 'Bead.no_history')
ign(CITYDECIDE, 'CityInfo.running', 'CityInfo.status', 'CityInfo.error', 'CityInfo.path', 'CityInfo.phases_completed')
ign(SESSIONFLAG, 'SessionResponse.configured_named_session', 'SessionResponse.running', 'SessionResponse.submission_capabilities.supports_follow_up', 'SessionResponse.submission_capabilities.supports_interrupt_now', 'SessionResponse.options', 'SessionResponse.reason', 'SessionResponse.title')
ign('ignored: the failure message says what went wrong; its machine code is technical', 'Run.last_error.code')
ign(WAIT, 'WaitView.expires_at', 'WaitView.id', 'WaitView.kind', 'WaitView.note', 'WaitView.nudge_id', 'WaitView.state', 'WaitView.status')
ign('ignored: the page lists the team\'s workers from the agents list; the host\'s per-worker status rows add nothing', 'StatusBody.agent_details', 'StatusAgentDetail.group_name', 'StatusAgentDetail.scale_label', 'StatusBody.named_session_details')
ign(HOST, 'StatusBody.beads.beads_store', 'StatusBody.beads.preflight_reason', 'StatusBody.beads.native_store_eligible', 'StatusBody.beads_version', 'StatusBody.dolt_version', 'StatusBody.conditional_writes.effective', 'StatusBody.conditional_writes.mode', 'StatusBody.conditional_writes.origin', 'StatusBody.conditional_writes.notices', 'StatusBody.conditional_writes.stores', 'StatusBody.store_health.live_rows', 'StatusBody.store_health.path', 'StatusBody.store_health.ratio_mb_per_row', 'StatusBody.store_health.size_bytes', 'StatusBody.store_health.threshold_mb_per_row', 'StatusBody.store_health.warning', 'StatusBody.path', 'StatusBody.partial_errors', 'StatusBody.partial', 'SupervisorHealthOutputBody.build_id', 'SupervisorHealthOutputBody.packs_lock_sha256', 'SupervisorHealthOutputBody.startup.phase', 'SupervisorHealthOutputBody.startup.phases_completed', 'SupervisorHealthOutputBody.startup.ready')
ign(HOSTRUN, 'StatusBody.uptime_sec', 'HealthOutputBody.uptime_sec', 'SupervisorHealthOutputBody.uptime_sec')
ign(SUMMARY, 'UsageBody.recent.input_tokens', 'UsageBody.recent.output_tokens', 'UsageBody.recent.cache_read_tokens', 'UsageBody.recent.cache_creation_tokens', 'UsageBody.recent.wall_seconds', 'UsageBody.recent_window_secs', 'UsageBody.today.cache_creation_tokens', 'UsageBody.today.cache_read_tokens', 'UsageBody.today.wall_seconds', 'UsageBody.recent_by_session', 'UsageSessionRecent.input_tokens', 'UsageSessionRecent.output_tokens', 'UsageSessionRecent.cache_read_tokens', 'UsageSessionRecent.cache_creation_tokens', 'UsageSessionRecent.cost_usd_estimate', 'UsageBody.partial_reasons', 'UsageBody.available', 'UsageBody.source')
for k in list(D):
    if k.startswith('UsageBody.recent.') and D[k] == 'shown':
        D[k] = SUMMARY
for k in ('UsageBody.today.compute_facts', 'UsageBody.today.invocations', 'UsageBody.recent.compute_facts', 'UsageBody.recent.invocations', 'UsageBody.recent.cost_usd_estimate', 'UsageBody.recent.unpriced'):
    if k in D and D[k] == 'shown':
        D[k] = SUMMARY
json.dump(D, open(f'{OUT}/gascity_team_ledger.json', 'w'), indent=2, sort_keys=True)
print(sum(1 for v in D.values() if v == 'shown'), 'shown,', sum(1 for v in D.values() if v != 'shown'), 'ignored')
