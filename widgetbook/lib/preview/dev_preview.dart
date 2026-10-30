import 'package:opencode_mobile/domain/dev_preview.dart';
import 'package:opencode_mobile/ui/screens/dev_preview_screen.dart';
import 'package:widgetbook/widgetbook.dart';

import '../catalog.dart';
import '../fakes/fake_app.dart';

// The web catalog has no platform web view, so the page area shows the
// screen's own "Preview isn't available here" state; the bar and the
// "Ask for a change…" field are the real ones.

DevPreviewTarget _target(String server) => DevPreviewTarget.resolve(
  const DevPreviewLink(port: 8080),
  serverBaseUrl: server,
);

DevPreviewConversation _conversation() => DevPreviewConversation(
  delivery: () => DevPreviewDelivery.now,
  send: (_) async {},
);

CatalogComponent devPreview() => catalogComponent(
  area: 'Preview',
  screen: 'DevPreviewScreen',
  name: 'In-app preview',
  useCases: [
    WidgetbookUseCase(
      name: 'From a conversation',
      builder: (_) => FakeAppScene<FakeApp>(
        create: () => FakeApp.create(profiles: const []),
        builder: (_, app) => DevPreviewScreen(
          target: _target('http://127.0.0.1:4096'),
          supported: false,
          voiceSupported: true,
          conversation: _conversation(),
        ),
      ),
    ),
    WidgetbookUseCase(
      name: 'Opened from Development services',
      builder: (_) => FakeAppScene<FakeApp>(
        create: () => FakeApp.create(profiles: const []),
        builder: (_, app) => DevPreviewScreen(
          target: _target('http://100.64.1.2:4096'),
          supported: false,
        ),
      ),
    ),
  ],
);
