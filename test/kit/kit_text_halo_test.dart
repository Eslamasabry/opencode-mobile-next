// KitTextHalo: on a bright live look, text2 and text3 keep a halo of the
// ground around their letters; nothing else changes. Text on a solid fill,
// primary text, and pages without a bright look keep none.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';

const _ground = Color(0xFF0B0C0E);

Widget _app(Widget child) => MaterialApp(
  theme: AppTheme.dark(),
  home: KitEffectsScope(
    // A look is on: the live background is off by default.
    effects: const KitEffects(liveBackground: true),
    child: Material(child: child),
  ),
);

List<Shadow>? _shadowsOf(WidgetTester tester, String words) => tester
    .widget<RichText>(
      find.descendant(of: find.text(words), matching: find.byType(RichText)),
    )
    .text
    .style
    ?.shadows;

void main() {
  testWidgets('quiet text on the ground keeps a halo of the ground', (
    tester,
  ) async {
    await tester.pumpWidget(
      _app(
        const KitTextHalo(
          ground: _ground,
          child: Column(
            children: [
              KitText('quiet', tone: KitTextTone.tertiary),
              KitText('secondary', tone: KitTextTone.secondary),
              KitText('primary'),
              KitText('on the accent', tone: KitTextTone.onAccent),
            ],
          ),
        ),
      ),
    );
    expect(_shadowsOf(tester, 'quiet'), KitTextHalo.shadowsFor(_ground));
    expect(_shadowsOf(tester, 'secondary'), KitTextHalo.shadowsFor(_ground));
    expect(_shadowsOf(tester, 'primary'), isNull);
    expect(_shadowsOf(tester, 'on the accent'), isNull);
  });

  testWidgets('text on a solid fill or under a recolouring mask keeps none', (
    tester,
  ) async {
    await tester.pumpWidget(
      _app(
        KitTextHalo(
          ground: _ground,
          child: Column(
            children: [
              const ColoredBox(
                color: Color(0xFF16181B),
                child: KitText('on a card', tone: KitTextTone.tertiary),
              ),
              ShaderMask(
                shaderCallback: (r) => const LinearGradient(
                  colors: [Colors.white, Colors.green],
                ).createShader(r),
                blendMode: BlendMode.srcIn,
                child: const KitText('swept', tone: KitTextTone.tertiary),
              ),
              ShaderMask(
                shaderCallback: (r) => const LinearGradient(
                  colors: [Colors.white, Colors.transparent],
                ).createShader(r),
                blendMode: BlendMode.dstIn,
                child: const KitText('faded', tone: KitTextTone.tertiary),
              ),
            ],
          ),
        ),
      ),
    );
    expect(_shadowsOf(tester, 'on a card'), isNull);
    expect(_shadowsOf(tester, 'swept'), isNull);
    expect(_shadowsOf(tester, 'faded'), KitTextHalo.shadowsFor(_ground));
  });

  testWidgets('no ground, no halo', (tester) async {
    await tester.pumpWidget(
      _app(
        const KitTextHalo(
          ground: null,
          child: KitText('quiet', tone: KitTextTone.tertiary),
        ),
      ),
    );
    expect(_shadowsOf(tester, 'quiet'), isNull);
  });

  testWidgets('the live background gives one only while it paints a bright '
      'look', (tester) async {
    // Under flutter test the look is not painted, so pages keep none.
    await tester.pumpWidget(
      _app(
        const KitLiveBackground(
          child: KitText('quiet', tone: KitTextTone.tertiary),
        ),
      ),
    );
    expect(_shadowsOf(tester, 'quiet'), isNull);
    KitLiveBackground.paintWithoutLoops = true;
    addTearDown(() => KitLiveBackground.paintWithoutLoops = false);
    await tester.pumpWidget(
      _app(
        KitLiveBackground(
          style: kitLiveStyleById('aurora'),
          child: const KitText('quiet', tone: KitTextTone.tertiary),
        ),
      ),
    );
    await tester.runAsync(() => Future<void>.delayed(Duration.zero));
    await tester.pump();
    expect(_shadowsOf(tester, 'quiet'), isNotNull);
  });
}
