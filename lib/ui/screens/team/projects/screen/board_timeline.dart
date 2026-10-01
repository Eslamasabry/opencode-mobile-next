part of '../team_projects_screen.dart';

class TeamProjectBoard extends StatefulWidget {
  const TeamProjectBoard({
    super.key,
    required this.controller,
    required this.projectId,
    this.milestone,
  });
  final TeamProjectController controller;
  final String projectId;
  final String? milestone;
  @override
  State<TeamProjectBoard> createState() => _TeamProjectBoardState();
}

class _TeamProjectBoardState extends State<TeamProjectBoard> {
  String? _milestone, _repo, _server;
  bool _graph = false;
  int? _column;
  @override
  void initState() {
    super.initState();
    _milestone = widget.milestone;
  }

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: widget.controller,
    builder: (context, _) {
      final c = widget.controller;
      final l = lookupAppLocalizations(Localizations.localeOf(context));
      final p = c.snapshot?.projects
          .where((p) => p.id == widget.projectId)
          .firstOrNull;
      if (p == null) {
        return KitStateView(
          icon: Icons.work_outline,
          title: l.teamProjectSelect,
        );
      }
      final phases = p.phases
          .where((ph) => _milestone == null || ph.milestoneId == _milestone)
          .map((ph) => ph.id)
          .toSet();
      final tasks = p.tasks
          .where(
            (t) =>
                phases.contains(t.phaseId) &&
                (_repo == null || t.repoId == _repo) &&
                (_server == null || t.serverId == _server),
          )
          .toList();
      final columns = [
        l.teamProjectBacklog,
        l.teamProjectReady,
        l.teamProjectWorking,
        l.teamProjectReview,
        l.teamProjectDone,
      ];
      int column(TeamTask t) => switch (t.status) {
        'running' => 2,
        'review' || 'verified' || 'waiting' || 'failed' || 'stalled' => 3,
        'done' || 'merged' => 4,
        _ => t.dependsOn.isEmpty ? 1 : 0,
      };
      KitTaskState mark(TeamTask t) => switch (column(t)) {
        2 => KitTaskState.working,
        3 => t.status == 'failed' ? KitTaskState.failed : KitTaskState.needsYou,
        4 => KitTaskState.done,
        _ => KitTaskState.waiting,
      };
      return KitScreen(
        topBar: KitTopBar(title: l.teamProjectBoard, subtitle: p.name),
        header: [
          _gutter(
            context,
            KitSegmented<bool>(
              segments: [
                KitSegment(value: false, label: l.teamProjectBoard),
                KitSegment(value: true, label: l.teamProjectGraph),
              ],
              selected: _graph,
              onChanged: (value) => setState(() => _graph = value),
              semanticsLabel: l.teamProjectBoard,
            ),
          ),
          KitPickerRow<String>(
            title: l.teamProjectMilestoneFilter,
            choices: [
              KitChoice(value: '', title: l.teamProjectAll),
              for (final m in p.specDraft.milestones)
                KitChoice(value: m.id, title: m.title),
            ],
            selected: _milestone ?? '',
            onSelected: (value) =>
                setState(() => _milestone = value.isEmpty ? null : value),
          ),
          KitPickerRow<String>(
            title: l.teamProjectRepoFilter,
            choices: [
              KitChoice(value: '', title: l.teamProjectAll),
              for (final r in p.repos) KitChoice(value: r.id, title: r.name),
            ],
            selected: _repo ?? '',
            onSelected: (value) =>
                setState(() => _repo = value.isEmpty ? null : value),
          ),
          KitPickerRow<String>(
            title: l.teamProjectServerFilter,
            choices: [
              KitChoice(value: '', title: l.teamProjectAll),
              for (final s in c.snapshot!.servers)
                KitChoice(value: s.id, title: s.name),
            ],
            selected: _server ?? '',
            onSelected: (value) =>
                setState(() => _server = value.isEmpty ? null : value),
          ),
        ],
        body: _graph
            ? ListView(
                padding: KitScreen.padding(context),
                children: [
                  KitWorkGraph(
                    nodes: [
                      for (final t in tasks)
                        KitWorkGraphNode(
                          id: t.id,
                          title: t.title,
                          mark: mark(t),
                          dependsOn: t.dependsOn,
                          word: _word(l, t.status),
                        ),
                    ],
                    onOpen: (id) => _openTask(context, c, p.id, id),
                  ),
                ],
              )
            : KitBoardLanes(
                columns: [
                  for (var i = 0; i < columns.length; i++)
                    KitBoardColumn(
                      label: columns[i],
                      count: tasks.where((t) => column(t) == i).length,
                    ),
                ],
                // Opens where the work is, not on an empty Backlog.
                selected:
                    _column ??
                    () {
                      for (var i = 0; i < columns.length; i++) {
                        if (tasks.any((t) => column(t) == i)) return i;
                      }
                      return 0;
                    }(),
                onSelected: (value) => setState(() => _column = value),
                laneBuilder: (context, i) => KitBoardLane(
                  cards: [
                    for (final t in tasks.where((t) => column(t) == i))
                      KitTaskCard(
                        key: ValueKey(t.id),
                        title: t.title,
                        mark: mark(t),
                        onOpen: () => _openTask(context, c, p.id, t.id),
                      ),
                  ],
                  empty: KitStateView(
                    icon: Icons.task_alt,
                    title: l.teamProjectNoTasks,
                    size: KitStateSize.inline,
                  ),
                ),
              ),
      );
    },
  );
}

class TeamProjectTimeline extends StatefulWidget {
  const TeamProjectTimeline({
    super.key,
    required this.controller,
    required this.projectId,
  });
  final TeamProjectController controller;
  final String projectId;
  @override
  State<TeamProjectTimeline> createState() => _TeamProjectTimelineState();
}

class _TeamProjectTimelineState extends State<TeamProjectTimeline> {
  String _filter = '';
  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: widget.controller,
    builder: (context, _) {
      final l = lookupAppLocalizations(Localizations.localeOf(context));
      final p = widget.controller.snapshot?.projects
          .where((p) => p.id == widget.projectId)
          .firstOrNull;
      final events = [...?p?.timeline].reversed.where(
        (e) =>
            _filter.isEmpty ||
            (_filter == 'problem'
                ? ['failed', 'problem', 'stalled', 'paused'].contains(e.kind)
                : e.kind == _filter),
      );
      final days = <String, List<TeamTimelineEvent>>{};
      for (final e in events) {
        final at = DateTime.tryParse(e.at);
        final day = at == null
            ? l.teamProjectUnknown
            : shortDateLabel(at, localeName: l.localeName);
        (days[day] ??= []).add(e);
      }
      return KitScreen(
        topBar: KitTopBar(title: l.teamProjectTimeline, subtitle: p?.name),
        header: [
          _gutter(
            context,
            KitSegmented<String>(
              segments: [
                KitSegment(value: '', label: l.teamProjectAll),
                KitSegment(value: 'decision', label: l.teamProjectDecisions),
                KitSegment(value: 'merge', label: l.teamProjectMerges),
                KitSegment(value: 'problem', label: l.teamProjectProblems),
              ],
              selected: _filter,
              onChanged: (v) => setState(() => _filter = v),
              semanticsLabel: l.teamProjectTimeline,
            ),
          ),
        ],
        body: ListView(
          padding: KitScreen.padding(context),
          children: [
            if (days.isEmpty)
              KitStateView(icon: Icons.history, title: l.teamProjectNoTasks),
            for (final day in days.entries)
              KitTimelineDay(
                title: day.key,
                status: p?.simulated == true ? l.teamProjectDemo : '',
                items: [
                  for (final group in _foldRepeats(day.value))
                    KitTeamItem(
                      title: group.count > 1
                          ? l.teamProjectTimelineRepeated(
                              group.first.text,
                              group.count,
                            )
                          : group.first.text,
                      detail: _role(
                        context,
                        widget.controller,
                        group.first.actor,
                      ),
                      meta: _age(context, group.first.at),
                      onPressed: group.first.taskId.isEmpty
                          ? null
                          : () => _openTask(
                              context,
                              widget.controller,
                              widget.projectId,
                              group.first.taskId,
                            ),
                    ),
                ],
              ),
          ],
        ),
      );
    },
  );
}
