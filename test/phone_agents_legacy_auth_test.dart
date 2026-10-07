import 'dart:async';
import 'dart:convert';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/agent_auth_probe.dart';
import 'package:opencode_mobile/domain/agent_catalog.dart';
import 'package:opencode_mobile/domain/agent_sign_in.dart';
import 'package:opencode_mobile/domain/phone_agent_host.dart';
import 'package:opencode_mobile/domain/phone_agents.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/phone_agent_host_port.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _LegacyHost implements PhoneAgentHostPort {
  _LegacyHost(this.phase);
  final AgentSignInPhase? phase;

  @override
  Stream<AgentSetupProgress> get setupChanges => const Stream.empty();
  @override
  AgentSetupProgress get setupProgress =>
      const AgentSetupProgress(agentId: '', phase: AgentSetupPhase.idle);
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
    signInPhase: agentId == 'claude' ? phase : null,
  );
  @override
  Future<void> restoreInstall() async {}
  @override
  Future<void> cancelInstall() async {}
  @override
  Future<void> stop() async {}
  @override
  Future<void> dispose() async {}
  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _AuthHost extends _LegacyHost implements PhoneAgentAuthPort {
  _AuthHost() : super(AgentSignInPhase.signedIn);
  int reads = 0;
  AgentAuthProbeState result = AgentAuthProbeState.signedOut;
  @override
  Future<AgentAuthProbeResult> probeSignIn(String agentId) async {
    reads++;
    return AgentAuthProbeResult(state: result);
  }

  @override
  bool supportsSignOut(String agentId) => false;
  @override
  Future<AgentAuthProbeResult> signOut(String agentId) =>
      throw UnsupportedError('Sign-out is outside this fixture.');
}

class _NativeStatusHost implements AgentSignInHost {
  _NativeStatusHost({this.pending = false});
  final bool pending;
  final _answer = Completer<AgentSignInHostUpdate>();
  AgentSignInRun? _run;
  int reads = 0;

  AgentSignInHostUpdate _result(AgentSignInRun run) => AgentSignInHostUpdate(
    runId: run.runId,
    phase: AgentSignInPhase.signedOut,
  );

  @override
  Future<AgentSignInHostUpdate> status(AgentSignInRun run) async {
    reads++;
    _run = run;
    return pending ? _answer.future : _result(run);
  }

  void finishPending() {
    final run = _run;
    if (run != null && !_answer.isCompleted) _answer.complete(_result(run));
  }

  @override
  Future<void> cancelAndDrain(AgentSignInRun run) async {}
  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

Future<ConnectionController> _controller(
  _LegacyHost host,
  _NativeStatusHost native,
) async {
  SharedPreferences.setMockInitialValues({
    'oc.profiles': jsonEncode([
      ServerProfile(
        id: 'phone',
        name: 'This phone',
        baseUrl: 'http://127.0.0.1:4097',
      ).toJson(),
    ]),
    'oc.activeProfile': 'phone',
  });
  final store = ProfileStore(prefs: await SharedPreferences.getInstance());
  await store.load();
  return ConnectionController(
    store,
    phoneAgentHostFactory: (_) => host,
    agentSignInHostFactory: () => native,
  );
}

void main() {
  final binding = TestWidgetsFlutterBinding.ensureInitialized();
  const secure = MethodChannel('plugins.it_nomads.com/flutter_secure_storage');
  setUp(() {
    binding.defaultBinaryMessenger.setMockMethodCallHandler(
      secure,
      (_) async => null,
    );
  });
  tearDown(
    () => binding.defaultBinaryMessenger.setMockMethodCallHandler(secure, null),
  );

  testWidgets(
    'legacy host sign-in truth publishes inventory without a native read',
    (tester) async {
      final native = _NativeStatusHost(pending: true);
      final controller = await _controller(
        _LegacyHost(AgentSignInPhase.signedIn),
        native,
      );
      var finished = false;
      final refresh = controller.refreshAgentRows().then(
        (_) => finished = true,
      );
      try {
        await tester.pump();
        expect(native.reads, 0);
        expect(finished, isTrue);
        expect(
          controller.agentRows.singleWhere((row) => row.id == 'claude').status,
          PhoneAgentStatus.ready,
        );
      } finally {
        native.finishPending();
        await refresh;
        controller.dispose();
        await tester.pump(const Duration(minutes: 2));
      }
    },
  );

  test('legacy host without sign-in truth still reads native status', () async {
    final native = _NativeStatusHost();
    final controller = await _controller(_LegacyHost(null), native);
    addTearDown(controller.dispose);
    await controller.refreshAgentRows();
    expect(native.reads, 1);
    expect(
      controller.agentRows.singleWhere((row) => row.id == 'claude').status,
      PhoneAgentStatus.signedOut,
    );
  });

  test(
    'auth-capable host refreshes despite an inspected signed-in phase',
    () async {
      final host = _AuthHost();
      final native = _NativeStatusHost();
      final controller = await _controller(host, native);
      addTearDown(controller.dispose);
      await controller.refreshAgentRows();
      expect(host.reads, 1);
      expect(native.reads, 0);
      expect(
        controller.agentRows.singleWhere((row) => row.id == 'claude').status,
        PhoneAgentStatus.signedOut,
      );
      host.result = AgentAuthProbeState.signedIn;
      await controller.refreshAgentRows();
      expect(host.reads, 2);
      expect(
        controller.agentRows.singleWhere((row) => row.id == 'claude').status,
        PhoneAgentStatus.ready,
      );
    },
  );
}
