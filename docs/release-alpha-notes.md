# Release notes draft — Alpha 1.0.31+32

Owner: paste into the GitHub release body for tag `v1.0.31+32` (or adjust the
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
