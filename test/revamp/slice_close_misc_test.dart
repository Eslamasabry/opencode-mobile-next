// slice-close-misc: the review-board leftovers in Servers, Remaining usage,
// the Project tab and Notifications.
//
// - profile-monitor: the "Background checks" page is gone. The Inbox covers
//   other servers; whether each server is checked is set in Notifications,
//   where every former door now lands.
// - provider-quota: replaced by the Plans page (test/usage_plans_page_test.dart).
// - project-hub: Changes and Terminal carry live lines, read without a
//   poller.
// - notifications-settings: the background line in plain words.
//
// Synthetic fixtures only; nothing reaches a server.

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import '../support/fake_server_gateway.dart';
import 'package:opencode_mobile/domain/product_repository.dart';
import '../support/fake_event_channel.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/l10n/app_localizations_en.dart';
import 'package:opencode_mobile/state/automation_policy.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/project_hub_screen.dart';
import 'package:opencode_mobile/ui/screens/servers_screen.dart';
import 'package:opencode_mobile/ui/search/search_index.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../support/profile_monitor_fixture.dart';

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

class _HubApi extends FakeServerGateway {
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

  group('project-hub: the count on Changes', () {
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

    // The Files page is the project's files (vision 2026-10-20): the count
    // rides on Changes in the bar's ⋯, read when the page opens.
    Future<Finder> changes(WidgetTester tester, String text) async {
      await tester.tap(_key('project-hub-menu'));
      await tester.pumpAndSettle();
      final found = find.descendant(
        of: _key('project-hub-changes'),
        matching: find.text(text, findRichText: true),
      );
      return found;
    }

    Future<void> closeMenu(WidgetTester tester) async {
      await tester.tapAt(const Offset(4, 4));
      await tester.pumpAndSettle();
    }

    testWidgets('Changes says how many files changed', (tester) async {
      await pump(tester);
      expect(await changes(tester, 'Changes · 3 files'), findsOneWidget);
    });

    testWidgets('nothing changed: Changes alone, no count', (tester) async {
      await pump(tester, changed: 0, terminals: const [false]);
      expect(await changes(tester, 'Changes'), findsOneWidget);
    });

    testWidgets('read again when the last conversation finishes, never on a '
        'timer', (tester) async {
      final (controller, repository) = await pump(tester, changed: 1);
      expect(await changes(tester, 'Changes · 1 file'), findsOneWidget);
      await closeMenu(tester);
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
      expect(await changes(tester, 'Changes · 4 files'), findsOneWidget);
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
