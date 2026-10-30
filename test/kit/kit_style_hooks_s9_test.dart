// Slice S9 of the style cleanup: the last shared hooks the style specs
// drive. Each is a no-op for Solid and, left at its default, answers what
// every style drew before; a made-up spec (standing in for Glass through
// `debugKitStyleSpecs`) moves each one to show that a spec alone can.
import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/kit/kit_surface_look.dart';
import 'package:opencode_mobile/ui/kit/style/kit_style.dart';
import 'package:opencode_mobile/ui/kit/style/kit_style_brutal.dart';

/// A made-up style that records what it is asked and answers each new
/// request field visibly.
class _HookSpec extends KitStyleSpec {
  const _HookSpec();

  static var tokensNow = const KitStyleTokens(defaults: KitKnobs());
  static final seen = <KitSurfaceRequest>[];

  static const ink = Color(0xFF101010);
  static const section = Color(0xFF202020);
  static const field = Color(0xFF304050);
  static const hover = Color(0xFF506070);
  static const filled = Color(0x40FF0000);
  static const shown = Color(0xFFE0E0E0);

  @override
  KitStyleTokens get tokens => tokensNow;

  @override
  Decoration resolve(KitSurfaceRequest r) {
    seen.add(r);
    return switch (r.role) {
      KitSurfaceRole.divider => KitSurfaceDecoration(
        fill: r.selected
            ? section
            : r.withinGroup
            ? Colors.transparent
            : ink,
        edgeWidth: r.selected ? 1 : 0,
      ),
      KitSurfaceRole.field => KitSurfaceDecoration(
        borderRadius: r.radius,
        fill: r.hovered ? hover : field,
      ),
      // A row group's panel set straight on the page.
      KitSurfaceRole.content when r.level == KitSurfaceLevel.surface1 =>
        KitSurfaceDecoration(borderRadius: r.radius, fill: Colors.transparent),
      _ => KitSurfaceDecoration(
        borderRadius: r.radius,
        circle: r.circle,
        fill: r.color ?? shown,
        faceShift: r.pressed ? 3 : 0,
      ),
    };
  }

  @override
  Decoration? resolveFilledLayer(KitSurfaceRequest r) =>
      const KitSurfaceDecoration(fill: filled);
}

const _made = KitSurfaceStyle.soft;

Future<void> _pump(
  WidgetTester tester,
  Widget child, {
  KitSurfaceStyle style = _made,
  bool scroll = true,
}) async {
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    MaterialApp(
      debugShowCheckedModeBanner: false,
      theme: AppTheme.dark(),
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      builder: (context, child) => MediaQuery(
        data: MediaQuery.of(context).copyWith(disableAnimations: true),
        child: child!,
      ),
      home: KitEffectsScope(
        effects: KitEffects(surfaceStyle: style),
        child: Scaffold(body: scroll ? ListView(children: [child]) : child),
      ),
    ),
  );
}

KitRequestCard _card({KitRequestLook look = KitRequestLook.standard}) =>
    KitRequestCard.ask(
      kind: KitRequestKind.permission,
      look: look,
      title: 'Run a shell command',
      who: 'fox',
      reason: KitNeedsYouReason.decision,
      ifIgnored: 'The agent waits; nothing is lost.',
      announcement: 'Permission needed: Run a shell command',
      summary: 'flutter test',
      targets: look == KitRequestLook.question
          ? const [KitRequestTarget('flutter test')]
          : const [],
      answers: KitRequestDecide(
        allowLabel: 'Run once',
        rejectLabel: "Don't run",
        onAllow: () {},
        onReject: () {},
      ),
    );

/// The attention start edge's paint, if the card draws one.
Finder _startEdge(WidgetTester tester) {
  final attention = KitTokens.of(
    tester.element(find.byType(KitRequestCard)),
  ).roles.attention;
  return find.byWidgetPredicate((w) => w is ColoredBox && w.color == attention);
}

void main() {
  setUp(() {
    _HookSpec.tokensNow = const KitStyleTokens(defaults: KitKnobs());
    _HookSpec.seen.clear();
    debugKitStyleSpecs = (style) => style == _made ? const _HookSpec() : null;
  });
  tearDown(() => debugKitStyleSpecs = null);

  group('needs you (KitStyleTokens.needsYou)', () {
    test('by default it follows the glow, as every style had it', () {
      const look = KitNeedsYouLook();
      final loud = look.decide(halo: true);
      expect(
        (loud.tint, loud.line, loud.ring, loud.startEdge, loud.pill),
        (true, true, true, false, false),
      );
      final calm = look.decide(halo: false);
      expect(
        (calm.tint, calm.line, calm.ring, calm.startEdge),
        (false, false, false, true),
      );
      final question = look.decide(halo: true, question: true);
      expect(
        (question.tint, question.line, question.ring, question.startEdge),
        (false, true, false, true),
      );
      // Solid (today's look) keeps the default; Soft, Brutal and Paper set
      // their own in their specs (the style polish).
      for (final style in [KitSurfaceStyle.solid]) {
        final tokens = kitStyleSpec(style).tokens;
        expect(tokens.needsYou.tag, KitNeedsYouTag.caption, reason: '$style');
        expect(tokens.needsYou.ring, isNull, reason: '$style');
      }
    });

    test('ring, tint and edge are decided apart from the glow', () {
      // Soft's guide: the start edge, no fill, whatever its glow.
      const soft = KitNeedsYouLook(tint: false, ring: false);
      final s = soft.decide(halo: true);
      expect((s.tint, s.ring, s.startEdge), (false, false, true));
      // Brutal's guide: the pill is the only amber.
      const brutal = KitNeedsYouLook(
        tag: KitNeedsYouTag.pill,
        startEdge: false,
      );
      final b = brutal.decide(halo: false);
      expect(
        (b.tint, b.ring, b.startEdge, b.pill),
        (false, false, false, true),
      );
    });

    testWidgets('Solid: the amber caption and the ring, no pill', (
      tester,
    ) async {
      await _pump(tester, _card(), style: KitSurfaceStyle.solid);
      final words = tester.widget<KitText>(
        find.ancestor(
          of: find.text('Needs you'),
          matching: find.byType(KitText),
        ),
      );
      expect(words.tone, KitTextTone.attention);
      expect(_startEdge(tester), findsNothing);
    });

    testWidgets('a spec sets "Needs you" as a pill with no start edge', (
      tester,
    ) async {
      _HookSpec.tokensNow = const KitStyleTokens(
        defaults: KitKnobs(),
        needsYou: KitNeedsYouLook(
          tag: KitNeedsYouTag.pill,
          tint: false,
          ring: false,
          startEdge: false,
        ),
      );
      await _pump(tester, _card());
      final roles = KitTokens.of(tester.element(find.text('Needs you'))).roles;
      final words = tester.widget<Text>(find.text('Needs you'));
      expect(words.style?.color, roles.onAttentionFill);
      // The pill is the style's control in the attention fill.
      expect(
        _HookSpec.seen.any(
          (r) =>
              r.role == KitSurfaceRole.control &&
              r.color == roles.attentionFill,
        ),
        isTrue,
      );
      expect(_startEdge(tester), findsNothing);
    });

    // On the phone, over the dark live background, the pill's words wore
    // the quiet tones' dark halo and looked smeared: they sit on the
    // opaque attention fill and never need one.
    testWidgets('the pill words carry no live-background halo', (tester) async {
      _HookSpec.tokensNow = const KitStyleTokens(
        defaults: KitKnobs(),
        needsYou: KitNeedsYouLook(tag: KitNeedsYouTag.pill),
      );
      await _pump(
        tester,
        KitTextHalo(ground: const Color(0xFF000000), child: _card()),
      );
      final words = tester.widget<Text>(find.text('Needs you'));
      expect(words.style?.shadows ?? const <Shadow>[], isEmpty);
    });

    testWidgets('a spec keeps the start edge and drops the tint and ring', (
      tester,
    ) async {
      _HookSpec.tokensNow = const KitStyleTokens(
        defaults: KitKnobs(),
        needsYou: KitNeedsYouLook(tint: false, ring: false),
      );
      await _pump(tester, _card());
      expect(_startEdge(tester), findsOneWidget);
      final roles = KitTokens.of(tester.element(find.text('Needs you'))).roles;
      // No attention tint asked for the face.
      expect(
        _HookSpec.seen.where(
          (r) =>
              r.role == KitSurfaceRole.content &&
              r.color ==
                  Color.alphaBlend(roles.attentionSurface, roles.surface1),
        ),
        isEmpty,
      );
    });
  });

  group('dividers between groups and within one', () {
    testWidgets('an inset line asks withinGroup; a spec parts it by space', (
      tester,
    ) async {
      await _pump(
        tester,
        const Column(
          children: [
            KitDivider(key: ValueKey('between')),
            KitDivider(key: ValueKey('within'), inset: KitDividerInset.text),
          ],
        ),
      );
      expect(_lineColor(tester, 'between'), _HookSpec.ink);
      expect(_lineColor(tester, 'within'), Colors.transparent);
    });

    testWidgets('Solid draws both alike, as before', (tester) async {
      await _pump(
        tester,
        const Column(
          children: [
            KitDivider(key: ValueKey('between')),
            KitDivider(key: ValueKey('within'), inset: KitDividerInset.text),
          ],
        ),
        style: KitSurfaceStyle.solid,
      );
      expect(_lineColor(tester, 'between'), _lineColor(tester, 'within'));
    });

    test('the real specs answer both alike (their default), but Brutal', () {
      debugKitStyleSpecs = null;
      for (final style in KitSurfaceStyle.values) {
        // Brutal rules between groups only (polish-brutal).
        if (style == KitSurfaceStyle.brutal) continue;
        for (final dark in [false, true]) {
          final roles = dark ? graphiteDark : graphiteLight;
          Decoration ask({required bool within}) => KitSurfaceLook.resolve(
            roles: roles,
            style: style,
            role: KitSurfaceRole.divider,
            solid: BoxDecoration(color: roles.hairline),
            withinGroup: within,
          );
          expect(ask(within: true), ask(within: false), reason: '$style');
        }
      }
    });
  });

  group('KitTextHalo', () {
    testWidgets('a styled opaque surface covers the halo; a clear one not', (
      tester,
    ) async {
      Future<List<Shadow>?> haloUnder(Color fill) async {
        List<Shadow>? got;
        await tester.pumpWidget(
          Directionality(
            textDirection: TextDirection.ltr,
            child: KitTextHalo(
              ground: const Color(0xFF123456),
              child: DecoratedBox(
                decoration: KitSurfaceDecoration(fill: fill),
                child: Builder(
                  builder: (context) {
                    got = KitTextHalo.of(context);
                    return const SizedBox();
                  },
                ),
              ),
            ),
          ),
        );
        return got;
      }

      expect(await haloUnder(const Color(0xFFFFFFFF)), isNull);
      expect(await haloUnder(const Color(0x40FFFFFF)), isNotNull);
    });
  });

  group('the prompt bubble decoration', () {
    testWidgets('the queued bubble takes the prompt bubble\'s look', (
      tester,
    ) async {
      late Decoration prompt;
      late Decoration queued;
      late Decoration solidQueued;
      Widget probe() => Builder(
        builder: (context) {
          prompt = kitPromptBubbleDecoration(context);
          queued = kitPromptBubbleDecoration(context, edge: false);
          return const SizedBox();
        },
      );
      await _pump(tester, probe());
      expect(queued, prompt);
      await _pump(tester, probe(), style: KitSurfaceStyle.solid);
      solidQueued = queued;
      expect((prompt as BoxDecoration).border, isNotNull);
      expect((solidQueued as BoxDecoration).border, isNull);
      expect(solidQueued.color, (prompt as BoxDecoration).color);
    });
  });

  group('row groups set on the page', () {
    for (final sliver in [false, true]) {
      testWidgets('${sliver ? 'KitSliverRowGroup' : 'KitRowGroup'}: rows '
          'reach into the margin under a bare panel and in flat lists '
          '(Solid) only', (tester) async {
        Widget group() {
          final rows = [
            KitRow(key: const ValueKey('row'), title: 'One', onTap: () {}),
          ];
          return sliver
              ? CustomScrollView(
                  slivers: [KitSliverRowGroup(label: 'Group', children: rows)],
                )
              : KitRowGroup(label: 'Group', children: rows);
        }

        Future<double> rowStart(KitSurfaceStyle style) async {
          await _pump(tester, group(), style: style, scroll: !sliver);
          return tester.getTopLeft(find.byKey(const ValueKey('row'))).dx;
        }

        final solid = await rowStart(KitSurfaceStyle.solid);
        // Soft is the made-up spec here (_made, since Glass retired), so the
        // framed panel is Brutal's: its ink edge keeps the rows inside.
        final framed = await rowStart(KitSurfaceStyle.brutal);
        final bare = await rowStart(_made);
        expect(solid, 0);
        expect(framed, 16);
        expect(bare, 0);
      });
    }
  });

  group('the section rule', () {
    testWidgets('sits right above the words, a space2 apart; Solid has none', (
      tester,
    ) async {
      await _pump(tester, const KitSectionLabel('Section'));
      final line = find.byKey(const ValueKey('kit-divider-line'));
      expect(line, findsOneWidget);
      final words = tester.getRect(find.text('Section'));
      final rule = tester.getRect(line);
      expect(rule.bottom, lessThanOrEqualTo(words.top));
      expect(words.top - rule.bottom, closeTo(8, 1));
      await _pump(
        tester,
        const KitSectionLabel('Section'),
        style: KitSurfaceStyle.solid,
      );
      expect(line, findsNothing);
    });

    testWidgets('a KitRowGroup draws it once', (tester) async {
      await _pump(
        tester,
        KitRowGroup(
          label: 'Group',
          children: [KitRow(title: 'One', onTap: () {})],
        ),
      );
      final ruleColor = _HookSpec.section;
      final rules = tester
          .widgetList(find.byKey(const ValueKey('kit-divider-line')))
          .where((w) => _paintedColor(w) == ruleColor);
      expect(rules, hasLength(1));
    });
  });

  group('press passed down', () {
    testWidgets('KitTappable.pressedOf follows the hold', (tester) async {
      final seen = <bool>[];
      await _pump(
        tester,
        KitTappable(
          key: const ValueKey('tap'),
          onTap: () {},
          label: 'Go',
          child: Builder(
            builder: (context) {
              seen.add(KitTappable.pressedOf(context));
              return const SizedBox(width: 60, height: 60);
            },
          ),
        ),
      );
      expect(seen.last, isFalse);
      final gesture = await tester.startGesture(
        tester.getCenter(find.byKey(const ValueKey('tap'))),
      );
      await tester.pump(const Duration(milliseconds: 200));
      expect(seen.last, isTrue);
      await gesture.up();
      await tester.pump();
      expect(seen.last, isFalse);
    });

    testWidgets('the floating "+" sinks while held under Brutal', (
      tester,
    ) async {
      debugKitStyleSpecs = null;
      await _pump(
        tester,
        KitFloatingAction(
          label: 'New conversation',
          onPressed: () {},
          buttonKey: const ValueKey('fab'),
          child: const SizedBox.expand(),
        ),
        style: KitSurfaceStyle.brutal,
        scroll: false,
      );
      double shift() => tester
          .widgetList<DecoratedBox>(
            find.descendant(
              of: find.byKey(const ValueKey('fab')),
              matching: find.byType(DecoratedBox),
            ),
          )
          .map((b) => b.decoration)
          .whereType<KitSurfaceDecoration>()
          .map((d) => d.faceShift)
          .fold(0.0, (a, b) => a > b ? a : b);
      expect(shift(), 0);
      final gesture = await tester.startGesture(
        tester.getCenter(find.byKey(const ValueKey('fab'))),
      );
      await tester.pump(const Duration(milliseconds: 200));
      await tester.pumpAndSettle();
      expect(shift(), KitStyleBrutal.offsetOf(KitSurfaceRole.floating));
      await gesture.up();
      await tester.pumpAndSettle();
    });
  });

  group('link underline (KitStyleTokens.linkUnderline)', () {
    Future<double?> thickness(
      WidgetTester tester,
      KitSurfaceStyle style,
    ) async {
      await _pump(
        tester,
        KitButton.tertiary(label: 'Just show me', onPressed: () {}),
        style: style,
      );
      final text = tester.widget<Text>(find.text('Just show me'));
      return text.style?.decoration == TextDecoration.underline
          ? text.style?.decorationThickness
          : null;
    }

    testWidgets('by default as the divider draws a line', (tester) async {
      debugKitStyleSpecs = null;
      expect(await thickness(tester, KitSurfaceStyle.solid), isNull);
      expect(await thickness(tester, KitSurfaceStyle.soft), isNull);
      expect(await thickness(tester, KitSurfaceStyle.brutal), 2);
      expect(await thickness(tester, KitSurfaceStyle.paper), 1);
    });

    testWidgets('a spec sets it apart from the divider', (tester) async {
      _HookSpec.tokensNow = const KitStyleTokens(
        defaults: KitKnobs(),
        linkUnderline: 1.5,
      );
      expect(await thickness(tester, _made), 1.5);
      _HookSpec.tokensNow = const KitStyleTokens(
        defaults: KitKnobs(),
        linkUnderline: 0,
      );
      expect(await thickness(tester, _made), isNull);
    });
  });

  group('filled controls and disabled controls', () {
    test('a highlight on an own fill keeps the part\'s layer by default', () {
      debugKitStyleSpecs = null;
      const own = ShapeDecoration(
        color: Color(0x29000000),
        shape: StadiumBorder(),
      );
      for (final style in KitSurfaceStyle.values) {
        // Brutal answers it (a held primary sinks; no layer).
        if (style == KitSurfaceStyle.brutal) continue;
        final look = KitSurfaceLook.resolve(
          roles: graphiteDark,
          style: style,
          role: KitSurfaceRole.stateLayer,
          solid: own,
          color: graphiteDark.accent,
          pressed: true,
        );
        expect(identical(look, own), isTrue, reason: '$style');
      }
    });

    test('a spec answers it apart from its face\'s highlight', () {
      final look = KitSurfaceLook.resolve(
        roles: graphiteDark,
        style: _made,
        role: KitSurfaceRole.stateLayer,
        solid: const BoxDecoration(),
        color: graphiteDark.accent,
        pressed: true,
      );
      expect((look as KitSurfaceDecoration).fill, _HookSpec.filled);
    });

    testWidgets('a held primary asks for its filled layer', (tester) async {
      await _pump(tester, KitButton.primary(label: 'Send', onPressed: () {}));
      final gesture = await tester.startGesture(
        tester.getCenter(find.text('Send')),
      );
      await tester.pump(const Duration(milliseconds: 200));
      final layers = tester
          .widgetList<DecoratedBox>(find.byType(DecoratedBox))
          .map((b) => b.decoration)
          .whereType<KitSurfaceDecoration>()
          .where((d) => d.fill == _HookSpec.filled);
      expect(layers, isNotEmpty);
      await gesture.up();
      await tester.pumpAndSettle();
    });

    testWidgets('a disabled button tells the style', (tester) async {
      await _pump(
        tester,
        const KitButton.primary(label: 'Start', onPressed: null),
      );
      expect(
        _HookSpec.seen
            .where((r) => r.role == KitSurfaceRole.control)
            .last
            .disabled,
        isTrue,
      );
    });
  });

  group('fields', () {
    test('the field halo follows the glow unless a style says', () {
      expect(const KitStyleTokens(defaults: KitKnobs()).fieldHaloShown, isTrue);
      expect(
        const KitStyleTokens(
          defaults: KitKnobs(),
          glow: KitStyleGlow(halo: false),
        ).fieldHaloShown,
        isFalse,
      );
      expect(
        const KitStyleTokens(
          defaults: KitKnobs(),
          fieldHalo: false,
        ).fieldHaloShown,
        isFalse,
      );
    });

    testWidgets('the ask field and the start box carry the same halo', (
      tester,
    ) async {
      final controller = TextEditingController();
      final focus = FocusNode();
      addTearDown(controller.dispose);
      addTearDown(focus.dispose);
      List<BoxShadow> halosOf(Finder host) => tester
          .widgetList<DecoratedBox>(
            find.descendant(of: host, matching: find.byType(DecoratedBox)),
          )
          .map((b) => b.decoration)
          .whereType<KitSurfaceDecoration>()
          .expand((d) => d.shadows)
          .toList();
      Widget fields() => Column(
        children: [
          KitAskField(
            controller: controller,
            focusNode: focus,
            hint: 'Ask for a change…',
            sendLabel: 'Send',
            onSend: () {},
          ),
          KitStartBox(
            controller: TextEditingController(),
            focusNode: FocusNode(),
            question: 'What should we build?',
          ),
        ],
      );
      await _pump(tester, fields());
      expect(halosOf(find.byType(KitAskField)), isNotEmpty);
      expect(halosOf(find.byType(KitStartBox)), isNotEmpty);
      _HookSpec.tokensNow = const KitStyleTokens(
        defaults: KitKnobs(),
        fieldHalo: false,
      );
      await _pump(tester, fields());
      expect(halosOf(find.byType(KitAskField)), isEmpty);
      expect(halosOf(find.byType(KitStartBox)), isEmpty);
    });

    testWidgets('kitFieldHalo under Solid is a plain shadow', (tester) async {
      late Decoration halo;
      const shadows = [BoxShadow(blurRadius: 4)];
      await _pump(
        tester,
        Builder(
          builder: (context) {
            halo = kitFieldHalo(
              context,
              look: const BoxDecoration(),
              radius: BorderRadius.zero,
              halo: shadows,
            );
            return const SizedBox();
          },
        ),
        style: KitSurfaceStyle.solid,
      );
      expect((halo as BoxDecoration).boxShadow, shadows);
    });

    testWidgets('a field tells the style it is hovered', (tester) async {
      await _pump(
        tester,
        KitField(
          label: 'Name',
          controller: TextEditingController(),
          fieldKey: const ValueKey('field'),
        ),
      );
      Color face() => tester
          .widgetList<DecoratedBox>(find.byType(DecoratedBox))
          .map((b) => b.decoration)
          .whereType<KitSurfaceDecoration>()
          .map((d) => d.fill)
          .firstWhere((c) => c == _HookSpec.field || c == _HookSpec.hover);
      expect(face(), _HookSpec.field);
      final mouse = await tester.createGesture(kind: PointerDeviceKind.mouse);
      await mouse.addPointer(location: Offset.zero);
      addTearDown(mouse.removePointer);
      await mouse.moveTo(tester.getCenter(find.byKey(const ValueKey('field'))));
      await tester.pumpAndSettle();
      expect(face(), _HookSpec.hover);
    });

    testWidgets('the one-time code is the style\'s field', (tester) async {
      await _pump(tester, const KitOneTimeCode(code: 'VPFI-KC2ZK'));
      expect(_HookSpec.seen.any((r) => r.role == KitSurfaceRole.field), isTrue);
      await _pump(
        tester,
        const KitOneTimeCode(code: 'VPFI-KC2ZK'),
        style: KitSurfaceStyle.solid,
      );
      final tokens = KitTokens.of(tester.element(find.byType(KitOneTimeCode)));
      final box = tester
          .widgetList<DecoratedBox>(
            find.descendant(
              of: find.byType(KitOneTimeCode),
              matching: find.byType(DecoratedBox),
            ),
          )
          .map((b) => b.decoration)
          .whereType<BoxDecoration>()
          .firstWhere((d) => d.color == tokens.insetSurface);
      expect(box.border, isNull);
    });
  });
}

Color _lineColor(WidgetTester tester, String key) => _paintedColor(
  tester.widget(
    find.descendant(
      of: find.byKey(ValueKey(key)),
      matching: find.byKey(const ValueKey('kit-divider-line')),
    ),
  ),
);

/// The colour a KitDivider's line paints (its private hairline's public
/// `color`).
Color _paintedColor(Widget line) =>
    ((line as ExcludeSemantics).child! as dynamic).color as Color;
