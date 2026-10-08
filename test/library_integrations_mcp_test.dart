// Providers screen behaviour: MCP resources, MCP sign-in and the MCP actions
// on the Providers screen (split from library_integrations_test.dart).

import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/mcp_oauth.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/widgets/provider_logo.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'support/library_integrations_fixtures.dart';

class _SwitchingRepositoryController extends ConnectionController {
  _SwitchingRepositoryController(
    super.store,
    this.initialRepository,
    this.readyRepository,
  );

  final ProductRepository initialRepository;
  final Completer<ProductRepository?> readyRepository;
  int actionRepositoryCalls = 0;

  @override
  Future<ProductRepository?> prepareActionRepository() {
    actionRepositoryCalls += 1;
    if (actionRepositoryCalls == 1) return Future.value(initialRepository);
    return readyRepository.future.then((replacement) {
      repository = replacement;
      return replacement;
    });
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  const secureStorage = MethodChannel(
    'plugins.it_nomads.com/flutter_secure_storage',
  );

  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(secureStorage, (_) async => null);
  });

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(secureStorage, null);
  });

  // Provider logos are fetched favicons; tests render the monogram instead.
  setUpAll(() => ProviderLogo.imageProviderOverride = (_) => null);

  tearDownAll(() => ProviderLogo.imageProviderOverride = null);

  testWidgets('MCP resources remain available when integrations fail', (
    tester,
  ) async {
    final repository = IntegrationsRepository()
      ..integrationError = const ProductException('Providers unavailable')
      ..resources = const [
        McpResourceInfo(
          name: 'Project handbook',
          server: 'docs',
          uri: 'mcp://docs/handbook',
        ),
      ];

    await tester.pumpWidget(app(await integrationsController(repository)));
    await tester.pumpAndSettle();

    expect(find.text('Providers unavailable'), findsOneWidget);
    expect(find.text('Could not load this section'), findsOneWidget);
    await tester.scrollUntilVisible(
      find.text('Project handbook'),
      180,
      scrollable: find.byType(Scrollable).first,
    );
    expect(find.text('Project handbook'), findsOneWidget);
  });

  testWidgets(
    'every section failing says so once, and one Try again reloads them all',
    (tester) async {
      tester.view.physicalSize = const Size(412, 915);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final repository = IntegrationsRepository()
        ..integrationError = const ProductException('Providers unavailable')
        ..serverError = const ProductException('MCP unavailable')
        ..resourceError = const ProductException('Resources unavailable');

      await tester.pumpWidget(app(await integrationsController(repository)));
      await tester.pumpAndSettle();

      // One primary per screen (KitScreen asserts it): one error for the
      // page, at the first failed section, never three.
      expect(tester.takeException(), isNull);
      expect(find.text('Could not load this page'), findsOneWidget);
      expect(find.text('Could not load this section'), findsNothing);
      final retry = find.widgetWithText(KitButton, 'Try again');
      expect(retry, findsOneWidget);

      repository
        ..integrationError = null
        ..serverError = null
        ..resourceError = null
        ..resources = const [
          McpResourceInfo(
            name: 'Project handbook',
            server: 'docs',
            uri: 'mcp://docs/handbook',
          ),
        ];
      await tester.tap(retry);
      await tester.pumpAndSettle();

      expect(find.text('Could not load this page'), findsNothing);
      expect(find.byKey(const ValueKey('mcp-empty')), findsOneWidget);
      await tester.scrollUntilVisible(
        find.text('Project handbook'),
        180,
        scrollable: find.byType(Scrollable).first,
      );
      expect(find.text('Project handbook'), findsOneWidget);
    },
  );

  testWidgets('MCP Disconnect waits for the confirm sheet', (tester) async {
    final repository = IntegrationsRepository()
      ..servers = const [
        McpServerInfo(name: 'remote-tools', status: 'connected'),
      ];
    await tester.pumpWidget(app(await integrationsController(repository)));
    await tester.pumpAndSettle();

    Future<void> tapDisconnect() async {
      final row = find.byKey(const ValueKey('mcp-server-remote-tools'));
      await tester.ensureVisible(row);
      await tester.tap(row);
      await tester.pumpAndSettle();
      await tester.tap(
        find.byKey(const ValueKey('mcp-disconnect-remote-tools')),
      );
      await tester.pumpAndSettle();
    }

    await tapDisconnect();
    expect(
      find.byKey(const ValueKey('mcp-disconnect-confirm-sheet')),
      findsOneWidget,
    );
    expect(find.text('Disconnect remote-tools?'), findsOneWidget);
    expect(find.textContaining('Agents lose its tools'), findsOneWidget);
    await tester.tap(find.text('Stay connected'));
    await tester.pumpAndSettle();
    expect(repository.mcpDisconnected, isEmpty);

    await tapDisconnect();
    await tester.tapAt(const Offset(10, 10));
    await tester.pumpAndSettle();
    expect(
      find.byKey(const ValueKey('mcp-disconnect-confirm-sheet')),
      findsNothing,
    );
    expect(repository.mcpDisconnected, isEmpty);

    await tapDisconnect();
    await tester.tap(find.byKey(const ValueKey('confirm-mcp-disconnect')));
    await tester.pumpAndSettle();
    expect(repository.mcpDisconnected, ['remote-tools']);
  });

  testWidgets('MCP authentication shows the validated destination host', (
    tester,
  ) async {
    final repository = IntegrationsRepository()
      ..servers = const [
        McpServerInfo(name: 'remote-tools', status: 'needs_auth'),
      ]
      ..mcpAuthLaunch = McpAuthLaunch(
        authorizationUrl: Uri.parse(
          'https://mcp-auth.example.com:8443/authorize?state=secret',
        ),
        oauthState: 'mcp-state-1',
      );

    await tester.pumpWidget(app(await integrationsController(repository)));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Sign in to remote-tools'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));

    expect(
      find.byKey(const ValueKey('authorization-launch-sheet')),
      findsOneWidget,
    );
    expect(find.text('Sign in at mcp-auth.example.com:8443?'), findsOneWidget);
    expect(find.textContaining('state=secret'), findsNothing);

    await tester.tap(find.text('Cancel'));
    await tester.pumpAndSettle();
  });

  testWidgets('unsafe MCP authentication URL is rejected before confirmation', (
    tester,
  ) async {
    final repository = IntegrationsRepository()
      ..servers = const [
        McpServerInfo(name: 'remote-tools', status: 'needs_auth'),
      ]
      ..mcpAuthLaunch = McpAuthLaunch(
        authorizationUrl: Uri.parse('opencode://authorize'),
        oauthState: 'mcp-state-1',
      );

    await tester.pumpWidget(app(await integrationsController(repository)));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Sign in to remote-tools'));
    await tester.pump();

    expect(
      find.byKey(const ValueKey('authorization-launch-sheet')),
      findsNothing,
    );
    expect(find.textContaining('unsafe authorization link'), findsOneWidget);
    expect(find.textContaining('opencode://authorize'), findsNothing);
    expect(repository.mcpCompleteCalls, 0);
  });

  testWidgets('MCP authorization completes from a state-validated callback URL', (
    tester,
  ) async {
    Uri? opened;
    final repository = IntegrationsRepository()
      ..servers = const [
        McpServerInfo(name: 'remote-tools', status: 'needs_auth'),
      ]
      ..mcpAuthLaunch = McpAuthLaunch(
        authorizationUrl: Uri.parse(
          'https://mcp-auth.example.com/authorize?client_id=mobile',
        ),
        oauthState: 'mcp-state-1',
      );

    await tester.pumpWidget(
      app(
        await integrationsController(repository),
        authorizationLauncher: (destination) async {
          opened = destination;
          return true;
        },
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('Sign in to remote-tools'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));
    await tester.tap(find.text('Open browser'));
    await tester.pump(const Duration(milliseconds: 300));
    expect(find.text('Open external link?'), findsOneWidget);
    // The confirmation slides in; let it land before tapping.
    await tester.pump(const Duration(milliseconds: 300));
    await tester.tap(find.text('Open link'));
    await tester.pumpAndSettle();

    expect(opened?.host, 'mcp-auth.example.com');
    expect(find.byKey(const ValueKey('pending-mcp-oauth')), findsOneWidget);
    expect(
      find.textContaining('Automatic callback capture is unavailable'),
      findsOneWidget,
    );

    await tester.ensureVisible(
      find.byKey(const ValueKey('enter-mcp-oauth-code')),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('enter-mcp-oauth-code')));
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const ValueKey('mcp-oauth-code-input')),
      'http://127.0.0.1:19876/mcp/oauth/callback?code=code-1&state=mcp-state-1',
    );
    await tester.tap(find.byKey(const ValueKey('complete-mcp-oauth')));
    await tester.pumpAndSettle();

    expect(repository.mcpCompleteCalls, 1);
    expect(repository.mcpCompletionCode, 'code-1');
    expect(find.byKey(const ValueKey('pending-mcp-oauth')), findsNothing);
    expect(
      find.textContaining(
        'Connected and tools are available',
        findRichText: true,
      ),
      findsOneWidget,
    );
    expect(find.text('remote-tools authenticated'), findsOneWidget);
  });

  testWidgets(
    'MCP authorization rejects mismatched state and can be cancelled',
    (tester) async {
      final repository = IntegrationsRepository()
        ..servers = const [
          McpServerInfo(name: 'remote-tools', status: 'needs_auth'),
        ]
        ..mcpAuthLaunch = McpAuthLaunch(
          authorizationUrl: Uri.parse(
            'https://mcp-auth.example.com/authorize?client_id=mobile',
          ),
          oauthState: 'mcp-state-1',
        );

      await tester.pumpWidget(
        app(
          await integrationsController(repository),
          authorizationLauncher: (_) async => true,
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('Sign in to remote-tools'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 300));
      await tester.tap(find.text('Open browser'));
      await tester.pump(const Duration(milliseconds: 300));
      expect(find.text('Open external link?'), findsOneWidget);
      // The confirmation slides in; let it land before tapping.
      await tester.pump(const Duration(milliseconds: 300));
      await tester.tap(find.text('Open link'));
      await tester.pumpAndSettle();
      await tester.ensureVisible(
        find.byKey(const ValueKey('enter-mcp-oauth-code')),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('enter-mcp-oauth-code')));
      await tester.pumpAndSettle();
      await tester.enterText(
        find.byKey(const ValueKey('mcp-oauth-code-input')),
        'http://127.0.0.1:19876/mcp/oauth/callback?code=code-1&state=wrong',
      );
      await tester.tap(find.byKey(const ValueKey('complete-mcp-oauth')));
      await tester.pump();

      expect(find.textContaining('state does not match'), findsOneWidget);
      expect(repository.mcpCompleteCalls, 0);
      await tester.tap(find.text('Cancel'));
      await tester.pumpAndSettle();
      await tester.ensureVisible(
        find.byKey(const ValueKey('cancel-mcp-oauth')),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('cancel-mcp-oauth')));
      await tester.pumpAndSettle();

      expect(repository.mcpCancelCalls, 1);
      expect(find.byKey(const ValueKey('pending-mcp-oauth')), findsNothing);
    },
  );

  testWidgets('pending MCP authorization fits compact large-text phones', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(320, 480);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final repository = IntegrationsRepository()
      ..servers = const [
        McpServerInfo(name: 'remote-tools', status: 'needs_auth'),
      ]
      ..mcpAuthLaunch = McpAuthLaunch(
        authorizationUrl: Uri.parse(
          'https://mcp-auth.example.com/authorize?client_id=mobile',
        ),
        oauthState: 'mcp-state-1',
      );

    await tester.pumpWidget(
      app(
        await integrationsController(repository),
        authorizationLauncher: (_) async => true,
        textScale: 2,
      ),
    );
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    // Providers lead the screen now, so the MCP row starts below the fold.
    await tester.scrollUntilVisible(
      find.text('Sign in to remote-tools'),
      120,
      scrollable: find.byType(Scrollable).first,
    );
    await tester.ensureVisible(find.text('Sign in to remote-tools'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Sign in to remote-tools'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));
    expect(tester.takeException(), isNull);
    await tester.ensureVisible(find.text('Open browser'));
    await tester.tap(find.text('Open browser'));
    await tester.pump(const Duration(milliseconds: 300));
    expect(find.text('Open external link?'), findsOneWidget);
    // The confirmation slides in; let it land before tapping.
    await tester.pump(const Duration(milliseconds: 300));
    await tester.tap(find.text('Open link'));
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    await tester.scrollUntilVisible(
      find.byKey(const ValueKey('enter-mcp-oauth-code')),
      160,
      scrollable: find.byType(Scrollable).first,
    );

    expect(find.byKey(const ValueKey('enter-mcp-oauth-code')), findsOneWidget);
    expect(find.byKey(const ValueKey('cancel-mcp-oauth')), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('MCP actions wait for the wake-time replacement repository', (
    tester,
  ) async {
    final retainedRepository = IntegrationsRepository()
      ..servers = const [
        McpServerInfo(name: 'remote-tools', status: 'disabled'),
      ];
    final replacementRepository = IntegrationsRepository()
      ..servers = const [
        McpServerInfo(name: 'remote-tools', status: 'connected'),
      ];
    SharedPreferences.setMockInitialValues({});
    final preferences = await SharedPreferences.getInstance();
    final readyRepository = Completer<ProductRepository?>();
    final store = ProfileStore(prefs: preferences);
    await store.upsert(
      ServerProfile(
        id: 'switching-test',
        name: 'Test server',
        baseUrl: 'https://integrations.example',
      ),
    );
    await store.setActiveId('switching-test');
    final controller =
        _SwitchingRepositoryController(
            store,
            retainedRepository,
            readyRepository,
          )
          ..repository = retainedRepository
          ..status = StreamStatus.connected;
    addTearDown(controller.dispose);

    await tester.pumpWidget(app(controller));
    await tester.pumpAndSettle();
    await tester.ensureVisible(find.text('Connect remote-tools'));
    await tester.tap(find.text('Connect remote-tools'));
    await tester.pump();

    expect(retainedRepository.mcpConnectCalls, 0);
    expect(replacementRepository.mcpConnectCalls, 0);

    readyRepository.complete(replacementRepository);
    await tester.pumpAndSettle();

    expect(retainedRepository.mcpConnectCalls, 0);
    expect(replacementRepository.mcpConnectCalls, 1);
    expect(
      find.textContaining(
        'Connected and tools are available',
        findRichText: true,
      ),
      findsOneWidget,
    );
  });
}
