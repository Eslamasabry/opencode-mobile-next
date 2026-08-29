import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api2/gateway_mappers.dart';
import 'package:opencode_mobile/api2/models.dart';
import 'package:opencode_mobile/api2/transport.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';

dynamic fixture(String name) =>
    jsonDecode(File('test/fixtures/api2/$name').readAsStringSync());

const sessionID = 'ses_fb534b6a0ffeJ5pNlwiesRdWV2';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('capabilities', () {
    test('exact 25-flag truth per the port matrix', () {
      const caps = api2ServerCapabilities;
      expect(caps.managedWorkspaces, isFalse);
      expect(caps.workspaceWarp, isFalse);
      expect(caps.sessionSteal, isFalse);
      expect(caps.consoleOrganizations, isFalse);
      expect(caps.mcpOAuth, isFalse);
      expect(caps.mcpConfigWrites, isTrue);
      expect(caps.sessionShare, isFalse);
      expect(caps.sessionArchive, isFalse);
      expect(caps.sessionTodos, isFalse);
      expect(caps.messageDelete, isFalse);
      expect(caps.workspaceSymbols, isFalse);
      expect(caps.textSearch, isFalse);
      expect(caps.languageServiceStatus, isFalse);
      expect(caps.formatterStatus, isFalse);
      expect(caps.toolInventory, isFalse);
      expect(caps.experimentalCapabilities, isFalse);
      expect(caps.shellSettings, isFalse);
      expect(caps.remoteUpgrade, isFalse);
      expect(caps.clientDiagnostics, isFalse);
      expect(caps.gitInit, isFalse);
      expect(caps.providerRuntimeRefresh, isFalse);
      expect(caps.configuredProviderFallback, isFalse);
      expect(caps.globalEventStream, isTrue);
      expect(caps.worktreeReset, isFalse);
      expect(caps.legacyQuestionRequests, isFalse);
    });
  });

  group('sessions', () {
    test('maps the captured session page onto the v1 Session shape', () {
      final page = Api2Page.fromJson(
        fixture('sessions_page.json'),
        Api2Session.fromJson,
      );
      final session = mapApi2Session(page.data.first);
      expect(session.id, startsWith('ses_'));
      expect(session.title, 'Repo test strategy overview');
      expect(session.directory, '/home/eslam/Storage/Code/oc_app');
      expect(session.projectID, isNotEmpty);
      expect(session.archived, isFalse);
      expect(session.shareUrl, isNull);
      expect(session.time?.created, isNotNull);
    });
  });

  group('messages → parts round-trip', () {
    List<MessageWithParts> captured() {
      final page = Api2Page.fromJson(
        fixture('messages.json'),
        Api2Message.fromJson,
      );
      return mapApi2Messages(sessionID, page.data);
    }

    test('user message carries text and file attachment parts', () {
      final user = captured().first;
      expect(user.info.role, 'user');
      expect(user.info.sessionID, sessionID);
      final text = user.parts.firstWhere((part) => part.type == 'text');
      expect(text.text, contains('Reply with exactly the word: pong'));
      expect(text.isRenderable, isTrue);
      final file = user.parts.firstWhere((part) => part.type == 'file');
      expect(file.mime, 'text/plain');
      expect(file.filename, 'att.txt');
      expect(file.url, startsWith('data:text/plain;base64,'));
    });

    test('assistant "pong" capture maps reasoning/text/tool content', () {
      final assistant = captured()[1];
      expect(assistant.info.role, 'assistant');
      expect(assistant.info.agent, 'build');
      expect(assistant.info.providerID, 'openai');
      expect(assistant.info.modelID, 'gpt-5.6-sol');
      expect(assistant.info.time?.isDone, isTrue);
      expect(assistant.info.errorText, isNull);

      expect(assistant.parts, hasLength(3));
      expect(assistant.parts[0].type, 'reasoning');
      expect(assistant.parts[0].id, 'reasoning-0');
      expect(assistant.parts[1].type, 'text');
      expect(assistant.parts[1].id, 'text-0');
      expect(assistant.parts[1].text, 'pong.');

      final tool = assistant.parts[2];
      expect(tool.type, 'tool');
      expect(tool.toolName, 'read');
      expect(tool.callID, isNotEmpty);
      expect(tool.id, tool.callID);
      expect(tool.toolState.status, 'completed');
      expect(tool.toolState.input['path'], 'README.md');
      expect(tool.toolState.output, contains('OpenCode for Android'));
    });

    test('single assistant fixture parses tokens and cost', () {
      final message = Api2Message.fromJson(
        Map<String, dynamic>.from(fixture('message_assistant.json')['data']),
      );
      final mapped = mapApi2Message(sessionID, message!);
      expect(mapped.info.role, 'assistant');
      expect(mapped.info.tokens.input, greaterThan(0));
      expect(mapped.info.time?.isDone, isTrue);
    });

    test('paginated fixture pages map without loss of order', () {
      final page1 = Api2Page.fromJson(
        fixture('messages_page1.json'),
        Api2Message.fromJson,
      );
      final page2 = Api2Page.fromJson(
        fixture('messages_page2.json'),
        Api2Message.fromJson,
      );
      final mapped = mapApi2Messages(sessionID, [
        ...page1.data,
        ...page2.data,
      ]);
      expect(mapped, hasLength(page1.data.length + page2.data.length));
      for (final message in mapped) {
        expect(message.info.id, startsWith('msg_'));
        expect(message.info.sessionID, sessionID);
      }
    });

    test('shell message maps to a v1 tool part', () {
      final message = Api2Message.fromJson({
        'id': 'msg_shell',
        'type': 'shell',
        'time': {'created': 1, 'completed': 2},
        'shellID': 'sh_1',
        'command': 'ls -la',
        'status': 'exited',
        'exit': 0,
        'output': {'output': 'README.md\nlib\n', 'truncated': false},
      });
      final mapped = mapApi2Message(sessionID, message!);
      expect(mapped.info.role, 'assistant');
      final part = mapped.parts.single;
      expect(part.type, 'tool');
      expect(part.toolName, 'shell');
      expect(part.toolState.status, 'completed');
      expect(part.toolState.input['command'], 'ls -la');
      expect(part.toolState.output, contains('README.md'));
    });

    test('failed shell maps to a tool error state', () {
      final message = Api2Message.fromJson({
        'id': 'msg_shell2',
        'type': 'shell',
        'time': {'created': 1, 'completed': 2},
        'command': 'false',
        'status': 'exited',
        'exit': 1,
        'output': {'output': 'boom', 'truncated': false},
      });
      final part = mapApi2Message(sessionID, message!).parts.single;
      expect(part.toolState.status, 'error');
      expect(part.toolState.output, contains('boom'));
    });

    test('switch markers and system-ish variants become hidden parts', () {
      for (final json in [
        {
          'id': 'msg_a',
          'type': 'agent-switched',
          'time': {'created': 1},
          'agent': 'plan',
          'previous': 'build',
        },
        {
          'id': 'msg_b',
          'type': 'model-switched',
          'time': {'created': 1},
          'model': {'id': 'm2', 'providerID': 'p'},
        },
        {
          'id': 'msg_c',
          'type': 'location-switched',
          'time': {'created': 1},
          'location': {'directory': '/tmp/x'},
        },
        {
          'id': 'msg_d',
          'type': 'system',
          'time': {'created': 1},
          'text': 'context update',
        },
        {
          'id': 'msg_e',
          'type': 'skill',
          'time': {'created': 1},
          'skill': 'sk',
          'name': 'My Skill',
          'text': 'skill body',
        },
        {
          'id': 'msg_f',
          'type': 'synthetic',
          'time': {'created': 1},
          'text': 'synthetic body',
        },
        {'id': 'msg_g', 'type': 'brand-new-variant', 'time': {'created': 1}},
      ]) {
        final mapped = mapApi2Message(
          sessionID,
          Api2Message.fromJson(Map<String, dynamic>.from(json))!,
        );
        expect(mapped.info.role, 'user', reason: json['type'].toString());
        expect(mapped.parts.single.synthetic, isTrue);
        expect(mapped.parts.single.isRenderable, isFalse);
      }
    });

    test('completed compaction becomes an assistant summary message', () {
      final mapped = mapApi2Message(
        sessionID,
        Api2Message.fromJson({
          'id': 'msg_comp',
          'type': 'compaction',
          'time': {'created': 1, 'completed': 2},
          'status': 'completed',
          'reason': 'manual',
          'summary': 'We did things.',
        })!,
      );
      expect(mapped.info.role, 'assistant');
      expect(mapped.parts.single.text, 'We did things.');
      expect(mapped.parts.single.isRenderable, isTrue);
    });

    test('tool error state carries the structured message', () {
      final state = Api2ToolState.fromJson({
        'status': 'error',
        'input': {'path': 'x'},
        'error': {'type': 'ToolError', 'message': 'file missing'},
      });
      final mapped = mapApi2ToolState(state, toolName: 'read');
      expect(mapped.status, 'error');
      expect(mapped.output, contains('file missing'));
    });

    test('tool file results surface as v1 output files', () {
      final state = Api2ToolState.fromJson({
        'status': 'completed',
        'input': {},
        'content': [
          {'type': 'text', 'text': 'wrote image'},
          {
            'type': 'file',
            'uri': 'file:///tmp/shot.png',
            'mime': 'image/png',
            'name': 'shot.png',
          },
        ],
      });
      final mapped = mapApi2ToolState(state, toolName: 'screenshot');
      expect(mapped.status, 'completed');
      expect(mapped.output, 'wrote image');
      expect(mapped.outputFiles, hasLength(1));
      expect(mapped.outputFiles.single.isImage, isTrue);
      expect(mapped.outputFiles.single.displayName, 'shot.png');
    });

    test('streaming tool input maps to the v1 pending preview', () {
      final state = Api2ToolState.fromJson({
        'status': 'streaming',
        'input': '{"pa',
      });
      final mapped = mapApi2ToolState(state);
      expect(mapped.status, 'pending');
      expect(mapped.inputJson, '{"pa');
    });
  });

  group('permissions', () {
    test('maps the v2 request onto the v1 shape', () {
      final request = Api2PermissionRequest.fromJson({
        'id': 'per_1',
        'sessionID': sessionID,
        'action': 'bash',
        'resources': ['git push*'],
        'save': ['git push*'],
        'metadata': {'command': 'git push'},
        'source': {'type': 'tool', 'messageID': 'msg_1', 'id': 'call_1'},
        'message': 'wants to push',
      });
      final mapped = mapApi2PermissionRequest(request!);
      expect(mapped.id, 'per_1');
      expect(mapped.sessionID, sessionID);
      expect(mapped.permission, 'bash');
      expect(mapped.patterns, ['git push*']);
      expect(mapped.always, ['git push*']);
      expect(mapped.tool?.messageID, 'msg_1');
      expect(mapped.tool?.callID, 'call_1');
    });

    test('reply strings map onto the v2 wire enum', () {
      expect(mapPermissionReply('once'), Api2PermissionReply.once);
      expect(mapPermissionReply('allow'), Api2PermissionReply.once);
      expect(mapPermissionReply('always'), Api2PermissionReply.always);
      expect(mapPermissionReply('reject'), Api2PermissionReply.reject);
      expect(mapPermissionReply('deny'), Api2PermissionReply.reject);
      expect(mapPermissionReply('never'), Api2PermissionReply.reject);
      expect(
        () => mapPermissionReply('maybe'),
        throwsA(isA<ProductException>()),
      );
    });
  });

  group('providers & catalog', () {
    test('builds the v1 ProvidersResponse from the captured catalogs', () {
      final providers = [
        for (final item in (fixture('providers.json')['data'] as List))
          Api2ProviderInfo.fromJson(Map<String, dynamic>.from(item as Map))!,
      ];
      final models = [
        for (final item in (fixture('models_sample.json')['data'] as List))
          Api2ModelInfo.fromJson(Map<String, dynamic>.from(item as Map))!,
      ];
      final defaultModel = Api2ModelInfo.fromJson(
        Map<String, dynamic>.from(fixture('model_default.json')['data'] as Map),
      );
      final response = mapApi2Providers(
        providers: providers,
        models: models,
        defaultModel: defaultModel,
      );
      expect(response.providers, isNotEmpty);
      expect(response.defaultProviderID, isNotNull);
      expect(response.defaultModelID, isNotNull);

      final withModels = response.providers.firstWhere(
        (provider) => provider.modelIDs.isNotEmpty,
      );
      final data = withModels.modelData[withModels.modelIDs.first]!;
      expect(data['capabilities'], isA<Map>());
      expect(data['limit'], isA<Map>());
      // Variants are reshaped from the v2 list to the v1 keyed map so the
      // existing catalog derivation finds reasoningEffort options.
      final variants = data['variants'];
      if (variants is Map && variants.isNotEmpty) {
        expect((variants.values.first as Map)['body'], isA<Map>());
      }
    });

    test('maps catalog models with variant options', () {
      final models = [
        for (final item in (fixture('models_sample.json')['data'] as List))
          Api2ModelInfo.fromJson(Map<String, dynamic>.from(item as Map))!,
      ];
      final withVariants = models.firstWhere(
        (model) => model.variants.isNotEmpty,
      );
      final catalog = mapApi2CatalogModel(withVariants);
      expect(catalog.providerID, isNotEmpty);
      expect(catalog.contextLimit, greaterThan(0));
      expect(catalog.variants, isNotEmpty);
      expect(
        catalog.variants.any((variant) => variant.reasoningEffort != null),
        isTrue,
      );
    });

    test('maps agents from the captured agent list', () {
      final agents = [
        for (final item in (fixture('agents.json')['data'] as List))
          Api2AgentInfo.fromJson(Map<String, dynamic>.from(item as Map))!,
      ];
      final build = agents.firstWhere((agent) => agent.id == 'build');
      expect(mapApi2Agent(build).name, 'build');
    });
  });

  group('files & vcs', () {
    test('maps fs entries with trailing-slash directories', () {
      final entries = [
        for (final item in (fixture('fs_list.json')['data'] as List))
          Api2FsEntry.fromJson(Map<String, dynamic>.from(item as Map))!,
      ];
      final directory = entries.firstWhere((entry) => entry.isDirectory);
      final node = mapApi2FsEntry(directory);
      expect(node.isDir, isTrue);
      expect(node.path.endsWith('/'), isFalse);
      expect(node.name, isNotEmpty);
    });

    test('maps vcs diff and status rows', () {
      final diff = mapVcsDiffJson({
        'file': 'lib/a.dart',
        'patch': '--- a\n+++ b',
        'additions': 3,
        'deletions': 1,
        'status': 'modified',
      });
      expect(diff.file, 'lib/a.dart');
      expect(diff.counts, (added: 3, removed: 1));

      final status = mapVcsStatusJson({
        'file': 'lib/a.dart',
        'status': 'modified',
        'additions': 3,
        'deletions': 1,
      });
      expect(status.path, 'lib/a.dart');
      expect(status.additions, 3);
    });
  });

  group('errors & health', () {
    test('maps the v2 error hierarchy to ApiException', () {
      final error = mapApi2Error(
        const Api2RequestError(
          'GET /session/x failed (HTTP 404): Session not found',
          statusCode: 404,
          tag: 'SessionNotFoundError',
        ),
      );
      expect(error, isA<ApiException>());
      expect(error.statusCode, 404);
      expect(error.errorTag, 'SessionNotFoundError');
    });

    test('maps health', () {
      final health = mapApi2Health(
        Api2Health(healthy: true, version: '0.0.0-beta-18600'),
      );
      expect(health.healthy, isTrue);
      expect(health.version, '0.0.0-beta-18600');
    });
  });
}
