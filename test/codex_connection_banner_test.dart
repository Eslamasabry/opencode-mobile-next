import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/widgets/app_connection_status.dart';
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
  return ConnectionController(store);
}

Widget _app(ConnectionController controller, {RouteFactory? onGenerateRoute}) {
  final navigator = GlobalKey<NavigatorState>();
  return MaterialApp(
    navigatorKey: navigator,
    theme: AppTheme.dark(),
    localizationsDelegates: AppLocalizations.localizationsDelegates,
    supportedLocales: AppLocalizations.supportedLocales,
    onGenerateRoute: onGenerateRoute,
    builder: (context, child) => ListenableBuilder(
      listenable: controller,
      builder: (context, _) => AppConditionsScope(
        conditions: [
          connectionKitStatus(
            context,
            controller,
            actionContext: () => navigator.currentState?.overlay?.context,
          ),
        ],
        child: child!,
      ),
    ),
    home: const KitScreen(body: SizedBox()),
  );
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('rejected Codex token offers the token editor action', (
    tester,
  ) async {
    final controller = await _controller(codex: true);
    try {
      controller.passwordRejected = true;
      Object? pushedArguments;
      await tester.pumpWidget(
        _app(
          controller,
          onGenerateRoute: (settings) {
            if (settings.name == '/servers') {
              pushedArguments = settings.arguments;
              return MaterialPageRoute<void>(
                builder: (_) => const KitScreen(body: Text('servers-route')),
              );
            }
            return null;
          },
        ),
      );
      expect(
        find.textContaining(
          'rejected the connection token. Update it to reconnect.',
        ),
        findsOneWidget,
      );
      expect(find.byKey(const ValueKey('banner-update-token')), findsOneWidget);
      expect(find.text('Update password'), findsNothing);
      await tester.tap(find.byKey(const ValueKey('banner-update-token')));
      await tester.pumpAndSettle();
      expect(find.text('servers-route'), findsOneWidget);
      expect(pushedArguments, 'edit-active');
    } finally {
      await tester.pumpWidget(const SizedBox());
      controller.dispose();
    }
  });

  // Owner, 2026-10-27: the connection line is about the link. A standing
  // promise about drafts ("Review draft stays here; nothing is sent
  // automatically") showed on every agent reconnect; the draft stays in its
  // composer, which says what happens to it when Send is pressed.
  testWidgets('a reconnect line is about the link, with no draft promise', (
    tester,
  ) async {
    final controller = await _controller(codex: true);
    try {
      controller
        ..status = StreamStatus.reconnecting
        ..notifyListeners();
      await tester.pumpWidget(_app(controller));
      expect(find.byKey(const ValueKey('connection-status-banner')), findsOne);
      expect(find.textContaining('Review draft stays here'), findsNothing);
      expect(
        find.textContaining('nothing is sent automatically'),
        findsNothing,
      );
      expect(find.textContaining('Reconnect to '), findsNothing);
    } finally {
      await tester.pumpWidget(const SizedBox());
      controller.dispose();
    }
  });
}
