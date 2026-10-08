// Behaviour of screen-library-2's pages (wave 2b): Commands & tools explains
// its gates instead of dropping them (P7.4), Add MCP server explains a
// server that cannot add one and asks before discarding what was typed, and
// External agents lists agents by urgency with removal on the row, named and
// confirmed. The task and draft journey lives in
// test/external_agent_widget_test.dart.
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api2/gateway_mappers.dart'
    show api2ServerCapabilities;
import 'package:opencode_mobile/domain/external_agent.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/external_agents.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/capabilities_screen.dart';
import 'package:opencode_mobile/ui/screens/external_agents_screen.dart';
import 'package:opencode_mobile/ui/screens/mcp_setup_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../external_agent_state_test.dart'
    show FakeExternalGateway, agentCard, waitingTask;

class _Repository implements ProductRepository {
  McpServerDraft? added;

  @override
  void setLocation({String? directory, String? workspace}) {}

  @override
  Future<List<CommandInfo>> listCommands() async => const [];

  @override
  Future<List<SkillInfo>> listSkills() async => const [];

  @override
  Future<List<ReferenceInfo>> listReferences() async => const [];

  @override
  Future<void> addMcpServer(
    McpServerDraft draft, {
    required McpConfigScope scope,
  }) async => added = draft;

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

final _laptop = ServerProfile(
  id: 'laptop',
  name: 'Laptop',
  baseUrl: 'https://laptop.example',
);

class _Controller extends ConnectionController {
  _Controller(super.store, this.repo, this.caps);

  final _Repository repo;
  final ServerCapabilities caps;

  @override
  ServerCapabilities get capabilities => caps;

  @override
  ServerProfile? get profile => _laptop;

  @override
  Future<ProductRepository?> prepareActionRepository() async => repo;

  @override
  Future<void> reloadAfterConfigurationChange() async {}
}

Future<_Controller> _controller(ServerCapabilities caps) async {
  SharedPreferences.setMockInitialValues({});
  final preferences = await SharedPreferences.getInstance();
  return _Controller(ProfileStore(prefs: preferences), _Repository(), caps)
    ..directory = '/work/app';
}

Widget _app(Widget home) => MaterialApp(
  theme: AppTheme.light(),
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  home: home,
);

/// Pushes [page] over a host so Back has somewhere to go.
Widget _host(Widget page) => _app(
  Builder(
    builder: (context) => KitScreen(
      topBar: const KitTopBar(title: 'Host'),
      body: Center(
        child: KitButton.primary(
          label: 'Open page',
          onPressed: () => pushKitPage<void>(context, (_) => page),
        ),
      ),
    ),
  ),
);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('Tools', () {
    testWidgets('keeps the Tools tab and says why on a server without it', (
      tester,
    ) async {
      final controller = await _controller(api2ServerCapabilities);
      addTearDown(controller.dispose);
      await tester.pumpWidget(_app(CapabilitiesScreen(controller: controller)));
      await tester.pumpAndSettle();

      for (final tab in const [
        'MCP',
        'Commands',
        'Tools',
        'Skills',
        'References',
        'External agents',
      ]) {
        expect(find.byKey(ValueKey('capabilities-tab-$tab')), findsOneWidget);
      }
      await tester.tap(find.byKey(const ValueKey('capabilities-tab-Tools')));
      await tester.pumpAndSettle();
      expect(
        find.byKey(const ValueKey('capabilities-tools-unavailable')),
        findsOneWidget,
      );
      expect(find.text("Laptop doesn't list its tools"), findsOneWidget);
      expect(tester.takeException(), isNull);
    });

    testWidgets('a link counted without Tools still lands on its tab', (
      tester,
    ) async {
      final controller = await _controller(api2ServerCapabilities);
      addTearDown(controller.dispose);
      // Search counts Skills as 1 on a server without a tool list.
      await tester.pumpWidget(
        _app(CapabilitiesScreen(controller: controller, initialTab: 1)),
      );
      await tester.pumpAndSettle();
      // MCP · Commands · Tools · Skills: Skills is the fourth tab.
      expect(
        tester.widget<KitTabSwitcher>(find.byType(KitTabSwitcher)).index,
        3,
      );
    });

    testWidgets('a section can be asked for by name', (tester) async {
      final controller = await _controller(ServerCapabilities.allV1);
      addTearDown(controller.dispose);
      await tester.pumpWidget(
        _app(
          CapabilitiesScreen(
            controller: controller,
            initialSection: ToolsSection.references,
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(
        tester.widget<KitTabSwitcher>(find.byType(KitTabSwitcher)).index,
        4,
      );
      // Only the opened tab is built; the others wait for a first visit.
      expect(
        find.byKey(const ValueKey('tools-section-references')),
        findsOneWidget,
      );
      expect(find.byKey(const ValueKey('tools-section-mcp')), findsNothing);
      await tester.tap(find.byKey(const ValueKey('capabilities-tab-MCP')));
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('tools-section-mcp')), findsOneWidget);
      // A visited tab is kept.
      expect(
        find.byKey(
          const ValueKey('tools-section-references'),
          skipOffstage: false,
        ),
        findsOneWidget,
      );
    });

    testWidgets('a server without a catalog keeps External agents, says why', (
      tester,
    ) async {
      final controller = await _controller(
        const ServerCapabilities(serverCatalog: false),
      );
      addTearDown(controller.dispose);
      await tester.pumpWidget(_app(CapabilitiesScreen(controller: controller)));
      await tester.pumpAndSettle();
      expect(find.byType(KitTabSwitcher), findsNothing);
      expect(
        find.byKey(const ValueKey('tools-section-externalAgents')),
        findsOneWidget,
      );
      expect(find.byKey(const ValueKey('tools-unavailable')), findsOneWidget);
      expect(
        find.byKey(const ValueKey('capabilities-unavailable')),
        findsNothing,
      );
    });
  });

  group('Add MCP server', () {
    testWidgets('a server that cannot add one explains instead of a form', (
      tester,
    ) async {
      final controller = await _controller(
        const ServerCapabilities(mcpConfigWrites: false, mcpRuntimeAdds: false),
      );
      addTearDown(controller.dispose);
      await tester.pumpWidget(_app(McpSetupScreen(controller: controller)));
      await tester.pumpAndSettle();
      expect(
        find.byKey(const ValueKey('mcp-setup-unavailable')),
        findsOneWidget,
      );
      expect(find.byKey(const ValueKey('mcp-name')), findsNothing);
    });

    testWidgets('Save names what is missing and adds nothing', (tester) async {
      final controller = await _controller(ServerCapabilities.allV1);
      addTearDown(controller.dispose);
      await tester.pumpWidget(_app(McpSetupScreen(controller: controller)));
      await tester.pumpAndSettle();
      expect(find.text('Enter a server name'), findsNothing);
      await tester.tap(find.byKey(const ValueKey('mcp-save')));
      await tester.pumpAndSettle();
      expect(find.text('Enter a server name'), findsOneWidget);
      expect(controller.repo.added, isNull);
    });

    testWidgets('Back with typed input asks before discarding it', (
      tester,
    ) async {
      final controller = await _controller(ServerCapabilities.allV1);
      addTearDown(controller.dispose);
      await tester.pumpWidget(_host(McpSetupScreen(controller: controller)));
      await tester.tap(find.text('Open page'));
      await tester.pumpAndSettle();
      await tester.enterText(find.byKey(const ValueKey('mcp-name')), 'docs');
      await tester.pump();

      await tester.binding.handlePopRoute();
      await tester.pumpAndSettle();
      expect(find.text('Discard this MCP server?'), findsOneWidget);
      await tester.tap(find.text('Keep editing'));
      await tester.pumpAndSettle();
      expect(find.byType(McpSetupScreen), findsOneWidget);

      await tester.binding.handlePopRoute();
      await tester.pumpAndSettle();
      await tester.tap(find.text('Discard server'));
      await tester.pumpAndSettle();
      expect(find.byType(McpSetupScreen), findsNothing);
    });
  });

  group('External agents', () {
    late ExternalAgentStore store;
    setUp(() async {
      final secrets = <String, String>{};
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(
            const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
            (call) async {
              final args = Map<String, dynamic>.from(call.arguments as Map);
              switch (call.method) {
                case 'write':
                  secrets[args['key'] as String] = args['value'] as String;
                  return null;
                case 'read':
                  return secrets[args['key']];
                case 'delete':
                  secrets.remove(args['key']);
                  return null;
                default:
                  return null;
              }
            },
          );
      SharedPreferences.setMockInitialValues({});
      store = ExternalAgentStore(
        await SharedPreferences.getInstance(),
        const FlutterSecureStorage(),
      );
    });
    tearDown(() => store.dispose());

    testWidgets('an agent waiting for a reply comes first and says so', (
      tester,
    ) async {
      const quiet = ExternalAgentCard(
        name: 'Quiet agent',
        description: 'Answers later',
        cardUrl: 'https://quiet.example/.well-known/agent-card.json',
        endpoint: 'https://quiet.example/rpc',
        version: '1',
        auth: ExternalAgentAuth.none,
        supported: true,
        skills: [],
      );
      final asking = await store.add(agentCard, 'fixture-token');
      await store.add(quiet, '');
      await store.saveTask(
        asking.id,
        ExternalTaskRecord(
          localId: 'waiting',
          title: 'Choose a color',
          created: DateTime(2026, 9, 27),
          task: waitingTask,
        ),
      );
      await tester.pumpWidget(_app(ExternalAgentsScreen(store: store)));
      await tester.pumpAndSettle();

      final color = tester.getTopLeft(find.text('Color agent'));
      final later = tester.getTopLeft(find.text('Quiet agent'));
      // Quiet was added last (newest), yet the agent that needs a reply
      // leads the one list.
      expect(color.dy, lessThan(later.dy));
      expect(find.textContaining('Needs you'), findsOneWidget);
      // The yellow mark leads the row that waits, and only that row.
      final waitingRow = find.byType(KitRow).first;
      expect(
        find.descendant(of: waitingRow, matching: find.byType(KitTaskMark)),
        findsOneWidget,
      );
      expect(
        find.descendant(
          of: find.ancestor(
            of: find.text('Quiet agent'),
            matching: find.byType(KitRow),
          ),
          matching: find.byType(KitTaskMark),
        ),
        findsNothing,
      );
    });

    testWidgets('removal is on the row, names the agent and confirms', (
      tester,
    ) async {
      await store.add(agentCard, 'fixture-token');
      await tester.pumpWidget(
        _app(
          ExternalAgentsScreen(
            store: store,
            gatewayFactory: FakeExternalGateway.new,
          ),
        ),
      );
      await tester.pumpAndSettle();

      await tester.longPress(find.text('Color agent'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Remove Color agent from this phone'));
      await tester.pumpAndSettle();
      expect(find.text('Remove Color agent?'), findsOneWidget);
      await tester.tap(
        find.byKey(const ValueKey('external-agent-remove-confirm')),
      );
      await tester.pumpAndSettle();
      expect(store.profiles, isEmpty);
      expect(
        find.byKey(const ValueKey('external-agents-empty')),
        findsOneWidget,
      );
    });
  });
}
