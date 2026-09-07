# Luna batch audit — 2026-09-07

Ten bounded reliability slices were implemented on the existing `dev` checkout,
starting at `f59fbfe9a3ba761bb15060ae32f012eca7194206`. Scope and worker ownership
are recorded in [the batch ledger](../backlog/luna-batch-2026-09-07.md).

## Lead audit findings resolved

- Codex: bind a send to the exact connection epoch, invalidate stale requests
  and approvals on loss, and retain tracked sessions after partial resume.
- Pairing: isolate probe exceptions; redact untrusted endpoint details without
  losing safe fallback diagnostics.
- Desktop updates: use the production Navigator context for deferred notices,
  validate project release links, and confirm actual external-link opening.
- Share: prevent delayed cold-consume results or disposal from resurrecting text.
- Session search: preserve rows only within the same query/profile scope and
  clear retired loading state when replacing the repository.
- Import: freeze the reviewed nested JSON and return independent request trees;
  reject cycles, excessive depth, unsupported values, and unsafe destinations.
- MCP: distinguish saved configuration from successful reconnection, including
  reload paths that return normally with a connection error. Retry does not save
  the same configuration again.
- Voice: serialize deletion with installation, finish all owned cleanup attempts,
  release locks after failure, and surface asynchronous write failures.
- Digest: preserve unknown outcome/session-total meaning, expose copy feedback,
  localize metadata, and remove the nested Activity empty-state scroller that
  swallowed gestures at large text sizes.
- Stash: fail before publication when existing transaction collection fails;
  verify retry and restart recovery without losing previous saved prompts.

The first complete scan exposed four failures. Repairs preserved the localization
baseline (nine reported UI literal occurrences were moved into ARB), settled
constructor monitor notifications before the per-session assertion, exercised the
question sheet through its real notification route at 2x text with keyboard, and
fixed a controller bug where unsupported form events inflated attention counts.
The form regression now asserts both an empty form store and zero attention.

## Validation

The final frozen-source scan passed **2,075 tests across 212 files**, in 11
serial chunks (432.43 seconds total), with no failures. Three optional preview
tests were skipped: phone context, completion digest capture, and remaining
usage capture. The digest capture was subsequently run explicitly and passed
for dark, light, and RTL at 2.5x text on a 320x640 surface.

Source-content fingerprint:
`4503d1c3615f98ce99d5b60a46c90ed3c79e317eb566150f2cf5c7aabc967efd`.
The runner checked the fingerprint before every chunk and after the final chunk;
coverage was not combined with the earlier failed scan. The complete file list
and chunk outcomes are in [the result manifest](luna-batch-2026-09-07-results.json).

Additional gates: changed Dart files format clean; `git diff --check` clean;
Flutter analyzer clean; generated SDK analyzer clean and **47 SDK tests pass**.
The focused audit-repair run passed 61 tests before the final full scan.

Rendered digest evidence: [dark](../qa/luna-batch-2026-09-07/digest-dark.png),
[light](../qa/luna-batch-2026-09-07/digest-light.png), and
[RTL / large text](../qa/luna-batch-2026-09-07/digest-rtl-large.png).
These captures exercise the production Activity screen, including its outer
scroll gesture and reachable digest actions; they use synthetic metadata.
The raw local evidence directory is `/tmp/ocmn-luna-audit-20260907/`; its initial
failed scan is retained separately from `final-suite/`.

Toolchain: Flutter 3.47.2, Dart 3.13.2, JDK 17, Android compile API 37 (installed
SDK package `platforms;android-37.0`). The handover cache reports Flutter 3.47.1;
validation instead uses the 3.47.2 tag from `shorebirdtech/flutter`, revision
`d3b14c876900e553bc736ca19295fc09e3853e8e`. This local standard-engine debug build
is not proof of the release-pinned Shorebird engine or the stable CI signer.

## Android result

The final source built with `flutter build apk --debug --target-platform
android-x64 --no-pub` (20.5 seconds in Gradle) and installed with `adb install -r`.
Installed version: `1.0.36+37`. APK SHA-256:
`83ed8ce7c4868b697ccfe30ba5e2f1312a9d529fbb2e2037ba300a84afc4cf0c`.

Verified on the final installed APK:

- [Warm share](../qa/luna-batch-2026-09-07/android-warm-share.png) and
  [cold share](../qa/luna-batch-2026-09-07/android-cold-share.png) populate an
  unsent composer; [reopening](../qa/luna-batch-2026-09-07/android-reopened-draft.png)
  retains the draft.
- A failed inventory refresh retains all 11 loaded rows and exposes an honest
  [retry state](../qa/luna-batch-2026-09-07/android-search-refresh-failure.png).
  A retained historical row still [opens its messages](../qa/luna-batch-2026-09-07/android-recovered-chat.png).
- At Android font scale 2.0, a swipe starting inside Activity's empty state moves
  the outer list and reaches/expands the
  [completion digest section](../qa/luna-batch-2026-09-07/android-activity-digest-large.png).
  This native fixture shows the honest empty-metadata state; populated digest
  rendering is covered by the separate widget captures. Font scale was reset.
- No additional prompt POST occurred: the fixture's counter stayed at its one
  earlier fixture-development probe. AndroidRuntime/Flutter error logs were empty.

Early immediate-after-launch UiAutomator checks failed to observe cold text.
The final cold capture waits for activity launch and 10 seconds of Flutter startup;
manual cold capture also passed. No app change was made based on that automation
failure, and this is not a timing guarantee. The harness also needed to read
Flutter labels from `content-desc` and scroll the long inventory to its error row.
These earlier failures remain in the raw local logs. Structured final observations
are in [the Android result](luna-batch-2026-09-07-android.json).

Onboarding and the demo permission/allow-once flow were additionally exercised
on the earlier debug build of this batch. The final native checks above were
repeated after reinstalling the final source. Third-party Kotlin migration
warnings remain; no compiler failure occurred.

## Evidence boundaries

Native checks use a fresh Android 14 x86_64 AVD, `OCMN_Luna_Audit`, through ADB.
The app connects to an isolated synthetic v1 HTTP server over ADB reverse on
port 4123. Session and prompt content is synthetic; no provider/model account
is contacted. The fixture acknowledges prompts without executing them and records
sanitized method/path/status metadata for detecting accidental sends.

This batch is local debug verification, not a signed release, native CI run,
physical-phone test, TalkBack audit, live Codex integration, real voice-model
installation, or authenticated provider validation. No storage schema migration
or generated SDK edit is introduced. Imported transcript data stays in memory;
pairing diagnostics do not retain credentials or raw untrusted endpoint details.
