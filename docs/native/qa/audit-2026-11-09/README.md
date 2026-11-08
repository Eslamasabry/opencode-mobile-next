# Native visual audit — merge41 (2026-11-09)

Independent, read-only audit of `native/screens-merge41` at `0337a21f1`. Nothing was fixed. Findings are grouped by
the kit part or rule that causes them, so one kit fix closes many screens. Flutter reference: `mobile-next/master`
(`c7ebb72e1`) `lib/` and `test/goldens`.

**Evidence:** every `evidence/*.png` is a side-by-side with red boxes on the problem. Labels marked **(m37)** come from
the 2026-11-08 emulator device walk of the merge37 preview (`/home/eslam/Storage2/wt/device-walk-2026-11-08/shots`).
Re-check those on merge41 before fixing. Where the merge41 render sweep shows the same screen, the m37 label was
dropped. Everything else is a merge41 render.

**Already being fixed** (noted, **not counted as new findings**): glass (glass-panes), spacing (kit-spacing), home
layout (home-a1), search (search-b1), agents page (agents-bento), report screen (report-r1), chat running state
(chat-running), the Prompt editor crash and the Antigravity sign-in.

## Sources

| Code | Source | Revision | Count |
|---|---|---|---|
| R | **This audit's render sweep.** Capture-only desktop tests on the throwaway branch `native/audit-41-renders` (merge41 + `native/next`), run on CI (runs 42516808436 … 42518242949). Six variants per state: `solid-light`, `solid-dark`, `glass-light`, `glass-dark` (desktop Glass = frost fallback), `solid-dark-ar` (RTL), `solid-light-text13` (1.3×), all at 412 × 915 dp, density 2.625. Not committed here (6,497 PNGs, ~1 GB); kept at `/home/eslam/Storage2/wt/audit-41-renders-out/all/`, named `<module>__<screen>__<state>__<variant>.png`. | merge41 | 6,497 renders, 1,086 states, 33 modules |
| G | CI-recorded desktop goldens (`native/**/desktopTest/goldens`, re-recorded on the runner in `06b0839f9`) | merge41 | 110 |
| K | Kit screenshots (`native/ui/*/src/screenshots`) | merge41 | ~190 |
| LG | Liquid-glass emulator renders (`docs/native/qa/liquid-glass-2026-11-08`) | merge40 + liquid | 26 (one is an empty file) |
| L | Looks captures (`/home/eslam/wt/lane-looks/looks-out/screens`) | 11-08 | 12 |
| DW | Device walk, Flutter vs native, emulator (`/home/eslam/Storage2/wt/device-walk-2026-11-08`) | merge37 | 60 native, 63 Flutter |
| S27/S31 | Design visual folders (`docs/design/native-ui-rebuild/visual/s27-open-project`, `docs/native/qa/s31-app`) | earlier | ~40 |

Method: about 45 contact sheets (~280 screens and states) judged by eye against the owner's rules and the Flutter
goldens; an automated scan of 2,165 sweep renders (`solid-light` and `solid-dark-ar`) for ink in the leading 6 dp
strip, which found the gutter class; and a blank-render check over all 6,497.

## Coverage

Inventory from source: every `AppRoute` installed by an `EntryInstaller` (131 routes), every `SettingsPage` (27), and
every overlay call site in `feature/*`, `ui/kit-chat`, `ui/shell` (287: 130 sheets, 101 confirms, 28 dialogs, 28 menus).
Eight nominal states each: populated, empty, loading, error, offline, long text, RTL, 1.3×.

| Surface | Total | Rendered (any state) | State cells | Cells rendered |
|---|---|---|---|---|
| Routes | 131 | **99 (76 %)** | 1,048 | 520 (50 %) |
| Settings pages | 27 | **24 (89 %)** | 216 | 72 (33 %) |
| Overlay call sites | 287 | **47 (16 %)** | 2,296 | 91 (4 %) |
| **All** | **445** | **170 (38 %)** | **3,560** | **683 (19 %)** |

- Glass and Solid, dark and light: all four rendered for 128 surfaces (desktop Glass draws the frost fallback; the
  liquid look exists only in the 26 LG emulator renders: list, chat, sheet, menu, settings).
- Daily path (home, chat idle/running/approval/error, composer, settings, agents, servers, search, files, git,
  setup): every screen rendered. **Gap:** menus and sheets that open by a tap failed to capture in the sweep (chat
  header menu, message menu, model picker, tray, permission details, row long-press, files/folder menu, terminal
  menu); for those, the evidence is LG/L/DW only.
- Not rendered (32 routes): `ServerProviderAuthRoute`, `CapabilityAddComputerRoute`, `CapabilityAgentAccountRoute`,
  `AgentToolsRoute`, `CapabilitiesRoute`, `CardsSettingsRoute`, `DemoRoute`, `ChatsRoute.All`,
  `ChatsRoute.Attention`, `SessionRelationsRoute`, `MonitorInboxRoute`, `CodexProbeRoute`,
  `FactsBookRoute.{Connect,ReminderEdit,Search,Thing}`, `FilesRoute.{ProjectFile,ProjectFolder}`,
  `AgentHandoffRoute`, `IntegrationsRoute`, `MonitorRoute`, `KeptPromptsRoute`, `AgentAccountRoute`,
  `CapabilityServerSetupRoute`, `ServersRoute.SetupAssist`, `LibraryRoute`, `ServerIntegrationRoute`,
  `ServerSettingsRoute`, `SettingsRoute.{ModelDefaults,Permissions}`, `SetupRoute.Home`, `SetupReviewRoute`. Settings
  pages not rendered: ServerConfiguration (needs DI), VoiceLicense, QuietTime. Causes: modules without a
  `desktopTest` set (agenttools, capabilities, codexprobe, handoff, integrations, monitor, setupassistant,
  setupreview), or ViewModel/DI-only screens.
- Blank renders: the six `gate__gate__opening-wordless__*` are one flat colour. Probably the intended wordless
  splash; not counted.

Full manifest: [`manifest.csv`](manifest.csv), one line per surface, an `x` per state with a render, and its sources.

## Severity totals

New findings, counted per screen instance (one class on one screen = one finding):

| Severity | Count |
|---|---|
| Blocker | 1 (Terminal) |
| Major | 106 |
| Minor | 18 |
| **Total new** | **125** |
| In progress (not counted) | ~13 (glass ×5, chat-running ×2, agents-bento ×2, report-r1, home-a1/kit-spacing ×3) |

Resolved since the m37 device walk (seen in merge41 renders): the AI Team page now has an intro, "How it works" and
a turn-on action (`team__off__*`); the permission card has "Always allow" and Details; the slash list shows app
commands with "Show all"; the welcome CTA says "Connect to my computer"; the terminal key row has arrows.

## Findings by CLASS (ordered by screens one fix reaches)

Severity: **B** blocker, **M** major, **m** minor.

### C01 Icon on a plate (box or circle behind a glyph) — M — 15 screens
Rule: no icons inside boxes or circles.
- **Kit cause:** `IconTile` (`ui/kit/.../modal/KitSheet.kt:838`: `clip(Tile).background(surface3)`) heads every
  `KitConfirm` (101 call sites), `KitDialog` and icon sheet. `KitStateView` draws its glyph on the same kind of
  plate, so every empty and error state inherits it. Beyond those two parts: the composer tray (Photos/Camera/File/More
  each on a filled circle), the tool row's Run glyph (green outlined box, `e20-s14-row-states`), Conversations' search
  and gear on white circles (Looks capture, m37; s10 goldens draw them bare, so two header variants exist), and the
  model sheet's check in a filled circle.
- **Screens:** Home opening failed (empty, saved, en, ar), Conversations offline-failed, Usage › Limits empty, Open
  project error and empty, every confirm (e.g. Delete schedule), Trust this certificate, Voice setup (offer,
  downloading, mobile data), composer tray, chat tool rows, Conversations header, model sheet.
- **Evidence:** `evidence/C01-icon-plates.png`, `evidence/C22-confirm-tile-and-green-delete.png`,
  `evidence/C20-empty-icon-slots.png`.

### C02 Content with no gutter (0 dp to the screen edge) — M — 14 screens
Rule: consistent 16 dp gutters.
- **Kit cause:** `KitScreen` pads its bottom part with `tokens.gutter` (`KitScreen.kt:249`) but not its body, so
  each screen must remember its own padding. These did not: Terminal (server, local, project terminal: output, "Send
  to terminal" label, key row), Project health ("Refresh"), Development services, Update this server, Notices,
  Saved prompts (search label and field), Projects picker (project rows), slash list and the composer under it, AI
  Team › Living spec (labels and fields), AI Team › Findings (cards edge to edge), Provider sign-ins. Plus
  `ChatRequestSlot`'s "Needs your decision" label at x = 0 (clipped in ar).
- **Evidence:** `evidence/C06b-zero-gutter-merge41.png`, `evidence/C06-zero-gutter.png`,
  `evidence/C25-team-pills-gutter.png`. Report a problem's edge-to-edge Details band is in progress (report-r1).

### C03 Status shown as a tinted pill — M — 11 screens
Rule: no pill badges for status.
- **Kit cause:** `KitStatusPill` (`ui/kit/.../frame/KitStatusPill.kt`, used by `ChatsScreen`) draws "● Laptop · Not
  answering" as a tinted capsule and truncates the name ("This p…") with room to spare. Other instances: the composer's
  "■ Stop" chip; Changes' "✓ Uncommitted" chip, which reads as a badge; `KitRow`'s file-type tag "MD"; AI Team
  findings' "Critical" and "Minor" capsules; the terminal's "build-server" capsule.
- **Screens:** Conversations header (connecting, connected, offline, long name, ar), Home opening failed, every
  running chat, Changes, file rows, AI Team findings, Terminal.
- **Evidence:** `evidence/C02-status-pills.png`, `evidence/C25-team-pills-gutter.png`. Overlap: home-a1 and
  chat-running.

### C04 Selected state is a slab — M — 11 screens
Rule: selected states look intentional, not dark slabs.
- **Kit cause:** the radio/choice-row selected fill and the selected-card fill: a near-black green slab in dark, a
  flat mint band in light. The welcome card stacks three signals (border, slab, check badge).
- **Screens:** Welcome ("Where should your agent run?"), the Servers arrival sheet, Voice typing (speech model),
  Switch branch sheet, Schedule editor (repeat), Project picker sheet, Server switcher, Model sheet, Appearance
  (colour cards, surface cards), guided Appearance cards, Usage tab switcher.
- **Evidence:** `evidence/C03-selected-slabs.png`.

### C05 Actions drawn as plain text — M — 10 screens
- File viewer (binary and text): "Copy contents Find in file Show source" run together on one line. "More file
  actions / Open with / Save" are centred text lines; "Open with" also appears as the accent button above.
- Voice typing: "Download … again" and "Delete … (161 MB)" are bold paragraphs, and delete is not marked.
- Server editor: "Check connection" and "Inspect certificate" look like headings. Home opening failed: "Change server".
  Chat offline banner: "Try again". Phone › Running: "أوقف" (Stop) as bold text. Saved prompts: "Edit Delete". AI Team
  home: "Give a quick task". AI Team task: answer and stop actions.
- **Kit cause:** screens use text + `clickable` instead of a kit text action or action row with a touch target.
- **Evidence:** `evidence/C10-text-as-buttons.png`.

### C06 Duplicated text — M — 9 screens
Rule: no duplicated text.
- **Kit cause:** `KitInputDialog` (`ui/kit/.../modal/KitDialog.kt:305-326`) passes the same `validate()` line to the
  field *and* to the button's `Disabled(reason)`, so the reason is printed twice, before anything is typed. That
  affects every input dialog (41 call sites): Send back with a note, Open a project, Create a folder, rename, …
- Other instances: Switch branch sheet ("Branch name" above the field and again below the button); Available on this
  server ("Not on this server" under every one of nine rows); Git (each changed file listed twice: a toggle row, then a
  "Changes" row repeating the name; "no remote" said three times, m37); Diff page ("README.md" three times, m37); Saved
  prompts ("Kept from a removed server" as a row subtitle and again as a heading); Chat with a request ("Waiting for
  you" and "Working", m37); running chat ("3 steps · Working", then "Working" again).
- **Evidence:** `evidence/C21-duplicate-validation.png`, `evidence/C08-duplicated-text.png`.

### C07 Raw or technical words in user copy — 2 M, 6 m — 8 screens
Rule: plain words; technical text under Details.
- **M** Notices (Bundled components): raw Markdown shown as text (`## …`, backticks, `[LICENSES/…](LICENSES/…)`)
  with SHA-256/NDK/Cargo detail, at 0 dp.
- **M** Voice typing: "The models below are for the offline engine, which this version does not include yet."
  directly above a working Download button.
- m: Updates "Build 10001"; Usage › Cost "Timezone: UTC" and ISO range "2026-11-01 – 2026-11-07"; Add a server
  "unencrypted at the HTTP layer", "Connection checked · 2.1.1"; Changes "Showing Uncommitted: it is the view with
  changes."; Settings hub raw lowercase "laptop · Connected"; Review "Also changed / 1".

### C08 Text cut without an ellipsis — B on Terminal, M elsewhere — 8 screens
Rule: text never cut without an ellipsis.
- **Kit cause:** the composer chip row fades its last chip mid-word ("Auto-approve pa", ar "الموافقة التلقائية"); the
  new-chat, Conversations and AI Team board chip rows cut the last chip at the screen edge ("alpha-a", "Ru", "Don")
  with no fade or scroll hint; the request slot's label is clipped at the right edge in RTL.
- **Terminal (B):** lines clipped at the right edge, link rows run together ("…help.ubuntu.comOpen link ·
  landscape.cano"), top-bar actions "New …"/"All t…", a "disconnected" notice over a live prompt (m37), and 0 dp
  gutters (C02, merge41).
- **Evidence:** `evidence/C04-text-cut.png`.

### C09 RTL — M — 6 screens
Rule: RTL mirrored.
- Composer at 360 dp RTL: the model chip truncates ("…Crouton Fa", ellipsis on the leading side) where LTR shows the
  full name.
- Home opening failed (ar): title "This phone لا يستجيب", where the default server name is not localized; the chip
  shows "…This pho".
- Units are not bidi-isolated: Phone › Running shows "MB 215.0" and "%3.0".
- Digits differ by screen in the same locale: Conversations and Schedules use Arabic-Indic, Usage uses Western.
- Read-only composer (ar) is shorter than en and its bottom border is cut.
- Git (ar): on and off switches show the thumb on the same side; check `KitSwitch` mirroring.
- **Evidence:** `evidence/C11-rtl.png`, `evidence/C24-rtl-bidi-switch.png`.

### C10 Destructive confirms drawn as the accent primary — M — 6 screens
`KitConfirm` defaults to `Neutral`, and a neutral confirm's button is the accent. Screens that delete without
passing a kind get a green "Delete" under an info tile: Delete schedule (seen), plus call sites in `ChatScreen`,
`WebSearchPanel`, `ServerEditorScreen`, `LibraryScreen`, `TeamEditorScreen` (found by source scan).
`evidence/C22-confirm-tile-and-green-delete.png`.

### C11 Notice glyph is an empty ring that reads as a radio button — M — 5 screens
`KitNotice`'s neutral/working tone and `KitStateView`'s empty fallback draw "○". In the chat offline banner ("Not
connected to the server right now"), Reconnecting, the notify offer, the phone-setup size notice and Usage › Limits
empty, it looks like an unchecked radio. `evidence/C13-notice-ring.png`.

### C12 Loading or empty that draws a blank page — M — 3 screens
- Linux on this phone (checking) and On this phone setup journey (checking): only a 2 dp progress line under the top
  bar on an empty page.
- Settings › Storage (default): title only, nothing below; no empty-state words.
- **Kit cause:** these screens skip `KitStateView` loading/empty. `evidence/C23-blank-pages.png`.

### C13 Top-bar actions truncate — M — 3 screens
`KitTopBar` text actions ellipsize in a fixed slot: "New …", "All t…" (Terminal), "Copy updated …" (Review comment).
`evidence/C05-topbar-truncation.png`.

### C14 Reserved but empty leading slot — m — 3 screens
The Conversations meta line is indented ~16 dp past the title with nothing in the gap (s10 goldens). The model
sheet's Recent row is indented with no glyph. Menu rows without an icon are the inverse (C17).
`evidence/C20-empty-icon-slots.png`.

### C15 More than one accent button — M — 2 screens (+ agents in progress)
Add a server, step 2: "Scan code" and "Save and connect" are both accent (m37). Agents: one accent per agent card
(**agents-bento**). Nothing in the kit or a lint stops a second primary. `evidence/C07-two-primaries.png`.

### C16 Appearance — 1 M, 2 m
- **m (harness):** the `appearance-*` goldens draw two columns at 412 dp, because `AppearancePage.kt:110` gates that on
  `LocalKitWindow.cls.isWide` and the golden's desktop window is wide. The sweep render at a true phone window shows
  one column with full theme names. The goldens lock in a layout no phone gets, with "O / ff" under a badge and
  "Grap.."; fix the harness (check every 412 × 768 settings golden).
- **M:** one page with seven sections and 40+ controls (mode, 31 themes, 12 accents, surfaces, background, strength,
  text size…): the dev-panel rule. Flutter's first visit is the four-row guided list. `evidence/C09-appearance-parity.png`.
- **m:** guided steps show a green "Changes" tag before each description; step counts disagree (2 of 4, 4 of 6).

### C17 Menus lose their icon column — m — 2 menus
Chat ⋯ › Go to: the last three rows have no icon, so the text column jumps (m37). Files ⋯ holds one item, "View"
(m37; the sweep could not open it on merge41). `evidence/C12-menu-icons.png`.

### C18 Dead end — 1 M, 1 m
Files ⋯ menu with only "View" (M, m37). Home opening failed has no gear (m).

### C19 Code and diff wrapping — m — 2 screens
The Write tool's diff wraps inside string literals at 360 dp while chat code blocks scroll sideways; at 1.3× the file
name breaks mid-word. `evidence/C15-diff-wrap.png`.

### C20 Flutter parity seen on screen — m — 2 screens
Notifications has no "Usage alerts" row (`evidence/C17-notifications-parity.png`). The Settings hub has no back arrow
while every other pushed page has one. Feature parity is covered by
`/home/eslam/Storage2/wt/parity-audit-2026-11-08/REPORT.md` and the device-walk report, and is not repeated here.

### In progress (observed, not counted)
- **Glass legibility (glass-panes), B:** transcript text collides with the liquid header, composer, menus and model
  sheet; a dark plate behind the light-mode server chip; a box nested in the model sheet.
  `evidence/C16-glass-legibility.png`.
- **Spacing (kit-spacing, home-a1):** "Mark all seen" floats under the first row; "Since you were away" is clipped
  under the bar; This phone's section gaps are uneven.
- **Chat running (chat-running):** Stop chip plus "Stop and send".
- **Agents (agents-bento):** several accent buttons, as listed above.

### Not counted (unconfirmed)
- Dark desktop renders draw list titles heavier than light ones (s10 home, settings hub); emulator dark screens do
  not. Probably the desktop rasterizer, not the kit.
- The "+" FAB is an icon on a filled circle. It is the Android-native FAB; the owner should rule on it against C01.

## Top 20 worst screens

Ranked by severity, then by the number of classes on the screen. "ip" = a builder already owns part of it.

| # | Screen | Classes | Why |
|---|---|---|---|
| 1 | Terminal (server, local, project) | C08 B, C02, C03, C13 | Clipped lines, 0 dp output and keys, run-on link row, truncated actions, status capsule |
| 2 | Chat under Glass | ip glass-panes (B) | Header, composer, menus, model sheet unreadable over text |
| 3 | Composer (running / stuck / slash) | C08, C03, C09, C02 (ip chat-running) | "Auto-approve pa", Stop pill, RTL "…Crouton Fa", slash list at 0 dp |
| 4 | Conversations home | C01, C03, C14, C08 (ip home-a1) | Status capsule, icons on circles, empty meta indent, chip cut at the edge |
| 5 | Home opening failed / offline-failed | C01, C03, C05, C09, C18 | Icon tile, "This p…" capsule, "Change server" as text, untranslated "This phone", no gear |
| 6 | Notices (Bundled components) | C07 M, C02 | Raw Markdown and build-system detail at 0 dp |
| 7 | Chat with a request | C02, C08, C06, C09 | Label at 0 dp, clipped in ar, "Waiting for you" + "Working" |
| 8 | Git + Switch branch | C06, C04 | Each file twice, "Branch name" twice, mint slab, "no remote" ×3 |
| 9 | File viewer | C05, C06 | Run-on actions, centred text buttons, "Open with" twice |
| 10 | Every input dialog (KitInputDialog) | C06 | The validation reason printed twice, before typing |
| 11 | Every confirm (KitConfirm) | C01, C10 | Icon tile; deletes drawn as the green primary |
| 12 | Appearance | C16, C04 | 40+ controls; goldens show a wide layout at 412 dp |
| 13 | Voice typing | C04, C05, C07 | Slab, bold-paragraph actions, unmarked delete, contradictory copy |
| 14 | Development services / Project health / Update this server | C02 | Buttons and text at 0 dp |
| 15 | Saved prompts | C02, C05, C06 | Label at 0 dp, "Edit Delete" as text, heading repeated |
| 16 | AI Team findings / Living spec | C02, C03 | Critical/Minor capsules, cards and fields edge to edge |
| 17 | Add a server / Server editor | C15, C05, C07, C01 | Two accent buttons, "Check connection" as a heading, jargon, certificate tile |
| 18 | Usage (Cost, Limits) | C01, C11, C07, C09 | Ring on a plate, "Timezone: UTC", ISO dates, mixed digits |
| 19 | Storage / Linux checking / setup journey checking | C12 | Blank pages |
| 20 | Model sheet | C04, C01, C14 (+ip glass) | Slab and check-in-circle, empty leading slot, a star on every row |
