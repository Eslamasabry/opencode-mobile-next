// TEAM-301: the managed Gas City runtime on the phone, Dart side.
//
// The status parsing, the runtime's dispatch and polling against a scripted
// runner, the pinned downloads it reports, and the loopback control mode of
// the Gas City gateway. The shell half (aiteam.sh run for real inside a
// stand-in for Termux's Ubuntu) is test/termux_aiteam_script_test.dart.
@Timeout(Duration(minutes: 3))
library;

import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/setup/aiteam_scripts.dart';
import 'package:opencode_mobile/builtin/team/builtin_team.dart';
import 'package:opencode_mobile/domain/orchestration_gateway.dart';
import 'package:opencode_mobile/orchestration/adapters/gascity/gascity_gateway.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/termux/bridge.dart';
import 'package:opencode_mobile/termux/team_runtime.dart';
import 'package:opencode_mobile/termux/team_scripts.dart';

void main() {
  group('TeamRuntimeStatus', () {
    test('parses the download failure detail', () {
      final failure = TeamDownloadFailure.parse(
        'download http github.com 404',
      )!;
      expect(failure.kind, TeamDownloadFailureKind.http);
      expect(failure.host, 'github.com');
      expect(failure.code, 404);
      expect(
        TeamDownloadFailure.parse('download')!.kind,
        TeamDownloadFailureKind.other,
      );
      expect(
        TeamDownloadFailure.parse('download brand-new h 1')!.kind,
        TeamDownloadFailureKind.other,
      );
      expect(TeamDownloadFailure.parse('manifest-download'), isNull);
      expect(TeamDownloadFailure.parse('checksum-mismatch gc'), isNull);
      expect(TeamDownloadFailure.parse(null), isNull);
    });

    test('parses every phase and the failed reason', () {
      for (final entry in {
        'idle': TeamRuntimePhase.idle,
        'queued': TeamRuntimePhase.queued,
        'downloading': TeamRuntimePhase.downloading,
        'verifying': TeamRuntimePhase.verifying,
        'installing-packages': TeamRuntimePhase.installingPackages,
        'installed': TeamRuntimePhase.installed,
        'creating-city': TeamRuntimePhase.creatingCity,
        'city-ready': TeamRuntimePhase.cityReady,
        'starting': TeamRuntimePhase.starting,
        'ready': TeamRuntimePhase.ready,
        'stopping': TeamRuntimePhase.stopping,
        'stopped': TeamRuntimePhase.stopped,
        'removing': TeamRuntimePhase.removing,
        'failed': TeamRuntimePhase.failed,
        'failed:checksum-mismatch gc': TeamRuntimePhase.failed,
        'something-new': TeamRuntimePhase.unknown,
      }.entries) {
        expect(
          TeamRuntimePhase.parse(entry.key),
          entry.value,
          reason: entry.key,
        );
      }
      expect(TeamRuntimePhase.downloading.isTransient, isTrue);
      expect(TeamRuntimePhase.ready.isTransient, isFalse);
      final status = TeamRuntimeStatus.parse(
        '{"installed":true,"versions":{"gc":"1.4.1","bd":null,"dolt":"2.3.3"},'
        '"phase":"failed","message":"gc did not match","verb":"install",'
        '"busy":false,"pid":null,"supervisor_pid":null,"health":null,'
        '"agents":null,"city":null,"rig":null,"project":null,'
        '"url":"http://127.0.0.1:8372","last_error":"gc did not match",'
        '"state_phase":"failed:checksum-mismatch gc","reason":"checksum-mismatch gc",'
        '"killed_by_android":false,"removed":[],"log":"/l","updated_at":1700000000}',
      );
      expect(status.phase, TeamRuntimePhase.failed);
      expect(status.rawPhase, 'failed:checksum-mismatch gc');
      expect(status.reason, 'checksum-mismatch gc');
      expect(
        TeamRuntimeStatus.parse('{"phase":"failed:x"}').reason,
        'x',
        reason: 'a status without state_phase still yields the token',
      );
      expect(status.versions, {'gc': '1.4.1', 'dolt': '2.3.3'});
      expect(status.checksumMismatch, isTrue);
      expect(status.city, '');
      expect(status.hasCity, isFalse);
      expect(status.updatedAt, DateTime.utc(2023, 11, 14, 22, 13, 20));
      expect(TeamRuntimeStatus.parse('garbage'), TeamRuntimeStatus.unreadable);
      expect(TeamRuntimeStatus.parse('[1]').phase, TeamRuntimePhase.unknown);
    });

    test('killedByAndroid and readiness', () {
      final killed = TeamRuntimeStatus.parse(
        '{"phase":"ready","killed_by_android":true,"health":"unreachable",'
        '"city":"phone","supervisor_pid":null}',
      );
      expect(killed.killedByAndroid, isTrue);
      expect(killed.isReady, isFalse);
      final ready = TeamRuntimeStatus.parse(
        '{"phase":"ready","killed_by_android":false,"health":"ok",'
        '"city":"phone","supervisor_pid":42,"agents":1}',
      );
      expect(ready.isReady, isTrue);
      expect(ready.supervisorPid, 42);
      expect(ready.agents, 1);
    });
  });

  group('TermuxTeamRuntime', () {
    test('supportsAiTeam: a 64-bit phone and the pinned upstream builds for '
        'its CPU', () async {
      Future<String> noRun(String script, {Duration? timeout}) async => '';
      final phone = TermuxTeamRuntime(
        runner: noRun,
        archProbe: () async => 'aarch64\n',
      );
      expect(await phone.supportsAiTeam, isTrue);
      expect(await phone.unsupportedReason, isNull);
      final pinned = (await phone.manifest())!;
      expect(pinned.arch, 'arm64');
      expect(pinned.gascity, AiTeamPins.gascity);
      expect(pinned.baseUrl, isEmpty);
      expect(pinned.json, isNot(contains('aiteam-assets-1')));
      for (final download in AiTeamPins.arm64) {
        expect(pinned.json, contains(download.url));
      }
      // The emulator gets the x86_64 builds, which upstream publishes too.
      final x86 = TermuxTeamRuntime(
        runner: noRun,
        archProbe: () async => 'x86_64',
      );
      expect(await x86.supportsAiTeam, isTrue);
      expect((await x86.manifest())!.arch, 'x86_64');
      final arm32 = TermuxTeamRuntime(
        runner: noRun,
        archProbe: () async => 'armv7l',
      );
      expect(await arm32.supportsAiTeam, isFalse);
      expect(await arm32.unsupportedReason, contains('64-bit ARM'));
      final noManifest = TermuxTeamRuntime(
        runner: noRun,
        manifestLoader: () async => null,
        archProbe: () async => 'aarch64',
      );
      expect(await noManifest.supportsAiTeam, isFalse);
      expect(await noManifest.unsupportedReason, contains('ships no'));
      expect(TeamRuntimeManifest.parse('{"schema":2}'), isNull);
      expect(TeamRuntimeManifest.parse('nope'), isNull);
    });

    test(
      'a verb dispatches, polls status until idle and answers the last',
      () async {
        final scripts = <String>[];
        var polls = 0;
        Future<String> runner(String script, {Duration? timeout}) async {
          scripts.add(script);
          if (script.contains('exec bash "\$AITEAM" status')) {
            polls++;
            return polls < 3
                ? '{"phase":"downloading","busy":true,"pid":7}'
                : '{"phase":"installed","busy":false,"installed":true}';
          }
          return 'aiteam-started:7\n';
        }

        final runtime = TermuxTeamRuntime(
          runner: runner,
          archProbe: () async => 'aarch64',
          pollInterval: const Duration(milliseconds: 10),
        );
        final status = await runtime.install();
        expect(status.phase, TeamRuntimePhase.installed);
        expect(polls, 3);
        expect(scripts.first, contains("nohup bash \"\$AITEAM\" 'install'"));
        expect(scripts.first, contains('bash "\$AITEAM" queue \'install\''));
        expect(scripts.first, contains('OC_AITEAM_PINS_EOF'));
        expect(scripts.first, contains(TermuxTeamScripts.pinsFile()));
        expect(scripts.first, isNot(contains('OC_AITEAM_RIG_EOF')));

        scripts.clear();
        await runtime.init(
          "/root/projects/it's here",
          city: 'phone',
          rig: 'app',
        );
        expect(
          scripts.first,
          contains(
            "'init' '/root/projects/it'\"'\"'s here' '--city' 'phone' '--rig' 'app'",
          ),
        );
        // The team's script for that project, named as asked.
        expect(
          scripts.first,
          contains(
            TermuxTeamScripts.rigFile("/root/projects/it's here", rig: 'app'),
          ),
        );
        for (final verb in ['start', 'stop', 'remove']) {
          scripts.clear();
          await switch (verb) {
            'start' => runtime.start(),
            'stop' => runtime.stop(),
            _ => runtime.remove(),
          };
          expect(scripts.first, contains("nohup bash \"\$AITEAM\" '$verb'"));
        }
      },
    );

    test(
      'a refused dispatch is a bridge error, and statusStream ends on idle',
      () async {
        Future<String> runner(String script, {Duration? timeout}) async {
          if (script.contains('exec bash "\$AITEAM" status')) {
            return '{"phase":"ready","busy":false,"health":"ok","city":"phone"}';
          }
          throw const TermuxBridgeException(
            'aiteam-busy:install:12',
            code: 'command_failed',
          );
        }

        final runtime = TermuxTeamRuntime(
          runner: runner,
          archProbe: () async => 'aarch64',
        );
        await expectLater(
          runtime.start(),
          throwsA(isA<TermuxBridgeException>()),
        );
        await expectLater(
          runtime.install(),
          throwsA(isA<TermuxBridgeException>()),
        );
        final statuses = await runtime.statusStream().toList();
        expect(statuses, hasLength(1));
        expect(statuses.single.isReady, isTrue);
      },
    );

    test(
      'logTail, managedProjects and createManagedProject (TEAM-302)',
      () async {
        final scripts = <String>[];
        final runtime = TermuxTeamRuntime(
          runner: (script, {Duration? timeout}) async {
            scripts.add(script);
            if (script.contains('aiteam.log')) return '[aiteam] hello\n';
            if (script.contains('mkdir -p')) return '/root/projects/new-app\n';
            if (script.contains('proot-distro')) {
              return 'calc\nnotes\n';
            }
            return '';
          },
          archProbe: () async => 'aarch64',
        );
        expect(await runtime.logTail(lines: 50), '[aiteam] hello\n');
        expect(scripts.last, contains('tail -n 50'));
        expect(scripts.last, contains('.oc/aiteam/aiteam.log'));
        expect(await runtime.managedProjects(), [
          '/root/projects/calc',
          '/root/projects/notes',
        ]);
        expect(scripts.last, contains('containers/opencode-ubuntu/rootfs'));
        expect(scripts.last, contains('installed-rootfs/opencode-ubuntu'));
        expect(
          await runtime.createManagedProject('new-app'),
          '/root/projects/new-app',
        );
        await expectLater(
          runtime.createManagedProject('../x'),
          throwsA(isA<TermuxBridgeException>()),
        );
        expect(
          () => TermuxBridge.aiteamLogTailScript(lines: 0),
          throwsArgumentError,
        );
        // A bridge failure reads as no log and no projects, never an error.
        final broken = TermuxTeamRuntime(
          runner: (_, {Duration? timeout}) async =>
              throw const TermuxBridgeException('gone'),
          archProbe: () async => 'aarch64',
        );
        expect(await broken.logTail(), '');
        expect(await broken.managedProjects(), isEmpty);
        // The projects script lists directories only, skipping bare origins.
        final tmp = Directory.systemTemp.createTempSync('oc-projects-');
        addTearDown(() => tmp.deleteSync(recursive: true));
        final projects =
            '${tmp.path}/var/lib/proot-distro/installed-rootfs/opencode-ubuntu/root/projects';
        Directory('$projects/calc').createSync(recursive: true);
        Directory('$projects/calc.git').createSync(recursive: true);
        Directory('$projects/zeta').createSync(recursive: true);
        File('$projects/README').writeAsStringSync('');
        final listed = Process.runSync(
          'bash',
          ['-c', TermuxBridge.aiteamProjectsScript()],
          environment: {'PREFIX': tmp.path},
        );
        expect(listed.exitCode, 0, reason: '${listed.stderr}');
        expect(listed.stdout.toString().trim().split('\n'), ['calc', 'zeta']);
      },
    );

    test('the download size is the pinned archives plus the packages', () {
      final arm64 = TeamRuntimeManifest.pinned('arm64', AiTeamPins.arm64);
      expect(arm64.totalBytes, AiTeamPins.bytesFor(AiTeamPins.arm64) + 3000000);
      // About 115 MB, not the ~290 MB of the old Android builds.
      expect(arm64.totalBytes, inInclusiveRange(100000000, 130000000));
      // The old native layout's manifests still parse (they are still
      // shipped, and checked by shipped_download_urls_test), unused.
      final legacy = TeamRuntimeManifest.parse(
        File('assets/aiteam/manifest.json').readAsStringSync(),
      )!;
      expect(legacy.totalBytes, greaterThan(250 * 1000 * 1000));
      expect(
        TeamRuntimeManifest.parse(
          '{"schema":1,"arch":"arm64","files":{"gc":{},"bd":{},"dolt":{},"wrapper":{}}}',
        )!.totalBytes,
        0,
      );
    });

    test('the phone config is Gas City on loopback with this phone as host', () {
      final runtime = TermuxTeamRuntime(
        runner: (script, {Duration? timeout}) async => '',
        archProbe: () async => 'aarch64',
      );
      final status = TeamRuntimeStatus.parse(
        '{"phase":"ready","health":"ok","city":"phone","url":"http://127.0.0.1:8372"}',
      );
      final config = runtime.phoneOrchestrationConfig(
        status,
        enabledAt: DateTime.utc(2026, 9, 11),
      );
      expect(config.provider, OrchestrationProvider.gascity);
      expect(config.url, 'http://127.0.0.1:8372');
      expect(config.city, 'phone');
      expect(config.hostMode, OrchestrationHostMode.phone);
      expect(config.hostKind, OrchestrationHostKind.phone);
      expect(config.front, isFalse);
      expect(config.enabledAt, DateTime.utc(2026, 9, 11));
      // Never taken for the in-app team (its own port, 8472).
      expect(BuiltinTeam.isBuiltinConfig(config), isFalse);
      final restored = OrchestrationConfig.fromJson(config.toJson())!;
      expect(restored.hostMode, OrchestrationHostMode.phone);
      expect(restored.hostKind, OrchestrationHostKind.phone);
    });
  });

  group('GasCityGateway on the phone', () {
    test('loopback + phone host turns controls on without a front and '
        'posts with X-GC-Request only', () async {
      final requests = <HttpRequest>[];
      final bodies = <String>[];
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      addTearDown(() => server.close(force: true));
      server.listen((request) async {
        requests.add(request);
        bodies.add(await utf8.decoder.bind(request).join());
        request.response
          ..statusCode = 202
          ..headers.contentType = ContentType.json
          ..write('{"request_id":"corr-1"}');
        await request.response.close();
      });
      final url = 'http://127.0.0.1:${server.port}';
      final phone = GasCityGateway(
        url: url,
        city: 'phone',
        hostMode: OrchestrationHostMode.phone,
      );
      addTearDown(phone.close);
      expect(phone.loopbackControl, isTrue);
      expect(
        phone.capabilities.asMap(),
        OrchestrationCapabilities.gascityLoopback.asMap(),
      );
      expect(phone.capabilities.anyControl, isTrue);
      expect(phone.capabilities.phoneHost, isTrue);
      expect(phone.capabilities.mergeReadiness, isFalse);
      final receipt = await phone.assign(
        'oc-1',
        agentId: 'gastown.polecat',
        requestId: 'req-1',
      );
      expect(
        receipt.status,
        MutationReceiptStatus.accepted,
        reason: receipt.message,
      );
      expect(receipt.correlationId, 'corr-1');
      expect(requests, hasLength(1));
      expect(requests.single.uri.path, '/v0/city/phone/sling');
      expect(bodies.single, contains('"bead":"oc-1"'));
      expect(requests.single.headers.value('x-gc-request'), 'req-1');
      expect(requests.single.headers.value('idempotency-key'), isNull);
      // The front-only routes stay absent on loopback.
      expect(await phone.mergeReadiness('run-1'), isNull);
      expect(await phone.policy(), isNull);
      expect(
        (await phone.merge('run-1', requestId: 'm')).message,
        'front required',
      );
      expect(requests, hasLength(1));

      // The same URL as a computer host stays read-only (no front).
      final computer = GasCityGateway(url: url, city: 'phone');
      addTearDown(computer.close);
      expect(computer.loopbackControl, isFalse);
      expect(computer.capabilities.anyControl, isFalse);
      expect(
        (await computer.assign('oc-1', agentId: 'a', requestId: 'r')).message,
        'front required',
      );
      // And a phone host off loopback never gets the shortcut.
      final tailnet = GasCityGateway(
        url: 'http://100.64.0.9:8372',
        city: 'phone',
        hostMode: OrchestrationHostMode.phone,
      );
      addTearDown(tailnet.close);
      expect(tailnet.loopbackControl, isFalse);
      expect(tailnet.capabilities.anyControl, isFalse);
    });

    test('gascityLoopback is gascityRead plus the control switches, '
        'controlCreateWork (TEAM-306) and phoneHost', () {
      final read = OrchestrationCapabilities.gascityRead.asMap();
      final loopback = OrchestrationCapabilities.gascityLoopback.asMap();
      final front = OrchestrationCapabilities.gascityFront.asMap();
      for (final entry in loopback.entries) {
        final expected = switch (entry.key) {
          // The direct task path exists only where the planner can be
          // off and the supervisor is reachable: the phone's loopback.
          'phoneHost' || 'controlCreateWork' => true,
          // The phone's own city is set up and removed by the app; a
          // project is not deleted from it by hand (OD1).
          'controlProjectRemove' => false,
          final key when key.startsWith('control') => front[key],
          final key => read[key],
        };
        expect(entry.value, expected, reason: entry.key);
      }
      expect(front['controlCreateWork'], isFalse);
    });
  });
}
