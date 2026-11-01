import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/foundation.dart';
import 'package:path_provider/path_provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../platform/network.dart';
import '../platform/platform_capabilities.dart';
import '../voice/device.dart';
import '../voice/model_download.dart';
import '../voice/model_manifest.dart';
import 'local_llm_pack.dart';
import 'local_llm_platform.dart';

/// Why the assistant cannot be offered on this phone, in the order checked.
enum LocalAssistantBlock { platform, abi, memory }

/// The gate: 64-bit phones (the emulator's x86_64 included) with about 6 GB of
/// RAM. Total memory only, never the Java heap class (voice-model-memory-gate).
/// A phone that cannot report its memory is not offered the download.
LocalAssistantBlock? localAssistantBlock(VoiceDeviceInfo device) {
  const abis = {'arm64-v8a', 'x86_64'};
  if (device.supportedAbis.isEmpty ||
      !device.supportedAbis.any(abis.contains)) {
    return LocalAssistantBlock.abi;
  }
  final memory = device.totalMemoryMb;
  if (memory == null || memory < localAssistantPack.minimumMemoryMb) {
    return LocalAssistantBlock.memory;
  }
  return null;
}

/// Space the download needs: the file, a quarter extra for staging and the
/// engine's cache.
int localAssistantRequiredBytes() {
  final bytes = localAssistantPack.downloadBytes;
  return bytes + math.max(512 * 1024 * 1024, bytes ~/ 8);
}

enum LocalAssistantPhase {
  checking,
  idle,
  needsWifi,
  noSpace,
  downloading,
  verifying,
  loading,
  error,
}

enum LocalSummaryStatus { preparing, running, done, stopped, failed }

/// One summary: what the model has written so far and how it ended. Listen to
/// it; [stop] cancels generation on the device.
class LocalSummaryRun extends ChangeNotifier {
  LocalSummaryRun._(this._cancel);

  final Future<void> Function() _cancel;
  final StringBuffer _text = StringBuffer();
  LocalSummaryStatus status = LocalSummaryStatus.preparing;
  String? backend;
  int firstTokenMs = -1;
  int totalMs = 0;
  bool _disposed = false;

  String get text => _text.toString();
  bool get active =>
      status == LocalSummaryStatus.preparing ||
      status == LocalSummaryStatus.running;

  double? get wordsPerSecond {
    final words = text.trim().isEmpty
        ? 0
        : text.trim().split(RegExp(r'\s+')).length;
    final generating = totalMs - math.max(firstTokenMs, 0);
    return generating <= 0 || words == 0 ? null : words * 1000 / generating;
  }

  Future<void> stop() async {
    if (!active) return;
    _set(LocalSummaryStatus.stopped);
    await _cancel();
  }

  void _append(String token) {
    _text.write(token);
    if (status == LocalSummaryStatus.preparing) {
      status = LocalSummaryStatus.running;
    }
    _notify();
  }

  void _set(LocalSummaryStatus value) {
    status = value;
    _notify();
  }

  void _notify() {
    if (!_disposed) notifyListeners();
  }

  @override
  void dispose() {
    _disposed = true;
    super.dispose();
  }
}

/// "Assistant on this phone": the switch, the download, the model's life in
/// memory and the summary it writes. Downloads are never automatic.
class LocalAssistantController extends ChangeNotifier {
  LocalAssistantController({
    required this.root,
    required this.preferences,
    required this.downloader,
    this.platform = const AndroidLocalLlmPlatform(),
    this.devicePlatform = voiceDevicePlatform,
    Future<NetworkReading> Function()? readNetwork,
  }) : _readNetwork = readNetwork ?? (() => const NetworkBridge().current());

  static const enabledKey = 'local_assistant.enabled';

  final String root;
  final SharedPreferences preferences;
  final VoiceModelDownloader downloader;
  final LocalLlmPlatform platform;
  final VoiceDevicePlatform devicePlatform;
  final Future<NetworkReading> Function() _readNetwork;

  static LocalAssistantController? _shared;
  static Future<LocalAssistantController>? _sharedFuture;

  /// The shared controller once [shared] has finished, else null; for menus
  /// that cannot wait.
  static LocalAssistantController? get peek => _shared;

  static Future<LocalAssistantController> shared() => _sharedFuture ??= create()
      .then((value) => _shared = value)
      .catchError((Object error) {
        _sharedFuture = null;
        throw error;
      });

  static Future<LocalAssistantController> create() async {
    final support = await getApplicationSupportDirectory();
    final controller = LocalAssistantController(
      root: '${support.path}/local_llm_models',
      preferences: await SharedPreferences.getInstance(),
      downloader: VoiceModelDownloader(
        store: const LocalVoiceFileStore(),
        http: LocalVoiceHttpTransport(),
      ),
    );
    await controller.initialize();
    return controller;
  }

  LocalAssistantPhase phase = LocalAssistantPhase.checking;
  bool enabled = false;
  bool installed = false;
  bool loaded = false;
  LocalAssistantBlock? block;
  VoiceDownloadProgress? progress;
  Object? error;
  String? backend;
  bool fellBackToCpu = false;
  VoiceCancellationToken? _cancellation;
  StreamSubscription<LocalLlmEvent>? _events;
  LocalSummaryRun? _run;
  String? _runId;
  Future<void>? _loading;
  bool _disposed = false;
  int _generation = 0;
  int _ids = 0;

  VoiceModelPack get pack => localAssistantPack;
  bool get available => block == null;
  bool get busyDownloading =>
      phase == LocalAssistantPhase.downloading ||
      phase == LocalAssistantPhase.verifying;

  /// Whether a conversation's menu offers "Summarize on this phone".
  bool get offersSummary => available && enabled && installed;

  String get modelPath => downloader.filePath(root, pack, pack.files.first);

  Future<void> initialize() async {
    if (_disposed) return;
    final generation = ++_generation;
    phase = LocalAssistantPhase.checking;
    notifyListeners();
    enabled = preferences.getBool(enabledKey) ?? false;
    try {
      final device = platformCapabilities.isAndroid
          ? await devicePlatform.getDeviceInfo()
          : const VoiceDeviceInfo.unsupported();
      if (generation != _generation) return;
      block = platformCapabilities.isAndroid
          ? localAssistantBlock(device)
          : LocalAssistantBlock.platform;
      installed = await _installedQuickly();
      _events ??= platform.events.listen(_onEvent);
      phase = LocalAssistantPhase.idle;
    } catch (exception) {
      if (generation != _generation) return;
      error = exception;
      phase = LocalAssistantPhase.error;
    }
    _notify();
  }

  /// The marker and the file's length; the full hash was checked when the
  /// download finished (hashing 3.7 GB on every launch would take a minute).
  Future<bool> _installedQuickly() async {
    final store = downloader.store;
    if (!await store.exists(downloader.markerPath(root, pack))) return false;
    final path = modelPath;
    return await store.exists(path) &&
        await store.length(path) == pack.files.first.length;
  }

  Future<void> setEnabled(bool value) async {
    if (_disposed || enabled == value) return;
    enabled = value;
    await preferences.setBool(enabledKey, value);
    if (!value) await _unloadNow();
    _notify();
  }

  /// Starts the download. On a mobile or metered connection it stops first
  /// ([LocalAssistantPhase.needsWifi]) unless [allowMobileData] is set.
  Future<void> download({bool allowMobileData = false}) async {
    if (_disposed || !available || busyDownloading || installed) return;
    final generation = ++_generation;
    error = null;
    final reading = await _readNetwork();
    if (generation != _generation) return;
    final onWifi =
        reading.transport == NetworkTransport.wifi && reading.metered != true;
    if (!onWifi && !allowMobileData) {
      phase = LocalAssistantPhase.needsWifi;
      _notify();
      return;
    }
    final device = await devicePlatform.getDeviceInfo();
    if (generation != _generation) return;
    final free = device.availableStorageBytes;
    if (free != null && free < localAssistantRequiredBytes()) {
      phase = LocalAssistantPhase.noSpace;
      _notify();
      return;
    }
    final cancellation = VoiceCancellationToken();
    _cancellation = cancellation;
    phase = LocalAssistantPhase.downloading;
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
          if (generation != _generation || _disposed) return;
          progress = value;
          _notify();
        },
        onVerifying: () {
          if (generation != _generation || _disposed) return;
          phase = LocalAssistantPhase.verifying;
          _notify();
        },
      );
      if (generation != _generation) return;
      installed = true;
      progress = null;
      phase = LocalAssistantPhase.idle;
    } on VoiceDownloadCancelled {
      if (generation != _generation) return;
      progress = null;
      phase = LocalAssistantPhase.idle;
    } catch (exception) {
      if (generation != _generation) return;
      error = exception;
      progress = null;
      phase = LocalAssistantPhase.error;
    } finally {
      if (identical(_cancellation, cancellation)) _cancellation = null;
    }
    _notify();
  }

  void cancelDownload() => _cancellation?.cancel();

  /// Removes the model file (and any half-downloaded one) from the phone.
  Future<void> deleteModel() async {
    if (_disposed || busyDownloading) return;
    await _unloadNow();
    await downloader.deletePack(root, pack);
    installed = false;
    phase = LocalAssistantPhase.idle;
    error = null;
    _notify();
  }

  void dismissProblem() {
    if (phase == LocalAssistantPhase.error ||
        phase == LocalAssistantPhase.needsWifi ||
        phase == LocalAssistantPhase.noSpace) {
      error = null;
      phase = LocalAssistantPhase.idle;
      _notify();
    }
  }

  /// Loads the model if it is not loaded (GPU first, CPU as the fallback).
  Future<void> ensureLoaded() {
    if (loaded) return Future.value();
    return _loading ??= _load().whenComplete(() => _loading = null);
  }

  Future<void> _load() async {
    phase = LocalAssistantPhase.loading;
    _notify();
    try {
      final result = await platform.loadModel(modelPath, backend: 'gpu');
      loaded = true;
      backend = result.backend;
      fellBackToCpu = result.fellBack;
      phase = LocalAssistantPhase.idle;
    } catch (exception) {
      loaded = false;
      error = exception;
      phase = LocalAssistantPhase.error;
      rethrow;
    } finally {
      _notify();
    }
  }

  /// Writes a short summary of [transcript]; the returned run streams it.
  LocalSummaryRun summarize(String transcript) {
    if (_run?.active ?? false) {
      unawaited(_run!.stop());
    }
    final run = LocalSummaryRun._(() => platform.cancel());
    _run = run;
    final id = 'summary-${++_ids}';
    _runId = id;
    unawaited(_runSummary(run, id, transcript));
    return run;
  }

  Future<void> _runSummary(
    LocalSummaryRun run,
    String id,
    String transcript,
  ) async {
    try {
      await ensureLoaded();
      if (!run.active) return; // stopped while the model was loading
      run.backend = backend;
      await platform.generate(
        id,
        buildLocalSummaryPrompt(transcript),
        maxTokens: localSummaryMaxTokens,
      );
    } catch (_) {
      if (run.active) run._set(LocalSummaryStatus.failed);
    }
  }

  void _onEvent(LocalLlmEvent event) {
    final run = _run;
    switch (event) {
      case LocalLlmUnloaded():
        loaded = false;
        backend = null;
        if (run != null && run.active) run._set(LocalSummaryStatus.failed);
        _notify();
      case LocalLlmToken(:final id, :final text):
        if (id == _runId && run != null && run.active) run._append(text);
      case LocalLlmDone():
        if (event.id == _runId && run != null && run.active) {
          run
            ..firstTokenMs = event.firstTokenMs
            ..totalMs = event.totalMs
            .._set(LocalSummaryStatus.done);
        }
      case LocalLlmCancelled():
        if (event.id == _runId && run != null && run.active) {
          run._set(LocalSummaryStatus.stopped);
        }
      case LocalLlmFailed():
        if (event.id == _runId && run != null && run.active) {
          run._set(LocalSummaryStatus.failed);
        }
    }
  }

  Future<void> _unloadNow() async {
    final run = _run;
    if (run != null && run.active) await run.stop();
    if (loaded) {
      loaded = false;
      backend = null;
      await platform.unload();
    }
  }

  @override
  void dispose() {
    if (_disposed) return;
    _disposed = true;
    _cancellation?.cancel();
    unawaited(_events?.cancel());
    super.dispose();
  }

  void _notify() {
    if (!_disposed) notifyListeners();
  }
}
