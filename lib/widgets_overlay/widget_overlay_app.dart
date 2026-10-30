// The home-screen widgets' floating host (WidgetOverlayActivity →
// widgetOverlayMain in main.dart): a transparent window with its own engine
// that shows one small kit panel (style, steer or voice) over the launcher.
// It builds no connection controller, reads no profile secrets and never
// reconstructs a server client: everything it does goes through the
// `oc/widgets` channel, which native binds to the widget that was tapped.
import 'dart:async';

import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../l10n/app_localizations.dart';
import '../state/app_locale.dart' show resolveAppLocales;
import '../state/profiles.dart' show AppAppearance, ProfileStore, ThemePackId;
import '../ui/app_theme.dart';
import '../ui/screens/widgets/widget_configure_screen.dart';
import '../ui/screens/widgets/widget_project_screen.dart';
import '../ui/screens/widgets/widget_steer_screen.dart';
import '../ui/theme_packs.dart' show effectiveThemePack;
import 'widget_overlay_api.dart';
import 'widget_overlay_launch.dart';

void runWidgetOverlayApp() {
  WidgetsFlutterBinding.ensureInitialized();
  unawaited(_start());
}

Future<void> _start() async {
  var appearance = AppAppearance.dark;
  var themePack = ThemePackId.opencode;
  try {
    // Only the plain display preferences; no secure storage is touched.
    final store = ProfileStore(prefs: await SharedPreferences.getInstance());
    appearance = store.appearance;
    themePack = store.themePack;
  } on Object {
    // Unreadable preferences: the app's defaults (dark, OpenCode).
  }
  const api = PortWidgetOverlayApi();
  WidgetOverlayLaunch? launch;
  try {
    launch = WidgetOverlayLaunch.fromPlatform(await api.getLaunchArguments());
  } on Object {
    launch = null;
  }
  if (launch == null) {
    // Nothing to show without a bound widget: leave, never fall back to the app.
    unawaited(api.close());
    return;
  }
  runApp(
    WidgetOverlayApp(
      api: api,
      launch: launch,
      appearance: appearance,
      themePack: themePack,
    ),
  );
}

class WidgetOverlayApp extends StatelessWidget {
  const WidgetOverlayApp({
    super.key,
    required this.api,
    required this.launch,
    this.appearance = AppAppearance.dark,
    this.themePack = ThemePackId.opencode,
  });

  final WidgetOverlayApi api;
  final WidgetOverlayLaunch launch;
  final AppAppearance appearance;
  final ThemePackId themePack;

  @override
  Widget build(BuildContext context) {
    final pack = effectiveThemePack(themePack);
    return MaterialApp(
      title: 'OpenCode Mobile',
      debugShowCheckedModeBanner: false,
      color: Colors.transparent,
      themeMode: switch (appearance) {
        AppAppearance.system => ThemeMode.system,
        AppAppearance.light => ThemeMode.light,
        AppAppearance.dark || AppAppearance.saver => ThemeMode.dark,
      },
      theme: AppTheme.light(pack),
      darkTheme: appearance == AppAppearance.saver
          ? AppTheme.saver(pack)
          : AppTheme.dark(pack),
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      localeListResolutionCallback: resolveAppLocales,
      home: switch (launch.route) {
        '/widgets/configure' => WidgetConfigureScreen(api: api, launch: launch),
        '/widgets/project' => WidgetProjectScreen(api: api, launch: launch),
        '/widgets/steer' => WidgetSteerScreen(api: api, launch: launch),
        '/widgets/voice' => WidgetSteerScreen(
          api: api,
          launch: launch,
          voice: true,
        ),
        _ => const SizedBox.shrink(),
      },
    );
  }
}
