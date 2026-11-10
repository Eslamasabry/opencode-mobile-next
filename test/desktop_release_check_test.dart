import 'dart:async';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit_buttons.dart';
import 'package:opencode_mobile/ui/kit/kit_screen.dart';
import 'package:opencode_mobile/ui/kit/kit_status_line.dart';
import 'package:opencode_mobile/ui/kit/kit_top_bar.dart';
import 'package:opencode_mobile/update/desktop_release_check.dart';

class _FakeChecker extends DesktopReleaseChecker {
  _FakeChecker(this.release);

  final DesktopReleaseInfo? release;
  int calls = 0;

  @override
  Future<DesktopReleaseInfo?> fetchLatest() async {
    calls += 1;
    return release;
  }
}

class _RecordingAdapter implements HttpClientAdapter {
  _RecordingAdapter(this.payload);

  final String payload;
  RequestOptions? lastRequest;

  @override
  void close({bool force = false}) {}

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    lastRequest = options;
    return ResponseBody.fromString(
      payload,
      200,
      headers: {
        Headers.contentTypeHeader: ['application/json'],
      },
    );
  }
}

class _HangingAdapter implements HttpClientAdapter {
  @override
  void close({bool force = false}) {}

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) => Completer<ResponseBody>().future;
}

const _release = DesktopReleaseInfo(
  tag: 'v1.0.30+31-preview.9',
  htmlUrl:
      'https://github.com/Eslamasabry/opencode-mobile-next/releases/tag/v31',
);
const _available = 'Update v1.0.30+31-preview.9 is available';
const _whatChanged =
    'The release page lists what changed and has the downloads.';
const _open = 'Open release page';

/// The notice where main.dart mounts it (above the Navigator), with a page
/// on the kit's screen frame below, whose status slot draws the line.
Widget _host(
  DesktopReleaseNotice Function(Widget child) notice, {
  GlobalKey<NavigatorState>? navigatorKey,
}) => MaterialApp(
  navigatorKey: navigatorKey,
  theme: AppTheme.dark(),
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  builder: (context, child) => notice(child ?? const SizedBox.shrink()),
  home: KitScreen(
    topBar: const KitTopBar(title: 'Work'),
    body: ListView(children: const [Text('OpenCode')]),
    bottom: KitActionBlock(
      primary: KitAction(label: 'New conversation', onPressed: () {}),
    ),
  ),
);

void main() {
  group('tag parsing and comparison', () {
    test('extracts build numbers from release tags', () {
      expect(buildNumberFromTag('v1.0.25+26-preview.5'), 26);
      expect(buildNumberFromTag('v1.0.19+20'), 20);
      expect(buildNumberFromTag('v2.0.0'), isNull);
      expect(buildNumberFromTag('nonsense'), isNull);
    });

    test('newer means a strictly larger build number', () {
      expect(
        isNewerRelease(currentBuildNumber: 25, tag: 'v1.0.25+26-preview.5'),
        isTrue,
      );
      expect(
        isNewerRelease(currentBuildNumber: 26, tag: 'v1.0.25+26-preview.5'),
        isFalse,
      );
      expect(isNewerRelease(currentBuildNumber: 1, tag: 'v2.0.0'), isFalse);
    });
  });

  test('checker requests the releases API and skips drafts', () async {
    final adapter = _RecordingAdapter(
      '[{"draft":true,"tag_name":"v9.9.9+99"},'
      '{"draft":false,"prerelease":false,"tag_name":"v1.0.25+26-preview.5",'
      '"html_url":"https://github.com/Eslamasabry/opencode-mobile-next/releases/tag/v1"}]',
    );
    final checker = DesktopReleaseChecker(
      dio: Dio()..httpClientAdapter = adapter,
    );

    final release = await checker.fetchLatest();

    expect(
      adapter.lastRequest?.uri.toString(),
      startsWith(
        'https://api.github.com/repos/Eslamasabry/opencode-mobile-next/releases',
      ),
    );
    expect(release?.tag, 'v1.0.25+26-preview.5');
    expect(
      release?.htmlUrl,
      'https://github.com/Eslamasabry/opencode-mobile-next/releases/tag/v1',
    );
    expect(adapter.lastRequest?.connectTimeout, desktopReleaseNetworkTimeout);
    expect(adapter.lastRequest?.receiveTimeout, desktopReleaseNetworkTimeout);
    expect(adapter.lastRequest?.sendTimeout, desktopReleaseNetworkTimeout);
  });

  test('checker offers only promoted releases, never a pre-release', () async {
    // A new version is published as a GitHub pre-release first and promoted
    // after quiet days (`scripts/release.sh promote`); until then normal
    // users keep seeing the last promoted release.
    final adapter = _RecordingAdapter(
      '[{"draft":false,"prerelease":true,"tag_name":"v1.0.29+30",'
      '"html_url":"https://github.com/Eslamasabry/opencode-mobile-next/releases/tag/v30"},'
      '{"draft":false,"tag_name":"v1.0.29+31",'
      '"html_url":"https://github.com/Eslamasabry/opencode-mobile-next/releases/tag/v31"},'
      '{"draft":false,"prerelease":false,"tag_name":"v1.0.28+29",'
      '"html_url":"https://github.com/Eslamasabry/opencode-mobile-next/releases/tag/v29"}]',
    );

    final release = await DesktopReleaseChecker(
      dio: Dio()..httpClientAdapter = adapter,
    ).fetchLatest();

    // The pre-release and the entry that does not say it is a normal release
    // are both passed over.
    expect(release?.tag, 'v1.0.28+29');
  });

  test('checker returns nothing while only a pre-release is newer', () async {
    final adapter = _RecordingAdapter(
      '[{"draft":false,"prerelease":true,"tag_name":"v1.0.29+30",'
      '"html_url":"https://github.com/Eslamasabry/opencode-mobile-next/releases/tag/v30"}]',
    );

    final release = await DesktopReleaseChecker(
      dio: Dio()..httpClientAdapter = adapter,
    ).fetchLatest();

    expect(release, isNull);
  });

  test('checker chooses the highest valid build from unordered entries', () async {
    final adapter = _RecordingAdapter(
      '[{"draft":false,"prerelease":false,"tag_name":"latest",'
      '"html_url":"https://github.com/Eslamasabry/opencode-mobile-next/releases"},'
      '{"draft":false,"prerelease":false,"tag_name":"v1.0.25+26-preview.5",'
      '"html_url":"https://github.com/Eslamasabry/opencode-mobile-next/releases/tag/v26"},'
      '{"draft":false,"prerelease":false,"tag_name":"v1.0.99+999oops",'
      '"html_url":"https://github.com/Eslamasabry/opencode-mobile-next/releases/tag/bad"},'
      '{"draft":false,"prerelease":false,"tag_name":"v1.0.27+28",'
      '"html_url":"https://github.com/Eslamasabry/opencode-mobile-next/releases/tag/v28"}]',
    );

    final release = await DesktopReleaseChecker(
      dio: Dio()..httpClientAdapter = adapter,
    ).fetchLatest();

    expect(release?.tag, 'v1.0.27+28');
    expect(
      release?.htmlUrl,
      'https://github.com/Eslamasabry/opencode-mobile-next/releases/tag/v28',
    );
  });

  test('unversioned and malformed entries do not hide a valid update', () async {
    final adapter = _RecordingAdapter(
      '[{"draft":false,"prerelease":false,"tag_name":"v1.0.28+29oops",'
      '"html_url":"https://github.com/Eslamasabry/opencode-mobile-next/releases"},'
      '{"draft":false,"prerelease":false,"tag_name":"v1.0.28",'
      '"html_url":"https://github.com/Eslamasabry/opencode-mobile-next/releases"},'
      '{"draft":false,"prerelease":false,"tag_name":"v1.0.26+27-preview.1",'
      '"html_url":"https://github.com/Eslamasabry/opencode-mobile-next/releases/tag/v27"}]',
    );

    final release = await DesktopReleaseChecker(
      dio: Dio()..httpClientAdapter = adapter,
    ).fetchLatest();

    expect(release?.tag, 'v1.0.26+27-preview.1');
  });

  test('checker network wait is bounded even when the adapter hangs', () async {
    final checker = DesktopReleaseChecker(
      dio: Dio()..httpClientAdapter = _HangingAdapter(),
      timeout: const Duration(milliseconds: 10),
    );

    await expectLater(checker.fetchLatest(), throwsA(isA<TimeoutException>()));
  });

  test('a release URL that is not GitHub over https falls back', () async {
    for (final hostile in const [
      'javascript:alert(1)',
      'http://github.com/Eslamasabry/opencode-mobile-next/releases',
      'https://user:pass@github.com/releases',
      'https://github.com.evil.test/releases',
      'https://github.com/Eslamasabry/opencode-mobile-next-evil/releases',
      'https://github.com/another-owner/opencode-mobile-next/releases',
      'https://github.com/Eslamasabry/opencode-mobile-next/issues',
      'https://github.com/Eslamasabry/opencode-mobile-next/releases?tab=tags',
      'https://github.com/Eslamasabry/opencode-mobile-next/releases#latest',
      'https://example.test/rel',
      '',
    ]) {
      final adapter = _RecordingAdapter(
        '[{"draft":false,"prerelease":false,"tag_name":"v1.0.25+26-preview.5",'
        '"html_url":"${hostile.replaceAll('"', '')}"}]',
      );
      final checker = DesktopReleaseChecker(
        dio: Dio()..httpClientAdapter = adapter,
      );

      final release = await checker.fetchLatest();

      expect(release?.htmlUrl, desktopReleasesPageUrl, reason: hostile);
    }
  });

  testWidgets('a newer release shows one status line whose Open release '
      'page launches', (tester) async {
    final checker = _FakeChecker(_release);
    Uri? launched;
    await tester.pumpWidget(
      _host(
        (child) => DesktopReleaseNotice(
          checker: checker,
          enabledOverride: true,
          currentBuildNumberLoader: () async => 26,
          launcher: (url) async => launched = url,
          child: child,
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text(_available), findsOneWidget);
    expect(find.text(_whatChanged), findsOneWidget);
    expect(find.byType(KitStatusLine), findsOneWidget);
    expect(find.byType(SnackBar), findsNothing);
    // The line sits under the top bar, clear of the pinned primary.
    expect(
      tester.getBottomLeft(find.text(_whatChanged)).dy,
      lessThan(tester.getTopLeft(find.text('New conversation')).dy),
    );

    await tester.tap(find.text(_open));
    await tester.pump();
    expect(
      launched,
      Uri.parse(
        'https://github.com/Eslamasabry/opencode-mobile-next/releases/tag/v31',
      ),
    );

    // A resume never re-checks while the line is known.
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
    await tester.pump();
    expect(checker.calls, 1);
  });

  testWidgets('production Open release page uses the external-link '
      'confirmation', (tester) async {
    final navigatorKey = GlobalKey<NavigatorState>();
    await tester.pumpWidget(
      _host(
        navigatorKey: navigatorKey,
        (child) => DesktopReleaseNotice(
          navigatorKey: navigatorKey,
          checker: _FakeChecker(_release),
          enabledOverride: true,
          currentBuildNumberLoader: () async => 26,
          child: child,
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text(_open));
    await tester.pumpAndSettle();

    expect(find.text('Open external link?'), findsOneWidget);
    await tester.tap(find.text('Cancel'));
    await tester.pumpAndSettle();
  });

  testWidgets('Dismiss hides the line for the rest of the run', (tester) async {
    final checker = _FakeChecker(_release);
    var minutes = 0;
    await tester.pumpWidget(
      _host(
        (child) => DesktopReleaseNotice(
          checker: checker,
          enabledOverride: true,
          currentBuildNumberLoader: () async => 26,
          now: () => DateTime(2026, 1, 1).add(Duration(minutes: minutes)),
          child: child,
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text(_available), findsOneWidget);

    await tester.tap(find.byKey(const ValueKey('kit-status-dismiss')));
    await tester.pumpAndSettle();
    expect(find.text(_available), findsNothing);

    minutes = 30;
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
    await tester.pumpAndSettle();
    expect(find.text(_available), findsNothing);
    expect(checker.calls, 1);
  });

  testWidgets('an older or equal release shows nothing', (tester) async {
    await tester.pumpWidget(
      _host(
        (child) => DesktopReleaseNotice(
          checker: _FakeChecker(_release),
          enabledOverride: true,
          currentBuildNumberLoader: () async => 31,
          child: child,
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.byType(KitStatusLine), findsNothing);
  });

  testWidgets('throttle keeps rapid resumes to one check', (tester) async {
    final checker = _FakeChecker(null);
    var minutes = 0;
    await tester.pumpWidget(
      _host(
        (child) => DesktopReleaseNotice(
          checker: checker,
          enabledOverride: true,
          currentBuildNumberLoader: () async => 26,
          now: () => DateTime(2026, 1, 1).add(Duration(minutes: minutes)),
          child: child,
        ),
      ),
    );
    await tester.pump();
    expect(checker.calls, 1);

    minutes = 5;
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
    await tester.pump();
    expect(checker.calls, 1);

    minutes = 20;
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
    await tester.pump();
    expect(checker.calls, 2);
  });

  testWidgets('non-desktop platforms never check', (tester) async {
    final checker = _FakeChecker(null);
    await tester.pumpWidget(
      _host(
        (child) => DesktopReleaseNotice(
          checker: checker,
          enabledOverride: false,
          currentBuildNumberLoader: () async => 26,
          child: child,
        ),
      ),
    );
    await tester.pump();
    await tester.pump();

    expect(checker.calls, 0);
  });
}
