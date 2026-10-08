import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/agents/agent_install_guard.dart';
import 'package:opencode_mobile/builtin/setup/preflight.dart';
import 'package:opencode_mobile/domain/agent_catalog.dart';

void main() {
  test('normal and negative QA floor preserve every pinned estimate', () {
    for (final agent in AgentCatalog.builtIn.agents.where(
      (a) => a.recipe != null,
    )) {
      final sizes =
          agent.recipe!.artifacts.values
              .map((a) => a.downloadBytes)
              .whereType<int>()
              .toList()
            ..sort();
      final normal = sizes.isEmpty ? null : sizes.last;
      expect(
        const AgentInstallGuard(minimumFreeBytes: 0).downloadBytesFor(agent),
        normal,
      );
      expect(
        const AgentInstallGuard(minimumFreeBytes: -1).downloadBytesFor(agent),
        normal,
      );
    }
  });
  test('raised QA floor applies to six targets and excludes real Claude', () {
    const guard = AgentInstallGuard(minimumFreeBytes: 8589934592);
    for (final id in AgentInstallGuard.agentIds) {
      expect(
        requiredSetupFreeBytes(
          guard.downloadBytesFor(AgentCatalog.builtIn.byId(id)!)!,
        ),
        8589934592,
      );
    }
    final claude = AgentCatalog.builtIn.byId('claude')!;
    expect(
      guard.downloadBytesFor(claude),
      const AgentInstallGuard(minimumFreeBytes: 0).downloadBytesFor(claude),
    );
  });
  test(
    'small QA floor never reduces catalog requirement and odd floor rounds up',
    () {
      final agent = AgentCatalog.builtIn.byId('omp-acp')!;
      final pin = const AgentInstallGuard(
        minimumFreeBytes: 0,
      ).downloadBytesFor(agent)!;
      expect(
        const AgentInstallGuard(minimumFreeBytes: 1).downloadBytesFor(agent),
        pin,
      );
      expect(
        requiredSetupFreeBytes(
          const AgentInstallGuard(
            minimumFreeBytes: 8589934593,
          ).downloadBytesFor(agent)!,
        ),
        8589934594,
      );
    },
  );
  test('maximum QA floor saturates native admission rather than wrapping', () {
    final fx = AgentCatalog.builtIn.byId('fx')!;
    final bytes = const AgentInstallGuard(
      minimumFreeBytes: 0x7fffffffffffffff,
    ).downloadBytesFor(fx)!;
    expect(requiredSetupFreeBytes(bytes), 0x7fffffffffffffff);
  });
}
