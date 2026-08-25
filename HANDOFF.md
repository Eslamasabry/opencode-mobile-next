# oc_app handoff

Last updated: 2026-08-25 (Asia/Dubai)

## Current GitHub APK

- Branch: `master`
- App version: `1.0.17+18`
- Git tag: `v1.0.17+18`
- APK: <https://github.com/Eslamasabry/oc_app/releases/download/v1.0.17%2B18/app-release.apk>
- APK SHA-256: `ca1be0aa4260ab533d56ff1cf5e65adad8cbbfbffff9f3e5ac6cdc155473fe24`
- Shorebird release ID: `789345`

This APK includes the OpenCode-driven model/mode/agent picker, repaired diff viewer,
and in-app image/file previews. It was installed on the Android simulator and
verified as version code 18/version name `1.0.17`. Flutter analysis was clean and
all 190 tests passed.

## Shorebird preview patch for `1.0.16+17`

- Shorebird release ID: `789134`
- Stable Patch 1 ID: `617007`
- Patch branch/tag: `v1.0.16+17-patch1`
- Patch commit: `760c71dcc2de7bffb54cd70ded2b262481e74502`

The image/file preview implementation was rebuilt from the exact `v1.0.16+17`
release tag and published with `shorebird patch android
--release-version=1.0.16+17`. No native- or asset-diff override was used. The
first launch downloads the patch; the next full app launch activates it.

The first patch attempt was safely cancelled because newly referenced Material
icon glyphs changed the tree-shaken font asset. The patch branch reuses glyphs
already present in the original APK, after which Shorebird's compatibility gate
passed. Analysis was clean, the full 190-test suite passed before that icon-only
adjustment, and the focused 30 preview/chat tests passed afterward.

## Previous Shorebird baseline

- Implementation commit: `4b17f40` (`Enforce reliable local Termux model`)
- App version: `1.0.14+15`
- Shorebird release ID: `787919`
- Shorebird Stable Patch 1: `615599`
- Shorebird Stable Patch 2: `615694`
- Baseline APK: <https://github.com/Eslamasabry/oc_app/releases/download/v1.0.14%2B15/app-release.apk>

Patch 2 remains the current fix for `1.0.14+15`. An installed `1.0.14+15` downloads it on launch and activates it on the following full restart. Native Android changes still require a new APK.

## Chat issue and fix

The apparent endless `thinking` state was caused by intermittent upstream 503 responses and long server retries from `opencode/big-pickle`; Flutter SSE itself was working.

Patch 1 migrated only automatically chosen `big-pickle` preferences. It mistakenly preserved an explicitly selected legacy model, which could leave a user's phone on the failing provider.

Patch 2 forces the managed local Termux profile (`http://127.0.0.1:4096`) back to `opencode/nemotron-3.5-lightning-free` whenever its catalog loads, including when `big-pickle` had been explicitly persisted. Remote profiles are not changed.

## Verified evidence

- Started from the exact bad persisted state: explicit `opencode|big-pickle`.
- Confirmed the original APK launched Shorebird Patch 2 from `patches/2/dlc.vmcode`.
- Confirmed the persisted model became `opencode|nemotron-3.5-lightning-free` and its explicit marker became false.
- Started the real Termux/OpenCode `1.18.21` server on `127.0.0.1:4096`.
- Sent `hello` through the Android UI and observed `Hello! How can I help you today?`.
- Flutter analysis: clean.
- Flutter tests: all 181 passed.
- Release-script safety test: passed.

## Continuation checks

```bash
git status --short --branch
git log -3 --oneline --decorate
/home/eslam/.shorebird/bin/shorebird patches list --release-version=1.0.16+17
/home/eslam/.config/shorebird/bin/shorebird patches list --release-version=1.0.14+15
adb devices -l
```

Do not print or copy persisted server passwords from Android preferences, process arguments, or Termux diagnostics into chat or logs.

## Conversation note

The user later clarified that “this chat keeps crashing” referred to the long Codex conversation, not the Android chat screen. This handoff exists to allow a fresh conversation without losing the release context above.
