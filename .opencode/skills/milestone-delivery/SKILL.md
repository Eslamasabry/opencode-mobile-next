---
name: milestone-delivery
description: Use for dev, master, milestone promotion, Shorebird, patches, releases, APKs, signing, tags, CI, or artifact verification.
---

# Milestone Delivery

## Simple Branch Model

- All development happens on `dev`.
- `master` records explicitly approved major milestones.
- Promote reviewed `dev` to `master`; do not invent release/APK/freeze branches.
- Tag only a clean synchronized `master` commit after explicit approval.

## Android Delivery

- Prefer Shorebird for every public Android baseline and eligible Dart-only patch.
- A public sideload baseline is the exact APK produced by Shorebird.
- A Shorebird patch targets one exact existing baseline version.
- A raw `flutter build apk --release` or CI APK is test-only and never public.
- If native code, dependencies, assets, or Shorebird config changed, use a new
  baseline rather than forcing a patch.

## Safety

- Default to dry-run and verification.
- Never push, tag, publish, upload, invoke Shorebird release/patch, or touch
  signing secrets without explicit user authorization.
- Never bypass branch, clean-tree, sync, signer, package, version, test, or
  patch-eligibility checks.
- Verify that GitHub attaches the exact Shorebird artifact; do not allow a tag
  workflow to replace it with a separately rebuilt raw Flutter APK.
- Report physical-device checks as human evidence, not automated guarantees.

Use precise terms: public sideload baseline, Shorebird patch, Store AAB, and CI
APK. Avoid "frozen APK" or ambiguous "release APK" language.
