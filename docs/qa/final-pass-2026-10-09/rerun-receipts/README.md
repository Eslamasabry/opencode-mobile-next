# Final device pass

Device: `emulator-5554`. One shared lock reservation.

These rows report driver evidence; plan checks and an unreviewed GIF do not certify a device.

- candidate_sha256: `82877732e8bb362722f49e3193b8ffe2a91aacbaf37954769e244a1de37462c7`
- normal_sha256: `82877732e8bb362722f49e3193b8ffe2a91aacbaf37954769e244a1de37462c7`

| Row | Status | Reason | Receipts |
| --- | --- | --- | --- |
| fq9-upgrade | fail | fq9_checks_failed | [receipt 1](fq9-upgrade/fq9-2203-rerun-upgrade-upgrade.json) |
| ba-install | pass | verified | [receipt 1](ba-install/fx-device.json) |
| ba-removal | pass | verified | [receipt 1](ba-removal/fx-uninstall-observations.json) |
| ba-storage-floor | blocked | stale_storage_floor_artifact | [receipt 1](ba-storage-floor.json) |
| bb5 | fail | row_not_qualified | [receipt 1](bb5.txt) |
| fq3 | fail | fq3_checks_failed | [receipt 1](../../FQ3e-2026-10-09/fq3-final-513c3a2da207.json) |
| fq9-background | blocked | setup_active_or_unknown | [receipt 1](fq9-background-preparation.json) |
| bd7 | fail | bd7_proof_incomplete | [receipt 1](bd7-saved-report/report.json) |
| fb1 | fail | fresh_avd_driver_failed | [receipt 1](fb1-preparation.json) |
| demo | blocked | final_app_navigation_failed | [receipt 1](demo-preparation.json) |
| normal-restore | pass | normal_restored | [receipt 1](normal-restore.json) |
