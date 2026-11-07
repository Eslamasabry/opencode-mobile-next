import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/platform/app_installer.dart';
import 'package:opencode_mobile/update/desktop_release_check.dart';
import 'package:opencode_mobile/update/in_app_update_service.dart';

class _RecordingAdapter implements HttpClientAdapter {
  _RecordingAdapter(this.responses);

  final Map<String, String> responses;
  final List<String> requestedUrls = [];

  @override
  void close({bool force = false}) {}

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    requestedUrls.add(options.path);
    final payload = responses[options.path] ?? '[]';
    return ResponseBody.fromString(
      payload,
      200,
      headers: {
        Headers.contentTypeHeader: ['application/json'],
      },
    );
  }
}

void main() {
  group('InAppUpdateService', () {
    test('extracts build number and determines newer releases', () {
      expect(buildNumberFromTag('v2.1.4+62'), 62);
      expect(buildNumberFromTag('v2.1.5+63-preview.1'), 63);
      expect(buildNumberFromTag('invalid-tag'), isNull);

      expect(isNewerRelease(currentBuildNumber: 61, tag: 'v2.1.4+62'), isTrue);
      expect(isNewerRelease(currentBuildNumber: 62, tag: 'v2.1.4+62'), isFalse);
      expect(isNewerRelease(currentBuildNumber: 63, tag: 'v2.1.4+62'), isFalse);
    });

    test('formats release size correctly', () {
      const release = AppReleaseInfo(
        tag: 'v2.1.4+62',
        name: 'OpenCode Mobile 2.1.4+62',
        changelog: 'Test changelog',
        apkUrl: 'https://example.com/test.apk',
        apkName: 'test.apk',
        apkSize: 42 * 1024 * 1024,
        htmlUrl: 'https://example.com/release',
        buildNumber: 62,
      );
      expect(release.formattedSize, '42.0 MB');
    });

    test('picks best APK matching device ABI', () async {
      AppInstaller.getSupportedAbisOverride = () async => ['arm64-v8a'];

      final releasesPayload = jsonEncode([
        {
          'tag_name': 'v2.1.4+65',
          'name': 'OpenCode 2.1.4+65',
          'body': 'Notes for 65',
          'draft': false,
          'html_url': 'https://github.com/test/release/65',
          'assets': [
            {
              'name': 'opencode-mobile-2.1.4+65-armeabi-v7a.apk',
              'size': 35000000,
              'browser_download_url': 'https://download/armeabi.apk',
            },
            {
              'name': 'opencode-mobile-2.1.4+65-arm64-v8a.apk',
              'size': 40000000,
              'browser_download_url': 'https://download/arm64.apk',
            },
            {
              'name': 'opencode-mobile-2.1.4+65.apk',
              'size': 100000000,
              'browser_download_url': 'https://download/universal.apk',
            },
          ],
        },
      ]);

      final dio = Dio();
      dio.httpClientAdapter = _RecordingAdapter({
        'https://primary.releases': releasesPayload,
      });

      final service = InAppUpdateService(
        dio: dio,
        primaryReleasesUrl: 'https://primary.releases',
        fallbackReleasesUrl: 'https://fallback.releases',
      );

      final result = await service.checkForNewRelease(
        ignoreCache: true,
        currentBuildNumberLoader: () async => 62,
      );

      expect(result, isNotNull);
      expect(result!.tag, 'v2.1.4+65');
      expect(result.apkName, 'opencode-mobile-2.1.4+65-arm64-v8a.apk');
      expect(result.apkUrl, 'https://download/arm64.apk');

      AppInstaller.getSupportedAbisOverride = null;
    });

    test('falls back to secondary URL when primary is empty', () async {
      AppInstaller.getSupportedAbisOverride = () async => ['arm64-v8a'];

      final fallbackPayload = jsonEncode([
        {
          'tag_name': 'v2.1.4+63',
          'name': 'OpenCode 2.1.4+63',
          'body': 'Notes from fallback',
          'draft': false,
          'assets': [
            {
              'name': 'opencode-mobile-2.1.4+63.apk',
              'size': 90000000,
              'browser_download_url': 'https://fallback/universal.apk',
            },
          ],
        },
      ]);

      final dio = Dio();
      dio.httpClientAdapter = _RecordingAdapter({
        'https://primary.releases': '[]',
        'https://fallback.releases': fallbackPayload,
      });

      final service = InAppUpdateService(
        dio: dio,
        primaryReleasesUrl: 'https://primary.releases',
        fallbackReleasesUrl: 'https://fallback.releases',
      );

      final result = await service.checkForNewRelease(
        ignoreCache: true,
        currentBuildNumberLoader: () async => 62,
      );

      expect(result, isNotNull);
      expect(result!.tag, 'v2.1.4+63');
      expect(result.apkName, 'opencode-mobile-2.1.4+63.apk');

      AppInstaller.getSupportedAbisOverride = null;
    });

    test('session dismissal tracks dismissed tags', () {
      final service = InAppUpdateService();
      expect(service.isDismissedForSession('v2.1.4+62'), isFalse);
      service.dismissForSession('v2.1.4+62');
      expect(service.isDismissedForSession('v2.1.4+62'), isTrue);
      expect(service.isDismissedForSession('v2.1.4+63'), isFalse);
    });
  });
}
