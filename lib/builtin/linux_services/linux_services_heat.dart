/// The heat guard for Linux services (owner decision Q3, 2026-10-31): at
/// CRITICAL or worse every service is held off; once the phone has stayed
/// cool for [ThermalPolicy]'s wait, the hold goes and the services the
/// person keeps on start again. The same thresholds and the same wait as
/// the AI Team's guard ([ThermalPolicy.veryHot], [ThermalPolicy.cool]), fed
/// by the guard's one native listener (`onReading`).
///
/// The hold is a file the supervisor reads, so it outlives the app: a
/// process that died while the phone was hot finds it again and gives it
/// back once cool.
library;

import 'dart:async';

import '../../platform/thermal.dart';
import '../thermal_guard.dart' show ThermalPolicy;

class LinuxServicesHeat {
  LinuxServicesHeat({
    required this.readHold,
    required this.setHold,
    required this.enabled,
    this.coolFor = const Duration(minutes: 2),
    DateTime Function()? clock,
  }) : _clock = clock ?? DateTime.now;

  /// Whether the services are held off now (an earlier process may have).
  final Future<bool> Function() readHold;
  final Future<void> Function(bool held) setHold;

  /// The heat guard's setting; off, nothing new is held, but a hold is
  /// still given back once cool.
  final bool Function() enabled;
  final Duration coolFor;
  final DateTime Function() _clock;

  bool? _held;
  DateTime? _coolSince;
  Future<void> _queue = Future.value();

  bool get held => _held ?? false;

  /// One reading; acts at most once at a time, in order.
  Future<void> observe(ThermalReading reading) =>
      _queue = _queue.then((_) => _observe(reading)).catchError((Object _) {});

  Future<void> _observe(ThermalReading reading) async {
    final now = _clock();
    final held = _held ??= await readHold();
    if (!held) {
      _coolSince = null;
      if (enabled() && ThermalPolicy.veryHot(reading)) {
        await setHold(true);
        _held = true;
      }
      return;
    }
    if (!ThermalPolicy.cool(reading)) {
      _coolSince = null;
      return;
    }
    final since = _coolSince ??= now;
    if (now.difference(since) >= coolFor) {
      await setHold(false);
      _held = false;
      _coolSince = null;
    }
  }
}
