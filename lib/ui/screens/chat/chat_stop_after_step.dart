part of '../chat_screen.dart';

// "Stop after this step": a long-press on Stop (the chip at the end of the
// composer's chips row) sends the running turn a
// steering note that asks the agent to stop once the step it is on is done.
// OpenCode 2 takes a steer at the next step boundary, so the step finishes
// and the agent reads the note before it starts another. A tap on Stop still
// stops at once.
//
// The hold is a shortcut, never the only path (docs/ux-system/teaching.md):
// a tap on the live line shows the turn's steps with a plain "Stop after
// this step" under it, and a tip points at the hold once a reply has run
// for a while. Once chosen, the live line reads "Stopping after this step"
// with Undo while the note still waits in the inbox, and Stop holds a
// waiting look, until the turn ends.

mixin _ChatStopAfterStepFields {
  /// The note went; cleared when the turn ends or Undo takes it back.
  bool _stopAfterStepPending = false;

  /// Undo's request is in flight.
  bool _undoingStopAfterStep = false;

  /// The live line was tapped: the running turn's steps are shown.
  bool _liveStepsShown = false;

  /// Wakes the screen when the running reply reaches the Stop tip's moment.
  Timer? _stopTipTimer;
}

extension _ChatStopAfterStep on _ChatScreenState {
  /// How long a reply runs before the hold on Stop is worth a tip.
  static const _stopTipAfter = Duration(seconds: 10);

  /// Stop's long-press menu, only where a send made now steers this turn.
  List<KitMenuItem> _stopMenu() {
    if (_watching || _activeDelivery != PromptDelivery.steer) {
      return const [];
    }
    return [
      KitMenuItem(
        key: const Key('composer-stop-after-step'),
        label: _chatL10n(context).chatStopAfterStep,
        icon: AppIconography.stop,
        onSelected: () => unawaited(_stopAfterThisStep()),
      ),
    ];
  }

  /// Sends the note as a steer of the running turn, leaving the box as it
  /// is; the live line then says it waits.
  Future<void> _stopAfterThisStep() async {
    final strings = _chatL10n(context);
    final delivery = _activeDelivery;
    if (delivery != PromptDelivery.steer || _stopAfterStepPending) return;
    _conn.tips.markUsed(TeachingIds.stopAfterStep);
    try {
      final api = await _conn.prepareActionTransport();
      if (api == null) {
        throw StateError(
          _conn.connectionError ??
              strings.chatUiOpenCodeIsReconnectingTryAgainWhenThe,
        );
      }
      final selection = _conn.selectionForSession(widget.sessionID);
      await _conn.ensureInteractiveSendAllowed(expectedApi: api);
      await api.promptAsync(
        widget.sessionID,
        text: strings.chatStopAfterStepPrompt,
        model: selection.model,
        agent: selection.agent?.isNotEmpty == true ? selection.agent : null,
        variant: selection.variant.isEmpty ? null : selection.variant,
        delivery: delivery,
      );
      if (!mounted) return;
      _setChatState(() => _stopAfterStepPending = true);
    } catch (error) {
      if (mounted) _showActionError(error);
    }
  }

  /// Whether [item] is the stop note this screen sent (the live line says
  /// it, so the waiting-sends strip does not say it again).
  bool _isStopAfterStepNote(Api2InboxItem item) =>
      _stopAfterStepPending &&
      item.type == 'user' &&
      (item.promptText ?? '').trim() ==
          _chatL10n(context).chatStopAfterStepPrompt.trim();

  /// The stop note while it still waits in the server's inbox (not yet
  /// read by the agent): only then can Undo take it back. Servers without
  /// an inbox (a direct Codex steer) have nothing to take back, so they
  /// get no Undo.
  Api2InboxItem? _pendingStopNote() {
    if (!_stopAfterStepPending || !_conn.capabilities.inbox) return null;
    for (final item in _conn.inboxItemsFor(widget.sessionID)) {
      if (_isStopAfterStepNote(item)) return item;
    }
    return null;
  }

  /// Undo: cancels the note before the agent reads it.
  Future<void> _undoStopAfterStep(Api2InboxItem note) async {
    final strings = _chatL10n(context);
    _setChatState(() => _undoingStopAfterStep = true);
    try {
      await _conn.cancelInboxItem(widget.sessionID, note.id);
      if (!mounted) return;
      _setChatState(() => _stopAfterStepPending = false);
      _showComposerNote(strings.chatStopAfterStepUndone);
    } on ApiException catch (error) {
      if (!mounted) return;
      if (error.statusCode == 409 || error.statusCode == 404) {
        _showComposerNote(strings.chatStopAfterStepTooLate);
      } else {
        _showActionError(error);
      }
    } catch (error) {
      if (mounted) _showActionError(error);
    } finally {
      if (mounted) _setChatState(() => _undoingStopAfterStep = false);
    }
  }

  /// The running turn's live line with its visible paths: a tap shows the
  /// steps (and "Stop after this step" under it), and a chosen stop reads
  /// "Stopping after this step · Undo". Clears everything once the turn
  /// has ended. Called once per build.
  ({KitTurnLive live, int index})? _withStopAfterStep(
    ({KitTurnLive live, int index})? turn,
  ) {
    if (turn == null) {
      _stopAfterStepPending = false;
      _liveStepsShown = false;
      _stopTipTimer?.cancel();
      _stopTipTimer = null;
      return null;
    }
    final strings = _chatL10n(context);
    final live = turn.live;
    final menu = _stopMenu().isNotEmpty;
    final note = _pendingStopNote();
    _queueStopTip(live, menu: menu);
    return (
      index: turn.index,
      live: KitTurnLive(
        activity: live.activity,
        since: live.since,
        teamAlsoWorking: live.teamAlsoWorking,
        note: live.note,
        detail: live.detail,
        step: live.step,
        stuck: live.stuck,
        stuckCommand: _stuckName,
        onTap: () => _setChatState(() => _liveStepsShown = !_liveStepsShown),
        tapLabel: _liveStepsShown
            ? strings.chatLiveHideSteps
            : strings.chatLiveShowSteps,
        actions: [
          if (_liveStepsShown && menu && !_stopAfterStepPending)
            KitAction(
              key: const Key('live-stop-after-step'),
              label: strings.chatStopAfterStep,
              onPressed: () => unawaited(_stopAfterThisStep()),
            ),
        ],
        pending: _stopAfterStepPending
            ? strings.chatStopAfterStepPending
            : null,
        pendingAction: note == null
            ? null
            : KitAction(
                key: const Key('live-stop-after-step-undo'),
                label: strings.chatStopAfterStepUndo,
                working: _undoingStopAfterStep,
                onPressed: () => unawaited(_undoStopAfterStep(note)),
              ),
      ),
    );
  }

  /// Offers the Stop tip once the reply has run for [_stopTipAfter] and
  /// the hold is there to teach; until then a timer wakes the screen.
  void _queueStopTip(KitTurnLive live, {required bool menu}) {
    final tips = _conn.tips;
    const id = TeachingIds.stopAfterStep;
    final since = live.since;
    if (!menu ||
        _stopAfterStepPending ||
        since == null ||
        tips.isActive(id) ||
        !tips.due(id, maxCount: teachingEntry(id).maxCount)) {
      return;
    }
    final left = _stopTipAfter - clock.now().difference(since);
    if (left > Duration.zero) {
      if (_stopTipTimer?.isActive != true) {
        _stopTipTimer = Timer(left + const Duration(milliseconds: 50), () {
          if (mounted) _setChatState(() {});
        });
      }
      return;
    }
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted || _stopAfterStepPending) return;
      offerTip(tips, id, visit: this, moment: _tipMoment());
    });
  }

  /// What would make a tip unwelcome on this screen now.
  TipMoment _tipMoment() => TipMoment(
    questionWaiting:
        _conn.permissionsForSession(widget.sessionID).isNotEmpty ||
        _conn.questionForSession(widget.sessionID) != null ||
        _conn.formForSession(widget.sessionID) != null,
    keyboardOverAnchor: MediaQuery.viewInsetsOf(context).bottom > 0,
    // Nothing is taught before the first reply (the nudges' rule too).
    onboarding: !_conn.nudges.firstReplySeen,
  );

  /// The Stop tip while it is this screen's to show.
  KitComposerTip? _stopTip() {
    const id = TeachingIds.stopAfterStep;
    final tips = _conn.tips;
    if (!tips.isActive(id) || tips.activeIsReplay) return null;
    return KitComposerTip(
      message: _chatL10n(context).tipStopAfterStep,
      onDismissed: () => tips.dismiss(id),
    );
  }
}
