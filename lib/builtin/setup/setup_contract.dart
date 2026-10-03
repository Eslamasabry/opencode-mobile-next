import 'package:flutter/foundation.dart';

import '../../state/download_size.dart';

/// The shared contract of the phone setup engine
/// (docs/design/phone-setup-v2-2026-09-24.md). Screens depend on these types
/// only; the engine implements [SetupEngine]. Nothing here knows about any
/// particular component, so adding one (AI Team, Python, anything) never
/// changes this file.

/// Where a setup job installs and runs OpenCode: the app's own Linux, or
/// Termux's. The components are the same; a few checks differ by host.
enum SetupHostKind { builtin, termux }

/// One installable piece: a check that says whether it is there, and an
/// install that puts it there and reports progress with `::oc` lines.
@immutable
class SetupComponent {
  const SetupComponent({
    required this.id,
    required this.title,
    required this.shortTitle,
    required this.checkScript,
    required this.installScript,
    this.dependsOn = const [],
    this.required = false,
    this.defaultOn = false,
    this.estimatedSeconds = 60,
    this.downloadBytes,
    this.downloadSize = const DownloadSize.unknown(),
    this.removeScript,
    this.presenceScript,
    this.sizeScript,
    this.why,
    this.summary,
    this.native = false,
    this.agentUser = false,
    this.jobStep = false,
    this.app,
  });

  final String id;
  final String title;
  final String shortTitle;

  /// One plain sentence shown when a required component cannot be switched
  /// off ("OpenCode needs it to run").
  final String? why;

  /// What an optional component does, in one line, beside its switch
  /// ("Speak instead of typing, even offline"). Null says nothing more
  /// than the title.
  final String? summary;
  final List<String> dependsOn;
  final bool required;
  final bool defaultOn;

  /// Measured on a mid-range phone; weights the overall bar and the ETA.
  final int estimatedSeconds;

  /// Display/preflight estimate only. Never use this for mobile consent.
  final int? downloadBytes;

  /// Trusted payload evidence for consent. Unknown unless a pinned manifest
  /// covers this installation; apt/npm estimates and HEAD overrides are not it.
  final DownloadSize downloadSize;

  /// Exit 0 = installed and healthy; the last line of output is its version.
  final String checkScript;

  /// Idempotent. Reports with `::oc stage|bytes|percent|version` lines.
  final String installScript;
  final String? removeScript;

  /// Removal inventory, independent of pinned-version/health checks: exit 0
  /// means present (including a partial install), 1 absent, anything else
  /// unknown. Output is discarded. Null means presence cannot be verified.
  /// Native components use the native installed status instead.
  final String? presenceScript;

  /// What removing it would give back, for the remove question: prints
  /// kilobytes on its last line and exits 0. Null (or a failure) leaves
  /// the figure out rather than guessing.
  final String? sizeScript;

  /// Installed by native code rather than a script (the Linux base itself).
  final bool native;

  /// Install in the fixed non-root oc view. Never a caller-supplied uid.
  final bool agentUser;

  /// Not something installed but a step the engine itself runs at the end of
  /// every job: starting OpenCode and connecting to it. It is in the registry
  /// so the progress checklist can name it; lists of things to install
  /// (Customize, sizes) leave it out.
  final bool jobStep;

  /// Installed by the app itself, into its own storage, instead of by a
  /// script inside Linux (the voice typing model). Its check, install and
  /// removal are Dart calls, so it works on either host and before Linux is
  /// there; the native job runs it as a step the app completes. The scripts
  /// of such a component are empty.
  final SetupAppComponent? app;

  /// The same component with [bytes] as its download size: an app-side
  /// component knows its real size only once it has asked the device.
  SetupComponent withDownloadBytes(int? bytes, {DownloadSize? evidence}) =>
      SetupComponent(
        id: id,
        title: title,
        shortTitle: shortTitle,
        checkScript: checkScript,
        installScript: installScript,
        dependsOn: dependsOn,
        required: required,
        defaultOn: defaultOn,
        estimatedSeconds: estimatedSeconds,
        downloadBytes: bytes,
        downloadSize: evidence ?? downloadSize,
        removeScript: removeScript,
        presenceScript: presenceScript,
        sizeScript: sizeScript,
        why: why,
        summary: summary,
        native: native,
        agentUser: agentUser,
        jobStep: jobStep,
        app: app,
      );

  /// Keep the selected app component's payload evidence with its display size.
  SetupComponent withAppOffer(SetupAppOffer offer) =>
      withDownloadBytes(offer.downloadBytes, evidence: offer.downloadSize);
}

/// What an app-side component offers this phone, from [SetupAppComponent.offer].
@immutable
class SetupAppOffer {
  const SetupAppOffer({
    required this.downloadBytes,
    this.installed = false,
    this.downloadSize = const DownloadSize.unknown(),
  });

  /// What installing it downloads (for an installed one, what removing it
  /// frees).
  final int downloadBytes;
  final bool installed;

  /// Payload for a new install; independent of removal/storage display bytes.
  final DownloadSize downloadSize;
}

/// Where an app-side install is, for the progress row.
enum SetupAppStage { downloading, verifying }

@immutable
class SetupAppProgress {
  const SetupAppProgress({
    required this.stage,
    this.bytesDone,
    this.bytesTotal,
  });

  final SetupAppStage stage;
  final int? bytesDone;
  final int? bytesTotal;
}

/// Why an app-side install or removal did not happen, as a kind the engine
/// turns into plain words; no exception text crosses this API.
enum SetupAppFailureKind {
  offline,
  noSpace,
  checksum,
  unsupported,
  busy,
  cancelled,
  failed,
}

class SetupAppFailure implements Exception {
  const SetupAppFailure(this.kind);

  final SetupAppFailureKind kind;

  @override
  String toString() => 'SetupAppFailure(${kind.name})';
}

/// A [SetupComponent] the app installs itself ([SetupComponent.app]).
///
/// [check] answers as a check script would; [install] is idempotent and
/// resumes what an earlier, interrupted run left; [cancel] stops a running
/// install and keeps what it downloaded for the next run.
abstract class SetupAppComponent {
  /// Null when this phone cannot have it at all: the item is not offered.
  Future<SetupAppOffer?> offer();

  /// Installed and usable; the version is shown on the finished row.
  Future<({bool ok, String? version})> check();

  /// Installs it, reporting through [onProgress]; returns its version.
  /// Throws [SetupAppFailure].
  Future<String?> install({
    required void Function(SetupAppProgress progress) onProgress,
  });

  void cancel();

  /// Removes it and confirms it is gone. Throws [SetupAppFailure].
  Future<void> remove();
}

enum SetupState { idle, running, done, failed, interrupted, cancelled }

enum ComponentState { pending, checking, running, done, failed, skipped }

@immutable
class ComponentProgress {
  const ComponentProgress({
    required this.id,
    required this.state,
    this.stage,
    this.bytesDone,
    this.bytesTotal,
    this.percent,
    this.version,
    this.error,
  });

  final String id;
  final ComponentState state;

  /// What is happening now, in plain words, from `::oc stage`.
  final String? stage;
  final int? bytesDone;

  /// Null or 0 when the total is unknown.
  final int? bytesTotal;
  final double? percent;
  final String? version;
  final String? error;

  /// This component's own fraction from its real signal, or null when it
  /// only reports stages (the view shows it as indeterminate).
  double? get fraction {
    if (state == ComponentState.done || state == ComponentState.skipped) {
      return 1;
    }
    final total = bytesTotal;
    if (total != null && total > 0 && bytesDone != null) {
      return (bytesDone! / total).clamp(0, 1).toDouble();
    }
    if (percent != null) return (percent! / 100).clamp(0, 1).toDouble();
    return null;
  }
}

@immutable
class SetupProgress {
  const SetupProgress({
    required this.state,
    required this.components,
    required this.overall,
    this.current,
    this.etaSeconds,
    this.error,
    this.logTail = '',
    this.jobId,
    this.firstSetup = false,
    this.adding = const [],
  });

  static const idle = SetupProgress(
    state: SetupState.idle,
    components: [],
    overall: 0,
  );

  final SetupState state;

  /// In install order; only the components of this job.
  final List<ComponentProgress> components;

  /// 0..1, weighted by each component's estimate.
  final double overall;
  final String? current;

  /// Null until there is enough real progress to estimate honestly.
  final int? etaSeconds;
  final String? error;
  final String logTail;

  /// Which job this is; a new `run` makes a new one, so a view can reset
  /// its bar exactly when the job changes. Null while idle.
  final String? jobId;

  /// The job was started as the phone's first setup (screen A or the
  /// first-run welcome), so it ends on "name your first project". Kept in
  /// the job's own params ([SetupJobParams]) so it survives the app being
  /// killed: a notification tap after a cold start still knows where the
  /// job should end.
  final bool firstSetup;

  /// The tools an "Add tools" job adds, by component id; empty for a first
  /// setup or an update. The screen and the notification then say "Adding
  /// AI Team" instead of "Setting up OpenCode on this phone". Kept in the
  /// job's params like [firstSetup].
  final List<String> adding;

  bool get canContinue =>
      state == SetupState.failed ||
      state == SetupState.interrupted ||
      state == SetupState.cancelled;

  /// The job has ended, one way or another: what it installed is on the
  /// phone (or not) for good, so screens that show it read the phone again.
  bool get ended => canContinue || state == SetupState.done;

  /// Whether [next] is the moment a job ended (a first end of this job).
  bool endedBefore(SetupProgress previous) =>
      ended && (!previous.ended || previous.jobId != jobId);
}

/// Bumped whenever a setup job ends, however it ends: what it installed is
/// on the phone for good, so the pages that list installed tools read it
/// again (This phone, Settings > AI Team). A separate signal from the
/// engine's progress, which only a watched engine keeps polling for.
final ValueNotifier<int> setupToolsChanged = ValueNotifier<int>(0);

/// Facts about a job rather than a component, kept in the job's params
/// under [key] so setup.json carries them without a schema change: the
/// native runner stores params opaquely, and components read only their own
/// id's entry.
abstract final class SetupJobParams {
  static const key = '_job';

  /// Marks a job as the phone's first setup ([SetupProgress.firstSetup]).
  static const firstSetup = <String, Map<String, String>>{
    key: {'first': '1'},
  };

  static bool isFirstSetup(Map<String, Map<String, String>> params) =>
      params[key]?['first'] == '1';

  /// Marks a job as adding [ids] to a phone that is set up
  /// ([SetupProgress.adding]).
  static Map<String, Map<String, String>> adding(Iterable<String> ids) => {
    key: {'adding': ids.join(',')},
  };

  static List<String> addingIds(Map<String, Map<String, String>> params) => [
    for (final id in (params[key]?['adding'] ?? '').split(','))
      if (id.trim().isNotEmpty) id.trim(),
  ];
}

/// Runs any set of components; first setup, "Add tools" and updates are all
/// just different selections. Running again after an interruption skips what
/// the check scripts find installed, so every run is also a resume.
abstract class SetupEngine {
  /// Every component the app knows, in dependency order.
  List<SetupComponent> get registry;

  /// The live progress of the current or last job.
  ValueListenable<SetupProgress> get progress;

  /// Starts (or continues) a job for [ids] plus what they depend on. Required
  /// components are always included. [params] are passed to scripts by
  /// component id (for example opencode: {runtime: opencode2}).
  Future<void> run(
    Set<String> ids, {
    Map<String, Map<String, String>> params = const {},
  });

  /// Stops the running job; finished components stay installed.
  Future<void> cancel();

  /// Reads the persisted job (after an app restart) into [progress].
  Future<void> restore();

  /// Ids of optional components that are installed now.
  Future<Set<String>> installedOptional();
}

/// A job's log as people see it (the details view, a failed-job report):
/// the scripts' own lines, never the app's `OCTRACE` timing lines, which
/// belong in the device log and diagnostics only. They never reach the job
/// log on purpose; this keeps one that slipped in from burying the real
/// error at the end.
String setupLogForPeople(String log) {
  if (!log.contains('OCTRACE')) return log;
  return log.split('\n').where((line) => !line.contains('OCTRACE')).join('\n');
}
