part of '../terminal_screen.dart';

/// The server's terminals: one list, running first, each row opening its
/// terminal, with rename and stop or remove in the row's menu.
class TerminalScreen extends StatefulWidget {
  final ConnectionController controller;

  /// Draws its own top bar ("Terminal"). Null decides by where it sits: a
  /// page of its own when nothing above it already frames it.
  final bool? page;

  /// Fixed rows under the top bar (the terminal page's source choice).
  final List<Widget> header;

  /// Offered when this server has no terminals: switch to this phone's.
  final VoidCallback? onUsePhone;

  const TerminalScreen({
    super.key,
    required this.controller,
    this.page,
    this.header = const [],
    this.onUsePhone,
  });

  @override
  State<TerminalScreen> createState() => _TerminalScreenState();
}

/// A failure about the list that keeps the rows: a refresh or a start that
/// did not work, with the step that tries again.
typedef _ListFailure = ({String title, String message, VoidCallback retry});

class _TerminalScreenState extends State<TerminalScreen> {
  List<TerminalProcess>? _processes;
  String? _error;
  _ListFailure? _failure;
  bool _creating = false;
  bool _removingEnded = false;
  DateTime _loadStartedAt = DateTime.now();
  ServerOperationsGateway? _activeRepository;
  int _locationRevision = -1;
  int _dataRefreshRevision = -1;
  int _ptyRevision = -1;
  int _loadGeneration = 0;

  ServerOperationsGateway? get _repository => widget.controller.repository;

  @override
  void initState() {
    super.initState();
    widget.controller.addListener(_controllerChanged);
    _captureLocation();
    _load();
  }

  void _captureLocation() {
    _activeRepository = _repository;
    _locationRevision = _revisionOf(_repository);
    _dataRefreshRevision = widget.controller.dataRefreshRevision;
    _ptyRevision = widget.controller.ptyRevision;
  }

  void _controllerChanged() {
    final repository = _repository;
    final revision = _revisionOf(repository);
    final dataRefreshRevision = widget.controller.dataRefreshRevision;
    final ptyRevision = widget.controller.ptyRevision;
    if (identical(repository, _activeRepository) &&
        revision == _locationRevision &&
        dataRefreshRevision == _dataRefreshRevision &&
        ptyRevision == _ptyRevision) {
      return;
    }
    final locationChanged =
        !identical(repository, _activeRepository) ||
        revision != _locationRevision;
    _activeRepository = repository;
    _locationRevision = revision;
    final dataRefreshChanged = dataRefreshRevision != _dataRefreshRevision;
    _dataRefreshRevision = dataRefreshRevision;
    _ptyRevision = ptyRevision;
    _loadGeneration++;
    if (widget.controller.lifecycleSuspended) {
      setState(() {
        _processes ??= const [];
        _error = null;
      });
      return;
    }
    if (widget.controller.connectionLoading && !dataRefreshChanged) {
      return;
    }
    if (locationChanged) {
      setState(() {
        _processes = null;
        _error = null;
        _failure = null;
        _creating = false;
      });
    }
    _load();
  }

  int _revisionOf(ServerOperationsGateway? repository) => Object.hash(
    widget.controller.locationRevision,
    repository is LocationAwareProductRepository
        ? (repository as LocationAwareProductRepository).locationRevision
        : 0,
  );

  bool _isCurrentLocation(ServerOperationsGateway repository, int revision) =>
      mounted &&
      identical(repository, _repository) &&
      revision == _revisionOf(repository);

  /// The repository for an act started at [locationRevision]; null (and the
  /// act fails in words) once the person has moved to another place.
  Future<ServerOperationsGateway?> Function() _actionRepository(
    int locationRevision,
  ) => () async {
    if (locationRevision != widget.controller.locationRevision) return null;
    final repository = await widget.controller.prepareActionRepository();
    if (locationRevision != widget.controller.locationRevision) return null;
    return repository;
  };

  Future<void> _load() async {
    final generation = ++_loadGeneration;
    // A server with no terminals is explained, not asked.
    if (!widget.controller.capabilities.terminal) return;
    if (_processes == null) {
      setState(() {
        _error = null;
        _loadStartedAt = DateTime.now();
      });
    }
    try {
      final repository = await widget.controller.prepareActionRepository();
      if (!mounted || generation != _loadGeneration) return;
      if (repository == null) {
        throw ProductException(_l10nOf(context).e7SetupServerDisconnected);
      }
      final processes = await repository.listTerminals();
      if (mounted && generation == _loadGeneration) {
        setState(() {
          _processes = processes;
          _error = null;
          _failure = null;
        });
      }
    } catch (error) {
      if (mounted && generation == _loadGeneration) {
        final message = productErrorText(error);
        setState(() {
          _error = message;
          if (_processes != null) {
            _failure = (
              title: _l10nOf(context).refreshFailed,
              message: setupUiMessage(_l10nOf(context), message),
              retry: () => unawaited(_load()),
            );
          }
        });
      }
    }
  }

  Future<void> _create() async {
    if (_creating) return;
    setState(() {
      _creating = true;
      _failure = null;
    });
    final l10n = _l10nOf(context);
    final repository = await widget.controller.prepareActionRepository();
    if (!mounted) return;
    if (repository == null) {
      setState(() {
        _creating = false;
        _failure = (
          title: l10n.terminalScreenCreateFailed,
          message: l10n.e7SetupServerDisconnected,
          retry: () => unawaited(_create()),
        );
      });
      return;
    }
    final revision = _revisionOf(repository);
    try {
      final process = await repository.createTerminal(
        title: l10n.e7SetupTerminalNumber((_processes?.length ?? 0) + 1),
      );
      if (!_isCurrentLocation(repository, revision)) return;
      setState(() => _creating = false);
      await _open(process, repository);
    } catch (error) {
      if (_isCurrentLocation(repository, revision)) {
        setState(
          () => _failure = (
            title: l10n.terminalScreenCreateFailed,
            message: setupUiMessage(l10n, productErrorText(error)),
            retry: () => unawaited(_create()),
          ),
        );
      }
    } finally {
      if (_isCurrentLocation(repository, revision) && _creating) {
        setState(() => _creating = false);
      }
    }
  }

  Future<void> _open(
    TerminalProcess process, [
    ServerOperationsGateway? repository,
  ]) async {
    final gateway = repository ?? _repository;
    if (gateway == null) return;
    await pushKitPage<void>(
      context,
      (_) => TerminalSurface(
        repository: gateway,
        repositoryResolver: () => widget.controller.repository,
        dataRefreshRevisionResolver: () =>
            widget.controller.dataRefreshRevision,
        keepLiveInBackgroundResolver: () =>
            widget.controller.keepLiveInBackground,
        repositoryChanges: widget.controller,
        process: process,
      ),
    );
    // A rename, a stop or a new terminal on the page shows in the list.
    if (mounted) await _load();
  }

  /// Whether the list still shows the place an act started in: a rename or
  /// removal that ends after the person moved on must not reload the new
  /// place's list (it already loaded when the place changed).
  bool Function() _startedHere() {
    final repository = _repository;
    final revision = _revisionOf(repository);
    return () => repository != null && _isCurrentLocation(repository, revision);
  }

  Future<void> _rename(TerminalProcess process) async {
    final stillHere = _startedHere();
    final renamed = await _renameTerminal(
      context,
      process,
      _actionRepository(widget.controller.locationRevision),
    );
    if (renamed != null && stillHere()) await _load();
  }

  Future<void> _remove(TerminalProcess process) async {
    final stillHere = _startedHere();
    final removed = await _removeTerminal(
      context,
      process,
      _actionRepository(widget.controller.locationRevision),
    );
    if (removed && stillHere()) await _load();
  }

  /// Removes every ended terminal after one question; running ones stay.
  Future<void> _removeEnded(List<TerminalProcess> ended) async {
    if (_removingEnded || ended.isEmpty) return;
    final l10n = _l10nOf(context);
    final repository = _actionRepository(widget.controller.locationRevision);
    final stillHere = _startedHere();
    setState(() => _removingEnded = true);
    try {
      final removed = await showKitConfirm(
        context,
        title: l10n.terminalScreenRemoveEndedTitle(ended.length),
        body: l10n.terminalScreenRemoveEndedBody,
        confirmLabel: l10n.terminalScreenRemoveEnded(ended.length),
        kind: KitConfirmKind.destructive,
        icon: AppIconography.delete,
        confirmKey: const ValueKey('terminal-remove-ended-confirm'),
        action: () async {
          final gateway = await repository();
          if (gateway == null) {
            throw ProductException(l10n.e7SetupServerDisconnected);
          }
          for (final process in ended) {
            await gateway.removeTerminal(process.id);
          }
        },
      );
      if (removed && stillHere()) await _load();
    } finally {
      if (mounted) setState(() => _removingEnded = false);
    }
  }

  bool _standalone(BuildContext context) =>
      !KitStatusLineSlot.existsAbove(context) &&
      Scaffold.maybeOf(context) == null;

  bool _hasTopBar(BuildContext context) => widget.page ?? _standalone(context);

  @override
  Widget build(BuildContext context) {
    final l10n = _l10nOf(context);
    final processes = _processes;
    final supported = widget.controller.capabilities.terminal;
    final listed = supported && processes != null && processes.isNotEmpty;
    final ended = [
      if (supported)
        for (final process in processes ?? const <TerminalProcess>[])
          if (!process.running) process,
    ];
    return KitScreen(
      topBar: _hasTopBar(context)
          ? KitTopBar(
              title: l10n.libraryTerminalTitle,
              menuKey: const ValueKey('terminal-list-menu'),
              // Clearing up is occasional: it waits in the bar's menu
              // rather than under the list.
              menu: [
                if (ended.isNotEmpty)
                  KitMenuItem(
                    key: const ValueKey('terminal-remove-ended'),
                    label: l10n.terminalScreenRemoveEnded(ended.length),
                    icon: AppIconography.clearAll,
                    destructive: true,
                    onSelected: () => unawaited(_removeEnded(ended)),
                  ),
              ],
            )
          : null,
      header: widget.header,
      width: KitScreenWidth.list,
      // One new-terminal action per state: the empty state has its own.
      bottom: listed
          ? KitActionBlock(
              primary: KitAction(
                key: const ValueKey('terminal-new'),
                label: l10n.e7SetupNewTerminal,
                icon: AppIconography.add,
                working: _creating,
                onPressed: _create,
              ),
            )
          : null,
      body: supported ? _body(context, l10n) : _unsupported(context, l10n),
    );
  }

  /// This server keeps no terminals: say so, about the terminal only and
  /// naming the server ("Laptop doesn't share a terminal"), instead of an
  /// empty list, and offer this phone's terminal where there is one and no
  /// source choice already offers it.
  Widget _unsupported(BuildContext context, AppLocalizations l10n) {
    final onUsePhone = widget.onUsePhone;
    final name = widget.controller.profile?.name.trim();
    return KitStateView.missing(
      key: const ValueKey('terminal-unavailable'),
      capability: _terminalCapability,
      title: name == null || name.isEmpty
          ? l10n.terminalScreenNoTerminalThisServer
          : l10n.terminalScreenNoTerminalNamed(name),
      why: l10n.terminalScreenNoTerminalWhy,
      size: KitStateSize.page,
      icon: AppIconography.terminal,
      enableKey: const ValueKey('terminal-use-phone'),
      enable: onUsePhone == null
          ? null
          : KitAction(
              label: l10n.terminalScreenUsePhone,
              onPressed: onUsePhone,
            ),
    );
  }

  /// One terminal's row: open on tap; rename and stop or remove from its
  /// menu (long-press or right click).
  Widget _processRow(BuildContext context, TerminalProcess process) {
    final l10n = _l10nOf(context);
    final roles = KitTokens.of(context).roles;
    final running = process.running;
    final code = process.exitCode;
    final state = running
        ? l10n.terminalScreenRowRunning(process.command)
        : code == null
        ? l10n.terminalScreenRowEndedNoCode(process.command)
        : l10n.terminalScreenRowEnded('$code', process.command);
    return KitRow(
      leading: KitRow.icon(
        context,
        AppIconography.terminal,
        color: running ? roles.success : null,
      ),
      title: process.title,
      supporting: TextSpan(text: state),
      trailing: const KitChevron(),
      onTap: () => unawaited(_open(process)),
      menuLabel: l10n.terminalScreenMenuLabel(process.title),
      menu: [
        KitMenuItem(
          key: const ValueKey('terminal-menu-open'),
          label: l10n.terminalScreenOpen(process.title),
          icon: AppIconography.terminal,
          onSelected: () => unawaited(_open(process)),
        ),
        KitMenuItem(
          key: const ValueKey('terminal-menu-rename'),
          label: l10n.terminalScreenRename(process.title),
          icon: AppIconography.edit,
          onSelected: () => unawaited(_rename(process)),
        ),
        KitMenuItem(
          key: const ValueKey('terminal-menu-remove'),
          label: running
              ? l10n.terminalScreenStop(process.title)
              : l10n.terminalScreenRemove(process.title),
          icon: running ? AppIconography.stopCircle : AppIconography.delete,
          destructive: true,
          onSelected: () => unawaited(_remove(process)),
        ),
      ],
    );
  }

  Widget _body(BuildContext context, AppLocalizations l10n) {
    final processes = _processes;
    final error = _error;
    if (processes == null && error == null) {
      // A wait that runs past 8 s says so and offers Try again (STATE-5).
      return KitStateView(
        key: const ValueKey('terminal-loading'),
        icon: AppIconography.terminal,
        title: l10n.terminalScreenLoading,
        progress: const KitProgress.waiting(),
        since: _loadStartedAt,
        onSlow: [
          KitAction(
            label: l10n.commonRetry,
            onPressed: () => unawaited(_load()),
          ),
        ],
      );
    }
    if (processes == null) {
      // Every load failure draws the unplugged cable (design standard §10).
      return KitStateView(
        key: const ValueKey('terminal-list-failed'),
        icon: AppIconography.error,
        tone: AppStatusTone.failure,
        illustration: const StatesUnpluggedScene(),
        title: l10n.terminalListFailedTitle,
        body: setupUiMessage(l10n, error!),
        primary: KitAction(
          label: l10n.commonRetry,
          onPressed: () => unawaited(_load()),
        ),
        tertiary: [
          KitAction(
            key: const ValueKey('product-error-report-bug'),
            label: l10n.e7LibraryReportABug,
            onPressed: () => unawaited(openBugReport(context)),
          ),
        ],
      );
    }
    // Running first (they are what the person is using), then the ended
    // ones, each group in the server's order.
    final running = [
      for (final process in processes)
        if (process.running) process,
    ];
    final ended = [
      for (final process in processes)
        if (!process.running) process,
    ];
    final failure = _failure;
    final tokens = KitTokens.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        // A failed refresh or start unfolds over the kept rows and folds
        // away once it works (design standard §10).
        KitReveal(
          child: failure == null
              ? null
              : Padding(
                  padding: EdgeInsetsDirectional.symmetric(
                    horizontal: tokens.gutter,
                    vertical: tokens.space2,
                  ),
                  child: KitNotice(
                    key: const ValueKey('terminal-list-notice'),
                    tone: AppStatusTone.failure,
                    title: failure.title,
                    message: failure.message,
                    actions: [
                      KitAction(
                        label: l10n.refreshRetry,
                        onPressed: failure.retry,
                      ),
                    ],
                  ),
                ),
        ),
        Expanded(
          key: const ValueKey('refresh-content'),
          child: KitRefresh(
            onRefresh: _load,
            child: processes.isEmpty
                ? KitStateView(
                    // The terminal window with its prompt: the same drawing
                    // as This phone's terminal before it is set up.
                    key: const ValueKey('terminal-none'),
                    icon: AppIconography.terminal,
                    illustration: const StatesTerminalScene(),
                    title: l10n.terminalScreenEmptyTitle,
                    body: _emptyBody(l10n),
                    primary: KitAction(
                      key: const ValueKey('terminal-new'),
                      label: l10n.e7SetupNewTerminal,
                      icon: AppIconography.add,
                      working: _creating,
                      onPressed: _create,
                    ),
                  )
                : ListView(
                    physics: const AlwaysScrollableScrollPhysics(),
                    padding: EdgeInsetsDirectional.only(
                      top: tokens.space2,
                      bottom: KitScreen.endPadding(context),
                    ),
                    children: [
                      KitRowGroup(
                        key: const ValueKey('terminal-session-rows'),
                        children: [
                          // A terminal started or removed while the list is
                          // open unfolds in or folds away where it was
                          // (design standard §10, kit-v2: a list that
                          // changes while open uses KitAnimatedRows); the
                          // first paint shows the rows at once.
                          KitAnimatedRows(
                            children: [
                              for (final (index, process) in [
                                ...running,
                                ...ended,
                              ].indexed)
                                KeyedSubtree(
                                  key: ValueKey(
                                    'terminal-session-${process.id}',
                                  ),
                                  child: Column(
                                    mainAxisSize: MainAxisSize.min,
                                    crossAxisAlignment:
                                        CrossAxisAlignment.stretch,
                                    children: [
                                      if (index > 0)
                                        const KitDivider(
                                          inset: KitDividerInset.text,
                                        ),
                                      _processRow(context, process),
                                    ],
                                  ),
                                ),
                            ],
                          ),
                        ],
                      ),
                      // Framed by a page without this bar: the clear-up
                      // stays under the list (the bar's menu otherwise).
                      if (ended.isNotEmpty && !_hasTopBar(context))
                        Padding(
                          padding: EdgeInsetsDirectional.only(
                            start: tokens.gutter,
                            top: tokens.space3,
                            end: tokens.gutter,
                          ),
                          child: KitActionBlock(
                            tertiary: [
                              KitAction(
                                key: const ValueKey('terminal-remove-ended'),
                                label: l10n.terminalScreenRemoveEnded(
                                  ended.length,
                                ),
                                icon: AppIconography.clearAll,
                                working: _removingEnded,
                                onPressed: () => unawaited(_removeEnded(ended)),
                              ),
                            ],
                          ),
                        ),
                    ],
                  ),
          ),
        ),
      ],
    );
  }

  /// "Start one in oc_app." — the project the new terminal opens in.
  String _emptyBody(AppLocalizations l10n) {
    final directory = widget.controller.directory?.trim();
    final name = directory == null || directory.isEmpty
        ? null
        : directory
              .replaceAll('\\', '/')
              .split('/')
              .where((part) => part.isNotEmpty)
              .lastOrNull;
    return name == null
        ? l10n.terminalScreenEmptyBodyNoProject
        : l10n.terminalScreenEmptyBody(KitBidi.ltr(name));
  }

  @override
  void dispose() {
    _loadGeneration++;
    widget.controller.removeListener(_controllerChanged);
    super.dispose();
  }
}
