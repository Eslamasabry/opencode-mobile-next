import 'dart:async';
import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../domain/genui/gen_ui.dart';
import '../domain/genui/gen_ui_history.dart';
import '../domain/server_gateway.dart';
import 'delayed_answers.dart';
import 'gen_ui_journal.dart';
import 'gen_ui_coverage.dart';

String genUiScopeKey(GenUiScope scope) => jsonEncode([
  scope.profileID,
  scope.sourceId,
  scope.directory,
  scope.workspace,
]);
String _sessionKey(GenUiScope scope, String id) =>
    jsonEncode([genUiScopeKey(scope), id]);

final class GenUiTarget {
  const GenUiTarget(this.scope, this.sessionID);
  final GenUiScope scope;
  final String sessionID;
}

final class _Source {
  _Source(
    this.scope,
    this.gateway,
    this.endpoint,
    this.current,
    this.ready,
    this.beforeSend,
  );
  final GenUiScope scope;
  final ServerGateway gateway;
  final String endpoint;
  bool Function() current;
  final bool ready;
  Future<void> Function()? beforeSend;
}

final class _View {
  _View(this.messages, this.complete, this.parsed);
  final List<MessageWithParts> messages;
  final bool complete;
  final Map<String, GenUiParse> parsed;
}

/// Shared by the chat controller and its phone/list sources. No UI or host IO.
final class GenUiStateController extends ChangeNotifier {
  GenUiStateController(SharedPreferences prefs, {required this.delayedAnswers})
    : journal = GenUiJournal(prefs),
      _covered = GenUiCoverage(prefs);
  final GenUiJournal journal;
  final GenUiCoverage _covered;
  final DelayedAnswers delayedAnswers;
  final _sources = <String, _Source>{};
  final _views = <String, _View>{};
  final _revisions = <String, int>{};
  final _delivery = <String, GenUiDeliveryState>{};
  // Live server echoes can lead bounded history while a turn is running.
  // Keep only volatile receipt summaries; the dispatch journal stays intact.
  final _liveAnswers = <String, ({GenUiCard card, String summary})>{};
  final _sends = <String, Future<void>>{};
  final _held = <String, Completer<void>>{};
  final _closedProfiles = <String>{};
  final _reads = <String, Future<void>>{};
  final _deletedSessions = <String>{};
  final _remembering = <String>{};
  final _queuedRecovery = <String, GenUiTarget>{};
  final _coveragePending = <String>{};
  bool _coverageOverflow = false;
  Future<void> _historyLane = Future.value();
  Future<void>? _recoveryPass;
  bool _disposed = false;
  Timer? _recoveryFollowup;
  int _dispatchSerial = 0;
  bool recoveryIncomplete = false;

  void register(
    GenUiScope scope,
    ServerGateway gateway, {
    required String endpoint,
    required bool Function() current,
    required bool ready,
    Future<void> Function()? beforeSend,
  }) {
    final key = genUiScopeKey(scope), old = _sources[genUiScopeKey(scope)];
    if (old != null &&
        (!identical(old.gateway, gateway) ||
            old.endpoint != endpoint ||
            old.ready != ready)) {
      invalidateScope(scope);
    }
    if (old != null &&
        identical(old.gateway, gateway) &&
        old.endpoint == endpoint &&
        old.ready == ready) {
      old.current = current;
      old.beforeSend = beforeSend;
      return;
    }
    _sources[key] = _Source(
      scope,
      gateway,
      endpoint,
      current,
      ready,
      beforeSend,
    );
    if (ready) {
      recoveryIncomplete = true;
      // Nothing may read this source again soon (a phone agent's chat
      // connects a new gateway while its event channel stays open): read it
      // now, or the conversations list says "couldn't load" until a refresh.
      _recoveryFollowup ??= Timer(const Duration(milliseconds: 350), () {
        _recoveryFollowup = null;
        if (!_disposed) unawaited(recover(const []));
      });
    }
  }

  bool available(GenUiScope scope) {
    final source = _sources[genUiScopeKey(scope)];
    return !_disposed &&
        !_closedProfiles.contains(scope.profileID) &&
        source != null &&
        source.ready &&
        source.current();
  }

  void _notify() {
    if (!_disposed) notifyListeners();
  }

  void invalidateProfile(String profile) {
    for (final source in _sources.values.toList()) {
      if (source.scope.profileID == profile) invalidateScope(source.scope);
    }
  }

  void invalidateScope(GenUiScope scope) {
    _liveAnswers.removeWhere((_, receipt) => receipt.card.scope == scope);
    final prefix = genUiScopeKey(scope);
    for (final key in {
      ..._views.keys,
      ..._reads.keys,
    }.where((k) => (jsonDecode(k) as List).first == prefix).toList()) {
      _revisions[key] = (_revisions[key] ?? 0) + 1;
      _views.remove(key);
    }
    for (final entry in _held.keys.toList()) {
      final identity = jsonDecode(entry) as List;
      if (identity[0] == scope.profileID &&
          identity[1] == scope.sourceId &&
          identity[2] == scope.directory &&
          identity[3] == scope.workspace) {
        _undo(entry);
      }
    }
  }

  void forgetSource(GenUiScope scope) {
    invalidateScope(scope);
    _sources.remove(genUiScopeKey(scope));
    final prefix = genUiScopeKey(scope);
    _deletedSessions.removeWhere(
      (k) => (jsonDecode(k) as List).first == prefix,
    );
    _coveragePending.removeWhere(
      (k) => (jsonDecode(k) as List).first == prefix,
    );
    _revisions.removeWhere(
      (k, _) =>
          (jsonDecode(k) as List).first == prefix && !_reads.containsKey(k),
    );
    _delivery.removeWhere((k, _) {
      final value = jsonDecode(k) as List;
      return value[0] == scope.profileID &&
          value[1] == scope.sourceId &&
          value[2] == scope.directory &&
          value[3] == scope.workspace &&
          !_sends.containsKey(k);
    });
    _notify();
  }

  void stale(GenUiScope scope, String sessionID) {
    final key = _sessionKey(scope, sessionID);
    final old = _views[key];
    if (old == null && !_reads.containsKey(key)) return;
    // Invalidate every active read, even when the visible view is already stale.
    _revisions[key] = (_revisions[key] ?? 0) + 1;
    if (old == null || !old.complete) return;
    _views[key] = _View(old.messages, false, old.parsed);
    _notify();
  }

  void observe(
    GenUiScope scope,
    String sessionID,
    List<MessageWithParts> messages, {
    required bool tailComplete,
  }) {
    if (!available(scope) ||
        _deletedSessions.contains(_sessionKey(scope, sessionID))) {
      return;
    }
    final parsed = <String, GenUiParse>{};
    final copy = <MessageWithParts>[];
    for (final message in messages.skip(
      messages.length > 100 ? messages.length - 100 : 0,
    )) {
      if (message.info.sessionID != sessionID) continue;
      copy.add(
        MessageWithParts(info: message.info, parts: List.of(message.parts)),
      );
      if (message.info.role != 'assistant') continue;
      for (final part in message.parts) {
        final result = genUiFromPart(
          part,
          scope: scope,
          sessionID: sessionID,
          messageID: message.info.id,
        );
        if (result != null) parsed[_partKey(message.info.id, part)] = result;
      }
    }
    final key = _sessionKey(scope, sessionID);
    _liveAnswers.removeWhere((_, receipt) {
      final card = receipt.card;
      if (card.scope != scope || card.sessionID != sessionID) return false;
      final unchanged = parsed.values.any(
        (p) =>
            p is GenUiParsed &&
            p.card.identity == card.identity &&
            p.card.revision == card.revision,
      );
      // A lagging, card-only snapshot does not undo the live acknowledgement.
      // A removed/replaced card or authoritative contrary chronology does.
      return !unchanged ||
          (tailComplete &&
              genUiStateFor(card, copy, tailComplete: true) ==
                  GenUiCardState.passedOver);
    });
    _revisions[key] = (_revisions[key] ?? 0) + 1;
    if (!_views.containsKey(key) && _views.length >= 20) {
      final evicted = _views.keys.first;
      final view = _views.remove(evicted);
      if (!_reads.containsKey(evicted)) _revisions.remove(evicted);
      for (final p in view?.parsed.values ?? const <GenUiParse>[]) {
        if (p is GenUiParsed && !_sends.containsKey(p.card.identity)) {
          _delivery.remove(p.card.identity);
        }
      }
    }
    _views[key] = _View(List.unmodifiable(copy), tailComplete, parsed);
    _notify();
    _scheduleRemember(scope, sessionID);
  }

  /// Accepts a complete, server-origin live user message, never a UI projection.
  void observeLiveAnswer(
    GenUiScope scope,
    MessageWithParts message, {
    required String correlationID,
  }) {
    if (!available(scope) ||
        correlationID.isEmpty ||
        correlationID.length > 256 ||
        message.info.role != 'user' ||
        message.info.id.isEmpty ||
        message.parts.any(
          (p) =>
              p.synthetic ||
              (p.messageID != null && p.messageID != message.info.id),
        )) {
      return;
    }
    final key = _sessionKey(scope, message.info.sessionID);
    if (_deletedSessions.contains(key)) return;
    final view = _views[key], source = _sources[genUiScopeKey(scope)];
    if (view == null || source == null) return;
    final List<GenUiReference> entries;
    try {
      entries = journal.read(scope.profileID);
    } catch (_) {
      return;
    }
    for (final parsed in view.parsed.values) {
      if (parsed is! GenUiParsed) continue;
      final card = parsed.card;
      final reserved = entries.any(
        (e) =>
            e.identity == card.identity &&
            e.scope == scope &&
            e.revision == card.revision &&
            e.endpoint == source.endpoint &&
            e.dispatchID == correlationID,
      );
      if (!reserved) continue;
      final answer = genUiAnswerIn(message, card);
      if (answer == null ||
          genUiStateFor(card, [
                ...view.messages,
                message,
              ], tailComplete: false) !=
              GenUiCardState.answered) {
        continue;
      }
      _liveAnswers[card.identity] = (card: card, summary: answer.summary);
      while (_liveAnswers.length > 100) {
        _liveAnswers.remove(_liveAnswers.keys.first);
      }
      _notify();
      return;
    }
  }

  String? _liveSummary(GenUiCard card, _View view) {
    final receipt = _liveAnswers[card.identity];
    if (receipt == null || receipt.card.revision != card.revision) return null;
    return view.parsed.values.any(
          (p) =>
              p is GenUiParsed &&
              p.card.identity == card.identity &&
              p.card.revision == card.revision,
        )
        ? receipt.summary
        : null;
  }

  void _scheduleRemember(GenUiScope scope, String sessionID) {
    final key = _sessionKey(scope, sessionID),
        view = _views[_sessionKey(scope, sessionID)];
    if (!_remembering.add(key)) return;
    unawaited(
      _remember(scope, sessionID)
          .catchError((Object _) {
            recoveryIncomplete = true;
            _notify();
          })
          .whenComplete(() {
            _remembering.remove(key);
            if (!_disposed &&
                !_deletedSessions.contains(key) &&
                available(scope) &&
                _views[key] != null &&
                !identical(_views[key], view)) {
              _scheduleRemember(scope, sessionID);
            }
          }),
    );
  }

  static String _partKey(String message, Part part) =>
      jsonEncode([message, part.id, part.callID]);

  GenUiParse? cardForPart(
    GenUiScope scope,
    String sessionID,
    String messageID,
    Part part,
  ) {
    // Cached parts observed under an authoritative assistant message win.
    final cached = available(scope)
        ? _views[_sessionKey(scope, sessionID)]?.parsed[_partKey(
            messageID,
            part,
          )]
        : null;
    if (cached != null) return cached;
    // Not (yet) confirmed: the call still finished and the server accepted
    // it, so it reads as a card, never as a raw tool row. Its state stays
    // unknown (no answer is accepted from it) until the view confirms it.
    return genUiFromPart(
      part,
      scope: scope,
      sessionID: sessionID,
      messageID: messageID,
    );
  }

  /// Registration qualification without transport liveness. Retained OAuth
  /// may survive browser suspension, but never removal of this registration.
  bool registeredReady(GenUiScope scope) =>
      !_disposed &&
      !_closedProfiles.contains(scope.profileID) &&
      _sources[genUiScopeKey(scope)]?.ready == true;

  /// Previously observed payload, for continuing an already accepted connector
  /// while the event transport is temporarily suspended. This alone must never
  /// authorize a new request; callers must recover and use [state] first.
  GenUiCard? observedCard(GenUiCard card) {
    if (_disposed || _closedProfiles.contains(card.scope.profileID)) {
      return null;
    }
    final view = _views[_sessionKey(card.scope, card.sessionID)];
    for (final parsed in view?.parsed.values ?? const <GenUiParse>[]) {
      if (parsed is GenUiParsed &&
          parsed.card.identity == card.identity &&
          parsed.card.revision == card.revision) {
        return parsed.card;
      }
    }
    return null;
  }

  /// Null means recovery is pending, not evidence that a card was removed.
  GenUiCardState? observedState(GenUiCard card) {
    if (_disposed ||
        _closedProfiles.contains(card.scope.profileID) ||
        _deletedSessions.contains(_sessionKey(card.scope, card.sessionID))) {
      return GenUiCardState.unknown;
    }
    final view = _views[_sessionKey(card.scope, card.sessionID)];
    if (view == null || !view.complete) return null;
    return genUiStateFor(card, view.messages, tailComplete: true);
  }

  GenUiCardState state(GenUiCard card) {
    if (!available(card.scope)) return GenUiCardState.unknown;
    final view = _views[_sessionKey(card.scope, card.sessionID)];
    if (view == null) return GenUiCardState.unknown;
    if (_liveSummary(card, view) != null) return GenUiCardState.answered;
    return genUiStateFor(card, view.messages, tailComplete: view.complete);
  }

  List<GenUiCard> waiting(GenUiScope scope, String sessionID) {
    if (!available(scope)) return const [];
    return List.unmodifiable([
      for (final result
          in _views[_sessionKey(scope, sessionID)]?.parsed.values ??
              const <GenUiParse>[])
        if (result is GenUiParsed &&
            state(result.card) == GenUiCardState.waiting)
          result.card,
    ]);
  }

  String? summary(GenUiCard card) {
    if (state(card) != GenUiCardState.answered) return null;
    final live = _liveSummary(
      card,
      _views[_sessionKey(card.scope, card.sessionID)]!,
    );
    if (live != null) return live;
    var afterCard = false;
    for (final message
        in _views[_sessionKey(card.scope, card.sessionID)]!.messages) {
      if (message.info.id == card.messageID) {
        afterCard = true;
        continue;
      }
      if (!afterCard) continue;
      final answer = genUiAnswerIn(message, card);
      if (answer != null) return answer.summary;
    }
    return null;
  }

  GenUiDeliveryState delivery(GenUiCard card) {
    final known = _delivery[card.identity];
    if (known != null) return known;
    try {
      if (journal
          .read(card.scope.profileID)
          .any((e) => e.identity == card.identity && e.dispatchID != null)) {
        return GenUiDeliveryState.deliveryUnknown;
      }
    } catch (_) {
      return GenUiDeliveryState.deliveryUnknown;
    }
    return GenUiDeliveryState.idle;
  }

  Future<void> _remember(GenUiScope scope, String sessionID) async {
    final source = _sources[genUiScopeKey(scope)];
    final key = _sessionKey(scope, sessionID),
        view = _views[_sessionKey(scope, sessionID)];
    if (source == null || !available(scope) || view == null || !view.complete) {
      return;
    }
    final settled = <String>{};
    await journal.update(scope.profileID, (entries) {
      if (!available(scope) ||
          _deletedSessions.contains(key) ||
          !identical(_views[key], view) ||
          !identical(_sources[genUiScopeKey(scope)], source)) {
        return entries;
      }
      final result = List<GenUiReference>.of(entries);
      for (final parsed in view.parsed.values) {
        if (parsed is! GenUiParsed) continue;
        final card = parsed.card;
        final status = genUiStateFor(card, view.messages, tailComplete: true);
        final old = result
            .where((r) => r.identity == card.identity)
            .firstOrNull;
        if (status == GenUiCardState.waiting && old?.dispatchID == null) {
          result.removeWhere((r) => r.identity == card.identity);
          result.add(GenUiReference.card(card, source.endpoint));
        } else if (status == GenUiCardState.answered ||
            status == GenUiCardState.passedOver) {
          result.removeWhere((r) => r.identity == card.identity);
          settled.add(card.identity);
        }
      }
      while (result.length > 100) {
        final index = result.indexWhere((r) => r.dispatchID == null);
        if (index < 0) break;
        result.removeAt(index);
      }
      return result;
    });
    if (identical(_views[key], view)) {
      for (final identity in settled) {
        _delivery.remove(identity);
      }
      _notify();
    }
  }

  /// Reads newest first, retaining chronological order within the bounded tail.
  Future<void> recoverOne(GenUiTarget target) {
    final key = _sessionKey(target.scope, target.sessionID);
    final existing = _reads[key];
    if (existing != null) return existing.timeout(const Duration(seconds: 10));
    var wanted = true;
    late final Future<void> work;
    work = _historyLane
        .then((_) async {
          if (wanted) await _recoverOne(target, () => wanted);
        })
        .whenComplete(() {
          if (identical(_reads[key], work)) _reads.remove(key);
          if (!_views.containsKey(key)) _revisions.remove(key);
        });
    _historyLane = work.then<void>((_) {}, onError: (Object _) {});
    _reads[key] = work;
    return work.timeout(
      const Duration(seconds: 10),
      onTimeout: () {
        wanted = false;
        throw TimeoutException('Card history timed out.');
      },
    );
  }

  Future<void> _recoverOne(GenUiTarget target, bool Function() wanted) async {
    final scope = target.scope, sid = target.sessionID;
    if (!available(scope) ||
        _deletedSessions.contains(_sessionKey(scope, sid))) {
      return;
    }
    final source = _sources[genUiScopeKey(scope)]!;
    final gateway = source.gateway;
    if (gateway is! GenUiHistoryGateway) {
      throw const ProductException('Card history is unavailable.');
    }
    final key = _sessionKey(scope, sid);
    final revision = _revisions[key] ?? 0;
    final deadline = DateTime.now().add(const Duration(seconds: 10));
    final before = _recoverySnapshot(source, sid);
    // Loading a Paseo agent to read its history makes the helper send session
    // events, which bump the revision on every read. The agent staying idle on
    // the same prompt means no turn ran, so the read itself is still whole;
    // only publishing the view keeps the strict revision fence.
    bool settled() {
      final now = _recoverySnapshot(source, sid);
      return before?.idle == true &&
          before?.promptStamp != null &&
          now?.idle == true &&
          now?.promptStamp == before?.promptStamp;
    }

    bool live() =>
        wanted() &&
        available(scope) &&
        !_deletedSessions.contains(key) &&
        identical(_sources[genUiScopeKey(scope)], source) &&
        DateTime.now().isBefore(deadline);
    bool current() => live() && (_revisions[key] ?? 0) == revision;
    final messages = <MessageWithParts>[];
    String? cursor;
    var more = false;
    for (var page = 0; page < 2; page++) {
      final result = await (gateway as GenUiHistoryGateway).genUiHistoryPage(
        sid,
        cursor: cursor,
        limit: 50,
      );
      if (!current() && !(live() && settled())) return;
      messages.insertAll(0, result.items);
      more = result.hasMore;
      if (!more) break;
      if (result.olderCursor == null || result.olderCursor == cursor) break;
      cursor = result.olderCursor;
      // One page containing a user boundary covers asks in the current turn.
      // Pending older identities still get a second bounded page.
      final missing = journal
          .read(scope.profileID)
          .any(
            (e) =>
                e.scope == scope &&
                e.sessionID == sid &&
                e.endpoint == source.endpoint &&
                !messages.any((m) => m.info.id == e.messageID),
          );
      if (!missing && messages.any((m) => m.info.role == 'user')) break;
    }
    final missing = journal
        .read(scope.profileID)
        .any(
          (r) =>
              r.scope == scope &&
              r.sessionID == sid &&
              r.endpoint == source.endpoint &&
              !messages.any((m) => m.info.id == r.messageID),
        );
    final readComplete =
        !missing && !(more && !messages.any((m) => m.info.role == 'user'));
    // Coverage only for a read with no card in it at all: then nothing can
    // wait, nothing needs the journal, and skipping later reads hides nothing.
    // A chat that ever showed a card in its recent history is always re-read.
    // Any card tool counts, whatever its state: a running or half-written
    // one may complete after this read without a new prompt.
    final cardFree = !messages.any(
      (m) => m.parts.any(
        (part) =>
            part.type == 'tool' &&
            AgentToolAdapters.cardShowNames.contains(part.toolName),
      ),
    );
    Future<void> cover() async {
      final stamp = before?.promptStamp;
      if (!readComplete || !cardFree || !live() || stamp == null) return;
      if (!settled()) return;
      await _covered.remember(
        scope.profileID,
        _coverageIdentity(source, sid),
        stamp,
      );
    }

    if (!current()) {
      // A newer view may exist; never publish over it. A whole, card-free read
      // of an agent that ran no turn still settles this session.
      if (live() && settled() && readComplete && cardFree) {
        _coveragePending.remove(key);
        await cover();
      }
      return;
    }
    if (!readComplete) {
      recoveryIncomplete = true;
      _trackCoverage(key);
    } else {
      _coveragePending.remove(key);
    }
    observe(scope, sid, messages, tailComplete: true);
    await journal.drained;
    // Explicit recoverOne calls stay unconditional (answer reconciliation);
    // coverage never covers a running turn, a truncated tail, a superseded
    // source, a changed prompt or a read that held a card.
    await cover();
  }

  ({String? promptStamp, bool idle})? _recoverySnapshot(
    _Source source,
    String sid,
  ) {
    final gateway = source.gateway;
    if (!source.scope.sourceId.startsWith('paseo:') ||
        gateway is! GenUiRecoverySnapshotGateway) {
      return null;
    }
    return (gateway as GenUiRecoverySnapshotGateway).genUiRecoverySnapshot(sid);
  }

  String _coverageIdentity(_Source source, String sid) =>
      jsonEncode([source.endpoint, genUiScopeKey(source.scope), sid]);

  /// False when reading [target]'s history would wake a stored agent.
  /// Background recovery skips those; answering a card still reads them.
  bool _mayRead(GenUiTarget target) {
    final gateway = _sources[genUiScopeKey(target.scope)]?.gateway;
    return gateway is! GenUiLoadGuardGateway ||
        (gateway as GenUiLoadGuardGateway).genUiMayLoad(target.sessionID);
  }

  bool _needsRecovery(GenUiTarget target) {
    final source = _sources[genUiScopeKey(target.scope)];
    if (source == null) return true;
    if (!_mayRead(target)) return false;
    final snapshot = _recoverySnapshot(source, target.sessionID);
    final stamp = snapshot?.promptStamp;
    if (snapshot?.idle != true || stamp == null) return true;
    // Journaled cards and uncertain answers must always be reconciled, even
    // when their turn's prompt was previously covered.
    try {
      if (journal
          .read(target.scope.profileID)
          .any(
            (entry) =>
                entry.scope == target.scope &&
                entry.sessionID == target.sessionID &&
                entry.endpoint == source.endpoint,
          )) {
        return true;
      }
    } catch (_) {
      return true;
    }
    return !_covered.contains(
      target.scope.profileID,
      _coverageIdentity(source, target.sessionID),
      stamp,
    );
  }

  void _trackCoverage(String key) {
    if (_coveragePending.length < 100) {
      _coveragePending.add(key);
    } else if (!_coveragePending.contains(key)) {
      _coverageOverflow = true;
    }
  }

  Future<void> recover(Iterable<GenUiTarget> recent) {
    for (final target in recent) {
      if (!available(target.scope)) continue;
      if (!_needsRecovery(target)) {
        _coveragePending.remove(_sessionKey(target.scope, target.sessionID));
        continue;
      }
      _trackCoverage(_sessionKey(target.scope, target.sessionID));
      if (_queuedRecovery.length >= 100) {
        recoveryIncomplete = true;
        break;
      }
      _queuedRecovery[_sessionKey(target.scope, target.sessionID)] = target;
    }
    if (_recoveryPass != null) return _recoveryPass!;
    late final Future<void> pass;
    pass = _drainRecovery().whenComplete(() {
      if (identical(_recoveryPass, pass)) _recoveryPass = null;
      if (!_disposed && _queuedRecovery.isNotEmpty) {
        _recoveryFollowup?.cancel();
        _recoveryFollowup = Timer(const Duration(milliseconds: 350), () {
          _recoveryFollowup = null;
          if (!_disposed) unawaited(recover(const []));
        });
      }
    });
    _recoveryPass = pass;
    return pass;
  }

  Future<void> _drainRecovery() async {
    // Each pass has its own hard budget. New event hints are coalesced into
    // one deferred followup, so a last event during a read is not lost.
    final recent = _queuedRecovery.values.toList();
    _queuedRecovery.clear();
    await _recoverPass(recent);
    if (_queuedRecovery.isNotEmpty) recoveryIncomplete = true;
    _notify();
  }

  Future<void> _recoverPass(Iterable<GenUiTarget> recent) async {
    final targets = <String, GenUiTarget>{};
    final unread = <GenUiTarget>[];
    var failed = false;
    for (final source in _sources.values.toList()) {
      if (!available(source.scope)) continue;
      try {
        for (final entry in journal.read(source.scope.profileID)) {
          if (entry.scope == source.scope &&
              entry.endpoint == source.endpoint) {
            final t = GenUiTarget(entry.scope, entry.sessionID);
            if (!_mayRead(t)) continue;
            targets[_sessionKey(t.scope, t.sessionID)] = t;
          }
        }
      } catch (_) {
        failed = true;
        recoveryIncomplete = true;
      }
    }
    for (final t in recent) {
      if (!_needsRecovery(t)) {
        _coveragePending.remove(_sessionKey(t.scope, t.sessionID));
        continue;
      }
      targets.putIfAbsent(_sessionKey(t.scope, t.sessionID), () => t);
    }
    for (final key in targets.keys) {
      _trackCoverage(key);
    }
    if (targets.length > 20) recoveryIncomplete = true;
    final deadline = DateTime.now().add(const Duration(seconds: 60));
    for (final target in targets.values.take(20)) {
      if (_disposed || DateTime.now().isAfter(deadline)) {
        recoveryIncomplete = true;
        break;
      }
      try {
        await recoverOne(target).timeout(deadline.difference(DateTime.now()));
      } catch (_) {
        failed = true;
        recoveryIncomplete = true;
        unread.add(target);
      }
    }
    recoveryIncomplete =
        failed || _coverageOverflow || _coveragePending.isNotEmpty;
    _retryUnread(unread);
    _notify();
  }

  /// Reads that failed in a row; each retry waits a little longer.
  int _failedPasses = 0;

  /// Reads [unread] again a little later. A conversation the agent has just
  /// started has no history to read for its first seconds, and a quiet one
  /// sends no event that would ask again.
  void _retryUnread(List<GenUiTarget> unread) {
    if (unread.isEmpty) {
      _failedPasses = 0;
      return;
    }
    if (_disposed || ++_failedPasses > 5) return;
    _recoveryFollowup?.cancel();
    _recoveryFollowup = Timer(Duration(seconds: 2 * _failedPasses), () {
      _recoveryFollowup = null;
      if (!_disposed) unawaited(recover(unread));
    });
  }

  Future<void> answer(
    GenUiCard card,
    GenUiAnswer answer,
    List<PromptAttachment> attachments,
  ) {
    final existing = _sends[card.identity];
    if (existing != null) return existing;
    try {
      _requireWaiting(card);
      genUiAnswerText(
        card,
        answer,
      ); // validates before starting the Undo window
      _validateAttachments(card, answer, attachments);
      if (delivery(card) == GenUiDeliveryState.deliveryUnknown) {
        throw const ProductException(
          'Check the conversation before sending this answer again.',
        );
      }
    } catch (error, stack) {
      return Future.error(error, stack);
    }
    final done = Completer<void>();
    _held[card.identity] = done;
    _delivery[card.identity] = GenUiDeliveryState.held;
    final copy = List<PromptAttachment>.unmodifiable(attachments);
    late final Future<void> operation;
    operation = done.future.whenComplete(() {
      if (identical(_sends[card.identity], operation)) {
        _sends.remove(card.identity);
      }
    });
    _sends[card.identity] = operation;
    delayedAnswers.hold(
      'genui:${card.identity}',
      label: 'Card answer',
      send: () async {
        if (!identical(_held.remove(card.identity), done)) return;
        try {
          await _send(card, answer, copy);
          if (!done.isCompleted) done.complete();
        } catch (error, stack) {
          if (available(card.scope) &&
              !_deletedSessions.contains(
                _sessionKey(card.scope, card.sessionID),
              ) &&
              delivery(card) != GenUiDeliveryState.deliveryUnknown) {
            _delivery[card.identity] = GenUiDeliveryState.failed;
          }
          _notify();
          if (!done.isCompleted) done.completeError(error, stack);
        }
      },
    );
    _notify();
    return operation;
  }

  void undo(GenUiCard card) => _undo(card.identity);
  void _undo(String identity) {
    final done = _held.remove(identity);
    if (done == null) return;
    delayedAnswers.undo('genui:$identity');
    _delivery.remove(identity);
    if (!done.isCompleted) done.complete();
    _notify();
  }

  void _requireWaiting(GenUiCard card) {
    if (!available(card.scope) || state(card) != GenUiCardState.waiting) {
      throw const ProductException(
        'This card changed. Refresh the conversation.',
      );
    }
  }

  void _validateAttachments(
    GenUiCard card,
    GenUiAnswer answer,
    List<PromptAttachment> attachments,
  ) {
    if (answer is! GenUiPhotoAnswer) {
      if (attachments.isNotEmpty) {
        throw const ProductException('This answer does not accept photos.');
      }
      return;
    }
    final source = _sources[genUiScopeKey(card.scope)];
    if (source?.gateway.capabilities.promptAttachments != true ||
        attachments.length != answer.count) {
      throw const ProductException(
        'These photos cannot be sent to this agent.',
      );
    }
    var bytes = 0;
    for (final file in attachments) {
      const allowed = {'image/png', 'image/jpeg', 'image/gif', 'image/webp'};
      if (!allowed.contains(file.mime) || file.url.length > 14 * 1024 * 1024) {
        throw const ProductException('Choose a supported photo under 10 MB.');
      }
      try {
        final data = UriData.parse(file.url);
        if (data.mimeType != file.mime || !data.isBase64) {
          throw const FormatException();
        }
        final count = data.contentAsBytes().length;
        if (count == 0 || count > 10 * 1024 * 1024) {
          throw const FormatException();
        }
        bytes += count;
      } catch (_) {
        throw const ProductException('Choose a supported photo under 10 MB.');
      }
    }
    if (bytes > 20 * 1024 * 1024) {
      throw const ProductException('These photos are larger than 20 MB.');
    }
  }

  Future<void> _send(
    GenUiCard card,
    GenUiAnswer answer,
    List<PromptAttachment> attachments,
  ) async {
    _requireWaiting(card);
    final source = _sources[genUiScopeKey(card.scope)]!;
    _delivery[card.identity] = GenUiDeliveryState.sending;
    _notify();
    await recoverOne(GenUiTarget(card.scope, card.sessionID));
    _requireWaiting(card);
    final history = source.gateway;
    final idle =
        history is GenUiHistoryGateway &&
        await (history as GenUiHistoryGateway).genUiSessionIdle(card.sessionID);
    _requireWaiting(card);
    if (!identical(_sources[genUiScopeKey(card.scope)], source) || !idle) {
      throw const ProductException(
        'Wait for this agent to finish before answering.',
      );
    }
    if (journal
        .read(card.scope.profileID)
        .any((e) => e.identity == card.identity && e.dispatchID != null)) {
      throw const ProductException(
        'Check the conversation before sending this answer again.',
      );
    }
    final text = genUiAnswerText(card, answer);
    final gateway = source.gateway;
    final correlation = gateway is CorrelatedPromptGateway
        ? (gateway as CorrelatedPromptGateway).createPromptMessageID()
        : 'card-${DateTime.now().microsecondsSinceEpoch}-${++_dispatchSerial}';
    final dispatch = GenUiReference.card(
      card,
      source.endpoint,
      dispatchID: correlation,
    );
    await journal.put(dispatch);
    var dispatched = false;
    bool stillOwned() =>
        !_disposed &&
        !_closedProfiles.contains(card.scope.profileID) &&
        !_deletedSessions.contains(_sessionKey(card.scope, card.sessionID)) &&
        identical(_sources[genUiScopeKey(card.scope)], source);
    void fence() {
      _requireWaiting(card);
      if (!identical(_sources[genUiScopeKey(card.scope)], source)) {
        throw const ProductException('The connection changed.');
      }
      dispatched = true;
    }

    // The Undo window, history read and journal write can outlive a phone
    // runtime ownership change. Ask the bound source immediately before IO.
    try {
      await source.beforeSend?.call();
      _requireWaiting(card);
      if (!identical(_sources[genUiScopeKey(card.scope)], source)) {
        throw const ProductException('The connection changed.');
      }
    } catch (_) {
      await journal.discardUnsent(dispatch);
      rethrow;
    }

    try {
      if (gateway is CorrelatedPromptGateway) {
        await (gateway as CorrelatedPromptGateway).promptWithMessageID(
          card.sessionID,
          messageID: correlation,
          text: text,
          attachments: attachments,
          beforeSend: fence,
        );
      } else {
        fence();
        await gateway.promptAsync(
          card.sessionID,
          text: text,
          attachments: attachments,
        );
      }
    } catch (_) {
      if (!dispatched) {
        await journal.discardUnsent(dispatch);
      } else if (stillOwned()) {
        _delivery[card.identity] = GenUiDeliveryState.deliveryUnknown;
      }
      throw const ProductException(
        'The answer is not confirmed. Check the conversation.',
      );
    }
    if (!stillOwned()) return;
    _delivery[card.identity] = GenUiDeliveryState.deliveryUnknown;
    stale(card.scope, card.sessionID);
    try {
      await recoverOne(GenUiTarget(card.scope, card.sessionID));
    } catch (_) {}
    _notify();
  }

  Future<void> removeSession(GenUiScope scope, String sessionID) async {
    final key = _sessionKey(scope, sessionID);
    _liveAnswers.removeWhere(
      (_, receipt) =>
          receipt.card.scope == scope && receipt.card.sessionID == sessionID,
    );
    _deletedSessions.add(key);
    _coveragePending.remove(key);
    _revisions[key] = (_revisions[key] ?? 0) + 1;
    final view = _views.remove(key);
    for (final result in view?.parsed.values ?? const <GenUiParse>[]) {
      if (result is GenUiParsed) {
        _undo(result.card.identity);
        _delivery.remove(result.card.identity);
      }
    }
    await journal.removeSession(scope, sessionID);
    _notify();
  }

  Future<void> closeProfile(String profile) async {
    _closedProfiles.add(profile);
    for (final source
        in _sources.values
            .where((s) => s.scope.profileID == profile)
            .toList()) {
      forgetSource(source.scope);
    }
    _delivery.removeWhere(
      (identity, _) => (jsonDecode(identity) as List).first == profile,
    );
    await Future.wait([
      journal.closeProfile(profile),
      _covered.closeProfile(profile),
    ]);
  }

  void reopenProfile(String profile) {
    _closedProfiles.remove(profile);
    journal.reopenProfile(profile);
    _covered.reopenProfile(profile);
  }

  @override
  void dispose() {
    if (_disposed) return;
    for (final identity in _held.keys.toList()) {
      _undo(identity);
    }
    _disposed = true;
    _recoveryFollowup?.cancel();
    _sources.clear();
    _views.clear();
    _liveAnswers.clear();
    super.dispose();
  }
}
