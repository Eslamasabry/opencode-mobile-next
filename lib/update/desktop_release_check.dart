import 'dart:async';
import 'dart:io' show Platform;

import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter/material.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:url_launcher/url_launcher.dart';

import '../ui/widgets/external_link.dart';

/// Desktop builds cannot receive Shorebird patches, so Linux and Windows
/// check the project's GitHub releases instead and point at the release
/// page. Nothing downloads automatically.
const desktopReleasesApiUrl =
    'https://api.github.com/repos/Eslamasabry/oc_app/releases';
const desktopReleasesPageUrl =
    'https://github.com/Eslamasabry/oc_app/releases';

class DesktopReleaseInfo {
  final String tag;
  final String htmlUrl;

  const DesktopReleaseInfo({required this.tag, required this.htmlUrl});
}

/// Extracts the Android-style build number from a release tag such as
/// `v1.0.25+26-preview.5` (→ 26) or `v1.0.19+20` (→ 20). Returns null when
/// the tag carries none.
int? buildNumberFromTag(String tag) {
  final match = RegExp(r'\+(\d+)').firstMatch(tag);
  return match == null ? null : int.tryParse(match.group(1)!);
}

/// True when [tag] names a strictly newer build than the running
/// [currentBuildNumber]. Build numbers are the project's monotonic release
/// ordering; a tag without one is never reported as newer.
bool isNewerRelease({required int currentBuildNumber, required String tag}) {
  final tagBuild = buildNumberFromTag(tag);
  return tagBuild != null && tagBuild > currentBuildNumber;
}

/// Fetches the newest non-draft release. Pre-releases count: the preview
/// lineage is this app's distribution channel.
class DesktopReleaseChecker {
  DesktopReleaseChecker({Dio? dio}) : _dio = dio ?? Dio();

  final Dio _dio;

  Future<DesktopReleaseInfo?> fetchLatest() async {
    final response = await _dio.get<List<dynamic>>(
      desktopReleasesApiUrl,
      queryParameters: {'per_page': 10},
      options: Options(
        headers: {'Accept': 'application/vnd.github+json'},
        responseType: ResponseType.json,
      ),
    );
    for (final entry in response.data ?? const <dynamic>[]) {
      if (entry is! Map) continue;
      if (entry['draft'] == true) continue;
      final tag = entry['tag_name'];
      if (tag is! String || tag.isEmpty) continue;
      // `html_url` arrives over the network, so it only survives if it is an
      // https GitHub URL with no embedded credentials; anything else falls
      // back to the compiled-in releases page rather than being launched.
      final htmlUrl = entry['html_url'];
      final parsed = htmlUrl is String ? safeExternalLinkUri(htmlUrl) : null;
      final trusted =
          parsed != null &&
          parsed.scheme == 'https' &&
          (parsed.host == 'github.com' || parsed.host.endsWith('.github.com'));
      return DesktopReleaseInfo(
        tag: tag,
        htmlUrl: trusted ? parsed.toString() : desktopReleasesPageUrl,
      );
    }
    return null;
  }
}

bool get _runningOnDesktop =>
    !kIsWeb && (Platform.isLinux || Platform.isWindows);

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
      if (currentBuild == null) return;
      final latest =
          await (widget.checker ?? DesktopReleaseChecker()).fetchLatest();
      if (latest == null || !mounted) return;
      if (isNewerRelease(
        currentBuildNumber: currentBuild,
        tag: latest.tag,
      )) {
        _showAvailable(latest);
      }
    } on Exception catch (error) {
      debugPrint('Desktop release check failed: $error');
    } finally {
      _checking = false;
    }
  }

  void _showAvailable(DesktopReleaseInfo release) {
    if (_noticeShown) return;
    _noticeShown = true;
    final messenger = widget.messengerKey.currentState;
    if (messenger == null) return;
    messenger.showSnackBar(
      SnackBar(
        duration: const Duration(seconds: 12),
        content: Text('OpenCode ${release.tag} is available.'),
        action: SnackBarAction(
          label: 'View',
          onPressed: () {
            final url =
                safeExternalLinkUri(release.htmlUrl) ??
                Uri.parse(desktopReleasesPageUrl);
            final launch =
                widget.launcher ??
                (uri) => launchUrl(uri, mode: LaunchMode.externalApplication);
            unawaited(launch(url));
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
