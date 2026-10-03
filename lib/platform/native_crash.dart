import 'package:flutter/foundation.dart';

/// The latest private JVM crash summary returned by `oc/lifecycle`.
///
/// Native code discards raw exception messages before saving. Dart repeats that
/// boundary: only fixed messages and structured Java symbols reach diagnostics.
/// Arbitrary platform strings, URLs, login codes and filesystem paths are never
/// accepted as exception text or stack frames.
@immutable
class NativeCrashRecord {
  const NativeCrashRecord._({
    required this.timestamp,
    required this.exceptionClass,
    required this.message,
    required this.frames,
    required this.causes,
  });

  static const _messages = {
    'Input/output failure',
    'Permission denied',
    'Invalid state',
    'Invalid argument',
    'Missing value',
    'Channel reply failure',
    'Interrupted operation',
    '[redacted]',
  };
  static final _symbol = RegExp(
    r'^(?:java\.|javax\.|android\.|kotlin\.|kotlinx\.|io\.flutter\.|io\.github\.eslamasabry\.opencode_mobile\.)[A-Za-z_$][A-Za-z0-9_$.]*$',
  );
  static final _frame = RegExp(
    r'^([A-Za-z_$][A-Za-z0-9_$.]*)\.([A-Za-z_$][A-Za-z0-9_$]*|<init>|<clinit>)\(([A-Za-z_$][A-Za-z0-9_$]*\.(?:kt|java):[0-9]{1,7}|Native Method|Unknown Source)\)$',
  );

  static NativeCrashRecord? fromMap(Object? raw) {
    if (raw is! Map) return null;
    final millis = raw['timestamp'];
    if (millis is! int || millis <= 0 || millis > 253402300799999) {
      return null;
    }
    final details = _readDetails(raw, frameLimit: 24);
    if (details == null) return null;
    final rawCauses = raw['causes'];
    final causes = <NativeCrashCause>[];
    if (rawCauses is List) {
      for (final rawCause in rawCauses.take(3)) {
        final cause = _readDetails(rawCause, frameLimit: 8);
        if (cause != null) causes.add(cause);
      }
    }
    return NativeCrashRecord._(
      timestamp: DateTime.fromMillisecondsSinceEpoch(millis),
      exceptionClass: details.exceptionClass,
      message: details.message,
      frames: details.frames,
      causes: List.unmodifiable(causes),
    );
  }

  static NativeCrashCause? _readDetails(
    Object? raw, {
    required int frameLimit,
  }) {
    if (raw is! Map) return null;
    final exception = raw['exceptionClass'];
    if (exception is! String ||
        exception.length > 256 ||
        !_symbol.hasMatch(exception)) {
      return null;
    }
    final rawFrames = raw['frames'];
    final frames = <String>[];
    if (rawFrames is List) {
      for (final value in rawFrames.take(frameLimit)) {
        if (value is! String || value.length > 512) continue;
        final match = _frame.firstMatch(value);
        if (match != null && _symbol.hasMatch(match.group(1)!)) {
          frames.add(value);
        }
      }
    }
    final rawMessage = raw['message'];
    return NativeCrashCause._(
      exceptionClass: exception,
      message: rawMessage is String && _messages.contains(rawMessage)
          ? rawMessage
          : '[redacted]',
      frames: List.unmodifiable(frames),
    );
  }

  final DateTime timestamp;
  final String exceptionClass;
  final String message;
  final List<String> frames;

  /// Bounded nested causes expose the actionable exception Android often
  /// wraps in a RuntimeException while starting an Activity or service.
  final List<NativeCrashCause> causes;

  // Separate package components so the generic token scrubber does not mistake
  // a long Java class name for a credential and erase the useful stack symbols.
  String get diagnosticMessage =>
      '${exceptionClass.replaceAll('.', ' > ')}: $message';

  String get diagnosticStack => [
    _renderFrames(frames),
    for (final cause in causes)
      'Caused by ${cause.exceptionClass.replaceAll('.', ' > ')}: '
          '${cause.message}\n${_renderFrames(cause.frames)}',
  ].where((part) => part.isNotEmpty).join('\n');

  static String _renderFrames(List<String> frames) => frames
      .map((frame) {
        final match = _frame.firstMatch(frame)!;
        final method = match.group(2)!;
        final className = match.group(1)!;
        final separator = '${className.split('.').last}.$method'.length >= 32
            ? '. '
            : '.';
        return 'at ${className.replaceAll('.', ' > ')}$separator'
            '$method (${match.group(3)})';
      })
      .join('\n');

  /// Bounded individual attributes keep the top symbols in Performance even
  /// though its generic scrubber limits each value to 80 characters.
  Map<String, Object?> get traceAttrs => {
    'class': exceptionClass.split('.').last,
    'message': message,
    'at': timestamp.toIso8601String(),
    for (var i = 0; i < frames.length && i < 6; i++)
      'frame$i': _shortFrame(frames[i]),
    for (var i = 0; i < causes.length; i++) ...{
      'cause$i': causes[i].exceptionClass.split('.').last,
      'causeMessage$i': causes[i].message,
      if (causes[i].frames.isNotEmpty)
        'causeFrame$i': _shortFrame(causes[i].frames.first),
    },
  };

  static String _shortFrame(String frame) {
    final match = _frame.firstMatch(frame)!;
    final symbol = '${match.group(1)!.split('.').last}.${match.group(2)}';
    final display = symbol.length >= 32
        ? symbol.replaceFirst('.', '. ')
        : symbol;
    return '$display '
        '(${match.group(3)})';
  }
}

@immutable
class NativeCrashCause {
  const NativeCrashCause._({
    required this.exceptionClass,
    required this.message,
    required this.frames,
  });

  final String exceptionClass;
  final String message;
  final List<String> frames;
}
