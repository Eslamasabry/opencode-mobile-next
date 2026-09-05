import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api2/events.dart';
import 'package:opencode_mobile/api2/gateway_events.dart';
import 'package:opencode_mobile/api2/gateway_mappers.dart';
import 'package:opencode_mobile/api2/models.dart';
import 'package:opencode_mobile/state/session_read_state.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'support/complete_message_history.dart';

class _Preferences implements SharedPreferences {
  bool fail = true;
  String? saved;
  @override
  String? getString(String key) => null;
  @override
  Future<bool> setString(String key, String value) async {
    if (fail) throw StateError('Storage unavailable');
    saved = value;
    return true;
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _EmptyHistory extends OpenCodeApi with CompleteMessageHistory {
  _EmptyHistory() : super(baseUrl: 'http://127.0.0.1:9');
}

void main() {
  test(
    'read cache failure retains watermarks and does not poison later writes',
    () async {
      final preferences = _Preferences();
      final store = SessionReadStore(preferences);
      await store.record('profile', 'first', 100);
      await store.drain('profile');
      expect(store.viewed('profile', 'first'), 100);
      preferences.fail = false;
      await store.record('profile', 'second', 200);
      expect(jsonDecode(preferences.saved!), {'first': 100, 'second': 200});
    },
  );

  test(
    'partial move preserves absent metadata; explicit local destination clears workspace',
    () {
      final adapter = Api2EventAdapter();
      final partial = adapter
          .adapt(
            Api2EventEnvelope.fromJson({
              'type': 'session.moved',
              'data': {'sessionID': 'ses_1'},
            }),
          )
          .single;
      expect(partial.properties['info'], {'id': 'ses_1'});
      final local = adapter
          .adapt(
            Api2EventEnvelope.fromJson({
              'type': 'session.moved',
              'data': {
                'sessionID': 'ses_1',
                'location': {'directory': '/work/local'},
                'subpath': null,
              },
            }),
          )
          .single;
      expect(local.properties['info'], {
        'id': 'ses_1',
        'directory': '/work/local',
        'workspaceID': null,
        'path': null,
      });
    },
  );

  test(
    'unexpected instruction event IDs cannot synthesize transcript messages',
    () {
      for (final id in [null, 'msg_existing', 'evt_', 'unrecognized']) {
        final mapped = Api2EventAdapter().adapt(
          Api2EventEnvelope.fromJson({
            'id': id,
            'type': 'session.instructions.updated',
            'data': {'sessionID': 'ses_1', 'text': 'private instruction'},
          }),
        );
        expect(mapped.map((e) => e.type), ['session.instructions.updated']);
        expect(
          jsonEncode(mapped.single.properties),
          isNot(contains('private')),
        );
      }
    },
  );

  test(
    'instruction notices stay redacted when description lacks punctuation',
    () {
      final message = mapApi2Message(
        'ses_1',
        Api2SystemMessage(
          id: 'msg_1',
          time: Api2MessageTime(created: 1),
          text: 'private instruction',
          description: 'Instructions updated mobile.note',
        ),
      );
      expect(message.parts.single.text, 'Session instructions updated.');
      expect(message.parts.single.toolName, 'instructions');
    },
  );

  test(
    'complete-history fixture defaults to empty without recursion',
    () async {
      final api = _EmptyHistory();
      addTearDown(api.close);
      expect((await api.messagePage('ses_1')).items, isEmpty);
      expect((await api.messagePage('ses_1', cursor: 'older')).items, isEmpty);
    },
  );
}
