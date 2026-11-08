part of '../setup_engine.dart';

const _runtimeOwnershipUnknown = 'runtime_ownership_unknown';
final _legacyOwnershipUnknown = RegExp(r'\bownershipUnknown\b');

/// Preserve the native recovery category through the persisted setup job.
/// It is rendered as plain words plus recovery by the progress view.
bool _isRuntimeOwnershipFailure(Object? error) {
  if (error is BuiltinLinuxException) {
    return error.code == _runtimeOwnershipUnknown ||
        _legacyOwnershipUnknown.hasMatch(error.details ?? '') ||
        _legacyOwnershipUnknown.hasMatch(error.message);
  }
  return error is String &&
      (error == _runtimeOwnershipUnknown ||
          _legacyOwnershipUnknown.hasMatch(error));
}

/// A [ValueNotifier] that tells its engine when the first listener comes and
/// the last goes, so polling runs only while someone watches (or a job
/// runs).
class _WatchedProgress extends ValueNotifier<SetupProgress> {
  _WatchedProgress(this._changed) : super(SetupProgress.idle);

  final VoidCallback _changed;
  var _count = 0;

  bool get watched => _count > 0;

  @override
  set value(SetupProgress next) {
    final ended = next.endedBefore(value);
    super.value = next;
    // Not a listener (that would keep the engine polling): pages that show
    // what is installed read the phone again when a job ends.
    if (ended) setupToolsChanged.value++;
  }

  @override
  void addListener(VoidCallback listener) {
    super.addListener(listener);
    _count++;
    if (_count == 1) _changed();
  }

  @override
  void removeListener(VoidCallback listener) {
    super.removeListener(listener);
    if (_count > 0) _count--;
    if (_count == 0) _changed();
  }
}

// ---- pure parts, tested directly --------------------------------------------

/// How long the native job waits for an app-side component: a download on
/// a slow network can take far longer than starting OpenCode.
const appStepWaitMinutes = 120;

/// An app-side failure in words the failure matcher
/// ([describeSetupFailure]) turns into the right plain message; it is the
/// component's recorded error, never an exception's text.
String appFailureReason(SetupAppFailureKind kind) => switch (kind) {
  SetupAppFailureKind.offline => 'Could not connect to the download server',
  SetupAppFailureKind.noSpace => 'No space left on device',
  SetupAppFailureKind.checksum => 'The download did not match its checksum',
  SetupAppFailureKind.unsupported => 'This phone cannot run it',
  SetupAppFailureKind.busy => 'Another download of it is running',
  SetupAppFailureKind.cancelled => 'cancelled',
  SetupAppFailureKind.failed => 'The download failed',
};

/// The params a new run gets. Continue keeps what the stopped job was for
/// (OpenCode 2, a version) unless the caller asks for something else; job
/// facts ([SetupJobParams], such as "first setup") are not a request for
/// something else, so they are laid over the stopped job's params rather
/// than replacing them. A stopped job's own facts carry over the same way.
/// A completed job also preserves its runtime unless explicitly overridden;
/// changing the fresh-install default must not upgrade an installed server.
Map<String, Map<String, String>> effectiveJobParams(
  Map<String, Map<String, String>> requested,
  SetupJobRecord? last,
) {
  final facts = requested[SetupJobParams.key];
  final components = {
    for (final entry in requested.entries)
      if (entry.key != SetupJobParams.key) entry.key: entry.value,
  };
  final base = components.isEmpty && last != null && last.canContinue
      ? last.params
      : components;
  // Interrupted legacy jobs resume on the only supported runtime. A v1
  // package pin cannot leak into the OC2 installer. Starting OC2 still copies
  // history before its first launch; explicit moves preflight both histories.
  final openCode = {...?base[SetupComponentIds.openCode]};
  if (openCode['runtime'] == 'opencode1' ||
      (openCode['version']?.startsWith('1.') ?? false)) {
    openCode.remove('version');
  }
  const runtime = 'opencode2';
  return {
    ...base,
    SetupComponentIds.openCode: {...openCode, 'runtime': runtime},
    if (facts != null)
      SetupJobParams.key: {...?base[SetupJobParams.key], ...facts},
  };
}

/// [ids] plus every required component, expanded by `dependsOn` and put in
/// dependency order (registry order among equals).
List<SetupComponent> expandSelection(
  List<SetupComponent> registry,
  Set<String> ids,
) {
  final byId = {for (final component in registry) component.id: component};
  final wanted = <String>{};
  void want(String id) {
    final component = byId[id];
    if (component == null || !wanted.add(id)) return;
    component.dependsOn.forEach(want);
  }

  for (final component in registry) {
    if (component.required || ids.contains(component.id)) want(component.id);
  }
  final ordered = <SetupComponent>[];
  final placed = <String>{};
  final visiting = <String>{};
  void place(SetupComponent component) {
    if (placed.contains(component.id)) return;
    if (!visiting.add(component.id)) {
      throw StateError(
        'Setup components depend on each other: ${component.id}',
      );
    }
    for (final dependency in component.dependsOn) {
      final other = byId[dependency];
      if (other != null && wanted.contains(dependency)) place(other);
    }
    visiting.remove(component.id);
    placed.add(component.id);
    ordered.add(component);
  }

  for (final component in registry) {
    if (wanted.contains(component.id)) place(component);
  }
  return ordered;
}

/// 0..1 over [components], each weighted by its estimate. A running one
/// counts its real fraction, or half when it only reports stages, and never
/// less than it already reached ([floors], updated here).
double overallFraction(
  List<SetupComponent> components,
  List<ComponentProgress> progress, {
  Map<String, double>? floors,
}) {
  var total = 0.0;
  var done = 0.0;
  for (var i = 0; i < components.length && i < progress.length; i++) {
    final weight = components[i].estimatedSeconds.toDouble();
    total += weight;
    done += weight * _fraction(progress[i], floors);
  }
  if (total <= 0) return 0;
  return (done / total).clamp(0, 1).toDouble();
}

double _fraction(ComponentProgress progress, Map<String, double>? floors) {
  final double raw = switch (progress.state) {
    ComponentState.done || ComponentState.skipped => 1,
    ComponentState.running => progress.fraction ?? .5,
    _ => 0,
  };
  if (floors == null) return raw;
  if (progress.state == ComponentState.running ||
      progress.state == ComponentState.done ||
      progress.state == ComponentState.skipped) {
    // A running component never shows as finished before it is.
    final capped = progress.state == ComponentState.running
        ? raw.clamp(0, .98).toDouble()
        : raw;
    final best = capped > (floors[progress.id] ?? 0)
        ? capped
        : floors[progress.id]!;
    floors[progress.id] = best;
    return best;
  }
  // Pending or failed after an interruption: keep what it had reached.
  return floors[progress.id] ?? raw;
}

/// Seconds left: the estimates not yet done, scaled by how fast this job has
/// really gone so far. Null until it has made real progress for at least
/// [minimum], so an early guess never shows.
int? estimateEta({
  required List<SetupComponent> components,
  required List<ComponentProgress> progress,
  required double overall,
  required Duration elapsed,
  Duration minimum = const Duration(seconds: 10),
}) {
  if (elapsed < minimum) return null;
  var total = 0.0;
  var before = 0.0;
  for (var i = 0; i < components.length && i < progress.length; i++) {
    final weight = components[i].estimatedSeconds.toDouble();
    total += weight;
    if (progress[i].state == ComponentState.skipped) before += weight;
  }
  final doneNow = overall * total - before;
  if (doneNow <= 0) return null;
  final remaining = total * (1 - overall);
  if (remaining <= 0) return 0;
  final pace = elapsed.inMilliseconds / 1000 / doneNow;
  return (remaining * pace).ceil();
}

String? _string(Object? value) =>
    value is String && value.isNotEmpty ? value : null;

int? _int(Object? value) => value is num ? value.toInt() : null;

Map<String, String> _strings(Object? value) => {
  if (value is Map)
    for (final entry in value.entries)
      if (entry.key is String && entry.value != null)
        entry.key as String: '${entry.value}',
};

SetupState _jobState(String state) => switch (state) {
  'running' => SetupState.running,
  'done' => SetupState.done,
  'failed' => SetupState.failed,
  'cancelled' => SetupState.cancelled,
  _ => SetupState.interrupted,
};

/// [record] as the screens see it: states, plain-word errors, the weighted
/// bar and (once honest) the ETA. [components] are the job's, in its order.
SetupProgress progressFromRecord(
  SetupJobRecord record,
  List<SetupComponent> components,
  AppLocalizations l10n, {
  required DateTime now,
  Map<String, double>? floors,
}) {
  final titles = {for (final c in components) c.id: c.shortTitle};
  final list = <ComponentProgress>[];
  String? jobError;
  for (final component in record.components) {
    final state = component.componentState;
    String? error;
    if (state == ComponentState.failed) {
      final failure = describeSetupFailure(
        component,
        record.logTail,
        titles[component.id] ?? component.id,
        l10n,
        step: component.id == SetupComponentIds.start,
      );
      error = failure.component;
      jobError ??= failure.job;
    }
    list.add(
      ComponentProgress(
        id: component.id,
        state: state,
        stage: component.stage,
        bytesDone: component.done,
        bytesTotal: component.total,
        percent: component.percent,
        version: component.version,
        error: error,
      ),
    );
  }
  final state = _jobState(record.state);
  final overall = overallFraction(components, list, floors: floors);
  final elapsed = now.difference(
    DateTime.fromMillisecondsSinceEpoch(record.startedAt),
  );
  return SetupProgress(
    jobId: record.jobId,
    state: state,
    components: list,
    overall: state == SetupState.done ? 1 : overall,
    current: record.current,
    etaSeconds: state == SetupState.running
        ? estimateEta(
            components: components,
            progress: list,
            overall: overall,
            elapsed: elapsed,
          )
        : null,
    error: state == SetupState.failed
        ? (_isRuntimeOwnershipFailure(record.error)
                  ? _runtimeOwnershipUnknown
                  : record.failureKind == SetupFailureKind.persistence
                  ? l10n.phoneSetupErrorInstall('OpenCode')
                  : jobError) ??
              l10n.phoneSetupErrorInstall(
                titles[record.current] ?? record.current ?? 'OpenCode',
              )
        : null,
    // A failure can arrive before the script emits output. Keep its original
    // reason in Details even after the row is translated into plain words.
    logTail: KitRedact.text(
      setupLogForPeople(
        [
          record.logTail.trimRight(),
          if (state == SetupState.failed) ...{
            for (final raw in [
              for (final row in record.components)
                if (row.componentState == ComponentState.failed) row.error,
              record.error,
            ])
              if (raw != null &&
                  raw.trim().isNotEmpty &&
                  !record.logTail.contains(raw.trim()))
                raw.trim(),
          },
        ].where((line) => line.isNotEmpty).join('\n'),
      ),
    ),
    firstSetup: SetupJobParams.isFirstSetup(record.params),
    adding: SetupJobParams.addingIds(record.params),
    phoneUnsupported:
        state == SetupState.failed &&
        record.components.any(
          (row) =>
              row.componentState == ComponentState.failed &&
              nodeUnsupported(row),
        ),
  );
}

/// "Adding AI Team" (or "Adding Python and AI Team") for an "Add tools" job
/// that adds [ids], named by their titles in [registry].
String setupAddingTitle(
  AppLocalizations l10n,
  List<SetupComponent> registry,
  List<String> ids,
) {
  final names = [
    for (final id in ids)
      for (final component in registry)
        if (component.id == id) component.shortTitle,
  ];
  final String joined;
  if (names.length <= 1) {
    joined = names.join();
  } else {
    joined = l10n.phoneSetupStartListPair(
      names
          .sublist(0, names.length - 1)
          .join(l10n.phoneSetupStartListSeparator),
      names.last,
    );
  }
  return l10n.aiteamComponentAddingTitle(joined);
}

final _offline = RegExp(
  r'Could not resolve|Temporary failure resolving|Unable to resolve host|'
  r'No address associated|Network is unreachable|Could not connect to|'
  r'Failed to connect|Connection timed out|timed out after|'
  r'curl: \((6|7|28|35|52|56)\)|curl exit (6|7|28|35|52|56)\)|'
  r'UnknownHostException|SocketTimeoutException|ConnectException|'
  r'ECONNRESET|ETIMEDOUT|EAI_AGAIN|ENOTFOUND|Failed to fetch',
  caseSensitive: false,
);
final _noSpace = RegExp(
  r'No space left on device|ENOSPC|not enough free space',
  caseSensitive: false,
);
final _interrupted = RegExp(
  r'terminated with signal (?:9|15)\b|\bSIG(?:KILL|TERM)\b',
  caseSensitive: false,
);
final _checksum = RegExp(
  r'checksum|did not match its checksum',
  caseSensitive: false,
);

/// [failed] is the Node.js step that found no way to run Node on this
/// phone (node_compat.dart, issue #117).
bool nodeUnsupported(SetupJobComponent failed) =>
    failed.id == SetupComponentIds.node &&
    (failed.error ?? '').startsWith(NodeCompatFailure.unsupported);

/// A failed component in plain words, for its row ([component]) and for the
/// whole job ([job]). The technical reason stays in the log tail, which the
/// details view shows.
({String component, String job}) describeSetupFailure(
  SetupJobComponent failed,
  String logTail,
  String title,
  AppLocalizations l10n, {
  bool step = false,
}) {
  final reason = failed.error ?? '';
  if (_isRuntimeOwnershipFailure(reason)) {
    return (component: _runtimeOwnershipUnknown, job: _runtimeOwnershipUnknown);
  }
  if (reason.startsWith('[oc] Some packages are still unfinished.') ||
      reason.startsWith('[oc] Package repair could not finish.') ||
      reason.startsWith('[oc] A required package is not fully installed.')) {
    final text = l10n.phoneSetupErrorPackageRepair;
    return (component: text, job: text);
  }
  if (step) {
    // The finisher may return native exception text. Keep that in Details;
    // it is not safe or useful to interpolate into the person's recovery.
    final text = l10n.phoneSetupErrorCannotStart;
    return (component: text, job: text);
  }
  // Issue #117: Node.js crashes on this phone's system in every safe mode.
  // Not a retry, not a download: the way forward is another computer.
  if (nodeUnsupported(failed)) {
    final text = l10n.phoneSetupErrorNodeUnsupported;
    return (component: text, job: text);
  }
  // The OpenCode install names its own reason on its last line.
  for (final (marker, text) in [
    (OpenCodeInstallFailure.noProgram, l10n.phoneSetupErrorOpenCodeNoProgram),
    (OpenCodeInstallFailure.wontRun, l10n.phoneSetupErrorOpenCodeWontRun),
    (OpenCodeInstallFailure.noStart, l10n.phoneSetupErrorOpenCodeNoStart),
  ]) {
    if (reason.startsWith(marker)) return (component: text, job: text);
  }
  // Only the end of the log: an old warning further up is not the reason.
  final lines = setupLogForPeople(logTail).trimRight().split('\n');
  final recent = [
    ...lines.skip(lines.length > 15 ? lines.length - 15 : 0),
    reason,
  ].join('\n');
  if (_noSpace.hasMatch(recent)) {
    final text = l10n.phoneSetupErrorNoSpace(title);
    return (component: text, job: text);
  }
  if (_interrupted.hasMatch(recent)) {
    // A signal proves interruption, not why Android or another process sent
    // it. In particular, do not claim that low memory caused SIGTERM.
    final text = l10n.phoneSetupErrorInterrupted;
    return (component: text, job: text);
  }
  if (_offline.hasMatch(recent)) {
    return (
      component: l10n.phoneSetupErrorOffline(title),
      job: l10n.phoneSetupErrorNoInternet,
    );
  }
  if (_checksum.hasMatch(reason)) {
    final text = l10n.phoneSetupErrorChecksum(title);
    return (component: text, job: text);
  }
  final text = l10n.phoneSetupErrorInstall(title);
  return (component: text, job: text);
}
