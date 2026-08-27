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

## Shorebird chat-timeline, updater, command-launcher, and review patches for `1.0.19+20`

- Shorebird release ID: `792729`
- Stable Patch 1 ID: `619842`
- Tool-timeline commit: `4a00bd59b7e390b1f078a1fca9c761e9db9ccac8`
- Stable Patch 2 ID: `619851`
- Update-notice commit: `95eb0061806857afe47561c4cfc5cf21ee103f2f`
- Stable Patch 3 ID: `619917`
- Managed-server updater commit: `36bbdb6ab58cd56c00e9cce6c3d5a9f1c42ad63c`
- Stable Patch 4 ID: `619952`
- Integrated-workbench commit: `346766ccbaee517688d360892ab83eb690e84274`
- Shorebird-safe icon commit: `8f9a558`
- Stable Patch 5 ID: `619985`
- Command-launcher commit: `e75034368674581f93ff53969ec9a7db27fc3e8a`
- Stable Patch 6 ID: `620296`
- Review/focus/catalog/updater commit: `bf4f7912b725b369dbdd04e5a86826984334ec4a`
- Stable Patch 7 ID: `620648`
- Provider-catalog authority fix: `c13d1f42c41e26fcf0c7dd265b1007dbf2a8780b`
- Stable Patch 8 ID: `620696`
- Connected-provider contract fix: `58b321a7eae755ed1641b269a58325169b696ea6`
- Stable Patch 9 ID: `620735`
- Connected-integration reconciliation: `f9eb7b7f03da074da2e609021b3a70b3d14cd934`
- Stable Patch 10 ID: `620789`
- Provider-inventory recovery: `7b6db9b3a9128416432927f058b90f4946473b17`
- OpenCode reference revision: `c2eacd72afc4a4984564c393e15ab30011057269`
- Command/feature map: [`docs/opencode-command-feature-map.md`](docs/opencode-command-feature-map.md)

Patch 1 extends the tool-run boundary across adjacent assistant message records.
For example, an edit emitted in one OpenCode assistant record and a shell command
emitted in the next now share one growing tool-call card. User or assistant text,
reasoning, file content, or an assistant error ends the run. The transformation is
presentation-only, so stored conversations are not rewritten and old chats receive
the same grouping.

Patch 2 adds an app-level, lifecycle-aware Shorebird update notice. When a future
patch is available, the app shows a persistent `Receiving Shorebird update…`
snackbar while `ShorebirdUpdater.update()` downloads it, followed by
`Shorebird update ready — restart to apply.` It also checks after resume, throttles
checks for 15 minutes, coalesces concurrent checks, and silently ignores unsupported
or offline checks. Shorebird's automatic updater remains enabled as a fallback. If
the automatic updater finishes before Dart observes the download, the app shows only
the truthful ready-to-restart notice rather than simulating progress.

The update UI cannot announce its own Patch 2 download because that Dart code is not
active until Patch 2 launches. Existing `1.0.19+20` installs download Patch 2 on one
launch and activate it after the next full restart; subsequent patches can show the
new receiving and ready notices.

Patch 3 adds an in-app update path for the OpenCode server owned by this app at
`http://127.0.0.1:4096`. Settings opens the existing guided Termux manager, which
installs the latest stable `opencode-ai`, refreshes the server-provided model catalog,
restarts only the managed process, and reconnects the profile. It refuses to begin
while a generation is active and keeps the running server alive until package and
model work succeeds. Arbitrary remote servers are deliberately not administered by
the app; their Settings action copies `opencode upgrade` and
`opencode models --refresh` for the operator to run on the host.

Flutter analysis was clean and all 215 tests passed, including deterministic widget
coverage for the downloading and restart-ready snackbar states. Patches 1 and 2
passed compatibility checks without native or asset overrides. The Pixel 6
simulator downloaded Patch 2 (1,470,780-byte x86_64 patch) and activated
`patches/2/dlc.vmcode` after a cold restart; no Flutter fatal error was logged.

For Patch 3, Flutter analysis remained clean, all 218 tests passed, the generated
Termux manager passed Bash syntax validation, and the release APK built with the
Shorebird-pinned Flutter `3.47.1`. Shorebird's own dry run reported no compatibility
issues and the patch was published without native- or asset-diff bypasses. The exact
GitHub APK was restored on `emulator-5554` without clearing its data, downloaded the
1,482,350-byte x86_64 Patch 3, and activated `patches/3/dlc.vmcode` after a cold
restart. The updater Settings tile, setup screen, installed-version copy, and update
confirmation were then verified from the active patch with no Flutter/render failure.

Patch 4 replaces the crowded chat overflow workflow with one integrated composer
workbench. Its `Context`, `Commands`, `Run`, `Review`, and `Session` tabs swap one
bounded panel above the input without creating stacked card surfaces. Commands are
loaded from the connected OpenCode server rather than hardcoded; Review loads the
session diff, todos, pending requests, and generated-artifact count on demand. At
keyboard-constrained heights the tabs become a compact Workbench menu so model,
attachment, voice, input, and send controls remain reachable at 640x320 with 2x text.

Tool chains now have one outer group surface. Expanded tool calls are flat rows with
dividers, not bordered cards nested inside the group. Consecutive reasoning parts and
consecutive user-facing assistant text parts merge across adjacent assistant records
until the content type changes. Reasoning that renders on one line is shown directly;
longer reasoning retains the accessible 48dp expand control. Tools, reasoning, text,
files, user messages, and errors remain truthful run boundaries, and the transformation
continues to apply to old chats without rewriting stored messages.

Final-tree Flutter analysis was clean and all 221 tests passed. Shorebird's dry run
reported no compatibility issues. The four Material glyphs initially unique to the
new workbench were replaced with glyphs already shipped in the tree-shaken release
font before publication. The exact GitHub APK (version code 20/version name 1.0.19)
downloaded the 1,595,003-byte x86_64 Patch 4 and activated
`patches/4/dlc.vmcode` after a cold restart. The Pixel 6 simulator then rendered the
five tabs, server-provided `/init` and `/review` command rows, and the content-sized
Review panel without a Flutter/render failure.

Patch 5 supersedes Patch 4's generic five-tab workbench. The composer now has one
compact Commands action that opens a searchable, flat OpenCode command launcher.
It maps the current upstream TUI and web-client registries instead of presenting
`/init` and `/review` as the complete command surface. Mobile-native actions and
live server commands are visibly labelled; server commands continue to come from
OpenCode's command endpoint, including configured commands, MCP prompts, and slash
skills. Typing `/` in the composer shows the same relevance-ranked suggestions,
and selecting a server command inserts it for argument editing before submission.

The implemented native map includes session/workspace/file/terminal navigation,
model/agent/variant selection, MCP/connect/skills/status, diff, sharing, rename,
fork, compact, undo/redo, transcript copy/export, and help. The source-backed map
also records the remaining upstream gaps rather than silently inventing substitutes:
timeline, timestamps, thinking visibility, full prompt editor, move, experimental
warp, themes, organization switching, and TUI-only diagnostics.

Final-tree Flutter analysis was clean and all 223 tests passed. Shorebird's dry run
reported no compatibility issues and Patch 5 was published without native- or
asset-diff overrides. The exact public GitHub APK (version code 20/version name
1.0.19) downloaded the 1,605,747-byte x86_64 Patch 5 and activated
`patches/5/dlc.vmcode` after a cold restart. On `emulator-5554`, the active patch
showed the live server `/review` result first for an exact search and `/diff` as a
separate mobile-native action; Android logs contained no Flutter, RenderFlex,
overflow, or fatal-exception failure.

Patch 6 replaces the stacked diff sheet with a full-screen Review workspace. Phone
layouts use compact changed-file tabs; wider layouts add a file rail. Review supports
unified and split diff modes, old/new line numbers, hunk navigation, large-diff
virtualization, line-range selection, copying, file questions, and grounded comments
that return to the chat composer with the file and line range attached. Loading,
retry, empty, and no-diff states remain explicit and the compact 640x320/2x-text
layout is covered.

The chat composer now derives its compact layout from the stable device height rather
than the keyboard-shrunk body constraints, so focusing the prompt does not replace the
TextField and dismiss the Android keyboard. Model and agent details continue to come
from OpenCode. Detailed metadata is pruned against the server's current authoritative
provider/model/agent inventory, the picker refreshes every time it opens, and a manual
refresh action is available. This removes deleted catalog entries without introducing
a hardcoded model list.

The managed OpenCode installer now cleans the legacy persistent npm cache before its
storage check and uses a fresh isolated npm cache for each download attempt. This
repairs both the corrupt-cache `ENOENT rename` failure and the subsequent low-storage
failure it caused. On `emulator-5554`, the cache had grown to 838 MiB; after the scoped
cleanup the in-app updater completed, refreshed the model catalog, restarted the
authenticated server, and reported OpenCode `1.18.23`. The picker then exposed six
current models and an `ox alpha` search returned `No matching models`.

Final-tree Flutter analysis was clean and all 230 tests passed. The release APK built
with pinned Flutter `3.47.1`; Shorebird's dry run reported no issues, and Patch 6 was
published without native- or asset-diff overrides. The exact public GitHub APK (SHA-256
`c94c305d1ad329e1126065890fd3d0e4ffe92ddf26f7f12584532885f1164887`)
downloaded the 1,661,180-byte x86_64 Patch 6 and activated
`patches/6/dlc.vmcode` after a cold restart. On-device checks confirmed the Review
empty state, a prompt that remained focused with the keyboard shown after four seconds,
and the refreshed model picker. The next native maintenance release should upgrade
Gradle to at least 9.1, AGP to at least 9.0.1, and Kotlin to at least 2.3.20.

Patch 7 fixes newly connected providers being hidden when OpenCode's legacy
`/config/providers` response lags behind the current v2 provider/model catalog. When
the detailed v2 catalog is available it is now authoritative; the legacy list is only
a fallback. Catalog refreshes also run after provider API-key/OAuth connection and on
the corresponding OpenCode SSE update events. The regression case reproduces the
reported state exactly: legacy data exposes only Zen while the current catalog exposes
`zai-coding-plan/glm-5.2`; both providers remain visible and the Z.AI selection is
preserved. The current catalog still removes truly stale entries such as `ox-alpha`.

Flutter analysis was clean and all 231 tests passed. Shorebird's dry run reported no
compatibility issue and Patch 7 was published to the stable track without native or
asset overrides. A real Z.AI completion was not claimed because the isolated Pixel 6
simulator has no user Z.AI credential. Its saved Android snapshot also entered a
framework/binder-starvation state during activation verification; a no-snapshot cold
boot resolved package discovery but remained too slow for trustworthy Patch 7 runtime
activation evidence. Use the user's configured server for the final provider call.

Patch 8 corrects the remaining source-of-truth mistake found against the live Ubuntu
server. OpenCode's documented `/provider` route returns `all`, `connected`, and each
provider's model map; experimental `/api/provider` and `/api/model` only describe the
providers and models active in the current location and can legitimately contain Zen
alone. The app now filters `/provider` to its ordered `connected` IDs, uses those model
maps as the picker authority, and applies v2 capability metadata only as enrichment for
matching rows. Older OpenCode servers retain the `/config/providers` fallback.

The live Coding Plan credential completed a direct read-only request through
`zai-coding-plan/glm-5.2`. A second request attached through the same OpenCode server
URL used by mobile (`http://127.0.0.1:4747`) returned the exact `ZAI_SERVER_OK` marker.
The refreshed OpenCode `1.18.23` server also reported 11 connected providers through
`/provider`, including `zai-coding-plan` with seven models. No credential was copied
into this repository or the mobile app. Flutter analysis was clean, all 232 tests
passed, Shorebird's dry run reported no issue, and Patch 8 was published stable without
native or asset overrides.

Patch 9 targets the user's actual personal-phone topology: OpenCode Mobile and the
OpenCode server both run on Android, with the server in Termux at
`http://127.0.0.1:4096`. OpenCode can report a provider connection through
`/api/integration` while temporarily omitting that provider from `/provider`. When
that exact mismatch occurs, the app now recovers only the integration-confirmed
provider IDs from `/config/providers`, including their model maps and thinking
variants. It does not expose unrelated configured or available providers and does
not hardcode Z.AI or model names.

The regression test reproduces the phone report: `/provider` contains Zen alone,
the integration API reports a connected `zai-coding-plan`, and the configured model
map contains `glm-5.2` with `high` and `max` variants. The selector recovers that
provider, model, variants, and selection. Flutter analysis was clean, all 233 tests
passed, and Shorebird's dry run found no compatibility issue. Patch 9 was published
to Stable without native or asset overrides. Phone runtime activation remains to be
verified by the user because the phone's ADB, SSH, and OpenCode ports were not
remotely reachable; the workstation server was not treated as phone evidence.

Patch 10 fixes the remaining ordering bug using direct read-only evidence from the
user's phone over Tailscale/Termux SSH. Its OpenCode `1.18.23` server returned only
`opencode` in `/provider.connected`, while `/provider.all` contained the complete
`zai-coding-plan` model map and `/api/integration` reported that exact integration
connected. `/config/providers` contained Zen alone. The parser had discarded the
unlisted `/provider.all` rows before connected-integration reconciliation ran.

The app now retains the full provider inventory internally while still exposing only
OpenCode-connected or integration-confirmed providers. Exact connected integration
IDs can recover their model maps from `/provider.all`; unrelated available providers
remain hidden. The regression reproduces the phone payload, including Zen-only
`/provider.connected` and `/config/providers`, and verifies Z.AI model and thinking
variant recovery. Flutter analysis was clean, all 233 tests passed, Shorebird's dry
run reported no issue, and Patch 10 was published to Stable without native or asset
overrides. Runtime activation on the personal phone is the remaining gate.

On the Ubuntu workstation, `z node_modules/opencode-ai` failed because zoxide had no
matching history entry. The direct package path is `/home/eslam/node_modules/opencode-ai`;
running `cd /home/eslam/node_modules/opencode-ai && node postinstall.mjs` repaired the
binary, after which `opencode --version` reported `1.18.23` and
`opencode models opencode --refresh` contained no Ox Alpha entry.

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
