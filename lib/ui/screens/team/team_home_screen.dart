/// The AI Team page while the team is on (programme P3.4, "the team page
/// is one page"; [TeamPage] shows the off state in its place): what the
/// team does now, what needs the person, its tasks, and the team itself,
/// in the person's words.
///
/// Top to bottom, in one scroll view:
///
/// 1. The top bar ([KitTopBar]): "AI Team" with where the team runs as its
///    subtitle ("On this phone", "On dev-pc", plus "· Paused" when the
///    person paused it, "· Cooling down" when the heat guard paused it,
///    "· Stopped" when Android stopped it, "· Not answering"). The board is
///    its action; Search joins it only when there are more than eight
///    tasks, and opens the pinned [KitSearchField] with its one filter
///    menu. Its menu: Refresh, Change address (a team found at an
///    address), the phone's Termux team's own controls (keep it running,
///    stop, remove; reachable in every state) and Turn off: the
///    team-plugin-sheet's switches, merged here. Turned off, [TeamPage]
///    shows the off state on the same page.
/// 2. One line for the whole team, in the screen's status slot (so it and
///    the app's connection line never show at once: the slot draws the more
///    urgent): old data, else the heat guard's pause (why, and that it
///    carries on by itself — never Resume), else the stage of the task just
///    given ([TeamDispatchAttempts], P6.3: "Task created · sending it to the
///    team…", "Task sent to the team · waiting for a worker", "A worker
///    started your task", each only once the host confirmed it; a refusal
///    or an unconfirmed step comes before the team's Now), else the team's
///    Now ([teamNowStatus]). Android stopping the phone's Termux team is its
///    own line above the list, with Start the team again, and then the page
///    says nothing that contradicts it.
/// 3. What needs the person, only when something does, with no heading of
///    its own: one question as a request block with its answers. The one
///    question's block is also its task's row: it names the task, carries
///    its step count and opens its conversation, so the task is not listed
///    again below. Several questions are no section of their own (owner
///    rule 2026-09-27): each one's task row becomes the question ("Needs
///    you · Keep drafts in SQLite? · 2 min ago") and opens the Gate sheet
///    of `gate_sheet.dart`; a question with no task listed is a row of its
///    own at the top of the same list.
/// 4. **Tasks**: ONE panel of rows with no heading, ordered by urgency
///    (owner rule 2026-09-27): what needs the person, then what runs, then
///    what waits, then what finished (three shown, the rest behind one
///    row), never split into state sections. A row is the task's title,
///    one supporting line ("Working · 3 of 5 steps done", "Done · merged 5h
///    ago") and one leading mark ([KitTaskMark]) that carries the state; it
///    opens the task's conversation ([TeamConversation.open]), the same
///    page every other door to a task opens.
/// 5. The team itself, one panel: the **agents** row ("3 agents · 1
///    working", opening [TeamAgentsScreen]); **how it runs** (the host's
///    speed in one line, opening Technical details); and **what it spent
///    today** when the host reports it (an estimate for the whole team,
///    never a task's cost; unknown is never zero).
///
/// **Give the team a task** (TEAM-204, only with `controlMessage`) is the
/// screen's one primary button, pinned below the list; it opens
/// [StartRunSheet] through [TeamConversation.start], so a task given here
/// lands in its conversation like one given from Solo · Team. A task the
/// host made but its workers refused is said by the status line (the task
/// stays on the board; the host's words only under Technical details). Old
/// data shows the one status line with its age (the rows
/// stay at full strength, LOOK-14); pull to refresh calls
/// [OrchestrationController.refresh].
library;

import 'dart:async';

import 'package:flutter/foundation.dart' show ValueListenable;
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart' show ProviderScope;
import 'package:intl/intl.dart' show DateFormat;

import '../../../builtin/team/builtin_team.dart'
    show BuiltinTeam, BuiltinTeamStartProgress;
import '../../../builtin/thermal_guard.dart';
import '../../../builtin/thermal_guard_teams.dart'
    show thermalGuardSlotProvider;
import '../../../domain/orchestration_gateway.dart';
import '../../../l10n/app_localizations.dart';
import '../../../state/connection.dart';
import '../../../state/orchestration.dart';
import '../../../state/team_dispatch.dart';
import '../../../state/team_overview.dart';
import '../../../termux/team_runtime.dart';
import '../../app_theme.dart';
import '../../kit/kit.dart';
import '../../kit/scenes/team_scenes.dart';
import '../../widgets/relative_time.dart';
import '../../widgets/team_now.dart';
import '../../widgets/team_now_line_view.dart'
    show TeamNowActivity, teamNowActivityLine, teamWorkerStartUsual;
import '../../widgets/team_host_form.dart' show TeamHostProbe;
import '../../widgets/team_phone_onboarding.dart' show TeamPhoneKilledNotice;
import '../../widgets/team_receipt.dart';
import '../../widgets/team_vocabulary.dart';
import '../team_conversation/team_conversation.dart' show TeamConversation;
import 'gate_sheet.dart';
import 'start_run_sheet.dart';
import '../../../state/team_planning.dart'
    show TeamPlanningRequest, TeamPlanningStatus;
import 'team_board_screen.dart';
import 'team_agents_screen.dart';
import 'team_migration.dart';
import 'team_needs_you.dart';
import 'team_rig_screen.dart';
import 'team_settings_screen.dart';
import 'team_states.dart';

AppLocalizations _copy(BuildContext context) =>
    lookupAppLocalizations(Localizations.localeOf(context));

/// The task search's filters, offered with the search field only.
enum TeamRunFilter { active, blocked, completed, all }

/// Search and filters appear only for a longer list than this: a short
/// list needs no controls to find a task in it.
const teamHomeSearchAfter = 8;

/// Finished tasks shown before "Show N more".
const teamHomeDoneShown = 3;

class TeamHomeScreen extends StatefulWidget {
  const TeamHomeScreen({
    super.key,
    required this.controller,
    this.onOpenRun,
    this.onOpenAgent,
    this.now,
    this.connection,
    this.thermalGuard,
    this.probe,
    this.teamRuntime,
    this.onTeamChanged,
  });

  final OrchestrationController controller;

  /// The connection whose server this team belongs to: Change address and
  /// Turn off write its profile. The app's own when null (none in a test
  /// without one: then the page offers neither).
  final ConnectionController? connection;

  /// The heat guard's slot ([thermalGuardSlotProvider] when null): a pause
  /// for heat reads as one, never as the person's pause.
  final ValueListenable<ThermalGuard?>? thermalGuard;

  /// The Gas City probe of the address form; tests pass a fake.
  final TeamHostProbe? probe;

  /// The Termux team runtime; tests pass a fake.
  final TermuxTeamRuntime? teamRuntime;

  /// After Change address or Turn off replaced this team. [TeamPage]
  /// follows the connection by itself; opened on its own (over a task's
  /// conversation), the page goes back to the start, since the team it
  /// showed is gone.
  final VoidCallback? onTeamChanged;

  /// Opens a task; its conversation ([TeamConversation.open]) when null.
  final ValueChanged<OrchestrationRun>? onOpenRun;

  /// Opens an agent's detail from the agents list; pushes [AgentScreen]
  /// when null.
  final ValueChanged<OrchestrationAgent>? onOpenAgent;

  /// Clock for relative ages and the "today" group; tests pin it.
  final DateTime Function()? now;

  @override
  State<TeamHomeScreen> createState() => _TeamHomeScreenState();
}

class _TeamHomeScreenState extends State<TeamHomeScreen> {
  TeamRunFilter _filter = TeamRunFilter.all;
  final _search = TextEditingController();
  String _query = '';

  /// The search field and filters, behind the top bar's search action.
  bool _searchOpen = false;

  /// The finished tasks past the first three.
  bool _doneExpanded = false;

  bool _refreshing = false;

  /// Android stopped the phone's Termux team ([TeamPhoneKilledNotice]).
  bool _killed = false;

  /// Keeps the notice (and what it read) as the page moves between its
  /// states.
  final _killedKey = GlobalKey();

  ConnectionController? _scopeConnection;
  ValueListenable<ThermalGuard?>? _scopeHeat;

  DateTime get _now => (widget.now ?? DateTime.now)();

  ConnectionController? get _connection =>
      widget.connection ?? _scopeConnection;

  ValueListenable<ThermalGuard?>? get _heat =>
      widget.thermalGuard ?? _scopeHeat;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    // The app's own connection and heat guard, when this page runs inside
    // the app's provider scope (a test without one passes its own or
    // none).
    try {
      final container = ProviderScope.containerOf(context, listen: false);
      if (widget.connection == null) {
        try {
          _scopeConnection = container.read(connProvider);
        } catch (_) {
          _scopeConnection = null;
        }
      }
      if (widget.thermalGuard == null) {
        _scopeHeat = container.read(thermalGuardSlotProvider);
      }
    } on StateError {
      _scopeConnection = null;
      _scopeHeat = null;
    }
  }

  @override
  void dispose() {
    _search.dispose();
    super.dispose();
  }

  List<Listenable?> _watched = const [];
  Listenable? _merged;

  /// The team, the heat guard (its slot and the guard in it) and the
  /// connection, merged once per set: a new merge on every build would
  /// subscribe again to a team that is being turned off.
  Listenable _watching() {
    final heat = _heat;
    final parts = [
      widget.controller,
      TeamDispatchAttempts.of(widget.controller),
      heat,
      heat?.value,
      _connection,
      BuiltinTeamStartProgress.shared,
    ];
    var same = _merged != null && parts.length == _watched.length;
    for (var i = 0; same && i < parts.length; i++) {
      same = identical(parts[i], _watched[i]);
    }
    if (!same) {
      _watched = parts;
      _merged = Listenable.merge(parts);
    }
    return _merged!;
  }

  /// The heat guard's hold on this team, when it matches this profile, its
  /// phone host and city ([teamOverview]).
  ThermalTeamHold? _heatHold() {
    final hold = _heat?.value?.holds[widget.controller.profileId];
    if (hold == null) return null;
    final controller = widget.controller;
    return teamOverview(
      profileId: controller.profileId,
      host: controller.host,
      agents: null,
      isStale: true,
      heatHold: hold,
    ).heatHold;
  }

  void _teamChanged() {
    final changed = widget.onTeamChanged;
    if (changed != null) return changed();
    if (mounted) Navigator.of(context).popUntil((route) => route.isFirst);
  }

  void _toggleSearch() => setState(() {
    _searchOpen = !_searchOpen;
    if (!_searchOpen) {
      _search.clear();
      _query = '';
      _filter = TeamRunFilter.all;
    }
  });

  void _openRun(OrchestrationRun run) {
    final open = widget.onOpenRun;
    if (open != null) return open(run);
    unawaited(TeamConversation.open(context, widget.controller, runId: run.id));
  }

  void _openSettings() {
    unawaited(
      openTeamSettings(
        context,
        controller: widget.controller,
        connection: _connection,
        thermalGuard: _heat,
        probe: widget.probe,
        teamRuntime: widget.teamRuntime,
        now: widget.now,
        onOpenAgent: widget.onOpenAgent,
        onTeamChanged: _teamChanged,
      ),
    );
  }

  void _openGate(OrchestrationGate gate) =>
      showGateSheet(context, widget.controller, gate.id, now: () => _now);

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

  /// The task's conversation opens once the host took it (the planner's
  /// message or the direct task); back here, the status line says where a
  /// direct task stands ([TeamDispatchAttempts]).
  Future<void> _startRun() async {
    await TeamConversation.start(context, widget.controller);
  }

  @override
  Widget build(BuildContext context) {
    final l10n = _copy(context);
    return ListenableBuilder(
      listenable: _watching(),
      builder: (context, _) {
        final controller = widget.controller;
        final ready =
            controller.phase == OrchestrationPhase.ready &&
            controller.snapshot.hasData;
        final hold = _heatHold();
        // The one primary action (design standard §2): pinned below the
        // list, never over it, only where this phone can give a task.
        final canStart = controller.capabilities.controlMessage && ready;
        final searchable =
            ready &&
            teamVisibleRuns(controller.snapshot.runs).length >
                teamHomeSearchAfter;
        final line = ready ? _status(context, l10n, controller, hold) : null;
        return KitScreen(
          key: const ValueKey('team-home'),
          topBar: KitTopBar(
            title: l10n.teamUiHomeTitle,
            // Where the team runs, in one phrase; the address, the version
            // and the engine are behind Technical details. A pause for
            // heat and Android's stop are said as what they are, never as
            // the person's pause.
            subtitle: _subtitle(l10n, controller, hold),
            actions: [
              if (searchable || _searchOpen)
                KitAction(
                  key: const ValueKey('team-home-search-open'),
                  label: _searchOpen
                      ? l10n.teamUiHomeSearchClose
                      : l10n.teamUiHomeSearchHint,
                  icon: _searchOpen
                      ? AppIconography.close
                      : AppIconography.search,
                  onPressed: _toggleSearch,
                ),
              // The board (docs/design/team-board-2026-09-26.md).
              if (ready)
                KitAction(
                  key: const ValueKey('team-home-board'),
                  label: l10n.teamBoardOpenTooltip,
                  icon: AppIconography.kanban,
                  onPressed: () =>
                      openTeamBoard(context, controller, now: widget.now),
                ),
              // Setup and configuration: agents, how it runs, spend, turning
              // it off (Team settings), never on the work page.
              KitAction(
                key: const ValueKey('team-home-settings'),
                label: l10n.teamSettingsOpenTooltip,
                icon: AppIconography.settings,
                onPressed: _openSettings,
              ),
            ],
            menu: [
              KitMenuItem(
                key: const ValueKey('team-home-refresh'),
                label: l10n.teamUiRefresh,
                icon: AppIconography.retry,
                enabled: !_refreshing,
                onSelected: () => unawaited(_refresh()),
              ),
              if (widget.connection != null &&
                  teamMigrationOffered(widget.connection!))
                KitMenuItem(
                  key: const ValueKey('team-home-migration'),
                  label: l10n.teamMigrationMenu,
                  icon: AppIconography.swap,
                  onSelected: () =>
                      unawaited(openTeamMigration(context, widget.connection!)),
                ),
            ],
            menuKey: const ValueKey('team-home-more'),
          ),
          // A list on its own: centred at the list width on a PC.
          width: KitScreenWidth.list,
          search: _searchOpen && ready ? _searchField(l10n) : null,
          // Android stopped the phone's Termux team: said here, with
          // Start the team again (map: team-phone-onboarding-killed).
          status: line,
          // Stopped by Android: nothing loads until it starts again, and
          // the line above says so.
          loading: !_killed && (teamScreenLoading(controller) || _refreshing),
          loadingLabel: l10n.teamUiCardLoading,
          body: _body(context, hold),
          // Stopped by Android: giving a task needs the team running, so
          // the action stays in its place, off, and says why.
          bottom: _killed
              ? KitActionBlock(
                  primary: KitAction(
                    key: const ValueKey('team-home-start-run'),
                    label: l10n.teamUiStartRunFab,
                    icon: AppIconography.add,
                    onPressed: null,
                    disabledReason: l10n.teamHomeStoppedStartFirst,
                  ),
                )
              : canStart
              ? KitButton.primary(
                  key: const ValueKey('team-home-start-run'),
                  label: l10n.teamUiStartRunFab,
                  icon: AppIconography.add,
                  onPressed: _startRun,
                )
              : null,
        );
      },
    );
  }

  static String _filterLabel(AppLocalizations l10n, TeamRunFilter f) =>
      switch (f) {
        TeamRunFilter.active => l10n.teamUiHomeFilterActive,
        TeamRunFilter.blocked => l10n.teamUiHomeFilterBlocked,
        TeamRunFilter.completed => l10n.teamUiHomeFilterCompleted,
        TeamRunFilter.all => l10n.teamUiHomeFilterAll,
      };

  /// The search field and its one filter menu (never a row of chips); the
  /// chosen filter reads as a removable chip in words.
  KitSearchField _searchField(AppLocalizations l10n) => KitSearchField(
    label: l10n.teamUiHomeSearchHint,
    controller: _search,
    autofocus: true,
    onChanged: (query) => setState(() => _query = query),
    filters: [
      for (final f in TeamRunFilter.values)
        KitMenuItem(
          key: ValueKey('team-home-filter-${f.name}'),
          label: _filterLabel(l10n, f),
          checked: f == _filter,
          onSelected: () => setState(() => _filter = f),
        ),
    ],
    activeFilter: _filter == TeamRunFilter.all
        ? null
        : _filterLabel(l10n, _filter),
    onClearFilter: () => setState(() => _filter = TeamRunFilter.all),
    fieldKey: const ValueKey('team-home-search'),
    clearKey: const ValueKey('team-home-search-clear'),
    filterKey: const ValueKey('team-home-filter-menu'),
  );

  String? _age(AppLocalizations l10n, DateTime? at) => at == null
      ? null
      : relativeTimeLabel(at.millisecondsSinceEpoch, now: _now, l10n: l10n);

  static bool _isFinished(OrchestrationRun run) =>
      run.state == RunState.completed || run.state == RunState.cancelled;

  bool _matches(OrchestrationRun run, String query, Set<String> gated) {
    if (query.isNotEmpty && !run.title.toLowerCase().contains(query)) {
      return false;
    }
    return switch (_filter) {
      TeamRunFilter.active =>
        run.state == RunState.working || run.state == RunState.planning,
      TeamRunFilter.blocked =>
        run.state == RunState.blocked ||
            run.state == RunState.failed ||
            run.state == RunState.waiting ||
            gated.contains(run.id),
      TeamRunFilter.completed => _isFinished(run),
      TeamRunFilter.all => true,
    };
  }

  /// Where the team runs, then what holds it: Android's stop, the heat
  /// guard's pause, else [teamHostCondition] (the person's pause, not
  /// answering).
  String _subtitle(
    AppLocalizations l10n,
    OrchestrationController controller,
    ThermalTeamHold? hold,
  ) {
    final String? held;
    if (_killed) {
      held = l10n.teamHomeHostStopped;
    } else if (hold != null) {
      held = hold.serviceStopped
          ? l10n.teamHomeHostStoppedForHeat
          : l10n.teamHomeHostCooling;
    } else {
      return teamHostPhrase(
        l10n,
        controller,
        teamStarting:
            BuiltinTeam.isBuiltinConfig(controller.config) &&
            BuiltinTeamStartProgress.shared.running,
      );
    }
    return [teamHostPlace(l10n, controller), held].join(teamUsageSeparator);
  }

  /// "10:42" today, "Sep 27, 10:42" before: when the last-known team was
  /// read.
  String _asOf(BuildContext context, DateTime at) {
    final locale = Localizations.localeOf(context).toLanguageTag();
    final local = at.toLocal();
    final now = _now.toLocal();
    final today =
        local.year == now.year &&
        local.month == now.month &&
        local.day == now.day;
    return today
        ? DateFormat.jm(locale).format(local)
        : DateFormat.MMMd(locale).add_jm().format(local);
  }

  /// The team as last read, while Android has it stopped: its tasks and
  /// agents, dimmed, each with the state it had, under one "as of" label.
  /// Rows do not open: the tasks and agents answer again once the team
  /// runs (the reason is the page's pinned line).
  List<Widget> _lastKnown(
    BuildContext context,
    AppLocalizations l10n,
    TeamLastKnown lastKnown,
  ) {
    final tokens = KitTokens.of(context);
    final asOf = _asOf(context, lastKnown.asOf);
    final titles = teamAgentTitles(l10n, lastKnown.agents);
    return [
      if (lastKnown.runs.isNotEmpty) ...[
        SizedBox(height: tokens.sectionGap),
        KitRowGroup(
          key: const ValueKey('team-home-last-known-tasks'),
          label: l10n.teamHomeLastKnownTasks(asOf),
          children: [
            for (final run in lastKnown.runs)
              KitRow(
                key: ValueKey('team-home-last-known-run-${run.id}'),
                leading: KitTaskMark(state: teamRunMark(run, needsYou: false)),
                title: run.title,
                titleMaxLines: 2,
                enabled: false,
                disabledReason: teamRunStateWord(l10n, run.state),
              ),
          ],
        ),
      ],
      if (lastKnown.agents.isNotEmpty) ...[
        SizedBox(height: tokens.sectionGap),
        KitRowGroup(
          key: const ValueKey('team-home-last-known-agents'),
          label: l10n.teamHomeLastKnownAgents(asOf),
          children: [
            for (final agent in lastKnown.agents)
              KitRow(
                key: ValueKey('team-home-last-known-agent-${agent.id}'),
                leading: KitRow.icon(context, AppIconography.agent),
                title: titles[agent.id] ?? agent.name,
                enabled: false,
                disabledReason: teamAgentStateWord(l10n, agent.state),
              ),
          ],
        ),
      ],
    ];
  }

  Widget _body(BuildContext context, ThermalTeamHold? hold) {
    final l10n = _copy(context);
    final tokens = KitTokens.of(context);
    final controller = widget.controller;
    // Android stopped the phone's Termux team (map:
    // team-phone-onboarding-killed): said at the top of the page, where it
    // scrolls (at large text it is taller than a fixed header allows).
    final killed = TeamPhoneKilledNotice.appliesTo(controller.config)
        ? TeamPhoneKilledNotice(
            key: _killedKey,
            controller: controller,
            runtime: widget.teamRuntime,
            onKilledChanged: (killed) {
              if (mounted) setState(() => _killed = killed);
            },
          )
        : null;
    // Stopped by Android: that line is the page's state (with Start the
    // team again), and the team as last read stays under it, dimmed and
    // "as of", so the person still sees what it was doing. Nothing on it
    // acts: it needs the team running. No "not answering, the app keeps
    // trying" under it.
    if (killed != null && _killed) {
      final lastKnown = controller.lastKnown;
      return ListView(
        key: const ValueKey('team-home-stopped'),
        padding: EdgeInsetsDirectional.only(
          bottom: KitScreen.endPadding(context),
        ),
        children: [
          killed,
          if (lastKnown != null) ..._lastKnown(context, l10n, lastKnown),
        ],
      );
    }
    if (teamScreenState(
          context,
          controller: controller,
          keyPrefix: 'team-home',
          onRetry: _refreshing ? null : _refresh,
        )
        case final state?) {
      if (killed == null) return state;
      return Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          killed,
          Expanded(child: state),
        ],
      );
    }

    final snapshot = controller.snapshot;
    final gated = teamGatedRuns(snapshot);
    final gates = teamOpenGates(controller);
    // The host's upkeep (patrols, chores) is never counted or listed here.
    final runs = teamVisibleRuns(snapshot.runs);
    final ordered = [...runs]..sort((a, b) => teamCompareRuns(a, b, gated));
    // Every question is its task's row (owner rule 2026-09-27, one list):
    // "Needs you · <question> · 2 min ago", opening the Gate sheet; a
    // question whose task is not listed (or a second one on the same task)
    // is a row of its own at the top of the list. Nothing shows twice.
    final gateOfRun = <String, OrchestrationGate>{};
    final looseGates = <OrchestrationGate>[];
    final listedIds = {for (final run in runs) run.id};
    for (final gate in gates) {
      final id = teamGateRun(snapshot, gate)?.id;
      if (id != null && listedIds.contains(id) && !gateOfRun.containsKey(id)) {
        gateOfRun[id] = gate;
      } else {
        looseGates.add(gate);
      }
    }
    final query = (_searchOpen ? _query : '').trim().toLowerCase();
    final filtering =
        _searchOpen && (query.isNotEmpty || _filter != TeamRunFilter.all);
    final visible = [
      for (final run in ordered)
        if (!filtering || _matches(run, query, gated)) run,
    ];
    final listed = visible;
    final loose =
        !filtering ||
            (_filter != TeamRunFilter.active &&
                _filter != TeamRunFilter.completed &&
                query.isEmpty)
        ? looseGates
        : const <OrchestrationGate>[];
    final open = [
      for (final run in listed)
        if (!_isFinished(run)) run,
    ];
    final done = [
      for (final run in listed)
        if (_isFinished(run)) run,
    ];
    final doneShown = _doneExpanded || filtering
        ? done
        : done.take(teamHomeDoneShown).toList();
    final hiddenDone = done.length - doneShown.length;
    final rest = teamRest(snapshot.agents, config: controller.config);

    Widget row(OrchestrationRun run) {
      final gate = gateOfRun[run.id];
      final needsYou = gate != null || gated.contains(run.id);
      final record = gate == null ? null : teamGateMutation(controller, gate);
      final InlineSpan line;
      if (gate != null) {
        // The row is the question: "Needs you · <question> · 2 min ago",
        // with the answer's receipt as a word after "Needs you" while the
        // host has not confirmed it ("Needs you · Not confirmed yet · …").
        line = teamGateRowLine(context, [
          l10n.teamUiHomeRunNeedsYou,
          gate.title,
          ?_age(l10n, gate.createdAt),
        ], record: record);
      } else {
        final task = teamTaskLine(
          l10n,
          run,
          snapshot.work,
          needsYou: needsYou,
          now: _now,
          cycleOf: controller.cycleFor,
          explainWait: true,
          checkEvery: teamCheckInterval(controller),
          paused: rest == TeamRest.paused,
        );
        // What happens next, once, on the working task's own row (the
        // team's Now line no longer repeats the task above the list).
        final reviewNext =
            !needsYou &&
            run.state == RunState.working &&
            teamRunStage(run, snapshot.work, cycleOf: controller.cycleFor) ==
                TeamStage.working;
        line = TextSpan(
          text: reviewNext
              ? [task, l10n.teamUiHomeRunReviewNext].join(teamUsageSeparator)
              : task,
        );
      }
      return KitRow(
        key: ValueKey('team-home-run-${run.id}'),
        leading: KitTaskMark(
          state: teamRunMark(run, needsYou: needsYou, work: snapshot.work),
        ),
        // A task's title is the person's own objective: two lines.
        title: run.title,
        titleMaxLines: 2,
        supporting: line,
        supportingMaxLines: 2,
        supportingKey: ValueKey('team-home-run-state-${run.id}'),
        // A question opens the Gate sheet; any other task its conversation.
        onTap: gate != null ? () => _openGate(gate) : () => _openRun(run),
      );
    }

    // The first section sits close under the top bar; the rest keep the
    // list's section spacing.
    final gap = SizedBox(height: tokens.sectionGap);
    final planning = teamPendingPlanning(controller, _now);
    final planned = filtering ? const <TeamPlanningRequest>[] : planning;

    Widget planningRow(TeamPlanningRequest request) {
      final activity = switch (request.status) {
        TeamPlanningStatus.refused => TeamNowActivity.refused,
        TeamPlanningStatus.unconfirmed => TeamNowActivity.unconfirmed,
        _ => TeamNowActivity.planning,
      };
      final waited = _now.difference(request.sentAt);
      return KitRow(
        key: ValueKey('team-home-planning-${request.key}'),
        leading: KitTaskMark(
          state: switch (activity) {
            TeamNowActivity.refused => KitTaskState.failed,
            TeamNowActivity.unconfirmed => KitTaskState.waiting,
            _ => KitTaskState.working,
          },
        ),
        title: request.objective,
        titleMaxLines: 2,
        supporting: TextSpan(
          text: teamNowActivityLine(
            l10n,
            activity,
            elapsed: waited.isNegative ? Duration.zero : waited,
          ),
        ),
        supportingMaxLines: 2,
        supportingKey: ValueKey('team-home-planning-${request.key}-state'),
        onTap: () => unawaited(
          TeamConversation.openPlanning(
            context,
            controller,
            request,
            now: widget.now,
          ),
        ),
      );
    }

    final children = <Widget>[
      ?killed,
      SizedBox(height: tokens.space3),
      // The tasks: one panel, most urgent first, what finished last.
      if (runs.isEmpty && planning.isEmpty && loose.isEmpty)
        KitStateView(
          key: const ValueKey('team-home-runs-empty'),
          size: KitStateSize.inline,
          liveRegion: false,
          icon: AppIconography.checklist,
          // The team gathered at an empty board, its one slot waiting.
          illustration: const TeamBoardScene(),
          // Room for the board and its three agents to read (88 dp, the
          // inline default, cramped them).
          illustrationWidth: 168,
          title: l10n.teamUiCardEmptyTitle,
          // One sentence that teaches; the pinned button is the action, so
          // the state never repeats it.
          body: controller.capabilities.controlMessage
              ? l10n.emptyTeachTeamRunsMessage
              : l10n.teamUiCardEmptyHint,
        )
      // A task still being planned is a row of its own: the list is not
      // empty, so no "No tasks match" over it.
      else if (visible.isEmpty && planned.isEmpty && loose.isEmpty)
        KitStateView(
          key: const ValueKey('team-home-runs-empty-filtered'),
          size: KitStateSize.inline,
          liveRegion: false,
          icon: AppIconography.filterOff,
          title: l10n.teamUiHomeRunsEmptyFiltered,
          body: l10n.teamUiHomeRunsEmptyHint,
        ),
      if (visible.isEmpty && planned.isEmpty && loose.isEmpty) gap,
      if (listed.isNotEmpty || loose.isNotEmpty || planned.isNotEmpty) ...[
        KitRowGroup(
          key: const ValueKey('team-home-tasks'),
          // No heading: the page is the task list and its title says so.
          children: [
            for (final gate in loose)
              TeamGateRow(
                key: ValueKey('team-home-gate-${gate.id}'),
                receiptKey: ValueKey('team-home-gate-${gate.id}-receipt'),
                controller: controller,
                gate: gate,
                now: _now,
                onTap: () => _openGate(gate),
              ),
            // A task just given, before the planner lists it: its row
            // opens its conversation, whose Now line says where it stands
            // (the planning card this replaced is gone, slice-P5.1).
            for (final request in planned) planningRow(request),
            for (final run in open) row(run),
            for (final run in doneShown) row(run),
            if (hiddenDone > 0)
              KitRow(
                key: const ValueKey('team-home-completed-more'),
                leading: KitRow.icon(context, AppIconography.chevronDown),
                title: l10n.teamUiHomeDoneMore(hiddenDone),
                onTap: () => setState(() => _doneExpanded = true),
              ),
          ],
        ),
        gap,
      ],
      // The host's projects: each one's own page holds what can be done
      // with it (pause, resume, delete, its scheduled jobs).
      if (snapshot.projects.isNotEmpty &&
          (controller.capabilities.controlProject ||
              controller.capabilities.controlProjectRemove ||
              controller.capabilities.scheduledJobs)) ...[
        KitSectionLabel(l10n.teamHomeProjectsTitle),
        KitRowGroup(
          key: const ValueKey('team-home-projects'),
          children: [
            for (final project in snapshot.projects)
              KitRow(
                key: ValueKey('team-home-project-${project.id}'),
                leading: KitRow.icon(context, AppIconography.projects),
                title: project.name,
                supporting: TextSpan(
                  text: project.suspended
                      ? l10n.teamRigStatePaused
                      : l10n.teamRigStateActive,
                ),
                trailing: const KitChevron(),
                onTap: () =>
                    unawaited(openTeamRig(context, controller, project.id)),
              ),
          ],
        ),
        gap,
      ],
    ];

    return KeyedSubtree(
      key: const ValueKey('team-home-data'),
      child: KitRefresh(
        key: const ValueKey('team-home-pull'),
        onRefresh: _refresh,
        child: ListView(
          key: const ValueKey('team-home-runs'),
          physics: const AlwaysScrollableScrollPhysics(),
          padding: EdgeInsetsDirectional.only(
            bottom: KitScreen.endPadding(context),
          ),
          children: children,
        ),
      ),
    );
  }

  /// The page's one status line (design standard §5, the status slot):
  /// old data, then the heat guard's hold, then a direct task's refusal or
  /// unconfirmed step, then the team's Now, then the direct task's progress.
  KitStatus? _status(
    BuildContext context,
    AppLocalizations l10n,
    OrchestrationController controller,
    ThermalTeamHold? hold,
  ) {
    final stale = teamStatusLine(
      context,
      controller: controller,
      keyPrefix: 'team-home',
      onRetry: _refreshing ? null : _refresh,
    );
    final attempt = TeamDispatchAttempts.of(controller).latest;
    final dispatch = attempt == null
        ? null
        : _dispatchStatus(context, l10n, controller, attempt);
    // A refusal or a step nobody confirmed comes before the team's Now;
    // progress comes after it.
    final problem = switch (attempt?.phase) {
      TeamDispatchPhase.assignRefused ||
      TeamDispatchPhase.createUnconfirmed ||
      TeamDispatchPhase.dispatchUnconfirmed ||
      TeamDispatchPhase.unknown => dispatch,
      _ => null,
    };
    return KitStatus.highest([
      stale,
      // A pause for heat is the guard's, not the person's: it says why and
      // that the team carries on by itself, and offers no Resume (waking
      // the agents would heat the phone again).
      if (hold != null) _heatStatus(context, l10n, hold),
      problem,
      teamNowStatus(
        context,
        controller: controller,
        now: _now,
        keyPrefix: 'team-home-now',
      ),
      if (problem == null) dispatch,
    ]);
  }

  /// The heat guard's line: paused (or stopped) since when, and that the
  /// team carries on by itself once the phone has cooled. Neutral: nothing
  /// here waits on the person (LOOK-4).
  KitStatus _heatStatus(
    BuildContext context,
    AppLocalizations l10n,
    ThermalTeamHold hold,
  ) {
    final at = teamClockLabel(context, hold.since);
    return KitStatus(
      kind: KitStatusKind.heat,
      id: 'team-home:heat',
      key: const ValueKey('team-home-heat'),
      icon: hold.serviceStopped
          ? AppIconography.stopCircle
          : AppIconography.pause,
      tone: AppStatusTone.neutral,
      message: hold.serviceStopped
          ? l10n.teamHomeHeatStoppedLine(at)
          : l10n.teamHomeHeatPausedLine(at),
    );
  }

  /// Where the task just given stands (P6.3,
  /// docs/design/team-immediate-dispatch-contract.md): only what the host
  /// confirmed. The host's own words are never the line; they wait behind
  /// Technical details, redacted. Null when there is nothing to say here
  /// (the sheet said it, or nothing was sent).
  KitStatus? _dispatchStatus(
    BuildContext context,
    AppLocalizations l10n,
    OrchestrationController controller,
    TeamDispatchController attempt,
  ) {
    final attempts = TeamDispatchAttempts.of(controller);
    final checkAgain = KitAction(
      key: const ValueKey('team-home-dispatch-check'),
      label: l10n.teamDispatchCheckAgain,
      icon: AppIconography.retry,
      onPressed: _refreshing ? null : () => unawaited(_refresh()),
    );
    final record = attempt.problemRecord;
    final hostWords = record?.receipt?.message?.trim() ?? '';
    final taskId = attempt.workId;
    final details = record == null || (hostWords.isEmpty && taskId == null)
        ? const <KitAction>[]
        : [
            KitAction(
              key: const ValueKey('team-home-dispatch-details'),
              label: l10n.teamUiTechnicalDetails,
              icon: AppIconography.info,
              onPressed: () => unawaited(
                showKitTechnicalDetails(
                  context,
                  title: l10n.teamUiTechnicalDetails,
                  sheetKey: const ValueKey('team-home-dispatch-details-sheet'),
                  values: [
                    if (taskId != null)
                      KitTechnicalValue(l10n.teamDispatchTaskId, taskId),
                  ],
                  notes: [if (hostWords.isNotEmpty) l10n.teamDispatchHostWords],
                  // Redacted by the kit before it is shown or copied.
                  text: hostWords,
                ),
              ),
            ),
          ];
    KitStatus line({
      required String message,
      required AppStatusTone tone,
      required IconData icon,
      String? supporting,
      String? next,
      KitAction? action,
      List<KitAction> more = const [],
      DateTime? since,
      bool dismissible = true,
    }) => KitStatus(
      kind: KitStatusKind.work,
      id: 'team-home:dispatch:${attempt.phase.name}',
      key: ValueKey('team-home-dispatch-${attempt.phase.name}'),
      messageKey: const ValueKey('team-home-dispatch-text'),
      icon: icon,
      tone: tone,
      message: message,
      supporting: supporting,
      next: next,
      action: action,
      more: more,
      since: since,
      // Closing the line changes nothing on the host.
      onDismiss: dismissible ? attempts.dismiss : null,
    );
    return switch (attempt.phase) {
      TeamDispatchPhase.creating => line(
        message: l10n.teamDispatchCreating,
        tone: AppStatusTone.progress,
        icon: AppIconography.waiting,
        since: attempt.startedAt,
        dismissible: false,
      ),
      TeamDispatchPhase.sending => line(
        message: l10n.teamDispatchSending,
        tone: AppStatusTone.progress,
        icon: AppIconography.send,
        since: attempt.startedAt,
        dismissible: false,
      ),
      TeamDispatchPhase.awaitingWorker => line(
        message: l10n.teamDispatchAwaitingWorker,
        tone: AppStatusTone.progress,
        icon: AppIconography.waiting,
        // An expectation, never a deadline or a measured figure.
        next: [
          l10n.teamNowNextWorker,
          l10n.teamNowUsuallyWithin(
            KitSince.durationWords(l10n, teamWorkerStartUsual),
          ),
        ].join(' · '),
      ),
      TeamDispatchPhase.workerObserved => line(
        message: l10n.teamDispatchWorkerStarted,
        tone: AppStatusTone.ok,
        icon: AppIconography.agent,
      ),
      TeamDispatchPhase.assignRefused => line(
        message: l10n.teamDispatchAssignRefused,
        tone: AppStatusTone.failure,
        icon: AppIconography.error,
        supporting: l10n.teamDispatchAssignRefusedHint,
        action: checkAgain,
        more: details,
      ),
      TeamDispatchPhase.createUnconfirmed => line(
        message: l10n.teamDispatchCreateUnconfirmed,
        tone: AppStatusTone.neutral,
        icon: AppIconography.warning,
        supporting: l10n.teamDispatchCheckBoard,
        action: checkAgain,
        more: details,
      ),
      TeamDispatchPhase.dispatchUnconfirmed => line(
        message: l10n.teamDispatchDispatchUnconfirmed,
        tone: AppStatusTone.neutral,
        icon: AppIconography.warning,
        supporting: l10n.teamDispatchCheckBoard,
        action: checkAgain,
        more: details,
      ),
      TeamDispatchPhase.unknown => line(
        message: l10n.teamDispatchUnknown,
        tone: AppStatusTone.neutral,
        icon: AppIconography.cloudOff,
        action: checkAgain,
      ),
      TeamDispatchPhase.idle ||
      TeamDispatchPhase.unavailable ||
      TeamDispatchPhase.invalidInput ||
      TeamDispatchPhase.createRefused => null,
    };
  }
}
