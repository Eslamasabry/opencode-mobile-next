import 'dart:async';
import 'dart:convert';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/shared_project_roots.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test(
    'opened folders persist per profile, push the union, and are swept',
    () async {
      SharedPreferences.setMockInitialValues({});
      final pushed = <List<String>>[];
      SharedProjectRoots.pushOverride = (roots) async => pushed.add(roots);
      addTearDown(() => SharedProjectRoots.pushOverride = null);

      await SharedProjectRoots.remember('a', '/sdcard/CodeAnything');
      await SharedProjectRoots.remember('b', '/sdcard/Other');
      await SharedProjectRoots.remember('a', '/root/projects/app'); // ignored
      expect(pushed.last, [
        '/storage/emulated/0/CodeAnything',
        '/storage/emulated/0/Other',
      ]);

      final prefs = await SharedPreferences.getInstance();
      final keys = ProfileStore(prefs: prefs).profileScopedPreferenceKeys('a');
      expect(keys, contains('oc.sharedProjects.a'));
      await prefs.remove('oc.sharedProjects.a');
      await SharedProjectRoots.push(prefs);
      expect(pushed.last, ['/storage/emulated/0/Other']);
    },
  );

  // A server removal must never wait on the native side: a reply that never
  // comes (no handler, as in a widget test) once left the removal hanging.
  testWidgets('removing a server unbinds its folders without waiting on '
      'the native side, and a server without folders sends nothing', (
    tester,
  ) async {
    SharedPreferences.setMockInitialValues({
      'oc.profiles': jsonEncode([
        for (final id in ['a', 'b', 'c'])
          {'id': id, 'name': id, 'baseUrl': 'http://192.168.1.2:4096'},
      ]),
      'oc.activeProfile': 'b',
      'oc.sharedProjects.a': ['/storage/emulated/0/CodeAnything'],
      'oc.sharedProjects.b': ['/storage/emulated/0/Other'],
    });
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    for (final channel in const [
      MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
      MethodChannel('oc/background'),
    ]) {
      messenger.setMockMethodCallHandler(channel, (_) async => null);
      addTearDown(() => messenger.setMockMethodCallHandler(channel, null));
    }
    final pushed = <List<String>>[];
    final never = Completer<void>();
    SharedProjectRoots.pushOverride = (roots) {
      pushed.add(roots);
      return never.future;
    };
    addTearDown(() => SharedProjectRoots.pushOverride = null);
    final store = ProfileStore(prefs: await SharedPreferences.getInstance());
    await store.load();
    final controller = ConnectionController(store);
    addTearDown(controller.dispose);

    final removed = await controller.deleteProfileAndLocalData('c');
    expect(removed.failures, isEmpty);
    expect(pushed, isEmpty);

    final result = await controller.deleteProfileAndLocalData('a');
    expect(result.failures, isEmpty);
    expect(store.profiles.map((p) => p.id), ['b']);
    expect(pushed, [
      ['/storage/emulated/0/Other'],
    ]);
  }, timeout: const Timeout(Duration(seconds: 30)));
}
