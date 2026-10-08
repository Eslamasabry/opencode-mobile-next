// The two screens FG5 checks in the five new languages (docs/l10n/
// fg5-key-set.md): the first-run welcome and a new chat's composer. Shared by
// the widget test (test/l10n_fg5_screens_test.dart) and the goldens
// (test/goldens/languages_golden_test.dart).
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/server_probe.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/servers_screen.dart';

import '../../tool/capture/fixtures.dart';
import 'setup_capture_preferences.dart';

/// The five languages as a device reports them. Brazilian Portuguese is
/// `pt_BR`; the app matches it to its one `pt` catalogue.
const fg5Locales = <Locale>[
  Locale('ja'),
  Locale.fromSubtags(languageCode: 'zh', countryCode: 'CN'),
  Locale('es'),
  Locale('pt', 'BR'),
  Locale('ru'),
];

/// File-name token of a locale (TEST-20: lower-case letters and digits).
String fg5Token(Locale locale) =>
    '${locale.languageCode}${locale.countryCode == 'BR' ? 'br' : ''}';

/// Registers the Japanese and Simplified Chinese fallback faces the goldens
/// need: the engine under test has no system CJK font. Subsets of Noto Sans
/// CJK (SIL OFL) built by tool/l10n/subset_cjk_fixtures.py.
Future<void> loadFg5CjkFonts() async {
  Future<void> load(String family, String path) async {
    final loader = FontLoader(family)
      ..addFont(File(path).readAsBytes().then(ByteData.sublistView));
    await loader.load();
  }

  await load(
    'Fg5NotoSansCjkJp',
    'test/fixtures/fonts/NotoSansCJKjp-Regular-subset.otf',
  );
  await load(
    'Fg5NotoSansCjkSc',
    'test/fixtures/fonts/NotoSansCJKsc-Regular-subset.otf',
  );
}

/// The capture theme as main.dart adapts it for [locale], with the CJK
/// languages falling back to the fixture faces, as Android falls back to
/// its own Noto Sans CJK.
ThemeData fg5Theme({required bool light, required Locale locale}) {
  final theme = AppTheme.forLocale(captureTheme(light: light), locale);
  final fallback = switch (locale.languageCode) {
    'ja' => const ['Fg5NotoSansCjkJp'],
    'zh' => const ['Fg5NotoSansCjkSc'],
    _ => const <String>[],
  };
  if (fallback.isEmpty) return theme;
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

void _mockPlatform(WidgetTester tester) {
  const secure = MethodChannel('plugins.it_nomads.com/flutter_secure_storage');
  const termux = MethodChannel('oc/termux');
  final messenger = tester.binding.defaultBinaryMessenger;
  messenger.setMockMethodCallHandler(
    secure,
    (call) async => call.method == 'readAll' ? <String, String>{} : null,
  );
  // No Termux: nothing is found running on the phone, so the welcome shows
  // only its own three choices.
  messenger.setMockMethodCallHandler(termux, (call) async {
    if (call.method == 'getCapabilities') return {'installed': false};
    return null;
  });
  addTearDown(() {
    messenger.setMockMethodCallHandler(secure, null);
    messenger.setMockMethodCallHandler(termux, null);
  });
}

MaterialApp _materialApp({
  required Locale locale,
  required bool light,
  required Widget home,
  double textScale = 1,
}) => MaterialApp(
  debugShowCheckedModeBanner: false,
  theme: fg5Theme(light: light, locale: locale),
  locale: locale,
  supportedLocales: AppLocalizations.supportedLocales,
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  builder: (context, child) => MediaQuery(
    data: MediaQuery.of(context).copyWith(
      textScaler: TextScaler.linear(textScale),
      disableAnimations: true,
    ),
    child: child!,
  ),
  home: home,
);

/// Mounts the first-run welcome (Servers with nothing saved) in [locale] at
/// [size]. Returns what to call once the test is done with it.
Future<Future<void> Function()> mountFg5Welcome(
  WidgetTester tester, {
  required Locale locale,
  bool light = true,
  Size size = const Size(412, 915),
  double devicePixelRatio = 1,
  double textScale = 1,
  GlobalKey? boundary,
}) async {
  _mockPlatform(tester);
  debugPlatformCapabilities = const PlatformCapabilities.android();
  tester.view.physicalSize = size * devicePixelRatio;
  tester.view.devicePixelRatio = devicePixelRatio;
  addTearDown(tester.view.reset);
  final previousProbe = serverProbe;
  final prefs = await setupCapturePreferences();
  final store = SeededProfileStore(prefs: prefs, seeded: const []);
  final controller = CaptureController(store);
  Widget app = ProviderScope(
    overrides: [
      bootstrapProvider.overrideWithValue(AppBootstrap(store)),
      connProvider.overrideWithValue(controller),
    ],
    child: _materialApp(
      locale: locale,
      light: light,
      textScale: textScale,
      home: const ServersScreen(),
    ),
  );
  if (boundary != null) app = RepaintBoundary(key: boundary, child: app);
  await tester.pumpWidget(app);
  for (var i = 0; i < 20; i++) {
    await tester.pump(const Duration(milliseconds: 100));
  }
  return () async {
    serverProbe = previousProbe;
    debugPlatformCapabilities = null;
    await tester.pumpWidget(const SizedBox.shrink());
    controller.dispose();
  };
}

void _noop() {}

/// A new chat's composer in [locale]: the question above, the starter chips,
/// and the composer with a draft, the model chip and the offline note.
Future<Future<void> Function()> mountFg5Composer(
  WidgetTester tester, {
  required Locale locale,
  bool light = true,
  Size size = const Size(412, 915),
  double devicePixelRatio = 1,
  double textScale = 1,
  GlobalKey? boundary,
}) async {
  tester.view.physicalSize = size * devicePixelRatio;
  tester.view.devicePixelRatio = devicePixelRatio;
  addTearDown(tester.view.reset);
  final controller = TextEditingController();
  final focus = FocusNode();
  Widget app = _materialApp(
    locale: locale,
    light: light,
    textScale: textScale,
    home: Builder(
      builder: (context) {
        final l10n = lookupAppLocalizations(Localizations.localeOf(context));
        if (controller.text != l10n.chatStartFindBug) {
          controller.text = l10n.chatStartFindBug;
        }
        final tokens = KitTokens.of(context);
        return Scaffold(
          body: SafeArea(
            child: Column(
              children: [
                Expanded(
                  child: Center(
                    child: SingleChildScrollView(
                      padding: EdgeInsets.all(tokens.gutter),
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          KitText(
                            l10n.chatsNewPrompt,
                            role: KitTextRole.title,
                            textAlign: TextAlign.center,
                          ),
                          SizedBox(height: tokens.space3),
                          KitChipWrap(
                            children: [
                              for (final label in [
                                l10n.chatStartExplainProject,
                                l10n.chatStartFindBug,
                                l10n.chatStartAddTests,
                                l10n.chatStartWhatChanged,
                              ])
                                KitChip.action(label: label, onPressed: _noop),
                            ],
                          ),
                        ],
                      ),
                    ),
                  ),
                ),
                Padding(
                  padding: EdgeInsets.fromLTRB(
                    tokens.gutter,
                    0,
                    tokens.gutter,
                    tokens.space2,
                  ),
                  child: KitComposer(
                    controller: controller,
                    focusNode: focus,
                    hint: l10n.chatUiAskOpenCode,
                    onSend: _noop,
                    offline: true,
                    onTools: _noop,
                    onVoice: _noop,
                    onOpenEditor: _noop,
                    model: const KitComposerChips.model(
                      label: '',
                      onPressed: _noop,
                      state: KitModelChipState.chooseNeeded,
                    ),
                  ),
                ),
              ],
            ),
          ),
        );
      },
    ),
  );
  if (boundary != null) app = RepaintBoundary(key: boundary, child: app);
  await tester.pumpWidget(app);
  for (var i = 0; i < 10; i++) {
    await tester.pump(const Duration(milliseconds: 100));
  }
  return () async {
    await tester.pumpWidget(const SizedBox.shrink());
    controller.dispose();
    focus.dispose();
  };
}
