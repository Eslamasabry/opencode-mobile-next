import 'dart:convert';
import 'dart:io';

import 'package:crypto/crypto.dart';

import 'common.dart';

bool ownedSessionID(Object? value) =>
    value is String &&
    RegExp(r'^ses_[A-Za-z0-9]{1,128}$').stringMatch(value) == value;

/// Durable intent and exact IDs; contains no messages, provider data or secrets.
class SessionOwnership {
  final File file;
  final Map<String, dynamic> _data;
  SessionOwnership._(this.file, this._data);

  String get runID => _data['runID'] as String;
  String get engine => _data['engine'] as String;
  String get directory => _data['directory'] as String;
  String get titlePrefix => _data['titlePrefix'] as String;
  int get appUID => _data['appUID'] as int;
  bool get legacy => _data['legacy'] as bool;
  List<String> get sessionIDs => List<String>.unmodifiable(
    (_data['ownedSessions'] as List).cast<String>(),
  );

  static String filename(
    String runID,
    String engine,
    String caseName,
    String attempt,
  ) =>
      '$runID-$engine-$caseName-${sha256.convert(utf8.encode(attempt)).toString().substring(0, 16)}-ownership.json';

  static SessionOwnership create(
    File file, {
    required String runID,
    required String sourceRevision,
    required String attemptID,
    required String engine,
    required String caseName,
    required int appUID,
    required int appBuild,
    bool legacy = false,
    List<String> sessionIDs = const [],
  }) {
    final data = <String, dynamic>{
      'schemaVersion': 1,
      'runID': runID,
      'sourceRevision': sourceRevision,
      'attemptID': attemptID,
      'engine': engine,
      'case': caseName,
      'appUID': appUID,
      'appBuild': appBuild,
      'directory': '/root/projects/$runID',
      'titlePrefix': legacy ? runID : '$runID-$engine-$caseName',
      'legacy': legacy,
      'ownedSessions': sessionIDs.toSet().toList(),
    };
    _validate(data);
    _safe(file);
    if (file.existsSync()) {
      final previous = read(file);
      final identity = Map<String, dynamic>.from(previous._data)
        ..['ownedSessions'] = data['ownedSessions'];
      if (jsonEncode(identity) != jsonEncode(data)) {
        throw const ProbeFailure('ownership_intent_conflict');
      }
      return previous;
    }
    final ledger = SessionOwnership._(file, data);
    ledger._write();
    return ledger;
  }

  static SessionOwnership read(File file) {
    _safe(file);
    try {
      if (file.lengthSync() > 64 * 1024) {
        throw const ProbeFailure('ownership_evidence_too_large');
      }
      final data = jsonDecode(file.readAsStringSync());
      if (data is! Map<String, dynamic>) {
        throw const ProbeFailure('ownership_evidence_invalid');
      }
      _validate(data);
      return SessionOwnership._(file, data);
    } on ProbeFailure {
      rethrow;
    } catch (_) {
      throw const ProbeFailure('ownership_evidence_invalid');
    }
  }

  Future<void> recordCreated(String id) async {
    if (!ownedSessionID(id)) {
      throw const ProbeFailure('ownership_session_invalid');
    }
    final ids = sessionIDs.toSet()..add(id);
    if (ids.length > 128) throw const ProbeFailure('ownership_budget_exceeded');
    _data['ownedSessions'] = ids.toList();
    _write();
  }

  /// Call only after DELETE success or a confirmed absent exact session ID.
  Future<void> markDeleted(String id) async {
    if (!ownedSessionID(id)) {
      throw const ProbeFailure('ownership_session_invalid');
    }
    _data['ownedSessions'] = sessionIDs.where((value) => value != id).toList();
    _write();
  }

  /// The caller must have completed bounded discovery before removing intent.
  void removeIfEmpty() {
    if (sessionIDs.isNotEmpty) {
      throw const ProbeFailure('ownership_cleanup_incomplete');
    }
    _safe(file);
    file.deleteSync();
  }

  bool matchesSession(Map<String, dynamic> session) {
    final id = session['id'];
    if (!ownedSessionID(id)) return false;
    final location = session['location'];
    final actualDirectory =
        session['directory'] ??
        (location is Map ? location['directory'] : null);
    if (actualDirectory != directory || session['title'] is! String) {
      return false;
    }
    if (legacy) {
      if (!sessionIDs.contains(id)) return false;
    }
    return matchesTitle(session['title']);
  }

  bool matchesTitle(Object? value) {
    if (value is! String) return false;
    final title = value;
    if (legacy) {
      final pattern = engine == 'opencode'
          ? '^${RegExp.escape(runID)}-oc1-[A-Za-z0-9_-]{1,160}\$'
          : '^${RegExp.escape(runID)}(?:-[1-9][0-9]{0,5})?\$';
      return RegExp(pattern).stringMatch(title) == title;
    }
    final pattern = '^${RegExp.escape(titlePrefix)}-session-[1-9][0-9]{0,5}\$';
    return RegExp(pattern).stringMatch(title) == title;
  }

  void _write() {
    try {
      _safe(file);
      final staged = File('${file.path}.pending');
      _safe(staged);
      staged.writeAsStringSync('${jsonEncode(_data)}\n', flush: true);
      staged.renameSync(file.path);
    } on ProbeFailure {
      rethrow;
    } catch (_) {
      throw const ProbeFailure('ownership_write_failed');
    }
  }

  static void _safe(File file) {
    if (FileSystemEntity.isLinkSync(file.path)) {
      throw const ProbeFailure('ownership_path_unsafe');
    }
    var parent = file.absolute.parent;
    while (true) {
      if (FileSystemEntity.isLinkSync(parent.path)) {
        throw const ProbeFailure('ownership_path_unsafe');
      }
      final next = parent.parent;
      if (next.path == parent.path) break;
      parent = next;
    }
  }

  static void _validate(Map<String, dynamic> data) {
    const keys = {
      'schemaVersion',
      'runID',
      'sourceRevision',
      'attemptID',
      'engine',
      'case',
      'appUID',
      'appBuild',
      'directory',
      'titlePrefix',
      'legacy',
      'ownedSessions',
    };
    final run = data['runID'];
    final engine = data['engine'];
    final scenario = data['case'];
    if (data.keys.toSet().difference(keys).isNotEmpty ||
        keys.difference(data.keys.toSet()).isNotEmpty ||
        data['schemaVersion'] is! int ||
        data['schemaVersion'] != 1 ||
        run is! String ||
        RegExp(r'^fq3-[A-Za-z0-9_-]{1,80}$').stringMatch(run) != run ||
        data['sourceRevision'] is! String ||
        RegExp(
              r'^[0-9a-f]{40}$',
            ).stringMatch(data['sourceRevision'] as String) !=
            data['sourceRevision'] ||
        data['attemptID'] is! String ||
        RegExp(
              r'^[A-Za-z0-9_-]{1,160}$',
            ).stringMatch(data['attemptID'] as String) !=
            data['attemptID'] ||
        !const {'opencode', 'opencode2'}.contains(engine) ||
        !const {
          'stream',
          'reconnect',
          'model',
          'abort',
          'allow',
          'deny',
          'image',
          'cards',
        }.contains(scenario) ||
        data['appUID'] is! int ||
        (data['appUID'] as int) < 10000 ||
        data['appBuild'] is! int ||
        !const {2195, 2196, 2197, 2202}.contains(data['appBuild']) ||
        data['legacy'] is! bool ||
        data['directory'] != '/root/projects/$run' ||
        data['titlePrefix'] !=
            (data['legacy'] == true ? run : '$run-$engine-$scenario') ||
        data['ownedSessions'] is! List ||
        (data['ownedSessions'] as List).length > 128 ||
        !(data['ownedSessions'] as List).every(ownedSessionID)) {
      throw const ProbeFailure('ownership_evidence_invalid');
    }
  }
}
