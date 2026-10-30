// Gate G14 (docs/ux-system/kit-v2.md §8.4): the modal parts from the
// keyboard on a PC. Esc closes the top one and obeys the draft and dirty
// rules; Enter confirms only a neutral question; Tab reaches every action.
// Runs with debugPlatformCapabilities set to desktop.
import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';

import 'kit_harness.dart';
import 'team_kit_test_support.dart' show pumpTeam;

const _pc = Size(1280, 800);

Future<bool?> _ask(
  WidgetTester tester,
  BuildContext context,
  KitConfirmKind kind, {
  void Function(bool)? onResult,
}) async {
  unawaited(
    showKitConfirm(
      context,
      title: 'Delete fox?',
      body: 'Removed from the server.',
      confirmLabel: 'Delete conversation',
      kind: kind,
    ).then((v) => onResult?.call(v)),
  );
  await tester.pumpAndSettle();
  return null;
}

/// Whether keyboard focus is on [target] itself or something inside it
/// (not on a container around it).
bool _focusIn(WidgetTester tester, Finder target) {
  final focused = FocusManager.instance.primaryFocus?.context;
  if (focused == null) return false;
  final element = tester.element(target);
  if (focused == element) return true;
  var found = false;
  (focused as Element).visitAncestorElements((ancestor) {
    if (ancestor == element) {
      found = true;
      return false;
    }
    return true;
  });
  return found;
}

/// Whether keyboard focus is on the button that shows [label].
bool _focusedOn(WidgetTester tester, String label) => _focusIn(
  tester,
  find
      .ancestor(
        of: find.text(label),
        matching: find.byWidgetPredicate((w) => w is ButtonStyleButton),
      )
      .first,
);

void main() {
  setUp(
    () => debugPlatformCapabilities = const PlatformCapabilities.linuxDesktop(),
  );
  tearDown(() => debugPlatformCapabilities = null);

  testWidgets('server trust: Tab reaches acceptance and Escape grants no trust', (
    tester,
  ) async {
    final context = await pumpKitHost(tester, size: _pc);
    bool? result;
    void openReview() {
      result = null;
      unawaited(
        showKitServerTrust(
          context,
          origin: 'https://private.example:4099',
          fingerprintSha256:
              '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef',
          certificateChanged: true,
        ).then((value) => result = value),
      );
    }

    Future<void> focusAcceptance() async {
      final accept = find.byKey(const ValueKey('server-trust-accept'));
      for (var i = 0; i < 12 && !_focusIn(tester, accept); i++) {
        await tester.sendKeyEvent(LogicalKeyboardKey.tab);
        await tester.pump();
      }
      expect(_focusIn(tester, accept), isTrue);
    }

    openReview();
    await tester.pumpAndSettle();
    expect(find.byType(KitServerTrustBody), findsOneWidget);
    await focusAcceptance();
    expect(result, isNull);
    await tester.sendKeyEvent(LogicalKeyboardKey.escape);
    await tester.pumpAndSettle();
    expect(result, isFalse);
    expect(find.byType(KitServerTrustBody), findsNothing);

    openReview();
    await tester.pumpAndSettle();
    await focusAcceptance();
    await tester.sendKeyEvent(LogicalKeyboardKey.enter);
    await tester.pumpAndSettle();
    expect(result, isTrue);
  });

  for (final milestone in [false, true]) {
    testWidgets('team row activates with keyboard: $milestone', (tester) async {
      var calls = 0;
      await pumpTeam(
        tester,
        milestone
            ? KitMilestoneRow(
                title: 'Milestone',
                status: 'Ready',
                onPressed: () => calls++,
              )
            : KitProjectRow(
                title: 'Project',
                status: 'Ready',
                onPressed: () => calls++,
              ),
      );
      await tester.sendKeyEvent(LogicalKeyboardKey.tab);
      await tester.pump();
      await tester.sendKeyEvent(LogicalKeyboardKey.enter);
      await tester.pump();
      expect(calls, 1);
    });
  }

  testWidgets('fact row that opens a thing activates with keyboard', (
    tester,
  ) async {
    var opened = 0;
    await tester.pumpWidget(
      MaterialApp(
        theme: AppTheme.dark(),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: Scaffold(
          body: KitFactRow(
            title: 'Dentist',
            text: 'Dentist appointment Wed 21 Oct 10:00.',
            provenance: KitProvenance.told,
            observedAt: DateTime(2026, 10, 8),
            open: () => opened++,
          ),
        ),
      ),
    );
    await tester.sendKeyEvent(LogicalKeyboardKey.tab);
    await tester.pump();
    await tester.sendKeyEvent(LogicalKeyboardKey.enter);
    await tester.pump();
    expect(opened, 1);
  });

  testWidgets('accent row: Tab reaches each swatch and Enter chooses it', (
    tester,
  ) async {
    final chosen = <String?>[];
    await tester.pumpWidget(
      MaterialApp(
        theme: AppTheme.dark(),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: Scaffold(
          body: KitAccentRow(
            semanticsLabel: 'Accent',
            matchLabel: 'Match theme',
            theme: graphiteDark,
            swatches: const [
              KitAccentSwatch(
                id: 'teal',
                color: Color(0xFF3CCFCF),
                label: 'Teal',
              ),
            ],
            selected: null,
            onChanged: chosen.add,
          ),
        ),
      ),
    );
    // Match theme first, then the swatch.
    await tester.sendKeyEvent(LogicalKeyboardKey.tab);
    await tester.sendKeyEvent(LogicalKeyboardKey.tab);
    await tester.pump();
    await tester.sendKeyEvent(LogicalKeyboardKey.enter);
    await tester.pump();
    expect(chosen, ['teal']);
  });

  testWidgets('window row is read-only: Tab passes over it', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        theme: AppTheme.dark(),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: const Scaffold(
          body: KitWindowRow(
            title: '5 hours',
            left: .4,
            valueLabel: '40% left · 14:10',
          ),
        ),
      ),
    );
    await tester.sendKeyEvent(LogicalKeyboardKey.tab);
    await tester.pump();
    expect(
      find.descendant(
        of: find.byType(KitWindowRow),
        matching: find.byType(Focus),
      ),
      findsNothing,
    );
    expect(
      find.descendant(
        of: find.byType(KitWindowRow),
        matching: find.byType(KitTappable),
      ),
      findsNothing,
    );
  });

  group('showKitConfirm', () {
    testWidgets('Esc cancels', (tester) async {
      final context = await pumpKitHost(tester, size: _pc);
      bool? result;
      await _ask(
        tester,
        context,
        KitConfirmKind.destructive,
        onResult: (v) => result = v,
      );
      await tester.sendKeyEvent(LogicalKeyboardKey.escape);
      await tester.pumpAndSettle();
      expect(result, isFalse);
      expect(find.text('Delete fox?'), findsNothing);
    });

    testWidgets('Enter confirms a neutral question', (tester) async {
      final context = await pumpKitHost(tester, size: _pc);
      bool? result;
      await _ask(
        tester,
        context,
        KitConfirmKind.neutral,
        onResult: (v) => result = v,
      );
      await tester.sendKeyEvent(LogicalKeyboardKey.enter);
      await tester.pumpAndSettle();
      expect(result, isTrue);
    });

    for (final kind in [
      KitConfirmKind.destructive,
      KitConfirmKind.stop,
      KitConfirmKind.discard,
    ]) {
      testWidgets('Enter does not confirm ${kind.name}; Tab to it does', (
        tester,
      ) async {
        final context = await pumpKitHost(tester, size: _pc);
        bool? result;
        await _ask(tester, context, kind, onResult: (v) => result = v);
        await tester.sendKeyEvent(LogicalKeyboardKey.enter);
        await tester.pumpAndSettle();
        expect(result, isNull);
        expect(find.text('Delete fox?'), findsOneWidget);
        // Tab reaches the confirm, then the cancel.
        var tabs = 0;
        while (!_focusedOn(tester, 'Delete conversation') && tabs < 6) {
          await tester.sendKeyEvent(LogicalKeyboardKey.tab);
          await tester.pump();
          tabs++;
        }
        expect(_focusedOn(tester, 'Delete conversation'), isTrue);
        // On a PC the answers sit in one row in reading order, the confirm
        // last (visual language §5): Shift+Tab from it reaches the cancel.
        await tester.sendKeyDownEvent(LogicalKeyboardKey.shift);
        await tester.sendKeyEvent(LogicalKeyboardKey.tab);
        await tester.sendKeyUpEvent(LogicalKeyboardKey.shift);
        await tester.pump();
        expect(
          _focusedOn(tester, KitConfirmSheet.cancelFor(context, kind)),
          isTrue,
        );
        // Tab back to the confirm: Enter on the focused button activates it.
        await tester.sendKeyEvent(LogicalKeyboardKey.tab);
        await tester.pump();
        expect(_focusedOn(tester, 'Delete conversation'), isTrue);
        await tester.sendKeyEvent(LogicalKeyboardKey.enter);
        await tester.pumpAndSettle();
        expect(result, isTrue);
      });
    }
  });

  group('showKitSheet', () {
    testWidgets('Esc closes a sheet with nothing to lose', (tester) async {
      final context = await pumpKitHost(tester, size: _pc);
      unawaited(
        showKitSheet<void>(
          context,
          title: 'Language',
          body: (_) => const Text('English'),
        ),
      );
      await tester.pumpAndSettle();
      await tester.sendKeyEvent(LogicalKeyboardKey.escape);
      await tester.pumpAndSettle();
      expect(find.text('Language'), findsNothing);
    });

    testWidgets('Esc with unsaved input asks in place; Esc again goes back', (
      tester,
    ) async {
      final context = await pumpKitHost(tester, size: _pc);
      final dirty = ValueNotifier(true);
      addTearDown(dirty.dispose);
      unawaited(
        showKitSheet<void>(
          context,
          title: 'Edit server',
          body: (_) => const TextField(),
          dirty: dirty,
        ),
      );
      await tester.pumpAndSettle();
      await tester.sendKeyEvent(LogicalKeyboardKey.escape);
      await tester.pumpAndSettle();
      expect(find.text('Discard your changes?'), findsOneWidget);
      await tester.sendKeyEvent(LogicalKeyboardKey.escape);
      await tester.pumpAndSettle();
      expect(find.text('Discard your changes?'), findsNothing);
      expect(find.text('Edit server'), findsOneWidget);
    });

    testWidgets('Esc from inside a text field still closes', (tester) async {
      final context = await pumpKitHost(tester, size: _pc);
      unawaited(
        showKitSheet<void>(
          context,
          title: 'Rename',
          body: (_) => const TextField(autofocus: true),
        ),
      );
      await tester.pumpAndSettle();
      await tester.enterText(find.byType(TextField), 'fox');
      await tester.sendKeyEvent(LogicalKeyboardKey.escape);
      await tester.pumpAndSettle();
      expect(find.text('Rename'), findsNothing);
    });

    testWidgets('Tab reaches the close button and every action', (
      tester,
    ) async {
      final context = await pumpKitHost(tester, size: _pc);
      unawaited(
        showKitSheet<void>(
          context,
          title: 'Language',
          body: (_) => const Text('English'),
          primary: KitAction(label: 'Use English', onPressed: () {}),
          secondary: KitAction(label: 'Keep current', onPressed: () {}),
          tertiary: [KitAction(label: 'More languages', onPressed: () {})],
        ),
      );
      await tester.pumpAndSettle();
      final reached = <String>{};
      for (var i = 0; i < 10; i++) {
        await tester.sendKeyEvent(LogicalKeyboardKey.tab);
        await tester.pump();
        for (final label in ['Use English', 'Keep current', 'More languages']) {
          if (_focusedOn(tester, label)) reached.add(label);
        }
        if (_focusIn(tester, find.byTooltip('Close'))) reached.add('close');
      }
      expect(reached, {
        'Use English',
        'Keep current',
        'More languages',
        'close',
      });
    });
  });

  group('showKitMenu', () {
    testWidgets('Esc closes the menu without choosing', (tester) async {
      final context = await pumpKitHost(tester, size: _pc);
      var runs = 0;
      var closed = false;
      KitMenuItem? result;
      unawaited(
        showKitMenu(
          context,
          items: [KitMenuItem(label: 'Archive', onSelected: () => runs++)],
        ).then((v) {
          result = v;
          closed = true;
        }),
      );
      await tester.pumpAndSettle();
      expect(find.text('Archive'), findsOneWidget);
      await tester.sendKeyEvent(LogicalKeyboardKey.escape);
      await tester.pumpAndSettle();
      expect(closed, isTrue);
      expect(result, isNull);
      expect(runs, 0);
      expect(find.text('Archive'), findsNothing);
    });
  });

  group('showKitTerm', () {
    testWidgets('Esc closes the explanation', (tester) async {
      final context = await pumpKitHost(tester, size: _pc);
      var closed = false;
      unawaited(
        showKitTerm(
          context,
          term: 'MCP',
          explanation: 'A way to give the assistant extra tools.',
          bubbleKey: const ValueKey('term-bubble'),
        ).then((_) => closed = true),
      );
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('term-bubble')), findsOneWidget);
      await tester.sendKeyEvent(LogicalKeyboardKey.escape);
      await tester.pumpAndSettle();
      expect(closed, isTrue);
      expect(find.byKey(const ValueKey('term-bubble')), findsNothing);
    });
  });

  // A 1×1 PNG.
  final png = base64Decode(
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==',
  );

  group('showKitImageGallery', () {
    testWidgets('arrows change picture and Esc closes', (tester) async {
      final context = await pumpKitHost(tester, size: _pc);
      var closed = false;
      unawaited(
        showKitImageGallery(
          context,
          galleryKey: const ValueKey('gallery'),
          pictures: [
            for (var i = 0; i < 3; i++)
              KitGalleryPicture(
                id: 'p$i',
                name: 'p$i.png',
                load: () async => png,
              ),
          ],
        ).then((_) => closed = true),
      );
      await tester.pumpAndSettle();
      expect(find.text('1 of 3'), findsOneWidget);
      await tester.sendKeyEvent(LogicalKeyboardKey.arrowRight);
      await tester.pumpAndSettle();
      expect(find.text('2 of 3'), findsOneWidget);
      await tester.sendKeyEvent(LogicalKeyboardKey.arrowLeft);
      await tester.pumpAndSettle();
      expect(find.text('1 of 3'), findsOneWidget);
      await tester.sendKeyEvent(LogicalKeyboardKey.escape);
      await tester.pumpAndSettle();
      expect(closed, isTrue);
      expect(find.byKey(const ValueKey('gallery')), findsNothing);
    });

    testWidgets('in right-to-left the left arrow is the next picture', (
      tester,
    ) async {
      final context = await pumpKitHost(
        tester,
        size: _pc,
        locale: const Locale('ar'),
      );
      unawaited(
        showKitImageGallery(
          context,
          pictures: [
            for (var i = 0; i < 3; i++)
              KitGalleryPicture(
                id: 'p$i',
                name: 'p$i.png',
                load: () async => png,
              ),
          ],
        ),
      );
      await tester.pumpAndSettle();
      await tester.sendKeyEvent(LogicalKeyboardKey.arrowLeft);
      await tester.pumpAndSettle();
      expect(find.text('2 من 3'), findsOneWidget);
    });
  });

  group('showKitImageMarkup', () {
    testWidgets('Tab reaches Done and Esc closes without a result', (
      tester,
    ) async {
      final context = await pumpKitHost(tester, size: _pc);
      var closed = false;
      KitMarkupResult? result;
      unawaited(
        showKitImageMarkup(
          context,
          bytes: png,
          name: 'p.png',
          markupKey: const ValueKey('markup'),
        ).then((r) {
          result = r;
          closed = true;
        }),
      );
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 600));
      await tester.runAsync(
        () => Future<void>.delayed(const Duration(milliseconds: 200)),
      );
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));
      expect(find.byKey(const ValueKey('kit-markup-done')), findsOneWidget);
      var reached = false;
      for (var i = 0; i < 20 && !reached; i++) {
        await tester.sendKeyEvent(LogicalKeyboardKey.tab);
        await tester.pump();
        reached = _focusIn(
          tester,
          find.byKey(const ValueKey('kit-markup-done')),
        );
      }
      expect(reached, isTrue);
      await tester.sendKeyEvent(LogicalKeyboardKey.escape);
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 600));
      expect(closed, isTrue);
      expect(result, isNull);
    });
  });
}
