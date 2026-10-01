<!--
OpenCode Mobile is an independent community project, not affiliated with the
official OpenCode team.

Do not open a pull request that demonstrates or fixes a security
vulnerability before it has been reported privately. See SECURITY.md.
-->

## What changed and why

<!--
The commit body is the durable record; this section should read like it.
Say what the user could not do before, or what was wrong. "Refactor" and
"cleanup" are not reasons.
-->

## How it was verified

Run the gates with the pinned Flutter
(`~/.shorebird/bin/cache/flutter/<rev>/bin/flutter`), not whatever is on
`PATH`. See [CONTRIBUTING.md](../CONTRIBUTING.md).

- [ ] `flutter analyze` — clean, no new `// ignore`
- [ ] `flutter test --concurrency=1` — full suite green (paste the count)
- [ ] New behavior is covered by a test that fails without this change

```
<!-- paste the flutter test summary line -->
```

## Checklist

Tick what applies; delete what does not. An unticked box with a sentence
saying why is a fine answer.

- [ ] **Screenshots** for anything visible — before and after
- [ ] **Accessibility**: labels on new controls, tap targets ≥ 48 dp, layout
      holds at 2.5× text on a 360 dp width
- [ ] **Privacy / security**: this touches credentials, stored data, external
      links, or notifications — and the notes below say how
- [ ] **Profile-scoped storage**: any new per-profile key is named
      `oc.<what>.<profileId>` so the deletion sweep finds it
- [ ] **External links**: every URL the app did not author goes through
      `openExternalLink`
- [ ] **Migration notes** for a changed stored format
- [ ] **Gateway boundary**: UI talks to `lib/domain/`, not `api/` or `api2/`
- [ ] **Generated code** in `packages/opencode_sdk/` is not hand-edited
- [ ] **Docs**: README, CONTRIBUTING, PRIVACY, or THIRD_PARTY_NOTICES updated
      if this makes any of them untrue
- [ ] **Dependencies**: `pubspec.lock` changed — `THIRD_PARTY_NOTICES.md`
      regenerated (the hygiene test will tell you)

## Smoke matrix results

Required for a release or sideload pull request; delete for anything else.
Procedure and table: [docs/qa/smoke-matrix.md](../docs/qa/smoke-matrix.md).
Link the record you filled in (`docs/qa/smoke-<version>-<date>/README.md`).

- [ ] Android 12 (API 31) emulator
- [ ] Android 13 (API 33) emulator
- [ ] Android 14 (API 34) emulator
- [ ] Android 15 (API 35) emulator
- [ ] Android 16 (API 36) emulator, and 36.1 for the promoted-notification path
- [ ] Two physical devices (model and Android version in the record)
- [ ] Install and upgrade, connect, chat, permission, background,
      notifications, built-in Linux, AI Team and storage access all ticked
      per version
- [ ] No crash or ANR in `adb logcat -b crash`
- [ ] Doze and standby run for background changes
      ([doze-standby.md](../docs/qa/doze-standby.md))

## Privacy, security, and release notes

<!--
Required if any of those boxes are ticked. What data moves, what is stored,
what a hostile server could do with this, whether this needs a full APK or
can ride a Shorebird patch.
-->

## Anything a reviewer should push back on

<!--
Shortcuts taken, alternatives rejected, things you are unsure about. This
section existing is not an admission of failure.
-->
