// This phone (programme P1.5): one page for OpenCode inside the app, with
// the status row, the one act it needs now, and one list. Add tools opens
// phone setup's Customize sheet in add mode, priced with the kit's cost
// line, and installs only the new tools. A saved setup from Termux, which
// the app no longer runs, gets the one notice about it.
import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/builtin_server_recovery.dart';
import 'package:opencode_mobile/builtin/setup/setup_contract.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/this_phone_screen.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/widgets/phone_server_card.dart';

import 'revamp/screen_phone_1_fixtures.dart';

final _l10n = lookupAppLocalizations(const Locale('en'));

/// The parts both hosts show, by key.
const _shared = [
  'this-phone-title',
  'this-phone-state',
  'this-phone-detail',
  'this-phone-connect',
  'this-phone-stop',
  'this-phone-add-tools',
  'this-phone-installed',
  'this-phone-keep-running',
];

final _termuxProfile = ServerProfile(
  id: 'termux',
  name: 'This phone',
  baseUrl: 'http://127.0.0.1:4096',
  password: 'synthetic-test-secret',
  serverVersion: '1.18.29',
);

Future<void> _settle(WidgetTester tester, {int frames = 10}) async {
  for (var i = 0; i < frames; i++) {
    await tester.pump(const Duration(milliseconds: 100));
  }
}

Set<String> _present(WidgetTester tester) => {
  for (final key in _shared)
    if (find.byKey(ValueKey(key)).evaluate().isNotEmpty) key,
};

void main() {
  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (_) async => null,
        );
  });

  Future<void> inApp(WidgetTester tester, {PhoneLinux? linux}) async {
    await pumpPhone(
      tester,
      home: const ThisPhoneScreen(),
      linux: linux ?? PhoneLinux(running: true),
      profiles: [inAppProfile],
      // The fake server never answers a start; the real wait is 90 s.
      readyTimeout: const Duration(milliseconds: 300),
    );
    await _settle(tester);
  }

  String text(WidgetTester tester, String key) => tester
      .widget<RichText>(
        find
            .descendant(
              of: find.byKey(ValueKey(key)),
              matching: find.byType(RichText),
              matchRoot: true,
            )
            .first,
      )
      .text
      .toPlainText();

  // Sweep 3680: the server's card in the first view sat ~9px above the
  // gesture bar. It clears the gesture inset like every list's last row.
  testWidgets('the server card in the first view clears the gesture bar', (
    tester,
  ) async {
    tester.view.padding = const FakeViewPadding(bottom: 102);
    tester.view.viewPadding = const FakeViewPadding(bottom: 102);
    addTearDown(tester.view.resetPadding);
    addTearDown(tester.view.resetViewPadding);
    await inApp(tester);
    final screenBottom =
        tester.view.physicalSize.height / tester.view.devicePixelRatio;
    final inset = 102 / tester.view.devicePixelRatio;
    final card = tester.getRect(
      find.byKey(const ValueKey('this-phone-server')),
    );
    expect(card.bottom, lessThanOrEqualTo(screenBottom - inset));
  });

  testWidgets('nothing on the page is red: Stop and Remove are plain, and '
      'Stop still asks first', (tester) async {
    await inApp(tester);
    expect(
      find.byWidgetPredicate((w) => w is KitButton && w.destructive),
      findsNothing,
    );
    expect(
      find.byWidgetPredicate((w) => w is KitRow && w.destructive),
      findsNothing,
    );
    await tester.ensureVisible(find.byKey(const ValueKey('this-phone-stop')));
    await tester.pump();
    await tester.tap(find.byKey(const ValueKey('this-phone-stop')));
    await tester.pumpAndSettle();
    expect(
      find.byKey(const ValueKey('stop-local-server-confirm-sheet')),
      findsOneWidget,
    );
  });

  testWidgets('in the app: the status, the one act it needs, then one list', (
    tester,
  ) async {
    await inApp(tester);
    expect(find.text(_l10n.setupRuntimeTwo), findsOneWidget);
    expect(text(tester, 'this-phone-state'), _l10n.thisPhoneAnswerRunning);
    expect(
      text(tester, 'this-phone-detail'),
      contains(_l10n.thisPhoneHostInApp),
    );
    expect(_present(tester), _shared.toSet());
    // Every act names what it acts on.
    expect(find.text(_l10n.thisPhoneStop), findsOneWidget);
    expect(find.text(_l10n.thisPhoneAnswerConnect), findsOneWidget);
    // Only the in-app Linux has a terminal and Remove.
    await tester.scrollUntilVisible(
      find.byKey(const ValueKey('this-phone-remove')),
      200,
      scrollable: find.byType(Scrollable).first,
    );
    expect(find.byKey(const ValueKey('this-phone-terminal')), findsOneWidget);
    expect(find.text(_l10n.thisPhoneRemove), findsOneWidget);
    // The technical fold is last, after Remove.
    await tester.scrollUntilVisible(
      find.byKey(const ValueKey('this-phone-details')),
      200,
      scrollable: find.byType(Scrollable).first,
    );
    expect(find.byKey(const ValueKey('termux-procs-row')), findsNothing);
    await unmountPhone(tester);
  });

  testWidgets('the agents row does not say "on this phone" twice', (
    tester,
  ) async {
    await inApp(tester);
    final row = find.byKey(const ValueKey('local-agent-row'));
    await tester.scrollUntilVisible(
      row,
      200,
      scrollable: find.byType(Scrollable).first,
    );
    expect(
      find.descendant(
        of: row,
        matching: find.text(_l10n.settingsV3AgentsOnPhone),
      ),
      findsOneWidget,
    );
    expect(
      find.descendant(of: row, matching: find.text(_l10n.localAgentTitle)),
      findsNothing,
    );
    await unmountPhone(tester);
  });

  // Vision 2026-10-20: one answer in the first view, nothing else up front.
  testWidgets('the answer is short: Restart and Stop are in the first view, '
      'and no mint button while it runs', (tester) async {
    await inApp(tester);
    final view = tester.view.physicalSize / tester.view.devicePixelRatio;
    final answer = tester.getRect(
      find.byKey(const ValueKey('this-phone-state')),
    );
    expect(answer.center.dx, closeTo(view.width / 2, 1));
    expect(
      tester.getRect(find.byKey(const ValueKey('this-phone-title'))).top,
      lessThan(view.height),
    );
    expect(
      tester.getRect(find.byKey(const ValueKey('this-phone-restart'))).top,
      lessThan(view.height),
      reason: 'Restart is in the first view, not below a tall hero',
    );
    expect(
      tester.getRect(find.byKey(const ValueKey('this-phone-stop'))).top,
      lessThan(view.height),
    );
    expect(
      find.byWidgetPredicate(
        (w) => w is KitButton && w.role == KitButtonRole.primary,
      ),
      findsNothing,
    );
    await unmountPhone(tester);
  });

  testWidgets('runtime choices are absent after decommissioning', (
    tester,
  ) async {
    await inApp(tester);
    expect(find.byKey(const ValueKey('this-phone-switch')), findsNothing);
    expect(find.byKey(const ValueKey('this-phone-move-line')), findsNothing);
    await unmountPhone(tester);
  });

  testWidgets('the server log waits folded under Details, last', (
    tester,
  ) async {
    await pumpPhone(
      tester,
      home: const ThisPhoneScreen(),
      linux: PhoneLinux(running: true, log: 'INFO  session created\n'),
      profiles: [inAppProfile],
    );
    await _settle(tester);
    expect(find.byKey(const ValueKey('this-phone-log')), findsNothing);
    final details = find.byKey(const ValueKey('this-phone-details'));
    await tester.scrollUntilVisible(
      details,
      200,
      scrollable: find.byType(Scrollable).first,
    );
    // Clear of the page's end padding, whatever the list's length.
    await tester.ensureVisible(details);
    await _settle(tester);
    await tester.tap(details);
    await _settle(tester);
    expect(find.byKey(const ValueKey('this-phone-log')), findsOneWidget);
    expect(find.textContaining('session created'), findsOneWidget);
    // The panel names the server; "Server log" is not said a second time.
    expect(find.text(_l10n.phoneServerCardVersion('1.18.29')), findsOneWidget);
    expect(find.text(_l10n.builtinServerLogTitle), findsNothing);
    await unmountPhone(tester);
  });

  testWidgets('stopping asks first, then the page offers Start', (
    tester,
  ) async {
    final linux = PhoneLinux(running: true);
    await inApp(tester, linux: linux);
    await tester.ensureVisible(find.byKey(const ValueKey('this-phone-stop')));
    await tester.pump();
    await tester.tap(find.byKey(const ValueKey('this-phone-stop')));
    await _settle(tester);
    expect(linux.running, isTrue, reason: 'the question changes nothing');
    await tester.tap(find.byKey(const ValueKey('confirm-stop-local-server')));
    await _settle(tester);
    expect(linux.running, isFalse);
    expect(text(tester, 'this-phone-state'), _l10n.thisPhoneAnswerOff);
    expect(find.text(_l10n.thisPhoneStart), findsOneWidget);
    await unmountPhone(tester);
  });

  testWidgets('the first Start asks once to keep the server running (P6.7)', (
    tester,
  ) async {
    const lifecycle = MethodChannel('oc/lifecycle');
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    messenger.setMockMethodCallHandler(
      lifecycle,
      (call) async => call.method == 'keepAliveInfo'
          ? {'manufacturer': 'Google', 'batteryOptimizationIgnored': false}
          : null,
    );
    addTearDown(() => messenger.setMockMethodCallHandler(lifecycle, null));
    final linux = PhoneLinux();
    await inApp(tester, linux: linux);
    await tester.tap(find.byKey(const ValueKey('this-phone-start')));
    await _settle(tester);
    expect(linux.running, isTrue);
    expect(
      find.byKey(const ValueKey('phone-consent-batteryExemption')),
      findsOneWidget,
    );
    await tester.tap(find.text('Not now'));
    await _settle(tester);

    // A later start asks nothing.
    await tester.ensureVisible(find.byKey(const ValueKey('this-phone-stop')));
    await tester.pump();
    await tester.tap(find.byKey(const ValueKey('this-phone-stop')));
    await _settle(tester);
    await tester.tap(find.byKey(const ValueKey('confirm-stop-local-server')));
    await _settle(tester);
    await tester.tap(find.byKey(const ValueKey('this-phone-start')));
    await _settle(tester);
    expect(linux.running, isTrue);
    expect(
      find.byKey(const ValueKey('phone-consent-batteryExemption')),
      findsNothing,
    );
    await unmountPhone(tester);
    // A status poll that was in flight at unmount ends on its own, and so
    // does the connection's eight-second "still connecting" grace clock
    // (3d64653c): the fake server never answers the connect.
    await tester.pump(const Duration(seconds: 9));
  });

  testWidgets('Add tools opens Customize in add mode, priced before it '
      'installs, and installs only the new tools', (tester) async {
    var progressOpened = 0;
    PhoneServerCardRoutes.openProgressOverride = (_) async => progressOpened++;
    addTearDown(() => PhoneServerCardRoutes.openProgressOverride = null);
    final engine = await pumpPhone(
      tester,
      home: const ThisPhoneScreen(),
      linux: PhoneLinux(running: true),
      profiles: [inAppProfile],
    );
    await _settle(tester);
    await tester.ensureVisible(
      find.byKey(const ValueKey('this-phone-add-tools')),
    );
    await tester.pump();
    await tester.tap(find.byKey(const ValueKey('this-phone-add-tools')));
    await _settle(tester);
    expect(
      find.byKey(const ValueKey('phone-setup-customize-sheet')),
      findsOneWidget,
    );
    expect(find.text(_l10n.phoneSetupStartAddTitle), findsWidgets);
    await tester.tap(
      find.byKey(const ValueKey('phone-setup-customize-python')),
    );
    await _settle(tester);
    // The kit's cost line, before the button that installs.
    expect(
      find.byKey(const ValueKey('phone-setup-customize-cost')),
      findsOneWidget,
    );
    await tester.tap(find.byKey(const ValueKey('phone-setup-customize-done')));
    await _settle(tester);
    expect(engine.runs, [
      {'python'},
    ]);
    expect(engine.runParams.single, {
      ...SetupJobParams.adding({'python'}),
      'opencode': {'runtime': 'opencode2'},
    });
    expect(progressOpened, 1);
    // The sheet's device look gives up after 5 s off a phone.
    await tester.pump(const Duration(seconds: 6));
    await unmountPhone(tester);
  });

  test('no screen pushes the retired Termux wizard route (P1.3)', () {
    final pushes = <String>[];
    for (final entity in Directory('lib').listSync(recursive: true)) {
      if (entity is! File || !entity.path.endsWith('.dart')) continue;
      final source = entity.readAsStringSync();
      if (source.contains("'/termux-setup'")) pushes.add(entity.path);
    }
    expect(pushes, isEmpty);
    expect(
      File('lib/ui/screens/termux_setup_screen.dart').existsSync(),
      isFalse,
    );
    expect(
      File('lib/ui/screens/builtin_server_screen.dart').existsSync(),
      isFalse,
    );
  });

  testWidgets('a saved setup from Termux gets the one notice, with its two '
      'ways forward, and nothing else about Termux', (tester) async {
    await pumpPhone(
      tester,
      home: const ThisPhoneScreen(),
      linux: PhoneLinux(installed: false, openCode: false),
      profiles: [_termuxProfile],
    );
    await _settle(tester);
    expect(find.byKey(const ValueKey('termux-retired-notice')), findsOneWidget);
    expect(find.text(_l10n.termuxRetiredNotice), findsOneWidget);
    expect(find.text(_l10n.termuxRetiredSetUpLinux), findsOneWidget);
    expect(find.text(_l10n.chatsHomeConnectComputer), findsOneWidget);
    // Nothing else on the page speaks of Termux.
    expect(find.textContaining('Termux'), findsOneWidget);
    await unmountPhone(tester);
  });

  testWidgets('without a Termux setup there is no notice', (tester) async {
    await inApp(tester);
    expect(find.byKey(const ValueKey('termux-retired-notice')), findsNothing);
    await unmountPhone(tester);
  });

  testWidgets('nothing set up: one act, set it up', (tester) async {
    await pumpPhone(
      tester,
      home: const ThisPhoneScreen(),
      linux: PhoneLinux(installed: false, openCode: false),
    );
    await _settle(tester);
    expect(text(tester, 'this-phone-state'), _l10n.thisPhoneAnswerNotSetUp);
    expect(find.text(_l10n.thisPhoneSetUp), findsOneWidget);
    expect(find.byKey(const ValueKey('this-phone-list')), findsNothing);
    await unmountPhone(tester);
  });

  // Vision 2026-10-20, honesty: a restart is promised only when recovery
  // really scheduled one.
  group('a server that stopped by itself', () {
    Future<void> stopped(
      WidgetTester tester,
      BuiltinRecoveryState state,
    ) async {
      final recovery = ValueNotifier(state);
      addTearDown(recovery.dispose);
      await pumpPhone(
        tester,
        home: const ThisPhoneScreen(),
        linux: PhoneLinux(),
        profiles: [inAppProfile],
        overrides: [thisPhoneRecoveryProvider.overrideWithValue(recovery)],
      );
      await _settle(tester);
    }

    testWidgets('with nothing scheduled: why, no promise, Restart now', (
      tester,
    ) async {
      await stopped(
        tester,
        BuiltinRecoveryState(
          phase: BuiltinRecoveryPhase.paused,
          profileId: inAppProfile.id,
        ),
      );
      expect(
        text(tester, 'this-phone-state'),
        _l10n.thisPhoneAnswerStoppedItself,
      );
      expect(find.textContaining('by itself in'), findsNothing);
      expect(find.text(_l10n.thisPhoneAnswerRestartsSoon), findsNothing);
      expect(find.text(_l10n.thisPhoneAnswerRestartNow), findsOneWidget);
      await unmountPhone(tester);
    });

    testWidgets('with a retry scheduled: the countdown, and Restart now', (
      tester,
    ) async {
      await stopped(
        tester,
        BuiltinRecoveryState(
          phase: BuiltinRecoveryPhase.waiting,
          profileId: inAppProfile.id,
          attempts: 1,
          nextAttemptAt: DateTime.now().add(const Duration(seconds: 42)),
        ),
      );
      expect(
        text(tester, 'this-phone-answer-line'),
        _l10n.thisPhoneAnswerRestartsIn(_l10n.thisPhoneAboutSeconds(45)),
      );
      expect(find.text(_l10n.thisPhoneAnswerRestartNow), findsOneWidget);
      await unmountPhone(tester);
    });

    testWidgets('after the retries are used up: it says so', (tester) async {
      await stopped(
        tester,
        BuiltinRecoveryState(
          phase: BuiltinRecoveryPhase.exhausted,
          profileId: inAppProfile.id,
          attempts: 3,
        ),
      );
      expect(text(tester, 'this-phone-failure'), _l10n.thisPhoneAnswerGaveUp);
      await unmountPhone(tester);
    });
  });

  testWidgets('opened as a page it has Back, and the server row names only '
      'where it runs (the version is under Details)', (tester) async {
    await pumpPhone(
      tester,
      home: const SizedBox.expand(key: ValueKey('opener')),
      routes: {thisPhoneRoute: (_) => const ThisPhoneScreen()},
      linux: PhoneLinux(running: true),
      profiles: [inAppProfile],
      readyTimeout: const Duration(milliseconds: 300),
    );
    unawaited(
      openThisPhone(tester.element(find.byKey(const ValueKey('opener')))),
    );
    await _settle(tester);
    expect(find.byKey(const ValueKey('this-phone-back')), findsOneWidget);
    expect(text(tester, 'this-phone-detail'), _l10n.thisPhoneHostInApp);
    expect(find.textContaining('1.18.29'), findsNothing);
    await tester.tap(find.byKey(const ValueKey('this-phone-back')));
    await _settle(tester);
    expect(find.byType(ThisPhoneScreen), findsNothing);
    await unmountPhone(tester);
  });
}
