part of '../chat_screen.dart';

// Where the transcript is: following the latest reply, the earlier-messages
// pill, and jumping to (and marking) one message.

mixin _ChatScrollFields {
  bool _awayFromLatest = false;

  /// How far the transcript is scrolled from its newest message, in pixels
  /// (reversed list), as of the last scroll. Below the "away" distance the
  /// view still does not show what lands at the bottom.
  double _offsetFromLatest = 0;

  /// The transcript's height and its bottom padding (clearance for the
  /// composer and phrase chips) as last laid out. A jump to the newest row
  /// aligns it above that padding; at alignment 0 the positioned list puts
  /// it flush with its edge, under the composer.
  double _transcriptExtent = 0;
  double _transcriptBottomPadding = 0;

  /// Counts jumps to the newest message. A history reload that captured its
  /// anchor before one does not pull the view back to that anchor.
  int _latestJumps = 0;

  /// Set by a send, cleared by the person's own drag: until then the view
  /// keeps the newest row above the composer, whatever lands after the jump
  /// (the reply, an inbox reload, a notice row).
  bool _followAfterSend = false;
  bool _followJumpQueued = false;

  /// Scrolls the transcript by a distance ([_watchLiveEnd]).
  final ScrollOffsetController _messageOffset = ScrollOffsetController();

  /// What the earlier-messages pill last said, kept while it fades out.
  int _earlierPillCount = 0;

  /// How many messages sit above the viewport while reading history (0
  /// otherwise), worked out from the list's item positions. The pill
  /// listens to this, not to the positions themselves: they change on
  /// every frame of a scroll, and the pill built again on each of them
  /// relaid out its layer and recorded the page around the transcript
  /// again, the floating composer with it (scroll-perf, 2026-10-24: a
  /// scroll through history missed more than half its frames on the
  /// emulator).
  final ValueNotifier<int> _earlierCount = ValueNotifier<int>(0);

  /// While the reader is scrolled away from the latest message, the rendered
  /// message count is pinned so a completing turn cannot shift the visible
  /// content by one item (reversed-list index anchoring). Pending messages
  /// materialize when the reader returns to the live end.
  int? _pinnedMessageCount;
  String? _highlightedMessageID;

  /// The step [_jumpToMessage] was asked to show (its call id): its work
  /// line opens, the step flashes, and [_stepRevealContext] is where it
  /// was drawn, for the final scroll onto it.
  String? _highlightedStepID;
  BuildContext? _stepRevealContext;

  /// [ChatScreen.landOnFailure] happens once, after the first history.
  bool _landedOnFailure = false;
  Timer? _highlightTimer;

  /// The bubble just sent and where its words were typed (the composer's
  /// field, global): its row lifts it from there once ([_claimLift]).
  ({String id, Rect from, DateTime at})? _pendingLift;
}

extension _ChatScroll on _ChatScreenState {
  /// Notes where the words of the prompt [messageID] were typed, before the
  /// composer clears: its bubble lifts from there into the transcript
  /// (KitMessage.prompt liftFrom; owner request, 2026-10-27).
  void _markLift(String messageID) {
    // The focus node can outlive its field (voice mode replaced the
    // composer): no field on screen, no lift.
    final context = _focus.context;
    final field = context != null && context.mounted
        ? context.findRenderObject()
        : null;
    _pendingLift = field is RenderBox && field.attached && field.hasSize
        ? (
            id: messageID,
            from: field.localToGlobal(Offset.zero) & field.size,
            at: clock.now(),
          )
        : null;
  }

  /// Where the prompt row [messageID] lifts its bubble from: the composer's
  /// field, once, for the bubble just sent; null for every other row and
  /// every later build.
  Rect? _claimLift(String messageID) {
    final lift = _pendingLift;
    if (lift == null || lift.id != messageID) return null;
    _pendingLift = null;
    // A row first built long after the send (scrolled back to) is simply
    // there.
    return clock.now().difference(lift.at) < const Duration(seconds: 2)
        ? lift.from
        : null;
  }

  bool _onTranscriptScroll(ScrollNotification notification) {
    // Code/table readers own nested scrollables. Their gestures must not
    // change whether the transcript follows the latest message.
    if (notification.depth != 0 || notification.metrics.axis != Axis.vertical) {
      return false;
    }
    if (notification is ScrollStartNotification &&
        notification.dragDetails != null) {
      _followAfterSend = false;
    }
    // The list is reversed, so pixel offset measures distance scrolled away
    // from the newest message.
    _offsetFromLatest = notification.metrics.pixels;
    // Measured from the newest end, not from zero: after a jump (find, a
    // step link) the list is anchored on the target item and its offsets
    // start there. A short conversation shown whole after a find had
    // pixels = min = max = 483 and raised "Jump to latest" over a list that
    // cannot scroll at all (the frosted header's taller viewport, 2026-10-30).
    final metrics = notification.metrics;
    final away =
        metrics.pixels -
            (metrics.minScrollExtent.isFinite ? metrics.minScrollExtent : 0) >
        480;
    if (away != _awayFromLatest) {
      void apply() => _setChatState(() {
        _awayFromLatest = away;
        _pinnedMessageCount = away ? _messages.length : null;
      });
      // A scroll position can settle while the list lays out (new content
      // ends a fling): rebuild after that frame, never during it.
      if (WidgetsBinding.instance.schedulerPhase ==
          SchedulerPhase.persistentCallbacks) {
        WidgetsBinding.instance.addPostFrameCallback((_) {
          if (mounted && away != _awayFromLatest) apply();
        });
      } else {
        apply();
      }
    }
    return false;
  }

  /// How many messages the transcript currently renders — the full list, or
  /// the pinned count while the reader is scrolled away from the live end.
  int get _renderedMessageCount {
    final pinned = _pinnedMessageCount;
    final count = _visibleHistory.length;
    if (!_awayFromLatest || pinned == null) return count;
    return pinned < count ? pinned : count;
  }

  /// Servers return undone rows until the undo is final (v2 until commit,
  /// v1 until the next prompt). Keep them in the hydration cache, but apply
  /// the server's boundary to every chat view on every server.
  Iterable<MessageWithParts> get _visibleHistory {
    final boundary =
        _conn.sessionsById[widget.sessionID]?.stagedRevert?.messageID;
    return boundary == null
        ? _messages
        : _messages.takeWhile(
            (message) => message.info.id.compareTo(boundary) < 0,
          );
  }

  /// Brings [_earlierCount] up to date: on each change of the item
  /// positions, and after each build of the page (reading history starts
  /// or ends, messages arrive).
  void _syncEarlierCount() {
    if (!mounted) return;
    _keepNewestInView();
    _earlierCount.value = _awayFromLatest && _messages.length > 30
        ? _earlierMessageCount(_messagePositions.itemPositions.value)
        : 0;
  }

  /// Messages sitting entirely above the viewport: the transcript's list
  /// index grows toward older messages, so everything past the largest
  /// visible index is "earlier".
  int _earlierMessageCount(Iterable<ItemPosition> positions) {
    var oldestVisible = -1;
    for (final position in positions) {
      if (position.index >= _renderedMessageCount) continue;
      if (position.itemTrailingEdge <= 0 || position.itemLeadingEdge >= 1) {
        continue;
      }
      if (position.index > oldestVisible) oldestVisible = position.index;
    }
    if (oldestVisible < 0) return 0;
    final earlier = _renderedMessageCount - 1 - oldestVisible;
    return earlier > 0 ? earlier : 0;
  }

  /// While following after a send, a newest row that is not fully above the
  /// composer brings it into view on the next frame: a short glide when it
  /// is already on screen (owner request, 2026-10-27), a jump when it is
  /// not there at all or the person asked for less motion.
  void _keepNewestInView() {
    if (!_followAfterSend || _followJumpQueued || _transcriptExtent <= 0) {
      return;
    }
    final inset = _transcriptBottomPadding / _transcriptExtent;
    final newest = _messagePositions.itemPositions.value
        .where((position) => position.index == 0)
        .firstOrNull;
    // Reversed list: the leading edge is the row's bottom, measured up from
    // the viewport's bottom.
    if (newest != null && newest.itemLeadingEdge >= inset - 0.01) return;
    _followJumpQueued = true;
    final onScreen = newest != null;
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      if (!mounted || !_followAfterSend || !_messageScroll.isAttached) {
        _followJumpQueued = false;
        return;
      }
      _latestJumps++;
      final alignment = (_transcriptBottomPadding / _transcriptExtent).clamp(
        0.0,
        0.5,
      );
      if (!onScreen || KitMotion.reduced(context)) {
        _followJumpQueued = false;
        _messageScroll.jumpTo(index: 0, alignment: alignment);
        return;
      }
      // One glide at a time: frames during it do not queue another.
      await _messageScroll.scrollTo(
        index: 0,
        alignment: alignment,
        duration: KitMotion.standard,
        curve: KitMotion.enter,
      );
      _followJumpQueued = false;
    });
  }

  /// The newest line is never left under what floats over the transcript.
  /// When that part grows (a chip comes up over the composer, a tip, a
  /// request) the padding at the list's end grows with it; for a list that
  /// was positioned at a row other than the newest and read down to its end
  /// the view does not move with it, and the last line sat under the new
  /// part until something new arrived (owner phone, 2026-10-29: "Running a
  /// command" half under the Fast chip). Called when the padding grows:
  /// [before] is where the newest row's bottom sat then, [oldPadding] the
  /// padding it had. Two frames on, a list that was at the live end and did
  /// not move is scrolled the missing distance (one quick glide that the
  /// next frame's check replaces while the part folds in). A list that
  /// followed on its own, or a reader higher up, is left alone.
  void _watchLiveEnd(double oldPadding) {
    if (_awayFromLatest || _transcriptExtent <= 0) return;
    final extent = _transcriptExtent;
    double? bottomOf() => _messagePositions.itemPositions.value
        .where((position) => position.index == 0)
        .map((position) => position.itemLeadingEdge * extent)
        .firstOrNull;
    final before = bottomOf();
    if (before == null || before > oldPadding + _liveEndSlack) return;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!mounted ||
            _awayFromLatest ||
            _followJumpQueued ||
            !_messageScroll.isAttached) {
          return;
        }
        final after = bottomOf();
        if (after == null || (after - before).abs() > 1) return;
        final missing = _transcriptBottomPadding - after;
        if (missing <= 1) return;
        unawaited(
          _messageOffset.animateScroll(
            offset: -missing,
            duration: KitMotion.quick,
          ),
        );
      });
      WidgetsBinding.instance.scheduleFrame();
    });
  }

  /// How far above the old clear line the newest row's bottom may sit and
  /// still count as "at the live end" ([_watchLiveEnd]).
  static const double _liveEndSlack = 24;

  void _jumpToLatest() {
    if (!_messageScroll.isAttached) return;
    _latestJumps++;
    _setChatState(() {
      _awayFromLatest = false;
      _pinnedMessageCount = null;
    });
    final alignment = _transcriptExtent > 0
        ? (_transcriptBottomPadding / _transcriptExtent).clamp(0.0, 0.5)
        : 0.0;
    if (KitMotion.reduced(context)) {
      _messageScroll.jumpTo(index: 0, alignment: alignment);
    } else {
      _messageScroll.scrollTo(
        index: 0,
        alignment: alignment,
        duration: KitMotion.standard,
        curve: KitMotion.enter,
      );
    }
  }

  /// [ChatScreen.landOnFailure]: the newest turn that ended in an error,
  /// scrolled to and marked once. None in the loaded history: the chat
  /// opens at its newest turn as usual.
  void _landOnFailedTurn() {
    if (!widget.landOnFailure || _landedOnFailure) return;
    _landedOnFailure = true;
    MessageWithParts? failed;
    for (final message in _visibleHistory) {
      if (message.info.errorText != null) failed = message;
    }
    if (failed == null) return;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) _jumpToMessage(failed!.info.id, alignment: .3);
    });
  }

  /// Scrolls to [messageID] and marks it for a moment. With [stepID] (a
  /// step's call id) the step's folded work opens, the step itself is
  /// marked instead of the whole turn, and the list settles on the step.
  void _jumpToMessage(
    String messageID, {
    double alignment = .5,
    String? stepID,
  }) {
    final chronologicalIndex = _messages.indexWhere(
      (message) => message.info.id == messageID,
    );
    if (chronologicalIndex < 0 ||
        chronologicalIndex >= _visibleHistory.length) {
      _showActionError(_chatL10n(context).chatUiThatMessageIsNoLongerInThis);
      return;
    }
    final listIndex = _visibleHistory.length - 1 - chronologicalIndex;
    _highlightTimer?.cancel();
    _setChatState(() {
      // Materialize any messages deferred while scrolled away so the target
      // index maps onto the rendered list.
      _pinnedMessageCount = null;
      _highlightedMessageID = messageID;
      _highlightedStepID = stepID;
      _stepRevealContext = null;
    });
    final findKey = _findOpen ? _findKey : null;
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      if (!mounted ||
          !_messageScroll.isAttached ||
          _highlightedMessageID != messageID) {
        return;
      }
      if (findKey != null) {
        // A distant animated scroll builds a temporary list. Its excerpt
        // context can be retired before the final list takes over. Materialize
        // search targets directly, then animate only the precise reveal.
        _messageScroll.jumpTo(index: listIndex, alignment: alignment);
      } else {
        await _messageScroll.scrollTo(
          index: listIndex,
          alignment: alignment,
          duration: KitMotion.reduced(context)
              ? Duration.zero
              : KitMotion.standard,
          curve: KitMotion.enter,
        );
      }
      if (findKey == null && stepID == null) return;
      final layout = WidgetsBinding.instance.endOfFrame;
      // jumpTo can be a no-op for the same list index. Still wait for a
      // scheduled layout before revealing the changed excerpt.
      WidgetsBinding.instance.scheduleFrame();
      await layout;
      final target = findKey != null ? _findExcerptContext : _stepRevealContext;
      if (!mounted ||
          (findKey != null && (!_findOpen || _findKey != findKey)) ||
          (stepID != null && _highlightedStepID != stepID) ||
          target == null ||
          !target.mounted) {
        return;
      }
      final scrollable = Scrollable.of(target);
      final viewport = scrollable.context.findRenderObject() as RenderBox;
      final excerpt = target.findRenderObject() as RenderBox;
      final top = excerpt.localToGlobal(Offset.zero, ancestor: viewport).dy;
      final desired = math.max(
        0.0,
        (viewport.size.height - excerpt.size.height) * .3,
      );
      final position = scrollable.position;
      // This list centers slivers around an arbitrary item. ensureVisible's
      // sliver reveal offset is unreliable there; use measured viewport pixels.
      final delta = top - desired;
      await position.moveTo(
        position.pixels +
            (position.axisDirection == AxisDirection.up ? -delta : delta),
        clamp: false,
        duration: KitMotion.reduced(context) ? Duration.zero : KitMotion.quick,
        curve: KitMotion.enter,
      );
    });
    _highlightTimer = Timer(const Duration(seconds: 2), () {
      if (mounted && _highlightedMessageID == messageID) {
        _setChatState(() {
          _highlightedMessageID = null;
          _highlightedStepID = null;
          _stepRevealContext = null;
        });
      }
    });
  }
}
