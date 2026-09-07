import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/voice/model_download.dart';
import 'package:opencode_mobile/voice/model_manifest.dart';

const _file = VoiceModelFile(
  name: 'model.onnx',
  length: 5,
  sha256: '2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824',
);
const _pack = VoiceModelPack(
  id: 'test',
  label: 'Test',
  description: 'Test',
  repository: 'owner/repository',
  revision: '0123456789012345678901234567890123456789',
  minimumMemoryMb: 1,
  files: [_file],
);

class _MemorySink implements VoiceByteSink {
  _MemorySink(this.onClose, List<int> initial)
    : bytes = BytesBuilder()..add(initial);

  final void Function(Uint8List bytes) onClose;
  final BytesBuilder bytes;

  @override
  void add(List<int> value) => bytes.add(value);

  @override
  Future<void> close() async => onClose(bytes.takeBytes());
}

class _MemoryStore implements VoiceFileStore {
  final Map<String, Uint8List> files = {};
  int atomicWrites = 0;

  void put(String path, List<int> bytes) =>
      files[path] = Uint8List.fromList(bytes);

  @override
  Future<void> createDirectory(String path) async {}

  @override
  Future<void> delete(String path) async => files.remove(path);

  @override
  Future<bool> exists(String path) async => files.containsKey(path);

  @override
  Future<int> length(String path) async => files[path]!.length;

  @override
  Future<void> move(String from, String to) async {
    files[to] = files.remove(from)!;
  }

  @override
  Future<VoiceByteSink> openWrite(String path, {required bool append}) async =>
      _MemorySink(
        (bytes) => files[path] = bytes,
        append ? files[path] ?? const [] : const [],
      );

  @override
  Stream<List<int>> read(String path) => Stream.value(files[path]!);

  @override
  Future<Uint8List> readBytes(String path) async => files[path]!;

  @override
  Future<void> writeAtomic(String path, List<int> bytes) async {
    atomicWrites++;
    files[path] = Uint8List.fromList(bytes);
  }
}

class _FailingPublicationStore extends _MemoryStore {
  bool failMarkerPublication = false;

  @override
  Future<void> move(String from, String to) async {
    if (failMarkerPublication &&
        from.contains('.installing/') &&
        from.endsWith('/${VoiceModelDownloader.markerName}')) {
      failMarkerPublication = false;
      throw FileSystemException('simulated atomic publication failure', to);
    }
    await super.move(from, to);
  }
}

class _FailingDeleteStore extends _MemoryStore {
  String? failingPath;

  @override
  Future<void> delete(String path) async {
    if (path == failingPath) {
      failingPath = null;
      throw FileSystemException('simulated cleanup failure', path);
    }
    await super.delete(path);
  }
}

class _ThrowingSink implements VoiceByteSink {
  @override
  void add(List<int> bytes) => throw StateError('simulated sink failure');

  @override
  Future<void> close() async {}
}

class _ThrowingSinkStore extends _MemoryStore {
  @override
  Future<VoiceByteSink> openWrite(String path, {required bool append}) async =>
      _ThrowingSink();
}

class _CancellingOnCloseStore extends _MemoryStore {
  _CancellingOnCloseStore(this.cancellation);

  final VoiceCancellationToken cancellation;

  @override
  Future<VoiceByteSink> openWrite(String path, {required bool append}) async =>
      _MemorySink((bytes) {
        files[path] = bytes;
        cancellation.cancel();
      }, append ? files[path] ?? const [] : const []);
}

class _FakeHttp implements VoiceHttpTransport {
  _FakeHttp(this.handler);

  final VoiceHttpResponse Function(Map<String, String> headers) handler;
  Map<String, String>? lastHeaders;

  @override
  void close() {}

  @override
  Future<VoiceHttpResponse> get(
    Uri uri, {
    Map<String, String> headers = const {},
    VoiceCancellationToken? cancellation,
  }) async {
    expect(uri.scheme, 'https');
    lastHeaders = headers;
    return handler(headers);
  }
}

VoiceHttpResponse _response(
  List<int> bytes, {
  int status = 200,
  Map<String, String> headers = const {},
}) => VoiceHttpResponse(
  statusCode: status,
  contentLength: bytes.length,
  headers: headers,
  body: Stream.value(bytes),
);

void main() {
  test(
    'valid download is checksum verified before atomic install marker',
    () async {
      final store = _MemoryStore();
      final http = _FakeHttp((_) => _response(utf8.encode('hello')));
      final downloader = VoiceModelDownloader(store: store, http: http);

      await downloader.download(
        '/models',
        _pack,
        cancellation: VoiceCancellationToken(),
        onProgress: (_) {},
        onVerifying: () {},
      );

      expect(store.atomicWrites, 1);
      expect(await downloader.verifyInstalled('/models', _pack), isTrue);
      expect(
        store.files[downloader.filePath('/models', _pack, _file)],
        utf8.encode('hello'),
      );

      store.put(
        downloader.filePath('/models', _pack, _file),
        utf8.encode('HELLO'),
      );
      expect(await downloader.verifyInstalled('/models', _pack), isFalse);
      expect(
        store.files,
        isNot(contains(downloader.markerPath('/models', _pack))),
      );
    },
  );

  test(
    'safe partial response resumes only with matching Content-Range',
    () async {
      final store = _MemoryStore();
      late final VoiceModelDownloader downloader;
      final http = _FakeHttp((headers) {
        expect(headers['Range'], 'bytes=2-');
        return _response(
          utf8.encode('llo'),
          status: 206,
          headers: const {'content-range': 'bytes 2-4/5'},
        );
      });
      downloader = VoiceModelDownloader(store: store, http: http);
      store.put(
        '${downloader.packDirectory('/models', _pack)}.installing/${_file.name}.part',
        utf8.encode('he'),
      );

      await downloader.download(
        '/models',
        _pack,
        cancellation: VoiceCancellationToken(),
        onProgress: (_) {},
        onVerifying: () {},
      );

      expect(await downloader.verifyInstalled('/models', _pack), isTrue);
    },
  );

  test(
    'corrupt payload and cancellation never create an install marker',
    () async {
      final corruptStore = _MemoryStore();
      final corruptDownloader = VoiceModelDownloader(
        store: corruptStore,
        http: _FakeHttp((_) => _response(utf8.encode('HELLO'))),
      );
      await expectLater(
        corruptDownloader.download(
          '/models',
          _pack,
          cancellation: VoiceCancellationToken(),
          onProgress: (_) {},
          onVerifying: () {},
        ),
        throwsA(isA<VoiceDownloadException>()),
      );
      expect(
        corruptStore.files,
        isNot(contains(corruptDownloader.markerPath('/models', _pack))),
      );

      final cancelledStore = _MemoryStore();
      final cancelledDownloader = VoiceModelDownloader(
        store: cancelledStore,
        http: _FakeHttp((_) => _response(utf8.encode('hello'))),
      );
      final cancellation = VoiceCancellationToken();
      await expectLater(
        cancelledDownloader.download(
          '/models',
          _pack,
          cancellation: cancellation,
          onProgress: (_) => cancellation.cancel(),
          onVerifying: () {},
        ),
        throwsA(isA<VoiceDownloadCancelled>()),
      );
      expect(cancelledStore.files, isEmpty);
    },
  );

  test('real filesystem install verifies streamed SHA-256 exactly', () async {
    final temporary = await Directory.systemTemp.createTemp('voice-model-test');
    addTearDown(() => temporary.delete(recursive: true));
    final downloader = VoiceModelDownloader(
      store: const LocalVoiceFileStore(),
      http: _FakeHttp((_) => _response(utf8.encode('hello'))),
    );

    await downloader.download(
      temporary.path,
      _pack,
      cancellation: VoiceCancellationToken(),
      onProgress: (_) {},
      onVerifying: () {},
    );

    expect(await downloader.verifyInstalled(temporary.path, _pack), isTrue);
    await File(
      downloader.filePath(temporary.path, _pack, _file),
    ).writeAsString('HELLO', flush: true);
    expect(await downloader.verifyInstalled(temporary.path, _pack), isFalse);
  });

  test('cancellation aborts a stalled response body promptly', () async {
    final store = _MemoryStore();
    final body = StreamController<List<int>>();
    var aborts = 0;
    final downloader = VoiceModelDownloader(
      store: store,
      http: _FakeHttp(
        (_) => VoiceHttpResponse(
          statusCode: 200,
          contentLength: 5,
          headers: const {},
          body: body.stream,
          abort: () => aborts++,
        ),
      ),
      bodyIdleTimeout: const Duration(seconds: 5),
    );
    final cancellation = VoiceCancellationToken();
    final download = downloader.download(
      '/models',
      _pack,
      cancellation: cancellation,
      onProgress: (_) {},
      onVerifying: () {},
    );
    await Future<void>.delayed(const Duration(milliseconds: 20));
    cancellation.cancel();

    await expectLater(
      download.timeout(const Duration(seconds: 1)),
      throwsA(isA<VoiceDownloadCancelled>()),
    );
    expect(aborts, greaterThan(0));
    await body.close();
  });

  test('cancellation aborts a stalled native TLS handshake promptly', () async {
    final server = await ServerSocket.bind(InternetAddress.loopbackIPv4, 0);
    final sockets = <Socket>[];
    final serverSubscription = server.listen(sockets.add);
    final transport = LocalVoiceHttpTransport(
      connectTimeout: const Duration(seconds: 30),
    );
    addTearDown(() async {
      transport.close();
      for (final socket in sockets) {
        socket.destroy();
      }
      await serverSubscription.cancel();
      await server.close();
    });
    final cancellation = VoiceCancellationToken();
    final request = transport.get(
      Uri.parse('https://127.0.0.1:${server.port}/model'),
      cancellation: cancellation,
    );
    await Future<void>.delayed(const Duration(milliseconds: 30));
    cancellation.cancel();

    await expectLater(
      request.timeout(const Duration(seconds: 1)),
      throwsA(isA<VoiceDownloadCancelled>()),
    );
  });

  test('stalled response body fails on the idle timeout', () async {
    final body = StreamController<List<int>>();
    var aborts = 0;
    final downloader = VoiceModelDownloader(
      store: _MemoryStore(),
      http: _FakeHttp(
        (_) => VoiceHttpResponse(
          statusCode: 200,
          contentLength: 5,
          headers: const {},
          body: body.stream,
          abort: () => aborts++,
        ),
      ),
      bodyIdleTimeout: const Duration(milliseconds: 30),
    );

    await expectLater(
      downloader.download(
        '/models',
        _pack,
        cancellation: VoiceCancellationToken(),
        onProgress: (_) {},
        onVerifying: () {},
      ),
      throwsA(
        isA<VoiceDownloadException>().having(
          (error) => error.message,
          'message',
          contains('Timed out'),
        ),
      ),
    );
    expect(aborts, greaterThan(0));
    await body.close();
  });

  test('failed atomic re-download retains the verified old model', () async {
    final store = _MemoryStore();
    final good = VoiceModelDownloader(
      store: store,
      http: _FakeHttp((_) => _response(utf8.encode('hello'))),
    );
    await good.download(
      '/models',
      _pack,
      cancellation: VoiceCancellationToken(),
      onProgress: (_) {},
      onVerifying: () {},
    );
    final corrupt = VoiceModelDownloader(
      store: store,
      http: _FakeHttp((_) => _response(utf8.encode('HELLO'))),
    );

    await expectLater(
      corrupt.download(
        '/models',
        _pack,
        replaceExisting: true,
        cancellation: VoiceCancellationToken(),
        onProgress: (_) {},
        onVerifying: () {},
      ),
      throwsA(isA<VoiceDownloadException>()),
    );

    expect(await good.verifyInstalled('/models', _pack), isTrue);
    expect(
      store.files[good.filePath('/models', _pack, _file)],
      utf8.encode('hello'),
    );
  });

  test('publication failure rolls back every verified old file', () async {
    final store = _FailingPublicationStore();
    final downloader = VoiceModelDownloader(
      store: store,
      http: _FakeHttp((_) => _response(utf8.encode('hello'))),
    );
    await downloader.download(
      '/models',
      _pack,
      cancellation: VoiceCancellationToken(),
      onProgress: (_) {},
      onVerifying: () {},
    );
    store.failMarkerPublication = true;

    await expectLater(
      downloader.download(
        '/models',
        _pack,
        replaceExisting: true,
        cancellation: VoiceCancellationToken(),
        onProgress: (_) {},
        onVerifying: () {},
      ),
      throwsA(isA<FileSystemException>()),
    );

    expect(await downloader.verifyInstalled('/models', _pack), isTrue);
    expect(
      store.files[downloader.filePath('/models', _pack, _file)],
      utf8.encode('hello'),
    );
    expect(
      store.files.keys.where((path) => path.endsWith('.voice-backup')),
      isEmpty,
    );
  });

  test(
    'same-pack installs are serialized across downloader instances',
    () async {
      final store = _MemoryStore();
      var activeBodies = 0;
      var maximumActiveBodies = 0;
      VoiceHttpResponse response(Map<String, String> _) => VoiceHttpResponse(
        statusCode: 200,
        contentLength: 5,
        headers: const {},
        body: (() async* {
          activeBodies++;
          maximumActiveBodies = maximumActiveBodies < activeBodies
              ? activeBodies
              : maximumActiveBodies;
          await Future<void>.delayed(const Duration(milliseconds: 20));
          yield utf8.encode('hello');
          activeBodies--;
        })(),
      );
      final first = VoiceModelDownloader(
        store: store,
        http: _FakeHttp(response),
      );
      final second = VoiceModelDownloader(
        store: store,
        http: _FakeHttp(response),
      );

      await Future.wait([
        first.download(
          '/models',
          _pack,
          replaceExisting: true,
          cancellation: VoiceCancellationToken(),
          onProgress: (_) {},
          onVerifying: () {},
        ),
        second.download(
          '/models',
          _pack,
          replaceExisting: true,
          cancellation: VoiceCancellationToken(),
          onProgress: (_) {},
          onVerifying: () {},
        ),
      ]);

      expect(maximumActiveBodies, 1);
      expect(await first.verifyInstalled('/models', _pack), isTrue);
    },
  );

  test(
    'delete waits for an in-flight install and removes every owned path',
    () async {
      final store = _MemoryStore();
      final bodyStarted = Completer<void>();
      final releaseBody = Completer<void>();
      final downloader = VoiceModelDownloader(
        store: store,
        http: _FakeHttp(
          (_) => VoiceHttpResponse(
            statusCode: 200,
            contentLength: 5,
            headers: const {},
            body: (() async* {
              bodyStarted.complete();
              await releaseBody.future;
              yield utf8.encode('hello');
            })(),
          ),
        ),
      );
      final directory = downloader.packDirectory('/models', _pack);
      store.put(downloader.markerPath('/models', _pack), utf8.encode('stale'));
      store.put('${downloader.markerPath('/models', _pack)}.tmp', [0]);
      store.put('${downloader.markerPath('/models', _pack)}.voice-backup', [1]);
      store.put('$directory.installing/${VoiceModelDownloader.markerName}', [
        5,
      ]);
      store.put(
        '$directory.installing/${VoiceModelDownloader.markerName}.tmp',
        [6],
      );
      store.put('$directory.installing/${_file.name}', [2]);
      store.put('$directory.installing/${_file.name}.part', [3]);
      store.put(
        '${downloader.filePath('/models', _pack, _file)}.voice-backup',
        [4],
      );

      final download = downloader.download(
        '/models',
        _pack,
        replaceExisting: true,
        cancellation: VoiceCancellationToken(),
        onProgress: (_) {},
        onVerifying: () {},
      );
      await bodyStarted.future;

      var deleted = false;
      final deletion = downloader.deletePack('/models', _pack).then((_) {
        deleted = true;
      });
      expect(deleted, isFalse);

      releaseBody.complete();
      await download;
      await deletion;
      expect(store.files, isEmpty);
    },
  );

  test(
    'cleanup failure releases the pack lock for a later operation',
    () async {
      final store = _FailingDeleteStore();
      final downloader = VoiceModelDownloader(
        store: store,
        http: _FakeHttp((_) => _response(utf8.encode('hello'))),
      );
      final marker = downloader.markerPath('/models', _pack);
      store.put(marker, utf8.encode('stale'));
      store.put('${downloader.filePath('/models', _pack, _file)}.part', [1]);
      store.failingPath = marker;

      await expectLater(
        downloader.deletePack('/models', _pack),
        throwsA(isA<FileSystemException>()),
      );
      expect(store.files, contains(marker));
      expect(
        store.files,
        isNot(contains('${downloader.filePath('/models', _pack, _file)}.part')),
      );

      await downloader
          .download(
            '/models',
            _pack,
            cancellation: VoiceCancellationToken(),
            onProgress: (_) {},
            onVerifying: () {},
          )
          .timeout(const Duration(seconds: 1));
      expect(await downloader.verifyInstalled('/models', _pack), isTrue);
    },
  );

  test(
    'response body sink and progress callback failures complete the download',
    () async {
      final sinkDownloader = VoiceModelDownloader(
        store: _ThrowingSinkStore(),
        http: _FakeHttp((_) => _response(utf8.encode('hello'))),
      );
      await expectLater(
        sinkDownloader
            .download(
              '/models',
              _pack,
              cancellation: VoiceCancellationToken(),
              onProgress: (_) {},
              onVerifying: () {},
            )
            .timeout(const Duration(seconds: 1)),
        throwsA(isA<StateError>()),
      );

      final callbackDownloader = VoiceModelDownloader(
        store: _MemoryStore(),
        http: _FakeHttp((_) => _response(utf8.encode('hello'))),
      );
      await expectLater(
        callbackDownloader
            .download(
              '/models',
              _pack,
              cancellation: VoiceCancellationToken(),
              onProgress: (_) => throw StateError('simulated callback failure'),
              onVerifying: () {},
            )
            .timeout(const Duration(seconds: 1)),
        throwsA(isA<StateError>()),
      );
    },
  );

  test('cancellation during finalization cleans a truncated partial', () async {
    final cancellation = VoiceCancellationToken();
    final store = _CancellingOnCloseStore(cancellation);
    final downloader = VoiceModelDownloader(
      store: store,
      http: _FakeHttp(
        (_) => VoiceHttpResponse(
          statusCode: 200,
          contentLength: 5,
          headers: const {},
          body: Stream.value(utf8.encode('hel')),
        ),
      ),
    );

    await expectLater(
      downloader.download(
        '/models',
        _pack,
        cancellation: cancellation,
        onProgress: (_) {},
        onVerifying: () {},
      ),
      throwsA(isA<VoiceDownloadCancelled>()),
    );
    expect(store.files, isEmpty);
  });
}
