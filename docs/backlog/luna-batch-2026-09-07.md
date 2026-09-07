# Luna implementation batch — 2026-09-07

Base: `f59fbfe9a3ba761bb15060ae32f012eca7194206`, existing `dev` checkout.
Maintainer requested ten Luna implementation assignments followed by a lead
audit. This batch improves existing behavior; it does not complete ten backlog
epics or authorize release, signing, CI dispatch, or live account access.

## Scope and ownership

| Piece | Finish line | Exclusive write ownership | Focused checks |
|---|---|---|---|
| 1 Codex recovery | Discard stale socket frames; partial resume remains degraded; never replay ambiguous mutations | `lib/codex/`, Codex tests | Transport/gateway disconnect and partial-resume regressions |
| 2 Pairing | Safe diagnostic text and fallback after individual candidate probe failure | `lib/state/pairing.dart`, pairing payload tests | Malformed/credential-bearing addresses, thrown probe, successful fallback |
| 3 Desktop updates | Select highest valid project build, trusted release link, bounded/lifecycle-safe check | `lib/update/desktop_release_check.dart`, its test | Unordered releases, malformed entries, trusted links and notice delivery |
| 4 Shared text | New warm share wins over delayed cold share; disposal is safe | `lib/platform/share_intent.dart`, its test | Delayed consume, disposal and notifier lifecycle |
| 5 Session search | Keep useful same-query rows after refresh failure without crossing query/profile boundaries | Global sessions screen and its test | Refresh error/retry, delayed pages and scope changes |
| 6 Import review | Review freezes nested transcript data and validates explicit destination | `lib/domain/session_import.dart`, its test | Input/output mutation isolation and valid unknown protocol fields |
| 7 MCP recovery | Retry connection after successful configuration without adding it twice | MCP setup screen and its test | Saved/reconnect failure, retry, changed location and route lifecycle |
| 8 Voice storage | Serialize deletion with install publication; remove owned temporary files | Voice downloader and its test | Concurrent install/delete, cancellation and cleanup failure |
| 9 Completion digest | Localized, accessible metadata and copy feedback; preserve unknown/session-total truth | Completion digest widget/model, `app_en.arb`, new card test | Zero/unknown, clipboard outcome, narrow large-text and RTL |
| 10 Prompt storage | Resolve an evidenced interrupted-write/delete integrity gap with retry/restart recovery | Prompt shelf store and its three focused tests | Transaction/cache failure, attachment ownership, interrupted deletion |

Workers read current code and existing tests before changing behavior. Piece 10
must identify a concrete uncovered gap before editing; an audit alone is not
counted as an implemented slice. Existing public APIs stay stable unless the
lead approves a necessary contract change.

## Coordination and acceptance

- Workers share the checkout and must preserve all other actors' changes.
- Lead owns shared controller/chat/domain gateway/main integration, generated
  localization, this ledger, serial test execution, audit and Git publication.
- Workers do not run test/analyzer/build/pub processes or commit/push. They
  return changed paths, behavioral regressions and limitations. The lead sends
  concrete audit findings back to the owner.
- No generated SDK edits, new providers/languages, automatic sending, hidden
  feature exposure, signing, native workflow dispatch or release work.
- Accept source only after reviewing the complete diff, running affected tests
  serially, checking analyzer/format, and reviewing synthetic UI evidence where
  applicable. Such captures do not prove device or live-account behavior.
- Freeze a final source candidate, enumerate all recursive Flutter test files,
  then run bounded serial chunks with completed-file evidence. SDK checks run
  separately. Report skips/failures and native-host limitations explicitly.
- Land logical `[skip ci]` commits directly on `dev` and push to `mobile-next/dev`.

## Initial evidence

The source checkout matched the base revision. Two pre-existing untracked
artifacts (an APK and a video still) are outside this batch. The handover's
`91f8bd...` Shorebird cache actually reports Flutter 3.47.1, not 3.47.2;
the other installed SDKs report 3.38.5. The lead is preparing the official
3.47.2 source tag for validation, independently of worker implementation.

## Final outcome

All ten slices were implemented and lead-audited. Audit corrections and the
final frozen-source gates are recorded in [the verification report](../verification/luna-batch-2026-09-07.md):
2,075 Flutter tests pass across 212 files (three optional previews skipped),
47 SDK tests pass, analyzers/format/diff are clean, and the final local debug
APK was installed and exercised through ADB. Native checks use a synthetic
server and a fresh emulator; release and live-account validation remain outside
this batch.
