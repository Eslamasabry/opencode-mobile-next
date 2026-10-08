// Providers screen behaviour: connect, disconnect, OAuth, MCP, and the
// API-key-led sign-in for Anthropic and Google.
//
// Regenerate deliberately, and look at every changed image before committing it:
//   flutter test --update-goldens --dart-define=CAPTURE_EVIDENCE=true \
//     test/library_integrations_test.dart --plain-name "evidence"

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/library_screen.dart';
import 'package:opencode_mobile/ui/screens/mcp_catalog_screen.dart';
import 'package:opencode_mobile/ui/screens/mcp_setup_screen.dart';
import 'package:opencode_mobile/ui/widgets/connect_methods.dart';
import 'package:opencode_mobile/ui/widgets/provider_logo.dart';

import '../tool/capture/fixtures.dart' show captureTheme, loadCaptureFonts;
import 'support/library_integrations_fixtures.dart';

const _evidence = bool.fromEnvironment('CAPTURE_EVIDENCE');

/// Connected providers expose account actions in their row menu.
Future<void> _openProviderDisconnect(WidgetTester tester) async {
  final row = find.byKey(const ValueKey('provider-cloud'));
  await tester.ensureVisible(row);
  await tester.tap(row);
  await tester.pumpAndSettle();
  await tester.tap(find.byKey(const ValueKey('disconnect-provider-cloud')));
  await tester.pumpAndSettle();
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

  for (final (id, name, host) in const [
    ('anthropic', 'Anthropic', 'console.anthropic.com'),
    ('google', 'Google', 'aistudio.google.com'),
  ]) {
    testWidgets('$name leads with an API key: no browser sign-in, a link to '
        'the key page, no key echoed', (tester) async {
      final repository = IntegrationsRepository()
        ..integrations = [
          IntegrationInfo(
            id: id,
            name: name,
            methods: const [
              IntegrationMethodInfo(
                type: 'oauth',
                id: 'oauth-1',
                label: 'Browser sign-in',
              ),
              IntegrationMethodInfo(type: 'key', label: 'API key'),
            ],
            connectionCount: 0,
          ),
        ];
      final controller = await integrationsController(repository);
      addTearDown(controller.dispose);
      await tester.pumpWidget(app(controller));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(ValueKey('connect-provider-$id')));
      await tester.pumpAndSettle();

      // Straight to the key dialog: no method sheet, no OAuth call.
      expect(find.byKey(const ValueKey('connect-method-sheet')), findsNothing);
      expect(find.byKey(const ValueKey('provider-key-field')), findsOneWidget);
      expect(
        find.textContaining(
          'does not allow browser sign-in',
          findRichText: true,
        ),
        findsOneWidget,
      );
      expect(repository.oauthCalls, 0);

      await tester.tap(find.text('Get a key from $name'));
      await tester.pumpAndSettle();
      expect(find.text('Open external link?'), findsOneWidget);
      expect(find.textContaining(host, findRichText: true), findsWidgets);
      await tester.tap(
        find.descendant(
          of: find.byKey(const ValueKey('external-link-confirm')),
          matching: find.text('Cancel'),
        ),
      );
      await tester.pumpAndSettle();
      // Back in the key dialog after the link's confirmation.
      expect(find.byKey(const ValueKey('provider-key-field')), findsOneWidget);

      await tester.enterText(
        find.byKey(const ValueKey('provider-key-field')),
        'sk-secret-value-123',
      );
      await tester.tap(find.byKey(const ValueKey('confirm-provider-key')));
      await tester.pumpAndSettle();
      expect(repository.savedKeys, [id]);
      expect(find.textContaining('sk-secret-value-123'), findsNothing);
      // Saved, but the catalog has no model of it: not called ready.
      expect(
        find.text('$name key saved. The server has not loaded it yet.'),
        findsOneWidget,
      );
      expect(tester.takeException(), isNull);
    });
  }

  testWidgets('a key-only provider without a key method keeps what the '
      'server offers', (tester) async {
    final repository = IntegrationsRepository()
      ..integrations = const [
        IntegrationInfo(
          id: 'anthropic',
          name: 'Anthropic',
          methods: [
            IntegrationMethodInfo(
              type: 'oauth',
              id: 'oauth-1',
              label: 'Account sign-in',
            ),
          ],
          connectionCount: 0,
        ),
      ];
    final controller = await integrationsController(repository);
    addTearDown(controller.dispose);
    await tester.pumpWidget(app(controller));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('connect-provider-anthropic')));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('provider-key-field')), findsNothing);
  });

  testWidgets('another provider keeps its browser sign-in choice', (
    tester,
  ) async {
    final repository = IntegrationsRepository()
      ..integrations = const [
        IntegrationInfo(
          id: 'cloud',
          name: 'Cloud Provider',
          methods: [
            IntegrationMethodInfo(
              type: 'oauth',
              id: 'oauth-1',
              label: 'Account sign-in',
            ),
            IntegrationMethodInfo(type: 'key', label: 'API key'),
          ],
          connectionCount: 0,
        ),
      ];
    final controller = await integrationsController(repository);
    addTearDown(controller.dispose);
    await tester.pumpWidget(app(controller));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('connect-provider-cloud')));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('connect-method-sheet')), findsOneWidget);
  });

  test('key page links are official https pages for key-only providers', () {
    expect(providerKeyPageUrl('anthropic'), startsWith('https://'));
    expect(providerKeyPageUrl('google'), startsWith('https://'));
    expect(providerKeyPageUrl('groq'), isNull);
  });

  // Evidence only (docs/qa/slice-api-key-signin-2026-09-29):
  //   flutter test --update-goldens --dart-define=CAPTURE_EVIDENCE=true \
  //     test/library_integrations_test.dart --plain-name "evidence"
  for (final (size, light) in const [
    (Size(412, 915), false),
    (Size(1280, 800), true),
  ]) {
    testWidgets('evidence · ${size.width.toInt()}', skip: !_evidence, (
      tester,
    ) async {
      await loadCaptureFonts();
      tester.view.physicalSize = size;
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final repository = IntegrationsRepository()
        ..integrations = const [
          IntegrationInfo(
            id: 'anthropic',
            name: 'Anthropic',
            methods: [
              IntegrationMethodInfo(
                type: 'oauth',
                id: 'oauth-1',
                label: 'Browser sign-in',
              ),
              IntegrationMethodInfo(type: 'key', label: 'API key'),
            ],
            connectionCount: 0,
          ),
        ];
      final controller = await integrationsController(repository);
      addTearDown(controller.dispose);
      debugDefaultTargetPlatformOverride = TargetPlatform.android; // ARCH-11
      try {
        await tester.pumpWidget(
          MaterialApp(
            theme: captureTheme(light: light),
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            home: IntegrationsScreen(controller: controller),
          ),
        );
        await tester.pumpAndSettle();
        await tester.tap(
          find.byKey(const ValueKey('connect-provider-anthropic')),
        );
        await tester.pumpAndSettle();
        await expectLater(
          find.byType(MaterialApp),
          matchesGoldenFile(
            '../docs/qa/slice-api-key-signin-2026-09-29/'
            'anthropic_key_${size.width.toInt()}_${light ? 'light' : 'dark'}.png',
          ),
        );
        await tester.pumpWidget(const SizedBox.shrink());
      } finally {
        debugDefaultTargetPlatformOverride = null;
      }
    });
  }

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

  test('provider OAuth completion accepts a code or callback URL', () {
    expect(providerOAuthCompletionCode(' returned-code '), 'returned-code');
    expect(
      providerOAuthCompletionCode(
        'https://auth.example.com/callback?code=returned-code&state=state-1',
      ),
      'returned-code',
    );
  });

  testWidgets(
    'provider integrations remain available when MCP resources fail',
    (tester) async {
      final repository = IntegrationsRepository()
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

      await tester.pumpWidget(app(await integrationsController(repository)));
      await tester.pumpAndSettle();

      expect(find.text('GitHub'), findsOneWidget);
      await tester.scrollUntilVisible(
        find.text('Could not refresh MCP data. Try again.'),
        200,
        scrollable: find.byType(Scrollable).first,
      );
      expect(
        find.text('Could not refresh MCP data. Try again.'),
        findsOneWidget,
      );
      expect(find.text('Resources unavailable'), findsNothing);
      expect(find.text('Could not load this section'), findsOneWidget);
    },
  );

  testWidgets('empty MCP state opens persistent native setup', (tester) async {
    final controller = await integrationsController(IntegrationsRepository());
    addTearDown(controller.dispose);

    await tester.pumpWidget(app(controller));
    await tester.pumpAndSettle();

    expect(find.text('No MCP servers configured'), findsOneWidget);
    expect(find.byKey(const ValueKey('add-mcp-server')), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('add-mcp-server')));
    await tester.pumpAndSettle();
    // P2.4: Add opens the add sheet; Enter manually is the form.
    expect(find.byKey(const ValueKey('mcp-add-sheet')), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('mcp-add-manual')));
    await tester.pumpAndSettle();

    expect(find.byType(McpSetupScreen), findsOneWidget);
    // A persistent write offers where to save it (this project or all).
    expect(find.byKey(const ValueKey('mcp-scope')), findsOneWidget);
  });

  testWidgets('Add › Browse the catalogue opens the MCP catalogue', (
    tester,
  ) async {
    final controller = await integrationsController(IntegrationsRepository());
    addTearDown(controller.dispose);

    await tester.pumpWidget(app(controller));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('add-mcp-server')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('mcp-add-catalog')));
    await tester.pumpAndSettle();

    expect(find.byType(McpCatalogScreen), findsOneWidget);
    // Nothing is fetched until the person agrees.
    expect(find.byKey(const ValueKey('mcp-catalog-consent')), findsOneWidget);
  });

  testWidgets(
    'custom providers from opencode.json appear as configured on the server',
    (tester) async {
      final repository = IntegrationsRepository()
        ..integrations = const [
          IntegrationInfo(
            id: 'anthropic',
            name: 'Anthropic',
            methods: [IntegrationMethodInfo(type: 'key', label: 'API key')],
            connectionCount: 0,
          ),
        ];
      final controller = await integrationsController(repository);
      addTearDown(controller.dispose);
      CatalogModel model(String id, String providerID) => CatalogModel(
        id: id,
        providerID: providerID,
        name: id,
        enabled: true,
        status: 'active',
        contextLimit: 128000,
        outputLimit: 8192,
        reasoning: false,
        attachments: false,
        tools: true,
        variants: const [],
      );
      // The integrations list only knows providers with a connection
      // method; the catalog (v1 /config/providers) also carries the custom
      // provider declared in opencode.json, source "config".
      controller.catalog = CatalogSnapshot(
        providers: const [
          CatalogProvider(id: 'anthropic', name: 'Anthropic', enabled: true),
          CatalogProvider(id: 'my-llm', name: 'My LLM', enabled: true),
          CatalogProvider(id: 'off', name: 'Disabled', enabled: false),
        ],
        models: [
          model('claude', 'anthropic'),
          model('local-a', 'my-llm'),
          model('local-b', 'my-llm'),
        ],
        agents: const [],
      );

      await tester.pumpWidget(app(controller));
      await tester.pumpAndSettle();

      expect(find.text('My LLM'), findsOneWidget);
      expect(
        find.textContaining('Server-managed', findRichText: true),
        findsOneWidget,
      );
      expect(
        find.textContaining('2 models', findRichText: true),
        findsOneWidget,
      );
      expect(
        find.byKey(const ValueKey('connect-provider-my-llm')),
        findsNothing,
      );
      expect(
        find.byKey(const ValueKey('disconnect-provider-my-llm')),
        findsNothing,
      );
      // The listed provider keeps its own Connect action; the disabled
      // catalog entry is not a provider the server can use.
      expect(
        find.byKey(const ValueKey('connect-provider-anthropic')),
        findsOneWidget,
      );
      expect(find.text('Disabled'), findsNothing);
      // R18 removed the aggregate count; each row carries its own state.
      expect(find.text('1 connected · 1 available'), findsNothing);
    },
  );

  testWidgets('provider aliases retain separate regional connection states', (
    tester,
  ) async {
    final repository = IntegrationsRepository()
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
    final controller = await integrationsController(repository);
    addTearDown(controller.dispose);

    await tester.pumpWidget(app(controller));
    await tester.pumpAndSettle();

    expect(find.text('Z.AI Coding Plan · Global'), findsOneWidget);
    expect(find.text('Z.AI Coding Plan · China'), findsOneWidget);
    expect(find.text('Zhipu AI Coding Plan'), findsNothing);
    expect(
      find.textContaining('Server-managed', findRichText: true),
      findsOneWidget,
    );
    expect(
      find.text('Connected · Server-managed', findRichText: true),
      findsOneWidget,
    );
    expect(
      find.textContaining('Not connected', findRichText: true),
      findsOneWidget,
    );
    expect(
      find.byKey(const ValueKey('connect-provider-zai-coding-plan')),
      findsOneWidget,
    );
    expect(find.text('1 connected · 1 available'), findsNothing);
    // Connected providers lead the list regardless of alias order.
    expect(
      tester.getTopLeft(find.text('Z.AI Coding Plan · China')).dy,
      lessThan(tester.getTopLeft(find.text('Z.AI Coding Plan · Global')).dy),
    );
  });

  testWidgets(
    'stored provider credential requires confirmation before disconnect',
    (tester) async {
      final repository = IntegrationsRepository()
        ..integrations = const [
          IntegrationInfo(
            id: 'cloud',
            name: 'Cloud Provider',
            methods: [IntegrationMethodInfo(type: 'key', label: 'API key')],
            connections: [
              IntegrationConnectionInfo(
                type: 'credential',
                id: 'credential-1',
                label: 'Personal key',
              ),
            ],
            connectionCount: 1,
          ),
        ];
      final controller = await integrationsController(repository);
      addTearDown(controller.dispose);

      await tester.pumpWidget(app(controller));
      await tester.pumpAndSettle();

      expect(
        find.textContaining(
          'Stored credential: Personal key',
          findRichText: true,
        ),
        findsOneWidget,
      );
      await _openProviderDisconnect(tester);

      expect(find.text('Disconnect Cloud Provider?'), findsOneWidget);
      expect(
        find.textContaining('A reply already running finishes first'),
        findsOneWidget,
      );
      await tester.tap(find.text('Cancel'));
      await tester.pumpAndSettle();
      expect(repository.providerDisconnectCalls, 0);

      await _openProviderDisconnect(tester);
      await tester.tap(
        find.byKey(const ValueKey('confirm-provider-disconnect')),
      );
      await tester.pumpAndSettle();

      expect(repository.providerDisconnectCalls, 1);
      expect(repository.disconnectedIntegration?.id, 'cloud');
      expect(repository.disconnectedIntegration?.credentialIDs, [
        'credential-1',
      ]);
      expect(find.text('Cloud Provider disconnected'), findsOneWidget);
      expect(
        find.byKey(const ValueKey('connect-provider-cloud')),
        findsOneWidget,
      );
    },
  );

  testWidgets('legacy OAuth provider can be disconnected from mobile', (
    tester,
  ) async {
    final repository = IntegrationsRepository()
      ..integrations = const [
        IntegrationInfo(
          id: 'cloud',
          name: 'Cloud Provider',
          methods: [
            IntegrationMethodInfo(type: 'oauth', id: '0', label: 'Cloud OAuth'),
          ],
          connections: [
            IntegrationConnectionInfo(
              type: 'runtime',
              label: 'Connected to OpenCode',
            ),
          ],
          connectionCount: 1,
        ),
      ];
    final controller = await integrationsController(repository);
    addTearDown(controller.dispose);

    await tester.pumpWidget(app(controller));
    await tester.pumpAndSettle();

    await _openProviderDisconnect(tester);
    await tester.tap(find.byKey(const ValueKey('confirm-provider-disconnect')));
    await tester.pumpAndSettle();

    expect(repository.providerDisconnectCalls, 1);
    expect(repository.disconnectedIntegration?.credentialIDs, isEmpty);
    expect(find.text('Cloud Provider disconnected'), findsOneWidget);
    expect(
      find.byKey(const ValueKey('connect-provider-cloud')),
      findsOneWidget,
    );
  });

  testWidgets(
    'environment provider explains that mobile cannot disconnect it',
    (tester) async {
      final repository = IntegrationsRepository()
        ..integrations = const [
          IntegrationInfo(
            id: 'environment-provider',
            name: 'Environment Provider',
            methods: [
              IntegrationMethodInfo(
                type: 'env',
                label: 'Server environment',
                environmentNames: ['PROVIDER_TOKEN'],
              ),
            ],
            connections: [
              IntegrationConnectionInfo(type: 'env', label: 'PROVIDER_TOKEN'),
            ],
            connectionCount: 1,
          ),
        ];
      final controller = await integrationsController(repository);
      addTearDown(controller.dispose);

      await tester.pumpWidget(app(controller));
      await tester.pumpAndSettle();

      expect(
        find.textContaining('Server environment', findRichText: true),
        findsOneWidget,
      );
      expect(
        find.byKey(const ValueKey('provider-environment-provider')),
        findsOneWidget,
      );
      expect(
        tester
            .widget<KitRow>(
              find.byKey(const ValueKey('provider-environment-provider')),
            )
            .menu
            .where(
              (item) =>
                  item.key ==
                  const ValueKey('disconnect-provider-environment-provider'),
            ),
        isEmpty,
      );
      expect(repository.providerDisconnectCalls, 0);
    },
  );

  // Emulator QA B10: rows read "Not connected · API key · Server
  // environment: 302AI_API_KEY". The line says how to connect; the variable
  // name is under the row's Details.
  testWidgets('an unconnected provider says how to connect; env names are '
      'under Details', (tester) async {
    final repository = IntegrationsRepository()
      ..integrations = const [
        IntegrationInfo(
          id: '302ai',
          name: '302.AI',
          methods: [
            IntegrationMethodInfo(type: 'key', label: 'API key'),
            IntegrationMethodInfo(
              type: 'env',
              label: 'Server environment',
              environmentNames: ['302AI_API_KEY'],
            ),
          ],
          connectionCount: 0,
        ),
        IntegrationInfo(
          id: 'onlyenv',
          name: 'Only Env',
          methods: [
            IntegrationMethodInfo(
              type: 'env',
              label: 'Server environment',
              environmentNames: ['ONLY_ENV_KEY'],
            ),
          ],
          connectionCount: 0,
        ),
      ];
    final controller = await integrationsController(repository);
    addTearDown(controller.dispose);

    await tester.pumpWidget(app(controller));
    await tester.pumpAndSettle();

    expect(find.textContaining('_API_KEY', findRichText: true), findsNothing);
    expect(
      find.textContaining('ONLY_ENV_KEY', findRichText: true),
      findsNothing,
    );
    expect(
      find.text('Not connected · Add an API key', findRichText: true),
      findsOneWidget,
    );
    expect(
      find.text('Not connected · Set up on the server', findRichText: true),
      findsOneWidget,
    );

    final row = find.byKey(const ValueKey('connect-provider-302ai'));
    await tester.ensureVisible(row);
    await tester.longPress(row);
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('provider-details-302ai')));
    await tester.pumpAndSettle();
    expect(
      find.byKey(const ValueKey('provider-details-sheet-302ai')),
      findsOneWidget,
    );
    expect(find.text('302.AI details'), findsOneWidget);
    expect(find.text('302AI_API_KEY'), findsOneWidget);
    expect(
      find.textContaining('set this where the server runs'),
      findsOneWidget,
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('legacy OAuth can be removed while environment stays active', (
    tester,
  ) async {
    final repository = IntegrationsRepository()
      ..integrations = const [
        IntegrationInfo(
          id: 'cloud',
          name: 'Cloud Provider',
          methods: [
            IntegrationMethodInfo(type: 'oauth', id: '0', label: 'Cloud OAuth'),
            IntegrationMethodInfo(
              type: 'env',
              label: 'Server environment',
              environmentNames: ['CLOUD_TOKEN'],
            ),
          ],
          connections: [
            IntegrationConnectionInfo(type: 'env', label: 'CLOUD_TOKEN'),
          ],
          connectionCount: 1,
        ),
      ];
    final controller = await integrationsController(repository);
    addTearDown(controller.dispose);

    await tester.pumpWidget(app(controller));
    await tester.pumpAndSettle();
    await _openProviderDisconnect(tester);
    expect(
      find.textContaining('server environment, which mobile cannot remove'),
      findsOneWidget,
    );
    await tester.tap(find.byKey(const ValueKey('confirm-provider-disconnect')));
    await tester.pumpAndSettle();

    expect(repository.providerDisconnectCalls, 1);
    expect(
      find.text(
        'Cloud Provider credential removed; server environment remains active',
      ),
      findsOneWidget,
    );
    expect(
      find.textContaining('Server environment', findRichText: true),
      findsOneWidget,
    );
    await tester.tap(find.byKey(const ValueKey('provider-cloud')));
    await tester.pumpAndSettle();
    expect(
      find.byKey(const ValueKey('disconnect-provider-cloud')),
      findsOneWidget,
    );
  });

  testWidgets('failed provider disconnect keeps its action visible for retry', (
    tester,
  ) async {
    final repository = IntegrationsRepository()
      ..providerDisconnectError = const ProductException(
        'The connection remains visible so you can retry.',
      )
      ..integrations = const [
        IntegrationInfo(
          id: 'cloud',
          name: 'Cloud Provider',
          methods: [IntegrationMethodInfo(type: 'key', label: 'API key')],
          connections: [
            IntegrationConnectionInfo(
              type: 'credential',
              id: 'credential-1',
              label: 'Personal key',
            ),
          ],
          connectionCount: 1,
        ),
      ];
    final controller = await integrationsController(repository);
    addTearDown(controller.dispose);

    await tester.pumpWidget(app(controller));
    await tester.pumpAndSettle();
    await _openProviderDisconnect(tester);
    await tester.tap(find.byKey(const ValueKey('confirm-provider-disconnect')));
    await tester.pumpAndSettle();

    expect(repository.providerDisconnectCalls, 1);
    expect(
      find.text(
        'Could not confirm authentication. Return to the original source and try again.',
      ),
      findsOneWidget,
    );
    await _openProviderDisconnect(tester);
    expect(
      find.byKey(const ValueKey('confirm-provider-disconnect')),
      findsOneWidget,
    );
    expect(repository.providerDisconnectCalls, 1);
  });

  testWidgets(
    'provider disconnect confirmation fits a compact large-text phone',
    (tester) async {
      tester.view.physicalSize = const Size(320, 480);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final repository = IntegrationsRepository()
        ..integrations = const [
          IntegrationInfo(
            id: 'cloud',
            name: 'Cloud Provider',
            methods: [IntegrationMethodInfo(type: 'key', label: 'API key')],
            connections: [
              IntegrationConnectionInfo(
                type: 'credential',
                id: 'credential-1',
                label: 'Personal key',
              ),
            ],
            connectionCount: 1,
          ),
        ];
      final controller = await integrationsController(repository);
      addTearDown(controller.dispose);

      await tester.pumpWidget(app(controller, textScale: 2));
      await tester.pumpAndSettle();
      await _openProviderDisconnect(tester);

      expect(find.text('Disconnect Cloud Provider?'), findsOneWidget);
      expect(find.text('Cancel'), findsOneWidget);
      expect(find.text('Disconnect Cloud Provider'), findsOneWidget);
      expect(tester.takeException(), isNull);
    },
  );
}
