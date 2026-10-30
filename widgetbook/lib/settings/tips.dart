// Help › Tips (TipsScreen, the teaching system): every registered tip and
// mini tutorial with "Show me", and "Show tips again". One state replays a
// tip in place, as "Show me" does.
import 'package:flutter/widgets.dart';
import 'package:opencode_mobile/ui/screens/settings/tips_screen.dart';
import 'package:widgetbook/widgetbook.dart';

import '../catalog.dart';
import '../fakes/fake_app.dart';

Widget _scene({String? autoTap}) => FakeAppScene<FakeApp>(
  platform: FakePlatform.android,
  create: () => FakeApp.create(profiles: const []),
  autoTap: autoTap,
  builder: (context, app) => TipsScreen(controller: app.connection),
);

CatalogComponent tipsScreen() => catalogComponent(
  area: 'Settings',
  screen: 'TipsScreen',
  name: 'Help, Tips',
  useCases: [
    WidgetbookUseCase(name: 'Every tip and tutorial', builder: (_) => _scene()),
    WidgetbookUseCase(
      name: 'A tip replayed',
      builder: (_) => _scene(autoTap: 'tips-show-me-send-later'),
    ),
  ],
);
