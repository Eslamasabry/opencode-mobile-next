part of '../servers_screen.dart';

class _ServersScreenState extends ConsumerState<ServersScreen> {
  bool _busy = false;

  bool _handledRouteArgument = false;

  /// A connect attempt from the list that failed, rendered inline above the
  /// rows in the same verdict style the editor uses — never a red snackbar
  /// carrying a raw exception.
  String? _listFailure;

  String? _listFailureDetails;

  /// Bumped after Termux setup returns so the running-server entry re-reads
  /// the phone instead of trusting what it saw before the user left.
  int _termuxRevision = 0;

  /// The first screen's last look found OpenCode or Termux on this phone:
  /// that leads the page, and the welcome steps back.
  bool _termuxFound = false;

  @override
  void initState() {
    super.initState();
    // The welcome as the first thing a device shows is what makes it new
    // (see [FirstRun]); the shell reads this after the first connect.
    final store = ref.read(bootstrapProvider).store;
    unawaited(
      FirstRun(
        store.prefs,
      ).observeServers(hasServers: store.profiles.isNotEmpty),
    );
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_handledRouteArgument) return;
    _handledRouteArgument = true;
    // The connection banner's "Update password" action routes here with this
    // argument: open the active profile's editor with the password focused so
    // a rotated serve password is one paste away (never a modal).
    final argument = ModalRoute.of(context)?.settings.arguments;
    if (argument is ServersRouteRequest) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) unawaited(_handleRouteRequest(argument));
      });
      return;
    }
    if (argument == 'edit-active') {
      final store = ref.read(bootstrapProvider).store;
      ServerProfile? active;
      for (final p in store.profiles) {
        if (p.id == store.activeId) active = p;
      }
      final target = active;
      if (target != null) {
        WidgetsBinding.instance.addPostFrameCallback((_) {
          if (mounted) unawaited(_edit(existing: target, focusPassword: true));
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final bootstrap = ref.watch(bootstrapProvider);
    final accountConnection = ref.watch(connProvider);
    final store = bootstrap.store;
    final copy = lookupAppLocalizations(Localizations.localeOf(context));
    final hasServers = store.profiles.isNotEmpty;
    // A root page (§1.18): the product mark and name, and About. Which
    // server needs the person is said on its own row (R1).
    //
    // With no server connected this is the whole app (no shell, so no
    // Settings): Report a bug and the Setup guide, which otherwise live in
    // Settings, sit in its menu. Opened from Settings, Settings has them.
    final isRoot = !(ModalRoute.of(context)?.canPop ?? false);
    final topBar = KitTopBar(
      title: copy.openCodeConnectionLabel,
      brand: true,
      actions: [
        KitAction(
          key: const ValueKey('servers-about'),
          label: copy.e7SetupAboutNotices,
          icon: AppIconography.info,
          onPressed: () => Navigator.pushNamed(context, '/about'),
        ),
      ],
      menuKey: const ValueKey('servers-menu'),
      menu: [
        if (isRoot) ...[
          KitMenuItem(
            key: const ValueKey('servers-report-bug'),
            label: copy.e7LibraryReportABug,
            icon: AppIconography.bug,
            onSelected: () => unawaited(openBugReport(context)),
          ),
          KitMenuItem(
            key: const ValueKey('servers-setup-guide'),
            label: copy.onboardingSetupGuide,
            icon: AppIconography.guide,
            onSelected: () => unawaited(
              pushKitPage<void>(
                context,
                (_) => const GuideScreen(embedded: false),
              ),
            ),
          ),
        ],
      ],
    );
    if (!hasServers) {
      return KitScreen(
        topBar: topBar,
        width: KitScreenWidth.reading,
        body: _WelcomeView(
          busy: _busy,
          found: _termuxFound,
          runningServer: _runningServerEntry(
            store.profiles,
            accountConnection,
            lead: true,
          ),
          phoneSetup: PhoneSetupWelcomeEntry(revision: _termuxRevision),
          onComputer: () => unawaited(_edit()),
          onPhone: _openPhoneSetup,
          onDemo: _demo,
        ),
      );
    }
    final activeId = store.activeId;
    final phoneServer = phoneServerProfile(store.profiles, activeId);
    final saved = [
      for (final p in store.profiles)
        if (!looksLikeInAppServer(p) && !shownAsPhoneRow(p)) p,
    ];
    final tokens = KitTokens.of(context);
    // The other servers' words come from the one attention source, which an
    // isolated profile never reads.
    final monitor = accountConnection.isIsolated
        ? null
        : accountConnection.profileMonitor;
    return KitScreen(
      topBar: topBar,
      width: KitScreenWidth.list,
      // One bar for a connect, save or removal in flight (standard §4).
      loading: _busy,
      loadingLabel: copy.e7SetupServerOperation,
      // Adding a server is what this screen offers beyond its rows:
      // the one primary, pinned below the list (§1, §2).
      // The demo is the welcome's "Just show me"; with servers saved it
      // would be a second, lesser way in (R3).
      bottom: KitActionBlock(
        primary: KitAction(
          key: const ValueKey('servers-add'),
          label: copy.e7SetupAddServer,
          icon: AppIconography.add,
          onPressed: _busy ? null : () => _edit(),
        ),
      ),
      body: ListView(
        padding: EdgeInsetsDirectional.only(
          bottom: KitScreen.endPadding(context),
        ),
        children: [
          // A saved password or token this phone can no longer read is
          // said once, by the connection status line above (with Enter the
          // password) and by the server's row: no notice here repeats it.
          // A connect or a removal that failed unfolds over the rows and
          // folds away when dismissed or retried (design standard §10).
          KitReveal(
            key: const ValueKey('server-connect-failure-slot'),
            child: switch (_listFailure) {
              final failure? => _Rails(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    KitNotice(
                      key: const ValueKey('server-connect-failure'),
                      tone: AppStatusTone.failure,
                      message: failure,
                      onDismiss: () => setState(() {
                        _listFailure = null;
                        _listFailureDetails = null;
                      }),
                    ),
                    if (_listFailureDetails != null)
                      KitDetailsFold(text: _listFailureDetails),
                  ],
                ),
              ),
              null => null,
            },
          ),
          SizedBox(height: tokens.space2),
          // Every server in one list (R1): the rows ordered by urgency,
          // each saying first what it needs ("Needs you"), what runs there
          // or that its password must be entered again, then the phone's own
          // servers, which decide on their own whether they show. OpenCode
          // inside this app ("This phone") is one of the rows, ranked like
          // the others and first among equals; its saved entries are how
          // the app reaches it, so they are not listed again. A server
          // added or forgotten while the list is open unfolds in or folds
          // away where it was (design standard §10).
          ListenableBuilder(
            listenable: Listenable.merge([accountConnection, ?monitor]),
            builder: (context, _) {
              final ordered = _byUrgency([
                ?phoneServer,
                ...saved,
              ], accountConnection);
              return KitRowGroup(
                key: const ValueKey('servers-list'),
                children: [
                  KitAnimatedRows(
                    key: const ValueKey('saved-server-rows'),
                    children: [
                      for (final (i, p) in ordered.indexed)
                        KeyedSubtree(
                          key: ValueKey('saved-server-${p.id}'),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.stretch,
                            children: [
                              if (i > 0)
                                const KitDivider(inset: KitDividerInset.text),
                              if (p == phoneServer)
                                PhoneServerCard.row(
                                  key: ValueKey('phone-server-card-${p.id}'),
                                  connection: accountConnection,
                                  profile: p,
                                  connected:
                                      accountConnection.api != null &&
                                      accountConnection.profile?.id == p.id,
                                  onOpen: _busy ? null : () => _connect(p),
                                  onDisconnect: () async {
                                    if (!await confirmDisconnectServer(
                                      context,
                                      accountConnection,
                                    )) {
                                      return;
                                    }
                                    await accountConnection.disconnect(
                                      keepActive: true,
                                    );
                                  },
                                  onRemoved: () {
                                    if (mounted) setState(() {});
                                  },
                                )
                              else
                                _ServerRow(
                                  profile: p,
                                  connected:
                                      accountConnection.api != null &&
                                      accountConnection.profile?.id == p.id,
                                  snapshot: _snapshotFor(p, accountConnection),
                                  working:
                                      accountConnection.api != null &&
                                          accountConnection.profile?.id == p.id
                                      ? accountConnection.busySessions.length
                                      : null,
                                  busy: _busy,
                                  showAccount:
                                      p.id == activeId &&
                                      accountConnection.isConnected &&
                                      accountConnection
                                          .capabilities
                                          .agentAccount,
                                  queued: _waitingFor(p, accountConnection),
                                  moveDestination: _moveDestinationName(
                                    p,
                                    accountConnection,
                                    copy,
                                  ),
                                  onMoveQueued: () => _moveQueued(p),
                                  onConnect: () => _connect(p),
                                  onEdit: () => _edit(existing: p),
                                  onRemove: () => _delete(p),
                                  onAccount: () => pushKitPage<void>(
                                    context,
                                    (_) => AgentAccountScreen(
                                      connection: accountConnection,
                                    ),
                                  ),
                                ),
                            ],
                          ),
                        ),
                    ],
                  ),
                  // The phone's own servers close the list, a row each.
                  _runningServerEntry(
                    store.profiles,
                    accountConnection,
                    dividerAbove: ordered.isNotEmpty,
                  ),
                ],
              );
            },
          ),
        ],
      ),
    );
  }

  // setState for the extension members in the sibling part files: an
  // extension may not call the protected State.setState directly.
  void _set(VoidCallback fn) => setState(fn);
}
