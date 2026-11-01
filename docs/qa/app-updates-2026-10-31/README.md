# App updates, Layer 1: evidence (2026-10-31)

Spec and frozen channel contract: docs/design/app-updates-2026-10-31.md.

## What was run

- Emulator `emulator-5584` (x86_64, API 36), installed builds 4290 and 4291 (local release key 1DE5BF08...), both built with
  `--dart-define=OC_APP_UPDATE_FEED=http://10.0.2.2:8099/latest.json` through `tool/qa/vps_build_apk.sh` with `OC_BUILD_ARGS`.
  The define is the proof override: no release script passes it, and `test/app_update_controller_test.dart` pins that.
- A scratch feed (the JSON shape of one GitHub release, plus APK and SHA256SUMS) served from the PC; a scratch OpenCode
  server on :4301 so the app had a home screen to show the notice on.
- Signer refusal: the same 4291 APK re-signed with a throwaway key (never committed).

## Flow shown (one screen per PNG, /home/eslam/Storage2/wt/shots-2026-10-31/appupdate/)

1. 01-found-notice-and-badge: "Version 2.2.0 is ready", Update and Not now, dot on the gear.
2. 02-gear-sheet: gear tap asks Update now or Open Settings.
3. 03-updates-page-found: Settings > This app > Updates, size, notes, switch, Check now.
4. 04-downloading: progress in the status line.
5. 05, 06: Android's "Install unknown apps" setting for this app, then allowed; returning continues by itself.
6. 07-android-confirm: Android's own "Do you want to update this app?".
7. 08, 09: after Update, the app is 2.1.0+4291 and the Updates page says "Up to date".
8. 10, 11: signer mismatch (throwaway key): downloaded, checked, refused with "This download isn't signed like your app, so it wasn't installed". Nothing was installed.

## Review follow-ups (build 4292)

12-whats-new-link-check (the gear sheet's What's new goes through the external link check), 13-updates-page-no-duplicate-line
(plain "Version 2.1.0", build under Details, no status line), 14-refused-signer-open-release (signer refusal offers the
release, not a retry). The rail dot is covered by a widget test at 900 dp. The emulator's font scale was 2.0 (another
agent's); it was set to 1.0 inside the lock hold and put back.

Shot 14 was re-taken as real-font renders (emulator-5584 is reserved for a crash investigation; the 4293 build crashed
at cold start there with a JNI-not-initialized SIGSEGV while 4292 ran, cause not found): 14-refused-signer-normal-text,
14-refused-signer-2x-text.

## Tests

- test/app_update_controller_test.dart, test/app_update_ui_test.dart (VPS, all pass).
- Mutation check: 16 of them fail with the fixes removed (checksum, signer, package and version checks, 6 h throttle,
  retry-after-failure, Not now, release over patch, gear label, gear sheet, search gate).
- Kotlin: AppUpdatePolicyTest in the VPS apkbuild dir; detekt clean (tool/qa/kotlin_static_analysis.sh).

## Known gaps

- About no longer has its own update row; Updates is the one way.
- The rail dot is proven by a widget test, not an emulator shot.
