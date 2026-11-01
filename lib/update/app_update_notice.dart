import 'dart:async';

import 'package:flutter/material.dart';

import '../l10n/app_localizations.dart';
import '../ui/app_iconography.dart';
import '../ui/kit/kit_buttons.dart';
import '../ui/kit/kit_status_line.dart';
import 'app_update_controller.dart';
import 'shorebird_update_notice.dart' show UpdateStatusScope;

/// The app's own update in the one status line, and the clock that asks for
/// news: a check at start-up and on every resume (the controller asks
/// GitHub at most once every six hours).
///
/// "Version 2.2.0 is ready" offers Update and Not now; Not now hides only
/// the offer, for that version, until the next app start. Work the person
/// started (download, check, install) keeps its line, and a downloaded
/// code-push patch says it takes effect on the next start. A release wins
/// over the patch (the controller's phase decides).
class AppUpdateNotice extends StatefulWidget {
  const AppUpdateNotice({
    super.key,
    required this.controller,
    required this.child,
  });

  final AppUpdateController controller;
  final Widget child;

  @override
  State<AppUpdateNotice> createState() => _AppUpdateNoticeState();
}

class _AppUpdateNoticeState extends State<AppUpdateNotice>
    with WidgetsBindingObserver {
  bool _patchHidden = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    widget.controller.addListener(_changed);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _safely(widget.controller.start());
    });
  }

  @override
  void didUpdateWidget(AppUpdateNotice old) {
    super.didUpdateWidget(old);
    if (old.controller != widget.controller) {
      old.controller.removeListener(_changed);
      widget.controller.addListener(_changed);
    }
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      _safely(widget.controller.onResumed());
    }
  }

  /// An update check must never take the app down.
  void _safely(Future<void> work) => unawaited(
    work.catchError((Object e) => debugPrint('App update check failed.')),
  );

  void _changed() {
    if (mounted) setState(() {});
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    widget.controller.removeListener(_changed);
    super.dispose();
  }

  KitStatus? _status(BuildContext context) {
    final c = widget.controller;
    if (!c.noticeVisible) return null;
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final version = c.release?.version ?? '';
    switch (c.phase) {
      case AppUpdatePhase.available:
        return KitStatus(
          kind: KitStatusKind.update,
          id: 'update:release',
          icon: AppIconography.download,
          message: l10n.appUpdateReadyTitle(version),
          action: KitAction(
            label: l10n.appUpdateUpdate,
            onPressed: () => unawaited(c.updateNow()),
          ),
          dismissTooltip: l10n.appUpdateNotNow,
          onDismiss: c.notNow,
        );
      case AppUpdatePhase.downloading:
        final p = c.progress;
        return KitStatus(
          kind: KitStatusKind.update,
          id: 'update:release',
          icon: AppIconography.download,
          message: l10n.appUpdateDownloadingTitle(version),
          supporting: p == null
              ? null
              : l10n.appUpdatePercent((p * 100).floor().toString()),
          action: KitAction(label: l10n.appUpdateCancel, onPressed: c.cancel),
        );
      case AppUpdatePhase.verifying:
        return KitStatus(
          kind: KitStatusKind.update,
          id: 'update:release',
          icon: AppIconography.download,
          message: l10n.appUpdateVerifying,
        );
      case AppUpdatePhase.installing:
        return KitStatus(
          kind: KitStatusKind.update,
          id: 'update:release',
          icon: AppIconography.download,
          message: l10n.appUpdateInstalling,
        );
      case AppUpdatePhase.readyToInstall:
        return KitStatus(
          kind: KitStatusKind.update,
          id: 'update:release',
          icon: AppIconography.download,
          message: l10n.appUpdateInstallReadyTitle(version),
          supporting: c.needsPermission ? l10n.appUpdatePermissionNeeded : null,
          action: KitAction(
            label: l10n.appUpdateInstall,
            onPressed: () => unawaited(c.updateNow()),
          ),
          dismissTooltip: l10n.appUpdateNotNow,
          onDismiss: c.notNow,
        );
      case AppUpdatePhase.restartToUpdate:
        if (_patchHidden) return null;
        return KitStatus(
          kind: KitStatusKind.update,
          id: 'update:app',
          icon: AppIconography.download,
          message: l10n.shorebirdUpdateReadyTitle,
          supporting: l10n.shorebirdUpdateReadyBody,
          onDismiss: () {
            if (mounted) setState(() => _patchHidden = true);
          },
        );
      case AppUpdatePhase.none ||
          AppUpdatePhase.checking ||
          AppUpdatePhase.failed:
        return null;
    }
  }

  @override
  Widget build(BuildContext context) =>
      UpdateStatusScope(status: _status(context), child: widget.child);
}
