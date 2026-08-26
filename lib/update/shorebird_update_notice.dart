import 'dart:async';

import 'package:flutter/material.dart';
import 'package:shorebird_code_push/shorebird_code_push.dart';

enum AppUpdateState { current, available, restartRequired, unavailable }

abstract interface class AppUpdateService {
  bool get isAvailable;

  Future<AppUpdateState> checkForUpdate();

  Future<void> downloadUpdate();
}

class ShorebirdAppUpdateService implements AppUpdateService {
  ShorebirdAppUpdateService({ShorebirdUpdater? updater})
    : _updater = updater ?? ShorebirdUpdater();

  final ShorebirdUpdater _updater;

  @override
  bool get isAvailable => _updater.isAvailable;

  @override
  Future<AppUpdateState> checkForUpdate() async {
    return switch (await _updater.checkForUpdate()) {
      UpdateStatus.upToDate => AppUpdateState.current,
      UpdateStatus.outdated => AppUpdateState.available,
      UpdateStatus.restartRequired => AppUpdateState.restartRequired,
      UpdateStatus.unavailable => AppUpdateState.unavailable,
    };
  }

  @override
  Future<void> downloadUpdate() => _updater.update();
}

class ShorebirdUpdateNotice extends StatefulWidget {
  const ShorebirdUpdateNotice({
    super.key,
    required this.service,
    required this.messengerKey,
    required this.child,
  });

  final AppUpdateService service;
  final GlobalKey<ScaffoldMessengerState> messengerKey;
  final Widget child;

  @override
  State<ShorebirdUpdateNotice> createState() => _ShorebirdUpdateNoticeState();
}

class _ShorebirdUpdateNoticeState extends State<ShorebirdUpdateNotice>
    with WidgetsBindingObserver {
  bool _checking = false;
  bool _readyNoticeShown = false;
  DateTime? _lastCheck;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      unawaited(_checkForUpdate());
    });
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      unawaited(_checkForUpdate());
    }
  }

  Future<void> _checkForUpdate() async {
    if (!widget.service.isAvailable || _checking || _readyNoticeShown) return;
    final now = DateTime.now();
    if (_lastCheck case final previous?
        when now.difference(previous) < const Duration(minutes: 15)) {
      return;
    }
    _checking = true;
    _lastCheck = now;
    try {
      switch (await widget.service.checkForUpdate()) {
        case AppUpdateState.current || AppUpdateState.unavailable:
          break;
        case AppUpdateState.restartRequired:
          _showReady();
        case AppUpdateState.available:
          _showDownloading();
          await widget.service.downloadUpdate();
          if (mounted) _showReady();
      }
    } on Exception catch (error) {
      widget.messengerKey.currentState?.removeCurrentSnackBar(
        reason: SnackBarClosedReason.remove,
      );
      debugPrint('Shorebird update check failed: $error');
    } finally {
      _checking = false;
    }
  }

  void _showDownloading() {
    final messenger = widget.messengerKey.currentState;
    if (messenger == null) return;
    messenger.removeCurrentSnackBar(reason: SnackBarClosedReason.remove);
    messenger.showSnackBar(
      const SnackBar(
        duration: Duration(days: 1),
        content: Row(
          children: [
            SizedBox.square(
              dimension: 18,
              child: CircularProgressIndicator(strokeWidth: 2),
            ),
            SizedBox(width: 12),
            Expanded(child: Text('Receiving Shorebird update…')),
          ],
        ),
      ),
    );
  }

  void _showReady() {
    if (_readyNoticeShown) return;
    _readyNoticeShown = true;
    final messenger = widget.messengerKey.currentState;
    if (messenger == null) return;
    messenger.removeCurrentSnackBar(reason: SnackBarClosedReason.remove);
    messenger.showSnackBar(
      SnackBar(
        duration: const Duration(seconds: 10),
        content: const Text('Shorebird update ready — restart to apply.'),
        action: SnackBarAction(label: 'Got it', onPressed: () {}),
      ),
    );
  }

  @override
  Widget build(BuildContext context) => widget.child;

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }
}
