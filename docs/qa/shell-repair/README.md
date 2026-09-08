# Shell navigation repair evidence

Base: `0eabc2d`. This slice retains destination state while switching with an interruptible 180ms ease-out-cubic dissolve. Only selected content receives focus, input, semantics and active tickers. Reduced effects select immediately. The opaque tonal dock uses 16dp rails, a 72dp normal height, and localized label metrics shared by measurement and rendering. No backdrop blur is used.

Pinned Flutter 3.47.2 / Dart 3.13.2, Shorebird revision `e16cf749ccaa38d7050335ff305def49b1c7c84c`:

- `flutter pub get`: passed.
- Format of six changed Dart files: passed.
- `flutter test --no-pub --concurrency=1 test/retained_tab_view_test.dart test/home_navigation_test.dart`: 18 passed.
- `flutter test --no-pub --concurrency=1 test/glass_surface_test.dart test/codex_navigation_test.dart test/accessibility_guidelines_test.dart test/text_scale_overflow_test.dart --name 'home shell|opaque navigation|error snackbar|Codex hides|OpenCode keeps'`: 10 passed.
- `flutter test --no-pub --concurrency=1 tool/capture/fluid_shell_test.dart`: 14 passed, generating 16 PNGs.
- Scoped `dart analyze` on six changed Dart files: no issues.

The capture matrix includes all four destinations in light/dark at 390dp, Workspace/More at 320dp and 2.5x, high-contrast More in both themes, and two transition frames at 60ms. Widget fixtures use mock data; they are not ADB captures or a device performance benchmark. Large-type truncation in Workspace/More page content belongs to their separate repairs on the integration branch. This slice verifies dock bounds and label wrapping, Android Back, Files location/search retention, capability routing, active accessibility traversal, focus and ticker isolation.

Initial tests exposed a real narrow label wrap caused by partial theme styles; resolving and painting identical label metrics fixed it. Semantics assertions use the simulated accessibility traversal, since widget lookup may still find excluded retained elements.
