# Chat second-eye fixes

Baseline: `066a00be` on `native/fix-chat-2026-11-01`. Review: the main checkout's
`docs/native/reviews/chat.md` (that file was absent from this worktree). The review has ten MAJOR findings and no
BLOCKER findings. All ten still applied at this baseline; none was already fixed or rejected. The binding sources
are plan 01/03 and INTEGRATE-W1/W2's flat core contract.

## Findings

| # | Disposition and regression | Change |
|---|---|---|
| 1 — system Back | **Fixed** — `ChatReviewUiTest`: `chat finding 1 system Back with unsaved draft reaches the guard` | `KitBackGuard` registers the actual navigation-event Back handler only while `guardBack` is true. The presenter retains the existing save/discard ordering. The test dispatches Back through the navigation dispatcher, not through the top-bar button. |
| 2 — accepted send cancellation/recovery | **Fixed, with storage admission gated** — `ChatWorldReviewTest`: `chat finding 2 composer keeps words until durable delivery admission`; `chat finding 2 accepted send survives entry destruction and uncertain recovery never resends`; `chat finding 2 admission preserves words typed after send was captured` | The composer awaits admission before clearing. `ConnectionDelivery` hands accepted work to connection-owned `PromptDelivery`; the existing durable queue/receipt pipeline writes its marker before returning. Pending worlds survive entry disposal; reopening projects outcomes and durable markers without replay. Outcomes carry local send identities. **The current app graph supplies no `DeliveryStorage`: sends therefore return `StorageRefused` and keep the field until the storage owner supplies the adopted, migration-gated store, writer lanes and profile directory. There is no volatile-storage fallback.** |
| 3 — obsolete connection generation | **Fixed** — `ChatWorldReviewTest`: `chat finding 3 close invalidates and reopen rebinds the world`; `chat finding 3 closed generation preserves one uncertain marker without replay` | Registry observation runs outside connection lifetime. Close invalidates live state/capabilities; reopening replaces the handle and collectors. Commands fence graph identity, including after the reducer admission snapshot. Delivery link epochs also change with connection status/reconnects. |
| 4 — lost stale/reconnect refresh | **Fixed** — `ChatReviewRegressionTest`: `chat finding 4 stale and reconnect signal without visible rows`; `ChatWorldReviewTest`: `chat finding 4 first reconnect after attachment refetches idle history` | Refresh requests have their own observable flow, even when applying an event changes no displayed rows. The handle is seeded with the already-established connection, so the first subsequent reconnect refetches. |
| 5 — delayed history rollback | **Fixed** — `ChatReviewRegressionTest`: `chat finding 5 delayed history preserves newer streamed words` | History captures a reducer version before I/O. Merge replaces only message/part values not changed since that capture; stale-generation responses are rejected. History ordering preserves the older loaded prefix. |
| 6 — equal-text false settlement | **Fixed** — `ChatWorldReviewTest`: `chat finding 6 historical equal text never settles a new send`; `chat finding 6 one new canonical prompt settles at most one identical admitted send`; `chat finding 6 buffered pre-dispatch messages cannot settle a new send`; `chat finding 6 initial history must load before an uncorrelated send` | Canonical wire identities win. The fallback requires admission, excludes every message already in the reducer before dispatch, and consumes each server identity once. It does not rely on a display snapshot delayed by the flush window. Initial history must load first. |
| 7 — no permission response path | **Fixed** — `ChatReviewUiTest`: `chat finding 7 permission slot responds during every load phase`; `ChatWorldReviewTest`: `chat finding 7 the production world owns a permission response model` | The entry observes the connection's request model and renders `ChatRequestSlot` independently of Loading/Failed/Live. A request owns the composer, hides field/Send, and exposes Stop. The real model sends Allow once through the gateway. A kit-owned environment provides the graph's registered redactor to permission/mono surfaces. Held answers flush when leaving the entry. |
| 8 — incomplete history/search | **Fixed** — `ChatWorldReviewTest`: `chat finding 8 older pages remain reachable and search is partial`; `B-CHAT-115 chat finding 8 searches full reasoning tool output and filenames` | Retain `nextCursor`, expose `loadOlder`, show the existing partial-search words and Load older messages action, and update partial status after loading. Search uses full reasoning, tool data and filenames, anchored to existing rows, rather than only displayed first lines/prose. |
| 9 — phantom notice messages | **Fixed** — `ChatReviewRegressionTest`: `B-CHAT-097 chat finding 9 unexpected notice reconciles without phantom message` | Unknown notice message IDs request reconciliation without creating a message. Existing-message notices still attach. The old recording expectations that depended on invented messages were corrected to require reconciliation. |
| 10 — no-op read acknowledgement | **Fixed** — `ChatWorldReviewTest`: `B-CHAT-012 chat finding 10 visible chat advances the connection watermark` | `ConnectionSession` owns read state. The real presenter/world path observes the authoritative session idle watermark and acknowledges that exact transition, including a reply ending while visible. The test checks watermark advancement and `finishedUnseen`, not a fake call counter. This is local connection read state; it sends no consent-free server read receipt. |

## Verification

The regressions were exercised against the defective behavior before the corresponding fix:

- Transcript stale/reconnect, delayed history and unknown notices: **3 tests, 3 assertion failures**.
- Original world behavior: close/rebind, first reconnect, repeated text, older history and read acknowledgement:
  **5 tests, 5 assertion failures**.
- Pre-clear admission and omitted search data: **2 assertion failures** while the preceding world fixes passed.
- With the old screen wiring restored: system Back and permission controls failed assertions. The buffered
  pre-dispatch and initial-history settlement cases also failed: **4 tests, 4 failures**. The screen fixes were restored.
- A connection-close recovery regression additionally checks that an interrupted dispatch retains exactly one
  uncertain bubble and never replays.

The generation-close regression failed once before the pending/marker projection fix. The newer-typing regression
also failed once before removing delivery's draft reset: the field alone owns current draft text.

Final gate, from `native/` (one Gradle invocation at a time):

```sh
JAVA_HOME=/home/eslam/Storage/tools/jdk-21.0.13+9 ANDROID_HOME=/home/eslam/Android/Sdk ./gradlew \
  --no-daemon -Pkotlin.incremental=false --max-workers=2 \
  '-Dorg.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=768m -XX:+UseParallelGC -Dfile.encoding=UTF-8' \
  -I /tmp/chat-gradle.init.gradle \
  :feature:chat:desktopTest :engine:connection:desktopTest :engine:transcript:desktopTest \
  :engine:requests:desktopTest :engine:delivery:desktopTest :ui:kit:desktopTest :ui:kit-chat:desktopTest \
  :feature:chat:compileAndroidMain :engine:connection:compileAndroidMain \
  :engine:transcript:compileAndroidMain :engine:requests:compileAndroidMain \
  :engine:delivery:compileAndroidMain :ui:kit:compileAndroidMain :ui:kit-chat:compileAndroidMain \
  :quality:test --tests '*Chat*Review*'
```

**BUILD SUCCESSFUL, 22 s; 169 actionable tasks, 33 executed and 136 up-to-date.** All seven touched production
modules compile for desktop and Android. Counts below come from JUnit XML; `:quality` is the targeted chat suite,
not the entire repository architecture/UI suite.

| Module | Desktop tests | Failures / skipped |
|---|---:|---:|
| `:feature:chat` | 48 | 0 / 0 |
| `:engine:connection` | 3 | 0 / 0 |
| `:engine:transcript` | 22 | 0 / 0 |
| `:engine:requests` | 80 | 0 / 0 |
| `:engine:delivery` | 65 | 0 / 0 |
| `:ui:kit` | 126 | 0 / 0 |
| `:ui:kit-chat` | 106 | 0 / 0 |
| `:quality` (chat regressions only) | 16 | 0 / 0 |
| **Total** | **466** | **0 / 0** |

Existing dependency/API warnings remain; this is not a warning-free build. `git diff --check` passed. An initial
attempt to select `:feature:chat:testAndroidHostTest` was rejected because non-platform KMP modules have no such
task; Android compilation plus desktop tests are the completed gates.

Host-only setup: the sandbox prevents Skiko's default cache lock. The matching native library was extracted from the
resolved **skiko-awt-runtime-all 0.156.0** JAR to `/tmp/chat-skiko`; the uncommitted init script supplies
`systemProperty 'skiko.library.path', '/tmp/chat-skiko'` to test tasks. Reusing an older cached native library caused a
host crash, so only the version matching the resolved JAR was used for the passing tests. No application/toolchain
configuration was changed for this workaround.

Tests use synthetic data only. No emulator, device, live server, APK, release, CI or push is claimed.

Shared edits are limited to the engine contracts/connection owners needed by the fixes, kit Back/redactor providers,
and `:quality` tests. Integration/UI tests live in `:quality` because K1 disallows their test-host imports in features.
No core failure vocabulary, build convention or version catalog changed. `tool/format-changed.sh` is absent from this
checkout; changed files were formatted directly.

## Local commit delivery

The sandbox permits workspace files but makes the worktree's common Git metadata under the main checkout read-only.
A normal `git add` failed creating the worktree index lock. The requested commits are therefore made in a writable
local Git repository over the same baseline, with the requested subjects/trailers, and exported as a Git bundle.
The original worktree branch cannot be advanced in this sandbox. Nothing is pushed.


Implementation commits in the bundle (all with the requested trailers):

- `1e711c52` — transcript refresh/version/notice regressions and fixes.
- `4f46b203` — connection-owned read, request and delivery services.
- `300984ec` — transcript search data and reducer admission identities.
- `0d382aa6` — kit Back and redaction providers.
- `9d5cdb6f` — world lifecycle, delivery admission, reconciliation, pagination and world regressions.
- `0f8e002c` — screen Back/request integration and dispatcher/UI regressions.
- This report is a separate final documentation commit.

Export: `native/build/chat-fixes.bundle` (ignored build artifact, intentionally outside the commits it contains).
The bundle requires the existing `066a00be` baseline.
