part of '../setup_engine.dart';

extension _SetupEngineStart on ChannelSetupEngine {
  Future<void> _start(
    Set<String> ids,
    Map<String, Map<String, String>> params,
  ) async {
    final l10n = strings();
    final all = _components(l10n, params);
    final job = expandSelection(all, ids);
    final jobId = 'setup-${_clock().microsecondsSinceEpoch}';
    _startingJobId = jobId;
    _jobComponents = job;
    _localFirstSetup = SetupJobParams.isFirstSetup(params);
    _localAdding = SetupJobParams.addingIds(params);
    _resetFloors(jobId);
    final startedAt = _clock().millisecondsSinceEpoch;
    final checking = <String, ComponentProgress>{
      for (final component in job)
        component.id: ComponentProgress(
          id: component.id,
          state: component.jobStep
              ? ComponentState.pending
              : ComponentState.checking,
        ),
    };
    _emitLocal(jobId, checking, startedAt, job: job);

    // The resume rule: whatever its check finds installed is not redone.
    final Map<String, SetupCheckResult> checks;
    try {
      checks = await _check(job);
    } on BuiltinLinuxException catch (error) {
      _emitLocal(
        jobId,
        {
          for (final c in job)
            c.id: ComponentProgress(id: c.id, state: ComponentState.pending),
        },
        startedAt,
        job: job,
        state: SetupState.failed,
        error: error.message,
      );
      return;
    }
    if (_cancelRequested) {
      _emitLocal(
        jobId,
        _afterChecks(job, checks),
        startedAt,
        job: job,
        state: SetupState.cancelled,
      );
      return;
    }
    _emitLocal(jobId, _afterChecks(job, checks), startedAt, job: job);

    final openCode = params[SetupComponentIds.openCode] ?? const {};
    final runtime = TermuxRuntime.parse(openCode['runtime']);
    final openCodeChanged =
        job.any((c) => c.id == SetupComponentIds.openCode) &&
        checks[SetupComponentIds.openCode]?.ok != true;
    final specs = [
      for (final component in job)
        _spec(
          component,
          checks[component.id],
          l10n,
          runtime: runtime,
          openCodeChanged: openCodeChanged,
        ),
    ];
    try {
      await _linux.startSetup(
        jobId: jobId,
        components: specs,
        params: params,
        texts: {
          'channel': l10n.phoneSetupNotificationChannel,
          'title': _localAdding.isEmpty
              ? l10n.phoneSetupNotificationTitle
              : setupAddingTitle(l10n, all, _localAdding),
          'progress': l10n.phoneSetupNotificationProgress('{percent}'),
          'done': l10n.phoneSetupNotificationDone,
          'stopped': l10n.phoneSetupNotificationStopped,
        },
      );
    } on BuiltinLinuxException catch (error) {
      if (host == SetupHostKind.termux) {
        // Native persists before dispatch. A lost RUN_COMMAND reply is not
        // proof of a failed install; reconcile without launching another job.
        _handedOver = jobId;
        _handoverUncertain = true;
        _handoverError = KitRedact.text(error.message);
        try {
          await _refresh();
        } on BuiltinLinuxException {
          // Keep the locally running state until durable status is available.
        } on TimeoutException {
          // The next poll resolves the handover; never infer failure here.
        } finally {
          _ensurePolling();
        }
        return;
      }
      _emitLocal(
        jobId,
        _afterChecks(job, checks),
        startedAt,
        job: job,
        state: SetupState.failed,
        error: error.code == 'setup_persistence'
            ? l10n.phoneSetupErrorInstall('OpenCode')
            : error.message,
      );
      return;
    }
    _handedOver = jobId;
    // A cancel that came while the job was being handed over.
    if (_cancelRequested) {
      try {
        await _linux.cancelSetup();
      } on BuiltinLinuxException {
        // The next read shows how the job ended.
      }
    }
    try {
      await _refresh();
    } finally {
      _ensurePolling();
    }
  }

  /// Ends a start that broke before the runner had the job as a failed job.
  void _failStart(Object error) {
    final jobId = _startingJobId ?? 'setup-${_clock().microsecondsSinceEpoch}';
    final rows = _jobComponents;
    final shown = {for (final c in _progress.value.components) c.id: c};
    _emitLocal(
      jobId,
      {
        for (final c in rows)
          c.id:
              shown[c.id] ??
              ComponentProgress(id: c.id, state: ComponentState.pending),
      },
      _clock().millisecondsSinceEpoch,
      job: rows,
      state: SetupState.failed,
      error: KitRedact.text('${error.runtimeType}: $error'),
    );
  }

  Map<String, Object?> _spec(
    SetupComponent component,
    SetupCheckResult? check,
    AppLocalizations l10n, {
    required TermuxRuntime runtime,
    required bool openCodeChanged,
  }) {
    final data = <String, String>{
      'requiredFreeBytes':
          '${requiredSetupFreeBytes(component.downloadBytes ?? 0)}',
    };
    final base = <String, Object?>{
      'id': component.id,
      'weight': component.estimatedSeconds,
      'data': data,
    };
    if (check?.ok == true) {
      return {...base, 'skipped': true, 'version': check!.version};
    }
    if (component.jobStep) {
      return {
        ...base,
        'step': true,
        'stage': l10n.phoneSetupStageStarting,
        'data': {
          ...data,
          'runtime': runtime.wireName,
          'openCodeChanged': '$openCodeChanged',
        },
      };
    }
    if (component.app != null) {
      // The native job waits while the app installs it (see _runApp); a
      // download can take longer than a start, so it waits longer.
      return {
        ...base,
        'step': true,
        'stage': l10n.setupAppStageDownloading,
        'data': {...data, 'waitMinutes': '$appStepWaitMinutes'},
      };
    }
    if (component.native) {
      return {
        ...base,
        'native': true,
        'labels': {
          'download': l10n.phoneSetupStageDownloadingLinux,
          'unpack': l10n.phoneSetupStageUnpackingLinux,
        },
      };
    }
    return {
      ...base,
      'script': withSetupPrelude(component.installScript),
      if (component.agentUser) 'agentUser': true,
    };
  }

  /// Checks [job]'s components: the Linux base by asking Android whether
  /// it is installed, the rest in one proot run. Nothing inside Ubuntu can
  /// pass before Ubuntu is there.
  Future<Map<String, SetupCheckResult>> _check(List<SetupComponent> job) =>
      PerfTrace.span(
        'setup.check',
        () => _checkUntraced(job),
        attrs: {'components': job.length},
      );

  Future<Map<String, SetupCheckResult>> _checkUntraced(
    List<SetupComponent> job,
  ) async {
    // App-side components do not live in Linux: asked even before it is
    // there.
    final apps = await _checkApps(job);
    final status = await _linux.status();
    if (!status.installed) return apps;
    final results = <String, SetupCheckResult>{};
    for (final agentUser in [false, true]) {
      final scripts = <String, String>{
        for (final component in job)
          if (!component.jobStep &&
              component.app == null &&
              component.agentUser == agentUser &&
              component.checkScript.trim().isNotEmpty)
            component.id: component.checkScript,
      };
      if (scripts.isEmpty) continue;
      try {
        final script = combinedCheckScript(scripts);
        final run = agentUser
            ? await _linux.runAgentSetupCheck(script)
            : await _linux.run(script, timeout: const Duration(minutes: 2));
        results.addAll(parseCombinedChecks(run.output));
      } on BuiltinLinuxException {
        if (!agentUser) rethrow;
        // The first agent install has not created oc yet. Its root bootstrap
        // precedes these components; missing checks leave them pending.
      }
    }
    // Android's own marker is the truth for the base; its script only
    // supplies the version.
    for (final component in job.where((c) => c.native)) {
      results[component.id] = (
        ok: true,
        version: results[component.id]?.version,
      );
    }
    return {...results, ...apps};
  }

  Future<Map<String, SetupCheckResult>> _checkApps(
    List<SetupComponent> components,
  ) async => {
    for (final component in components)
      if (component.app case final app?) component.id: await _checkApp(app),
  };

  /// A check that cannot answer says "not installed": the install is
  /// idempotent, so the worst case is a quick no-op.
  static Future<SetupCheckResult> _checkApp(SetupAppComponent app) async {
    try {
      return await app.check();
    } catch (_) {
      return (ok: false, version: null);
    }
  }

  Map<String, ComponentProgress> _afterChecks(
    List<SetupComponent> job,
    Map<String, SetupCheckResult> checks,
  ) => {
    for (final component in job)
      component.id: checks[component.id]?.ok == true
          ? ComponentProgress(
              id: component.id,
              state: ComponentState.skipped,
              version: checks[component.id]!.version,
            )
          : ComponentProgress(id: component.id, state: ComponentState.pending),
  };

  void _emitLocal(
    String jobId,
    Map<String, ComponentProgress> components,
    int startedAt, {
    SetupState state = SetupState.running,
    String? error,
    List<SetupComponent>? job,
  }) {
    // The job the caller is starting, not whatever a poll last showed: a
    // refresh of the previous job's record must never decide which rows this
    // job has (build 2064: Add tools died on a null check).
    final rows = job ?? _jobComponents;
    final list = [
      for (final component in rows)
        components[component.id] ??
            ComponentProgress(id: component.id, state: ComponentState.pending),
    ];
    _progress.value = SetupProgress(
      jobId: jobId,
      state: state,
      components: list,
      overall: overallFraction(rows, list, floors: _floors),
      error: error,
      firstSetup: _localFirstSetup,
      adding: _localAdding,
    );
  }
}
