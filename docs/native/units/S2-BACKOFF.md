# Unit S2-BACKOFF: one `BackoffPolicy` for hidden reconnects

Date: 2026-11-01. Branch: `native/backoff-2026-11-01`, made from `claude/backoff-hidden-reconnects-taw6yw` at
`ba8f649e`.
Write set: the `BackoffPolicy` files in `native/core/net/**` (with the `:core:net` owner), the PR-01 and PR-04 scripts
in `native/tool/perf/**`, and this report.

Inputs:
- plan 15, row S2-BACKOFF;
- plan 13: amendment A2, §12.1 (reconnect schedule), §3.5 (PR-01, PR-04), §3.7 (run rules), WP-13.6, and budgets
  PB-40, PB-51, PB-52 and PB-53;
- plan 04: N4-D6, N4-D8, §6.2 and §6.4;
- `docs/native/units/S1-NET.md`, `INTEGRATE-W1.md` and `INTEGRATE-W2.md`. Only the flat `Failure` contract is used.
  This unit adds no failure values.

**Result:** every hidden reconnect now waits for a value from one `BackoffPolicy.delayMs(attempt, random,
visibility)`. PB-51, PB-52 and PB-53 are green in `:core:net` common tests. PB-40 is green for the part that
`:core:net` controls: no timer is armed while the app is hidden and idle. All 70 `:core:net:desktopTest` tests and all 56 `testAndroidHostTest` tests pass, and the
app still builds.
The PR-01 and PR-04 scripts exist, and their verdict logic is covered by 15 self-tests.

**Shipping state:** implemented, verified in tests, committed and pushed. Nothing is wired into the app yet,
because no module depends on `:core:net`. It was **not run on the emulator**: this cloud container has no emulator,
and the bench APK and its `BenchSeedReceiver` (WP-13.0) do not exist yet. So the roadmap's "once on the emulator"
is still open; see Open problems 1. There is no PR, no CI run and no release.

---

## 1. What was built

### 1.1 `:core:net` (`native/core/net/src/commonMain/.../stream/`)

| File | Change |
|---|---|
| `BackoffPolicy.kt` | The A2 shape from plan 13 §12.1. Every field from 04 is kept. Two fields are new: `hiddenAfterAttempt = 2` and `hiddenRangeMs = 60_000..120_000`. There are two functions. `delayMs(attempt, random)` is 04's formula, unchanged, and serves the foreground. `delayMs(attempt, random, visibility): Long?` serves all three visibilities: Foreground uses the formula; HiddenWatching uses the formula for attempts up to `hiddenAfterAttempt` and then a uniform draw from `hiddenRangeMs`; HiddenIdle returns `null`. The presets use 04's positional values: `OpenCode2 = (500, 16_000, true, null, 30_000, 6, 45_000)` and `GasCity = (500, 30_000, false, 0.5..1.5, 30_000, MAX, 90_000)`. `init` rejects a hidden range that is empty or starts below 60 s (PB-51). The old `HiddenPace` class and the `delayMs(attempt, hidden: Boolean, random)` overload are removed, so the schedule lives in one place. |
| `EventStreamLoop.kt` | The input `hidden: StateFlow<Boolean>` is replaced by `visibility: StateFlow<Visibility>`, which uses `:core:common`'s Pacer enum. (1) Each wait is `policy.delayMs(attempt, random, visibility)`. (2) HiddenIdle closes a live stream (a new interrupt, `End.Idle`) and cancels a pending wait (`Wake.Idle`). The loop then reports `StreamStatus.Suspended` and suspends on the visibility flow alone. It arms no timer and dials nothing; a `retryNow` sent while suspended is answered by the dial that follows the return. (3) A change to Foreground ends a hidden wait (`Wake.Visible`) and dials at once as attempt 1: the count is reset, so if that dial fails, the next wait is 1 s. (4) Waiting offline also ends on HiddenIdle, which then reports `Suspended`. The network-change rule is unchanged: a flap on the same default network never shortens a hidden wait, and a new default network id does. |

### 1.2 `native/tool/perf/` (new folder)

| File | What it does |
|---|---|
| `conn_log_proxy.py` | A counting TCP proxy that sits between the app and the fixture server. For every connection it writes a JSON line with accept and close times, plus the method and path of the first request line. It never logs headers (Basic credentials), query strings or bodies. While the file `server-down` exists, it cuts every live connection, and it logs and then closes each new one. That plays "fixture server stopped" (PR-04) without hiding the attempts. |
| `hidden_checks.py` | Turns the proxy log, the run's marks and the device readings into `PR-01.json` / `PR-04.json` (result schema of 13 §3.7). Exit codes: 0 pass, 1 fail, 2 for a missing input or mark. **PR-01:** PB-53 (no event stream still open 30 s after going hidden; no request at all in the idle window) and PB-40 (app CPU ≤ 1 s per hour from `/proc/<pid>/stat`; 0 fired wake-up alarms from `dumpsys alarm`). **PR-04:** PB-52 (no request in airplane mode; 1–2 stream attempts in the first minute back) and PB-51 (with the server down while hidden: first retry ≈ 1 s, second ≈ 2 s later, every later gap 60–120 s, at most one attempt per 60 s after the second, and a retry ≤ 3 s after the return to the foreground). Default tolerance is 1.5 s. |
| `hidden_lib.sh` | Shared steps: emulator-only serial, the emulator lock, the restore preamble with an exit trap, the proxy started and stopped by exact PID, the seed (host id and port only) and open, waiting for the stream, marks, CPU ticks and wake-ups. If WP-13.0's `restore_device.sh` is missing, it falls back to a minimal restore of what these scripts change (airplane mode, deviceidle, screen). |
| `hidden_run.sh` | PR-01, `--scenario idle [--minutes 30]`: seed, open, connect, Home, screen off, 30 min, `dumpsys deviceidle force-idle`, 30 min, then the verdicts. It also keeps the raw dumps (`meminfo`, `alarm`, `batterystats`) for the owners of PB-23, 42, 43 and 44. Other scenarios (PR-02, PR-03, PR-03b) exit 2, because they belong to other units. |
| `offline_run.sh` | PR-04: seed with `stayConnected`, connect, Home, screen off, airplane mode for 10 min, back for 1 min, server down for 10 min (hidden), foreground while still down, server up, then the verdicts. |
| `test/test_hidden_checks.py` | 15 self-tests. Each budget passes on a clean synthetic run and fails on a planted violation, including the audit #6 schedule of every 16 s while hidden. Two proxy tests run on loopback: credentials and queries are never logged, and the down flag cuts a live stream and logs each refused attempt. |
| `linux_ready_snapshot.md` | The `linux-ready` snapshot recipe for S3 (WP-13.6): what the snapshot holds, the steps, and when to refresh it. |

## 2. Rows covered

These budgets are plan 13 ids. They are not in `catalog/behaviors.jsonl`, which has no PB rows. Each test is named
after its row.

| Row | Test (common, `:core:net:desktopTest`) |
|---|---|
| PB-51 | `BackoffPolicyTest`: `PB-51 hidden retries 1 s 2 s then 60-120 s` (2,000 draws, spread across the range), `PB-51 foreground visibility is 04's formula unchanged`, `PB-51 Gas City hidden pace…`, `PB-51 a policy refuses a hidden range that would wake the radio more than once a minute`. `EventStreamLoopTest`: `PB-51 hidden retries 1 s 2 s then 60-120 s` (one virtual hour), `PB-51 at most one try per 60 s after the second, also once a hidden stream drops`, `PB-51 return to foreground retries at once` (and the next wait is 1 s), `PB-51 a hidden wait is not shortened by a flap on the same network, only by a new default network`. |
| PB-52 | `EventStreamLoopTest`: `PB-52 no dial while offline, hidden or not` (10 min, Foreground and HiddenWatching, then one dial at once), `PB-52 losing the network while hidden spends no tries`. |
| PB-53 | `BackoffPolicyTest`: `PB-53 hidden idle dials nothing`. `EventStreamLoopTest`: `PB-53 hidden idle opens no stream and arms no timer`, `PB-53 going hidden idle closes a live stream and cancels a pending retry` (0 open sessions), `PB-53 hidden idle to watching dials at once with the count reset`, `PB-53 a retryNow while hidden idle dials nothing until the person returns`. |
| PB-40 | `PB-53 hidden idle opens no stream and arms no timer` checks that `advanceUntilIdle()` leaves virtual time unmoved, so the loop schedules no wake-up. CPU per hour and fired alarms are device numbers; `hidden_run.sh` measures them (PR-01). |
| N4-D6 (kept) | `N4-D6 OpenCode 2 policy is 1 2 4 8 16 16 s with no jitter`, `N4-D6 the presets keep 04's numbers`, `N4-D6 Gas City policy is jittered and capped at 30 s`. These moved from `HttpTest` to `BackoffPolicyTest`. All earlier `EventStreamLoopTest` rows still pass unchanged (B-PROTO-033, 132, 133, 146, N4-D10). |

## 3. Commands and results

Run in this container, on JDK 21 (`/usr/lib/jvm/java-21-openjdk-amd64`), with `ANDROID_HOME=/opt/android-sdk`
(`platform-tools`, `platforms;android-37.1`, `build-tools;37.0.0`).

| Command | Result |
|---|---|
| `./gradlew :core:net:desktopTest` | **70 tests, 0 failures** (BackoffPolicyTest 8, EventStreamLoopTest 29, HttpTest 14, others unchanged). The first run had 2 failures, and both were mistakes in the tests: two harnesses shared one virtual clock and the tests assumed absolute times. Fixed with offsets. |
| `./gradlew :core:net:testAndroidHostTest` | **56 tests, 0 failures**. This is the same common suite on the Android host; the 14 tests in `NoResendTest` and `TransportTrustTest` exist only on desktop. |
| `./gradlew :androidApp:assembleDebug` | **BUILD SUCCESSFUL**: `androidApp/build/outputs/apk/preview/debug/androidApp-preview-debug.apk` (see §3.1) |
| `python3 -I native/tool/perf/test/test_hidden_checks.py` (with `-W error::ResourceWarning`) | **15 tests OK** |
| `bash -n` on `hidden_lib.sh`, `hidden_run.sh`, `offline_run.sh` | OK (`shellcheck` is not installed here) |
| `native/tool/check_write_set.sh --globs 'native/core/net/** native/tool/perf/**' --also docs/native/units/S2-BACKOFF.md` | OK |
| `native/tool/check_commits.sh <base>` | OK |
| `native/tool/scan_push.sh <base>` | clean (no private addresses, e-mails or secrets) |

Tests-first note: the new tests were written before the code. They could not be run red, because Gradle could not
resolve dependencies yet (§5, item 6). The first run was already against the new code.

### 3.1 App build

`./gradlew :core:net:desktopTest :core:net:testAndroidHostTest :androidApp:assembleDebug` ran once on the final tree
and exited 0. No module depends on `:core:net` yet, so the app build shows only that the branch still compiles. It
does not exercise the new loop on a device.

## 4. Deviations

1. **Visibility has three values, not four.** A2 names HiddenLive and HiddenReplyWatch. `:core:common`'s Pacer
   `Visibility` (`Foreground`, `HiddenWatching`, `HiddenIdle`) merges them, and they get the same schedule. That
   enum was used because `:core:common` belongs to another owner.
2. **The Foreground return resets the count.** A2 says the return dial counts "as attempt 1", so a failed return dial
   waits 1 s and does not continue the hidden count. Before this unit, the loop kept the hidden attempt number.
3. **`@RetryLoop` is not applied.** The annotation and the K-P1 rule do not exist yet (`:core:common` / `:quality`).
   `waitBeforeRetry` is documented as the one retry wait, and it waits only for `delayMs` values.
4. **HiddenIdle is enforced in the loop too.** Plan 04 §6.4 has `:engine:connection` cancel the streams for
   BackgroundIdle. The loop now also refuses to dial and closes a live stream on HiddenIdle, so PB-53 holds even when
   the cancel is late. This adds safety and does not change the contract.
5. **`HiddenPace` was removed.** Its only users were inside `:core:net`; `core/domain/Factory.kt` mentions the loop
   only in a comment.

## 5. Catalog requests and owner requests

- **WP-13.0 (perf lead) / `:androidApp` owner:** `BenchSeedReceiver` needs a boolean extra `stayConnected`. With it,
  PR-04's app is BackgroundWatching while hidden, without a running turn. The scripts also assume a receiver class
  name. They default to `<bench pkg>/io.github.eslamasabry.oc.app.bench.BenchSeedReceiver`, and
  `OC_BENCH_SEED_RECEIVER` overrides it. Please confirm the name.
- **WP-13.0:** `tool/perf/restore_device.sh`. The scripts use it once it exists and fall back to a minimal restore
  until then.
- **`:core:common` owner:** add the `@RetryLoop` annotation (plan 13 §11.2 rule 1), so `EventStreamLoop.waitBeforeRetry`
  can carry it.
- **Graph owner (`:engine:connection` / `:core:domain`):** bind `EventStreamLoop(visibility = …)` to the Pacer's
  `StateFlow<Visibility>` that is built from `LinkDemand`. Nothing constructs the loop yet.
- No library or catalog changes are needed.
- Local only (not committed): Maven Central answered 429 to this container. A user-level Gradle init script,
  `~/.gradle/init.d/mirror.gradle.kts`, puts Google's Maven Central mirror first. The repository's settings are
  unchanged.

## 6. Open problems

1. **Emulator proof not run.** The scripts are written and their logic is tested, but they have not run against a
   device. They need the bench APK with `BenchSeedReceiver` (WP-13.0), FX-OC2-PERF on the upstream port, and the
   PC emulator. The first T3/T4 run should run `offline_run.sh --detach` and `hidden_run.sh --scenario idle --detach`,
   then check the `dumpsys alarm` wake-up parsing on API 37 (`hr_wakeups`).
2. **PB-52 on a real device sees only one side.** In airplane mode a dial fails on the device, so the proxy cannot
   see it. PR-04 proves PB-52 by its effect: no request reaches the fixture in airplane mode, and the first minute
   back has 1–2 stream attempts, which means the backoff count was not spent offline. An attempt counter on the app
   side (a `PerfTrace` mark per dial) would count it directly. That needs the `:core:net` owner and PerfTrace.
3. **Host-class gating differs between the code and plan 04.** `NetworkGate` exempts only Loopback. Plan 04 §6.4 also
   exempts PrivateLan and LinkLocal. PB-52 says "non-loopback", which matches the code. The bench seed points at
   the emulator's host alias, which is a private-LAN address, so if the code ever followed 04 §6.4, PR-04's airplane
   part would stop proving anything. The `:core:net` owner and the plan 04 owner need to choose one rule.
4. **`hidden_run.sh` holds PR-02, PR-03 and PR-03b as stubs** that exit 2. Those scenarios belong to their own units.
