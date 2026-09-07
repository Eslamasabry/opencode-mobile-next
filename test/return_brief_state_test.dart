import 'dart:async';
import 'dart:convert';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/return_brief_state.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:shared_preferences_platform_interface/shared_preferences_platform_interface.dart';

import 'return_brief_test.dart' show brief, session;

class BriefTestPreferences extends InMemorySharedPreferencesStore {
  BriefTestPreferences() : super.withData({});
  bool refuse = false;
  Completer<void>? barrier;
  final entered = Completer<void>();
  @override
  Future<bool> setValue(String valueType, String key, Object value) async {
    if (key.contains('oc.returnBrief.')) {
      if (!entered.isCompleted) entered.complete();
      await barrier?.future;
      if (refuse) return false;
    }
    return super.setValue(valueType, key, value);
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUp(() {
    SharedPreferences.setMockInitialValues({});
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (_) async => null,
        );
  });

  test('restart restores exact identity by profile and location', () async {
    final prefs = await SharedPreferences.getInstance();
    final store = ReturnBriefStore(prefs);
    await store.acknowledge('p', 'dir-a', brief([session('same', 20)]));
    final restarted = ReturnBriefStore(prefs);
    expect(restarted.acknowledged('p', 'dir-a').coversRun('same', 20), isTrue);
    expect(restarted.acknowledged('q', 'dir-a').runs, isEmpty);
    expect(restarted.acknowledged('p', 'dir-b').runs, isEmpty);
    expect(
      restarted.acknowledged('p', 'dir-a').coversRun('later', 10),
      isFalse,
    );
  });

  test(
    'write refusal keeps the card unacknowledged across retry and restart',
    () async {
      SharedPreferences.resetStatic();
      final backend = BriefTestPreferences()..refuse = true;
      SharedPreferencesStorePlatform.instance = backend;
      final prefs = await SharedPreferences.getInstance();
      final store = ReturnBriefStore(prefs);
      final shown = brief([session('a', 20)]);
      await expectLater(store.acknowledge('p', 'loc', shown), throwsStateError);
      expect(store.acknowledged('p', 'loc').runs, isEmpty);
      expect(ReturnBriefStore(prefs).acknowledged('p', 'loc').runs, isEmpty);
      backend.refuse = false;
      await store.acknowledge('p', 'loc', shown);
      expect(ReturnBriefStore(prefs).acknowledged('p', 'loc').runs, {
        ('a', 20),
      });
    },
  );

  test(
    'queued writes merge snapshots while arrivals during save remain new',
    () async {
      SharedPreferences.resetStatic();
      final backend = BriefTestPreferences()..barrier = Completer<void>();
      SharedPreferencesStorePlatform.instance = backend;
      final prefs = await SharedPreferences.getInstance();
      final store = ReturnBriefStore(prefs);
      final first = store.acknowledge('p', 'loc', brief([session('a', 20)]));
      await backend.entered.future;
      final second = store.acknowledge('p', 'loc', brief([session('b', 20)]));
      expect(store.acknowledged('p', 'loc').runs, isEmpty);
      backend.barrier!.complete();
      await Future.wait([first, second]);
      final remaining = brief([
        session('a', 20),
        session('b', 20),
        session('new', 10),
      ], ack: store.acknowledged('p', 'loc'));
      expect(remaining.unreviewed.single.session.id, 'new');
    },
  );

  test('corrupt data and evicted locations start unacknowledged', () async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('oc.returnBrief.p', '{broken');
    final store = ReturnBriefStore(prefs);
    expect(store.acknowledged('p', 'loc').runs, isEmpty);
    for (var i = 0; i < 65; i++) {
      await store.acknowledge('p', 'dir$i', brief([session('a', 20)]));
    }
    expect(store.acknowledged('p', 'dir0').runs, isEmpty);
    expect(
      (jsonDecode(prefs.getString('oc.returnBrief.p')!) as Map).length,
      64,
    );
  });

  test(
    'profile deletion drains pending dismissal and clears persisted and in-memory ack',
    () async {
      SharedPreferences.resetStatic();
      final backend = BriefTestPreferences()..barrier = Completer<void>();
      SharedPreferencesStorePlatform.instance = backend;
      final prefs = await SharedPreferences.getInstance();
      final profiles = ProfileStore(prefs: prefs);
      await profiles.load();
      await profiles.upsert(
        ServerProfile(id: 'p', name: 'P', baseUrl: 'http://localhost'),
      );
      await profiles.setActiveId('p');
      final controller = ConnectionController(profiles);
      addTearDown(controller.dispose);
      final shown = brief([session('a', 20)]);
      final scope = controller.returnBriefScope;
      final writing = controller.dismissReturnBrief(
        shown,
        expectedScope: scope,
      );
      await backend.entered.future;
      final deletion = controller.deleteProfileAndLocalData('p');
      backend.barrier!.complete();
      await writing;
      await deletion;
      expect(prefs.containsKey('oc.returnBrief.p'), isFalse);
      expect(ReturnBriefStore(prefs).acknowledged('p', scope.$2).runs, isEmpty);
      expect(controller.returnBriefAcknowledgement.runs, isEmpty);
    },
  );
}
