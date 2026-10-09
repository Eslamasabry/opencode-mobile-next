// The AI setup page shows the server's configuration, which holds provider
// keys, tool-server environments and headers, commands and agent prompts.
// None of those may reach the screen, whatever shape the server nests them in.
import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/setup_assistant.dart';

void main() {
  const markers = [
    'MARKER_PROVIDER_KEY',
    'MARKER_ENV_VALUE',
    'MARKER_HEADER_VALUE',
    'MARKER_COMMAND_BODY',
    'MARKER_AGENT_PROMPT',
    'MARKER_TOKEN',
    'MARKER_NESTED_LIST',
  ];

  test('a server configuration shows none of its secrets', () {
    final config = <String, Object?>{
      'model': 'anthropic/claude',
      'provider': {
        'anthropic': {
          'options': {'apiKey': 'MARKER_PROVIDER_KEY', 'baseURL': 'https://x'},
          'models': {
            'claude': {'name': 'Claude'},
          },
        },
      },
      'mcp': {
        'docs': {
          'type': 'local',
          'command': ['node', 'MARKER_COMMAND_BODY'],
          'environment': {'API': 'MARKER_ENV_VALUE'},
          'headers': {'Authorization': 'Bearer MARKER_HEADER_VALUE'},
        },
      },
      'agent': {
        'review': {'prompt': 'MARKER_AGENT_PROMPT', 'description': 'Reviews'},
      },
      'share': {'accessToken': 'MARKER_TOKEN'},
      'plugins': [
        {
          'secretList': ['MARKER_NESTED_LIST'],
        },
      ],
    };
    final shown = jsonEncode(setupRedact(config));
    for (final marker in markers) {
      expect(shown, isNot(contains(marker)), reason: marker);
    }
    // What is safe to read stays readable.
    expect(shown, contains('anthropic/claude'));
    expect(shown, contains('Reviews'));
  });

  test('a changed secret is masked by the name it sits under', () {
    for (final name in ['apiKey', 'accessToken', 'Authorization-secret']) {
      expect(
        setupRedact('MARKER_PROVIDER_KEY', name),
        isNot(contains('MARKER')),
      );
    }
  });
}
