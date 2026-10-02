/// A role's page (AI Team roles): what a role is for, how it works and
/// which model it runs with, in one form, plus what it is doing now and the
/// tasks it was given. Also the create form for a new role (`roleId` null):
/// a short starter template and three example chips that fill the fields.
///
/// Saving is explicit: the pinned primary ("Save", or "Create role") is
/// enabled once something changed and closes the page; nothing is written
/// while typing. A built-in role can be reset to how it shipped and one of
/// the person's own can be deleted, each after a question naming the role.
///
/// The live worker wearing this role (a Gas City worker on a computer or
/// the phone's one worker) shows as "Working on …" with its conversation
/// one tap away; its generated name waits under Details.
///
/// Kit only (KIT-1): [KitScreen] with [KitTopBar], [KitField]s, rows on
/// [KitRowGroup]s and a pinned [KitActionBlock].
library;

import 'dart:async';

import 'package:flutter/services.dart' show TextInputAction;
import 'package:flutter/widgets.dart';

import '../../../builtin/team/builtin_team.dart' show BuiltinTeam;
import '../../../domain/orchestration_gateway.dart';
import '../../../l10n/app_localizations.dart';
import '../../../state/connection.dart';
import '../../../state/orchestration.dart';
import '../../../state/team_conversation.dart' show teamSessionState;
import '../../../state/team_roles.dart';
import '../../app_iconography.dart';
import '../../kit/kit.dart';
import '../../widgets/relative_time.dart';
import '../../widgets/team_role_copy.dart';
import '../../widgets/team_vocabulary.dart';
import '../team_conversation/team_conversation.dart';
import 'team_agents_screen.dart' show teamAgentNickname, teamRoleModelName;
import 'team_model_sheet.dart';

AppLocalizations _copy(BuildContext context) =>
    lookupAppLocalizations(Localizations.localeOf(context));

class RoleScreen extends StatefulWidget {
  const RoleScreen({
    super.key,
    required this.controller,
    required this.roles,
    this.roleId,
    this.connection,
    this.onOpenAgent,
    this.now,
  });

  final OrchestrationController controller;
  final TeamRolesController roles;

  /// The role to edit; null creates a new one.
  final String? roleId;

  /// Names the models of the picker's catalog; without one the model row
  /// cannot be changed.
  final ConnectionController? connection;
  final ValueChanged<OrchestrationAgent>? onOpenAgent;
  final DateTime Function()? now;

  @override
  State<RoleScreen> createState() => _RoleScreenState();
}

class _RoleScreenState extends State<RoleScreen> {
  final _name = TextEditingController();
  final _purpose = TextEditingController();
  final _instructions = TextEditingController();
  String? _model;
  bool _filled = false;
  bool _saving = false;
  bool _showNameError = false;

  TeamRole? get _role =>
      widget.roleId == null ? null : widget.roles.byId(widget.roleId!);

  bool get _creating => widget.roleId == null;

  bool get _remote => !BuiltinTeam.isBuiltinConfig(widget.controller.config);

  DateTime get _now => (widget.now ?? DateTime.now)();

  @override
  void initState() {
    super.initState();
    _name.addListener(_changed);
    _purpose.addListener(_changed);
    _instructions.addListener(_changed);
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_filled) return;
    _filled = true;
    final l10n = _copy(context);
    final role = _role;
    if (role == null) {
      _instructions.text = l10n.teamRoleStarterInstructions;
    } else {
      _name.text = teamRoleName(l10n, role);
      _purpose.text = teamRolePurpose(l10n, role);
      _instructions.text = role.instructions;
      _model = role.model;
    }
  }

  @override
  void dispose() {
    _name.dispose();
    _purpose.dispose();
    _instructions.dispose();
    super.dispose();
  }

  void _changed() {
    if (mounted) setState(() {});
  }

  /// Something differs from what is saved (or, for a new role, has a name).
  bool _dirty(AppLocalizations l10n) {
    final role = _role;
    if (role == null) return _name.text.trim().isNotEmpty;
    return _name.text.trim() != teamRoleName(l10n, role) ||
        _purpose.text.trim() != teamRolePurpose(l10n, role) ||
        _instructions.text.trim() != role.instructions.trim() ||
        _model != role.model;
  }

  /// The text to store: a built-in role's shipped words stay unstored, so
  /// they keep following the app's language.
  String _stored(String text, String shown, String original) =>
      text.trim() == shown ? original : text.trim();

  /// Saves the form; false when the name is missing.
  Future<bool> _save() async {
    final l10n = _copy(context);
    if (_name.text.trim().isEmpty) {
      setState(() => _showNameError = true);
      return false;
    }
    if (_saving) return false;
    setState(() => _saving = true);
    try {
      final role = _role;
      final model = _remote ? role?.model : _model;
      if (role == null) {
        await widget.roles.add(
          name: _name.text.trim(),
          purpose: _purpose.text.trim(),
          instructions: _instructions.text.trim(),
          model: model,
        );
      } else {
        await widget.roles.update(
          role.copyWith(
            name: _stored(_name.text, teamRoleName(l10n, role), role.name),
            purpose: _stored(
              _purpose.text,
              teamRolePurpose(l10n, role),
              role.purpose,
            ),
            instructions: _instructions.text.trim(),
            model: () => model,
          ),
        );
      }
      return true;
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  Future<void> _saveAndClose() async {
    if (await _save() && mounted) Navigator.of(context).maybePop();
  }

  Future<void> _pickModel() async {
    final connection = widget.connection;
    if (connection == null || _remote) return;
    final l10n = _copy(context);
    unawaited(connection.ensureCatalog());
    final choice = await showTeamModelSheet(
      context,
      connection: connection,
      current: _model,
      title: l10n.teamRoleModelRow,
      defaultTitle: l10n.teamRoleModelSheetDefault,
      defaultHint: l10n.teamRoleModelSheetDefaultHint,
    );
    if (choice == null || !mounted) return;
    setState(() => _model = choice.spec);
  }

  Future<void> _giveTask() async {
    final role = _role;
    if (role == null) return;
    if (_dirty(_copy(context)) && !await _save()) return;
    if (!mounted) return;
    await TeamConversation.start(context, widget.controller, roleId: role.id);
  }

  Future<void> _reset() async {
    final role = _role;
    if (role == null) return;
    final l10n = _copy(context);
    final name = teamRoleName(l10n, role);
    final yes = await showKitConfirm(
      context,
      title: l10n.teamRoleResetTitle(name),
      body: l10n.teamRoleResetBody,
      confirmLabel: l10n.teamRoleReset(name),
      confirmKey: const ValueKey('team-role-reset-confirm'),
    );
    if (!yes || !mounted) return;
    await widget.roles.reset(role.id);
    if (mounted) Navigator.of(context).maybePop();
  }

  Future<void> _delete() async {
    final role = _role;
    if (role == null) return;
    final l10n = _copy(context);
    final name = teamRoleName(l10n, role);
    final yes = await showKitConfirm(
      context,
      title: l10n.teamRoleDeleteTitle(name),
      body: l10n.teamRoleDeleteBody(name),
      confirmLabel: l10n.teamRoleDelete(name),
      kind: KitConfirmKind.destructive,
      confirmKey: const ValueKey('team-role-delete-confirm'),
    );
    if (!yes || !mounted) return;
    await widget.roles.remove(role.id);
    if (mounted) Navigator.of(context).maybePop();
  }

  void _openWorker(OrchestrationAgent agent) {
    final open = widget.onOpenAgent;
    if (open != null) return open(agent);
    unawaited(
      openTeamAgentConversation(context, agent, team: widget.controller),
    );
  }

  /// The live worker wearing this role now, when there is one.
  OrchestrationAgent? _liveWorker() {
    final role = _role;
    if (role == null) return null;
    final controller = widget.controller;
    for (final agent in controller.snapshot.agents) {
      if (teamAgentIsLive(agent) &&
          teamSessionState(agent) == AgentState.working &&
          teamRoleIdOfWorker(widget.roles, controller, agent) == role.id) {
        return agent;
      }
    }
    return null;
  }

  void _fill(String name, String purpose, String instructions) {
    _name.text = name;
    _purpose.text = purpose;
    _instructions.text = instructions;
  }

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: Listenable.merge([
      widget.controller,
      widget.roles,
      ?widget.connection,
    ]),
    builder: (context, _) => _screen(context),
  );

  Widget _screen(BuildContext context) {
    final l10n = _copy(context);
    final tokens = KitTokens.of(context);
    final role = _role;
    if (!_creating && role == null) {
      // Deleted or reset elsewhere while open: nothing to edit.
      return KitScreen(
        key: const ValueKey('team-role'),
        topBar: KitTopBar(title: l10n.teamRolesTitle),
        body: const SizedBox.shrink(),
      );
    }
    final name = role == null
        ? l10n.teamRoleNewTitle
        : teamRoleName(l10n, role);
    final dirty = _dirty(l10n);
    return KitScreen(
      key: const ValueKey('team-role'),
      topBar: KitTopBar(title: name),
      width: KitScreenWidth.list,
      body: ListView(
        key: const ValueKey('team-role-list'),
        padding: EdgeInsetsDirectional.only(
          top: tokens.space3,
          start: tokens.gutter,
          end: tokens.gutter,
          bottom: KitScreen.endPadding(context),
        ),
        children: [
          ..._live(context, l10n, tokens),
          if (_creating) ...[
            KitSectionLabel(
              l10n.teamRoleExamplesLabel,
              margin: EdgeInsetsDirectional.zero,
            ),
            KitChipWrap(
              children: [
                KitChip.action(
                  key: const ValueKey('team-role-example-docs'),
                  label: l10n.teamRoleExampleDocs,
                  onPressed: () => _fill(
                    l10n.teamRoleExampleDocs,
                    l10n.teamRoleExampleDocsPurpose,
                    l10n.teamRoleExampleDocsInstructions,
                  ),
                ),
                KitChip.action(
                  key: const ValueKey('team-role-example-security'),
                  label: l10n.teamRoleExampleSecurity,
                  onPressed: () => _fill(
                    l10n.teamRoleExampleSecurity,
                    l10n.teamRoleExampleSecurityPurpose,
                    l10n.teamRoleExampleSecurityInstructions,
                  ),
                ),
                KitChip.action(
                  key: const ValueKey('team-role-example-designer'),
                  label: l10n.teamRoleExampleDesigner,
                  onPressed: () => _fill(
                    l10n.teamRoleExampleDesigner,
                    l10n.teamRoleExampleDesignerPurpose,
                    l10n.teamRoleExampleDesignerInstructions,
                  ),
                ),
              ],
            ),
            SizedBox(height: tokens.space4),
          ],
          KitField(
            label: l10n.teamRoleFieldName,
            controller: _name,
            textInputAction: TextInputAction.next,
            error: _showNameError && _name.text.trim().isEmpty
                ? l10n.teamRoleNameRequired
                : null,
            fieldKey: const ValueKey('team-role-name'),
          ),
          SizedBox(height: tokens.space3),
          KitField(
            label: l10n.teamRoleFieldPurpose,
            controller: _purpose,
            hint: l10n.teamRoleFieldPurposeHint,
            textInputAction: TextInputAction.next,
            fieldKey: const ValueKey('team-role-purpose'),
          ),
          SizedBox(height: tokens.space3),
          KitField(
            label: l10n.teamRoleFieldInstructions,
            kind: KitFieldKind.multiline,
            controller: _instructions,
            hint: l10n.teamRoleFieldInstructionsHint,
            fieldKey: const ValueKey('team-role-instructions'),
          ),
          SizedBox(height: tokens.sectionGap),
          KitRowGroup(
            children: [
              KitRow(
                key: const ValueKey('team-role-model'),
                leading: KitRow.icon(context, AppIconography.model),
                title: l10n.teamRoleModelRow,
                supporting: TextSpan(
                  text: _remote
                      ? l10n.teamRoleComputerModel
                      : _model == null
                      ? l10n.teamRoleTeamModel
                      : teamRoleModelName(widget.connection, _model!),
                ),
                trailing: _remote || widget.connection == null
                    ? null
                    : const KitChevron(),
                onTap: _remote || widget.connection == null
                    ? null
                    : () => unawaited(_pickModel()),
              ),
            ],
          ),
          if (role != null) ..._recent(context, l10n, tokens, role),
          ..._details(l10n, tokens),
        ],
      ),
      bottom: KitActionBlock(
        primary: KitAction(
          key: const ValueKey('team-role-save'),
          label: _creating ? l10n.teamRoleCreate : l10n.teamRoleSave,
          working: _saving,
          onPressed: dirty && !_saving ? _saveAndClose : null,
        ),
        secondary: role == null
            ? null
            : KitAction(
                key: const ValueKey('team-role-give-task'),
                label: l10n.teamRoleGiveTask(name),
                icon: AppIconography.send,
                onPressed: _saving ? null : () => unawaited(_giveTask()),
              ),
        tertiary: [
          if (role != null && role.builtIn)
            KitAction(
              key: const ValueKey('team-role-reset'),
              label: l10n.teamRoleReset(name),
              icon: AppIconography.restore,
              onPressed: () => unawaited(_reset()),
            ),
          if (role != null && !role.builtIn)
            KitAction(
              key: const ValueKey('team-role-delete'),
              label: l10n.teamRoleDelete(name),
              icon: AppIconography.delete,
              destructive: true,
              onPressed: () => unawaited(_delete()),
            ),
        ],
      ),
    );
  }

  /// The live worker of this role: its task and its conversation.
  List<Widget> _live(
    BuildContext context,
    AppLocalizations l10n,
    KitTokens tokens,
  ) {
    final agent = _liveWorker();
    if (agent == null) return const [];
    final snapshot = widget.controller.snapshot;
    WorkItem? item;
    for (final work in snapshot.work) {
      if (work.id == agent.currentWorkId) item = work;
    }
    OrchestrationRun? run;
    for (final r in snapshot.runs) {
      if (item?.runId == r.id) run = r;
    }
    final task = (run?.title ?? item?.title)?.trim();
    final since = agent.sessionStartedAt ?? agent.lastActivity;
    final age = since == null
        ? null
        : relativeTimeLabel(
            since.millisecondsSinceEpoch,
            now: _now,
            l10n: l10n,
          );
    final runId = run?.id;
    return [
      KitRowGroup(
        key: const ValueKey('team-role-live'),
        children: [
          if (task != null && task.isNotEmpty)
            KitRow(
              key: const ValueKey('team-role-live-task'),
              leading: KitTaskMark(
                state: KitTaskState.working,
                label: teamAgentStateWord(l10n, AgentState.working),
              ),
              title: l10n.teamRoleWorkingNow(task),
              titleMaxLines: 2,
              supporting: age == null ? null : TextSpan(text: age),
              trailing: runId == null ? null : const KitChevron(),
              onTap: runId == null
                  ? null
                  : () => unawaited(
                      TeamConversation.open(
                        context,
                        widget.controller,
                        runId: runId,
                      ),
                    ),
            ),
          KitRow(
            key: const ValueKey('team-role-live-open'),
            leading: KitRow.icon(context, AppIconography.chat),
            title: l10n.teamRoleOpenConversation,
            supporting: agent.model == null || agent.model!.isEmpty
                ? null
                : TextSpan(text: l10n.teamRoleUses(agent.model!)),
            trailing: const KitChevron(),
            onTap: () => _openWorker(agent),
          ),
        ],
      ),
      SizedBox(height: tokens.sectionGap),
    ];
  }

  /// Recent tasks given to this role; each opens its conversation.
  List<Widget> _recent(
    BuildContext context,
    AppLocalizations l10n,
    KitTokens tokens,
    TeamRole role,
  ) {
    final runs = teamRunsOfRole(
      widget.roles,
      widget.controller,
      role.id,
    ).take(5).toList();
    return [
      SizedBox(height: tokens.sectionGap),
      KitSectionLabel(
        l10n.teamRoleRecentTasks,
        margin: EdgeInsetsDirectional.zero,
      ),
      KitRowGroup(
        key: const ValueKey('team-role-tasks'),
        children: [
          if (runs.isEmpty)
            KitRow(
              key: const ValueKey('team-role-no-tasks'),
              title: l10n.teamRoleNoTasks(teamRoleName(l10n, role)),
              titleMaxLines: 2,
              enabled: false,
            )
          else
            for (final run in runs)
              KitRow(
                key: ValueKey('team-role-task-${run.id}'),
                title: run.title,
                titleMaxLines: 2,
                supporting: (run.updatedAt ?? run.startedAt) == null
                    ? null
                    : TextSpan(
                        text: relativeTimeLabel(
                          (run.updatedAt ?? run.startedAt)!
                              .millisecondsSinceEpoch,
                          now: _now,
                          l10n: l10n,
                        ),
                      ),
                trailing: const KitChevron(),
                onTap: () => unawaited(
                  TeamConversation.open(
                    context,
                    widget.controller,
                    runId: run.id,
                  ),
                ),
              ),
        ],
      ),
    ];
  }

  /// The live worker's generated name, last and folded.
  List<Widget> _details(AppLocalizations l10n, KitTokens tokens) {
    final agent = _liveWorker();
    if (agent == null) return const [];
    return [
      SizedBox(height: tokens.sectionGap),
      KitDetailsFold(
        label: l10n.teamUiTechnicalDetails,
        foldKey: const ValueKey('team-role-details'),
        values: [
          KitTechnicalValue(
            l10n.teamRoleWorkerName,
            teamAgentNickname(agent) ?? agent.name,
          ),
        ],
      ),
    ];
  }
}
