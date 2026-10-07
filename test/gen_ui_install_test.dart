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

Map<String, dynamic> _decodeJsonc(String source) {
  // Independent fixture decoder: quoted strings win over comment/comma tokens.
  final uncommented = source.replaceAllMapped(
    RegExp(r'"(?:\\.|[^"\\])*"|//[^\r\n]*|/\*[\s\S]*?\*/'),
    (match) => match[0]!.startsWith('"') ? match[0]! : ' ',
  );
  final strict = uncommented.replaceAllMapped(
    RegExp(r'"(?:\\.|[^"\\])*"|,(\s*[}\]])'),
    (match) => match[1] ?? match[0]!,
  );
  return jsonDecode(strict) as Map<String, dynamic>;
}

void main() {
  test('stages OpenCode while only qualified Claude becomes ready', () async {
    final runner = _Runner();
    final verifier = _Verifier({GenUiAgent.claude});
    final status =
        await ManagedGenUiInstaller(
          runner: runner,
          verifier: verifier,
        ).setEnabled(
          profileId: 'one',
          agents: GenUiAgent.values.toSet(),
          enabled: true,
        );
    expect(runner.calls, GenUiAgent.values);
    expect(verifier.calls, [GenUiAgent.claude]);
    expect(status, isA<GenUiSetupPartial>());
    expect(status.agents, [GenUiAgent.claude]);
  });

  test(
    'review 1 failed verifier reports verification failure not restart',
    () async {
      final status =
          await ManagedGenUiInstaller(
            runner: _Runner(),
            verifier: _Verifier({}),
          ).setEnabled(
            profileId: 'one',
            agents: {GenUiAgent.claude},
            enabled: true,
          );
      expect(status, isA<GenUiSetupFailed>());
      expect(
        (status as GenUiSetupFailed).reason,
        GenUiSetupProblem.verificationFailed,
      );
    },
  );

  test('review 1 missing verifier reports unqualified not restart', () async {
    final status = await ManagedGenUiInstaller(
      runner: _Runner(),
    ).setEnabled(profileId: 'one', agents: {GenUiAgent.claude}, enabled: true);
    expect(status, isA<GenUiSetupUnavailable>());
    expect(
      (status as GenUiSetupUnavailable).reason,
      GenUiSetupProblem.notQualified,
    );
  });

  test('registration never claims a ready runtime', () async {
    final runner = _Runner();
    final status = await ManagedGenUiInstaller(runner: runner).setEnabled(
      profileId: 'profile-1',
      agents: {GenUiAgent.claude},
      enabled: true,
    );
    expect(status, isA<GenUiSetupUnavailable>());
    expect(status.agents, isEmpty);
  });

  test(
    'OpenCode collision reports partial while Claude remains ready',
    () async {
      final runner = _Runner()
        ..outcomes[GenUiAgent.openCode1] = GenUiInstallOutcome.nameCollision;
      final status =
          await ManagedGenUiInstaller(
            runner: runner,
            verifier: _Verifier({GenUiAgent.claude}),
          ).setEnabled(
            profileId: 'profile-1',
            agents: {GenUiAgent.claude, GenUiAgent.openCode1},
            enabled: true,
          );
      expect(status, isA<GenUiSetupPartial>());
      expect(status.agents, [GenUiAgent.claude]);
      expect(runner.calls, [GenUiAgent.claude, GenUiAgent.openCode1]);
    },
  );

  test(
    'OpenCode registration cannot become ready through a permissive verifier',
    () async {
      final runner = _Runner();
      final verifier = _Verifier(GenUiAgent.values.toSet());
      final status =
          await ManagedGenUiInstaller(
            runner: runner,
            verifier: verifier,
          ).setEnabled(
            profileId: 'one',
            agents: {GenUiAgent.openCode1, GenUiAgent.openCode2},
            enabled: true,
          );
      expect(runner.calls, [GenUiAgent.openCode1, GenUiAgent.openCode2]);
      expect(verifier.calls, isEmpty);
      expect(status, isA<GenUiSetupUnavailable>());
      expect(
        (status as GenUiSetupUnavailable).reason,
        GenUiSetupProblem.notQualified,
      );
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
      expect(runner.calls, [GenUiAgent.claude, GenUiAgent.openCode1]);
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
      bool requireMarker = false,
      Set<String> runtimes = const {'/usr/bin/node'},
      String? unsafePath,
      bool foreignOwner = false,
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
runtime_paths = {'/usr/bin/node', '/opt/node/bin/node', '/opt/oc-node/bin/node'}
available = set(${jsonEncode(runtimes.toList())})
unsafe_path = ${jsonEncode(unsafePath ?? '')}
original_access = os.access
os.access = lambda path, mode: (path in available) if path in runtime_paths else (True if (path.startswith('/home/oc/.local/share/oc-agents/claude/') or path == '/home/oc/.local/node/bin/node') else original_access(path, mode))
original_stat = os.stat
def owned_stat(path, *args, **kwargs):
    path = str(path)
    try: info = original_stat(path, *args, **kwargs)
    except FileNotFoundError:
        if path not in runtime_paths and not path.startswith('/opt/'): raise
        info = os.stat_result([stat.S_IFREG | 0o755,0,0,1,0,0,0,0,0,0])
    values = list(info)
    values[4] = 1000 if path == unsafe_path and ${foreignOwner ? 'True' : 'False'} else 0
    values[0] &= ~0o022
    if path == unsafe_path and not ${foreignOwner ? 'True' : 'False'}: values[0] |= 0o020
    return os.stat_result(values)
os.stat = owned_stat
original_safe = safe
safe = lambda path, *args, **kwargs: (path in available) if str(path) in runtime_paths else (True if (str(path).startswith('/home/oc/.local/share/oc-agents/claude/') or str(path) == '/home/oc/.local/node/bin/node') else original_safe(path, *args, **kwargs))
def check(*args, **kwargs):
    data = json.loads(base64.b64decode(sys.argv[1]))
    argv = args[0]
    if ${requireMarker ? 'True' : 'False'} and read(data['directory'] + '/enabled') != b'enabled\\n':
        return subprocess.CompletedProcess(args, 1, b'')
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

    Future<Map<String, dynamic>> effectiveMcp() async {
      final json = await config();
      final jsonc = _decodeJsonc(
        await File('${root.path}/config/opencode.jsonc').readAsString(),
      );
      // Pinned OC1 merges the JSONC MCP object over the JSON MCP object.
      return {
        ...(json['mcp'] as Map? ?? const {}),
        ...(jsonc['mcp'] as Map? ?? const {}),
      };
    }

    test(
      'OpenCode 1 schema-only JSONC receives the effective registration',
      () async {
        await Directory('${root.path}/config').create();
        final file = File('${root.path}/config/opencode.jsonc');
        const schema = r'  "$schema": "https://opencode.ai/config.json"';
        const original = '{\n$schema\n}\n';
        await file.writeAsString(original);

        expect(await apply('one'), 0);

        expect(await file.readAsString(), original);
        expect((await effectiveMcp())['oc-ui'], {
          'type': 'local',
          'command': ['/usr/bin/node', '${root.path}/managed/server.cjs'],
          'enabled': true,
        });
        expect(await apply('one'), 0);
        expect(await file.readAsString(), original);
      },
    );

    const commentedConfig = r'''{
  // Keep this user's model choice.
  "$schema": "https://opencode.ai/config.json",
  "model": "provider/model",
  "mcp": {
    /* A user-managed server, not owned by this installer. */
    "other": {"type": "remote", "url": "https://example.test/a//b/*c*/", "enabled": false,},
  },
}
''';

    for (final trailingComma in [false, true]) {
      test(
        'OpenCode 1 JSONC preserves string-array separator with trailing comma $trailingComma',
        () async {
          await Directory('${root.path}/config').create();
          final file = File('${root.path}/config/opencode.jsonc');
          final original =
              '{\n'
              '  // An unrelated local MCP server.\n'
              '  "mcp": {"other": {"type": "local", '
              '"command": ["node", "server.js"${trailingComma ? ',' : ''}]}}\n'
              '}\n';
          await file.writeAsString(original);

          expect(await apply('one'), 0);

          expect(await file.readAsString(), original);
          final effective = await effectiveMcp();
          expect(effective['other']['command'], ['node', 'server.js']);
          expect(effective['oc-ui']['command'], [
            '/usr/bin/node',
            '${root.path}/managed/server.cjs',
          ]);
        },
      );
    }

    test(
      'OpenCode 1 JSONC merges owned entry without changing user content',
      () async {
        await Directory('${root.path}/config').create();
        final file = File('${root.path}/config/opencode.jsonc');
        final lower = File('${root.path}/config/opencode.json');
        const lowerOriginal =
            '{"model":"lower-priority","mcp":{"lower":{"enabled":false}}}';
        await lower.writeAsString(lowerOriginal);
        await file.writeAsString(commentedConfig);

        expect(await apply('one'), 0);

        expect(await file.readAsString(), commentedConfig);
        final effective = await effectiveMcp();
        expect(effective['oc-ui']['command'], [
          '/usr/bin/node',
          '${root.path}/managed/server.cjs',
        ]);
        expect(effective['other'], {
          'type': 'remote',
          'url': 'https://example.test/a//b/*c*/',
          'enabled': false,
        });
        expect(effective['lower'], {'enabled': false});
        expect((await config())['model'], 'lower-priority');
      },
    );

    test('OpenCode 1 disable preserves JSONC and later user edits', () async {
      await Directory('${root.path}/config').create();
      final file = File('${root.path}/config/opencode.jsonc');
      await file.writeAsString(commentedConfig);
      expect(await apply('one'), 0);
      final installed = await file.readAsString();
      final edited = installed.replaceFirst(
        '"model": "provider/model",',
        '"model": "provider/model",\n  "userAdded": 42,',
      );
      await file.writeAsString(edited);

      expect(await apply('one', enabled: false), 10);

      final disabled = await file.readAsString();
      expect(disabled, edited);
      expect(_decodeJsonc(disabled)['userAdded'], 42);
      expect(await effectiveMcp(), isNot(contains('oc-ui')));
      expect((await effectiveMcp())['other']['enabled'], false);
      expect(await File('${root.path}/managed/enabled').exists(), false);
    });

    for (final collisionFile in ['opencode.jsonc', 'opencode.json']) {
      test(
        'OpenCode 1 JSONC refuses a genuine collision in $collisionFile',
        () async {
          await Directory('${root.path}/config').create();
          final jsonc = File('${root.path}/config/opencode.jsonc');
          final json = File('${root.path}/config/opencode.json');
          const collision =
              '{"mcp":{"oc-ui":{"command":["custom"]}},"keep":42}';
          final jsoncOriginal = collisionFile == 'opencode.jsonc'
              ? collision
              : commentedConfig;
          final jsonOriginal = collisionFile == 'opencode.json'
              ? collision
              : '{"keep":43}';
          await jsonc.writeAsString(jsoncOriginal);
          await json.writeAsString(jsonOriginal);

          expect(await apply('one'), 21);

          expect(await jsonc.readAsString(), jsoncOriginal);
          expect(await json.readAsString(), jsonOriginal);
          expect(await File('${root.path}/managed/enabled').exists(), false);
          expect(
            await File('${root.path}/managed/owners.json').exists(),
            false,
          );
        },
      );
    }

    for (final entry in [
      (label: 'flat null', mcp: '{"oc-ui":null}'),
      (
        label: 'nested compatibility',
        mcp: '{"servers":{"oc-ui":{"command":["custom"]}}}',
      ),
    ]) {
      test(
        'OpenCode 1 current JSON refuses ${entry.label} collision',
        () async {
          await Directory('${root.path}/config').create();
          final file = File('${root.path}/config/opencode.json');
          final original = '{"mcp":${entry.mcp},"keep":42}';
          await file.writeAsString(original);

          expect(await apply('one'), 21);

          expect(await file.readAsString(), original);
          expect(await File('${root.path}/managed/enabled').exists(), false);
          expect(
            await File('${root.path}/managed/owners.json').exists(),
            false,
          );
        },
      );
    }

    for (final mcp in ['null', '[]', 'false']) {
      test('OpenCode 1 JSONC refuses a nonobject MCP override $mcp', () async {
        await Directory('${root.path}/config').create();
        final file = File('${root.path}/config/opencode.jsonc');
        final original = '{"mcp":$mcp}';
        await file.writeAsString(original);
        expect(await apply('one'), 21);
        expect(await file.readAsString(), original);
        expect(await File('${root.path}/config/opencode.json').exists(), false);
        expect(await File('${root.path}/managed/enabled').exists(), false);
      });
    }

    for (final blocked in [
      (name: 'config.json', contents: '{"mcp":{"oc-ui":null}}'),
      (name: 'opencode.jsonc', contents: '{"mcp":{"oc-ui":null}}'),
      (name: 'config.json', contents: '{"mcp":{"servers":{"oc-ui":null}}}'),
      (name: 'opencode.jsonc', contents: '{"mcp":{"servers":{"oc-ui":null}}}'),
      (name: 'config.json', contents: '{"mcp":{"oc-ui":null},"mcp":{}}'),
      (name: 'opencode.jsonc', contents: '{"mcp":{"oc-ui":null},"mcp":{}}'),
      (name: 'opencode.jsonc', contents: '{"mcp":{"other":{},"other":{}}}'),
      (name: 'opencode.jsonc', contents: '{"mcp": {/* unfinished'),
      (name: 'config', contents: 'model = "user-model"\n'),
    ]) {
      test(
        'OpenCode 1 refuses unsafe overlay ${blocked.name} ${blocked.contents}',
        () async {
          await Directory('${root.path}/config').create();
          final file = File('${root.path}/config/${blocked.name}');
          await file.writeAsString(blocked.contents);
          expect(await apply('one'), 21);
          expect(await file.readAsString(), blocked.contents);
          expect(
            await File('${root.path}/config/opencode.json').exists(),
            false,
          );
          expect(await File('${root.path}/managed/enabled').exists(), false);
        },
      );
    }

    test(
      'OpenCode 1 refuses symlinked JSONC without touching its target',
      () async {
        await Directory('${root.path}/config').create();
        final outside = File('${root.path}/outside.jsonc');
        await outside.writeAsString(commentedConfig);
        await Link('${root.path}/config/opencode.jsonc').create(outside.path);
        expect(await apply('one'), 22);
        expect(await outside.readAsString(), commentedConfig);
        expect(await File('${root.path}/config/opencode.json').exists(), false);
        expect(await File('${root.path}/managed/enabled').exists(), false);
      },
    );

    test(
      'OpenCode 1 JSONC self-check rollback preserves user settings',
      () async {
        await Directory('${root.path}/config').create();
        final jsonc = File('${root.path}/config/opencode.jsonc');
        await jsonc.writeAsString(commentedConfig);
        final original = {
          'model': 'user-model',
          'mcp': {
            'lower': {'enabled': false},
          },
        };
        await File(
          '${root.path}/config/opencode.json',
        ).writeAsString(jsonEncode(original));

        expect(await apply('one', failCheck: true), 24);

        expect(await jsonc.readAsString(), commentedConfig);
        expect(await config(), original);
        expect(await File('${root.path}/managed/enabled').exists(), false);
        expect(await File('${root.path}/managed/owners.json').exists(), false);
        expect(await File('${root.path}/managed/server.cjs').exists(), false);
      },
    );

    test(
      'OpenCode uses existing root-owned pinned Node without a system alias',
      () async {
        expect(await apply('one', runtimes: {'/opt/node/bin/node'}), 0);
        expect((await config())['mcp']['oc-ui']['command'], [
          '/opt/node/bin/node',
          '${root.path}/managed/server.cjs',
        ]);
        expect(await apply('one', enabled: false, runtimes: {}), 10);
        expect((await config())['mcp'], isEmpty);
      },
    );

    test(
      'OpenCode retains and removes a legacy system Node registration after runtime removal',
      () async {
        expect(await apply('one'), 0);
        expect(
          await apply('two', runtimes: {'/opt/node/bin/node', '/usr/bin/node'}),
          0,
        );
        expect((await config())['mcp']['oc-ui']['command'], [
          '/usr/bin/node',
          '${root.path}/managed/server.cjs',
        ]);
        expect(await apply('one', enabled: false, runtimes: {}), 10);
        expect(await apply('two', enabled: false, runtimes: {}), 10);
        expect((await config())['mcp'], isEmpty);
      },
    );

    test(
      'OpenCode unsafe preferred runtime never falls back to another candidate',
      () async {
        expect(
          await apply(
            'one',
            runtimes: {'/opt/node/bin/node', '/usr/bin/node'},
            unsafePath: '/opt/node/bin/node',
          ),
          22,
        );
        expect(await File('${root.path}/managed/enabled').exists(), false);
      },
    );

    test(
      'OpenCode refuses writable or foreign-owned helper ancestors',
      () async {
        await Directory('${root.path}/managed').create();
        expect(await apply('one', unsafePath: '${root.path}/managed'), 22);
        expect(
          await apply(
            'one',
            unsafePath: '${root.path}/managed',
            foreignOwner: true,
          ),
          22,
        );
        expect(await File('${root.path}/managed/server.cjs').exists(), false);
      },
    );

    test(
      'OpenCode 2 persists direct tools in isolated mcp servers config',
      () async {
        expect(
          await apply(
            'one',
            agent: GenUiAgent.openCode2,
            runtimes: {'/opt/node/bin/node'},
          ),
          0,
        );
        expect((await config())['mcp']['servers']['oc-ui'], {
          'type': 'local',
          'command': ['/opt/node/bin/node', '${root.path}/managed/server.cjs'],
          'disabled': false,
          'codemode': false,
        });
        expect(
          await apply(
            'one',
            agent: GenUiAgent.openCode2,
            verify: true,
            runtimes: {'/opt/node/bin/node'},
          ),
          24,
        );
      },
    );

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
      'Claude show preallow is exact, idempotent and removed on disable',
      () async {
        await Directory('${root.path}/config').create();
        final settings = File('${root.path}/config/settings.json');
        final original = <String, dynamic>{
          'theme': 'dark',
          'permissions': {
            'allow': ['Read', 'mcp__other__show'],
            'ask': ['Bash'],
            'deny': ['Write'],
          },
        };
        await settings.writeAsString(jsonEncode(original));
        expect(await apply('one', agent: GenUiAgent.claude), 0);
        var value = jsonDecode(await settings.readAsString()) as Map;
        expect(value['permissions']['allow'], [
          'Read',
          'mcp__other__show',
          'mcp__oc-ui__show',
        ]);
        expect(value['permissions']['ask'], ['Bash']);
        expect(value['permissions']['deny'], ['Write']);
        expect(await apply('one', agent: GenUiAgent.claude), 0);
        value = jsonDecode(await settings.readAsString()) as Map;
        expect(value['permissions']['allow'], [
          'Read',
          'mcp__other__show',
          'mcp__oc-ui__show',
        ]);
        expect(
          await apply('one', agent: GenUiAgent.claude, enabled: false),
          10,
        );
        expect(jsonDecode(await settings.readAsString()), original);
      },
    );

    test(
      'Claude show preallow preserves user-owned exact permission',
      () async {
        await Directory('${root.path}/config').create();
        final settings = File('${root.path}/config/settings.json');
        const original =
            '{"permissions":{"allow":["mcp__oc-ui__show","Read"]}}';
        await settings.writeAsString(original);
        expect(await apply('one', agent: GenUiAgent.claude), 0);
        expect(
          await apply('one', agent: GenUiAgent.claude, enabled: false),
          10,
        );
        expect(jsonDecode(await settings.readAsString()), jsonDecode(original));
      },
    );

    test(
      'Claude show preallow verifies and repairs missing permission',
      () async {
        expect(await apply('one', agent: GenUiAgent.claude), 0);
        final settings = File('${root.path}/config/settings.json');
        await settings.writeAsString('{"permissions":{"allow":["Read"]}}');
        expect(await apply('one', agent: GenUiAgent.claude, verify: true), 24);
        expect(await apply('one', agent: GenUiAgent.claude), 0);
        expect(await apply('one', agent: GenUiAgent.claude, verify: true), 0);
        expect(
          (jsonDecode(await settings.readAsString())
              as Map)['permissions']['allow'],
          ['Read', 'mcp__oc-ui__show'],
        );
      },
    );

    test(
      'Claude show preallow rejects malformed or symlinked settings',
      () async {
        await Directory('${root.path}/config').create();
        final settings = File('${root.path}/config/settings.json');
        await settings.writeAsString('{"permissions":{"allow":"Read"}}');
        expect(await apply('one', agent: GenUiAgent.claude), 21);
        expect(await File('${root.path}/managed/enabled').exists(), false);
        await settings.delete();
        final outside = File('${root.path}/outside.json');
        await outside.writeAsString('{}');
        await Link(settings.path).create(outside.path);
        expect(await apply('one', agent: GenUiAgent.claude), 22);
        expect(await outside.readAsString(), '{}');
      },
    );

    test(
      'Claude show preallow rolls back its rule on self-check failure',
      () async {
        await Directory('${root.path}/config').create();
        final settings = File('${root.path}/config/settings.json');
        const original = '{"permissions":{"allow":["Read"]},"theme":"dark"}';
        await settings.writeAsString(original);
        expect(
          await apply('one', agent: GenUiAgent.claude, failCheck: true),
          24,
        );
        expect(jsonDecode(await settings.readAsString()), jsonDecode(original));
        expect(await File('${root.path}/managed/enabled').exists(), false);
      },
    );

    test(
      'Claude show preallow migrates an existing owned registration',
      () async {
        expect(await apply('one', agent: GenUiAgent.claude), 0);
        final manifest = File('${root.path}/managed/owners.json');
        final old =
            jsonDecode(await manifest.readAsString()) as Map<String, dynamic>;
        old.remove('showPermissionAdded');
        await manifest.writeAsString(jsonEncode(old));
        await File('${root.path}/config/settings.json').writeAsString('{}');
        expect(await apply('one', agent: GenUiAgent.claude), 0);
        final settings =
            jsonDecode(
                  await File(
                    '${root.path}/config/settings.json',
                  ).readAsString(),
                )
                as Map;
        expect(settings['permissions']['allow'], ['mcp__oc-ui__show']);
        expect(
          await apply('one', agent: GenUiAgent.claude, enabled: false),
          10,
        );
        expect(
          jsonDecode(
            await File('${root.path}/config/settings.json').readAsString(),
          ),
          {},
        );
      },
    );

    test('Claude show preallow resets ownership after disable', () async {
      expect(await apply('one', agent: GenUiAgent.claude), 0);
      expect(await apply('one', agent: GenUiAgent.claude, enabled: false), 10);
      final settings = File('${root.path}/config/settings.json');
      const userSettings =
          '{"permissions":{"allow":["mcp__oc-ui__show","Read"]}}';
      await settings.writeAsString(userSettings);
      expect(await apply('one', agent: GenUiAgent.claude), 0);
      expect(await apply('one', agent: GenUiAgent.claude, enabled: false), 10);
      expect(
        jsonDecode(await settings.readAsString()),
        jsonDecode(userSettings),
      );
    });

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

    test(
      'review 5 unchanged enable keeps marker throughout verification',
      () async {
        expect(await apply('one', agent: GenUiAgent.claude), 0);
        final helper = File('${root.path}/managed/server.cjs');
        final marker = File('${root.path}/managed/enabled');
        final oldHelper = await helper.stat();
        final oldMarker = await marker.stat();
        expect(
          await apply('one', agent: GenUiAgent.claude, requireMarker: true),
          0,
        );
        expect(
          await apply(
            'one',
            agent: GenUiAgent.claude,
            verify: true,
            requireMarker: true,
          ),
          0,
        );
        expect((await helper.stat()).modified, oldHelper.modified);
        expect((await marker.stat()).modified, oldMarker.modified);
      },
    );

    test('review 6 repairs owned helper and marker corruption', () async {
      expect(await apply('one', agent: GenUiAgent.claude), 0);
      final helper = File('${root.path}/managed/server.cjs');
      final expected = await helper.readAsString();
      await helper.writeAsString('damaged helper');
      await File(
        '${root.path}/managed/enabled',
      ).writeAsString('damaged marker');
      expect(await apply('one', agent: GenUiAgent.claude), 0);
      expect(await helper.readAsString(), expected);
      expect(
        await File('${root.path}/managed/enabled').readAsString(),
        'enabled\n',
      );
      expect(await apply('one', agent: GenUiAgent.claude, verify: true), 0);
    });

    test('review 6 modified owned helper cannot block disable', () async {
      expect(await apply('one', agent: GenUiAgent.claude), 0);
      await File(
        '${root.path}/managed/server.cjs',
      ).writeAsString('damaged helper');
      expect(await apply('one', agent: GenUiAgent.claude, enabled: false), 10);
      expect(await File('${root.path}/managed/enabled').exists(), false);
      expect((await config())['mcpServers'], isEmpty);
    });

    test(
      'review 6 recovers exact helper orphan from interrupted first install',
      () async {
        expect(await apply('one', agent: GenUiAgent.claude), 0);
        await File('${root.path}/managed/owners.json').delete();
        await File('${root.path}/managed/enabled').delete();
        await File(
          '${root.path}/config/opencode.json',
        ).writeAsString('{"unrelated":42}');
        expect(await apply('one', agent: GenUiAgent.claude), 0);
        expect((await config())['unrelated'], 42);
        expect(await apply('one', agent: GenUiAgent.claude, verify: true), 0);
      },
    );

    test('review 6 unknown orphan remains an unowned collision', () async {
      await Directory('${root.path}/managed').create();
      final helper = File('${root.path}/managed/server.cjs');
      await helper.writeAsString('unrelated executable');
      expect(await apply('one', agent: GenUiAgent.claude), 21);
      expect(await helper.readAsString(), 'unrelated executable');
      expect(await File('${root.path}/managed/owners.json').exists(), false);
    });

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
