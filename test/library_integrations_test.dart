import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/library_screen.dart';
import 'package:opencode_mobile/ui/screens/mcp_setup_screen.dart';
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
    attemptID: 'attempt-1',
    url: 'https://provider-auth.example.com/authorize',
    instructions: '',
    mode: IntegrationAuthMode.auto,
  );
  IntegrationAuthStatus oauthStatus = const IntegrationAuthStatus(
    state: IntegrationAuthState.pending,
  );
  Map<String, String>? oauthInputs;
  int oauthCalls = 0;
  int oauthStatusCalls = 0;
  int oauthCompleteCalls = 0;
  int oauthCancelCalls = 0;
  int providerRefreshCalls = 0;
  int mcpConnectCalls = 0;
  String? oauthCompletionCode;

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
  Future<void> connectMcp(String name) async {
    mcpConnectCalls += 1;
  }

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
  Future<IntegrationAuthStatus> integrationOAuthStatus(String attemptID) async {
    oauthStatusCalls++;
    return oauthStatus;
  }

  @override
  Future<void> completeIntegrationOAuth(
    String attemptID, {
    String? code,
  }) async {
    oauthCompleteCalls++;
    oauthCompletionCode = code;
  }

  @override
  Future<void> cancelIntegrationOAuth(String attemptID) async {
    oauthCancelCalls++;
  }

  @override
  Future<void> refreshProviderRuntime() async {
    providerRefreshCalls++;
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

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
    return readyRepository.future;
  }
}

Future<ConnectionController> _controller(ProductRepository repository) async {
  SharedPreferences.setMockInitialValues({});
  final preferences = await SharedPreferences.getInstance();
  return ConnectionController(ProfileStore(prefs: preferences))
    ..repository = repository
    ..status = StreamStatus.connected;
}

Widget _app(
  ConnectionController controller, {
  Future<bool> Function(Uri destination)? authorizationLauncher,
}) => MaterialApp(
  home: IntegrationsScreen(
    controller: controller,
    authorizationLauncher: authorizationLauncher,
  ),
);

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

  testWidgets('empty MCP state opens persistent native setup', (tester) async {
    final controller = await _controller(_IntegrationsRepository());
    addTearDown(controller.dispose);

    await tester.pumpWidget(_app(controller));
    await tester.pumpAndSettle();

    expect(find.text('No MCP servers configured'), findsOneWidget);
    expect(find.byKey(const ValueKey('add-mcp-server')), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('add-mcp-server')));
    await tester.pumpAndSettle();

    expect(find.byType(McpSetupScreen), findsOneWidget);
    expect(find.text('Persisted configuration'), findsOneWidget);
  });

  testWidgets('provider aliases retain separate regional connection states', (
    tester,
  ) async {
    final repository = _IntegrationsRepository()
      ..integrations = const [
        IntegrationInfo(
          id: 'zai-coding-plan',
          name: 'Z.AI Coding Plan',
          methods: [IntegrationMethodInfo(type: 'key', label: 'API key')],
          connectionCount: 0,
        ),
        IntegrationInfo(
          id: 'zhipuai-coding-plan',
          name: 'Zhipu AI Coding Plan',
          methods: [IntegrationMethodInfo(type: 'key', label: 'API key')],
          connectionCount: 1,
        ),
      ];
    final controller = await _controller(repository);
    addTearDown(controller.dispose);

    await tester.pumpWidget(_app(controller));
    await tester.pumpAndSettle();

    expect(find.text('Z.AI Coding Plan · Global'), findsOneWidget);
    expect(find.text('Z.AI Coding Plan · China'), findsOneWidget);
    expect(find.text('Zhipu AI Coding Plan'), findsNothing);
    expect(find.text('Connected\n(server-managed)'), findsOneWidget);
    expect(find.text('Connect'), findsOneWidget);
  });

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

  testWidgets('MCP actions wait for the wake-time replacement repository', (
    tester,
  ) async {
    final retainedRepository = _IntegrationsRepository()
      ..servers = const [
        McpServerInfo(name: 'remote-tools', status: 'disabled'),
      ];
    final replacementRepository = _IntegrationsRepository()
      ..servers = const [
        McpServerInfo(name: 'remote-tools', status: 'connected'),
      ];
    SharedPreferences.setMockInitialValues({});
    final preferences = await SharedPreferences.getInstance();
    final readyRepository = Completer<ProductRepository?>();
    final controller =
        _SwitchingRepositoryController(
            ProfileStore(prefs: preferences),
            retainedRepository,
            readyRepository,
          )
          ..repository = retainedRepository
          ..status = StreamStatus.connected;
    addTearDown(controller.dispose);

    await tester.pumpWidget(_app(controller));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Connect').first);
    await tester.pump();

    expect(retainedRepository.mcpConnectCalls, 0);
    expect(replacementRepository.mcpConnectCalls, 0);

    readyRepository.complete(replacementRepository);
    await tester.pumpAndSettle();

    expect(retainedRepository.mcpConnectCalls, 0);
    expect(replacementRepository.mcpConnectCalls, 1);
    expect(find.text('Connected and tools are available'), findsOneWidget);
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
      expect(repository.oauthCancelCalls, 1);
      expect(
        find.byKey(const ValueKey('pending-provider-oauth')),
        findsNothing,
      );
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
      expect(repository.oauthCancelCalls, 1);
    },
  );

  testWidgets(
    'automatic OAuth stays visible and checks server attempt status',
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
              ),
            ],
            connectionCount: 0,
          ),
        ];

      await tester.pumpWidget(
        _app(
          await _controller(repository),
          authorizationLauncher: (_) async => true,
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('Connect'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Open browser'));
      await tester.pumpAndSettle();

      expect(
        find.byKey(const ValueKey('pending-provider-oauth')),
        findsOneWidget,
      );
      expect(find.text('Connecting Cloud Provider'), findsOneWidget);
      await tester.tap(find.text('Check'));
      await tester.pumpAndSettle();

      expect(repository.oauthStatusCalls, 1);
      expect(
        find.byKey(const ValueKey('pending-provider-oauth')),
        findsOneWidget,
      );
    },
  );

  testWidgets('code OAuth completes, refreshes models, and clears its state', (
    tester,
  ) async {
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
            ),
          ],
          connectionCount: 0,
        ),
      ]
      ..oauthLaunch = const IntegrationAuthLaunch(
        attemptID: 'attempt-code',
        url: 'https://provider-auth.example.com/authorize',
        instructions: 'Paste the browser code',
        mode: IntegrationAuthMode.code,
      )
      ..oauthStatus = const IntegrationAuthStatus(
        state: IntegrationAuthState.complete,
      );

    await tester.pumpWidget(
      _app(
        await _controller(repository),
        authorizationLauncher: (_) async => true,
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('Connect'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Open browser'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Enter code'));
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const ValueKey('oauth-completion-code')),
      'returned-code',
    );
    await tester.tap(find.text('Complete'));
    await tester.pumpAndSettle();

    expect(repository.oauthCompleteCalls, 1);
    expect(repository.oauthCompletionCode, 'returned-code');
    expect(repository.oauthStatusCalls, 1);
    expect(repository.providerRefreshCalls, 1);
    expect(find.byKey(const ValueKey('pending-provider-oauth')), findsNothing);
    expect(find.text('Cloud Provider is connected'), findsOneWidget);
  });
}
