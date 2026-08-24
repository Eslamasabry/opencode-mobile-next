import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:isolate';
import 'dart:typed_data';

import 'package:crypto/crypto.dart';

import 'model_manifest.dart';

class VoiceDownloadCancelled implements Exception {
  const VoiceDownloadCancelled();
}

class VoiceDownloadException implements Exception {
  const VoiceDownloadException(this.message);

  final String message;

  @override
  String toString() => message;
}

class VoiceCancellationToken {
  bool _cancelled = false;
  final Completer<void> _cancelledCompleter = Completer<void>();

  bool get isCancelled => _cancelled;
  Future<void> get whenCancelled => _cancelledCompleter.future;

  void cancel() {
    if (_cancelled) return;
    _cancelled = true;
    _cancelledCompleter.complete();
  }

  void throwIfCancelled() {
    if (_cancelled) throw const VoiceDownloadCancelled();
  }
}

class VoiceDownloadProgress {
  const VoiceDownloadProgress({
    required this.received,
    required this.total,
    required this.fileName,
  });

  final int received;
  final int total;
  final String fileName;

  double get fraction => total == 0 ? 0 : (received / total).clamp(0, 1);
}

abstract interface class VoiceByteSink {
  void add(List<int> bytes);
  Future<void> close();
}

abstract interface class VoiceFileStore {
  Future<void> createDirectory(String path);
  Future<bool> exists(String path);
  Future<int> length(String path);
  Stream<List<int>> read(String path);
  Future<Uint8List> readBytes(String path);
  Future<VoiceByteSink> openWrite(String path, {required bool append});
  Future<void> delete(String path);
  Future<void> move(String from, String to);
  Future<void> writeAtomic(String path, List<int> bytes);
}

class LocalVoiceFileStore implements VoiceFileStore {
  const LocalVoiceFileStore();

  @override
  Future<void> createDirectory(String path) =>
      Directory(path).create(recursive: true);

  @override
  Future<void> delete(String path) async {
    final file = File(path);
    if (await file.exists()) await file.delete();
  }

  @override
  Future<bool> exists(String path) => File(path).exists();

  @override
  Future<int> length(String path) => File(path).length();

  @override
  Future<void> move(String from, String to) async {
    await delete(to);
    await File(from).rename(to);
  }

  @override
  Future<VoiceByteSink> openWrite(String path, {required bool append}) async =>
      _LocalVoiceByteSink(
        File(path).openWrite(mode: append ? FileMode.append : FileMode.write),
      );

  @override
  Stream<List<int>> read(String path) => File(path).openRead();

  @override
  Future<Uint8List> readBytes(String path) => File(path).readAsBytes();

  @override
  Future<void> writeAtomic(String path, List<int> bytes) async {
    final temporary = '$path.tmp';
    final file = await File(temporary).open(mode: FileMode.write);
    try {
      await file.writeFrom(bytes);
      await file.flush();
    } finally {
      await file.close();
    }
    await move(temporary, path);
  }
}

class _LocalVoiceByteSink implements VoiceByteSink {
  _LocalVoiceByteSink(this.sink);

  final IOSink sink;

  @override
  void add(List<int> bytes) => sink.add(bytes);

  @override
  Future<void> close() => sink.close();
}

class VoiceHttpResponse {
  const VoiceHttpResponse({
    required this.statusCode,
    required this.contentLength,
    required this.headers,
    required this.body,
    this.abort,
  });

  final int statusCode;
  final int contentLength;
  final Map<String, String> headers;
  final Stream<List<int>> body;
  final void Function()? abort;
}

abstract interface class VoiceHttpTransport {
  Future<VoiceHttpResponse> get(
    Uri uri, {
    Map<String, String> headers = const {},
    VoiceCancellationToken? cancellation,
  });
  void close();
}

class LocalVoiceHttpTransport implements VoiceHttpTransport {
  LocalVoiceHttpTransport({this.connectTimeout = const Duration(seconds: 15)});

  final Duration connectTimeout;
  final Set<HttpClient> _clients = {};
  bool _closed = false;

  @override
  void close() {
    _closed = true;
    for (final client in _clients.toList()) {
      client.close(force: true);
    }
    _clients.clear();
  }

  @override
  Future<VoiceHttpResponse> get(
    Uri uri, {
    Map<String, String> headers = const {},
    VoiceCancellationToken? cancellation,
  }) async {
    if (uri.scheme != 'https') {
      throw const VoiceDownloadException(
        'Voice models may only be downloaded over HTTPS.',
      );
    }
    if (_closed) {
      throw const VoiceDownloadException('Voice download transport is closed.');
    }
    final token = cancellation ?? VoiceCancellationToken();
    token.throwIfCancelled();
    final client = HttpClient()
      ..userAgent = 'OpenCode-Mobile/voice-model'
      ..connectionTimeout = connectTimeout
      ..idleTimeout = const Duration(seconds: 20);
    _clients.add(client);
    void abort() {
      client.close(force: true);
      _clients.remove(client);
    }

    try {
      var current = uri;
      HttpClientResponse? response;
      for (var redirects = 0; redirects <= 5; redirects++) {
        final request = await _networkStep(
          client.getUrl(current),
          token,
          abort,
          'connecting to the model server',
        );
        request.followRedirects = false;
        headers.forEach(request.headers.set);
        final currentResponse = await _networkStep(
          request.close(),
          token,
          abort,
          'waiting for the model server',
        );
        response = currentResponse;
        if (!currentResponse.isRedirect) break;
        final location = currentResponse.headers.value(
          HttpHeaders.locationHeader,
        );
        if (location == null || redirects == 5) {
          throw const VoiceDownloadException(
            'Voice model download returned an invalid redirect.',
          );
        }
        final redirected = current.resolve(location);
        if (redirected.scheme != 'https') {
          throw const VoiceDownloadException(
            'Voice model download redirected to a non-HTTPS URL.',
          );
        }
        await _networkStep(
          currentResponse.drain<void>(),
          token,
          abort,
          'following the model redirect',
        );
        current = redirected;
      }
      if (response == null) {
        throw const VoiceDownloadException(
          'Model server returned no response.',
        );
      }
      final responseHeaders = <String, String>{};
      response.headers.forEach((name, values) {
        responseHeaders[name.toLowerCase()] = values.join(',');
      });
      return VoiceHttpResponse(
        statusCode: response.statusCode,
        contentLength: response.contentLength,
        headers: responseHeaders,
        body: response,
        abort: abort,
      );
    } catch (_) {
      abort();
      rethrow;
    }
  }

  Future<T> _networkStep<T>(
    Future<T> operation,
    VoiceCancellationToken cancellation,
    void Function() abort,
    String description,
  ) async {
    cancellation.throwIfCancelled();
    final result = Completer<T>();
    var active = true;
    final timer = Timer(connectTimeout, () {
      if (!active) return;
      abort();
      result.completeError(
        VoiceDownloadException('Timed out while $description.'),
      );
    });
    operation.then(
      (value) {
        if (active && !result.isCompleted) result.complete(value);
      },
      onError: (Object error, StackTrace stackTrace) {
        if (active && !result.isCompleted) {
          result.completeError(error, stackTrace);
        }
      },
    );
    unawaited(
      cancellation.whenCancelled.then((_) {
        if (!active || result.isCompleted) return;
        abort();
        result.completeError(const VoiceDownloadCancelled());
      }),
    );
    try {
      return await result.future;
    } finally {
      active = false;
      timer.cancel();
    }
  }
}

class VoiceModelDownloader {
  VoiceModelDownloader({
    required this.store,
    required this.http,
    this.bodyIdleTimeout = const Duration(seconds: 20),
  });

  static const markerName = '.installed.json';

  final VoiceFileStore store;
  final VoiceHttpTransport http;
  final Duration bodyIdleTimeout;
  static final Map<String, Future<void>> _installLocks = {};

  String packDirectory(String root, VoiceModelPack pack) =>
      '$root/${pack.id}-${pack.revision}';

  String filePath(String root, VoiceModelPack pack, VoiceModelFile file) =>
      '${packDirectory(root, pack)}/${file.name}';

  String markerPath(String root, VoiceModelPack pack) =>
      '${packDirectory(root, pack)}/$markerName';

  Future<bool> verifyInstalled(
    String root,
    VoiceModelPack pack, {
    VoiceCancellationToken? cancellation,
  }) async {
    cancellation?.throwIfCancelled();
    final marker = markerPath(root, pack);
    if (!await store.exists(marker)) return false;
    try {
      final data = jsonDecode(utf8.decode(await store.readBytes(marker)));
      if (data is! Map<String, dynamic> ||
          data['pack'] != pack.id ||
          data['revision'] != pack.revision) {
        await store.delete(marker);
        return false;
      }
      for (final file in pack.files) {
        if (!await _verifyFile(
          filePath(root, pack, file),
          file,
          cancellation: cancellation,
        )) {
          await store.delete(marker);
          return false;
        }
      }
      return true;
    } on VoiceDownloadCancelled {
      rethrow;
    } catch (_) {
      await store.delete(marker);
      return false;
    }
  }

  Future<void> download(
    String root,
    VoiceModelPack pack, {
    required VoiceCancellationToken cancellation,
    required void Function(VoiceDownloadProgress progress) onProgress,
    required void Function() onVerifying,
    bool replaceExisting = false,
  }) => _withInstallLock(packDirectory(root, pack), () async {
    if (!replaceExisting &&
        await verifyInstalled(root, pack, cancellation: cancellation)) {
      return;
    }
    final directory = packDirectory(root, pack);
    final stagingDirectory = '$directory.installing';
    await store.createDirectory(stagingDirectory);
    var completed = 0;
    try {
      for (final file in pack.files) {
        cancellation.throwIfCancelled();
        final stagedPath = '$stagingDirectory/${file.name}';
        if (await _verifyFile(stagedPath, file, cancellation: cancellation)) {
          completed += file.length;
          onProgress(
            VoiceDownloadProgress(
              received: completed,
              total: pack.downloadBytes,
              fileName: file.name,
            ),
          );
          continue;
        }
        await store.delete(stagedPath);
        await _downloadFile(
          pack.urlFor(file),
          '$stagedPath.part',
          file,
          cancellation,
          (received) => onProgress(
            VoiceDownloadProgress(
              received: completed + received,
              total: pack.downloadBytes,
              fileName: file.name,
            ),
          ),
        );
        onVerifying();
        cancellation.throwIfCancelled();
        if (!await _verifyFile(
          '$stagedPath.part',
          file,
          cancellation: cancellation,
        )) {
          await store.delete('$stagedPath.part');
          throw VoiceDownloadException(
            'Checksum validation failed for ${file.name}.',
          );
        }
        await store.move('$stagedPath.part', stagedPath);
        completed += file.length;
      }

      onVerifying();
      for (final file in pack.files) {
        cancellation.throwIfCancelled();
        if (!await _verifyFile(
          '$stagingDirectory/${file.name}',
          file,
          cancellation: cancellation,
        )) {
          throw VoiceDownloadException(
            'Final verification failed for ${file.name}.',
          );
        }
      }
      final marker = utf8.encode(
        jsonEncode({
          'pack': pack.id,
          'revision': pack.revision,
          'files': [
            for (final file in pack.files)
              {'name': file.name, 'length': file.length, 'sha256': file.sha256},
          ],
        }),
      );
      cancellation.throwIfCancelled();
      await store.writeAtomic('$stagingDirectory/$markerName', marker);
      cancellation.throwIfCancelled();
      await store.createDirectory(directory);
      final installedPaths = <String>[];
      final backups = <String, String>{};
      final publications = <(String, String)>[
        for (final file in pack.files)
          ('$stagingDirectory/${file.name}', '$directory/${file.name}'),
        ('$stagingDirectory/$markerName', markerPath(root, pack)),
      ];
      try {
        for (final (staged, target) in publications) {
          cancellation.throwIfCancelled();
          if (await store.exists(target)) {
            final backup = '$target.voice-backup';
            await store.delete(backup);
            await store.move(target, backup);
            backups[target] = backup;
          }
          await store.move(staged, target);
          installedPaths.add(target);
        }
      } catch (_) {
        for (final path in installedPaths.reversed) {
          await store.delete(path);
        }
        for (final entry in backups.entries) {
          if (await store.exists(entry.value)) {
            await store.move(entry.value, entry.key);
          }
        }
        rethrow;
      }
      for (final backup in backups.values) {
        await store.delete(backup);
      }
    } on VoiceDownloadCancelled {
      for (final file in pack.files) {
        await store.delete('$stagingDirectory/${file.name}');
        await store.delete('$stagingDirectory/${file.name}.part');
      }
      await store.delete('$stagingDirectory/$markerName');
      rethrow;
    }
  });

  Future<void> _downloadFile(
    Uri uri,
    String temporaryPath,
    VoiceModelFile file,
    VoiceCancellationToken cancellation,
    void Function(int received) onProgress,
  ) async {
    var offset = await store.exists(temporaryPath)
        ? await store.length(temporaryPath)
        : 0;
    if (offset < 0 || offset >= file.length) {
      await store.delete(temporaryPath);
      offset = 0;
    }
    var response = await http.get(
      uri,
      headers: offset > 0 ? {'Range': 'bytes=$offset-'} : const {},
      cancellation: cancellation,
    );
    var append = false;
    final expectedContentRange =
        'bytes $offset-${file.length - 1}/${file.length}';
    if (offset > 0 &&
        response.statusCode == HttpStatus.partialContent &&
        response.headers['content-range'] == expectedContentRange) {
      append = true;
    } else if (response.statusCode == HttpStatus.ok) {
      offset = 0;
      await store.delete(temporaryPath);
    } else {
      response.abort?.call();
      throw VoiceDownloadException(
        'Model server returned HTTP ${response.statusCode}.',
      );
    }
    final expectedResponseLength = file.length - offset;
    if (response.contentLength != -1 &&
        response.contentLength != expectedResponseLength) {
      response.abort?.call();
      throw VoiceDownloadException(
        'Unexpected length for ${file.name}: ${response.contentLength} bytes.',
      );
    }
    late final VoiceByteSink sink;
    try {
      cancellation.throwIfCancelled();
      sink = await store.openWrite(temporaryPath, append: append);
    } catch (_) {
      response.abort?.call();
      rethrow;
    }
    var received = offset;
    StreamSubscription<List<int>>? subscription;
    Timer? idleTimer;
    final bodyDone = Completer<void>();
    void fail(Object error, [StackTrace? stackTrace]) {
      if (!bodyDone.isCompleted) bodyDone.completeError(error, stackTrace);
    }

    void resetIdleTimer() {
      idleTimer?.cancel();
      idleTimer = Timer(bodyIdleTimeout, () {
        response.abort?.call();
        fail(
          VoiceDownloadException(
            'Timed out waiting for ${file.name} download data.',
          ),
        );
      });
    }

    try {
      subscription = response.body.listen(
        (chunk) {
          if (bodyDone.isCompleted) return;
          resetIdleTimer();
          if (received + chunk.length > file.length) {
            response.abort?.call();
            fail(
              VoiceDownloadException('${file.name} exceeded its pinned size.'),
            );
            return;
          }
          sink.add(chunk);
          received += chunk.length;
          onProgress(received);
        },
        onError: fail,
        onDone: () {
          if (!bodyDone.isCompleted) bodyDone.complete();
        },
      );
      resetIdleTimer();
      unawaited(
        cancellation.whenCancelled.then((_) {
          if (bodyDone.isCompleted) return;
          response.abort?.call();
          unawaited(subscription?.cancel());
          fail(const VoiceDownloadCancelled());
        }),
      );
      await bodyDone.future;
    } finally {
      idleTimer?.cancel();
      unawaited(subscription?.cancel());
      await sink.close();
      response.abort?.call();
    }
    if (received != file.length) {
      throw VoiceDownloadException(
        'Incomplete ${file.name}: received $received of ${file.length} bytes.',
      );
    }
  }

  Future<bool> _verifyFile(
    String path,
    VoiceModelFile file, {
    VoiceCancellationToken? cancellation,
  }) async {
    cancellation?.throwIfCancelled();
    if (!await store.exists(path) || await store.length(path) != file.length) {
      return false;
    }
    cancellation?.throwIfCancelled();
    final digest = store is LocalVoiceFileStore
        ? await _sha256LocalFile(path, cancellation)
        : (await sha256.bind(store.read(path)).first).toString();
    cancellation?.throwIfCancelled();
    return digest == file.sha256;
  }

  Future<String> _sha256LocalFile(
    String path,
    VoiceCancellationToken? cancellation,
  ) async {
    if (cancellation == null) return Isolate.run(() => _sha256File(path));
    cancellation.throwIfCancelled();
    final messages = ReceivePort();
    final exits = ReceivePort();
    final result = Completer<String>();
    Isolate? isolate;
    late final StreamSubscription<dynamic> messageSubscription;
    late final StreamSubscription<dynamic> exitSubscription;
    messageSubscription = messages.listen((message) {
      if (message is String && !result.isCompleted) {
        result.complete(message);
      } else if (message is List &&
          message.length == 2 &&
          !result.isCompleted) {
        result.completeError(
          StateError(message.first.toString()),
          StackTrace.fromString(message.last.toString()),
        );
      }
    });
    exitSubscription = exits.listen((_) {
      if (!result.isCompleted) {
        result.completeError(
          StateError('Model hash worker exited unexpectedly.'),
        );
      }
    });
    try {
      isolate = await Isolate.spawn<(SendPort, String)>(_sha256Worker, (
        messages.sendPort,
        path,
      ), onExit: exits.sendPort);
      unawaited(
        cancellation.whenCancelled.then((_) {
          if (result.isCompleted) return;
          isolate?.kill(priority: Isolate.immediate);
          result.completeError(const VoiceDownloadCancelled());
        }),
      );
      return await result.future;
    } finally {
      isolate?.kill(priority: Isolate.immediate);
      await messageSubscription.cancel();
      await exitSubscription.cancel();
      messages.close();
      exits.close();
    }
  }

  Future<T> _withInstallLock<T>(String key, Future<T> Function() action) async {
    final previous = _installLocks[key] ?? Future.value();
    final release = Completer<void>();
    final current = release.future;
    _installLocks[key] = current;
    await previous.catchError((_) {});
    try {
      return await action();
    } finally {
      release.complete();
      if (identical(_installLocks[key], current)) _installLocks.remove(key);
    }
  }

  Future<void> deletePack(String root, VoiceModelPack pack) async {
    await store.delete(markerPath(root, pack));
    for (final file in pack.files) {
      final path = filePath(root, pack, file);
      await store.delete(path);
      await store.delete('$path.part');
    }
  }
}

Future<String> _sha256File(String path) async =>
    (await sha256.bind(File(path).openRead()).first).toString();

Future<void> _sha256Worker((SendPort, String) message) async {
  try {
    message.$1.send(await _sha256File(message.$2));
  } catch (error, stackTrace) {
    message.$1.send([error.toString(), stackTrace.toString()]);
  }
}
