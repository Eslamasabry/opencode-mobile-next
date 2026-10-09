// Preference gate for settings: every stored key is accounted for, every
// setting a control writes reads back after a restart, and the per-server ones
// go with the server. Ledger: test/fixtures/coverage/settings_preferences_ledger.json
import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/state/automation_policy.dart';
import 'package:opencode_mobile/state/notification_preferences.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/reader_preferences.dart';
import 'package:opencode_mobile/ui/kit/kit_effects.dart';
import 'package:shared_preferences/shared_preferences.dart';

Map<String, dynamic> _ledger() =>
    jsonDecode(
          File(
            'test/fixtures/coverage/settings_preferences_ledger.json',
          ).readAsStringSync(),
        )
        as Map<String, dynamic>;

Set<String> _sourceKeys() {
  final found = <String>{};
  final pattern = RegExp('[\'"](oc\\.[A-Za-z0-9_.]*)');
  for (final entity in Directory('lib').listSync(recursive: true)) {
    if (entity is! File || !entity.path.endsWith('.dart')) continue;
    if (entity.path.contains('/l10n/')) continue;
    for (final m in pattern.allMatches(entity.readAsStringSync())) {
      found.add(m.group(1)!);
    }
  }
  return found..remove('oc.');
}

Future<SharedPreferences> _fresh(Map<String, Object> seed) async {
  SharedPreferences.setMockInitialValues(seed);
  return SharedPreferences.getInstance();
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('every stored key in the app is classified, none is stale', () {
    final ledger = _ledger();
    final source = _sourceKeys();
    expect(
      source.difference(ledger.keys.toSet()),
      isEmpty,
      reason: 'a new preference key needs a scope in the ledger',
    );
    expect(
      ledger.keys.toSet().difference(source),
      isEmpty,
      reason: 'a ledger key that no code writes any more',
    );
    for (final e in ledger.entries) {
      final v = e.value as Map<String, dynamic>;
      expect(v['scope'], isIn(['global', 'profile', 'shared', 'other']));
      expect((v['why'] as String).isNotEmpty, isTrue, reason: e.key);
      if (v['scope'] == 'global') {
        expect(e.key.endsWith('.'), isFalse, reason: '${e.key} has no id');
      }
    }
  });

  test('the sweep takes every per-server key shape of the ledger', () async {
    final ledger = _ledger();
    final seed = <String, Object>{};
    for (final e in ledger.entries) {
      if ((e.value as Map)['scope'] != 'profile') continue;
      seed[e.key.endsWith('.') ? '${e.key}doomed' : e.key] = 'x';
      seed[e.key.endsWith('.') ? '${e.key}keeper' : '${e.key}.keeper'] = 'x';
    }
    final prefs = await _fresh({...seed, 'oc.appearance': 'light'});
    final store = ProfileStore(prefs: prefs);
    final swept = store.profileScopedPreferenceKeys('doomed');
    expect(swept, isNotEmpty);
    final missed = seed.keys.where(
      (k) => k.contains('doomed') && !swept.contains(k),
    );
    expect(missed, isEmpty);
    expect(swept.any((k) => k.contains('keeper')), isFalse);
    expect(swept, isNot(contains('oc.appearance')));
  });

  group('every setting reads back after a restart', () {
    test('app-wide display settings', () async {
      final prefs = await _fresh({});
      final store = ProfileStore(prefs: prefs);
      await store.setTranscriptTimestampsVisible(true);
      await store.setTranscriptReasoningExpanded(true);
      await store.setAppearance(AppAppearance.light);
      await store.setThemePack(ThemePackId.values.last);
      await store.setEffects(
        KitEffects.defaults.copyWith(motion: KitMotionLevel.calm),
      );
      await prefs.reload();
      final again = ProfileStore(prefs: prefs);
      expect(again.transcriptTimestampsVisible, isTrue);
      expect(again.transcriptReasoningExpanded, isTrue);
      expect(again.appearance, AppAppearance.light);
      expect(again.themePack, ThemePackId.values.last);
      expect(again.effects.motion, KitMotionLevel.calm);
    });

    test('the shared notification choices', () async {
      final prefs = await _fresh({});
      final n = NotificationPreferences(prefs);
      await n.setRequests(false);
      await n.setFinishedRuns(false);
      await prefs.reload();
      final again = NotificationPreferences(prefs);
      expect(again.requests, isFalse);
      expect(again.finishedRuns, isFalse);
    });

    test('per-server choices read back and leave with the server', () async {
      final prefs = await _fresh({'oc.appearance': 'light'});
      final store = ProfileStore(prefs: prefs);
      for (final id in ['doomed', 'keeper']) {
        final automation = AutomationPolicyController(
          profileId: id,
          preferences: prefs,
        );
        await automation.setSupervision(AutomationSupervision.autonomous);
        await automation.setAutoApprove(true);
        await ReaderPreferencesStore(
          prefs: prefs,
          profileId: id,
        ).update(sourceFirst: true, wrapCode: true);
        await store.setAgent(id, 'plan');
        automation.dispose();
      }
      await prefs.reload();
      final back = AutomationPolicyController(
        profileId: 'doomed',
        preferences: prefs,
      );
      expect(back.value.supervision, AutomationSupervision.autonomous);
      expect(back.value.autoApprove, isTrue);
      back.dispose();
      final reader = ReaderPreferencesStore(prefs: prefs, profileId: 'doomed');
      expect(reader.value.sourceFirst, isTrue);
      expect(reader.value.wrapCode, isTrue);
      expect(store.agentFor('doomed'), 'plan');

      final before = store.profileScopedPreferenceKeys('doomed');
      expect(before, containsAll(['oc.automation.doomed', 'oc.agent.doomed']));
      expect(before, contains('oc.readerPreferences.doomed'));
      expect(await store.removeScopedPreferences('doomed'), isEmpty);
      expect(store.profileScopedPreferenceKeys('doomed'), isEmpty);
      expect(prefs.getString('oc.automation.keeper'), isNotNull);
      expect(prefs.getString('oc.readerPreferences.keeper'), isNotNull);
      expect(prefs.getString('oc.appearance'), 'light');
    });
  });
}
