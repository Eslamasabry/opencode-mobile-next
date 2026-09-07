// Production quota-monitor UI with synthetic memory-only source and gateways.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/demo/demo_store.dart';
import 'package:opencode_mobile/domain/provider_quota.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/provider_quota_monitor.dart';
import 'package:opencode_mobile/state/provider_quota_overview.dart';
import 'package:opencode_mobile/ui/screens/provider_quota_screen.dart';
import 'package:opencode_mobile/ui/screens/quota_monitor_screen.dart';
import '../../test/provider_quota_test.dart' show providerQuotaFixture;
import 'fixtures.dart'
    show capturePng, captureTheme, loadCaptureFonts, writePng;

final _now = DateTime.utc(2026, 9, 7, 12);
final _profile = ServerProfile(
  id: 'synthetic-quota',
  name: 'Work server',
  baseUrl: 'https://collector.example',
  username: 'synthetic',
  password: 'synthetic-only',
);

class _Store extends ProfileStore {
  _Store() : super(prefs: DemoPreferences());
  @override
  List<ServerProfile> get profiles => [_profile];
  @override
  String? get activeId => _profile.id;
}

class _Gateway implements ProviderQuotaGateway {
  @override
  Future<ProviderQuotaSnapshot> readSnapshot() async =>
      ProviderQuotaSnapshot.fromJson(
        providerQuotaFixture(fetchedAtMs: _now.millisecondsSinceEpoch),
      );
  @override
  void close() {}
}

class _Connection extends ConnectionController {
  _Connection() : super(_Store());
  ProviderQuotaMonitor? _captureMonitor;
  @override
  ProviderQuotaMonitor get quotaMonitor =>
      _captureMonitor ??= ProviderQuotaMonitor(
        store: store,
        createGateway: (_, _) => _Gateway(),
        isReadable: (id) => id == _profile.id,
        networkWifi: () async => true,
        alert: ({required profileID, required key, required token}) async =>
            true,
        dismiss: (_) async => true,
        clock: () => _now,
      );
}

Future<void> completeRead(
  WidgetTester tester,
  Future<void> operation,
  String stage,
) async {
  var complete = false;
  final observed = operation.then((_) => complete = true);
  for (var i = 0; i < 50 && !complete; i++) {
    await tester.pump(const Duration(milliseconds: 20));
  }
  expect(
    complete,
    isTrue,
    reason: '$stage did not complete after bounded fake-clock pumps',
  );
  await observed;
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);
  for (final light in [true, false]) {
    testWidgets('quota monitor ${light ? 'light' : 'dark'}', (tester) async {
      tester.view.physicalSize = const Size(420, 1000);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final connection = _Connection(), boundary = GlobalKey();
      final overview = ProviderQuotaOverview(
        connection,
        clock: () => _now,
        gatewayFactory: (_) => _Gateway(),
      );
      final l10n = lookupAppLocalizations(const Locale('en'));
      Widget app(Widget screen) => RepaintBoundary(
        key: boundary,
        child: MaterialApp(
          debugShowCheckedModeBanner: false,
          theme: captureTheme(light: light),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: screen,
        ),
      );
      try {
        await tester.pumpWidget(
          app(ProviderQuotaScreen(controller: connection, overview: overview)),
        );
        await completeRead(
          tester,
          overview.allowAndRefresh(),
          'initial quota read',
        );
        await tester.pumpAndSettle(
          const Duration(milliseconds: 100),
          EnginePhase.sendSemanticsUpdate,
          const Duration(seconds: 3),
        );
        final enable = find.widgetWithText(TextButton, l10n.quotaMonitorEnable);
        await tester.ensureVisible(enable);
        await tester.tap(enable);
        await tester.pumpAndSettle(
          const Duration(milliseconds: 100),
          EnginePhase.sendSemanticsUpdate,
          const Duration(seconds: 3),
        );
        expect(find.byType(AlertDialog), findsOneWidget);
        expect(tester.takeException(), isNull);
        await writePng(
          'docs/qa/provider-quota/monitor-consent-${light ? 'light' : 'dark'}.png',
          await capturePng(tester, boundary, pixelRatio: 1),
        );
        await tester.tap(
          find.descendant(
            of: find.byType(AlertDialog),
            matching: find.widgetWithText(
              FilledButton,
              l10n.quotaMonitorEnable,
            ),
          ),
        );
        await tester.pumpAndSettle(
          const Duration(milliseconds: 100),
          EnginePhase.sendSemanticsUpdate,
          const Duration(seconds: 3),
        );
        expect(find.byType(QuotaMonitorScreen), findsOneWidget);
        await completeRead(
          tester,
          connection.quotaMonitor.refresh(),
          'monitor read',
        );
        await tester.pumpAndSettle(
          const Duration(milliseconds: 100),
          EnginePhase.sendSemanticsUpdate,
          const Duration(seconds: 3),
        );
        expect(find.text(l10n.quotaMonitorCurrent), findsOneWidget);
        expect(tester.takeException(), isNull);
        await writePng(
          'docs/qa/provider-quota/monitor-review-${light ? 'light' : 'dark'}.png',
          await capturePng(tester, boundary, pixelRatio: 1),
        );
      } finally {
        await tester.pumpWidget(const SizedBox.shrink());
        overview.dispose();
        connection.quotaMonitor.dispose();
        connection.dispose();
        await tester.pump();
      }
    });
  }
}
