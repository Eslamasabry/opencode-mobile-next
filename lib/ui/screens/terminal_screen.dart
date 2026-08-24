import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:xterm/xterm.dart' as xterm;

import '../../api/product_repository.dart';
import '../../state/connection.dart';
import '../widgets/product_states.dart';

class TerminalScreen extends StatefulWidget {
  final ConnectionController controller;
  const TerminalScreen({super.key, required this.controller});

  @override
  State<TerminalScreen> createState() => _TerminalScreenState();
}

class _TerminalScreenState extends State<TerminalScreen> {
  List<TerminalProcess>? _processes;
  String? _error;
  bool _creating = false;
  ProductRepository? _activeRepository;
  int _locationRevision = -1;
  int _ptyRevision = -1;
  int _loadGeneration = 0;

  ProductRepository? get _repository => widget.controller.repository;

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
    _ptyRevision = widget.controller.ptyRevision;
  }

  void _controllerChanged() {
    final repository = _repository;
    final revision = _revisionOf(repository);
    final ptyRevision = widget.controller.ptyRevision;
    if (identical(repository, _activeRepository) &&
        revision == _locationRevision &&
        ptyRevision == _ptyRevision) {
      return;
    }
    final locationChanged =
        !identical(repository, _activeRepository) ||
        revision != _locationRevision;
    _activeRepository = repository;
    _locationRevision = revision;
    _ptyRevision = ptyRevision;
    _loadGeneration++;
    setState(() {
      _processes = null;
      _error = null;
      if (locationChanged) _creating = false;
    });
    _load();
  }

  int _revisionOf(ProductRepository? repository) => Object.hash(
    widget.controller.locationRevision,
    repository is LocationAwareProductRepository
        ? (repository as LocationAwareProductRepository).locationRevision
        : 0,
  );

  bool _isCurrentLocation(ProductRepository repository, int revision) =>
      mounted &&
      identical(repository, _repository) &&
      revision == _revisionOf(repository);

  Future<void> _load() async {
    final generation = ++_loadGeneration;
    final repository = _repository;
    if (repository == null) {
      setState(() => _error = 'The server is not connected.');
      return;
    }
    setState(() => _error = null);
    try {
      final processes = await repository.listTerminals();
      if (mounted && generation == _loadGeneration) {
        setState(() => _processes = processes);
      }
    } catch (error) {
      if (mounted && generation == _loadGeneration) {
        setState(() => _error = error.toString());
      }
    }
  }

  Future<void> _create() async {
    final repository = _repository;
    if (repository == null || _creating) return;
    final revision = _revisionOf(repository);
    setState(() => _creating = true);
    try {
      final process = await repository.createTerminal(
        title: 'Terminal ${(_processes?.length ?? 0) + 1}',
      );
      if (!_isCurrentLocation(repository, revision)) return;
      await _open(process, repository);
      if (!_isCurrentLocation(repository, revision)) return;
      await _load();
    } catch (error) {
      if (_isCurrentLocation(repository, revision)) {
        _showError(error.toString());
      }
    } finally {
      if (_isCurrentLocation(repository, revision)) {
        setState(() => _creating = false);
      }
    }
  }

  Future<void> _open(
    TerminalProcess process, [
    ProductRepository? repository,
  ]) async {
    await Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => TerminalSurface(
          repository: repository ?? _repository!,
          repositoryResolver: () => widget.controller.repository,
          repositoryChanges: widget.controller,
          process: process,
        ),
      ),
    );
  }

  Future<void> _rename(TerminalProcess process) async {
    final repository = _repository;
    if (repository == null) return;
    final revision = _revisionOf(repository);
    var editedTitle = process.title;
    final title = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Rename terminal'),
        content: TextFormField(
          initialValue: process.title,
          autofocus: true,
          decoration: const InputDecoration(labelText: 'Title'),
          onChanged: (value) => editedTitle = value,
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, editedTitle.trim()),
            child: const Text('Save'),
          ),
        ],
      ),
    );
    if (title?.isNotEmpty != true ||
        !_isCurrentLocation(repository, revision)) {
      return;
    }
    try {
      await repository.renameTerminal(process.id, title!);
      if (!_isCurrentLocation(repository, revision)) return;
      await _load();
    } catch (error) {
      if (_isCurrentLocation(repository, revision)) {
        _showError(error.toString());
      }
    }
  }

  Future<void> _remove(TerminalProcess process) async {
    final repository = _repository;
    if (repository == null) return;
    final revision = _revisionOf(repository);
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(process.running ? 'Stop terminal?' : 'Remove terminal?'),
        content: Text(
          process.running
              ? 'The running process and its child processes will be terminated.'
              : 'This terminal record will be removed.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            style: FilledButton.styleFrom(
              backgroundColor: Theme.of(context).colorScheme.error,
            ),
            onPressed: () => Navigator.pop(context, true),
            child: Text(process.running ? 'Stop' : 'Remove'),
          ),
        ],
      ),
    );
    if (confirmed != true || !_isCurrentLocation(repository, revision)) return;
    try {
      await repository.removeTerminal(process.id);
      if (!_isCurrentLocation(repository, revision)) return;
      await _load();
    } catch (error) {
      if (_isCurrentLocation(repository, revision)) {
        _showError(error.toString());
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_processes == null && _error == null) return const LoadingList();
    if (_error != null && _processes == null) {
      return ProductErrorState(message: _error!, onRetry: _load);
    }
    return Stack(
      children: [
        if (_processes!.isEmpty)
          RefreshIndicator(
            onRefresh: _load,
            child: ProductEmptyState(
              icon: Icons.terminal_rounded,
              title: 'No terminal processes',
              message: 'Start a shell in the active workspace.',
              actionLabel: 'New terminal',
              onAction: _create,
            ),
          )
        else
          RefreshIndicator(
            onRefresh: _load,
            child: ListView.separated(
              physics: const AlwaysScrollableScrollPhysics(),
              padding: const EdgeInsets.only(top: 8, bottom: 92),
              itemCount: _processes!.length,
              separatorBuilder: (_, _) => const Divider(height: 1, indent: 68),
              itemBuilder: (context, index) {
                final process = _processes![index];
                return ListTile(
                  minTileHeight: 68,
                  leading: _ProcessIndicator(running: process.running),
                  title: Text(process.title),
                  subtitle: Text(
                    process.running
                        ? '${process.command} - PID ${process.pid}'
                        : '${process.command} - exited ${process.exitCode ?? ''}',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      fontFamily: 'monospace',
                      fontSize: 12,
                    ),
                  ),
                  trailing: PopupMenuButton<String>(
                    tooltip: 'Terminal actions',
                    onSelected: (value) {
                      if (value == 'rename') _rename(process);
                      if (value == 'remove') _remove(process);
                    },
                    itemBuilder: (context) => [
                      const PopupMenuItem(
                        value: 'rename',
                        child: Text('Rename'),
                      ),
                      PopupMenuItem(
                        value: 'remove',
                        child: Text(process.running ? 'Stop' : 'Remove'),
                      ),
                    ],
                  ),
                  onTap: () => _open(process),
                );
              },
            ),
          ),
        Positioned(
          right: 16,
          bottom: 16,
          child: FloatingActionButton.extended(
            heroTag: 'new-terminal',
            onPressed: _creating ? null : _create,
            icon: _creating
                ? const SizedBox.square(
                    dimension: 18,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(Icons.add_rounded),
            label: const Text('Terminal'),
          ),
        ),
      ],
    );
  }

  void _showError(String message) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(message),
        backgroundColor: Theme.of(context).colorScheme.error,
      ),
    );
  }

  @override
  void dispose() {
    _loadGeneration++;
    widget.controller.removeListener(_controllerChanged);
    super.dispose();
  }
}

class _ProcessIndicator extends StatelessWidget {
  final bool running;
  const _ProcessIndicator({required this.running});

  @override
  Widget build(BuildContext context) {
    final color = running
        ? Theme.of(context).colorScheme.primary
        : Theme.of(context).hintColor;
    return Semantics(
      label: running ? 'Running' : 'Exited',
      child: Container(
        width: 38,
        height: 38,
        decoration: BoxDecoration(
          border: Border.all(color: color.withValues(alpha: .55)),
          borderRadius: BorderRadius.circular(9),
        ),
        child: Icon(Icons.terminal_rounded, size: 20, color: color),
      ),
    );
  }
}

class TerminalSurface extends StatefulWidget {
  final ProductRepository repository;
  final ProductRepository? Function()? repositoryResolver;
  final Listenable? repositoryChanges;
  final TerminalProcess process;

  const TerminalSurface({
    super.key,
    required this.repository,
    this.repositoryResolver,
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
  final _accessibleInput = TextEditingController();
  TerminalChannel? _channel;
  StreamSubscription<String>? _subscription;
  String? _error;
  bool _connecting = true;
  bool _closed = false;
  int _connectionGeneration = 0;
  Timer? _resizeTimer;
  bool _accessibleMode = false;
  bool _lifecycleSuspended = false;
  String _transcript = '';
  final _transcriptSanitizer = _TerminalTranscriptSanitizer();
  ProductRepository? _activeRepository;

  ProductRepository? get _repository => widget.repositoryResolver == null
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
    _repositoryChanged(
      forceReconnect: oldWidget.process.id != widget.process.id,
    );
  }

  void _repositoryChanged({bool forceReconnect = false}) {
    final repository = _repository;
    if (!forceReconnect && identical(repository, _activeRepository)) return;
    _activeRepository = repository;
    if (repository == null) {
      _connectionGeneration++;
      _resizeTimer?.cancel();
      _resizeTimer = null;
      final subscription = _subscription;
      final channel = _channel;
      _subscription = null;
      _channel = null;
      if (mounted && !_lifecycleSuspended) {
        setState(() {
          _connecting = false;
          _closed = true;
          _error = 'The server transport is reconnecting.';
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
      _error = null;
    });
    final previousSubscription = _subscription;
    final previousChannel = _channel;
    _subscription = null;
    _channel = null;
    try {
      await _closeConnectionBestEffort(previousSubscription, previousChannel);
      if (!mounted ||
          _lifecycleSuspended ||
          generation != _connectionGeneration) {
        return;
      }
      if (repository == null) {
        throw StateError('The server transport is reconnecting.');
      }
      _activeRepository = repository;
      _transcriptSanitizer.reset();
      final channel = await repository.connectTerminal(widget.process.id);
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
          }
        },
        onError: (Object error) {
          if (mounted && generation == _connectionGeneration) {
            setState(() {
              _error = error.toString();
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
          _error = error.toString();
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
    if (_lifecycleSuspended) return;
    _lifecycleSuspended = true;
    _connectionGeneration++;
    _resizeTimer?.cancel();
    _resizeTimer = null;
    final subscription = _subscription;
    final channel = _channel;
    _subscription = null;
    _channel = null;
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

  void _write(String value) {
    if (!_canWrite) return;
    _channel!.write(value);
  }

  void _sendControl(String value) {
    _write(value);
    _focus.requestFocus();
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

  Future<void> _copyOutput() async {
    final selection = _terminal.buffer.getText(_terminalController.selection);
    final text = selection.isNotEmpty ? selection : _transcript;
    if (text.isEmpty) return;
    await Clipboard.setData(ClipboardData(text: text));
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final status = _lifecycleSuspended
        ? 'Paused'
        : _connecting
        ? 'Connecting'
        : _error != null
        ? 'Unavailable'
        : _closed
        ? 'Connection closed'
        : 'Connected - PID ${widget.process.pid}';
    final canWrite = _canWrite;
    return Scaffold(
      resizeToAvoidBottomInset: true,
      appBar: AppBar(
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(widget.process.title, overflow: TextOverflow.ellipsis),
            Semantics(
              liveRegion: true,
              label: 'Terminal status: $status',
              excludeSemantics: true,
              child: Text(
                status,
                style: theme.textTheme.labelSmall?.copyWith(
                  color: theme.hintColor,
                ),
              ),
            ),
          ],
        ),
        actions: [
          IconButton(
            tooltip: 'Copy terminal selection or transcript',
            onPressed: _copyOutput,
            icon: const Icon(Icons.copy_rounded),
          ),
          IconButton(
            key: const Key('terminal-accessible-mode'),
            tooltip: _accessibleMode
                ? 'Use interactive terminal'
                : 'Use accessible transcript and input',
            onPressed: () => setState(() => _accessibleMode = !_accessibleMode),
            icon: Icon(
              _accessibleMode
                  ? Icons.terminal_rounded
                  : Icons.accessibility_new_rounded,
            ),
          ),
          IconButton(
            key: const Key('terminal-reconnect'),
            tooltip: 'Reconnect',
            onPressed: _connecting || _lifecycleSuspended ? null : _connect,
            icon: const Icon(Icons.refresh_rounded),
          ),
        ],
      ),
      body: Column(
        children: [
          if (_connecting) const LinearProgressIndicator(minHeight: 2),
          if (_error != null)
            MaterialBanner(
              content: Text(_error!),
              actions: [
                TextButton(onPressed: _connect, child: const Text('Retry')),
              ],
            ),
          Expanded(
            child: _accessibleMode
                ? _AccessibleTerminal(
                    transcript: _transcript,
                    input: _accessibleInput,
                    enabled: canWrite,
                    onSend: _sendAccessibleInput,
                  )
                : ColoredBox(
                    color: const Color(0xFF0A0C0F),
                    child: Semantics(
                      label:
                          'Interactive terminal. Use the accessibility button for a readable transcript and labeled input.',
                      child: ExcludeSemantics(
                        child: xterm.TerminalView(
                          _terminal,
                          controller: _terminalController,
                          scrollController: _scrollController,
                          focusNode: _focus,
                          autofocus: true,
                          autoResize: true,
                          keyboardType: TextInputType.text,
                          keyboardAppearance: Brightness.dark,
                          deleteDetection: true,
                          hardwareKeyboardOnly: false,
                          readOnly: !canWrite,
                          padding: const EdgeInsets.all(10),
                          textStyle: const xterm.TerminalStyle(
                            fontFamily: 'monospace',
                            fontSize: 12.5,
                          ),
                        ),
                      ),
                    ),
                  ),
          ),
          SafeArea(
            top: false,
            child: Semantics(
              container: true,
              label: 'Terminal control keys. Swipe horizontally for more.',
              child: SizedBox(
                height: 56,
                child: ListView(
                  key: const Key('terminal-control-strip'),
                  scrollDirection: Axis.horizontal,
                  padding: const EdgeInsets.symmetric(
                    horizontal: 8,
                    vertical: 4,
                  ),
                  children: [
                    _TerminalKey(
                      label: 'Ctrl-C',
                      semanticLabel: 'Interrupt, Control C',
                      onTap: canWrite ? () => _sendControl('\x03') : null,
                    ),
                    _TerminalKey(
                      label: 'Ctrl-D',
                      semanticLabel: 'End of input, Control D',
                      onTap: canWrite ? () => _sendControl('\x04') : null,
                    ),
                    _TerminalKey(
                      label: 'Esc',
                      semanticLabel: 'Escape key',
                      onTap: canWrite ? () => _sendControl('\x1b') : null,
                    ),
                    _TerminalKey(
                      label: 'Tab',
                      semanticLabel: 'Tab key',
                      onTap: canWrite ? () => _sendControl('\t') : null,
                    ),
                    _TerminalKey(
                      label: '↑',
                      semanticLabel: 'Up arrow key',
                      onTap: canWrite ? () => _sendControl('\x1b[A') : null,
                    ),
                    _TerminalKey(
                      label: '↓',
                      semanticLabel: 'Down arrow key',
                      onTap: canWrite ? () => _sendControl('\x1b[B') : null,
                    ),
                    _TerminalKey(
                      label: '←',
                      semanticLabel: 'Left arrow key',
                      onTap: canWrite ? () => _sendControl('\x1b[D') : null,
                    ),
                    _TerminalKey(
                      label: '→',
                      semanticLabel: 'Right arrow key',
                      onTap: canWrite ? () => _sendControl('\x1b[C') : null,
                    ),
                  ],
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    widget.repositoryChanges?.removeListener(_repositoryChanged);
    _connectionGeneration++;
    _resizeTimer?.cancel();
    unawaited(_subscription?.cancel().catchError((_) {}));
    unawaited(_channel?.close().catchError((_) {}));
    _terminalController.dispose();
    _scrollController.dispose();
    _focus.dispose();
    _accessibleInput.dispose();
    super.dispose();
  }
}

class _TerminalKey extends StatelessWidget {
  final String label;
  final String semanticLabel;
  final VoidCallback? onTap;

  const _TerminalKey({
    required this.label,
    required this.semanticLabel,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.symmetric(horizontal: 2),
    child: Semantics(
      button: true,
      enabled: onTap != null,
      label: semanticLabel,
      hint: onTap == null
          ? 'Unavailable while the terminal is disconnected'
          : 'Sends this key to the terminal',
      excludeSemantics: true,
      child: ConstrainedBox(
        constraints: const BoxConstraints(minWidth: 48, minHeight: 48),
        child: OutlinedButton(onPressed: onTap, child: Text(label)),
      ),
    ),
  );
}

class _AccessibleTerminal extends StatelessWidget {
  final String transcript;
  final TextEditingController input;
  final bool enabled;
  final VoidCallback onSend;

  const _AccessibleTerminal({
    required this.transcript,
    required this.input,
    required this.enabled,
    required this.onSend,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(12),
      child: Column(
        children: [
          Expanded(
            child: Semantics(
              label: 'Terminal transcript',
              textField: true,
              readOnly: true,
              child: Container(
                width: double.infinity,
                padding: const EdgeInsets.all(10),
                color: const Color(0xFF0A0C0F),
                child: SingleChildScrollView(
                  reverse: true,
                  child: SelectableText(
                    transcript.isEmpty ? 'No terminal output yet.' : transcript,
                    style: const TextStyle(fontFamily: 'monospace'),
                  ),
                ),
              ),
            ),
          ),
          const SizedBox(height: 8),
          Row(
            children: [
              Expanded(
                child: TextField(
                  key: const Key('terminal-accessible-input'),
                  controller: input,
                  enabled: enabled,
                  decoration: InputDecoration(
                    labelText: 'Terminal command input',
                    hintText: 'Type a command',
                    helperText: enabled
                        ? null
                        : 'Input is unavailable while disconnected.',
                    border: const OutlineInputBorder(),
                  ),
                  textInputAction: TextInputAction.send,
                  onSubmitted: (_) => onSend(),
                ),
              ),
              const SizedBox(width: 8),
              IconButton.filled(
                tooltip: enabled
                    ? 'Send command to terminal'
                    : 'Terminal input unavailable',
                onPressed: enabled ? onSend : null,
                icon: const Icon(Icons.keyboard_return_rounded),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

/// Converts a stream of terminal bytes decoded as text into a readable,
/// append-only transcript. Terminal rendering commands are intentionally not
/// exposed to assistive technology.
class _TerminalTranscriptSanitizer {
  static const _normal = 0;
  static const _escape = 1;
  static const _escapeIntermediate = 2;
  static const _csi = 3;
  static const _osc = 4;
  static const _oscEscape = 5;
  static const _controlString = 6;
  static const _controlStringEscape = 7;

  int _state = _normal;
  bool _pendingCarriageReturn = false;

  void reset() {
    _state = _normal;
    _pendingCarriageReturn = false;
  }

  String add(String chunk) {
    // OpenCode prefixes binary WebSocket metadata frames with NUL. The
    // repository decodes binary frames to strings, so keep them out of the
    // human-readable transcript regardless of their JSON payload shape.
    if (chunk.isEmpty || (_state == _normal && chunk.startsWith('\x00'))) {
      return '';
    }
    final output = StringBuffer();

    for (final rune in chunk.runes) {
      if (_state == _normal && _pendingCarriageReturn) {
        if (rune == 0x0a) {
          output.write('\n');
          _pendingCarriageReturn = false;
          continue;
        }
        output.write('\n');
        _pendingCarriageReturn = false;
      }

      switch (_state) {
        case _normal:
          if (rune == 0x1b) {
            _state = _escape;
          } else if (rune == 0x9b) {
            _state = _csi;
          } else if (rune == 0x9d) {
            _state = _osc;
          } else if (rune == 0x90 ||
              rune == 0x98 ||
              rune == 0x9e ||
              rune == 0x9f) {
            _state = _controlString;
          } else if (rune == 0x0d) {
            _pendingCarriageReturn = true;
          } else if (rune == 0x0a || rune == 0x09) {
            output.writeCharCode(rune);
          } else if ((rune >= 0x20 && rune != 0x7f) &&
              !(rune >= 0x80 && rune <= 0x9f)) {
            output.writeCharCode(rune);
          }
        case _escape:
          if (rune == 0x5b) {
            _state = _csi;
          } else if (rune == 0x5d) {
            _state = _osc;
          } else if (rune == 0x50 ||
              rune == 0x58 ||
              rune == 0x5e ||
              rune == 0x5f) {
            _state = _controlString;
          } else if (rune >= 0x20 && rune <= 0x2f) {
            _state = _escapeIntermediate;
          } else if (rune != 0x1b) {
            _state = _normal;
          }
        case _escapeIntermediate:
          if (rune == 0x1b) {
            _state = _escape;
          } else if (rune >= 0x30 && rune <= 0x7e) {
            _state = _normal;
          } else if (rune < 0x20 || rune > 0x2f) {
            _state = _normal;
          }
        case _csi:
          if (rune == 0x1b) {
            _state = _escape;
          } else if (rune >= 0x40 && rune <= 0x7e) {
            _state = _normal;
          }
        case _osc:
          if (rune == 0x07 || rune == 0x9c) {
            _state = _normal;
          } else if (rune == 0x1b) {
            _state = _oscEscape;
          }
        case _oscEscape:
          if (rune == 0x5c) {
            _state = _normal;
          } else if (rune != 0x1b) {
            _state = _osc;
          }
        case _controlString:
          if (rune == 0x9c) {
            _state = _normal;
          } else if (rune == 0x1b) {
            _state = _controlStringEscape;
          }
        case _controlStringEscape:
          if (rune == 0x5c) {
            _state = _normal;
          } else if (rune != 0x1b) {
            _state = _controlString;
          }
      }
    }
    return output.toString();
  }
}
