import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../api/models.dart';

/// Persists a compact recent-sessions snapshot for the Android home-screen
/// widget and asks native to redraw it.
///
/// Privacy: the widget deliberately shows session titles. The recorded
/// notification rules (fixed copy only, no titles) protect the lock screen,
/// where content appears without the user's choice; a home-screen widget is
/// a surface the user explicitly placed, sized, and can remove, and it shows
/// exactly the titles the launcher-visible app UI shows. Prompt text, tool
/// input, file names, and server errors are never written.
class WidgetSessionSnapshot {
  WidgetSessionSnapshot({
    required this.prefs,
    Future<void> Function()? refreshNative,
    bool? isAndroid,
  }) : _refreshNative = refreshNative ?? _refreshViaChannel,
       _isAndroid =
           isAndroid ??
           (!kIsWeb && defaultTargetPlatform == TargetPlatform.android);

  /// Read by the native widget as `flutter.oc.widgetSessions`.
  static const prefsKey = 'oc.widgetSessions';

  /// At most this many sessions are ever written.
  static const maxSessions = 4;

  static const _channel = MethodChannel('oc/background');

  final SharedPreferences prefs;
  final Future<void> Function() _refreshNative;
  final bool _isAndroid;
  String? _lastWritten;

  static Future<void> _refreshViaChannel() async {
    try {
      await _channel.invokeMethod<Object?>('refreshHomeWidget');
    } catch (_) {
      // No engine-side handler (tests, desktop, activity gone): the widget
      // simply keeps its last drawn state.
    }
  }

  /// Serializes the current root sessions and writes them only when the
  /// payload actually changed, so frequent controller notifications stay
  /// cheap and the widget redraws only on real changes.
  ///
  /// [profileID] names the server profile the sessions belong to. The widget
  /// stamps it onto every row's tap intent so a tap after a profile switch
  /// opens the app normally instead of another profile's chat.
  Future<void> update({
    required List<Session> sessions,
    required Set<String> busySessions,
    required bool connected,
    String profileID = '',
  }) async {
    if (!_isAndroid) return;
    final entries = [
      for (final session in sessions.take(maxSessions))
        {
          'id': session.id,
          'title': session.title?.trim().isNotEmpty == true
              ? session.title!.trim()
              : 'Untitled session',
          'busy': busySessions.contains(session.id),
          'updatedAt': session.time?.updated ?? session.time?.created ?? 0,
        },
    ];
    final payload = jsonEncode({
      'connected': connected,
      'profileID': profileID,
      'sessions': entries,
    });
    if (payload == _lastWritten && prefs.getString(prefsKey) == payload) {
      return;
    }
    _lastWritten = payload;
    await prefs.setString(prefsKey, payload);
    await _refreshNative();
  }

  /// Drops the snapshot when it belongs to [profileID], so removing a server
  /// also removes the session titles its widget was still showing on the home
  /// screen. A snapshot written before rows carried a profile has no
  /// identifiable owner and is dropped too.
  ///
  /// The three outcomes are kept apart deliberately: "there was nothing of
  /// yours to clear" and "the write refused" look identical to a caller that
  /// only gets a bool, and a deletion flow has to tell the user the
  /// difference.
  ///
  /// Runs on every platform: the stored payload is the privacy-relevant
  /// artifact, and only the native redraw is Android-specific.
  Future<WidgetSnapshotClear> clearForProfile(String profileID) async {
    final raw = prefs.getString(prefsKey);
    if (raw == null) return WidgetSnapshotClear.nothingToClear;
    String? owner;
    try {
      final decoded = jsonDecode(raw);
      if (decoded is Map) owner = decoded['profileID']?.toString() ?? '';
    } catch (_) {
      // Unreadable payload: it cannot be shown to be another profile's, so
      // treat it as removable rather than leaving it behind.
      owner = '';
    }
    if (owner == null || (owner.isNotEmpty && owner != profileID)) {
      return WidgetSnapshotClear.nothingToClear;
    }
    try {
      if (!await prefs.remove(prefsKey)) return WidgetSnapshotClear.failed;
    } catch (_) {
      return WidgetSnapshotClear.failed;
    }
    _lastWritten = null;
    if (_isAndroid) await _refreshNative();
    return WidgetSnapshotClear.cleared;
  }
}

/// What [WidgetSessionSnapshot.clearForProfile] actually did.
enum WidgetSnapshotClear {
  /// No snapshot, or one belonging to another profile: nothing was at risk.
  nothingToClear,

  /// The profile's snapshot is gone from disk.
  cleared,

  /// A snapshot belonging to the profile is still on disk — the store
  /// refused the write. The caller must not report the data as deleted.
  failed,
}
