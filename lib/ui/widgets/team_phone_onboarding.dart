/// AI Team on this phone through phone setup v2 (programme P1.7): the one
/// way a phone's team is set up, in the app's own Linux.
///
/// - [openTeamOnThisPhone] is "Set up AI Team on this phone" everywhere
///   (the team's intro, Plugins' AI Team). It opens Add tools with AI Team
///   switched on and runs it as a v2 job through phone setup's progress
///   screen: its steps, success and failure are the setup's own rows. When
///   AI Team is installed already it goes straight on.
/// - [TeamPhoneReadyScreen] is the ready page after it: it turns the team
///   on for the project ([BuiltinTeam.turnOn]), shows the stages as they
///   pass, says what went wrong with the one retry, and ends with "Give the
///   team a first task", which lands in its conversation.
///
/// Built from kit parts only (kit-v2 §9); the ready page uses phone setup's
/// own hero ([PhoneSetupHero]), so it reads as the same family as
/// phone-setup-ready.
library;

import 'dart:async';

import 'package:flutter/material.dart';

import '../../builtin/builtin_linux.dart' show BuiltinLinux;
import '../../builtin/setup/components.dart' show SetupComponentIds;
import '../../builtin/setup/phone_setup.dart';
import '../../builtin/setup/setup_contract.dart';
import '../../builtin/team/builtin_team.dart';
import '../../builtin/team/builtin_team_job.dart';
import '../../diagnostics/failed_job_report.dart';
import '../../feedback/bug_report.dart' show failedJobReportAction;
import '../../l10n/app_localizations.dart';
import '../../state/connection.dart';
import '../../state/orchestration.dart';
import '../../state/profiles.dart';
import '../app_theme.dart';
import '../kit/kit.dart';
import '../kit/scenes/setup_ready_scene.dart';
import '../kit/scenes/setup_unplugged_scene.dart';
import '../kit/scenes/team_scenes.dart';
import '../screens/phone_setup/phone_setup_hero.dart';
import '../screens/phone_setup/phone_setup_routes.dart';
import '../screens/project_folder_actions.dart';
import '../screens/team_conversation/team_conversation.dart';
import 'product_states.dart' show productErrorText;
import 'builtin_team_section.dart'
    show
        builtinTeamFailureText,
        builtinTeamProjectName,
        builtinTeamStageRows,
        sharedBuiltinTeam;

AppLocalizations _copy(BuildContext context) =>
    lookupAppLocalizations(Localizations.localeOf(context));

/// Runs the Add tools job for [ids] and shows its progress until it
/// closes. Tests replace it; the app runs phone setup's progress screen.
@visibleForTesting
Future<void> Function(BuildContext context, Set<String> ids)?
debugTeamPhoneRunJob;

Future<void> _runJob(
  BuildContext context,
  Set<String> ids,
  ServerProfile profile,
) async {
  final override = debugTeamPhoneRunJob;
  if (override != null) return override(context, ids);
  final runtime = BuiltinLinux.runtimeForProfile(profile);
  await PhoneSetup.engine.run(
    ids,
    params: {
      ...SetupJobParams.adding(ids),
      'opencode': {'runtime': runtime.wireName},
    },
  );
  if (context.mounted) await openPhoneSetupProgress(context);
}

/// Whether [progress] is a finished job that put AI Team on the phone.
bool _installedTeam(SetupProgress progress) =>
    progress.state == SetupState.done &&
    progress.components.any(
      (component) =>
          component.id == SetupComponentIds.aiTeam &&
          (component.state == ComponentState.done ||
              component.state == ComponentState.skipped),
    );

/// "Set up AI Team on this phone": Add tools with AI Team switched on in
/// the app's own Linux, then the ready page that turns it on for the
/// project. AI Team that is installed already skips straight to
/// the ready page. Returns once the person is back where they started.
Future<void> openTeamOnThisPhone(
  BuildContext context,
  ConnectionController connection,
) async {
  final profile = connection.profile;
  if (profile == null) return;
  if (connection.managedOpenCode2MigrationRequired) {
    await showKitAlert(
      context,
      title: _copy(context).phoneServerCardFailedTitle,
      body: _copy(context).openCode2UpgradeFailed,
    );
    return;
  }
  final engine = PhoneSetup.engine;
  var installed = false;
  try {
    installed = (await engine.installedOptional()).contains(
      SetupComponentIds.aiTeam,
    );
  } catch (_) {
    // Unknown: the Add tools sheet reads it again and says so.
  }
  if (!context.mounted) return;
  if (!installed) {
    final ids = await showPhoneSetupCustomize(
      context,
      addMode: true,
      selected: const {SetupComponentIds.aiTeam},
    );
    if (ids == null || ids.isEmpty || !context.mounted) return;
    final before = engine.progress.value.jobId;
    await _runJob(context, ids, profile);
    if (!context.mounted) return;
    final after = engine.progress.value;
    // Left before it finished, or it failed: the progress screen said so
    // and keeps Continue; the team is not on yet.
    if (!ids.contains(SetupComponentIds.aiTeam) ||
        after.jobId == before ||
        !_installedTeam(after)) {
      return;
    }
  }
  await pushKitPage<void>(
    context,
    (_) => TeamPhoneReadyScreen(connection: connection),
    settings: const RouteSettings(name: teamPhoneReadyRouteName),
  );
}

/// The ready page's route name.
const teamPhoneReadyRouteName = 'team-phone-ready';

/// Gives the in-app profile the team's plugin config, as Plugins' AI Team
/// section does after its own turn-on.
Future<void> _enableBuiltin(
  ConnectionController connection,
  ServerProfile profile,
) async {
  profile.orchestration = BuiltinTeam.config();
  await connection.store.upsert(profile);
  connection.syncOrchestration();
}

/// The words for what stopped a turn-on.
String teamPhoneTurnOnFailureText(AppLocalizations l10n, Object error) =>
    switch (error) {
      final BuiltinTeamException e => builtinTeamFailureText(l10n, e),
      final other => l10n.aiteamComponentFailed(
        productErrorText(other, l10n: l10n),
      ),
    };

enum _Ready { choose, turningOn, failed, ready }

/// The ready page after AI Team is on the phone (phone-setup-ready's team
/// moment, map: team-phone-onboarding-success): it turns the team on for
/// the open project, shows the stages as they pass, and ends with "Give
/// the team a first task".
///
/// With no project open it asks for one first, through the same folder
/// sheet Work uses. A failure names what went wrong and offers the one
/// retry. Leaving never stops the turn-on: it is the app's one job, and
/// coming back follows it.
class TeamPhoneReadyScreen extends StatefulWidget {
  const TeamPhoneReadyScreen({
    super.key,
    required this.connection,
    this.team,
    this.chooseProject,
    this.startTask,
  });

  final ConnectionController connection;

  /// The in-app team; tests pass a fake.
  final BuiltinTeam? team;

  /// Picks the project when none is open; the folder sheet by default.
  final Future<String?> Function(BuildContext context)? chooseProject;

  /// "Give the team a first task"; [TeamConversation.start] by default.
  final Future<void> Function(
    BuildContext context,
    OrchestrationController team,
  )?
  startTask;

  @override
  State<TeamPhoneReadyScreen> createState() => _TeamPhoneReadyScreenState();
}

class _TeamPhoneReadyScreenState extends State<TeamPhoneReadyScreen> {
  BuiltinTeamJob get _job => BuiltinTeamJob.shared;

  _Ready _state = _Ready.choose;
  String? _project;
  bool _wasRunning = false;
  bool _starting = false;

  /// Moves the stage times on while the job runs.
  Timer? _ticker;

  @override
  void initState() {
    super.initState();
    _job.addListener(_onJob);
    widget.connection.addListener(_onConnection);
    if (_job.running) {
      // Came back to a turn-on that is still going: follow it.
      _project = _job.project;
      _state = _Ready.turningOn;
      _wasRunning = true;
      _syncTicker();
      return;
    }
    final open = widget.connection.directory;
    if (open != null && open.trim().isNotEmpty) {
      _project = open;
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) unawaited(_turnOn());
      });
    }
  }

  @override
  void dispose() {
    _job.removeListener(_onJob);
    widget.connection.removeListener(_onConnection);
    _ticker?.cancel();
    super.dispose();
  }

  void _onConnection() {
    if (mounted) setState(() {});
  }

  void _syncTicker() {
    if (_job.running) {
      _ticker ??= Timer.periodic(const Duration(seconds: 1), (_) {
        if (mounted) setState(() {});
      });
    } else {
      _ticker?.cancel();
      _ticker = null;
    }
  }

  void _onJob() {
    if (!mounted) return;
    final finished = _wasRunning && !_job.running;
    _wasRunning = _job.running;
    _syncTicker();
    setState(() {
      if (finished) {
        _state = _job.error == null ? _Ready.ready : _Ready.failed;
      }
    });
  }

  Future<void> _choose() async {
    final choose =
        widget.chooseProject ??
        (context) =>
            ProjectFolderActions.openFolder(context, widget.connection);
    final path = await choose(context);
    if (path == null || !mounted) return;
    setState(() => _project = path);
    await _turnOn();
  }

  Future<void> _turnOn() async {
    final project = _project;
    final profile = widget.connection.profile;
    if (project == null || profile == null || _job.running) return;
    final l10n = _copy(context);
    final connection = widget.connection;
    setState(() => _state = _Ready.turningOn);
    final team = widget.team ?? sharedBuiltinTeam;
    final notice = l10n.aiteamComponentNotice;
    await _job.run(
      stages: BuiltinTeamStage.values,
      project: project,
      work: (onStage) async {
        await team.turnOn(project, notice: notice, onStage: onStage);
        await _enableBuiltin(connection, profile);
      },
    );
  }

  Future<void> _firstTask() async {
    final team = widget.connection.orchestration;
    if (team == null || _starting) return;
    setState(() => _starting = true);
    final start =
        widget.startTask ??
        (context, team) async {
          final record = await TeamConversation.start(context, team);
          // The task was given and its conversation closed again: the
          // team is on, so this page has done its part.
          if (record != null &&
              record.status != MutationStatus.rejected &&
              context.mounted) {
            Navigator.of(context).maybePop();
          }
        };
    try {
      await start(context, team);
    } finally {
      if (mounted) setState(() => _starting = false);
    }
  }

  void _close() => Navigator.of(context).maybePop();

  @override
  Widget build(BuildContext context) {
    final l10n = _copy(context);
    final tokens = KitTokens.of(context);
    final name = builtinTeamProjectName(_project ?? _job.project ?? '');
    final job = _job;
    final running = job.running;
    final stages = job.stages.isEmpty
        ? null
        : Column(
            key: const ValueKey('team-phone-ready-stages'),
            crossAxisAlignment: CrossAxisAlignment.stretch,
            mainAxisSize: MainAxisSize.min,
            children: [
              for (final row in builtinTeamStageRows(
                l10n,
                job,
                failed: _state == _Ready.failed,
                project: name,
              ))
                Padding(
                  padding: EdgeInsetsDirectional.only(bottom: tokens.space1),
                  child: row,
                ),
            ],
          );
    final error = job.error;
    final detail = error is BuiltinTeamException ? error.detail.trim() : '';

    final Widget body;
    switch (_state) {
      case _Ready.choose:
        body = PhoneSetupHero(
          key: const ValueKey('team-phone-ready-choose'),
          scene: const TeamWakingScene(),
          title: l10n.teamPhoneReadyChooseTitle,
          titleKey: const ValueKey('team-phone-ready-title'),
          body: l10n.aiteamComponentTurnOnBody,
          primary: KitAction(
            key: const ValueKey('team-phone-ready-choose-project'),
            label: l10n.teamUiPhoneChooseProjectTitle,
            onPressed: () => unawaited(_choose()),
          ),
        );
      case _Ready.turningOn:
        body = PhoneSetupHero(
          key: const ValueKey('team-phone-ready-turning-on'),
          scene: const TeamWakingScene(),
          ambient: running,
          title: l10n.teamPhoneReadyTurningOnTitle,
          titleKey: const ValueKey('team-phone-ready-title'),
          body: l10n.aiteamComponentTurnOnExpectation,
          progress: const KitProgress.waiting(),
          content: stages,
        );
      case _Ready.failed:
        body = PhoneSetupHero(
          key: const ValueKey('team-phone-ready-failed'),
          scene: const SetupUnpluggedScene(),
          title: l10n.teamPhoneReadyFailedTitle,
          titleKey: const ValueKey('team-phone-ready-title'),
          body: error == null ? null : teamPhoneTurnOnFailureText(l10n, error),
          bodyKey: const ValueKey('team-phone-ready-failure'),
          bodyTone: AppStatusTone.failure,
          content: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            mainAxisSize: MainAxisSize.min,
            children: [
              ?stages,
              // The raw output, last and folded (KIT-33).
              if (detail.isNotEmpty)
                KitDetailsFold(
                  label: l10n.aiteamComponentShowDetails,
                  text: detail,
                  foldKey: const ValueKey('team-phone-ready-details'),
                ),
            ],
          ),
          primary: KitAction(
            key: const ValueKey('team-phone-ready-retry'),
            label: l10n.aiteamComponentTurnOn(name),
            icon: AppIconography.retry,
            onPressed: () => unawaited(_turnOn()),
          ),
          // Report this failure (P8.4), with what stopped the turn-on.
          tertiary: [
            ?failedJobReportAction(
              context,
              key: const ValueKey('team-phone-ready-report'),
              title: error == null
                  ? null
                  : teamPhoneTurnOnFailureText(l10n, error),
              capture: () => FailedJobReport.teamSetup(_job),
            ),
          ],
        );
      case _Ready.ready:
        final team = widget.connection.orchestration;
        body = PhoneSetupHero(
          key: const ValueKey('team-phone-ready-done'),
          scene: const SetupReadyScene(),
          entranceDuration: KitMotion.celebration,
          title: l10n.teamUiPhoneSuccessTitle,
          titleKey: const ValueKey('team-phone-ready-title'),
          body: l10n.teamPhoneReadyBody(name),
          liveRegion: false,
          primary: KitAction(
            key: const ValueKey('team-phone-ready-first-task'),
            label: l10n.teamPhoneReadyFirstTask,
            icon: AppIconography.add,
            working: _starting,
            onPressed: team == null || _starting
                ? null
                : () => unawaited(_firstTask()),
          ),
        );
    }
    return KitScreen(
      // Close only, as phone-setup-ready: the drawing and its title are
      // the page's heading, and leaving never stops the turn-on.
      topBar: KitTopBar(
        title: '',
        exit: KitTopBarExit.close,
        exitKey: const ValueKey('team-phone-ready-close'),
        onExit: _close,
      ),
      width: KitScreenWidth.reading,
      body: KeyedSubtree(key: const ValueKey('team-phone-ready'), child: body),
    );
  }
}
