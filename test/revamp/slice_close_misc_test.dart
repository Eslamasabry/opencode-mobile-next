// slice-close-misc: the review-board leftovers in Servers, Remaining usage,
// the Project tab and Notifications.
//
// - profile-monitor: the "Background checks" page is gone. The Inbox covers
//   other servers; whether each server is checked is set in Notifications,
//   where every former door now lands.
// - provider-quota (collector path): answers under the provider and server
//   they came from, no "Codex account windows / Reported plan / Snapshot
//   checked" header, no unreported-window rows, no collector controls or
//   second Refresh when the collector is missing, a guide link instead of a
//   repository path, and no monitoring list while nothing is monitored.
// - project-hub: Changes and Terminal carry live lines, read without a
//   poller.
// - notifications-settings: the background line in plain words.
//
// Synthetic fixtures only; nothing reaches a server.
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/domain/provider_quota.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/l10n/app_localizations_en.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/state/automation_policy.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/provider_quota_monitor.dart';
import 'package:opencode_mobile/ui/screens/project_hub_screen.dart';
import 'package:opencode_mobile/ui/screens/provider_quota_screen.dart';
import 'package:opencode_mobile/ui/screens/servers_screen.dart';
import 'package:opencode_mobile/ui/search/search_index.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../tool/capture/fixtures.dart' show loadCaptureFonts;
import '../provider_quota_test.dart' show providerQuotaFixture;
import '../support/profile_monitor_fixture.dart';
import 'screen_usage_1_support.dart' show usageApp;
import 'screen_usage_2_support.dart';

final _en = AppLocalizationsEn();
Finder _key(String key) => find.byKey(ValueKey(key));

void _phone(WidgetTester tester, {double height = 915}) {
  tester.view.physicalSize = Size(412, height);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
}

const _secure = MethodChannel('plugins.it_nomads.com/flutter_secure_storage');

void _mockSecure() {
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  messenger.setMockMethodCallHandler(
    _secure,
    (call) async => call.method == 'readAll' ? <String, String>{} : null,
  );
  addTearDown(() => messenger.setMockMethodCallHandler(_secure, null));
}

// ---------------------------------------------------------------------------
// Project tab fixtures.

class _HubApi extends OpenCodeApi {
  _HubApi() : super(baseUrl: 'http://localhost');
  @override
  Future<List<Session>> sessions() async => [];
}

class _HubRepository implements ProductRepository {
  _HubRepository({required this.changed, required this.terminals});

  int changed;
  List<bool> terminals;
  int statusReads = 0;

  @override
  void setLocation({String? directory, String? workspace}) {}

  @override
  Future<List<VersionControlFile>> listFileStatuses() async {
    statusReads++;
    return [
      for (var i = 0; i < changed; i++)
        VersionControlFile(
          path: 'lib/file_$i.dart',
          status: 'modified',
          additions: 1,
          deletions: 0,
        ),
    ];
  }

  @override
  Future<List<TerminalProcess>> listTerminals() async => [
    for (final (i, running) in terminals.indexed)
      TerminalProcess(
        id: 'pty-$i',
        title: 'Terminal $i',
        command: 'bash',
        arguments: const [],
        directory: '/srv/shopfront',
        running: running,
        pid: 100 + i,
      ),
  ];

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

/// The real controller; [poke] stands in for the transport's own
/// notification after a test flips a field it has no event for.
class _HubController extends ConnectionController {
  _HubController(super.store);
  void poke() => notifyListeners();
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('profile-monitor: Background checks is gone', () {
    setUp(AutomationPolicyController.resetShared);

    testWidgets('Servers lists no Background checks row', (tester) async {
      _mockSecure();
      _phone(tester, height: 1400);
      final store = await monitorStore(count: 1);
      final controller = ConnectionController(
        store,
        monitorGatewayFactory: (_) => (
          gateway: MonitorTestGateway(),
          operations: MonitorTestOperations(),
        ),
      );
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            bootstrapProvider.overrideWithValue(AppBootstrap(controller.store)),
            connProvider.overrideWithValue(controller),
          ],
          child: MaterialApp(
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            home: const ServersScreen(),
          ),
        ),
      );
      await tester.pump();
      expect(_key('servers-list'), findsOneWidget);
      expect(_key('servers-background-checks'), findsNothing);
      expect(find.text(_en.monitorBackgroundChecks), findsNothing);
      await tester.pumpWidget(const SizedBox.shrink());
      controller.dispose();
      await tester.pump();
    });

    test('searching "background checks" lands where each server\'s checks '
        'are turned on', () {
      final entries = allSearchEntries(_en);
      expect(entries.where((e) => e.id == 'inside-servers-monitor'), isEmpty);
      final found = entries.where((e) => e.matches('background checks'));
      expect(found.map((e) => e.id), contains('inside-notifications-servers'));
      final target = found
          .firstWhere((e) => e.id == 'inside-notifications-servers')
          .target;
      expect(target?.pageId, 'notifications-settings');
      expect(target?.sectionId, 'servers');
    });
  });

  group('provider-quota: the collector path answers in plain words', () {
    setUpAll(loadCaptureFonts);
    setUp(
      () => debugPlatformCapabilities = const PlatformCapabilities.android(),
    );
    tearDown(() => debugPlatformCapabilities = null);

    Future<QuotaHarness> pump(
      WidgetTester tester,
      ProviderQuotaSnapshot Function() answer, {
      Future<void> Function(QuotaHarness h)? before,
    }) async {
      _phone(tester, height: 2400);
      final h = await quotaHarness(
        clock: () => DateTime(2026, 9, 6, 12),
        snapshot: answer,
      );
      if (before != null) await before(h);
      await tester.pumpWidget(
        usageApp(
          QuotaHarnessOwner(
            harness: h,
            child: ProviderQuotaScreen(
              controller: h.connection,
              overview: h.quota,
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      return h;
    }

    Future<void> consentAndRead(WidgetTester tester) async {
      for (final key in ['quota-consent', 'quota-read']) {
        await tester.ensureVisible(_key(key));
        await tester.pumpAndSettle();
        await tester.tap(_key(key));
        await tester.pumpAndSettle();
      }
    }

    testWidgets('a reading is answer rows under the provider and server, '
        'with account facts in Details and no collector controls', (
      tester,
    ) async {
      await pump(tester, () => quotaSnapshot(DateTime(2026, 9, 6, 12)));
      await consentAndRead(tester);
      expect(
        find.text(_en.quotaCollectorFrom('Codex', 'Studio')),
        findsOneWidget,
      );
      expect(
        find.textContaining('left in this 5-hour window · resets at'),
        findsOneWidget,
      );
      // The old header and the unreported window are gone.
      expect(find.text('Codex account windows'), findsNothing);
      expect(find.textContaining('Reported plan'), findsNothing);
      expect(find.textContaining('Snapshot checked'), findsNothing);
      expect(_key('quota-window-secondary'), findsNothing);
      expect(find.text('Not reported'), findsNothing);
      // The collector is no longer a row of its own; stopping it names
      // the server it acts on.
      expect(_key('quota-source'), findsNothing);
      expect(find.text('Collector server'), findsNothing);
      expect(find.text('Stop using this collector'), findsNothing);
      expect(find.text(_en.quotaStopCollector('Studio')), findsOneWidget);
      // Nothing is monitored yet: no monitoring list, and no jargon notice.
      expect(_key('quota-monitor-section'), findsNothing);
      expect(find.textContaining('No provider sources'), findsNothing);
      // The alert follows the rows it acts on.
      expect(
        tester.getTopLeft(_key('quota-enable-monitoring')).dy,
        greaterThan(tester.getTopLeft(_key('quota-windows')).dy),
      );
      // Where the collector is, the plan and the reading time: in Details.
      await tester.ensureVisible(_key('quota-details'));
      await tester.tap(_key('quota-details'));
      await tester.pumpAndSettle();
      expect(find.textContaining(quotaOrigin), findsOneWidget);
      expect(find.text(_en.quotaCollectorAddressLabel), findsOneWidget);
      expect(find.text(_en.quotaPlanLabel), findsOneWidget);
      expect(find.text('plus'), findsOneWidget);
      expect(find.text(_en.quotaReadAtLabel), findsOneWidget);
      expect(tester.takeException(), isNull);
    });

    testWidgets('a reading with no reported limit says so in words', (
      tester,
    ) async {
      await pump(tester, () {
        final value = providerQuotaFixture(
          fetchedAtMs: DateTime(2026, 9, 6, 12).millisecondsSinceEpoch,
        );
        value['windows'] = [
          {'id': 'secondary', 'status': 'missing'},
        ];
        return ProviderQuotaSnapshot.fromJson(value);
      });
      await consentAndRead(tester);
      expect(
        find.text(_en.quotaCollectorNoWindows('Codex', 'Studio')),
        findsOneWidget,
      );
      expect(_key('quota-windows'), findsNothing);
    });

    testWidgets('a missing collector: what it needs and how to get it, with '
        'no collector controls and no second Refresh', (tester) async {
      await pump(
        tester,
        () => throw const ProviderQuotaFailure(QuotaFailureKind.unsupported),
      );
      await consentAndRead(tester);
      expect(find.text(_en.quotaNeedsCollector('Studio')), findsOneWidget);
      expect(_key('quota-retry'), findsNothing);
      expect(_key('quota-collector'), findsNothing);
      expect(_key('quota-stop'), findsNothing);
      expect(find.text('Stop using this collector'), findsNothing);
      expect(_key('quota-monitor-section'), findsNothing);
      await tester.tap(_key('quota-collector-how-to'));
      await tester.pumpAndSettle();
      // The guide is a link, not a path in the app's repository.
      expect(
        find.text(_en.quotaCollectorStepInstall('Studio')),
        findsOneWidget,
      );
      expect(find.textContaining('tool/quota'), findsNothing);
      expect(_key('quota-collector-guide'), findsOneWidget);
      expect(find.text(_en.quotaCollectorGuide), findsOneWidget);
    });

    testWidgets('a source monitored on another server is listed with its '
        'state in words, not a collector address', (tester) async {
      await pump(
        tester,
        () => quotaSnapshot(DateTime(2026, 9, 6, 12)),
        before: (h) async {
          await h.connection.store.upsert(
            ServerProfile(
              id: 'laptop',
              name: 'Laptop',
              baseUrl: 'https://laptop.example:8443',
              username: 'fixture-user',
              password: 'fixture-only-laptop-password',
            ),
          );
          const hash =
              'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb';
          await h.connection.store.prefs.setString(
            ProviderQuotaMonitor.key('laptop'),
            jsonEncode({
              'version': 1,
              'rules': {
                QuotaProvider.codex.name: const QuotaMonitorRules(
                  source: hash,
                  account: hash,
                  token: hash,
                  threshold: 90,
                ).toJson(),
              },
            }),
          );
        },
      );
      expect(_key('quota-monitor-section'), findsOneWidget);
      expect(
        find.text(_en.quotaSourceTitle('Laptop', 'Codex')),
        findsOneWidget,
      );
      expect(find.text('https://laptop.example:8443'), findsNothing);
      expect(find.textContaining('Snapshot checked'), findsNothing);
      expect(
        find.text(_en.quotaMonitorDisable('Codex', 'Laptop')),
        findsOneWidget,
      );
      expect(tester.takeException(), isNull);
    });
  });

  group('project-hub: live lines under Changes and Terminal', () {
    Future<(_HubController, _HubRepository)> pump(
      WidgetTester tester, {
      int changed = 3,
      List<bool> terminals = const [true, false],
    }) async {
      SharedPreferences.setMockInitialValues({});
      final prefs = await SharedPreferences.getInstance();
      final repository = _HubRepository(changed: changed, terminals: terminals);
      final controller = _HubController(ProfileStore(prefs: prefs))
        ..api = _HubApi()
        ..repository = repository
        ..directory = '/srv/shopfront'
        ..status = StreamStatus.connected;
      addTearDown(controller.dispose);
      await tester.pumpWidget(
        MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: Scaffold(body: ProjectHub(controller: controller)),
        ),
      );
      await tester.pumpAndSettle();
      return (controller, repository);
    }

    Finder line(String tool, String text) => find.descendant(
      of: _key('project-hub-$tool'),
      matching: find.text(text, findRichText: true),
    );

    testWidgets('Changes says how many files changed; Terminal how many run', (
      tester,
    ) async {
      await pump(tester);
      expect(line('changes', '3 files changed'), findsOneWidget);
      expect(line('terminal', '1 running'), findsOneWidget);
    });

    testWidgets('nothing changed says so; no terminal running says nothing', (
      tester,
    ) async {
      await pump(tester, changed: 0, terminals: const [false]);
      expect(line('changes', 'No changes'), findsOneWidget);
      expect(
        find.descendant(
          of: _key('project-hub-terminal'),
          matching: find.textContaining('running', findRichText: true),
        ),
        findsNothing,
      );
    });

    testWidgets('read again when the last conversation finishes, never on a '
        'timer', (tester) async {
      final (controller, repository) = await pump(tester, changed: 1);
      expect(line('changes', '1 file changed'), findsOneWidget);
      final reads = repository.statusReads;
      // Time alone reads nothing.
      await tester.pump(const Duration(minutes: 5));
      expect(repository.statusReads, reads);
      controller.busySessions = {'s1'};
      controller.poke();
      await tester.pump();
      repository.changed = 4;
      controller.busySessions = {};
      controller.poke();
      await tester.pumpAndSettle();
      expect(repository.statusReads, reads + 1);
      expect(line('changes', '4 files changed'), findsOneWidget);
    });
  });

  test('notifications: the background line is plain and short', () {
    expect(
      _en.e7SettingsUi34,
      'Android stops this after 6 hours a day. The app will tell you when '
      'it does.',
    );
    expect(_en.monitorDisclosure, isNot(contains('Keep live')));
    expect(_en.monitorDisclosure, contains(_en.e7SettingsUi25));
  });
}
