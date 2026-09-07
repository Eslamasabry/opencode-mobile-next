import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/provider_quota.dart';
import 'package:opencode_mobile/state/provider_quota_budgets.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:shared_preferences_platform_interface/shared_preferences_platform_interface.dart';

import 'provider_quota_test.dart' show providerQuotaFixture;

class _Store extends InMemorySharedPreferencesStore {
  _Store() : super.withData({});
  bool refuse = false;
  Completer<void>? gate;
  @override
  Future<bool> setValue(String type, String key, Object value) async {
    await gate?.future;
    return refuse ? false : super.setValue(type, key, value);
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late SharedPreferences prefs;
  late _Store platform;
  late ProviderQuotaBudgets budgets;
  var present = true;
  var current = true;
  final now = DateTime.fromMillisecondsSinceEpoch(1000000, isUtc: true);
  ProviderQuotaBudgets make({String origin = 'https://synthetic.example'}) =>
      ProviderQuotaBudgets(
        preferences: prefs,
        profileId: 'profile',
        serverOrigin: origin,
        isCurrent: () => current,
        isProfilePresent: () => present,
        clock: () => now,
      );
  ProviderQuotaSnapshot snapshot({
    String account = 'a',
    int reset = 1300000,
    double used = 100,
    String status = 'ok',
  }) {
    final value = providerQuotaFixture();
    (value['account'] as Map)['ref'] = account * 64;
    final window = (value['windows'] as List).first as Map;
    window['usedPercent'] = used;
    window['resetsAtMs'] = reset;
    if (status != 'ok') {
      value['status'] = status;
      value['windows'] = <Object>[];
      value['ordinaryUsageAllowed'] = null;
    }
    return ProviderQuotaSnapshot.fromJson(value);
  }

  setUp(() async {
    final previous = SharedPreferencesStorePlatform.instance;
    addTearDown(() => SharedPreferencesStorePlatform.instance = previous);
    SharedPreferences.setMockInitialValues({});
    platform = _Store();
    SharedPreferencesStorePlatform.instance = platform;
    prefs = await SharedPreferences.getInstance();
    present = current = true;
    budgets = make();
    addTearDown(() => budgets.dispose());
  });

  test(
    'threshold survives restart with account, source and unit isolation',
    () async {
      final data = snapshot();
      expect(
        await budgets.save(data, data.windows.first, const QuotaBudget(75)),
        isTrue,
      );
      final restored = make();
      addTearDown(restored.dispose);
      expect(restored.rule(data, data.windows.first)?.percent, 75);
      final other = snapshot(account: 'b');
      expect(restored.rule(other, other.windows.first), isNull);
      final moved = make(origin: 'https://different.example');
      addTearDown(moved.dispose);
      expect(moved.rule(data, data.windows.first), isNull);
      expect(prefs.getString(budgets.key), contains('percentUsed'));
      expect(prefs.getString(budgets.key), isNot(contains('usedPercent')));
    },
  );

  test('failed write retains previous rule and durable preference', () async {
    final data = snapshot();
    await budgets.save(data, data.windows.first, const QuotaBudget(75));
    platform.refuse = true;
    expect(
      await budgets.save(data, data.windows.first, const QuotaBudget(90)),
      isFalse,
    );
    expect(budgets.rule(data, data.windows.first)?.percent, 75);
    expect(budgets.failed, isTrue);
    final restored = make();
    addTearDown(restored.dispose);
    expect(restored.rule(data, data.windows.first)?.percent, 75);
  });

  test(
    'attention requires opt-in fresh evidence and dedupes across restart',
    () async {
      final data = snapshot();
      await budgets.save(data, data.windows.first, const QuotaBudget(90));
      await budgets.observe(data, stale: false);
      expect(budgets.attentionVisible, isFalse);
      await budgets.save(
        data,
        data.windows.first,
        const QuotaBudget(90, attention: true),
      );
      await budgets.observe(data, stale: true);
      expect(budgets.attentionVisible, isFalse);
      await budgets.observe(snapshot(status: 'rateLimited'), stale: false);
      expect(budgets.attentionVisible, isFalse);
      await budgets.observe(data, stale: false);
      expect(budgets.attentionVisible, isTrue);
      final restored = make();
      addTearDown(restored.dispose);
      await restored.observe(data, stale: false);
      expect(restored.attentionVisible, isFalse);
      await restored.observe(snapshot(reset: 1400000), stale: false);
      expect(restored.attentionVisible, isTrue);
    },
  );

  test(
    'profile deletion sweeps rules and a pending save cannot resurrect them',
    () async {
      final data = snapshot();
      await budgets.save(data, data.windows.first, const QuotaBudget(75));
      final store = ProfileStore(prefs: prefs);
      expect(
        store.profileScopedPreferenceKeys('profile'),
        contains(budgets.key),
      );
      platform.gate = Completer<void>();
      final save = budgets.save(
        data,
        data.windows.first,
        const QuotaBudget(90),
      );
      present = current = false;
      expect(await store.removeScopedPreferences('profile'), isEmpty);
      platform.gate!.complete();
      expect(await save, isFalse);
      expect(prefs.containsKey(budgets.key), isFalse);
      expect(budgets.rule(data, data.windows.first), isNull);
    },
  );

  test(
    'unknown resets never rearm and detached sources cannot alert',
    () async {
      final value = providerQuotaFixture(provider: QuotaProvider.glm);
      final window = (value['windows'] as List).first as Map<String, dynamic>;
      window.remove('durationSeconds');
      window.remove('resetsAtMs');
      window['usedPercent'] = 100;
      final data = ProviderQuotaSnapshot.fromJson(value);
      await budgets.save(
        data,
        data.windows.first,
        const QuotaBudget(90, attention: true),
      );
      await budgets.observe(data, stale: false);
      expect(budgets.attentionVisible, isTrue);
      final restored = make();
      addTearDown(restored.dispose);
      await restored.observe(data, stale: false);
      expect(restored.attentionVisible, isFalse);
      current = false;
      await budgets.observe(data, stale: false);
      expect(budgets.attentionVisible, isFalse);
      expect(budgets.rule(data, data.windows.first), isNull);
    },
  );
}
