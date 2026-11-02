VERIFIED: green — `:feature:chat:desktopTest` 154/0, `:ui:kit-chat:desktopTest` 109/0, `:quality:test` 58/0 (K1 kit-only included), `:androidApp:testPreviewDebugUnitTest` 11/0, `:androidApp:assemblePreviewDebug` BUILD SUCCESSFUL (cloud container, JDK 21, Android SDK 37.1 / build-tools 37.0.0)

# P12 CHAT-HEADER — the conversation header, its menu and chips

Branch `claude/chat-screen-native-parity-119ovc`, based on `native/p12-chatbase-2026-11-03` (`92991b8e`).
Commits: `97ab7d0b` header + menu, `0a8c3638` screen tests, then approval chip + free-model note (see `git log`).

## What a person can do now (Android preview, real OC2 gateway)

How to reach it: Servers → a saved server → Conversations → open a conversation. Everything below is on the chat
screen itself: the bar at the top and the composer row at the bottom. No new bar level was added.

| Journey | What happens | Engine / gateway |
|---|---|---|
| Title and place | The bar shows the conversation title (server text; "Untitled conversation" when none), and under it **server · project** (the saved server's name and the session's folder name). | `ConnectionGraph.profile.name`, `gateway.session(id).directory` / `location.directory`, live `SessionChanged` |
| Rename | Tap the title (visible path, chevron), or ⋯ → **Rename conversation**. The dialog opens with the title selected; a blank title is refused ("Type a title."); unchanged/cancel does nothing. The new title shows as soon as the server acknowledged it; the server's own later title wins. | `ServerGateway.renameSession` (OC2 `PATCH /session/{id}`) |
| ⋯ conversation menu | **Go to:** Find, Details. **Do:** Share / Stop sharing, Fork conversation, Rename, Continue with…, Copy transcript, Archive conversation. **Delete conversation** last, after a divider. Every entry follows the conversation's own `ServerCapabilities` (never the backend kind). Fork waits, dimmed, for the first prompt ("Available after the first prompt"). | `HeaderRules.menu` |
| Details | A sheet: Server, Project, Model, Agent, Context used ("30% used · plenty left" / "… · at the limit"), Tokens. The session is read again when it opens. | `gateway.session(id)` (`contextUse`, `tokens`, `selection`) |
| Continue with… | A sheet "Continue this work" (title · project): **Models on this server**, the current one first marked **In use**; picking another moves this conversation to it after the server reads it back; a refusal keeps the sheet open with "Couldn't change the model." and Details. "Your conversation stays here." | `ChatWorld.loadModels/selectModel` (the composer's verified selection path) |
| Copy transcript | Markdown on the clipboard: `# title`, `## User` (the person's own words verbatim), `## Assistant` and reasoning masked through the app's `Redactor` (SEC-13). "Only loaded messages" is noted when older history is not loaded. | `world.transcript`, `LocalKitRedactor` |
| Share / Stop sharing | Share copies the link; the menu then offers Stop sharing. | `ServerOperationsGateway.shareSession/unshareSession` |
| Fork conversation | Opens the copy as a new chat. | `forkSession(id, null)` → `AppNavigator.openChat` |
| Archive / Delete | Both ask first, naming the conversation ("Delete “Fix the build”?"); nothing runs before the answer; the chat leaves only after the server removed it. A refusal stays in the conversation. | `archiveSession`, `deleteSession` |
| A failed act | One plain status line ("Couldn't rename the conversation.") with **Details** (the redacted failure in a sheet) and Dismiss. Never raw error text. | `KitStatusContribution` |
| Approval mode chip | In the composer row next to Model and Attach: **Ask first** / **Auto-approve on** / **Approve everything**. A tap opens the three modes (current one checked). Only "Approve everything" asks first ("Approve everything?"). While the conversation is open, every waiting permission ask of a conversation whose effective mode is automatic — including asks already waiting when the mode is switched on — is answered **once** through the requests engine (never "always"). Sub-agents follow a parent that passes its mode down. Leaving "Approve everything" turns the server-wide switch off. A refused save keeps the previous mode and says "Couldn't save the approval setting." | `ApprovalPresenter` + `engine:requests` `ApprovalModes`/`AutoApprovalSweep`, `RequestsEngine.answerNow(Permission(Once))`; store `oc.autoApprove.<profileId>` (Flutter JSON format, unchanged) |
| Free-model note | "Free model, slower" in the one status line when replies come from OpenCode's free model with nothing else signed in; Dismiss keeps it dismissed for this conversation on this server (`oc.freeModelNoteDismissed.<profileId>`, newest 200). | `catalog()` + `configuredProviders()` (never logged) |

Server support today: the native OC2 gateway reports `sessionShare`, `sessionArchive`, `sessionDelete` and
`sessionFork` **false**, so on OC2 the menu shows Find, Details, Rename, Continue with… and Copy transcript; the other
rows appear on a server that reports them (tested with fakes). See "Integrator" below.

## Catalog rows

Covered (tests named after them):

- **B-CHAT-018** project name from a path (trailing slashes, Windows separators) — `ChatHeaderPresenterTest`, `ChatHeaderUiTest`.
- **B-CHAT-019** context fraction tokens/window, unknown when missing — `ChatHeaderPresenterTest`.
- **B-CHAT-057** context levels 70/95, floored and clamped — `ChatHeaderPresenterTest`, `ChatHeaderUiTest` (Details sheet words).
- **B-CHAT-020** the conversation menu offers "Continue with…" (native: this server's models) — presenter + screen tests. The "link lines on both sides" part needs agent hand-off (phone agents), not in the native app: partial.
- **B-CHAT-015** free-model dismissals per server, bounded, key swept on server deletion (the key is the ordinary `oc.<what>.<id>` shape that `ProfileKeyRule` / `DefaultProfileScope` sweep; the sweep itself is not re-tested here).
- **B-STATE-099** approval setting under the profile-scoped key, round-trips, refused write keeps the last choice, malformed storage reads as none — `ApprovalPresenterTest`.
- **B-AGENT-221/223/224/225** nothing auto-answered without a setting; inheritance through a parent; turning off makes later asks wait; switching on answers waiting asks; approve-everything asks first — `ApprovalPresenterTest`, `ChatHeaderUiTest`.
- Copy transcript masking (SEC-13; cited as B-CHAT-111 in the test name for the copied-transcript surface).

Still missing:

- **B-CHAT-007/008/011 running background tasks chip** — no native engine parses task notices yet (`turnfacts` named in the brief does not exist on this base or any remote branch). Not built; nothing fake shipped.
- **B-CHAT-108 skill conversation creation** — the native OC2 gateway has no skills listing or skill-session call (`skills()` is the default `Unavailable`). Blocked at the protocol layer (outside this write set).
- **B-CHAT-124 web search review flow** — no native `WebSearchGateway` implementation and `webSearch = false`. Blocked at the protocol layer.
- **B-CHAT-066/067 model chip naming** — owned by the composer unit (the Model button already names agent · model · level).
- Separate agent chip: the composer's Model button already carries the agent (Flutter's combined label); no second chip added.
- Menu rows **Files, Git, Review changes, Timeline, Subagents, Compact**: no native routes exist on this base (`ui/routes` has none); add the rows when the Files/Git/Review units land their routes (one `HeaderAction` + one `when` arm each).

## Tests and results

| Command | Result |
|---|---|
| `:feature:chat:desktopTest` | 154 tests, 0 failures (new: `ChatHeaderPresenterTest` 18, `ApprovalPresenterTest` 8) |
| `:quality:test` | 58 tests, 0 failures (new: `ChatHeaderUiTest` 10 screen tests by visible text, incl. Arabic; Konsist K1–K4 pass) |
| `:ui:kit-chat:desktopTest` | 109 tests, 0 failures |
| `:androidApp:testPreviewDebugUnitTest` | 11 tests, 0 failures |
| `:androidApp:assemblePreviewDebug` | BUILD SUCCESSFUL |

Not run: live OC2 server, emulator or device. The screen tests live in `:quality` because the screen needs the kit's
redactor and feature modules may not name `:core:security`, even in tests (module rule).

## Files

New: `feature/chat/.../header/{world,presentation,approval,freemodel,ui}/*`, `ui/kit-chat/.../header/KitChatHeaderParts.kt`
(⋯ button + menu, clipboard effect, `rememberKitMask`), `ui/words/.../strings_chat_header.xml` (EN + AR),
`androidApp/.../graph/ChatHeaderWiring.kt` (prefs-backed approval and free-note stores),
`quality/.../ChatHeaderUiTest.kt`.

Small edits to shared chat files (single-owner library, minimal): `ChatScreen.kt` (+`header` parameter: the bar and
overlays; `rowChips` into the composer chrome), `ChatEntries.kt` (+ the header ViewModel), `ComposerHost.kt`
(+`ComposerChrome.rowChips`, defaulted), `BasicComposerHost.kt` (one line: `chrome.rowChips()` in the controls row).

## Deviations

- **Archive/Delete "with undo":** there is no unarchive call (Flutter's `sessionUnarchive` is false too) and Flutter's
  chat menu asks first rather than offering Undo. Native follows Flutter: confirm first, run, then leave.
- **Auto-approval runs while the conversation is open** (the sweep lives with the chat entry), not connection-wide as
  in Flutter. Moving the sweep to the connection graph belongs to the requests engine owner.
- **Continue with…** offers only this server's models (Flutter also offers agents on the phone with a hand-off record).
- Copy confirmation relies on Android's own clipboard notice; no extra "Copied" line.
- New strings sit in `strings_chat_header.xml`; the `ui:words` owner may move them.

## Integrator

- Nothing to add to `androidApp/build.gradle.kts`: everything is in `:feature:chat`, `:ui:kit-chat`, `:ui:words`.
- When OC2 share/archive/delete/fork are implemented and their capability flags turned on in `Oc2Gateway`, the rows
  appear with no header change (OC2 `deleteSession` is already implemented but `sessionDelete` is false).
- Merge note for the other chat units: `ComposerChrome` gained a defaulted `rowChips` parameter, and `ChatScreen` a
  defaulted `header` parameter; both are source-compatible.
- Local build note: Maven Central rate-limited this container (HTTP 429); a user-level Gradle init script (not
  committed) put Google's Maven Central mirror first.
