import 'dart:ui' show Locale;

import 'package:shared_preferences/shared_preferences.dart';

import '../../l10n/app_localizations.dart';
import '../../state/move_to_opencode2.dart';
import '../../state/profiles.dart';
import '../builtin_linux.dart';
import '../builtin_server.dart';
import 'node_compat.dart';
import 'setup_engine.dart';

/// The last step of a setup job: start OpenCode inside the app and connect
/// to it, through the same [BuiltinServerStarter] the opening card and the
/// return-to-app hook use, so setup can never start the server differently.
///
/// A server that already runs the right OpenCode, with the app connected to
/// it, is left alone ("Add tools" must not cut a running session); anything
/// else starts (or restarts) it and connects.
class BuiltinSetupFinisher {
  BuiltinSetupFinisher({
    required this.store,
    required this.starter,
    required this.connect,
    required this.isConnectedTo,
    required this.strings,
  });

  final ProfileStore store;
  final BuiltinServerStarter starter;

  /// Connects the app to [profile]; null on success, else why not.
  final Future<String?> Function(ServerProfile profile) connect;
  final bool Function(ServerProfile profile) isConnectedTo;
  final AppLocalizations Function() strings;

  BuiltinLinux get linux => starter.linux;

  /// Copies the Node.js safe mode the Node step chose inside Linux (issue
  /// #117) into the app's preference, which the server start reads. A read
  /// that fails changes nothing: Linux's own file still reaches the server.
  Future<void> syncNodeCompat() async {
    try {
      final read = await linux.run(
        'cat ${NodeCompat.stateFile} 2>/dev/null || true',
        timeout: const Duration(seconds: 30),
      );
      if (!read.ok) return;
      await NodeCompatStore.write(
        await SharedPreferences.getInstance(),
        NodeCompat.parseState(read.output),
      );
    } catch (_) {}
  }

  Future<String?> call(SetupFinishRequest request) async {
    final l10n = strings();
    request.runtime.requireSupported();
    final profile = await ensureBuiltinProfile(
      store,
      flavor: ServerFlavor.v2,
      name: l10n.phoneSetupProfileName,
      version: request.version,
      // The older setup screen's names, in either language.
      replaceNames: {
        for (final words in [
          lookupAppLocalizations(const Locale('en')),
          lookupAppLocalizations(const Locale('ar')),
        ])
          for (final runtime in [words.setupRuntimeTwo])
            words.builtinServerProfileName(runtime),
      },
    );
    // Reuse the selected migration source, including its local keys and team.
    // Only a source without its own team may adopt an older alias's team.
    await teamFollowsRuntime(
      store,
      profile,
      sameHost: (p) =>
          p.backend == ServerBackend.openCode &&
          BuiltinLinux.managesServerUrl(p.baseUrl),
    );
    await syncNodeCompat();
    var running = false;
    try {
      running = (await linux.status()).serverRunning;
    } on BuiltinLinuxException {
      running = false;
    }
    if (!request.openCodeChanged && running && isConnectedTo(profile)) {
      return null;
    }
    // Running but not ours to trust (another runtime, or the app is not
    // connected to it): restarting costs seconds and removes the doubt.
    final migrating = profile.requiresOpenCode2Migration;
    final failure = await starter.start(
      profile,
      migrateLegacyHistory: migrating,
    );
    if (failure?.problem == BuiltinStartProblem.componentRecovery) {
      return 'runtime_ownership_unknown';
    }
    if (failure != null) {
      return migrating ? l10n.setupSwitchFailed : failure.reason(l10n);
    }
    if (migrating) {
      profile
        ..requiresOpenCode2Migration = false
        ..openCode2HistoryPending = true;
      try {
        await store.upsert(profile);
      } catch (_) {
        profile
          ..requiresOpenCode2Migration = true
          ..openCode2HistoryPending = false;
        return l10n.setupSwitchFailed;
      }
    }
    return connect(profile);
  }
}
