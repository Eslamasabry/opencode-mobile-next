VERIFIED: green

# P12-CHAT-GAPS — two native chat gaps (B-CHAT-029, B-CHAT-057)

Branch `claude/chat-parity-native-integration-h6ol5f`, based on `native/p12-integrate-2026-11-03` (`c6d14e8d`).
Write set: `native/engine/cards/`, `native/feature/chat/`, this report. No app wiring, routes, strings or modules
were needed (both gaps live inside code that is already routed and in the app).

## 1. B-CHAT-029 — deep or cyclic card payloads (`:engine:cards`)

**What was actually wrong.** The validator (`CardValidator.bounded`, the port of Flutter `_bounded` in
`lib/domain/genui/gen_ui_validation.dart`) already had Flutter's depth (8), count (4096) and identity-cycle guards,
and the existing `fromInput` cyclic/deep tests passed. The unguarded recursion sat one step earlier:
`CardJson.tree` copies the wire input (`fromPart` → `tree(tool.input)`) and an answer line (`receipt` →
`CardJson.parse`) into the validator's tree with no depth limit, so a deeply nested card from the server threw
`StackOverflowError` before the validator ever saw it (it escapes `fromInput`'s `catch` list and would crash the
transcript projection). Wire JSON cannot be cyclic; cycles reach the codec only as in-memory trees, which
`bounded` refuses as a bad value (unchanged, still tested).

**Fix.**
- `CardJson.validatorTree` / `parseForValidator` (internal): a value deeper than `CardValidator.MAX_DEPTH` (8) is
  replaced by a `Cut` marker instead of being copied. Dart `_bounded` refuses the first value past depth 8 before
  it looks at the value, so the validator now gives **exactly Flutter's refusal**: `TooLarge`, or the earlier
  failure in depth-first walk order (e.g. `BadValue` for a broken string before the deep branch). The size check
  after the walk only runs when no `Cut` exists, so accepted payloads and revisions are unchanged.
- `CardJson.tree` / `parse` (general use: corpus, `CardRecovery`) refuse nesting past `MAX_TREE_DEPTH` (256) with
  `IllegalArgumentException`, which every caller already treats as "not readable", never a stack overflow.
- `CardValidator.MAX_DEPTH` names the existing limit (8).

**Tests** (`engine/cards/src/commonTest/.../CardDepthTest.kt`, all `B-CHAT-029 …`), red first against the unfixed
code — 3 of 6 failed with `StackOverflowError`:

| Test | Before | After |
|---|---|---|
| a very deep wire payload is refused as too large, never a crash (100 000 levels) | StackOverflowError | pass |
| the refusal is the one Flutter gives - the first failure in walk order wins | StackOverflowError | pass |
| general JSON deeper than the tree limit is refused plainly, not with a stack overflow | StackOverflowError | pass |
| depth 8 is the last level the validator reads, from the wire and as a tree | pass | pass |
| a cyclic tree is a bad value, a normal payload still parses (same revision wire vs tree) | pass | pass |
| a deep answer line is not a receipt and never crashes the card state | pass (16 000 levels fit the test stack) | pass |

Existing `CardCodecTest` (`B-CHAT-029` cyclic/non-JSON/non-finite/surrogate, very deep tree) and the shared corpus
(`CardCorpusTest`) stay green.

## 2. B-CHAT-057 — one `ContextLevel` (`:feature:chat`)

**Deviation from the brief.** The brief names `:engine:turnfacts` as the single source. That module does not exist
on this base or on any remote branch (checked every `native/p12*` and `claude/*` head); the turnfacts port lives in
`:engine:transcript` as package `io.github.eslamasabry.oc.engine.transcript.turnfacts` (see P12-CHAT-TRANSCRIPT,
"Moving them to `:engine:turnfacts` later is a package move"). Creating a new module needs a `MODULE_OWNERS` row,
which only the coordinator edits, and `:engine:transcript` is a shared single-owner module. So the single source is
the existing `turnfacts.ContextLevel` + `contextLevel(fraction)` in `:engine:transcript`, untouched.

**What was duplicated.** `feature/chat` already used the turnfacts enum for the context ring and sheet
(`TurnFactsPresenter`, `ChatContextSheet`), but the conversation header kept its own
`header.presentation.ContextLevel { Calm, Warning, Full }` and its own 70/95 thresholds in
`HeaderRules.contextLevel`. No other copy exists (`KitContextLevel` in `:ui:kit-chat` is the kit's display enum,
mapped by `ChatContextSheet.toKit()`; the kit may not depend on engines).

**Fix.** The header enum is deleted; `DetailsUi.contextLevel` and `ChatHeaderBar` use the turnfacts enum;
`HeaderRules.contextLevel(percent)` is now a one-line adapter over the turnfacts rule (`percent / 100.0`, exact at
70 and 95). Behaviour is unchanged.

**Tests.** `ChatHeaderPresenterTest` `B-CHAT-057 the header and the context ring read one level rule` (every percent
0–100 equals the turnfacts rule; null stays null); the existing `B-CHAT-057 …` header thresholds test, the Details
sheet test and `TurnFactsTest` now compile against the single enum and pass; `ContextUsageTest` unchanged.

## Catalog rows

- Covered: **B-CHAT-029** (deep from the wire, deep answer line, cyclic tree, walk-order reason, normal payload,
  general tree limit), **B-CHAT-057** (single level rule for header and ring).
- Still missing in this unit: none.

## How to reach it

Nothing new to route. Cards appear in the chat transcript (`:feature:chat` card slot, P12-CHAT-CARDS); the context
level shows in the chat header's ⋯ → Details ("Context") and in the context ring/sheet.

## Verification

Run in this cloud container (JDK 21, Android SDK platforms;android-37.1 + build-tools 37.0.0), on candidate
`a9bfcc1d` (base `c6d14e8d` + the two code commits); report-only commits follow.

| Command | Result |
|---|---|
| `./gradlew :engine:cards:desktopTest` (before the fix, with the new tests) | 48 tests, **3 failed** (StackOverflowError) — the red run |
| `./gradlew :engine:cards:desktopTest` | 48 tests, 0 failures, 0 skipped |
| `./gradlew :feature:chat:desktopTest :engine:transcript:desktopTest` | feature:chat 216 / engine:transcript 152 tests, 0 failures, 0 skipped |
| `./gradlew :quality:test :androidApp:testPreviewDebugUnitTest :androidApp:assemblePreviewDebug` | BUILD SUCCESSFUL; quality 59 tests, androidApp 15 tests, 0 failures; `androidApp-preview-debug.apk` built |
| `tool/check_commits.sh`, `OC_BASE=origin/native/p12-integrate-2026-11-03 tool/scan_push.sh` | OK / clean |

Not run: emulator flows (no device here; neither change alters a screen).

## For the integrator

- Build note for cloud containers: Maven Central answered `429 Too Many Requests` for every build here; a
  user-level init script (`~/.gradle/init.d/`, not committed) pointed `repo.maven.apache.org` at Google's Central
  mirror (`maven-central.storage-download.googleapis.com/maven2/`). Nothing in the repo changed for it.
- If `:engine:turnfacts` is created later, move the `turnfacts` package out of `:engine:transcript`; feature/chat
  then changes imports only.
