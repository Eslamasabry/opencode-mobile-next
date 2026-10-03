part of '../main.dart';

/// Renders immediately so a preferences or secure-storage failure can never
/// leave Android showing a blank native window.
class AppBootstrapGate extends StatefulWidget {
  const AppBootstrapGate({
    super.key,
    required this.diagnostics,
    this.loader,
    this.controllerFactory,
    this.resetSavedSignIns,
  });

  final AppDiagnosticsController diagnostics;
  final AppBootstrapLoader? loader;

  /// Supplies controlled transports for startup ordering tests.
  @visibleForTesting
  final ConnectionController Function(
    ProfileStore store,
    AppDiagnosticsController diagnostics,
  )?
  controllerFactory;

  /// Injectable boundary for the confirmed reset; never loads saved metadata.
  final Future<void> Function()? resetSavedSignIns;

  @override
  State<AppBootstrapGate> createState() => _AppBootstrapGateState();
}

class _AppBootstrapGateState extends State<AppBootstrapGate> {
  AppBootstrap? _bootstrap;
  ConnectionController? _controller;
  Object? _error;
  bool _loading = true;
  int _generation = 0;
  bool _resetting = false;
  bool _resetFailed = false;
  bool _resetConfirming = false;
  final Set<Future<void>> _loads = {};

  /// When this attempt to open began: after 8 s the page says it is still
  /// opening and offers Try again (STATE-5).
  DateTime _since = DateTime.now();

  @override
  void initState() {
    super.initState();
    // Every kit error state offers "Report a problem" (P8.3, C26): Report a
    // problem opens with the failure attached, from the very first page on,
    // so even a start that fails can be reported.
    KitReportHook.handler = (context, report) =>
        openReportProblem(context, error: report);
    // Even the synchronous part of preferences/Keystore setup waits until
    // the opening state has painted. Draft/photo/notification prerequisites
    // below still complete before any conversation or connection is exposed.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) unawaited(_load(initial: true));
    });
  }

  Future<void> _load({bool initial = false}) {
    if (_resetting) return Future<void>.value();
    final pending = _runLoad(initial: initial);
    _loads.add(pending);
    unawaited(pending.whenComplete(() => _loads.remove(pending)));
    return pending;
  }

  Future<void> _runLoad({bool initial = false}) async {
    final generation = ++_generation;
    // Initial fields already describe the opening state. Do not schedule a
    // redundant opening-state rebuild merely because work starts post-frame.
    if (!initial) {
      setState(() {
        _loading = true;
        _resetFailed = false;
        _error = null;
        _since = DateTime.now();
      });
    }
    ConnectionController? pendingController;
    try {
      final bootstrap = await PerfTrace.span(
        'app.bootstrap',
        widget.loader ?? AppBootstrap.create,
      );
      if (!mounted || generation != _generation) return;
      final controller =
          widget.controllerFactory?.call(bootstrap.store, widget.diagnostics) ??
          ConnectionController(
            bootstrap.store,
            diagnostics: widget.diagnostics,
          );
      pendingController = controller;
      // Before any conversation reads its draft: the older drafts move into
      // Saved prompts, then a photo the camera handed back after Android
      // stopped the app joins its own conversation's draft (P3.2). Both keep
      // their source on failure and retry on the next start.
      await controller.migrateOlderDrafts();
      if (platformCapabilities.supportsPromptPhotos) {
        await controller.recoverPendingPhoto();
      }
      // Before anything can alert: quiet hours and Wi-Fi only become one
      // shared definition. Idempotent, and a failure leaves the legacy
      // per-server records in charge.
      try {
        await controller.migrateNotificationPreferences();
      } catch (error, stack) {
        widget.diagnostics.record(error, stack, source: 'notify-migration');
      }
      if (!mounted || generation != _generation) {
        controller.dispose();
        return;
      }
      _controller?.dispose();
      pendingController = null;
      setState(() {
        _bootstrap = bootstrap;
        _controller = controller;
        _loading = false;
      });
      WidgetsBinding.instance.addPostFrameCallback(
        (_) => PerfTrace.markOnce('app.shell_frame'),
      );
    } catch (error, stack) {
      pendingController?.dispose();
      widget.diagnostics.record(error, stack, source: 'bootstrap');
      if (!mounted || generation != _generation) return;
      setState(() {
        _error = error;
        _loading = false;
      });
    }
  }

  Future<void> _startFresh(BuildContext context) async {
    if (_resetting || _resetConfirming) return;
    _resetConfirming = true;
    final l10n = AppLocalizations.of(context);
    final confirmed = await showKitConfirm(
      context,
      kind: KitConfirmKind.destructive,
      title: l10n.bootstrapStartFreshTitle,
      body: l10n.bootstrapStartFreshBody,
      confirmLabel: l10n.bootstrapStartFreshConfirm,
      confirmKey: const ValueKey('confirm-start-fresh'),
    );
    _resetConfirming = false;
    if (!confirmed || !mounted) return;
    await _resetSignIns();
  }

  Future<void> _resetSignIns() async {
    if (_resetting || !mounted) return;
    // Invalidate loaders before waiting: no old result can mount a controller
    // or reconnect with the credentials this reset is about to erase.
    ++_generation;
    setState(() {
      _resetting = true;
      _error = null;
    });
    await Future.wait(_loads.toList());
    if (!mounted) return;
    // Phone agents keep their own sign-ins in the built-in Linux; close and
    // drain them before the stored sign-ins they depend on are erased.
    try {
      await _controller?.closePhoneAgentsForSignInReset();
    } catch (error, stack) {
      widget.diagnostics.record(error, stack, source: 'bootstrap-reset');
    }
    if (!mounted) return;
    _controller?.dispose();
    _controller = null;
    _bootstrap = null;
    try {
      await (widget.resetSavedSignIns ?? AppBootstrap.resetSavedSignIns)();
      if (!mounted) return;
      setState(() => _resetting = false);
      await _load();
    } catch (error, stack) {
      widget.diagnostics.record(error, stack, source: 'bootstrap-reset');
      if (!mounted) return;
      setState(() {
        _resetting = false;
        _loading = false;
        _resetFailed = true;
        _error = error;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final bootstrap = _bootstrap;
    final controller = _controller;
    if (bootstrap != null && controller != null) {
      return ProviderScope(
        overrides: [
          bootstrapProvider.overrideWithValue(bootstrap),
          connProvider.overrideWithValue(controller),
        ],
        child: const OcApp(),
      );
    }
    return MaterialApp(
      title: 'OpenCode Mobile',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.light(),
      darkTheme: AppTheme.dark(),
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      home: Builder(
        builder: (context) => _Ground(
          child: KitScreen(
            width: KitScreenWidth.reading,
            body: _bootstrapState(context),
          ),
        ),
      ),
    );
  }

  /// The app opening (map page bootstrap-gate): "Opening…" while the saved
  /// servers are read, then, if that fails, what failed in words with Try
  /// again, Copy details and Report a problem; the reason is under Details.
  Widget _bootstrapState(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    if (_resetting) {
      return KitStateView(
        key: const ValueKey('app-bootstrap-resetting'),
        icon: AppIconography.waiting,
        tone: AppStatusTone.progress,
        title: l10n.bootstrapResettingTitle,
        body: l10n.bootstrapResettingBody,
        progress: const KitProgress.waiting(),
      );
    }
    final retry = KitAction(
      key: const ValueKey('retry-app-bootstrap'),
      label: l10n.commonRetry,
      onPressed: () => unawaited(_resetFailed ? _resetSignIns() : _load()),
    );
    final reset = KitAction(
      key: const ValueKey('start-fresh-app-bootstrap'),
      label: l10n.bootstrapStartFresh,
      onPressed: () => unawaited(_startFresh(context)),
    );
    final error = _error;
    if (_loading || error == null) {
      return KitStateView(
        key: const ValueKey('app-bootstrap-opening'),
        icon: AppIconography.waiting,
        tone: AppStatusTone.progress,
        title: l10n.bootstrapOpeningTitle,
        body: l10n.bootstrapOpeningBody,
        progress: const KitProgress.waiting(),
        since: _since,
        onSlow: [retry],
      );
    }
    return KitStateView.error(
      key: const ValueKey('app-bootstrap-failed'),
      title: _resetFailed
          ? l10n.bootstrapResetFailedTitle
          : l10n.bootstrapFailedTitle,
      body: _resetFailed
          ? l10n.bootstrapResetFailedBody
          : l10n.bootstrapFailedBody,
      error: error,
      details: widget.diagnostics.sanitize(error.toString(), limit: 300),
      retry: retry,
      secondary: reset,
      reportSource: 'bootstrap-gate',
    );
  }

  @override
  void dispose() {
    _generation++;
    _controller?.dispose();
    super.dispose();
  }
}
