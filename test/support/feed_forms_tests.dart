part of '../side_connections_test.dart';

class _FormServer extends _Server {
  final formsByDirectory = <String, List<Api2FormInfo>>{};
  final formReads = <String?>[];
  final formReplies = <(String?, String, String, Map<String, dynamic>)>[];
  final formCancels = <(String?, String, String)>[];
  Completer<void>? formGate;
  Completer<void>? replyGate;
  Object? replyError;
  int messageReads = 0;
}

class _FormApi extends _Api {
  _FormApi(_FormServer super.server, super.baseUrl);
  _FormServer get data => server as _FormServer;
  @override
  ServerCapabilities get capabilities => const ServerCapabilities(forms: true);

  @override
  Future<List<Api2FormInfo>> pendingForms() async {
    data.formReads.add(directory);
    final snapshot = List<Api2FormInfo>.of(
      data.formsByDirectory[directory] ?? [],
    );
    await data.formGate?.future;
    return snapshot;
  }

  @override
  Future<List<Api2FormInfo>> sessionForms(String sessionID) async {
    data.sessionReads++;
    return (await pendingForms())
        .where((f) => f.sessionID == sessionID)
        .toList();
  }

  @override
  Future<List<MessageWithParts>> messages(String id) async {
    data.messageReads++;
    return [];
  }

  @override
  Future<void> replyForm(
    String sessionID,
    String formID,
    Map<String, dynamic> answer,
  ) async {
    final accepted = data.formsByDirectory[directory]
        ?.where((f) => f.id == formID && f.sessionID == sessionID)
        .firstOrNull;
    data.formReplies.add((
      directory,
      sessionID,
      formID,
      jsonDecode(jsonEncode(answer)) as Map<String, dynamic>,
    ));
    await data.replyGate?.future;
    if (data.replyError case final error?) throw error;
    data.formsByDirectory[directory]?.removeWhere(
      (f) => identical(f, accepted),
    );
  }

  @override
  Future<void> cancelForm(String sessionID, String formID) async {
    data.formCancels.add((directory, sessionID, formID));
    data.formsByDirectory[directory]?.removeWhere(
      (f) => f.id == formID && f.sessionID == sessionID,
    );
  }
}

Map<String, dynamic> _formJson({
  String sessionID = 'form-session',
  num minimum = 0,
}) => {
  'id': 'form-1',
  'sessionID': sessionID,
  'title': 'Configure the run',
  'metadata': {
    'origin': {'step': 1},
  },
  'fields': [
    {'key': 'approved', 'type': 'boolean', 'required': true},
    {'key': 'amount', 'type': 'number', 'minimum': minimum, 'maximum': 10},
    {'key': 'count', 'type': 'integer', 'minimum': 0},
    {
      'key': 'targets',
      'type': 'multiselect',
      'options': [
        {'value': 'a', 'label': 'Alpha'},
        {'value': 'b', 'label': 'Beta'},
      ],
    },
    {
      'key': 'note',
      'type': 'string',
      'when': [
        {'key': 'approved', 'op': 'eq', 'value': true},
      ],
    },
  ],
};

Api2FormInfo _form({String sessionID = 'form-session', num minimum = 0}) =>
    Api2FormInfo.fromJson(_formJson(sessionID: sessionID, minimum: minimum))!;

const _typedFormAnswer = <String, dynamic>{
  'approved': true,
  'amount': 2.5,
  'count': 3,
  'targets': ['a', 'b'],
  'note': 'Ready',
};

final _formControllers = <ConnectionController>[];

void _formTest(String name, Future<void> Function(WidgetTester) body) {
  testWidgets(name, (tester) async {
    try {
      await body(tester);
    } finally {
      for (final controller in _formControllers) {
        controller.dispose();
      }
      _formControllers.clear();
      await tester.pump();
    }
  });
}

Future<_World> _formWorld(
  WidgetTester tester, {
  void Function(Map<String, _Server>)? before,
}) async {
  final world = await _world(
    tester,
    serverFactory: _FormServer.new,
    apiFactory: (server, url) => _FormApi(server as _FormServer, url),
    before: before,
  );
  _formControllers.add(world.controller);
  return world;
}

Future<(ChatFeedItem, ConnectionController)> _showFormRow(
  WidgetTester tester,
  _World world,
  String url,
  String folder, {
  String sessionID = 'form-session',
}) async {
  final c = world.controller;
  final initial = c.chatFeed().items.firstWhere(
    (r) => r.directory == folder && r.sessionID == sessionID,
  );
  final owner = c.connectionForRow(initial)!;
  owner.elsewhereAttention.handle(
    EventEnvelope(
      type: 'form.v2.created',
      directory: folder,
      properties: {'form': _formJson(sessionID: sessionID)},
    ),
  );
  await owner.refreshChatFeed();
  await tester.pump(const Duration(milliseconds: 50));
  final row = c.chatFeed().items.firstWhere(
    (r) => r.directory == folder && r.sessionID == sessionID,
  );
  expect(row.status, ChatStatus.needsYou);
  return (row, owner);
}

void _feedFormsTests() {
  for (final side in [false, true]) {
    _formTest('feed form typed reply uses exact unopened scope side=$side', (
      tester,
    ) async {
      final url = side ? _termux : _ubuntu;
      final folder = side ? '/data/forms' : '/root/other';
      final w = await _formWorld(
        tester,
        before: (servers) {
          servers[url]!.global.add(_row('form-session', folder));
          (servers[url]! as _FormServer).formsByDirectory[folder] = [_form()];
        },
      );
      final c = w.controller;
      final mainLocation = c.directory;
      final initial = c.chatFeed().items.firstWhere(
        (r) => r.directory == folder,
      );
      final owner = c.connectionForRow(initial)!;
      final location = owner.directory;
      final server = w.servers[url]! as _FormServer;
      final sessionReads = server.sessionReads, messages = server.messageReads;
      final (row, _) = await _showFormRow(tester, w, url, folder);
      final request = c.formRequestForFeedItem(row);
      expect(request, isNotNull);
      expect(request!.identity.profileID, side ? 'termux' : 'ubuntu');
      expect(request.identity.directory, folder);
      expect(request.identity.sessionID, 'form-session');
      expect(request.identity.formID, 'form-1');
      expect(request.isPending(), isTrue);
      await request.reply(_typedFormAnswer);
      expect(server.formReplies, hasLength(1));
      final reply = server.formReplies.single;
      expect(
        (reply.$1, reply.$2, reply.$3),
        (folder, 'form-session', 'form-1'),
      );
      expect(reply.$4, _typedFormAnswer);
      expect(request.isPending(), isFalse);
      expect(owner.directory, location);
      expect(c.directory, mainLocation);
      expect(server.sessionReads, sessionReads);
      expect(server.messageReads, messages);
      expect(
        (w.servers[side ? _ubuntu : _termux]! as _FormServer).formReplies,
        isEmpty,
      );
    });
  }

  Future<(_World, ChatFeedItem, ConnectionController, CapturedFormRequest)> one(
    WidgetTester tester,
  ) async {
    final w = await _formWorld(
      tester,
      before: (servers) {
        servers[_termux]!.global.add(_row('form-session', '/data/forms'));
        (servers[_termux]! as _FormServer).formsByDirectory['/data/forms'] = [
          _form(),
        ];
      },
    );
    final (row, owner) = await _showFormRow(tester, w, _termux, '/data/forms');
    final request = w.controller.formRequestForFeedItem(row);
    expect(request, isNotNull);
    return (w, row, owner, request!);
  }

  _formTest(
    'feed form identical IDs remain isolated by directory and profile',
    (tester) async {
      final locations = [
        (_ubuntu, '/root/one', 'form-session'),
        (_ubuntu, '/root/two', 'other-session'),
        (_termux, '/data/forms', 'form-session'),
      ];
      final w = await _formWorld(
        tester,
        before: (servers) {
          for (final (url, folder, session) in locations) {
            servers[url]!.global.add(_row(session, folder));
            (servers[url]! as _FormServer).formsByDirectory[folder] = [
              _form(sessionID: session),
            ];
          }
        },
      );
      final requests = <CapturedFormRequest>[];
      for (final (url, folder, session) in locations) {
        final (row, _) = await _showFormRow(
          tester,
          w,
          url,
          folder,
          sessionID: session,
        );
        final request = w.controller.formRequestForFeedItem(row);
        expect(request, isNotNull);
        requests.add(request!);
      }
      expect(requests.map((r) => r.identity.key).toSet(), hasLength(3));
      await requests.last.cancel();
      expect((w.servers[_termux]! as _FormServer).formCancels, [
        ('/data/forms', 'form-session', 'form-1'),
      ]);
      expect((w.servers[_ubuntu]! as _FormServer).formCancels, isEmpty);
      expect(requests.first.isPending(), isTrue);
      expect(requests[1].isPending(), isTrue);
    },
  );

  for (final changed in ['constraint', 'metadata', 'condition']) {
    _formTest('feed form full schema $changed change retires captured answer', (
      tester,
    ) async {
      final (w, row, owner, old) = await one(tester);
      final raw = _formJson();
      if (changed == 'constraint') (raw['fields'] as List)[1]['minimum'] = 2;
      if (changed == 'metadata') {
        raw['metadata'] = {
          'origin': {'step': 2},
        };
      }
      if (changed == 'condition') {
        (raw['fields'] as List)[4]['when'][0]['value'] = false;
      }
      (w.servers[_termux]! as _FormServer).formsByDirectory['/data/forms'] = [
        Api2FormInfo.fromJson(raw)!,
      ];
      await owner.refreshChatFeed();
      final next = w.controller.formRequestForFeedItem(row);
      expect(next, isNotNull);
      expect(old.isPending(), isFalse);
      expect(next!.identity.revision, isNot(old.identity.revision));
      await expectLater(
        old.reply(_typedFormAnswer),
        throwsA(isA<ProductException>()),
      );
      expect((w.servers[_termux]! as _FormServer).formReplies, isEmpty);
    });
  }

  _formTest(
    'feed form current-location chat and list share identity and singleflight',
    (tester) async {
      final w = await _formWorld(
        tester,
        before: (servers) {
          (servers[_ubuntu]! as _FormServer)
              .formsByDirectory['/root/projects/app'] = [
            _form(sessionID: 'u1'),
          ];
        },
      );
      final c = w.controller;
      await c.selectLocation(directory: '/root/projects/app');
      await c.refreshPendingForms();
      final row = c.chatFeed().items.firstWhere((r) => r.sessionID == 'u1');
      final chat = c.formRequestForForm(c.forms.values.single);
      final list = c.formRequestForFeedItem(row);
      expect(chat, isNotNull);
      expect(list, isNotNull);
      expect(chat!.identity.key, list!.identity.key);
      final server = w.servers[_ubuntu]! as _FormServer;
      server.replyGate = Completer<void>();
      final first = chat.reply(_typedFormAnswer);
      final second = list.reply(_typedFormAnswer);
      await tester.pump();
      expect(server.formReplies, hasLength(1));
      server.replyGate!.complete();
      await Future.wait([first, second]);
      expect(chat.isPending(), isFalse);
      expect(list.isPending(), isFalse);
    },
  );

  for (final change in ['deleted', 'endpoint edited']) {
    _formTest('feed form captured reply is fenced when profile is $change', (
      tester,
    ) async {
      final (w, _, _, request) = await one(tester);
      Future<void>? deletion;
      if (change == 'deleted') {
        deletion = w.controller.deleteProfileAndLocalData('termux');
      } else {
        w.controller.store.profiles
                .firstWhere((p) => p.id == 'termux')
                .baseUrl =
            'https://replacement.example.com';
      }
      expect(request.isPending(), isFalse);
      await expectLater(
        request.reply(_typedFormAnswer),
        throwsA(isA<ProductException>()),
      );
      await expectLater(request.cancel(), throwsA(isA<ProductException>()));
      if (deletion != null) await deletion;
      final server = w.servers[_termux]! as _FormServer;
      expect(server.formReplies, isEmpty);
      expect(server.formCancels, isEmpty);
    });
  }

  _formTest('feed form preflight rejects an unseen schema replacement', (
    tester,
  ) async {
    final (w, _, _, request) = await one(tester);
    final server = w.servers[_termux]! as _FormServer;
    server.formsByDirectory['/data/forms'] = [_form(minimum: 2)];
    await expectLater(
      request.reply(_typedFormAnswer),
      throwsA(isA<ProductException>()),
    );
    expect(server.formReplies, isEmpty);
    expect(request.isPending(), isFalse);
  });

  _formTest('feed form late preflight mismatch preserves newer cached schema', (
    tester,
  ) async {
    final (w, row, owner, request) = await one(tester);
    final server = w.servers[_termux]! as _FormServer;
    server.formsByDirectory['/data/forms'] = [_form(minimum: 2)];
    final preflight = Completer<void>();
    server.formGate = preflight;
    final before = server.formReads.length;
    final sending = request.reply(_typedFormAnswer);
    final refused = expectLater(sending, throwsA(isA<ProductException>()));
    await tester.pump();
    expect(server.formReads.length, greaterThan(before));

    // A later inventory read can finish while the earlier preflight is held.
    server.formGate = null;
    await owner.refreshChatFeed();
    final replacement = w.controller.formRequestForFeedItem(row);
    expect(replacement, isNotNull);
    expect(replacement!.identity.revision, isNot(request.identity.revision));
    preflight.complete();
    await refused;

    expect(replacement.isPending(), isTrue);
    expect(
      w.controller.formRequestForFeedItem(row)?.identity.key,
      replacement.identity.key,
    );
    expect(server.formReplies, isEmpty);
  });

  for (final surface in ['chat', 'list']) {
    _formTest(
      'feed form current-location $surface preflight checks server schema',
      (tester) async {
        final w = await _formWorld(
          tester,
          before: (servers) {
            (servers[_ubuntu]! as _FormServer)
                .formsByDirectory['/root/projects/app'] = [
              _form(sessionID: 'u1'),
            ];
          },
        );
        final c = w.controller;
        await c.selectLocation(directory: '/root/projects/app');
        await c.refreshPendingForms();
        final row = c.chatFeed().items.firstWhere((r) => r.sessionID == 'u1');
        final request = surface == 'chat'
            ? c.formRequestForForm(c.forms.values.single)
            : c.formRequestForFeedItem(row);
        expect(request, isNotNull);
        final server = w.servers[_ubuntu]! as _FormServer;
        server.formsByDirectory['/root/projects/app'] = [
          _form(sessionID: 'u1', minimum: 2),
        ];

        await expectLater(
          request!.reply(_typedFormAnswer),
          throwsA(isA<ProductException>()),
        );

        expect(server.formReplies, isEmpty);
        expect(request.isPending(), isFalse);
      },
    );
  }

  _formTest('feed form late reply cannot retire a replacement schema', (
    tester,
  ) async {
    final (w, row, owner, request) = await one(tester);
    final server = w.servers[_termux]! as _FormServer;
    server.replyGate = Completer<void>();
    final sending = request.reply(_typedFormAnswer);
    await tester.pump();
    expect(server.formReplies, hasLength(1));
    server.formsByDirectory['/data/forms'] = [_form(minimum: 2)];
    await owner.refreshChatFeed();
    final replacement = w.controller.formRequestForFeedItem(row);
    expect(replacement, isNotNull);
    expect(replacement!.identity.revision, isNot(request.identity.revision));
    server.replyGate!.complete();
    await sending;
    expect(replacement.isPending(), isTrue);
    expect(
      w.controller.formRequestForFeedItem(row)?.identity.key,
      replacement.identity.key,
    );
  });

  _formTest('feed form duplicate submit shares one wire attempt', (
    tester,
  ) async {
    final (w, _, _, request) = await one(tester);
    final server = w.servers[_termux]! as _FormServer;
    server.replyGate = Completer<void>();
    final first = request.reply(_typedFormAnswer);
    final second = request.reply(_typedFormAnswer);
    await tester.pump();
    expect(server.formReplies, hasLength(1));
    server.replyGate!.complete();
    await Future.wait([first, second]);
    expect(request.isPending(), isFalse);
  });

  _formTest('feed form invalid answer 400 allows a corrected typed reply', (
    tester,
  ) async {
    final (w, _, _, request) = await one(tester);
    final server = w.servers[_termux]! as _FormServer;
    server.replyError = ApiException('invalid_answer', statusCode: 400);
    await expectLater(
      request.reply({..._typedFormAnswer, 'amount': -1}),
      throwsA(isA<ApiException>()),
    );
    expect(request.isPending(), isTrue);
    server.replyError = null;
    await request.reply(_typedFormAnswer);
    expect(server.formReplies.map((r) => r.$4['amount']), [-1, 2.5]);
  });

  _formTest(
    'feed form uncertain reply blocks retries after equivalent refresh',
    (tester) async {
      final (w, row, owner, request) = await one(tester);
      final server = w.servers[_termux]! as _FormServer;
      server.replyError = StateError('Delivery unknown');
      await expectLater(
        request.reply(_typedFormAnswer),
        throwsA(isA<StateError>()),
      );
      await owner.refreshChatFeed();
      final refreshed = w.controller.formRequestForFeedItem(row);
      expect(refreshed, isNotNull);
      server.replyError = null;
      await expectLater(
        refreshed!.reply(_typedFormAnswer),
        throwsA(isA<ProductException>()),
      );
      await expectLater(refreshed.cancel(), throwsA(isA<ProductException>()));
      expect(server.formReplies, hasLength(1));
      expect(server.formCancels, isEmpty);
    },
  );

  _formTest('feed form resolved global event fences a stale scoped read', (
    tester,
  ) async {
    final (w, row, owner, request) = await one(tester);
    final server = w.servers[_termux]! as _FormServer;
    server.formGate = Completer<void>();
    final refreshing = owner.refreshChatFeed();
    await tester.pump();
    server.formsByDirectory['/data/forms'] = [];
    server.globalEvent!(
      EventEnvelope(
        type: 'form.v2.replied',
        directory: '/data/forms',
        properties: {'id': 'form-1', 'sessionID': 'form-session'},
      ),
    );
    server.formGate!.complete();
    server.formGate = null;
    await refreshing;
    await tester.pump(const Duration(milliseconds: 100));
    expect(request.isPending(), isFalse);
    expect(w.controller.formRequestForFeedItem(row), isNull);
    await expectLater(
      request.reply(_typedFormAnswer),
      throwsA(isA<ProductException>()),
    );
    expect(server.formReplies, isEmpty);
  });

  _formTest(
    'feed forms and questions share four waiting-directory read slots',
    (tester) async {
      final w = await _formWorld(tester);
      final c = w.controller, server = w.servers[_ubuntu]! as _FormServer;
      for (var i = 0; i < 7; i++) {
        final folder = '/root/forms$i', session = 'form-$i';
        server.global.add(_row(session, folder));
        server.formsByDirectory[folder] = [_form(sessionID: session)];
        c.elsewhereAttention.handle(
          EventEnvelope(
            type: 'form.v2.created',
            directory: folder,
            properties: {'form': _formJson(sessionID: session)},
          ),
        );
      }
      server.global.add(_row('idle', '/root/idle'));
      server.formReads.clear();
      server.questionReads.clear();
      final location = c.directory,
          sessions = server.sessionReads,
          messages = server.messageReads;
      await c.refreshChatFeed();
      final directories = {...server.formReads, ...server.questionReads};
      expect(server.formReads.toSet(), hasLength(4));
      expect(directories, hasLength(4));
      expect(directories, isNot(contains('/root/idle')));
      expect(directories, isNot(contains(null)));
      final first = Set<String?>.of(directories);
      server.formReads.clear();
      server.questionReads.clear();
      await c.refreshChatFeed();
      expect({...server.formReads, ...server.questionReads}, hasLength(4));
      expect({...first, ...server.formReads}, hasLength(7));
      expect(c.directory, location);
      expect(server.sessionReads, sessions);
      expect(server.messageReads, messages);
    },
  );
}
