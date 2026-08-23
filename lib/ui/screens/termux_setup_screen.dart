import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../api/opencode_api.dart';
import '../../state/connection.dart';
import '../../state/profiles.dart';
import '../../termux/bridge.dart';

/// Automated on-device setup: the app drives Termux end-to-end.
/// User taps: (1) get Termux if missing, (2) paste one unlock line,
/// everything else — install, server start, connect — is automatic.
class TermuxSetupScreen extends ConsumerStatefulWidget {
  const TermuxSetupScreen({super.key});

  @override
  ConsumerState<TermuxSetupScreen> createState() => _TermuxSetupScreenState();
}

enum _Phase { checking, needTermux, needUnlock, ready, installing, starting, connected, failed }

class _TermuxSetupScreenState extends ConsumerState<TermuxSetupScreen> {
  _Phase _phase = _Phase.checking;
  bool _busy = false;
  String? _error;
  int _pollSeconds = 0;
  Timer? _poll;

  static const port = 4096;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _refresh());
  }

  Future<void> _refresh() async {
    final installed = await TermuxBridge.isInstalled();
    if (!mounted) return;
    setState(() {
      _phase = installed ? _phaseAfterInstall() : _Phase.needTermux;
    });
  }

  /// We cannot observe Termux's properties file directly; after the user
  /// confirms the unlock paste we optimistically move to ready and let the
  /// install step surface any remaining problem.
  _Phase _phaseAfterInstall() =>
      (_phase == _Phase.needUnlock || _phase == _Phase.checking)
          ? _Phase.needUnlock
          : _phase;

  // ----- actions -----

  Future<void> _getTermux() async {
    const url = 'https://f-droid.org/en/packages/com.termux/';
    final uri = Uri.tryParse(url);
    if (uri != null) await launchUrl(uri, mode: LaunchMode.externalApplication);
  }

  Future<void> _openTermuxAndCopy() async {
    await Clipboard.setData(const ClipboardData(text: TermuxBridge.unlockCommand));
    await TermuxBridge.openTermux();
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
        content: Text('Paste into Termux and press Enter, then come back.'),
        duration: Duration(seconds: 5)));
  }

  Future<void> _confirmUnlocked() async {
    setState(() => _phase = _Phase.ready);
  }

  Future<void> _installAndStart() async {
    setState(() {
      _busy = true;
      _error = null;
      _phase = _Phase.installing;
    });
    try {
      // Foreground session: the whole install runs visibly inside Termux.
      await TermuxBridge.run(TermuxBridge.installAndServeScript(port: port),
          background: false);
      await Future<void>.delayed(const Duration(milliseconds: 400));
      await TermuxBridge.openTermux();
      if (!mounted) return;
      setState(() {
        _phase = _Phase.starting;
        _pollSeconds = 0;
      });
      _startPolling();
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _busy = false;
        _phase = _Phase.failed;
        _error =
            '$e\n\nIf you have not completed the unlock step yet, go back to step 2.';
      });
    }
  }

  void _startPolling() {
    _poll?.cancel();
    _poll = Timer.periodic(const Duration(seconds: 3), (t) async {
      _pollSeconds = t.tick * 3;
      try {
        final api = OpenCodeApi(baseUrl: 'http://127.0.0.1:$port');
        final health = await api.health().timeout(const Duration(seconds: 2));
        if (health.healthy == true) {
          t.cancel();
          if (!mounted) return;
          await _finishConnect();
          return;
        }
      } catch (_) {/* not up yet */}
      if (!mounted) return;
      setState(() {});
      // First run pulls an Ubuntu chroot (~400MB): allow up to 15 minutes.
      if (_pollSeconds >= 900 && mounted) {
        t.cancel();
        setState(() {
          _busy = false;
          _phase = _Phase.failed;
          _error =
              'The server did not come up within 15 minutes.\n'
              'Open the live log to see what happened inside Termux '
              '(~/.oc/install.log and ~/.oc/server.log).';
        });
      }
    });
  }

  Future<void> _openLiveLog() async {
    try {
      await TermuxBridge.run(TermuxBridge.logScript(port: port),
          background: false);
      // Give the service a beat to register the session before we foreground
      // the Termux activity.
      await Future<void>.delayed(const Duration(milliseconds: 400));
    } catch (_) {}
    await TermuxBridge.openTermux();
  }

  Future<void> _finishConnect() async {
    final bootstrap = ref.read(bootstrapProvider).value!;
    final store = bootstrap.store;
    final url = 'http://127.0.0.1:$port';
    var profile = store.profiles.where((p) => p.baseUrl == url).firstOrNull;
    profile ??= ServerProfile(
      id: DateTime.now().microsecondsSinceEpoch.toString(),
      name: 'This device (Termux)',
      baseUrl: url,
    );
    await store.upsert(profile);
    await store.load();
    await ref.read(connProvider).connect(profile);
    if (!mounted) return;
    final conn = ref.read(connProvider);
    if (conn.api == null) {
      setState(() {
        _busy = false;
        _phase = _Phase.failed;
        _error = conn.lastError ?? 'Server is up but connection failed.';
      });
      return;
    }
    setState(() => _phase = _Phase.connected);
    Navigator.of(context).pushNamedAndRemoveUntil('/home', (_) => false);
  }

  Future<void> _retryFromScratch() async {
    try {
      await TermuxBridge.run(TermuxBridge.stopScript(port: port));
    } catch (_) {}
    if (mounted) {
      setState(() => _phase = _Phase.ready);
      await _installAndStart();
    }
  }

  // ----- UI -----

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return PopScope(
      canPop: _phase != _Phase.installing && _phase != _Phase.starting || true,
      child: Scaffold(
        appBar: AppBar(title: const Text('On-device setup')),
        body: Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 560),
            child: ListView(
              padding: const EdgeInsets.all(20),
              shrinkWrap: true,
              children: [
                Row(children: [
                  Icon(Icons.smartphone_rounded, color: theme.colorScheme.primary),
                  const SizedBox(width: 8),
                  Text('Run opencode on this phone',
                      style: theme.textTheme.titleMedium),
                ]),
                const SizedBox(height: 6),
                Text(
                    'OpenCode will install and run inside Termux automatically. '
                    'Two quick taps from you; the app does the rest.',
                    style: theme.textTheme.bodySmall!
                        .copyWith(color: theme.hintColor)),
                const SizedBox(height: 20),

                _stepTile(
                  n: 1,
                  title: 'Get Termux',
                  state: _phaseState(_phase.index >= _Phase.needUnlock.index ||
                      _phase != _Phase.needTermux),
                  body: _phase == _Phase.needTermux
                      ? Column(crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            const Text(
                                'Install the F-Droid build of Termux (the Play Store '
                                'build is outdated), then return here.'),
                            const SizedBox(height: 10),
                            Wrap(spacing: 8, children: [
                              FilledButton.icon(
                                  onPressed: _getTermux,
                                  icon: const Icon(Icons.download_rounded),
                                  label: const Text('Download page')),
                              OutlinedButton(
                                  onPressed: _refresh,
                                  child: const Text('I installed it')),
                            ]),
                          ],
                        )
                      : null,
                ),
                const SizedBox(height: 8),

                _stepTile(
                  n: 2,
                  title: 'Unlock the bridge',
                  state: _phaseState(_phase.index > _Phase.needUnlock.index),
                  enabled: _phase != _Phase.needTermux,
                  body: (_phase == _Phase.needUnlock || _phase == _Phase.checking)
                      ? Column(crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            const Text(
                                'Android requires a one-time consent inside Termux. '
                                'Tap below: it copies the unlock line and opens '
                                'Termux — paste it and press Enter.'),
                            const SizedBox(height: 10),
                            const CmdPreview(),
                            const SizedBox(height: 10),
                            Wrap(spacing: 8, children: [
                              FilledButton.icon(
                                  onPressed: _openTermuxAndCopy,
                                  icon: const Icon(Icons.open_in_new_rounded),
                                  label: const Text('Copy & open Termux')),
                              OutlinedButton(
                                  onPressed: _confirmUnlocked,
                                  child: const Text('Done, continue')),
                            ]),
                          ],
                        )
                      : null,
                ),
                const SizedBox(height: 8),

                _stepTile(
                  n: 3,
                  title: 'Install opencode & start server',
                  state: switch (_phase) {
                    _Phase.installing => _StepState.running,
                    _Phase.starting => _StepState.running,
                    _Phase.connected => _StepState.done,
                    _Phase.failed => _StepState.error,
                    _ => _StepState.idle,
                  },
                  enabled: _phase.index > _Phase.needUnlock.index,
                  body: switch (_phase) {
                    _Phase.ready => Column(crossAxisAlignment:
                        CrossAxisAlignment.start, children: [
                        const Text(
                            'Tries the fast native build first (~30 s), '
                            'falls back to an Ubuntu chroot only if needed. '
                            'Progress runs visibly in Termux (opens it for you) '
                            'while the app watches for the server.'),
                        const SizedBox(height: 10),
                        FilledButton.icon(
                            onPressed: _busy ? null : _installAndStart,
                            icon: const Icon(Icons.rocket_launch_rounded),
                            label: const Text('Install & start')),
                      ]),
                    _Phase.installing => _ProgressLine(
                        text:
                            'Installing Ubuntu + opencode inside Termux… ($_pollSeconds s)'),
                    _Phase.starting => _ProgressLine(
                        text:
                            'Starting server on 127.0.0.1:$port… ($_pollSeconds s)'),
                    _Phase.connected => const Text('Connected!'),
                    _Phase.failed => Column(crossAxisAlignment:
                        CrossAxisAlignment.start, children: [
                        Text(_error ?? 'Something went wrong.',
                            style: TextStyle(color: theme.colorScheme.error)),
                        const SizedBox(height: 10),
                        Wrap(spacing: 8, runSpacing: 8, children: [
                          FilledButton.icon(
                              onPressed: _busy ? null : _retryFromScratch,
                              icon: const Icon(Icons.refresh_rounded),
                              label: const Text('Retry')),
                          OutlinedButton.icon(
                              onPressed: _openLiveLog,
                              icon: const Icon(Icons.receipt_long_rounded),
                              label: const Text('Live log in Termux')),
                          OutlinedButton(
                              onPressed: () =>
                                  setState(() => _phase = _Phase.needUnlock),
                              child: const Text('Back to unlock step')),
                        ]),
                      ]),
                    _ => null,
                  },
                ),
                if (_phase == _Phase.installing || _phase == _Phase.starting) ...[
                  const SizedBox(height: 8),
                  Row(children: [
                    Expanded(
                      child: Text(
                        'You can watch progress: tap “Live log in Termux” below.',
                        style: theme.textTheme.bodySmall!
                            .copyWith(color: theme.hintColor),
                      ),
                    ),
                  ]),
                  const SizedBox(height: 6),
                  OutlinedButton.icon(
                    onPressed: _openLiveLog,
                    icon: const Icon(Icons.receipt_long_rounded, size: 18),
                    label: const Text('Live log in Termux'),
                  ),
                ],
              ],
            ),
          ),
        ),
      ),
    );
  }

  _StepState _phaseState(bool done) => done ? _StepState.done : _StepState.idle;

  Widget _stepTile({
    required int n,
    required String title,
    required _StepState state,
    Widget? body,
    bool enabled = true,
  }) {
    final theme = Theme.of(context);
    final active = enabled;
    return Opacity(
      opacity: active ? 1 : .45,
      child: Container(
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(
          color: theme.colorScheme.surfaceContainerHighest.withValues(alpha: .3),
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: theme.dividerColor.withValues(alpha: .4)),
        ),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Row(children: [
            CircleAvatar(
              radius: 11,
              backgroundColor: switch (state) {
                _StepState.done => Colors.green.shade400,
                _StepState.running => theme.colorScheme.primary,
                _StepState.error => theme.colorScheme.error,
                _StepState.idle => theme.hintColor.withValues(alpha: .4),
              },
              child: switch (state) {
                _StepState.done => const Icon(Icons.check_rounded,
                    size: 14, color: Colors.white),
                _ => Text('$n',
                    style: const TextStyle(fontSize: 12, color: Colors.white)),
              },
            ),
            const SizedBox(width: 10),
            Expanded(child: Text(title, style: theme.textTheme.titleSmall)),
            if (state == _StepState.running)
              const SizedBox(
                  width: 14,
                  height: 14,
                  child: CircularProgressIndicator(strokeWidth: 2)),
          ]),
          if (body != null) ...[
            const SizedBox(height: 10),
            body,
          ],
        ]),
      ),
    );
  }
}

enum _StepState { idle, running, done, error }

class _ProgressLine extends StatelessWidget {
  final String text;
  const _ProgressLine({required this.text});

  @override
  Widget build(BuildContext context) {
    return Row(children: [
      const SizedBox(
          width: 14,
          height: 14,
          child: CircularProgressIndicator(strokeWidth: 2)),
      const SizedBox(width: 10),
      Expanded(child: Text(text)),
    ]);
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
        style: theme.textTheme.bodySmall!.copyWith(fontFamily: 'monospace', fontSize: 11),
      ),
    );
  }
}
