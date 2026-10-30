import 'dart:async';
import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/domain/agent_handoff.dart';
import 'package:opencode_mobile/domain/genui/gen_ui_history.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/state/agent_handoff_controller.dart';
import 'package:opencode_mobile/state/agent_handoff_links.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:shared_preferences_platform_interface/shared_preferences_platform_interface.dart';

class _Disk extends InMemorySharedPreferencesStore {
  _Disk(super.data) : super.withData();
  bool refuseLinks = false;

  @override
  Future<bool> setValue(String type, String key, Object value) {
    if (refuseLinks && key.startsWith('flutter.oc.agentHandoffs.')) {
      return Future.value(false);
    }
    return super.setValue(type, key, value);
  }
}

CatalogSnapshot _catalog({bool enabled = true}) => CatalogSnapshot(
  providers: [
    CatalogProvider(id: 'anthropic', name: 'Anthropic', enabled: enabled),
  ],
  models: [
    CatalogModel(
      id: 'anthropic/opus',
      providerID: 'anthropic',
      name: 'Opus',
      enabled: enabled,
      status: 'active',
      contextLimit: 100000,
      outputLimit: 10000,
      reasoning: true,
      attachments: false,
      tools: true,
      variants: const [],
    ),
  ],
  agents: const [],
);

class _Gateway
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

  CatalogSnapshot catalog = _catalog();
  final mutations = <String>[];
  Future<void> Function()? beforePrompt;
  Future<void> Function()? beforeCreate;
  Future<void> Function()? beforeWrite;
  Object? writeError;
  Object? createError;
  Object? promptError;
  Object? sessionError;
  bool changedSource = false;
  int sourceReads = 0;
  String? sentText;
  ModelRef? sentModel;
  String? sentAgent;
  String? markdown;

  @override
  Future<CatalogSnapshot> loadCatalog() =>
      throw StateError('unsupported catalog');
  @override
  Future<ProvidersResponse> providers() async => ProvidersResponse(
    providers: [
      for (final provider in catalog.providers)
        if (provider.enabled)
          ProviderInfo(
            id: provider.id,
            name: provider.name,
            modelIDs: [
              for (final model in catalog.models)
                if (model.enabled && model.providerID == provider.id) model.id,
            ],
            modelData: {
              for (final model in catalog.models)
                if (model.enabled && model.providerID == provider.id)
                  model.id: {
                    'variants': {
                      for (final variant in model.variants)
                        variant.id: {'disabled': variant.disabled},
                    },
                  },
            },
          ),
    ],
  );
  @override
  Future<Session> session(String id) async {
    sourceReads++;
    if (sessionError case final Object error) throw error;
    return Session(
      id: id,
      title: 'Repair parser',
      directory: directory,
      time: SessionTime(updated: changedSource && sourceReads > 1 ? 2 : 1),
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
        parts: [Part(type: 'text', text: 'Keep the parser compatible.')],
      ),
      MessageWithParts(
        info: MessageInfo(id: 'm2', sessionID: sessionID, role: 'assistant'),
        parts: [
          Part(
            type: 'text',
            text: 'The remaining task is the empty-input case.',
          ),
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
    mutations.add('write:$directory');
    this.markdown = markdown;
    await beforeWrite?.call();
    if (writeError case final Object error) throw error;
    return '.handoff/$recordId/record.md';
  }

  @override
  Future<Session> createSession() async {
    mutations.add('create');
    await beforeCreate?.call();
    if (createError case final Object error) throw error;
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
    await beforePrompt?.call();
    beforeSend();
    mutations.add('prompt:$sessionID');
    sentText = text;
    sentModel = model;
    sentAgent = agent;
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

class _Fixture {
  _Fixture(this.prefs, this.disk);
  final SharedPreferences prefs;
  final _Disk disk;
  final source = _Gateway();
  final target = _Gateway();
  bool current = true;
  final phases = <AgentHandoffPhase>[];
  AgentHandoffLinks get links => AgentHandoffLinks.forPreferences(prefs);

  AgentHandoffController controller({
    String? filesystemId = 'managed-linux',
    bool useDefaultRecordId = false,
    bool sameParent = false,
    HandoffOrigin sourceOrigin = HandoffOrigin.server,
    HandoffOrigin targetOrigin = HandoffOrigin.server,
  }) {
    late final AgentHandoffController result;
    result = AgentHandoffController(
      source: source,
      sourceScope: HandoffScope(
        profileId: 'p',
        origin: sourceOrigin,
        directory: '/project',
        filesystemId: filesystemId,
      ),
      sourceSessionId: 'source',
      target: HandoffTarget(
        gateway: target,
        scope: HandoffScope(
          profileId: sameParent ? 'p' : 'q',
          origin: targetOrigin,
          directory: '/project',
          filesystemId: filesystemId,
        ),
        model: ModelRef(providerID: 'anthropic', modelID: 'anthropic/opus'),
        label: 'Claude Code',
        agent: 'claude',
      ),
      links: links,
      isCurrent: () => current,
      recordIdFactory: useDefaultRecordId ? null : () => 'review-record',
      onChanged: () => phases.add(result.phase),
    );
    return result;
  }
}

Future<_Fixture> _setup() async {
  SharedPreferences.setMockInitialValues({
    'oc.profiles': jsonEncode([
      {'id': 'p'},
      {'id': 'q'},
    ]),
  });
  final disk = _Disk(await SharedPreferencesStorePlatform.instance.getAll());
  SharedPreferencesStorePlatform.instance = disk;
  SharedPreferences.resetStatic();
  return _Fixture(await SharedPreferences.getInstance(), disk);
}

Matcher _failure(HandoffFailureKind kind) =>
    throwsA(isA<AgentHandoffFailure>().having((e) => e.kind, 'kind', kind));

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test(
    'edited prompt masks credentials while preserving the exact UUID path',
    () async {
      final fixture = await _setup();
      final controller = fixture.controller(useDefaultRecordId: true);
      final draft = await controller.prepare(lastState: 'Waiting for you');
      final edited = '${draft.prompt}\napi_key=synthetic-sensitive-value';
      final reviewed = draft.sanitizePrompt(edited);
      expect(reviewed, contains(draft.relativePath));
      expect(reviewed, isNot(contains('synthetic-sensitive-value')));
      await controller.send(edited);
      expect(fixture.target.sentText, reviewed);
    },
  );

  test('preparation never exposes raw server errors', () async {
    final fixture = await _setup();
    fixture.source.sessionError = StateError('synthetic-private-server-detail');
    final controller = fixture.controller();
    await expectLater(
      controller.prepare(lastState: 'Failed'),
      _failure(HandoffFailureKind.preparationFailed),
    );
    expect(controller.failure.toString(), isNot(contains('synthetic-private')));
    expect(fixture.target.mutations, isEmpty);
  });

  test(
    'prepare exposes editable prompt and record without mutations',
    () async {
      final fixture = await _setup();
      final controller = fixture.controller();
      final draft = await controller.prepare(lastState: 'Waiting for you');
      expect(controller.phase, AgentHandoffPhase.ready);
      expect(draft.prompt, contains(draft.relativePath));
      expect(draft.record.markdown, contains('Keep the parser compatible.'));
      expect(draft.record.markdown, contains('empty-input case'));
      expect(fixture.source.mutations, isEmpty);
      expect(fixture.target.mutations, isEmpty);
      expect(fixture.prefs.containsKey('oc.agentHandoffs.p'), isFalse);
    },
  );

  test(
    'send writes record then creates and sends edited prompt before linking canonical IDs',
    () async {
      final fixture = await _setup();
      final controller = fixture.controller();
      final draft = await controller.prepare(lastState: 'Waiting for you');
      final edited = '${draft.prompt}\nStart with the empty-input regression.';
      final id = await controller.send(edited);
      expect(id, 'draft-new');
      expect(controller.createdSessionId, 'durable-draft-new');
      expect(fixture.target.mutations, [
        'write:/project',
        'create',
        'prompt:draft-new',
      ]);
      expect(fixture.target.markdown, draft.record.markdown);
      expect(fixture.target.sentText, edited);
      expect(fixture.target.sentModel!.modelID, 'opus');
      expect(fixture.target.sentAgent, 'claude');
      final link = fixture.links.forSession('p', 'durable-source').single;
      expect(link.targetSessionId, 'durable-draft-new');
      expect(link.sourceTitle, 'Repair parser');
      expect(
        fixture.links.forSession('q', 'durable-draft-new').single.id,
        'review-record',
      );
      expect(controller.phase, AgentHandoffPhase.complete);
      expect(
        fixture.phases,
        containsAllInOrder([
          AgentHandoffPhase.writingFile,
          AgentHandoffPhase.creating,
          AgentHandoffPhase.sending,
          AgentHandoffPhase.linking,
          AgentHandoffPhase.complete,
        ]),
      );
    },
  );

  test(
    'default UUID record path survives prompt redaction and sends unchanged',
    () async {
      final fixture = await _setup();
      final controller = fixture.controller(useDefaultRecordId: true);
      final draft = await controller.prepare(lastState: 'Idle');
      expect(
        draft.recordId,
        matches(
          RegExp(
            r'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$',
          ),
        ),
      );
      expect(await controller.send(draft.prompt), 'draft-new');
      expect(fixture.target.sentText, contains(draft.relativePath));
      expect(fixture.target.sentText, draft.prompt);
      expect(
        fixture.links.forSession('q', 'durable-draft-new').single.id,
        draft.recordId,
      );
    },
  );

  test(
    'server to phone-agent links persist under durable parent with both origins',
    () async {
      final fixture = await _setup();
      final controller = fixture.controller(
        sameParent: true,
        sourceOrigin: HandoffOrigin.server,
        targetOrigin: HandoffOrigin.phoneAgent,
      );
      final draft = await controller.prepare(lastState: 'Idle');
      expect(await controller.send(draft.prompt), 'draft-new');
      final outgoing = fixture.links
          .forSession('p', 'durable-source', origin: HandoffOrigin.server)
          .single;
      expect(outgoing.sourceOrigin, HandoffOrigin.server);
      expect(outgoing.targetOrigin, HandoffOrigin.phoneAgent);
      expect(outgoing.targetProfileId, 'p');
      expect(
        fixture.links
            .forSession(
              'p',
              'durable-draft-new',
              origin: HandoffOrigin.phoneAgent,
            )
            .single
            .id,
        outgoing.id,
      );
      expect(
        fixture.links.forSession(
          'p',
          'durable-draft-new',
          origin: HandoffOrigin.server,
        ),
        isEmpty,
      );
    },
  );

  test(
    'same runtime model choice routes to native switch without file support',
    () async {
      final fixture = await _setup();
      fixture.source.capabilities = const ServerCapabilities(
        agentHandoffFiles: false,
      );
      const scope = HandoffScope(profileId: 'p', directory: '/project');
      final controller = AgentHandoffController(
        source: fixture.source,
        sourceScope: scope,
        sourceSessionId: 'source',
        target: HandoffTarget(
          gateway: fixture.source,
          scope: scope,
          model: ModelRef(providerID: 'anthropic', modelID: 'opus'),
          label: 'Opus',
          startNewAgent: false,
        ),
        links: fixture.links,
        isCurrent: () => true,
      );
      expect(
        controller.route(ModelRef(providerID: 'openai', modelID: 'sol')),
        HandoffRoute.switchModel,
      );
      expect(fixture.source.mutations, isEmpty);
    },
  );

  test(
    'matching folder names without filesystem evidence cannot hand off',
    () async {
      final fixture = await _setup();
      final controller = fixture.controller(filesystemId: null);
      expect(controller.route(null), HandoffRoute.unavailable);
      await expectLater(
        controller.prepare(lastState: 'Idle'),
        _failure(HandoffFailureKind.projectMismatch),
      );
      expect(fixture.target.mutations, isEmpty);
    },
  );

  test(
    'unavailable target model fails before history or remote mutations',
    () async {
      final fixture = await _setup();
      fixture.target.catalog = _catalog(enabled: false);
      await expectLater(
        fixture.controller().prepare(lastState: 'Idle'),
        _failure(HandoffFailureKind.targetUnavailable),
      );
      expect(fixture.source.sourceReads, 0);
      expect(fixture.target.mutations, isEmpty);
    },
  );

  test('send revalidates model availability after review', () async {
    final fixture = await _setup();
    final controller = fixture.controller();
    final draft = await controller.prepare(lastState: 'Idle');
    fixture.target.catalog = _catalog(enabled: false);
    await expectLater(
      controller.send(draft.prompt),
      _failure(HandoffFailureKind.targetUnavailable),
    );
    expect(fixture.target.mutations, isEmpty);
  });

  test(
    'a changed source snapshot is rejected before a draft is offered',
    () async {
      final fixture = await _setup();
      fixture.source.changedSource = true;
      final controller = fixture.controller();
      await expectLater(
        controller.prepare(lastState: 'Idle'),
        _failure(HandoffFailureKind.sourceChanged),
      );
      expect(controller.draft, isNull);
      expect(fixture.target.mutations, isEmpty);
    },
  );

  test('new source activity during review requires a fresh handoff', () async {
    final fixture = await _setup();
    final controller = fixture.controller();
    final draft = await controller.prepare(lastState: 'Idle');
    fixture.source.changedSource = true;
    await expectLater(
      controller.send(draft.prompt),
      _failure(HandoffFailureKind.sourceChanged),
    );
    expect(fixture.target.mutations, isEmpty);
  });

  test(
    'closing the sheet after review prevents all remote mutations',
    () async {
      final fixture = await _setup();
      final controller = fixture.controller();
      final draft = await controller.prepare(lastState: 'Idle');
      fixture.current = false;
      await expectLater(
        controller.send(draft.prompt),
        _failure(HandoffFailureKind.staleScope),
      );
      expect(fixture.target.mutations, isEmpty);
    },
  );

  test('a closed agent connection is named in the details', () async {
    final fixture = await _setup();
    final controller = fixture.controller();
    final draft = await controller.prepare(lastState: 'Idle');
    fixture.target.isClosed = true;
    await expectLater(
      controller.send(draft.prompt),
      _failure(HandoffFailureKind.staleScope),
    );
    expect(controller.failureDetail, 'agent connection closed');
    expect(fixture.target.mutations, isEmpty);
  });

  for (final deletion in [false, true]) {
    test(
      '${deletion ? 'profile deletion' : 'scope change'} during target preparation fences prompt dispatch',
      () async {
        final fixture = await _setup();
        final controller = fixture.controller();
        final draft = await controller.prepare(lastState: 'Idle');
        fixture.target.beforePrompt = () async {
          if (deletion) {
            await AgentHandoffLinks.closeProfile(fixture.prefs, 'p');
          } else {
            fixture.target.directory = '/another-project';
          }
        };
        await expectLater(
          controller.send(draft.prompt),
          _failure(HandoffFailureKind.deliveryUnknown),
        );
        expect(fixture.target.mutations, ['write:/project', 'create']);
        expect(fixture.target.sentText, isNull);
        expect(controller.createdSessionId, 'draft-new');
        expect(fixture.prefs.containsKey('oc.agentHandoffs.p'), isFalse);
      },
    );
  }

  for (final duringCreation in [false, true]) {
    test(
      '${duringCreation ? 'creation' : 'delivery'} timeout cannot create or send twice',
      () async {
        final fixture = await _setup();
        final controller = fixture.controller();
        final draft = await controller.prepare(lastState: 'Idle');
        if (duringCreation) {
          fixture.target.createError = TimeoutException('Synthetic timeout');
        } else {
          fixture.target.promptError = TimeoutException('Synthetic timeout');
        }
        await expectLater(
          controller.send(draft.prompt),
          _failure(
            duringCreation
                ? HandoffFailureKind.creationUnknown
                : HandoffFailureKind.deliveryUnknown,
          ),
        );
        expect(controller.phase, AgentHandoffPhase.uncertain);
        final previous = List<String>.of(fixture.target.mutations);
        await expectLater(
          controller.send(draft.prompt),
          _failure(HandoffFailureKind.invalidDraft),
        );
        await expectLater(
          controller.retryLink(),
          _failure(HandoffFailureKind.invalidDraft),
        );
        expect(fixture.target.mutations, previous);
      },
    );
  }

  test('failed record write never creates a target conversation', () async {
    final fixture = await _setup();
    final controller = fixture.controller();
    final draft = await controller.prepare(lastState: 'Idle');
    fixture.target.writeError = StateError('Synthetic write failure');
    await expectLater(
      controller.send(draft.prompt),
      _failure(HandoffFailureKind.fileWriteFailed),
    );
    expect(fixture.target.mutations, ['write:/project']);
    expect(controller.createdSessionId, isNull);
  });

  test(
    'invalid edited prompts remain reviewable and cause no mutations',
    () async {
      final fixture = await _setup();
      final controller = fixture.controller();
      final draft = await controller.prepare(lastState: 'Idle');
      for (final text in [
        '',
        'Continue without the record',
        '${draft.prompt}${'x' * 17000}',
      ]) {
        await expectLater(
          controller.send(text),
          _failure(HandoffFailureKind.invalidDraft),
        );
        expect(controller.phase, AgentHandoffPhase.ready);
      }
      expect(fixture.target.mutations, isEmpty);
    },
  );

  test('link retry saves metadata only after acknowledged delivery', () async {
    final fixture = await _setup();
    final controller = fixture.controller();
    final draft = await controller.prepare(lastState: 'Idle');
    fixture.disk.refuseLinks = true;
    await expectLater(
      controller.send(draft.prompt),
      _failure(HandoffFailureKind.linkFailed),
    );
    expect(controller.createdSessionId, 'durable-draft-new');
    final previous = List<String>.of(fixture.target.mutations);
    fixture.disk.refuseLinks = false;
    await controller.retryLink();
    expect(controller.phase, AgentHandoffPhase.complete);
    expect(controller.failure, isNull);
    expect(fixture.target.mutations, previous);
    expect(fixture.links.forSession('q', 'durable-draft-new'), hasLength(1));
  });
}
