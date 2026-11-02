# GLM draft-review triage — 2026-11-02

The independent reviewer was **OpenCode 1.19.0 running
`zai-coding-plan/glm-5.3` on the VPS**, with exit code 0. The coordinator retains
`GLM-DRAFT-REVIEW.md` and `glm-worker-proof.json` in the
`native-drafts-artifacts-20261102` evidence directory. The review's SHA-256 matches
the execution receipt:
`bfb5a9ae4597b2db978750705f170f3adf831d2d8b0bfdf9280721557f4b8294`.

**Scope matters:** GLM reviewed an early integration snapshot, before
`ConversationDrafts` and the editor/world wiring were complete. Its missing-type
and missing-wiring findings describe that snapshot. This document is coordinator
triage against the later integration source and [frozen contract][contract]; it is
not an independent GLM review of the final candidate. Neither the worker's exit
code nor the dispositions below establish that pending tests or device flows
passed.

| Finding | Later source disposition and remaining boundary |
|---|---|
| **F1 — whitespace save drops retained attachments** | **Addressed.** [`Drafts.saveLocked`][drafts] retains previous attachment references when the replacement list is null. Only an explicit list replaces them. [`DraftsTest`][store-tests] contains the whitespace/null-attachment regression. The text-only session also refuses existing attachments instead of consuming unsupported data. |
| **F2 — local dispatch clears the editor / same-text newer edit is erased** | **Addressed in the integrated path.** [`BasicComposer`][editor] no longer clears on a successful local send return. [`GraphChatWorld`][world] captures submission identity and watches Accepted/canonical evidence; failed or unconfirmed outcomes do not authorize clearing. [`DraftSession.clearAfterSend`][sessions] checks the captured object and edit revision, so changing away and back to identical words invalidates an old clear. Regressions are present in [session][session-tests] and [editor][editor-tests] tests. |
| **F3 — initial empty editor overwrites restored content** | **Addressed.** The [editor][editor] is keyed by target, stays disabled before restoration, and reacts to external restore revisions. Accepted user edits are reported synchronously; programmatic restoration does not emit an initial empty change. The [editor regression][editor-tests] explicitly covers that boundary and target isolation. |
| **F4 — refused profile removal permanently fences a surviving profile** | **Explicit recovery implemented, not rollback.** [`PreviewRuntime.connectNow`][runtime] permits reopening only through explicit Connect after saved-profile/trust validation; bootstrap and internal creation cannot reopen the fence. [`ConversationDrafts.reopenProfile`][sessions] serializes against cleanup and retires old writer generations. As the [contract states][contract], a later metadata/secret failure can leave the profile present after its drafts were removed; reconnect permits new drafts and does not restore deleted ones. [Session regressions][session-tests] cover recovery and queued-writer fencing. |
| **F5 — another profile's deletion destroys unattributed legacy drafts** | **Addressed by refusal.** [`Drafts.closeProfile`][drafts] refuses cleanup without changing the index when several profiles could own a legacy entry. [Session coverage][session-tests] names this ambiguous-owner case. With one possible owner, legacy cleanup remains part of that profile's removal; this preview does not add Flutter migration. |
| **F6 — wrong-typed preference crashes or looks absent** | **Addressed at the store boundary.** [`Drafts.load`][drafts] distinguishes absent from present/non-string using `contains`, converts throwing reads to unreadable storage, and preserves cancellation. The [real adapter][binding] still reports invalid types by throwing, but the draft read boundary now contains that failure. [`DraftsTest`][store-tests] includes both wrong-type and throwing-read regressions; unreadable indexes refuse writes. |
| **F7 — raw autosave state/scope and no close boundary** | **Addressed for the new session path.** [`ConversationDrafts`/`DraftSession`][sessions] use injected app scope and one serial dispatcher; debounce changes, flush, acknowledgement and closure enter that lane. Display close has a bounded wait while admitted app-scope storage work survives it. The lower-level [`DraftAutosave`][autosave] still accepts a bare scope and requires serialized use; this is not a claim that arbitrary concurrent direct callers became safe. |
| **F8 — empty legacy owner bypasses profile fence** | **Latent low-level limitation remains.** The general [`Drafts.save`][drafts] API still accepts an empty owner. The native text-session path derives owners from a real conversation profile and applies generation/store fences; no preview writer intentionally supplies the legacy empty owner. This does not establish a global fence for migration or other future direct legacy writers. Such callers require their own admission policy before being enabled. |

The runtime now binds the real acknowledged preferences, profile directory and
file vault in [PreviewDrafts][binding], rather than a volatile fallback. The
[contract][contract] and [engine integration notes][engine-notes] record the
current text-only boundary. Tests linked above are source coverage, not pass
receipts. The coordinator's final validation record must identify the tested
candidate and separately report builds, runtime/device flows and any unrun gates.

[contract]: ../../../native/contracts/chat-drafts-2026-11-02.md
[engine-notes]: ../../../native/engine/composer/DRAFTS-INTEGRATION-2026-11-02.md
[drafts]: ../../../native/engine/composer/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/composer/drafts/Drafts.kt
[sessions]: ../../../native/engine/composer/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/composer/drafts/ConversationDrafts.kt
[autosave]: ../../../native/engine/composer/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/composer/drafts/DraftAutosave.kt
[world]: ../../../native/feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/world/GraphChatWorld.kt
[editor]: ../../../native/feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/composer/BasicComposerHost.kt
[runtime]: ../../../native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/PreviewRuntime.kt
[binding]: ../../../native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/PreviewDrafts.kt
[store-tests]: ../../../native/engine/composer/src/commonTest/kotlin/io/github/eslamasabry/oc/engine/composer/DraftsTest.kt
[session-tests]: ../../../native/engine/composer/src/commonTest/kotlin/io/github/eslamasabry/oc/engine/composer/ConversationDraftsTest.kt
[editor-tests]: ../../../native/feature/chat/src/desktopTest/kotlin/io/github/eslamasabry/oc/feature/chat/ComposerDraftRestoreTest.kt
