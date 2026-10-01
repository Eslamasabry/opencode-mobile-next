part of '../setup_engine.dart';

extension _SetupEnginePoll on ChannelSetupEngine {
  bool get _jobRunning => _record?.state == 'running';

  void _ensurePolling() {
    if (_polling || _disposed) return;
    _polling = true;
    _poll = Timer(pollInterval, _tick);
  }

  Future<void> _tick() async {
    try {
      await _refresh(timeout: readTimeout);
    } catch (error, stack) {
      // Polling is the only way the screens see the job, and the only way
      // the app's own step gets run: one bad read must never end it.
      debugPrint(KitRedact.text('setup: status poll failed: $error\n$stack'));
    }
    if (!_disposed &&
        (_progress.watched || _jobRunning || _handoverUncertain)) {
      _poll = Timer(pollInterval, _tick);
    } else {
      _polling = false;
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
    if (host == SetupHostKind.termux &&
        record.state == 'running' &&
        _cancelRequested) {
      // A cancel during a lost start reply must survive until status recovers.
      // Keep the flag through failures so polling retries before any finisher.
      await _linux.cancelSetup();
      return;
    }
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
    } catch (_) {
      error = appFailureReason(SetupAppFailureKind.failed);
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
              host: host,
              runtime: TermuxRuntime.parse(step.data['runtime']),
              openCodeChanged: step.data['openCodeChanged'] == 'true',
              version: version,
            ),
          ),
        );
      } catch (e) {
        error = '$e';
      }
    }
    try {
      await _linux.completeSetupStep(
        jobId: record.jobId,
        id: SetupComponentIds.start,
        ok: error == null,
        error: error == null ? null : KitRedact.text(error),
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

  Future<SetupJobRecord?> _read({Duration? timeout}) async {
    final String? text;
    final limit =
        timeout ?? (host == SetupHostKind.termux ? readTimeout : null);
    try {
      final read = _linux.setupStatus();
      text = await (limit == null ? read : read.timeout(limit));
    } on BuiltinLinuxException {
      if (host == SetupHostKind.termux) rethrow;
      return null;
    } on TimeoutException {
      if (host == SetupHostKind.termux) rethrow;
      debugPrint('setup: status read took over ${limit!.inSeconds} s');
      return null;
    }
    final record = SetupJobRecord.parse(text);
    if (host == SetupHostKind.termux &&
        text != null &&
        text.trim().isNotEmpty &&
        record == null) {
      throw const BuiltinLinuxException(
        'The saved Termux setup could not be read.',
        code: 'setup_status_invalid',
      );
    }
    if (record != null) {
      // Hostless records predate host selection and belong only to built-in.
      final recordedHost = record.host ?? SetupHostKind.builtin.name;
      if (recordedHost != host.name) {
        throw const BuiltinLinuxException(
          'The saved setup belongs to a different host.',
          code: 'setup_host_mismatch',
        );
      }
      if (host == SetupHostKind.termux) {
        TermuxSetupHost.validateParams(record.params);
      }
      _record = record;
    }
    return record;
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

  /// Records each finished component, and the finished job, as spans timed
  /// by the native runner's own clock (setup.json's startedAt/endedAt).
  void _traceFinished(SetupJobRecord record) {
    for (final component in record.components) {
      final started = component.startedAt;
      final ended = component.endedAt;
      if (started == null ||
          ended == null ||
          (component.state != 'done' && component.state != 'failed') ||
          !_traced.add('${record.jobId}/${component.id}')) {
        continue;
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
    }
  }

  void _resetFloors(String jobId) {
    if (_floorsJob == jobId) return;
    _floorsJob = jobId;
    _floors.clear();
  }
}
