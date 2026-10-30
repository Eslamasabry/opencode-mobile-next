// Fakes for the Continue with… sheet: a gateway that can be read and written
// like an agent host, and a ContinueWithHost over real controllers.
import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/domain/agent_handoff.dart';
import 'package:opencode_mobile/domain/genui/gen_ui_history.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/state/agent_handoff_controller.dart';
import 'package:opencode_mobile/state/agent_handoff_links.dart';
import 'package:opencode_mobile/state/continue_with.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:shared_preferences_platform_interface/shared_preferences_platform_interface.dart';

class RefusingLinksDisk extends InMemorySharedPreferencesStore {
  RefusingLinksDisk(super.data) : super.withData() {
    current = this;
  }

  /// The disk the last [FakeContinueWithHost.prefsWithProfiles] installed.
  static RefusingLinksDisk? current;
  bool refuseLinks = false;

  @override
  Future<bool> setValue(String type, String key, Object value) {
    if (refuseLinks && key.startsWith('flutter.oc.agentHandoffs.')) {
      return Future.value(false);
    }
    return super.setValue(type, key, value);
  }
}

CatalogSnapshot opusCatalog() => const CatalogSnapshot(
  providers: [
    CatalogProvider(id: 'anthropic', name: 'Anthropic', enabled: true),
  ],
  models: [
    CatalogModel(
      id: 'anthropic/opus',
      providerID: 'anthropic',
      name: 'Opus',
      enabled: true,
      status: 'active',
      contextLimit: 100000,
      outputLimit: 10000,
      reasoning: true,
      attachments: false,
      tools: true,
      variants: [],
    ),
  ],
  agents: [],
);

class FakeHandoffGateway
    implements
        ServerGateway,
        CatalogGateway,
        GenUiHistoryGateway,
        AgentHandoffTargetGateway {
  @override
  String? directory = '/project';
  @override
  String? workspace;
  @override
  bool isClosed = false;
  @override
  ServerCapabilities capabilities = const ServerCapabilities(
    agentHandoffFiles: true,
    sessionTodos: false,
    sessionDiff: false,
  );

  final mutations = <String>[];
  Object? promptError;
  Object? sessionError;
  String? sentText;
  String? markdown;
  String userText = 'Keep the parser compatible.';

  @override
  Future<CatalogSnapshot> loadCatalog() => throw StateError('unsupported');
  @override
  Future<ProvidersResponse> providers() async => ProvidersResponse(
    providers: [
      ProviderInfo(
        id: 'anthropic',
        name: 'Anthropic',
        modelIDs: ['anthropic/opus'],
        modelData: {'anthropic/opus': const {}},
      ),
    ],
  );
  @override
  Future<Session> session(String id) async {
    if (sessionError case final Object error) throw error;
    return Session(
      id: id,
      title: 'Repair parser',
      directory: directory,
      time: SessionTime(updated: 1),
    );
  }

  @override
  Future<GenUiHistoryPage> genUiHistoryPage(
    String sessionID, {
    String? cursor,
    int limit = 50,
  }) async => GenUiHistoryPage(
    items: [
      MessageWithParts(
        info: MessageInfo(id: 'm1', sessionID: sessionID, role: 'user'),
        parts: [Part(type: 'text', text: userText)],
      ),
      MessageWithParts(
        info: MessageInfo(id: 'm2', sessionID: sessionID, role: 'assistant'),
        parts: [
          Part(type: 'text', text: 'The remaining task is the empty case.'),
        ],
      ),
    ],
    hasMore: false,
  );

  @override
  Future<String> writeAgentHandoff({
    required String directory,
    required String recordId,
    required String markdown,
  }) async {
    mutations.add('write');
    this.markdown = markdown;
    return '.handoff/$recordId/record.md';
  }

  @override
  Future<Session> createSession() async {
    mutations.add('create');
    return Session(id: 'draft-new', directory: directory);
  }

  @override
  Future<void> promptAgentHandoff(
    String sessionID, {
    required String text,
    required ModelRef model,
    String? agent,
    String? variant,
    required void Function() beforeSend,
  }) async {
    beforeSend();
    mutations.add('prompt');
    sentText = text;
    if (promptError case final Object error) throw error;
  }

  @override
  String handoffSessionId(String sessionID) => 'durable-$sessionID';
  @override
  String handoffNavigationSessionId(String sessionID) =>
      sessionID == 'durable-draft-new' ? 'draft-new' : sessionID;
  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class FakeContinueWithHost implements ContinueWithHost {
  FakeContinueWithHost(this.prefs, {this.agentBlock, this.loadError});

  final SharedPreferences prefs;
  final source = FakeHandoffGateway();
  final target = FakeHandoffGateway();
  final ContinueWithBlock? agentBlock;
  final Object? loadError;
  final switched = <String>[];
  final adopted = <String>[];
  Object? switchError;

  static Future<SharedPreferences> prefsWithProfiles() async {
    SharedPreferences.setMockInitialValues({
      'oc.profiles': jsonEncode([
        {'id': 'p'},
      ]),
    });
    final disk = RefusingLinksDisk(
      await SharedPreferencesStorePlatform.instance.getAll(),
    );
    SharedPreferencesStorePlatform.instance = disk;
    SharedPreferences.resetStatic();
    return SharedPreferences.getInstance();
  }

  HandoffScope get scope => const HandoffScope(
    profileId: 'p',
    directory: '/project',
    filesystemId: 'managed-linux',
  );

  @override
  String get lastState => 'Waiting for you';

  int loads = 0;

  @override
  Future<ContinueWithPlan> load() async {
    loads++;
    if (loadError case final Object error) throw error;
    return ContinueWithPlan(
      sourceTitle: 'Repair parser',
      project: 'project',
      models: [
        ContinueWithChoice(
          key: 'model:openai/gpt',
          agentName: 'OpenAI',
          modelName: 'GPT-6 Terra',
          target: HandoffTarget(
            gateway: source,
            scope: scope,
            model: ModelRef(providerID: 'openai', modelID: 'gpt-6-terra'),
            label: 'GPT-6 Terra',
            startNewAgent: false,
          ),
        ),
      ],
      agents: [
        if (agentBlock != null)
          ContinueWithChoice(
            key: 'agent:claude',
            agentName: 'Claude Code',
            modelName: 'Claude Code',
            agentId: 'claude',
            block: agentBlock,
          )
        else
          ContinueWithChoice(
            key: 'agent:claude:opus',
            agentName: 'Claude Code',
            modelName: 'Opus 5.5',
            agentId: 'claude',
            target: HandoffTarget(
              gateway: target,
              scope: const HandoffScope(
                profileId: 'p',
                directory: '/project',
                filesystemId: 'managed-linux',
                origin: HandoffOrigin.phoneAgent,
              ),
              model: ModelRef(providerID: 'anthropic', modelID: 'opus'),
              label: 'Claude Code',
              agent: 'claude',
            ),
          ),
      ],
    );
  }

  bool active = true;

  @override
  AgentHandoffController controllerFor(
    ContinueWithChoice choice, {
    required bool Function() isCurrent,
    required VoidCallback onChanged,
  }) => AgentHandoffController(
    source: source,
    sourceScope: scope,
    sourceSessionId: 'source',
    target: choice.target!,
    links: AgentHandoffLinks.forPreferences(prefs),
    isCurrent: () => isCurrent() && active,
    onChanged: onChanged,
    recordIdFactory: () => 'review-record',
  );

  @override
  Future<void> switchModel(ContinueWithChoice choice) async {
    if (switchError case final Object error) throw error;
    switched.add(choice.modelName);
  }

  @override
  Future<String> adopt(
    ContinueWithChoice choice,
    String navigationSessionId,
  ) async {
    adopted.add(navigationSessionId);
    return navigationSessionId;
  }
}
