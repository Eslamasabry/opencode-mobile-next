// Gallery for KitPageRoute (docs/ux-system/kit-api/KitPageRoute.md "Galleries
// required"): the transition is size-independent (one animation, the same
// on every window class), so this shoots only 412×915 at DPR 3 — mid-way
// through the push, mid-way back, and once in Arabic (the arriving page
// slides in from the left). This does not use the shared `kitGalleryShot`
// (`kit_gallery.dart`, G4/TEST-9): that harness settles every shot and turns
// animations off first (for its own G5 accessibility pass), which would hide
// the very thing these frames exist to show.
//
// Since 2026-10-26 (owner: native Android motion) Android pages use the
// platform's predictive back transition: the kit's shared axis shots run
// under a non-Android theme platform (fuchsia), and the `android` shots show
// the Android fade-forwards slide mid-way.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/goldens/kit/kit_page_route_golden_test.dart
// and look at every changed image before committing it.
import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/kit/kit_page_route.dart';

import '../../../tool/capture/fixtures.dart' show captureTheme;
import 'kit_gallery.dart' show loadKitGalleryFonts;

const _size = Size(412, 915);

/// [captureTheme] with an Arabic-script fallback on the whole text theme, so
/// a page's own copy always reads (TEST-8) whether or not the shot is in
/// Arabic — the same fallback family `loadKitGalleryFonts` loads.
ThemeData _theme({required bool light}) {
  final theme = captureTheme(light: light);
  final text = theme.textTheme.apply(
    fontFamilyFallback: const ['Noto Sans Arabic'],
  );
  return theme.copyWith(
    textTheme: text,
    primaryTextTheme: theme.primaryTextTheme.apply(
      fontFamilyFallback: const ['Noto Sans Arabic'],
    ),
  );
}

Widget _page(String label, ThemeData theme) => Scaffold(
  backgroundColor: theme.scaffoldBackgroundColor,
  body: Center(child: Text(label, style: theme.textTheme.displayMedium)),
);

/// Pumps a one-page app at [_size] and DPR 3, runs [drive] against the
/// home page's context (it pushes, pops, and pumps by whatever amount puts
/// the transition where the shot wants it) and returns the boundary to
/// compare against `goldens/kit/<name>.png`.
Future<void> _shot(
  WidgetTester tester, {
  required String name,
  required bool light,
  Locale locale = const Locale('en'),
  bool android = false,
  required Future<void> Function(BuildContext context, ThemeData theme) drive,
}) async {
  tester.view.physicalSize = _size * 3.0;
  tester.view.devicePixelRatio = 3.0;
  addTearDown(tester.view.reset);
  final own = _theme(light: light);
  final theme = android ? own : own.copyWith(platform: TargetPlatform.fuchsia);
  final boundary = GlobalKey();
  late BuildContext context;
  // ARCH-11: rendered as Android.
  debugDefaultTargetPlatformOverride = TargetPlatform.android;
  try {
    await tester.pumpWidget(
      RepaintBoundary(
        key: boundary,
        child: MaterialApp(
          debugShowCheckedModeBanner: false,
          theme: theme,
          locale: locale,
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(context).copyWith(disableAnimations: false),
            child: child!,
          ),
          home: Builder(
            builder: (inner) {
              context = inner;
              return _page('A', theme);
            },
          ),
        ),
      ),
    );
    // The app's own initial route settles before the shot's own push, so
    // only the shot's transition is on screen.
    await tester.pumpAndSettle();
    await drive(context, theme);
    expect(tester.takeException(), isNull);
  } finally {
    debugDefaultTargetPlatformOverride = null;
  }
  await expectLater(find.byKey(boundary), matchesGoldenFile('$name.png'));
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadKitGalleryFonts);

  for (final light in [false, true]) {
    final mode = light ? 'light' : 'dark';

    testWidgets('kit_page_route mid forward · $mode', (tester) async {
      await _shot(
        tester,
        name: 'kit_page_route_mid_forward_$mode',
        light: light,
        drive: (context, theme) async {
          unawaited(pushKitPage<void>(context, (_) => _page('B', theme)));
          await tester.pump();
          await tester.pump(const Duration(milliseconds: 125));
        },
      );
    });

    testWidgets('kit_page_route android mid forward · $mode', (tester) async {
      await _shot(
        tester,
        name: 'kit_page_route_android_mid_forward_$mode',
        light: light,
        android: true,
        drive: (context, theme) async {
          unawaited(pushKitPage<void>(context, (_) => _page('B', theme)));
          await tester.pump();
          await tester.pump(const Duration(milliseconds: 200));
        },
      );
    });

    testWidgets('kit_page_route mid reverse · $mode', (tester) async {
      await _shot(
        tester,
        name: 'kit_page_route_mid_reverse_$mode',
        light: light,
        drive: (context, theme) async {
          unawaited(pushKitPage<void>(context, (_) => _page('B', theme)));
          await tester.pumpAndSettle();
          Navigator.of(context).pop();
          await tester.pump();
          await tester.pump(const Duration(milliseconds: 125));
        },
      );
    });
  }
}
