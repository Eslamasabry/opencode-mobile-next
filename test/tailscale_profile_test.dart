import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/connection.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  test(
    'Tailscale guidance and HTTPS profile survive reload and profile deletion removes guidance',
    () async {
      final messenger =
          TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
      const channel = MethodChannel(
        'plugins.it_nomads.com/flutter_secure_storage',
      );
      messenger.setMockMethodCallHandler(
        channel,
        (call) async => call.method == 'readAll' ? <String, String>{} : null,
      );
      addTearDown(() => messenger.setMockMethodCallHandler(channel, null));
      SharedPreferences.setMockInitialValues({});
      final prefs = await SharedPreferences.getInstance();
      var store = ProfileStore(prefs: prefs);
      await store.load();
      await store.upsert(
        ServerProfile(
          id: 'tail',
          name: 'Private server',
          baseUrl: 'https://work.example.ts.net',
          username: '',
          password: '',
        ),
      );
      await prefs.setBool('oc.tailscale.tail', true);
      store = ProfileStore(prefs: prefs);
      await store.load();
      expect(store.profiles.single.baseUrl, 'https://work.example.ts.net');
      expect(prefs.getBool('oc.tailscale.tail'), isTrue);
      expect(
        store.profileScopedPreferenceKeys('tail'),
        contains('oc.tailscale.tail'),
      );
      final controller = ConnectionController(store);
      addTearDown(controller.dispose);
      await controller.deleteProfileAndLocalData('tail');
      expect(store.profiles, isEmpty);
      expect(prefs.getBool('oc.tailscale.tail'), isNull);
    },
  );
}
