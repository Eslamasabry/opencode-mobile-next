// Golden renders of the agent account surfaces (FA2/FA3): the Settings ›
// Agents rows after the sign-in status check (Signed in as an account,
// Signed in, Sign in needed, not installed), the agent's sheet with Sign out
// of the agent, the question asked before it, and the plain notice when the
// agent could not confirm it signed out. 412x915, light and dark, plus
// Arabic (right to left) for each. The account is a fixture.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/goldens/agents_account_golden_test.dart
// and look at every changed image before committing it.
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
  row('agents_account_row'),
  sheet('agents_account_sheet'),
  confirm('agents_account_confirm'),
  failed('agents_account_failed');

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
    home: Builder(
      builder: (context) => Material(
        color: Theme.of(context).scaffoldBackgroundColor,
        child: home,
      ),
    ),
  ),
);

FakeAccountAgentsSource _agents(_Scene scene) {
  final agents = FakeAccountAgentsSource(
    rows: [
      agentRowFor('claude', FakeAgentStage.ready),
      agentRowFor('fx', FakeAgentStage.ready),
      agentRowFor('codex', FakeAgentStage.ready),
      agentRowFor('gemini', FakeAgentStage.notInstalled),
    ],
  );
  agents.signOutCapable.addAll(['claude', 'fx']);
  agents.check(
    'claude',
    const AgentAuthProbeResult(
      state: AgentAuthProbeState.signedIn,
      accountDisplayName: 'sam@example.com',
    ),
  );
  // fx answers signed in without naming the account.
  agents.check(
    'fx',
    const AgentAuthProbeResult(state: AgentAuthProbeState.signedIn),
  );
  // Codex has no qualified status check yet: today's wording.
  agents.check(
    'codex',
    const AgentAuthProbeResult.failed(AgentAuthProbeError.probeUnsupported),
  );
  agents.logoutUnconfirmed = scene == _Scene.failed;
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
  final host = FakeChatsHost(
    FakeChatFeedSource(
      items: const [],
      projects: [project('alpha')],
      lastUsed: '/root/projects/alpha',
    ),
  )..phoneAgents = _agents(scene);
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
  if (scene == _Scene.row) return;
  await tester.tap(find.byKey(const ValueKey('agents-row-claude')));
  await tester.pumpAndSettle();
  if (scene == _Scene.sheet) return;
  await tester.tap(find.byKey(const ValueKey('agents-sign-out')));
  await tester.pumpAndSettle();
  if (scene == _Scene.confirm) return;
  await tester.tap(find.byKey(const ValueKey('agents-sign-out-confirm')));
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
