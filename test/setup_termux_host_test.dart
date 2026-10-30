import 'dart:async';
import 'dart:convert';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/builtin/setup/setup_engine.dart';
import 'package:opencode_mobile/builtin/setup/setup_contract.dart';
import 'package:opencode_mobile/builtin/setup/termux_setup_host.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/ui/kit/kit_redact.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const channel = MethodChannel('oc/termux');
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;

  setUp(() {
    debugPlatformCapabilities = const PlatformCapabilities.android();
    KitRedact.clearKnownSecrets();
  });
  tearDown(() {
    messenger.setMockMethodCallHandler(channel, null);
    debugPlatformCapabilities = null;
    KitRedact.clearKnownSecrets();
  });

  test(
    'Termux checks use its installed base and preserve check exit status',
    () async {
      final calls = <MethodCall>[];
      messenger.setMockMethodCallHandler(channel, (call) async {
        calls.add(call);
        return switch (call.method) {
          'setupHostInstalled' => true,
          'setupRun' => {
            'stdout': 'component absent',
            'stderr': '',
            'exitCode': 1,
            'err': -1,
          },
          _ => throw MissingPluginException(),
        };
      });
      final host = TermuxSetupHost();
      expect((await host.status()).installed, isTrue);
      final check = await host.run(
        'command -v missing',
        timeout: const Duration(seconds: 12),
      );
      expect(check.ok, isFalse);
      expect(check.output, 'component absent');
      expect(calls.last.arguments, {
        'script': 'command -v missing',
        'timeoutMs': 12000,
      });
    },
  );

  test(
    'permission failures stay actionable and cannot expose credentials',
    () async {
      messenger.setMockMethodCallHandler(channel, (call) async {
        throw PlatformException(
          code: 'permission_denied',
          message: 'RUN_COMMAND denied; password=fake-private-value',
        );
      });
      await expectLater(
        TermuxSetupHost().status(),
        throwsA(
          isA<BuiltinLinuxException>()
              .having((error) => error.code, 'code', 'permission_denied')
              .having(
                (error) => error.message,
                'message',
                contains('RUN_COMMAND'),
              )
              .having(
                (error) => error.message.contains('fake-private-value'),
                'secret present',
                isFalse,
              ),
        ),
      );
    },
  );

  test(
    'Termux refuses hostless and other-host jobs before starting anything',
    () async {
      final calls = <String>[];
      String? savedHost;
      messenger.setMockMethodCallHandler(channel, (call) async {
        calls.add(call.method);
        if (call.method != 'setupStatus') throw MissingPluginException();
        return jsonEncode({
          'jobId': 'setup-old',
          'host': ?savedHost,
          'state': 'interrupted',
          'components': <String, Object?>{},
        });
      });
      final engine = ChannelSetupEngine.termux(finisher: (_) async => null);
      addTearDown(engine.dispose);
      for (final host in <String?>[null, 'builtin', 'unknown']) {
        savedHost = host;
        await expectLater(
          engine.resume(),
          throwsA(
            isA<BuiltinLinuxException>().having(
              (error) => error.code,
              'code',
              'setup_host_mismatch',
            ),
          ),
        );
      }
      expect(calls, everyElement('setupStatus'));
    },
  );
  test('unsafe or unknown raw setup params are rejected by the validator', () {
    for (final params in [
      {
        'opencode': {'password': 'fake-value'},
      },
      {
        'opencode': {'runtime': 'unsupported'},
      },
      {
        'opencode': {'version': '1.2.3; echo injected'},
      },
      {
        '_job': {'adding': 'token=fake-value'},
      },
    ]) {
      expect(
        () => TermuxSetupHost.validateParams(params),
        throwsA(
          isA<BuiltinLinuxException>().having(
            (error) => error.code,
            'code',
            'invalid_setup',
          ),
        ),
      );
    }
  });
  test('legacy runtime and version requests normalize to supported OC2', () {
    final params = effectiveJobParams({
      'opencode': {'runtime': 'opencode1', 'version': '1.19.0'},
    }, null);
    expect(params['opencode'], {'runtime': 'opencode2'});
    expect(() => TermuxSetupHost.validateParams(params), returnsNormally);
  });
  test(
    'unsafe effective setup params never generate install scripts',
    () async {
      var generated = false;
      final calls = <String>[];
      messenger.setMockMethodCallHandler(channel, (call) async {
        calls.add(call.method);
        if (call.method == 'setupStatus') return null;
        throw MissingPluginException();
      });
      final engine = ChannelSetupEngine.termux(
        finisher: (_) async => null,
        components: (_, _) {
          generated = true;
          return [];
        },
      );
      addTearDown(engine.dispose);
      for (final params in [
        {
          'opencode': {'password': 'fake-value'},
        },
        {
          'opencode': {'version': '2.1.1; echo injected'},
        },
        {
          '_job': {'adding': 'token=fake-value'},
        },
      ]) {
        await expectLater(
          engine.run({}, params: params),
          throwsA(
            isA<BuiltinLinuxException>().having(
              (error) => error.code,
              'code',
              'invalid_setup',
            ),
          ),
        );
      }
      expect(generated, isFalse);
      expect(calls, everyElement('setupStatus'));
    },
  );

  test(
    'inaccessible or corrupt durable state never becomes a fresh job',
    () async {
      final calls = <String>[];
      var inaccessible = true;
      messenger.setMockMethodCallHandler(channel, (call) async {
        calls.add(call.method);
        if (inaccessible) {
          throw PlatformException(
            code: 'permission_denied',
            message: 'RUN_COMMAND denied',
          );
        }
        return '{invalid';
      });
      final engine = ChannelSetupEngine.termux(finisher: (_) async => null);
      addTearDown(engine.dispose);
      // A start that cannot read the saved job publishes a failure; it never
      // starts a fresh job over it.
      await engine.run({});
      expect(engine.progress.value.state, SetupState.failed);
      inaccessible = false;
      await engine.run({});
      expect(engine.progress.value.state, SetupState.failed);
      expect(calls, everyElement('setupStatus'));
    },
  );
  test(
    'lost start reply observes the durable running job without restarting',
    () async {
      Map<String, Object?>? job;
      var starts = 0;
      messenger.setMockMethodCallHandler(channel, (call) async {
        switch (call.method) {
          case 'setupStatus':
            return job == null ? null : jsonEncode(job);
          case 'setupHostInstalled':
            return false;
          case 'startSetup':
            starts++;
            final args = call.arguments as Map;
            job = {
              'jobId': args['jobId'],
              'host': 'termux',
              'state': 'running',
              'current': 'linux',
              'order': ['linux'],
              'components': {
                'linux': {'state': 'running', 'weight': 1},
              },
            };
            throw PlatformException(
              code: 'termux_setup',
              message: 'Reply timed out.',
            );
          default:
            throw MissingPluginException();
        }
      });
      final engine = ChannelSetupEngine.termux(
        finisher: (_) async => null,
        components: (_, _) => const [
          SetupComponent(
            id: 'linux',
            title: 'Linux',
            shortTitle: 'Linux',
            checkScript: '',
            installScript: '',
            native: true,
            required: true,
          ),
        ],
      );
      addTearDown(engine.dispose);
      await engine.run({'linux'});
      expect(engine.progress.value.state, SetupState.running);
      expect(engine.progress.value.jobId, job!['jobId']);
      expect(engine.progress.value.current, 'linux');
      expect(engine.progress.value.error, isNull);
      await engine.run({'linux'});
      expect(starts, 1);
    },
  );
  test('queued cancellation survives lost start and status replies', () async {
    final dispatchStarted = Completer<void>();
    final releaseReply = Completer<void>();
    final cancelled = Completer<void>();
    Map<String, Object?>? job;
    var loseStatus = false;
    var cancelCalls = 0;
    var finishes = 0;
    messenger.setMockMethodCallHandler(channel, (call) async {
      switch (call.method) {
        case 'setupStatus':
          if (loseStatus) {
            loseStatus = false;
            throw PlatformException(
              code: 'termux_setup',
              message: 'Status unavailable.',
            );
          }
          return job == null ? null : jsonEncode(job);
        case 'setupHostInstalled':
          return false;
        case 'startSetup':
          final args = call.arguments as Map;
          job = {
            'jobId': args['jobId'],
            'host': 'termux',
            'state': 'running',
            'current': 'start',
            'order': ['linux', 'start'],
            'components': {
              'linux': {'state': 'done', 'weight': 1},
              'start': {'state': 'running', 'weight': 1},
            },
          };
          dispatchStarted.complete();
          await releaseReply.future;
          loseStatus = true;
          throw PlatformException(
            code: 'termux_setup',
            message: 'Reply timed out.',
          );
        case 'cancelSetup':
          cancelCalls++;
          job!['state'] = 'cancelled';
          cancelled.complete();
          return null;
        default:
          throw MissingPluginException();
      }
    });
    final engine = ChannelSetupEngine.termux(
      finisher: (_) async {
        finishes++;
        return null;
      },
      pollInterval: const Duration(milliseconds: 1),
      components: (_, _) => const [
        SetupComponent(
          id: 'linux',
          title: 'Linux',
          shortTitle: 'Linux',
          checkScript: '',
          installScript: '',
          native: true,
          required: true,
        ),
        SetupComponent(
          id: 'start',
          title: 'Connect',
          shortTitle: 'Connect',
          checkScript: '',
          installScript: '',
          jobStep: true,
          required: true,
        ),
      ],
    );
    addTearDown(engine.dispose);
    final running = engine.run({'linux'});
    await dispatchStarted.future;
    await engine.cancel();
    expect(cancelCalls, 0);
    releaseReply.complete();
    await running;
    await cancelled.future.timeout(const Duration(seconds: 2));
    expect(cancelCalls, 1);
    expect(finishes, 0);
  });

  test(
    'a phone without Termux is asked once per watch, not every minute',
    () async {
      var reads = 0;
      messenger.setMockMethodCallHandler(channel, (call) async {
        if (call.method == 'setupStatus') {
          reads++;
          throw PlatformException(
            code: 'permission_denied',
            message: 'Termux RUN_COMMAND permission is required.',
          );
        }
        throw MissingPluginException();
      });
      final engine = ChannelSetupEngine.termux(
        finisher: (_) async => null,
        pollInterval: const Duration(milliseconds: 1),
        idlePollInterval: const Duration(milliseconds: 2),
        foreground: () => true,
      );
      addTearDown(engine.dispose);
      void listener() {}
      engine.progress.addListener(listener);
      await Future<void>.delayed(const Duration(milliseconds: 120));
      expect(reads, 1);
      // A page that starts watching again asks again, once.
      engine.progress.removeListener(listener);
      engine.progress.addListener(listener);
      await Future<void>.delayed(const Duration(milliseconds: 120));
      expect(reads, 2);
      engine.progress.removeListener(listener);
    },
  );
}
