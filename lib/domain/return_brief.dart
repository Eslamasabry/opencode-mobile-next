import 'dart:convert';

import '../api/models.dart';

/// What a session in the brief is waiting for, in the order the app answers
/// them (a permission outranks a question outranks a form).
enum ReturnBriefBlocker { permission, question, form }

/// One finished-and-unreviewed session.
class ReturnBriefRun {
  const ReturnBriefRun({required this.session, required this.idleAt});
  final Session session;

  /// The server's idle watermark for this session (ms since epoch).
  final int idleAt;
}

/// One session blocked on the user, with the request the brief can answer.
class ReturnBriefRequest {
  const ReturnBriefRequest({
    required this.session,
    required this.blocker,
    required this.requestID,
  });
  final Session session;
  final ReturnBriefBlocker blocker;

  /// Id of the pending request so an acknowledgement can tell a request
  /// the user has already seen from a new one.
  final String requestID;

  String get acknowledgementID =>
      jsonEncode([session.id, blocker.name, requestID]);
}

/// Exact identities the user dismissed, for one profile and location: the
/// (session, idle watermark) pairs and request ids that were actually on
/// screen. A session with a newer idle, a session never shown, or a request
/// with a new id is not covered. Bounded; whatever overflows simply stays
/// unacknowledged and may show again.
class ReturnBriefAck {
  const ReturnBriefAck({required this.runs, required this.requestIDs});
  static const empty = ReturnBriefAck(runs: {}, requestIDs: {});
  static const maxRuns = 64;
  static const maxRequests = 32;

  /// `(sessionID, idle)` pairs.
  final Set<(String, int)> runs;
  final Set<String> requestIDs;

  bool coversRun(String sessionID, int idle) =>
      runs.contains((sessionID, idle));
  bool coversRequest(String requestID) => requestIDs.contains(requestID);

  /// The union with [shown], keeping the newest [maxRuns] runs and the most
  /// recently added [maxRequests] request ids.
  ReturnBriefAck merge({
    required Iterable<(String, int)> shownRuns,
    required Iterable<String> shownRequestIDs,
  }) {
    final mergedRuns = {...runs, ...shownRuns}.toList()
      ..sort((a, b) {
        final byIdle = b.$2.compareTo(a.$2);
        return byIdle != 0 ? byIdle : a.$1.compareTo(b.$1);
      });
    final mergedRequests = [
      ...requestIDs.where((id) => !shownRequestIDs.contains(id)),
      ...shownRequestIDs,
    ];
    return ReturnBriefAck(
      runs: mergedRuns.take(maxRuns).toSet(),
      requestIDs: mergedRequests.length > maxRequests
          ? mergedRequests.sublist(mergedRequests.length - maxRequests).toSet()
          : mergedRequests.toSet(),
    );
  }

  Map<String, Object> toJson() => {
    'runs': [
      for (final (id, idle) in runs) [id, idle],
    ],
    'requests': requestIDs.toList(),
  };

  /// Null for anything malformed: a broken blob reads as "never dismissed",
  /// which only ever shows more, never hides.
  static ReturnBriefAck? fromJson(Object? raw) {
    if (raw is! Map) return null;
    final runs = raw['runs'];
    final requests = raw['requests'];
    if (runs is! List || requests is! List) return null;
    final pairs = <(String, int)>{};
    for (final entry in runs.take(maxRuns)) {
      if (entry is! List || entry.length != 2) return null;
      final id = entry[0];
      final idle = entry[1];
      if (id is! String || id.isEmpty || idle is! int || idle <= 0) {
        return null;
      }
      pairs.add((id, idle));
    }
    final ids = <String>{};
    for (final id in requests.take(maxRequests)) {
      if (id is! String || id.isEmpty) return null;
      ids.add(id);
    }
    return ReturnBriefAck(runs: pairs, requestIDs: ids);
  }
}

/// The compact "while you were away" summary for one profile + location.
/// Built only from state the controller already holds: server idle
/// watermarks, the app's existing read state, and the pending request maps.
/// It never computes an absence interval, never marks anything viewed, and
/// says "unknown" when the server cannot report read state.
class ReturnBrief {
  const ReturnBrief._({
    required this.unreviewed,
    required this.requests,
    required this.readStateKnown,
    required this.inventoryPartial,
    required this.hiddenUnreviewed,
    required this.hiddenRequests,
  });

  /// Rows shown before the card collapses the rest into "N more".
  static const maxShownRuns = 3;
  static const maxShownRequests = 3;

  /// Unreviewed runs newer than the acknowledgement, newest first.
  final List<ReturnBriefRun> unreviewed;

  /// Pending requests not covered by the acknowledgement.
  final List<ReturnBriefRequest> requests;

  /// False when the server cannot report which runs were seen, so
  /// [unreviewed] is necessarily empty and the card must say so.
  final bool readStateKnown;

  /// True while the session inventory is incomplete.
  final bool inventoryPartial;

  /// Unreviewed runs beyond [maxShownRuns], counted but not listed.
  final int hiddenUnreviewed;
  final int hiddenRequests;

  bool get isEmpty => unreviewed.isEmpty && requests.isEmpty;

  /// What dismissing THIS snapshot acknowledges: exactly the rows on screen
  /// (never the collapsed "N more"), merged into [previous]. Anything that
  /// arrives after the snapshot was built is therefore still new.
  ReturnBriefAck acknowledgement(ReturnBriefAck? previous) =>
      (previous ?? ReturnBriefAck.empty).merge(
        shownRuns: [for (final run in unreviewed) (run.session.id, run.idleAt)],
        shownRequestIDs: [
          for (final request in requests) request.acknowledgementID,
        ],
      );

  /// [sessions] are the current location's sessions in the order the
  /// Workspace shows them. [isUnread] is the app's read-state test and is
  /// consulted only when [readStateKnown]. [blockerOf] returns the pending
  /// request (kind + id) a session is waiting on, or null.
  static ReturnBrief build({
    required Iterable<Session> sessions,
    required bool readStateKnown,
    required bool inventoryPartial,
    required bool Function(Session session) isUnread,
    required bool Function(String sessionID) isBusy,
    required (ReturnBriefBlocker, String)? Function(String sessionID) blockerOf,
    ReturnBriefAck? ack,
  }) {
    final runs = <ReturnBriefRun>[];
    final requests = <ReturnBriefRequest>[];
    for (final session in sessions) {
      if (session.parentID != null || session.archived) continue;
      final blocker = blockerOf(session.id);
      if (blocker != null) {
        final (kind, id) = blocker;
        final request = ReturnBriefRequest(
          session: session,
          blocker: kind,
          requestID: id,
        );
        if (ack == null || !ack.coversRequest(request.acknowledgementID)) {
          requests.add(request);
        }
        continue;
      }
      final idle = session.time?.idle;
      if (!readStateKnown ||
          idle == null ||
          idle <= 0 ||
          isBusy(session.id) ||
          !isUnread(session)) {
        continue;
      }
      if (ack != null && ack.coversRun(session.id, idle)) continue;
      runs.add(ReturnBriefRun(session: session, idleAt: idle));
    }
    runs.sort((a, b) {
      final byTime = b.idleAt.compareTo(a.idleAt);
      return byTime != 0 ? byTime : a.session.id.compareTo(b.session.id);
    });
    requests.sort((a, b) {
      final byKind = a.blocker.index.compareTo(b.blocker.index);
      return byKind != 0 ? byKind : a.session.id.compareTo(b.session.id);
    });
    final shown = runs.take(maxShownRuns).toList();
    return ReturnBrief._(
      unreviewed: List.unmodifiable(shown),
      requests: List.unmodifiable(requests.take(maxShownRequests)),
      readStateKnown: readStateKnown,
      inventoryPartial: inventoryPartial,
      hiddenUnreviewed: runs.length - shown.length,
      hiddenRequests: requests.length > maxShownRequests
          ? requests.length - maxShownRequests
          : 0,
    );
  }
}
