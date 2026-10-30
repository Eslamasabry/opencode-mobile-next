/// The AI Team's board (docs/design/team-board-2026-09-26.md): a project's
/// tasks (Gas City beads) in five columns — Backlog · Ready · Working ·
/// Review · Done — as [KitBoardLanes]: paged one at a time with the next one
/// peeking on a phone, side by side on a wide window, under the strip of
/// column tabs with counts. It opens on Working.
///
/// - A card opens its team conversation ([openTeamBoardCard]); a card that
///   belongs to no task opens its Work sheet.
/// - A card's "⋯" and a long press open the move sheet. The person may start
///   a Backlog task, take a Ready one back, reprioritise either, cancel either
///   (confirmed), and put a cancelled one back; Working, Review and Done are
///   the team's. No gesture moves a card by itself.
/// - A move shows at once ("Moving to Ready…" in its new column) and stays
///   until the host shows it there; a refusal puts it back with a notice.
/// - The host's bookkeeping beads never appear ([teamBoardIsBookkeeping]).
/// - A host that allows no writes shows the board read-only and says so in
///   the one status line.
/// - Old cards stay at full strength; the status line says how old they are
///   (STATE-18, LOOK-14).
library;

import 'dart:async';

import 'package:flutter/material.dart';

import '../../../domain/orchestration_gateway.dart';
import '../../../domain/orchestration_work_edits.dart';
import '../../../l10n/app_localizations.dart';
import '../../../state/orchestration.dart';
import '../../../state/team_board.dart';
import '../../app_theme.dart';
import '../../kit/kit.dart';
import '../../kit/scenes/team_scenes.dart';
import '../../widgets/grace_timer.dart';
import '../../widgets/team_board_card.dart';
import '../../widgets/team_board_move_sheet.dart';
import '../team_conversation/team_conversation.dart';
import 'team_states.dart';
import 'work_sheet.dart';

AppLocalizations _copy(BuildContext context) =>
    lookupAppLocalizations(Localizations.localeOf(context));

/// How long a moved card waits in its new column for the host to show it
/// there before the board goes back to what the host says.
const teamBoardPendingHold = Duration(seconds: 30);

/// Opens [card]'s team conversation
/// (docs/design/team-conversation-2026-09-26.md) when it belongs to a task,
/// else the card's Work sheet.
Future<void> openTeamBoardCard(
  BuildContext context,
  OrchestrationController controller,
  TeamBoardCard card, {
  DateTime Function()? now,
}) {
  final runId = card.runId;
  if (runId != null) {
    return TeamConversation.open(context, controller, runId: runId);
  }
  return showWorkSheet(context, controller, card.id, now: now);
}

/// Opens the board for [controller].
Future<void> openTeamBoard(
  BuildContext context,
  OrchestrationController controller, {
  DateTime Function()? now,
}) => Navigator.of(context).push(
  KitPageRoute<void>(
    builder: (_) => TeamBoardScreen(controller: controller, now: now),
  ),
);

class TeamBoardScreen extends StatefulWidget {
  const TeamBoardScreen({
    super.key,
    required this.controller,
    this.projectId,
    this.onOpenCard,
    this.edits,
    this.now,
  });

  final OrchestrationController controller;

  /// The project to show first; the first one with open work when null.
  final String? projectId;

  /// Opens a card; [openTeamBoardCard] when null.
  final ValueChanged<TeamBoardCard>? onOpenCard;

  /// The bead edits; resolved from the controller's gateway when null.
  final OrchestrationWorkEditGateway? edits;

  /// Clock for ages and the Done window; tests pin it.
  final DateTime Function()? now;

  @override
  State<TeamBoardScreen> createState() => _TeamBoardScreenState();
}

class _TeamBoardScreenState extends State<TeamBoardScreen> {
  late final TeamBoardEdits _edits = TeamBoardEdits(
    widget.controller,
    edits: widget.edits,
  );
  TeamBoardColumn _column = TeamBoardColumn.working;

  /// The opening column is chosen once, from the first board with data.
  bool _columnChosen = false;
  String? _project;
  bool _projectChosen = false;
  bool _refreshing = false;
  int _busy = 0;

  /// Cards the person moved, by id, until the host shows them there.
  final _pending = <String, TeamBoardColumn>{};
  final _pendingTimers = <String, Timer>{};

  /// A move the host refused: the card's title and the host's words.
  _BoardFailure? _failure;

  DateTime get _now => (widget.now ?? DateTime.now)();

  @override
  void initState() {
    super.initState();
    _project = widget.projectId;
    _projectChosen = widget.projectId != null;
    widget.controller.addListener(_prune);
  }

  @override
  void dispose() {
    widget.controller.removeListener(_prune);
    for (final timer in _pendingTimers.values) {
      timer.cancel();
    }
    super.dispose();
  }

  bool get _ready =>
      widget.controller.phase == OrchestrationPhase.ready &&
      widget.controller.snapshot.hasData;

  TeamBoard _board() => buildTeamBoard(
    widget.controller.snapshot,
    projectId: _projectFilter,
    now: _now,
    pending: _pending,
  );

  /// The project filter: none when the host reports at most one project.
  String? get _projectFilter =>
      widget.controller.snapshot.projects.length > 1 ? _project : null;

  /// Drops the moves the host now shows (or whose card is gone).
  void _prune() {
    if (_pending.isEmpty || !mounted) return;
    final board = _board();
    final settled = [
      for (final id in _pending.keys)
        if (board.find(id)?.moving != true) id,
    ];
    if (settled.isEmpty) return;
    setState(() => settled.forEach(_dropPending));
  }

  void _dropPending(String id) {
    _pending.remove(id);
    _pendingTimers.remove(id)?.cancel();
  }

  void _chooseDefaultProject() {
    if (_projectChosen) return;
    final snapshot = widget.controller.snapshot;
    final projects = snapshot.projects;
    if (projects.length <= 1) return;
    _projectChosen = true;
    for (final project in projects) {
      final board = buildTeamBoard(snapshot, projectId: project.id, now: _now);
      if (board.total - board.count(TeamBoardColumn.done) > 0) {
        _project = project.id;
        return;
      }
    }
    _project = projects.first.id;
  }

  /// Working, unless it is empty: then the first column with something.
  TeamBoardColumn _openingColumn(TeamBoard board) {
    if (board.count(TeamBoardColumn.working) > 0) {
      return TeamBoardColumn.working;
    }
    for (final column in TeamBoardColumn.values) {
      if (board.count(column) > 0) return column;
    }
    return TeamBoardColumn.working;
  }

  void _select(TeamBoardColumn column) => setState(() => _column = column);

  void _chooseProject(String id) => setState(() {
    _project = id;
    _pending.clear();
    for (final t in _pendingTimers.values) {
      t.cancel();
    }
    _pendingTimers.clear();
  });

  /// The host's projects as one choice (the title's switcher).
  Future<void> _showProjects(List<OrchestrationProject> projects) async {
    final l10n = _copy(context);
    final current = _projectFilter;
    await showKitSheet<void>(
      context,
      title: l10n.teamBoardProjectTooltip,
      icon: AppIconography.projects,
      sheetKey: const ValueKey('team-board-project-sheet'),
      body: (sheet) => KitChoiceList<String>.single(
        semanticsLabel: l10n.teamBoardProjectTooltip,
        choices: [
          for (final project in projects)
            KitChoice(
              key: ValueKey('team-board-project-${project.id}'),
              value: project.id,
              title: project.name,
            ),
        ],
        selected: current,
        onSelected: (id) {
          Navigator.of(sheet).pop();
          if (id != current) _chooseProject(id);
        },
      ),
    );
  }

  Future<void> _refresh() async {
    if (_refreshing) return;
    setState(() => _refreshing = true);
    try {
      final controller = widget.controller;
      if (controller.phase == OrchestrationPhase.failed) {
        await controller.retry();
      } else {
        await controller.refresh();
      }
    } finally {
      if (mounted) setState(() => _refreshing = false);
    }
  }

  void _open(TeamBoardCard card) {
    final open = widget.onOpenCard;
    if (open != null) return open(card);
    openTeamBoardCard(context, widget.controller, card, now: widget.now);
  }

  Future<void> _showMoves(TeamBoardCard card) async {
    final moves = _edits.movesFor(card);
    final choice = await showTeamBoardMoveSheet(
      context,
      card: card,
      moves: moves,
      readOnly: _edits.readOnly,
      hasConversation: card.runId != null,
    );
    if (!mounted || choice == null) return;
    switch (choice) {
      case TeamBoardChoseOpen():
        _open(card);
      case TeamBoardChoseMove(:final move):
        await _move(card, move);
    }
  }

  Future<void> _move(TeamBoardCard card, TeamBoardMove move) async {
    WorkPriority? priority;
    if (move == TeamBoardMove.priority) {
      priority = await showTeamBoardPrioritySheet(
        context,
        current: card.priority,
      );
      if (priority == null || !mounted) return;
    }
    if (move == TeamBoardMove.cancel) {
      final sure = await confirmTeamBoardCancel(context, card.item.title);
      if (!sure || !mounted) return;
    }
    await _runMove(move, card, priority: priority);
  }

  /// Asks the host for [move]; Try again on a refusal comes straight here,
  /// the person has already chosen and confirmed.
  Future<void> _runMove(
    TeamBoardMove move,
    TeamBoardCard card, {
    WorkPriority? priority,
  }) async {
    final target = teamBoardMoveTarget(move);
    setState(() {
      _failure = null;
      _busy++;
      if (target != null) {
        _pending[card.id] = target;
        _pendingTimers.remove(card.id)?.cancel();
      }
    });
    final result = await _edits.run(move, card, priority: priority);
    if (!mounted) return;
    setState(() {
      _busy--;
      if (!result.ok) {
        _dropPending(card.id);
        _failure = _BoardFailure(
          card.item.title,
          result.message,
          () => unawaited(_runMove(move, card, priority: priority)),
        );
      } else if (target != null && _pending.containsKey(card.id)) {
        // The host took it; if it has not shown it yet, wait a while
        // for it, then trust what it says.
        _pendingTimers[card.id] = Timer(teamBoardPendingHold, () {
          if (mounted) setState(() => _dropPending(card.id));
        });
      }
    });
    _prune();
  }

  /// The one "Give the team a task" sheet (P3.5; the board's own add
  /// sheet is gone): Start now opens the task's conversation, Keep in
  /// backlog makes it and shows the Backlog here. A refusal stays in the
  /// sheet with what was typed.
  Future<void> _add() async {
    setState(() => _failure = null);
    await TeamConversation.start(
      context,
      widget.controller,
      offerBacklog: true,
      projectId: _projectFilter ?? _singleProject,
      onKeptInBacklog: (_) {
        if (mounted) _select(TeamBoardColumn.backlog);
      },
    );
  }

  String? get _singleProject {
    final projects = widget.controller.snapshot.projects;
    return projects.length == 1 ? projects.single.id : null;
  }

  @override
  Widget build(BuildContext context) {
    final l10n = _copy(context);
    return ListenableBuilder(
      listenable: widget.controller,
      builder: (context, _) {
        final tokens = KitTokens.of(context);
        final controller = widget.controller;
        final ready = _ready;
        if (ready) _chooseDefaultProject();
        final board = ready ? _board() : null;
        if (board != null && !board.isEmpty && !_columnChosen) {
          _columnChosen = true;
          _column = _openingColumn(board);
        }
        final projects = controller.snapshot.projects;
        final project = projects
            .where((p) => p.id == _projectFilter)
            .firstOrNull;
        final switchable = projects.length > 1 && project != null;
        // Once per screen (§6): an empty board offers it in its state instead.
        final canAdd =
            ready && _edits.canCreate && board != null && !board.isEmpty;
        final line = ready
            ? teamStatusLine(
                    context,
                    controller: controller,
                    keyPrefix: 'team-board',
                    onRetry: _refreshing ? null : _refresh,
                  ) ??
                  (_edits.readOnly
                      ? KitStatus(
                          kind: KitStatusKind.info,
                          id: 'team-board:read-only',
                          key: const ValueKey('team-board-read-only'),
                          icon: AppIconography.locked,
                          tone: AppStatusTone.neutral,
                          message: l10n.teamBoardStatusReadOnly,
                        )
                      : null)
            : null;
        final failure = _failure;
        return KitScreen(
          key: const ValueKey('team-board'),
          topBar: KitTopBar(
            title: l10n.teamBoardTitle,
            // The project under the title ("oc_app ▾") switches the board.
            subtitle: switchable
                ? project.name
                : projects.length == 1
                ? projects.single.name
                : null,
            onTitleTap: switchable ? () => _showProjects(projects) : null,
            titleTapLabel: switchable ? l10n.teamBoardProjectTooltip : null,
            titleKey: const ValueKey('team-board-project'),
            actions: [
              if (canAdd)
                KitAction(
                  key: const ValueKey('team-board-add'),
                  label: l10n.teamUiStartRunTitle,
                  icon: AppIconography.add,
                  onPressed: _add,
                ),
            ],
          ),
          status: line,
          header: [
            KitReveal(
              child: failure == null
                  ? null
                  : Padding(
                      padding: EdgeInsetsDirectional.only(
                        start: tokens.gutter,
                        end: tokens.gutter,
                        bottom: tokens.space2,
                      ),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        children: [
                          KitNotice(
                            key: const ValueKey('team-board-failure'),
                            tone: AppStatusTone.failure,
                            title: l10n.teamBoardMoveFailedTitle(failure.title),
                            message: l10n.teamBoardMoveFailedBody,
                            actions: [
                              KitAction(
                                key: const ValueKey('team-board-failure-retry'),
                                label: l10n.teamUiCardRetry,
                                icon: AppIcons.retry,
                                onPressed: failure.retry,
                              ),
                            ],
                            onDismiss: () => setState(() => _failure = null),
                          ),
                          // The host's own words: technical, under Details.
                          if (failure.hostWords?.trim() case final words?
                              when words.isNotEmpty)
                            KitDetailsFold(
                              label: l10n.teamUiTechnicalDetails,
                              foldKey: const ValueKey(
                                'team-board-failure-details',
                              ),
                              notes: [l10n.teamDispatchHostWords],
                              text: words,
                            ),
                        ],
                      ),
                    ),
            ),
          ],
          loading: teamScreenLoading(controller) || _refreshing || _busy > 0,
          loadingLabel: l10n.teamUiCardLoading,
          body: _body(context, board),
        );
      },
    );
  }

  static List<KitBoardColumn> _columns(
    AppLocalizations l10n,
    TeamBoard? board,
  ) => [
    for (final column in TeamBoardColumn.values)
      KitBoardColumn(
        label: teamBoardColumnWord(l10n, column),
        count: board?.count(column),
        needsYou: board != null && board.needsYou(column) ? 1 : 0,
        tabKey: ValueKey('team-board-tab-${column.name}'),
      ),
  ];

  Widget _body(BuildContext context, TeamBoard? board) {
    final l10n = _copy(context);
    final controller = widget.controller;
    if (teamScreenFailed(controller)) {
      return teamScreenState(
            context,
            controller: controller,
            keyPrefix: 'team-board',
            onRetry: _refreshing ? null : _refresh,
          ) ??
          const SizedBox.shrink();
    }
    if (board == null) {
      // The first answer: the columns without counts over skeleton cards
      // (§4), and after 8 s the honest "isn't answering".
      return GraceTimer(
        waiting: true,
        builder: (context, overdue) => overdue
            ? KitStateView(
                key: const ValueKey('team-board-not-answering'),
                icon: AppIconography.cloudOff,
                tone: AppStatusTone.neutral,
                title: l10n.teamUiStateNotAnsweringTitle,
                body: teamNotAnsweringBody(l10n, controller),
                secondary: KitAction(
                  key: const ValueKey('team-board-retry'),
                  label: l10n.teamUiCardRetry,
                  onPressed: _refreshing ? null : _refresh,
                  icon: AppIcons.retry,
                ),
              )
            : KeyedSubtree(
                key: const ValueKey('team-board-loading'),
                child: KitBoardLanes(
                  loading: true,
                  columns: _columns(l10n, null),
                  selected: TeamBoardColumn.working.index,
                  onSelected: (_) {},
                  stripKey: const ValueKey('team-board-tabs'),
                  laneBuilder: (context, index) => KitBoardLane.loading(
                    laneKey: ValueKey(
                      'team-board-column-${TeamBoardColumn.values[index].name}',
                    ),
                  ),
                ),
              ),
      );
    }
    if (board.isEmpty) {
      return KitRefresh(
        onRefresh: _refresh,
        child: ListView(
          key: const ValueKey('team-board-empty'),
          physics: const AlwaysScrollableScrollPhysics(),
          padding: EdgeInsetsDirectional.only(
            bottom: KitScreen.endPadding(context),
          ),
          children: [
            KitStateView(
              icon: AppIconography.checklist,
              illustration: const TeamBoardScene(),
              title: l10n.teamBoardEmptyTitle,
              body: l10n.teamBoardEmptyBody,
              primary: _edits.canCreate
                  ? KitAction(
                      key: const ValueKey('team-board-empty-add'),
                      label: l10n.teamUiStartRunTitle,
                      icon: AppIconography.add,
                      onPressed: _add,
                    )
                  : null,
            ),
          ],
        ),
      );
    }
    return KitBoardLanes(
      columns: _columns(l10n, board),
      selected: _column.index,
      onSelected: (index) => _select(TeamBoardColumn.values[index]),
      pagesKey: const ValueKey('team-board-pages'),
      stripKey: const ValueKey('team-board-tabs'),
      laneBuilder: (context, index) =>
          _lane(context, board, TeamBoardColumn.values[index]),
    );
  }

  Widget _lane(BuildContext context, TeamBoard board, TeamBoardColumn column) {
    final l10n = _copy(context);
    final cards = board.cards(column);
    final now = _now;
    final (icon, title, body) = switch (column) {
      TeamBoardColumn.backlog => (
        AppIconography.inbox,
        l10n.teamBoardEmptyBacklogTitle,
        l10n.teamBoardEmptyBacklogBody,
      ),
      TeamBoardColumn.ready => (
        AppIconography.queueAdd,
        l10n.teamBoardEmptyReadyTitle,
        l10n.teamBoardEmptyReadyBody,
      ),
      TeamBoardColumn.working => (
        AppIconography.agent,
        l10n.teamBoardEmptyWorkingTitle,
        l10n.teamBoardEmptyWorkingBody,
      ),
      TeamBoardColumn.review => (
        AppIconography.review,
        l10n.teamBoardEmptyReviewTitle,
        l10n.teamBoardEmptyReviewBody,
      ),
      TeamBoardColumn.done => (
        AppIconography.checkCircle,
        l10n.teamBoardEmptyDoneTitle,
        l10n.teamBoardEmptyDoneBody,
      ),
    };
    return KitBoardLane(
      laneKey: ValueKey('team-board-column-${column.name}'),
      listKey: ValueKey('team-board-list-${column.name}'),
      rowsKey: ValueKey('team-board-rows-${column.name}-$_projectFilter'),
      // Pull to refresh on the lane in view only: one per screen (LAY-12,
      // PERF-4).
      onRefresh: column == _column ? _refresh : null,
      empty: KitStateView(
        key: ValueKey('team-board-column-empty-${column.name}'),
        size: KitStateSize.inline,
        liveRegion: false,
        icon: icon,
        title: title,
        body: body,
      ),
      cards: [
        for (final card in cards)
          TeamBoardCardView(
            key: ValueKey('team-board-slot-${card.id}'),
            card: card,
            now: now,
            onOpen: () => _open(card),
            onMoves: _edits.movesFor(card).isEmpty
                ? null
                : () => _showMoves(card),
            onLongPress: () => _showMoves(card),
          ),
      ],
    );
  }
}

/// A move the host refused: the card's title, the host's words (for the
/// Details fold only) and what Try again does.
class _BoardFailure {
  const _BoardFailure(this.title, this.hostWords, this.retry);

  final String title;
  final String? hostWords;
  final VoidCallback retry;
}
