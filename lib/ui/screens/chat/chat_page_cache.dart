part of '../chat_screen.dart';

// What one build of the page reads, kept between builds so a rebuild that
// changes nothing costs nothing: the connection fields the page draws from
// (a notification that moved none of them is not a rebuild), and the
// transcript's display passes (folded work, merged text, turn footers),
// worked out again only for the part of the conversation that changed.

/// Counts the messages run through the transcript's display passes.
/// Tests use it to assert that streaming into the newest turn does not
/// work the whole history out again on every flush.
@visibleForTesting
int debugChatDisplayPassMessages = 0;

/// The display passes over one stretch of the transcript, with what they
/// were worked out from.
class _DerivedStretch {
  _DerivedStretch({
    required this.identities,
    required this.busy,
    required this.metaAlways,
    required this.ignore,
    required this.settled,
    required this.changed,
    required this.display,
    required this.owners,
  });

  /// Each message, its parts list and each part, in order: the transcript
  /// model replaces a message or a part rather than changing it, so the
  /// same objects mean the same words.
  final List<Object> identities;
  final bool busy;
  final bool metaAlways;
  final Set<String> ignore;
  final List<MessageWithParts> settled;

  /// Whether [settleStaleLive] changed anything in this stretch.
  final bool changed;
  final List<List<Part>> display;
  final Set<int> owners;

  bool matches(
    List<MessageWithParts> messages,
    int from,
    int to, {
    required bool busy,
    required bool metaAlways,
    required Set<String> ignore,
  }) =>
      busy == this.busy &&
      metaAlways == this.metaAlways &&
      setEquals(ignore, this.ignore) &&
      _sameIdentities(identities, messages, from, to);
}

List<Object> _transcriptIdentities(
  List<MessageWithParts> messages,
  int from,
  int to,
) => [
  for (var i = from; i < to; i++) ...[
    messages[i],
    messages[i].parts,
    ...messages[i].parts,
  ],
];

bool _sameIdentities(
  List<Object> known,
  List<MessageWithParts> messages,
  int from,
  int to,
) {
  var at = 0;
  for (var i = from; i < to; i++) {
    final message = messages[i];
    final parts = message.parts;
    if (at + 2 + parts.length > known.length) return false;
    if (!identical(known[at++], message)) return false;
    if (!identical(known[at++], parts)) return false;
    for (final part in parts) {
      if (!identical(known[at++], part)) return false;
    }
  }
  return at == known.length;
}

/// Whether two connection snapshots ([_ChatPageCache._pageConnectionKey])
/// say the same thing: collections by their contents, the rest by `==`
/// (identity for objects without one, which only ever costs a rebuild).
bool _samePageKey(List<Object?> a, List<Object?> b) {
  if (a.length != b.length) return false;
  for (var i = 0; i < a.length; i++) {
    final x = a[i];
    final y = b[i];
    if (x is Set && y is Set) {
      if (!setEquals(x, y)) return false;
    } else if (x is List && y is List) {
      if (!listEquals(x, y)) return false;
    } else if (x != y) {
      return false;
    }
  }
  return true;
}

/// [read], or a value equal to nothing when it throws (a store that cannot
/// be read now): the page then draws, and handles the failure as it does.
Object? _readOrChanged(Object? Function() read) {
  try {
    return read();
  } catch (_) {
    return Object();
  }
}

extension _ChatPageCache on _ChatScreenState {
  /// What the page reads from the connection, as values a later
  /// notification can be compared with. A notification that changes none
  /// of them (another conversation streaming, a monitor's tick) is skipped.
  /// A value read here that the connection builds afresh on every call
  /// compares unequal and only costs the rebuild the page always had.
  List<Object?> _pageConnectionKey() {
    final c = _conn;
    final id = widget.sessionID;
    final session = c.sessionsById[id];
    final parentID = session?.parentID;
    final selection = c.selectionForSession(id);
    final status = c.connectionStatus;
    final approval = c.autoApprovalFor(id);
    final retry = c.retryStates[id];
    final permissions = c.permissionsForSession(id);
    final question = c.questionForSession(id);
    final queued = c.queuedPromptsFor(id);
    final tips = c.tips;
    final capabilities = c.capabilities;
    // This conversation, its parent, its siblings and its sub-agents.
    final related = [
      for (final other in c.sessionsById.values)
        if (other.parentID != null &&
            (other.parentID == id || other.parentID == parentID))
          other,
    ];
    final relatedIDs = {id, ?parentID, for (final other in related) other.id};
    // Every value at a fixed place: a value that comes and goes would shift
    // the rest and make every comparison unequal.
    return [
      c.isIsolated,
      // A fresh object on every read; what varies is the server's and two
      // flags.
      capabilities.genUi,
      capabilities.agentHandoffFiles,
      c.version,
      c.profile,
      c.store.activeId,
      [...c.store.profiles],
      c.locationRevision,
      c.connectionRevision,
      c.dataRefreshRevision,
      c.offlineFlushRevision,
      c.directory,
      c.workspace,
      c.api,
      c.repository,
      c.status,
      c.isConnected,
      c.connectionError,
      c.connectionStarting,
      c.connectionLoading,
      c.catalogLoading,
      status.phase,
      status.waiting,
      status.profileId,
      status.serverName,
      status.since,
      status.retrying,
      status.quiet,
      status.attemptRevision,
      status.nextAttemptAt,
      session,
      parentID == null ? null : c.sessionsById[parentID],
      related,
      {...relatedIDs.where(c.busySessions.contains)},
      permissions,
      [
        for (final p in permissions) ...[
          c.isRequestPending(c.permissionIdentity(p)),
          c.autoApprovalFailure(p.id),
        ],
      ],
      question,
      question == null
          ? null
          : c.isRequestPending(c.questionIdentity(question)),
      c.formForSession(id),
      c.inboxItemsFor(id),
      queued,
      [
        for (final entry in queued) ...[
          c.queuedPromptSending(entry.id),
          c.queuedPromptAcceptedUnrecorded(entry.id),
        ],
      ],
      _readOrChanged(() => c.commandReceiptsFor(id)),
      retry,
      retry?.next,
      approval.setting,
      approval.explicit,
      c.autoApprovedFor(id),
      selection.model?.providerID,
      selection.model?.modelID,
      selection.agent,
      selection.variant,
      c.modelForSession(id)?.providerID,
      c.modelForSession(id)?.modelID,
      c.displayModelForSession(id)?.providerID,
      c.displayModelForSession(id)?.modelID,
      c.agentForSession(id),
      c.variantForSession(id),
      c.selectedModel?.providerID,
      c.selectedModel?.modelID,
      c.selectedAgent,
      c.catalog,
      c.agents,
      c.turnInterruptReason(id),
      c.turnStoppedFromThisApp(id),
      c.turnStallFor(id),
      c.turnRunCheck(id),
      c.sessionRevertSaving(id),
      c.sessionNoteReceipt(id),
      c.transcriptTimestampsVisible,
      c.transcriptReasoningExpanded,
      c.canUsePromptShelf,
      c.promptShelfProfileID,
      c.promptStash,
      c.sentPromptHistory,
      c.supportsInbox,
      c.supportsSessionSkills,
      c.supportsSessionNotes,
      c.supportsStagedRevert,
      c.serverOwnsSessionSelection,
      c.isAgentBackend,
      c.localServerBusy,
      c.finishedRunNotificationsReady,
      c.defersTurnInterruption,
      _loading ? c.cachedSessionTail(id) : null,
      c.orchestration,
      c.orchestration?.snapshot,
      tips.isActive(TeachingIds.sendLater),
      tips.isActive(TeachingIds.stopAfterStep),
      tips.activeIsReplay,
      // The page's own state that the connection's listeners move.
      _localTurnSince,
      _voiceConversation,
      _voiceReplyWatch,
      _voiceReplyState,
      _voiceOwnerScope,
      _speechOwnerScope,
      _readAloudVoiceID,
    ];
  }

  /// Rebuilds the page for a connection notification only when something
  /// it draws changed ([_pageConnectionKey]).
  void _rebuildForConnection() {
    final List<Object?> key;
    try {
      key = _pageConnectionKey();
    } catch (_) {
      // A read the page itself guards (receipts, storage) failed: draw.
      _pageConnKey = null;
      _setChatState(() {});
      return;
    }
    final previous = _pageConnKey;
    _pageConnKey = key;
    if (previous != null && _samePageKey(previous, key)) return;
    _setChatState(() {});
  }

  /// The transcript as the page draws it: [settleStaleLive], the display
  /// parts ([_timelineDisplayParts]) and the turns' footer owners
  /// ([_turnActionOwners]), the same answers as running them over the whole
  /// list. Everything before the newest prompt is kept while its messages
  /// are the same objects; a stream flush works out the newest turn only.
  ({
    List<MessageWithParts> messages,
    List<List<Part>> displayParts,
    Set<int> owners,
  })
  _deriveTranscript({
    required bool busy,
    required bool metaAlways,
    required Set<String> ignore,
  }) {
    final raw = _messages;
    // The newest prompt that starts a turn: every turn before it is over,
    // whatever runs now. A prompt still waiting in the inbox is not drawn
    // and does not start one.
    var boundary = 0;
    for (var i = raw.length - 1; i >= 0; i--) {
      if (_isPrompt(raw[i]) && !ignore.contains(raw[i].info.id)) {
        boundary = i;
        break;
      }
    }
    var prefix = _derivedPrefix;
    var tail = _derivedTail;
    final samePrefix =
        prefix != null &&
        boundary == _derivedBoundary &&
        prefix.matches(
          raw,
          0,
          boundary,
          busy: busy,
          metaAlways: metaAlways,
          ignore: ignore,
        );
    final sameTail =
        samePrefix &&
        tail != null &&
        tail.matches(
          raw,
          boundary,
          raw.length,
          busy: busy,
          metaAlways: metaAlways,
          ignore: ignore,
        );
    final known = _derived;
    if (sameTail && known != null) return known;
    if (!samePrefix) {
      final before = raw.sublist(0, boundary);
      debugChatDisplayPassMessages += before.length;
      // A turn follows: what settles at the very end of a conversation
      // does not settle here.
      final settled = settleStaleLive(
        before,
        busy: busy,
        moreFollows: boundary < raw.length,
      );
      final display = _timelineDisplayParts(settled);
      prefix = _DerivedStretch(
        identities: _transcriptIdentities(raw, 0, boundary),
        busy: busy,
        metaAlways: metaAlways,
        ignore: ignore,
        settled: settled,
        changed: !identical(settled, before),
        display: display,
        owners: _turnActionOwners(
          settled,
          display,
          ignore: ignore,
          metaAlways: metaAlways,
        ),
      );
      _derivedPrefix = prefix;
      _derivedBoundary = boundary;
    }
    final after = raw.sublist(boundary);
    debugChatDisplayPassMessages += after.length;
    final settledTail = settleStaleLive(after, busy: busy);
    final displayTail = _timelineDisplayParts(settledTail, liveTail: busy);
    // Nothing settled: the page reads the transcript itself, as before.
    final messages = !prefix.changed && identical(settledTail, after)
        ? raw
        : [...prefix.settled, ...settledTail];
    final display = [...prefix.display, ...displayTail];
    final owners = {
      ...prefix.owners,
      ..._turnActionOwners(
        messages,
        display,
        from: prefix.settled.length,
        ignore: ignore,
        metaAlways: metaAlways,
        running: busy,
      ),
    };
    tail = _DerivedStretch(
      identities: _transcriptIdentities(raw, boundary, raw.length),
      busy: busy,
      metaAlways: metaAlways,
      ignore: ignore,
      settled: settledTail,
      changed: !identical(settledTail, after),
      display: displayTail,
      owners: const {},
    );
    _derivedTail = tail;
    return _derived = (
      messages: messages,
      displayParts: display,
      owners: owners,
    );
  }
}
