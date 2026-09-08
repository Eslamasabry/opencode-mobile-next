# Clear file browsing: matched evidence

The browser keeps filenames first, presents each change state once, retains the complete file listing, and keeps Review one tap away. Failed-folder Retry stays on its requested destination. Deleted-file taps and menus open the existing Review journey.

Finish line: find, read/review and return with folder intent preserved. Non-goals: dependency hiding, storage/API changes, shared icon/theme replacement, or a rewritten diff reader.

## Capture provenance

`before/` renders FilesScreen from base `0eabc2d`; `after/` renders this branch. The exact same synthetic fixtures, current shared theme, simple Files app bar, fonts, 1080×2400 viewport and 2.625 density are used. Text scale is 1x or 2x. These are actual Flutter widgets; no server, credentials, real files, native keyboard or updated APK is involved. Original on-device observations are maintained separately in the coordinator audit.

| State | Before | After |
| --- | --- | --- |
| dark, 1x, populated | [Before](before/files-dark-1x-populated.png) | [After](after/files-dark-1x-populated.png) |
| dark, 1x, empty | [Before](before/files-dark-1x-empty.png) | [After](after/files-dark-1x-empty.png) |
| dark, 1x, folder | [Before](before/files-dark-1x-folder.png) | [After](after/files-dark-1x-folder.png) |
| dark, 2x, populated | [Before](before/files-dark-2x-populated.png) | [After](after/files-dark-2x-populated.png) |
| dark, 2x, empty | [Before](before/files-dark-2x-empty.png) | [After](after/files-dark-2x-empty.png) |
| dark, 2x, folder | [Before](before/files-dark-2x-folder.png) | [After](after/files-dark-2x-folder.png) |
| light, 1x, populated | [Before](before/files-light-1x-populated.png) | [After](after/files-light-1x-populated.png) |
| light, 1x, empty | [Before](before/files-light-1x-empty.png) | [After](after/files-light-1x-empty.png) |
| light, 1x, folder | [Before](before/files-light-1x-folder.png) | [After](after/files-light-1x-folder.png) |
| light, 2x, populated | [Before](before/files-light-2x-populated.png) | [After](after/files-light-2x-populated.png) |
| light, 2x, empty | [Before](before/files-light-2x-empty.png) | [After](after/files-light-2x-empty.png) |
| light, 2x, folder | [Before](before/files-light-2x-folder.png) | [After](after/files-light-2x-folder.png) |

## Geometry and tradeoff

16dp page rails; 24dp icon slot, 20dp glyph and 12dp title gap; 56dp minimum file rows; two filename lines; 16sp filename and 12sp supporting text. The previous dense filename/subtitle sizes were 14sp/11sp. Selection uses a 2dp underline; review uses a 48dp minimum unboxed row. Ordinary folder glyphs are neutral, leaving accent for selection and Review. No files were hidden or reordered. Large text appropriately shows fewer rows; scroll remains available.

Breadcrumbs retain accessible ActionChips with intrinsic height and a full-width left-aligned viewport. A centering regression found in the first candidate capture was corrected before the final images and tests.

## Checks

Pinned Shorebird Flutter 3.47.2, revision e16cf749ccaa38d7050335ff305def49b1c7c84c. Pub get succeeded; final analyzer found no issues; 75 focused tests/captures passed serially on the final source. Baseline capture run separately passed 8 tests. The first new deleted-file test needed real scrolling at 320dp/2x and now verifies the rendered review destination and return.

```bash
flutter test --no-pub --concurrency=1 test/files_screen_recovery_test.dart test/product_ui_regression_test.dart test/home_navigation_test.dart test/desktop_context_menu_test.dart test/desktop_shortcuts_test.dart tool/capture/clear_files_test.dart
flutter analyze --no-pub
```

No full suite, updated APK, TalkBack, device frame timing, push, CI, signing or release claim. The final app-wide icon family and shared visual tokens belong to coordinator integration.
