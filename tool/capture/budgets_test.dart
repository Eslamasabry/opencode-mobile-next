// Synthetic production Usage/Remaining captures; no provider or server calls.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/demo/demo_store.dart';
import 'package:opencode_mobile/domain/provider_quota.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/provider_quota_overview.dart';
import 'package:opencode_mobile/state/usage_overview.dart';
import 'package:opencode_mobile/ui/screens/provider_quota_screen.dart';
import 'package:opencode_mobile/ui/screens/usage_screen.dart';

import '../../test/provider_quota_test.dart' show providerQuotaFixture;
import '../../test/usage_statistics_test.dart' show stats;
import 'fixtures.dart'
    show capturePng, captureTheme, loadCaptureFonts, writePng;

final _now = DateTime.utc(2026, 9, 7, 12);
final _profile = ServerProfile(
  id: 'capture-budget',
  name: 'Synthetic server',
  baseUrl: 'https://synthetic.example',
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

class _Repository implements ServerOperationsGateway, UsageStatisticsGateway {
  @override
  bool get usageStatisticsSupported => true;
  @override
  Future<UsageStatistics> loadUsageStatistics(UsageQuery query) async =>
      stats();
  @override
  dynamic noSuchMethod(Invocation invocation) =>
      throw StateError('Unexpected capture operation');
}

class _Connection extends ConnectionController {
  _Connection() : super(_Store()) {
    repository = _Repository();
  }
  @override
  Future<ServerOperationsGateway?> prepareActionRepository() async =>
      repository;
}

class _Quota implements ProviderQuotaGateway {
  @override
  Future<ProviderQuotaSnapshot> readSnapshot() async =>
      ProviderQuotaSnapshot.fromJson(
        providerQuotaFixture(fetchedAtMs: _now.millisecondsSinceEpoch),
      );
  @override
  void close() {}
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);
  for (final light in [true, false]) {
    testWidgets('personal budgets ${light ? 'light' : 'dark'}', (tester) async {
      tester.view.physicalSize = const Size(420, 1100);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final connection = _Connection();
      final usage = UsageOverview(
        connection,
        clock: () => _now,
        timezoneLoader: () async => 'UTC',
      );
      final quota = ProviderQuotaOverview(
        connection,
        clock: () => _now,
        gatewayFactory: (_) => _Quota(),
      );
      final boundary = GlobalKey();
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
          app(UsageScreen(controller: connection, overview: usage)),
        );
        await tester.pumpAndSettle();
        await tester.ensureVisible(
          find.byKey(const ValueKey('usage-budget-usd')),
        );
        await tester.tap(find.byKey(const ValueKey('usage-budget-usd')));
        await tester.pumpAndSettle();
        await tester.enterText(
          find.descendant(
            of: find.byType(AlertDialog),
            matching: find.byType(TextFormField),
          ),
          '25',
        );
        await tester.tap(find.text(l10n.fileSave));
        await tester.pumpAndSettle();
        expect(
          find.text(l10n.usageBudgetProgress('3.42', '25', 'USD')),
          findsOneWidget,
        );
        expect(tester.takeException(), isNull);
        await writePng(
          'docs/qa/provider-quota/consumption-budget-${light ? 'light' : 'dark'}.png',
          await capturePng(tester, boundary, pixelRatio: 1),
        );
        await tester.pumpWidget(
          app(ProviderQuotaScreen(controller: connection, overview: quota)),
        );
        await tester.pumpAndSettle();
        await quota.allowAndRefresh();
        await tester.pumpAndSettle();
        final threshold = find.byKey(const ValueKey('quota-threshold-primary'));
        await tester.ensureVisible(threshold);
        await tester.tap(threshold);
        await tester.pumpAndSettle();
        await tester.tap(find.text(l10n.quotaBudgetPercent('90')).last);
        await tester.pumpAndSettle();
        expect(tester.takeException(), isNull);
        await writePng(
          'docs/qa/provider-quota/personal-threshold-${light ? 'light' : 'dark'}.png',
          await capturePng(tester, boundary, pixelRatio: 1),
        );
      } finally {
        await tester.pumpWidget(const SizedBox.shrink());
        quota.dispose();
        usage.dispose();
        connection.dispose();
        await tester.pump();
      }
    });
  }
}
