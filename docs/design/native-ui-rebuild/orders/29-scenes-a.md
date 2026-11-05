ORDER: 29-scenes-a
ENGINE: sonnet
DEPENDS: 01
WRITES: native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/scene/FoldersOpenScene.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/scene/PortalScene.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/scene/ServersCast.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/scene/ServersLinkScene.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/scene/ServersWelcomeScene.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/scene/SetupCast.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/scene/SetupPhoneScene.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/scene/SetupReadyScene.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/scene/SetupStepsScene.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/scene/SetupUnpluggedScene.kt native/ui/kit/src/desktopTest/kotlin/**/rb/o29/** native/ui/words/src/commonMain/composeResources/values/strings_rb29.xml native/ui/words/src/commonMain/composeResources/values-ar/strings_rb29.xml docs/design/native-ui-rebuild/waivers/29.tsv docs/design/native-ui-rebuild/evidence/29.md
GATE: native/tool/design/gate_order.sh 29
TIMEOUT_MIN: 240
---
# Order 29-scenes-a: scenes

This order is binding together with `docs/design/native-ui-rebuild/GOLDEN-STANDARD.md`. Read the whole standard before you touch a file. Where this order and the standard disagree, stop and mark the order blocked (standard §13).

## 1. Identity

- Engine: Claude Sonnet 5.5 (`sonnet`). Time limit: 240 minutes.
- Worktree (created by the lead; never create one): `eslam@169.58.71.211:~/wt/rb-29`, branch `native/rb-29-scenes-a`.
- Flutter source of truth: `~/wt/flutter-ref` at FLUTTER_REF `8302de53773adef45eaaad016df2bd3cd51d1ebe`.
- Gate manifest: `docs/design/native-ui-rebuild/orders/29.gate.tsv` (22 goldens). Gate command: `native/tool/design/gate_order.sh 29`.
- Depends on: order 04. Do not start until each is merged into `native/looks-pass-2026-11-04` (the lead dispatches only then).
- Report as agent name `rb-29-sonnet` (standard §11).

## 2. Parts, in the order you port them

| # | Tracker id | Flutter file | Golden test | Goldens | Tier | Native files you own (NEW = you create it) | Capacity classes (role) |
|---|---|---|---|---|---|---|---|
| 1 | `part-folders_open_scene` | `lib/ui/kit/scenes/folders_open_scene.dart` (`~/wt/flutter-ref/lib/ui/kit/scenes/folders_open_scene.dart`) | – | 0 | T2 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/scene/FoldersOpenScene.kt` | – |
| 2 | `part-portal_scene` | `lib/ui/kit/scenes/portal_scene.dart` (`~/wt/flutter-ref/lib/ui/kit/scenes/portal_scene.dart`) | – | 0 | T2 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/scene/PortalScene.kt` | – |
| 3 | `part-servers_cast` | `lib/ui/kit/scenes/servers_cast.dart` (`~/wt/flutter-ref/lib/ui/kit/scenes/servers_cast.dart`) | – | 0 | T2 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/scene/ServersCast.kt` | – |
| 4 | `part-servers_link_scene` | `lib/ui/kit/scenes/servers_link_scene.dart` (`~/wt/flutter-ref/lib/ui/kit/scenes/servers_link_scene.dart`) | – | 0 | T2 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/scene/ServersLinkScene.kt` | – |
| 5 | `part-servers_welcome_scene` | `lib/ui/kit/scenes/servers_welcome_scene.dart` (`~/wt/flutter-ref/lib/ui/kit/scenes/servers_welcome_scene.dart`) | – | 0 | T2 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/scene/ServersWelcomeScene.kt` | – |
| 6 | `part-setup_cast` | `lib/ui/kit/scenes/setup_cast.dart` (`~/wt/flutter-ref/lib/ui/kit/scenes/setup_cast.dart`) | – | 0 | T2 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/scene/SetupCast.kt` | – |
| 7 | `part-setup_phone_scene` | `lib/ui/kit/scenes/setup_phone_scene.dart` (`~/wt/flutter-ref/lib/ui/kit/scenes/setup_phone_scene.dart`) | – | 0 | T2 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/scene/SetupPhoneScene.kt` | – |
| 8 | `part-setup_ready_scene` | `lib/ui/kit/scenes/setup_ready_scene.dart` (`~/wt/flutter-ref/lib/ui/kit/scenes/setup_ready_scene.dart`) | – | 0 | T2 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/scene/SetupReadyScene.kt` | – |
| 9 | `part-setup_steps_scene` | `lib/ui/kit/scenes/setup_steps_scene.dart` (`~/wt/flutter-ref/lib/ui/kit/scenes/setup_steps_scene.dart`) | – | 0 | T2 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/scene/SetupStepsScene.kt` | – |
| 10 | `part-setup_unplugged_scene` | `lib/ui/kit/scenes/setup_unplugged_scene.dart` (`~/wt/flutter-ref/lib/ui/kit/scenes/setup_unplugged_scene.dart`) | – | 0 | T2 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/scene/SetupUnpluggedScene.kt` | – |

Logic-only parts (no widget, no golden): each needs `rb/o29/<Pascal>ParityTest.kt` asserting every constant and rule of the Dart file (standard §6 step 8): `folders_open_scene`, `portal_scene`, `servers_cast`, `servers_link_scene`, `servers_welcome_scene`, `setup_cast`, `setup_phone_scene`, `setup_ready_scene`, `setup_steps_scene`, `setup_unplugged_scene`.

## 3. Gallery goldens (scenes that render several parts)

22 PNGs, 14 scenes, tier T4. Golden tests: `test/goldens/kit/kit_scenes_golden_test.dart`.

Scenes: `kit_scenes_default`, `kit_scenes_default_1280x800`, `kit_scenes_default_1600x1000`, `kit_scenes_default_360x800`, `kit_scenes_default_800x1280`, `kit_scenes_default_ar`, `kit_scenes_default_ar_1280x800`, `kit_scenes_default_text2`, `kit_scenes_default_text2_1280x800`, `kit_scenes_mirrored_ar`, `kit_scenes_mirrored_ar_1280x800`, `kit_scenes_portal_folders`, `kit_scenes_servers`, `kit_scenes_setup`

## 4. Write set

You may create, change or delete only paths matching these globs (the `WRITES:` header). `gate_order.sh` fails on any other path.

- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/scene/FoldersOpenScene.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/scene/PortalScene.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/scene/ServersCast.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/scene/ServersLinkScene.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/scene/ServersWelcomeScene.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/scene/SetupCast.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/scene/SetupPhoneScene.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/scene/SetupReadyScene.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/scene/SetupStepsScene.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/scene/SetupUnpluggedScene.kt`
- `native/ui/kit/src/desktopTest/kotlin/**/rb/o29/**`
- `native/ui/words/src/commonMain/composeResources/values/strings_rb29.xml`
- `native/ui/words/src/commonMain/composeResources/values-ar/strings_rb29.xml`
- `docs/design/native-ui-rebuild/waivers/29.tsv`
- `docs/design/native-ui-rebuild/evidence/29.md`

## 5. Gate and done

1. `cd ~/wt/rb-29 && native/tool/design/gate_order.sh 29` exits 0. It runs the static checks, fetches the Flutter goldens, runs `--tests '*.rb.o29.*'` in the manifest modules, and compares every golden on its bounding box (standard §9).
2. `docs/design/native-ui-rebuild/evidence/29.md` holds the gate summary (pasted), every waiver, and the folded gaps.
3. Every part (and screen) of this order is `gate-pass` in the tracker, then you post `done` (standard §11).
4. Your commits follow standard §10; you never push.

Done means all four. Anything else is `blocked` or `failed` with one line saying why.
