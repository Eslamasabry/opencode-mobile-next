import 'dart:async';
import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// What the app remembers about one tip or mini tutorial. Plain data.
@immutable
class TipRecord {
  const TipRecord({this.count = 0, this.lastShown, this.done = false});

  /// How many times it has been shown at its moment (replays from Help ›
  /// Tips are not counted).
  final int count;
  final DateTime? lastShown;

  /// The person used the thing it teaches (or finished or left the tour):
  /// it never shows again until "Show tips again".
  final bool done;

  TipRecord copyWith({int? count, DateTime? lastShown, bool? done}) =>
      TipRecord(
        count: count ?? this.count,
        lastShown: lastShown ?? this.lastShown,
        done: done ?? this.done,
      );

  Map<String, Object?> toJson() => {
    'count': count,
    if (lastShown != null) 'last': lastShown!.toUtc().toIso8601String(),
    if (done) 'done': true,
  };

  static TipRecord fromJson(Object? value) {
    if (value is! Map) return const TipRecord();
    final count = value['count'];
    return TipRecord(
      count: count is int && count > 0 ? count : 0,
      lastShown: DateTime.tryParse('${value['last']}'),
      done: value['done'] == true,
    );
  }
}

/// Why a tip may not show right now, as the host sees its own screen.
@immutable
class TipMoment {
  const TipMoment({
    this.questionWaiting = false,
    this.keyboardOverAnchor = false,
    this.onboarding = false,
  });

  /// A question or approval card waits for the person.
  final bool questionWaiting;

  /// The keyboard is up over the control the tip points at.
  final bool keyboardOverAnchor;

  /// An onboarding step is on screen.
  final bool onboarding;

  bool get blocked => questionWaiting || keyboardOverAnchor || onboarding;
}

/// The teaching system's rules, kept in one place so no screen can break
/// them (docs/ux-system/teaching.md):
/// - a tip shows at most [maxCount] times (default 2), and never again once
///   the person used the thing ([markUsed]);
/// - one tip at a time, app-wide, with a quiet [cooldown] between tips, and
///   at most one new tip per screen visit;
/// - never while a question card waits, the keyboard is up over the anchor,
///   or an onboarding step shows ([TipMoment]);
/// - stored app-wide as `oc.tips` (not per server); [reset] is "Show tips
///   again".
///
/// The app uses the one instance on `ConnectionController.tips`.
class TipController extends ChangeNotifier {
  TipController(this.preferences, {DateTime Function()? now})
    : _now = now ?? DateTime.now;

  static const storageKey = 'oc.tips';

  /// The quiet gap after one tip before another may show.
  static const cooldown = Duration(minutes: 2);

  static const defaultMaxCount = 2;

  final SharedPreferences preferences;
  final DateTime Function() _now;

  Map<String, TipRecord>? _records;
  String? _active;
  int _activePriority = 0;
  bool _activeReplay = false;
  DateTime? _lastShownAny;
  final _visitsWithTip = Expando<bool>('tip-visit');

  Map<String, TipRecord> _load() => _records ??= () {
    final records = <String, TipRecord>{};
    try {
      final value = jsonDecode(preferences.getString(storageKey) ?? '{}');
      if (value is Map) {
        for (final entry in value.entries) {
          records['${entry.key}'] = TipRecord.fromJson(entry.value);
        }
      }
    } catch (_) {
      // Unreadable state means "nothing shown"; a repeated tip is harmless.
    }
    return records;
  }();

  Future<void> _save() => preferences.setString(
    storageKey,
    jsonEncode({
      for (final entry in _load().entries) entry.key: entry.value.toJson(),
    }),
  );

  void _persist() => unawaited(_save().catchError((Object _) => false));

  /// The tip or tour on screen now, or null.
  String? get active => _active;

  /// Whether [id] is on screen now as a replay from Help › Tips.
  bool get activeIsReplay => _activeReplay;

  bool isActive(String id) => _active == id;

  TipRecord record(String id) => _load()[id] ?? const TipRecord();

  /// Whether [id] could still show at a future moment.
  bool due(String id, {int maxCount = defaultMaxCount}) {
    final record = this.record(id);
    return !record.done && record.count < maxCount;
  }

  /// Shows [id] when the rules allow it; returns whether it is now active.
  /// [visit] is the screen visit (its State, say): one new tip per visit.
  /// A higher [priority] replaces a lower one on screen; a refusal consumes
  /// nothing.
  bool offer(
    String id, {
    Object? visit,
    int maxCount = defaultMaxCount,
    int priority = 0,
    TipMoment moment = const TipMoment(),
  }) {
    // Asked again while it shows (a rebuild): still showing. A replay from
    // Help › Tips is not its moment.
    if (_active == id) return !_activeReplay;
    if (moment.blocked || !due(id, maxCount: maxCount)) return false;
    if (visit != null && _visitsWithTip[visit] == true) return false;
    if (_active != null && (_activeReplay || priority <= _activePriority)) {
      return false;
    }
    final now = _now();
    final last = _lastShownAny;
    if (_active == null && last != null && now.difference(last) < cooldown) {
      return false;
    }
    final records = _load();
    final record = records[id] ?? const TipRecord();
    records[id] = record.copyWith(count: record.count + 1, lastShown: now);
    _lastShownAny = now;
    if (visit != null) _visitsWithTip[visit] = true;
    _active = id;
    _activePriority = priority;
    _activeReplay = false;
    _persist();
    notifyListeners();
    return true;
  }

  /// "Show me" in Help › Tips: shows [id] now, whatever the rules say, and
  /// counts nothing.
  void replay(String id) {
    _active = id;
    _activeReplay = true;
    _activePriority = 1 << 30;
    notifyListeners();
  }

  /// The tip left the screen (it faded, was touched away, or its place
  /// closed). It still counts as shown.
  void dismiss(String id) {
    if (_active != id) return;
    _active = null;
    _activeReplay = false;
    _activePriority = 0;
    notifyListeners();
  }

  /// The person used what [id] teaches (or finished or left its tour): it
  /// never shows again, and leaves the screen if it is there.
  void markUsed(String id) {
    final records = _load();
    final record = records[id] ?? const TipRecord();
    final wasActive = _active == id;
    if (wasActive) {
      _active = null;
      _activeReplay = false;
      _activePriority = 0;
    }
    if (!record.done) {
      records[id] = record.copyWith(done: true);
      _persist();
    }
    if (wasActive) notifyListeners();
  }

  /// "Show tips again": every tip and tour may show again at its moment.
  Future<void> reset() async {
    _records = {};
    _lastShownAny = null;
    final hadActive = _active != null;
    _active = null;
    _activeReplay = false;
    _activePriority = 0;
    await preferences.remove(storageKey);
    if (hadActive) notifyListeners();
  }
}
