import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';

import '../domain/agent_run_failure.dart';
import '../ui/kit/kit_redact.dart';

/// The standing notes a conversation has already said, per saved server: a
/// note such as "Auto-approve is on" shows once, folds away by itself after
/// a few seconds or on Dismiss, and stays away in that conversation, while
/// the state it names keeps showing where it lives (the Auto-approve chip,
/// the composer's + sheet). Owner, 2026-10-27: "they should be dismissible
/// and maybe go away on their own".
///
/// Kept under `oc.chatNoticesSeen.<profileId>` as `<note>:<sessionID>`
/// entries, so deleting the server sweeps them (ProfileStore's scoped-key
/// sweep).
abstract final class ChatNoticeMemory {
  /// The newest entries kept; an older conversation may say its note again.
  static const maxEntries = 400;

  /// The Auto-approve note under the header while the agent works.
  static const autoApprove = 'auto-approve';

  static String keyFor(String profileId) => 'oc.chatNoticesSeen.$profileId';

  static String _entry(String notice, String sessionID) => '$notice:$sessionID';

  static List<String> _read(SharedPreferences prefs, String profileId) {
    try {
      return [...?prefs.getStringList(keyFor(profileId))];
    } catch (_) {
      // A value of another type: nothing was said that can be read.
      return [];
    }
  }

  static bool seen(
    SharedPreferences prefs,
    String profileId,
    String notice,
    String sessionID,
  ) => _read(prefs, profileId).contains(_entry(notice, sessionID));

  /// Saves that the note was said. False when storage refused it; it is
  /// then hidden only until the chat is opened again.
  static Future<bool> remember(
    SharedPreferences prefs,
    String profileId,
    String notice,
    String sessionID,
  ) async {
    final entry = _entry(notice, sessionID);
    var kept = _read(prefs, profileId)
      ..remove(entry)
      ..add(entry);
    if (kept.length > maxEntries) {
      kept = kept.sublist(kept.length - maxEntries);
    }
    try {
      return await prefs.setStringList(keyFor(profileId), kept);
    } catch (_) {
      return false;
    }
  }

  /// The state the note names was turned off: the next time it is on, the
  /// note says so once more.
  static Future<bool> forget(
    SharedPreferences prefs,
    String profileId,
    String notice,
    String sessionID,
  ) async {
    final kept = _read(prefs, profileId);
    if (!kept.remove(_entry(notice, sessionID))) return true;
    try {
      return await prefs.setStringList(keyFor(profileId), kept);
    } catch (_) {
      return false;
    }
  }
}

/// Why an agent's newest turn failed, per conversation and saved server, as
/// the chat saw it. Paseo 0.11.2 keeps a failure's words (`lastError`, the
/// "[System Error]" row) only while the helper holds it: after a restart
/// the conversation says only that its last turn failed (an `error`
/// attention or status), so the chat said "No reply came back" over a
/// sign-in it had shown a minute before (emulator, 2026-10-28).
///
/// Kept under `oc.agentTurnFailures.<profileId>` (one JSON entry per
/// conversation, newest last, redacted), so deleting the server sweeps it
/// (ProfileStore's scoped-key sweep). Forgotten when a later turn answers.
abstract final class AgentTurnFailureMemory {
  /// The newest entries kept.
  static const maxEntries = 100;

  static String keyFor(String profileId) => 'oc.agentTurnFailures.$profileId';

  static List<Map<String, Object?>> _read(
    SharedPreferences prefs,
    String profileId,
  ) {
    try {
      return [
        for (final raw in prefs.getStringList(keyFor(profileId)) ?? const [])
          if (jsonDecode(raw) case final Map<String, Object?> entry) entry,
      ];
    } catch (_) {
      // Another type or a broken entry: nothing that can be read.
      return [];
    }
  }

  /// The newest kept failure of [sessionID], or of another record of the
  /// same conversation ([alsoAs]: a fork an older build made on reopen).
  static AgentRunFailure? read(
    SharedPreferences prefs,
    String profileId,
    String sessionID, {
    Iterable<String> alsoAs = const [],
  }) {
    final ids = {sessionID, ...alsoAs};
    for (final entry in _read(prefs, profileId).reversed) {
      if (!ids.contains(entry['s'])) continue;
      final reason = entry['r'];
      if (reason is! String || reason.trim().isEmpty) return null;
      final provider = entry['p'];
      final model = entry['m'];
      return AgentRunFailure(
        reason: reason,
        provider: provider is String ? provider : null,
        model: model is String ? model : null,
      );
    }
    return null;
  }

  /// Saves [failure] for [sessionID]. False when storage refused it.
  static Future<bool> remember(
    SharedPreferences prefs,
    String profileId,
    String sessionID,
    AgentRunFailure failure,
  ) async {
    var kept = _read(prefs, profileId)
      ..removeWhere((entry) => entry['s'] == sessionID)
      ..add({
        's': sessionID,
        'r': KitRedact.text(failure.reason),
        'p': ?failure.provider,
        'm': ?failure.model,
      });
    if (kept.length > maxEntries) {
      kept = kept.sublist(kept.length - maxEntries);
    }
    try {
      return await prefs.setStringList(keyFor(profileId), [
        for (final entry in kept) jsonEncode(entry),
      ]);
    } catch (_) {
      return false;
    }
  }

  static Future<bool> forget(
    SharedPreferences prefs,
    String profileId,
    String sessionID, {
    Iterable<String> alsoAs = const [],
  }) async {
    final ids = {sessionID, ...alsoAs};
    final kept = _read(prefs, profileId);
    final before = kept.length;
    kept.removeWhere((entry) => ids.contains(entry['s']));
    if (kept.length == before) return true;
    try {
      return await prefs.setStringList(keyFor(profileId), [
        for (final entry in kept) jsonEncode(entry),
      ]);
    } catch (_) {
      return false;
    }
  }
}

/// Messages the chat sent again after the agent's server withdrew them, per
/// conversation and saved server. Paseo withdraws a steer Claude has not read
/// when its turn is stopped (claude/agent.js withdrawUnstartedMessages) but
/// keeps the message in the conversation's record, so "Stop the command"
/// sends the same words again and the record holds them twice. The first
/// copy was never read: the chat leaves it out, now and every time the
/// conversation opens again.
///
/// Kept under `oc.withdrawnPrompts.<profileId>` as `<sessionID>|<messageID>`
/// entries, so deleting the server sweeps them (ProfileStore's scoped-key
/// sweep).
abstract final class WithdrawnPromptMemory {
  /// The newest entries kept.
  static const maxEntries = 300;

  static String keyFor(String profileId) => 'oc.withdrawnPrompts.$profileId';

  static String _entry(String sessionID, String messageID) =>
      '$sessionID|$messageID';

  static List<String> _read(SharedPreferences prefs, String profileId) {
    try {
      return [...?prefs.getStringList(keyFor(profileId))];
    } catch (_) {
      // A value of another type: nothing withdrawn can be read.
      return [];
    }
  }

  /// The withdrawn messages of [sessionID].
  static Set<String> read(
    SharedPreferences prefs,
    String profileId,
    String sessionID,
  ) {
    final prefix = '$sessionID|';
    return {
      for (final entry in _read(prefs, profileId))
        if (entry.startsWith(prefix)) entry.substring(prefix.length),
    };
  }

  /// Saves that [messageID] was withdrawn and sent again. False when storage
  /// refused it; the copy then shows again when the chat is reopened.
  static Future<bool> remember(
    SharedPreferences prefs,
    String profileId,
    String sessionID,
    String messageID,
  ) async {
    final entry = _entry(sessionID, messageID);
    var kept = _read(prefs, profileId)
      ..remove(entry)
      ..add(entry);
    if (kept.length > maxEntries) {
      kept = kept.sublist(kept.length - maxEntries);
    }
    try {
      return await prefs.setStringList(keyFor(profileId), kept);
    } catch (_) {
      return false;
    }
  }
}
