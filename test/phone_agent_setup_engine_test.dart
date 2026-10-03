import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/agents/agent_components.dart';
import 'package:opencode_mobile/builtin/agents/paseo_scripts.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/builtin/setup/setup_contract.dart';
import 'package:opencode_mobile/builtin/setup/setup_engine.dart';
import 'package:opencode_mobile/builtin/setup/setup_scripts.dart';
import 'package:opencode_mobile/domain/agent_catalog.dart';

import 'setup_engine_test.dart' show FakeLinux, en, spec;

class _Arm64V1Linux extends FakeLinux {
  final agentChecks = <String>[];

  _Arm64V1Linux() {
    serverRunning = true;
    checks = {
      'linux': (true, '24.04'),
      'essentials': (true, 'ready'),
      'python': (true, '3.12'),
    };
  }

  @override
  Future<BuiltinLinuxStatus> status() async => const BuiltinLinuxStatus(
    installed: true,
    phase: BuiltinLinuxPhase.ready,
    serverRunning: true,
    abi: 'arm64-v8a',
  );

  @override
  Future<BuiltinLinuxRunResult> runAgentSetupCheck(
    String script, {
    Duration timeout = const Duration(minutes: 2),
  }) async {
    agentChecks.add(script);
    // First install has no agent user yet; the native channel reports that
    // precondition rather than crashing. The root bootstrap must come first.
    throw const BuiltinLinuxException('Agent user is not installed yet');
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test(
    'ARM64 OpenCode 1 host hands off pinned Node after root user bootstrap',
    () async {
      final linux = _Arm64V1Linux();
      final agent = AgentCatalog.builtIn.byId('claude')!;
      final lock = await File(
        PaseoPhoneScripts.packageLockAsset,
      ).readAsString();
      final engine = ChannelSetupEngine(
        linux: linux,
        strings: () => en,
        components: (strings, _) => phoneAgentComponents(strings, agent, lock),
        pollInterval: const Duration(days: 1),
      );
      try {
        await engine.run(
          {'agent-claude'},
          params: const {
            'opencode': {'runtime': 'opencode1'},
            'phoneAgentOwner': {'profileId': 'phone', 'agentId': 'claude'},
          },
        );
        expect(engine.progress.value.state, SetupState.running);
        expect(linux.agentChecks, hasLength(1));
        final sent = (linux.started.single['components'] as List).cast<Map>();
        final ids = sent.map((c) => c['id']).toList();
        expect(ids.indexOf('agent-user'), lessThan(ids.indexOf('agent-node')));
        expect(ids, isNot(contains('opencode')));
        expect(ids, isNot(contains('start')));
        expect(spec(linux, 'agent-user')['agentUser'], isNull);
        for (final id in ['agent-node', 'agent-paseo', 'agent-claude']) {
          expect(spec(linux, id)['agentUser'], true);
          expect(spec(linux, id)['script'], startsWith(setupPrelude));
        }
        expect(
          spec(linux, 'agent-node')['script'],
          contains(PaseoPhoneScripts.nodeArm64Sha256),
        );
        expect(spec(linux, 'linux')['skipped'], true);
        expect(spec(linux, 'essentials')['skipped'], true);
        expect(spec(linux, 'python')['skipped'], true);
        expect(linux.serverRunning, true);
      } finally {
        engine.dispose();
      }
    },
  );
}
