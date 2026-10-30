part of '../this_phone_screen.dart';

/// The in-app server's crash recovery as This phone reads it: the app-long
/// owner's state ([PhoneServerHealing.recovery]), only when that owner
/// already runs (the app shell starts it). Nothing else creates it here, so
/// a page with no owner promises no restart. Captures and tests override it.
final thisPhoneRecoveryProvider =
    Provider.autoDispose<ValueListenable<BuiltinRecoveryState>?>((ref) {
      if (!ref.exists(phoneServerHealingProvider)) return null;
      return _RecoveryView(ref.read(phoneServerHealingProvider).recovery);
    });

class _RecoveryView implements ValueListenable<BuiltinRecoveryState> {
  const _RecoveryView(this.recovery);

  final BuiltinServerRecovery recovery;

  @override
  BuiltinRecoveryState get value => recovery.value;

  @override
  void addListener(VoidCallback listener) => recovery.addListener(listener);

  @override
  void removeListener(VoidCallback listener) =>
      recovery.removeListener(listener);
}

/// What the app does by itself about a server that stopped, right now.
enum _SelfRestart { none, restarting, scheduled, exhausted }

/// The page's one answer: its words, and the one act it needs, if any.
class _Answer {
  const _Answer({
    required this.tone,
    required this.answer,
    this.line,
    this.lineIsProblem = false,
    this.until,
    this.untilLine,
    this.quiet,
    this.primary,
  });

  final KitAnswerTone tone;
  final String answer;
  final String? line;

  /// The line says what went wrong (keyed for the tests that read it).
  final bool lineIsProblem;
  final DateTime? until;
  final String Function(Duration left)? untilLine;

  /// A quiet text action under the line.
  final KitAction? quiet;

  /// The one mint button at the thumb.
  final KitAction? primary;
}

/// "about 40 s", "about 2 min": rounded up, so the promise is never early.
String thisPhoneWait(AppLocalizations l10n, Duration left) {
  final seconds = left.inSeconds;
  if (seconds < 60) {
    final rounded = seconds <= 5 ? 5 : ((seconds + 4) ~/ 5) * 5;
    return l10n.thisPhoneAboutSeconds(rounded);
  }
  return l10n.thisPhoneAboutMinutes((seconds + 59) ~/ 60);
}

/// "410 MB", "1.2 GB": a size in round figures, isolated left to right.
String thisPhoneSize(int bytes) {
  final mb = bytes / (1024 * 1024);
  if (mb < 1000) {
    final rounded = mb < 100 ? mb.round() : (mb / 10).round() * 10;
    return KitBidi.ltr('$rounded\u00a0MB');
  }
  return KitBidi.ltr('${(mb / 1024).toStringAsFixed(1)}\u00a0GB');
}

extension _ThisPhoneAnswer on _ThisPhoneScreenState {
  /// Resident memory of what runs on this phone's host, when it was read:
  /// the in-app Linux's own scan, or the Termux report the page reads for
  /// Running on this phone (Termux's own app left out).
  int? get _memory {
    if (_host.kind == PhoneHostKind.inApp) return _memoryBytes;
    final report = _processes;
    if (report == null) return null;
    final kb = report.processes
        .where((p) => !p.isHostApp)
        .fold<int>(0, (sum, p) => sum + p.rssKb);
    return kb > 0 ? kb * 1024 : null;
  }

  /// Reads the in-app Linux's memory once the page's other reads are done
  /// (they share the phone's one task slot). Left out when it fails.
  Future<void> _readMemory() async {
    if (_host.kind != PhoneHostKind.inApp ||
        _host.state != PhoneHostState.running) {
      return;
    }
    try {
      final scan = await LinuxManager(
        ref.read(builtinLinuxProvider),
      ).scanProcesses();
      final kb = scan.processes.fold<int>(0, (sum, p) => sum + p.rssKb);
      _setMemory(kb > 0 ? kb * 1024 : null);
    } catch (_) {
      _setMemory(null);
    }
  }

  /// The Termux recovery for this host's saved connection, where Termux
  /// runs (the same object the "Restart after a crash" row shows).
  ManagedServerRecovery? get _termuxRecovery {
    if (_host.kind != PhoneHostKind.termux ||
        !platformCapabilities.supportsTermux) {
      return null;
    }
    final profile = _host.profile;
    if (profile == null) return null;
    return ManagedServerRecovery.forProfile(
      _connection.store.prefs,
      profile.id,
    );
  }

  (_SelfRestart, DateTime?) _selfRestart(
    ValueListenable<BuiltinRecoveryState>? inApp,
    ManagedServerRecovery? termux,
  ) {
    if (_host.kind == PhoneHostKind.inApp) {
      final value = inApp?.value;
      if (value == null || value.profileId != _host.profile?.id) {
        return (_SelfRestart.none, null);
      }
      return switch (value.phase) {
        BuiltinRecoveryPhase.restarting => (_SelfRestart.restarting, null),
        BuiltinRecoveryPhase.waiting => (
          _SelfRestart.scheduled,
          value.nextAttemptAt,
        ),
        BuiltinRecoveryPhase.exhausted => (_SelfRestart.exhausted, null),
        _ => (_SelfRestart.none, null),
      };
    }
    if (termux == null || !termux.enabled) return (_SelfRestart.none, null);
    return switch (termux.phase) {
      ManagedRecoveryPhase.restarting => (_SelfRestart.restarting, null),
      ManagedRecoveryPhase.waitingToRetry => (
        _SelfRestart.scheduled,
        termux.nextAttemptAt,
      ),
      ManagedRecoveryPhase.exhausted => (_SelfRestart.exhausted, null),
      _ => (_SelfRestart.none, null),
    };
  }

  /// Why a server nobody stopped is not running, in plain words: Android's
  /// kill (memory or its background process limit; the exit code cannot
  /// tell which, so neither is named), else that it stopped by itself.
  String _stoppedWhy(AppLocalizations l10n) {
    final server = _performance?.services[BuiltinLinux.serverServiceName];
    return server?.exitReason == BuiltinServiceExitReason.memoryOrPhantomKill
        ? l10n.thisPhoneAnswerAndroidClosed
        : l10n.thisPhoneAnswerStoppedItself;
  }

  /// Whether the person stopped the server themselves (Stop on this page;
  /// cleared by the next start that answered).
  bool get _stoppedByPerson {
    final profile = _host.profile;
    if (profile == null) return false;
    if (_host.kind == PhoneHostKind.termux) return true;
    final server = _performance?.services[BuiltinLinux.serverServiceName];
    return DeliberateServerStop.isMarked(_connection.store.prefs, profile.id) ||
        server?.lastStopRequested == true;
  }

  /// The heat guard's hold on the AI Team of this phone, when it holds one.
  _Answer? _heat(AppLocalizations l10n, ThermalGuard? guard) {
    if (guard == null ||
        guard.hold == ThermalHold.none ||
        guard.holds.isEmpty) {
      return null;
    }
    final prefs = _connection.store.prefs;
    final resumes = guard.holds.keys.any(
      (id) => AutomationPolicyController.forProfile(
        prefs,
        id,
      ).value.allows(AutomationBehavior.thermalRecovery),
    );
    final at = resumes ? guard.policy.resumeAt : null;
    return _Answer(
      tone: KitAnswerTone.paused,
      answer: guard.hold == ThermalHold.stopped
          ? l10n.thisPhoneAnswerHeatStopped
          : l10n.thisPhoneAnswerHeatPaused,
      line: !resumes
          ? l10n.thisPhoneAnswerHeatStays
          : l10n.thisPhoneAnswerHeatResumeCool,
      until: at,
      untilLine: at == null
          ? null
          : (left) => left == Duration.zero
                ? l10n.thisPhoneAnswerHeatResumeCool
                : l10n.thisPhoneAnswerHeatResumeIn(thisPhoneWait(l10n, left)),
    );
  }

  /// The one answer for the state the page reads now.
  _Answer _answerNow(
    AppLocalizations l10n, {
    required ValueListenable<BuiltinRecoveryState>? recovery,
    required ManagedServerRecovery? termuxRecovery,
    required ThermalGuard? guard,
  }) {
    final state = _host.state;
    final (selfRestart, restartAt) = _selfRestart(recovery, termuxRecovery);
    final runtimeName = _runtimeName(_host.runtime);
    KitAction act(String key, String label, Future<void> Function() run) =>
        KitAction(
          key: ValueKey(key),
          label: label,
          onPressed: () => unawaited(run()),
        );
    _Answer working(String answer, {String? line, KitAction? quiet}) => _Answer(
      tone: KitAnswerTone.working,
      answer: answer,
      line: line,
      quiet: quiet,
    );

    if (_removing) return working(l10n.thisPhoneAnswerRemoving);
    if (_connecting) return working(l10n.thisPhoneAnswerConnecting);
    switch (state) {
      case PhoneHostState.checking:
        return working(l10n.thisPhoneAnswerChecking);
      case PhoneHostState.settingUp:
        return working(
          l10n.thisPhoneAnswerSettingUp,
          quiet: act(
            'this-phone-progress',
            l10n.phoneServerCardShowProgress,
            _showProgress,
          ),
        );
      case PhoneHostState.starting:
        return selfRestart == _SelfRestart.restarting
            ? working(l10n.thisPhoneAnswerRestarting, line: _stoppedWhy(l10n))
            : working(l10n.thisPhoneAnswerStarting);
      case PhoneHostState.stopping:
        return working(l10n.thisPhoneAnswerStopping);
      case PhoneHostState.notSetUp ||
          PhoneHostState.needsYou ||
          PhoneHostState.stopped ||
          PhoneHostState.running:
        break;
    }

    // Termux cannot be reached: its cause and the one fix.
    final reach = _host.termuxProblem;
    if (reach != null) {
      final words = termuxProblemWords(
        l10n,
        reach,
        runtime: _host.runtimeKnown ? runtimeName : null,
      );
      return _Answer(
        tone: KitAnswerTone.needsYou,
        answer: l10n.thisPhoneAnswerTermux,
        line: words.line,
        lineIsProblem: true,
        primary: act(
          'this-phone-termux-fix',
          words.action,
          () => _fixTermux(reach),
        ),
      );
    }
    final problem = _host.problem;
    final switchTarget = _host.switchTarget;
    _Answer needs(
      String answer,
      String line,
      KitAction primary, {
      KitAction? quiet,
    }) => _Answer(
      tone: KitAnswerTone.needsYou,
      answer: answer,
      line: line,
      lineIsProblem: true,
      primary: primary,
      quiet: quiet,
    );
    if (problem == PhoneHostProblem.componentRecovery) {
      return needs(
        l10n.thisPhoneAnswerRecovery,
        l10n.inAppServerOwnershipRecoveryBody,
        act(
          'this-phone-recover-ownership',
          l10n.e7SetupOpenAppSettings,
          TermuxBridge.openAppSettings,
        ),
      );
    }
    if (state == PhoneHostState.notSetUp) {
      if (problem == PhoneHostProblem.check) {
        return needs(
          l10n.thisPhoneAnswerCheckFailed,
          l10n.thisPhoneCheckFailed(runtimeName),
          act('this-phone-check-again', l10n.commonRetry, _load),
        );
      }
      return _Answer(
        tone: KitAnswerTone.off,
        answer: l10n.thisPhoneAnswerNotSetUp,
        primary: act('this-phone-set-up', l10n.thisPhoneSetUp, _setUp),
      );
    }
    if (state == PhoneHostState.needsYou && switchTarget != null) {
      return needs(
        l10n.thisPhoneAnswerSwitchStopped,
        l10n.thisPhoneSwitchStopped(_runtimeName(switchTarget)),
        act(
          'this-phone-switch-retry',
          l10n.setupSwitchRetry(_runtimeName(switchTarget)),
          () => openPhoneSetupTermux(
            context,
            job: TermuxHostJob.switchRuntime,
            target: switchTarget,
          ),
        ),
      );
    }
    if (state == PhoneHostState.needsYou) {
      switch (problem) {
        case PhoneHostProblem.check:
          return needs(
            l10n.thisPhoneAnswerCheckFailed,
            l10n.thisPhoneCheckFailed(runtimeName),
            act('this-phone-check-again', l10n.commonRetry, _load),
          );
        case PhoneHostProblem.install:
          return needs(
            l10n.thisPhoneAnswerInstallFailed,
            l10n.thisPhoneInstallFailed(runtimeName),
            act('this-phone-reinstall', l10n.thisPhoneInstallAgain, _reinstall),
            // The version installed before may still start.
            quiet: act('this-phone-start', l10n.thisPhoneStart, _start),
          );
        case PhoneHostProblem.stop:
          return needs(
            l10n.thisPhoneAnswerStopFailed,
            l10n.thisPhoneStopFailed(runtimeName),
            act('this-phone-start', l10n.thisPhoneStart, _start),
          );
        case PhoneHostProblem.start ||
            PhoneHostProblem.componentRecovery ||
            null:
          break;
      }
    }

    if (state == PhoneHostState.running) {
      final heat = _heat(l10n, guard);
      if (heat != null) return heat;
      final history = historyLine(l10n, _history);
      final memory = _memory;
      final storage = _host.bytesUsed;
      final cost = memory != null
          ? l10n.thisPhoneAnswerMemory(thisPhoneSize(memory))
          : storage != null
          ? l10n.thisPhoneAnswerStorage(thisPhoneSize(storage))
          : null;
      final connect = !_connectedHere && _host.profile != null;
      return _Answer(
        tone: KitAnswerTone.ok,
        answer: l10n.thisPhoneAnswerRunning,
        line: problem == PhoneHostProblem.stop
            ? l10n.thisPhoneStopFailed(runtimeName)
            : history ?? cost,
        lineIsProblem: problem == PhoneHostProblem.stop,
        quiet: connect
            ? act('this-phone-connect', l10n.thisPhoneAnswerConnect, _connect)
            : null,
      );
    }

    // Stopped (or a start that failed): why, what the app does by itself,
    // and Restart now.
    final failedStart = problem == PhoneHostProblem.start;
    if (!failedStart && _stoppedByPerson) {
      return _Answer(
        tone: KitAnswerTone.off,
        answer: l10n.thisPhoneAnswerOff,
        line: l10n.thisPhoneAnswerOffLine,
        primary: act('this-phone-start', l10n.thisPhoneStart, _start),
      );
    }
    final restartNow = act(
      'this-phone-start',
      failedStart ? l10n.thisPhoneStartAgain : l10n.thisPhoneAnswerRestartNow,
      _start,
    );
    final why = failedStart
        ? l10n.thisPhoneAnswerStartFailed
        : _stoppedWhy(l10n);
    switch (selfRestart) {
      case _SelfRestart.scheduled:
        return _Answer(
          tone: KitAnswerTone.paused,
          answer: why,
          line: l10n.thisPhoneAnswerRestartsSoon,
          until: restartAt,
          untilLine: restartAt == null
              ? null
              : (left) => left == Duration.zero
                    ? l10n.thisPhoneAnswerRestartsSoon
                    : l10n.thisPhoneAnswerRestartsIn(thisPhoneWait(l10n, left)),
          primary: restartNow,
        );
      case _SelfRestart.restarting:
        return working(l10n.thisPhoneAnswerRestarting, line: why);
      case _SelfRestart.exhausted:
        return _Answer(
          tone: KitAnswerTone.needsYou,
          answer: why,
          line: l10n.thisPhoneAnswerGaveUp,
          lineIsProblem: true,
          primary: restartNow,
        );
      case _SelfRestart.none:
        return _Answer(
          tone: failedStart ? KitAnswerTone.needsYou : KitAnswerTone.off,
          answer: why,
          line: failedStart ? l10n.thisPhoneAnswerStartFailedLine : null,
          lineIsProblem: failedStart,
          primary: restartNow,
        );
    }
  }

  /// The answer, centred in the first view; it listens to the heat guard
  /// and the crash recovery so their changes show at once.
  Widget _answerView(_Answer answer) => KitStatusAnswer(
    key: const ValueKey('this-phone-status'),
    tone: answer.tone,
    answer: answer.answer,
    answerKey: const ValueKey('this-phone-state'),
    line: answer.line,
    lineKey: ValueKey(
      answer.lineIsProblem ? 'this-phone-failure' : 'this-phone-answer-line',
    ),
    countdownTo: answer.until,
    countdownLine: answer.untilLine,
    action: answer.quiet,
  );
}
