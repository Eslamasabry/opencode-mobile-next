/// What the Services pages share: the live list of services with the acts
/// that change it, the words for a service's state, and the words for a
/// problem. Pages talk to [LinuxServices] only (the frozen API); nothing
/// here knows about the supervisor.
library;

import 'dart:async';

import 'package:clock/clock.dart';
import 'package:flutter/material.dart';

import '../../../builtin/linux_services/linux_services.dart';
import '../../../l10n/app_localizations.dart';
import '../../app_iconography.dart';
import '../../kit/kit.dart';

/// What the person just asked for, until the supervisor shows it.
enum LinuxServiceAct { start, stop, restart, remove }

/// Whether a service is wanted on but needs the person: it gave up, or it
/// cannot get its port.
bool linuxServiceNeedsYou(LinuxServiceState s) =>
    s.phase == LinuxServicePhase.failed ||
    s.phase == LinuxServicePhase.portBusy;

/// The chip of a row: **Stop** while the service is kept running, **Start**
/// when it is off, finished, or gave up (a new Start is the way back).
bool linuxServiceShowsStop(LinuxServiceState s) =>
    s.on &&
    s.phase != LinuxServicePhase.failed &&
    s.phase != LinuxServicePhase.exited;

/// Wanted on but held off: the heat guard stops services when the phone is
/// too hot and starts them again when it cools.
bool linuxServicePaused(LinuxServiceState s) =>
    s.on && s.phase == LinuxServicePhase.off;

/// Urgency order (owner rule: no state sections): failed, port busy,
/// retrying, running, then off.
int _rank(LinuxServiceState s) => linuxServicePaused(s)
    ? 3
    : switch (s.phase) {
        LinuxServicePhase.failed => 0,
        LinuxServicePhase.portBusy => 1,
        LinuxServicePhase.backoff => 2,
        LinuxServicePhase.starting ||
        LinuxServicePhase.running ||
        LinuxServicePhase.stopping => 3,
        LinuxServicePhase.exited || LinuxServicePhase.off => 4,
      };

List<LinuxServiceState> linuxServicesByUrgency(List<LinuxServiceState> all) =>
    [...all]..sort((a, b) {
      final byRank = _rank(a).compareTo(_rank(b));
      if (byRank != 0) return byRank;
      return a.def.title.toLowerCase().compareTo(b.def.title.toLowerCase());
    });

/// The live list of services, and the acts on them. One per page; it reads
/// once, then follows [LinuxServices.watch] while it lives.
class LinuxServicesFeed extends ChangeNotifier {
  LinuxServicesFeed(this.api);

  final LinuxServices api;

  /// Null until the first answer.
  List<LinuxServiceState>? states;

  /// Why the list could not be read (nothing to show), or why a later
  /// refresh failed (the old list stays).
  Object? failure;

  /// An act that did not work, until the next one starts.
  Object? actFailure;

  bool loading = true;

  /// When the current load began, for the slow-wait words.
  DateTime? loadingSince;

  final Map<String, LinuxServiceAct> pending = {};

  StreamSubscription<List<LinuxServiceState>>? _sub;
  Timer? _ticker;
  bool _disposed = false;

  LinuxServiceState? byName(String name) {
    for (final s in states ?? const <LinuxServiceState>[]) {
      if (s.def.name == name) return s;
    }
    return null;
  }

  void _notify() {
    if (!_disposed) notifyListeners();
  }

  Future<void> load() async {
    // Not awaited: a stream's cancel may take its time, and the list is
    // read again meanwhile.
    unawaited(_sub?.cancel());
    _sub = null;
    loading = true;
    loadingSince = clock.now();
    failure = null;
    _notify();
    try {
      states = await api.read();
      if (_disposed) return;
      _listen();
      // A service that is on with no supervisor (it reads as starting) is
      // started now; nothing happens when all is well.
      unawaited(api.ensureSupervisor().catchError((Object _) {}));
    } catch (error) {
      failure = error;
    }
    loading = false;
    loadingSince = null;
    _syncTicker();
    _notify();
  }

  void _listen() {
    _sub = api.watch().listen(
      (list) {
        states = list;
        failure = null;
        _syncTicker();
        _notify();
      },
      onError: (Object error) {
        failure = error;
        _notify();
      },
    );
  }

  /// A one-second beat while a retry countdown is on screen.
  void _syncTicker() {
    final counting = (states ?? const <LinuxServiceState>[]).any(
      (s) => s.phase == LinuxServicePhase.backoff,
    );
    if (counting && _ticker == null) {
      _ticker = Timer.periodic(const Duration(seconds: 1), (_) => _notify());
    } else if (!counting) {
      _ticker?.cancel();
      _ticker = null;
    }
  }

  /// Shows [error] as the last act's failure (an act that ran elsewhere).
  void reportFailure(Object error) {
    actFailure = error;
    _notify();
  }

  /// Runs [act] on [name]. Returns whether it worked; if not, [actFailure]
  /// says why. The list is read again afterwards.
  Future<bool> run(String name, LinuxServiceAct act) async {
    if (pending.containsKey(name)) return false;
    pending[name] = act;
    actFailure = null;
    _notify();
    var ok = true;
    try {
      switch (act) {
        case LinuxServiceAct.start:
          await api.start(name);
        case LinuxServiceAct.stop:
          await api.stop(name);
        case LinuxServiceAct.restart:
          await api.restart(name);
        case LinuxServiceAct.remove:
          await api.remove(name);
      }
    } catch (error) {
      ok = false;
      actFailure = error;
    }
    try {
      if (!_disposed) states = await api.read();
    } catch (_) {
      // The watch brings the list back; the act's own result stands.
    }
    pending.remove(name);
    _syncTicker();
    _notify();
    return ok;
  }

  @override
  void dispose() {
    _disposed = true;
    unawaited(_sub?.cancel());
    _ticker?.cancel();
    super.dispose();
  }
}

/// A problem in plain words and a way forward. The technical text never
/// appears here; see [linuxServiceDetails].
String linuxServiceProblemText(AppLocalizations l10n, Object? error) {
  if (error is! LinuxServiceException) return l10n.linuxSvcProblemFailed;
  return switch (error.problem) {
    LinuxServiceProblem.linuxMissing => l10n.linuxSvcProblemMissing,
    LinuxServiceProblem.invalidName => l10n.linuxSvcErrNameShape,
    LinuxServiceProblem.nameTaken => l10n.linuxSvcErrNameTaken,
    LinuxServiceProblem.invalidCommand => l10n.linuxSvcErrCommand,
    LinuxServiceProblem.reservedPort => l10n.linuxSvcErrPortReserved,
    LinuxServiceProblem.portTaken => l10n.linuxSvcErrPortTaken,
    LinuxServiceProblem.tooMany => l10n.linuxSvcProblemTooMany,
    LinuxServiceProblem.supervisorDown => l10n.linuxSvcCouldNotStart,
    LinuxServiceProblem.writeFailed => l10n.linuxSvcProblemWrite,
  };
}

/// The body of a page that could not read the services: a runner that does
/// not answer has its own sentence, since the title already says it.
String linuxServiceLoadBody(AppLocalizations l10n, Object? error) =>
    error is LinuxServiceException &&
        error.problem == LinuxServiceProblem.supervisorDown
    ? l10n.linuxSvcDownBody
    : linuxServiceProblemText(l10n, error);

/// The redacted technical text of a problem, for a Details fold.
String? linuxServiceDetails(Object? error) {
  if (error is LinuxServiceException && error.details != null) {
    return KitRedact.text(error.details!);
  }
  return null;
}

/// The words for what a service is doing now. [act] is what the person just
/// asked for, which shows before the supervisor answers.
String linuxServiceStatus(
  BuildContext context,
  AppLocalizations l10n,
  LinuxServiceState s, {
  LinuxServiceAct? act,
}) {
  if (act != null) {
    return switch (act) {
      LinuxServiceAct.start || LinuxServiceAct.restart => l10n.linuxSvcStarting,
      LinuxServiceAct.stop => l10n.linuxSvcStopping,
      LinuxServiceAct.remove => l10n.linuxSvcRemoving,
    };
  }
  final at = s.lastExitAt;
  final time = at == null ? null : KitTime.clock(context, at);
  switch (s.phase) {
    case LinuxServicePhase.off:
      return s.on ? l10n.linuxSvcPaused : l10n.linuxSvcOff;
    case LinuxServicePhase.starting:
      return l10n.linuxSvcStarting;
    case LinuxServicePhase.stopping:
      return l10n.linuxSvcStopping;
    case LinuxServicePhase.running:
      final since = s.since;
      return since == null
          ? l10n.linuxSvcRunning
          : l10n.linuxSvcRunningFor(
              KitSince.durationLabel(context, clock.now().difference(since)),
            );
    case LinuxServicePhase.backoff:
      final next = s.nextTryAt;
      final seconds = next == null
          ? 0
          : (next.difference(clock.now()).inMilliseconds / 1000).ceil().clamp(
              0,
              3600,
            );
      return l10n.linuxSvcRetrying(s.lastExit ?? 1, seconds);
    case LinuxServicePhase.portBusy:
      return l10n.linuxSvcPortBusy('${s.def.port ?? ''}');
    case LinuxServicePhase.failed:
      return s.restarts > 0 && time != null
          ? l10n.linuxSvcGaveUp(s.restarts, time)
          : l10n.linuxSvcGaveUpNoCount(time ?? '');
    case LinuxServicePhase.exited:
      return l10n.linuxSvcExited(s.lastExit ?? 0);
  }
}

/// A row's supporting line: the state, then the port when it has one.
String linuxServiceRowLine(
  BuildContext context,
  AppLocalizations l10n,
  LinuxServiceState s, {
  LinuxServiceAct? act,
}) {
  final status = linuxServiceStatus(context, l10n, s, act: act);
  final port = s.def.port;
  final showPort =
      port != null &&
      act == null &&
      !linuxServicePaused(s) &&
      (s.phase == LinuxServicePhase.running ||
          s.phase == LinuxServicePhase.off ||
          s.phase == LinuxServicePhase.exited);
  return showPort ? '$status · ${l10n.linuxSvcPort('$port')}' : status;
}

IconData linuxServiceIcon(LinuxServiceState s) => linuxServicePaused(s)
    ? AppIconography.pause
    : switch (s.phase) {
        LinuxServicePhase.failed ||
        LinuxServicePhase.portBusy => AppIconography.error,
        LinuxServicePhase.backoff => AppIconography.restart,
        LinuxServicePhase.running ||
        LinuxServicePhase.starting => AppIconography.checkCircle,
        _ => AppIconography.terminal,
      };

/// One log line in words. The supervisor's marker lines (`::svc ...`) say
/// what happened; every other line is the program's own and stays as it is.
KitLogLine linuxServiceLogLine(AppLocalizations l10n, KitLogLine line) {
  final text = line.text;
  if (!text.startsWith('::svc ')) return line;
  final parts = text.substring(6).trim().split(RegExp(r'\s+'));
  int? at(int i) => i < parts.length ? int.tryParse(parts[i]) : null;
  final words = switch (parts.first) {
    'start' => l10n.linuxSvcLogStarted,
    'exit' =>
      (at(1) ?? 0) == 0
          ? l10n.linuxSvcLogEnded(0)
          : l10n.linuxSvcLogCrashed(at(1)!),
    'backoff' => l10n.linuxSvcLogRetry(at(1) ?? 0),
    'gave-up' => l10n.linuxSvcLogGaveUp,
    'port-busy' => l10n.linuxSvcLogPortBusy('${at(1) ?? ''}'),
    _ => null,
  };
  if (words == null) return line;
  final level = switch (parts.first) {
    'exit' when (at(1) ?? 0) != 0 => KitLogLevel.warning,
    'gave-up' || 'port-busy' => KitLogLevel.error,
    _ => KitLogLevel.normal,
  };
  return KitLogLine(words, level: level, at: line.at);
}

/// Asks before a service is removed, and says what goes and what stays.
Future<bool> confirmRemoveLinuxService(
  BuildContext context,
  LinuxServiceState s,
) {
  final l10n = AppLocalizations.of(context);
  final name = s.def.title;
  return showKitConfirm(
    context,
    title: l10n.linuxSvcRemoveTitle(name),
    body: l10n.linuxSvcRemoveBody,
    confirmLabel: l10n.linuxSvcRemoveNamed(name),
    icon: AppIconography.stop,
    kind: KitConfirmKind.destructive,
    consequenceItems: [
      KitConsequence(l10n.linuxSvcRemoveGoes, mark: KitConsequenceMark.lost),
      KitConsequence(l10n.linuxSvcRemoveStays, mark: KitConsequenceMark.kept),
    ],
    sheetKey: const ValueKey('linux-service-remove-sheet'),
    confirmKey: const ValueKey('linux-service-remove-confirm'),
  );
}
