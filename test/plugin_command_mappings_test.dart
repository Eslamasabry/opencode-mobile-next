import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/state/plugin_command_mappings.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:shared_preferences_platform_interface/shared_preferences_platform_interface.dart';

class _Store extends InMemorySharedPreferencesStore {
  _Store() : super.withData({});
  bool refuse = false;
  bool refuseRemove = false;
  @override
  Future<bool> remove(String key) async =>
      refuseRemove ? false : super.remove(key);
  Completer<void>? gate;
  final started = Completer<void>();
  @override
  Future<bool> setValue(String type, String key, Object value) async {
    if (!started.isCompleted) started.complete();
    await gate?.future;
    return refuse ? false : super.setValue(type, key, value);
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late SharedPreferences prefs;
  late _Store platform;
  var readable = true;
  final scope = PluginCommandMappings.scope(
    baseUrl: 'https://example.test',
    directory: '/project',
  );
  PluginCommandMappings make() =>
      PluginCommandMappings(prefs, 'p', () => readable);
  setUp(() async {
    final previous = SharedPreferencesStorePlatform.instance;
    addTearDown(() => SharedPreferencesStorePlatform.instance = previous);
    SharedPreferences.setMockInitialValues({});
    platform = _Store();
    SharedPreferencesStorePlatform.instance = platform;
    prefs = await SharedPreferences.getInstance();
    readable = true;
  });
  test('save restart restore and profile deletion sweep', () async {
    await make().set(scope, 'plugin-a', ['review', 'test']);
    await prefs.reload();
    expect(make().load(scope), {
      'plugin-a': ['review', 'test'],
    });
    expect(
      ProfileStore(prefs: prefs).profileScopedPreferenceKeys('p'),
      contains(make().key),
    );
    for (final key in ProfileStore(
      prefs: prefs,
    ).profileScopedPreferenceKeys('p')) {
      await prefs.remove(key);
    }
    expect(make().load(scope), isEmpty);
  });
  test(
    'clear reclaims full history across locations and survives restart',
    () async {
      for (var i = 0; i < PluginCommandMappings.maxMappings; i++) {
        await make().set(
          PluginCommandMappings.scope(
            baseUrl: 'https://example.test',
            directory: '/previous/$i',
          ),
          'plugin',
          ['review'],
        );
      }
      await expectLater(
        make().set(scope, 'current', ['test']),
        throwsStateError,
      );
      final otherProfile = PluginCommandMappings(prefs, 'other', () => true);
      await otherProfile.set(scope, 'other', ['keep']);
      await make().clear();
      await prefs.reload();
      expect(prefs.containsKey(make().key), isFalse);
      expect(make().load(scope), isEmpty);
      expect(otherProfile.load(scope), {
        'other': ['keep'],
      });
      await make().set(scope, 'current', ['test']);
      await prefs.reload();
      expect(make().load(scope), {
        'current': ['test'],
      });
    },
  );
  test('clear waits for previous writes and reports refused removal', () async {
    platform.gate = Completer<void>();
    final pending = make().set(scope, 'old', ['review']);
    await platform.started.future;
    final cleared = make().clear();
    platform.gate!.complete();
    await pending;
    await cleared;
    expect(prefs.containsKey(make().key), isFalse);
    await make().set(scope, 'retained', ['review']);
    platform.refuseRemove = true;
    await expectLater(make().clear(), throwsStateError);
    expect(make().load(scope), {
      'retained': ['review'],
    });
  });
  test('endpoint account and location changes never inherit links', () async {
    await make().set(scope, 'plugin-a', ['review']);
    for (final other in [
      PluginCommandMappings.scope(
        baseUrl: 'https://other.test',
        directory: '/project',
      ),
      PluginCommandMappings.scope(
        baseUrl: 'https://example.test',
        directory: '/other',
      ),
      PluginCommandMappings.scope(
        baseUrl: 'https://example.test',
        directory: '/project',
        username: 'other',
      ),
      PluginCommandMappings.scope(
        baseUrl: 'https://example.test',
        directory: '/project',
        workspace: 'other',
      ),
    ]) {
      expect(make().load(other), isEmpty);
    }
    expect(
      PluginCommandMappings(prefs, 'other', () => true).load(scope),
      isEmpty,
    );
    expect(prefs.getString(make().key), isNot(contains('/project')));
  });
  test('overlapping stores preserve unrelated mappings', () async {
    await Future.wait([
      make().set(scope, 'one', ['review']),
      make().set(scope, 'two', ['test']),
    ]);
    expect(make().load(scope), {
      'one': ['review'],
      'two': ['test'],
    });
    await make().set(scope, 'one', []);
    expect(make().load(scope), {
      'two': ['test'],
    });
  });
  test('failed platform save is not represented as durable', () async {
    await make().set(scope, 'one', ['review']);
    platform.refuse = true;
    await expectLater(make().set(scope, 'one', ['test']), throwsStateError);
    expect(make().load(scope), {
      'one': ['review'],
    });
  });
  test(
    'deletion during dispatched write cannot resurrect profile data',
    () async {
      platform.gate = Completer<void>();
      final pending = make().set(scope, 'one', ['review']);
      final expectation = expectLater(pending, throwsStateError);
      await platform.started.future;
      readable = false;
      await prefs.remove(make().key);
      platform.gate!.complete();
      await expectation;
      expect(prefs.containsKey(make().key), isFalse);
    },
  );
  test(
    'untrusted oversized or unsupported stored schemas fall back empty',
    () async {
      await prefs.setString(make().key, '{"version":2,"mappings":[]}');
      expect(make().load(scope), isEmpty);
      await expectLater(
        make().set(scope, 'one', List.generate(17, (i) => '$i')),
        throwsFormatException,
      );
      await expectLater(
        make().set(scope, 'one', ['bad\ncommand']),
        throwsFormatException,
      );
    },
  );
}
