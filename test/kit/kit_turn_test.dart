// KitTurn (docs/ux-system/kit-api/KitTurn.md; STATE-16, KIT-41, STATE-5,
// AUTO-15, KIT-23): one turn of a conversation. The numbered groups follow
// the spec's "Tests required".
import 'package:clock/clock.dart';
import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/chat/kit_markdown.dart';
import 'package:opencode_mobile/ui/kit/chat/kit_message.dart';
import 'package:opencode_mobile/ui/kit/chat/kit_turn.dart';
import 'package:opencode_mobile/ui/kit/chat/kit_work_line.dart';
import 'package:opencode_mobile/ui/kit/kit_menu.dart';
import 'package:opencode_mobile/ui/kit/kit_motion.dart';
import 'package:opencode_mobile/ui/kit/kit_redact.dart';
import 'package:opencode_mobile/ui/kit/kit_text.dart';

import 'kit_motion_still.dart';

const _turnKey = ValueKey('turn-1');
const _footerKey = ValueKey('turn-1-footer');
const _copyKey = ValueKey('message-copy-1');
const _moreKey = ValueKey('turn-1-more');
const _bubbleKey = ValueKey('prompt-1');
const _replyKey = ValueKey('reply-1');
const _lineKey = ValueKey('work-1');

const _replyWords =
    'The flakiness comes from the price refresh racing the assertion.';

Widget _app(
  Widget child, {
  bool reduced = false,
  double textScale = 1,
  TextDirection? direction,
}) => MaterialApp(
  debugShowCheckedModeBanner: false,
  theme: AppTheme.dark(),
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  builder: (context, inner) => MediaQuery(
    data: MediaQuery.of(context).copyWith(
      disableAnimations: reduced,
      textScaler: TextScaler.linear(textScale),
    ),
    child: direction == null
        ? inner!
        : Directionality(textDirection: direction, child: inner!),
  ),
  home: Scaffold(
    body: SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [child],
      ),
    ),
  ),
);

Future<void> _pump(
  WidgetTester tester,
  Widget child, {
  Size size = const Size(432, 915),
  bool reduced = false,
  double textScale = 1,
  TextDirection? direction,
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    _app(child, reduced: reduced, textScale: textScale, direction: direction),
  );
  await tester.pump();
}

KitMessage _prompt({List<KitMenuItem> menu = const []}) => KitMessage.prompt(
  body: const KitMarkdown('Fix the flaky checkout test.', selectable: false),
  menu: menu,
  bubbleKey: _bubbleKey,
);

const _reply = KitMessage.reply(
  body: KitMarkdown(_replyWords, selectable: false),
  bodyKey: _replyKey,
);

KitWorkLine _line(KitWorkState state) => KitWorkLine(
  key: _lineKey,
  counts: const KitWorkCounts(read: 3, edited: 1),
  state: state,
  steps: const [KitText('Read lib/main.dart', role: KitTextRole.mono)],
);

KitTurnFooter _footer({
  List<String>? ran,
  String Function()? copy,
  bool menu = true,
  String? meta = 'Sonnet 4.5 · 12k tokens · 10:42',
}) => KitTurnFooter(
  copyText: copy ?? () => _replyWords,
  meta: meta,
  menu: menu
      ? [
          KitMenuItem(
            label: 'Fork from here',
            onSelected: () => ran?.add('fork'),
          ),
          KitMenuItem(label: 'Revert', onSelected: () => ran?.add('revert')),
        ]
      : const [],
);

KitTurn _turn({
  KitTurnPhase phase = KitTurnPhase.finished,
  List<Widget>? blocks,
  KitMessage? prompt,
  KitTurnFooter? footer,
  bool latest = false,
  bool highlighted = false,
  DateTime? since,
}) => KitTurn(
  prompt: prompt ?? _prompt(),
  blocks: blocks ?? [_line(KitWorkState.done), _reply],
  phase: phase,
  since: since,
  footer: footer,
  latest: latest,
  highlighted: highlighted,
  turnKey: _turnKey,
  footerKey: _footerKey,
  copyKey: _copyKey,
  moreKey: _moreKey,
);

void main() {
  kitMotionStillTests(
    'KitTurn',
    builds: {
      'running': () => _turn(
        phase: KitTurnPhase.running,
        blocks: [_line(KitWorkState.running)],
      ),
      'finished': () => _turn(footer: _footer()),
    },
    changes: {
      'reply finishes': KitMotionChange(
        build: () => _turn(
          phase: KitTurnPhase.running,
          latest: true,
          blocks: [_line(KitWorkState.running)],
        ),
        act: (tester, stage) => stage.rebuild(
          _turn(latest: true, footer: _footer(meta: 'Reply finished')),
        ),
        shows: 'Reply finished',
      ),
    },
  );

  late List<MethodCall> platform;
  late List<Map<Object?, Object?>> announcements;

  setUp(() {
    platform = [];
    announcements = [];
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    messenger.setMockMethodCallHandler(SystemChannels.platform, (call) async {
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
    debugPlatformCapabilities = null;
    KitRedact.clearKnownSecrets();
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    messenger.setMockMethodCallHandler(SystemChannels.platform, null);
    messenger.setMockDecodedMessageHandler<dynamic>(
      SystemChannels.accessibility,
      null,
    );
  });

  String? copied() {
    final call = platform.lastWhere((c) => c.method == 'Clipboard.setData');
    return (call.arguments as Map)['text'] as String?;
  }

  testWidgets('1 · prompt, then blocks in order, then the footer last', (
    tester,
  ) async {
    await _pump(tester, _turn(footer: _footer(), latest: true));
    final prompt = tester.getRect(find.byKey(_bubbleKey));
    final line = tester.getRect(find.byKey(_lineKey));
    final reply = tester.getRect(find.byKey(_replyKey));
    final footer = tester.getRect(find.byKey(_footerKey));
    expect(prompt.bottom, lessThanOrEqualTo(line.top));
    expect(line.bottom, lessThanOrEqualTo(reply.top));
    expect(reply.bottom, lessThanOrEqualTo(footer.top));
    // space4 between the prompt and the first block.
    expect(line.top - prompt.bottom, 16);
  });

  group('2 · a running turn has no footer', () {
    for (final phase in [KitTurnPhase.running, KitTurnPhase.waitingForYou]) {
      testWidgets('${phase.name}: no Copy or More even with a footer', (
        tester,
      ) async {
        final ran = <String>[];
        await _pump(
          tester,
          _turn(
            phase: phase,
            blocks: [
              _reply,
              _line(
                phase == KitTurnPhase.running
                    ? KitWorkState.running
                    : KitWorkState.waitingForYou,
              ),
            ],
            footer: _footer(ran: ran),
            latest: true,
          ),
          // The running work line's mark is still under reduced motion.
          reduced: true,
        );
        expect(find.byKey(_footerKey), findsNothing);
        expect(find.byKey(_copyKey), findsNothing);
        expect(find.byKey(_moreKey), findsNothing);
        expect(find.byTooltip('Copy reply'), findsNothing);

        await tester.longPress(find.byKey(_replyKey));
        await tester.pumpAndSettle();
        expect(find.byType(KitMenuPanel), findsOneWidget);
        expect(find.text('Copy reply'), findsOneWidget);
        expect(find.text('Fork from here'), findsOneWidget);
        await tester.tap(find.text('Fork from here'));
        await tester.pumpAndSettle();
        expect(ran, ['fork']);
      });
    }
  });

  group('3 · a finished turn has exactly one footer', () {
    testWidgets('one Copy and one More; More opens the given items', (
      tester,
    ) async {
      final ran = <String>[];
      await _pump(tester, _turn(footer: _footer(ran: ran)));
      expect(find.byKey(_copyKey), findsOneWidget);
      expect(find.byKey(_moreKey), findsOneWidget);
      expect(find.byTooltip('Copy reply'), findsOneWidget);
      expect(find.byTooltip('More for this reply'), findsOneWidget);

      await tester.tap(find.byKey(_moreKey));
      await tester.pumpAndSettle();
      expect(find.byType(KitMenuPanel), findsOneWidget);
      expect(find.text('Fork from here'), findsOneWidget);
      expect(find.text('Revert'), findsOneWidget);
      await tester.tap(find.text('Revert'));
      await tester.pumpAndSettle();
      expect(ran, ['revert']);
    });

    testWidgets('an empty menu leaves More out', (tester) async {
      await _pump(tester, _turn(footer: _footer(menu: false)));
      expect(find.byKey(_copyKey), findsOneWidget);
      expect(find.byKey(_moreKey), findsNothing);
    });

    testWidgets('no footer given: no footer drawn', (tester) async {
      await _pump(tester, _turn());
      expect(find.byKey(_footerKey), findsNothing);
      expect(find.byKey(_copyKey), findsNothing);
    });
  });

  testWidgets('4 · meta words only on the latest turn', (tester) async {
    await _pump(tester, _turn(footer: _footer(), latest: true));
    expect(find.text('Sonnet 4.5 · 12k tokens · 10:42'), findsOneWidget);

    await _pump(tester, _turn(footer: _footer()));
    expect(find.text('Sonnet 4.5 · 12k tokens · 10:42'), findsNothing);
    expect(find.byKey(_copyKey), findsOneWidget);
    expect(find.byKey(_moreKey), findsOneWidget);
  });

  testWidgets('5 · Copy reads at tap time, copies verbatim, announces once, '
      'shows no SnackBar', (tester) async {
    var value = 'first render';
    // A registered secret stays in the copy: the reply is the person's own
    // content (SEC-13).
    KitRedact.registerKnownSecret('sk-verbatim-1234567890');
    await _pump(tester, _turn(footer: _footer(copy: () => value)));
    value = 'at tap time sk-verbatim-1234567890';
    await tester.tap(find.byKey(_copyKey));
    await tester.pump();
    expect(copied(), 'at tap time sk-verbatim-1234567890');
    expect(announcements, hasLength(1));
    expect((announcements.single['data'] as Map)['message'], 'Copied');
    expect(find.byType(SnackBar), findsNothing);
  });

  group('6 · the starting line', () {
    testWidgets('escalates after 8 s; a block silences it', (tester) async {
      await _pump(
        tester,
        _turn(phase: KitTurnPhase.starting, blocks: [], since: clock.now()),
      );
      expect(find.text('Starting the model…'), findsOneWidget);
      await tester.pump(const Duration(seconds: 8));
      expect(find.text('Starting the model…'), findsNothing);
      expect(find.text('Still waiting for the model · 8 s'), findsOneWidget);

      await _pump(
        tester,
        _turn(phase: KitTurnPhase.starting, blocks: [_reply], since: null),
      );
      expect(find.textContaining('Starting'), findsNothing);
      expect(find.textContaining('Still waiting'), findsNothing);
    });
  });

  group('6b · the live line of a running turn', () {
    testWidgets('says what it does, adds the time after 5 s, turns slow '
        'after 20 s, with no dot and no Stop', (tester) async {
      await _pump(
        tester,
        KitTurn(
          blocks: const [],
          phase: KitTurnPhase.running,
          live: KitTurnLive(
            activity: KitTurnActivity.waitingForModel,
            since: clock.now(),
          ),
        ),
      );
      expect(find.text('Thinking…'), findsOneWidget);
      await tester.pump(const Duration(seconds: 12));
      expect(find.text('Thinking · 12 s'), findsOneWidget);
      await tester.pump(const Duration(seconds: 13));
      expect(find.text("Waiting for the model's first word · 25 s"), findsOne);
      await tester.pump(const Duration(seconds: 40));
      expect(
        find.text("Waiting for the model's first word · 1 min 5 s"),
        findsOne,
      );
      expect(find.text('Stop reply'), findsNothing);
      expect(find.byKey(const ValueKey('kit-turn-live-dot')), findsNothing);
    });

    testWidgets('a light sweeps over the words; reduced motion holds them '
        'still', (tester) async {
      KitMotion.loops = true;
      addTearDown(() => KitMotion.loops = false);
      final turn = KitTurn(
        blocks: const [],
        phase: KitTurnPhase.running,
        live: KitTurnLive(
          activity: KitTurnActivity.waitingForModel,
          since: clock.now(),
        ),
      );
      await _pump(tester, turn);
      expect(find.byKey(const ValueKey('kit-turn-live-sweep')), findsOneWidget);
      expect(find.text('Thinking…'), findsOneWidget);
      await _pump(tester, turn, reduced: true);
      expect(find.byKey(const ValueKey('kit-turn-live-sweep')), findsNothing);
      expect(find.text('Thinking…'), findsOneWidget);
      // Unmount before the looping sweep outlives the test.
      await tester.pumpWidget(const SizedBox());
    });

    testWidgets('on the prompt row too', (tester) async {
      await _pump(
        tester,
        KitTurn(
          segment: KitTurnSegment.first,
          blocks: const [],
          phase: KitTurnPhase.finished,
          live: KitTurnLive(
            activity: KitTurnActivity.sending,
            since: clock.now(),
          ),
        ),
      );
      expect(find.text('Thinking…'), findsOneWidget);
      expect(find.text('Stop reply'), findsNothing);
    });
  });

  group('7 · stopped and interrupted', () {
    for (final (phase, words) in [
      (KitTurnPhase.stopped, 'You stopped this reply.'),
      (
        KitTurnPhase.interrupted,
        'The connection dropped before this reply finished.',
      ),
    ]) {
      testWidgets('${phase.name}: its end line and a footer', (tester) async {
        await _pump(tester, _turn(phase: phase, footer: _footer()));
        expect(find.text(words), findsOneWidget);
        expect(find.byKey(_copyKey), findsOneWidget);
        expect(find.textContaining('Starting'), findsNothing);
        // The end line sits before the footer.
        expect(
          tester.getRect(find.text(words)).bottom,
          lessThanOrEqualTo(tester.getRect(find.byKey(_footerKey)).top),
        );
      });
    }

    testWidgets('failed: a footer and no end line of its own', (tester) async {
      await _pump(tester, _turn(phase: KitTurnPhase.failed, footer: _footer()));
      expect(find.byKey(_copyKey), findsOneWidget);
      expect(find.textContaining('stopped'), findsNothing);
      expect(find.textContaining('connection dropped'), findsNothing);
    });
  });

  testWidgets("8 · a prompt's long-press opens the prompt's menu", (
    tester,
  ) async {
    await _pump(
      tester,
      _turn(
        prompt: _prompt(
          menu: [KitMenuItem(label: 'Edit and resend', onSelected: () {})],
        ),
        footer: _footer(),
      ),
    );
    await tester.longPress(find.byKey(_bubbleKey));
    await tester.pumpAndSettle();
    expect(find.text('Edit and resend'), findsOneWidget);
    expect(find.text('Copy reply'), findsNothing);
    expect(find.text('Fork from here'), findsNothing);
  });

  testWidgets('9 · highlighted paints the surface1 band, no accent', (
    tester,
  ) async {
    final roles = ThemeRoles.resolve(AppTheme.dark());
    await _pump(tester, _turn(footer: _footer()));
    final plain = tester.renderObject(find.byKey(_turnKey));
    expect(plain, isNot(paints..rrect(color: roles.surface1)));

    await _pump(tester, _turn(footer: _footer(), highlighted: true));
    await tester.pumpAndSettle();
    final lit = tester.renderObject(find.byKey(_turnKey));
    expect(lit, paints..rrect(color: roles.surface1));
    expect(lit, isNot(paints..rrect(color: roles.accent)));
  });

  testWidgets('10 · one container with the menu as custom actions; no live '
      'region; 48 dp buttons 8 dp apart', (tester) async {
    final semantics = tester.ensureSemantics();
    await _pump(tester, _turn(footer: _footer(), latest: true));
    final data = tester.getSemantics(find.byKey(_turnKey)).getSemanticsData();
    final labels = [
      for (final id in data.customSemanticsActionIds ?? const <int>[])
        CustomSemanticsAction.getAction(id)!.label,
    ];
    expect(labels, ['Copy reply', 'Fork from here', 'Revert']);

    final root = tester.getSemantics(find.byKey(_turnKey));
    var live = 0;
    bool visit(SemanticsNode node) {
      if (node.getSemanticsData().flagsCollection.isLiveRegion) live++;
      node.visitChildren(visit);
      return true;
    }

    visit(root);
    expect(live, 0);

    final copy = tester.getRect(find.byKey(_copyKey));
    final more = tester.getRect(find.byKey(_moreKey));
    expect(copy.width, greaterThanOrEqualTo(48));
    expect(copy.height, greaterThanOrEqualTo(48));
    expect(more.width, greaterThanOrEqualTo(48));
    expect(more.left - copy.right, 8);
    semantics.dispose();
  });

  group('11 · desktop capabilities', () {
    testWidgets('Tab order: prompt → blocks → Copy → More', (tester) async {
      debugPlatformCapabilities = const PlatformCapabilities.linuxDesktop();
      await _pump(
        tester,
        _turn(
          prompt: _prompt(
            menu: [KitMenuItem(label: 'Edit and resend', onSelected: () {})],
          ),
          footer: _footer(),
        ),
      );
      String? focused() {
        final focus = FocusManager.instance.primaryFocus?.context;
        if (focus == null) return null;
        final box = focus.findRenderObject()! as RenderBox;
        final centre = box.localToGlobal(box.size.center(Offset.zero));
        for (final (name, key) in [
          ('prompt', _bubbleKey),
          ('blocks', _lineKey),
          ('copy', _copyKey),
          ('more', _moreKey),
        ]) {
          if (tester.getRect(find.byKey(key)).contains(centre)) return name;
        }
        return 'other';
      }

      final order = <String?>[];
      for (var i = 0; i < 4; i++) {
        await tester.sendKeyEvent(LogicalKeyboardKey.tab);
        await tester.pump();
        order.add(focused());
      }
      expect(order, ['prompt', 'blocks', 'copy', 'more']);
    });

    testWidgets('right-click on a reply opens the menu at the pointer', (
      tester,
    ) async {
      debugPlatformCapabilities = const PlatformCapabilities.linuxDesktop();
      await _pump(tester, _turn(footer: _footer()));
      final point = tester.getCenter(find.byKey(_replyKey));
      await tester.tapAt(
        point,
        buttons: kSecondaryMouseButton,
        kind: PointerDeviceKind.mouse,
      );
      await tester.pumpAndSettle();
      expect(find.byType(KitMenuPanel), findsOneWidget);
      expect(find.text('Copy reply'), findsOneWidget);
      final panel = tester.getRect(find.byType(KitMenuPanel));
      // Opened at the pointer, not anchored to the turn's box.
      expect(panel.inflate(24).contains(point), isTrue);
      expect((panel.top - point.dy).abs(), lessThan(48));
    });

    testWidgets('Shift+F10 inside the turn opens its menu', (tester) async {
      debugPlatformCapabilities = const PlatformCapabilities.linuxDesktop();
      await _pump(tester, _turn(footer: _footer()));
      // Focus the copy button itself: the nearest Focus above its glyph.
      Focus.of(
        tester.element(
          find.descendant(
            of: find.byKey(_copyKey),
            matching: find.byType(Icon),
          ),
        ),
      ).requestFocus();
      await tester.pump();
      expect(FocusManager.instance.primaryFocus?.context, isNotNull);
      await tester.sendKeyDownEvent(LogicalKeyboardKey.shiftLeft);
      await tester.sendKeyEvent(LogicalKeyboardKey.f10);
      await tester.sendKeyUpEvent(LogicalKeyboardKey.shiftLeft);
      await tester.pumpAndSettle();
      expect(find.byType(KitMenuPanel), findsOneWidget);
      expect(find.text('Copy reply'), findsOneWidget);
      expect(find.text('Revert'), findsOneWidget);
    });
  });

  testWidgets('12 · reduced motion: one pump settles', (tester) async {
    // Toggled in place: re-pumping the app would animate its theme.
    final lit = ValueNotifier(false);
    addTearDown(lit.dispose);
    await _pump(
      tester,
      ValueListenableBuilder<bool>(
        valueListenable: lit,
        builder: (context, value, _) =>
            _turn(footer: _footer(), highlighted: value),
      ),
      reduced: true,
    );
    lit.value = true;
    await tester.pump();
    expect(tester.hasRunningAnimations, isFalse);
    final roles = ThemeRoles.resolve(AppTheme.dark());
    expect(
      tester.renderObject(find.byKey(_turnKey)),
      paints..rrect(color: roles.surface1),
    );
  });

  group('13 · 200 % text at 320 dp', () {
    for (final direction in [TextDirection.ltr, TextDirection.rtl]) {
      testWidgets(direction.name, (tester) async {
        await _pump(
          tester,
          _turn(
            footer: _footer(
              meta: 'Claude Sonnet 4.5 with a long name · 128k tokens · 10:42',
            ),
            latest: true,
            phase: KitTurnPhase.interrupted,
          ),
          size: const Size(320, 1400),
          textScale: 2,
          direction: direction,
        );
        expect(tester.takeException(), isNull);
        // The meta wraps above the buttons, which stay on one row.
        final meta = tester.getRect(find.textContaining('Claude Sonnet'));
        final copy = tester.getRect(find.byKey(_copyKey));
        final more = tester.getRect(find.byKey(_moreKey));
        expect(meta.bottom, lessThanOrEqualTo(copy.top));
        expect(copy.top, more.top);
      });
    }
  });
}
