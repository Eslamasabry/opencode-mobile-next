import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api2/events.dart';
import 'package:opencode_mobile/api2/gateway_events.dart';
import 'package:opencode_mobile/api2/gateway_mappers.dart';
import 'package:opencode_mobile/api2/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/offline_queue.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/widgets/pickers.dart';
import 'package:shared_preferences/shared_preferences.dart';

ModelRef ref(String id) => ModelRef(providerID: 'p', modelID: id);
Session _session(
  String id, {
  String model = 'one',
  String agent = 'build',
  String variant = '',
}) => Session(
  id: id,
  directory: '/one',
  title: id,
  cost: 3,
  reverted: true,
  selection: SessionSelection(
    model: ref(model),
    variant: variant,
    agent: agent,
  ),
);

class SelectionApi extends OpenCodeApi implements SessionSelectionGateway {
  // Selection-only fake has no durable client-ID prompt endpoint.
  @override
  ServerCapabilities get capabilities => const ServerCapabilities();
  SelectionApi() : super(baseUrl: 'http://localhost');
  final values = <String, Session>{'a': _session('a'), 'b': _session('b')};
  final writes = <String>[];
  SessionSelection? createdDefaults;
  Future<void> Function()? beforeWrite;
  Future<Session> Function(String)? read;

  @override
  Future<Session> session(String id) async =>
      read == null ? values[id]! : await read!(id);
  @override
  Future<List<Session>> sessions() async => values.values.toList();
  @override
  Future<Map<String, String>> sessionStatuses() async => {};
  @override
  Future<Session> createSelectedSession(SessionSelection defaults) async {
    createdDefaults = defaults;
    return values['new'] = Session(
      id: 'new',
      directory: '/one',
      selection: defaults,
    );
  }

  @override
  Future<void> setSessionModel(
    String id,
    ModelRef model,
    String variant,
  ) async {
    writes.add('model:$id:${model.wireName}:$variant');
    await beforeWrite?.call();
    values[id] = values[id]!.copyWith(
      selection: values[id]!.selection!.withModel(model, variant),
    );
  }

  @override
  Future<void> setSessionAgent(String id, String agent) async {
    writes.add('agent:$id:$agent');
    await beforeWrite?.call();
    values[id] = values[id]!.copyWith(
      selection: values[id]!.selection!.withAgent(agent),
    );
  }

  @override
  Future<void> promptAsync(
    String sessionID, {
    required String text,
    ModelRef? model,
    String? agent,
    String? variant,
    List<PromptAttachment> attachments = const [],
    List<PromptAgentMention> agentMentions = const [],
    PromptDelivery? delivery,
  }) async {
    writes.add('prompt:$sessionID:$text');
  }
}

class SelectionController extends ConnectionController {
  SelectionController(super.store);
  @override
  ServerProfile get profile => ServerProfile(
    id: 'p',
    name: 'Test',
    baseUrl: 'http://localhost',
    flavor: ServerFlavor.v2,
  );
}

class OfflinePhoneSelectionController extends ConnectionController {
  OfflinePhoneSelectionController(super.store) : super(isAgentBackend: true);
  @override
  ServerProfile get profile => ServerProfile(
    id: 'p',
    name: 'Phone agents',
    baseUrl: 'ws://localhost',
    backend: ServerBackend.paseo,
  );
}

Future<ConnectionController> controllerFor(SelectionApi api) async {
  SharedPreferences.setMockInitialValues({});
  final controller =
      SelectionController(
          ProfileStore(prefs: await SharedPreferences.getInstance()),
        )
        ..api = api
        ..directory = '/one'
        ..status = StreamStatus.connected
        ..selectedModel = ref('default')
        ..selectedAgent = 'default-agent'
        ..selectedVariant = 'high';
  addTearDown(controller.dispose);
  await controller.refreshSessions();
  return controller;
}

void event(
  ConnectionController controller,
  String type,
  Map<String, dynamic> data,
) {
  final adapter = Api2EventAdapter();
  for (final envelope in adapter.adapt(
    Api2EventEnvelope.fromJson({'type': type, 'data': data}),
  )) {
    controller.handleEventForTesting(envelope);
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test(
    'cold start model fallback is per conversation and survives restart',
    () async {
      final api = SelectionApi();
      api.values['b'] = _session('b', model: 'two');
      final c = await controllerFor(api);
      c.rememberModelName('p', 'one', 'First model');
      c.rememberModelName('p', 'two', 'Second model');
      expect(c.displayModelForSession('a')?.modelID, 'one');
      expect(c.displayModelForSession('b')?.modelID, 'two');
      // Let the bounded profile cache finish before simulating process restart.
      await Future<void>.delayed(Duration.zero);
      final restored = SelectionController(c.store)
        ..api = SelectionApi()
        ..status = StreamStatus.disconnected;
      addTearDown(restored.dispose);
      expect(restored.selectionForSession('a').modelKnown, isFalse);
      expect(restored.modelForSession('a'), isNull);
      expect(restored.displayModelForSession('a')?.modelID, 'one');
      expect(restored.displayModelForSession('b')?.modelID, 'two');
      expect(restored.displayModelForSession('unknown'), isNull);
      expect(restored.knownModelName('p', 'two'), 'Second model');
      // A confirmed server default invalidates, rather than borrows, a model.
      restored.sessionsById['a'] = Session(
        id: 'a',
        selection: SessionSelection(),
      );
      expect(restored.displayModelForSession('a'), isNull);
      expect(restored.displayModelForSession('b')?.modelID, 'two');
      await Future<void>.delayed(Duration.zero);
      final afterDefault = SelectionController(c.store)
        ..api = SelectionApi()
        ..status = StreamStatus.disconnected;
      addTearDown(afterDefault.dispose);
      expect(afterDefault.displayModelForSession('a'), isNull);
      expect(afterDefault.displayModelForSession('b')?.modelID, 'two');
    },
  );

  testWidgets('session agent failure stays visible without changing defaults', (
    tester,
  ) async {
    final api = SelectionApi();
    final controller = await controllerFor(api);
    controller.catalog = const CatalogSnapshot(
      providers: [],
      models: [
        CatalogModel(
          id: 'one',
          providerID: 'p',
          name: 'One',
          enabled: true,
          status: 'active',
          contextLimit: 1000,
          outputLimit: 100,
          reasoning: false,
          attachments: false,
          tools: true,
          variants: [],
        ),
      ],
      agents: [
        CatalogAgent(id: 'build', mode: 'primary', hidden: false),
        CatalogAgent(id: 'plan', mode: 'primary', hidden: false),
      ],
    );
    api.beforeWrite = () async =>
        throw ApiException('write failed', statusCode: 503);
    await tester.pumpWidget(
      MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: Scaffold(
          body: ModelCatalogView(
            controller: controller,
            applyScope: ModelPickerApplyScope.session,
            sessionID: 'a',
          ),
        ),
      ),
    );
    // The agent is chosen from the footer's menu (no dialog); the choice
    // is staged until the apply action.
    await tester.tap(find.byKey(const Key('model-picker-agent')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('model-picker-agent-plan')));
    await tester.pumpAndSettle();
    expect(api.writes, isEmpty);
    await tester.tap(find.text('Use for this conversation'));
    await tester.pumpAndSettle();
    expect(
      find.text('Model saved. Agent choice was not confirmed. Try again.'),
      findsOneWidget,
    );
    expect(controller.agentForSession('a'), 'build');
    expect(controller.selectedAgent, 'default-agent');
  });

  test(
    'offline v2 metadata remains server-owned and writes require a connection',
    () async {
      final api = SelectionApi();
      final controller = await controllerFor(api);
      controller.api = null;
      expect(controller.modelForSession('a')!.wireName, 'p/one');
      expect(controller.agentForSession('a'), 'build');
      await expectLater(
        controller.selectAgentForSession('a', 'plan'),
        throwsA(isA<ProductException>()),
      );
      expect(controller.selectedAgent, 'default-agent');
    },
  );

  test(
    'a queued prompt cancelled during selection is never delivered',
    () async {
      final api = SelectionApi();
      api.values['a'] = api.values['a']!.copyWith(stagedRevert: null);
      final controller = await controllerFor(api);
      final started = Completer<void>();
      final pending = Completer<void>();
      api.beforeWrite = () {
        started.complete();
        return pending.future;
      };
      await controller.queuePrompt(
        QueuedPrompt(
          id: 'cancel',
          profileID: 'p',
          sessionID: 'a',
          text: 'Do not send',
          modelProviderID: 'p',
          modelID: 'saved',
          agent: 'plan',
          createdAt: DateTime.now().millisecondsSinceEpoch,
        ),
      );
      final flush = controller.flushOfflineQueue();
      await started.future;
      expect(controller.queuedPromptSending('cancel'), isFalse);
      expect(await controller.removeQueuedPrompt('cancel'), isTrue);
      pending.complete();
      await flush;
      expect(api.writes, ['model:a:p/saved:']);
      expect(controller.lastFlushedPromptCount, 0);
    },
  );

  test('selection wait rejects a retired dispatch transport', () async {
    final api = SelectionApi();
    final controller = await controllerFor(api);
    final started = Completer<void>();
    final pending = Completer<void>();
    api.beforeWrite = () {
      started.complete();
      return pending.future;
    };
    final change = controller.selectAgentForSession('a', 'plan');
    await started.future;
    final waiting = controller.waitForSessionSelection('a', expectedApi: api);
    final check = expectLater(waiting, throwsA(isA<ProductException>()));
    controller.api = SelectionApi();
    pending.complete();
    await change;
    await check;
    expect(controller.sessionsById['a']!.selection!.agent, 'build');
  });

  test(
    'v2 reads retain variants and distinguish inherited from unknown',
    () async {
      final mapped = mapApi2Session(
        Api2Session.fromJson({
          'id': 'a',
          'model': {'providerID': 'p', 'id': 'one', 'variant': 'high'},
          'agent': 'plan',
        })!,
      );
      expect(mapped.selection!.variant, 'high');
      final api = SelectionApi();
      api.values['a'] = mapApi2Session(Api2Session(id: 'a'));
      final controller = await controllerFor(api);
      expect(controller.modelForSession('a'), isNull);
      expect(controller.agentForSession('a'), '');
      expect(controller.variantForSession('a'), '');
      expect(controller.selectionForSession('a').modelKnown, isTrue);
      expect(controller.selectionForSession('missing').modelKnown, isFalse);
    },
  );

  test(
    'live selection replaces variant and patches only the matching session',
    () async {
      final api = SelectionApi();
      api.values['a'] = _session('a', variant: 'high');
      final controller = await controllerFor(api);
      event(controller, 'session.model.selected', {
        'sessionID': 'a',
        'model': {'providerID': 'p', 'id': 'other'},
      });
      event(controller, 'session.agent.selected', {
        'sessionID': 'a',
        'agent': 'plan',
      });
      event(controller, 'session.renamed', {
        'sessionID': 'a',
        'title': 'Renamed',
      });
      expect(controller.modelForSession('a')!.wireName, 'p/other');
      expect(controller.variantForSession('a'), '');
      expect(controller.agentForSession('a'), 'plan');
      expect(controller.modelForSession('b')!.wireName, 'p/one');
      expect(controller.sessionsById['a']!.cost, 3);
      expect(controller.sessionsById['a']!.reverted, isTrue);
      expect(controller.sessionsById['a']!.title, 'Renamed');
      expect(controller.modelLibrary.recent, isEmpty);
      event(controller, 'session.moved', {
        'sessionID': 'a',
        'projectID': 'new-project',
        'location': {'directory': '/other'},
      });
      expect(controller.sessionsById['a']!.directory, '/other');
      expect(
        controller.sortedSessions().map((s) => s.id),
        isNot(contains('a')),
      );
      expect(controller.modelForSession('a')!.wireName, 'p/other');
    },
  );

  test(
    'a model event cannot establish an unhydrated agent or clear with malformed data',
    () async {
      final api = SelectionApi();
      final pending = Completer<Session>();
      final controller = await controllerFor(api);
      api.read = (_) => pending.future;
      event(controller, 'session.model.selected', {
        'sessionID': 'unloaded',
        'model': {'providerID': 'p', 'id': 'remote'},
      });
      expect(controller.selectionForSession('unloaded').agentKnown, isFalse);
      event(controller, 'session.model.selected', {'sessionID': 'unloaded'});
      expect(controller.modelForSession('unloaded')!.wireName, 'p/remote');
      pending.complete(_session('unloaded', model: 'remote', agent: 'plan'));
      await controller.ensureSession('unloaded');
      expect(controller.agentForSession('unloaded'), 'plan');
    },
  );

  test(
    'cold start phone selection stays unknown before its gateway exists',
    () async {
      final c = await controllerFor(SelectionApi());
      expect(c.displayModelForSession('a')?.modelID, 'one');
      await Future<void>.delayed(Duration.zero);
      final offline = OfflinePhoneSelectionController(c.store);
      addTearDown(offline.dispose);
      expect(offline.api, isNull);
      expect(offline.selectionForSession('a').modelKnown, isFalse);
      expect(offline.modelForSession('a'), isNull);
      expect(offline.displayModelForSession('a')?.modelID, 'one');
      expect(offline.displayModelForSession('b')?.modelID, 'one');
    },
  );

  for (final v2 in [false, true]) {
    test(
      'cold start ${v2 ? "v2" : "v1"} status does not discard selection hydration',
      () async {
        final api = SelectionApi();
        final c = await controllerFor(api);
        final pending = Completer<Session>();
        api.read = (_) => pending.future;
        final read = c.ensureSession('a');
        final status = <String, dynamic>{
          'sessionID': 'a',
          'status': {'type': 'busy'},
        };
        if (v2) {
          event(c, 'session.status', status);
        } else {
          c.handleEventForTesting(
            EventEnvelope(type: 'session.status', properties: status),
          );
        }
        pending.complete(_session('a', model: 'fresh'));
        await read;
        expect(c.busySessions, contains('a'));
        expect(c.modelForSession('a')?.modelID, 'fresh');
      },
    );
  }

  test('pending metadata cannot overwrite a newer remote selection', () async {
    final api = SelectionApi();
    final controller = await controllerFor(api);
    final pending = Completer<Session>();
    api.read = (_) => pending.future;
    final read = controller.ensureSession('a');
    event(controller, 'session.agent.selected', {
      'sessionID': 'a',
      'agent': 'remote',
    });
    pending.complete(_session('a'));
    await read;
    expect(controller.agentForSession('a'), 'remote');
  });

  test(
    'intentional writes serialize and confirmation adopts server state',
    () async {
      final api = SelectionApi();
      final controller = await controllerFor(api);
      final pending = Completer<void>();
      final started = Completer<void>();
      api.beforeWrite = () {
        if (!started.isCompleted) started.complete();
        return pending.future;
      };
      final model = controller.selectModelForSession('a', ref('two'));
      final agent = controller.selectAgentForSession('a', 'plan');
      await started.future;
      expect(api.writes, ['model:a:p/two:']);
      expect(controller.sessionSelectionSaving('a'), isTrue);
      pending.complete();
      await Future.wait([model, agent]);
      expect(api.writes, ['model:a:p/two:', 'agent:a:plan']);
      expect(controller.modelForSession('a')!.wireName, 'p/two');
      expect(controller.agentForSession('a'), 'plan');
      expect(controller.sessionSelectionSaving('a'), isFalse);
      await controller.selectModelForSession('a', ref('two'));
      await controller.selectAgentForSession('a', 'plan');
      expect(api.writes, hasLength(2));
      expect(controller.selectedModel!.wireName, 'p/default');
      expect(controller.selectedAgent, 'default-agent');
    },
  );

  test('failed writes retain confirmed selection and allow retry', () async {
    final api = SelectionApi();
    final controller = await controllerFor(api);
    api.beforeWrite = () async =>
        throw ApiException('unavailable', statusCode: 503);
    await expectLater(
      controller.selectModelForSession('a', ref('two')),
      throwsA(isA<ApiException>()),
    );
    expect(controller.modelForSession('a')!.wireName, 'p/one');
    expect(controller.sessionSelectionErrors['a'], contains('unavailable'));
    api.beforeWrite = null;
    await controller.selectModelForSession('a', ref('two'));
    expect(controller.modelForSession('a')!.wireName, 'p/two');
    expect(controller.sessionSelectionErrors, isEmpty);
  });

  test(
    'new sessions receive defaults and offline replay applies its saved snapshot',
    () async {
      final api = SelectionApi();
      // This scenario sends; the shared metadata fixture otherwise has an
      // active revert, which correctly requires explicit resolution first.
      api.values['a'] = api.values['a']!.copyWith(stagedRevert: null);
      final controller = await controllerFor(api);
      await controller.createSession();
      expect(api.createdDefaults!.model!.wireName, 'p/default');
      expect(controller.variantForSession('new'), 'high');
      expect(controller.modelForSession('a')!.wireName, 'p/one');
      await controller.queuePrompt(
        QueuedPrompt(
          id: 'queued',
          profileID: 'p',
          sessionID: 'a',
          text: 'Saved prompt',
          modelProviderID: 'p',
          modelID: 'saved',
          variant: '',
          agent: 'saved-agent',
          createdAt: DateTime.now().millisecondsSinceEpoch,
        ),
      );
      await controller.flushOfflineQueue();
      expect(api.writes, [
        'model:a:p/saved:',
        'agent:a:saved-agent',
        'prompt:a:Saved prompt',
      ]);
      expect(controller.queuedPromptsFor('a'), isEmpty);
    },
  );
}
