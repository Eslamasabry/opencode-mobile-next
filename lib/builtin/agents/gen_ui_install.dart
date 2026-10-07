import 'dart:async';

import '../../domain/genui/gen_ui_status.dart';
import '../builtin_linux.dart';
import 'gen_ui_install_scripts.dart';

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

/// Re-checks the pinned Claude runtime and this profile's registered MCP tool.
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
    if (agent != GenUiAgent.claude) return false;
    try {
      final result = await _linux.runAgentSetupCheck(
        genUiVerificationScript(profileId: profileId, agent: agent),
      );
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
      final result = agent == GenUiAgent.claude
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
    for (final agent in agents) {
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
            try {
              if (await _verifier?.verify(profileId: profileId, agent: agent) ??
                  false) {
                ready.add(agent);
              }
            } catch (_) {
              // A failed readiness check leaves registration restart-required.
            }
          }
        case GenUiInstallOutcome.removalPending:
          changed = true;
        case GenUiInstallOutcome.notInstalled:
          break;
        case GenUiInstallOutcome.runtimeMissing:
          failure ??= GenUiSetupProblem.runtimeMissing;
        case GenUiInstallOutcome.nameCollision:
          failure ??= GenUiSetupProblem.conflict;
        case GenUiInstallOutcome.unsafePath:
          failure ??= GenUiSetupProblem.permissionDenied;
        case GenUiInstallOutcome.failed:
          failure ??= enabled
              ? GenUiSetupProblem.registrationFailed
              : GenUiSetupProblem.removalFailed;
      }
    }
    if (failure != null) {
      return changed
          ? GenUiSetupPartial(agents: ready, reason: failure)
          : GenUiSetupFailed(reason: failure);
    }
    if (!enabled && !changed) return const GenUiSetupOff();
    if (enabled && ready.length == agents.length) {
      return GenUiSetupOn(agents: ready);
    }
    // Unverified registrations and loaded catalog removal need a new runtime
    // check. This never restarts agents or treats config writes as discovery.
    return GenUiSetupRestartRequired(agents: ready);
  }
}
