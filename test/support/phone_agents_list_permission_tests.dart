part of '../phone_agents_controller_test.dart';

Map<String, dynamic> _shellPermission({
  String command = 'rm -v obsolete.txt',
}) => {
  'id': 'permission-shared',
  'name': 'Bash',
  'kind': 'tool',
  'provider': 'claude',
  'input': {'command': command},
  'detail': {'type': 'shell', 'command': command},
  'suggestions': <Object>[],
};

Future<({_World world, ChatFeedItem row, FakePaseoSocket socket})>
_permissionListSetup(WidgetTester? tester) async {
  final w = await _world(tester, secure: _FakeSecure({}));
  w.state.runtimes = {'claude': _ready('claude')};
  w.state.agents = [
    {
      ..._agent('new-claude', _project),
      'pendingPermissions': [_shellPermission()],
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

Future<void> _showPermissionRow(
  WidgetTester tester,
  ConnectionController c,
  ChatFeedItem row,
) => tester.pumpWidget(
  MaterialApp(
    theme: captureTheme(),
    localizationsDelegates: AppLocalizations.localizationsDelegates,
    supportedLocales: AppLocalizations.supportedLocales,
    home: Scaffold(
      body: SingleChildScrollView(
        child: Builder(
          builder: (context) =>
              ConnectionChatsHost(c).listRequest(context, row) ??
              const SizedBox(),
        ),
      ),
    ),
  ),
);

void _listPermissionTests() {
  testWidgets(
    'list permission captures revision and never sends a changed held command',
    (tester) async {
      final x = await _permissionListSetup(tester);
      final c = x.world.controller;
      final permission = c.permissionForFeedItem(x.row)!;
      final request = c.permissionIdentityForFeedItem(x.row, permission);
      await c.refreshChatFeed();
      expect(c.permissionForFeedItem(x.row)!.id, permission.id);
      expect(c.isRequestPending(request), isTrue);
      await _showPermissionRow(tester, c, x.row);
      await tester.tap(find.byKey(const Key('permission-card-allow')));
      await tester.pump();
      x.socket.push('agent_permission_request', {
        'agentId': 'new-claude',
        'request': _shellPermission(command: 'rm -v different.txt'),
      });
      await tester.pump();
      expect(c.isRequestPending(request), isFalse);
      expect(c.permissionForFeedItem(x.row)!.id, isNot(permission.id));
      await tester.pump(const Duration(seconds: 4));
      expect(x.socket.of('agent_permission_response'), isEmpty);
      expect(find.textContaining('rm -v different.txt'), findsOneWidget);
      await tester.pumpWidget(const SizedBox());
      c.dispose();
      await tester.pump();
    },
  );

  testWidgets('list permission source identities isolate colliding wire IDs', (
    tester,
  ) async {
    final x = await _permissionListSetup(tester);
    final c = x.world.controller;
    const other = '/root/projects/other';
    x.world.state.agents.add({
      ..._agent('new-claude', other),
      'pendingPermissions': [_shellPermission()],
    });
    await c.store.setLocation('local', directory: other);
    await c.refreshAgentRows();
    await c.refreshChatFeed();
    final row = c.chatFeed().items.firstWhere(
      (r) => r.sourceId == 'paseo:$other',
    );
    final a = c.permissionForFeedItem(x.row)!,
        b = c.permissionForFeedItem(row)!;
    expect(a.id, isNot(b.id));
    final aRequest = c.permissionIdentityForFeedItem(x.row, a);
    final bRequest = c.permissionIdentityForFeedItem(row, b);
    expect(() => c.permissionIdentityForFeedItem(row, a), throwsArgumentError);
    expect(
      () =>
          c.answerPermissionForFeedItem(row, 'once', expectedRequest: aRequest),
      throwsArgumentError,
    );
    await Future.wait([
      c.answerPermissionForFeedItem(x.row, 'once', expectedRequest: aRequest),
      c.answerPermissionForFeedItem(x.row, 'reject', expectedRequest: aRequest),
    ]);
    expect(x.socket.of('agent_permission_response'), hasLength(1));
    expect(c.isRequestPending(bRequest), isTrue);
    final socket = x
        .world
        .host
        .sockets[x.world.host.gateways.indexWhere((g) => g.directory == other)];
    expect(socket.of('agent_permission_response'), isEmpty);
    await c.answerPermission(b.id, 'reject', expectedRequest: bRequest);
    expect(
      socket.of('agent_permission_response').single['requestId'],
      'permission-shared',
    );
    c.dispose();
    await tester.pump();
  });

  testWidgets(
    'list permission disappears on resolution and disconnect fences old replies',
    (tester) async {
      final x = await _permissionListSetup(tester);
      final c = x.world.controller;
      final before = c.permissionForFeedItem(x.row)!;
      final request = c.permissionIdentity(before);
      x.socket.push('agent_permission_resolved', {
        'agentId': 'new-claude',
        'requestId': 'permission-shared',
      });
      await tester.pump();
      expect(c.permissionForFeedItem(x.row), isNull);
      expect(c.isRequestPending(request), isFalse);
      x.socket.push('agent_permission_request', {
        'agentId': 'new-claude',
        'request': _shellPermission(),
      });
      await tester.pump();
      final after = c.permissionForFeedItem(x.row)!;
      expect(after.id, isNot(before.id));
      final next = c.permissionIdentity(after);
      await x.socket.close();
      await tester.pump();
      expect(c.isRequestPending(next), isFalse);
      expect(c.permissionForFeedItem(x.row), isNull);
      await c.answerPermission(after.id, 'once', expectedRequest: next);
      expect(x.socket.of('agent_permission_response'), isEmpty);
      c.dispose();
      await tester.pump();
    },
  );

  test('list permission profile deletion fences held approval', () async {
    final x = await _permissionListSetup(null);
    final c = x.world.controller;
    final permission = c.permissionForFeedItem(x.row)!;
    final request = c.permissionIdentity(permission);
    final deletion = c.deleteProfileAndLocalData('local');
    expect(c.isRequestPending(request), isFalse);
    await c.answerPermission(permission.id, 'once', expectedRequest: request);
    expect(x.socket.of('agent_permission_response'), isEmpty);
    await deletion;
    c.dispose();
  });

  testWidgets(
    'list permission for a newly created conversation routes draft ID to daemon ID',
    (tester) async {
      final x = await _permissionListSetup(tester);
      final c = x.world.controller;
      final realID = await c.startAgentChatIn(
        _project,
        agentId: 'claude',
        firstPrompt: 'Please remove obsolete.txt after asking me.',
      );
      final gateway =
          x.world.host.gateways[x.world.host.sockets.indexOf(x.socket)];
      final row = c.chatFeed().items.firstWhere(
        (r) =>
            r.sourceId == 'paseo:$_project' &&
            gateway.daemonSessionId(r.sessionID) == realID,
      );
      expect(row.sessionID, isNot(realID));
      x.socket.push('agent_permission_request', {
        'agentId': realID,
        'request': {..._shellPermission(), 'id': 'permission-created'},
      });
      await tester.pump();
      final permission = c.permissionForFeedItem(row)!;
      expect(permission.sessionID, row.sessionID);
      final before = x.world.host.gateways.length;
      final creates = x.socket.of('create_agent_request').length;
      final resumes = List<String>.of(x.world.state.resumed);
      await c.answerPermissionForFeedItem(
        row,
        'once',
        expectedRequest: c.permissionIdentityForFeedItem(row, permission),
      );
      final response = x.socket.of('agent_permission_response').single;
      expect(response['agentId'], realID);
      expect(response['requestId'], 'permission-created');
      expect(response['response'], {'behavior': 'allow'});
      expect(x.world.host.gateways.length, before);
      expect(x.socket.of('create_agent_request'), hasLength(creates));
      expect(x.world.state.resumed, resumes);
      expect(x.socket.of('fetch_agent_timeline_request'), isEmpty);
      c.dispose();
      await tester.pump();
    },
  );

  for (final disposedChat in [false, true]) {
    testWidgets(
      'list permission shows and answers with Undo after disposed chat=$disposedChat',
      (tester) async {
        final x = await _permissionListSetup(tester);
        final c = x.world.controller;
        if (disposedChat) {
          await c.openChatFeedItem(x.row);
          c.backendForConversation(x.row.sessionID)!.dispose();
        }
        final count = x.world.host.gateways.length;
        final api = c.api;
        final location = c.locationRevision;
        final resumed = List<String>.of(x.world.state.resumed);
        expect(c.connectionForRow(x.row), isNull);
        expect(x.row.status, ChatStatus.needsYou);
        await _showPermissionRow(tester, c, x.row);
        await tester.pump();
        expect(find.textContaining('rm -v obsolete.txt'), findsOneWidget);
        expect(find.byKey(const Key('permission-card-allow')), findsOneWidget);
        await tester.tap(find.byKey(const Key('permission-card-allow')));
        await tester.pump();
        await tester.tap(find.byKey(const Key('permission-card-undo')));
        await tester.pump(const Duration(seconds: 4));
        expect(x.socket.of('agent_permission_response'), isEmpty);
        await tester.tap(find.byKey(const Key('permission-card-reject')));
        await tester.pump(const Duration(seconds: 4));
        await tester.pump();
        final response = x.socket.of('agent_permission_response').single;
        expect(response['agentId'], 'new-claude');
        expect(response['requestId'], 'permission-shared');
        expect(response['response'], {'behavior': 'deny'});
        expect(x.world.host.gateways.length, count);
        expect(c.api, same(api));
        expect(c.locationRevision, location);
        expect(x.world.state.resumed, resumed);
        expect(x.socket.of('fetch_agent_timeline_request'), isEmpty);
        expect(x.socket.of('resume_agent_request'), isEmpty);
        await tester.pumpWidget(const SizedBox());
        c.dispose();
        await tester.pump();
      },
    );
  }
}
