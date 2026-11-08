// The terminal area's empty and unavailable states, drawn for the contact
// sheet (the look gate): no terminals yet and a server without terminals.
// Checks the words a person needs are
// on each screen and writes build/coverage/termscene_*.png.
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import '../support/fake_server_gateway.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/ui/screens/terminal_screen.dart';

import '../../tool/capture/fixtures.dart';
import '../goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;
import 'paseo_coverage_support.dart';
import 'servers_support.dart';
import 'tools_support.dart';

class _NoTerminalApi extends FakeServerGateway {
  _NoTerminalApi() : super(baseUrl: 'ws://build.example.net:6767');

  @override
  ServerCapabilities get capabilities =>
      const ServerCapabilities(terminal: false);
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(() async {
    await loadKitGalleryFonts();
    await loadCaptureFonts();
  });
  setUp(() {
    const secure = MethodChannel(
      'plugins.it_nomads.com/flutter_secure_storage',
    );
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          secure,
          (call) async => call.method == 'readAll' ? <String, String>{} : null,
        );
    mockNoTermux();
  });

  Future<String> scene(
    WidgetTester tester,
    String name, {
    FakeServerGateway? api,
    Widget Function(dynamic controller)? home,
  }) async {
    tester.view.physicalSize = const Size(412, 915);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final (store, controller) = await toolsConnection(ToolsRepo());
    if (api != null) controller.api = api;
    addTearDown(controller.dispose);
    final boundary = GlobalKey();
    await tester.pumpWidget(
      serversApp(
        boundary,
        store,
        controller,
        home:
            home?.call(controller) ??
            TerminalScreen(controller: controller, page: true),
      ),
    );
    await frames(tester, 14);
    await writeCasePng(tester, boundary, 'termscene_$name');
    return flat(screenText(tester).join('\n'));
  }

  testWidgets('no terminals yet: the page starts one', (tester) async {
    final text = await scene(tester, 'none');
    expect(text, contains('Terminal'));
    expect(text, isNot(contains('No terminals yet')));
  });

  testWidgets('a server without terminals explains', (tester) async {
    final text = await scene(tester, 'unavailable', api: _NoTerminalApi());
    expect(text.length, greaterThan(40));
  });
}
