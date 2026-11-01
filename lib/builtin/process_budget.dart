/// The app's own budget for heavy work on this phone.
///
/// Android 12+ stops an app's child processes once there are more than 32
/// of them (the "phantom process" limit), the oldest first: the agent
/// helper, every Claude Code conversation and the background agents go with
/// one SIGKILL. And when the person turns that limit off (Developer
/// options, Android 14+), nothing stops the app from growing until the
/// phone overheats. So the app keeps its own count either way:
///
/// - Before heavy work starts (a new agent conversation, the AI Team, a
///   setup job), [ProcessBudgetGuard.admit] reads the live count and waits
///   while there is no room under [ProcessBudget.cap] (32, or
///   [ProcessBudget.liftedCap] once Android's limit is off: a little more,
///   never unlimited).
/// - It also waits while the phone is hot ([ThermalPolicy.hot], the same
///   rule that pauses the AI Team) or the battery is low and unplugged,
///   even with room to spare.
/// - Waiting work starts by itself when room frees up; nothing is started
///   that Android would kill.
///
/// The guard never stops anything that runs: it only holds what has not
/// started. What ends is cleaned up as before (the native orphan reaper and
/// each job's own stop).
library;

import 'dart:async';

import 'package:clock/clock.dart';
import 'package:flutter/foundation.dart';

import '../platform/thermal.dart';
import 'builtin_linux.dart';
import 'heat_tier.dart';
import 'phone_process_watch.dart';
import 'thermal_guard.dart';

/// Heavy work the budget admits, with about how many processes it adds as
/// it starts (its own and its first tools).
enum HeavyJob {
  /// A new conversation with an agent on this phone (Claude Code: the
  /// agent, its card helper and its first tool).
  agentSession(4),

  /// The AI Team engine and its first worker.
  aiTeam(6),

  /// A setup job: an install or an update of a component.
  setupJob(3),

  /// A Linux service the app starts on request.
  linuxService(2);

  const HeavyJob(this.processes);

  final int processes;
}

/// Why heavy work waits.
enum BudgetWait {
  /// Too many programs run already.
  room,

  /// The phone is hot.
  warm,

  /// The battery is low and the phone is not charging.
  battery,
}

/// The rules, with no platform in them.
@immutable
class ProcessBudget {
  const ProcessBudget._();

  /// The app's own ceiling while Android's limit is on: Android's 32.
  static const cap = PhoneProcessWatch.androidLimit;

  /// The ceiling once the person turned Android's limit off. A bit more
  /// room for bigger jobs; heat and the battery still hold work back.
  static const liftedCap = 48;

  /// Kept free under the ceiling for the tools of work already running (a
  /// build, a test run): new work never takes the last few.
  static const reserve = 4;

  /// At or under this battery level, unplugged, new heavy work waits.
  static const lowBattery = 20;

  static int ceiling({required bool limitLifted}) =>
      limitLifted ? liftedCap : cap;

  /// Whether [job] may start now. [running] counts the processes Android
  /// counts (the app's own excepted) plus what was admitted moments ago and
  /// does not show yet. Heat comes first, then the battery, then room.
  static BudgetWait? decide({
    required HeavyJob job,
    required int running,
    required bool limitLifted,
    ThermalReading thermal = ThermalReading.unknown,
    BatteryReading battery = BatteryReading.unknown,
    bool fullSpeed = false,
  }) {
    if (ThermalPolicy.hot(thermal)) return BudgetWait.warm;
    // Below the pause: a forecast near severe, or a moderate status, already
    // holds new heavy work (HeatTier.hold, perf audit S1). Always full
    // speed (Keep running) switches this step off; the pause above stays.
    if (!fullSpeed && HeatTierPolicy.decide(thermal: thermal).holdsNewWork) {
      return BudgetWait.warm;
    }
    final level = battery.level;
    if (level != null && level <= lowBattery && battery.charging == false) {
      return BudgetWait.battery;
    }
    final room = ceiling(limitLifted: limitLifted) - reserve - running;
    return job.processes <= room ? null : BudgetWait.room;
  }
}

/// One piece of work held by the budget, for the line that says why.
class BudgetWaiter {
  BudgetWaiter._(this.job, this.since);

  final HeavyJob job;
  final DateTime since;
  BudgetWait wait = BudgetWait.room;
  final _ready = Completer<void>();
}

/// The reason the budget gave up waiting for a caller.
class BudgetCancelled implements Exception {
  const BudgetCancelled();
}

/// Holds heavy work until the phone has room, is cool and has battery.
/// One per app ([instance]); tests make their own with fakes.
class ProcessBudgetGuard extends ChangeNotifier {
  ProcessBudgetGuard({
    Future<int?> Function()? count,
    Future<ThermalReading> Function()? thermal,
    Future<BatteryReading> Function()? battery,
    Future<bool> Function()? limitLifted,
    Future<int> Function()? agents,
    bool? enabled,
    this.pollEvery = const Duration(seconds: 5),
    this.reservationLife = const Duration(seconds: 45),
  }) : _count = count ?? _liveCount,
       _thermal = thermal ?? ThermalBridge().current,
       _battery = battery ?? ThermalBridge().battery,
       _limitLifted = limitLifted ?? _limitOn,
       _agents = agents ?? _liveAgents,
       _enabled = enabled;

  /// The app's guard: holds nothing until [install] runs at app start
  /// (tests and widget galleries never wait on a phone they do not have).
  static ProcessBudgetGuard instance = ProcessBudgetGuard(enabled: false);

  /// Called once from `main`: the real guard, on where there is an in-app
  /// Linux to count.
  static void install({Future<bool> Function()? limitLifted}) {
    instance = ProcessBudgetGuard();
    if (limitLifted != null) limitLiftedReader = limitLifted;
  }

  /// Where the live count comes from: the app's one process watch.
  static PhoneProcessWatch get watch => PhoneProcessWatch.shared;

  /// Whether Android's limit is off; set at start-up from the
  /// Developer-options reader. Off (the limit applies) until known.
  static Future<bool> Function() limitLiftedReader = () async => false;

  /// "Always full speed" (Keep running); set by the thermal guard's start.
  static bool Function() fullSpeedReader = () => false;

  static Future<int?> _liveCount() async => (await watch.sample()).childCount;

  static Future<bool> _limitOn() => limitLiftedReader();

  static Future<int> _liveAgents() async {
    final reading = watch.last ?? await watch.sample();
    return reading.groups
        .where((g) => g.kind == PhoneProcessGroupKind.claudeCode)
        .length;
  }

  final Future<int?> Function() _count;
  final Future<ThermalReading> Function() _thermal;
  final Future<BatteryReading> Function() _battery;
  final Future<bool> Function() _limitLifted;
  final Future<int> Function() _agents;
  final bool? _enabled;

  /// How often the guard looks again while something waits.
  final Duration pollEvery;

  /// How long an admitted job's processes count before they show in a
  /// reading: two starts in a row must not both take the last room.
  final Duration reservationLife;

  final _waiting = <BudgetWaiter>[];
  final _reservations = <(DateTime, int)>[];
  final _roomWaits = <DateTime>[];
  Timer? _timer;
  bool _checking = false;

  /// The last count read, its ceiling, and how many agent conversations
  /// ran then: the words of the waiting line.
  int? lastCount;
  int lastCeiling = ProcessBudget.cap;
  int runningAgents = 0;

  bool get _on => _enabled ?? BuiltinLinux.supported;

  /// Whether the budget can hold work here (false where there is no in-app
  /// Linux to count): callers skip the wait entirely, not even a microtask.
  bool get active => _on;

  /// What waits now, oldest first.
  List<BudgetWaiter> get waiting => List.unmodifiable(_waiting);

  /// The most pressing reason anything waits, null when nothing does.
  BudgetWait? get wait {
    if (_waiting.isEmpty) return null;
    final reasons = _waiting.map((w) => w.wait).toSet();
    for (final reason in BudgetWait.values.reversed) {
      if (reasons.contains(reason)) return reason;
    }
    return null;
  }

  /// The budget held work for room at least twice in the last hour: the
  /// phone keeps running into Android's limit (the Developer-options card
  /// may help). Heat and the battery do not count.
  bool get queuesOften {
    final since = clock.now().subtract(const Duration(hours: 1));
    return _roomWaits.where((at) => at.isAfter(since)).length >= 2;
  }

  /// Waits until [job] may start, then returns. Starts at once when the
  /// phone cannot be read (no in-app Linux, an older build): the budget
  /// never blocks work it cannot measure. Throws [BudgetCancelled] when
  /// [cancelled] completes first.
  Future<void> admit(HeavyJob job, {Future<void>? cancelled}) async {
    if (!_on) return;
    final now = clock.now();
    final waiter = BudgetWaiter._(job, now);
    final first = await _decide(job);
    if (first == null) {
      _reserve(job);
      return;
    }
    waiter.wait = first;
    if (first == BudgetWait.room) _roomWaits.add(now);
    _waiting.add(waiter);
    notifyListeners();
    _schedule();
    if (cancelled != null) {
      unawaited(
        cancelled.then((_) {
          if (_waiting.remove(waiter)) {
            notifyListeners();
            if (!waiter._ready.isCompleted) {
              waiter._ready.completeError(const BudgetCancelled());
            }
          }
        }),
      );
    }
    await waiter._ready.future;
  }

  void _reserve(HeavyJob job) {
    _reservations.add((clock.now(), job.processes));
  }

  int get _reserved {
    final since = clock.now().subtract(reservationLife);
    _reservations.removeWhere((r) => !r.$1.isAfter(since));
    return _reservations.fold(0, (sum, r) => sum + r.$2);
  }

  Future<BudgetWait?> _decide(HeavyJob job) async {
    int? count;
    var lifted = false;
    var thermal = ThermalReading.unknown;
    var battery = BatteryReading.unknown;
    try {
      count = await _count();
    } on Object {
      count = null;
    }
    // Unreadable: start; the budget never blocks work it cannot measure.
    if (count == null) return null;
    try {
      lifted = await _limitLifted();
    } on Object {
      lifted = false;
    }
    try {
      thermal = await _thermal();
    } on Object {
      thermal = ThermalReading.unknown;
    }
    try {
      battery = await _battery();
    } on Object {
      battery = BatteryReading.unknown;
    }
    lastCount = count;
    lastCeiling = ProcessBudget.ceiling(limitLifted: lifted);
    final decision = ProcessBudget.decide(
      job: job,
      running: count + _reserved,
      limitLifted: lifted,
      thermal: thermal,
      battery: battery,
      fullSpeed: fullSpeedReader(),
    );
    if (decision == BudgetWait.room) {
      try {
        runningAgents = await _agents();
      } on Object {
        runningAgents = 0;
      }
    }
    return decision;
  }

  void _schedule() {
    _timer ??= Timer.periodic(pollEvery, (_) => unawaited(check()));
  }

  /// Looks again now; starts what fits, oldest first. Public for tests.
  Future<void> check() async {
    if (_checking) return;
    _checking = true;
    try {
      var changed = false;
      for (final waiter in List.of(_waiting)) {
        final decision = await _decide(waiter.job);
        if (!_waiting.contains(waiter)) continue;
        if (decision == null) {
          _waiting.remove(waiter);
          _reserve(waiter.job);
          if (!waiter._ready.isCompleted) waiter._ready.complete();
          changed = true;
        } else if (decision != waiter.wait) {
          waiter.wait = decision;
          changed = true;
        } else {
          // Oldest first: a younger job never jumps a waiting older one.
          break;
        }
      }
      if (changed) notifyListeners();
    } finally {
      _checking = false;
      if (_waiting.isEmpty) {
        _timer?.cancel();
        _timer = null;
      }
    }
  }

  @override
  void dispose() {
    _timer?.cancel();
    for (final waiter in _waiting) {
      if (!waiter._ready.isCompleted) {
        waiter._ready.completeError(const BudgetCancelled());
      }
    }
    _waiting.clear();
    super.dispose();
  }
}

/// When Android last stopped one of the app's Linux programs by itself (a
/// SIGKILL nobody asked for: its process limit, or memory) within [within],
/// from the native service records; null when it did not.
DateTime? lastAndroidKill(
  BuiltinPerformance performance, {
  Duration within = const Duration(hours: 24),
  DateTime? now,
}) {
  final since = (now ?? clock.now()).subtract(within);
  DateTime? latest;
  for (final record in performance.services.values) {
    final at = record.lastExitAtMs;
    if (at == null ||
        record.lastStopRequested != false ||
        record.exitReason != BuiltinServiceExitReason.memoryOrPhantomKill) {
      continue;
    }
    final when = DateTime.fromMillisecondsSinceEpoch(at, isUtc: true);
    if (when.isBefore(since)) continue;
    if (latest == null || when.isAfter(latest)) latest = when;
  }
  return latest;
}
