import 'package:flutter/foundation.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  tearDown(() => debugPlatformCapabilities = null);

  test('Android has every platform feature this app ships', () {
    const caps = PlatformCapabilities.android();
    expect(caps.isAndroid, isTrue);
    expect(caps.isDesktop, isFalse);
    expect(caps.supportsTermux, isTrue);
    expect(caps.supportsVoice, isTrue);
    expect(caps.supportsBackgroundService, isTrue);
    expect(caps.supportsNotifications, isTrue);
    expect(caps.supportsHomeWidget, isTrue);
    expect(caps.supportsCodePush, isTrue);
    expect(caps.supportsUsbHostBridge, isTrue);
    expect(caps.supportsQrPairing, isTrue);
    // Shorebird covers Android, so the GitHub fallback stays off.
    expect(caps.supportsDesktopReleaseCheck, isFalse);
  });

  test('Linux desktop has none of the Android-only features', () {
    const caps = PlatformCapabilities.linuxDesktop();
    expect(caps.isAndroid, isFalse);
    expect(caps.isDesktop, isTrue);
    expect(caps.supportsTermux, isFalse);
    expect(caps.supportsVoice, isFalse);
    expect(caps.supportsBackgroundService, isFalse);
    expect(caps.supportsNotifications, isFalse);
    expect(caps.supportsHomeWidget, isFalse);
    expect(caps.supportsCodePush, isFalse);
    expect(caps.supportsUsbHostBridge, isFalse);
    // mobile_scanner has no Linux implementation, and a desktop user would be
    // pointing a webcam at the screen that printed the code. Paste is better.
    expect(caps.supportsQrPairing, isFalse);
    // Something has to tell a desktop user a new build exists.
    expect(caps.supportsDesktopReleaseCheck, isTrue);
  });

  test('every capability is claimed by exactly one update channel', () {
    for (final caps in const [
      PlatformCapabilities.android(),
      PlatformCapabilities.linuxDesktop(),
      PlatformCapabilities(platform: TargetPlatform.windows),
    ]) {
      expect(
        caps.supportsCodePush && caps.supportsDesktopReleaseCheck,
        isFalse,
        reason: 'two update notices would fight on $caps',
      );
    }
  });

  test('macOS is desktop but has no release artifact to check', () {
    const caps = PlatformCapabilities(platform: TargetPlatform.macOS);
    expect(caps.isDesktop, isTrue);
    // There is no macos/ runner in this repo; nothing publishes a build.
    expect(caps.supportsDesktopReleaseCheck, isFalse);
    expect(caps.supportsCodePush, isFalse);
  });

  test('web is neither Android nor desktop', () {
    const caps = PlatformCapabilities(
      platform: TargetPlatform.android,
      isWeb: true,
    );
    expect(caps.isAndroid, isFalse);
    expect(caps.isDesktop, isFalse);
    expect(caps.supportsTermux, isFalse);
    expect(caps.supportsDesktopReleaseCheck, isFalse);
  });

  test('the ambient value defaults to Android under flutter_test', () {
    // The suite must exercise the Android paths by default, so a gate added
    // without a desktop test still runs the branch users actually get.
    expect(platformCapabilities.isAndroid, isTrue);
  });

  test('the ambient value is overridable and restorable', () {
    debugPlatformCapabilities = const PlatformCapabilities.linuxDesktop();
    expect(platformCapabilities.isDesktop, isTrue);
    expect(platformCapabilities.supportsTermux, isFalse);
    debugPlatformCapabilities = null;
    expect(platformCapabilities.isAndroid, isTrue);
  });
}
