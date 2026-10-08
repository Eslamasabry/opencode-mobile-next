# Normal2198 app removal and launch certification

IN PROGRESS on branch sol/ba-2198-cert. Finish line: six targets have observed app launch/rejection, app removal with measured cleanup and retained Claude/shared state, and honest low-storage results. Non-goals: account sign-in/logout, touching Claude installations, credential export, bypassing certification gates, filling storage or shell removal of agents.

Normal2198 is coordinator source064a43a3687773626dc5ce37853446c29be41e0c, SHA2562069cc0ca62554e3fd798f8ba8bfa46f44ae84be4ff82142e87b495cdc8b89a3, signer1DE5BF08…. Every device case runs under an outer `flock -w1800 /home/eslam/Storage/tmp/oc-emulator.lock`, checks /data free space before installation and verifies the normal APK in finally. Agents run one at a time and must be removed through the app before proceeding. No manual deletion fallback.

The driver observes the current target sheet, target-specific Remove confirmation, freed-size result, Done and fresh Not installed row. It separately measures target payload allocated bytes and /data free-space delta; neither is mislabeled as the exact backend receipt. Retention uses only Claude signed-in boolean, public gate fingerprints, opaque conversation-ID digests, shared executable presence and account-home directory counts. Account contents and names are never read/exported. Screenshots mask the entire Claude row and every signed-in identity before writing JPEGs.

The old adapter failed4 of16 cases against current localized confirmation/scoped chip semantics. Updated snapshots reflect the reviewed frontend copy. Reverting the adapter reproduced4 failures and2 missing-method errors across18 cases; restoring it and the screenshot privacy tests passes64 offline cases. Privacy stub initially failed both masking tests. These are QA-driver checks, not app qualification.

Low-storage app injection is compile-time only. Normal2198 has default0; a separate reviewed QA APK with OC_QA_AGENT_INSTALL_MIN_FREE_BYTES=8589934592 is required. Coordinator was asked for that artifact while removal/launch runs proceed. Available RAM remains below6144MiB; no local Gradle/APK build is admitted. Existing policy-only proof cannot promote per-agent app-path storage cells.

Actual daemon launch remains distinct from a bounded picker/auth rejection. A local draft or inspected Sign in sheet is never counted as a daemon launch; the stock app deliberately gates uncertified/signed-out targets.

Run one target only:

```sh
flock -w1800 /home/eslam/Storage/tmp/oc-emulator.lock python3 tool/qa/fq_install2/device_2198.py fx --lock-held
```

The driver refuses any pre-existing target installation or target process, measures storage before dispatch, and never invokes legacy manual cleanup or the2196 restore path. App installation timeout must be diagnosed and target absence established before another agent runs. First fx pilot timed out after its bounded240-second native wait; normal2198 was verified afterward. [Closed result](fx-device.json) does not assert install/removal or promote matrix cells. Native/UI observation is queued behind another lane's lock.

Navigation-timeout capture now uses the configured account-masked screenshot hook too. Its regression test failed before the fix and all66 offline driver cases pass afterward. BC's30-minute emulator hold is expected; read-only observation remains queued, with a3600-second retry if the1800-second wait expires. No device install is dispatched while waiting.
