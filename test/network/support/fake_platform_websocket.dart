import 'dart:async';

import 'package:opencode_mobile/network/platform_websocket.dart';

/// In-memory [PlatformWebSocket]: tests push frames through [controller]
/// and read what the channel sent from [sent].
class FakePlatformWebSocket implements PlatformWebSocket {
  final controller = StreamController<Object>();
  final sent = <Object>[];
  final _done = Completer<void>();
  bool closed = false;
  int? requestedCode;

  @override
  Stream<Object> get messages => controller.stream;

  @override
  void send(Object data) => sent.add(data);

  @override
  bool get isOpen => !closed;

  @override
  Future<void> close({int? code, String? reason}) async {
    closed = true;
    requestedCode = code;
    if (!controller.isClosed) unawaited(controller.close());
    if (!_done.isCompleted) _done.complete();
  }

  @override
  Future<void> get done => _done.future;

  @override
  int? get closeCode => null;

  @override
  String? get closeReason => null;
}
