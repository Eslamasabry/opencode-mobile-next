# screens-nav: screens, navigation, deep links, adaptive layout, shortcuts

Area map for the native rewrite (Android first, KMP-ready). Written 2026-11-01 from
`/home/eslam/Storage/Code/wt/oc_app-batch1` (branch `int/batch1-2026-11-01`, HEAD `65e3f70a3`,
app version 2.1.0+57). Read-only study; nothing in the repo was changed.

Scope: `lib/ui/screens` (301 files, ~125k lines), `lib/ui/widgets` (108 files, ~36k lines),
`lib/ui/navigation`, `lib/ui/desktop`, `lib/ui/search`, `lib/ui/teaching`, `lib/ui/git`,
`lib/ui/agents`, `lib/main.dart` + `lib/main_parts/*` (route/launch/link/share/alert logic),
`lib/widgets_overlay`, plus the Kotlin launch/link/share/shortcut channels and the manifest.
The component library `lib/ui/kit` (280 files, ~107k lines) is **not** inventoried here (another
map owns it) but its layout/navigation/motion parts that decide screen behaviour are described.

Feature ids are `SCREENS-NAV-NN`. Each id is one user-visible feature or one important internal
capability a rewrite must reproduce or consciously drop.

## 0. How to use this file, and what to trust

- **Code is the truth.** `docs/design/ui-ledger/` (`pages.md` 5,975 lines, `navigation.md`,
  `gesture-audit.md`, `ledger.json`) and `docs/ux-system/map/*.json` (356 page records, schema:
  `pageId, kind (screen|sheet|dialog|overlay|tab|onboarding-step), module, file, states[], needs[],
  whenMissing{}`) were generated 2026-09/10 and are stale in places (e.g. `workspace`, `activity`,
  `manage-project`, `legacy-drafts` pages were retired; the bottom nav is gone). They are still the
  best per-page element tables: a rewrite agent should open `docs/design/ui-ledger/pages.md` for
  the page it ports, then confirm against the code path cited here.
- `docs/ux-system/target-ia.md` is the decided information architecture (Phase 3, 2026-09-26),
  `docs/ux-system/kit-v2.md` (1,374 lines) the component spec, `docs/ux-system/teaching.md` the
  tips spec, `docs/design/ui-ledger/gesture-audit.md` the "no feature only behind a gesture" audit.
- Every Flutter widget test name for a screen lives under `test/` (1,665 test files overall). The
  owner said widget tests come later; logic tests are catalogued in `wt/native-plan/catalog`.
- Wherever this file says "plain words" it means the owner rule: never show a raw exception or
  server string as copy; plain sentence + way forward, technical text only under a Details fold
  (redacted). A rewrite must keep the **mapping tables** that produce those sentences
  (`lib/ui/agent_error_words.dart`, `send_failure_words.dart`, `git_words.dart`,
  `permission_presentation.dart`, `widgets/product_states.dart`, `widgets/connection_failure.dart`,
  `widgets/setup_ui_messages.dart`, `screens/recent_error_words.dart`, `domain/product_failure.dart`).

## 1. Owner UI rules that govern every screen (enforce in the rewrite)

1. **Kit only.** Every component comes from one kit; screens only arrange (layout, scroll, builders,
   semantics, focus). A missing part is added to the kit even if one screen uses it. Gate G16
   ratchets the count of non-kit widgets down. (`AGENTS.md`, `kit-v2.md` §9)
2. **No glass.** `Glass`/`Crystal` styles are removed (`ecf6069b3`); aurora off by default;
   flat lists, not boxes. Surface styles that remain: `solid` (default and Saver), `soft`, `brutal`,
   `paper`, `signal` (`lib/state/effects.dart`). Fine-tune knobs: corners (round/soft/sharp), depth
   (flat/raised), density (comfortable/compact), edges (hairline/none).
3. **Android-native feel to the maximum** (owner 2026-10-26): Material ripple/InkSparkle, predictive
   back, stretch overscroll, haptics (`KitHaptics`: send=light, commit=medium, select=selection click,
   done=confirm API30+, longPress=platform), system bars follow theme (`kit_system_bars.dart`).
4. **Motion 150-250 ms, subtle, reduced-motion respected** (`KitMotion.reduced`): system "remove
   animations" always wins over the app's Motion level (`full|calm|off`). Chat-specific motion is
   owner-approved (MOT-2, MOT-5): reply words fade in, reply glides, steps fold, chat grows out of
   its row (450 ms open, 300 ms close `KitMotion.rowReturn`), sent prompt lifts.
5. **Simplify and natural**: judge every idea by what it removes. No new bar/row levels (fold status
   into the existing status line or chip row). Obvious options are visible first-page rows, never in
   a "..." menu. Actions name their target ("Stop the server on this phone", never bare
   "Disconnect"). **Steer, never queue** where the backend can steer.
6. **One status line per window** (`KitStatusKind` priority: connection > appStopped > heat >
   riskySwitch > work > update > info). Screens contribute a `KitStatus`; the slot above the
   navigator owns drawing. Two indicators for the same thing is a bug (e.g. while the server pill
   shows Connecting the line is suppressed).
7. **Component hard max widths** (owner 2026-10-28): lists 720 reading / 960 list, panes 296/700/340,
   sheet 640, dialog 560/480, popover 200-320, control 400, chip 320, glyph 96, bubble 85 % /
   start inset 48 (`lib/ui/kit/kit_layout.dart`). Verified with extreme content at 280/412/1600 dp.
8. **Text scale**: app text size choice multiplies the phone scale (0.85/0.92/1.0/1.15) and the root
   caps at **2.5x** (`AppTheme.maxTextScale`, `KitText.appScale`). The owner's 2026-10-31 decision
   (frame/chrome text capped at **1.3x**, reading text scales fully) is **not in this checkout**
   (branch `fix/large-text-2026-10-31`): the rewrite must implement it.
9. **Arabic RTL** everywhere: `AlignmentDirectional`/`PositionedDirectional`, mirrored direction
   glyphs only (back, carets, reply, undo; set pinned by `test/app_iconography_mirroring_test.dart`),
   technical text (paths, ids, commands, URLs) always LTR via `KitBidi.auto`/`KitText.mono`.
   ARBs: en 9.5k keys, ar 10.5k (more plural forms), zh 8.7k, es 2.7k, ja/pt/ru 661 (partial; fall
   back to English). Locale fallback trap in section 9.
10. **Teach what is hidden** (owner 2026-10-21): every gesture/hidden feature ships a tip or mini
    tutorial registered in the teaching system and replayable in Help > Tips; a visible path always
    exists (SCREENS-NAV-25).
11. **Never idle copy that claims more than is verified** ("Connected" only when a probe proved it;
    "Sent" only on ack; receipts "Sending / Not confirmed yet / Not accepted").

## 2. Process model and entry points

One Android application id `io.github.eslamasabry.opencode_mobile` (preview build: suffix
`.preview`, own data and built-in Ubuntu, label "OpenCode Preview", `shortcuts_preview.xml`;
`android/app/build.gradle.kts`). `minSdk = max(flutter, 26)`. Same-signature in-place update is a
product requirement of the rewrite.

| Entry | Android component | Dart entrypoint | What it is |
|---|---|---|---|
| Main app | `MainActivity` (`FlutterFragmentActivity`, `singleTop`, `taskAffinity=""`, `adjustResize`, handles configChanges incl. orientation/screenSize/density/uiMode/layoutDirection, `enableOnBackInvokedCallback=true` app-wide) | `main()` -> `AppBootstrapGate` -> `OcApp` | the whole product |
| Manage space | `ManageSpaceActivity` (exported, excluded from recents; app info > Storage > Manage space replaces Clear storage) | `manageSpaceMain` (`lib/manage_space_main.dart`) | own tiny Flutter app: measures files, Export projects, Clear cache, Delete everything (explicit confirm). Reads only plain prefs (appearance, theme pack); no secrets, no connections |
| Widget floating host | `WidgetOverlayActivity` (translucent, `WidgetOverlayTheme`), `WidgetConfigureActivity` (APPWIDGET_CONFIGURE, exported), `WidgetRouteActivity` | `widgetOverlayMain` (`lib/widgets_overlay`) | transparent window with one kit panel: `/widgets/configure` (ground+accent), `/widgets/project` (which project the Conversations widget lists), `/widgets/steer`, `/widgets/voice`; talks only through `oc/widgets`; leaves at once when no bound widget |
| Scheduled run engine | headless engine from `ScheduledPromptRuntime.kt` | `scheduledPromptMain` (`lib/scheduling/entrypoint.dart`) | executes a due schedule (`oc/scheduled_prompts` `execute`), no UI |
| Widget action engine | `WidgetActionRuntime.kt` | `widgetActionMain` (`lib/background/widget_action_entrypoint.dart`) | temporary engine for widget Reply/Status checks, `oc/widget_action` (`checkStatus`, `cancel`); no UI bootstrap |
| Quick Settings tiles | `AttentionTileService` (needs-you count, opens Chats on Needs you) and `PauseTileService` (pause/resume background mode) | native | tile cache read from prefs `oc.attentionTile` |
| Widgets | `SessionsWidgetProvider`, `SessionsWideWidgetProvider`, `SessionsListWidgetProvider`, `SessionsStripWidgetProvider`, `UsageWidgetProvider`, `UsageWideWidgetProvider`, `StatusWidgetProvider` | native + `oc/widget_action` | tap actions arrive as launch actions / coding-alert extras (section 4) |
| Share target | `MainActivity` intent-filter `SEND text/plain` | `oc/share` | text becomes first prompt of a new session |
| Links | `MainActivity` intent-filter `VIEW` `opencode-mobile://session` and `://team` (DEFAULT+BROWSABLE) | `oc/link` | section 4.2 |
| Static shortcuts | `res/xml/shortcuts.xml` (`connect`, `new_task`; `${appShortcuts}` placeholder) | `oc/shortcut` | long-press launcher icon |
| Boot/update receivers | `BuiltinRestoreReceiver`, `ScheduledPromptReceiver` (BOOT_COMPLETED, MY_PACKAGE_REPLACED, TIME_SET, TIMEZONE_CHANGED, exact-alarm permission change) | native | restore in-app server, reschedule alarms |

Cold start sequence (`lib/main.dart`, `main_parts/bootstrap_gate.dart`):
`PerfTrace.markOnce('app.main')` -> (desktop only) `setUpDesktopWindow` -> `AppDiagnosticsController` +
`installAppErrorCapture` -> `runApp(AppBootstrapGate)` (renders "Opening..." **immediately** so a
prefs/Keystore failure can never leave Android on a blank native window) -> post-frame:
`PerfTrace app.first_frame`, `CrashDiagnosticsStartup.start`, `ReportProblemStartup.start` (neither
may delay the first frame) -> `AppBootstrap.create` (SharedPreferences + secure-storage profile
restore in groups of four) -> `ConnectionController` -> `migrateOlderDrafts()` ->
`recoverPendingPhoto()` -> `migrateNotificationPreferences()` -> swap to `ProviderScope(OcApp)`.

## 3. Navigation model

### 3.1 One Navigator, named routes, no router package

`MaterialApp` + a single `GlobalKey<NavigatorState>`; two `NavigatorObserver`s: `_TopRouteTracker`
(top route name, top page context, and the `appPageChanges` tick that makes app lines fold only when
a *page* changes) and `PerfTraceNavigatorObserver` (route timing).

| Route | Screen | Notes |
|---|---|---|
| `/` | `_Root` | decides destination (SCREENS-NAV-02) |
| `/servers` | `ServersScreen` | arguments: `null`, `'edit-active'` (opens active profile editor with password focused), or `ServersRouteRequest` (`connect(profileID, detectedRunning)`, `add({backend,initialUrl})`, `enterPhoneCredentials({profileID,openCode2})`, `edit(profileID)`, `forget(profileID)`) |
| `/home` | `HomeScreen` | `pushNamedAndRemoveUntil('/home')` is how flows "land" |
| `/guide`, `/about`, `/debug` | `GuideScreen(embedded:false)`, `AboutScreen`, `AppDiagnosticsScreen` | |
| `/this-phone` | `ThisPhoneScreen(kind: PhoneHostKind?)` | registered **only when `supportsTermux`** (Android) |
| `/chat/<sessionId>` | `chatLandingPage` (`ConversationBackendScope` + `ChatScreen`) | `ChatRouteArguments`: `discardIfUntouched`, `focusComposer`, `landOnRequestID`, `landOnFailure`, `menuAction: SessionMenuAction`; ctors `newlyCreated()` and `firstRun()` (new + keyboard up) |
| named non-path routes | `'linux-manager'`, `'phone-setup-start'`, `'phone-setup-progress'`, `'phone-setup-ready'`, `'phone-setup-progress-termux'`, `'termux-migration'`, `'team-phone-ready'`, `'local-terminal'` | names exist so notification taps can tell "already looking at it" |

Everything else is `pushKitPage(context, builder)` / `KitPageRoute` (unnamed). Sheets/dialogs are
`showKitSheet / showKitConfirm / showKitAlert / showKitInputDialog / showKitMenu` (never raw
`showModalBottomSheet`). Session ids pushed to `/chat/` are validated `^[A-Za-z0-9_-]+$`.

Navigation conventions a rewrite must keep:
- **Landing = `pushNamedAndRemoveUntil('/home', (_) => false)`** after connect, setup ready,
  migration, reset; **Servers is a root** when no profile is saved or sign-in must be re-entered.
- A chat is "already showing" when top route name == `/chat/<id>`: a second tap/notification does
  not stack a copy (`c5842e836`); a widget Reply on the open chat does `pushReplacement` with the
  composer focused.
- Conversations of other connections/agents open through `ChatsHost.openChat` ->
  `ConnectionController.openChatFeedItem` which switches the connection/project first, then
  `pushNamed('/chat/<id>')`; `ConversationBackendScope` swaps in the *conversation's own backend*
  (Claude Code/Codex on the phone, side OpenCode server) as `connProvider` override so models,
  approvals and the connection line are never the main server's; for an agent chat opened before its
  connection exists it shows `KitSkeletonTranscript` while `openBackendForConversation` runs.
- New-conversation page is a `fullscreenDialog` route (Close, not Back).
- `CloseRouteIntent` (Ctrl+W) uses `maybePop` so `PopScope` vetoes survive.

### 3.2 Page transitions and Back (hard-won)

`KitPageRoute` (`lib/ui/kit/kit_page_route.dart`, `motion/kit_page_transitions.dart`): on Android the
platform transition with **predictive back** (`KitAndroidPageTransitionsBuilder`), elsewhere shared-
axis fade-through (`KitMotion.standard`); duration zero and `popGestureEnabled=false` under reduced
motion; opaque, no barrier; `offstage` setter overridden so the Hero measuring frame does not hide the
page under reduced motion. A page that **grew out of a row** (`KitPageOrigin.mark(rowContext)` in
Chats) reverses in `KitMotion.rowReturn` = 300 ms with per-part reverse curves (bounds mirrored
emphasized; content leaves in first 40 %; ground returns in last 20 %; dim fades linearly) - commits
`c62155ff2`, `c4a24b05f`, `0a886b852`; the Chats list freezes its rows while a page above is moving
(`_held`), so the row it shrinks back into has not moved (`f7e775dd3`).
**Predictive back vs. the row transform** (demo lesson): the chat opts into predictive back only when
Back loses nothing - `PopScope.canPop` is true when the composer text equals the saved draft, nothing
is being recovered, and a page is beneath; the draft write and the "delete untouched new
conversation" happen *after* the page has gone (`f6c005423`); otherwise Back runs a local-only save
first and, if refused, asks (`_askLeaveUnsavedDraft`).

### 3.3 Back behaviour map

- Home (phone): `PopScope(canPop:false)`; first Back shows status "Press back again to exit" for 2 s
  (`_backExitWindow`), second within the window `SystemNavigator.pop()`. With a rail/sidebar: Files
  first unwinds its browser (`ProjectHubBackController.handleBack`), then non-Chats destinations
  return to Chats, then the double-back guard.
- Chat: described above. Session note, prompt editor, external task: `PopScope` asks before
  discarding unsaved work. Isolated-task sheet: Back = close/cancel waiting. Files: Back goes up a
  folder / clears search / closes keyboard. Project hub: Back leaves Files to the hub. Dev preview:
  Back steps the page's own web history first.
- Esc closes overlays; `StopOnEscape` stops a running reply with a hardware keyboard only when the
  chat is the current route, no text field is mid-composition, and find is closed.

## 4. Entry routing: shortcuts, links, shares, notifications, widgets, tiles

All of this lives in `lib/main_parts/*.dart` as extensions on `_OcAppState`. The shared pattern, which
the rewrite must reproduce as one reusable "pending intent" mechanism:

1. Native captures the intent **once**, **strips the extras from the Intent** (so a configuration
   change cannot replay it), whitelists/bounds it, and parks it.
2. Dart's inbound handler is installed first; then it calls `consume*` (that call is also the
   "Dart ready" ack - native only pushes live events after it).
3. Dart holds a `ValueNotifier` (`pending`) and **re-evaluates on every connection change**:
   *waiting* (a saved server is on its way and nothing has failed) shows a "waiting" status line and
   retains the item; *final* conditions (no profile, re-entry needed, connection failed) consume it
   with a notice and open `/servers`; ready -> route. Consumption is "only if still the pending
   one" so an item that arrived during handling is not swallowed.
4. Nothing is ever sent, created, or resumed on the person's behalf except where the action *is*
   "New task" (creates an empty session, never a message).

### 4.1 Launcher actions (`oc/shortcut`) - SCREENS-NAV-14

`LaunchAction` wire values (`lib/platform/launch_shortcut.dart`; native whitelist in
`LaunchShortcutChannel.kt`): `connect`, `new_task`, `activity`, `phone_setup`, `phone_setup_done`,
`usage`, `usage_settings`, `linux`, `background_settings`, and `usage_target:<sign_in|add_key|set_up>:
<providerId>:<profileId>` (regex-validated both sides: provider `[a-z0-9][a-z0-9_-]{0,31}`, profile
`[A-Za-z0-9._-]{1,64}`). Extras: `oc.shortcut`, `oc.shortcut.profile`, `oc.shortcut.session`.

| Action | Source | Route |
|---|---|---|
| `connect` | static shortcut | push `/servers` over whatever shows (unless already on top); connection untouched |
| `new_task` | static shortcut | waits for connection ready; `createSession()` then `pushNamed('/chat/<id>', ChatRouteArguments.newlyCreated())`; guards that api/repository/location revision did not change during create |
| `activity` | QS tile, sessions widget, shortcut | `OpenChatsIntent(needsYou:true)` at shell root (Chats + Inbox over it); no profile -> Servers + notice |
| `usage` | Usage widget row | `UsageHubScreen(initialSection: plans)`; no profile -> Servers + notice |
| `usage_settings` | Usage widget empty state | `UsageWidgetScreen` |
| `background_settings` | widget recovery | `NotificationsSettingsScreen(page: background, initialSection:'background')` - opens the setting, never enables it |
| `usage_target:*` | Usage widget row needing sign-in/key | `usageSignInDestination`: read-usage-from page, agent sign-in sheet, or the row's server's Providers page (connect that server first if not active); gone server -> Usage page + notice |
| `linux` | Status widget | `pushNamed('/this-phone', PhoneHostKind.inApp)` (Android only) |
| `phone_setup`, `phone_setup_done` | SetupService notifications | never waits for connection; `openPhoneSetupFromNotification` reads `setup.json` state: running/stopped -> progress screen; done & first setup & ready not yet shown -> ready screen; otherwise nothing; no-op if progress/ready already on top |
| pinned session (`SessionLaunch{profileID,sessionID}`) | dynamic shortcut | only for the **active** profile (other profile -> dropped with notice "other server", never switches silently); pushes `/chat/<id>` unless already top |

### 4.2 Deep links (`oc/link`) - SCREENS-NAV-16

Native `SessionLinkIngress.accepts`: non-empty, <=1024 chars, every char in `0x21..0x7e`; only
`opencode-mobile://session*` and `://team*` accepted; rejected links are consumed too (no replay).
Dart (`SessionLinkIntent`, strict parsers in `lib/domain/session_handoff.dart`, `team_link.dart`,
`session_address_link.dart`):
- `opencode-mobile://session?profile=<profileID>&session=<sessionID>` - ids validated by
  `SessionResumeCommand.isSafeIdentifier`; opens that session on the saved server with that id. Other
  saved server -> `connect` first, then `pushNamedAndRemoveUntil('/home')` + `pushNamed('/chat/..')`.
  Unknown server -> confirm sheet "Add server" (link carries only the sender's local profile id,
  never an address). Re-entry needed -> Servers + notice.
- `opencode-mobile://team/gate/<profileId>/<gateId>` and `.../run/<profileId>/<runId>` - route ids
  `^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$` (may contain `:`); open the project page with the gate sheet
  (or task conversation) when the server has `projectLifecycle`, else Chats on Needs you / Running,
  or `TeamConversation.route(runId)`. Never answers anything.
- `opencode-mobile://session/v2?...` (**address links**, P3.9, gated off by capability
  `sessionAddressHandoff`): parsed network-silent into `SessionAddressLink` (origin, instanceId UUID,
  sessionId; failure categories enum of 21 codes); a bottom sheet (`showSessionAddressSheet`) asks
  before every step that reaches out (check server, add server, verify sign-in); a link arriving while
  the sheet is up replaces it; only a found existing conversation navigates.
- The Session menu's "Open on another phone" shows the QR of the link (`qr` package, own
  CustomPainter) plus copyable text; optional "Include this server's address" switch.

### 4.3 Text share-in (`oc/share`) - SCREENS-NAV-17

`SEND text/plain`: subject + body (`subject\nbody`, unless body already starts with subject), trimmed.
Waits for a connected, non-loading server (status line "waiting for server", once). On ready:
`createSession()` (revision/identity checked as above) then pushes `ChatScreen(sessionID,
initialText: text)` - the text sits **unsent** in the composer. Failure keeps the text, status line
with **Try again**, More = Copy / Discard (Discard has Undo; text dropped only when the Undo bar goes);
a second failure says so. Outgoing share (`shareText`) excludes this app from the chooser.

### 4.4 Notification taps / coding alerts (`oc/background` `consumeCodingAlertOpen`) - SCREENS-NAV-18

Extras `kind`, `sessionID`, `profileID`, `monitorToken`, `reply`. `CodingAlertKind`: `permission`,
`question`, `complete`, `error`, `quota`, `quota_reset`, `checkin`, `team_decision`, `team_run_failed`,
`team_review`, `team_completed`, `team_progress`. Routing (`alerts_and_shortcuts.dart`):
team kinds -> `_openTeamLink`; quota/quotaReset -> verify the token still belongs to a source then
Usage > Plans (else notice "source changed"); a `monitorToken` (another saved server's watch) ->
`openMonitoredRequest` (revalidates profile, location and exact request, switch-server dialog, lands on
the request's **card in its conversation** - never answers from monitor metadata); `question` ->
chat landing on the question card; `complete` with a profile (widget row of another project/
connection) -> `openWidgetConversation` first, not found -> `/home` (never a broken chat), found ->
chat, composer focused when `reply`; everything else -> chat. The coding-alert open waits for
transport only when it has no token. `chatLandingRoute` names the route `/chat/<id>` so duplicates
collapse.

### 4.5 Scheduled-run notification (`oc/scheduled_prompts` `consumeOpen`) - SCREENS-NAV-19

Tap payload is resolved against stored schedules + history (`resolveScheduledOpen`, uses
`scheduledProfileFingerprint`), held up to **30 s** (`_scheduledOpenWaitLimit`) while the app's own
startup connection settles (a cold start used to race the root's connect and strand the user on the
list), then `openScheduledRun` (the run's conversation, switching server if needed) else the
schedule's history (`openStoredScheduledPrompts`). Deleted/changed routing never falls back to another
target. Re-consumed on every resume.

### 4.6 Capability enable flows - SCREENS-NAV-20

`registerCapabilityFlows` registers a handler for each of the 21 `KitEnableFlows` ids
(`addServer`, `addServerCodex`, `addServerPaseo`, `serverGeneration`, `modelSignIn`, `teamTurnOn`,
`teamHostGuide`, `mcpAdd`, `projectChoose`, `projectGitInit`, `quotaCollectorGuide`,
`addExternalAgent`, `phoneSetup`, `phoneSetupTermux`, `addToolClaude`, `voiceModelSetup`,
`allowMicrophone`, `allowNotifications`, `allowBackground`, `allowCamera`, `tailscaleSetup`). A
capability missing anywhere shows `KitCapabilityExplainer` ("X isn't set up - Turn it on") that runs
the handler; a flow the platform cannot run stays **unregistered** so the explainer explains rather
than offering a dead button. Doc: `docs/ux-system/capabilities.md/.json`.

## 5. Adaptive layout and desktop

### 5.1 Window classes (`KitLayout`, dp)
compact < 600, medium 600-839, expanded 840-1199, large >= 1200; short height < 480 keeps the
compact arrangement (phone landscape) and keeps modal parts at <= medium.

`KitNav.layoutOf`: compact -> `none` (**no bottom bar**, owner 2026-10-20); medium or short -> floating
**rail** (80 dp, icon + label always visible); expanded/large -> **sidebar** (296 dp, wider with larger
text up to 400 / 1/3 window) holding the shell controls and destinations; 2 to 5 destinations; an
unavailable destination is *absent*, never disabled. Destinations: Chats (badge = unified attention
count), Files (only when `ProjectHub.isAvailable(capabilities)` and not a phone), Settings.
`KitScreen`: single pane capped (reading 720 / list 960), `twoPane` (list 296 + detail <= 700),
`threePane` (+ side 340); `KitScreen.openDetail` fills the detail instead of pushing. Panes fold to one
column below detail min 360. Users of panes: Settings hub (list = search + five groups, detail =
group rows), Files (tree | preview with draggable divider on desktop), Team projects (three-pane).
Chat is always a pushed page (phone, tablet, desktop); width-capped & centred from tablet up.

Modal parts: sheet -> bottom sheet capped 640 (medium); centred dialog panel 560 on expanded+; full-
height sheet -> end-side sheet 400-480 on wide; modal max 90 % height; confirm 560 (medium)/480 dialog.

`finePointer` = desktop build OR a mouse connected: adds hover and tooltips, **never smaller targets**.

### 5.2 Desktop layer (`desktopInteractions` = `platformCapabilities.isDesktop`; additive, always
false on Android) - SCREENS-NAV-11 / 12

Keyboard (`lib/ui/desktop/shortcuts.dart`): every binding carries a modifier (Ctrl, or Cmd on macOS -
both accepted); no plain-letter accelerators so typing never fires one.

| Keys | Intent | Behaviour |
|---|---|---|
| Ctrl/Cmd+K | `OpenCommandPaletteIntent` | command launcher (also the shell's Search button on Android) |
| Ctrl/Cmd+N | `NewSessionIntent` | `createSession` + chat (surface may claim first) |
| Ctrl/Cmd+F | `FindInSurfaceIntent` | claimed by chat (find bar), Files (search field); else swallowed |
| Ctrl/Cmd+, | `OpenSettingsIntent` | Settings over current screen |
| Ctrl/Cmd+W | `CloseRouteIntent` | `maybePop` |
| Ctrl/Cmd+/ | `ShowShortcutsHelpIntent` | help sheet, grouped Anywhere / Conversation / Review |
| Ctrl/Cmd+` / `OpenTerminalIntent` | Terminal page (shell-level: pops to root then re-asks) |
| Ctrl/Cmd+1..3 | `SelectDestinationIntent(0..2)` | Chats / Files / Settings (phone: Files and Settings open as pages) |
| (no key) | `OpenChatsIntent(needsYou/running)` | notifications, tile, widgets, search |
| Ctrl/Cmd+Enter | send | composer (desktop Enter) |
| Esc | stop reply / close find / close overlay | see 3.3 |
| F2 / Shift+F2 | cycle recent model | `ModelShortcuts` (also hardware keyboards on Android) |
| F4 / Shift+F4 | thinking level | |
| Ctrl+B | move running work to background | |
| F3 / Shift+F3 | next/previous find match | |
| Up / Down at caret edge | prompt history | `PromptHistoryNavigation` keeps the original draft |
| PgUp/PgDn, Home/End | transcript pages / ends | `TranscriptKey` |
| `]` `[`, N/F7, P/Shift+F7 | review next/previous file/change | diff page |
| Ctrl/Cmd+C | copy selection / transcript | transcript is mouse-selectable only with a fine pointer |
| Shift+F10 / Menu / right-click | row context menu | `KitContextRegion` / `KitRow.menu`; every region also has a visible control |

Surfaces claim intents through `AppShortcutSurface` mixin/`AppShortcutSignals` (newest first, each
verifying it is the current route); fallback runs only when nobody claimed. **Off desktop only the bus
is installed (no key bindings)** so search results/notifications reach shell tabs through the same
intents.

Command launcher (`showCommandPalette`): kit sheet (centred panel on wide), search field, Enter runs
the first match (drawn filled), Arrow Down moves into rows. Empty state: **Go to** group (Needs you,
Files + project name, Projects, Settings, Servers, Terminal, Scheduled prompts, AI Team, Help) then
the **3 most recent conversations**; typing searches the same matcher as Settings search
(`lib/domain/settings_search.dart`: word/prefix/one-typo, label first), the app-wide `searchIndex`
entries (~110 hub rows/inside-page rows/places), up to 400 conversations (agent label + project in
hint, each opens on its own backend via `ConnectionChatsHost.openChat`) and up to 200 projects (open
that project's Files page).

Other desktop features: right-click menus; `DesktopFileDropTarget` (desktop_drop) on the composer
with highlight panel and failure alert (never exception text); persistent draggable scrollbars on
vertical scrollables with a controller (mouse deliberately **not** a drag device so selection works);
mouse text selection across the transcript; window state restore (`desktop_window_state_v1` JSON
`{x,y,width,height,maximized}` clamped to an attached display; default 900x700, min 480x600; title
"OpenCode"); window icon; `DesktopReleaseNotice` (GitHub release-tag check where Shorebird cannot
reach); Termux/voice/camera/QR/notifications/widgets/shortcuts all gated off via
`platformCapabilities` (`supportsTermux, supportsTailscaleHandoff, supportsVoice, supportsReadAloud,
supportsLocalPdf, supportsVoiceConversation, supportsPromptPhotos, supportsBackgroundService,
supportsNotifications, supportsHomeWidget, supportsLaunchShortcuts, supportsQuickSettingsTile,
supportsCodePush, supportsDesktopReleaseCheck, supportsUsbHostBridge, supportsQrPairing`) - a
capability-named seam so iOS/desktop answer in one file. Linux and Windows runners exist and are
experimental (`docs/desktop.md`); there is no macOS target.

## 6. Feature inventory

Format: **ID - name.** What it is / does. *Files.* Behaviours to keep. File paths are relative to
`lib/ui/` unless they start with `lib/` or `android/`. "Kit" parts live in `lib/ui/kit/`.

### 6A. Process, shell, navigation, system entry

**SCREENS-NAV-01 - App bootstrap gate.** `AppBootstrapGate` shows a kit state page *before* anything
loads: "Opening..." with a waiting progress; after **8 s** the same page offers Try again
(`onSlow`); a failure shows words + Retry + Copy details + Report a problem + **Start fresh**
(confirm sheet; waits for in-flight loads, closes phone agents (`closePhoneAgentsForSignInReset`),
erases saved sign-ins via `AppBootstrap.resetSavedSignIns`, reloads; a failed reset has its own copy
and retry). Generation counter invalidates stale loads. Installs the global `KitReportHook` so every
error state can "Report a problem" even if the app never started. *main.dart, main_parts/bootstrap_gate.dart.*

**SCREENS-NAV-02 - Root decision (`_Root`, route `/`).** In order: no profile -> `ServersScreen`;
`managedOpenCode2MigrationRequired` -> `ManagedMoveView` (automatic OC1->OC2 move, calm progress,
retries quietly while the phone server starts, failure with Details + Try again; `ManagedMoveRunner`
waits for Linux `true` up to 10 s per probe); `hasConnectedServer` -> `HomeScreen`; profile needs
password/Codex-token re-entry -> `ServersScreen`; else connecting: starts the saved profile
(`_connectSaved`), for the in-app server first `PhoneServerHealing.startForLaunch` (launch start gets
its turn before any "stopped" verdict - "no stopped flash", QA B7) then connects. Also: attaches
`PhoneSetup` finishers (BuiltinSetupFinisher / TermuxSetupFinisher: ends every setup job by
starting OpenCode and connecting), resumes a pending **Linux reset** (`LinuxResetFlow.resumeIfPending`,
shows setup progress), runs `AppExitRecovery.runOnce` (why Android last ended the app + revive phone
OpenCode and AI Team), starts the **thermal guard** (pauses AI Team while hot; filed in "While you were
away"), attaches `ReplyWatch` (times replies, keeps phone awake while a reply runs on in-app server).
The connecting page is `SavedServerConnectionCard` (SCREENS-NAV-73) in a bar-less `_Ground` + `KitScreen`
(`bodySays: {connection}` so the line is not said twice). *main_parts/root.dart.*

**SCREENS-NAV-03 - Opening shell with last-known titles.** While the saved server starts/connects the
root builds the **same `HomeScreen`** with `HomeOpening{rows,status,tone,onServer,notice}`: the server
pill says Starting/Connecting (or Offline when it needs the person, with the connection card above the
rows), the rows are last time's titles (`cachedSessionInventory`, <=100 labels, 64 KiB, plus saved agent
previews) as read-only stand-ins, filtered chips disabled, New chat not floated. When the server answers
the root builds the same widget without `opening` so **nothing moves** (jitter J2, `c45c02031`); stand-ins
stay until the live list has rows or has been read empty (or 10 s). Nothing marks the server connected or
fills live maps from the cache. *screens/home_opening.dart, home_screen.dart, chats_home_screen.dart.*

**SCREENS-NAV-04 - Route table / navigator model.** See section 3.1. Also `ServersRouteRequest`
handling in `ServersScreen.didChangeDependencies` (one-shot, post-frame). *main.dart `routes`/
`onGenerateRoute`, navigation/chat_route.dart, navigation/attention_landing.dart.*

**SCREENS-NAV-05 - Page transitions, predictive back, grow-from-row.** Section 3.2. *kit/kit_page_route.dart,
kit/motion/kit_page_transitions.dart (`KitPageOrigin`).*

**SCREENS-NAV-06 - Home shell.** `HomeScreen`: `KitNav` + `KitScreen` + `KitTabSwitcher(lazy:true,
preload:{chats})` (destinations built on first visit - no hidden tab builds or reads at startup).
Phone: no destinations; `KitTopBar.shell(KitShellControls)` = server pill (name, "+N" other shown servers,
status word Connected/Connecting/Reconnecting/Offline/"Switching", tone), Search, Settings gear.
Rail: same bar, rail names the destination. Sidebar: controls are the sidebar header, pane has no bar,
Chats' own large title. Settings tab shows search only (no current server, SC3). Files row when the server
has no project tools: `KitRow.unavailable` "Project is not available on <server>" with "Switch server",
shown after the person was on Files and the server changed (`_projectWentAway`, STATE-12); a shortcut to
Files on such a server explains in a sheet. `FirstRun.markLanded/markReturning` after first answer; if
SSE cannot connect `enablePollingFallback`. Shortcut surface handles `SelectDestinationIntent`,
`OpenChatsIntent` (Needs you = Chats + Inbox opened after frame; Running = filter), `FindInSurfaceIntent`
(Files), `OpenProjectToolIntent(files|search)`, `OpenTerminalIntent` (always the Terminal page - it
explains why when the server keeps no terminals). Server pill tap -> SCREENS-NAV-08 then acts on the
returned `ServerSwitcherChoice`. *screens/home_screen.dart.*

**SCREENS-NAV-07 - App-wide status line + one-shot notices.** `AppConnectionStatusScope` (above the
navigator) merges conditions in priority order from one source each: connection (offline / reconnecting /
not answering with 8 s clock shared by every line; certificate-changed hides not-answering), Android
stopped the app (`AppExitNoticeLine`, folds on page change not on a timer - jitter fix `39be428aa`),
heat (`ThermalNoticeLine`, once per episode), risky switch (never dismissible), work, update
(`ShorebirdUpdateNotice` / `DesktopReleaseNotice` - "Update ready", lowest). The app's own one-shot line
(`_say`, `_showWaiting`, share failed) is `KitStatusKind.work`; auto-clears after `KitMotion.undoWindow`
except under accessible navigation (waits for Dismiss). Snackbars exist only for done-with-Undo (KIT-34);
a failure with no part to sit on is a blocking alert (`showProductError`). `AppConditionsScope` merges
complete conditions *including changed callbacks* - no equality shortcut may keep an old profile's
actions alive. `ConversationStatusScope` replaces the app line with the conversation backend's own.
*widgets/app_connection_status.dart, kit/kit_status_line.dart, kit_status_slot.dart, widgets/conversation_status_scope.dart,
widgets/app_exit_notice.dart, thermal_notice.dart, background_pause_notice.dart.*

**SCREENS-NAV-08 - Server switcher sheet.** From the pill: one `showKitSheet` "Servers", every saved
server one compact row with plain state ("Connected", conversation count or "Needs you - N", "Not
answering", sign-in needed, "Switching"...), same-host servers told apart by port, "This phone" rows
(in-app OpenCode / Termux server / Claude Code daemon via `LocalServerRow`, with their ⋯ actions
`PhoneServerAction`), waiting requests of other servers as tappable rows (`ServerSwitcherOpenChat`),
"Add a server", leave/disconnect (named target, confirm). **The sheet only chooses**: it returns a sealed
`ServerSwitcherChoice` (`OpenServers(request)`, `OpenPhoneSetup`, `OpenChat(item)`, `PhoneAction(...)`,
`Leave(alreadyDisconnected)`) and the shell acts after the sheet closed, so navigation never runs from
a context being torn down. Connecting/credentials/adding/forgetting have **one** implementation
(ServersScreen). *widgets/server_switcher_sheet.dart (979), local_server_row.dart, phone_server_card.dart.*

**SCREENS-NAV-09 - Connection details sheet.** `showConnectionDetailsSheet`: what is going on, what to
check, raw error under Details, Try again, Switch server; failures are diagnosed by
`ConnectionFailure.diagnose` (same words as the root card). Banner actions: Update password/token ->
`/servers` with `'edit-active'`; certificate review -> `ServersRouteRequest.edit`. *widgets/connection_status_banner.dart,
connection_failure.dart.*

**SCREENS-NAV-10 - Search / command launcher / Go to.** Section 5.2. Index in
`search/search_index.dart` (`SearchEntry{id,title,keywords,kind(hubRow|insideSettings|destination),
icon,gate,serverGate,group,parent,pages,target,open}`; ids are widget keys; `hiddenByServer` counts
rows the connected server hides, shown as one muted line "N settings aren't available on this server -
Why"). `searchIndex` = `hub_rows.dart` (~40), `inside_pages.dart` (~35 incl. notifications sections,
appearance, usage, tools tabs, phone/Linux pages, Claude Code), `places.dart` (tabs, project tools,
all/archived conversations, AI Team agents/demo, AI Team). `go_to_commands.dart` builds the empty
state. A UI-ledger coverage test joins entries to ledger pages via `pages`. *search/\*.*

**SCREENS-NAV-11 - Desktop keyboard layer.** Section 5.2 table. *desktop/shortcuts.dart (816),
widgets/model_shortcuts.dart.*

**SCREENS-NAV-12 - Desktop interaction extras.** Right-click menus, file drop, scrollbars, selection,
window state/icon, release notice, platform capability gates. *desktop/\*, lib/desktop/\*, update/desktop_release_check.dart.*

**SCREENS-NAV-13 - Adaptive layout.** Section 5.1. *kit/kit_layout.dart, kit_nav.dart, kit_screen.dart,
kit_max_width.dart.*

**SCREENS-NAV-14 - Launcher / tile / widget launch actions.** Section 4.1. *main_parts/launch_route.dart,
platform/launch_shortcut.dart, android `LaunchShortcutChannel.kt`, `res/xml/shortcuts*.xml`.*

**SCREENS-NAV-15 - Pinned-session launcher shortcuts.** Dart owns payload `{profileID, sessions:[{id,title}]}`
(<=4 sessions, titles cut to 80 chars) pushed over `oc/shortcut` `setPinnedSessions` ->
`PinnedSessionShortcuts.publish` (ShortcutManagerCompat; shortcut id `oc.session:<profile>:<session>`;
stale entries are **disabled**, not removed, so user-pinned copies grey out with a reason and never show
an old title; a deleted/disconnected server withdraws all). Last published set remembered in prefs
`oc.pinnedShortcuts`. Pins themselves: `oc.sessionPins.<profile>` JSON `{scope:[sessionId...]}`.
*background/pinned_session_shortcuts.dart, state/session_pins.dart, android PinnedSessionShortcuts.kt.*

**SCREENS-NAV-16 - Deep links.** Section 4.2. *platform/session_link.dart, main_parts/link_routes.dart,
widgets/session_address_sheets.dart, widgets/session_handoff_sheets.dart, android LaunchLinkChannel.kt, SessionLinkIngress.kt.*

**SCREENS-NAV-17 - Text share-in.** Section 4.3. *main_parts/share_route.dart, platform/share_intent.dart, LaunchShareChannel.kt.*

**SCREENS-NAV-18 - Notification-tap routing.** Section 4.4. *main_parts/alerts_and_shortcuts.dart,
navigation/attention_landing.dart, screens/profile_monitor_screen.dart (`openMonitoredRequest`).*

**SCREENS-NAV-19 - Scheduled-run notification routing.** Section 4.5. *main_parts/schedule_route.dart.*

**SCREENS-NAV-20 - Enable-flow registry.** Section 4.6. *capability_flows.dart, kit/kit_capability_explainer.dart.*

**SCREENS-NAV-21 - Lifecycle in the shell.** `didChangeAppLifecycleState`: resumed -> `PhoneServerHealing.setForeground(true)`,
consume coding-alert open **in parallel with** transport recovery (`Future.wait`; destination capture
starts before slow health/catalog requests), `_resumeTransport` (healing check for in-app profile +
`resumeFromLifecycle`, then `connectIfNeeded`), consume scheduled open; hidden/paused/detached ->
`suspendForLifecycle()`; post-first-frame `restoreBackgroundLiveMode`. `registerCapabilityFlows`,
`inAppAutoUpdateProvider` (keeps in-app server on the pinned OpenCode when idle) read at init. *main.dart.*

**SCREENS-NAV-22 - Secondary Flutter entrypoints.** Section 2 table. Manage space app
(`screens/manage_space_screen.dart`: `ManageSpaceScreen`, `ProjectExportScreen` - "This phone > Export
projects", same export any time; controller `ProjectExportController` over `oc/project_export`). *manage_space_main.dart,
widgets_overlay/\*, scheduling/entrypoint.dart, background/widget_action_entrypoint.dart.*

**SCREENS-NAV-23 - Root theming.** `MaterialApp.builder` stacks (outer to inner): `ProductErrorLocale`
(error words follow app locale without call-site plumbing), `Theme(AppTheme.forLocale)` (Arabic font
etc.), `KitText.appScale` (text size x phone scale, cap 2.5, eased), `AppConnectionStatusScope`,
`UpdateStatusScope`, `ShorebirdUpdateNotice`, `DesktopReleaseNotice`, `AppShortcuts`, `KitEffectsScope`
(Saver => `effects.underSaver`), `kitStyleTheme` (design school ground). `themeMode` from appearance
(system/light/dark/**saver** = dark + AppTheme.saver). Theme packs (`ThemePackId`: opencode, catppuccin,
gruvbox, solarized, dracula, nord, tokyoNight, oneDark, monokai, rosePine, everforest, kanagawa, ayu,
nightOwl, github, palenight, synthwave, cobalt, midnight, paper, forest, ocean, sunset, sakura,
lavender, ..., `dynamic` last = Material You via `dynamic_color` `getCorePalette()` harvested into
`harvestedDynamicPack`; falls back to OpenCode pack below Android 12). Accent override
(`appearanceAccentById`). `KitScrollBehavior`. Locale: `locale: appLocale` (null = system),
`localeListResolutionCallback: resolveAppLocales`. Persist keys in section 7. *main.dart, app_theme.dart,
theme_packs*.dart, kit/style/\*.*

**SCREENS-NAV-24 - First run, welcome, nudges.** `FirstRun` (prefs `oc.firstRun.state` armed|pending|done,
`oc.firstRun.notifyAsk` pending|answered): a device is "new" only if the welcome (no saved servers) was
the first thing it showed; an upgraded install never sees it; first run ends on Chats (no empty
conversation opens). Servers welcome page = `servers_welcome_scene` + choice "this phone" vs "my computer"
(`_WelcomeChoice`) + found-Termux lead. `PhoneSetupWelcomeEntry` states cost line from the setup
registry's real estimates. The one first-reply notification offer (`FirstReplyNotifyOffer`,
`chat_notices.dart`). Nudges (`state/nudges.dart`, ids `approvals`, `review-changes`, `leave`, `compact`,
`pin`; prefs `oc.nudges.shown|projects|firstReplySeen`) are one slot above the composer (`NudgeSlot`),
never while a question card waits, each tied to a real moment. *state/first_run.dart, chat/nudge_slot.dart,
state/nudges.dart, state/conversation_nudges.dart.*

**SCREENS-NAV-25 - Teaching system.** Registry `teachingRegistry` (`teaching/teaching.dart`): tips
`stop-after-step` (a reply ran 10 s and Stop offers "Stop after this step"; priority 2), `send-later`
(words wait and Send offers "Send later or repeat"), `hold-mic` (mic used with a tap, box empty again);
tour `make-it-yours` (4 steps over Appearance chips, offered once after first reply, "Not now" is an
answer). `TipController` (`oc.tips` JSON, app-wide): one tip at a time, 2 min cool-down, <=2 shows per
tip, one new tip per screen visit, never while a question card waits, keyboard over anchor, or onboarding.
Help > Tips (`settings/tips_screen.dart`) lists every entry with "Show me" and "Show tips again"; a test
fails when an entry has no Help row or no en+ar copy. Widgets: `KitTip`, `KitTour`. *teaching/teaching.dart,
state/tips.dart, screens/settings/tips_screen.dart.*

**SCREENS-NAV-26 - Plain-words layer.** Section 0. Also `locale_format.dart` (money/counts via intl so
Arabic gets Arabic-Indic digits), `early_l10n.dart` (strings safe in `initState`/after `await`),
`widgets/relative_time.dart`, `domain/relative_age.dart`. *see list in section 0.*

### 6B. Conversations list and starting

**SCREENS-NAV-27 - Chats home.** `ChatsHomeScreen` is the home: large title "Conversations" with a
tappable "N need you" line (opens Inbox, amber), filter chips (project summary chip, connections chip
only when >1 live connection, **Running** toggle), quiet lines that scroll with the feed (only-project,
incomplete + retry naming each connection once, agents' notices), `KitStatusLine` for unreachable
connections ("X isn't answering - Try again", hideable with x until the set changes), sections
**Today / Earlier** (a "needs you" row sorts first inside its group; no own group), `KitFeedItem` rows
(agent mark + label only when the feed mixes agents/servers, title via `presentedSessionTitleText`
("New conversation" for dated placeholders), state word Needs you / Working (+ live `running` summary) /
Failed / Stopped / Done / "Can't reopen", relative time, project label), pull-to-refresh
(`KitRefresh`), skeleton rows while loading, entrance fade+8 dp rise for late-arriving rows only
(first painted rows play none), floating **New chat** button (hidden when the empty state already offers
it), calm empty states (empty, running-empty, filtered-empty, project-empty, "Connect a computer" only
when none saved). Behaviour: feed list `ChatFeedSource.chatFeed(filter)` (`ChatFeedFilter{projectDirectory,
running, needsYou, otherFolders, agentId, includeSubagents}`); stops rebuilding while covered by a page
and while a page above moves (`_held`, `89828a3c8`); a row being opened says "Opening..." and the bar
runs (an old Claude conversation takes seconds to reopen); an agent chat that cannot reopen asks first
(`agentResumeNotice.requiresAcknowledgement` -> confirm "Start new conversation" ->
`openNewChatReplacing`). Width reading (720) centred on wide. Developer-flag FactsBook "Memory" section
above the list. *screens/chats/chats_home_screen.dart (1004), chats_host.dart (`ChatsHost` seam,
`ConnectionChatsHost`), widgets/older_sessions_pager.dart, last_known_sessions.dart.*

**SCREENS-NAV-28 - Request cards inside the list.** A needs-you row shows its request card under the row
(permission: command/file named, Allow once / Reject with Undo window; outside-project asks name the
write and the file; question/form rows open the conversation, owner decision 1) so most asks are
answered without opening the chat. Buttons are secondary in lists (the screen keeps one primary). *chats_host.dart
`listRequest`, chat/permission_sheet.dart `permissionRequestCard`.*

**SCREENS-NAV-29 - Project / connection filters.** `showChatsProjectSheet` ("Show chats from": All
projects with total, each project, "Other folders" (temp/home/root), "Open a project..." which closes
the sheet first - one sheet at a time - then opens the Open-a-project sheet and filters to it);
`showChatsSourcesSheet` ("Connections in this list": one switch per connection - each saved OpenCode
server, the phone agents; the active one cannot be hidden; `oc.chatListShown.<profile>`). *chats/chats_project_sheet.dart,
chats_sources_sheet.dart.*

**SCREENS-NAV-30 - New conversation.** `showNewChat` -> `NewChatScreen` (fullscreen dialog): centred
title + Close, glowing question field "What would you like to build?", large **agent cards** for the
agents this connection can start (OpenCode, Claude Code, Pi, Codex... from `AgentCatalog`/phone agents;
agent chip checked against what the phone can run now, stopped helper restarts), one Model + Project
group (model picker, project chooser, "In a separate copy" switch when a worktree can be made), pinned
**"Start with <agent>"**. Typed words are kept as a draft per project (`oc.draft.new-chat.<base64url project>.<profileId>`,
`KitDraft`) until sent; an offered text (FactsBook summary) is not the person's own and is not draft-
saved. Sending creates the session in the chosen project with the text as first message and *replaces*
this screen with the chat. Failure in words + Details + Retry; a Git problem swaps Retry for the **Git
repair** action (SCREENS-NAV-32). Wide windows keep reading width. *chats/new_chat_screen.dart (694).*

**SCREENS-NAV-31 - Import from Claude Code.** `showClaudeImport`: lists the person's Claude Code
conversations for this project (what it was about, project folder, last used; never ids); tap imports
into this server's list and returns the new id. Only where `ProviderConversationImportGateway.providerImportSupported`.
*chats/claude_import_screen.dart.*

**SCREENS-NAV-32 - Git repair sheet.** `showGitRepairSheet(project)`: starts at once, says how it goes,
then what happened/next; true when Git can read the project again. Triggered by
`ProductFailureCategory.projectGit` on start. *chats/git_repair_sheet.dart, git_words.dart.*

**SCREENS-NAV-33 - Start in a separate copy.** `showIsolatedTaskSheet`: asks the task, makes a git
worktree on its own branch, waits for the project's setup, opens a conversation in the copy and sends
the task; Back closes and stops waiting. Also `ChatsHost.separateCopyProject/startSeparateCopy`; hidden
for folders with no commit. *isolated_task_sheet.dart (726), state/isolated_task_launch.dart.*

**SCREENS-NAV-34 - All conversations.** `GlobalSessionsScreen`: search-first list across projects with
one project filter menu, Active/Archived choice, one panel per project, "Continue this session here?"
sheet when the session's folder differs, load-more on scroll (automatic; visible Try again on failure),
pull to refresh. Reached from `/sessions` command and Search ("all-conversations", "archived-conversations"). *global_sessions_screen.dart (1189).*

**SCREENS-NAV-35 - Inbox.** `openInbox`/`InboxScreen`: one calm list of everything that waits on the
person across connections, oldest first; each row = the question, where it waits (server - project -
conversation); permissions answerable in place (exact command, Allow once / Reject, same Undo window as
chat); questions/forms open their conversation; empty: one calm line + conversations that finished and
were not opened. Entry: "N need you" line, Search Go to, notifications, QS tile, widgets. Rows land on
the card (`landOnRequestID`). `inboxCount` = what it lists. *inbox/inbox_screen.dart, inbox_items.dart
(`InboxFeedEntry`, `InboxWatchedEntry`).*

**SCREENS-NAV-36 - Saved-server monitor inbox.** `ProfileMonitorInbox` rows for requests on watched
servers the app has no live connection to: kind + conversation only (never the command), cannot be
answered from here; tap -> `openMonitoredRequest` with switch-server dialog. Counts sit on the switcher
and server rows (owner rule R4). *screens/profile_monitor_screen.dart, state/profile_monitor.dart.*

**SCREENS-NAV-37 - Older sessions / last-known.** `OlderSessionsPager`, `LastKnownSessions` (titles saved
from last time shown read-only while connecting). *widgets/.*

### 6C. The conversation (chat)

`ChatScreen` is one Dart **library of 71 files / ~27.7k lines** (`screens/chat_screen.dart` 801 lines +
70 part/helper files in `screens/chat/`) treated as a single-owner unit. Mixins/extensions group state:
`_ChatDraftFields`, `_ChatCommandFields`, `_ChatSessionMenuFields`... A rewrite should model it as
one conversation controller (state holder) + stateless composables, not a god screen.

**SCREENS-NAV-38 - Chat frame and lifecycle.** Constructor: `sessionID, initialText, discardIfUntouched,
focusComposer, landOnRequestID, landOnFailure, menuAction, showAppBar, watch (ChatWatch), hostKeyboardUp`
(+ `isolated` demo mode via `ConnectionController.isIsolated`). Wraps: `PopScope` (3.2),
`KitLinkScope(claim: _claimDevLink)` (localhost links open Preview), `StopOnEscape`, find shortcuts
(Esc/F3), `ModelShortcuts`, `SessionViewObserver` (read acknowledgement only while the transcript is
visibly read: `ready: !_loading && _error==null && !_awayFromLatest`), `MarkdownInteractionScope(enabled:
false)` in demo. Page frame = `KitScreen` (full width on phone, conversation centred at its cap from
tablet up). One loading bar for first load **and** reconnect (label names the backend). Opening speed
contract: while history loads, show the **saved tail excerpt** read-only (`cachedSessionTail`, <=12
redacted excerpts of 1,000 scalars: text only, no reasoning/tools/attachments/errors/credentials/
cursors); `chat.open_to_first_transcript` is recorded with `source: saved_excerpt|live`. Perf-trace
spans: `chat.open_to_fresh`, `chat.send_to_ack`, `prompt.sent/accepted/first_token/turn`. Page reads go
through `chat_page_cache.dart` (connection fields + display passes cached so a rebuild that changed
nothing costs nothing; streaming into the newest turn must not recompute the whole history).
*chat_screen.dart, chat/chat_page.dart, chat_page_cache.dart, chat_body.dart, chat_start.dart.*

**SCREENS-NAV-39 - Chat top bar.** `KitTopBar`: title (session title; for a watched team worker the task
title), scope line (project, plus server name when several connected) - tapping the **project chip
opens that project's Files** (`openProjectFiles`), actions in order: Stop reading (when read-aloud is
active), **Collapse all steps** (only while any step/fold is open), **running-work count** (`workCount`,
opens SCREENS-NAV-57, only when `projectManagement`), demo "Review changes", watch details; accessory =
**context ring** (`KitContextRing`, appears from 50 % of the model window, compacting state, tap opens
context details sheet with per-reply chart and "Compact now" when supported; usage remembered via
`contextUsageMemory` for the list); overflow menu = SCREENS-NAV-55. Display toggles with Undo bar:
reasoning expanded (`oc.transcript.reasoningExpanded`; flipping drops per-part overrides so one flip does
not erase choices), message timestamps (`oc.transcript.timestampsVisible`). *chat/chat_top_bar.dart.*

**SCREENS-NAV-40 - Chat status line and states.** One status line (priority: connection with *backend's
own words* - Claude Code "isn't answering", not "OpenCode is reconnecting" -, then message not sent,
prompt refused, staged revert, subagent n of m, shared-by-link, tail-excerpt note). A busy phone server
is not a dead one: quiet reconnect banner + composer note (`4208e6108`). States: first load skeleton
(`KitSkeletonTranscript`), load error page state with Details/Retry, opening excerpt. *chat/chat_states.dart,
chat_status_line.dart, no_reply_reason.dart (why the newest prompt got no reply: send wall, host
`lastError`, or `AgentTurnFailureMemory` after restart), assistant_error_row.dart.*

**SCREENS-NAV-41 - Transcript turn model (STATE-16).** Vocabulary at the top of `message_view.dart` /
`transcript_turns.dart`: **prompt** (your words+attachments) -> **turn** (prompt + everything until the
agent hands back) -> **step** (one server assistant message) -> **notice** (system line nobody typed:
context added, instructions updated, moved, compaction) -> **reply** (prose). One "more" control per
turn on the *last visible* step (turns often end on an empty bookkeeping step); prompts have no button
(long-press exposes menu to semantics) but every message has a visible "Message actions" disc (44 dp).
Work (reasoning + tools) of a stretch is pooled into the step that began it and folded under **one**
`KitWorkLine` when 2+ rows ("N steps"); a one-line reasoning right before a tool titles that row; a
multi-line thought titles a step only when its first line is a marked heading (bold/`#`). Folds open/red
only when the work *ended* on a failure. OpenCode 2 notices have role `user`: always use `_isPrompt /
_endsTurn / _turnSteps`, never `role != 'assistant'`. OC2 re-encodes attached images, so
`_matchesPendingPrompt` matches on text+count+file name only. Turn footer holds meta (time, tokens/cost
when toggled, model switch markers naming the model not its id). `TranscriptMarker` for model/agent/
location switches and compaction line; `BackgroundShellResultRow`, `BackgroundAgentResultCard`,
`TranscriptNotice`, `V2TranscriptRow` (unknown `v2:*` tag degrades to a generic notice, never crashes).
Sent photos as thumbnails under the prompt (data: URI or `file:///` read through the file transport; web
URLs never fetched). No boxes/frames: new elements are lines sharing the prose's left edge. *chat/
message_view.dart, transcript_turns.dart, transcript_rows.dart, chat_transcript.dart (uses
`ScrollablePositionedList`), sent_photos.dart.*

**SCREENS-NAV-42 - Streaming, live line and scroll.** Stream events fold into the transcript with
**coalesced** deltas (bounded rebuilds: `chat_stream.dart` counter asserts a burst of N deltas gives few
rebuilds); the live turn has typing caret after the words (`KitStreamCaret`), soft sweep over the live
line's words (owner choice C, replaced the pulsing dot), reply grow animation that "keeps its speed when
a line lands". Follow-latest on by default; "away from latest" = **480 px** scroll threshold;
"Jump to latest" pill; earlier-messages pill (rebuilt only when its count changes); after a send the
newest message stays in view until the person scrolls; sending from further up shows the new message;
jumping to the newest stops above the composer; no "Jump to latest" over a short chat after a find.
`_liveEndSlack = 24`. Stale live state is settled for drawing only, never in the message list;
OpenCode 2 turn stays "running" between steps and lost tool results settle. A compaction mid-turn must
not break the newest row. *chat/chat_stream.dart, chat_scroll.dart, chat_transcript.dart, chat_history.dart.*

**SCREENS-NAV-43 - Tool rows.** `ToolCard` -> `KitToolRow`: one line = what it did, where, how it went,
opening to its note and output; sub-agent the host can open is the plain agent line; `TitleWithDetail`
(title keeps natural width up to 2/3 of the row's real room). Tool kinds handled by name in
`tool_card_contract.dart`: read, list, glob, grep, bash/shell, edit, write, patch/apply_patch, webfetch,
websearch, task/subagent, todowrite/todo (`MobileTaskList`: "2 of 4 done" + plan order + "High priority"
chip), question, lsp, plan, worktree_setup, skill, sendmessage, toolsearch + generic. Param aliases
(`filePath`/`file_path`/`path`). Output preview via `SmartTextPreview` (Markdown prose with Rendered/
Source, JSON laid out, code coloured, "Show all" opens viewer; tool-output previews load their file once).
A failed tool call is still and neutral with Retry (10A). A shell step offers "Run this command again"
(prefilled dialog). Exit codes 143/130/137 read "Stopped". *widgets/tool_card*.dart, mobile_task_view.dart.*

**SCREENS-NAV-44 - Markdown and agent blocks.** `KitMarkdown` (tables, code blocks with copy,
images, find-mark highlight at render time without reparsing; patterns compiled once). Fenced agent
blocks: ```` ```choices ```` (tappable rows -> `AgentChoiceScope` handler or copy), ```` ```checklist ````
(read-only, no strike-through), ```` ```command ```` ("Run on your computer", copy per line). File-path
inline code becomes a link **only after** `MarkdownFileLinks.validate` proves the file is readable on the
connected server. Every link the app did not author -> `openExternalLink` (https only unless explicit
HTTP review; credentials/odd ports rejected; confirm dialog; **never launch a server-supplied URL
directly**). Localhost dev links are claimed by Preview (SCREENS-NAV-58). Code reader page for long code.
*widgets/markdown.dart, agent_blocks.dart, external_link.dart, kit/chat/kit_markdown*.dart.*

**SCREENS-NAV-45 - Agent cards (gen-UI) and connector cards.** A completed `oc-ui` tool call renders as
a native card in place of its tool row (`AgentCardView`): waiting (answerable once agent idle), held
(collapsed with Undo), sending, delivery unknown, failed, answered (read-only receipt), passed over ("Not
answered"), report, unreadable. Asks: choice list, form (`AgentCardForm`: one kit control per field,
required fields hold Send, never autofilled), confirm pair, photo ask (`StoreAgentCardPhotos`). A held
answer returns exactly as entered (`AgentCardDraft`). `ConnectorCardView`: the agent recommends a
catalogue MCP connector (name, reason, where it runs, lasts while server runs, one Connect with progress,
sign-in through external-link gate). *chat/agent_cards.dart, widgets/agent_card_*.dart, connector_card.dart,
domain/genui/.*

**SCREENS-NAV-46 - Requests: permission, question, form.** `attention_card.dart`: one `KitRequestCard`
above the composer per request, **one at a time, oldest first** (card says how many more wait); common
answer in place (Allow once / Reject), one Details opening the request sheet, receipt after answering
(Sending / Not confirmed yet / Not accepted - `PermissionAnswers` ledger shared by card and sheet,
K2 §4.8). A request arriving mid-sentence never takes composer focus. Permission sheet
(`showPermissionDetails/showPermissionSheet`): whole command/file/pattern list, change as read-only diff,
**"Always allow" is a risky switch that states its scope first** (replaces the old "Confirm broader
access" dialog; 13B: one-step confirm "Always allow <command> in this project?"); third identical ask
invites Always allow once per server+scope (`AlwaysAllowInvitation`, consent in flow, only where the
server keeps saved grants). Question sheet (`showQuestionSheet`): each prompt's choices (one/many), own
answer where allowed, Send says why it cannot, failed send stays with Try again, Dismiss confirmed
first (not restorable). Forms (`FormRenderer`, protocol notes section 8): fields string/number/integer/
boolean/multiselect/external/unknown, `format` date/date-time pickers, conditional (`when`) slots folded
with `KitReveal`, >=5 declared fields or description >140 chars -> full-height (side sheet on wide),
Dismiss confirm, throw-keeps-open error notice, kept answers. Landing: `landOnRequestID` marks the card
once (`KitArrivalScope`); landscape request card scrolls from its question; no Thinking line while a card
waits. External form-field links go through `openExternalLink`. *chat/attention_card.dart (754),
permission_sheet.dart (748), permission_ask.dart, question_sheet.dart, form_flow.dart, widgets/form_renderer.dart,
permission_presentation.dart.*

**SCREENS-NAV-47 - Composer.** `KitComposer` = one solid pill: "+" , field, one trailing control (mic while
empty, Send once words; **Stop is NOT here** - owner 2026-10-30: Stop sits at the trailing end of the chips
row above the pill for the whole running turn, amber when stuck; the field shows Send or mic only).
Field height cap 40 % of window. Strip above (`KitComposerStatusStrip`, scrolls sideways, never wraps):
automatic-approval chip (opens approvals sheet), "context waiting", "Background", agent features chip,
Stop, phrase chips row, and the **model chip** at the end (collapses to a glyph below ~6 chars). Hints:
"Ask <Agent>", "Waiting for you" (no glow, no Stop while blocked on a request - a typed message still goes
to the running turn), read-only/blocked reasons ("a Claude sub-agent answers only its main conversation"),
offline. "+" sheet (`_PromptTool`): Commands, Attach file, Web sources, Photo library, Camera, Voice,
Conversation (voice), Reuse a prompt, Clear text, Saved prompts, File from the project, plus this
conversation's own settings as rows at the top (model, approvals incl. Auto-approve switch, agent plan,
send running work to background) - they used to be chips (owner decision 4, 20 Oct). Glow around
the composer while a reply runs (liked by owner; opt-in "Glowing border while replying" switch;
`KitGlowStyle` classic|softRing). First Send press sends (picker keyboard). Notes wrap whole and stay
until the next change. *chat/composer.dart, composer_tools.dart, composer_tray.dart, chat_composer_region.dart,
kit/chat/kit_composer*.dart.*

**SCREENS-NAV-48 - Slash, @ and ! input; command sheet.** `/` (and Ctrl+K in chat) opens the **command
sheet** (`widgets/command_sheet.dart`, also embedded in Settings > Tools > Commands): grouped, searchable,
plain words; `CommandSheetEntry.app(slash, aliases, title, description, group, action)` for app actions and
`CommandInfo` for the server's. Built-in slash list: new(clear), sessions(resume,continue), workspaces,
move, warp, editor, files, health, terminal, models, agents, variants, mcps, connect, org, skills, tools,
references, status, debug, themes, diff, context, share, unshare, rename, timeline, fork, compact,
thinking, timestamps, undo, redo, copy, export, shell, retry, note, approvals, plan, reload, help. Support
is gated by **capabilities** (`conversationShellOn`, `supportsSessionNotes`, `globalSessionSearch`,
`projectManagement`, `fileBrowsing`, `terminal`, `sessionDiff`, `sessionFork`, `sessionCompact`,
`sessionRevert`, `serverCatalog`...), never the backend kind. A server command pick opens
`showRunCommandDialog` (what it does, arguments, which conversation; nothing runs until "Run /review").
`!cmd` lines run a shell step (`conversationShellOn`; "Run shell command" dialog, mono, 4 lines, error
stays under field with command kept). `@` lists subagents + project files (folders excluded), a pick
writes `@path ` and stages the file as an attachment naming that text; command-auth sheet for commands
needing provider auth. Mentioned agent -> `promptAgentMentions`. *chat/chat_commands.dart, chat_command_actions.dart,
command_launcher.dart, chat_mentions.dart, widgets/command_sheet.dart, run_command_dialog.dart, library/command_auth_sheet.dart.*

**SCREENS-NAV-49 - Attachments.** Files (file_picker), photo library (multi-select up to room left),
camera (`oc/camera`), web sources screen (search when server has a provider, or paste link; returns
selections; never sends/fetches), keyboard-inserted images/GIFs (IME `contentInserted`), desktop drop,
chat gallery (tap a transcript picture -> swipeable gallery with Edit and Send-to-field),
"File from the project". All photo picks scaled by the picker to **2048 px / q85** (full-size frames x5
re-sent each step caused the server's "WebSocket inbound queue overflow"). Limits: aggregate cap shared
by composer and offline queue; count/size/type validation with one note ("some attachments need
attention"); attachments of drafts/stash live in vaults (`oc.draftAttachmentVault`,
`oc.stashAttachmentVault.*`, pending camera photo `oc.pendingPromptPhoto`, recovered on next start
after Android killed the app). `PromptAttachment` domain model. *chat/chat_attachments.dart,
chat_gallery.dart, web_sources_screen.dart, state/prompt_photos.dart, draft_photo_recovery.dart.*

**SCREENS-NAV-50 - Send pipeline.** Optimistic bubble; `_matchesPendingPrompt` dedupes; "a prompt shows
once; an unconfirmed send is not also restored into the composer". Delivery by capability: **steer**
into the running turn where the backend can (OC2 steer lands at next step boundary; Paseo message joins
the turn), "after the reply" for OC1-style, "Stop and send" for agents that restart from a message,
**offline queue** (`oc.offlineQueue`, `OfflineQueueStore`, capability `offlinePromptQueue`) for sends
made while disconnected, "Waiting to send - N" bubble (`KitQueuedMessage`, oldest first, per-item state
words + menu: move to another server (`showQueuedPromptMoveSheet`), discard, send again; never acts on its
own), server inbox for accepted-not-delivered sends (OC2). **No automatic replay of uncertain sends**
(`f29f47634`): "Check again" only looks (`receipt_check.dart`, command receipts). Stop: tap stops at once
and keeps the draft; **long-press Stop (or tap the live line) = "Stop after this step"** (steering note
at next step boundary; live line then reads "Stopping after this step" with Undo; teaching tip);
Stop calls off a send still waiting; send after resume waits for connection not the queue; a steer sent
during a command says when it will be read; a stuck foreground command (> `stuckCommandAfter` = 15 min: agents cap a
foreground command at ~10 min, with room for their own `timeout 900`) shows an amber stuck line naming the command with Stop and offers "Stop and send".
**Send later or repeat**: long-press Send turns the box into a scheduled prompt for this conversation
(SCREENS-NAV-98); tip registered. First-reply notification offer; free-model note; unanswered-prompt
retry chips; withdrawn prompts return to the draft (`oc.withdrawnPrompts.*`). A usage-limit failure
offers another model; a signed-out agent/declined tool call is not a failure. *chat/chat_send.dart (767),
chat_queue.dart, chat_stop_after_step.dart, stuck_command.dart, pending_sends_strip.dart, chat_schedule_send.dart,
chat_notices.dart, send_failure_words.dart, state/offline_queue.dart.*

**SCREENS-NAV-51 - Drafts, history, stash, editor, phrase chips.** Draft per session
(`oc.sessionDrafts` JSON keyed by profile+session, with attachment refs) saved on a **600 ms** debounce,
on leave and on lifecycle; **conversation review/voice/watch never saves**; failure to save asks before
words are lost (`chat-leave-unsaved-draft`); untouched new conversation is deleted on leave (3 server
round-trips, now after the page is gone). Older drafts migrate into Saved prompts
(`migrateOlderDrafts`, backup `oc.olderDraftsBackupV1.*`). Prompt history (`oc.promptHistory.<profile>`),
saved prompts (`oc.promptStash.<profile>.<id>`: tap restores to draft with Undo; delete acts at once with
Undo, no confirmation sheet), "Reuse a prompt" sheet adds text only (no attachments). Full-screen prompt
editor (attachments on top, one tall field, Done above keyboard, Close with changes asks once).
**Phrase chips** row above the field (`KitPhraseChips`; `PhraseChipStore` learns the person's own
short phrases on this phone from what they send; moment derived by `derivePhraseMoment`; leads with
"the moment's starter"; Settings row to turn off / restore hidden; `oc.phraseChips.<profile>`,
`oc.phraseChips.enabled`). Empty conversation: facts about the folder (one listing + Git status,
each optional) and **starters** (`ChatStarter`; a label ending in "..." puts the start of a sentence in
the box; none shown until the folder was looked at). *chat/chat_drafts.dart, prompt_history.dart,
prompt_stash.dart, prompt_editor.dart, phrase_chips.dart, empty_chat.dart, state/session_drafts.dart, state/prompt_shelf.dart.*

**SCREENS-NAV-52 - Voice typing and voice conversation.** Mic: quick tap = dictate with on-device
model; **hold to talk, drag far to cancel** (`KitMicPress`: hold after a threshold, slop, cancel
distance, system-cancel); tip `hold-mic`. Voice model setup sheet (download consent on mobile data,
language/accuracy tiers; packs gate on total RAM, never Android memoryClass), voice notices (licenses).
Voice conversation mode (`supportsVoiceConversation` = voice + read aloud): explicit foreground turns,
"Speak replies" reads each reply when it finishes; a watch bound to the app-authored dispatch id, speech
scope and voice epoch so reconnect/scope switch/Exit never attach it to other text; `/` in voice
conversation is ignored. Widget "voice" route starts dictation in the floating box. *chat/chat_voice.dart,
voice_conversation.dart, kit/chat/kit_mic_hold.dart, lib/voice/\* (other map).*

**SCREENS-NAV-53 - Read aloud.** Per-reply "Read reply" (hidden when the reply has no prose) via system
TTS (`oc/read-aloud`) or the optional Natural voice (`sherpa_onnx`, `lib/voice/natural_voice.dart`);
first use asks consent to use the system speech engine (`oc.readAloudConsent`, app-wide); voice chooser
sheet; Stop in the top bar; reading pauses/resumes with activity lifecycle. *chat/read_aloud.dart.*

**SCREENS-NAV-54 - Find, timeline, message actions, fork/undo/revert.** Find bar docked under the top
bar (count "3 of 12", prev/next, Esc/F3, shows what is missing when history is partly loaded and offers
to search it all; hits marked via `TranscriptHighlight`; the active hit that is not visible in place
(thought, tool data, far down) appears as an excerpt panel above the turn). Timeline sheet (newest first,
searchable, jump; a prompt's row has Fork). Message actions sheet: copy (redacted unless the person's
own prompt), fork from here, read aloud, revert/undo from here, delete. "Undo from this prompt?" sheet
(`showStageRevertSheet`: two shapes by server ability - quote the prompt, say what happens to the
conversation and files) -> `StagedRevertScreen` (files listed, each opens its diff, pinned "Put everything
back" primary and destructive "Delete the hidden messages", each asks first). Copy transcript =
Markdown, every non-own part masked by `KitRedact` (a key a tool printed never reaches clipboard).
*chat/transcript_find.dart, timeline_sheet.dart, chat_message_actions.dart, staged_revert_screen.dart,
chat_session_menu.dart.*

**SCREENS-NAV-55 - Session menu and conversation-level pages.** `SessionMenuAction`: changes, timeline,
find, subagents, details, preview, share, unshare, compact, fork, rename, continueWith,
continueOnComputer, continueOnPhone, archive, delete + "Scheduled prompt..." first when
`canSchedulePrompts`. "Go to" group then "Do" group, destructive Delete last under a divider; gated by
`SessionMenuOffer.of(capabilities,...)` (never backend kind); Compact/Fork dimmed with reason before the
first prompt. Work rows reuse the same menu and hand conversation-needing acts to the chat via
`ChatRouteArguments.menuAction` (run once after first history arrives). Pages/sheets behind it:
`SessionContextScreen` (verdict, model bar, what fills the input, totals, raw figures under Details),
`ActiveContextScreen` (messages the server keeps after last compaction, searchable, by kind; each opens
its parts), `SessionRelationsScreen` (parent + subagents by urgency), `SessionNoteScreen` (mobile-owned
note for the agent, unsaved-changes guard), `SessionExportScreen` (JSON redacted by default or Markdown;
`FilePicker.saveFile`), `SessionImportScreen` (choose JSON, review title/count, destination chooser;
`pushReplacementNamed('/chat/id')`), `showSessionDestinationSheet` (move to another folder of the project /
warp to a cloud machine; confirm), `showConsoleOrganizationSheet`, share/unshare confirm, rename dialog
(error stays under field), archive/delete confirms. *widgets/session_menu.dart, chat/chat_session_menu.dart,
chat_session_actions.dart, session_*screen.dart, session_destination_sheet.dart.*

**SCREENS-NAV-56 - Approvals sheet.** `showSessionApprovalsSheet`: Ask first / Auto-approve this
conversation / Approve everything (only modes the connection supports), "Subagents follow this" switch
while auto-approving, one quiet line when inherited. Same choices as the composer chip; auto-approve
indicator strip. Server-wide "Approve everything" stored under reserved key `*` of `oc.autoApprove.<profile>`,
confirmed on enable; explicit "Ask" conversations still ask. OC2 app forces ask-all by default
(`oc.mobile.permissionRequests`). *chat/approvals_sheet.dart, approval_mode_menu.dart, state/session_auto_approval.dart.*

**SCREENS-NAV-57 - Work in this conversation.** `showRunningWorkSheet`: agents and commands this
conversation started in one list by urgency (failed, running, rest newest first); no state sections; no
preamble; the sheet's spinner stops when the agent is idle; `shortCommand` names commands by program.
`ShellOutputScreen`: command in plain words, state, two acts kept apart (stop with confirm; timeout
sheet), output fills the page, Follow switch (dragging up pauses follow), exit code/folder under Details.
"Move running work to background" (Ctrl+B) hands the running turn to the background
(`chat_running_work.dart`). Running-agents strip (`RunningAgentEntry`: parent, current, siblings).
*running_work_sheet.dart (1023), chat/chat_running_work.dart, widgets/running_agents_strip.dart.*

**SCREENS-NAV-58 - Dev-server preview.** Tapping `http(s)://localhost|127.x|0.0.0.0|[::1]:<port>`
(explicit port, no credentials) opens `DevPreviewScreen` (WebView): slim bar "Preview - localhost:8080",
Refresh, Open in browser (through `openExternalLink`), Copy address, dev logs (`showDevServerLogs`),
remote-host note ("must listen on 0.0.0.0"), "Nothing is answering on port N" + Try again; **the address
loaded is built from scheme+port+path plus the saved server's host, never taken from link text**
(loopback server -> `127.0.0.1:<port>`; otherwise `<server host>:<port>`; no server / leased transport /
no host -> blocked with plain explanation). Back steps through the page's history first. With a
conversation, one glowing "Ask for a change..." field sends a request into it (`DevPreviewDelivery`:
steer / agent restarts / after reply). Conversation menu "Preview a running app" lists ports mentioned in
messages and tool output (newest first, "On this phone"/"On <host>"). `detected_dev_servers`,
`dev_server_scan` state. Doc: `docs/design/dev-server-preview-2026-10-16.md`. *dev_preview_screen.dart,
dev_preview_conversation.dart, chat/chat_dev_preview.dart, domain/dev_preview.dart.*

**SCREENS-NAV-59 - Continue with another agent / on computer / on phone.** "Continue with..."
(`showContinueWith`): carry the work to another model/agent; sheet says what is not ready, walks sign-in,
opens the new conversation, leaves a quiet link line in both (`chat_handoff_link.dart`: where this work
went/came from; also the way out of a provider failure; deletion semantics in
`docs/design/agent-handoff-contract.md`). "Continue on computer": the terminal command that resumes the
session on the server's machine, fully wrapped, Copy, "reload conversation" when folder unknown.
"Open on another phone": QR + link (4.2). *widgets/continue_with_sheet.dart (723), session_handoff_sheets.dart,
session_handoff.dart, state/agent_handoff_links.dart (`oc.agentHandoffs.*`).*

**SCREENS-NAV-60 - Watching mode (AI Team workers).** `ChatWatch`: ordinary chat, read-only; one slim
status line says who is watched and its state; the composer addresses the worker through the team's
message control (words kept as draft per worker+server until the team takes them; receipt above composer);
no menu (share, fork, revert, rename, delete, compact), no approvals/questions/forms answered here, no read
receipt sent. `TeamWatchLiveScreen` is the same page drawn from the team's live output when the worker's
OpenCode session cannot be read (`TeamAgentTranscript`: prose + folded `[tool: ...]` lines; follow-latest
with Jump pill). *chat/watching.dart, team_watch_live.dart, team_agent_conversation.dart,
team_conversation_view.dart/actions/parts.*

**SCREENS-NAV-61 - Offline demo ("Try it offline").** `DemoScreen`: the production chat on a route-owned
gateway and ephemeral stores, one status line saying it is simulated; nothing replaces the real
connection/profile store/plugin singleton; `isIsolated` disables menu, drafts, markdown links, read state.
`TeamProjectDemoScreen` is the AI Team equivalent, reachable before any server. Entry: Settings (Try the
demo). *demo_screen.dart, chat/chat_dev_preview.dart.*

**SCREENS-NAV-62 - Model / agent / thinking picker.** `showModelPicker`: opens on plain choices that lead
with the reason (Default, Recently used, Cheapest; docs `vision-model-picker-2026-10-20`); a tap chooses
and closes (no Apply); "All models" -> `ModelCatalogView` (notices, "Reload providers" when a signed-in
provider is not loaded, search kept above keyboard, providers with `ProviderLogo` (models.dev mono logos,
cached weekly), thinking level/variant, agent selector, per-model options, sign-in shortcuts). Apply scope
labels: classic ("Use model and mode", client-side per prompt) vs session ("Use for this conversation",
OC2 session state). Model-name display: `model_display_name.dart`, runtime-prefixed ids named by their
model. `AgentFeaturesChip` + sheet: per-conversation agent switches (Claude fast mode...), one switch row
each, saved at once, refusal keeps old value. `showAgentModelSheet` for phone agents. Recent-model cycle
F2. *widgets/pickers*.dart, model_shortcuts.dart, agent_features_chip.dart, agents/agent_model_sheet.dart.*

### 6D. Project: files, review, git, terminal, services

**SCREENS-NAV-63 - Project hub ("Files" destination/page).** `ProjectHub`: large title "Files", project in
quiet text under it (tap switches), the project's files/folders as plain rows with uncommitted changes
marked on the name; Search and Terminal quiet icons in the bar; **Changes** and every other tool in the
bar's menu, each gated: `ProjectTool` = files, changes, terminal, health, worktrees, services, workspaces,
search. Project = last used for a chat (`oc.lastProject.<profile>`, never a temporary folder), else the
open one; no project -> says so + chooser. A server that cannot browse files keeps the tools as rows; the
destination is dropped when none is left. On phones it is a **page** `openProjectFiles(directory,
openFiles, focusSearch)` reached from the chat's project chip, Search, Ctrl+2, Go to Projects. Tapping the
destination again leaves Files for the hub. *project_hub_screen.dart (987), navigation/last_project.dart.*

**SCREENS-NAV-64 - Files browser, viewer, previews.** `FilesScreen` (`/file` browse, `/find/file` name
search, symbol and text search surfaces `_FileSurface{files,symbols,text}`), breadcrumbs, hidden-files
toggle, empty-folder state, stale-read retirement with scoped retry, shared-storage projects ask for **All
files access** first (`SharedStorageAccessFlow`; never at start-up, never for the app's own project space;
never shows a blocked folder as empty, #87), row actions sheet (Open, Attach to prompt, Add reference,
Open review, Copy path) behind a visible trailing "more" button (gesture audit), viewer = kit viewer
(`showFilePreviewSheet(Loading)`: Attach, More: Find, Copy, Wrap, Show source, Save to device, Copy path,
Open in Files, Open in Review; Ctrl+F) with previews: text/code (highlight), Markdown (Rendered/Source),
JSON, CSV/TSV table, SVG (flutter_svg; external/active content rejected), images (pinch/pan), PDF (native
isolated-process rasteriser `oc/local_pdf` + `LocalPdfService` in `:local_pdf` process; one request at a
time, off-screen pages never requested, cancelled when app leaves foreground, per-page retry, zoom).
Wi-Fi share of an opened file (`showFileShareSheet`: read-only download server, QR+link, stops on close/
Stop/15 min). Desktop: tree|preview split with draggable divider. *files_screen.dart + files/\*, widgets/
file_preview.dart, pdf_file_preview.dart, svg_file_preview.dart, delimited_file_preview.dart, file_share_sheet.dart,
shared_storage_access_flow.dart, platform/wifi_file_share.dart.*

**SCREENS-NAV-65 - Review changes and diff.** `ReviewWorkspace` (scopes `session | workingTree | branch`):
one `KitDiffView` (unified vs side-by-side chosen from width), each file a folded row with the first open,
view picker when server offers more than one, selection by tapping numbered lines (second tap = range) or
hunk header, selection bar: Clear, Copy, Add to prompt, Comment; comment sheet keeps text across dismiss
(DATA-1); expand context ("+N lines", "Show next N lines", "Hide revealed context"); starts with one plain
sentence; `ReviewHandoffSession` returns the formatted prompt to the host chat's composer or copies it;
`DiffPage` read-only single/multi file with "Change 1 of N" (N/P/F7). Pull to refresh; stale selection kept
across refresh. *review_workspace.dart (1254), kit/diff/\*, state/review_handoff.dart.*

**SCREENS-NAV-66 - Git sheet.** `showGitSheet(page)` (git page, docs `git-page-contract-2026-10-19.md`):
branch, changed files, stage/commit/push/pull, one-line rows, GitHub device-flow sign-in
(`GithubAccount`, token `oc.githubToken.*` secure), half-done operation banner, host-named footer. Words
from `git_words.dart` (Git's own text only under Details). *git/git_sheet*.dart, state/git_page_controller.dart.*

**SCREENS-NAV-67 - Server terminal.** `TerminalPage` -> `TerminalScreen`: one live shell at a time; as a
page the running terminal opens at once (new one if none), tabs once there are two, New terminal and
clear-up in the bar menu; framed by another page it is a list (running first; rename; stop vs remove;
long-press/right-click replaced by visible overflow). `TerminalSurface`: xterm (`third_party/xterm`)
with kit key bar (Esc, Tab, Ctrl, arrows, ...), one status line only while not connected, Copy appears
only while text selected, menu: readable text (accessible view), paste, reconnect, rename, details, stop;
`TerminalInputQueue` serialises keystrokes into ordered writes per flush (fast typing used to interleave
and echo out of order); top/htop work and a short screen stays usable (`5cc94b6bb`); terminal links
(`KitTerminalLinks`). Source line "Built-in Linux | <server>" switches shell; when a dev server runs for
the project the same line says so and opens the preview. *terminal_screen.dart, terminal/\*,
kit/kit_terminal_view.dart, terminal_key_bar.dart.*

**SCREENS-NAV-68 - Local terminal (built-in Ubuntu).** `LocalTerminalView`: a shell in the app's proot
Ubuntu with no OpenCode server involved (works while server stopped); channels
`io.github.eslamasabry.opencode_mobile/builtin_linux` + `oc/*` terminal channel registered by `LocalTerminal.kt`; scope line under title. Doc `docs/design/local-terminal-2026-09-24.md`. *local_terminal_screen.dart.*

**SCREENS-NAV-69 - Project tools pages.** `ProjectHealthScreen` (branch, changed files, language servers,
formatters; "Initialize Git repository?" dialog), `WorktreesScreen` (main copy + worktrees, pinned New
worktree, state words "Current", "Preparing...", "Setup failed", reset/remove confirms),
`DevelopmentServicesScreen` (saved dev commands & preview links: Start/Stop/Restart/Forget/Remove with
confirm, editor sheet, logs sheet, polls every 5 s; `oc.developmentServices.*`), `ManagedWorkspacesScreen`
(cloud environments from a workspace adapter: create/remove dialogs), `ProjectsScreen` (open folders,
rename on long-press/right-click + visible menu, "Current" mark never colour only), `ProjectFolderActions`
(create a folder on a server this app runs / pick a project / open by path), `FolderBrowserSheet`
("Open a project" for an on-device server: start page of three plain choices, browse Ubuntu projects folder,
phone storage place, create-new, type a path; `RemoteFolderSheet` for remote servers: recent + server
projects + path field), `SharedStorageAccessFlow`. *the files named, widgets/folder_browser.dart (1180),
remote_folder_picker.dart.*

**SCREENS-NAV-70 - Wi-Fi share of a file / project export.** See 64 and 22. *platform/wifi_file_share.dart.*

### 6E. Servers and connection

**SCREENS-NAV-71 - Servers screen.** `ServersScreen` (route `/servers`, also Settings > Server > Servers):
as the whole app (no shell yet) it carries the brand mark + title and a menu with About, Report a bug and
Setup guide; opened from Settings it is simply "Servers". With no saved server the **welcome** page
("Keep your work moving"): scene drawing, choice this phone vs my computer, found-Termux/OpenCode lead,
demo; with servers a list of rows, each saying its state in words with a visible More (Open page,
Connect/Reconnect, Edit, Forget...) - **every row opens its own server page** (SCREENS-NAV-75); the
phone's own rows lead the list (`TermuxRunningServerEntry` "This phone" row with Start/Stop in place,
`LocalAgentServerEntry` Claude Code daemon, `shownAsPhoneRow` so a server is never listed twice,
`TermuxMigrationRow`/`TermuxMigrationOffer` for Termux users, `termux_migration_entry`), queued-prompt
move per server, profile-monitor attention snapshot per row, a failed connect attempt shown inline above
the rows in the verdict style (never a snackbar with a raw exception), forget with confirm (also sweeps
`oc.*.<profileId>` keys and secrets via `ConnectionController.deleteProfileAndLocalData`). Connecting from
here is the only implementation of the runtime-choice detour (OC1 vs OC2 on the same phone), the editor
that stays open until connect succeeds, and credentials. *servers_screen.dart, servers/servers_state.dart,
servers_actions.dart, server_rows.dart.*

**SCREENS-NAV-72 - Add / edit server editor.** Multi-step ("Step 2 of 4 - Pair or enter the address",
kit staged progress): step 1 kind (`ServerBackend`: **openCode, codex, paseo**; one choice list acting on
tap; a note that agents are not alternatives - one computer runs all) + "other ways in" rows: this phone,
Tailscale (only `supportsTailscaleHandoff`), outside agents; Tailscale step (numbered phone steps: install
app, turn VPN on; help fold; address + the server's own sign-in on the connect step); connect step:
address or pairing code, auto-test after **800 ms** typing pause (`autoTestPause`) with progress and
verdicts, credentials (OC2 Basic user `opencode` + password; Codex token; Paseo password/secret), name,
directory; **QR pairing** (`PairingScannerScreen`, `mobile_scanner`: payload `{urls[], username:"opencode",
password}` <=1 KB, picks the address this device should dial, redacted `toString`, pop with
`PairingPayload` or null, "Paste instead"; desktop shows no scan affordance), certificate trust
(`inspectServerCertificate`, `ServerCertificateInfo`, pin `oc.certificatePin.<id>`, changed certificate
shown once), explicit plain-HTTP consent (`oc.cleartextOk.<id>`), private-network rules, socket-backend
probes (`probePaseoConnection`, `probeCodexConnection`), discard sheet, finish step with the linked
drawing. Network security config cannot express Tailscale 100.64.0.0/10, so address checks are in app
(`tailscale_address.dart`). Save never connects without success; password never echoed. *servers/
profile_editor*.dart (7 files), pairing_scanner_screen.dart, state/pairing.dart.*

**SCREENS-NAV-73 - Saved-server connection card.** `SavedServerConnectionCard` is a `KitStateView` page
for the root's connecting state, never a card around a message: connecting ("Connecting to ..."),
starting (in-app server), not answering (after the shared 8 s clock) with Try again, start failed (plain
reason, Details), credential re-entry (password/token/certificate), "Start the phone server" (in-app or
Termux), Open Termux setup, Change server. `GraceTimer` supplies the grace delays. *widgets/saved_server_connection_card.dart,
grace_timer.dart, connection_failure.dart.*

**SCREENS-NAV-74 - Phone-server card & rows.** `PhoneServerCard` ("This phone": OpenCode inside the app as
one row with the one act it needs now; ⋯ menu `PhoneServerAction`: restart, stop, switch runtime, remove...
each either leaves for setup progress or removes the server, run via `runPhoneServerAction` after a hosting
sheet closed), `phone_server_restart`, `phone_server_consents`, `phone_server_names` (display names:
"In-app Ubuntu", "Termux", "This phone"). *widgets/phone_server_card.dart (1419), local_server_row.dart.*

**SCREENS-NAV-75 - Server page (Settings per server).** `ServerPageScreen(profileId)` pinned to the profile
it opened for (SC11: switching server never redirects an edit): **Connection** (status+version, sign-in,
name & address, what it supports, `ConnectionCheckScreen` = five steps run on open, "Check again"; no server
text), **In this app** (model for new chats `oc.model.<id>`, notifications, watching, read-state share
`oc.shareSessionViews*`), **On the server** (providers, tools, permissions, AI setup review, shell,
plugins; under a line that says it is shared with every app). Rows needing a live connection are offered
only while connected to that server, else the page says so and offers Connect. Use my default, Reconnect
to <name>, Disconnect from <name>, Remove <name> (all name the target). Host update (`server_host_update.dart`:
pinned official upgrade run on the server's computer; restart dialog). "By server" pages
(`ByServerScreen`) for hub rows several servers own. *settings/server_page.dart (1025), hub_servers.dart,
connection_check_screen.dart, server_host_update.dart.*

**SCREENS-NAV-76 - Setup help pages.** `GuideScreen` (Setup guide: leads with `opencode2 pair`, scan or
paste, start talking; everything else behind "Advanced": HTTPS proxies, SSH tunnels, older servers, Termux),
`HostManagementScreen` (copyable OC2 setup commands - the app never executes them; pinned official npm
package; commands from `setup_commands.dart`, host scripts pinned by commit + SHA-256, never pipe a
download into a shell), `ReachComputerScreen` (Tailscale first, SSH tunnel second, never a public tunnel),
`TailscaleSetupScreen` (user-controlled handoff to the official app; returning a URL does not probe/save/
authenticate; `TailscalePhoneSteps`, `TailscaleHelpFold`, `oc/tailscale`; `oc.tailscale.*`),
`ServerCapabilitiesScreen` ("Available on this server": the explanation for every row rule 7 hides,
sourced server vs device). *guide_screen.dart, host_management_screen.dart, settings/reach_computer_screen.dart,
tailscale_setup_screen.dart, server_capabilities_screen.dart.*

**SCREENS-NAV-77 - Queued prompt move.** `showQueuedPromptMoveSheet`: move prompts queued for an
unreachable server into a conversation on the connected one. Opened from a server row and from the chat's
"waiting for other servers" notice. *widgets/queued_prompt_move_sheet.dart.*

### 6F. This phone: setup, Linux, Termux, local agents

**Note:** all of 78-83 are Android-only (`supportsTermux`) and the in-app Ubuntu is **proot**
owned by Kotlin (`BuiltinLinux.kt`, `SetupService.kt`, `BuiltinServerService.kt`); a native rewrite keeps
the Kotlin and re-skins these pages.

**SCREENS-NAV-78 - This phone.** `ThisPhoneScreen(kind: inApp|termux)`: one page ordered by likelihood -
status + the one act needed now (start, connect, finish a switch), update, add tools, installed tools,
storage, "Running on this phone" (only where the host can list), keep running, log, remove last. Each act
names its object ("Stop the server on this phone"); each optional tool (Python, AI Team, voice typing) has
its own "Remove <tool>" with measured space; a tool something else uses is named and cannot be removed;
version under Details; the installed check is retried before saying it could not check. Rows:
`ThisPhoneAnswer` (short so Restart/Stop show without scrolling), `ThisPhoneDetails`, `RunningOnPhoneRow`,
agents row, FactsBook phone node row. *this_phone_screen.dart (1334), this_phone/\*, running_on_phone_screen.dart,
widgets/running_on_phone_*.dart.*

**SCREENS-NAV-79 - Phone setup (A/B/C).** A `PhoneSetupStartScreen` ("On this phone": one promise, one
filled button, others folded; hero follows truth: running/stopped setup "42 % done", Termux setup,
ready in app, Termux running, else first-time promise with registry-derived time/download numbers;
Customize sheet = component picker `SetupCustomizeSheet`, add-mode for optional tools only); B
`PhoneSetupProgressScreen` (big ring with a real figure, one sentence, slim step trail, cost line with real
numbers, "Safe to leave", Pause/Continue, every step+log+Report under Details; job runs natively under a
foreground service so leaving never stops it, coming back re-reads `setup.json`; Pause = engine stop,
finished steps stay, downloads resume); C `PhoneSetupReadyScreen` (one name makes a project and lands in
its first conversation with keyboard up; folder sheet is the quiet alternative; `ChatRouteArguments.firstRun()`
and `HapticFeedback` done). Termux variants `PhoneSetupTermuxScreen`/`PhoneSetupTermuxJobScreen`
("Get Termux"/"Allow Termux" rows only the person can do), `PhoneSetupUnsupportedScreen` (non-Android),
`SetupProgressView`/`SetupTerminal` (`KitLogPanel`: follows newest line until scroll up, jump pill counts
new lines), hero drawings. Engine/contract in `lib/builtin/setup/*` (other map): SetupProgress, components
registry, `SetupState idle|running|stopped|done`. Notification asks at start. *phone_setup/\*,
widgets/setup_progress_view.dart, setup_terminal.dart, setup_ui_messages.dart.*

**SCREENS-NAV-80 - Linux manager.** `openLinuxManager(onRepair)` hub (in-app host only): What runs
(processes page, auto-refresh 10 s, stop with confirm), Ports, Storage, Packages (apt jobs with progress,
named Stop; `LinuxJobPanel` shown on top of every page that can start one and survives leaving), Repair
(question confirmed first -> phone setup check-and-repair), Reset Linux (`LinuxResetFlow`: sets Linux up
again at once or erases nothing; pending flag `oc.linuxReset.pending` resumed on next start). Docs
`docs/design/linux-manager-2026-10-31`. *linux_manager/\*.*

**SCREENS-NAV-81 - Termux pages.** `TermuxProcessesScreen` (running now, group stop, details, budget "18 of
32 background processes"), `TermuxStorageScreen` (measured categories, clean sheet), `TermuxMigrationScreen`
(move to the in-app server: review of what moves with measured sizes, copies projects (default on), private
copy of rest, never deletes Termux; resumable; `termuxMigrationRouteName`), `TermuxRunawayWatcher` ->
`WorkRunawayNotice` (helper burning CPU with nothing waiting; "Stop java" asks first), Termux problem fix
sheets (`showTermuxOtherAppsSheet`: allow-external-apps line to copy). *termux_*_screen.dart, widgets/termux_*.dart,
work_status_line.dart.*

**SCREENS-NAV-82 - Claude Code on this phone (local agent).** `LocalAgentScreen`, `LocalAgentOnboardingBlock`
(5 steps `node, paseo, claude, signIn, start`, states idle/running/done/error, failed-step mapping by
`LocalAgentFailureKind` portInUse/timeout/daemon/notInstalled..., project folder sheet
`showLocalAgentProjectSheet` under `~/projects`), `LocalAgentServerEntry` (daemon status via `claude.sh status`
on mount/resume), stop/restart/remove confirms. *local_agent_screen.dart, widgets/local_agent_*.dart, safety_confirms.dart.*

**SCREENS-NAV-83 - Phone health notices.** Thermal (`ThermalNoticeLine`, Keep-running heat switch),
Android-stopped-the-app line ("Restarted after the app closed", promise a restart only when one is
allowed), background pause notice (Android 15 caps dataSync FGS at 6 h/24 h; battery exemption does not lift
it), `ManagedServerRecoveryOption` ("Restart after a crash" switch; Termux). *widgets/thermal_notice.dart,
app_exit_notice.dart, background_pause_notice.dart, managed_server_recovery_option.dart.*

### 6G. Agents

**SCREENS-NAV-84 - Agents hub and agent sheet.** `AgentsHubScreen` (Settings > Agent > Agents: AI Team, the
agents on this phone, external agents - three homes merged into rows), `AgentsScreen`/`AgentsSection`
(every agent this phone can run + state + the one act: install, sign in, run phone check, resume; "Check
this phone"), `showAgentSheet(step: list|setup|check|signIn, signInAgain)` - **one sheet**, every step
replaces the frame in place; `AgentPhoneCheckView` (four steps passed/failed/waiting + measured download),
`AgentSignInTerminalScreen` (`claude auth login` on a terminal: the app opens Claude's sign-in page via
`openAgentSignInPage` (exact verified URL `https://claude.com/cai/oauth/authorize`, no port/userinfo/fragment),
user pastes code into Claude itself), `AgentAccountScreen/Panel` (Codex account: state in words, device-code
steps, limits/token usage; pinned to the profile/location opened), `CardsFromAgentsRow` (switch: Off blocks
card actions at once), `AgentStatusNotices` on Chats (plan limit, signed-out, stopped host, one restart
offer; each action names its target), `agentMark`/`agentPersona` text, `AgentFailure` (plain words + Details).
Agent chat resumption never forks (`ace6fcb28`). *agents/\*, agent_account_screen.dart.*

**SCREENS-NAV-85 - External agents (A2A).** `ExternalAgentsScreen` (saved agents by urgency), add agent
(address + bearer, `oc.a2aBearer.*` secure), `ExternalAgentDetailScreen` (tasks by urgency, New task pinned,
rare acts in menu, removal last confirmed), `ExternalTaskScreen` (state on status line, agent output, field
+ one pinned primary Send/Reply, pull to check, Stop/Forget in menu confirmed, unsaved-draft guard).
`oc.externalAgents`, `oc.a2aTasks.*`. *external_agents_screen.dart (1312), state/external_agents.dart.*

### 6H. AI Team

Backends: Gas City supervisor over HTTP, a recorded fixture (demo), and the **phone-resident native
project engine** (`OrchestrationProvider.phoneEngine`). Team data is a controller snapshot
(`OrchestrationController`, `TeamProjectController`), writes are controls with receipts.

**SCREENS-NAV-86 - Team page.** `openTeamPage` -> `TeamPage`: **On** -> `TeamHomeScreen` (one status line +
urgency-ordered task list, search with `TeamRunFilter`, Needs-you cards, agents tab, board/timeline entry;
`PhoneTeamOffPage` when the phone team is on but not answering; `TeamPhoneKilledNotice`); **Off** ->
`TeamIntroScreen` (what the team does, sets it up for this kind of server, turns into the team the moment
it is on); `TeamSettingsScreen` (agents, how it runs, spend, Turn off with confirm), `TeamAgentsScreen`,
`RoleScreen`, `AgentScreen` (message / reassign / stop / restart confirms; ctx % number), `TeamBoardScreen`
(+ move/priority/add/cancel sheets), `TeamRigScreen`, `TeamMigrationGate`/`TeamMigrationScreen` (keep old
team vs switch; `oc.teamMigrationKept.*`). Settings > AI Team's door is setup only (`openTeamSetup`). Host
form sheets: `showTeamHostSheet` (address+name test with Cancel test; save-anyway), guide sheet (4 host
steps pinned commit + SHA-256), turn-off sheet, discovery card ("<server> also runs an AI team. Turn it
on?"), technical details sheet (raw values, Copy, masked). *team/\*, widgets/team_*.dart (18 files).*

**SCREENS-NAV-87 - Team projects (native engine).** `TeamProjectsScreen` (three-pane on large: projects |
tasks | conversation; urgency-sorted), `TeamProjectOverview/Board/Timeline/ServersCard`,
`TeamProjectConversation` (review, steer, verify and promote a task from its conversation),
editors (`openTeamNewProject`, spec/plan editors, project settings, roles, defaults incl. Planner model
recovery, drafts `oc.teamEditorDrafts.*`), `TeamExecutionGate`/`TeamExecutionBlocked`/`TeamProtectionLine`/
`TeamPhoneServerProblem`/`TeamResumeUnavailable` (one plain line why the engine cannot run, with fix),
`TeamRefusal` (refused moves/merges in words with Try again), `TeamMergeFlow` (merge-on-green + Undo),
`teamProjectDestination` (link/notification target with `requestId`/`taskId`), demo `TeamProjectDemoScreen`
(persisted, isolated, even before any server; viewing it never turns the real team on). *team/projects/\*,
team/project_destination.dart, project_demo_screen.dart.*

**SCREENS-NAV-88 - Team conversation, gate, work, merge.** `TeamConversation` route (a task = a chat
conversation, workers = sub-agents, decision A): the lead's reply as plain event lines, one "Now" line
(`TeamNowLineView`), no-progress notice, steps; `showGateSheet` (decision / confirmation / free text /
run failed / review; reads gate from snapshot on every rebuild so an answer made elsewhere says so; confirm
sheets for destructive approve/deny/cancel; "if nobody answers" line), `TeamNeedsYouCard` (the one request
card, same as chat's), `TeamGateRow`, `showWorkSheet` (work item, dependencies, validation, linked session;
no sheet on a sheet - dependency rows close and reopen), `showTeamTaskDetails`, `TeamMergeSection` (ready/
not ready/merged, confirm "Merge into <branch>?", approve request), `TeamMergedCelebration` (once per
task), `TeamNeedsYouLabel`, `KitWorkGraph`. Words in `widgets/team_vocabulary.dart` (stages Planned/Working/
In review/Merged; roles in plain words, Gas City names only in Technical details). *chat/team_conversation_*.dart,
team/gate_sheet.dart, work_sheet.dart, merge_section.dart, task_details_sheet.dart, team_needs_you.dart.*

**SCREENS-NAV-89 - Team on this phone.** `openTeamOnThisPhone` ("Add tools" with AI Team on, then ready page
that turns it on for the project, stages, "Give the team a first task"), `TeamPhoneSetupScreen`,
`TeamPhoneSection` (stop/remove sheets, "Keep it running" tips), `BuiltinTeamSection`, `StartRunSheet`
(planner form / direct task / why neither; `TeamStartBlocked` with Wake planner), `TeamBoundariesRow`,
`TeamModelSheet`. *widgets/team_phone_*.dart, builtin_team_section.dart, team/start_run_sheet.dart, team_phone_setup_screen.dart.*

### 6I. Settings

**SCREENS-NAV-90 - Settings hub + group pages + search.** `SettingsScreen` (embedded as a tab or pushed;
`SettingsGroup` = Server(connections), Agent(ai), Conversations(chat), This app(thisApp), Help):
search field + **five one-word rows**; only Server adds a value line ("On this phone - Connected" /
"<name> - Offline" / "N servers" / "Not set up yet"); each opens `SettingsGroupScreen`; search results
(`searchEntries`) cross-fade with the rows, show the group word or the parent page title, Enter opens the
first. Group rows (`settingsGroupRowIds`): **Server**: saved servers, This phone. **Agent**: Model & mode,
Providers, Usage, Tools, Agents. **Conversations**: Show reasoning, Show timestamps, Phrase suggestions,
Default shell, Voice typing, Permissions & automation. **This app**: Notifications, Background,
Appearance, Language, Privacy & local data. **Help**: Setup guide, Tips, Report a problem (with
error-count badge), About & notices. Rows absent when gated out; a group with none has no door; "N
settings aren't available on this server - Why" line (`_hiddenInGroup`). Expanded+: two-pane (list =
search+groups, detail = group rows). With several servers: "You can change some of these for one
server" block (`SettingsServersBlock`) and "By server" rows (SC rules: personal choices apply
everywhere, owned things stay with their named owner, no global Advanced switch, copying to other
servers is an explicit action with receipts). `SettingsTarget` kinds device/connection/host/
providerAccount/project/chat (`domain/settings_scope.dart`). Perf span `settings.open`. *settings_screen.dart
(+ parts), settings/settings_group_page.dart, hub_servers.dart, domain/settings_search*.dart.*

**SCREENS-NAV-91 - Appearance.** `AppearanceSettingsScreen`: first visit **guided** (`KitGuide`: four
chips Colours / Style / Background / Text & motion, each showing its current value; a chip opens its 2-3
questions as smaller chips; Next bar suggests the next, a dot marks it, answered chips carry a check,
Finish ends "All set"), afterwards **one scrolling page** with every question in place ending in "Guide me
again" and "Reset to defaults" (asks first). Controls: appearance (system/light/dark/Saver), theme pack
list with preview sheet (Apply + Undo), accent, Style (solid/soft/brutal/paper/signal) + Fine-tune knobs,
Background (live background styles and intensity subtle/normal/bold, `KitLiveBackground`), text size,
motion level (full/calm/off), glow style, haptics/vibration, celebrations, activity glow, colours of glow.
Saver holds Style and Background and says why. Every choice applies at once and persists; a refused save
puts it back with a note; each question is its own screen-reader stop. Keys (all `oc.*` prefs, app-wide):
`oc.appearance, oc.appearanceAccent, oc.appearanceGuide, oc.themePack, oc.textSize, oc.effects{Motion,
Celebrations, ActivityGlow, GlowStyle, GlowColours, GlowSpeed, Depth, Corners, Density, Edges, SurfaceStyle,
BackgroundOn, LiveBackground, LiveStyle, LiveIntensity, LiveHeldByStyle}`, `oc.glass`. *settings/appearance_screen.dart
(1470), appearance_one_page.dart, widgets/appearance_picker.dart, state/effects.dart, app_preferences.dart.*

**SCREENS-NAV-92 - Notifications, background, keep running, automation.** `NotificationsSettingsScreen`
(page `all|notifications|background`, `initialSection` slug `what|quiet|servers|background|keep-running|
automation`): what notifies (finished runs, requests, quota alerts, check-in after N minutes
`oc.notify.checkInAfterMinutes`), Quiet hours (time picker; `oc.notify.quietStart/quietEnd`), Wi-Fi only
(`oc.notify.wifiOnly`) - both defined **once**, read by saved-server monitor and quota monitor - per-rule
`oc.notifyRules.*`, saved-server monitoring switch, background connection (keep live in background
`oc.keepLiveInBackground`, `ForegroundService dataSync|shortService`), `KeepRunningSection` (steps ordered
by what is left, "You're set", heat pause, limits Android keeps: Android 15 6 h/24 h), `AutomationSettingsSection`
(AI Team supervision level `KitChoiceList` stored `oc.automation.<profile>`, door to Always allowed
actions, "Your answers" = consents asked once in flow, `ConsentOwners`, `oc.inFlowConsent.*`,
`oc.permissionConsent.*`). A row is absent when the device cannot do it. How monitoring works is folded
under Details. *settings/notifications_settings_screen.dart, keep_running_screen.dart, automation_settings_screen.dart,
state/notification_preferences.dart.*

**SCREENS-NAV-93 - Smaller settings pages.** Privacy & local data (read-state sync, unsent work with size and
delete: queued prompts / drafts confirm sheets, privacy policy viewer `showPrivacyPolicy` from bundled
`PRIVACY.md`), Language (`showLanguageSheet`: system/en/ar/zh/es/pt/ja/ru; `oc.appLocale`), Phrase
suggestions, Show reasoning/timestamps (`TranscriptDisplayToggles`), Default shell sheet (server-backed,
loads lazily so a filtered-out row costs no request), Voice typing (`lib/voice` UI), Permissions &
automation, Always allowed actions (`SavedPermissionsScreen`: the agent's saved rules per project, revoke
with destructive confirm), AI setup (`AiSetupScreen`: **review only**, owner decision 2026-09-28 - settings
that apply, MCP servers and state, suggestions; nothing applied/undone), Server plugins (no page of its own any more: `ServerPluginsSection` "On the server" inside the server page,
SCREENS-NAV-75 - plugin inventory, refresh action on the page bar, command-link mapping dialog + "Clear all
personal command links"; `plugins_screen.dart` only keeps AI Team row helpers), Keyboard shortcuts row (desktop), Try the demo. *settings/\*, saved_permissions_screen.dart.*

### 6J. Tools and library

**SCREENS-NAV-94 - Tools hub.** `CapabilitiesScreen` (Settings > Tools; `openTools(section)`): tabs MCP,
Commands, Tools, Skills, References, External agents; each body built on first open and kept. `CommandsScreen`
(the command sheet embedded; a pick asks which conversation, default most recent), `ToolsScreen` (tool ids +
parameter types in words; model summary header opens the model picker), `SkillsScreen` + skill activation
sheet, `ReferencesScreen` (readable name + what it holds; tap opens the reference), pull to refresh on each,
loading skeleton / empty explains / failed explains. *capabilities_screen.dart, library/\*, tools_screen.dart.*

**SCREENS-NAV-95 - Providers and MCP.** `IntegrationsScreen(mode: providers|mcp|legacy-combined,
connectProviderID)`: provider connections (API key dialog `integrations-connect-key-dialog`, OAuth method
sheet, device-code/`Finish <provider>` dialog, pending-auth recovery "Forget on this device"/"Forget uncertain
start?" `oc.pendingAuth.*`, credential sheet: rename account, remove with confirm, disconnect), MCP servers
list (status, auth, **loopback OAuth callback** completes MCP sign-in automatically, manual code dialog),
add sheet `showMcpAddSheet` -> `McpCatalogScreen` (public MCP registry `registry.modelcontextprotocol.io`,
switches, ones on this server first, search re-reads registry) or `McpSetupScreen` (manual form: scope,
name+address, headers as name + obscured value, Advanced; saved/failed states). Setup assistant not offered
(not built). Provider keys come back from `/config/providers`: **never** logged/echoed (AGENTS security
invariant). Authorization launch goes through the external-link review. *library/integrations_*.dart,
credential_sheet.dart, mcp_catalog_screen.dart, mcp_setup_screen.dart, state/pending_auth.dart.*

### 6K. Usage

**SCREENS-NAV-96 - Usage hub.** `UsageHubScreen(initialSection: plans|spending)`: **Plans** (every subscription the
app can see across connections, most urgent first; `UsagePlanBlock`: name + tier left, one summary right
("12% left" / "Back at 1:25 PM"), one line per window with bars showing what is *left*, amber past alert
level, strong neutral when low/out - red is forbidden, `fdda22007`), **Spending** (OC2 `UsageScreen`: total
first, range+scope, USD/token budgets with set/clear dialogs, report) - Spending absent when the
connection cannot serve it and then no tab strip; one Refresh on the bar for the active tab
(`UsageRefreshSlot`); `UsageSubscriptionScreen` (every window with exact reset, this plan's alert level,
reset switch, where signed in, details folded), `UsageAddPickerScreen`/`UsageAddScreen` (providers never
used with logos; where to sign in when several connections), `UsageReadFromScreen` (a computer is read by a
one-shot read-only check through OpenCode on each refresh; in-app Ubuntu set up for real),
`UsageAlertsScreen` (one switch watches every subscription, level and reset-message defaults; quiet hours
and Wi-Fi only are the shared values), `ConnectionSignInLauncher`. Stored: `oc.usageAlerts.v1`,
`oc.usageAlertSeen.v1`, `oc.usageLast.*`, `oc.usageComputerReader.*`, `oc.paseoUsage.*`,
`oc.quotaMonitor.*`, `oc.quotaToken.*`, `oc.consumptionBudgets.*`, `oc.budgets.*`. *usage_hub_screen.dart,
usage_screen.dart, usage/\*.*

**SCREENS-NAV-97 - Usage widget settings.** `UsageWidgetScreen`: which plans, 5-hour/weekly/both, rows that
need setup; every connection read automatically; snapshot `oc.usageWidget.v1`, `oc.widgetUsage*`. *usage_widget_screen.dart,
background/usage_widget_snapshot.dart.*

### 6L. Scheduling

**SCREENS-NAV-98 - Scheduled prompts.** `ScheduledPromptsScreen`: calm row per schedule (its words, one
state line, quiet switch), one mint "New scheduled prompt" at the thumb; row opens `showScheduleSheet`: the
prompt, when (Once / Every day / Weekdays / Custom on a wheel `KitWheel`), where ("neon-breaker - Laptop":
server, project, conversation, agent, model), one main action naming the moment ("Schedule for 9:00 AM"),
delete / open last run's conversation / run now exits, last-run line, optional **morning summary**,
history, battery/exact-alarm copy; typed prompt kept as a draft; a changed server certificate says so in run
history; scheduled runs keep the server's plain-HTTP consent and pin; a conversation's schedule keeps its
model; finished one-time schedule reads "no more runs". Entry: conversation menu, Go to, Settings, chat
long-press Send. Execution is native + headless Dart (`scheduledPromptMain`, bounded Android leases,
`ScheduledPrompt*.kt`); stored `oc.scheduledPrompts.*`, `oc.scheduleDeleted.*`. *scheduling/\*, lib/scheduling/\*.*

### 6M. FactsBook (developer flag)

**SCREENS-NAV-99 - FactsBook.** Whole feature hidden unless `--dart-define=OC_FACTSBOOK=true` or pref
`oc.dev.factsbook` (app-wide, not swept with profile). Screens (spec 60/44): `FactsBookMemorySection` on
Chats, `AboutYou` hub (search, Needs you, Reminders, Triggers, Areas, things by kind, Add something,
Status, Settings), `NeedsYou` (changes proposed wait for Approve/Reject/Edit/Skip; chat offers; notes
not saved), `Capture` (add note/pasted text, "Also remind me" runs reminder check), `ReminderCheck`
sheet (title, date, time, repeat, zone, language editable before saving), `Reminders` (Active/Paused/Done),
`Thing` (facts with provenance), `Search` (words not meaning; locked notes not searched), `Areas`
(sharing per area; Health/Money locked folders start off; changes signed by this phone's owner key),
`Triggers`/`RuleEditor`/`Event` (rules suggest only; "What happened to this message"), `OfferCard`
("Talk about this with your agent?" Yes opens a chat whose first words are the summary only), `NotSaved`
(day-30 Retry/Export/Discard), `Status` (per-module word, never one green "connected"), `Settings`,
`Connect` (node address on a private network + one-time code -> token in secure storage; approval key),
`PhoneNode` (FactsBook inside built-in Linux: one button installs, starts, connects). Keys `oc.factsbook.*`,
`oc.factsbookNode.*`, `oc.factsbookOutbox.*`, `oc.factsbookOwner.*`, `oc.factsbook-node.passphrase|stopped`.
*factsbook/\* (21 files, ~6.9k lines), state/factsbook/\*.* Recommend porting last / behind the same flag.

### 6N. Widgets, manage space

**SCREENS-NAV-100 - Home-screen widget overlay panels.** `WidgetConfigureScreen` (ground `solid|tinted|
transparent`, accent `wallpaper|app|green|teal|blue|amber`, a drawing of the result, Save styles that one
widget, host closes itself), `WidgetProjectScreen` (Conversations widget Edit: All projects first, then
recent; one tap saves and closes), `WidgetSteerScreen` (floating box: type or dictate, send; steer mode
`steer|afterReply|restart|unknown` from native; voice route opens dictation at once and starts a new
conversation). All via `PortWidgetOverlayApi` (`oc/widgets`: `getLaunchArguments`, close...). Widget
tap routes arrive as launch actions / alert extras (4.1, 4.4); Reply lands with composer focused. ZTE
launcher traps (previewImage + single current-size render) live in native. *screens/widgets/\*, widgets_overlay/\*.*

**SCREENS-NAV-101 - Manage space.** `ManageSpaceScreen`: what clearing deletes (in-app server, projects,
conversations), Export projects first (`ProjectExportScreen`, SAF via `oc/project_export`), Clear cache
only, Delete everything after explicit confirm. *manage_space_screen.dart, manage_space_main.dart.*

### 6O. Diagnostics, about, updates

**SCREENS-NAV-102 - Report a problem / diagnostics.** `openReportProblem(error)` (every error state's
"Report a problem" ends here): `AppDiagnosticsScreen` - one calm card with the failure that opened it
(else newest kept error), user adds words if they want, send as GitHub issue / Copy (redacted by
`KitRedact`); draft key `oc.draft.report.app` (app-wide, cleared when sent); `AppDiagnosticsKeptScreen`
(everything kept: errors, exits, performance), `PerfTraceSection` (steps grouped by name slowest first;
copyable report; Clear), `ExitHistorySection` (Android `ApplicationExitInfo` records; problem exits lead),
`CrashReportsSection` (opt-in consent to keep crash/"stopped responding" reports; what/where/how much),
`openCrashReportShare` (sheet shows exactly the text, <=20 records/16 KiB; only an explicit Share
hands it over). Route `/debug` and desktop palette "Diagnostics". *app_diagnostics*.dart, perf_trace_section.dart,
exit_history_section.dart, crash_report*.dart, lib/diagnostics, feedback/bug_report.dart.*

**SCREENS-NAV-103 - About, Guide, Tips.** `AboutScreen` (build, what it is not, one-time tips, keyboard
shortcuts, open-source notices from bundled `THIRD_PARTY_NOTICES.md`/`LICENSES/`, privacy moved to
Privacy), `GuideScreen`, `TipsScreen`. *about_screen.dart, guide_screen.dart, settings/tips_screen.dart.*

**SCREENS-NAV-104 - Update notices.** `ShorebirdUpdateNotice` (code push: download automatically when the
profile's `AutomationBehavior.applyCodePush` allows; "Update ready - restart to apply" lowest status;
`recordServerAct` files it in "While you were away"), `DesktopReleaseNotice` (GitHub tag check). **Shorebird
ends at the native switch**; the in-app update notice stays (owner decision). *update/\*.*

### 6P. Cross-cutting UI systems

**SCREENS-NAV-105 - The kit (reference).** 280 files: layout/nav (`KitScreen, KitNav, KitTopBar(+shell,
scope, tail, centred, pill), KitSheet(+frame, modal, parts), KitDialog, KitConfirmSheet, KitMenu,
KitContextRegion`), rows/lists (`KitRow, KitRowGroup, KitSliverRowGroup, KitFeedItem, KitFilterChips,
KitSwipeAction, KitWindowRow`), inputs (`KitField, KitSearchField, KitSegmented, KitWheel,
KitStepSlider, KitOneTimeCode, KitDateTimePicker`), states (`KitStateView, KitSkeletonTranscript, KitNotice,
KitStatusLine, KitUndo, KitProgress*, KitChecklist, KitLogPanel`), chat (`kit/chat/*`: composer family,
turn/message/work line/tool row/queued message/step timeline/phrase chips/stream caret), diff
(`kit/diff/*`), motion (`kit/motion/*`: reveal, tab switcher, refresh, haptics, animated rows), style
(`kit/style/*` design schools), live background (`kit/live_background/*`), scenes (`kit/scenes/*` drawings),
viewer (`KitViewer*`), terminal (`KitTerminalView`, key bar), team parts, capability explainer, QR
(`KitQr`), scanner (`KitScanner`), web preview (`KitWebPreview`), image gallery/markup. Gates
(`test/kit_ratchet_test.dart` G16, capacity/max-width gates, manifest test). Debug-only per-frame check
(`_KitScreenCheck`): at most one primary button, one drawn status line, one refresh, one details fold per
visible subtree. Another map owns the kit; screens depend on it as above.

**SCREENS-NAV-106 - Iconography.** `AppIconography` (1,028 lines): product verbs -> Phosphor 2.1.0 (MIT)
glyphs as `IconData` constants (tree-shakeable), regular = action weight, selected nav = duotone fill,
mirroring set pinned by test. Agent/provider marks in `assets/marks/` (lobe-icons MIT, monochrome tinted);
`ProviderLogo` fetches models.dev logos (cached on disk, refreshed <= weekly). Use the owner's agent-glyphs
skill for icon work. *app_iconography.dart, widgets/provider_logo.dart.*

**SCREENS-NAV-107 - Accessibility contract.** Always-visible nav labels (A11Y-1), status words never
colour-only ("Needs you", "Done", "Stopped"), 44-48 dp targets, semantics on every list item and
announcements (`SemanticsService`: TalkBack hears when a reply finishes/fails/needs you, `a3fd9cb83`;
one-shot notices wait for Dismiss under accessible navigation), each Appearance question its own reader
stop, no text cut mid-word at 2x (pseudo-localised +40 % tests), `KitText.capScale(max: 1.5)` on page
titles, reduced motion, high contrast, Saver. *throughout; tests `test/a11y*`.*

**SCREENS-NAV-108 - External link gate.** `openExternalLink` (`widgets/external_link.dart`) is the only way a URL the
app did not author reaches `url_launcher` (markdown links in agent output, OC2 external form fields, update notices,
OAuth/authorization launches, preview "Open in browser"). Policy: only `https:` opens directly; `http:` only after a
separate explicitly-insecure confirmation (the risky choice is the error-toned one, Enter never selects it);
every other scheme refused (`intent:`, `file:`, `content:`, `javascript:`, `data:`, app-private); embedded
credentials refused; a host is required; hard length ceiling; the effective destination host is shown before
anything opens; every answer is a kit modal. `ExternalLinkOutcome` tells callers what happened. `openAgentSignInPage`
adds an exact-URL check (`https://claude.com/cai/oauth/authorize`). Security invariant in `AGENTS.md`: breaking it is a
security regression. Dev-preview is the one documented exception (SCREENS-NAV-58). *widgets/external_link.dart,
`test/external_link_test.dart`.*

**SCREENS-NAV-109 - Copy, redact, undo infrastructure.** `KitCopy.copy(context, text, redact:, announcement:)` is
the one copy service (shows a check in place, announces for screen readers, **no snackbar**); `KitRedact.text`
masks keys/tokens in everything that is not the person's own words (transcript copy, details, logs, share-failed
copy keeps the person's words verbatim with `redact:false`, SEC-13). `showKitUndo` is the one Undo bar: act-now
with inverse `onUndo`, or deferred-commit (`onCommit` runs exactly once unless Undo is taken); one bar at a time
(a new call first commits the pending one); window `KitMotion.undoWindow`; never auto-dismissed under accessible
navigation (Dismiss action instead); a throwing undo switches the bar to its failure form. Used by: saved-prompt
delete/restore, share discard, display toggles, theme apply, permission answers (held Undo window), card answers,
merge-on-green. *kit/kit_copy.dart, kit_undo.dart, kit_redact.dart.*

**SCREENS-NAV-110 - Per-conversation backend scope.** Every conversation route is wrapped by
`ConversationBackendScope` (SCREENS-NAV-04) which overrides `connProvider` and the status line with the
conversation's own connection (`backendForConversation`, `openBackendForConversation`,
`agentConversationNeedsBackend`); lists, sheets and the command launcher open conversations through
`ConnectionChatsHost.openChat`. The rewrite needs an explicit "conversation context" object (profile,
backend kind-as-capabilities, connection health) passed down rather than a global controller.
*navigation/attention_landing.dart, widgets/conversation_status_scope.dart, chats/chats_host.dart.*

**SCREENS-NAV-111 - Always allowed actions.** `SavedPermissionsScreen`: the agent's saved "always allow" rules for
the current project on servers that keep them (OC2), each revocable after a destructive confirm; pull to refresh;
reached from Permissions & automation, the server page, and the permission sheet's Always allow explanation. *saved_permissions_screen.dart.*

**SCREENS-NAV-112 - Session pins.** The only place a conversation can be pinned/unpinned in this checkout is a
tile in `SessionRelationsScreen` (guarded by a stack of identity/route/location-revision re-checks before the
mutating `setSessionPinned`; failure note "Could not update the pin. Return and try again"); the `pin` nudge is
hard-disabled (`conversation_nudges.dart`) and the Work screen that used to host pins was retired. Pins persist as
`oc.sessionPins.<profile>` (`{scope: [sessionId]}`, scope = directory+workspace, written only after storage
acknowledges), rank in the memoised `sortedSessions()` ordering and are published as launcher shortcuts
(SCREENS-NAV-15, max 4). Decide in the rewrite whether pins get a first-class entry (the shortcut feature has
no obvious way to create its input today). *state/session_pins.dart, state/connection/sessions.dart,
session_relations_screen.dart.*

## 7. Data this area stores (exact keys / paths / formats)

Everything below is Flutter `SharedPreferences` (Android file `shared_prefs/FlutterSharedPreferences.xml`,
every key prefixed `flutter.`) unless it says **secure** (`flutter_secure_storage` 11.x) or **file**.
Per-profile keys are `oc.<what>.<profileId>` so `ProfileStore.profileScopedPreferenceKeys` sweeps them on
delete (shared blobs need `ConnectionController.deleteProfileAndLocalData`). The first-launch migration
must carry all of them; the state/storage map owns formats of non-UI keys (see `lib/state/profiles.dart`).

| Key / path | What | Format / notes |
|---|---|---|
| `oc.activeProfile`, `oc.profiles` (+ secure secrets per profile) | saved servers, active one | JSON list; secrets (passwords, `oc.codexToken.<id>`, `oc.githubToken.*`, `oc.quotaToken.*`, `oc.a2aBearer.*`, `oc.byoHostSecrets.*`, `oc.agentHostSecret.*`, `oc.teamEngineAuth.*`) in secure storage |
| `oc.certificatePin.<id>`, `oc.cleartextOk.<id>` | pinned cert, explicit HTTP consent | consulted by scheduled runs too |
| `oc.lastProject.<profile>` | last project directory used for a chat | plain string |
| `oc.location.<id>`, `oc.recentLocations.<id>`, `oc.sharedProjects.<id>` | open project, recents, shared-storage project roots | |
| `oc.sessionDrafts` | composer drafts per profile+session (+attachment refs) | JSON map; 600 ms debounce write |
| `oc.draft.<target>.<profileId>` (`KitDraft`) | typed input kept across sheet dismissals (new chat `new-chat.<base64url dir>`, report, schedule, review comment, ...) | plain string; `.app` suffix = app-wide (Report a problem) |
| `oc.promptHistory.<profile>`, `oc.promptStash.<profile>.<id>`, `oc.draftAttachmentVault`, `oc.stashAttachmentVault.*`, `oc.pendingPromptPhoto`, `oc.withdrawnPrompts.*`, `oc.olderDraftsBackupV1.*`, `oc.savedPromptMigrationV1.*` | history (JSON list), stash (one key per prompt, `StashedPrompt.toJson`), attachment vaults | attachment bytes live in app files (see `app_flutter/` below) |
| `oc.offlineQueue` | offline prompt queue | JSON; capped by composer's aggregate attachment cap |
| `oc.sessionPins.<profile>` | pinned conversations | JSON `{scope:[sessionId]}` |
| `oc.pinnedShortcuts` | what was last published to the launcher | |
| `oc.chatListShown.<profile>`, `oc.sessionInventory.*`, `oc.chatTail.*`, `oc.conversationTitles.*`, `oc.contextUsage.*`, `oc.sessionViews.<profile>` | list sources shown, last-known titles (<=100, 64 KiB), tail excerpts (<=12 x 1,000 scalars), context ring values, read-state | redacted; never populate live maps |
| `oc.chatAgent.*`, `oc.sessionAgents.*`, `oc.agentModel.*`, `oc.model.*`, `oc.modelExplicit.*`, `oc.variant.*`, `oc.sessionModels.*`, `oc.sessionDisplayModels.*`, `oc.modelNames.*`, `oc.modelLibrary.*` | agent/model choices per profile/session | |
| `oc.autoApprove.<profile>` | auto-approve per conversation; reserved key `*` = server-wide "Approve everything" | |
| `oc.chatNoticesSeen.*`, `oc.agentTurnFailures.*`, `oc.freeModelNoteDismissed.*`, `oc.defaultNotices.*`, `oc.defaultProject.*` | one-time notes, remembered failures for "no reply" reason | |
| `oc.tips`, `oc.nudges.shown/projects/firstReplySeen`, `oc.firstRun.state`, `oc.firstRun.notifyAsk`, `oc.appearanceGuide` | teaching & onboarding memory | `oc.tips` JSON `{id:{count,lastShown,done}}` |
| `oc.appearance` (system/light/dark/saver), `oc.appearanceAccent`, `oc.themePack` (enum name), `oc.textSize`, `oc.effects*` (15 keys), `oc.glass*`, `oc.appLocale`, `oc.transcript.reasoningExpanded`, `oc.transcript.timestampsVisible`, `oc.readerPreferences.*`, `oc.phraseChips.*`, `oc.phraseChips.enabled`, `oc.readAloudConsent` | personal/device display choices | stored **by enum name**: unknown names (retired Glass/Crystal) read as Solid |
| `oc.notify.*` (`finishedRuns, requests, quotaAlerts, quietStart, quietEnd, wifiOnly, checkInAfterMinutes, version`), `oc.notifyRules.*`, `oc.keepLiveInBackground`, `oc.automation.<profile>`, `oc.automaticActivity.*`, `oc.inFlowConsent.*`, `oc.permissionConsent.*`, `oc.backgroundPauseDismissed`, `oc.shareSessionViews*`, `oc.readStateConsentMigration.v1` | notifications / background / consent | notification prefs migrated once (`migrateNotificationPreferences`) |
| `oc.monitorAlerts/Busy/Routes.*`, `oc.attentionTile` | saved-server monitoring; QS tile cache | |
| `oc.scheduledPrompts.*`, `oc.scheduleDeleted.*` + native store (`ScheduledPromptStore.kt`) | schedules, tombstones | native is source of truth for alarms |
| `oc.usageAlerts.v1`, `oc.usageAlertSeen.v1`, `oc.usageLast.*`, `oc.usageWidget.v1`, `oc.widgetUsage`, `oc.widgetUsageSubs`, `oc.widgetSessions`, `oc.widgetTarget.*`, `oc.widgetFollowup.*`, `oc.quotaMonitor.*`, `oc.paseoUsage.*`, `oc.consumptionBudgets.*`, `oc.budgets.*` | usage + widgets | |
| `oc.developmentServices.*`, `oc.thermalGuard`, `oc.thermalPause.*`, `oc.phoneServerStopped.*`, `oc.builtinServerOwner`, `oc.builtinRecovery.*`, `oc.linuxReset.pending`, `oc.termuxMigration.*`, `oc.termuxMigrationOffer.*`, `oc.managedServerRecovery.*`, `oc.replySpeed.inApp`, `oc.setupAudit.*`, `oc.setupRegistry.*` | phone / server owner state | `setup.json` file in app files is the setup job's truth |
| `oc.teamModel.*`, `oc.teamRoles.*`, `oc.teamTaskRoles.*`, `oc.teamWorkerStart.*`, `oc.teamWorkspace.*`, `oc.teamEditorDrafts.*`, `oc.teamMigrationKept.*`, `oc.teamEngineDeleted.*`, `oc.orchestration.*` | AI Team | |
| `oc.genui.enabled.*`, `oc.genui.covered.*`, `oc.genui.journal.*` | agent cards | |
| `oc.dev.factsbook` + `oc.factsbook*` | FactsBook flag + data | |
| `desktop_window_state_v1` | desktop window | JSON `{x,y,width,height,maximized}` |
| files | `files/` (rootfs of built-in Ubuntu - Kotlin already owns), `app_flutter/` (Flutter `path_provider` application documents: attachment vaults, provider logo cache, setup.json, voice models per `lib/voice`), cache dir | migration map is another agent's; this area only needs the vaults and logo cache |

## 8. Native bridges this area touches

| Channel | Kotlin class | Used by (screens/nav) | Methods seen |
|---|---|---|---|
| `oc/shortcut` | `LaunchShortcutChannel`, `PinnedSessionShortcuts` | launch routing | `consumeLaunchAction`, `consumeSessionLaunch`, `setPinnedSessions` ; native->Dart `launched`, `launchedSession` |
| `oc/link` | `LaunchLinkChannel`, `SessionLinkIngress` | deep links | `consumeSessionLink` ; `linked` |
| `oc/share` | `LaunchShareChannel` | share-in, outgoing share | `consumeSharedText`, `shareText{text,subject}` ; `shared` |
| `oc/background` | `MainActivityBackgroundChannel`, `BackgroundConnectionService` | notification taps, live mode, battery exemption | `consumeCodingAlertOpen` (map kind/sessionID/profileID/reply/monitorToken), live-background controls |
| `oc/scheduled_prompts` | `ScheduledPromptChannel` | schedule UI + open | `consumeOpen`, `execute`, CRUD |
| `oc/widgets` / `oc/widget_action` | `WidgetOverlayActivity`, `WidgetActionBridge/Runtime` | widget panels | `getLaunchArguments`, `close`, style save, steer, `checkStatus`, `cancel` |
| `oc/termux` | `TermuxActivityBridge` | This phone, Termux pages, server rows | run commands, status, open settings, `managesServerUrl` |
| `io.github.eslamasabry.opencode_mobile/builtin_linux` | `BuiltinLinux` | This phone, setup, Linux manager, local terminal, folder browser | install/start/stop/run/list (off main thread; ownership errors `NativeRuntimeOwnership`) |
| `oc/storage` | `StorageAccess` | shared-storage project flow | all-files-access check/request |
| `oc/camera`, `oc/voice`, `oc/read-aloud` | `MainActivityMedia`, `ReadAloudBridge` | composer, pairing scanner | permission, probe, capture, TTS |
| `oc/local_pdf` | `LocalPdfBridge` + `LocalPdfService` (isolated process `:local_pdf`) | PDF preview | render page, cancel |
| `oc/project_export` | `ProjectExportBridge` | Manage space, This phone | SAF export |
| `oc/tailscale` | `TailscaleHandoff` | Tailscale setup | open official app |
| `oc/network`, `oc/thermal`, `oc/lifecycle`, `oc/crash_diagnostics`, `oc/byo_host_signer` | `NetworkMonitor`, `ThermalMonitor`, `AppLifecycle`, `CrashDiagnosticsBridge`, `ByoHostSigner` | status lines, heat guard, exit history, BYO VPS | |
| Quick Settings / widgets / notifications | `AttentionTileService`, `PauseTileService`, `*WidgetProvider`, `StatusNotification*`, `CodingActionReceiver`, `LivePauseReceiver`, `StatusActionReceiver` | entry routing | PendingIntent extras listed in 4.1/4.4 |

Native pattern worth copying: every channel's `configure` resets `dartReady=false`; the "consume" call is the
readiness handshake; live events only after it; `reset()` on engine detach; `NativeChannelReplies.guarded`
wraps results so a throwing handler cannot kill the channel; long work off the main thread.

## 9. Tricky behaviours and hard-won fixes a rewrite must not lose

Commit hashes are in `git log` of the repo (short). Grouped by theme.

**Back / navigation / pending intents**
- Predictive back vs row transform; chat `canPop` only when nothing is lost (`f6c005423`, `0a886b852`, `c62155ff2`, `c4a24b05f`); list holds still while a page above moves (`f7e775dd3`, `89828a3c8`).
- A notification for the open chat must not stack a second copy (`c5842e836`); leave for `/home` only where the route is declared (`2d8f2ce6f`).
- Cold-start scheduled-run tap raced the root connect: 30 s hold then fall back to history (`23c851fc8`).
- Share/launch/link wait on "saved server on its way, nothing failed" - not on `hasConnectedServer` alone; consume only if still pending; intents stripped from the Intent so rotation cannot replay.
- Widget Reply on an already-open chat re-opens it with the composer focused (`abdf9e0fe`).
- Launch start before any "stopped" verdict: no stopped flash (`a3768cbb7`, QA B7); a running in-app server is reconnected or replaced, never called stopped (`5d7c8dbef`); resume reconnects while healing checks (`7b630c7b4`).
- Opening shell: same HomeScreen before/after connect so nothing moves (`c45c02031`, J2); the "restarted after the app closed" line folds on page change, not a timer (`39be428aa`).
- Locale fallback trap: generated `supportedLocales` is alphabetical so unmatched device languages fell to **Arabic**; English must lead the list (`resolveAppLocales`, `fedd85d8e`).
- Home Back: Files unwinds before destinations; tapping Files again leaves to hub (`f067a1cf0`).

**Chat correctness**
- OC2 notices have role `user`; turn edges via `_isPrompt/_endsTurn/_turnSteps`; images are re-encoded so match on text+count+name.
- A prompt shows once; unconfirmed send not restored into composer (`023331578`); no automatic replay of uncertain sends (`f29f47634`); Stop calls off a waiting send (`45cfc5983`); send after resume goes live not to queue (`4e635b73b`); steer waiting in inbox must not cut the turn it joins (`9de34551c`); words sent again after Stop show once (`75e43a60b`).
- Sub-agent page: no Stop, stall line opens main conversation (`205602578`); reopening a saved agent never forks (`ace6fcb28`); failed agent turn keeps its reason after force-stop/restart (`569917504`, `0dbd368e0`); signed-out agent and declined tool call are not failures (`e95203a9b`).
- OC2 turn stays running between steps; lost tool results settle (`697658cd8`); compaction mid-turn no longer breaks newest row (`43fabb514`); read history again when a turn crossed a resume (`b1cac692b`); no "History refreshed" note on move to latest (`f614d6a3b`).
- Rebuild storm: stop rebuilding whole history on every stream flush (`3cd315485`); earlier-messages pill builds only when count changes (`1fc4393d5`).
- Scroll: new message stays in view; jump stops above composer; sending from further up shows it (`9064fc102`, `8c614c58d`, `a1de5069d`, `890aa83a6`).
- Error text: every surface showing server/provider text goes through `agentErrorWords`; recovered errors are quiet lines; top banner hidden when a message already carries the same error; recurring "no raw errors" rule (`4d955a77b`, `6cdfca4e1`).
- Photos: scaled 2048 px/q85 (WebSocket overflow); gallery multi-select; camera photo recovery after Android killed the app.
- Request cards: one at a time; landscape card scrolls from question; arrival marks card once; no Thinking line while a card waits (`b36b83645`); cards in list answer with secondary buttons.
- Tool preview loads file once (`88aaa5d4f`); background-agent result kept separate from parent's reply.
- Stop moved to the chip row (owner 2026-10-30) and amber when stuck; "Stop and send" for non-steering agents.

**Lists / servers**
- List first paint holds this server's rows, not only agents' (`e268c012b`); late rows arrive (fade + 8 dp) while first-painted ones do not; "couldn't load" names each connection once and retries; unread agent cards never make list say "couldn't load" (`ace0663d8`); stopped-by-person reply says Stopped not Done (`f0420edee`).
- Same-host servers need ports to be told apart (`cc34b9d31`, `e9fe5715c`); a changed certificate shown once (`93f3d5879`); adding a server hides the stale banner about the current one (`6d9fc3e71`).
- Two in-app profiles both "This phone": the chat line must say which (`among: store.profiles`).
- A busy phone server is not a dead one (`4208e6108`); short agent link drops stay quiet (`6cd86fa36`); one indicator while switching/reconnecting (`bc35cb563`).
- Server switcher only chooses; shell acts after the sheet is closed.
- Bootstrap: renders immediately; secure-storage reads in groups of four (`profiles.md`); drain phone agents before erasing sign-ins (`ee2c53940`).

**Files / project / terminal**
- Shared-storage project asks for All files access, never shows empty (`63adfa9b8`, #87); `/files` opens server folder without a project (`ec6d7be4f`).
- Terminal input queue (ordered writes); top/htop; short screen usable (`5cc94b6bb`).
- Dev preview address is built, not taken from link text.

**Settings / copy / icons**
- Settings: server tap opens its own page (`42a72fe6e`, `05c7f0eb1`); one page per server (`05d416916`); phone's own server named "this phone's server" (`3ce48ccd5`).
- One glyph per meaning (`0afbda57c`); mirror reading-direction glyphs only (`a98cebeee`); nothing red anywhere (`fdda22007`); icons never mislead.
- Usage: low/out use strong neutral not amber/red; bars show what is left (`92c1d8ca1`, `65476b1a4`).
- Phone setup: notifications asked when setup starts (`580c83595`); installed check retried before "could not check" (`3f3f6b9e8`); setup ownership race (`c05b4bf82`); refresh installed tools without false absence (`f441c209c`).

**Security / privacy invariants that live in UI**
- Every URL the app did not author -> `openExternalLink` (only exception: dev preview, built address, documented). Copy actions go through `KitCopy`/`KitRedact` (the person's own words verbatim; everything else masked). Provider credentials never echoed. Pairing payload is a credential (redacted `toString`). Per-profile key naming and sweep. Deep links carry ids only; address links parsed network-silent with consent per step; shortcut/launch actions never send. Crash storage never delays the first frame, consent-gated. No analytics SDK in code (an Aptabase plan exists only in `docs/aptabase-telemetry-plan.md`).

## 10. Performance notes

- Startup: first paint is the bootstrap state; heavy work post-frame; Home builds only Chats at start (`KitTabSwitcher lazy/preload`); settings/other tabs never read at startup (`docs/qa/codex-perf-2026-09-28/startup.md`); cached opening shell; `PerfTrace` marks `app.main, app.first_frame, app.bootstrap, app.shell_frame, app.first_connected, chat.open_to_fresh, settings.open` (full list in code: also `agent.start.feed, app.recover*, connect.health, events.connected, lifecycle.reload, permissions.refresh, prompt.*, questions.refresh, reply.awake_capped, session.create, sessions.refresh, thermal.*`). Keep the same marks; owner uses the Performance report in App diagnostics.
- Chat: coalesced stream flushes; page-cache of display passes; `ScrollablePositionedList` for the transcript; memoised session ordering; sessions page + status read overlap; Native v2 HTTP keeps idle sockets 15 s; Dio's large-JSON isolate decode; chat tail prefetch coalesced with live hydration.
- Lists stop following the connection while covered; markdown patterns compiled once; Chats row entrance only for later rows.
- Memory/process: PDF render in an isolated process; one request at a time; no always-running animations except where the owner approved (live line sweep, composer glow).
- Flutter baseline (from the demo comparison): APK 107 MB vs 2.5 MB native demo, cold start ~0.4 s vs ~0.25 s; Flutter engine startup cost is gone in the rewrite, but the 107 MB includes bundled ML Kit barcode model, sherpa-onnx, assets.

## 11. Risks and recommendations for the native rewrite

1. **The chat screen is the product** (27.7k lines in 71 files; by far the longest fix history). Port it as a
   conversation state-holder with explicit state machines (send, stop, steer, queue, receipts, drafts, scroll)
   and write the tests first from `test/` chat suites; do not port widget-by-widget.
2. **Pending-intent mechanism** (section 4) is subtle and replicated 7 times; build it once in shared Kotlin
   (or common Kotlin) with the waiting/final/consume rules, and port the launch/link/share/tile/pinned-
   shortcut tests (`test/launch_*`, `session_address_link_*`, `background_notification_navigation_test.dart`).
3. **Predictive back + container transform** conflict (demo lesson): decide per route class
   (list->chat grow; settings pages; sheets). Chat must use the "pop only when nothing is lost" rule.
   Compose Multiplatform navigation is in flux: JetBrains' notes for 1.10 describe Navigation 3 on
   non-Android targets as alpha and the adaptive libraries at alpha versions
   ([What's new in Compose Multiplatform 1.10](https://kotlinlang.org/docs/multiplatform/whats-new-compose-110.html));
   Android's own Navigation 3 supports predictive back and shared elements
   ([Navigation with shared elements](https://developer.android.com/develop/ui/compose/animation/shared-elements/navigation)).
   Re-verify the versions when the rewrite starts; keep navigation behind a small interface so Android can use
   the stable stack while iOS/desktop follow.
4. **Adaptive**: the Flutter app is phone-first with a rail/sidebar and 2/3-pane parts already; port
   `KitLayout` thresholds (600/840/1200, short < 480) as the single window-class function; keep "absent,
   never disabled" and the one-status-line rule.
5. **Capability gating, never backend kind**: ~30 capability flags (`ServerCapabilities`) decide every menu,
   tab, slash command and row; copy the flag list from the gateway and gate in one place.
6. **Plain words**: the mapping tables (section 0) are product content. Port them as data with their tests;
   every error state needs the Details fold + Report a problem hook.
7. **Text scale**: implement the owner's 1.3x chrome cap / full reading scale from day one (not in Flutter yet).
8. **RTL/Arabic**: ARBs already exist (en/ar/zh/es + partial ja/pt/ru); plan string import tooling
   (ARB -> Android resources/CMP resources) with ICU plural parity; Arabic-Indic digits via locale formatting.
9. **Teaching, nudges, Help > Tips, search index, Go to** are cross-cutting registries whose entries
   point at widget keys; give composables stable test tags equal to the current `ValueKey` strings
   (`chats-new-chat`, `server-switcher-button`, `home-shell-search`, `session-menu-<value>`...) so Flutter
   tests port mechanically.
10. **Android-only vs desktop**: ~35 places gate on `supportsTermux`, 16 on `desktopInteractions`. KMP shared
    code should expose the `PlatformCapabilities` object as an interface; Android-only screens (This phone,
    setup, Linux manager, Termux, local agents, widgets, QS tiles, shortcuts) stay in the Android module.
11. **"While you were away"**: acts are recorded (`recordServerAct`, `oc.automaticActivity.*`) but no screen in
    this checkout shows them; decide whether the rewrite builds the page or drops the recording.
12. **FactsBook** is flag-gated, 21 files / ~6.9k lines of screens; port last.
13. **Shorebird ends at the switch**: every Dart-only hotfix path disappears; the in-app update notice must
    move to a Play/GitHub-APK check (the same notice slot, lowest priority).
14. **Day-1 migration** of UI-facing state (drafts, stash, offline queue, pins, tips, appearance) must read
    the `flutter.`-prefixed keys above and preserve JSON shapes; drafts with attachment vaults are the most
    fragile (files + keys must move together).

## 12. Source pointers (read these before porting a cluster)

- Page-by-page element tables: `docs/design/ui-ledger/pages.md`, `ledger.json`, `navigation.md`, `findings.md`, `gesture-audit.md`; older page records `docs/ux-system/map/*.json` (per-page `states`, `needs`, `whenMissing`); `docs/qa/screen-census/` has rendered PNGs per page state.
- IA and journeys: `docs/ux-system/target-ia.md`, `journeys.md`, `capabilities.md/.json`, `personas-verticals.md`, `screen-standard.md`, `taxonomy.md`, `teaching.md`, `kit-v2.md`.
- Owner decisions (memory dir): no-bottom-nav, chat-kit-decisions, chat-transcript-turn-model, android-native-feel, text-scale-cap, component-max-width-rule, teach-what-is-hidden, settings-scope-model, simplify-and-natural, obvious-options-first, actions-name-their-target, steer-not-queue, no-glass-rule, no-raw-errors-rule, design-standard-rule, appearance-studio-decision.
- Protocol/behaviour docs: `docs/opencode2-protocol-notes.md`, `docs/design/dev-server-preview-2026-10-16.md`, `command-receipts-contract.md`, `agent-handoff-contract.md`, `genui-contract-2026-10-07.md`, `git-page-contract-2026-10-19.md`, `local-terminal-2026-09-24.md`, `phone-setup-v2-2026-09-24.md`, `linux-manager-2026-10-31/`, `docs/desktop.md`.

## Appendix A. Every file in scope, with its public classes/entry functions

Generated from the source on 2026-11-01 (line counts in parentheses). "part/helpers" = a `part of` file
or helpers with no public class. Use it to find where a feature above lives.

#### Screens (`lib/ui/screens/`)


`./`

- `about_screen.dart` (562) — AboutScreen, showPrivacyPolicy()
- `active_context_screen.dart` (522) — ActiveContextScreen
- `agent_account_screen.dart` (613) — AgentAccountScreen, AgentAccountPanel
- `app_diagnostics_kept_screen.dart` (342) — AppDiagnosticsKeptScreen
- `app_diagnostics_screen.dart` (495) — openReportProblem(), AppDiagnosticsScreen
- `automation_settings_screen.dart` (537) — AutomationSettingsSections, AutomationSettingsSection
- `capabilities_screen.dart` (320) — ToolsSection, CapabilitiesScreen, openTools(), openExternalAgents()
- `chat_screen.dart` (801) — ChatScreen
- `crash_report_share.dart` (169) — openCrashReportShare()
- `crash_reports_section.dart` (337) — CrashReportsSection
- `demo_screen.dart` (189) — DemoScreen
- `dev_preview_conversation.dart` (92) — DevPreviewDelivery, DevPreviewConversation
- `dev_preview_screen.dart` (472) — openDevPreview(), DevPreviewScreen
- `dev_server_logs.dart` (95) — showDevServerLogs()
- `development_services_screen.dart` (1187) — DevelopmentServicesScreen
- `exit_history_section.dart` (272) — ExitHistorySection
- `external_agents_screen.dart` (1313) — ExternalAgentsScreen, ExternalAgentDetailScreen, ExternalTaskScreen
- `files_screen.dart` (540) — pushWorkingTreeReview(), FilesBackController, FilesScreen
- `global_sessions_screen.dart` (1189) — GlobalSessionsScreen
- `guide_screen.dart` (381) — GuideScreen, Cmd, Bullet
- `home_opening.dart` (39) — HomeOpening
- `home_screen.dart` (705) — HomeScreen
- `host_management_screen.dart` (155) — HostManagementScreen
- `isolated_task_sheet.dart` (726) — showIsolatedTaskSheet(), IsolatedTaskSheet
- `keep_running_screen.dart` (321) — openKeepRunningScreen(), KeepRunningSection
- `library_screen.dart` (66) — part/helpers
- `local_agent_screen.dart` (89) — LocalAgentScreen
- `local_terminal_screen.dart` (447) — LocalTerminalView
- `manage_space_screen.dart` (516) — ManageSpaceScreen, ProjectExportScreen
- `managed_workspaces_screen.dart` (723) — ManagedWorkspacesScreen
- `mcp_catalog_screen.dart` (650) — McpAddPath, showMcpAddSheet(), McpCatalogScreen
- `mcp_setup_screen.dart` (1030) — McpSetupScreen
- `pairing_scanner_screen.dart` (251) — PairingScannerScreen
- `perf_trace_section.dart` (174) — PerfTraceSection
- `profile_monitor_screen.dart` (362) — openMonitoredRequest(), ProfileMonitorInbox
- `project_folder_actions.dart` (719) — ProjectFolderActions
- `project_health_screen.dart` (542) — ProjectHealthScreen
- `project_hub_screen.dart` (988) — ProjectTool, OpenProjectToolIntent, openProjectTool(), openProjectFiles(), ProjectHubBackController, ProjectHub
- `projects_screen.dart` (552) — ProjectsScreen
- `recent_error_words.dart` (46) — part/helpers
- `review_workspace.dart` (1255) — ReviewDiffMode, ReviewDiffScope, ReviewWorkspace, DiffPage
- `running_on_phone_screen.dart` (719) — RunningOnPhoneScreen
- `running_work_sheet.dart` (1023) — showRunningWorkSheet(), RunningWorkSheet, ShellOutputScreen
- `saved_permissions_screen.dart` (389) — SavedPermissionsScreen
- `server_capabilities_screen.dart` (558) — ServerFeatureSource, ServerFeature, ServerCapabilitiesScreen
- `servers_screen.dart` (197) — ServersRouteRequest, ServersRouteRequestKind, ServersScreen
- `session_context_screen.dart` (936) — SessionContextBreakdownKind, SessionContextBreakdownSegment, SessionContextMetrics, SessionContextScreen
- `session_destination_sheet.dart` (780) — SessionDestinationMode, showSessionDestinationSheet(), showConsoleOrganizationSheet()
- `session_export_screen.dart` (333) — SessionExportScreen
- `session_import_screen.dart` (673) — SessionImportFile, SessionImportScreen
- `session_note_screen.dart` (455) — SessionNoteScreen
- `session_relations_screen.dart` (525) — SessionRelationsScreen
- `settings_screen.dart` (591) — SettingsGroup, SettingsScreen
- `shared_storage_access_flow.dart` (244) — SharedStorageOutcome, SharedStorageAccessFlow
- `staged_revert_screen.dart` (724) — showStageRevertSheet(), StagedRevertScreen
- `tailscale_setup_screen.dart` (398) — TailscaleSetupScreen, TailscalePhoneSteps, TailscaleHelpFold
- `terminal_screen.dart` (319) — TerminalPage, TerminalSource
- `termux_migration_screen.dart` (1137) — openTermuxMigration(), TermuxMigrationScreen
- `termux_processes_screen.dart` (794) — TermuxProcessesScreen
- `termux_storage_screen.dart` (678) — TermuxStorageScreen
- `this_phone_screen.dart` (1335) — openThisPhone(), ThisPhoneScreen
- `tools_screen.dart` (539) — ToolsScreen, ToolParameterType
- `usage_hub_screen.dart` (194) — UsageSection, UsageHubScreen
- `usage_refresh_slot.dart` (54) — UsageRefreshSlot
- `usage_screen.dart` (955) — UsageScreen
- `usage_widget_screen.dart` (191) — UsageWidgetScreen
- `web_sources_screen.dart` (559) — WebSourcesScreen
- `worktrees_screen.dart` (719) — WorktreesScreen

`agents/`

- `agent_error_notice.dart` (35) — AgentErrorNotice
- `agent_model_sheet.dart` (145) — showAgentModelSheet()
- `agent_notices.dart` (161) — AgentStatusNotices
- `agent_sheet.dart` (1006) — AgentSheetStep, showAgentSheet(), AgentSheet
- `agent_sign_in_terminal.dart` (386) — AgentSignInTerminalScreen
- `agents_screen.dart` (64) — AgentsScreen
- `agents_section.dart` (265) — AgentsSection
- `agents_text.dart` (325) — AgentFailure
- `cards_from_agents_row.dart` (169) — CardsFromAgentsRow
- `phone_check_view.dart` (140) — AgentPhoneCheckView

`chat/`

- `agent_cards.dart` (135) — part/helpers
- `approval_mode_menu.dart` (188) — part/helpers
- `approvals_sheet.dart` (368) — showSessionApprovalsSheet(), SessionApprovalsSheet
- `assistant_error_row.dart` (283) — part/helpers
- `attention_card.dart` (754) — part/helpers
- `chat_attachments.dart` (556) — part/helpers
- `chat_body.dart` (144) — part/helpers
- `chat_command_actions.dart` (571) — part/helpers
- `chat_commands.dart` (667) — part/helpers
- `chat_composer_region.dart` (644) — part/helpers
- `chat_dev_preview.dart` (149) — part/helpers
- `chat_drafts.dart` (427) — part/helpers
- `chat_files.dart` (466) — part/helpers
- `chat_gallery.dart` (194) — part/helpers
- `chat_handoff_link.dart` (140) — part/helpers
- `chat_history.dart` (497) — part/helpers
- `chat_mentions.dart` (218) — part/helpers
- `chat_message_actions.dart` (316) — part/helpers
- `chat_notices.dart` (649) — part/helpers
- `chat_page.dart` (345) — part/helpers
- `chat_page_cache.dart` (401) — part/helpers
- `chat_queue.dart` (391) — part/helpers
- `chat_requests.dart` (214) — part/helpers
- `chat_running_work.dart` (328) — part/helpers
- `chat_schedule_send.dart` (113) — part/helpers
- `chat_scroll.dart` (418) — part/helpers
- `chat_send.dart` (767) — part/helpers
- `chat_session_actions.dart` (489) — part/helpers
- `chat_session_menu.dart` (510) — part/helpers
- `chat_start.dart` (137) — part/helpers
- `chat_states.dart` (605) — part/helpers
- `chat_status_line.dart` (210) — part/helpers
- `chat_stop_after_step.dart` (232) — part/helpers
- `chat_stream.dart` (562) — part/helpers
- `chat_top_bar.dart` (380) — part/helpers
- `chat_transcript.dart` (784) — part/helpers
- `chat_voice.dart` (314) — part/helpers
- `command_launcher.dart` (50) — part/helpers
- `composer.dart` (782) — part/helpers
- `composer_tools.dart` (328) — part/helpers
- `composer_tray.dart` (63) — part/helpers
- `empty_chat.dart` (359) — ChatStartFacts, ChatStarter
- `form_flow.dart` (214) — FormAnswerReceipt, FormAnswerReceipts
- `message_view.dart` (764) — part/helpers
- `no_reply_reason.dart` (217) — part/helpers
- `nudge_slot.dart` (264) — part/helpers
- `pending_sends_strip.dart` (306) — part/helpers
- `permission_ask.dart` (266) — PermissionAsk
- `permission_sheet.dart` (749) — PermissionAnswerState, PermissionAnswers, showPermissionDetails(), showPermissionSheet(), showQuestionDetails(), PermissionSheet
- `phrase_chips.dart` (149) — part/helpers
- `prompt_editor.dart` (203) — part/helpers
- `prompt_history.dart` (162) — part/helpers
- `prompt_stash.dart` (580) — part/helpers
- `question_sheet.dart` (412) — showQuestionSheet()
- `read_aloud.dart` (298) — part/helpers
- `receipt_check.dart` (66) — part/helpers
- `sent_photos.dart` (92) — part/helpers
- `session_sheets.dart` (87) — part/helpers
- `stuck_command.dart` (121) — part/helpers
- `team_agent_conversation.dart` (616) — TeamAgentConversationMiss, TeamAgentConversationLookup, openTeamAgentConversation(), openTeamAgentConversationById(), TeamControllerScope, TeamOpenConversationRow, TeamAgentTranscript
- `team_conversation_actions.dart` (772) — part/helpers
- `team_conversation_parts.dart` (530) — part/helpers
- `team_conversation_view.dart` (429) — TeamPendingTask, TeamConversationScreen
- `team_watch_live.dart` (349) — TeamWatchLiveScreen
- `timeline_sheet.dart` (271) — part/helpers
- `transcript_find.dart` (345) — part/helpers
- `transcript_rows.dart` (799) — TranscriptMarker, BackgroundShellResultRow, TranscriptNotice, V2TranscriptRow, BackgroundAgentResultCard
- `transcript_turns.dart` (749) — part/helpers
- `voice_conversation.dart` (671) — part/helpers
- `watching.dart` (328) — ChatWatch

`chats/`

- `chats_home_screen.dart` (1004) — ChatsHomeScreen
- `chats_host.dart` (604) — ConnectionChatsHost
- `chats_project_sheet.dart` (161) — ChatsProjectChoice, ChatsAllProjects, ChatsOtherFolders, ChatsOneProject, ChatsOpenProject, showChatsProjectSheet()
- `chats_sources_sheet.dart` (85) — showChatsSourcesSheet()
- `claude_import_screen.dart` (190) — showClaudeImport(), ClaudeImportScreen
- `git_repair_sheet.dart` (204) — showGitRepairSheet()
- `new_chat_screen.dart` (694) — showNewChat(), NewChatScreen

`factsbook/`

- `about_you_screen.dart` (341) — FactsBookAboutYouScreen
- `areas_screen.dart` (212) — FactsBookAreasScreen
- `capture_screen.dart` (451) — FactsBookCaptureScreen
- `connect_screen.dart` (182) — FactsBookConnectScreen
- `event_detail_screen.dart` (276) — FactsBookEventScreen
- `factsbook_routes.dart` (70) — openFactsBookAboutYou(), openFactsBookCapture(), openFactsBookNeedsYou(), openFactsBookNotSaved(), openFactsBookThing(), openFactsBookSearch(), openFactsBookConnect(), openFactsBookReminders(), openFactsBookAreas(), openFactsBookStatus(), openFactsBookSettings(), openFactsBookTriggers(), openFactsBookRule(), openFactsBookEvent(), openFactsBookPhoneNode()
- `factsbook_text.dart` (282) — part/helpers
- `memory_care_screen.dart` (644) — FactsBookMemoryCareScreen
- `memory_section.dart` (413) — FactsBookMemorySection
- `not_saved_screen.dart` (343) — FactsBookNotSavedScreen
- `offer_card.dart` (189) — FactsBookOfferCard
- `phone_node_screen.dart` (413) — FactsBookPhoneNodeScreen
- `reminder_check_sheet.dart` (459) — showFactsBookReminderCheck()
- `reminders_screen.dart` (302) — FactsBookRemindersScreen
- `rule_editor_screen.dart` (715) — FactsBookRuleEditorScreen
- `search_screen.dart` (176) — FactsBookSearchScreen
- `settings_screen.dart` (295) — FactsBookSettingsScreen
- `status_screen.dart` (383) — FactsBookStatusScreen
- `thing_screen.dart` (404) — FactsBookThingScreen
- `trigger_text.dart` (116) — part/helpers
- `triggers_screen.dart` (275) — FactsBookTriggersScreen

`files/`

- `files_actions.dart` (138) — part/helpers
- `files_body.dart` (514) — part/helpers
- `files_entries.dart` (293) — part/helpers
- `files_loading.dart` (295) — part/helpers
- `files_viewer.dart` (378) — part/helpers

`inbox/`

- `inbox_items.dart` (165) — InboxEntry, InboxFeedEntry, InboxWatchedEntry
- `inbox_screen.dart` (483) — openInbox(), InboxScreen

`library/`

- `command_auth_sheet.dart` (283) — part/helpers
- `commands_screen.dart` (129) — CommandsScreen
- `credential_sheet.dart` (424) — part/helpers
- `integration_tiles.dart` (901) — part/helpers
- `integrations_autowait.dart` (248) — part/helpers
- `integrations_connect.dart` (590) — part/helpers
- `integrations_mcp.dart` (464) — part/helpers
- `integrations_page.dart` (578) — part/helpers
- `integrations_screen.dart` (432) — IntegrationsMode, IntegrationsScreen
- `integrations_signin.dart` (545) — part/helpers
- `pending_auth_recovery.dart` (13) — part/helpers
- `references_screen.dart` (273) — ReferencesScreen
- `skill_activation.dart` (318) — part/helpers
- `skills_screen.dart` (196) — SkillsScreen

`linux_manager/`

- `linux_manager_common.dart` (211) — LinuxJobPanel
- `linux_manager_screen.dart` (175) — openLinuxManager(), LinuxManagerScreen
- `linux_packages_page.dart` (503) — LinuxPackagesPage
- `linux_ports_page.dart` (285) — LinuxPortsPage
- `linux_processes_page.dart` (457) — LinuxProcessesPage
- `linux_reset.dart` (132) — part/helpers
- `linux_storage_page.dart` (326) — LinuxStoragePage

`phone_setup/`

- `phone_setup_customize_sheet.dart` (563) — SetupCustomizeSheet
- `phone_setup_hero.dart` (155) — PhoneSetupHero
- `phone_setup_progress_screen.dart` (574) — PhoneSetupProgressScreen
- `phone_setup_ready_screen.dart` (301) — PhoneSetupReadyScreen
- `phone_setup_routes.dart` (133) — openPhoneSetupStart(), openPhoneSetupProgress(), openPhoneSetupReady()
- `phone_setup_selection.dart` (143) — part/helpers
- `phone_setup_start_screen.dart` (763) — PhoneSetupStartScreen
- `phone_setup_termux_job_screen.dart` (672) — PhoneSetupTermuxJobScreen, openPhoneSetupTermuxJob(), PhoneSetupUnsupportedScreen
- `phone_setup_termux_screen.dart` (440) — PhoneSetupTermuxScreen, openPhoneSetupTermux()
- `phone_setup_welcome_entry.dart` (305) — PhoneWelcomeOffer, PhoneSetupWelcomeEntry

`scheduling/`

- `schedule_copy.dart` (129) — part/helpers
- `schedule_routes.dart` (201) — openScheduledPrompts(), openStoredScheduledPrompts(), openScheduledRun()
- `schedule_sheet.dart` (540) — ScheduleSheetRule, ScheduleSheetExit, showScheduleSheet()
- `scheduled_prompts_screen.dart` (362) — ScheduledPromptsScreen

`servers/`

- `profile_editor.dart` (405) — part/helpers
- `profile_editor_fields.dart` (500) — part/helpers
- `profile_editor_pairing.dart` (580) — part/helpers
- `profile_editor_probe.dart` (484) — part/helpers
- `profile_editor_steps.dart` (389) — part/helpers
- `profile_editor_trust.dart` (134) — part/helpers
- `profile_editor_widgets.dart` (335) — part/helpers
- `server_rows.dart` (474) — part/helpers
- `servers_actions.dart` (600) — part/helpers
- `servers_state.dart` (369) — part/helpers

`settings/`

- `agents_permissions_screens.dart` (157) — AgentsHubScreen, PermissionsAutomationScreen
- `ai_setup_screen.dart` (524) — AiSetupScreen
- `appearance_one_page.dart` (191) — part/helpers
- `appearance_screen.dart` (1471) — AppearanceSection, AppearanceSettingsScreen
- `connection_check_screen.dart` (208) — ConnectionCheckScreen
- `default_shell_row.dart` (252) — DefaultShellRow
- `glass_choice_control.dart` (80) — GlassChoiceControl
- `hub_servers.dart` (400) — SettingsServersBlock, openServerPage(), LanguageSettingsRow, ByServerKind, ByServerScreen
- `notifications_settings_screen.dart` (788) — NotificationsPage, NotificationsSettingsScreen
- `personal_settings_screens.dart` (305) — PrivacySettingsScreen
- `phrase_chips_row.dart` (55) — PhraseChipsSettingsRow
- `plugins_screen.dart` (115) — part/helpers
- `reach_computer_screen.dart` (86) — ReachComputerScreen
- `server_host_update.dart` (160) — part/helpers
- `server_page.dart` (1026) — ServerPageScreen
- `server_plugins_section.dart` (480) — ServerPluginsActions, ServerPluginsSection
- `settings_group_page.dart` (457) — SettingsGroupScreen
- `tips_screen.dart` (149) — TipsScreen

`team/`

- `agent_screen.dart` (986) — AgentScreen
- `gate_sheet.dart` (1122) — showGateSheet(), GateSheet
- `merge_section.dart` (920) — TeamMergeSection
- `policy_block.dart` (181) — TeamPolicyBlock, TeamBoundaryChips, TeamBoundariesRow
- `project_demo_screen.dart` (71) — TeamProjectDemoScreen
- `project_destination.dart` (38) — openTeamProjectDestination()
- `role_screen.dart` (583) — RoleScreen
- `start_run_sheet.dart` (1146) — StartRunResult, showStartRunSheet(), StartRunSheet, TeamStartBlocked
- `task_details_sheet.dart` (837) — showTeamTaskDetails(), TeamTaskDetails, TaskEventScope
- `team_agents_screen.dart` (523) — TeamAgentStanding, openTeamRole(), TeamAgentsScreen
- `team_board_screen.dart` (646) — openTeamBoardCard(), openTeamBoard(), TeamBoardScreen
- `team_home_screen.dart` (1114) — TeamRunFilter, TeamHomeScreen
- `team_intro_screen.dart` (635) — TeamIntroScreen
- `team_migration.dart` (132) — TeamMigrationGate, openTeamMigration(), TeamMigrationScreen
- `team_model_sheet.dart` (84) — TeamModelChoice, showTeamModelSheet()
- `team_needs_you.dart` (480) — TeamNeedsYouCard, TeamGateRow
- `team_page.dart` (311) — openTeamPage(), openTeamSetup(), PhoneTeamOffPage, TeamPage
- `team_phone_setup_screen.dart` (421) — openPhoneTeamSetup(), TeamPhoneSetupScreen
- `team_rig_screen.dart` (348) — openTeamRig(), TeamRigScreen
- `team_settings_screen.dart` (531) — openTeamSettings(), TeamSettingsScreen
- `team_states.dart` (416) — part/helpers
- `work_graph.dart` (74) — WorkGraphNode
- `work_sheet.dart` (653) — showWorkSheet(), WorkValidation, WorkSheet

`team/projects/`

- `team_digest.dart` (33) — part/helpers
- `team_execution_gate.dart` (216) — TeamExecutionNeed, TeamExecutionGate, TeamExecutionBlocked, TeamProtectionLine, TeamPhoneServerProblem, TeamResumeUnavailable
- `team_merge_flow.dart` (147) — part/helpers
- `team_project_conversation.dart` (985) — TeamProjectConversation
- `team_project_editors.dart` (1497) — openTeamNewProject(), openTeamSpecEditor(), openTeamPlanEditor(), openTeamProjectSettings(), openTeamRoles(), openTeamDefaults()
- `team_project_editors_roles.dart` (312) — part/helpers
- `team_projects_screen.dart` (221) — TeamProjectsScreen
- `team_refusal.dart` (253) — TeamRefusal
- `team_status_words.dart` (40) — part/helpers

`team/projects/screen/`

- `board_timeline.dart` (276) — TeamProjectBoard, TeamProjectTimeline
- `overview.dart` (454) — TeamProjectOverview
- `overview_helpers.dart` (498) — part/helpers
- `servers_card.dart` (190) — TeamProjectServers

`team_conversation/`

- `team_conversation.dart` (166) — TeamConversation

`terminal/`

- `terminal_accessible.dart` (196) — part/helpers
- `terminal_input.dart` (65) — TerminalInputQueue
- `terminal_list.dart` (806) — TerminalScreen
- `terminal_surface.dart` (670) — TerminalSurface

`this_phone/`

- `this_phone_answer.dart` (461) — part/helpers
- `this_phone_details.dart` (193) — part/helpers

`usage/`

- `usage_actions.dart` (291) — UsageSignInOutcome, ConnectionSignInLauncher
- `usage_add_picker_screen.dart` (53) — UsageAddPickerScreen
- `usage_add_screen.dart` (166) — UsageAddScreen
- `usage_alerts_screen.dart` (277) — UsageAlertsScreen
- `usage_plan_card.dart` (163) — UsagePlanBlock
- `usage_plans_page.dart` (338) — UsagePlansPage
- `usage_read_from_screen.dart` (377) — UsageReadFromScreen
- `usage_subscription_screen.dart` (551) — UsageSubscriptionScreen
- `usage_text.dart` (381) — UsageText

`widgets/`

- `widget_configure_screen.dart` (130) — WidgetConfigureScreen
- `widget_project_screen.dart` (118) — WidgetProjectScreen
- `widget_steer_screen.dart` (114) — WidgetSteerScreen

#### Widgets (`lib/ui/widgets/`)


`./`

- `agent_blocks.dart` (296) — AgentBlockKinds, AgentChoiceScope, AgentChoicesBlock, AgentChecklistItem, AgentChecklistBlock, AgentCommandBlock
- `agent_card_asks.dart` (493) — AgentCardDraft, AgentCardAsk, PickedPhoto
- `agent_card_form.dart` (342) — AgentCardForm
- `agent_card_nodes.dart` (184) — part/helpers
- `agent_card_photos.dart` (105) — StoreAgentCardPhotos
- `agent_card_view.dart` (295) — AgentCardView
- `agent_features_chip.dart` (283) — AgentFeaturesModel, AgentFeaturesChip, showAgentFeaturesSheet()
- `always_allow_invitation.dart` (223) — AlwaysAllowInvitation
- `app_connection_status.dart` (269) — AppConnectionStatusScope, AppExitFoldOnPageChange, AppConditionsScope
- `app_exit_notice.dart` (212) — AppExitNoticeLine
- `appearance_picker.dart` (233) — showThemePackPreview()
- `background_pause_notice.dart` (141) — part/helpers
- `builtin_team_section.dart` (760) — BuiltinTeamSection
- `command_sheet.dart` (775) — CommandSheetEntry, CommandSheetTab, CommandSheet
- `connect_methods.dart` (59) — part/helpers
- `connection_failure.dart` (325) — ConnectionFailureAction, ConnectionFailure
- `connection_status_banner.dart` (566) — ConnectionStatusBanner, showConnectionDetailsSheet()
- `connector_card.dart` (502) — ConnectorCardView
- `continue_with_sheet.dart` (724) — ContinueWithResult, showContinueWith(), showContinueWithSheet()
- `conversation_status_scope.dart` (81) — ConversationStatusScope
- `default_notices.dart` (78) — part/helpers
- `delimited_file_preview.dart` (93) — DelimitedNotice, DelimitedFilePreview
- `external_link.dart` (277) — ExternalLinkOutcome, openExternalLink(), openAgentSignInPage()
- `file_preview.dart` (881) — FilePreviewData, FilePreviewUnavailable, showFilePreviewSheet(), showFilePreviewSheetLoading(), FilePreviewBody, SmartTextPreview
- `file_share_sheet.dart` (172) — showFileShareSheet()
- `folder_browser.dart` (1181) — FolderBrowserChoice, FolderBrowserOpen, FolderBrowserCreate, FolderBrowserEnterPath, PhoneStoragePlace, FolderBrowserSheet
- `form_renderer.dart` (1352) — FormRenderer
- `grace_timer.dart` (86) — GraceTimer
- `language_picker.dart` (198) — showLanguageSheet(), LanguageSettingsTile
- `last_known_sessions.dart` (79) — LastKnownSessions
- `local_agent_onboarding.dart` (1271) — LocalAgentUiStep, LocalAgentUiStepState, LocalAgentOnboardingBlock, showLocalAgentProjectSheet()
- `local_agent_server_entry.dart` (476) — LocalAgentServerEntry
- `local_server_row.dart` (295) — LocalServerRowMenuItem, LocalServerRow
- `managed_move_view.dart` (76) — ManagedMoveView
- `managed_server_recovery_option.dart` (221) — ManagedServerRecoveryOption
- `markdown.dart` (335) — MarkdownInteractionScope, MarkdownFileLinks, MarkdownText
- `mobile_task_view.dart` (188) — MobileTaskList
- `model_shortcuts.dart` (246) — TranscriptKey, ModelShortcuts, StopOnEscape, ModelCycleButton
- `older_sessions_pager.dart` (205) — OlderSessionsPager
- `pdf_file_preview.dart` (228) — LocalPdfUnavailable, LocalPdfPages, PdfFilePreview
- `phone_project_kind.dart` (40) — part/helpers
- `phone_server_card.dart` (1420) — PhoneServerAction, PhoneServerCardRoutes, PhoneRuntimeRemoval, PhoneServerCard
- `phone_server_consents.dart` (128) — part/helpers
- `phone_server_names.dart` (110) — part/helpers
- `phone_server_restart.dart` (80) — part/helpers
- `pickers.dart` (782) — ModelPickerApplyScope, showModelPicker(), ModelCatalogView
- `pickers_catalog_footer.dart` (297) — part/helpers
- `pickers_catalog_models.dart` (384) — part/helpers
- `pickers_catalog_sections.dart` (386) — part/helpers
- `pickers_choices.dart` (205) — part/helpers
- `product_states.dart` (157) — ProductErrorLocale, showProductError()
- `prompt_history_navigation.dart` (65) — PromptHistoryNavigation
- `provider_logo.dart` (84) — ProviderLogo
- `question_options.dart` (19) — part/helpers
- `queued_prompt_move_sheet.dart` (448) — showQueuedPromptMoveSheet()
- `reader_preferences.dart` (119) — ReaderPreferencesScope, ReaderWrapButton
- `relative_time.dart` (19) — part/helpers
- `remote_folder_picker.dart` (202) — RemoteFolderSheet
- `request_routes.dart` (57) — RequestRoutes
- `run_command_dialog.dart` (295) — showRunCommandDialog()
- `running_agents_strip.dart` (105) — RunningAgentEntry, RunningAgentRelation
- `running_on_phone_row.dart` (83) — RunningOnPhoneRow
- `running_on_phone_text.dart` (146) — part/helpers
- `runtime_switch_status.dart` (33) — part/helpers
- `safety_confirms.dart` (198) — part/helpers
- `saved_server_connection_card.dart` (483) — SavedServerConnectionCard
- `server_switcher_sheet.dart` (979) — ServerSwitcherChoice, ServerSwitcherOpenServers, ServerSwitcherOpenPhoneSetup, ServerSwitcherOpenChat, ServerSwitcherPhoneAction, ServerSwitcherLeave, showServerSwitcher(), ServerSwitcherSheet
- `session_address_sheets.dart` (467) — SessionAddressFailureNotice, SessionAddressOpened, showSessionAddressSheet(), SessionAddressSheetBody
- `session_handoff.dart` (85) — SessionNavigationScope, showSessionHandoff()
- `session_handoff_sheets.dart` (448) — showContinueOnComputerSheet(), showContinueOnPhoneSheet(), SessionAddressOffer, ContinueOnComputerSheet, ContinueOnPhoneSheet
- `session_menu.dart` (293) — SessionMenuAction, SessionMenuOffer
- `session_read_state.dart` (160) — SessionViewObserver, SessionUnreadBadge
- `session_title.dart` (38) — part/helpers
- `setup_progress_view.dart` (738) — SetupProgressView
- `setup_terminal.dart` (169) — SetupTerminal
- `setup_ui_messages.dart` (425) — part/helpers
- `svg_file_preview.dart` (63) — SvgFilePreview
- `team_agent_row.dart` (134) — TeamContextNumber, TeamAgentRow
- `team_board_card.dart` (236) — TeamBoardCardView
- `team_board_move_sheet.dart` (223) — TeamBoardSheetChoice, TeamBoardChoseMove, TeamBoardChoseOpen, showTeamBoardMoveSheet(), showTeamBoardPrioritySheet()
- `team_controls.dart` (132) — part/helpers
- `team_discover.dart` (89) — TeamServerKind
- `team_discovery_card.dart` (198) — TeamDiscoveryResult, TeamDiscovery
- `team_host_form.dart` (628) — showTeamHostSheet(), TeamHostFormActions, TeamHostForm, showTeamHostGuideSheet(), showTeamTurnOffSheet()
- `team_job_words.dart` (79) — part/helpers
- `team_moments.dart` (267) — TeamCelebrations, TeamMergedCelebration, TeamNeedsYouLabel
- `team_now.dart` (402) — TeamRest
- `team_now_line_view.dart` (531) — TeamNowLineView
- `team_phone_onboarding.dart` (821) — openTeamOnThisPhone(), TeamPhoneReadyScreen, TeamPhoneKilledNotice
- `team_phone_section.dart` (567) — TeamPhoneSection, showTeamPhoneTipsSheet()
- `team_receipt.dart` (122) — part/helpers
- `team_role_copy.dart` (98) — part/helpers
- `team_switch.dart` (105) — TeamOffOutcome
- `team_technical_details.dart` (332) — showTeamHostDetailsSheet(), TeamHostDetailsSheet, TeamIdentityRow, TeamTechnicalValue, TeamTermRow
- `team_vocabulary.dart` (1243) — TeamMark, TeamStage, TeamAgentRole, TeamRunProgress, TeamFailureClass
- `terminal_view.dart` (51) — TerminalView
- `termux_migration_entry.dart` (213) — TermuxMigrationRow, TermuxMigrationOffer
- `termux_phone_tools.dart` (256) — TermuxStorageRow, PhoneProcessesRow, TermuxRunawayWatcher
- `termux_problem_fix.dart` (166) — TermuxProblemWords, showTermuxOtherAppsSheet()
- `termux_running_server_entry.dart` (673) — LocalServerCardActions, TermuxRunningServerEntry, shownAsPhoneRow()
- `thermal_notice.dart` (90) — ThermalNoticeLine
- `tool_card.dart` (659) — ToolCard, TitleWithDetail, StepNote
- `tool_card_body.dart` (963) — part/helpers
- `tool_card_contract.dart` (807) — part/helpers
- `tool_card_parts.dart` (307) — part/helpers
- `transcript_display_toggles.dart` (127) — TranscriptDisplayToggles
- `transcript_highlight.dart` (180) — TranscriptHighlight, TranscriptMatchExcerpt
- `work_status_line.dart` (206) — WorkStatus, WorkRunawayNotice, RunawayStopOutcome

#### Git sheet (`lib/ui/git/`)


`./`

- `git_sheet.dart` (334) — showGitSheet(), GitSheet
- `git_sheet_home.dart` (529) — part/helpers
- `git_sheet_signin.dart` (148) — part/helpers
- `git_sheet_steps.dart` (585) — part/helpers

#### Search (`lib/ui/search/`)


`./`

- `go_to_commands.dart` (201) — part/helpers
- `search_index.dart` (496) — SearchEntryKind, SearchScope, SearchEntry

`search_index/`

- `hub_rows.dart` (664) — part/helpers
- `inside_pages.dart` (512) — part/helpers
- `places.dart` (220) — part/helpers

#### Teaching (`lib/ui/teaching/`)


`./`

- `teaching.dart` (294) — TeachingKind, TeachingEntry, TeachingIds, MakeItYoursAnchors

#### Agent text (`lib/ui/agents/`)


`./`

- `agent_persona_text.dart` (63) — part/helpers

#### Navigation (`lib/ui/navigation/`)


`./`

- `attention_landing.dart` (144) — ConversationBackendScope
- `chat_route.dart` (45) — ChatRouteArguments
- `last_project.dart` (21) — part/helpers

#### Desktop (`lib/ui/desktop/`)


`./`

- `context_menu.dart` (78) — ContextMenuAction, ContextMenuRegion, showContextMenu()
- `desktop_interaction.dart` (84) — AppScrollBehavior, DesktopScrollbarArea, OwnScrollbar, DesktopSelectionArea
- `file_drop.dart` (149) — DroppedFile, DesktopFileDropTarget, DesktopFileDropTargetState
- `shortcuts.dart` (817) — OpenCommandPaletteIntent, NewSessionIntent, FindInSurfaceIntent, OpenSettingsIntent, CloseRouteIntent, ShowShortcutsHelpIntent, OpenTerminalIntent, SelectDestinationIntent, OpenChatsIntent, ShortcutHelpGroup, ShortcutHelpEntry, AppShortcutSignals, AppShortcutScope, AppShortcutSurface, AppShortcutHandlers, AppShortcuts, DesktopCommandSection, DesktopCommand, showCommandPalette(), showShortcutsHelp()

#### main_parts (`lib/main_parts/`)


`./`

- `alerts_and_shortcuts.dart` (345) — part/helpers
- `bootstrap_gate.dart` (287) — AppBootstrapGate
- `launch_route.dart` (403) — part/helpers
- `link_routes.dart` (324) — part/helpers
- `root.dart` (643) — part/helpers
- `schedule_route.dart` (105) — part/helpers
- `share_route.dart` (175) — part/helpers

#### Widget overlay (`lib/widgets_overlay/`)


`./`

- `widget_accents.dart` (17) — part/helpers
- `widget_compose_controller.dart` (142) — WidgetComposeNotice, WidgetComposeBlock, WidgetComposeController
- `widget_overlay_api.dart` (38) — PortWidgetOverlayApi
- `widget_overlay_app.dart` (108) — WidgetOverlayApp
- `widget_overlay_launch.dart` (52) — WidgetOverlayLaunch
- `widget_style_controller.dart` (67) — WidgetStyleProblem, WidgetStyleController
