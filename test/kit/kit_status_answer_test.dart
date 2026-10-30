// KitStatusAnswer (a status page's one big centred answer): its words, the
// live countdown, the quiet action, and the still-motion registration G8
// requires of every kit part.
import 'kit_motion_still.dart';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';

Widget _host(Widget child) => MaterialApp(
  theme: AppTheme.dark(),
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  home: Scaffold(body: Center(child: child)),
);

void main() {
  kitMotionStillTests(
    'KitStatusAnswer',
    builds: {
      'ok': () => const KitStatusAnswer(
        tone: KitAnswerTone.ok,
        answer: 'Everything is running',
        line: 'Uses about 410 MB of memory',
      ),
      'paused': () => const KitStatusAnswer(
        tone: KitAnswerTone.paused,
        answer: 'Paused, your phone is warm',
      ),
      'working': () => const KitStatusAnswer(
        tone: KitAnswerTone.working,
        answer: 'Starting the server',
      ),
      'needsYou': () => const KitStatusAnswer(
        tone: KitAnswerTone.needsYou,
        answer: "The server didn't start",
      ),
      'off': () =>
          const KitStatusAnswer(tone: KitAnswerTone.off, answer: 'Off'),
    },
    changes: {
      'answer changes': KitMotionChange(
        build: () => const KitStatusAnswer(
          tone: KitAnswerTone.ok,
          answer: 'Everything is running',
        ),
        act: (tester, stage) => stage.rebuild(
          const KitStatusAnswer(
            tone: KitAnswerTone.off,
            answer: 'The server is off',
          ),
        ),
        shows: 'The server is off',
      ),
    },
  );

  testWidgets('says the answer and its line, one header, and the action '
      'runs', (tester) async {
    final handle = tester.ensureSemantics();
    var taps = 0;
    await tester.pumpWidget(
      _host(
        KitStatusAnswer(
          tone: KitAnswerTone.ok,
          answer: 'Everything is running',
          line: 'Uses about 410 MB of memory',
          action: KitAction(
            label: "Use this phone's server",
            onPressed: () => taps++,
          ),
        ),
      ),
    );
    expect(find.text('Everything is running'), findsOneWidget);
    expect(find.text('Uses about 410 MB of memory'), findsOneWidget);
    expect(
      tester.getSemantics(find.text('Everything is running')),
      matchesSemantics(label: 'Everything is running', isHeader: true),
    );
    await tester.tap(find.text("Use this phone's server"));
    expect(taps, 1);
    handle.dispose();
  });

  testWidgets('a countdown line follows the time left and stops ticking', (
    tester,
  ) async {
    final until = DateTime.now().add(const Duration(seconds: 3));
    await tester.pumpWidget(
      _host(
        KitStatusAnswer(
          tone: KitAnswerTone.paused,
          answer: 'The server stopped by itself',
          countdownTo: until,
          countdownLine: (left) =>
              left == Duration.zero ? 'In a moment' : 'In ${left.inSeconds} s',
        ),
      ),
    );
    expect(find.textContaining('In '), findsOneWidget);
    await tester.runAsync(
      () => Future<void>.delayed(const Duration(seconds: 4)),
    );
    await tester.pump(const Duration(seconds: 1));
    expect(find.text('In a moment'), findsOneWidget);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('every tone draws without overflow at 2.0 text in 320 dp', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(320, 640);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    for (final tone in KitAnswerTone.values) {
      await tester.pumpWidget(
        MediaQuery(
          data: const MediaQueryData(
            size: Size(320, 640),
            textScaler: TextScaler.linear(2),
          ),
          child: _host(
            KitStatusAnswer(
              tone: tone,
              answer: 'Android closed the server',
              line: 'It restarts by itself in about 40 s',
            ),
          ),
        ),
      );
      expect(tester.takeException(), isNull, reason: tone.name);
    }
  });

  testWidgets('no tone draws no mark; the calm scale is smaller', (
    tester,
  ) async {
    await tester.pumpWidget(
      _host(
        const KitStatusAnswer(
          answer: 'Nothing running',
          scale: KitAnswerScale.calm,
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.byType(KitStatusMark), findsNothing);
    final style = tester.widget<Text>(find.text('Nothing running')).style!;
    expect(style.fontSize, 28);
  });
}
