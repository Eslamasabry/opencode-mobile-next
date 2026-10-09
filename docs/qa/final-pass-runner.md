# Final-pass runner

`tool/qa/final_pass.py` runs the existing device drivers in one exclusive
`/home/eslam/Storage/tmp/oc-emulator.lock` reservation, waiting at most 3600 seconds.
It is a plan by default. The plan does not read manifests, run subprocesses,
create directories or touch the device:

```bash
python3 tool/qa/final_pass.py --dry-run
```

Only the coordinator's final device pass should use `--execute`:

```bash
python3 tool/qa/final_pass.py --execute \
  --candidate-apk /absolute/path/to/approved-candidate.apk \
  --candidate-build BUILD_NUMBER --inputs /absolute/private/final-pass-inputs.json
```

`--date YYYY-MM-DD` selects `docs/qa/final-pass-<date>/README.md` (default: today).
An existing output directory is refused, preserving prior receipts. Both APKs
are checked with the existing APK identity verifier before device access. Normal
is APK 2199 by default; an alternate `--normal-apk` must still be build 2199 with
the approved local signer. No APK is built, downloaded, signed or published.

Order is BA install, BA removal, BA storage floor, BB5 idle, FQ3 OC1/OC2,
FQ9 upgrade, FQ9 background (one uninterrupted 300/1800-second observation),
BD7 saved crash report, FB1 clean-run plan, demo GIF. The candidate is installed
before the first device row and reselected if a driver restores another build.
A failed row stops that row; later independent rows still run. An installation
failure, timed-out external driver or explicitly retained/unclean device work
blocks subsequent device rows, but FB1 still runs. Interrupts stop the batch.
Normal installation and a build-number check are attempted before releasing the
reservation, including on failures and interrupts. A restoration failure is a
failed row, never a successful batch.

The README is refreshed after each row and links to the driver's receipts. No
raw process output or exception text is copied into the summary. A zero process
exit alone does not qualify a row; adapters inspect fresh evidence. All rows
must pass for exit 0; fail or blocked yields 1, interruption yields 130.

## Private inputs

The JSON file is bounded to 64 KiB, rejects duplicate keys, and has only known
row names. Omitted prerequisites produce blocked rows. It contains artifact and
fixture paths, not provider credentials. Example shape (replace every path and
hash with the reviewed value):

```json
{
  "ba-install": {"agent": "fx"},
  "ba-removal": {"agent": "fx", "manifest": "/private/ba-artifacts.json"},
  "ba-storage-floor": {"agent": "fx", "manifest": "/private/ba-artifacts.json"},
  "bb5": {
    "runner_apk": "/private/runner.apk", "target_sha": "REVIEWED_SHA256",
    "runner_sha": "REVIEWED_SHA256", "normal_apk": "/private/normal-2199.apk",
    "normal_sidecar": "/private/normal-2199.apk.sha256", "normal_sha": "REVIEWED_SHA256",
    "normal_version": 2199, "apksigner": "/absolute/path/to/apksigner",
    "aapt": "/absolute/path/to/aapt"
  },
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

For a BA matrix, replace `agent` with `agents`, a unique list of up to six IDs:
`codex`, `gemini`, `qwen`, `goose`, `omp-acp`, `fx`. One failed agent stops that row.
The real Claude account is outside all driver selections.

The background fixture must already be running as specified by the existing
FQ9 driver: an app-owned OC1 foreground tool with real 45-minute ticks, a session
titled `<run_id>-background`, and matching private receipt. The batch does not
enroll accounts, create fixtures or replace real credentials. The demo remains
operator-driven: keep notifications and account values off screen, follow the
printed setup → pick-agent → approve-tool cues. It uses the existing recorder's
private output folder, not the website. Recording is blocked until synthetic-data
attestation is supplied, and remains blocked for publication pending human
privacy review. FB1 passes only its offline plan check; it does not certify a
clean installation on the shared emulator.

## Driver compatibility at implementation

The runner preserves the existing drivers' eligibility rules; it does not change
version constants or claim evidence for a different candidate:

| Row | Current prerequisite / integration gap |
| --- | --- |
| BA install | Newer `device_2199.py` is absent from this base; requires its exact artifact and fresh install receipt. Generic driver cannot prove fresh install. |
| BA removal/storage | Existing generic driver accepts reviewed artifact manifests matching the candidate; storage also needs the separate flagged guard artifact. |
| BB5 | Current driver pins QA/normal 2198, incompatible with required normal 2199. Integrate the newer BB driver first. |
| FQ3 | Pins 2197 and reacquires the lock per phase. Explicitly blocked; its owner must provide a candidate-compatible inherited-lock entry point. |
| FQ9 | Pins 2198. Other candidates are blocked. Upgrade additionally requires its installed baseline; the batch will not downgrade an already selected candidate to manufacture that baseline. |
| BD7 | Pins 2198; other candidates are blocked. Complete saved-report, consent cleanup and normal-restore evidence are required. |
| FB1 | Existing fresh-plan check works offline; actual fresh qualification needs a separate clean AVD. |
| Demo | Existing 75-second recorder works with synthetic-data attestation and an operator; private review remains pending. |

BA status references newer commits `1530daff8`, `19d46dd22`, `beabadb70`,
`fdaef2ff4`; BB status references `39c582096`. They were not cherry-picked or
rewritten by this slice. After integrating new drivers, rerun the adapter checks
and update version eligibility with their owners before the device pass.

Python drivers that expose a module-local lock receive a duplicate of the
parent's already-locked descriptor. Their acquisition verifies the same inode;
their local unlock is a no-op, and closing their duplicate retains the parent
reservation. Module references are restored afterward. Drivers with an existing
inherited-FD CLI receive that exact descriptor. Unsupported lock contracts are
blocked; the runner never temporarily releases the reservation.

## Offline verification

```bash
python3 -m unittest tool.qa.test_final_pass tool.qa.test_final_pass_install \
  tool.qa.test_final_pass_protocols tool.qa.test_final_pass_misc
```

The tests use temporary files, mocked drivers and temporary local flock files.
They do not invoke adb, APK verification, recording or ffmpeg. No final device
pass has been performed by this implementation slice.

Verification: 35 focused offline tests pass; the real emulator and all device
drivers remain unrun in this slice.
