import 'dart:async';

import 'package:flutter/services.dart';

/// Submit the first frame before waiting for native launch synchronization.
Future<void> showBb8FrameAndWaitForNativeReady({
  required Future<void> Function() firstFrame,
  MethodChannel channel = const MethodChannel('oc/bb8_qa'),
}) async {
  await firstFrame();
  await waitForBb8NativeReady(channel: channel);
}

/// QA-only rendezvous; production authentication scripts remain unchanged.
Future<void> waitForBb8NativeReady({
  MethodChannel channel = const MethodChannel('oc/bb8_qa'),
}) {
  final ready = Completer<void>();
  Timer? retry;
  final deadline = Timer(const Duration(seconds: 10), () {
    if (!ready.isCompleted) {
      ready.completeError(TimeoutException('bb8_native_ready_timed_out'));
    }
  });

  Future<void> attempt() async {
    if (ready.isCompleted) return;
    try {
      final reply = await channel.invokeMethod<Object?>('ready');
      if (ready.isCompleted) return;
      if (reply == true) {
        ready.complete();
      } else {
        ready.completeError(StateError('bb8_native_ready_invalid'));
      }
    } on MissingPluginException {
      if (!ready.isCompleted) {
        retry = Timer(const Duration(milliseconds: 100), attempt);
      }
    } catch (_) {
      if (!ready.isCompleted) {
        ready.completeError(StateError('bb8_native_ready_unavailable'));
      }
    }
  }

  unawaited(attempt());
  return ready.future.whenComplete(() {
    deadline.cancel();
    retry?.cancel();
  });
}
