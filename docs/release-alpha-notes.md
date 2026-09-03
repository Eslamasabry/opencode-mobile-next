# Release notes draft - Alpha 1.0.34+35

Used by `scripts/cut-alpha.sh` for tag `v1.0.34+35`. Do not publish until the
release signer, exact commit, Android quality run, and physical-device smoke
check have all been recorded.

---

# OpenCode Mobile - Alpha 1.0.34+35

> ⚠️ **This is an alpha release of a vibecoded app.** OpenCode Mobile is
> built heavily with AI assistance by an independent community maintainer.
> It is not affiliated with the official OpenCode project. Expect rough
> edges, breaking changes between previews, and untested corners.

> **Desktop builds are experimental.** Linux is exercised on a virtual display
> in CI. Windows compiles and packages in CI but still needs routine manual
> testing. The Android app is the primary target.

## New in 1.0.34

- Model selection is isolated per conversation instead of changing every open
  session, including queued and mid-turn sends.
- Android and desktop now display the consistent **OpenCode Mobile** identity.
- Settings → About shows the exact app version, package ID, and Android signing
  certificate so update conflicts can be diagnosed before data is removed.
- Notification permission approvals require device authentication on Android
  12 and newer.
- Linux packages use `opencode-mobile` and refuse to overwrite or remove the
  OpenCode server CLI.
- Public security reporting, Discussions, dependency alerts, and stable CI
  test signing are enabled.

## Report a bug (please, genuinely)

The app carries its own bug-report flow now: **More hub → Report a bug**,
the error screens themselves, or Settings → About. The form arrives with
your app version and platform prefilled and nothing else — redact the rest
(no passwords, no keys, no hostnames, no transcripts).

## New in 1.0.33: providers and background

- **Providers**: every provider shows its real logo (fetched from Google's
  public favicon service by domain, monogram fallback), a Connected / Not
  connected state with model count, connected providers first, and the
  Providers section now leads the Integrations screen.
- **Background**: the persistent notification is live — session title, what
  the agent is doing, running and pending counts, a progress bar, Android 16
  live-update promotion, and a Pause action. Background mode is one tap from
  the Workspace header, suggested from the empty Activity inbox, and the
  Settings hub shows whether it is running.

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
