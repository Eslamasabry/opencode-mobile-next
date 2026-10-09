// Golden renders of the suggested-connector card (FC8): suggested, connecting
// and connected, dark, light and Arabic, at 412x915.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/goldens/connector_card_golden_test.dart
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/genui/gen_ui.dart';
import 'package:opencode_mobile/domain/mcp_chat.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connector_card_host.dart';
import 'package:opencode_mobile/ui/widgets/agent_card_view.dart';

import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart' show KitTokens;

import '../../tool/capture/fixtures.dart' show captureTheme;
import '../support/agent_card_fakes.dart';
import '../support/connector_card_fakes.dart';
import 'kit/kit_gallery.dart' show loadKitGalleryFonts;

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

const _phases = {
  'suggested': McpChatPhase.suggested,
  'connecting': McpChatPhase.connecting,
  'connected': McpChatPhase.toolsReady,
};

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadKitGalleryFonts);

  for (final entry in _phases.entries) {
    for (final variant in ['dark', 'light', 'ar']) {
      testWidgets('connector_card_${entry.key} · $variant', (tester) async {
        tester.view.physicalSize = const Size(412, 915);
        tester.view.devicePixelRatio = 1;
        addTearDown(tester.view.reset);
        debugDefaultTargetPlatformOverride = TargetPlatform.android;
        final boundary = GlobalKey();
        final chat = FakeConnectorChat(McpChatSnapshot(entry.value));
        await tester.pumpWidget(
          RepaintBoundary(
            key: boundary,
            child: MaterialApp(
              debugShowCheckedModeBanner: false,
              theme: _theme(
                light: variant == 'light',
                locale: Locale(variant == 'ar' ? 'ar' : 'en'),
              ),
              locale: Locale(variant == 'ar' ? 'ar' : 'en'),
              localizationsDelegates: AppLocalizations.localizationsDelegates,
              supportedLocales: AppLocalizations.supportedLocales,
              home: Builder(
                builder: (context) => Material(
                  color: Theme.of(context).scaffoldBackgroundColor,
                  child: ListView(
                    padding: const EdgeInsets.only(top: 24),
                    children: [
                      AgentCardView(
                        controller: FakeGenUi(),
                        parse: GenUiParsed(connectorCard()),
                        agentLabel: 'OpenCode',
                        connectors: FakeConnectorHost(
                          ConnectorReady(
                            item: connectorItem(),
                            chat: chat,
                            canSignIn: true,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
        );
        await tester.pump();
        await tester.pump(const Duration(milliseconds: 700));
        expect(tester.takeException(), isNull);
        await expectLater(
          find.byKey(boundary),
          matchesGoldenFile('connector_card_${entry.key}_$variant.png'),
        );
        await tester.pumpWidget(const SizedBox.shrink());
        debugDefaultTargetPlatformOverride = null;
      });
    }
  }
}
