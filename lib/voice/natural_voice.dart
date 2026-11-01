import 'dart:async';
import 'dart:io';
import 'dart:math' as math;

import 'package:flutter/foundation.dart';
import 'package:path_provider/path_provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'device.dart';
import 'model_download.dart';
import 'model_manifest.dart';

/// One downloadable reading voice: the same pinned-file pack the speech
/// models use (url, length and sha256 per file, verified before use), plus
/// the languages it can speak.
///
/// File order is fixed: model, voices, tokens, lexicon.
class NaturalVoicePack extends VoiceModelPack {
  const NaturalVoicePack({
    required super.id,
    required super.label,
    required super.description,
    required super.repository,
    required super.revision,
    required super.files,
    required super.minimumMemoryMb,
    required this.languages,
    required this.sampleRate,
    required this.speakerId,
  });

  /// ISO 639-1 codes this voice speaks.
  final Set<String> languages;

  /// The model's output rate in Hz (checked again when it speaks).
  final int sampleRate;

  /// The voice inside the model's voice file.
  final int speakerId;

  VoiceModelFile get model => files[0];
  VoiceModelFile get voices => files[1];
  VoiceModelFile get modelTokens => files[2];
  VoiceModelFile get lexicon => files[3];
}

/// The natural reading voices. English is Kokoro v1.0 (int8, Apache-2.0),
/// voice `af_heart` (speaker 3 in sherpa-onnx's list), from
/// csukuangfj/kokoro-int8-multi-lang-v1_0, which sherpa-onnx publishes with
/// its tts-models release.
///
/// Arabic is not offered: sherpa-onnx 2.0 removed espeak-ng, and every
/// published Arabic Kokoro-runtime model (Nabra-82M, the Nabra 7M distill)
/// needs Arabic grapheme-to-phoneme from espeak plus diacritization, which
/// no bundled lexicon replaces. Arabic replies are read by the phone's voice.
/// A pack is added here when sherpa-onnx publishes a lexicon-based Arabic
/// model.
const naturalVoicePacks = <NaturalVoicePack>[
  NaturalVoicePack(
    id: 'kokoro-en',
    label: 'Natural voice (English)',
    description: 'Kokoro, a natural English reading voice.',
    repository: 'csukuangfj/kokoro-int8-multi-lang-v1_0',
    revision: '57adb37fc0e8d4bd430cfc1b8f8c2294945b9af2',
    minimumMemoryMb: 2000,
    languages: {'en'},
    sampleRate: 24000,
    speakerId: 3,
    files: [
      VoiceModelFile(
        name: 'model.int8.onnx',
        length: 114202993,
        sha256:
            '75635ea3b85a32d00094dcdd61a258aba87c160f7c264c304a7b3ae9d734195e',
      ),
      VoiceModelFile(
        name: 'voices.bin',
        length: 28200960,
        sha256:
            '1c5a5b983d3d50d8586d437a51f3faa2da7919ce76a013c081e65671a3447c29',
      ),
      VoiceModelFile(
        name: 'tokens.txt',
        length: 687,
        sha256:
            '6ebb6bb288f20f3ae8d004d3c2ca27697da27c037d75e81a60e2a6a663f95425',
      ),
      VoiceModelFile(
        name: 'lexicon-us-en.txt',
        length: 5956885,
        sha256:
            '7daaab53a181be9885b853a8582bf1838186317e5dadacbcef9c426d6fa0da14',
      ),
    ],
  ),
];

/// Everything the synthesis worker needs: plain strings and numbers, so it
/// crosses to the worker isolate.
@immutable
class NaturalSpeech {
  const NaturalSpeech({
    required this.packID,
    required this.modelPath,
    required this.voicesPath,
    required this.tokensPath,
    required this.lexiconPath,
    required this.speakerId,
    required this.sampleRate,
    required this.numThreads,
  });

  final String packID;
  final String modelPath;
  final String voicesPath;
  final String tokensPath;
  final String lexiconPath;
  final int speakerId;
  final int sampleRate;
  final int numThreads;
}

/// The language a reply is written in, as a lowercase language code, or
/// `other` when its script has no code here. Arabic script is `ar`, other
/// non-Latin scripts are `other`; Latin script follows [appLanguageCode]
/// when that language is written in Latin letters, else it is `en`.
String detectReadLanguage(String text, String? appLanguageCode) {
  var arabic = 0;
  var latin = 0;
  var other = 0;
  for (final rune in text.runes) {
    if ((rune >= 0x0600 && rune <= 0x06FF) ||
        (rune >= 0x0750 && rune <= 0x077F) ||
        (rune >= 0xFB50 && rune <= 0xFDFF) ||
        (rune >= 0xFE70 && rune <= 0xFEFF)) {
      arabic++;
    } else if ((rune >= 0x41 && rune <= 0x5A) ||
        (rune >= 0x61 && rune <= 0x7A) ||
        (rune >= 0xC0 && rune <= 0x24F)) {
      latin++;
    } else if ((rune >= 0x0370 && rune <= 0x05FF) ||
        (rune >= 0x0700 && rune <= 0x074F) ||
        (rune >= 0x0780 && rune <= 0x1FFF) ||
        (rune >= 0x3040 && rune <= 0xD7FF) ||
        (rune >= 0xFF00 && rune <= 0xFFEF)) {
      other++;
    }
  }
  if (arabic == 0 && latin == 0 && other == 0) {
    return _languageOf(appLanguageCode);
  }
  if (arabic >= latin && arabic >= other) return 'ar';
  if (other > latin) return 'other';
  final app = _languageOf(appLanguageCode);
  const nonLatinApp = {
    'ar', 'fa', 'ur', 'he', 'iw', 'ru', 'uk', 'el', 'hi', 'bn', 'ta', //
    'zh', 'ja', 'ko', 'th',
  };
  return app.isEmpty || nonLatinApp.contains(app) ? 'en' : app;
}

String _languageOf(String? code) =>
    (code ?? '').toLowerCase().split(RegExp('[-_]')).first;

class NaturalVoiceNoSpace implements Exception {
  const NaturalVoiceNoSpace(this.requiredBytes);

  final int requiredBytes;

  @override
  String toString() => 'Not enough free storage for the natural voice.';
}

enum NaturalVoiceState { checking, idle, downloading, verifying, error }

/// The read-aloud voice choice and the natural voice's files.
///
/// "Phone's voice" is the default. "Natural voice" is chosen once its files
/// are on the phone: choosing it starts the download (the same verified
/// downloader the speech models use, in the same `voice_models` folder), and
/// the choice takes effect when the files verify. Removing the voice deletes
/// the files and goes back to the phone's voice.
class NaturalVoiceController extends ChangeNotifier {
  NaturalVoiceController({
    required this.root,
    required this.preferences,
    required this.downloader,
    this.packs = naturalVoicePacks,
    this.devicePlatform = voiceDevicePlatform,
  });

  static const _chosenKey = 'voice.read_aloud_natural';

  final String root;
  final SharedPreferences preferences;
  final VoiceModelDownloader downloader;
  final List<NaturalVoicePack> packs;
  final VoiceDevicePlatform devicePlatform;

  NaturalVoiceState state = NaturalVoiceState.checking;
  VoiceDownloadProgress? progress;
  Object? error;

  bool _chosen = false;
  bool _disposed = false;
  int _generation = 0;
  VoiceCancellationToken? _cancellation;
  NaturalVoicePack? _working;
  final Set<String> _installed = {};
  final Set<String> _noted = {};

  static Future<NaturalVoiceController>? _shared;

  static Future<NaturalVoiceController> shared() =>
      _shared ??= _create().catchError((Object error) {
        _shared = null;
        throw error;
      });

  static Future<NaturalVoiceController> _create() async {
    final support = await getApplicationSupportDirectory();
    final preferences = await SharedPreferences.getInstance();
    final controller = NaturalVoiceController(
      root: '${support.path}/voice_models',
      preferences: preferences,
      downloader: VoiceModelDownloader(
        store: const LocalVoiceFileStore(),
        http: LocalVoiceHttpTransport(),
      ),
    );
    await controller.initialize();
    return controller;
  }

  /// The person asked for the natural voice (its files may still be coming).
  bool get chosen => _chosen;

  /// Read-aloud uses the natural voice now: chosen, and a voice is on the
  /// phone.
  bool get active => _chosen && _installed.isNotEmpty;

  bool get busy =>
      state == NaturalVoiceState.downloading ||
      state == NaturalVoiceState.verifying;

  NaturalVoicePack? get workingPack => _working;

  bool isInstalled(NaturalVoicePack pack) => _installed.contains(pack.id);

  /// The voice to read [languageCode] with, or null when none that is on the
  /// phone speaks it.
  NaturalVoicePack? packFor(String languageCode) {
    for (final pack in packs) {
      if (_installed.contains(pack.id) &&
          pack.languages.contains(languageCode)) {
        return pack;
      }
    }
    return null;
  }

  /// True the first time it is asked for [languageCode]: the one plain note
  /// that the natural voice cannot read it, so the phone's voice does.
  bool takeFallbackNote(String languageCode) => _noted.add(languageCode);

  NaturalSpeech speechFor(NaturalVoicePack pack) => NaturalSpeech(
    packID: pack.id,
    modelPath: downloader.filePath(root, pack, pack.model),
    voicesPath: downloader.filePath(root, pack, pack.voices),
    tokensPath: downloader.filePath(root, pack, pack.modelTokens),
    lexiconPath: downloader.filePath(root, pack, pack.lexicon),
    speakerId: pack.speakerId,
    sampleRate: pack.sampleRate,
    // Reading keeps up with speech on two threads; one leaves phones with
    // four or fewer cores responsive.
    numThreads: Platform.numberOfProcessors >= 4 ? 2 : 1,
  );

  Future<void> initialize() async {
    if (_disposed) return;
    final generation = ++_generation;
    state = NaturalVoiceState.checking;
    error = null;
    _chosen = preferences.getBool(_chosenKey) ?? false;
    _notify();
    try {
      final found = <String>{};
      for (final pack in packs) {
        if (await downloader.verifyInstalled(root, pack)) found.add(pack.id);
        if (!_current(generation)) return;
      }
      _installed
        ..clear()
        ..addAll(found);
      state = NaturalVoiceState.idle;
    } catch (exception) {
      if (!_current(generation)) return;
      error = exception;
      state = NaturalVoiceState.error;
    }
    _notify();
  }

  /// "Phone's voice": read with the phone's engine; a running download stops.
  Future<void> choosePhoneVoice() async {
    if (_disposed) return;
    _cancellation?.cancel();
    _chosen = false;
    await preferences.setBool(_chosenKey, false);
    _notify();
  }

  /// "Natural voice": use it now when it is on the phone, else download it
  /// first.
  Future<void> chooseNaturalVoice(NaturalVoicePack pack) async {
    if (_disposed || busy) return;
    if (isInstalled(pack)) {
      _chosen = true;
      await preferences.setBool(_chosenKey, true);
      _notify();
      return;
    }
    await download(pack);
  }

  Future<void> download(NaturalVoicePack pack) async {
    if (_disposed || busy) return;
    final generation = ++_generation;
    _working = pack;
    error = null;
    try {
      final info = await devicePlatform.getDeviceInfo();
      final available = info.availableStorageBytes;
      final int needed =
          pack.downloadBytes +
          math.max(128 * 1024 * 1024, pack.downloadBytes ~/ 4);
      if (available != null && available < needed) {
        throw NaturalVoiceNoSpace(needed);
      }
    } on NaturalVoiceNoSpace catch (exception) {
      error = exception;
      state = NaturalVoiceState.error;
      _notify();
      return;
    } catch (_) {
      // An unmeasurable phone can still try.
    }
    if (!_current(generation)) return;
    final cancellation = VoiceCancellationToken();
    _cancellation = cancellation;
    state = NaturalVoiceState.downloading;
    progress = VoiceDownloadProgress(
      received: 0,
      total: pack.downloadBytes,
      fileName: pack.files.first.name,
    );
    _notify();
    try {
      await downloader.download(
        root,
        pack,
        cancellation: cancellation,
        onProgress: (value) {
          if (!_current(generation) ||
              !identical(_cancellation, cancellation)) {
            return;
          }
          progress = value;
          state = NaturalVoiceState.downloading;
          _notify();
        },
        onVerifying: () {
          if (!_current(generation) ||
              !identical(_cancellation, cancellation)) {
            return;
          }
          state = NaturalVoiceState.verifying;
          _notify();
        },
      );
      if (!_current(generation) || cancellation.isCancelled) return;
      _installed.add(pack.id);
      _chosen = true;
      await preferences.setBool(_chosenKey, true);
      state = NaturalVoiceState.idle;
      progress = null;
    } on VoiceDownloadCancelled {
      if (!_current(generation)) return;
      state = NaturalVoiceState.idle;
      progress = null;
    } catch (exception) {
      if (!_current(generation)) return;
      error = exception;
      state = NaturalVoiceState.error;
      progress = null;
    } finally {
      if (identical(_cancellation, cancellation)) _cancellation = null;
      if (_current(generation)) _notify();
    }
  }

  void cancelDownload() => _cancellation?.cancel();

  /// Deletes the voice's files and goes back to the phone's voice.
  Future<void> remove(NaturalVoicePack pack) async {
    if (_disposed || busy) return;
    final generation = ++_generation;
    await downloader.deletePack(root, pack);
    if (!_current(generation)) return;
    _installed.remove(pack.id);
    if (_installed.isEmpty) {
      _chosen = false;
      await preferences.setBool(_chosenKey, false);
    }
    error = null;
    state = NaturalVoiceState.idle;
    _notify();
  }

  @override
  void dispose() {
    if (_disposed) return;
    _disposed = true;
    ++_generation;
    _cancellation?.cancel();
    super.dispose();
  }

  void _notify() {
    if (!_disposed) notifyListeners();
  }

  bool _current(int generation) => !_disposed && generation == _generation;
}
