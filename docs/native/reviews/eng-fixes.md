# ENG review fixes

Verified against this checkout's starting code, `066a00be`, on 2026-11-01. The numbered findings come from
`docs/native/reviews/eng.md` in docs commit `3bb7632e`; that report is newer than this fix branch's base. Binding
contracts: plan 01/03 and `INTEGRATE-W1.md` / `INTEGRATE-W2.md`. Seven findings fixed, one already fixed, none rejected.

| Finding | Disposition and evidence |
|---|---|
| 1 — BLOCKER: obsolete core contracts | **Already fixed** by INTEGRATE-W2. Production and tests use the flat `Failure` leaves and `core.common.dart.DartJson`. The only old-family text remaining is a historical test comment. Current desktop tests and Android compilation pass. No obsolete types were restored. |
| 2 — MAJOR: deletion misses a credential saved concurrently | **Fixed** — `B-STATE-063 eng-2 deletion drains a passwordless profile save before enumerating secrets`. Close admission, drain the store mutex before any sweep, and enumerate secret names inside the row-removal mutex. |
| 3 — MAJOR: cancellation/process interruption separates rows from secrets | **Fixed** — `B-STATE-098 eng-3 cancellation finishes an admitted profile save`, `B-STATE-098 eng-3 cancellation completes the admitted row and secret removal`, and the restart/compensation tests listed below. Admitted transactions finish noncancellably; durable journals repair interrupted saves and deletions before loading profiles. Prior secret values stay in a scoped SecretStore staging alias, never in prefs journals. Reset saved sign-ins serializes with saves and invalidates journals and staging aliases. |
| 4 — MAJOR: embedded deletion overwrites another profile's update | **Fixed** — `B-STATE-063 eng-4 embedded removal preserves another profile update across writer lanes`, `B-STATE-063 eng-4 keeping removed prompts preserves concurrent saved prompts`, and `B-STATE-063 eng-4 a changed confirmed queue refuses deletion`. Multi-key conditional commits protect the complete transformation; conflicts recompute against current data, with eight attempts maximum. A typed confirmation covers the target's queued records; changed or missing confirmation refuses removal. |
| 5 — MAJOR: mobile consent stays granted after revocation fails | **Fixed** — `B-SEC-013 eng-5 revocation withdraws the grant before a refused save`. Revoke and advance the observer revision before waiting for the writer or disk. Reads and download dispatch remain private on refusal. |
| 6 — MAJOR: interrupted migration grants sharing | **Fixed** — `B-SEC-142 eng-6 cancelled migration stays private after reconstruction` and `B-SEC-142 eng-6 refused completion stays private after reconstruction`. Migrated grants require durable journal completion on every read, including in a new store instance. |
| 7 — MAJOR: failed compensation authorizes a replacement owner | **Fixed** — `B-STATE-124 eng-7 stale grant with refused compensation stays private after restart` and `B-STATE-124 eng-7 a durable grant belongs only to its captured owner`. Opt-ins first persist as pending; reads reject pending grants and mismatched owner digests. `ReadStateConsentStore` now requires the existing `SettingsOwners` identity port. |
| 8 — MAJOR: crash startup races single-flight/reset | **Fixed** — `B-SEC-045 eng-8 startup claims ownership before an immediate source reenters` and `B-SEC-045 eng-8 reset during suspended consent read cannot erase evidence`. Install lazy work before starting it, publish through one atomic lifecycle state, and fence both sides of suspended file operations. |

Additional finding-3 tests in `EngTransactionTest`:

- `B-STATE-098 eng-3 restart repairs a row removed before secret deletion`.
- `B-STATE-098 eng-3 restart rolls back an interrupted save without losing the old sign-in`.
- `B-STATE-098 eng-3 refused deletion completion keeps the row across restart`.
- `B-STATE-098 eng-3 sign-in reset cannot restore an interrupted save backup`.

The regressions for every applicable finding were run failing before their fixes. The initial runs reproduced four
consent failures, five profile-transaction failures, two embedded-edit failures and two crash-startup failures. The
reset/recovery and refused-journal-completion cases were also run failing before their compensations were added.
The final suite includes additional owner-binding and saved-prompt concurrency coverage.

Only unit modules changed, plus two necessary shared files: `core:data`'s `KeyValueStore` and `PrefsKeyValueStore`.
The existing one-key CAS cannot atomically compare and update both the queue and saved prompts across different
writer lanes; the added `compareAndCommit` uses the same file-wide lock, failure vocabulary and publication rules.
Unsupported implementations refuse the operation. Profile journal keys, staging-secret aliases and consent-owner
keys are registered with the existing profile-scope registries. Existing deletion fixtures now supply explicit queue
confirmations; the transient-journal fixture is covered by recovery tests rather than seeded as arbitrary JSON.

Final local validation: **349 tests, zero failures, zero errors, zero skipped**. Android compilation also passed for
`:engine:settings`, `:engine:profiles` and `:engine:diagnostics`; `:core:data` compiled as part of its host tests.

| Module/task | Tests |
|---|---:|
| `:core:data:desktopTest` | 52 |
| `:core:data:testAndroidHostTest` | 46 |
| `:engine:settings:desktopTest` | 120 |
| `:engine:profiles:desktopTest` | 59 |
| `:engine:diagnostics:desktopTest` | 72 |

The final command, from `native/`, used JDK 21, the requested SDK, `-Xmx2g`, two workers, in-process Kotlin compilation
and the PC's shared build lock:

```sh
JAVA_HOME=/home/eslam/Storage/tools/jdk-21.0.13+9 \
ANDROID_HOME=/home/eslam/Android/Sdk \
flock -x /home/eslam/Storage/tmp/oc-build.lock \
./gradlew --no-daemon --no-configuration-cache -Pkotlin.incremental=false \
  -I /tmp/eng-test-home.init.gradle.kts \
  :core:data:desktopTest :core:data:testAndroidHostTest \
  :engine:settings:desktopTest :engine:profiles:desktopTest :engine:diagnostics:desktopTest \
  :engine:settings:compileAndroidMain :engine:profiles:compileAndroidMain \
  :engine:diagnostics:compileAndroidMain --continue
```

The temporary init script sets test JVM `user.home` to `/tmp/eng-test-home` and `maven.repo.local` to the existing
`/home/eslam/.m2/repository`. A `.m2` symlink under that temporary home points to the same cache. Robolectric initially
could not create its lock under the read-only real home; this environment-only adjustment resolved it. The sparse
checkout also omitted the tracked Flutter ledger fixture; its unchanged HEAD contents were materialized locally for
`ProfileSweepShapesTest`. Neither adjustment changes committed source. `tool/format-changed.sh` is absent; changed
files were tidied directly and `git diff --check` passes. No emulator, device, iOS, APK or full-repository gate ran.
Final Gradle log: `/tmp/eng-final-validation.log`.

Code commits, all with the requested subject suffix and both trailers:

- `6ce4e12a` — mobile consent revocation (5).
- `4e73d74c` — durable migration and owner-bound sharing grants (6, 7).
- `63a89cc6` — conditional embedded edits and confirmed queue plans (4).
- `e7f72882` — crash startup ownership and generation fencing (8).
- `5b15e35d` — profile-store draining, transaction recovery and reset integration (2, 3).

Git limitation: this managed checkout allows source edits but mounts its shared Git metadata read-only. Normal
`git add` failed while creating the worktree index lock. These are real local commits in `/tmp/eng-fixes.git`, on
`native/fix-eng-local`, with parent `066a00be`; this report is committed after the five code commits. The portable
bundle is `/tmp/eng-fixes.bundle`, with `066a00be` as its prerequisite. Import it in a writable checkout to attach the
commits. This checkout retains the matching source edits; its original branch and index could not be updated.
Nothing was pushed.
