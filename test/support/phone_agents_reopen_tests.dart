part of '../phone_agents_controller_test.dart';

void _reopenAgentTests() {
  test(
    'new Claude saved row retains the daemon ID across source loss',
    () async {
      final x = await _permissionListSetup(null);
      final c = x.world.controller;
      addTearDown(c.dispose);
      final daemonID = await c.startAgentChatIn(
        _project,
        agentId: 'claude',
        firstPrompt: 'Ask before removing obsolete.txt',
      );
      await c.refreshChatFeed();
      final source =
          x.world.host.gateways[x.world.host.sockets.indexOf(x.socket)];
      final row = c.chatFeed().items.singleWhere(
        (r) =>
            r.sourceId == 'paseo:$_project' &&
            source.daemonSessionId(r.sessionID) == daemonID,
      );
      expect(row.sessionID, isNot(daemonID));
      // Rebuilding the merged feed takes the app's saved-row snapshot.
      await c.store.setLocation('local', directory: '/root/projects/other');
      await c.refreshAgentRows();
      await c.refreshChatFeed();
      await pumpEventQueue();
      final saved =
          jsonDecode(c.store.prefs.getString('oc.agentFeed.local')!) as List;
      expect(
        saved.map((entry) => (entry as Map)['sessionID']),
        contains(daemonID),
      );
      expect(
        saved.map((entry) => (entry as Map)['sessionID']),
        isNot(contains(row.sessionID)),
      );
      expect(
        c.savedAgentSessionPreviews.map((entry) => entry.id),
        contains(daemonID),
      );
    },
  );

  for (final status in ['running', 'idle']) {
    for (final disposeChat in [false, true]) {
      test(
        'reopen newly created $status Claude with disposed chat=$disposeChat',
        () async {
          final x = await _permissionListSetup(null);
          final c = x.world.controller;
          addTearDown(c.dispose);
          final daemonID = await c.startAgentChatIn(
            _project,
            agentId: 'claude',
            firstPrompt: 'Ask before removing obsolete.txt',
          );
          x.world.state.agents = [
            for (final agent in x.world.state.agents)
              if (agent['id'] == daemonID)
                {
                  ...agent,
                  'status': status,
                  'pendingPermissions': [
                    {..._shellPermission(), 'id': 'reopen-permission'},
                  ],
                }
              else
                agent,
          ];
          await c.refreshChatFeed();
          final source =
              x.world.host.gateways[x.world.host.sockets.indexOf(x.socket)];
          final row = c.chatFeed().items.singleWhere(
            (r) =>
                r.sourceId == 'paseo:$_project' &&
                source.daemonSessionId(r.sessionID) == daemonID,
          );
          expect(row.sessionID, isNot(daemonID));
          expect(row.status, ChatStatus.needsYou);
          if (disposeChat) c.backendForConversation(daemonID)!.dispose();
          final creates = x.socket.of('create_agent_request').length;
          final route = await c.openChatFeedItem(row);
          final backend = c.backendForConversation(route.sessionID)!;
          for (final socket in x.world.host.sockets) {
            socket.handlers['fetch_agent_request'] = (request) {
              final agent = x.world.state.agents
                  .where((a) => a['id'] == request['agentId'])
                  .firstOrNull;
              return agent == null
                  ? (
                      'rpc_error',
                      {
                        'error': 'Agent not found',
                        'requestType': 'fetch_agent_request',
                      },
                    )
                  : ('fetch_agent_response', {'agent': agent});
            };
            socket.handlers['fetch_agent_timeline_request'] = (request) =>
                request['agentId'] != daemonID
                ? (
                    'rpc_error',
                    {
                      'error': 'Agent not found',
                      'requestType': 'fetch_agent_timeline_request',
                    },
                  )
                : (
                    'fetch_agent_timeline_response',
                    {
                      'agentId': daemonID,
                      'entries': <Object>[],
                      'hasMore': false,
                    },
                  );
          }
          // Match ChatScreen's first authoritative history read: a local draft
          // ID sent to this independent gateway used to fail with unavailable.
          expect(await backend.api!.messages(route.sessionID), isEmpty);
          expect(route.sessionID, daemonID);
          await backend.refreshPendingPermissions();
          final permission = backend.permissionForSession(daemonID)!;
          expect(permission.patterns, ['rm -v obsolete.txt']);
          await backend.answerPermission(
            permission.id,
            'once',
            expectedRequest: backend.permissionIdentity(permission),
          );
          final responses = [
            for (final socket in x.world.host.sockets)
              ...socket.of('agent_permission_response'),
          ];
          expect(responses, hasLength(1));
          expect(responses.single['agentId'], daemonID);
          expect(responses.single['requestId'], 'reopen-permission');
          expect(x.socket.of('create_agent_request'), hasLength(creates));
          expect(x.world.state.resumed, isEmpty);
          for (final socket in x.world.host.sockets) {
            expect(socket.of('resume_agent_request'), isEmpty);
            expect(socket.of('send_agent_message_request'), isEmpty);
          }
        },
      );
    }
  }
}
