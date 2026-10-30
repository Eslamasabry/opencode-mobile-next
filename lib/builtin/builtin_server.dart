import 'dart:async';
import 'dart:convert';
import 'dart:math';

import 'package:clock/clock.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../domain/server_probe.dart';
import '../domain/opencode_history_copy.dart';
import '../l10n/app_localizations.dart';
import '../state/profiles.dart';
import 'builtin_linux.dart';
import 'deliberate_stop.dart';
import 'quota_collector_host.dart';
import 'setup/setup_engine.dart' show ChannelSetupEngine;
import 'team/builtin_team.dart';

/// The bridge the whole app uses; tests override it.
final builtinLinuxProvider = Provider<BuiltinLinux>((ref) => BuiltinLinux());

/// One starter per app, so the start screen, the opening card and the
/// return-to-app hook all see the same "starting" state and share the
/// once-only automatic start.
final builtinServerStarterProvider = Provider<BuiltinServerStarter>((ref) {
  final starter = BuiltinServerStarter(linux: ref.watch(builtinLinuxProvider));
  ref.onDispose(starter.dispose);
  return starter;
});

/// Shape only: an OpenCode profile at 127.0.0.1:4097 on a runner that has
/// the built-in Linux. Cheap and synchronous, so it can decide whether to ask
/// the bridge at all; it is not the answer by itself, because a developer can
/// point 4097 at anything (adb reverse, a tunnel).
bool looksLikeInAppServer(ServerProfile? profile) =>
    profile != null &&
    profile.backend == ServerBackend.openCode &&
    BuiltinLinux.supported &&
    BuiltinLinux.managesServerUrl(profile.baseUrl);

/// The one answer to "is this the OpenCode that runs inside the app":
/// the profile's shape plus Ubuntu actually installed on this phone.
Future<bool> isInAppServer(ServerProfile? profile, BuiltinLinux linux) async {
  if (!looksLikeInAppServer(profile)) return false;
  try {
    return (await linux.status()).installed;
  } on BuiltinLinuxException {
    return false;
  }
}

/// The saved profile of the in-app server for [flavor], if there is one.
ServerProfile? findBuiltinProfile(ProfileStore store, ServerFlavor flavor) {
  final candidates = store.profiles
      .where(
        (profile) =>
            profile.backend == ServerBackend.openCode &&
            BuiltinLinux.managesServerUrl(profile.baseUrl) &&
            profile.flavor == flavor,
      )
      .toList();
  // Both old runtime aliases now speak OC2. Preserve the selected source's
  // identity instead of adopting whichever alias happened to be saved first.
  for (final profile in candidates) {
    if (profile.id == store.activeId) return profile;
  }
  // A setup resumed without an active connection must still retain its
  // unfinished history proof before considering an ordinary OC2 alias.
  for (final profile in candidates) {
    if (profile.requiresOpenCode2Migration || profile.openCode2HistoryPending) {
      return profile;
    }
  }
  return candidates.firstOrNull;
}

/// The saved profile of the in-app server for [flavor], made on first use.
///
/// One profile per runtime, like the Termux server. The password is made
/// once and lives with the profile in secure storage; the server reads its
/// copy from a root-only file that every start rewrites, so a password the
/// keystore lost is simply replaced.
Future<ServerProfile> ensureBuiltinProfile(
  ProfileStore store, {
  required ServerFlavor flavor,
  required String name,
  String? version,
  Set<String> replaceNames = const {},
}) async {
  final existing = findBuiltinProfile(store, flavor);
  // A name the app generated earlier gives way to [name]; one the person
  // chose stays.
  if (existing != null && replaceNames.contains(existing.name)) {
    existing.name = name;
  }
  final profile =
      existing ??
      ServerProfile(
        id: DateTime.now().microsecondsSinceEpoch.toString(),
        name: name,
        baseUrl: BuiltinLinux.serverUrl,
        flavor: flavor,
      );
  profile.username = BuiltinLinux.serverUsername;
  if (profile.password.isEmpty || profile.requiresPasswordReentry) {
    renewBuiltinPassword(profile);
  }
  if (version != null) profile.serverVersion = version;
  await store.upsert(profile);
  return profile;
}

/// Gives the in-app [profile] a fresh random password. Safe by design: the
/// server reads its copy from a root-only file that every start rewrites, so
/// nothing but this app ever needs the old one.
void renewBuiltinPassword(ServerProfile profile) {
  final random = Random.secure();
  final bytes = List<int>.generate(32, (_) => random.nextInt(256));
  profile.password = base64UrlEncode(bytes).replaceAll('=', '');
  profile.requiresPasswordReentry = false;
}

/// What kind of thing stopped a start, so the page can say why in plain
/// words and the launch path knows whether one more try can help.
enum BuiltinStartProblem {
  /// OpenCode's process ended before it answered.
  exited,

  /// OpenCode kept running but did not answer in time.
  timedOut,

  /// The start was withdrawn (the app left the screen, a policy changed).
  interrupted,

  /// The saved server password is missing: setup has to make a new one.
  passwordMissing,

  /// The password file inside Ubuntu could not be written.
  passwordNotSaved,

  /// The phone did not let the app start OpenCode (the bridge refused).
  refused,

  /// Android process ownership could not be proven; restarting this app is required.
  componentRecovery,

  /// Another start is already running.
  busy,
}

/// Why a start did not end with a server answering our password.
class BuiltinServerStartFailure {
  const BuiltinServerStartFailure.detail(
    String this._detail, {
    this.problem = BuiltinStartProblem.refused,
  }) : _timeoutSeconds = null;
  const BuiltinServerStartFailure.exited()
    : _detail = null,
      problem = BuiltinStartProblem.exited,
      _timeoutSeconds = null;
  const BuiltinServerStartFailure.timedOut(int seconds)
    : _detail = null,
      problem = BuiltinStartProblem.timedOut,
      _timeoutSeconds = seconds;

  final String? _detail;
  final int? _timeoutSeconds;
  final BuiltinStartProblem problem;

  /// Native diagnostics for a redacted Details fold only.
  String? get technicalDetails => _detail;

  /// A fast failure that one more start can fix. A timeout already waited
  /// long, a withdrawn start was not wanted, and a missing password needs
  /// setup, so none of those is tried again on its own.
  bool get retryable => switch (problem) {
    BuiltinStartProblem.exited ||
    BuiltinStartProblem.passwordNotSaved ||
    BuiltinStartProblem.refused => true,
    _ => false,
  };

  /// A short reason for [AppLocalizations.builtinServerStartFailed]: the
  /// technical line kept under Details.
  String reason(AppLocalizations l10n) {
    switch (problem) {
      case BuiltinStartProblem.exited:
        return l10n.builtinServerExited;
      case BuiltinStartProblem.timedOut:
        return l10n.builtinServerTimedOut(_timeoutSeconds!);
      default:
        return _detail!;
    }
  }

  /// Why, in plain words, with the way forward: the page's explanation.
  String explanation(AppLocalizations l10n) => switch (problem) {
    BuiltinStartProblem.exited => l10n.inAppServerStartExitedBody,
    BuiltinStartProblem.timedOut => l10n.inAppServerStartTimedOutBody(
      _timeoutSeconds!,
    ),
    BuiltinStartProblem.interrupted => l10n.inAppServerStartInterruptedBody,
    BuiltinStartProblem.passwordMissing ||
    BuiltinStartProblem.passwordNotSaved => l10n.inAppServerStartPasswordBody,
    BuiltinStartProblem.refused => l10n.inAppServerStartRefusedBody,
    BuiltinStartProblem.componentRecovery =>
      l10n.inAppServerOwnershipRecoveryBody,
    BuiltinStartProblem.busy => l10n.inAppServerStartFailedBody,
  };
}

/// Writes the password file, starts the server (restarting one that runs)
/// and waits until it answers with [profile]'s password. Null on success.
///
/// The single start path: the setup screen and the opening card both call
/// it, so the two can never start the server differently.
Future<BuiltinServerStartFailure?> startBuiltinServer({
  required BuiltinLinux linux,
  required ServerProfile profile,
  Duration readyTimeout = const Duration(seconds: 90),
  Duration pollInterval = const Duration(seconds: 2),
  bool Function()? stillWanted,
  int? recoveryGeneration,
  bool migrateLegacyHistory = false,
  Future<void> Function(ServerProfile profile)? saveRenewedPassword,
}) async {
  const cancelled = BuiltinServerStartFailure.detail(
    'The server start was not confirmed.',
    problem: BuiltinStartProblem.interrupted,
  );
  bool wanted() => stillWanted?.call() ?? true;
  if (!wanted()) return cancelled;
  if (profile.requiresOpenCode2Migration && !migrateLegacyHistory) {
    return const BuiltinServerStartFailure.detail(
      'Your phone server needs to move to OpenCode 2. Your earlier conversations are safe.',
      problem: BuiltinStartProblem.interrupted,
    );
  }
  // The Keystore lost the password (backup restore, phone move): the server
  // takes whatever this start writes, so make a new one, keep it, go on.
  if ((profile.password.isEmpty || profile.requiresPasswordReentry) &&
      saveRenewedPassword != null) {
    try {
      renewBuiltinPassword(profile);
      await saveRenewedPassword(profile);
    } catch (_) {
      profile.password = '';
    }
  }
  if (profile.password.isEmpty) {
    return const BuiltinServerStartFailure.detail(
      'The saved server password is missing.',
      problem: BuiltinStartProblem.passwordMissing,
    );
  }
  // Which step threw, for Details: a start that keeps failing names it.
  var step = 'password';
  try {
    if (profile.requiresOpenCode2Migration) {
      final admission = await linux.run(openCode1HistorySwitchCheckScript);
      if (!admission.ok) {
        return const BuiltinServerStartFailure.detail(
          'Your earlier conversations could not be moved safely. Both histories are unchanged.',
          problem: BuiltinStartProblem.interrupted,
        );
      }
    }
    final written = await linux.run(
      BuiltinLinux.writePasswordScript(profile.password),
      timeout: const Duration(seconds: 60),
    );
    if (!written.ok) {
      return const BuiltinServerStartFailure.detail(
        'The server password could not be saved.',
        problem: BuiltinStartProblem.passwordNotSaved,
      );
    }
    if (!wanted()) return cancelled;
    final script = BuiltinLinux.serverScript(
      runtime: BuiltinLinux.runtimeFor(profile.flavor),
    );
    step = 'start';
    if (recoveryGeneration == null) {
      await linux.startServer(
        script,
        port: BuiltinLinux.serverPort,
        restoreRecipe: BuiltinServerRestoreRecipe(
          profileId: profile.id,
          runtime: BuiltinLinux.runtimeFor(profile.flavor),
        ),
      );
    } else {
      await linux.restartServer(
        script,
        port: BuiltinLinux.serverPort,
        expectedGeneration: recoveryGeneration,
      );
    }
    final deadline = DateTime.now().add(readyTimeout);
    while (wanted()) {
      step = 'probe';
      final probe = await serverProbe(
        baseUrl: BuiltinLinux.serverUrl,
        username: profile.username,
        password: profile.password,
      );
      if (!wanted()) return cancelled;
      if (probe.ok) {
        if (recoveryGeneration != null) {
          step = 'confirm';
          await linux.confirmServerRecovery(
            expectedGeneration: recoveryGeneration,
          );
          if (!wanted()) return cancelled;
        }
        return null;
      }
      step = 'status';
      final status = await linux.status();
      if (!status.serverRunning) {
        return const BuiltinServerStartFailure.exited();
      }
      if (!DateTime.now().isBefore(deadline)) {
        return BuiltinServerStartFailure.timedOut(readyTimeout.inSeconds);
      }
      await Future<void>.delayed(pollInterval);
    }
    return cancelled;
  } catch (error) {
    // A native refusal after the start was withdrawn is the withdrawal
    // (the app left the screen), not the phone saying no.
    if (!wanted()) return cancelled;
    return BuiltinServerStartFailure.detail(
      couldNotStartReason(error, step),
      problem: builtinStartProblem(error),
    );
  }
}

/// Stable native failure code, with legacy detail recognition for older APKs.
@visibleForTesting
BuiltinStartProblem builtinStartProblem(Object error) {
  if (error is BuiltinLinuxException &&
      (error.code == 'runtime_ownership_unknown' ||
          (error.details ?? '').contains('ownershipUnknown'))) {
    return BuiltinStartProblem.componentRecovery;
  }
  return BuiltinStartProblem.refused;
}

/// The reason kept under Details when a start throws: the plain words, then
/// the step that threw, the error's kind and the phone's own description when
/// the native side sent one, so a start that keeps failing can be diagnosed
/// from the card. Only kinds and native check text: never a script or output.
@visibleForTesting
String couldNotStartReason(Object error, String step) {
  final kind = error is BuiltinLinuxException
      ? error.code ?? 'no code'
      : error.runtimeType.toString();
  final details = error is BuiltinLinuxException ? error.details : null;
  final why = details != null && details.isNotEmpty ? '; $details' : '';
  return 'The phone server could not start ($step: $kind$why)';
}

/// Starts the in-app server for the app shell: once on its own when the app
/// opens or comes back with that server selected and it is stopped, and on
/// request from the connection card.
class BuiltinServerStarter extends ChangeNotifier {
  BuiltinServerStarter({
    required this.linux,
    this.readyTimeout = const Duration(seconds: 90),
    this.pollInterval = const Duration(seconds: 2),
  });

  final BuiltinLinux linux;
  final Duration readyTimeout;
  final Duration pollInterval;

  /// App binding persists the selected phone owner before an explicit start.
  /// Failure prevents dispatch rather than risking a different profile's policy.
  Future<void> Function(ServerProfile profile)? beforeManualStart;

  /// Saves a password the start had to make because the saved one was lost.
  /// Unset, a lost password still ends the start as before.
  Future<void> Function(ServerProfile profile)? saveRenewedPassword;

  bool _starting = false;
  BuiltinServerStartFailure? _failure;
  String? _failedProfileID;
  bool _autoStartUsed = false;
  bool? _installed;
  bool _disposed = false;

  /// True while a start is waiting for the server to answer.
  bool get starting => _starting;

  /// The profile a running start brings up, or null when none runs. While
  /// the app switches this phone between OpenCode versions it is the
  /// version being switched to, not the one being left.
  ServerProfile? get startingProfile => _starting ? _startingProfile : null;
  ServerProfile? _startingProfile;

  /// The server this starter is bringing up: [startingProfile] while a start
  /// runs, then for [handover] after a confirmed start answered, the moment
  /// between "the server answers" and "the app connected to it". A status
  /// line uses it to name the server being switched to, never the one being
  /// left during the move to OpenCode 2.
  ServerProfile? get bringingUp {
    final starting = startingProfile;
    if (starting != null) return starting;
    final until = _handoverUntil;
    if (until == null || !clock.now().isBefore(until)) return null;
    return _handoverProfile;
  }

  /// How long [bringingUp] keeps naming a server that answered: a connect
  /// follows within moments.
  static const handover = Duration(seconds: 10);
  ServerProfile? _handoverProfile;
  DateTime? _handoverUntil;

  /// Grows by one on every start that ended with the server answering, so a
  /// screen waiting on a stopped server knows to connect again.
  int get readyCount => _readyCount;
  int _readyCount = 0;

  /// Identifies a confirmed explicit start so recovery can reset its budget.
  String? get manuallyStartedProfileId => _manuallyStartedProfileId;
  String? _manuallyStartedProfileId;
  int get manualReadyCount => _manualReadyCount;
  int _manualReadyCount = 0;

  /// The last start's failure for [profile], or null.
  BuiltinServerStartFailure? failureFor(ServerProfile? profile) =>
      profile != null && profile.id == _failedProfileID ? _failure : null;

  /// Synchronous [isInAppServer] from the last status read, for build
  /// methods. Unknown (never read, or the read failed) counts as no.
  bool recognises(ServerProfile? profile) =>
      _installed == true && looksLikeInAppServer(profile);

  /// Whether the next [autoStartIfStopped] may start the server.
  bool get autoStartAvailable => !_autoStartUsed;

  /// The app opened or came back to the foreground: one automatic start is
  /// allowed again. Never called from a failure path, so a server that dies
  /// on start is not restarted in a loop.
  void allowAutoStart() => _autoStartUsed = false;

  /// Shares the controller's native installation observation with connection
  /// cards even when the subsequent authenticated start fails.
  void observeInstalled(bool installed) {
    if (_installed == installed) return;
    _installed = installed;
    _notify();
  }

  /// [observeInstalled] plus whether the app's own OpenCode process runs.
  void observeStatus(BuiltinLinuxStatus status) {
    if (_installed == status.installed && _running == status.serverRunning) {
      return;
    }
    _installed = status.installed;
    _running = status.serverRunning;
    _notify();
  }

  bool? _running;

  /// Synchronous: the last status read said the in-app OpenCode process
  /// runs. A page uses it to say "isn't answering" rather than "stopped"
  /// when a connect failed while the process is alive.
  bool runningFor(ServerProfile? profile) =>
      _running == true && recognises(profile);

  /// A later authenticated probe completed the same automatic start after its
  /// initial timeout. Clear only that profile's failure and publish readiness
  /// once, so consumers reconnect without retaining a stale failure card.
  void confirmRecovered(ServerProfile profile) {
    if (_starting || _failedProfileID != profile.id || _failure == null) return;
    _failure = null;
    _failedProfileID = null;
    _installed = true;
    _readyCount++;
    _notify();
  }

  void clearFailure() {
    if (_failure == null) return;
    _failure = null;
    _failedProfileID = null;
    _notify();
  }

  /// Starts the server when Ubuntu is installed and the server is not
  /// running, at most once until [allowAutoStart]. A server that is already
  /// running (the app was only in the background) is left alone. Returns
  /// true when it started one that now answers.
  Future<bool> autoStartIfStopped(
    ServerProfile? profile, {
    bool Function()? mayStart,
  }) async {
    if (_autoStartUsed || _starting || !looksLikeInAppServer(profile)) {
      return false;
    }
    _autoStartUsed = true;
    final BuiltinLinuxStatus status;
    try {
      status = await linux.status();
    } on BuiltinLinuxException {
      return false;
    }
    _installed = status.installed;
    if (!status.installed || status.serverRunning) {
      _notify();
      return false;
    }
    if (mayStart != null && !mayStart()) return false;
    return await start(profile!, automatic: true, stillWanted: mayStart) ==
        null;
  }

  /// Starts (or restarts) the server for [profile] and waits for it.
  Future<BuiltinServerStartFailure?> start(
    ServerProfile profile, {
    bool automatic = false,
    bool Function()? stillWanted,
    int? recoveryGeneration,
    bool migrateLegacyHistory = false,
  }) async {
    if (_starting) {
      return const BuiltinServerStartFailure.detail(
        'The phone server is already starting.',
        problem: BuiltinStartProblem.busy,
      );
    }
    _starting = true;
    _startingProfile = profile;
    _handoverProfile = null;
    _handoverUntil = null;
    _failure = null;
    _failedProfileID = null;
    _notify();
    BuiltinServerStartFailure? failure;
    try {
      if (!automatic) await beforeManualStart?.call(profile);
      failure = await startBuiltinServer(
        linux: linux,
        profile: profile,
        readyTimeout: readyTimeout,
        pollInterval: pollInterval,
        stillWanted: () => !_disposed && (stillWanted?.call() ?? true),
        recoveryGeneration: recoveryGeneration,
        migrateLegacyHistory: migrateLegacyHistory,
        saveRenewedPassword: saveRenewedPassword,
      );
    } catch (error) {
      failure = BuiltinServerStartFailure.detail(
        couldNotStartReason(error, 'owner'),
        problem: builtinStartProblem(error),
      );
    }
    _starting = false;
    _startingProfile = null;
    if (failure == null) {
      DeliberateServerStop.clearLater(profile.id);
      _installed = true;
      _running = true;
      _readyCount++;
      if (!automatic) {
        _manualReadyCount++;
        _manuallyStartedProfileId = profile.id;
        _handoverProfile = profile;
        _handoverUntil = clock.now().add(handover);
      }
      // Usage on In-app Ubuntu comes back with it unless it was turned off.
      unawaited(InAppQuotaCollector.restartIfSetUp(profile, linux: linux));
      // A team the person turned on comes back with OpenCode: Android
      // stops both when it reclaims the app.
      if ((!automatic || recoveryGeneration == null) &&
          BuiltinTeam.isBuiltinConfig(profile.orchestration)) {
        unawaited(
          BuiltinTeam(linux: linux).ensureRunning(
            notice: ChannelSetupEngine.deviceStrings().aiteamComponentNotice,
          ),
        );
      }
    } else {
      _failure = failure;
      _failedProfileID = profile.id;
      if (failure.problem == BuiltinStartProblem.exited) _running = false;
    }
    _notify();
    return failure;
  }

  void _notify() {
    if (!_disposed) notifyListeners();
  }

  @override
  void dispose() {
    _disposed = true;
    super.dispose();
  }
}

/// Project folders inside the in-app Ubuntu, read and made through the
/// bridge instead of through OpenCode, so OpenCode is never asked about a
/// folder that does not exist yet.
class BuiltinProjectFolders {
  const BuiltinProjectFolders(this.linux);

  final BuiltinLinux linux;

  static String pathFor(String name) =>
      '${BuiltinLinux.projectsDir}/${name.trim()}';

  Future<List<String>> list() async {
    final result = await linux.run(
      BuiltinLinux.listProjectsScript(),
      timeout: const Duration(seconds: 30),
    );
    if (!result.ok) throw BuiltinLinuxException(_describe(result));
    return BuiltinLinux.parseProjectList(result.output);
  }

  Future<bool> exists(String path) async {
    final result = await linux.run(
      BuiltinLinux.folderExistsScript(path),
      timeout: const Duration(seconds: 30),
    );
    // `test -d` answers 0 or 1; anything else is the bridge failing.
    if (result.exitCode == 0) return true;
    if (result.exitCode == 1) return false;
    throw BuiltinLinuxException(_describe(result));
  }

  /// Makes [path] a git project unless it already is a folder. `created` is
  /// false for a folder that was already there.
  Future<({String path, bool created})> create(String path) async {
    final result = await linux.run(
      BuiltinLinux.createFolderScript(path),
      timeout: const Duration(seconds: 60),
    );
    final lines = result.output.trim().split('\n');
    final last = lines.isEmpty ? '' : lines.last.trim();
    if (result.ok && last == 'created $path') {
      return (path: path, created: true);
    }
    if (result.ok && last == 'exists $path') {
      return (path: path, created: false);
    }
    throw BuiltinLinuxException(_describe(result));
  }

  /// Makes the folder's first commit when it has none (an empty one, no file
  /// touched). Throws when the folder is not a git repository or the commit
  /// failed.
  Future<void> makeFirstCommit(String path) async {
    final result = await linux.run(
      BuiltinLinux.firstCommitScript(path),
      timeout: const Duration(seconds: 30),
    );
    final last = result.output.trim().split('\n').last.trim();
    if (result.ok && (last == 'committed' || last == 'exists')) return;
    throw BuiltinLinuxException(_describe(result));
  }

  /// True when [path] is a git folder with no commit yet.
  Future<bool> isEmptyRepo(String path) async {
    final result = await linux.run(
      BuiltinLinux.repoStateScript(path),
      timeout: const Duration(seconds: 20),
    );
    if (!result.ok) throw BuiltinLinuxException(_describe(result));
    return result.output.trim().split('\n').last.trim() == 'empty';
  }

  static String _describe(BuiltinLinuxRunResult result) {
    final output = result.output.trim();
    return output.isEmpty ? 'exit ${result.exitCode}' : output;
  }
}
