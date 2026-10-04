part of '../main.dart';

/// Remembers which route sits on top of the shell navigator. Only the name
/// matters: unnamed routes (dialogs, sheets, pushed pages) report null.
class _TopRouteTracker extends NavigatorObserver {
  final List<Route<dynamic>> _stack = [];

  String? get topName => _stack.isEmpty ? null : _stack.last.settings.name;

  /// A context inside the top page (below the navigator's overlay), for
  /// the app-level parts that need one: the Undo bar, Copy.
  BuildContext? get topContext {
    for (final route in _stack.reversed) {
      if (route is ModalRoute && route.subtreeContext != null) {
        return route.subtreeContext;
      }
    }
    return null;
  }

  @override
  void didPush(Route<dynamic> route, Route<dynamic>? previousRoute) {
    _stack.add(route);
  }

  @override
  void didPop(Route<dynamic> route, Route<dynamic>? previousRoute) {
    _stack.remove(route);
  }

  @override
  void didRemove(Route<dynamic> route, Route<dynamic>? previousRoute) {
    _stack.remove(route);
  }

  @override
  void didReplace({Route<dynamic>? newRoute, Route<dynamic>? oldRoute}) {
    final index = oldRoute == null ? -1 : _stack.indexOf(oldRoute);
    if (index >= 0) {
      if (newRoute == null) {
        _stack.removeAt(index);
      } else {
        _stack[index] = newRoute;
      }
    } else if (newRoute != null) {
      _stack.add(newRoute);
    }
  }
}

/// Decides the start destination from persisted state.
class _Root extends ConsumerStatefulWidget {
  const _Root({required this.say});

  /// The app's one-shot line (the shell's status notice).
  final void Function(String message, {String? supporting, bool failed}) say;

  @override
  ConsumerState<_Root> createState() => _RootState();
}

class _RootState extends ConsumerState<_Root> {
  bool _started = false;
  int _attempts = 0;
  late final ConnectionController _controller;
  late final BuiltinServerStarter _builtin;

  @override
  void initState() {
    super.initState();
    _controller = ref.read(connProvider)..addListener(_changed);
    _builtin = ref.read(builtinServerStarterProvider)..addListener(_changed);
    _attachPhoneSetup();
    _recoverFromLastExit();
    // Times each reply and keeps the phone awake while one runs on the
    // in-app server (lib/builtin/reply_watch.dart).
    ref.read(replyWatchProvider).attach(ConnectionReplySource(_controller));
    // The status scope is above this route. Publish the guard after mounting
    // so its slot notification cannot rebuild an ancestor during this build.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) _startThermalGuard();
    });
  }

  void _startThermalGuard() {
    // Pause the AI Team on this phone while Android says it is hot.
    startThermalGuard(
      ref.read(thermalGuardSlotProvider),
      store: _controller.store,
      linux: ref.read(builtinLinuxProvider),
      diagnostics: _controller.diagnostics,
      // Every confirmed pause, stop and resume is filed in that server's
      // While you were away (P6.2); the guard still decides alone.
      onAct: (kind, team, since, at) => unawaited(
        _controller.recordServerAct(
          profileId: team.id,
          kind: switch (kind) {
            ThermalNoticeKind.paused => AutomaticActKind.heatPause,
            ThermalNoticeKind.stopped => AutomaticActKind.heatStop,
            ThermalNoticeKind.resumed => AutomaticActKind.heatResume,
          },
          eventId: 'thermal.${kind.name}:${since.microsecondsSinceEpoch}',
          at: at,
        ),
      ),
    );
  }

  /// Once per process: why Android last ended the app, and bring back the
  /// phone's OpenCode (and the AI Team) it stopped with it.
  void _recoverFromLastExit() {
    unawaited(
      ref
          .read(appExitRecoveryProvider)
          .runOnce(
            store: _controller.store,
            active: _controller.profile,
            starter: _builtin,
            diagnostics: _controller.diagnostics,
            recover: ref.read(phoneServerHealingProvider).check,
            // The team that ran comes back too: healing restarts OpenCode
            // only (an app update ends both).
            reviveTeam: (_) => BuiltinTeam(linux: _builtin.linux).ensureRunning(
              notice: ChannelSetupEngine.deviceStrings().aiteamComponentNotice,
              observe: true,
            ),
          ),
    );
  }

  /// The phone setup engine ends every job by starting OpenCode and
  /// connecting; only the app shell has the profiles and the connection.
  void _attachPhoneSetup() {
    AppLocalizations strings() {
      final locale =
          _controller.appLocale.value ?? PlatformDispatcher.instance.locale;
      final supported = AppLocalizations.supportedLocales.any(
        (candidate) => candidate.languageCode == locale.languageCode,
      );
      return lookupAppLocalizations(
        supported ? Locale(locale.languageCode) : const Locale('en'),
      );
    }

    // Read now: these closures outlive this widget (the engine is app-wide),
    // and a widget's ref throws once the widget is gone.
    final store = ref.read(bootstrapProvider).store;
    PhoneSetup.attach(
      strings: strings,
      // Built when the step runs, so the shell reads the profile store only
      // when a setup job actually needs it.
      finisher: (request) => BuiltinSetupFinisher(
        store: store,
        starter: _builtin,
        strings: strings,
        isConnectedTo: (profile) =>
            _controller.profile?.id == profile.id &&
            _controller.hasConnectedServer,
        connect: (profile) async {
          await _controller.connect(profile);
          if (_controller.hasConnectedServer) return null;
          final l10n = strings();
          return l10n.builtinServerConnectFailed(
            _controller.lastError ?? l10n.builtinServerStopped,
          );
        },
      ).call(request),
      // The Termux host ends the same way, through Termux's own manager.
      termuxFinisher: (request) => TermuxSetupFinisher(
        store: store,
        strings: strings,
        isConnectedTo: (profile) =>
            _controller.profile?.id == profile.id &&
            _controller.hasConnectedServer,
        connect: (profile) async {
          await _controller.connect(profile);
          if (_controller.hasConnectedServer) return null;
          final l10n = strings();
          return l10n.builtinServerConnectFailed(
            _controller.lastError ?? l10n.e7SetupAuthFailed,
          );
        },
      ).call(request),
    );
  }

  void _changed() {
    if (!mounted) return;
    // A successful connect resets the streak, so the next failure after a
    // long healthy session starts its count from one again.
    if (_controller.hasConnectedServer) _attempts = 0;
    setState(() {});
  }

  bool _startingPhoneServer = false;

  Future<void> _startPhoneServer() async {
    if (_startingPhoneServer) return;
    setState(() => _startingPhoneServer = true);
    final strings = lookupAppLocalizations(Localizations.localeOf(context));
    try {
      await LocalServerControls(
        store: _controller.store,
        connection: _controller,
      ).restart();
    } on LocalServerControlFailure catch (failure) {
      // What failed in words; Termux's own text stays in diagnostics.
      widget.say(
        strings.rootPhoneServerStartFailed,
        supporting: productErrorText(failure, l10n: strings),
        failed: true,
      );
    } finally {
      if (mounted) setState(() => _startingPhoneServer = false);
    }
    if (!mounted) return;
    _started = false;
    _connectSaved();
  }

  void _connectSaved() {
    if (_started) return;
    final conn = _controller;
    // A connection may already be in flight (for example a launch intent).
    // Mounting the root must not replace its gateway while health is pending.
    if (conn.api != null) return;
    final profile = conn.profile;
    if (profile == null ||
        profile.requiresPasswordReentry ||
        profile.requiresCodexTokenReentry) {
      return;
    }
    _started = true;
    _attempts += 1;
    if (looksLikeInAppServer(profile)) {
      // Until the launch start has had its turn, nothing here is a verdict:
      // no "stopped" page flashes before "Starting…" (QA B7).
      _autoStartPending = true;
      final retry = _retrying;
      WidgetsBinding.instance.addPostFrameCallback(
        (_) => unawaited(_autoStartThenConnect(profile, retry: retry)),
      );
      return;
    }
    WidgetsBinding.instance.addPostFrameCallback((_) => conn.connect(profile));
  }

  /// True from the in-app server's launch until its start (and one retry)
  /// had its turn.
  bool _autoStartPending = false;

  /// Set only while Try again calls [_connectSaved].
  bool _retrying = false;

  /// Launch joins the same foreground recovery owner as resume and polling,
  /// then starts a stopped server the person did not stop themselves
  /// ([PhoneServerHealing.startForLaunch]) and connects.
  Future<void> _autoStartThenConnect(
    ServerProfile profile, {
    bool retry = false,
  }) async {
    try {
      await ref
          .read(phoneServerHealingProvider)
          .startForLaunch(profile, retry: retry);
    } finally {
      if (mounted) setState(() => _autoStartPending = false);
    }
  }

  Future<void> _startInAppServer() async {
    final profile = _controller.profile;
    if (profile == null || _builtin.starting) return;
    final failure = await _builtin.start(profile);
    if (!mounted || failure != null || _controller.profile?.id != profile.id) {
      return;
    }
    // This explicit action also authorizes connecting when automatic
    // reconnect is off. The recovery owner may already have connected it.
    _started = true;
    await ref
        .read(phoneServerHealingProvider)
        .connectIfNeeded(profile, automatic: false);
  }

  @override
  Widget build(BuildContext context) {
    final conn = ref.watch(connProvider);
    if (conn.profile == null) return const ServersScreen();
    if (conn.hasConnectedServer) {
      return const HomeScreen();
    }
    if (conn.profile!.requiresPasswordReentry ||
        conn.profile!.requiresCodexTokenReentry) {
      return const ServersScreen();
    }
    _connectSaved();
    final navigator = Navigator.of(context);
    final profile = conn.profile!;
    final inApp = _builtin.recognises(profile);
    final startFailure = _builtin.failureFor(profile);
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    // Cached opening shell (docs/qa/codex-speed-2026-09-28 item 1): the
    // titles this server listed last time, read-only under the honest
    // connection state. Nothing here marks the server connected, fills the
    // live session map or enables a live action.
    final status = conn.connectionStatus;
    final cached = conn.cachedSessionInventory;
    // Conversations with agents on this phone were saved separately: the
    // opening list shows them too, so the list never grows a second wave.
    final agents = conn.savedAgentSessionPreviews;
    final sessions = [...?cached?.sessions, ...agents]
      ..sort((a, b) => b.updated.compareTo(a.updated));
    final lastKnown = sessions.isEmpty
        ? null
        : SessionInventoryPreview(
            cached?.fetchedAt ?? DateTime.now(),
            sessions,
          );
    final opening = lastKnown != null;
    // While the launch start is pending, an early refused connect or a
    // first failed try is not the answer yet: the page keeps connecting.
    final pending = _autoStartPending && looksLikeInAppServer(profile);
    // The in-app OpenCode process runs but the connect failed (a busy phone
    // right after boot): it is not "stopped". Say it is not answering, as
    // the status line does, while the healing owner keeps reconnecting.
    final runningSilent =
        !pending &&
        inApp &&
        startFailure == null &&
        conn.lastError != null &&
        _builtin.runningFor(profile);
    final error = pending || runningSilent
        ? null
        : startFailure != null
        ? l10n.builtinServerStartFailed(startFailure.reason(l10n))
        : conn.lastError;
    final card = SavedServerConnectionCard(
      size: opening ? KitStateSize.inline : KitStateSize.page,
      profileName: serverDisplayName(profile, l10n, among: conn.store.profiles),
      usesConnectionToken: conn.usesConnectionToken,
      requiresTokenReentry: profile.requiresCodexTokenReentry,
      baseUrl: profile.baseUrl,
      // The raw failure: the card diagnoses it into words and keeps
      // the text itself under Details only.
      error: error,
      attempts: _attempts,
      // The controller's one eight-second clock, shared with every status
      // line; `since` is set once an attempt actually began (P4.4).
      notAnswering:
          runningSilent ||
          !pending &&
              status.phase == ConnectionStatusPhase.notAnswering &&
              status.since != null,
      inAppServer: inApp,
      startingInAppServer: inApp && _builtin.starting,
      inAppStartFailed: !pending && startFailure != null,
      // Why it did not start, in plain words (QA B1); the technical line
      // stays under Details.
      inAppStartFailedBody: pending ? null : startFailure?.explanation(l10n),
      onOpenInAppSetup: !pending && startFailure != null
          ? () => openPhoneSetupStart(context)
          : null,
      supportsTermux:
          !inApp &&
          !conn.usesConnectionToken &&
          platformCapabilities.supportsTermux,
      onChangeServer: () =>
          navigator.pushNamedAndRemoveUntil('/servers', (_) => false),
      onUpdateToken: () => navigator.pushNamedAndRemoveUntil(
        '/servers',
        (_) => false,
        arguments: 'edit-active',
      ),
      onUpdatePassword: () => navigator.pushNamedAndRemoveUntil(
        '/servers',
        (_) => false,
        arguments: 'edit-active',
      ),
      onOpenTermuxSetup:
          !inApp &&
              !conn.usesConnectionToken &&
              platformCapabilities.supportsTermux
          // The phone's own Termux server goes to This phone (Start is
          // there); any other server on this phone goes to phone setup.
          ? () => TermuxBridge.managesServerUrl(profile.baseUrl)
                ? openThisPhone(context, kind: PhoneHostKind.termux)
                : openPhoneSetupStart(context)
          : null,
      // The app's own phone server: when nothing answers, it is stopped
      // (a phone restart, Android closing Termux, the app closed for
      // OpenCode inside the app), and one tap starts it.
      onStartPhoneServer: inApp
          ? _startInAppServer
          : !conn.usesConnectionToken &&
                platformCapabilities.supportsTermux &&
                TermuxBridge.managesServerUrl(profile.baseUrl)
          ? _startPhoneServer
          : null,
      startingPhoneServer: inApp ? _builtin.starting : _startingPhoneServer,
      onRetry: () {
        _builtin.clearFailure();
        _started = false;
        _retrying = true;
        _connectSaved();
        _retrying = false;
      },
    );
    // A KitScreen, so the app's line (a share waiting for this server)
    // shows above the card (map page root-connecting). The card is this
    // server's connection state, with its own Try again and Details, so the
    // slot leaves the shared connection line out here (`bodySays`): the
    // page says it once. Every other app line still shows (P4.4).
    return _Ground(
      child: KitScreen(
        bodySays: const {KitStatusKind.connection},
        width: opening ? KitScreenWidth.list : KitScreenWidth.full,
        body: lastKnown != null
            ? ListView(
                key: const ValueKey('opening-shell'),
                children: [
                  card,
                  LastKnownSessions(
                    preview: lastKnown,
                    refreshing:
                        error == null && !profile.requiresCodexTokenReentry,
                  ),
                ],
              )
            : card,
      ),
    );
  }

  @override
  void dispose() {
    _controller.removeListener(_changed);
    _builtin.removeListener(_changed);
    super.dispose();
  }
}

/// The page ground under a bar-less root page (the app opening, the saved
/// server connecting): a KitScreen draws its ground and keeps out of the
/// system bars only with a top bar, so this does both. The ground runs
/// under the status and navigation bars; the content (the app's status
/// line first) stays inside the safe area.
class _Ground extends StatelessWidget {
  const _Ground({required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) => KitSurface(
    level: KitSurfaceLevel.ground,
    shape: KitShape.square,
    padding: KitSurfacePadding.none,
    clip: false,
    child: SafeArea(child: child),
  );
}
