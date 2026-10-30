// KitConfirmSheet.md "What changes" rows 1-7 and decision D22, owned by
// kit-KitDetailsFold: the mark is the library's one header tile (neutral
// surface3, never the accent, never red, for every kind), consequences draw through KitConsequences (lost-then-info for
// plain facts, consequenceItems as given, never both), details through the
// public KitDetailsFold, the typed name isolated by KitBidi.ltr with no
// literal isolate in the source, the title and body wrap at 200 %, routes
// closes the question, and reduced motion settles in one pump (G8).
import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_test/flutter_test.dart';
import '../support/no_red.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/widgets/request_routes.dart';

import '../../tool/capture/fixtures.dart' show loadCaptureFonts;
import 'kit_harness.dart';

Future<bool?> Function() _open(
  WidgetTester tester,
  BuildContext context, {
  KitConfirmKind kind = KitConfirmKind.destructive,
  List<String> consequences = const [],
  List<KitConsequence>? consequenceItems,
  String? typedName,
  List<KitTechnicalValue> details = const [],
  Future<void> Function()? action,
  RequestRoutes? routes,
}) {
  bool? result;
  unawaited(
    showKitConfirm(
      context,
      title: 'Delete fox?',
      body: 'The conversation is removed from the server.',
      confirmLabel: 'Delete conversation',
      kind: kind,
      consequences: consequences,
      consequenceItems: consequenceItems,
      typedName: typedName,
      details: details,
      action: action,
      routes: routes,
    ).then((value) => result = value),
  );
  return () async => result;
}

/// The one 44 dp header tile the confirmation draws: the fill and corners
/// it paints (under Solid, its surface's Material).
({Color? color, BorderRadiusGeometry? borderRadius}) _tile(
  WidgetTester tester,
  KitTokens tokens,
) {
  final square = find.descendant(
    of: find.byType(KitConfirmSheet),
    matching: find.byWidgetPredicate(
      (w) =>
          w is SizedBox &&
          w.width == tokens.markSize &&
          w.height == tokens.markSize,
    ),
  );
  final tile = tester.widget<Material>(
    find.ancestor(of: square, matching: find.byType(Material)).first,
  );
  final shape = tile.shape;
  return (
    color: tile.color,
    borderRadius: shape is RoundedRectangleBorder ? shape.borderRadius : null,
  );
}

Iterable<IconData?> _consequenceIcons(WidgetTester tester) => tester
    .widgetList<Icon>(
      find.descendant(
        of: find.byKey(const ValueKey('kit-consequences')),
        matching: find.byType(Icon),
      ),
    )
    .map((icon) => icon.icon);

void main() {
  group('rows 1-2: the mark is the header tile (LOOK-5, LOOK-6)', () {
    testWidgets('neutral: a surface3 tile, text1 glyph, no accent, no red', (
      tester,
    ) async {
      final context = await pumpKitHost(tester);
      final tokens = KitTokens.of(context);
      final roles = tokens.roles;
      _open(tester, context, kind: KitConfirmKind.neutral);
      await tester.pumpAndSettle();
      final tile = _tile(tester, tokens);
      expect(tile.color, roles.surface3);
      expect(tile.borderRadius, BorderRadius.circular(tokens.markRadius));
      final glyph = tester.widget<Icon>(
        find.byIcon(KitConfirmSheet.iconFor(KitConfirmKind.neutral)),
      );
      expect(glyph.color, roles.text1);
      expect(glyph.color, isNot(roles.accent));
      expect(glyph.color, isNotRed);
      final fill = filledButton(
        tester,
        'Delete conversation',
      ).style?.backgroundColor?.resolve({});
      expect(fill, isNotRed);
    });

    testWidgets('stop, destructive, discard: neutral tile, strong neutral '
        'confirm, nothing red anywhere in the question', (tester) async {
      final context = await pumpKitHost(tester);
      final tokens = KitTokens.of(context);
      final roles = tokens.roles;
      for (final kind in KitConfirmKind.values) {
        _open(
          tester,
          context,
          kind: kind,
          consequences: const ['Its messages are removed'],
          typedName: kind == KitConfirmKind.destructive ? 'fox' : null,
          details: const [],
        );
        await tester.pumpAndSettle();
        final tile = _tile(tester, tokens);
        expect(tile.color, roles.surface3, reason: '$kind');
        // The consequences may draw the same icon; the glyph is the tile's.
        final glyph = tester.widget<Icon>(
          find.byIcon(KitConfirmSheet.iconFor(kind)).first,
        );
        expect(glyph.color, roles.text1, reason: '$kind');
        final fill = filledButton(
          tester,
          'Delete conversation',
        ).style?.backgroundColor?.resolve({});
        if (kind == KitConfirmKind.neutral) {
          expect(fill, isNot(roles.text1), reason: '$kind');
        } else {
          // Safe by its confirmation, not by a colour: the strong neutral.
          expect(fill, roles.text1, reason: '$kind');
        }
        final all = paintedColours(tester, find.byType(KitConfirmSheet));
        expect(all, isNotEmpty);
        expect(all.where(isRedHue), isEmpty, reason: '$kind paints a red');
        await tester.tap(find.byKey(const ValueKey('kit-confirm-cancel')));
        await tester.pumpAndSettle();
      }
    });

    testWidgets('the tile is out of semantics', (tester) async {
      final handle = tester.ensureSemantics();
      final context = await pumpKitHost(tester);
      _open(tester, context);
      await tester.pumpAndSettle();
      expect(
        find.ancestor(
          of: find.byIcon(KitConfirmSheet.iconFor(KitConfirmKind.destructive)),
          matching: find.byType(ExcludeSemantics),
        ),
        findsWidgets,
      );
      handle.dispose();
    });
  });

  group('row 3: consequences through KitConsequences (D22)', () {
    testWidgets('destructive: the first fact is lost, the rest info', (
      tester,
    ) async {
      final context = await pumpKitHost(tester);
      _open(
        tester,
        context,
        consequences: const ['3 queued prompts', '2 drafts', '1 stash'],
      );
      await tester.pumpAndSettle();
      expect(_consequenceIcons(tester), [
        AppIconography.warning,
        AppIconography.info,
        AppIconography.info,
      ]);
    });

    testWidgets('neutral: every fact is info', (tester) async {
      final context = await pumpKitHost(tester);
      _open(
        tester,
        context,
        kind: KitConfirmKind.neutral,
        consequences: const ['Anyone with the link can read it', 'Two files'],
      );
      await tester.pumpAndSettle();
      expect(_consequenceIcons(tester), [
        AppIconography.info,
        AppIconography.info,
      ]);
    });

    testWidgets('separators are one physical pixel', (tester) async {
      final context = await pumpKitHost(tester);
      _open(tester, context, consequences: const ['One', 'Two', 'Three']);
      await tester.pumpAndSettle();
      final dividers = tester.widgetList<Divider>(
        find.descendant(
          of: find.byKey(const ValueKey('kit-consequences')),
          matching: find.byType(Divider),
        ),
      );
      expect(dividers, hasLength(2));
      final dpr = tester.view.devicePixelRatio;
      for (final divider in dividers) {
        expect(divider.thickness, 1 / dpr);
      }
    });

    testWidgets('consequenceItems draw as given, with the kept mark', (
      tester,
    ) async {
      final context = await pumpKitHost(tester);
      _open(
        tester,
        context,
        consequenceItems: const [
          KitConsequence(
            'The branch is deleted',
            mark: KitConsequenceMark.lost,
          ),
          KitConsequence(
            '2 edited files are kept',
            mark: KitConsequenceMark.kept,
          ),
        ],
      );
      await tester.pumpAndSettle();
      expect(find.text('2 edited files are kept'), findsOneWidget);
      expect(_consequenceIcons(tester), [
        AppIconography.warning,
        AppIconography.check,
      ]);
    });

    test('passing both consequences and consequenceItems asserts', () {
      final facts = ['3 queued prompts'];
      final items = [const KitConsequence('2 edited files are kept')];
      expect(
        () => KitConfirmSheet(
          title: 'Delete fox?',
          body: 'Removed.',
          confirmLabel: 'Delete',
          onConfirm: () {},
          onCancel: () {},
          consequences: facts,
          consequenceItems: items,
        ),
        throwsAssertionError,
      );
    });

    testWidgets('showKitConfirm asserts on both too', (tester) async {
      final context = await pumpKitHost(tester);
      expect(
        () => showKitConfirm(
          context,
          title: 'Delete fox?',
          body: 'Removed.',
          confirmLabel: 'Delete',
          consequences: const ['3 queued prompts'],
          consequenceItems: const [KitConsequence('kept')],
        ),
        throwsAssertionError,
      );
    });
  });

  group('row 4: details through the public KitDetailsFold', () {
    const details = [
      KitTechnicalValue('Branch', 'feat/fox', key: Key('branch')),
      KitTechnicalValue('Branch again', 'feat/fox'),
      KitTechnicalValue('Path', 'src/a.dart'),
    ];

    testWidgets('collapsed first; the toggle opens it; a value shows once', (
      tester,
    ) async {
      final context = await pumpKitHost(tester, size: const Size(412, 1200));
      _open(tester, context, details: details);
      await tester.pumpAndSettle();
      expect(find.byType(KitDetailsFold), findsOneWidget);
      expect(find.text('feat/fox'), findsNothing);
      await tester.tap(find.byKey(const ValueKey('kit-details-toggle')));
      await tester.pumpAndSettle();
      expect(find.text('feat/fox'), findsOneWidget);
      expect(find.text('src/a.dart'), findsOneWidget);
    });

    testWidgets('values are left to right and selectable', (tester) async {
      final context = await pumpKitHost(
        tester,
        size: const Size(412, 1200),
        locale: const Locale('ar'),
      );
      _open(tester, context, details: details);
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('kit-details-toggle')));
      await tester.pumpAndSettle();
      final value = find.byKey(const Key('branch'));
      expect(
        tester
            .element(value)
            .dependOnInheritedWidgetOfExactType<Directionality>()!
            .textDirection,
        TextDirection.ltr,
      );
      expect(
        find.descendant(of: value, matching: find.byType(SelectableText)),
        findsOneWidget,
      );
    });

    testWidgets('a value the redactor matches is refused', (tester) async {
      final context = await pumpKitHost(tester);
      _open(
        tester,
        context,
        details: const [
          KitTechnicalValue('Provider key', 'sk-ant-FAKEFAKEFAKE0123'),
        ],
      );
      await tester.pumpAndSettle();
      final error = tester.takeException();
      expect(error, isA<FlutterError>());
      expect('$error', contains('Provider key'));
    });
  });

  group('row 5: the typed name (COPY-30)', () {
    testWidgets('the label isolates the name with KitBidi.ltr', (tester) async {
      final context = await pumpKitHost(tester);
      _open(tester, context, typedName: 'feat/fox');
      await tester.pumpAndSettle();
      final l10n = AppLocalizations.of(context);
      final field = tester.widget<TextField>(
        find.byKey(const ValueKey('kit-confirm-typed-name')),
      );
      expect(
        field.decoration?.labelText,
        l10n.kitConfirmTypeName(KitBidi.ltr('feat/fox')),
      );
      expect(field.textDirection, TextDirection.ltr);
      final tokens = KitTokens.of(context);
      expect(field.style?.fontSize, 13);
      expect(field.style, tokens.typedName);
    });

    test('no literal isolate in kit_confirm_sheet.dart', () {
      final source = File(
        'lib/ui/kit/kit_confirm_sheet.dart',
      ).readAsStringSync();
      expect(source, isNot(matches(RegExp('[\u2066-\u2069]'))));
      expect(source, isNot(matches(RegExp(r'\\u206[6-9]'))));
    });
  });

  group('row 6 and 200 % text (A11Y-8)', () {
    // The app's real faces: the test font's square glyphs would break every
    // word and say nothing about a real phone.
    setUpAll(loadCaptureFonts);

    for (final width in [320.0, 360.0, 412.0]) {
      testWidgets('at 2.0 and $width wide: wraps, nothing cut or overflowed', (
        tester,
      ) async {
        tester.view.physicalSize = Size(width, 800);
        tester.view.devicePixelRatio = 1;
        addTearDown(tester.view.reset);
        late BuildContext context;
        await tester.pumpWidget(
          MaterialApp(
            theme: AppTheme.dark(),
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            builder: (context, child) => MediaQuery(
              data: MediaQuery.of(
                context,
              ).copyWith(textScaler: const TextScaler.linear(2)),
              child: child!,
            ),
            home: Scaffold(
              body: Builder(
                builder: (inner) {
                  context = inner;
                  return const SizedBox.expand();
                },
              ),
            ),
          ),
        );
        _open(
          tester,
          context,
          consequences: const ['3 queued prompts will be deleted'],
        );
        await tester.pumpAndSettle();
        expect(tester.takeException(), isNull);
        final title = tester.renderObject<RenderParagraph>(
          find.descendant(
            of: find.byType(KitConfirmSheet),
            matching: find.text('Delete fox?'),
          ),
        );
        expect(title.didExceedMaxLines, isFalse);
        expect(find.byType(KitText), findsWidgets);
        for (final label in ['Delete conversation', 'Cancel']) {
          final finder = find.text(label);
          await tester.ensureVisible(finder);
          await tester.pumpAndSettle();
          final rect = tester.getRect(finder);
          expect(rect.left, greaterThanOrEqualTo(0), reason: label);
          expect(rect.right, lessThanOrEqualTo(width), reason: label);
          expect(rect.bottom, lessThanOrEqualTo(800), reason: label);

          expect(
            tester.renderObject<RenderParagraph>(finder).didExceedMaxLines,
            isFalse,
            reason: label,
          );
        }
      });
    }
  });

  testWidgets('routes: answered elsewhere closes the question, false', (
    tester,
  ) async {
    final context = await pumpKitHost(tester);
    var pending = true;
    final changes = ChangeNotifier();
    addTearDown(changes.dispose);
    final result = _open(
      tester,
      context,
      routes: RequestRoutes(changes: changes, isPending: () => pending),
    );
    await tester.pumpAndSettle();
    expect(find.text('Delete fox?'), findsOneWidget);
    pending = false;
    changes.notifyListeners();
    await tester.pump();
    await tester.pumpAndSettle();
    expect(find.text('Delete fox?'), findsNothing);
    expect(await result(), isFalse);
  });

  group('reduced motion settles in one pump (G8)', () {
    const off = KitEffects(motion: KitMotionLevel.off);

    testWidgets('the failed state', (tester) async {
      final context = await pumpKitHost(tester, effects: off);
      _open(tester, context, action: () async => throw StateError('nope'));
      await tester.pumpAndSettle();
      // Pressed through its callback: a synthetic tap starts Material's
      // ink splash, framework feedback that ignores reduced motion.
      filledButton(tester, 'Delete conversation').onPressed!();
      await tester.pump();
      expect(find.byKey(const ValueKey('kit-confirm-failed')), findsOneWidget);
      expect(tester.hasRunningAnimations, isFalse);
    });

    testWidgets('the in-place swap inside a sheet', (tester) async {
      final context = await pumpKitHost(tester, effects: off);
      unawaited(
        showKitSheet<void>(
          context,
          title: 'Workspace',
          body: (inner) => TextButton(
            onPressed: () => unawaited(
              showKitConfirm(
                inner,
                title: 'Delete fox?',
                body: 'Removed.',
                confirmLabel: 'Delete conversation',
                kind: KitConfirmKind.destructive,
              ),
            ),
            child: const Text('Delete'),
          ),
        ),
      );
      await tester.pumpAndSettle();
      tester
          .widget<TextButton>(
            find.ancestor(
              of: find.text('Delete'),
              matching: find.byType(TextButton),
            ),
          )
          .onPressed!();
      await tester.pump();
      expect(find.text('Delete fox?'), findsOneWidget);
      expect(tester.hasRunningAnimations, isFalse);
    });
  });
}
