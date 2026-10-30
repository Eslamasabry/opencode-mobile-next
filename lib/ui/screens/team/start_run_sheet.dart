/// Start a run (TEAM-204; 02-ux-flows-and-screens §7, 06-decisions §A.3):
/// the sheet behind the AI Team home's primary button, in the kit's one
/// sheet frame ([showKitSheet]). **Objective** (multi-line, kept as a
/// draft), **Project** ("Let the planner choose" by default),
/// **Supervision** (High / Balanced / Autonomous with their descriptions;
/// it starts at this server's level from Settings › What runs by itself,
/// High until the person chose another there),
/// **Planner** (shown, not chosen: the Mayor), **Boundaries** (read-only
/// host policy from `controller.policy`, TEAM-207; the row is absent when
/// the host reports none rather than invented), then [Send to planner].
///
/// Sending is one `messageAgent` to `gastown.mayor` — the supervisor has
/// no objective endpoint, so the objective and the supervision line go as
/// the message ([composeTeamPlanningMessage]). A planner the host lists as
/// suspended or stopped (the lean profile), or no planner at all, means
/// the team can't take a task: the sheet opens as "Team can't take
/// tasks" with the reason in plain words and a way on — Wake
/// the planner where the host takes agent controls, else Try again and
/// the host guide — and moves on to the form by itself once the planner is
/// awake; nothing is sent — unless the host can create work itself
/// (`controlCreateWork`, the phone's loopback supervisor, TEAM-306), in
/// which case the sheet offers the **direct task** form instead: a
/// project, a title and optional details go as one bead
/// (`createWork`) slung at the project's worker pool
/// (`<rig>/gastown.polecat`, [teamWorkerPoolId]) through
/// [OrchestrationController.giveTask], through one [TeamDispatchController]
/// per tap ([TeamDispatchAttempts], P6.3): the sheet says each stage the
/// host confirmed ("Creating your task…", then "Task created · sending it
/// to the team…") and the team page's Now line carries it on once the
/// sheet closes. A planner with no live session is woken
/// (`controlAgent(start)`) before the message goes.
///
/// Data safety (DATA-1, DATA-2): what the person typed is a [KitDraft] per
/// server profile (`oc.draft.team.objective.<profileId>` and the direct
/// task's two fields), so a swipe, Back or a refusal loses nothing and the
/// next open brings it back; it is cleared once the host took the task, and
/// the profile deletion sweep removes it. A refusal keeps the sheet open
/// with the host's words, so the person edits and sends again.
library;

import 'dart:async';

import 'package:flutter/material.dart';

import '../../../builtin/team/builtin_team.dart' show BuiltinTeam;
import '../../../domain/orchestration_gateway.dart';
import '../../../l10n/app_localizations.dart';
import '../../../state/automation_policy.dart';
import '../../../state/orchestration.dart';
import '../../../state/team_dispatch.dart';
import '../../../state/team_model.dart' show TeamModelStore;
import '../../../state/team_planning.dart';
import '../../../state/team_roles.dart';
import '../../app_theme.dart';
import '../../kit/kit.dart';
import '../../widgets/team_host_form.dart';
import '../../widgets/team_now.dart' show teamAgentKeptOff;
import '../../widgets/team_role_copy.dart';
import 'policy_block.dart';

AppLocalizations _copy(BuildContext context) =>
    lookupAppLocalizations(Localizations.localeOf(context));

/// The draft targets of the sheet's fields ([KitDraft.keyFor]).
const teamStartRunObjectiveDraft = 'team.objective';
const teamStartRunTaskDraft = 'team.task';
const teamStartRunDetailsDraft = 'team.taskDetails';

/// What the sheet did: the host's record, and whether the task was kept in
/// the backlog (made, given to no one) rather than started.
@immutable
class StartRunResult {
  const StartRunResult(this.record, {this.backlog = false});

  final MutationRecord record;
  final bool backlog;
}

/// Opens the sheet; resolves with the message record once sent (the
/// `createWork` record for a direct task), null when the person backed
/// out or the team could not take a task.
///
/// When the team can't take a task ([teamStartBlocked]) the sheet opens
/// first as "Team can't take tasks" with the way on; once
/// the planner is awake it closes and the task sheet opens in its place.
///
/// [offerBacklog] (the board, P3.5: its add sheet is this one sheet) adds
/// **Keep in backlog** beside the send where the host creates work: the
/// task is made and waits in the Backlog, given to no one. [projectId] is
/// the project chosen at first (the board's filter).
Future<StartRunResult?> showStartRunSheet(
  BuildContext context,
  OrchestrationController controller, {
  bool offerBacklog = false,
  String? projectId,
  String? roleId,
}) async {
  final l10n = _copy(context);
  if (teamStartBlocked(controller)) {
    final ready = await showKitSheet<bool>(
      context,
      title: l10n.teamStartRunBlockedTitle,
      icon: AppIconography.agent,
      sheetKey: const ValueKey('team-start-run-blocked-sheet'),
      body: (sheetContext) => TeamStartBlocked(
        controller: controller,
        onReady: () => Navigator.of(sheetContext).pop(true),
      ),
    );
    if (ready != true || !context.mounted) return null;
  }
  if (!context.mounted) return null;
  return showKitSheet<StartRunResult>(
    context,
    title: l10n.teamUiStartRunTitle,
    icon: AppIconography.agent,
    sheetKey: const ValueKey('team-start-run-sheet'),
    body: (_) => StartRunSheet(
      controller: controller,
      offerBacklog: offerBacklog,
      projectId: projectId,
      roleId: roleId,
    ),
  );
}

/// The supervision level's name and description.
(String, String) teamSupervisionCopy(
  AppLocalizations l10n,
  TeamSupervision level,
) => switch (level) {
  TeamSupervision.high => (
    l10n.teamUiStartRunSupervisionHigh,
    l10n.teamUiStartRunSupervisionHighHint,
  ),
  TeamSupervision.balanced => (
    l10n.teamUiStartRunSupervisionBalanced,
    l10n.teamUiStartRunSupervisionBalancedHint,
  ),
  TeamSupervision.autonomous => (
    l10n.teamUiStartRunSupervisionAutonomous,
    l10n.teamUiStartRunSupervisionAutonomousHint,
  ),
};

/// The planner is off (or missing), but this host both creates and
/// assigns work and lists a project (the dispatch contract's admission):
/// a task goes straight to a project's worker.
bool teamStartHasDirectPath(OrchestrationController controller) {
  final planner = teamPlannerAgent(controller.snapshot.agents);
  final off = planner == null || teamPlannerIsOff(planner);
  return off &&
      controller.capabilities.controlCreateWork &&
      controller.capabilities.controlAssign &&
      controller.snapshot.projects.isNotEmpty;
}

/// True when this team can't take a task now: no planner, or one that is
/// switched off, and no direct path to a worker.
bool teamStartBlocked(OrchestrationController controller) {
  if (teamStartHasDirectPath(controller)) return false;
  final planner = teamPlannerAgent(controller.snapshot.agents);
  return planner == null || teamPlannerIsOff(planner);
}

/// "Let the planner choose": the project choice with no project.
const _anyProject = '';

/// The sheet's body: the planner form, the direct task, or why neither.
/// Its actions sit at its end, so it also works on its own in a page.
class StartRunSheet extends StatefulWidget {
  const StartRunSheet({
    super.key,
    required this.controller,
    this.offerBacklog = false,
    this.projectId,
    this.roleId,
  });

  final OrchestrationController controller;

  /// Offer "Keep in backlog" (where the host creates work).
  final bool offerBacklog;

  /// The project chosen at first.
  final String? projectId;

  /// The role chosen at first (a role page's Give a task); without one the
  /// role is suggested from the person's words.
  final String? roleId;

  @override
  State<StartRunSheet> createState() => _StartRunSheetState();
}

class _StartRunSheetState extends State<StartRunSheet> {
  final _objective = TextEditingController();
  final _task = TextEditingController();
  final _details = TextEditingController();
  late String? _projectId = widget.projectId;
  late String? _directProjectId = widget.projectId;

  /// The team's roles once loaded; the direct task's "Who" row needs them.
  TeamRolesController? _roles;

  /// The role the person picked; null follows the suggestion.
  late String? _pickedRoleId = widget.roleId;

  /// Keep in backlog was refused: the host's words ('' when it gave none;
  /// never shown as copy — the notice says it in plain words).
  String? _backlogError;

  /// This server's level (Settings › What runs by itself); a pick here is
  /// for this one task and never changes that setting.
  late TeamSupervision _supervision =
      widget.controller.automation.value.supervision.team;
  bool _sending = false;
  bool _waking = false;
  bool _showEmpty = false;
  bool _showTaskEmpty = false;

  /// The direct task's refused create: the host's words, for Technical
  /// details only ('' when it gave none).
  String? _directError;

  /// The direct task's attempt while this sheet sends it (P6.3).
  TeamDispatchController? _attempt;

  /// The planner's refusal of the last send: the host's words.
  String? _refused;

  late final KitDraft? _objectiveDraft = _draft(
    teamStartRunObjectiveDraft,
    _objective,
  );
  late final KitDraft? _taskDraft = _draft(teamStartRunTaskDraft, _task);
  late final KitDraft? _detailsDraft = _draft(
    teamStartRunDetailsDraft,
    _details,
  );

  KitDraft? _draft(String target, TextEditingController controller) {
    final profileId = widget.controller.profileId;
    if (profileId.isEmpty) return null;
    return KitDraft(
      target: target,
      profileId: profileId,
      controller: controller,
    );
  }

  @override
  void initState() {
    super.initState();
    _objective.addListener(_changed);
    _task.addListener(_changed);
    _details.addListener(_changed);
    unawaited(_loadRoles());
  }

  Future<void> _loadRoles() async {
    try {
      final roles = await loadTeamRoles(widget.controller.profileId);
      if (mounted) setState(() => _roles = roles);
    } catch (_) {
      // Without roles the task goes as written.
    }
  }

  /// The role this task goes as: the person's pick, else the one the app
  /// suggests from the typed words. Null until the roles are loaded.
  TeamRole? get _role {
    final roles = _roles;
    if (roles == null) return null;
    final picked = _pickedRoleId == null ? null : roles.byId(_pickedRoleId!);
    if (picked != null) return picked;
    return roles.byId(roles.suggest('${_task.text} ${_details.text}')) ??
        roles.byId(TeamRoleIds.general);
  }

  Future<void> _pickRole() async {
    final roles = _roles;
    if (roles == null || _sending) return;
    final l10n = _copy(context);
    final current = _role?.id;
    final id = await showKitSheet<String>(
      context,
      title: l10n.teamStartRunWhoTitle,
      icon: AppIconography.agent,
      sheetKey: const ValueKey('team-start-run-who-sheet'),
      body: (sheetContext) => Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          for (final role in roles.roles)
            KitRow(
              key: ValueKey('team-start-run-who-${role.id}'),
              title: teamRoleName(l10n, role),
              supporting: teamRolePurpose(l10n, role).isEmpty
                  ? null
                  : TextSpan(text: teamRolePurpose(l10n, role)),
              supportingMaxLines: 2,
              selected: role.id == current,
              onTap: () => Navigator.of(sheetContext).pop(role.id),
            ),
        ],
      ),
    );
    if (id != null && mounted) setState(() => _pickedRoleId = id);
  }

  @override
  void dispose() {
    _attempt?.removeListener(_changed);
    _objective.dispose();
    _task.dispose();
    _details.removeListener(_changed);
    _details.dispose();
    super.dispose();
  }

  void _changed() {
    if (mounted) setState(() {});
  }

  OrchestrationProject? get _project {
    for (final project in widget.controller.snapshot.projects) {
      if (project.id == _projectId) return project;
    }
    return null;
  }

  /// The host took the task: nothing typed needs keeping.
  Future<void> _clearDrafts() async {
    await Future.wait([
      ?_objectiveDraft?.clear(),
      ?_taskDraft?.clear(),
      ?_detailsDraft?.clear(),
    ]);
  }

  /// Closes with [record]. The drafts are cleared only when the host made
  /// the task ([clearDrafts]): an unconfirmed create keeps the words.
  void _close(
    MutationRecord record, {
    bool backlog = false,
    bool clearDrafts = true,
  }) {
    if (clearDrafts) unawaited(_clearDrafts());
    final navigator = Navigator.of(context);
    if (navigator.canPop()) {
      navigator.pop(StartRunResult(record, backlog: backlog));
    }
  }

  /// Keep in backlog is offered: asked for, and the host creates work.
  bool get _backlog =>
      widget.offerBacklog && widget.controller.capabilities.controlCreateWork;

  /// Keep in backlog: the task is made in [projectId] and given to no one;
  /// it waits in the board's Backlog. A refusal keeps the sheet open with
  /// the host's words and everything typed.
  Future<void> _keepInBacklog({
    required TextEditingController title,
    required String? projectId,
    String? details,
    required VoidCallback onEmpty,
  }) async {
    final words = title.text.trim();
    if (words.isEmpty) {
      onEmpty();
      return;
    }
    if (_sending) return;
    setState(() {
      _sending = true;
      _backlogError = null;
    });
    try {
      final controller = widget.controller;
      final record = await controller.createWork(
        title: words,
        description: details == null || details.trim().isEmpty
            ? null
            : details.trim(),
        projectId: projectId,
      );
      if (!mounted) return;
      if (record.status == MutationStatus.rejected) {
        setState(() => _backlogError = record.receipt?.message?.trim() ?? '');
        return;
      }
      unawaited(controller.refresh().catchError((Object _) {}));
      _close(record, backlog: true);
    } finally {
      if (mounted) setState(() => _sending = false);
    }
  }

  /// The refusal of Keep in backlog, when any.
  List<Widget> _backlogRefusal(AppLocalizations l10n, KitTokens tokens) {
    final error = _backlogError;
    if (error == null) return const [];
    return [
      SizedBox(height: tokens.space3),
      KitNotice(
        key: const ValueKey('team-start-run-backlog-error'),
        tone: AppStatusTone.failure,
        icon: AppIconography.error,
        message: l10n.teamBoardAddFailed,
      ),
    ];
  }

  /// "Keep in backlog", the sheet's other way (P3.5).
  KitAction _backlogAction(VoidCallback onPressed) => KitAction(
    key: const ValueKey('team-start-run-backlog'),
    label: _copy(context).teamStartRunKeepInBacklog,
    icon: AppIconography.archive,
    onPressed: _sending ? null : onPressed,
  );

  Future<void> _send(OrchestrationAgent planner) async {
    final objective = _objective.text.trim();
    if (objective.isEmpty) {
      setState(() => _showEmpty = true);
      return;
    }
    if (_sending) return;
    setState(() {
      _sending = true;
      _refused = null;
    });
    final controller = widget.controller;
    try {
      if (planner.sessionId == null || planner.sessionId!.isEmpty) {
        // No live session to message: wake the planner first. The host
        // answers the message route by agent id once it is awake.
        setState(() => _waking = true);
        await controller.controlAgent(planner.id, AgentControlAction.start);
        if (!mounted) return;
        setState(() => _waking = false);
      }
      final record = await controller.messageAgent(
        planner.id,
        composeTeamPlanningMessage(
          objective: objective,
          supervision: _supervision,
          projectName: _project?.name,
        ),
      );
      if (!mounted) return;
      if (record.status == MutationStatus.rejected) {
        // Refused: stay, say why, keep the words for another try.
        setState(() => _refused = record.receipt?.message?.trim() ?? '');
        return;
      }
      _close(record);
    } finally {
      if (mounted) {
        setState(() {
          _sending = false;
          _waking = false;
        });
      }
    }
  }

  /// The direct-task project: the chosen one, else the first listed.
  String? get _directProject {
    final projects = widget.controller.snapshot.projects;
    for (final project in projects) {
      if (project.id == _directProjectId) return project.id;
    }
    return projects.isEmpty ? null : projects.first.id;
  }

  /// The planner is off, but this host both creates and assigns work
  /// (the dispatch contract's admission): the direct form.
  bool get _direct => teamStartHasDirectPath(widget.controller);

  /// Sends the direct task through one attempt ([TeamDispatchAttempts]):
  /// one create, then one assignment of that exact task, each once.
  Future<void> _sendDirect() async {
    final title = _task.text.trim();
    if (title.isEmpty) {
      setState(() => _showTaskEmpty = true);
      return;
    }
    final projectId = _directProject;
    if (_sending || projectId == null) return;
    final attempt = TeamDispatchAttempts.of(widget.controller).begin();
    _attempt?.removeListener(_changed);
    _attempt = attempt..addListener(_changed);
    setState(() {
      _sending = true;
      _directError = null;
    });
    try {
      final details = _details.text.trim();
      final roles = _roles;
      final config = widget.controller.config;
      await attempt.submit(
        title: title,
        description: details.isEmpty ? null : details,
        projectId: projectId,
        agentId: teamWorkerPoolId(projectId),
        role: _role,
        roles: roles,
        teamModel: roles == null
            ? null
            : TeamModelStore(roles.prefs).read(widget.controller.profileId),
        // Only the phone's own team has a model to apply; a team on a
        // computer runs the model its host decides.
        applyModel: BuiltinTeam.isBuiltinConfig(config)
            ? BuiltinTeam().applyModel
            : null,
      );
      if (!mounted) return;
      switch (attempt.phase) {
        case TeamDispatchPhase.createRefused:
          // The task was not made: stay, say so, let the person edit. The
          // host's words wait under Technical details.
          setState(
            () => _directError =
                attempt.problemRecord?.receipt?.message?.trim() ?? '',
          );
          TeamDispatchAttempts.of(widget.controller).dismiss();
        case TeamDispatchPhase.unavailable || TeamDispatchPhase.invalidInput:
          TeamDispatchAttempts.of(widget.controller).dismiss();
        case TeamDispatchPhase.createUnconfirmed:
          // It may exist: no blind resend from here. The team page's Now
          // line says so; the words stay for after a check.
          final record = attempt.problemRecord;
          if (record != null) _close(record, clearDrafts: false);
        default:
          // The task exists. A refused assignment comes back as its own
          // record so no conversation opens on a task nobody took.
          final assigned = attempt.assignMutationKey == null
              ? null
              : widget.controller.mutation(attempt.assignMutationKey!);
          final created = widget.controller.mutation(
            attempt.createMutationKey!,
          )!;
          _close(
            attempt.phase == TeamDispatchPhase.assignRefused && assigned != null
                ? assigned
                : created,
          );
      }
    } finally {
      if (mounted) setState(() => _sending = false);
    }
  }

  /// The direct task's stage while this sheet sends it: what the host
  /// confirmed so far, never more.
  Widget? _directStage(AppLocalizations l10n) {
    final attempt = _attempt;
    if (attempt == null || !_sending) return null;
    final message = switch (attempt.phase) {
      TeamDispatchPhase.creating => l10n.teamDispatchCreating,
      TeamDispatchPhase.sending => l10n.teamDispatchSending,
      _ => null,
    };
    if (message == null) return null;
    return KitNotice(
      key: const ValueKey('team-start-run-direct-stage'),
      messageKey: const ValueKey('team-start-run-direct-stage-text'),
      tone: AppStatusTone.progress,
      icon: AppIconography.waiting,
      message: message,
    );
  }

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: widget.controller,
    builder: (context, _) {
      final planner = teamPlannerAgent(widget.controller.snapshot.agents);
      if (_direct) return _directForm(context);
      // The planner went off while the sheet was open: the same reason and
      // way on as the sheet that opens when it is off.
      if (planner == null || teamPlannerIsOff(planner)) {
        return TeamStartBlocked(controller: widget.controller);
      }
      return _form(context, planner);
    },
  );

  /// A field's or a group's name, above it.
  Widget _label(KitTokens tokens, String text) => Padding(
    padding: EdgeInsetsDirectional.only(bottom: tokens.labelGap),
    child: KitText(text, role: KitTextRole.label, tone: KitTextTone.secondary),
  );

  Widget _form(BuildContext context, OrchestrationAgent planner) {
    final l10n = _copy(context);
    final tokens = KitTokens.of(context);
    final projects = widget.controller.snapshot.projects;
    final refused = _refused;
    return Column(
      key: const ValueKey('team-start-run-form'),
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        KitField(
          label: l10n.teamUiStartRunObjectiveLabel,
          kind: KitFieldKind.multiline,
          controller: _objectiveDraft == null ? _objective : null,
          draft: _objectiveDraft,
          hint: l10n.teamUiStartRunObjectiveHint,
          autofocus: true,
          error: _showEmpty && _objective.text.trim().isEmpty
              ? l10n.teamUiStartRunObjectiveEmpty
              : null,
          fieldKey: const ValueKey('team-start-run-objective'),
        ),
        if (projects.isNotEmpty) ...[
          SizedBox(height: tokens.space4),
          _label(tokens, l10n.teamUiStartRunProjectLabel),
          KitChoiceList<String>.single(
            key: const ValueKey('team-start-run-project'),
            semanticsLabel: l10n.teamUiStartRunProjectLabel,
            actsOnTap: false,
            choices: [
              KitChoice(
                key: const ValueKey('team-start-run-project-any'),
                value: _anyProject,
                title: l10n.teamUiStartRunProjectAny,
              ),
              for (final project in projects)
                KitChoice(
                  key: ValueKey('team-start-run-project-${project.id}'),
                  value: project.id,
                  title: project.name,
                ),
            ],
            selected: _projectId ?? _anyProject,
            onSelected: (value) {
              if (_sending) return;
              setState(() => _projectId = value == _anyProject ? null : value);
            },
          ),
        ],
        SizedBox(height: tokens.space4),
        _label(tokens, l10n.teamUiStartRunSupervisionLabel),
        KitChoiceList<TeamSupervision>.single(
          semanticsLabel: l10n.teamUiStartRunSupervisionLabel,
          actsOnTap: false,
          choices: [
            for (final level in TeamSupervision.values)
              KitChoice(
                key: ValueKey('team-start-run-supervision-${level.name}'),
                value: level,
                title: teamSupervisionCopy(l10n, level).$1,
                supporting: teamSupervisionCopy(l10n, level).$2,
              ),
          ],
          selected: _supervision,
          onSelected: (level) {
            if (_sending) return;
            setState(() => _supervision = level);
          },
        ),
        // The planner is not a choice here: the primary names it ("Send to
        // the Mayor") and its id waits under Technical details.
        if (widget.controller.policy case final policy?) ...[
          SizedBox(height: tokens.space4),
          TeamBoundariesRow(policy: policy),
        ],
        if (_waking) ...[
          SizedBox(height: tokens.space3),
          KitNotice(
            key: const ValueKey('team-start-run-waking'),
            tone: AppStatusTone.progress,
            icon: AppIconography.waiting,
            message: l10n.teamUiStartRunWaking,
          ),
        ],
        if (refused != null) ...[
          SizedBox(height: tokens.space3),
          KitNotice(
            key: const ValueKey('team-start-run-refused'),
            tone: AppStatusTone.failure,
            icon: AppIconography.error,
            title: refused.isEmpty
                ? l10n.teamUiGateAnswerRejectedNoMessage
                : l10n.teamUiStartRunRefused,
            message: l10n.teamStartRunRefusedKept,
          ),
          if (refused.isNotEmpty)
            KitDetailsFold(
              label: l10n.teamUiTechnicalDetails,
              foldKey: const ValueKey('team-start-run-refused-details'),
              notes: [l10n.teamDispatchHostWords],
              text: refused,
            ),
        ],
        ..._backlogRefusal(l10n, tokens),
        SizedBox(height: tokens.space5),
        // The form's one primary; Sending shows on it (STATE-10). The
        // board adds Keep in backlog: the task waits, given to no one.
        KitActionBlock(
          primary: KitAction(
            key: const ValueKey('team-start-run-send'),
            label: l10n.teamUiStartRunSend(l10n.teamUiStartRunPlannerMayor),
            icon: AppIconography.send,
            working: _sending,
            onPressed: _sending ? null : () => _send(planner),
          ),
          secondary: _backlog
              ? _backlogAction(
                  () => unawaited(
                    _keepInBacklog(
                      title: _objective,
                      projectId: _projectId,
                      onEmpty: () => setState(() => _showEmpty = true),
                    ),
                  ),
                )
              : null,
        ),
        SizedBox(height: tokens.space3),
        KitDetailsFold(
          label: l10n.teamUiTechnicalDetails,
          foldKey: const ValueKey('team-start-run-technical'),
          values: [
            KitTechnicalValue(
              l10n.teamUiStartRunPlannerLabel,
              planner.id,
              key: const ValueKey('team-start-run-planner-id'),
            ),
          ],
        ),
      ],
    );
  }

  /// The direct task (TEAM-306): intro, project, title, details, Send to
  /// an agent, the stage while it sends (P6.3), the host's refusal when
  /// any, and the host guide below for the person who would rather wake
  /// the planner. The fields stay editable while the host answers: what
  /// was sent is already taken, and Send waits (a disabled field would
  /// repeat its reason under each field).
  Widget _directForm(BuildContext context) {
    final l10n = _copy(context);
    final tokens = KitTokens.of(context);
    final projects = widget.controller.snapshot.projects;
    final error = _directError;
    return Column(
      key: const ValueKey('team-start-run-direct'),
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        KitNotice(
          icon: AppIconography.agent,
          message: l10n.teamUiStartRunDirectIntro,
          liveRegion: false,
        ),
        SizedBox(height: tokens.space4),
        _label(tokens, l10n.teamUiStartRunProjectLabel),
        KitChoiceList<String>.single(
          key: const ValueKey('team-start-run-direct-project'),
          semanticsLabel: l10n.teamUiStartRunProjectLabel,
          actsOnTap: false,
          choices: [
            for (final project in projects)
              KitChoice(
                key: ValueKey('team-start-run-direct-project-${project.id}'),
                value: project.id,
                title: project.name,
              ),
          ],
          selected: _directProject,
          onSelected: (value) {
            if (_sending) return;
            setState(() => _directProjectId = value);
          },
        ),
        SizedBox(height: tokens.space4),
        KitField(
          label: l10n.teamUiStartRunDirectTitle,
          controller: _taskDraft == null ? _task : null,
          draft: _taskDraft,
          hint: l10n.teamUiStartRunDirectTitleHint,
          autofocus: true,
          textInputAction: TextInputAction.next,
          error: _showTaskEmpty && _task.text.trim().isEmpty
              ? l10n.teamUiStartRunDirectTitleRequired
              : null,
          fieldKey: const ValueKey('team-start-run-direct-title'),
        ),
        SizedBox(height: tokens.space3),
        KitField(
          label: l10n.teamUiStartRunDirectDetails,
          kind: KitFieldKind.multiline,
          controller: _detailsDraft == null ? _details : null,
          draft: _detailsDraft,
          fieldKey: const ValueKey('team-start-run-direct-details'),
        ),
        if (_role case final role?) ...[
          SizedBox(height: tokens.space4),
          _label(tokens, l10n.teamStartRunWho),
          KitRowGroup(
            children: [
              KitRow(
                key: const ValueKey('team-start-run-who'),
                leading: KitRow.icon(context, AppIconography.agent),
                title: teamRoleName(l10n, role),
                supporting: TextSpan(
                  text: [
                    if (_pickedRoleId == null)
                      l10n.teamStartRunWhoSuggested
                    else
                      teamRolePurpose(l10n, role),
                    l10n.teamStartRunWhoChange,
                  ].where((part) => part.isNotEmpty).join(' · '),
                ),
                supportingMaxLines: 2,
                trailing: const KitChevron(),
                onTap: _sending ? null : () => unawaited(_pickRole()),
              ),
            ],
          ),
        ],
        if (_directStage(l10n) case final stage?) ...[
          SizedBox(height: tokens.space3),
          stage,
        ],
        if (error != null) ...[
          SizedBox(height: tokens.space3),
          KitNotice(
            key: const ValueKey('team-start-run-direct-error'),
            tone: AppStatusTone.failure,
            icon: AppIconography.error,
            message: l10n.teamDispatchCreateRefused,
          ),
          // The host's own words: technical, redacted by the fold.
          if (error.isNotEmpty)
            KitDetailsFold(
              label: l10n.teamUiTechnicalDetails,
              foldKey: const ValueKey('team-start-run-direct-error-details'),
              notes: [l10n.teamDispatchHostWords],
              text: error,
            ),
        ],
        ..._backlogRefusal(l10n, tokens),
        SizedBox(height: tokens.space5),
        // Send is the sheet's one primary; the host guide is the rare
        // other path (design standard §2). The board adds Keep in backlog.
        KitActionBlock(
          primary: KitAction(
            key: const ValueKey('team-start-run-direct-send'),
            label: l10n.teamUiStartRunDirectSend,
            icon: AppIconography.send,
            working: _sending,
            onPressed: _sending ? null : _sendDirect,
          ),
          secondary: _backlog
              ? _backlogAction(
                  () => unawaited(
                    _keepInBacklog(
                      title: _task,
                      details: _details.text,
                      projectId: _directProject,
                      onEmpty: () => setState(() => _showTaskEmpty = true),
                    ),
                  ),
                )
              : null,
          tertiary: [
            KitAction(
              key: const ValueKey('team-start-run-host-guide'),
              label: l10n.teamUiStartRunHostGuide,
              icon: AppIconography.guide,
              onPressed: () => showTeamHostGuideSheet(context),
            ),
          ],
        ),
      ],
    );
  }
}

/// Why this team can't take a task, in plain words, and the way on: Wake
/// the planner where the host takes agent controls (never for an agent the
/// app keeps off on its phone team), else Try again with the host guide.
/// [onReady] runs once the team can take a task again (the planner woke,
/// or a check found it on), so the blocked sheet can hand over to the task
/// sheet.
class TeamStartBlocked extends StatefulWidget {
  const TeamStartBlocked({super.key, required this.controller, this.onReady});

  final OrchestrationController controller;
  final VoidCallback? onReady;

  @override
  State<TeamStartBlocked> createState() => _TeamStartBlockedState();
}

/// The reason the team can't take a task.
enum _Blocked { plannerOff, noPlanner, noProject }

class _TeamStartBlockedState extends State<TeamStartBlocked> {
  bool _busy = false;

  /// The host took the wake; the planner is not listed awake yet.
  bool _asked = false;

  /// A check found the planner still off.
  bool _stillOff = false;

  /// The host refused the wake: its words, for Technical details only
  /// ('' when it gave none).
  String? _refused;
  bool _handedOver = false;

  OrchestrationController get _controller => widget.controller;

  @override
  void initState() {
    super.initState();
    _controller.addListener(_changed);
  }

  @override
  void dispose() {
    _controller.removeListener(_changed);
    super.dispose();
  }

  void _changed() {
    if (!mounted) return;
    if (!teamStartBlocked(_controller)) {
      _handOver();
      return;
    }
    setState(() {});
  }

  void _handOver() {
    final onReady = widget.onReady;
    if (_handedOver || onReady == null) return;
    _handedOver = true;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) onReady();
    });
  }

  _Blocked get _reason {
    final planner = teamPlannerAgent(_controller.snapshot.agents);
    final caps = _controller.capabilities;
    // A host that gives tasks straight to a worker needs only a project.
    if (caps.controlCreateWork &&
        caps.controlAssign &&
        _controller.snapshot.projects.isEmpty &&
        (planner == null || teamAgentKeptOff(_controller.config, planner))) {
      return _Blocked.noProject;
    }
    return planner == null ? _Blocked.noPlanner : _Blocked.plannerOff;
  }

  /// The planner the app may wake from here, if any.
  OrchestrationAgent? get _wakeable {
    final planner = teamPlannerAgent(_controller.snapshot.agents);
    if (planner == null || !_controller.capabilities.controlAgent) return null;
    if (teamAgentKeptOff(_controller.config, planner)) return null;
    return planner;
  }

  Future<void> _wake(OrchestrationAgent planner) async {
    if (_busy) return;
    setState(() {
      _busy = true;
      _refused = null;
      _stillOff = false;
    });
    try {
      final record = await _controller.controlAgent(
        planner.id,
        AgentControlAction.resume,
      );
      if (!mounted) return;
      if (record.status == MutationStatus.rejected) {
        setState(() => _refused = record.receipt?.message?.trim() ?? '');
        return;
      }
      setState(() => _asked = true);
      await _controller.refresh();
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _check() async {
    if (_busy) return;
    setState(() {
      _busy = true;
      _stillOff = false;
    });
    try {
      await _controller.refresh();
      if (!mounted) return;
      if (!teamStartBlocked(_controller)) {
        _handOver();
        return;
      }
      setState(() => _stillOff = true);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = _copy(context);
    final reason = _reason;
    final planner = reason == _Blocked.plannerOff ? _wakeable : null;
    final refused = _refused;
    final (String title, String body, Key key) = switch (reason) {
      _Blocked.plannerOff => (
        l10n.teamStartRunPlannerOff,
        planner != null
            ? l10n.teamStartRunPlannerOffWakeBody
            : l10n.teamStartRunPlannerOffHostBody,
        const ValueKey('team-start-run-planner-off'),
      ),
      _Blocked.noPlanner => (
        l10n.teamStartRunNoPlanner,
        l10n.teamStartRunNoPlannerBody,
        const ValueKey('team-start-run-planner-missing'),
      ),
      _Blocked.noProject => (
        l10n.teamStartRunNoProject,
        l10n.teamStartRunNoProjectBody,
        const ValueKey('team-start-run-no-project'),
      ),
    };
    final check = KitAction(
      key: const ValueKey('team-start-run-check'),
      label: l10n.teamUiCardRetry,
      icon: AppIconography.sync,
      working: _busy && planner == null,
      onPressed: _busy ? null : () => unawaited(_check()),
    );
    // Waking is the one primary where the app can do it; after the host
    // took it, Try again. Elsewhere Try again, with the host guide for
    // switching it on where the team runs.
    final KitAction primary = planner != null && !_asked
        ? KitAction(
            key: const ValueKey('team-start-run-wake'),
            label: l10n.teamStartRunWake,
            icon: AppIconography.play,
            working: _busy,
            onPressed: _busy ? null : () => unawaited(_wake(planner)),
          )
        : check;
    final guide = KitAction(
      key: const ValueKey('team-start-run-host-guide'),
      label: l10n.teamUiStartRunHostGuide,
      icon: AppIconography.guide,
      onPressed: () => showTeamHostGuideSheet(context),
    );
    final notices = <Widget>[
      if (_asked && refused == null)
        KitNotice(
          key: const ValueKey('team-start-run-wake-asked'),
          tone: AppStatusTone.progress,
          icon: AppIconography.waiting,
          message: l10n.teamStartRunWakeAsked,
        ),
      if (_stillOff)
        KitNotice(
          key: const ValueKey('team-start-run-still-off'),
          icon: AppIconography.info,
          message: reason == _Blocked.noProject
              ? l10n.teamStartRunStillNoProject
              : l10n.teamStartRunStillOff,
        ),
      if (refused != null) ...[
        KitNotice(
          key: const ValueKey('team-start-run-wake-refused'),
          tone: AppStatusTone.failure,
          icon: AppIconography.error,
          title: l10n.teamStartRunWakeRefused,
          message: l10n.teamStartRunWakeRefusedNext,
        ),
        // The host's own words: technical, redacted by the fold.
        if (refused.isNotEmpty)
          KitDetailsFold(
            label: l10n.teamUiTechnicalDetails,
            foldKey: const ValueKey('team-start-run-wake-refused-details'),
            notes: [l10n.teamDispatchHostWords],
            text: refused,
          ),
      ],
    ];
    // A state, not a form (design standard §3): what is wrong, and the
    // way on from here.
    return KitStateView(
      key: key,
      size: KitStateSize.inline,
      icon: AppIconography.warning,
      title: title,
      body: body,
      content: notices.isEmpty
          ? null
          : Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              mainAxisSize: MainAxisSize.min,
              children: [
                for (final (i, notice) in notices.indexed) ...[
                  if (i > 0) SizedBox(height: KitTokens.of(context).space2),
                  notice,
                ],
              ],
            ),
      primary: primary,
      secondary: reason == _Blocked.noProject ? null : guide,
    );
  }
}

// ---------------------------------------------------------------------------
// Requests the planner has not listed yet
// ---------------------------------------------------------------------------

/// The requests for [controller] at [now] the planner has not listed yet:
/// every Start-a-run request still waiting on a run, newest first (the team
/// page's rows for them); resolved and dismissed ones drop.
List<TeamPlanningRequest> teamPendingPlanning(
  OrchestrationController controller,
  DateTime now,
) => [
  for (final request in teamPlanningRequests(
    mutations: controller.mutations,
    runs: controller.snapshot.runs,
    dismissed: {
      for (final record in controller.mutations)
        if (controller.isPlanningDismissed(record.key)) record.key,
    },
    now: now,
  ))
    if (request.status != TeamPlanningStatus.started) request,
];
