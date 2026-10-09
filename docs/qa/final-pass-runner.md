# Final-pass runner

`tool/qa/final_pass.py` runs the device drivers under one exclusive
`/home/eslam/Storage/tmp/oc-emulator.lock` reservation (up to 3600 seconds to acquire).
The default is an inert plan: no manifest reads, subprocesses, output files or adb.

```bash
python3 tool/qa/final_pass.py --dry-run --fast
python3 tool/qa/final_pass.py --execute --fast \
  --candidate-apk /home/eslam/Storage/tmp/oc-apk-share/oc-2202.apk \
  --candidate-build 2202 --inputs /absolute/private/final-pass-inputs.json \
  --output docs/qa/final-pass-2202
```

Normal restoration defaults to the approved local-signed APK 2202. An alternate
`--normal-apk` must have that build and signer. Candidate and normal identities
are checked before taking the lock. Existing output directories are refused;
`--date` chooses the default `docs/qa/final-pass-<date>` directory. No build,
download, signing or publication is performed by the runner.

## Sequencing

Upgrade runs first, before candidate installation: its driver must observe the
actual reviewed baseline already installed, with its existing history receipt.
The runner does not install an older baseline. The remaining order is BA install,
BA removal, BA storage floor, BB5 idle, FQ3 OC1/OC2, FQ9 background, BD7 saved
report, FB1 fresh-plan check, demo. `--fast` omits background and demo entirely;
it cannot start the 30-minute dwell or GIF recorder.

Before each subsequent device row, the runner installs/reselects the candidate
if needed. Drivers with module-local locks borrow the parent's descriptor and
cannot unlock it. FQ3 validates an inherited descriptor and executes phases in
that process without nested flock acquisition; `--no-matrix` retains receipts
without changing the global certification matrix. Other subprocess drivers
receive the descriptor through `pass_fds` and validate it themselves.

Rows retain their evidence checks. An ordinary failure does not hide later
independent results. Installation failures, timeouts and retained/unclean work
block later device work, while FB1 can still check its offline plan. Finally the
runner restores normal 2202 and verifies its installed version before unlocking,
including on failure or interruption. Summary updates occur after every row.
Exit codes: 0 all selected rows passed, 1 failed/blocked, 130 interrupted.

## Private inputs

Inputs are a duplicate-free JSON object, at most 64 KiB, with known row names.
Only paths, public model names and reviewed hashes belong here; never credentials.
Missing inputs remain explicit prerequisites rather than fabricated passes.

```json
{
  "ba-install": {"agent": "fx", "manifest": "/private/ba-artifacts.json"},
  "ba-removal": {"agent": "fx", "manifest": "/private/ba-artifacts.json"},
  "ba-storage-floor": {"agent": "fx", "manifest": "/private/ba-artifacts.json"},
  "bb5": {
    "qa_apk": "/private/qa-2202.apk", "runner_apk": "/private/runner.apk",
    "target_sha": "REVIEWED_SHA256", "runner_sha": "REVIEWED_SHA256",
    "normal_apk": "/home/eslam/Storage/tmp/oc-apk-share/oc-2202.apk",
    "normal_sidecar": "/home/eslam/Storage/tmp/oc-apk-share/oc-2202.apk.sha256",
    "normal_sha": "REVIEWED_SHA256", "normal_version": 2202,
    "apksigner": "/absolute/path/to/apksigner", "aapt": "/absolute/path/to/aapt"
  },
  "fq3": {"oc1_model": "opencode/big-pickle", "oc2_model": "opencode/big-pickle"},
  "fq9-upgrade": {
    "manifest": "/private/fq9-artifacts.json", "session_receipt": "/private/history.json",
    "run_id": "fq9-final-upgrade"
  },
  "fq9-background": {
    "manifest": "/private/fq9-artifacts.json", "session_receipt": "/private/live-turn.json",
    "run_id": "fq9-final-background"
  },
  "demo": {"synthetic_demo_attested": true}
}
```

BA uses the reviewed `fq_install2` manifest schema, whose normal artifact must
match the candidate path/hash/build. Storage also needs a separate reviewed
8 GiB-floor guard artifact and restores normal afterwards. The parameterized
fresh-install driver replaces the missing version-named driver and verifies
fresh job, checksum, pinned executable, product removal and retained state.
`agents` can replace `agent` with up to six unique targets: codex, gemini, qwen,
goose, omp-acp, fx. Claude/account actions are excluded.

BB5 requires a separate **QA-enabled 2202** target built with
`-PocBuiltinRuntimeQa=true`, its signed instrumentation runner, and normal 2202.
The stock normal APK disables these native hooks; repinning it cannot qualify
BB5. The driver verifies target/runner/normal hashes, package, signer and versions.

FQ3 uses its 2202 evidence directory; historical certification remains separate.
FQ9 verifies candidate/normal identity and the exact installed previous artifact.
An identical previous artifact exercises in-place retention, not a cross-version
upgrade. Its automated preservation checks do not establish Keystore sign-in or
complete semantic history, so manual qualification remains pending.

The background receipt must describe the existing app-sent OC1 tool turn with
real 45-minute ticks and title `<run_id>-background`. No shortened dwell qualifies.
BD7 requires actual crash, saved report, preview and cleanup evidence. FB1 checks
only the offline clean-AVD plan; a real fresh install needs a separate clean AVD.
The demo requires synthetic-data attestation, a human operator and subsequent
privacy review. Neither FB1 plan nor recording is represented as device certification.

## Verification

See [BD16 evidence](BD16-2026-10-09/README.md) for regression commands and the
separate real-run results. Tests use synthetic files/mocked device operations;
the real-run summary is the authority on which prerequisites and rows completed.
