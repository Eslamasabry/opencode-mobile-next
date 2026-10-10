// Execution-gated project actions (New project, Approve and start, lanes,
// merge, verify, move, Promote) follow what the engine can do right now
// (OrchestrationCapabilities), never the server's flavor. While it cannot
// run work the actions are not drawn as dead buttons: one plain line says
// why and carries the one action that fixes it, the setup flow.
//
// The gate is bound to a project controller by the page that owns the
// team ([TeamExecutionGate.bind]); screens pushed on the navigator find it
// from the controller they already hold. A controller nobody bound (the
// demo, which simulates every action) is never gated.
import 'package:flutter/material.dart' show Icons;
import 'package:flutter/widgets.dart';

import '../../../../domain/orchestration_gateway.dart'
    show OrchestrationCapabilities;
import '../../../../l10n/app_localizations.dart';
import '../../../../state/connection.dart';
import '../../../../state/orchestration.dart';
import '../../../../state/team_project_controller.dart';
import '../../../kit/kit.dart';
import '../team_phone_setup_screen.dart';

/// What an action needs the engine to be able to do.
enum TeamExecutionNeed {
  lanes,
  promotion,
  mergeQueue,
  verification,
  placement,
  resume,

  /// Delete a project (the engine's `deleteProject`).
  removal,
}

class TeamExecutionGate {
  TeamExecutionGate._(this.team, this.connection);

  final OrchestrationController team;
  final ConnectionController connection;

  static final Expando<TeamExecutionGate> _bound = Expando();

  /// Binds [team]'s project controller to the gate. Safe to repeat.
  static void bind(OrchestrationController team, ConnectionController c) {
    final projects = team.projectController;
    if (projects == null) return;
    final existing = _bound[projects];
    if (existing != null && identical(existing.connection, c)) return;
    _bound[projects] = TeamExecutionGate._(team, c);
  }

  static TeamExecutionGate? of(TeamProjectController controller) =>
      _bound[controller];

  OrchestrationCapabilities get capabilities => team.capabilities;

  bool permits(TeamExecutionNeed need) => switch (need) {
    TeamExecutionNeed.lanes => capabilities.projectLanes,
    TeamExecutionNeed.promotion => capabilities.projectPromotion,
    TeamExecutionNeed.mergeQueue => capabilities.projectMergeQueue,
    TeamExecutionNeed.verification => capabilities.projectVerification,
    TeamExecutionNeed.placement => capabilities.projectPlacement,
    TeamExecutionNeed.resume => capabilities.projectResume,
    TeamExecutionNeed.removal => capabilities.projectLifecycle,
  };

  /// Opens "Turn on AI Team on this phone".
  Future<void> setUp(BuildContext context) =>
      openPhoneTeamSetup(context, connection);

  /// Whether [need] can run now; true for an unbound controller.
  /// The demo simulates every action, so it is never gated, even when the
  /// page that shows it belongs to a phone team that is not ready.
  static bool allows(TeamProjectController c, TeamExecutionNeed need) =>
      c.snapshot?.simulated == true || (of(c)?.permits(need) ?? true);
}

/// The one plain line, with the fix, shown once per page while the engine
/// cannot run work. Draws nothing otherwise.
class TeamExecutionBlocked extends StatelessWidget {
  const TeamExecutionBlocked({super.key, required this.controller});

  final TeamProjectController controller;

  @override
  Widget build(BuildContext context) {
    final gate = TeamExecutionGate.of(controller);
    if (gate == null ||
        controller.snapshot?.simulated == true ||
        gate.permits(TeamExecutionNeed.lanes)) {
      return const SizedBox.shrink();
    }
    final l = lookupAppLocalizations(Localizations.localeOf(context));
    return KitNotice(
      key: const ValueKey('team-execution-blocked'),
      message: l.phoneTeamBlocked,
      actions: [
        KitAction(
          key: const ValueKey('team-execution-set-up'),
          label: l.teamIntroTurnOnPhone,
          onPressed: () => gate.setUp(context),
        ),
      ],
    );
  }
}

/// One plain line once the phone's team can run work: what fences its files.
/// Draws nothing for the demo, a team that cannot run work, or an unknown
/// boundary.
class TeamProtectionLine extends StatelessWidget {
  const TeamProtectionLine({super.key, required this.controller});

  final TeamProjectController controller;

  @override
  Widget build(BuildContext context) {
    final gate = TeamExecutionGate.of(controller);
    if (gate == null ||
        controller.snapshot?.simulated == true ||
        !gate.permits(TeamExecutionNeed.lanes)) {
      return const SizedBox.shrink();
    }
    final l = lookupAppLocalizations(Localizations.localeOf(context));
    final line = phoneTeamProtectionText(l, gate.team.boundaryTier);
    if (line == null) return const SizedBox.shrink();
    return KitNotice(
      key: const ValueKey('team-protection-line'),
      message: line,
      icon: Icons.shield_outlined,
    );
  }
}

/// Why the phone's own server reads "Not reachable", with the one action
/// that fixes it. The raw state goes under Copy details, never on screen.
/// Draws nothing for the demo, an unbound controller, or a phone that is
/// reachable.
class TeamPhoneServerProblem extends StatelessWidget {
  const TeamPhoneServerProblem({super.key, required this.controller});

  final TeamProjectController controller;

  @override
  Widget build(BuildContext context) {
    final gate = TeamExecutionGate.of(controller);
    final servers = controller.snapshot?.servers ?? const <TeamServer>[];
    if (gate == null ||
        controller.snapshot?.simulated == true ||
        !servers.any((s) => s.phone && !s.online)) {
      return const SizedBox.shrink();
    }
    final l = lookupAppLocalizations(Localizations.localeOf(context));
    final team = gate.team;
    final failed = team.phase == OrchestrationPhase.failed;
    final notReady = !failed && !gate.permits(TeamExecutionNeed.lanes);
    final message = failed
        ? l.teamServerPhoneFailed
        : notReady
        ? l.teamServerPhoneNotReady
        : l.teamServerPhoneNoAnswer;
    final details = [
      'phase=${team.phase.name}',
      if (team.lastError != null) 'error=${team.lastError!.kind.name}',
      'projectLanes=${gate.permits(TeamExecutionNeed.lanes)}',
    ].join(' ');
    final fix = failed || notReady
        ? KitAction(
            key: const ValueKey('team-server-set-up'),
            label: l.teamIntroTurnOnPhone,
            onPressed: () => gate.setUp(context),
          )
        : KitAction(
            key: const ValueKey('team-server-retry'),
            label: l.teamProjectRetry,
            onPressed: () async {
              await team.refresh();
              await controller.load();
            },
          );
    return KitNotice.error(
      key: const ValueKey('team-server-problem'),
      message: message,
      errorKind: KitErrorKind.other,
      details: details,
      retry: fix,
    );
  }
}

/// One plain line on an interrupted project when the engine can run work
/// but does not offer Resume: why there is no button, and what to do.
class TeamResumeUnavailable extends StatelessWidget {
  const TeamResumeUnavailable({super.key, required this.controller});

  final TeamProjectController controller;

  @override
  Widget build(BuildContext context) {
    final interrupted =
        controller.snapshot?.projects.any((p) => p.status == 'interrupted') ??
        false;
    if (!interrupted ||
        !TeamExecutionGate.allows(controller, TeamExecutionNeed.lanes) ||
        TeamExecutionGate.allows(controller, TeamExecutionNeed.resume)) {
      return const SizedBox.shrink();
    }
    final l = lookupAppLocalizations(Localizations.localeOf(context));
    return KitNotice(
      key: const ValueKey('team-resume-unavailable'),
      message: l.teamProjectResumeUnavailable,
    );
  }
}
