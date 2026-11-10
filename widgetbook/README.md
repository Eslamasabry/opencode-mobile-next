# App catalog

A Widgetbook catalog that renders the app's **real screens** with fake data,
an **app map** made from the code, and a **screenshot gallery**, all rebuilt
by themselves when the code changes.

| URL (Tailscale) | What |
| --- | --- |
| `http://<pc-tailnet-ip>:8766/` | the catalog (every screen, every state; theme, locale, text scale, viewport addons) |
| `/map/` | the app map: a box per screen, an arrow per navigation link, live thumbnails |
| `/shots/` | one full-size screenshot per use-case, grouped by area, each linking to its live use-case |
| `/status.json` | the last build: refs, time, coverage counts, sweep failures |

Deep link to a use-case: `/#/?path=usage/plans-page/loading` (add `&preview` to
hide the side panels).

The screens follow [the screen standard](../docs/ux-system/screen-standard.md);
the items marked **[auto]** there are checked on every rebuild (see "The screen
standard check" below).

## How it is made

```
widgetbook/
  lib/catalog.dart          catalogComponent(): how a use-case names its screen
  lib/all_components.dart   the list of every component (add new ones here)
  lib/fakes/fake_app.dart   the shared fake app scope (see below)
  lib/<area>/*.dart         use-cases, one folder per app area
  tool/inventory.dart       every screen in lib/ui, from the code (package:analyzer)
  tool/coverage_check.dart  the coverage ratchet
  tool/app_map.dart         navigation edges -> map.json (+ map/index.html)
  tool/sweep.mjs            headless Chromium screenshots + gallery
  coverage_baseline.json    screens with no use-case yet (only shrinks)
../tool/catalog/rebuild.sh  unattended rebuild + publish
../tool/catalog/serve.py    the static server behind :8766
```

The package depends on the app with `path: ..` and never changes the app's
`pubspec.yaml`. Screens are the app's own classes; only the data is fake.

### The shared fake app scope (`lib/fakes/fake_app.dart`)

- `FakeApp.create(profiles:, connected:, capabilities:, prefs:)`: a real
  `ConnectionController` over in-memory preferences and an in-memory Keystore.
  With `connected: true` it reports a live connection whose server has the
  given `ServerCapabilities`. Repository calls are not answered; a screen that
  lists server data needs its own fake in the use-case.
- `FakePlatform.android | web | none`: pins `PlatformCapabilities`.
- `FakeAppScene<T>`: builds a `FakeApp` (or a harness built on one) and shows
  the real screen, and disposes everything with the use-case. `autoTap:` presses a
  keyed widget after load, for states reached by a tap.
- Theme, locale and text scale come from the Widgetbook addons (the app's real
  light and dark themes, English and Arabic, 1.0 / 1.3 / 2.0).
- Usage has its own harness, `lib/fakes/usage_harness.dart`, built on `FakeApp`.

## Add a use-case

1. Write a function that returns a `CatalogComponent` in `lib/<area>/`:

   ```dart
   CatalogComponent guideScreen() => catalogComponent(
     area: 'Guide',                 // folder in the tree
     screen: 'GuideScreen',         // the real class in lib/ui
     name: 'Getting started guide', // component name
     useCases: [
       WidgetbookUseCase(
         name: 'On an Android phone',
         builder: (_) => FakeAppScene<FakeApp>(
           platform: FakePlatform.android,
           create: () => FakeApp.create(profiles: const []),
           builder: (context, app) => const GuideScreen(),
         ),
       ),
     ],
   );
   ```

   `area`, `screen`, `name` and each use-case `name` must be string literals:
   the tools read them from the source. Do not put `/` in a name.
2. Add it to `allComponents()` in `lib/all_components.dart`.
3. `dart run tool/coverage_check.dart --write` to remove the screen from
   `coverage_baseline.json`, and commit both.

A screen that is a private class cannot be catalogued (another package cannot
see it). Catalog its public parent.

## The coverage ratchet

`dart run tool/coverage_check.dart` (run it from `widgetbook/`) lists the
screens in `lib/ui` (a public widget named `*Screen` / `*Page`, or one the code
pushes as a route or sheet; kit parts are excluded) and compares them with the
`screen:` of every `catalogComponent` and with `coverage_baseline.json`. It
fails when

- a screen has no use-case and is not in the baseline: a **new uncatalogued
  screen**. Add its use-case. This is the check that keeps the catalog from
  rotting;
- the baseline lists a screen that now has a use-case, or that no longer
  exists: run `--write` to shrink the baseline;
- a use-case names a `screen:` that is not in `lib/ui` (a typo).

`--write` refuses to add screens to the baseline; only `--init` (first time)
does. Like `test/kit_ratchet_test.dart`, the baseline only goes down.

## The screen standard check

`flutter test test/standard_check_test.dart` (inside `widgetbook/`, a plain VM
test, a few seconds) pumps **every** use-case through the builder the catalog
uses at 412 x 915 in four passes (en at text 1.0, en at 2.0, ar at 1.0, en
dark at 1.0) and measures:

- **overflow**: any Flutter error or RenderFlex overflow;
- **cut-off text**: a paragraph that exceeds its max lines (an ellipsis shows);
- **text sizes**: distinct font sizes of visible text, icons ignored (limit 3);
- **main actions**: filled primary `KitButton`s in the first view (limit 1);
- **first-view items**: top-level `KitRow`, `KitSwitchRow`, `KitExpandRow`,
  `KitAgentCard`, `KitTaskCard`, `KitFeedItem`, `KitRequestCard` whose top is
  inside the first 915 px (limit 7);
- **row lines**: a `KitRow`, `KitSwitchRow`, `KitExpandRow` or `KitFeedItem` in the first view shows more than 2 lines of text (limit 2; en 1.0 pass);
- **red**: any fill, icon or text that is clearly red (hue 345-15, saturation
  over 0.5, lightness 0.25-0.75). Amber is listed per use-case as "amber used"
  but is not a finding.

The last three limits apply to the en 1.0 pass; the others to all four. Results
go to `standard_report.json`. `standard_baseline.json` holds today's findings:
a new finding fails the test, and a fixed one must be removed from the baseline
(`UPDATE_STANDARD_BASELINE=1 flutter test test/standard_check_test.dart`; add `ADMIT_NEW_USE_CASES=1` only when cataloguing a screen that already shipped, so its findings today are recorded). Each
rule has a tiny fake screen in the test that breaks it, to prove the check
catches it. Use-cases that read Widgetbook's knobs panel are skipped (a plain
test has no Widgetbook). Fonts: Geist and Noto Sans Arabic are loaded, so text
widths are close to the real ones. Plan cards that are not a kit card type are
not counted as first-view items.

The rebuild runs it after the web build, shows the result in `status.json`
(`standard`), and adds a badge per screenshot in `/shots/` plus
`/shots/issues.html`.

## The app map

`dart run tool/app_map.dart --out build/site` writes `build/site/map/map.json`
(`nodes {id, class, file, area, reach, useCases}`, `edges {from, to, via}`,
`unresolved`) and copies `map/index.html`. The page has two tabs: **Sitemap**,
a foldable tree from Home (a screen shown before appears as "shown above"),
then "Not reachable from Home" and "Opened only from search or notifications";
and **Focus**, "Comes from" | the screen picture | "Opens", each link named
(screen, sheet, search, deeplink). Tap a picture to open the use-case; `/map/#ScreenId`
links to a screen.

It is syntax-level and best effort. Edge kinds (`via`):
`push` (Navigator / `pushKitPage` with a route whose builder builds the
screen), `sheet`, `route` (a helper returns a route), `helper:NAME` (a call to
a top-level function, or a method of the same class, that opens the screen),
`named:/path` (a route in `main.dart`'s table), `row` (a Settings hub row),
`search` (a search-index result), `embed` (a screen built
inside another, such as the home tabs), `launch` (built by `main.dart` or
`lib/main_parts`: launch routes, deep links, share; shown as `@entry` nodes).
Anything it cannot see (a builder that calls a function or variable, a
computed route name) is listed under `unresolved`, never guessed. Navigation
into private classes is counted in `internalPrivateTargets`.

## The screenshot sweep

`node tool/sweep.mjs --base http://127.0.0.1:8770 --catalog catalog.json
--out build/site/shots` loads every use-case at 412 x 915 with `&preview`,
three at a time with a timeout, saves `shots/<area>/<component>/<state>.png`
and a `.thumb.png`, flags blank pages and "Scene failed" console errors in
`shots/report.json`, and writes `shots/index.html`.

## Run by hand

```bash
FL=~/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin
cd widgetbook
$FL/flutter pub get && $FL/flutter analyze
$FL/dart run tool/coverage_check.dart
$FL/dart run tool/catalog_scan.dart catalog.json
$FL/dart run tool/app_map.dart --out build/site
$FL/flutter build web --release --no-web-resources-cdn   # background, ~2 min
```

## Automation

- `tool/catalog/rebuild.sh` (timer `oc-catalog-rebuild.timer`, every 10
  minutes) reads the local refs `preview/fb-usage` (app code) and
  `feat/widgetbook-poc` (this package), exits if neither moved since the last
  attempt, otherwise merges them in the detached worktree
  `/home/eslam/Storage2/wt/oc_app-catalog`, builds the web catalog (one build
  slot of `tool/qa/machine_lock.sh`, `nice -n 15 ionice -c3`), runs the
  coverage check, the map and the sweep, and publishes to
  `/home/eslam/Storage2/catalog/releases/<app12>-<catalog8>/`, switching the
  `current` symlink atomically. The last 3 releases are kept. A failure keeps
  the previous release serving and records the step in `last_failure.json`
  and in `current/status.json` (`lastFailure`). Log: `catalog/last_build.log`.
- `oc-catalog-serve.service` serves `catalog/current` on `<pc-tailnet-ip>:8766`
  (`tool/catalog/serve.py`; no caching, follows the symlink on each request).
- By hand: `tool/catalog/rebuild.sh --force`, or `systemctl --user start
  oc-catalog-rebuild.service`.

## Web build seams

The app compiles for the browser with two tiny seams (native builds unchanged):
`lib/builtin/abi_shim.dart` (re-exports `dart:ffi` `Abi`, with a browser
stand-in) and `lib/builtin/int64_max_shim.dart` (a browser cannot hold the
signed 64-bit maximum). The catalog also fakes the Keystore, the BYO signer and
agent cleanup, because their defaults read `dart:io`'s `Platform`.
