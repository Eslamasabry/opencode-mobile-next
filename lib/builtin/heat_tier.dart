/// Easing the phone's agents before it is hot (perf audit 2026-11-01,
/// slice S1, the Dart half).
///
/// [ThermalPolicy] pauses the AI Team when Android says SEVERE. By then the
/// chassis is hot and the CPU has been throttled for a while, and the agents
/// that cause most of the heat now (Claude Code and its test runs) are not
/// touched at all. This adds two gentler steps below it:
///
/// - [HeatTier.slow]: the agents' programs run at a lower priority, so the
///   app and the phone's own chat stay smooth and the work costs less
///   heat per second. Nothing is stopped; replies keep streaming.
/// - [HeatTier.hold]: the same, and new heavy work waits (the process
///   budget asks [HeatTier.holdsNewWork]).
///
/// A tier is chosen from Android's thermal status and 30-second forecast,
/// the battery (low and unplugged), and whether the app is out of sight.
/// "Always full speed" switches the lowering off; the protective pause at
/// SEVERE stays on either way.
library;

import 'dart:async';
import 'dart:io';

import 'package:flutter/foundation.dart';

import '../diagnostics/perf_trace.dart';
import '../platform/app_visibility.dart';
import '../platform/thermal.dart';
import 'builtin_linux.dart';

enum HeatTier {
  /// Full speed.
  normal,

  /// Agent programs at lower priority.
  slow,

  /// Lower priority, and new heavy work waits.
  hold;

  bool get slowsAgents => this != normal;
  bool get holdsNewWork => this == hold;
}

/// The rules, with no platform in them (unit-tested on their own).
@immutable
class HeatTierPolicy {
  const HeatTierPolicy._();

  /// The forecast at or above which agents are slowed / new work waits. The
  /// forecast reads 1.0 where Android throttles hard.
  static const slowHeadroom = 0.7;
  static const holdHeadroom = 0.9;

  /// Unplugged and at or under this level: slow / hold.
  static const slowBattery = 20;
  static const holdBattery = 10;

  /// How long the readings must stay clear of a step before it is left: a
  /// hot reading in between starts the wait again, so it never flaps.
  static const calmFor = Duration(minutes: 2);

  /// The raw tier for these readings. [batteryLevel] and [charging] are
  /// null when unknown (then the battery does not count). [hidden]: the app
  /// is out of sight, where agent tools may run slower for less battery.
  static HeatTier decide({
    required ThermalReading thermal,
    int? batteryLevel,
    bool? charging,
    bool hidden = false,
    bool fullSpeed = false,
    bool powerSave = false,
  }) {
    if (fullSpeed) return HeatTier.normal;
    final headroom = thermal.headroom ?? 0;
    final unplugged = charging == false;
    final level = batteryLevel;
    if (thermal.status.atLeast(ThermalStatus.moderate) ||
        headroom >= holdHeadroom ||
        (unplugged && level != null && level <= holdBattery)) {
      return HeatTier.hold;
    }
    if (thermal.status.atLeast(ThermalStatus.light) ||
        headroom >= slowHeadroom ||
        (unplugged && level != null && level <= slowBattery) ||
        powerSave ||
        hidden) {
      return HeatTier.slow;
    }
    return HeatTier.normal;
  }
}

/// The battery now, from the kernel's power-supply files (no native call,
/// so it works in this build). Unknown values stay null.
class BatterySnapshot {
  const BatterySnapshot({this.level, this.charging});

  static const unknown = BatterySnapshot();

  final int? level;
  final bool? charging;

  static BatterySnapshot fromSysfs(String? capacity, String? status) {
    final level = int.tryParse((capacity ?? '').trim());
    final state = (status ?? '').trim().toLowerCase();
    return BatterySnapshot(
      level: level != null && level >= 0 && level <= 100 ? level : null,
      charging: switch (state) {
        'charging' || 'full' => true,
        'discharging' || 'not charging' => false,
        _ => null,
      },
    );
  }

  static Future<BatterySnapshot> read() async {
    Future<String?> file(String name) async {
      try {
        return await File(
          '/sys/class/power_supply/battery/$name',
        ).readAsString().timeout(const Duration(seconds: 1));
      } on Object {
        return null;
      }
    }

    if (kIsWeb) return unknown;
    return fromSysfs(await file('capacity'), await file('status'));
  }
}

/// A script that sets the CPU priority ("nice" value [nice]) of every
/// Paseo helper's programs and of the proot tracer under them, inside the
/// agent view of Ubuntu. Programs started later inherit it.
///
/// Reads `/proc` only; changes only priorities, never signals a process.
/// Prints the number of threads it set (or `none`).
String agentNiceScript(int nice) {
  assert(nice >= 0 && nice <= 19);
  return '''
nice_value=$nice
roots=
for dir in /proc/[0-9]*; do
  pid=\${dir#/proc/}
  [ "\$pid" = "\$\$" ] && continue
  cmd=\$(tr '\\0' ' ' < "\$dir/cmdline" 2>/dev/null) || continue
  case "\$cmd" in
    "sh -c"*|"/bin/sh -c"*) continue ;;
    *"paseo daemon run --home /home/oc/.oc-profiles/"*) roots="\$roots \$pid" ;;
  esac
done
[ -n "\$roots" ] || { echo none; exit 0; }
set=\$(
  for dir in /proc/[0-9]*; do
    [ -r "\$dir/stat" ] || continue
    read -r line < "\$dir/stat" 2>/dev/null || continue
    rest=\${line##*) }
    set -- \$rest
    echo "\${dir#/proc/} \$2 \${line#*(}"
  done | awk -v roots="\$roots" '
    BEGIN { n = split(roots, r, " "); for (i = 1; i <= n; i++) keep[r[i]] = 1 }
    { pp[\$1] = \$2; comm[\$1] = \$0; sub(/^[0-9]+ [0-9]+ /, "", comm[\$1]); sub(/\\).*\$/, "", comm[\$1]); ord[++m] = \$1 }
    END {
      for (round = 0; round < 8; round++)
        for (i = 1; i <= m; i++) { p = ord[i]; if (!(p in keep) && (pp[p] in keep)) keep[p] = 1 }
      for (p in keep) {
        print p
        q = pp[p]
        if (comm[q] ~ /proot/) print q
      }
    }' | sort -u
)
count=0
for pid in \$set; do
  for task in /proc/\$pid/task/*; do
    tid=\${task##*/}
    renice -n "\$nice_value" -p "\$tid" >/dev/null 2>&1 && count=\$((count + 1))
  done
done
echo "set \$count"
''';
}

/// Runs the priority script; replaced in tests. Answers how many threads
/// it set (0 when no agent helper runs), or null when it could not run.
typedef NiceRunner = Future<int?> Function(int nice);

/// Follows [HeatTierPolicy] and applies the result to the agents. One per
/// app, fed by the thermal guard's readings (no second native listener).
class AgentThrottle extends ChangeNotifier {
  AgentThrottle({
    required this.fullSpeed,
    NiceRunner? run,
    Future<BatterySnapshot> Function()? battery,
    ValueListenable<bool>? hidden,
    DateTime Function()? clock,
    PerfMark? mark,
    Future<bool> Function()? powerSave,
  }) : _powerSave = powerSave ?? ThermalBridge().powerSave,
       _run = run ?? _linuxRun,
       _battery = battery ?? _bridgeBattery,
       _hidden = hidden ?? AppVisibility.instance.hidden,
       _clock = clock ?? DateTime.now,
       _mark = mark ?? PerfTrace.mark {
    _hidden.addListener(_onHidden);
  }

  /// "Always full speed" (Keep running): read at every decision.
  final bool Function() fullSpeed;
  final NiceRunner _run;
  final Future<BatterySnapshot> Function() _battery;
  final ValueListenable<bool> _hidden;
  final DateTime Function() _clock;
  final PerfMark _mark;
  final Future<bool> Function() _powerSave;

  /// Whether the last run went through Android's background class.
  static bool usedNative = false;

  static const slowNice = 10;
  static const holdNice = 15;

  HeatTier _tier = HeatTier.normal;
  HeatTier _applied = HeatTier.normal;
  DateTime? _calmSince;
  ThermalReading _last = ThermalReading.unknown;
  Future<void> _queue = Future.value();
  bool _disposed = false;
  DateTime? _lastRun;

  /// While easing, the priorities are set again this often: a helper or a
  /// conversation started since inherits nothing from the app.
  static const reapplyEvery = Duration(minutes: 5);

  /// The tier in force.
  HeatTier get tier => _tier;

  /// The process budget's reading (native, else the kernel's files).
  static Future<BatterySnapshot> _bridgeBattery() async {
    final reading = await ThermalBridge().battery();
    return BatterySnapshot(level: reading.level, charging: reading.charging);
  }

  static Future<int?> _linuxRun(int nice) async {
    // Android's background class (this build's native side), else renice.
    final bridge = ThermalBridge();
    final level = switch (nice) {
      0 => 'normal',
      <= slowNice => 'slow',
      _ => 'hold',
    };
    final native = await bridge.agentPriority(level);
    final threads = native?['threads'];
    if (native != null && threads is num) {
      usedNative = true;
      return threads.toInt();
    }
    final result = await BuiltinLinux().runAgentSetupCheck(
      agentNiceScript(nice),
      timeout: const Duration(seconds: 30),
    );
    if (!result.ok) return null;
    final match = RegExp(r'set (\d+)').firstMatch(result.output);
    return match == null ? 0 : int.parse(match.group(1)!);
  }

  /// One reading from the thermal guard.
  Future<void> observe(ThermalReading reading) {
    _last = reading;
    return evaluate();
  }

  void _onHidden() => unawaited(evaluate());

  /// Decides again from the latest reading; one run at a time.
  Future<void> evaluate() =>
      _queue = _queue.then((_) => _evaluate()).catchError((Object _) {
        // Setting priorities is best effort; the next reading tries again.
      });

  Future<void> _evaluate() async {
    if (_disposed) return;
    final now = _clock();
    final battery = await _battery();
    final saver = await _powerSave();
    final wanted = HeatTierPolicy.decide(
      thermal: _last,
      batteryLevel: battery.level,
      charging: battery.charging,
      hidden: _hidden.value,
      fullSpeed: fullSpeed(),
      powerSave: saver,
    );
    // Going up is at once. Coming down waits for calm, so a reading that
    // flickers around a threshold does not flap the priorities.
    if (wanted.index >= _tier.index) {
      _tier = wanted;
      _calmSince = null;
    } else {
      final since = _calmSince ??= now;
      if (now.difference(since) >= HeatTierPolicy.calmFor ||
          wanted == HeatTier.normal && fullSpeed()) {
        _tier = wanted;
        _calmSince = null;
      }
    }
    final due =
        _tier != _applied ||
        (_tier.slowsAgents &&
            (_lastRun == null || now.difference(_lastRun!) >= reapplyEvery));
    if (!due) return;
    final nice = switch (_tier) {
      HeatTier.normal => 0,
      HeatTier.slow => slowNice,
      HeatTier.hold => holdNice,
    };
    final set = await _run(nice);
    _lastRun = now;
    if (_tier != _applied) {
      _mark(
        'heat.tier',
        attrs: {
          'tier': _tier.name,
          'threads': set,
          ..._last.traceAttrs,
          if (battery.level != null) 'battery': battery.level,
          'hidden': _hidden.value,
        },
      );
    }
    // A failed run is tried again at the next reading.
    if (set != null) _applied = _tier;
    if (!_disposed) notifyListeners();
  }

  @override
  void dispose() {
    _disposed = true;
    _hidden.removeListener(_onHidden);
    super.dispose();
  }
}

typedef PerfMark = void Function(String name, {Map<String, Object?> attrs});
