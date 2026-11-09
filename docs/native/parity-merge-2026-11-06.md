# Native parity integration and first-send recovery

Worktree: `/home/owner/Storage2/wt/scr-merge`; branch: `native/screens-merge`.
Starting revision: `27bc42b60`. Implementation candidate: `a9ca8b33e`.

Finish line: integrate the four parity branches in order, reconcile slow first-send receipts without resending, and keep the real conversation's composer available.
Non-goals: compilation, analyzer or test execution, emulator/device runs, live-server probes, pushes, signing and releases.

## Merges and conflicts

All four merges used `git merge --no-ff --no-commit`, followed by a merge commit with the requested body and trailers.

| Order | Branch | Integrated tip | Merge commit |
|---|---|---|---|
| 1 | `origin/native/parity-settings` | `8dee0ca57` | `54149edc6` |
| 2 | `origin/native/parity-projects` | `f5c44062d` | `5c7d7eb96` |
| 3 | `origin/native/parity-lists` | `d80aaf99b` | `292e8ab09` |
| 4 | `origin/native/parity-chat` | `b28024f06` | `fa5b8ca62` |

No textual conflicts. The starting revision is an ancestor of each parity tip, and every tip is an ancestor of the merged candidate. The earlier crash, N-19 keyboard, N-20 through N-28, Glass and upgrade fixes remain in history.

Static integration review covered the combined `ChatEntries` collectors and list-row `thenAction`, `ContinueOnPhone` header/menu cases, row navigation, default `ChatWorld` methods and `GraphChatWorld` implementations, and all composer tray glyph cases. The new Send later action discarded its argument; `a9ca8b33e` now writes and reads back the existing schedule prompt draft before navigation. Write refusal stays in chat, and normal editor restoration preserves later edits.

## First-send and composer fix: `137870192`

Source root causes:

- The nominal 95-second instant-start wait treated `SendOutcome.Unconfirmed` as terminal. A quick uncertain transport result bypassed the remaining confirmation window.
- The send link inspected observed admission only when the HTTP call returned. A later event or message-list entry could leave the original send uncertain.
- The new-conversation route waited for confirmed dispatch before opening chat. Its starting frame has agent/model chips rather than the real composer.
- The same uncertain send appeared both in the status strip and beside its pending bubble. After Check again, a second summary could also repeat the bubble's uncertainty.
- Approvals/forms explicitly hid the editor field. First-send recovery metadata could also keep the next conversation blocked after a late receipt had resolved elsewhere.

Implemented behavior:

- Correlated uncertain POST results keep their original client message ID. The gateway checks observed events and exact inbox/message identities every two seconds for up to 120 seconds after the uncertain response. No mutation is repeated.
- The instant-start owner observes the original local send for up to 240 seconds after local admission; uncertainty remains provisional. Scope/profile/epoch changes stop acceptance of stale proof.
- Once the engine owns the local send, `onStarted` opens the real chat immediately. The New entry remains underneath while its receipt observer runs. Chat adopts the existing engine bubble instead of creating another send.
- Chat performs a bounded two-minute receipt check after attachment. Later user-message events, inbox events or transcript refreshes trigger additional read-only checks, including after the timer ends. The pending bubble keeps a real Check again button.
- Receipt cleanup is idempotent only after exact scoped journal proof. Concurrent host/chat/manual observers can agree after one has removed the marker; same-ID replacements and failed writes remain protected.
- An uncertain bubble owns its notice and check result; the redundant status warning is suppressed. A confirmed result can use the status strip after the bubble disappears.
- The editor stays mounted while approvals/forms wait; existing send guards and Stop behavior remain. The deliberate Find replacement from N-27 remains intact.
- First-send review envelopes add optional `reviewId` and `draftDigest` fields. Exact confirmed proof clears stale review/creation locks. Compare-and-commit and the original digest protect replacement drafts and metadata. These use existing profile-scoped keys and deletion paths.
- Legacy envelopes can recover from a single unambiguous confirmed receipt for the exact profile/scope/session/tab. Multiple legacy receipts remain unresolved; absence of a queue marker or equal prompt text never counts as admission proof. Legacy draft words without a digest are retained.

## Verification actually performed

- Formatted changed Kotlin/Gradle sources with cached ktfmt.
- `git diff --check` and final candidate diff checks.
- Parsed all six merged resource XML files.
- Checked the four ancestry relationships and the ordered merge parents.
- Confirmed seven protected input/layout/Glass preference files are unchanged from the starting revision: `KitScreen`, `KitField`, `GlassSpec`, `GlassFinish`, `AuroraImage`, `KitSurfaceGlass`, `PersonalPrefs`.
- Source review of API implementations/callers, receipt fences, pending-bubble adoption, metadata cleanup and composer submission guards.

Regression sources were added/updated in `GatewaySendLinkTest`, `ReceiptsTest` and `ChatRulesTest`. They cover late exact-ID confirmation, bounded absence, changed scope, concurrent cleanup, replacement entries, storage refusal and a single uncertainty notice. They have **not** run.

**No compilation, analyzer, tests, preflight, emulator/device work or live-server probes ran. Nothing was pushed, signed, deployed or released.**

## Gaps and coordinator follow-up

1. Compile the full merged candidate and send compiler errors back. Implementation and static review do not prove compilation or device behavior.
2. Rewalk first plain `Hi` on This phone, delayed/missing SSE, slow admission, Check again, reopening and process restart. Verify one server user message, one uncertainty notice at most, a working button, and the next New conversation becoming available after late confirmation.
3. Verify composer visibility and IME focus while sending, with approvals/forms, at large text, with Glass, and after opening/closing Find. Repeat N-19 Send-once and selection checks.
4. Verify late cleanup while a replacement draft exists, plus profile/project changes during a read. Ambiguous legacy first-send bindings intentionally remain blocked for review.
5. Verify Send later and repeat receive the composer text, retain subsequent schedule edits after recreation, and stay in chat on a refused draft write.
6. The existing `feature/chat/build.gradle.kts` capture-only filter excludes most `*Test.kt`, including `ChatRulesTest` and the merged `ErrorRecoveryTest`. Added source is not an executed regression gate; the coordinator must account for that exclusion when scheduling checks.
7. This integrates the supplied branch work; it does not qualify or close every remaining item in `/home/owner/Storage2/wt/native-parity/PARITY.md`. No new parity census or device screenshots were produced.

All new commits have subjects ending `[skip ci]`, a body, `Order: screens`, `Engine: codex`, and `Co-Authored-By: Codex <noreply@openai.com>`.
