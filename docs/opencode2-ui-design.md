# OpenCode 2 UI design — locked decisions

Status: **LOCKED** for the v2 port (phases 2–3, with a phase-4 preview).
Date: 2026-08-29. Authoritative inputs: `docs/opencode2-protocol-notes.md`,
`docs/opencode2-port-matrix.md`, `docs/design-inspiration.md`. Build agents
implement what is written here; deviations require editing this file first.

Conventions used throughout:

- **Tokens**: colors come from `Theme.of(context).colorScheme` and
  `AppTheme.successOf(theme)`; mono text uses `AppTheme.monoFamily`
  (`'AppMono'`). Surfaces follow the app's existing ladder: sheets and
  composer on `surfaceContainerLow`, chips/inputs on `surfaceContainerHigh`,
  pinned bars on `surfaceContainerHigh` with `elevation: 6` (the
  `model-picker-apply-bar` recipe). Radii: 24 for sheets/composer, 14 for
  chips/bubbles, 12 for inline cards.
- **Type**: section labels `labelLarge`, meta lines `labelSmall` +
  `onSurfaceVariant`, body `bodyMedium`, sheet titles `titleMedium`.
- **Widget keys**: kebab-case `Key('...')`/`ValueKey('...')`, matching the
  existing `settings-category-*` / `model-picker-apply-bar` style. Every
  interactive element specced here names its key.
- **Motion**: `TweenAnimationBuilder` / implicit animations only.
  **flutter_animate is banned.** Every animation is skipped when
  `MediaQuery.disableAnimationsOf(context)` is true (the `EntranceReveal`
  and `_ContextMeterLine` discipline). Standard curve `Curves.easeOutCubic`,
  durations 150–400 ms.

Mobbin citations follow the `docs/design-inspiration.md` format.

---

## 1. Connection & auth flow (v2 password step)

**Locked: the existing server-profile editor and Test-connection verdict
absorb v2 auth; no new screens.** The profile editor
(`lib/ui/screens/servers_screen.dart`) already has Name / URL / Username /
Password fields, a `Test connection` button, and a probe verdict row —
v2 changes what the probe does and how the password field behaves.

### Protocol detection

- `probeServerConnection` (`lib/api/server_probe.dart`) becomes a
  **two-step flavor probe**: try `GET /api/health` with Basic
  `opencode:<password>` first; if it 404s (path missing), try
  `GET /global/health`. Result gains a `flavor` field:
  `ServerFlavor.v2 | ServerFlavor.v1 | ServerFlavor.unknown`.
- The Test-connection verdict row keeps its success/failure anatomy and adds
  the flavor as the headline: **"OpenCode 2 · v0.0.0-beta-18600"** or
  **"OpenCode 1 · v0.3.x — limited feature set"** (labelSmall subtitle:
  "This app targets OpenCode 2; some features are unavailable on v1
  servers" — see §7). Key: `server-probe-verdict` (existing widget, add the
  key). 503 `service_starting` during probe → "Server is starting — retry in
  a moment" with an auto-retry after `retry-after`.
- The saved `ServerProfile` caches the detected flavor + version; the
  connection layer re-verifies on every cold connect via `/api/health` and
  updates the cache. `GET /api/experimental/migration/v1` returning
  `running` puts a blocking full-screen progress state on connect
  (`migration-progress-screen`), per protocol notes §11 Misc.

### When the password field appears — error taxonomy

The password field is **always visible** in the editor (it already is), but
first-run copy changes: label "Server password", helper text "Printed by
`opencode2 serve` at startup (`server password …`)". The probe maps failures
to four distinct verdicts (extend `_explain`):

| condition | verdict copy | follow-up behavior |
|---|---|---|
| transport error / timeout | "Could not reach the server. Check the address and that `opencode2 serve` is running." | none |
| `/api/health` 404 but `/global/health` 200 | "This is an OpenCode 1 server." + limited-support note | save allowed; app runs in v1-gated mode (§7) |
| `/api/health` 401, password empty | "This server requires its serve password." | scroll to + focus password field, shake-free; key `server-password-field` |
| `/api/health` 401, password present | "Password rejected. Copy the current `server password` line from the server output — it changes on every restart unless `OPENCODE_PASSWORD` is set." | select-all the password field |

### Paste-first password UX

The per-run password is a random 32-byte base64url string nobody types.
Locked treatment (patterned on paste-forward code entry —
[Monzo enter-code](https://mobbin.com/screens/08650bfd-d0fa-4f4e-8f79-97a0d7fa4692) —
and password reveal toggles —
[Meta AI Wi-Fi password](https://mobbin.com/screens/fa319866-3c2a-4f9f-92d0-062cae225ef0)):

- Obscured `TextField`, `AppMono` when revealed, with **two suffix icons**:
  a paste button (`Icons.content_paste_rounded`, key
  `server-password-paste`) that reads the clipboard, trims whitespace/the
  `server password ` prefix if present, and fills the field; and a show/hide
  eye toggle (key `server-password-visibility`). Paste is the primary
  affordance and sits closest to the field edge.
- `autocorrect: false`, `enableSuggestions: false`,
  `keyboardType: TextInputType.visiblePassword`.
- **QR / `?auth_token` path: deferred.** The server prints no QR today; we
  will not build a scanner speculatively. The transport must still support
  `?auth_token=` internally (SSE/WS contexts), so the QR pairing flow
  (cf. [Comet add-device sync code + QR](https://mobbin.com/screens/be6cec44-92b3-489e-a896-0377573be8fb))
  can be added later without protocol work. Do not stub UI for it now.

### Storage & mid-session re-auth

- **Keep the existing mechanism unchanged**: password in Android Keystore
  via `flutter_secure_storage` (`pw.<profileId>`, `lib/state/profiles.dart`),
  metadata in `SharedPreferences`. It already matches protocol notes §1's
  "secure storage" requirement. Username field stays (defaults to
  `opencode` when blank — `server_probe.dart` already does this).
- **Mid-session 401** (password rotated because the server restarted): the
  connection layer maps any `UnauthorizedError` to a persistent
  `connection_status_banner.dart` state — banner text "Server password
  changed — reconnect", action button "Update password" (key
  `banner-update-password`) that opens the profile editor with the password
  field focused and a fresh Test connection primed. Never a modal; never
  silent retry loops (Basic auth cannot self-heal). SSE stream 401s route to
  the same banner instead of the generic reconnect backoff.

---

## 2. Forms renderer (replaces questions)

**Locked: one shared `FormRenderer` widget** (`lib/ui/widgets/form_renderer.dart`)
that renders `Form.Info` (§8 of protocol notes) everywhere forms appear —
chat, Requests screen, notifications deep-link, and integration connect
(§8 below reuses it for `method.form`). The v1 `_QuestionSheet`
(`lib/ui/screens/requests_screen.dart:405`) donates its interaction
skeleton and dies.

### Layout: sheet vs full-screen

- **≤ 4 declared fields → modal bottom sheet** (`showModalBottomSheet`,
  `isScrollControlled: true`, top radius 24, `surfaceContainerLow`).
- **≥ 5 declared fields, or any field with `description` longer than
  ~140 chars → full-screen dialog** (`MaterialPageRoute`,
  `fullscreenDialog: true`).
- Count **declared** fields, not currently-active ones — a form must not
  jump between presentations as `when` conditions toggle.
- Both share the same scrollable field column and the same pinned reply bar.

### Header

Title (`titleMedium`) + optional origin line (`labelSmall`,
`onSurfaceVariant`): "Asked by the agent in this session" or, for
`sessionID == "global"` (MCP elicitation), "Asked by an MCP server". Key:
`form-sheet` on the sheet root, `form-title`.

### Field type → M3 control mapping

| Field | Control | Details |
|---|---|---|
| `string`, no options | Outlined `TextField` | `maxLength` shows the M3 counter; `format: email/uri` sets keyboard type; `format: date`/`date-time` renders a read-only field opening `showDatePicker` (+`showTimePicker`); `placeholder` → hintText; `default` prefills. Multiline (`maxLength` > 120 or no maxLength) gets `minLines: 1, maxLines: 5`. |
| `string` with `options`, ≤ 4 options | Radio card group | `RadioListTile` in a rounded `surfaceContainerHigh` container, `option.description` as subtitle — the KOHO single-question pattern ([KOHO rental agreement step](https://mobbin.com/screens/34efcefa-7a2e-4dca-af88-f5f311aa6cb7)). |
| `string` with `options`, ≥ 5 | `DropdownMenu` | full-width; `option.description` in the menu entry's subtitle when present. |
| `string` `custom: true` | above + trailing "Other…" | selecting Other reveals a text field (same `when` animation). |
| `number` / `integer` | Outlined `TextField` | numeric keyboard; `integer` adds digits-only input formatter; `minimum`/`maximum` validated inline ("Must be between 1 and 10"). No steppers, no sliders. |
| `boolean` | `SwitchListTile` | `title` + `description` subtitle; `default` sets initial. |
| `multiselect`, ≤ 8 options | `FilterChip` wrap | 8 px spacing; selected count caption when `minItems`/`maxItems` set ("Pick 1–3"). |
| `multiselect`, ≥ 9 options | `CheckboxListTile` list | inside the rounded container. |
| `multiselect` `custom: true` | + "Add your own" text field | submitting adds an input chip. |
| `external` | Action card | `surfaceContainerHigh` card: `title`, `description`, trailing `Icons.open_in_new_rounded`, caption "Opens in your browser". Launches `url` via url_launcher. Contributes **no** answer key; never blocks validity. |

Every field: `title` as label, `description` as `bodySmall` helper below the
control, `required` marked with the M3 asterisk convention. Field keys:
`form-field-<key>`.

### `when` conditional behavior

- Evaluate `when` (ALL conditions; `eq` on multiselect = includes;
  unanswered reference = false) against live answers on every change.
- **Animate with `AnimatedSize`** (240 ms, easeOutCubic) wrapping each
  field slot; an inactive field renders `SizedBox.shrink()` inside it, so
  fields slide open/closed in place — no fade library, no overlay.
  Reduced-motion renders instantly. Precedent for cascading conditional
  fields: [Meetup report form](https://mobbin.com/screens/88db91fa-c6b2-4592-a6a8-ef69171a257b)
  (category dropdown → sub-reason → free text).
- Deactivated fields **keep their draft answer in state but are excluded
  from the reply payload** and from validation, matching the protocol rule
  "inactive fields are neither required nor answerable".

### Validation & errors

- Local validation mirrors the schema (required-when-active, min/maxLength,
  pattern, min/max, minItems/maxItems) and renders inline `errorText` under
  the offending control on submit attempt — the inline-red-under-field
  pattern ([Posh event form](https://mobbin.com/screens/a045f07c-8a2b-4f3b-91c0-61a73b24da0b)).
  The submit button is enabled optimistically; a failed local pass scrolls
  to the first error.
- Server 400 `FormInvalidAnswerError` → error banner pinned above the reply
  bar (key `form-error-banner`), form stays open. 409
  `FormAlreadySettledError` → toast "Already answered elsewhere", close.

### Reply / cancel — pinned bar

Reuse the pinned apply-bar recipe (`Material` + `elevation: 6` +
`SafeArea`, exactly like `model-picker-apply-bar`), key `form-apply-bar`:

- `FilledButton` "Send answers" (key `form-submit`; spinner-in-button while
  posting, the `_PermissionDialogState` pattern).
- `TextButton` "Dismiss" (key `form-cancel`) → confirm dialog "Dismiss this
  request? The agent continues without your answers." → `POST .../cancel`.
  Copy donated from `_QuestionSheet._reject`.

### Surfacing mid-chat

- A `form.created` for the open session inserts an **inline attention card
  in the transcript** at arrival position (component `FormRequestCard`, key
  `form-request-card-<id>`): `Icons.fact_check_outlined` in primary,
  form title, "N questions" caption, trailing `FilledButton.tonal`
  "Answer" that opens the renderer. The card is compact (72 px min height,
  `_ActiveSetupCard` proportions) and flips to a checked "Answered" /
  "Dismissed" quiet state on `form.replied`/`form.cancelled` — it never
  disappears (transcript truth).
- The chat screen auto-opens the renderer only when the app is foregrounded
  on that session and no other sheet is up (same rule the permission dialog
  uses today).
- **Requests screen** lists pending forms of all sessions (replacing
  question tiles) from `GET /api/form/request` + per-session lists; global
  (`"global"`) forms appear here only, under a "Server requests" section.
- **Notification shade**: form alerts reuse `CodingAlert` with
  `quickReply: false` always — multi-field forms cannot be answered from a
  RemoteInput. The notification's single action "Answer…" deep-links to the
  form renderer. (Exception intentionally rejected: even one-field forms go
  through the app — keeping one path is worth more than the rare saved tap.)
  Reconnect hydration: poll `GET /api/form/request` after every SSE
  reconnect — form events are ephemeral.

### What the questions code donates

From `_QuestionSheet`: the `Set<String>`-per-field selection state, the
custom-answer text controller lifecycle, busy/error submit handling, the
reject confirm dialog. From `requests_screen.dart`: tile → sheet
navigation and per-tile pending badge. Delete `PendingQuestion`,
`answerQuestion`/`rejectQuestion` paths after phase 3.

---

## 3. Permission sheet v2

**Locked: the permission prompt graduates from `AlertDialog` to a modal
bottom sheet** (`lib/ui/screens/chat/permission_sheet.dart`, replacing
`permission_dialog.dart`), because v2 requests carry more structure
(action + N resources + save patterns + source + message). Pattern
grounding: consent sheet presenting exactly-what-is-shared in a scoped card
with a stacked choice pair
([CLEAR "Share with TSA" sheet](https://mobbin.com/screens/509bc82c-0ab9-43bc-afda-33c49e175746))
and decline-with-consequence confirmation
([Oura "Continue without sharing"](https://mobbin.com/screens/a0dbeace-7769-4cbc-9791-f7a43d3d59ac)).

### Anatomy (top → bottom), key `permission-sheet`

1. **Header**: `Icons.admin_panel_settings_outlined` + action title from the
   existing `permissionRequestTitle(...)` mapping in
   `lib/ui/permission_presentation.dart` (extend for v2 `action` strings:
   read/edit/bash/webfetch/…). `titleMedium`.
2. **Context line** (`bodyMedium`): `request.message` when present, else
   "The agent wants to use `<action>`."
3. **Resources card**: rounded-12 `surfaceContainerHigh` container listing
   `resources[]`, one row each — leading glyph by action (terminal icon for
   bash, file icon for read/edit, globe for webfetch), `SelectableText` in
   `AppMono` `bodySmall`. More than 6 rows → the card caps at ~200 px and
   scrolls internally. Key `permission-resources`.
4. **Save explainer** (only when `save[]` non-empty): `labelLarge` "Always
   allow would also cover" + save patterns in mono — donated verbatim from
   the current dialog body.
5. **Source chip** (when `source.type == "tool"`): `ActionChip`
   "From tool call" (key `permission-source-chip`) that dismisses the sheet
   and scrolls the transcript to the tool card with `source.id`.
6. **Action triad** (stacked, full-width, in a bottom-pinned bar —
   apply-bar recipe, key `permission-apply-bar`):
   - `FilledButton` **Allow once** — key `permission-allow-once` (primary;
     "safer" default position, same as today).
   - `OutlinedButton` **Always allow** — key `permission-allow-always`.
     Keeps the existing two-step `_confirmAlways` dialog **unchanged in
     copy and structure** (it is exactly right), fed by `save[]` (fallback
     `resources[]`). Adds one line: "Manage saved grants in Settings →
     Saved permissions."
   - `TextButton` **Reject…** — key `permission-reject`.

### Reject-with-message

Tapping **Reject…** does not submit; it expands (same `AnimatedSize`
treatment as form `when`, 240 ms) an inline optional `TextField` — hint
"Tell the agent why, or what to do instead (optional)", 3-line max, key
`permission-reject-message` — and swaps the triad for two buttons:
`FilledButton.tonal` styled with `error`/`errorContainer` **Send rejection**
(key `permission-reject-send`, submits `{reply:"reject", message:<text or
omitted>}`) and `TextButton` **Back**. Empty message is valid. This keeps
the fast path (Allow once) one tap and the thoughtful path two taps, and is
the only place the keyboard ever appears on this sheet.

### Consistency with notifications & Requests

- Notification actions stay **Allow once / Deny / Reply**
  (`CodingAlertAction` in `lib/background/live_background.dart`): Deny maps
  to `{reply:"reject"}` with no message; **Reply**'s RemoteInput text maps
  to `{reply:"reject", message:<text>}` — steering-by-rejection, matching
  the server contract that `message` is shown to the model on reject.
  `connection.dart:_handleCodingAlertAction` keeps its routing role.
- The Requests screen `_PermissionTile` adopts the same triad labels and
  the same expandable reject field; its inline once/always/reject segmented
  actions are replaced by opening this same sheet (one component, three
  entry points: chat auto-present, Requests tile, notification tap).
- After SSE reconnect, pending permissions are re-polled
  (`GET /api/permission/request` — asks are not replayed); the sheet
  auto-dismisses on `permission.replied` from elsewhere with a toast
  "Handled on another device".

---

## 4. Session-level model/agent switching

**Locked: the picker UI stays; only its apply semantics and messaging
change.** In v2 model/agent/variant are session state
(`POST /api/session/{id}/model` / `/agent`), not per-prompt fields.

- `ModelCatalogView` (`lib/ui/widgets/pickers.dart`) is reused as-is:
  search, agent row, provider filter, and the pinned
  `model-picker-apply-bar`. Changes:
  - When opened **with an active session**: apply button label becomes
    **"Use for this session"**; a `labelSmall` line above it reads
    "Applies to this session's next turns." On apply →
    `POST .../model` (and `/agent` if changed). Variant rides inside the v2
    model ref (`{id, providerID, variant}`), so the variant `ChoiceChip`
    row keeps its exact current home in the apply bar. Existing keys
    (`use-model-*`, `model-variant-*`) unchanged.
  - When opened **without a session** (More hub `_ActiveSetupCard`,
    first-run): label **"Use for new sessions"**; the selection becomes the
    client default passed in `POST /api/session` bodies. Seed the default
    from `GET /api/model/default` when the user has never chosen.
- The composer's `composer-model-context` button and the More hub card now
  render **server-authoritative session state** (from `Session.Info.agent`
  / `.model`, updated by `session.model.selected` / `session.agent.selected`
  events) instead of client-only state. A switch made from the TUI shows up
  live.

### Transcript marker rows

New shared component `TranscriptMarker`
(`lib/ui/screens/chat/message_view.dart`), rendering `model-switched`,
`agent-switched`, and `location-switched` messages (and reused by §6 for
compaction-completed). Visual spec — a subtle divider-row, deliberately
quieter than any bubble:

- Full-width `Row`: hairline `Divider` (existing transcript treatment,
  `theme.dividerColor.withValues(alpha: .35)`) — center pill — hairline.
- Pill: `StadiumBorder`, `surfaceContainerHigh`, 1 px `outlineVariant`
  border, padding 10×4; content: 14 px icon + `labelSmall` in
  `onSurfaceVariant`. Icons: `Icons.memory_rounded` (model),
  `Icons.support_agent_rounded` (agent), `Icons.drive_file_move_outline`
  (location).
- Copy: "Model → gpt-5.6-sol · high", "Agent → plan",
  "Moved → ~/Code/other-project" (basename only). `previous` shown only in
  the tooltip/long-press detail, not inline.
- Vertical padding 12 above/below (24 total rhythm). No entrance
  animation — markers appear with the transcript batch.
- Keys: `transcript-marker-<type>-<messageId>`.

---

## 5. Inbox / queued prompts

**Locked composer rule: Send always stays live; tap = steer, long-press =
queue; everything pending shows in ONE strip.**

### Composer while a turn runs

Today `_ComposerSubmit` flips to a Stop button while busy, making send
impossible. v2 replaces that:

- While busy, the submit slot renders **two adjacent controls**: a
  `IconButton.filledTonal` **Stop** (error tint, `Icons.stop_rounded`, key
  `chat-stop-button` — its current styling, new dedicated key) and the
  regular filled **Send** (key stays `chat-send-button`).
- **Tap Send while busy → `delivery: "steer"`** (the v2 default; closest to
  what v1 users experienced) — the prompt interjects at the next step
  boundary.
- **Long-press Send while busy → menu** with two rows: "Send now — steers
  the current run" and "Queue for after this run" (key
  `send-delivery-menu`; menu rows `send-delivery-steer`,
  `send-delivery-queue`). When idle, long-press does nothing special (no
  menu) — delivery is meaningless when idle.
- Precedent for a delivery-conditions choice at send time:
  [Quo schedule-message conditions sheet](https://mobbin.com/screens/2285d30c-b4fc-4f61-92e2-8db13a3cd54f);
  for the composer transforming into a deferred-send state:
  [Apple Messages "Send Later"](https://mobbin.com/screens/d009dfae-11b8-4b72-8d4c-b7910f97fcfa).

### One queue concept: the Pending sends strip

The existing offline `_QueuedPromptsStrip` / `_QueuedPromptBubble`
(`message_view.dart:1309`) generalizes into **`PendingSendsStrip`** — the
single surface for both offline drafts (`QueuedPrompt`, flushed on
reconnect) and server inbox items (`GET /api/session/{id}/inbox`,
`session.inbox.*` events). Same position (above the composer, right-aligned
bubble column), same bubble anatomy (14-radius `surfaceContainerLow` bubble
with `outlineVariant` border), differentiated only by status line + icon:

| kind | icon | status line |
|---|---|---|
| offline draft | `Icons.schedule_rounded` | "Queued — will send when reconnected" (unchanged) |
| inbox, `delivery: steer` | `Icons.bolt_rounded` | "Sending at next step" |
| inbox, `delivery: queue` | `Icons.hourglass_bottom_rounded` | "Waiting for this run to finish" |
| inbox `synthetic`/`compaction`/`move` items | `Icons.auto_awesome_outlined` | "Context update pending" (not editable) |

Keys: `pending-send-<inboxId|queuedId>`. Items dissolve on
`session.inbox.delivered` / `.cancelled` (150 ms fade-out via
`AnimatedOpacity` before removal; instant under reduced motion).

### Bubble tap → actions sheet (key `pending-send-actions`)

- Offline draft: **Edit** (restores to composer — existing
  `_editQueuedPrompt`), **Discard** (existing confirm copy).
- Inbox `user` item: **Send now** (`POST .../steer`) or **Wait for this
  run** (`POST .../queue`) — only the row that flips the current mode is
  shown; **Cancel** (`DELETE .../inbox/{id}`, confirm "Cancel this pending
  message?"). 409 already-delivered → toast "Already delivered", strip
  refreshes. No reorder — the server offers none; do not fake it.
- No edit for inbox items (server items are immutable): Cancel returns the
  text to the composer as a draft — that *is* the edit affordance.

### Merging rule

When the connection is lost with inbox items pending, both kinds coexist in
the strip in arrival order — the user sees one list of "things that will
reach the agent", never two queue UIs. Mission Control's session rows gain a
small `N pending` caption (key `mission-pending-count-<id>`) sourced from
the same state.

---

## 6. New transcript message variants

One new lightweight component plus two reuses. All variants are
**compact-first**: collapsed rows that expand on tap, never competing with
user/assistant bubbles.

### `TranscriptNotice` (new, in `message_view.dart`) — for `synthetic`, `system`, `skill`

Full-width quiet card: rounded-12, transparent fill, 1 px
`outlineVariant.withValues(alpha:.5)` border, padding 12×8. Leading 16 px
icon + `labelSmall` header + collapsed 2-line `bodySmall` preview of
`text`; tap toggles full text (`AnimatedSize`, 200 ms). Keys
`transcript-notice-<messageId>`.

| variant | icon | header |
|---|---|---|
| `synthetic` | `Icons.auto_awesome_outlined` | `description` ?? "Context added" |
| `system` | `Icons.settings_suggest_outlined` | `description` ?? "System update" |
| `skill` | `Icons.electric_bolt_outlined` | "Skill · `<name>`" (name chip in `AppMono`) |

### `shell` messages — reuse `ToolCard`

Add a shell contract to `lib/ui/widgets/tool_card.dart`'s `_ToolContract`
table: header "Shell · `<command>`" (command in `AppMono`, ellipsized),
status treatment identical to tool states — running shows the live spinner
row; `exited` shows an exit badge (`exit 0` in `AppTheme.successOf`,
non-zero in `error`, `timeout`/`killed` in `tertiary`) — and the existing
`_ToolOutputPreview` renders `output.output` (mono, ~6-line preview,
"Show all" expands; `output.truncated` appends a "truncated" caption).
Built from `session.shell.started/ended` events live, `shell` messages on
hydration. Key `shell-card-<shellID>`.

### `compaction` messages — marker + card hybrid

- `running`: `TranscriptMarker`-style pill "Compacting conversation…" with
  a 12 px inline `CircularProgressIndicator(strokeWidth: 2)` replacing the
  icon. Ephemeral `session.compaction.delta` text is **ignored** (no
  streaming summary — noise).
- `completed`: a `TranscriptNotice` with `Icons.compress_rounded`, header
  "Context compacted", preview = first lines of `summary`; expanded state
  renders the summary through the existing markdown widget
  (`lib/ui/widgets/markdown.dart`). The existing `_EarlierMessagesPill`
  sits directly above it unchanged — compaction does not hide messages.
- `failed`: same notice in error tint, header "Compaction failed", body
  `error.message`.
- Keys: `compaction-<status>-<messageId>`.

`agent-switched` / `model-switched` / `location-switched` are §4's
`TranscriptMarker`. Unknown future message types render as a generic
`TranscriptNotice` ("Server message") — never crash, never drop silently.

---

## 7. Feature gating

**Locked rule, by surface class** (evaluated from the connection's detected
`ServerFlavor`, exposed as `conn.capabilities`):

1. **Nav destinations & More-grid tiles** whose entire screen has no
   backend → **hide entirely.** The grid reflows; an all-dead tile is
   noise, and the More grid is our shop window. (Yes, hiding nav is fine —
   these are power surfaces, not core tabs.)
2. **Settings rows** → **show disabled with explainer subtitle** ("Not
   available on OpenCode 2 servers"). Settings is where users go looking
   for a thing they remember; a vanished row reads as a bug. Pattern:
   inline info-banner-with-disabled-control
   ([Klarna "Email update not available"](https://mobbin.com/screens/9ca31275-b895-4b6c-8571-16db36792624)),
   in-place explainer instead of removal
   ([Garmin Connect gated Nutrition tab](https://mobbin.com/screens/dd6772a3-d943-431b-b9b4-2be6945c4b4a)).
3. **Menu/sheet actions inside surviving screens** (chat overflow, session
   long-press) → **hide.** Menus list possible actions only.
4. **Tabs inside surviving screens** → hide the tab, keep the screen.
5. **v2-only features on v1 servers** → **hide** (steer/queue long-press
   menu, pending-inbox bubbles, forms renderer, transcript markers,
   session stats). The composer silently falls back to v1 semantics; no
   explainers for features the user has never seen.

One standard explainer component: `GatedRow` wrapper (key
`gated-<feature>`) that renders the row at 55% opacity, `enabled: false`,
subtitle swapped for the explainer; tapping shows a snackbar "Requires an
OpenCode 1 server" (or 2). No upsell styling — this is capability, not
plan, gating.

### The ~25 no-equivalent features — locked treatments

| # | v1 feature (surface) | class | treatment on v2 |
|---|---|---|---|
| 1 | Managed workspaces inventory (`managed_workspaces_screen`) | More tile | **Hide** tile + screen |
| 2 | Workspace status polling (workspace_screen sections) | screen section | **Hide** section |
| 3 | Workspace adapter discovery (create flow) | screen | **Hide** with #1 |
| 4 | Workspace sync-list | action | **Hide** with #1 |
| 5 | Warp session to workspace (`session_destination_sheet`) | sheet action | **Hide** row |
| 6 | Sync start (steal preflight) | internal | dead code — delete path |
| 7 | Steal / "Continue here" (`global_sessions_screen`) | list action | **Hide**; future: rebuild on export+import+move (port-matrix §2 note) |
| 8 | Console org list + switch (`session_destination_sheet`) | sheet section | **Hide** section |
| 9 | MCP OAuth start/callback/cancel (`mcp_oauth.dart`, integrations pending rows) | flow | **Hide** the auth rows; MCP servers with `status: needs_auth` show a disabled explainer chip "Authenticate from the server machine" |
| 10 | Session share (chat overflow) | menu action | **Hide** |
| 11 | Session unshare | menu action | **Hide** |
| 12 | Session archive (workspace long-press) | menu action | **Hide** |
| 13 | Todos sheet (`session_sheets.dart`) | chat sheet | **Hide** entry; todo truth may return via tool metadata later |
| 14 | Delete message (chat long-press) | menu action | **Hide** (PATCH edit ≠ delete; do not fake with edit) |
| 15 | Workspace symbols tab (`files_screen` Symbols) | tab | **Hide** tab |
| 16 | Full-text search `findText` | none (unused) | delete dead code |
| 17 | LSP status section (`project_health_screen`) | screen section | **Hide** section |
| 18 | Formatter status section (project health) | screen section | **Hide** section |
| 19 | Git init action (project health) | action | **Show disabled** with explainer "Run `git init` from a terminal" (health screens explain) |
| 20 | Tools inventory (`tools_screen`) | More tile | **Hide** tile + screen |
| 21 | Experimental capabilities probe | internal | delete; replace with flavor gate |
| 22 | Shell settings (pty shells + global config, `coding_settings_screen`) | settings rows | **Show disabled**: "Shell selection isn't available on OpenCode 2 servers" |
| 23 | Remote server upgrade (`server_settings_screen`) | settings row | **Show disabled**: "Upgrade from the machine running the server" |
| 24 | Send diagnostics to server log (`app_diagnostics_screen`) | settings row | **Show disabled**: "This server doesn't accept client logs" |
| 25 | Provider runtime refresh (`instance/dispose`, integrations) | action | **Hide** (v2 hot-reloads config; `DELETE /api/debug/location` is debug-only — do not wire) |
| 26 | Session-scoped diff (`session.diff`) | — | not gated: collapses onto `GET /api/vcs/diff` (port matrix) |

Inverse (v2-only, hidden on v1): steer/queue menu + inbox strip items
(server kind), forms, transcript markers & new variants, saved-permission
management via location param, session stats, instructions editor,
`model.default` seeding, integration command-connect.

---

## 8. Integrations & credentials — phase-4 preview (one screen concept)

Not built in phases 2–3; locked concept so phase 4 starts warm. Screen:
**Integrations** (evolves `library/integrations_screen.dart`).

- **List**: sections "Connected" / "Available", one row per
  `Integration.Info` — name, and for connected: connection summary chips
  (`credential` connections as label chips, the active one filled;
  `env` connections as an outlined chip "ENV · OPENAI_API_KEY"). Row key
  `integration-<id>`.
- **Detail sheet** (tap row): connections list with per-credential actions
  (Activate → `POST .../activate`, Rename → PATCH label, Remove → DELETE,
  driven by `credential.updated`/`credential.switched` events), then one
  button per `method`:
  - **Key method** → sheet with the paste-first secret field from §1
    (paste + visibility toggles, `AppMono`) plus `method.form` fields
    rendered by **§2's `FormRenderer`** (the `answer` payload), optional
    label field → `POST .../connect/key`.
  - **OAuth method** → tap opens `url` externally, then an **attempt card**
    replaces the button: spinner + "Waiting for approval in your browser…"
    + expiry countdown from `time.expires` + Cancel
    (`DELETE .../oauth/{attemptID}`); poll status every 2 s. `mode:"code"`
    adds a code field + "Complete" (`POST .../complete`). Failed shows
    `message` inline with Retry. Blocking-connect precedent:
    [Klarna "You're connecting…"](https://mobbin.com/screens/016b4c0e-cca2-475d-b1d5-e0865fd86dd9);
    explainer-then-single-CTA:
    [Strava Connect Garmin](https://mobbin.com/screens/662ef51b-cf8b-43dc-8366-7025edc590cc).
  - **Command method** → attempt card with `label` + instructions text and
    the same poll/cancel loop (`GET .../connect/command/{attemptID}`).
  - **Env method** → informational row only ("Set `NAMES` on the server").
- Persist `integrationID` **with** every pending attempt (port-matrix risk
  #7). Attempt cards restore across app restarts until expiry.
- Keys: `integration-connect-<methodId>`, `integration-attempt-card`,
  `integration-attempt-cancel`, `credential-<credId>-activate`.

---

## 9. Motion & continuity

flutter_animate remains **banned**; everything below is stock implicit
animations or `TweenAnimationBuilder`, always short-circuited by
`MediaQuery.disableAnimationsOf`.

| existing pattern | extends to |
|---|---|
| `EntranceReveal` (staggered fade/rise, `lib/ui/widgets/entrance.dart`) | Requests screen form/permission tiles; integrations method list; **not** transcript rows (transcript has its own insertion flow) |
| Pinned apply bar (`model-picker-apply-bar` recipe) | `form-apply-bar` (§2), `permission-apply-bar` (§3) — identical Material/elevation/SafeArea structure |
| `_ContextMeterLine` (400 ms tweened fill) | unchanged; it now reads usage from `session.usage.updated` events — no visual change |
| `AnimatedContainer` composer surface (180 ms) | hosts the busy-state Stop+Send swap via `AnimatedSwitcher` 150 ms fade-through; pending strip items exit via 150 ms `AnimatedOpacity` |
| `AnimatedSize` (new, but same family) | form `when` reveals (240 ms), permission reject-field expansion (240 ms), `TranscriptNotice` expand (200 ms) |
| `_TypingIndicator` / tool spinner rows | shell running state, compaction running pill, OAuth attempt card spinner |

No hero transitions, no custom route animations, no shimmer. New surfaces
inherit screen-level entrance from their sheet/route as today.

---

## 10. Build order (against port phases 2–3)

Sized S / M / L. Order is dependency-true: each item only needs the API
work of its phase.

| # | phase | UI work | size | files touched |
|---|---|---|---|---|
| 1 | 0/2 | Flavor probe + verdict row + password paste/reveal + error taxonomy (§1) | **S** | `lib/api/server_probe.dart`, `lib/ui/screens/servers_screen.dart` |
| 2 | 0/2 | 401 re-auth banner + profile flavor cache (§1) | **S** | `lib/state/profiles.dart`, `lib/state/connection.dart`, `lib/ui/widgets/connection_status_banner.dart` |
| 3 | 2 | Picker apply semantics ("Use for this session"), server-authoritative model/agent state, `model.default` seed (§4) | **S** | `lib/ui/widgets/pickers.dart`, `lib/ui/screens/chat_screen.dart`, `lib/state/connection.dart`, `lib/ui/screens/library_screen.dart` |
| 4 | 2 | `TranscriptMarker` + `TranscriptNotice` + shell ToolCard contract + compaction rows (§4, §6) | **M** | `lib/ui/screens/chat/message_view.dart`, `lib/ui/widgets/tool_card.dart`, `lib/api/models.dart` |
| 5 | 2 | Busy composer (Stop+Send, steer default, long-press queue menu) (§5) | **M** | `lib/ui/screens/chat/composer.dart`, `lib/ui/screens/chat_screen.dart` |
| 6 | 2 | `PendingSendsStrip` merging offline queue + inbox; actions sheet; Mission Control pending count (§5) | **M** | `lib/ui/screens/chat/message_view.dart`, `lib/state/offline_queue.dart`, `lib/state/connection.dart`, `lib/ui/screens/mission_control_screen.dart` |
| 7 | 2/3 | Feature-gating layer: `ServerFlavor` capabilities object, `GatedRow`, apply the §7 table | **M** | `lib/state/connection.dart`, `lib/ui/widgets/product_states.dart` (GatedRow), plus each gated screen listed in §7 |
| 8 | 3 | Permission sheet v2 + reject-message + notification Reply mapping (§3) | **M** | new `lib/ui/screens/chat/permission_sheet.dart` (delete `permission_dialog.dart`), `lib/ui/permission_presentation.dart`, `lib/ui/screens/requests_screen.dart`, `lib/background/live_background.dart`, `lib/state/connection.dart` |
| 9 | 3 | `FormRenderer` + `FormRequestCard` + Requests screen rewrite + notification deep-link (§2) | **L** | new `lib/ui/widgets/form_renderer.dart`, `lib/ui/screens/requests_screen.dart`, `lib/ui/screens/chat_screen.dart`, `lib/state/connection.dart`, `lib/background/live_background.dart` |
| 10 | 4 | Integrations connect flows + attempt cards (§8) | **L** | `lib/ui/screens/library/integrations_screen.dart`, `lib/api/product_repository.dart` |

Test keys introduced in this document (for widget tests, following the
existing style): `server-probe-verdict`, `server-password-field`,
`server-password-paste`, `server-password-visibility`,
`banner-update-password`, `form-sheet`, `form-field-<key>`,
`form-apply-bar`, `form-submit`, `form-cancel`, `form-error-banner`,
`form-request-card-<id>`, `permission-sheet`, `permission-resources`,
`permission-source-chip`, `permission-apply-bar`, `permission-allow-once`,
`permission-allow-always`, `permission-reject`,
`permission-reject-message`, `permission-reject-send`,
`transcript-marker-<type>-<id>`, `transcript-notice-<id>`,
`shell-card-<shellID>`, `compaction-<status>-<id>`, `chat-stop-button`,
`send-delivery-menu`, `send-delivery-steer`, `send-delivery-queue`,
`pending-send-<id>`, `pending-send-actions`, `mission-pending-count-<id>`,
`gated-<feature>`, `integration-<id>`, `integration-connect-<methodId>`,
`integration-attempt-card`, `integration-attempt-cancel`,
`credential-<credId>-activate`, `migration-progress-screen`.
