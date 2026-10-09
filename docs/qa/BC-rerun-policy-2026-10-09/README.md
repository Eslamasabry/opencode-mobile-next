# FQ3/FQ9 rerun policy

Finish line: keep the OC1 comparison on the 2196 baseline model, distinguish
provider availability from failed assertions, and select/restore the managed
runtime through the app around FQ9 seeding. Non-goals: another full matrix,
new APK, upgrade qualification, or changing provider/account configuration.

Branch: `sol/bc-rerun-inputs`, from `feat/genui-fe` at `d15c9fea9`.

FQ3's default OC1 policy prefers `zai-coding-plan/glm-5.3` in the connected live
catalog. If unavailable, it records the actual fallback model and blocks
inference without submitting prompts. Structural version/create/catalog checks
retain their own results. Receipts include requested/selected model provenance
and selections by phase. The final-pass adapter ignores the old `oc1_model`
override so an existing Big Pickle row cannot silently bypass this policy;
explicit standalone CLI model selections remain available for diagnostics.

Provider blockers use `state: blocked`, `code: provider_unavailable`, preserve
the original fixed code, and never become passes. Missing model prerequisites,
known auth/model errors and bounded provider HTTP failures can block inference.
Bad request HTTP 400, generic timeouts, malformed data and failed assertions
remain failures. A missing inference prerequisite also blocks its dependent
history check. Strict evidence validation accepts only these scoped blockers;
a provider-only final-pass row is blocked, while mixed assertion failures still
fail. Cleanup failures still stop later rows.

For seeded FQ9 upgrades, exact baseline identity is checked first. A transaction
then observes the prior managed generation, refuses active setup/turns, and
uses the app's In-app Ubuntu → Manage → confirmed runtime switch when OC1 is
needed. OC1 remains active through seed, capture, update and preservation.
Finally, after normal APK restoration, the app switches back to the original
runtime. Restoration is armed before UI mutation; failures retain the primary
error and add `runtimeRestoreError`, which stops later final-pass device rows.
No preferences, services or native setup entry points are used to switch.

Offline verification: 47 focused Dart tests and 80 focused Python tests passed.
The final-pass baseline-override/provider-block tests failed before the adapter
change. The pinned Dart analyzer and diff checks are clean. No matrix or upgrade pass
is claimed here.

## Locked device check

[Final check](device-check.json) on normal 2203 passed the actual app-managed
OC2 → OC1 → OC2 round trip. The one FQ3 stream phase selected
`zai-coding-plan/glm-5.3` from the connected catalog, received a real streamed
reply and cleaned its owned sessions. [Full phase receipt](../FQ3e-2026-10-09/fq3-bc-rerun-policy-a-opencode-stream.json)
records baseline availability, actual model, app-managed provenance and the
four passing version/create/models/stream checks.

The installed SHA-256, signer and UID match before and after. No APK install,
seeded upgrade fixture, account operation or provider configuration change was
performed. Prior managed OC2 was verified restored, all owned FQ3 sessions were
removed, and the lock was released. The other FQ3 phases and actual upgrade
retention still belong to the coordinator's final pass.

Three earlier navigation preflights stopped before runtime mutation and left
OC2 intact: [Settings](initial-navigation-block.json),
[heading collision](navigation-heading-block.json), and
[combined server-pill label](navigation-pill-block.json). Their selectors were
corrected and covered by focused tests. The final check used the current shell
semantics and completed one real runtime round trip.

## Commands and revisions

Implementation: `9a66d74d2`; navigation fixes: `52f333781`, `01cfcb89b`,
`b0433537b`. Every check used `OC_TEST_SLOTS=2`.

```bash
OC_TEST_SLOTS=2 tool/qa/machine_lock.sh test -- python3 -m unittest \
  tool.qa.test_final_pass_provider_policy tool.qa.test_final_pass_protocols \
  tool.qa.fq3.test_update_matrix tool.qa.fq9.test_app_runtime \
  tool.qa.fq9.test_run tool.qa.fq9.test_protocol_failures tool.qa.fq9.test_fixture

OC_TEST_SLOTS=2 tool/qa/machine_lock.sh test -- \
  ~/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/flutter \
  test --no-pub --concurrency=1 test/fq3_oc1_failure_facts_test.dart \
  test/fq3_oc1_test.dart test/fq3_evidence_test.dart
```

The device wrapper owned `/home/eslam/Storage/tmp/oc-emulator.lock` on fd 9,
verified installed 2203, ran `RuntimeTransaction(device).begin()`, and in a
`finally` block ran `restore()` and compared the complete installed identity.
Between those calls it ran the pinned Dart executable with:

```text
run tool/qa/fq3_certify.dart --phase --engine opencode --case stream
  --app-managed-only --run-id fq3-bc-rerun-policy-a
  --inherited-emulator-lock-fd 9
```

This documents the completed attempt; use a fresh run ID for another run.
