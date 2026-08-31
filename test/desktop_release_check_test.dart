import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
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

Widget _host(DesktopReleaseNotice notice) => MaterialApp(
  scaffoldMessengerKey: notice.messengerKey,
  home: Scaffold(body: notice),
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
      '{"draft":false,"tag_name":"v1.0.25+26-preview.5",'
      '"html_url":"https://github.com/Eslamasabry/opencode-mobile/releases/tag/v1"}]',
    );
    final checker = DesktopReleaseChecker(
      dio: Dio()..httpClientAdapter = adapter,
    );

    final release = await checker.fetchLatest();

    expect(adapter.lastRequest?.uri.toString(), startsWith(
      'https://api.github.com/repos/Eslamasabry/opencode-mobile/releases',
    ));
    expect(release?.tag, 'v1.0.25+26-preview.5');
    expect(
      release?.htmlUrl,
      'https://github.com/Eslamasabry/opencode-mobile/releases/tag/v1',
    );
  });

  test('a release URL that is not GitHub over https falls back', () async {
    for (final hostile in const [
      'javascript:alert(1)',
      'http://github.com/Eslamasabry/opencode-mobile/releases',
      'https://user:pass@github.com/releases',
      'https://github.com.evil.test/releases',
      'https://example.test/rel',
      '',
    ]) {
      final adapter = _RecordingAdapter(
        '[{"draft":false,"tag_name":"v1.0.25+26-preview.5",'
        '"html_url":"${hostile.replaceAll('"', '')}"}]',
      );
      final checker = DesktopReleaseChecker(
        dio: Dio()..httpClientAdapter = adapter,
      );

      final release = await checker.fetchLatest();

      expect(release?.htmlUrl, desktopReleasesPageUrl, reason: hostile);
    }
  });

  testWidgets('a newer release shows one notice whose View action launches', (
    tester,
  ) async {
    final messengerKey = GlobalKey<ScaffoldMessengerState>();
    final checker = _FakeChecker(
      const DesktopReleaseInfo(
        tag: 'v1.0.30+31-preview.9',
        htmlUrl: 'https://example.test/release',
      ),
    );
    Uri? launched;
    await tester.pumpWidget(
      _host(
        DesktopReleaseNotice(
          messengerKey: messengerKey,
          checker: checker,
          enabledOverride: true,
          currentBuildNumberLoader: () async => 26,
          launcher: (url) async => launched = url,
          child: const SizedBox.shrink(),
        ),
      ),
    );
    await tester.pump();
    await tester.pump();

    expect(
      find.text('OpenCode v1.0.30+31-preview.9 is available.'),
      findsOneWidget,
    );

    // Let the snackbar finish its entrance before tapping its action.
    await tester.pumpAndSettle();
    await tester.tap(find.text('View'));
    await tester.pump();
    expect(launched, Uri.parse('https://example.test/release'));

    // A resume never re-shows the notice within the run.
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
    await tester.pump();
    expect(checker.calls, 1);
  });

  testWidgets('throttle keeps rapid resumes to one check', (tester) async {
    final messengerKey = GlobalKey<ScaffoldMessengerState>();
    final checker = _FakeChecker(null);
    var minutes = 0;
    await tester.pumpWidget(
      _host(
        DesktopReleaseNotice(
          messengerKey: messengerKey,
          checker: checker,
          enabledOverride: true,
          currentBuildNumberLoader: () async => 26,
          now: () => DateTime(2026, 1, 1).add(Duration(minutes: minutes)),
          child: const SizedBox.shrink(),
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
    final messengerKey = GlobalKey<ScaffoldMessengerState>();
    final checker = _FakeChecker(null);
    await tester.pumpWidget(
      _host(
        DesktopReleaseNotice(
          messengerKey: messengerKey,
          checker: checker,
          enabledOverride: false,
          currentBuildNumberLoader: () async => 26,
          child: const SizedBox.shrink(),
        ),
      ),
    );
    await tester.pump();
    await tester.pump();

    expect(checker.calls, 0);
  });
}
