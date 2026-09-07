import 'dart:async';

import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';

import '../platform/platform_capabilities.dart';

enum ReadAloudFailure {
  unsupported,
  noOfflineVoice,
  engineUnavailable,
  tooLong,
  busy,
}

class ReadAloudVoice {
  const ReadAloudVoice({
    required this.id,
    required this.label,
    required this.locale,
  });

  final String id;
  final String label;
  final String locale;
}

class ReadAloudException implements Exception {
  const ReadAloudException(this.failure);

  final ReadAloudFailure failure;

  @override
  String toString() => 'Read-aloud is unavailable.';
}

/// Foreground-only system TTS. The caller must obtain explicit consent before
/// [speak]: an external engine's offline flag is not a network/privacy guarantee.
/// Text must be nonblank and at most 12000 UTF-16 code units. Empty input maps
/// to [ReadAloudFailure.tooLong] (the frozen API has no invalid-input variant).
/// Construction performs no platform or engine I/O. No text is persisted here.
class ReadAloudController extends ChangeNotifier with WidgetsBindingObserver {
  static const _channel = MethodChannel('oc/read-aloud');
  static ReadAloudController? _owner;
  static int _sequence = 0;
  static bool _listening = false;

  bool _speaking = false;
  String? _activeID;
  ReadAloudFailure? _failure;
  String? _operation;
  bool _disposed = false;
  bool _observing = false;
  int _generation = 0;
  final Set<String> _pendingOperations = {};
  final Map<String, ReadAloudFailure> _retiredFailures = {};

  bool get speaking => _speaking;
  String? get activeID => _activeID;
  ReadAloudFailure? get failure => _failure;

  void _prepare() {
    if (_disposed) {
      throw const ReadAloudException(ReadAloudFailure.engineUnavailable);
    }
    if (!platformCapabilities.supportsReadAloud) {
      _fail(ReadAloudFailure.unsupported);
    }
    if (!_observing) {
      WidgetsBinding.instance.addObserver(this);
      _observing = true;
    }
    if (!_listening) {
      _listening = true;
      _channel.setMethodCallHandler((call) async {
        final data = call.arguments;
        final owner = _owner;
        if (call.method != 'status' ||
            data is! Map ||
            owner == null ||
            data['operationID'] != owner._operation) {
          return;
        }
        switch (data['status']) {
          case 'completed':
          case 'cancelled':
            owner._clear();
          case 'engineUnavailable':
          case 'busy':
            owner._clear(
              failure: data['status'] == 'busy'
                  ? ReadAloudFailure.busy
                  : ReadAloudFailure.engineUnavailable,
            );
          default:
            // An unrecognized terminal response cannot leave UI speaking forever.
            final operation = owner._operation!;
            owner._clear(failure: ReadAloudFailure.engineUnavailable);
            unawaited(owner._stopOperation(operation));
        }
      });
    }
  }

  Never _fail(ReadAloudFailure value) {
    _failure = value;
    if (!_disposed) notifyListeners();
    throw ReadAloudException(value);
  }

  static ReadAloudFailure _decode(Object error) {
    if (error is ReadAloudException) return error.failure;
    if (error is PlatformException) {
      for (final value in ReadAloudFailure.values) {
        if (value.name == error.code) return value;
      }
    }
    return ReadAloudFailure.engineUnavailable;
  }

  Future<List<ReadAloudVoice>> voices() async {
    _prepare();
    final generation = _generation;
    try {
      final result = await _channel
          .invokeMethod<Object?>('voices')
          .timeout(const Duration(seconds: 12));
      if (_disposed || generation != _generation) return const [];
      if (result is! List) {
        throw const ReadAloudException(ReadAloudFailure.engineUnavailable);
      }
      final voices = <ReadAloudVoice>[];
      final ids = <String>{};
      for (final item in result) {
        if (item is! Map ||
            item['id'] is! String ||
            item['label'] is! String ||
            item['locale'] is! String ||
            (item['id'] as String).isEmpty ||
            (item['locale'] as String).isEmpty ||
            !ids.add(item['id'] as String)) {
          throw const ReadAloudException(ReadAloudFailure.engineUnavailable);
        }
        voices.add(
          ReadAloudVoice(
            id: item['id'] as String,
            label: item['label'] as String,
            locale: item['locale'] as String,
          ),
        );
      }
      if (voices.isEmpty) {
        throw const ReadAloudException(ReadAloudFailure.noOfflineVoice);
      }
      _failure = null;
      notifyListeners();
      return List.unmodifiable(voices);
    } catch (error) {
      if (_disposed || generation != _generation) return const [];
      _fail(_decode(error));
    }
  }

  /// Resolves when playback is accepted, not when all chunks have completed.
  /// [id] stays in Dart; native callbacks contain only generated operation IDs.
  Future<void> speak(String id, String text, {String? voiceID}) async {
    _prepare();
    if (text.trim().isEmpty || text.length > 12000) {
      _fail(ReadAloudFailure.tooLong);
    }
    final state = WidgetsBinding.instance.lifecycleState;
    if (state != null && state != AppLifecycleState.resumed) {
      _fail(ReadAloudFailure.busy);
    }
    // No await between claiming ownership and dispatching: platform FIFO plus
    // operation-scoped stop prevents an old controller stopping its replacement.
    final previous = _owner;
    if (previous != null) unawaited(previous.stop());
    final operation = '${DateTime.now().microsecondsSinceEpoch}-${++_sequence}';
    _generation++;
    _owner = this;
    _operation = operation;
    _activeID = id;
    _speaking = true;
    _failure = null;
    _pendingOperations.add(operation);
    notifyListeners();
    if (_disposed || _operation != operation) {
      final retiredFailure = _retiredFailures.remove(operation);
      _pendingOperations.remove(operation);
      if (retiredFailure != null) {
        throw ReadAloudException(retiredFailure);
      }
      return;
    }
    ReadAloudFailure? retiredFailure;
    try {
      final result = await _channel
          .invokeMethod<Object?>('speak', {
            'operationID': operation,
            'text': text,
            'voiceID': voiceID,
          })
          .timeout(const Duration(seconds: 12));
      retiredFailure = _retiredFailures[operation];
      if (retiredFailure != null) {
        throw ReadAloudException(retiredFailure);
      }
      if (_operation != operation || _disposed) return;
      if (result is! Map ||
          result['operationID'] != operation ||
          result['status'] != 'accepted') {
        throw const ReadAloudException(ReadAloudFailure.engineUnavailable);
      }
    } catch (error) {
      retiredFailure ??= _retiredFailures[operation];
      if (retiredFailure != null) {
        throw ReadAloudException(retiredFailure);
      }
      if (_operation != operation || _disposed) return;
      final value = _decode(error);
      _clear(failure: value);
      unawaited(_stopOperation(operation));
      throw ReadAloudException(value);
    } finally {
      _pendingOperations.remove(operation);
      _retiredFailures.remove(operation);
    }
  }

  void _clear({ReadAloudFailure? failure}) {
    final operation = _operation;
    if (failure != null &&
        operation != null &&
        _pendingOperations.contains(operation)) {
      _retiredFailures[operation] = failure;
    }
    _generation++;
    _operation = null;
    _activeID = null;
    _speaking = false;
    _failure = failure;
    if (identical(_owner, this)) _owner = null;
    if (!_disposed) notifyListeners();
  }

  Future<void> stop() async {
    final operation = _operation;
    _clear();
    if (operation == null || !platformCapabilities.supportsReadAloud) return;
    await _stopOperation(operation);
  }

  Future<void> _stopOperation(String operation) async {
    if (!platformCapabilities.supportsReadAloud) return;
    try {
      await _channel
          .invokeMethod<Object?>('stop', {'operationID': operation})
          .timeout(const Duration(seconds: 3));
    } catch (_) {
      // Native pause/destroy independently stops; never expose engine errors.
    }
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state != AppLifecycleState.resumed) unawaited(stop());
  }

  @override
  void dispose() {
    if (_disposed) return;
    _disposed = true;
    if (_observing) WidgetsBinding.instance.removeObserver(this);
    unawaited(stop());
    super.dispose();
  }
}
