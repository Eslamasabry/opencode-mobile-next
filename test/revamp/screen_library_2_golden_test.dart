// Golden renders of screen-library-2's pages (wave 2b): Commands & tools
// with its explained gates, Add MCP server (form, until-restart, not
// available, discard question), External agents (list by urgency, empty,
// removal question), Add agent after the check, an agent's page, and a
// task (draft, needs a reply, stop question). Phone 412x915 and one wide
// window (1280x800) for the main pages, dark and light (owner decision
// 2026-09-27: no Arabic), with the app's real fonts at DPR 1.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/revamp/screen_library_2_golden_test.dart
// and look at every changed image before committing it.
import '../support/fake_server_capabilities.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/product_repository.dart';
import 'package:opencode_mobile/api2/gateway_mappers.dart'
    show api2ServerCapabilities;
import 'package:opencode_mobile/domain/external_agent.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/external_agents.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/capabilities_screen.dart';
import 'package:opencode_mobile/ui/screens/external_agents_screen.dart';
import 'package:opencode_mobile/ui/screens/mcp_setup_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../tool/capture/fixtures.dart' show captureTheme, loadCaptureFonts;
import '../external_agent_state_test.dart'
    show FakeExternalGateway, agentCard, waitingTask, completeTask;

class _Repository implements ProductRepository {
  @override
  void setLocation({String? directory, String? workspace}) {}

  @override
  Future<List<CommandInfo>> listCommands() async => const [];

  @override
  Future<List<SkillInfo>> listSkills() async => const [];

  @override
  Future<List<ReferenceInfo>> listReferences() async => const [];

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

final _laptop = ServerProfile(
  id: 'laptop',
  name: 'Laptop',
  baseUrl: 'https://laptop.example',
);

class _Controller extends ConnectionController {
  _Controller(super.store, this.caps);

  final ServerCapabilities caps;
  final _repo = _Repository();

  @override
  ServerCapabilities get capabilities => caps;

  @override
  ServerProfile? get profile => _laptop;

  @override
  Future<ProductRepository?> prepareActionRepository() async => _repo;
}

Future<_Controller> _server(ServerCapabilities caps) async {
  SharedPreferences.setMockInitialValues({});
  final preferences = await SharedPreferences.getInstance();
  return _Controller(ProfileStore(prefs: preferences), caps)
    ..directory = '/home/me/projects/mobile';
}

const _phone = Size(412, 915);
const _wide = Size(1280, 800);

String _name(String shot, Size size, bool light) => [
  shot,
  if (size != _phone) '${size.width.toInt()}x${size.height.toInt()}',
  light ? 'light' : 'dark',
].join('_');

Future<void> _shot(
  WidgetTester tester,
  String shot, {
  required bool light,
  required Widget home,
  Size size = _phone,
  Future<void> Function()? act,
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  debugDefaultTargetPlatformOverride = TargetPlatform.android;
  final boundary = GlobalKey();
  try {
    await tester.pumpWidget(
      RepaintBoundary(
        key: boundary,
        child: MaterialApp(
          debugShowCheckedModeBanner: false,
          theme: captureTheme(light: light),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(context).copyWith(disableAnimations: true),
            child: child!,
          ),
          home: home,
        ),
      ),
    );
    await tester.pumpAndSettle();
    if (act != null) {
      await act();
      await tester.pumpAndSettle();
    }
    expect(tester.takeException(), isNull);
    await expectLater(
      find.byKey(boundary),
      matchesGoldenFile('goldens/${_name(shot, size, light)}.png'),
    );
  } finally {
    await tester.pumpWidget(const SizedBox.shrink());
    debugDefaultTargetPlatformOverride = null;
  }
}

const _quiet = ExternalAgentCard(
  name: 'Palette agent',
  description: 'Suggests colour palettes for a brand or a screen.',
  cardUrl: 'https://palette.example/.well-known/agent-card.json',
  endpoint: 'https://palette.example/rpc',
  version: '2.1',
  auth: ExternalAgentAuth.none,
  supported: true,
  skills: [
    ExternalAgentSkill(
      'Palette from a brief',
      'Five colours with contrast notes.',
    ),
  ],
);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);

  late ExternalAgentStore store;
  setUp(() async {
    final secrets = <String, String>{};
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (call) async {
            final args = Map<String, dynamic>.from(
              (call.arguments as Map?) ?? const {},
            );
            switch (call.method) {
              case 'write':
                secrets[args['key'] as String] = args['value'] as String;
                return null;
              case 'read':
                return secrets[args['key']];
              case 'readAll':
                return <String, String>{};
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

  /// Two agents: Color agent has a task waiting for a reply and one done;
  /// Palette agent was added last.
  Future<ExternalAgentProfile> seed() async {
    final color = await store.add(agentCard, 'fixture-token');
    await store.add(_quiet, '');
    await store.saveTask(
      color.id,
      ExternalTaskRecord(
        localId: 'done',
        title: 'Name the three accent colours',
        created: DateTime(2026, 9, 26),
        task: completeTask,
      ),
    );
    await store.saveTask(
      color.id,
      ExternalTaskRecord(
        localId: 'waiting',
        title: 'Choose a colour for the welcome screen',
        created: DateTime(2026, 9, 27),
        task: waitingTask,
      ),
    );
    return color;
  }

  for (final light in [false, true]) {
    final tone = light ? 'light' : 'dark';

    group('capabilities ($tone)', () {
      for (final size in [_phone, _wide]) {
        testWidgets('tools not listed ${size.width.toInt()}', (tester) async {
          final controller = await _server(api2ServerCapabilities);
          addTearDown(controller.dispose);
          await _shot(
            tester,
            'library2_capabilities_tools_unavailable',
            light: light,
            size: size,
            home: CapabilitiesScreen(controller: controller),
            act: () => tester.tap(
              find.byKey(const ValueKey('capabilities-tab-Built-in')),
            ),
          );
        });
      }

      testWidgets('catalog not shared', (tester) async {
        final controller = await _server(
          const ServerCapabilities(serverCatalog: false),
        );
        addTearDown(controller.dispose);
        await _shot(
          tester,
          'library2_capabilities_unavailable',
          light: light,
          home: CapabilitiesScreen(controller: controller),
        );
      });
    });

    group('mcp setup ($tone)', () {
      for (final size in [_phone, _wide]) {
        testWidgets('form ${size.width.toInt()}', (tester) async {
          final controller = await _server(testServerCapabilities);
          addTearDown(controller.dispose);
          await _shot(
            tester,
            'library2_mcp_setup_form',
            light: light,
            size: size,
            home: McpSetupScreen(controller: controller),
          );
        });
      }

      testWidgets('until restart', (tester) async {
        final controller = await _server(api2ServerCapabilities);
        addTearDown(controller.dispose);
        await _shot(
          tester,
          'library2_mcp_setup_runtime',
          light: light,
          home: McpSetupScreen(controller: controller),
        );
      });

      testWidgets('errors after Save', (tester) async {
        final controller = await _server(testServerCapabilities);
        addTearDown(controller.dispose);
        await _shot(
          tester,
          'library2_mcp_setup_errors',
          light: light,
          home: McpSetupScreen(controller: controller),
          act: () => tester.tap(find.byKey(const ValueKey('mcp-save'))),
        );
      });

      testWidgets('not available', (tester) async {
        final controller = await _server(
          const ServerCapabilities(
            mcpConfigWrites: false,
            mcpRuntimeAdds: false,
          ),
        );
        addTearDown(controller.dispose);
        await _shot(
          tester,
          'library2_mcp_setup_unavailable',
          light: light,
          home: McpSetupScreen(controller: controller),
        );
      });
    });

    group('external agents ($tone)', () {
      for (final size in [_phone, _wide]) {
        testWidgets('list ${size.width.toInt()}', (tester) async {
          await seed();
          await _shot(
            tester,
            'library2_external_agents_list',
            light: light,
            size: size,
            home: ExternalAgentsScreen(store: store),
          );
        });
      }

      testWidgets('empty', (tester) async {
        await _shot(
          tester,
          'library2_external_agents_empty',
          light: light,
          home: ExternalAgentsScreen(store: store),
        );
      });

      testWidgets('remove question', (tester) async {
        await seed();
        await _shot(
          tester,
          'library2_external_agents_remove_sheet',
          light: light,
          home: ExternalAgentsScreen(store: store),
          act: () async {
            await tester.longPress(find.text('Palette agent'));
            await tester.pumpAndSettle();
            await tester.tap(find.text('Remove Palette agent from this phone'));
          },
        );
      });

      testWidgets('add agent checked', (tester) async {
        final gateway = FakeExternalGateway();
        await _shot(
          tester,
          'library2_add_agent_checked',
          light: light,
          home: ExternalAgentsScreen(
            store: store,
            gatewayFactory: () => gateway,
          ),
          act: () async {
            await tester.tap(find.text('Add agent'));
            await tester.pumpAndSettle();
            await tester.enterText(
              find.byKey(const ValueKey('external-agent-address')),
              'https://agent.example',
            );
            await tester.pump();
            await tester.tap(find.text('Check agent'));
            await tester.pumpAndSettle();
            FocusManager.instance.primaryFocus?.unfocus();
          },
        );
      });

      testWidgets('agent page', (tester) async {
        final color = await seed();
        await _shot(
          tester,
          'library2_external_agent_detail',
          light: light,
          home: ExternalAgentDetailScreen(store: store, profile: color),
        );
      });

      testWidgets('task needs a reply', (tester) async {
        final color = await seed();
        final gateway = FakeExternalGateway()..next = waitingTask;
        await _shot(
          tester,
          'library2_external_task_reply',
          light: light,
          home: ExternalTaskScreen(
            store: store,
            profile: color,
            record: store
                .tasks(color.id)
                .firstWhere((r) => r.localId == 'waiting'),
            gateway: gateway,
          ),
        );
      });

      testWidgets('task draft', (tester) async {
        final color = await seed();
        final draft = ExternalTaskRecord(
          localId: 'draft',
          title: '',
          created: DateTime(2026, 9, 27),
          draft: 'Suggest a calm accent that stays readable in sunlight.',
        );
        await store.saveTask(color.id, draft);
        await _shot(
          tester,
          'library2_external_task_draft',
          light: light,
          home: ExternalTaskScreen(
            store: store,
            profile: color,
            record: draft,
            gateway: FakeExternalGateway(),
          ),
        );
      });

      testWidgets('stop question', (tester) async {
        final color = await seed();
        final gateway = FakeExternalGateway()..next = waitingTask;
        await _shot(
          tester,
          'library2_external_task_stop_sheet',
          light: light,
          home: ExternalTaskScreen(
            store: store,
            profile: color,
            record: store
                .tasks(color.id)
                .firstWhere((r) => r.localId == 'waiting'),
            gateway: gateway,
          ),
          act: () async {
            await tester.tap(find.byKey(const ValueKey('external-task-menu')));
            await tester.pumpAndSettle();
            await tester.tap(find.text('Ask Color agent to stop this task'));
          },
        );
      });
    });
  }
}
