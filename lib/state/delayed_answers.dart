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

  bool isHeld(String requestId) => false;

  String? heldLabel(String requestId) => null;

  void hold(
    String requestId, {
    required String label,
    required Future<void> Function() send,
  }) {
    unawaited(send());
  }

  void undo(String requestId) {}

  Future<void> flush() async {}
}
