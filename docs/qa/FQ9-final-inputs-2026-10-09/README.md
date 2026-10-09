# FQ9 inputs for the next candidate

Finish line: generate reviewed next-candidate artifact inputs offline, then,
only in the coordinator's final device pass, seed one owned retained-history
fixture under the runner's shared lock after verifying the actual installed
2202 baseline and before its upgrade. Non-goals: device work now, creating an AVD,
building/signing an APK, changing other adapters, inference, account operations
or claiming a completed whole-batch pass.

The next candidate's build, path and hash are **not known yet**. The fixed
[reviewed plan](reviewed-plan.json) records the approved 2202 baseline and a
synthetic OC1 fixture specification. It contains no invented next-candidate
identity or history receipt. Host artifact verification is separate evidence;
this plan alone does not prove what is installed on a device.

## Offline input generation after candidate review

The coordinator supplies the three values below after reviewing the actual
next APK. Run from this worktree. The output must be a new private directory
under Storage; never reuse a prior attempt or replace its receipts.

```bash
: "${NEXT_APK:?Set the absolute path of the reviewed next APK}"
: "${NEXT_BUILD:?Set its reviewed versionCode}"
: "${REVIEWED_NEXT_SHA:?Set its reviewed SHA-256}"
python3 tool/qa/fq9/prepare_final_inputs.py \
  --plan docs/qa/FQ9-final-inputs-2026-10-09/reviewed-plan.json \
  --candidate-apk "$NEXT_APK" \
  --candidate-build "$NEXT_BUILD" \
  --candidate-sha256 "$REVIEWED_NEXT_SHA" \
  --output /home/eslam/Storage/tmp/fq9-final-inputs-NEXT
```

The builder creates the artifact manifest, `rows.json` and `fixture-plan.json`.
It must not contact a device or create a fake `history.json`. The reserved
history receipt path in the generated configuration is an instruction for the
future locked seed, not proof of an existing conversation. The builder verifies
the real APKs against their reviewed identities; a supplied hash is not a
substitute for reading and verifying the artifact.

In the FQ9 artifact manifest, `previous` is approved baseline 2202 and
`candidate` and `normal` are the same reviewed next APK. FQ9's inner `normal`
means its candidate restoration between checks. The final runner's outer
`--normal-apk` remains approved 2202 and restores that APK before releasing the
shared reservation. Keep these two meanings distinct.

## Future final pass only

**Do not execute this device command until the coordinator explicitly starts
the final pass.** Merge the generated FQ9 row into the coordinator's reviewed
other-row configuration, preserving its generated paths and fixture plan, and
save that combined file as `rows-merged.json` in the same private output
directory. The command below describes the intended final invocation; the
other adapters still have 2202 eligibility constraints and are owned elsewhere.
Their next-build compatibility and prerequisites must be resolved separately.
This FQ9 input work does not make a whole next-build pass ready.

```bash
python3 tool/qa/final_pass.py --execute --fast \
  --candidate-apk "$NEXT_APK" --candidate-build "$NEXT_BUILD" \
  --normal-apk /home/eslam/Storage/tmp/oc-apk-share/oc-2202.apk \
  --inputs /home/eslam/Storage/tmp/fq9-final-inputs-NEXT/rows-merged.json \
  --output docs/qa/final-pass-NEXT
```

The final runner must hold
`/home/eslam/Storage/tmp/oc-emulator.lock` continuously through fixture seeding,
baseline inspection, upgrade, evidence writing and final 2202 restoration.
There is no separate seed command to run outside that reservation. Upgrade is
the first device row, before any candidate replacement. Verify the actual
already-installed APK matches the complete reviewed 2202 identity, including
hash and signer; a filename or version number alone is insufficient. Require
the same signer for baseline and candidate. Never install/downgrade to 2202
merely to manufacture an upgrade baseline.

The future locked seed uses only the app's canonical project backing directory,
`context.filesDir/projects` (the plan's `files/projects`), which the app exposes
as `/root/projects` in its Ubuntu guest. It must not create a host-side
`rootfs/root/projects` directory: that guest path is a mount and writing its
underlying rootfs would create an unrelated persistent collision. The owned
project/session uses run ID `fq9-2202-next-retained-a` and the `-retained` title
suffix. Existing collisions are blockers, not permission to reuse or delete
someone else's project.

Create the private ownership receipt exclusively with mode `0600`, before the
`noReply` POST, so an interrupted seed still has a durable record of its owned
identifiers. Send only the authored marker from the reviewed plan with
`noReply: true`; do not ask a model to answer, enroll a provider, expose runtime
credentials or start background inference. A prepared receipt does not mean
the POST succeeded. Verify the resulting session/history through the app's
existing server before treating the receipt as usable upgrade input, and fail
closed on a partial or contradictory seed. Never fabricate successful history
from the plan alone.

The automated upgrade checks preserve their existing limits: selected profile,
preference, encrypted-storage and history comparisons do not establish usable
Keystore sign-in or complete semantic history. Keep those manual checks pending
until separately reviewed; do not rewrite their flags to make the batch pass.
Retain the synthetic project/session and ownership receipt for reviewed cleanup
after evidence inspection. Do not silently remove them on failure or delete
unrelated data.

No device qualification, successful fixture seed, next-candidate upgrade or
whole-batch pass is claimed by these planning files.

## Offline verification and handoff

[Host verification](host-verification.json) records baseline APK host identity,
174 passing FQ9 tests and 35 passing runner/adapter tests, all through the shared
`OC_TEST_SLOTS=2` test lock. New next-build/seed tests failed before the adapter
change. The checked-in plan is exercised against the actual seeder in a temporary
filesystem: only canonical `files/projects/<run-id>` is created, the hidden
rootfs path stays empty, and the marker uses `noReply` after receipt persistence.
Tests also cover fresh/private output, APK-path symlinks, receipt collision,
wrong baseline, durable-write failure, and preservation of manual pending flags.

The baseline APK was checked with host APK tools; no ADB, emulator reservation,
fixture seed or next-APK install occurred. The next reviewed APK/build/SHA are
the only remaining materialization inputs to the builder. The generated private
row requests real fixture seeding during the future reserved pass; it does not
claim history already exists. Whole-batch readiness, manual qualification and
other next-build adapters remain the coordinator's separate gates.
