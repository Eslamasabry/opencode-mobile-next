part of '../phone_agents_controller_test.dart';

void _listQuestionTests() {
  Map<String, dynamic> fixture(String provider) => Map<String, dynamic>.from(
    (jsonDecode(
              File(
                'test/fixtures/paseo/native_questions_0_9_2.json',
              ).readAsStringSync(),
            )
            as Map)[provider]
        as Map,
  );

  Future<({_World world, ChatFeedItem row, FakePaseoSocket socket})> setup(
    WidgetTester? tester, {
    String provider = 'claude',
  }) async {
    final w = await _world(tester);
    w.state.runtimes = {'claude': _ready('claude'), provider: _ready(provider)};
    w.state.providers = [_provider(provider)];
    w.state.agents = [
      {
        ..._agent('c1', _project, provider: provider),
        'pendingPermissions': [fixture(provider)],
      },
    ];
    await w.controller.rememberLastUsedProject(_project);
    await w.controller.refreshAgentRows();
    await w.controller.refreshChatFeed();
    final row = w.controller.chatFeed().items.firstWhere(
      (r) => r.sourceId == 'paseo:$_project',
    );
    final index = w.host.gateways.indexWhere((g) => g.directory == _project);
    return (world: w, row: row, socket: w.host.sockets[index]);
  }

  for (final provider in ['claude', 'pi']) {
    testWidgets('list question answers $provider without opening a backend', (
      tester,
    ) async {
      final x = await setup(tester, provider: provider);
      final c = x.world.controller;
      final count = x.world.host.gateways.length;
      final api = c.api;
      final location = c.locationRevision;
      expect(c.connectionForRow(x.row), isNull);
      expect(x.row.status, ChatStatus.needsYou);
      final question = c.questionForFeedItem(x.row)!;
      final request = c.questionIdentityForFeedItem(x.row, question);
      expect(c.isRequestPending(request), isTrue);
      var changes = 0;
      c.addListener(() => changes++);
      await Future.wait([
        c.answerQuestionForFeedItem(x.row, [
          ['Kotlin'],
          if (provider == 'claude') ['Search'] else [],
        ], expectedRequest: request),
        c.rejectQuestionForFeedItem(x.row, expectedRequest: request),
      ]);
      await tester.pump();
      final response = x.socket.of('agent_permission_response').single;
      expect(response['agentId'], 'c1');
      expect(response['requestId'], question.id);
      expect(response['response'], {
        'behavior': 'allow',
        'updatedInput': {
          'answers': provider == 'claude'
              ? {
                  'Which stack should I use?': 'Kotlin',
                  'Which features do you need?': 'Search',
                }
              : {'Response': 'Kotlin', 'Comment': ''},
        },
      });
      expect(c.questionForFeedItem(x.row), isNull);
      expect(c.isRequestPending(request), isFalse);
      expect(changes, greaterThan(0));
      expect(c.connectionForRow(x.row), isNull);
      expect(x.world.host.gateways.length, count);
      expect(c.api, same(api));
      expect(c.locationRevision, location);
      expect(x.world.state.resumed, isEmpty);
      for (final type in [
        'create_agent_request',
        'resume_agent_request',
        'fetch_agent_timeline_request',
      ]) {
        expect(x.socket.of(type), isEmpty);
      }
      c.dispose();
      await tester.pump();
    });
  }

  testWidgets('list question rejects on the feed source', (tester) async {
    final x = await setup(tester);
    final c = x.world.controller;
    final question = c.questionForFeedItem(x.row)!;
    await c.rejectQuestionForFeedItem(
      x.row,
      expectedRequest: c.questionIdentityForFeedItem(x.row, question),
    );
    expect(x.socket.of('agent_permission_response').single['response'], {
      'behavior': 'deny',
    });
    expect(c.questionForFeedItem(x.row), isNull);
    c.dispose();
    await tester.pump();
  });

  testWidgets('list question fences changed revision and disconnected source', (
    tester,
  ) async {
    final x = await setup(tester);
    final c = x.world.controller;
    final question = c.questionForFeedItem(x.row)!;
    final request = c.questionIdentityForFeedItem(x.row, question);
    final changed = fixture('claude');
    ((changed['input'] as Map)['questions'] as List).first['question'] =
        'Changed question';
    x.socket.push('agent_permission_request', {
      'agentId': 'c1',
      'request': changed,
    });
    await tester.pump();
    expect(c.isRequestPending(request), isFalse);
    await c.rejectQuestionForFeedItem(x.row, expectedRequest: request);
    expect(x.socket.of('agent_permission_response'), isEmpty);
    final next = c.questionForFeedItem(x.row)!;
    final nextRequest = c.questionIdentityForFeedItem(x.row, next);
    await x.socket.close();
    await tester.pump();
    expect(c.isRequestPending(nextRequest), isFalse);
    expect(c.questionForFeedItem(x.row), isNull);
    await c.rejectQuestionForFeedItem(x.row, expectedRequest: nextRequest);
    expect(x.socket.of('agent_permission_response'), isEmpty);
    c.dispose();
    await tester.pump();
  });
  testWidgets('list question source identity isolates colliding IDs', (
    tester,
  ) async {
    final x = await setup(tester);
    final c = x.world.controller;
    const other = '/root/projects/other';
    x.world.state.agents.add({
      ..._agent('c1', other),
      'pendingPermissions': [fixture('claude')],
    });
    await c.store.setLocation('local', directory: other);
    await c.refreshAgentRows();
    await c.refreshChatFeed();
    final otherRow = c.chatFeed().items.firstWhere(
      (r) => r.sourceId == 'paseo:$other',
    );
    final question = c.questionForFeedItem(x.row)!;
    final request = c.questionIdentityForFeedItem(x.row, question);
    final otherQuestion = c.questionForFeedItem(otherRow)!;
    final otherRequest = c.questionIdentityForFeedItem(otherRow, otherQuestion);
    expect(otherQuestion.id, question.id);
    expect(
      () => c.questionIdentityForFeedItem(otherRow, question),
      throwsArgumentError,
    );
    expect(
      () => c.rejectQuestionForFeedItem(otherRow, expectedRequest: request),
      throwsArgumentError,
    );
    // Same ID in OpenCode is unrelated to either phone source.
    c.handleEventForTesting(
      EventEnvelope(
        type: 'question.v2.asked',
        properties: {
          'id': question.id,
          'sessionID': question.sessionID,
          'questions': [
            {'header': 'OC', 'question': 'OpenCode only', 'options': []},
          ],
        },
      ),
    );
    await c.rejectQuestionForFeedItem(x.row, expectedRequest: request);
    expect(c.isRequestPending(otherRequest), isTrue);
    expect(c.questions[question.id]!.prompts.single.title, 'OC');
    final otherSocket = x
        .world
        .host
        .sockets[x.world.host.gateways.indexWhere((g) => g.directory == other)];
    expect(otherSocket.of('agent_permission_response'), isEmpty);
    // Existing request widgets may use the generic API with the bound identity.
    await c.rejectQuestion(otherQuestion.id, expectedRequest: otherRequest);
    expect(otherSocket.of('agent_permission_response'), hasLength(1));
    expect(x.socket.of('agent_permission_response'), hasLength(1));
    c.dispose();
    await tester.pump();
  });

  test(
    'list question cannot reuse an identity after source replacement',
    () async {
      final x = await setup(null);
      final c = x.world.controller;
      final question = c.questionForFeedItem(x.row)!;
      final request = c.questionIdentity(question);
      expect(c.isRequestPending(request), isTrue);
      await c.closePhoneAgentsForSignInReset();
      expect(c.isRequestPending(request), isFalse);
      expect(c.questionForFeedItem(x.row), isNull);
      await c.rejectQuestionForFeedItem(x.row, expectedRequest: request);
      expect(x.socket.of('agent_permission_response'), isEmpty);
      c.dispose();
      await Future<void>.delayed(Duration.zero);
    },
  );

  testWidgets(
    'list question equivalent refresh keeps identity but resolution retires it',
    (tester) async {
      final x = await setup(tester);
      final c = x.world.controller;
      final question = c.questionForFeedItem(x.row)!;
      final request = c.questionIdentityForFeedItem(x.row, question);
      await c.refreshChatFeed();
      expect(c.isRequestPending(request), isTrue);
      x.socket.push('agent_permission_resolved', {
        'agentId': 'c1',
        'requestId': question.id,
      });
      await tester.pump();
      expect(c.isRequestPending(request), isFalse);
      x.socket.push('agent_permission_request', {
        'agentId': 'c1',
        'request': fixture('claude'),
      });
      await tester.pump();
      await c.rejectQuestionForFeedItem(x.row, expectedRequest: request);
      expect(x.socket.of('agent_permission_response'), isEmpty);
      c.dispose();
      await tester.pump();
    },
  );
  testWidgets(
    'list question routes the selected OpenCode source without waking it',
    (tester) async {
      final w = await _world(tester);
      final c = w.controller;
      await c.selectLocation(directory: _project);
      w.oc.global = [_ocRow('oc-question', _project)];
      await c.refreshChatFeed();
      c.handleEventForTesting(
        EventEnvelope(
          type: 'question.v2.asked',
          properties: {
            'id': 'oc-request',
            'sessionID': 'oc-question',
            'questions': [
              {
                'header': 'OC',
                'question': 'Choose',
                'options': [
                  {'label': 'Yes'},
                ],
              },
            ],
          },
        ),
      );
      final row = c.chatFeed().items.single;
      final revision = c.locationRevision;
      final question = c.questionForFeedItem(row)!;
      final request = c.questionIdentityForFeedItem(row, question);
      await expectLater(
        c.rejectQuestion('wrong-id', expectedRequest: request),
        throwsArgumentError,
      );
      await c.answerQuestionForFeedItem(row, [
        ['Yes'],
      ], expectedRequest: request);
      final sent = w.oc.questionAnswers.single;
      expect(sent.$1, 'oc-question');
      expect(sent.$2, 'oc-request');
      expect(sent.$3, [
        ['Yes'],
      ]);
      expect(c.isRequestPending(request), isFalse);
      expect(c.locationRevision, revision);
      expect(c.questionForFeedItem(row), isNull);
      c.dispose();
      await tester.pump();
    },
  );
}
