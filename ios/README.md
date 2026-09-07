# iOS remote-client source preparation

This target is a **source scaffold, not a verified iPhone build or TestFlight
release**. Native compilation and plugin behavior still require macOS/Xcode.
There is no Termux/on-device server, Android foreground service, notification
reply bridge, home widget, voice, photo capture, or QR scanning enabled on iOS
in this first slice. Use a reachable HTTPS OpenCode server and paste/manual
pairing. Background suspension requires refetch on return, not continuous SSE.

## Provenance and configuration

- Generated with Flutter **3.47.2**, revision
  `d3b14c876900e553bc736ca19295fc09e3853e8e`, after inspecting a disposable
  scaffold. The existing Dart app, pubspec and dependency lockfile were retained.
- Runner identifier: `io.github.eslamasabry.opencodeMobile`; test target appends
  `.RunnerTests`. This is source configuration, not App Store registration.
- Template deployment target: **iOS 15.0**, with Flutter's generated Swift
  Package Manager integration and AppDelegate/SceneDelegate lifecycle.
- Debug/Profile and Release entitlements declare an empty keychain-access-group
  array per the locked `flutter_secure_storage_darwin` instructions. No shared
  App Group, personal Team ID, provisioning profile or signing secret is stored.
- Local-network purpose text is present. There is no broad ATS cleartext
  exemption, background mode, microphone/camera permission prompt or push
  entitlement added merely because a dependency supports those features.
- Icons are opaque renders of the existing project asset. Recreate with
  `python3 tool/branding/generate_ios_icons.py` (Pillow, development only).

Flutter's generator rewrote `pubspec.lock` and migration metadata even with
`--no-overwrite --no-pub`. The unwanted lockfile change was restored; root and
Android migration history were preserved while integrating the generated iOS
entry. Do not run scaffolding over unrelated work without reviewing the diff.

## Checks and remaining gates

The new `ios-quality.yml` workflow is unsigned simulator preparation only:

```sh
flutter pub get --enforce-lockfile
flutter analyze --no-pub
flutter test --no-pub --concurrency=1 test/ios_runner_contract_test.dart \
  test/ios_remote_platform_gating_test.dart test/platform_capabilities_test.dart
flutter build ios --simulator --debug --no-codesign
```

The uploaded `.app` ZIP is for a Mac's iOS Simulator, **not installable on an
iPhone**. It is not an IPA. Paid Apple enrollment/signing is a later distribution
step, not a prerequisite to source or simulator work.

Before claiming iOS support: prove the plugin deployment/architecture matrix,
Keychain save/restart/failure behavior, file picking/export, network permission
and TLS handling, keyboard/safe areas, foreground/resume reconciliation,
VoiceOver, text scaling and a physical-device run. UI gates do not remove
plugins from the native build.

Before App Store submission: inventory app and bundled-SDK required-reason
APIs/privacy manifests and reconcile actual data flows with App Store privacy
answers. No empty manifest is used to pretend that review is complete. Keep
camera/voice/share-extension/push work separately capability-gated.
