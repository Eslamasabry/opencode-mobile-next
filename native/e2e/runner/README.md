# Flow runner (S0-FLOWS)

Runs the parity flows in `native/e2e/flows/` on **one PC emulator** against the Flutter reference app or the
native app. Python 3 standard library only; always run with `python3 -I`.

## Tools

| File | What it does |
|---|---|
| `run.py` | the runner (below) |
| `hooks.py` | the allowlist of `[adb]` hooks a flow may call |
| `upgrade.py` | the upgrade gate behind `native/tool/qa/upgrade_e2e.sh` (E00-S09): Flutter APK, seed, `adb install -r` native, Part C, evidence record |
| `device_state.py` | reads and compares the app's OS-held state (grants, channels, shortcuts, widgets, tiles, alarms, rootfs inodes, voice files) |
| `gen_strings.py` | ARB + Android `strings.xml` + `flows/_strings/extra*.json` → `out/strings.js` (`output.s/c/re/rc/t/fx`) |
| `lint_flows.py` | plan 14 §4.2 rules + catalog rows; `--report` lists fixtures, fx actions, hooks and string keys used |
| `find_key.py` | finds the string key for a visible text |
| `catalog.py` | reads `e2e-flows.md` (39 flows) and `behaviors.jsonl` (240 `portable_e2e` rows) |
| `fixture-defaults.json` | fixture facts (`${output.fx.*}`); `native/e2e/fixtures/fixture-data.json` overrides it |
| `maestro.version` | pinned Maestro CLI and the SHA-256 of its zip |
| `tests/` | unit tests: `cd native/e2e && python3 -I -m unittest discover -s runner/tests -t .` |

## Commands

```bash
# No device: lint every flow and run `maestro check-syntax` on each file
python3 -I native/e2e/runner/run.py --check-syntax --maestro /path/to/maestro

# What a run would do (no device touched)
python3 -I native/e2e/runner/run.py --app flutter --group G2 --dry-run

# Prove a group on Flutter R (plan 14 §4.7), on the default emulator-5584
python3 -I native/e2e/runner/run.py --app flutter --apk R-4600.apk --expect-avd OCN_A --group G2

# The same files against the native release build
python3 -I native/e2e/runner/run.py --app native --apk app-stable-release.apk --expect-avd OCN_A \
    --expect-signer 1DE5BF08146F269BCD9EB5C2FFC94469CE4617D37806285955F978A62494D60C --flows E2E-01,E2E-05

# The upgrade gate (E00-S09): one command, headless, exit 0 only when every assertion passed
native/tool/qa/upgrade_e2e.sh --flutter-apk R-62.apk --native-apk native-switch.apk \
    --expect-signer 1DE5BF08146F269BCD9EB5C2FFC94469CE4617D37806285955F978A62494D60C --avd OCN_A

# Arabic repeat (AVD-AR)
python3 -I native/e2e/runner/run.py --app flutter --flows E2E-22,E2E-23 --lang ar
```

## What the runner guarantees

1. **One device, verified.** `--serial` defaults to `emulator-5584` and must match `emulator-<port>`. Before any
   change it checks that `adb devices` lists it as `device`, that `ro.kernel.qemu=1`, and with `--expect-avd` that
   `adb emu avd name` reports that AVD. Every adb call carries `-s <serial>`, Maestro gets `--device <serial>`, and
   a failed adb command stops the run (audit codex-04 #14). The owner's phone is never a target.
2. **Lock and resources.** It takes the emulator lock (`--lock`, default `/home/eslam/Storage/tmp/oc-emulator.lock`,
   or `OC_EMULATOR_LOCK`) and refuses under 7 GB available RAM (`--skip-resource-checks` to override on a
   bigger box).
3. **Install.** `adb install -r` only (never `-d`, never uninstall); `--expect-signer` checks the certificate
   with `apksigner` first.
4. **Fixtures.** Starts the fixtures named in the selected flows' headers through
   `native/e2e/fixtures/fx.py start --ids … --state FILE` (S0-FX contract) and stops them by exact PID; or uses
   `--fixtures-env FILE` (JSON env: `SERVER_PASSWORD`, `SERVER_B_PASSWORD`, `FX_CTL`, …) for fixtures already
   running. Passwords are passed to Maestro with `-e` and scrubbed from every output.
5. **Hooks.** `[adb]` steps call `flows/_hooks/adb.js`, which POSTs to a hook server the runner binds on
   `127.0.0.1:<random>` with a per-run token. Only `hooks.py` names run, with validated arguments, on the verified
   serial. Results are small JSON objects, never device text.
6. **Per flow.** Loads the header's snapshot (`<app>-<snapshot>`, skipped for `base`), clears logcat, runs
   `maestro --device <serial> test --format junit …`, saves logcat, retries once (unless `--no-retry`), and
   classifies `PASSED` / `FAILED` / `FLAKY`.
7. **Outputs.** `out/<app>-<stamp>/flows.json`, per-flow `report.xml`, `maestro.log`, `logcat.txt`, and
   `summary.txt`. A canary secret or a per-run password in logcat fails the run.

Exit codes: `0` all passed, `1` a flow failed or a secret leaked, `2` could not run.

## Upgrade gate (E00-S09)

`native/tool/qa/upgrade_e2e.sh` (`upgrade.py`) wraps `run.py --flows E2E-39`:

1. Refuses (exit 2) unless both APKs are signed by `--expect-signer` only, share the package and the native
   versionCode is higher (`aapt2`, `apksigner` from PATH or `$ANDROID_HOME/build-tools`).
2. Holds the emulator lock for the whole run and hands its descriptor to `run.py` (`OC_EMULATOR_LOCK_FD`), boots
   `--avd` on port 5584 headless: from snapshot `flutter-2.1.4-seeded` when the AVD has it **and** its fixture state
   is under `--seed-home` (default `~/.cache/oc-e2e/seeds/<snapshot>/fx`, with that snapshot's fixture passwords in
   `secrets.env`, mode 0600, never in the repository), else wiped. `adb root` happens before Maestro connects.
3. Fresh: Part A seeds the Flutter app and the `install-native` hook saves the snapshot right before the install.
   Restored: `--seeded` makes the flow skip Part A. Either way `install-native` writes `state/before.json`
   and the `upgrade-capture` hook writes `state/after-migration.json` after the first native launch.
4. Verdicts: Part C's labelled steps (`S4`…`S12`, `UC-*`) from Maestro's `commands-*.json`, and the host-side
   comparison of the two state files. One `PASS`/`FAIL` line each; `VERDICT: PASS|FAIL` last.
5. The evidence record goes to `docs/qa/native-switch-<date>/README.md` (a `-2` suffix rather than overwrite) with
   `screens/native-<screen>.png` from Part C's screenshots.

Exit: 0 every assertion passed, 1 one failed, 2 could not run.
