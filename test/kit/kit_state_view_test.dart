// KitStateView v2 (docs/ux-system/kit-api/KitStateView.md "Tests
// required"): the escalation on KitSince's fake clock, the onSlow cap, the
// .error defaults (network fix, Copy details, Report a problem within two
// taps), .missing (explains, offers-enable, cost, prerequisite), the default
// constructor's unchanged actions, the details fold, the finish haptic and
// reduced motion.
import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit_buttons.dart';
import 'package:opencode_mobile/ui/kit/kit_motion.dart';
import 'package:opencode_mobile/ui/kit/kit_notice.dart';
import 'package:opencode_mobile/ui/kit/kit_redact.dart';
import 'package:opencode_mobile/ui/kit/kit_since.dart';
import 'package:opencode_mobile/ui/kit/kit_state_view.dart';
import 'package:opencode_mobile/ui/kit/kit_technical_value.dart';
import 'package:opencode_mobile/ui/kit/kit_text.dart';
import 'package:opencode_mobile/ui/kit/motion/kit_haptics.dart';

const _bodyKey = ValueKey('state-body');
const _fakeKey = 'sk-ant-api03-AbCdEfGhIjKlMnOpQrStUv';
const _fakeToken = 'tok3nAbCdEfGhIjKlMnOp';
const _details =
    'GET /file?path=src failed\n'
    'Authorization: Bearer $_fakeToken\n'
    'provider key $_fakeKey';

/// Now on the clock KitSince reads (package:clock, faked by testWidgets).
DateTime _now() {
  final epoch = DateTime.utc(2000);
  return epoch.add(KitSince.statusOf(epoch).elapsed);
}

Widget _app(Widget child, {bool reduced = false}) => MaterialApp(
  debugShowCheckedModeBanner: false,
  theme: AppTheme.dark(),
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  builder: (context, inner) => MediaQuery(
    data: MediaQuery.of(context).copyWith(disableAnimations: reduced),
    child: inner!,
  ),
  home: Scaffold(body: child),
);

Future<void> _pump(
  WidgetTester tester,
  Widget child, {
  bool settle = true,
  bool reduced = false,
}) async {
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(_app(child, reduced: reduced));
  if (settle) await tester.pumpAndSettle();
}

KitAction _act(String label, [VoidCallback? onPressed]) =>
    KitAction(label: label, onPressed: onPressed ?? () {});

void main() {
  tearDown(() {
    KitReportHook.handler = null;
    KitHaptics.enabled = true;
  });

  group('escalation (fake clock)', () {
    testWidgets('7 s: unchanged; 8 s: "Still waiting after 8 s", the onSlow '
        'ways out, announced once; the title stays', (tester) async {
      final semantics = tester.ensureSemantics();
      final since = _now().subtract(const Duration(seconds: 7));
      var restarted = 0;
      await _pump(
        tester,
        KitStateView(
          icon: AppIconography.cloudOff,
          title: 'Starting OpenCode…',
          body: 'This takes a few seconds.',
          bodyKey: _bodyKey,
          tone: AppStatusTone.progress,
          since: since,
          onSlow: [_act('Try again'), _act('Restart', () => restarted++)],
        ),
      );
      String label() => tester.getSemantics(find.byKey(_bodyKey)).label;
      final labels = [label()];
      expect(find.text('This takes a few seconds.'), findsOneWidget);
      expect(find.text('Restart'), findsNothing);

      final toSlow = KitMotion.escalateAfter - _now().difference(since);
      await tester.pump(toSlow - const Duration(milliseconds: 10));
      labels.add(label());
      expect(find.text('Restart'), findsNothing);

      await tester.pump(const Duration(milliseconds: 10));
      await tester.pumpAndSettle();
      labels.add(label());
      expect(find.text('Still waiting after 8 s'), findsOneWidget);
      expect(find.text('This takes a few seconds.'), findsNothing);
      expect(find.text('Starting OpenCode…'), findsOneWidget);
      expect(find.text('Try again'), findsOneWidget);
      await tester.tap(find.text('Restart'));
      expect(restarted, 1);

      await tester.pump(const Duration(minutes: 3));
      labels.add(label());
      final changes = [
        for (var i = 1; i < labels.length; i++)
          if (labels[i] != labels[i - 1]) labels[i],
      ];
      // The live region reads the title and the new body, once.
      expect(changes, ['Starting OpenCode…\nStill waiting after 8 s']);
      semantics.dispose();
    });

    testWidgets('without since nothing escalates', (tester) async {
      await _pump(
        tester,
        KitStateView(
          icon: AppIconography.cloudOff,
          title: 'Starting OpenCode…',
          body: 'This takes a few seconds.',
          tone: AppStatusTone.progress,
          onSlow: [_act('Restart')],
        ),
      );
      await tester.pump(const Duration(minutes: 1));
      expect(find.text('This takes a few seconds.'), findsOneWidget);
      expect(find.text('Restart'), findsNothing);
    });

    testWidgets('onSlow with three actions asserts', (tester) async {
      await _pump(
        tester,
        KitStateView(
          icon: AppIconography.cloudOff,
          title: 'Waiting',
          since: _now(),
          onSlow: [_act('Try again'), _act('Restart'), _act('Leave it')],
        ),
        settle: false,
      );
      expect(tester.takeException(), isAssertionError);
    });
  });

  group('.error', () {
    testWidgets('network: Try again, Switch server, Copy details; no Report', (
      tester,
    ) async {
      KitReportHook.handler = (_, _) async {};
      await _pump(
        tester,
        KitStateView.error(
          title: 'Couldn’t reach the server',
          error: TimeoutException('t'),
          details: _details,
          retry: _act('Try again'),
          switchServer: _act('Switch server'),
        ),
      );
      final primary = tester.widget<KitButton>(
        find.ancestor(
          of: find.text('Try again'),
          matching: find.byType(KitButton),
        ),
      );
      expect(primary.role, KitButtonRole.primary);
      final secondary = tester.widget<KitButton>(
        find.ancestor(
          of: find.text('Switch server'),
          matching: find.byType(KitButton),
        ),
      );
      expect(secondary.role, KitButtonRole.secondary);
      expect(find.text('Copy details'), findsOneWidget);
      expect(find.text('Report a problem'), findsNothing);
    });

    testWidgets('other with a handler: Copy details and Report a problem; one '
        'tap reports once, redacted, with the source', (tester) async {
      final reports = <KitReport>[];
      KitReportHook.handler = (_, report) async => reports.add(report);
      await _pump(
        tester,
        const KitStateView.error(
          title: 'Couldn’t load permissions',
          error: FormatException('bad'),
          details: _details,
          reportSource: 'saved-permissions',
        ),
      );
      expect(find.text('Copy details'), findsOneWidget);
      await tester.tap(find.text('Report a problem'));
      await tester.pumpAndSettle();
      expect(reports, hasLength(1));
      final report = reports.single;
      expect(report.source, 'saved-permissions');
      expect(report.title, 'Couldn’t load permissions');
      expect(report.errorType, 'FormatException');
      expect(report.details, isNot(contains(_fakeToken)));
      expect(report.details, isNot(contains('AbCdEfGhIjKlMnOp')));
      expect(report.details, contains('GET /file?path=src failed'));
    });

    group('no handler', () {
      late List<MethodCall> platform;
      late List<Map<Object?, Object?>> announcements;

      setUp(() {
        platform = [];
        announcements = [];
        final messenger =
            TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
        messenger.setMockMethodCallHandler(SystemChannels.platform, (
          call,
        ) async {
          platform.add(call);
          return null;
        });
        messenger.setMockDecodedMessageHandler<dynamic>(
          SystemChannels.accessibility,
          (message) async {
            final map = message as Map<Object?, Object?>;
            if (map['type'] == 'announce') announcements.add(map);
            return null;
          },
        );
      });

      tearDown(() {
        final messenger =
            TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
        messenger.setMockMethodCallHandler(SystemChannels.platform, null);
        messenger.setMockDecodedMessageHandler<dynamic>(
          SystemChannels.accessibility,
          null,
        );
      });

      testWidgets('no Report; Copy details copies the redacted text, says '
          'Copied once, no SnackBar', (tester) async {
        await _pump(
          tester,
          const KitStateView.error(
            title: 'Couldn’t load files',
            error: FormatException('x'),
            details: _details,
            copyDetailsKey: ValueKey('files-copy-details'),
          ),
        );
        expect(find.text('Report a problem'), findsNothing);
        await tester.tap(find.byKey(const ValueKey('files-copy-details')));
        await tester.pumpAndSettle();
        final copies = platform.where((c) => c.method == 'Clipboard.setData');
        expect(copies, hasLength(1));
        final text = (copies.single.arguments as Map)['text'] as String;
        expect(text, KitRedact.text(_details));
        expect(text, isNot(contains(_fakeToken)));
        expect(announcements, hasLength(1));
        expect((announcements.single['data'] as Map)['message'], 'Copied');
        expect(find.byType(SnackBar), findsNothing);
      });

      testWidgets('progress to ok gives the finish haptic once, and none '
          'with Vibration off', (tester) async {
        Future<int> finish() async {
          final tone = ValueNotifier(AppStatusTone.progress);
          await _pump(
            tester,
            ValueListenableBuilder(
              valueListenable: tone,
              builder: (_, value, _) => KitStateView(
                icon: AppIconography.check,
                title: value == AppStatusTone.ok ? 'Ready' : 'Installing…',
                tone: value,
              ),
            ),
          );
          platform.clear();
          tone.value = AppStatusTone.ok;
          await tester.pumpAndSettle();
          tone.value = AppStatusTone.ok;
          await tester.pumpAndSettle();
          return platform
              .where((c) => c.method == 'HapticFeedback.vibrate')
              .length;
        }

        expect(await finish(), 1);
        await tester.pumpWidget(const SizedBox.shrink());
        KitHaptics.enabled = false;
        expect(await finish(), 0);
      });
    });
  });

  group('.missing', () {
    testWidgets('explains: no primary, the body is why', (tester) async {
      await _pump(
        tester,
        const KitStateView.missing(
          capability: 'voice.model',
          title: 'Voice needs a model',
          why: 'Voice runs on this phone once a model is downloaded.',
          bodyKey: _bodyKey,
        ),
      );
      expect(find.byType(KitButton), findsNothing);
      expect(
        tester.widget<KitText>(find.byKey(_bodyKey)).text,
        'Voice runs on this phone once a model is downloaded.',
      );
    });

    testWidgets('offers-enable: the primary is enable, the cost above it', (
      tester,
    ) async {
      var enabled = 0;
      await _pump(
        tester,
        KitStateView.missing(
          capability: 'voice.model',
          title: 'Voice needs a model',
          why: 'Voice runs on this phone once a model is downloaded.',
          enable: _act('Download voice model', () => enabled++),
          enableKey: const ValueKey('voice-enable'),
          cost: const ['About 208 MB', 'about 4 min'],
        ),
      );
      final button = tester.widget<KitButton>(
        find.byKey(const ValueKey('voice-enable')),
      );
      expect(button.role, KitButtonRole.primary);
      final cost = find.byType(KitNotice);
      expect(cost, findsOneWidget);
      expect(
        tester.getBottomLeft(cost).dy,
        lessThanOrEqualTo(
          tester.getTopLeft(find.byKey(const ValueKey('voice-enable'))).dy,
        ),
      );
      await tester.tap(find.byKey(const ValueKey('voice-enable')));
      expect(enabled, 1);
    });

    test('a prerequisite without enable asserts (G37)', () {
      expect(
        () => KitStateView.missing(
          capability: 'termux',
          title: 'Set up Termux first',
          why: 'Termux runs the server on this phone.',
          prerequisite: true,
        ),
        throwsAssertionError,
      );
    });
  });

  testWidgets('the default constructor with tone failure adds no defaults', (
    tester,
  ) async {
    KitReportHook.handler = (_, _) async {};
    await _pump(
      tester,
      KitStateView(
        icon: AppIconography.error,
        title: 'Couldn’t load',
        tone: AppStatusTone.failure,
        details: _details,
        primary: _act('Try again'),
      ),
    );
    expect(find.byType(KitButton), findsOneWidget);
    expect(find.text('Copy details'), findsNothing);
    expect(find.text('Report a problem'), findsNothing);
  });

  testWidgets('the details fold holds notes, values, text and child; the '
      'old keys still find it', (tester) async {
    await _pump(
      tester,
      const KitStateView(
        icon: AppIconography.error,
        title: 'Not answering',
        details: 'connection refused',
        detailNotes: ['Check the server is running.'],
        detailValues: [KitTechnicalValue('Address', 'http://127.0.0.1:4096')],
        detailsChild: Text('live log'),
      ),
    );
    expect(find.text('Check the server is running.'), findsNothing);
    await tester.tap(find.byKey(const ValueKey('kit-state-details')));
    await tester.pumpAndSettle();
    expect(find.text('Check the server is running.'), findsOneWidget);
    expect(find.textContaining('127.0.0.1:4096'), findsWidgets);
    expect(find.text('live log'), findsOneWidget);
    expect(
      find.descendant(
        of: find.byKey(const ValueKey('kit-state-details-text')),
        matching: find.textContaining('connection refused'),
      ),
      findsWidgets,
    );
    await tester.tap(find.byKey(const ValueKey('kit-state-details')));
    await tester.pumpAndSettle();
    expect(find.text('live log'), findsNothing);
  });

  testWidgets('reduced motion settles after one pump (G8)', (tester) async {
    await _pump(
      tester,
      KitStateView.error(
        title: 'Couldn’t load',
        details: _details,
        retry: _act('Try again'),
      ),
      settle: false,
      reduced: true,
    );
    await tester.pump();
    expect(tester.hasRunningAnimations, isFalse);
    expect(tester.binding.hasScheduledFrame, isFalse);
  });

  group('calm (vision 2026-10-20)', () {
    testWidgets('the words in the middle, the main action at the bottom, no '
        'icon tile', (tester) async {
      var started = 0;
      var shown = 0;
      await _pump(
        tester,
        KitStateView.calm(
          title: 'Nothing running',
          body: 'Start a conversation, or open one.',
          primary: KitAction(
            label: 'New conversation',
            onPressed: () => started++,
          ),
          tertiary: [
            KitAction(
              label: 'Show all conversations',
              onPressed: () => shown++,
            ),
          ],
        ),
      );
      expect(find.text('Nothing running'), findsOneWidget);
      expect(find.text('Start a conversation, or open one.'), findsOneWidget);
      expect(find.byKey(const ValueKey('kit-state-icon')), findsNothing);
      expect(
        find.byKey(const ValueKey('kit-state-illustration')),
        findsNothing,
      );
      final words = tester.getCenter(find.text('Nothing running'));
      final main = tester.getRect(find.text('New conversation'));
      // The main action sits low, in thumb reach; the words above the middle
      // of the room left over.
      expect(main.top, greaterThan(915 * .75));
      expect(words.dy, lessThan(main.top / 2 + 40));
      expect(words.dx, closeTo(412 / 2, 2));
      await tester.tap(find.text('New conversation'));
      await tester.tap(find.text('Show all conversations'));
      expect((started, shown), (1, 1));
    });

    testWidgets('inline: centred, the first quiet action under the words', (
      tester,
    ) async {
      await _pump(
        tester,
        ListView(
          children: [
            KitStateView.calm(
              title: 'No projects opened',
              body: 'Projects you open appear here.',
              size: KitStateSize.inline,
              tertiary: [KitAction(label: 'Try again', onPressed: () {})],
            ),
          ],
        ),
      );
      expect(find.text('No projects opened'), findsOneWidget);
      expect(find.text('Try again'), findsOneWidget);
      expect(
        tester.getTopLeft(find.text('Try again')).dy,
        greaterThan(tester.getBottomLeft(find.text('No projects opened')).dy),
      );
    });

    testWidgets('large text on a narrow phone never breaks a word', (
      tester,
    ) async {
      tester.view.physicalSize = const Size(320, 693);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      await tester.pumpWidget(
        MediaQuery(
          data: const MediaQueryData(
            size: Size(320, 693),
            textScaler: TextScaler.linear(2),
          ),
          child: _app(const KitStateView.calm(title: 'No conversations yet')),
        ),
      );
      await tester.pumpAndSettle();
      final text = tester.widget<Text>(find.text('No conversations yet'));
      final box = tester.getSize(find.text('No conversations yet'));
      final painter = TextPainter(
        text: TextSpan(text: 'conversations', style: text.style),
        textDirection: TextDirection.ltr,
      )..layout();
      expect(box.width, greaterThanOrEqualTo(painter.width - .5));
      painter.dispose();
    });
  });
}
