// Providers screen behaviour: provider OAuth (code and automatic), provider
// search and the OAuth retry (split from library_integrations_test.dart).

import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/widgets/provider_logo.dart';

import 'support/library_integrations_fixtures.dart';

/// Opens the waiting sign-in's row (the sign-in folded into the provider
/// list) and taps its one primary, "Finish signing in to {name}".
Future<void> _finishSignIn(WidgetTester tester, String name) async {
  await tester.tap(find.byKey(const ValueKey('pending-provider-oauth')));
  await tester.pumpAndSettle();
  await tester.tap(find.text('Finish signing in to $name'));
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

  testWidgets(
    'OAuth rejects blank required text while allowing blank optional text',
    (tester) async {
      final repository = IntegrationsRepository()
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

      await tester.pumpWidget(app(await integrationsController(repository)));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('connect-provider-cloud')));
      await tester.pumpAndSettle();

      await tester.enterText(
        find.byKey(const ValueKey('oauth-prompt-tenant')),
        '   ',
      );
      await tester.tap(find.byKey(const ValueKey('oauth-inputs-continue')));
      await tester.pump();

      expect(find.text('Enter a value'), findsOneWidget);
      expect(repository.oauthCalls, 0);

      await tester.enterText(
        find.byKey(const ValueKey('oauth-prompt-tenant')),
        'acme',
      );
      await tester.tap(find.byKey(const ValueKey('oauth-inputs-continue')));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 300));

      expect(repository.oauthCalls, 1);
      expect(repository.oauthInputs, {'tenant': 'acme', 'label': ''});
      expect(
        find.byKey(const ValueKey('authorization-launch-sheet')),
        findsOneWidget,
      );

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
      final repository = IntegrationsRepository()
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

      await tester.pumpWidget(app(await integrationsController(repository)));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('connect-provider-cloud')));
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
      await tester.tap(find.byKey(const ValueKey('oauth-inputs-continue')));
      await tester.pump();

      expect(find.text('Select an option'), findsOneWidget);
      expect(repository.oauthCalls, 0);

      await tester.tap(find.text('Production').last);
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
      await tester.tap(find.byKey(const ValueKey('oauth-inputs-continue')));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 300));

      expect(repository.oauthCalls, 1);
      expect(repository.oauthInputs, {
        'mode': 'basic',
        'note': 'visible value',
      });
      expect(
        find.text('Sign in at provider-auth.example.com?'),
        findsOneWidget,
      );
      expect(
        find.byKey(const ValueKey('authorization-launch-sheet')),
        findsOneWidget,
      );

      await tester.tap(find.text('Cancel'));
      await tester.pumpAndSettle();
      expect(repository.oauthCancelCalls, 1);
    },
  );

  testWidgets(
    'automatic OAuth stays visible and checks server attempt status',
    (tester) async {
      final repository = IntegrationsRepository()
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
          attemptID: 'attempt-device',
          url: 'https://provider-auth.example.com/device',
          instructions: 'Enter code: ABCD-EFGH',
          mode: IntegrationAuthMode.auto,
        );

      await tester.pumpWidget(
        app(
          await integrationsController(repository),
          authorizationLauncher: (_) async => true,
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('connect-provider-cloud')));
      await tester.pumpAndSettle();
      expect(find.textContaining('The server says:'), findsOneWidget);
      expect(
        find.descendant(
          of: find.byKey(const ValueKey('authorization-launch-sheet')),
          matching: find.textContaining('Enter code: ABCD-EFGH'),
        ),
        findsOneWidget,
      );
      await tester.tap(find.text('Open browser'));
      await tester.pump(const Duration(milliseconds: 300));
      expect(find.text('Open external link?'), findsOneWidget);
      // The confirmation slides in; let it land before tapping.
      await tester.pump(const Duration(milliseconds: 300));
      await tester.tap(find.text('Open link'));
      await tester.pumpAndSettle();

      // The sign-in is the provider's own row, marked and worded; no card.
      final row = find.byKey(const ValueKey('pending-provider-oauth'));
      expect(row, findsOneWidget);
      expect(
        find.descendant(
          of: row,
          matching: find.text('Sign-in waiting', findRichText: true),
        ),
        findsOneWidget,
      );
      expect(find.text('Connecting Cloud Provider'), findsNothing);
      await _finishSignIn(tester, 'Cloud Provider');
      await tester.pumpAndSettle();

      expect(repository.oauthStatusCalls, 1);
      expect(
        find.byKey(const ValueKey('pending-provider-oauth')),
        findsOneWidget,
      );
    },
  );

  testWidgets('automatic OAuth cannot be cancelled during its callback', (
    tester,
  ) async {
    final statusCompleter = Completer<IntegrationAuthStatus>();
    final repository = IntegrationsRepository()
      ..oauthStatusCompleter = statusCompleter
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
    final controller = await integrationsController(repository);
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      app(controller, authorizationLauncher: (_) async => true),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('connect-provider-cloud')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Open browser'));
    await tester.pump(const Duration(milliseconds: 300));
    expect(find.text('Open external link?'), findsOneWidget);
    // The confirmation slides in; let it land before tapping.
    await tester.pump(const Duration(milliseconds: 300));
    await tester.tap(find.text('Open link'));
    await tester.pumpAndSettle();
    await _finishSignIn(tester, 'Cloud Provider');
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 500));

    // While the check runs, the row opens nothing: no Cancel mid-callback.
    final pendingRow = find.byKey(const ValueKey('pending-provider-oauth'));
    expect(tester.widget<KitRow>(pendingRow).onTap, isNull);
    await tester.tapAt(tester.getCenter(pendingRow));
    await tester.pump(const Duration(milliseconds: 500));
    expect(find.byKey(const ValueKey('sign-in-sheet')), findsNothing);

    statusCompleter.complete(
      const IntegrationAuthStatus(state: IntegrationAuthState.pending),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('pending-provider-oauth')));
    await tester.pumpAndSettle();
    expect(find.text('Cancel Cloud Provider sign-in'), findsOneWidget);
  });

  testWidgets('code OAuth completes, refreshes models, and clears its state', (
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
      app(
        await integrationsController(repository),
        authorizationLauncher: (_) async => true,
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('connect-provider-cloud')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Open browser'));
    await tester.pump(const Duration(milliseconds: 300));
    expect(find.text('Open external link?'), findsOneWidget);
    // The confirmation slides in; let it land before tapping.
    await tester.pump(const Duration(milliseconds: 300));
    await tester.tap(find.text('Open link'));
    await tester.pumpAndSettle();
    await _finishSignIn(tester, 'Cloud Provider');
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const ValueKey('oauth-completion-code')),
      'https://provider-auth.example.com/callback?code=returned-code&state=state-1',
    );
    await tester.tap(find.widgetWithText(KitButton, 'Finish signing in'));
    await tester.pumpAndSettle();

    expect(repository.oauthCompleteCalls, 1);
    expect(repository.oauthCompletionCode, 'returned-code');
    expect(repository.oauthStatusCalls, 1);
    expect(repository.providerRefreshCalls, 1);
    expect(find.byKey(const ValueKey('pending-provider-oauth')), findsNothing);
    expect(find.text('Cloud Provider is connected'), findsOneWidget);
  });

  testWidgets('provider search filters by name, id, model, and alias', (
    tester,
  ) async {
    const key = [IntegrationMethodInfo(type: 'key', label: 'API key')];
    final repository = IntegrationsRepository()
      ..integrations = const [
        IntegrationInfo(
          id: 'anthropic',
          name: 'Anthropic',
          methods: key,
          connectionCount: 0,
        ),
        IntegrationInfo(
          id: 'openai',
          name: 'OpenAI',
          methods: key,
          connectionCount: 1,
        ),
        IntegrationInfo(
          id: 'zai',
          name: 'Zhipu AI',
          methods: key,
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
    controller.catalog = CatalogSnapshot(
      providers: const [
        CatalogProvider(id: 'anthropic', name: 'Anthropic', enabled: true),
        CatalogProvider(id: 'openai', name: 'OpenAI', enabled: true),
        CatalogProvider(id: 'zai', name: 'Zhipu AI', enabled: true),
      ],
      models: [model('claude-sonnet-4', 'anthropic'), model('gpt-5', 'openai')],
      agents: const [],
    );

    await tester.pumpWidget(app(controller));
    await tester.pumpAndSettle();

    final search = find.byKey(const ValueKey('providers-search'));
    expect(search, findsOneWidget);
    expect(find.text('Anthropic'), findsOneWidget);
    expect(find.text('OpenAI'), findsOneWidget);
    expect(find.text('Z.AI · Global'), findsOneWidget);
    expect(find.byKey(const ValueKey('providers-search-clear')), findsNothing);

    // Case-insensitive on the presented name.
    await tester.enterText(search, 'ANTH');
    await tester.pump(KitMotion.typingSettle);
    await tester.pumpAndSettle();
    expect(find.text('Anthropic'), findsOneWidget);
    expect(find.text('OpenAI'), findsNothing);
    expect(find.text('Z.AI · Global'), findsNothing);
    expect(
      find.byKey(const ValueKey('providers-search-clear')),
      findsOneWidget,
    );

    // A model id the provider serves.
    await tester.enterText(search, 'gpt');
    await tester.pump(KitMotion.typingSettle);
    await tester.pumpAndSettle();
    expect(find.text('OpenAI'), findsOneWidget);
    expect(find.text('Anthropic'), findsNothing);

    // A consolidated alias: the China route id finds the Z.AI family.
    await tester.enterText(search, 'zhipuai');
    await tester.pump(KitMotion.typingSettle);
    await tester.pumpAndSettle();
    expect(find.text('Z.AI · Global'), findsOneWidget);
    expect(find.text('OpenAI'), findsNothing);
    expect(find.text('Anthropic'), findsNothing);

    // The explained section label survives filtering; R18 removes counts.
    expect(find.text('Providers'), findsOneWidget);
    expect(find.text('1 connected · 2 available'), findsNothing);

    // Nothing matches: a small empty state with a Clear search action.
    await tester.enterText(search, 'no-such-provider');
    await tester.pump(KitMotion.typingSettle);
    await tester.pumpAndSettle();
    expect(
      find.byKey(const ValueKey('providers-search-empty')),
      findsOneWidget,
    );
    expect(
      find.text(
        'Nothing in ${KitBidi.auto('Providers')} matches “${KitBidi.auto('no-such-provider')}”',
      ),
      findsOneWidget,
    );
    expect(find.text('Anthropic'), findsNothing);
    expect(find.text('OpenAI'), findsNothing);

    await tester.tap(find.text('Clear search'));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('providers-search-empty')), findsNothing);
    expect(tester.widget<TextField>(search).controller!.text, isEmpty);
    expect(find.text('Anthropic'), findsOneWidget);
    expect(find.text('OpenAI'), findsOneWidget);
    expect(find.text('Z.AI · Global'), findsOneWidget);

    // The field's own clear button restores the list too.
    await tester.enterText(search, 'open');
    await tester.pump(KitMotion.typingSettle);
    await tester.pumpAndSettle();
    expect(find.text('Anthropic'), findsNothing);
    await tester.tap(find.byKey(const ValueKey('providers-search-clear')));
    await tester.pumpAndSettle();
    expect(find.text('Anthropic'), findsOneWidget);
    expect(find.text('OpenAI'), findsOneWidget);
    expect(find.text('Z.AI · Global'), findsOneWidget);
  });

  testWidgets('completed OAuth can retry a failed runtime refresh', (
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
              label: 'Cloud OAuth',
            ),
          ],
          connectionCount: 0,
        ),
      ]
      ..oauthStatus = const IntegrationAuthStatus(
        state: IntegrationAuthState.complete,
      )
      ..providerRefreshError = const ProductException('Refresh failed');
    final controller = await integrationsController(repository);
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      app(controller, authorizationLauncher: (_) async => true),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('connect-provider-cloud')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Open browser'));
    await tester.pump(const Duration(milliseconds: 300));
    expect(find.text('Open external link?'), findsOneWidget);
    // The confirmation slides in; let it land before tapping.
    await tester.pump(const Duration(milliseconds: 300));
    await tester.tap(find.text('Open link'));
    await tester.pumpAndSettle();
    await _finishSignIn(tester, 'Cloud Provider');
    await tester.pumpAndSettle();

    expect(
      find.text('Signed in · tap to finish', findRichText: true),
      findsOneWidget,
    );
    expect(repository.oauthStatusCalls, 1);
    expect(repository.providerRefreshCalls, 1);

    repository.providerRefreshError = null;
    await tester.tap(find.byKey(const ValueKey('pending-provider-oauth')));
    await tester.pumpAndSettle();
    expect(find.text('Authentication complete'), findsOneWidget);
    await tester.tap(find.text('Finish signing in to Cloud Provider'));
    await tester.pumpAndSettle();

    expect(repository.oauthStatusCalls, 1);
    expect(repository.providerRefreshCalls, 2);
    expect(find.byKey(const ValueKey('pending-provider-oauth')), findsNothing);
  });
}
