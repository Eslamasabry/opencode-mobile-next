import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:crypto/crypto.dart';
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
  test('the APK is hashed in chunks, with the same result as whole', () async {
    final dir = await Directory.systemTemp.createTemp('apk');
    addTearDown(() => dir.delete(recursive: true));
    final file = File('${dir.path}/update.apk');
    final bytes = Uint8List.fromList(
      List<int>.generate(3 * 1024 * 1024 + 17, (i) => (i * 31) & 0xff),
    );
    await file.writeAsBytes(bytes);
    expect(await fileSha256(file), sha256.convert(bytes).toString());
  });

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
              'browser_download_url':
                  'https://github.com/Eslamasabry/opencode-mobile-next/releases/download/v2.1.4+65/armeabi.apk',
            },
            {
              'name': 'opencode-mobile-2.1.4+65-arm64-v8a.apk',
              'size': 40000000,
              'browser_download_url':
                  'https://github.com/Eslamasabry/opencode-mobile-next/releases/download/v2.1.4+65/arm64.apk',
            },
            {
              'name': 'opencode-mobile-2.1.4+65.apk',
              'size': 100000000,
              'browser_download_url':
                  'https://github.com/Eslamasabry/opencode-mobile-next/releases/download/v2.1.4+65/universal.apk',
            },
            {
              'name': 'SHA256SUMS',
              'size': 300,
              'browser_download_url':
                  'https://github.com/Eslamasabry/opencode-mobile-next/releases/download/v2.1.4+65/SHA256SUMS',
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
      );

      final result = await service.checkForNewRelease(
        ignoreCache: true,
        currentBuildNumberLoader: () async => 62,
      );

      expect(result, isNotNull);
      expect(result!.tag, 'v2.1.4+65');
      expect(result.apkName, 'opencode-mobile-2.1.4+65-arm64-v8a.apk');
      expect(
        result.apkUrl,
        'https://github.com/Eslamasabry/opencode-mobile-next/releases/download/v2.1.4+65/arm64.apk',
      );

      AppInstaller.getSupportedAbisOverride = null;
    });

    test(
      'only this repository, promoted releases with checksums are offered',
      () async {
        AppInstaller.getSupportedAbisOverride = () async => ['arm64-v8a'];
        Map<String, Object> release(
          String tag, {
          bool pre = false,
          bool sums = true,
          String host = 'github.com',
        }) => {
          'tag_name': tag,
          'name': tag,
          'body': '',
          'draft': false,
          'prerelease': pre,
          'html_url': 'https://github.com/x',
          'assets': [
            {
              'name': 'opencode-mobile-arm64-v8a.apk',
              'size': 1,
              'browser_download_url':
                  'https://$host/Eslamasabry/opencode-mobile-next/releases/download/$tag/a.apk',
            },
            if (sums)
              {
                'name': 'SHA256SUMS',
                'size': 1,
                'browser_download_url':
                    'https://$host/Eslamasabry/opencode-mobile-next/releases/download/$tag/SHA256SUMS',
              },
          ],
        };
        for (final (payload, offered) in [
          ([release('v2.1.5+66', pre: true)], false),
          ([release('v2.1.5+66', sums: false)], false),
          ([release('v2.1.5+66', host: 'evil.example')], false),
          ([release('v2.1.5+66')], true),
        ]) {
          final dio = Dio()
            ..httpClientAdapter = _RecordingAdapter({
              'https://primary.releases': jsonEncode(payload),
            });
          final service = InAppUpdateService(
            dio: dio,
            primaryReleasesUrl: 'https://primary.releases',
          );
          final result = await service.checkForNewRelease(
            ignoreCache: true,
            currentBuildNumberLoader: () async => 62,
          );
          expect(result != null, offered, reason: jsonEncode(payload));
        }
        AppInstaller.getSupportedAbisOverride = null;
      },
    );

    test('trusted URLs and checksum lines', () {
      expect(
        isTrustedDownloadUrl(
          'https://github.com/Eslamasabry/opencode-mobile-next/releases/download/v1/a.apk',
        ),
        isTrue,
      );
      expect(
        isTrustedDownloadUrl(
          'https://github.com/AltafYafai/opencode-mobile-next/releases/download/v1/a.apk',
        ),
        isFalse,
      );
      expect(
        isTrustedDownloadUrl(
          'http://github.com/Eslamasabry/opencode-mobile-next/releases/download/v1/a.apk',
        ),
        isFalse,
      );
      final hex = 'a' * 64;
      expect(
        expectedSha256(
          '$hex  app-arm64-v8a.apk\n${'b' * 64}  other.apk',
          'app-arm64-v8a.apk',
        ),
        hex,
      );
      expect(expectedSha256('$hex *app.apk', 'app.apk'), hex);
      expect(expectedSha256('$hex  other.apk', 'app.apk'), isNull);
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
