import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:shorebird_code_push/shorebird_code_push.dart';

import '../l10n/app_localizations.dart';
import '../ui/app_iconography.dart';
import '../ui/kit/kit_status_line.dart';
import '../ui/kit/kit_status_slot.dart';
import 'app_update_controller.dart';

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

/// The update service for a build Shorebird does not patch.
///
/// Desktop bundles are released as GitHub artifacts, not Shorebird patches,
/// so constructing a `ShorebirdUpdater` there only produces an updater that
/// reports itself unavailable after doing FFI work. This says so up front,
/// and keeps the notice from ever entering its check loop.
class UnavailableAppUpdateService implements AppUpdateService {
  const UnavailableAppUpdateService();

  @override
  bool get isAvailable => false;

  @override
  Future<AppUpdateState> checkForUpdate() async => AppUpdateState.unavailable;

  @override
  Future<void> downloadUpdate() async {}
}

/// Adds one app-wide [status] to the conditions every screen's status line
/// reads (`KitStatusScope`, kit_status_slot.dart), keeping the conditions of
/// any scope above it.
///
/// The update notices sit above the Navigator (in `MaterialApp.builder`), so
/// a condition they raise reaches every `KitScreen`'s status slot, where
/// `KitStatus.highest` keeps it below connection, heat and a screen's own
/// work line (STATE-19: an update is the lowest condition). A scope that
/// main.dart provides above the notices keeps working: its conditions come
/// first.
class UpdateStatusScope extends StatefulWidget {
  const UpdateStatusScope({
    super.key,
    required this.status,
    required this.child,
  });

  /// Null: nothing to add.
  final KitStatus? status;
  final Widget child;

  @override
  State<UpdateStatusScope> createState() => _UpdateStatusScopeState();
}

class _UpdateStatusScopeState extends State<UpdateStatusScope> {
  final _conditions = ValueNotifier<List<KitStatus>>(const []);
  ValueListenable<List<KitStatus>>? _outer;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final outer = KitStatusScope.of(context);
    if (!identical(outer, _outer)) {
      _outer?.removeListener(_merge);
      _outer = outer..addListener(_merge);
      _merge();
    }
  }

  @override
  void didUpdateWidget(UpdateStatusScope oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (!_sameStatus(oldWidget.status, widget.status)) _merge();
  }

  void _merge() {
    _conditions.value = [...?_outer?.value, ?widget.status];
  }

  @override
  void dispose() {
    _outer?.removeListener(_merge);
    _conditions.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) =>
      KitStatusScope(conditions: _conditions, child: widget.child);
}

/// Same identity and words: a rebuild that changes nothing the person sees
/// does not redraw every slot.
bool _sameStatus(KitStatus? a, KitStatus? b) =>
    identical(a, b) ||
    (a != null &&
        b != null &&
        a.id == b.id &&
        a.message == b.message &&
        a.supporting == b.supporting &&
        a.action?.label == b.action?.label);

/// Receives a code-push update without a word and, once it is ready, says
/// so in the app's one status line: "App update ready", "It takes effect
/// when you fully close the app and open it again." (map page
/// `shorebird-update-notice`, owner verdict Fix: silent download, one
/// status line that never covers the pinned action, no tool name).
///
/// States: nothing shown (checking, downloading, up to date, unavailable,
/// and a failed download, which tries again on a later resume), ready (the
/// status line). Its Dismiss hides the line for the rest of the run and
/// changes nothing real: the update still applies on the next cold start.
class ShorebirdUpdateNotice extends StatefulWidget {
  const ShorebirdUpdateNotice({
    super.key,
    required this.service,
    required this.child,
    this.messengerKey,
    this.currentProfileId,
    this.allowsAutomaticUpdate,
    this.onDownloaded,
    this.updates,
  });

  final AppUpdateService service;

  /// The app-wide update holder. When given, a downloaded patch is reported
  /// to it and the status line is drawn from there (`AppUpdateNotice`), so a
  /// newer release wins over the patch; this widget still downloads.
  final AppUpdateController? updates;
  final Widget child;

  /// The current saved server owns this app-wide automatic act. Returning
  /// null keeps automatic checks unavailable until a server is selected.
  final String? Function()? currentProfileId;

  /// Reads that server's persisted `applyCodePush` automation choice at each
  /// execution boundary. Missing policy wiring does not grant permission.
  final bool Function(String profileId)? allowsAutomaticUpdate;

  /// Reports a completed download, never a check or an already-ready patch.
  /// The profile is captured before downloading so switching servers cannot
  /// move the act into another server's history. A failed history save must
  /// not trigger another download; the composition owner handles that state.
  final Future<void> Function({
    required String profileId,
    required String eventId,
    required DateTime at,
  })?
  onDownloaded;

  /// Not used: the notice speaks through the status line, never a
  /// snackbar. Kept so existing callers compile (R11).
  final GlobalKey<ScaffoldMessengerState>? messengerKey;

  @override
  State<ShorebirdUpdateNotice> createState() => _ShorebirdUpdateNoticeState();
}

class _ShorebirdUpdateNoticeState extends State<ShorebirdUpdateNotice>
    with WidgetsBindingObserver {
  bool _checking = false;
  bool _ready = false;
  bool _hidden = false;
  DateTime? _lastCheck;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    if (_allowedProfile() != null) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        unawaited(_checkForUpdate());
      });
    }
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed && _allowedProfile() != null) {
      unawaited(_checkForUpdate());
    }
  }

  String? _allowedProfile() {
    if (!mounted) return null;
    final profileId = widget.currentProfileId?.call();
    if (profileId == null ||
        widget.allowsAutomaticUpdate?.call(profileId) != true) {
      return null;
    }
    return profileId;
  }

  Future<void> _checkForUpdate() async {
    if (_allowedProfile() == null ||
        !widget.service.isAvailable ||
        _checking ||
        _ready) {
      return;
    }
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
          // Consent and profile may have changed during the check. Capture
          // ownership and the recorder before the download leaves the app.
          final profileId = _allowedProfile();
          if (profileId == null) return;
          final onDownloaded = widget.onDownloaded;
          final eventId =
              'shorebird-update:${DateTime.now().microsecondsSinceEpoch}';
          await widget.service.downloadUpdate();
          _showReady();
          await onDownloaded?.call(
            profileId: profileId,
            eventId: eventId,
            at: DateTime.now().toUtc(),
          );
      }
    } on Exception {
      // Never claims readiness after a failed download; the next resume
      // after the throttle tries again.
      debugPrint('App update could not be completed.');
    } finally {
      _checking = false;
    }
  }

  void _showReady() {
    if (!mounted || _ready) return;
    widget.updates?.patchReady();
    setState(() => _ready = true);
  }

  KitStatus? _status(BuildContext context) {
    if (!_ready || _hidden || widget.updates != null) return null;
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    return KitStatus(
      kind: KitStatusKind.update,
      id: 'update:app',
      icon: AppIconography.download,
      message: l10n.shorebirdUpdateReadyTitle,
      supporting: l10n.shorebirdUpdateReadyBody,
      onDismiss: () {
        if (mounted) setState(() => _hidden = true);
      },
    );
  }

  @override
  Widget build(BuildContext context) =>
      UpdateStatusScope(status: _status(context), child: widget.child);

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }
}
