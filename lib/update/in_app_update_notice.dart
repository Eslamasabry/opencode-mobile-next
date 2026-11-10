import 'dart:async';

import 'package:flutter/foundation.dart' show kReleaseMode;
import 'package:flutter/widgets.dart';

import '../platform/platform_capabilities.dart';
import 'app_update_sheet.dart';
import 'in_app_update_service.dart';

/// Coordinator that performs the update check and presents the prompt.
class InAppUpdateCoordinator {
  static bool _checking = false;
  static bool _sheetOpen = false;

  /// Checks for updates on startup or resume, and shows the prompt sheet if
  /// a newer release is found and not dismissed in this session.
  static Future<void> checkOnStartup({
    required BuildContext context,
    InAppUpdateService? service,
  }) async {
    final updateService = service ?? InAppUpdateService.instance;
    if (!updateService.isAvailable || _checking || _sheetOpen) return;

    _checking = true;
    try {
      final release = await updateService.checkForNewRelease();
      if (release == null || !context.mounted) return;

      if (updateService.isDismissedForSession(release.tag)) {
        return;
      }

      _sheetOpen = true;
      try {
        await showInAppUpdateSheet(
          context,
          release: release,
          service: updateService,
        );
      } finally {
        _sheetOpen = false;
      }
    } catch (e) {
      debugPrint('Startup update check failed: $e');
    } finally {
      _checking = false;
    }
  }

  /// Explicit check triggered from Settings / About screen.
  static Future<AppReleaseInfo?> checkManually({
    required BuildContext context,
    InAppUpdateService? service,
  }) async {
    final updateService = service ?? InAppUpdateService.instance;
    final release = await updateService.checkForNewRelease(ignoreCache: true);
    if (release != null && context.mounted) {
      _sheetOpen = true;
      try {
        await showInAppUpdateSheet(
          context,
          release: release,
          service: updateService,
        );
      } finally {
        _sheetOpen = false;
      }
    }
    return release;
  }
}

/// Lifecycle observer that runs the update check on app opening (start and resume)
/// on Android.
class InAppUpdateNotice extends StatefulWidget {
  const InAppUpdateNotice({
    super.key,
    required this.child,
    this.navigatorKey,
    this.service,
    this.enabledOverride,
  });

  final Widget child;
  final GlobalKey<NavigatorState>? navigatorKey;
  final InAppUpdateService? service;
  final bool? enabledOverride;

  @override
  State<InAppUpdateNotice> createState() => _InAppUpdateNoticeState();
}

class _InAppUpdateNoticeState extends State<InAppUpdateNotice>
    with WidgetsBindingObserver {
  // Release builds only: debug APKs cannot run on the pinned engine, and a
  // widget test that mounts the app must not start a GitHub check (its
  // delayed timer outlived every such test, #126).
  bool get _enabled =>
      widget.enabledOverride ??
      (kReleaseMode && platformCapabilities.isAndroid);

  Timer? _delay;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _triggerCheck();
    });
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      _triggerCheck();
    }
  }

  void _triggerCheck() {
    if (!_enabled) return;
    // Delay slightly to give the UI a moment to finish launching/routing
    _delay?.cancel();
    _delay = Timer(const Duration(milliseconds: 1200), () {
      if (!mounted) return;
      final targetContext = widget.navigatorKey?.currentContext ?? context;
      if (targetContext.mounted) {
        unawaited(
          InAppUpdateCoordinator.checkOnStartup(
            context: targetContext,
            service: widget.service,
          ),
        );
      }
    });
  }

  @override
  void dispose() {
    _delay?.cancel();
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => widget.child;
}
