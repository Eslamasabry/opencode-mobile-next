import 'support/fake_server_capabilities.dart';
import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'support/fake_server_gateway.dart';
import 'package:opencode_mobile/domain/provider_presentation.dart';
import 'package:opencode_mobile/domain/product_repository.dart';
import 'support/fake_event_channel.dart';
import 'package:opencode_mobile/domain/profile_monitor.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/home_screen.dart';
import 'package:opencode_mobile/ui/screens/settings_screen.dart';
import 'package:opencode_mobile/ui/search/search_index.dart';
import 'package:opencode_mobile/voice/model_manager.dart';
import 'package:opencode_mobile/voice/voice_ui.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'revamp/screen_voice_1_fixtures.dart';
import 'support/settings_doors.dart';

// The Settings rebuild (docs/design/settings-ia-audit-2026-10-12.md and the
// Settings Standard): five groups, no pickers with one server, "By server"
// with several, a page per server with three sections, the override count
// and "Use my default". Assertions are about what a person reads and taps.

class _Api extends FakeServerGateway {
  _Api() : super(baseUrl: 'http://localhost');

  @override
  ServerCapabilities get capabilities => testServerCapabilities;

  @override
  Future<Health> health() async => Health(healthy: true, version: '1.18.23');
}

class _Repository implements ProductRepository {
  @override
  void setLocation({String? directory, String? workspace}) {}

  @override
  Future<List<TerminalProcess>> listTerminals() async => [];

  @override
  Future<TerminalShellSettings> loadTerminalShellSettings() async =>
      const TerminalShellSettings(selected: '', options: []);

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

Future<ConnectionController> _controller({int servers = 1}) async {
  SharedPreferences.setMockInitialValues({
    'oc.profiles': jsonEncode([
      {
        'id': 'profile-1',
        'name': 'Workstation',
        'flavor': 'v2',
        'baseUrl': 'http://localhost:4096',
        'username': '',
      },
      if (servers > 1)
        {
          'id': 'profile-2',
          'name': 'Laptop',
          'flavor': 'v2',
          'baseUrl': 'http://laptop.example:4096',
          'username': '',
        },
      if (servers > 2)
        {
          'id': 'profile-3',
          'name': 'Studio',
          'flavor': 'v2',
          'baseUrl': 'http://studio.example:4096',
          'username': '',
        },
    ]),
    'oc.activeProfile': 'profile-1',
  });
  final preferences = await SharedPreferences.getInstance();
  final store = ProfileStore(prefs: preferences);
  await store.load();
  return ConnectionController(store)
    ..api = _Api()
    ..repository = _Repository()
    ..status = StreamStatus.connected;
}

Widget _app(
  ConnectionController controller, {
  Locale locale = const Locale('en'),
  Widget? home,
}) => ProviderScope(
  overrides: [connProvider.overrideWithValue(controller)],
  child: MaterialApp(
    theme: AppTheme.dark(),
    locale: locale,
    localizationsDelegates: AppLocalizations.localizationsDelegates,
    supportedLocales: AppLocalizations.supportedLocales,
    home: home ?? SettingsScreen(controller: controller),
  ),
);

final _en = lookupAppLocalizations(const Locale('en'));
final _ar = lookupAppLocalizations(const Locale('ar'));

Finder _row(String key) => find.byKey(ValueKey(key));

Finder _valueOn(String rowKey, String text) =>
    find.descendant(of: _row(rowKey), matching: find.text(text));

Future<void> _tall(WidgetTester tester) async {
  tester.view
    ..physicalSize = const Size(412, 3000)
    ..devicePixelRatio = 1;
  addTearDown(tester.view.reset);
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    debugPlatformCapabilities = const PlatformCapabilities.android();
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (_) async => null,
        );
  });
  tearDown(() => debugPlatformCapabilities = null);

  group('with one server', () {
    testWidgets('no pickers: server-owned rows act on the one server', (
      tester,
    ) async {
      await _tall(tester);
      final controller = await _controller();
      addTearDown(controller.dispose);
      await tester.pumpWidget(_app(controller));
      await tester.pumpAndSettle();

      await openSettingsGroup(tester, SettingsGroup.connections);
      expect(find.text(_en.settingsV3ByServer), findsNothing);
      expect(_row('settings-servers-block'), findsNothing);
      expect(find.text(_en.settingsV3ServersBlock), findsNothing);
      // The Servers row names the one server and that it is connected.
      expect(
        _valueOn('settings-saved-servers', 'Workstation · Connected'),
        findsOneWidget,
      );
    });

    testWidgets('rows are one line: names and values, no explanations', (
      tester,
    ) async {
      await _tall(tester);
      final controller = await _controller();
      addTearDown(controller.dispose);
      await tester.pumpWidget(_app(controller));
      await tester.pumpAndSettle();

      // The five words never change with the server.
      for (final label in [
        _en.settingsHubServer,
        _en.settingsHubGroupAgent,
        _en.settingsHubGroupConversations,
        _en.settingsHubGroupThisApp,
        _en.settingsHubHelp,
      ]) {
        expect(find.text(label), findsOneWidget, reason: label);
      }
      // Only Server says more: where the server runs and how it is doing.
      expect(
        find.descendant(
          of: _row('settings-hub-row-connections'),
          matching: find.text(
            _en.settingsHubServerLine(
              KitBidi.auto('Workstation'),
              _en.e7WorkspaceConnected,
            ),
          ),
        ),
        findsOneWidget,
      );
      // The long subtitles of the old hub moved to the pages they open.
      await openSettingsGroup(tester, SettingsGroup.chat);
      expect(find.textContaining('Your common short phrases'), findsNothing);
      expect(find.text(_en.transcriptTogglesReasoningOn), findsNothing);
    });

    testWidgets(
      'the Model row never calls a model chosen here a server default',
      (tester) async {
        await _tall(tester);
        final controller = await _controller();
        addTearDown(controller.dispose);
        controller.selectedModel = ModelRef(
          providerID: 'opencode',
          modelID: 'nemotron-free',
        );
        await controller.store.setModel(
          'profile-1',
          'opencode',
          'nemotron-free',
          explicit: true,
        );
        await tester.pumpWidget(_app(controller));
        await tester.pumpAndSettle();
        await openSettingsGroup(tester, SettingsGroup.ai);

        expect(find.text(_en.modelServerDefault), findsNothing);
        expect(
          find.descendant(
            of: _row('settings-model-and-mode'),
            matching: find.textContaining('Nemotron Free'),
          ),
          findsOneWidget,
        );
      },
    );
  });

  group('with several servers', () {
    testWidgets('server-owned rows say By server and the servers are listed', (
      tester,
    ) async {
      await _tall(tester);
      final controller = await _controller(servers: 2);
      addTearDown(controller.dispose);
      await tester.pumpWidget(_app(controller));
      await tester.pumpAndSettle();

      for (final (group, keys) in [
        (
          SettingsGroup.ai,
          ['settings-model-and-mode', 'settings-providers', 'settings-tools'],
        ),
        (SettingsGroup.chat, ['default-shell-settings-entry']),
      ]) {
        await openSettingsGroup(tester, group);
        for (final key in keys) {
          expect(
            _valueOn(key, _en.settingsV3ByServer),
            findsOneWidget,
            reason: key,
          );
        }
        await closeSettingsGroup(tester);
      }
      await openSettingsGroup(tester, SettingsGroup.connections);
      expect(
        _valueOn('settings-saved-servers', _en.settingsV3ServersCount(2)),
        findsOneWidget,
      );
      // Under Server's rows: the line, then each server by its own name.
      expect(find.text(_en.settingsV3ServersBlock), findsOneWidget);
      expect(_row('settings-server-profile-1'), findsOneWidget);
      expect(_row('settings-server-profile-2'), findsOneWidget);
      expect(
        _valueOn('settings-server-profile-1', _en.settingsV3Connected),
        findsOneWidget,
      );
      expect(
        _valueOn('settings-server-profile-2', _en.settingsV3NotConnected),
        findsOneWidget,
      );
    });

    testWidgets('By server lists each server and opens its page at the row', (
      tester,
    ) async {
      await _tall(tester);
      final controller = await _controller(servers: 2);
      addTearDown(controller.dispose);
      await tester.pumpWidget(_app(controller));
      await tester.pumpAndSettle();
      await openSettingsGroup(tester, SettingsGroup.ai);

      await tester.tap(_row('settings-model-and-mode'));
      await tester.pumpAndSettle();
      expect(find.byType(ByServerScreen), findsOneWidget);
      expect(_row('by-server-profile-1'), findsOneWidget);
      expect(_row('by-server-profile-2'), findsOneWidget);
      // A model chosen on neither server: both say the server picks.
      expect(find.text(_en.settingsV3ServerPick), findsNWidgets(2));

      await tester.tap(_row('by-server-profile-2'));
      await tester.pumpAndSettle();
      expect(find.byType(ServerPageScreen), findsOneWidget);
      expect(find.text('Laptop'), findsWidgets);
    });

    testWidgets('a connected server opens the model picker, not its page', (
      tester,
    ) async {
      await _tall(tester);
      final controller = await _controller(servers: 2);
      addTearDown(controller.dispose);
      await tester.pumpWidget(_app(controller));
      await tester.pumpAndSettle();
      await openSettingsGroup(tester, SettingsGroup.ai);
      await tester.tap(_row('settings-model-and-mode'));
      await tester.pumpAndSettle();

      await tester.tap(_row('by-server-profile-1'));
      await tester.pumpAndSettle();
      expect(find.byType(ServerPageScreen), findsNothing);
      expect(
        find.text(_en.settingsV3ModelNewOn('Workstation')),
        findsOneWidget,
      );
    });

    testWidgets('the Tools page says what tools are and where they live', (
      tester,
    ) async {
      await _tall(tester);
      final controller = await _controller(servers: 2);
      addTearDown(controller.dispose);
      await tester.pumpWidget(_app(controller));
      await tester.pumpAndSettle();
      await openSettingsGroup(tester, SettingsGroup.ai);
      await tester.tap(_row('settings-tools'));
      await tester.pumpAndSettle();
      expect(find.byType(ByServerScreen), findsOneWidget);
      expect(find.text(_en.settingsV3ToolsByServerNote), findsOneWidget);
    });

    testWidgets('Status and version sits under its title, never cut', (
      tester,
    ) async {
      await _tall(tester);
      final controller = await _controller(servers: 2);
      addTearDown(controller.dispose);
      await tester.pumpWidget(_app(controller));
      await tester.pumpAndSettle();
      await openSettingsGroup(tester, SettingsGroup.connections);
      await tester.tap(_row('settings-server-profile-2'));
      await tester.pumpAndSettle();
      final status = _row('server-row-status');
      final title = tester.getRect(
        find.descendant(
          of: status,
          matching: find.text(_en.settingsV3StatusVersion),
        ),
      );
      final value = tester.getRect(
        find.descendant(
          of: status,
          matching: find.text(_en.settingsV3NotConnected),
        ),
      );
      expect(value.top, greaterThanOrEqualTo(title.bottom));
    });

    testWidgets('a server page has Connection, In this app and On <server>', (
      tester,
    ) async {
      await _tall(tester);
      final controller = await _controller(servers: 2);
      addTearDown(controller.dispose);
      await tester.pumpWidget(_app(controller));
      await tester.pumpAndSettle();

      await openSettingsGroup(tester, SettingsGroup.connections);
      await tester.tap(_row('settings-server-profile-1'));
      await tester.pumpAndSettle();

      expect(find.text(_en.settingsV3SectionConnection), findsOneWidget);
      expect(find.text(_en.settingsV3SectionInApp), findsOneWidget);
      expect(find.text(_en.settingsV3SectionOn('Workstation')), findsOneWidget);
      // The server-owned section says whose it is before anything changes.
      expect(find.text(_en.settingsV3SavedOn('Workstation')), findsOneWidget);
      expect(find.text(_en.settingsV3ModelNew), findsOneWidget);
      // The title already says whose page it is: no repeated subtitle.
      expect(find.text(_en.settingsV3ModelNewOn('Workstation')), findsNothing);
      // The shell is Workstation's own setting: it is on this page, in the
      // section that says it is saved there, and not on the hub's pickers.
      expect(_row('default-shell-settings-entry'), findsOneWidget);
      // No override yet, so no way back is offered.
      expect(_row('server-use-my-default'), findsNothing);
    });

    testWidgets('a long model name never shortens the model row label', (
      tester,
    ) async {
      await _tall(tester);
      // The test font draws every letter 16 dp wide, so the label alone needs
      // 432 dp: give the page room for it, then only the value could cut it.
      tester.view.physicalSize = const Size(560, 3000);
      final controller = await _controller(servers: 2);
      addTearDown(controller.dispose);
      await controller.store.setModel(
        'profile-1',
        'some-provider',
        'a-very-long-model-name-that-goes-on-and-on-v12-preview',
      );
      await tester.pumpWidget(_app(controller));
      await tester.pumpAndSettle();
      await openSettingsGroup(tester, SettingsGroup.connections);
      await tester.tap(_row('settings-server-profile-1'));
      await tester.pumpAndSettle();
      final label = tester.widget<Text>(find.text(_en.settingsV3ModelNew));
      final box = tester.renderObject<RenderParagraph>(
        find.text(_en.settingsV3ModelNew),
      );
      expect(label.data, _en.settingsV3ModelNew);
      expect(box.didExceedMaxLines, isFalse);
      expect(box.size.height, lessThan(30));
    });

    testWidgets('a server the app is not connected to says what it can', (
      tester,
    ) async {
      await _tall(tester);
      final controller = await _controller(servers: 2);
      addTearDown(controller.dispose);
      await tester.pumpWidget(_app(controller));
      await tester.pumpAndSettle();

      await openSettingsGroup(tester, SettingsGroup.connections);
      await tester.tap(_row('settings-server-profile-2'));
      await tester.pumpAndSettle();

      // Pinned to the server it opened for: nothing of Workstation here.
      expect(find.text('Workstation'), findsNothing);
      expect(find.text(_en.settingsV3ConnectTo('Laptop')), findsOneWidget);
      // Under Connect: what connecting gives, not the "saved on" note that
      // belongs to the server's own settings (owner's emulator pass, 2229).
      expect(
        find.textContaining(_en.settingsV3ConnectToChange, findRichText: true),
        findsOneWidget,
      );
      expect(
        find.textContaining(
          _en.settingsV3SavedOn('Laptop'),
          findRichText: true,
        ),
        findsNothing,
      );
      expect(_row('server-disconnect'), findsNothing);
      expect(_row('server-row-providers'), findsNothing);
      expect(_row('server-remove'), findsOneWidget);
    });
  });

  group('rows line up', () {
    testWidgets('the Language chevron sits where its siblings\' chevrons do', (
      tester,
    ) async {
      await _tall(tester);
      final controller = await _controller();
      addTearDown(controller.dispose);
      await tester.pumpWidget(_app(controller));
      await tester.pumpAndSettle();
      await openSettingsGroup(tester, SettingsGroup.thisApp);

      double chevronRight(String rowKey) {
        final chevron = find.descendant(
          of: _row(rowKey),
          matching: find.byIcon(AppIconography.chevronRight),
        );
        expect(chevron, findsOneWidget, reason: rowKey);
        return tester.getTopRight(chevron).dx;
      }

      // Appearance is the neighbouring row that also ends in "value ›".
      expect(
        chevronRight('settings-category-language'),
        chevronRight('settings-category-appearance'),
      );
    });
  });

  group('overrides and the way back', () {
    testWidgets('a server that differs shows a count and Use my default', (
      tester,
    ) async {
      await _tall(tester);
      final controller = await _controller(servers: 2);
      // Disposed at the end, after the tree: the monitor keeps a timer.

      final monitor = controller.profileMonitor;
      await monitor.setRules(
        'profile-2',
        const ProfileNotifyRules(enabled: true, notifications: false),
      );
      await tester.pumpWidget(_app(controller));
      await tester.pumpAndSettle();

      // Like Slack's "Channel-specific notifications 1".
      await openSettingsGroup(tester, SettingsGroup.thisApp);
      expect(
        _valueOn(
          'settings-category-notifications',
          _en.settingsV3DifferentOn(1),
        ),
        findsOneWidget,
      );
      await closeSettingsGroup(tester);
      await openSettingsGroup(tester, SettingsGroup.connections);

      await tester.tap(_row('settings-server-profile-2'));
      await tester.pumpAndSettle();
      await tester.scrollUntilVisible(
        _row('server-use-my-default'),
        200,
        scrollable: find.byType(Scrollable).last,
      );
      expect(find.text(_en.settingsV3UseMyDefault), findsOneWidget);
      expect(
        find.text(_en.settingsV3UseMyDefaultDetail('Laptop')),
        findsOneWidget,
      );

      await tester.tap(_row('server-use-my-default'));
      await tester.pumpAndSettle();

      // Reset removes the difference; it does not write a value of its own.
      expect(monitor.rulesFor('profile-2').notifications, isTrue);
      expect(monitor.rulesFor('profile-2').enabled, isTrue);
      expect(_row('server-use-my-default'), findsNothing);
      await tester.pageBack();
      await tester.pumpAndSettle();
      await closeSettingsGroup(tester);
      await openSettingsGroup(tester, SettingsGroup.thisApp);
      expect(find.textContaining('Different on'), findsNothing);
      await tester.pumpWidget(const SizedBox.shrink());
      controller.dispose();
      await tester.pump();
    });
  });

  group('Use my default resets every difference the page shows', () {
    testWidgets('notifications, new-chat model and read sharing all go back', (
      tester,
    ) async {
      await _tall(tester);
      final controller = await _controller(servers: 2);
      final monitor = controller.profileMonitor;
      await monitor.setRules(
        'profile-2',
        const ProfileNotifyRules(enabled: true, notifications: false),
      );
      await controller.store.setModel('profile-2', 'anthropic', 'claude');
      // Servers that existed before consent was per server start with
      // sharing on; this one was switched off.
      await controller.store.appPreferences.readStateConsent.setEnabled(
        'profile-2',
        false,
      );
      await tester.pumpWidget(_app(controller));
      await tester.pumpAndSettle();

      await openSettingsGroup(tester, SettingsGroup.connections);
      await tester.tap(_row('settings-server-profile-2'));
      await tester.pumpAndSettle();
      await tester.scrollUntilVisible(
        _row('server-use-my-default'),
        200,
        scrollable: find.byType(Scrollable).last,
      );
      await tester.tap(_row('server-use-my-default'));
      await tester.pumpAndSettle();

      expect(monitor.rulesFor('profile-2').notifications, isTrue);
      expect(controller.store.modelFor('profile-2').$1, isNull);
      expect(
        controller.store.appPreferences.readStateConsent.enabled('profile-2'),
        isTrue,
      );
      expect(_row('server-use-my-default'), findsNothing);
      await tester.pumpWidget(const SizedBox.shrink());
      controller.dispose();
      await tester.pump();
    });

    testWidgets('a model difference alone offers the way back', (tester) async {
      await _tall(tester);
      final controller = await _controller(servers: 2);
      await controller.store.setModel('profile-2', 'anthropic', 'claude');
      await tester.pumpWidget(_app(controller));
      await tester.pumpAndSettle();
      await openSettingsGroup(tester, SettingsGroup.connections);
      await tester.tap(_row('settings-server-profile-2'));
      await tester.pumpAndSettle();
      await tester.scrollUntilVisible(
        _row('server-use-my-default'),
        200,
        scrollable: find.byType(Scrollable).last,
      );
      expect(_row('server-use-my-default'), findsOneWidget);
      await tester.pumpWidget(const SizedBox.shrink());
      controller.dispose();
      await tester.pump();
    });
  });

  group('Use my default for read sharing', () {
    testWidgets('off, then reset, is on again after reopening the page', (
      tester,
    ) async {
      await _tall(tester);
      final controller = await _controller(servers: 2);
      final consent = controller.store.appPreferences.readStateConsent;
      expect(consent.enabled('profile-2'), isTrue);
      await tester.pumpWidget(_app(controller));
      await tester.pumpAndSettle();
      await openSettingsGroup(tester, SettingsGroup.connections);

      bool shareRead() =>
          tester.widget<KitSwitchRow>(_row('server-row-share-read')).value;

      await tester.tap(_row('settings-server-profile-2'));
      await tester.pumpAndSettle();
      await tester.scrollUntilVisible(
        _row('server-row-share-read'),
        200,
        scrollable: find.byType(Scrollable).last,
      );
      expect(shareRead(), isTrue);
      expect(_row('server-use-my-default'), findsNothing);

      await tester.tap(_row('server-row-share-read'));
      await tester.pumpAndSettle();
      expect(shareRead(), isFalse);
      await tester.scrollUntilVisible(
        _row('server-use-my-default'),
        200,
        scrollable: find.byType(Scrollable).last,
      );
      await tester.tap(_row('server-use-my-default'));
      await tester.pumpAndSettle();

      expect(_row('server-use-my-default'), findsNothing);
      // The page says what it put back, naming the server.
      await tester.scrollUntilVisible(
        find.textContaining('Laptop to your own choices'),
        -200,
        scrollable: find.byType(Scrollable).last,
      );
      expect(find.textContaining(_en.settingsV3ShareRead), findsWidgets);
      expect(shareRead(), isTrue);

      await tester.pageBack();
      await tester.pumpAndSettle();
      await tester.tap(_row('settings-server-profile-2'));
      await tester.pumpAndSettle();
      await tester.scrollUntilVisible(
        _row('server-row-share-read'),
        200,
        scrollable: find.byType(Scrollable).last,
      );
      expect(shareRead(), isTrue);
      expect(consent.enabled('profile-2'), isTrue);
      await tester.pumpWidget(const SizedBox.shrink());
      controller.dispose();
      await tester.pump();
    });
  });

  group('a server page keeps to its server', () {
    testWidgets('its note is the On-server section\'s, not loose page text', (
      tester,
    ) async {
      await _tall(tester);
      final controller = await _controller(servers: 2);
      final handle = tester.ensureSemantics();
      await tester.pumpWidget(
        _app(
          controller,
          home: ServerPageScreen(
            controller: controller,
            profileId: 'profile-1',
          ),
        ),
      );
      await tester.pumpAndSettle();

      final note = _en.settingsV3SavedOn('Workstation');
      // Seen, but not read as a page of its own.
      expect(find.text(note), findsOneWidget);
      expect(find.bySemanticsLabel(note), findsNothing);
      final section = tester.getSemantics(
        find.byKey(const ValueKey('server-section-on-semantics')),
      );
      expect(section.hint, note);
      handle.dispose();
      await tester.pumpWidget(const SizedBox.shrink());
      controller.dispose();
      await tester.pump();
    });

    testWidgets('Notifications opens only this server\'s choices', (
      tester,
    ) async {
      await _tall(tester);
      final controller = await _controller(servers: 2);
      await tester.pumpWidget(
        _app(
          controller,
          home: ServerPageScreen(
            controller: controller,
            profileId: 'profile-2',
          ),
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(_row('server-row-notifications'));
      await tester.pumpAndSettle();

      expect(_row('monitor-enabled-profile-2'), findsOneWidget);
      expect(_row('monitor-enabled-profile-1'), findsNothing);
      expect(find.text(_en.notifySectionWhat), findsNothing);
      // The general page is one tap away.
      await tester.tap(_row('notifications-all-servers-link'));
      await tester.pumpAndSettle();
      expect(_row('monitor-enabled-profile-1'), findsOneWidget);
      expect(_row('monitor-enabled-profile-2'), findsOneWidget);
      await tester.pumpWidget(const SizedBox.shrink());
      controller.dispose();
      await tester.pump();
    });
  });

  group('model names read alike connected or not', () {
    test('ids are tidied to a short name and its provider', () {
      expect(
        readableModelLabel('openai', 'gpt-6.1-sol'),
        'GPT-6.1 Sol · OpenAI',
      );
      expect(
        readableModelLabel('zai-coding-plan', 'glm-5.3'),
        'GLM-5.3 · Z.AI Coding Plan',
      );
      expect(
        readableModelLabel('anthropic', 'claude-opus-4-5'),
        'Claude Opus 4.5 · Anthropic',
      );
      // A loaded catalog's own names win.
      expect(
        readableModelLabel(
          'acme',
          'x1',
          modelName: 'Acme One',
          providerName: 'Acme Labs',
        ),
        'Acme One · Acme Labs',
      );
    });

    testWidgets('a server that is not connected shows a name, not an id', (
      tester,
    ) async {
      await _tall(tester);
      final controller = await _controller(servers: 2);
      await controller.store.setModel('profile-2', 'openai', 'gpt-6.1-sol');
      await tester.pumpWidget(
        _app(
          controller,
          home: ServerPageScreen(
            controller: controller,
            profileId: 'profile-2',
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('GPT-6.1 Sol · OpenAI'), findsOneWidget);
      expect(find.text('openai/gpt-6.1-sol'), findsNothing);
      await tester.pumpWidget(const SizedBox.shrink());
      controller.dispose();
      await tester.pump();
    });
  });

  group('Agents is one group of three rows that state their state', () {
    testWidgets('each row says what it is and where it stands', (tester) async {
      await _tall(tester);
      final controller = await _controller();
      addTearDown(controller.dispose);
      await tester.pumpWidget(
        _app(controller, home: AgentsHubScreen(controller: controller)),
      );
      await tester.pumpAndSettle();
      // No group label repeats a row's own title.
      expect(find.text(_en.settingsV3AgentsPhone), findsNothing);
      expect(find.text(_en.settingsV3AgentsOnPhone), findsOneWidget);
      expect(find.text(_en.a2aTitle), findsOneWidget);
      expect(find.text(_en.settingsV3AgentsExternalCount(0)), findsOneWidget);
      expect(find.byType(KitRowGroup), findsOneWidget);
      await tester.pumpWidget(const SizedBox.shrink());
    });
  });

  group('search deep links', () {
    Future<void> openEntry(
      WidgetTester tester,
      ConnectionController controller,
      String id,
    ) async {
      final scope = SearchScope(controller: controller);
      final entry = searchIndex(_en, scope).firstWhere((e) => e.id == id);
      unawaited(entry.open(tester.element(find.byType(SettingsScreen)), scope));
      await tester.pumpAndSettle();
    }

    testWidgets('quiet hours land on Notifications, not Background', (
      tester,
    ) async {
      final controller = await _controller();
      addTearDown(controller.dispose);
      await tester.pumpWidget(_app(controller));
      await tester.pumpAndSettle();

      await openEntry(tester, controller, 'inside-notifications-quiet');
      expect(find.byType(NotificationsSettingsScreen), findsOneWidget);
      expect(_row('notifications-section-quiet'), findsOneWidget);
      expect(_row('notifications-section-background'), findsNothing);
    });

    testWidgets('voice typing opens as a page, not a sheet', (tester) async {
      await _tall(tester);
      final controller = await _controller();
      addTearDown(controller.dispose);
      await tester.pumpWidget(_app(controller));
      await tester.pumpAndSettle();

      final scope = SearchScope(controller: controller);
      final entry = searchIndex(
        _en,
        scope,
      ).firstWhere((e) => e.id == 'settings-voice');
      final models = await ScriptedVoiceModels.create(installed: {});
      addTearDown(models.dispose);
      voiceManagerLoader = () async => models;
      addTearDown(() => voiceManagerLoader = VoiceModelManager.shared);
      unawaited(entry.open(tester.element(find.byType(SettingsScreen)), scope));
      await tester.pumpAndSettle();
      expect(find.byType(VoiceSettingsScreen), findsOneWidget);
      expect(find.byType(KitSheet), findsNothing);
      expect(find.text(_en.settingsV3VoiceTyping), findsWidgets);
    });

    testWidgets('keep running lands on Background', (tester) async {
      final controller = await _controller();
      addTearDown(controller.dispose);
      await tester.pumpWidget(_app(controller));
      await tester.pumpAndSettle();

      await openEntry(tester, controller, 'settings-keep-running');
      expect(find.byType(NotificationsSettingsScreen), findsOneWidget);
      expect(_row('notifications-section-quiet'), findsNothing);
      expect(find.text(_en.settingsV3Background), findsWidgets);
    });

    testWidgets('permissions land on their own page under Chat', (
      tester,
    ) async {
      final controller = await _controller();
      addTearDown(controller.dispose);
      await tester.pumpWidget(_app(controller));
      await tester.pumpAndSettle();

      await openEntry(tester, controller, 'settings-automation');
      expect(find.byType(PermissionsAutomationScreen), findsOneWidget);
      expect(find.text(_en.settingsV3Permissions), findsWidgets);
    });

    testWidgets('language opens the hub at its row, and Appearance has none', (
      tester,
    ) async {
      final controller = await _controller();
      addTearDown(controller.dispose);
      await tester.pumpWidget(_app(controller));
      await tester.pumpAndSettle();

      await openEntry(tester, controller, 'inside-appearance-language');
      expect(find.byType(SettingsScreen), findsWidgets);
      expect(_row('settings-category-language'), findsOneWidget);

      await tester.pumpWidget(const SizedBox());
      await tester.pumpWidget(
        _app(
          controller,
          home: AppearanceSettingsScreen(controller: controller),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text(_en.e7LocaleUiLanguage), findsNothing);
    });

    testWidgets('the launcher finds Servers and the model by their new names', (
      tester,
    ) async {
      final controller = await _controller();
      addTearDown(controller.dispose);
      final scope = SearchScope(controller: controller);
      List<String> found(String query) => [
        for (final e in searchEntries(_en, scope, query)) e.id,
      ];
      expect(found('servers'), contains('settings-saved-servers'));
      expect(
        found('shell for commands'),
        contains('default-shell-settings-entry'),
      );
      expect(found('voice typing'), contains('settings-voice'));
      expect(
        found('permissions and automation'),
        contains('settings-automation'),
      );
      // The retired names still find their rows.
      expect(found('default shell'), contains('default-shell-settings-entry'));
      expect(found('this server'), contains('settings-category-server'));
    });
  });

  group('round 2', () {
    testWidgets('the Settings tab has no server switcher chip', (tester) async {
      // The rail's Settings destination (a tablet); a phone opens Settings
      // as a page from the gear instead.
      tester.view
        ..physicalSize = const Size(800, 1280)
        ..devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final controller = await _controller(servers: 2);
      addTearDown(controller.dispose);
      await tester.pumpWidget(
        ProviderScope(
          overrides: [connProvider.overrideWithValue(controller)],
          child: MaterialApp(
            theme: AppTheme.dark(),
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            home: const HomeScreen(initialTab: 2),
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(settingsDoor(SettingsGroup.ai), findsOneWidget);
      // The tab starts with its title, like Chats and Files.
      expect(find.byKey(const ValueKey('settings-tab-title')), findsOneWidget);
      expect(
        find.descendant(
          of: find.byKey(const ValueKey('settings-tab-title')),
          matching: find.text(_en.librarySettingsTitle),
        ),
        findsOneWidget,
      );
      expect(
        find.byKey(const ValueKey('server-switcher-button')),
        findsNothing,
      );
      // Another tab keeps it.
      await tester.tap(find.byKey(const ValueKey('home-shell-tab-chats')));
      await tester.pumpAndSettle();
      expect(
        find.byKey(const ValueKey('server-switcher-button')),
        findsOneWidget,
      );
    });

    testWidgets(
      'read state is a per-server consent: switching one server leaves the other',
      (tester) async {
        await _tall(tester);
        final controller = await _controller(servers: 2);
        addTearDown(controller.dispose);
        final consent = controller.store.appPreferences.readStateConsent;
        await tester.pumpWidget(
          _app(
            controller,
            home: ServerPageScreen(
              controller: controller,
              profileId: 'profile-2',
            ),
          ),
        );
        await tester.pumpAndSettle();
        // Servers saved before the per-server rule keep their old answer.
        expect(consent.enabled('profile-2'), isTrue);
        final row = _row('server-row-share-read');
        await tester.scrollUntilVisible(
          row,
          200,
          scrollable: find.byType(Scrollable).last,
        );
        await tester.tap(row);
        // The acknowledgment is real storage work, not a frame.
        await tester.runAsync(
          () => Future<void>.delayed(const Duration(milliseconds: 200)),
        );
        await tester.pumpAndSettle();
        // Only the server whose page this is, though it is not connected.
        expect(consent.enabled('profile-2'), isFalse);
        expect(consent.enabled('profile-1'), isTrue);
      },
    );

    testWidgets('the server page shows the model by name, one icon-free row', (
      tester,
    ) async {
      await _tall(tester);
      final controller = await _controller();
      addTearDown(controller.dispose);
      await controller.store.setModel('profile-1', 'opencode', 'big-pickle');
      controller.catalog = const CatalogSnapshot(
        providers: [],
        agents: [],
        models: [
          CatalogModel(
            id: 'big-pickle',
            providerID: 'opencode',
            name: 'Big Pickle',
            enabled: true,
            status: 'active',
            contextLimit: 1000,
            outputLimit: 100,
            reasoning: false,
            attachments: false,
            tools: true,
            variants: [],
          ),
        ],
      );
      await tester.pumpWidget(
        _app(
          controller,
          home: ServerPageScreen(
            controller: controller,
            profileId: 'profile-1',
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Big Pickle · OpenCode'), findsOneWidget);
      expect(find.textContaining('opencode/'), findsNothing);
      expect(find.text('Shell for commands'), findsOneWidget);
      expect(
        find.descendant(
          of: _row('default-shell-settings-entry'),
          matching: find.byType(KitRowIcon),
        ),
        findsNothing,
      );
    });
  });

  group('Arabic and tablet', () {
    testWidgets('Arabic mirrors the rows and shows the new labels', (
      tester,
    ) async {
      await _tall(tester);
      final controller = await _controller(servers: 2);
      addTearDown(controller.dispose);
      await tester.pumpWidget(_app(controller, locale: const Locale('ar')));
      await tester.pumpAndSettle();

      expect(find.text(_ar.settingsHubServer), findsOneWidget);
      await openSettingsGroup(tester, SettingsGroup.connections);
      expect(find.text(_ar.settingsV3ServersBlock), findsOneWidget);
      await closeSettingsGroup(tester);
      await openSettingsGroup(tester, SettingsGroup.ai);
      expect(
        _valueOn('settings-model-and-mode', _ar.settingsV3ByServer),
        findsOneWidget,
      );
      // Mirrored: the name sits at the right edge, the value at the left.
      final title = find.descendant(
        of: _row('settings-model-and-mode'),
        matching: find.text(_ar.settingsHubModelRow),
      );
      final value = _valueOn('settings-model-and-mode', _ar.settingsV3ByServer);
      expect(
        tester.getCenter(title).dx,
        greaterThan(tester.getCenter(value).dx),
      );
      expect(tester.takeException(), isNull);
    });

    testWidgets(
      'a tablet keeps the five words on the left, a group on the right',
      (tester) async {
        tester.view
          ..physicalSize = const Size(1280, 800)
          ..devicePixelRatio = 1;
        addTearDown(tester.view.reset);
        final controller = await _controller(servers: 2);
        addTearDown(controller.dispose);
        await tester.pumpWidget(_app(controller));
        await tester.pumpAndSettle();

        final list = find.byKey(const ValueKey('settings-list-pane'));
        final detail = find.byKey(const ValueKey('settings-detail-pane'));
        expect(list, findsOneWidget);
        // Server is open first; its page lists each server.
        expect(
          find.descendant(
            of: detail,
            matching: _row('settings-server-profile-2'),
          ),
          findsOneWidget,
        );
        await tester.tap(settingsDoor(SettingsGroup.ai));
        await tester.pumpAndSettle();
        expect(
          find.descendant(
            of: detail,
            matching: _row('settings-model-and-mode'),
          ),
          findsOneWidget,
        );
        expect(tester.takeException(), isNull);
      },
    );
  });
}
