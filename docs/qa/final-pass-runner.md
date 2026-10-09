# Final-pass runner

`tool/qa/final_pass.py` runs the device drivers under one exclusive
`/home/eslam/Storage/tmp/oc-emulator.lock` reservation (up to 3600 seconds to acquire).
The default is an inert plan: no manifest reads, subprocesses, output files or adb.

```bash
python3 tool/qa/final_pass.py --dry-run --fast
python3 tool/qa/final_pass.py --execute --fast \
  --candidate-apk /home/eslam/Storage/tmp/oc-apk-share/oc-2203.apk \
  --candidate-build 2203 --inputs /absolute/private/final-pass-inputs.json \
  --output docs/qa/final-pass-2203
```

Normal restoration defaults to the approved local-signed APK 2203. An alternate
`--normal-apk` must have that build and signer. Candidate and normal identities
are checked before taking the lock. Existing output directories are refused;
`--date` chooses the default `docs/qa/final-pass-<date>` directory. No host build, artifact download, signing or publication is performed by the runner.
Authorized product install/setup rows can download their managed components.

## Sequencing

Upgrade runs first, before candidate installation: its driver must observe the
actual reviewed baseline already installed, with its existing history receipt
or an explicit reviewed seed request. The seed path is new, private and outside
the checkout; its real receipt is written under the same reservation after
exact-baseline admission and before the no-reply history marker is posted.
The runner does not install an older baseline. The remaining order is BA install,
BA removal, BA storage floor, BB5 idle, FQ3 OC1/OC2, FQ9 background, BD7 saved
report, FB1 fresh-device check (or offline plan when not enabled), demo. `--fast` omits background and demo entirely;
it cannot start the 30-minute dwell or GIF recorder.

Before each subsequent device row, the runner installs/reselects the candidate
if needed. Drivers with module-local locks borrow the parent's descriptor and
cannot unlock it. FQ3 validates an inherited descriptor and executes phases in
that process without nested flock acquisition; the current-build protocol matrix is written after validated phases and cleanup. Other subprocess drivers
receive the descriptor through `pass_fds` and validate it themselves.

Rows retain their evidence checks. An ordinary failure does not hide later
independent results. BA install/removal/storage drivers now confirm continuation
with fresh observations: the normal APK's signer/hash/build, idle native setup
and app checks, at least 800 MB available, and all six managed targets absent
(zero allocated bytes, no launcher/payload/staging/lock or target processes).
A failed row stays failed. Only a complete confirmation permits later device
rows; missing observations or retained/unclean work block them. Multi-agent rows
preserve that decision when stopping on an agent failure. FB1 can still check its independent fresh-device prerequisites. Rejected preflight is observed without replacing the APK or
deleting unexpected files. Finally the
runner restores normal 2203 and verifies its installed version before unlocking,
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
  "bb5": {"skip_reason": "qa_2203_artifact_unavailable"},
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

The existing BB5 driver requires a separate **QA-enabled 2202** target built
with `-PocBuiltinRuntimeQa=true`, its signed instrumentation runner and normal
2202. That contract is incompatible with this 2203 batch: record the explicit
prerequisite blocker until a reviewed 2203 target/runner and driver are available.
The stock normal APK disables these native hooks; repinning it cannot qualify
BB5. The driver verifies target/runner/normal hashes, package, signer and versions.

FQ3 uses its 2203 evidence directory; historical receipts remain separate.
FQ9 verifies candidate/normal identity and the exact installed previous artifact.
For upgrade and background, the adapter accepts an explicitly reviewed build newer than
2202 with previous=2202. FQ3 and BD7 target 2203. Use the
[reviewed next-candidate input builder](FQ9-final-inputs-2026-10-09/README.md)
to supply `seed_history_receipt` instead of `session_receipt`; never both.
The builder does not seed or manufacture session IDs offline. FQ9's manifest
`normal` is the candidate, while the outer batch still restores normal 2203.
An identical previous artifact exercises in-place retention, not a cross-version
upgrade. Its automated preservation checks do not establish Keystore sign-in or
complete semantic history, so manual qualification remains pending.

The background receipt must describe the existing app-sent OC1 tool turn with
real 45-minute ticks and title `<run_id>-background`. No shortened dwell qualifies.
BD7 requires actual crash, saved report, preview and cleanup evidence. FB1
defaults to an offline plan; explicit `provision_fresh` runs the separate AVD.
The demo requires synthetic data and subsequent privacy review; the optional
`offline_demo` driver performs the product demo interactions. A plan or unreviewed
recording does not certify a device.

## Verification

See [BD16 evidence](BD16-2026-10-09/README.md) for regression commands and the
separate real-run results. Tests use synthetic files/mocked device operations;
the real-run summary is the authority on which prerequisites and rows completed.

## APK 2203 final-pass preparation

Use the [actual final-pass report](final-pass-2026-10-09/README.md) for results;
not every selected row completed. Relative output paths are now normalized to
absolute paths before dispatch. The FQ3 matrix validator accepts the exact
2202/2203 model-scoped directories without accepting cross-build paths.

Optional per-row inputs for the approved 2203 batch:

- `ba-storage-floor: {"skip_reason": "stale_storage_floor_artifact"}` explicitly
  records the stale QA90001 omission.
- `bb5: {"skip_reason": "qa_2203_artifact_unavailable"}` records the missing
  QA-enabled target. The current standalone BB5 driver still requires QA2202;
  its native hooks cannot be inferred from stock release2203.
- `fq9-background` may add `prepare_fixture: true` to its reviewed manifest and
  new private `session_receipt`. The app sends the fixed free-model tool prompt;
  only the full 5/30-minute observation can qualify survival.
- `fb1: {"provision_fresh": true}` checks 6 GiB host RAM, 16 GiB Storage and
  2 GiB root headroom, KVM and ports before creating a new Storage-only AVD.
  It stops only that owned emulator. Existing run directories are refused.
- `demo: {"offline_demo": true}` requires the product's explicit simulated
  disclosure before recording. Its GIF stays private pending visual review.

The shared-device unsafe-continuation guard still blocks later shared-device
rows; independent fresh-device preflight may continue. All attempts restore
normal2203 before releasing the one reservation. No automatic second batch
is used to replace failed evidence.
