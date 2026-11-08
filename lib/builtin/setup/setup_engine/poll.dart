part of '../setup_engine.dart';

extension _SetupEnginePoll on ChannelSetupEngine {
  bool get _jobRunning => _record?.state == 'running';

  /// Reads within [pollInterval]: a job started, a hand-over is unsure, or
  /// a page began watching. Brings an idle wait forward.
  void _ensurePolling() {
    if (_disposed) return;
    if (_polling) {
      final pending = _poll;
      if (_pollIdle && pending != null && pending.isActive) {
        pending.cancel();
        _pollIdle = false;
        _poll = Timer(pollInterval, _tick);
      }
      return;
    }
    _polling = true;
    _pollIdle = false;
    _poll = Timer(pollInterval, _tick);
  }

  Future<void> _tick() async {
    final following = _jobRunning || _handoverUncertain;
    // A watched engine with no job reads only while the app is in front.
    if (following || _foreground()) {
      try {
        await _refresh(timeout: readTimeout);
      } on BuiltinLinuxException catch (error) {
        debugPrint(KitRedact.text('setup: status poll failed: $error'));
      } catch (error, stack) {
        // Polling is the only way the screens see the job, and the only way
        // the app's own step gets run: one bad read must never end it.
        debugPrint(KitRedact.text('setup: status poll failed: $error\n$stack'));
      }
    }
    if (_disposed) {
      _polling = false;
      _poll = null;
    } else if (_jobRunning || _handoverUncertain) {
      _pollIdle = false;
      _poll = Timer(pollInterval, _tick);
    } else if (_progress.watched) {
      // Nothing runs: a page merely shows the last job. A job this engine
      // starts brings the next read forward (see [_ensurePolling]).
      _pollIdle = true;
      _poll = Timer(idlePollInterval, _tick);
    } else {
      _polling = false;
      _pollIdle = false;
      _poll = null;
    }
  }

  Future<void> _refresh({Duration? timeout}) async {
    final record = await _read(timeout: timeout);
    if (_handoverUncertain) {
      _handoverUncertain = false;
      final error = _handoverError;
      _handoverError = null;
      if (record?.jobId != _handedOver) {
        final current = _progress.value;
        _emitLocal(
          current.jobId!,
          {for (final component in current.components) component.id: component},
          0,
          state: SetupState.failed,
          error: error,
        );
        return;
      }
    }
    if (record == null) return;
    if (_startingJobId != null && record.jobId != _startingJobId) return;
    _show(record);
    _followAppStep(record);
    if (record.state == 'running' &&
        record.current == SetupComponentIds.start &&
        _finishing != record.jobId) {
      final step = record.component(SetupComponentIds.start);
      if (step != null && step.state == 'running') {
        _finishing = record.jobId;
        unawaited(_finish(record, step));
      }
    }
  }

  /// Starts the app-side component the native job is waiting on, once per
  /// job, and stops one the job no longer waits on (cancelled, or the
  /// native side gave up on it).
  void _followAppStep(SetupJobRecord record) {
    final run = _appRun;
    if (run != null &&
        (record.jobId != run.jobId ||
            record.state != 'running' ||
            record.current != run.id)) {
      run.app.cancel();
    }
    final id = record.current;
    if (record.state != 'running' || id == null) return;
    final step = record.component(id);
    if (step == null || step.state != 'running') return;
    SetupAppComponent? app;
    for (final component in _jobComponents) {
      if (component.id == id) app = component.app;
    }
    if (app == null || !_appStarted.add('${record.jobId}/$id')) return;
    unawaited(_runApp(record.jobId, id, app));
  }

  /// Installs an app-side component and tells the native job how it went.
  Future<void> _runApp(String jobId, String id, SetupAppComponent app) async {
    _appRun = (jobId: jobId, id: id, app: app);
    _appLive = null;
    String? error;
    String? version;
    try {
      version = await app.install(
        onProgress: (progress) => _appProgress(jobId, progress),
      );
    } on SetupAppFailure catch (failure) {
      if (failure.kind == SetupAppFailureKind.cancelled) {
        // The job was cancelled or gave up on it: nothing to report.
        _appDone(jobId, id);
        return;
      }
      error = appFailureReason(failure.kind);
    } catch (failure) {
      error = _isRuntimeOwnershipFailure(failure)
          ? _runtimeOwnershipUnknown
          : appFailureReason(SetupAppFailureKind.failed);
    }
    _appDone(jobId, id);
    try {
      await _linux.completeSetupStep(
        jobId: jobId,
        id: id,
        ok: error == null,
        error: error,
        version: version == null ? null : KitRedact.text(version),
      );
    } on BuiltinLinuxException {
      // Lost on the way: the next poll starts it again, which finds it
      // installed (or resumes the download) and reports again.
      _appStarted.remove('$jobId/$id');
      return;
    }
    try {
      await _refresh();
    } on BuiltinLinuxException {
      // The next poll reads the job.
    } on TimeoutException {
      // Likewise.
    }
  }

  void _appDone(String jobId, String id) {
    final run = _appRun;
    if (run != null && run.jobId == jobId && run.id == id) _appRun = null;
    _appLive = null;
  }

  /// Shows an app-side install's bytes at most four times a second, and at
  /// once when its stage changes.
  void _appProgress(String jobId, SetupAppProgress progress) {
    if (_appRun?.jobId != jobId) return;
    final previous = _appLive;
    _appLive = progress;
    final now = _clock();
    final shown = _appLiveShown;
    if (previous?.stage == progress.stage &&
        shown != null &&
        now.difference(shown) < const Duration(milliseconds: 250)) {
      return;
    }
    _appLiveShown = now;
    final record = _record;
    if (record != null && record.jobId == jobId && !_disposed) _show(record);
  }

  Future<void> _finish(SetupJobRecord record, SetupJobComponent step) async {
    final version = record.component(SetupComponentIds.openCode)?.version;
    String? error;
    final finish = finisher;
    if (finish == null) {
      error = strings().phoneSetupErrorCannotStart;
    } else {
      try {
        error = await PerfTrace.span(
          'setup.finish',
          () => finish(
            SetupFinishRequest(
              runtime: OpenCodeRuntime.parse(step.data['runtime']),
              openCodeChanged: step.data['openCodeChanged'] == 'true',
              version: version,
            ),
          ),
        );
      } catch (e) {
        error = _isRuntimeOwnershipFailure(e) ? _runtimeOwnershipUnknown : '$e';
      }
    }
    try {
      await _linux.completeSetupStep(
        jobId: record.jobId,
        id: SetupComponentIds.start,
        ok: error == null,
        error: _isRuntimeOwnershipFailure(error)
            ? _runtimeOwnershipUnknown
            : error == null
            ? null
            : KitRedact.text(error),
        version: version == null ? null : KitRedact.text(version),
      );
    } on BuiltinLinuxException {
      // The acknowledgement may have been lost. Let the next poll either
      // observe completion or retry the idempotent finish on this same job.
      _finishing = null;
      return;
    }
    try {
      await _refresh();
    } on BuiltinLinuxException {
      // Poll again; failure to read is not evidence the finish was lost.
    } on TimeoutException {
      // A late status reply is recoverable on the next poll.
    }
  }

  Future<SetupJobRecord?> _read({
    Duration? timeout,
    SetupReadScope? scope,
  }) async {
    // An unreadable job is not evidence of a fresh install. In particular,
    // never apply the new runtime default after a channel failure or timeout.
    final text = await _statusText(timeout ?? readTimeout, scope);
    final record = SetupJobRecord.parse(text);
    if (text != null && text.trim().isNotEmpty && record == null) {
      throw const BuiltinLinuxException(
        'The saved phone setup could not be read.',
        code: 'setup_status_invalid',
      );
    }
    if (record != null) {
      // Hostless records predate host selection and belong to built-in. A
      // record an older build wrote for another host is not this one's.
      final recordedHost = record.host ?? builtinSetupHostName;
      if (recordedHost != builtinSetupHostName) {
        throw const BuiltinLinuxException(
          'The saved setup belongs to a different host.',
          code: 'setup_host_mismatch',
        );
      }
      _record = record;
    }
    return record;
  }

  /// `setupStatus()` with a timeout that [scope] can cancel. Not
  /// `Future.timeout`: that timer outlives a screen that has gone.
  Future<String?> _statusText(Duration timeout, SetupReadScope? scope) {
    final done = Completer<String?>();
    final timer = Timer(timeout, () {
      if (!done.isCompleted) {
        done.completeError(TimeoutException(null, timeout));
      }
    });
    final release = scope?.add(() {
      timer.cancel();
      if (!done.isCompleted) done.completeError(const SetupReadAbandoned());
    });
    _linux.setupStatus().then(
      (text) {
        if (!done.isCompleted) done.complete(text);
      },
      onError: (Object error, StackTrace stack) {
        if (!done.isCompleted) done.completeError(error, stack);
      },
    );
    return done.future.whenComplete(() {
      timer.cancel();
      release?.call();
    });
  }

  void _show(SetupJobRecord record) {
    final starting = _startingJobId;
    if (starting != null && record.jobId != starting) return;
    final l10n = strings();
    if (_jobComponents.isEmpty ||
        _progress.value.jobId != record.jobId ||
        _jobComponents.length != record.components.length) {
      final all = _components(l10n, record.params);
      _jobComponents = [
        for (final component in record.components)
          all.firstWhere(
            (candidate) => candidate.id == component.id,
            orElse: () => SetupComponent(
              id: component.id,
              title: component.id,
              shortTitle: component.id,
              checkScript: '',
              installScript: '',
            ),
          ),
      ];
    }
    _resetFloors(record.jobId);
    _traceFinished(record);
    _progress.value = progressFromRecord(
      _withAppLive(record, l10n),
      _jobComponents,
      l10n,
      now: _clock(),
      floors: _floors,
    );
  }

  /// [record] with the running app-side install's own progress on its row.
  SetupJobRecord _withAppLive(SetupJobRecord record, AppLocalizations l10n) {
    final run = _appRun;
    final live = _appLive;
    if (run == null || live == null || run.jobId != record.jobId) {
      return record;
    }
    return SetupJobRecord(
      jobId: record.jobId,
      host: record.host,
      state: record.state,
      current: record.current,
      startedAt: record.startedAt,
      updatedAt: record.updatedAt,
      error: record.error,
      logTail: record.logTail,
      params: record.params,
      components: [
        for (final component in record.components)
          component.id == run.id && component.state == 'running'
              ? SetupJobComponent(
                  id: component.id,
                  state: component.state,
                  weight: component.weight,
                  stage: switch (live.stage) {
                    SetupAppStage.downloading => l10n.setupAppStageDownloading,
                    SetupAppStage.verifying => l10n.setupAppStageVerifying,
                  },
                  done: live.bytesDone,
                  total: live.bytesTotal,
                  startedAt: component.startedAt,
                  data: component.data,
                )
              : component,
      ],
    );
  }

  /// The exit code a failed step reported (`data.exit`, or "exit 127" in its
  /// error text); null when it gave none. Only the number is ever reported.
  static int? _exitCodeOf(SetupJobComponent component) {
    final fromData = int.tryParse(component.data['exit'] ?? '');
    if (fromData != null) return fromData;
    final match = RegExp(
      r'\bexit(?: code| status)?[ :=]+(\d{1,3})\b',
      caseSensitive: false,
    ).firstMatch(component.error ?? '');
    return match == null ? null : int.parse(match.group(1)!);
  }

  /// Records each finished component, and the finished job, as spans timed
  /// by the native runner's own clock (setup.json's startedAt/endedAt).
  void _traceFinished(SetupJobRecord record) {
    final counted = _countsForAnalytics(record);
    for (final component in record.components) {
      if (!counted) break;
      if ((component.state == 'running' ||
              component.state == 'done' ||
              component.state == 'failed') &&
          _traced.add('${record.jobId}/${component.id}/reached')) {
        AppTelemetry.track(AnalyticsEvent.setupStepReached, {
          'step': AnalyticsSetupStep.of(component.id),
        });
      }
    }
    for (final component in record.components) {
      final started = component.startedAt;
      final ended = component.endedAt;
      if (started == null ||
          ended == null ||
          (component.state != 'done' && component.state != 'failed') ||
          !_traced.add('${record.jobId}/${component.id}')) {
        continue;
      }
      if (component.state == 'failed') {
        final exit = _exitCodeOf(component);
        AppTelemetry.failure('setup_step_failed', {
          'step': component.id,
          'exit': exit,
        });
        if (counted) {
          final props = <String, Object?>{
            'step': AnalyticsSetupStep.of(component.id),
            'kind': analyticsFailureKind(exit: exit, error: component.error),
            'compat_mode': _ranInCompatibilityMode(record, component),
          };
          if (component.id == SetupComponentIds.node) {
            unawaited(
              _nodeVariantForAnalytics(record, component).then((variant) {
                if (variant != null) props['node_variant'] = variant;
                AppTelemetry.track(AnalyticsEvent.setupStepFailed, props);
              }),
            );
          } else {
            AppTelemetry.track(AnalyticsEvent.setupStepFailed, props);
          }
        }
      }
      if (counted) {
        unawaited(
          _trackLinuxStep(
            record,
            component,
            Duration(milliseconds: ended - started),
          ),
        );
      }
      PerfTrace.recordDuration(
        'setup.component',
        Duration(milliseconds: ended - started),
        attrs: {'id': component.id},
        error: component.state == 'failed' ? component.state : null,
      );
    }
    if (record.state != 'running' &&
        record.startedAt > 0 &&
        record.updatedAt >= record.startedAt &&
        _traced.add('${record.jobId}/')) {
      PerfTrace.recordDuration(
        'setup.job',
        Duration(milliseconds: record.updatedAt - record.startedAt),
        attrs: {'state': record.state},
        error: record.state == 'done' ? null : record.state,
      );
      if (counted && record.state == 'done') {
        HealthFlow.linuxSetupStep(
          step: 'done',
          ok: true,
          elapsed: Duration(milliseconds: record.updatedAt - record.startedAt),
          freeGigabytes: AppTelemetry.health?.freeGigabytes,
        );
        AppTelemetry.track(AnalyticsEvent.setupDone, {
          'minutes': AnalyticsBuckets.minutesOf(
            Duration(milliseconds: record.updatedAt - record.startedAt),
          ),
        });
      }
    }
  }

  /// The `linux_setup_step` word for [component], or null for a component
  /// that is not a built-in Linux step (voice, the final start).
  static String? _linuxStepWord(SetupJobComponent component, bool failed) =>
      switch (component.id) {
        SetupComponentIds.linux =>
          failed &&
                  analyticsFailureKind(
                        exit: _exitCodeOf(component),
                        error: component.error,
                      ) ==
                      AnalyticsFailureKind.network
              ? 'download'
              : 'unpack',
        SetupComponentIds.essentials ||
        SetupComponentIds.python => 'essentials',
        SetupComponentIds.node => 'node',
        SetupComponentIds.openCode => 'opencode',
        SetupComponentIds.aiTeam => 'agents',
        _ => null,
      };

  /// One finished step as `linux_setup_step`: result, time, exit and
  /// signal kind, the Node safe mode that ran, and free space. Words and
  /// ranges only; the step's error text is never sent.
  Future<void> _trackLinuxStep(
    SetupJobRecord record,
    SetupJobComponent component,
    Duration elapsed,
  ) async {
    final failed = component.state == 'failed';
    final step = _linuxStepWord(component, failed);
    if (step == null) return;
    var compat = 'none';
    if (component.id == SetupComponentIds.node) {
      try {
        final variant = await _nodeVariantForAnalytics(record, component);
        compat = switch (variant) {
          AnalyticsNodeVariant.armcap => 'armcap',
          AnalyticsNodeVariant.threads => 'threads',
          AnalyticsNodeVariant.both => 'both',
          _ => 'none',
        };
      } catch (_) {}
    }
    HealthFlow.linuxSetupStep(
      step: step,
      ok: !failed,
      elapsed: elapsed,
      exit: failed ? _exitCodeOf(component) : null,
      compatMode: compat,
      freeGigabytes: AppTelemetry.health?.freeGigabytes,
    );
  }

  /// Whether [record] belongs in setup analytics: a job this app run saw
  /// working, or one that changed in the last half hour. An old finished
  /// job read again at start-up is not counted twice.
  bool _countsForAnalytics(SetupJobRecord record) {
    if (record.state == 'running') _traced.add('${record.jobId}/live');
    if (_traced.contains('${record.jobId}/live')) return true;
    final age = _clock().millisecondsSinceEpoch - record.updatedAt;
    return age >= 0 && age < const Duration(minutes: 30).inMilliseconds;
  }

  /// The Node.js safe mode for a failed Node step: none worked when the
  /// step gave up (node_compat.dart), else the one this phone kept, if any.
  Future<AnalyticsNodeVariant?> _nodeVariantForAnalytics(
    SetupJobRecord record,
    SetupJobComponent component,
  ) async {
    final text = '${component.error ?? ''}\n${record.logTail}';
    if (text.contains(NodeCompatFailure.unsupported)) {
      return AnalyticsNodeVariant.noneWorked;
    }
    try {
      final state = await NodeCompatStore.load();
      return state == null ? null : AnalyticsNodeVariant.of(state.variant.name);
    } catch (_) {
      return null;
    }
  }

  /// Whether [component] was run again in compatibility mode (issue #117):
  /// its data says so, or the job's log shows the retry line.
  bool _ranInCompatibilityMode(
    SetupJobRecord record,
    SetupJobComponent component,
  ) {
    if (component.data['compat'] == 'true') return true;
    final labels = {
      'Trying again in compatibility mode',
      strings().phoneSetupStageCompat,
    };
    return labels.any((label) => record.logTail.contains('==> $label'));
  }

  void _resetFloors(String jobId) {
    if (_floorsJob == jobId) return;
    _floorsJob = jobId;
    _floors.clear();
  }
}
