import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations_en.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/widgets/saved_server_connection_card.dart';

Widget _card({
  String? error,
  int attempts = 1,
  bool termux = true,
  bool codex = false,
  bool tokenRequired = false,
  VoidCallback? onTermux,
  VoidCallback? onPassword,
  VoidCallback? onCertificate,
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
      onReviewCertificate: onCertificate,
      onUpdateToken: onToken,
    ),
  ),
);

void main() {
  // Jitter J1 (2026-10-28): above the last-known list on a cold start, the
  // card went "Starting OpenCode…" (with Switch server) then "Connecting…"
  // (without it, another picture) and the list under it jumped +47 / -38 dp.
  testWidgets('above the last-known list, starting and connecting keep one '
      'height', (tester) async {
    Future<double> height({required bool starting}) async {
      await tester.pumpWidget(
        MaterialApp(
          theme: AppTheme.light(),
          home: Scaffold(
            body: ListView(
              children: [
                SavedServerConnectionCard(
                  key: const ValueKey('card'),
                  size: KitStateSize.inline,
                  profileName: 'On this phone',
                  baseUrl: 'http://127.0.0.1:4097',
                  error: null,
                  attempts: 1,
                  supportsTermux: false,
                  inAppServer: true,
                  startingInAppServer: starting,
                  onStartPhoneServer: () {},
                  onChangeServer: () {},
                  onRetry: () {},
                ),
              ],
            ),
          ),
        ),
      );
      await tester.pump(const Duration(milliseconds: 500));
      return tester.getSize(find.byKey(const ValueKey('card'))).height;
    }

    final starting = await height(starting: true);
    expect(await height(starting: false), starting);
    expect(find.byKey(const ValueKey('saved-server-change')), findsOneWidget);
  });

  testWidgets('changed certificate offers review of this server', (
    tester,
  ) async {
    var reviews = 0;
    await tester.pumpWidget(
      _card(
        url: 'https://private.example:4099',
        error: 'The server certificate changed.',
        onCertificate: () => reviews++,
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text("The server's certificate changed"), findsOneWidget);
    await tester.tap(
      find.byKey(const ValueKey('saved-server-review-certificate')),
    );
    expect(reviews, 1);
  });

  testWidgets(
    'unknown ownership replaces restart and setup with safe recovery',
    (tester) async {
      var settings = 0;
      var starts = 0;
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: SavedServerConnectionCard(
              profileName: 'This phone',
              baseUrl: 'http://127.0.0.1:4097',
              error: 'IllegalStateException: ownershipUnknown',
              attempts: 1,
              supportsTermux: false,
              inAppServer: true,
              inAppStartFailed: true,
              inAppStartFailedBody:
                  AppLocalizationsEn().inAppServerOwnershipRecoveryBody,
              onRetry: () => starts++,
              onStartPhoneServer: () => starts++,
              onOpenInAppSetup: () => starts++,
              onRecoverInAppOwnership: () => settings++,
              onChangeServer: () {},
            ),
          ),
        ),
      );
      expect(find.textContaining('ownershipUnknown'), findsNothing);
      expect(find.text('Try again'), findsNothing);
      expect(
        find.byKey(const ValueKey('saved-server-start-phone')),
        findsNothing,
      );
      expect(
        find.byKey(const ValueKey('saved-server-open-in-app-setup')),
        findsNothing,
      );
      await tester.tap(
        find.byKey(const ValueKey('saved-server-recover-ownership')),
      );
      expect(settings, 1);
      expect(starts, 0);
    },
  );

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

  testWidgets('loopback keeps retry primary and Termux setup secondary', (
    tester,
  ) async {
    var opened = false;
    await tester.pumpWidget(
      _card(
        error: 'Health check failed: connection refused',
        onTermux: () => opened = true,
      ),
    );
    expect(find.text('Nothing answered on this phone'), findsOneWidget);
    // What to check is under Details, below the actions (standard §3).
    await tester.ensureVisible(find.byKey(const ValueKey('kit-state-details')));
    await tester.tap(find.byKey(const ValueKey('kit-state-details')));
    await tester.pumpAndSettle();
    expect(find.text('What to check'), findsOneWidget);
    expect(find.textContaining('Termux'), findsWidgets);
    await tester.ensureVisible(
      find.byKey(const ValueKey('saved-server-open-termux')),
    );
    await tester.tap(find.byKey(const ValueKey('saved-server-open-termux')));
    expect(opened, isTrue);
    // Retry is primary; optional setup is not presented as the diagnosis.
    expect(
      find.byKey(const ValueKey('saved-server-retry-primary')),
      findsOneWidget,
    );
    expect(
      tester.widget(find.byKey(const ValueKey('saved-server-open-termux'))),
      isA<KitButton>().having((b) => b.role, 'role', KitButtonRole.tertiary),
    );
  });

  testWidgets('remote failures do not offer a local Termux setup shortcut', (
    tester,
  ) async {
    await tester.pumpWidget(
      _card(
        error: 'connection refused',
        url: 'https://work.example',
        onTermux: () {},
      ),
    );
    expect(
      find.byKey(const ValueKey('saved-server-open-termux')),
      findsNothing,
    );
    expect(
      find.byKey(const ValueKey('saved-server-retry-primary')),
      findsOneWidget,
    );
  });

  testWidgets('details expander reveals the raw error', (tester) async {
    tester.view.physicalSize = const Size(900, 1800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(
      _card(error: 'Health check failed: connection refused'),
    );
    expect(find.byKey(const ValueKey('kit-state-details-text')), findsNothing);
    await tester.ensureVisible(find.byKey(const ValueKey('kit-state-details')));
    await tester.tap(find.byKey(const ValueKey('kit-state-details')));
    await tester.pumpAndSettle();
    expect(
      find.byKey(const ValueKey('kit-state-details-text')),
      findsOneWidget,
    );
    // The address, then the raw error, in mono.
    expect(
      find.text(
        'http://127.0.0.1:4096\nHealth check failed: connection refused',
      ),
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

  group('OpenCode inside the app', () {
    Widget inApp({
      String? error = 'Health check failed: connection refused',
      bool starting = false,
      bool startFailed = false,
      VoidCallback? onStart,
      VoidCallback? onSetup,
      VoidCallback? onTermux,
    }) => MaterialApp(
      theme: AppTheme.light(),
      home: Scaffold(
        body: SavedServerConnectionCard(
          profileName: 'This phone, built-in (OpenCode)',
          baseUrl: 'http://127.0.0.1:4097',
          error: error,
          attempts: 1,
          supportsTermux: false,
          onChangeServer: () {},
          onRetry: () {},
          onOpenTermuxSetup: onTermux,
          onStartPhoneServer: onStart ?? () {},
          inAppServer: true,
          startingInAppServer: starting,
          inAppStartFailed: startFailed,
          onOpenInAppSetup: onSetup,
        ),
      ),
    );

    testWidgets('stopped: one Start button, no Termux checklist', (
      tester,
    ) async {
      tester.view.physicalSize = const Size(900, 1800);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      var started = 0;
      await tester.pumpWidget(inApp(onStart: () => started++, onTermux: () {}));
      expect(find.text('OpenCode inside the app is stopped'), findsOneWidget);
      expect(find.text('Start and connect'), findsOneWidget);
      expect(find.textContaining('Termux'), findsNothing);
      expect(find.textContaining('adb'), findsNothing);
      expect(find.byKey(const ValueKey('saved-server-checks')), findsNothing);
      expect(
        find.byKey(const ValueKey('saved-server-open-termux')),
        findsNothing,
      );
      // Start already connects; no second "Try again" beside it.
      expect(find.byKey(const ValueKey('saved-server-retry')), findsNothing);
      expect(find.text('http://127.0.0.1:4097'), findsNothing);
      await tester.tap(find.byKey(const ValueKey('saved-server-start-phone')));
      expect(started, 1);
    });

    testWidgets('starting: the calm connecting state says so', (tester) async {
      await tester.pumpWidget(inApp(starting: true));
      expect(find.text('Starting OpenCode inside the app…'), findsOneWidget);
      expect(
        find.byKey(const ValueKey('saved-server-connect-progress')),
        findsOneWidget,
      );
      expect(find.text('OpenCode inside the app is stopped'), findsNothing);
    });

    testWidgets('a failed start points at the setup and its log', (
      tester,
    ) async {
      tester.view.physicalSize = const Size(900, 1800);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      var opened = false;
      await tester.pumpWidget(
        inApp(
          error: 'OpenCode did not answer: the server stopped.',
          startFailed: true,
          onSetup: () => opened = true,
        ),
      );
      expect(
        find.text('OpenCode inside the app did not start'),
        findsOneWidget,
      );
      await tester.tap(
        find.byKey(const ValueKey('saved-server-open-in-app-setup')),
      );
      expect(opened, isTrue);
    });
  });

  testWidgets('repeated attempts are counted in the connecting title', (
    tester,
  ) async {
    await tester.pumpWidget(_card(attempts: 3));
    expect(find.text('Connecting again (attempt 3)'), findsOneWidget);
  });

  testWidgets('a stopped phone server that is starting says Starting, never '
      'stopped, and no button stands in for the progress', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        theme: AppTheme.light(),
        home: Scaffold(
          body: SavedServerConnectionCard(
            profileName: 'This device (Termux)',
            baseUrl: 'http://127.0.0.1:4096',
            error: 'Cannot reach http://127.0.0.1:4096: Connection refused',
            attempts: 1,
            supportsTermux: true,
            onChangeServer: () {},
            onRetry: () {},
            onStartPhoneServer: () {},
            startingPhoneServer: true,
          ),
        ),
      ),
    );
    expect(find.text('Starting OpenCode on this phone…'), findsOneWidget);
    expect(find.textContaining('stopped'), findsNothing);
    expect(
      find.byKey(const ValueKey('saved-server-connect-progress')),
      findsOneWidget,
    );
    expect(find.byType(FilledButton), findsNothing);
    expect(find.byType(CircularProgressIndicator), findsNothing);
    expect(find.text('Switch server'), findsOneWidget);
  });

  testWidgets('three failed tries lead with Switch server, Try again beside', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(900, 1800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(
      _card(
        error: 'connection refused',
        url: 'https://work.example',
        attempts: 3,
      ),
    );
    expect(
      find.byKey(const ValueKey('saved-server-change-primary')),
      findsOneWidget,
    );
    expect(find.byKey(const ValueKey('saved-server-retry')), findsOneWidget);
    // The address is under Details, not in the words.
    expect(find.textContaining('work.example'), findsNothing);
  });

  testWidgets('a stopped phone server offers Start, never Try again', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(900, 1800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(
      MaterialApp(
        theme: AppTheme.light(),
        home: Scaffold(
          body: SavedServerConnectionCard(
            profileName: 'This device (Termux)',
            baseUrl: 'http://127.0.0.1:4096',
            error: 'Cannot reach http://127.0.0.1:4096: Connection refused',
            attempts: 1,
            supportsTermux: true,
            onChangeServer: () {},
            onRetry: () {},
            onStartPhoneServer: () {},
          ),
        ),
      ),
    );
    expect(
      find.byKey(const ValueKey('saved-server-start-phone')),
      findsOneWidget,
    );
    expect(find.byKey(const ValueKey('saved-server-retry')), findsNothing);
  });

  testWidgets('a Tailscale address offers its setup when the app has it', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(900, 1800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    addTearDown(KitCapabilities.debugReset);
    KitCapabilities.debugReset();
    await tester.pumpWidget(
      _card(error: 'connection refused', url: 'http://100.100.1.2:4096'),
    );
    // No flow registered: nothing that leads nowhere.
    expect(find.byKey(const ValueKey('saved-server-tailscale')), findsNothing);

    KitRequestCount.value = 0;
    KitCapabilities.registerFlow(
      KitEnableFlows.tailscaleSetup,
      (context, request) async => KitRequestCount.value++,
    );
    await tester.pumpWidget(const SizedBox());
    await tester.pumpWidget(
      _card(error: 'connection refused', url: 'http://100.100.1.2:4096'),
    );
    await tester.ensureVisible(
      find.byKey(const ValueKey('saved-server-tailscale')),
    );
    await tester.tap(find.byKey(const ValueKey('saved-server-tailscale')));
    await tester.pump();
    expect(KitRequestCount.value, 1);
  });
}

/// Counts enable-flow calls in the Tailscale test.
abstract final class KitRequestCount {
  static int value = 0;
}
