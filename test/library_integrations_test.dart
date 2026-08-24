import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/library_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _IntegrationsRepository implements ProductRepository {
  List<McpServerInfo> servers = const [];
  List<McpResourceInfo> resources = const [];
  List<IntegrationInfo> integrations = const [];
  Object? serverError;
  Object? resourceError;
  Object? integrationError;
  String mcpAuthorizationUrl = 'https://mcp-auth.example.com/authorize';
  IntegrationAuthLaunch oauthLaunch = const IntegrationAuthLaunch(
    url: 'https://provider-auth.example.com/authorize',
    instructions: '',
  );
  Map<String, String>? oauthInputs;
  int oauthCalls = 0;

  @override
  void setLocation({String? directory, String? workspace}) {}

  @override
  Future<List<McpServerInfo>> listMcpServers() async {
    if (serverError case final error?) throw error;
    return servers;
  }

  @override
  Future<List<McpResourceInfo>> listMcpResources() async {
    if (resourceError case final error?) throw error;
    return resources;
  }

  @override
  Future<List<IntegrationInfo>> listIntegrations() async {
    if (integrationError case final error?) throw error;
    return integrations;
  }

  @override
  Future<String> startMcpAuthentication(String name) async =>
      mcpAuthorizationUrl;

  @override
  Future<IntegrationAuthLaunch> startIntegrationOAuth(
    String id,
    String methodID, {
    Map<String, String> inputs = const {},
    String? label,
  }) async {
    oauthCalls++;
    oauthInputs = Map.of(inputs);
    return oauthLaunch;
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

Future<ConnectionController> _controller(ProductRepository repository) async {
  SharedPreferences.setMockInitialValues({});
  final preferences = await SharedPreferences.getInstance();
  return ConnectionController(ProfileStore(prefs: preferences))
    ..repository = repository
    ..status = StreamStatus.connected;
}

Widget _app(ConnectionController controller) =>
    MaterialApp(home: IntegrationsScreen(controller: controller));

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('authorization URL policy accepts only credential-free HTTPS hosts', () {
    expect(
      parseAuthorizationUrl('https://login.example.com/oauth?state=abc'),
      Uri.parse('https://login.example.com/oauth?state=abc'),
    );

    for (final value in [
      'http://login.example.com/oauth',
      'javascript:alert(1)',
      'opencode://oauth/callback',
      'https:///oauth',
      'https://user:password@login.example.com/oauth',
      '',
    ]) {
      expect(
        () => parseAuthorizationUrl(value),
        throwsA(isA<ProductException>()),
        reason: value,
      );
    }
  });

  testWidgets(
    'provider integrations remain available when MCP resources fail',
    (tester) async {
      final repository = _IntegrationsRepository()
        ..resourceError = const ProductException('Resources unavailable')
        ..integrations = const [
          IntegrationInfo(
            id: 'github',
            name: 'GitHub',
            methods: [
              IntegrationMethodInfo(
                type: 'oauth',
                id: 'oauth',
                label: 'GitHub OAuth',
              ),
            ],
            connectionCount: 0,
          ),
        ];

      await tester.pumpWidget(_app(await _controller(repository)));
      await tester.pumpAndSettle();

      expect(find.text('GitHub'), findsOneWidget);
      expect(find.text('Resources unavailable'), findsOneWidget);
      expect(find.text('Could not load this section'), findsOneWidget);
    },
  );

  testWidgets('MCP resources remain available when integrations fail', (
    tester,
  ) async {
    final repository = _IntegrationsRepository()
      ..integrationError = const ProductException('Providers unavailable')
      ..resources = const [
        McpResourceInfo(
          name: 'Project handbook',
          server: 'docs',
          uri: 'mcp://docs/handbook',
        ),
      ];

    await tester.pumpWidget(_app(await _controller(repository)));
    await tester.pumpAndSettle();

    expect(find.text('Project handbook'), findsOneWidget);
    expect(find.text('Providers unavailable'), findsOneWidget);
    expect(find.text('Could not load this section'), findsOneWidget);
  });

  testWidgets('MCP authentication shows the validated destination host', (
    tester,
  ) async {
    final repository = _IntegrationsRepository()
      ..servers = const [
        McpServerInfo(name: 'remote-tools', status: 'needs_auth'),
      ]
      ..mcpAuthorizationUrl =
          'https://mcp-auth.example.com:8443/authorize?state=secret';

    await tester.pumpWidget(_app(await _controller(repository)));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Authenticate'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));

    expect(find.text('Open authorization page?'), findsOneWidget);
    expect(find.text('Destination host'), findsOneWidget);
    expect(find.text('mcp-auth.example.com:8443'), findsOneWidget);
    expect(find.textContaining('state=secret'), findsNothing);

    await tester.tap(find.text('Cancel'));
    await tester.pumpAndSettle();
  });

  testWidgets('unsafe MCP authentication URL is rejected before confirmation', (
    tester,
  ) async {
    final repository = _IntegrationsRepository()
      ..servers = const [
        McpServerInfo(name: 'remote-tools', status: 'needs_auth'),
      ]
      ..mcpAuthorizationUrl = 'opencode://authorize';

    await tester.pumpWidget(_app(await _controller(repository)));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Authenticate'));
    await tester.pump();

    expect(find.text('Open authorization page?'), findsNothing);
    expect(find.textContaining('unsafe authorization link'), findsOneWidget);
  });

  testWidgets(
    'OAuth rejects blank required text while allowing blank optional text',
    (tester) async {
      final repository = _IntegrationsRepository()
        ..integrations = const [
          IntegrationInfo(
            id: 'cloud',
            name: 'Cloud Provider',
            methods: [
              IntegrationMethodInfo(
                type: 'oauth',
                id: 'oauth-1',
                label: 'Cloud OAuth',
                prompts: [
                  {'type': 'text', 'key': 'tenant', 'message': 'Tenant'},
                  {
                    'type': 'text',
                    'key': 'label',
                    'message': 'Optional label',
                    'required': false,
                  },
                ],
              ),
            ],
            connectionCount: 0,
          ),
        ];

      await tester.pumpWidget(_app(await _controller(repository)));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Connect'));
      await tester.pumpAndSettle();

      await tester.enterText(
        find.byKey(const ValueKey('oauth-prompt-tenant')),
        '   ',
      );
      await tester.tap(find.text('Continue'));
      await tester.pump();

      expect(find.text('Enter a value'), findsOneWidget);
      expect(repository.oauthCalls, 0);

      await tester.enterText(
        find.byKey(const ValueKey('oauth-prompt-tenant')),
        'acme',
      );
      await tester.tap(find.text('Continue'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 300));

      expect(repository.oauthCalls, 1);
      expect(repository.oauthInputs, {'tenant': 'acme', 'label': ''});
      expect(find.text('Open authorization page?'), findsOneWidget);

      await tester.tap(find.text('Cancel'));
      await tester.pumpAndSettle();
    },
  );

  testWidgets(
    'OAuth conditions validate visible selects and submit only visible values',
    (tester) async {
      final repository = _IntegrationsRepository()
        ..integrations = const [
          IntegrationInfo(
            id: 'cloud',
            name: 'Cloud Provider',
            methods: [
              IntegrationMethodInfo(
                type: 'oauth',
                id: 'oauth-1',
                label: 'Cloud OAuth',
                prompts: [
                  {
                    'type': 'select',
                    'key': 'mode',
                    'message': 'Connection mode',
                    'options': [
                      {'label': 'Advanced', 'value': 'advanced'},
                      {'label': 'Basic', 'value': 'basic'},
                    ],
                  },
                  {
                    'type': 'text',
                    'key': 'secret',
                    'message': 'Advanced secret',
                    'when': {'key': 'mode', 'op': 'eq', 'value': 'advanced'},
                  },
                  {
                    'type': 'select',
                    'key': 'workspace',
                    'message': 'Workspace',
                    'options': [
                      {'label': 'Production', 'value': 'production'},
                    ],
                    'when': {'key': 'mode', 'op': 'eq', 'value': 'advanced'},
                  },
                  {
                    'type': 'text',
                    'key': 'note',
                    'message': 'Basic note',
                    'when': {'key': 'mode', 'op': 'neq', 'value': 'advanced'},
                  },
                ],
              ),
            ],
            connectionCount: 0,
          ),
        ];

      await tester.pumpWidget(_app(await _controller(repository)));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Connect'));
      await tester.pumpAndSettle();

      await tester.tap(find.byKey(const ValueKey('oauth-prompt-mode')));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Advanced').last);
      await tester.pumpAndSettle();

      expect(find.text('Advanced secret'), findsOneWidget);
      expect(find.text('Workspace'), findsOneWidget);
      expect(find.text('Basic note'), findsNothing);
      await tester.enterText(
        find.byKey(const ValueKey('oauth-prompt-secret')),
        'do-not-submit',
      );
      await tester.tap(find.text('Continue'));
      await tester.pump();

      expect(find.text('Select an option'), findsOneWidget);
      expect(repository.oauthCalls, 0);

      await tester.tap(find.byKey(const ValueKey('oauth-prompt-workspace')));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Production').last);
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('oauth-prompt-mode')));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Basic').last);
      await tester.pumpAndSettle();

      expect(find.text('Advanced secret'), findsNothing);
      expect(find.text('Workspace'), findsNothing);
      expect(find.text('Basic note'), findsOneWidget);
      await tester.enterText(
        find.byKey(const ValueKey('oauth-prompt-note')),
        'visible value',
      );
      await tester.tap(find.text('Continue'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 300));

      expect(repository.oauthCalls, 1);
      expect(repository.oauthInputs, {
        'mode': 'basic',
        'note': 'visible value',
      });
      expect(find.text('provider-auth.example.com'), findsOneWidget);
      expect(find.text('Open authorization page?'), findsOneWidget);

      await tester.tap(find.text('Cancel'));
      await tester.pumpAndSettle();
    },
  );
}
