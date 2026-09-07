import 'dart:async';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:package_info_plus/package_info_plus.dart';

import '../platform/platform_capabilities.dart';
import '../ui/widgets/external_link.dart';

/// Desktop builds cannot receive Shorebird patches, so Linux and Windows
/// check the project's GitHub releases instead and point at the release
/// page. Nothing downloads automatically.
const desktopReleasesApiUrl =
    'https://api.github.com/repos/Eslamasabry/opencode-mobile-next/releases';
const desktopReleasesPageUrl =
    'https://github.com/Eslamasabry/opencode-mobile-next/releases';
const desktopReleaseNetworkTimeout = Duration(seconds: 10);

const _desktopReleaseOwner = 'Eslamasabry';
const _desktopReleaseRepository = 'opencode-mobile-next';
final _desktopReleaseTagPattern = RegExp(
  r'^v\d+\.\d+\.\d+\+(\d+)(?:-[0-9A-Za-z]+(?:[.-][0-9A-Za-z]+)*)?$',
);

class DesktopReleaseInfo {
  final String tag;
  final String htmlUrl;

  const DesktopReleaseInfo({required this.tag, required this.htmlUrl});
}

/// Extracts the Android-style build number from a release tag such as
/// `v1.0.25+26-preview.5` (→ 26) or `v1.0.19+20` (→ 20). Returns null when
/// the tag carries none.
int? buildNumberFromTag(String tag) {
  final match = _desktopReleaseTagPattern.firstMatch(tag);
  return match == null ? null : int.tryParse(match.group(1)!);
}

/// True when [tag] names a strictly newer build than the running
/// [currentBuildNumber]. Build numbers are the project's monotonic release
/// ordering; a tag without one is never reported as newer.
bool isNewerRelease({required int currentBuildNumber, required String tag}) {
  final tagBuild = buildNumberFromTag(tag);
  return tagBuild != null && tagBuild > currentBuildNumber;
}

/// Fetches the newest valid non-draft release. Pre-releases count: the
/// preview lineage is this app's distribution channel.
class DesktopReleaseChecker {
  DesktopReleaseChecker({
    Dio? dio,
    Duration timeout = desktopReleaseNetworkTimeout,
  }) : _dio = dio ?? Dio(),
       _timeout = timeout;

  final Dio _dio;
  final Duration _timeout;

  Future<DesktopReleaseInfo?> fetchLatest() async {
    final cancelToken = CancelToken();
    final response = await _dio
        .get<List<dynamic>>(
          desktopReleasesApiUrl,
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
            cancelToken.cancel('Desktop release request timed out');
            throw TimeoutException(
              'Desktop release request timed out',
              _timeout,
            );
          },
        );

    DesktopReleaseInfo? newest;
    var newestBuild = -1;
    for (final entry in response.data ?? const <dynamic>[]) {
      if (entry is! Map) continue;
      if (entry['draft'] == true) continue;
      final tag = entry['tag_name'];
      if (tag is! String) continue;
      final build = buildNumberFromTag(tag);
      // GitHub's API is ordered by publication time, not this app's
      // monotonic build number. Ignore malformed/unversioned entries while
      // choosing the highest valid project build.
      if (build == null || build <= newestBuild) continue;

      final htmlUrl = entry['html_url'];
      final parsed = htmlUrl is String ? _trustedReleaseUri(htmlUrl) : null;
      newestBuild = build;
      newest = DesktopReleaseInfo(
        tag: tag,
        htmlUrl: parsed?.toString() ?? desktopReleasesPageUrl,
      );
    }
    return newest;
  }
}

Uri? _trustedReleaseUri(String? value) {
  final parsed = safeExternalLinkUri(value);
  if (parsed == null ||
      parsed.scheme != 'https' ||
      parsed.host.toLowerCase() != 'github.com' ||
      parsed.hasPort ||
      parsed.hasQuery ||
      parsed.fragment.isNotEmpty) {
    return null;
  }
  final segments = parsed.pathSegments;
  if (segments.length < 3 ||
      segments[0].toLowerCase() != _desktopReleaseOwner.toLowerCase() ||
      segments[1].toLowerCase() != _desktopReleaseRepository.toLowerCase() ||
      segments[2].toLowerCase() != 'releases' ||
      (segments.length != 3 &&
          (segments.length != 5 ||
              segments[3].toLowerCase() != 'tag' ||
              segments[4].isEmpty))) {
    return null;
  }
  return parsed;
}

/// The complement of Shorebird code push: the platforms that ship a release
/// artifact but cannot receive a patch.
///
/// Read through the capability seam rather than `dart:io`'s `Platform`, which
/// reports the *host* — under `flutter_test` on Linux that made this true for
/// every widget test that mounted the app, so the suite quietly enabled a
/// GitHub release check on what it was pretending was a phone.
bool get _runningOnDesktop => platformCapabilities.supportsDesktopReleaseCheck;

/// Mirrors the Shorebird notice's lifecycle: checks on start and resume,
/// throttled, showing one snackbar per run with a View action that opens the
/// release page externally.
class DesktopReleaseNotice extends StatefulWidget {
  const DesktopReleaseNotice({
    super.key,
    required this.messengerKey,
    required this.child,
    this.checker,
    this.enabledOverride,
    this.currentBuildNumberLoader,
    this.launcher,
    this.navigatorKey,
    this.now,
  });

  final GlobalKey<ScaffoldMessengerState> messengerKey;
  final Widget child;
  final DesktopReleaseChecker? checker;

  /// Tests override platform detection; production derives it from the
  /// running platform.
  final bool? enabledOverride;
  final Future<int?> Function()? currentBuildNumberLoader;
  final Future<void> Function(Uri url)? launcher;

  /// The Navigator context used by the production external-link confirmation.
  /// The notice is mounted above the app Navigator in [MaterialApp.builder].
  final GlobalKey<NavigatorState>? navigatorKey;
  final DateTime Function()? now;

  @override
  State<DesktopReleaseNotice> createState() => _DesktopReleaseNoticeState();
}

class _DesktopReleaseNoticeState extends State<DesktopReleaseNotice>
    with WidgetsBindingObserver {
  bool _checking = false;
  bool _noticeShown = false;
  DateTime? _lastCheck;

  bool get _enabled => widget.enabledOverride ?? _runningOnDesktop;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      unawaited(_check());
    });
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) unawaited(_check());
  }

  Future<int?> _loadCurrentBuildNumber() async {
    if (widget.currentBuildNumberLoader case final loader?) return loader();
    final info = await PackageInfo.fromPlatform();
    return int.tryParse(info.buildNumber);
  }

  Future<void> _check() async {
    if (!_enabled || _checking || _noticeShown) return;
    final now = (widget.now ?? DateTime.now)();
    if (_lastCheck case final previous?
        when now.difference(previous) < const Duration(minutes: 15)) {
      return;
    }
    _checking = true;
    _lastCheck = now;
    try {
      final currentBuild = await _loadCurrentBuildNumber();
      if (currentBuild == null || !mounted) return;
      final latest = await (widget.checker ?? DesktopReleaseChecker())
          .fetchLatest();
      if (latest == null || !mounted) return;
      if (isNewerRelease(currentBuildNumber: currentBuild, tag: latest.tag)) {
        _showAvailable(context, latest);
      }
    } on Exception catch (error) {
      debugPrint('Desktop release check failed: $error');
    } finally {
      _checking = false;
    }
  }

  void _showAvailable(BuildContext context, DesktopReleaseInfo release) {
    if (_noticeShown) return;
    final messenger = widget.messengerKey.currentState;
    if (!mounted || messenger == null) {
      // A check that could not reach the messenger did not deliver anything;
      // let the next resume retry without waiting for the normal throttle.
      _lastCheck = null;
      return;
    }
    _noticeShown = true;
    messenger.showSnackBar(
      SnackBar(
        duration: const Duration(seconds: 12),
        content: Text('OpenCode ${release.tag} is available.'),
        action: SnackBarAction(
          label: 'View',
          onPressed: () {
            final url =
                _trustedReleaseUri(release.htmlUrl) ??
                Uri.parse(desktopReleasesPageUrl);
            if (widget.launcher case final launch?) {
              // Keep the existing callback as a lightweight test seam. The
              // production path below always goes through the app's external
              // link confirmation and URL policy.
              unawaited(launch(url));
            } else {
              final linkContext =
                  widget.navigatorKey?.currentContext ?? context;
              if (linkContext.mounted) {
                unawaited(openExternalLink(linkContext, url.toString()));
              }
            }
          },
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) => widget.child;

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }
}
