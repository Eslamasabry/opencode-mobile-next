import 'dart:convert';

import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart' show Locale;
import 'package:shared_preferences/shared_preferences.dart';

import '../domain/models.dart';
import '../platform/platform_capabilities.dart';
import '../l10n/app_localizations.dart';
import '../ui/kit/kit_redact.dart';
import '../ui/widgets/session_title.dart';
import 'widget_v2_contract.dart';

/// Persists a compact recent-sessions snapshot for the Android home-screen
/// widget and asks native to redraw it.
///
/// Privacy: the widget deliberately shows session titles. The recorded
/// notification rules (fixed copy only, no titles) protect the lock screen,
/// where content appears without the user's choice; a home-screen widget is
/// a surface the user explicitly placed, sized, and can remove, and it shows
/// titles, the project display name, and a short assistant reply excerpt chosen
/// by the owner. Excerpts are redacted and bounded before storage. User
/// prompts, tool input/output, reasoning, raw routing and errors are excluded.
class WidgetSessionSnapshot {
  WidgetSessionSnapshot({
    required this.prefs,
    Future<void> Function()? refreshNative,
    bool? isAndroid,
  }) : _refreshNative = refreshNative ?? _refreshViaChannel,
       _isAndroid = isAndroid ?? platformCapabilities.supportsHomeWidget;

  /// Read by the native widget as `flutter.oc.widgetSessions`.
  static const prefsKey = 'oc.widgetSessions';

  /// App-private connection routing, never part of the launcher snapshot.
  static String targetKey(String profileID) => 'oc.widgetTarget.$profileID';

  static Map<String, Object?>? _opaqueTarget(Map? target) =>
      target?['fingerprint'] is String
      ? {'fingerprint': target!['fingerprint']}
      : null;

  Future<bool> _saveTarget(
    String profileID,
    String scopeToken,
    Map? target,
  ) async {
    if (profileID.isEmpty) return target == null;
    final key = targetKey(profileID);
    if (_opaqueTarget(target) == null || scopeToken.isEmpty) {
      return !prefs.containsKey(key) || await prefs.remove(key);
    }
    final encoded = jsonEncode({
      'profileID': profileID,
      'scopeToken': scopeToken,
      'fingerprint': target!['fingerprint'],
      'directory': target['directory'],
      'workspace': target['workspace'],
    });
    return prefs.getString(key) == encoded ||
        await prefs.setString(key, encoded);
  }

  /// At most this many sessions are ever written: enough that a
  /// Conversations widget scoped to one project still has rows.
  static const maxSessions = 12;

  /// At most this many projects are offered when a widget is edited.
  static const maxProjects = 12;

  /// At most this many servers are listed for the Status widget.
  static const maxServers = 6;

  static const _channel = MethodChannel('oc/background');

  final SharedPreferences prefs;
  final Future<void> Function() _refreshNative;
  final bool _isAndroid;
  String? _lastWritten;
  Future<void> _writeTail = Future.value();

  /// Native service pause invalidates its disk copy outside the Dart cache.
  /// Force the next event publication to restore the complete current truth.
  void invalidateNativeCopy() {
    _lastWritten = null;
  }

  Future<T> _serialize<T>(Future<T> Function() action) {
    final next = _writeTail.then((_) => action());
    _writeTail = next.then<void>((_) {}, onError: (Object _, StackTrace _) {});
    return next;
  }

  /// Redraws the home-screen widget. The attention tile snapshot reuses it so
  /// only this file and live_background.dart talk to `oc/background`.
  static Future<void> refreshHomeWidget() => _refreshViaChannel();

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
    String? untitledLabel,
    AppLocalizations? l10n,
    String scopeToken = '',
    String lastProjectID = '',
    int pendingCount = 0,
    Map<String, Map<String, Object?>> details = const {},
    List<WidgetOtherConversation> others = const [],
    List<Map<String, Object?>> teamRows = const [],
    WidgetActionResult? actionResult,
    Map<String, int> appTheme = const {},
    Map<String, Object?>? onDemand,
    bool preserveForOnDemand = false,
    String server = '',
    List<Map<String, Object?>> servers = const [],
    List<String> projects = const [],
  }) async {
    if (!_isAndroid) return;
    // The widget has no widget tree: its caller supplies the app language.
    // Share the app's presentation so dated server placeholders never leak
    // into launcher titles, then apply the launcher privacy/length limits.
    final strings = l10n ?? lookupAppLocalizations(const Locale('en'));
    int rankOf(String id, bool needsYou) =>
        needsYou || details[id]?['requestID'] != null
        ? 0
        : busySessions.contains(id)
        ? 1
        : 2;
    // Rows of this connection keep their rich details; conversations of other
    // projects and connections are plain (title, project, time, busy) and have
    // no inline actions. With none of the latter the order is unchanged.
    final candidates = <(int, int, int, Map<String, Object?>)>[
      for (final (index, session) in sessions.indexed)
        (
          rankOf(session.id, false),
          session.time?.updated ?? session.time?.created ?? 0,
          index,
          {
            'id': session.id,
            'title': safeWidgetTitle(
              presentedSessionTitleText(
                session.title,
                fallback: untitledLabel ?? strings.workspaceNewSession,
                l10n: strings,
              ),
            ),
            'busy': busySessions.contains(session.id),
            'updatedAt': session.time?.updated ?? session.time?.created ?? 0,
            if (safeWidgetTitle(server) case final name when name.isNotEmpty)
              'server': name,
            ..._safeWidgetDetails(details[session.id]),
          },
        ),
      for (final (index, other) in others.indexed)
        (
          other.needsYou ? 0 : (other.busy ? 1 : 2),
          other.updatedAt,
          sessions.length + index,
          {
            'id': other.id,
            'title': safeWidgetTitle(
              presentedSessionTitleText(
                other.title,
                fallback: untitledLabel ?? strings.workspaceNewSession,
                l10n: strings,
              ),
            ),
            'busy': other.busy,
            'updatedAt': other.updatedAt,
            if (safeWidgetTitle(other.server) case final name
                when name.isNotEmpty)
              'server': name,
            // Waiting on the person, answerable only in the app: no request
            // identity crosses from another connection.
            if (other.needsYou) 'needsYou': true,
            ..._safeWidgetDetails({'project': other.project}),
          },
        ),
    ];
    candidates.sort((a, b) {
      final byRank = a.$1.compareTo(b.$1);
      if (byRank != 0) return byRank;
      if (others.isNotEmpty) {
        final byTime = b.$2.compareTo(a.$2);
        if (byTime != 0) return byTime;
      }
      return a.$3.compareTo(b.$3);
    });
    final entries = [for (final c in candidates.take(maxSessions)) c.$4];
    final otherRunning = others.where((o) => o.busy).length;
    final otherNeedsYou = others.where((o) => o.needsYou).length;
    final payload = jsonEncode({
      'version': 2,
      'onDemand': ?_opaqueTarget(onDemand),
      'appTheme': appTheme,
      'scopeToken': scopeToken,
      'lastProjectID': lastProjectID,
      'runningCount': busySessions.length + otherRunning,
      'pendingCount': pendingCount + otherNeedsYou,
      'connected': connected,
      'profileID': profileID,
      // The Status widget's server lines: a name, its state and since when.
      'servers': [
        for (final row in servers.take(maxServers))
          {
            'name': safeWidgetTitle(row['name'] as String? ?? ''),
            'state': switch (row['state']) {
              'online' => 'online',
              'connecting' => 'connecting',
              _ => 'offline',
            },
            if (row['since'] case final int since when since > 0)
              'since': since,
            if (row['phone'] == true) 'phone': true,
          },
      ],
      // The projects a Conversations widget can be scoped to (its Edit).
      'projects': [
        for (final name in projects.map(safeWidgetProject).toSet())
          if (name.isNotEmpty) name,
      ].take(maxProjects).toList(),
      'sessions': [
        ...entries.take(maxSessions - teamRows.length.clamp(0, 1)),
        ...teamRows.take(1),
      ].toList(),
      if (actionResult != null) 'actionResult': actionResult.toJson(),
    });
    await _serialize(() async {
      // Lifecycle suspension retires only the transport. Keep the explicit tap
      // target and its proofs until fresh foreground events replace it; never
      // resurrect a removed snapshot or one belonging to another profile.
      if (preserveForOnDemand) {
        await prefs.reload();
        final raw = prefs.getString(prefsKey);
        if (raw == null) return;
        try {
          final old = jsonDecode(raw);
          if (old is! Map || old['profileID'] != profileID) return;
          final oldTarget = old['onDemand'];
          // Migrate the previous build's routing fields on the first app event,
          // including suspension, before publishing the opaque snapshot.
          if (oldTarget is Map &&
              (oldTarget.containsKey('directory') ||
                  oldTarget.containsKey('workspace')) &&
              !await _saveTarget(
                profileID,
                old['scopeToken'] as String,
                oldTarget,
              )) {
            return;
          }
          final paused = jsonEncode({
            ...old,
            'onDemand': oldTarget is Map ? _opaqueTarget(oldTarget) : null,
            'connected': false,
            // "Offline · last seen ..." counts from here.
            'pausedAt': DateTime.now().millisecondsSinceEpoch,
          });
          if (paused != raw && await prefs.setString(prefsKey, paused)) {
            _lastWritten = paused;
            await _refreshNative();
          }
        } catch (_) {}
        return;
      }
      // Publish routing first; mismatched scope/fingerprint makes a partially
      // failed write unusable rather than dispatching to a default directory.
      if (!await _saveTarget(profileID, scopeToken, onDemand)) return;
      if (payload == _lastWritten && prefs.getString(prefsKey) == payload) {
        return;
      }
      if (!await prefs.setString(prefsKey, payload)) return;
      _lastWritten = payload;
      await _refreshNative();
    });
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
  Future<WidgetSnapshotClear> clearForProfile(String profileID) =>
      _serialize(() => _clearForProfile(profileID));

  Future<WidgetSnapshotClear> _clearForProfile(String profileID) async {
    if (_isAndroid) {
      try {
        final result = await _channel.invokeMapMethod<String, dynamic>(
          'clearWidgetProfile',
          {'profileID': profileID},
        );
        // A host that answers with nothing has no native copy to clear; only
        // an explicit refusal from the native side counts as a failure.
        if (result != null && result['cleared'] != true) {
          return WidgetSnapshotClear.failed;
        }
      } on MissingPluginException {
        // Injected test writer, or unsupported host with no native state.
      } catch (_) {
        return WidgetSnapshotClear.failed;
      }
    }
    final hadTarget = prefs.containsKey(targetKey(profileID));
    try {
      if (hadTarget && !await prefs.remove(targetKey(profileID))) {
        return WidgetSnapshotClear.failed;
      }
    } catch (_) {
      return WidgetSnapshotClear.failed;
    }
    final raw = prefs.getString(prefsKey);
    if (raw == null) {
      return hadTarget
          ? WidgetSnapshotClear.cleared
          : WidgetSnapshotClear.nothingToClear;
    }
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
      return hadTarget
          ? WidgetSnapshotClear.cleared
          : WidgetSnapshotClear.nothingToClear;
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

/// A conversation of another project or connection, shown on the widget as a
/// plain row: no preview, no inline actions, no paths.
class WidgetOtherConversation {
  const WidgetOtherConversation({
    required this.id,
    required this.title,
    required this.project,
    required this.updatedAt,
    required this.busy,
    required this.needsYou,
    this.server = '',
  });

  /// The connection's display name ("MacBook"), never its address.
  final String server;
  final String id, title, project;
  final int updatedAt;
  final bool busy, needsYou;
}

/// Only reviewed compact fields cross into launcher storage.
Map<String, Object?> _safeWidgetDetails(Map<String, Object?>? details) {
  if (details == null) return const {};
  final todos = details['todos'];
  return {
    'onDemandActions': [
      if (details['onDemandActions'] is List)
        for (final action in details['onDemandActions'] as List)
          if (const {'allowOnce', 'reject', 'stop'}.contains(action)) action,
    ],
    if (details['runMarker'] case final String marker
        when marker.isNotEmpty && marker.length <= 512)
      'runMarker': marker,
    if (details['startedAt'] case final int value)
      'startedAt': value < 0 ? 0 : value,
    if (details['step'] case final String value) 'step': safeWidgetTitle(value),
    if (details['project'] case final String value)
      'project': safeWidgetProject(value),
    if (details['preview'] case final String value)
      'preview': safeWidgetPreview(value),
    if (details['model'] case final String value)
      'model': safeWidgetTitle(value),
    if (details['requestID'] case final String value
        when value.isNotEmpty && value.length <= 512)
      'requestID': value,
    if (details['ask'] case final Map ask) 'ask': _safeWidgetAsk(ask),
    // A question or form waits on the person; the app answers it, not the
    // widget, so it carries no request identity.
    if (details['needsYou'] == true) 'needsYou': true,
    'steerMode':
        const {
          'steer',
          'afterReply',
          'restart',
          'unknown',
        }.contains(details['steerMode'])
        ? details['steerMode']
        : 'unknown',
    'todos': todos is List
        ? [
            for (final task in todos.whereType<Map>().take(5))
              if (task['title'] is String && task['done'] is bool)
                {
                  'title': safeWidgetTitle(task['title'] as String),
                  'done': task['done'],
                },
          ]
        : const [],
    if (details['tasksDone'] case final int value)
      'tasksDone': value.clamp(0, 100000),
    if (details['tasksTotal'] case final int value)
      'tasksTotal': value.clamp(0, 100000),
  };
}

/// Control characters and the bidirectional overrides and isolates (which
/// can reorder a title on the launcher).
bool _unsafeInTitle(int c) =>
    c < 0x20 ||
    c == 0x7f ||
    c == 0x061c ||
    c == 0x200e ||
    c == 0x200f ||
    (c >= 0x202a && c <= 0x202e) ||
    (c >= 0x2066 && c <= 0x2069);

String safeWidgetTitle(String value) => _safeWidgetText(value, 80);

/// What a permission request says on the home screen: [kind] (run, write,
/// read, other), the question in the app's words, the file or command, its
/// full path and its folder. A command is never split into path parts, and a
/// request that names only a pattern gives a folder, never an invented file.
Map<String, Object?> widgetAskFor({
  required String permission,
  required List<String> patterns,
  required AppLocalizations strings,
  String? tool,
  String? target,
}) {
  final toolName = tool?.trim().toLowerCase() ?? '';
  final kind = switch (permission) {
    'bash' || 'shell' => 'run',
    'edit' || 'write' || 'multiedit' || 'patch' => 'write',
    'read' => 'read',
    'external_directory' => switch (toolName) {
      'write' || 'edit' || 'multiedit' || 'patch' || 'apply_patch' => 'write',
      'read' || 'glob' || 'grep' || 'list' || 'ls' => 'read',
      'bash' || 'shell' => 'run',
      _ => 'other',
    },
    _ => 'other',
  };
  final question = switch ((permission, kind)) {
    ('external_directory', 'write') => strings.chatRequestAskOutsideWrite,
    ('external_directory', 'read') => strings.chatRequestAskOutsideRead,
    ('external_directory', 'run') => strings.chatRequestAskOutsideRun,
    ('external_directory', _) => strings.chatRequestAskDirectory,
    ('bash' || 'shell', _) => strings.chatRequestAskCommand,
    ('read', _) => strings.chatRequestAskRead,
    ('edit' || 'write' || 'multiedit' || 'patch', _) =>
      strings.chatRequestAskEdit,
    _ => strings.e7PermissionAction7(permission),
  };
  final first = patterns.isEmpty ? '' : patterns.first.trim();
  final seen = target?.trim() ?? '';
  final base = <String, Object?>{'kind': kind, 'q': safeWidgetTitle(question)};
  // A command: the request's own pattern is the command when the call was
  // not seen. It is text, not a path.
  if (kind == 'run') {
    final command = seen.isNotEmpty
        ? seen
        : (permission == 'external_directory' ? '' : first);
    // An outside-the-project ask names the folder it reaches into in its pattern.
    final folder = permission == 'external_directory'
        ? first.replaceAll(RegExp(r'[\\/]?\*+$'), '')
        : '';
    return {
      ...base,
      if (command.isNotEmpty) 'file': safeWidgetTitle(command),
      if (folder.isNotEmpty) 'dir': safeWidgetPath(folder),
    };
  }
  if (seen.isEmpty) {
    final folder = first.replaceAll(RegExp(r'[\\/]?\*+$'), '');
    return {...base, if (folder.isNotEmpty) 'dir': safeWidgetPath(folder)};
  }
  final isPath = !seen.contains(' ') && seen.contains('/');
  final cut = isPath ? seen.lastIndexOf('/') : -1;
  return {
    ...base,
    'file': safeWidgetTitle(isPath ? seen.substring(cut + 1) : seen),
    if (isPath) 'path': safeWidgetPath(seen),
    if (isPath && cut > 0) 'dir': safeWidgetPath(seen.substring(0, cut)),
  };
}

/// A file path or folder on a launcher line: the same redaction as a title,
/// with room for a full path.
String safeWidgetPath(String value) => _safeWidgetText(value, 160);

/// The reviewed fields of a permission ask: kind, question, file, path, folder.
Map<String, Object?> _safeWidgetAsk(Map ask) {
  String text(String key, String Function(String) clean) =>
      ask[key] is String ? clean(ask[key] as String) : '';
  final kind = const {'run', 'write', 'read'}.contains(ask['kind'])
      ? ask['kind'] as String
      : 'other';
  return {
    'kind': kind,
    'q': text('q', safeWidgetTitle),
    'file': text('file', safeWidgetTitle),
    'path': text('path', safeWidgetPath),
    'dir': text('dir', safeWidgetPath),
  };
}

/// Only a display name, even when a caller accidentally supplies a path.
String safeWidgetProject(String value) {
  final parts = value.split(RegExp(r'[\\/]+')).where((p) => p.isNotEmpty);
  if (parts.isEmpty) return '';
  final name = safeWidgetTitle(parts.last);
  // A folder called "project" or "tmp" tells the person nothing; show no line.
  return _genericProjectNames.contains(name.toLowerCase()) ? '' : name;
}

const _genericProjectNames = {
  'project',
  'projects',
  'workspace',
  'tmp',
  'temp',
  'home',
  'root',
  'src',
};

String safeWidgetPreview(String value) =>
    _safeWidgetText(stripWidgetMarkdown(_withoutInlineReasoning(value)), 240);

/// Plain words for a launcher line: no backticks, emphasis marks, heading or
/// list markers, and links keep their label but not their address.
String stripWidgetMarkdown(String value) {
  var text = value
      .replaceAll(RegExp(r'^\s*```[^\n]*$', multiLine: true), '')
      .replaceAllMapped(RegExp(r'!?\[([^\]]*)\]\([^)]*\)'), (m) => m[1]!)
      .replaceAll(RegExp(r'^\s{0,3}#{1,6}\s+', multiLine: true), '')
      .replaceAll(RegExp(r'^\s{0,3}>\s?', multiLine: true), '')
      .replaceAll(RegExp(r'^\s*(?:[-*+]|\d+[.)])\s+', multiLine: true), '')
      .replaceAllMapped(RegExp(r'`+([^`]*)`+'), (m) => m[1]!)
      .replaceAllMapped(RegExp(r'(\*\*|__|~~)(.+?)\1'), (m) => m[2]!)
      .replaceAllMapped(
        RegExp(r'(?<![\w*])\*(?!\s)([^*\n]+?)(?<!\s)\*(?![\w*])'),
        (m) => m[1]!,
      )
      .replaceAllMapped(
        RegExp(r'(?<![\w_])_(?!\s)([^_\n]+?)(?<!\s)_(?![\w_])'),
        (m) => m[1]!,
      );
  text = text.replaceAll('`', '');
  return text;
}

final _widgetReasoningMarker = RegExp(
  r'<\s*(/?)\s*(?:think|thinking)\s*>',
  caseSensitive: false,
);

/// Some providers put reasoning delimiters inside an ordinary text part.
/// Hide complete and still-open blocks before truncating the excerpt. An
/// orphan close may follow truncated reasoning, so discard its prefix too.
/// This recognizes only these explicit delimiters, not arbitrary HTML/code.
String _withoutInlineReasoning(String value) {
  final visible = StringBuffer();
  var depth = 0;
  var offset = 0;
  for (final marker in _widgetReasoningMarker.allMatches(value)) {
    if (depth == 0) visible.write(value.substring(offset, marker.start));
    if (marker.group(1)!.isEmpty) {
      depth += 1;
    } else if (depth > 0) {
      depth -= 1;
      if (depth == 0) visible.write(' ');
    } else {
      visible.clear();
    }
    offset = marker.end;
  }
  if (depth == 0) visible.write(value.substring(offset));
  return visible.toString();
}

/// Latest assistant prose only: never user prompts, synthetic text, reasoning,
/// tool output or message errors. Used by bounded reads as well as app display.
String safeWidgetAssistantPreview(List<MessageWithParts> messages) {
  for (final message in messages.reversed) {
    if (message.info.role != 'assistant') continue;
    final text = message.parts
        .where((part) => part.type == 'text' && !part.synthetic)
        .map((part) => part.text)
        .join(' ');
    final preview = safeWidgetPreview(text);
    if (preview.isNotEmpty) return preview;
  }
  return '';
}

String _safeWidgetText(String value, int limit) {
  final safe = String.fromCharCodes(
    KitRedact.text(value).runes.map((c) => _unsafeInTitle(c) ? 0x20 : c),
  ).replaceAll(RegExp(r'\s+'), ' ').trim();
  return String.fromCharCodes(safe.runes.take(limit));
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
