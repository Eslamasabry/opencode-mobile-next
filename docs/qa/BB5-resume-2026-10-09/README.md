# BB5 resumed qualification — 2026-10-09

State: **not device qualified; fixture repair in progress**. BB7 has not started.

The merged runtime compiled, 131 focused native tests and 55 focused Dart tests
passed, 37 host restore tests and two actual Java guard tests passed, and Flutter
analysis is clean. Build/signature details are in build-verification.md,
signed-qa-artifacts.json and native-test-results.json. The normal-2202 host
change has failing-first and removed-fix evidence in host-verification.md.

## Actual device result

Held the shared emulator lock across baseline inspection, QA replacement,
scenario, scoped recovery and normal2202 restoration. Only emulator-5554 was used.
The initial full-UID server-only admission refused a pre-existing additional
runtime. Invoked the existing BuiltinServerService stop action, then tapped the
actual product Start button. Full-UID server-only admission passed afterward.
No inference of logical chat idleness was made from this setup.

The signed QA2198 app and corrected runner were installed in place after exact
normal2202 identity and same-signer checks. The scenario now retained its actual
primary code: **bb5_fixture_owner_changed**. **bb5_cleanup_owner_changed** was a
separate cleanup failure. RuntimePrepared and IdleWaitEntered were unproven;
there is no minute-idle, helper-pause, notification-tap or return success claim.
This demonstrates the previously reproduced collision between the fixture's
qa_bb5_idle owner and the foreground controller's legitimate saved owner.
See device-session.txt and device-failure.json.

## Restoration

Generic restoration recovered the real native owner and policy. With the entire
UID stopped, removed only new qa_bb5_idle native keys proven absent in the
pre-session snapshot. The corrected native cleanup then passed and removed its
own marker/policy, empty helper home and fixture. No account/config payload was
exported, copied or changed. Normal2202 was installed with -r, checked against
its exact hash and expected signer, and actual Connected UI and authenticated
health were verified. Idle policy is default-off with no pending marker, no QA
keys and no fixture. The emulator lock was released. Evidence: final-restoration.json,
qa-fixture-recovery.json, cleanup-result.json and normal2202-restored.jpg.

The next fixture must retain the actual saved runtime owner and valid existing
recipe/budget. It must not relax production owner checks, disable the foreground
controller, delete real profile homes or write synthetic recovery policy.
