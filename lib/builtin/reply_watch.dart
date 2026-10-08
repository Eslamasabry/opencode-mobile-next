import 'dart:async';
import 'dart:convert';
import 'dart:math' as math;

import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../api/models.dart' show EventEnvelope;
import '../diagnostics/perf_trace.dart';
import '../state/connection.dart';
import 'builtin_linux.dart';
import 'builtin_server.dart' show builtinLinuxProvider, looksLikeInAppServer;

/// One reply, timed in the app from the server's events.
@immutable
class ReplyTiming {
  const ReplyTiming({
    required this.inApp,
    required this.total,
    required this.finishedAt,
    this.firstToken,
    this.serverFirstToken,
    this.model,
    this.failed = false,
  });

  /// Whether it ran on the in-app server.
  final bool inApp;

  /// From the prompt reaching the app back from the server (its user
  /// message) to the reply's first output arriving: what the person waits
  /// before anything moves. Null when the reply ended without any.
  final Duration? firstToken;

  /// The same wait by the server's own clock (user message created → first
  /// part started), without the trip to the app. Null when it did not say.
  final Duration? serverFirstToken;

  /// From the prompt to the reply's end.
  final Duration total;

  /// `provider/model` of the reply, when the server said.
  final String? model;

  /// The reply ended with an error.
  final bool failed;

  final DateTime finishedAt;

  Map<String, Object?> toJson() => {
    'inApp': inApp,
    'totalMs': total.inMilliseconds,
    'firstMs': firstToken?.inMilliseconds,
    'serverFirstMs': serverFirstToken?.inMilliseconds,
    'model': model,
    'failed': failed,
    'finishedAt': finishedAt.millisecondsSinceEpoch,
  };

  static ReplyTiming? fromJson(Object? json) {
    if (json is! Map) return null;
    final total = json['totalMs'];
    final finished = json['finishedAt'];
    if (total is! num || finished is! num) return null;
    Duration? ms(Object? v) =>
        v is num ? Duration(milliseconds: v.toInt()) : null;
    return ReplyTiming(
      inApp: json['inApp'] == true,
      total: Duration(milliseconds: total.toInt()),
      firstToken: ms(json['firstMs']),
      serverFirstToken: ms(json['serverFirstMs']),
      model: json['model']?.toString(),
      failed: json['failed'] == true,
      finishedAt: DateTime.fromMillisecondsSinceEpoch(finished.toInt()),
    );
  }
}

/// Where the last in-app reply timing is kept between launches (device-wide,
/// not per server: only the in-app server is ever timed for this row).
const replySpeedPreferenceKey = 'oc.replySpeed.inApp';

/// What [ReplyWatch] reads from the connection. The app passes
/// [ConnectionReplySource]; tests pass their own.
abstract class ReplySource implements Listenable {
  Stream<EventEnvelope> get events;

  /// Whether the connected server is the one inside the app.
  bool get onInAppServer;

  /// Sessions with a reply running now on the connected server.
  Set<String> get busySessions;
}

/// [ReplySource] over the app's [ConnectionController].
class ConnectionReplySource implements ReplySource {
  ConnectionReplySource(this.connection);

  final ConnectionController connection;

  @override
  Stream<EventEnvelope> get events => connection.events;

  @override
  bool get onInAppServer =>
      connection.hasConnectedServer && looksLikeInAppServer(connection.profile);

  @override
  Set<String> get busySessions => connection.busySessions;

  @override
  void addListener(VoidCallback listener) => connection.addListener(listener);

  @override
  void removeListener(VoidCallback listener) =>
      connection.removeListener(listener);
}

/// Watches replies on the connected server, for two things:
///
/// * **Keeping the phone awake** while a reply runs on the in-app server,
///   as Termux's wake lock does for a server there. Every hold ends by
///   itself after [hold]; it is renewed every [renewEvery] while a reply
///   still runs and released as soon as none does. One unbroken stretch is
///   held for at most [ceiling] (nothing in the app assumes unbounded
///   background time), then not again until the replies stop.
/// * **Timing** each reply (prompt → first output → end) into the
///   performance trace (`reply.first_token`, `reply.done`) and [lastInApp]
///   / [last] for This phone's reply speed.
class ReplyWatch extends ChangeNotifier {
  ReplyWatch({
    required Future<BuiltinWorkLeaseStatus> Function(
      String leaseId,
      bool on,
      Duration hold,
    )
    setChatLease,
    String? leaseId,
    int Function()? nowMicros,
    DateTime Function()? now,
    this.hold = const Duration(minutes: 10),
    this.renewEvery = const Duration(minutes: 5),
    this.ceiling = const Duration(hours: 6),
    Future<String?> Function()? loadLast,
    Future<void> Function(String json)? saveLast,
  }) : _setChatLease = setChatLease,
       _leaseId = leaseId ?? _newLeaseId(),
       _saveLast = saveLast,
       _nowMicros = nowMicros ?? (() => PerfTrace.nowMicros),
       _now = now ?? DateTime.now {
    if (loadLast != null) unawaited(_restore(loadLast));
  }

  /// The watch that holds the in-app server's wake lock through [linux];
  /// its last in-app reply timing survives a relaunch.
  factory ReplyWatch.forLinux(BuiltinLinux linux) => ReplyWatch(
    setChatLease: (leaseId, on, hold) =>
        linux.setChatWorkLease(leaseId: leaseId, on: on, hold: hold),
    loadLast: () async {
      final prefs = await SharedPreferences.getInstance();
      return prefs.getString(replySpeedPreferenceKey);
    },
    saveLast: (json) async {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString(replySpeedPreferenceKey, json);
    },
  );

  final Future<void> Function(String json)? _saveLast;

  Future<void> _restore(Future<String?> Function() load) async {
    try {
      final raw = await load();
      if (raw == null || _disposed || _lastInApp != null) return;
      final timing = ReplyTiming.fromJson(jsonDecode(raw));
      if (timing == null) return;
      _lastInApp = timing;
      notifyListeners();
    } catch (_) {
      // Nothing kept, or unreadable: the row simply waits for the next reply.
    }
  }

  static String _newLeaseId() {
    final random = math.Random.secure();
    return 'chat.${List.generate(16, (_) => random.nextInt(256).toRadixString(16).padLeft(2, '0')).join()}';
  }

  final Future<BuiltinWorkLeaseStatus> Function(
    String leaseId,
    bool on,
    Duration hold,
  )
  _setChatLease;
  final String _leaseId;
  final int Function() _nowMicros;
  final DateTime Function() _now;
  final Duration hold;
  final Duration renewEvery;
  final Duration ceiling;

  ReplySource? _source;
  StreamSubscription<EventEnvelope>? _events;
  bool _disposed = false;

  // --- keeping the phone awake ----------------------------------------------

  bool _wantAwake = false;
  bool _holding = false;
  bool _capped = false;

  /// Renewals in this stretch: the stretch's length in [renewEvery] steps,
  /// counted on the timer so a changed wall clock cannot stretch it.
  int _renewals = 0;
  int _leaseGeneration = 0;
  Timer? _renew;
  Future<void> _holdChain = Future.value();

  /// Whether the phone is kept awake for a running reply now.
  bool get holdingAwake => _holding;

  /// Whether the last unbroken stretch of replies reached [ceiling], so the
  /// phone was let sleep again although a reply still ran.
  bool get awakeCapped => _capped;

  // --- timing ---------------------------------------------------------------

  final _runs = <String, _Run>{};
  final _userMessages = <String>{}; // insertion-ordered
  final _assistantMessages = <String>{};
  static const _remembered = 400;

  ReplyTiming? _last;
  ReplyTiming? _lastInApp;

  /// The last reply on any server.
  ReplyTiming? get last => _last;

  /// The last reply on the in-app server.
  ReplyTiming? get lastInApp => _lastInApp;

  /// Starts watching [source]; once per watch.
  void attach(ReplySource source) {
    if (_source != null || _disposed) return;
    _source = source;
    source.addListener(_sourceChanged);
    _events = source.events.listen(_onEvent);
    _sourceChanged();
  }

  void _sourceChanged() {
    final source = _source;
    if (source == null || _disposed) return;
    final want = source.onInAppServer && source.busySessions.isNotEmpty;
    if (want == _wantAwake) return;
    _wantAwake = want;
    _leaseGeneration++;
    _renew?.cancel();
    _renew = null;
    if (want) {
      _renewals = 0;
      _capped = false;
      _setHold(true);
      _renew = Timer.periodic(renewEvery, (_) => _renewHold());
    } else {
      _setHold(false);
    }
  }

  void _renewHold() {
    if (!_wantAwake || _disposed || _capped) return;
    _renewals = _renew?.tick ?? _renewals + 1;
    if (renewEvery * _renewals >= ceiling) {
      _renew?.cancel();
      _renew = null;
      _capped = true;
      _leaseGeneration++;
      PerfTrace.mark('reply.awake_capped');
      _setHold(false);
      return;
    }
    _setHold(true);
  }

  /// Serialized: every logical off survives newer intents, so an old stretch
  /// cannot renew a replacement stretch or clear another owner's work.
  void _setHold(bool on) {
    final generation = _leaseGeneration;
    _holdChain = _holdChain.then((_) async {
      if (!on) {
        await _releaseLease();
        return;
      }
      if (!_currentLeaseIntent(generation)) return;
      final remaining = ceiling - renewEvery * _renewals;
      if (remaining <= Duration.zero) return;
      BuiltinWorkLeaseStatus status;
      try {
        status = await _setChatLease(
          _leaseId,
          true,
          hold < remaining ? hold : remaining,
        );
      } catch (_) {
        // A partial channel handoff may have acquired the lease. Drain only
        // this ID and keep later off/disposal work runnable after exceptions.
        await _releaseLease();
        if (_currentLeaseIntent(generation)) _stopRenewing();
        return;
      }
      if (!_currentLeaseIntent(generation)) {
        // Late acquisition after idle/disposal must never become visible or
        // survive just because the next queued off has not run yet.
        await _releaseLease();
        return;
      }
      if (status.capped) {
        _capped = true;
        _stopRenewing();
        PerfTrace.mark('reply.awake_capped');
        // Retain the native capped logical key until actual idle/disposal.
        // Releasing and reacquiring it here would reset its lifetime boundary.
        if (!_disposed) notifyListeners();
      } else if (!status.held) {
        await _releaseLease();
        if (_currentLeaseIntent(generation)) _stopRenewing();
      } else {
        _publishHeld(true);
      }
    });
  }

  bool _currentLeaseIntent(int generation) =>
      !_disposed && _wantAwake && !_capped && generation == _leaseGeneration;

  void _stopRenewing() {
    _renew?.cancel();
    _renew = null;
    _leaseGeneration++;
    _publishHeld(false);
  }

  void _publishHeld(bool held) {
    if (_holding == held) return;
    _holding = held;
    if (!_disposed) notifyListeners();
  }

  Future<void> _releaseLease() async {
    try {
      await _setChatLease(_leaseId, false, hold);
    } catch (_) {
      // Native expiration still bounds this lease if its release is unavailable.
    }
    _publishHeld(false);
  }

  void _onEvent(EventEnvelope event) {
    final props = event.properties;
    switch (event.type) {
      case 'message.updated':
        final info = props['info'];
        if (info is! Map) return;
        final id = info['id']?.toString();
        final sessionID = info['sessionID']?.toString();
        if (id == null || sessionID == null) return;
        if (info['role'] == 'user') {
          if (!_remember(_userMessages, id)) return;
          final current = _runs[sessionID];
          // A prompt queued behind a running reply belongs to that run.
          if (current != null &&
              (current.busy || current.firstMicros != null)) {
            return;
          }
          _pruneRuns();
          _runs[sessionID] = _Run(
            startMicros: _nowMicros(),
            inApp: _source?.onInAppServer ?? false,
            serverCreatedMs: _asInt(_map(info['time'])?['created']),
          );
        } else if (info['role'] == 'assistant') {
          _remember(_assistantMessages, id);
          final run = _runs[sessionID];
          if (run != null) run.model ??= _modelOf(info);
        }
      case 'message.part.updated':
        final part = props['part'];
        if (part is! Map) return;
        final type = part['type']?.toString();
        if (type != 'text' && type != 'reasoning' && type != 'tool') return;
        final sessionID = (part['sessionID'] ?? props['sessionID'])?.toString();
        final messageID = part['messageID']?.toString();
        if (sessionID == null || messageID == null) return;
        if (!_assistantMessages.contains(messageID)) return;
        final time = _map(part['time']) ?? _map(_map(part['state'])?['time']);
        _firstOutput(sessionID, serverStartMs: _asInt(time?['start']));
      case 'message.part.delta':
        final sessionID = props['sessionID']?.toString();
        final messageID = props['messageID']?.toString();
        if (sessionID == null || messageID == null) return;
        if (!_assistantMessages.contains(messageID)) return;
        _firstOutput(sessionID);
      case 'session.status':
        final sessionID = props['sessionID']?.toString();
        if (sessionID == null) return;
        final raw = props['status'];
        final status = raw is Map ? raw['type']?.toString() : raw?.toString();
        if (status == 'busy' || status == 'retry') {
          _runs[sessionID]?.busy = true;
        } else if (status == 'idle') {
          _finish(sessionID, failed: false);
        }
      case 'session.idle':
        final sessionID = props['sessionID']?.toString();
        if (sessionID != null) _finish(sessionID, failed: false);
      case 'session.error':
        final sessionID = props['sessionID']?.toString();
        if (sessionID != null) _finish(sessionID, failed: true);
    }
  }

  void _firstOutput(String sessionID, {int? serverStartMs}) {
    final run = _runs[sessionID];
    if (run == null || run.firstMicros != null) return;
    run.firstMicros = _nowMicros();
    final created = run.serverCreatedMs;
    if (created != null && serverStartMs != null && serverStartMs >= created) {
      run.serverFirst = Duration(milliseconds: serverStartMs - created);
    }
    PerfTrace.recordDuration(
      'reply.first_token',
      Duration(microseconds: run.firstMicros! - run.startMicros),
      attrs: {
        'server': run.inApp ? 'in-app' : 'other',
        if (run.serverFirst != null)
          'server_ms': run.serverFirst!.inMilliseconds,
        'model': ?run.model,
      },
    );
  }

  void _finish(String sessionID, {required bool failed}) {
    final run = _runs.remove(sessionID);
    if (run == null) return;
    final end = _nowMicros();
    final first = run.firstMicros;
    final timing = ReplyTiming(
      inApp: run.inApp,
      firstToken: first == null
          ? null
          : Duration(microseconds: first - run.startMicros),
      serverFirstToken: run.serverFirst,
      total: Duration(microseconds: end - run.startMicros),
      model: run.model,
      failed: failed,
      finishedAt: _now(),
    );
    PerfTrace.recordDuration(
      'reply.done',
      timing.total,
      attrs: {
        'server': run.inApp ? 'in-app' : 'other',
        if (timing.firstToken != null)
          'first_ms': timing.firstToken!.inMilliseconds,
        'model': ?run.model,
      },
      error: failed ? 'reply failed' : null,
    );
    _last = timing;
    if (run.inApp) {
      _lastInApp = timing;
      final save = _saveLast;
      if (save != null) {
        unawaited(
          Future<void>.sync(
            () => save(jsonEncode(timing.toJson())),
          ).catchError((Object _) {}),
        );
      }
    }
    if (!_disposed) notifyListeners();
  }

  /// Runs whose end never came (a lost connection) are dropped after an
  /// hour, so they cannot pile up or time a later reply.
  void _pruneRuns() {
    final now = _nowMicros();
    _runs.removeWhere(
      (_, run) => now - run.startMicros > Duration.microsecondsPerHour,
    );
  }

  static bool _remember(Set<String> set, String id) {
    if (!set.add(id)) return false;
    while (set.length > _remembered) {
      set.remove(set.first);
    }
    return true;
  }

  static Map<Object?, Object?>? _map(Object? value) =>
      value is Map ? value : null;

  static int? _asInt(Object? value) => value is num ? value.toInt() : null;

  static String? _modelOf(Map<Object?, Object?> info) {
    var provider = info['providerID']?.toString();
    var model = info['modelID']?.toString();
    final nested = _map(info['model']);
    if (nested != null) {
      provider ??= nested['providerID']?.toString();
      model ??= (nested['modelID'] ?? nested['id'])?.toString();
    }
    if (provider == null || model == null) return null;
    return PerfTrace.scrub('$provider/$model', limit: 60);
  }

  @override
  void dispose() {
    if (_disposed) return;
    _disposed = true;
    _wantAwake = false;
    _leaseGeneration++;
    _renew?.cancel();
    _source?.removeListener(_sourceChanged);
    unawaited(_events?.cancel());
    // Always close this logical ID, including an acquisition still in flight.
    _setHold(false);
    super.dispose();
  }
}

class _Run {
  _Run({required this.startMicros, required this.inApp, this.serverCreatedMs});

  final int startMicros;
  final bool inApp;
  final int? serverCreatedMs;
  bool busy = false;
  int? firstMicros;
  Duration? serverFirst;
  String? model;
}

/// One watch per app, attached to the connection by the app shell
/// (lib/main.dart); This phone reads its timings.
final replyWatchProvider = Provider<ReplyWatch>((ref) {
  final watch = ReplyWatch.forLinux(ref.watch(builtinLinuxProvider));
  ref.onDispose(watch.dispose);
  return watch;
});
