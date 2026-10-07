# Patch canary and rollback

BD1 finish line: the read-only script output describes a staging-first owner-phone canary and rollback for the exact released version. Non-goal: uploading a patch, promoting it, changing tracks on a phone, or claiming a canary was run.

Run `scripts/release.sh patch-plan` anywhere on this branch. It only reads pubspec and prints instructions; it requires no credentials, clean master checkout, network or Flutter build. Existing release and signing safeguards remain in place.

Before an OTA patch, obtain owner approval to upload **staging only**, use the exact full-release baseline, and keep native/assets/dependencies unchanged. The owner phone must explicitly select staging with the Shorebird updater API; installing a normal stable APK alone will not do that. Do not change the phone signer. Record candidate SHA, full release version, patch number, selected track, phone identity, installed patch number, affected journeys, restart/reconnect, data preservation and owner approval. No receipt exists in this change.

After the receipt, obtain fresh approval to promote the same patch in the Console. `release.sh patch --publish` historically creates a stable patch; it is not promotion and must not be used to skip this procedure. BD1 is documentation/output only and does not change publication mechanics. A future enforcement change should require a candidate-bound receipt and a reviewed promotion contract.

Rollback through the Console's patch menu, or with CLI 1.6.120+ using the exact version and patch number printed by the script. A rollback is reversible; it is learned at the next patch check and takes effect on restart. If reverting past multiple newer patches, roll each back. Verify recovery and local data on the owner phone. Older 1.6.x installations use the Console; do not assume the command exists.

Primary sources checked 2026-10-07: [staging patches](https://docs.shorebird.dev/code-push/guides/staging-patches/), [rollback](https://docs.shorebird.dev/code-push/rollback/). No release/patch/signing command was executed.
