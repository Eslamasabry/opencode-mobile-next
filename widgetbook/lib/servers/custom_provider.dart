// The "Add provider" page (CustomProviderScreen): declaring an endpoint the
// server's own catalog does not list, on the server running inside the app.
// Nothing here writes a file or restarts a process.
import 'package:flutter/widgets.dart';
import 'package:opencode_mobile/ui/screens/custom_provider_screen.dart';
import 'package:widgetbook/widgetbook.dart';

import '../catalog.dart';
import '../fakes/fake_app.dart';

/// The phone engine (its config is the app's to write) or a saved server on
/// someone else's machine (it is not, so the page explains instead of a form).
Widget _scene({required bool onThisPhone}) => FakeAppScene<FakeApp>(
  platform: onThisPhone ? FakePlatform.android : FakePlatform.none,
  create: () => FakeApp.create(
    profiles: [
      onThisPhone
          ? const FakeProfile(
              'phone',
              'This phone',
              url: 'http://127.0.0.1:4097',
            )
          : const FakeProfile('work', 'Workstation'),
    ],
    connected: true,
  ),
  builder: (context, app) => CustomProviderScreen(controller: app.connection),
);

CatalogComponent customProviderScreen() => catalogComponent(
  area: 'Servers',
  screen: 'CustomProviderScreen',
  name: 'Add provider',
  useCases: [
    WidgetbookUseCase(
      name: 'Empty form, on this phone',
      builder: (_) => _scene(onThisPhone: true),
    ),
    WidgetbookUseCase(
      name: 'Not available on a server elsewhere',
      builder: (_) => _scene(onThisPhone: false),
    ),
  ],
);
