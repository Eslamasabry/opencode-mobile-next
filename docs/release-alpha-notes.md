# Release notes draft — Alpha 1.0.32+33

Owner: paste into the GitHub release body for tag `v1.0.32+33` (or adjust the
tag/version when the actual cut is made). Keep the two warning blocks at the
top verbatim; everything below the fold can be trimmed per release.

---

# OpenCode Mobile — Alpha

> ⚠️ **This is an alpha release of a vibecoded app.** OpenCode Mobile is
> built heavily with AI assistance by an independent community maintainer.
> It is not affiliated with the official OpenCode project. Expect rough
> edges, breaking changes between previews, and untested corners.

> ⚠️ **The desktop builds have never been hardware-tested.** Linux is
> packaged but the window has never been seen on a display; Windows builds
> in CI but has never been launched by anyone. Desktop testing *is*
> contributor testing — if you try one, you are among the first, and the
> in-app **Report a bug** button is the fastest way to tell us what
> happened.

## Report a bug (please, genuinely)

The app carries its own bug-report flow now: **More hub → Report a bug**,
the error screens themselves, or Settings → About. The form arrives with
your app version and platform prefilled and nothing else — redact the rest
(no passwords, no keys, no hostnames, no transcripts).

## New in 1.0.32: the UI/UX refresh

- **Identity**: a new launcher icon drawn from the app's own prompt glyph,
  Space Grotesk headlines, and one sanctioned depth/emphasis recipe.
- **Chat**: permission requests arrive as an inline card above the composer
  instead of a locked modal; selectable answers; cost and tokens behind a
  toggle; streaming and running-tool emphasis; tool groups summarised as a
  sentence; `choices`, `checklist` and `command` blocks the agent can emit;
  a real model name in the chip; long-press to attach; a haptic when a run
  finishes; one session menu; a full-screen diff viewer.
- **Onboarding**: a three-step pairing guide, inline connection errors,
  bottom-sheet confirms everywhere, plain-language Termux steps.
- **Workspace**: wrapped diffs on phones, Activity as a pure inbox, Terminal
  one tap away, swipe-to-archive with Undo, fewer nouns ("Move", "Cloud
  environments", "This computer").
- **Server data the TUI cannot show**: retry countdowns, tool durations,
  actionable error cards, permission previews with the exact command and a
  diff, model pricing, agent colours, session cost and diff size.
- **Platform**: share text from any app into a new session; predictive back;
  Riverpod 3; `flutter_secure_storage` 11 (see the upgrade note below).

### Upgrade note: stored credentials

`flutter_secure_storage` 11 drops the legacy Android ciphers that 1.0.31
(v10) migrated away from. Upgrading from 1.0.31 keeps every saved server.
Upgrading from **1.0.30 or earlier straight to this build** loses saved
server credentials: the app opens on the welcome screen and you pair again.

## What is in this cut

- **OpenCode 1 and OpenCode 2 (beta)** — one app, dual-stack. The connect
  flow detects the server flavor; v2 adds the inbox (steer or queue a
  prompt while a run is live), forms, and pairing by QR scan or paste.
- **Session-first Workspace**, one unified **Activity** surface (requests,
  forms, alerts), a simplified composer, and **review-to-prompt** staging.
- **Desktop**: Linux packages (`.deb` + tarball + SHA256SUMS, reproducible),
  window state persistence, keyboard shortcut layer (Ctrl+K, Ctrl+N,
  Ctrl+1–4, …), right-click menus, scrollbars, mouse text selection,
  drag-and-drop file attach. Windows: CI-built zip, experimental.
- The full audit-and-fix trail is in `docs/audits/` and
  `docs/reverification-report.md` — including what is still known-open.

## Install

- **Android**: attach the APK from this release; upgrade in place over
  previous previews (same signing lineage). `scripts/release.sh` built it;
  Shorebird receives Dart-only patches for this baseline.
- **Linux**: `.deb` (Ubuntu 24.04-era dependencies) or the portable tarball;
  SHA256SUMS attached. See `docs/desktop.md` for runtime requirements
  (libsecret/keyring, zenity) and honest limitations.
- **Windows**: CI artifact only (see workflow runs) — experimental,
  untested, contributors wanted.

## Not in this cut

- Play Store distribution (signing decision still open).
- iOS/macOS. Localization beyond English (the l10n layer exists; `en` only).
- Server-version guarantees: tested against OpenCode 1.18.x and the v2 beta
  this repo pins; other versions may differ.
