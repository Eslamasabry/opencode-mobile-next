import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';

import '../domain/genui/gen_ui.dart';
import '../domain/server_gateway.dart' show ProductException;

/// Only routing/correlation metadata is persisted, never card or answer content.
final class GenUiReference {
  const GenUiReference({
    required this.scope,
    required this.sessionID,
    required this.messageID,
    required this.callID,
    required this.revision,
    required this.endpoint,
    this.dispatchID,
  });
  factory GenUiReference.card(
    GenUiCard card,
    String endpoint, {
    String? dispatchID,
  }) => GenUiReference(
    scope: card.scope,
    sessionID: card.sessionID,
    messageID: card.messageID,
    callID: card.callID,
    revision: card.revision,
    endpoint: endpoint,
    dispatchID: dispatchID,
  );
  final GenUiScope scope;
  final String sessionID, messageID, callID, revision, endpoint;
  final String? dispatchID;
  String get identity => jsonEncode([
    scope.profileID,
    scope.sourceId,
    scope.directory,
    scope.workspace,
    sessionID,
    messageID,
    callID,
  ]);
  Map<String, Object?> toJson() => {
    'profile': scope.profileID,
    'source': scope.sourceId,
    'directory': scope.directory,
    'workspace': scope.workspace,
    'session': sessionID,
    'message': messageID,
    'call': callID,
    'revision': revision,
    'endpoint': endpoint,
    if (dispatchID != null) 'dispatch': dispatchID,
  };
  static GenUiReference? decode(Object? raw, String profile) {
    if (raw is! Map || raw['profile'] != profile) return null;
    String? field(String key) {
      final v = raw[key];
      return v is String && v.isNotEmpty && v.length <= 4096 ? v : null;
    }

    final source = field('source'),
        directory = field('directory'),
        session = field('session'),
        message = field('message'),
        call = field('call'),
        revision = field('revision'),
        endpoint = field('endpoint');
    final workspace = raw['workspace'];
    if (source == null ||
        directory == null ||
        session == null ||
        message == null ||
        call == null ||
        revision == null ||
        endpoint == null ||
        (workspace != null &&
            (workspace is! String || workspace.length > 4096))) {
      return null;
    }
    if (raw.containsKey('dispatch') && field('dispatch') == null) return null;
    return GenUiReference(
      scope: GenUiScope(
        profileID: profile,
        sourceId: source,
        directory: directory,
        workspace: workspace as String?,
      ),
      sessionID: session,
      messageID: message,
      callID: call,
      revision: revision,
      endpoint: endpoint,
      dispatchID: field('dispatch'),
    );
  }
}

final class GenUiJournal {
  GenUiJournal(this.prefs);
  final SharedPreferences prefs;
  final _closed = <String>{};
  Future<void> _writes = Future.value();
  static String key(String profile) => 'oc.genui.journal.$profile';

  List<GenUiReference> read(String profile) {
    final text = prefs.getString(key(profile));
    if (text == null) return [];
    try {
      if (utf8.encode(text).length > 65536) throw const FormatException();
      final raw = jsonDecode(text);
      if (raw is! Map ||
          raw['v'] != 1 ||
          raw['entries'] is! List ||
          (raw['entries'] as List).length > 100) {
        throw const FormatException();
      }
      final entries = <GenUiReference>[];
      for (final item in raw['entries'] as List) {
        final entry = GenUiReference.decode(item, profile);
        if (entry == null) throw const FormatException();
        entries.add(entry);
      }
      return entries;
    } catch (_) {
      // A corrupt dispatch record must never be interpreted as permission to resend.
      throw const ProductException('Saved card delivery needs review.');
    }
  }

  Future<void> update(
    String profile,
    List<GenUiReference> Function(List<GenUiReference>) change,
  ) {
    final work = _writes.then((_) async {
      if (_closed.contains(profile)) {
        throw const ProductException('The profile changed.');
      }
      final previous = read(profile);
      final entries = change(previous);
      if (identical(previous, entries)) return;
      if (entries.length > 100) {
        throw const ProductException('Too many pending card answers.');
      }
      final encoded = jsonEncode({
        'v': 1,
        'entries': entries.map((e) => e.toJson()).toList(),
      });
      if (utf8.encode(encoded).length > 65536) {
        throw const ProductException('Saved card delivery is full.');
      }
      if (!await prefs.setString(key(profile), encoded)) {
        throw const ProductException('The card answer could not be saved.');
      }
    });
    _writes = work.then<void>((_) {}, onError: (Object _) {});
    return work;
  }

  Future<void> put(GenUiReference value) => update(value.scope.profileID, (
    entries,
  ) {
    final old = entries.where((e) => e.identity == value.identity).firstOrNull;
    if (old?.dispatchID != null && value.dispatchID == null) return entries;
    final kept = entries.where((e) => e.identity != value.identity).toList();
    // Only non-dispatched candidate hints can be evicted.
    if (kept.length >= 100) {
      final removable = kept.indexWhere((e) => e.dispatchID == null);
      if (removable >= 0) kept.removeAt(removable);
    }
    return [...kept, value];
  });

  /// Remove this exact pre-dispatch reservation after the send fence refused it.
  /// This is the only mutation allowed after close: it cannot add an entry or
  /// clear a different dispatch. Callers must know no bytes were dispatched.
  Future<void> discardUnsent(GenUiReference expected) {
    final work = _writes.then((_) async {
      if (expected.dispatchID == null) return;
      final profile = expected.scope.profileID;
      // A completed deletion sweep wins. Do not recreate even an empty journal.
      if (!prefs.containsKey(key(profile))) return;
      final entries = read(profile);
      final kept = entries
          .where(
            (entry) =>
                entry.identity != expected.identity ||
                entry.revision != expected.revision ||
                entry.endpoint != expected.endpoint ||
                entry.dispatchID != expected.dispatchID,
          )
          .toList();
      if (kept.length == entries.length) return;
      // Compare and invoke the preference mutation without an intervening await;
      // profile deletion cannot run between the existence check and this write.
      final saved = kept.isEmpty
          ? prefs.remove(key(profile))
          : prefs.setString(
              key(profile),
              jsonEncode({
                'v': 1,
                'entries': kept.map((entry) => entry.toJson()).toList(),
              }),
            );
      if (!await saved) {
        throw const ProductException(
          'The unsent card answer could not be cleared.',
        );
      }
    });
    _writes = work.then<void>((_) {}, onError: (Object _) {});
    return work;
  }

  Future<void> remove(String profile, String identity) => update(
    profile,
    (entries) => entries.where((e) => e.identity != identity).toList(),
  );

  Future<void> removeSession(GenUiScope scope, String sessionID) => update(
    scope.profileID,
    (entries) => entries
        .where((e) => e.scope != scope || e.sessionID != sessionID)
        .toList(),
  );

  Future<void> closeProfile(String profile) {
    _closed.add(profile);
    return _writes;
  }

  void reopenProfile(String profile) => _closed.remove(profile);
  Future<void> get drained => _writes;
}
