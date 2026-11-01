# S0-FLOWS: the 39 parity device flows and their runner

Date: 2026-11-01. Branch: `native/flows-2026-11-01` (from `claude/native-test-flows-omhi3u` at `6627d320`).
Write set: `native/e2e/flows/**`, `native/e2e/runner/**` (+ this report).
Inputs: plan 14 §4 (device flows), `catalog/e2e-flows.md` (39 flows), plan 15 row S0-FLOWS, audit codex-04 #14.

**Shipping state:**
- **Done:** all 39 flows are written as Maestro 2.12.0 YAML. They are linted against the catalog, syntax-checked with
  `maestro check-syntax`, committed and pushed.
- **Not done:** none of them has run on a device. This container has no emulator, so not one flow has been proven
  on Flutter R. The finish line of the plan 15 row ("slice-1/2 flows green on Flutter R by end of W2") is still
  open and is the T3 holder's job.
- No PR, CI run, signing or release.

## 1. What was built

### 1.1 Flows (`native/e2e/flows/`)

| Path | Content |
|---|---|
| `<area>/E2E-NN-<slug>.yaml` | 39 main flows. Each header has the title, slice, AVD roles, fixtures, snapshot, `rows` (exactly the catalog's "Proves") and `KNOWN-FLUTTER-GAPS`. `appId: ${APP_ID}`. Every catalog step is copied as a `# S<n>` comment, and the step's first command carries `label: "S<n> … [rows]"`. |
| `upgrade/_parts/partA-seed.yaml`, `partC-assert.yaml` | The E2E-39 seed and assert parts. They are written once and run on both apps (Part B = Part C on Flutter). |
| `_sub/*.yaml` | 11 shared subflows: start-clean, connect-server, connect-fx-oc2, add-second-server, open-settings, settings-search, new-conversation, send-prompt, restart-app, open-shade, close-shade. |
| `_hooks/adb.js` | Runs an `[adb]` step through the runner's hook server; the result lands in `output.hook`. |
| `_fx/fx.js` | Runs an `[fx]` step as `POST {FX_CTL}/fx {target, action, args}`; the result lands in `output.fxr`. |
| `_strings/extra.en.json`, `extra-b.en.json` (+ `extra.ar.json`) | Labels that are not in the ARB, each with its source path. 1 + 19 keys. |
| `README.md` | The file rules, the area map, and the markers (UNIT-ONLY, SUSPECTED-GAP, confirm, NEEDS-HOOK). |

Selectors are only visible text and accessibility labels, taken from the strings both apps share:
`${output.s|c|re|rc|t.<key>}`. The flows use 787 distinct keys: ARB keys, `android_<name>` from
`res/values/strings.xml`, and `x_<name>` from the extra files. Fixture facts come from `${output.fx.<name>}`. No
flow uses an `id:` selector, and no flow names a literal package.

### 1.2 Runner (`native/e2e/runner/`)

| File | Content |
|---|---|
| `run.py` | The runner. It drives one PC emulator (`--serial`, default `emulator-5584`) against `--app flutter\|native`. Details are in §4. |
| `hooks.py` | The allowlist of 24 `[adb]` hooks, each with validated arguments. Five are marked `verified=False` because their recipe is [U]: qs-tile, locale, camera-scene, mic-feed, and the poster/WAV paths. |
| `gen_strings.py` | Builds `out/strings.js` from the ARB, Android `strings.xml` and the extra files. Forms: `t` raw, `s` exact regex, `c` contains, `re` placeholders and ICU → regex, `rc` contains-pattern. Arabic is limited to English's keys and falls back to English. |
| `lint_flows.py` | Plan 14 §4.2 rules 1–8, plus: referenced files exist, string and fixture keys exist, the union of all flows' rows is the 240 `portable_e2e` rows, and `--report` lists the fixtures, fx actions, hooks and keys the flows use. |
| `catalog.py`, `find_key.py`, `fixture-defaults.json`, `maestro.version`, `README.md`, `tests/` | Catalog reader, key finder, default fixture facts (no secrets), Maestro version pin (2.12.0, zip SHA-256 `a6044a84…3ec5e`), docs, and 35 unit tests. |

## 2. Catalog rows covered

All 240 `portable_e2e` rows of `behaviors.jsonl`, through 39 flows. The lint checks that each flow's header rows
equal the catalog's "Proves" list for that flow, and that every header row is named by at least one step label.

By area: AGENT 24, ANDR 32, BUILD 1, CHAT 17, KIT 2, L10N 4, LINUX 43, MEDIA 6, MIGR 10, NAV 53, PROTO 13, SEC 18,
STATE 17.

"Covered" means "a flow step names the row". It does not mean "proven": nothing has run yet.

The unit tests are named after what they prove (the lint and runner rules). The flows are the tests for the catalog
rows.

## 3. Flows, fixtures and snapshots

Counts per flow:
- **SG:** steps marked `SUSPECTED-GAP` (likely to fail on Flutter R).
- **UO:** clauses marked `UNIT-ONLY` (left to the row's unit test, catalog rule 4).
- **cf:** labels marked `# confirm` (to check on the first Flutter run).
- **NH:** steps marked `NEEDS-HOOK`.

| Flow | File | Slice | AVD | Snapshot | Fixtures | Rows | SG | UO | cf | NH |
|---|---|---|---|---|---|---|---|---|---|---|
| E2E-01 | connect/E2E-01-first-launch-typed-address.yaml | 2 | A, 26 | base | FX-OC2 | 4 | 0 | 0 | 0 | 0 |
| E2E-02 | connect/E2E-02-pairing-code-qr.yaml | 3 | A | base | FX-OC2, FX-TLS | 5 | 0 | 3 | 3 | 0 |
| E2E-03 | connect/E2E-03-connection-failures-plain.yaml | 2 | A | base | FX-OC2, FX-TLS, FX-CODEX, replay | 6 | 2 | 2 | 10 | 0 |
| E2E-04 | connect/E2E-04-transport-trust.yaml | 2 | A | base | FX-OC2, FX-TLS | 4 | 1 | 3 | 5 | 0 |
| E2E-05 | chat/E2E-05-stream-stop-search-links.yaml | 2 | A, 26 | base | FX-OC2, fx-proxy | 9 | 1 | 7 | 6 | 0 |
| E2E-06 | chat/E2E-06-approve-deny-always.yaml | 3 | A | base | FX-OC2 | 6 | 2 | 3 | 6 | 0 |
| E2E-07 | surfaces/E2E-07-answer-from-shade.yaml | 4 | A | base | FX-OC2 | 8 | 2 | 0 | 2 | 1 |
| E2E-08 | surfaces/E2E-08-needs-you-finished-status.yaml | 4 | A | base | FX-OC2, FX-OC2-B | 10 | 1 | 3 | 2 | 0 |
| E2E-09 | surfaces/E2E-09-keep-running-background.yaml | 4 | A | base | FX-OC2 | 2 | 0 | 2 | 1 | 0 |
| E2E-10 | chat/E2E-10-network-loss-never-twice.yaml | 2 | A | base | FX-OC2, fx-proxy | 6 | 1 | 4 | 6 | 0 |
| E2E-11 | chat/E2E-11-drafts-saved-queued.yaml | 3 | A | base | FX-OC2, FX-OC2-B | 8 | 2 | 5 | 3 | 0 |
| E2E-12 | chat/E2E-12-attach-photos-files-mentions.yaml | 5 | A | base | FX-OC2 | 4 | 2 | 2 | 4 | 0 |
| E2E-13 | chat/E2E-13-voice-dictation-autosend.yaml | 5 | A | base | FX-OC2, fx-proxy | 3 | 1 | 2 | 3 | 0 |
| E2E-14 | project/E2E-14-files-share-export.yaml | 5 | A | flutter-linux | FX-OC2 | 4 | 1 | 5 | 4 | 0 |
| E2E-15 | project/E2E-15-git-github-remotes-undo.yaml | 5 | A | flutter-linux | FX-OC2, FX-GH, FX-GIT | 7 | 1 | 5 | 3 | 0 |
| E2E-16 | project/E2E-16-terminal-shells-outlive.yaml | 5 | A | flutter-linux | FX-OC2 | 2 | 0 | 2 | 3 | 0 |
| E2E-17 | linux/E2E-17-linux-setup-preflight-resume.yaml | 3 | A, S, F | base | FX-LINUXDL, fx-proxy | 17 | 2 | 12 | 3 | 0 |
| E2E-18 | linux/E2E-18-phone-server-recovery-status.yaml | 3 | A | flutter-linux | FX-OC2, FX-OC2-B | 12 | 0 | 9 | 1 | 2 |
| E2E-19 | linux/E2E-19-remove-linux-free-space-reset.yaml | 3 | A | flutter-linux | FX-OC2, FX-LINUXDL | 4 | 0 | 3 | 3 | 0 |
| E2E-20 | project/E2E-20-dev-servers-ports-processes.yaml | 5 | A | flutter-linux | FX-OC2 | 8 | 0 | 5 | 3 | 0 |
| E2E-21 | nav/E2E-21-conversations-list.yaml | 2 | A | base | FX-OC2, FX-OC2-B | 10 | 2 | 6 | 4 | 0 |
| E2E-22 | nav/E2E-22-find-any-setting.yaml | 4 | A, T, AR | base | FX-OC2, FX-OC2-B | 11 | 1 | 6 | 2 | 0 |
| E2E-23 | nav/E2E-23-language-text-size-look.yaml | 4 | A, AR | widgets | FX-OC2 | 6 | 0 | 3 | 1 | 0 |
| E2E-24 | nav/E2E-24-command-launcher.yaml | 5 | A, T | base | FX-OC2 | 3 | 0 | 1 | 2 | 1 |
| E2E-25 | surfaces/E2E-25-shortcuts-tile-links-share.yaml | 4 | A | base | FX-OC2, FX-OC2-B | 10 | 2 | 6 | 2 | 1 |
| E2E-26 | surfaces/E2E-26-home-screen-widgets.yaml | 4 | A | widgets | FX-OC2, FX-OC2-B | 7 | 0 | 4 | 4 | 0 |
| E2E-27 | surfaces/E2E-27-scheduled-prompts.yaml | 4 | A | base | FX-OC2 | 4 | 1 | 3 | 2 | 0 |
| E2E-28 | linux/E2E-28-ai-team-on-phone.yaml | after-switch | A | flutter-linux | FX-OC2, FX-LINUXDL | 4 | 0 | 3 | 2 | 0 |
| E2E-29 | agents/E2E-29-codex-connect-steer-abort.yaml | 5 | A | base | FX-CODEX, FX-OC2 | 5 | 1 | 3 | 3 | 0 |
| E2E-30 | agents/E2E-30-claude-pi-through-paseo.yaml | 3 | A | base | FX-PASEO, FX-OC2 | 7 | 0 | 2 | 3 | 0 |
| E2E-31 | linux/E2E-31-phone-agents-signin-terminal.yaml | 3 | A | flutter-linux | FX-OC2, FX-LINUXDL, FX-AUTH | 3 | 1 | 2 | 2 | 0 |
| E2E-32 | linux/E2E-32-memory-on-phone.yaml | after-switch | A | flutter-linux | FX-OC2, FX-NODE | 9 | 0 | 6 | 2 | 0 |
| E2E-33 | agents/E2E-33-plan-usage-subscriptions.yaml | 5 | A | base | FX-OC2, FX-OC2-B, FX-USAGE | 2 | 1 | 2 | 2 | 0 |
| E2E-34 | privacy/E2E-34-privacy-diagnostics-reports.yaml | 4 | A | base | FX-OC2 | 7 | 1 | 4 | 2 | 1 |
| E2E-35 | connect/E2E-35-remove-server-everything.yaml | 2 | A | flutter-linux | FX-OC2, FX-OC2-B | 2 | 0 | 2 | 3 | 0 |
| E2E-36 | nav/E2E-36-several-servers-needs-me.yaml | 4 | A | base | FX-OC2, FX-OC2-B | 5 | 0 | 3 | 2 | 0 |
| E2E-37 | chat/E2E-37-providers-models.yaml | 5 | A | base | FX-OC2 | 5 | 1 | 3 | 2 | 0 |
| E2E-38 | nav/E2E-38-tips-and-tour.yaml | 4 | A | base | FX-OC2 | 3 | 0 | 2 | 1 | 0 |
| E2E-39 | upgrade/E2E-39-upgrade-in-place.yaml | 6 | A | base | FX-OC2, FX-OC2-B, fx-proxy, FX-TLS, FX-LINUXDL, FX-GH | 11 | 1 | 6 | 1 | 0 |

### Which flows need which fixture

| Fixture | Flows |
|---|---|
| FX-OC2 | every flow except E2E-17 |
| FX-OC2-B | E2E-08, 11, 18, 21, 22, 25, 26, 33, 35, 36, 39 |
| fx-proxy | E2E-05, 10, 13, 17, 39 |
| FX-TLS | E2E-02, 03, 04, 39 |
| replay (wire replay server) | E2E-03 |
| FX-LINUXDL | E2E-17, 19, 28, 31, 39 |
| FX-CODEX | E2E-03, 29 |
| FX-PASEO | E2E-30 |
| FX-GH | E2E-15, 39 |
| FX-GIT | E2E-15 |
| FX-AUTH | E2E-31 |
| FX-NODE | E2E-32 |
| FX-USAGE | E2E-33 |

### Snapshots

The runner loads AVD snapshot `<app>-<snapshot>` when a flow's header names one other than `base`:

| Snapshot | Flows | What it holds |
|---|---|---|
| `flutter-linux` / `native-linux` | E2E-14, 15, 16, 18, 19, 20, 28, 31, 32, 35 | built-in Linux already set up |
| `widgets` | E2E-23, 26 | Conversations, Status and Usage widgets placed by hand once (adb cannot place widgets) |

E2E-39 starts from `base`, because Part A is the seed and must itself be proven on Flutter.

## 4. Runner safety (audit codex-04 #14 applied)

- **One serial, verified first.** `--serial` must match `emulator-<port>` (default `emulator-5584`). Before any
  change the runner checks that `adb devices` lists it as `device`, that `ro.kernel.qemu=1`, and, with
  `--expect-avd`, that `adb emu avd name` matches. Every adb call carries `-s <serial>`, Maestro gets
  `--device <serial>`, and a failed adb command stops the run.
- **Lock and resources.** It holds the emulator lock (an fcntl flock on `--lock`), refuses to run with under
  7 GB of free RAM, and installs only with `install -r` (never `-d`, never an uninstall). `--expect-signer` checks
  the signer with apksigner.
- **Hooks.** The hook server binds 127.0.0.1 on a random port, needs the per-run token, runs only `hooks.py`
  entries with validated arguments, and answers with small JSON (counts and booleans, never device text).
- **Fixtures.** It stops fixtures by exact PID. It scrubs passwords, the hook token and the canary secrets from every
  output. A canary or password found in logcat fails the run.

## 5. Commands and results (this container)

```
cd native/e2e && python3 -I -m unittest discover -s runner/tests -t .   → Ran 35 tests … OK
python3 -I native/e2e/runner/lint_flows.py                              → 39 flows, 240 rows, 0 errors
python3 -I native/e2e/runner/run.py --check-syntax --maestro /opt/maestro-x/maestro/bin/maestro
                                                                         → 52 files checked, 0 problems
  (with a planted bad file: the bad file is reported and the command exits 1)
python3 -I native/e2e/runner/run.py --app flutter --group G2 --dry-run  → plan printed, no device touched
git grep for private IPs / emails in native/e2e                          → none (only 10.0.2.2 and the documented
                                                                            test addresses 192.0.2.1, 100.100.1.1)
```

Resolved versions:
- **Maestro CLI 2.12.0**, from the GitHub release `cli-2.12.0`. Zip SHA-256
  `a6044a84542f522f434c5dc3d609e60c7446d423f3ed1b2ae513f5c48663ec5e`. Its `check-syntax` rejects unknown commands
  and properties, but does not check that a referenced file exists (the lint does).
- Python 3.13, standard library only.
- JDK 21.

The Android SDK was not installed: this unit runs no Gradle and has no emulator.

## 6. Deviations from the plan

1. **Hooks run, they are not comments.** `[adb]` steps call `_hooks/adb.js` → the runner's local hook server,
   instead of plan 14 §4.2 rule 8's comment-only `# adb:` markers. Maestro runs its JS on the host, so `http.post`
   reaches the runner in the middle of a flow. The `# adb: <hook>` comment is kept above each call and the lint
   checks it.
2. **Where the tools live.** Plan 14 §4.1 puts them in `native/tool/e2e/` and `native/e2e/strings/`; here they are
   in `native/e2e/runner/` (the write set):
   - `run.py`, `gen_strings.py` and `lint_flows.py` live there;
   - the strings table is generated at run time into `runner/out/strings.js` (gitignored), so flows load
     `../../runner/out/strings.js`, not `../../build/strings.js`;
   - `maestro.version` is `runner/maestro.version`.
3. **Label-only selectors.** The `s` form is regex-escaped exact text, because Maestro text selectors are regexes.
   `c`/`rc` (contains) were added because Flutter merges semantics labels.
4. **E2E-39 location.** It lives in `flows/upgrade/`, with its parts in `flows/upgrade/_parts/`. `native/e2e/upgrade/`
   belongs to S6-MIGRHARD.
5. **Not written yet:** `slices.json`, `known-gaps.md` and `used-keys.txt` (they sit outside the write set). The
   header fields and `lint_flows.py --report` hold the same data until then. `KNOWN-FLUTTER-GAPS` is "none known
   (not yet run on Flutter R)" everywhere; suspected gaps are `SUSPECTED-GAP` comments until the first run
   confirms them (plan 14 §4.7 step 4).
6. **E2E-01 S4.** It pastes the bare address with Maestro's own `setClipboard` + `pasteText` instead of an adb
   clipboard hook.

## 7. Requests

- **Catalog:** none (no Gradle library is used).
- **Coordinator:**
  1. Add `native/e2e/build/` to `.gitignore` if the plan path is kept (the runner already uses `runner/out/`, which
     is ignored).
  2. Decide whether `slices.json` and `known-gaps.md` move into `native/e2e/flows/`.
- **S0-FX: the fixture control contract.** The runner calls `native/e2e/fixtures/fx.py start --ids … --state FILE`,
  which writes `{"pids": [...], "env": {...}}`, and `fx.py stop --state FILE`. During a flow, `POST {FX_CTL}/fx
  {target, action, args}` → 200 + JSON (`FX_CTL` defaults to `http://127.0.0.1:8898`).
  - **Env names:** SERVER_PASSWORD, SERVER_B_PASSWORD, CODEX_TOKEN, PASEO_PASSWORD, NODE_PAIR_CODE.
  - **Actions the flows use** (from `lint_flows.py --report`; three writers chose them, so merge synonyms such as
    `request-log` / `request-count`, `restore` / `restore-endpoint`, `expire-request` / `expire-pending`):
    - FX-OC2: answer-pending, clear-request-log, delay-endpoint, drop-stream, expire-pending, expire-request,
      hang-replies, hold-response, lose-response, pairing-code, refuse-endpoint, release-response, reorder-events,
      request-count, request-log, restore, restore-endpoint, seed-sessions, set-capabilities, set-permission-mode,
      set-providers, start-server, start-session, stop-server, touch-project, wait-request.
    - FX-OC2-B: alias-origin, refuse-endpoint, request-count, request-log, restore-endpoint, seed-sessions,
      set-capabilities, start-server, stop-server.
    - fx-proxy: clear-request-log, cut-host, cut-mid-request, drop-stream, request-log, restore-host, throttle,
      unthrottle.
    - FX-TLS: clear-request-log, request-log, swap-cert.
    - FX-CODEX: drop-socket, fail-scope, request-log, require-token, restore, script-turn.
    - FX-PASEO: add-history, archive-elsewhere, drop-socket, fail-update, refuse-import, refuse-terminal,
      request-log, restore.
    - FX-GH: approve, deny, publish-release, revoke-token.
    - FX-GIT: reject-push, restore.
    - FX-LINUXDL: corrupt-checksum, cut, request-log, restore, serve-variant, throttle.
    - FX-NODE: request-log, restore, unreachable.
    - FX-USAGE: request-log, set-state.
    - stub-model: set-context-usage.
  - **New stub prompts:** `e2e:ask-read-outside`, `e2e:ask-fetch`, `e2e:ask-write-outside` (E2E-06).
  - **Device files:** `native/e2e/fixtures/device-files/` (photo1..3.jpg, data.xlsx, report.docx, notes.txt, a
    name-only stub), `fixtures/camera/{qr-pairing,qr-other,none}.png` and
    `fixtures/audio/{speech-3min,phrase-6s,speech-3min-broken}.wav`.
  - **Fixture facts** (used as `# literal-ok` literals until `fixture-data.json` exists):
    - Connection and projects: blackholeAddress, projectEmpty, projectNoGit, searchWord, longHistoryNewest/Oldest,
      downloadNameOnly.
    - Voice and models: speechChunk1–3, phraseWords, stubModelId/Name, stubThinkingModelName,
      unloadedProviderName, providerWithAllMethods, choiceOption1–3, choice4Option1.
    - Git and agents: gitBranch, gitRemoteUrl, codexProjectDir, paseoProjectDir, paseoHistoryTitle,
      paseoImportTitles, paseoImportBody, paseoTerminalTitle, paseoTerminalReplay, authCode, nodeThing.
    - Also: FX-OC2-B's session `ses_e2e_shared` must share its id with FX-OC2.
- **New hooks to add once their recipes are chosen** (these steps are written as `# NEEDS-HOOK` today, with the
  visible outcome asserted around them):
  - `parallel-tap` (E2E-07)
  - `launch-extra` (E2E-25)
  - `key-combo` Ctrl+K (E2E-24)
  - `share-unavailable` (E2E-34)
  - `break-phone-server-start` and `phone-server-impostor` (E2E-18)
  - `place-widget` (E2E-39 Part A)
- **l10n owner:** the 20 `x_*` labels in `_strings/extra*.en.json` go into `:ui:words` with the same text. Six come
  from Android resource files `gen_strings` does not read yet (`widget_v2_strings.xml`, `scheduled_prompts.xml`);
  reading all of `res/values/*.xml` would remove them.

## 8. Open problems

1. **Nothing has run.** Every flow is unproven on Flutter R. The T3 holder runs
   `run.py --app flutter --apk R-<N>.apk --expect-avd OCN_A --group G2` first (E2E-01, 03, 04, 05, 10, 21, 35), then
   fixes labels marked `# confirm` (117 markers across the flows) and records real gaps.
2. **Likely Flutter gaps.** There are 32 SUSPECTED-GAP markers. The largest:
   - Conversation rows have no pin action (E2E-21 S5, E2E-25 S1).
   - Notifications have no reply field or option buttons (E2E-07 S3–S4).
   - Lock-screen redaction cannot show without a PIN (E2E-08 S6).
   - GitHub, update-feed, usage and Linux-download hosts are hard-coded on Flutter R (E2E-15 S4, E2E-33 S1,
     E2E-17 S7, E2E-39 S13: these need the `-qa.yaml` companions on native stableQa, none written yet).
   - Plan 14 §4.7 allows at most two gap steps per flow before a redesign. E2E-03, 06, 07, 11, 12, 17, 21 and 25
     sit at two.
3. **Unverified hook recipes [U]:**
   - `locale` restarts zygote, which may drop Maestro's driver mid-flow (E2E-23 S2).
   - `camera-scene` and `mic-feed` depend on emulator console commands not checked on the PC.
   - `qs-tile` assumes the `cmd statusbar *-tile` syntax.
4. **The `widgets` snapshot is manual.** Someone must place the three widgets once per AVD and app.
5. **`flutter-linux` snapshots** need FX-LINUXDL or the real pinned download hosts for the first setup.
6. **E2E-17 runs on three AVDs.** One file serves A, S and F; on AVD S, steps 2–13 are skipped through
   `output.small`, so its row proof comes from the A and F runs.
7. **The 240-row union holds only while the catalog's "Proves" lists stand.** If `e2e-flows.md` changes, the lint
   test fails until the flow headers follow.
