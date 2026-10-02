import 'dart:async';
import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/server_probe.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/builtin/builtin_server.dart';
import 'package:opencode_mobile/builtin/builtin_server_recovery.dart';
import 'package:opencode_mobile/builtin/setup/setup_engine.dart';
import 'package:opencode_mobile/l10n/app_localizations_en.dart';
import 'package:opencode_mobile/state/automation_policy.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// The owner's phone, 2026-10-02: with setup long finished and the person
/// only chatting, the Performance report's Recent list was several
/// `linux.setupStatus` a second, `linux.status`, and `/api/health` +
/// `/global/health` probes every few seconds. These pin the cadence.

/// A phone whose setup finished: the last job is done, nothing runs.
class _SetupLinux extends BuiltinLinux {
  int setupReads = 0;
  Map<String, Object?>? job = {
    'version': 1,
    'jobId': 'setup-1',
    'state': 'done',
    'current': null,
    'components': const [],
    'startedAt': 1,
    'updatedAt': 2,
    'error': null,
    'logTail': '',
    'params': const {},
  };

  @override
  Future<String?> setupStatus() async {
    setupReads++;
    return job == null ? null : jsonEncode(job);
  }
}

class _Store extends ProfileStore {
  _Store({required super.prefs, required this.all});
  final List<ServerProfile> all;
  @override
  List<ServerProfile> get profiles => all;
}

/// The built-in server, up and healthy.
class _ServerLinux extends BuiltinLinux {
  int statusReads = 0;
  bool running = true;

  @override
  Future<BuiltinLinuxStatus> status() async {
    statusReads++;
    return BuiltinLinuxStatus(
      installed: true,
      phase: BuiltinLinuxPhase.ready,
      serverRunning: running,
      serverRestartWanted: true,
      serverRecoveryGeneration: 1,
    );
  }

  @override
  Future<void> cancelServerRecovery() async {}

  @override
  Future<void> restartServer(
    String script, {
    int port = 4097,
    required int expectedGeneration,
  }) async {
    running = true;
  }

  @override
  Future<BuiltinLinuxRunResult> run(
    String script, {
    Duration timeout = const Duration(minutes: 2),
  }) async => const BuiltinLinuxRunResult(exitCode: 0, output: '');
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  final en = AppLocalizationsEn();

  group('setup engine', () {
    testWidgets('a page that watches a finished setup reads it once a '
        'minute, not twice a second', (tester) async {
      final linux = _SetupLinux();
      final engine = ChannelSetupEngine(linux: linux, strings: () => en);
      void listener() {}
      // The Settings tab's AI Team row, kept built behind the chat.
      engine.progress.addListener(listener);

      for (var minute = 0; minute < 20; minute++) {
        await tester.pump(const Duration(minutes: 1));
      }
      // Was 2400 (every 500 ms for 20 minutes).
      expect(linux.setupReads, inInclusiveRange(1, 21));
      engine.progress.removeListener(listener);
      engine.dispose();
    });

    testWidgets('a watched idle engine reads nothing in the background', (
      tester,
    ) async {
      final linux = _SetupLinux();
      var foreground = true;
      final engine = ChannelSetupEngine(
        linux: linux,
        strings: () => en,
        foreground: () => foreground,
      );
      void listener() {}
      engine.progress.addListener(listener);
      await tester.pump(const Duration(seconds: 1));
      final before = linux.setupReads;
      expect(before, 1);
      foreground = false;
      await tester.pump(const Duration(minutes: 10));
      expect(linux.setupReads, before);
      engine.progress.removeListener(listener);
      engine.dispose();
    });

    testWidgets('a running job is still followed every 500 ms, and a watcher '
        'that arrives brings the read forward', (tester) async {
      final linux = _SetupLinux();
      final engine = ChannelSetupEngine(linux: linux, strings: () => en);
      void listener() {}
      engine.progress.addListener(listener);
      await tester.pump(const Duration(seconds: 1));
      expect(linux.setupReads, 1);
      // A job started elsewhere (cold-start restore) is running now.
      linux.job = {...linux.job!, 'state': 'running', 'current': 'linux'};
      await engine.restore();
      final restored = linux.setupReads;
      await tester.pump(const Duration(seconds: 2));
      expect(linux.setupReads - restored, inInclusiveRange(3, 5));
      linux.job = {...linux.job!, 'state': 'done', 'current': null};
      await tester.pump(const Duration(seconds: 1));
      final done = linux.setupReads;
      await tester.pump(const Duration(seconds: 30));
      expect(linux.setupReads, done);
      engine.progress.removeListener(listener);
      engine.dispose();
    });
  });

  group('built-in server recovery', () {
    late SharedPreferences prefs;
    late _ServerLinux linux;
    late ServerProfile phone;
    late int probes;

    setUp(() async {
      SharedPreferences.setMockInitialValues({});
      prefs = await SharedPreferences.getInstance();
      AutomationPolicyController.resetShared();
      phone = ServerProfile(
        id: 'phone',
        name: 'This phone',
        baseUrl: BuiltinLinux.serverUrl,
      );
      linux = _ServerLinux();
      probes = 0;
      serverProbe = ({required baseUrl, username, password}) async {
        probes++;
        return linux.running
            ? const ServerProbeResult.success('1')
            : const ServerProbeResult.failure('Unavailable');
      };
      addTearDown(() => serverProbe = probeServerConnection);
    });

    BuiltinServerRecovery make() => BuiltinServerRecovery(
      store: _Store(prefs: prefs, all: [phone]),
      linux: linux,
      starter: BuiltinServerStarter(
        linux: linux,
        readyTimeout: Duration.zero,
        pollInterval: Duration.zero,
      ),
      onRestart: ({required profileId, required eventId, required at}) async =>
          true,
    );

    testWidgets('a healthy server is checked every 45 s in the foreground, '
        'at once on resume, and never in the background', (tester) async {
      final recovery = make();
      recovery.setProfile(phone);
      recovery.setForeground(true);
      await tester.pump();
      expect(recovery.value.phase, BuiltinRecoveryPhase.ready);
      expect(probes, 1);

      await tester.pump(const Duration(minutes: 10));
      // Was 120 checks (every 5 s): 240 status reads and 120 probes, each
      // probe two requests on OpenCode 1.
      expect(probes, inInclusiveRange(13, 15));
      expect(linux.statusReads, lessThanOrEqualTo(2 * 15));

      recovery.setForeground(false);
      final away = probes;
      await tester.pump(const Duration(minutes: 10));
      expect(probes, away);

      recovery.setForeground(true);
      await tester.pump();
      expect(probes, away + 1);
      recovery.dispose();
    });

    testWidgets('a server that went down is rechecked every 5 s', (
      tester,
    ) async {
      final recovery = make();
      recovery.setProfile(phone);
      recovery.setForeground(true);
      await tester.pump();
      linux.running = false;
      serverProbe = ({required baseUrl, username, password}) async {
        probes++;
        return const ServerProbeResult.failure('Unavailable');
      };
      linux.running = true;
      await tester.pump(const Duration(seconds: 45));
      expect(recovery.value.phase, BuiltinRecoveryPhase.unconfirmed);
      final unconfirmed = probes;
      await tester.pump(const Duration(seconds: 20));
      expect(probes - unconfirmed, inInclusiveRange(3, 4));
      recovery.dispose();
    });
  });
}
