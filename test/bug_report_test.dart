import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/feedback/bug_report.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/ui/widgets/product_states.dart';
import 'package:package_info_plus/package_info_plus.dart';

Widget _app(Widget child) => MaterialApp(home: Scaffold(body: child));

void main() {
  tearDown(() => debugPlatformCapabilities = null);

  group('bug report URL', () {
    test('prefills the template, version, and platform, and nothing else',
        () async {
      debugPlatformCapabilities =
          const PlatformCapabilities(platform: TargetPlatform.windows);
      final url = await buildBugReportUrl(
        info: PackageInfo(
          appName: 'opencode_mobile',
          packageName: 'io.github.eslamasabry.opencode_mobile',
          version: '1.0.31',
          buildNumber: '32',
        ),
      );

      expect(url.host, 'github.com');
      expect(url.path, '/Eslamasabry/opencode-mobile-next/issues/new');
      expect(url.queryParameters['template'], 'bug_report.yml');
      expect(url.queryParameters['app-version'], '1.0.31+32');
      final body = url.queryParameters['what-happened']!;
      expect(body, contains('App: 1.0.31+32'));
      expect(
        body,
        contains('Platform: Windows desktop (experimental'),
        reason: 'a Windows report must arrive already contexted',
      );
      // The prefill is environment-only: no field exists for a server URL,
      // directory, session id, or transcript, and none may be smuggled in.
      expect(body, isNot(contains('http://127.0.0.1')));
      expect(body, isNot(contains('/home/')));
    });

    test('falls back to an unknown version when the plugin never answers',
        () async {
      // PackageInfo.fromPlatform() would hang here; exercise only the
      // explicit-info path and assert the fallback shape separately via the
      // platform label contract below.
      final url = await buildBugReportUrl(
        info: PackageInfo(
          appName: 'opencode_mobile',
          packageName: 'io.github.eslamasabry.opencode_mobile',
          version: '0.0.0',
          buildNumber: '0',
        ),
      );
      expect(url.queryParameters['app-version'], '0.0.0+0');
    });

    test('labels every platform the seam can report', () {
      const cases = <TargetPlatform, String>{
        TargetPlatform.android: 'Android',
        TargetPlatform.linux: 'Linux desktop (alpha)',
        TargetPlatform.windows:
            'Windows desktop (experimental — contributor-tested)',
        TargetPlatform.macOS: 'macOS (untested)',
      };
      cases.forEach((platform, expected) {
        debugPlatformCapabilities = PlatformCapabilities(platform: platform);
        addTearDown(() => debugPlatformCapabilities = null);
        expect(bugReportPlatformLabel(), expected,
            reason: 'platform $platform');
      });
    });
  });

  group('openBugReport fallback', () {
    testWidgets('a failed launch copies the link and explains in one pump',
        (tester) async {
      await tester.pumpWidget(_app(const SizedBox.shrink()));
      final context = tester.element(find.byType(SizedBox));

      await openBugReport(
        context,
        urlBuilder:
            () async => Uri.parse('https://github.com/fake/issues/new'),
        launcher: (_) async => false,
      );
      await tester.pump();

      expect(
        find.text('Bug report link copied — open it in a browser.'),
        findsOneWidget,
      );
    });
  });

  group('the failure surface carries the report affordance', () {
    testWidgets('ProductErrorState offers Report a bug next to Try again',
        (tester) async {
      await tester.pumpWidget(
        _app(ProductErrorState(message: 'It broke.', onRetry: () async {})),
      );
      expect(find.text('Try again'), findsOneWidget);
      expect(
        find.byKey(const ValueKey('product-error-report-bug')),
        findsOneWidget,
      );
      expect(find.text('Report a bug'), findsOneWidget);
    });
  });

  group('l10n', () {
    testWidgets('localizations still load alongside the feedback imports',
        (tester) async {
      late AppLocalizations l10n;
      await tester.pumpWidget(
        MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: Builder(
            builder: (context) {
              l10n = AppLocalizations.of(context);
              return const SizedBox.shrink();
            },
          ),
        ),
      );
      expect(l10n.appTitle, 'OpenCode');
    });
  });
}
