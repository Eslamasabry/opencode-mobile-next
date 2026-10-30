// KitAskField (the preview's one glowing "Ask for a change…" field): its
// behaviour, and the still-motion registration G8 requires.
import 'kit_motion_still.dart';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/kit/kit_surface_look.dart';

Widget _host(Widget child) => MaterialApp(
  theme: AppTheme.dark(),
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  home: Scaffold(body: Center(child: child)),
);

KitAskField _field({
  TextEditingController? controller,
  VoidCallback? onSend,
  VoidCallback? onVoice,
  KitAskFieldPhase phase = KitAskFieldPhase.ready,
  String? note,
}) => KitAskField(
  fieldKey: const ValueKey('field'),
  sendKey: const ValueKey('send'),
  voiceKey: const ValueKey('voice'),
  controller: controller ?? TextEditingController(),
  focusNode: FocusNode(),
  hint: 'Ask for a change…',
  sendLabel: 'Send to the conversation',
  voiceLabel: 'Speak your change',
  listeningHint: 'Listening…',
  stopListeningLabel: 'Done speaking',
  onSend: onSend ?? () {},
  onVoice: onVoice,
  phase: phase,
  note: note,
);

void main() {
  kitMotionStillTests(
    'KitAskField',
    builds: {'default': () => _field()},
    changes: {
      'a note arrives': KitMotionChange(
        build: () => _field(),
        act: (tester, stage) =>
            stage.rebuild(_field(note: 'Sent to the conversation')),
        shows: 'Sent to the conversation',
      ),
    },
  );

  testWidgets('the mic while empty, Send once there are words', (tester) async {
    var sent = 0;
    var spoke = 0;
    final controller = TextEditingController();
    await tester.pumpWidget(
      _host(
        _field(
          controller: controller,
          onSend: () => sent++,
          onVoice: () => spoke++,
        ),
      ),
    );
    expect(find.text('Ask for a change…'), findsOneWidget);
    expect(find.byTooltip('Speak your change'), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('voice')));
    expect(spoke, 1);
    await tester.enterText(find.byKey(const ValueKey('field')), 'Faster');
    await tester.pumpAndSettle();
    expect(find.byTooltip('Send to the conversation'), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('send')));
    expect(sent, 1);
  });

  testWidgets('without voice, an empty field cannot send', (tester) async {
    var sent = 0;
    await tester.pumpWidget(_host(_field(onSend: () => sent++)));
    await tester.tap(find.byKey(const ValueKey('send')));
    expect(sent, 0);
  });

  testWidgets('listening says so, and the button finishes it', (tester) async {
    var spoke = 0;
    await tester.pumpWidget(
      _host(_field(onVoice: () => spoke++, phase: KitAskFieldPhase.listening)),
    );
    expect(find.text('Listening…'), findsOneWidget);
    await tester.tap(find.byTooltip('Done speaking'));
    expect(spoke, 1);
  });

  testWidgets('working holds still: no send', (tester) async {
    var sent = 0;
    await tester.pumpWidget(
      _host(
        _field(
          controller: TextEditingController(text: 'Faster'),
          onSend: () => sent++,
          phase: KitAskFieldPhase.working,
        ),
      ),
    );
    await tester.tap(find.byKey(const ValueKey('send')), warnIfMissed: false);
    expect(sent, 0);
  });

  testWidgets('a style that asks for a field halo keeps it plain at rest', (
    tester,
  ) async {
    double restAlpha(KitSurfaceStyle style) {
      final halo = tester
          .widgetList<DecoratedBox>(
            find.ancestor(
              of: find.byKey(const ValueKey('field')),
              matching: find.byType(DecoratedBox),
            ),
          )
          .map((box) => box.decoration)
          .whereType<KitSurfaceDecoration>()
          .firstWhere(
            (face) =>
                face.fill == Colors.transparent && face.shadows.isNotEmpty,
          );
      return halo.shadows.first.color.a;
    }

    await tester.pumpWidget(
      _host(
        KitEffectsScope(
          effects: const KitEffects(surfaceStyle: KitSurfaceStyle.soft),
          child: _field(),
        ),
      ),
    );
    // Soft asks for the field halo (KitStyleTokens.fieldHalo): it reads
    // plainly around the well even unfocused.
    expect(restAlpha(KitSurfaceStyle.soft), greaterThanOrEqualTo(.45));
  });
}
