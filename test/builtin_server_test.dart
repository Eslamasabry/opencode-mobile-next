import 'package:clock/clock.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/server_probe.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/builtin/builtin_server.dart';
import 'package:opencode_mobile/l10n/app_localizations_en.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/termux/bridge.dart';

/// The Android side as the starter sees it: Ubuntu installed or not, one
/// server that runs once started (or dies at once when [serverDies]).
class _FakeLinux extends BuiltinLinux {
  bool installed = true;
  bool serverRunning = false;
  bool serverDies = false;
  int statusReads = 0;
  final scripts = <String>[];
  final started = <String>[];
  BuiltinLinuxRunResult Function(String script)? runAnswer;

  @override
  Future<BuiltinLinuxStatus> status() async {
    statusReads++;
    return BuiltinLinuxStatus(
      installed: installed,
      phase: installed ? BuiltinLinuxPhase.ready : BuiltinLinuxPhase.idle,
      serverRunning: serverRunning,
    );
  }

  @override
  Future<BuiltinLinuxRunResult> run(
    String script, {
    Duration timeout = const Duration(minutes: 2),
  }) async {
    scripts.add(script);
    return runAnswer?.call(script) ??
        const BuiltinLinuxRunResult(exitCode: 0, output: '');
  }

  @override
  Future<void> startServer(String script, {int port = 4097}) async {
    started.add(script);
    serverRunning = !serverDies;
  }
}

ServerProfile _inApp({ServerFlavor flavor = ServerFlavor.v1}) =>
    ServerProfile(
        id: 'builtin',
        name: 'This phone, built-in',
        baseUrl: BuiltinLinux.serverUrl,
        flavor: flavor,
      )
      ..username = BuiltinLinux.serverUsername
      ..password = 'secret';

void main() {
  late _FakeLinux linux;

  setUp(() {
    linux = _FakeLinux();
    serverProbe = ({required baseUrl, username, password}) async =>
        linux.serverRunning
        ? const ServerProbeResult.success('1.18.29')
        : const ServerProbeResult.failure('refused');
  });

  tearDown(() => serverProbe = probeServerConnection);

  group('recognising the in-app server', () {
    test('needs the address and Ubuntu installed here', () async {
      expect(await isInAppServer(_inApp(), linux), isTrue);
      linux.installed = false;
      expect(await isInAppServer(_inApp(), linux), isFalse);
      linux.installed = true;
      final termux = ServerProfile(
        id: 't',
        name: 'Termux',
        baseUrl: 'http://127.0.0.1:4096',
      );
      expect(await isInAppServer(termux, linux), isFalse);
      expect(linux.statusReads, 2, reason: 'the wrong port never asks');
    });
  });

  group('startBuiltinServer', () {
    test('writes the password, starts the profile runtime, waits', () async {
      final failure = await startBuiltinServer(
        linux: linux,
        profile: _inApp(flavor: ServerFlavor.v2),
        pollInterval: Duration.zero,
      );
      expect(failure, isNull);
      expect(linux.scripts.single, BuiltinLinux.writePasswordScript('secret'));
      expect(linux.started.single, contains('opencode2 serve'));
      expect(
        linux.started.single,
        BuiltinLinux.serverScript(runtime: TermuxRuntime.openCode2),
      );
    });

    test('cancelled confirmation never reports successful startup', () async {
      var wanted = true;
      serverProbe = ({required baseUrl, username, password}) async {
        wanted = false;
        return const ServerProbeResult.success('1.18.29');
      };
      final failure = await startBuiltinServer(
        linux: linux,
        profile: _inApp(),
        pollInterval: Duration.zero,
        stillWanted: () => wanted,
      );
      expect(failure, isNotNull);
      expect(linux.started, hasLength(1));
    });

    test('cancelled owner never starts the server', () async {
      final failure = await startBuiltinServer(
        linux: linux,
        profile: _inApp(),
        pollInterval: Duration.zero,
        stillWanted: () => false,
      );
      expect(failure, isNotNull);
      expect(linux.started, isEmpty);
    });

    test('a server that exits is reported as such', () async {
      linux.serverDies = true;
      final failure = await startBuiltinServer(
        linux: linux,
        profile: _inApp(),
        pollInterval: Duration.zero,
      );
      expect(failure?.reason(AppLocalizationsEn()), 'the server stopped');
    });
  });

  group('BuiltinServerStarter', () {
    late BuiltinServerStarter starter;

    setUp(() {
      starter = BuiltinServerStarter(linux: linux, pollInterval: Duration.zero);
    });

    tearDown(() => starter.dispose());

    test('starts a stopped server once, then not again', () async {
      expect(starter.recognises(_inApp()), isFalse);
      expect(await starter.autoStartIfStopped(_inApp()), isTrue);
      expect(linux.started, hasLength(1));
      expect(starter.readyCount, 1);
      expect(starter.recognises(_inApp()), isTrue);

      // The server dies; without the app opening or resuming again, nothing
      // starts it on its own.
      linux.serverRunning = false;
      expect(await starter.autoStartIfStopped(_inApp()), isFalse);
      expect(linux.started, hasLength(1));

      starter.allowAutoStart();
      expect(await starter.autoStartIfStopped(_inApp()), isTrue);
      expect(linux.started, hasLength(2));
    });

    test('a failed start is kept for the card and not retried', () async {
      linux.serverDies = true;
      expect(await starter.autoStartIfStopped(_inApp()), isFalse);
      expect(starter.failureFor(_inApp()), isNotNull);
      expect(await starter.autoStartIfStopped(_inApp()), isFalse);
      expect(linux.started, hasLength(1));
      starter.clearFailure();
      expect(starter.failureFor(_inApp()), isNull);
    });

    test('leaves a running server alone', () async {
      linux.serverRunning = true;
      expect(await starter.autoStartIfStopped(_inApp()), isFalse);
      expect(linux.started, isEmpty);
      expect(starter.recognises(_inApp()), isTrue);
    });

    test('does nothing when Ubuntu is not installed', () async {
      linux.installed = false;
      expect(await starter.autoStartIfStopped(_inApp()), isFalse);
      expect(linux.started, isEmpty);
      expect(starter.recognises(_inApp()), isFalse);
    });

    test('names the server it brings up while starting and for a short '
        'handover after a confirmed start', () async {
      final target = _inApp(flavor: ServerFlavor.v2);
      var now = DateTime(2026, 10, 8, 12);
      await withClock(Clock(() => now), () async {
        expect(starter.bringingUp, isNull);
        final start = starter.start(target);
        expect(starter.startingProfile, same(target));
        expect(starter.bringingUp, same(target));
        expect(await start, isNull);
        expect(starter.startingProfile, isNull);
        // Answered; the app connects to it within moments.
        expect(starter.bringingUp, same(target));
        now = now.add(BuiltinServerStarter.handover);
        expect(starter.bringingUp, isNull);
        // An automatic start (the app reopening) is no handover.
        linux.serverRunning = false;
        expect(await starter.autoStartIfStopped(_inApp()), isTrue);
        expect(starter.bringingUp, isNull);
      });
    });
  });

  group('BuiltinProjectFolders', () {
    test('create reports whether the folder is new', () async {
      final folders = BuiltinProjectFolders(linux);
      linux.runAnswer = (_) => const BuiltinLinuxRunResult(
        exitCode: 0,
        output: 'created /root/projects/hello\n',
      );
      expect(await folders.create('/root/projects/hello'), (
        path: '/root/projects/hello',
        created: true,
      ));
      expect(
        linux.scripts.last,
        BuiltinLinux.createFolderScript('/root/projects/hello'),
      );
      linux.runAnswer = (_) => const BuiltinLinuxRunResult(
        exitCode: 0,
        output: 'exists /root/projects/hello\n',
      );
      expect((await folders.create('/root/projects/hello')).created, isFalse);
      linux.runAnswer = (_) =>
          const BuiltinLinuxRunResult(exitCode: 1, output: 'mkdir: denied');
      await expectLater(
        folders.create('/root/projects/hello'),
        throwsA(isA<BuiltinLinuxException>()),
      );
    });

    test(
      'exists reads test -d, and a bridge failure is not "missing"',
      () async {
        final folders = BuiltinProjectFolders(linux);
        linux.runAnswer = (_) =>
            const BuiltinLinuxRunResult(exitCode: 1, output: '');
        expect(await folders.exists('/root/projects/x'), isFalse);
        linux.runAnswer = (_) =>
            const BuiltinLinuxRunResult(exitCode: 0, output: '');
        expect(await folders.exists('/root/projects/x'), isTrue);
        linux.runAnswer = (_) =>
            const BuiltinLinuxRunResult(exitCode: -1, output: 'timed out');
        await expectLater(
          folders.exists('/root/projects/x'),
          throwsA(isA<BuiltinLinuxException>()),
        );
      },
    );
  });
}
