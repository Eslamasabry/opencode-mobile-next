import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:crypto/crypto.dart';
import 'package:opencode_mobile/update/app_release.dart';
import 'package:opencode_mobile/update/app_update_platform.dart';
import 'package:opencode_mobile/update/app_update_source.dart';

const kUpdateOwnSigner = 'AA11';
final kUpdateApkBytes = utf8.encode('the new app');
final kUpdateApkSha = sha256.convert(kUpdateApkBytes).toString();

Map<String, dynamic> updateReleaseJson({int build = 60, String notes = ''}) => {
  'tag_name': 'v2.2.0+$build',
  'html_url':
      'https://github.com/Eslamasabry/opencode-mobile-next/releases/tag/v2.2.0%2B$build',
  'draft': false,
  'prerelease': false,
  'body': notes.isEmpty
      ? '# OpenCode Mobile 2.2.0\n\nThis release is faster. More below.\n\n- one'
      : notes,
  'assets': [
    for (final n in [
      'opencode-mobile-2.2.0+$build.apk',
      'opencode-mobile-2.2.0+$build-arm64-v8a.apk',
      'opencode-mobile-2.2.0+$build-armeabi-v7a.apk',
      'SHA256SUMS',
    ])
      {
        'name': n,
        'browser_download_url':
            'https://github.com/Eslamasabry/opencode-mobile-next/releases/download/v2.2.0%2B$build/$n',
        'size': n == 'SHA256SUMS' ? 300 : 40000000,
      },
  ],
};

class FakeUpdateSource implements AppUpdateSource {
  AppRelease? latest;
  Object? fetchError;
  int fetches = 0;
  int downloads = 0;
  String? downloadedUrl;
  List<int> bytes = kUpdateApkBytes;
  String sumsHash = kUpdateApkSha;
  Completer<void>? hold;
  Object? downloadError;

  @override
  Future<AppRelease?> fetchLatest() async {
    fetches++;
    if (fetchError != null) throw fetchError!;
    return latest;
  }

  @override
  Future<String> fetchText(String url) async => [
    '$sumsHash  opencode-mobile-2.2.0+60-arm64-v8a.apk',
    '$sumsHash  opencode-mobile-2.2.0+60-armeabi-v7a.apk',
    '$sumsHash  opencode-mobile-2.2.0+60.apk',
  ].join('\n');

  @override
  Future<void> download(
    String url,
    File dest, {
    required void Function(int received, int total) onProgress,
    required DownloadCancel cancel,
  }) async {
    downloads++;
    if (downloadError != null) throw downloadError!;
    downloadedUrl = url;
    await dest.parent.create(recursive: true);
    onProgress(5, 10);
    if (hold != null) {
      await Future.any([hold!.future, cancel.whenCancelled]);
      if (cancel.isCancelled) throw const DownloadCancelled();
    }
    await dest.writeAsBytes(bytes);
    onProgress(10, 10);
  }
}

class FakeUpdatePlatform implements AppUpdatePlatform {
  bool canInstallNow = true;
  String signer = kUpdateOwnSigner;
  String package = 'io.github.eslamasabry.opencode_mobile';
  int versionCode = 60;
  List<String> abis = ['arm64-v8a', 'armeabi-v7a'];
  final installs = <String>[];
  int permissionOpened = 0;
  List<AppInstallEvent> events = [
    const AppInstallEvent(AppInstallState.pendingUser),
  ];

  @override
  Future<bool> canInstall() async => canInstallNow;
  @override
  Future<void> openInstallPermission() async => permissionOpened++;
  @override
  Future<ApkInfo> inspectApk(String path) async => ApkInfo(
    packageName: package,
    versionCode: versionCode,
    signerSha256: signer,
  );
  @override
  Future<String> ownSignerSha256() async => kUpdateOwnSigner;
  @override
  Future<List<String>> supportedAbis() async => abis;
  @override
  Stream<AppInstallEvent> install(String path) {
    installs.add(path);
    return Stream.fromIterable(events);
  }
}
