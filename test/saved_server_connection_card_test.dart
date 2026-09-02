import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/widgets/saved_server_connection_card.dart';

Widget _card({
  String? error,
  int attempts = 1,
  bool termux = true,
  VoidCallback? onTermux,
  VoidCallback? onPassword,
  String url = 'http://127.0.0.1:4096',
}) => MaterialApp(
  theme: AppTheme.light(),
  home: Scaffold(
    body: SavedServerConnectionCard(
      profileName: 'Laptop',
      baseUrl: url,
      error: error,
      attempts: attempts,
      supportsTermux: termux,
      onChangeServer: () {},
      onRetry: () {},
      onOpenTermuxSetup: onTermux,
      onUpdatePassword: onPassword,
    ),
  ),
);

void main() {
  testWidgets('connecting state shows progress and no actions', (tester) async {
    tester.view.physicalSize = const Size(900, 1800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(_card());
    expect(find.text('Connecting to Laptop'), findsOneWidget);
    expect(
      find.byKey(const ValueKey('saved-server-connect-progress')),
      findsOneWidget,
    );
    expect(find.text('Try again'), findsNothing);
  });

  testWidgets('a loopback failure explains Termux and offers to check it', (
    tester,
  ) async {
    var opened = false;
    await tester.pumpWidget(
      _card(
        error: 'Health check failed: connection refused',
        onTermux: () => opened = true,
      ),
    );
    expect(find.text('Nothing is listening on this device'), findsOneWidget);
    expect(find.byKey(const ValueKey('saved-server-checks')), findsOneWidget);
    expect(find.textContaining('Termux'), findsWidgets);
    await tester.ensureVisible(
      find.byKey(const ValueKey('saved-server-open-termux')),
    );
    await tester.tap(find.byKey(const ValueKey('saved-server-open-termux')));
    expect(opened, isTrue);
    // Try again is still there, but demoted.
    expect(find.byKey(const ValueKey('saved-server-retry')), findsOneWidget);
  });

  testWidgets('details expander reveals the raw error', (tester) async {
    tester.view.physicalSize = const Size(900, 1800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(
      _card(error: 'Health check failed: connection refused'),
    );
    expect(find.byKey(const ValueKey('saved-server-raw-error')), findsNothing);
    await tester.ensureVisible(
      find.byKey(const ValueKey('saved-server-details')),
    );
    await tester.tap(find.byKey(const ValueKey('saved-server-details')));
    await tester.pumpAndSettle();
    expect(
      find.byKey(const ValueKey('saved-server-raw-error')),
      findsOneWidget,
    );
    expect(
      find.text('Health check failed: connection refused'),
      findsOneWidget,
    );
  });

  testWidgets('a rejected password leads with Update password', (tester) async {
    tester.view.physicalSize = const Size(900, 1800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    var pressed = false;
    await tester.pumpWidget(
      _card(
        error: 'Health check failed (HTTP 401)',
        url: 'https://dev.tail.net',
        onPassword: () => pressed = true,
      ),
    );
    expect(find.text('Password rejected'), findsOneWidget);
    await tester.tap(
      find.byKey(const ValueKey('saved-server-update-password')),
    );
    expect(pressed, isTrue);
  });

  testWidgets('repeated attempts are counted in the connecting title', (
    tester,
  ) async {
    await tester.pumpWidget(_card(attempts: 3));
    expect(find.text('Connecting again (attempt 3)'), findsOneWidget);
  });
}
