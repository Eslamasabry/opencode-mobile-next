part of '../phone_agents_controller_test.dart';

void _nativeQuestionControllerTests() {
  testWidgets(
    'native questions hydrate the chat controller and answer as choices',
    (tester) async {
      final fixtures =
          jsonDecode(
                File(
                  'test/fixtures/paseo/native_questions_0_9_2.json',
                ).readAsStringSync(),
              )
              as Map;
      final native = Map<String, dynamic>.from(fixtures['claude'] as Map);
      final w = await _world(tester);
      final c = w.controller;
      w.state.runtimes = {'claude': _ready('claude')};
      w.state.agents = [
        {
          ..._agent('c1', _project),
          'pendingPermissions': [native],
        },
      ];
      await c.rememberLastUsedProject(_project);
      await c.refreshAgentRows();
      await c.refreshChatFeed();
      final row = c.chatFeed().items.firstWhere(
        (r) => r.sourceId == 'paseo:$_project',
      );
      expect(row.status, ChatStatus.needsYou);
      final route = await c.openChatFeedItem(row);
      await tester.pump();
      final backend = c.backendForConversation(route.sessionID)!;
      await backend.refreshPendingQuestions();
      final question = backend.questionForSession(route.sessionID);
      expect(question, isNotNull);
      expect(backend.permissions, isEmpty);
      expect(backend.capabilities.legacyQuestionRequests, isTrue);
      expect(question!.prompts, hasLength(2));
      final socket =
          w.host.sockets[w.host.gateways.indexOf(backend.api as PaseoGateway)];
      expect(socket.of('agent_permission_response'), isEmpty);
      await backend.answerQuestion(question.id, [
        ['Kotlin'],
        ['Search', 'History'],
      ], expectedRequest: backend.questionIdentity(question));
      expect(socket.of('agent_permission_response').single['response'], {
        'behavior': 'allow',
        'updatedInput': {
          'answers': {
            'Which stack should I use?': 'Kotlin',
            'Which features do you need?': 'Search, History',
          },
        },
      });
      expect(backend.questionForSession(route.sessionID), isNull);
      c.dispose();
      await tester.pump();
    },
  );

  testWidgets('question identity includes native optionality', (tester) async {
    final w = await _world(tester);
    final c = w.controller;
    void publish(bool optional) => c.handleEventForTesting(
      EventEnvelope(
        type: 'question.v2.asked',
        properties: {
          'id': 'same-id',
          'sessionID': 'same-session',
          'questions': [
            {
              'header': 'Comment',
              'question': 'Any notes?',
              'custom': true,
              'optional': optional,
              'options': [],
            },
          ],
        },
      ),
    );
    publish(false);
    final requiredQuestion = c.questions['same-id']!;
    expect(requiredQuestion.prompts.single.optional, isFalse);
    final identity = c.questionIdentity(requiredQuestion);
    publish(true);
    expect(c.questions['same-id']!.prompts.single.optional, isTrue);
    expect(c.isRequestPending(identity), isFalse);
    c.dispose();
    await tester.pump();
  });
}
