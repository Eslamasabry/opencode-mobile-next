import 'dart:async';

import 'package:flutter/foundation.dart';

import '../domain/models.dart';
import 'nudges.dart';

/// Everything the conversation nudges need to know, read from existing state
/// by the conversation screen. Plain values, so the trigger rules below are
/// testable without a screen.
@immutable
class ConversationNudgeFacts {
  const ConversationNudgeFacts({
    required this.busy,
    required this.hasAssistantReply,
    this.pendingPermissions = const {},
    this.approvalsAvailable = false,
    this.runChangedFiles = false,
    this.reviewAvailable = false,
    this.contextUsage,
    this.compactAvailable = false,
    this.finishedRunNotificationsReady = false,
    this.suppressed = false,
  });

  /// The conversation has a run in progress.
  final bool busy;

  /// The transcript holds at least one assistant message.
  final bool hasAssistantReply;

  /// Requests waiting on the person: request id to permission kind.
  final Map<String, String> pendingPermissions;

  /// The Approvals control exists here and is not already automatic.
  final bool approvalsAvailable;

  /// The latest run edited files (see [runChangedFiles]).
  final bool runChangedFiles;

  /// The server can show the conversation's changes.
  final bool reviewAvailable;

  /// Fraction of the model's context window in use; null when unknown.
  final double? contextUsage;

  /// The server can compact and a compact would be accepted now.
  final bool compactAvailable;

  /// A finished-run notification would really reach the person right now.
  final bool finishedRunNotificationsReady;

  /// Another inline card owns the space above the composer. Nothing is
  /// offered meanwhile, and nothing is consumed.
  final bool suppressed;
}

/// Tool names that write to the project. A completed call is the evidence
/// that a run changed files; the session diff summary is v1-only and lags.
const _fileWritingTools = {
  'edit',
  'write',
  'multiedit',
  'patch',
  'apply_patch',
};

/// Whether the latest run (everything after the last prompt) completed a
/// file-writing tool call.
bool runChangedFiles(List<MessageWithParts> messages) {
  for (var index = messages.length - 1; index >= 0; index -= 1) {
    final message = messages[index];
    if (message.info.role == 'user') return false;
    for (final part in message.parts) {
      if (part.type == 'tool' &&
          _fileWritingTools.contains(part.toolName?.toLowerCase()) &&
          part.toolState.status == 'completed') {
        return true;
      }
    }
  }
  return false;
}

typedef NudgeTimerFactory =
    Timer Function(Duration duration, void Function() callback);

/// Turns a stream of [ConversationNudgeFacts] into offers on the registry.
/// One per open conversation; the registry decides whether an offer shows.
class ConversationNudgeWatcher {
  ConversationNudgeWatcher({
    required this.registry,
    required this.conversation,
    NudgeTimerFactory? createTimer,
    this.onFirstReply,
    DateTime Function()? now,
  }) : _createTimer = createTimer ?? Timer.new,
       _now = now ?? DateTime.now;

  /// How long a run must keep the person waiting before leaving is suggested.
  static const longWait = Duration(seconds: 60);

  /// Context use from which compacting is worth suggesting.
  static const contextThreshold = .8;

  final NudgeRegistry registry;
  final String conversation;
  final NudgeTimerFactory _createTimer;
  final DateTime Function() _now;

  /// Called once per install, when the first reply is seen. [waited] runs
  /// from the start of the run that produced it, or is null when this
  /// screen did not see that run start.
  final void Function(Duration? waited)? onFirstReply;
  DateTime? _runStarted;
  bool _firstReplyReported = false;

  ConversationNudgeFacts? _facts;
  Timer? _waitTimer;
  bool _waitedLong = false;
  bool _disposed = false;

  void observe(ConversationNudgeFacts facts) {
    if (_disposed) return;
    final wasBusy = _facts?.busy ?? facts.busy;
    _facts = facts;

    for (final entry in facts.pendingPermissions.entries) {
      registry.notePermissionRequest(
        conversation: conversation,
        kind: entry.value,
        requestID: entry.key,
      );
    }
    _syncWaitTimer(facts.busy);

    if (!registry.firstReplySeen) {
      // First run. The observation that sees the first reply only records
      // it: that moment belongs to the reply itself (and to the phase 3b
      // notification card), so tips start with the next trigger.
      if (facts.busy) _runStarted ??= _now();
      if (!facts.busy && facts.hasAssistantReply) {
        unawaited(registry.markFirstReplySeen());
        final started = _runStarted;
        if (!_firstReplyReported) {
          _firstReplyReported = true;
          onFirstReply?.call(
            started == null ? null : _now().difference(started),
          );
        }
      }
      return;
    }

    _retireStale(facts);
    if (facts.suppressed) return;

    final runFinished = wasBusy && !facts.busy;
    final repeated = registry.repeatedPermissionKind(conversation);
    if (repeated != null &&
        facts.approvalsAvailable &&
        facts.pendingPermissions.isEmpty) {
      // After the third request is answered, not beside it: the request card
      // needs the space and the person's attention first.
      registry.offer(NudgeId.approvals, scope: conversation, detail: repeated);
    }
    if (runFinished && facts.runChangedFiles && facts.reviewAvailable) {
      registry.offer(NudgeId.reviewChanges, scope: conversation);
    }
    if (_waitedLong && facts.busy && facts.finishedRunNotificationsReady) {
      registry.offer(NudgeId.leaveAndBeTold, scope: conversation);
    }
    final usage = facts.contextUsage;
    if (usage != null &&
        usage >= contextThreshold &&
        facts.compactAvailable &&
        !facts.busy) {
      registry.offer(
        NudgeId.compact,
        scope: conversation,
        detail: '${(usage * 100).clamp(0, 100).round()}',
      );
    }
  }

  /// A card whose sentence stopped being true leaves on its own.
  void _retireStale(ConversationNudgeFacts facts) {
    final active = registry.activeFor(conversation);
    if (active == null) return;
    final stale = switch (active.id) {
      NudgeId.leaveAndBeTold =>
        !facts.busy || !facts.finishedRunNotificationsReady,
      NudgeId.approvals => !facts.approvalsAvailable,
      NudgeId.compact =>
        !facts.compactAvailable || (facts.contextUsage ?? 0) < contextThreshold,
      NudgeId.reviewChanges => !facts.reviewAvailable,
      NudgeId.pinConversations => false,
    };
    if (stale) registry.release(active.id);
  }

  void _syncWaitTimer(bool busy) {
    if (!busy) {
      _waitTimer?.cancel();
      _waitTimer = null;
      _waitedLong = false;
      return;
    }
    // The wait is measured while this conversation is open and busy, which
    // is exactly the time the person spent watching it.
    _waitTimer ??= _createTimer(longWait, () {
      if (_disposed) return;
      _waitedLong = true;
      final facts = _facts;
      if (facts != null) observe(facts);
    });
  }

  void dispose() {
    _disposed = true;
    _waitTimer?.cancel();
    // Screens dispose while the widget tree is locked; other listeners (the
    // Work tab) must not be told to rebuild until that frame is over.
    scheduleMicrotask(() => registry.releaseScope(conversation));
  }
}
