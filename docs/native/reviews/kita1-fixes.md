# KITA1 review fixes

Date: 2026-11-01. Code baseline: `066a00be` (`native/fix-kita1-2026-11-01`).
Review source: `3bb7632e:docs/native/reviews/kita1.md`, read from the main checkout because that newer
documentation commit is not in this worktree's baseline. The findings were checked against this worktree's code.
Contracts: plan 01, plan 03, `native/AGENTS.md`, `INTEGRATE-W1.md` and `INTEGRATE-W2.md`.

All requested BLOCKER/MAJOR findings are addressed: one already fixed by integration, five fixed here.
Production changes stay in `:ui:kit`, in `KitScreen.kt` and `KitRow.kt`. Core's flat `Failure` contract is unchanged.

## Disposition by finding

| Finding | Disposition and evidence |
|---|---|
| **1 BLOCKER — nonexistent error contract** | **Already fixed.** `KitErrorKind.of`, `PartAScenes` and `PartADesktopTest` use flat `Failure.Network`, `TimedOut`, `Certificate`, `Refused` and other current leaves. No stale `Failure.Net` references remain in the kit source. Preserve W2 D4's explicit classifier decision for `TimedOut`, transport refusals and `Refused(DispatchUncertain)`. The existing `B-KIT-024 timeouts and transport failures are network and the rest are other` test passes; both module targets compile. |
| **2 MAJOR — body exposed to system/IME insets** | **Fixed** — `Kita1FrameInsetsTest`: `B-KIT-029 finding 2 frame consumes safe and keyboard insets with or without optional slots`. Apply and consume safe drawing plus IME insets around the framed column, independently of optional slots; ground remains outside the padding. An internal inset-source override permits deterministic geometry tests without changing the public screen signature. Test all four bar/bottom combinations, keyboard appearance/removal, horizontal cutout clearance and no second payment by an inset-aware child. |
| **3 MAJOR — long value consumes title width** | **Fixed** — `Kita1RowValueTest`: `B-KIT-027 finding 3 measured long value moves below title at normal scale and in RTL`. Measure the title/value with the active typography, allowing for the leading tile, gaps and chevron. Move a value below if the pair cannot fit; retain large-text stacking. Test 320/412 dp, LTR/RTL, title text layout without ellipsis, long values below and short values inline. |
| **4 MAJOR — disabled row's action still enabled** | **Fixed** — `Kita1DisabledRowTest`: `B-KIT-051 finding 4 disabled row blocks inline and stacked action activation`. Propagate the row's disabled state and reason to its trailing action and make its callback inert. Pointer input and direct semantics activation cannot call it, at normal or large text scale. |
| **5 MAJOR — independent sibling status slots** | **Fixed** — `Kita1PaneStatusTest`: `B-KIT-035 finding 5 sibling panes share status priority override and removal`. Establish the slot above expanded sibling panes when no encompassing slot exists. Draw its selected line under the list's bar; pane screens contribute to that slot. Test Update versus Work, app-wide Connection override, and removal of the detail contribution. Exactly one line remains throughout. |
| **6 MAJOR — list sheet confined to one pane** | **Fixed** — `Kita1PaneSheetTest`: `B-KIT-032 finding 6 list sheet covers window and blocks sibling input`. Page/pane frames reuse an encompassing sheet host, creating one only when needed. At 1280×800, a sheet declared in the list has a window-wide scrim and a 560 dp panel; taps aimed at detail/side actions invoke neither callback. |
| **7 MINOR — duplicated disabled reason** | **Rejected for this change set, scope only.** The request targets BLOCKER and MAJOR findings. This MINOR finding is not claimed fixed or rejected as a defect. |
| **8 MINOR — time-only live-region changes** | **Rejected for this change set, scope only.** The request targets BLOCKER and MAJOR findings. This MINOR finding is not claimed fixed or rejected as a defect. |

## Red before green

All five new regression tests executed and failed before their corresponding fixes. The inset-source test seam
existed for the inset red run but was not consumed by the frame yet.

| Finding | Observed red assertion |
|---|---|
| 2 | Expected body left inset 16 dp, observed 0 dp. |
| 3 | The title was ellipsized and its visible bounds were zero. Confirmed again with the final test assertions and only the width fix removed; restored the fix afterward. |
| 4 | Trailing action failed `assertIsNotEnabled`. |
| 5 | Expected one status line, observed two. |
| 6 | Expected window-wide scrim, observed the 296 dp list-pane width. |

Focused green runs passed after the fixes. The final complete module run also passed:

| Task | Tests | Failures / errors / skipped |
|---|---:|---:|
| `:ui:kit:desktopTest` | 131 | 0 / 0 / 0 |
| `:ui:kit:testAndroidHostTest` | 179 | 0 / 0 / 0 |
| **Total** | **310** | **0 / 0 / 0** |

Both production targets and their test sources compiled. `git diff --check` passed. The commit-message gate
`native/tool/check_commits.sh` passed. The referenced `tool/format-changed.sh` is absent in this checkout;
new tests were formatted with ktfmt 0.64, while production edits retain surrounding formatting to avoid unrelated churn.

## Reproduction and sandbox setup

One Gradle invocation at a time under the existing shared `/home/eslam/Storage/tmp/oc-build.lock`, opened read-only
and held with exclusive `flock`. From `native/`:

```sh
JAVA_HOME=/home/eslam/Storage/tools/jdk-21.0.13+9 \
ANDROID_HOME=/home/eslam/Android/Sdk \
JAVA_TOOL_OPTIONS=-Dskiko.data.path=/tmp/kita1-skiko \
./gradlew --no-daemon -Pkotlin.incremental=false \
  -Dorg.gradle.jvmargs='-Xmx2g -XX:MaxMetaspaceSize=768m -XX:+UseParallelGC -Dfile.encoding=UTF-8' \
  --max-workers=2 -I /tmp/kita1-tests.init.gradle \
  :ui:kit:desktopTest :ui:kit:testAndroidHostTest
```

The temporary init script redirects only test JVM home/lock files, reusing the existing Maven cache:

```groovy
allprojects {
    tasks.withType(Test).configureEach {
        systemProperty 'user.home', '/tmp/kita1-test-home'
        systemProperty 'maven.repo.local', '/home/eslam/.m2/repository'
    }
}
```

Create `/tmp/kita1-test-home` before running. Initial attempts encountered read-only home paths for Skiko and
Robolectric's download lock, before UI assertions could run. The temporary settings above resolved both;
no project or user Gradle settings were changed. Final run: **BUILD SUCCESSFUL**, 46 seconds.
Warnings about the read-only Android analytics settings and existing deprecated APIs were nonfatal.
No emulator/device, APK/release, iOS, full application, or golden-image verification gate was run.
Inset regression evidence uses synthetic insets on desktop; the Android host suite is Robolectric evidence.

## Local commit handoff

The worktree's shared Git metadata lives outside the sandbox's writable roots. Updating its normal index or
branch fails with a read-only-filesystem error at `index.lock`. The checkout branch therefore remains at `066a00be`,
with the finished files present as local changes.

Five small fix commits and this report commit are preserved in a separate writable local Git repository at
`/tmp/kita1-fixes.git`, on `native/fix-kita1-2026-11-01`, and exported as `/tmp/kita1-fixes.bundle`.
The bundle requires baseline `066a00be`, already present in the original repository. Every subject ends in
`[skip ci]` and every commit has the two requested coauthor/session trailers. No push was performed.
