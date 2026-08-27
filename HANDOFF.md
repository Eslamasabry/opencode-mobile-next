# oc_app handoff

Last updated: 2026-08-28 (Asia/Dubai)

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

## Production Android hardening branch

- Branch: `production/android-release-hardening`
- Implementation commit: `e6f290d64b3e0668eac15b50bbd2af3e11bb13cf`
- Compare/PR: <https://github.com/Eslamasabry/oc_app/pull/new/production/android-release-hardening>

This branch prepares the next native baseline without changing the patchable
`1.0.19+20` release on `master`. It removes the debug signing fallback, adds a
dedicated release signing configuration and ignored properties template, and
upgrades to Gradle `9.5.0`, AGP `9.3.2`, built-in Kotlin with KGP `2.4.0`, and
current AGP-9-compatible file/audio plugins. `flutter_secure_storage` advances
from v9 to v10 deliberately so existing Android credentials pass through the
required cipher migration; do not jump this release line directly to v11.

Pinned Flutter `3.47.1` completed a release APK build in 139.5 seconds. The
159.1 MB artifact used the known debug certificate only as an ignored local
test key; that file was removed and the artifact must not be distributed.
Flutter analysis was clean and all 237 tests passed. Shorebird's debug Maven
repository lacked this fork's debug embedding, so release mode was the valid
build gate. Pixel 6 install/launch verification was environment-blocked: the
AVD never started Android's package service and then crashed with QEMU main-loop
and CPU-thread watchdog errors even after its test data was reset.

The remaining owner gate is the signing/package identity decision. Choose and
back up the production upload key, decide how existing debug-signed sideload
users transition, populate ignored `android/key.properties`, export the expected
certificate fingerprint, then cut a new full Shorebird release. These native
and dependency changes cannot ship as a patch to `1.0.19+20`.

The branch also has a bounded production-guardrail follow-up: a bundled,
user-facing privacy policy; adaptive, round, and Android 13 themed launcher
resources; a pinned GitHub Actions analysis/test/lint/release-compile gate; and
pre-build verification that the release keystore is private, outside the
repository, non-debug, and matches the expected certificate. Flutter analysis
is clean, all 239 tests pass serially, the release-script safety harness passes,
and Android release resources compile.

The owner explicitly corrected the next priority: do not lead with Play/store
work while core product journeys remain incomplete. The next lane is an
authoritative comparison of every generated Dart OpenCode API against app call
sites, followed by implementation of the highest-impact hidden or incomplete
coding workflow. Signing and store publication remain deferred until that
product-completeness audit is worked down.

That API audit is now recorded in
[`docs/opencode-sdk-coverage.md`](docs/opencode-sdk-coverage.md). The generated
Dart SDK exposes 188 HTTP operations across 32 groups; the app directly used 30
before this audit and also retained several handwritten compatibility routes.
The first gap repaired is Review truth. The existing session diff remains the
safe default, while adjacent Working tree and Branch scopes now use the generated
`vcs.diff` API with the contract's exact `git` and `branch` modes. All scopes
reuse the same flat file tabs, unified/split renderer, line selection, copy,
question, and grounded-comment workflow.

Flutter analysis is clean and all 241 tests pass serially. The generated VCS
mapping has a real loopback HTTP contract test covering location, mode, context,
patch, counts, and status. A test-signed release APK built in 189.1 seconds,
installed on a cold-booted Pixel 6 emulator, and launched as the resumed Android
activity with no Flutter, RenderFlex, overflow, or fatal-exception log. The
throwaway signing material was removed and the APK must not be distributed.
Live VCS response evidence is still missing: the emulator has no OpenCode server,
and the user's Tailscale-visible phone refused Termux SSH on port 8022 during the
check. Do not claim the live `/vcs/diff` endpoint until one of those server paths
is reachable.

The next audited gap repaired is provider OAuth completion. Mobile now preserves
OpenCode's OAuth attempt ID, `auto`/`code` mode, instructions, and expiry instead
of discarding them after opening the browser. Provider connections show one flat,
visible pending row with Check or Enter code plus cancellation. On app resume,
automatic attempts query the generated status endpoint; code attempts accept the
returned code and call the generated completion endpoint. Failed and expired
states remain truthful, and provider/model inventories refresh only after the
server reports completion. Cancelling the browser confirmation also releases the
server attempt.

The owner explicitly deferred Traycer and the agent/task-tree lane. Do not add it
now. The next product-completeness lane should stay within core OpenCode mobile
workflows, beginning with coding health surfaces such as VCS status/info, symbol
search, LSP status, and formatter status. Flutter analysis is clean and all 244
tests pass serially, including loopback HTTP coverage for OAuth start/status/
complete/cancel and widget coverage for automatic, code, cancel, and model-refresh
flows.

Project coding health is also no longer hidden in the generated SDK. Workspace
now exposes one native Project health destination for the selected location. It
shows the current/default branch, changed files and line counts, active/erroring
language servers, and enabled/disabled formatters using generated `vcs.get`,
`vcs.status`, `lsp.status`, and `formatter.status` calls. The presentation is a
flat sequence of sections and rows, not nested cards. Each API has an independent
loading/error/empty state, so an older server missing one endpoint does not erase
the other results.

Flutter analysis remains clean and all 248 tests pass serially. New coverage
checks exact location-scoped HTTP paths and response mapping, partial endpoint
failure, workspace discoverability, absence of nested cards, and 320dp rendering
at 2x text scale. Live status responses still need a reachable OpenCode project;
do not claim live-server verification from the contract and widget tests alone.

Workspace symbol navigation is now implemented without adding a separate nested
surface. The Files screen has adjacent Files and Symbols tabs above one contextual
search field. Symbols use the generated, location-scoped `/find/symbol` endpoint,
show the LSP kind plus file/line/column, and open the existing smart file preview
at the exact source line with line numbers and a highlighted target. Switching
back to Files preserves ordinary browsing, and an older server missing symbol
search shows a scoped error without breaking its file list.

Flutter analysis is clean and all 250 tests pass serially. Contract coverage
verifies file-URI normalization and zero-based LSP position conversion; widget
coverage verifies exact-line opening, accessibility-scale layout at 320dp, and
the old-server fallback. The generated SDK audit now counts 39 direct operations.
Live symbol results remain unverified until a reachable OpenCode project exposes
an active language server.

The last plain skill-content surface now uses the shared smart file preview.
Opening a skill renders its Markdown headings, tables, inline code, fenced code,
lists, and links while retaining one explicit Raw mode for exact source inspection.
The skill sheet remains a single flat content surface; it does not introduce a
nested card or a second scrolling container. Focused widget coverage verifies the
rendered/raw transition and a 320dp phone at 2x text scale. Final-tree Flutter
analysis is clean and all 252 tests pass serially. Traycer remains explicitly
deferred.

Project references are no longer display-only. The mobile command launcher now
exposes `/references` with `/reference` and `/refs` aliases. Selecting an item
adds the visible `@name` mention and OpenCode's exact directory file part
(`application/x-directory` plus a canonical `file://` URL) to the current
composer. Pending and sent references render as references rather than broken
downloadable attachments. The standalone Library screen copies the exact
mention. This is grounded in the upstream TUI and web composer contract; no
reference names or paths are hardcoded.

Reference URL encoding also detects Windows drive and UNC paths returned by a
remote server instead of applying the Android phone's path rules.

Final-tree Flutter analysis is clean and all 256 tests pass serially. Coverage
includes the exact serialized directory-part contract, standalone copy behavior,
remote Windows path encoding, and the full command-launcher to composer to Send
interaction.

Long chats now have a mobile-native message timeline. The app bar groups Timeline,
Changes, and Todos under one Session views control to preserve horizontal space;
`/timeline` opens the same searchable flat list. Selecting any user or assistant
message uses a stable indexed anchor and briefly highlights the destination, even
when mixed-height Markdown, tool groups, diffs, and images make pixel-offset math
unreliable. The sheet expands automatically for accessibility-scale text and is
covered at 320dp with a 2x text scale.

`/fork` now mirrors OpenCode's upstream UI rather than forking only the complete
session. It lists canonical user prompts, sends the selected `messageID` through
the generated fork API, then restores that prompt's text and file parts into the
new session composer for editing or resending. The general timeline also exposes
the same fork action beside eligible user prompts. The legacy whole-session fork
remains available in the session overflow menu. Traycer remains explicitly
deferred. Final-tree Flutter analysis is clean and all 261 tests pass serially;
coverage includes indexed long-chat jumps, prompt/file restoration, the exact
generated fork request, and the 320dp accessibility layout.

Transcript display parity is now native rather than slash-only. Session views
groups checked Expand reasoning and Show timestamps actions with Timeline,
Changes, and Todos; `/thinking`, `/toggle-thinking`, `/timestamps`, and
`/toggle-timestamps` call the same handlers. Both preferences persist app-wide
through `ProfileStore` and update retained chats through `ConnectionController`.
No chat migration is involved: timestamps come from stored OpenCode message
creation times, while the existing reasoning parts expand or collapse in place.
Reasoning shorter than two rendered lines remains visible regardless of the
global long-reasoning setting, and individual long blocks remain tappable.

Final-tree Flutter analysis is clean and all 263 tests pass serially. Coverage
includes preference restoration, old-chat live updates through both the command
launcher and native Session views, timestamp metadata, and global reasoning
collapse. Traycer remains deferred.

`/editor` now matches the upstream prompt-editing intent instead of opening the
project file browser. It opens a focused full-screen multiline editor initialized
with the exact composer `TextEditingValue`, so both the draft and cursor/range
selection survive the round trip. Existing attachments remain visible and
previewable; they can be removed or extended through the same bounded file picker
and aggregate-size rules as the compact composer. Done returns changes without
sending, while back/close protects dirty text or attachments with an explicit
discard choice. The composer also exposes a direct expand action.

Project browsing is now the distinct `/files` action with `/open` as the web-client
alias. Command-search coverage ensures `/open` cannot drift back to the prompt
editor. Final-tree Flutter analysis is clean and all 265 tests pass serially,
including selection and attachment round trips, cancel/discard behavior,
no-auto-send behavior, and a 320dp/2x-text keyboard-inset layout. Traycer remains
deferred.

The remaining location and organization commands now use their actual generated
OpenCode contracts. `/move` lists known directories for the session project and
can transfer current working changes. `/warp` distinguishes connected
experimental workspaces from the Local project and can copy changes during the
warp. `/org`, `/orgs`, and `/switch-org` show organizations grouped by Console
account, switch the selected account/org pair, dispose the old instance, and
rebuild the location-scoped transport so provider and model catalogs reload.
Each operation has its own loading, empty, error, current, and unavailable
states; the surfaces are flat lists rather than nested cards.

The app sends OpenCode's synthetic no-reply working-directory reminder after a
successful move or warp, then rehydrates the existing chat on the replacement
transport. Workspace listing remains compatible with servers that predate the
status endpoint. Contract tests cover exact paths, queries, payloads, instance
disposal, and reminder serialization; widget tests cover launcher mapping,
change-transfer choices, transport replacement, and 320dp rendering at 2x text.
Final-tree Flutter analysis is clean and all 272 tests pass serially. The
generated SDK audit now counts 46 directly used operations. Traycer remains
explicitly deferred.

The ordinary chat send path now uses the generated `session.prompt_async`
transport rather than duplicating that endpoint through handwritten Dio. The
wire contract remains unchanged: selected provider/model, agent, thinking
variant, text, uploaded files, OpenCode directory references, directory, and
workspace are all preserved. Declared generated errors are translated back to
the app's product-facing `ApiException` without losing HTTP status, error tag,
request ID, or server detail; undeclared transport failures retain the existing
Dio fallback. Exact loopback coverage guards both the full request and error
mapping. Final-tree Flutter analysis is clean and all 274 tests pass serially.
The generated SDK audit now counts 47 directly used operations. Traycer remains
explicitly deferred.

Server-provided slash commands now use generated `session.command`, preserving
their exact arguments, `provider/model` route, selected thinking variant,
directory, and workspace. Declared server failures retain product-facing HTTP
and OpenCode error details. Shell execution deliberately remains on its bounded
compatibility request because the generated `SessionShellRequest` omits
`variant`; replacing it would silently discard High, Max, Fast, or any other
server-provided thinking mode. A loopback transport regression proves both the
generated command payload and the retained shell variant. Redundant handwritten
prompt and command body builders were removed after their stronger end-to-end
wire tests replaced them. Final-tree Flutter analysis is clean and all 274 tests
pass serially. The generated SDK audit now counts 48 directly used operations.
Traycer remains explicitly deferred.

Stopping a generation is now a production-safe lifecycle action. It waits for
the same wake-time transport reconciliation as sending, calls generated
`session.abort` with the selected directory/workspace, ignores repeated taps
while the first stop is pending, and shows a product-facing failure instead of
silently swallowing it. Exact transport tests cover success and declared server
errors; widget tests prove a stale retained API is not used after wake and a
failed stop remains visible. The unused handwritten `initProject` wrapper was
removed; the visible `/init` workflow continues through OpenCode's authoritative
server-command catalog. Final-tree Flutter analysis is clean and all 278 tests
pass serially. The generated SDK audit now counts 49 directly used operations.
Traycer remains explicitly deferred.

Session creation, rename, and deletion now use one wake-safe mutation path
instead of capturing whichever API object a retained screen happened to hold.
The underlying calls use generated `session.create`, `session.update`, and
`session.delete`, with directory/workspace scoping and product-facing declared
errors. Successful but looser session responses from older OpenCode servers are
still accepted, avoiding a false failure after a mutation already committed.
The Chats list now confirms permanent deletion just like Workspace; cancellation
does not call the server, and failed mutations leave the session visible.
Transport tests cover exact methods, paths, queries, bodies, loose success
responses, and declared failures. A controller regression proves create, rename,
and delete all wait for the post-wake replacement API. Final-tree Flutter
analysis is clean and all 281 tests pass serially. The generated SDK audit now
counts 51 directly used operations. Traycer remains explicitly deferred.

The session read side now uses generated `session.list`, `session.get`,
`session.status`, and `session.messages`. Loose metadata returned by older
OpenCode servers still falls back to the app's tolerant parser. Message info and
parts pass through the SDK's lossless raw-union wrappers, so custom tools,
plugins, structured input/output, metadata, and future part fields are not
flattened or discarded before the existing renderer sees them. Exact loopback
coverage proves location queries, loose metadata, busy/retry statuses, declared
errors, and nested plugin-specific tool data. Final-tree Flutter analysis is
clean and all 283 tests pass serially. The generated SDK audit now counts 55
directly used operations. Traycer remains explicitly deferred.

Session Todos and Diff now use generated `session.todo` and `session.diff` with
the selected directory/workspace instead of duplicate handwritten requests.
Typed todo priority and diff patch/count/status data are preserved in the app;
unknown future diff statuses do not leak the SDK's internal fallback enum label.
If an older server returns a successful but looser todo or diff payload, the app
falls back only for that deserialization failure and retains missing-priority
todos plus legacy before/after diff content. Todo rows now show non-pending state
and server priority without adding a nested card. Exact loopback coverage proves
paths, location queries, strict mapping, loose successful responses, and declared
OpenCode errors. Final-tree Flutter analysis is clean and all 287 tests pass
serially. The generated SDK audit now counts 57 directly used operations. Traycer
remains explicitly deferred.

Pending permission and question flows now use generated legacy and V2 transports
for both hydration and replies. This covers `permission.list`,
`permission.reply`, the bounded deprecated `permission.respond` fallback,
`v2.permission.request.list`, `v2.session.permission.reply`,
`v2.question.request.list`, `v2.session.question.reply`, and
`v2.session.question.reject`. Strict V2 location envelopes are verified before
publishing requests; successful looser payloads from older servers retain their
existing tolerant path. Declared request IDs and error tags still resolve
permission/question reply races idempotently instead of leaving stale dialogs.

Permission and question replies now wait for the lifecycle action transport, so
a retained Requests or Chat surface cannot answer through the API/repository
being replaced during Android wake reconciliation. Exact loopback tests cover
strict and loose envelopes, location queries, bodies, unavailable-endpoint
fallback, and generated OpenCode errors; controller coverage proves permission,
answer, and rejection actions all use the replacement wake transport. The
final tree has clean Flutter analysis and all 291 tests pass serially. The
generated SDK audit now counts 65 directly used operations. Traycer remains
explicitly deferred.

The active Files surface now uses generated `file.list`, `file.read`,
`find.files`, and `find.text` contracts with the selected directory/workspace.
Binary type, base64 encoding, and MIME metadata survive the mapping so image and
file previews remain viewable, downloadable, and attachable. Successful legacy
responses still accept nodes without newly required fields, raw text bodies,
line-array content, and loose search matches. File browsing, filename search,
symbol search, direct file viewing, and chat tool-output previews all wait for
the post-wake transport before reading, preventing a retained screen from using
the API being replaced after Android idle. Exact loopback tests prove methods,
paths, queries, strict and loose mappings, binary bytes, and generated error
identity; a widget regression proves a tool image loads only through the
replacement wake transport. Final-tree Flutter analysis is clean and all 295
tests pass serially. The generated SDK audit now counts 69 directly used
operations. Traycer remains explicitly deferred.

Wake safety now covers the full retained foreground action surface, not only
chat send/stop and file reads. `ConnectionController` resolves the product
repository paired with the post-wake API, and chat share/unshare, fork,
compact, revert/restore, retry, shell, Review, and Todos use it. Library MCP,
provider OAuth/key actions, commands, Skills, and References; workspace session
actions and destination discovery; project/Settings health; and terminal
list/create/rename/remove also wait for reconciliation. Existing transcript
data can render immediately during wake, while new requests cannot escape
through the repository being retired. Terminal dialogs additionally retain
their location revision so an actual workspace switch still fails closed.
Replacement-repository widget regressions cover a session fork, an MCP connect,
and all three project-health requests. Final-tree Flutter analysis is clean and
all 298 tests pass serially. The generated SDK audit remains at 69 directly used
operations. Traycer remains explicitly deferred.

Library now includes a native persistent MCP setup flow for remote URLs and
server-local commands. Users explicitly choose the current project or all
projects; the form validates transport fields and writes through generated
`configGet`/`configUpdate` or `globalConfigGet`/`globalConfigUpdate` contracts.
Duplicate names fail before a patch, and a successful patch rebuilds the
location transport because OpenCode invalidates the configured instance. If
that reconnect fails, the UI truthfully reports that configuration was saved,
disables editing, and offers Close instead of risking a duplicate submission.
Focused coverage includes exact loopback methods, paths, queries, payloads,
unsafe input, duplicate prevention, Library entry, compact 320dp/2x-text layout,
and post-save reconnect failure. Final-tree Flutter analysis is clean and all
307 tests pass serially. The generated SDK audit now counts 73 directly used
operations. Traycer remains explicitly deferred.

The next native baseline now has one authoritative Shorebird update owner.
`shorebird.yaml` disables the engine's automatic launch updater while the
existing app-level notice continues to check and download on startup/resume.
This removes a real race where `ShorebirdUpdater.update()` returned the benign
`UPDATE_IN_PROGRESS` result from the parallel engine thread and the UI announced
`restart to apply` before the patch was staged. A failed update now removes the
progress snackbar and never shows the ready notice; a release contract prevents
the second updater from being re-enabled.

This was reproduced and verified on the running Pixel 6 emulator with an exact
branch release-mode build signed by the known Android debug certificate for
simulation only. Before the fix, Shorebird rejected the deliberately mismatched
`1.0.19+20` test binary by hash while the UI still claimed ready. After the fix,
the engine logged `auto_update disabled`, the app-owned updater surfaced the
hash rejection internally, and the visible UI contained neither Receiving nor
restart-ready text. The same build connected through `adb reverse` to local
OpenCode `1.18.23`, opened Workspace -> More -> MCP and integrations -> Add MCP
server, rendered the project-scoped native form, and rejected an `ftp://` URL
inline without persisting it. Device logs contained no fatal exception,
RenderFlex, or overflow. The temporary server and reverse mapping were stopped,
the ignored signing file was removed, and this APK must not be distributed.
Flutter analysis is clean and all 309 tests pass serially.

Live background/wake reconciliation is now proven against OpenCode `1.18.23`
without spending provider tokens. With the app on Workspace, Android forced
deep idle and killed the process; a disposable session created and renamed
while idle appeared immediately after wake without manual refresh. In a second
run, the app stayed on that session's chat screen with PID `8628`, retained the
same PID while backgrounded, missed a server-side model-free shell event, then
showed its completed tool call and `wake-shell-proof` output immediately after
resume. Both paths had no fatal exception, RenderFlex, overflow, connection
refusal, or endpoint-unavailable log. The probe session was deleted afterward.

`tool/smoke_test.dart` now makes this server evidence stronger and safer. It
accepts explicit directory/workspace scope, verifies health, session lifecycle,
provider/agent catalogs, a model-free shell plus hydrated output, files/search,
and SSE, and deletes its disposable session from `finally` even when a check
throws. The hardened tool passed every check against the same OpenCode `1.18.23`
instance and analyzed cleanly.

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
- Stable Patch 11 ID: `620824`
- Provider runtime-auth synchronization: `421567626d39755b0bdac802f36fa0dbe3348667`
- Stable Patch 12 ID: `620848`
- Pre-existing provider runtime refresh: `b48222c686a99afcf796a254982931d717f29bc1`
- Stable Patch 13 ID: `620935`
- Provider alias consolidation: `59d2d194b9d915950381f7ca974845ee9a35c82c`
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

Patch 11 closes that runtime gate. Live phone testing showed that OpenCode `1.18.23`
stores a key submitted through `/api/integration/{id}/connect/key` in its new
integration database, while session prompts still read the legacy `/auth/{providerID}`
store. The model picker could therefore show a correctly connected Z.AI provider even
though chat returned `ProviderModelNotFoundError`. OpenCode also caches provider
inventories per instance, so writing legacy auth alone did not refresh an already
loaded `/root` session.

The app now synchronizes key credentials to both OpenCode stores and then disposes
only the selected location instance plus the server-default instance, matching
OpenCode's own compatibility client. It never logs or persists another copy of the
key. A transport regression verifies the exact integration, auth, selected-instance,
and default-instance request sequence and location scoping.

After the same auth synchronization and instance refresh were applied on the user's
Termux server, a real `/root` prompt completed through
`zai-coding-plan/glm-5.2` with `finish=stop`, no error, and the exact
`PHONE_ZAI_ROOT_OK` marker. The previously failing
`zai-coding-plan/glm-5.3-highspeed` pair then resolved correctly too; Z.AI rejected
the generation with HTTP 429 because the user's current subscription does not include
GLM-5.3-Highspeed. That remaining restriction is provider-plan entitlement, not a
model-picker or provider-ID mismatch. Flutter analysis was clean, all 234 tests
passed, Shorebird's dry run reported no issue, and Patch 11 was published to Stable
without native or asset overrides.

Patch 12 fixes the remaining upgrade-path gap found by testing the user's actual
mobile session rather than a CLI-created session. The app chat was scoped to
directory `/` and selected `zhipuai-coding-plan/glm-5.2`; earlier live verification
had exercised `/root` with the `zai-coding-plan` alias. Patch 11 refreshed a location
only when a key was newly submitted, so a credential connected before that patch did
not invalidate the already cached `/` provider runtime.

The connection controller now detects existing connected integrations and performs a
one-time provider-runtime refresh for the server default and for each selected
directory/workspace. The migration marker is scoped by server profile and location,
is written only after success, preserves immediate SSE startup, and never repeats on
ordinary reconnects. Older or temporarily unavailable servers remain connectable and
can retry later. After manually refreshing the exact `/` instance on the phone, a
real prompt through `zhipuai-coding-plan/glm-5.2` completed with `finish=stop`, no
error, and `PHONE_SLASH_ZHIPU_52_OK`. All 234 tests passed, Flutter analysis was
clean, Shorebird's dry run reported no issue, and Patch 12 was published to Stable
without native or asset overrides.

Patch 13 removes OpenCode's Z.AI compatibility aliases from user-facing model
selection without changing server truth. `zai`/`zhipuai` and their Coding Plan
variants now render as one branded provider group. Duplicate model IDs appear once,
the picker preserves the currently selected exact provider route when deduplicating,
and unique models remain selectable through the alias that actually exposes them.
The same presentation is used by the picker header and rows, Settings, assistant
model-change metadata, and Provider Connections. Generic providers keep their
existing `provider/model` labels. No model IDs or capabilities are hardcoded; all
model membership and metadata still come from OpenCode. All 236 tests passed,
Flutter analysis was clean, Shorebird's dry run reported no issue, and Patch 13 was
published to Stable without native or asset overrides.

Patch 14 corrects an unsafe assumption in that presentation. OpenCode treats the
four IDs as one GLM provider family for shared transforms, but they are distinct
backend routes: `zai` uses `api.z.ai`, `zhipuai` uses `open.bigmodel.cn`, and each
Coding Plan ID uses the corresponding domain's separate `/api/coding/paas/v4`
endpoint. Their credentials, connected state, model catalogs, and entitlements can
therefore differ even when model IDs overlap.

The picker still offers one compact Z.AI product-family filter, but duplicate model
IDs now remain independently selectable as `Global` and `China` routes. Selected
model headers, settings, assistant model-change metadata, and Provider Connections
show the route explicitly. Provider Connections no longer merges connected state or
chooses one alias's authentication method for the other. The exact OpenCode provider
ID remains the transport value, and a regression selects the China row and verifies
`zhipuai-coding-plan` reaches controller state.

Flutter analysis was clean, all 236 tests passed, and a clean release-mode launch on
the `Pixel_6` simulator reached the no-server state without a Flutter fatal error.
Shorebird's direct dry run reported no issue and Patch 14 (`621033`) was published to
Stable without native or asset overrides. The repository helper itself remains
fail-closed because the immutable full-release tag predates the already-live
Patch-2 `shorebird_code_push` dependency; the guard was not weakened.

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
