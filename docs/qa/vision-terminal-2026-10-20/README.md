# Terminal: a real terminal done right (2026-10-20)

Pick (vision page, "Terminal"): **A, with B's header line.** Comfortable mono
output, session tabs only when more than one session exists, one key row above
the keyboard, a calm prompt, and "Dev server running · Open preview" in the
header line only when the app really knows. No quick-command chips.

Finish line: opening Terminal shows a live shell at once (this phone or the
server), with one key row, tabs only for two or more sessions, and the dev-server
line only from a real signal. Non-goals: the chat's own "Open terminal" entry
(chat library, other owner), the shell's own prompt string, the preview screen.

## Audit: element → source → verdict

| Element of the pick | Real source | Verdict |
| --- | --- | --- |
| Live shell on this phone | `LocalTerminalSessions` (`lib/builtin/local_terminal.dart`, `LocalTerminal.kt`, `bash -l` in the built-in Ubuntu); Android only (`LocalTerminalSessions.supported`) | **Build.** Already real; restyle only. |
| Live shell on the server | `ServerOperationsGateway.listTerminals/createTerminal/connectTerminal/resizeTerminal/renameTerminal/removeTerminal`; OC2 `/api/pty` (protocol notes §9: list incl. exited, create defaults to the user's shell in the project, WebSocket with byte cursor); Paseo `terminal: true`; Codex `terminal: false` | **Build**, gated on `ServerCapabilities.terminal` (never the flavor). The screen now opens the shell directly instead of a list. |
| Open a shell straight away | Phone: `startShell()` when none (already done). Server: `createTerminal` when no terminal is running (OC2 `POST /api/pty`) | **Build** for both sources, so they behave alike. |
| Session tabs | Phone: `LocalTerminalSessions.shells`; server: `listTerminals()` (running and ended until removed) | **Build**, shown only with two or more sessions. Ended ones stay as quiet tabs until "Remove ended". |
| Tab names | Phone: "Shell 1", "Shell 2" (app-numbered); server: `Pty.Info.title` (rename is real: `PUT /api/pty/{id}`) | **Build.** No invented names ("main", "server" in the concept are image data). |
| Where it runs ("This phone" / server name) | `TerminalSource` + `ServerProfile.name` | **Build** as the one quiet line under the title; it switches source where both exist (Android). Said once (the concept repeated it). |
| "Ubuntu" next to This phone | Built-in Linux is Ubuntu, but naming the distro adds nothing a person acts on | **Leave out.** |
| One key row (Esc Tab Ctrl Alt ← ↑ ↓ → \| ~ /) | `TerminalKeyBar` + `sendTerminalBarKey` (sticky Ctrl/Alt, application-cursor arrows) | **Build** as a one-row form of the kit bar. Home/End/PgUp/PgDn and `-` stay reachable by sliding the row; ^C/^D become Ctrl then c/d (one row, as the pick says). |
| Comfortable mono output | xterm `TerminalView` in `KitTerminalView.live` | **Build:** larger face and line height, gutter padding, in a variant so other terminals (agent sign-in) keep their look. |
| Copy by long-press selection + header icon | xterm selection → `KitTerminalView.selectedText` → `KitAction.copy` | **Build:** Copy shows in the bar while text is selected; "Copy all output" stays in the menu for server terminals (their transcript is kept). The Paste / Copy output row is not built. |
| Calm prompt | The prompt is the shell's own `PS1` (the phone's `bash -l` in Ubuntu, or the server user's shell). The app does not and should not rewrite it | **Shown honestly:** the app keeps a steady mint cursor and calm colours; the prompt text is whatever the shell prints. Changing `PS1` would mean a native/rootfs change and overriding the person's own shell config. |
| "Dev server running · Open preview" | Real signals only: (1) a saved development service whose managed shell is running (`DevelopmentServices.status == running`, gated on `capabilities.developmentServices`) with the port from its own saved address; (2) a program in the built-in Linux that holds a socket and answers on loopback, started inside this project (`ListeningPorts` via `phoneScriptRunner`, only when the connected server is the phone). Preview opens through `openDevPreview` | **Build** with exactly those two signals. **Not used:** ports read from a shell's printed output (`portsForShell`, `DevPreviewLink.recent`): that is guessing from output text. |
| "port 8080" in the line | Known from both signals | **Leave out** of the surface (ports live one tap down: the preview shows the address). |
| Quick commands chips, separate command field | Nothing server-side; breaks interactive programs | **Rejected** by the owner; not built. |
| Paste / Copy output row | Paste is real (`Clipboard` → `terminal.paste`) | **Moved** into the bar's menu ("Paste into Shell 1"); no extra row. |
| Aurora glow behind the output | `KitLiveAurora` (another owner) | **Left out:** output must stay readable on the plain ground; the concept's glow is an image artifact here. |
| Keyboard, status bar, home bar | System | Not drawn by the app. |

## What changed

| Before | After |
| --- | --- |
| "Built-in Linux / OpenCode server" segmented control as a full row | One quiet line under "Terminal" says where the shell runs; it switches place where there are two (Android) |
| Server: a list of terminals, then a pushed page per terminal | The running terminal opens at once; one starts by itself when none runs (once per visit); others are tabs |
| Phone shells listed in the ⋯ menu | Tabs, only with two or more sessions; each tab's long-press has Stop/Close (and Rename on a server) |
| Two key rows (16 keys, plus ^C ^D on servers) on a grey panel | One row: Esc Tab Ctrl Alt ← ↑ ↓ → \| ~ / fill a phone; - Home End PgUp PgDn slide in; all 16 on a wide window |
| 13 px mono, 8 px padding, opaque box cutting the backdrop | 14 px mono on 1.5 lines, gutter padding, the screen's ground shows through (no seam) |
| Copy always in the bar (server) | Copy appears while text is selected; "Copy output" stays in the menu |
| Nothing about dev servers | "● Dev server running · Open preview" replaces the place line, only from a real signal (see audit); the place stays in the ⋯ menu |
| An ended server terminal read "Connection closed · Reconnect" | "Ended · code 1 · flutter"; Details now also says Running/Ended |

Kit: new `KitSessionTabs` (unit test, gallery 412/1280 dark/light/2.0, overflow
scene, still-motion registration, manifest row). Variants that leave other
screens as they were: `KitTerminalView.live(calm: true)`,
`TerminalKeyBar(singleRow: true)`, `KitTopBarScope.link` (`KitTopBarLink`) and
a plain (non-opening) scope line. Kit top bar, terminal view and chat goldens
are unchanged. G16 ratchet did not go up.

## Visual passes (against concept A)

1. Terminal box was opaque (xterm forces `backgroundOpacity`), so the backdrop
   stopped in a hard seam; place and dev line wrapped into two loose 48 dp rows;
   capture had no back arrow. Fixed: `backgroundOpacity: 0`, pushed route.
2. Header still two rows. Decision: the dev line replaces the place line while
   known; the place switch moves into ⋯ for that time. Server-tabs scene fixed.
3. At 320 dp / 2x text the half-scrolled top line of xterm painted over the
   header (no clip) and the action was cut ("Ope…"): clip added, line may wrap
   whole. Arabic: arrow caps lacked glyphs in the sans face: symbols use mono.
4. Wide window cut a key: all 16 keys centre when they fit. Pills had grown to
   the 48 dp target height: they hug their words again.

## Evidence

Renders (412x915 at 2x, one phone screen each) in
`/home/eslam/vision-assets/out/vision-terminal/`: `after-main-{dark,light}`
(dev server line), `after-tabs-*`, `after-server-*`, `after-server-tabs-*`,
`after-ended-*`, `after-not-set-up-*`, `after-main-320-2x-dark`,
`after-main-ar-dark`; `before-*` for today's screen. Capture test:
`tool/capture/vision_terminal_test.dart`.

Tests run (all passed): local_terminal_screen, revamp/screen_terminal_1 (+ goldens,
regenerated and viewed), coverage terminal actions/screens/wire/termux/paseo,
terminal_accessibility, product_ui_regression, motion_adopt, motion_states,
paseo_project_tools, library_refresh, project_hub, phone_server_card,
desktop_shortcuts, agent_sign_in_terminal, phone_agents coverage, kit
terminal view / top bar (+ r1, large text) / session tabs / manifest,
kit_ratchet, ui_glossary, text_scale_overflow, project_dev_server,
terminal_input_queue, kit goldens (session tabs, terminal view, top bar),
chat_states goldens. `flutter analyze`: no issues. Widgetbook: four Terminal
use-cases pass the screen standard; `standard ratchet` fails on findings in
other areas that predate this branch (chats, schedules, server trust).
The full suite was not run (owner rule).
