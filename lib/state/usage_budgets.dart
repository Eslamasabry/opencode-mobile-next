import 'dart:convert';

import 'package:crypto/crypto.dart';
import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../domain/usage_statistics.dart';
import 'usage_overview.dart';
import 'budget_persistence.dart';

enum UsageBudgetUnit { usd, tokens }

/// Personal budgets for measured consumption; never subscription allowances.
/// Each rule belongs to the exact server, project, timezone and window start.
/// The upper bound advances on refresh; a new start is a different budget.
class UsageBudgets extends ChangeNotifier {
  final SharedPreferences preferences;
  final String profileId;
  final String source;
  final bool Function() isCurrent;
  final bool Function() isProfilePresent;
  Map<String, Map<String, dynamic>> _rules = {};
  bool _disposed = false;
  bool saving = false;
  bool failed = false;

  UsageBudgets({
    required this.preferences,
    required this.profileId,
    required String serverOrigin,
    required this.isCurrent,
    required this.isProfilePresent,
  }) : source = sha256.convert(utf8.encode(serverOrigin)).toString() {
    try {
      final raw = preferences.getString(key);
      if (raw == null) return;
      if (raw.length > 65536) throw const FormatException();
      final data = jsonDecode(raw) as Map<String, dynamic>;
      if (data['version'] != 1) throw const FormatException();
      final rules = data['rules'] as Map<String, dynamic>;
      if (rules.length > 64) throw const FormatException();
      for (final entry in rules.entries) {
        if (!_validStoredRule(entry.key, entry.value)) {
          failed = true;
          continue;
        }
        _rules[entry.key] = Map<String, dynamic>.from(entry.value as Map);
      }
    } catch (_) {
      _rules = {};
      failed = true;
    }
  }

  String get key => 'oc.consumptionBudgets.$profileId';
  bool get available => !_disposed && isCurrent();
  static bool validLimit(Object? value, UsageBudgetUnit unit) =>
      value is num &&
      value.isFinite &&
      value > 0 &&
      value <= 9007199254740991 &&
      (unit != UsageBudgetUnit.tokens || value == value.truncateToDouble());

  static bool _validStoredRule(String id, Object? value) {
    try {
      final rule = Map<String, dynamic>.from(value as Map);
      final unit = UsageBudgetUnit.values.byName(rule['unit'] as String);
      const fields = {
        'source',
        'project',
        'range',
        'from',
        'timezone',
        'unit',
        'limit',
      };
      if (!RegExp(r'^[a-f0-9]{64}$').hasMatch(id) ||
          rule.keys.toSet().length != fields.length ||
          !rule.keys.toSet().containsAll(fields) ||
          !RegExp(r'^[a-f0-9]{64}$').hasMatch(rule['source'] as String) ||
          (rule['project'] != null && rule['project'] is! String) ||
          rule['range'] is! String ||
          !UsageRange.values.any((range) => range.name == rule['range']) ||
          (rule['from'] != null && rule['from'] is! int) ||
          rule['timezone'] is! String ||
          (rule['timezone'] as String).trim().isEmpty ||
          !validLimit(rule['limit'], unit)) {
        return false;
      }
      final scope = {
        'source': rule['source'],
        'project': rule['project'],
        'range': rule['range'],
        'from': rule['from'],
        'timezone': rule['timezone'],
        'unit': rule['unit'],
      };
      return sha256.convert(utf8.encode(jsonEncode(scope))).toString() == id;
    } catch (_) {
      return false;
    }
  }

  Map<String, dynamic> _scope(UsageSnapshot snapshot, UsageBudgetUnit unit) => {
    'source': source,
    'project': snapshot.query.projectID,
    'range': snapshot.range.name,
    'from': snapshot.query.from,
    'timezone': snapshot.query.timezone,
    'unit': unit.name,
  };
  String _id(UsageSnapshot snapshot, UsageBudgetUnit unit) => sha256
      .convert(utf8.encode(jsonEncode(_scope(snapshot, unit))))
      .toString();

  bool _matchesScope(
    Map<String, dynamic> rule,
    UsageSnapshot snapshot,
    UsageBudgetUnit unit,
  ) {
    final scope = _scope(snapshot, unit);
    return rule.length == scope.length + 1 &&
        scope.entries.every((entry) => rule[entry.key] == entry.value);
  }

  num? limit(UsageSnapshot snapshot, UsageBudgetUnit unit) {
    if (!available) return null;
    final rule = _rules[_id(snapshot, unit)];
    return rule != null && _matchesScope(rule, snapshot, unit)
        ? rule['limit'] as num?
        : null;
  }

  Future<bool> save(
    UsageSnapshot snapshot,
    UsageBudgetUnit unit,
    num? value,
  ) async {
    if (!available || saving || (value != null && !validLimit(value, unit))) {
      return false;
    }
    final next = Map<String, Map<String, dynamic>>.of(_rules);
    final id = _id(snapshot, unit);
    if (value == null) {
      next.remove(id);
    } else {
      next[id] = {..._scope(snapshot, unit), 'limit': value};
    }
    saving = true;
    failed = false;
    notifyListeners();
    var success = false;
    var degraded = false;
    try {
      final durable = await BudgetPersistence.update(
        preferences: preferences,
        key: key,
        changes: {id: next[id]},
        isCurrent: () => available,
        isProfilePresent: isProfilePresent,
      );
      success = durable != null;
      if (durable != null) {
        degraded = durable.entries.any(
          (entry) => !_validStoredRule(entry.key, entry.value),
        );
        _rules = {
          for (final entry in durable.entries)
            if (_validStoredRule(entry.key, entry.value))
              entry.key: Map<String, dynamic>.from(entry.value as Map),
        };
      }
    } catch (_) {
      success = false;
    } finally {
      saving = false;
      failed = !success || degraded;
      if (!_disposed) notifyListeners();
    }
    return success;
  }

  @override
  void dispose() {
    _disposed = true;
    _rules = {};
    super.dispose();
  }

  Future<bool> clearAll() async {
    if (!available || saving) return false;
    saving = true;
    failed = false;
    notifyListeners();
    final result = await BudgetPersistence.update(
      preferences: preferences,
      key: key,
      changes: {},
      clear: true,
      isCurrent: () => available,
      isProfilePresent: isProfilePresent,
    );
    if (result != null) _rules = {};
    saving = false;
    failed = result == null;
    if (!_disposed) notifyListeners();
    return result != null;
  }
}
