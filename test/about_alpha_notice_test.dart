import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/kit/kit.dart' show KitSkeletonRows;
import 'package:opencode_mobile/ui/screens/about_screen.dart'
    show AboutScreen, aboutNoticesForReaders, buildProvenanceBody;
import 'package:opencode_mobile/update/shorebird_update_notice.dart';
import 'package:package_info_plus/package_info_plus.dart';

/// A scripted updater: answers [state], counts the downloads.
class _Updater implements AppUpdateService {
  _Updater(this.state, {this.available = true});

  final AppUpdateState state;
  final bool available;
  int downloads = 0;

  @override
  bool get isAvailable => available;

  @override
  Future<AppUpdateState> checkForUpdate() async => state;

  @override
  Future<void> downloadUpdate() async => downloads++;
}

/// Settles the page, letting the package info and documents (real
/// platform and asset futures) resolve first.
Future<void> _settle(WidgetTester tester) async {
  await tester.pumpAndSettle();
  // The larger document decodes on a background isolate: give it real time,
  // up to 10 s (2 s ran out on a loaded test server, 2026-10-27).
  for (var i = 0; i < 100; i++) {
    await tester.runAsync(
      () => Future<void>.delayed(const Duration(milliseconds: 100)),
    );
    await tester.pumpAndSettle();
    if (find.byType(KitSkeletonRows).evaluate().isEmpty) break;
  }
}

Widget _app(Widget home) => MaterialApp(
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  home: home,
);

/// The build-provenance notice, the build identity and the bug-report path
/// on About. Runs in its own file because the documents load through a
/// process-wide asset cache.
void main() {
  setUp(() {
    // The asset cache keeps a load started in an earlier test's zone, which
    // never completes in this one: load the documents afresh.
    rootBundle
      ..evict('PRIVACY.md')
      ..evict('THIRD_PARTY_NOTICES.md');
    PackageInfo.setMockInitialValues(
      appName: 'OpenCode Mobile',
      packageName: 'com.example.opencode_mobile',
      version: '1.0.44',
      buildNumber: '52',
      buildSignature: '',
    );
  });

  testWidgets('About shows the build once, provenance and one report path', (
    tester,
  ) async {
    tester.view
      ..physicalSize = const Size(412, 915)
      ..devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(_app(const AboutScreen()));
    await _settle(tester);

    // Retitled "About"; the version is shown once, above the tabs.
    expect(find.text('About'), findsOneWidget);
    expect(find.text('OpenCode Mobile 1.0.44+52'), findsOneWidget);
    // No report path here: Settings has the one "Report a problem" row.
    expect(find.byKey(const ValueKey('about-report-bug')), findsNothing);
    expect(find.byKey(const ValueKey('about-alpha-report-bug')), findsNothing);
    expect(find.text('Report a bug on GitHub'), findsNothing);
    expect(find.text('About this build'), findsOneWidget);
    expect(
      find.text(
        'Android is the supported platform; desktop builds are experimental.',
      ),
      findsOneWidget,
    );
    expect(find.byKey(const Key('about-non-affiliation')), findsOneWidget);
    // The package id sits under Details, folded.
    expect(find.text('com.example.opencode_mobile'), findsNothing);
    await tester.tap(find.byKey(const ValueKey('about-details')));
    await tester.pumpAndSettle();
    expect(find.text('com.example.opencode_mobile'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('the version copies in one tap', (tester) async {
    String? clipboard;
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(SystemChannels.platform, (call) async {
          if (call.method == 'Clipboard.setData') {
            clipboard = (call.arguments as Map)['text'] as String?;
          }
          return null;
        });
    addTearDown(
      () => TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(SystemChannels.platform, null),
    );
    await tester.pumpWidget(_app(const AboutScreen()));
    await _settle(tester);

    await tester.tap(find.byKey(const ValueKey('about-copy-version')));
    await tester.pumpAndSettle();
    expect(clipboard, 'OpenCode Mobile 1.0.44+52');
  });

  testWidgets('check for updates: fetches an update and says to reopen', (
    tester,
  ) async {
    final updater = _Updater(AppUpdateState.available);
    await tester.pumpWidget(_app(AboutScreen(updateService: updater)));
    await _settle(tester);

    expect(find.text('Looks for a newer version of this app'), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('about-check-updates')));
    await tester.pumpAndSettle();
    expect(updater.downloads, 1);
    expect(
      find.text('Update ready. Close and reopen the app to use it.'),
      findsOneWidget,
    );
  });

  testWidgets('check for updates: up to date, and a build that cannot', (
    tester,
  ) async {
    await tester.pumpWidget(
      _app(AboutScreen(updateService: _Updater(AppUpdateState.current))),
    );
    await _settle(tester);
    await tester.tap(find.byKey(const ValueKey('about-check-updates')));
    await tester.pumpAndSettle();
    expect(find.text('You have the latest version'), findsOneWidget);

    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pumpWidget(
      _app(
        AboutScreen(
          updateService: _Updater(AppUpdateState.current, available: false),
        ),
      ),
    );
    await _settle(tester);
    await tester.tap(find.byKey(const ValueKey('about-check-updates')));
    await tester.pumpAndSettle();
    expect(
      find.text(
        "This build can't update itself. Install the newest release instead.",
      ),
      findsOneWidget,
    );
  });

  testWidgets('one page: Open source lists licenses, no privacy tab', (
    tester,
  ) async {
    // Tall, so the document under the identity is laid out.
    tester.view
      ..physicalSize = const Size(412, 2400)
      ..devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(_app(const AboutScreen()));
    await _settle(tester);

    // No tabs: the privacy policy lives in Settings › Privacy and data
    // (P3.10), so Open source is on the page itself.
    expect(find.byKey(const ValueKey('about-tabs')), findsNothing);
    expect(find.byKey(const ValueKey('about-privacy-document')), findsNothing);
    // The bundled components' notices sit behind their own row, so About
    // stays short (owner review of build 2061): no legal text on the page.
    expect(find.byKey(const ValueKey('about-notices-document')), findsNothing);
    expect(find.text('Bundled components'), findsOneWidget);
    expect(find.byKey(const ValueKey('about-all-licences')), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('about-all-licences')));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('about-licences-viewer')), findsOneWidget);
    expect(tester.takeException(), isNull);

    Navigator.of(
      tester.element(find.byKey(const ValueKey('about-licences-viewer'))),
    ).pop();
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('about-bundled-components')));
    await tester.pumpAndSettle();
    expect(
      find.byKey(const ValueKey('about-notices-document')),
      findsOneWidget,
    );
    expect(tester.takeException(), isNull);
  });

  test(
    'the notice retains provenance and desktop limits without a channel claim',
    () {
      // Android publication must not overstate readiness on other platforms.
      expect(buildProvenanceBody, contains('AI assistance'));
      expect(buildProvenanceBody, contains('Android is the primary supported'));
      expect(buildProvenanceBody, isNot(contains('public alpha')));
      expect(buildProvenanceBody, contains('not been hardware-tested'));
    },
  );

  test('the open source notices leave out the maintainer intro and '
      'regeneration steps', () {
    const file = '''# Third-Party Notices

OpenCode Mobile is an independent community project.

**How this file is verified.** Every version below is read from pubspec.lock.

## Bundled components

### Phosphor icon artwork

MIT.

## Package inventory

| Package | License |

## Regenerating this file

Run the tool.
''';
    final shown = aboutNoticesForReaders(file);
    expect(shown, startsWith('## Bundled components'));
    expect(shown, contains('## Package inventory'));
    expect(shown, isNot(contains('independent community project')));
    expect(shown, isNot(contains('How this file is verified')));
    expect(shown, isNot(contains('Regenerating')));
  });
}
