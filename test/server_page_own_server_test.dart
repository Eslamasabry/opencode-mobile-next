// The app's own server on its page (emulator check of 3800, 2026-10-27): it
// is named "On this phone", so its actions read "Remove On this phone", and
// it showed "Server updates · Upgrade from the machine running the server"
// although this phone is that machine and the server updates with the app.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/settings_screen.dart';

import '../tool/capture/fixtures.dart';
import 'coverage/servers_support.dart';
import 'support/fake_server_gateway.dart';

Future<ServersConnection> _open(
  WidgetTester tester,
  ServerProfile profile, {
  StreamStatus status = StreamStatus.connected,
}) async {
  final (store, base) = await serversState(profiles: [profile]);
  base.dispose();
  final controller = ServersConnection(store)
    ..api = FakeServerGateway(baseUrl: profile.baseUrl)
    ..status = status;
  addTearDown(controller.dispose);
  await tester.pumpWidget(
    captureApp(
      boundaryKey: GlobalKey(),
      controller: controller,
      store: store,
      home: ServerPageScreen(
        controller: controller,
        profileId: controller.profile!.id,
      ),
    ),
  );
  await frames(tester, 10);
  return controller;
}

final _phone = ServerProfile(
  id: 'phone',
  name: 'This phone',
  baseUrl: BuiltinLinux.serverUrl,
  username: BuiltinLinux.serverUsername,
  password: 'secret',
);

Finder _updateRows() => find.byWidgetPredicate(
  (widget) =>
      widget.key == const ValueKey('gated-remote-upgrade') ||
      widget.key == const Key('server-updates-tile'),
  skipOffstage: false,
);

void main() {
  setUp(() {
    debugPlatformCapabilities = const PlatformCapabilities.android();
    addTearDown(() => debugPlatformCapabilities = null);
  });

  testWidgets('the phone\'s own server names itself in its actions and has '
      'no update row', (tester) async {
    await _open(tester, _phone);

    expect(
      find.text("Remove this phone's server", skipOffstage: false),
      findsOneWidget,
    );
    expect(
      find.text("Reconnect to this phone's server", skipOffstage: false),
      findsOneWidget,
    );
    expect(find.textContaining('Remove On', skipOffstage: false), findsNothing);
    expect(_updateRows(), findsNothing);
  });

  testWidgets('another server keeps its name and its update row', (
    tester,
  ) async {
    await _open(
      tester,
      ServerProfile(
        id: 'srv',
        name: 'Studio',
        baseUrl: 'https://studio.example.net:4096',
      ),
    );

    expect(find.text('Remove Studio', skipOffstage: false), findsOneWidget);
    expect(_updateRows(), findsOneWidget);
  });

  // 3870 sweep: the Connect row still read "Connect to On this phone".
  testWidgets('the phone\'s own server offers to connect by its action name', (
    tester,
  ) async {
    final controller = await _open(
      tester,
      _phone,
      status: StreamStatus.disconnected,
    );

    expect(
      find.text("Connect to this phone's server", skipOffstage: false),
      findsOneWidget,
    );
    expect(
      find.textContaining('to On this phone', skipOffstage: false),
      findsNothing,
    );
    // A disconnected controller keeps a retry timer: end it here.
    await tester.pumpWidget(const SizedBox.shrink());
    controller.dispose();
    await tester.pump(const Duration(minutes: 1));
  });
}
