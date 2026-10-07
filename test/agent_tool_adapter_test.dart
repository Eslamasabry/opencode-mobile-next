import 'dart:convert';
import 'dart:io';

import 'package:opencode_mobile/domain/genui/gen_ui.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('every adapter keeps the registry contract', () {
    test('ids and Paseo providers are unique', () {
      final ids = AgentToolAdapters.all.map((agent) => agent.id).toList();
      expect(ids.toSet().length, ids.length);
      final providers = [
        for (final agent in AgentToolAdapters.all) ?agent.paseoProvider,
      ];
      expect(providers.toSet().length, providers.length);
    });

    test('an agent with tools names them and stores them, run by the user '
        'its config format expects', () {
      for (final agent in AgentToolAdapters.all) {
        expect(
          agent.naming == null,
          agent.config == null,
          reason: '${agent.id}: naming and config come together',
        );
        if (agent.config == McpConfigFormat.claudeCli) {
          expect(agent.runsAs, AgentRunUser.agentUser, reason: agent.id);
        }
        if (agent.config == McpConfigFormat.openCodeV1 ||
            agent.config == McpConfigFormat.openCodeV2) {
          expect(agent.runsAs, AgentRunUser.root, reason: agent.id);
        }
      }
    });

    test('only an agent with tools can be qualified or pre-allow cards', () {
      for (final agent in AgentToolAdapters.all) {
        if (agent.cardsQualified || agent.preAllowsCards) {
          expect(agent.supportsTools, isTrue, reason: agent.id);
        }
      }
    });

    test('a plan or question tool only exists with native answers', () {
      for (final agent in AgentToolAdapters.all) {
        if (agent.planToolName != null || agent.questionToolName != null) {
          expect(agent.answersNativeQuestions, isTrue, reason: agent.id);
        }
      }
    });
  });

  test('each naming scheme gives the name captured from its runtime', () {
    expect(AgentToolAdapter.claude.cardShowName, 'mcp__oc-ui__show');
    expect(AgentToolAdapter.openCode1.cardShowName, 'oc-ui_show');
    expect(AgentToolAdapter.openCode2.cardShowName, 'oc-ui_show');
    expect(AgentToolAdapter.pi.cardShowName, isNull);
    expect(AgentToolAdapters.cardShowNames, {'mcp__oc-ui__show', 'oc-ui_show'});
  });

  test('OpenCode product names distinguish the runtime generations', () {
    expect(AgentToolAdapter.openCode1.displayName, 'OpenCode 1');
    expect(AgentToolAdapter.openCode2.displayName, 'OpenCode 2');
  });

  test('a Paseo provider finds its agent; anything else finds none', () {
    expect(
      AgentToolAdapters.forPaseoProvider('claude'),
      AgentToolAdapter.claude,
    );
    expect(AgentToolAdapters.forPaseoProvider('omp'), AgentToolAdapter.ohMyPi);
    expect(AgentToolAdapters.forPaseoProvider('gemini'), isNull);
    expect(AgentToolAdapters.forPaseoProvider(null), isNull);
    expect(AgentToolAdapters.forPaseoProvider(42), isNull);
    expect(AgentToolAdapters.forPaseoProvider(['claude']), isNull);
  });

  test('tools are registered only for agents that support them; only '
      'Claude Code and captured OC1 are qualified for cards', () {
    expect(AgentToolAdapters.withTools, AgentToolAdapter.values);
    expect(AgentToolAdapters.all.where((agent) => agent.cardsQualified), [
      AgentToolAdapter.claude,
      AgentToolAdapter.openCode1,
    ]);
  });

  test('BA6 captured OC1 direct call parses only the accepted valid card', () {
    final parts =
        jsonDecode(
              File(
                'test/fixtures/genui/runtime_opencode1_tool.json',
              ).readAsStringSync(),
            )
            as List;
    const scope = GenUiScope(
      profileID: 'fixture',
      sourceId: 'opencode',
      directory: '/root/projects',
    );
    final invalid = Part.fromJson(Map<String, dynamic>.from(parts[0] as Map));
    expect(
      genUiFromPart(
        invalid,
        scope: scope,
        sessionID: 'session',
        messageID: 'message',
      ),
      isNull,
    );
    final valid = Part.fromJson(Map<String, dynamic>.from(parts[1] as Map));
    final parsed = genUiFromPart(
      valid,
      scope: scope,
      sessionID: 'session',
      messageID: 'message',
    );
    expect(parsed, isA<GenUiParsed>());
    final card = (parsed! as GenUiParsed).card;
    expect(card.id, 'oc-gaps-probe');
    expect(card.ask, isA<GenUiConfirmAsk>());
    expect(valid.toolName, AgentToolAdapter.openCode1.cardShowName);
  });

  test('ids stay the former enum names (they name helper folders)', () {
    expect(AgentToolAdapter.values.map((agent) => agent.name), [
      'claude',
      'openCode1',
      'openCode2',
    ]);
    expect(AgentToolAdapters.byId('openCode2'), AgentToolAdapter.openCode2);
    expect(AgentToolAdapters.byId('nope'), isNull);
  });
}
