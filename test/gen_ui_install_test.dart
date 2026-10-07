import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/agents/gen_ui_install.dart';
import 'package:opencode_mobile/builtin/agents/gen_ui_install_scripts.dart';
import 'package:opencode_mobile/domain/genui/gen_ui_status.dart';

final class _Runner implements GenUiSetupRunner {
  final calls = <GenUiAgent>[];
  final outcomes = <GenUiAgent, GenUiInstallOutcome>{};
  Completer<void>? hold;
  @override
  Future<GenUiInstallOutcome> run({
    required GenUiAgent agent,
    required String script,
  }) async {
    calls.add(agent);
    await hold?.future;
    return outcomes[agent] ?? GenUiInstallOutcome.registered;
  }
}

final class _Verifier implements GenUiSetupVerifier {
  _Verifier(this.ready);
  final Set<GenUiAgent> ready;
  final calls = <GenUiAgent>[];
  @override
  Future<bool> verify({
    required String profileId,
    required GenUiAgent agent,
  }) async {
    calls.add(agent);
    return ready.contains(agent);
  }
}

void main() {
  test('registration never claims a ready runtime', () async {
    final runner = _Runner();
    final status = await ManagedGenUiInstaller(runner: runner).setEnabled(
      profileId: 'profile-1',
      agents: {GenUiAgent.claude},
      enabled: true,
    );
    expect(status, isA<GenUiSetupRestartRequired>());
    expect(status.agents, isEmpty);
  });

  test(
    'mixed registration surfaces partial with no unverified agents',
    () async {
      final runner = _Runner()
        ..outcomes[GenUiAgent.openCode1] = GenUiInstallOutcome.nameCollision;
      final status = await ManagedGenUiInstaller(runner: runner).setEnabled(
        profileId: 'profile-1',
        agents: {GenUiAgent.claude, GenUiAgent.openCode1},
        enabled: true,
      );
      expect(status, isA<GenUiSetupPartial>());
      expect((status as GenUiSetupPartial).reason, GenUiSetupProblem.conflict);
      expect(status.agents, isEmpty);
    },
  );

  test('verified Claude registration becomes ready', () async {
    final verifier = _Verifier({GenUiAgent.claude});
    final status = await ManagedGenUiInstaller(
      runner: _Runner(),
      verifier: verifier,
    ).setEnabled(profileId: 'one', agents: {GenUiAgent.claude}, enabled: true);
    expect(status, isA<GenUiSetupOn>());
    expect(status.agents, [GenUiAgent.claude]);
  });

  test(
    'verified Claude remains ready when OpenCode runtime is missing',
    () async {
      final runner = _Runner()
        ..outcomes[GenUiAgent.openCode1] = GenUiInstallOutcome.runtimeMissing;
      final verifier = _Verifier({GenUiAgent.claude});
      final status =
          await ManagedGenUiInstaller(
            runner: runner,
            verifier: verifier,
          ).setEnabled(
            profileId: 'one',
            agents: {GenUiAgent.claude, GenUiAgent.openCode1},
            enabled: true,
          );
      expect(status, isA<GenUiSetupPartial>());
      expect(status.agents, [GenUiAgent.claude]);
      expect(verifier.calls, [GenUiAgent.claude]);
    },
  );

  test(
    'missing installation disables successfully without verification',
    () async {
      final runner = _Runner()
        ..outcomes[GenUiAgent.openCode2] = GenUiInstallOutcome.notInstalled;
      final verifier = _Verifier({});
      final status =
          await ManagedGenUiInstaller(
            runner: runner,
            verifier: verifier,
          ).setEnabled(
            profileId: 'one',
            agents: {GenUiAgent.openCode2},
            enabled: false,
          );
      expect(status, isA<GenUiSetupOff>());
      expect(verifier.calls, isEmpty);
    },
  );

  test('reject invalid profile before invoking setup', () async {
    final runner = _Runner();
    final status = await ManagedGenUiInstaller(runner: runner).setEnabled(
      profileId: '../other; touch x',
      agents: {GenUiAgent.claude},
      enabled: true,
    );
    expect(status, isA<GenUiSetupFailed>());
    expect(runner.calls, isEmpty);
  });

  test('serialize across installer instances', () async {
    final first = _Runner()..hold = Completer<void>();
    final second = _Runner();
    final a = ManagedGenUiInstaller(
      runner: first,
    ).setEnabled(profileId: 'one', agents: {GenUiAgent.claude}, enabled: true);
    final b = ManagedGenUiInstaller(
      runner: second,
    ).setEnabled(profileId: 'two', agents: {GenUiAgent.claude}, enabled: false);
    await Future<void>.delayed(Duration.zero);
    expect(first.calls, [GenUiAgent.claude]);
    expect(second.calls, isEmpty);
    first.hold!.complete();
    await Future.wait([a, b]);
    expect(second.calls, [GenUiAgent.claude]);
  });

  group('isolated persistent ownership transaction', () {
    late Directory root;
    setUp(() async {
      root = await Directory.systemTemp.createTemp('genui-install-');
    });
    tearDown(() async => root.delete(recursive: true));

    Future<int> apply(
      String owner, {
      bool enabled = true,
      bool failCheck = false,
      bool verify = false,
      bool wrongVersion = false,
      GenUiAgent agent = GenUiAgent.openCode1,
    }) async {
      final script = verify
          ? genUiVerificationScript(profileId: owner, agent: agent)
          : genUiInstallScript(
              profileId: owner,
              agent: agent,
              enabled: enabled,
            );
      final encoded = RegExp(
        r"python3 - '([^']+)'",
      ).firstMatch(script)!.group(1)!;
      final data =
          jsonDecode(utf8.decode(base64.decode(encoded)))
              as Map<String, dynamic>;
      data['directory'] = '${root.path}/managed';
      data['config'] = '${root.path}/config/opencode.json';
      final source = script
          .split("<<'OC_GENUI_SETUP' 2>/dev/null\n")[1]
          .split('\nOC_GENUI_SETUP')[0];
      // Real file/JSON/locking/atomic/rollback code runs against a temporary
      // tree. Replace only host identity/runtime execution, never file logic.
      final harness = source.replaceFirst('try: main()', '''
os.getuid = lambda: ${agent == GenUiAgent.claude ? 1000 : 0}
original_access = os.access
os.access = lambda path, mode: True if (path.startswith('/home/oc/.local/share/oc-agents/claude/') or path == '/home/oc/.local/node/bin/node') else original_access(path, mode)
original_safe = safe
safe = lambda path, *args, **kwargs: True if (path.startswith('/home/oc/.local/share/oc-agents/claude/') or path == '/home/oc/.local/node/bin/node') else original_safe(path, *args, **kwargs)
root_executable = lambda path: None
def check(*args, **kwargs):
    data = json.loads(base64.b64decode(sys.argv[1]))
    argv = args[0]
    output = b'{"jsonrpc":"2.0","id":1,"result":{}}\\n'
    if argv == [data['cli'], '--version']:
        output = ('${wrongVersion ? '0.0.0' : '2.1.283'} (Claude Code)\\n').encode()
    elif argv == [data['node'], '--version']:
        output = (data['nodeVersion']+'\\n').encode()
    elif argv == [data['cli'], 'mcp', 'list']:
        output = ('Checking MCP server health…\\n\\noc-ui: '+data['node']+' '+data['directory']+'/server.cjs - ✔ Connected\\n').encode()
    elif data['verify']:
        messages = [json.loads(row) for row in kwargs['input'].splitlines()]
        assert messages[-1]['method'] == 'tools/list'
        output += b'{"jsonrpc":"2.0","id":2,"result":{"tools":[{"name":"show","inputSchema":{}}]}}\\n'
    return subprocess.CompletedProcess(args, ${failCheck ? 1 : 0}, output)
subprocess.run = check
def fake_cli(args, env):
    data = json.loads(base64.b64decode(sys.argv[1]))
    if env['HOME'] != data['home'] or env['CLAUDE_CONFIG_DIR'] != str(pathlib.Path(data['config']).parent):
        return False
    if args[1:3] not in [['mcp','add'], ['mcp','remove']]: return False
    config = read_json(data['config'])
    entries = slot(config, 'claude', True)
    if args[2] == 'add':
        if args[3:] != ['--scope','user','--transport','stdio','oc-ui','--',data['node'],data['directory']+'/server.cjs']: return False
        entries['oc-ui'] = {'type':'stdio','command':data['node'],'args':[data['directory']+'/server.cjs'],'env':{}}
    else:
        if args[3:] != ['--scope','user','oc-ui']: return False
        entries.pop('oc-ui', None)
    save_json(data['config'], config)
    return True
cli = fake_cli
try: main()''');
      final process = await Process.start('/usr/bin/python3', [
        '-',
        base64.encode(utf8.encode(jsonEncode(data))),
      ]);
      process.stdin.write(harness);
      await process.stdin.close();
      final output = process.stdout.drain<void>();
      final errors = process.stderr.drain<void>();
      final code = await process.exitCode;
      await Future.wait([output, errors]);
      return code;
    }

    Future<Map<String, dynamic>> config() async =>
        jsonDecode(
              await File('${root.path}/config/opencode.json').readAsString(),
            )
            as Map<String, dynamic>;

    test(
      'two owners install idempotently and last disable removes only own entry',
      () async {
        await Directory('${root.path}/config').create();
        await File('${root.path}/config/opencode.json').writeAsString(
          jsonEncode({
            'model': 'keep',
            'mcp': {
              'other': {'enabled': false},
            },
          }),
        );
        expect(await apply('one'), 0);
        expect(await apply('one'), 0);
        expect(await apply('two'), 0);
        expect(await apply('one', enabled: false), 10);
        expect((await config())['mcp'], contains('oc-ui'));
        expect(
          await File('${root.path}/managed/enabled').readAsString(),
          'enabled\n',
        );
        expect(await apply('two', enabled: false), 10);
        expect(await File('${root.path}/managed/enabled').exists(), false);
        expect(await config(), {
          'model': 'keep',
          'mcp': {
            'other': {'enabled': false},
          },
        });
      },
    );

    test(
      'Claude CLI uses profile environment and user scope for add/remove',
      () async {
        expect(await apply('profile-one', agent: GenUiAgent.claude), 0);
        expect((await config())['mcpServers'], contains('oc-ui'));
        expect(
          await apply('profile-one', agent: GenUiAgent.claude, enabled: false),
          10,
        );
        expect((await config())['mcpServers'], isEmpty);
        expect(await File('${root.path}/managed/enabled').exists(), false);
      },
    );

    test(
      'Claude readiness checks pinned CLI and discovery in owned profile',
      () async {
        expect(await apply('one', agent: GenUiAgent.claude), 0);
        expect(await apply('one', agent: GenUiAgent.claude, verify: true), 0);
        expect(
          await apply(
            'one',
            agent: GenUiAgent.claude,
            verify: true,
            wrongVersion: true,
          ),
          24,
        );
        await File('${root.path}/managed/enabled').delete();
        expect(await apply('one', agent: GenUiAgent.claude, verify: true), 24);
      },
    );

    test(
      'disable absent runtime does not create directories or invoke runtime',
      () async {
        expect(
          await apply(
            'one',
            agent: GenUiAgent.openCode2,
            enabled: false,
            failCheck: true,
          ),
          11,
        );
        expect(await root.list().toList(), isEmpty);
      },
    );

    test('unowned entry collision preserves existing config', () async {
      await Directory('${root.path}/config').create();
      final file = File('${root.path}/config/opencode.json');
      const original = '{"mcp":{"oc-ui":{"command":["custom"]}},"keep":42}';
      await file.writeAsString(original);
      expect(await apply('one'), 21);
      expect(await file.readAsString(), original);
      expect(await File('${root.path}/managed/enabled').exists(), false);
    });

    test('symlinked managed directory refuses writes', () async {
      final outside = await Directory('${root.path}/outside').create();
      await Link('${root.path}/managed').create(outside.path);
      expect(await apply('one'), 22);
      expect(await outside.list().toList(), isEmpty);
    });

    test('edited owned entry is never removed', () async {
      expect(await apply('one'), 0);
      final value = await config();
      (value['mcp'] as Map)['oc-ui'] = {
        'command': ['user-edit'],
      };
      await File(
        '${root.path}/config/opencode.json',
      ).writeAsString(jsonEncode(value));
      expect(await apply('one', enabled: false), 21);
      expect((await config())['mcp']['oc-ui'], {
        'command': ['user-edit'],
      });
    });

    test('failed self-check rolls back only newly managed writes', () async {
      await Directory('${root.path}/config').create();
      await File(
        '${root.path}/config/opencode.json',
      ).writeAsString('{"keep":42}');
      expect(await apply('one', failCheck: true), 24);
      expect((await config())['keep'], 42);
      expect((await config())['mcp'], isEmpty);
      expect(await File('${root.path}/managed/enabled').exists(), false);
      expect(await File('${root.path}/managed/owners.json').exists(), false);
      expect(await File('${root.path}/managed/server.cjs').exists(), false);
    });
  });
}
