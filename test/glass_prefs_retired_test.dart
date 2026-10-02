// Liquid glass is gone: the strike counters its crash guard kept on the
// device go on the next load, and nothing else is touched.
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const secure = MethodChannel('plugins.it_nomads.com/flutter_secure_storage');
  setUp(
    () => TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(secure, (_) async => null),
  );
  tearDown(
    () => TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(secure, null),
  );

  test('load drops the retired glass guard keys and nothing else', () async {
    SharedPreferences.setMockInitialValues({
      'oc.glassLiquidActive': true,
      'oc.glassLiquidStrikes': 2,
      'oc.glassLiquidOffUntil': 123456,
      'oc.effectsGlass': false,
      'oc.activeProfile': 'laptop',
    });
    final prefs = await SharedPreferences.getInstance();
    await ProfileStore(prefs: prefs).load();

    for (final key in ProfileStore.retiredGlassPreferenceKeys) {
      expect(prefs.containsKey(key), isFalse, reason: key);
    }
    expect(prefs.getString('oc.activeProfile'), 'laptop');
    // The old switch is read by nothing and harmless to leave.
    expect(prefs.getBool('oc.effectsGlass'), isFalse);
  });
}
