import 'package:flutter/foundation.dart' show visibleForTesting;
import 'package:flutter/services.dart';

import '../domain/workspace_paths.dart';
import '../platform/platform_capabilities.dart';
import '../ui/kit/kit_redact.dart';
import 'bridge_models.dart';
import 'scripts/aiteam_script.dart';
import 'scripts/install_and_serve_script.dart';
import 'scripts/local_agents_script.dart';
import 'scripts/manager_script.dart';
import 'scripts/project_scripts.dart';
import 'scripts/restart_script.dart';
import 'scripts/script_support.dart';
import 'scripts/setup_base_script.dart';
import 'scripts/status_scripts.dart';
import 'scripts/tools_script.dart';
import 'team_scripts.dart';

export 'bridge_models.dart';

/// Drives Termux over the `oc/termux` method channel.
///
/// Termux is an Android app and the channel is implemented only by the
/// Android runner, so every entry point here is a no-op-with-an-answer on
/// desktop rather than a throw: `MissingPluginException` is not a
/// `PlatformException`, so a bridge that caught only the latter let a raw
/// framework exception escape into UI code that had no idea what it meant.
class TermuxBridge {
  static const _channel = MethodChannel('oc/termux');

  /// Every failure the bridge can report for "this platform has no Termux".
  static const unsupportedPlatformCode = 'unsupported_platform';

  /// Whether the Termux bridge can do anything at all here.
  static bool get supported => platformCapabilities.supportsTermux;

  static TermuxBridgeException get _unsupported => const TermuxBridgeException(
    'Termux runs on Android. This desktop build connects to an OpenCode '
    'server you start yourself.',
    code: unsupportedPlatformCode,
  );

  static const termuxHome = termuxHomeDirectory;
  static const managedServerPort = termuxManagedServerPort;
  static const managedServerUrl = 'http://127.0.0.1:$managedServerPort';

  /// The OpenCode server version a published build installs and updates to.
  ///
  /// Pinned, deliberately. `latest` meant an APK sitting on a phone for
  /// months would one day install a server release published long after this
  /// client was written and tested against it — a protocol change on the
  /// server side would then break setup on a device whose owner changed
  /// nothing. This is the version the app's contracts and fixtures are
  /// verified against; raising it is a code change with a test run behind it,
  /// not something that happens on its own.
  ///
  /// Keep this in step with the shell fallback in [_managerScript]
  /// (`requested_version="${2:-…}"`); a test asserts the two agree.
  static const defaultOpenCodeVersion = '1.18.32';

  /// The npm dist-tag, available only when a caller passes it to
  /// [installAndServeScript] on purpose. Nothing in the app does today: it
  /// exists so a deliberate "install whatever is newest" flow has a name
  /// rather than a magic string.
  static const latestOpenCodeVersion = 'latest';

  static bool managesServerUrl(String? value) {
    final uri = value == null ? null : Uri.tryParse(value);
    if (uri == null || uri.scheme != 'http') return false;
    final port = uri.hasPort ? uri.port : 80;
    return uri.host == '127.0.0.1' && port == managedServerPort;
  }

  static Future<TermuxCapabilities> capabilities() async {
    if (!supported) return const TermuxCapabilities.unavailable();
    try {
      final raw = await _channel.invokeMapMethod<String, dynamic>(
        'getCapabilities',
      );
      return TermuxCapabilities.fromMap(raw ?? const {});
    } on MissingPluginException {
      // A runner with no `oc/termux` handler: honestly nothing installed.
      return const TermuxCapabilities.unavailable();
    }
  }

  static Future<bool> requestPermission() =>
      _invokeFlag('requestRunCommandPermission');

  /// Android's question for Termux's "Run commands" permission, answered
  /// in words: `granted`, `denied`, `permanentlyDenied` or `missing`.
  /// Throws [MissingPluginException] on a runner without the method.
  static Future<String?> requestRunCommandAccess() async {
    final raw = await _channel.invokeMethod<Object?>('requestRunCommandAccess');
    return raw is String ? raw : null;
  }

  static Future<bool> openTermux() => _invokeFlag('openTermux');

  static Future<bool> openAppSettings() => _invokeFlag('openAppSettings');

  /// Durable v2 setup. Scripts travel in memory only; metadata is allowlisted
  /// and redacted before the native host stores it. A lost callback never
  /// implies that the independently owned Termux process has stopped.
  static Future<void> startSetup({
    required String jobId,
    required List<Map<String, Object?>> components,
    Map<String, Map<String, String>> params = const {},
    Map<String, String> texts = const {},
  }) async {
    if (!RegExp(r'^[A-Za-z0-9_-]{1,96}$').hasMatch(jobId)) {
      throw const TermuxBridgeException(
        'Invalid setup job id.',
        code: 'invalid_setup',
      );
    }
    final safeParams = <String, Map<String, String>>{};
    const allowed = {
      'opencode': {'runtime', 'version'},
      '_job': {'first', 'adding'},
    };
    for (final entry in params.entries) {
      final keys = allowed[entry.key];
      if (keys == null || entry.value.keys.any((key) => !keys.contains(key))) {
        throw const TermuxBridgeException(
          'Unsupported setup parameter.',
          code: 'invalid_setup',
        );
      }
      for (final value in entry.value.entries) {
        final valid = switch (value.key) {
          'runtime' => {'opencode1', 'opencode2'}.contains(value.value),
          'version' => RegExp(
            r'^\d+\.\d+\.\d+(?:[-+][A-Za-z0-9.-]+)?$',
          ).hasMatch(value.value),
          'first' => {'0', '1'}.contains(value.value),
          'adding' =>
            value.value.isEmpty ||
                RegExp(
                  r'^[a-z][a-z0-9_-]*(?:,[a-z][a-z0-9_-]*)*$',
                ).hasMatch(value.value),
          _ => false,
        };
        if (!valid || KitRedact.containsSecret(value.value)) {
          throw const TermuxBridgeException(
            'Invalid setup parameter.',
            code: 'invalid_setup',
          );
        }
      }
      safeParams[entry.key] = {
        for (final value in entry.value.entries)
          if (keys.contains(value.key)) value.key: KitRedact.text(value.value),
      };
    }
    final safeComponents = <Map<String, Object?>>[];
    for (final component in components) {
      final id = component['id'];
      if (id is! String || !RegExp(r'^[a-z][a-z0-9_-]{0,63}$').hasMatch(id)) {
        throw const TermuxBridgeException(
          'Invalid setup component.',
          code: 'invalid_setup',
        );
      }
      final native = component['native'] == true;
      if (native && id != 'linux') {
        throw const TermuxBridgeException(
          'Unsupported native component.',
          code: 'invalid_setup',
        );
      }
      final data = component['data'];
      safeComponents.add({
        'id': id,
        'script': native ? setupBaseScript : component['script'],
        'native': native,
        'step': component['step'] == true,
        'skipped': component['skipped'] == true,
        'weight': component['weight'],
        'version': component['version'] is String
            ? KitRedact.text(component['version'] as String)
            : null,
        'data': data is Map
            ? {
                for (final key in ['runtime', 'openCodeChanged'])
                  if (data[key] is String)
                    key: KitRedact.text(data[key] as String),
              }
            : <String, String>{},
      });
    }
    await _setupInvoke<void>('startSetup', {
      'jobId': jobId,
      'components': safeComponents,
      'params': safeParams,
    });
  }

  static Future<String?> setupStatus() => _setupInvoke<String>('setupStatus');

  static Future<void> cancelSetup() => _setupInvoke<void>('cancelSetup');

  static Future<void> completeSetupStep({
    required String jobId,
    required String id,
    required bool ok,
    String? error,
    String? version,
  }) => _setupInvoke<void>('completeSetupStep', {
    'jobId': jobId, 'id': id, 'ok': ok,
    // Native persists fixed failure copy; arbitrary errors never cross here.
    'version': version == null ? null : KitRedact.text(version),
  });

  static Future<bool> setupHostInstalled() async =>
      await _setupInvoke<bool>('setupHostInstalled') ?? false;

  /// Runs a check in the same app-managed Ubuntu container as the v1 manager.
  static Future<TermuxCommandResult> setupRun(
    String script, {
    Duration timeout = const Duration(minutes: 2),
  }) async {
    final raw = await _setupInvoke<Map<Object?, Object?>>('setupRun', {
      'script': script,
      'timeoutMs': timeout.inMilliseconds,
    });
    return TermuxCommandResult.fromMap(
      raw?.cast<String, dynamic>() ?? const {},
    );
  }

  static Future<T?> _setupInvoke<T>(String method, [Object? args]) async {
    if (!supported) throw _unsupported;
    try {
      return await _channel.invokeMethod<T>(method, args);
    } on MissingPluginException {
      throw _unsupported;
    } on PlatformException catch (error) {
      throw TermuxBridgeException(
        KitRedact.text(error.message ?? 'Termux setup failed.'),
        code: error.code,
      );
    }
  }

  /// Pinned Canonical base; leaves any unknown existing container untouched.
  /// No credentials or generated manager scripts are written by this step.
  @visibleForTesting
  static const setupBaseScript = termuxSetupBaseScript;

  /// Every one of these answers "did the platform do the thing?", so a
  /// missing channel is simply `false` — never an exception a caller that
  /// wanted a bool has to know about.
  static Future<bool> _invokeFlag(String method) async {
    if (!supported) return false;
    try {
      return await _channel.invokeMethod<bool>(method) ?? false;
    } on MissingPluginException {
      return false;
    }
  }

  static Future<TermuxCommandResult> run(
    String script, {
    bool background = true,
    String? workdir,
    Duration timeout = const Duration(seconds: 30),
  }) async {
    if (!supported) throw _unsupported;
    try {
      var command = await _invoke(script, background, workdir, timeout);
      if (command.killedByServiceShutdown) {
        // The Termux service stops itself once its last task ends and kills
        // whatever arrives while it is going down (seen on the emulator
        // between a `status` poll and the next verb). It comes straight
        // back, so one retry after a short pause is enough.
        await Future<void>.delayed(serviceShutdownRetryDelay);
        command = await _invoke(script, background, workdir, timeout);
      }
      if (!command.successful) {
        throw TermuxBridgeException(
          command.failureMessage,
          code: 'command_failed',
        );
      }
      return command;
    } on MissingPluginException {
      // The Android runner registers `oc/termux`; nothing else does. Reaching
      // here means a platform slipped past [supported] — report it the way a
      // caller already handles rather than letting a framework exception out.
      throw _unsupported;
    } on PlatformException catch (error) {
      throw TermuxBridgeException(
        error.message ?? 'Termux command failed.',
        code: error.code,
      );
    }
  }

  /// How long [run] waits before its single retry after Termux killed the
  /// execution because its service was shutting down.
  static Duration serviceShutdownRetryDelay = const Duration(
    milliseconds: 1500,
  );

  static Future<TermuxCommandResult> _invoke(
    String script,
    bool background,
    String? workdir,
    Duration timeout,
  ) async {
    final raw = await _channel.invokeMapMethod<String, dynamic>('runInTermux', {
      'script': script,
      'background': background,
      'workdir': workdir ?? termuxHome,
      'timeoutMs': timeout.inMilliseconds,
    });
    return TermuxCommandResult.fromMap(raw ?? const {});
  }

  /// Test seam for [managedServerPassword].
  static Future<String?> Function()? managedServerPasswordOverride;

  /// The password this app generated for the OpenCode server it manages on
  /// this phone, read back from the file setup wrote (`~/.oc/server.password`,
  /// mode 600). Null when there is none or Termux cannot be asked.
  ///
  /// The app's own secure copy can be lost while the server keeps running (a
  /// reinstall, cleared app data, an unreadable keystore after a restore).
  /// The person was never shown this password and may have no terminal to
  /// read it from, so the app restores its own secret rather than asking for
  /// it. The value is never logged and never leaves this device.
  static Future<String?> managedServerPassword() async {
    final override = managedServerPasswordOverride;
    if (override != null) return override();
    if (!supported) return null;
    try {
      final result = await run(
        'f="\$HOME/.oc/server.password"; [ -s "\$f" ] && cat "\$f"',
        timeout: const Duration(seconds: 10),
      );
      final value = result.stdout.trim();
      if (value.isEmpty ||
          value.length > 512 ||
          RegExp(r'[\x00-\x20\x7f]').hasMatch(value)) {
        return null;
      }
      return value;
    } catch (_) {
      return null;
    }
  }

  static Future<void> verifyBridge() async {
    final result = await run("printf 'opencode-bridge-ok'");
    if (result.stdout.trim() != 'opencode-bridge-ok') {
      throw const TermuxBridgeException(
        'Termux returned an unexpected bridge response.',
        code: 'invalid_probe',
      );
    }
  }

  /// Keeps the managed on-device OpenCode server able to reach providers
  /// while Android is idle. The manager releases this lock when the server is
  /// stopped or exits; repeated acquisitions are safe in Termux.
  static Future<void> ensureWakeLock() async {
    await run(ensureWakeLockScript, timeout: const Duration(seconds: 10));
  }

  static const ensureWakeLockScript =
      "termux-wake-lock >/dev/null 2>&1 || true; "
      "echo opencode-server-wake-lock-held";

  static Future<TermuxSetupStatus> status() async {
    final result = await run(statusScript());
    return TermuxSetupStatus.parse(result.stdout);
  }

  static Future<TermuxStorageSnapshot> storage() async {
    final result = await run(
      storageScript(),
      timeout: const Duration(seconds: 8),
    );
    return TermuxStorageSnapshot.parse(result.stdout);
  }

  static String storageScript() => termuxStorageScript();

  // ---- Phone tools (TEAM-304/305): storage and process views -------------
  //
  // `~/.oc/tools.sh` lives beside manager.sh and is rewritten on every call
  // (tmp + mv, so a scan already running keeps its own copy). Every verb
  // answers with JSON or `key=value` lines that lib/termux/storage.dart and
  // lib/termux/processes.dart parse; nothing here starts or stops the
  // managed server.
  static const toolsPath = termuxToolsPath;

  static const toolVerbs = termuxToolVerbs;

  static String toolsScriptForTesting() => termuxToolsScript;

  static String toolsCommandScript(String verb, {String argument = ''}) =>
      termuxToolsCommandScript(verb, argument: argument);

  /// Runs one tools verb and returns its stdout.
  static Future<String> runTool(
    String verb, {
    String argument = '',
    Duration timeout = const Duration(seconds: 30),
  }) async {
    final result = await run(
      toolsCommandScript(verb, argument: argument),
      timeout: timeout,
    );
    return result.stdout;
  }

  static String recoveryControlScript(String token, {required bool enable}) =>
      termuxRecoveryControlScript(token, enable: enable);

  static Future<TermuxSetupSnapshot> setupSnapshot() async {
    final result = await run(setupSnapshotScript());
    return TermuxSetupSnapshot.parse(result.stdout);
  }

  /// Inspects only the app-owned Ubuntu installation; never starts a server.
  static Future<TermuxInstallation> inspectInstallation() async {
    final result = await run(
      installationScript(),
      timeout: const Duration(seconds: 25),
    );
    return TermuxInstallation.parse(result.stdout);
  }

  static String installationScript() => termuxInstallationScript();

  static Future<String> diagnostics() async {
    final result = await run(diagnosticsScript());
    return result.stdout.trim();
  }

  static String createProjectFolderScript(String name) =>
      termuxCreateProjectFolderScript(name);

  /// Creates a project folder under `/root/projects` in the managed
  /// container and returns its absolute path. Only the app-managed Termux
  /// server can do this; other servers have no folder-creation API.
  static Future<String> createProjectFolder(String name) async {
    final problem = projectFolderNameProblem(name);
    if (problem != null) {
      throw TermuxBridgeException(problem, code: 'invalid_folder_name');
    }
    final folder = name.trim();
    final result = await run(
      createProjectFolderScript(folder),
      timeout: const Duration(seconds: 45),
    );
    final path = result.stdout.trim().split('\n').last.trim();
    if (path != '$managedProjectsDirectory/$folder') {
      throw TermuxBridgeException(
        'The folder could not be created in the managed server.',
        code: 'folder_not_created',
      );
    }
    return path;
  }

  static bool isLaunchAcknowledged(String output) => RegExp(
    r'(^|\n)manager-(started|already-running):[0-9]+($|\n)',
  ).hasMatch(output.trim());

  static const unlockCommand =
      "mkdir -p ~/.termux && touch ~/.termux/termux.properties && "
      "grep -q '^allow-external-apps=true' ~/.termux/termux.properties || "
      "echo 'allow-external-apps=true' >> ~/.termux/termux.properties; "
      "termux-reload-settings; echo bridge-unlocked";

  static String installAndServeScript({
    int port = 4096,
    required String password,
    String? version,
    TermuxRuntime runtime = TermuxRuntime.openCode1,
  }) => termuxInstallAndServeScript(
    port: port,
    password: password,
    version: version,
    runtime: runtime,
  );

  static String statusScript() => termuxStatusScript();

  static String setupSnapshotScript() => termuxSetupSnapshotScript();

  static String diagnosticsScript() => termuxDiagnosticsScript();

  static String restartScript({
    int port = managedServerPort,
    required String operationID,
    String? recoveryToken,
    String? expectedOperationID,
    TermuxRuntime? switchTarget,
    String? switchPassword,
  }) => termuxRestartScript(
    port: port,
    operationID: operationID,
    recoveryToken: recoveryToken,
    expectedOperationID: expectedOperationID,
    switchTarget: switchTarget,
    switchPassword: switchPassword,
  );

  static String stopScript({int port = 4096}) => termuxStopScript(port: port);

  static String managerScriptForTesting() => termuxManagerScript;

  // ---------------------------------------------------------------------------
  // AI Team on this phone (TEAM-301): ~/.oc/aiteam.sh next to manager.sh.
  // ---------------------------------------------------------------------------

  /// Where the bridge writes `aiteam.sh` ([TermuxTeamScripts.aiteamScript]).
  static const aiteamPath = '$termuxHome/.oc/aiteam.sh';

  /// Where every dispatch writes the pinned downloads `aiteam.sh install`
  /// reads ([TermuxTeamScripts.pinsFile]).
  static const aiteamPinsPath = '$termuxHome/.oc/aiteam-pins';

  /// The loopback supervisor of the Termux team.
  static const aiteamSupervisorUrl = TermuxTeamScripts.supervisorUrl;

  static const aiteamDetachedVerbs = termuxAiteamDetachedVerbs;

  static String aiteamScriptForTesting() => TermuxTeamScripts.aiteamScript;

  static String aiteamStatusScript() => aiteamVerbScript('status');

  static String aiteamLogPathScript() => aiteamVerbScript('log');

  static String aiteamLogTailScript({int lines = 200}) =>
      termuxAiteamLogTailScript(lines: lines);

  static String aiteamProjectsScript() => termuxAiteamProjectsScript();

  static String aiteamVerbScript(
    String verb, {
    List<String> args = const [],
    @visibleForTesting String? pinsFile,
  }) => termuxAiteamVerbScript(verb, args: args, pinsFile: pinsFile);

  /// Runs one `aiteam.sh` verb through the bridge and returns its stdout.
  static Future<String> aiteam(
    String verb, {
    List<String> args = const [],
    Duration timeout = const Duration(seconds: 30),
  }) async {
    final result = await run(
      aiteamVerbScript(verb, args: args),
      timeout: timeout,
    );
    return result.stdout;
  }

  // ---------------------------------------------------------------------------
  // Claude Code on this phone: ~/.oc/claude.sh next to manager.sh. It installs
  // a pinned Node.js, the Paseo daemon and Claude Code into the app-managed
  // Ubuntu and runs the daemon on loopback. docs/claude-on-this-phone.md.
  // ---------------------------------------------------------------------------

  /// Where the bridge writes [_localAgentsScript].
  static const localAgentsPath = '$termuxHome/.oc/claude.sh';

  /// The daemon's only listen address. Loopback, always: the app never offers
  /// the Paseo relay and the script has no way to widen this.
  static const localAgentsPort = 6767;
  static const localAgentsUrl = 'ws://127.0.0.1:$localAgentsPort';

  static const localAgentsPins = termuxLocalAgentsPins;

  static const localAgentsDetachedVerbs = termuxLocalAgentsDetachedVerbs;

  static const localAgentsVerbs = termuxLocalAgentsVerbs;

  static String localAgentsScriptForTesting() => termuxLocalAgentsScript;

  static String localAgentsPinsFile([
    Map<String, String> pins = localAgentsPins,
  ]) => termuxLocalAgentsPinsFile(pins);

  static String localAgentsLogTailScript({int lines = 200}) =>
      termuxLocalAgentsLogTailScript(lines: lines);

  /// What a foreground Termux terminal runs to sign in to Claude. Short on
  /// purpose: it travels as a command argument, and the script it names was
  /// written by an earlier bridge call.
  static const localAgentsSignInCommand = 'bash "\$HOME/.oc/claude.sh" signin';

  static String localAgentsVerbScript(
    String verb, {
    List<String> args = const [],
  }) => termuxLocalAgentsVerbScript(verb, args: args);

  /// Opens a Termux terminal the person can see and type into, running
  /// [command]. The bridge's normal path feeds a script on stdin, which a
  /// terminal session does not read, and waits for a result a sign-in may
  /// take minutes to produce; this one hands the command over and returns.
  static Future<bool> openTerminalSession(String command) async {
    if (!supported) return false;
    try {
      return await _channel.invokeMethod<bool>('openTermuxSession', {
            'command': command,
          }) ??
          false;
    } on MissingPluginException {
      return false;
    } on PlatformException catch (error) {
      throw TermuxBridgeException(
        error.message ?? 'Termux could not open a terminal.',
        code: error.code,
      );
    }
  }
}
