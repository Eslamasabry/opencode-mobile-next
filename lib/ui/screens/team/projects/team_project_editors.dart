import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';

import '../../../../l10n/app_localizations.dart';
import '../../../../domain/relative_age.dart';
import '../../../../state/team_project_controller.dart';
import '../../../kit/kit.dart';
import '../team_model_sheet.dart';
import 'team_execution_gate.dart';
import 'team_refusal.dart';

/// Opens the team's model sheet for a role; [fallback] picks the fallback
/// model. Null means the sheet was dismissed.
typedef TeamRolesModelPicker =
    Future<TeamModelChoice?> Function(
      BuildContext context, {
      required String? current,
      required bool fallback,
    });

Future<void> openTeamNewProject(
  BuildContext context,
  TeamProjectController controller, {
  bool quick = false,
}) => _open(context, controller, quick ? _Kind.quick : _Kind.create);
Future<void> openTeamSpecEditor(
  BuildContext context,
  TeamProjectController controller,
  String projectId,
) => _open(context, controller, _Kind.spec, projectId);
Future<void> openTeamPlanEditor(
  BuildContext context,
  TeamProjectController controller,
  String projectId,
) => _open(context, controller, _Kind.plan, projectId);
Future<void> openTeamProjectSettings(
  BuildContext context,
  TeamProjectController controller,
  String projectId,
) => _open(context, controller, _Kind.settings, projectId);
Future<void> openTeamRoles(
  BuildContext context,
  TeamProjectController controller, {
  TeamRolesModelPicker? modelPicker,
}) => _open(context, controller, _Kind.roles, '', modelPicker);

Future<void> openTeamDefaults(
  BuildContext context,
  TeamProjectController controller,
) => _open(context, controller, _Kind.defaults);

enum _Kind { create, quick, spec, plan, settings, roles, defaults }

Future<void> _open(
  BuildContext context,
  TeamProjectController controller,
  _Kind kind, [
  String projectId = '',
  TeamRolesModelPicker? modelPicker,
]) async {
  final gate = TeamExecutionGate.of(controller);
  final startsWork =
      kind == _Kind.create ||
      kind == _Kind.quick ||
      (kind == _Kind.plan &&
          controller.snapshot?.projects
                  .where((p) => p.id == projectId)
                  .firstOrNull
                  ?.status ==
              'plan');
  if (gate != null && startsWork && !gate.permits(TeamExecutionNeed.lanes)) {
    // Not a form that cannot be sent: the one line and the fix.
    final l = lookupAppLocalizations(Localizations.localeOf(context));
    if (await showKitConfirm(
      context,
      title: l.phoneTeamBlockedTitle,
      body: l.phoneTeamBlocked,
      confirmLabel: l.teamIntroTurnOnPhone,
      cancelLabel: l.phoneTeamStopCancel,
    )) {
      if (context.mounted) await gate.setUp(context);
    }
    return;
  }
  await showKitFramedSheet<void>(
    context,
    builder: (_) => _Editor(
      controller: controller,
      kind: kind,
      projectId: projectId,
      modelPicker: modelPicker ?? _gatePicker(controller),
    ),
  );
}

/// The phone's model catalog, when the page that owns the team bound one;
/// the simulated demo has no catalog and keeps typed names.
TeamRolesModelPicker? _gatePicker(TeamProjectController controller) {
  final connection = TeamExecutionGate.of(controller)?.connection;
  if (connection == null) return null;
  return (context, {required current, required fallback}) {
    final l = lookupAppLocalizations(Localizations.localeOf(context));
    unawaited(connection.ensureCatalog());
    return showTeamModelSheet(
      context,
      connection: connection,
      current: current,
      title: fallback ? l.teamProjectEditorFallback : l.teamProjectEditorModel,
      defaultTitle: fallback
          ? l.teamProjectEditorNoFallback
          : l.teamRoleModelSheetDefault,
      defaultHint: fallback
          ? l.teamProjectEditorNoFallbackHint
          : l.teamRoleModelSheetDefaultHint,
    );
  };
}

/// Keeps the KitField draft contract while delegating every persistent
/// operation to the deletion-aware, redacting controller writer.
class _ControllerDraft extends KitDraft {
  _ControllerDraft({
    required super.target,
    required this.owner,
    required super.controller,
    required this.isAlive,
    required this.onFailure,
  }) : super(profileId: owner.profileId);
  final TeamProjectController owner;
  final bool Function() isAlive;
  final VoidCallback onFailure;

  @override
  Future<void> save() async {
    final value = controller.text;
    try {
      await owner.saveEditorDraft(target, value);
    } catch (_) {
      if (isAlive()) onFailure();
    }
  }

  @override
  Future<void> restore() async {
    try {
      final value = await owner.readEditorDraft(target);
      if (isAlive() &&
          value != null &&
          value.isNotEmpty &&
          controller.text.isEmpty) {
        controller.text = value;
      }
    } catch (_) {
      if (isAlive()) onFailure();
    }
  }

  @override
  Future<void> clear() => owner.clearEditorDraft(target);
}

class _Editor extends StatefulWidget {
  const _Editor({
    required this.controller,
    required this.kind,
    required this.projectId,
    this.modelPicker,
  });
  final TeamProjectController controller;
  final _Kind kind;
  final String projectId;
  final TeamRolesModelPicker? modelPicker;
  @override
  State<_Editor> createState() => _EditorState();
}

class _EditorState extends State<_Editor> {
  final Map<String, TextEditingController> _fields = {};
  final Map<String, KitDraft> _fieldDrafts = {};
  late final String _draftTarget;
  bool _restoring = true;
  bool _complete = false;
  int? _reviewedRevision;
  late TeamProjectSettings _settings;
  late List<TeamRepo> _repos;
  late List<TeamMilestone> _milestones;
  late List<TeamTask> _tasks;
  late List<TeamPhase> _phases;
  String? _serverId;
  String? _roleId;
  String? _budgetChoice;
  String? _error;
  TeamRefusal? _refusal;
  bool _working = false;
  bool _planFirst = true;
  bool _history = false;
  bool _chargingTouched = false;
  TeamProjectRole? _role;
  int _id = 0;
  TeamProjectController get _controller => widget.controller;
  String get _contextFilesHelp => _controller.snapshot?.simulated == false
      ? _l.teamProjectEditorContextFilesHelpReal
      : _l.teamProjectEditorContextFilesHelp;
  TeamProject? get _project => _controller.snapshot?.projects
      .where((p) => p.id == widget.projectId)
      .firstOrNull;
  AppLocalizations get _l => AppLocalizations.of(context);
  bool get _creating =>
      widget.kind == _Kind.create || widget.kind == _Kind.quick;
  String _newId() => 'edit-${DateTime.now().microsecondsSinceEpoch}-${_id++}';

  @override
  void initState() {
    super.initState();
    final p = _project;
    _reviewedRevision = p?.revision;
    final defaults =
        _controller.snapshot?.defaultSettings ?? const TeamProjectSettings();
    _settings =
        p?.settings ??
        (_creating
            ? defaults.copyWith(mode: '', budget: const TeamBudget())
            : defaults);
    _repos = [...?p?.repos];
    _milestones = [...?p?.specDraft.milestones];
    _tasks = [...?p?.tasks];
    _phases = [...?p?.phases];
    if (_settings.budget.chosen) {
      _budgetChoice = _settings.budget.unlimited ? 'unlimited' : 'limited';
    }
    _text('name', p?.name ?? '');
    _text('goal', p?.specDraft.goal ?? '');
    _text('constraints', p?.specDraft.constraints ?? '');
    _text('decisions', p?.specDraft.decisions ?? '');
    _text('outOfScope', p?.specDraft.outOfScope ?? '');
    _text('contextFiles', p?.specDraft.contextFiles.join('\n') ?? '');
    _text('lanes', '${_settings.maxLanes}');
    _text('daily', _settings.budget.daily?.toString() ?? '');
    _text('total', _settings.budget.total?.toString() ?? '');
    _text('tokens', _settings.budget.taskTokens?.toString() ?? '');
    _text('rounds', '${_settings.maxFixRounds}');
    _draftTarget = 'team-editor.${widget.kind.name}.${widget.projectId}';
    unawaited(_restore());
  }

  TextEditingController _text(String key, [String initial = '']) =>
      _fields.putIfAbsent(key, () => TextEditingController(text: initial));
  String _value(String key) => _text(key).text.trim();
  @override
  void dispose() {
    for (final c in _fields.values) {
      c.dispose();
    }
    super.dispose();
  }

  void _change(VoidCallback action) {
    setState(action);
    if (!_restoring && !_complete) _persist();
  }

  void _persist() {
    final payload = jsonEncode({
      'fields': {for (final e in _fields.entries) e.key: e.value.text},
      'settings': _settings.toJson(),
      'repos': _repos.map((r) => r.toJson()).toList(),
      'milestones': _milestones.map((m) => m.toJson()).toList(),
      'tasks': _tasks.map((t) => t.toJson()).toList(),
      'phases': _phases.map((p) => p.toJson()).toList(),
      'server': _serverId,
      'roleId': _roleId,
      'budget': _budgetChoice,
      'planFirst': _planFirst,
      'chargingTouched': _chargingTouched,
      'role': _role?.toJson(),
      'reviewedRevision': _reviewedRevision,
    });
    // The controller serializes these writes, redacts content, and closes
    // the writer before deleting a profile. No UI write can recreate it.
    unawaited(
      _controller.saveEditorDraft(_draftTarget, payload).catchError((Object _) {
        if (mounted && !_complete) {
          setState(() => _error = _l.teamProjectEditorDraftFailed);
        }
      }),
    );
  }

  Future<void> _restore() async {
    try {
      final saved = await _controller.readEditorDraft(_draftTarget);
      if (!mounted) return;
      if (saved != null && saved.isNotEmpty) {
        final data = jsonDecode(saved) as Map<String, dynamic>;
        setState(() {
          for (final e in (data['fields'] as Map<String, dynamic>).entries) {
            _text(e.key).text = e.value as String;
          }
          _settings = TeamProjectSettings.fromJson(
            Map<String, dynamic>.from(data['settings'] as Map),
          );
          _repos = [
            for (final r in data['repos'] as List)
              TeamRepo.fromJson(Map<String, dynamic>.from(r as Map)),
          ];
          _milestones = [
            for (final m in data['milestones'] as List)
              TeamMilestone.fromJson(Map<String, dynamic>.from(m as Map)),
          ];
          _tasks = [
            for (final t in data['tasks'] as List)
              TeamTask.fromJson(Map<String, dynamic>.from(t as Map)),
          ];
          _phases = [
            for (final p in data['phases'] as List)
              TeamPhase.fromJson(Map<String, dynamic>.from(p as Map)),
          ];
          _serverId = data['server'] as String?;
          _roleId = data['roleId'] as String?;
          _budgetChoice = data['budget'] as String?;
          _planFirst = data['planFirst'] as bool? ?? true;
          _chargingTouched = data['chargingTouched'] as bool? ?? false;
          if (data['role'] != null) {
            _role = TeamProjectRole.fromJson(
              Map<String, dynamic>.from(data['role'] as Map),
            );
          }
          _reviewedRevision =
              data['reviewedRevision'] as int? ?? _reviewedRevision;
        });
      }
    } catch (_) {
      if (mounted) setState(() => _error = _l.teamProjectEditorDraftFailed);
    }
    if (mounted) setState(() => _restoring = false);
  }

  Widget _field(
    String key,
    String label, {
    String initial = '',
    bool multiline = false,
    bool number = false,
    bool decimal = false,
  }) => KitField(
    key: ValueKey(key),
    label: label,
    controller: _text(key, initial),
    draft: _fieldDrafts.putIfAbsent(
      key,
      () => _ControllerDraft(
        target: '$_draftTarget.$key',
        owner: _controller,
        controller: _text(key, initial),
        isAlive: () => mounted && !_complete,
        onFailure: () {
          if (mounted && !_complete) {
            setState(() => _error = _l.teamProjectEditorDraftFailed);
          }
        },
      ),
    ),
    kind: multiline
        ? KitFieldKind.multiline
        : number
        ? KitFieldKind.number
        : KitFieldKind.text,
    decimal: decimal,
    onChanged: (_) => _change(() {}),
  );
  Widget _choice(
    String label,
    Map<String, String> options,
    String? selected,
    ValueChanged<String> onChanged,
  ) => Column(
    crossAxisAlignment: CrossAxisAlignment.stretch,
    children: [
      KitSectionLabel.inline(label),
      if (options.isEmpty)
        KitNotice(message: _l.teamProjectEditorNoOptions)
      else
        KitChoiceList<String>.single(
          semanticsLabel: label,
          choices: [
            for (final e in options.entries)
              KitChoice(value: e.key, title: e.value),
          ],
          selected: selected,
          onSelected: onChanged,
        ),
    ],
  );
  Widget _button(
    String label,
    VoidCallback action, {
    bool destructive = false,
  }) => KitButton.tertiary(
    label: label,
    onPressed: _working ? null : action,
    destructive: destructive,
  );

  TeamProjectSettings _editedSettings() => _settings.copyWith(
    maxLanes: _settings.mode == 'single'
        ? 1
        : int.tryParse(_value('lanes')) ?? 0,
    maxFixRounds: int.tryParse(_value('rounds')) ?? 0,
    budget: TeamBudget(
      chosen: _budgetChoice != null,
      unlimited: _budgetChoice == 'unlimited',
      daily: _budgetChoice == 'unlimited'
          ? null
          : double.tryParse(_value('daily')),
      total: _budgetChoice == 'unlimited'
          ? null
          : double.tryParse(_value('total')),
      taskTokens: int.tryParse(_value('tokens')),
    ),
  );
  String? _settingsError() {
    final s = _editedSettings();
    if (s.mode.isEmpty) return _l.teamProjectEditorChooseMode;
    if (s.maxLanes < 1 || s.maxLanes > 32) {
      return _l.teamProjectEditorPositiveLanes;
    }
    if (!s.budget.chosen) return _l.teamProjectEditorChooseBudget;
    if (!s.budget.unlimited &&
        ((s.budget.daily ?? 0) <= 0 || (s.budget.total ?? 0) <= 0)) {
      return _l.teamProjectEditorPositiveBudget;
    }
    if (s.maxFixRounds < 0 || s.maxFixRounds > 3) {
      return _l.teamProjectEditorFixRoundsRange;
    }
    if (_value('tokens').isNotEmpty && (s.budget.taskTokens ?? 0) <= 0) {
      return _l.teamProjectEditorPositiveTokens;
    }
    return null;
  }

  String? _creationError() {
    final settingsError = _settingsError();
    if (settingsError != null) return settingsError;
    final noGoal = _value('goal').isEmpty;
    if (noGoal || _allRepos().isEmpty) {
      if (noGoal && _allRepos().isEmpty && !_repoTyped) {
        return _l.teamProjectEditorRequired;
      }
      if (noGoal) return _l.teamProjectEditorGoalRequired;
      return _repoTyped
          ? _l.teamProjectEditorRepoIncomplete
          : _l.teamProjectEditorRepoMissing;
    }
    if (widget.kind == _Kind.quick && (_roleId == null || _serverId == null)) {
      return _l.teamProjectEditorChooseRoleServer;
    }
    return null;
  }

  /// A repo the person typed but has not added yet. It counts once it has a
  /// name, a folder and a place to run, so "Add repo" is optional for the
  /// first one.
  bool get _repoTyped =>
      _value('repoName').isNotEmpty || _value('repoPath').isNotEmpty;
  TeamRepo? _pendingRepo() {
    if (_serverId == null ||
        _value('repoName').isEmpty ||
        _value('repoPath').isEmpty) {
      return null;
    }
    return TeamRepo(
      id: _pendingRepoId ??= _newId(),
      name: _value('repoName'),
      path: _value('repoPath'),
      serverId: _serverId!,
    );
  }

  String? _pendingRepoId;
  List<TeamRepo> _allRepos() => [..._repos, ?_pendingRepo()];

  void _updateCharging() {
    if (_chargingTouched) return;
    final phone =
        _controller.snapshot?.servers.any(
          (s) =>
              s.phone &&
              (_serverId == s.id || _allRepos().any((r) => r.serverId == s.id)),
        ) ??
        false;
    _settings = _settings.copyWith(
      chargingOnly:
          phone && _settings.mode == 'parallel' && DateTime.now().hour >= 23,
    );
  }

  Future<bool> _send(
    TeamProjectAction action, {
    TeamSpec? spec,
    List<TeamTask>? tasks,
    List<TeamPhase>? phases,
    TeamProjectRole? role,
    String? text,
    bool close = true,
  }) async {
    if (_working || _complete) return false;
    _change(() {
      _working = true;
      _error = null;
      _refusal = null;
    });
    final result = await _controller.execute(
      TeamProjectCommand(
        requestId: _controller.newRequestId(),
        action: action,
        projectId: widget.projectId,
        expectedRevision: _reviewedRevision,
        name: _creating ? _projectName() : _value('name'),
        text: text ?? _value('goal'),
        settings: _editedSettings(),
        spec: spec,
        tasks: tasks,
        phases: phases,
        repos: _allRepos(),
        roleId: _roleId ?? '',
        serverId: _serverId ?? '',
        role: role,
        confirmed: !_planFirst,
      ),
    );
    if (result.accepted) _reviewedRevision = result.revision;
    // A completed command consumes its draft even if the person dismissed
    // the sheet while the write was in flight.
    if (result.accepted && close) {
      _complete = true;
      try {
        await _controller.clearEditorDraft(_draftTarget);
        for (final draft in _fieldDrafts.values) {
          await draft.clear();
        }
      } catch (_) {
        if (mounted) {
          setState(() {
            _working = false;
            _error = _l.teamProjectEditorDraftClearFailed;
          });
        }
        return true;
      }
    }
    if (!mounted) return result.accepted;
    _change(() {
      _working = false;
      _refusal = result.accepted || result.code == 'staleRevision'
          ? null
          : teamRefusalFor(
              _l,
              action,
              result.code.isEmpty ? 'saveFailed' : result.code,
            );
      _error = result.accepted
          ? null
          : result.code == 'staleRevision'
          ? _l.teamProjectEditorChangedElsewhere
          : _refusal!.message;
    });
    if (result.accepted && close) Navigator.of(context).pop();
    return result.accepted;
  }

  /// The plain sentence, a way forward, and the code under Details.
  List<Widget> _refusalNotice(TeamRefusal refusal) => [
    KitNotice(
      key: const ValueKey('team-refusal'),
      message: refusal.message,
      notes: [refusal.next],
      actions: [KitAction(label: _l.teamProjectRetry, onPressed: _save)],
    ),
    KitDetailsFold(
      values: [KitTechnicalValue(_l.teamRefusalCode, refusal.code)],
    ),
  ];

  Future<void> _reload() async {
    final approved = await showKitConfirm(
      context,
      title: _l.teamProjectEditorReloadConfirmTitle,
      body: _l.teamProjectEditorDiscardDraft,
      confirmLabel: _l.teamProjectEditorReload,
    );
    if (!approved || !mounted) return;
    _complete = true;
    try {
      await _controller.clearEditorDraft(_draftTarget);
      for (final draft in _fieldDrafts.values) {
        await draft.clear();
      }
    } catch (_) {
      if (mounted) {
        setState(() {
          _complete = false;
          _error = _l.teamProjectEditorDraftFailed;
        });
      }
      return;
    }
    if (!mounted) return;
    final navigator = Navigator.of(context);
    final controller = _controller;
    final kind = widget.kind;
    final id = widget.projectId;
    navigator.pop();
    unawaited(_open(navigator.context, controller, kind, id));
  }

  Future<void> _save() async {
    if (_complete) return;
    if (_creating ||
        widget.kind == _Kind.settings ||
        widget.kind == _Kind.defaults) {
      final error = _settingsError();
      if (error != null) {
        _change(() => _error = error);
        return;
      }
    }
    if (_creating) {
      final error = _creationError();
      if (error != null) {
        _change(() => _error = error);
        return;
      }
      await _send(
        widget.kind == _Kind.quick
            ? TeamProjectAction.createQuickTask
            : TeamProjectAction.createProject,
        spec: TeamSpec(
          goal: _value('goal'),
          contextFiles: _lines(_value('contextFiles')),
        ),
      );
    } else if (widget.kind == _Kind.spec) {
      await _saveSpec(approve: true);
    } else if (widget.kind == _Kind.plan) {
      final edited = [
        for (final t in _tasks)
          t.copyWith(
            title: _value('task-${t.id}'),
            criteria: _lines(_value('criteria-${t.id}')),
          ),
      ];
      await _send(
        _project?.status == 'plan'
            ? TeamProjectAction.approvePlan
            : TeamProjectAction.replan,
        tasks: edited,
        phases: _phases,
      );
    } else if (widget.kind == _Kind.settings || widget.kind == _Kind.defaults) {
      await _send(
        widget.kind == _Kind.defaults
            ? TeamProjectAction.updateDefaults
            : TeamProjectAction.updateSettings,
      );
    } else if (_role != null) {
      if (_value('roleName').isEmpty) {
        _change(() => _error = _l.teamProjectEditorRoleRequired);
        return;
      }
      final saved = await _send(
        TeamProjectAction.saveRole,
        role: _role!.copyWith(
          name: _value('roleName'),
          instructions: _value('roleInstructions'),
          model: _value('roleModel'),
          fallbackModel: _value('roleFallback'),
        ),
        close: false,
      );
      if (saved && mounted) _change(() => _role = null);
    }
  }

  String _projectName() {
    final name = _value('name');
    if (name.isNotEmpty) return name;
    final goal = _value('goal').split('\n').first.trim();
    return goal.length > 40 ? '${goal.substring(0, 40).trim()}…' : goal;
  }

  List<String> _lines(String value) => value
      .split('\n')
      .map((v) => v.trim())
      .where((v) => v.isNotEmpty)
      .toList();
  TeamSpec _currentSpec() => TeamSpec(
    version: (_project?.specVersions.lastOrNull?.version ?? 0) + 1,
    goal: _value('goal'),
    constraints: _value('constraints'),
    decisions: _value('decisions'),
    outOfScope: _value('outOfScope'),
    contextFiles: _lines(_value('contextFiles')),
    milestones: [
      for (final m in _milestones)
        m.copyWith(
          title: _value('milestone-${m.id}'),
          criteria: _lines(_value('milestoneCriteria-${m.id}')),
        ),
    ],
  );
  Future<void> _saveSpec({required bool approve}) async {
    final spec = _currentSpec();
    if (spec.goal.isEmpty ||
        (approve &&
            (spec.milestones.isEmpty ||
                spec.milestones.any(
                  (m) => m.title.isEmpty || m.criteria.isEmpty,
                )))) {
      _change(() => _error = _l.teamProjectEditorSpecRequired);
      return;
    }
    if (await _send(
          TeamProjectAction.saveSpecDraft,
          spec: spec,
          close: !approve,
        ) &&
        approve &&
        mounted) {
      await _send(TeamProjectAction.approveSpec, spec: spec);
    }
  }

  Future<void> _requestChange() async {
    final text = _value('changeRequest');
    if (text.isEmpty || _value('goal').isEmpty) {
      _change(() => _error = _l.teamProjectEditorChangeRequired);
      return;
    }
    if (!await _send(
          TeamProjectAction.saveSpecDraft,
          spec: _currentSpec(),
          close: false,
        ) ||
        !mounted) {
      return;
    }
    if (!await _send(
          TeamProjectAction.requestSpecChange,
          text: text,
          close: false,
        ) ||
        !mounted) {
      return;
    }
    final spec = _project!.specDraft;
    _change(() {
      _text('goal').text = spec.goal;
      _text('constraints').text = spec.constraints;
      _text('decisions').text = spec.decisions;
      _text('outOfScope').text = spec.outOfScope;
      _text('contextFiles').text = spec.contextFiles.join('\n');
      _milestones = [...spec.milestones];
      for (final m in _milestones) {
        _text('milestone-${m.id}').text = m.title;
        _text('milestoneCriteria-${m.id}').text = m.criteria.join('\n');
      }
      _text('changeRequest').clear();
    });
  }

  @override
  Widget build(BuildContext context) {
    final title = switch (widget.kind) {
      _Kind.create => _l.teamProjectEditorNewProject,
      _Kind.quick => _l.teamProjectEditorQuickTask,
      _Kind.spec => _l.teamProjectEditorSpec,
      _Kind.plan => _l.teamProjectEditorPlan,
      _Kind.settings => _l.teamProjectEditorSettings,
      _Kind.roles => _l.teamProjectEditorRoles,
      _Kind.defaults => _l.teamProjectEditorDefaults,
    };
    final creationError = _creating ? _creationError() : null;
    // At large text the hint under the pinned button would eat the form, so
    // it moves to the end of the scrolling form instead (still shown once).
    final large = MediaQuery.textScalerOf(context).scale(1) >= 1.5;
    final inlineReason = large && creationError != null;
    return KitSheet(
      title: title,
      handle: false,
      fill: true,
      loading: _working || _restoring,
      onClose: () => Navigator.of(context).pop(),
      primary:
          (widget.kind == _Kind.roles && _role == null) ||
              (widget.kind == _Kind.plan && _project?.status == 'planFailed')
          ? null
          : KitAction(
              label: switch (widget.kind) {
                _Kind.create => _l.teamProjectEditorStartPlanning,
                _Kind.quick =>
                  _planFirst
                      ? _l.teamProjectEditorStartPlanning
                      : _l.teamProjectEditorStartTask,
                _Kind.spec => _l.teamProjectEditorApproveSpec,
                _Kind.plan =>
                  _project?.status == 'plan'
                      ? _l.teamProjectEditorApprovePlan
                      : _l.teamProjectEditorApplyPlan,
                _ => _l.teamProjectEditorSave,
              },
              onPressed:
                  _working || _restoring || _complete || creationError != null
                  ? null
                  : _save,
              disabledReason: inlineReason ? null : creationError,
              working: _working,
            ),
      secondary: widget.kind == _Kind.spec
          ? KitAction(
              label: _l.teamProjectEditorSaveDraft,
              onPressed: _working || _restoring
                  ? null
                  : () => _saveSpec(approve: false),
            )
          : null,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          if (_error != null && _refusal?.message == _error)
            ..._refusalNotice(_refusal!)
          else if (_error != null)
            KitNotice(message: _error!),
          if (_project != null && _project!.revision != _reviewedRevision)
            _button(_l.teamProjectEditorReload, _reload),
          if (_restoring)
            const KitSkeletonRows()
          else ...[
            if (_creating) ..._creation(),
            if (_creating ||
                widget.kind == _Kind.settings ||
                widget.kind == _Kind.defaults)
              ..._settingsFields(),
            if (widget.kind == _Kind.spec) ..._specFields(),
            if (widget.kind == _Kind.plan) ..._planFields(),
            if (widget.kind == _Kind.roles) ..._roleFields(),
            if (inlineReason) KitNotice(message: creationError),
          ],
        ],
      ),
    );
  }

  List<Widget> _creation() => [
    _field('goal', _l.teamProjectEditorGoalLabel, multiline: true),
    KitSectionLabel.inline(_l.teamProjectEditorRepos),
    for (final r in _repos)
      KitRow(
        title: r.name,
        supporting: TextSpan(
          text: _controller.snapshot?.servers
              .where((s) => s.id == r.serverId)
              .firstOrNull
              ?.name,
        ),
        action: KitAction(
          label: _l.teamProjectEditorRemove,
          onPressed: () => _change(() => _repos.remove(r)),
        ),
      ),
    _field('repoName', _l.teamProjectEditorRepoName),
    _field('repoPath', _l.teamProjectEditorRepoPath),
    _choice(
      _l.teamProjectEditorWhereRuns,
      {
        for (final s in _controller.snapshot?.servers ?? <TeamServer>[])
          s.id: s.name,
      },
      _serverId,
      (v) => _change(() {
        _serverId = v;
        _updateCharging();
      }),
    ),
    _button(_l.teamProjectEditorAddRepo, () {
      if (_serverId == null ||
          _value('repoPath').isEmpty ||
          _value('repoName').isEmpty) {
        _change(() => _error = _l.teamProjectEditorRepoRequired);
        return;
      }
      _change(() {
        _repos.add(
          TeamRepo(
            id: _newId(),
            name: _value('repoName'),
            path: _value('repoPath'),
            serverId: _serverId!,
          ),
        );
        _text('repoName').clear();
        _text('repoPath').clear();
        _pendingRepoId = null;
        _error = null;
        _updateCharging();
      });
    }),
    if (widget.kind == _Kind.quick) ...[
      _choice(
        _l.teamProjectEditorRole,
        {
          for (final r in _controller.snapshot?.roles ?? <TeamProjectRole>[])
            r.id: r.name,
        },
        _roleId,
        (v) => _change(() => _roleId = v),
      ),
      KitSwitchRow(
        title: _l.teamProjectEditorPlanFirst,
        value: _planFirst,
        onChanged: (v) => _change(() => _planFirst = v),
      ),
    ],
  ];

  /// The host that will run the work: the chosen one, else where the first
  /// repo lives. Only measured numbers are shown; with none, it says so.
  String _costLine() {
    final servers = _controller.snapshot?.servers ?? const <TeamServer>[];
    final id = _serverId ?? _allRepos().firstOrNull?.serverId;
    final host = servers.where((s) => s.id == id).firstOrNull;
    if (host == null) return _l.teamProjectEditorCostNoHost;
    final name = host.phone ? _l.teamProjectEditorThisPhone : host.name;
    final memory = host.memoryMb;
    if (memory == null || memory <= 0) {
      return _l.teamProjectEditorCostNotMeasured(name);
    }
    return _l.teamProjectEditorCostMeasured(name, memory.round().toString());
  }

  List<Widget> _modeFields() => [
    _choice(
      _l.teamProjectEditorMode,
      {
        'single': _l.teamProjectEditorSingle,
        'parallel': _l.teamProjectEditorParallel,
      },
      _settings.mode.isEmpty ? null : _settings.mode,
      (v) => _change(() {
        _settings = _settings.copyWith(mode: v);
        _updateCharging();
      }),
    ),
    if (_settings.mode == 'parallel')
      _field('lanes', _l.teamProjectEditorMaxLanes, number: true),
    KitNotice(message: _costLine()),
    KitSwitchRow(
      title: _l.teamProjectEditorCharging,
      value: _settings.chargingOnly,
      onChanged: (v) => _change(() {
        _chargingTouched = true;
        _settings = _settings.copyWith(chargingOnly: v);
      }),
    ),
  ];
  List<Widget> _screenFields() => [
    KitSwitchRow(
      title: _l.teamProjectEditorScreenOff,
      value: _settings.keepWorkingScreenOff,
      onChanged: (v) => _change(
        () => _settings = _settings.copyWith(keepWorkingScreenOff: v),
      ),
    ),
    KitNotice(message: _l.teamProjectEditorScreenOffHelp),
  ];
  List<Widget> _reviewFields() => [
    _choice(
      _l.teamProjectEditorReview,
      {
        'milestones': _l.teamProjectEditorMilestonesRisk,
        'every_step': _l.teamProjectEditorEveryStep,
      },
      _settings.reviewLevel,
      (v) => _change(() => _settings = _settings.copyWith(reviewLevel: v)),
    ),
  ];
  List<Widget> _budgetFields() => [
    _choice(
      _l.teamProjectEditorBudget,
      {
        'limited': _l.teamProjectEditorSetLimits,
        'unlimited': _l.teamProjectEditorNoLimit,
      },
      _budgetChoice,
      (v) => _change(() => _budgetChoice = v),
    ),
    if (_budgetChoice == 'limited') ...[
      _field(
        'daily',
        _l.teamProjectEditorDailyBudget,
        number: true,
        decimal: true,
      ),
      _field(
        'total',
        _l.teamProjectEditorTotalBudget,
        number: true,
        decimal: true,
      ),
      KitNotice(message: _l.teamProjectEditorBudgetHelp),
    ],
  ];
  List<Widget> _limitFields() => [
    _field('tokens', _l.teamProjectEditorTaskTokens, number: true),
    KitSwitchRow(
      title: _l.teamProjectEditorAutoFix,
      value: _settings.autoFix,
      onChanged: (v) =>
          _change(() => _settings = _settings.copyWith(autoFix: v)),
    ),
    if (_settings.autoFix)
      _field('rounds', _l.teamProjectEditorMaxRounds, number: true),
  ];

  /// Settings and defaults keep one flat order. A new project shows what it
  /// must decide first (mode, review, budget) and folds the rest below.
  List<Widget> _settingsFields() => _creating
      ? [
          ..._modeFields(),
          ..._reviewFields(),
          ..._budgetFields(),
          KitSectionLabel.inline(_l.teamProjectEditorMoreOptions),
          _field('name', _l.teamProjectEditorName),
          KitNotice(message: _l.teamProjectEditorNameHelp),
          _field(
            'contextFiles',
            _l.teamProjectEditorContextFiles,
            multiline: true,
          ),
          KitNotice(message: _contextFilesHelp),
          ..._screenFields(),
          ..._limitFields(),
        ]
      : [
          ..._modeFields(),
          ..._screenFields(),
          ..._reviewFields(),
          ..._budgetFields(),
          ..._limitFields(),
        ];
  List<Widget> _specFields() => [
    KitNotice(message: _l.teamProjectEditorDraftApproval),
    _field('changeRequest', _l.teamProjectEditorChangeRequest, multiline: true),
    _button(_l.teamProjectEditorAskChange, _requestChange),
    _field('goal', _l.teamProjectEditorGoal, multiline: true),
    _field('contextFiles', _l.teamProjectEditorContextFiles, multiline: true),
    KitNotice(message: _contextFilesHelp),
    _field('constraints', _l.teamProjectEditorConstraints, multiline: true),
    _field('decisions', _l.teamProjectEditorDecisions, multiline: true),
    _field('outOfScope', _l.teamProjectEditorOutOfScope, multiline: true),
    KitSectionLabel.inline(_l.teamProjectEditorMilestones),
    for (final m in _milestones) ...[
      _field(
        'milestone-${m.id}',
        _l.teamProjectEditorMilestoneTitle,
        initial: m.title,
      ),
      _field(
        'milestoneCriteria-${m.id}',
        _l.teamProjectEditorCriteria,
        initial: m.criteria.join('\n'),
        multiline: true,
      ),
      Wrap(
        children: [
          if (_milestones.indexOf(m) > 0)
            _button(
              _l.teamProjectEditorMoveUp,
              () => _change(() {
                final i = _milestones.indexOf(m);
                _milestones.removeAt(i);
                _milestones.insert(i - 1, m);
              }),
            ),
          if (_milestones.indexOf(m) < _milestones.length - 1)
            _button(
              _l.teamProjectEditorMoveDown,
              () => _change(() {
                final i = _milestones.indexOf(m);
                _milestones.removeAt(i);
                _milestones.insert(i + 1, m);
              }),
            ),
          _button(
            _l.teamProjectEditorRemove,
            () => _change(() => _milestones.remove(m)),
          ),
        ],
      ),
    ],
    _button(
      _l.teamProjectEditorAddMilestone,
      () => _change(() => _milestones.add(TeamMilestone(id: _newId()))),
    ),
    _button(
      _l.teamProjectEditorHistory,
      () => _change(() => _history = !_history),
    ),
    if (_history)
      for (final spec in _project?.specVersions.reversed ?? <TeamSpec>[])
        _historyVersion(spec),
  ];
  String _specText(TeamSpec spec) => [
    _l.teamProjectEditorGoal,
    spec.goal,
    _l.teamProjectEditorConstraints,
    spec.constraints,
    _l.teamProjectEditorDecisions,
    spec.decisions,
    _l.teamProjectEditorOutOfScope,
    spec.outOfScope,
    _l.teamProjectEditorContextFiles,
    ...spec.contextFiles,
    _l.teamProjectEditorMilestones,
    for (final m in spec.milestones) ...[m.title, ...m.criteria],
  ].join('\n');
  Widget _historyVersion(TeamSpec spec) {
    final previous = _project?.specVersions
        .where((s) => s.version < spec.version)
        .lastOrNull;
    final at = DateTime.tryParse(spec.approvedAt);
    final age = at == null
        ? _l.teamProjectEditorUnknownDate
        : relativeAgeLabel(DateTime.now().difference(at), at: at, l10n: _l);
    final actor = spec.approvedBy == 'person'
        ? _l.teamProjectEditorYou
        : KitRedact.text(spec.approvedBy);
    final title = '${_l.teamProjectEditorVersion} ${spec.version}';
    final diff = previous == null
        ? <KitDiffFile>[]
        : [
            KitDiffFile.fromTexts(
              '${_l.teamProjectEditorVersion} ${previous.version} → $title',
              before: _specText(previous),
              after: _specText(spec),
            ),
          ];
    return KitExpandRow(
      title: title,
      supporting: TextSpan(
        text: '${_l.teamProjectEditorApprovedBy} $actor · $age',
      ),
      children: [
        KitText(_specText(spec)),
        if (diff.isNotEmpty)
          KitDiffView(
            files: diff,
            maxLines: 12,
            onOpenAll: () => showKitDiff(context, title: title, files: diff),
          ),
      ],
    );
  }

  Future<void> _recoverPlan(TeamProjectAction action) async {
    if (await _send(action, close: false) && mounted) {
      _change(() {
        _tasks = [...?_project?.tasks];
        _phases = [...?_project?.phases];
        for (final task in _tasks) {
          _text('task-${task.id}').text = task.title;
          _text('criteria-${task.id}').text = task.criteria.join('\n');
        }
      });
    }
  }

  List<Widget> _planFields() => [
    if (_project?.status == 'planFailed') ...[
      KitNotice(message: _l.teamProjectEditorPlanFailed),
      _button(
        _l.teamProjectEditorUseAsTask,
        () => _recoverPlan(TeamProjectAction.usePlanAsTask),
      ),
      _button(
        _l.teamProjectEditorAskAgain,
        () => _recoverPlan(TeamProjectAction.retryPlan),
      ),
    ] else ...[
      KitNotice(message: _l.teamProjectEditorPlanHelp),
      for (final phase in _phases) ...[
        KitSectionLabel.inline(phase.title),
        KitSwitchRow(
          title: _l.teamProjectEditorRisky,
          value: phase.risky,
          onChanged: (v) => _change(() {
            _phases[_phases.indexOf(phase)] = phase.copyWith(risky: v);
          }),
        ),
        for (final t in _tasks.where((t) => t.phaseId == phase.id)) ...[
          _field(
            'task-${t.id}',
            _l.teamProjectEditorTaskTitle,
            initial: t.title,
          ),
          _field(
            'criteria-${t.id}',
            _l.teamProjectEditorCriteria,
            initial: t.criteria.join('\n'),
            multiline: true,
          ),
          _choice(
            _l.teamProjectEditorRole,
            {
              for (final r
                  in _controller.snapshot?.roles ?? <TeamProjectRole>[])
                r.id: r.name,
            },
            t.roleId,
            (v) => _change(
              () => _tasks[_tasks.indexOf(t)] = t.copyWith(roleId: v),
            ),
          ),
          _choice(
            _l.teamProjectEditorRepo,
            {for (final r in _repos) r.id: r.name},
            t.repoId,
            (v) => _change(() {
              final r = _repos.firstWhere((r) => r.id == v);
              _tasks[_tasks.indexOf(t)] = t.copyWith(
                repoId: r.id,
                serverId: r.serverId,
              );
            }),
          ),
          _choice(
            _l.teamProjectEditorServer,
            {
              for (final server
                  in _controller.snapshot?.servers ?? <TeamServer>[])
                server.id: server.name,
            },
            t.serverId,
            (v) => _change(
              () => _tasks[_tasks.indexOf(t)] = t.copyWith(serverId: v),
            ),
          ),
          if (_tasks.length > 1)
            KitChoiceList<String>.multi(
              semanticsLabel: _l.teamProjectEditorDependencies,
              choices: [
                for (final other in _tasks.where((o) => o.id != t.id))
                  KitChoice(value: other.id, title: other.title),
              ],
              selected: t.dependsOn.toSet(),
              onChanged: (v) => _change(
                () => _tasks[_tasks.indexOf(t)] = t.copyWith(
                  dependsOn: v.toList(),
                ),
              ),
            ),
          _button(
            _l.teamProjectEditorRemoveTask,
            () => _change(() {
              _tasks.removeWhere((other) => other.id == t.id);
              _tasks = [
                for (final other in _tasks)
                  other.copyWith(
                    dependsOn: other.dependsOn
                        .where((id) => id != t.id)
                        .toList(),
                  ),
              ];
            }),
          ),
        ],
      ],
    ],
  ];

  /// The model the same way Team settings picks it, from the phone's
  /// models; typed only where there is no catalog (the demo).
  Widget _modelField(String key, String label, {required bool fallback}) {
    final picker = widget.modelPicker;
    if (picker == null) return _field(key, label);
    final current = _value(key);
    return KitRow(
      key: ValueKey(key),
      title: label,
      supporting: TextSpan(
        text: current.isNotEmpty
            ? current
            : fallback
            ? _l.teamProjectEditorNoFallback
            : _l.teamRoleModelSheetDefault,
      ),
      onTap: () async {
        final choice = await picker(
          context,
          current: current.isEmpty ? null : current,
          fallback: fallback,
        );
        if (choice == null || !mounted) return;
        _change(() => _text(key).text = choice.spec ?? '');
      },
    );
  }

  void _editRole(TeamProjectRole role) => _change(() {
    _role = role;
    _text('roleName').text = role.name;
    _text('roleInstructions').text = role.instructions;
    _text('roleModel').text = role.model;
    _text('roleFallback').text = role.fallbackModel;
  });
  List<Widget> _roleFields() => [
    if (_role == null) ...[
      for (final role in _controller.snapshot?.roles ?? <TeamProjectRole>[])
        KitRow(
          title: role.name,
          supporting: TextSpan(
            text: [
              role.model,
              if (role.readOnly) _l.teamProjectEditorReadOnlyShort,
            ].where((t) => t.isNotEmpty).join(' · '),
          ),
          onTap: () => _editRole(role),
        ),
      _button(
        _l.teamProjectEditorAddRole,
        () => _editRole(TeamProjectRole(id: _newId())),
      ),
    ] else ...[
      _field('roleName', _l.teamProjectEditorRoleName),
      _field(
        'roleInstructions',
        _l.teamProjectEditorInstructions,
        multiline: true,
      ),
      if (_role!.readOnly) KitNotice(message: _l.teamProjectEditorReadOnlyRole),
      _modelField('roleModel', _l.teamProjectEditorModel, fallback: false),
      _modelField('roleFallback', _l.teamProjectEditorFallback, fallback: true),
      for (final p in _controller.snapshot?.projects ?? <TeamProject>[])
        for (final t in p.tasks.where((t) => t.roleId == _role!.id))
          KitRow(
            title: t.title,
            supporting: TextSpan(text: p.name),
          ),
      _button(_l.teamProjectEditorAllRoles, () => _change(() => _role = null)),
    ],
  ];
}
