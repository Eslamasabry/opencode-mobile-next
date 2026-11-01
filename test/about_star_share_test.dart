import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/screens/about_screen.dart';
import 'package:opencode_mobile/ui/search/search_index.dart';
import 'package:package_info_plus/package_info_plus.dart';

Widget _app(Widget home, {Locale locale = const Locale('en')}) => MaterialApp(
  locale: locale,
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  home: home,
);

/// A widget test on [platform]; the override is cleared before the binding
/// checks its invariants.
void _test(
  String name,
  Future<void> Function(WidgetTester tester) body, {
  TargetPlatform platform = TargetPlatform.android,
}) {
  testWidgets(name, (tester) async {
    debugDefaultTargetPlatformOverride = platform;
    try {
      await body(tester);
    } finally {
      debugDefaultTargetPlatformOverride = null;
    }
  });
}

void main() {
  setUp(() {
    rootBundle
      ..evict('PRIVACY.md')
      ..evict('THIRD_PARTY_NOTICES.md');
    PackageInfo.setMockInitialValues(
      appName: 'OpenCode Mobile',
      packageName: 'com.example.opencode_mobile',
      version: '2.2.0',
      buildNumber: '57',
      buildSignature: '',
    );
  });

  _test('About has Star on GitHub and Share the app rows', (tester) async {
    await tester.pumpWidget(_app(const AboutScreen()));
    await tester.pumpAndSettle();
    final star = find.byKey(const ValueKey('about-star-github'));
    final share = find.byKey(const ValueKey('about-share-app'));
    await tester.scrollUntilVisible(
      share,
      300,
      scrollable: find.byType(Scrollable).first,
    );
    expect(star, findsOneWidget);
    expect(share, findsOneWidget);
    expect(find.text('Star on GitHub'), findsOneWidget);
    expect(find.text('Helps others find the app'), findsOneWidget);
    expect(find.text('Share the app'), findsOneWidget);
  });

  _test('Star on GitHub goes through openExternalLink', (tester) async {
    final opened = <Uri>[];
    await tester.pumpWidget(
      _app(
        AboutScreen(
          linkLauncher: (uri) async {
            opened.add(uri);
            return true;
          },
        ),
      ),
    );
    await tester.pumpAndSettle();
    final star = find.byKey(const ValueKey('about-star-github'));
    await tester.scrollUntilVisible(
      star,
      300,
      scrollable: find.byType(Scrollable).first,
    );
    await tester.tap(star);
    await tester.pumpAndSettle();
    // The shared gate asks first, naming the host.
    expect(find.byKey(const ValueKey('external-link-confirm')), findsOneWidget);
    expect(opened, isEmpty);
    await tester.tap(find.text('Open link'));
    await tester.pumpAndSettle();
    expect(opened, [
      Uri.parse('https://github.com/Eslamasabry/opencode-mobile-next'),
    ]);
  });

  _test('Share the app sends one line and the website link', (tester) async {
    final calls = <MethodCall>[];
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
      const MethodChannel('oc/share'),
      (call) async {
        calls.add(call);
        return true;
      },
    );
    addTearDown(
      () => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
        const MethodChannel('oc/share'),
        null,
      ),
    );
    await tester.pumpWidget(_app(const AboutScreen()));
    await tester.pumpAndSettle();
    final share = find.byKey(const ValueKey('about-share-app'));
    await tester.scrollUntilVisible(
      share,
      300,
      scrollable: find.byType(Scrollable).first,
    );
    await tester.tap(share);
    await tester.pumpAndSettle();
    expect(calls, hasLength(1));
    expect(calls.single.method, 'shareText');
    final text = (calls.single.arguments as Map)['text'] as String;
    expect(
      text,
      contains('https://eslamasabry.github.io/opencode-mobile-next/'),
    );
    expect(text.trim().split('\n'), hasLength(1));
    expect(text, startsWith('OpenCode Mobile'));
  });

  _test('Share the app is absent off Android', platform: TargetPlatform.linux, (
    tester,
  ) async {
    await tester.pumpWidget(_app(const AboutScreen()));
    await tester.pumpAndSettle();
    await tester.scrollUntilVisible(
      find.byKey(const ValueKey('about-star-github')),
      300,
      scrollable: find.byType(Scrollable).first,
    );
    expect(find.byKey(const ValueKey('about-share-app')), findsNothing);
  });

  _test('the rows read in Arabic', (tester) async {
    await tester.pumpWidget(
      _app(const AboutScreen(), locale: const Locale('ar')),
    );
    await tester.pumpAndSettle();
    await tester.scrollUntilVisible(
      find.byKey(const ValueKey('about-share-app')),
      300,
      scrollable: find.byType(Scrollable).first,
    );
    expect(find.text('شارك التطبيق'), findsOneWidget);
  });

  _test('Tell others sits right under the version rows, above Details and '
      'Open source', (tester) async {
    tester.view
      ..physicalSize = const Size(412, 3000)
      ..devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(_app(const AboutScreen()));
    // The package info is a real platform future: give it real time.
    for (var i = 0; i < 10; i++) {
      await tester.runAsync(
        () => Future<void>.delayed(const Duration(milliseconds: 50)),
      );
      await tester.pumpAndSettle();
    }
    final spread = tester.getTopLeft(
      find.byKey(const ValueKey('about-spread')),
    );
    final open = tester.getTopLeft(
      find.byKey(const ValueKey('about-open-source')),
    );
    final details = tester.getTopLeft(
      find.byKey(const ValueKey('about-details')),
    );
    // The update check moved to Settings › This app › Updates; the version
    // rows are what sits above now.
    final version = tester.getTopLeft(
      find.byKey(const ValueKey('about-version')),
    );
    expect(spread.dy, greaterThan(version.dy));
    expect(spread.dy, lessThan(details.dy));
    expect(spread.dy, lessThan(open.dy));
  });

  test('Settings search finds star and share in both languages', () {
    for (final code in ['en', 'ar']) {
      final entries = allSearchEntries(lookupAppLocalizations(Locale(code)));
      final star = entries.firstWhere((e) => e.id == 'about-star-github');
      final share = entries.firstWhere((e) => e.id == 'about-share-app');
      expect(star.matches('star'), isTrue, reason: code);
      expect(share.matches('share'), isTrue, reason: code);
      expect(star.matches(star.title), isTrue, reason: code);
      expect(share.matches(share.title), isTrue, reason: code);
    }
  });
}
