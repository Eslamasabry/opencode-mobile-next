part of 'team_project_editors.dart';

/// Plan recovery, per-role model choice and the roles list of the editor.
extension _EditorRoles on _EditorState {
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

  /// A role's model as the person reads it: the catalog's name for it, the
  /// spec itself when the catalog does not list it, and "Server default" when
  /// none is chosen (never the chat's model).
  String _modelLabel(String spec) {
    if (spec.isEmpty) return _l.teamModelServerDefault;
    final models =
        TeamExecutionGate.of(_controller)?.connection.catalog?.models ??
        const <CatalogModel>[];
    for (final m in models) {
      if (teamModelSpec(m.providerID, m.id) == spec) return m.name;
    }
    return spec;
  }

  /// Picks [roleId]'s model and saves the complete role at once, so a new job
  /// uses it. Planner and checker stay read-only (their role is saved whole).
  Future<void> _pickRoleModel(String roleId) async {
    final picker = widget.modelPicker;
    final role = _controller.snapshot?.roles
        .where((r) => r.id == roleId)
        .firstOrNull;
    if (picker == null || role == null || _working || !mounted) return;
    final choice = await picker(
      context,
      current: role.model.isEmpty ? null : role.model,
      fallback: false,
    );
    if (choice == null || !mounted) return;
    final spec = choice.spec ?? '';
    if (spec == role.model) return;
    final saved = await _send(
      TeamProjectAction.saveRole,
      role: role.copyWith(model: spec),
      close: false,
    );
    if (!saved || !mounted || roleId != 'planner') return;
    final failed = _controller.snapshot?.projects.where(
      (p) => p.status == 'planFailed',
    );
    final target =
        failed?.where((p) => p.id == widget.retryProjectId).firstOrNull ??
        failed?.firstOrNull;
    if (target != null) _change(() => _askAgainProject = target.id);
  }

  /// The saved planner choice only affects new jobs, so asking again is the
  /// person's explicit tap: a fresh proposal from the current roles.
  Future<void> _askAgain() async {
    final id = _askAgainProject;
    final project = _controller.snapshot?.projects
        .where((p) => p.id == id)
        .firstOrNull;
    if (id == null || project == null) return;
    final sent = await _send(
      TeamProjectAction.retryPlan,
      projectId: id,
      expectedRevision: project.revision,
      text: '',
      close: false,
    );
    if (sent && mounted) Navigator.of(context).pop();
  }

  List<Widget> _modelFields() {
    if (widget.modelPicker == null) return const [];
    final roles = _controller.snapshot?.roles ?? const <TeamProjectRole>[];
    final rows = [
      for (final id in const ['planner', 'worker', 'checker'])
        ...roles.where((r) => r.id == id),
    ];
    if (rows.isEmpty) return const [];
    return [
      KitSectionLabel.inline(_l.teamModelsSection),
      KitReveal(
        child: _askAgainProject == null
            ? null
            : KitNotice(
                key: const ValueKey('team-model-ask-again'),
                message: _l.teamModelPlannerSaved,
                actions: [
                  KitAction(
                    key: const ValueKey('team-model-ask-again-action'),
                    label: _l.teamProjectEditorAskAgain,
                    onPressed: _working ? null : _askAgain,
                  ),
                ],
              ),
      ),
      for (final r in rows)
        KitRow(
          key: ValueKey('team-model-role-${r.id}'),
          title: r.name,
          supporting: TextSpan(text: _modelLabel(r.model)),
          onTap: _working ? null : () => _pickRoleModel(r.id),
        ),
      KitNotice(message: _l.teamModelsNote),
    ];
  }

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
            : _l.teamModelServerDefault,
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

  /// Planner, Worker, Checker, then the others as they were made: saving a
  /// role never moves it.
  List<TeamProjectRole> _orderedRoles() {
    const first = ['planner', 'worker', 'checker'];
    final roles = _controller.snapshot?.roles ?? const <TeamProjectRole>[];
    return [
      for (final id in first) ...roles.where((r) => r.id == id),
      ...roles.where((r) => !first.contains(r.id)),
    ];
  }

  String _roleNote(TeamProjectRole role) => [
    role.model,
    if (role.readOnly) _l.teamProjectEditorReadOnlyShort,
  ].where((t) => t.isNotEmpty).join(' · ');

  List<Widget> _roleFields() => [
    if (_role == null) ...[
      for (final role in _orderedRoles())
        KitRow(
          title: role.name,
          // A role with no model and no note has no second line, not an
          // empty one that holds a line's height open.
          supporting: _roleNote(role).isEmpty
              ? null
              : TextSpan(text: _roleNote(role)),
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
      // Model first and at the same place for every role; what only some
      // roles have (the read-only note) comes last, never above them.
      _modelField('roleModel', _l.teamProjectEditorModel, fallback: false),
      KitSectionLabel.inline(_l.teamProjectEditorFallbackSection),
      _modelField('roleFallback', _l.teamProjectEditorFallback, fallback: true),
      KitNotice(message: _l.teamProjectEditorFallbackNote),
      if (_role!.readOnly) KitNotice(message: _l.teamProjectEditorReadOnlyRole),
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
