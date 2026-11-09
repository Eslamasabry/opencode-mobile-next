# Smoothness round 2, 2026-11-09

Branch `native/smooth-2` (fixes 1 and 2) and `native/smooth-2-menus` (fix 3). Brief: build the three look-neutral
fixes round 1 left as options, measure them on the Galaxy S24, and guard them with tests.

## What landed

| Fix | What changed | Landed | Guard tests |
|---|---|---|---|
| 3. Menus in the app's own window | `KitSheetHost` holds a menu layer above the page, its sheet and its dialog. A `KitMenu` inside a host is drawn there with the composition locals of the place it opened from, as a popup carried them. Same panel, placement (`MenuPlacement` in the layer's pixels) and motion. An outside press closes it and is not passed to the page (as a focusable popup), a press on the panel's padding keeps it open, and Back closes it. Outside any host (none in the app today) it keeps the popup. | e9b0fd0ce | `KitMenuInWindowTest` (no popup node while a page's menu is open; it opens beside its anchor; an outside press closes it without reaching the page; a row runs its item; the panel's padding keeps it open) |
| 1. Liquid glass at half resolution | `native/third_party/backdrop`: the Kyant `backdrop` 2.0.1 sources, vendored with their licence and a README. A dependency substitution in `settings.gradle.kts` serves the fork wherever `io.github.kyant0:backdrop` resolves. `drawBackdrop(resolutionScale)` records the pane's backdrop layer (backdrop, colour controls, blur, lens) at that share of the pane's pixels and scales it up. Rim light, specular, tint and sheen stay outside that layer, at full resolution. `KitPlatform.liquidResolution` is 0.5 on Android. Only panes frosted at least 4 dp take it (bars, message box, sheets, menus). The chips (2 dp) and the + (1 dp) stay at full resolution, where text behind them would read softer. | ae02f883c | `KitLiquidResolutionTest` (which panes take the reduced resolution; a full-resolution platform keeps all at 1) |
| 2. One live background | `KitTheme` holds one `KitLiveShare`: one `LiveFrameClock` and one recorded picture per page size. Every framed page (`KitScreen`, `KitLiveBackground(shared = true)`) shows it: both pages on screen during a push or pop read one clock, record one picture and share its blurred copies. A shader look's reduced frame is recorded once a frame for all pages of a size, memoised on every input of its pixels (look, time, strength, palette, size, density). Each page still draws the picture in its own frame, so nothing shows through a sliding page. A look's thumbnail keeps its own. The arriving page's look now continues from the same moment instead of starting its own clock at 0. | ae02f883c | `KitLiveShareTest` (two pages draw the look once a frame, on one clock and one picture, in Solid and Glass) |
| Live capture (coordinator, the owner's "stale background" report) | The shared picture is recorded on every draw of every page that shows it, never kept. Its blurred copies follow glass-live's frame keys, merged with glass-live's fix. | ae02f883c | `KitGlassLiveCaptureTest` (a pane's frost follows the picture through three changes, on a page's own picture and on one shared by two pages; pages of other sizes never share a picture) |

CI: GitHub-hosted runs 42600579307 (menus) and 42600583080 (fixes 1 and 2). Both are green, and the new tests are in
the shard reports. The remaining kit failures in those runs are the same ones native/next shows. The
`PhoneOwnServerMarkTest` failure is native/next's own (it fails on the menu-only branch too).

## Galaxy S24, 120 Hz (Test Lab): before

Same method as round 1. `GlassBenchmark` (FrameTimingMetric, R8, `CompilationMode.Full`, 3 runs) on the proof page,
matrix 8831947300754051935. "Before" is native/next at ece1e5d20 plus the round-2 proof scenes (push/pop, still
shots), built on the VPS. "Late" means a frame finished after its deadline. "> 1 vsync late" means more than 8.3 ms
late, a frame the eye sees repeated.

| Path · look | frames/run | late % (per run) | > 1 vsync late % (per run) | frame CPU p50 / p90 / p99 ms (median run) |
|---|---|---|---|---|
| chatGlass (still background) | 1882 / 1884 / 1883 | 0 / 0 / 0 | 0.0 / 0.0 / 0.0 | 4.5 / 5.1 / 5.7 |
| chatGlassAurora | 1971 / 1930 / 1926 | 100 / 100 / 100 | 0.0 / 100 / 100 (device slow state, as in round 1) | 9.9 / 11.2 / 12.1 |
| menuGlass (reply menu, ×8) | 356 / 357 / 356 | 16 / 14 / 15 | 10.7 / 10.4 / 11.5 | 3.7 / 7.3 / 30.4 |
| menuSolid | 346 / 349 / 349 | 13 / 11 / 12 | 11.0 / 9.5 / 8.6 | 2.3 / 5.6 / 28.8 |
| menuGlassAurora | 1874 / 1888 / 1887 | 81 / 59 / 54 | 16.9 / 2.4 / 2.5 | 5.6 / 9.9 / 15.1 |
| pushGlass (push + pop ×8) | 793 / 794 / 794 | 63 / 61 / 60 | 10.3 / 5.5 / 4.7 | 6.4 / 10.9 / 22.8 |
| pushGlassAurora | 1548 / 1555 / 1549 | 75 / 79 / 78 | 12.5 / 11.4 / 12.4 | 7.2 / 11.4 / 17.4 |

The menu paths still show what round 1 found: about 10 % stutter frames in Solid and Glass alike, and a frame CPU
p99 of 29–30 ms on the open (the popup window's creation and first composition). Push and pop stutter 5–12 %,
about 12 % over the aurora.

## After: not measured yet (Test Lab quota)

The "after" matrix was refused with `TEST_QUOTA_EXCEEDED`. The free plan's physical-device runs for 2026-11-09 were
used up (billing stays off). It runs on the next day's quota with the same targets. The app APK is the preview
benchmark build of 8dfd7114c: ece1e5d20 plus the three fixes, without later native/next work, so only the fixes
differ from "before". The test APK is the one already used for "before":

```
gcloud firebase test android run --type instrumentation \
  --app androidApp-preview-benchmark.apk  # CI artifact native-previewBenchmark-apk of branch native/smooth-2-proof
  --test benchmark-benchmark.apk          # the "before" run's test APK (gs://…/smooth2-base-1448/)
  --device model=SC-51E,version=36 \
  --test-targets "class io.github.eslamasabry.oc.benchmark.GlassBenchmark#chatGlassAurora,class …#chatGlass,class …#menuGlassAurora,class …#menuGlass,class …#menuSolid,class …#pushGlassAurora,class …#pushGlass,class io.github.eslamasabry.oc.benchmark.GlassShots" \
  --environment-variables additionalTestOutputDir=/sdcard/Download --directories-to-pull /sdcard/Download --timeout 45m
```

What each "after" row isolates: `chatGlass` and `chatGlassAurora` measure fix 1 alone (one page and no menu).
`menuSolid` measures fix 3 alone (Solid has no liquid panes). `menuGlass` measures fixes 1 and 3. `pushGlass` and
`pushGlassAurora` measure fix 2, plus fix 1 on their panes.

The look proof for fix 1 comes from `GlassShots`: `chat-glass.png` against `chat-glass-liquid-full.png`, and the same
pair over the painted aurora. Both are from one build on one device at the same scale; `liquid=full` puts the library
back to full resolution. On desktop the liquid pane is the stand-in face (the library runs on Android 13+ only), so
desktop renders cannot show this difference. The menu shots (`menuGlass` / `menuSolid` with `hold`) failed on
"before" because UiAutomator only searched the focused popup window for "Done". With the menu in the app's window
they run as written.

## Not changed

Typing recomposing the composer (round 1 cause 6) belongs to another agent.
