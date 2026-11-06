# Native screens upgrade

Finish line: Start preserves the prompt and model through a compact creation flow into a calm, native chat with a reversible row transition.
Non-goal: release, signing, publication, live-server qualification, and unrelated settings redesign.

Basis: the full DESIGN-REVIEW.md and all PNG sets in native-qa-2561012c5 were read/opened. Flutter reference is read-only.
The review's NotificationText Android regex crash is already fixed in this branch's baseline.

## U1 — instant first chat

Start swaps immediately to a kit chat frame with the exact prompt and Sending… while creation/delivery runs. Definite failures offer retry on the same attempt; uncertain creation/delivery never replays. The prompt, project and model/agent choices are persisted before creation; a scoped journal fences process-death retries. A created conversation takes its initial title from the first prompt. OpenCode 2 session model/mode are set and read back before dispatch; they are not fields of its prompt body.

Boundary remaining: ordinary Start opens instantly. “In a separate copy” still opens the existing readiness/approval flow before chat; its draft and choices are saved before that handoff. Extending instant chat to that flow needs integration with its explicit worktree approval owner (U1 is partial for that variant).

Validation: source inspection, formatter parsing and diff whitespace checks only. Compilation, tests and device/screenshot qualification are intentionally reserved for the coordinator. No push, merge, signing or release authorized.

## U2 — short creation screen

Prompt first, followed by one sideways-scrolling row of agent/model/project chips; compact agent rows replace tiles and Add an agent is last. Separate copy and import live in the project sheet. Actual server location/project and actual model defaults prefill the choices. Start becomes available with text when server setup is ready. Static inspection and formatter only; no compile/tests.

## U3 — agents and modes

Only primary/all-mode user agents appear; Build and Plan are a separate, server-backed mode chip. Background helpers are excluded at both adapter and chooser/action boundaries, including stale saved helper selections. Static inspection and formatter only; no compile/tests.

## U4 — one quiet notice

Chat retains one priority status slot, hidden while the keyboard is open. The duplicate notification ask and Free model banner are removed. Catalog-backed free-model state is handed to the composer as a chip tag independent of banner dismissal (composer consumer lands in U9). Source/format inspection only; no compile/tests.

## U5 — server names

Pairing hostname or address DNS name supplies the default; IP-only connections honestly say Computer until renamed. Existing IP-derived names normalize on read. The display-name field is visible and custom names are retained. Current health schema has no hostname field; no endpoint is invented. Added naming/pairing test cases as source only; not run. No compile/tests.

## U6 — readable transcript, message Details

Model switches, token/model metadata and reply timing moved into message Details. Reply Copy and More remain visible; tap/hold opens the same menu. The contextual tip appears after a reply with no keyboard/question/menu, at most twice; Help Tips replays and resets it. Added a kit reading interaction wrapper, preserving link/code targets. Source/formatter/XML checks only; no compile/tests.

## U7 — one gutter

16 dp inset for list/group seams, status lines, empty states and Tools controls. Server editor accordions share the form edge. Chat bottom controls reuse the frame gutter; creation chips keep their fixed gutter while scrolling. Source/formatter parsing and diff checks only; no compile/tests.

## U8 — framework row/chat motion

The real conversation feed row and chat now share their ConversationRef key and KitGrowPage, using Compose SharedTransitionLayout and Nav3's predictive back. Open/return settle at 250/200 ms; content fades take 150 ms. Effective reduced-motion policy also controls navigation and predictive corners. Source/diff checks only; coordinator must compile and qualify gesture cancel/commit, RTL and reduced motion on device.

## U9 — one mic/Send composer

Frameless kit composer retains the working glow, with one round primary mic/Send slot, 150 ms fade (snap under reduced motion), five text lines, scrolling model/approval chips and a vector chevron. Free is a catalog-backed model-chip tag. Placeholder names the agent. Hold mic starts voice conversation; + menu offers the visible twin, with shared teaching, replay/reset and the existing mini tutorial. Actual binding is BasicComposerHost; the unbound FullComposer path is updated too. Source/formatter/XML checks only; no compile/tests.

## U10 — project-aware empty chat

Show the real project name immediately from the header and load facts from the session folder, with scoped file/inspection reads, bounded waits and stale-result fences. Only known file/Git facts are shown. Up to three starters sit above the composer, respecting overlay chrome insets. Source/formatter parsing and diff checks only; no compile/tests.

## U11 — Conversations rows: mark, live dot, server, swipe to archive

Each home row leads with the agent's monochrome mark (persona table: OpenCode, Claude Code, Codex, Gemini, Kilo, Cline; other agents get initials), a dot on the mark while it is working or needs you (the status word stays in the line, so colour is never the only cue), the server name after the project when the feed carries one, and the time. Swipe toward the end of the line (mirrors in RTL) past 40% archives; the row folds away (150 ms, instant under reduced motion), the Undo bar rises at the bottom, and the archive is sent when the window ends (app scope, so leaving the page still sends it). A refusal brings the row back with a plain notice. Long-press opens Open and Archive; Archive is also a custom accessibility action on the row. Only OpenCode rows on a server with `sessionArchive` can be archived. New: `KitSwipeRow`, `KitFeedItem` agent/live/server/long-press, `ChatsActions.archive/undoArchive/commitArchive`. Source only; not compiled or run.

Gaps: `ChatFeedItem.sourceLabel` is set only when the merged feed holds more than one source, so a single-server list shows no server name; `agentLabel` is never filled by any source, so names come from the persona table; the archive failure notice has no dismiss; the long-press menu has no visible twin beyond the accessibility action and the All conversations screen; Paseo/Codex rows are not archivable here.

## U12 — predictive back from a chat

Already in place from U8, checked against the pinned versions: Navigation3 1.2.0 with navigationevent 1.1.0 (`native/gradle/libs.versions.toml`). `ShellMotion.predictivePop` uses Android's published values: scale to 90%, fade starting at 35% progress, edge shift of width/20 minus 8 dp, 28 dp corners, and the gesture clears back to the page when abandoned (`GestureCancelled` in `RowTransformGate`). Compose shared bounds do not follow a seeked gesture, so the shared element runs on the forward tap and the back button/tap, and the gesture uses the plain predictive-back preview (the shell switches shared bounds off while a gesture is in progress). The chat therefore shrinks toward the swiped edge during the gesture, not toward its row. I did not find a Nav3 1.2.0 switch that changes this; no code changed for this item.

## Coordinator validation remaining

Compile this candidate, then validate Start success/definite failure/uncertain delivery and process death with chosen model/mode; compact layout with keyboard/large text/RTL; notice priority; message Details and teaching replay/reset; server rename; row/back gesture cancel/commit and reduced motion; empty/typed/attachment/busy/voice composer states; empty project facts and thumb-reachable starters. No runtime, screenshot, build or release qualification is claimed here.

Final static receipt: all 57 changed Kotlin files parsed through ktfmt 0.64 (dry run), all eight changed XML resource files parsed, diff whitespace clean, and ten upgrade commits have the required subject suffix/body/trailers. No Gradle, Flutter, tests, emulator/device runs, pushes or releases were performed. Legacy formatting outside the edited blocks was preserved.
