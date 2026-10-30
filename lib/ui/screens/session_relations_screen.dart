import 'dart:async';

import 'package:flutter/material.dart';

import '../../domain/product_repository.dart';
import '../../l10n/app_localizations.dart';
import '../../state/connection.dart';
import '../app_theme.dart';
import '../early_l10n.dart';
import '../kit/kit.dart';
import '../widgets/product_states.dart' show productErrorText;
import '../widgets/relative_time.dart';
import '../widgets/session_handoff.dart';
import '../widgets/session_title.dart';

/// The conversation a subagent was started from and the subagents it
/// started (map page session-relations): one list ordered by urgency, each
/// row saying its state in words, opening its transcript on tap.
class SessionRelationsScreen extends StatefulWidget {
  final ConnectionController controller;
  final String sessionID;

  const SessionRelationsScreen({
    super.key,
    required this.controller,
    required this.sessionID,
  });

  @override
  State<SessionRelationsScreen> createState() => _SessionRelationsScreenState();
}

class _SessionRelationsScreenState extends State<SessionRelationsScreen> {
  Session? _parent;
  List<Session>? _children;
  Object? _error;

  /// A pin or stop that failed, shown over the kept rows.
  String? _actionFailure;
  DateTime? _loadStartedAt;
  int _generation = 0;
  int _routeOperationGeneration = 0;
  int _dataRefreshRevision = 0;
  late SessionNavigationScope _scope;
  bool _selecting = false;

  @override
  void initState() {
    super.initState();
    _scope = SessionNavigationScope(widget.controller);
    _dataRefreshRevision = widget.controller.dataRefreshRevision;
    widget.controller.addListener(_controllerChanged);
    unawaited(_load());
  }

  void _controllerChanged() {
    if (!mounted) return;
    if (!_scope.matches(widget.controller)) {
      _generation++;
      setState(
        () => _error = StateError(
          _copy(context).e7SharedSessionLocationChangedReturnAndReopenRelated,
        ),
      );
      return;
    }
    final revision = widget.controller.dataRefreshRevision;
    if (revision == _dataRefreshRevision) return;
    _dataRefreshRevision = revision;
    unawaited(_load());
  }

  @override
  void didUpdateWidget(covariant SessionRelationsScreen oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (identical(oldWidget.controller, widget.controller) &&
        oldWidget.sessionID == widget.sessionID) {
      return;
    }
    oldWidget.controller.removeListener(_controllerChanged);
    ++_routeOperationGeneration;
    ++_generation;
    _scope = SessionNavigationScope(widget.controller);
    _dataRefreshRevision = widget.controller.dataRefreshRevision;
    _parent = null;
    _children = null;
    _error = null;
    _actionFailure = null;
    _selecting = false;
    widget.controller.addListener(_controllerChanged);
    unawaited(_load());
  }

  bool _routeIsCurrent(
    int generation,
    ConnectionController controller,
    String sessionID,
  ) =>
      mounted &&
      generation == _routeOperationGeneration &&
      identical(widget.controller, controller) &&
      widget.sessionID == sessionID;

  bool _relationIsListed(String id) =>
      _parent?.id == id ||
      (_children?.any((session) => session.id == id) ?? false);

  Future<void> _load() async {
    // Runs from initState, so inherited lookups are not yet allowed.
    final copy = earlyAppLocalizations(context);
    if (!mounted) return;
    final controller = widget.controller;
    final sessionID = widget.sessionID;
    final routeGeneration = _routeOperationGeneration;
    final generation = ++_generation;
    _loadStartedAt = DateTime.now();
    setState(() => _error = null);
    try {
      _scope.check(controller);
      final repository = await controller.prepareActionRepository();
      if (!_routeIsCurrent(routeGeneration, controller, sessionID)) return;
      _scope.check(controller);
      if (repository == null) {
        throw ProductException(copy.e7SharedOpenCodeIsReconnectingTryAgain);
      }
      final current = await repository.getSessionDetails(sessionID);
      if (!_routeIsCurrent(routeGeneration, controller, sessionID)) return;
      _scope.check(controller);
      final parentID = current.parentID ?? current.id;
      var parent = controller.sessionsById[parentID];
      parent ??= parentID == current.id
          ? current
          : await repository.getSessionDetails(parentID);
      final children = await repository.listSessionChildren(parentID);
      _scope.check(controller);
      if (!mounted ||
          generation != _generation ||
          !_routeIsCurrent(routeGeneration, controller, sessionID)) {
        return;
      }
      setState(() {
        _parent = parent;
        _children = children;
      });
    } catch (error) {
      if (!mounted ||
          generation != _generation ||
          !_routeIsCurrent(routeGeneration, controller, sessionID)) {
        return;
      }
      setState(() => _error = error);
    }
  }

  Future<void> _select(Session session) async {
    final copy = _copy(context);
    if (_selecting) return;
    final controller = widget.controller;
    final sessionID = widget.sessionID;
    final routeGeneration = _routeOperationGeneration;
    final scope = _scope;
    bool routeIsCurrent() =>
        _routeIsCurrent(routeGeneration, controller, sessionID);
    setState(() => _selecting = true);
    try {
      scope.check(controller);
      final repository = await controller.prepareActionRepository();
      if (!routeIsCurrent()) return;
      scope.check(controller);
      if (repository == null) {
        throw StateError(copy.e7SharedOpenCodeIsReconnectingTryAgain);
      }
      final current = await repository.getSessionDetails(session.id);
      if (!routeIsCurrent()) return;
      scope.check(controller);
      if (!_relationIsListed(session.id)) {
        throw StateError(copy.e7SharedSessionIsNoLongerRelatedToThis);
      }
      if (current.id != session.id ||
          current.directory != session.directory ||
          current.workspaceID != session.workspaceID) {
        throw StateError(copy.e7SharedSessionLocationChangedReturnAndTryAgain);
      }
      if (mounted) Navigator.of(context).pop(current);
    } catch (_) {
      if (routeIsCurrent()) {
        setState(
          () => _error = StateError(
            copy.e7SharedSessionUnavailableOrLocationChangedReturnOr,
          ),
        );
      }
    } finally {
      if (routeIsCurrent()) setState(() => _selecting = false);
    }
  }

  Future<void> _pin(Session session) async {
    final copy = _copy(context);
    final controller = widget.controller;
    final sessionID = widget.sessionID;
    final routeGeneration = _routeOperationGeneration;
    final scope = _scope;
    bool routeIsCurrent() =>
        _routeIsCurrent(routeGeneration, controller, sessionID);
    setState(() => _actionFailure = null);
    try {
      scope.check(controller);
      final repository = await controller.prepareActionRepository();
      if (!routeIsCurrent()) return;
      scope.check(controller);
      if (repository == null) {
        throw StateError(copy.e7SharedOpenCodeIsReconnectingTryAgain);
      }
      final current = await repository.getSessionDetails(session.id);
      if (!routeIsCurrent()) return;
      scope.check(controller);
      if (!_relationIsListed(session.id)) {
        throw StateError(copy.e7SharedSessionIsNoLongerRelatedToThis);
      }
      if (current.id != session.id ||
          current.directory != session.directory ||
          current.workspaceID != session.workspaceID) {
        throw StateError(copy.e7SharedSessionLocationChangedReturnAndTryAgain);
      }
      // Recheck immediately before the mutating call. A route replacement or
      // location change during the identity read must not pin the new route's
      // session using the old tile's decision.
      if (!routeIsCurrent()) return;
      scope.check(controller);
      await controller.setSessionPinned(
        session.id,
        !controller.isSessionPinned(session.id),
        locationRevision: scope.revision,
      );
    } catch (_) {
      if (mounted && routeIsCurrent()) {
        setState(
          () => _actionFailure = copy.e7SharedCouldNotUpdateThePinReturnAnd,
        );
      }
    }
  }

  /// Stops a working subagent after a question that names it; the stop
  /// runs inside the question, so a failure keeps it open with Try again.
  Future<void> _stop(Session session, String title) async {
    final l10n = _copy(context);
    final controller = widget.controller;
    final scope = _scope;
    await showKitConfirm(
      context,
      title: l10n.sessionRelationsStopTitle(title),
      body: l10n.sessionRelationsStopBody,
      confirmLabel: l10n.sessionRelationsStopConfirm,
      kind: KitConfirmKind.stop,
      icon: AppIconography.stopCircle,
      action: () async {
        scope.check(controller);
        final api = await controller.prepareActionTransport();
        scope.check(controller);
        if (api == null) {
          throw StateError(l10n.e7SharedOpenCodeIsReconnectingTryAgain);
        }
        controller.noteStopFromThisApp(session.id);
        try {
          await api.abort(session.id);
        } catch (_) {
          controller.noteStopFromThisApp(session.id, asked: false);
          rethrow;
        }
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    final l10n = _copy(context);
    return KitScreen(
      topBar: KitTopBar(
        title: l10n.sessionRelationsTitle,
        actions: [
          KitAction(
            label: l10n.e7SharedRefreshSubagentSessions,
            icon: AppIconography.retry,
            onPressed: _load,
          ),
        ],
      ),
      width: KitScreenWidth.list,
      body: _body(context, l10n),
    );
  }

  Widget _body(BuildContext context, AppLocalizations l10n) {
    final parent = _parent;
    final children = _children;
    final error = _error;
    if (error != null &&
        (parent == null || !_scope.matches(widget.controller))) {
      return KitStateView.error(
        key: const ValueKey('session-relations-failed'),
        title: l10n.sessionRelationsFailedTitle,
        body: productErrorText(error, l10n: l10n),
        error: error,
        retry: KitAction(label: l10n.commonRetry, onPressed: _load),
      );
    }
    if (parent == null || children == null) {
      return KitStateView(
        key: const ValueKey('session-relations-loading'),
        icon: AppIconography.nested,
        title: l10n.sessionRelationsLoading,
        progress: const KitProgress.waiting(),
        since: _loadStartedAt,
        onSlow: [KitAction(label: l10n.commonRetry, onPressed: _load)],
      );
    }

    return ListenableBuilder(
      listenable: widget.controller,
      builder: (context, _) {
        final tokens = KitTokens.of(context);
        final failure = error != null
            ? l10n.e7SharedDetail514(productErrorText(error, l10n: l10n))
            : _actionFailure;
        final ordered = _byUrgency(children);
        return KitRefresh(
          onRefresh: _load,
          child: ListView(
            key: const ValueKey('session-relations-list'),
            physics: const AlwaysScrollableScrollPhysics(),
            padding: EdgeInsetsDirectional.only(
              top: tokens.space2,
              bottom: KitScreen.endPadding(context),
            ),
            children: [
              KitReveal(
                child: failure == null
                    ? null
                    : Padding(
                        padding: EdgeInsetsDirectional.symmetric(
                          horizontal: tokens.gutter,
                          vertical: tokens.space2,
                        ),
                        child: KitNotice(
                          key: const ValueKey('session-relations-notice'),
                          tone: AppStatusTone.failure,
                          message: failure,
                          actions: [
                            KitAction(
                              label: l10n.commonRetry,
                              onPressed: () {
                                setState(() => _actionFailure = null);
                                unawaited(_load());
                              },
                            ),
                          ],
                        ),
                      ),
              ),
              KitRowGroup(
                label: l10n.sessionRelationsStartedFrom,
                children: [_row(context, parent, isParent: true)],
              ),
              SizedBox(height: tokens.sectionGap),
              if (children.isEmpty)
                KitStateView(
                  key: const ValueKey('session-relations-empty'),
                  icon: AppIconography.nested,
                  title: l10n.e7SharedNoSubagentSessionsYet,
                  body: l10n.e7SharedDelegatedWorkWillAppearHereWithoutMixing,
                  size: KitStateSize.inline,
                )
              else
                KitRowGroup(
                  label: l10n.sessionRelationsSubagentCount(children.length),
                  children: [
                    for (final child in ordered)
                      _row(context, child, isParent: false),
                  ],
                ),
            ],
          ),
        );
      },
    );
  }

  /// Needs you first, then working, then the rest newest first (owner rule
  /// 2026-09-27: one list ordered by urgency, no state sections).
  List<Session> _byUrgency(List<Session> children) {
    int rank(Session session) {
      if (_needsYou(session.id)) return 0;
      if (widget.controller.busySessions.contains(session.id)) return 1;
      return 2;
    }

    int created(Session session) => session.time?.created ?? 0;
    final sorted = [...children];
    sorted.sort((a, b) {
      final byRank = rank(a).compareTo(rank(b));
      if (byRank != 0) return byRank;
      return created(b).compareTo(created(a));
    });
    return sorted;
  }

  bool _needsYou(String sessionID) =>
      widget.controller.permissionForSession(sessionID) != null ||
      widget.controller.questionForSession(sessionID) != null;

  Widget _row(BuildContext context, Session session, {required bool isParent}) {
    final l10n = _copy(context);
    final controller = widget.controller;
    final current = widget.sessionID == session.id;
    final busy = controller.busySessions.contains(session.id);
    final needsYou = _needsYou(session.id);
    final title = presentedSessionTitle(
      session,
      fallback: isParent
          ? l10n.e7SharedParentSession
          : l10n.globalSessionsUntitled,
      l10n: l10n,
    );
    final created = session.time?.created;
    final when = created == null
        ? null
        : relativeTimeLabel(created, l10n: l10n);
    final Widget leading;
    final InlineSpan supporting;
    if (needsYou) {
      leading = KitNeedsYou.mark();
      supporting = TextSpan(
        children: [
          KitNeedsYou.span(context),
          TextSpan(text: l10n.sessionRelationsOpenToAnswer),
        ],
      );
    } else {
      // A resting parent is a conversation, not a finished step: its icon,
      // not a Done mark, so the mark and the word agree.
      leading = !busy && isParent
          ? KitRow.icon(context, AppIconography.chat)
          : KitStatusMark(
              state: busy ? KitMarkState.working : KitMarkState.done,
            );
      final word = busy
          ? l10n.globalSessionsWorking
          : isParent
          ? l10n.sessionRelationsIdle
          : KitStatusMark.wordFor(context, KitMarkState.done);
      supporting = TextSpan(
        text: [
          word,
          ?when,
          if (current) l10n.sessionRelationsThisConversation,
        ].join(' · '),
      );
    }
    final pinned = controller.isSessionPinned(session.id);
    return KitRow(
      key: ValueKey('session-relation-${session.id}'),
      leading: leading,
      title: title,
      titleMaxLines: 2,
      supporting: supporting,
      selected: current,
      enabled: !_selecting,
      disabledReason: _selecting ? l10n.sessionRelationsOpening : null,
      trailing: current ? null : const KitChevron(),
      onTap: current || _selecting ? null : () => _select(session),
      menuLabel: l10n.sessionRelationsRowMenu(title),
      menu: [
        if (!current)
          KitMenuItem(
            key: const ValueKey('session-relation-open'),
            label: l10n.sessionRelationsOpen(title),
            icon: AppIconography.chat,
            onSelected: () => _select(session),
          ),
        KitMenuItem(
          key: const ValueKey('session-relation-handoff'),
          label: l10n.sessionRelationsCopyHandoff(title),
          icon: AppIconography.copy,
          onSelected: () => showSessionHandoff(
            context,
            controller: controller,
            sessionID: session.id,
            projectID: session.projectID,
          ),
        ),
        if (controller.canPinSessions)
          KitMenuItem(
            key: const ValueKey('session-relation-pin'),
            label: pinned
                ? l10n.sessionRelationsUnpin(title)
                : l10n.sessionRelationsPin(title),
            icon: AppIconography.pin,
            onSelected: () => unawaited(_pin(session)),
          ),
        if (busy && !isParent)
          KitMenuItem(
            key: const ValueKey('session-relation-stop'),
            label: l10n.sessionRelationsStop(title),
            icon: AppIconography.stopCircle,
            destructive: true,
            onSelected: () => unawaited(_stop(session, title)),
          ),
      ],
    );
  }

  @override
  void dispose() {
    _generation++;
    _routeOperationGeneration++;
    widget.controller.removeListener(_controllerChanged);
    super.dispose();
  }
}

AppLocalizations _copy(BuildContext context) =>
    lookupAppLocalizations(Localizations.localeOf(context));
