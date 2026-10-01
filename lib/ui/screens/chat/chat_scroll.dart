part of '../chat_screen.dart';

// Where the transcript is: following the latest reply, the earlier-messages
// pill, and jumping to (and marking) one message.

mixin _ChatScrollFields {
  bool _awayFromLatest = false;

  /// What the earlier-messages pill last said, kept while it fades out.
  int _earlierPillCount = 0;

  /// While the reader is scrolled away from the latest message, the rendered
  /// message count is pinned so a completing turn cannot shift the visible
  /// content by one item (reversed-list index anchoring). Pending messages
  /// materialize when the reader returns to the live end.
  int? _pinnedMessageCount;
  String? _highlightedMessageID;

  /// [ChatScreen.landOnFailure] happens once, after the first history.
  bool _landedOnFailure = false;
  Timer? _highlightTimer;
}

extension _ChatScroll on _ChatScreenState {
  bool _onTranscriptScroll(ScrollNotification notification) {
    // Code/table readers own nested scrollables. Their gestures must not
    // change whether the transcript follows the latest message.
    if (notification.depth != 0 || notification.metrics.axis != Axis.vertical) {
      return false;
    }
    // The list is reversed, so pixel offset measures distance scrolled away
    // from the newest message.
    final away = notification.metrics.pixels > 480;
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

  void _jumpToLatest() {
    if (!_messageScroll.isAttached) return;
    _setChatState(() {
      _awayFromLatest = false;
      _pinnedMessageCount = null;
    });
    if (KitMotion.reduced(context)) {
      _messageScroll.jumpTo(index: 0);
    } else {
      _messageScroll.scrollTo(
        index: 0,
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

  void _jumpToMessage(String messageID, {double alignment = .5}) {
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
      if (findKey == null) return;
      final layout = WidgetsBinding.instance.endOfFrame;
      // jumpTo can be a no-op for the same list index. Still wait for a
      // scheduled layout before revealing the changed excerpt.
      WidgetsBinding.instance.scheduleFrame();
      await layout;
      final target = _findExcerptContext;
      if (!mounted ||
          !_findOpen ||
          _findKey != findKey ||
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
        _setChatState(() => _highlightedMessageID = null);
      }
    });
  }
}
