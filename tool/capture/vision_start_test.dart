// Captures of the New conversation screen (vision slice, 2026-10-20): the
// real NewChatScreen opened the way the app opens it (a full-screen page with
// a close), the live aurora painted (still), real fonts, no server. Phone
// 412x915 dp at 2x pixels, one full screen per image.
//
//   flutter test --concurrency=1 tool/capture/vision_start_test.dart
//   flutter test --concurrency=1 --dart-define=VISION_OUT=<dir> \
//     tool/capture/vision_start_test.dart
//
// Output: /home/eslam/Storage2/launch-footage/vision-start/opus/<scene>.png
import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/phone_agents.dart' show AgentRow;
import 'package:opencode_mobile/domain/phone_agents_source.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/chats/new_chat_screen.dart';

import '../../test/goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;
import '../../test/support/agents_fakes.dart';
import '../../test/support/chats_fakes.dart';
import 'fixtures.dart';

const _out = String.fromEnvironment(
  'VISION_OUT',
  defaultValue: '/home/eslam/Storage2/launch-footage/vision-start/opus',
);

enum _Scene {
  fourDark('four-agents-dark'),
  fourLight('four-agents-light', light: true),
  three('three-agents-dark', agents: _Agents.three),
  typed('typed-dark', text: true),
  today('today-claude-ready-dark', agents: _Agents.today),
  fresh('today-fresh-phone-dark', agents: _Agents.fresh),
  single('single-agent-server-dark', agents: _Agents.none),
  small('small-320-text2-dark', size: Size(320, 693), scale: 2),
  arabic('arabic-rtl-dark', locale: Locale('ar'));

  const _Scene(
    this.file, {
    this.light = false,
    this.agents = _Agents.four,
    this.text = false,
    this.size = const Size(412, 915),
    this.scale = 1,
    this.locale = const Locale('en'),
  });
  final String file;
  final bool light;
  final _Agents agents;
  final bool text;
  final Size size;
  final double scale;
  final Locale locale;
}

/// Which agents this phone has. OpenCode (the server's own) is always one.
/// Today only Claude Code is certified for chat on the phone; [four] and
/// [three] show the layout as it will be once Codex (and Gemini) pass the
/// certification matrix, by offering them as choices with no row.
/// [today] and [fresh] are this build's real cases.
enum _Agents { four, three, today, fresh, none }

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadKitGalleryFonts);
  setUp(() {
    KitLiveBackground.paintWithoutLoops = true;
    KitMotion.loops = false;
  });
  tearDown(() {
    KitLiveBackground.paintWithoutLoops = false;
    KitLiveBackground.frozenSeconds = null;
  });

  for (final scene in _Scene.values) {
    testWidgets(scene.file, (tester) async {
      debugDefaultTargetPlatformOverride = TargetPlatform.android;
      // Blurred shadows as a phone draws them (the test binding flattens
      // them); put back before the test ends.
      debugDisableShadows = false;
      // A phone's density, so hairlines are as thin as on the device.
      tester.view.physicalSize = scene.size * 2;
      tester.view.devicePixelRatio = 2;
      addTearDown(tester.view.reset);
      KitLiveBackground.frozenSeconds = 21;
      final rows = switch (scene.agents) {
        _Agents.fresh => [agentRowFor('claude', FakeAgentStage.notInstalled)],
        _Agents.none => <AgentRow>[],
        _ => [agentRowFor('claude', FakeAgentStage.ready)],
      };
      final alsoReady = switch (scene.agents) {
        _Agents.four => const [('codex', 'Codex'), ('gemini', 'Gemini')],
        _Agents.three => const [('codex', 'Codex')],
        _ => const <(String, String)>[],
      };
      final host =
          FakeChatsHost(
              FakeChatFeedSource(
                projects: [project('neon-breaker', kind: 'node')],
                lastUsed: '/root/projects/neon-breaker',
              ),
            )
            ..phoneAgents =
                (FakePhoneAgentsSource(
                    // A remote server with only its own agent: no phone agents.
                    available: scene.agents != _Agents.none,
                    rows: rows,
                    selected: switch (scene.agents) {
                      _Agents.none || _Agents.fresh => 'opencode',
                      _ => 'claude',
                    },
                  )
                  ..alsoReady = alsoReady
                  // Claude Code's helper names its default model.
                  ..models['claude'] = const [
                    AgentModelChoice(
                      id: 'claude-opus-5-5',
                      name: 'Opus 5.5',
                      isDefault: true,
                    ),
                    AgentModelChoice(
                      id: 'claude-sonnet-5-5',
                      name: 'Sonnet 5.5',
                    ),
                  ]);
      if (scene.agents == _Agents.none) host.startAgentName = 'OpenCode';
      final boundary = GlobalKey();
      await tester.pumpWidget(
        RepaintBoundary(
          key: boundary,
          child: chatsApp(
            host,
            const SizedBox.expand(key: ValueKey('opener')),
            light: scene.light,
            textScale: scene.scale,
            locale: scene.locale,
          ),
        ),
      );
      // Opened as the app opens it, so the page has its close.
      unawaited(
        showNewChat(tester.element(find.byKey(const ValueKey('opener')))),
      );
      await tester.pump();
      await tester.runAsync(
        () => Future<void>.delayed(const Duration(milliseconds: 200)),
      );
      await tester.pumpAndSettle();
      if (scene.text) {
        await tester.enterText(
          find.byKey(const ValueKey('chats-new-field')),
          'A neon brick breaker for my phone, with levels and a high score',
        );
        await tester.pumpAndSettle();
      }
      expect(tester.takeException(), isNull);
      await writePng(
        '$_out/${scene.file}.png',
        await capturePng(tester, boundary, pixelRatio: 2),
      );
      await tester.pumpWidget(const SizedBox.shrink());
      debugDisableShadows = true;
      debugDefaultTargetPlatformOverride = null;
    });
  }
}
