import 'dart:math' as math;

import 'package:flutter/foundation.dart';
import 'package:path_provider/path_provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'model_download.dart';
import 'device.dart';
import 'model_manifest.dart';

enum VoiceModelState {
  checking,
  required,
  downloading,
  verifying,
  ready,
  loading,
  error,
}

class VoicePackSupport {
  const VoicePackSupport({required this.supported, this.reason});

  final bool supported;
  final String? reason;
}

class VoiceModelPreflightException implements Exception {
  const VoiceModelPreflightException(this.message);

  final String message;

  @override
  String toString() => message;
}

class VoiceModelManager extends ChangeNotifier {
  VoiceModelManager({
    required this.root,
    required this.preferences,
    required this.downloader,
    this.devicePlatform = voiceDevicePlatform,
  });

  static const _selectedPackKey = 'voice.selected_pack';
  static const _languageKey = 'voice.language';

  final String root;
  final SharedPreferences preferences;
  final VoiceModelDownloader downloader;
  final VoiceDevicePlatform devicePlatform;

  VoiceModelState state = VoiceModelState.checking;
  VoiceModelPack selectedPack = voiceModelPacks.first;
  VoiceLanguage language = VoiceLanguage.auto;
  VoiceDownloadProgress? progress;
  VoiceDeviceInfo deviceInfo = const VoiceDeviceInfo.unknown();
  Object? error;
  VoiceCancellationToken? _downloadCancellation;
  final Set<String> _installedPackIDs = {};
  bool _disposed = false;
  bool _preparingDownload = false;

  static Future<VoiceModelManager>? _sharedManager;

  bool isInstalled(VoiceModelPack pack) => _installedPackIDs.contains(pack.id);
  bool get isReady =>
      state == VoiceModelState.ready && isInstalled(selectedPack);

  String pathFor(VoiceModelFile file) =>
      downloader.filePath(root, selectedPack, file);

  static Future<VoiceModelManager> shared() =>
      _sharedManager ??= create().catchError((Object error) {
        _sharedManager = null;
        throw error;
      });

  static Future<VoiceModelManager> create() async {
    final support = await getApplicationSupportDirectory();
    final preferences = await SharedPreferences.getInstance();
    final http = LocalVoiceHttpTransport();
    final manager = VoiceModelManager(
      root: '${support.path}/voice_models',
      preferences: preferences,
      downloader: VoiceModelDownloader(
        store: const LocalVoiceFileStore(),
        http: http,
      ),
    );
    await manager.initialize();
    return manager;
  }

  Future<void> initialize() async {
    if (_disposed) return;
    state = VoiceModelState.checking;
    error = null;
    selectedPack = voiceModelPack(
      preferences.getString(_selectedPackKey) ?? 'base',
    );
    language = VoiceLanguage.fromID(preferences.getString(_languageKey));
    notifyListeners();
    try {
      deviceInfo = await devicePlatform.getDeviceInfo();
      if (_disposed) return;
      _installedPackIDs.clear();
      for (final pack in voiceModelPacks) {
        if (await downloader.verifyInstalled(root, pack)) {
          _installedPackIDs.add(pack.id);
        }
      }
      state = isInstalled(selectedPack)
          ? VoiceModelState.ready
          : VoiceModelState.required;
    } catch (exception) {
      error = exception;
      state = VoiceModelState.error;
    }
    _notify();
  }

  int requiredStorageBytes(VoiceModelPack pack) =>
      pack.downloadBytes + math.max(128 * 1024 * 1024, pack.downloadBytes ~/ 4);

  VoicePackSupport supportFor(VoiceModelPack pack, {bool replacing = false}) {
    const runtimeAbis = {'arm64-v8a', 'armeabi-v7a', 'x86', 'x86_64'};
    if (deviceInfo.supportedAbis.isNotEmpty &&
        !deviceInfo.supportedAbis.any(runtimeAbis.contains)) {
      return VoicePackSupport(
        supported: false,
        reason: 'No bundled voice runtime supports this device ABI.',
      );
    }
    final memory = deviceInfo.memoryClassMb;
    if (memory != null && memory < pack.minimumMemoryMb) {
      return VoicePackSupport(
        supported: false,
        reason:
            '${pack.label} needs at least ${pack.minimumMemoryMb} MB of app memory; this device reports $memory MB.',
      );
    }
    final available = deviceInfo.availableStorageBytes;
    if ((!isInstalled(pack) || replacing) &&
        available != null &&
        available < requiredStorageBytes(pack)) {
      return VoicePackSupport(
        supported: false,
        reason:
            '${pack.label} needs ${formatModelBytes(requiredStorageBytes(pack))} free, including a safety margin.',
      );
    }
    return const VoicePackSupport(supported: true);
  }

  Future<void> selectPack(VoiceModelPack pack) async {
    if (_disposed ||
        _preparingDownload ||
        state == VoiceModelState.downloading ||
        state == VoiceModelState.verifying ||
        state == VoiceModelState.loading) {
      return;
    }
    selectedPack = pack;
    await preferences.setString(_selectedPackKey, pack.id);
    if (_disposed) return;
    state = isInstalled(pack)
        ? VoiceModelState.ready
        : VoiceModelState.required;
    error = null;
    _notify();
  }

  Future<void> setLanguage(VoiceLanguage value) async {
    if (_disposed) return;
    language = value;
    await preferences.setString(_languageKey, value.name);
    if (_disposed) return;
    _notify();
  }

  Future<void> downloadSelected({bool replaceExisting = false}) async {
    if (_disposed ||
        _preparingDownload ||
        state == VoiceModelState.downloading ||
        state == VoiceModelState.verifying ||
        state == VoiceModelState.loading) {
      return;
    }
    _preparingDownload = true;
    final pack = selectedPack;
    try {
      deviceInfo = await devicePlatform.getDeviceInfo();
    } catch (exception) {
      _preparingDownload = false;
      if (_disposed) return;
      error = exception;
      state = VoiceModelState.error;
      _notify();
      return;
    }
    if (_disposed || selectedPack.id != pack.id) {
      _preparingDownload = false;
      return;
    }
    final support = supportFor(pack, replacing: replaceExisting);
    if (!support.supported) {
      _preparingDownload = false;
      error = VoiceModelPreflightException(support.reason!);
      state = VoiceModelState.error;
      _notify();
      return;
    }
    final cancellation = VoiceCancellationToken();
    _downloadCancellation = cancellation;
    _preparingDownload = false;
    state = VoiceModelState.downloading;
    error = null;
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
        replaceExisting: replaceExisting,
        onProgress: (value) {
          if (_disposed || !identical(_downloadCancellation, cancellation)) {
            return;
          }
          progress = value;
          if (state != VoiceModelState.downloading) {
            state = VoiceModelState.downloading;
          }
          _notify();
        },
        onVerifying: () {
          if (_disposed || !identical(_downloadCancellation, cancellation)) {
            return;
          }
          state = VoiceModelState.verifying;
          _notify();
        },
      );
      if (cancellation.isCancelled) return;
      _installedPackIDs.add(pack.id);
      state = selectedPack.id == pack.id
          ? VoiceModelState.ready
          : VoiceModelState.required;
      progress = null;
    } on VoiceDownloadCancelled {
      state = isInstalled(selectedPack)
          ? VoiceModelState.ready
          : VoiceModelState.required;
      progress = null;
    } catch (exception) {
      error = exception;
      state = VoiceModelState.error;
    } finally {
      if (identical(_downloadCancellation, cancellation)) {
        _downloadCancellation = null;
      }
      _notify();
    }
  }

  void cancelDownload() => _downloadCancellation?.cancel();

  Future<void> deletePack(VoiceModelPack pack) async {
    if (_disposed ||
        _preparingDownload ||
        state == VoiceModelState.downloading ||
        state == VoiceModelState.verifying ||
        state == VoiceModelState.loading) {
      return;
    }
    await downloader.deletePack(root, pack);
    _installedPackIDs.remove(pack.id);
    if (selectedPack.id == pack.id) state = VoiceModelState.required;
    _notify();
  }

  Future<void> redownloadPack(VoiceModelPack pack) async {
    if (_disposed) return;
    await selectPack(pack);
    await downloadSelected(replaceExisting: true);
  }

  void markLoading() {
    if (!_disposed && isInstalled(selectedPack)) {
      state = VoiceModelState.loading;
      notifyListeners();
    }
  }

  void markReady() {
    if (!_disposed && isInstalled(selectedPack)) {
      state = VoiceModelState.ready;
      notifyListeners();
    }
  }

  @override
  void dispose() {
    if (_disposed) return;
    _disposed = true;
    _downloadCancellation?.cancel();
    downloader.http.close();
    super.dispose();
  }

  void _notify() {
    if (!_disposed) notifyListeners();
  }
}
