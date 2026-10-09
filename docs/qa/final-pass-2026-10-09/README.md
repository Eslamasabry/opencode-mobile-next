# APK 2203 final pass — incomplete

One real batch ran on 2026-10-09 under one exclusive emulator reservation with
`flock` acquisition timeout 3600 seconds. It selected all ten rows and exited 1.
**Normal APK 2203 was restored and its installed version verified before release
of the reservation.** No owner-phone access, build, signing or push occurred.

Candidate: `ec778efa3de1730402f096fc126e16fd2e83264e`, versionCode 2203,
SHA256 `82877732e8bb362722f49e3193b8ffe2a91aacbaf37954769e244a1de37462c7`,
local signer `1DE5BF08146F269BCD9EB5C2FFC94469CE4617D37806285955F978A62494D60C`.
The initial installed baseline was 2202. See [scope](RUN-NOTES.md),
[dry-run](dry-run.json), [attempt source hashes](driver-sources.json), and the
[unaltered runner summary](runner-summary.md).

| Row | Reviewed result | Evidence and limitation |
| --- | --- | --- |
| FQ9 2202→2203 upgrade | **FAIL** | [Receipt](fq9-upgrade/fq9-2202-next-retained-a-upgrade.json): `protocol_response_invalid`; upgrade/retention did not complete. The relative-path adapter bug misreported this as a missing receipt in the original summary. |
| BA fresh install (fx) | **FAIL** | [Receipt](ba-install/fx-device.json): app install, checksum, pin and exact version verified; launch entrypoint timed out, and freed-space display was not captured. Product removal left no target processes/files; retained-state hashes matched. |
| BA removal (fx) | **FAIL** | [Receipt](ba-removal/fx-uninstall-observations.json): removal and reclaimed bytes verified, but the driver missed the Not installed row while the completion sheet was open. [Screenshot](ba-removal/fx-final.jpg) shows “fx removed. Freed 13 MB.” Continuation checks passed. |
| BA storage floor | **BLOCKED / skipped** | [Receipt](ba-storage-floor.json): QA90001 is stale. It was not installed or rebuilt. |
| BB5 idle | **BLOCKED** | [Receipt](bb5-prerequisite.json): no QA-enabled 2203 target and matching instrumentation were supplied; available QA2202 cannot qualify 2203. |
| FQ3 OC1/OC2 matrix | **FAIL** | [Aggregate](../FQ3e-2026-10-09/fq3-final-d9342b7e395e.json): all 16 phases ran, cleanup completed, both histories survived switching. Seven capability checks failed; details below. A directory allowlist bug also rejected this valid receipt during the batch. |
| FQ9 5/30-minute background | **BLOCKED, not run** | [Receipt](prerequisite-blocks.json): the FQ3 receipt rejection set the runner's unsafe-to-continue guard. No dwell or background survival claim. |
| BD7 saved report | **BLOCKED, not run** | [Receipt](prerequisite-blocks.json): same FQ3 guard. No new saved-report qualification. |
| FB1 clean first reply | **FAIL** | [Portable receipt](fb1/report.json): a new AVD booted, but `fb1_install_failed` stopped the check before onboarding/setup/reply. The underlying Android failure was not exported; do not infer an app defect or successful clean install. |
| Demo GIF | **BLOCKED, not recorded** | [Receipt](prerequisite-blocks.json): same FQ3 guard. No GIF was captured, reviewed or exported. |
| Normal restoration | **PASS** | [Receipt](normal-restore.json): install succeeded; installed build 2203 verified under the same reservation. |

FQ3 OC1 passed version/create/models/stream/reconnect/abort; model and cards
returned `oc1_prompt_error`, permission allow/deny timed out, and image content
was unverified. OC2 passed all except model switching and image inference
(`inference_execution_failed`). Protocol switching passed. The
[protocol matrix](../../verification/agent-certification-matrix.md) was regenerated
**offline from the saved receipt after the allowlist fix**, retaining those
failures and all unrelated existing certification cells. This is protocol-only
evidence, not UI, install, background or physical-device certification.

FB1 used `/home/eslam/Storage/android-qa-fb1-2203-final`, a fresh named API35 AVD,
3072 MiB guest RAM and two cores. Admission required 6 GiB available host RAM,
16 GiB free on Storage, 2 GiB free on `/`, KVM and free 5556/5557 ports.
Its emulator process was stopped by exact owned PID. The failed AVD and private
logs remain under Storage; no accounts were imported. The successful admission
path did not export its exact resource counters, so only the enforced bounds
are recorded here.

Two runner corrections were made **after** this attempt: absolute output paths
before driver dispatch, and the missing 2202/2203 FQ3 evidence directories.
[Regression evidence](receipts-red.txt), [green checks](receipts-green.txt), and
[offline receipt review](receipt-review.json) distinguish the fixes from the
original attempt. No second device batch was run and no skipped row is promoted.
[Verification details](checks.md) record the focused tests and clean analyzer.
The runner changes are committed locally as `d39463e38`; the attempted driver
snapshot remains recorded separately in `driver-sources.json`.

Remaining gate: a new reviewed reservation for upgrade/background/BD7/demo after
these fixes, the missing QA2203 BB5 artifacts, BA launch/confirmation handling,
and diagnosis of the fresh-AVD install failure. This final pass is not green.
