// Coverage ratchet for what the native setup runner records about a job
// (setup.json): each case is the record the way the runner writes it, read
// by the app's real engine mapper (SetupJobRecord.parse, progressFromRecord)
// and drawn by the real setup progress screen with its details open (see
// paseo_coverage_support.dart for the rules).
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/setup/components.dart';
import 'package:opencode_mobile/builtin/setup/setup_engine.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/screens/phone_setup/phone_setup_progress_screen.dart';

import '../../tool/capture/fixtures.dart';
import '../goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;
import '../support/fake_setup_engine.dart';
import 'paseo_coverage_support.dart';
import 'servers_support.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(() async {
    await loadKitGalleryFonts();
    await loadCaptureFonts();
  });
  setUp(() {
    const secure = MethodChannel(
      'plugins.it_nomads.com/flutter_secure_storage',
    );
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          secure,
          (call) async => call.method == 'readAll' ? <String, String>{} : null,
        );
  });
  final family = CoverageFamily('phone_job', prefix: '');
  registerLedgerTests(family);
  final l10n = lookupAppLocalizations(const Locale('en'));

  for (final variant in family.cases) {
    final id = variant['id'] as String;
    testWidgets('setup job · $id', (tester) async {
      tester.view.physicalSize = const Size(412, 2000);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final raw = Map<String, dynamic>.from(variant['payload'] as Map);
      final record = SetupJobRecord.parse(jsonEncode(raw))!;
      final registry = setupComponents(
        l10n,
        params: record.params,
        host: raw['host'] == 'termux'
            ? SetupHostKind.termux
            : SetupHostKind.builtin,
      );
      final progress = progressFromRecord(
        record,
        registry,
        l10n,
        now: DateTime.fromMillisecondsSinceEpoch((raw['updatedAt'] as int)),
      );
      final engine = FakeSetupEngine(registry: registry)..emit(progress);
      final boundary = GlobalKey();
      final (store, controller) = await serversState();
      controller.dispose();
      await tester.pumpWidget(
        captureApp(
          boundaryKey: boundary,
          controller: controller,
          store: store,
          home: PhoneSetupProgressScreen(
            engine: engine,
            firstSetup: progress.firstSetup,
            openReady: (context) async {},
          ),
        ),
      );
      await frames(tester, 14);
      final seen = <String>[screenText(tester).join('\n')];
      await writeCasePng(tester, boundary, 'phone_$id');
      final details = find.text('Details');
      if (details.evaluate().isNotEmpty) {
        await tester.ensureVisible(details.last);
        await tester.tap(details.last, warnIfMissed: false);
        await frames(tester, 10);
        seen.add(screenText(tester).join('\n'));
        await writeCasePng(tester, boundary, 'phone_${id}_details');
      }
      final screen = seen.join('\n');
      final problems = checkCase(family, variant, screen, primaryText: screen);
      await tester.pumpWidget(const SizedBox.shrink());
      expect(problems, isEmpty, reason: 'screen text:\n${flat(screen)}');
    });
  }
}
