import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/phone_processes.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const channel = MethodChannel('test.phone_processes');
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  late List<MethodCall> calls;
  late Object? response;
  late PhoneProcesses processes;

  setUp(() {
    calls = [];
    processes = PhoneProcesses(channel: channel);
    debugPlatformCapabilities = const PlatformCapabilities.android();
    messenger.setMockMethodCallHandler(channel, (call) async {
      calls.add(call);
      return response;
    });
  });
  tearDown(() {
    messenger.setMockMethodCallHandler(channel, null);
    debugPlatformCapabilities = null;
  });

  test('a native reading becomes typed rows and reaper counts', () async {
    response = {
      'processes': [
        {
          'pid': 20100,
          'ppid': 20005,
          'name': 'claude',
          'command': 'claude --resume abc --print',
          'cpuPercent': 12.5,
          'rssBytes': 409600,
          'elapsedSeconds': 90,
          'startTicks': 500,
          'owner': 'paseo',
          'session': 'abc',
        },
        {'pid': 15001, 'owner': 'orphan', 'cpuPercent': 99},
        {'pid': 'not a pid'},
        {'pid': 7, 'owner': 'from-a-newer-apk'},
      ],
      'sampledMs': 1003,
      'orphansReaped': 6,
      'orphansLastReapedAtMs': 1000,
    };
    final snapshot = await processes.list();
    expect(calls.single.method, 'processes');
    expect(snapshot.processes.map((p) => p.pid), [20100, 15001, 7]);
    final claude = snapshot.processes.first;
    expect(claude.owner, PhoneProcessOwner.paseo);
    expect(claude.session, 'abc');
    expect(claude.cpuPercent, 12.5);
    expect(claude.rssBytes, 409600);
    expect(claude.elapsed, const Duration(seconds: 90));
    expect(snapshot.processes[1].owner, PhoneProcessOwner.orphan);
    expect(snapshot.processes[1].cpuPercent, 99.0);
    expect(snapshot.processes[1].session, isNull);
    expect(snapshot.processes[2].owner, PhoneProcessOwner.other);
    expect(snapshot.orphansReaped, 6);
    expect(
      snapshot.orphansLastReapedAt,
      DateTime.fromMillisecondsSinceEpoch(1000, isUtc: true),
    );
  });

  test('stop sends each pid once and reads stopped and refused', () async {
    response = {
      'stopped': [15001],
      'refused': [
        {'pid': 9000, 'reason': 'app'},
        {'pid': 20000, 'reason': 'serverRoot'},
        {'pid': 4242, 'reason': 'notFound'},
      ],
    };
    final result = await processes.stop([15001, 9000, 15001, 20000, 4242]);
    expect(calls.single.method, 'stopProcesses');
    expect(calls.single.arguments, {
      'pids': [15001, 9000, 20000, 4242],
    });
    expect(result.stopped, [15001]);
    expect(result.refused, {
      9000: PhoneProcessStopRefusal.app,
      20000: PhoneProcessStopRefusal.serverRoot,
      4242: PhoneProcessStopRefusal.notFound,
    });
  });

  test('off Android nothing is asked', () async {
    debugPlatformCapabilities = const PlatformCapabilities.linuxDesktop();
    expect((await processes.list()).processes, isEmpty);
    expect((await processes.stop([1])).stopped, isEmpty);
    expect(calls, isEmpty);
  });
}
