// Golden renders of the New conversation screen, opened as the app opens it
// (a page that closes): four agents as cards (Claude Code chosen; Codex and
// Gemini offered as certified agents would be), three (the odd one spans the
// row), the same four with a message typed, this build's fresh phone
// (OpenCode chosen, Claude Code to set up), a single-agent server (no cards),
// large text on a small phone, and right to left. 412x915 (320x640 for large
// text), dark and light, real fonts.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/goldens/vision_start_golden_test.dart
// and look at every changed image before committing it.
import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/screens/chats/new_chat_screen.dart';

import 'kit/kit_gallery.dart' show loadKitGalleryFonts;
import '../support/agents_fakes.dart';
import '../support/chats_fakes.dart';
import '../../tool/capture/fixtures.dart' show captureTheme;

enum _Scene {
  several('vision_start_cards', size: Size(412, 915)),
  odd('vision_start_cards3', size: Size(412, 915), also: 1),
  typed('vision_start_typed', size: Size(412, 915), text: true),
  freshPhone('vision_start_fresh', size: Size(412, 915), fresh: true),
  single('vision_start_single', size: Size(412, 915), cards: false),
  largeText('vision_start_cards_text2', size: Size(320, 640), scale: 2),
  rtl('vision_start_cards_ar', size: Size(412, 915), locale: Locale('ar'));

  const _Scene(
    this.file, {
    required this.size,
    this.text = false,
    this.cards = true,
    this.fresh = false,
    this.also = 2,
    this.scale = 1,
    this.locale = const Locale('en'),
  });
  final String file;
  final Size size;
  final bool text;
  final bool cards;

  /// Claude Code not installed yet: OpenCode chosen, Claude Code to set up.
  final bool fresh;

  /// How many of Codex and Gemini are offered beside Claude Code.
  final int also;
  final double scale;
  final Locale locale;
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadKitGalleryFonts);

  for (final light in [false, true]) {
    final mode = light ? 'light' : 'dark';
    for (final scene in _Scene.values) {
      testWidgets('${scene.file} · $mode', (tester) async {
        final boundary = GlobalKey();
        debugDefaultTargetPlatformOverride = TargetPlatform.android;
        tester.view.physicalSize = scene.size;
        tester.view.devicePixelRatio = 1;
        addTearDown(tester.view.reset);
        final host =
            FakeChatsHost(
                FakeChatFeedSource(
                  projects: [project('neon-breaker', kind: 'node')],
                  lastUsed: '/root/projects/neon-breaker',
                ),
              )
              ..phoneAgents =
                  (FakePhoneAgentsSource(
                      available: scene.cards,
                      rows: [
                        agentRowFor(
                          'claude',
                          scene.fresh
                              ? FakeAgentStage.notInstalled
                              : FakeAgentStage.ready,
                        ),
                      ],
                      selected: scene.cards && !scene.fresh
                          ? 'claude'
                          : 'opencode',
                    )
                    ..alsoReady = scene.fresh
                        ? const []
                        : const [
                            ('codex', 'Codex'),
                            ('gemini', 'Gemini'),
                          ].take(scene.also).toList());
        if (!scene.cards) host.startAgentName = 'OpenCode';
        await tester.pumpWidget(
          RepaintBoundary(
            key: boundary,
            child: chatsApp(
              host,
              const SizedBox.expand(key: ValueKey('opener')),
              light: light,
              textScale: scene.scale,
              locale: scene.locale,
              // Arabic falls back to the Noto Sans Arabic the fonts load.
              theme: AppTheme.forLocale(
                captureTheme(light: light),
                scene.locale,
              ),
            ),
          ),
        );
        // Opened as the app opens it, so the page has its Close.
        unawaited(
          showNewChat(tester.element(find.byKey(const ValueKey('opener')))),
        );
        await tester.pumpAndSettle();
        if (scene.text) {
          await tester.enterText(
            find.byKey(const ValueKey('chats-new-field')),
            'A neon brick breaker for my phone, with levels',
          );
          await tester.pumpAndSettle();
        }
        expect(tester.takeException(), isNull);
        await expectLater(
          find.byKey(boundary),
          matchesGoldenFile('${scene.file}_$mode.png'),
        );
        await tester.pumpWidget(const SizedBox.shrink());
        debugDefaultTargetPlatformOverride = null;
      });
    }
  }
}
