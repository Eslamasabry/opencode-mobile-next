import 'dart:async';
import 'dart:convert';

import 'acp_models.dart';
export 'acp_models.dart';

enum AcpFailure {
  closed,
  transport,
  timeout,
  protocol,
  version,
  rpc,
  capacity,
  state,
}

/// Safe to report: excludes agent error messages, data, paths and credentials.
class AcpException implements Exception {
  const AcpException(this.failure, {this.rpcCode});
  final AcpFailure failure;
  final int? rpcCode;
  @override
  String toString() =>
      'ACP ${failure.name} failure'
      '${rpcCode == null ? '' : ' (code $rpcCode)'}';
}

class _Pending {
  _Pending(this.completer, this.timer);
  final Completer<AcpJson> completer;
  final Timer timer;
}

class _Permission {
  _Permission(this.sessionId);
  final String sessionId;
}

/// Isolated ACP v1 feasibility client; no networking, processes or persistence.
///
/// The caller supplies an ordered byte stream and a byte writer that completes
/// when a frame is accepted. Outgoing writes are serialized. This client never
/// retries a prompt: after timeout or transport loss its outcome is unknown.
/// Listen to [updates] before prompting; notifications are volatile, not replay.
/// The caller owns and closes its transport. [dispose] only stops this client.
/// FS and terminal capabilities are deliberately false. Permissions default to
/// cancelled; a callback may select only an offered, recognized option ID.
class AcpClient {
  AcpClient(
    Stream<List<int>> input,
    this._write, {
    this.requestTimeout = const Duration(seconds: 30),
    this.maxFrameBytes = 1024 * 1024,
    this.onPermission,
  }) {
    if (requestTimeout <= Duration.zero || maxFrameBytes < 1) {
      throw ArgumentError('ACP limits must be positive');
    }
    _subscription = input.listen(
      _receive,
      onError: (Object error) =>
          _fail(const AcpException(AcpFailure.transport)),
      onDone: () => _fail(const AcpException(AcpFailure.closed)),
    );
  }

  final Future<void> Function(List<int>) _write;
  final Duration requestTimeout;
  final int maxFrameBytes;
  final Future<String?> Function(AcpPermissionRequest)? onPermission;
  final _updates = StreamController<AcpSessionUpdate>.broadcast();
  final _pending = <int, _Pending>{};
  final _permissions = <Object, _Permission>{};
  final _sessions = <String>{};
  final _activePrompts = <String>{};
  final _cancelled = <String>{};
  final _frame = <int>[];
  late final StreamSubscription<List<int>> _subscription;
  Future<void> _writeTail = Future.value();
  Future<void>? _inputStopped;
  int _queuedWrites = 0;
  int _nextId = 0;
  bool _closed = false;
  bool _initializing = false;
  AcpInitializeResult? _initialized;

  Stream<AcpSessionUpdate> get updates => _updates.stream;

  Future<AcpInitializeResult> initialize() async {
    if (_initialized != null || _initializing) {
      throw const AcpException(AcpFailure.state);
    }
    _initializing = true;
    try {
      final result = _parse(
        await _request('initialize', {
          'protocolVersion': 1,
          'clientCapabilities': {
            'fs': {'readTextFile': false, 'writeTextFile': false},
            'terminal': false,
          },
          'clientInfo': {'name': 'opencode-mobile-acp-spike', 'version': '0.1'},
        }),
        AcpInitializeResult.fromJson,
      );
      if (result.protocolVersion != 1) {
        const error = AcpException(AcpFailure.version);
        _fail(error);
        throw error;
      }
      return _initialized = result;
    } finally {
      _initializing = false;
    }
  }

  Future<AcpSession> newSession({required String cwd}) async {
    _requireReady();
    if (!cwd.startsWith('/') || cwd.contains('\u0000')) {
      throw ArgumentError('ACP cwd must be an absolute host Unix path');
    }
    final session = _parse(
      await _request('session/new', {'cwd': cwd, 'mcpServers': <Object>[]}),
      AcpSession.fromJson,
    );
    _sessions.add(session.sessionId);
    return session;
  }

  Future<AcpPromptResult> prompt({
    required String sessionId,
    required String text,
  }) async {
    _requireSession(sessionId);
    if (!_activePrompts.add(sessionId)) {
      throw const AcpException(AcpFailure.state);
    }
    _cancelled.remove(sessionId);
    try {
      final result = _parse(
        await _request('session/prompt', {
          'sessionId': sessionId,
          'prompt': [
            {'type': 'text', 'text': text},
          ],
        }),
        AcpPromptResult.fromJson,
      );
      return result;
    } finally {
      _activePrompts.remove(sessionId);
      await _cancelPermissions(sessionId);
    }
  }

  Future<void> cancel(String sessionId) async {
    _requireSession(sessionId);
    _cancelled.add(sessionId);
    await _cancelPermissions(sessionId);
    await _send({
      'jsonrpc': '2.0',
      'method': 'session/cancel',
      'params': {'sessionId': sessionId},
    });
  }

  /// Explicit only; no method is chosen or authentication initiated implicitly.
  Future<void> authenticate(String methodId) async {
    _requireReady();
    if (!_initialized!.authMethods.any(
      (method) =>
          method.id == methodId &&
          (method.fields['type'] == null || method.fields['type'] == 'agent'),
    )) {
      throw const AcpException(AcpFailure.state);
    }
    await _request('authenticate', {'methodId': methodId});
  }

  void _requireReady() {
    if (_closed) throw const AcpException(AcpFailure.closed);
    if (_initialized == null) throw const AcpException(AcpFailure.state);
  }

  void _requireSession(String sessionId) {
    _requireReady();
    if (!_sessions.contains(sessionId)) {
      throw const AcpException(AcpFailure.state);
    }
  }

  T _parse<T>(AcpJson json, T Function(AcpJson) decode) {
    try {
      return decode(json);
    } catch (_) {
      const error = AcpException(AcpFailure.protocol);
      _fail(error);
      throw error;
    }
  }

  Future<AcpJson> _request(String method, AcpJson params) {
    if (_closed) return Future.error(const AcpException(AcpFailure.closed));
    if (_pending.length >= 64) {
      return Future.error(const AcpException(AcpFailure.capacity));
    }
    final id = _nextId++;
    final completer = Completer<AcpJson>();
    final timer = Timer(requestTimeout, () {
      // Abandon the entire connection: an expired prompt cannot be retried.
      _fail(const AcpException(AcpFailure.timeout));
    });
    _pending[id] = _Pending(completer, timer);
    unawaited(
      _send({
        'jsonrpc': '2.0',
        'id': id,
        'method': method,
        'params': params,
      }).catchError((Object error) {
        _fail(const AcpException(AcpFailure.transport));
      }),
    );
    return completer.future;
  }

  Future<void> _send(AcpJson message) async {
    if (_closed) throw const AcpException(AcpFailure.closed);
    if (_queuedWrites >= 64) {
      const error = AcpException(AcpFailure.capacity);
      _fail(error);
      throw error;
    }
    final bytes = utf8.encode('${jsonEncode(message)}\n');
    if (bytes.length - 1 > maxFrameBytes) {
      const error = AcpException(AcpFailure.capacity);
      _fail(error);
      throw error;
    }
    _queuedWrites++;
    final next = _writeTail.then((_) async {
      if (_closed) throw const AcpException(AcpFailure.closed);
      await _write(bytes).timeout(requestTimeout);
    });
    // Catch before publishing the tail: no failed write is left unobserved.
    _writeTail = next.then<void>(
      (_) {},
      onError: (Object error) {
        _fail(const AcpException(AcpFailure.transport));
      },
    );
    try {
      await next;
    } catch (_) {
      throw const AcpException(AcpFailure.transport);
    } finally {
      _queuedWrites--;
    }
  }

  void _receive(List<int> bytes) {
    if (_closed) return;
    try {
      for (final byte in bytes) {
        if (byte == 10) {
          if (_frame.isEmpty) {
            throw const FormatException('Empty ACP frame');
          }
          final frame = utf8.decode(_frame);
          _frame.clear();
          _dispatch(acpObject(jsonDecode(frame)));
          if (_closed) return;
        } else {
          if (_frame.length >= maxFrameBytes || byte < 0 || byte > 255) {
            _fail(const AcpException(AcpFailure.capacity));
            return;
          }
          _frame.add(byte);
        }
      }
    } catch (_) {
      _fail(const AcpException(AcpFailure.protocol));
    }
  }

  void _dispatch(AcpJson message) {
    if (message['jsonrpc'] != '2.0') {
      throw const FormatException('Invalid ACP envelope');
    }
    final id = message['id'];
    final method = message['method'];
    if (method != null) {
      if (method is! String) {
        throw const FormatException('Invalid ACP method');
      }
      final params = acpObject(message['params'] ?? <String, Object?>{});
      if (id == null) {
        if (method == 'session/update') {
          _updates.add(AcpSessionUpdate.fromJson(params));
        }
      } else {
        if (id is! int && id is! String) {
          throw const FormatException('Invalid ACP id');
        }
        if (method == 'session/request_permission') {
          _permission(id, AcpPermissionRequest.fromJson(params));
        } else {
          unawaited(
            _send({
              'jsonrpc': '2.0',
              'id': id,
              'error': {'code': -32601, 'message': 'Method not supported'},
            }).catchError((Object error) {}),
          );
        }
      }
      return;
    }
    if (id is! int) throw const FormatException('Invalid ACP response');
    final pending = _pending[id];
    if (pending == null) return; // Late/unknown responses have no side effects.
    if (message.containsKey('result') == message.containsKey('error')) {
      throw const FormatException('Invalid ACP response');
    }
    // Validate before removing: malformed results must still fail the waiter.
    final result = message.containsKey('result')
        ? acpObject(message['result'])
        : null;
    final error = message.containsKey('error')
        ? acpObject(message['error'])
        : null;
    if (error != null && error['code'] is! int) {
      throw const FormatException('Invalid ACP error');
    }
    _pending.remove(id);
    pending.timer.cancel();
    if (error != null) {
      pending.completer.completeError(
        AcpException(AcpFailure.rpc, rpcCode: error['code'] as int),
      );
    } else {
      pending.completer.complete(result!);
    }
  }

  void _permission(Object id, AcpPermissionRequest request) {
    if (_permissions.containsKey(id) || _permissions.length >= 64) {
      _fail(const AcpException(AcpFailure.capacity));
      return;
    }
    final permission = _Permission(request.sessionId);
    _permissions[id] = permission;
    unawaited(
      () async {
        String? selected;
        if (_activePrompts.contains(request.sessionId) &&
            !_cancelled.contains(request.sessionId) &&
            onPermission != null) {
          try {
            selected = await onPermission!(request).timeout(requestTimeout);
          } catch (_) {
            // Handler failures/timeouts are denials, never wire/log errors.
          }
        }
        if (_closed || !identical(_permissions[id], permission)) return;
        final matches = request.options.where(
          (option) => option.optionId == selected && option.isKnownKind,
        );
        if (_cancelled.contains(request.sessionId) || matches.length != 1) {
          selected = null;
        }
        _permissions.remove(id);
        await _replyPermission(id, selected);
      }().catchError((Object error) {}),
    );
  }

  Future<void> _replyPermission(Object id, String? selected) => _send({
    'jsonrpc': '2.0',
    'id': id,
    'result': {
      'outcome': {
        'outcome': selected == null ? 'cancelled' : 'selected',
        'optionId': ?selected,
      },
    },
  });

  Future<void> _cancelPermissions(String sessionId) async {
    final ids = _permissions.entries
        .where((entry) => entry.value.sessionId == sessionId)
        .map((entry) => entry.key)
        .toList();
    for (final id in ids) {
      _permissions.remove(id);
      if (!_closed) await _replyPermission(id, null);
    }
  }

  void _fail(AcpException error) {
    if (_closed) return;
    _closed = true;
    _frame.clear();
    _permissions.clear();
    _sessions.clear();
    _activePrompts.clear();
    for (final pending in _pending.values) {
      pending.timer.cancel();
      pending.completer.completeError(error);
    }
    _pending.clear();
    _inputStopped = _subscription.cancel().catchError((Object error) {});
    unawaited(_updates.close());
  }

  Future<void> dispose() async {
    _fail(const AcpException(AcpFailure.closed));
    try {
      await _inputStopped?.timeout(requestTimeout);
    } catch (_) {
      // Transport cancellation failures must not leak underlying I/O text.
    }
  }
}
