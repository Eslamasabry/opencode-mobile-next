# oc_app handoff

Last updated: 2026-08-27 (Asia/Dubai)

## Current GitHub APK

- Branch: `master`
- App version: `1.0.19+20`
- Git tag: `v1.0.19+20`
- Release commit: `efed66192ed4ee0f70d95c4b28d137e0e3a071ed`
- Release: <https://github.com/Eslamasabry/oc_app/releases/tag/v1.0.19%2B20>
- APK: <https://github.com/Eslamasabry/oc_app/releases/download/v1.0.19%2B20/app-release.apk>
- APK SHA-256: `c94c305d1ad329e1126065890fd3d0e4ffe92ddf26f7f12584532885f1164887`
- APK size: `158719165` bytes
- Shorebird release ID: `792729`

This APK includes the OpenCode-driven model/mode/agent picker, repaired diff viewer,
in-app image/file previews, wake-time UI reconciliation, and the opt-in Android
foreground service for live background coding sessions. It also groups consecutive
tool calls into a growing timeline run until reasoning, assistant text, or a file
part begins. Model metadata is displayed only initially or when the model changes;
token and cost usage is aggregated once at the end of each assistant run. These
rules are derived from stored OpenCode parts at render time, so they apply to old
chats without a migration.

The exact GitHub asset was registered through Shorebird with Flutter `3.47.1`,
installed on `emulator-5554`, and verified as version code 20/version name
`1.0.19`. An existing real chat rendered without repeated model metadata or a
Flutter/render failure. The GitHub-downloaded asset matched the local tested APK
byte-for-byte. Flutter analysis was clean and all 213 tests passed.

## Shorebird generated-artifact patch for `1.0.18+19`

- Shorebird release ID: `789571`
- Stable Patch 1 ID: `617162`
- Artifact workflow commit: `adfdeca`
- Smart text commit: `65c145c`

Tool results such as `{"filePath":"/tmp/opencode/shots/captcha-r1.png"}` are
now first-class chat artifacts. The app retrieves them from the connected
OpenCode server, renders images inline, opens images and text files in the
shared viewer, saves them through Android's system document destination, and
can attach the fetched bytes back to the composer for a follow-up comment.

Text rendering is content-aware across chat, tool output, generated artifacts,
and the workspace file viewer. Markdown has rendered/raw modes and formatted
tables; JSON is indented; recognized source files use selectable,
language-labelled code blocks. No native- or asset-diff override was used.
The simulator downloaded Patch 1 on first launch and activated
`patches/1/dlc.vmcode` after a cold restart; no Flutter fatal error was logged.

## Shorebird idle-recovery patch for `1.0.18+19`

- Shorebird release ID: `789571`
- Stable Patch 2 ID: `618271`
- Patch commit: `9da3b96`

Foreground sends now wait for the shared wake-time reconciliation instead of
capturing a stale API transport. Keep-live resumes health-check the retained
transport, rebuild it when stale, and coalesce concurrent resume/send signals.

For the managed loopback server, the app refreshes Termux's wake lock before
connect and post-idle health checks. Future guided setups retain the same wake
lock until OpenCode stops or exits, instead of releasing it as soon as setup
returns. Remote profiles are unaffected, and Termux bridge failures remain
non-blocking. Shorebird's compatibility verification passed without native or
asset overrides. Flutter analysis was clean; all 204 tests passed. The simulator
downloaded Patch 2, activated `patches/2/dlc.vmcode` after a cold restart, kept
the foreground service active, and showed the Termux partial wake lock held.

## Shorebird OpenCode tool-rendering patch for `1.0.18+19`

- Shorebird release ID: `789571`
- Stable Patch 3 ID: `619804`
- Contract renderer commit: `f7ed30e`
- Shorebird asset-safe follow-up: `d60eb4f`
- OpenCode reference revision: `c5ef753d2869982183f64bf1ec6c92b7c4149c59`

Tool parts now retain OpenCode's structured `state.input`, `state.output`, and
`state.metadata` instead of flattening the contract before rendering. The chat
has exact presentations for `read`, `list`, `glob`, `grep`, `bash`/`shell`,
`edit`, `write`, `patch`/`apply_patch`, `webfetch`, `websearch`, `task`,
`todowrite`, `question`, `lsp`, and `skill`, with a structured fallback for MCP
and plugin tools. Shell metadata and ANSI control sequences are removed from
the visible transcript, edits and patches render as bounded two-axis diffs, and
Markdown/code/JSON output continues through the shared smart-text renderer.

Artifact discovery is deliberately contract-based: explicit `filePath` values,
OpenCode attachments, and shell `metadata.outputPath` become previewable files;
ordinary prose, path-like strings, and web-result URLs do not. Read-tool image
attachments inherit the requested filename, so existing generated images keep
their inline preview, open, download, attach-back, and comment workflow.

Consecutive OpenCode context tools (`read`, `list`, `glob`, and `grep`) collapse
into one compact `Exploring`/`Explored` row with read/search/list counts. Text,
reasoning, shell commands, mutations, errors, and file-bearing tools break the
group and remain directly visible. This follows OpenCode's own context grouping
contract while keeping mobile horizontal space for the path/command subtitle.

Flutter analysis was clean and all 212 tests passed. The first patch attempt was
safely cancelled when Shorebird detected one new Material icon glyph; the glyph
was replaced with one already shipped in the APK and Patch 3 then passed the
compatibility gate without native or asset overrides. The exact GitHub release
APK matched SHA-256
`41190803397f5c481d7e12c919221b5441f7651de9a27bef645ba6c928c44896`.
The Pixel 6 simulator downloaded Patch 3, activated
`patches/3/dlc.vmcode` after a cold restart, connected to the real local
Termux/OpenCode server, and rendered an existing shell tool as
`Shell · wake-check · exit 0`; expanding it showed the command and output with
no generic sections, layout failure, or Flutter fatal error.

## Background connection behavior

Settings now offers `Keep coding session live`. It starts an Android `dataSync`
foreground service with a persistent notification and keeps SSE and an open terminal
transport alive while the Activity is backgrounded. A second explicit action opens
Android's battery-optimization exemption prompt. The app still reconciles chat,
workspace, files, terminal lists, requests, and catalog data whenever it wakes, even
when the live transport survived.

The exact release APK was tested across Home-backgrounding and forced deep idle. The
same process and foreground service survived, the setting survived the upgrade from
version code 18 to 19, and no fatal exception was logged. Android 15+ limits
`dataSync` foreground-service use to six background hours per rolling 24-hour period;
the battery exemption does not bypass that platform limit.

## Shorebird wake patch for `1.0.17+18`

- Shorebird release ID: `789345`
- Stable Patch 1 ID: `617081`
- Patch commit: `b83bf27c5636a7f48707ad8f15fc669a82946c26`

This Dart-only patch rehydrates screen-owned data after resume and SSE reconnects.
It repairs stale chat, workspace, file, and terminal UI for existing `1.0.17+18`
installs. The foreground service and Android permissions are native changes and
therefore require installing `1.0.18+19`; Shorebird cannot add them to an older APK.

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
/home/eslam/.shorebird/bin/shorebird patches list --release-version=1.0.18+19
/home/eslam/.shorebird/bin/shorebird patches list --release-version=1.0.19+20
/home/eslam/.config/shorebird/bin/shorebird patches list --release-version=1.0.14+15
adb devices -l
```

Do not print or copy persisted server passwords from Android preferences, process arguments, or Termux diagnostics into chat or logs.

## Conversation note

The user later clarified that “this chat keeps crashing” referred to the long Codex conversation, not the Android chat screen. This handoff exists to allow a fresh conversation without losing the release context above.
