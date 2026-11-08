import 'dart:collection';

import 'package:flutter/foundation.dart';

/// The app's own short log: the last [capacity] lines, in memory only.
///
/// It holds the breadcrumb words, the analytics events that were sent, the
/// error categories the app recorded and whatever the app printed with
/// `debugPrint`. It never leaves the phone by itself: its only reader is
/// "Send a problem report", which redacts it, shows it to the person and
/// sends it only when they tap Send (docs/telemetry/health-and-diagnostics.md
/// section E).
class AppLogRing {
  AppLogRing({this.capacity = 500});

  /// The process-wide ring.
  static final AppLogRing instance = AppLogRing();

  final int capacity;
  final Queue<String> _lines = Queue<String>();

  /// One line is kept short: a line longer than this is cut.
  static const int maxLineLength = 300;

  /// Adds [line] with the time, dropping the oldest line past [capacity].
  void add(String line, {DateTime? at}) {
    if (capacity <= 0) return;
    final t = (at ?? DateTime.now()).toUtc();
    String two(int v) => v.toString().padLeft(2, '0');
    var text = line.replaceAll(RegExp(r'\s+'), ' ').trim();
    if (text.isEmpty) return;
    if (text.length > maxLineLength) {
      text = '${text.substring(0, maxLineLength)}…';
    }
    _lines.addLast('${two(t.hour)}:${two(t.minute)}:${two(t.second)} $text');
    while (_lines.length > capacity) {
      _lines.removeFirst();
    }
  }

  /// The kept lines, oldest first.
  List<String> get lines => List.unmodifiable(_lines);

  void clear() => _lines.clear();

  /// Routes `debugPrint` into [ring] as well as its normal output. Call once
  /// at start-up; later calls do nothing.
  static void captureDebugPrint([AppLogRing? ring]) {
    if (_captured) return;
    _captured = true;
    final target = ring ?? instance;
    final original = debugPrint;
    debugPrint = (String? message, {int? wrapWidth}) {
      if (message != null) target.add(message);
      original(message, wrapWidth: wrapWidth);
    };
  }

  static bool _captured = false;
}
