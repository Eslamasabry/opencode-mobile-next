/// The one AI Team page (programme P3.4, "the team page is one page"):
/// every door to the AI Team opens it — Settings › AI Team, Plugins' AI
/// Team row, search, New conversation's Team while the team is off — and
/// it shows the team in whatever state it is in:
///
/// - **On** ([OrchestrationController] for the connected server): the
///   work itself ([TeamHomeScreen]): one status line and one list of
///   tasks, most urgent first. Setup (agents, how it runs, spend, Turn
///   off) is Team settings, from the top bar or Settings › AI Team.
/// - **Off**: the same page says what the team does and sets it up for
///   this kind of server ([TeamIntroScreen]); the moment it is on, the
///   page turns into the team, with no hop back and no second page.
///
/// The retired AI Team sheet (team-plugin-sheet) and the separate intro
/// route both land here.
library;

import 'dart:async';

import 'package:flutter/material.dart';

import '../../../l10n/app_localizations.dart';
import '../../../state/connection.dart';
import '../../../state/orchestration.dart' show OrchestrationPhase;
import '../../../state/phone_team_setup.dart';
import '../../../state/profiles.dart' show OrchestrationProvider;
import '../../../state/team_project_demo.dart';
import '../../../domain/orchestration_gateway.dart' show OrchestrationRun;
import '../../../voice/device.dart';
import '../../app_theme.dart';
import '../../kit/kit.dart';
import '../../widgets/team_host_form.dart' show TeamHostProbe;
import 'team_home_screen.dart';
import 'projects/team_projects_screen.dart';
import 'projects/team_execution_gate.dart';
import 'team_intro_screen.dart';
import 'team_migration.dart';
import 'team_phone_setup_screen.dart';
import 'team_settings_screen.dart';

/// Opens the AI Team page for the connected server. [onOpenRun] replaces
/// what a task row opens (a task's conversation that opened this page
/// takes its own task back instead of stacking a second copy).
Future<void> openTeamPage(
  BuildContext context,
  ConnectionController connection, {
  TeamHostProbe? probe,
  Future<VoiceDeviceInfo> Function()? deviceProbe,
  DateTime Function()? now,
  ValueChanged<OrchestrationRun>? onOpenRun,
}) => pushKitPage<void>(
  context,
  (_) => TeamPage(
    connection: connection,
    probe: probe,
    deviceProbe: deviceProbe,
    now: now,
    onOpenRun: onOpenRun,
  ),
);

/// Settings › AI Team's door (setup only): Team settings while the team is
/// on, the intro and turn-on flow while it is off. The work page stays
/// behind the Work strip and search.
Future<void> openTeamSetup(
  BuildContext context,
  ConnectionController connection, {
  TeamHostProbe? probe,
  Future<VoiceDeviceInfo> Function()? deviceProbe,
}) {
  final profile = connection.profile;
  final team = connection.orchestration;
  if (profile?.orchestration?.provider == OrchestrationProvider.phoneEngine) {
    // The phone's own team: the page itself shows the team or how to turn
    // it on, so the door is always the page.
    return openTeamPage(context, connection);
  }
  if (profile != null &&
      profile.orchestration != null &&
      team != null &&
      team.profileId == profile.id) {
    if (team.capabilities.projectLifecycle && team.projectController != null) {
      return openTeamPage(context, connection);
    }
    return openTeamSettings(
      context,
      controller: team,
      connection: connection,
      probe: probe,
    );
  }
  return openTeamPage(
    context,
    connection,
    probe: probe,
    deviceProbe: deviceProbe,
  );
}

/// The phone's own team when it is on but not answering: one headline,
/// one action, and the progress of a check that is already running.
class PhoneTeamOffPage extends StatelessWidget {
  const PhoneTeamOffPage({super.key, required this.connection});

  final ConnectionController connection;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final flow = phoneTeamSetupForContext(context, connection);
    return ListenableBuilder(
      listenable: flow,
      builder: (context, _) {
        final waiting = flow.phase == PhoneTeamSetupPhase.confirming;
        final working = flow.isRunning && !waiting;
        final failed =
            flow.phase == PhoneTeamSetupPhase.failed && flow.automatic;
        final done = PhoneTeamSetupStep.values
            .where((s) => flow.took(s) != null)
            .length;
        final String title;
        final String? body;
        final IconData icon;
        final AppStatusTone tone;
        final String action;
        if (waiting) {
          // The check is waiting for the person, not working: say so.
          title = l10n.phoneTeamStripWaiting;
          body = l10n.phoneTeamStripReview;
          icon = AppIconography.phone;
          tone = AppStatusTone.neutral;
          action = l10n.phoneTeamOffReview;
        } else if (working) {
          title = l10n.phoneTeamStripChecking;
          body = l10n.phoneTeamStripStep(
            (done + 1).clamp(1, PhoneTeamSetupStep.values.length),
            PhoneTeamSetupStep.values.length,
          );
          icon = AppIconography.waiting;
          tone = AppStatusTone.progress;
          action = l10n.teamIntroTurnOnPhone;
        } else if (failed) {
          final copy = phoneTeamProblemCopy(
            l10n,
            flow.problem ?? PhoneTeamSetupProblem.engine,
            flow,
          );
          title = copy.title;
          body = copy.body;
          icon = AppIconography.warning;
          tone = AppStatusTone.failure;
          action = l10n.teamStartAgain;
        } else {
          title = l10n.phoneTeamOffTitle;
          body = l10n.phoneTeamOffBody;
          icon = AppIconography.cloudOff;
          tone = AppStatusTone.neutral;
          action = l10n.teamIntroTurnOnPhone;
        }
        return KitScreen(
          topBar: KitTopBar(title: l10n.teamUiHomeTitle),
          width: KitScreenWidth.reading,
          body: KitStateView(
            key: ValueKey(
              'phone-team-off-${waiting
                  ? 'waiting'
                  : working
                  ? 'working'
                  : failed
                  ? 'failed'
                  : 'off'}',
            ),
            icon: icon,
            tone: tone,
            title: title,
            body: body,
            primary: KitAction(
              key: const ValueKey('phone-team-off-turn-on'),
              label: action,
              icon: AppIconography.play,
              onPressed: () =>
                  unawaited(openPhoneTeamSetup(context, connection)),
            ),
          ),
        );
      },
    );
  }
}

class TeamPage extends StatelessWidget {
  const TeamPage({
    super.key,
    required this.connection,
    this.probe,
    this.deviceProbe,
    this.now,
    this.onOpenRun,
  });

  final ConnectionController connection;

  /// The Gas City probe (discovery and the address form); tests pass a
  /// fake.
  final TeamHostProbe? probe;

  /// The device facts the phone's pre-flight reads; tests stand in.
  final Future<VoiceDeviceInfo> Function()? deviceProbe;

  /// Clock for the team's ages; tests pin it.
  final DateTime Function()? now;

  /// What a task row opens; its conversation when null.
  final ValueChanged<OrchestrationRun>? onOpenRun;

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: connection,
    builder: (context, _) {
      final profile = connection.profile;
      final team = connection.orchestration;
      if (profile != null &&
          profile.orchestration != null &&
          team != null &&
          team.profileId == profile.id) {
        final projects = team.projectController;
        final engine =
            profile.orchestration!.provider ==
            OrchestrationProvider.phoneEngine;
        if (engine &&
            (team.phase == OrchestrationPhase.failed ||
                (team.phase == OrchestrationPhase.ready &&
                    !(team.capabilities.projectLifecycle &&
                        projects != null)))) {
          // The phone's team is not answering (an app update stops it) or
          // cannot run projects yet: say so and offer the one-tap setup.
          return PhoneTeamOffPage(
            key: const ValueKey('team-page-phone-off'),
            connection: connection,
          );
        }
        if (team.capabilities.projectLifecycle && projects != null) {
          if (engine) TeamExecutionGate.bind(team, connection);
          return TeamProjectsScreen(
            key: ObjectKey(projects),
            controller: projects,
            onLeaveDemo: () async {
              try {
                await leaveTeamProjectDemo(connection);
              } catch (_) {
                if (!context.mounted) return;
                final copy = AppLocalizations.of(context);
                await showKitAlert(
                  context,
                  title: copy.teamProjectOff,
                  body: copy.teamProjectError,
                );
              }
            },
          );
        }
        // A new controller (a changed address) is a new team: fresh state.
        return TeamMigrationGate(
          connection: connection,
          child: TeamHomeScreen(
            key: ObjectKey(team),
            controller: team,
            connection: connection,
            probe: probe,
            now: now,
            onOpenRun: onOpenRun,
            // This page follows the connection by itself.
            onTeamChanged: () {},
          ),
        );
      }
      if (profile != null && profile.orchestration != null) {
        // Turned on a moment ago: the team starts in the next frame.
        final l10n = lookupAppLocalizations(Localizations.localeOf(context));
        return KitScreen(
          key: const ValueKey('team-page-starting'),
          topBar: KitTopBar(title: l10n.teamUiHomeTitle),
          loading: true,
          loadingLabel: l10n.teamUiCardLoading,
          body: const SizedBox.shrink(),
        );
      }
      // Off, or no server: the same page says what the team does and
      // sets it up. Keyed by the server so a switch looks afresh.
      return TeamIntroScreen(
        key: ValueKey('team-page-off-${profile?.id}'),
        controller: connection,
        probe: probe,
        deviceProbe: deviceProbe,
      );
    },
  );
}
