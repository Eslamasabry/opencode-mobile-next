---
name: platform-runtime
description: Use for Android MethodChannels, background services, notifications, Termux, voice, camera, sharing, desktop windows, or native builds.
---

# Platform Runtime

## Boundaries

The app owns five Android MethodChannel families: `oc/termux`, `oc/voice`,
`oc/camera`, `oc/background`, and `oc/share`. Keep Dart method names, argument
maps, result maps, error codes, and native handlers synchronized. Test missing,
malformed, stale, denied, and unsupported-platform behavior.

Ask before adding Android permissions, services, receivers, native dependencies,
or running physical-device/ADB commands.

## Background And Notifications

- The foreground service is `dataSync`, `START_NOT_STICKY`, and keeps the
  existing Flutter process important; it does not own a headless engine.
- Android 15 limits `dataSync` foreground services to six background hours per
  rolling 24 hours. Battery optimization exemption does not remove that limit.
- Timeout or user pause must update in-memory and persisted Dart state.
- Notification actions are bound to exact request and session IDs.
- Allow from a notification means one-time approval and requires device
  authentication where supported.
- Notifications must never include prompts, commands, paths, filenames,
  outputs, request content, credentials, or raw server errors.
- The home widget may show titles because the user placed it, but profile
  deletion must clear its profile snapshot.

## Termux

- Current shipping setup installs pinned OpenCode 1 inside named proot Ubuntu;
  `docs/opencode2-termux.md` is design evidence, not current behavior.
- The server binds to loopback and uses a generated password stored privately
  by both app and Termux.
- Scripts validate app-owned process identity before kill/remove operations.
- Manager scripts, state, credentials, and logs are atomically written with
  restrictive modes; logs remain bounded.
- A native timeout removes the callback but cannot terminate an already running
  Termux command.
- Preserve architecture-specific Ubuntu hashes, free-space checks, lock
  ownership, and rollback behavior.

## Voice And Camera

- Voice audio is PCM16 mono 16kHz, memory-only, capped at 30 seconds, and never
  auto-sent.
- Whisper packs have immutable revisions, exact sizes, and SHA-256 hashes.
- Downloads remain HTTPS-only, staged, resumable only with exact range evidence,
  verified before publication, and rollback-safe.
- Recognition uses local sherpa-onnx, bounded threads, serialized decode, and
  generation checks so canceled output cannot become a draft.
- Camera is Android-only, requested at point of use, and frames are not stored.

## Desktop

- Desktop excludes Termux, Android background mode, coding notifications, home
  widget, QR scanning, Shorebird, and local voice UI.
- Window default is 900x700, minimum 480x600; persisted geometry is validated
  and clamped to current displays.
- Linux uses libsecret/keyring and packages tar/deb; Windows uses WinCred and an
  unsigned x64 zip. Compilation is not physical-device evidence.

## Verification

Run focused platform tests serially, then analyzer/full suite. For Android
native changes also run release lint and a release compile check. A raw Flutter
APK remains test-only.
