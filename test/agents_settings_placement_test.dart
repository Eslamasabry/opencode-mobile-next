import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/domain/agent_catalog.dart';
import 'package:opencode_mobile/domain/agent_sign_in.dart';
import 'package:opencode_mobile/domain/phone_agent_host.dart';
import 'package:opencode_mobile/domain/phone_agents.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/phone_agent_host_port.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/screens/servers_screen.dart'
    show ServersRouteRequest;
import 'package:opencode_mobile/ui/screens/settings_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../tool/capture/fixtures.dart' show loadCaptureFonts;

const _builtIn = 'http://127.0.0.1:4097';
const _remote = 'http://workstation.local:4096';

class _Api extends OpenCodeApi {
  _Api() : super(baseUrl: 'http://localhost');

  @override
  ServerCapabilities get capabilities => ServerCapabilities.allV1;

  @override
  Future<Health> health() async => Health(healthy: true, version: '1.18.23');
}

class _Repository implements ProductRepository {
  @override
  void setLocation({String? directory, String? workspace}) {}

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

/// A phone agent host with Claude installed, checked and signed in.
class _Host implements PhoneAgentHostPort {
  @override
  Stream<AgentSetupProgress> get setupChanges => const Stream.empty();

  @override
  AgentSetupProgress get setupProgress =>
      const AgentSetupProgress(agentId: '', phase: AgentSetupPhase.idle);

  @override
  Future<void> restoreInstall() async {}

  @override
  Future<AgentArchitecture?> architecture() async => AgentArchitecture.arm64;

  @override
  Future<PhoneAgentRuntime> inspect(
    String agentId, {
    AgentSignInState? signIn,
    AgentCapabilities capabilities = const AgentCapabilities(),
  }) async => PhoneAgentRuntime(
    agentId: agentId,
    installed: agentId == 'claude',
    hostAvailable: true,
    architectureQualified: agentId == 'claude',
    signInPhase: AgentSignInPhase.signedIn,
  );

  @override
  Future<AgentPhoneCheckResult> selfTest(String agentId) async =>
      AgentPhoneCheckResult(
        agentId: agentId,
        architecture: AgentArchitecture.arm64,
        passed: true,
        completed: AgentPhoneCheckStep.values,
      );

  @override
  Future<void> dispose() async {}

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

Future<ConnectionController> _controller({
  required String activeUrl,
  bool savedBuiltIn = false,
}) async {
  SharedPreferences.setMockInitialValues({
    'oc.profiles': jsonEncode([
      {'id': 'active', 'name': 'Active', 'baseUrl': activeUrl, 'username': ''},
      if (savedBuiltIn)
        {
          'id': 'phone',
          'name': 'This phone',
          'baseUrl': _builtIn,
          'username': '',
        },
    ]),
    'oc.activeProfile': 'active',
  });
  final store = ProfileStore(prefs: await SharedPreferences.getInstance());
  await store.load();
  return ConnectionController(store, phoneAgentHostFactory: (_) => _Host())
    ..api = _Api()
    ..repository = _Repository()
    ..status = StreamStatus.connected;
}

Object? _routeArgs;

Widget _app(ConnectionController controller) => ProviderScope(
  overrides: [connProvider.overrideWithValue(controller)],
  child: MaterialApp(
    theme: AppTheme.light(),
    localizationsDelegates: AppLocalizations.localizationsDelegates,
    supportedLocales: AppLocalizations.supportedLocales,
    onGenerateRoute: (settings) {
      if (settings.name == '/servers') {
        _routeArgs = settings.arguments;
        return MaterialPageRoute<void>(
          builder: (_) => const Scaffold(body: Text('servers route')),
        );
      }
      return null;
    },
    home: SettingsScreen(controller: controller),
  ),
);

Finder _key(String key) => find.byKey(ValueKey(key));

void main() {
  setUpAll(loadCaptureFonts);
  setUp(() {
    _routeArgs = null;
    debugPlatformCapabilities = const PlatformCapabilities.android();
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (_) async => null,
        );
  });
  tearDown(() => debugPlatformCapabilities = null);

  testWidgets('on the built-in server Settings has an Agents row that opens '
      'the section with Check this phone', (tester) async {
    final controller = await _controller(activeUrl: _builtIn);
    addTearDown(controller.dispose);
    tester.view.physicalSize = const Size(412, 1400);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(_app(controller));
    await tester.pumpAndSettle();
    final row = _key('settings-agents');
    expect(row, findsOneWidget);
    expect(find.text('Agents run on the built-in server'), findsNothing);
    await tester.ensureVisible(row);
    await tester.tap(row);
    await tester.pumpAndSettle();
    expect(_key('agents-screen'), findsOneWidget);
    expect(_key('agents-section'), findsOneWidget);
    expect(find.text('Check this phone'), findsOneWidget);
    await tester.ensureVisible(_key('agents-check-phone'));
    await tester.tap(_key('agents-check-phone'));
    await tester.pumpAndSettle();
    for (final step in ['Installed', 'Version', 'Connection', 'Ready']) {
      expect(find.text(step), findsWidgets);
    }
    // Leave nothing running: the controller owns the host's timers.
    await tester.pumpWidget(const SizedBox.shrink());
    controller.dispose();
    await tester.pump(const Duration(minutes: 2));
  });

  testWidgets('on a remote server the row stays, says why, and offers no '
      'switch without a built-in server', (tester) async {
    final controller = await _controller(activeUrl: _remote);
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(controller));
    await tester.pumpAndSettle();
    final row = _key('settings-agents');
    await tester.ensureVisible(row);
    expect(
      find.descendant(
        of: row,
        matching: find.text('Agents run on the built-in server'),
      ),
      findsOneWidget,
    );
    await tester.tap(row);
    await tester.pumpAndSettle();
    expect(_key('agents-unavailable'), findsOneWidget);
    expect(_key('agents-switch-builtin'), findsNothing);
  });

  testWidgets('with a built-in server saved, the page offers the switch', (
    tester,
  ) async {
    final controller = await _controller(
      activeUrl: _remote,
      savedBuiltIn: true,
    );
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(controller));
    await tester.pumpAndSettle();
    await tester.ensureVisible(_key('settings-agents'));
    await tester.tap(_key('settings-agents'));
    await tester.pumpAndSettle();
    expect(find.text('Switch to the built-in server'), findsOneWidget);
    await tester.tap(_key('agents-switch-builtin'));
    await tester.pumpAndSettle();
    final request = _routeArgs as ServersRouteRequest;
    expect(request.profileID, 'phone');
  });

  testWidgets('off a phone, with no agents, there is no Agents row', (
    tester,
  ) async {
    debugPlatformCapabilities = const PlatformCapabilities.linuxDesktop();
    final controller = await _controller(activeUrl: _remote);
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(controller));
    await tester.pumpAndSettle();
    expect(_key('settings-agents'), findsNothing);
  });
}
