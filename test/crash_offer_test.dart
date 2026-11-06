// E02-S91: the opt-in crash report offer. Once per crash, never after a
// normal exit, off when the setting is off, never sending anything by
// itself; Review and send opens Report a problem with the crash attached,
// and the prefilled GitHub link stays within the app's link ceiling (the
// stack is shortened with a note). Secrets: test/coverage/diag_privacy_test.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/feedback/crash_offer.dart';
import 'package:opencode_mobile/feedback/problem_report.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/platform/app_exit.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/widgets/crash_offer_notice.dart';
import 'package:shared_preferences/shared_preferences.dart';

final _now = DateTime.utc(2026, 11, 6, 12);

AppLaunchReport _launch({
  int? exitReason,
  DateTime? exitAt,
  DateTime? jvmAt,
  int frames = 3,
}) => AppLaunchReport.fromMap({
  if (exitReason != null)
    'exit': {
      'reason': exitReason,
      'timestamp': (exitAt ?? _now.subtract(const Duration(minutes: 5)))
          .millisecondsSinceEpoch,
    },
  if (jvmAt != null)
    'lastCrash': {
      'timestamp': jvmAt.millisecondsSinceEpoch,
      'exceptionClass': 'java.lang.IllegalStateException',
      'message': 'Invalid state',
      'frames': [
        for (var i = 0; i < frames; i++)
          'io.github.eslamasabry.opencode_mobile.MainActivity.step$i'
              '(MainActivity.kt:${100 + i})',
      ],
    },
});

Future<SharedPreferences> _prefs([Map<String, Object> values = const {}]) {
  SharedPreferences.setMockInitialValues(values);
  return SharedPreferences.getInstance();
}

const _device = KeepAliveInfo(
  manufacturer: 'samsung',
  model: 'SM-S918B',
  androidRelease: '15',
  sdk: 35,
);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('which exits are offered', () {
    CrashOfferFacts? from(AppLaunchReport report, [int lastOffered = 0]) =>
        CrashOfferFacts.fromLaunch(
          report,
          lastOfferedMillis: lastOffered,
          now: _now,
        );

    test('nothing after a normal exit, an update, a stop or memory', () {
      expect(from(AppLaunchReport.empty), isNull);
      for (final reason in [
        AndroidExitReason.exitSelf,
        AndroidExitReason.unknown,
        AndroidExitReason.packageUpdated,
        AndroidExitReason.userRequested,
        AndroidExitReason.lowMemory,
      ]) {
        expect(from(_launch(exitReason: reason)), isNull, reason: '$reason');
      }
    });

    test('a crash, a native crash and an ANR are offered', () {
      for (final reason in [
        AndroidExitReason.crash,
        AndroidExitReason.crashNative,
        AndroidExitReason.anr,
      ]) {
        final facts = from(_launch(exitReason: reason));
        expect(facts, isNotNull, reason: '$reason');
        expect(facts!.exceptionType, isNull);
        expect(facts.stack, isEmpty);
      }
    });

    test('the JVM record of the same crash lends its type and stack', () {
      final at = _now.subtract(const Duration(minutes: 5));
      final facts = from(
        _launch(
          exitReason: AndroidExitReason.crash,
          exitAt: at.add(const Duration(seconds: 1)),
          jvmAt: at,
        ),
      )!;
      expect(facts.exceptionType, 'java.lang.IllegalStateException');
      expect(facts.exceptionMessage, 'Invalid state');
      expect(facts.stack, hasLength(3));
      expect(facts.at, at.add(const Duration(seconds: 1)));
    });

    test('a JVM record from another crash does not lend its stack', () {
      final facts = from(
        _launch(
          exitReason: AndroidExitReason.crashNative,
          jvmAt: _now.subtract(const Duration(days: 2)),
        ),
      )!;
      expect(facts.exceptionType, isNull);
      expect(facts.exitReason, 'native crash');
    });

    test('already offered or older than a week: nothing', () {
      final at = _now.subtract(const Duration(hours: 1));
      final report = _launch(exitReason: AndroidExitReason.crash, exitAt: at);
      expect(from(report, at.millisecondsSinceEpoch), isNull);
      expect(
        from(
          _launch(
            jvmAt: _now.subtract(
              CrashReportOffer.maxAge + const Duration(hours: 1),
            ),
          ),
        ),
        isNull,
      );
    });
  });

  group('CrashReportOffer', () {
    test('appears once per crash, not on the next start', () async {
      final prefs = await _prefs();
      final report = _launch(
        exitReason: AndroidExitReason.crash,
        jvmAt: _now.subtract(const Duration(minutes: 5)),
      );
      final first = CrashReportOffer();
      await first.consider(
        report,
        prefs,
        now: _now,
        device: () async => _device,
      );
      expect(first.pending, isNotNull);
      expect(first.pending!.model, 'SM-S918B');
      // The JVM record stays on disk: the next start sees it again.
      final second = CrashReportOffer();
      await second.consider(report, prefs, now: _now);
      expect(second.pending, isNull);
      // A new crash is offered again.
      final third = CrashReportOffer();
      await third.consider(
        _launch(exitReason: AndroidExitReason.anr, exitAt: _now),
        prefs,
        now: _now.add(const Duration(minutes: 1)),
      );
      expect(third.pending, isNotNull);
    });

    test('runs once per process and dismiss ends it', () async {
      final prefs = await _prefs();
      final offer = CrashReportOffer();
      var changes = 0;
      offer.addListener(() => changes++);
      final report = _launch(exitReason: AndroidExitReason.crash);
      await offer.consider(report, prefs, now: _now);
      expect(offer.pending, isNotNull);
      offer.dismiss();
      expect(offer.pending, isNull);
      await offer.consider(report, await _prefs(), now: _now);
      expect(offer.pending, isNull);
      expect(changes, 2);
    });

    test(
      'off in Settings: no offer, and the crash still counts as seen',
      () async {
        final prefs = await _prefs({CrashReportOffer.enabledKey: false});
        expect(CrashReportOffer.enabled(prefs), isFalse);
        final report = _launch(exitReason: AndroidExitReason.crash);
        final off = CrashReportOffer();
        await off.consider(report, prefs, now: _now);
        expect(off.pending, isNull);
        await CrashReportOffer.setEnabled(prefs, true);
        final on = CrashReportOffer();
        await on.consider(report, prefs, now: _now);
        expect(on.pending, isNull);
      },
    );

    test('on by default', () async {
      expect(CrashReportOffer.enabled(await _prefs()), isTrue);
    });
  });

  group('the report', () {
    CrashOfferFacts facts({int frames = 3}) => CrashOfferFacts.fromLaunch(
      _launch(
        exitReason: AndroidExitReason.crash,
        jvmAt: _now.subtract(const Duration(minutes: 5)),
        frames: frames,
      ),
      lastOfferedMillis: 0,
      now: _now,
    )!.withDevice(_device);

    test('carries type, short stack, version, Android and device', () {
      final kit = crashKitReport(
        facts(),
        title: 'The app closed unexpectedly',
        version: '2.1.0+57',
      );
      final report = ProblemReport.build(
        description: '',
        version: '2.1.0+57',
        platform: 'Android',
        error: kit,
      );
      expect(report.title, 'The app closed unexpectedly');
      expect(
        report.whatHappened,
        allOf(
          contains('Type: java.lang.IllegalStateException'),
          contains('Message: Invalid state'),
          contains('Android: 15 (API 35)'),
          contains('Device: samsung SM-S918B'),
          contains('App: 2.1.0+57'),
          contains('MainActivity.step0 (MainActivity.kt:100)'),
        ),
      );
      expect(report.whatHappened, isNot(contains('left out')));
      expect(
        report.link().clipboard,
        ProblemReportClipboard.none,
        reason: 'a short crash fits the link whole',
      );
    });

    test('a long stack is shortened with a note to fit the GitHub link', () {
      final long = facts(frames: 24);
      expect(long.stack, hasLength(24));
      final kit = crashKitReport(long, title: 'Closed', version: '2.1.0+57');
      final report = ProblemReport.build(
        description: 'It closed when I opened a chat.',
        version: '2.1.0+57',
        platform: 'Android',
        error: kit,
        connection: 'OpenCode 2.0.28 · on this phone · connected',
      );
      expect(report.whatHappened, contains('left out to keep the GitHub link'));
      expect(report.whatHappened, contains('Stack (first'));
      final link = report.link();
      expect(link.clipboard, ProblemReportClipboard.none);
      expect(link.uri.toString().length, lessThanOrEqualTo(2048));
      expect(
        link.uri.queryParameters['what-happened'],
        contains('Type: java.lang.IllegalStateException'),
      );
    });

    test('a crash without a JVM record says the stack was not captured', () {
      final kit = crashKitReport(
        CrashOfferFacts(at: _now, exitReason: 'native crash'),
        title: 'Closed',
      );
      expect(kit.errorType, isNull);
      expect(kit.details, contains('Stack: not captured'));
      expect(kit.details, contains('Android: unknown'));
    });

    test('device facts that are not plain words are dropped', () {
      final info = KeepAliveInfo.fromMap({
        'model': 'Pixel 9 <script>',
        'release': '15',
        'sdk': 35,
        'manufacturer': 'Google',
      });
      expect(info.model, isEmpty);
      expect(info.androidRelease, '15');
      expect(info.sdk, 35);
      expect(KeepAliveInfo.fromMap({'model': 'SM-S918B'}).model, 'SM-S918B');
    });
  });

  group('the notice', () {
    Future<void> pump(
      WidgetTester tester,
      CrashReportOffer offer, {
      required void Function(KitReport report) opened,
    }) async {
      await tester.pumpWidget(
        MaterialApp(
          theme: AppTheme.dark(),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: Scaffold(
            body: ListenableBuilder(
              listenable: offer,
              builder: (context, _) {
                final status = crashOfferKitStatus(
                  context,
                  offer,
                  version: () async => '2.1.0+57',
                  open: (context, report) async => opened(report),
                );
                return Column(
                  children: [
                    if (status != null) KitStatusLine.of(status),
                    const Spacer(),
                  ],
                );
              },
            ),
          ),
        ),
      );
    }

    Future<CrashReportOffer> crashed() async {
      final offer = CrashReportOffer();
      await offer.consider(
        _launch(
          exitReason: AndroidExitReason.crash,
          jvmAt: _now.subtract(const Duration(minutes: 5)),
        ),
        await _prefs(),
        now: _now,
      );
      return offer;
    }

    testWidgets('fixed words, Review and send opens the report', (
      tester,
    ) async {
      final offer = await crashed();
      final opened = <KitReport>[];
      await pump(tester, offer, opened: opened.add);
      expect(
        find.text(
          'OpenCode Mobile closed unexpectedly last time. '
          'Send a crash report?',
        ),
        findsOneWidget,
      );
      // The exception never shows as the notice's words.
      expect(find.textContaining('IllegalStateException'), findsNothing);
      expect(find.byTooltip('Not now'), findsOneWidget);
      await tester.tap(find.text('Review and send'));
      await tester.pumpAndSettle();
      expect(opened, hasLength(1));
      expect(opened.single.title, 'The app closed unexpectedly');
      expect(opened.single.errorType, 'java.lang.IllegalStateException');
      expect(offer.pending, isNull);
      expect(find.byKey(const ValueKey('crash-offer-notice')), findsNothing);
    });

    testWidgets('Not now dismisses without opening anything', (tester) async {
      final offer = await crashed();
      final opened = <KitReport>[];
      await pump(tester, offer, opened: opened.add);
      await tester.tap(find.byTooltip('Not now'));
      await tester.pumpAndSettle();
      expect(opened, isEmpty);
      expect(offer.pending, isNull);
      expect(find.text('Review and send'), findsNothing);
    });
  });
}
