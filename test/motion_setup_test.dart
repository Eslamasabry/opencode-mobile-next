// Slice A of the motion and illustration pass
// (docs/design/motion-and-illustration-2026-09-25.md): setup and connecting.
//
// - Setup start leads with its drawing at the top, not an empty band
//   (design regressions ledger rows 1–2).
// - Setup progress keeps its title and bar in view while the log is open;
//   the log is one box, one line per line (ledger row 16).
// - Setup ready is one step: the celebration, one name field, and the
//   folder sheet only when asked.
// - Connecting states show the drawing that fits; only a wait loops.
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/kit/scenes/setup_phone_scene.dart';
import 'package:opencode_mobile/ui/kit/scenes/setup_ready_scene.dart';
import 'package:opencode_mobile/ui/kit/scenes/setup_steps_scene.dart';
import 'package:opencode_mobile/ui/kit/scenes/setup_unplugged_scene.dart';
import 'package:opencode_mobile/ui/widgets/saved_server_connection_card.dart';

import '../tool/capture/fixtures.dart' show loadCaptureFonts;
import 'support/phone_setup_scenes.dart';

void _mockSecureStorage(WidgetTester tester) {
  const secure = MethodChannel('plugins.it_nomads.com/flutter_secure_storage');
  tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
    secure,
    (call) async => call.method == 'readAll' ? <String, String>{} : null,
  );
  addTearDown(
    () => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
      secure,
      null,
    ),
  );
}

Future<void> _scene(WidgetTester tester, String name) async {
  _mockSecureStorage(tester);
  await pumpSetupScene(
    tester,
    setupScenes.firstWhere((s) => s.name == name),
    boundary: GlobalKey(),
  );
}

Finder _drawing<T extends KitScene>() => find.byWidgetPredicate(
  (widget) => widget is KitIllustration && widget.scene is T,
);

Widget _card({
  String? error,
  bool starting = false,
  bool notAnswering = false,
  bool phone = true,
  bool reduce = false,
}) => MaterialApp(
  theme: AppTheme.dark(),
  builder: (context, child) => MediaQuery(
    data: MediaQuery.of(context).copyWith(disableAnimations: reduce),
    child: child!,
  ),
  home: Scaffold(
    body: SavedServerConnectionCard(
      profileName: 'Laptop',
      baseUrl: 'http://127.0.0.1:4096',
      error: error,
      attempts: 1,
      supportsTermux: true,
      onChangeServer: () {},
      onRetry: () {},
      onStartPhoneServer: phone ? () {} : null,
      startingPhoneServer: starting,
      notAnswering: notAnswering,
    ),
  ),
);

void main() {
  setUpAll(loadCaptureFonts);
  tearDown(() => KitMotion.loops = false);

  group('setup start', () {
    testWidgets('leads with the phone drawing at the top of the page', (
      tester,
    ) async {
      await _scene(tester, 'setup_start');
      final drawing = find.byKey(const ValueKey('phone-setup-hero-drawing'));
      expect(drawing, findsOneWidget);
      expect(_drawing<SetupPhoneScene>(), findsOneWidget);
      // No empty band above it: it starts right under the top bar (the
      // kit's KitTopBar since the phone pages became KitScreen pages,
      // 5088cc85).
      final appBar = tester.getRect(find.byType(KitTopBar));
      expect(tester.getRect(drawing).top - appBar.bottom, lessThan(24));
      // The title follows it, in the upper half of the screen.
      final title = tester.getRect(
        find.byKey(const ValueKey('phone-setup-start-headline')),
      );
      expect(title.top, greaterThan(tester.getRect(drawing).bottom));
      expect(title.top, lessThan(915 / 2));
    });

    testWidgets('a running setup draws its journey', (tester) async {
      await _scene(tester, 'setup_start_progress');
      expect(_drawing<SetupStepsScene>(), findsOneWidget);
    });
  });

  group('setup progress', () {
    testWidgets('the log opens in view, in the Details sheet', (tester) async {
      // Opening Details brings the log into view (design regressions ledger
      // row 16). Since the vision ring (2026-10-20) the steps and the log
      // are one tap down, in the Details sheet over the ring.
      await _scene(tester, 'setup_progress_log');
      final sheet = tester.getRect(
        find.byKey(const Key('phone-setup-progress-details-sheet')),
      );
      // The newest log line is on screen, not below the fold.
      final log = tester.getRect(find.byKey(const Key('setup-progress-log')));
      expect(log.bottom, lessThanOrEqualTo(915));
      expect(log.top, greaterThan(sheet.top));
    });

    testWidgets('each log line takes one line, in one box', (tester) async {
      // The log is the kit's KitLogPanel since 231e31ec (KitChecklist): it
      // is the one tinted box, and each line is one row of one height.
      await _scene(tester, 'setup_progress_log');
      final log = find.byKey(const Key('setup-progress-log'));
      expect(log, findsOneWidget);
      // One tinted box: the panel's own surface, which the style resolver
      // builds just under the panel's key. Nothing inside it draws another
      // (the live dot is a circle, not a box).
      bool tintedBox(Decoration? decoration) =>
          decoration is BoxDecoration &&
          decoration.color != null &&
          decoration.shape == BoxShape.rectangle;
      final boxes = find.descendant(
        of: log,
        matching: find.byWidgetPredicate(
          (w) =>
              (w is DecoratedBox && tintedBox(w.decoration)) ||
              (w is Container && tintedBox(w.decoration)),
        ),
      );
      expect(boxes, findsOneWidget);
      final lines = find.descendant(
        of: log,
        matching: find.byWidgetPredicate(
          (w) =>
              w.key is ValueKey<String> &&
              (w.key! as ValueKey<String>).value.startsWith('kit-log-line-'),
        ),
      );
      expect(lines, findsWidgets);
      final heights = {
        for (final element in lines.evaluate())
          tester.getSize(find.byWidget(element.widget)).height,
      };
      // Wrapped apt lines would give rows of two heights.
      expect(heights, hasLength(1));
    });

    testWidgets('Pause sits at the thumb, Details under the sentence', (
      tester,
    ) async {
      await _scene(tester, 'setup_progress_running');
      final pause = tester.getRect(
        find.byKey(const Key('setup-progress-cancel')),
      );
      final details = tester.getRect(
        find.byKey(const Key('setup-progress-details')),
      );
      final sentence = tester.getRect(
        find.byKey(const Key('setup-progress-title')),
      );
      expect(details.top, greaterThan(sentence.bottom));
      expect(pause.top, greaterThan(details.bottom));
      expect(pause.bottom, greaterThan(915 - 96));
    });
  });

  group('setup ready', () {
    testWidgets('one step: the celebration, one name, no sheet on top', (
      tester,
    ) async {
      await _scene(tester, 'setup_ready');
      expect(_drawing<SetupReadyScene>(), findsOneWidget);
      expect(find.byType(TextField), findsOneWidget);
      expect(find.byType(BottomSheet), findsNothing);
      expect(find.text('Create and open'), findsOneWidget);
      expect(find.text('Open a folder instead'), findsOneWidget);
      final appBar = tester.getRect(find.byType(KitTopBar));
      final drawing = find.byKey(const ValueKey('phone-setup-hero-drawing'));
      expect(tester.getRect(drawing).top - appBar.bottom, lessThan(24));
    });

    testWidgets('the celebration plays once and then rests', (tester) async {
      KitMotion.loops = true;
      await _scene(tester, 'setup_ready');
      await tester.pump(KitMotion.celebration);
      expect(tester.hasRunningAnimations, isFalse);
    });
  });

  group('connecting', () {
    testWidgets('starting the phone server draws the phone, breathing', (
      tester,
    ) async {
      KitMotion.loops = true;
      await tester.pumpWidget(_card(starting: true));
      await tester.pump(KitMotion.entrance);
      await tester.pump(const Duration(milliseconds: 16));
      expect(_drawing<SetupPhoneScene>(), findsOneWidget);
      final illustration = tester.widget<KitIllustration>(
        _drawing<SetupPhoneScene>(),
      );
      expect(
        (illustration.scene as SetupPhoneScene).mood,
        SetupPhoneMood.starting,
      );
      expect(illustration.ambient, isTrue);
      expect(tester.hasRunningAnimations, isTrue);
      await tester.pumpWidget(const SizedBox());
      await tester.pump(const Duration(milliseconds: 300));
    });

    testWidgets('not answering draws the plug short of the portal', (
      tester,
    ) async {
      await tester.pumpWidget(_card());
      expect(_drawing<KitPortalScene>(), findsOneWidget);
      // The connection's own 8 s wait ran out (the controller's clock).
      await tester.pumpWidget(_card(notAnswering: true));
      await tester.pump(KitMotion.entrance);
      expect(
        find.byKey(const ValueKey('saved-server-not-answering')),
        findsOne,
      );
      expect(_drawing<SetupUnpluggedScene>(), findsOneWidget);
    });

    testWidgets('a stopped phone server rests: drawn, and still', (
      tester,
    ) async {
      KitMotion.loops = true;
      await tester.pumpWidget(_card(error: 'Connection refused'));
      await tester.pump(KitMotion.entrance);
      await tester.pump(const Duration(milliseconds: 16));
      final stopped = _drawing<SetupPhoneScene>();
      expect(stopped, findsOneWidget);
      expect(
        (tester.widget<KitIllustration>(stopped).scene as SetupPhoneScene).mood,
        SetupPhoneMood.stopped,
      );
      expect(tester.hasRunningAnimations, isFalse);
    });

    testWidgets('another server that did not answer draws the plug, never '
        'the phone', (tester) async {
      // Since c274356e (coord-main, map page root-connecting) a remote
      // server that did not answer shows the unplugged drawing; the phone
      // drawing is only for this phone's own server.
      await tester.pumpWidget(_card(error: 'Connection refused', phone: false));
      await tester.pumpAndSettle();
      expect(_drawing<SetupUnpluggedScene>(), findsOneWidget);
      expect(_drawing<SetupPhoneScene>(), findsNothing);
      expect(find.byType(KitIllustration), findsOneWidget);
    });

    testWidgets('reduced motion shows the drawing finished and still', (
      tester,
    ) async {
      KitMotion.loops = true;
      // Stopped: no waiting bar, so anything moving would be the drawing.
      await tester.pumpWidget(_card(error: 'Connection refused', reduce: true));
      expect(_drawing<SetupPhoneScene>(), findsOneWidget);
      expect(tester.hasRunningAnimations, isFalse);
      // Without reduced motion the same drawing plays its entrance.
      await tester.pumpWidget(const SizedBox());
      await tester.pumpWidget(_card(error: 'Connection refused'));
      expect(tester.hasRunningAnimations, isTrue);
    });
  });
}
