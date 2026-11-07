import 'dart:async';
import 'dart:io';

import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:path_provider/path_provider.dart';

import '../diagnostics/perf_trace.dart';
import '../platform/app_installer.dart';
import '../platform/platform_capabilities.dart';
import 'desktop_release_check.dart' show buildNumberFromTag, isNewerRelease;
import 'shorebird_update_notice.dart' show AppUpdateService, AppUpdateState;

/// Representation of a validated GitHub release asset.
@immutable
class AppReleaseInfo {
  const AppReleaseInfo({
    required this.tag,
    required this.name,
    required this.changelog,
    required this.apkUrl,
    required this.apkName,
    required this.apkSize,
    required this.htmlUrl,
    required this.buildNumber,
    this.publishedAt,
  });

  final String tag;
  final String name;
  final String changelog;
  final String apkUrl;
  final String apkName;
  final int apkSize;
  final String htmlUrl;
  final int buildNumber;
  final DateTime? publishedAt;

  String get formattedSize {
    if (apkSize <= 0) return '';
    final mb = apkSize / (1024 * 1024);
    return '${mb.toStringAsFixed(1)} MB';
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is AppReleaseInfo &&
          runtimeType == other.runtimeType &&
          tag == other.tag &&
          apkUrl == other.apkUrl &&
          buildNumber == other.buildNumber;

  @override
  int get hashCode => Object.hash(tag, apkUrl, buildNumber);
}

/// Service that checks for GitHub releases, downloads update APKs, and
/// launches direct in-app package installations on Android.
class InAppUpdateService implements AppUpdateService {
  InAppUpdateService({
    Dio? dio,
    String? primaryReleasesUrl,
    String? fallbackReleasesUrl,
    Duration timeout = const Duration(seconds: 15),
  }) : _dio = dio ?? PerfTraceInterceptor.traced(Dio(), 'app-update'),
       _primaryReleasesUrl =
           primaryReleasesUrl ??
           'https://api.github.com/repos/Eslamasabry/opencode-mobile-next/releases',
       _fallbackReleasesUrl =
           fallbackReleasesUrl ??
           'https://api.github.com/repos/AltafYafai/opencode-mobile-next/releases',
       _timeout = timeout;

  static final InAppUpdateService instance = InAppUpdateService();

  final Dio _dio;
  final String _primaryReleasesUrl;
  final String _fallbackReleasesUrl;
  final Duration _timeout;

  final Set<String> _dismissedSessionTags = <String>{};
  AppReleaseInfo? _cachedNewerRelease;
  DateTime? _lastCheckTime;

  bool isDismissedForSession(String tag) => _dismissedSessionTags.contains(tag);

  void dismissForSession(String tag) {
    _dismissedSessionTags.add(tag);
  }

  @override
  bool get isAvailable => platformCapabilities.isAndroid;

  @override
  Future<AppUpdateState> checkForUpdate() async {
    if (!isAvailable) return AppUpdateState.unavailable;
    try {
      final release = await checkForNewRelease();
      if (release != null) {
        return AppUpdateState.available;
      }
      return AppUpdateState.current;
    } catch (_) {
      return AppUpdateState.unavailable;
    }
  }

  @override
  Future<void> downloadUpdate() async {
    final release = _cachedNewerRelease ?? await checkForNewRelease();
    if (release == null) return;
    await downloadAndInstall(release: release);
  }

  /// Checks if a newer release is available than the running build.
  /// If [ignoreCache] is false, throttles requests within 10 minutes.
  Future<AppReleaseInfo?> checkForNewRelease({
    bool ignoreCache = false,
    Future<int?> Function()? currentBuildNumberLoader,
  }) async {
    final now = DateTime.now();
    if (!ignoreCache && _lastCheckTime != null) {
      if (now.difference(_lastCheckTime!) < const Duration(minutes: 10)) {
        return _cachedNewerRelease;
      }
    }

    final currentBuild = await _resolveCurrentBuildNumber(
      currentBuildNumberLoader,
    );
    if (currentBuild == null) return null;

    final deviceAbis = await AppInstaller.getSupportedAbis();

    AppReleaseInfo? release = await _fetchFromUrl(
      _primaryReleasesUrl,
      currentBuild,
      deviceAbis,
    );
    if (release == null && _primaryReleasesUrl != _fallbackReleasesUrl) {
      release = await _fetchFromUrl(
        _fallbackReleasesUrl,
        currentBuild,
        deviceAbis,
      );
    }

    _lastCheckTime = now;
    _cachedNewerRelease = release;
    return release;
  }

  Future<int?> _resolveCurrentBuildNumber(
    Future<int?> Function()? loader,
  ) async {
    if (loader != null) return loader();
    try {
      final packageInfo = await PackageInfo.fromPlatform();
      return int.tryParse(packageInfo.buildNumber);
    } catch (_) {
      return null;
    }
  }

  Future<AppReleaseInfo?> _fetchFromUrl(
    String url,
    int currentBuild,
    List<String> deviceAbis,
  ) async {
    try {
      final cancelToken = CancelToken();
      final response = await _dio
          .get<List<dynamic>>(
            url,
            queryParameters: {'per_page': 10},
            cancelToken: cancelToken,
            options: Options(
              connectTimeout: _timeout,
              receiveTimeout: _timeout,
              sendTimeout: _timeout,
              headers: {'Accept': 'application/vnd.github+json'},
              responseType: ResponseType.json,
            ),
          )
          .timeout(
            _timeout,
            onTimeout: () {
              cancelToken.cancel('Release check timed out');
              throw TimeoutException('Release check timed out', _timeout);
            },
          );

      final data = response.data;
      if (data == null || data.isEmpty) return null;

      AppReleaseInfo? candidate;
      var candidateBuild = currentBuild;

      for (final item in data) {
        if (item is! Map) continue;
        if (item['draft'] == true) continue;
        final tagName = item['tag_name'];
        if (tagName is! String) continue;

        final build = buildNumberFromTag(tagName);
        if (build == null || build <= candidateBuild) continue;
        if (!isNewerRelease(currentBuildNumber: currentBuild, tag: tagName)) {
          continue;
        }

        final assets = item['assets'];
        if (assets is! List) continue;

        final selectedAsset = _selectBestApkAsset(assets, deviceAbis);
        if (selectedAsset == null) continue;

        final publishedAtStr = item['published_at'];
        DateTime? publishedAt;
        if (publishedAtStr is String) {
          publishedAt = DateTime.tryParse(publishedAtStr);
        }

        candidateBuild = build;
        candidate = AppReleaseInfo(
          tag: tagName,
          name: (item['name'] as String?)?.trim() ?? tagName,
          changelog: (item['body'] as String?)?.trim() ?? '',
          apkUrl: selectedAsset.downloadUrl,
          apkName: selectedAsset.name,
          apkSize: selectedAsset.size,
          htmlUrl: (item['html_url'] as String?) ?? '',
          buildNumber: build,
          publishedAt: publishedAt,
        );
      }

      return candidate;
    } catch (e) {
      debugPrint('Failed to fetch releases from $url: $e');
      return null;
    }
  }

  _AssetCandidate? _selectBestApkAsset(
    List<dynamic> assets,
    List<String> deviceAbis,
  ) {
    final parsed = <_AssetCandidate>[];
    for (final raw in assets) {
      if (raw is! Map) continue;
      final name = raw['name'];
      final url = raw['browser_download_url'];
      final size = raw['size'];
      if (name is String &&
          url is String &&
          size is int &&
          name.toLowerCase().endsWith('.apk')) {
        parsed.add(_AssetCandidate(name: name, downloadUrl: url, size: size));
      }
    }
    if (parsed.isEmpty) return null;

    // 1. Try to find an APK matching the primary or supported device ABIs
    for (final abi in deviceAbis) {
      final cleanAbi = abi.toLowerCase();
      final match = parsed.where(
        (a) =>
            a.name.toLowerCase().contains('-$cleanAbi.apk') ||
            a.name.toLowerCase().contains('$cleanAbi.apk'),
      );
      if (match.isNotEmpty) {
        return match.first;
      }
    }

    // 2. Try to find a universal APK (doesn't name an ABI like arm64/armeabi/x86)
    final universal = parsed.where((a) {
      final lower = a.name.toLowerCase();
      return !lower.contains('arm64') &&
          !lower.contains('armeabi') &&
          !lower.contains('v7a') &&
          !lower.contains('x86') &&
          !lower.contains('x86_64');
    });
    if (universal.isNotEmpty) {
      return universal.first;
    }

    // 3. Fallback to any APK
    return parsed.first;
  }

  /// Downloads the specified release APK with progress reporting and initiates
  /// direct package installation.
  Future<bool> downloadAndInstall({
    required AppReleaseInfo release,
    void Function(double fraction, int received, int total)? onProgress,
    CancelToken? cancelToken,
  }) async {
    try {
      final tempDir = await getTemporaryDirectory();
      final updateDir = Directory('${tempDir.path}/updates');
      if (!await updateDir.exists()) {
        await updateDir.create(recursive: true);
      }

      final targetFile = File('${updateDir.path}/${release.apkName}');
      if (await targetFile.exists()) {
        try {
          await targetFile.delete();
        } catch (_) {}
      }

      await _dio.download(
        release.apkUrl,
        targetFile.path,
        cancelToken: cancelToken,
        onReceiveProgress: (received, total) {
          if (onProgress != null) {
            final fraction = total > 0 ? (received / total).clamp(0.0, 1.0) : 0.0;
            onProgress(fraction, received, total);
          }
        },
      );

      if (!await targetFile.exists() || await targetFile.length() <= 0) {
        throw StateError('Downloaded APK is empty or missing');
      }

      final installed = await AppInstaller.installApk(targetFile.path);
      return installed;
    } catch (e) {
      debugPrint('Failed to download and install update: $e');
      rethrow;
    }
  }
}

class _AssetCandidate {
  const _AssetCandidate({
    required this.name,
    required this.downloadUrl,
    required this.size,
  });

  final String name;
  final String downloadUrl;
  final int size;
}
