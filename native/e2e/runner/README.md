# Flow runner (S0-FLOWS)

Runs the parity flows in `native/e2e/flows/` on **one PC emulator** against the Flutter reference app or the
native app. Python 3 standard library only; always run with `python3 -I`.

## Tools

| File | What it does |
|---|---|
| `run.py` | the runner (below) |
| `hooks.py` | the allowlist of `[adb]` hooks a flow may call |
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
