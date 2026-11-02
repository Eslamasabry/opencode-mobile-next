# P12-PROMPTS — saved prompts, slash commands, tasks (2026-11-03 parity push)

Branch pushed: `claude/prompts-stash-parity-ti58t9` (this session can push only that branch; the brief named
`native/p12-prompts-2026-11-03` — the integrator should take the commits from the pushed branch).

## Status: implemented, committed, pushed. NOT compiled, NOT tested, NOT on a device

The session's network policy refuses `dl.google.com` (HTTP 403 from the egress proxy). That host serves both the
Android SDK (`sdkmanager`) and Google's Maven repository (`google()`, so the Android Gradle plugin 9.3.3). With no AGP
the native Gradle build cannot configure at all, so **no Gradle task ran in this session** — not `desktopTest`, not
`:quality:test`, not `:androidApp:assemblePreviewDebug`. Maven Central was also returning 429 to this session.

All code was written against the current source (signatures read from the kit, engine, routes and shell) but it has
never been compiled. Treat every test below as **written, not run**. Fix for the next run: add `dl.google.com` (and
`maven.google.com`) to the environment's allowed domains.

## Journeys implemented (reachable once it compiles)

| Journey | How to reach it | Engine / data |
|---|---|---|
| Saved prompts: list newest first, search | Chat top bar → **Saved prompts** | `PromptShelf` (`:engine:composer`) over the app prefs + `prompt-stash-attachments-v1` vault |
| Save a new prompt | Saved prompts → **Save prompt for later** → field → **Save** | `PromptShelf.stash` (50 cap, never evicts) |
| Edit a prompt | **Edit** under the prompt | delete-with-undo, then stash a copy with the same date, files and references; undone if the new save fails |
| Delete with Undo | **Delete** under the prompt → "Saved prompt deleted · Undo" bar | `PromptShelf.deleteWithUndo` |
| Insert into the composer | Tap the prompt or **Restore to draft** | `ComposerInbox` → `ComposerChrome.pendingInsert` → `BasicComposer` (replaces an empty field, else appends on a new line); the saved copy stays |
| Slash commands from the server | Saved prompts → **Slash commands and agents** | `ServerOperationsGateway.commands()`, gated on `ServerCapabilities.slashCommands`; tap = `/name ` in the composer |
| Agent task list | Saved prompts → **Tasks** | `SessionGateway.todos()`, gated on `sessionTodos`; progress, states, priority, "Show unfinished only" |
| Flutter data | — | Same keys (`oc.promptStash.<profile>.<id>`, `oc.promptHistory.<profile>`, `oc.stashAttachmentVault.<profile>`) and vault folder, read by the engine unchanged |
| Server removed | — | Every shelf use first runs `PromptShelf.clearForProfile` for ids left in shelf keys with no live profile, and deletes stash files whose owner hash is no live profile |

On OpenCode 2 today, `Oc2Gateway` reports `slashCommands = false` and `sessionTodos = false` and returns Unavailable
for both, so those two pages show the plain "Slash commands and agents isn't available on <server>" / "Tasks isn't
available on <server>" state. They fill in as soon as the protocol unit implements `GET /api/command` (documented in
`docs/opencode2-protocol-notes.md:454`) and a todo read. No protocol file was edited (not in this write set).

## Files

- New module `native/feature/prompts/**`: `PromptsEntries`, `presentation/` (`SavedPromptsStore`, `ShelfSavedPrompts`,
  `SavedPromptsUi`, `SavedPromptsViewModel`, `ConversationLists` + `RegistryConversationLists` +
  `PromptsGraphAccessors` (ConnectionScope), `ServerListsViewModels`), `ui/` (`SavedPromptsScreen`,
  `ServerListsScreens`), tests.
- New `native/ui/routes/.../PromptsRoutes.kt` (`PromptsRoute.Saved|Commands|Tasks(ref)`).
- New `native/ui/kit-chat/.../composer/ComposerInbox.kt` (app-scoped one-slot hand-back; snapshot state).
- New `native/feature/chat/.../composer/ChatPromptsDoor.kt`.
- New `native/androidApp/.../graph/PromptsWiring.kt` (PromptShelf, SavedPromptsStore, orphan retirement).
- Smallest edits to existing files: `androidApp/build.gradle.kts` (+1 line), `MODULE_OWNERS` (+1 line),
  `feature/chat/ChatEntries.kt` (inject inbox + navigator, pass the door), `feature/chat/ui/ChatScreen.kt` (optional
  `prompts` parameter, one top-bar action, `pendingInsert`/`insertDone` wired), `feature/chat/composer/BasicComposerHost.kt`
  (one `LaunchedEffect` that applies an insert once).

All copy is existing `:ui:words` strings (English + Arabic already present); `:ui:words` was not edited.

## Catalog rows covered by written tests (not run)

| Row | Test |
|---|---|
| B-STATE-140 | save → restart (new shelf over the same stores) → listed; other profile never sees it; `clearForProfile` removes it; unreadable shelf named, not empty; screen lists by words; Arabic page |
| B-STATE-085 | delete durable at once + Undo restores id/date; restore keeps the saved copy and targets one conversation; edit keeps date and order; Undo bar on screen |
| B-CHAT-088 | 50 saved never evict, 51st refused with the plain "full" line; empty/unreadable screen copy |
| B-STATE-135 | a refused write during edit keeps the original |
| B-STATE-141 | a removed profile's shelf refuses new prompts |
| B-NAV-051 | commands listed, `/name ` inserted, return to the same chat (no duplicate); not-on-server state; offline + Try again; screen tests |
| B-CHAT-065 | bounded states, optional priority, progress ignores cancelled, unfinished filter; screen shows "1 of 2 done" |

Files: `feature/prompts/src/commonTest/.../SavedPromptsTest.kt`, `ServerListsTest.kt`,
`feature/prompts/src/desktopTest/.../PromptsScreensTest.kt`.

## Not done (still missing)

- **Background-work line in turns and running tasks in the chip row** (B-CHAT-007, B-CHAT-063, B-CHAT-111,
  B-AGENT-036/151): not started. They need transcript/turn data from the kit-chat and transcript owners plus a
  running-work source; time went to the saved-prompts journey and to the blocked build.
- **"Save prompt for later" from the composer's own text** (Flutter `+` menu, clears the composer): the native
  composer has no `+` menu and its draft is owned by the composer/drafts units; here a prompt is written on the Saved
  prompts page instead. The empty-state body ("Choose Save prompt for later in the + menu…") was therefore not used.
- **Attachments when inserting**: insertion is text only; saved files and references stay on the saved copy (shown as
  "2 attachments"). Restoring files needs the composer's attachment API (`replaceAttachments`).
- **Older-drafts migration notice** (B-STATE-087) and B-STATE-134/136/137/139 vault rows: engine behaviour, already
  covered by `:engine:composer` tests; no new UI for them.
- B-SEC-172 ordering: see below.

## Integrator actions

1. Allow `dl.google.com` for the build machine, then run `./gradlew :feature:prompts:desktopTest :feature:chat:desktopTest
   :quality:test :androidApp:assemblePreviewDebug` and fix any compile slips (none known, none ruled out).
2. Profile deletion (B-SEC-172): add `PromptShelf.clearForProfile(id)` to the preview deletion command in
   `PreviewRuntime` before the secret is deleted (I could not edit that file). Until then the shelf retires a removed
   server's keys and files the next time Saved prompts is used.
3. Protocol unit: implement `Oc2Gateway.commands()` (`GET /api/command`) and a todo read, and set the two capability
   flags; the pages need no change.
4. `ComposerInbox` lives in `:ui:kit-chat` (the lowest module both features may see). If the kit-chat owner prefers,
   move it to an engine; only the import changes.
