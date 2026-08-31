import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api2/models.dart';

dynamic fixture(String name) =>
    jsonDecode(File('test/fixtures/api2/$name').readAsStringSync());

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('parses the captured session list page with cursors', () {
    final page = Api2Page.fromJson(fixture('sessions_page.json'), Api2Session.fromJson);
    expect(page.data, hasLength(3));
    expect(page.hasNext, isTrue);
    expect(page.hasPrevious, isTrue);
    expect(page.nextCursor, isNotEmpty);

    final first = page.data.first;
    expect(first.id, startsWith('ses_'));
    expect(first.title, 'Repo test strategy overview');
    expect(first.model?.providerID, 'openai');
    expect(first.model?.id, 'gpt-5.6-sol');
    expect(first.tokens.input, 59422);
    expect(first.tokens.cacheRead, 78336);
    expect(first.time.created, isNotNull);
    expect(first.directory, '/home/dev/projects/oc_app');
    expect(first.archived, isFalse);

    final child = page.data[1];
    expect(child.parentID, isNotNull);
    expect(child.agent, 'explore');
  });

  test('parses the captured session create response', () {
    final session = Api2Session.fromJson(
      Map<String, dynamic>.from(fixture('session_create.json')['data'] as Map),
    );
    expect(session, isNotNull);
    expect(session!.title, 'api2 fixture probe');
    expect(session.projectID, isNotEmpty);
  });

  test('parses the captured message list into the typed union', () {
    final page = Api2Page.fromJson(fixture('messages.json'), Api2Message.fromJson);
    expect(page.data.length, greaterThanOrEqualTo(3));

    final user = page.data.first as Api2UserMessage;
    expect(user.text, contains('pong'));
    expect(user.files, hasLength(1));
    expect(user.files.first.mime, 'text/plain');
    expect(user.files.first.sourceType, 'inline');
    expect(user.files.first.name, 'att.txt');
    expect(utf8.decode(base64Decode(user.files.first.data!)), 'tiny attachment');

    final assistant = page.data[1] as Api2AssistantMessage;
    expect(assistant.agent, 'build');
    expect(assistant.model?.providerID, 'openai');
    expect(assistant.finish, 'tool-calls');
    expect(assistant.completed, isTrue);
    expect(assistant.tokens?.input, greaterThan(0));

    final reasoning = assistant.content.whereType<Api2ReasoningContent>();
    expect(reasoning, isNotEmpty);

    final text = assistant.content.whereType<Api2TextContent>().first;
    expect(text.text, 'pong.');

    final tool = assistant.content.whereType<Api2ToolCallContent>().first;
    expect(tool.name, 'read');
    expect(tool.id, startsWith('call_'));
    final state = tool.state as Api2ToolCompleted;
    expect(state.input['path'], 'README.md');
    expect(state.content.whereType<Api2ToolResultText>().first.text,
        contains('OpenCode for Android'));
    expect(tool.time?.ran, isNotNull);
  });

  test('parses a single captured assistant message', () {
    final message = Api2Message.fromJson(
      Map<String, dynamic>.from(fixture('message_assistant.json')['data'] as Map),
    );
    expect(message, isA<Api2AssistantMessage>());
    final assistant = message as Api2AssistantMessage;
    expect(assistant.content, isNotEmpty);
    expect(assistant.text, isNotEmpty);
  });

  test('parses the captured prompt inbox receipt', () {
    final item = Api2InboxItem.fromJson(
      Map<String, dynamic>.from(fixture('prompt_receipt.json')['data'] as Map),
    );
    expect(item, isNotNull);
    expect(item!.id, startsWith('msg_'));
    expect(item.sessionID, startsWith('ses_'));
    expect(item.type, 'user');
    expect(item.delivery, Api2Delivery.steer);
    expect(item.promptText, contains('pong'));
    expect(item.timeCreated, isNotNull);
  });

  test('parses captured model, provider, agent, and catalog listings', () {
    final models = (fixture('models_sample.json')['data'] as List)
        .map((j) => Api2ModelInfo.fromJson(Map<String, dynamic>.from(j as Map)))
        .whereType<Api2ModelInfo>()
        .toList();
    expect(models, isNotEmpty);

    final fallback = Api2ModelInfo.fromJson(
      Map<String, dynamic>.from(fixture('model_default.json')['data'] as Map),
    )!;
    expect(fallback.id, 'gpt-5.6-sol');
    expect(fallback.providerID, 'openai');
    expect(fallback.capabilities.tools, isTrue);
    expect(fallback.capabilities.input, contains('image'));
    expect(fallback.variants, contains('high'));
    expect(fallback.limit.context, 1050000);
    expect(fallback.ref(variant: 'high').toString(), 'openai/gpt-5.6-sol#high');

    final providers = (fixture('providers.json')['data'] as List)
        .map((j) => Api2ProviderInfo.fromJson(Map<String, dynamic>.from(j as Map)))
        .whereType<Api2ProviderInfo>()
        .toList();
    expect(providers.map((p) => p.id), contains('openai'));

    final agents = (fixture('agents.json')['data'] as List)
        .map((j) => Api2AgentInfo.fromJson(Map<String, dynamic>.from(j as Map)))
        .whereType<Api2AgentInfo>()
        .toList();
    expect(agents.map((a) => a.id), containsAll(['build', 'plan']));
    final subagents = agents.where((a) => a.mode == 'subagent');
    expect(subagents.any((a) => !a.selectable), isTrue);

    final commands = (fixture('commands.json')['data'] as List)
        .map((j) => Api2Command.fromJson(Map<String, dynamic>.from(j as Map)))
        .whereType<Api2Command>()
        .toList();
    expect(commands.map((c) => c.name), contains('review'));

    final skills = (fixture('skills_sample.json')['data'] as List)
        .map((j) => Api2Skill.fromJson(Map<String, dynamic>.from(j as Map)))
        .whereType<Api2Skill>()
        .toList();
    expect(skills, isNotEmpty);
    expect(skills.first.id, isNotEmpty);
  });

  test('parses captured location and fs listings', () {
    final location = Api2Location.fromJson(fixture('location.json'))!;
    expect(location.directory, '/home/dev/projects/oc_app');
    expect(location.project?.id, isNotEmpty);
    expect(location.project?.canonical, location.directory);

    final entries = (fixture('fs_list.json')['data'] as List)
        .map((j) => Api2FsEntry.fromJson(Map<String, dynamic>.from(j as Map)))
        .whereType<Api2FsEntry>()
        .toList();
    expect(entries.first.isDirectory, isTrue);
    expect(entries.any((e) => e.path == 'lib/main.dart' && !e.isDirectory),
        isTrue);
  });

  test('unknown message types and enum values never throw', () {
    final message = Api2Message.fromJson({
      'id': 'msg_future',
      'type': 'hologram',
      'time': {'created': 1},
      'brandNewField': {'nested': true},
    });
    expect(message, isA<Api2UnknownMessage>());
    expect(message!.raw['brandNewField'], isNotNull);

    final oddAssistant = Api2Message.fromJson({
      'id': 'msg_a',
      'type': 'assistant',
      'finish': 'brand-new-reason',
      'content': [
        {'type': 'text', 'text': 'hi'},
        {'type': 'video', 'uri': 'x'},
        'not-a-map',
        {'type': 'tool', 'id': 'c1', 'name': 't', 'state': {'status': 'paused'}},
      ],
    }) as Api2AssistantMessage;
    expect(oddAssistant.finish, 'brand-new-reason');
    expect(oddAssistant.content, hasLength(3));
    expect(oddAssistant.content[1], isA<Api2UnknownContent>());
    final oddTool = oddAssistant.content[2] as Api2ToolCallContent;
    expect(oddTool.state, isA<Api2ToolStateUnknown>());

    expect(Api2SessionOutcome.parse('vanished'), Api2SessionOutcome.unknown);
    expect(Api2Delivery.parse('teleport'), Api2Delivery.unknown);
    expect(Api2FormFieldType.parse('hologram'), Api2FormFieldType.unknown);
    expect(Api2FormStatus.parse(42), Api2FormStatus.unknown);
    expect(Api2Session.fromJson({'title': 'no id'}), isNull);
    expect(Api2Message.fromJson({'type': 'user'}), isNull);
  });

  test('parses permission requests and replies use wire names', () {
    final request = Api2PermissionRequest.fromJson({
      'id': 'per_1',
      'sessionID': 'ses_1',
      'action': 'bash',
      'resources': ['git push*'],
      'save': ['git push*'],
      'metadata': {'command': 'git push'},
      'source': {'type': 'tool', 'messageID': 'msg_1', 'id': 'call_1'},
      'message': 'wants to push',
    })!;
    expect(request.action, 'bash');
    expect(request.resources, ['git push*']);
    expect(request.source?.messageID, 'msg_1');
    expect(Api2PermissionReply.always.wire, 'always');
    expect(Api2PermissionReply.reject.wire, 'reject');
    expect(Api2PermissionEffect.parse('deny'), Api2PermissionEffect.deny);
    expect(Api2PermissionEffect.parse('later'), Api2PermissionEffect.unknown);
  });

  test('parses forms with the six field types and when conditions', () {
    final form = Api2FormInfo.fromJson({
      'id': 'frm_1',
      'sessionID': 'global',
      'title': 'Connect',
      'fields': [
        {
          'key': 'env',
          'type': 'string',
          'required': true,
          'options': [
            {'value': 'prod', 'label': 'Production'},
            {'value': 'dev'},
          ],
          'custom': true,
        },
        {'key': 'retries', 'type': 'integer', 'minimum': 0, 'maximum': 5},
        {'key': 'ratio', 'type': 'number', 'default': 0.5},
        {'key': 'confirm', 'type': 'boolean', 'default': false},
        {
          'key': 'tags',
          'type': 'multiselect',
          'options': [
            {'value': 'a'},
            {'value': 'b'},
          ],
          'minItems': 1,
          'when': [
            {'key': 'confirm', 'op': 'eq', 'value': true},
          ],
        },
        {'key': 'oauth', 'type': 'external', 'url': 'https://example.com'},
        {'key': 'mystery', 'type': 'quantum'},
      ],
    })!;
    expect(form.sessionID, 'global');
    expect(form.fields, hasLength(7));
    expect(form.fields[0].type, Api2FormFieldType.string);
    expect(form.fields[0].required, isTrue);
    expect(form.fields[0].options, hasLength(2));
    expect(form.fields[0].custom, isTrue);
    expect(form.fields[1].type, Api2FormFieldType.integer);
    expect(form.fields[1].maximum, 5);
    expect(form.fields[2].defaultValue, 0.5);
    expect(form.fields[3].type, Api2FormFieldType.boolean);
    expect(form.fields[4].type, Api2FormFieldType.multiselect);
    expect(form.fields[5].type, Api2FormFieldType.external);
    expect(form.fields[5].url, 'https://example.com');
    expect(form.fields[6].type, Api2FormFieldType.unknown);

    final tags = form.fields[4];
    expect(tags.activeFor({}), isFalse);
    expect(tags.activeFor({'confirm': false}), isFalse);
    expect(tags.activeFor({'confirm': true}), isTrue);

    final includes = Api2FormCondition(key: 'tags', op: 'eq', value: 'a');
    expect(includes.holds({'tags': ['a', 'b']}), isTrue);
    expect(includes.holds({'tags': ['b']}), isFalse);

    final state = Api2FormState.fromJson({
      'status': 'answered',
      'answer': {'env': 'prod'},
    });
    expect(state.status, Api2FormStatus.answered);
    expect(state.answer?['env'], 'prod');
  });

  test('parses the captured error envelope shape', () {
    final j = fixture('error_not_found.json') as Map;
    expect(j['_tag'], 'SessionNotFoundError');
    expect(j['sessionID'], 'ses_doesnotexist');
  });
}
