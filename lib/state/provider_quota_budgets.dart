import 'dart:convert';

import 'package:crypto/crypto.dart';
import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../domain/provider_quota.dart';
import 'budget_persistence.dart';

/// A personal percentage threshold, never a provider allowance or spend total.
class QuotaBudget {
  final double percent;
  final bool attention;
  final String? alertedWindow;
  const QuotaBudget(this.percent, {this.attention = false, this.alertedWindow});
}

/// Device-local rules; no credentials, measured usage or notification payloads.
/// The profile deletion sweep automatically owns `oc.budgets.<profileId>`.
class ProviderQuotaBudgets extends ChangeNotifier {
  final SharedPreferences preferences;
  final String profileId;
  final String source;
  final bool Function() isCurrent;
  final bool Function() isProfilePresent;
  final DateTime Function() clock;
  Map<String, QuotaBudget> _rules = {};
  bool _disposed = false;
  bool saving = false;
  bool failed = false;
  bool attentionVisible = false;
  int _generation = 0;

  ProviderQuotaBudgets({
    required this.preferences,
    required this.profileId,
    required String serverOrigin,
    required this.isCurrent,
    required this.isProfilePresent,
    DateTime Function()? clock,
  }) : source = sha256.convert(utf8.encode(serverOrigin)).toString(),
       clock = clock ?? DateTime.now {
    try {
      final raw = preferences.getString(key);
      if (raw == null) return;
      if (raw.length > 65536) throw const FormatException();
      final data = jsonDecode(raw) as Map<String, dynamic>;
      if (data['version'] != 1) throw const FormatException();
      final rules = data['rules'] as Map<String, dynamic>;
      if (rules.length > 64) throw const FormatException();
      for (final entry in rules.entries) {
        if (!RegExp(r'^[a-f0-9]{64}$').hasMatch(entry.key)) {
          throw const FormatException();
        }
        final value = entry.value as Map<String, dynamic>;
        final percent = value['percent'] as num;
        if (!percent.isFinite ||
            percent <= 0 ||
            percent > 100 ||
            value['unit'] != 'percentUsed' ||
            value['attention'] is! bool ||
            (value['alertedWindow'] != null &&
                (value['alertedWindow'] is! String ||
                    !RegExp(
                      r'^[a-f0-9]{64}$',
                    ).hasMatch(value['alertedWindow'])))) {
          throw const FormatException();
        }
        _rules[entry.key] = QuotaBudget(
          percent.toDouble(),
          attention: value['attention'] as bool,
          alertedWindow: value['alertedWindow'] as String?,
        );
      }
    } catch (_) {
      _rules = {};
      failed = true;
    }
  }

  String get key => 'oc.budgets.$profileId';
  bool get available => !_disposed && isCurrent();

  String _id(ProviderQuotaSnapshot snapshot, ProviderQuotaWindow window) =>
      sha256
          .convert(
            utf8.encode(
              jsonEncode([
                source,
                snapshot.provider.name,
                snapshot.account.ref,
                window.id,
                window.durationSeconds,
                'percentUsed',
              ]),
            ),
          )
          .toString();

  bool usable(ProviderQuotaSnapshot snapshot, ProviderQuotaWindow window) =>
      available &&
      quotaCollectionAvailable(snapshot.provider) &&
      snapshot.canShowWindows &&
      snapshot.account.ref != null &&
      window.status == QuotaWindowStatus.reported;

  QuotaBudget? rule(
    ProviderQuotaSnapshot snapshot,
    ProviderQuotaWindow window,
  ) => usable(snapshot, window) ? _rules[_id(snapshot, window)] : null;

  Future<bool> save(
    ProviderQuotaSnapshot snapshot,
    ProviderQuotaWindow window,
    QuotaBudget? budget,
  ) async {
    if (!usable(snapshot, window) ||
        saving ||
        (budget != null &&
            (!budget.percent.isFinite ||
                budget.percent <= 0 ||
                budget.percent > 100))) {
      return false;
    }
    final next = Map<String, QuotaBudget>.of(_rules);
    final id = _id(snapshot, window);
    if (budget == null) {
      next.remove(id);
    } else {
      final old = next[id];
      next[id] = QuotaBudget(
        budget.percent,
        attention: budget.attention,
        alertedWindow:
            old?.percent == budget.percent && old?.attention == budget.attention
            ? old?.alertedWindow
            : null,
      );
    }
    return _write(next);
  }

  Future<bool> _write(Map<String, QuotaBudget> next) async {
    if (!available || saving) return false;
    saving = true;
    failed = false;
    notifyListeners();
    var success = false;
    try {
      final changes = <String, dynamic>{};
      for (final id in {..._rules.keys, ...next.keys}) {
        final old = _rules[id];
        final value = next[id];
        if (identical(old, value)) continue;
        changes[id] = value == null
            ? null
            : {
                'unit': 'percentUsed',
                'percent': value.percent,
                'attention': value.attention,
                if (value.alertedWindow != null)
                  'alertedWindow': value.alertedWindow,
              };
      }
      final durable = await BudgetPersistence.update(
        preferences: preferences,
        key: key,
        changes: changes,
        isCurrent: () => available,
        isProfilePresent: isProfilePresent,
      );
      success = durable != null;
      if (durable != null) {
        _rules = {
          for (final entry in durable.entries)
            entry.key: QuotaBudget(
              (entry.value['percent'] as num).toDouble(),
              attention: entry.value['attention'] as bool,
              alertedWindow: entry.value['alertedWindow'] as String?,
            ),
        };
      }
    } catch (_) {
      success = false;
    } finally {
      saving = false;
      failed = !success;
      if (!_disposed) notifyListeners();
    }
    return success;
  }

  /// Only a successful, fresh read can produce attention. Persist dedupe before
  /// displaying, once per reported reset; unknown resets never rearm locally.
  Future<void> observe(
    ProviderQuotaSnapshot snapshot, {
    required bool stale,
  }) async {
    final generation = ++_generation;
    attentionVisible = false;
    if (!available ||
        saving ||
        stale ||
        !snapshot.canShowWindows ||
        snapshot.freshness != QuotaFreshness.fresh ||
        snapshot.isStale(clock()) ||
        clock().isBefore(snapshot.fetchedAt)) {
      return;
    }
    final next = Map<String, QuotaBudget>.of(_rules);
    var changed = false;
    for (final window in snapshot.windows) {
      final budget = rule(snapshot, window);
      if (budget == null ||
          !budget.attention ||
          window.usedPercent! < budget.percent ||
          (window.resetsAt != null && !clock().isBefore(window.resetsAt!))) {
        continue;
      }
      final marker = sha256
          .convert(
            utf8.encode(
              jsonEncode([
                _id(snapshot, window),
                window.resetsAt?.millisecondsSinceEpoch,
              ]),
            ),
          )
          .toString();
      if (budget.alertedWindow == marker) continue;
      next[_id(snapshot, window)] = QuotaBudget(
        budget.percent,
        attention: true,
        alertedWindow: marker,
      );
      changed = true;
    }
    if (changed &&
        await _write(next) &&
        available &&
        generation == _generation) {
      attentionVisible = true;
      notifyListeners();
    }
  }

  void clearAttention() {
    _generation++;
    attentionVisible = false;
  }

  Future<bool> clearAll() async {
    if (!available || saving) return false;
    saving = true;
    clearAttention();
    notifyListeners();
    final durable = await BudgetPersistence.update(
      preferences: preferences,
      key: key,
      changes: {},
      clear: true,
      isCurrent: () => available,
      isProfilePresent: isProfilePresent,
    );
    if (durable != null) _rules = {};
    failed = durable == null;
    saving = false;
    if (!_disposed) notifyListeners();
    return durable != null;
  }

  @override
  void dispose() {
    _disposed = true;
    clearAttention();
    _rules = {};
    super.dispose();
  }
}
