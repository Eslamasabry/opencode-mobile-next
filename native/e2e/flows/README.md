# Parity device flows (S0-FLOWS)

The 39 cross-app flows of `docs/design/native-rewrite-2026-11-01/catalog/e2e-flows.md`, as Maestro 2.12.0 YAML.
The **same file** runs against the Flutter reference app and, later, the native app (plan 14 §4). Selectors are
visible text and accessibility labels only, read from the frozen strings both apps share.

Run them with `native/e2e/runner/run.py` (see `native/e2e/runner/README.md`).

## Layout

```
flows/
├── <area>/E2E-NN-<slug>.yaml   one main file per flow (area: connect, chat, surfaces, nav, privacy, project,
│                               linux, agents, upgrade)
├── _sub/*.yaml                 shared subflows (start-clean, connect-fx-oc2, connect-server, add-second-server,
│                               open-settings, settings-search, new-conversation, send-prompt, restart-app,
│                               open-shade, close-shade)
├── _hooks/adb.js               [adb] steps: one allowlisted host hook (runner/hooks.py) via the runner's hook server
├── _fx/fx.js                   [fx] steps: one fixture control call (POST {FX_CTL}/fx {target, action, args})
└── _strings/extra*.<lang>.json labels the Flutter app hard-codes in Dart (not in the ARB), with the Dart path
```

| Area | Flows |
|---|---|
| connect | E2E-01, 02, 03, 04, 35 |
| chat | E2E-05, 06, 10, 11, 12, 13, 37 |
| surfaces | E2E-07, 08, 09, 25, 26, 27 |
| nav | E2E-21, 22, 23, 24, 36, 38 |
| privacy | E2E-34 |
| project | E2E-14, 15, 16, 20 |
| linux | E2E-17, 18, 19, 28, 31, 32 |
| agents | E2E-29, 30, 33 |
| upgrade | E2E-39 (+ `upgrade/_parts/partA-seed.yaml`, `partC-assert.yaml`) |

## File rules (checked by `runner/lint_flows.py`)

```yaml
# E2E-NN <title exactly as in e2e-flows.md>
# slice: 2   avd: A   fixtures: FX-OC2, fx-proxy   snapshot: base
# rows: B-…, B-…                      (exactly the flow's "Proves" list)
# KNOWN-FLUTTER-GAPS: none known (not yet run on Flutter R)
appId: ${APP_ID}
tags: [parity, slice-2, g2]
---
- runScript: ../../runner/out/strings.js      # always the first command
# S1 <step text from e2e-flows.md, as a comment>
# S1 [B-…]
- tapOn:
    text: ${output.s.someKey}
    label: "S1 <short words> [B-…]"           # the FIRST command after '# S<n> [...]' carries label "S<n> …"
```

1. Header: `slice`, `avd` (A, S, F, T, AR, 36, 26), `fixtures` (FX-* ids, `fx-proxy`, `replay`, or `none`),
   `snapshot` (`base`, `flutter-linux`, `widgets`, `flutter-final-seeded`), `rows`, `KNOWN-FLUTTER-GAPS`.
2. `appId: ${APP_ID}`, never a literal package.
3. Selectors are `${output.<form>.<key>}`:
   - `s` exact text, `c` contains the text (Flutter merges semantics, e.g. "Title\nSubtitle"), `re` pattern with
     `{placeholders}` → `.*` and ICU plural/select → alternation, `rc` contains the pattern, `t` raw text for
     `inputText`. Keys are ARB keys (`lib/l10n/app_en.arb`), `android_<name>` for
     `android/app/src/main/res/values/strings.xml`, and `x_<name>` from `_strings/extra*.en.json`.
   - `${output.fx.<name>}` for fixture facts (`runner/fixture-defaults.json`, overridden by
     `native/e2e/fixtures/fixture-data.json` when S0-FX adds it).
   - Any other literal needs `# literal-ok: <reason>` on the line or the comment line above.
   - `id:` selectors are banned.
   - Find a key: `python3 -I native/e2e/runner/find_key.py "Allow once"`.
4. Every step `S<n>` from the catalog has a `# S<n> [rows]` comment; the first command after it carries
   `label: "S<n> … [rows]"`. Every header row appears in at least one label.
5. No `clearState: true` in a flow whose snapshot is not `base` (built-in Linux lives in app data).
6. Waits are `extendedWaitUntil` ≤ 60 s (≤ 300 s in flows tagged `linux-setup`); there is no sleep.
7. `retry:` only around system UI that is not ours, with a `# retry-reason:` comment right above.
8. `[adb]` steps: a `# adb: <hook>` comment directly above `runScript: {file: ../_hooks/adb.js, env: {HOOK, ARGS}}`.
   Hooks are listed in `runner/hooks.py`; results land in `output.hook` (assert with `assertTrue`).
9. `[fx]` steps: `runScript: {file: ../_fx/fx.js, env: {TARGET, ACTION, ARGS}}`; the answer lands in `output.fxr`.
   `runner/lint_flows.py --report` lists every (target, action) pair the flows use: that list is the control
   contract the fixture lane (S0-FX) implements.
10. Referenced subflows and scripts must exist (`maestro check-syntax` does not check that).

## Clauses a device cannot observe

Catalog rule 4: a step asserts what a person or a log can observe. A clause only a unit test reaches is left as a
`# UNIT-ONLY: <clause>` comment under its step; its row's native unit test proves it. A step that needs a fixture
or a QA hook that does not exist on Flutter R is marked `# SUSPECTED-GAP: <reason>`; it becomes a
`KNOWN-FLUTTER-GAPS` entry (and is wrapped in `runFlow: {when: {true: "${APP == 'native'}"}}`) only after the first
Flutter run shows it fails, with the coordinator's OK (plan 14 §4.7).

## Labels to confirm on the first Flutter run

Steps that name a control by role only use the best ARB key found from the source; those lines carry
`# confirm: <what>` and must be checked against the Flutter R screen on the first run (e2e-flows "Things to confirm").
