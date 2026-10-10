part of '../gateway.dart';

/// Terminals the daemon runs in the open folder: list, open, type, resize,
/// rename and close (`list_terminals_request`, `create_terminal_request`,
/// `subscribe_terminal_request`, `terminal_input`, `kill_terminal_request`,
/// `terminal.rename.request`). Output arrives as binary frames on the same
/// socket ([PaseoTransport.binaryFrames]).
mixin _PaseoTerminalsApi on _PaseoWorkspaceBase {
  Future<List<TerminalProcess>> listTerminals() async {
    final payload = await _workspaceRequest('list_terminals_request', {
      'cwd': _scope,
    });
    return [
      for (final raw in paseoList(payload['terminals'], max: 1000))
        _terminalProcess(paseoObject(raw)),
    ];
  }

  TerminalProcess _terminalProcess(Map<String, dynamic> terminal) {
    final id = paseoString(terminal['id'], max: 256);
    final title = terminal['title'];
    final name = terminal['name'];
    final cwd = terminal['cwd'];
    final directory = cwd is String && cwd.isNotEmpty ? cwd : _scope;
    return TerminalProcess(
      id: id,
      title: title is String && title.trim().isNotEmpty
          ? title.trim()
          : (name is String && name.isNotEmpty ? name : id),
      // The row says where it runs ("Running · app"): the daemon names no
      // command.
      command: _folderName(directory),
      arguments: const [],
      directory: directory,
      // The daemon lists a terminal only while its shell runs.
      running: true,
      pid: 0,
    );
  }

  Future<TerminalProcess> createTerminal({String? title}) async {
    final name = title?.trim() ?? '';
    final payload = await _workspaceRequest('create_terminal_request', {
      'cwd': _scope,
      if (name.isNotEmpty) 'name': name,
    }, mutation: true);
    return _terminalProcess(paseoObject(payload['terminal']));
  }

  Future<void> renameTerminal(String id, String title) async {
    final payload = await _workspaceRequest('terminal.rename.request', {
      'terminalId': paseoString(id, max: 256),
      'title': title.trim(),
    }, mutation: true);
    if (payload['success'] != true) {
      throw PaseoFailure(PaseoFailureKind.unavailable);
    }
  }

  Future<void> resizeTerminal(
    String id, {
    required int rows,
    required int cols,
  }) async {
    if (rows <= 0 || cols <= 0) return;
    transport.send('terminal_input', {
      'terminalId': paseoString(id, max: 256),
      'message': {'type': 'resize', 'rows': rows, 'cols': cols},
    });
  }

  Future<void> removeTerminal(String id) async {
    final payload = await _workspaceRequest('kill_terminal_request', {
      'terminalId': paseoString(id, max: 256),
    }, mutation: true);
    if (payload['success'] == true) return;
    // A shell that already ended is gone, which is what was asked for.
    final left = await listTerminals();
    if (left.any((terminal) => terminal.id == id)) {
      throw PaseoFailure(PaseoFailureKind.unavailable);
    }
  }

  /// Opens the live output of terminal [id]. The daemon first sends what the
  /// screen shows now (its restore frame), so every connect, including a
  /// reconnect, starts from a reset screen and the current state.
  Future<TerminalChannel> connectTerminal(String id, {int? cursor}) async {
    final terminalId = paseoString(id, max: 256);
    final scope = _scope;
    final epoch = _locationEpoch;
    // Listen before asking: the restore frame can follow the answer at once.
    final channel = _PaseoTerminalChannel(transport, terminalId);
    try {
      final payload = await transport.request('subscribe_terminal_request', {
        'terminalId': terminalId,
        'restore': {'mode': 'visible-snapshot', 'scrollbackLines': 500},
      }, mutation: true);
      _checkLocation(scope, epoch);
      final slot = payload['slot'];
      if (slot is! int || slot < 0 || slot > 255) {
        throw PaseoFailure(PaseoFailureKind.invalidResponse);
      }
      channel.attach(transport.epoch, slot);
      return channel;
    } catch (_) {
      await channel.close();
      rethrow;
    }
  }
}

/// A terminal's output as text. Frames of other slots and connections are
/// ignored; a lost connection or an exited shell ends the stream, which the
/// terminal page shows as closed with a way to reconnect.
class _PaseoTerminalChannel implements TerminalChannel {
  _PaseoTerminalChannel(this._transport, this._terminalId) {
    _frames = _transport.binaryFrames.listen(_onFrame);
    _events = _transport.events.listen((event) {
      if (event.type == 'terminal_stream_exit' &&
          event.payload['terminalId'] == _terminalId) {
        unawaited(close());
      }
    });
    _disconnects = _transport.disconnects.listen((_) => unawaited(close()));
    _decoder = utf8.decoder.startChunkedConversion(
      _TextSink((text) {
        if (!_output.isClosed && text.isNotEmpty) _output.add(text);
      }),
    );
  }

  static const _outputOpcode = 0x01;
  static const _restoreOpcode = 0x05;

  final PaseoTransport _transport;
  final String _terminalId;
  final _output = StreamController<String>();
  late final StreamSubscription<PaseoBinaryFrame> _frames;
  late final StreamSubscription<PaseoEvent> _events;
  late final StreamSubscription<int> _disconnects;
  late final Sink<List<int>> _decoder;
  int? _epoch;
  int? _slot;
  bool _closed = false;

  /// Frames that beat the daemon's answer; kept until the slot is known.
  final _early = <PaseoBinaryFrame>[];

  void attach(int epoch, int slot) {
    _epoch = epoch;
    _slot = slot;
    final early = List.of(_early);
    _early.clear();
    early.forEach(_onFrame);
  }

  void _onFrame(PaseoBinaryFrame frame) {
    if (_closed) return;
    if (_slot == null) {
      if (_early.length < 64) _early.add(frame);
      return;
    }
    if (frame.slot != _slot || frame.epoch != _epoch) return;
    if (frame.opcode == _restoreOpcode) {
      // The restore replaces the screen: reset it, then replay.
      _output.add('\x1bc');
      _decoder.add(frame.payload);
    } else if (frame.opcode == _outputOpcode) {
      _decoder.add(frame.payload);
    }
  }

  @override
  Stream<String> get output => _output.stream;

  @override
  int? get cursor => null;

  @override
  void write(String value) {
    if (_closed || value.isEmpty) return;
    _transport.send('terminal_input', {
      'terminalId': _terminalId,
      'message': {'type': 'input', 'data': value},
    }, expectedEpoch: _epoch);
  }

  @override
  Future<void> close() async {
    if (_closed) return;
    _closed = true;
    final slot = _slot;
    if (slot != null && _transport.connected && _transport.epoch == _epoch) {
      try {
        _transport.send('unsubscribe_terminal_request', {
          'terminalId': _terminalId,
        }, expectedEpoch: _epoch);
      } catch (_) {
        // The connection is going away; the daemon drops the stream with it.
      }
    }
    await _frames.cancel();
    await _events.cancel();
    await _disconnects.cancel();
    // A close with no listener never completes; the page listens, but a
    // failed open has no one to.
    if (!_output.isClosed) unawaited(_output.close());
  }
}

class _TextSink implements Sink<String> {
  _TextSink(this._onText);

  final void Function(String text) _onText;

  @override
  void add(String data) => _onText(data);

  @override
  void close() {}
}
