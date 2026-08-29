import 'dart:async';
import 'dart:convert';
import 'dart:math';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../state/connection.dart';
import '../../state/profiles.dart';
import '../../termux/bridge.dart';
import '../app_theme.dart';

class TermuxSetupScreen extends ConsumerStatefulWidget {
  const TermuxSetupScreen({super.key});

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
  Timer? _poll;
  bool _busy = false;
  bool _refreshing = false;
  bool _polling = false;
  bool _monitoringFailed = false;
  int _snapshotFailures = 0;
  int _elapsedSeconds = 0;
  String? _error;
  String? _lastLaunchOutput;
  String _setupOutput = '';
  final ScrollController _outputScrollController = ScrollController();

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    WidgetsBinding.instance.addPostFrameCallback((_) => unawaited(_refresh()));
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) unawaited(_refresh());
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _poll?.cancel();
    _outputScrollController.dispose();
    super.dispose();
  }

  Future<void> _refresh() async {
    if (_refreshing) return;
    _refreshing = true;
    try {
      final capabilities = await TermuxBridge.capabilities();
      if (!mounted) return;
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
          _error = null;
        });
        return;
      }

      try {
        await TermuxBridge.verifyBridge();
      } on TermuxBridgeException catch (error) {
        if (!mounted) return;
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
      await _refreshStatus();
    } on PlatformException catch (error) {
      if (!mounted) return;
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
    await Clipboard.setData(
      const ClipboardData(text: TermuxBridge.unlockCommand),
    );
    final opened = await TermuxBridge.openTermux();
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(
          opened
              ? 'Paste the copied line in Termux, press Enter, then return here.'
              : 'Could not open Termux.',
        ),
        duration: const Duration(seconds: 5),
      ),
    );
  }

  Future<void> _verifyUnlock() async {
    if (_busy) return;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final granted = await TermuxBridge.requestPermission();
      if (!granted) {
        throw const TermuxBridgeException(
          'Android denied the Termux command permission. Allow it in OpenCode app settings.',
          code: 'permission_denied',
        );
      }
      await TermuxBridge.verifyBridge();
      await _refreshStatus();
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
      if (candidate.baseUrl == localUrl) {
        profile = candidate;
        break;
      }
    }
    profile ??= ServerProfile(
      id: DateTime.now().microsecondsSinceEpoch.toString(),
      name: 'This device (Termux)',
      baseUrl: localUrl,
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
      if (profile.baseUrl == localUrl && profile.password.isNotEmpty) {
        return profile;
      }
    }
    return null;
  }

  Future<void> _installAndStart() async {
    if (_busy || _phase == _Phase.installing) return;
    var launchRequested = false;
    setState(() {
      _busy = true;
      _error = null;
      _elapsedSeconds = 0;
      _monitoringFailed = false;
      _snapshotFailures = 0;
      _setupOutput = '';
      _lastLaunchOutput = null;
    });
    try {
      await TermuxBridge.verifyBridge();
      final profile = await _ensureLocalProfile();
      final command = TermuxBridge.installAndServeScript(
        port: port,
        password: profile.password,
      );
      launchRequested = true;
      final launch = await TermuxBridge.run(command);
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
      var initialStatus = await TermuxBridge.status();
      for (
        var attempt = 0;
        attempt < 10 && initialStatus.phase == 'idle';
        attempt++
      ) {
        await Future<void>.delayed(const Duration(milliseconds: 250));
        initialStatus = await TermuxBridge.status();
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
      if (mounted) setState(() => _busy = false);
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
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('Update managed OpenCode?'),
        content: Text(
          [
            if (currentVersion?.isNotEmpty == true)
              'Installed version: $currentVersion.',
            'The app will install the latest stable OpenCode release, refresh its model catalog, restart only the managed local server, and reconnect this profile.',
            'The server will be briefly unavailable. Active generation should be stopped first.',
          ].join('\n\n'),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(dialogContext).pop(true),
            child: const Text('Update'),
          ),
        ],
      ),
    );
    if (confirmed == true && mounted) await _installAndStart();
  }

  Future<bool> _recoverPersistedSetupAfterTimeout() async {
    try {
      final snapshot = await TermuxBridge.setupSnapshot();
      if (!mounted || snapshot.status.phase == 'idle') return false;
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
        await _finishConnect(profile);
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

  void _startPolling() {
    _poll?.cancel();
    _poll = Timer.periodic(const Duration(seconds: 1), (_) {
      _elapsedSeconds += 1;
      unawaited(_refreshStatus());
    });
  }

  void _stopPolling() {
    _poll?.cancel();
    _poll = null;
  }

  Future<void> _refreshStatus() async {
    if (_polling) return;
    _polling = true;
    try {
      final snapshot = await TermuxBridge.setupSnapshot();
      if (!mounted) return;
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
        setState(() => _phase = _Phase.installing);
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
        await _finishConnect(profile);
        return;
      }
      setState(() {
        _phase = _Phase.ready;
        _error = null;
      });
    } on TermuxBridgeException catch (error) {
      if (!mounted) return;
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
    setState(() => _phase = _Phase.connected);
  }

  void _continueToApp() {
    Navigator.of(context).pushNamedAndRemoveUntil('/home', (_) => false);
  }

  Future<void> _stopServer() async {
    if (_busy) return;
    _stopPolling();
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await TermuxBridge.run(TermuxBridge.stopScript(port: port));
      await ref.read(connProvider).disconnect();
      if (!mounted) return;
      setState(() {
        _status = null;
        _phase = _Phase.ready;
        _error = 'The local server is stopped. Its installed files are kept.';
      });
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
    return Scaffold(
      appBar: AppBar(title: const Text('On-device setup')),
      body: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 560),
          child: ListView(
            padding: const EdgeInsets.all(20),
            shrinkWrap: true,
            children: [
              Row(
                children: [
                  Icon(
                    Icons.smartphone_rounded,
                    color: theme.colorScheme.primary,
                  ),
                  const SizedBox(width: 8),
                  Text(
                    'Run OpenCode on this phone',
                    style: theme.textTheme.titleMedium,
                  ),
                ],
              ),
              const SizedBox(height: 6),
              Text(
                'The app installs OpenCode in a private Ubuntu environment, starts '
                'an authenticated local server, and reconnects automatically.',
                style: theme.textTheme.bodySmall!.copyWith(
                  color: theme.hintColor,
                ),
              ),
              const SizedBox(height: 20),
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
                                icon: const Icon(Icons.download_rounded),
                                label: const Text('Download page'),
                              ),
                              OutlinedButton(
                                onPressed: _refresh,
                                child: const Text('Check again'),
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
                title: 'Authorize the bridge',
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
                          const Text(
                            'Termux requires one local setting before Android can send it commands. '
                            'Copy the line, run it once in Termux, then verify here.',
                          ),
                          const SizedBox(height: 10),
                          const CmdPreview(),
                          if (_error != null) ...[
                            const SizedBox(height: 10),
                            Text(
                              _error!,
                              style: TextStyle(color: theme.colorScheme.error),
                            ),
                          ],
                          const SizedBox(height: 10),
                          Wrap(
                            spacing: 8,
                            runSpacing: 8,
                            children: [
                              FilledButton.icon(
                                onPressed: _busy ? null : _openTermuxAndCopy,
                                icon: const Icon(Icons.open_in_new_rounded),
                                label: const Text('Copy & open Termux'),
                              ),
                              OutlinedButton(
                                onPressed: _busy ? null : _verifyUnlock,
                                child: Text(
                                  _busy ? 'Verifying...' : 'Verify & continue',
                                ),
                              ),
                              TextButton(
                                onPressed: _busy
                                    ? null
                                    : TermuxBridge.openAppSettings,
                                child: const Text('App settings'),
                              ),
                            ],
                          ),
                        ],
                      )
                    : null,
              ),
              const SizedBox(height: 8),
              _stepTile(
                n: 3,
                title: 'Install or update OpenCode',
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
                      Text(
                        _error ??
                            'Setup runs once in the background and records every stage. '
                                'Leaving this screen will not start another installer.',
                      ),
                      const SizedBox(height: 10),
                      FilledButton.icon(
                        onPressed: _busy ? null : _installAndStart,
                        icon: const Icon(Icons.rocket_launch_rounded),
                        label: const Text('Install & start'),
                      ),
                    ],
                  ),
                  _Phase.installing => Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      _ProgressLine(
                        text:
                            '${_status?.message ?? 'Starting setup'} ($_elapsedSeconds s)',
                      ),
                      const SizedBox(height: 8),
                      Text(
                        'The first install downloads Ubuntu and can take several minutes. '
                        'You can leave this screen and return later.',
                        style: theme.textTheme.bodySmall!.copyWith(
                          color: theme.hintColor,
                        ),
                      ),
                      const SizedBox(height: 8),
                      _LiveSetupTerminal(
                        output: _setupOutput,
                        running: true,
                        controller: _outputScrollController,
                        onCopy: _setupOutput.isEmpty ? null : _copySetupOutput,
                      ),
                    ],
                  ),
                  _Phase.connected => Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Icon(
                            Icons.check_circle_rounded,
                            size: 18,
                            color: Colors.green.shade400,
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
                          color: theme.hintColor,
                        ),
                      ),
                      const SizedBox(height: 12),
                      Wrap(
                        spacing: 8,
                        runSpacing: 8,
                        children: [
                          FilledButton.icon(
                            onPressed: _busy ? null : _continueToApp,
                            icon: const Icon(Icons.arrow_forward_rounded),
                            label: const Text('Continue to app'),
                          ),
                          OutlinedButton.icon(
                            key: const Key('update-managed-opencode'),
                            onPressed: _busy ? null : _confirmUpdate,
                            icon: const Icon(Icons.system_update_alt_rounded),
                            label: const Text('Update OpenCode'),
                          ),
                          OutlinedButton.icon(
                            onPressed: _busy ? null : _stopServer,
                            icon: const Icon(Icons.stop_circle_outlined),
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
                        _error ?? 'Setup failed.',
                        style: TextStyle(color: theme.colorScheme.error),
                      ),
                      const SizedBox(height: 10),
                      _LiveSetupTerminal(
                        output: _setupOutput,
                        running: false,
                        controller: _outputScrollController,
                        onCopy: _copyFailureReport,
                        copyTooltip: 'Copy failure report',
                      ),
                      const SizedBox(height: 10),
                      if (_monitoringFailed)
                        Wrap(
                          spacing: 8,
                          runSpacing: 8,
                          children: [
                            FilledButton.icon(
                              onPressed: _busy ? null : _resumeLiveOutput,
                              icon: const Icon(Icons.sync_rounded),
                              label: const Text('Resume live view'),
                            ),
                            OutlinedButton(
                              onPressed: _busy ? null : _retry,
                              child: const Text('Stop & retry'),
                            ),
                          ],
                        )
                      else
                        FilledButton.icon(
                          onPressed: _busy ? null : _retry,
                          icon: const Icon(Icons.refresh_rounded),
                          label: const Text('Stop & retry'),
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

  Widget _stepTile({
    required int n,
    required String title,
    required _StepState state,
    Widget? body,
    bool enabled = true,
  }) {
    final theme = Theme.of(context);
    return Opacity(
      opacity: enabled ? 1 : .45,
      child: Container(
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(
          color: theme.colorScheme.surfaceContainerHighest.withValues(
            alpha: .3,
          ),
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: theme.dividerColor.withValues(alpha: .4)),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
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
                        : ThemeData.estimateBrightnessForColor(background) ==
                              Brightness.dark
                        ? Colors.white
                        : Colors.black87;
                    return CircleAvatar(
                      radius: 11,
                      backgroundColor: background,
                      child: state == _StepState.done
                          ? Icon(
                              Icons.check_rounded,
                              size: 14,
                              color: foreground,
                            )
                          : Text(
                              '$n',
                              style: TextStyle(fontSize: 12, color: foreground),
                            ),
                    );
                  },
                ),
                const SizedBox(width: 10),
                Expanded(child: Text(title, style: theme.textTheme.titleSmall)),
                if (state == _StepState.running)
                  const SizedBox(
                    width: 14,
                    height: 14,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  ),
              ],
            ),
            if (body != null) ...[const SizedBox(height: 10), body],
          ],
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

class _LiveSetupTerminal extends StatelessWidget {
  final String output;
  final bool running;
  final ScrollController controller;
  final VoidCallback? onCopy;
  final String copyTooltip;

  const _LiveSetupTerminal({
    required this.output,
    required this.running,
    required this.controller,
    required this.onCopy,
    this.copyTooltip = 'Copy output',
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final terminalBackground = theme.colorScheme.surfaceContainerLowest;
    final terminalText = theme.colorScheme.onSurfaceVariant;
    final overlay = theme.colorScheme.onSurface;
    final liveAccent = AppTheme.successOf(theme);
    final visibleOutput = output.isEmpty
        ? r'$ Waiting for Termux output...'
        : output;

    return Container(
      height: 220,
      clipBehavior: Clip.antiAlias,
      decoration: BoxDecoration(
        color: terminalBackground,
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: overlay.withValues(alpha: .1)),
      ),
      child: Column(
        children: [
          Container(
            height: 38,
            padding: const EdgeInsets.only(left: 12, right: 4),
            decoration: BoxDecoration(
              color: overlay.withValues(alpha: .035),
              border: Border(
                bottom: BorderSide(color: overlay.withValues(alpha: .08)),
              ),
            ),
            child: Row(
              children: [
                Icon(
                  Icons.circle,
                  size: 8,
                  color: running ? liveAccent : overlay.withValues(alpha: .38),
                ),
                const SizedBox(width: 8),
                Text(
                  running ? 'LIVE OUTPUT' : 'LAST OUTPUT',
                  style: TextStyle(
                    color: overlay.withValues(alpha: .6),
                    fontFamily: 'AppMono',
                    fontSize: 10,
                    fontWeight: FontWeight.w700,
                    letterSpacing: 1.1,
                  ),
                ),
                const Spacer(),
                IconButton(
                  onPressed: onCopy,
                  tooltip: copyTooltip,
                  icon: const Icon(Icons.copy_rounded, size: 16),
                  color: overlay.withValues(alpha: .7),
                  disabledColor: overlay.withValues(alpha: .24),
                  visualDensity: VisualDensity.compact,
                ),
              ],
            ),
          ),
          Expanded(
            child: Scrollbar(
              controller: controller,
              thumbVisibility: true,
              child: SingleChildScrollView(
                controller: controller,
                padding: const EdgeInsets.all(12),
                child: SizedBox(
                  width: double.infinity,
                  child: SelectableText(
                    visibleOutput,
                    style: TextStyle(
                      color: terminalText,
                      fontFamily: 'AppMono',
                      fontSize: 11,
                      height: 1.45,
                    ),
                  ),
                ),
              ),
            ),
          ),
        ],
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
          fontFamily: 'AppMono',
          fontSize: 11,
        ),
      ),
    );
  }
}
