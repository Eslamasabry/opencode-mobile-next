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
  static const port = 4096;
  static const localUrl = 'http://127.0.0.1:$port';

  _Phase _phase = _Phase.checking;
  TermuxSetupStatus? _status;
  Timer? _poll;
  bool _busy = false;
  bool _refreshing = false;
  bool _polling = false;
  int _elapsedSeconds = 0;
  String? _error;
  String? _lastLaunchOutput;

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
    setState(() {
      _busy = true;
      _error = null;
      _elapsedSeconds = 0;
    });
    try {
      await TermuxBridge.verifyBridge();
      final profile = await _ensureLocalProfile();
      final command = TermuxBridge.installAndServeScript(
        port: port,
        password: profile.password,
      );
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

  void _startPolling() {
    _poll?.cancel();
    _poll = Timer.periodic(const Duration(seconds: 3), (_) {
      _elapsedSeconds += 3;
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
      final status = await TermuxBridge.status();
      if (!mounted) return;
      _status = status;
      if (status.isRunning) {
        setState(() => _phase = _Phase.installing);
        if (_poll == null) _startPolling();
        return;
      }
      _stopPolling();
      if (status.isFailed) {
        setState(() {
          _phase = _Phase.failed;
          _error = status.message;
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
      _stopPolling();
      setState(() {
        _phase = _Phase.failed;
        _error = error.message;
      });
    } finally {
      _polling = false;
    }
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
    Navigator.of(context).pushNamedAndRemoveUntil('/home', (_) => false);
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

  Future<void> _showDiagnostics() async {
    if (_busy) return;
    setState(() => _busy = true);
    String diagnostics;
    try {
      diagnostics = await TermuxBridge.diagnostics();
    } on TermuxBridgeException catch (error) {
      diagnostics = error.message;
    } finally {
      if (mounted) setState(() => _busy = false);
    }
    final output = _lastLaunchOutput;
    if (output != null && output.isNotEmpty) {
      diagnostics = '===== Last launcher result =====\n$output\n$diagnostics';
    }
    if (!mounted) return;
    await showDialog<void>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Termux diagnostics'),
        content: SizedBox(
          width: 620,
          child: SingleChildScrollView(
            child: SelectableText(
              diagnostics,
              style: const TextStyle(fontFamily: 'monospace', fontSize: 12),
            ),
          ),
        ),
        actions: [
          TextButton(
            onPressed: () =>
                Clipboard.setData(ClipboardData(text: diagnostics)),
            child: const Text('Copy'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Close'),
          ),
        ],
      ),
    );
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
                state: _phase == _Phase.needTermux
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
                title: 'Install OpenCode & start server',
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
                      OutlinedButton.icon(
                        onPressed: _busy ? null : _showDiagnostics,
                        icon: const Icon(Icons.receipt_long_rounded, size: 18),
                        label: const Text('Diagnostics'),
                      ),
                    ],
                  ),
                  _Phase.connected => const Text('Connected.'),
                  _Phase.failed => Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        _error ?? 'Setup failed.',
                        style: TextStyle(color: theme.colorScheme.error),
                      ),
                      const SizedBox(height: 10),
                      Wrap(
                        spacing: 8,
                        runSpacing: 8,
                        children: [
                          FilledButton.icon(
                            onPressed: _busy ? null : _retry,
                            icon: const Icon(Icons.refresh_rounded),
                            label: const Text('Stop & retry'),
                          ),
                          OutlinedButton.icon(
                            onPressed: _busy ? null : _showDiagnostics,
                            icon: const Icon(Icons.receipt_long_rounded),
                            label: const Text('Diagnostics'),
                          ),
                        ],
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
                CircleAvatar(
                  radius: 11,
                  backgroundColor: switch (state) {
                    _StepState.done => Colors.green.shade400,
                    _StepState.running => theme.colorScheme.primary,
                    _StepState.error => theme.colorScheme.error,
                    _StepState.idle => theme.hintColor.withValues(alpha: .4),
                  },
                  child: state == _StepState.done
                      ? const Icon(
                          Icons.check_rounded,
                          size: 14,
                          color: Colors.white,
                        )
                      : Text(
                          '$n',
                          style: const TextStyle(
                            fontSize: 12,
                            color: Colors.white,
                          ),
                        ),
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
          fontFamily: 'monospace',
          fontSize: 11,
        ),
      ),
    );
  }
}
