# P12-GIT — the Git page in the native app

Date: 2026-11-02 (parity push started 2026-11-03 plan day). Base: `native/m1-2026-11-03` at `8a4eee69`.
Branch pushed: **`claude/git-native-parity-wh5zz2`** — the session's harness only permits pushing this branch; the
brief named `native/p12-git-2026-11-03`. The integrator should fetch `claude/git-native-parity-wh5zz2` (or rename it).
Write set used: `native/engine/git/**`, `native/feature/git/**`, one new route file, one wiring file, two lines in
`native/androidApp/build.gradle.kts`, and the smallest chat edit to reach the page (below).

Shipping state: **implemented and committed/pushed; engine + presenter verified by tests; the Compose screen, the
ViewModel, the entry, the Android wiring and the chat edit are NOT compiled** (see §4). Not assembled, not installed,
not run on a device. No PR, no CI, no signing, no release.

---

## 1. Journeys now in the app

| Journey | How |
|---|---|
| Open the Git page of a conversation's project | Conversations → open a chat → top bar **Git** (next to Search). Opens `GitRoute(ref)`. |
| See status | Branch row (name, "No commits yet", "Not on a branch"), Get latest / Send rows with their state ("Up to date", "2 commits to push", "First time, links this branch", "Add a remote below first"), half-done merge/rebase banner, conflict count, the changed files with kind and `+n −m` counts, recent saves. |
| Diff per file | Each changed file has a **Changes** row → full-height sheet with the file's patch (`git diff` + `git diff --cached`, untracked files as new, binary says so) in the kit `KitDiffView`. |
| Branches | Branch row → sheet: local branches (switch), remote-only branches ("On origin, not on this device yet" → switch creates a tracking branch), new branch name → **New branch** (`git switch -c`). Names validated before any shell. |
| Save (commit) | Tick/untick files (all start ticked except conflicts), message field, **Save N files** commits exactly the ticked paths. Missing identity shows the name/email form ("Your name on saves") which sets them for this repository only. |
| Save and send | One tap: commit, then push, then one reread; busy line names each half; a failed send keeps the save and says "Saved here, not sent yet. …". |
| Get latest / Send | `git pull --ff-only` / `git push` (`--set-upstream origin <branch>` the first time) through the server's `POST /api/shell`. Rejected push says "Pull first"; diverged history says merge/rebase on that device. |
| Discard one file | Diff sheet → **Discard changes** → confirm "Discard your changes?" → command picked by change kind; conflicts are never discarded. |
| GitHub sign-in | GitHub section on the page: **Sign in to GitHub** → code shown → **Open GitHub** (through `ExternalLinkPolicy` → opener; only `https://github.com/…`) → signed in as *login*; **Manage access on GitHub**; **Sign out of GitHub** (confirm). Token stored at `oc.githubToken.<profileId>` in the app's encrypted secret store; never logged; registered with the `Redactor`. |
| Unavailable states | Not connected / no project folder / server cannot run commands → plain sentence; not a repository → "Not a Git repository"; first-read failure → plain words + **Details** (Git's text, redacted) + Try again; a failed refresh keeps the last snapshot. |

Errors are always the plain `:ui:words` sentence (all Git copy already existed in English and Arabic); Git's own text
appears only under Details through the `Redactor`.

## 2. Catalog rows

Covered by tests (names start with the row id):

| Row | Where |
|---|---|
| B-SEC-083 | `GitScriptTest` (quoting, NUL refusal without echo, exact render, name validation, envelope decode with noise, cut-off / nodir / nogit) |
| B-BUILD-013 | `ServerGitShellTest` (start → poll → paged output → DELETE; quoted `sh -c` in the project folder; 404 = unsupported with no server text; server timeout) |
| B-BUILD-015, 016 | `GitParsersTest` (unicode renames with spaces, conflicts by code, detached/unborn, numstat joins incl. binary/rename; log/branches/operation/remotes; stderr classification; scrubbing) |
| B-BUILD-017 | `RealGitTest` (desktopTest; the generated scripts through real `sh`/`git`/`base64`: unicode rename, not-a-repo, hostile commit message runs nothing, branches, diff, discard, push/pull, rejected push, non-fast-forward pull) |
| B-BUILD-018..021 | `GitRepositoryTest` (+ `GitParsersTest` branch names) |
| B-NAV-020, 021, 022 | `GitPageTest`, `GitPresenterTest`, `GitScreenTest` |
| B-SEC-084, 085, 086, 087, 088, 089, 090 | `GitHubTest` (+ `GitPresenterTest`, `GitScreenTest` for 084) |

Not covered / deviations:

- **B-SEC-081** (token through a private 0600 file to the phone's Linux): the phone runtime is not part of the native
  app yet, so no host receives the token. Not ported.
- **B-SEC-082**: "on servers no sign-in is offered". The brief asks for GitHub device sign-in to be reachable, and the
  only host today is a server, so the page **offers** the sign-in but keeps the line "Sending and getting latest use
  the Git sign-in set up on *server*" and **never sends the token to the server** (the server shell has no token path).
  The token becomes useful when the phone runtime host lands.
- **B-NAV-023** (header chip names the branch): the chat gets a plain **Git** action, not a branch chip (the chip needs
  a branch read inside the chat world, which belongs to the chat lead).
- Remotes/add-remote, clone, fetch (Flutter "Remotes, clone and details") and "Write it for me" / "Ask the agent to fix
  conflicts" are not ported.
- "Copy code" for the GitHub code is not offered (no kit clipboard part outside Details); the code is shown large.

## 3. Tests and results

Run in a scratch JVM-only Gradle project (no AGP; see §4) that compiles the real sources of `:core:common`,
`:core:model`, `:core:security`, `:core:data`, `:core:net`, `:engine:git` (explicit-API on) and the presentation package
of `:feature:git`, with `kotlinx-coroutines-test` and `ktor-client-mock`:

| Suite | Tests | Failures |
|---|---|---|
| `GitScriptTest` | 6 | 0 |
| `GitParsersTest` | 7 | 0 |
| `GitRepositoryTest` | 11 | 0 |
| `ServerGitShellTest` | 4 | 0 |
| `GitHubTest` | 10 | 0 |
| `GitPageTest` | 7 | 0 |
| `RealGitTest` (desktopTest, real git 2.x) | 4 | 0 |
| `GitPresenterTest` (feature) | 7 | 0 |
| **Total** | **56** | **0** |

`GitScreenTest` (6 Compose tests by visible text, EN + AR) is written but **not run** (Compose cannot be resolved here).

## 4. Environment limits (why parts are unverified)

- `dl.google.com` is refused by this session's egress proxy and `maven.google.com` redirects there, so the Android SDK
  could not be installed and AGP/androidx/Compose artifacts cannot be resolved. `./gradlew :androidApp:assemblePreviewDebug`
  and every module task of the real build could not run. Maven Central was reachable only through Google's Central
  mirror, which is what the scratch check used.
- Therefore **not compiled**: `feature/git/ui/*`, `GitViewModel`, `GitEntries`, `GitRoute`, `androidApp/.../GitWiring.kt`,
  and the chat edit. They were checked by hand against the exact kit/route/Metro signatures in this tree.
- `tool/format-changed.sh` was not run (formatter not available offline).

## 5. Integrator to-do

1. Build and fix: `:engine:git:desktopTest :feature:git:desktopTest :feature:chat:desktopTest :androidApp:assemblePreviewDebug :quality:test`.
2. `MODULE_OWNERS`: add rows for `:engine:git` and `:feature:git` (owner P12-GIT) — `ModuleOwnersTest` will fail
   without them. `:engine:git` depends on core only, so it needs no `engineOrder` entry; add `git` there if another
   engine should use it.
3. Chat edit (smallest possible, chat lead to review): `ChatActions.openGit()` with a default no-op (`ChatUi.kt`),
   `ChatViewModel.openGit()` → `nav.open(GitRoute(route.ref))` (route/nav became private vals), and a second top-bar
   `KitLineAction` "Git" (`gitSheetTitle`, tag `chat-git-chip`) in `ChatScreen.kt`.
4. Profile deletion (B-SEC-085): `GitWiring` sweeps `oc.githubToken.*` entries of removed profiles whenever the loaded
   profile list changes (only after `PreviewStores.warm()` succeeded). Please also register the prefix in the deletion
   transaction (`PreviewRuntime.remove`: `registerSecret("oc.githubToken.", "git")`) and in "reset saved sign-ins".
5. OC2 owner: the native `Oc2Gateway` reports `remoteShell = false` although OC2 serves `/api/shell`; the page
   therefore tries the shell and treats a 404 as "can't run Git". Setting `remoteShell = true` for OC2 makes
   B-NAV-022's capability gate exact.
6. `ExternalLinkOpener` is implemented privately in `GitWiring` (Intent `ACTION_VIEW`); replace with the platform's
   shared opener when `:android:platform` joins the app.

## 6. Files

- `native/engine/git/**` — `GitModels`, `GitShell` (+ `shQuote`), `GitScript` (+ envelope), `GitParsers`, `GitProblem`,
  `GitRepository`, `ServerGitShell`, `GitHubDeviceFlow` (+ `NetGitHubHttp`), `GitHubAccount` (+ token store),
  `GitPage`, `GitServices`; tests in `commonTest` and `desktopTest`.
- `native/feature/git/**` — `GitUi`/`GitActions`, `GitPresenter`, `GitViewModel`, `GitScreen`, `GitWords`,
  `GitEntries`; tests.
- `native/ui/routes/.../GitRoute.kt` (new file).
- `native/androidApp/.../graph/GitWiring.kt` (new file) and two module lines in `androidApp/build.gradle.kts`.
