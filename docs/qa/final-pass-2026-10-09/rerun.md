# APK 2203 final-pass rerun — incomplete

Driver base: `feat/genui-fe` **7c6c0a1e8**. App code remains candidate
`ec778efa`, APK2203. One emulator reservation with a 3600-second acquisition
limit covered baseline preparation, all ten rows and final normal restoration.
The batch exited **1**: **2 passed, 5 failed, 3 blocked**; final restoration passed.

**Normal 2203 was reinstalled and its version verified before unlocking.**
No owner-phone access, build, push or full-suite rerun. The fresh FB1 emulator
was stopped. The preceding final-pass evidence is unchanged.

The upgrade row began from an exact verified2202 replace installation, without
clearing data ([baseline receipt](rerun-baseline.json)). The temporary baseline
was installed inside the same reservation. The missing managed socket then
prevented upgrade qualification; this is a runtime prerequisite failure, not
an inferred application defect.

- candidate_sha256: `82877732e8bb362722f49e3193b8ffe2a91aacbaf37954769e244a1de37462c7`
- normal_sha256: `82877732e8bb362722f49e3193b8ffe2a91aacbaf37954769e244a1de37462c7`

| Row | Status | Reason | Receipts |
| --- | --- | --- | --- |
| fq9-upgrade | fail | fq9_checks_failed | [receipt 1](rerun-receipts/fq9-upgrade/fq9-2203-rerun-upgrade-upgrade.json) |
| ba-install | pass | verified | [receipt 1](rerun-receipts/ba-install/fx-device.json) |
| ba-removal | pass | verified | [receipt 1](rerun-receipts/ba-removal/fx-uninstall-observations.json) |
| ba-storage-floor | blocked | stale_storage_floor_artifact | [receipt 1](rerun-receipts/ba-storage-floor.json) |
| bb5 | fail | row_not_qualified | [receipt 1](rerun-receipts/bb5.txt) |
| fq3 | fail | fq3_checks_failed | [receipt 1](../FQ3e-2026-10-09/fq3-final-513c3a2da207.json) |
| fq9-background | blocked | setup_active_or_unknown | [receipt 1](rerun-receipts/fq9-background-preparation.json) |
| bd7 | fail | bd7_proof_incomplete | [receipt 1](rerun-receipts/bd7-saved-report/report.json) |
| fb1 | fail | fresh_avd_driver_failed | [receipt 1](rerun-receipts/fb1-preparation.json) |
| demo | blocked | final_app_navigation_failed | [receipt 1](rerun-receipts/demo-preparation.json) |
| normal-restore | pass | normal_restored | [receipt 1](rerun-receipts/normal-restore.json) |

## Result interpretation

- **BA:** fx fresh install, expected sign-in rejection, removal, retained state
  and clean continuation passed with the merged picker/removal drivers.
- **Storage floor:** deliberately skipped; QA90001 remains stale.
- **BB5:** QA2203 artifact inputs were used. The driver refused the initial
  runtime state (`bb5_initial_other_runtime_refused`). The row's raw status is
  fail; the idle assertions did not run. Treat this as a blocked runtime
  precondition, not proof of an app idle failure.
- **FQ3:** all 11 OC1 assertions, history switching and cleanup passed.
  OC2 model/image returned `inference_execution_failed`; abort timed out.
  Other OC2 assertions passed. Its receipts do not expose a provider refusal
  for those generic errors, so they remain **unclassified assertion failures**,
  not app defects and not invented provider-unavailable results. The merged
  policy still records confirmed provider-only refusals as `provider_unavailable`;
  none was established by this run's failure receipts.
- **Background:** `setup_active_or_unknown` blocked preparation. No 5/30-minute
  background dwell or survival qualification occurred.
- **BD7:** crash report, share preview and cleanup were recorded, but
  `visible_recent_exit` was false. The adapter correctly withheld qualification
  even though the inner report's `result` said PASS.
- **FB1:** a new run-specific Storage AVD booted, installed2203 and reached
  onboarding/phone setup ([partial screenshots](rerun-receipts/fb1-partial)).
  Setup did not finish within the outer 1800-second driver window; no terminal
  FB1 report or first reply was produced. The row ended `fresh_avd_driver_failed`
  and the owned emulator stopped. The original install failure did not recur.
- **Demo:** `final_app_navigation_failed` blocked capture. No GIF was recorded
  or exported.

The [protocol matrix](../../verification/agent-certification-matrix.md) is the
projection generated from this actual FQ3 receipt. It does not qualify UI,
background survival or installation. Existing unrelated certification cells
are preserved.

## Reproduction and verification

The private per-row input file is
`/home/eslam/Storage/tmp/final-rerun-2203-20261009/rows.json`; it uses the supplied
[QA2203 BB5 inputs](../BB5-QA2203-2026-10-09/final-pass-inputs.json), new FQ9
receipt destinations and the merged OC1 baseline policy. The wrapper at
`/home/eslam/Storage/tmp/final-rerun-2203-20261009/run.py` verifies artifacts,
acquires `final_pass.reservation()` once, performs the baseline replace install,
and calls `final_pass.execute()` with the same inherited descriptor. The normal
restoration occurs before that wrapper releases the reservation.

[Dry-run](rerun-dry-run.json) selected all rows.
[Source hashes](rerun-driver-sources.json) freeze the exact attempt.
[Adapter tests](rerun-preflight-tests.txt): 23 passed.
The only local driver adjustment gives each new FB1 run a distinct AVD directory;
it never reuses/deletes the retained prior AVD. Its
[failing-first test](rerun-fresh-red.txt) then [6 passing tests](rerun-fresh-green.txt)
cover the change. No Flutter/native source change or build was needed.
No extra device retry was performed after this batch.
