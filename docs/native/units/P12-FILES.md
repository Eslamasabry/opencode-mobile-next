# P12-FILES — Project Files in a conversation (native parity push, 2026-11-03)

Branch: `claude/native-project-files-parity-ydrbm4`. The session's git rules fixed this name, so it replaces the brief's
`native/p12-files-2026-11-03`. Base: `native/m1-2026-11-03` at `8a4eee69`. Write set: `native/feature/files/**`,
`native/engine/files/**`, app wiring. Every edit outside that set is listed under "Outside the write set", with its reason.

**Shipping states, kept apart:**

- **Implemented:** all of it.
- **Committed and pushed:** 6 commits, each passing `tool/check_commits.sh` and `tool/scan_push.sh`.
- **Verified:** only the pure-Kotlin layers, in a scratch JVM build (see "Environment blocker").
- **Not verified:** the Compose UI and the Android code were **not compiled here**.
- **Not done:** no APK was built, nothing was signed, delivered or released.

## Environment blocker (read first)

This cloud session's network policy refuses `dl.google.com`, and `maven.google.com` redirects there. So nothing
from Google's repositories could be downloaded:

- no Android SDK (`commandlinetools`, `platforms;android-37.1`, `build-tools;37.0.0`);
- no AGP 9.3.3;
- no `androidx.*` artifacts, which also covers Compose 1.13 desktop (it depends on `androidx.compose.runtime`) and
  `androidx.lifecycle`;
- no foojay. JDK 17 came from the Temurin GitHub release instead.

Because the root `build.gradle.kts` resolves AGP, **no Gradle task of `native/` can run in this session**. That
includes `:core:common:desktopTest` and `:androidApp:assemblePreviewDebug`. The owner can fix it under Network
access (allow `dl.google.com` and `maven.google.com`; see https://code.claude.com/docs/en/cloud-environments#network-access).

What was verified instead, in scratch Kotlin Multiplatform JVM builds. These live in the session scratchpad and are
not committed. They compile the real module sources against Maven Central artifacts at the catalog's versions.

| Scratch build | Sources compiled | Tests run | Result |
|---|---|---|---|
| `kbuild` | `core/{common,model,security,domain,net}`, `protocol/oc2`, `engine/files` (common + desktop/jvmShared) | all `engine/files` common tests, **all** `protocol/oc2` common tests (existing + new), the Android `SocketShareServer` copied verbatim from `FilesWiring.kt` with `FilesWiringTest` | **100 tests, 0 failures** |
| `vmbuild` | the above core + `engine/files`, `ui/routes`, `feature/files/…/presentation` | `FolderViewModelTest`, `FileViewModelTest` | **18 tests, 0 failures** |

`vmbuild` uses scratch-only stand-ins for `androidx.lifecycle.ViewModel`/`viewModelScope`,
`androidx.navigation3.runtime.NavKey`, `SavedStateConfiguration` and the metrox factory key. They exist only because
the real ones live in Google Maven. Production code was compiled unchanged.

**Not compiled or run here:**

- `feature/files/**/ui`, `FilesEntries.kt`, `FilesGraph.kt` (Metro), `KitViewerBytes.kt`, the chat edit and
  `FilesWiring.kt` (the Android-API part);
- the two desktop Compose test files;
- the manifest provider;
- the Konsist/`:quality` gates and `:androidApp:assemblePreviewDebug`.

Every kit signature the screens use was checked by hand against the source, but the integrator must run:

```
cd native && ./gradlew :engine:files:desktopTest :feature:files:desktopTest :protocol:oc2:desktopTest \
  :ui:kit-content:desktopTest :feature:chat:desktopTest :quality:test :androidApp:testPreviewDebugUnitTest \
  :androidApp:assemblePreviewDebug
```

## Journeys now in the app (implemented; the Android build is unverified, see above)

1. **Browse the project tree from a conversation.**
   - Open a chat and tap **Files** in its top bar (beside Search). That opens `FilesRoute.Folder(from = chat)` at the
     project root, titled "Project".
   - Folder rows have a chevron; tap one to go a level deeper. Each folder is its own route, so system back and
     predictive back go up one level.
   - Dot entries stay hidden until **Show hidden files** (a visible bar action) is tapped. A folder with only hidden
     entries says so and offers the same action.
   - **Search files** under the bar searches the whole project by name (`/fs/find`).
   - Plain states with Try again cover:
     - a failed read (with Details);
     - a disconnected server ("isn't connected");
     - a server without file browsing.
2. **View a file.**
   - Tap a file row to open `FilesRoute.File` in the kit viewer frame (name, parent folder, Close).
   - **Code** uses the code body (and opens at `line` when given). **Text** and **Markdown** use the text body; see
     deviation 2.
   - **Images** show in the zoomable image view through the new kit-content `KitViewerImageBody`.
   - **PDF** pages render one at a time, each once and on demand, through `PdfRenderer`. There is a 10 MB / 200-page
     cap with plain words.
   - Anything else (binary, or invalid UTF-8 per B-MEDIA-001) shows "Can't show this file · type · size" and offers
     Open with.
3. **Ask about this file (`@`-mention into the composer).**
   - **Ask about this file** is the viewer's primary button. It appends `@path ` to *this conversation's* draft
     (`ConversationDrafts`, persisted).
   - When the server's project folder is known, it also adds the file mention attachment (`Mentions.projectFile`,
     the same shape as the composer's `@` list).
   - It then pops every Files page so the person lands back in the chat with the text in the field. The chat
     composer restores it on return.
4. **Open with.**
   - A visible **Open with** button under the viewer, and in the binary body.
   - The exact bytes go to a private cache copy behind the new FileProvider (`<applicationId>.files`), then
     `ACTION_VIEW` with a read grant through the system chooser.
5. **Share on Wi-Fi with QR and the never-share-secrets guard.**
   - **Share on Wi-Fi** is the viewer's second action. It opens a sheet with:
     - a QR code (`KitQr`) and the link;
     - who can download it ("Anyone on this Wi-Fi with the link…");
     - the download count and time left;
     - **Stop sharing**.
   - Closing the sheet, Stop, leaving the file, or 15 minutes ends the share.
   - The server binds only the phone's Wi-Fi/hotspot IPv4 address (`wlan*`/`swlan*`/`ap*`, private LAN only). The
     path is `/<192-bit SecureRandom token>/<name>`: anything else gets 404, non-GET/HEAD gets 405, and the file is
     sent as an attachment with `no-store`/`nosniff`.
   - `ShareGuard` refuses, before anything listens, every path that may hold secrets, with the sentence "may hold keys
     or passwords":
     - `.env*` files;
     - private keys and certificates (`id_rsa*`, `id_ed25519*`, `id_ecdsa*`, `*.pem`, `*.key`, `*.p12`, `*.pfx`,
       `*.keystore`, `*.jks`);
     - `.ssh`, `.gnupg`, `.aws`, `.kube`, `.docker`, `.gcloud`, `.codex`, `.claude`;
     - every `.oc-*` folder or file;
     - `~/.config/opencode`, `~/.local/share/opencode`, `~/.config/gh`, `/etc/ssh`;
     - `auth.json`, `shadow`, `.netrc`, `.npmrc`, `.git-credentials`, `*credentials*.json`;
     - any name containing "password", "secret" or "token".
   - No Wi-Fi gets its own plain sentence.

How to reach each screen: Conversations → a conversation → **Files** (top bar) → folder rows → a file row. The
viewer's buttons: **Ask about this file** / **Share on Wi-Fi** / **Open with**.

## Catalog rows covered

| Row | What proves it | Where |
|---|---|---|
| B-MEDIA-034 | token path serves exactly one file as attachment, downloads counted, 404 elsewhere, 405 non-GET, HEAD not counted, stop closes, 15-min expiry, new share stops the old, private-LAN-only, no Wi-Fi refused, secret files refused before listening; real socket over HTTP | `WifiShareTest` (12), `ShareGuardTest` (2), `FilesWiringTest` (1, socket), `FileViewModelTest` (4), `FilesScreensTest` (2) |
| B-MEDIA-001 | strict UTF-8 preview, NUL ⇒ binary, invalid UTF-8 shows no text while Open with/Share get exact bytes; viewer kinds; cut without splitting a character; gateway keeps non-UTF-8 bytes exact as base64 | `ProjectFilesTest` (5), `Oc2FilesTest` (2), `FileViewModelTest` (4), `FilesScreensTest` (1), `KitViewerBytesDesktopTest` (2) |
| B-NAV-026 | Files shows exactly the conversation's project (its own gateway and pinned location), folder/file navigation, hidden entries, disconnected/unavailable states, name search | `ProjectFilesTest` (4), `Oc2FilesTest` (4), `FolderViewModelTest` (7), `FilesScreensTest` (4) |
| B-NAV-028 | header shows the parent folder only (the kit's `ViewerPaths`, already covered by S3-KCCONTENT); path helpers | `ProjectFilesTest` (1) |
| B-CHAT-087 (part) | Ask about this file mentions the exact path in the chat it came from and returns there; a failed mention stays and says so | `FileViewModelTest` (2), `FilesScreensTest` (1) |

Run results (scratch JVM, see blocker): engine 24/24, gateway 6/6 new (plus all 69 existing `protocol/oc2` commonTest tests still green; its fixture-backed desktopTest suite was not run),
ViewModels 18/18, socket 1/1. The Compose tests (`FilesScreensTest` 8,
`KitViewerBytesDesktopTest` 2) are **written but not run**.

**Rows still missing or only partly covered:**

- **B-CHAT-087 send side:** a mention the person deletes before Send must send no file part.
  `Mentions.resolveFileMentions` exists in `:engine:composer` but the send pipeline does not call it yet. That
  belongs to the composer/delivery owner.
- **B-NAV-044** (the project hub with Changes, Git, terminal and other tool rows) belongs to `:feature:project`
  (S5-PROJUI). Files opens straight into the tree.
- **B-BUILD-004/005** (action-coverage ledgers) have no native ledger yet.

## Deviations (plain list)

1. **File chips in replies are not wired.**
   - The native chat renders reply text as plain `KitReplyBlock`s. It does not render `KitMarkdown`, which already
     has `KitMarkdownActions.onPath`, so a reply has no path chips to tap yet.
   - The destination is ready: the chat lead only needs
     `onPath = { path, line -> nav.open(FilesRoute.File(ref, path, line)) }` once replies render through
     `KitMarkdown`.
   - Tool-output "Show all" can use the same route.
2. **Markdown files show as wrapped text, not rendered.**
   - No Markdown parser module (`:core:markdown`) exists yet. `KitMarkdown` takes parsed `KitMdBlock`s, and nothing
     in native produces them from a file.
   - The engine already classifies `FileBody.Markdown`, so switching is a one-line change in `FileScreen`.
3. **PDF renders in-process (`PdfRenderer`), not in the isolated service** that plan 07 D03 / Flutter `LocalPdf` uses.
   - Limits are the same as Flutter's: 10 MB, 200 pages, at most 4096 px per side, one page at a time.
   - The security owner should decide whether the isolated renderer is required before release.
4. **The viewer menu items are missing:** Find, Copy, Wrap, Show source, Save to device, Copy path, Open in Review.
   - The kit has no clipboard part that features may use; `LocalClipboardManager` is outside K1's allowlist.
   - Save needs `ActivityResults.createDocument`, which has no Android binding yet.
   - **Attach to prompt** (upload the bytes) and **Add reference** are also not offered. Ask about this file (mention)
     is the one composer path.
5. **There is no Files hub (D01).**
   - The chat's Files action opens the tree root directly.
   - The rail/Ctrl+2/Go to "Files" entry with no conversation is not built. The routes always carry `from`.
6. **Opening a file on the phone from a chat link (D05a)** and **sharing phone-storage files** are not built. Native
   has no phone-file links yet. Only server project files (fetched bytes) are shared, which is the Flutter viewer's
   path.
7. **Phone files are not checked against app-private storage.**
   - Flutter's extra `isPrivatePath` check (app-private storage except built-in Linux) has nothing to guard here,
     because shared bytes always come from the server.
   - `ShareGuard` (names and folders) applies to every share.
8. **Arabic copy is provisional.**
   - The new strings (`strings_files.xml`) are English and Arabic only; the other languages fall back to English.
   - The Arabic lines are written by the agent and need a native-speaker pass.
9. **One edit is one line of the chat's single-owner library.** `ChatActions.openFiles()` defaults to a no-op so other
   hosts and test fakes still compile.

## Outside the write set (each needed for a reachable, real journey)

| Path | Why |
|---|---|
| `native/protocol/oc2/…/Oc2Gateway.kt` (+ `Oc2FilesTest.kt`) | `listFiles`/`fileContent`/`findFile` were `unavailable("fileBrowsing")` stubs. Without them nothing is real. Ported `fsList`/`fsReadBytes`/`fsFind`; `fileBrowsing = true`; `findText` now names `textSearch`. |
| `native/feature/chat/…/ChatUi.kt`, `ChatViewModel.kt`, `ChatScreen.kt` | the smallest reachable entry: one `KitLineAction` "Files" beside Search, `openFiles()` → `nav.open(FilesRoute.Folder(ref))`. Compact windows show 2 actions, so it is visible without overflow. |
| `native/ui/routes/…/FilesRoutes.kt` | new file, as the brief allows. |
| `native/ui/words/…/values{,-ar}/strings_files.xml` | new files with 12 lines of copy the ARB lacked. Everything else reuses `readerUi*`, `files*` and `fileShare*`. |
| `native/ui/kit-content/…/viewer/KitViewerBytes.kt` (+ desktop test) | new kit parts `KitViewerImageBody` and `KitPdfPngPage`. Features may not touch `ImageBitmap`/`Painter` (K1), so picture and PDF bytes are decoded in the kit (`decodeToImageBitmap`). The kit-content owner should review. |
| `native/MODULE_OWNERS` | two rows (`:engine:files`, `:feature:files`). `ModuleOwnersTest` requires them. |
| `native/androidApp/src/main/AndroidManifest.xml`, `res/xml/oc_files_paths.xml` | the FileProvider for Open with (`cache-path open-with/` only). |
| `native/androidApp/src/test/…/FilesWiringTest.kt` | the socket test for `FilesWiring.kt`. |

## What the integrator must do

1. Build on a machine that reaches Google Maven (command above). The most likely compile risks are:
   - Metro: `Set<ConversationDrafts>` is injected into `DraftComposerMentions` and relies on `ChatDraftBindings`'
     `@Multibinds` in `:feature:chat`. Also the `@ContributesTo(ConnectionScope)` accessor `FilesGraphAccessors`.
   - `androidx.core.content.FileProvider` must be on `:androidApp`'s compile classpath (it comes transitively through
     `activity-compose`).
   - `decodeToImageBitmap`'s opt-in.
2. Run `:quality:test`. Konsist K1/K2: every feature import was checked against `KitOnly`, and presentation imports no
   Compose or words.
3. Check it on the emulator:
   - open a chat → Files → a folder → a file → Ask about this file (the composer shows `@path `);
   - Open with on a PNG;
   - Share on Wi-Fi (QR scans; the link downloads on a laptop on the same Wi-Fi; `.env` is refused);
   - a PDF.
4. When replies render through `KitMarkdown`, wire `onPath` to `FilesRoute.File` (deviation 1).
5. Have the composer/delivery owner call `Mentions.resolveFileMentions` on send, so a deleted `@path` sends no file
   part (B-CHAT-087).
