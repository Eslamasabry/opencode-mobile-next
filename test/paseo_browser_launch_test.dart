import 'dart:async';
import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/domain/agent_tools/browser_claude_launch.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/paseo/gateway.dart';
import 'package:opencode_mobile/paseo/transport.dart';

import 'paseo_gateway_test.dart' show FakeDaemon, agentJson;

class _Daemon extends FakeDaemon {
  _Daemon(this.trace);
  final List<String> trace;
  @override
  void send(String message) {
    final frame = jsonDecode(message) as Map<String, dynamic>;
    if (frame['type'] == 'hello') {
      hello = frame;
      push('status', {'status': 'server_info', 'version': '0.9.2'});
      return;
    }
    if (frame['type'] != 'ping') {
      trace.add('rpc:${(frame['message'] as Map)['type']}');
    }
    super.send(message);
  }
}

class _Port implements BrowserClaudeLaunchPort {
  _Port(this.trace);
  final List<String> trace;
  final targets = <BrowserEnrollmentTarget>[];
  final revoked = <BrowserLaunchReservation>[];
  Completer<BrowserLaunchReservation?>? pending;
  bool deny = false;
  bool failRevoke = false;
  int sequence = 0;
  BrowserLaunchReservation grant(BrowserEnrollmentTarget target) =>
      BrowserLaunchReservation(
        id: 'grant-${++sequence}',
        launchId: sequence.toRadixString(16).padLeft(32, '0'),
        target: target,
      );
  @override
  Future<BrowserLaunchReservation?> beforeBrowserClaudeLaunch({
    required String profileId,
    required String sourceId,
    required String sessionId,
    required String daemonAgentId,
  }) async {
    trace.add('reserve');
    final target = BrowserEnrollmentTarget(
      profileId: profileId,
      sourceId: sourceId,
      sessionId: sessionId,
      daemonAgentId: daemonAgentId,
    );
    targets.add(target);
    return pending != null ? pending!.future : (deny ? null : grant(target));
  }

  @override
  Future<void> revokeBrowserClaudeLaunch(
    BrowserLaunchReservation reservation,
  ) async {
    trace.add('revoke');
    revoked.add(reservation);
    if (failRevoke) throw StateError('private native text');
  }
}

class _World {
  _World({bool noop = false}) {
    daemon = _Daemon(trace);
    port = _Port(trace);
    registry = BrowserClaudeLaunchRegistry(
      port: noop ? const NoopBrowserClaudeLaunchPort() : port,
    );
    daemon.handlers['create_agent_request'] = (_) =>
        ('create_agent_response', {'agent': agentJson('a1')});
    daemon.handlers['fetch_agent_request'] = (r) =>
        ('fetch_agent_response', {'agent': agentJson(r['agentId'] as String)});
    daemon.handlers['get_providers_snapshot_request'] = (_) => (
      'get_providers_snapshot_response',
      {
        'entries': [
          {
            'provider': 'claude',
            'status': 'ready',
            'models': [
              {'id': 'haiku', 'isDefault': true},
            ],
            'modes': [
              {'id': 'default'},
            ],
          },
        ],
      },
    );
    daemon.handlers['send_agent_message_request'] = (r) => (
      'send_agent_message_response',
      {'agentId': r['agentId'], 'accepted': true},
    );
    daemon.handlers['set_agent_model_request'] = (_) =>
        ('set_agent_model_response', {});
    daemon.handlers['set_agent_mode_request'] = (_) =>
        ('set_agent_mode_response', {});
    daemon.handlers['list_commands_request'] = (_) => (
      'list_commands_response',
      {
        'commands': [
          {'name': '/compact'},
        ],
      },
    );
    daemon.handlers['cancel_agent_request'] = (_) =>
        ('cancel_agent_response', {});
    daemon.handlers['archive_agent_request'] = (_) =>
        ('archive_agent_response', {});
    daemon.handlers['resume_agent_request'] = (_) =>
        ('resume_agent_response', {'agent': agentJson('a2')});
    gateway =
        PaseoGateway(
          transport: PaseoTransport(
            endpoint: 'ws://127.0.0.1:6767',
            socketFactory: (_, _) async => daemon,
          ),
          directory: '/work/app',
        )..configureBrowserClaudeLaunch(
          registry: registry,
          profileId: 'owner',
          sourceId: 'paseo:/work/app',
          sourceIdForDirectory: (directory) => 'paseo:$directory',
        );
  }
  final trace = <String>[];
  late final _Daemon daemon;
  late final _Port port;
  late final BrowserClaudeLaunchRegistry registry;
  late final PaseoGateway gateway;
  Future<String> draft({bool browser = true}) async {
    final session = await gateway.createSession();
    if (browser) {
      await gateway.setBrowserRequestedForSession(session.id, requested: true);
    }
    return session.id;
  }

  Future<void> close() async {
    gateway.close();
    await registry.close();
  }
}

void main() {
  test('ordinary first prompt retains the original create path', () async {
    final w = _World();
    addTearDown(w.close);
    final id = await w.draft(browser: false);
    await w.gateway.promptAsync(id, text: 'hello');
    expect(
      w.daemon.of('create_agent_request').single['initialPrompt'],
      'hello',
    );
    expect(w.daemon.of('send_agent_message_request'), isEmpty);
    expect(w.port.targets, isEmpty);
  });

  test(
    'browser first prompt creates cold, maps, reserves then sends once',
    () async {
      final w = _World();
      addTearDown(w.close);
      final id = await w.draft();
      await w.gateway.promptAsync(
        id,
        text: 'hello',
        attachments: const [
          PromptAttachment(
            mime: 'image/png',
            filename: 'shot.png',
            url: 'data:image/png;base64,iVBORw0KGgo=',
          ),
        ],
      );
      final create = w.daemon.of('create_agent_request').single;
      expect(create.containsKey('initialPrompt'), isFalse);
      expect(create.containsKey('images'), isFalse);
      expect(create.containsKey('clientMessageId'), isFalse);
      final target = w.port.targets.single;
      expect(target.profileId, 'owner');
      expect(target.sourceId, 'paseo:/work/app');
      expect(target.sessionId, 'a1');
      expect(target.daemonAgentId, 'a1');
      final send = w.daemon.of('send_agent_message_request').single;
      expect(send['agentId'], 'a1');
      expect(send['text'], 'hello');
      expect(send['images'], hasLength(1));
      expect(
        w.trace.indexOf('rpc:create_agent_request'),
        lessThan(w.trace.indexOf('reserve')),
      );
      expect(
        w.trace.indexOf('reserve'),
        lessThan(w.trace.indexOf('rpc:send_agent_message_request')),
      );
    },
  );

  test(
    'failed browser reserve sends nothing until explicitly turned off',
    () async {
      final w = _World(noop: true);
      addTearDown(w.close);
      final id = await w.draft();
      await expectLater(
        w.gateway.promptAsync(id, text: 'hello'),
        throwsA(isA<ProductException>()),
      );
      expect(
        w.daemon.of('create_agent_request').single.containsKey('initialPrompt'),
        isFalse,
      );
      expect(w.daemon.of('send_agent_message_request'), isEmpty);
      await w.gateway.setBrowserRequestedForSession(id, requested: false);
      await w.gateway.promptAsync(id, text: 'continue without browser');
      expect(w.daemon.of('send_agent_message_request'), hasLength(1));
    },
  );

  test(
    'reserve precedes direct modes models and scoped command discovery',
    () async {
      for (final action in ['mode', 'model', 'commands', 'prompt']) {
        final w = _World();
        await w.gateway.session('a1');
        await w.gateway.setBrowserRequestedForSession('a1', requested: true);
        if (action == 'mode') {
          await w.gateway.setSessionAgent('a1', 'acceptEdits');
        }
        if (action == 'model') {
          await w.gateway.setSessionModel(
            'a1',
            ModelRef(providerID: 'claude', modelID: 'opus'),
            '',
          );
        }
        if (action == 'commands') {
          expect(await w.gateway.listCommands(), isEmpty);
          expect(w.daemon.of('list_commands_request'), isEmpty);
          expect(
            (await w.gateway.listBrowserCommandsForSession('a1')).single.name,
            'compact',
          );
          expect(w.daemon.of('list_commands_request').single['agentId'], 'a1');
        }
        if (action == 'prompt') {
          await w.gateway.promptAsync(
            'a1',
            text: 'continue',
            agent: 'acceptEdits',
            model: ModelRef(providerID: 'claude', modelID: 'opus'),
          );
        }
        final mutation = w.trace.lastIndexWhere(
          (e) =>
              e.startsWith('rpc:') &&
              !e.contains('fetch') &&
              !e.contains('providers'),
        );
        expect(
          w.trace.indexOf('reserve'),
          greaterThanOrEqualTo(0),
          reason: action,
        );
        expect(w.trace.indexOf('reserve'), lessThan(mutation), reason: action);
        await w.close();
      }
    },
  );

  test('abort and delete revoke before daemon mutation', () async {
    for (final delete in [false, true]) {
      final w = _World();
      final id = await w.draft();
      await w.gateway.promptAsync(id, text: 'hello');
      if (delete) {
        await w.gateway.deleteSession(id);
      } else {
        await w.gateway.abort(id);
      }
      expect(w.port.revoked, hasLength(1));
      final mutation = delete
          ? 'rpc:archive_agent_request'
          : 'rpc:cancel_agent_request';
      expect(w.trace.indexOf('revoke'), lessThan(w.trace.indexOf(mutation)));
      await w.close();
    }
  });

  test(
    'resume retires old generation, maps new ID and reserves before send',
    () async {
      final w = _World();
      addTearDown(w.close);
      final id = await w.draft();
      await w.gateway.promptAsync(id, text: 'hello');
      expect(await w.gateway.resumeHostAgentChat(id), 'a2');
      expect(w.port.revoked.single.target.daemonAgentId, 'a1');
      expect(w.port.targets.last.daemonAgentId, 'a2');
      expect(
        w.trace.indexOf('revoke'),
        lessThan(w.trace.indexOf('rpc:resume_agent_request')),
      );
      expect(
        w.trace.indexOf('rpc:resume_agent_request'),
        lessThan(w.trace.lastIndexOf('reserve')),
      );
      await w.gateway.promptAsync(id, text: 'resumed');
      expect(w.daemon.of('send_agent_message_request').last['agentId'], 'a2');
    },
  );

  test('explicit new chat replacement revokes old authority', () async {
    final w = _World();
    addTearDown(w.close);
    final id = await w.draft();
    await w.gateway.promptAsync(id, text: 'hello');
    final replacement = await w.gateway.startNewHostAgentChat(
      id,
      newChatAcknowledged: true,
    );
    expect(w.port.revoked, hasLength(1));
    expect(
      w.registry.isRequested(
        profileId: 'owner',
        sourceId: 'paseo:/work/app',
        sessionId: replacement,
      ),
      isTrue,
    );
  });

  test(
    'late reservation after session deletion is revoked and never sends',
    () async {
      final w = _World();
      addTearDown(w.close);
      final id = await w.draft();
      final pending = w.port.pending = Completer<BrowserLaunchReservation?>();
      final prompt = w.gateway.promptAsync(id, text: 'hello');
      final rejected = expectLater(prompt, throwsA(isA<ProductException>()));
      while (w.port.targets.isEmpty) {
        await Future<void>.delayed(Duration.zero);
      }
      await w.gateway.deleteSession(id);
      pending.complete(w.port.grant(w.port.targets.single));
      await rejected;
      expect(w.daemon.of('send_agent_message_request'), isEmpty);
      expect(w.port.revoked, hasLength(1));
    },
  );

  test(
    'profile revoke fences in-flight reservation without blocking cleanup',
    () async {
      final w = _World();
      addTearDown(w.close);
      final id = await w.draft();
      final pending = w.port.pending = Completer<BrowserLaunchReservation?>();
      final rejected = expectLater(
        w.gateway.promptAsync(id, text: 'hello'),
        throwsA(isA<ProductException>()),
      );
      while (w.port.targets.isEmpty) {
        await Future<void>.delayed(Duration.zero);
      }
      await w.registry.revokeProfile(profileId: 'owner');
      pending.complete(w.port.grant(w.port.targets.single));
      await rejected;
      expect(w.daemon.of('send_agent_message_request'), isEmpty);
      expect(w.port.revoked, hasLength(1));
    },
  );

  test('close retires authority before closing the transport', () async {
    final w = _World();
    final id = await w.draft();
    await w.gateway.promptAsync(id, text: 'hello');
    w.gateway.close();
    expect(w.port.revoked, hasLength(1));
    await Future<void>.delayed(Duration.zero);
    await w.registry.close();
    expect(w.daemon.closed, isTrue);
  });
  test(
    'cleanup quarantine preserves intent and refuses an ordinary fallback',
    () async {
      final w = _World();
      addTearDown(w.close);
      final id = await w.draft();
      await w.gateway.promptAsync(id, text: 'hello');
      w.port.failRevoke = true;
      await w.gateway.abort(id);
      expect(
        w.registry.isRequested(
          profileId: 'owner',
          sourceId: 'paseo:/work/app',
          sessionId: 'a1',
        ),
        isTrue,
      );
      await expectLater(
        w.gateway.promptAsync(id, text: 'must not send'),
        throwsA(isA<ProductException>()),
      );
      expect(w.daemon.of('send_agent_message_request'), hasLength(1));
    },
  );

  test(
    'cancel or delete while cold creation is pending never resurrects a turn',
    () async {
      for (final delete in [false, true]) {
        final w = _World();
        final id = await w.draft();
        w.daemon.handlers['create_agent_request'] = (_) => null;
        final rejected = expectLater(
          w.gateway.promptAsync(id, text: 'hello'),
          throwsA(isA<ProductException>()),
        );
        while (w.daemon.of('create_agent_request').isEmpty) {
          await Future<void>.delayed(Duration.zero);
        }
        if (delete) {
          await w.gateway.deleteSession(id);
        } else {
          await w.gateway.abort(id);
        }
        w.daemon.push('create_agent_response', {
          'requestId': w.daemon.of('create_agent_request').single['requestId'],
          'agent': agentJson('a1'),
        });
        await rejected;
        expect(w.port.targets, isEmpty);
        expect(w.daemon.of('send_agent_message_request'), isEmpty);
        expect(w.daemon.of('archive_agent_request').single['agentId'], 'a1');
        await w.close();
      }
    },
  );

  test(
    'enabling browser while provider discovery waits prevents ordinary create',
    () async {
      final w = _World();
      addTearDown(w.close);
      final id = await w.draft(browser: false);
      w.daemon.handlers['get_providers_snapshot_request'] = (_) => null;
      final rejected = expectLater(
        w.gateway.promptAsync(id, text: 'hello'),
        throwsA(isA<ProductException>()),
      );
      while (w.daemon.of('get_providers_snapshot_request').isEmpty) {
        await Future<void>.delayed(Duration.zero);
      }
      await w.gateway.setBrowserRequestedForSession(id, requested: true);
      w.daemon.push('get_providers_snapshot_response', {
        'requestId': w.daemon
            .of('get_providers_snapshot_request')
            .single['requestId'],
        'entries': [
          {
            'provider': 'claude',
            'status': 'ready',
            'models': <Object>[],
            'modes': <Object>[],
          },
        ],
      });
      await rejected;
      expect(w.daemon.of('create_agent_request'), isEmpty);
      expect(w.daemon.of('send_agent_message_request'), isEmpty);
    },
  );

  test(
    'shared request suppresses draft commands before local inventory loads',
    () async {
      final w = _World();
      addTearDown(w.close);
      await w.gateway.setBrowserRequestedForSession('a1', requested: true);
      final other =
          PaseoGateway(
            transport: PaseoTransport(
              endpoint: 'ws://127.0.0.1:6767',
              socketFactory: (_, _) async => w.daemon,
            ),
            directory: '/work/app',
          )..configureBrowserClaudeLaunch(
            registry: w.registry,
            profileId: 'owner',
            sourceId: 'paseo:/work/app',
          );
      expect(await other.listCommands(), isEmpty);
      expect(w.daemon.of('list_commands_request'), isEmpty);
      other.close();
    },
  );

  test(
    'trusted folder rebinding uses new source and retains old source intent',
    () async {
      final w = _World();
      addTearDown(w.close);
      final id = await w.draft();
      await w.gateway.promptAsync(id, text: 'first');
      w.gateway.setLocation(directory: '/work/second');
      expect(w.port.revoked, hasLength(1));
      expect(
        w.registry.isRequested(
          profileId: 'owner',
          sourceId: 'paseo:/work/app',
          sessionId: 'a1',
        ),
        isTrue,
      );
      w.daemon.handlers['create_agent_request'] = (_) => (
        'create_agent_response',
        {'agent': agentJson('a2', cwd: '/work/second')},
      );
      final second = await w.draft();
      await w.gateway.promptAsync(second, text: 'second');
      expect(w.port.targets.last.sourceId, 'paseo:/work/second');
      expect(w.port.targets.last.daemonAgentId, 'a2');
    },
  );
}
