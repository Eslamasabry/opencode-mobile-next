import 'dart:io';

import 'package:flutter/services.dart';

enum VoiceMicrophonePermission { granted, denied, permanentlyDenied }

class VoiceDeviceInfo {
  const VoiceDeviceInfo({
    required this.availableStorageBytes,
    required this.memoryClassMb,
    required this.supportedAbis,
    required this.hasMicrophone,
  });

  const VoiceDeviceInfo.unknown()
    : availableStorageBytes = null,
      memoryClassMb = null,
      supportedAbis = const [],
      hasMicrophone = true;

  final int? availableStorageBytes;
  final int? memoryClassMb;
  final List<String> supportedAbis;
  final bool hasMicrophone;
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
    if (!Platform.isAndroid) return const VoiceDeviceInfo.unknown();
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
    if (!Platform.isAndroid) return VoiceMicrophonePermission.granted;
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
      return VoiceMicrophonePermission.granted;
    }
  }

  @override
  Future<void> openAppSettings() async {
    if (!Platform.isAndroid) return;
    try {
      await _channel.invokeMethod<void>('openAppSettings');
    } on MissingPluginException {
      // The recovery action is Android-only and unavailable in unit tests.
    }
  }
}

const voiceDevicePlatform = AndroidVoiceDevicePlatform();
