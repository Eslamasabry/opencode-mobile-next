# P12-SEARCH-USAGE: Search conversations, Usage and cost

Branch: `claude/search-usage-parity-o8x97z`. The brief named `native/p12-search-2026-11-03`, but this cloud session may push only to its assigned `claude/…` branch. Commits: `38fd4996`, `a4055864`, `8a78373b` and this report.

## Status, in the runbook's separate states

| State | Result |
|---|---|
| Implemented | Yes. Both screens, their ViewModels, the Android wiring and the entry points are written. |
| Compiled / tested | **No.** This session's network policy blocks `dl.google.com`. That host serves Google Maven, AGP and the Android SDK, so Gradle can't configure any module here (`Could not GET https://dl.google.com/dl/android/maven2/… 403`). No compile ran, no test ran and no APK was built. All code was written against the source of the kit, routes, gateway and protocol on `native/m1-2026-11-03`. |
| Enabled in the app | Yes, through `:androidApp` (module list and `graph/SearchUsageWiring.kt`), once it compiles. |
| Verified on a device | No. |

**Integrator, first action:** on a machine with Google Maven, run
`./gradlew :feature:search:desktopTest :feature:usage:desktopTest :feature:chats:desktopTest :feature:servers:desktopTest :protocol:oc2:compileKotlinDesktop :androidApp:assemblePreviewDebug`, fix any compile slips, then do the device check at the end of this file.

## Journeys now in the app

1. **Search conversations.** Conversations → **Search conversations** (a tertiary button under "Show all") opens `SearchRoute.Conversations`.
   - Typing (300 ms settle, from the kit field) runs the server's own title search across **every project** on an OC2 server (`GET /api/session?search=…&parentID=null&order=desc`, unscoped, the same request as Flutter `listGlobalSessions`).
   - A gateway that isn't OC2, or a server that answers that call with 404 or Unavailable, falls back to the current project's pages, matched by title here. At most 400 conversations are read. The screen then says "Only this server's current project is searched."
   - **Also search inside recent conversations** is a visible switch row, off by default. When on, it reads the newest 50 messages of the 20 most recent conversations and lists **Inside conversations** hits with the matching words ("You: …" / "Reply: …").
   - Several connected servers show as chips, so the person can choose which one to search.
   - **Recent searches** show while the field is empty. A search is kept when the person presses Enter or opens a result. Each one is a removable chip, and there is a **Clear recent searches** button. Storage is app-wide in `oc.search.recent` (at most 8).
   - A result opens that conversation on its own server (`nav.openChat(ref)`).
   - Other states: no match uses the kit's no-match view. A failed search shows a plain line, Details and Try again. With no server connected, the screen says so and offers **Open Servers**. "Load more" follows the server cursor.
2. **Usage and cost.** Conversations → Servers → a connected server → **Usage and cost** opens `UsageRoute.Hub(profile)`.
   - The page shows the estimated cost first, then the period and time zone.
   - The calendar is a segmented control: Today, 30 days, This year, All time. Ranges use calendar arithmetic in the device's own IANA zone.
   - The project filter has "All projects" plus every project seen on the server (from the unscoped session list), sent as `project=<id>`.
   - Several servers show as chips.
   - Breakdowns: Activity counts, Tokens, Providers (a rollup where a variant is not a new model, and an overflow reads "Cost subtotal unavailable"), Model usage, Tool reliability with the success rate of finished calls, and Activity by day.
   - The estimate disclosure stays visible.
   - Data comes from `GET /api/session/stats`, and on 404 from `/api/experimental/session/stats` (stable). Both are GETs, so the retry never repeats a mutation.

## Catalog rows covered by tests (written, **not yet run**)

| Row | Where |
|---|---|
| B-NAV-049 (title match, conversations only as matched results) | `SearchMatchTest`, `SearchViewModelTest` |
| B-PROTO-122 (global unless a project is chosen, calendar ranges include today, all time omits `from`, native time zone, a missing project never becomes all projects) | `UsageQueryTest`, `UsageViewModelTest` |
| B-PROTO-121 (provider rollups by returned ids, variants are not models, overflow is unavailable, empty data has no providers) | `UsageQueryTest` |
| B-NAV-078 (authorization / absent endpoint / unreadable stay distinct, late answers can't mislabel totals, previous result only for unchanged filters) | `UsageViewModelTest`, `UsageScreenTest` |
| B-AGENT-310 (cost estimate and compact token formatting, tiny cost not shown as zero) | `UsageQueryTest` |
| B-L10N-010 (Arabic) | `SearchScreenTest`, `UsageScreenTest` |
| SCREENS-NAV-34 / SCREENS-NAV-96 (map rows: search states, recent searches, server choice, no server, load more; usage server switch and project failure) | the ViewModel and screen tests |

Not covered (not built): B-STATE-146 and B-STATE-147 (usage budgets), B-NAV-079 (Subscriptions/Plans), B-NAV-056/057/058 (command launcher, Go to, settings search index), E2E-24 and E2E-33.

Test counts: Search has 5 match + 11 ViewModel + 6 screen tests. Usage has 7 query/figure + 8 ViewModel + 5 screen tests. Result: **not run** (see the status table).

## Deviations and integrator items

1. **Out-of-set files (all additive, no existing line changed except the entry-point edits):**
   - `protocol/oc2/…/live/Oc2SearchUsage.kt` (new). The native gateway has no `listGlobalSessions` and no usage call, and `Oc2Gateway.json` is `internal`, so the read helper had to live in `:protocol:oc2`. When S5-OC2MORE implements `HostGateway.listGlobalSessions` and a usage facet, swap the wiring to them and delete this file.
   - `ui/routes/SearchRoutes.kt` and `ui/routes/UsageRoutes.kt` (new). `UsageRoute.Hub` takes `profile`, not plan 07's `section`: only the Spent section exists so far.
   - `ui/words/…/values{,-ar}/strings_p12_search_usage.xml` (new, native-only copy with the `p12` prefix so DriftTest never sees it as a stale ARB key). Existing `usage*` and `globalSessions*` strings are reused.
   - Smallest entry edits:
     - `feature/chats`: `ChatsActions.openSearch()` has a default body; `ChatsViewModel` overrides it; `ChatsScreen` gains one button.
     - `feature/servers`: `ServersActions.openUsage(id)` has a default body; `ServersViewModel` overrides it; `ServersScreen` shows a button on connected servers.
   - `androidApp/build.gradle.kts`: two module lines.
2. **`MODULE_OWNERS`** needs `:feature:search` and `:feature:usage` (`ModuleOwnersTest` gates it, and only the coordinator edits that file).
3. **Opening at the matching message** is not possible yet. `ChatRoute` has no message anchor (owned by the chat lead). Inside-text hits carry `messageId` and show the matching words; the chat opens at its end. Proposed change: `ChatRoute(ref, focusComposer, anchorMessageId: String? = null)`; then `SearchViewModel.open` passes `hit.messageId`.
4. **Recent searches** are app-wide, not per profile, so profile deletion does not sweep them. The words are the person's own, and Flutter has no equivalent to migrate. If the owner wants them per server, use `oc.search.recent.<profileId>` and register it with the deletion sweep.
5. **Not built (non-goals for this slice):** usage budgets, the Plans/Remaining tab (subscriptions, Add, Read from, Alerts, Usage widget settings), the command launcher / Go to and Settings search, and the Settings › Agent › Usage row (no Settings hub in the preview yet). Usage is reached from Servers.

## Device check for the integrator

1. Open the preview APK and connect an OC2 server.
2. Conversations → Search conversations → type part of a title → the row opens that chat.
3. Turn on "Also search inside recent conversations" → type a word from a recent reply → the Inside conversations row shows it.
4. Clear the field → the search is listed under Recent searches → remove it.
5. Servers → Usage and cost → switch Today / 30 days / This year / All time and a project → the totals change, and the time zone line names the phone's zone.
6. Repeat in Arabic (RTL).
