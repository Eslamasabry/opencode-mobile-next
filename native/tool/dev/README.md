# Dev loop: see a change in seconds

Three loops, fastest first. All of them leave the owner's real app (`io.github.eslamasabry.opencode_mobile`) and the
preview (`…opencode_mobile.native`) alone: the phone loops install a third app, **OpenCode Dev**.

| Loop | What you see | Edit → visible (measured 2026-11-09, PC pop-os, ZTE NX721J over wireless adb) | Command |
|---|---|---|---|
| 1. Desktop hot reload | real screens with canned data in a desktop window, state kept | **7–11 s** per saved edit in `feature:chats` (2 s on a build-cache hit); the first reload after a start is slow (see below) | `dev-loop.sh hot` |
| 2. Phone, warm incremental install | the whole app on the phone, as "OpenCode Dev" | **19–26 s** (`once`), ~21 s (`watch`); 34 s on a loaded PC | `dev-loop.sh once` / `dev-loop.sh watch` |
| 3. Android Studio Live Edit | Compose edits pushed into the running dev app | not measured here (needs the Studio GUI once) | Run **OpenCode Dev (devDebug)** once in Studio |

`dev-loop.sh` is `/home/eslam/Storage2/tools/dev-loop.sh` on the PC (not in the repo: it holds this PC's paths, device id
and memory rules). `dev-loop.sh status` shows the warm daemon and what the phone has; `dev-loop.sh stop` ends the
daemons and the hot gallery. Owner rule: no local builds 00:00–02:00 (the script refuses `once`/`watch`/`hot`/`reload`
then; use the VPS).

## The `dev` flavor (`:androidApp`, `src/dev`)

- Package `io.github.eslamasabry.opencode_mobile.native.dev`, label **OpenCode Dev**, amber icon, `versionName` `…-dev`,
  debuggable. Its own data: it starts clean and never sees the real app's servers or chats.
- Signed with a dedicated dev keystore (SHA-256 `807ED593…020D`), never the PC's debug key (that is a real lineage) or the
  preview key. `native/local.properties` names it: `oc.devSigning=/home/eslam/Storage2/keys/dev-loop/dev-signing.properties`
  (a properties file with `storeFile`, `storePassword`, `keyAlias`, `keyPassword`; also `-Poc.devSigning` or
  `OC_DEV_SIGNING`). Without it the dev build stops at `refuseDevWithoutDevKey`; it never falls back to another key.
- Nothing global collides: every provider authority is `${applicationId}.…`; `src/dev/AndroidManifest.xml` turns off the
  seven home-screen widgets, both Quick Settings tiles and the `opencode-mobile://` link filter (links keep opening the
  real app, no chooser); launcher shortcuts point at the dev package (`src/dev/res/xml/shortcuts.xml`); the debug
  test-report broadcast is `${applicationId}.TEST_REPORT`. The share-sheet entry stays, labelled "OpenCode Dev".
- Only `devDebug` exists (no dev release/benchmark/qa, no dev unit or device tests), so CI and the `test*` tasks do no
  extra work. Crash reports default to off (it is not the `preview` flavor).
- Known overlap: the built-in phone server picks its port from the package name (`BuiltinLinux.defaultServerPort`:
  `.native` → 4100, otherwise 4098), so the dev copy defaults to 4098 like the switch build. It only binds a free port,
  so it never takes over a running server; set up the phone server in the dev copy only when you mean to.

## 2. Phone loop (`dev-loop.sh once | watch | install`)

- `once`: one `:androidApp:assembleDevDebug`, then `adb install -r --incremental` (falls back to `--streaming`), timed.
  `watch`: the same build under Gradle `--continuous`; every save rebuilds and re-installs. `install`: re-install the
  last APK. `./gradlew :androidApp:installDevDebug` also works (Studio uses it), only slower to install.
- Warm daemon: `-Xmx3g`, Kotlin in-process (one JVM, so `kotlin.daemon.jvmargs` never starts a second one), incremental
  Kotlin, configuration cache, `-Pandroid.injected.build.abi=arm64-v8a` (only the phone's ABI is packaged: 66 MB instead
  of both ABIs). The dev signing config also writes the v4 `.idsig` that incremental install streams from.
- Build outputs: `oc.buildRoot=/home/eslam/.cache/oc-dev-loop/build` in `native/local.properties` moves every `build/`
  directory off the nearly full Storage2 drive (settings.gradle.kts; unset everywhere else, nothing changes).
- Memory: the loop does not take the pc-heavy lock. It needs cap + 1.5 GB free to start a cold daemon, writes its cap
  (5000 MB: a warm daemon holds 2.6–4.2 GB) to `tools/dev-loop.holder`, and its daemon carries
  `-XX:ErrorFile=…hs_err_devloop…` as a marker (Gradle keeps `-D` jvmargs off the command line). `pc-heavy.sh`
  subtracts the part of the cap the dev daemon has not used yet from the memory it checks, and its end-of-job daemon
  clean-up leaves that daemon warm. The rule stays: at most one other heavy job. The loop runs at normal priority.
- It never opens the dev app on the phone; open it yourself (or `adb shell am start -n …native.dev/io.github.eslamasabry.opencode_mobile.MainActivity`)
  only when the phone is not in use.

Measured on a one-line padding change in `feature/chats` `ChatsScreen.kt` (2026-11-09, PC load average 6–8 from
other jobs):

| Step | Time |
|---|---|
| First cold build of `devDebug` (this worktree, empty `build/`) | 6 min 43 s |
| Warm no-change `once` | 9 s |
| `:feature:chats:compileAndroidMain` | 14–15 s (Kotlin rebuilds the whole KMP module after a common-source change: "Non-incremental compilation will be performed: Incremental compilation might be incorrect (KT-62686)"; `kotlin.kmp.separateCompilation=true` would avoid it but does not compile this project today) |
| `:androidApp:mergeLibDexDevDebug` + `packageDevDebug` | 4.4 s |
| install: `adb install --incremental` / AGP `installDevDebug` | 1.6–2.8 s / 6.5 s |
| **edit → on the phone, `once`** | **19.1 s, 25.5 s** (34 s when the loop ran under `nice` beside a test job, so it no longer does) |
| edit → on the phone, `watch` | ~21 s (19 s build from the change, then the install) |
| the same with `installDevDebug` instead of incremental install | 29 s, 45 s |

A change in a smaller module is faster, one in a large one (`:feature:chat`) slower: the module's compile is most of it.
After a change to `build-logic` or the root plugin list every module recompiles once (~5 min).

## 1. Desktop hot reload (`dev-loop.sh hot [conversations|chatparts|shell]`)

`./gradlew -Poc.hotReload=true :desktopApp:hotRunGallery --auto` with the dev daemon settings (`--auto`: reload on
save; with `DEV_LOOP_HOT_MODE=` it waits for `dev-loop.sh reload`, i.e. `./gradlew reload`, which suits an agent that
edits several files and then reloads once). `-Poc.hotReload=true` applies the
JetBrains Compose Hot Reload plugin (catalog `compose-hotreload`) to `:desktopApp` and every `oc.kmp.compose` module;
without it nothing changes, so CI and APK builds never carry it.

The gallery (`desktopApp/src/main/kotlin/…/desktop/dev/DevGallery.kt`, main class `DevGalleryKt`, not the desktop app's
entry) shows a 412 × 915 dp phone frame with:

- **Conversations**: `ChatsScreen` from the canned feed of `feature/chats` LooksCaptureTest;
- **ChatParts**: kit-chat transcript parts (tool steps, command box, permission card, code, inline diff), as in ChatScenes;
- **Shell**: the real desktop app (`ui:shell` `App`), no server.

Chips switch light/dark, Arabic and 1.3× text. A changed picture of the frame is saved (at most about once a second)
as `<scene>-<light|dark>.png` and `latest.png` in `~/.cache/oc-dev-loop/shots` (`OC_HOT_SHOTS`), so an agent edits,
saves, waits for the reload line and reads the picture.
A new canned screen: add a `Scene` and a composable that calls the real `XScreen(state, noActions())`.

Measured (2026-11-09, one-line padding edits in `feature/chats` `ChatsScreen.kt`, timed from the save to the runtime's
"Resolved 'dirty' @Composable scopes" line in `build/…/desktopApp/run/main/main.chr.log`, and checked on the picture):

| Step | Time |
|---|---|
| `dev-loop.sh hot` to the first picture, after a pull that changed many modules | 3–4.5 min (desktop jars compile with the hot-reload flags) |
| `--auto`: the first reload after a start | ~4.5 min: the recompiler forks its own single-use Gradle daemon and recompiles ~40 tasks once |
| `--auto`: each later saved edit → redrawn | **7.0, 7.4, 9.2, 11.2 s**; 2.1 s when the build cache already has that variant |
| explicit `dev-loop.sh reload`, the first one | 77 s (a second, cold Gradle daemon, because the gallery holds the first) |

Most of the 7–11 s is `:feature:chats:compileKotlinDesktop` recompiling the whole module (the same KT-62686 fallback as
on Android); a smaller module reloads faster. State survives a reload (the selected scene, theme and toggles stay).
Editing `DevGallery.kt` itself also reloads, but the screenshot writer then stops until the next start.

Memory: the gallery JVM, the dev daemon that launched it and the recompiler's own daemon (`-Xmx2g` from
gradle.properties) run together, about 6–7 GB; start it only when `free -m` shows that much available.

Needs a JetBrains Runtime for enhanced class redefinition. The script passes Android Studio's bundled JBR 25
(`-Pcompose.reload.jbr.binary=/snap/android-studio/current/jbr/bin/java`, override with `DEV_LOOP_JBR`); without one the
plugin stops with "Failed to find suitable JetBrains Runtime 21". The PC has no Xvfb, so the window opens on the
desktop (`DISPLAY=:1`).

## 3. Android Studio Live Edit / Apply Changes

Android Studio 2026.2.2 (build AI-262, supports AGP up to 9.5.1; this project uses 9.3.3) is installed (snap `android-studio`, `/snap/bin/android-studio`).
Once, by a person at the PC:

1. Open `/home/eslam/Storage2/wt/dev-loop/native` (the Gradle root, not the repo root).
2. Settings › Build, Execution, Deployment › Build Tools › Gradle: Gradle JDK = `/home/eslam/Storage2/tools/jdk21`
   (Metro needs JDK 21); Gradle user home = `/home/eslam/Storage2/tools/gradle-home` (shares the warm caches).
3. Build Variants: `:androidApp` → **devDebug**.
4. Pick the phone (NX721J) and the run configuration **OpenCode Dev (devDebug)** (`native/.run/`), press Run.
5. Settings › Editor › Live Edit: "Push edits automatically". Compose edits then reach the running app in seconds;
   other code edits use Apply Changes (Ctrl+Alt+F10) or a re-run.

Not measured from here: Live Edit and Apply Changes run inside the Studio GUI. Studio with ~110 modules and its own
Gradle daemon will want several GB (an estimate, not measured), so stop the dev loop daemon first (`dev-loop.sh stop`) and do not run it in
quiet hours (00:00–02:00). Live Edit covers Compose code in the modules the app compiles; if a KMP `commonMain` edit
is not pushed, use Apply Changes or `dev-loop.sh once`.
