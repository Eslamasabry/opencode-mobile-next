import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/termux/termux_reach.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const channel = MethodChannel('oc/termux');
  setUp(() {
    debugPlatformCapabilities = const PlatformCapabilities.android();
    TermuxAccess.blocked = false;
  });
  tearDown(() {
    debugPlatformCapabilities = null;
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, null);
  });

  test(
    'a refused legacy permission request returns denied without throwing',
    () async {
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, (call) async {
            if (call.method == 'requestRunCommandAccess') {
              throw MissingPluginException();
            }
            throw PlatformException(code: 'permission_in_progress');
          });
      expect(await TermuxAccess.request(), TermuxAccessAnswer.denied);
    },
  );
}
