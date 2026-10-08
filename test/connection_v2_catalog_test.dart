import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'support/connection_v2_fixtures.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('catalog replaces stale saved model and agent', (tester) async {
    final store = await memoryProfileStore({
      'oc.model.server': 'removed-provider|removed-model',
      'oc.agent.server': 'removed-agent',
      'oc.modelLibrary.server': jsonEncode({
        'favorites': [
          {'providerID': 'provider-1', 'modelID': 'model-1'},
          {'providerID': 'removed-provider', 'modelID': 'removed-model'},
        ],
        'recent': [
          {'providerID': 'removed-provider', 'modelID': 'removed-model'},
          {'providerID': 'provider-1', 'modelID': 'model-1'},
        ],
      }),
    });
    final api = V2Api(
      providersResult: ProvidersResponse(
        providers: [
          ProviderInfo(
            id: 'provider-1',
            name: 'Provider',
            modelIDs: const ['model-1'],
          ),
        ],
        defaultProviderID: 'missing-default',
        defaultModelID: 'missing-model',
      ),
      agentsResult: [AgentInfo(name: 'build')],
    );
    final controller = ConnectionController(
      store,
      apiFactory: (_) => api,
      repositoryFactory: (_) => QuestionRepository(legacyUnavailable: false),
      eventStreamFactory:
          ({required api, required onEvent, required onStatus, onError}) =>
              FakeEventStream(
                api: api,
                onEvent: onEvent,
                onStatus: onStatus,
                onError: onError,
              ),
    );
    addTearDown(controller.dispose);

    await controller.connect(
      ServerProfile(
        id: 'server',
        name: 'Server',
        baseUrl: 'http://127.0.0.1:1',
      ),
    );
    await tester.pump();
    await tester.pump();

    expect(controller.selectedModel?.providerID, 'provider-1');
    expect(controller.selectedModel?.modelID, 'model-1');
    expect(controller.selectedAgent, 'build');
    expect(store.modelFor('server'), ('provider-1', 'model-1'));
    expect(store.agentFor('server'), 'build');
    expect(
      controller.modelLibrary.favorites.single.wireName,
      'provider-1/model-1',
    );
    expect(
      store.modelLibraryFor('server').recent.single.wireName,
      'provider-1/model-1',
    );
    api.catalogUnavailable = true;
    await controller.refreshCatalog();
    expect(controller.catalogError, isNotNull);
    expect(
      controller.modelLibrary.favorites.single.wireName,
      'provider-1/model-1',
    );
    expect(
      store.modelLibraryFor('server').recent.single.wireName,
      'provider-1/model-1',
    );
    controller.dispose();
  });

  testWidgets(
    'model shortcuts and chat choices survive location changes and isolate profiles',
    (tester) async {
      final store = await memoryProfileStore({
        'oc.modelLibrary.server': jsonEncode({
          'favorites': [
            {'providerID': 'p', 'modelID': 'a'},
          ],
          'recent': [],
        }),
        'oc.modelLibrary.other': jsonEncode({
          'favorites': [
            {'providerID': 'p', 'modelID': 'b'},
          ],
          'recent': [],
        }),
      });
      final controller = ConnectionController(
        store,
        apiFactory: (_) => V2Api(
          providersResult: ProvidersResponse(
            providers: [
              ProviderInfo(
                id: 'p',
                name: 'Provider',
                modelIDs: const ['a', 'b'],
              ),
            ],
          ),
        ),
        repositoryFactory: (_) => QuestionRepository(legacyUnavailable: false),
        eventStreamFactory:
            ({required api, required onEvent, required onStatus, onError}) =>
                FakeEventStream(
                  api: api,
                  onEvent: onEvent,
                  onStatus: onStatus,
                  onError: onError,
                ),
      );
      addTearDown(controller.dispose);
      await controller.connect(
        ServerProfile(
          id: 'server',
          name: 'First',
          baseUrl: 'http://127.0.0.1:1',
        ),
      );
      await tester.pump();
      await tester.pump();
      await controller.selectModelForSession(
        'chat',
        ModelRef(providerID: 'p', modelID: 'b'),
      );
      await controller.selectLocation(directory: '/another-project');
      expect(controller.modelLibrary.favorites.single.modelID, 'a');
      expect(controller.modelLibrary.recent.first.modelID, 'b');
      expect(controller.modelForSession('chat')?.modelID, 'b');
      await controller.connect(
        ServerProfile(
          id: 'other',
          name: 'Second',
          baseUrl: 'http://127.0.0.1:1',
        ),
      );
      await tester.pump();
      await tester.pump();
      expect(controller.modelLibrary.favorites.single.modelID, 'b');
      expect(controller.modelLibrary.recent, isEmpty);
      expect(controller.sessionModels, isEmpty);
      expect(store.modelLibraryFor('server').favorites.single.modelID, 'a');
      await controller.disconnect();
      expect(controller.modelLibrary.favorites, isEmpty);
    },
  );

  testWidgets(
    'fresh catalog follows project chat defaults, not provider order',
    (tester) async {
      final api = V2Api(
        providersResult: ProvidersResponse(
          providers: [
            ProviderInfo(
              id: 'google',
              name: 'Google',
              modelIDs: const ['image-default'],
            ),
            ProviderInfo(
              id: 'openai',
              name: 'OpenAI',
              modelIDs: const ['gpt-5.6-sol'],
            ),
          ],
          defaultProviderID: 'google',
          defaultModelID: 'image-default',
        ),
        agentsResult: [
          AgentInfo(name: 'build', mode: 'primary'),
          AgentInfo(name: 'plan', mode: 'primary'),
          AgentInfo(name: 'explore', mode: 'subagent'),
        ],
      );
      final controller = ConnectionController(
        await memoryProfileStore(),
        apiFactory: (_) => api,
        repositoryFactory: (_) => QuestionRepository(
          legacyUnavailable: false,
          defaults: ChatDefaults(
            model: ModelRef(providerID: 'openai', modelID: 'gpt-5.6-sol'),
            agent: 'plan',
          ),
        ),
        eventStreamFactory:
            ({required api, required onEvent, required onStatus, onError}) =>
                FakeEventStream(
                  api: api,
                  onEvent: onEvent,
                  onStatus: onStatus,
                  onError: onError,
                ),
      );
      addTearDown(controller.dispose);

      await controller.connect(
        ServerProfile(
          id: 'server',
          name: 'Server',
          baseUrl: 'http://127.0.0.1:1',
        ),
      );
      await tester.pump();
      await tester.pump();

      expect(controller.selectedModel?.providerID, 'openai');
      expect(controller.selectedModel?.modelID, 'gpt-5.6-sol');
      expect(controller.selectedAgent, 'plan');
      controller.dispose();
    },
  );

  testWidgets(
    'connected catalog prunes a model exposed only by the active v2 surface',
    (tester) async {
      final api = V2Api(
        providersResult: ProvidersResponse(
          providers: [
            ProviderInfo(
              id: 'opencode',
              name: 'OpenCode Zen',
              modelIDs: const ['current-model'],
            ),
          ],
          defaultProviderID: 'opencode',
          defaultModelID: 'current-model',
        ),
        agentsResult: [AgentInfo(name: 'build')],
      );
      final detailed = CatalogSnapshot(
        providers: const [
          CatalogProvider(id: 'opencode', name: 'OpenCode Zen', enabled: true),
        ],
        models: const [
          CatalogModel(
            id: 'ox-alpha',
            providerID: 'opencode',
            name: 'Ox Alpha',
            enabled: true,
            status: 'active',
            contextLimit: 1000000,
            outputLimit: 131072,
            reasoning: true,
            attachments: true,
            tools: true,
            variants: [],
          ),
          CatalogModel(
            id: 'current-model',
            providerID: 'opencode',
            name: 'Current model',
            enabled: true,
            status: 'active',
            contextLimit: 128000,
            outputLimit: 16000,
            reasoning: true,
            attachments: true,
            tools: true,
            variants: [],
          ),
        ],
        agents: const [
          CatalogAgent(id: 'build', mode: 'primary', hidden: false),
        ],
      );
      final controller = ConnectionController(
        await memoryProfileStore({'oc.model.server': 'opencode|ox-alpha'}),
        apiFactory: (_) => api,
        repositoryFactory: (_) =>
            QuestionRepository(legacyUnavailable: false, catalog: detailed),
        eventStreamFactory:
            ({required api, required onEvent, required onStatus, onError}) =>
                FakeEventStream(
                  api: api,
                  onEvent: onEvent,
                  onStatus: onStatus,
                  onError: onError,
                ),
      );
      addTearDown(controller.dispose);

      await controller.connect(
        ServerProfile(
          id: 'server',
          name: 'Server',
          baseUrl: 'http://127.0.0.1:1',
        ),
      );
      await tester.pump();
      await tester.pump();

      expect(controller.catalog?.models.map((model) => model.id), [
        'current-model',
      ]);
      expect(controller.selectedModel?.modelID, 'current-model');
      expect(controller.catalogDetailed, isTrue);
      controller.dispose();
    },
  );

  testWidgets('connected catalog keeps Z.AI when active v2 only shows Zen', (
    tester,
  ) async {
    final api = V2Api(
      providersResult: ProvidersResponse(
        providers: [
          ProviderInfo(
            id: 'opencode',
            name: 'OpenCode Zen',
            modelIDs: const ['nemotron'],
            modelData: const {
              'nemotron': {
                'capabilities': {
                  'reasoning': true,
                  'attachment': true,
                  'toolcall': true,
                },
                'variants': {
                  'fast': {'reasoningEffort': 'low'},
                },
              },
            },
          ),
          ProviderInfo(
            id: 'zai-coding-plan',
            name: 'Z.AI Coding Plan',
            modelIDs: const ['glm-5.2'],
          ),
        ],
        defaultProviderID: 'opencode',
        defaultModelID: 'nemotron',
      ),
      agentsResult: [AgentInfo(name: 'build')],
    );
    final detailed = CatalogSnapshot(
      providers: const [
        CatalogProvider(id: 'opencode', name: 'OpenCode Zen', enabled: true),
      ],
      models: const [
        CatalogModel(
          id: 'nemotron',
          providerID: 'opencode',
          name: 'Nemotron',
          enabled: true,
          status: 'active',
          contextLimit: 262144,
          outputLimit: 262144,
          reasoning: false,
          attachments: false,
          tools: false,
          variants: [],
        ),
      ],
      agents: const [CatalogAgent(id: 'build', mode: 'primary', hidden: false)],
    );
    final controller = ConnectionController(
      await memoryProfileStore({'oc.model.server': 'zai-coding-plan|glm-5.2'}),
      apiFactory: (_) => api,
      repositoryFactory: (_) =>
          QuestionRepository(legacyUnavailable: false, catalog: detailed),
      eventStreamFactory:
          ({required api, required onEvent, required onStatus, onError}) =>
              FakeEventStream(
                api: api,
                onEvent: onEvent,
                onStatus: onStatus,
                onError: onError,
              ),
    );
    addTearDown(controller.dispose);

    await controller.connect(
      ServerProfile(
        id: 'server',
        name: 'Server',
        baseUrl: 'http://127.0.0.1:1',
      ),
    );
    await tester.pump();
    await tester.pump();

    expect(
      controller.catalog?.providers.map((provider) => provider.id),
      contains('zai-coding-plan'),
    );
    expect(
      controller.catalog?.models.map(
        (model) => '${model.providerID}/${model.id}',
      ),
      contains('zai-coding-plan/glm-5.2'),
    );
    expect(controller.selectedModel?.providerID, 'zai-coding-plan');
    expect(controller.selectedModel?.modelID, 'glm-5.2');
    final zenModel = controller.catalog!.models.singleWhere(
      (model) => model.providerID == 'opencode' && model.id == 'nemotron',
    );
    expect(zenModel.reasoning, isTrue);
    expect(zenModel.attachments, isTrue);
    expect(zenModel.tools, isTrue);
    expect(zenModel.variants.single.id, 'fast');
    controller.dispose();
  });

  testWidgets(
    'v1 integration credential does not expose a provider absent from runtime',
    (tester) async {
      final api = V2Api(
        providersResult: ProvidersResponse.fromJson({
          'connected': ['opencode'],
          'all': [
            {
              'id': 'opencode',
              'name': 'OpenCode Zen',
              'models': {'nemotron': <String, Object?>{}},
            },
            {
              'id': 'zai-coding-plan',
              'name': 'Z.AI Coding Plan',
              'models': {
                'glm-5.2': {
                  'name': 'GLM-5.2',
                  'capabilities': {'reasoning': true, 'toolcall': true},
                  'limit': {'context': 1000000, 'output': 131072},
                  'variants': {
                    'high': {'reasoningEffort': 'high'},
                    'max': {'reasoningEffort': 'max'},
                  },
                },
              },
            },
          ],
          'default': {'opencode': 'nemotron'},
        }),
        configuredProvidersResult: ProvidersResponse.fromJson({
          'providers': [
            {
              'id': 'opencode',
              'name': 'OpenCode Zen',
              'models': {'nemotron': <String, Object?>{}},
            },
          ],
        }),
        agentsResult: [AgentInfo(name: 'build')],
      );
      final detailed = CatalogSnapshot(
        providers: const [
          CatalogProvider(id: 'opencode', name: 'OpenCode Zen', enabled: true),
        ],
        models: const [
          CatalogModel(
            id: 'nemotron',
            providerID: 'opencode',
            name: 'Nemotron',
            enabled: true,
            status: 'active',
            contextLimit: 262144,
            outputLimit: 262144,
            reasoning: true,
            attachments: false,
            tools: true,
            variants: [],
          ),
        ],
        agents: const [
          CatalogAgent(id: 'build', mode: 'primary', hidden: false),
        ],
      );
      final store = await memoryProfileStore({
        'oc.model.termux': 'zai-coding-plan|glm-5.2',
      });
      final repository = QuestionRepository(
        legacyUnavailable: false,
        catalog: detailed,
        integrations: const [
          IntegrationInfo(
            id: 'zai-coding-plan',
            name: 'Z.AI Coding Plan',
            methods: [],
            connectionCount: 1,
          ),
        ],
      );
      final controller = ConnectionController(
        store,
        apiFactory: (_) => api,
        repositoryFactory: (_) => repository,
        eventStreamFactory:
            ({required api, required onEvent, required onStatus, onError}) =>
                FakeEventStream(
                  api: api,
                  onEvent: onEvent,
                  onStatus: onStatus,
                  onError: onError,
                ),
      );
      addTearDown(controller.dispose);

      await controller.connect(
        ServerProfile(
          id: 'termux',
          name: 'This device (Termux)',
          baseUrl: 'http://127.0.0.1:4096',
        ),
      );
      await tester.pump();
      await tester.pump();

      expect(
        controller.catalog?.providers.map((provider) => provider.id),
        isNot(contains('zai-coding-plan')),
      );
      expect(
        controller.catalog?.models.map(
          (model) => '${model.providerID}/${model.id}',
        ),
        isNot(contains('zai-coding-plan/glm-5.2')),
      );
      expect(controller.selectedModel?.providerID, 'opencode');
      expect(repository.runtimeRefreshCalls, 1);
      expect(store.providerRuntimeWasRefreshed('termux'), isTrue);

      // A location change refreshes the runtime; the filesystem root is no
      // longer a selectable workspace, so use a project folder.
      // It runs once the folder is open, just before the catalog.
      await controller.selectLocation(directory: '/root/projects/app');
      await tester.pump();
      await tester.pump();
      expect(repository.runtimeRefreshCalls, 2);
      expect(
        store.providerRuntimeWasRefreshed(
          'termux',
          directory: '/root/projects/app',
        ),
        isTrue,
      );

      await controller.connect(
        ServerProfile(
          id: 'termux',
          name: 'This device (Termux)',
          baseUrl: 'http://127.0.0.1:4096',
        ),
      );
      expect(repository.runtimeRefreshCalls, 2);
      controller.dispose();
    },
  );

  testWidgets('Termux profile keeps a valid model returned by OpenCode', (
    tester,
  ) async {
    final store = await memoryProfileStore({
      'oc.model.termux': 'opencode|big-pickle',
    });
    final api = V2Api(
      providersResult: ProvidersResponse(
        providers: [
          ProviderInfo(
            id: 'opencode',
            name: 'OpenCode Zen',
            modelIDs: const ['nemotron-3.5-lightning-free', 'big-pickle'],
          ),
        ],
        defaultProviderID: 'opencode',
        defaultModelID: 'big-pickle',
      ),
    );
    final controller = ConnectionController(
      store,
      apiFactory: (_) => api,
      repositoryFactory: (_) => QuestionRepository(legacyUnavailable: false),
      eventStreamFactory:
          ({required api, required onEvent, required onStatus, onError}) =>
              FakeEventStream(
                api: api,
                onEvent: onEvent,
                onStatus: onStatus,
                onError: onError,
              ),
    );
    addTearDown(controller.dispose);

    await controller.connect(
      ServerProfile(
        id: 'termux',
        name: 'This device (Termux)',
        baseUrl: 'http://127.0.0.1:4096',
      ),
    );
    await tester.pump();
    await tester.pump();

    expect(controller.selectedModel?.providerID, 'opencode');
    expect(controller.selectedModel?.modelID, 'big-pickle');
    expect(store.modelFor('termux'), ('opencode', 'big-pickle'));
    expect(store.modelWasExplicitlySelected('termux'), isFalse);
    controller.dispose();
  });

  testWidgets('Termux profile keeps an explicit valid OpenCode model', (
    tester,
  ) async {
    final store = await memoryProfileStore({
      'oc.model.termux': 'opencode|big-pickle',
      'oc.modelExplicit.termux': true,
    });
    final api = V2Api(
      providersResult: ProvidersResponse(
        providers: [
          ProviderInfo(
            id: 'opencode',
            name: 'OpenCode Zen',
            modelIDs: const ['nemotron-3.5-lightning-free', 'big-pickle'],
          ),
        ],
        defaultProviderID: 'opencode',
        defaultModelID: 'big-pickle',
      ),
    );
    final controller = ConnectionController(
      store,
      apiFactory: (_) => api,
      repositoryFactory: (_) => QuestionRepository(legacyUnavailable: false),
      eventStreamFactory:
          ({required api, required onEvent, required onStatus, onError}) =>
              FakeEventStream(
                api: api,
                onEvent: onEvent,
                onStatus: onStatus,
                onError: onError,
              ),
    );
    addTearDown(controller.dispose);

    await controller.connect(
      ServerProfile(
        id: 'termux',
        name: 'This device (Termux)',
        baseUrl: 'http://127.0.0.1:4096',
      ),
    );
    await tester.pump();
    await tester.pump();

    expect(controller.selectedModel?.modelID, 'big-pickle');
    expect(store.modelFor('termux'), ('opencode', 'big-pickle'));
    expect(store.modelWasExplicitlySelected('termux'), isTrue);
    controller.dispose();
  });

  testWidgets(
    'project default replaces an old automatic provider-order fallback',
    (tester) async {
      final store = await memoryProfileStore({
        'oc.model.termux': 'google|image-default',
        'oc.modelExplicit.termux': false,
      });
      final api = V2Api(
        providersResult: ProvidersResponse(
          providers: [
            ProviderInfo(
              id: 'google',
              name: 'Google',
              modelIDs: const ['image-default'],
            ),
            ProviderInfo(
              id: 'openai',
              name: 'OpenAI',
              modelIDs: const ['gpt-5.6-sol'],
            ),
          ],
          defaultProviderID: 'google',
          defaultModelID: 'image-default',
        ),
      );
      final controller = ConnectionController(
        store,
        apiFactory: (_) => api,
        repositoryFactory: (_) => QuestionRepository(
          legacyUnavailable: false,
          defaults: ChatDefaults(
            model: ModelRef(providerID: 'openai', modelID: 'gpt-5.6-sol'),
          ),
        ),
        eventStreamFactory:
            ({required api, required onEvent, required onStatus, onError}) =>
                FakeEventStream(
                  api: api,
                  onEvent: onEvent,
                  onStatus: onStatus,
                  onError: onError,
                ),
      );
      addTearDown(controller.dispose);

      await controller.connect(
        ServerProfile(
          id: 'termux',
          name: 'This device (Termux)',
          baseUrl: 'http://127.0.0.1:4096',
        ),
      );
      await tester.pump();
      await tester.pump();

      expect(controller.selectedModel?.providerID, 'openai');
      expect(controller.selectedModel?.modelID, 'gpt-5.6-sol');
      expect(store.modelFor('termux'), ('openai', 'gpt-5.6-sol'));
      expect(store.modelWasExplicitlySelected('termux'), isFalse);
      controller.dispose();
    },
  );
}
