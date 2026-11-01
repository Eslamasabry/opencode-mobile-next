import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';

import '../domain/termux_migration.dart';

/// Owns only the migration-offer dismissal flag. Profile deletion closes
/// admission and drains writes before discovering and removing scoped keys.
abstract final class TermuxMigrationOfferStore {
  static final _pending = Expando<Map<String, Future<void>>>();
  static final _closed = Expando<Set<String>>();
  static final _epochs = Expando<Map<String, int>>();

  static Future<void> dismiss(
    SharedPreferences prefs,
    String id, {
    required bool Function() mayWrite,
  }) {
    final epoch = _epochs[prefs]?[id] ?? 0;
    void check() {
      if (!mayWrite() ||
          (_closed[prefs]?.contains(id) ?? false) ||
          (_epochs[prefs]?[id] ?? 0) != epoch) {
        throw const TermuxMigrationException(TermuxMigrationFailure.cancelled);
      }
    }

    final pending = _pending[prefs] ??= {};
    final operation = (pending[id] ?? Future<void>.value()).then((_) async {
      check();
      if (!await prefs.setBool('oc.termuxMigrationOffer.$id', true)) {
        throw const TermuxMigrationException(TermuxMigrationFailure.storage);
      }
      check();
    });
    pending[id] = operation.then<void>(
      (_) {},
      onError: (Object _, StackTrace _) {},
    );
    return operation;
  }

  static Future<void> closeProfile(SharedPreferences prefs, String id) {
    (_closed[prefs] ??= {}).add(id);
    final epochs = _epochs[prefs] ??= {};
    epochs[id] = (epochs[id] ?? 0) + 1;
    return _pending[prefs]?[id] ?? Future<void>.value();
  }

  static Future<void> cancelDeletion(SharedPreferences prefs, String id) async {
    final epoch = _epochs[prefs]?[id];
    await _pending[prefs]?[id];
    try {
      await prefs.reload();
      final rows = jsonDecode(prefs.getString('oc.profiles') ?? 'null');
      if (rows is List &&
          rows.any((row) => row is Map && row['id'] == id) &&
          _epochs[prefs]?[id] == epoch) {
        _closed[prefs]?.remove(id);
      }
    } catch (_) {
      // Keep admission closed when durable ownership is uncertain.
    }
  }
}
