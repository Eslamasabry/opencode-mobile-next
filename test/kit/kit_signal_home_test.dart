// The Signal style on the Conversations home, as the owner's review of the
// gallery asked (2026-10-30, concept CD): the slip under its row holds only
// the `$` command and the keys; a running row shows its segment timer and
// what it runs; New conversation is a dark ink key with its words; the
// filters are outlined keys of one height; the page title is set larger
// and heavier. Every other style keeps its own look. And the controller
// knows the command a running conversation runs, for that row.
import 'package:clock/clock.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/background/live_background.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/kit/kit_surface_look.dart';
import 'package:opencode_mobile/ui/kit/style/kit_style_theme.dart';
import 'package:opencode_mobile/ui/theme_packs_signal.dart';
import 'package:shared_preferences/shared_preferences.dart';

final _now = DateTime(2026, 10, 30, 11, 6);

Future<void> _pump(
  WidgetTester tester,
  Widget child, {
  KitSurfaceStyle style = KitSurfaceStyle.signal,
}) async {
  await withClock(Clock.fixed(_now), () async {
    await tester.pumpWidget(
      MaterialApp(
        theme: AppTheme.light(),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        builder: (context, app) => KitEffectsScope(
          effects: KitEffects(surfaceStyle: style),
          child: kitStyleTheme(child: app!),
        ),
        home: Scaffold(body: child),
      ),
    );
    await tester.pumpAndSettle();
  });
}

KitRequestCard _slip({required bool inList}) => KitRequestCard.ask(
  kind: KitRequestKind.permission,
  icon: AppIconography.terminal,
  title: 'Run a command?',
  who: 'OpenCode',
  reason: KitNeedsYouReason.decision,
  ifIgnored: 'The agent waits; nothing is lost.',
  announcement: 'Permission needed: Run a command',
  detail: 'Pushes the branch',
  summary: 'git push origin main',
  since: _now,
  inList: inList,
  answers: KitRequestDecide(
    allowLabel: 'Allow',
    rejectLabel: 'Reject',
    onAllow: () {},
    onReject: () {},
    secondary: inList,
  ),
);

KitFeedItem _working() => KitFeedItem(
  title: 'Fix the TV focus ring',
  project: 'IPTV_King',
  state: 'Working',
  stateTone: KitFeedStateTone.running,
  time: 'now',
  running: KitFeedRunning(
    since: _now.subtract(const Duration(minutes: 3)),
    command: 'flutter test',
  ),
  onTap: () {},
);

Finder _rich(String text) => find.textContaining(text, findRichText: true);

void main() {
  group('the slip under its row', () {
    testWidgets('holds only the \$ command and the keys', (tester) async {
      await _pump(tester, ListView(children: [_slip(inList: true)]));
      expect(find.text('NEEDS YOU'), findsNothing);
      expect(find.text('Run a command?'), findsNothing);
      expect(find.text('Pushes the branch'), findsNothing);
      expect(find.text(r'$ git push origin main'), findsOneWidget);
      // Allow is still the slip's green key, though asked as a secondary.
      final allow = tester.widget<KitButton>(
        find.ancestor(of: find.text('Allow'), matching: find.byType(KitButton)),
      );
      expect(allow.role, KitButtonRole.primary);
    });

    testWidgets('in the chat it keeps its words', (tester) async {
      await _pump(tester, ListView(children: [_slip(inList: false)]));
      expect(find.text('NEEDS YOU'), findsOneWidget);
      expect(find.text('Run a command?'), findsOneWidget);
      expect(find.text(r'$ git push origin main'), findsOneWidget);
    });

    testWidgets('another style keeps the whole card', (tester) async {
      await _pump(
        tester,
        ListView(children: [_slip(inList: true)]),
        style: KitSurfaceStyle.solid,
      );
      expect(find.text('Run a command?'), findsOneWidget);
      expect(find.text('git push origin main'), findsOneWidget);
    });
  });

  group('the running row', () {
    testWidgets('a segment timer and the command it runs', (tester) async {
      await _pump(tester, ListView(children: [_working()]));
      expect(find.byKey(const ValueKey('kit-feed-timer')), findsOneWidget);
      expect(find.text('3 min'), findsOneWidget);
      expect(_rich('Running'), findsOneWidget);
      expect(_rich('flutter test'), findsOneWidget);
      // The line says it runs: no "Working" beside it.
      expect(_rich('Working'), findsNothing);
      expect(find.text('now'), findsNothing);
    });

    testWidgets('another style keeps its time and state', (tester) async {
      await _pump(
        tester,
        ListView(children: [_working()]),
        style: KitSurfaceStyle.solid,
      );
      expect(find.byKey(const ValueKey('kit-feed-timer')), findsNothing);
      expect(_rich('Working'), findsOneWidget);
      expect(_rich('flutter test'), findsNothing);
    });
  });

  group('the home around the list', () {
    Widget fab() => KitFloatingAction(
      label: 'New conversation',
      onPressed: () {},
      child: const SizedBox.expand(),
    );

    testWidgets('New conversation is a dark ink key with its words', (
      tester,
    ) async {
      await _pump(tester, fab());
      expect(find.text('New conversation'), findsOneWidget);
      final key = tester.widget<DecoratedBox>(
        find
            .ancestor(
              of: find.text('New conversation'),
              matching: find.byWidgetPredicate(
                (w) =>
                    w is DecoratedBox && w.decoration is KitSurfaceDecoration,
              ),
            )
            .first,
      );
      final face = key.decoration as KitSurfaceDecoration;
      expect(face.fill, SignalPalette.light.inkKey);
      expect(face.shadows.single.offset, const Offset(0, 3));

      await _pump(tester, fab(), style: KitSurfaceStyle.solid);
      expect(find.text('New conversation'), findsNothing);
    });

    testWidgets('the filters are outlined keys of one height', (tester) async {
      await _pump(
        tester,
        Align(
          alignment: Alignment.topLeft,
          child: KitFilterChips(
            chips: [
              KitChip.summary(
                label: 'All projects',
                expanded: false,
                onPressed: () {},
              ),
              KitFilterChip(
                label: 'Running',
                selected: false,
                onPressed: () {},
              ),
            ],
          ),
        ),
      );
      double pill(String label) => tester
          .getSize(
            find
                .ancestor(
                  of: find.text(label, findRichText: true),
                  matching: find.byWidgetPredicate(
                    (w) =>
                        w is DecoratedBox &&
                        w.decoration is KitSurfaceDecoration,
                  ),
                )
                .first,
          )
          .height;
      expect(pill('All projects'), greaterThanOrEqualTo(36));
      expect(pill('Running'), pill('All projects'));
    });

    testWidgets('the page title is set larger and heavier', (tester) async {
      late TextStyle signal, solid;
      Widget probe(void Function(TextStyle) out) => Builder(
        builder: (context) {
          out(KitText.styleOf(context, KitTextRole.largeTitle));
          return const SizedBox();
        },
      );
      await _pump(tester, probe((s) => signal = s));
      await _pump(
        tester,
        probe((s) => solid = s),
        style: KitSurfaceStyle.solid,
      );
      expect(signal.fontSize, 32);
      expect(signal.fontWeight!.value, greaterThan(solid.fontWeight!.value));
      expect(signal.fontWeight!.value, greaterThan(700));
    });
  });

  group('the chat while it works', () {
    KitTurn turn() => KitTurn(
      blocks: const [],
      phase: KitTurnPhase.running,
      live: KitTurnLive(
        activity: KitTurnActivity.working,
        detail: 'Running sleep 150',
        since: _now.subtract(const Duration(seconds: 72)),
      ),
    );

    testWidgets('the live line is a green signal with a segment timer', (
      tester,
    ) async {
      await _pump(tester, ListView(children: [turn()]));
      expect(
        find.byKey(const ValueKey('kit-turn-live-signal')),
        findsOneWidget,
      );
      expect(find.byKey(const ValueKey('kit-turn-live-timer')), findsOne);
      expect(find.text('1 min 12 s'), findsOneWidget);
      // The time is the timer's, not the words'.
      expect(find.textContaining('Running sleep 150'), findsOneWidget);
      expect(find.textContaining('· 1'), findsNothing);
      expect(find.byKey(const ValueKey('kit-turn-live-dot')), findsNothing);
      await tester.pumpWidget(const SizedBox());
    });

    testWidgets('another style keeps the dot and the time in words', (
      tester,
    ) async {
      await _pump(
        tester,
        ListView(children: [turn()]),
        style: KitSurfaceStyle.solid,
      );
      expect(find.byKey(const ValueKey('kit-turn-live-dot')), findsOneWidget);
      expect(find.byKey(const ValueKey('kit-turn-live-timer')), findsNothing);
      await tester.pumpWidget(const SizedBox());
    });
  });

  test('the controller knows the command a conversation runs', () async {
    TestWidgetsFlutterBinding.ensureInitialized();
    SharedPreferences.setMockInitialValues({});
    final preferences = await SharedPreferences.getInstance();
    final controller = ConnectionController(
      ProfileStore(prefs: preferences),
      backgroundLive: BackgroundLiveController(
        preferences: preferences,
        liveStatusDebounce: Duration.zero,
        invoke: (method, [arguments]) async => const {},
      ),
    );
    addTearDown(controller.dispose);
    controller.busySessions = {'a'};
    void tool(String status, {String tool = 'bash'}) =>
        controller.handleEventForTesting(
          EventEnvelope(
            type: 'message.part.updated',
            properties: {
              'part': {
                'type': 'tool',
                'sessionID': 'a',
                'callID': 'c1',
                'tool': tool,
                'state': {
                  'status': status,
                  'input': {'command': 'flutter test\n--reporter expanded'},
                },
              },
            },
          ),
        );
    tool('running');
    final now = controller.runningNow('a')!;
    expect(now.command, 'flutter test');
    expect(now.line, 'Running a command…');
    tool('completed');
    expect(controller.runningNow('a')!.command, isNull);
    tool('running', tool: 'read');
    expect(controller.runningNow('a')!.command, isNull);
    expect(controller.runningNow('b'), isNull);

    // OpenCode 2's rebuilt tool part names its session beside the part.
    controller.handleEventForTesting(
      EventEnvelope(
        type: 'message.part.updated',
        properties: {
          'sessionID': 'a',
          'part': {
            'type': 'tool',
            'callID': 'c2',
            'tool': 'bash',
            'state': {
              'status': 'running',
              'input': {'command': 'sleep 150'},
            },
          },
        },
      ),
    );
    expect(controller.runningNow('a')!.command, 'sleep 150');
  });
}
