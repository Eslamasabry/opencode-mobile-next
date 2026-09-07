import 'dart:async';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/voice/device.dart';
import 'package:opencode_mobile/voice/model_download.dart';
import 'package:opencode_mobile/voice/model_manager.dart';
import 'package:opencode_mobile/voice/model_manifest.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _NoopStore implements VoiceFileStore {
  @override
  Future<void> createDirectory(String path) async {}

  @override
  Future<void> delete(String path) async {}

  @override
  Future<bool> exists(String path) async => false;

  @override
  Future<int> length(String path) async => 0;

  @override
  Stream<List<int>> read(String path) => const Stream.empty();

  @override
  Future<Uint8List> readBytes(String path) async => Uint8List(0);

  @override
  Future<VoiceByteSink> openWrite(String path, {required bool append}) async =>
      throw UnimplementedError();

  @override
  Future<void> move(String from, String to) async {}

  @override
  Future<void> writeAtomic(String path, List<int> bytes) async {}
}

class _FakeHttp implements VoiceHttpTransport {
  int closeCalls = 0;

  @override
  Future<VoiceHttpResponse> get(
    Uri uri, {
    Map<String, String> headers = const {},
    VoiceCancellationToken? cancellation,
  }) async => throw UnimplementedError();

  @override
  void close() => closeCalls++;
}

class _FakeDevicePlatform implements VoiceDevicePlatform {
  final VoiceDeviceInfo info = const VoiceDeviceInfo.unknown();
  Completer<VoiceDeviceInfo>? infoGate;
  int infoCalls = 0;

  @override
  Future<VoiceDeviceInfo> getDeviceInfo() {
    infoCalls++;
    return infoGate?.future ?? Future.value(info);
  }

  @override
  Future<VoiceMicrophonePermission> requestMicrophonePermission() async =>
      VoiceMicrophonePermission.granted;

  @override
  Future<void> openAppSettings() async {}
}

class _FakeDownloader extends VoiceModelDownloader {
  _FakeDownloader() : this._(_FakeHttp());

  _FakeDownloader._(this.httpControl)
    : super(store: _NoopStore(), http: httpControl);

  final _FakeHttp httpControl;
  Completer<bool>? baseVerification;
  Completer<void>? baseVerificationStarted;
  Completer<void>? downloadGate;
  Completer<void>? downloadStarted;
  int verifyCalls = 0;
  int downloadCalls = 0;
  int deleteCalls = 0;

  @override
  Future<bool> verifyInstalled(
    String root,
    VoiceModelPack pack, {
    VoiceCancellationToken? cancellation,
  }) async {
    verifyCalls++;
    if (pack.id == 'base' && baseVerification != null) {
      baseVerificationStarted?.complete();
      return baseVerification!.future;
    }
    return false;
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
    downloadCalls++;
    downloadStarted?.complete();
    await downloadGate?.future;
  }

  @override
  Future<void> deletePack(String root, VoiceModelPack pack) async {
    deleteCalls++;
  }
}

Future<VoiceModelManager> _manager(
  _FakeDownloader downloader, {
  _FakeDevicePlatform? device,
}) async {
  SharedPreferences.setMockInitialValues({});
  final preferences = await SharedPreferences.getInstance();
  return VoiceModelManager(
    root: '/models',
    preferences: preferences,
    downloader: downloader,
    devicePlatform: device ?? _FakeDevicePlatform(),
  );
}

void main() {
  test(
    'deletion invalidates an in-flight initialize readiness result',
    () async {
      final downloader = _FakeDownloader()
        ..baseVerification = Completer<bool>()
        ..baseVerificationStarted = Completer<void>();
      final manager = await _manager(downloader);
      addTearDown(manager.dispose);

      final initializing = manager.initialize();
      await downloader.baseVerificationStarted!.future;
      await manager.deletePack(voiceModelPack('base'));
      downloader.baseVerification!.complete(true);
      await initializing;

      expect(downloader.deleteCalls, 1);
      expect(manager.isInstalled(voiceModelPack('base')), isFalse);
      expect(manager.state, VoiceModelState.required);
      expect(manager.isReady, isFalse);
    },
  );

  test('a new selection invalidates old initialize verification', () async {
    final downloader = _FakeDownloader()
      ..baseVerification = Completer<bool>()
      ..baseVerificationStarted = Completer<void>();
    final manager = await _manager(downloader);
    addTearDown(manager.dispose);

    final initializing = manager.initialize();
    await downloader.baseVerificationStarted!.future;
    await manager.selectPack(voiceModelPack('small'));
    downloader.baseVerification!.complete(true);
    await initializing;

    expect(manager.selectedPack.id, 'small');
    expect(manager.isInstalled(voiceModelPack('base')), isFalse);
    expect(manager.state, VoiceModelState.required);
    expect(manager.isReady, isFalse);
  });

  test(
    'initialize cannot publish readiness from a superseded download',
    () async {
      final downloader = _FakeDownloader()
        ..downloadGate = Completer<void>()
        ..downloadStarted = Completer<void>();
      final manager = await _manager(downloader);
      addTearDown(manager.dispose);

      final downloading = manager.downloadSelected();
      await downloader.downloadStarted!.future;
      expect(manager.state, VoiceModelState.downloading);

      await manager.initialize();
      expect(manager.state, VoiceModelState.required);
      downloader.downloadGate!.complete();
      await downloading;

      expect(manager.isInstalled(voiceModelPack('base')), isFalse);
      expect(manager.state, VoiceModelState.required);
      expect(manager.isReady, isFalse);
    },
  );

  test('dispose during device preflight prevents download work', () async {
    final device = _FakeDevicePlatform()
      ..infoGate = Completer<VoiceDeviceInfo>();
    final downloader = _FakeDownloader();
    final manager = await _manager(downloader, device: device);

    final downloading = manager.downloadSelected();
    expect(device.infoCalls, 1);
    manager.dispose();
    device.infoGate!.complete(const VoiceDeviceInfo.unknown());
    await downloading;

    expect(downloader.downloadCalls, 0);
    expect(downloader.httpControl.closeCalls, 1);
  });

  test(
    'superseded initialize does not publish its probed device info',
    () async {
      final sentinel = const VoiceDeviceInfo(
        availableStorageBytes: 1,
        memoryClassMb: 1,
        supportedAbis: ['sentinel'],
        hasMicrophone: true,
      );
      final device = _FakeDevicePlatform()
        ..infoGate = Completer<VoiceDeviceInfo>();
      final downloader = _FakeDownloader();
      final manager = await _manager(downloader, device: device);
      addTearDown(manager.dispose);
      manager.deviceInfo = sentinel;

      final initializing = manager.initialize();
      expect(device.infoCalls, 1);
      await manager.selectPack(voiceModelPack('small'));
      device.infoGate!.complete(const VoiceDeviceInfo.unknown());
      await initializing;

      expect(manager.deviceInfo, same(sentinel));
      expect(downloader.verifyCalls, 0);
    },
  );

  test(
    'cancel during device preflight prevents work and stale device info',
    () async {
      final sentinel = const VoiceDeviceInfo(
        availableStorageBytes: 1,
        memoryClassMb: 1,
        supportedAbis: ['sentinel'],
        hasMicrophone: true,
      );
      final device = _FakeDevicePlatform()
        ..infoGate = Completer<VoiceDeviceInfo>();
      final downloader = _FakeDownloader();
      final manager = await _manager(downloader, device: device);
      addTearDown(manager.dispose);
      manager.deviceInfo = sentinel;

      final downloading = manager.downloadSelected();
      expect(device.infoCalls, 1);
      manager.cancelDownload();
      device.infoGate!.complete(const VoiceDeviceInfo.unknown());
      await downloading;

      expect(downloader.downloadCalls, 0);
      expect(manager.deviceInfo, same(sentinel));
      expect(manager.state, VoiceModelState.required);
    },
  );
}
