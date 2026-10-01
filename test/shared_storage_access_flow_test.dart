import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/platform/storage_access.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/shared_project_roots.dart';
import 'package:opencode_mobile/state/shared_storage_gate.dart';
import 'package:opencode_mobile/ui/screens/shared_storage_access_flow.dart';

final _inApp = ServerProfile(
  id: 'builtin',
  name: 'This phone',
  baseUrl: BuiltinLinux.serverUrl,
  username: BuiltinLinux.serverUsername,
);

final _termux = ServerProfile(
  id: 'termux',
  name: 'Termux',
  baseUrl: 'http://127.0.0.1:4096',
  username: 'opencode',
);

final _remote = ServerProfile(
  id: 'remote',
  name: 'Server',
  baseUrl: 'https://example.test',
  username: 'u',
);

void main() {
  late StorageAccess access;
  late int opened;
  late bool grantOnOpen;
  late SharedStorageOutcome? outcome;

  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    await SharedPreferences.getInstance();
    debugPlatformCapabilities = const PlatformCapabilities(
      platform: TargetPlatform.android,
      isWeb: false,
    );
    access = StorageAccess.notGranted;
    opened = 0;
    grantOnOpen = true;
    outcome = null;
    StorageAccessBridge.statusOverride = () async => access;
    StorageAccessBridge.openOverride = () async {
      opened++;
      if (grantOnOpen) access = StorageAccess.granted;
    };
    SharedStorageGate.termuxStorageOverride = () async => false;
    SharedStorageAccessFlow.confinedRunningOverride = (_) async => false;
  });

  tearDown(() {
    debugPlatformCapabilities = null;
    StorageAccessBridge.statusOverride = null;
    StorageAccessBridge.openOverride = null;
    SharedStorageGate.termuxStorageOverride = null;
    SharedStorageAccessFlow.confinedRunningOverride = null;
    SharedStorageAccessFlow.restartOverride = null;
    SharedProjectRoots.pushOverride = null;
  });

  Future<void> pump(
    WidgetTester tester,
    ServerProfile profile,
    String path, {
    bool appSpace = false,
    bool work = false,
  }) async {
    tester.view
      ..devicePixelRatio = 1
      ..physicalSize = const Size(420, 1400);
    addTearDown(tester.view.reset);
    await tester.pumpWidget(
      MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: Builder(
          builder: (context) => TextButton(
            onPressed: () async =>
                outcome = await SharedStorageAccessFlow.ensure(
                  context,
                  profile,
                  path,
                  offerAppSpace: appSpace,
                  workRunning: work,
                ),
            child: const Text('go'),
          ),
        ),
      ),
    );
    await tester.tap(find.text('go'));
    await tester.pumpAndSettle();
  }

  testWidgets('explains first, then opens the system page, then proceeds', (
    tester,
  ) async {
    await pump(tester, _inApp, '/sdcard/CodeAnything');
    expect(find.text('Allow access to files?'), findsOneWidget);
    expect(find.textContaining('turn it off any time'), findsOneWidget);
    expect(opened, 0, reason: 'nothing is asked before the explanation');
    await tester.tap(find.text('Allow access to files'));
    await tester.pumpAndSettle();
    expect(opened, 1);
    expect(outcome, SharedStorageOutcome.proceed);
  });

  testWidgets('coming back without access says the folder was not opened', (
    tester,
  ) async {
    grantOnOpen = false;
    await pump(tester, _inApp, '/storage/emulated/0/CodeAnything');
    await tester.tap(find.text('Allow access to files'));
    await tester.pumpAndSettle();
    expect(find.text('Folder not opened'), findsOneWidget);
    await tester.tap(find.text('Close'));
    await tester.pumpAndSettle();
    expect(outcome, SharedStorageOutcome.declined);
  });

  testWidgets('Not now declines without opening anything', (tester) async {
    await pump(tester, _inApp, '/sdcard/app');
    await tester.tap(find.text('Not now'));
    await tester.pumpAndSettle();
    expect(opened, 0);
    expect(outcome, SharedStorageOutcome.declined);
  });

  testWidgets('the app project space can be chosen instead', (tester) async {
    await pump(tester, _inApp, '/sdcard/app', appSpace: true);
    await tester.tap(find.text('Use the app’s project space'));
    await tester.pumpAndSettle();
    expect(opened, 0);
    expect(outcome, SharedStorageOutcome.useAppSpace);
  });

  testWidgets(
    'app-private folders, granted access and other servers never ask',
    (tester) async {
      await pump(tester, _inApp, '/root/projects/app');
      expect(find.text('Allow access to files?'), findsNothing);
      expect(outcome, SharedStorageOutcome.proceed);

      access = StorageAccess.granted;
      await pump(tester, _inApp, '/sdcard/app');
      expect(find.text('Allow access to files?'), findsNothing);
      expect(outcome, SharedStorageOutcome.proceed);

      access = StorageAccess.notGranted;
      await pump(tester, _remote, '/sdcard/app');
      expect(find.text('Allow access to files?'), findsNothing);
      expect(outcome, SharedStorageOutcome.proceed);
      expect(opened, 0);
    },
  );

  testWidgets('a Termux server needs Termux storage, not the app\'s', (
    tester,
  ) async {
    await pump(tester, _termux, '/sdcard/CodeAnything');
    expect(find.text('Allow Termux storage?'), findsOneWidget);
    expect(find.text('Allow access to files?'), findsNothing);
    await tester.tap(find.text('Not now'));
    await tester.pumpAndSettle();
    expect(outcome, SharedStorageOutcome.declined);

    SharedStorageGate.termuxStorageOverride = () async => true;
    await pump(tester, _termux, '/sdcard/CodeAnything');
    expect(find.text('Allow Termux storage?'), findsNothing);
    expect(outcome, SharedStorageOutcome.proceed);
  });

  group('a new folder while AI Team is on', () {
    late int restarts;
    late bool confined;
    setUp(() {
      access = StorageAccess.granted;
      restarts = 0;
      confined = true;
      SharedProjectRoots.pushOverride = (_) async {};
      SharedStorageAccessFlow.confinedRunningOverride = (_) async => confined;
      SharedStorageAccessFlow.restartOverride = (_) async {
        restarts++;
        return true;
      };
    });

    testWidgets('asks, restarts, then opens', (tester) async {
      await pump(tester, _inApp, '/sdcard/CodeAnything');
      expect(find.text('Restart to open folder?'), findsOneWidget);
      expect(find.textContaining('pause and carry on'), findsOneWidget);
      expect(restarts, 0);
      await tester.tap(find.text('Restart and open'));
      await tester.pumpAndSettle();
      expect(restarts, 1);
      expect(outcome, SharedStorageOutcome.proceed);
      // Remembered: the same folder never asks again.
      await pump(tester, _inApp, '/storage/emulated/0/CodeAnything');
      expect(find.text('Restart and open'), findsNothing);
      expect(restarts, 1);
      expect(outcome, SharedStorageOutcome.proceed);
    });

    testWidgets('says so when work is running, and Not now opens nothing', (
      tester,
    ) async {
      await pump(tester, _inApp, '/sdcard/Busy', work: true);
      expect(find.textContaining('running right now'), findsOneWidget);
      await tester.tap(find.text('Not now'));
      await tester.pumpAndSettle();
      expect(restarts, 0);
      expect(outcome, SharedStorageOutcome.declined);
    });

    testWidgets('a failed restart does not open the folder', (tester) async {
      SharedStorageAccessFlow.restartOverride = (_) async => false;
      await pump(tester, _inApp, '/sdcard/Fail');
      await tester.tap(find.text('Restart and open'));
      await tester.pumpAndSettle();
      expect(find.text('Restart did not finish'), findsOneWidget);
      await tester.tap(find.text('Close'));
      await tester.pumpAndSettle();
      expect(outcome, SharedStorageOutcome.declined);
    });

    testWidgets('not confined: no sheet', (tester) async {
      confined = false;
      await pump(tester, _inApp, '/sdcard/Plain');
      expect(find.text('Restart and open'), findsNothing);
      expect(restarts, 0);
      expect(outcome, SharedStorageOutcome.proceed);
    });
  });
}
