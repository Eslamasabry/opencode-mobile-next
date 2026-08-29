import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/voice/device.dart';

void _installChannel(Future<Object?> Function(MethodCall call)? handler) {
  TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
      .setMockMethodCallHandler(const MethodChannel('oc/voice'), handler);
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  tearDown(() {
    debugPlatformCapabilities = null;
    _installChannel(null);
  });

  group('on a platform with no voice capture', () {
    setUp(() {
      debugPlatformCapabilities = const PlatformCapabilities.linuxDesktop();
      _installChannel(null);
    });

    test('the device probe reports capture unsupported, not a microphone', () async {
      final info = await voiceDevicePlatform.getDeviceInfo();
      expect(info.captureSupported, isFalse);
      // The old code returned VoiceDeviceInfo.unknown() here, whose
      // hasMicrophone is true — a desktop user was told the device had a
      // microphone this app could record from.
      expect(info.hasMicrophone, isFalse);
      expect(info.supportedAbis, isEmpty);
      expect(info.availableStorageBytes, isNull);
    });

    test('microphone permission is refused, never silently granted', () async {
      expect(
        await voiceDevicePlatform.requestMicrophonePermission(),
        VoiceMicrophonePermission.permanentlyDenied,
      );
    });

    test('opening app settings is a no-op rather than a throw', () async {
      await expectLater(voiceDevicePlatform.openAppSettings(), completes);
    });
  });

  group('on Android', () {
    test('the device probe uses the channel', () async {
      _installChannel((call) async {
        expect(call.method, 'getDeviceInfo');
        return <String, dynamic>{
          'availableStorageBytes': 8000000000,
          'memoryClassMb': 256,
          'supportedAbis': <String>['arm64-v8a'],
          'hasMicrophone': true,
        };
      });
      final info = await voiceDevicePlatform.getDeviceInfo();
      expect(info.captureSupported, isTrue);
      expect(info.hasMicrophone, isTrue);
      expect(info.memoryClassMb, 256);
      expect(info.supportedAbis, ['arm64-v8a']);
    });

    test('a granted permission still comes back granted', () async {
      _installChannel((call) async => 'granted');
      expect(
        await voiceDevicePlatform.requestMicrophonePermission(),
        VoiceMicrophonePermission.granted,
      );
    });

    test('a missing handler leaves the probe merely unknown', () async {
      // Unit tests and a detached engine both land here. Capture is still
      // possible on the device, so the model picker must not be emptied.
      _installChannel(null);
      final info = await voiceDevicePlatform.getDeviceInfo();
      expect(info.captureSupported, isTrue);
      expect(info.hasMicrophone, isTrue);
    });
  });
}
