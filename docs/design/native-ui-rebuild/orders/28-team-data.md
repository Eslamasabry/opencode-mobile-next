ORDER: 28-team-data
ENGINE: commandcode
DEPENDS: 01
WRITES: native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/team/KitTeamData.kt native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/team/TeamParts.kt native/ui/kit/src/desktopTest/kotlin/**/rb/o28/** native/ui/words/src/commonMain/composeResources/values/strings_rb28.xml native/ui/words/src/commonMain/composeResources/values-ar/strings_rb28.xml docs/design/native-ui-rebuild/waivers/28.tsv docs/design/native-ui-rebuild/evidence/28.md
GATE: native/tool/design/gate_order.sh 28
TIMEOUT_MIN: 240
---
# Order 28-team-data: agents and team

This order is binding together with `docs/design/native-ui-rebuild/GOLDEN-STANDARD.md`. Read the whole standard before you touch a file. Where this order and the standard disagree, stop and mark the order blocked (standard §13).

## 1. Identity

- Engine: Command Code (GLM) (`commandcode`). Time limit: 240 minutes.
- Worktree (created by the lead; never create one): `eslam@169.58.71.211:~/wt/rb-28`, branch `native/rb-28-team-data`.
- Flutter source of truth: `~/wt/flutter-ref` at FLUTTER_REF `8302de53773adef45eaaad016df2bd3cd51d1ebe`.
- Gate manifest: `docs/design/native-ui-rebuild/orders/28.gate.tsv` (0 goldens). Gate command: `native/tool/design/gate_order.sh 28`.
- Depends on: order 13. Do not start until each is merged into `native/looks-pass-2026-11-04` (the lead dispatches only then).
- Report as agent name `rb-28-commandcode` (standard §11).

## 2. Parts, in the order you port them

| # | Tracker id | Flutter file | Golden test | Goldens | Tier | Native files you own (NEW = you create it) | Capacity classes (role) |
|---|---|---|---|---|---|---|---|
| 1 | `part-kit_team_data` | `lib/ui/kit/team/kit_team_data.dart` (`~/wt/flutter-ref/lib/ui/kit/team/kit_team_data.dart`) | – | 0 | T2 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/team/KitTeamData.kt` | – |
| 2 | `part-team_parts` | `lib/ui/kit/team/team_parts.dart` (`~/wt/flutter-ref/lib/ui/kit/team/team_parts.dart`) | – | 0 | T2 | NEW `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/team/TeamParts.kt` | – |

Logic-only parts (no widget, no golden): each needs `rb/o28/<Pascal>ParityTest.kt` asserting every constant and rule of the Dart file (standard §6 step 8): `kit_team_data`, `team_parts`.

## 3. Write set

You may create, change or delete only paths matching these globs (the `WRITES:` header). `gate_order.sh` fails on any other path.

- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/team/KitTeamData.kt`
- `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/team/TeamParts.kt`
- `native/ui/kit/src/desktopTest/kotlin/**/rb/o28/**`
- `native/ui/words/src/commonMain/composeResources/values/strings_rb28.xml`
- `native/ui/words/src/commonMain/composeResources/values-ar/strings_rb28.xml`
- `docs/design/native-ui-rebuild/waivers/28.tsv`
- `docs/design/native-ui-rebuild/evidence/28.md`

## 4. Gate and done

1. `cd ~/wt/rb-28 && native/tool/design/gate_order.sh 28` exits 0. It runs the static checks, fetches the Flutter goldens, runs `--tests '*.rb.o28.*'` in the manifest modules, and compares every golden on its bounding box (standard §9).
2. `docs/design/native-ui-rebuild/evidence/28.md` holds the gate summary (pasted), every waiver, and the folded gaps.
3. Every part (and screen) of this order is `gate-pass` in the tracker, then you post `done` (standard §11).
4. Your commits follow standard §10; you never push.

Done means all four. Anything else is `blocked` or `failed` with one line saying why.
