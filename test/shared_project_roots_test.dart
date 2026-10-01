import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
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
}
