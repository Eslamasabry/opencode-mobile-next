// Phone setup's Termux host (programme P1.3): the old Termux wizard's steps
// as the v2 checklist. Behaviour moved from the deleted
// termux_setup_screen_test.dart: the person steps (get Termux, allow it),
// the install that follows by itself, starting an OpenCode Termux already
// has instead of downloading it again, a failure with Continue setup, and a
// launch that timed out but started anyway.
import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/termux_host_setup.dart';
import 'package:opencode_mobile/termux/bridge.dart';
import 'package:opencode_mobile/ui/screens/phone_setup/phone_setup_termux_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'support/termux_channel_fixture.dart';

final _l10n = lookupAppLocalizations(const Locale('en'));

void main() {
  late TermuxChannelFixture termux;
  late MemoryProfileStore store;
  late FakePhoneConnection connection;
  final opened = <String>[];
  final copied = <String>[];

  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    store = MemoryProfileStore(prefs: await SharedPreferences.getInstance());
    connection = FakePhoneConnection(store);
    termux = TermuxChannelFixture()..install();
    opened.clear();
    copied.clear();
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(SystemChannels.platform, (call) async {
          if (call.method == 'Clipboard.setData') {
            copied.add((call.arguments as Map)['text'] as String);
          }
          return null;
        });
    addTearDown(() {
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(SystemChannels.platform, null);
      connection.dispose();
    });
  });

  Future<void> pump(WidgetTester tester, {int frames = 6}) async {
    for (var i = 0; i < frames; i++) {
      await tester.pump(const Duration(milliseconds: 100));
    }
  }

  Future<void> mount(
    WidgetTester tester, {
    TermuxHostJob job = TermuxHostJob.install,
    bool firstSetup = true,
  }) async {
    tester.view
      ..physicalSize = const Size(412, 1400)
      ..devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          bootstrapProvider.overrideWithValue(AppBootstrap(store)),
          connProvider.overrideWithValue(connection),
        ],
        child: MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          routes: {'/home': (_) => const Scaffold(body: Text('App home'))},
          home: PhoneSetupTermuxScreen(
            job: job,
            firstSetup: firstSetup,
            openLink: (_, url) async => opened.add(url),
          ),
        ),
      ),
    );
    await pump(tester);
  }

  Future<void> unmount(WidgetTester tester) async {
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pump(const Duration(seconds: 2));
  }

  void resumeFromElsewhere(WidgetTester tester) {
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.inactive);
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.paused);
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
  }

  testWidgets('without Termux the first row waits on the person; coming back '
      'from the download page checks again', (tester) async {
    termux
      ..termuxInstalled = false
      ..permissionGranted = false;
    await mount(tester);
    expect(find.text(_l10n.phoneSetupProgressTitle), findsOneWidget);
    final get = find.byKey(const ValueKey('phone-setup-termux-get'));
    expect(get, findsOneWidget);
    expect(
      find.textContaining(_l10n.e7SetupInstallTermuxDetail),
      findsOneWidget,
    );
    await tester.ensureVisible(get);
    await tester.tap(get);
    await pump(tester);
    expect(opened, [termuxDownloadUrl]);
    // Nothing ran in Termux: there is no Termux yet.
    expect(termux.methods.toSet(), {'getCapabilities'});

    termux.termuxInstalled = true;
    resumeFromElsewhere(tester);
    await pump(tester);
    expect(
      find.byKey(const ValueKey('phone-setup-termux-allow')),
      findsOneWidget,
    );
    expect(find.textContaining(_l10n.phoneSetupTermuxAllowHow), findsOneWidget);
    await unmount(tester);
  });

  testWidgets('Allow copies the unlock line and opens Termux; back from it, '
      'the install starts by itself and ends in the app', (tester) async {
    termux.permissionGranted = false;
    await mount(tester);
    final allow = find.byKey(const ValueKey('phone-setup-termux-allow'));
    await tester.ensureVisible(allow);
    await tester.tap(allow);
    await pump(tester);
    expect(copied, [TermuxBridge.unlockCommand]);
    expect(
      termux.methods,
      containsAllInOrder(['requestRunCommandPermission', 'openTermux']),
    );
    expect(termux.launchCalls, 0, reason: 'nothing installs before return');

    resumeFromElsewhere(tester);
    await pump(tester, frames: 12);
    expect(termux.bridgeChecks, greaterThan(0));
    expect(termux.launchCalls, 1);
    // The manager's own words on the Linux row while it works.
    expect(find.textContaining('Setting up Ubuntu'), findsOneWidget);
    // A profile with a generated password was saved for the new server.
    expect(store.saved.single.baseUrl, TermuxBridge.managedServerUrl);
    expect(store.saved.single.password, isNotEmpty);

    termux.statusOutput = termuxSnapshot(
      phase: 'ready',
      version: termux.runtime.pinnedVersion,
      runtime: termux.runtime,
    );
    await pump(tester, frames: 15);
    expect(connection.connectCalls, 1);
    expect(find.text('App home'), findsOneWidget);
    await unmount(tester);
  });

  testWidgets('an OpenCode Termux already has is started, not downloaded '
      'again', (tester) async {
    termux.inventoryOutput =
        'ubuntu=installed\nversion=2.1.1\nruntime=opencode2\n';
    store.saved.add(
      ServerProfile(
        id: 'phone',
        name: 'This phone',
        baseUrl: TermuxBridge.managedServerUrl,
        password: 'synthetic-test-secret',
      ),
    );
    await mount(tester);
    await pump(tester, frames: 15);
    expect(termux.launchCalls, 0);
    expect(termux.restartCalls, 1);
    expect(connection.connectCalls, 1);
    expect(find.text('App home'), findsOneWidget);
    await unmount(tester);
  });

  testWidgets('a failed install is a failed row with Continue setup, which '
      'runs it again', (tester) async {
    await mount(tester);
    await pump(tester, frames: 12);
    expect(termux.launchCalls, 1);
    termux.statusOutput = termuxSnapshot(
      phase: 'failed',
      runtime: termux.runtime,
      message: 'OpenCode install failed',
      log: '[oc] Installing OpenCode\n[oc] ERROR: npm could not install\n',
    );
    await pump(tester, frames: 15);
    final resume = find.byKey(const Key('setup-progress-continue'));
    expect(resume, findsOneWidget);
    // The script's words are under Details, not the row's (no raw errors).
    expect(find.textContaining('npm could not install'), findsNothing);
    final details = find.byKey(const Key('setup-progress-details'));
    await tester.ensureVisible(details);
    await tester.tap(details);
    await pump(tester, frames: 4);
    expect(find.textContaining('npm could not install'), findsWidgets);

    termux.statusOutput = null;
    await tester.ensureVisible(resume);
    await tester.tap(resume);
    await pump(tester, frames: 12);
    expect(termux.launchCalls, 2);
    await unmount(tester);
  });

  testWidgets('a launch that timed out follows the setup Termux started', (
    tester,
  ) async {
    termux.pendingLaunch = Completer<Map<String, Object>>();
    await mount(tester);
    await pump(tester, frames: 4);
    termux.statusOutput = termuxSnapshot(
      phase: 'installing_opencode',
      runtime: termux.runtime,
      message: 'Installing OpenCode',
    );
    termux.pendingLaunch!.completeError(
      PlatformException(code: 'command_timeout', message: 'Timed out'),
    );
    await pump(tester, frames: 8);
    // Not a failure: the manager's journal says it is installing.
    expect(find.byKey(const Key('setup-progress-continue')), findsNothing);
    expect(find.textContaining('Installing OpenCode'), findsOneWidget);
    await unmount(tester);
  });

  testWidgets('an update ends back where it was opened', (tester) async {
    tester.view
      ..physicalSize = const Size(412, 1400)
      ..devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    termux.inventoryOutput =
        'ubuntu=installed\nversion=2.1.1\nruntime=opencode2\n';
    store.saved.add(
      ServerProfile(
        id: 'phone',
        name: 'This phone',
        baseUrl: TermuxBridge.managedServerUrl,
        password: 'synthetic-test-secret',
      ),
    );
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          bootstrapProvider.overrideWithValue(AppBootstrap(store)),
          connProvider.overrideWithValue(connection),
        ],
        child: MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: Builder(
            builder: (context) => Scaffold(
              body: Center(
                child: TextButton(
                  onPressed: () =>
                      openPhoneSetupTermux(context, job: TermuxHostJob.update),
                  child: const Text('This phone'),
                ),
              ),
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.text('This phone'));
    await pump(tester, frames: 8);
    expect(find.text(_l10n.phoneSetupTermuxUpdatingTitle), findsOneWidget);
    expect(termux.launchCalls, 1);
    termux.statusOutput = termuxSnapshot(
      phase: 'ready',
      version: termux.runtime.pinnedVersion,
      runtime: termux.runtime,
    );
    await pump(tester, frames: 30);
    expect(find.byType(PhoneSetupTermuxScreen), findsNothing);
    expect(find.text('This phone'), findsOneWidget);
    await unmount(tester);
  });
}
