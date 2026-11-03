# INTEGRATE-P12 — P12 integration ledger

Branch `native/p12-integrate-2026-11-03` (worktree `~/wt/claude-integrate`). Local only, never pushed.

## Rules (amended brief, 2026-11-02)

- Merge only verified branches, in the order of `~/wt/verify/verified.txt` (`<unit> p12v/<unit> <commit>`),
  each with `git merge --no-ff`. Units in `~/wt/verify/blocked.txt` are listed here, not merged.
- After each merge: `:androidApp:assemblePreviewDebug compileKotlinDesktop` plus the touched modules' tests.
- After each green wave: preview APK, line appended to `~/wt/p12-integrated.txt`.
- Gradle: `-Dorg.gradle.workers.max=3 -Dorg.gradle.jvmargs=-Xmx3g`, `JAVA_HOME=~/wt/native-repair-20261102/tools/jdk`.
  Desktop UI tests need `LD_LIBRARY_PATH=~/wt/native-repair-20261102/tools/runtime-libs/root/usr/lib/x86_64-linux-gnu`
  (this host has no `libGL.so.1`; without it every Skiko test fails with `LibraryLoadException`).
- Signing: `androidApp` has no release signing config, so wave APKs are `assemblePreviewDebug`.

## Merged before the amended brief (earlier session)

S2-MD, S2-PLATFORM, P3, P6, S2-SERVERS, P12-SETTINGS (`0eac8f3d3`), see `git log`.
An unverified `p12v/projects` merge was in progress at hand-over; it was aborted (`projects` not in
`verified.txt`, its verifier was still working).

## Merge log

| # | Unit | Commit | Merge | Result |
|---|------|--------|-------|--------|
| 1 | P12-COMPOSER (`native/p12-composer-2026-11-03`) | `335056b0f` | `501d3abf9` | Clean merge. assemblePreviewDebug + compileKotlinDesktop green; `:engine:composer`, `:engine:delivery`, `:feature:chat`, `:protocol:oc2`, `:ui:words` desktopTest green; `:quality:test` green except `p3.TranscriptStreamUiTest` (see Known) |
| 2 | migration (`p12v/migration`) | `b50dce933` | (this) | Conflicts: MODULE_OWNERS and androidApp dependency list (union), androidApp `buildTypes{benchmark}` + `productFlavors{switch}` (both kept), MainActivity (MigrationRoot now wraps the settings-aware `App(…, appearance, AppLocale.applied)`), ChatWorldReviewTest (`filePickers =` named arg). Compile green; `:engine:migration`, `:feature:gate`, `:ui:words` green; `:android:migration:testQaUnitTest` 64/64 when rerun alone (B-MIGR-010 took 49 s of its 60 s budget, timed out under load); `:quality:test` only the known P3 timeout |
| 3 | agents (`p12v/agents`) | `8e27771db` | (this) | Conflicts: androidApp dependency list (union), BasicComposerHost `attachmentWords` (kept the composer's fuller failure mapping; agents only reformatted it), ChatWorldReviewTest (kept `filePickers =`). Compile green; `:engine:agents`, `:feature:agents`, `:feature:chat`, `:feature:servers`, `:protocol:paseo`, `:ui:routes`, `:ui:words` green; `:quality:test` only the known P3 timeout |
| 4 | linux (`p12v/linux`) | `432ebf26d` | (this) | Conflicts: MODULE_OWNERS and androidApp dependency list (union); `compose.resources` is now `implementation` (LinuxWiring needs it at runtime, P6's debug proof keeps it), `compose.foundation` stays debug-only; BasicComposerHost/ChatWorldReviewTest kept HEAD (same as #3). Compile green; `:feature:chat`, `:feature:linux`, `:feature:phone`, `:feature:servers`, `:ui:routes` green; `:quality:test` only the known P3 timeout |
| 5 | git (`p12v/git`) | `dc8783c59` | (this) | Conflicts: MODULE_OWNERS, androidApp deps (union), ChatWorldReviewTest (HEAD). Integration fix: WorldComposerFilesModelsTest `SelectionGateway` got duplicate `createSelectedSession`/`setSessionAgent` (the verifier's stubs vs the composer's real fakes); kept the composer's. Compile green; `:engine:git`, `:feature:chat`, `:feature:git`, `:ui:routes` green; `:quality:test` only the known P3 timeout |
| 6 | platform (`p12v/platform`) | `92732babf` | (this) | Conflicts: MODULE_OWNERS, androidApp deps (union; `:android:notifications` was already listed, kept once), MainActivity (P12-SETTINGS `attachBaseContext` + P12-PLATFORM `onPostCreate`/`onNewIntent`/`onResume` routing, both kept; the migration-wrapped root stays), BasicComposerHost (HEAD). Compile green; `:android:tiles`, `:engine:updates`, `:feature:chat`, `:feature:chats`, `:feature:updates`, `:ui:routes` green; `:quality:test` only the known P3 timeout |
| 7 | previewqr (`p12v/previewqr`) | `d8ca921f1` | (this) | Conflicts: MODULE_OWNERS, androidApp deps (union); ChatScreen top-bar actions (git's chip kept, then `+ extraActions` from preview); ServersUi/ServersViewModel/ServersScreen (agents' Settings › Agents entry + pairing scanner/paste, both kept). Compile green; `:android:webview`, `:feature:chat`, `:feature:preview`, `:feature:servers`, `:ui:routes`, `:ui:words` green; `:quality:test` only the known P3 timeout |
| 8 | prompts (`p12v/prompts`) | `ebb4951a7` | (this) | Conflicts: MODULE_OWNERS, androidApp deps (union); ChatEntries (preview's `extraActions` + prompts' `ChatPromptsDoor` both passed; one `navigator`, plus `inbox`); ChatScreen (git chip, saved-prompts action, then `+ extraActions`; both params); BasicComposerHost, WorldComposerFilesModelsTest, ChatWorldReviewTest kept HEAD (the verifier's gateway stubs again duplicated the composer's fakes). Compile green; `:feature:chat`, `:feature:prompts`, `:ui:routes` green; `:ui:kit-chat` and `:quality:test` only the two pre-existing frame-test timeouts |
| 9 | schedules (`p12v/schedules`) | `0a7183981` | (this) | Conflicts: MODULE_OWNERS, androidApp deps, ServersUi/ServersViewModel (agents + pairing + `openSchedules`, union); BasicComposerHost (HEAD). Compile green; `:android:schedules`, `:engine:schedules`, `:feature:chat`, `:feature:schedules`, `:feature:servers`, `:protocol:oc2`, `:ui:routes` green; `:quality:test` only the pre-existing P3 timeout |
| 10 | chat-cards (`p12v/chat-cards`) | `ab1f7739f` | (this) | Conflicts: MODULE_OWNERS, androidApp deps (union); ChatEntries: the cards slot (`CompositionLocalProvider(LocalChatCards)`) now wraps the screen that also gets preview's `extraActions` and the prompts door; constructor `inbox, cards, navigator`. Compile green; `:engine:cards`, `:engine:composer`, `:engine:delivery`, `:feature:chat`, `:protocol:oc2`, `:ui:routes`, `:ui:words` green; `:ui:kit-chat`/`:quality:test` only the two pre-existing frame-test timeouts |
| 11 | chat-transcript (`p12v/chat-transcript`) | `10bee575d` | (this) | Conflicts in `:feature:chat` only: ChatEntries (turn `facts` + `onOpenSession` added to the preview/prompts/cards call); ChatViewModel (keeps `private val route` for git's `openGit`, adds `clock`); ChatScreen (both param sets; context-ring action then git/prompts, `+ extraActions`); ChatTranscript work line (`TurnWorkLine` when facts exist, cards under it). Compile green; `:engine:composer`, `:engine:delivery`, `:engine:transcript`, `:feature:chat`, `:protocol:oc2`, `:ui:words` green; `:ui:kit-chat`/`:quality:test` only the two pre-existing frame-test timeouts (still `UncompletedCoroutinesError`, no assertion) |
| 12 | chat-header (`p12v/chat-header`) | `49b0293e5` | (this) | Conflicts: ChatEntries (header VM + slot added to the combined call), ChatScreen params (union). **Integration fix:** the real app always passes the header slot, and `ChatHeaderTopBar` replaced the whole bar, which would have hidden git, saved prompts, preview and the context reading. `ChatHeaderTopBar` now takes `extraActions` (inline up to the window limit, the rest appended to its ⋯ menu); ChatScreen builds the unit actions once for both bars. New `ChatHeaderUiTest` "P12 integration the header bar keeps the other units' bar actions…" passes. Compile green; `:engine:composer`, `:engine:delivery`, `:feature:chat`, `:protocol:oc2`, `:ui:words` green; `:ui:kit-chat`/`:quality:test` only the two pre-existing frame-test timeouts |
| 13 | chat-feel (`p12v/chat-feel`) | `b8061844d` | (this) | **Integration fixes.** (a) Prompts and chat-feel both drove the composer's one `pendingInsert`/`insertDone` hook (append vs replace): `InsertRequest` gains `replace` (default false); ChatScreen offers "Edit and resend" first (`replace = true`), then saved prompts/slash commands, and routes each acknowledgement to the side that offered it; BasicComposerHost runs one effect keyed on the request, after draft restore, appending or replacing, focusing on replace. (b) Both units declared `AndroidExternalLinkOpener`: kept PREVIEW-QR's single app binding (one opener, security invariant) and injected it into `AndroidChatOutside`. Also ChatViewModel (`route`, `nav`, `clock`, `outside`), ChatScreen/ChatTranscript params and sheets (union). Compile green; `:engine:composer`, `:engine:delivery`, `:feature:chat` (prompts' and chat-feel's insert tests both pass), `:protocol:oc2`, `:ui:words`, `:androidApp:testPreviewDebugUnitTest` (11/11) green; `:ui:kit-chat`/`:quality:test` only the two pre-existing frame-test timeouts |
| 14 | files (`p12v/files`) | `4efbc330a` | (this) | Conflicts: MODULE_OWNERS, androidApp deps, AndroidManifest (platform's receivers + files' FileProvider, union); ChatViewModel (Git and Files routes, the full constructor); ChatScreen: the Files action joins the shared bar actions (plain bar and header bar); Oc2Gateway `findFile` implemented by both, kept the composer's (skips directories, tolerates odd entries); ChatWorldReviewTest/WorldComposerFilesModelsTest kept HEAD (duplicate verifier stubs). Compile green; `:engine:files`, `:feature:chat`, `:feature:files`, `:protocol:oc2`, `:ui:kit-content`, `:ui:routes`, `:ui:words` green; `:androidApp:testPreviewDebugUnitTest` 11/12 in the loaded run, `FilesWiringTest` passes alone (see Known); `:quality:test` only the pre-existing P3 timeout |
| 15 | search (`p12v/search`, P12-SEARCH-USAGE) | `57874639b` | (this) | Conflicts: MODULE_OWNERS, androidApp deps, ServersUi/ServersViewModel (agents, pairing, schedules + `openUsage`, union); BasicComposerHost, ChatWorldReviewTest (HEAD). Compile green; `:engine:usage`, `:feature:chat`, `:feature:chats`, `:feature:search`, `:feature:servers`, `:feature:usage`, `:protocol:oc2`, `:ui:routes`, `:ui:words`, `:androidApp:testPreviewDebugUnitTest` green; `:quality:test` only the pre-existing P3 timeout |
| 16 | settings follow-up (`p12v/settings`) | `6325290ef` | (this) | One verifier commit on top of the already-merged P12-SETTINGS. Conflict: androidApp `compose.resources` (already `implementation` since #4; one line, both reasons). Compile green; `:androidApp:testPreviewDebugUnitTest` green; `:quality:test` only the pre-existing P3 timeout |

## Known

- **Pre-existing, not merge breaks:** `quality/…/p3/TranscriptStreamUiTest` (P3) and `ui/kit-chat`
  `ReplyStreamFrameBenchmark` B-CHAT-096 PB-11 run ~8 000 real Compose frames inside the default 1-minute
  `runTest`/`runComposeUiTest` budget. On this host they take 81 s and 95 s even alone at load average 4.4
  (124 s at load 18). `git diff 596ee5310 HEAD` (the pre-session base) is empty for `ui/kit-chat`,
  `engine/transcript`, `ui/kit`, `ui/design` and `core/markdown`, so no merge here changed their path. Owner
  fix: a longer explicit `timeout` on those two tests, or move them to the device benchmark (P3 already owes J2).
- Same pattern: `android/migration` `MigrationRunnerTest` B-MIGR-010 (exhaustive kill-at-every-step) runs 49 s of a
  60 s `runTest` budget alone and times out when the host is loaded.
- `androidApp` `FilesWiringTest` B-MEDIA-034 (P12-FILES) is flaky under a loaded parallel run: after
  `server.close()` HttpURLConnection can reuse a kept-alive connection and get a 200 instead of the expected
  `IOException`. It passes alone (twice). Owner fix: `Connection: close` on the test's requests.

## Blocked units

(none so far)

## Waves

| Wave | Time | APK | Units |
|------|------|-----|-------|
| 1 | 2026-11-02 22:01 | `~/wt/p12-apks/p12-wave1-501d3abf9-preview-debug.apk` (25.1 MB) | P12-COMPOSER |
| 2 | 2026-11-02 23:55 | `~/wt/p12-apks/p12-wave2-021be255a-preview-debug.apk` (34.9 MB) | migration, agents, linux, git, platform, previewqr |
