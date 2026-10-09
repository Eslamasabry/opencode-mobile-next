// Coverage ratchet for what the provider quota collector sends the Usage
// page's Remaining tab (a deployment extension with no contract: the schema
// is what lib/domain/provider_quota.dart parses). Each case goes through the
// app's real parser and the real screen, after the person's consent and an
// explicit read (see paseo_coverage_support.dart for the rules).
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/provider_quota.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/provider_quota_overview.dart';
import 'package:opencode_mobile/ui/screens/provider_quota_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../tool/capture/fixtures.dart';
import '../goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;
import 'lists_support.dart';
import 'paseo_coverage_support.dart';

class _Gateway implements ProviderQuotaGateway {
  _Gateway(this.snapshot);
  final ProviderQuotaSnapshot snapshot;

  @override
  Future<ProviderQuotaSnapshot> readSnapshot() async => snapshot;

  @override
  void close() {}
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  final family = CoverageFamily('quota', prefix: '');
  const secureChannel = MethodChannel(
    'plugins.it_nomads.com/flutter_secure_storage',
  );

  setUpAll(() async {
    await loadKitGalleryFonts();
    await loadCaptureFonts();
  });

  registerLedgerTests(family);

  for (final variant in family.cases) {
    final id = variant['id'] as String;
    testWidgets('quota · $id', (tester) async {
      tester.view.physicalSize = const Size(420, 1600);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final secure = <String, String>{};
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(secureChannel, (call) async {
            final arguments = call.arguments as Map;
            final key = arguments['key'] as String;
            if (call.method == 'write') {
              secure[key] = arguments['value'] as String;
            }
            if (call.method == 'read') return secure[key];
            return null;
          });
      addTearDown(
        () => TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
            .setMockMethodCallHandler(secureChannel, null),
      );
      SharedPreferences.setMockInitialValues({});
      final store = ProfileStore(prefs: await SharedPreferences.getInstance());
      final profile = ServerProfile(
        id: 'quota-profile',
        name: 'Synthetic collector',
        baseUrl: 'https://collector.example:8443',
        username: 'fixture-user',
        password: 'fixture-only-quota-password',
      );
      await store.upsert(profile);
      await store.setActiveId(profile.id);
      final connection = ConnectionController(store);
      final snapshot = ProviderQuotaSnapshot.fromJson(variant['payload']);
      final overview = ProviderQuotaOverview(
        connection,
        providerGatewayFactory: (_, provider) => _Gateway(snapshot),
        clock: () => DateTime.fromMillisecondsSinceEpoch(
          1788960000000 + 2000,
          isUtc: true,
        ),
      );
      // The collector's provider for this case.
      await tester.runAsync(() async => overview);
      final boundary = GlobalKey();
      await tester.pumpWidget(
        RepaintBoundary(
          key: boundary,
          child: MaterialApp(
            theme: captureTheme(),
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            home: Material(
              child: ProviderQuotaScreen(
                controller: connection,
                overview: overview,
              ),
            ),
          ),
        ),
      );
      await settle(tester);
      Future<void> reveal(Finder target) async {
        await tester.scrollUntilVisible(
          target,
          160,
          maxScrolls: 60,
          scrollable: find.byType(Scrollable).first,
        );
      }

      final provider = snapshot.provider;
      if (overview.provider != provider) overview.selectProvider(provider);
      await settle(tester);
      await reveal(find.byKey(const ValueKey('quota-consent')));
      await tester.tap(find.byKey(const ValueKey('quota-consent')));
      await settle(tester);
      await reveal(find.byKey(const ValueKey('quota-read')));
      await tester.tap(find.byKey(const ValueKey('quota-read')));
      await settle(tester, rounds: 8);
      final details = find.byKey(const ValueKey('quota-details'));
      if (details.evaluate().isNotEmpty) {
        await reveal(details);
        await tester.tap(find.text('Details'));
        await settle(tester);
      }
      final text = screenText(tester).join('\n');
      await writeCasePng(tester, boundary, 'lists-quota-$id');
      File('build/coverage/quota_$id.txt').writeAsStringSync(flat(text));
      final problems = checkCase(family, variant, text);
      await tester.pumpWidget(const SizedBox.shrink());
      overview.dispose();
      connection.dispose();
      await tester.pump(const Duration(seconds: 1));
      expect(problems, isEmpty, reason: 'screen text:\n${flat(text)}');
    });
  }
}
