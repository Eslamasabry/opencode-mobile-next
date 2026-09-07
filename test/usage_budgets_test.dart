import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/usage_statistics.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/usage_budgets.dart';
import 'package:opencode_mobile/state/usage_overview.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:shared_preferences_platform_interface/shared_preferences_platform_interface.dart';

import 'usage_statistics_test.dart' show stats;

class _Store extends InMemorySharedPreferencesStore {
  _Store() : super.withData({});
  bool refuse = false;
  Completer<void>? gate;
  Completer<void>? started;
  @override
  Future<bool> setValue(String type, String key, Object value) async {
    if (started?.isCompleted == false) started!.complete();
    await gate?.future;
    return refuse ? false : super.setValue(type, key, value);
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late SharedPreferences prefs;
  late _Store platform;
  late UsageBudgets budgets;
  var current = true, present = true;
  UsageBudgets make({String origin = 'https://synthetic.example'}) =>
      UsageBudgets(
        preferences: prefs,
        profileId: 'profile',
        serverOrigin: origin,
        isCurrent: () => current,
        isProfilePresent: () => present,
      );
  UsageSnapshot sample({
    int from = 100,
    int to = 200,
    String? project,
    String timezone = 'UTC',
  }) => UsageSnapshot(
    statistics: stats(),
    query: UsageQuery(
      from: from,
      to: to,
      timezone: timezone,
      projectID: project,
    ),
    range: UsageRange.today,
    scope: project == null ? UsageScope.allProjects : UsageScope.currentProject,
    fetchedAt: DateTime.fromMillisecondsSinceEpoch(to),
  );

  setUp(() async {
    final previous = SharedPreferencesStorePlatform.instance;
    addTearDown(() => SharedPreferencesStorePlatform.instance = previous);
    SharedPreferences.setMockInitialValues({});
    platform = _Store();
    SharedPreferencesStorePlatform.instance = platform;
    prefs = await SharedPreferences.getInstance();
    current = present = true;
    budgets = make();
    addTearDown(() => budgets.dispose());
  });

  test(
    'restart keeps measured USD and token budgets in their exact scopes',
    () async {
      final data = sample();
      expect(await budgets.save(data, UsageBudgetUnit.usd, 12.5), isTrue);
      expect(await budgets.save(data, UsageBudgetUnit.tokens, 10000), isTrue);
      final restored = make();
      addTearDown(restored.dispose);
      expect(restored.limit(sample(to: 250), UsageBudgetUnit.usd), 12.5);
      expect(restored.limit(data, UsageBudgetUnit.tokens), 10000);
      for (final other in [
        sample(from: 101),
        sample(project: 'other'),
        sample(timezone: 'Asia/Dubai'),
      ]) {
        expect(restored.limit(other, UsageBudgetUnit.usd), isNull);
      }
      final moved = make(origin: 'https://other.example');
      addTearDown(moved.dispose);
      expect(moved.limit(data, UsageBudgetUnit.usd), isNull);
      expect(await budgets.save(data, UsageBudgetUnit.tokens, 1.5), isFalse);
      expect(
        await budgets.save(data, UsageBudgetUnit.usd, double.infinity),
        isFalse,
      );
    },
  );

  test(
    'failed write preserves durable previous budget and deletion sweeps it',
    () async {
      final data = sample();
      await budgets.save(data, UsageBudgetUnit.usd, 10);
      platform.refuse = true;
      expect(await budgets.save(data, UsageBudgetUnit.usd, 20), isFalse);
      expect(budgets.limit(data, UsageBudgetUnit.usd), 10);
      final restored = make();
      addTearDown(restored.dispose);
      expect(restored.limit(data, UsageBudgetUnit.usd), 10);
      final profileStore = ProfileStore(prefs: prefs);
      expect(
        profileStore.profileScopedPreferenceKeys('profile'),
        contains(budgets.key),
      );
      expect(await profileStore.removeScopedPreferences('profile'), isEmpty);
      present = current = false;
      expect(budgets.limit(data, UsageBudgetUnit.usd), isNull);
    },
  );

  test(
    'replacement screens serialize writes and merge separate units',
    () async {
      final replacement = make();
      addTearDown(replacement.dispose);
      platform.gate = Completer<void>();
      platform.started = Completer<void>();
      final first = budgets.save(sample(), UsageBudgetUnit.usd, 10);
      await platform.started!.future;
      final second = replacement.save(sample(), UsageBudgetUnit.tokens, 100);
      platform.gate!.complete();
      expect(await first, isTrue);
      expect(await second, isTrue);
      final restored = make();
      addTearDown(restored.dispose);
      expect(restored.limit(sample(), UsageBudgetUnit.usd), 10);
      expect(restored.limit(sample(), UsageBudgetUnit.tokens), 100);
    },
  );

  test('deletion during a pending write cannot resurrect the budget', () async {
    platform.gate = Completer<void>();
    platform.started = Completer<void>();
    final write = budgets.save(sample(), UsageBudgetUnit.usd, 10);
    await platform.started!.future;
    present = current = false;
    await ProfileStore(prefs: prefs).removeScopedPreferences('profile');
    platform.gate!.complete();
    expect(await write, isFalse);
    expect(prefs.containsKey(budgets.key), isFalse);
  });

  test(
    'explicit clearing recovers full capacity without silently evicting rules',
    () async {
      for (var i = 0; i < 64; i++) {
        expect(
          await budgets.save(sample(from: i), UsageBudgetUnit.usd, i + 1),
          isTrue,
        );
      }
      expect(
        await budgets.save(sample(from: 65), UsageBudgetUnit.usd, 99),
        isFalse,
      );
      expect(budgets.failed, isTrue);
      expect(budgets.limit(sample(from: 0), UsageBudgetUnit.usd), 1);
      expect(await budgets.clearAll(), isTrue);
      expect(prefs.containsKey(budgets.key), isFalse);
      expect(
        await budgets.save(sample(from: 65), UsageBudgetUnit.usd, 99),
        isTrue,
      );
    },
  );
}
