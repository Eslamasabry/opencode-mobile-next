import 'dart:async';

import 'package:flutter/material.dart';

import '../../api/models.dart';
import '../../api/product_repository.dart';
import '../../api2/models.dart' show Api2FormInfo;
import '../../domain/completion_digest.dart';
import '../../domain/orchestration_gateway.dart';
import '../../domain/return_brief.dart';
import '../../domain/while_away.dart';
import '../../l10n/app_localizations.dart';
import '../../state/automatic_activity.dart';
import '../../state/connection.dart';
import '../../state/orchestration.dart';
import '../app_iconography.dart';
import '../navigation/chat_route.dart' show ChatRouteArguments;
import '../kit/kit.dart';
import '../kit/scenes/states_scenes.dart';
import '../permission_presentation.dart';
import '../widgets/attention_feed_rows.dart';
import '../widgets/completion_digest.dart';
import '../widgets/product_states.dart' show productErrorText;
import '../widgets/relative_time.dart';
import '../widgets/request_routes.dart';
import '../widgets/session_title.dart';
import '../widgets/team_receipt.dart';
import '../widgets/team_vocabulary.dart';
import 'chat/form_flow.dart';
import 'chat/permission_sheet.dart';
import 'profile_monitor_screen.dart';
import 'run_result_screen.dart';
import 'team/agent_screen.dart';
import 'team/gate_sheet.dart';
import 'team/project_destination.dart';

part 'activity/activity_rows.dart';
part 'activity/activity_pending.dart';
part 'activity/activity_tiles.dart';
part 'activity/activity_details.dart';
part 'activity/activity_question_form.dart';

/// Inbox: the single cross-session control centre (audit §3, §8; target IA
/// "Dock tab 2").
///
/// It replaces the former Mission Control and Pending requests screens, which
/// showed the same pending count behind two mental models. One destination,
/// one badge, three sections:
///
/// 1. **Needs attention** — permissions, questions, and v2 forms, each row
///    opening the *exact* resolver (the same permission sheet and form flow
///    chat uses), never merely a link to the related chat. A permission can
///    also be allowed once from its row. When the connected server runs the
///    AI Team plugin, its gates join the same list in the BRD §47 order —
///    decision requested, run failed, then the app's own permissions, then
///    review ready, gate beads and blocked agents — each opening the
///    read-only Gate sheet (02-ux §6).
/// 2. **Running** — sessions busy right now, with their subagent counts;
///    while the connection is down they read "Last seen running" instead of
///    a live mark.
/// 3. **While you were away** — what finished (completion digests, on
///    demand) and every automatic act the app did (a reconnect, a request
///    allowed by itself, a queued message sent, a heat pause), newest first
///    in the same list. An act names what it was done to, says what was
///    done and when in one line (with Undo where the act has one) and opens
///    that thing's page. None of it is ever Needs you.
///
/// From an expanded window the Inbox is two panes (KitScreen.twoPane): the
/// list on the start side, and the picked request answered in the detail
/// pane instead of a sheet.
///
/// Every row is server truth the controller already holds; nothing here is
/// estimated. Session history and cross-project discovery live in Work and
/// the all-sessions finder, not here: an empty inbox reads as success.
class ActivityScreen extends StatefulWidget {
  final ConnectionController controller;

  /// A notification tap can name the session whose question should open
  /// immediately, so the alert lands on the answer rather than a list.
  final String? initialQuestionSessionID;

  /// An AI Team notification or link (TEAM-203) names the gate whose sheet
  /// should open as soon as the plugin has data; ids only, and nothing is
  /// sent by opening it.
  final String? initialTeamGateId;

  /// True when Activity is hosted as a primary navigation destination, which
  /// already supplies the top bar. Pushed routes (deep links, notifications)
  /// build their own page frame.
  final bool embedded;

  /// The clock behind the AI Team rows' ages; tests pin it.
  final DateTime Function()? now;

  const ActivityScreen({
    super.key,
    required this.controller,
    this.initialQuestionSessionID,
    this.initialTeamGateId,
    this.embedded = false,
    this.now,
  });

  @override
  State<ActivityScreen> createState() => _ActivityScreenState();
}

/// The request picked for the detail pane (expanded windows only).
enum _PickKind { permission, question, form }

typedef _Pick = ({_PickKind kind, String id});

class _ActivityScreenState extends State<ActivityScreen> {
  /// The embedded tab is built by the shell's IndexedStack at launch and
  /// stays alive; it takes its truth from the controller's own hydration and
  /// live events, and reconciles on pull-to-refresh. Only a pushed Activity —
  /// a deep link or a notification tap — pays for an entry refresh, exactly
  /// as the former Requests screen did.
  late bool _loading = !widget.embedded;
  bool _refreshing = false;
  String? _error;
  bool _initialQuestionScheduled = false;
  bool _initialQuestionHandled = false;
  bool _initialGateScheduled = false;
  bool _initialGateHandled = false;
  Object? _digestScope;
  final Set<(String, int)> _expandedDigests = {};
  final Set<(String, int)> _dismissedDigests = {};

  /// Automatic acts hidden by a Dismiss whose Undo window is still open.
  final Set<String> _dismissedActs = {};

  /// How an Undo of an automatic act ended, until the row goes.
  final Map<String, AutomaticUndoResult> _undoResults = {};

  /// Extensions in the part files cannot call [setState] themselves.
  void _set(VoidCallback fn) => setState(fn);
  _Pick? _picked;

  Object get _currentDigestScope => (
    widget.controller,
    widget.controller.profile?.id,
    widget.controller.connectionRevision,
    widget.controller.locationRevision,
    widget.controller.directory,
    widget.controller.workspace,
  );

  void _clearDigestScope() {
    if (_digestScope == _currentDigestScope) return;
    _digestScope = _currentDigestScope;
    _expandedDigests.clear();
    _dismissedDigests.clear();
    _dismissedActs.clear();
    _undoResults.clear();
    _picked = null;
  }

  @override
  void didUpdateWidget(covariant ActivityScreen oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.controller != widget.controller) {
      oldWidget.controller.removeListener(_changed);
      widget.controller.addListener(_changed);
    }
    _clearDigestScope();
  }

  /// What was done, in the person's language (the saved summary is only
  /// the thing's name).
  static String _actWords(AutomaticAct act, AppLocalizations l10n) =>
      switch (act.kind) {
        AutomaticActKind.reconnect => l10n.whileAwayActReconnected,
        AutomaticActKind.restart => l10n.whileAwayActRestarted,
        AutomaticActKind.heatPause => l10n.whileAwayActHeatPaused,
        AutomaticActKind.heatStop => l10n.whileAwayActHeatStopped,
        AutomaticActKind.heatResume => l10n.whileAwayActHeatResumed,
        AutomaticActKind.update => l10n.whileAwayActUpdated,
        AutomaticActKind.permissionApproval => l10n.whileAwayActAllowed,
        AutomaticActKind.queuedSend => l10n.whileAwayActQueuedSent,
        AutomaticActKind.other => l10n.whileAwayActOther,
      };

  @override
  void initState() {
    super.initState();
    widget.controller.addListener(_changed);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      if (!widget.embedded) _refreshPending();
      _scheduleInitialQuestion();
      _scheduleInitialGate();
    });
  }

  @override
  void dispose() {
    widget.controller.removeListener(_changed);
    super.dispose();
  }

  void _changed() {
    if (!mounted) return;
    setState(() {});
    _scheduleInitialQuestion();
    _scheduleInitialGate();
  }

  static String _place(Session session) {
    final directory = session.directory?.trim() ?? '';
    if (directory.isEmpty) return '';
    final parts = directory
        .split('/')
        .where((part) => part.isNotEmpty)
        .toList();
    return parts.isEmpty ? directory : parts.last;
  }

  @override
  Widget build(BuildContext context) {
    _clearDigestScope();
    final l10n = _l10n(context);
    final controller = widget.controller;
    // Permissions, questions and forms carry no timestamp; the controller
    // keeps them in arrival order, which is oldest first.
    final permissions = controller.awaitingPermissions.toList();
    final questions = controller.questions.values.toList()
      ..sort((a, b) {
        final selected = widget.initialQuestionSessionID;
        if (selected == null) return 0;
        final aSelected = a.sessionID == selected;
        final bSelected = b.sessionID == selected;
        return aSelected == bSelected ? 0 : (aSelected ? -1 : 1);
      });
    // §7 rule 5: forms are v2-only, so a v1 connection never lists them even
    // if a stale entry survived a server switch.
    final formsAvailable = controller.capabilities.forms;
    final sessionForms = !formsAvailable
        ? const <Api2FormInfo>[]
        : controller.forms.values
              .where((form) => form.sessionID != 'global')
              .toList();
    // Global (MCP elicitation) forms have no session to open, so they keep
    // their own subsection rather than pretending to map to a chat.
    final globalForms = !formsAvailable
        ? const <Api2FormInfo>[]
        : controller.forms.values
              .where((form) => form.sessionID == 'global')
              .toList();

    final running = controller.busySessions
        .map((id) => controller.sessionsById[id] ?? Session(id: id, title: id))
        .where((session) => session.parentID == null)
        .toList();
    // AI Team items of the connected server only: the plugin controller is
    // built for the connected profile and disposed on disconnect, so a gate
    // from another server never reaches this list.
    final team = _teamRows(controller.orchestration);

    final loading =
        _loading ||
        controller.permissionsLoading ||
        controller.questionsLoading ||
        (formsAvailable && controller.formsLoading);
    final error =
        _error ??
        controller.permissionsError ??
        controller.questionsError ??
        (formsAvailable ? controller.formsError : null);
    final attentionCount =
        permissions.length +
        questions.length +
        sessionForms.length +
        globalForms.length +
        team.length;
    final empty =
        attentionCount == 0 &&
        running.isEmpty &&
        controller.unifiedAttentionCount == 0;
    final hasCheckIns =
        !controller.isIsolated &&
        controller.store.profiles.any((profile) {
          if (!controller.isProfileReadable(profile.id)) return false;
          final monitor = controller.profileMonitor;
          final snapshot = monitor.snapshotFor(profile.id);
          return snapshot.isCurrent &&
              snapshot.dueCheckIns(monitor.rulesFor(profile.id)).isNotEmpty;
        });
    // Running work is live only while the connection is: without it the
    // last known rows stay, marked as last seen, never a turning spinner.
    final live = controller.isConnected;
    final picked = _picked;

    bool isPicked(_PickKind kind, String id) =>
        picked != null && picked.kind == kind && picked.id == id;

    final attentionRows = <Widget>[
      for (final row in team)
        if (row.rank < teamActivityPermissionRank) row.widget,
      for (final permission in permissions)
        ActivityPermissionTile(
          key: ValueKey('activity-permission-${permission.id}'),
          permission: permission,
          controller: controller,
          selected: isPicked(_PickKind.permission, permission.id),
          // P4.2a: on a phone the row lands on its card in the
          // conversation, never on a sheet over this list.
          onOpen: _opener(
            (kind: _PickKind.permission, id: permission.id),
            () =>
                _openChat(permission.sessionID, landOnRequestID: permission.id),
          ),
        ),
      for (final question in questions)
        ActivityQuestionTile(
          key: ValueKey('activity-question-${question.id}'),
          question: question,
          controller: controller,
          selected: isPicked(_PickKind.question, question.id),
          onOpen: _opener((
            kind: _PickKind.question,
            id: question.id,
          ), () => _openChat(question.sessionID, landOnRequestID: question.id)),
        ),
      for (final form in sessionForms)
        ActivityFormTile(
          key: ValueKey('activity-form-${form.id}'),
          form: form,
          controller: controller,
          selected: isPicked(_PickKind.form, form.id),
          onOpen: _opener((
            kind: _PickKind.form,
            id: form.id,
          ), () => _openChat(form.sessionID, landOnRequestID: form.id)),
        ),
      for (final row in team)
        if (row.rank > teamActivityPermissionRank) row.widget,
    ];

    // The server's own forms (MCP elicitation) have no conversation to
    // open; they follow the conversations' requests in the same list.
    final globalFormRows = [
      for (final form in globalForms)
        ActivityFormTile(
          key: ValueKey('activity-global-form-${form.id}'),
          form: form,
          controller: controller,
        ),
    ];
    // What every saved server waits on, from the one attention feed
    // (slice-P4.2b): another server's requests, gates and failures, and a
    // failure here, each naming its server. Check-in reminders are not
    // requests; they stay the monitor's own rows.
    final feed = controller.attentionFeed;
    final now = (widget.now ?? DateTime.now)();
    final feedRows = [
      for (final item in inboxFeedItems(controller, feed))
        AttentionFeedRow(
          key: ValueKey(('attention-row', item.identity)),
          controller: controller,
          item: item,
          now: now,
          onOpenConversation: (sessionID, landing) => _openChat(
            sessionID,
            landOnRequestID: landing.landOnRequestID,
            landOnFailure: landing.landOnFailure,
          ),
        ),
    ];
    // Other servers this list cannot speak for, and the way forward.
    final coverageRows = inboxCoverageRows(context, controller, feed, now: now);
    final monitor = ProfileMonitorInbox.rowsFor(controller);
    final runningRows = [
      for (final session in running)
        _SessionRow(
          key: ValueKey('activity-running-${session.id}'),
          session: session,
          live: live,
          subagents: _subagentCount(session.id),
          detail:
              controller.sessionDetailsErrors[session.id] ?? _place(session),
          onTap: () => _openChat(session.id),
        ),
    ];
    // While you were away: what finished and what the app did by itself,
    // newest first, together (P6.2).
    final awayRows = [..._finishedRows(), ..._automaticRows()]
      ..sort((a, b) => b.at.compareTo(a.at));
    final finishedRows = [for (final entry in awayRows) entry.row];
    final history = controller.automaticActivity;
    final historyTrouble = history == null
        ? null
        : history.corruptHistory
        ? l10n.whileAwayHistoryUnreadable
        : history.persistenceFailed
        ? l10n.whileAwayHistoryUnsaved
        : null;
    final historyNotice = historyTrouble == null
        ? null
        : _Section(
            child: Padding(
              padding: EdgeInsetsDirectional.symmetric(
                horizontal: KitTokens.of(context).gutter,
              ),
              child: KitNotice(
                key: const ValueKey('activity-auto-history-notice'),
                message: historyTrouble,
              ),
            ),
          );
    // The one list (owner rule R1, 2026-09-27): no headed state sections.
    // Most urgent first: what waits on the person here, the server's own
    // forms, what every saved server waits on or failed at (the feed),
    // running work (and check-ins due on it), what finished, newest first,
    // then the servers this list cannot speak for. Each row's mark and its
    // word ("Needs you", "Working", "Finished") carry the meaning. A row
    // that arrives while the Inbox is open unfolds in, one answered (here or
    // on another device) folds away where it was (design standard §10).
    final rows = [
      ...attentionRows,
      ...globalFormRows,
      ...feedRows,
      ...runningRows,
      ...monitor.checkIns,
      ...finishedRows,
      ...coverageRows,
    ];
    Widget oneList(List<Widget> rows) => _Section(
      child: KitRowGroup(
        key: const ValueKey('activity-list'),
        children: [
          _DividedRows(key: const ValueKey('activity-rows'), rows: rows),
        ],
      ),
    );

    final Widget list;
    if (loading && empty) {
      list = ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        children: const [KitSkeletonRows()],
      );
    } else if (error != null && empty) {
      list = ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: KitScreen.padding(context),
        children: [
          KitStateView.error(
            key: const ValueKey('activity-error'),
            size: KitStateSize.inline,
            title: l10n.e7WorkspaceRefreshFailed,
            body: error,
            retry: KitAction(
              label: l10n.isolatedTaskRetryOpen,
              onPressed: _refresh,
            ),
          ),
        ],
      );
    } else if (empty) {
      final quiet = [...monitor.checkIns, ...finishedRows, ...coverageRows];
      list = ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: EdgeInsetsDirectional.only(
          bottom: KitScreen.endPadding(context),
        ),
        children: [
          if (!hasCheckIns)
            _ActivityStatus(
              known:
                  controller.isConnected &&
                  controller.unknownAttentionProfileCount == 0,
              // In the shell the connection line above says the server is
              // away and holds the one Reconnect (R3); a pushed Inbox has no
              // such line, so it keeps its own.
              offline: !controller.isConnected,
              onRefresh: widget.embedded && !controller.isConnected
                  ? null
                  : _refresh,
            ),
          if (quiet.isNotEmpty) oneList(quiet),
          ?historyNotice,
        ],
      );
    } else {
      list = KitScrollArea(
        builder: (scrollController) => ListView(
          controller: scrollController,
          physics: const AlwaysScrollableScrollPhysics(),
          padding: EdgeInsetsDirectional.only(
            bottom: KitScreen.endPadding(context),
          ),
          children: [
            if (error != null)
              _Section(
                child: Padding(
                  padding: EdgeInsetsDirectional.symmetric(
                    horizontal: KitTokens.of(context).gutter,
                  ),
                  child: KitNotice.error(
                    key: const ValueKey('activity-refresh-failed'),
                    title: l10n.e7WorkspaceRefreshFailed,
                    message: error,
                    retry: KitAction(
                      label: l10n.isolatedTaskRetryOpen,
                      onPressed: _refresh,
                    ),
                  ),
                ),
              ),
            if (rows.isNotEmpty) oneList(rows),
            ?historyNotice,
          ],
        ),
      );
    }

    // Pull to refresh is the one refresh (R4): no second one in the bar.
    final body = KitRefresh(onRefresh: _refresh, child: list);
    final topBar = widget.embedded
        ? null
        : KitTopBar(title: l10n.shellTabInbox);
    return KitScreen.twoPane(
      topBar: topBar,
      loading: loading && !empty,
      loadingLabel: l10n.activityLoading,
      listPaneKey: const ValueKey('activity-list-pane'),
      detailPaneKey: const ValueKey('activity-detail-pane'),
      list: body,
      detail: _detail(),
      emptyDetail: KitStateView(
        key: const ValueKey('activity-detail-empty'),
        icon: AppIconography.inbox,
        title: attentionCount > 0
            ? l10n.activityPickRequest
            : l10n.activityClearHere,
        body: attentionCount > 0 ? l10n.activityPickRequestDetail : null,
      ),
    );
  }
}
