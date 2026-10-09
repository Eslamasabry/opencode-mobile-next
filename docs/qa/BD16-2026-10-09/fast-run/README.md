# Final device pass

Device: `emulator-5554`. One shared lock reservation.

These rows report driver evidence; plan checks and an unreviewed GIF do not certify a device.

- candidate_sha256: `e63fb2e4ff32280ad4c739aee9c17db508eab2e99a42573c4e83bd66dc0babb0`
- normal_sha256: `e63fb2e4ff32280ad4c739aee9c17db508eab2e99a42573c4e83bd66dc0babb0`

| Row | Status | Reason | Receipts |
| --- | --- | --- | --- |
| fq9-upgrade | blocked | fq9_configuration_invalid |  |
| ba-install | fail | row_not_qualified | [receipt 1](ba-install/fx-device.json) |
| ba-removal | blocked | device_prerequisite_failed |  |
| ba-storage-floor | blocked | device_prerequisite_failed |  |
| bb5 | blocked | device_prerequisite_failed |  |
| fq3 | blocked | device_prerequisite_failed |  |
| bd7 | blocked | device_prerequisite_failed |  |
| fb1 | pass | plan_only_not_device_qualification | [receipt 1](fb1-plan.json) |
| normal-restore | pass | normal_restored | [receipt 1](normal-restore.json) |
