import 'dart:async';

import 'package:flutter/foundation.dart';

/// Answers to an agent's request (Allow, Reject, a choice) held for a short
/// window before they are sent, so a mistaken tap can be undone. One per
/// connection: an answer held from the Conversations list shows as held in
/// the chat too.
///
/// Contract (frozen for the parallel slices of 2026-10-07):
/// - [hold] keeps the answer for [window], then runs `send` once. Holding
///   again for the same request replaces the earlier answer.
/// - [undo] drops a held answer; nothing is sent.
/// - [heldLabel] is the held answer's words ("Allowed"), null when none.
/// - [flush] sends every held answer now (the app is leaving).
class DelayedAnswers extends ChangeNotifier {
  DelayedAnswers({this.window = const Duration(seconds: 3)});

  /// How long an answer can be undone.
  final Duration window;

  final Map<String, _Held> _held = {};
  bool _disposed = false;

  bool isHeld(String requestId) => _held.containsKey(requestId);

  String? heldLabel(String requestId) => _held[requestId]?.label;

  void hold(
    String requestId, {
    required String label,
    required Future<void> Function() send,
  }) {
    if (_disposed) {
      unawaited(_run(send));
      return;
    }
    _held.remove(requestId)?.timer.cancel();
    late final _Held entry;
    final timer = Timer(window, () {
      if (!identical(_held[requestId], entry)) return;
      _held.remove(requestId);
      _notify();
      unawaited(_run(send));
    });
    entry = _Held(label, send, timer);
    _held[requestId] = entry;
    _notify();
  }

  void undo(String requestId) {
    final entry = _held.remove(requestId);
    if (entry == null) return;
    entry.timer.cancel();
    _notify();
  }

  /// Sends every held answer now and clears them. Safe to call twice.
  Future<void> flush() async {
    if (_held.isEmpty) return;
    final entries = _held.values.toList();
    _held.clear();
    for (final entry in entries) {
      entry.timer.cancel();
    }
    _notify();
    await Future.wait([for (final entry in entries) _run(entry.send)]);
  }

  /// A send that fails belongs to its own caller's error handling; it never
  /// escapes from a timer.
  Future<void> _run(Future<void> Function() send) async {
    try {
      await send();
    } catch (_) {}
  }

  void _notify() {
    if (!_disposed) notifyListeners();
  }

  /// Held answers are sent, not lost, when the owner goes away.
  @override
  void dispose() {
    if (_disposed) return;
    final entries = _held.values.toList();
    _held.clear();
    for (final entry in entries) {
      entry.timer.cancel();
      unawaited(_run(entry.send));
    }
    _disposed = true;
    super.dispose();
  }
}

class _Held {
  _Held(this.label, this.send, this.timer);

  final String label;
  final Future<void> Function() send;
  final Timer timer;
}
