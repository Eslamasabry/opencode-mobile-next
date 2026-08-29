# Five-lens UI/UX deep audit

> Status: re-verified 2026-08-29 — see
> [`reverification-report.md`](reverification-report.md). Lens 1/2/3/5
> ~fully fixed; Lens 4 (visual tokens) 14 findings still open.

Audit date: 2026-08-29. Branch: `production/android-release-hardening`.
Companion to [`docs/ui-feature-audit.md`](ui-feature-audit.md) (whose F/E/A
items are not repeated here). Five parallel lenses, each verified against
source with file:line evidence.

Legend: severity High / Med / Low. "Fix" is one-line and concrete.

---

## Lens 1 — Chat transcript & content rendering

| # | Sev | Finding | Evidence | Fix |
|---|---|---|---|---|
| C1 | **High** | Per-token `setState` re-renders the transcript and re-parses all markdown; open code fences re-run `highlight.parse` on the accumulated buffer per token, on the UI thread. Jank + battery cost grows with message length in the app's core loop. | `chat_screen.dart:336–360` (no batching), `:3036`; `markdown.dart:163–173, 762–771`; `code_highlight.dart:108–124` | Buffer deltas, flush once per frame/~50 ms with one `setState`; memoize `MarkdownText` per `(partId, length)` |
| C2 | Med | Assistant prose is selectable, so long-press opens text selection instead of the Copy/Fork/Delete menu — violating the codebase's own stated rule (user bubbles do it right). The menu is effectively hidden on the most common surface. | `markdown.dart:150–152` (rule), `message_view.dart:725` vs `:976` | Pass `selectable: false` for assistant parts inside `_MessageView` |
| C3 | Med | Tool/reasoning expansion state lives in item-local state and is destroyed by list recycling — scroll away and back, everything re-collapses. | `tool_card.dart:276, 284–288`; `message_view.dart:1097–1104` | Hoist expansion into a session-scoped `Map<partId, bool>` (store exists for `transcriptReasoningExpanded`) |
| C4 | Med | Language chip + copy button float over the first code lines with a 48px target — first ~3 lines are covered, and tapping top-right to select/scroll triggers copy. | `markdown.dart:774–821`; `app_theme.dart:253–260` | Promote chip+copy into a real header row (or add top padding when overlay present) |
| C5 | Med | Expanded tool bodies are nested vertical scroll traps (never hand off to transcript); `_DiffPreview` runs `IntrinsicWidth` over up to 500 lines per frame. | `tool_card.dart:1517–1526, 1058–1088` | Cap inline height ~240px, route "see all" to the virtualized `showFilePreviewSheet` |
| C6 | Med | Tool images decode at full resolution (no `cacheWidth`) — a 2160px screenshot ≈ 33 MB decoded for a 260px preview; offscreen bitmaps persist in cache. | `tool_card.dart:1344–1348`; `file_preview.dart:332–341` | `cacheWidth: (maxWidth * dpr).round()` |
| C7 | Med | `_Reasoning` creates a fresh `TextPainter` per build, never disposes it; O(text) layout per rebuild — compounds C1 during reasoning streams. | `message_view.dart:1121–1127` | Cache painter in State, re-layout on change, `dispose()` |
| C8 | Med | Timeline jump highlight embeds `$highlighted` in the widget key — element remount kills the 180 ms fade and silently resets that message's expansion state. | `message_view.dart:805–806`; trigger `chat_screen.dart:1451–1465` | Drop `$highlighted` from the key; let `AnimatedContainer` animate in place |
| C9 | Low | Running tool group force re-expands over the user; collapsing during a run is undone on the next tool. | `message_view.dart:534–542` | `_userCollapsed` flag; only auto-open if not manually collapsed |
| C10 | Low | "N earlier messages" pill always pinned (even at latest) and count includes visible messages. | `chat_screen.dart:3225–3234`; `message_view.dart:312–315` | Gate on `_awayFromLatest`; render `count − visible` |
| C11 | Low | Assistant prose has no width cap on wide screens (user bubbles 640, assistant to 860) — ~110–120 char lines on tablets. | `chat_screen.dart:3152–3155`; `message_view.dart:824` | `ConstrainedBox(maxWidth: 640)` on assistant markdown |
| C12 | Low | Muted text uses `hintColor` (fixed black54/white60) — drops below ~4:1 contrast on tinted light packs (gruvbox/solarized). Converges with V4. | `message_view.dart:875–878, 1117–1120` | Use `colorScheme.onSurfaceVariant` |
| C13 | Low | No width cap for pathological single lines in `CodeBlock` — a streamed 100k-char minified line becomes a ~100k-px layout/selection surface. | `markdown.dart:762–771` | Soft-wrap lines >~1000 chars in preprocessing |
| C14 | Low | Reversed list index-anchoring shifts visible content by one item when a turn completes while scrolled up. | `chat_screen.dart:3165–3173, 1411–1419` | Defer append while `_awayFromLatest`; materialize pending on jump-to-latest |

Already good: virtualized reversed list, deferred-delta queue for orphan
parts, part merging, typing-indicator blink with `disableAnimations`
handling, scheme-derived highlight colors, tool-group ticker, error
auto-expand, `embedded:` flattening, file-preview zoom/rendered-raw modes.

## Lens 2 — Loading / error / empty states & feedback

| # | Sev | Finding | Evidence | Fix |
|---|---|---|---|---|
| S1 | **High** | Chat transcript flashes to skeleton — or a full-screen error — on every rehydrate/refresh even when content exists; scroll position resets. Sibling screen already guards correctly. | `chat_screen.dart:3123–3126` + `_load()` at 655–658; correct pattern `session_context_screen.dart:259–261` | Guard both branches with `&& _messages.isEmpty` |
| S2 | **High** | Todos sheet is a dead-end: bare spinner on load, **raw exception text** on failure, no retry. | `session_sheets.dart:39–47` | `LoadingList` / `ProductErrorState(onRetry:)` / `ProductInlineEmpty` |
| S3 | Med | `StateError` "Bad state:" prefix leaks into visible errors at ~10 call sites (chat, files, review, context, sheets, commands, workspaces). | `chat_screen.dart:661, 1843`; `files_screen.dart:208, 255, 1052`; `session_sheets.dart:25`; `commands_screen.dart:182`; `managed_workspaces_screen.dart:169`; … | Replace `StateError`s with `ProductException` (clean `toString()` exists) |
| S4 | Med | Raw transport exceptions surface verbatim in mutation snackbars ("Send failed: Connection closed while opening…") — exactly when users need guidance. | `chat_screen.dart:1011, 1736`; `global_sessions_screen.dart:174, 230`; `sessions_tab.dart:23, 188`; +6 more | One `productErrorText(Object)` helper: pass through `ProductException`/`ApiException.message`, else "OpenCode is unreachable. Try again." |
| S5 | Med | `ApiException` message embeds the raw HTTP response body — developer JSON in `ProductErrorState` and snackbars. | `opencode_api.dart:77–78, 90–91` | Extract body `message` field; else truncate to ~120 chars |
| S6 | Med | Two error-snackbar idioms: error-red+hide-current vs plain default — same severity renders differently. | styled: `servers_screen.dart:24–31`, `worktrees_screen.dart:447–457`; plain: `chat_screen.dart:1011`, `files_screen.dart:1107, 1133`, +6 more | One `showProductError()` funnel (theme already floats globally, `app_theme.dart:273`) |
| S7 | Med | Retry vocabulary split: "Try again" (shared state, mission control, review) vs "Retry" (9 hand-rolled rows). | `product_states.dart:146`; `project_health_screen.dart:274, 473`; `files_screen.dart:1001`; +6 | Standardize one label; reuse shared components |
| S8 | Med | Tools screen nulls its data at the start of `_load()`, so pull-to-refresh swaps a populated list for a skeleton. | `tools_screen.dart:59–66` vs 296–297 | Skeleton only on first load; keep stale rows |
| S9 | Low | Settings health failure shows "Health unavailable" with no reason and an unlabeled refresh icon. | `settings_screen.dart:69, 87–93, 109–111` | Show short reason; label "Check again" |
| S10 | Low | File viewer blanks to skeleton on manual reload (content cleared at fetch start). | `files_screen.dart:1045–1048, 1209` | Fetch into temp; skeleton only when `_content == null` |
| S11 | Low | Full-height sheets use bare centered spinners vs the `LoadingList` skeleton idiom elsewhere. | `session_destination_sheet.dart:386, 558`; `session_sheets.dart:40`; `about_screen.dart:36` | Use `LoadingList(rows:)` |
| S12 | Low | "Search  tools" double space while count loads. | `tools_screen.dart:200` | Omit count until loaded |

Already good: shared states cover ~20 screens; `ConnectionStatusBanner` is
honest/liveRegion; capability gating degrades per-section without breaking
siblings; most lists keep content during refresh; empty states explain + CTA.

## Lens 3 — Touch ergonomics, gestures, sheets, keyboard

| # | Sev | Finding | Evidence | Fix |
|---|---|---|---|---|
| T1 | **High** | Message long-press menu (copy/fork/delete) has zero visible affordance and is the ONLY copy path for prompts — plus the only destructive per-message action. "long-press" appears nowhere user-facing. Combined with C2, copy is simultaneously hidden and broken on assistant messages. | `chat_screen.dart:1468, 3201`; `message_view.dart:798–804` | Subtle chevron in the meta row opening the same sheet; one-line hint in `_EmptyTranscript` |
| T2 | Med | No swipe affordances anywhere (zero `Dismissible`/horizontal drag); session delete/rename buried in trailing popup menus. | grep; `sessions_tab.dart:100–106`; `workspace_screen.dart:697–708` | `Dismissible` end-swipe → existing `showConfirmSheet` |
| T3 | Med | Sheet search fields: keyboard covers the result list and dragging scrolls the sheet instead of dismissing the keyboard (timeline, command launcher; only model picker opts in). | `timeline_sheet.dart:285–307`; `command_launcher.dart:283–308, 352, 452`; opt-in at `pickers.dart:371` | `ScrollViewKeyboardDismissBehavior.onDrag` on all sheet lists; shrink `initialChildSize` under keyboard |
| T4 | Med | Review comment composer: no max-height/scroll wrapper around a `maxLines: 7` autofocus field + keyboard — RenderFlex overflow risk at 320dp/2x; "Add to prompt" unreachable. | `review_workspace.dart:1484–1543` | Wrap in scrollable `ConstrainedBox(maxHeight: .9 * height)` |
| T5 | Med | Review workspace is the only dense surface without pull-to-refresh; refresh is a lone top-right icon — opposite corner from where you scroll the diff. | `review_workspace.dart:185–190` | `RefreshIndicator` around the compact layout canvas |
| T6 | Med | Inline composer suggestion rows are ~30dp tall (compact padding 6) directly under the thumbs — mis-taps while typing one-handed. | `command_launcher.dart:580–589, 673–682` | `ConstrainedBox(minHeight: 44)` |
| T7 | Med | The three core composer sheets opt out of the theme drag handle; their header zones aren't draggable — collapse is undiscoverable. | `pickers.dart:24`; `chat_screen.dart:1349, 2200` (vs theme default `app_theme.dart:157`) | Re-enable `showDragHandle` or feed header drags to the sheet controller |
| T8 | Low/Med | Review phone toolbar mode buttons ~36dp in the densest control row. | `review_workspace.dart:1174–1187` | `minimumSize: Size(64, 44)` + padded tap target |
| T9 | Low | Floating transcript pills below 44dp (jump-to-latest ≈40dp; earlier-messages ≈28–30dp) overlaying a scrolling list. | `message_view.dart:262–270, 306–324` | Raise paddings to 48/44dp |
| T10 | Low | Standard M3 chips (32dp) used as primary filters (model intents, variants, file breadcrumbs). | `pickers.dart:451–458, 586–615`; `files_screen.dart:529–550` | Shared `ChipTheme` comfortable density (≥40dp) |
| T11 | Low | Home NavigationBar height 64 inline vs themed 68 — two sources of truth. | `home_screen.dart:193–199` vs `app_theme.dart:163` | Drop the inline override |
| T12 | Low | Hunk prev/next lives only at the top of the diff pane — repeated top-corner reaches one-handed. | `review_workspace.dart:1032–1151` | Mirror hunk buttons into the bottom selection bar slot |

Already good: keyboard-safe compact derivation stays fixed; send/stop/model
all in bottom third; terminal key row enforces 48dp with swipe hint;
tool-card headers, review file tabs, Files/Symbols segmented control all
correctly sized; pull-to-refresh coverage broad (only review missing).

## Lens 4 — Visual design-system consistency

| # | Sev | Finding | Evidence | Fix |
|---|---|---|---|---|
| V1 | **High** | Raw `Colors.green/orange` status colors at three NEW sites (beyond the known termux block): managed-workspace "connected", MCP status dots, termux check icon — while sibling states in the same switches use scheme roles. | `managed_workspaces_screen.dart:351` (vs 356–371); `integration_tiles.dart:667, 669`; `termux_setup_screen.dart:796` (vs `successOf` at 917) | Map connected→`successOf`, in-progress→`tertiary`; one status-color helper |
| V2 | **High** | Diff "added" tint uses raw `Colors.green` in session sheets while the canonical diff renderer uses `successOf` — same concept, two colors, one breaks theme packs. | `session_sheets.dart:420, 452` vs `tool_card.dart:1103` | `AppTheme.successOf(theme).withValues(alpha: .15)` |
| V3 | Med | Brightness-fallback `AppTheme.success(scheme)` used where pack-aware `successOf` is required (terminal status chips, review). | `terminal_screen.dart:373, 401`; `review_workspace.dart:693` | Swap to `successOf` |
| V4 | Med | `theme.hintColor` is the de-facto muted role (~61 hits) but isn't scheme-derived — drifts off-palette on Catppuccin/Gruvbox/Material You. Converges with C12. | `message_view.dart` (11), `product_states.dart:82, 195, 240`, `servers_screen.dart:212, 348`, `session_sheets.dart` (8) | Replace with `scheme.onSurfaceVariant` (or a semantic role on the extension) |
| V5 | Med | Hand-rolled hairlines at five arbitrary alphas bypass `DividerThemeData` (outlineVariant .7). | `message_view.dart:675, 682`; `session_sheets.dart:304, 392`; `review_workspace.dart:1346, 1435`; `tool_card.dart:400, 1063`; `markdown.dart:669, 725, 760`; `session_context_screen.dart:395, 485`; `termux_setup_screen.dart:907` | `Color hairline(BuildContext)` helper; sweep all `dividerColor` uses |
| V6 | Med | Ad-hoc `fontSize:` clumps bypass the type scale; mono body splits 12 / 12.5 / 11; captions at 11 vs `labelSmall`; one-off 13 and 17. | 12.5: `markdown.dart:768`, `requests_screen.dart:312`, `review_workspace.dart:1306, 1381`, `guide_screen.dart:182`, `saved_permissions_screen.dart:218`, `terminal_screen.dart:836`; 12: `session_sheets.dart:438, 464`; 11: `settings_screen.dart:122`, `review_workspace.dart:1408`; 17: `home_screen.dart:257` | Declare code=12, caption=11, body=14; normalize stragglers |
| V7 | Med | Corner-radius stragglers 7/9/10/11/13 off the canonical 12/14 grid. | `tool_card.dart:918` (7); `terminal_screen.dart:383` (9); `settings_screen.dart:257`, `library_screen.dart:252` (11); `pickers.dart:214` (13); `review_workspace.dart:639, 650`; `termux_setup_screen.dart:1014` (10) | Snap to nearest 12/14 |
| V8 | Med | Same verb, three glyphs: copy (`copy_all_outlined` vs `copy_rounded` vs `content_copy_rounded`), run (`electric_bolt` vs `bolt`), stop (`stop_circle` vs `stop_rounded`). | `tools_screen.dart:409`; `markdown.dart:806`; `review_workspace.dart:1099`; `mission_control_screen.dart:208`; `composer.dart:425`; `terminal_screen.dart:220` | Pick one per verb; icon-alias block in `AppTheme` |
| V9 | Med | "In-progress" color role inconsistent: connecting→`tertiary` (home) vs →`primary` (managed workspaces) for the same concept. | `home_screen.dart:308` vs `managed_workspaces_screen.dart:356` | Standardize in the status-color helper (with V1) |
| V10 | Low | Title-role outliers: confirm sheet `titleMedium` vs every other sheet `titleLarge`; review re-derives `titleLarge` w600/-0.3; only w800 in the tree. | `confirm_sheet.dart:50`; `review_workspace.dart:1494–1497`; `files_screen.dart:954` | `titleLarge`; delete overrides; w800→w700 |
| V11 | Low | Review reimplements empty/error/notice states instead of the shared `ProductEmptyState`/`ProductErrorState` (padding/icon/action placement differ). Converges with S7. | `review_workspace.dart:1600–1634` | Port onto shared components |
| V12 | Low | Hand-rolled section labels beside canonical `SectionLabel` (40+ correct usages). | `servers_screen.dart:209–214`; `session_sheets.dart:155–161, 54–56`; `guide_screen.dart:141–146` | Replace with `SectionLabel` |
| V13 | Low | `'AppMono'` string hardcoded ~60× though `AppTheme.monoFamily` exists. | `session_sheets.dart` (6), `review_workspace.dart` (10), `command_launcher.dart` (5), `markdown.dart:52, 692, 732, 767` | `AppTheme.mono(context)` helper; migrate; zero visual change |
| V14 | Low | Spacing stragglers 7/9/11/13 off the 4/8/12/16/18/24 rhythm, concentrated in review workspace and chip internals. | `review_workspace.dart:521, 643, 668, 771, 1034`; `tool_card.dart:419, 900`; `composer.dart:170`; `terminal_screen.dart:404`; `tools_screen.dart:245` | Snap 7→8, 9→8, 11→12, 13→12 |
| V15 | Low | Three floating-surface recipes (apply bars vs quick-ask pill vs transcript pills) with different surface/elevation/border treatments. | `pickers.dart:551`; `form_renderer.dart:1179`; `workspace_screen.dart:756–760`; `message_view.dart:255, 301` | One `FloatingBar`/`FloatingPill` style (surfaceContainerHigh, elevation 3) |

Already good: zero `withOpacity` (fully `withValues`), zero `'monospace'`
strings, nested-card discipline holds, component reuse genuinely broad
(SectionLabel 40+, ProductEmptyState 20+), icon family discipline near-total,
weight system tight (the three V10 sites are the only outliers).

## Lens 5 — First-run & onboarding journey

| # | Sev | Finding | Evidence | Fix |
|---|---|---|---|---|
| O1 | **High** | Fresh server with no projects = dead end: the empty-projects early-return renders BEFORE the quick-ask pill, so a brand-new server offers no way to create a session or send a first prompt. Bites the Termux path immediately (install succeeds → server serves `$HOME` → no projects). | `workspace_screen.dart:208–216` vs pill at 429–438; repeated `projects_screen.dart:210–216` | Keep the pill rendered in the empty state; let `createSession()` fall back to the server default directory |
| O2 | Med | First-run "Save to finish" doesn't finish: auto-connect only fires `if (wasActive)`, and a new profile has no active id — Save returns to the list after the verdict promised completion. | `servers_screen.dart:69–84` vs copy at 929–933 | Auto-connect newly created profiles; or rename "Save & connect" |
| O3 | Med | DNS failure is mislabeled "connection refused — is opencode serve running?" A typo'd hostname sends users to check the server instead of the spelling — the most common first-run mistake. | `server_probe.dart:101–103` | Inspect `SocketException.osError`; distinct "host name could not be resolved" message |
| O4 | Med | Guide is buried post-connect: only path is More → Settings → About → Setup guide; no help affordance in Home, chat, or the More grid. | `settings/personal_settings_screens.dart:226–238` vs `library_screen.dart:44–102` | Add a "Setup guide" card to the More hub |
| O5 | Med | Termux time expectation understated: "can take several minutes" vs a realistic ~15-minute first run; abandoning at minute 4 looks like a failure. | `termux_setup_screen.dart:772–778` | "Typically 10–15 minutes on first run — you can leave and return" |
| O6 | Med | Chat suggestion chips are static and context-blind: "Explain this project" / "What changed recently?" are dead ends in a `$HOME`-rooted first-run session with no project/git. | `message_view.dart:139–143` | Seed one chip from the active project; substitute "List what's in this directory" when no project detected |
| O7 | Low | No first-session orientation for Review, `/` commands, or Mission Control (zero coach marks repo-wide). | `files_screen.dart:424–440`; `home_screen.dart:108–121`; `composer.dart:108–114, 176, 241` | One static tip row in `_EmptyTranscript` |
| O8 | Low | Remote path assumes a server exists: editor gives no pointer to host-install instructions when Test fails with timeout/refused. | `servers_screen.dart:456–465`; guide at `guide_screen.dart:24–41`; host mgmt post-connect only | On timeout/refused verdict, append "No server there yet? See the host setup guide" |
| O9 | Low | "Stop & retry" overstates recovery cost — the script actually resumes where setup left off. | `termux_setup_screen.dart:591–604, 872–877` (resume logic `bridge.dart:850`) | Label "Retry — resumes where setup left off" |

Already good: cold start cannot dead-end (bootstrap error card with Try
again); test-connection verdicts are specific and truthful per failure mode;
URL guidance strong without the removed pre-seed; password provenance
explained (`OPENCODE_SERVER_PASSWORD` helper); no ejection on outage;
permission timing is value-first (POST_NOTIFICATIONS only with
"Keep coding session live").

---

## Cross-lens convergences (one fix, multiple lenses)

1. **Copy/discoverability cluster** — C2 + T1: assistant-message copy is both
   broken (selection wins long-press) and undiscoverable (no affordance).
2. **Muted-role token** — C12 + V4: `hintColor` → `onSurfaceVariant` sweep
   fixes contrast and palette tracking at once.
3. **Review workspace debt** — T4 + T5 + T8 + T12 + V11 (+ S7): one focused
   pass over `review_workspace.dart` addresses overflow, refresh, target
   sizes, hunk reach, and state-component reuse.
4. **Error copy pipeline** — S3 + S4 + S5 + S6 + S7: one
   `productErrorText()` helper + one error-snackbar funnel + label sweep.
5. **Status-color helper** — V1 + V2 + V3 + V9: single
   `statusColor(state, theme)` removes every remaining raw green/orange and
   the connecting-role split.
6. **Streaming performance** — C1 + C7 + C6: batching + painter caching +
   `cacheWidth` together fix the jank-prone core loop.

## Updated priority view

1. **High-severity fixes:** S1 (chat rehydrate flash), S2 (todos dead-end),
   O1 (first-prompt dead end on fresh server), C1 (streaming jank), C2+T1
   (copy cluster), V1+V2 (raw status colors).
2. **Quick wins:** S12, T11, V10, O2, O3, O9 — all one-liners.
3. **Focused passes:** Review workspace (convergence 3), error copy pipeline
   (4), status-color helper (5), streaming perf (6), sheets/keyboard (T3+T7).
4. **Feature slices from the base audit** (`ui-feature-audit.md`) still stand:
   drafts, image paste, localization, widget deep-link.
