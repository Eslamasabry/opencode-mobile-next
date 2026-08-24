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
    _activeRepository = repository;
    _locationRevision = revision;
    _ptyRevision = ptyRevision;
    _loadGeneration++;
    setState(() {
      _processes = null;
      _error = null;
    });
    _load();
  }

  int _revisionOf(ProductRepository? repository) => Object.hash(
    widget.controller.locationRevision,
    repository is LocationAwareProductRepository
        ? (repository as LocationAwareProductRepository).locationRevision
        : 0,
  );

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
    setState(() => _creating = true);
    try {
      final process = await repository.createTerminal(
        title: 'Terminal ${(_processes?.length ?? 0) + 1}',
      );
      if (!mounted) return;
      await _open(process);
      await _load();
    } catch (error) {
      if (mounted) _showError(error.toString());
    } finally {
      if (mounted) setState(() => _creating = false);
    }
  }

  Future<void> _open(TerminalProcess process) async {
    await Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) =>
            TerminalSurface(repository: _repository!, process: process),
      ),
    );
  }

  Future<void> _rename(TerminalProcess process) async {
    final controller = TextEditingController(text: process.title);
    final title = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Rename terminal'),
        content: TextField(
          controller: controller,
          autofocus: true,
          decoration: const InputDecoration(labelText: 'Title'),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, controller.text.trim()),
            child: const Text('Save'),
          ),
        ],
      ),
    );
    controller.dispose();
    if (title?.isNotEmpty != true) return;
    try {
      await _repository?.renameTerminal(process.id, title!);
      await _load();
    } catch (error) {
      if (mounted) _showError(error.toString());
    }
  }

  Future<void> _remove(TerminalProcess process) async {
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
    if (confirmed != true) return;
    try {
      await _repository?.removeTerminal(process.id);
      await _load();
    } catch (error) {
      if (mounted) _showError(error.toString());
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
  final TerminalProcess process;

  const TerminalSurface({
    super.key,
    required this.repository,
    required this.process,
  });

  @override
  State<TerminalSurface> createState() => _TerminalSurfaceState();
}

class _TerminalSurfaceState extends State<TerminalSurface> {
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
  String _transcript = '';

  @override
  void initState() {
    super.initState();
    _terminal = xterm.Terminal(
      maxLines: 5000,
      onOutput: _write,
      onResize: _resize,
    );
    _connect();
  }

  Future<void> _connect() async {
    final generation = ++_connectionGeneration;
    setState(() {
      _connecting = true;
      _error = null;
    });
    try {
      await _subscription?.cancel();
      await _channel?.close();
      final channel = await widget.repository.connectTerminal(
        widget.process.id,
      );
      if (!mounted || generation != _connectionGeneration) {
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
          _error = error.toString();
        });
      }
    }
  }

  void _write(String value) {
    if (_connecting || _closed) return;
    _channel?.write(value);
  }

  void _sendControl(String value) {
    _write(value);
    _focus.requestFocus();
  }

  void _appendTranscript(String chunk) {
    final plain = chunk
        .replaceAll(RegExp(r'\x1B\[[0-?]*[ -/]*[@-~]'), '')
        .replaceAll('\r', '');
    _transcript = '$_transcript$plain';
    if (_transcript.length > 100000) {
      _transcript = _transcript.substring(_transcript.length - 100000);
    }
    if (_accessibleMode && mounted) setState(() {});
  }

  void _sendAccessibleInput() {
    final value = _accessibleInput.text;
    if (value.isEmpty) return;
    _write('$value\r');
    _accessibleInput.clear();
  }

  void _resize(int cols, int rows, int pixelWidth, int pixelHeight) {
    if (cols <= 0 || rows <= 0) return;
    _resizeTimer?.cancel();
    _resizeTimer = Timer(const Duration(milliseconds: 150), () {
      if (!mounted || _closed) return;
      unawaited(
        widget.repository
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
    return Scaffold(
      resizeToAvoidBottomInset: true,
      appBar: AppBar(
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(widget.process.title, overflow: TextOverflow.ellipsis),
            Text(
              _connecting
                  ? 'Connecting'
                  : _closed
                  ? 'Exited'
                  : 'PID ${widget.process.pid}',
              style: theme.textTheme.labelSmall?.copyWith(
                color: theme.hintColor,
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
            onPressed: _connecting ? null : _connect,
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
                    enabled: !_connecting && !_closed,
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
                          readOnly: _connecting || _closed,
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
            child: SizedBox(
              height: 56,
              child: ListView(
                scrollDirection: Axis.horizontal,
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                children: [
                  _TerminalKey(
                    label: 'Ctrl-C',
                    semanticLabel: 'Interrupt, Control C',
                    onTap: () => _sendControl('\x03'),
                  ),
                  _TerminalKey(
                    label: 'Ctrl-D',
                    semanticLabel: 'End of input, Control D',
                    onTap: () => _sendControl('\x04'),
                  ),
                  _TerminalKey(
                    label: 'Esc',
                    semanticLabel: 'Escape key',
                    onTap: () => _sendControl('\x1b'),
                  ),
                  _TerminalKey(
                    label: 'Tab',
                    semanticLabel: 'Tab key',
                    onTap: () => _sendControl('\t'),
                  ),
                  _TerminalKey(
                    label: '↑',
                    semanticLabel: 'Up arrow key',
                    onTap: () => _sendControl('\x1b[A'),
                  ),
                  _TerminalKey(
                    label: '↓',
                    semanticLabel: 'Down arrow key',
                    onTap: () => _sendControl('\x1b[B'),
                  ),
                  _TerminalKey(
                    label: '←',
                    semanticLabel: 'Left arrow key',
                    onTap: () => _sendControl('\x1b[D'),
                  ),
                  _TerminalKey(
                    label: '→',
                    semanticLabel: 'Right arrow key',
                    onTap: () => _sendControl('\x1b[C'),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  @override
  void dispose() {
    _connectionGeneration++;
    _resizeTimer?.cancel();
    _subscription?.cancel();
    unawaited(_channel?.close());
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
  final VoidCallback onTap;

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
      label: semanticLabel,
      excludeSemantics: true,
      child: SizedBox(
        height: 48,
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
                  decoration: const InputDecoration(
                    labelText: 'Terminal command input',
                    hintText: 'Type a command',
                    border: OutlineInputBorder(),
                  ),
                  textInputAction: TextInputAction.send,
                  onSubmitted: (_) => onSend(),
                ),
              ),
              const SizedBox(width: 8),
              IconButton.filled(
                tooltip: 'Send command to terminal',
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
