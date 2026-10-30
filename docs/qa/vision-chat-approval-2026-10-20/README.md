# Chat with a question (vision slice, 2026-10-20)

Finish line: when the agent asks before acting, the chat shows one card in
place that names the action and its target as a question, shows exactly what
it will run or touch, says how long it has waited, and answers in one tap;
the composer says "Waiting for you" with no glow or Stop. Non-goal: the Inbox,
server sheet, notification and widget rows (same request model, their own
slices).

Pick (owner, vision page): **A + B's file list**. Concept images:
`/home/eslam/Storage2/launch-footage/screen-concepts/b1-chat-approval/`.

## Technical-honesty audit (written before editing)

Sources read: `docs/opencode2-protocol-notes.md` §7, the OpenCode source in
`~/Storage/Code/opencode-abort-fix-20260914` (core `tool/bash.ts`, `edit.ts`,
`write.ts`, `apply-patch.ts`; OC1 `opencode/src/tool/shell.ts`, `edit.ts`,
`write.ts`, `apply_patch.ts`), `docs/qa/FQ3c-2026-10-08/permission-classification.md`
(2.0.10 stable facts), and the app mappers: `lib/state/connection/permissions.dart`
(`_handlePermissionV2`, `_handlePermission`, `_handleLegacyPermission`),
`lib/api2/gateway_mappers.dart`, `lib/paseo/mappers.dart` (`paseoPermission`),
`lib/codex/gateway.dart`, `lib/acp/` (not wired to a chat).

What each server really sends in a permission request:

| Source | action | what it touches | "always" pattern | diff | why |
|---|---|---|---|---|---|
| OpenCode 2 stable (2.0.x) shell | `shell` (beta: `bash`) | `resources: [command]` | `save`: prefix pattern (`printf *`, `npm install *`); beta: the command | none | `message?` |
| OpenCode 2 edit / write | `edit` | `resources: [path]`, location-relative | `save: ["*"]` | none (no metadata) | `message?` |
| OpenCode 2 apply_patch | `edit` | `resources: [every path]` | `save: ["*"]` | none | `message?` |
| OpenCode 1 bash | `bash` | `patterns`: command parts, `metadata.command` | `always`: prefix (`npm install *`) | none | none |
| OpenCode 1 edit / write | `edit` | `patterns: [relative path]`, `metadata.filepath` | `always: ["*"]` | `metadata.diff` | none |
| OpenCode 1 apply_patch | `edit` | `patterns: [relative paths]` | `always: ["*"]` | `metadata.files[]`: `relativePath`, `type` (add/update/delete/move), `additions`, `deletions` | none |
| Paseo (Claude Code, Pi; phone agents) shell | mapped `bash` | `metadata.command`, `cwd` | the command, only when the provider suggested a rule | none | title/description |
| Paseo edit / write | mapped `edit` | `metadata.filePath` (may be absolute) | the path, only when suggested | `metadata.diff` (write: whole new file) | title/description |
| Codex | `shell` / `patch` | command / every change path | none (`persistentPermissionGrants: false`) | per-file text in one string | `reason` |
| ACP client (`lib/acp/`) | - | - | - | - | not wired to a chat: nothing to show |

Element → source → verdict:

| Element of the pick | Source | Verdict |
|---|---|---|
| Card in the chat, no sheet | already in place over the composer (`attention_card.dart`) | **Keep** |
| Question title naming action + target, "Run npm install in neon-breaker?" | action from `permission`; the command from `commandPreview`; the project from the chat's own folder | **Build**. The command goes in the title only when it is one short line; a long one reads "Run a command in neon-breaker?" with the command in the block. Paseo's `cwd` names the folder when it differs. |
| "Edit notes.txt?" | the single path in `patterns`/`resources` | **Build**: "Edit notes.txt?"; several paths: "Edit 5 files?" |
| "Delete 3 old build files?" (B) | only OC1 apply_patch says `type: delete` per file | **Build only from OC1 `metadata.files`** ("Delete 3 files?" when every file is a delete, "Create" when every file is new). OC2, Paseo and Codex never say it: they read "Edit". |
| B's list of exactly what it touches | `patterns`/`resources` for edit/patch, every path | **Build**: max three paths, then "+2 more"; all of them in Details. Today the card showed only the first path and called five files "this file": a real honesty bug, fixed. |
| Diff size per file | OC1 `metadata.files[].additions/deletions`; a single-file `metadata.diff` (OC1, Paseo, Codex) | **Build where present** ("+4 −1"); OC2 sends no diff, so no size: shown as the path alone. |
| For a shell command, what it touches | the command only | **Only the command** in the mono block; no guessed files. |
| Exact thing in one mono block | `commandPreview` / path | **Keep**, now with "Details" inside it. |
| "Needs you · 20 s" | no server timestamp in any request (OC2 `Permission.Request` has none) | **Build client-side, honestly**: the time since this phone first saw the request, kept per connection so it does not restart when the chat is reopened. |
| Allow once (main) / Reject | `reply: once / reject` on every source | **Keep**; primary on the end side, under the thumb. |
| Named "Always allow npm install" | `save` / `always` patterns | **Build**: one pattern `X *` → "Always allow X"; `*` on an edit → "Always allow edits"; a short exact command → "Always allow <command>"; anything else keeps "Always allow commands like this" / "requests like this". Shown only where the server keeps grants (`persistentPermissionGrants`) and the request allows one (`canAlwaysAllow`); never for Codex. |
| Details | the request sheet (`showPermissionDetails`) | **Keep** |
| "· from this phone" in A's block | the profile kind | **Leave out**: it repeats the header's server and is not about this request. |
| Header pill "Needs you · 20 s" (A) | - | **Leave out**: the card says it right under it (a repeat). |
| Composer "Waiting for you", no glow, no spinner | the session has a waiting request of its own | **Build**: placeholder "Waiting for you", no glow, Send instead of Stop while blocked (Reject is the way to say no; both servers end the turn on Reject). |
| "Use Shell" tool words | OC2 stable's action is `shell`; the app only knew `bash` | **Fix**: `shell` reads as a command everywhere the request is worded. |
| Spinner while blocked | - | **Removed** with the Stop ring. |
| Model chips while blocked | - | already gone (vision-chat slice). |

Not available, so not shown: why the agent asks for an OC1 or OC2 shell or
edit (no field; OC2's optional `message` is shown when present), a diff for
OpenCode 2, file kinds for anything but OC1 apply_patch, the request's true
server-side age.

## What changed

- **Question look** for the request cards in the conversation (permission,
  question, form): `KitRequestCard.ask(look: KitRequestLook.question)`. Calm
  `surface1` card with a .5 attention line and a 4 dp attention edge at the
  start (no brown fill, no ring), "● Needs you · 20 s" counting seconds for the
  first minute then minutes, the ask at the title size, what it runs or touches
  in one bordered mono block with Details inside it, Reject | Allow once (the
  primary at the end; stacked, at the bottom), the named grant centred and
  underlined. Lists, sheets and team screens keep the standard look.
- **The ask from the request** (`lib/ui/screens/chat/permission_ask.dart`):
  "Run npm install in neon-breaker?", "Run a command in neon-breaker?" for a
  long command without a family, "Edit notes.txt?", "Edit 5 files?",
  "Create/Delete N files?" only from OC1 `metadata.files`, "Read settings.json?".
  B's list: every path (three in place, "+2 more"), the file glyph, "+4 −1" only
  where a diff or `additions/deletions` exists; a shell command shows only
  itself. "Always allow npm install" / "Always allow edits" / "Always allow
  every command" from the saved pattern; the confirm names the same thing.
- **Fixes found on the way**: OC2 stable's `shell` action read "Use Shell"
  (now a command everywhere it is worded: card, sheet, announcement, icon); an
  edit of five files showed only the first path and said "this file" (now all
  paths, in the card and in the list/sheet summary line); the confirm for an
  edit grant named one file while the grant covered every edit.
- **Waiting time** counts from when this phone first showed the request, kept
  per connection (`requestFirstSeen`), so reopening the chat does not restart it.
- **Composer while blocked**: "Waiting for you", no glow, Send/mic instead of
  Stop, the mic as a quiet surface so the card's Allow once is the one solid
  mint. Once an answer is held for Undo, the composer is back to working.
- OpenCode's running `question` step no longer repeats above its card.

Kit additions (other screens unchanged): `KitRequestLook`, `KitRequestTarget`,
`KitRequestCard.targets/look/maxTargetsInPlace`, `kitRequestFrame(edge:)`,
`KitTokens.needsYouDotSize/needsYouEdgeWidth/needsYouLineAlpha`,
`KitSinceTicks.secondsThenMinutes`, `KitSince.shortAge`, `KitComposer.quiet`.
`kit_request_card.dart` was split into two parts (`kit_request_question.dart`,
`kit_request_shape.dart`) to stay under the 1,500-line ratchet;
`permission_ask.dart` keeps `permission_sheet.dart` under the chat's 800.

## Visual passes against concept A (+ B's list)

1. First render: card worked but the 4 dp ring read as a muddy second outline,
   the border was too faint, the mono block was 63 dp tall around one short
   line, the empty-composer mic was solid mint (two mint actions), "five
   files" hid four of them. Fixed: no ring, stronger line, Details underlined.
2. Block rows at their link's height (48 dp), list rows tighter; title falls
   back to the headline from 1.3 text; Arabic fonts in the capture; the
   project name no longer breaks at its hyphen ("neon-breaker" stays whole in
   RTL); stacked answers keep Allow once at the bottom.
3. File glyphs on paths (B), the composer returns to working as soon as an
   answer is held, the question card takes the same look and its running
   step stops repeating "Waiting for you"; large text puts size and Details on
   their own line (no overflow at 320 dp × 2.5).

Kept as is, on purpose: no header pill (the card says it), no "· from this
phone" (repeats the header), Reject stays the kit's filled secondary, the
two-row composer and the transcript styling belong to other slices.

## Evidence

`/home/eslam/Storage2/launch-footage/vision-chat-approval/` (412×915 dp at 2x,
one phone screen each): `command-dark.png`, `command-light.png`,
`edit-one-file-dark.png`, `edit-five-files-dark.png`, `question-dark.png`,
`allowed-undo-dark.png`, `small-320-text2-dark.png`, `arabic-rtl-dark.png`;
before: `before/`. Produced by `tool/capture/vision_chat_approval_test.dart`.

## Not done honestly

- At 320 dp with 2x text the card keeps to the kit's 45 % cap so Allow once
  stays in sight: the ask drops "in neon-breaker" (the header names it) and
  reads whole, and the command block scrolls under it (a larger share pushed
  the answers off screen).
- No server sends the time a request was asked; the age is this phone's.
- Paseo edits name absolute paths; they read from the project only when inside
  the chat's folder.
