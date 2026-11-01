# Perf slices that ship as a Dart patch (2026-11-01)

Branch `perf/dart-2026-11-01`, from `patch/2.1.0-p1`. Audit: `/home/eslam/Storage2/wt/perf-audit-2026-11-01/REPORT.md`.

| Slice | What now works | Proof |
|---|---|---|
| S3 phone budget | Each agent-host start writes a marked "This is a phone" block into the profile's `claude/CLAUDE.md` and thread caps (`MAKEFLAGS=-j2`, `GOMAXPROCS=2`, ...) into `claude/settings.json` `env`. The person's own text and values are kept; Always full speed removes only what we added. | `test/phone_budget_test.dart` |
| S4 quiet networking | OpenCode 2 event streams out of sight retry after 1 s, 2 s, then every 60 to 120 s (jittered), skip a try when Android has no network, and try at once on return. Foreground pace unchanged. Each stream end records `net.stream` (api, kind, bytes, up seconds, hidden) in the Performance trace; Paseo links record bytes and loopback. | `test/reconnect_backoff_test.dart`, `test/api2_sse_test.dart` |
| S1 heat tiers (Dart half) | `HeatTierPolicy` picks normal / slow / hold from thermal status, 30 s forecast, battery (sysfs, unplugged only) and app visibility. `AgentThrottle` renices the Paseo helper's tree and its proot tracer through the agent view of Ubuntu (`renice`), going up at once and down after 2 minutes of calm, re-applied every 5 minutes. Fed by the thermal guard's readings (no second listener). | `test/heat_tier_test.dart` (the script is run against a real `/proc` with a stand-in helper) |
| Always full speed | Switch on Keep running (off by default): no priority lowering, no note, no caps. | `test/full_speed_switch_test.dart` |

## Finding 8 (OPENCODE_DISABLE_MODELS_FETCH): not shipped

Proven that OpenCode 2.2.5 (the pinned build, sha256 verified) honors it: with an HTTPS proxy logger, a default server made 3 CONNECTs to `models.opencode.ai:443` within 15 s of a model request; with the variable set it made none. But it is a start-time switch with no refresh route (`/api/model/refresh` does not exist), so the model list could never update without a new app build. That breaks "the model list refreshes when the picker opens", so it stays off.

## Not in this branch
- Process budget hold on heat tiers: done on `perf/native-2026-11-01`, where both branches meet (`ProcessBudget.decide` asks `HeatTierPolicy`, switched off by Always full speed). See [../perf-native-2026-11-01/README.md](../perf-native-2026-11-01/README.md).
- Hidden-timer diet (S5 Dart): waits for `fix/background-cpu-2026-11-01`.
