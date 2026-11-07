// Issue #124 ("Could not load this section"): the Providers page and the
// MCP tab never show an error for an expected condition (a reconnect, a
// connection that is not ready yet, a list the server does not have), and a
// real failure names its section and says why in plain words, with the
// technical text under Details.
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api2/transport.dart';
import 'package:opencode_mobile/domain/product_repository.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/ui/screens/library_screen.dart';
import 'package:opencode_mobile/ui/widgets/provider_logo.dart';

import 'support/library_integrations_fixtures.dart';

Widget _page(ConnectionController controller, IntegrationsMode mode) =>
    MaterialApp(
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      home: IntegrationsScreen(controller: controller, mode: mode),
    );

const _github = IntegrationInfo(
  id: 'github',
  name: 'GitHub',
  methods: [IntegrationMethodInfo(type: 'key', label: 'API key')],
  connectionCount: 0,
);

/// The gateway's wrapper around a transport failure, as `_guard` makes it.
ProductException _wrapped(Object cause) =>
    ProductException('Could not load integrations', cause: cause);

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
  setUpAll(() => ProviderLogo.logoLoaderOverride = (_) => null);
  tearDownAll(() => ProviderLogo.logoLoaderOverride = null);

  testWidgets('the MCP tab reloads by itself after a reconnect instead of '
      'saying it could not load', (tester) async {
    final first = IntegrationsRepository()
      ..servers = const [McpServerInfo(name: 'docs', status: 'connected')];
    final controller = await integrationsController(first);
    addTearDown(controller.dispose);
    await tester.pumpWidget(_page(controller, IntegrationsMode.mcp));
    await tester.pumpAndSettle();
    expect(find.text('docs'), findsOneWidget);

    // The app came back from the background: a new transport.
    final second = IntegrationsRepository()
      ..servers = const [McpServerInfo(name: 'tickets', status: 'connected')];
    reconnectIntegrations(controller, second);
    await tester.pumpAndSettle();

    expect(find.text('Could not load this section'), findsNothing);
    expect(find.byKey(const ValueKey('mcp-load-failed')), findsNothing);
    expect(find.text('docs'), findsNothing);
    expect(find.text('tickets'), findsOneWidget);
  });

  testWidgets('providers opened while the connection is not ready wait for '
      'it, then load by themselves', (tester) async {
    final controller = await integrationsController(null);
    addTearDown(controller.dispose);
    await tester.pumpWidget(_page(controller, IntegrationsMode.providers));
    await tester.pumpAndSettle();

    expect(find.text('Waiting for the server'), findsOneWidget);
    expect(find.text('Could not load this section'), findsNothing);
    expect(find.byKey(const ValueKey('providers-load-failed')), findsNothing);

    reconnectIntegrations(
      controller,
      IntegrationsRepository()..integrations = const [_github],
    );
    await tester.pumpAndSettle();

    expect(find.text('Waiting for the server'), findsNothing);
    expect(find.text('GitHub'), findsOneWidget);
  });

  testWidgets('a server without the resource list says so calmly: no error, '
      'no Try again', (tester) async {
    final repository = IntegrationsRepository()
      ..servers = const [McpServerInfo(name: 'docs', status: 'connected')]
      // OpenCode answers a route it does not have with a bare 404.
      ..resourceError = const ProductException(
        'Could not load MCP resources',
        cause: Api2RequestError(
          'GET /mcp/resource failed (HTTP 404): HTTP 404',
          statusCode: 404,
        ),
      );
    final controller = await integrationsController(repository);
    addTearDown(controller.dispose);
    await tester.pumpWidget(_page(controller, IntegrationsMode.mcp));
    await tester.pumpAndSettle();

    expect(find.text('docs'), findsOneWidget);
    expect(find.text("This server doesn't list MCP resources"), findsOneWidget);
    expect(find.text('Could not load this section'), findsNothing);
    expect(find.byKey(const ValueKey('resources-load-failed')), findsNothing);
    expect(find.text('Try again'), findsNothing);
  });

  testWidgets('an unreachable server names the section and says why; the '
      'technical text stays under Details', (tester) async {
    final repository = IntegrationsRepository()
      ..integrationError = _wrapped(
        const Api2NetworkError(
          'GET /integration failed: Connection refused, errno = 111',
        ),
      );
    final controller = await integrationsController(repository);
    addTearDown(controller.dispose);
    await tester.pumpWidget(_page(controller, IntegrationsMode.providers));
    await tester.pumpAndSettle();

    expect(find.text('Could not load providers'), findsOneWidget);
    expect(find.text('OpenCode is unreachable. Try again.'), findsOneWidget);
    expect(find.text('Could not load this section'), findsNothing);
    expect(find.textContaining('errno'), findsNothing);
    expect(find.textContaining('Could not load integrations'), findsNothing);
    // The technical text is kept for Details and Copy details, folded.
    expect(
      find.byKey(const ValueKey('kit-state-copy-details')),
      findsOneWidget,
    );

    // Try again reloads it.
    repository
      ..integrationError = null
      ..integrations = const [_github];
    await tester.tap(find.text('Try again'));
    await tester.pumpAndSettle();
    expect(find.text('GitHub'), findsOneWidget);
  });

  testWidgets('a project folder the server does not have, and a refused '
      'password, each say what to do', (tester) async {
    final repository = IntegrationsRepository()
      ..serverError = const ProductException(
        'Could not load MCP servers',
        cause: Api2RequestError(
          'GET /mcp failed (HTTP 404): Location not found: /nonexistent/dir',
          statusCode: 404,
          tag: 'LocationNotFoundError',
          body: {
            '_tag': 'LocationNotFoundError',
            'location': {'directory': '/nonexistent/dir'},
            'message': 'Location not found: /nonexistent/dir',
          },
        ),
      );
    final controller = await integrationsController(repository);
    addTearDown(controller.dispose);
    await tester.pumpWidget(_page(controller, IntegrationsMode.mcp));
    await tester.pumpAndSettle();

    expect(find.text('Could not load MCP servers'), findsOneWidget);
    expect(
      find.text(
        "This project's folder isn't on the server any more. "
        'Open another project.',
      ),
      findsOneWidget,
    );
    expect(find.textContaining('/nonexistent/dir'), findsNothing);

    repository.serverError = _wrapped(
      const Api2AuthRequired('GET /mcp: authentication required'),
    );
    await tester.tap(find.text('Try again'));
    await tester.pumpAndSettle();
    expect(find.text('Could not load MCP servers'), findsOneWidget);
    expect(
      find.text(
        "The server didn't accept the sign-in. Check the password in the "
        "server's settings.",
      ),
      findsOneWidget,
    );
  });
}
