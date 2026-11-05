ORDER: 49-agents
ENGINE: codex
DEPENDS: 13,21,23
WRITES: native/feature/agents/src/commonMain/kotlin/**/ui/** native/feature/agents/src/commonMain/kotlin/**/*Screen.kt native/feature/agents/src/commonMain/kotlin/**/*Sheet*.kt native/feature/agents/src/desktopTest/kotlin/**/rb/o49/** native/feature/agents/build.gradle.kts native/feature/agenttools/src/commonMain/kotlin/**/ui/** native/feature/agenttools/src/commonMain/kotlin/**/*Screen.kt native/feature/agenttools/src/commonMain/kotlin/**/*Sheet*.kt native/feature/agenttools/src/desktopTest/kotlin/**/rb/o49/** native/feature/agenttools/build.gradle.kts native/feature/handoff/src/commonMain/kotlin/**/ui/** native/feature/handoff/src/commonMain/kotlin/**/*Screen.kt native/feature/handoff/src/commonMain/kotlin/**/*Sheet*.kt native/feature/handoff/src/desktopTest/kotlin/**/rb/o49/** native/feature/handoff/build.gradle.kts native/feature/codexprobe/src/commonMain/kotlin/**/ui/** native/feature/codexprobe/src/commonMain/kotlin/**/*Screen.kt native/feature/codexprobe/src/commonMain/kotlin/**/*Sheet*.kt native/feature/codexprobe/src/desktopTest/kotlin/**/rb/o49/** native/feature/codexprobe/build.gradle.kts native/feature/computer/src/commonMain/kotlin/**/ui/** native/feature/computer/src/commonMain/kotlin/**/*Screen.kt native/feature/computer/src/commonMain/kotlin/**/*Sheet*.kt native/feature/computer/src/desktopTest/kotlin/**/rb/o49/** native/feature/computer/build.gradle.kts native/ui/words/src/commonMain/composeResources/values/strings_rb49.xml native/ui/words/src/commonMain/composeResources/values-ar/strings_rb49.xml docs/design/native-ui-rebuild/waivers/49.tsv docs/design/native-ui-rebuild/evidence/49.md
GATE: native/tool/design/gate_order.sh 49
TIMEOUT_MIN: 360
---
# Order 49-agents: screens

This order is binding together with `docs/design/native-ui-rebuild/GOLDEN-STANDARD.md`. Read the whole standard before you touch a file. Where this order and the standard disagree, stop and mark the order blocked (standard §13).

## 1. Identity

- Engine: Codex gpt-6.1-sol (`codex`). Time limit: 360 minutes.
- Worktree (created by the lead; never create one): `eslam@169.58.71.211:~/wt/rb-49`, branch `native/rb-49-agents`.
- Flutter source of truth: `~/wt/flutter-ref` at FLUTTER_REF `8302de53773adef45eaaad016df2bd3cd51d1ebe`.
- Gate manifest: `docs/design/native-ui-rebuild/orders/49.gate.tsv` (104 goldens). Gate command: `native/tool/design/gate_order.sh 49`.
- Depends on: order 13, order 21, order 23. Do not start until each is merged into `native/looks-pass-2026-11-04` (the lead dispatches only then).
- Report as agent name `rb-49-codex` (standard §11).

## 2. Screens

Native modules: `native/feature/agents`, `native/feature/agenttools`, `native/feature/handoff`, `native/feature/codexprobe`, `native/feature/computer`. You change Compose files only (files under a `ui` package, `*Screen.kt`, `*Sheet*.kt`). ViewModels, `*Ui` state classes, `*Entries.kt` and engines are read-only: if the Flutter screen needs data the `XUi` state does not carry, mark the screen blocked with the missing field (standard §13), never invent it.

Screens to rebuild, from the screen inventory (`docs/design/native-rewrite-2026-11-01/plan/07-screens-navigation.md` §5, rows by id):

| Tracker id | Inventory row | Flutter | Kind | Native home |
|---|---|---|---|---|
| `screen-G01` | G01 | `AgentsHubScreen` + `AgentsScreen` + `AgentsSection` (+ F18) | page | **merged** (owner choice O-07-5, default merge): `AgentsRoute.Hub` |
| `screen-G02` | G02 | `showAgentSheet` (steps list / setup / check / signIn) + `AgentPhoneCheckView` | sheet | `SheetKey.Agent(agentId, step, signInAgain)` |
| `screen-G03` | G03 | `AgentSignInTerminalScreen` | page | `AgentsRoute.SignInTerminal(agentId)` |
| `screen-G04` | G04 | `AgentAccountScreen` / `AgentAccountPanel` | page | `AgentsRoute.Account(profileId)` |
| `screen-G05` | G05 | `AgentStatusNotices`, `CardsFromAgentsRow` | part | parts of B01 and G01 |
| `screen-G06` | G06 | `ExternalAgentsScreen`, `ExternalAgentDetailScreen`, `ExternalTaskScreen` / `openExternalAgents` | page | **dropped** (ADR D6 default: A2A dropped) |

Screen goldens (Flutter screen galleries; each is a state of one of the screens above): `test/goldens/agents_*`, `test/goldens/agent_*`, `test/goldens/providers_*`, `test/goldens/plugins_*`, `test/goldens/picker_*`, `test/revamp/goldens/agent_*` — 104 PNGs, listed in the manifest. Their scene inputs are in the golden test that writes them (`grep -rl "<golden stem>" ~/wt/flutter-ref/test`).

## 3. Write set

You may create, change or delete only paths matching these globs (the `WRITES:` header). `gate_order.sh` fails on any other path.

- `native/feature/agents/src/commonMain/kotlin/**/ui/**`
- `native/feature/agents/src/commonMain/kotlin/**/*Screen.kt`
- `native/feature/agents/src/commonMain/kotlin/**/*Sheet*.kt`
- `native/feature/agents/src/desktopTest/kotlin/**/rb/o49/**`
- `native/feature/agents/build.gradle.kts`
- `native/feature/agenttools/src/commonMain/kotlin/**/ui/**`
- `native/feature/agenttools/src/commonMain/kotlin/**/*Screen.kt`
- `native/feature/agenttools/src/commonMain/kotlin/**/*Sheet*.kt`
- `native/feature/agenttools/src/desktopTest/kotlin/**/rb/o49/**`
- `native/feature/agenttools/build.gradle.kts`
- `native/feature/handoff/src/commonMain/kotlin/**/ui/**`
- `native/feature/handoff/src/commonMain/kotlin/**/*Screen.kt`
- `native/feature/handoff/src/commonMain/kotlin/**/*Sheet*.kt`
- `native/feature/handoff/src/desktopTest/kotlin/**/rb/o49/**`
- `native/feature/handoff/build.gradle.kts`
- `native/feature/codexprobe/src/commonMain/kotlin/**/ui/**`
- `native/feature/codexprobe/src/commonMain/kotlin/**/*Screen.kt`
- `native/feature/codexprobe/src/commonMain/kotlin/**/*Sheet*.kt`
- `native/feature/codexprobe/src/desktopTest/kotlin/**/rb/o49/**`
- `native/feature/codexprobe/build.gradle.kts`
- `native/feature/computer/src/commonMain/kotlin/**/ui/**`
- `native/feature/computer/src/commonMain/kotlin/**/*Screen.kt`
- `native/feature/computer/src/commonMain/kotlin/**/*Sheet*.kt`
- `native/feature/computer/src/desktopTest/kotlin/**/rb/o49/**`
- `native/feature/computer/build.gradle.kts`
- `native/ui/words/src/commonMain/composeResources/values/strings_rb49.xml`
- `native/ui/words/src/commonMain/composeResources/values-ar/strings_rb49.xml`
- `docs/design/native-ui-rebuild/waivers/49.tsv`
- `docs/design/native-ui-rebuild/evidence/49.md`

## 4. Gate and done

1. `cd ~/wt/rb-49 && native/tool/design/gate_order.sh 49` exits 0. It runs the static checks, fetches the Flutter goldens, runs `--tests '*.rb.o49.*'` in the manifest modules, and compares every golden on its bounding box (standard §9).
2. `docs/design/native-ui-rebuild/evidence/49.md` holds the gate summary (pasted), every waiver, and the folded gaps.
3. Every part (and screen) of this order is `gate-pass` in the tracker, then you post `done` (standard §11).
4. Your commits follow standard §10; you never push.

Done means all four. Anything else is `blocked` or `failed` with one line saying why.
