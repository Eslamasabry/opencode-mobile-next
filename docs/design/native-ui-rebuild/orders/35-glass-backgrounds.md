ORDER: 35-glass-backgrounds
ENGINE: sonnet
DEPENDS: 31,34
WRITES: native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveAuroraImage.kt native/ui/kit/src/commonMain/composeResources/drawable/aurora_dark.webp native/ui/kit/src/commonMain/composeResources/drawable/aurora_light.webp native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveVivid.kt native/ui/gate/src/desktopTest/kotlin/**/rb/o35/** native/ui/kit/src/desktopTest/kotlin/**/rb/o35/** native/ui/words/src/commonMain/composeResources/values/strings_rb35.xml native/ui/words/src/commonMain/composeResources/values-ar/strings_rb35.xml docs/design/native-ui-rebuild/waivers/35.tsv docs/design/native-ui-rebuild/evidence/35.md
GATE: native/tool/design/gate_order.sh 35
TIMEOUT_MIN: 300
---
# Order 35-glass-backgrounds: glass backgrounds

This order is binding together with `docs/design/native-ui-rebuild/GOLDEN-STANDARD.md`. Read the whole standard before you touch a file. Where this order and the standard disagree, stop and mark the order blocked (standard §13).

## 1. Identity

- Engine: Claude Sonnet 5.5 (`sonnet`). Time limit: 300 minutes.
- Worktree (created by the lead; never create one): `eslam@169.58.71.211:~/wt/rb-35`, branch `native/rb-35-glass-backgrounds`.
- Flutter source of truth: `~/wt/flutter-ref` at FLUTTER_REF `8302de53773adef45eaaad016df2bd3cd51d1ebe`; Glass sources at GLASS_REF `1bbdc3f9c7779148a689186e6d5ca3cd59062ab5` in `~/Storage/Code/oc_app` (standard §2.2).
- Gate manifest: `docs/design/native-ui-rebuild/orders/35.gate.tsv` (0 goldens). Gate command: `native/tool/design/gate_order.sh 35`.
- Depends on: order 31, order 34. Do not start until each is merged into `native/looks-pass-2026-11-04` (the lead dispatches only then).
- Report as agent name `rb-35-sonnet` (standard §11).

## 2. Parts, in the order you port them

| # | Tracker id | Flutter file | Golden test | Goldens | Tier | Native files you own (NEW = you create it) | Capacity classes (role) |
|---|---|---|---|---|---|---|---|
| 1 | `part-glass-kit_live_aurora_image` | `lib/ui/kit/live_background/styles/kit_live_aurora_image.dart` @ GLASS_REF (`git -C ~/Storage/Code/oc_app show 1bbdc3f9c:lib/ui/kit/live_background/styles/kit_live_aurora_image.dart`) | – | 0 | T6 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveAuroraImage.kt`<br>NEW `native/ui/kit/src/commonMain/composeResources/drawable/aurora_dark.webp`<br>NEW `native/ui/kit/src/commonMain/composeResources/drawable/aurora_light.webp` | – |
| 2 | `part-glass-kit_live_vivid` | `lib/ui/kit/live_background/styles/kit_live_vivid.dart` @ GLASS_REF (`git -C ~/Storage/Code/oc_app show 1bbdc3f9c:lib/ui/kit/live_background/styles/kit_live_vivid.dart`) | – | 0 | T6 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveVivid.kt` | – |

Logic-only parts (no widget, no golden): each needs `rb/o35/<Pascal>ParityTest.kt` asserting every constant and rule of the Dart file (standard §6 step 8): `kit_live_aurora_image`, `kit_live_vivid`.

Also read, at GLASS_REF: `lib/ui/kit/live_background/kit_live_style.dart`, `lib/ui/kit/live_background/kit_live_styles.dart`, `lib/ui/kit/kit_live_background.dart`, `lib/ui/kit/style/kit_style.dart`, `assets/backgrounds/aurora-dark.webp`, `assets/backgrounds/aurora-light.webp`.

## 3. Lead prerequisites

- In the live-look registry (order 31, `KitLiveStyles`): the ids `auroraImage` and `vivid` registered against `KitLiveAuroraImage` / `KitLiveVivid` (lead stubs; you fill them).
- The two WebP files copied from GLASS_REF `assets/backgrounds/` into `native/ui/kit/src/commonMain/composeResources/drawable/` (you may replace them only with byte-identical copies).

## 4. Write set

You may create, change or delete only paths matching these globs (the `WRITES:` header). `gate_order.sh` fails on any other path.

- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveAuroraImage.kt`
- `native/ui/kit/src/commonMain/composeResources/drawable/aurora_dark.webp`
- `native/ui/kit/src/commonMain/composeResources/drawable/aurora_light.webp`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/KitLiveVivid.kt`
- `native/ui/gate/src/desktopTest/kotlin/**/rb/o35/**`
- `native/ui/kit/src/desktopTest/kotlin/**/rb/o35/**`
- `native/ui/words/src/commonMain/composeResources/values/strings_rb35.xml`
- `native/ui/words/src/commonMain/composeResources/values-ar/strings_rb35.xml`
- `docs/design/native-ui-rebuild/waivers/35.tsv`
- `docs/design/native-ui-rebuild/evidence/35.md`

## 5. Gate and done

1. `cd ~/wt/rb-35 && native/tool/design/gate_order.sh 35` exits 0. It runs the static checks, fetches the Flutter goldens, runs `--tests '*.rb.o35.*'` in the manifest modules, and compares every golden on its bounding box (standard §9).
2. `docs/design/native-ui-rebuild/evidence/35.md` holds the gate summary (pasted), every waiver, and the folded gaps.
3. Every part (and screen) of this order is `gate-pass` in the tracker, then you post `done` (standard §11).
4. Your commits follow standard §10; you never push.

Done means all four. Anything else is `blocked` or `failed` with one line saying why.
