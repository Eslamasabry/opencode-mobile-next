import 'dart:convert';

/// Only this entry is managed by mobile. The pinned server counts the UTF-8
/// bytes of JSON.stringify(value), including quotes and escapes.
abstract interface class SessionNoteGateway {
  static const key = 'mobile.note';
  static const maxBytes = 8192;
  static int encodedBytes(String value) =>
      utf8.encode(jsonEncode(value)).length;
  bool get sessionNotesSupported;
  Future<String?> loadSessionNote(String sessionID);
  Future<void> saveSessionNote(String sessionID, String value);
  Future<void> removeSessionNote(String sessionID);
}

enum SessionNoteFailure { unsupported, changed, invalidValue, tooLarge, busy }

class SessionNoteException implements Exception {
  final SessionNoteFailure failure;
  final int maxBytes;
  const SessionNoteException(
    this.failure, {
    this.maxBytes = SessionNoteGateway.maxBytes,
  });
}

class SessionNoteReview {
  final Object scope;
  final String sessionID;
  final String? value;
  final int revision;
  const SessionNoteReview({
    required this.scope,
    required this.sessionID,
    required this.value,
    required this.revision,
  });
}
