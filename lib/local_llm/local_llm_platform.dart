import 'dart:async';

import 'package:flutter/services.dart';

import '../platform/platform_capabilities.dart';

/// One message from the native engine on `oc/local_llm/events`.
sealed class LocalLlmEvent {
  const LocalLlmEvent();

  static LocalLlmEvent? parse(Object? raw) {
    if (raw is! Map) return null;
    final id = raw['id'] as String?;
    switch (raw['type']) {
      case 'token':
        return LocalLlmToken(id ?? '', raw['text'] as String? ?? '');
      case 'done':
        return LocalLlmDone(
          id ?? '',
          firstTokenMs: (raw['firstTokenMs'] as num?)?.toInt() ?? -1,
          totalMs: (raw['totalMs'] as num?)?.toInt() ?? 0,
          chunks: (raw['chunks'] as num?)?.toInt() ?? 0,
        );
      case 'cancelled':
        return LocalLlmCancelled(id ?? '');
      case 'error':
        return LocalLlmFailed(id ?? '', raw['code'] as String? ?? 'unknown');
      case 'unloaded':
        return LocalLlmUnloaded(raw['reason'] as String? ?? 'requested');
    }
    return null;
  }
}

class LocalLlmToken extends LocalLlmEvent {
  const LocalLlmToken(this.id, this.text);
  final String id;
  final String text;
}

class LocalLlmDone extends LocalLlmEvent {
  const LocalLlmDone(
    this.id, {
    required this.firstTokenMs,
    required this.totalMs,
    required this.chunks,
  });
  final String id;
  final int firstTokenMs;
  final int totalMs;
  final int chunks;
}

class LocalLlmCancelled extends LocalLlmEvent {
  const LocalLlmCancelled(this.id);
  final String id;
}

class LocalLlmFailed extends LocalLlmEvent {
  const LocalLlmFailed(this.id, this.code);
  final String id;
  final String code;
}

class LocalLlmUnloaded extends LocalLlmEvent {
  const LocalLlmUnloaded(this.reason);
  final String reason;
}

class LocalLlmException implements Exception {
  const LocalLlmException(this.code);
  final String code;

  @override
  String toString() => 'LocalLlmException($code)';
}

/// The `loadModel` answer: the backend that really loaded, and whether it
/// is the CPU after the GPU was asked for.
class LocalLlmLoaded {
  const LocalLlmLoaded({required this.backend, this.fellBack = false});
  final String backend;
  final bool fellBack;
}

abstract interface class LocalLlmPlatform {
  Stream<LocalLlmEvent> get events;
  Future<LocalLlmLoaded> loadModel(String path, {String backend = 'gpu'});
  Future<void> generate(String id, String prompt, {required int maxTokens});
  Future<void> cancel();
  Future<void> unload();
}

/// Dart half of `oc/local_llm`; the Kotlin half is LocalLlmBridge.kt.
class AndroidLocalLlmPlatform implements LocalLlmPlatform {
  const AndroidLocalLlmPlatform();

  static const _channel = MethodChannel('oc/local_llm');
  static final Stream<LocalLlmEvent> _events =
      const EventChannel('oc/local_llm/events')
          .receiveBroadcastStream()
          .map(LocalLlmEvent.parse)
          .where((e) => e != null)
          .cast<LocalLlmEvent>();

  @override
  Stream<LocalLlmEvent> get events =>
      platformCapabilities.isAndroid ? _events : const Stream.empty();

  @override
  Future<LocalLlmLoaded> loadModel(
    String path, {
    String backend = 'gpu',
  }) async {
    try {
      final result = await _channel.invokeMapMethod<String, dynamic>(
        'loadModel',
        {'path': path, 'backend': backend},
      );
      return LocalLlmLoaded(
        backend: result?['backend'] as String? ?? 'cpu',
        fellBack: result?['fellBack'] == true,
      );
    } on PlatformException catch (error) {
      throw LocalLlmException(error.code);
    }
  }

  @override
  Future<void> generate(
    String id,
    String prompt, {
    required int maxTokens,
  }) async {
    try {
      await _channel.invokeMethod<void>('generate', {
        'id': id,
        'prompt': prompt,
        'maxTokens': maxTokens,
      });
    } on PlatformException catch (error) {
      throw LocalLlmException(error.code);
    }
  }

  @override
  Future<void> cancel() async {
    try {
      await _channel.invokeMethod<void>('cancel');
    } on PlatformException {
      // Nothing was running.
    }
  }

  @override
  Future<void> unload() async {
    try {
      await _channel.invokeMethod<void>('unload');
    } on PlatformException {
      // Already unloaded.
    }
  }
}
