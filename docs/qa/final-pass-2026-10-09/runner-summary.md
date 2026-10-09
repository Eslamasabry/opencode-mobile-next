# Final device pass

Device: `emulator-5554`. One shared lock reservation.

These rows report driver evidence; plan checks and an unreviewed GIF do not certify a device.

- candidate_sha256: `82877732e8bb362722f49e3193b8ffe2a91aacbaf37954769e244a1de37462c7`
- normal_sha256: `82877732e8bb362722f49e3193b8ffe2a91aacbaf37954769e244a1de37462c7`

| Row | Status | Reason | Receipts |
| --- | --- | --- | --- |
| fq9-upgrade | blocked | fq9_receipt_unavailable_or_invalid | [receipt 1](fq9-upgrade/fq9-2202-next-retained-a-upgrade.json) |
| ba-install | fail | row_not_qualified | [receipt 1](ba-install/fx-device.json) |
| ba-removal | fail | row_not_qualified | [receipt 1](ba-removal/fx-uninstall-observations.json) |
| ba-storage-floor | blocked | stale_storage_floor_artifact | [receipt 1](ba-storage-floor.json) |
| bb5 | blocked | qa_2203_artifact_unavailable | [receipt 1](bb5-prerequisite.json) |
| fq3 | fail | fq3_receipt_invalid | [receipt 1](../FQ3e-2026-10-09/fq3-final-d9342b7e395e.json) |
| fq9-background | blocked | device_prerequisite_failed |  |
| bd7 | blocked | device_prerequisite_failed |  |
| fb1 | fail | fb1_install_failed | [receipt 1](../../../../../android-qa-fb1-2203-final/evidence/report.json) |
| demo | blocked | device_prerequisite_failed |  |
| normal-restore | pass | normal_restored | [receipt 1](normal-restore.json) |
