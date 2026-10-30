// Gallery (gate G4) for KitFrost: the chat's frosted glass (owner,
// 2026-10-30; concepts-2026-10-30/glass, the balanced recipe). The composer
// pane and the header band over words and a dark code block, at the three
// strengths; and a whole chat page scrolled so the transcript runs under
// both, light and dark.
//
// Animations stay on in these shots: the system's "remove animations"
// turns the glass off (reduced motion keeps it solid).
//
// Regenerate deliberately:
//   flutter test --update-goldens test/goldens/kit/kit_frost_golden_test.dart
// and look at every changed image before committing it.
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';

import 'kit_gallery.dart';

/// What the glass lies over: words and a dark code block.
Widget _behind(KitTokens tokens) => Column(
  crossAxisAlignment: CrossAxisAlignment.stretch,
  children: [
    for (var i = 0; i < 4; i++)
      Padding(
        padding: EdgeInsets.symmetric(vertical: tokens.space2),
        child: KitText('The focus ring on the TV grid is cut off, line $i.'),
      ),
    const KitCodeBlock(
      text: '- padding: EdgeInsets.all(12),\n+ padding: EdgeInsets.all(15),',
    ),
    for (var i = 4; i < 8; i++)
      Padding(
        padding: EdgeInsets.symmetric(vertical: tokens.space2),
        child: KitText('The focus ring on the TV grid is cut off, line $i.'),
      ),
  ],
);

Widget _scene({
  KitFrostRole role = KitFrostRole.composer,
  KitFrostStrength strength = KitFrostStrength.balanced,
}) => Builder(
  builder: (context) {
    final tokens = KitTokens.of(context);
    final header = role == KitFrostRole.header;
    final glass = KitFrost(
      role: role,
      strength: strength,
      shown: true,
      tint: header ? tokens.roles.ground : tokens.roles.surface2,
      borderRadius: header
          ? BorderRadius.zero
          : BorderRadius.circular(tokens.composerRadius),
      child: Padding(
        padding: EdgeInsets.all(tokens.space4),
        child: KitText(
          header ? 'Fix the TV focus ring' : 'Steer while it works…',
          role: header ? KitTextRole.title : KitTextRole.body,
        ),
      ),
    );
    return Padding(
      padding: EdgeInsets.all(tokens.gutter),
      child: Stack(
        children: [
          // Scenery: what lies under the glass is not read, and G5 would
          // measure words half under a pane.
          ExcludeSemantics(child: _behind(tokens)),
          Positioned(top: 140, left: 0, right: 0, child: glass),
        ],
      ),
    );
  },
);

/// A chat page: bar, a transcript that opts in to running under it, and
/// the composer layer; scrolled to the middle so both are glass.
Widget _chat({required bool light}) => KitStatusScope(
  conditions: ValueNotifier(const []),
  child: MaterialApp(
    debugShowCheckedModeBanner: false,
    theme: light ? AppTheme.light() : AppTheme.dark(),
    localizationsDelegates: AppLocalizations.localizationsDelegates,
    supportedLocales: AppLocalizations.supportedLocales,
    home: KitScreen(
      topBar: const KitTopBar(
        title: 'Fix the TV focus ring',
        subtitle: 'IPTV_King · branch main',
      ),
      body: KitComposer.layer(
        body: Builder(
          builder: (context) {
            final tokens = KitTokens.of(context);
            return ListView.builder(
              key: const ValueKey('transcript'),
              reverse: true,
              padding: EdgeInsets.fromLTRB(
                tokens.gutter,
                tokens.space2 + KitFrostHeader.topClearanceOf(context),
                tokens.gutter,
                tokens.space2 + KitBottomInset.of(context).bottom,
              ),
              itemCount: 30,
              itemBuilder: (context, i) => Padding(
                padding: EdgeInsets.symmetric(vertical: tokens.space2),
                child: i.isEven
                    ? KitText(
                        'Step $i: the ring is clipped by the grid padding; '
                        'I moved the outline inside the tile.',
                      )
                    : KitCodeBlock(
                        text:
                            '\$ flutter test test/tv_grid_test.dart\n'
                            '00:04 +12: All tests passed!',
                      ),
              ),
            );
          },
        ),
        composer: Builder(
          builder: (context) => KitGlass(
            borderRadius: BorderRadius.circular(
              KitTokens.of(context).composerRadius,
            ),
            child: const SizedBox(
              height: 56,
              child: Align(
                alignment: AlignmentDirectional.centerStart,
                child: Padding(
                  padding: EdgeInsetsDirectional.only(start: 16),
                  child: KitText('Steer while it works…'),
                ),
              ),
            ),
          ),
        ),
      ),
    ),
  ),
);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadKitGalleryFonts);
  setUp(GlassChoiceStore.debugReset);

  for (final light in [false, true]) {
    final mode = light ? 'light' : 'dark';
    for (final size in kitGallerySizes) {
      final at = kitGallerySize(size);
      testWidgets('kit_frost default · $at · $mode', (tester) async {
        await kitGalleryPart(
          tester,
          name: kitGalleryName('kit_frost_default', size, light: light),
          size: size,
          light: light,
          removeAnimations: false,
          child: _scene(),
        );
      });
    }
    for (final size in kitGalleryScaledSizes) {
      final at = kitGallerySize(size);
      testWidgets('kit_frost default · 2.0 text · $at · $mode', (tester) async {
        await kitGalleryPart(
          tester,
          name: kitGalleryName(
            'kit_frost_default',
            size,
            light: light,
            text2: true,
          ),
          size: size,
          light: light,
          textScale: 2,
          removeAnimations: false,
          child: _scene(),
        );
      });
    }
    const phone = Size(412, 915);
    for (final strength in [KitFrostStrength.weak, KitFrostStrength.strong]) {
      testWidgets('kit_frost ${strength.name} · $mode', (tester) async {
        await kitGalleryPart(
          tester,
          name: kitGalleryName(
            'kit_frost_${strength.name}',
            phone,
            light: light,
          ),
          size: phone,
          light: light,
          removeAnimations: false,
          child: _scene(strength: strength),
        );
      });
    }
    testWidgets('kit_frost header · $mode', (tester) async {
      await kitGalleryPart(
        tester,
        name: kitGalleryName('kit_frost_header', phone, light: light),
        size: phone,
        light: light,
        removeAnimations: false,
        child: _scene(role: KitFrostRole.header),
      );
    });
    testWidgets('kit_frost chat, scrolled under both · $mode', (tester) async {
      tester.view.physicalSize = phone * 3;
      tester.view.devicePixelRatio = 3;
      addTearDown(tester.view.reset);
      debugDefaultTargetPlatformOverride = TargetPlatform.android;
      try {
        await tester.pumpWidget(_chat(light: light));
        await tester.pumpAndSettle();
        await tester.drag(
          find.byKey(const ValueKey('transcript')),
          const Offset(0, 500),
        );
        await tester.pumpAndSettle();
        await expectLater(
          find.byType(MaterialApp),
          matchesGoldenFile('kit_frost_chat_$mode.png'),
        );
      } finally {
        debugDefaultTargetPlatformOverride = null;
      }
    });
  }
}
