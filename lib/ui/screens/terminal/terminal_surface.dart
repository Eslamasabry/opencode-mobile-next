part of '../terminal_screen.dart';

/// One server terminal, live: the shell with the kit's key bar, one status
/// line only while it is not connected, Copy in the bar and the rest
/// (readable text, paste, reconnect, rename, details, stop) in its menu.
class TerminalSurface extends StatefulWidget {
  final ServerOperationsGateway repository;
  final ServerOperationsGateway? Function()? repositoryResolver;
  final int Function()? dataRefreshRevisionResolver;
  final bool Function()? keepLiveInBackgroundResolver;
  final Listenable? repositoryChanges;
  final TerminalProcess process;

  const TerminalSurface({
    super.key,
    required this.repository,
    this.repositoryResolver,
    this.dataRefreshRevisionResolver,
    this.keepLiveInBackgroundResolver,
    this.repositoryChanges,
    required this.process,
  });

  @override
  State<TerminalSurface> createState() => _TerminalSurfaceState();
}

class _TerminalSurfaceState extends State<TerminalSurface>
    with WidgetsBindingObserver {
  late final xterm.Terminal _terminal;
  final _terminalController = xterm.TerminalController();
  final _scrollController = ScrollController();
  final _focus = FocusNode();
  final _keys = TerminalKeyBarController();
  final _accessibleInput = TextEditingController();
  TerminalChannel? _channel;
  late final TerminalInputQueue _input = TerminalInputQueue(_sendNow);
  StreamSubscription<String>? _subscription;
  String? _error;
  bool _connecting = true;
  DateTime _connectingSince = DateTime.now();

  /// The connection has taken longer than [KitMotion.escalateAfter]: the
  /// loading bar alone would hide a stuck wait (STATE-5), so the line says
  /// so and offers Reconnect.
  bool _slowConnect = false;
  Timer? _slowTimer;
  bool _closed = false;
  int _connectionGeneration = 0;
  Timer? _resizeTimer;
  bool _accessibleMode = false;
  bool _lifecycleSuspended = false;
  String _transcript = '';
  final _transcriptSanitizer = _TerminalTranscriptSanitizer();
  int? _terminalCursor;
  ServerOperationsGateway? _activeRepository;
  int _activeDataRefreshRevision = -1;

  /// The name shown; a rename here changes it at once.
  String? _renamed;

  String get _title => _renamed ?? widget.process.title;

  ServerOperationsGateway? get _repository => widget.repositoryResolver == null
      ? widget.repository
      : widget.repositoryResolver!();

  bool get _canWrite =>
      !_lifecycleSuspended &&
      !_connecting &&
      !_closed &&
      _error == null &&
      _channel != null;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    widget.repositoryChanges?.addListener(_repositoryChanged);
    _activeRepository = _repository;
    _activeDataRefreshRevision =
        widget.dataRefreshRevisionResolver?.call() ?? -1;
    _terminal = xterm.Terminal(
      maxLines: 5000,
      onOutput: _write,
      onResize: _resize,
    );
    final lifecycleState = WidgetsBinding.instance.lifecycleState;
    _lifecycleSuspended =
        lifecycleState == AppLifecycleState.hidden ||
        lifecycleState == AppLifecycleState.paused ||
        lifecycleState == AppLifecycleState.detached;
    if (!_lifecycleSuspended) _connect();
  }

  @override
  void didUpdateWidget(covariant TerminalSurface oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (!identical(oldWidget.repositoryChanges, widget.repositoryChanges)) {
      oldWidget.repositoryChanges?.removeListener(_repositoryChanged);
      widget.repositoryChanges?.addListener(_repositoryChanged);
    }
    if (oldWidget.process.id != widget.process.id) _renamed = null;
    _repositoryChanged(
      forceReconnect: oldWidget.process.id != widget.process.id,
    );
  }

  void _repositoryChanged({bool forceReconnect = false}) {
    final repository = _repository;
    final dataRefreshRevision =
        widget.dataRefreshRevisionResolver?.call() ?? -1;
    final dataRefreshChanged =
        dataRefreshRevision != _activeDataRefreshRevision;
    if (!forceReconnect &&
        !dataRefreshChanged &&
        identical(repository, _activeRepository)) {
      return;
    }
    _activeRepository = repository;
    _activeDataRefreshRevision = dataRefreshRevision;
    if (repository == null) {
      _connectionGeneration++;
      _resizeTimer?.cancel();
      _resizeTimer = null;
      final subscription = _subscription;
      final channel = _channel;
      _rememberCursor(channel);
      _subscription = null;
      _channel = null;
      _input.discard();
      if (mounted && !_lifecycleSuspended) {
        setState(() {
          _connecting = false;
          _closed = true;
          _error = _l10nOf(context).e7SetupTransportReconnecting;
        });
      }
      unawaited(_closeConnection(subscription, channel).catchError((_) {}));
      return;
    }
    if (!_lifecycleSuspended && mounted) unawaited(_connect());
  }

  Future<void> _connect() async {
    if (_lifecycleSuspended) return;
    final generation = ++_connectionGeneration;
    final repository = _repository;
    setState(() {
      _connecting = true;
      _connectingSince = DateTime.now();
      _slowConnect = false;
      _error = null;
    });
    _slowTimer?.cancel();
    _slowTimer = Timer(KitMotion.escalateAfter, () {
      if (mounted && _connecting && generation == _connectionGeneration) {
        setState(() => _slowConnect = true);
      }
    });
    final previousSubscription = _subscription;
    final previousChannel = _channel;
    _rememberCursor(previousChannel);
    _subscription = null;
    _channel = null;
    _input.discard();
    try {
      await _closeConnectionBestEffort(previousSubscription, previousChannel);
      if (!mounted ||
          _lifecycleSuspended ||
          generation != _connectionGeneration) {
        return;
      }
      if (repository == null) {
        throw ProductException(_l10nOf(context).e7SetupTransportReconnecting);
      }
      _activeRepository = repository;
      _transcriptSanitizer.reset();
      final channel = await repository.connectTerminal(
        widget.process.id,
        cursor: _terminalCursor,
      );
      if (!mounted ||
          _lifecycleSuspended ||
          generation != _connectionGeneration) {
        await channel.close();
        return;
      }
      _channel = channel;
      _subscription = channel.output.listen(
        (chunk) {
          if (generation == _connectionGeneration) {
            _terminal.write(chunk);
            _appendTranscript(chunk);
            _rememberCursor(channel);
          }
        },
        onError: (Object error) {
          if (mounted && generation == _connectionGeneration) {
            setState(() {
              _error = productErrorText(error);
              _closed = true;
            });
          }
        },
        onDone: () {
          if (mounted && generation == _connectionGeneration) {
            setState(() => _closed = true);
          }
        },
      );
      setState(() {
        _connecting = false;
        _closed = false;
      });
      _focus.requestFocus();
    } catch (error) {
      if (mounted && generation == _connectionGeneration) {
        setState(() {
          _connecting = false;
          _closed = true;
          _error = productErrorText(error);
        });
      }
    }
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    switch (state) {
      case AppLifecycleState.hidden:
      case AppLifecycleState.paused:
      case AppLifecycleState.detached:
        _suspendForLifecycle();
      case AppLifecycleState.resumed:
        _resumeFromLifecycle();
      case AppLifecycleState.inactive:
        break;
    }
  }

  void _suspendForLifecycle() {
    if (widget.keepLiveInBackgroundResolver?.call() == true) return;
    if (_lifecycleSuspended) return;
    _lifecycleSuspended = true;
    _connectionGeneration++;
    _resizeTimer?.cancel();
    _resizeTimer = null;
    final subscription = _subscription;
    final channel = _channel;
    _rememberCursor(channel);
    _subscription = null;
    _channel = null;
    _input.discard();
    if (mounted) {
      setState(() {
        _connecting = false;
        _closed = true;
        _error = null;
      });
    }
    unawaited(_closeConnection(subscription, channel).catchError((_) {}));
  }

  void _resumeFromLifecycle() {
    if (!_lifecycleSuspended || !mounted) return;
    _lifecycleSuspended = false;
    unawaited(_connect());
  }

  Future<void> _closeConnection(
    StreamSubscription<String>? subscription,
    TerminalChannel? channel,
  ) async {
    // Retired transports are generation-guarded, so their potentially slow
    // cleanup must never hold up the replacement connection.
    if (subscription != null) {
      unawaited(subscription.cancel().catchError((_) {}));
    }
    if (channel != null) {
      unawaited(channel.close().catchError((_) {}));
    }
  }

  Future<void> _closeConnectionBestEffort(
    StreamSubscription<String>? subscription,
    TerminalChannel? channel,
  ) async {
    try {
      await _closeConnection(
        subscription,
        channel,
      ).timeout(const Duration(seconds: 2));
    } catch (_) {
      // A retired transport must not prevent its replacement from connecting.
    }
  }

  /// Everything the terminal sends (typing, the key bar, a paste). Text from
  /// the phone's keyboard takes the key bar's latched Ctrl or Alt.
  void _write(String value) {
    if (!_canWrite) return;
    _input.write(_keys.apply(value));
  }

  void _sendNow(String value) {
    if (!_canWrite) return;
    _channel!.write(value);
  }

  /// Desktop keyboards deliver each key once as a hardware event; routing
  /// printable characters from there (instead of the IME text path) keeps
  /// fast typing from being re-delivered as accumulated deltas.
  KeyEventResult _onTerminalKey(FocusNode node, KeyEvent event) {
    if (!terminalKeyEventText(event)) return KeyEventResult.ignored;
    _write(event.character!);
    return KeyEventResult.handled;
  }

  void _rememberCursor(TerminalChannel? channel) {
    final cursor = channel?.cursor;
    if (cursor != null && cursor >= 0) _terminalCursor = cursor;
  }

  void _appendTranscript(String chunk) {
    final plain = _transcriptSanitizer.add(chunk);
    if (plain.isEmpty) return;
    _transcript = '$_transcript$plain';
    if (_transcript.length > 100000) {
      _transcript = _transcript.substring(_transcript.length - 100000);
    }
    if (_accessibleMode && mounted) setState(() {});
  }

  void _sendAccessibleInput() {
    final value = _accessibleInput.text;
    if (value.isEmpty || !_canWrite) return;
    _write('$value\r');
    _accessibleInput.clear();
  }

  void _resize(int cols, int rows, int pixelWidth, int pixelHeight) {
    if (cols <= 0 || rows <= 0) return;
    _resizeTimer?.cancel();
    _resizeTimer = Timer(const Duration(milliseconds: 150), () {
      if (!mounted || !_canWrite) return;
      final repository = _repository;
      if (repository == null) return;
      unawaited(
        repository
            .resizeTerminal(widget.process.id, rows: rows, cols: cols)
            .catchError((_) {}),
      );
    });
  }

  /// The selection, or the whole readable output when nothing is selected.
  String _copyText() {
    final selection = KitTerminalView.selectedText(
      _terminal,
      _terminalController,
    );
    return selection.isNotEmpty ? selection : _transcript;
  }

  Future<void> _paste() async {
    final data = await Clipboard.getData(Clipboard.kTextPlain);
    final text = data?.text;
    if (text == null || text.isEmpty || !mounted) return;
    _terminal.paste(text);
    _focus.requestFocus();
  }

  Future<void> _rename() async {
    final renamed = await _renameTerminal(
      context,
      widget.process,
      () async => _repository,
    );
    if (renamed != null && mounted) setState(() => _renamed = renamed.trim());
  }

  Future<void> _remove() async {
    final process = widget.process;
    final removed = await _removeTerminal(
      context,
      TerminalProcess(
        id: process.id,
        title: _title,
        command: process.command,
        arguments: process.arguments,
        directory: process.directory,
        running: process.running && !_closed,
        pid: process.pid,
        exitCode: process.exitCode,
      ),
      () async => _repository,
    );
    if (removed && mounted) await Navigator.of(context).maybePop();
  }

  Future<void> _details() {
    final l10n = _l10nOf(context);
    final process = widget.process;
    final command = [process.command, ...process.arguments].join(' ');
    return showKitTechnicalDetails(
      context,
      title: l10n.terminalScreenDetailsTitle(_title),
      text: '',
      values: [
        KitTechnicalValue(l10n.terminalScreenDetailCommand, command),
        if (process.directory.isNotEmpty)
          KitTechnicalValue(l10n.terminalScreenDetailFolder, process.directory),
        KitTechnicalValue(l10n.terminalScreenDetailPid, '${process.pid}'),
        if (process.exitCode != null)
          KitTechnicalValue(
            l10n.terminalScreenDetailExit,
            '${process.exitCode}',
          ),
      ],
    );
  }

  /// The one line, shown only while the terminal is not connected.
  KitStatus? _status(AppLocalizations l10n) {
    final reconnect = KitAction(
      key: const ValueKey('terminal-status-reconnect'),
      label: l10n.e7SetupReconnect,
      onPressed: () => unawaited(_connect()),
    );
    if (_lifecycleSuspended) {
      return KitStatus(
        kind: KitStatusKind.connection,
        id: 'terminal-connection',
        icon: AppIconography.pause,
        message: l10n.terminalScreenPaused,
      );
    }
    if (_connecting) {
      if (!_slowConnect) return null;
      return KitStatus(
        kind: KitStatusKind.connection,
        id: 'terminal-connection',
        icon: AppIconography.sync,
        tone: AppStatusTone.progress,
        message: l10n.terminalScreenConnecting,
        since: _connectingSince,
        onSlow: [reconnect],
      );
    }
    final error = _error;
    if (error != null) {
      return KitStatus(
        kind: KitStatusKind.connection,
        id: 'terminal-connection',
        icon: AppIconography.error,
        tone: AppStatusTone.failure,
        message: setupUiMessage(l10n, error),
        action: reconnect,
      );
    }
    if (_closed) {
      return KitStatus(
        kind: KitStatusKind.connection,
        id: 'terminal-connection',
        icon: AppIconography.cloudOff,
        message: l10n.e7SetupConnectionClosed,
        action: reconnect,
      );
    }
    return null;
  }

  @override
  Widget build(BuildContext context) {
    final l10n = _l10nOf(context);
    final canWrite = _canWrite;
    final running = widget.process.running && !_closed;
    return KitScreen(
      status: _status(l10n),
      loading: _connecting,
      loadingLabel: l10n.terminalScreenConnecting,
      topBar: KitTopBar(
        title: _title,
        menuKey: const ValueKey('terminal-surface-menu'),
        actions: [
          KitAction.copy(
            key: const ValueKey('terminal-copy'),
            label: l10n.terminalScreenCopy,
            text: _copyText,
          ),
        ],
        menu: [
          KitMenuItem(
            key: const Key('terminal-accessible-mode'),
            label: _accessibleMode
                ? l10n.terminalScreenLiveMode
                : l10n.terminalScreenReadableMode,
            icon: _accessibleMode
                ? AppIconography.terminal
                : AppIconography.article,
            onSelected: () =>
                setState(() => _accessibleMode = !_accessibleMode),
          ),
          KitMenuItem(
            key: const ValueKey('terminal-paste'),
            label: l10n.terminalScreenPaste(_title),
            icon: AppIconography.paste,
            enabled: canWrite,
            disabledReason: l10n.e7SetupInputDisconnected,
            onSelected: () => unawaited(_paste()),
          ),
          KitMenuItem(
            key: const Key('terminal-reconnect'),
            label: l10n.e7SetupReconnect,
            icon: AppIconography.retry,
            enabled: !_connecting && !_lifecycleSuspended,
            disabledReason: l10n.terminalScreenConnecting,
            onSelected: () => unawaited(_connect()),
          ),
          KitMenuItem(
            key: const ValueKey('terminal-rename'),
            label: l10n.terminalScreenRename(_title),
            icon: AppIconography.edit,
            onSelected: () => unawaited(_rename()),
          ),
          KitMenuItem(
            key: const ValueKey('terminal-details'),
            label: l10n.terminalScreenDetails,
            icon: AppIconography.info,
            onSelected: () => unawaited(_details()),
          ),
          KitMenuItem(
            key: const ValueKey('terminal-remove'),
            label: running
                ? l10n.terminalScreenStop(_title)
                : l10n.terminalScreenRemove(_title),
            icon: running ? AppIconography.stopCircle : AppIconography.delete,
            destructive: true,
            onSelected: () => unawaited(_remove()),
          ),
        ],
      ),
      body: _accessibleMode
          ? _AccessibleTerminal(
              transcript: _transcript,
              input: _accessibleInput,
              enabled: canWrite,
              onSend: _sendAccessibleInput,
            )
          : KitTerminalView.live(
              terminal: _terminal,
              semanticsLabel: l10n.e7SetupTerminalSemantics,
              controller: _terminalController,
              scrollController: _scrollController,
              focusNode: _focus,
              readOnly: !canWrite,
              keys: _keys,
              interruptKeys: true,
              // Desktop: hardware keys only, so a keystroke is never
              // delivered twice (key event + IME delta).
              onKeyEvent: desktopInteractions ? _onTerminalKey : null,
              keysKey: const ValueKey('terminal-keys'),
            ),
    );
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    widget.repositoryChanges?.removeListener(_repositoryChanged);
    _connectionGeneration++;
    _resizeTimer?.cancel();
    _slowTimer?.cancel();
    unawaited(_subscription?.cancel().catchError((_) {}));
    unawaited(_channel?.close().catchError((_) {}));
    _terminalController.dispose();
    _scrollController.dispose();
    _input.close();
    _focus.dispose();
    _keys.dispose();
    _accessibleInput.dispose();
    super.dispose();
  }
}
