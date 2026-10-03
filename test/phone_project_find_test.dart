// "Find projects" in the Open a project sheet's menu (phone storage place):
// the step swaps in place, projects fill in as they are found, one tap opens
// one, and back returns to the folder list (lib/ui/widgets/folder_browser.dart).
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/builtin_folders.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/platform/phone_project_scan.dart';
import 'package:opencode_mobile/platform/phone_storage_folders.dart';
import 'package:opencode_mobile/ui/widgets/folder_browser.dart';

const _root = PhoneStorageFolders.root;

PhoneProject _project(String name, {bool git = false}) => PhoneProject(
  name: name,
  path: '$_root/Documents/$name',
  kind: PhoneProjectKind.node,
  hasGit: git,
);

void main() {
  late List<PhoneProjectScan> scans;
  late List<Duration> limits;
  FolderBrowserChoice? choice;

  Future<void> open(WidgetTester tester, {bool withScan = true}) async {
    tester.view
      ..devicePixelRatio = 1
      ..physicalSize = const Size(412, 915);
    addTearDown(tester.view.reset);
    scans = [];
    limits = [];
    choice = null;
    await tester.pumpWidget(
      MaterialApp(
        supportedLocales: AppLocalizations.supportedLocales,
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        home: Scaffold(
          body: Builder(
            builder: (context) => TextButton(
              onPressed: () async =>
                  choice = await showModalBottomSheet<FolderBrowserChoice>(
                    context: context,
                    isScrollControlled: true,
                    builder: (_) => FolderBrowserSheet(
                      list: (_) async => [
                        const FolderEntry(
                          name: 'demo',
                          path: '/root/projects/demo',
                        ),
                      ],
                      phone: PhoneStoragePlace(
                        list: (_) async => [
                          const FolderEntry(
                            name: 'Documents',
                            path: '$_root/Documents',
                          ),
                        ],
                        ensureAccess: (_) async => true,
                        scan: withScan
                            ? (limit) {
                                limits.add(limit);
                                final scan = PhoneProjectScan.manual();
                                scans.add(scan);
                                return scan;
                              }
                            : null,
                      ),
                    ),
                  ),
              child: const Text('go'),
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.text('go'));
    await tester.pumpAndSettle();
  }

  Future<void> tapKey(WidgetTester tester, String key) async {
    await tester.tap(find.byKey(ValueKey(key)));
    await tester.pumpAndSettle();
  }

  Future<void> choosePhone(WidgetTester tester) async {
    await tapKey(tester, 'folder-browser-places');
    await tapKey(tester, 'place-phone');
  }

  Future<void> openFind(WidgetTester tester) async {
    await tapKey(tester, 'kit-sheet-menu');
    await tester.tap(find.text('Find projects'));
    // The quiet progress line keeps animating while it looks.
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));
  }

  testWidgets('the menu item shows for the phone place only', (tester) async {
    await open(tester);
    await tapKey(tester, 'kit-sheet-menu');
    expect(find.text('Find projects'), findsNothing);
    await tester.tapAt(const Offset(5, 5));
    await tester.pumpAndSettle();
    await choosePhone(tester);
    await tapKey(tester, 'kit-sheet-menu');
    expect(find.text('Find projects'), findsOneWidget);
  });

  testWidgets('no scanner, no menu item', (tester) async {
    await open(tester, withScan: false);
    await choosePhone(tester);
    await tapKey(tester, 'kit-sheet-menu');
    expect(find.text('Find projects'), findsNothing);
    expect(find.text('Enter a path'), findsOneWidget);
  });

  testWidgets('swaps in place, rows fill in, a tap opens the project', (
    tester,
  ) async {
    await open(tester);
    await choosePhone(tester);
    await openFind(tester);
    expect(find.text('Projects on this phone'), findsOneWidget);
    expect(find.text('Looking…'), findsOneWidget);
    expect(find.text('Documents'), findsNothing);
    expect(limits, [const Duration(seconds: 10)]);

    scans.single.emit(_project('api', git: true));
    await tester.pump();
    scans.single.emit(_project('site'));
    await tester.pump();
    expect(find.text('api'), findsOneWidget);
    expect(find.text('site'), findsOneWidget);
    expect(find.text('Git'), findsOneWidget);
    expect(
      find.byWidgetPredicate(
        (w) =>
            w is RichText &&
            RegExp(r'Node · .*/Documents').hasMatch(w.text.toPlainText()),
      ),
      findsNWidgets(2),
    );

    scans.single.finish(PhoneScanEnd.completed);
    await tester.pumpAndSettle();
    expect(find.text('2 found'), findsOneWidget);
    expect(find.text('Looking…'), findsNothing);

    await tester.tap(find.text('api'));
    await tester.pumpAndSettle();
    expect(choice, isA<FolderBrowserOpen>());
    expect((choice! as FolderBrowserOpen).path, '$_root/Documents/api');
  });

  testWidgets('nothing found says so and what to do next', (tester) async {
    await open(tester);
    await choosePhone(tester);
    await openFind(tester);
    scans.single.finish(PhoneScanEnd.completed);
    await tester.pumpAndSettle();
    expect(find.text('No projects found'), findsOneWidget);
    expect(find.text('Browse folders or start a New project.'), findsOneWidget);
    expect(find.text('0 found'), findsNothing);
  });

  testWidgets('the time cap says so and Look deeper scans for 30 seconds', (
    tester,
  ) async {
    await open(tester);
    await choosePhone(tester);
    await openFind(tester);
    scans.single.emit(_project('api'));
    scans.single.finish(PhoneScanEnd.timedOut);
    await tester.pumpAndSettle();
    expect(find.text('Stopped after 10 seconds · 1 found'), findsOneWidget);
    await tester.tap(find.text('Look deeper'));
    await tester.pump();
    expect(limits, [const Duration(seconds: 10), const Duration(seconds: 30)]);
    expect(find.text('Looking…'), findsOneWidget);
    expect(find.text('api'), findsNothing);
    scans.last.emit(_project('late'));
    scans.last.finish(PhoneScanEnd.timedOut);
    await tester.pumpAndSettle();
    expect(find.text('Stopped after 30 seconds · 1 found'), findsOneWidget);
    expect(find.text('Look deeper'), findsNothing);
  });

  testWidgets('back cancels the scan and returns to the folder list', (
    tester,
  ) async {
    await open(tester);
    await choosePhone(tester);
    await openFind(tester);
    scans.single.emit(_project('api'));
    await tester.pump();
    await tester.tap(find.byKey(const ValueKey('phone-scan-back')));
    await tester.pumpAndSettle();
    expect(scans.single.cancelled, isTrue);
    expect(find.text('Projects on this phone'), findsNothing);
    expect(find.text('Documents'), findsOneWidget);
    expect(find.text('Internal storage'), findsOneWidget);
  });

  testWidgets('a finished look is kept for the sheet and shown again', (
    tester,
  ) async {
    await open(tester);
    await choosePhone(tester);
    await openFind(tester);
    scans.single.emit(_project('api'));
    scans.single.finish(PhoneScanEnd.completed);
    await tester.pumpAndSettle();
    await tapKey(tester, 'phone-scan-back');
    await openFind(tester);
    expect(scans.length, 1);
    expect(find.text('api'), findsOneWidget);
    expect(find.text('1 found'), findsOneWidget);
  });
}
