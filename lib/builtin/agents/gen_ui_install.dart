import 'dart:async';

import '../../domain/genui/gen_ui_status.dart';
import '../builtin_linux.dart';
import 'gen_ui_install_scripts.dart';

// A parser or persisted recipe does not qualify a live backend. Installation
// can stage any adapter with tools, but only evidence-backed adapters
// ([AgentToolAdapter.cardsQualified]) may become ready.

/// Configuration outcome only. It never establishes live tool availability.
enum GenUiInstallOutcome {
  registered,
  removalPending,
  notInstalled,
  runtimeMissing,
  nameCollision,
  unsafePath,
  failed,
}

abstract interface class GenUiSetupRunner {
  Future<GenUiInstallOutcome> run({
    required GenUiAgent agent,
    required String script,
  });
}

abstract interface class GenUiSetupVerifier {
  Future<bool> verify({required String profileId, required GenUiAgent agent});
}

/// Re-checks the qualified runtime and this profile's registered MCP tool.
/// Qualification of its transport is independent and precedes this check.
final class BuiltinGenUiSetupVerifier implements GenUiSetupVerifier {
  BuiltinGenUiSetupVerifier({BuiltinLinux? linux})
    : _linux = linux ?? BuiltinLinux();
  final BuiltinLinux _linux;

  @override
  Future<bool> verify({
    required String profileId,
    required GenUiAgent agent,
  }) async {
    if (!agent.cardsQualified) return false;
    try {
      final script = genUiVerificationScript(
        profileId: profileId,
        agent: agent,
      );
      final result = agent.runsAs == AgentRunUser.agentUser
          ? await _linux.runAgentSetupCheck(script)
          : await _linux.run(script);
      return result.exitCode == 0;
    } catch (_) {
      return false;
    }
  }
}

abstract interface class GenUiInstaller {
  Future<GenUiSetupStatus> setEnabled({
    required String profileId,
    required Set<GenUiAgent> agents,
    required bool enabled,
  });
}

/// Runs fixed scripts through the existing private Ubuntu bridges. Raw output
/// and exceptions are deliberately confined here and never exposed to callers.
final class BuiltinGenUiSetupRunner implements GenUiSetupRunner {
  BuiltinGenUiSetupRunner({BuiltinLinux? linux})
    : _linux = linux ?? BuiltinLinux();
  final BuiltinLinux _linux;

  @override
  Future<GenUiInstallOutcome> run({
    required GenUiAgent agent,
    required String script,
  }) async {
    try {
      final result = agent.runsAs == AgentRunUser.agentUser
          ? await _linux.runAgentSetupCheck(script)
          : await _linux.run(script);
      // Exit status is the entire result protocol; never trust stdout.
      return switch (result.exitCode) {
        0 => GenUiInstallOutcome.registered,
        10 => GenUiInstallOutcome.removalPending,
        11 => GenUiInstallOutcome.notInstalled,
        20 => GenUiInstallOutcome.runtimeMissing,
        21 => GenUiInstallOutcome.nameCollision,
        22 => GenUiInstallOutcome.unsafePath,
        _ => GenUiInstallOutcome.failed,
      };
    } catch (_) {
      return GenUiInstallOutcome.failed;
    }
  }
}

final class ManagedGenUiInstaller implements GenUiInstaller {
  ManagedGenUiInstaller({
    required GenUiSetupRunner runner,
    GenUiSetupVerifier? verifier,
  }) : _runner = runner,
       _verifier = verifier;
  factory ManagedGenUiInstaller.builtin() => ManagedGenUiInstaller(
    runner: BuiltinGenUiSetupRunner(),
    verifier: BuiltinGenUiSetupVerifier(),
  );
  final GenUiSetupRunner _runner;
  final GenUiSetupVerifier? _verifier;

  // Serialize across instances as well as profiles. The scripts also use a
  // filesystem lock to cover another app process or interrupted caller.
  static Future<void> _tail = Future<void>.value();

  @override
  Future<GenUiSetupStatus> setEnabled({
    required String profileId,
    required Set<GenUiAgent> agents,
    required bool enabled,
  }) {
    if (!RegExp(r'^[a-zA-Z0-9_-]{1,96}$').hasMatch(profileId)) {
      return Future.value(
        const GenUiSetupFailed(reason: GenUiSetupProblem.storageFailed),
      );
    }
    final requested = Set<GenUiAgent>.of(agents);
    final result = Completer<GenUiSetupStatus>();
    _tail = _tail.then((_) async {
      try {
        result.complete(await _apply(profileId, requested, enabled));
      } catch (_) {
        result.complete(
          const GenUiSetupFailed(reason: GenUiSetupProblem.installationFailed),
        );
      }
    });
    return result.future;
  }

  Future<GenUiSetupStatus> _apply(
    String profileId,
    Set<GenUiAgent> agents,
    bool enabled,
  ) async {
    if (agents.isEmpty) {
      return enabled
          ? const GenUiSetupUnavailable(reason: GenUiSetupProblem.notQualified)
          : const GenUiSetupOff();
    }
    GenUiSetupProblem? failure;
    var changed = false;
    final ready = <GenUiAgent>[];
    // Each agent's own problem, so the status can name who it is about.
    final problems = <GenUiAgent, GenUiSetupProblem>{};
    for (final agent in agents) {
      GenUiSetupProblem? problem;
      final outcome = await _runner.run(
        agent: agent,
        script: genUiInstallScript(
          profileId: profileId,
          agent: agent,
          enabled: enabled,
        ),
      );
      switch (outcome) {
        case GenUiInstallOutcome.registered:
          changed = true;
          if (enabled) {
            // Persisted config/self-check is only preparation. A permissive
            // verifier must not promote an unqualified transport. Adding an
            // OpenCode runtime here requires recorded device evidence first.
            if (!agent.cardsQualified) {
              problem ??= GenUiSetupProblem.notQualified;
              break;
            }
            final verifier = _verifier;
            if (verifier == null) {
              problem ??= GenUiSetupProblem.notQualified;
            } else {
              try {
                if (await verifier.verify(profileId: profileId, agent: agent)) {
                  ready.add(agent);
                } else {
                  problem ??= GenUiSetupProblem.verificationFailed;
                }
              } catch (_) {
                problem ??= GenUiSetupProblem.verificationFailed;
              }
            }
          }
        case GenUiInstallOutcome.removalPending:
          changed = true;
        case GenUiInstallOutcome.notInstalled:
          break;
        case GenUiInstallOutcome.runtimeMissing:
          problem ??= GenUiSetupProblem.runtimeMissing;
        case GenUiInstallOutcome.nameCollision:
          problem ??= GenUiSetupProblem.conflict;
        case GenUiInstallOutcome.unsafePath:
          problem ??= GenUiSetupProblem.permissionDenied;
        case GenUiInstallOutcome.failed:
          problem ??= enabled
              ? GenUiSetupProblem.registrationFailed
              : GenUiSetupProblem.removalFailed;
      }
      if (problem != null) {
        failure ??= problem;
        problems[agent] = problem;
      }
    }
    if (failure != null) {
      final affected = [
        for (final MapEntry(:key, :value) in problems.entries)
          if (value == failure) key,
      ];
      if (enabled && ready.isEmpty) {
        return failure == GenUiSetupProblem.notQualified
            ? GenUiSetupUnavailable(reason: failure, affected: affected)
            : GenUiSetupFailed(reason: failure, affected: affected);
      }
      return changed
          ? GenUiSetupPartial(
              agents: ready,
              reason: failure,
              affected: affected,
            )
          : GenUiSetupFailed(reason: failure, affected: affected);
    }
    if (!enabled && !changed) return const GenUiSetupOff();
    if (enabled && ready.length == agents.length) {
      return GenUiSetupOn(agents: ready);
    }
    if (enabled) {
      return const GenUiSetupFailed(
        reason: GenUiSetupProblem.verificationFailed,
      );
    }
    // Removal of persisted config does not prove active catalogs dropped it.
    // This status never promises that restarting repairs failed verification.
    return GenUiSetupRestartRequired(agents: const []);
  }
}
