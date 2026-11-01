// The Claude Agent trial (lib/state/claude_agent_trial.dart): OpenCode 2's own
// Claude Agent provider on this phone's server, beside Claude Code through
// Paseo. These pin the gate (phone server, OpenCode 2.2.3+, provider
// offered), the setup order (link first, one connection, wait for the
// check), the states the picker words, and the root script's safety rules.

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/claude_agent_link.dart';
import 'package:opencode_mobile/domain/claude_agent_trial.dart';
import 'package:opencode_mobile/domain/product_repository.dart';
import 'package:opencode_mobile/state/claude_agent_trial.dart';

const _external = IntegrationMethodInfo(
  type: 'external',
  id: claudeAgentLoginMethodId,
  label: 'Claude Code login (claude auth login)',
);

IntegrationInfo _claude([
  List<IntegrationConnectionInfo> connections = const [],
]) => IntegrationInfo(
  id: claudeAgentProviderId,
  name: 'Claude Agent',
  methods: const [_external],
  connections: connections,
  connectionCount: connections.length,
);

IntegrationConnectionInfo _connection(String id, {String? status}) =>
    IntegrationConnectionInfo(
      type: 'credential',
      id: id,
      label: 'Claude Agent',
      status: status,
    );

class _Operations extends ProductRepository
    implements ExternalIntegrationGateway, IntegrationCredentialGateway {
  List<IntegrationConnectionInfo> connections = [];
  bool offered = true;
  String? statusAfterConnect;
  final connected = <(String, String)>[];
  final removed = <String>[];
  int _next = 0;

  @override
  void setLocation({String? directory, String? workspace}) {}

  @override
  Future<List<IntegrationInfo>> listIntegrations() async => [
    const IntegrationInfo(
      id: 'anthropic',
      name: 'Anthropic',
      methods: [IntegrationMethodInfo(type: 'key', label: 'API key')],
      connectionCount: 0,
    ),
    if (offered) _claude(connections),
  ];

  @override
  Future<void> connectIntegrationExternal(
    String integrationID,
    String methodID,
  ) async {
    connected.add((integrationID, methodID));
    // OpenCode lists the newest connection first.
    connections = [
      _connection('cred-${_next++}', status: statusAfterConnect),
      ...connections,
    ];
  }

  @override
  Future<void> removeCredential(String id) async {
    removed.add(id);
    connections = [
      for (final c in connections)
        if (c.id != id) c,
    ];
  }

  @override
  Future<void> activateCredential(String id) async {}

  @override
  Future<void> renameCredential(String id, String label) async {}

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _Host implements ClaudeAgentTrialHost {
  _Host(this.operations);

  @override
  bool onPhoneServer = true;
  @override
  String? serverVersion = '2.2.5';
  @override
  String? agentOwnerId = 'owner-1';
  @override
  String? scope = 'phone@2.2.5';
  @override
  final _Operations operations;
  @override
  bool? hasClaudeAgentModels = false;

  /// What the model list holds after the next refresh.
  bool modelsAfterRefresh = false;
  int refreshes = 0;

  @override
  Future<void> refreshCatalog() async {
    refreshes++;
    hasClaudeAgentModels = modelsAfterRefresh;
  }
}

class _Link implements ClaudeAgentLinkRunner {
  _Link([this.result = ClaudeAgentLinkResult.linked]);
  ClaudeAgentLinkResult result;
  final owners = <String>[];
  @override
  Future<ClaudeAgentLinkResult> link(String ownerId) async {
    owners.add(ownerId);
    return result;
  }
}

ClaudeAgentTrial _trial(_Host host, _Link link) => ClaudeAgentTrial(
  host,
  link: link,
  pollInterval: Duration.zero,
  pollAttempts: 3,
);

void main() {
  test('server versions compare by release number', () {
    expect(serverVersionAtLeast('2.2.3', '2.2.3'), isTrue);
    expect(serverVersionAtLeast('2.2.5', '2.2.3'), isTrue);
    expect(serverVersionAtLeast('v2.10.0-beta', '2.2.3'), isTrue);
    expect(serverVersionAtLeast('3.0.0', '2.2.3'), isTrue);
    expect(serverVersionAtLeast('2.2.2', '2.2.3'), isFalse);
    expect(serverVersionAtLeast('2.1.9', '2.2.3'), isFalse);
    expect(serverVersionAtLeast(null, '2.2.3'), isFalse);
    expect(serverVersionAtLeast('local', '2.2.3'), isFalse);
  });

  group('the gate', () {
    test(
      'needs this phone, OpenCode 2.2.3+ and the provider offered',
      () async {
        final host = _Host(_Operations());
        final trial = _trial(host, _Link());
        expect(trial.capable, isFalse, reason: 'nothing read yet');
        await trial.refresh();
        expect(trial.capable, isTrue);
        expect(trial.state, ClaudeAgentTrialState.available);

        host.serverVersion = '2.2.2';
        expect(trial.capable, isFalse);
        host.serverVersion = '2.2.5';
        host.onPhoneServer = false;
        expect(trial.capable, isFalse);
        expect(trial.state, ClaudeAgentTrialState.unavailable);
        host.onPhoneServer = true;

        host.operations.offered = false;
        await trial.refresh();
        expect(trial.capable, isFalse);
      },
    );

    test('a first list that misses it is read once more', () async {
      // OpenCode loads its providers for a project on the first read there.
      final ops = _Operations()..offered = false;
      final host = _Host(ops);
      final trial = ClaudeAgentTrial(
        host,
        link: _Link(),
        lateLoadDelay: const Duration(milliseconds: 10),
      );
      await trial.refresh();
      expect(trial.capable, isFalse);
      ops.offered = true;
      await Future<void>.delayed(const Duration(milliseconds: 50));
      expect(trial.capable, isTrue);
    });

    test('another server or profile needs a fresh read', () async {
      final host = _Host(_Operations());
      final trial = _trial(host, _Link());
      await trial.refresh();
      host.scope = 'other@2.2.5';
      expect(trial.capable, isFalse);
    });

    test('the capability flag survives the managed-host copy', () {
      const base = ServerCapabilities();
      expect(base.claudeAgent, isFalse);
      final on = base.withClaudeAgent(true);
      expect(on.claudeAgent, isTrue);
      expect(on.withGenUi(true).claudeAgent, isTrue);
      expect(on.withGenUi(true).genUi, isTrue);
      expect(base.withGenUi(true).withClaudeAgent(true).genUi, isTrue);
      expect(on.serverCatalog, base.serverCatalog);
    });
  });

  group('turning it on', () {
    test(
      'links the agent home, connects once and waits for the models',
      () async {
        final ops = _Operations();
        final host = _Host(ops)..modelsAfterRefresh = true;
        final link = _Link();
        final trial = _trial(host, link);
        await trial.refresh();
        final states = <ClaudeAgentTrialState>[];
        trial.addListener(() => states.add(trial.state));

        await trial.start();

        expect(link.owners, ['owner-1']);
        expect(ops.connected, [
          (claudeAgentProviderId, claudeAgentLoginMethodId),
        ]);
        expect(states, contains(ClaudeAgentTrialState.preparing));
        expect(trial.state, ClaudeAgentTrialState.ready);
        expect(host.refreshes, greaterThan(0));
      },
    );

    test('an existing connection is reused, never doubled', () async {
      final ops = _Operations()..connections = [_connection('cred-old')];
      final host = _Host(ops)..modelsAfterRefresh = true;
      final trial = _trial(host, _Link());
      await trial.refresh();
      await trial.start();
      expect(ops.connected, isEmpty);
      expect(trial.state, ClaudeAgentTrialState.ready);
    });

    test('no sign-in reads as needs sign-in', () async {
      final ops = _Operations()..statusAfterConnect = 'needs_auth';
      final host = _Host(ops);
      final trial = _trial(host, _Link());
      await trial.refresh();
      await trial.start();
      expect(trial.state, ClaudeAgentTrialState.needsSignIn);
    });

    test(
      'Claude Code missing or the launcher taken stop before connecting',
      () async {
        for (final (result, state) in [
          (
            ClaudeAgentLinkResult.notInstalled,
            ClaudeAgentTrialState.notInstalled,
          ),
          (ClaudeAgentLinkResult.blocked, ClaudeAgentTrialState.blocked),
          (ClaudeAgentLinkResult.failed, ClaudeAgentTrialState.failed),
        ]) {
          final ops = _Operations();
          final trial = _trial(_Host(ops), _Link(result));
          await trial.refresh();
          await trial.start();
          expect(trial.state, state, reason: '$result');
          expect(ops.connected, isEmpty, reason: '$result');
        }
      },
    );

    test('models that never arrive read as failed', () async {
      final ops = _Operations();
      final host = _Host(ops);
      final trial = _trial(host, _Link());
      await trial.refresh();
      await trial.start();
      expect(trial.state, ClaudeAgentTrialState.failed);
    });

    test('check again asks OpenCode afresh and keeps one connection', () async {
      final ops = _Operations()..statusAfterConnect = 'needs_auth';
      final host = _Host(ops);
      final trial = _trial(host, _Link());
      await trial.refresh();
      await trial.start();
      expect(trial.state, ClaudeAgentTrialState.needsSignIn);

      // The person signed in to Claude Code meanwhile.
      ops.statusAfterConnect = null;
      host.modelsAfterRefresh = true;
      await trial.checkAgain();

      expect(ops.connected, hasLength(2));
      expect(ops.removed, ['cred-0']);
      expect(ops.connections.map((c) => c.id), ['cred-1']);
      expect(trial.state, ClaudeAgentTrialState.ready);
    });

    test('nothing happens off this phone', () async {
      final ops = _Operations();
      final host = _Host(ops)..onPhoneServer = false;
      final link = _Link();
      final trial = _trial(host, link);
      await trial.refresh();
      await trial.start();
      expect(link.owners, isEmpty);
      expect(ops.connected, isEmpty);
    });
  });

  group('the root script', () {
    test('refuses an agent home id that is not one', () {
      expect(() => ClaudeAgentLink.script('../x'), throwsArgumentError);
      expect(() => ClaudeAgentLink.script(''), throwsArgumentError);
    });

    test('points at the shared agent home and never touches the sign-in', () {
      final script = ClaudeAgentLink.script('owner-1');
      expect(script, contains("home='/home/oc/.oc-profiles/owner-1/claude'"));
      expect(
        script,
        contains(
          "export CLAUDE_CONFIG_DIR='/home/oc/.oc-profiles/owner-1/claude'",
        ),
      );
      // Ahead of the Terminal's launcher on root's PATH; typed in a
      // terminal it hands over to that launcher unchanged.
      expect(script, contains('shim=/usr/local/sbin/claude'));
      expect(
        script,
        contains(
          'if [ -t 0 ] && [ -x /usr/local/bin/claude ]; then exec /usr/local/bin/claude "\$@"; fi',
        ),
      );
      expect(script, contains(ClaudeAgentLink.shimMarker));
      // Never replaces a launcher it did not write.
      expect(script, contains("! grep -qx '${ClaudeAgentLink.shimMarker}'"));
      for (final secret in [
        '.credentials.json',
        'cp ',
        'cat "\$home',
        'rm -rf',
      ]) {
        expect(script, isNot(contains(secret)), reason: secret);
      }
    });

    test('its result line is read, anything else is a failure', () {
      expect(
        ClaudeAgentLink.parse(0, 'noise\noc-claude-agent:linked\n'),
        ClaudeAgentLinkResult.linked,
      );
      expect(
        ClaudeAgentLink.parse(0, 'oc-claude-agent:linked-without-resume'),
        ClaudeAgentLinkResult.linkedWithoutResume,
      );
      expect(
        ClaudeAgentLink.parse(0, 'oc-claude-agent:not-installed'),
        ClaudeAgentLinkResult.notInstalled,
      );
      expect(
        ClaudeAgentLink.parse(0, 'oc-claude-agent:blocked'),
        ClaudeAgentLinkResult.blocked,
      );
      expect(ClaudeAgentLink.parse(0, ''), ClaudeAgentLinkResult.failed);
      expect(
        ClaudeAgentLink.parse(1, 'oc-claude-agent:linked'),
        ClaudeAgentLinkResult.failed,
      );
    });
  });
}
