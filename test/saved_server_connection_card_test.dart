import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/widgets/saved_server_connection_card.dart';

Widget _card({
  String? error,
  int attempts = 1,
  bool termux = true,
  bool codex = false,
  bool tokenRequired = false,
  VoidCallback? onTermux,
  VoidCallback? onPassword,
  VoidCallback? onToken,
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
      usesConnectionToken: codex,
      requiresTokenReentry: tokenRequired,
      onChangeServer: () {},
      onRetry: () {},
      onOpenTermuxSetup: onTermux,
      onUpdatePassword: onPassword,
      onUpdateToken: onToken,
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

  testWidgets('a rejected Codex token leads with Update token', (tester) async {
    var pressed = false;
    await tester.pumpWidget(
      _card(
        codex: true,
        error: 'Codex authentication failed: token rejected',
        url: 'wss://codex.example',
        onToken: () => pressed = true,
      ),
    );
    expect(find.text('Connection token rejected'), findsOneWidget);
    expect(find.textContaining('Termux'), findsNothing);
    await tester.ensureVisible(
      find.byKey(const ValueKey('saved-server-update-token')),
    );
    await tester.tap(find.byKey(const ValueKey('saved-server-update-token')));
    expect(pressed, isTrue);
  });

  testWidgets('a missing Codex token is actionable even without an error', (
    tester,
  ) async {
    var pressed = false;
    await tester.pumpWidget(
      _card(codex: true, tokenRequired: true, onToken: () => pressed = true),
    );
    expect(find.text('Connection token required'), findsOneWidget);
    expect(
      find.byKey(const ValueKey('saved-server-update-token')),
      findsOneWidget,
    );
    await tester.ensureVisible(
      find.byKey(const ValueKey('saved-server-update-token')),
    );
    await tester.tap(find.byKey(const ValueKey('saved-server-update-token')));
    expect(pressed, isTrue);
  });

  testWidgets('a Codex local failure never offers the Termux path', (
    tester,
  ) async {
    await tester.pumpWidget(
      _card(
        codex: true,
        error: 'Health check failed: connection refused',
        onTermux: () {},
      ),
    );
    expect(find.text('Codex listener unavailable'), findsOneWidget);
    expect(
      find.byKey(const ValueKey('saved-server-open-termux')),
      findsNothing,
    );
    expect(find.textContaining('opencode'), findsNothing);
  });

  testWidgets('repeated attempts are counted in the connecting title', (
    tester,
  ) async {
    await tester.pumpWidget(_card(attempts: 3));
    expect(find.text('Connecting again (attempt 3)'), findsOneWidget);
  });
}
