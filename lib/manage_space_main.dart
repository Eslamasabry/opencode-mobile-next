// The manage-space entrypoint (ManageSpaceActivity → manageSpaceMain in
// main.dart): its own small app, not the whole one. It reads no profiles'
// secrets, connects to nothing and starts nothing; it only measures the
// app's files and offers export, cache clearing and delete. It reads the
// saved appearance and theme (plain preferences) so it looks like the app.
import 'dart:async';

import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'builtin/project_export.dart';
import 'builtin/project_export_controller.dart';
import 'l10n/app_localizations.dart';
import 'state/app_locale.dart' show resolveAppLocales;
import 'state/profiles.dart' show AppAppearance, ProfileStore, ThemePackId;
import 'ui/app_theme.dart';
import 'ui/screens/manage_space_screen.dart';
import 'ui/theme_packs.dart' show effectiveThemePack;

void runManageSpaceApp() {
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
  runApp(
    ManageSpaceApp(
      controller: ProjectExportController(
        platform: MethodChannelProjectExport(),
      ),
      appearance: appearance,
      themePack: themePack,
    ),
  );
}

class ManageSpaceApp extends StatelessWidget {
  const ManageSpaceApp({
    super.key,
    required this.controller,
    this.onClose,
    this.appearance = AppAppearance.dark,
    this.themePack = ThemePackId.opencode,
  });

  final ProjectExportController controller;
  final VoidCallback? onClose;

  /// Settings › Appearance, as the app saved it (dark when never chosen,
  /// like the app), so the page follows the app and the device setting.
  final AppAppearance appearance;
  final ThemePackId themePack;

  @override
  Widget build(BuildContext context) {
    final pack = effectiveThemePack(themePack);
    return MaterialApp(
      title: 'OpenCode Mobile',
      debugShowCheckedModeBanner: false,
      themeMode: switch (appearance) {
        AppAppearance.system => ThemeMode.system,
        AppAppearance.light => ThemeMode.light,
        AppAppearance.dark => ThemeMode.dark,
      },
      theme: AppTheme.light(pack),
      darkTheme: AppTheme.dark(pack),
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      localeListResolutionCallback: resolveAppLocales,
      home: ManageSpaceScreen(controller: controller, onClose: onClose),
    );
  }
}
