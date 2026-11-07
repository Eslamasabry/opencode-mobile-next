import 'support/fake_server_capabilities.dart';
import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'support/fake_server_gateway.dart';
import 'package:opencode_mobile/domain/product_repository.dart';
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
import 'package:opencode_mobile/ui/screens/local_agent_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../tool/capture/fixtures.dart' show loadCaptureFonts;

const _builtIn = 'http://127.0.0.1:4097';

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
      {
        'id': 'active',
        'name': 'Active',
        'flavor': 'v2',
        'baseUrl': activeUrl,
        'username': '',
      },
      if (savedBuiltIn)
        {
          'id': 'phone',
          'name': 'This phone',
          'flavor': 'v2',
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

Widget _app(ConnectionController controller) => ProviderScope(
  overrides: [connProvider.overrideWithValue(controller)],
  child: MaterialApp(
    theme: AppTheme.light(),
    localizationsDelegates: AppLocalizations.localizationsDelegates,
    supportedLocales: AppLocalizations.supportedLocales,
    home: const LocalAgentScreen(),
  ),
);

void main() {
  setUpAll(loadCaptureFonts);
  setUp(() {
    debugPlatformCapabilities = const PlatformCapabilities.android();
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (_) async => null,
        );
  });
  tearDown(() => debugPlatformCapabilities = null);

  testWidgets('on the built-in server, Claude Code on this phone opens the '
      'agents it reads from that host, not the first-time set-up', (
    tester,
  ) async {
    final controller = await _controller(activeUrl: _builtIn);
    addTearDown(controller.dispose);
    tester.view.physicalSize = const Size(412, 1400);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(_app(controller));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('local-agent-managed')), findsOneWidget);
    expect(find.byKey(const ValueKey('agents-section')), findsOneWidget);
    expect(find.byKey(const ValueKey('local-agent-block')), findsNothing);
    expect(find.text('Set up Claude Code'), findsNothing);
    expect(find.textContaining('Termux'), findsNothing);
    // Claude is installed on that host: its row is not "Not installed".
    final claude = find.byKey(const ValueKey('agents-row-claude'));
    expect(claude, findsOneWidget);
    expect(
      find.descendant(
        of: claude,
        matching: find.textContaining('Not installed'),
      ),
      findsNothing,
    );
    await tester.pumpWidget(const SizedBox.shrink());
    controller.dispose();
    await tester.pump(const Duration(minutes: 2));
  });
}
