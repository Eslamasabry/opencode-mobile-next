import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/widgets/connection_status_banner.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _BannerStore extends ProfileStore {
  _BannerStore({required super.prefs, required this.server});

  final ServerProfile server;

  @override
  List<ServerProfile> get profiles => [server];

  @override
  String? get activeId => server.id;
}

Future<ConnectionController> _controller({required bool codex}) async {
  SharedPreferences.setMockInitialValues({});
  final server = ServerProfile(
    id: 'codex-profile',
    name: codex ? 'Codex' : 'OpenCode',
    baseUrl: 'http://localhost',
    backend: codex ? ServerBackend.codex : ServerBackend.openCode,
  );
  final store = _BannerStore(
    prefs: await SharedPreferences.getInstance(),
    server: server,
  );
  return ConnectionController(store, isIsolated: true);
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('rejected Codex token offers the token editor action', (
    tester,
  ) async {
    final controller = await _controller(codex: true);
    addTearDown(controller.dispose);
    controller.passwordRejected = true;

    Object? pushedArguments;
    await tester.pumpWidget(
      MaterialApp(
        onGenerateRoute: (settings) {
          if (settings.name == '/servers') {
            pushedArguments = settings.arguments;
            return MaterialPageRoute<void>(
              builder: (_) => const Scaffold(body: Text('servers-route')),
            );
          }
          return null;
        },
        home: Scaffold(body: ConnectionStatusBanner(controller: controller)),
      ),
    );

    expect(
      find.text('The connection token was rejected. Update it to reconnect.'),
      findsOneWidget,
    );
    expect(find.byKey(const ValueKey('banner-update-token')), findsOneWidget);
    expect(find.text('Update password'), findsNothing);

    await tester.tap(find.byKey(const ValueKey('banner-update-token')));
    await tester.pumpAndSettle();
    expect(find.text('servers-route'), findsOneWidget);
    expect(pushedArguments, 'edit-active');
  });

  testWidgets('Codex reconnect keeps the review draft without auto-resend', (
    tester,
  ) async {
    final controller = await _controller(codex: true);
    addTearDown(controller.dispose);
    controller.status = StreamStatus.reconnecting;

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(body: ConnectionStatusBanner(controller: controller)),
      ),
    );

    expect(find.textContaining('Review draft stays here'), findsOneWidget);
    expect(
      find.textContaining('nothing is sent automatically'),
      findsOneWidget,
    );
    expect(find.text('Try again'), findsOneWidget);
  });
}
