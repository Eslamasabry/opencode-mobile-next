/// AI Team on this phone through phone setup v2 (programme P1.7): the one
/// way a phone's team is set up, whichever host runs OpenCode (inside the
/// app or in Termux).
///
/// - [openTeamOnThisPhone] is "Set up AI Team on this phone" everywhere
///   (the team's intro, Plugins' AI Team, the Termux "On this phone"
///   section). It opens Add tools with AI Team switched on, on the host of
///   the connected server, and runs it as a v2 job through that host's
///   progress screen: its steps, success and failure are the setup's own
///   rows (the old five-step block of the Termux wizard is gone). When AI
///   Team is installed already it goes straight on.
/// - [TeamPhoneReadyScreen] is the ready page after it: it turns the team
///   on for the project (in-app: [BuiltinTeam.turnOn]; Termux:
///   [TermuxTeamRuntime] `install` check, `init`, `start`), shows the
///   stages as they pass, says what went wrong with the one retry, and ends
///   with "Give the team a first task", which lands in its conversation.
/// - [TeamPhoneKilledNotice] says on the team page that Android stopped the
///   phone's Termux team, with "Start the team again".
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
import '../../termux/bridge.dart';
import '../../termux/team_runtime.dart';
import '../app_theme.dart';
import '../kit/kit.dart';
import '../kit/scenes/setup_ready_scene.dart';
import '../kit/scenes/setup_unplugged_scene.dart';
import '../kit/scenes/team_scenes.dart';
import '../screens/phone_setup/phone_setup_hero.dart';
import '../screens/phone_setup/phone_setup_routes.dart';
import '../screens/phone_setup/phone_setup_termux_job_screen.dart'
    show openPhoneSetupTermuxJob;
import '../screens/project_folder_actions.dart';
import '../screens/team_conversation/team_conversation.dart';
import 'product_states.dart' show productErrorDetails, productErrorText;
import 'builtin_team_section.dart'
    show
        builtinTeamFailureText,
        builtinTeamProjectName,
        builtinTeamStageRows,
        sharedBuiltinTeam;

AppLocalizations _copy(BuildContext context) =>
    lookupAppLocalizations(Localizations.localeOf(context));

TermuxTeamRuntime? _sharedRuntime;

/// The Termux team runtime the phone's widgets share; one instance per app
/// so the arm64 probe is read once.
TermuxTeamRuntime get teamPhoneRuntime =>
    debugTeamPhoneRuntime ?? (_sharedRuntime ??= TermuxTeamRuntime());

/// Tests swap the shared runtime for a fake; reset to null in `addTearDown`.
@visibleForTesting
TermuxTeamRuntime? debugTeamPhoneRuntime;

/// How often the Termux section re-reads status while a verb runs.
const teamPhonePollInterval = Duration(seconds: 2);

/// The host a phone's team is installed on: Termux for the Termux server,
/// the app's own Linux otherwise.
SetupHostKind teamPhoneHostOf(ServerProfile profile) =>
    TermuxBridge.managesServerUrl(profile.baseUrl)
    ? SetupHostKind.termux
    : SetupHostKind.builtin;

/// The honest sentence for a failed Termux team [status] (03-onboarding
/// §5); the checksum refusal gets its own.
String teamPhoneFailureText(AppLocalizations l10n, TeamRuntimeStatus status) {
  final reason = status.reason ?? '';
  if (status.phase == TeamRuntimePhase.ready && !status.isReady) {
    return l10n.teamUiPhoneFailedHealth(status.url);
  }
  if (status.checksumMismatch) {
    final parts = reason.split(RegExp(r'\s+'));
    return l10n.teamUiPhoneFailedChecksum(parts.length > 1 ? parts[1] : 'file');
  }
  if (reason == 'unsupported-arch') {
    return l10n.teamUiPhoneFailedUnsupportedArch;
  }
  if (reason == 'no-space') {
    final detail = status.lastError ?? '';
    return l10n.teamUiPhoneFailedNoSpace(
      detail.isEmpty ? '' : '${detail.split(':').last.trim()}.',
    );
  }
  final download = status.downloadFailure;
  if (download != null) return teamPhoneDownloadFailureText(l10n, download);
  if (reason.startsWith('manifest')) return l10n.teamUiPhoneFailedDownload;
  if (reason == 'packages') return l10n.teamUiPhoneFailedPackages;
  if (reason.startsWith('project')) return l10n.teamUiPhoneFailedProject;
  if (reason.startsWith('gc-') ||
      reason == 'not-installed' ||
      reason == 'no-city') {
    return l10n.teamUiPhoneFailedCity;
  }
  if (reason == 'supervisor-exited') {
    return l10n.teamUiPhoneFailedSupervisorExited;
  }
  if (reason == 'health-timeout') {
    return l10n.teamUiPhoneFailedHealth(status.url);
  }
  final error = status.lastError ?? '';
  if (reason == 'interrupted' || error.contains('stopped unexpectedly')) {
    return l10n.teamUiPhoneFailedInterrupted;
  }
  // The script's own sentence when it is one; its output said in words
  // otherwise. A bare reason id or phase is not words: the output folded
  // under Details says what it was.
  if (error.isEmpty) return l10n.productErrorTermux;
  return l10n.teamUiPhoneFailedReason(productErrorText(error, l10n: l10n));
}

/// The runtime's own words for a failed [status] (the step, the reason id
/// and the script's last error), for Details and reports only.
String teamPhoneFailureDetails(TeamRuntimeStatus status) =>
    _TermuxTeamFailure(status).toString();

/// Why a download failed, naming the server that was asked, so a user can
/// tell a phone that is offline from a server that refused the file (issue
/// #87: a download pointed at a private address failed as "did not finish").
String teamPhoneDownloadFailureText(
  AppLocalizations l10n,
  TeamDownloadFailure failure,
) {
  final host = failure.host;
  if (host.isEmpty) return l10n.teamUiPhoneFailedDownload;
  final code = failure.code?.toString() ?? '?';
  return switch (failure.kind) {
    TeamDownloadFailureKind.dns => l10n.teamUiPhoneFailedDownloadDns(host),
    TeamDownloadFailureKind.connect => l10n.teamUiPhoneFailedDownloadConnect(
      host,
    ),
    TeamDownloadFailureKind.timeout => l10n.teamUiPhoneFailedDownloadTimeout(
      host,
    ),
    TeamDownloadFailureKind.tls => l10n.teamUiPhoneFailedDownloadTls(host),
    TeamDownloadFailureKind.http => l10n.teamUiPhoneFailedDownloadHttp(
      host,
      code,
    ),
    TeamDownloadFailureKind.interrupted =>
      l10n.teamUiPhoneFailedDownloadInterrupted(host),
    TeamDownloadFailureKind.write => l10n.teamUiPhoneFailedDownloadWrite,
    TeamDownloadFailureKind.other => l10n.teamUiPhoneFailedDownloadOther(
      host,
      code,
    ),
  };
}

/// Writes the Termux team's config onto [profile] and lets the connection
/// build its controller (03 §3). The store instance is the source of truth
/// for the connected profile, so the same object is updated and saved.
Future<void> teamPhoneEnable(
  ConnectionController connection,
  ServerProfile profile,
  TermuxTeamRuntime runtime,
  TeamRuntimeStatus status,
) async {
  final config = runtime.phoneOrchestrationConfig(status);
  profile.orchestration = config;
  await connection.store.upsert(profile);
  connection.syncOrchestration();
}

/// Runs the Add tools job for [ids] on [host] and shows its progress until
/// it closes. Tests replace it; the app runs the host's own progress screen
/// (the in-app one, or the Termux one with its person steps).
@visibleForTesting
Future<void> Function(
  BuildContext context,
  SetupHostKind host,
  Set<String> ids,
)?
debugTeamPhoneRunJob;

Future<void> _runJob(
  BuildContext context,
  SetupHostKind host,
  Set<String> ids,
  ServerProfile profile,
) async {
  final override = debugTeamPhoneRunJob;
  if (override != null) return override(context, host, ids);
  if (host == SetupHostKind.termux) {
    return openPhoneSetupTermuxJob(context, adding: ids);
  }
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

/// "Set up AI Team on this phone": Add tools with AI Team switched on, on
/// the host of the connected server, then the ready page that turns it on
/// for the project. AI Team that is installed already skips straight to
/// the ready page. Returns once the person is back where they started.
Future<void> openTeamOnThisPhone(
  BuildContext context,
  ConnectionController connection, {
  TermuxTeamRuntime? runtime,
}) async {
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
  final host = teamPhoneHostOf(profile);
  final engine = PhoneSetup.of(host);
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
      host: host,
    );
    if (ids == null || ids.isEmpty || !context.mounted) return;
    final before = engine.progress.value.jobId;
    await _runJob(context, host, ids, profile);
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
    (_) => TeamPhoneReadyScreen(
      connection: connection,
      host: host,
      runtime: runtime,
    ),
    settings: const RouteSettings(name: teamPhoneReadyRouteName),
  );
}

/// The ready page's route name.
const teamPhoneReadyRouteName = 'team-phone-ready';

/// A Termux step that did not reach the phase it should have.
class _TermuxTeamFailure implements Exception {
  const _TermuxTeamFailure(this.status, {this.output = ''});

  final TeamRuntimeStatus status;

  /// The last lines `aiteam.sh` printed (its `aiteam.log`), where the
  /// programs say what went wrong: the status alone only says "the output
  /// above says why" (issue #112).
  final String output;

  /// What a failure report carries: the step and why, in the runtime's own
  /// words, then the output (the report redacts it).
  @override
  String toString() => [
    'aiteam.sh ${status.verb}: ${status.rawPhase}',
    if (status.reason case final reason? when reason.isNotEmpty) reason,
    if (status.lastError case final error? when error.isNotEmpty) error,
    if (output.trim().isNotEmpty) ...['', 'Output:', output.trimRight()],
  ].join('\n');
}

/// How many lines of `aiteam.log` a turn-on failure carries: the whole
/// `init` of a store that failed three times fits, and the report keeps
/// its last 120 lines.
const _termuxFailureLogLines = 80;

/// A [_TermuxTeamFailure] for [status] with the log's last lines; a log
/// that cannot be read leaves just the status.
Future<_TermuxTeamFailure> _termuxFailure(
  TermuxTeamRuntime runtime,
  TeamRuntimeStatus status,
) async => _TermuxTeamFailure(
  status,
  output: await runtime.logTail(lines: _termuxFailureLogLines),
);

/// The turn-on of the Termux team, one at a time, kept outside the page so
/// it goes on when the person leaves (the in-app one is
/// [BuiltinTeamJob.shared]).
final teamPhoneTermuxJob = BuiltinTeamJob();

/// Turns the Termux team on for [project]: the programs the v2 job
/// installed are recorded (`install` checks and downloads nothing when they
/// are there), the store is made next to the project (`init`), the
/// supervisor starts, and the profile gets the team's config.
Future<void> _turnOnTermux(
  TermuxTeamRuntime runtime,
  ConnectionController connection,
  ServerProfile profile,
  String project,
  void Function(BuiltinTeamStage stage) onStage,
) async {
  onStage(BuiltinTeamStage.preparing);
  var status = await runtime.install();
  if (status.phase != TeamRuntimePhase.installed) {
    throw await _termuxFailure(runtime, status);
  }
  onStage(BuiltinTeamStage.addingProject);
  status = await runtime.init(project);
  if (status.phase != TeamRuntimePhase.cityReady) {
    throw await _termuxFailure(runtime, status);
  }
  onStage(BuiltinTeamStage.starting);
  status = await runtime.start();
  if (!status.isReady) throw await _termuxFailure(runtime, status);
  onStage(BuiltinTeamStage.waiting);
  await teamPhoneEnable(connection, profile, runtime, status);
}

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
      final _TermuxTeamFailure e => teamPhoneFailureText(l10n, e.status),
      final TermuxBridgeException e =>
        '${l10n.teamUiPhoneDispatchFailed} ${productErrorText(e, l10n: l10n)}',
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
    required this.host,
    this.runtime,
    this.team,
    this.chooseProject,
    this.startTask,
  });

  final ConnectionController connection;

  /// Where the team was installed: the app's own Linux or Termux.
  final SetupHostKind host;

  /// The Termux team runtime; tests pass a fake.
  final TermuxTeamRuntime? runtime;

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
  bool get _termux => widget.host == SetupHostKind.termux;

  BuiltinTeamJob get _job =>
      _termux ? teamPhoneTermuxJob : BuiltinTeamJob.shared;

  TermuxTeamRuntime get _runtime => widget.runtime ?? teamPhoneRuntime;

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
    if (_termux) {
      final runtime = _runtime;
      await _job.run(
        stages: BuiltinTeamStage.values,
        project: project,
        work: (onStage) =>
            _turnOnTermux(runtime, connection, profile, project, onStage),
      );
    } else {
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

/// The team page's word that Android stopped the phone's Termux team while
/// the app was away (map: team-phone-onboarding-killed, merged into the
/// team page), with "Start the team again". Nothing when the team runs
/// somewhere else, runs fine, or its state cannot be read.
class TeamPhoneKilledNotice extends StatefulWidget {
  const TeamPhoneKilledNotice({
    super.key,
    required this.controller,
    this.runtime,
    this.onKilledChanged,
  });

  final OrchestrationController controller;

  /// The Termux team runtime; tests pass a fake.
  final TermuxTeamRuntime? runtime;

  /// Told whenever the notice starts or stops saying Android stopped the
  /// team, so the page under it says nothing that contradicts it (no "not
  /// answering, the app keeps trying" under "start the team again").
  final ValueChanged<bool>? onKilledChanged;

  /// Whether [config] is the team Termux runs on this phone (the in-app
  /// team restarts by itself).
  static bool appliesTo(OrchestrationConfig config) =>
      config.hostMode == OrchestrationHostMode.phone &&
      !BuiltinTeam.isBuiltinConfig(config) &&
      TermuxBridge.supported;

  @override
  State<TeamPhoneKilledNotice> createState() => _TeamPhoneKilledNoticeState();
}

class _TeamPhoneKilledNoticeState extends State<TeamPhoneKilledNotice> {
  TermuxTeamRuntime get _runtime => widget.runtime ?? teamPhoneRuntime;

  bool _killed = false;
  bool _busy = false;
  String? _error;

  /// The raw failure behind [_error], for Copy details only.
  String? _errorDetails;

  @override
  void initState() {
    super.initState();
    if (TeamPhoneKilledNotice.appliesTo(widget.controller.config)) {
      unawaited(_read());
    }
  }

  Future<void> _read() async {
    bool killed;
    try {
      killed = (await _runtime.status()).killedByAndroid;
    } catch (_) {
      killed = false;
    }
    if (mounted && killed != _killed) _setKilled(killed);
  }

  void _setKilled(bool killed) {
    setState(() => _killed = killed);
    widget.onKilledChanged?.call(killed);
  }

  Future<void> _start() async {
    final l10n = _copy(context);
    setState(() {
      _busy = true;
      _error = null;
      _errorDetails = null;
    });
    try {
      final status = await _runtime.start();
      if (!mounted) return;
      if (status.isReady) {
        _setKilled(false);
        await widget.controller.retry();
      } else {
        setState(() {
          _error = teamPhoneFailureText(l10n, status);
          _errorDetails = teamPhoneFailureDetails(status);
        });
      }
    } on TermuxBridgeException catch (error) {
      if (mounted) {
        setState(() {
          _error = l10n.teamUiPhoneActionFailed(
            productErrorText(error, l10n: l10n),
          );
          _errorDetails = productErrorDetails(error);
        });
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    if (!_killed) return const SizedBox.shrink();
    final l10n = _copy(context);
    final tokens = KitTokens.of(context);
    final start = KitAction(
      key: const ValueKey('team-phone-killed-start'),
      label: l10n.teamPhoneStartTeamAgain,
      icon: AppIconography.play,
      working: _busy,
      onPressed: _busy ? null : () => unawaited(_start()),
    );
    return Padding(
      padding: EdgeInsetsDirectional.fromSTEB(
        tokens.gutter,
        tokens.space2,
        tokens.gutter,
        tokens.space2,
      ),
      child: _error == null
          ? KitNotice(
              key: const ValueKey('team-phone-killed'),
              icon: AppIconography.warning,
              // Neutral, as Claude Code's "Android stopped it": amber is
              // needs-you only (LOOK-4).
              tone: AppStatusTone.neutral,
              message: l10n.teamUiPhoneKilled,
              actions: [start],
            )
          // A start that failed: the words, Start again, and the raw
          // output only behind Copy details.
          : KitNotice.error(
              key: const ValueKey('team-phone-killed'),
              message: _error!,
              details: _errorDetails,
              retry: start,
            ),
    );
  }
}
