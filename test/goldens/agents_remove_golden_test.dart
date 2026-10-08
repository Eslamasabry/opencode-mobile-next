// Golden renders of "Remove agent" (FA6, docs/design/BA10-contract.md): the
// agent's sheet with Remove as its quiet destructive action, the question
// before it, the progress in place, and what was freed. 412x915, light and
// dark, plus Arabic (right to left) for each. The source is a fake that plays
// the controller's part, so these are not device proof.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/goldens/agents_remove_golden_test.dart
// and look at every changed image before committing it.
import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/agent_auth_probe.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/agents/agents_section.dart';
import 'package:opencode_mobile/ui/screens/chats/chats_host.dart';

import '../../tool/capture/fixtures.dart' show captureTheme;
import '../support/agents_fakes.dart';
import '../support/chats_fakes.dart';
import 'kit/kit_gallery.dart' show loadKitGalleryFonts;

enum _Scene {
  sheet('agents_remove_sheet'),
  confirm('agents_remove_confirm'),
  pending('agents_remove_pending'),
  done('agents_remove_done');

  const _Scene(this.name);
  final String name;
}

/// The capture theme as main.dart adapts it for [locale], with Arabic
/// falling back to Noto Sans Arabic as on a device (kit gallery, TEST-8).
ThemeData _theme({required bool light, required Locale locale}) {
  final theme = AppTheme.forLocale(captureTheme(light: light), locale);
  if (locale.languageCode != 'ar') return theme;
  const fallback = ['Noto Sans Arabic'];
  // Buttons carry their own text styles in the app theme.
  ButtonStyle? withFallback(ButtonStyle? style) {
    final text = style?.textStyle;
    if (style == null || text == null) return style;
    return style.copyWith(
      textStyle: WidgetStateProperty.resolveWith(
        (states) =>
            text.resolve(states)?.copyWith(fontFamilyFallback: fallback),
      ),
    );
  }

  final text = theme.textTheme.apply(fontFamilyFallback: fallback);
  return theme.copyWith(
    extensions: [
      ...theme.extensions.values,
      KitTokens.fromRoles(ThemeRoles.resolve(theme), text),
    ],
    textTheme: text,
    primaryTextTheme: theme.primaryTextTheme.apply(
      fontFamilyFallback: fallback,
    ),
    filledButtonTheme: FilledButtonThemeData(
      style: withFallback(theme.filledButtonTheme.style),
    ),
    textButtonTheme: TextButtonThemeData(
      style: withFallback(theme.textButtonTheme.style),
    ),
    outlinedButtonTheme: OutlinedButtonThemeData(
      style: withFallback(theme.outlinedButtonTheme.style),
    ),
    elevatedButtonTheme: ElevatedButtonThemeData(
      style: withFallback(theme.elevatedButtonTheme.style),
    ),
  );
}

Widget _app(
  FakeChatsHost host,
  Widget home, {
  required bool light,
  required Locale locale,
}) => ProviderScope(
  overrides: [chatsHostProvider.overrideWithValue(host)],
  child: MaterialApp(
    debugShowCheckedModeBanner: false,
    theme: _theme(light: light, locale: locale),
    locale: locale,
    supportedLocales: AppLocalizations.supportedLocales,
    localizationsDelegates: AppLocalizations.localizationsDelegates,
    builder: (context, child) => MediaQuery(
      data: MediaQuery.of(context).copyWith(disableAnimations: true),
      child: child!,
    ),
    home: Builder(
      builder: (context) => Material(
        color: Theme.of(context).scaffoldBackgroundColor,
        child: home,
      ),
    ),
  ),
);

FakeRemovableAgentsSource _agents(_Scene scene) {
  final agents = FakeRemovableAgentsSource(
    rows: [
      agentRowFor('claude', FakeAgentStage.ready),
      agentRowFor('fx', FakeAgentStage.ready),
      agentRowFor('codex', FakeAgentStage.ready),
      agentRowFor('gemini', FakeAgentStage.notInstalled),
    ],
  );
  agents.signOutCapable.addAll(['claude', 'fx']);
  agents.removable.addAll(['fx', 'codex']);
  agents.check(
    'claude',
    const AgentAuthProbeResult(
      state: AgentAuthProbeState.signedIn,
      accountDisplayName: 'sam@example.com',
    ),
  );
  agents.check(
    'fx',
    const AgentAuthProbeResult(state: AgentAuthProbeState.signedIn),
  );
  agents.check(
    'codex',
    const AgentAuthProbeResult.failed(AgentAuthProbeError.probeUnsupported),
  );
  // Codex has no qualified check; give it a ready row all the same.
  agents.rows = [
    agentRowFor('claude', FakeAgentStage.ready),
    agentRowFor('fx', FakeAgentStage.ready),
    agentRowFor('codex', FakeAgentStage.ready),
    agentRowFor('gemini', FakeAgentStage.notInstalled),
  ];
  if (scene == _Scene.pending) agents.removeGate = Completer<void>();
  return agents;
}

Future<void> _mount(
  WidgetTester tester,
  _Scene scene, {
  required bool light,
  required Locale locale,
  required GlobalKey boundary,
}) async {
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  final agents = _agents(scene);
  final host = FakeChatsHost(
    FakeChatFeedSource(
      items: const [],
      projects: [project('alpha')],
      lastUsed: '/root/projects/alpha',
    ),
  )..phoneAgents = agents;
  await tester.pumpWidget(
    RepaintBoundary(
      key: boundary,
      child: _app(
        host,
        ListView(
          children: [
            Padding(
              padding: const EdgeInsets.only(top: 16),
              child: const AgentsSection(),
            ),
          ],
        ),
        light: light,
        locale: locale,
      ),
    ),
  );
  await tester.pumpAndSettle();
  await tester.tap(find.byKey(const ValueKey('agents-row-fx')));
  await tester.pumpAndSettle();
  if (scene == _Scene.sheet) return;
  await tester.tap(find.byKey(const ValueKey('agents-remove')));
  await tester.pumpAndSettle();
  if (scene == _Scene.confirm) return;
  await tester.tap(find.byKey(const ValueKey('agents-remove-confirm')));
  if (scene == _Scene.pending) {
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 600));
    return;
  }
  await tester.pumpAndSettle();
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadKitGalleryFonts);

  for (final locale in const [Locale('en'), Locale('ar')]) {
    final suffix = locale.languageCode == 'ar' ? '_ar' : '';
    for (final light in [false, true]) {
      final mode = light ? 'light' : 'dark';
      for (final scene in _Scene.values) {
        testWidgets('${scene.name}$suffix · $mode', (tester) async {
          final boundary = GlobalKey();
          debugDefaultTargetPlatformOverride =
              TargetPlatform.android; // ARCH-11
          await _mount(
            tester,
            scene,
            light: light,
            locale: locale,
            boundary: boundary,
          );
          expect(tester.takeException(), isNull);
          if (locale.languageCode == 'ar') {
            expect(
              Directionality.of(tester.element(find.byType(AgentsSection))),
              TextDirection.rtl,
            );
          }
          await expectLater(
            find.byKey(boundary),
            matchesGoldenFile('${scene.name}${suffix}_$mode.png'),
          );
          await tester.pumpWidget(const SizedBox.shrink());
          debugDefaultTargetPlatformOverride = null;
        });
      }
    }
  }
}
