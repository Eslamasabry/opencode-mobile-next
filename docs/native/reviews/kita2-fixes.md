# KITA2 second-eye fixes

2026-11-01. Audited against checkout base `066a00be` on `native/fix-kita2-2026-11-01`.
The requested review is absent from that tree; its source is
`3bb7632e:docs/native/reviews/kita2.md` (findings originally reviewed at `7db496b2`).
All eight remaining BLOCKER/MAJOR defects are fixed in `:ui:kit`; finding 1 was already fixed.
No core, shell, feature, build-logic, catalog, or dependency contract was changed.
INTEGRATE-W1/W2's flat `Failure` contract is retained.

## Disposition by finding

Test names below are exact Kotlin test names in the four `Kita2*ReviewTest.kt` files under
`native/ui/kit/src/desktopTest/kotlin/io/github/eslamasabry/oc/ui/kit/`.

| Finding | Disposition and evidence |
|---|---|
| 1 BLOCKER — removed core fixtures | **Already fixed.** Current `PartBRulesTest` and `PartBScenes` use `Failure.Network`, `Failure.StorageRefused`, and the other flat variants, as INTEGRATE-W2 records. No `Failure.Net`, `Failure.Storage`, or `StorageProblem` remains in kit source. Desktop tests and Android host-test compilation verify the integrated fixtures. |
| 2 MAJOR — late secret registration / silent default | **Fixed.** Input transformation registers the final accepted text synchronously before publication; explicit Paste registers inside its edit before replacement and respects the length limit. Missing `LocalKitSecretSink` now fails closed. Tests: `B-SEC-204 KITA2-2 typing registers each accepted edit before publishing the state`; `B-SEC-204 KITA2-2 Paste registers the accepted value before publishing it`; `KITA2-2 a secret field requires the app redactor sink`. |
| 3 MAJOR — saved/prefilled secret dialog | **Fixed.** Secrets use empty `remember { TextFieldState() }`; nonempty secret initial values are refused. Tests: `KITA2-3 secret dialog text never enters saved state or recreation`; `KITA2-3 secret dialogs reject a prefilled initial value`. The first inspects nested saved-state payload values and recreates the field with the saved registry. |
| 4 MAJOR — sheet Back lost after dialog dismissal | **Fixed.** Back ownership uses active dialog visibility; exit releases only the matching retained dialog. Test: `B-KIT-032 KITA2-4 Back after Keep editing asks again and releases the closed dialog`. It verifies the old entry is gone and a second Back asks again without falling through. |
| 5 MAJOR — nondismissible modals leak Back | **Fixed.** Visible sheets/dialogs consume Back through exit; dismissal is checked inside the handler. Tests: `B-KIT-032 KITA2-5 a nondismissible sheet consumes Back`; `B-KIT-016 KITA2-5 a nondismissible dialog consumes Back`. Both assert zero navigation fallthrough and zero dismissal callbacks. |
| 6 MAJOR — working action runs from More | **Fixed.** Overflow items suppress working actions in semantics and at dispatch. Test: `B-KIT-011 KITA2-6 a working action stays inert in More and as a button`. It opens the real menu and asserts zero mutation callbacks. |
| 7 MAJOR — blank validation bypass | **Fixed.** Empty text is validated, and submission rereads and validates the current text for both button and IME paths. Test: `B-KIT-016 KITA2-7 blank validation blocks button and IME submission`. |
| 8 MAJOR — previous dialog operation's text reused | **Fixed.** Hidden dialogs release their input state. `operationKey` (default: `initial`) scopes visible operations; callers can distinguish targets with identical initial text. Test: `B-KIT-016 KITA2-8 reopening after cancellation starts with the current target`. Additional checks: `B-KIT-016 KITA2-8 different targets with the same initial words start fresh`; `B-KIT-016 KITA2-8 nonsecret edits survive recreation within an operation`. |
| 9 MAJOR — disabled risk confirmation still dispatches | **Fixed.** The confirmation button and callback both check current enabled/locked/checked/risk state. Test: `B-KIT-027 KITA2-9 disabling an open risk fold prevents confirmation`. |
| 10 MINOR — original menu test sensitivity | **Rejected from this remediation scope.** This is the review's MINOR test-strength issue; the requested fix scope is BLOCKER/MAJOR. The original `MenuScene` panel test remains unchanged. No claim is made that it proves real-menu dispatch exactly once. |

The review/plan 16's B-SEC-187 secret-state label conflicts with the checked-in behavior catalog's Linux-filesystem
row of that number. The secret persistence tests therefore use finding `KITA2-3` directly; no catalog was edited.
Hosts rendering editable secrets must explicitly provide a synchronous sink bound to the app's redactor. The screenshot
fixture now explicitly provides its test sink; production has no silent no-op fallback.

## Failing-first evidence

Before production edits, the regression run compiled and failed all 12 original new cases, covering each of findings
2–9. Expected failures included enabled working/disabled controls, blank Save enabled, stale reopened text, a retained
dialog, Back reaching the navigation fallback, a saved-state payload containing synthetic input, accepted secret
prefills, and an absent sink being accepted. The typed-input sink observed text after publication.

The initial Paste harness encountered an empty headless system clipboard. It was replaced with an injected clipboard
and rerun against the pre-fix `KitField`: it failed specifically because the sink observed the already-published pasted
value. Restoring the fix made it pass. No harness failure is counted as the Paste regression evidence.

All 12 original cases subsequently passed. Two additional operation-identity/restoration checks also passed.
Logs and red JUnit evidence are retained at `/tmp/kita2-red-final.log`, `/tmp/kita2-paste-red.log`, and
`/tmp/kita2-red-results/`; final build output is `/tmp/kita2-full-tests.log`.

## Validation

**BUILD SUCCESSFUL** on the committed code (`4cf206d2`):

| Task | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| `:ui:kit:desktopTest` | 140 | 0 | 0 | 0 |
| `:ui:kit:testAndroidHostTest` | 179 | 0 | 0 | 0 |
| **Total** | **319** | **0** | **0** | **0** |

The touched module compiles for desktop and Android. Existing compiler/AGP warnings remain; no compiler error remains.
The final combined invocation succeeded in 54 seconds; desktop results were reused from its preceding full successful
run and Android host tests executed after the sandbox-lock adjustment.

Every Gradle invocation held the PC's existing `/home/eslam/Storage/tmp/oc-build.lock` exclusively (opened read-only,
without modifying the lock file). Final command from the repository root:

```sh
exec 9< /home/eslam/Storage/tmp/oc-build.lock
flock -w 60 9
cd native
JAVA_HOME=/home/eslam/Storage/tools/jdk-21.0.13+9 \
ANDROID_HOME=/home/eslam/Android/Sdk \
./gradlew --no-daemon -Pkotlin.incremental=false \
  '-Dorg.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=768m -XX:+UseParallelGC -Dfile.encoding=UTF-8' \
  --max-workers=2 -I /tmp/kita2-test-env.gradle \
  :ui:kit:desktopTest :ui:kit:testAndroidHostTest
```

The explicit JVM option overrides this host's larger user-level heap setting. The temporary init script sets test-only
`skiko.data.path=/tmp/kita2-skiko`; Android host tests additionally set
`user.home=/tmp/kita2-robolectric-home`, `maven.repo.local=/home/eslam/.m2/repository`, and
`robolectric.dependency.repo.url=https://repo.maven.apache.org/maven2`.
The first desktop attempt could not write Skiko's home-directory lock; the first Android attempt could not write
Robolectric's home-directory download lock. These sandbox-only adjustments resolved them without repository changes.
No global HOME, Gradle settings, goldens, or device state was changed.

New test files were formatted with ktfmt; existing source edits follow their surrounding style and pass
`git diff --check`. The referenced `tool/format-changed.sh` is absent from this checkout.
No emulator, device, APK, or screenshot-baseline qualification is claimed.

## Local commits and import

The supplied worktree's shared Git metadata is read-only: staging fails while creating its `index.lock`.
Consequently its HEAD remains `066a00be`, and the edited files remain visible as worktree changes.
The small commits were created in separate writable Git metadata at `/tmp/kita2-local-commits.git`, using this exact
worktree as the source. No protected Git metadata was changed and nothing was pushed.

| Commit | Scope |
|---|---|
| `6d2acb74` | Findings 6 and 9: working overflow / risk confirmation |
| `61dd2bbb` | Findings 4 and 5: modal lifetime / Back ownership |
| `8fbf8c9f` | Findings 7 and 8: validation / dialog operations |
| `4cf206d2` | Findings 2 and 3: synchronous registration / secret state |
| This report's commit | Finding dispositions and validation evidence |

Every subject ends with `[skip ci]` and every commit has both requested trailers.
The local branch is `native/fix-kita2-2026-11-01`. Its incremental bundle is `/tmp/kita2-fixes.bundle`, with base
`066a00be` (already in the supplied repository). Import into a writable repository without changing its current branch:

```sh
git fetch /tmp/kita2-fixes.bundle \
  native/fix-kita2-2026-11-01:refs/heads/native/kita2-fixed-import
```

The bundle contains all four code commits and this report's commit. Commit-message and privacy checks were run locally;
no push command was used.
