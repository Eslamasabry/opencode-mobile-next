import 'dart:async';
import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:uuid/uuid.dart';

import '../a2a/client.dart';
import '../domain/external_agent.dart';

ExternalAgentGateway createExternalAgentGateway() => A2aClient();

/// Separate agent identities, using the same `oc.<data>.<id>` storage convention.
/// Tombstones survive failed deletion so credentials can be removed on retry.
class ExternalAgentStore extends ChangeNotifier {
  final SharedPreferences prefs;
  final FlutterSecureStorage secure;
  ExternalAgentStore(this.prefs, this.secure);
  static Future<void>? _writes;
  static const registryKey = 'oc.externalAgents';
  String taskKey(String id) => 'oc.a2aTasks.$id';
  String credentialKey(String id) => 'oc.a2aBearer.$id';
  final Map<String, int> _revisions = {};
  bool _disposed = false;
  int revision(String id) => _revisions[id] ?? 0;
  List<ExternalAgentProfile> get profiles {
    try {
      final raw = prefs.getString(registryKey);
      if (raw == null) return [];
      if (raw.length > 256000) throw const FormatException();
      final json = jsonDecode(raw);
      if (json is! List || json.length > 20) throw const FormatException();
      return [
        for (final item in json)
          ExternalAgentProfile.fromJson(Map<String, dynamic>.from(item as Map)),
      ];
    } catch (_) {
      throw const ExternalAgentException(ExternalAgentIssue.storage);
    }
  }

  bool contains(String id) => profiles.any((p) => p.id == id && !p.deleting);
  List<ExternalTaskRecord> tasks(String id) {
    try {
      final raw = prefs.getString(taskKey(id));
      if (raw == null) return [];
      if (raw.length > 4 * 1024 * 1024) throw const FormatException();
      final json = jsonDecode(raw);
      if (json is! List || json.length > 30) throw const FormatException();
      return [
        for (final item in json)
          ExternalTaskRecord.fromJson(Map<String, dynamic>.from(item as Map)),
      ];
    } catch (_) {
      throw const ExternalAgentException(ExternalAgentIssue.storage);
    }
  }

  Future<T> _write<T>(Future<T> Function() action) {
    final result = Completer<T>();
    final previous = _writes;
    final gate = Completer<void>();
    _writes = gate.future;
    Future<void> execute() async {
      T? value;
      ExternalAgentException? failure;
      try {
        value = await action();
      } on ExternalAgentException catch (e) {
        failure = e;
      } catch (_) {
        failure = const ExternalAgentException(ExternalAgentIssue.storage);
      }
      if (!_disposed) notifyListeners();
      // Release the idle tail before completing the caller. A future retained
      // from an obsolete route/test zone must not own later store operations.
      if (identical(_writes, gate.future)) _writes = null;
      gate.complete();
      if (failure != null) {
        result.completeError(failure);
      } else {
        result.complete(value as T);
      }
    }

    if (previous == null) {
      unawaited(execute());
    } else {
      unawaited(previous.then((_) => execute()));
    }
    return result.future;
  }

  Future<void> _registry(List<ExternalAgentProfile> list) async {
    final encoded = jsonEncode(list.map((p) => p.toJson()).toList());
    if (encoded.length > 256000) {
      throw const ExternalAgentException(ExternalAgentIssue.storage);
    }
    if (!await prefs.setString(registryKey, encoded)) {
      throw const ExternalAgentException(ExternalAgentIssue.storage);
    }
  }

  Future<void> _taskRecords(String id, List<ExternalTaskRecord> records) async {
    final encoded = jsonEncode(records.map((r) => r.toJson()).toList());
    if (encoded.length > 4 * 1024 * 1024 ||
        !await prefs.setString(taskKey(id), encoded)) {
      throw const ExternalAgentException(ExternalAgentIssue.storage);
    }
  }

  Future<ExternalAgentProfile> add(ExternalAgentCard card, String token) =>
      _write(() async {
        if (!card.supported ||
            (card.auth == ExternalAgentAuth.bearer && token.trim().isEmpty)) {
          throw const ExternalAgentException(ExternalAgentIssue.unsupported);
        }
        final list = profiles;
        if (list.length >= 20) {
          throw const ExternalAgentException(ExternalAgentIssue.storage);
        }
        final id = const Uuid().v4();
        await _registry([
          ...list,
          ExternalAgentProfile(id, card, deleting: true),
        ]);
        if (card.auth == ExternalAgentAuth.bearer) {
          await secure.write(key: credentialKey(id), value: token.trim());
        }
        final profile = ExternalAgentProfile(id, card);
        await _registry([...list, profile]);
        return profile;
      });
  Future<String?> credential(String id) async {
    if (!contains(id)) {
      throw const ExternalAgentException(ExternalAgentIssue.scope);
    }
    try {
      return await secure.read(key: credentialKey(id));
    } catch (_) {
      throw const ExternalAgentException(ExternalAgentIssue.authentication);
    }
  }

  Future<void> updateCredential(String id, String value) => _write(() async {
    if (!contains(id) || value.trim().isEmpty) {
      throw const ExternalAgentException(ExternalAgentIssue.scope);
    }
    _revisions[id] = revision(id) + 1;
    await secure.write(key: credentialKey(id), value: value.trim());
  });
  Future<void> saveTask(
    String id,
    ExternalTaskRecord record, {
    bool existingOnly = false,
  }) => _write(() async {
    if (!contains(id)) {
      throw const ExternalAgentException(ExternalAgentIssue.scope);
    }
    final records = tasks(id);
    final at = records.indexWhere((r) => r.localId == record.localId);
    if (at < 0) {
      if (existingOnly) {
        throw const ExternalAgentException(ExternalAgentIssue.scope);
      }
      if (records.length >= 30) {
        throw const ExternalAgentException(ExternalAgentIssue.storage);
      }
      records.insert(0, record);
    } else {
      records[at] = record;
    }
    await _taskRecords(id, records);
  });
  Future<void> removeTask(String id, String localId) => _write(() async {
    if (!contains(id)) {
      throw const ExternalAgentException(ExternalAgentIssue.scope);
    }
    final records = tasks(id)..removeWhere((r) => r.localId == localId);
    await _taskRecords(id, records);
  });
  Future<void> saveDraft(String id, String localId, String text) =>
      _write(() async {
        if (!contains(id)) {
          throw const ExternalAgentException(ExternalAgentIssue.scope);
        }
        if (text.length > 16000) {
          throw const ExternalAgentException(ExternalAgentIssue.storage);
        }
        final records = tasks(id);
        final at = records.indexWhere((record) => record.localId == localId);
        if (at < 0 || records[at].task != null || records[at].uncertain) {
          throw const ExternalAgentException(ExternalAgentIssue.scope);
        }
        records[at] = records[at].withDraft(text);
        await _taskRecords(id, records);
      });
  Future<void> claimSend(String id, ExternalTaskRecord expected) =>
      _write(() async {
        if (!contains(id)) {
          throw const ExternalAgentException(ExternalAgentIssue.scope);
        }
        final records = tasks(id);
        final at = records.indexWhere((r) => r.localId == expected.localId);
        if (at < 0 ||
            records[at].uncertain ||
            records[at].draft != expected.draft ||
            jsonEncode(records[at].task?.toJson()) !=
                jsonEncode(expected.task?.toJson())) {
          throw const ExternalAgentException(ExternalAgentIssue.uncertain);
        }
        records[at] = expected.withTask(expected.task, uncertain: true);
        await _taskRecords(id, records);
      });
  Future<void> delete(String id) {
    _revisions[id] = revision(id) + 1;
    return _write(() async {
      final list = profiles;
      final at = list.indexWhere((p) => p.id == id);
      if (at < 0) return;
      list[at] = ExternalAgentProfile(id, list[at].card, deleting: true);
      await _registry(list);
      await secure.delete(key: credentialKey(id));
      if (!await prefs.remove(taskKey(id))) {
        throw const ExternalAgentException(ExternalAgentIssue.storage);
      }
      list.removeAt(at);
      await _registry(list);
    });
  }

  @override
  void dispose() {
    _disposed = true;
    super.dispose();
  }
}

class ExternalTaskController extends ChangeNotifier {
  final ExternalAgentStore store;
  final ExternalAgentProfile profile;
  final ExternalAgentGateway gateway;
  ExternalTaskRecord record;
  final int _revision;
  bool _disposed = false;
  bool busy = false;
  bool fresh = false;
  bool foreground = true;
  bool cancelUnconfirmed = false;
  ExternalAgentIssue? issue;
  DateTime? checkedAt;
  ExternalTaskController({
    required this.store,
    required this.profile,
    required this.record,
    ExternalAgentGateway? gateway,
  }) : gateway = gateway ?? A2aClient(),
       _revision = store.revision(profile.id) {
    store.addListener(_storeChanged);
  }
  bool get current =>
      !_disposed &&
      store.contains(profile.id) &&
      store.revision(profile.id) == _revision &&
      store.tasks(profile.id).any((r) => r.localId == record.localId);
  void _storeChanged() {
    if (!current) {
      fresh = false;
      issue = ExternalAgentIssue.scope;
      gateway.close();
      notifyListeners();
    }
  }

  void _check() {
    if (!current) throw const ExternalAgentException(ExternalAgentIssue.scope);
  }

  bool get canContinue =>
      current &&
      foreground &&
      !busy &&
      fresh &&
      !record.uncertain &&
      record.task?.state == ExternalTaskState.inputRequired;
  bool get canCancel =>
      current &&
      foreground &&
      !busy &&
      fresh &&
      record.task?.id != null &&
      record.task?.terminal == false &&
      record.task?.state != ExternalTaskState.unknown;
  Future<String?> _credential() async {
    final token = await store.credential(profile.id);
    _check();
    return token;
  }

  Future<void> _save(ExternalTaskRecord value) async {
    _check();
    // Update visible receipt even if local persistence fails. Never turn a
    // received task ID into an untracked automatic resend opportunity.
    record = value;
    await store.saveTask(profile.id, value, existingOnly: true);
    _check();
  }

  Future<void> _run(Future<void> Function() action) async {
    if (!current || busy || !foreground) return;
    busy = true;
    issue = null;
    notifyListeners();
    try {
      await action();
    } on ExternalAgentException catch (e) {
      issue = e.issue;
      fresh = false;
    } catch (_) {
      issue = ExternalAgentIssue.unavailable;
      fresh = false;
    } finally {
      busy = false;
      if (!_disposed) notifyListeners();
    }
  }

  Future<void> submit(String text, {bool continuation = false}) {
    if (continuation && !canContinue) return Future.value();
    if (!continuation && (record.task != null || record.uncertain)) {
      return Future.value();
    }
    return _run(() async {
      final previous = record.task;
      final token = await _credential();
      // Persist unresolved state BEFORE any mutating request. On restart it
      // stays uncertain until a known task can be queried, never resent.
      await store.claimSend(profile.id, record);
      record = record.withTask(previous, uncertain: true);
      _check();
      if (!foreground) {
        throw const ExternalAgentException(ExternalAgentIssue.uncertain);
      }
      final task = await gateway.send(
        profile.card,
        token,
        text,
        continuation: continuation ? previous : null,
      );
      _check();
      await _save(record.withTask(task));
      fresh = foreground;
      checkedAt = DateTime.now();
    });
  }

  Future<void> refresh() => _run(() async {
    final previous = record.task;
    if (previous?.id == null) return;
    fresh = false;
    final token = await _credential();
    final task = await gateway.getTask(profile.card, token, previous!.id!);
    _check();
    if (task.contextId != previous.contextId) {
      throw const ExternalAgentException(ExternalAgentIssue.invalidResponse);
    }
    final unresolved =
        record.uncertain &&
        task.state == ExternalTaskState.inputRequired &&
        task.statusMessageId == previous.statusMessageId;
    await _save(record.withTask(task, uncertain: unresolved));
    fresh = foreground;
    checkedAt = DateTime.now();
  });
  Future<void> cancel() {
    if (!canCancel) return Future.value();
    return _run(() async {
      final previous = record.task!;
      final token = await _credential();
      // Re-query immediately before cancel; a terminal task is not restarted.
      final latest = await gateway.getTask(profile.card, token, previous.id!);
      _check();
      if (latest.contextId != previous.contextId) {
        throw const ExternalAgentException(ExternalAgentIssue.invalidResponse);
      }
      if (latest.terminal) {
        await _save(record.withTask(latest));
        fresh = foreground;
        return;
      }
      if (!foreground) return;
      final task = await gateway.cancel(profile.card, token, previous.id!);
      _check();
      if (task.contextId != previous.contextId) {
        throw const ExternalAgentException(ExternalAgentIssue.invalidResponse);
      }
      await _save(record.withTask(task, uncertain: record.uncertain));
      cancelUnconfirmed = !task.terminal;
      fresh = foreground;
      checkedAt = DateTime.now();
    });
  }

  void background() {
    foreground = false;
    fresh = false;
    if (!_disposed) notifyListeners();
  }

  Future<void> resume() {
    foreground = true;
    return refresh();
  }

  @override
  void dispose() {
    _disposed = true;
    store.removeListener(_storeChanged);
    gateway.close();
    super.dispose();
  }
}
