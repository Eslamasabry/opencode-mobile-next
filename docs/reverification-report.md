# Re-verification report — v2 migration claim + "all findings fixed" claim

> Historical report. CI availability statements below are superseded: current
> Android, Linux, Windows, and SDK workflows run on GitHub Actions.

> **2026-08-30 re-check (HEAD `17a42bf`, 69 commits later):** analyzer clean,
> **1021/1021 tests pass**. Lens 4 visual sweep landed (12/15 fixed), the
> fix-regression cluster mostly landed (7/13), desktop Phase 1 + window
> state + packaging + a Linux CI gate shipped, queue bounds + labelled
> steer/queue shipped. **The High v2 `_selectLocation` flavor bug is back /
> was lost in a merge** (`connection.dart:3320` still uses the v1 factory;
> no flavor=v2 + saved-location test) — top action below. CI billing block
> still stands (both workflows' own headers admit they have never run).
> Details in the addendum at the bottom of this file.

Report date: 2026-08-29. Branch `production/android-release-hardening` @
`ff9f74e` (1.0.28+29). Re-verifies every finding from
[`ui-feature-audit.md`](ui-feature-audit.md),
[`ui-audit-lenses.md`](ui-audit-lenses.md), and
[`stack-gaps-and-desktop.md`](stack-gaps-and-desktop.md), plus the OpenCode 2
migration, with five parallel evidence-based verification passes.

## Code health (run locally this session)

- `flutter analyze` (pinned Shorebird 3.47.1): **No issues found**.
- `flutter test`: **811/811 passed** (was ~506 at the original audit).
- ⚠️ **The GitHub Actions gate is dead**: all 35 recorded runs of
  `android-quality.yml` fail in ~5 s with an Actions **billing block**
  ("recent account payments have failed"). Nothing has passed CI since at
  least 2026-08-28; fix billing or the workflow gate before the next release.

## Headline verdicts

1. **"Migrated to v2" — TRUE for Phases 0–3, honestly incomplete for Phase 4,
   with one real dual-stack bug.** The port plan's own checkboxes match
   reality except one stale note.
2. **"All agent findings fixed" — TRUE for the base audit and chat/state/
   touch/first-run lenses (~95%+), FALSE for the visual design-system lens
   (1 of 15 fixed) and largely FALSE for the stack/desktop review (~20%
   shipped; the #1 priority — a local database — has zero movement).**

Scorecard across the 74 originally audited findings:

| Audit doc | Findings | Fixed | Partial | Not fixed |
|---|---|---|---|---|
| Base audit (F/E/A) | 26 incl. Lens 1 (C) | 23 | 3 (E4, A3, C6) | 0 |
| Lens 2 (S) + Lens 3 (T) | 24 | 23 | 1 (S11) | 0 |
| Lens 4 (V) + Lens 5 (O) | 24 | 8 | 2 (V4, O7) | **14** |

---

## 1. OpenCode 2 migration — verified

**Shipped and verified with real request bodies + tests
(`api2_*_test.dart`, `v2_feature_gating_test.dart`,
`connection_v2_gateway_test.dart`):**

- **Dual-stack**: v1 untouched and default; flavor detection via
  `/api/health` 401-signal (`server_probe.dart:139–199`), persisted as
  `ServerProfile.flavor`, re-detected on wrong-flavor failures;
  protocol-neutral `ServerGateway` (`lib/domain/server_gateway.dart`) with v1
  and v2 (`lib/api2/gateway.dart`) implementations.
- **Transport**: per-run Basic auth (`api2/transport.dart:41–43`), password
  Keystore-only and never serialized/logged; mid-session 401 → banner +
  "Update password" route.
- **SSE**: `event:`/`id:` parsed (`sse2.dart:96–99` — the old v1 discard is
  intentionally unchanged for v1), double-encoded data decoded, ~60-type v2
  event union (`api2/events.dart:118–403`), adapted to v1 envelopes
  (`gateway_events.dart`), unknown events never throw; client-side reconcile
  on reconnect.
- **Read path**: cursor pagination with a bounded 20-page hydration walk
  (documented caveat in the plan), v2 message union mapped onto the renderer
  including v2-only variants (`v2:switch`/`v2:notice`/`v2:compaction`).
- **Interaction**: send body `{text, files, agents, skills, delivery,
  resume}`; model/agent as session state via `switchModel`/`switchAgent`;
  interrupt replaces abort; permissions with reject-with-message bound to
  exact request IDs (incl. notification replies); **forms replace questions**
  across chat cards, Requests, Mission Control, and the form renderer;
  **inbox/steer** shipped (`PendingSendsStrip` merging offline drafts + v2
  inbox sends, steer-on-tap, delivery picker on long-press, send-while-busy
  when `supportsInbox`).
- **Surfaces**: files (binary-sniffed bytes), VCS diff (session diff
  collapsed onto working-tree diff), PTY over connect-tickets + WS with
  cursor resume, catalog/config/commands/skills/references, MCP list/add/
  connect/disconnect.
- **Capability gating** (§7 risk solved): `ServerCapabilities` truth table;
  share/archive, steal/warp/orgs, LSP/formatter/git-init, remote upgrade,
  shell settings, diagnostics send, MCP OAuth, symbols, tool inventory,
  message delete, todos, worktree reset — all hidden or scoped-errored on
  v2, backed by `test/v2_feature_gating_test.dart`.
- **Carried risks closed**: offline queue flushes through the gateway (no
  payload migration needed); wake reconciliation preserved over the
  protocol-neutral pair; single `/api/event` with client-side location
  filtering; integration attempts carry `integrationID` (in-memory, honest
  cross-restart limitation).

**Phase 4 (unchecked in the plan — accurate):** integrations/credentials
shipped at transport+UI level; **absent**: inbox Mission-Control surface,
session import/export, staged revert UI, instructions, websearch, message
editing, session stats, Termux flip to `opencode2` (bootstrap still installs
`opencode-ai`).

**Bugs found in the migration (new, this pass):**

| Sev | Issue | Where |
|---|---|---|
| **High** | `_selectLocation` rebuilds the **v1** transport regardless of flavor; invoked on connect when a saved location exists, from the location picker, and from move/warp. On a v2 profile the just-succeeded v2 connect is followed by a v1 health check that cannot succeed (mislabelable as a rotated password). No test covers flavor=v2 + saved location. | `lib/state/connection.dart:3109–3114` (vs the flavor branch at `:665–670`) |
| Med | Every v2 send re-issues `switchModel`/`switchAgent` even when unchanged — two extra round-trips per prompt; silently overrides model/agent changed by another client on the same session. | `gateway.dart:242–261`, `chat_screen.dart:1114–1123` |
| Med | `session.diff` on v2 silently returns the **working-tree** diff, not the session's — chat diff counts can over-report. | `gateway.dart:152–161` |
| Low | Two concurrent SSE subscriptions to the same `/api/event` (location + global channels) — works but doubles stream load. | `connection.dart:1241–1291` |
| Low | Fork anchor mapping is lossy ("before" excludes the long-pressed message) — documented in code, user-visible. | `gateway_operations.dart:120–140` |
| Low | Dead-code risk: `withAuthToken`/`authTokenUri` would put base64 creds in URLs if adopted — keep un-logged. | `transport.dart:65–84` |
| Low | Password field always visible in the editor (plan says v2-only); defensible but doc/UI disagree. | `servers_screen.dart:931–988` |
| Doc | Port plan Phase-3 note "capability gating in flight" is stale — gating is done and tested. | `docs/opencode2-port-plan.md` |

## 2. Findings re-verification

### Base audit + Lens 1 (chat): 23/26 FIXED, 3 PARTIAL, 0 open

Highlights — F1 label, F2 Termux palette, F3 status-dot Semantics, E1
haptics, E2 double-back-to-exit, E5 global text-scale clamp, A1 **per-session
persisted drafts** (50-draft cap, respects provisional cleanup), A2 **image
paste via IME commitContent**, A4 **widget tap-through deep link** all
landed. C1 streaming fix is the real mechanism (50 ms trailing-window
flushes, content-keyed markdown cache, open-fence highlight skip, LRU-48
parse cache, test counters). C3 expansion hoisted to a session-scoped store;
C5/C13/C6 preview caps and `cacheWidth` on tool thumbnails; C14 pinned-count
anchor fix.

Partials:

- **E4**: flush now announces "Sent N queued prompts · M drafts waiting for
  other servers", but flush is still **active-profile-only** (names the
  skipped ones instead of flushing) and the confirmation only fires from an
  open ChatScreen.
- **A3**: localization **scaffolding fully present** (`l10n.yaml`, `app_en.arb`,
  delegates, `generate: true`) but only ~2 strings converted — app remains
  hardcoded English.
- **C6**: tool-card thumbnails decode at preview size; the full-screen
  preview sheet still decodes full-res (defensible for 5× zoom, but the
  cited site is unchanged).

New issues spotted (regressions/side-effects of fixes):

1. **Global reasoning toggle now persistently overwrites per-part expansion
   choices** for the whole session (the C3 store amplifies a transient
   override) — `message_view.dart:1591–1598`.
2. Assistant prose lost partial text selection (intended C2 trade-off; whole-
   message copy only) — consider a selection mode.
3. C10 overcorrected: no "earlier messages" affordance at all for 10–30
   message transcripts, and no "N new below" badge on jump-to-latest while
   pinned.
4. Text-scale clamp `clamp(1.0, 2.0)` also clamps **up** users below 1.0.
5. The streaming message itself still re-parses its full buffer per 50 ms
   flush (memo is whole-message-keyed; per-block memoization not done).
6. Drafts persist **text only** — staged attachments are lost on navigation.
7. One raw green survives in the very screen F2 cleaned
   (`termux_setup_screen.dart:797`).
8. E4 confirmation is chat-scoped; flush completing elsewhere is unannounced.

### Lens 2 (states) + Lens 3 (touch): 23/24 FIXED, 1 PARTIAL, 0 open

All twelve S/T mechanisms verified present, including the S1 skeleton guard
(`_loading && _messages.isEmpty`), `productErrorText()`/`showProductError()`
funnel, `errorBodyDetail()` extraction, tools/files refresh keeping stale
rows, Dismissible swipe-delete on both session lists, keyboard-dismiss on
sheet drags, review comment scroll-constraint + pull-to-refresh + bottom
hunk bar + 44dp mode buttons, global comfortable ChipTheme, themed nav height.

Partial: **S11** — all cited sheet sites converted except
`lib/voice/notices.dart:32–37` (bare spinner + raw `${snapshot.error}`, no
retry).

New issues:

1. **Raw `error.toString()` snackbars bypass the new funnel** at
   `saved_permissions_screen.dart:146`, `prompt_editor.dart:64`,
   `integrations_screen.dart:146, 824`, `markdown.dart:89`,
   `appearance_picker.dart:73`.
2. **`worktrees_screen.dart` `_showError(error.toString())`** (5 sites) can
   reintroduce the exact "Bad state:" leak the audit fixed — plus
   `coding_settings_screen.dart:145`, `mcp_setup_screen.dart:99`.
3. Message-actions affordance (T1 fix) is ~28dp — below the 44dp floor the
   codebase now enforces elsewhere.
4. Bottom hunk bar disappears while a selection is active (selection bar
   takes its slot).
5. T3 residual: `initialChildSize` not shrunk under the keyboard — first
   tap-to-type still covers half the list on short devices.

### Lens 4 (visual) + Lens 5 (first-run): 8/24 FIXED, 2 PARTIAL, **14 NOT FIXED**

Fixed: V11 (review states onto shared components), O1 (first-prompt dead end
— pill renders in the empty state, `directory` omitted so the server scopes
to default), O2 (Save auto-connects new profiles), O3 (distinct DNS verdict
via `SocketException.osError`), O5 (honest 10–15-minute copy), O6
(project-aware suggestion chips), O8 (guide pointer on timeout/refused), O9
("Retry — resumes where setup left off").

**Not fixed — the entire visual token cluster**: V1 raw status greens/oranges
(`managed_workspaces_screen.dart:351`, `integration_tiles.dart:685/687`,
`termux_setup_screen.dart:797`), V2 raw green diff tint
(`session_sheets.dart:439/471`), V3 `success(scheme)` fallback sites,
V5 hairline alphas, V6 off-scale font sizes, V7 radius stragglers, V8
copy/bolt/stop glyph drift, V9 connecting-role split, V10 title-role
outliers, V12 hand-rolled section labels, V13 `'AppMono'` literals (~67, up
from ~60), V14 spacing stragglers, V15 floating-surface recipes. Partial:
V4 (`hintColor` down ~61→~44, no semantic muted role), O7 (tip row covers
commands + long-press but not Review/Mission Control).

New issues:

1. **A third hardcoded green in Review** — `review_workspace.dart:2025–2032`
   (`_additionColor` hex `0xff78c59d`/`0xff176b4b`) bypasses both
   `AppTheme.success` and pack-aware `successOf`; theme packs will render
   alien greens in diff rows.
2. The success **plumbing exists** (`theme_packs.dart:23`,
   `app_theme.dart:36–38`) — only call-site migration is missing.
3. Drift is spreading, not shrinking: `permission_sheet.dart:228` adds a new
   `fontSize: 12.5`; bolt/stop glyph drift now occurs **within single
   components** (`composer.dart:193 vs 535`; `chat_screen.dart:3419` vs
   `composer.dart:468`).

### Stack & desktop review: ~20% shipped, priorities untouched

**Shipped since the review:** `EntranceReveal` staggered list entrances
(ticker-driven, reduced-motion aware, +tests), **swipe-to-delete session
rows** (`Dismissible` in sessions tab + workspace), window **minimum size**
(480×600), partial settings platform-gating (background category, Termux-
managed row), i18n scaffolding (see A3), widget deep-link (verified under
the base audit as A4).

**Absent — including every review priority:**

- **#1 drift/SQLite baseline**: zero movement (no dep, offline queue still a
  prefs JSON blob, no cache/drafts-table/FTS/event-log). Note the chat-open
  skeleton (S1) is fixed only for *rehydrates*; first open of an uncached
  transcript still shows a skeleton — a local cache is still the real fix.
- Share intent, app-level shortcuts (Ctrl+K/Esc), right-click menus,
  markdown toolbar, drag-drop attach: absent.
- TTS read-aloud: absent.
- Shimmer skeletons, real Hero transitions, theme-pack lerp, count-up
  tickers: absent.
- Quick Settings tile, app shortcuts (`shortcuts.xml`): absent.
- Desktop: **Phase 1 mostly open** — `termux/bridge.dart:57` still catches
  only `PlatformException` (the exact flagged bug); `termux_setup_screen`
  has zero platform checks and `/termux-setup` is registered unconditionally
  (`main.dart:341`); servers screen pushes Termux setup ungated. Scrollbars
  still 3 files; window position/size persistence absent; no desktop
  shortcuts/context menus; no CI build-linux/windows jobs or packaging; no
  Windows runner; voice desktop shim absent. Shorebird-vs-GitHub update
  split confirmed working as designed.

New issues:

1. **CI gate dead** (billing) — see headline.
2. **Voice entry is a latent desktop half-feature**: the chat mic button has
   no platform check and the desktop device stub reports
   `hasMicrophone: true` + "granted", so desktop users can enter voice setup
   and record into Android-shaped paths. Hide it off-Android until the
   desktop shim exists.
3. Stack section of `stack-gaps-and-desktop.md` is now stale (pubspec gained
   file_picker/localizations; version 1.0.28+29).

---

## Remaining work, prioritized

1. **Fix the v2 `_selectLocation` flavor bug** (High) + a
   flavor=v2-with-saved-location regression test; then the switch-on-every-
   send and session-diff-scope issues.
2. **Restore CI** (Actions billing) — the release gate is currently
   unevidenceable.
3. **Lens-4 visual sweep** (14 open findings): one status-color helper +
   `successOf` migration, hairline/font/radius/icon-alias normalization.
   The new `review_workspace.dart` hardcoded green goes first.
4. **Close the fix regressions**: reasoning-toggle overwrite, worktrees/
   misc raw-error snackbars through the funnel, S11 voice notices, message-
   actions 44dp, drafts-with-attachments, C10 short-transcript case.
5. **drift/SQLite baseline** in the next native release (unchanged priority).
6. **Desktop Phase 1**: bridge `MissingPluginException` guard, gate
   termux screen + route + servers entry, hide the desktop voice mic.
7. Phase-4 v2-native features (export/import, staged revert, instructions,
   websearch, Termux `opencode2` flip) per the port plan.

---

## Addendum — 2026-08-30 re-check (HEAD `17a42bf`, 69 commits after this report)

Gates: analyzer **clean**; **1021/1021 tests pass** (was 811).

### What landed (verified)

- **Lens 4 visual sweep (`6bba267`) — 12/15 FIXED**: new
  `AppStatusTone`/`AppTheme.statusColor` (zero raw `Colors.green/orange`
  left in `lib/`), `mutedOf` semantic role (`hintColor` 61→**0**),
  `AppTheme.hairline`, `AppIcons` alias block (zero glyph synonyms), font
  and radius stragglers swept, spacing stragglers gone, review hex greens
  → `successOf`. Partial: V10 (two title re-derivations), V12 (two
  hand-rolled labels), V13 (one `'AppMono'` literal). Open: V15
  floating-surface recipes.
- **Fix-regression cluster (`7287ab6`) — 7/13 FIXED**: R1 reasoning toggle
  no longer persistently overwrites per-part choices, R3 text scale now
  one-sided up to **2.5×**, R6, R8 (voice notices → shared states), R9
  (44dp message-actions), R11 (all six raw-error snackbars), R12
  (worktrees/settings through the funnel). R5 partial (attachment loss now
  disclosed at selection time). Still open: R2 (short-transcript pill +
  "N new below"), R4 (streaming message still re-parses per flush), R7
  (flush still active-profile-only, chat-scoped announce), R10 (hunk bar
  vs selection bar — now documented as deliberate), R13 (sheet
  initialChildSize under keyboard).
- **v2**: credential-in-URL helpers deleted (`transport.dart` — B6 fixed);
  session-diff scope honestly labelled in review-handoff references
  (B3 partial — the Review "Session" tab still shows the working-tree diff
  on v2); fork anchor loss compensated in the composer (B5 partial).
- **Desktop — Phase 1 effectively complete (`631fb16` merge)**: platform
  capability seam (`lib/platform/platform_capabilities.dart`) with every
  Termux surface + the `/termux-setup` route gated, pinned by tests; bridge
  catches `MissingPluginException`; voice reports unavailable off-Android
  and the chat mic is capability-gated (no more fake microphone).
- **Desktop Phase 2/3 partially landed**: window size/position/maximized
  persistence (versioned key, display-clamped, tested); packaging shipped
  (`linux/packaging/` `.desktop` + hicolor icons + AppStream metainfo,
  `scripts/package-linux.sh` → tarball + `.deb` + SHA256SUMS, contract
  tests); **new `desktop-linux.yml` CI gate** (analyze, chunked serial
  tests, release build + `ldd`, packaging, tag release job);
  `docs/desktop.md` (honest experimental status, including "the window has
  never been seen on a display").
- **Queue + composer**: bounded offline queue (50 entries / 3×20 MiB /
  14 days, eviction notice — `e91f20e`); labelled **Steer/Queue** delivery
  control replacing the hidden long-press (`c80652b`).
- Also since yesterday: session-first Workspace, unified Activity surface
  (Mission Control + Requests), simplified composer, review-to-prompt
  handoff, an automated accessibility gate (`f24643e`), public-launch
  governance (fail-closed profile deletion, server local-data deletion,
  non-affiliation disclaimer, regenerated notices), IPv6 loopback
  normalization.

### Still open after the 08-30 wave

1. ~~**HIGH — the v2 `_selectLocation` flavor bug survived**~~ →
   **RESOLVED 2026-08-31 (`0ce258c "Restore the v2 rescope fix and guard it
   at the source"`).** Timeline: fix authored `ceb3fe2` → its test
   deliberately dropped `41dcddb` (unsound fake that hung the suite; the
   product fix stood) → the fix itself lost in merge `e44ccda` (caught by
   this report's 08-30 re-check) → re-landed with
   `test/connection_transport_factory_guard_test.dart` and an in-code note
   that the fix "has already been lost to a merge once". `_apiFactory` now
   has no callers outside the flavor-aware builder; the guard test passes.
   Not ignored: only the test drop was deliberate, and it was documented.
   (Validated 2026-08-31 after the question "was it ignored on purpose" —
   see the git forensics in that session.)
2. **CI billing block still stands** — both `android-quality.yml` and
   `desktop-linux.yml` headers state they have never run (account payments
   failed). The new Linux gate is unproven in CI.
3. B2 `switchModel`/`switchAgent` re-issued on every send (clobbers other
   clients' selections; 2 extra round-trips).
4. B4 two concurrent `/api/event` subscriptions (the code comment now says
   "v2 has a single stream" while opening two).
5. B7 password field still unconditional in the editor (copy softened).
6. drift/SQLite, share intent, TTS, TileService/shortcuts.xml, desktop
   Ctrl+K/context menus/scrollbars: all still absent (unchanged priorities).
7. V15 floating surfaces; R2/R4/R7/R13 residuals; two fresh small leaks —
   review full-page error renders `error.toString()`
   (`review_workspace.dart:1918–1929`) and `form_renderer._messageOf`
   hand-strips only `"Exception: "`.
8. docs/desktop.md drift: voice gate is now
   `platformCapabilities.supportsVoice` (not `Platform.isAndroid`), and
   Ctrl+Enter exists (doc says it doesn't).
