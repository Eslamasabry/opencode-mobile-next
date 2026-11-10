import 'dart:math' as math;

import 'package:flutter/widgets.dart';

import '../l10n/app_localizations.dart';
import 'audio.dart';
import 'device.dart';
import 'model_download.dart';
import 'model_manager.dart';
import 'model_manifest.dart';

AppLocalizations voiceStrings(BuildContext context) =>
    Localizations.of<AppLocalizations>(context, AppLocalizations) ??
    lookupAppLocalizations(const Locale('en'));

/// A download or storage size in plain words: "160 MB" or "1.2 GB", in
/// decimal units like the store listing and the phone setup sheet. The exact
/// size in MiB ([formatModelBytes]) belongs under Details only.
String voiceSizeText(AppLocalizations strings, int bytes) {
  final megabytes = bytes / 1000000;
  if (megabytes >= 1000) {
    return strings.phoneSetupStartGigabytes(
      (megabytes / 1000).toStringAsFixed(1),
    );
  }
  // Only nothing is "0 MB"; a few bytes still round up to one.
  return strings.phoneSetupStartMegabytes(
    (bytes <= 0 ? 0 : math.max(1, megabytes.round())).toString(),
  );
}

String voiceLanguageLabel(VoiceLanguage language, AppLocalizations strings) =>
    switch (language) {
      VoiceLanguage.auto => strings.e7VoiceUiAuto,
      VoiceLanguage.english => strings.e7VoiceUiEnglish,
      VoiceLanguage.arabic => strings.e7VoiceUiArabic,
    };

/// Labels in our bundled manifest are authored UI, not provider/model names.
/// Unknown caller-supplied packs retain their own labels and descriptions.
String voicePackLabel(VoiceModelPack pack, AppLocalizations strings) =>
    switch (pack.id) {
      'base' => strings.e7VoiceUiBalanced,
      'small' => strings.e7VoiceUiAccurate,
      'tiny' => strings.e7VoiceUiCompact,
      _ => pack.label,
    };

String voicePackDescription(VoiceModelPack pack, AppLocalizations strings) =>
    switch (pack.id) {
      'base' => strings.e7VoiceUiBalancedDetail,
      'small' => strings.e7VoiceUiAccurateDetail,
      'tiny' => strings.e7VoiceUiCompactDetail,
      _ => pack.description,
    };

String? voiceSupportReason(
  VoiceModelManager manager,
  VoiceModelPack pack,
  VoicePackSupport support,
  AppLocalizations strings,
) => switch (support.kind) {
  VoicePackUnsupported.abi => strings.e7VoiceUiUnsupportedAbi,
  VoicePackUnsupported.memory => strings.e7VoiceUiMemory(
    voicePackLabel(pack, strings),
    pack.minimumMemoryMb,
    manager.deviceInfo.totalMemoryMb!,
  ),
  VoicePackUnsupported.storage => strings.e7VoiceUiStorage(
    voicePackLabel(pack, strings),
    voiceSizeText(strings, manager.requiredStorageBytes(pack)),
  ),
  null => support.reason,
};

/// User-facing explanation; native/file/network diagnostics remain available
/// separately as technical details. No lifecycle or download decision uses copy.
String voiceErrorText(
  Object? error,
  AppLocalizations strings, {
  VoiceModelManager? manager,
}) {
  if (error is VoicePermissionDenied) {
    return error.permanent
        ? strings.e7VoiceUiPermissionBlocked
        : strings.e7VoiceUiPermissionRequired;
  }
  if (error is VoiceDeviceUnavailable) {
    return strings.e7VoiceUiDeviceUnavailable;
  }
  if (error is VoiceModelPreflightException) {
    final pack = error.pack;
    return switch (error.support?.kind) {
      VoicePackUnsupported.abi => strings.e7VoiceUiUnsupportedAbi,
      VoicePackUnsupported.memory when pack != null && error.memoryMb != null =>
        strings.e7VoiceUiMemory(
          voicePackLabel(pack, strings),
          pack.minimumMemoryMb,
          error.memoryMb!,
        ),
      VoicePackUnsupported.storage
          when pack != null && error.requiredBytes != null =>
        strings.e7VoiceUiStorage(
          voicePackLabel(pack, strings),
          voiceSizeText(strings, error.requiredBytes!),
        ),
      _ => strings.e7VoiceUiInputFailed,
    };
  }
  if (error is VoiceDownloadException) {
    return switch (error.failure) {
      VoiceDownloadFailure.https => strings.e7VoiceUiHttpsRequired,
      VoiceDownloadFailure.closed => strings.e7VoiceUiTransportClosed,
      VoiceDownloadFailure.redirect => strings.e7VoiceUiInvalidRedirect,
      VoiceDownloadFailure.unsafeRedirect => strings.e7VoiceUiUnsafeRedirect,
      VoiceDownloadFailure.noResponse => strings.e7VoiceUiNoResponse,
      VoiceDownloadFailure.timeout => strings.e7VoiceUiDownloadTimeout,
      VoiceDownloadFailure.checksum => strings.e7VoiceUiChecksumFailed,
      VoiceDownloadFailure.verification => strings.e7VoiceUiVerificationFailed,
      VoiceDownloadFailure.http => strings.e7VoiceUiHttpFailed,
      VoiceDownloadFailure.length => strings.e7VoiceUiLengthFailed,
      VoiceDownloadFailure.incomplete => strings.e7VoiceUiIncomplete,
      VoiceDownloadFailure.unknown => strings.e7VoiceUiDownloadFailed,
    };
  }
  // These legacy StateError values originate in our recorder/controller. Their
  // original diagnostics remain unchanged for callers and cancellation tests.
  if (error is StateError) {
    final message = error.message;
    if (message == 'No audio was captured.') return strings.e7VoiceUiNoAudio;
    if (message == 'Local voice input is unavailable on this platform.') {
      return strings.e7VoiceUiInputUnavailable;
    }
    if (message == 'Recording was interrupted.' ||
        message == 'Voice input was interrupted.') {
      return strings.e7VoiceUiInterrupted;
    }
    if (message.startsWith('Microphone state error:') ||
        message.startsWith('Microphone error:')) {
      return strings.e7VoiceUiMicrophoneError;
    }
  }
  return strings.e7VoiceUiInputFailed;
}
