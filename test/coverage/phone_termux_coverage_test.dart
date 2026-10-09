// Coverage ratchet for what the Termux setup manager reports (its `status`
// script, key=value lines plus the install log): each case is that answer,
// read by the app's real parser and drawn by the real Termux setup screen with
// its Details open (see paseo_coverage_support.dart for the rules).
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/termux_host_setup.dart';
import 'package:opencode_mobile/ui/screens/phone_setup/phone_setup_termux_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../tool/capture/fixtures.dart' show captureTheme, loadCaptureFonts;
import '../goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;
import '../support/termux_channel_fixture.dart';
import 'paseo_coverage_support.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(() async {
    await loadKitGalleryFonts();
    await loadCaptureFonts();
  });
  final family = CoverageFamily('phone_termux', prefix: '');
  registerLedgerTests(family);

  for (final variant in family.cases) {
    final id = variant['id'] as String;
    testWidgets('termux setup · $id', (tester) async {
      SharedPreferences.setMockInitialValues({});
      final store = MemoryProfileStore(
        prefs: await SharedPreferences.getInstance(),
      );
      final connection = FakePhoneConnection(store);
      final termux = TermuxChannelFixture()..install();
      addTearDown(connection.dispose);
      termux.inventoryOutput =
          'ubuntu=installed\nversion=1.18.29\nruntime=opencode1\n';
      final payload = variant['payload'] as Map;
      final status = Map<String, dynamic>.from(payload['status'] as Map);
      final nowSeconds = DateTime.now().millisecondsSinceEpoch ~/ 1000;
      final lines = [
        for (final entry in status.entries)
          if ('${entry.value}'.isNotEmpty)
            '${entry.key}=${entry.value == 'NOW-90' ? nowSeconds - 90 : entry.value}',
      ];
      termux.statusOutput =
          '${lines.join('\n')}\n__OC_SETUP_OUTPUT__\n${payload['log']}';
      tester.view
        ..physicalSize = const Size(412, 1800)
        ..devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final boundary = GlobalKey();
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            bootstrapProvider.overrideWithValue(AppBootstrap(store)),
            connProvider.overrideWithValue(connection),
          ],
          child: MaterialApp(
            theme: captureTheme(),
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            home: RepaintBoundary(
              key: boundary,
              child: PhoneSetupTermuxScreen(
                job: TermuxHostJob.update,
                openLink: (_, _) async {},
              ),
            ),
          ),
        ),
      );
      for (var i = 0; i < 14; i++) {
        await tester.pump(const Duration(milliseconds: 100));
      }
      final seen = <String>[screenText(tester).join('\n')];
      final details = find.text('Details');
      if (details.evaluate().isNotEmpty) {
        await tester.ensureVisible(details.last);
        await tester.tap(details.last, warnIfMissed: false);
        for (var i = 0; i < 6; i++) {
          await tester.pump(const Duration(milliseconds: 100));
        }
        seen.add(screenText(tester).join('\n'));
      }
      await writeCasePng(tester, boundary, 'phone_$id');
      final screen = seen.join('\n');
      final problems = checkCase(family, variant, screen, primaryText: screen);
      await tester.pumpWidget(const SizedBox.shrink());
      await tester.pump(const Duration(seconds: 2));
      expect(problems, isEmpty, reason: 'screen text:\n${flat(screen)}');
    });
  }
}
