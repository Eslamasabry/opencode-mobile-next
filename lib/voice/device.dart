import 'package:flutter/services.dart';

import '../platform/platform_capabilities.dart';

enum VoiceMicrophonePermission { granted, denied, permanentlyDenied }

class VoiceDeviceInfo {
  const VoiceDeviceInfo({
    required this.availableStorageBytes,
    required this.memoryClassMb,
    required this.supportedAbis,
    required this.hasMicrophone,
    this.captureSupported = true,
  });

  /// A device that answered nothing useful — an Android build with no
  /// `oc/voice` handler, or a probe that failed. Capture is still possible,
  /// so the model picker shows every pack rather than none.
  const VoiceDeviceInfo.unknown()
    : availableStorageBytes = null,
      memoryClassMb = null,
      supportedAbis = const [],
      hasMicrophone = true,
      captureSupported = true;

  /// A platform this app has no voice capture path for at all.
  ///
  /// Distinct from [VoiceDeviceInfo.unknown]: this is not "we could not
  /// measure the device", it is "there is nothing to measure". The old code
  /// returned `unknown` off Android, whose `hasMicrophone = true` and granted
  /// permission let a desktop user walk into voice setup, download a model,
  /// and record into Android-shaped paths.
  const VoiceDeviceInfo.unsupported()
    : availableStorageBytes = null,
      memoryClassMb = null,
      supportedAbis = const [],
      hasMicrophone = false,
      captureSupported = false;

  final int? availableStorageBytes;
  final int? memoryClassMb;
  final List<String> supportedAbis;
  final bool hasMicrophone;

  /// False when the running platform has no speech capture path.
  final bool captureSupported;
}

abstract interface class VoiceDevicePlatform {
  Future<VoiceDeviceInfo> getDeviceInfo();
  Future<VoiceMicrophonePermission> requestMicrophonePermission();
  Future<void> openAppSettings();
}

class AndroidVoiceDevicePlatform implements VoiceDevicePlatform {
  const AndroidVoiceDevicePlatform();

  static const _channel = MethodChannel('oc/voice');

  @override
  Future<VoiceDeviceInfo> getDeviceInfo() async {
    if (!platformCapabilities.supportsVoice) {
      return const VoiceDeviceInfo.unsupported();
    }
    try {
      final result = await _channel.invokeMapMethod<String, dynamic>(
        'getDeviceInfo',
      );
      return VoiceDeviceInfo(
        availableStorageBytes: (result?['availableStorageBytes'] as num?)
            ?.toInt(),
        memoryClassMb: (result?['memoryClassMb'] as num?)?.toInt(),
        supportedAbis:
            (result?['supportedAbis'] as List?)?.cast<String>() ?? const [],
        hasMicrophone: result?['hasMicrophone'] as bool? ?? true,
      );
    } on MissingPluginException {
      return const VoiceDeviceInfo.unknown();
    }
  }

  @override
  Future<VoiceMicrophonePermission> requestMicrophonePermission() async {
    // Nothing routes here off Android — the composer's voice tool and the
    // voice settings row are both gated — so this is the backstop, and it
    // says no rather than claiming a grant the platform never made.
    if (!platformCapabilities.supportsVoice) {
      return VoiceMicrophonePermission.permanentlyDenied;
    }
    try {
      final status = await _channel.invokeMethod<String>(
        'requestMicrophonePermission',
      );
      return switch (status) {
        'granted' => VoiceMicrophonePermission.granted,
        'permanentlyDenied' => VoiceMicrophonePermission.permanentlyDenied,
        _ => VoiceMicrophonePermission.denied,
      };
    } on MissingPluginException {
      // Android with no handler registered (unit tests, a detached engine).
      // The recorder plugin performs its own runtime permission check, so
      // defer to it rather than blocking here.
      return VoiceMicrophonePermission.granted;
    }
  }

  @override
  Future<void> openAppSettings() async {
    if (!platformCapabilities.supportsVoice) return;
    try {
      await _channel.invokeMethod<void>('openAppSettings');
    } on MissingPluginException {
      // The recovery action is Android-only and unavailable in unit tests.
    }
  }
}

const voiceDevicePlatform = AndroidVoiceDevicePlatform();
