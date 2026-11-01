import 'dart:async';
import 'dart:typed_data';

import 'package:opencode_mobile/local_llm/local_llm_controller.dart';
import 'package:opencode_mobile/local_llm/local_llm_pack.dart';
import 'package:opencode_mobile/local_llm/local_llm_platform.dart';
import 'package:opencode_mobile/platform/network.dart';
import 'package:opencode_mobile/voice/device.dart';
import 'package:opencode_mobile/voice/model_download.dart';
import 'package:opencode_mobile/voice/model_manifest.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _NoHttp implements VoiceHttpTransport {
  @override
  Future<VoiceHttpResponse> get(
    Uri uri, {
    Map<String, String> headers = const {},
    VoiceCancellationToken? cancellation,
  }) => throw UnimplementedError();

  @override
  void close() {}
}

/// Files by path -> length; enough for the controller's installed check.
class MemoryLengthStore implements VoiceFileStore {
  final Map<String, int> lengths = {};

  @override
  Future<bool> exists(String path) async => lengths.containsKey(path);
  @override
  Future<int> length(String path) async => lengths[path] ?? 0;
  @override
  Future<void> delete(String path) async => lengths.remove(path);
  @override
  Future<void> createDirectory(String path) async {}
  @override
  Stream<List<int>> read(String path) => const Stream.empty();
  @override
  Future<Uint8List> readBytes(String path) async => Uint8List(0);
  @override
  Future<VoiceByteSink> openWrite(String path, {required bool append}) =>
      throw UnimplementedError();
  @override
  Future<void> move(String from, String to) async {}
  @override
  Future<void> writeAtomic(String path, List<int> bytes) async {}
}

class FakeAssistantDownloader extends VoiceModelDownloader {
  FakeAssistantDownloader(this.files) : super(store: files, http: _NoHttp());

  final MemoryLengthStore files;
  int downloads = 0;
  Completer<void>? gate;

  void installNow() {
    files.lengths[markerPath('/m', localAssistantPack)] = 1;
    files.lengths[filePath(
          '/m',
          localAssistantPack,
          localAssistantPack.files.first,
        )] =
        localAssistantPack.files.first.length;
  }

  @override
  Future<void> download(
    String root,
    VoiceModelPack pack, {
    required VoiceCancellationToken cancellation,
    required void Function(VoiceDownloadProgress progress) onProgress,
    required void Function() onVerifying,
    bool replaceExisting = false,
  }) async {
    downloads++;
    onProgress(
      VoiceDownloadProgress(
        received: pack.downloadBytes ~/ 2,
        total: pack.downloadBytes,
        fileName: 'x',
      ),
    );
    final hold = gate;
    if (hold != null) {
      await Future.any([hold.future, cancellation.whenCancelled]);
    }
    cancellation.throwIfCancelled();
    onVerifying();
    installNow();
  }

  @override
  Future<void> deletePack(String root, VoiceModelPack pack) async {
    files.lengths.clear();
  }
}

class FakeLlm implements LocalLlmPlatform {
  final controller = StreamController<LocalLlmEvent>.broadcast();
  final calls = <String>[];
  String backendToUse = 'gpu';
  bool fellBack = false;
  Object? loadError;
  Completer<void>? loadGate;
  String? lastPrompt;
  int? lastMaxTokens;

  @override
  Stream<LocalLlmEvent> get events => controller.stream;

  @override
  Future<LocalLlmLoaded> loadModel(
    String path, {
    String backend = 'gpu',
  }) async {
    calls.add('load:$backend');
    await loadGate?.future;
    final error = loadError;
    if (error != null) throw error;
    return LocalLlmLoaded(backend: backendToUse, fellBack: fellBack);
  }

  @override
  Future<void> generate(
    String id,
    String prompt, {
    required int maxTokens,
  }) async {
    calls.add('generate:$id');
    lastPrompt = prompt;
    lastMaxTokens = maxTokens;
  }

  @override
  Future<void> cancel() async => calls.add('cancel');

  @override
  Future<void> unload() async => calls.add('unload');

  void emit(LocalLlmEvent event) => controller.add(event);
}

class FakeDevice implements VoiceDevicePlatform {
  FakeDevice(this.info);
  VoiceDeviceInfo info;

  @override
  Future<VoiceDeviceInfo> getDeviceInfo() async => info;
  @override
  Future<VoiceMicrophonePermission> requestMicrophonePermission() async =>
      VoiceMicrophonePermission.granted;
  @override
  Future<void> openAppSettings() async {}
}

VoiceDeviceInfo phone({
  int? totalMemoryMb = 8000,
  List<String> abis = const ['arm64-v8a'],
  int? freeBytes = 20 * 1000 * 1000 * 1000,
}) => VoiceDeviceInfo(
  availableStorageBytes: freeBytes,
  memoryClassMb: 256,
  totalMemoryMb: totalMemoryMb,
  supportedAbis: abis,
  hasMicrophone: true,
);

const wifi = NetworkReading(
  status: NetworkStatus.online,
  transport: NetworkTransport.wifi,
  metered: false,
  connected: true,
);
const mobile = NetworkReading(
  status: NetworkStatus.online,
  transport: NetworkTransport.mobile,
  metered: true,
  connected: true,
);

class Rig {
  Rig._(this.controller, this.llm, this.downloader, this.device);

  final LocalAssistantController controller;
  final FakeLlm llm;
  final FakeAssistantDownloader downloader;
  final FakeDevice device;

  static Future<Rig> create({
    VoiceDeviceInfo? info,
    NetworkReading network = wifi,
    bool installed = false,
    bool enabled = false,
  }) async {
    SharedPreferences.setMockInitialValues({
      if (enabled) LocalAssistantController.enabledKey: true,
    });
    final llm = FakeLlm();
    final downloader = FakeAssistantDownloader(MemoryLengthStore());
    if (installed) downloader.installNow();
    final device = FakeDevice(info ?? phone());
    final controller = LocalAssistantController(
      root: '/m',
      preferences: await SharedPreferences.getInstance(),
      downloader: downloader,
      platform: llm,
      devicePlatform: device,
      readNetwork: () async => network,
    );
    await controller.initialize();
    return Rig._(controller, llm, downloader, device);
  }
}
