import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/domain/chat_feed.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/paseo/chat_feed_source.dart';
import 'package:opencode_mobile/paseo/gateway.dart';
import 'package:opencode_mobile/paseo/transport.dart';

import 'paseo_gateway_test.dart' show FakeDaemon, agentJson, writePermission;

void main() {
  final fixtures =
      jsonDecode(
            File(
              'test/fixtures/paseo/native_questions_0_9_2.json',
            ).readAsStringSync(),
          )
          as Map<String, dynamic>;
  Map<String, dynamic> request(String kind) =>
      jsonDecode(jsonEncode(fixtures[kind])) as Map<String, dynamic>;
  late FakeDaemon daemon;
  late PaseoGateway gateway;
  late List<EventEnvelope> events;
  late Map<String, dynamic> agent;
  Future<void> settle() =>
      Future<void>.delayed(const Duration(milliseconds: 30));
  Future<void> push(Map<String, dynamic> value) async {
    daemon.push('agent_permission_request', {
      'agentId': 'a1',
      'request': value,
    });
    await settle();
  }

  Map<String, dynamic> response() =>
      daemon.of('agent_permission_response').last['response']
          as Map<String, dynamic>;

  setUp(() async {
    daemon = FakeDaemon();
    agent = agentJson('a1');
    daemon.handlers['fetch_agents_request'] = (_) => (
      'fetch_agents_response',
      {
        'entries': [
          {'agent': agent},
        ],
        'pageInfo': {'nextCursor': null, 'prevCursor': null, 'hasMore': false},
      },
    );
    gateway = PaseoGateway(
      directory: '/work/app',
      transport: PaseoTransport(
        endpoint: 'ws://127.0.0.1:6767',
        socketFactory: (_, _) async => daemon,
      ),
    );
    events = [];
    gateway.openEventChannel(onEvent: events.add, onStatus: (_) {});
    await gateway.sessions();
  });
  tearDown(() {
    gateway.close();
  });

  test(
    'Claude native questions become typed questions, never permissions',
    () async {
      await push(request('claude'));
      expect(gateway.capabilities.legacyQuestionRequests, isTrue);
      expect(await gateway.pendingPermissions(), isEmpty);
      final question = PendingQuestion.fromJson(
        (await gateway.pendingQuestionsV2()).single,
      );
      expect(question.id, 'permission-question');
      expect(question.sessionID, 'a1');
      expect(question.prompts.first.title, 'Stack');
      expect(question.prompts.first.question, 'Which stack should I use?');
      expect(
        question.prompts.first.choices.first.description,
        'Shared language',
      );
      expect(question.prompts.first.custom, isTrue);
      expect(question.prompts.last.multiple, isTrue);
      expect(events.where((e) => e.type == 'question.v2.asked'), hasLength(1));
      expect(events.where((e) => e.type == 'permission.asked'), isEmpty);
    },
  );

  test(
    'Claude answers use exact question keys and string multi/custom values',
    () async {
      await push(request('claude'));
      await gateway.answerQuestionV2('a1', 'permission-question', [
        ['Rust'],
        ['Search', 'History', 'Offline'],
      ]);
      expect(response(), {
        'behavior': 'allow',
        'updatedInput': {
          'answers': {
            'Which stack should I use?': 'Rust',
            'Which features do you need?': 'Search, History, Offline',
          },
        },
      });
      expect(await gateway.pendingQuestionsV2(), isEmpty);
      expect(events.last.type, 'question.v2.replied');
      await push(request('claude'));
      expect(await gateway.pendingQuestionsV2(), isEmpty);
    },
  );

  for (final provider in ['pi', 'omp']) {
    test(
      '$provider uses header keys and preserves optional comment with empty answer',
      () async {
        agent['provider'] = provider;
        await gateway.sessions();
        await push({
          ...request('pi'),
          'provider': provider,
          'name': '$provider ask_user',
        });
        final question = PendingQuestion.fromJson(
          (await gateway.pendingQuestionsV2()).single,
        );
        expect(question.prompts, hasLength(2));
        expect(question.prompts.last.optional, isTrue);
        expect(question.prompts.last.custom, isTrue);
        await gateway.answerQuestionV2('a1', 'ui-question', [
          ['Rust'],
          [],
        ]);
        expect(response(), {
          'behavior': 'allow',
          'updatedInput': {
            'answers': {'Response': 'Rust', 'Comment': ''},
          },
        });
      },
    );
  }

  test(
    'plan displays full plan and approval never restores bypass mode',
    () async {
      await push(request('plan'));
      final question = PendingQuestion.fromJson(
        (await gateway.pendingQuestionsV2()).single,
      );
      expect(
        question.prompts.single.question,
        request('plan')['input']['plan'],
      );
      expect(question.prompts.single.choices.map((c) => c.label), [
        'Approve',
        'Keep planning',
      ]);
      expect(question.prompts.single.custom, isFalse);
      await gateway.answerQuestionV2('a1', 'permission-plan', [
        ['Approve'],
      ]);
      expect(response(), {
        'behavior': 'allow',
        'selectedActionId': 'implement',
      });
    },
  );

  test(
    'keep planning denies without interrupt or persistent permission',
    () async {
      await push(request('plan'));
      await gateway.answerQuestionV2('a1', 'permission-plan', [
        ['Keep planning'],
      ]);
      expect(response(), {'behavior': 'deny', 'selectedActionId': 'reject'});
      expect(events.last.type, 'question.v2.rejected');
    },
  );

  test(
    'question reject sends deny; legacy permission replies cannot approve it',
    () async {
      await push(request('claude'));
      await expectLater(
        gateway.respondPermission('permission-question', 'once'),
        throwsA(isA<PaseoFailure>()),
      );
      await gateway.rejectQuestionV2('a1', 'permission-question');
      expect(response(), {'behavior': 'deny'});
      expect(events.last.type, 'question.v2.rejected');
    },
  );

  test(
    'wrong session, duplicate, invalid values and disconnected answers never send',
    () async {
      await push(request('claude'));
      await expectLater(
        gateway.answerQuestionV2('other', 'permission-question', [
          ['Dart'],
          ['Search'],
        ]),
        throwsA(isA<PaseoFailure>()),
      );
      await expectLater(
        gateway.answerQuestionV2('a1', 'permission-question', [
          ['Dart', 'Kotlin'],
          ['Search'],
        ]),
        throwsA(isA<PaseoFailure>()),
      );
      await gateway.transport.close();
      await expectLater(
        gateway.answerQuestionV2('a1', 'permission-question', [
          ['Dart'],
          ['Search'],
        ]),
        throwsA(isA<PaseoFailure>()),
      );
      expect(daemon.of('agent_permission_response'), isEmpty);
    },
  );

  test(
    'snapshots recover pending questions and retire resolved ones',
    () async {
      agent['pendingPermissions'] = [request('claude')];
      await gateway.sessions();
      expect(await gateway.pendingQuestionsV2(), hasLength(1));
      agent['pendingPermissions'] = <Object>[];
      await gateway.sessions();
      expect(await gateway.pendingQuestionsV2(), isEmpty);
      expect(
        events.where((e) => e.type == 'question.v2.replied'),
        hasLength(1),
      );
    },
  );

  test('malformed question is not turned into a generic approval', () async {
    await push({
      ...request('claude'),
      'input': {
        'questions': [
          {'header': 'Bad'},
        ],
      },
    });
    expect(await gateway.pendingQuestionsV2(), isEmpty);
    expect(await gateway.pendingPermissions(), isEmpty);
    await push(writePermission('write'));
    expect((await gateway.pendingPermissions()).single.id, 'write');
  });

  test(
    'feed reports live questions as needs-you and clears after reply',
    () async {
      final source = PaseoChatFeedSource(gateway);
      addTearDown(source.dispose);
      await source.refreshChatFeed();
      var changes = 0;
      final subscription = source.changes.listen((_) => changes++);
      addTearDown(subscription.cancel);
      await push(request('claude'));
      expect(source.chatFeed().items.single.status, ChatStatus.needsYou);
      expect(changes, greaterThan(0));
      await gateway.rejectQuestionV2('a1', 'permission-question');
      await settle();
      expect(source.chatFeed().items.single.status, ChatStatus.idle);
    },
  );
  test(
    'unknown or mismatched provider questions never become approvals',
    () async {
      await push({...request('claude'), 'provider': 'codex'});
      expect(await gateway.pendingQuestionsV2(), isEmpty);
      expect(await gateway.pendingPermissions(), isEmpty);
      expect(daemon.of('agent_permission_response'), isEmpty);
    },
  );

  test(
    'Claude duplicate headers keep distinct full-question answer keys',
    () async {
      final raw = request('claude');
      raw['input']['questions'][1]['header'] = 'Stack';
      await push(raw);
      await gateway.answerQuestionV2('a1', 'permission-question', [
        ['Dart'],
        ['History'],
      ]);
      expect(response()['updatedInput']['answers'], {
        'Which stack should I use?': 'Dart',
        'Which features do you need?': 'History',
      });
    },
  );

  test(
    'options-free native input permits custom text and optional empty string',
    () async {
      agent['provider'] = 'pi';
      await gateway.sessions();
      final raw = request('pi');
      raw['input'] = {
        'questions': [
          {
            'header': 'Response',
            'question': 'Optional note',
            'options': [],
            'allowEmpty': true,
          },
        ],
      };
      await push(raw);
      final prompt = PendingQuestion.fromJson(
        (await gateway.pendingQuestionsV2()).single,
      ).prompts.single;
      expect(prompt.custom, isTrue);
      expect(prompt.optional, isTrue);
      await gateway.answerQuestionV2('a1', 'ui-question', [[]]);
      expect(response()['updatedInput']['answers'], {'Response': ''});
    },
  );

  test(
    'required answer cannot be empty and closed choices cannot be invented',
    () async {
      final raw = request('claude');
      raw['input']['questions'][0]['allowOther'] = false;
      await push(raw);
      await expectLater(
        gateway.answerQuestionV2('a1', 'permission-question', [
          [],
          ['Search'],
        ]),
        throwsA(isA<PaseoFailure>()),
      );
      await expectLater(
        gateway.answerQuestionV2('a1', 'permission-question', [
          ['Unknown'],
          ['Search'],
        ]),
        throwsA(isA<PaseoFailure>()),
      );
      expect(daemon.of('agent_permission_response'), isEmpty);
    },
  );

  test('resolved and scope-changed question replies never send', () async {
    await push(request('claude'));
    daemon.push('agent_permission_resolved', {
      'agentId': 'a1',
      'requestId': 'permission-question',
    });
    await settle();
    expect(await gateway.pendingQuestionsV2(), isEmpty);
    await expectLater(
      gateway.rejectQuestionV2('a1', 'permission-question'),
      throwsA(isA<PaseoFailure>()),
    );
    await push({...request('claude'), 'id': 'second'});
    gateway.setLocation(directory: '/work/other');
    expect(await gateway.pendingQuestionsV2(), isEmpty);
    await expectLater(
      gateway.rejectQuestionV2('a1', 'second'),
      throwsA(isA<PaseoFailure>()),
    );
    expect(daemon.of('agent_permission_response'), isEmpty);
  });
  test(
    'isOther alias permits custom text and malformed flags never approve',
    () async {
      final raw = request('claude');
      raw['input']['questions'][0].remove('allowOther');
      raw['input']['questions'][0]['isOther'] = true;
      await push(raw);
      final question = PendingQuestion.fromJson(
        (await gateway.pendingQuestionsV2()).single,
      );
      expect(question.prompts.first.custom, isTrue);
      await gateway.answerQuestionV2('a1', 'permission-question', [
        ['Rust'],
        ['Search'],
      ]);
      final malformed = request('claude');
      malformed['id'] = 'malformed';
      malformed['input']['questions'][0]['isOther'] = 'true';
      await push(malformed);
      expect(await gateway.pendingQuestionsV2(), isEmpty);
    },
  );

  test(
    'changed question revision replaces the visible prompt, rejects old closed choice',
    () async {
      final raw = request('claude');
      raw['input']['questions'][0]['allowOther'] = false;
      await push(raw);
      final changed = jsonDecode(jsonEncode(raw)) as Map<String, dynamic>;
      changed['input']['questions'][0]['question'] = 'Which language instead?';
      changed['input']['questions'][0]['options'] = [
        {'label': 'Rust'},
      ];
      await push(changed);
      final question = PendingQuestion.fromJson(
        (await gateway.pendingQuestionsV2()).single,
      );
      expect(question.prompts.first.question, 'Which language instead?');
      await expectLater(
        gateway.answerQuestionV2('a1', 'permission-question', [
          ['Dart'],
          ['Search'],
        ]),
        throwsA(isA<PaseoFailure>()),
      );
      expect(daemon.of('agent_permission_response'), isEmpty);
      expect(events.where((e) => e.type == 'question.v2.asked'), hasLength(2));
    },
  );
  test(
    'malformed same-session replacement invalidates the previous question',
    () async {
      await push(request('claude'));
      final malformed = request('claude');
      malformed['input']['questions'][0]['multiSelect'] = 'yes';
      await push(malformed);
      expect(await gateway.pendingQuestionsV2(), isEmpty);
      await expectLater(
        gateway.answerQuestionV2('a1', 'permission-question', [
          ['Dart'],
          ['Search'],
        ]),
        throwsA(isA<PaseoFailure>()),
      );
      expect(daemon.of('agent_permission_response'), isEmpty);
    },
  );

  test(
    'a colliding request in another session cannot invalidate a question',
    () async {
      await push(request('claude'));
      daemon.push('agent_update', {'agent': agentJson('a2')});
      await settle();
      daemon.push('agent_permission_request', {
        'agentId': 'a2',
        'request': {
          ...request('claude'),
          'input': {'questions': []},
        },
      });
      await settle();
      final question = PendingQuestion.fromJson(
        (await gateway.pendingQuestionsV2()).single,
      );
      expect(question.sessionID, 'a1');
      await gateway.answerQuestionV2('a1', 'permission-question', [
        ['Dart'],
        ['Search'],
      ]);
      expect(response()['behavior'], 'allow');
    },
  );
}
