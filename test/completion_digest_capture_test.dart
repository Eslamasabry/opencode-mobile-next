import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart' show StreamStatus;
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/activity_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../tool/capture/fixtures.dart'
    show capturePng, captureTheme, loadCaptureFonts;

/// Synthetic server metadata for an opt-in render capture. It exercises the
/// production Activity -> completion digest path without contacting a server.
Future<ConnectionController> _captureController() async {
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  return ConnectionController(ProfileStore(prefs: prefs))
    ..status = StreamStatus.connected
    ..directory = '/work/shop'
    ..sessionsById = {
      'digest-capture-session': Session(
        id: 'digest-capture-session',
        title: 'Review the migration',
        directory: '/work/shop',
        time: SessionTime(created: 1, updated: 2, idle: 3),
        summary: const SessionDiffSummary(additions: 4, deletions: 1, files: 2),
      ),
    };
}

class _CaptureVariant {
  const _CaptureVariant({
    required this.name,
    required this.light,
    required this.textScaler,
    required this.textDirection,
  });

  final String name;
  final bool light;
  final TextScaler textScaler;
  final TextDirection textDirection;
}

const _variants = [
  _CaptureVariant(
    name: 'dark',
    light: false,
    textScaler: TextScaler.noScaling,
    textDirection: TextDirection.ltr,
  ),
  _CaptureVariant(
    name: 'light',
    light: true,
    textScaler: TextScaler.noScaling,
    textDirection: TextDirection.ltr,
  ),
  _CaptureVariant(
    name: 'rtl-large',
    light: false,
    textScaler: TextScaler.linear(2.5),
    textDirection: TextDirection.rtl,
  ),
];

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  final captureDirectory =
      Platform.environment['OC_COMPLETION_DIGEST_CAPTURE_DIR'];

  testWidgets('captures the production Activity completion digest states', (
    tester,
  ) async {
    final output = Directory(captureDirectory!);
    expect(
      output.existsSync(),
      isTrue,
      reason:
          'Verify/create the capture directory before setting '
          'OC_COMPLETION_DIGEST_CAPTURE_DIR',
    );
    await loadCaptureFonts();
    tester.view.physicalSize = const Size(320, 640);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);

    final controller = await _captureController();
    addTearDown(controller.dispose);

    for (final variant in _variants) {
      // Dispose the previous production subtree so its scroll position and
      // lazy sliver children cannot leak into the next visual variant.
      await tester.pumpWidget(const SizedBox.shrink());
      await tester.pump();
      final boundary = GlobalKey();
      await tester.pumpWidget(
        RepaintBoundary(
          key: boundary,
          child: MaterialApp(
            debugShowCheckedModeBanner: false,
            theme: captureTheme(light: variant.light),
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            home: MediaQuery(
              data: MediaQueryData(
                size: const Size(320, 640),
                textScaler: variant.textScaler,
              ),
              child: Directionality(
                textDirection: variant.textDirection,
                child: Scaffold(
                  body: ActivityScreen(
                    key: ValueKey('digest-capture-${variant.name}'),
                    controller: controller,
                    embedded: true,
                  ),
                ),
              ),
            ),
          ),
        ),
      );
      await tester.pump();

      final scrollable = find.byType(Scrollable).first;
      final scrollableState = tester.state<ScrollableState>(scrollable);
      final digestSection = find.text('Completion digests');
      var outerActivityMoved = false;
      if (variant.name == 'rtl-large') {
        final before = scrollableState.position.pixels;
        await tester.drag(scrollable, const Offset(0, -180));
        await tester.pump();
        outerActivityMoved = scrollableState.position.pixels > before;
      }
      for (
        var attempt = 0;
        attempt < 20 && digestSection.evaluate().isEmpty;
        attempt++
      ) {
        final before = scrollableState.position.pixels;
        await tester.drag(scrollable, const Offset(0, -180));
        await tester.pump();
        outerActivityMoved |= scrollableState.position.pixels > before;
      }
      if (variant.name == 'rtl-large') {
        expect(
          outerActivityMoved,
          isTrue,
          reason: 'large RTL capture must scroll the outer Activity list',
        );
      }
      expect(digestSection, findsOneWidget);
      await tester.ensureVisible(digestSection);
      await tester.pump();
      await tester.tap(digestSection);
      await tester.pump();
      final session = find.text('Review the migration');
      await tester.scrollUntilVisible(session, 180, scrollable: scrollable);
      await tester.pump();
      await tester.tap(session);
      await tester.pump();

      final card = find.byKey(const Key('completion-digest-card'));
      for (
        var attempt = 0;
        attempt < 20 && card.evaluate().isEmpty;
        attempt++
      ) {
        await tester.drag(scrollable, const Offset(0, -180));
        await tester.pump();
      }
      expect(card, findsOneWidget);
      await tester.ensureVisible(card);
      await tester.pump();
      await tester.pump();
      final cardContext = tester.element(card);
      expect(Directionality.of(cardContext), variant.textDirection);
      expect(
        MediaQuery.textScalerOf(cardContext).scale(10),
        variant.textScaler.scale(10),
      );
      expect(tester.takeException(), isNull);
      final png = await capturePng(tester, boundary, pixelRatio: 1);
      File(
        '${output.path}/${variant.name}.png',
      ).writeAsBytesSync(png, flush: true);
      await tester.pumpWidget(const SizedBox.shrink());
      await tester.pump();
    }
  }, skip: captureDirectory == null || captureDirectory.trim().isEmpty);
}
