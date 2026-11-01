# 07. Screens and navigation

Written 2026-11-01. Owner of this section's code: the coordinator (`:ui:routes`, `:ui:shell` and `:engine:entry` are
single-owner modules, ADR §5.2). Feature agents own their own route file, entry installer and screens.

## For the owner, in five lines

1. Every Flutter screen, sheet and dialog has a named native home below: 85 pages in the app, 26 shared sheets, plus the
   Manage space and widget panels, checked against the freeze branch (`int/freeze-2026-11-01`, `fc5c1e78f`). Nothing is
   left without a decision.
2. Phones keep today's shape: Conversations is home, the gear opens Settings, Files is reached from inside a
   conversation, and there is no bottom bar. Tablets and landscape phones keep the side rail.
3. Every way into the app (shortcuts, links, shares, notifications, widgets, tiles, scheduled runs) goes through one
   router. It waits until the app is ready and opens the right place exactly once, including shortcuts and
   notifications that the Flutter app created before the update.
4. Removed: "Press back again to exit", the Termux management pages, outside (A2A) agents, and four dead or duplicate
   pages. AI Team and FactsBook come right after the switch.
5. Six small choices for you are in §13. Each has the default we build if you say nothing.

Tags: **[V]** checked today against official docs or the source. **[R]** read in the repo, a map, a research file, the
ADR or memory. **[U]** unverified; the named proof settles it. Behaviour ids such as `B-NAV-025` are rows of
`../catalog/BEHAVIORS.md`. Flow ids such as `E2E-25` are flows in `../catalog/e2e-flows.md`. `SCREENS-NAV-NN` ids are
features of `../map/screens-nav.md`.

---

## 0. Scope, inputs and how this section fits

### 0.1 What this section decides

- The full route list (§2.2), the shared-sheet list (§2.3), the navigator's behaviour (§2.4–§2.7).
- Transitions and Back for every route class (§3).
- The navigation graph (§4) and the screen-by-screen inventory: Flutter thing → native home (§5).
- What is dropped, merged or deferred, and why (§6).
- Entry routing for every Android entry point (§7).
- Adaptive layouts (§8) and keyboard shortcuts (§9).
- Per-screen obligations that cut across features (§10), build order (§11) and acceptance checks (§12).
- **The shapes that slice 0 freezes for navigation.** This section is the single source for: every `:ui:routes` key and
  its `meta`, `RouteMeta`/`RouteMotion`/`RouteRequirement`, `AppNavigator`, `Arrivals` and the arrival types, `SheetKey`,
  `SearchEntry`/`SearchExclusion`, `CapabilityFlow`, and the `:engine:entry` types `EntryRequest`, `EntryTarget`,
  `EntryHandoff` and `ShellWorld`. Section 03 §15 and ADR §9.4 show older versions of some of these; §16 lists every
  difference and the amendment the coordinator writes into 03 and the ADR before the slice-0 freeze.

### 0.2 What it does not decide

- The file skeleton, DI wiring and Gradle setup of `:ui:routes`, `:ui:shell` and `:engine:entry`: section 03 owns
  them (its choices S5, S10, S12 and S17, and the §15.4 pattern of wrapping the entry provider in the shell). Where 03
  shows a different signature for a type listed in §0.1, this section's shape is the one frozen (§16).
- Kit part internals (`KitSheetHost`, `KitNavRail`, `KitTopBar`, transitions' exact curves): the kit section. This
  section names the kit APIs it needs and what they must do.
- Chat internals (transcript, composer, send pipeline): the chat section. This section gives the chat its route, its
  arrivals and its Back rule.
- Widget, notification and tile rendering: the Android surfaces section. This section owns where their taps land.

### 0.3 Inputs read

ADR `01-architecture-decision.md` (binding), `03-architecture-modules.md` (in progress, aligned with S5/S10/S12/S17),
the maps `screens-nav.md` (112 features, the main source), `chat-genui.md`, `android-platform.md`, `builtin-linux.md`,
`agents-backends.md`, `desktop-web-cross.md`, `ui-kit.md`, `media-voice.md`, `protocols.md`, `state-data.md`, the
research files `motion-native.md`, `kmp-cmp-status.md`, `architecture-stacks.md`, `android-surfaces.md`,
`testing-perf.md`, the catalog, and the owner memory files listed in §15. The Flutter source at
`/home/eslam/Storage/Code/wt/oc_app-batch1` (HEAD `65e3f70a3`, app 2.1.0+57) was read to confirm entry points,
extras and dead code. The 23 UI files the freeze branch adds (`git diff --diff-filter=A 65e3f70a3 fc5c1e78f -- lib/ui`)
were read on 2026-11-01 for the freeze-tip rows (02 §3.5, S02-3); until `flutter-final` is tagged the source for those
rows is the read-only worktree `/home/eslam/Storage/Code/wt/oc_app-freeze`.

Inventory basis [R, counted today]: 143 Flutter classes named `*Screen`, `*Page` or `*View` (some are parts, not pages)
and 78 public `show*` sheet/dialog functions, plus private sheets inside the chat library.

---

## 1. Rules this section enforces, and where

| Rule (source) | How navigation enforces it |
|---|---|
| No bottom bar on phones; Conversations is home; gear for Settings; Files from inside the chat (memory `no-bottom-nav-decision`) | Compact windows have no nav chrome (§8.2). `ChatsRoute.Conversations` is the only home root. Files is a top-level destination only on medium and wider windows. On phones it opens from the chat's project chip, Search, `/files` and Ctrl+2. |
| Absent, never disabled (screens-nav §5.1, desktop-web-cross 21) | A route whose **platform** capability is off is not registered and has no door (§2.8). A route whose **server** capability is off opens the capability explainer sheet instead of a dead page. |
| Root never intercepts Back; drop "Press back again to exit" (ADR §9.4, D5) | `ChatsRoute.Conversations` and `ServersRoute.Servers` compose no back handler. Back at the root goes to the system, which plays back-to-home. |
| Chat pops only when nothing is lost (ADR §9.4) | The chat's `NavigationBackHandler` is enabled only while the composer differs from the saved draft or a photo is being recovered (§3.4). |
| Sheets and dialogs are screen state, not routes (ADR §9.4) | Local sheets live in the screen's `XUi`. Sheets that several features open are `SheetKey`s shown by the shell's sheet host (§2.3). Neither is a back-stack entry. |
| One sheet at a time; the sheet only chooses (screens-nav SCREENS-NAV-08, kit `no sheet on a sheet`) | Every `KitSheetHost`, local or shared, reports to the shell's `SheetPresence` (§2.3). Opening a sheet closes the open one first, and any navigator call while a sheet shows waits for that sheet's exit before acting (N2, §2.4). |
| Deep links only through `EntryRouter` (ADR §4 row 12) | Nav3's deep-link matcher is not used. `MainActivity` turns every intent into an `EntryRequest` (§7.1). |
| `EntryRouter` hands over state plus acknowledgement (03 S12) | `pending: StateFlow<EntryHandoff?>` + `consume(id)`; the shell maps targets to routes (§7.3). |
| Actions name their target; obvious options are visible rows (memory) | Every route that acts on a server, project or conversation carries that id, so the page title and actions can name it. Rows that open pages are visible rows, never only in a `⋯` menu (§10.6). |
| Steer, never queue (memory) | No navigation choice offers "queue". The only queue page is the offline queue move sheet (`SheetKey.QueuedPromptMove`). |
| Teach what is hidden (memory) | Every hidden gesture a route adds is registered as a tip (§10.3). |
| Kit only (AGENTS.md, ADR Konsist rule 1) | Feature `Entries.kt` files are the only place that imports `androidx.navigation3.runtime`. Features never write transition or pane metadata: each key carries plain `RouteMeta` data, and only `:ui:shell` turns it into Nav3 metadata with the kit's motion specs (§2.9). No raw `tween`/`spring`. |
| Accessibility (ADR §9.4, A11Y rules of the kit) | Page titles are announced on every route change, focus returns to the row that opened a page, and rail labels are always visible (§10.8). |
| Route keys never hold secrets or user text | Enforced by `RouteKeyContentTest` (§2.2.3). Pairing payloads, shared text and passwords travel through engines, never through a key. |

---

## 2. The navigation model

### 2.1 Where each piece lives

| Piece | Module / file | Owner |
|---|---|---|
| `AppRoute` sealed root, `RootRoute`, `TopLevelRoute`, `TopLevel` | `:ui:routes` `AppRoute.kt` | coordinator |
| One route file per feature (`ChatsRoutes.kt`, `ProjectRoutes.kt`, …), each key with its `meta` | `:ui:routes` | that feature's agent |
| `SheetKey` sealed root and shared sheet keys | `:ui:routes` `Sheets.kt` | coordinator (feature agents add rows through a PR to the coordinator) |
| `AppNavigator`, `NavOp`, `Arrivals` and arrival types, `RouteMeta`, `RouteMotion`, `Pane`, `RouteRequirement`, `EntryInstaller`, `SheetInstaller`, `SearchEntry`, `SearchExclusion`, `CapabilityFlow` | `:ui:routes` (`oc.kmp.library`, no Compose: all of these are plain data or non-composable interfaces; the two installers reuse Nav3's `EntryProviderScope` DSL, §2.3) | coordinator |
| `SerializersModule` (`subclassesOfSealed<AppRoute>()` and `<SheetKey>()`) | `:ui:routes` `RouteSerializers.kt` | coordinator |
| `ConversationRef`, `ConversationBackend`, `ProfileId`, `SessionId`, `ProjectDir`, `ProjectPath`, `PrefillAddress`, `RequestRef` value types; the shared id rule `SafeId` | `:core:model` | core owner (freeze item, ADR §10.3) |
| `ServerFlag` and `PlatformFlag` enums (the names `RouteRequirement` uses); `ServerCapabilities.has(ServerFlag)`, `PlatformCapabilities.has(PlatformFlag)` and `ProjectTools.available(capabilities)` | enums in `:core:model`; the evaluators in `:core:domain` | core owner |
| `SheetPresence` composition local (every `KitSheetHost` reports visible / exit finished) | `:ui:kit` (provided by `:ui:shell`) | kit owner |
| `App()`, `NavDisplay`, scenes, `AppNavigatorImpl`, `ArrivalsImpl`, `ShellSheetHost`, `RouteGate`, `withRouteMeta` (key meta → Nav3 transition and pane metadata), `EntryHandoffCollector`, `ShortcutDispatcher`, rail/sidebar frame, opening gate, safe mode | `:ui:shell` | coordinator |
| `EntryRouter`, `EntryRequest`, `EntryTarget`, `EntryHandoff`, `RootDecision`, link parsers, and `ShellWorld` (the one read model `:ui:shell` uses for bootstrap and gate state, saved and active profiles, per-profile capability flags, update and safe-mode state) | `:engine:entry` (engine 16 in ADR §6.2, so it may read `:engine:profiles`, `:engine:connection`, `:engine:diagnostics`, `:engine:settings` and `:core:data`'s `MigrationGate`) | coordinator |
| `SearchMatcher` (the one matcher for Settings search and the command launcher) | `:engine:settings` (pure; works on `(id, words)` pairs, never on route types) | settings agent |
| `EntryIntentReader` (intent → `EntryRequest`, strip extras) | `:android:platform` | platform agent |
| `MainActivity` intent capture, manifest filters | `:androidApp` | coordinator |
| Entry installer (`XEntries.kt`), sheet installer (`XSheets.kt`), search entries (`XSearch.kt`), capability flows (`XFlows.kt`) per feature, all as `@ContributesIntoSet` multibindings | `:feature:x` | feature agent |

`:ui:shell` reads world state only through `:engine:entry`'s `ShellWorld`, so its dependency list in ADR §6.1 stays as
written; the opening gate, `RootViewModel`, `RouteGuard`, `RouteGate`, `UpdateViewModel` and `SafeModeViewModel` all
take `ShellWorld`.

### 2.2 Route keys

#### 2.2.1 The root types (Kotlin, `:ui:routes`)

```kotlin
package io.github.eslamasabry.oc.ui.routes

/** Every page of the main app. Sealed across the files of this package (03 S17). */
@Serializable
sealed interface AppRoute : NavKey {
    /** Plain data, declared with a getter so it is never serialized. May depend on the key's own arguments
     *  (for example `Notifications(page = Background)` needs `BackgroundService`). The navigator reads it before a push
     *  (N7); only :ui:shell turns it into Nav3 metadata (§2.9). Abstract: a key without a decision does not compile. */
    val meta: RouteMeta
}

/** May sit at index 0 of the back stack. */
sealed interface RootRoute : AppRoute

/** A rail/sidebar destination. The rail highlights the nearest one from the top of the stack. */
sealed interface TopLevelRoute : AppRoute {
    val destination: TopLevel
}

enum class TopLevel { Conversations, Files, Settings }

data class RouteMeta(
    val motion: RouteMotion = RouteMotion.Page,     // the push class only; operation-based classes are §3.1
    val growsFromRow: Boolean = false,               // true only for ChatRoute.Chat
    val pane: Pane = Pane.Single,
    val requires: RouteRequirement = RouteRequirement.None,
) {
    companion object { val Page = RouteMeta() }
}

enum class RouteMotion { Page, FullScreenDialog }
enum class Pane { Single, List, Detail }
```

`ConversationRef` (`:core:model`, for the slice-0 freeze):

```kotlin
@Serializable
data class ConversationRef(
    val profileId: ProfileId,                 // always a SAVED profile id (for phone agents: the phone profile that owns them)
    val sessionId: SessionId,
    val backend: ConversationBackend = ConversationBackend.Main,
)

enum class ConversationBackend { Main, PhoneAgents }   // PhoneAgents = Claude Code, Pi or Codex through Paseo on this phone
```

**Phone agents have no saved profile of their own.** Flutter runs them on a virtual connection whose id is the phone
profile's id plus `.agents` (`agentBackendProfileSuffix`, `lib/state/connection.dart:245`;
`phone_agents_backend.dart:117`). That id is never in the saved list, and their notifications and widget rows carry the
**owner** phone profile id (`_alertProfileId`, `connection.dart:582`); Flutter finds the backend from the session
(`backendForConversation`, `phone_agents.dart:157`). Native keeps the saved id in the ref and names the backend
explicitly:

- `ConnectionRegistry.graphFor(ref)` (ADR §9.1) opens the main graph or, for `PhoneAgents`, the phone-agents graph whose
  internal connection id stays `<profileId>.agents` (storage keys and the deletion sweep keep the Flutter prefix rule).
- Every entry path that starts from (owner profile id, session id) — notifications, widget rows, pinned shortcuts,
  scheduled runs — first calls `ConnectionRegistry.resolveRef(ownerProfileId, sessionId)` (the port of
  `backendForConversation`; it also accepts a stored `<id>.agents` id and splits it) inside `EntryRouter`, so the ref
  built from a notification equals the ref of the row and N4 dedupes it.
- `RouteGuard` keeps `PhoneAgents` refs while their owner profile is saved and the platform has `BuiltinLinux`.

**One id rule** (`SafeId` in `:core:model`): `ProfileId` = `^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$` (the Flutter
`isSafeIdentifier` rule, `lib/domain/session_handoff.dart:120`; Flutter profile ids are microsecond timestamps, plus
the `.agents` suffix inside the engine); `SessionId` = `^[A-Za-z0-9][A-Za-z0-9._:-]{0,511}$` (OpenCode `ses_…`, Codex
UUIDs, Paseo ids). The edge validators keep their frozen Flutter rules — the link parser uses `isSafeIdentifier` for
both ids, the shortcut whitelist and the scheduled-run extras use their own regexes — and every one of them is a
**subset** of `SafeId`, which `SafeIdTest` asserts with the same sample strings, so an id an edge accepts always builds
a value class. The value classes are built only through `ProfileId.parse(raw): ProfileId?` / `SessionId.parse(raw)`;
`init` never throws on the entry path. `EntryRouter.decide` turns an id that fails the rule into `Drop(notice)` ("That link is not
complete"). The id rule the old map credited to "the Flutter /chat/<id> rule" does not exist in `onGenerateRoute`; the
`^[A-Za-z0-9_-]+$` check lives only in `global_sessions_screen.dart:526` and is subsumed by `SessionId`.

#### 2.2.2 Every route (85)

`P` = `ProfileId`, `D` = `ProjectDir` (absolute server path, value class), `F` = `ProjectPath` (path relative to the
project, value class), `R` = `ConversationRef`. `ProjectDir` and `ProjectPath` hold at most **1,024 UTF-8 bytes**; a
longer path (rare: Linux allows 4,096) is interned by `:engine:project`'s `PathTable` (memory plus
`files/native/paths/<profileId>.json`, swept with the profile) and the value class holds `~<id>` instead, so a key never
carries more than 1,024 bytes per path field. "Root" and "Top" mark `RootRoute` and `TopLevelRoute`. Pane is the
list-detail role (§8.3). "Push motion" is the key's static `RouteMotion`; the classes that depend on how a page was
opened (grow from row, replace, root, top level) are chosen by the navigator's operation (§3.1). Requirements are in
§2.8. `from: R?` marks a page opened **for a conversation**: two callers with different `from` are two different pages.

| # | File | Key | Arguments | Root / Top | Pane | Push motion | Feature module |
|---|---|---|---|---|---|---|---|
| 1 | `OnboardingRoutes.kt` | `OnboardingRoute.ManagedMove` | — | Root | single | Page | `:feature:onboarding` |
| 2 | `ChatsRoutes.kt` | `ChatsRoute.Conversations` | — | Root, Top(Conversations) | single | Page | `:feature:chats` |
| 3 | | `ChatsRoute.Inbox` | — | | single | Page | `:feature:chats` |
| 4 | | `ChatsRoute.NewConversation` | `profileId: P?`, `projectDir: D?` | | single | FullScreenDialog | `:feature:chats` |
| 5 | | `ChatsRoute.AllConversations` | `profileId: P`, `archived: Boolean` | | single | Page | `:feature:chats` |
| 6 | | `ChatsRoute.ImportConversation` | `profileId: P` | | single | Page | `:feature:chats` |
| 7 | | `ChatsRoute.ClaudeImport` | `profileId: P`, `projectDir: D` | | single | Page | `:feature:chats` |
| 8 | `ChatRoutes.kt` | `ChatRoute.Chat` | `ref: R` | | single | Page (`growsFromRow = true`) | `:feature:chat` |
| 9 | | `ChatRoute.Demo` | — | | single | Page | `:feature:chat` |
| 10 | `ComposerRoutes.kt` | `ComposerRoute.WebSources` | `ref: R` | | single | Page | `:feature:chat-composer` |
| 11 | `ChatPagesRoutes.kt` | `ChatPagesRoute.Context` | `ref: R` | | single | Page | `:feature:chat-pages` |
| 12 | | `ChatPagesRoute.ActiveContext` | `ref: R` | | single | Page | `:feature:chat-pages` |
| 13 | | `ChatPagesRoute.Relations` | `ref: R` | | single | Page | `:feature:chat-pages` |
| 14 | | `ChatPagesRoute.Note` | `ref: R` | | single | Page | `:feature:chat-pages` |
| 15 | | `ChatPagesRoute.Export` | `ref: R` | | single | Page | `:feature:chat-pages` |
| 16 | | `ChatPagesRoute.StagedRevert` | `ref: R`, `messageId: MessageId` | | single | Page | `:feature:chat-pages` |
| 17 | | `ChatPagesRoute.ShellOutput` | `ref: R`, `shellId: String` (≤ 128, server id) | | single | Page | `:feature:chat-pages` |
| 18 | `ProjectRoutes.kt` | `ProjectRoute.Files` | `profileId: P`, `projectDir: D?`, `from: R?` | Top(Files) | list | Page | `:feature:project` |
| 19 | | `ProjectRoute.Folder` | `profileId: P`, `projectDir: D`, `path: F`, `from: R?` | | list | Page | `:feature:project` |
| 20 | | `ProjectRoute.FileView` | `profileId: P`, `projectDir: D?`, `path: F`, `line: Int?`, `from: R?` | | detail | Page | `:feature:project` |
| 21 | | `ProjectRoute.Review` | `profileId: P`, `projectDir: D`, `scope: ReviewScope` (`Session(ref)`, `WorkingTree`, `Branch`), `from: R?` | | single | Page | `:feature:project` |
| 22 | | `ProjectRoute.Diff` | `profileId: P`, `source: DiffSource` (`TurnFiles(ref, messageId)`, `WorkingTreeFile(path: F)`, `RevertFile(ref, messageId, path: F)`), `from: R?` | | single | Page | `:feature:project` |
| 23 | | `ProjectRoute.Terminal` | `profileId: P`, `projectDir: D?` | | single | Page | `:feature:project` |
| 24 | | `ProjectRoute.DevPreview` | `profileId: P`, `secure: Boolean`, `port: Int`, `path: String` (starts `/`, ≤ 2,048), `from: R?` | | single | Page | `:feature:project` |
| 25 | | `ProjectRoute.Health` | `profileId: P`, `projectDir: D` | | single | Page | `:feature:project` |
| 26 | | `ProjectRoute.Worktrees` | `profileId: P`, `projectDir: D` | | single | Page | `:feature:project` |
| 27 | | `ProjectRoute.Services` | `profileId: P`, `projectDir: D` | | single | Page | `:feature:project` |
| 28 | | `ProjectRoute.Workspaces` | `profileId: P`, `projectDir: D` | | single | Page | `:feature:project` |
| 29 | | `ProjectRoute.Projects` | `profileId: P` | | single | Page | `:feature:project` |
| 30 | `ServersRoutes.kt` | `ServersRoute.Servers` | — | Root | single | Page | `:feature:servers` |
| 31 | | `ServersRoute.Editor` | `mode: EditorMode` (`Add(backend: Backend?, prefillAddress: PrefillAddress?, openCode2: Boolean)`, `Edit(profileId)`, `Credentials(profileId)`), `connectOnSave: Boolean`, `focusPassword: Boolean` | | single | Page | `:feature:servers` |
| 32 | | `ServersRoute.PairingScanner` | — | | single | FullScreenDialog | `:feature:servers` |
| 33 | | `ServersRoute.ServerPage` | `profileId: P` | | single | Page | `:feature:servers` |
| 34 | | `ServersRoute.ConnectionCheck` | `profileId: P` | | single | Page | `:feature:servers` |
| 35 | | `ServersRoute.ReachComputer` | — | | single | Page | `:feature:servers` |
| 36 | | `ServersRoute.HostSetup` | `profileId: P` | | single | Page | `:feature:servers` |
| 37 | | `ServersRoute.Capabilities` | `profileId: P` | | single | Page | `:feature:servers` |
| 38 | | `ServersRoute.Tailscale` | — | | single | Page | `:feature:servers` |
| 39 | `PhoneRoutes.kt` | `PhoneRoute.ThisPhone` | — | | single | Page | `:feature:phone` |
| 40 | | `PhoneRoute.SetupStart` | `addTools: Boolean` | | single | Page | `:feature:phone` |
| 41 | | `PhoneRoute.SetupProgress` | — | | single | Page | `:feature:phone` |
| 42 | | `PhoneRoute.SetupReady` | — | | single | Page | `:feature:phone` |
| 43 | | `PhoneRoute.Linux` | — | | single | Page | `:feature:phone` |
| 44 | | `PhoneRoute.Running` | — | | single | Page | `:feature:phone` |
| 45 | | `PhoneRoute.LinuxPorts` | — | | single | Page | `:feature:phone` |
| 46 | | `PhoneRoute.LinuxStorage` | — | | single | Page | `:feature:phone` |
| 47 | | `PhoneRoute.LinuxPackages` | — | | single | Page | `:feature:phone` |
| 48 | | `PhoneRoute.LinuxServices` | — | | single | Page | `:feature:phone` (D16) |
| 49 | | `PhoneRoute.LinuxService` | `name: String` (`[a-z][a-z0-9-]{1,23}`) | | single | Page | `:feature:phone` (D16) |
| 50 | | `PhoneRoute.LinuxServiceAdd` | — | | single | Page | `:feature:phone` (D16) |
| 51 | | `PhoneRoute.LinuxBackups` | — | | single | Page | `:feature:phone` (D16) |
| 52 | | `PhoneRoute.LocalTerminal` | — | | single | Page | `:feature:phone` |
| 53 | | `PhoneRoute.KeepRunning` | — | | single | Page | `:feature:phone` |
| 54 | | `PhoneRoute.TermuxMigration` | — | | single | Page | `:feature:phone` (D7) |
| 55 | `AgentsRoutes.kt` | `AgentsRoute.Hub` | — | | single | Page | `:feature:agents` |
| 56 | | `AgentsRoute.SignInTerminal` | `agentId: String` (catalog id) | | single | Page | `:feature:agents` |
| 57 | | `AgentsRoute.Account` | `profileId: P` | | single | Page | `:feature:agents` |
| 58 | `SettingsRoutes.kt` | `SettingsRoute.Hub` | — | Top(Settings) | list | Page | `:feature:settings` |
| 59 | | `SettingsRoute.Group` | `group: SettingsGroupId` (`Server`, `Agent`, `Conversations`, `ThisApp`, `Help`) | | detail | Page | `:feature:settings` |
| 60 | | `SettingsRoute.ByServer` | `kind: ByServerKind` | | detail | Page | `:feature:settings` |
| 61 | | `SettingsRoute.Appearance` | — | | detail | Page | `:feature:settings` |
| 62 | | `SettingsRoute.Notifications` | `page: NotificationsPage` (`All`, `Notifications`, `Background`), `section: NotificationsSection?` | | detail | Page | `:feature:settings` |
| 63 | | `SettingsRoute.Privacy` | — | | detail | Page | `:feature:settings` |
| 64 | | `SettingsRoute.Voice` | — | | detail | Page | `:feature:settings` |
| 65 | | `SettingsRoute.PermissionsAutomation` | — | | detail | Page | `:feature:settings` |
| 66 | | `SettingsRoute.SavedPermissions` | `profileId: P`, `projectDir: D?` | | detail | Page | `:feature:settings` |
| 67 | | `SettingsRoute.AiSetup` | `profileId: P` | | detail | Page | `:feature:settings` |
| 68 | | `SettingsRoute.Providers` | `profileId: P`, `connectProviderId: String?` | | detail | Page | `:feature:settings` |
| 69 | | `SettingsRoute.Tools` | `profileId: P`, `section: ToolsSection` (`Mcp`, `Commands`, `Tools`, `Skills`, `References`), `from: R?` | | detail | Page | `:feature:settings` |
| 70 | | `SettingsRoute.McpCatalog` | `profileId: P` | | detail | Page | `:feature:settings` |
| 71 | | `SettingsRoute.McpSetup` | `profileId: P` | | detail | Page | `:feature:settings` |
| 72 | `UsageRoutes.kt` | `UsageRoute.Hub` | `section: UsageSection` (`Plans`, `Spending`) | | single | Page | `:feature:usage` |
| 73 | | `UsageRoute.Subscription` | `key: String` (usage source key, ≤ 128) | | single | Page | `:feature:usage` |
| 74 | | `UsageRoute.Add` | `profileId: P?` | | single | Page | `:feature:usage` |
| 75 | | `UsageRoute.ReadFrom` | `profileId: P?`, `providerId: String?` | | single | Page | `:feature:usage` |
| 76 | | `UsageRoute.Alerts` | — | | single | Page | `:feature:usage` |
| 77 | | `UsageRoute.WidgetSettings` | — | | single | Page | `:feature:usage` |
| 78 | `SchedulesRoutes.kt` | `SchedulesRoute.List` | `profileId: P`, `projectDir: D`, `from: R?` | | single | Page | `:feature:schedules` |
| 79 | `AboutRoutes.kt` | `AboutRoute.ReportProblem` | `problemId: Long?`, `jobId: String?` | | single | Page | `:feature:about` |
| 80 | | `AboutRoute.Kept` | — | | single | Page | `:feature:about` |
| 81 | | `AboutRoute.About` | — | | single | Page | `:feature:about` |
| 82 | | `AboutRoute.Notices` | — | | single | Page | `:feature:about` |
| 83 | | `AboutRoute.Guide` | — | | single | Page | `:feature:about` |
| 84 | | `AboutRoute.Tips` | — | | single | Page | `:feature:about` |
| 85 | `SettingsRoutes.kt` | `SettingsRoute.Updates` | — | | detail | Page | `:feature:settings` (freeze tip, 02 §3.5) |

Outside the main back stack:

| Host activity | Keys | Owner |
|---|---|---|
| `ManageSpaceActivity` (own small `NavDisplay`, no connections, no secrets; SCREENS-NAV-101) | `ManageSpaceRoute.Home`, `ManageSpaceRoute.ExportProjects` | `:feature:settings` |
| `WidgetOverlayActivity` (no back stack; one panel per launch; SCREENS-NAV-100), reached only through the two frozen trampolines `WidgetConfigureActivity` (`APPWIDGET_CONFIGURE`, widget Edit) and `WidgetRouteActivity` (steer / voice buttons) | `WidgetPanel.Style(widgetId)`, `WidgetPanel.Project(widgetId)`, `WidgetPanel.Steer(widgetId, voice: Boolean)` | `:feature:widget-panels` |

**Both hosts can be the first native screen after the update**, before `MainActivity` has ever run: Manage space from
App info › Storage, a panel from a widget button. Section 05 §7.1 makes receivers, workers, tiles and services await the
`MigrationGate`; these activities do the same on the UI side:

- Each draws the kit "Updating your app…" state (the same part as the opening gate, §5 A01) until `MigrationGate` is
  open, and only then builds its `NavDisplay` or panel. The widget panels read display preferences that do not exist
  until migration step M1 has run.
- Manage space's **Delete everything** first asks `MigrationRunner` to stop (or waits for the step in flight to finish;
  every step is idempotent, 05 §7.10), then wipes, including the migration journal and the Flutter backup, so the runner
  can never write migrated profiles or secrets back after the person deleted them. The confirm says so when a migration
  was still running ("The app was still moving your data from the old version; this deletes that too").
- E2E-39 gains a step: install the native build over Flutter 2.1.0+57 and, before opening the app, open App info ›
  Storage › Manage space; see "Updating your app…", then the Manage space page with the migrated sizes.

`TeamRoute.*` and `FactsBookRoute.*` are not created until those features are scheduled (ADR §12 D6). No placeholder
keys exist before then.

#### 2.2.3 Rules for every route key

Rules 1–4 are checked in `commonTest` of `:ui:routes`; rule 6 in `:ui:shell` `androidUnitTest` (Robolectric).

1. `@Serializable`, a `data class` or `data object`, inside its feature's sealed interface in `:ui:routes`, with its
   `meta` declared as a getter. `RouteRoundTripTest` serializes one sample of every subclass (from the sealed
   serializer descriptor) through the app `SerializersModule`, reads it back, and checks `meta` is not in the output.
2. **Fields are ids, enums, booleans, small ints, relative or project paths, and static anchor ids.** Never: user-typed
   text, message text, shared text, passwords, tokens, pairing payloads, error text, URLs with credentials, or a host
   name for the dev preview. `RouteKeyContentTest` fails on any field whose name contains `text`, `body`, `message`
   (except `messageId`), `password`, `token`, `secret`, `payload`, `error`, `url`, `host`. Allowlist: `prefillAddress` on
   `ServersRoute.Editor.Add`, typed `PrefillAddress` (`:core:model`): its only constructor `PrefillAddress.parse(raw)`
   keeps scheme, host and port and **drops user info, path, query and fragment before the value exists**, so a pasted or
   linked `https://user:pass@host` can never reach saved state. The test also fails on any `PrefillAddress` sample whose
   text contains `@`, `?` or `#`.
3. **Equality means "the same page", and no key appears twice in the stack.** `open` or `openChat` for a key that is
   already **on top** only posts the arrival (N4, N5). For a key that is **deeper** in the stack, the navigator calls
   `popTo(key)` and then posts the arrival (N10). This keeps Nav3's rule that every entry's `contentKey` (by default
   `key.toString()`) is unique: the saveable-state and `ViewModelStore` decorators key on it, so two equal keys would
   share one `ChatViewModel`, one composer and one scroll, and a scene that composed both would crash [R review of Nav3
   1.2.0 `NavDisplay`; P2 confirms]. Two different pages must never have equal keys (hence `from` in §2.2.2).
4. **Arrival details are not in the key.** Focus the composer, land on a request, run a menu action, highlight a row: these
   go through `Arrivals` (§2.5), so re-opening the same page keeps its ViewModel and its scroll.
5. **A key must still render.** `RouteGuard` (§2.6) removes keys whose profile no longer exists or whose platform
   capability is off, both after a restore and live, whenever the profile store changes. Every other key renders its
   own loading or "not available on <server>" state.
6. **Size budget.** The shell saves at most the root plus the **top 20** entries (the live stack is untouched; after a
   restore the middle is gone, and Back from the 21st entry goes to the root), and a run of consecutive `Folder`
   entries is capped at 8 live (the 9th push drops the oldest `Folder` of the run; breadcrumbs still show and open the
   full path). `BackStackSizeTest` (Robolectric, `Parcel.dataSize()` of the real saved `Bundle`) asserts: 21 maximal
   keys (two 1,024-byte path fields each, plus a `ConversationRef` with a 512-char session id) stay under **128 KiB**,
   and a realistic 20-entry stack (Conversations, Chat, Files, 8 folders, FileView, Settings pages) stays under
   **16 KiB**. Android's binder buffer is about 1 MB per process and shared, so both numbers leave room.

### 2.3 Shared sheets (26)

A sheet that only one screen opens is that screen's state (`XUi.sheet`) and is drawn with the kit's `KitSheet`. A sheet
that **several features** open, or that the shell or `EntryRouter` opens, is a `SheetKey`. Features cannot depend on
each other (ADR §6.1), so the owning feature contributes a `SheetInstaller` and every caller uses
`AppNavigator.showSheet(key)`. The shell's `ShellSheetHost` renders it through the kit's in-window M3 `BottomSheet`
host, which owns predictive back while visible (ADR §9.4). A `SheetKey` is **not a route**: it is never a back-stack
entry and is not saved; ADR §9.4 ("sheets are screen state") and 03 §15.7 still hold, the state just belongs to the
shell instead of one screen (§16 records the 03 amendment).

`SheetInstaller` keeps `:ui:routes` free of Compose by reusing Nav3's entry DSL as a typed key → content registry,
exactly like `EntryInstaller` (the interface method is not composable; the composable lambdas live in the feature):

```kotlin
// :ui:routes
public fun interface SheetInstaller {
    public fun EntryProviderScope<SheetKey>.install()        // feature: entry<SheetKey.Git> { key -> GitSheet(...) }
}
// :ui:shell builds entryProvider<SheetKey> { sheetInstallers.forEach { with(it) { install() } } } once and draws
// provider(key).Content() inside KitSheetHost. [U: NavEntry.Content() outside NavDisplay — slice-1 shell spike, with
// 03 §15.4's identical assumption. Fallback: :ui:routes becomes oc.kmp.compose (ADR §5.2 amendment) and the installer
// returns a @Composable (SheetKey) -> Unit map.]
```

**`SheetPresence`** (`:ui:kit`, provided by the shell). Every `KitSheetHost` — the shell's and every screen's local
one — reports `shown(id)` when it starts to show and `hidden(id)` when its exit animation has finished. N2 (§2.4) waits
on it, so "close the local sheet, then open `SheetKey.OpenProject`" (B03) and any `open()` called from a local sheet's
row are sequenced the same way as shared sheets, and the kit's "no sheet on a sheet" rule holds for both kinds. The kit
sheet API also gets `onHidden`, for screens that want to act after their own sheet has gone.

```kotlin
@Serializable
sealed interface SheetKey {
    @Serializable data object ServerSwitcher : SheetKey
    @Serializable data class ConnectionDetails(val profileId: ProfileId) : SheetKey
    @Serializable data class SwitchServer(val profileId: ProfileId, val reason: SwitchReason) : SheetKey
    @Serializable data class QueuedPromptMove(val fromProfileId: ProfileId) : SheetKey
    @Serializable data class SessionAddress(val linkTicket: Long) : SheetKey      // the parsed link stays in :engine:entry
    @Serializable data object CommandLauncher : SheetKey
    @Serializable data object ShortcutsHelp : SheetKey
    @Serializable data class ModelPicker(val scope: ModelScope) : SheetKey      // Conversation(ref) | NewConversations(profileId) | NewConversationDraft
    @Serializable data class RunCommand(val profileId: ProfileId, val commandId: String, val ref: ConversationRef?) : SheetKey
    @Serializable data class Permission(val request: RequestRef) : SheetKey
    @Serializable data class Question(val request: RequestRef) : SheetKey
    @Serializable data class Form(val request: RequestRef) : SheetKey
    @Serializable data class Approvals(val ref: ConversationRef) : SheetKey
    @Serializable data class ContinueWith(val ref: ConversationRef) : SheetKey
    @Serializable data class ContinueOnComputer(val ref: ConversationRef) : SheetKey
    @Serializable data class ContinueOnPhone(val ref: ConversationRef) : SheetKey
    @Serializable data class SessionDestination(val ref: ConversationRef, val mode: DestinationMode) : SheetKey // Move | Warp | Organization
    @Serializable data class Schedule(val scheduleId: String?, val ref: ConversationRef?) : SheetKey
    @Serializable data class Git(val profileId: ProfileId, val projectDir: ProjectDir) : SheetKey
    @Serializable data class OpenProject(val profileId: ProfileId, val purpose: OpenProjectPurpose) : SheetKey
    @Serializable data class Agent(val agentId: String?, val step: AgentStep, val signInAgain: Boolean) : SheetKey
    @Serializable data object VoiceModelSetup : SheetKey
    @Serializable data object PrivacyPolicy : SheetKey
    @Serializable data object AppUpdate : SheetKey                                    // Update now / What's new link / Go to Settings
    @Serializable data class WhatsNew(val startStep: Int = 0) : SheetKey              // freeze tip: the walkthrough
    @Serializable data class CapabilityExplainer(val flow: EnableFlowId, val host: HostKind?) : SheetKey
}
```

| Sheet | Owner module | Opened from | Flutter source |
|---|---|---|---|
| `ServerSwitcher` | `:feature:servers` | the server pill (Conversations top bar on compact and medium, sidebar header on expanded) | `server_switcher_sheet.dart` (SCREENS-NAV-08) |
| `ConnectionDetails` | `:feature:servers` | status line "Details" on any page; Servers row | `connection_status_banner.dart` (09) |
| `SwitchServer` | `:feature:servers` | `EntryRouter` (monitored request, other-server link) | `openMonitoredRequest` dialog (18, 36) |
| `QueuedPromptMove` | `:feature:servers` | Servers row; chat notice "waiting for other servers" | `queued_prompt_move_sheet.dart` (77) |
| `SessionAddress` | `:feature:servers` | `EntryRouter` (address links, gated by `sessionAddressHandoff`) | `session_address_sheets.dart` (16) |
| `CommandLauncher` | `:feature:search` | Search button, Ctrl+K, `/help` | `showCommandPalette` (10) |
| `ShortcutsHelp` | `:feature:search` | Ctrl+/, About, Settings › Help row (keyboard seen) | `showShortcutsHelp` |
| `ModelPicker` | `:feature:chat-composer` | model chip, + sheet row, `/models`, Settings › Agent › Model & mode, New conversation, enable flow `modelSignIn` fallback | `showModelPicker` (62) |
| `RunCommand` | `:feature:chat-composer` | server command pick in chat; Settings › Tools › Commands | `run_command_dialog.dart` (48) |
| `Permission`, `Question`, `Form` | `:feature:chat` | chat request card Details; Conversations row card; Inbox row | `permission_sheet.dart`, `question_sheet.dart`, `form_renderer.dart` (46) |
| `Approvals` | `:feature:chat-pages` | composer approval chip; session menu; `/approvals` | `approvals_sheet.dart` (56) |
| `ContinueWith`, `ContinueOnComputer`, `ContinueOnPhone` | `:feature:chat-pages` | session menu; handoff link line; provider failure | `continue_with_sheet.dart`, `session_handoff_sheets.dart` (59) |
| `SessionDestination` | `:feature:chat-pages` | session menu; `/move`, `/warp`, `/org` | `session_destination_sheet.dart` (55) |
| `Schedule` | `:feature:schedules` | schedules list; session menu "Scheduled prompt…"; long-press Send | `schedule_sheet.dart` (98) |
| `Git` | `:feature:project` | chat branch chip; Files hub bar; Go to | `git_sheet.dart` (66) |
| `OpenProject` | `:feature:project` | Conversations project filter "Open a project…"; New conversation; Files with no project; Projects | `folder_browser.dart`, `remote_folder_picker.dart` (69) |
| `Agent` | `:feature:agents` | Agents hub; New conversation agent cards; agent notices on Conversations | `agent_sheet.dart` (84) |
| `VoiceModelSetup` | `:feature:settings` | chat mic "Choose another model"; Settings › Voice; enable flow | `showVoiceModelSetupSheet` |
| `PrivacyPolicy` | `:feature:about` | Settings › Privacy; About | `showPrivacyPolicy` |
| `AppUpdate` | `:feature:about` | update status line; **a gear tap (or the rail's Settings) while an update waits** opens this sheet first — "Update now", a "What's new" link (external release page through `openExternalLink`), or go on to Settings (§8.2; memory `app-updates-two-layers`) | `ShorebirdUpdateNotice` successor; `kit_update_sheet.dart` and `home_screen.dart` `_openSettingsPage` on the freeze branch |
| `WhatsNew` | `:feature:about` | once after an update (`:engine:settings` remembers the version shown); About › What's new; Help › Tips | `whats_new_sheet.dart`, `whats_new_places.dart` (freeze tip). One step per new thing; "Show me" closes the sheet, then `open(target)` + `HighlightArrival(anchor)` (`updates-check-now` on `SettingsRoute.Updates`, `agents-hub-list` on `AgentsRoute.Hub`, the Appearance anchor on `SettingsRoute.Appearance`; composer anchors only once a chat is open, as on the freeze branch); the last step offers Share |
| `CapabilityExplainer` | `:ui:shell` (kit part + shell VM) | any gated door (§2.8, §7.12) | `KitCapabilityExplainer` (20) |

Sheet rules:

- **Results go through engines, not callbacks.** A model choice is written to `:engine:composer`/`:engine:settings`; a
  chosen project to `:engine:project`; a schedule to `:engine:schedule`. The caller's ViewModel observes engine state.
  This survives process death and needs no result bus.
- **Each shared sheet gets its own ViewModel**, created in a child `ViewModelStore` that the shell clears when the sheet
  finishes hiding [U: exact Metro/lifecycle wiring, slice-1 shell spike].
- **Typed input survives dismissal** through `KitDraft` (`oc.draft.<target>.<profileId>`, kit X2). A sheet with a
  multiline field must declare a draft target (kit gate G48).

### 2.4 `AppNavigator` (the only navigation API ViewModels see)

```kotlin
interface AppNavigator {
    /** The top key; for ViewModels that must know whether they are on top (03 §15.3). */
    val top: StateFlow<AppRoute?>

    /** Push [route]. On top already: arrival only (N5). Deeper: popTo it (N10). Gated routes: §2.8. */
    fun open(route: AppRoute)

    /** Open a conversation. [fromOrigin] = the caller is a row that registered a grow-from-row origin (§3.1).
     *  When Chat(ref) is already on top, only posts [arrival] (no second copy, c5842e836); deeper: popTo (N10). */
    fun openChat(ref: ConversationRef, arrival: ChatArrival = ChatArrival(), fromOrigin: Boolean = false)

    /** Replace the top entry (new conversation → its chat; import → the imported chat; terminal ↔ local terminal). */
    fun replaceTop(route: AppRoute)

    /** Clear the whole stack to [root] (null = whatever RootDecision says now), then push [then] if given.
     *  Flutter's pushNamedAndRemoveUntil('/servers' | '/', (_) => false). Runs N3 for every removed entry. */
    fun resetTo(root: RootRoute? = null, then: AppRoute? = null)

    /** resetTo(ChatsRoute.Conversations, then). Flutter's pushNamedAndRemoveUntil('/home', (_) => false). */
    fun land(then: AppRoute? = null)

    /** Replace only index 0 when the root KIND changes (RootViewModel only). Pages above stay unless N12 removes them. */
    fun replaceRoot(root: RootRoute)

    /** Rail, sidebar, Ctrl+1..3: reselect = pop to that destination; else pop to root and push it. */
    fun selectDestination(destination: TopLevel)

    /** Same path as the system Back: runs the top page's handler first (Ctrl+W, a page's own Close). */
    fun back()

    /** Pop until the top equals [route]; false when it is not in the stack. */
    fun popTo(route: AppRoute, inclusive: Boolean = false): Boolean

    fun showSheet(key: SheetKey)
    fun closeSheet()

    val arrivals: Arrivals
}

/** The last change, held in snapshot state by AppNavigatorImpl; the shell's transition specs read it (§3.1). */
sealed interface NavOp {
    data object Push : NavOp
    data class OpenChat(val fromOrigin: Boolean) : NavOp
    data object ReplaceTop : NavOp
    data object Reset : NavOp              // resetTo, land, replaceRoot
    data object SelectDestination : NavOp
    data class Pop(val gesture: Boolean) : NavOp   // back, popTo, the system Back
}
```

This changes the ADR §9.4 list (`open`, `openChat(ref, focusComposer)`, `land`, `replaceRoot`, `back`): `openChat`
takes a `ChatArrival` and `fromOrigin` instead of `focusComposer`, `land` takes `then`, `replaceRoot` replaces only
index 0, and `top`, `replaceTop`, `resetTo`, `selectDestination`, `popTo`, `showSheet`, `closeSheet` and `arrivals`
are added. §16 records this as an ADR §9.4 amendment; it is a freeze item for the coordinator at the end of slice 0
(ADR §10.3).

**Who clears the whole stack** (every Flutter `pushNamedAndRemoveUntil` call site, read 2026-11-01):

| Flutter call site | Native call |
|---|---|
| Connect or save succeeded from Servers or the editor (`servers_actions.dart:235`, `:310`) | `land()` |
| Setup ready with a project (`phone_setup_ready_screen.dart:168`, `:222`), This phone actions (`this_phone_screen.dart:552`, `:768`), Termux move done (`termux_migration_screen.dart:781`), `addToolClaude` connected (`capability_flows.dart:116`), links and alerts that land (`link_routes.dart:83/226`, `alerts_and_shortcuts.dart:123`), local agent connected (`inside_pages.dart:476`, now inside the Agents hub, M3) | `land()` or `land(then)` |
| A chat with nothing beneath it leaves for home (`chat_drafts.dart:383`) | not needed: index 0 is always a `RootRoute`, so a chat always has Conversations (or Servers) under it |
| Switcher **Leave** or **Disconnect**, and the active phone server removed (`home_screen.dart:652`, `:658`) | `resetTo(ServersRoute.Servers)` |
| Server page **Disconnect** (`server_page.dart:192`) | `resetTo(ServersRoute.Servers)` |
| Connection card **Change server**, **Update password**, **Update token**, **Review certificate**, and the opening state's server line (`root.dart:530–541`, `:597`) | `resetTo(ServersRoute.Servers)` + `ServersArrival(Edit(profileId, focusPassword = true))` for the password and token, `ServersArrival(Edit(profileId))` for the certificate, no arrival for Change server |
| Setup start's phone-server paths (`phone_setup_start_screen.dart:395`) | `resetTo(ServersRoute.Servers)` + `ServersArrival(Connect(profileId, detectedRunning = true))` or `ServersArrival(EnterPhoneCredentials(null, openCode2 = true))` |
| Setup ready, **Leave** without a project (`phone_setup_ready_screen.dart:229`, route `'/'`) | `resetTo()` (RootDecision picks the root) |

Behaviour every implementation must have (unit-tested in `:ui:shell` `commonTest` over a plain `SnapshotStateList`):

| # | Rule | Why / source |
|---|---|---|
| N1 | All calls run on the main dispatcher. A call from a ViewModel coroutine is re-posted with `AppDispatchers.main`. | Nav3's back stack is snapshot state. |
| N2 | While any sheet is visible — shared or local, as `SheetPresence` reports (§2.3) — `open`, `openChat`, `replaceTop`, `resetTo`, `land`, `replaceRoot` and `selectDestination` first close the sheet (shared: the shell closes it; local: `SheetPresence.requestClose()` asks its host), wait for its exit to finish (at most one kit `standard` duration, 250 ms, then act anyway), then act. | "The sheet only chooses; the shell acts after it closed" (SCREENS-NAV-08); kit "no sheet on a sheet". |
| N3 | Entries removed by `resetTo`, `land`, `selectDestination`, `popTo` or `replaceTop` run the same leave path as Back: draft flush, "discard the untouched new conversation" after the page is gone. | f6c005423. |
| N4 | `openChat` with the same `ref` on top posts the arrival only. A widget Reply on the open chat therefore focuses the composer in place (abdf9e0fe). | B-NAV-005. |
| N5 | `open` with the top key equal posts the arrival only (notification for the page already shown, `phone_setup` while progress shows). | SCREENS-NAV-14. |
| N6 | `replaceRoot` is a no-op when index 0 already equals the root. | Avoids recreating Conversations on every connection change. |
| N7 | Before any push the navigator evaluates `route.meta.requires` through `RouteGate` (§2.8): failed → not pushed; "absent" or the explainer sheet. | Absent, never disabled. |
| N8 | Every change is reported once to `PerfTrace` (`nav.<from>-><to>` span) and to the diagnostics ring as route **class names only**, never arguments. | Keeps the Flutter `PerfTraceNavigatorObserver` marks; privacy. |
| N9 | `back()` with only the root left does nothing. Leaving the app is the system's own Back (gesture or button), which plays back-to-home. | Root never intercepts Back. |
| N10 | **No key twice.** `open`/`openChat` for a key already deeper in the stack calls `popTo(key)` (running N3 for what it removes) and then posts the arrival. Example: Chat(A) → Relations(A) → Chat(C) → Relations(C) → "parent" returns to the existing Chat(A) instead of stacking a second one. Flutter pushed a duplicate here; native cannot (Nav3 `contentKey` uniqueness, §2.2.3 rule 3). | One ViewModel and one composer per conversation. |
| N11 | `resetTo` clears every entry above and including index 0, puts [root] (or `RootDecision`'s root), then pushes [then]. It is the only call that may change the root kind outside `RootViewModel`. | The Flutter call sites in the table above. |
| N12 | **Live profile removal.** On every profile-store change the shell runs the `RouteGuard` filter on the live stack: entries whose `profileId` (or `ref.profileId`, or `from.profileId`) is no longer saved are removed (N3 runs for them). If that removal changes `RootDecision`'s root kind, the shell calls `resetTo()` instead of `replaceRoot`, so no second copy of the new root and no page of a gone profile can stay. Forgetting the last server from [Conversations, Settings.Hub, Servers, ServerPage(P), Chat(P,…)] leaves [Servers]; forgetting one of two servers leaves the stack minus P's pages. | Flutter's `_Root` left pushed pages of a gone profile in place. |
| N13 | Every call records its `NavOp` in snapshot state before changing the stack (`OpenChat(fromOrigin)` also sends the kit gate's `TapOpen` event, 08 §5.4). | §3.1 picks the motion from it. |

### 2.5 Arrivals: one-time details as acknowledged state

```kotlin
interface Arrivals {
    fun pending(route: AppRoute): StateFlow<Arrival?>   // keyed by route equality
    fun acknowledge(route: AppRoute, arrivalId: Long)
}

sealed interface Arrival { val id: Long }

@Serializable
data class ChatArrival(
    val focusComposer: Boolean = false,
    val landOn: Landing? = null,                 // Landing.Request(requestId) | Landing.OldestRequest | Landing.Failure
    val menuAction: SessionMenuAction? = null,   // run once after the first history page arrives
    val opened: OpenedAs = OpenedAs.Existing,    // Existing | NewlyCreated(discardIfUntouched = true) | FirstRun
    override val id: Long = 0,                   // assigned by the navigator
) : Arrival

data class ConversationsArrival(val filter: FeedFilter, override val id: Long = 0) : Arrival // None | Running | NeedsYou
data class HighlightArrival(val anchorId: String, override val id: Long = 0) : Arrival      // settings search, B-NAV-073, What's new
data class ServersArrival(val request: ServersRequest, override val id: Long = 0) : Arrival
data class FilesArrival(val focusSearch: Boolean, val openFiles: Boolean, override val id: Long = 0) : Arrival

/** Ported from Flutter's ServersRouteRequest (servers_screen.dart:127–187), all five kinds. */
sealed interface ServersRequest {
    data class Connect(val profileId: ProfileId, val detectedRunning: Boolean = false) : ServersRequest
    data class Add(val backend: Backend? = null, val prefillAddress: PrefillAddress? = null) : ServersRequest
    /** The phone's own server: restore the password the app wrote, connect; editor only if that is impossible. */
    data class EnterPhoneCredentials(val profileId: ProfileId?, val openCode2: Boolean) : ServersRequest
    /** Flutter's ServersRouteRequest.edit and 'edit-active' (the latter = Edit(active, focusPassword = true)). */
    data class Edit(val profileId: ProfileId, val focusPassword: Boolean = false) : ServersRequest
    data class Forget(val profileId: ProfileId) : ServersRequest
}
```

- This is the ADR's "state field the UI acknowledges", not an event channel (ADR §9.1).
- `ChatArrival` maps Flutter's `ChatRouteArguments` one to one: `discardIfUntouched` → `OpenedAs.NewlyCreated`,
  `focusComposer`, `landOnRequestID`, `landOnFailure`, `menuAction`, `firstRun()` → `OpenedAs.FirstRun` (new +
  keyboard up) [R SCREENS-NAV-04].
- **`EnterPhoneCredentials` keeps the restore-first rule** (`servers_actions.dart:166–205`): the Servers ViewModel asks
  `:engine:runtime` for the phone server's password that the app itself wrote (`TermuxBridge.managedServerPassword()`
  today); when it exists and was not already refused, it saves the profile (creating "This phone" if none exists) and
  connects (`detectedRunning = true`). Only when that is impossible does it open
  `ServersRoute.Editor(Add(prefillAddress = phone URL, openCode2) or Edit(profileId), connectOnSave = true,
  focusPassword = true)`. Callers: the switcher's phone rows, `PhoneRoute.SetupStart` (via `resetTo`, table above), the
  phone server card, and the connection card's Update password for the phone server. No one is asked to type a password
  the app generated.
- `Edit(profileId, focusPassword = true)` is Flutter's `'edit-active'`: the connection banner's Update password /
  Update token and AI setup open it.
- `NeedsYou` in `ConversationsArrival` makes the Conversations screen open the Inbox above itself after its first frame
  (the Flutter behaviour: `home_screen.dart:134` and `test/home_navigation_test.dart:752` "Conversations under it, the
  Inbox over it"). Catalog row B-NAV-025 and E2E-21 step 3 say the opposite ("opens Chats with its filter applied");
  they are amended in §16, and tests follow the code.
- Arrivals are not saved across process death. A lost arrival only loses a one-time focus or highlight, **with one
  exception handled outside arrivals**: "discard this conversation if it is still untouched" is a property of the
  session, not of the page. `:engine:composer` records the session id in `oc.pendingDiscard.<profileId>` when the app
  creates it for `OpenedAs.NewlyCreated` and clears it on the first typed character, attachment or send. The chat's
  leave path (N3) checks that record, so a restored, still-empty new conversation is still discarded after process
  death, and the record is swept with the profile.

### 2.6 Back-stack shape, root decision and process death

**Allowed shapes.** Index 0 is always a `RootRoute`: `ChatsRoute.Conversations`, `ServersRoute.Servers` or
`OnboardingRoute.ManagedMove`. Everything else sits above it, each key at most once (N10). The live stack has no depth
cap except the `Folder` run cap; the saved copy keeps the root plus the top 20 (§2.2.3 rule 6); `land()` and
`resetTo()` reset.

**First stack.** The shell shows the opening gate (§5 A01) until `BootstrapState.Ready` and `MigrationGate` is open. It
then builds the first stack from, in order: (1) the restored stack, if the activity was restored and `RouteGuard`
keeps at least the root; (2) otherwise `RootDecision.decide(world)`:

| Condition, checked in this order (SCREENS-NAV-02; Flutter `_Root.build`, `root.dart:443`) | Root |
|---|---|
| **No active profile** (`conn.profile == null`). This is also the state after Leave or Disconnect: `_disconnect` writes the active profile to null and keeps the saved list (`connect.dart:808`) | `ServersRoute.Servers`: the welcome when nothing is saved, otherwise the saved list |
| The active in-app profile needs the OpenCode 1 → 2 move (`requiresOpenCode2Migration` / `openCode2HistoryPending`) | `OnboardingRoute.ManagedMove` |
| The active profile needs password, Codex token or certificate re-entry | `ServersRoute.Servers` + `ServersArrival(Edit(profileId, focusPassword = true))`, which opens `ServersRoute.Editor(Credentials(profileId), focusPassword = true)` |
| Anything else (connected, connecting, starting, not answering) | `ChatsRoute.Conversations`; connecting shows its opening state with last-known rows, so nothing moves when the server answers (SCREENS-NAV-03, jitter J2) |

`ShellWorld` (§2.1) exposes `activeProfileId` and `savedProfileIds` separately; `RootDecision` never uses "is anything
saved" for row 1. §7.3 rule 4 uses the same "no active profile" condition.

**Later changes.** A shell-level `RootViewModel` re-runs `RootDecision` on every profile-store and connection change and
calls `replaceRoot` only when the root **kind** changes for a reason other than a profile removal (for example the
active profile now needs re-entry). Pages above stay, exactly like Flutter's `_Root` route under pushed pages. A profile
removal goes through N12 instead (filter, then `resetTo` if the root kind changes), and the explicit leave paths call
`resetTo` themselves (§2.4 table).

**`RouteGuard`** (after restore, and live through N12). Removes every key whose `profileId`, `ref.profileId` or
`from.profileId` is no longer saved (a `PhoneAgents` ref counts as saved while its owner profile is saved and the
platform has `BuiltinLinux`), and every key whose platform requirement is off (a desktop restoring an Android-only key).
If index 0 is removed, it is rebuilt by `RootDecision`. Server-capability checks are not done here: the server may not be
connected yet, so each page shows its own "not available on <server>" state with "Switch server" (Flutter
`_projectWentAway`, STATE-12).

**Side duties of Flutter's `_Root`** (setup finishers, Linux reset resume, app-exit recovery, thermal guard, reply watch)
are not navigation. They start from `AppStartup` in `:engine:entry` after the gate opens and report through status
lines (ADR §9.7), with one exception that navigates: when the Linux reset left unfinished resumes on start
(`root.dart:144–157`, `_finishLinuxReset` → `openPhoneSetupProgress`), `AppStartup` submits
`EntryRequest.Internal(EntryTarget.PhoneSetup(Progress))` to the router, so setup progress opens over the root once,
like any other entry.

### 2.7 Conversation context (per-conversation backend)

Flutter wraps every chat route in `ConversationBackendScope`, which swaps in the conversation's own connection
(SCREENS-NAV-110). Native:

- The chat entry, every `ChatPagesRoute` and every sheet keyed by a `ConversationRef` resolve their graph with
  `ConnectionRegistry.graphFor(ref)` (ADR §9.1). Nothing reads "the active server" to draw a conversation.
- When the graph is not open yet (an agent chat opened before its connection exists), the chat shows
  `KitSkeletonTranscript` while `ConnectionRegistry.open(ref.profileId)` runs.
- The status line inside a conversation shows that conversation's backend in its own words ("Claude Code isn't
  answering", not "OpenCode is reconnecting"). The shell's status slot asks the top entry for its `StatusSource`
  (§10.1).

### 2.8 Capability gates on routes

Each entry installer declares what its route needs:

```kotlin
sealed interface RouteRequirement {
    data object None : RouteRequirement
    data class Platform(val flag: PlatformFlag) : RouteRequirement          // e.g. BuiltinLinux, Voice, Camera, Notifications, BackgroundService, TailscaleHandoff, HomeWidget
    data class Server(val flag: ServerFlag, val explain: EnableFlowId?) : RouteRequirement // checked on the route's profile
    data class All(val parts: List<RouteRequirement>) : RouteRequirement
}

data class RouteMeta(
    val motion: RouteMotion = RouteMotion.Page,
    val pane: Pane = Pane.Single,
    val requires: RouteRequirement = RouteRequirement.None,
)
```

`RouteGate.check(route, world)` in `:ui:shell`:

- `Platform` false → the route is **not registered** on that platform (installers check `PlatformCapabilities` when
  they install). A call to `open` it logs `nav.absent` and does nothing. No UI may show a door to it.
- `Server` false on the route's profile → `showSheet(CapabilityExplainer(explain))` when an enable flow exists, else
  `ServersRoute.Capabilities(profileId)` ("Available on this server" explains why).
- Unknown (the profile is not connected) → the page opens and shows its own state.

Gated routes (Platform unless marked Server):

| Route | Requirement |
|---|---|
| all `PhoneRoute.*`, `AgentsRoute.SignInTerminal` | `BuiltinLinux` (Android) |
| `ServersRoute.PairingScanner` | `QrPairing` (camera) |
| `ServersRoute.Tailscale` | `TailscaleHandoff` |
| `SettingsRoute.Voice` | `Voice` |
| `SettingsRoute.Notifications(page = Background)` | `BackgroundService` |
| `UsageRoute.WidgetSettings` | `HomeWidget` |
| `ProjectRoute.Files/Folder/FileView` | Server `fileBrowsing` (explain `projectChoose`) |
| `ProjectRoute.Terminal` | Server `terminal` (page explains when the server keeps no terminals, SCREENS-NAV-06) |
| `ProjectRoute.Review`, `ProjectRoute.Diff` | Server `sessionDiff` or `projectManagement` |
| `ProjectRoute.Health`, `Worktrees`, `Services` | Server `projectManagement` |
| `ProjectRoute.Workspaces` | Server `managedWorkspaces` |
| `ChatsRoute.ClaudeImport` | Server `providerImportSupported` |
| `ChatsRoute.AllConversations` | Server `globalSessionSearch` |
| `ChatPagesRoute.Note` | Server `supportsSessionNotes` |
| `ChatPagesRoute.StagedRevert` | Server `sessionRevert` |
| `SettingsRoute.SavedPermissions` | Server `savedPermissionRules` |
| `SettingsRoute.Providers`, `McpCatalog`, `McpSetup`, `Tools` | Server `serverCatalog` (explain `modelSignIn` / `mcpAdd`) |
| `UsageRoute.Hub(section = Spending)` | Server `usageSpending` (the tab is absent, not the page) |

Flag names in this table follow the map where it cites them (`fileBrowsing`, `terminal`, `projectManagement`,
`sessionDiff`, `sessionRevert`, `supportsSessionNotes`, `globalSessionSearch`, `serverCatalog`,
`providerImportSupported`); the others are descriptive. The binding names come from `ServerCapabilities` (85 flags,
ADR §5.2 `:core:domain`): the feature agent copies the exact flag the Flutter screen checks today and records it in
`contracts/<slice>.md`. A screen with no gate today gets none.

### 2.9 Entry installers and ViewModels

```kotlin
// :feature:chats  ChatsEntries.kt  — the only file in the feature that imports androidx.navigation3.runtime (03 §2.2)
@ContributesIntoSet(AppScope::class)
@Inject
class ChatsEntries : EntryInstaller {
    override fun EntryProviderScope<NavKey>.install() {
        entry<ChatsRoute.Conversations>(metadata = RouteMeta(motion = RouteMotion.Root).toNav()) {
            val vm = metroViewModel<ConversationsViewModel>()
            ConversationsScreen(vm.state.collectAsStateWithLifecycle().value, vm)
        }
        entry<ChatsRoute.Inbox>(metadata = RouteMeta().toNav()) {
            val vm = metroViewModel<InboxViewModel>()
            InboxScreen(vm.state.collectAsStateWithLifecycle().value, vm)
        }
        entry<ChatsRoute.NewConversation>(metadata = RouteMeta(motion = RouteMotion.FullScreenDialog).toNav()) { key ->
            val vm = assistedMetroViewModel<NewConversationViewModel, NewConversationViewModel.Factory> { create(key) } // 03 S5
            NewConversationScreen(vm.state.collectAsStateWithLifecycle().value, vm)
        }
        // …
    }
}
```

- `RouteMeta.toNav()` turns motion into `NavDisplay.TransitionKey` / `PopTransitionKey` / `PredictivePopTransitionKey`
  metadata [V names, developer.android.com "Animate between destinations"] and pane into the list-detail metadata
  (§8.3). It lives in `:ui:routes` and is implemented by the kit's transition helpers.
- Entry decorators (shell): `rememberSaveableStateHolderNavEntryDecorator()`,
  `rememberViewModelStoreNavEntryDecorator()`, and the shell's `ShortcutClaimsDecorator` (§9.3) [V names in
  kmp-cmp-status §4].
- One ViewModel per page (ADR §9.1). A ViewModel gets `AppNavigator` by injection and never touches the back stack.

---

## 3. Transitions and Back

### 3.1 Motion per route class

All durations come from kit tokens (150–250 ms) except the owner-approved chat motions (MOT-2, MOT-5: the chat grows out
of its row in 450 ms and returns in 300 ms, `KitMotion.rowReturn`) [R ui-kit UI-KIT-07/08].

| `RouteMotion` | Used by | Forward (tap) | Pop (button, Ctrl+W, page Close) | Predictive back gesture | Motion level Calm | Motion level Off / system "remove animations" |
|---|---|---|---|---|---|---|
| `GrowFromRow` | `ChatRoute.Chat` opened from a row that registered an origin (Conversations, Inbox, All conversations) | container transform: `sharedBounds(ChatKey(ref))` from the row, 450 ms; list holds still | shrink into the row, 300 ms; falls back to `Page` pop if the row is gone or off screen | Material full-screen preview (scale 1 → 0.9, fade-through at 35 %, shift **away from the swipe edge**); shared bounds **off** | cross-fade 150 ms | none |
| `Page` | most pages | shared-axis X, 250 ms | reverse | Material full-screen preview | cross-fade 150 ms | none |
| `FullScreenDialog` | `NewConversation`, `PairingScanner` | slides up from the bottom, 250 ms | slides down | the page follows the finger down (`PredictivePopTransitionKey` = slide out vertically) | cross-fade | none |
| `TopLevel` | `Conversations`, `Files`, `Settings.Hub` when switched by rail/sidebar/Ctrl+1..3 | fade-through, 250 ms (leaving fades in the first third) | same | Material preview | cross-fade | none |
| `Replace` | `replaceTop` (new conversation → chat, import → chat) | cross-fade 250 ms | — | — | cross-fade | none |
| `Root` | `replaceRoot`, `land` | cross-fade 250 ms, no slide | — | — | cross-fade | none |

List-detail scenes (§8.3): changing only the detail pane cross-fades the pane in 150 ms; the list pane does not move.

### 3.2 Predictive back with the row → chat transform (option A, ADR §9.4)

Slice-0 proof P2 settles the exact hooks [U]. The shape:

```kotlin
// :ui:shell commonMain — sketch; names checked against Nav3 1.2.0 / NavigationEvent 1.1.0 in P2
@Composable
internal fun ShellNavDisplay(stack: NavBackStack<NavKey>, nav: AppNavigatorImpl, installers: Set<EntryInstaller>) {
    val dispatcher = LocalNavigationEventDispatcherOwner.current!!.navigationEventDispatcher
    val transition by dispatcher.transitionState.collectAsState()
    val inGesture = transition is NavigationEventTransitionState.InProgress
    var poppedByGesture by remember { mutableStateOf(false) }   // replaces the demo's 700 ms latch

    SharedTransitionLayout {
        // Kit reads this in Modifier.kitGrowFromRow(): SharedContentConfig.isEnabled = !inGesture && !poppedByGesture
        CompositionLocalProvider(LocalRowTransformEnabled provides (!inGesture && !poppedByGesture)) {
            NavDisplay(
                backStack = stack,
                onBack = { nav.onSystemBack(fromGesture = inGesture).also { if (inGesture) poppedByGesture = true } },
                sharedTransitionScope = this,
                sceneStrategies = listOf(rememberShellListDetailStrategy()),       // §8.3
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                    rememberShortcutClaimsDecorator(),
                ),
                transitionSpec = KitTransitions.pageForward,
                popTransitionSpec = KitTransitions.pagePop,
                predictivePopTransitionSpec = { edge -> KitTransitions.predictivePop(edge) },
                entryProvider = entryProvider { installers.forEach { with(it) { install() } } },
            )
        }
        // Cleared on settle, never on a timer:
        LaunchedEffect(isTransitionActive) { if (!isTransitionActive) poppedByGesture = false }
    }
}
```

- The Conversations list freezes its row order while `isTransitionActive` (Flutter `f7e775dd3`, `89828a3c8`): the
  screen passes `frozen = true` to the kit feed list, which applies queued reorders after settle.
- Option B (Compose 1.13 + Nav3 1.3 deferred two-stage transitions) lives in `:ui:shell` `androidMain` as a
  `JETPACK_AHEAD.md` row and becomes the default the week Google marks both stable (ADR §9.4).
- RTL: the predictive shift follows `swipeEdge`, not the layout direction, because an Arabic user can swipe from either
  edge [R motion-native §3.4].

### 3.3 Who owns Back (dispatch order, top wins)

1. An open menu or popup (`KitMenu`).
2. An open sheet or full-screen overlay (shared sheet, local sheet, image gallery, prompt editor, code viewer). The kit
   composes their handlers after the page, so they win ("last composed wins") [V motion-native §3.2].
3. An in-page mode: find bar, a search field with text, review line selection, Files search.
4. The page's own guard (table below).
5. Nav3 pops the entry.
6. At the root: nothing in the app; the system plays back-to-home.

This order is plain Kotlin (`BackOwnerPolicy` in `:ui:shell`) and table-tested in `commonTest` with
`navigationevent-testing` (testing-perf §4.2).

### 3.4 Pages with their own Back guard

A handler that is enabled turns off the system's predictive preview for that page, so every guard is enabled only while
it has something to protect.

| Page | Enabled when | On Back |
|---|---|---|
| `ChatRoute.Chat` | composer text ≠ saved draft, or a photo recovery is in progress | `actions.leaveRequested()`: local-only draft save through the writer lane; saved → pop; refused → confirm "Leave without saving?" (`chat-leave-unsaved-draft`). Because drafts save on a 600 ms debounce, the guard is usually off and the preview plays. |
| `ChatPagesRoute.Note` | note text ≠ saved note | same pattern; confirm on refusal |
| `ServersRoute.Editor` | step > 1 → previous step; step 1 and dirty → discard confirm | the password field is never draft-saved |
| `ProjectRoute.Files` / `Folder` | search field has text | clears the search |
| `ProjectRoute.DevPreview` | the web view can go back | steps the page's own history first (SCREENS-NAV-58) |
| `SettingsRoute.Hub` | search field has text | clears the search; results cross-fade back to the five groups |
| `ProjectRoute.Review` | a line selection exists | clears the selection |
| `PhoneRoute.TermuxMigration` | a copy is running | confirm "Stop moving?"; the move is resumable either way |

Pages with **no** guard by design: Conversations, Inbox, New conversation (its words are kept in `KitDraft` per project,
so Close and Back lose nothing), Settings pages, setup progress ("Safe to leave"), all viewers.

---

## 4. The navigation graph

### 4.1 Top level (all window sizes share one stack model)

```
                                   ┌───────────────────────── opening gate (pre-graph): Opening… / Updating your app… / safe mode
                                   ▼
index 0 (RootRoute):   ChatsRoute.Conversations ◄──── RootDecision ────► ServersRoute.Servers (welcome / credentials)
                          │   │   │                                          OnboardingRoute.ManagedMove
     server pill ─────────┘   │   └──── gear (compact) / rail Settings ──► SettingsRoute.Hub ──► Group ──► leaf pages
     Search (sheet) ──────────┘                                            (list | detail on expanded)
     rows / New chat ──► ChatRoute.Chat ◄──── notifications, widgets, pinned shortcuts, links, share, schedules
                          │  project chip ──► ProjectRoute.Files ──► Folder ──► FileView   (rail "Files" on medium+)
                          │  menu / chips  ──► ChatPagesRoute.* , ProjectRoute.Review / Diff / DevPreview / Terminal
                          │  sheets        ──► ModelPicker, Approvals, Permission/Question/Form, ContinueWith, Schedule, Git
     "N need you" ──► ChatsRoute.Inbox ──► ChatRoute.Chat (lands on the request card)
```

Rail / sidebar destinations (medium and wider): **Conversations**, **Files** (only when the connected server can browse
files), **Settings**. The rail highlights the nearest `TopLevelRoute` from the top of the stack: `[Conversations, Chat]`
highlights Conversations; `[Conversations, Chat, Files]` (opened from the project chip) highlights Files.

### 4.2 Area graphs

**Servers and connection**

```
ServersRoute.Servers ──► Editor(Add|Edit|Credentials) ──► PairingScanner (result → PairingInbox engine, never a key)
        │                         └──► Tailscale
        ├──► ServerPage(profileId) ──► ConnectionCheck ──► ReachComputer
        │            ├──► HostSetup   ├──► Capabilities   ├──► SettingsRoute.Providers / Tools / SavedPermissions / AiSetup
        │            └──► SettingsRoute.Notifications(section = servers)
        ├──► PhoneRoute.ThisPhone (the phone's own row)       ├──► PhoneRoute.TermuxMigration (Termux migration row)
        └── sheets: QueuedPromptMove, ConnectionDetails
ServerSwitcher sheet ──(after close)──► Servers(arrival) | PhoneRoute.SetupStart | ChatRoute.Chat | phone action | leave
```

**This phone (Android only)**

```
PhoneRoute.ThisPhone ──► SetupStart ──► SetupProgress ──► SetupReady ──land(then = Chat(FirstRun))
        ├──► Linux ──► Running | LinuxPorts | LinuxStorage | LinuxPackages | LinuxServices ──► LinuxService(name) / LinuxServiceAdd
        │         └──► LinuxBackups (also "Back up first" from Reset)
        ├──► LocalTerminal   ├──► KeepRunning   ├──► AgentsRoute.Hub   └──► TermuxMigration
```

**Settings (five groups, hub → page → sheet at most; target-ia §1.3)**

```
SettingsRoute.Hub (search + Server, Agent, Conversations, This app, Help)
  Group(Server):        ServersRoute.Servers, PhoneRoute.ThisPhone
  Group(Agent):         ModelPicker sheet (NewConversations), Providers, UsageRoute.Hub, Tools, AgentsRoute.Hub
  Group(Conversations): reasoning / timestamps / phrase-chips switches in place, Default shell (local sheet), Voice, PermissionsAutomation ──► SavedPermissions
  Group(ThisApp):       Notifications(Notifications), Notifications(Background) ──► PhoneRoute.KeepRunning, Appearance, Language (local sheet), Privacy ──► PrivacyPolicy sheet
  Group(Help):          AboutRoute.Guide, AboutRoute.Tips, AboutRoute.ReportProblem, AboutRoute.About ──► Notices
  With several servers: "By server" rows ──► ByServer(kind) ──► ServerPage(profileId)
```

**Usage**: `UsageRoute.Hub(Plans|Spending) ──► Subscription(key) | Add | ReadFrom | Alerts | WidgetSettings`.

**About**: every error state's "Report a problem" ──► `AboutRoute.ReportProblem(problemId)` ──► `Kept`.

---

## 5. Screen inventory: every Flutter screen, sheet and dialog → native home

Columns: **Flutter** (class or function; path under `lib/ui/` unless it starts with `lib/`), **Kind** (page, sheet,
dialog, state, part), **Native home** (route, shared sheet, local sheet in the named page, or "state of"),
**State holder** (ViewModel; S = shared shell VM), **Entry points**, **Rules and decisions that apply**, **Rows**
(catalog and flows), **Slice** (ADR slice numbers; the delivery plan owns dates).

### A. Shell, root and system entry (`:ui:shell`, `:feature:onboarding`, `:feature:search`)

| # | Flutter | Kind | Native home | State holder | Entry points | Rules and decisions | Rows | Slice |
|---|---|---|---|---|---|---|---|---|
| A01 | `AppBootstrapGate` (`lib/main_parts/bootstrap_gate.dart`) | state | Shell opening gate (pre-graph composable): Opening… / Updating your app… (migration) / Try again after 8 s / failure with Report a problem + Start fresh | `BootstrapViewModel` (S) over `:engine:entry` `BootstrapState` + `MigrationGate` | every launch | first frame draws immediately (ADR §9.7); Start fresh = confirm, closes phone agents first (ee2c53940); no raw errors | SCREENS-NAV-01, B-MIGR-*, E2E-39 | 1 |
| A02 | — (new) | state | Shell safe mode: Report a problem, Settings, Start fresh | `SafeModeViewModel` (S) | crash-loop guard (3 crashes in 10 s, ADR §9.7) | Settings opens without connecting | ADR §9.7 | 1 |
| A03 | `_Root` (`lib/main_parts/root.dart`) | logic | `RootDecision` (`:engine:entry`) + `RootViewModel` (S) calling `replaceRoot` (§2.6) | `RootViewModel` | launch, profile/connection change | side duties move to `AppStartup` | SCREENS-NAV-02, B-NAV-017, B-NAV-024 | 2 |
| A04 | `ManagedMoveView` | state | root route `OnboardingRoute.ManagedMove` | `ManagedMoveViewModel` | `RootDecision` | quiet retries while the phone server starts; failure: Details + Try again | B-NAV-029 | 3 |
| A05 | `HomeScreen` + `HomeOpening` | page | **merged away**: shell chrome (rail/sidebar/top-bar controls, §8.2) + `ChatsRoute.Conversations` root; opening state lives in Conversations | — | — | §6.2 M1 | SCREENS-NAV-03/06, B-NAV-037 | 2 |
| A06 | `SavedServerConnectionCard` | state | state block of Conversations (connecting / starting / not answering / credentials / start the phone server) | `ConversationsViewModel` | root connecting | "Connected" only when a probe proved it; shared 8 s clock | SCREENS-NAV-73, B-NAV-007/008/009, E2E-03 | 2 |
| A07 | `AppConnectionStatusScope`, `AppExitNoticeLine`, `ThermalNoticeLine`, background pause notice, one-shot notices | part | shell `StatusSlot` drawn by the top page's `KitScreen`; conditions from `:engine:attention` combiner (§10.1) | `StatusViewModel` (S) | always | one status line per window; priority connection > appStopped > heat > riskySwitch > work > update > info | SCREENS-NAV-07/83 | 2 |
| A08 | `showServerSwitcher` | sheet | `SheetKey.ServerSwitcher` | `ServerSwitcherViewModel` | server pill | only chooses; acts after close (N2); same-host servers told apart by port | SCREENS-NAV-08, B-NAV-040/041 | 2 |
| A09 | `showConnectionDetailsSheet` | sheet | `SheetKey.ConnectionDetails(profileId)` | `ConnectionDetailsViewModel` | status line Details; Servers row | Update password → `Editor(Credentials)`; certificate review → `Editor(Edit)` | SCREENS-NAV-09 | 2 |
| A10 | `KitCapabilityExplainer` + `registerCapabilityFlows` (`capability_flows.dart`) | sheet | `SheetKey.CapabilityExplainer(flow)` + `EnableFlows` table (§7.12) | `CapabilityExplainerViewModel` (S) | any gated door | unregistered flows explain instead of offering a dead button | SCREENS-NAV-20 | 2 |
| A11 | `showProductError` | dialog | kit `KitAlert` in the screen that failed (only when no part can carry the failure) | that screen's VM | — | fixed words + Details + Report a problem | SCREENS-NAV-26 | 2 |
| A12 | `showCommandPalette` (`desktop/shortcuts.dart`) | sheet | `SheetKey.CommandLauncher` | `CommandLauncherViewModel` | Search button, Ctrl+K, `/help` | empty field: Go to places then 3 recent conversations; same matcher as Settings search; up to 400 conversations and 200 projects | SCREENS-NAV-10, B-NAV-049/056/057/058, E2E-24 | 5 |
| A13 | `showShortcutsHelp` | sheet | `SheetKey.ShortcutsHelp` | none (reads `ShortcutCatalog`) | Ctrl+/, About, Settings › Help row | generated from the bindings (single source, §9) | DESKTOP-WEB-CROSS-12 | 5 |
| A14 | `DesktopReleaseNotice`, `ShorebirdUpdateNotice` | part | status line kind `update` + `SheetKey.AppUpdate` | `UpdateViewModel` (S) | update found | Shorebird ends at the switch; native updater (ADR §9.9) | SCREENS-NAV-104 | 4 |

### B. Conversations (`:feature:chats`)

| # | Flutter | Kind | Native home | State holder | Entry points | Rules and decisions | Rows | Slice |
|---|---|---|---|---|---|---|---|---|
| B01 | `ChatsHomeScreen` (`screens/chats/chats_home_screen.dart`) | page | `ChatsRoute.Conversations` (root, Top) | `ConversationsViewModel` | launch, `land()`, rail, Ctrl+1, Back from Files/Settings | one list by urgency, sections Today / Earlier only (no state sections); "N need you" line opens Inbox; filter chips (project, connections when > 1, Running); New chat floating action hidden when the empty state offers it; late rows fade + rise 8 dp, first rows none; row says "Opening…" while an old agent chat reopens; reading width 720 centred | SCREENS-NAV-27/37, B-NAV-010/024/027/030/031/037/065/066/067, E2E-21, E2E-36 | 2 |
| B02 | request cards inside rows (`chats_host.dart listRequest`) | part | part of B01 (kit-chat request card, secondary buttons) | B01 | — | permission answered in place with the Undo window; question/form rows open the chat | SCREENS-NAV-28 | 3 |
| B03 | `showChatsProjectSheet` | sheet | local sheet of B01 | B01 | project chip | "Open a project…" closes this sheet, then opens `SheetKey.OpenProject` | SCREENS-NAV-29 | 2 |
| B04 | `showChatsSourcesSheet` | sheet | local sheet of B01 | B01 | connections chip | the active connection cannot be hidden; `oc.chatListShown.<profile>` | SCREENS-NAV-29 | 2 |
| B05 | `InboxScreen` / `openInbox` | page | `ChatsRoute.Inbox` | `InboxViewModel` | "N need you" line; `ConversationsArrival(NeedsYou)` from notifications, Attention tile, widgets; Go to "Needs you" | oldest first; permissions answered in place; watched (monitor) rows tappable, never answerable here; empty: calm line + up to 5 finished-not-opened | SCREENS-NAV-35/36, B-NAV-002/003/025/082, E2E-36 | 3 |
| B06 | `ProfileMonitorInbox` | part | **dropped**: not mounted anywhere in this checkout; its rows are Inbox `InboxWatchedEntry` rows | — | — | §6.1 X5 | — | — |
| B07 | `NewChatScreen` / `showNewChat` | page | `ChatsRoute.NewConversation(profileId?, projectDir?)` | `NewConversationViewModel` | New chat button, empty states, Go to | full-screen dialog with Close; draft per project `oc.draft.new-chat.<b64url dir>.<profile>`; send → `replaceTop(Chat(ref))` with `OpenedAs.Existing`; Git failure swaps Retry for Git repair; agent cards checked against what the phone can run | SCREENS-NAV-30, B-NAV-032/053, E2E-21 | 2 |
| B08 | `showAgentModelSheet` (`screens/agents/agent_model_sheet.dart`) | sheet | local sheet of B07 | B07 | agent card model row | used only by New conversation [R grep] | SCREENS-NAV-62 | 3 |
| B09 | `showGitRepairSheet` | sheet | local sheet of B07 | B07 | start failure `projectGit` | starts at once; plain Git words | SCREENS-NAV-32 | 3 |
| B10 | `showIsolatedTaskSheet` | sheet | local sheet of B07 (and of B01 row menu "Start in a separate copy") | B07 / B01 | "In a separate copy" | Back closes and stops waiting; hidden for folders with no commit | SCREENS-NAV-33 | 5 |
| B11 | `showClaudeImport` / `ClaudeImportScreen` | page | `ChatsRoute.ClaudeImport(profileId, projectDir)` | `ClaudeImportViewModel` | New conversation "Import from Claude Code" | gated `providerImportSupported`; result → `replaceTop(Chat)` | SCREENS-NAV-31 | 5 |
| B12 | `GlobalSessionsScreen` | page | `ChatsRoute.AllConversations(profileId, archived)` | `AllConversationsViewModel` | `/sessions`, Search "all conversations"/"archived conversations" | search first; one project filter; "Continue this session here?" local sheet; load more on scroll with visible Try again | SCREENS-NAV-34 | 5 |
| B13 | `SessionImportScreen` | page | `ChatsRoute.ImportConversation(profileId)` | `ImportConversationViewModel` | All conversations menu; Search hub row | file picked through `ActivityResults` (SAF); result → `replaceTop(Chat)` | SCREENS-NAV-55 | 5 |
| B14 | `OlderSessionsPager`, `LastKnownSessions`, `KitFeedItem` rows | part | kit parts used by B01 | B01 | — | last-known rows never fill live maps | SCREENS-NAV-37 | 2 |

### C. The conversation (`:feature:chat`, `:feature:chat-composer`, `:feature:chat-pages`)

| # | Flutter | Kind | Native home | State holder | Entry points | Rules and decisions | Rows | Slice |
|---|---|---|---|---|---|---|---|---|
| C01 | `ChatScreen` (71 files, `screens/chat_screen.dart` + `screens/chat/*`) | page | `ChatRoute.Chat(ref)` + `ChatArrival` | `ChatViewModel` (S chat lead) hosting `ComposerViewModel` (`:feature:chat-composer`) inside the same entry | rows, Inbox, notifications, widgets, pinned shortcuts, links, share, scheduled runs, Search, continue-with, new conversation | Back guard §3.4; `GrowFromRow` when opened from a row; read acknowledgement only while the transcript is visibly read; first paint from the saved tail excerpt | SCREENS-NAV-38–54, CHAT-*, B-NAV-005/023, E2E-05/06/10/11 | 2 |
| C02 | find bar (`chat/transcript_find.dart`) | part | in-page mode of C01 | C01 | Ctrl+F, `/find`, session menu | Esc closes; F3 / Shift+F3; excerpt panel for hidden hits | SCREENS-NAV-54 | 2 |
| C03 | timeline sheet (`chat/timeline_sheet.dart`) | sheet | local sheet of C01 | C01 | session menu, `/timeline` | prompt row has Fork | SCREENS-NAV-54 | 4 |
| C04 | message actions (`chat/chat_message_actions.dart`) | sheet/menu | local `KitMenu` of C01 | C01 | the visible "Message actions" disc; long-press | copy redacted unless own prompt | SCREENS-NAV-41/54 | 2 |
| C05 | `showStageRevertSheet` | sheet | local sheet of C01 → `ChatPagesRoute.StagedRevert` | C01 | message actions "Undo from here" | two shapes by server ability | SCREENS-NAV-54 | 4 |
| C06 | session menu (`widgets/session_menu.dart`, `chat/chat_session_menu.dart`) | menu | local `KitMenu` of C01 (top-bar overflow) | C01 | ⋯ in the chat bar | "Go to" group then "Do" group, Delete last under a divider; gated by `SessionMenuOffer.of(capabilities)`; adds **Pin / Unpin** (O-07-2) | SCREENS-NAV-55, B-NAV-051, E2E-24 | 2 (items land with their pages) |
| C07 | context ring sheet | sheet | local sheet of C01 → `ChatPagesRoute.Context` | C01 | context ring | "Compact now" when supported | SCREENS-NAV-39 | 4 |
| C08 | `showRunningWorkSheet` | sheet | local sheet of C01 | C01 | work count in the bar | urgency order; spinner stops when idle | SCREENS-NAV-57 | 4 |
| C09 | `ShellOutputScreen` | page | `ChatPagesRoute.ShellOutput(ref, shellId)` | `ShellOutputViewModel` | C08 row | Follow switch; stop with confirm | SCREENS-NAV-57 | 4 |
| C10 | request card (`chat/attention_card.dart`) | part | kit-chat part above the composer in C01 | C01 | — | one at a time, oldest first; never takes composer focus | SCREENS-NAV-46 | 2 |
| C11 | `showPermissionSheet`, `showPermissionDetails`, `showQuestionSheet`, `showQuestionDetails`, forms (`widgets/form_renderer.dart`) | sheet | `SheetKey.Permission` / `Question` / `Form` (`RequestRef`) | `PermissionSheetViewModel`, `QuestionSheetViewModel`, `FormSheetViewModel` | C10 Details; B02; B05 | "Always allow" states its scope first (13B); forms ≥ 5 fields → full height (end sheet on wide); Dismiss confirmed | SCREENS-NAV-46, E2E-06 | 2 (forms 3) |
| C12 | `AlwaysAllowInvitation` | part | inline part of C10 | C01 | third identical ask | once per server + scope | SCREENS-NAV-46 | 3 |
| C13 | `showSessionApprovalsSheet` | sheet | `SheetKey.Approvals(ref)` | `ApprovalsViewModel` | approval chip; session menu; `/approvals` | only modes the connection supports; "Approve everything" confirmed | SCREENS-NAV-56 | 4 |
| C14 | `showModelPicker` + `ModelCatalogView` | sheet | `SheetKey.ModelPicker(scope)` | `ModelPickerViewModel` | model chip, + sheet, `/models`, `/agents`, `/variants`, Settings › Model & mode, B07 | a tap chooses and closes; "All models" is a step inside the same sheet | SCREENS-NAV-62, B-NAV-033–036, E2E-37 | 3 |
| C15 | `showAgentFeaturesSheet` | sheet | local sheet of the composer | `ComposerViewModel` | agent features chip | one switch per feature; refusal keeps the old value | SCREENS-NAV-62 | 3 |
| C16 | command sheet (`widgets/command_sheet.dart`) | sheet | local sheet of the composer; body is the kit part `KitCommandList` (also used by Settings › Tools › Commands) | `ComposerViewModel` | `/`, Ctrl+K inside the chat | capability-gated slash list (§5 C-slash) | SCREENS-NAV-48 | 3 |
| C17 | `showRunCommandDialog` | dialog | `SheetKey.RunCommand(profileId, commandId, ref?)` | `RunCommandViewModel` | C16 server command; Tools › Commands | nothing runs until "Run /review" | SCREENS-NAV-48 | 3 |
| C18 | `!` shell dialog, command-auth sheet (`library/command_auth_sheet.dart`) | dialog/sheet | local to the composer | `ComposerViewModel` | `!cmd`; command needing provider auth | error stays under the field | SCREENS-NAV-48 | 3 |
| C19 | + tray and More sheet (`chat/composer_tools.dart`, `composer_tray.dart`) | part/sheet | composer row + local sheet | `ComposerViewModel` | + | tool runs after the sheet closed; conversation settings rows first | SCREENS-NAV-47, CHAT-GENUI-28 | 3 |
| C20 | `WebSourcesScreen` | page | `ComposerRoute.WebSources(ref)` | `WebSourcesViewModel` | + sheet "Web sources" | selections staged in `:engine:composer`; never sends or fetches | SCREENS-NAV-49 | 5 |
| C21 | chat gallery + markup editor (`chat/chat_gallery.dart`, `showKitImageGallery`, `showKitImageMarkup`) | overlay | local full-screen overlay in C01 (kit) | C01 | tap a transcript picture | Send puts the picture in the field, never sends | CHAT-GENUI-19 | 5 |
| C22 | full-screen prompt editor (`chat/prompt_editor.dart`) | overlay | local full-screen overlay in C01, **not a route**: it shares the composer's `TextFieldState` (ADR §9.2) | `ComposerViewModel` | `/editor`, + sheet | Close with changes asks once | SCREENS-NAV-51 | 3 |
| C23 | saved prompts, reuse a prompt (`chat/prompt_stash.dart`, `prompt_history.dart`) | sheet | local sheets of the composer | `ComposerViewModel` | + sheet | delete acts at once with Undo, no confirm | SCREENS-NAV-51 | 3 |
| C24 | `showVoiceModelSetupSheet` | sheet | `SheetKey.VoiceModelSetup` | `VoiceModelSetupViewModel` | mic "Choose another model"; Settings › Voice; enable flow | packs gate on total RAM | SCREENS-NAV-52, MEDIA rows | 5 |
| C25 | voice conversation, read aloud voice chooser (`chat/voice_conversation.dart`, `chat/read_aloud.dart`) | state/sheet | state of C01; local sheet | C01 | mic hold; "Read reply" | Stop reading in the top bar | SCREENS-NAV-52/53 | 5 |
| C26 | queued message menu + `showQueuedPromptMoveSheet` | menu/sheet | menu local; `SheetKey.QueuedPromptMove(profileId)` | C01 / `QueuedPromptMoveViewModel` | "Waiting to send" bubble | never acts on its own | SCREENS-NAV-50/77, B-NAV-046 | 3 |
| C27 | schedule from chat (`chat/chat_schedule_send.dart`) | sheet | `SheetKey.Schedule(null, ref)` after `:engine:schedule` seeds the draft from the composer text | `ScheduleSheetViewModel` | long-press Send; session menu "Scheduled prompt…" | tip `send-later` | SCREENS-NAV-50/98 | 4 |
| C28 | `showContinueWith`, `showContinueOnComputerSheet`, `showContinueOnPhoneSheet` | sheet | `SheetKey.ContinueWith` / `ContinueOnComputer` / `ContinueOnPhone` | one VM each | session menu; handoff link line | QR drawn with qrose; address only with the opt-in switch | SCREENS-NAV-59, B-NAV-062 | 4 |
| C29 | `showSessionDestinationSheet`, `showConsoleOrganizationSheet` | sheet | `SheetKey.SessionDestination(ref, mode)` | `SessionDestinationViewModel` | session menu; `/move`, `/warp`, `/org` | gated by capability; confirm | SCREENS-NAV-55 | 5 |
| C30 | rename dialog, archive / delete / share / unshare confirms | dialog | local kit dialogs of C01 | C01 | session menu | error stays under the field; confirms name the conversation | SCREENS-NAV-55 | 4 |
| C31 | `SessionContextScreen` | page | `ChatPagesRoute.Context(ref)` | `ContextViewModel` | C07; session menu "Details"; `/context` | raw figures under Details | SCREENS-NAV-55 | 4 |
| C32 | `ActiveContextScreen` | page | `ChatPagesRoute.ActiveContext(ref)` | `ActiveContextViewModel` | C31 | searchable | SCREENS-NAV-55 | 4 |
| C33 | `SessionRelationsScreen` | page | `ChatPagesRoute.Relations(ref)` | `RelationsViewModel` | session menu "Subagents"; parent line | **pin tile moves to the session menu** (O-07-2) | SCREENS-NAV-55/112 | 4 |
| C34 | `SessionNoteScreen` | page | `ChatPagesRoute.Note(ref)` | `NoteViewModel` | session menu; `/note` | Back guard §3.4 | SCREENS-NAV-55 | 4 |
| C35 | `SessionExportScreen` | page | `ChatPagesRoute.Export(ref)` | `ExportViewModel` | session menu; `/export` | redacted JSON by default; save through `ActivityResults` | SCREENS-NAV-55, B-PROTO-096 | 4 |
| C36 | `StagedRevertScreen` | page | `ChatPagesRoute.StagedRevert(ref, messageId)` | `StagedRevertViewModel` | C05 | each file opens `ProjectRoute.Diff(RevertFile)`; both actions ask first | SCREENS-NAV-54 | 4 |
| C37 | `DevPreviewScreen` / `openDevPreview` | page | `ProjectRoute.DevPreview(profileId, secure, port, path, from)` | `DevPreviewViewModel` | localhost link in a reply; menu "Preview a running app"; terminal source line | address built from the saved host + port + path, never from link text; Back steps web history; "Ask for a change…" steers | SCREENS-NAV-58, B-NAV-043, E2E-20 | 5 |
| C38 | `showDevServerLogs` | sheet | local sheet of C37 | C37 | DevPreview bar | — | SCREENS-NAV-58 | 5 |
| C39 | `DemoScreen` ("Try it offline") | page | `ChatRoute.Demo` (the production chat on a demo connection graph and in-memory stores) | `ChatViewModel` with `DemoConnectionGraph` | Guide, Servers welcome, Settings › Help | menu, drafts, links and read state off; one status line says it is simulated | SCREENS-NAV-61 | 5 |
| C40 | agent cards, connector cards (`chat/agent_cards.dart`, `widgets/agent_card_*`, `connector_card.dart`) | part | kit-chat parts in the transcript | C01 | — | sign-in through the link policy | SCREENS-NAV-45 | later per genui section |
| C41 | watching mode, `TeamWatchLiveScreen`, `TeamConversation*` | page/state | **deferred** with AI Team (D6) | — | — | §6.3 | SCREENS-NAV-60/88 | after switch |

**C-slash: slash commands that navigate** (chat library `chat_command_actions.dart`; all gated by capabilities, never by
backend kind):

| Slash | Native target | Slash | Native target |
|---|---|---|---|
| `/new` | create session → `replaceTop(Chat, NewlyCreated)` | `/status` | `ServersRoute.ServerPage(ref.profileId)` |
| `/sessions` | `ChatsRoute.AllConversations` | `/debug` | `AboutRoute.ReportProblem` |
| `/workspaces` | `popTo(root)` (the one home; never a second shell) | `/themes` | `SettingsRoute.Appearance` |
| `/files` | `ProjectRoute.Files(profileId, projectDir)` + `FilesArrival` | `/diff` | `ProjectRoute.Review(scope = Session(ref))` |
| `/health` | `ProjectRoute.Health` | `/context` | C07 sheet |
| `/move`, `/warp`, `/org` | `SheetKey.SessionDestination(mode)` | `/timeline` | C03 sheet |
| `/editor` | C22 overlay | `/note` | `ChatPagesRoute.Note` |
| `/terminal` | `ProjectRoute.Terminal` | `/approvals` | `SheetKey.Approvals` |
| `/models`, `/agents`, `/variants` | `SheetKey.ModelPicker(Conversation(ref))` | `/plan` | kit task sheet (local) |
| `/connect` | `SettingsRoute.Providers` | `/help` | `SheetKey.CommandLauncher` |
| `/mcps`, `/tools`, `/skills`, `/references` | `SettingsRoute.Tools(section)` | `/share`, `/unshare`, `/rename`, `/fork`, `/compact`, `/thinking`, `/timestamps`, `/undo`, `/redo`, `/copy`, `/export`, `/shell`, `/retry`, `/reload` | actions in C01 (no navigation), or `ChatPagesRoute.Export` for `/export` |

### D. Project: files, review, Git, terminal, services (`:feature:project`)

| # | Flutter | Kind | Native home | State holder | Entry points | Rules and decisions | Rows | Slice |
|---|---|---|---|---|---|---|---|---|
| D01 | `ProjectHub` / `openProjectFiles` / `openProjectTool` (`project_hub_screen.dart`) | page | `ProjectRoute.Files(profileId, projectDir?)` (Top; list pane) | `FilesHubViewModel` | chat project chip; rail Files (medium+); Ctrl+2; Search / Go to "Files"; `/files` | project = last used for a chat (`oc.lastProject.<profile>`), never a temp folder; tools in a fixed order; Changes and other tools are **visible rows** (obvious options); reselecting Files pops to the hub | SCREENS-NAV-63, B-NAV-026/044, E2E-14/20 | 5 |
| D02 | `FilesScreen` + `files/*` (browse, name / symbol / text search) | page | `ProjectRoute.Folder(profileId, projectDir, path)` (list pane); search is an in-page mode of D01/D02 | `FolderViewModel` | folder rows; breadcrumbs (tap = `popTo`) | folders as routes give native predictive back per level; shared storage asks All files access first and never shows a blocked folder as empty (#87); visible "more" button per row | SCREENS-NAV-64 | 5 |
| D03 | `showFilePreviewSheet(Loading)` + viewers (`widgets/file_preview.dart`, `pdf_file_preview.dart`, `svg_file_preview.dart`, `delimited_file_preview.dart`) | sheet | `ProjectRoute.FileView(profileId, projectDir?, path, line?)`: a page on compact, the detail pane on expanded (O-07-3) | `FileViewViewModel` | file rows; path chips in replies; tool output "Show all" | Attach / More (Find, Copy, Wrap, Show source, Save to device, Copy path, Open in Files, Open in Review); PDF via the isolated service | SCREENS-NAV-64, B-NAV-028, E2E-14 | 5 |
| D04 | row actions sheet | sheet | local sheet of D02 | D02 | the row's visible "more" | — | SCREENS-NAV-64 | 5 |
| D05 | `showFileShareSheet` (Wi-Fi share) | sheet | local sheet of D03 | D03 | FileView More | stops on close, Stop, or 15 min | SCREENS-NAV-64/70 | 5 |
| D06 | `SharedStorageAccessFlow` | flow | `ActivityResults` + `PermissionGate` service (`:android:platform`) with a kit confirm in D02 | D02 | opening a shared-storage project | never at start-up | SCREENS-NAV-64 | 5 |
| D07 | `ReviewWorkspace` / `pushWorkingTreeReview` | page | `ProjectRoute.Review(profileId, projectDir, scope)` | `ReviewViewModel` | Changes row; changed-files rows under a turn; `/diff` | unified vs side-by-side from width; comment sheet local with `KitDraft`; hands the formatted prompt to the chat composer through `:engine:composer` | SCREENS-NAV-65, B-NAV-053 | 5 |
| D08 | `DiffPage` | page | `ProjectRoute.Diff(profileId, source)` | `DiffViewModel` | C36 files; changed-file rows | "Change 1 of N"; N / P / F7 | SCREENS-NAV-65 | 5 |
| D09 | `showGitSheet` (`lib/ui/git/*`) | sheet | `SheetKey.Git(profileId, projectDir)` | `GitSheetViewModel` | chat branch chip; Files hub; Go to | plain Git words; GitHub device flow; half-done banner | SCREENS-NAV-66, B-NAV-020/021/022/023, E2E-15 | 5 |
| D10 | `TerminalPage` / `TerminalScreen` (`terminal_screen.dart`, `terminal/*`) | page | `ProjectRoute.Terminal(profileId, projectDir?)` | `TerminalViewModel` | Ctrl+Backquote; `/terminal`; Files hub icon; Go to "Terminal" | one live shell; tabs from two; visible overflow instead of long-press; source line switches to `PhoneRoute.LocalTerminal` with `replaceTop` | SCREENS-NAV-67, E2E-16 | 5 |
| D11 | `ProjectHealthScreen` | page | `ProjectRoute.Health` | `HealthViewModel` | Files hub row; `/health`; enable flow `projectGitInit` | "Initialize Git repository?" confirm | SCREENS-NAV-69 | 5 |
| D12 | `WorktreesScreen` | page | `ProjectRoute.Worktrees` | `WorktreesViewModel` | Files hub row | pinned New worktree | SCREENS-NAV-69 | 5 |
| D13 | `DevelopmentServicesScreen` | page | `ProjectRoute.Services` | `ServicesViewModel` | Files hub row | editor and logs sheets local; polls 5 s only while shown | SCREENS-NAV-69 | 5 |
| D14 | `ManagedWorkspacesScreen` | page | `ProjectRoute.Workspaces` | `WorkspacesViewModel` | Files hub row; `/workspaces` menu | gated `managedWorkspaces` | SCREENS-NAV-69 | 5 |
| D15 | `ProjectsScreen` | page | `ProjectRoute.Projects(profileId)` | `ProjectsViewModel` | Go to "Projects"; enable flow `projectChoose` | "Current" mark never colour only; rename has a visible menu | SCREENS-NAV-69, B-NAV-011 | 5 |
| D16 | `FolderBrowserSheet`, `RemoteFolderSheet`, `ProjectFolderActions` | sheet | `SheetKey.OpenProject(profileId, purpose)` | `OpenProjectViewModel` | B03; B07; D01 with no project; D15 | first page = New project, Search, Opened before (memory `obvious-options-first`); browsing is a quiet link at the bottom | SCREENS-NAV-69 | 3 |

### E. Servers and connection (`:feature:servers`)

| # | Flutter | Kind | Native home | State holder | Entry points | Rules and decisions | Rows | Slice |
|---|---|---|---|---|---|---|---|---|
| E01 | `ServersScreen` (+ `servers/*`, welcome, `PhoneSetupWelcomeEntry`) | page | `ServersRoute.Servers` (root when no profile or re-entry; pushed from Settings) + `ServersArrival` | `ServersViewModel` | `RootDecision`; Settings › Server; launch `connect`; switcher "Add a server" | as root: brand + menu (About, Report a problem, Setup guide); welcome "this phone" vs "my computer"; every row opens its own server page; the phone's rows lead; a failed connect shows inline in the verdict style; forget = confirm + profile sweep | SCREENS-NAV-71, B-NAV-017/018/040/041, E2E-01, E2E-35 | 2 |
| E02 | profile editor (`servers/profile_editor*.dart`, 7 files) | page | `ServersRoute.Editor(mode)` | `EditorViewModel` | E01 Add / Edit / Credentials; enable flows `addServer*`; connection details | steps are in-page state (Back steps back, §3.4); auto-test after 800 ms; certificate trust sheet (`showKitServerTrust`) local; plain-HTTP consent; save never connects without success; password never echoed | SCREENS-NAV-72, B-NAV-006/016, E2E-01/02/04 | 2 |
| E03 | `PairingScannerScreen` | page | `ServersRoute.PairingScanner` | `PairingScannerViewModel` | E02 "Scan" | CameraX + zxing-cpp (Google-free, memory `freeze-owner-decisions`); payload goes to `:engine:profiles` `PairingInbox` (memory only, `Redacted`), **never** a route result; "Paste instead"; no scan on desktop | SCREENS-NAV-72, E2E-02 | 2 |
| E04 | `ServerPageScreen` / `openServerPage` (`settings/server_page.dart`) | page | `ServersRoute.ServerPage(profileId)` | `ServerPageViewModel` | E01 rows; By server rows; `/status`; switcher row ⋯ | pinned to its profile (SC11); Connection / In this app / On the server; every action names the server ("Disconnect from Laptop"); plugins section in place | SCREENS-NAV-75/93, B-NAV-068/069, E2E-22 | 2 (server-owned rows 4–5) |
| E05 | `ConnectionCheckScreen` | page | `ServersRoute.ConnectionCheck(profileId)` | `ConnectionCheckViewModel` | E04 Connection | five steps run on open; no server text | SCREENS-NAV-75 | 2 |
| E06 | `ReachComputerScreen` | page | `ServersRoute.ReachComputer` | none (static) | E05 (only entry today [R grep]) | Tailscale first, SSH tunnel second, never a public tunnel | SCREENS-NAV-76 | 4 |
| E07 | `HostManagementScreen` | page | `ServersRoute.HostSetup(profileId)` | `HostSetupViewModel` | E04 | copyable commands; the app never runs them; host update restart dialog stays local to E04 | SCREENS-NAV-76 | 4 |
| E08 | `ServerCapabilitiesScreen` | page | `ServersRoute.Capabilities(profileId)` | `CapabilitiesViewModel` | "N settings aren't available on this server – Why"; Tools hub; gate fallback (§2.8) | server vs device sources | SCREENS-NAV-76, B-NAV-081 | 4 |
| E09 | `TailscaleSetupScreen` | page | `ServersRoute.Tailscale` | `TailscaleViewModel` | E02 "other ways in"; enable flow `tailscaleSetup` | returning a URL does not probe, save or sign in | SCREENS-NAV-76 | 4 |
| E10 | `showSessionAddressSheet`, `showSessionHandoff` | sheet | `SheetKey.SessionAddress(linkTicket)` | `SessionAddressViewModel` | `EntryRouter` (address links) | asks before every step that reaches out; a new link replaces the sheet | SCREENS-NAV-16, B-NAV-061/062, E2E-25 | 5 |
| E11 | `openMonitoredRequest` switch dialog | dialog | `SheetKey.SwitchServer(profileId, reason)` | `SwitchServerViewModel` | `EntryRouter` | revalidates profile, location and request; lands on the request card | SCREENS-NAV-18/36 | 4 |
| E12 | `PhoneServerCard`, `LocalServerRow`, `TermuxRunningServerEntry`, `LocalAgentServerEntry` | part | rows inside E01 and F01 (kit rows) | E01 / F01 | — | one row per server, never twice (`shownAsPhoneRow`); Termux start/stop rows dropped under D7 | SCREENS-NAV-74 | 3 |
| E13 | `queued_prompt_move_sheet.dart` | sheet | `SheetKey.QueuedPromptMove(fromProfileId)` | see C26 | E01 row; chat notice | — | SCREENS-NAV-77 | 3 |

### F. This phone (`:feature:phone`, Android only)

| # | Flutter | Kind | Native home | State holder | Entry points | Rules and decisions | Rows | Slice |
|---|---|---|---|---|---|---|---|---|
| F01 | `ThisPhoneScreen` / `openThisPhone` (+ `this_phone/*`) | page | `PhoneRoute.ThisPhone` | `ThisPhoneViewModel` | Settings › Server › This phone; E01 phone row; launch `linux`; Status widget Linux line; enable flow `serverGeneration` (host = this phone) | ordered by likelihood; every act names its object ("Stop the server on this phone"); each optional tool has its own "Remove <tool>" with measured space | SCREENS-NAV-78, BUILTIN-LINUX-44, E2E-18 | 3 |
| F02 | `PhoneSetupStartScreen` (+ `SetupCustomizeSheet` local) | page | `PhoneRoute.SetupStart(addTools)` | `SetupStartViewModel` | welcome "this phone"; F01 Add tools; enable flow `phoneSetup` | one promise, one filled button; numbers from the registry | SCREENS-NAV-79, E2E-17 | 3 |
| F03 | `PhoneSetupProgressScreen` | page | `PhoneRoute.SetupProgress` | `SetupProgressViewModel` | F02 start; launch `phone_setup`; Reset Linux | job runs in `SetupService`; "Safe to leave"; Pause / Continue; Details holds steps, log, Report | SCREENS-NAV-79, E2E-17 | 3 |
| F04 | `PhoneSetupReadyScreen` | page | `PhoneRoute.SetupReady` | `SetupReadyViewModel` | F03 done; launch `phone_setup_done` | one name makes a project, then `land(then = Chat(ref))` with `OpenedAs.FirstRun`; haptic done | SCREENS-NAV-79 | 3 |
| F05 | `PhoneSetupTermuxScreen`, `PhoneSetupTermuxJobScreen` | page | **dropped** (D7 migrate-out only) | — | — | §6.1 X1 | SCREENS-NAV-79 | — |
| F06 | `PhoneSetupUnsupportedScreen` | page | **dropped**: off Android the phone routes are not registered | — | — | §6.1 X4 | — | — |
| F07 | `LinuxManagerScreen` / `openLinuxManager` | page | `PhoneRoute.Linux` | `LinuxHubViewModel` | F01 "Linux" | Repair asks first; Reset Linux confirm (never red) | SCREENS-NAV-80, BUILTIN-LINUX-40/43 | 3 |
| F08 | `LinuxProcessesPage` + `RunningOnPhoneScreen` | page | **merged**: `PhoneRoute.Running` (owner-approved S8, memory `linux-manager-plan`) | `RunningViewModel` | F01 "Running on this phone"; F07 | refresh 10 s only while shown; stop with confirm; protected rows never offered | SCREENS-NAV-78/80, BUILTIN-LINUX-11/40, E2E-20 | 3 |
| F09 | `LinuxPortsPage` | page | `PhoneRoute.LinuxPorts` | `LinuxPortsViewModel` | F07 | a port is listed only if a loopback connect succeeds; previewable rows open `ProjectRoute.DevPreview` | BUILTIN-LINUX-40 | 3 |
| F10 | `LinuxStoragePage` | page | `PhoneRoute.LinuxStorage` | `LinuxStorageViewModel` | F07 | cleanups only inside allowed roots | BUILTIN-LINUX-41, E2E-19 | 3 |
| F11 | `LinuxPackagesPage` + `LinuxJobPanel` | page | `PhoneRoute.LinuxPackages` | `LinuxPackagesViewModel` | F07 | the job panel survives leaving; named Stop | BUILTIN-LINUX-42 | 3 |
| F12 | Linux services pages (branch `feat/linux-services-ui-2026-10-31`) | page | `PhoneRoute.LinuxServices`, `LinuxService(name)`, `LinuxServiceAdd` | one VM each | F07 | port straight from the branch per ADR D16 | BUILTIN-LINUX-45/46 | 3 (D16) |
| F13 | Linux backups (branch `feat/linux-backup-2026-11-01`) + `ProjectExportScreen` in-app use | page | `PhoneRoute.LinuxBackups` ("Projects only" = the former Export projects) | `BackupsViewModel` | F07; F01; Reset "Back up first" | owner-approved move of Export into Backups | BUILTIN-LINUX-48/59 | 3 (D16) |
| F14 | `LocalTerminalView` | page | `PhoneRoute.LocalTerminal` | `LocalTerminalViewModel` | F01 "Open a terminal"; D10 source line (`replaceTop`) | works while the server is stopped | SCREENS-NAV-68, BUILTIN-LINUX-50 | 3 |
| F15 | `openKeepRunningScreen` / `KeepRunningSection` | page | `PhoneRoute.KeepRunning` | `KeepRunningViewModel` | Settings › Background summary row; enable flow `allowBackground`; `Notifications(section = keep-running)` target | steps ordered by what is left; "You're set"; Android's 6 h / 24 h stated plainly | SCREENS-NAV-92, ANDROID-PLATFORM-44 | 4 |
| F16 | `TermuxMigrationScreen` / `openTermuxMigration` + `TermuxMigrationRow` / `Offer` | page | `PhoneRoute.TermuxMigration` | `TermuxMigrationViewModel` | E01 migration row; F01 | never deletes Termux; resumable | SCREENS-NAV-81, BUILTIN-LINUX-63 | 3 |
| F17 | `TermuxProcessesScreen`, `TermuxStorageScreen`, `showTermuxOtherAppsSheet`, `TermuxRunawayWatcher` / `WorkRunawayNotice` | page/sheet | **dropped** (D7) | — | — | §6.1 X1 | SCREENS-NAV-81 | — |
| F18 | `LocalAgentScreen` (+ `LocalAgentOnboardingBlock`, `showLocalAgentProjectSheet`) | page | **merged** into `AgentsRoute.Hub` (§6.2 M3) | — | — | — | SCREENS-NAV-82 | — |
| F19 | `ManagedServerRecoveryOption` | part | row in F01 | F01 | — | Termux-only today: dropped with D7; the in-app server's recovery is native (BUILTIN-LINUX-20) | SCREENS-NAV-83 | 3 |

### G. Agents (`:feature:agents`)

| # | Flutter | Kind | Native home | State holder | Entry points | Rules and decisions | Rows | Slice |
|---|---|---|---|---|---|---|---|---|
| G01 | `AgentsHubScreen` + `AgentsScreen` + `AgentsSection` (+ F18) | page | **merged**: `AgentsRoute.Hub` | `AgentsHubViewModel` | Settings › Agent › Agents; F01 agents row; enable flow `addToolClaude` | every agent this phone can run + state + the one act needed (install, sign in, phone check, resume); "Cards from agents" switch row | SCREENS-NAV-84, E2E-31 | 3 |
| G02 | `showAgentSheet` (steps list / setup / check / signIn) + `AgentPhoneCheckView` | sheet | `SheetKey.Agent(agentId, step, signInAgain)` | `AgentSheetViewModel` | G01 rows; B07 agent cards; Conversations agent notices | one sheet; each step replaces the frame in place | SCREENS-NAV-84 | 3 |
| G03 | `AgentSignInTerminalScreen` | page | `AgentsRoute.SignInTerminal(agentId)` | `SignInTerminalViewModel` | G02 sign-in step | opens Claude's sign-in page only through the exact-URL check; holds the sign-in foreground claim | SCREENS-NAV-84, ANDROID-PLATFORM-16, E2E-31 | 3 |
| G04 | `AgentAccountScreen` / `AgentAccountPanel` | page | `AgentsRoute.Account(profileId)` | `AgentAccountViewModel` | E04 (Codex); enable flow `modelSignIn` (agent account) | pinned to the profile it opened for | SCREENS-NAV-84, E2E-29 | 5 |
| G05 | `AgentStatusNotices`, `CardsFromAgentsRow` | part | parts of B01 and G01 | B01 / G01 | — | each action names its target | SCREENS-NAV-84 | 3 |
| G06 | `ExternalAgentsScreen`, `ExternalAgentDetailScreen`, `ExternalTaskScreen` / `openExternalAgents` | page | **dropped** (ADR D6 default: A2A dropped) | — | — | §6.1 X2 | SCREENS-NAV-85 | — |

### H. AI Team (deferred, ADR D6)

Everything in SCREENS-NAV-86–89 waits for the AI Team phase after the switch: `TeamPage`, `TeamHomeScreen`,
`TeamIntroScreen`, `PhoneTeamOffPage`, `TeamSettingsScreen`, `TeamAgentsScreen`, `RoleScreen`, `AgentScreen`,
`TeamBoardScreen`, `TeamRigScreen`, `TeamMigrationScreen` / `TeamMigrationGate`, `TeamProjectsScreen` (+ overview,
board, timeline, servers card), `TeamProjectConversation`, the project editors (`openTeamNewProject`, spec, plan,
settings, roles, defaults), `TeamConversation` / `TeamConversationScreen`, `TeamWatchLiveScreen`, watching mode,
`TeamPhoneSetupScreen`, `TeamPhoneReadyScreen` / `openTeamOnThisPhone`, `TeamProjectDemoScreen`, and the sheets
`showGateSheet`, `showWorkSheet`, `showTeamTaskDetails`, `showStartRunSheet`, `showTeamBoardMoveSheet`,
`showTeamBoardPrioritySheet`, `showTeamHostSheet`, `showTeamHostGuideSheet`, `showTeamTurnOffSheet`,
`showTeamModelSheet`, `showTeamPhoneTipsSheet`, `showTeamHostDetailsSheet`.

Until then: no Team door anywhere (absent, never disabled); `opencode-mobile://team/...` links and `team_*`
notification kinds still route (§7.6, §7.8); Team data on disk stays untouched (ADR D6). Routes will be
`TeamRoute.*` in `TeamRoutes.kt`, owned by `:feature:team`, with Team projects as a three-pane scene on large windows.

### I. Settings (`:feature:settings`)

| # | Flutter | Kind | Native home | State holder | Entry points | Rules and decisions | Rows | Slice |
|---|---|---|---|---|---|---|---|---|
| I01 | `SettingsScreen` (hub, search, `SettingsServersBlock`) | page | `SettingsRoute.Hub` (Top; list pane) + `HighlightArrival` | `SettingsHubViewModel` | gear (compact); rail Settings; Ctrl+,; Ctrl+3 | search field + five one-word groups; only Server has a value line; "N settings aren't available on this server – Why"; with several servers "You can change some of these for one server" block | SCREENS-NAV-90, B-NAV-042/068/070/071/072/073, E2E-22 | 4 |
| I02 | `SettingsGroupScreen` (`settings/settings_group_page.dart`) | page | `SettingsRoute.Group(group)` (detail pane) | `SettingsGroupViewModel` | I01 rows; search results | rows per `settingsGroupRowIds` (kept as the test tags); a group with no rows has no door | SCREENS-NAV-90 | 4 |
| I03 | `ByServerScreen` | page | `SettingsRoute.ByServer(kind)` | `ByServerViewModel` | I02 "By server" rows; Privacy | personal choices everywhere; owned things with their named owner (memory `settings-scope-model`) | SCREENS-NAV-90, B-NAV-068 | 4 |
| I04 | `AppearanceSettingsScreen` (+ `appearance_one_page.dart`, `showThemePackPreview` local, `GlassChoiceControl`) | page | `SettingsRoute.Appearance` | `AppearanceViewModel` | I02 This app; `/themes`; tour `make-it-yours` | guided first visit, then one page; every choice applies at once; Saver holds Style and Background; **glass control dropped** (no glass); Background question absent until D11 | SCREENS-NAV-91, B-KIT-040/059, E2E-23/38 | 4 |
| I05 | `NotificationsSettingsScreen` (page all / notifications / background, section slugs) | page | `SettingsRoute.Notifications(page, section)` | `NotificationsViewModel` | I02; enable flow `allowNotifications`; launch `background_settings`; Pause tile long-press (§7.11) | quiet hours and Wi-Fi only defined once; keep-running is a summary row that opens `PhoneRoute.KeepRunning` (`section = keep-running` opens it directly); `section = automation` opens `PermissionsAutomation` | SCREENS-NAV-92, B-NAV-050, E2E-08/09 | 4 |
| I06 | `PermissionsAutomationScreen` + `AutomationSettingsSection` | page | `SettingsRoute.PermissionsAutomation` | `PermissionsAutomationViewModel` | I02 Conversations | AI Team supervision block absent until Team | SCREENS-NAV-92/93 | 4 |
| I07 | `SavedPermissionsScreen` | page | `SettingsRoute.SavedPermissions(profileId, projectDir?)` | `SavedPermissionsViewModel` | I06; E04; permission sheet's Always-allow explanation | revoke = destructive confirm | SCREENS-NAV-111 | 4 |
| I08 | `PrivacySettingsScreen` | page | `SettingsRoute.Privacy` | `PrivacyViewModel` | I02 This app | unsent work with size and delete; policy opens `SheetKey.PrivacyPolicy` | SCREENS-NAV-93, E2E-34 | 4 |
| I09 | `showLanguageSheet` / `LanguageSettingsTile` | sheet | local sheet of I02 | I02 | This app › Language | system / en / ar / zh / es / pt / ja / ru; English leads the fallback list (fedd85d8e) | SCREENS-NAV-93, B-L10N-001/008/009, E2E-23 | 4 |
| I10 | `PhraseChipsSettingsRow`, `TranscriptDisplayToggles`, `DefaultShellRow` (sheet) | part/sheet | switch rows in I02 Conversations; default shell = local sheet, loaded lazily | I02 | — | display toggles with Undo | SCREENS-NAV-93 | 4 |
| I11 | `VoiceSettingsScreen` (`lib/voice/voice_ui.dart`) + `showVoiceAutomaticSetupSheet` | page | `SettingsRoute.Voice` (automatic setup sheet local) | `VoiceSettingsViewModel` | I02 Conversations | Natural voice rows; hidden where read aloud does not exist | MEDIA-VOICE-21/22 | 5 |
| I12 | `AiSetupScreen` | page | `SettingsRoute.AiSetup(profileId)` | `AiSetupViewModel` | E04 On the server | review only (owner decision 2026-09-28) | SCREENS-NAV-93 | 5 |
| I13 | `IntegrationsScreen(mode: providers)` (+ key dialog, OAuth sheet, device-code dialog, credential sheet, pending-auth recovery) | page | `SettingsRoute.Providers(profileId, connectProviderId?)` | `ProvidersViewModel` | I02 Agent; E04; `/connect`; enable flow `modelSignIn`; launch `usage_target:add_key` | keys never echoed; authorization through the link policy | SCREENS-NAV-95, B-NAV-047, E2E-37 | 5 |
| I14 | `CapabilitiesScreen` / `openTools` (tabs MCP, Commands, Tools, Skills, References, External agents) | page | `SettingsRoute.Tools(profileId, section)`; MCP tab body = the former `IntegrationsScreen(mode: mcp)`; **External agents tab dropped** | `ToolsViewModel` (one per tab body, created on first open) | I02 Agent; `/mcps`, `/tools`, `/skills`, `/references`; enable flow `mcpAdd` | each tab built on first open and kept | SCREENS-NAV-94, B-NAV-076 | 5 |
| I15 | `showMcpAddSheet`, `McpCatalogScreen`, `McpSetupScreen` | sheet/page | add sheet local to I14; `SettingsRoute.McpCatalog(profileId)`; `SettingsRoute.McpSetup(profileId)` | `McpCatalogViewModel`, `McpSetupViewModel` | I14 MCP tab | loopback OAuth callback completes sign-in | SCREENS-NAV-95 | 5 |
| I16 | `CommandsScreen`, `ToolsScreen`, `SkillsScreen` (+ skill activation sheet), `ReferencesScreen` | page (embedded) | tab bodies of I14 | per tab | I14 | Commands pick → `SheetKey.RunCommand` | SCREENS-NAV-94 | 5 |
| I17 | `ServerPluginsSection` | part | section of E04 | E04 | — | refresh on the page bar | SCREENS-NAV-93 | 5 |
| I18 | `ManageSpaceScreen` + `ProjectExportScreen` (`lib/manage_space_main.dart`) | page | `ManageSpaceActivity`: `ManageSpaceRoute.Home`, `ManageSpaceRoute.ExportProjects` | `ManageSpaceViewModel`, `ExportProjectsViewModel` | Android app info › Storage › Manage space | reads plain preferences only; Delete everything after explicit confirm | SCREENS-NAV-101 | 4 |
| I19 | Keyboard shortcuts row; Try the demo row | row | rows in I02 Help (shortcuts row only when a hardware keyboard has been seen, or desktop) | I02 | — | opens `SheetKey.ShortcutsHelp` / `ChatRoute.Demo` | SCREENS-NAV-93 | 5 |

### J. Usage (`:feature:usage`)

| # | Flutter | Kind | Native home | State holder | Entry points | Rules and decisions | Rows | Slice |
|---|---|---|---|---|---|---|---|---|
| J01 | `UsageHubScreen` (+ `UsagePlansPage`, `UsageScreen` spending, `UsageRefreshSlot`) | page | `UsageRoute.Hub(section)` | `UsageHubViewModel` | I02 Agent; launch `usage`; quota notifications; enable flow `quotaCollectorGuide` | Plans most urgent first, bars show what is left, never red; Spending tab absent when unsupported (then no tab strip); one Refresh | SCREENS-NAV-96, B-NAV-078/079, E2E-33 | 5 |
| J02 | `UsageSubscriptionScreen` | page | `UsageRoute.Subscription(key)` | `UsageSubscriptionViewModel` | J01 plan rows | exact resets; details folded | SCREENS-NAV-96 | 5 |
| J03 | `UsageAddPickerScreen` + `UsageAddScreen` | page | **merged**: `UsageRoute.Add(profileId?)` with the connection choice as its first step (§6.2 M4) | `UsageAddViewModel` | J01 "Add"; sign-in helpers | — | SCREENS-NAV-96 | 5 |
| J04 | `UsageReadFromScreen` | page | `UsageRoute.ReadFrom(profileId?, providerId?)` | `UsageReadFromViewModel` | J01; launch `usage_target:set_up` | one-shot read-only check per refresh | SCREENS-NAV-96, B-NAV-077 | 5 |
| J05 | `UsageAlertsScreen` | page | `UsageRoute.Alerts` | `UsageAlertsViewModel` | J01 | quiet hours and Wi-Fi only are the shared values | SCREENS-NAV-96 | 5 |
| J06 | `UsageWidgetScreen` | page | `UsageRoute.WidgetSettings` | `UsageWidgetViewModel` | J01; launch `usage_settings` | feeds the Status widget plan line | SCREENS-NAV-97, B-NAV-080 | 5 |
| J07 | budget set / clear dialogs | dialog | local dialogs of J01 | J01 | Spending | — | SCREENS-NAV-96 | 5 |

### K. Scheduled prompts (`:feature:schedules`)

| # | Flutter | Kind | Native home | State holder | Entry points | Rules and decisions | Rows | Slice |
|---|---|---|---|---|---|---|---|---|
| K01 | `ScheduledPromptsScreen` / `openScheduledPrompts` / `openStoredScheduledPrompts` | page | `SchedulesRoute.List` (+ `HighlightArrival(scheduleId)`) | `SchedulesViewModel` | Go to "Scheduled prompts"; Settings search; scheduled-run notification fallback | calm rows; one mint "New scheduled prompt" at the thumb | SCREENS-NAV-98, B-NAV-054/055, E2E-27 | 4 |
| K02 | `showScheduleSheet` (+ `KitWheel`, history) | sheet | `SheetKey.Schedule(scheduleId?, ref?)` | `ScheduleSheetViewModel` | K01 rows; C27 | one main action naming the moment ("Schedule for 9:00 AM"); typed prompt kept as a draft | SCREENS-NAV-98 | 4 |

### L. About, diagnostics, help (`:feature:about`)

| # | Flutter | Kind | Native home | State holder | Entry points | Rules and decisions | Rows | Slice |
|---|---|---|---|---|---|---|---|---|
| L01 | `AppDiagnosticsScreen` / `openReportProblem` / `openBugReport` / `openFailedJobReport` (route `/debug`) | page | `AboutRoute.ReportProblem(problemId?, jobId?)` | `ReportProblemViewModel` | every error state's "Report a problem"; Help; `/debug`; Search "crash"; safe mode | the failure is recorded in `:engine:diagnostics` first and passed by id, never as text; draft `oc.draft.report.app` | SCREENS-NAV-102, B-NAV-015/045, E2E-34 | 4 |
| L02 | `AppDiagnosticsKeptScreen` (+ `PerfTraceSection`, `ExitHistorySection`, `CrashReportsSection`) | page | `AboutRoute.Kept` | `KeptViewModel` | L01 | problem exits lead; Performance report kept (owner uses it) | SCREENS-NAV-102, B-NAV-012 | 4 |
| L03 | `openCrashReportShare` | sheet | local sheet of L02 | L02 | Share | shows the exact text; only an explicit Share hands it over | SCREENS-NAV-102 | 4 |
| L04 | `AboutScreen` (+ notices) | page | `AboutRoute.About` and `AboutRoute.Notices` | `AboutViewModel` | I02 Help; Servers root menu | open-source notices from bundled files | SCREENS-NAV-103, B-NAV-001 | 4 |
| L05 | `GuideScreen` | page | `AboutRoute.Guide` | none (static + capability) | I02 Help; Servers root menu; Go to "Help" | leads with `opencode2 pair`; Advanced fold | SCREENS-NAV-76/103 | 4 |
| L06 | `TipsScreen` | page | `AboutRoute.Tips` | `TipsViewModel` | I02 Help | every teaching entry with "Show me"; "Show tips again" | SCREENS-NAV-25, B-NAV-074, E2E-38 | 4 |
| L07 | `showPrivacyPolicy` | sheet | `SheetKey.PrivacyPolicy` | none | I08; L04 | bundled `PRIVACY.md` (rewritten before the switch, ADR §9.6) | SCREENS-NAV-93 | 4 |

### M. Widget overlay panels (`:feature:widget-panels`, `WidgetOverlayActivity`)

| # | Flutter | Kind | Native home | State holder | Entry points | Rules and decisions | Rows | Slice |
|---|---|---|---|---|---|---|---|---|
| M01 | `WidgetConfigureScreen` (`/widgets/configure`) | panel | `WidgetPanel.Style(widgetId)` | `WidgetStyleViewModel` | widget Edit (Status), configure | ground and accent; Save styles that one widget; the host closes itself | SCREENS-NAV-100, ANDROID-PLATFORM-25 | 4 |
| M02 | `WidgetProjectScreen` (`/widgets/project`) | panel | `WidgetPanel.Project(widgetId)` | `WidgetProjectViewModel` | Conversations widget Edit | All projects first; one tap saves and closes | SCREENS-NAV-100 | 4 |
| M03 | `WidgetSteerScreen` (`/widgets/steer`, `/widgets/voice`) | panel | `WidgetPanel.Steer(widgetId, voice)` | `WidgetSteerViewModel` | widget steer / voice buttons | steer mode from the snapshot; voice starts dictation at once | SCREENS-NAV-100, ANDROID-PLATFORM-26 | 4 |

The overlay activity reads only display preferences, never secrets (ANDROID-PLATFORM-25). It has no back stack: Back
and a tap on the ground close the activity.

### N. FactsBook (deferred, ADR D6, behind `oc.dev.factsbook`)

`FactsBookAboutYouScreen`, `AreasScreen`, `CaptureScreen`, `ConnectScreen`, `EventScreen`, `MemoryCareScreen`,
`MemorySection` (on Conversations), `NotSavedScreen`, `OfferCard`, `PhoneNodeScreen`, `showFactsBookReminderCheck`,
`RemindersScreen`, `RuleEditorScreen`, `SearchScreen`, `SettingsScreen`, `StatusScreen`, `ThingScreen`,
`TriggersScreen` (21 files, SCREENS-NAV-99). Ported after the switch behind the same flag, as `FactsBookRoute.*` in
`:feature:factsbook`. Its data stays on disk untouched.

### O. Kit-level overlays (used by many screens; never routes)

`showKitConfirm`, `showKitInputDialog`, `showKitAlert`, `showKitMenu`, `showKitTechnicalDetails`, `showKitTerm`,
`showKitDatePicker` / `showKitTimePicker` / `showKitDateTimePicker`, `showKitViewer` (code reader), `showKitDiff`,
`showKitImageGallery`, `showKitImageMarkup`, `showKitTaskSheet` (plan tasks), `showKitRequestSheet`,
`showKitServerTrust`, `showKitTerminalLinkSheet`, `showKitProjectFileSheet` ("File from the project"),
`showContextMenu` / `KitContextRegion`, `showKitUndo`. Each is a kit part the screen shows from its own `XUi` state.
The kit section owns their APIs; this section only requires that each one handles Back through the kit (§3.3) and
obeys "one sheet at a time".

---

## 6. Dropped, merged and deferred

### 6.1 Dropped

| # | What | Why | Decided by |
|---|---|---|---|
| X1 | Termux management: `PhoneSetupTermuxScreen`, `PhoneSetupTermuxJobScreen`, `TermuxProcessesScreen`, `TermuxStorageScreen`, `showTermuxOtherAppsSheet`, `TermuxRunawayWatcher`, Termux start/stop rows, `ManagedServerRecoveryOption`, enable flow `phoneSetupTermux`, `ThisPhone(kind = termux)` | The built-in Linux replaced Termux for new users; only "move from Termux" stays. A Termux OpenCode server can still be added like any server by address. | ADR D7 default (migrate-out only); flips to "keep" only if the owner says so |
| X2 | Outside agents over A2A: `ExternalAgentsScreen`, `ExternalAgentDetailScreen`, `ExternalTaskScreen`, Tools "External agents" tab, enable flow `addExternalAgent` | No owner use seen; removes a third agents home. Stored identities stay on disk; their bearer secrets are still swept on profile delete. | ADR D6 default |
| X3 | "Press back again to exit" | Breaks Android's back-to-home animation; the root never intercepts Back. | ADR D5 default |
| X4 | `PhoneSetupUnsupportedScreen` | Off Android the phone routes are not registered at all (absent, never disabled). | This section (no behaviour lost) |
| X5 | `ProfileMonitorInbox` | Dead code: the class is defined but not mounted anywhere [R grep, 2026-11-01]; its rows are the Inbox's watched entries. | This section |
| X6 | `IntegrationsMode.legacyCombined` | No entry point opens it [R grep]. Providers and MCP already have their own places. | This section |
| X7 | `GlassChoiceControl` and `oc.glass*` choices | Glass styles were removed (memory `no-glass-rule`); stored values read as Solid. | Owner rule |
| X8 | "While you were away" page | No Flutter screen shows it today. Its feed rules (B-NAV-082) power the Inbox empty state's "finished, not opened yet" list, which is kept. | This section (nothing visible lost) |

### 6.2 Merged

| # | Into | From | Why (what it removes) |
|---|---|---|---|
| M1 | Shell chrome + `ChatsRoute.Conversations` | `HomeScreen` tab host (`KitTabSwitcher`) | One stack model for every window size; no hidden tab pages built at start-up; the rail is just a view of the stack. |
| M2 | `PhoneRoute.Running` | `RunningOnPhoneScreen` + `LinuxProcessesPage` | Two lists of the same processes; owner approved S8 on 2026-10-31 (memory `linux-manager-plan`). |
| M3 | `AgentsRoute.Hub` | `AgentsHubScreen` + `AgentsScreen` + `AgentsSection` + `LocalAgentScreen` | With A2A dropped and AI Team deferred, the hub and the agents list are the same rows; `LocalAgentScreen` on the built-in host already shows "the same rows Settings › Agents shows" and its Termux path goes with X1 [R `local_agent_screen.dart`]. One place to find agents. |
| M4 | `UsageRoute.Add` | `UsageAddPickerScreen` (53 lines) + `UsageAddScreen` | The picker is a first step, not a page; one fewer Back. |
| M5 | `SettingsRoute.Tools` MCP tab | `IntegrationsScreen(mode: mcp)` | It was already only embedded there [R `capabilities_screen.dart`]. |
| M6 | `PhoneRoute.LinuxBackups` | in-app `ProjectExportScreen` | Owner-approved "move Export projects into Backups" (memory `linux-manager-plan`). Manage space keeps its own Export page because it runs in a separate activity. |
| M7 | `ProjectRoute.FileView` (page / detail pane) | `showFilePreviewSheet` full-height sheet | One viewer for phone and tablet, native predictive back, survives process death. Owner choice O-07-3. |

Not merged, on purpose: server terminal (`ProjectRoute.Terminal`, `:feature:project`) and local terminal
(`PhoneRoute.LocalTerminal`, `:feature:phone`) stay two routes because the ADR puts them in different modules; they share
one kit terminal part and switch with `replaceTop`.

### 6.3 Deferred to after the switch (ADR D6, D11)

AI Team (§5 H), FactsBook (§5 N), live backgrounds and illustration scenes in Appearance (D11). Each is absent until
ported; nothing shows a disabled door.

---

## 7. Entry routing: every way into the app

### 7.1 Capturing intents in `MainActivity` (`:androidApp`)

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A restored activity (process death, recents) gets its ORIGINAL intent again: never replay it.
        val fromHistory = intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0
        if (savedInstanceState == null && !fromHistory) capture(intent)
        setContent { App(graph = (application as GraphOwner).appGraph) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        capture(intent)
    }

    private fun capture(intent: Intent) {
        val request = EntryIntentReader.read(intent)   // :android:platform; whitelists and bounds every field
        EntryIntentReader.strip(intent)                  // removes our extras, sets action MAIN, data null
        setIntent(intent)                                // a configuration change cannot replay it
        request?.let { (application as GraphOwner).appGraph.entryRouter.submit(it) }
    }
}
```

- Manifest stays as today: `singleTop`, `taskAffinity=""`, `adjustResize`, the full `configChanges` list (no
  recreation on rotation, fold or locale; Android 17 no longer restores the IME after recreation), and
  `enableOnBackInvokedCallback="true"` [R android-platform §3, V android-surfaces §9.3].
- `EntryIntentReader` reuses the Flutter-era Kotlin validators unchanged: `SessionLinkIngress.accepts` (non-empty,
  ≤ 1,024 chars, every char `0x21..0x7e`, only `opencode-mobile://session*` and `://team*`; rejected links are consumed
  too), the `LaunchShortcutChannel` whitelist and regexes, `ScheduledPromptPolicy.validId`.
- Requests submitted before the graph is ready wait inside `EntryRouter` (it lives in `AppScope`); there is no "Dart
  ready" handshake any more.

### 7.2 Types (`:engine:entry`, `commonMain`)

```kotlin
public sealed interface EntryRequest {
    public val channel: EntryChannel
    public val receivedAt: Instant

    public data class Launch(val action: LaunchAction, override val receivedAt: Instant) : EntryRequest { override val channel = EntryChannel.Shortcut }
    public data class PinnedSession(val profileId: ProfileId, val sessionId: SessionId, override val receivedAt: Instant) : EntryRequest { override val channel = EntryChannel.Shortcut }
    public data class Link(val raw: String, override val receivedAt: Instant) : EntryRequest { override val channel = EntryChannel.Link }
    public data class SharedText(val text: String, override val receivedAt: Instant) : EntryRequest { override val channel = EntryChannel.Share }      // subject + body joined, trimmed, ≤ 8,000 chars; held in memory only
    public data class AlertOpen(val kind: AlertKind, val profileId: ProfileId?, val sessionId: SessionId?, val monitorToken: String?, val reply: Boolean, override val receivedAt: Instant) : EntryRequest { override val channel = EntryChannel.Alert }
    public data class ScheduledOpen(val profileId: ProfileId, val scheduleId: String, val sessionId: SessionId?, override val receivedAt: Instant) : EntryRequest { override val channel = EntryChannel.Schedule }
    public data class TilePreferences(val tile: TileId, override val receivedAt: Instant) : EntryRequest { override val channel = EntryChannel.Shortcut }
}

public enum class EntryChannel { Shortcut, Link, Share, Alert, Schedule }

public sealed interface LaunchAction {
    public data object Connect : LaunchAction
    public data object NewTask : LaunchAction
    public data object Activity : LaunchAction
    public data object PhoneSetup : LaunchAction
    public data object PhoneSetupDone : LaunchAction
    public data object Usage : LaunchAction
    public data object UsageSettings : LaunchAction
    public data object Linux : LaunchAction
    public data object BackgroundSettings : LaunchAction
    public data class UsageTarget(val kind: UsageTargetKind, val providerId: String, val profileId: ProfileId) : LaunchAction // SignIn | AddKey | SetUp
}

/** Neutral targets; :ui:shell maps them to routes (ADR §4 row 26). */
public sealed interface EntryTarget {
    public data class Chat(val ref: ConversationRef, val focusComposer: Boolean, val landOn: LandOn?, val opened: Opened) : EntryTarget // LandOn: Request(id) | OldestRequest | Failure
    public data class Conversations(val filter: Filter) : EntryTarget          // None | Running
    public data object NeedsYou : EntryTarget                                 // Conversations + Inbox
    public data class Servers(val request: ServersRequestTarget?) : EntryTarget
    public data class PhoneSetup(val stage: SetupStage) : EntryTarget         // Progress | Ready
    public data object ThisPhone : EntryTarget
    public data class Usage(val section: UsageSectionTarget) : EntryTarget
    public data object UsageWidgetSettings : EntryTarget
    public data class ProviderSignIn(val profileId: ProfileId, val providerId: String) : EntryTarget
    public data class AgentSignIn(val agentId: String) : EntryTarget
    public data class UsageReadFrom(val profileId: ProfileId, val providerId: String) : EntryTarget
    public data class Settings(val section: SettingsSectionTarget) : EntryTarget // Notifications | Background
    public data class ScheduledPrompts(val highlightScheduleId: String?) : EntryTarget
    public data class SessionAddress(val linkTicket: Long) : EntryTarget
    public data class SwitchServerThen(val profileId: ProfileId, val reason: SwitchReason, val then: EntryTarget) : EntryTarget
    public data class Land(val then: EntryTarget?) : EntryTarget
}

public interface EntryRouter {
    public fun submit(request: EntryRequest)
    public val pending: StateFlow<EntryHandoff?>      // 03 S12: state plus acknowledgement
    public fun consume(handoffId: Long)
    public val waiting: StateFlow<EntryWaiting?>      // drives the one "waiting for <server>" status line
}

public data class EntryHandoff(val id: Long, val target: EntryTarget, val notice: EntryNotice?)
```

### 7.3 The rules (one mechanism replaces Flutter's seven, SCREENS-NAV §4)

1. **One slot per channel; newer wins.** `submit` replaces the channel's pending request and bumps its generation. A
   stale cold-start shortcut is never opened after a newer live one (B-ANDR-027).
2. **Evaluate on every change.** `decide(request, world)` is a pure function (table-tested) returning
   `Wait(reason)`, `Go(target, effects)` or `Drop(notice)`. It re-runs when bootstrap or the migration gate changes,
   when any connection status changes, on app resume, and on a 1 s tick only while a `ScheduledOpen` waits.
3. **Nothing before the gate.** Every request waits for `BootstrapState.Ready` and `MigrationGate.open` (ADR §9.7).
4. **Waiting vs final.** *Waiting* = a saved server is on its way and nothing has failed: keep the request and show
   one status line ("Waiting for <server>…", `KitStatusKind.work`). *Final* = no profile, re-entry needed, or the
   connection failed: consume the request with a plain notice and send the person to `Servers`.
5. **Consume once, and only if still the same.** After `Go` or `Drop`, the slot is cleared only if its generation did not
   change while effects ran, so an item that arrived meanwhile is not swallowed.
6. **Effects run in the router, guarded.** `CreateSession` checks that the profile, location and repository revision did
   not change during the create (Flutter guard); `SeedDraft` writes shared text into `:engine:composer` as the new
   conversation's draft; `Connect(profileId)` switches server first. A failed effect becomes a notice plus a fallback
   target; it never navigates to a broken page.
7. **Nothing is sent, created or resumed on the person's behalf** except where the action *is* "New task" (an empty
   session, never a message) or a share (an unsent draft).
8. **The shell acknowledges.** `EntryHandoffCollector` (in `:ui:shell`) collects `pending`, closes any sheet (N2),
   maps the target to navigator calls (§7.4), then calls `consume(id)`.
9. **Pending requests are memory-only.** A request that has not been consumed when the process dies is lost, as today.

`EntryTarget` → navigator (shell, `EntryTargetMapper`):

| Target | Navigator calls |
|---|---|
| `Chat` | `openChat(ref, ChatArrival(focusComposer, landOn, opened))` |
| `Conversations(filter)` | `selectDestination(Conversations)` + `ConversationsArrival(filter)` |
| `NeedsYou` | `selectDestination(Conversations)` + `ConversationsArrival(NeedsYou)` (Conversations opens the Inbox above itself) |
| `Servers(request)` | `open(ServersRoute.Servers)` + `ServersArrival(request)`; no push when Servers is already on top |
| `PhoneSetup(stage)` | `open(PhoneRoute.SetupProgress)` or `open(PhoneRoute.SetupReady)` (N5 dedupes) |
| `ThisPhone` | `open(PhoneRoute.ThisPhone)` |
| `Usage(section)`, `UsageWidgetSettings`, `UsageReadFrom` | `open(UsageRoute.Hub(section))`, `open(UsageRoute.WidgetSettings)`, `open(UsageRoute.ReadFrom(...))` |
| `ProviderSignIn` | `open(SettingsRoute.Providers(profileId, connectProviderId = providerId))` |
| `AgentSignIn` | `showSheet(SheetKey.Agent(agentId, SignIn, signInAgain = false))` |
| `Settings(section)` | `open(SettingsRoute.Notifications(page = Notifications or Background))` |
| `ScheduledPrompts(id)` | `open(SchedulesRoute.List)` + `HighlightArrival(id)` |
| `SessionAddress(ticket)` | `showSheet(SheetKey.SessionAddress(ticket))` |
| `SwitchServerThen` | `showSheet(SheetKey.SwitchServer(...))`; the sheet's VM resubmits `then` after the switch |
| `Land(then)` | `land()` then the mapping of `then` |

### 7.4 Launcher actions (static shortcuts, tiles, widgets, setup and status notifications; SCREENS-NAV-14)

Extra `oc.shortcut` (whitelist below, or the `usage_target` regex
`^usage_target:(sign_in|add_key|set_up):[a-z0-9][a-z0-9_-]{0,31}:[A-Za-z0-9._-]{1,64}$`).

| Action | Producers | Waits for | Final conditions | Target | Rows |
|---|---|---|---|---|---|
| `connect` | static shortcut; Status widget server lines | nothing | — | `Servers(null)` over whatever shows; connection untouched | B-ANDR-028, E2E-25 |
| `new_task` | static shortcut; Conversations widget "+" (data `opencode-widget://new-conversation/<id>`); status notification "New chat" | the active connection ready | no profile → Servers + "Add a server first"; re-entry → Servers(Credentials); failed → Servers + notice | effect `CreateSession` → `Chat(opened = NewlyCreated)` | B-ANDR-028 |
| `activity` | Attention tile; Status widget Tasks line; sessions widget | bootstrap only | no profile → Servers + notice | `NeedsYou` | B-NAV-025 |
| `usage` | Status widget plan line | bootstrap only | no profile → Servers + notice | `Usage(Plans)` | B-NAV-077 |
| `usage_settings` | usage widget empty state | bootstrap only | — | `UsageWidgetSettings` | — |
| `background_settings` | widget "Background settings" state | bootstrap only | — | `Settings(Background)`; opens the setting, never enables it | ANDROID-PLATFORM-26 |
| `usage_target:sign_in\|add_key\|set_up:<provider>:<profile>` | usage widget rows | the row's server connected (it is connected first if it is not the active one) | gone server or unknown provider → `Usage(Plans)` + notice | Claude on this phone → `AgentSignIn`; GLM / MiniMax and `add_key` → `ProviderSignIn`; `set_up` → `UsageReadFrom`; others → `ProviderSignIn` | B-NAV-077, E2E-26 |
| `linux` | Status widget Linux line | bootstrap only | not Android → dropped | `ThisPhone` | — |
| `phone_setup`, `phone_setup_done` | `SetupService` notifications | never waits for a connection | — | reads `setup.json`: running or stopped → `PhoneSetup(Progress)`; done, first setup, ready not yet shown → `PhoneSetup(Ready)`; else nothing | SCREENS-NAV-14 |
| unknown value | — | — | — | consumed, nothing happens | B-ANDR-028 |

### 7.5 Pinned-session shortcuts (SCREENS-NAV-15)

- Extras `oc.shortcut.profile`, `oc.shortcut.session`; ids `oc.session:<profile>:<session>`; at most 4; titles ≤ 80.
- Target: only for the **active** profile → `Chat(ref)` (dedupes on top). Another profile → dropped with the notice
  "That conversation is on another server" (never switches silently).
- Publication: `:android:background` republishes on every launch and on pin changes; stale ids are **disabled, not
  removed**, so user-pinned copies grey out with a reason (ANDROID-PLATFORM-30). Disconnect withdraws all; profile
  deletion clears them.
- Rows: B-ANDR-027, B-ANDR-030, E2E-25.

### 7.6 Deep links (SCREENS-NAV-16)

| Link | Parser (`:engine:entry`, ported from `lib/domain/session_handoff.dart`, `team_link.dart`, `session_address_link.dart`) | Target |
|---|---|---|
| `opencode-mobile://session?profile=<P>&session=<S>` | ids by `isSafeIdentifier`; exact shape only; credentials never stored | same saved server active → `Chat(ref)`; another saved server → effect `Connect`, then `Land(then = Chat(ref))`; unknown profile → `SessionAddress(ticket)` sheet on its "Add server" step (the link carries no address, so it leads to `ServersRoute.Editor(Add)` and keeps the link until the server is added and signed in); re-entry needed → `Servers(Credentials)` + notice |
| `opencode-mobile://session/v2?...` (address links) | parsed network-silent into origin, instance UUID, session id; 21 failure categories; gated by `sessionAddressHandoff` | `SessionAddress(ticket)`; only a found existing conversation navigates; a new link replaces the open sheet |
| `opencode-mobile://team/gate/<P>/<gate>`, `.../run/<P>/<run>` | ids `^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$` | until AI Team ships: gate → `NeedsYou`; run → `Conversations(Running)`. Never answers anything. |
| anything else, > 1,024 chars, bad chars | rejected by `SessionLinkIngress` | consumed, nothing happens |

Rows: B-NAV-061, B-NAV-062, B-NAV-063, B-NAV-064, B-ANDR-071, B-ANDR-073, E2E-25.

### 7.7 Share in and share out (SCREENS-NAV-17)

- **In** (switch release): `SEND text/plain` only, as today. Subject joined as the first line unless the body already
  starts with it. Waits for a connected, non-loading server (one "waiting for server" line). Effects: `CreateSession` in
  the active project → `SeedDraft(text)` → `Chat(focusComposer = true)`. The text sits **unsent** in the composer.
- **Failure**: status line with **Try again**; More = Copy / Discard (Discard has Undo; the text is dropped only when the
  Undo bar goes). A second failure says so. With no server: "Connect to a server and the shared text opens in a new
  conversation". Rows: B-ANDR-075, E2E-25.
- **Images and Direct Share** are owner choice O-07-4 (default: after the switch).
- **Out**: the system chooser with `EXTRA_EXCLUDE_COMPONENTS = [MainActivity]` (never offers itself), and explicit
  `FLAG_GRANT_READ_URI_PERMISSION` + `ClipData` for any `FileProvider` URI (Android 18 stops automatic grants)
  [V android-surfaces §12].

### 7.8 Notification taps (SCREENS-NAV-18)

Body tap or Open button → `MainActivity` with the alert extras (`FLAG_ACTIVITY_CLEAR_TOP | SINGLE_TOP`).

| `CodingAlertKind` | Target |
|---|---|
| `permission`, `question` without `monitorToken` | `Chat(ref, landOn = OldestRequest)`: the alert extras carry no request id, so the chat lands on its oldest waiting request card (the one the alert was about, since cards are answered oldest first); waits for transport |
| any kind with `monitorToken` (another saved server's watch) | effect `ResolveMonitoredRequest(token)` revalidates profile, location and exact request → `SwitchServerThen(profileId, then = Chat(landOn))`; never answers from monitor metadata; does not wait for transport |
| `complete` with a profile (a widget row of another project or connection) | effect `ResolveWidgetConversation` (switch connection/project first) → found: `Chat(focusComposer = reply)`; not found: `Land(null)` (never a broken chat) |
| `complete` (same connection) | `Chat(focusComposer = reply)` |
| `error` | `Chat(landOnFailure = true)` |
| `checkin` | `Chat(ref)` |
| `quota`, `quota_reset` | effect `VerifyQuotaToken` → `Usage(Plans)`; token no longer belongs to a source → `Usage(Plans)` + notice "That plan changed" |
| `team_decision`, `team_review` | until AI Team ships: `NeedsYou` |
| `team_run_failed`, `team_completed`, `team_progress` | until AI Team ships: `Conversations(None)` |
| `test` | open the app only |

Notification **actions** (Allow once, Reject, Stop the reply, Start, Stop OpenCode) go to receivers and services and do
not open the app; when an answer cannot be delivered, the receiver opens the app at the exact request
(ANDROID-PLATFORM-07). Other notifications: status row Open → `Chat(ref)` when one chat runs, else plain open; heat,
idle-return and voice-download notifications → plain open (resume where the person was). Rows: B-NAV-005, E2E-07,
E2E-08.

### 7.9 Scheduled-run taps (SCREENS-NAV-19)

Extras `scheduledProfileId`, `scheduledPromptId`, `scheduledSessionId`. The request is held up to **30 s** while the
app's own start-up connection settles (Flutter `23c851fc8`), then: run's conversation reachable → `Chat(ref)` (switching
server first if needed); else → `ScheduledPrompts(highlight = scheduleId)`; the morning summary → `ScheduledPrompts`; a
deleted schedule or a changed server → nothing (never falls back to another target). Re-evaluated on every resume.
Rows: B-NAV-054, E2E-27.

### 7.10 Widgets (taps that open the app)

| Widget part | Request | Target |
|---|---|---|
| Conversations widget: request row **Allow** / **Reject**, **Stop**, **Approve plan**, **Resume** | none (foreground-service action, ANDROID-PLATFORM-26) | app does not open |
| Conversations widget: **Reply** | `AlertOpen` built from the row's alert extras with `oc.widgetReply = true` | `Chat(focusComposer = true)`; on the open chat, focuses in place (N4) |
| Conversations widget: **Open**, a row | `AlertOpen(reply = false)` | `Chat(ref)` (other project/connection resolved first) |
| Conversations widget: **+** | `Launch(NewTask)` | §7.4 |
| Conversations widget: **Edit** | `WidgetConfigureActivity` → `WidgetPanel.Project` | overlay, not the app |
| steer / voice buttons | `WidgetRouteActivity` → `WidgetPanel.Steer` | overlay |
| Status widget lines | `connect`, `linux`, `activity`, `usage` | §7.4 |
| anywhere else on a widget | plain launch | resume where the person was |

Widgets are redrawn on `MY_PACKAGE_REPLACED`, so their PendingIntents are recreated by the native code after the update
(Android surfaces section). Rows: B-NAV-005, B-NAV-077, E2E-26.

### 7.11 Quick Settings tiles

| Tile | Tap | Long-press |
|---|---|---|
| `AttentionTileService` (needs-you count) | `startActivityAndCollapse(PendingIntent)` with `oc.shortcut=activity` → `NeedsYou` | **new**: `ACTION_QS_TILE_PREFERENCES` on `MainActivity` → `TilePreferences(Attention)` → `Settings(Notifications)` |
| `PauseTileService` (pause/resume live mode) | toggles through `LivePauseReceiver`; the app does not open | **new**: → `Settings(Background)` |

The long-press filter is added because without it Android opens App info [V android-surfaces §10]. Adding an intent
filter does not change any component name, so the component freeze (ADR §9.9) still holds.

### 7.12 Capability enable flows (SCREENS-NAV-20)

`EnableFlows` in `:ui:shell` maps each id to a target. A flow the platform cannot run is not registered, so the
explainer explains instead of offering a dead button.

| Flow id | Target | Registered when |
|---|---|---|
| `addServer` | `ServersRoute.Editor(Add(null))` | always |
| `addServerCodex`, `addServerPaseo` | `ServersRoute.Editor(Add(Codex))`, `(Add(Paseo))` | always |
| `serverGeneration` | host = this phone → `PhoneRoute.ThisPhone`; else `Editor(Add)` | always |
| `modelSignIn` | `serverCatalog` → `SettingsRoute.Providers`; `agentAccount` → `AgentsRoute.Account`; else `SheetKey.ModelPicker` | always |
| `mcpAdd` | `SettingsRoute.Tools(section = Mcp)` | always |
| `projectChoose` | `ProjectRoute.Projects` | always |
| `projectGitInit` | `ProjectRoute.Health` | always |
| `quotaCollectorGuide` | no profile → `Editor(Add)`; else `UsageRoute.Hub(Plans)` | always |
| `phoneSetup` | `PhoneRoute.SetupStart(false)` | Android |
| `addToolClaude` | `AgentsRoute.Hub`; connected through Claude Code → `land()` | Android |
| `voiceModelSetup` | `SheetKey.VoiceModelSetup` | `Voice` |
| `allowMicrophone`, `allowCamera` | `PermissionGate.request(...)`; permanently denied → app settings | `Voice` / `PromptPhotos` or `QrPairing` |
| `allowNotifications` | `SettingsRoute.Notifications(page = Notifications)` | `Notifications` |
| `allowBackground` | `PhoneRoute.KeepRunning` | `BackgroundService` |
| `tailscaleSetup` | `ServersRoute.Tailscale` | `TailscaleHandoff` |
| `teamTurnOn`, `teamHostGuide` | not registered until AI Team | — |
| `phoneSetupTermux`, `addExternalAgent` | not registered (X1, X2) | — |

### 7.13 The frozen entry contract (add to the component-freeze list, ADR §9.9)

Pinned shortcuts keep their intent inside the launcher, notifications posted by the Flutter app keep their
PendingIntents after the update, and placed widgets keep theirs until redrawn. So the native app must accept exactly
these, forever:

| Producer | Intent shape the native app must accept |
|---|---|
| Static shortcuts | ids `connect`, `new_task`; explicit `MAIN` to `.MainActivity`; extra `oc.shortcut` |
| Launch actions | extra `oc.shortcut` with the §7.4 whitelist and the `usage_target` regex |
| Pinned sessions | extras `oc.shortcut.profile`, `oc.shortcut.session`; ids `oc.session:<profile>:<session>` |
| Coding alerts | extras `io.github.eslamasabry.opencode_mobile.extra.CODING_ALERT_KIND`, `...extra.CODING_ALERT_SESSION_ID`, `...extra.CODING_ALERT_PROFILE_ID`, `io.github.eslamasabry.opencode_mobile.MONITOR_TOKEN` |
| Widget reply | boolean extra `oc.widgetReply` |
| Widget overlay | extras `oc.widgetMode`, `oc.widgetPayload`; data `opencode-widget://action/<widgetId>/<id>` |
| Status actions (receiver) | extras `io.github.eslamasabry.opencode_mobile.extra.STATUS_ACTION`, `...extra.STATUS_SESSION`; data `opencode://status/stop_reply` |
| Scheduled runs | extras `scheduledProfileId`, `scheduledPromptId`, `scheduledSessionId` |
| Links | `VIEW` + `BROWSABLE`, scheme `opencode-mobile`, hosts `session`, `team` |
| Share | `SEND` `text/plain`, `Intent.EXTRA_TEXT`, `Intent.EXTRA_SUBJECT` |

`:quality` `EntryContractTest` reads this table from `quality/freeze/entry-extras.txt` and checks that
`EntryIntentReader` accepts a sample of each, and E2E-39 (upgrade journey) proves a pinned shortcut and a notification
created by Flutter 2.1.0+57 open the right conversation in the native build.

---

## 8. Adaptive layouts

### 8.1 Window classes (one function, `:ui:design` `WindowClasses`, measured on the window, never the device)

| Class | Width | Short height | Nav chrome | Panes |
|---|---|---|---|---|
| Compact | < 600 dp | any | none (no bottom bar) | single |
| Medium | 600–839 dp | — | rail (80 dp, icon + label always visible) | single |
| Compact or medium, height < 480 dp (phone landscape) | ≥ 600 dp | yes | rail | single; modal parts stay ≤ medium |
| Expanded | 840–1199 dp | no | sidebar (296 dp, wider with large text, up to 400 / one third) | list-detail where declared |
| Large | ≥ 1200 dp | no | sidebar | list-detail; three-pane for Team projects later |

### 8.2 Chrome per class

- **Compact.** `ChatsRoute.Conversations` draws `KitTopBar.shell`: server pill (name, "+N" other shown servers, one
  status word), Search, Settings gear. No other page has shell controls.
- **Medium.** The rail sits at the start edge; the same top bar on Conversations; the rail names the destination.
- **Expanded / large.** The sidebar holds the shell controls (pill, Search, gear) as its header plus the destinations;
  Conversations has no bar, only its large title.
- **Persistent rail (O-07-1, default yes).** On medium and wider windows the rail or sidebar stays visible under every
  pushed page (chat, settings pages, viewers); pushed pages fill the content area beside it. Flutter covered the rail
  with pushed pages; keeping it removes a relayout on every push and keeps destinations one tap away. With O-07-1 = no,
  the shell hides the rail for any route whose `RouteMeta` is not top-level, and the push covers it.
- Destinations: Conversations (badge = unified attention count), Files (only when the connected server can browse
  files), Settings. An unavailable destination is absent. Selecting one calls `selectDestination` (§2.4).
- Status line: one per window, drawn by the top page's `KitScreen` under its bar (§10.1).

### 8.3 List-detail scenes

Material `ListDetailSceneStrategy` from `adaptive-navigation3` (CMP 1.3.0) with `listPane()` / `detailPane()` metadata
[V API names, developer.android.com "custom layouts"; U the CMP artifact's exact package, slice-1 shell spike]. On
compact and medium the same keys show one at a time; on expanded and large the scene shows the nearest list entry
beside the top detail entry.

| Area | List pane | Detail pane | Empty detail | Width rule |
|---|---|---|---|---|
| Settings | `SettingsRoute.Hub` | `Group`, `ByServer`, every settings leaf page (`detail` in §2.2.2) | "Choose a group" (`settings-detail-empty`) | list 296, detail ≤ 700, detail min 360 (else one column) |
| Files | `ProjectRoute.Files`, `Folder` | `ProjectRoute.FileView` | "Choose a file" | same; draggable divider only with a fine pointer (desktop) |

Everything else is single-pane at its kit width cap (component max widths, memory `component-max-width-rule`):

| Page | Cap |
|---|---|
| Conversations, Inbox, All conversations, New conversation | reading 720, centred from tablet up |
| Chat (transcript + composer) | conversation 700, centred; the composer and request cards share the cap |
| Servers, This phone, Linux pages, Agents, Usage, Schedules, About pages, chat pages | list 960 |
| Review / Diff | full width; side-by-side diff from expanded |
| Terminal, local terminal, dev preview, pairing scanner | full width (content surfaces) |

### 8.4 Sheets and dialogs by class (kit-v2 §8.2)

| Kind | Compact | Medium | Expanded / large | Short height |
|---|---|---|---|---|
| Sheet | bottom sheet | bottom sheet capped 640 | centred panel 560 | bottom sheet |
| Full-height sheet (`Form` ≥ 5 fields, model catalog) | full-height bottom sheet | full-height capped 640 | end-side sheet 400–480 | bottom sheet |
| Confirm | sheet | sheet capped 560 | centred dialog 480; Enter confirms only neutral | sheet |
| Input dialog / alert | dialog 480 | dialog 480 | dialog 480 | dialog |

### 8.5 Desktop (later phase, ADR §7.1)

The route list, navigator, scenes and shortcuts are common code, so `:desktopApp` gets them for free when its product
phase starts. Desktop adds: window state (`desktop_window_state_v1`, default 900 × 700, min 480 × 600), context menus on
right-click, persistent scrollbars, mouse text selection, file drop on the composer. Android-only routes are not
registered there. Until then `:desktopApp` is the kit gallery only.

---

## 9. Keyboard shortcuts

### 9.1 Bindings

Every binding is registered for **Ctrl and Meta** (Cmd on macOS later). No plain-letter accelerators, except the review
page's local letters and the focused request card's keys, and those never fire while a text field has focus.

| Keys | Action | Scope | Claimed by / fallback |
|---|---|---|---|
| Ctrl+K | command launcher | anywhere | chat claims it for its command sheet; fallback `showSheet(CommandLauncher)` |
| Ctrl+N | new conversation | anywhere | fallback: create session → `Chat(NewlyCreated)`; failure = one status line in plain words |
| Ctrl+F | find in this page | chat (find bar), Files (search), Settings (search) | swallowed when nobody claims it |
| Ctrl+, | Settings | anywhere | `selectDestination(Settings)` |
| Ctrl+W | close this page | anywhere | `back()` (page guards still run) |
| Ctrl+/ | keyboard shortcuts | anywhere | `showSheet(ShortcutsHelp)` |
| Ctrl+Backquote | terminal | anywhere | `open(ProjectRoute.Terminal(active profile))`; the page explains when the server keeps no terminals |
| Ctrl+1 / 2 / 3 | Conversations / Files / Settings | anywhere | `selectDestination` (on phones Files and Settings open as pages); Files unavailable on this server → no-op; Ctrl+4 does not exist |
| Ctrl+Enter | send | composer | — |
| Enter / Shift+Enter | send / newline | composer, only with a fine pointer; phones keep Enter = newline | — |
| Esc | close overlay; close find; stop the running reply | overlay; chat (only when no IME composition and find closed) | — |
| F2 / Shift+F2 | next / previous recent model | chat | — |
| F4 / Shift+F4 | thinking level | chat | — |
| Ctrl+B | move running work to the background | chat | — |
| F3 / Shift+F3 | next / previous find match | chat | — |
| Up / Down at the caret edge | prompt history | composer | keeps the original draft |
| PgUp / PgDn, Home / End | transcript pages / ends | chat, not typing | — |
| `]` `[`, N / F7, P / Shift+F7 | next / previous file, change | Review, Diff, not typing | — |
| A / D / 1–9 | allow / deny / choice | focused request card on wide windows | — |
| Shift+F10, Menu key | row context menu | rows with a menu | every menu also has a visible control |
| Ctrl+C | copy selection / transcript | transcript (fine pointer) | — |

### 9.2 Where they work

- **Android with a hardware keyboard: all of them** (decided here; Flutter bound only the chat-local keys on Android).
  Nothing changes for touch users, and no plain letters are bound.
- `MainActivity.onProvideKeyboardShortcuts` publishes the same groups to Android's system Keyboard Shortcuts Helper
  (Meta+/) [U: interplay with Compose key handling, slice-5 check on the emulator with `adb shell input keycombination`].
- Desktop: all, when its phase starts.

### 9.3 Implementation (`:ui:shell` commonMain)

- `ShortcutCatalog`: one list of `ShortcutBinding(id, keys, group = Anywhere|Conversation|Review, labelKey, scope)`.
  The help sheet and the Android helper are generated from it, so a binding without a help row cannot exist
  (`ShortcutCatalogTest`; the Flutter gesture audit found unlisted shortcuts before, `b2cf153f8`).
- `ShortcutDispatcher`: one `Modifier.onPreviewKeyEvent` at the root maps a key event to an `AppShortcut`, offers it to
  the claims of the **top entry and any open sheet only**, newest first, and runs the fallback when none claims it. This
  is the Compose form of Flutter's claim bus, where each claim re-checked `ModalRoute.isCurrent`.
- Kit API for features: `KitShortcutClaim(shortcut: AppShortcut, enabled: Boolean = true, onInvoke: () -> Boolean)`.
  The shell's `ShortcutClaimsDecorator` gives every Nav3 entry its own claim registry, so a buried page never steals
  a key.
- The kit tracks whether a text field has focus (`LocalKitTyping`), and plain-key shortcuts skip while it does.

---

## 10. Obligations every screen carries

### 10.1 One status line

- Screens contribute a `KitStatus` through their `XUi` (`statusContribution`); app-wide conditions come from the
  `:engine:attention` status combiner (ADR §5.2) through the shell's `StatusSource`.
- The top page's `KitScreen` draws exactly one line, highest priority first: connection > appStopped > heat >
  riskySwitch > work > update > info. While the server pill already says Connecting, the line is suppressed (one
  indicator per fact, `bc35cb563`).
- Conversation pages (C01, `ChatPagesRoute.*`) replace the connection condition with their own backend's (§2.7).
- One-shot notices (`work`) clear after `KitMotion.undoWindow`, except under accessible navigation, where they wait for
  Dismiss.

### 10.2 Test tags

Stable test tags equal the Flutter `ValueKey` strings where they existed (ADR §9.1); otherwise none, and tests find text.
The navigation-level tags to carry over: `home-shell-nav`, `home-shell-search`, `home-shell-settings`,
`home-shell-tab-chats`, `home-shell-tab-files`, `home-shell-tab-settings`, `home-shell-project-unavailable`,
`home-shell-project-switch-server`, `server-switcher-button`, `server-switcher-sheet`, `server-switcher-add`,
`server-switcher-current`, `kit-shell-server-name`, `kit-shell-server-status`, `chats-new-chat`, `chats-needs-you`,
`chats-filter-project`, `chats-filter-running`, `chats-filter-sources`, `chats-row-opening`, `chats-new-field`,
`chats-new-send`, `chats-new-model`, `chats-new-project`, `chats-new-separate-copy`, `chats-new-import-claude`,
`inbox-list`, `inbox-empty`, `inbox-finished`, `settings-hub-list`, `settings-hub-search-field`, `settings-list-pane`,
`settings-detail-pane`, `settings-detail-empty`, `settings-tab-title`, every `settingsGroupRowIds` id
(`settings-saved-servers`, `settings-this-phone`, `settings-model-and-mode`, `settings-providers`,
`settings-category-usage`, `settings-tools`, `settings-agents`, `settings-show-reasoning`, `settings-show-timestamps`,
`settings-phrase-chips`, `default-shell-settings-entry`, `settings-voice`, `settings-automation`,
`settings-category-notifications`, `settings-category-background`, `settings-category-appearance`,
`settings-category-language`, `settings-category-privacy`, `settings-setup-guide`, `settings-tips`,
`library-report-bug`, `settings-about-notices`), `chat-project-chip`, `chat-git-chip`, `session-actions-button`,
`session-menu-<action>`, `chat-leave-unsaved-draft`, `command-palette-query`, `keyboard-shortcuts-sheet`,
`agents-sheet`. The tag `home-shell-tab-*` now marks rail destinations.

### 10.3 Teaching (hidden gestures need a tip and a visible path)

| Hidden gesture | Visible path | Tip id |
|---|---|---|
| Long-press Stop / tap the live line = stop after this step | none needed beyond the tip; Stop is visible | `stop-after-step` (kept) |
| Long-press Send = send later or repeat | session menu "Scheduled prompt…" | `send-later` (kept) |
| Hold the mic = push to talk | tap the mic | `hold-mic` (kept) |
| Appearance tour | Settings › Appearance | `make-it-yours` (kept) |
| Long-press the launcher icon for pinned conversations | session menu Pin (O-07-2) | **new** `launcher-shortcuts`, offered once after the first pin |
| Long-press a conversation row for its menu | the chat's own ⋯ menu | none (the visible path is the primary one) |
| Long-press a Quick Settings tile | Settings › Notifications / Background rows | none (system gesture) |

Each tip has en + ar copy and a Help › Tips row (B-NAV-019, B-NAV-038, B-NAV-074).

### 10.4 Search index and Go to

- Every route in §2.2.2 is in the search index (`:feature:search`) by its title, or listed in `SearchExclusions.kt`
  with a reason. `SearchIndexCoverageTest` walks the `AppRoute` subclasses through the serializers module and fails on
  a route that is neither (B-NAV-057).
- An index entry stores `target: AppRoute` plus an optional `HighlightArrival` anchor id (anchor ids like
  `effects-vibration`, never route strings). Gated entries follow §2.8 (B-NAV-058, B-NAV-072).
- Go to (empty launcher): Needs you, Files + project name, Projects, Settings, Servers, Terminal, Scheduled prompts, Help;
  then the last 3 conversations. AI Team appears only once ported (B-NAV-056).

### 10.5 Text scale, RTL, motion, max width

- Page titles, bars, chips, the composer and sheet headers use kit frame text (capped at 1.3×); reading text scales
  fully (memory `text-scale-cap`). This is a kit job; screens never set a scale.
- RTL: the predictive shift follows the swipe edge; direction glyphs mirror; paths, ids, commands and URLs stay LTR.
- Reduced motion: §3.1's last two columns, from the kit's `MotionPolicy` (ADR §9.4).
- Max widths: §8.3 table, plus each kit part's own cap.

### 10.6 Actions and rows

- Every action that leaves a page or acts on a thing names its target: "Open Laptop's page", "Stop the server on this
  phone", "Disconnect from Laptop" (memory `actions-name-their-target`).
- Main actions are visible first-page rows; a `⋯` menu holds only rare or destructive acts (memory
  `obvious-options-first`).
- No screen adds a new bar or row level for navigation (memory `no-new-ui-levels`): the find bar replaces nothing and
  docks under the existing top bar; the rail is not a new level on phones because phones have none.

### 10.7 Performance marks

Keep the Flutter `PerfTrace` names so the owner's Performance report reads the same: `app.main`, `app.first_frame`,
`app.bootstrap`, `app.shell_frame`, `app.first_connected`, `chat.open_to_first_transcript` (with
`source: saved_excerpt|live`), `chat.open_to_fresh`, `settings.open`, plus `nav.<from>-><to>` from N8.

---

## 11. Build order and agent work packages

Each package is one agent, one branch, one worktree. Shared files (`:ui:routes` route files, `:ui:words` string files)
are edited only by the feature named on the row (ADR §4 row 22).

| Slice | Package | Owner | Writes | Reads | Done when |
|---|---|---|---|---|---|
| 0 | P2 predictive-back proof (ADR §11) | Opus | `:ui:shell` spike, kit transition helpers | motion-native §4.4 | the six checks pass on the emulator; option A hooks named for §3.2 |
| 0 | Route-key freeze for slices 1–3 | coordinator | `:ui:routes` `AppRoute.kt`, `Sheets.kt`, files for chats, chat, composer, servers, phone, agents, onboarding; `:core:model` ids | this section §2.2–§2.3 | `RouteRoundTripTest`, `RouteKeyContentTest` green |
| 1 | Shell skeleton | coordinator (Opus for the navigator) | `:ui:shell`: `App()`, opening gate, safe mode, `ShellNavDisplay`, `AppNavigatorImpl`, `ArrivalsImpl`, `ShellSheetHost`, `RouteGate`, rail/sidebar frame, status slot, list-detail strategy | §2, §3, §8 | navigator rules N1–N9 table-tested; desktop gallery shows the shell with fakes |
| 1 | Entry router core | Codex (backend) with the coordinator | `:engine:entry` types, `decide`, `RootDecision`, link parsers; `:android:platform` `EntryIntentReader` | §7 | the §7.4–§7.9 tables as `commonTest` rows; catalog rows named |
| 2 | Onboarding + Conversations + Inbox + New conversation | Sonnet | `:feature:onboarding`, `:feature:chats` | §5 A, B | E2E-01 and E2E-21 steps 1–7 green on the `.native` preview build (steps 8–9 need slice-4 chat pages) |
| 2 | Chat entry, arrivals and Back guard | chat lead (Opus) | `:feature:chat` entries | §3.4, §2.5 | B-NAV-005 test; draft guard test; dedupe test |
| 2 | Servers | Sonnet | `:feature:servers` | §5 E | E2E-01/02/03/04 green |
| 3 | Composer sheets | Sonnet | `:feature:chat-composer` | §5 C14–C24 | — |
| 3 | This phone, setup, Linux pages | Sonnet | `:feature:phone` | §5 F | E2E-17/18/19/20 green |
| 3 | Agents | Sonnet | `:feature:agents` | §5 G | E2E-31 green |
| 4 | Chat pages, Settings, Schedules, About, widget panels, tiles, scheduled-run routing | Sonnet ×4 (one per feature) | `:feature:chat-pages`, `:feature:settings`, `:feature:schedules`, `:feature:about`, `:feature:widget-panels` | §5 C, I, K, L, M; §7.9–§7.11 | E2E-22, E2E-25, E2E-26, E2E-27, E2E-38 green |
| 5 | Project, Usage, Search, keyboard shortcuts, demo | Sonnet ×3 | `:feature:project`, `:feature:usage`, `:feature:search`, `:ui:shell` shortcuts | §5 D, J, A12/A13; §9 | E2E-14/15/16/24/33 green |
| 6 | Switch hardening | coordinator | `quality/freeze/entry-extras.txt`, upgrade journey | §7.13 | E2E-39 green with a pinned shortcut, a Flutter-posted notification and both widgets |

Recipe for adding one page (any feature agent):

1. Add the key to your feature's route file in `:ui:routes` (rules §2.2.3). Add a sample to `RouteSamples.kt` for the
   round-trip test.
2. Write `XUi` + `XActions` in `presentation/`, the ViewModel (assisted factory if the key has arguments, 03 S5).
3. Add the `entry<…>` to `XEntries.kt` with `RouteMeta(motion, pane, requires)`.
4. Add the screen `XScreen(state, actions)` from kit parts only, with the Flutter `ValueKey` as the test tag when one
   existed.
5. Add strings to your `strings_<feature>.xml` (en + ar at least; ADR §7.5).
6. Add a search index entry or a `SearchExclusions` line.
7. If the page has a hidden gesture, register a tip (§10.3).
8. Run T0: your ViewModel test, `RouteRoundTripTest`, `RouteKeyContentTest`, `SearchIndexCoverageTest`.

---

## 12. Acceptance checks

### 12.1 Logic (T0/T1, `commonTest` on the desktop JVM)

| Test | Proves | Rows |
|---|---|---|
| `AppNavigatorTest` | N1–N9 on a plain `SnapshotStateList`: dedupe, `openChat` arrival-only on top, `land`, `replaceRoot` keeps pages above, `selectDestination` reselect pops, sheet-first ordering | B-NAV-005, B-NAV-024, B-NAV-037 |
| `RootDecisionTest` | the §2.6 table | B-NAV-017, B-NAV-024, B-NAV-029 |
| `RouteGuardTest` | restored stacks drop deleted profiles and off platform flags; root rebuilt | — |
| `BackOwnerPolicyTest` | §3.3 order; the root never consumes Back; chat guard enabled only when the draft differs | ADR §9.4 |
| `EntryRouterTest` | §7.3 rules 1–9; every row of §7.4–§7.10 | B-NAV-005, B-NAV-025, B-NAV-054, B-NAV-061, B-NAV-063, B-NAV-064, B-NAV-077, B-ANDR-027, B-ANDR-028, B-ANDR-071, B-ANDR-073, B-ANDR-075 |
| `LinkParserTest` | session, address and team link shapes; length and character bounds | B-NAV-063 |
| `EnableFlowsTest` | §7.12 registrations per platform capability | SCREENS-NAV-20 |
| `ShortcutCatalogTest` | every binding has a help row; no plain-letter global bindings | DESKTOP-WEB-CROSS-12 |
| `RouteRoundTripTest`, `RouteKeyContentTest`, `BackStackSizeTest` | §2.2.3 | — |
| `SearchIndexCoverageTest` | §10.4 | B-NAV-057 |
| `EntryContractTest` (`:quality`) | §7.13 | E2E-39 |

### 12.2 Device flows (T3, Maestro, both apps)

E2E-01 (cold start and welcome), E2E-21 (Conversations), E2E-22 (settings search and structure), E2E-24 (launcher and
menus), E2E-25 (shortcuts, tile, links, share), E2E-26 (widgets), E2E-27 (scheduled runs), E2E-36 (several servers),
E2E-38 (tips), E2E-39 (upgrade in place). Written against the Flutter release APK first, then run on the native release
build (ADR §10.1).

### 12.3 Emulator checks a person (or Opus) watches

1. Predictive back, list → chat, the six checks of motion-native §4.4: slow drag and commit lands with no jump; cancel
   returns without flicker; fling carries velocity; RTL from both edges; row scrolled off screen; reduced motion = plain
   pop.
2. Predictive back on a settings page, a full-screen dialog (New conversation) and at the root (back-to-home plays).
3. Tablet AVD (1280 × 800) and phone landscape: rail/sidebar, list-detail in Settings and Files, persistent rail under a
   chat (O-07-1), sheet adaptivity (§8.4), max widths with extreme content at 280, 412 and 1600 dp.
4. Hardware keyboard (`adb shell input keycombination`): every §9.1 row; nothing fires while typing in the composer.
5. Process death (`adb shell am kill` while backgrounded, then reopen from Recents): the stack restores; no link,
   share or shortcut replays (§7.1).
6. Arabic: the whole graph walked once in RTL.

---

## 13. Choices for the owner (each has a default; work proceeds on it)

| # | Question | Default | Why |
|---|---|---|---|
| O-07-1 | On tablets, landscape phones and desktop, should the side rail stay visible while a conversation or another page is open? (Phones are not affected.) | **Yes, keep it visible** | No jump on every page open; Conversations, Files and Settings stay one tap away; this is how Android's own tablet apps behave. |
| O-07-2 | Pinning a conversation: move "Pin" from the Subagents page (its only home today) to the conversation's ⋯ menu and the row's long-press menu? | **Yes** | Pins feed the launcher shortcuts, but nobody can find the control today (SCREENS-NAV-112). Same number of controls, better place. |
| O-07-3 | Open files as a page (with the usual back swipe) instead of a tall sheet on phones? On tablets the file shows beside the list either way. | **Yes, a page** | One viewer everywhere, native back preview, and it survives Android closing the app. |
| O-07-4 | Accept shared pictures from other apps, and show your recent conversations in Android's share sheet (Direct Share)? | **Not in the switch release; right after it** | The switch release is "nothing lost" (ADR §10); these are new features. |

Decisions already in the ADR that this section applies: D5 (drop "Press back again to exit"), D6 (AI Team and FactsBook
after the switch, A2A dropped), D7 (Termux migrate-out only), D11 (live backgrounds later), D16 (Linux services, vitals
and backups ported from their branches).

---

## 14. Risks and unknowns

| # | Risk or unknown | Settled by | Fallback |
|---|---|---|---|
| U-07-1 | Exact hook for turning shared bounds off during a gesture in Nav3 1.2.0 / NavigationEvent 1.1.0 | ADR P2 (slice 0) | plain shared-axis for list → chat until option B |
| U-07-2 | `subclassesOfSealed<AppRoute>()` across many files and `NavKey` polymorphism on all targets | `RouteRoundTripTest` in slice 0 | hand-written `polymorphic(NavKey::class) { subclass(...) }` generated by a small script |
| U-07-3 | CMP `adaptive-navigation3` 1.3.0 `ListDetailSceneStrategy` package and behaviour with a persistent rail outside `NavDisplay` | slice-1 shell spike | a custom `SceneStrategy` (the documented 30-line pattern) |
| U-07-4 | Per-sheet `ViewModelStore` and the kit's in-window `BottomSheet` host for shared sheets | slice-1 shell spike | local sheets only, with the owning feature's screen as a route-backed `OverlayScene` |
| U-07-5 | Activity restore re-delivering the original intent on some launchers despite `FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY` | §12.3 check 5 on the emulator and the owner's ZTE | also store the last consumed request fingerprint in `files/native/entry.json` and drop exact repeats within 10 minutes |
| U-07-6 | `onProvideKeyboardShortcuts` with Compose; system helper shows our groups | slice 5 emulator check | skip the system helper; the in-app help sheet stays |
| U-07-7 | `ACTION_QS_TILE_PREFERENCES` on the ZTE MyOS launcher | slice 4 owner phone check (read-only adb allowed) | App info opens, as today |
| R-07-1 | The chat library is the product (27.7k lines); its Back guard and arrivals are where regressions hide | chat lead owns C01; tests in §12.1 first | — |
| R-07-2 | 25 shared sheets make `Sheets.kt` a hotspot | coordinator owns the file; one PR per new key | — |

---

## 15. Sources

- ADR `01-architecture-decision.md` §4 rows 12/13/18/26, §5.2, §6, §7, §9.1, §9.4, §9.7, §9.9, §10, §11 P2, §12.
- `03-architecture-modules.md` choices S5, S10, S12, S17 and §2.2 (in progress on 2026-11-01).
- Maps: `screens-nav.md` (all sections; SCREENS-NAV-01–112), `chat-genui.md` (CHAT-GENUI-19/25/28/49/66/67),
  `android-platform.md` (§3, ANDROID-PLATFORM-05–07, 17–33), `builtin-linux.md` (BUILTIN-LINUX-40–50, 63–64, Q1),
  `agents-backends.md` (AGENTS-BACKENDS-46), `desktop-web-cross.md` (§3.B), `ui-kit.md` (UI-KIT-06/07/08/21–32),
  `media-voice.md` (voice settings), `protocols.md` (OpenCode 1 recognition, PROTOCOLS-34), `state-data.md`.
- Research: `motion-native.md` §3–§4, `kmp-cmp-status.md` §4, §12.3, `architecture-stacks.md` §5, §12,
  `android-surfaces.md` §0, §9–§12, §15, §17, `testing-perf.md` §4.2, §6.
- Catalog: `BEHAVIORS.md` NAV area (82 rows) and the ANDR rows named above; `e2e-flows.md` E2E-01, 21–27, 36, 38, 39.
- Flutter source read 2026-11-01 (`/home/eslam/Storage/Code/wt/oc_app-batch1`): `lib/ui/capability_flows.dart`,
  `lib/ui/screens/settings/settings_group_page.dart`, `lib/ui/screens/home_screen.dart`,
  `lib/ui/screens/chats/chats_host.dart`, `lib/ui/screens/inbox/inbox_screen.dart`,
  `lib/ui/screens/capabilities_screen.dart`, `lib/ui/screens/local_agent_screen.dart`,
  `lib/ui/screens/agents/agent_model_sheet.dart`, `lib/ui/screens/chat/chat_command_actions.dart`,
  `test/home_navigation_test.dart` (lines 700–830), and the Kotlin `LaunchActivityBridge.kt`,
  `BackgroundConnectionService.kt` (extra constants), `ScheduledPromptChannel.kt`, `SessionWidgetIntents.kt`.
- Official docs read 2026-11-01 [V]: developer.android.com "Navigation 3: custom layouts" (`SceneStrategy`,
  `sceneStrategies`, Material `ListDetailSceneStrategy`, `listPane`/`detailPane`/`extraPane`) and "Animate between
  destinations" (`transitionSpec`, `popTransitionSpec`, `predictivePopTransitionSpec`, `NavDisplay.TransitionKey`,
  `PopTransitionKey`, `PredictivePopTransitionKey`, `sharedTransitionScope`).
- Owner memory: `no-bottom-nav-decision`, `obvious-options-first`, `actions-name-their-target`, `settings-scope-model`,
  `simplify-and-natural`, `teach-what-is-hidden`, `no-new-ui-levels`, `text-scale-cap`, `component-max-width-rule`,
  `android-native-feel`, `steer-not-queue`, `chat-kit-decisions-2026-10-02`, `vision-decisions-2026-10-20`,
  `linux-manager-plan`, `freeze-owner-decisions-2026-11-01`, `dev-freeze-native-rewrite-2026-11-01`,
  `blocked-feature-decisions-2026-09-28`, `ux-skills-for-navigation`, `no-glass-rule`, `app-updates-two-layers`.
