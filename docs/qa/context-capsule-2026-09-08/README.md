# Context capsule verification

Candidate: `feature/mobile-context-actions`, based on `bf93b05`. Implemented and focused-verified locally on 2026-09-08; independent review and integration remain with the coordinator. This note accompanies the feature commit.

## Finish line

Chat Add → Context capsule → collect labeled text and supported images → review/edit/remove → Apply to the named task's existing draft. Cancel leaves the composer unchanged; Apply never sends. Applied content survives restart through existing draft storage and clears with its profile.

## Completed focused checks

- `test/context_capsule_test.dart`: editing and explicit application, cancellation, image/nonimage handling, late picker completion, permanent scope invalidation and explicit clipboard access.
- `test/context_capsule_chat_test.dart`: actual Chat entry, preserved composer, offline application without queue/send, restart, changed scope and shared attachment/profile deletion path.
- Existing `composer_layout_test.dart`, `composer_paste_test.dart`, `session_draft_test.dart`, `draft_attachments_test.dart` and `l10n_coverage_test.dart` for affected behavior.
- One localization generation, clean analyzer, serial tests under the machine lease.
- `tool/capture/context_capsule_test.dart`: actual screen with synthetic local content in light/dark, 320 px at 2× text, RTL and text-only mode; top and review-action frames.

Results: **11 feature tests + 47 affected checks passed**, analyzer clean, **five capture cases passed** and all ten PNGs visually inspected. Controls wrap at 320 px/2× and remain reachable by scrolling. RTL reverses directional alignment and action order. Captures mount the production capsule widget with fixture content, independently of Chat navigation; Chat entry/application is covered by integration tests. They are not device screenshots or evidence of a live native picker.

Commands used with this worktree's `build/traycer/env.ps1` and Flutter 3.47.2 / Dart 3.13.2:

```text
flutter pub get
flutter gen-l10n
dart format <seven changed Dart files>
flutter test --no-pub --concurrency=1 test/context_capsule_test.dart test/context_capsule_chat_test.dart
flutter analyze --no-pub
flutter test --no-pub --concurrency=1 test/composer_layout_test.dart test/composer_paste_test.dart test/session_draft_test.dart test/draft_attachments_test.dart test/l10n_coverage_test.dart
flutter test --no-pub --concurrency=1 tool/capture/context_capsule_test.dart
git diff --check
```

`pub get` resolved dependencies and produced this worktree's package configuration, then exited 1 at the host's Windows plugin-symlink requirement. No Windows setting changed. Localization generation succeeded once; all subsequent Flutter checks used `--no-pub`. Initial test failures were scroll-helper assumptions, fixed by scrolling lazy lists; a subsequent helper compile error was corrected. Analyzer's sole new lint was a missing brace block, corrected before its clean result. There are no remaining focused failures.

## Captures

| Mode | Editor | Review actions |
| --- | --- | --- |
| Light | [Editor](light.png) | [Apply and Cancel](light-review.png) |
| Dark | [Editor](dark.png) | [Apply and Cancel](dark-review.png) |
| 320 px / 2× text | [Upper viewport](narrow.png) | [Scrolled actions](narrow-review.png) |
| RTL | [Editor](rtl.png) | [Apply and Cancel](rtl-review.png) |
| Text only | [Explanation and editor](text-only.png) | [Apply and Cancel](text-only-review.png) |

## Privacy, accessibility and limits

No provider/server request is added. Clipboard reads and file selection require an explicit action. Capsule content stays in memory until Apply; persisted content then uses the app's existing private draft vault. No logging of excerpt contents, credentials or screenshot bytes. Existing attachments count toward picker budgets.

Controls use labeled Material buttons and fields, wrapping action groups and a scrollable layout; no custom motion. Native file-picker permissions/process death and live model image acceptance are not established by widget tests. The current connection capability controls whether images are offered. Unapplied capsules are intentionally not persisted. The repository-wide integration gate belongs to the coordinator.
