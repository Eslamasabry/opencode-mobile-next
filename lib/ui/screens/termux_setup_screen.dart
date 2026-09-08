import 'dart:async';
import 'dart:convert';
import 'dart:math';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../l10n/app_localizations.dart';
import '../../platform/platform_capabilities.dart';
import '../../state/connection.dart';
import '../../state/profiles.dart';
import '../../termux/bridge.dart';
import '../../termux/managed_server_recovery.dart';
import '../app_theme.dart';
import '../widgets/confirm_sheet.dart';
import '../widgets/setup_terminal.dart';

class TermuxSetupScreen extends ConsumerStatefulWidget {
  const TermuxSetupScreen({super.key, this.now});

  /// Tests can advance wall time independently of foreground timer callbacks.
  @visibleForTesting
  final DateTime Function()? now;

  @override
  ConsumerState<TermuxSetupScreen> createState() => _TermuxSetupScreenState();
}

enum _Phase {
  checking,
  needTermux,
  needUnlock,
  ready,
  installing,
  connected,
  failed,
}

class _TermuxSetupScreenState extends ConsumerState<TermuxSetupScreen>
    with WidgetsBindingObserver {
  static const port = TermuxBridge.managedServerPort;
  static const localUrl = TermuxBridge.managedServerUrl;

  _Phase _phase = _Phase.checking;
  TermuxSetupStatus? _status;
  TermuxInstallation? _installation;
  TermuxRuntime _selectedRuntime = TermuxRuntime.openCode1;
  TermuxRuntime? _committedRuntime;

  TermuxRuntime? get _knownRuntime {
    final status = _status;
    if (status != null &&
        (status.runtimeSelected || status.version.isNotEmpty)) {
      return status.runtime;
    }
    final installed = _installation;
    if (installed != null &&
        (installed.runtimeSelected || installed.openCodeVersion != null)) {
      return installed.runtime;
    }
    return _committedRuntime;
  }

  TermuxRuntime get _runtime => _knownRuntime ?? _selectedRuntime;

  String _runtimeName(TermuxRuntime runtime) {
    final l10n = AppLocalizations.of(context);
    return runtime == TermuxRuntime.openCode2
        ? l10n.setupRuntimeTwo
        : l10n.setupRuntimeOne;
  }

  bool _checkingInstallation = false;
  String? _installationError;
  bool _startingExisting = false;
  Timer? _poll;
  Timer? _elapsedTimer;
  bool _launching = false;
  int _statusEpoch = 0;
  String _launchMessage = 'Checking Termux connection';
  bool _busy = false;
  bool _refreshing = false;
  bool _polling = false;
  bool _monitoringFailed = false;
  bool _restarting = false;
  String? _restartOperationID;
  int _snapshotFailures = 0;
  String? _error;
  String? _lastLaunchOutput;
  String _setupOutput = '';

  /// Tracks the Termux round trip separately from Android permission dialogs.
  /// On return we verify automatically and keep a manual verification retry.
  bool _openedTermux = false;
  bool _returnedFromTermux = false;
  bool _copyingToTermux = false;
  bool _verifyOnReturn = false;
  bool _termuxWentToBackground = false;
  final ScrollController _outputScrollController = ScrollController();

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    WidgetsBinding.instance.addPostFrameCallback((_) => unawaited(_refresh()));
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.paused && _openedTermux) {
      _termuxWentToBackground = true;
    }
    if (state == AppLifecycleState.resumed) {
      // A permission dialog also resumes the app. Only verify after the
      // actual Termux background/return, even if the permission result arrived
      // before its resume callback. Inactive alone can be a permission dialog.
      if (_openedTermux) {
        if (!_termuxWentToBackground) return;
        if (_busy) {
          _verifyOnReturn = true;
          return;
        }
        _verifyAfterTermuxReturn();
        return;
      }
      if (_busy) return;
      unawaited(_refresh());
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _poll?.cancel();
    _elapsedTimer?.cancel();
    _outputScrollController.dispose();
    super.dispose();
  }

  Future<void> _refresh() async {
    // Off Android the build below is the unsupported card; there is no state
    // worth polling for and no channel to poll.
    if (!platformCapabilities.supportsTermux) return;
    if (_refreshing || _launching || _busy) return;
    final epoch = _statusEpoch;
    _refreshing = true;
    try {
      final capabilities = await TermuxBridge.capabilities();
      if (!mounted || epoch != _statusEpoch) return;
      if (!capabilities.installed) {
        _stopPolling();
        setState(() {
          _phase = _Phase.needTermux;
          _error = null;
        });
        return;
      }
      if (!capabilities.serviceAvailable || !capabilities.protocolSupported) {
        _stopPolling();
        setState(() {
          _phase = _Phase.failed;
          _error =
              'Termux ${capabilities.version ?? ''} does not support the '
              'required command-result protocol. Install the current F-Droid '
              'or GitHub build of Termux.';
        });
        return;
      }
      if (!capabilities.permissionGranted) {
        _stopPolling();
        setState(() {
          _phase = _Phase.needUnlock;
          // A denied permission dialog can resume after its result arrives.
          // Keep its actionable error visible across that refresh.
        });
        return;
      }

      try {
        await TermuxBridge.verifyBridge();
      } on TermuxBridgeException catch (error) {
        if (!mounted || epoch != _statusEpoch) return;
        _stopPolling();
        setState(() {
          _phase = _Phase.needUnlock;
          _error = error.code == 'command_timeout'
              ? 'Termux did not answer. Open Termux once, run the unlock line, '
                    'then verify again.'
              : error.message;
        });
        return;
      }
      if (!mounted || epoch != _statusEpoch) return;
      await _refreshStatus();
      if (mounted && (_phase == _Phase.ready || _phase == _Phase.failed)) {
        await _checkInstallation();
      }
    } on PlatformException catch (error) {
      if (!mounted || epoch != _statusEpoch) return;
      setState(() {
        _phase = _Phase.failed;
        _error = error.message ?? 'Android could not inspect Termux.';
      });
    } finally {
      _refreshing = false;
    }
  }

  Future<void> _getTermux() async {
    final uri = Uri.parse('https://f-droid.org/en/packages/com.termux/');
    await launchUrl(uri, mode: LaunchMode.externalApplication);
  }

  Future<void> _openTermuxAndCopy() async {
    if (_busy) return;
    final l10n = AppLocalizations.of(context);
    setState(() {
      _busy = true;
      _copyingToTermux = true;
      _error = null;
      _openedTermux = false;
      _returnedFromTermux = false;
      _verifyOnReturn = false;
      _termuxWentToBackground = false;
    });
    try {
      await _requestTermuxPermission();
      if (!mounted) return;
      await Clipboard.setData(
        const ClipboardData(text: TermuxBridge.unlockCommand),
      );
      if (!mounted) return;
      _openedTermux = true;
      final opened = await TermuxBridge.openTermux();
      if (!mounted) return;
      if (!opened) {
        throw TermuxBridgeException(l10n.termuxGuideOpenFailed);
      }
    } on TermuxBridgeException catch (error) {
      if (!mounted) return;
      setState(() {
        _openedTermux = false;
        _verifyOnReturn = false;
        _error = error.message;
      });
    } on PlatformException catch (error) {
      if (!mounted) return;
      setState(() {
        _openedTermux = false;
        _verifyOnReturn = false;
        _error = error.message ?? l10n.termuxGuideCopyOpenFailed;
      });
    } finally {
      if (mounted) {
        setState(() {
          _busy = false;
          _copyingToTermux = false;
        });
        if (_verifyOnReturn) _verifyAfterTermuxReturn();
      }
    }
  }

  Future<void> _requestTermuxPermission() async {
    final deniedMessage = AppLocalizations.of(context).termuxPermissionDenied;
    if (!await TermuxBridge.requestPermission()) {
      throw TermuxBridgeException(deniedMessage, code: 'permission_denied');
    }
  }

  void _verifyAfterTermuxReturn() {
    setState(() {
      _openedTermux = false;
      _verifyOnReturn = false;
      _termuxWentToBackground = false;
      _returnedFromTermux = true;
    });
    unawaited(_verifyUnlock(requestPermission: false));
  }

  Future<void> _verifyUnlock({bool requestPermission = true}) async {
    if (_busy) return;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      if (requestPermission) await _requestTermuxPermission();
      if (!mounted) return;
      await TermuxBridge.verifyBridge();
      await _refreshStatus();
      if (mounted && (_phase == _Phase.ready || _phase == _Phase.failed)) {
        await _checkInstallation();
      }
    } on TermuxBridgeException catch (error) {
      if (!mounted) return;
      setState(() {
        _phase = _Phase.needUnlock;
        _error = error.message;
      });
    } on PlatformException catch (error) {
      if (!mounted) return;
      setState(() {
        _phase = _Phase.needUnlock;
        _error = error.message ?? 'Termux bridge verification failed.';
      });
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<ServerProfile> _ensureLocalProfile() async {
    final store = ref.read(bootstrapProvider).store;
    ServerProfile? profile;
    for (final candidate in store.profiles) {
      if (candidate.backend == ServerBackend.openCode &&
          candidate.baseUrl == localUrl &&
          candidate.flavor ==
              (_runtime == TermuxRuntime.openCode2
                  ? ServerFlavor.v2
                  : ServerFlavor.v1)) {
        profile = candidate;
        break;
      }
    }
    profile ??= ServerProfile(
      id: DateTime.now().microsecondsSinceEpoch.toString(),
      name: 'This device (Termux)',
      baseUrl: localUrl,
      flavor: _runtime == TermuxRuntime.openCode2
          ? ServerFlavor.v2
          : ServerFlavor.v1,
    );
    profile.username = 'opencode';
    if (profile.password.isEmpty) {
      final random = Random.secure();
      final bytes = List<int>.generate(32, (_) => random.nextInt(256));
      profile.password = base64UrlEncode(bytes).replaceAll('=', '');
    }
    await store.upsert(profile);
    return profile;
  }

  ServerProfile? _localProfile() {
    for (final profile in ref.read(bootstrapProvider).store.profiles) {
      if (profile.backend == ServerBackend.openCode &&
          profile.baseUrl == localUrl &&
          profile.password.isNotEmpty &&
          profile.flavor ==
              (_runtime == TermuxRuntime.openCode2
                  ? ServerFlavor.v2
                  : ServerFlavor.v1)) {
        return profile;
      }
    }
    return null;
  }

  Future<void> _installAndStart() async {
    if (_busy || _phase == _Phase.installing) return;
    _selectedRuntime = _runtime;
    var launchRequested = false;
    _stopPolling();
    _statusEpoch++;
    setState(() {
      _busy = true;
      _launching = true;
      _phase = _Phase.installing;
      _status = null;
      _launchMessage = 'Checking Termux connection';
      _error = null;
      _monitoringFailed = false;
      _snapshotFailures = 0;
      _setupOutput = '';
      _lastLaunchOutput = null;
      _restarting = false;
      _startingExisting = false;
      _restartOperationID = null;
    });
    _startElapsedTimer();
    try {
      await TermuxBridge.verifyBridge();
      if (!mounted) return;
      setState(() => _launchMessage = 'Saving local server settings');
      final profile = await _ensureLocalProfile();
      if (!mounted) return;
      setState(() => _launchMessage = 'Starting setup in Termux');
      final command = TermuxBridge.installAndServeScript(
        port: port,
        password: profile.password,
        runtime: _selectedRuntime,
      );
      launchRequested = true;
      final launch = await TermuxBridge.run(command);
      if (!mounted) return;
      _lastLaunchOutput = [
        launch.stdout.trim(),
        launch.stderr.trim(),
      ].where((part) => part.isNotEmpty).join('\n');
      final launched = TermuxBridge.isLaunchAcknowledged(launch.stdout);
      if (!launched) {
        throw TermuxBridgeException(
          'Termux returned success without starting the setup manager.\n'
          '${_lastLaunchOutput?.isEmpty ?? true ? 'No launcher output was returned.' : _lastLaunchOutput}',
          code: 'invalid_launch_result',
        );
      }
      setState(() => _launchMessage = 'Reading setup progress');
      var initialStatus = await TermuxBridge.status();
      if (!mounted) return;
      for (
        var attempt = 0;
        attempt < 10 && initialStatus.phase == 'idle';
        attempt++
      ) {
        await Future<void>.delayed(const Duration(milliseconds: 250));
        if (!mounted) return;
        initialStatus = await TermuxBridge.status();
        if (!mounted) return;
      }
      if (initialStatus.phase == 'idle' ||
          initialStatus.message.contains('manager is missing')) {
        throw TermuxBridgeException(
          'The setup manager disappeared immediately after launch.\n'
          'Launcher: $_lastLaunchOutput',
          code: 'manager_missing',
        );
      }
      if (!mounted) return;
      _launching = false;
      _status = initialStatus;
      setState(() => _phase = _Phase.installing);
      _startPolling();
      await _refreshStatus();
    } on TermuxBridgeException catch (error) {
      if (!mounted) return;
      if (launchRequested &&
          error.code == 'command_timeout' &&
          await _recoverPersistedSetupAfterTimeout()) {
        return;
      }
      setState(() {
        _phase = error.code == 'permission_denied'
            ? _Phase.needUnlock
            : _Phase.failed;
        _error = error.message;
      });
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _phase = _Phase.failed;
        _error = 'Could not save or start the local setup: $error';
      });
    } finally {
      if (mounted) {
        _launching = false;
        if (_phase != _Phase.installing) _stopPolling();
        setState(() => _busy = false);
        if (_phase == _Phase.failed) unawaited(_checkInstallation());
      }
    }
  }

  Future<void> _confirmUpdate() async {
    final connection = ref.read(connProvider);
    if (connection.busySessions.isNotEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Stop active generation before updating OpenCode.'),
        ),
      );
      return;
    }
    final currentVersion = _status?.version.trim();
    final l10n = AppLocalizations.of(context);
    final confirmed = await showConfirmSheet(
      context,
      title: 'Update managed OpenCode?',
      message: [
        if (currentVersion?.isNotEmpty == true)
          'Installed version: $currentVersion.',
        l10n.setupRuntimeUpdateDetail(
          _runtimeName(_runtime),
          _runtime.pinnedVersion,
        ),
        'The server will be briefly unavailable. Active generation should be stopped first.',
      ].join('\n\n'),
      confirmLabel: 'Update',
      icon: AppIconography.systemDownload,
    );
    if (confirmed && mounted) await _installAndStart();
  }

  Future<void> _confirmRestart() async {
    final profile = _localProfile();
    if (profile == null) {
      setState(() {
        _phase = _Phase.ready;
        _error =
            'The saved credential for this managed server is unavailable. '
            'Run setup again to replace it safely.';
      });
      return;
    }
    final l10n = AppLocalizations.of(context);
    final busyCount = ref.read(connProvider).busySessions.length;
    final confirmed = await showConfirmSheet(
      context,
      title: l10n.termuxRestartTitle,
      message: [
        l10n.termuxRestartMessage,
        if (busyCount > 0) l10n.termuxRestartBusyMessage(busyCount),
      ].join('\n\n'),
      confirmLabel: l10n.termuxRestartConfirm,
      icon: AppIconography.restart,
    );
    if (confirmed && mounted) await _restartServer(profile);
  }

  Future<void> _restartServer(
    ServerProfile profile, {
    bool startExisting = false,
  }) async {
    if (_busy || (!startExisting && _phase != _Phase.connected)) return;
    final runtime = _runtime;
    _startingExisting = startExisting;
    _statusEpoch++;
    final operationID = DateTime.now().microsecondsSinceEpoch.toRadixString(36);
    _stopPolling();
    setState(() {
      _busy = true;
      _restarting = true;
      _restartOperationID = operationID;
      _phase = _Phase.installing;
      _error = null;
      _monitoringFailed = false;
      _snapshotFailures = 0;
      _setupOutput = '';
      _status = TermuxSetupStatus(
        phase: 'restarting',
        message: 'Restarting the local server',
        port: port,
        runner: 'proot',
        version: '',
        pid: null,
        operationID: operationID,
        runtime: runtime,
        runtimeSelected: true,
      );
    });
    _startElapsedTimer();
    try {
      await TermuxBridge.run(
        TermuxBridge.restartScript(port: port, operationID: operationID),
        timeout: const Duration(seconds: 45),
      );
      if (!mounted) return;
      final snapshot = await TermuxBridge.setupSnapshot();
      if (!mounted) return;
      _validateRestartSnapshot(snapshot.status);
      setState(() {
        _status = snapshot.status;
        _setupOutput = snapshot.output;
      });
      if (snapshot.status.isReady) {
        await _finishRestart(profile);
      } else if (snapshot.status.isRunning) {
        _startPolling();
      } else {
        setState(() {
          _restarting = false;
          _phase = _Phase.failed;
          _error = _persistedFailureMessage(snapshot.status, snapshot.output);
        });
      }
    } on TermuxBridgeException catch (error) {
      if (!mounted) return;
      if (error.code == 'command_timeout' &&
          await _recoverPersistedSetupAfterTimeout()) {
        return;
      }
      if (error.code != 'command_timeout' &&
          await _recoverReadyServerAfterRestartFailure()) {
        return;
      }
      setState(() {
        _restarting = false;
        _phase = _Phase.failed;
        _error = 'Could not restart the local server: ${error.message}';
      });
    } finally {
      if (mounted) {
        if (_phase != _Phase.installing) _stopPolling();
        setState(() => _busy = false);
      }
    }
  }

  Future<bool> _recoverReadyServerAfterRestartFailure() async {
    try {
      final snapshot = await TermuxBridge.setupSnapshot();
      if (!mounted || !snapshot.status.isReady) return false;
      _status = snapshot.status;
      _setupOutput = snapshot.output;
      if (_status!.operationID == _restartOperationID) {
        final profile = _localProfile();
        if (profile == null) return false;
        await _finishRestart(profile);
        return true;
      }
      if (_startingExisting) return false;
      setState(() {
        _restarting = false;
        _phase = _Phase.connected;
      });
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(AppLocalizations.of(context).termuxRestartNotPerformed),
        ),
      );
      return true;
    } on TermuxBridgeException {
      return false;
    }
  }

  void _validateRestartSnapshot(TermuxSetupStatus status) {
    if (!_restarting) {
      if (status.phase == 'restarting' && status.operationID.isNotEmpty) {
        _restarting = true;
        _restartOperationID = status.operationID;
      }
      return;
    }
    if (status.operationID != _restartOperationID ||
        (status.isReady &&
            status.operationResult != 'completed' &&
            status.operationResult != 'not_performed')) {
      throw const TermuxBridgeException(
        'Could not confirm this restart. Refresh its progress before retrying.',
        code: 'restart_unconfirmed',
      );
    }
  }

  Future<bool> _recoverPersistedSetupAfterTimeout() async {
    try {
      final snapshot = await TermuxBridge.setupSnapshot();
      if (!mounted || snapshot.status.phase == 'idle') return false;
      _validateRestartSnapshot(snapshot.status);
      _status = snapshot.status;
      _setupOutput = snapshot.output;
      if (snapshot.status.isRunning) {
        setState(() {
          _phase = _Phase.installing;
          _error = null;
        });
        _startPolling();
      } else if (snapshot.status.isFailed) {
        setState(() {
          _phase = _Phase.failed;
          _error = _persistedFailureMessage(snapshot.status, snapshot.output);
        });
      } else if (snapshot.status.isReady) {
        final profile = _localProfile();
        if (profile == null) return false;
        if (_restarting) {
          await _finishRestart(profile);
        } else {
          await _finishConnect(profile);
        }
      } else {
        setState(() => _phase = _Phase.ready);
      }
      return true;
    } on TermuxBridgeException {
      return false;
    }
  }

  String _persistedFailureMessage(
    TermuxSetupStatus status,
    String output, {
    String? bridgeMessage,
  }) {
    final outputLines = output
        .split('\n')
        .map((line) => line.trim())
        .where((line) => line.isNotEmpty)
        .toList();
    String? rootCause;
    for (final line in outputLines.reversed) {
      if (line.contains('[oc] ERROR:') ||
          line.contains('CANNOT LINK EXECUTABLE') ||
          line.contains('cannot locate symbol') ||
          line.contains('SSL_') ||
          line.startsWith('E:')) {
        rootCause = line;
        break;
      }
    }
    rootCause ??= outputLines.isEmpty ? null : outputLines.last;
    return [
      status.message,
      if (rootCause != null && rootCause != status.message)
        'Last setup output: $rootCause',
      if (bridgeMessage != null) 'Bridge detail: $bridgeMessage',
    ].join('\n');
  }

  void _startElapsedTimer() {
    _elapsedTimer ??= Timer.periodic(const Duration(seconds: 1), (_) {
      // Repaint from wall time, rather than counting callbacks that stop in
      // the background or when this route is closed.
      if (mounted) setState(() {});
    });
  }

  int? get _elapsedSeconds {
    final startedAt = _status?.startedAtEpochSeconds;
    if (startedAt == null) return null;
    final now = widget.now?.call() ?? DateTime.now();
    return max(0, now.millisecondsSinceEpoch ~/ 1000 - startedAt);
  }

  void _startPolling() {
    _startElapsedTimer();
    _poll?.cancel();
    _poll = Timer.periodic(const Duration(seconds: 1), (_) {
      unawaited(_refreshStatus());
    });
  }

  void _stopPolling() {
    _poll?.cancel();
    _poll = null;
    _elapsedTimer?.cancel();
    _elapsedTimer = null;
  }

  Future<void> _refreshStatus() async {
    if (_polling || _launching) return;
    final epoch = _statusEpoch;
    _polling = true;
    try {
      final snapshot = await TermuxBridge.setupSnapshot();
      if (!mounted || epoch != _statusEpoch) return;
      _validateRestartSnapshot(snapshot.status);
      _snapshotFailures = 0;
      _monitoringFailed = false;
      final status = snapshot.status;
      final outputChanged = snapshot.output != _setupOutput;
      final followOutput =
          !_outputScrollController.hasClients ||
          _outputScrollController.position.maxScrollExtent -
                  _outputScrollController.position.pixels <
              48;
      _status = status;
      if (status.runtimeSelected || status.version.isNotEmpty) {
        _committedRuntime = status.runtime;
      }
      if (status.isReady && status.version.isNotEmpty) {
        _installation = TermuxInstallation(
          ubuntuInstalled: true,
          openCodeVersion: status.version,
          runtime: _runtime,
          runtimeSelected: true,
        );
      }
      _setupOutput = snapshot.output;
      if (outputChanged && followOutput) {
        WidgetsBinding.instance.addPostFrameCallback((_) {
          if (!mounted || !_outputScrollController.hasClients) return;
          _outputScrollController.jumpTo(
            _outputScrollController.position.maxScrollExtent,
          );
        });
      }
      if (status.isRunning) {
        setState(() {
          _phase = _Phase.installing;
        });
        if (_poll == null) _startPolling();
        return;
      }
      _stopPolling();
      if (status.isFailed) {
        setState(() {
          _monitoringFailed =
              status.message == 'Could not read setup manager status';
          _phase = _Phase.failed;
          _error = _persistedFailureMessage(status, snapshot.output);
        });
        return;
      }
      if (status.isReady) {
        final profile = _localProfile();
        if (profile == null) {
          setState(() {
            _phase = _Phase.ready;
            _error =
                'A local server exists, but its saved credential is unavailable. '
                'Run setup again to replace it safely.';
          });
          return;
        }
        if (_restarting) {
          await _finishRestart(profile);
        } else {
          await _finishConnect(profile);
        }
        return;
      }
      setState(() {
        _phase = _Phase.ready;
        _error = null;
      });
    } on TermuxBridgeException catch (error) {
      if (!mounted || epoch != _statusEpoch) return;
      _snapshotFailures += 1;
      final setupMayBeRunning =
          _phase == _Phase.installing ||
          _phase == _Phase.checking ||
          _monitoringFailed ||
          (_status?.isRunning ?? false);
      if (setupMayBeRunning && _snapshotFailures < 3) {
        if (_poll == null) _startPolling();
        return;
      }
      _stopPolling();
      setState(() {
        _monitoringFailed = setupMayBeRunning;
        _phase = _Phase.failed;
        _error = setupMayBeRunning && _status != null
            ? _persistedFailureMessage(
                _status!,
                _setupOutput,
                bridgeMessage: error.message,
              )
            : error.message;
      });
    } finally {
      _polling = false;
    }
  }

  Future<void> _copySetupOutput() async {
    if (_setupOutput.isEmpty) return;
    await Clipboard.setData(ClipboardData(text: _setupOutput));
    if (!mounted) return;
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(const SnackBar(content: Text('Setup output copied.')));
  }

  /// The text a person reads on screen. [_error] keeps the exact bridge and
  /// protocol wording for the copied failure report, where it is useful;
  /// on screen the same fact is said in plain words.
  static String friendlyError(String raw) {
    if (raw.contains('command-result protocol')) {
      return 'This version of Termux is too old for the app to control it. '
          'Install the current F-Droid or GitHub build of Termux, then check '
          'again.';
    }
    if (raw.contains('setup manager disappeared') ||
        raw.contains('without starting the setup manager')) {
      return 'Termux opened but the setup did not start. Retry once; if it '
          'happens again, copy the failure report.';
    }
    if (raw.contains('Could not read setup manager status')) {
      return raw.replaceFirst(
        'Could not read setup manager status',
        'Lost track of the setup running in Termux',
      );
    }
    return raw;
  }

  Future<void> _copyFailureReport() async {
    String diagnostics;
    try {
      diagnostics = await TermuxBridge.diagnostics();
    } on TermuxBridgeException catch (error) {
      diagnostics = 'Diagnostics unavailable: ${error.message}';
    }
    final report = [
      if (_lastLaunchOutput?.isNotEmpty ?? false)
        '===== Last launcher result =====\n$_lastLaunchOutput',
      if (_error?.isNotEmpty ?? false) '===== Screen error =====\n$_error',
      diagnostics,
    ].join('\n');
    await Clipboard.setData(ClipboardData(text: report));
    if (!mounted) return;
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(const SnackBar(content: Text('Failure report copied.')));
  }

  Future<void> _resumeLiveOutput() async {
    if (_busy) return;
    setState(() {
      _phase = _Phase.installing;
      _error = null;
      _monitoringFailed = false;
      _snapshotFailures = 0;
    });
    _startPolling();
    await _refreshStatus();
  }

  Future<void> _finishConnect(ServerProfile profile) async {
    await ref.read(connProvider).connect(profile);
    if (!mounted) return;
    final connection = ref.read(connProvider);
    if (connection.api == null) {
      setState(() {
        _phase = _Phase.failed;
        _error =
            connection.lastError ??
            'The server started but authentication failed.';
      });
      return;
    }
    setState(() {
      _restarting = false;
      _phase = _Phase.connected;
    });
  }

  Future<void> _finishRestart(ServerProfile profile) async {
    final status = _status;
    if (status == null) return;
    _validateRestartSnapshot(status);
    if (status.operationResult == 'not_performed') {
      setState(() {
        _restarting = false;
        _restartOperationID = null;
        _phase = _Phase.connected;
        _error = status.message;
      });
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(AppLocalizations.of(context).termuxRestartNotPerformed),
        ),
      );
      return;
    }
    if (_startingExisting) {
      _startingExisting = false;
      await _finishConnect(profile);
      return;
    }
    if (!_restartProfileStillActive(profile)) return;
    final connection = ref.read(connProvider);
    await connection.retryConnection();
    if (!mounted) return;
    if (!_restartProfileStillActive(profile)) return;
    // A lifecycle resume may already have owned the first retry while the
    // server was still down. Once it completes, make one fresh attempt
    // against the manager's authenticated-ready server.
    if (connection.api == null) await connection.retryConnection();
    if (!mounted) return;
    if (!_restartProfileStillActive(profile)) return;
    if (connection.api == null) {
      setState(() {
        _restarting = false;
        _phase = _Phase.failed;
        _error =
            connection.lastError ??
            'The local server restarted, but the app could not reconnect.';
      });
      return;
    }
    setState(() {
      _restarting = false;
      _restartOperationID = null;
      _phase = _Phase.connected;
    });
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(AppLocalizations.of(context).termuxRestartSucceeded),
      ),
    );
  }

  bool _restartProfileStillActive(ServerProfile profile) {
    final active = ref.read(connProvider).profile;
    if (active?.id == profile.id && active?.baseUrl == localUrl) return true;
    setState(() {
      _restarting = false;
      _restartOperationID = null;
      _phase = _Phase.ready;
      _error =
          'The local server restarted, but the active server changed. '
          'Reconnect when you are ready.';
    });
    return false;
  }

  void _continueToApp() {
    Navigator.of(context).pushNamedAndRemoveUntil('/home', (_) => false);
  }

  Future<void> _stopServer() async {
    if (_busy) return;
    final runtime = _runtime;
    _stopPolling();
    setState(() {
      _busy = true;
      _restarting = false;
      _restartOperationID = null;
      _error = null;
    });
    try {
      final store = ref.read(bootstrapProvider).store;
      var recoveryCleanupFailed = false;
      for (final profile in store.profiles.where(
        (p) => TermuxBridge.managesServerUrl(p.baseUrl),
      )) {
        try {
          await ManagedServerRecovery.disableForProfile(
            store.prefs,
            profile.id,
          );
        } catch (_) {
          recoveryCleanupFailed = true;
        }
      }
      // Explicit Stop remains available even when preference storage or permit
      // revocation fails. The stop script revokes the permit before stopping
      // the manager, and its checked result decides whether the server stopped.
      await TermuxBridge.run(TermuxBridge.stopScript(port: port));
      await ref.read(connProvider).disconnect();
      if (!mounted) return;
      setState(() {
        _committedRuntime = runtime;
        _status = null;
        _installation = null;
        _phase = _Phase.ready;
        _error = recoveryCleanupFailed
            ? lookupAppLocalizations(
                Localizations.localeOf(context),
              ).managedRecoveryStoppedWithCleanupError
            : 'The local server is stopped. Its installed files are kept.';
      });
      await _checkInstallation();
    } on TermuxBridgeException catch (error) {
      if (!mounted) return;
      setState(() {
        _phase = _Phase.failed;
        _error = 'Could not stop the local server: ${error.message}';
      });
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _phase = _Phase.failed;
        _error = 'The server stopped, but the app could not disconnect: $error';
      });
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _retry() async {
    if (_busy) return;
    setState(() => _busy = true);
    var stopped = true;
    try {
      await TermuxBridge.run(TermuxBridge.stopScript(port: port));
    } on TermuxBridgeException catch (error) {
      stopped = false;
      if (mounted) setState(() => _error = error.message);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
    if (mounted && stopped) await _installAndStart();
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    // Nothing routes here off Android, but a deep link, a restored route, or
    // a future entry point could. The screen says so plainly instead of
    // presenting six steps that can never complete.
    if (!platformCapabilities.supportsTermux) {
      return Scaffold(
        appBar: AppBar(
          title: Text(AppLocalizations.of(context).setupScreenTitle),
        ),
        body: SingleChildScrollView(
          child: Center(
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 420),
              child: Padding(
                key: const Key('termux-setup-unsupported'),
                padding: const EdgeInsets.all(24),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(
                      AppIconography.deviceOff,
                      size: 40,
                      color: AppTheme.mutedOf(theme),
                    ),
                    const SizedBox(height: 12),
                    Text(
                      'On-device setup is Android only',
                      style: theme.textTheme.titleMedium,
                      textAlign: TextAlign.center,
                    ),
                    const SizedBox(height: 8),
                    Text(
                      'It drives Termux, which has no desktop equivalent. On '
                      'this machine, run `opencode serve` yourself and add it '
                      'as a server.',
                      style: theme.textTheme.bodySmall!.copyWith(
                        color: AppTheme.mutedOf(theme),
                      ),
                      textAlign: TextAlign.center,
                    ),
                    const SizedBox(height: 24),
                    _existingServerChoice(),
                  ],
                ),
              ),
            ),
          ),
        ),
      );
    }
    final l10n = AppLocalizations.of(context);
    if (_phase == _Phase.installing) {
      return Scaffold(
        appBar: AppBar(
          title: Text(AppLocalizations.of(context).setupScreenTitle),
        ),
        body: SafeArea(child: _buildSetupProgress()),
      );
    }
    return Scaffold(
      appBar: AppBar(
        title: Text(AppLocalizations.of(context).setupScreenTitle),
      ),
      body: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 560),
          child: ListView(
            padding: const EdgeInsets.all(20),
            shrinkWrap: true,
            children: [
              Row(
                children: [
                  Icon(AppIconography.phone, color: theme.colorScheme.primary),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      l10n.setupChooseServerTitle,
                      style: theme.textTheme.titleMedium,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 6),
              Text(
                l10n.setupChooseServerDescription,
                style: theme.textTheme.bodySmall!.copyWith(
                  color: AppTheme.mutedOf(theme),
                ),
              ),
              const SizedBox(height: 16),
              _existingServerChoice(),
              const Padding(
                padding: EdgeInsets.symmetric(vertical: 12),
                child: Divider(),
              ),
              _stepTile(
                n: 1,
                title: 'Get Termux',
                state:
                    const {_Phase.checking, _Phase.needTermux}.contains(_phase)
                    ? _StepState.idle
                    : _StepState.done,
                body: _phase == _Phase.needTermux
                    ? Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          const Text(
                            'Install the current F-Droid build of Termux, then return here.',
                          ),
                          const SizedBox(height: 10),
                          Wrap(
                            spacing: 8,
                            runSpacing: 8,
                            children: [
                              FilledButton.icon(
                                onPressed: _getTermux,
                                icon: const Icon(AppIconography.download),
                                label: const Text('Download page'),
                              ),
                              OutlinedButton(
                                onPressed: _refresh,
                                child: Text(
                                  AppLocalizations.of(context).setupCheckAgain,
                                ),
                              ),
                            ],
                          ),
                        ],
                      )
                    : null,
              ),
              const SizedBox(height: 8),
              _stepTile(
                n: 2,
                title: l10n.termuxGuideTitle,
                state: _phase == _Phase.needUnlock
                    ? _StepState.idle
                    : _phase == _Phase.checking || _phase == _Phase.needTermux
                    ? _StepState.idle
                    : _StepState.done,
                enabled: _phase != _Phase.needTermux,
                body: _phase == _Phase.needUnlock
                    ? Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(l10n.termuxGuideIntro),
                          if (_error != null) ...[
                            const SizedBox(height: 10),
                            Semantics(
                              liveRegion: true,
                              child: Text(
                                friendlyError(_error!),
                                style: TextStyle(
                                  color: theme.colorScheme.error,
                                ),
                              ),
                            ),
                          ],
                          const SizedBox(height: 10),
                          // Before the round trip, opening Termux is the
                          // next thing to do. After it, verifying is.
                          Wrap(
                            spacing: 8,
                            runSpacing: 8,
                            children: [
                              if (_returnedFromTermux) ...[
                                FilledButton.icon(
                                  key: const Key('termux-verify-unlock'),
                                  onPressed: _busy ? null : _verifyUnlock,
                                  icon: _busy && !_copyingToTermux
                                      ? const SizedBox.square(
                                          dimension: 18,
                                          child: CircularProgressIndicator(
                                            strokeWidth: 2,
                                          ),
                                        )
                                      : const Icon(AppIconography.check),
                                  label: Text(
                                    _busy && !_copyingToTermux
                                        ? 'Verifying...'
                                        : 'Verify & continue',
                                  ),
                                ),
                                OutlinedButton.icon(
                                  key: const Key('termux-copy-open'),
                                  onPressed: _busy ? null : _openTermuxAndCopy,
                                  icon: const Icon(AppIconography.externalLink),
                                  label: Text(
                                    _copyingToTermux
                                        ? l10n.termuxGuideOpening
                                        : 'Copy & open Termux',
                                  ),
                                ),
                              ] else ...[
                                FilledButton.icon(
                                  key: const Key('termux-copy-open'),
                                  onPressed: _busy ? null : _openTermuxAndCopy,
                                  icon: const Icon(AppIconography.externalLink),
                                  label: Text(
                                    _copyingToTermux
                                        ? l10n.termuxGuideOpening
                                        : 'Copy & open Termux',
                                  ),
                                ),
                                OutlinedButton(
                                  key: const Key('termux-verify-unlock'),
                                  onPressed: _busy ? null : _verifyUnlock,
                                  child: Text(
                                    _busy && !_copyingToTermux
                                        ? 'Verifying...'
                                        : 'Verify & continue',
                                  ),
                                ),
                              ],
                              TextButton(
                                onPressed: _busy
                                    ? null
                                    : TermuxBridge.openAppSettings,
                                child: const Text('App settings'),
                              ),
                            ],
                          ),
                          const SizedBox(height: 12),
                          const _TermuxPasteGuide(),
                          const SizedBox(height: 12),
                          Text(l10n.termuxGuideAutomaticCheck),
                          ExpansionTile(
                            tilePadding: EdgeInsets.zero,
                            title: Text(l10n.termuxGuideShowCommand),
                            children: const [CmdPreview()],
                          ),
                        ],
                      )
                    : null,
              ),
              const SizedBox(height: 8),
              _stepTile(
                n: 3,
                title: 'Choose how to continue',
                state: switch (_phase) {
                  _Phase.installing => _StepState.running,
                  _Phase.connected => _StepState.done,
                  _Phase.failed => _StepState.error,
                  _ => _StepState.idle,
                },
                enabled: !const {
                  _Phase.needTermux,
                  _Phase.needUnlock,
                  _Phase.checking,
                }.contains(_phase),
                body: switch (_phase) {
                  _Phase.checking => const _ProgressLine(
                    text: 'Checking Termux...',
                  ),
                  _Phase.ready => Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      if (_error != null) ...[
                        Text(friendlyError(_error!)),
                        const SizedBox(height: 10),
                      ],
                      _setupChoices(),
                    ],
                  ),
                  _Phase.installing => null,
                  _Phase.connected => Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Icon(
                            AppIconography.checkCircle,
                            size: 18,
                            color: AppTheme.statusColor(
                              Theme.of(context),
                              AppStatusTone.ok,
                            ),
                          ),
                          const SizedBox(width: 8),
                          const Expanded(
                            child: Text('OpenCode is running on this phone.'),
                          ),
                        ],
                      ),
                      const SizedBox(height: 6),
                      Text(
                        _status?.version.isNotEmpty == true
                            ? 'Version ${_status!.version} · $localUrl'
                            : localUrl,
                        style: theme.textTheme.bodySmall!.copyWith(
                          color: AppTheme.mutedOf(theme),
                        ),
                      ),
                      const SizedBox(height: 12),
                      Wrap(
                        spacing: 8,
                        runSpacing: 8,
                        children: [
                          FilledButton.icon(
                            onPressed: _busy ? null : _continueToApp,
                            icon: const Icon(AppIconography.forward),
                            label: const Text('Continue to app'),
                          ),
                          OutlinedButton.icon(
                            key: const Key('restart-managed-opencode'),
                            onPressed: _busy ? null : _confirmRestart,
                            icon: const Icon(AppIconography.restart),
                            label: Text(
                              _restarting
                                  ? l10n.termuxRestarting
                                  : l10n.termuxRestartServer,
                            ),
                          ),
                          OutlinedButton.icon(
                            key: const Key('update-managed-opencode'),
                            onPressed: _busy ? null : _confirmUpdate,
                            icon: const Icon(AppIconography.systemDownload),
                            label: const Text('Update OpenCode'),
                          ),
                          OutlinedButton.icon(
                            onPressed: _busy ? null : _stopServer,
                            icon: const Icon(AppIcons.stop),
                            label: Text(
                              _busy ? 'Stopping...' : 'Stop local server',
                            ),
                          ),
                        ],
                      ),
                    ],
                  ),
                  _Phase.failed => Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        _error == null
                            ? 'Setup failed.'
                            : friendlyError(_error!),
                        style: TextStyle(color: theme.colorScheme.error),
                      ),
                      const SizedBox(height: 10),
                      SetupTerminal(
                        output: _setupOutput,
                        running: false,
                        controller: _outputScrollController,
                        onCopy: _copyFailureReport,
                        copyTooltip: 'Copy failure report',
                      ),
                      const SizedBox(height: 10),
                      _setupChoices(showInstall: false),
                      const SizedBox(height: 12),
                      if (_monitoringFailed)
                        Wrap(
                          spacing: 8,
                          runSpacing: 8,
                          children: [
                            FilledButton.icon(
                              onPressed: _busy ? null : _resumeLiveOutput,
                              icon: const Icon(AppIconography.sync),
                              label: const Text('Resume live view'),
                            ),
                            OutlinedButton(
                              onPressed: _busy ? null : _retry,
                              child: const Text(
                                'Retry — resumes where setup left off',
                              ),
                            ),
                          ],
                        )
                      else
                        FilledButton.icon(
                          onPressed: _busy ? null : _retry,
                          icon: const Icon(AppIconography.retry),
                          label: const Text(
                            'Retry — resumes where setup left off',
                          ),
                        ),
                    ],
                  ),
                  _ => null,
                },
              ),
            ],
          ),
        ),
      ),
    );
  }

  Future<void> _checkInstallation() async {
    if (_checkingInstallation || _launching) return;
    final epoch = _statusEpoch;
    setState(() {
      _checkingInstallation = true;
      _installationError = null;
    });
    try {
      final installation = await TermuxBridge.inspectInstallation();
      if (!mounted || epoch != _statusEpoch) return;
      setState(() {
        _installation = installation;
        if (installation.runtimeSelected ||
            installation.openCodeVersion != null) {
          _selectedRuntime = installation.runtime;
          _committedRuntime = installation.runtime;
        } else if (!installation.ubuntuInstalled) {
          // An authoritative empty inventory also covers a user removing
          // Termux's installation outside this screen.
          _committedRuntime = null;
        }
      });
    } on TermuxBridgeException {
      if (!mounted || epoch != _statusEpoch) return;
      setState(() {
        _installation = null;
        _installationError = 'Could not check the installed environment.';
      });
    } finally {
      if (mounted) setState(() => _checkingInstallation = false);
    }
  }

  Future<void> _startInstalled() async {
    final profile = _localProfile();
    if (_busy || profile == null) return;
    final confirmed = await showConfirmSheet(
      context,
      title: 'Start installed OpenCode?',
      message:
          'Use OpenCode ${_installation?.openCodeVersion ?? ''} in the '
          'existing Ubuntu environment and connect to it. This restarts only '
          'the managed local server; it does not download or update packages.',
      confirmLabel: 'Start & connect',
      icon: AppIconography.play,
    );
    if (confirmed && mounted) {
      await _restartServer(profile, startExisting: true);
    }
  }

  Widget _setupChoices({bool showInstall = true}) {
    final theme = Theme.of(context);
    final l10n = AppLocalizations.of(context);
    final installed = _installation;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (_checkingInstallation)
          const _ProgressLine(text: 'Checking installed environment...')
        else if (_installationError != null) ...[
          Text(_installationError!),
          TextButton.icon(
            onPressed: _busy ? null : _checkInstallation,
            icon: const Icon(AppIconography.retry),
            label: Text(AppLocalizations.of(context).setupCheckAgain),
          ),
        ] else if (installed != null) ...[
          Text(
            installed.openCodeVersion != null
                ? 'Found OpenCode ${installed.openCodeVersion} in Ubuntu'
                : installed.ubuntuInstalled
                ? 'Ubuntu is installed. OpenCode is not installed yet.'
                : 'No managed Ubuntu installation found.',
            style: theme.textTheme.bodyMedium?.copyWith(
              fontWeight: FontWeight.w600,
            ),
          ),
          const SizedBox(height: 8),
          if (installed.openCodeVersion != null && _localProfile() != null)
            FilledButton.icon(
              onPressed: _busy ? null : _startInstalled,
              icon: const Icon(AppIconography.play),
              label: Text(AppLocalizations.of(context).setupStartInstalled),
            ),
          if (installed.openCodeVersion != null && _localProfile() == null)
            Text(AppLocalizations.of(context).setupMissingCredential),
        ],
        if (showInstall) ...[
          const SizedBox(height: 12),
          if (installed != null &&
              installed.openCodeVersion == null &&
              _knownRuntime == null) ...[
            Text(l10n.setupRuntimeTitle, style: theme.textTheme.titleMedium),
            const SizedBox(height: 8),
            RadioGroup<TermuxRuntime>(
              groupValue: _selectedRuntime,
              onChanged: (value) {
                if (_busy || _checkingInstallation || value == null) return;
                setState(() => _selectedRuntime = value);
              },
              child: Column(
                children: [
                  RadioListTile<TermuxRuntime>(
                    key: const Key('setup-runtime-opencode1'),
                    value: TermuxRuntime.openCode1,
                    enabled: !_busy && !_checkingInstallation,
                    title: Text(l10n.setupRuntimeOne),
                    subtitle: Text(l10n.setupRuntimeOneDetail),
                    contentPadding: EdgeInsets.zero,
                  ),
                  RadioListTile<TermuxRuntime>(
                    key: const Key('setup-runtime-opencode2'),
                    value: TermuxRuntime.openCode2,
                    enabled: !_busy && !_checkingInstallation,
                    title: Text(l10n.setupRuntimeTwo),
                    subtitle: Text(l10n.setupRuntimeTwoDetail),
                    contentPadding: EdgeInsets.zero,
                  ),
                ],
              ),
            ),
            const SizedBox(height: 12),
          ],
          Text(
            AppLocalizations.of(context).setupUbuntuOption,
            style: theme.textTheme.titleSmall,
          ),
          const SizedBox(height: 4),
          Text(
            l10n.setupRuntimeInstallDetail(
              _runtimeName(_runtime),
              _runtime.pinnedVersion,
            ),
          ),
          const SizedBox(height: 10),
          if (installed?.openCodeVersion != null)
            OutlinedButton.icon(
              onPressed: _busy || _checkingInstallation
                  ? null
                  : _reviewInstallChoice,
              icon: const Icon(AppIconography.tools),
              label: Text(
                installed?.openCodeVersion == _runtime.pinnedVersion
                    ? AppLocalizations.of(context).setupReinstallStart
                    : AppLocalizations.of(
                        context,
                      ).setupInstallVersionStart(_runtime.pinnedVersion),
              ),
            )
          else
            FilledButton.icon(
              onPressed: _busy || _checkingInstallation
                  ? null
                  : _reviewInstallChoice,
              icon: const Icon(AppIconography.launch),
              label: Text(AppLocalizations.of(context).setupInstallStart),
            ),
        ],
      ],
    );
  }

  Future<void> _reviewInstallChoice() async {
    if (_busy || _checkingInstallation) return;
    final installedVersion = _installation?.openCodeVersion;
    if (installedVersion != null) {
      final l10n = AppLocalizations.of(context);
      final confirmed = await showConfirmSheet(
        context,
        title: l10n.setupReplaceTitle,
        message: l10n.setupReplaceDescription(
          installedVersion,
          _runtime.pinnedVersion,
        ),
        confirmLabel: l10n.setupInstallRestart,
        icon: AppIconography.tools,
      );
      if (!confirmed || !mounted) return;
    }
    if (_installation == null) {
      final l10n = AppLocalizations.of(context);
      final confirmed = await showConfirmSheet(
        context,
        title: l10n.setupUncheckedTitle,
        message: l10n.setupUncheckedDescription,
        confirmLabel: l10n.setupUncheckedContinue,
        icon: AppIconography.question,
      );
      if (!confirmed || !mounted) return;
    }
    await _installAndStart();
  }

  Widget _existingServerChoice() {
    final theme = Theme.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          AppLocalizations.of(context).setupOwnOption,
          style: theme.textTheme.titleSmall,
        ),
        const SizedBox(height: 4),
        Text(AppLocalizations.of(context).setupOwnDescription),
        const SizedBox(height: 8),
        OutlinedButton.icon(
          onPressed: _busy
              ? null
              : () => Navigator.of(context).pushNamed('/servers'),
          icon: const Icon(AppIconography.link),
          label: Text(AppLocalizations.of(context).setupConnectExisting),
        ),
      ],
    );
  }

  Widget _buildSetupProgress() {
    final theme = Theme.of(context);
    final stage = _status?.phase;
    final title = _launching
        ? 'Preparing setup'
        : switch (stage) {
            'installing_ubuntu' => 'Setting up Ubuntu',
            'installing_opencode' => 'Installing OpenCode',
            'refreshing_models' => 'Getting models ready',
            'starting_server' => 'Starting local server',
            'restarting' => 'Restarting local server',
            _ => 'Preparing setup',
          };
    final message = _launching
        ? _launchMessage
        : _status?.message ?? 'Reading setup progress';
    final elapsedSeconds = _elapsedSeconds;
    final elapsed = elapsedSeconds == null
        ? null
        : elapsedSeconds < 60
        ? '${elapsedSeconds}s elapsed'
        : '${elapsedSeconds ~/ 60}m ${elapsedSeconds % 60}s elapsed';
    final summary = Padding(
      padding: const EdgeInsets.fromLTRB(20, 12, 20, 16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: [
          Semantics(
            liveRegion: true,
            child: Text(
              title,
              style: theme.textTheme.headlineSmall?.copyWith(
                color: theme.colorScheme.primary,
                fontWeight: FontWeight.w700,
              ),
            ),
          ),
          const SizedBox(height: 12),
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Padding(
                padding: EdgeInsets.only(top: 3),
                child: SizedBox.square(
                  dimension: 18,
                  child: CircularProgressIndicator(strokeWidth: 2),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Semantics(liveRegion: true, child: Text(message)),
              ),
            ],
          ),
          if (elapsed != null) ...[
            const SizedBox(height: 10),
            // Do not interrupt a screen reader every second. Legacy status
            // without a start timestamp cannot supply an elapsed total.
            Text(elapsed, style: theme.textTheme.labelLarge),
          ],
          const SizedBox(height: 10),
          Text(
            _launching
                ? 'Waiting for Termux to respond. This can take a little while.'
                : _restarting
                ? AppLocalizations.of(context).termuxRestartProgress
                : 'First-time setup can take 10–15 minutes. You can leave '
                      'this screen and return; setup keeps running.',
            style: theme.textTheme.bodySmall?.copyWith(
              color: theme.colorScheme.onSurfaceVariant,
            ),
          ),
        ],
      ),
    );
    Widget terminal({required bool expand}) => SetupTerminal(
      output: _setupOutput,
      running: true,
      controller: _outputScrollController,
      onCopy: _setupOutput.isEmpty ? null : _copySetupOutput,
      expand: expand,
    );

    return Align(
      alignment: Alignment.topCenter,
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 960),
        child: LayoutBuilder(
          builder: (context, constraints) {
            // Keep the log in the available screen area on a phone. At large
            // text sizes or in landscape, let the page scroll so status and
            // actions remain readable instead of squeezing either section.
            final largeText = MediaQuery.textScalerOf(context).scale(14) > 21;
            if (constraints.maxHeight < 560 || largeText) {
              return ListView(children: [summary, terminal(expand: false)]);
            }
            return Column(
              children: [
                summary,
                Expanded(child: terminal(expand: true)),
              ],
            );
          },
        ),
      ),
    );
  }

  Widget _stepTile({
    required int n,
    required String title,
    required _StepState state,
    Widget? body,
    bool enabled = true,
  }) {
    final theme = Theme.of(context);
    final stateLabel = !enabled
        ? 'not yet available'
        : switch (state) {
            _StepState.done => 'done',
            _StepState.running => 'in progress',
            _StepState.error => 'failed',
            _StepState.idle => 'to do',
          };
    // The header row is spoken as one phrase ("Step 2 of 3, done. Let the
    // app control Termux") rather than a badge, a title and a spinner read
    // separately; the body keeps its own semantics. Dimming alone is not the
    // signal for "not yet": a locked step also says so in text.
    return Semantics(
      container: true,
      label: 'Step $n of 3, $stateLabel. $title',
      child: Opacity(
        opacity: enabled ? 1 : .6,
        child: Container(
          padding: const EdgeInsets.symmetric(vertical: 14),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              ExcludeSemantics(
                child: Row(
                  children: [
                    Builder(
                      builder: (context) {
                        final background = switch (state) {
                          _StepState.done => AppTheme.successOf(theme),
                          _StepState.running => theme.colorScheme.primary,
                          _StepState.error => theme.colorScheme.error,
                          _StepState.idle =>
                            theme.colorScheme.surfaceContainerHighest,
                        };
                        final foreground = state == _StepState.idle
                            ? theme.colorScheme.onSurfaceVariant
                            : ThemeData.estimateBrightnessForColor(
                                    background,
                                  ) ==
                                  Brightness.dark
                            ? Colors.white
                            : Colors.black87;
                        return CircleAvatar(
                          radius: 11,
                          backgroundColor: background,
                          child: state == _StepState.done
                              ? Icon(
                                  AppIconography.check,
                                  size: 14,
                                  color: foreground,
                                )
                              : Text(
                                  '$n',
                                  style: TextStyle(
                                    fontSize: AppTheme.codeFontSize,
                                    color: foreground,
                                  ),
                                ),
                        );
                      },
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: Text(title, style: theme.textTheme.titleSmall),
                    ),
                    if (state == _StepState.running)
                      const SizedBox(
                        width: 14,
                        height: 14,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      ),
                    if (!enabled)
                      Text(
                        'Not yet',
                        style: theme.textTheme.labelSmall?.copyWith(
                          color: AppTheme.mutedOf(theme),
                        ),
                      ),
                  ],
                ),
              ),
              if (body != null) ...[const SizedBox(height: 10), body],
            ],
          ),
        ),
      ),
    );
  }
}

enum _StepState { idle, running, done, error }

class _ProgressLine extends StatelessWidget {
  final String text;
  const _ProgressLine({required this.text});

  @override
  Widget build(BuildContext context) => Row(
    children: [
      const SizedBox(
        width: 14,
        height: 14,
        child: CircularProgressIndicator(strokeWidth: 2),
      ),
      const SizedBox(width: 10),
      Expanded(child: Text(text)),
    ],
  );
}

class _TermuxPasteGuide extends StatelessWidget {
  const _TermuxPasteGuide();

  // This terminal output must match unlockCommand; it is not translated.
  static const _successOutput = 'bridge-unlocked';

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final l10n = AppLocalizations.of(context);
    final terminalStyle = theme.textTheme.bodyMedium?.copyWith(
      fontFamily: AppTheme.monoFamily,
    );
    return Column(
      children: [
        _PasteGuideStep(
          title: l10n.termuxGuideCopyTitle,
          description: l10n.termuxGuideCopyDescription,
          illustration: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(l10n.appTitle),
              const SizedBox(height: 8),
              Wrap(
                spacing: 8,
                crossAxisAlignment: WrapCrossAlignment.center,
                children: [
                  Icon(AppIconography.copy, color: theme.colorScheme.primary),
                  Text(l10n.termuxGuideCopied),
                  const Icon(AppIconography.externalLink),
                ],
              ),
            ],
          ),
        ),
        const SizedBox(height: 10),
        _PasteGuideStep(
          title: l10n.termuxGuidePasteTitle,
          description: l10n.termuxGuidePasteDescription,
          illustration: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('~ \$ ▋', style: terminalStyle),
              const SizedBox(height: 8),
              Wrap(
                spacing: 12,
                runSpacing: 8,
                crossAxisAlignment: WrapCrossAlignment.center,
                children: [
                  DecoratedBox(
                    decoration: BoxDecoration(
                      color: theme.colorScheme.secondaryContainer,
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: Padding(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 16,
                        vertical: 8,
                      ),
                      child: Text(
                        l10n.termuxGuidePaste,
                        style: TextStyle(
                          color: theme.colorScheme.onSecondaryContainer,
                        ),
                      ),
                    ),
                  ),
                  const Icon(AppIconography.touch),
                ],
              ),
            ],
          ),
        ),
        const SizedBox(height: 10),
        _PasteGuideStep(
          title: l10n.termuxGuideEnterTitle,
          description: l10n.termuxGuideEnterDescription,
          illustration: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(_successOutput, style: terminalStyle),
              const SizedBox(height: 8),
              DecoratedBox(
                decoration: BoxDecoration(
                  color: theme.colorScheme.primaryContainer,
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Padding(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 16,
                    vertical: 8,
                  ),
                  child: Wrap(
                    spacing: 8,
                    crossAxisAlignment: WrapCrossAlignment.center,
                    children: [
                      Icon(
                        AppIconography.returnKey,
                        color: theme.colorScheme.onPrimaryContainer,
                      ),
                      Text(
                        l10n.termuxGuideEnterKey,
                        style: TextStyle(
                          color: theme.colorScheme.onPrimaryContainer,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 8),
        Text(
          l10n.termuxGuideIllustrationNote,
          style: theme.textTheme.bodySmall,
        ),
      ],
    );
  }
}

class _PasteGuideStep extends StatelessWidget {
  const _PasteGuideStep({
    required this.title,
    required this.description,
    required this.illustration,
  });

  final String title;
  final String description;
  final Widget illustration;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        border: Border.all(color: theme.colorScheme.outlineVariant),
        borderRadius: BorderRadius.circular(12),
      ),
      child: LayoutBuilder(
        builder: (context, constraints) {
          final instructions = Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Semantics(
                header: true,
                child: Text(title, style: theme.textTheme.titleSmall),
              ),
              const SizedBox(height: 6),
              Text(description),
            ],
          );
          final preview = ExcludeSemantics(
            child: Container(
              width: double.infinity,
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: theme.colorScheme.surfaceContainerHighest,
                borderRadius: BorderRadius.circular(8),
              ),
              child: illustration,
            ),
          );
          // Keep the illustrations alongside the instructions when there is
          // room; large text and small phones use a single reading column.
          if (constraints.maxWidth >= 300 &&
              MediaQuery.textScalerOf(context).scale(16) <= 20) {
            return Row(
              children: [
                Expanded(child: instructions),
                const SizedBox(width: 12),
                SizedBox(
                  width: constraints.maxWidth >= 440 ? 190 : 136,
                  child: preview,
                ),
              ],
            );
          }
          return Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [instructions, const SizedBox(height: 12), preview],
          );
        },
      ),
    );
  }
}

class CmdPreview extends StatelessWidget {
  const CmdPreview({super.key});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(10),
      decoration: BoxDecoration(
        color: theme.brightness == Brightness.dark
            ? Colors.black.withValues(alpha: .45)
            : Colors.black.withValues(alpha: .05),
        borderRadius: BorderRadius.circular(8),
      ),
      child: SelectableText(
        TermuxBridge.unlockCommand,
        style: theme.textTheme.bodySmall!.copyWith(
          fontFamily: AppTheme.monoFamily,
          fontSize: AppTheme.captionFontSize,
        ),
      ),
    );
  }
}
