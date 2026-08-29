# Stack, componentry, and desktop-readiness review

Review date: 2026-08-29. Branch: `production/android-release-hardening`.
Companion to [`docs/ui-feature-audit.md`](ui-feature-audit.md) and
[`docs/ui-audit-lenses.md`](ui-audit-lenses.md). Every "missing" claim below
was verified by code grep on this date unless marked assumed.

Current stack (pubspec): dio, flutter_riverpod, highlight,
flutter_secure_storage 10, crypto, opencode_sdk (local), path_provider,
record, scrollable_positioned_list, sherpa_onnx, shared_preferences,
shorebird_code_push, url_launcher, uuid, xterm, dynamic_color,
package_info_plus, window_manager.

---

## 1. Local database — the biggest structural gap

**Verified: zero SQLite/drift/Isar/Hive anywhere.** Persistence is
`shared_preferences` only — including the offline compose queue, which lives
as a JSON blob under one prefs key with a 20 MB per-entry cap
(`lib/state/offline_queue.dart`).

Consequences already visible in the audits: chat flashes to skeleton on every
rehydrate (S1), drafts die on session switch (A1), no offline reading of past
chats, no local search over history, queue entries have no status/retry
columns.

**Recommendation: `drift` (sqlite3).** Type-safe, actively maintained, fits
the repo's test-heavy style. Enable FTS5. Tables to start:

| Table | Unblocks |
|---|---|
| `message_cache` (session, parts JSON, updatedAt) | Instant chat open (S1), offline reading, FTS history search |
| `drafts` (sessionId, text, selection, attachments) | A1 cross-session drafts |
| `outbox` (status, attempts, nextAttemptAt, payload) | Real queue semantics: retry/backoff, per-entry states, multi-profile |
| `event_log` (v2 port synergy) | HANDOFF documents `history`/`replay` as "deliberately unused (client-held event log)" — a local log makes replay/rewind real, unique differentiator |
| `kv` | Widget snapshot, window geometry, misc |

Guardrails (repo's own rules): the cache is **derived data only** — the
server stays authoritative; purge on disconnect (PRIVACY.md compliance);
enqueue/flush tests port from the current queue suite.

**Ship constraint:** `sqlite3_flutter_libs` is a native dependency → requires
a **full release**, cannot ride a Shorebird patch to 1.0.27. Bundle it with
the next native baseline (the v2 port will need one anyway).

## 2. Keyboard & input

Verified gaps:

- **No inbound share intent** — zero `ACTION_SEND`/receive-share handling in
  `lib/` or the Kotlin side. Sharing code from a browser/GitHub app into a
  prompt is a core mobile-coding loop and costs one intent filter + a
  composer prefill.
- **App-level keyboard shortcuts absent** — `Shortcuts`/`Actions` exist only
  in the composer (Ctrl/Cmd+Enter). Missing: Ctrl+K → existing command
  launcher, Esc → abort/close sheet, Ctrl+1–4 tab switch, Ctrl+F → timeline
  search. (Also feasibility doc Phase 2 list.)
- **No right-click context menus** — zero `onSecondaryTap`/`contextMenuBuilder`
  usage; matters for desktop parity.
- Image paste (A2) and a markdown formatting row (bold / inline-code / fence
  / link buttons above the keyboard) — composer is a plain field today.
- Desktop bonus once present: drag-and-drop file onto window → attachment.

## 3. Animations

Constraint (do not relitigate): `flutter_animate` stays banned — its timers
break `flutter_test` pending-timer checks; entrances use
ticker-driven `TweenAnimationBuilder`.

Concrete gaps, verified:

- **Staggered list-item motion** — the one facelift lane never finished
  (HANDOFF remaining list).
- **Skeletons are static** — no shimmer/pulse on `LoadingList`; a subtle
  pulse via TweenAnimationBuilder is the cheap upgrade.
- **Hero/shared-element transitions essentially unused** — one `Hero(` in
  the entire app (`session_context_screen.dart`). Candidates: workspace
  session card → chat, context gauge → context screen.
- **Theme-pack switching snaps** — lerp the `ColorScheme` over ~250 ms.
- **Count-up tickers** for tokens/cost/context meter numbers.
- Prerequisite: land C1 (streaming batching) first — animation on top of
  per-token full re-renders decorates jank rather than fixing it.

## 4. In-app chat components

What exists is strong (streaming, tool cards, timeline, fork, offline queue,
voice STT). Verified missing vs the modern AI-chat baseline:

- **TTS read-aloud** — zero TTS in `lib/`. `sherpa_onnx` ships offline TTS
  models; read-aloud of assistant responses completes the hands-free loop
  with the existing STT and matches the on-device privacy story.
- **Quote/reply into composer** — grounded comments exist in Review; chat has
  fork-from-prompt but no "quote this message above my reply."
- **Regenerate last response** — abort and retry exist; a one-tap re-run of
  the final prompt after a bad answer does not.
- **Message editing** — server-side edit exists only in v2
  (`v2.session.messageUpdate`); fold into the port plan rather than v1.
- **Swipe between sessions** — zero `Dismissible`/horizontal drag app-wide
  (T2); also the natural home for swipe-to-archive on session rows.
- Multi-select/bulk message actions — low priority; delete is per-message.

## 5. Icons & identity

- Material Symbols tree-shaken; discipline is near-total except the V8 verb
  drift (copy/bolt/stop glyphs) — fix via an icon-alias block in `AppTheme`.
- **Ship constraint worth codifying:** new Material glyphs historically broke
  Shorebird patch compatibility (Patch 3 had to substitute already-shipped
  glyphs). Treat "new icon glyph" like a native change: full-release only,
  or maintain a pre-approved glyph whitelist.
- Launcher identity is done (adaptive, round, Android 13 themed per
  HANDOFF); Files already has tinted type icons per the design doc.
- Optional differentiator: provider monograms in the 349-model picker —
  self-drawn letter chips + per-provider scheme accent (avoid trademarked
  logos; keep colors scheme-derived, not brand-derived).

## 6. Original-advancement candidates (differentiators, not parity)

1. **Local-first event log + replay** (from §1) — rewind a session offline,
   diff-what-changed-while-away. Nothing in the upstream clients does this.
2. **FTS history search** — "which chat fixed the Gradle signing issue?" across
   every project the phone has touched.
3. **Hands-free loop** — STT (exists) + TTS (missing) + read-aloud-while-
   screen-off via the existing foreground service.
4. **Share-target + Quick Settings tile + app shortcuts** — all verified
   absent (`res/xml/` has only widget/backup configs; no `TileService`, no
   `shortcuts.xml`); each is a small native slice with outsized reach.
5. **Widget v2** — interactive new-session button; per-project widget
   variants.

## 7. Desktop readiness review

`docs/desktop-feasibility.md` (2026-08-28) green-lit desktop and proved the
unmodified app builds a working Linux release binary with pinned Flutter
3.47.1. Code state on this branch:

### Done and verified ✅

- `linux/` runner committed (CMakeLists/runner).
- Every dependency has a desktop implementation (feasibility table; the
  package choices were deliberately desktop-safe). Voice native libs
  (`sherpa_onnx_linux/windows`) and `record` desktop backends are in the
  lockfile.
- `window_manager` bootstrap in `main.dart` (ensureInitialized →
  waitUntilReadyToShow → show/focus).
- **Desktop update story already built and well-tested**:
  `lib/update/desktop_release_check.dart` — GitHub-releases check with
  build-number comparison, 15-min throttle, resume re-check, View action.
  Mirrors the Shorebird notice lifecycle; `shorebird_code_push` safely
  no-ops off-Android.
- Responsive layouts already desktop-class: NavigationRail ≥760 (extended
  ≥1040), Files master-detail ≥900, Review two-pane ≥840, transcript caps.
- `lib/background/live_background.dart` degrades safely (catches
  `MissingPluginException`).

### Phase 1 gaps — gating (should be first) ❌

- `Platform.isAndroid/Linux/Windows` checks exist in **only 3 files**
  (`main.dart`, `voice/device.dart`, `desktop_release_check.dart`). The
  feasibility doc's Phase-1 requirement — gate Termux entries and
  Android-only settings rows — is **not implemented**: no platform checks in
  `termux_setup_screen.dart`, `servers_screen.dart`, or Settings screens.
  On desktop today: a dead Termux setup path, Shorebird/background-live
  rows for features that cannot work.
- `lib/termux/bridge.dart` catches `PlatformException` but not
  `MissingPluginException` (feasibility doc flags this exact bug).
- Voice stays Android-gated until `voice/device.dart` returns a desktop
  profile shim (capture itself would work via record+sherpa).

### Phase 2 gaps — desktop-class feel ❌

- Scrollbars in only 3 files (`review_workspace`, `termux_setup`,
  `file_preview`) — no always-visible desktop scrollbar policy.
- No hover-focused polish, no right-click context menus (verified zero).
- Window: no `setMinimumSize`, no remembered size/position (grep: zero).
- Keyboard: only composer Ctrl+Enter; no Ctrl+K/Esc/Ctrl+1–4 surface.
- Desktop notifications parity (background-live) — needs
  `local_notifier`/D-Bus slice; Android-only today (safe no-op).

### Phase 3 gaps — distribution ❌

- No CI `build-linux`/`build-windows` jobs (current workflow: analyze/test +
  Android lint/APK only).
- No packaging: no `.desktop` file/AppImage/tarball step, no Windows runner
  scaffold, no MSIX/signing decision.
- Host toolchain note (this machine): install `libstdc++-14-dev` or pass
  `--gcc-install-dir` (feasibility doc §toolchain) or release builds fail
  with `cannot find -lstdc++`.

### Verdict

**Feasible and close, but Phase 1 (gating) is the actual missing work** —
the binary runs, yet a Linux user currently sees Android-only surfaces that
cannot function. Suggested order: (1) gate Termux/background/Shorebird
surfaces off-Android + bridge `MissingPluginException` guard (small,
testable), (2) window min-size/position persistence + always-visible
scrollbars + Ctrl+K/Esc shortcuts + context menus, (3) CI build jobs +
tarball/.desktop + AppImage, (4) voice desktop shim + desktop notifications,
(5) MSIX/signing decision for Windows.

## Priority synthesis (this review + prior audits)

1. **drift/SQLite baseline** in the next native release (unblocks S1, A1,
   queue hardening, history search, event replay).
2. **Desktop Phase-1 gating** — smallest slice that makes Linux shippable.
3. **Share-target + app shortcuts + Quick Settings tile** — cheap native
   reach.
4. **TTS read-aloud** — completes the voice story already 80 % built.
5. **Keyboard surface** (Ctrl+K palette, Esc abort, share→composer, image
   paste) — one composer/shortcuts slice.
6. **Motion pass** after C1 streaming fix (list stagger, shimmer, hero,
   theme lerp).
