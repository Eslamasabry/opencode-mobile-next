part of '../phone_agents_controller_test.dart';

/// "Continue with…" into Claude Code, then opening the new conversation from
/// the source chat's "Continued in Claude Code" line (owner's phone,
/// 2026-10-26: that line opened a blank "Conversation" that failed with "The
/// Paseo daemon returned an unsupported response").
void _phoneAgentHandoffOpenTests() {
  /// Answers like the daemon: an id it doesn't know is `agent: null`.
  void daemonLike(FakePaseoSocket socket, _World w) {
    socket.handlers['fetch_agent_request'] = (request) {
      final id = request['agentId'];
      final agent = w.state.agents.where((a) => a['id'] == id).firstOrNull;
      return (
        'fetch_agent_response',
        {
          'agent': agent,
          'project': null,
          'error': agent == null ? 'Agent not found: $id' : null,
        },
      );
    };
    socket.handlers['fetch_agent_timeline_request'] = (request) => (
      'fetch_agent_timeline_response',
      {
        'agentId': request['agentId'],
        'epoch': 'epoch-1',
        'endCursor': {'epoch': 'epoch-1', 'seq': 1},
        'entries': [
          {
            'provider': 'claude',
            'item': {
              'type': 'user_message',
              'text': 'Continue the work from the previous conversation.',
              'messageId': 'u1',
            },
            'timestamp': _stamp,
            'seqStart': 1,
          },
        ],
      },
    );
  }

  /// Starts the continued conversation the way the sheet does, on the
  /// agent folder's own gateway; returns that gateway and the draft's id.
  Future<(_World, PaseoGateway, String)> continued(WidgetTester tester) async {
    final w = await _world(tester);
    w.state.runtimes = {'claude': _ready('claude')};
    w.state.configureSocket = (socket) => daemonLike(socket, w);
    final c = w.controller;
    await c.rememberLastUsedProject(_project);
    await c.refreshAgentRows();
    await c.refreshChatFeed();
    final gateway = await c.agentHandoffTargetGateway(_project) as PaseoGateway;
    final draft = await gateway.createSession();
    await gateway.promptAgentHandoff(
      draft.id,
      text: 'Continue the work from the previous conversation.',
      model: ModelRef(providerID: 'claude', modelID: 'claude-model'),
      beforeSend: () {},
    );
    return (w, gateway, draft.id);
  }

  Future<void> opensOnItsBackend(ConnectionController c, String id) async {
    final backend = c.backendForConversation(id);
    expect(backend, isNotNull, reason: 'no backend for $id');
    expect(backend!.isAgentBackend, isTrue);
    final messages = await backend.api!.messages(id);
    expect(
      messages.expand((m) => m.parts).map((p) => p.text),
      contains('Continue the work from the previous conversation.'),
    );
  }

  testWidgets('the Continued in line opens the conversation the list row '
      'opens', (tester) async {
    final (w, gateway, draft) = await continued(tester);
    final c = w.controller;
    try {
      final durable = gateway.handoffSessionId(draft);
      expect(durable, isNot(draft));
      await c.refreshChatFeed();
      final row = c.chatFeed().items.singleWhere(
        (r) =>
            r.sourceId == 'paseo:$_project' &&
            gateway.daemonSessionId(r.sessionID) == durable,
      );
      final fromList = (await c.openChatFeedItem(row)).sessionID;
      // What the link line does with the id the link saved.
      final fromLink = await c.agentHandoffAdopt(
        directory: _project,
        agentId: 'claude',
        sessionId: gateway.handoffNavigationSessionId(durable),
      );
      expect(fromLink, fromList);
      await opensOnItsBackend(c, fromLink);
    } finally {
      c.dispose();
      await tester.pump(const Duration(seconds: 3));
    }
  });

  testWidgets('the sheet opens the new conversation before the list has '
      'its row', (tester) async {
    final (w, gateway, draft) = await continued(tester);
    final c = w.controller;
    try {
      // What AgentHandoffController.send returns to the sheet.
      final sent = gateway.handoffNavigationSessionId(
        gateway.handoffSessionId(draft),
      );
      final open = await c.agentHandoffAdopt(
        directory: _project,
        agentId: 'claude',
        sessionId: sent,
      );
      await opensOnItsBackend(c, open);
    } finally {
      c.dispose();
      await tester.pump(const Duration(seconds: 3));
    }
  });
}
