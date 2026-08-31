# UI / feature audit — what to add, edit, and fix

> Status: re-verified 2026-08-29 — see
> [`reverification-report.md`](reverification-report.md) for what landed
> (23/26 here) and what remains.

Audit date: 2026-08-29. Branch: `production/android-release-hardening`
(release research tip: master + the OpenCode 2 research pair).

Two inputs were combined:

1. **Documentation lanes** — `docs/internal/handoff.md`, `docs/opencode2-port-plan.md`,
   `docs/opencode2-port-matrix.md`, `docs/design-inspiration.md`,
   `docs/traycer-oss-fit.md`, and the git history of the facelift slices.
2. **Code-level audit** — a read-through of navigation, composer, theming,
   accessibility, platform behaviors, and platform-channel wiring on the
   current tree.
3. **Follow-up lens audits** — five parallel deep passes (chat rendering,
   states/feedback, touch ergonomics, visual consistency, first-run journey)
   recorded in [`docs/ui-audit-lenses.md`](ui-audit-lenses.md) with their own
   priority view and cross-lens convergences.

Confidence notes: file/line references were verified against the tree on the
audit date; the widget tap-through deep-link item is "not found" rather than
"proven absent".

---

## 1. Fix — actual bugs / inconsistencies

| # | Finding | Where |
|---|---|---|
| F1 | Bottom-nav label mismatch: 4th destination is labeled `'More'` in the nav bar but opens `LibraryScreen`, whose AppBar title is `'Library'`. One rename fixes it. | `lib/ui/screens/home_screen.dart:83–86` (label), `:199` (`_titles`), `:64` (tab body) |
| F2 | Termux setup theming leaks: step circles use raw `Colors.green.shade400` (bypasses the pack-aware `AppTheme.successOf`), `Colors.white` step text on a translucent `hintColor` circle (low contrast in light themes), and a fixed dark terminal-preview palette. Breaks the otherwise complete theme-pack/Material You system. | `lib/ui/screens/termux_setup_screen.dart:916, 925, 931, 990–1040` |
| F3 | `_StatusDot` has no `Semantics` label; connection status is conveyed only visually/tooltip. TalkBack users get nothing from the dot (the connection banner does have Semantics). | `lib/ui/screens/home_screen.dart:282–300`; contrast `connection_status_banner.dart:38` |

## 2. Edit — polish gaps

| # | Finding | Where / note |
|---|---|---|
| E1 | **Zero haptics in the entire repo** — no `HapticFeedback` use anywhere. Candidates: send, queue-on-disconnect, tool-run complete, error, destructive confirms. | repo-wide grep |
| E2 | **No back-to-exit guard** — root `HomeScreen` has no `PopScope`/`SystemNavigator`; a single back press exits instantly. (`PopScope` is correctly used in chat, voice sheet, prompt editor, and servers.) | `lib/ui/screens/home_screen.dart` vs `chat_screen.dart:2969–2973` |
| E3 | Mission Control is a real, controller-driven screen but reachable **only** via one toolbar icon. A Library card or a tab slot would surface it. | `home_screen.dart:111–115` → `mission_control_screen.dart:18` |
| E4 | Silent queue flush: queued drafts send on reconnect with no success confirmation, and flush processes only the **active profile's** entries. | `lib/state/connection.dart:2255–2297` (per-profile filter at 2259) |
| E5 | Text-scale safety is per-screen: no global clamp in `main.dart`'s builder. 12 screens handle `textScalerOf` well (e.g. `library_screen.dart:165` clamps 1.0–2.0); unguarded screens can overflow at 2.0x. | `lib/main.dart:303–345`; reactive examples `servers_screen.dart:730`, `voice_ui.dart:446`, `command_launcher.dart:188` |

## 3. Add — missing features

| # | Feature | Detail |
|---|---|---|
| A1 | **Cross-session draft persistence** — the composer's `TextEditingController` is per-route; navigating to another session and back loses typed text. Only *queued* (offline) drafts persist today. A per-session draft map closes this. | `chat_screen.dart:232` (single `_composer`), `_leaveChat` at 2908–2926 only offers Keep editing / Discard |
| A2 | **Image paste into the composer** — no `onPaste`, clipboard-image, or pasteboard package anywhere; the only attachment path is `file_picker`. | composer/`chat_screen.dart:1151` |
| A3 | **Localization** — none: no `.arb` files, no `flutter_localizations`/`intl`, no `l10n.yaml`, no delegates on `MaterialApp`; all strings are hardcoded English. Biggest structural gap for store reach; RTL locales get untranslated chrome. | `lib/main.dart:300–345`, `pubspec.yaml` |
| A4 | **Widget tap-through deep-link** — the sessions home-screen widget is fully wired Flutter↔native (`widget_snapshot.dart` → `SessionsWidgetProvider.kt` → `MainActivity.kt:125`), but no widget-side tap handler routes into the existing `/chat/<id>` route (`main.dart:330–343`). Plumbing exists; wiring unverified. | `lib/background/widget_snapshot.dart`, `android/.../SessionsWidgetProvider.kt:33` |

## 4. Verified complete — no action needed

- **Terminal is a first-class bottom-nav tab** (the old "Terminal's bottom-nav
  slot" facelift lane is done), kept alive through tab switches via
  `IndexedStack` (`home_screen.dart:162`).
- **Offline compose queue** is the best-wired feature: queue on disconnect
  (`chat_screen.dart:896–908`), persistence with 20 MB cap
  (`offline_queue.dart`), inline pending strip with Edit/Discard
  (`message_view.dart:1309–1365`), oldest-first flush that stops on transport
  failure, mid-send queueing, and banner count.
- **Accessibility is strong**: 78 `Semantics`/`semanticLabel` usages across
  25+ files; no bare icon buttons found in spot checks (tool cards, markdown,
  files, message view, terminal keys).
- **Theming infrastructure is complete**: dark/light, `dynamic_color`
  Material You harvest with graceful pre-12 fallback, 3 static theme packs,
  success color as a `ThemeExtension`. Only F2 leaks.
- **Tablet/wide layouts**: NavigationRail ≥760 px, extended ≥1040 px, chat
  capped at 860 px, desktop window sizing.
- The lone `TODO` grep hit (`session_sheets.dart:53`) is a decorative
  `'TODO LIST'` header string, not unfinished work.

## 5. Strategic lanes (from the project's own docs)

### OpenCode 2 port — the dominant open lane

`docs/opencode2-port-plan.md`: Phases 1–4 all unchecked. Only research has
landed (`port/api2-core`: typed client; `port/domain-interface`: gateway
interfaces). Known breakage to carry during the port
(`docs/opencode2-port-matrix.md` risk list):

- `lib/api/sse.dart:133` discards `event:` lines — immediate functional break
  if v2 puts the event type there; v2 declares zero event names, so the
  contract must be captured from a live beta server.
- Chat hydration assumes an unpaginated message list; `v2.message.list`
  paginates.
- `QueuedPrompt` (offline queue) stores v1 per-prompt model/agent/variant;
  migration needed for already-queued drafts.
- Pending integration-OAuth attempts must start persisting `integrationID`.
- ~25 features have **no v2 equivalent** (workspace inventory/warp/sync-steal,
  session share/archive, todos sheet, symbols tab, LSP/formatter health, tools
  inventory, MCP OAuth, remote upgrade, diagnostics send, git init, shell
  settings, app.log, project.initGit) — feature-flag by detected server
  flavor, never silent 404s.

### v2-native feature candidates (post-port)

Inbox queue/steer UI; persistent PTY + per-session terminals with snapshot
reads; session import/export (principled sync-steal replacement); staged
revert (stage → review → commit); per-session instructions sheet; server-side
websearch composer tool; session stats dashboard; message editing; view
tracking (suppress duplicate notifications while chat is open); credential
rotation without reconnect; `vcs.branches` picker for worktree/diff scopes.
Full list: `docs/opencode2-port-matrix.md` §3.

### Deferred-but-documented product direction

- **Agent tree / task cockpit** (`docs/traycer-oss-fit.md`): owner-deferred;
  native direction recorded — root + child sessions as one live hierarchy,
  then Files/Review/Todos/Terminal/Requests as tabs for a selected root task,
  then native Spec/Ticket/Story/Review artifacts. Do not start without an
  owner go-ahead.
- **Desktop**: Linux builds work; Shorebird code-push is the only desktop gap.

### Residual verification debt (not code work)

- Session steal (`/sync/steal`) never proven live against a managed workspace.
- Notification permission/question actions need a permission-gated model run
  for full device proof.

## 6. Priority view

1. **Quick wins (hours):** F1 nav label, F2 Termux colors, F3 status-dot
   semantics, E1 haptics, E2 back-to-exit.
2. **Feature slices:** A1 draft persistence, A2 image paste, A4 widget
   deep-link, E3 Mission Control entry, E4 flush UX.
3. **Strategic:** A3 localization groundwork, then the OpenCode 2 port
   (Phase 0/1 first: dual-stack transport + flavor detection + SSE — it
   blocks everything else and holds the riskiest unknowns).

## 7. Localization groundwork (A3 — landed 2026-08-29)

The rails for l10n are in place; the app itself remains English-hardcoded
except for one pilot surface (the More hub's destination grid + the app
title). To continue converting a surface:

- **Where strings live:** `lib/l10n/app_en.arb` (template locale; add
  `app_<locale>.arb` siblings for new languages). Keys follow
  `<surface><Thing>` camelCase (e.g. `libraryRequestsTitle`) with an
  `@key` description for translators.
- **Generation:** `flutter gen-l10n` (config in `l10n.yaml`; also runs
  automatically on build via `generate: true` in `pubspec.yaml`).
  Output is committed at `lib/l10n/app_localizations*.dart` — it is not
  gitignored on this Flutter (3.47) since synthetic packages are gone.
- **Pattern in widgets:** import
  `package:opencode_mobile/l10n/app_localizations.dart` (or the relative
  path), then `final l10n = AppLocalizations.of(context);` and replace the
  literal with `l10n.yourKey`. The getter is non-nullable
  (`nullable-getter: false`), so any widget test that pumps the converted
  surface in a bare `MaterialApp` must add
  `localizationsDelegates: AppLocalizations.localizationsDelegates` and
  `supportedLocales: AppLocalizations.supportedLocales` (see
  `test/library_screen_test.dart`).
- **MaterialApp wiring:** delegates, `supportedLocales`, and
  `onGenerateTitle` are already on the app in `lib/main.dart`; new locales
  only need an arb file and a regen.
- **Suggested order:** convert per-surface (screen by screen), starting
  with low-churn chrome (Settings, Servers, Guide) and leaving
  `chat_screen.dart` until after the OpenCode 2 port to avoid double work.
