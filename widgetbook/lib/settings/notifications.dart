// The Notifications page (NotificationsSettingsScreen, Notifications part):
// what notifies, quiet hours and saved servers, on a phone. Android draws the
// notifications themselves; their copy is in
// docs/qa/vision-notifications-2026-10-20/README.md.
import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/ui/screens/settings_screen.dart';
import 'package:widgetbook/widgetbook.dart';

import '../catalog.dart';
import '../fakes/fake_app.dart';

Widget _scene({required bool granted}) => FakeAppScene<FakeApp>(
  platform: FakePlatform.android,
  create: () => FakeApp.create(
    profiles: const [
      FakeProfile('mac', 'MacBook'),
      FakeProfile('home', 'Home PC'),
    ],
    connected: true,
  ),
  builder: (context, app) {
    app.connection.backgroundLive.notificationGranted = granted;
    return ProviderScope(
      overrides: [
        bootstrapProvider.overrideWithValue(AppBootstrap(app.store)),
        connProvider.overrideWithValue(app.connection),
      ],
      child: NotificationsSettingsScreen(
        controller: app.connection,
        page: NotificationsPage.notifications,
      ),
    );
  },
);

CatalogComponent notificationsSettingsScreen() => catalogComponent(
  area: 'Settings',
  screen: 'NotificationsSettingsScreen',
  name: 'Notifications',
  useCases: [
    WidgetbookUseCase(
      name: 'Notifications allowed',
      builder: (_) => _scene(granted: true),
    ),
    WidgetbookUseCase(
      name: 'Android blocks notifications',
      builder: (_) => _scene(granted: false),
    ),
  ],
);
