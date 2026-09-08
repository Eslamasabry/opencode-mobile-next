# Refined root navigation — 2026-09-09

Finish line: root navigation gains clear material depth and responsive selection
while preserving tab state, capability routing, safe content bounds and Android
back behavior. No protocol, credential, font-family or stored-format changes.

The inset dock uses a tinted surface, quiet edge and shallow shadow. It stays
outside the scroll body, so it needs no backdrop blur or content overlap.
High contrast, accessible navigation and disabled animations produce an opaque
surface without shadows; disabled effects also make selection immediate.
The default Material selection animation is 220ms. No new looping motion.

Back unwinds Files search/folders first, then returns secondary destinations to
Workspace. Workspace retains the double-back exit guard. The existing
IndexedStack keeps mounted destinations and Files search state. Error snackbars
now pair the error background with onError text.

Verified with Shorebird Flutter 3.47.2 at
`e16cf749ccaa38d7050335ff305def49b1c7c84c`:

```sh
flutter pub get
flutter test --concurrency=1 test/home_navigation_test.dart test/glass_surface_test.dart test/codex_navigation_test.dart test/text_scale_overflow_test.dart test/accessibility_guidelines_test.dart
flutter test --no-pub --concurrency=1 tool/capture/fluid_shell_test.dart
```

55 focused tests passed, covering back hierarchy, retained Files state, Codex
capability routing, 2.5x text, light/dark accessibility and snackbar text contrast
above 4.5:1. Eight capture scenarios passed. Changed Dart files passed format
verification and `git diff --check`. Analyzer and the full stable-candidate suite
belong to the coordinator's integration checkpoint.

These are real Flutter widgets with synthetic fixture data at 390×844, not APK
or physical-device captures. They demonstrate this branch before integration
with the separate Workspace/chat refinements. All eight were visually inspected.

| Destination | Dark | Light |
| --- | --- | --- |
| Workspace | [Capture](tab-0-dark.png) | [Capture](tab-0-light.png) |
| Files | [Capture](tab-1-dark.png) | [Capture](tab-1-light.png) |
| Activity | [Capture](tab-2-dark.png) | [Capture](tab-2-light.png) |
| More | [Capture](tab-3-dark.png) | [Capture](tab-3-light.png) |

The Files fixture intentionally shows unavailable change indicators and an empty
folder. The dock does not mask either state. Content and primary Workspace
actions remain above the dock; the main scaffold does not extend its body behind
navigation. No signing, native CI, publication or installation was performed by
this slice.
