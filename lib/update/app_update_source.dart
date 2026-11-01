import 'dart:async';
import 'dart:io';

import 'package:dio/dio.dart';

import 'app_release.dart';

const appUpdateLatestUrl =
    'https://api.github.com/repos/Eslamasabry/opencode-mobile-next/releases/latest';

/// Proof override: a feed URL (the same JSON as one GitHub release) that
/// replaces the GitHub address. Empty unless a proof build is made with
/// `--dart-define=OC_APP_UPDATE_FEED=<url>`; no release script passes it
/// (test/app_update_controller_test.dart pins both).
const appUpdateFeedOverride = String.fromEnvironment('OC_APP_UPDATE_FEED');

/// Lets the person stop a download that is running.
class DownloadCancel {
  final _completer = Completer<void>();
  bool get isCancelled => _completer.isCompleted;
  Future<void> get whenCancelled => _completer.future;
  void cancel() {
    if (!_completer.isCompleted) _completer.complete();
  }
}

class DownloadCancelled implements Exception {
  const DownloadCancelled();
}

abstract interface class AppUpdateSource {
  /// The newest published release, or null when there is none.
  Future<AppRelease?> fetchLatest();

  /// A small text file of a release (`SHA256SUMS`).
  Future<String> fetchText(String url);

  /// Fetches [url] into [dest], resuming a partial file from where it
  /// stopped. Throws [DownloadCancelled] when [cancel] fires.
  Future<void> download(
    String url,
    File dest, {
    required void Function(int received, int total) onProgress,
    required DownloadCancel cancel,
  });
}

/// A file URL this app will download from: GitHub's release downloads for
/// this project, or (proof builds only) the feed's own origin.
bool isTrustedReleaseFileUrl(String url, {String feedOverride = ''}) {
  final uri = Uri.tryParse(url);
  if (uri == null || uri.host.isEmpty || uri.userInfo.isNotEmpty) return false;
  if (feedOverride.isNotEmpty) {
    final feed = Uri.tryParse(feedOverride);
    return feed != null && feed.host == uri.host && feed.port == uri.port;
  }
  return uri.scheme == 'https' &&
      uri.host == 'github.com' &&
      uri.path.startsWith('/Eslamasabry/opencode-mobile-next/releases/');
}

class DioAppUpdateSource implements AppUpdateSource {
  DioAppUpdateSource({Dio? dio, this.feedOverride = appUpdateFeedOverride})
    : _dio = dio ?? Dio();

  final Dio _dio;
  final String feedOverride;

  @override
  Future<AppRelease?> fetchLatest() async {
    final response = await _dio.get<dynamic>(
      feedOverride.isEmpty ? appUpdateLatestUrl : feedOverride,
      options: Options(
        connectTimeout: const Duration(seconds: 10),
        receiveTimeout: const Duration(seconds: 10),
        headers: const {'Accept': 'application/vnd.github+json'},
        responseType: ResponseType.json,
      ),
    );
    final data = response.data;
    return data is Map ? AppRelease.fromJson(data) : null;
  }

  void _requireTrusted(String url) {
    if (!isTrustedReleaseFileUrl(url, feedOverride: feedOverride)) {
      throw const FormatException('untrusted release file address');
    }
  }

  @override
  Future<String> fetchText(String url) async {
    _requireTrusted(url);
    final response = await _dio.get<String>(
      url,
      options: Options(
        connectTimeout: const Duration(seconds: 10),
        receiveTimeout: const Duration(seconds: 20),
        responseType: ResponseType.plain,
      ),
    );
    return response.data ?? '';
  }

  @override
  Future<void> download(
    String url,
    File dest, {
    required void Function(int received, int total) onProgress,
    required DownloadCancel cancel,
  }) async {
    _requireTrusted(url);
    await dest.parent.create(recursive: true);
    final part = File('${dest.path}.part');
    var have = await part.exists() ? await part.length() : 0;
    final token = CancelToken();
    unawaited(cancel.whenCancelled.then((_) => token.cancel('cancelled')));
    try {
      final response = await _dio.get<ResponseBody>(
        url,
        cancelToken: token,
        options: Options(
          responseType: ResponseType.stream,
          connectTimeout: const Duration(seconds: 15),
          receiveTimeout: const Duration(seconds: 30),
          headers: {if (have > 0) 'Range': 'bytes=$have-'},
          validateStatus: (s) => s == 200 || s == 206 || s == 416,
        ),
      );
      if (response.statusCode == 416) {
        // The partial file is already all of it, or stale: start over.
        await part.delete();
        await download(url, dest, onProgress: onProgress, cancel: cancel);
        return;
      }
      final resumed = response.statusCode == 206;
      if (!resumed) have = 0;
      final length =
          int.tryParse(response.headers.value('content-length') ?? '') ?? -1;
      final total = length < 0 ? -1 : length + have;
      final sink = part.openWrite(
        mode: resumed ? FileMode.append : FileMode.write,
      );
      var received = have;
      try {
        await for (final chunk in response.data!.stream) {
          sink.add(chunk);
          received += chunk.length;
          onProgress(received, total);
        }
        await sink.flush();
      } finally {
        await sink.close();
      }
      if (await dest.exists()) await dest.delete();
      await part.rename(dest.path);
    } on DioException catch (e) {
      if (CancelToken.isCancel(e)) throw const DownloadCancelled();
      rethrow;
    }
  }
}
