# Quiet chat

Finish line: code actions share one accessible compact toolbar and the composer starts at one line, grows with the draft, and preserves existing reading, editing and draft recovery behavior.

Non-goal: protocol, server, persistence or broad theme changes; no signing, publishing or release.

Design: keep conversation text as the primary surface. Language and code actions share a single row; secondary actions use neutral icons, localized tooltips and complete 48dp targets. Wrapping has an explicit toggled semantics state and a selected tint. The empty composer no longer reserves a second text line; multiline editing and the keyboard keep the same editor mounted.

Verified locally with Shorebird Flutter 3.47.2 / Dart 3.13.2, framework `e16cf749ccaa38d7050335ff305def49b1c7c84c`, engine `315ff24261`:

- `flutter pub get` passed.
- `dart format --output=none --set-exit-if-changed` for all seven changed Dart files passed (zero changes).
- `flutter test --concurrency=1 test/markdown_reading_test.dart test/common_file_preview_test.dart test/composer_layout_test.dart`: 51 focused cases passed. After fixing four new test assumptions, only the two affected files reran (41 passing); the 10 common-file-preview cases passed on the same unchanged application source. Exact copy/reader snapshot, wrap/scroll, draft save/delete, keyboard connection, attachment controls and stop/send behavior remain covered.
- `flutter test --no-pub --concurrency=1 tool/capture/quiet_chat_test.dart`: 2 passed, generating all four PNGs beside this note. All four reviewed visually; no clipping, wrapping toolbar controls, or invisible draft text.
- The new layout checks exercise 320dp width at 1x and 2.5x text scale. All code controls stay 48dp square in one row. The idle composer is below 120dp at default scale, grows with multiline input, returns to its original focused height after clearing, and keeps the same editor state.
- `git diff --check` passed. Full analyzer and full serial suite remain coordinator integration gates.

Screenshots use actual widgets with local fixture content, separately from the coordinator's ADB audit of the latest dev APK. They are not proof that these edits have been installed on the device. The local capture subclass supplies the current paginated message contract; the shared older capture fixture was not changed.

No persistence format, gateway, credentials or external URL behavior changed. Existing localized action names remain available through tooltips and screen-reader semantics. Wrapping also exposes its toggled state.

