// KitNotice v2 (docs/ux-system/kit-api/KitNotice.md): the cost line, the
// one-sentence offer, the error with
// its defaults (Copy details, Report a problem or the network fix), the
// KitReportHook seam, the error classifier, the tone colours, the one live
// region and reduced motion.
import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/semantics.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import '../support/no_red.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit_buttons.dart';
import 'package:opencode_mobile/ui/kit/kit_icon_button.dart';
import 'package:opencode_mobile/ui/kit/kit_notice.dart';
import 'package:opencode_mobile/ui/kit/kit_redact.dart';
import 'package:opencode_mobile/ui/kit/kit_tokens.dart';

import 'kit_motion_still.dart';

/// Named like the IO types the classifier knows, without importing dart:io.
class SocketException implements Exception {
  const SocketException();
}

class HandshakeException implements Exception {
  const HandshakeException();
}

class ClientException implements Exception {
  const ClientException();
}

const _fakeKey = 'sk-ant-api03-AbCdEfGhIjKlMnOpQrStUv';
const _fakeToken = 'tok3nAbCdEfGhIjKlMnOp';
const _details =
    'GET /file?path=src failed\n'
    'Authorization: Bearer $_fakeToken\n'
    'provider key $_fakeKey';

Future<void> _pump(
  WidgetTester tester,
  Widget child, {
  Size size = const Size(412, 915),
  double textScale = 1,
  Locale locale = const Locale('en'),
  bool light = false,
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    MaterialApp(
      debugShowCheckedModeBanner: false,
      theme: light ? AppTheme.light() : AppTheme.dark(),
      locale: locale,
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      builder: (context, app) => MediaQuery(
        data: MediaQuery.of(
          context,
        ).copyWith(textScaler: TextScaler.linear(textScale)),
        child: app!,
      ),
      home: Scaffold(
        body: SingleChildScrollView(
          padding: const EdgeInsets.symmetric(horizontal: 16),
          child: child,
        ),
      ),
    ),
  );
  await tester.pumpAndSettle();
}

SemanticsNode _root(WidgetTester tester) {
  var node = tester.getSemantics(find.byType(Scaffold));
  while (node.parent != null) {
    node = node.parent!;
  }
  return node;
}

/// Every live-region node on screen.
List<SemanticsNode> _liveRegions(WidgetTester tester) {
  final found = <SemanticsNode>[];
  void visit(SemanticsNode node) {
    if (node.getSemanticsData().flagsCollection.isLiveRegion) found.add(node);
    node.visitChildren((child) {
      visit(child);
      return true;
    });
  }

  visit(_root(tester));
  return found;
}

Color _iconColour(WidgetTester tester) => tester
    .widget<Icon>(
      find.descendant(of: find.byType(KitNotice), matching: find.byType(Icon)),
    )
    .color!;

ThemeRoles _roles(WidgetTester tester) =>
    KitTokens.of(tester.element(find.byType(KitNotice))).roles;

void main() {
  tearDown(() => KitReportHook.handler = null);

  group('cost', () {
    testWidgets('joins the items with a dot and is not a live region', (
      tester,
    ) async {
      final semantics = tester.ensureSemantics();
      await _pump(
        tester,
        const KitNotice.cost(['About 208 MB', 'about 4 min']),
      );
      expect(find.text('About 208 MB · about 4 min'), findsOneWidget);
      expect(_liveRegions(tester), isEmpty);
      semantics.dispose();
    });

    testWidgets('an optional title sits above the line', (tester) async {
      await _pump(
        tester,
        const KitNotice.cost([
          'About 550 MB memory per worker',
          'uses battery while it works',
        ], title: 'Before you start'),
      );
      final title = tester.getRect(find.text('Before you start'));
      final line = tester.getRect(
        find.text(
          'About 550 MB memory per worker · uses battery while it works',
        ),
      );
      expect(title.bottom, lessThanOrEqualTo(line.top));
    });
  });

  group('offer', () {
    const message = 'This server also runs an AI team. Turn it on?';

    Widget offer({
      VoidCallback? onAction,
      VoidCallback? onDismiss,
      String text = message,
    }) => KitNotice.offer(
      key: const ValueKey('team-offer'),
      message: text,
      action: KitAction(
        key: const ValueKey('team-offer-turn-on'),
        label: 'Turn on',
        onPressed: onAction ?? () {},
      ),
      onDismiss: onDismiss ?? () {},
      dismissKey: const ValueKey('team-offer-not-now'),
      dismissLabel: 'Not now',
    );

    testWidgets('one action and one close, by the keys passed in', (
      tester,
    ) async {
      var actions = 0;
      var dismissals = 0;
      await _pump(
        tester,
        offer(onAction: () => actions += 1, onDismiss: () => dismissals += 1),
      );
      // One action, by its words, and one close: nothing else to tap.
      expect(find.text('Turn on'), findsOneWidget);
      expect(find.byType(KitButton), findsOneWidget);
      expect(find.byType(KitIconButton), findsOneWidget);
      expect(find.byTooltip('Not now'), findsOneWidget);
      expect(find.byKey(const ValueKey('team-offer')), findsOneWidget);
      await tester.tap(find.byKey(const ValueKey('team-offer-turn-on')));
      await tester.tap(find.byKey(const ValueKey('team-offer-not-now')));
      expect((actions, dismissals), (1, 1));
    });

    testWidgets('a short sentence keeps its controls on its line', (
      tester,
    ) async {
      await _pump(tester, offer(text: 'Pin it?'), size: const Size(800, 600));
      final words = tester.getCenter(find.text('Pin it?')).dy;
      final action = tester.getCenter(
        find.byKey(const ValueKey('team-offer-turn-on')),
      );
      expect((action.dy - words).abs(), lessThan(1));
    });

    for (final locale in const [Locale('en'), Locale('ar')]) {
      testWidgets(
        'at 2.0 text the close ends the first line and the action starts '
        'under the sentence at its text inset (${locale.languageCode})',
        (tester) async {
          await _pump(
            tester,
            offer(),
            size: const Size(360, 800),
            textScale: 2,
            locale: locale,
          );
          expect(tester.takeException(), isNull);
          final sentence = tester.getRect(find.text(message));
          final action = tester.getRect(
            find.byKey(const ValueKey('team-offer-turn-on')),
          );
          final close = tester.getRect(
            find.byKey(const ValueKey('team-offer-not-now')),
          );
          // The close sits on the sentence's first line, not by the action.
          expect(close.center.dy, lessThan(sentence.top + 48));
          expect(close.center.dy, lessThan(action.top));
          expect(action.top, greaterThanOrEqualTo(sentence.bottom - 1));
          // The action's words start where the sentence's words start.
          final words = tester.getRect(find.text('Turn on'));
          expect(
            locale.languageCode == 'ar'
                ? (words.right - sentence.right).abs()
                : (words.left - sentence.left).abs(),
            lessThanOrEqualTo(1),
          );
          expect(close.height, greaterThanOrEqualTo(48));
          expect(close.width, greaterThanOrEqualTo(48));
          // The close sits at the end in both directions.
          expect(
            locale.languageCode == 'ar'
                ? close.center.dx < 180
                : close.center.dx > 180,
            isTrue,
          );
          final text = tester.widget<Text>(find.text(message));
          expect(text.maxLines, isNull);
          expect(text.overflow, isNull);
        },
      );
    }
  });

  group('error', () {
    KitNotice network(Object error, {String? details}) => KitNotice.error(
      message: 'Couldn’t load files',
      error: error,
      details: details,
      retry: KitAction(label: 'Try again', onPressed: () {}),
      switchServer: KitAction(label: 'Switch server', onPressed: () {}),
    );

    for (final (name, error) in <(String, Object)>[
      ('TimeoutException', TimeoutException('slow')),
      ('SocketException', const SocketException()),
    ]) {
      testWidgets('$name offers Try again and Switch server, no report', (
        tester,
      ) async {
        KitReportHook.handler = (_, _) async {};
        await _pump(tester, network(error, details: _details));
        expect(find.text('Try again'), findsOneWidget);
        expect(find.text('Switch server'), findsOneWidget);
        expect(find.text('Report a problem'), findsNothing);
        expect(find.byTooltip('Copy details'), findsOneWidget);
      });
    }

    testWidgets('no details, no Copy details', (tester) async {
      await _pump(tester, network(const SocketException()));
      expect(find.byTooltip('Copy details'), findsNothing);
    });

    testWidgets('errorKind overrides the classifier', (tester) async {
      await _pump(
        tester,
        KitNotice.error(
          message: 'Couldn’t reach the server',
          error: const FormatException('bad'),
          errorKind: KitErrorKind.network,
          switchServer: KitAction(label: 'Switch server', onPressed: () {}),
        ),
      );
      expect(find.text('Switch server'), findsOneWidget);
    });

    testWidgets('another failure offers Report a problem when a handler is '
        'set; one tap sends a redacted report', (tester) async {
      final reports = <KitReport>[];
      KitReportHook.handler = (_, report) async => reports.add(report);
      await _pump(
        tester,
        KitNotice.error(
          message: 'Couldn’t load files',
          error: const FormatException('unexpected character'),
          details: _details,
          reportSource: 'files-viewer',
          reportKey: const ValueKey('files-report'),
          retry: KitAction(label: 'Try again', onPressed: () {}),
          switchServer: KitAction(label: 'Switch server', onPressed: () {}),
        ),
      );
      expect(find.text('Try again'), findsOneWidget);
      expect(find.text('Switch server'), findsNothing);
      await tester.tap(find.byKey(const ValueKey('files-report')));
      await tester.pump();

      expect(reports, hasLength(1));
      final report = reports.single;
      expect(report.title, 'Couldn’t load files');
      expect(report.source, 'files-viewer');
      expect(report.errorType, 'FormatException');
      expect(report.details, isNot(contains(_fakeToken)));
      expect(report.details, isNot(contains('AbCdEfGhIjKlMnOp')));
      expect(report.details, contains(KitRedact.mask));
      expect(report.details, contains('GET /file?path=src failed'));
    });

    testWidgets('without a handler there is no Report a problem', (
      tester,
    ) async {
      await _pump(
        tester,
        KitNotice.error(
          message: 'Couldn’t save',
          error: StateError('x'),
          details: _details,
          retry: KitAction(label: 'Try again', onPressed: () {}),
        ),
      );
      expect(KitReportHook.available, isFalse);
      expect(find.text('Report a problem'), findsNothing);
      expect(find.byTooltip('Copy details'), findsOneWidget);
    });

    testWidgets('the report is reached in two taps from the error (P8.3)', (
      tester,
    ) async {
      final sent = <KitReport>[];
      // The app's flow: a preview of exactly what is sent, then Send.
      KitReportHook.handler = (context, report) => showDialog<void>(
        context: context,
        builder: (dialog) => AlertDialog(
          content: Text(report.details ?? report.title),
          actions: [
            TextButton(
              onPressed: () {
                sent.add(report);
                Navigator.pop(dialog);
              },
              child: const Text('Send'),
            ),
          ],
        ),
      );
      await _pump(
        tester,
        const KitNotice.error(
          message: 'Couldn’t load the diff',
          error: FormatException('bad hunk'),
          details: _details,
        ),
      );
      var taps = 0;
      await tester.tap(find.text('Report a problem'));
      taps += 1;
      await tester.pumpAndSettle();
      // The preview shows the redacted details, never the secret.
      expect(find.textContaining(_fakeToken), findsNothing);
      expect(find.textContaining('GET /file?path=src failed'), findsOneWidget);
      await tester.tap(find.text('Send'));
      taps += 1;
      await tester.pumpAndSettle();
      expect(sent, hasLength(1));
      expect(taps, lessThanOrEqualTo(2));
    });

    group('Copy details', () {
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

      testWidgets('copies the redacted details, says Copied once, no '
          'SnackBar', (tester) async {
        await _pump(
          tester,
          const KitNotice.error(
            message: 'Couldn’t load files',
            error: FormatException('x'),
            details: _details,
            copyDetailsKey: ValueKey('files-copy-details'),
          ),
        );
        await tester.tap(find.byKey(const ValueKey('files-copy-details')));
        await tester.pumpAndSettle();

        final copies = platform.where((c) => c.method == 'Clipboard.setData');
        expect(copies, hasLength(1));
        final text = (copies.single.arguments as Map)['text'] as String;
        expect(text, KitRedact.text(_details));
        expect(text, isNot(contains(_fakeToken)));
        expect(text, isNot(contains('AbCdEfGhIjKlMnOp')));
        expect(announcements, hasLength(1));
        expect((announcements.single['data'] as Map)['message'], 'Copied');
        expect(find.byType(SnackBar), findsNothing);
      });
    });
  });

  group('KitErrorKind.of', () {
    test('timeouts and IO types by name are network', () {
      expect(KitErrorKind.of(TimeoutException('t')), KitErrorKind.network);
      expect(KitErrorKind.of(const SocketException()), KitErrorKind.network);
      expect(KitErrorKind.of(const HandshakeException()), KitErrorKind.network);
      expect(KitErrorKind.of(const ClientException()), KitErrorKind.network);
    });

    test('anything else, and null, is other', () {
      expect(KitErrorKind.of(const FormatException('x')), KitErrorKind.other);
      expect(KitErrorKind.of(StateError('x')), KitErrorKind.other);
      expect(KitErrorKind.of('SocketException'), KitErrorKind.other);
      expect(KitErrorKind.of(null), KitErrorKind.other);
    });
  });

  group('KitReportHook', () {
    testWidgets('report is a no-op without a handler', (tester) async {
      await _pump(tester, const KitNotice(message: 'x'));
      final context = tester.element(find.byType(KitNotice));
      expect(KitReportHook.available, isFalse);
      await KitReportHook.report(context, const KitReport(title: 'x'));
    });

    test('redact is the kit redactor', () {
      expect(KitReportHook.redact(_details), KitRedact.text(_details));
      expect(KitReportHook.redact('plain words'), 'plain words');
    });
  });

  group('tones', () {
    for (final light in [false, true]) {
      final mode = light ? 'light' : 'dark';
      testWidgets('warning and failure paint text1, never amber or danger '
          '($mode)', (tester) async {
        for (final tone in [AppStatusTone.attention, AppStatusTone.failure]) {
          await _pump(
            tester,
            KitNotice(message: 'Battery saver is on', tone: tone),
            light: light,
          );
          final roles = _roles(tester);
          expect(_iconColour(tester), roles.text1, reason: '$tone');
          expect(_iconColour(tester), isNot(roles.attention));
          expect(_iconColour(tester), isNotRed);
        }
      });

      testWidgets('neutral text2, working accent, ok success ($mode)', (
        tester,
      ) async {
        for (final (tone, role)
            in <(AppStatusTone, Color Function(ThemeRoles))>[
              (AppStatusTone.neutral, (r) => r.text2),
              (AppStatusTone.progress, (r) => r.accent),
              (AppStatusTone.ok, (r) => r.success),
            ]) {
          await _pump(
            tester,
            KitNotice(message: 'Checked', tone: tone),
            light: light,
          );
          expect(_iconColour(tester), role(_roles(tester)), reason: '$tone');
        }
      });
    }

    testWidgets('the error form paints its glyph in text1', (tester) async {
      await _pump(tester, const KitNotice.error(message: 'Couldn’t save'));
      expect(_iconColour(tester), _roles(tester).text1);
    });
  });

  group('plain notice', () {
    testWidgets('shows at most two actions and a labelled dismiss', (
      tester,
    ) async {
      var dismissed = 0;
      await _pump(
        tester,
        KitNotice(
          message: 'The key was not accepted',
          actions: [
            for (final label in ['One', 'Two', 'Three'])
              KitAction(label: label, onPressed: () {}),
          ],
          onDismiss: () => dismissed += 1,
        ),
      );
      expect(find.text('One'), findsOneWidget);
      expect(find.text('Two'), findsOneWidget);
      expect(find.text('Three'), findsNothing);
      expect(find.byTooltip('Dismiss'), findsOneWidget);
      final close = tester.getSize(
        find.byKey(const ValueKey('kit-notice-dismiss')),
      );
      expect(close.width, greaterThanOrEqualTo(48));
      expect(close.height, greaterThanOrEqualTo(48));
      await tester.tap(find.byKey(const ValueKey('kit-notice-dismiss')));
      expect(dismissed, 1);
    });

    testWidgets('dismissKey and dismissLabel replace the defaults', (
      tester,
    ) async {
      await _pump(
        tester,
        KitNotice(
          message: 'Saved',
          onDismiss: () {},
          dismissKey: const ValueKey('saved-close'),
          dismissLabel: 'Hide',
        ),
      );
      expect(find.byKey(const ValueKey('saved-close')), findsOneWidget);
      expect(find.byTooltip('Hide'), findsOneWidget);
    });

    testWidgets('the retired card still draws', (tester) async {
      await _pump(
        tester,
        const KitNotice.card(message: 'Allow editing?', title: 'Permission'),
      );
      expect(find.text('Allow editing?'), findsOneWidget);
    });
  });

  kitMotionStillTests(
    'KitNotice',
    builds: {
      'cost': () => const KitNotice.cost(['About 208 MB', 'about 4 min']),
      'offer': () => KitNotice.offer(
        message: 'Pin it?',
        action: KitAction(label: 'Pin', onPressed: () {}),
        onDismiss: () {},
      ),
      'error': () => KitNotice.error(
        message: 'Couldn’t load files',
        error: TimeoutException('slow'),
        details: 'trace',
        retry: KitAction(label: 'Try again', onPressed: () {}),
      ),
    },
    changes: {
      'the verdict turns': KitMotionChange(
        build: () => const KitNotice(message: 'Checking'),
        act: (tester, stage) => stage.rebuild(
          const KitNotice(message: 'Answered', tone: AppStatusTone.ok),
        ),
        shows: 'Answered',
      ),
    },
  );

  // Flat polish (sweep 3730: "Add an API key", "Sign in to Claude Code",
  // "Choose a look first"): bare words under a notice read as a caption.
  testWidgets('its actions are small tonal buttons, starting where the words '
      'do', (tester) async {
    await _pump(
      tester,
      KitNotice(
        message: 'Using the free model. Add a key to use your own.',
        actions: [KitAction(label: 'Add an API key', onPressed: () {})],
      ),
    );
    final button = tester.widget<KitButton>(
      find.ancestor(
        of: find.text('Add an API key'),
        matching: find.byType(KitButton),
      ),
    );
    expect(button.role, KitButtonRole.secondary);
    expect(button.expand, isFalse);
    expect(
      tester
          .getTopLeft(
            find.ancestor(
              of: find.text('Add an API key'),
              matching: find.byType(KitButton),
            ),
          )
          .dx,
      tester.getTopLeft(find.textContaining('Using the free model')).dx,
    );
  });
}
