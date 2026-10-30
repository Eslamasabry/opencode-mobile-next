import 'dart:convert';

import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart' show Locale;
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations_en.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/background/widget_snapshot.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:opencode_mobile/ui/kit/kit_redact.dart';

Session _session(String id, {String? title, int? updated}) => Session(
  id: id,
  title: title,
  time: SessionTime(created: 1, updated: updated),
);

void main() {
  group('widgetAskFor', () {
    final en = AppLocalizationsEn();
    test('an outside write names the file and its folder', () {
      expect(
        widgetAskFor(
          permission: 'external_directory',
          patterns: const ['/root/*'],
          tool: 'write',
          target: '/root/outside-test.txt',
          strings: en,
        ),
        {
          'file': 'outside-test.txt',
          'kind': 'write',
          'q': 'Write outside this project?',
          'path': '/root/outside-test.txt',
          'dir': '/root',
        },
      );
    });

    test('a command is text: never split into a folder and a file', () {
      final ask = widgetAskFor(
        permission: 'external_directory',
        patterns: const ['/root/*'],
        tool: 'shell',
        target: 'touch /root/outside-test.txt',
        strings: en,
      );
      expect(ask['kind'], 'run');
      expect(ask['file'], 'touch /root/outside-test.txt');
      // The folder it reaches into comes from the request's own pattern.
      expect(ask['dir'], '/root');
      expect(ask.containsKey('path'), isFalse);
    });

    test('a shell ask without a seen call uses its pattern as the command', () {
      final ask = widgetAskFor(
        permission: 'shell',
        patterns: const ['touch /root/x'],
        strings: en,
      );
      expect(ask['kind'], 'run');
      expect(ask['file'], 'touch /root/x');
      expect(ask.containsKey('dir'), isFalse);
    });

    test('only a folder pattern gives a folder, never an invented file', () {
      final ask = widgetAskFor(
        permission: 'external_directory',
        patterns: const ['/root/*'],
        strings: en,
      );
      expect(ask['dir'], '/root');
      expect(ask.containsKey('file'), isFalse);
    });
  });

  TestWidgetsFlutterBinding.ensureInitialized();

  test('preview excludes inline reasoning and retains the following reply', () {
    expect(
      safeWidgetPreview('Intro <think>private reasoning</think>Done.'),
      'Intro Done.',
    );
    expect(
      safeWidgetPreview('< THINKING >private\nreasoning< / Thinking >Reply'),
      'Reply',
    );
    expect(
      safeWidgetPreview(
        '<think>outer<thinking>inner</thinking>private</think>Reply',
      ),
      'Reply',
    );
    expect(
      safeWidgetPreview(
        '<think>first</think>One<thinking>second</thinking>Two',
      ),
      'One Two',
    );
  });

  test('preview hides unclosed reasoning and orphan closing prefixes', () {
    expect(
      safeWidgetPreview('Public reply <think>still private'),
      'Public reply',
    );
    expect(safeWidgetPreview('<thinking>still private'), '');
    expect(safeWidgetPreview('</think>done'), 'done');
    expect(
      safeWidgetPreview('truncated reasoning</THINKING>Real reply'),
      'Real reply',
    );
  });

  test('preview shows plain words, never raw Markdown', () {
    expect(
      safeWidgetPreview('Done — `sleep 40` completed.'),
      'Done — sleep 40 completed.',
    );
    expect(
      safeWidgetPreview(
        '## Summary\n- **Fixed** the _bug_ in [the docs](https://x.test/a)\n'
        '1. ~~old~~ new *step*\n```dart\nprint(1);\n```',
      ),
      'Summary Fixed the bug in the docs old new step print(1);',
    );
    expect(
      safeWidgetPreview('snake_case_name and 2 * 3 * 4'),
      'snake_case_name and 2 * 3 * 4',
    );
  });

  test('project line is a real folder name or nothing', () {
    expect(safeWidgetProject('/tmp/run/project'), '');
    expect(safeWidgetProject('/home/me/oc_app'), 'oc_app');
    expect(safeWidgetProject(''), '');
  });

  test('preview leaves unrelated markup and code alone', () {
    expect(
      safeWidgetPreview('Use `<div>` and `a < b`; <thinker> is a name.'),
      'Use <div> and a < b; <thinker> is a name.',
    );
    expect(
      safeWidgetPreview('&lt;think&gt; is escaped.'),
      '&lt;think&gt; is escaped.',
    );
  });

  test('bounded assistant reads use the same inline reasoning boundary', () {
    expect(
      safeWidgetAssistantPreview([
        MessageWithParts(
          info: MessageInfo(id: 'a1', sessionID: 's1', role: 'assistant'),
          parts: [
            Part(type: 'text', text: '<think>private</think>Final reply'),
          ],
        ),
      ]),
      'Final reply',
    );
  });

  test('bounded read preview uses assistant prose only', () {
    MessageWithParts message(String role, List<Part> parts) => MessageWithParts(
      info: MessageInfo(id: role, sessionID: 's1', role: role),
      parts: parts,
    );
    expect(
      safeWidgetAssistantPreview([
        message('assistant', [Part(type: 'text', text: 'Old reply')]),
        message('assistant', [
          Part(type: 'reasoning', text: 'Private reasoning'),
          Part(type: 'tool', text: 'Private output'),
          Part(type: 'text', text: 'Synthetic prompt', synthetic: true),
          Part(type: 'text', text: 'Latest reply password=secret-value'),
        ]),
        message('user', [Part(type: 'text', text: 'Private user prompt')]),
      ]),
      'Latest reply password=•••',
    );
  });

  Future<(WidgetSessionSnapshot, SharedPreferences, List<int>)> harness({
    bool isAndroid = true,
  }) async {
    SharedPreferences.setMockInitialValues({});
    final prefs = await SharedPreferences.getInstance();
    final refreshes = <int>[];
    final snapshot = WidgetSessionSnapshot(
      prefs: prefs,
      isAndroid: isAndroid,
      refreshNative: () async => refreshes.add(1),
    );
    return (snapshot, prefs, refreshes);
  }

  test(
    'the Status widget gets each server as a name, a state and since when',
    () async {
      final (snapshot, prefs, _) = await harness();
      await snapshot.update(
        sessions: const [],
        busySessions: const {},
        connected: true,
        servers: [
          {'name': 'This phone', 'state': 'online', 'phone': true},
          {
            'name': 'Laptop',
            'state': 'offline',
            'since': 1700000000000,
            'url': 'http://10.0.0.2:4096',
          },
          {'name': 'Studio', 'state': 'weird'},
        ],
      );
      final servers =
          jsonDecode(
                prefs.getString(WidgetSessionSnapshot.prefsKey)!,
              )['servers']
              as List;
      expect(servers, [
        {'name': 'This phone', 'state': 'online', 'phone': true},
        {'name': 'Laptop', 'state': 'offline', 'since': 1700000000000},
        // Anything unknown is not claimed to be online.
        {'name': 'Studio', 'state': 'offline'},
      ]);
    },
  );

  test(
    'a permission ask carries only its question, kind, file and folder',
    () async {
      final (snapshot, prefs, _) = await harness();
      KitRedact.registerKnownSecret('registered-private-secret');
      addTearDown(KitRedact.clearKnownSecrets);
      await snapshot.update(
        sessions: [_session('s1')],
        busySessions: {},
        connected: true,
        details: {
          's1': {
            'requestID': 'perm-1',
            'ask': {
              'kind': 'write',
              'q': 'Write outside this project?',
              'file': 'outside-test.txt',
              'path': '/root/outside-test.txt',
              'dir': '/root',
              'toolInput': 'registered-private-secret',
            },
          },
        },
      );
      final row =
          (jsonDecode(
                        prefs.getString(WidgetSessionSnapshot.prefsKey)!,
                      )['sessions']
                      as List)
                  .single
              as Map;
      expect(row['ask'], {
        'kind': 'write',
        'q': 'Write outside this project?',
        'file': 'outside-test.txt',
        'path': '/root/outside-test.txt',
        'dir': '/root',
      });
      expect(jsonEncode(row), isNot(contains('registered-private-secret')));
    },
  );

  test(
    'project and assistant excerpt are redacted bounded display fields',
    () async {
      final (snapshot, prefs, _) = await harness();
      KitRedact.registerKnownSecret('registered-private-secret');
      addTearDown(KitRedact.clearKnownSecrets);
      await snapshot.update(
        sessions: [_session('s1')],
        busySessions: {},
        connected: false,
        details: {
          's1': {
            'project': '/private/routing/My project',
            'model': 'big-pickle',
            'preview':
                'Done registered-private-secret password=synthetic-password\n'
                'مرحبا\u061c\u200e\u200f\u202e\u2067\u0001 ${'😀' * 250}',
            'prompt': 'user-private-text',
            'toolInput': 'tool-private-text',
            'reasoning': 'reasoning-private-text',
            'error': 'error-private-text',
          },
        },
      );
      final raw = prefs.getString(WidgetSessionSnapshot.prefsKey)!;
      final row = (jsonDecode(raw)['sessions'] as List).single as Map;
      expect(row['project'], 'My project');
      expect(row['model'], 'big-pickle');
      expect((row['preview'] as String).runes.length, 240);
      expect(row['preview'], contains('مرحبا'));
      for (final forbidden in [
        '/private/routing',
        'registered-private-secret',
        'synthetic-password',
        'user-private-text',
        'tool-private-text',
        'reasoning-private-text',
        'error-private-text',
        '\u061c',
        '\u200e',
        '\u200f',
        '\u202e',
        '\u2067',
        '\u0001',
        '\n',
      ]) {
        expect(row['preview'], isNot(contains(forbidden)));
        expect(raw, isNot(contains(forbidden)));
      }
    },
  );

  test(
    'snapshot keeps routing private and profile deletion removes target',
    () async {
      final (snapshot, prefs, _) = await harness();
      await snapshot.update(
        sessions: [_session('s1')],
        busySessions: {},
        connected: true,
        profileID: 'server-1',
        scopeToken: 'opaque-scope',
        onDemand: {
          'fingerprint': 'opaque-fingerprint',
          'directory': '/private/project',
          'workspace': 'private-workspace',
        },
      );
      final raw = prefs.getString(WidgetSessionSnapshot.prefsKey)!;
      expect(raw, isNot(contains('/private/project')));
      expect(raw, isNot(contains('private-workspace')));
      expect(jsonDecode(raw)['onDemand'], {
        'fingerprint': 'opaque-fingerprint',
      });
      final target = jsonDecode(
        prefs.getString(WidgetSessionSnapshot.targetKey('server-1'))!,
      );
      expect(target['directory'], '/private/project');
      expect(target['scopeToken'], 'opaque-scope');
      expect(
        await snapshot.clearForProfile('server-1'),
        WidgetSnapshotClear.cleared,
      );
      expect(
        prefs.containsKey(WidgetSessionSnapshot.targetKey('server-1')),
        false,
      );
    },
  );
  test('suspension migrates legacy raw routing out of the snapshot', () async {
    final (snapshot, prefs, _) = await harness();
    await prefs.setString(
      WidgetSessionSnapshot.prefsKey,
      jsonEncode({
        'profileID': 'server-1',
        'scopeToken': 'scope',
        'onDemand': {
          'fingerprint': 'fp',
          'directory': '/private/project',
          'workspace': 'workspace',
        },
      }),
    );
    await snapshot.update(
      sessions: [],
      busySessions: {},
      connected: false,
      profileID: 'server-1',
      preserveForOnDemand: true,
    );
    final raw = prefs.getString(WidgetSessionSnapshot.prefsKey)!;
    expect(jsonDecode(raw)['onDemand'], {'fingerprint': 'fp'});
    expect(raw, isNot(contains('/private/project')));
    expect(
      jsonDecode(
        prefs.getString(WidgetSessionSnapshot.targetKey('server-1'))!,
      )['directory'],
      '/private/project',
    );
  });
  test(
    'writes at most twelve sessions with busy flags and title fallback',
    () async {
      final (snapshot, prefs, refreshes) = await harness();
      await snapshot.update(
        sessions: [
          _session('s1', title: 'Fix auth', updated: 50),
          _session('s2', updated: 40),
          _session('s3', title: 'Refactor', updated: 30),
          _session('s4', title: 'Docs', updated: 20),
          _session('s5', title: 'Fifth', updated: 10),
          _session('s6', title: 'Sixth', updated: 9),
          for (var i = 7; i <= 12; i++)
            _session('s$i', title: 'Row $i', updated: 9 - i),
          _session('s13', title: 'Never shown', updated: -10),
        ],
        busySessions: {'s1', 's3'},
        connected: true,
        profileID: 'server-1',
      );

      final decoded =
          jsonDecode(prefs.getString(WidgetSessionSnapshot.prefsKey)!)
              as Map<String, dynamic>;
      expect(decoded['connected'], isTrue);
      // The widget stamps this onto every row's tap intent so a tap after a
      // profile switch opens the app normally instead of a foreign chat.
      expect(decoded['profileID'], 'server-1');
      final sessions = decoded['sessions'] as List;
      // Enough rows for a Conversations widget scoped to one project.
      expect(sessions, hasLength(WidgetSessionSnapshot.maxSessions));
      expect(WidgetSessionSnapshot.maxSessions, 12);
      expect(sessions[0], {
        'id': 's1',
        'title': 'Fix auth',
        'busy': true,
        'updatedAt': 50,
      });
      expect((sessions[2] as Map)['title'], 'New conversation');
      expect((sessions[2] as Map)['busy'], isFalse);
      // The snapshot never carries prompt text, tool input, or file paths:
      // exactly these four keys per session.
      for (final entry in sessions) {
        expect((entry as Map).keys.toSet(), {
          'id',
          'title',
          'busy',
          'updatedAt',
        });
      }
      expect(refreshes, hasLength(1));
    },
  );

  test(
    'the untitled fallback is the caller\'s words, English if none',
    () async {
      final (snapshot, prefs, _) = await harness();
      await snapshot.update(
        sessions: [_session('s1', updated: 50)],
        busySessions: const {},
        connected: true,
        untitledLabel: 'محادثة بلا عنوان',
      );
      final decoded =
          jsonDecode(prefs.getString(WidgetSessionSnapshot.prefsKey)!)
              as Map<String, dynamic>;
      expect(
        ((decoded['sessions'] as List).single as Map)['title'],
        'محادثة بلا عنوان',
      );
    },
  );

  test('publishes presented titles instead of server placeholders', () async {
    final (snapshot, prefs, _) = await harness();
    await snapshot.update(
      sessions: [
        _session('default', title: 'New session - 2026-10-15T04:39:03.218Z'),
        _session('child', title: 'Child session - 2026-10-15T04:39:03.218Z'),
        _session('custom', title: 'Fix login 2026-10-15T04:39:03.218Z'),
        _session('markup', title: 'Fix auth…<tool_call>private input'),
      ],
      busySessions: const {},
      connected: true,
    );
    final decoded =
        jsonDecode(prefs.getString(WidgetSessionSnapshot.prefsKey)!) as Map;
    expect(
      [for (final row in decoded['sessions'] as List) (row as Map)['title']],
      ['New conversation', 'New conversation', 'Fix login', 'Fix auth'],
    );
  });

  test(
    'presented widget titles use app language and remain redacted',
    () async {
      final (snapshot, prefs, _) = await harness();
      await snapshot.update(
        sessions: [
          _session('default', title: 'New session - 2026-10-15T04:39:03.218Z'),
          _session('empty'),
          _session('custom', title: 'إصلاح تسجيل الدخول'),
          _session('secret', title: 'password=synthetic-password\nTitle'),
        ],
        busySessions: const {},
        connected: true,
        l10n: lookupAppLocalizations(const Locale('ar')),
      );
      final raw = prefs.getString(WidgetSessionSnapshot.prefsKey)!;
      final decoded = jsonDecode(raw) as Map;
      final titles = [
        for (final row in decoded['sessions'] as List) (row as Map)['title'],
      ];
      expect(titles.take(3), [
        'محادثة جديدة',
        'محادثة جديدة',
        'إصلاح تسجيل الدخول',
      ]);
      expect(raw, isNot(contains('synthetic-password')));
      expect(titles.last, contains('Title'));
      expect(titles.last, isNot(contains('\n')));
    },
  );

  test('unchanged payloads neither rewrite nor redraw', () async {
    final (snapshot, _, refreshes) = await harness();
    final sessions = [_session('s1', title: 'Fix auth', updated: 50)];
    await snapshot.update(
      sessions: sessions,
      busySessions: const {},
      connected: true,
    );
    await snapshot.update(
      sessions: sessions,
      busySessions: const {},
      connected: true,
    );
    expect(refreshes, hasLength(1));

    await snapshot.update(
      sessions: sessions,
      busySessions: {'s1'},
      connected: true,
    );
    expect(refreshes, hasLength(2));

    // A profile switch changes the payload, so the widget redraws and its
    // row intents pick up the new discriminator.
    await snapshot.update(
      sessions: sessions,
      busySessions: {'s1'},
      connected: true,
      profileID: 'server-2',
    );
    expect(refreshes, hasLength(3));
  });

  test('non-Android platforms write nothing', () async {
    final (snapshot, prefs, refreshes) = await harness(isAndroid: false);
    await snapshot.update(
      sessions: [_session('s1', title: 'Fix auth', updated: 50)],
      busySessions: const {},
      connected: true,
    );
    expect(prefs.getString(WidgetSessionSnapshot.prefsKey), isNull);
    expect(refreshes, isEmpty);
  });

  group('clearForProfile native answer', () {
    const channel = MethodChannel('oc/background');
    Future<void> answer(Object? value) async {
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, (_) async => value);
      addTearDown(
        () => TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
            .setMockMethodCallHandler(channel, null),
      );
    }

    test('a host with nothing to say still clears the stored copy', () async {
      final (snapshot, prefs, _) = await harness();
      await answer(null);
      await snapshot.update(
        sessions: [_session('s1', title: 'A')],
        busySessions: {},
        connected: true,
        profileID: 'p1',
      );
      expect(await snapshot.clearForProfile('p1'), WidgetSnapshotClear.cleared);
      expect(prefs.getString(WidgetSessionSnapshot.prefsKey), isNull);
    });

    test('an explicit native refusal is a failure', () async {
      final (snapshot, _, _) = await harness();
      await answer({'cleared': false});
      expect(await snapshot.clearForProfile('p1'), WidgetSnapshotClear.failed);
    });
  });

  test('conversations of other projects join the rows, plain and ordered '
      'needs-you, running, newest', () async {
    final (snapshot, prefs, _) = await harness();
    await snapshot.update(
      sessions: [_session('old', title: 'Old here', updated: 100)],
      busySessions: {},
      connected: true,
      details: {
        'old': {'project': 'here', 'preview': 'kept preview'},
      },
      others: const [
        WidgetOtherConversation(
          id: 'new',
          title: 'UI review',
          project: 'IPTV_King',
          updatedAt: 900,
          busy: false,
          needsYou: false,
        ),
        WidgetOtherConversation(
          id: 'run',
          title: 'Running one',
          project: 'IPTV_King',
          updatedAt: 500,
          busy: true,
          needsYou: false,
        ),
        WidgetOtherConversation(
          id: 'ask',
          title: 'Needs you',
          project: '/home/secret/dir',
          updatedAt: 1,
          busy: false,
          needsYou: true,
        ),
      ],
    );
    final decoded =
        jsonDecode(prefs.getString(WidgetSessionSnapshot.prefsKey)!) as Map;
    final rows = (decoded['sessions'] as List).cast<Map>();
    expect(rows.map((r) => r['id']), ['ask', 'run', 'new', 'old']);
    expect(decoded['runningCount'], 1);
    expect(decoded['pendingCount'], 1);
    final foreign = rows.first;
    expect(foreign['project'], 'dir');
    expect(foreign.containsKey('preview'), isFalse);
    expect(foreign.containsKey('requestID'), isFalse);
    expect(foreign['onDemandActions'], isEmpty);
    expect(jsonEncode(rows), isNot(contains('/home/secret')));
    // The active controller's own row keeps its details.
    expect(rows.last['preview'], 'kept preview');
  });

  test('rows name their server and mark what waits on the person', () async {
    final (snapshot, prefs, _) = await harness();
    await snapshot.update(
      sessions: [_session('mine', title: 'Mine', updated: 100)],
      busySessions: {},
      connected: true,
      server: 'This phone',
      details: {
        // A question: it waits on the person but has no request identity.
        'mine': {
          'project': 'neon-breaker',
          'needsYou': true,
          'ask': {'kind': 'other', 'q': 'Which colour?'},
        },
      },
      others: const [
        WidgetOtherConversation(
          id: 'far',
          title: 'Far away',
          project: 'api-server',
          updatedAt: 1,
          busy: false,
          needsYou: true,
          server: 'MacBook',
        ),
      ],
    );
    final decoded =
        jsonDecode(prefs.getString(WidgetSessionSnapshot.prefsKey)!) as Map;
    final rows = {
      for (final r in (decoded['sessions'] as List).cast<Map>()) r['id']: r,
    };
    expect(rows['mine']!['server'], 'This phone');
    expect(rows['mine']!['needsYou'], isTrue);
    expect(rows['mine']!.containsKey('requestID'), isFalse);
    expect((rows['mine']!['ask'] as Map)['q'], 'Which colour?');
    expect(rows['far']!['server'], 'MacBook');
    expect(rows['far']!['needsYou'], isTrue);
    expect(rows['far']!['onDemandActions'], isEmpty);
  });
}
