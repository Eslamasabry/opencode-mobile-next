import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/app_exit_recovery.dart';
import 'package:opencode_mobile/domain/app_diagnostics_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/platform/app_exit.dart';
import 'package:opencode_mobile/state/background_pause_notice.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/widgets/app_connection_status.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'support/fake_pause_gateway.dart';

class _Store extends ProfileStore {
  _Store({required super.prefs});

  @override
  List<ServerProfile> get profiles => const [];
}

/// The app-exit notice with a fixed answer, so the two "Android stopped it"
/// lines can meet in the one status slot.
class _Recovery extends AppExitRecovery {
  _Recovery(this._exit) : super(bridge: AppLifecycleBridge());

  AppExitNotice? _exit;

  @override
  AppExitNotice? get notice => _exit;

  @override
  void dismiss() {
    _exit = null;
    notifyListeners();
  }
}

final _en = lookupAppLocalizations(const Locale('en'));

/// Today at 3:10 PM, local: the notice then says "at 3:10 PM".
DateTime todayAt1510() {
  final now = DateTime.now();
  return DateTime(now.year, now.month, now.day, 15, 10);
}

/// The real app status scope (as main.dart hosts it above every page) on a
/// plain screen, with the pause notice on [gateway].
Future<BackgroundPauseNotice> mountScope(
  WidgetTester tester,
  FakePauseGateway gateway, {
  AppExitNotice? exit,
  Locale locale = const Locale('en'),
}) async {
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  final notice = BackgroundPauseNotice(
    gateway: gateway,
    preferences: prefs,
    refreshOnForeground: false,
  );
  final controller = ConnectionController(_Store(prefs: prefs));
  final recovery = _Recovery(exit);
  addTearDown(() {
    notice.dispose();
    controller.dispose();
    recovery.dispose();
  });
  final navigator = GlobalKey<NavigatorState>();
  await tester.pumpWidget(
    ProviderScope(
      overrides: [
        connProvider.overrideWithValue(controller),
        appExitRecoveryProvider.overrideWithValue(recovery),
        backgroundPauseNoticeProvider.overrideWithValue(notice),
      ],
      child: MaterialApp(
        navigatorKey: navigator,
        theme: AppTheme.light(),
        locale: locale,
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        builder: (context, child) => AppConnectionStatusScope(
          controller: controller,
          navigatorKey: navigator,
          child: child!,
        ),
        home: const KitScreen(body: SizedBox()),
      ),
    ),
  );
  await tester.pump();
  return notice;
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('the pause notice in the app status line', () {
    testWidgets('nothing shows while the connection runs, is off, or the '
        'phone cannot tell', (tester) async {
      for (final state in [
        BackgroundPauseState.unsupported,
        running,
        const BackgroundPauseState(supported: true),
      ]) {
        await mountScope(tester, FakePauseGateway(state));
        expect(
          find.byKey(const ValueKey('background-pause-notice')),
          findsNothing,
        );
      }
    });

    testWidgets('each reason says why in one line, with Resume named for '
        'what it starts', (tester) async {
      final at = todayAt1510();
      final cases = {
        BackgroundPauseReason.timeLimit: _en.backgroundPauseTimeLimit(
          'at 3:10 PM',
        ),
        BackgroundPauseReason.batteryRestricted: _en.backgroundPauseRestricted,
        BackgroundPauseReason.userStopped: _en.backgroundPauseUserStopped(
          'at 3:10 PM',
        ),
        BackgroundPauseReason.interrupted: _en.backgroundPauseInterrupted,
      };
      for (final MapEntry(key: reason, value: words) in cases.entries) {
        await mountScope(tester, FakePauseGateway(pausedFor(reason, at: at)));
        expect(find.text(words), findsOneWidget, reason: reason.name);
        expect(find.text(_en.backgroundPauseConsequence), findsOneWidget);
        expect(find.text(_en.backgroundPauseResume), findsOneWidget);
      }
      expect(
        _en.backgroundPauseTimeLimit('at 3:10 PM'),
        'Android paused the background connection at 3:10 PM to save '
        'battery.',
      );
    });

    testWidgets('Resume works through the gateway once, shows that it is '
        'resuming, and the notice goes away once Android confirms', (
      tester,
    ) async {
      final gateway = FakePauseGateway(
        pausedFor(BackgroundPauseReason.timeLimit),
      )..gate = Completer();
      await mountScope(tester, gateway);
      await tester.tap(find.text(_en.backgroundPauseResume));
      await tester.pump();
      expect(find.text(_en.backgroundPauseResuming), findsOneWidget);
      // A second tap joins the first; Android is asked once.
      await tester.tap(find.text(_en.backgroundPauseResume));
      await tester.pump();
      expect(gateway.resumeCalls, 1);

      gateway.gate!.complete(const BackgroundResumeResult(running));
      await tester.pumpAndSettle();
      expect(
        find.byKey(const ValueKey('background-pause-notice')),
        findsNothing,
      );
      expect(
        tester.takeAnnouncements().map((a) => a.message),
        contains(_en.backgroundPauseResumed),
      );
    });

    testWidgets('a resume Android does not confirm keeps the pause and '
        'offers Keep running in the background, Resume again under More', (
      tester,
    ) async {
      final paused = pausedFor(BackgroundPauseReason.batteryRestricted);
      final gateway = FakePauseGateway(paused)
        ..answer = () => BackgroundResumeResult(
          paused,
          error: DiagnosticsError.resumeFailed,
        );
      await mountScope(tester, gateway);
      await tester.tap(find.text(_en.backgroundPauseResume));
      await tester.pump();
      await tester.pump();
      expect(find.text(_en.backgroundPauseResumeFailed), findsOneWidget);
      expect(find.text(_en.backgroundPauseResumeFailedNext), findsOneWidget);
      expect(
        find.widgetWithText(KitButton, _en.backgroundPauseOpenKeepRunning),
        findsOneWidget,
      );
      expect(gateway.backgroundPause.paused, isTrue);
      // The way to try again stays one menu away.
      expect(find.text(_en.backgroundPauseResume), findsNothing);
      await tester.tap(find.byTooltip(_en.kitMore));
      await tester.pumpAndSettle();
      expect(find.text(_en.backgroundPauseResume), findsOneWidget);
    });

    testWidgets('busy (a permission prompt already deciding) is not a '
        'failure: the plain Resume stays', (tester) async {
      final paused = pausedFor(BackgroundPauseReason.timeLimit);
      final gateway = FakePauseGateway(paused)
        ..answer = () =>
            BackgroundResumeResult(paused, error: DiagnosticsError.busy);
      await mountScope(tester, gateway);
      await tester.tap(find.text(_en.backgroundPauseResume));
      await tester.pump();
      await tester.pump();
      expect(find.text(_en.backgroundPauseResumeFailed), findsNothing);
      expect(find.text(_en.backgroundPauseResume), findsOneWidget);
    });

    testWidgets('when Android allows no resume, the action is Keep running '
        'in the background', (tester) async {
      await mountScope(
        tester,
        FakePauseGateway(
          pausedFor(BackgroundPauseReason.batteryRestricted, canResume: false),
        ),
      );
      expect(find.text(_en.backgroundPauseResume), findsNothing);
      expect(find.text(_en.backgroundPauseOpenKeepRunning), findsOneWidget);
    });

    testWidgets('one notice at a time: the app exit first, the pause once it '
        'is dismissed', (tester) async {
      await mountScope(
        tester,
        FakePauseGateway(pausedFor(BackgroundPauseReason.userStopped)),
        exit: AppExitNotice(
          kind: AppExitKind.forceStop,
          at: DateTime.now().toUtc(),
          teamStopped: false,
        ),
      );
      expect(find.byKey(const ValueKey('app-exit-notice')), findsOneWidget);
      expect(
        find.byKey(const ValueKey('background-pause-notice')),
        findsNothing,
      );
      expect(find.byType(KitStatusLine), findsOneWidget);

      await tester.tap(find.byKey(const ValueKey('kit-status-dismiss')));
      await tester.pump();
      await tester.pump(const Duration(seconds: 1));
      expect(find.byKey(const ValueKey('app-exit-notice')), findsNothing);
      expect(
        find.byKey(const ValueKey('background-pause-notice')),
        findsOneWidget,
      );
    });

    testWidgets('Arabic says it in Arabic', (tester) async {
      await mountScope(
        tester,
        FakePauseGateway(pausedFor(BackgroundPauseReason.interrupted)),
        locale: const Locale('ar'),
      );
      final ar = lookupAppLocalizations(const Locale('ar'));
      expect(find.text(ar.backgroundPauseInterrupted), findsOneWidget);
      expect(find.text(ar.backgroundPauseResume), findsOneWidget);
    });
  });

  test('once the phone server is back, the pause goes before the app-exit '
      'recap; before that, the app exit leads', () {
    const exit = KitStatus(
      kind: KitStatusKind.appStopped,
      icon: AppIconography.restart,
      message: 'exit',
    );
    const paused = KitStatus(
      kind: KitStatusKind.appStopped,
      icon: AppIconography.pause,
      message: 'paused',
    );
    expect(
      KitStatus.highest(
        appStoppedLines(exit: exit, paused: paused, serverBack: false),
      ),
      same(exit),
    );
    expect(
      KitStatus.highest(
        appStoppedLines(exit: exit, paused: paused, serverBack: true),
      ),
      same(paused),
    );
  });

  group('BackgroundPauseNotice', () {
    late SharedPreferences prefs;

    setUp(() async {
      SharedPreferences.setMockInitialValues({});
      prefs = await SharedPreferences.getInstance();
    });

    test('a dismissed pause stays hidden after a restart; a new pause shows '
        'again', () async {
      final at = DateTime.utc(2026, 10, 8, 9);
      final gateway = FakePauseGateway(
        pausedFor(BackgroundPauseReason.timeLimit, at: at),
      );
      final first = BackgroundPauseNotice(
        gateway: gateway,
        preferences: prefs,
        refreshOnForeground: false,
      );
      expect(first.notice, isNotNull);
      first.dismiss();
      expect(first.notice, isNull);
      first.dispose();

      // A cold start reads the same durable pause.
      final restarted = BackgroundPauseNotice(
        gateway: gateway,
        preferences: prefs,
        refreshOnForeground: false,
      );
      addTearDown(restarted.dispose);
      expect(restarted.notice, isNull);

      gateway.setPause(
        pausedFor(
          BackgroundPauseReason.timeLimit,
          at: at.add(const Duration(days: 1)),
        ),
      );
      expect(restarted.notice?.phase, BackgroundPauseNoticePhase.paused);
    });

    test('a failed resume is remembered only for that pause', () async {
      final paused = pausedFor(
        BackgroundPauseReason.interrupted,
        at: DateTime.utc(2026, 10, 8, 9),
      );
      final gateway = FakePauseGateway(paused)
        ..answer = () => BackgroundResumeResult(
          paused,
          error: DiagnosticsError.resumeFailed,
        );
      final notice = BackgroundPauseNotice(
        gateway: gateway,
        preferences: prefs,
        refreshOnForeground: false,
      );
      addTearDown(notice.dispose);
      expect(await notice.resume(), isFalse);
      expect(notice.notice?.phase, BackgroundPauseNoticePhase.failed);
      gateway.setPause(
        pausedFor(
          BackgroundPauseReason.timeLimit,
          at: DateTime.utc(2026, 10, 9, 9),
        ),
      );
      expect(notice.notice?.phase, BackgroundPauseNoticePhase.paused);
    });

    testWidgets('returning to the app re-reads the pause receipt', (
      tester,
    ) async {
      final gateway = FakePauseGateway(running);
      final notice = BackgroundPauseNotice(
        gateway: gateway,
        preferences: prefs,
      );
      addTearDown(notice.dispose);
      // Leaving the app and coming back, one valid step at a time.
      for (final state in const [
        AppLifecycleState.inactive,
        AppLifecycleState.hidden,
        AppLifecycleState.paused,
        AppLifecycleState.hidden,
        AppLifecycleState.inactive,
        AppLifecycleState.resumed,
      ]) {
        tester.binding.handleAppLifecycleStateChanged(state);
      }
      await tester.pump();
      expect(gateway.refreshCalls, 1);
    });
  });
}
