// "Update agents on <computer>" on the Paseo server's own page: it asks
// first, names the computer, shows each step, and says plainly how it ended.
import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/settings_screen.dart';

import '../tool/capture/fixtures.dart';
import 'coverage/servers_support.dart';
import 'goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;

class _PaseoApi extends OpenCodeApi implements HostDaemonUpdateGateway {
  _PaseoApi() : super(baseUrl: 'ws://build.example.net:6767');
  int updates = 0;
  Completer<HostUpdateResult> answer = Completer();
  void Function(HostUpdatePhase)? progress;
  String version = '0.9.1';

  @override
  Future<Health> health() async => Health(healthy: true, version: version);

  @override
  bool get hostUpdateSupported => true;

  @override
  String? get hostVersion => version;

  @override
  Future<HostUpdateResult> updateHostDaemon({
    void Function(HostUpdatePhase phase)? onProgress,
  }) {
    updates++;
    progress = onProgress;
    return answer.future;
  }
}

final _boundary = GlobalKey();

Future<(_PaseoApi, ConnectionController)> _open(WidgetTester tester) async {
  final (store, base) = await serversState(
    profiles: [
      ServerProfile(
        id: 'srv',
        name: 'Build box',
        baseUrl: 'ws://build.example.net:6767',
        backend: ServerBackend.paseo,
        codexDirectory: '/work/shop',
      ),
    ],
  );
  base.dispose();
  final api = _PaseoApi();
  final controller = ServersConnection(store)
    ..api = api
    ..status = StreamStatus.connected;
  addTearDown(controller.dispose);
  await tester.pumpWidget(
    captureApp(
      boundaryKey: _boundary,
      controller: controller,
      store: store,
      home: ServerSettingsScreen(controller: controller),
    ),
  );
  await frames(tester, 10);
  return (api, controller);
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(() async {
    await loadKitGalleryFonts();
    await loadCaptureFonts();
  });
  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (call) async => call.method == 'readAll' ? <String, String>{} : null,
        );
    mockNoTermux();
  });

  final row = find.byKey(const Key('server-host-update'));

  Future<void> confirm(WidgetTester tester) async {
    await tester.ensureVisible(row);
    await tester.tap(row);
    await frames(tester, 10);
    await tester.tap(find.byKey(const Key('confirm-host-update')));
    await frames(tester, 10);
  }

  testWidgets('update asks first, names the computer, then installs', (
    tester,
  ) async {
    final (api, _) = await _open(tester);
    expect(find.text('Update agents on Build box'), findsOneWidget);
    await tester.ensureVisible(row);
    await tester.tap(row);
    await frames(tester, 10);
    expect(find.text('Update agents?'), findsOneWidget);
    expect(
      find.text('Conversations on Build box pause while it restarts.'),
      findsOneWidget,
    );
    expect(api.updates, 0, reason: 'asking is not updating');
    await tester.tap(find.byKey(const Key('confirm-host-update')));
    await frames(tester, 10);
    expect(api.updates, 1);

    // Each step the computer announces is on the page.
    expect(find.text('Starting the update…'), findsWidgets);
    api.progress!(HostUpdatePhase.downloading);
    await frames(tester, 4);
    expect(find.text('Downloading the newest version…'), findsWidgets);
    api.progress!(HostUpdatePhase.installing);
    await frames(tester, 4);
    expect(find.text('Installing…'), findsWidgets);

    api.version = '0.9.2';
    api.answer.complete(
      const HostUpdateResult(
        success: true,
        previousVersion: '0.9.1',
        newVersion: '0.9.2',
      ),
    );
    await frames(tester, 10);
    expect(
      find.text('Updated to 0.9.2. The app reconnects by itself.'),
      findsOneWidget,
    );
    // The page looks again once the helper is back.
    await tester.pump(const Duration(seconds: 4));
    await frames(tester, 6);
  });

  testWidgets('already up to date says so', (tester) async {
    final (api, _) = await _open(tester);
    await confirm(tester);
    api.answer.complete(
      const HostUpdateResult(
        success: true,
        previousVersion: '0.9.1',
        newVersion: '0.9.1',
      ),
    );
    await frames(tester, 10);
    expect(find.text('Build box is already up to date.'), findsOneWidget);
  });

  testWidgets('a failure is in plain words; the computer\'s text is Details', (
    tester,
  ) async {
    final (api, _) = await _open(tester);
    await confirm(tester);
    api.answer.complete(
      const HostUpdateResult(
        success: false,
        previousVersion: '0.9.1',
        reason: 'npm ERR! EACCES: permission denied',
      ),
    );
    await frames(tester, 10);
    expect(
      find.text(
        'The update did not finish. Try again, or update it on that computer.',
      ),
      findsOneWidget,
    );
    expect(find.textContaining('EACCES'), findsNothing);
    final details = find.byKey(const Key('server-host-update-details'));
    await tester.ensureVisible(details);
    await tester.tap(details);
    await frames(tester, 10);
    expect(
      find.byKey(const ValueKey('host-update-details-sheet')),
      findsOneWidget,
    );
    expect(find.textContaining('EACCES'), findsOneWidget);
  });

  testWidgets('a server that cannot update its helper has no such row', (
    tester,
  ) async {
    final (store, base) = await serversState(
      profiles: [
        ServerProfile(
          id: 'srv',
          name: 'Studio',
          baseUrl: 'https://studio.example.net:4096',
        ),
      ],
    );
    base.dispose();
    final controller = ServersConnection(store)
      ..api = OpenCodeApi(baseUrl: 'https://studio.example.net:4096')
      ..status = StreamStatus.connected;
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      captureApp(
        boundaryKey: GlobalKey(),
        controller: controller,
        store: store,
        home: ServerSettingsScreen(controller: controller),
      ),
    );
    await frames(tester, 10);
    expect(row, findsNothing);
  });

  // The look gate: build/coverage/od-agent-update-*.png, for the coordinator.
  testWidgets('look: the page, the question, the steps, a failure', (
    tester,
  ) async {
    Future<void> shot(String name) async => writePng(
      'build/coverage/od-agent-update-$name.png',
      await capturePng(tester, _boundary, pixelRatio: 1),
    );
    final (api, _) = await _open(tester);
    await shot('1-page');
    await tester.ensureVisible(row);
    await tester.tap(row);
    await frames(tester, 10);
    await shot('2-question');
    await tester.tap(find.byKey(const Key('confirm-host-update')));
    await frames(tester, 10);
    api.progress!(HostUpdatePhase.downloading);
    await frames(tester, 4);
    await shot('3-downloading');
    api.answer.complete(
      const HostUpdateResult(
        success: false,
        previousVersion: '0.9.1',
        reason: 'npm ERR! EACCES: permission denied',
      ),
    );
    await frames(tester, 10);
    await shot('4-failed');
  });
}
