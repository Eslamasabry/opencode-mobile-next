import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/server_probe.dart';
import 'package:opencode_mobile/builtin/app_exit_recovery.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/builtin/builtin_server.dart';
import 'package:opencode_mobile/builtin/team/builtin_team.dart';
import 'package:opencode_mobile/diagnostics/app_diagnostics.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/platform/app_exit.dart';
import 'package:opencode_mobile/platform/keep_alive_advice.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/automation_policy.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/keep_running_screen.dart';
import 'package:opencode_mobile/ui/screens/settings_screen.dart';
import 'package:opencode_mobile/ui/widgets/app_exit_notice.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// The owner's phone (RedMagic NX721J, Android 15), `dumpsys activity
/// exit-info`: `reason=10 (USER REQUESTED) subreason=21 (FORCE STOP) …
/// from pid 2285 (system) … importance=125` at 2026-09-26 00:06:33.
final _ownerRecord = <String, Object?>{
  'reason': 10,
  'subReason': 21,
  'status': 0,
  'importance': 125,
  'timestamp': DateTime(2026, 9, 26, 0, 6, 33).millisecondsSinceEpoch,
  'description':
      'stop io.github.eslamasabry.opencode_mobile due to from pid 2285',
};

Map<String, Object?> _record(int reason, {int sub = -1}) => {
  ..._ownerRecord,
  'reason': reason,
  'subReason': sub,
};

AppExitKind _kind(int reason, {int sub = -1}) =>
    AppExitRecord.fromMap(_record(reason, sub: sub))!.kind;

class _Store extends ProfileStore {
  _Store({required super.prefs, required this.all});

  final List<ServerProfile> all;

  @override
  List<ServerProfile> get profiles => all;

  @override
  Future<void> upsert(ServerProfile value) async {}
}

class _FakeLinux extends BuiltinLinux {
  final events = <String>[];
  bool serverRunning = false;
  bool failStart = false;
  final services = <String>{};
  Completer<void>? statusGate;
  Completer<void>? statusEntered;

  @override
  Future<BuiltinLinuxStatus> status() async {
    final gate = statusGate;
    if (gate != null) {
      statusGate = null;
      statusEntered?.complete();
      await gate.future;
    }
    return BuiltinLinuxStatus(
      installed: true,
      phase: BuiltinLinuxPhase.ready,
      serverRunning: serverRunning,
      services: [if (serverRunning) 'server', ...services],
    );
  }

  @override
  Future<BuiltinLinuxRunResult> run(
    String script, {
    Duration timeout = const Duration(minutes: 2),
  }) async => const BuiltinLinuxRunResult(exitCode: 0, output: '');

  @override
  Future<void> startServer(String script, {int port = 4097}) async {
    events.add('start server');
    if (failStart) throw const BuiltinLinuxException('Synthetic start failure');
    serverRunning = true;
  }

  @override
  Future<void> startService(
    String name,
    String script, {
    int? port,
    String? notice,
  }) async {
    events.add('start $name');
    services.add(name);
  }
}

class _FakeBridge extends AppLifecycleBridge {
  _FakeBridge(this.report);

  final AppLaunchReport report;
  int reads = 0;

  @override
  Future<AppLaunchReport> launchReport() async {
    reads++;
    return report;
  }
}

AppLaunchReport _report(Map<String, Object?>? exit, List<String> services) =>
    AppLaunchReport.fromMap({'exit': exit, 'previousServices': services});

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('exit classification', () {
    test("the owner's FORCE STOP by the system is a force stop", () {
      final record = AppExitRecord.fromMap(_ownerRecord)!;
      expect(record.reason, AndroidExitReason.userRequested);
      expect(record.subReason, AndroidExitReason.subForceStop);
      expect(record.importance, 125);
      expect(record.timestamp, DateTime(2026, 9, 26, 0, 6, 33));
      expect(record.kind, AppExitKind.forceStop);
      expect(record.kind.notable, isTrue);
    });

    test('every Android reason lands on one kind', () {
      expect(_kind(10), AppExitKind.forceStop);
      expect(_kind(11), AppExitKind.forceStop);
      expect(_kind(13, sub: 21), AppExitKind.forceStop);
      expect(_kind(13, sub: 22), AppExitKind.forceStop);
      expect(_kind(3), AppExitKind.lowMemory);
      expect(_kind(13, sub: 6), AppExitKind.lowMemory);
      expect(_kind(2, sub: 5), AppExitKind.lowMemory);
      expect(_kind(4), AppExitKind.crash);
      expect(_kind(5), AppExitKind.crash);
      expect(_kind(6), AppExitKind.crash);
      expect(_kind(7), AppExitKind.crash);
      expect(_kind(9), AppExitKind.killed);
      expect(_kind(13), AppExitKind.killed);
      expect(_kind(14), AppExitKind.killed);
    });

    test('a normal exit and an update say nothing', () {
      expect(_kind(1), AppExitKind.normal);
      expect(_kind(0), AppExitKind.normal);
      expect(_kind(16), AppExitKind.update);
      expect(_kind(15), AppExitKind.update);
      // Android ends the old process "because the user asked" on update.
      expect(_kind(10, sub: 25), AppExitKind.update);
      for (final kind in [AppExitKind.normal, AppExitKind.update]) {
        expect(kind.notable, isFalse);
      }
    });

    test('a record without a time is no record', () {
      expect(AppExitRecord.fromMap({'reason': 10}), isNull);
      expect(AppExitRecord.fromMap('nonsense'), isNull);
    });
  });

  group('oc/lifecycle bridge', () {
    const channel = MethodChannel(AppLifecycleBridge.channelName);
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    tearDown(() => messenger.setMockMethodCallHandler(channel, null));

    test('reads the exit and what was running', () async {
      messenger.setMockMethodCallHandler(channel, (call) async {
        expect(call.method, 'launchReport');
        return {
          'exit': _ownerRecord,
          'previousServices': ['server', 'aiteam'],
        };
      });
      final report = await AppLifecycleBridge().launchReport();
      expect(report.exit!.kind, AppExitKind.forceStop);
      expect(report.previousServices, ['server', 'aiteam']);
    });

    test('a missing or failing channel answers safely', () async {
      expect((await AppLifecycleBridge().launchReport()).exit, isNull);
      expect(
        await AppLifecycleBridge().openKeepAliveSetting(
          KeepAliveSetting.autostart,
        ),
        isFalse,
      );
      messenger.setMockMethodCallHandler(channel, (call) async {
        throw PlatformException(code: 'lifecycle', message: 'boom');
      });
      expect((await AppLifecycleBridge().launchReport()).exit, isNull);
      expect(
        (await AppLifecycleBridge().keepAliveInfo()).manufacturer,
        isEmpty,
      );
      expect(
        await AppLifecycleBridge().openKeepAliveSetting(
          KeepAliveSetting.appDetails,
        ),
        isFalse,
      );
    });
  });

  group('recovery on start', () {
    late SharedPreferences prefs;
    late _FakeLinux linux;
    late BuiltinServerStarter starter;
    late ServerProfile phone;
    late ServerProfile laptop;
    final restartActs = <String>[];

    setUp(() async {
      AutomationPolicyController.resetShared();
      restartActs.clear();
      SharedPreferences.setMockInitialValues({});
      prefs = await SharedPreferences.getInstance();
      linux = _FakeLinux();
      starter = BuiltinServerStarter(
        linux: linux,
        pollInterval: const Duration(milliseconds: 1),
      );
      phone = ServerProfile(
        id: 'phone',
        name: 'This phone',
        baseUrl: BuiltinLinux.serverUrl,
        username: BuiltinLinux.serverUsername,
        orchestration: BuiltinTeam.config(),
      )..password = 'secret';
      laptop = ServerProfile(
        id: 'laptop',
        name: 'Laptop',
        baseUrl: 'http://192.168.1.20:4096',
      );
      serverProbe = ({required baseUrl, username, password}) async =>
          linux.serverRunning
          ? const ServerProbeResult.success('1.18.29')
          : const ServerProbeResult.failure('refused');
    });

    tearDown(() {
      serverProbe = probeServerConnection;
      starter.dispose();
    });

    Future<AppExitRecovery> run(
      AppLaunchReport report, {
      ServerProfile? active,
      AppDiagnosticsController? diagnostics,
    }) async {
      final recovery = AppExitRecovery(bridge: _FakeBridge(report));
      await recovery.runOnce(
        store: _Store(prefs: prefs, all: [laptop, phone]),
        active: active ?? laptop,
        starter: starter,
        diagnostics: diagnostics,
        onRestart: ({required profileId, required eventId, required at}) async {
          restartActs.add('$profileId:$eventId');
          return true;
        },
      );
      // The team starts after the server answers, without being awaited.
      await Future<void>.delayed(const Duration(milliseconds: 20));
      return recovery;
    }

    test(
      'delegates to the shared healing owner without duplicate starts or acts',
      () async {
        final delegated = <String>[];
        final recovery = AppExitRecovery(
          bridge: _FakeBridge(_report(_ownerRecord, ['server'])),
        );
        await recovery.runOnce(
          store: _Store(prefs: prefs, all: [laptop, phone]),
          active: phone,
          starter: starter,
          recover: (profile) async {
            delegated.add(profile.id);
          },
          onRestart:
              ({required profileId, required eventId, required at}) async {
                restartActs.add(eventId);
                return true;
              },
        );
        expect(delegated, [phone.id]);
        expect(linux.events, isEmpty);
        expect(restartActs, isEmpty);
        recovery.dispose();
      },
    );

    test('after a force stop with the team running: one notice, and the '
        "phone's OpenCode and the team start again", () async {
      final diagnostics = AppDiagnosticsController();
      final recovery = await run(
        _report(_ownerRecord, ['server', 'aiteam']),
        diagnostics: diagnostics,
      );
      final notice = recovery.notice!;
      expect(notice.kind, AppExitKind.forceStop);
      expect(notice.at, DateTime(2026, 9, 26, 0, 6, 33));
      expect(notice.teamStopped, isTrue);
      expect(notice.offersKeepAlive, isTrue);
      expect(linux.events, [
        'start server',
        'start ${BuiltinTeam.serviceName}',
      ]);
      expect(diagnostics.entries.single.source, 'android.exit');
      expect(diagnostics.entries.single.message, contains('forceStop'));
      recovery.dismiss();
      expect(recovery.notice, isNull);
    });

    test(
      'disabled restart policy keeps recovery idle and files no act',
      () async {
        await AutomationPolicyController.forProfile(
          prefs,
          phone.id,
        ).setBehavior(AutomationBehavior.restartPhoneServer, false);
        final recovery = await run(_report(_ownerRecord, ['server']));
        expect(recovery.notice!.recoveryAllowed, isFalse);
        expect(linux.events, isEmpty);
        expect(restartActs, isEmpty);
      },
    );

    test('revoking policy during status probe prevents restart', () async {
      final gate = Completer<void>();
      linux.statusGate = gate;
      linux.statusEntered = Completer<void>();
      final running = run(_report(_ownerRecord, ['server']));
      await linux.statusEntered!.future;
      await AutomationPolicyController.forProfile(
        prefs,
        phone.id,
      ).setBehavior(AutomationBehavior.restartPhoneServer, false);
      gate.complete();
      await running;
      expect(linux.events, isEmpty);
      expect(restartActs, isEmpty);
    });

    test('failed recovery files no restart act', () async {
      linux.failStart = true;
      await run(_report(_ownerRecord, ['server']));
      expect(linux.events, ['start server']);
      expect(restartActs, isEmpty);
    });

    test('an already running server is not a confirmed restart', () async {
      linux.serverRunning = true;
      final recovery = await run(_report(_ownerRecord, ['server']));
      expect(recovery.notice!.recoveryAllowed, isTrue);
      expect(linux.events, isEmpty);
      expect(restartActs, isEmpty);
    });

    test('confirmed recovery files the exit episode only once', () async {
      final recovery = await run(_report(_ownerRecord, ['server']));
      expect(restartActs, [
        'phone:app-exit:${DateTime(2026, 9, 26, 0, 6, 33).microsecondsSinceEpoch}',
      ]);
      await recovery.runOnce(
        store: _Store(prefs: prefs, all: [laptop, phone]),
        active: laptop,
        starter: starter,
        onRestart: ({required profileId, required eventId, required at}) async {
          restartActs.add(eventId);
          return true;
        },
      );
      expect(restartActs, hasLength(1));
    });

    test('nothing was running: no notice and nothing started', () async {
      final recovery = await run(_report(_ownerRecord, const []));
      expect(recovery.notice, isNull);
      expect(linux.events, isEmpty);
    });

    test('an update or a normal exit says nothing but still brings the '
        'server back', () async {
      for (final exit in [_record(16), _record(1), null]) {
        linux
          ..events.clear()
          ..serverRunning = false;
        starter.allowAutoStart();
        linux.services.clear();
        final recovery = await run(_report(exit, ['server']));
        expect(recovery.notice, isNull, reason: '$exit');
        // The phone profile has the team on, so it follows the server.
        expect(linux.events.first, 'start server', reason: '$exit');
      }
    });

    test('opened on the phone server, the shell starts it: the recovery '
        'only tells', () async {
      final recovery = await run(
        _report(_record(3), ['server']),
        active: phone,
      );
      expect(recovery.notice!.kind, AppExitKind.lowMemory);
      expect(recovery.notice!.teamStopped, isFalse);
      expect(linux.events, isEmpty);
      expect(starter.autoStartAvailable, isTrue);
    });

    test('runs once per process', () async {
      final bridge = _FakeBridge(_report(_ownerRecord, ['server']));
      final recovery = AppExitRecovery(bridge: bridge);
      final store = _Store(prefs: prefs, all: [laptop, phone]);
      await recovery.runOnce(store: store, active: laptop, starter: starter);
      recovery.dismiss();
      await recovery.runOnce(store: store, active: laptop, starter: starter);
      expect(bridge.reads, 1);
      expect(recovery.notice, isNull);
    });
  });

  group('keep-alive advice per maker', () {
    test('Build.MANUFACTURER and BRAND map to a maker', () {
      expect(PhoneMaker.of('nubia', 'RedMagic'), PhoneMaker.nubia);
      expect(PhoneMaker.of('ZTE'), PhoneMaker.nubia);
      expect(PhoneMaker.of('Xiaomi', 'Redmi'), PhoneMaker.xiaomi);
      expect(PhoneMaker.of('Xiaomi', 'POCO'), PhoneMaker.xiaomi);
      expect(PhoneMaker.of('OnePlus'), PhoneMaker.oppo);
      expect(PhoneMaker.of('realme'), PhoneMaker.oppo);
      expect(PhoneMaker.of('OPPO'), PhoneMaker.oppo);
      expect(PhoneMaker.of('vivo'), PhoneMaker.vivo);
      expect(PhoneMaker.of('HUAWEI'), PhoneMaker.huawei);
      expect(PhoneMaker.of('HONOR'), PhoneMaker.huawei);
      expect(PhoneMaker.of('samsung'), PhoneMaker.samsung);
      expect(PhoneMaker.of('Google', 'google'), PhoneMaker.other);
      expect(PhoneMaker.of(''), PhoneMaker.other);
    });

    test('battery first everywhere; a lock where swiping force-stops', () {
      for (final maker in PhoneMaker.values) {
        final steps = keepAliveSteps(maker);
        expect(steps.first.kind, KeepAliveStepKind.battery);
        expect(steps.first.setting, KeepAliveSetting.battery);
        expect(
          steps.any((step) => step.kind == KeepAliveStepKind.lockInRecents),
          maker.closesOnSwipe,
          reason: maker.name,
        );
      }
      // On Nubia the lock matters most after the battery switch.
      expect(
        keepAliveSteps(PhoneMaker.nubia)[1].kind,
        KeepAliveStepKind.lockInRecents,
      );
      expect(
        keepAliveSteps(
          PhoneMaker.other,
        ).where((step) => step.kind == KeepAliveStepKind.autostart),
        isEmpty,
      );
    });

    test('each maker gets its own words', () {
      final l10n = lookupAppLocalizations(const Locale('en'));
      String detail(PhoneMaker maker, KeepAliveStepKind kind) =>
          keepAliveStepText(
            l10n,
            keepAliveSteps(maker).firstWhere((step) => step.kind == kind),
            maker,
          ).detail;
      expect(
        detail(PhoneMaker.nubia, KeepAliveStepKind.lockInRecents),
        l10n.keepRunningLockNubia,
      );
      expect(
        detail(PhoneMaker.samsung, KeepAliveStepKind.lockInRecents),
        l10n.keepRunningLockSamsung,
      );
      expect(
        detail(PhoneMaker.xiaomi, KeepAliveStepKind.background),
        l10n.keepRunningBackgroundXiaomi,
      );
      expect(
        detail(PhoneMaker.huawei, KeepAliveStepKind.autostart),
        l10n.keepRunningAutostartHuawei,
      );
      expect(
        detail(PhoneMaker.vivo, KeepAliveStepKind.background),
        l10n.keepRunningBackgroundVivo,
      );
    });
  });

  group('the notice', () {
    Future<AppExitRecovery> mountNotice(
      WidgetTester tester,
      AppLaunchReport report,
    ) async {
      SharedPreferences.setMockInitialValues({});
      final prefs = await SharedPreferences.getInstance();
      final recovery = AppExitRecovery(bridge: _FakeBridge(report));
      final linux = _FakeLinux();
      final starter = BuiltinServerStarter(linux: linux);
      addTearDown(starter.dispose);
      await recovery.runOnce(
        store: _Store(prefs: prefs, all: const []),
        active: null,
        starter: starter,
      );
      final connection = ConnectionController(
        _Store(prefs: prefs, all: const []),
      );
      addTearDown(connection.dispose);
      await tester.pumpWidget(const SizedBox.shrink());
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            appExitRecoveryProvider.overrideWithValue(recovery),
            connProvider.overrideWithValue(connection),
          ],
          child: MaterialApp(
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            builder: (context, child) => MediaQuery(
              data: MediaQuery.of(
                context,
              ).copyWith(alwaysUse24HourFormat: true),
              child: child!,
            ),
            home: Scaffold(
              body: Column(
                children: [
                  AppExitNoticeLine(now: DateTime(2026, 9, 26, 9)),
                  const Expanded(child: Placeholder()),
                ],
              ),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      return recovery;
    }

    testWidgets('says the app was closed and when, once, with Keep it '
        'running', (tester) async {
      final recovery = await mountNotice(
        tester,
        _report(_ownerRecord, ['server', 'aiteam']),
      );
      expect(
        find.text(
          // No saved in-app server here, so nothing restarts it: the
          // notice must not promise that it is starting again.
          'OpenCode Mobile was closed at 00:06. Your phone\'s OpenCode '
          'and the AI Team stopped with it. Start them again when you\'re '
          'ready.',
        ),
        findsOneWidget,
      );
      await tester.tap(find.byKey(const ValueKey('kit-notice-dismiss')));
      await tester.pumpAndSettle();
      expect(recovery.notice, isNull);
      expect(find.byKey(const ValueKey('app-exit-notice')), findsNothing);
    });

    test('never says "starting again" when automatic restart is off', () {
      final l10n = lookupAppLocalizations(const Locale('en'));
      for (final team in [false, true]) {
        final off = appExitMessage(
          l10n,
          AppExitNotice(
            kind: AppExitKind.forceStop,
            at: DateTime(2026, 9, 27, 21, 21),
            teamStopped: team,
            recoveryAllowed: false,
          ),
          'at 21:21',
        );
        expect(off, isNot(contains('starting again')));
        expect(off, contains('Start '));
        final on = appExitMessage(
          l10n,
          AppExitNotice(
            kind: AppExitKind.forceStop,
            at: DateTime(2026, 9, 27, 21, 21),
            teamStopped: team,
          ),
          'at 21:21',
        );
        expect(on, contains('starting again'));
      }
    });

    test('F10: a force stop is not blamed on Android, and once the '
        'server is back it says so instead of "starting again"', () {
      final l10n = lookupAppLocalizations(const Locale('en'));
      for (final team in [false, true]) {
        final notice = AppExitNotice(
          kind: AppExitKind.forceStop,
          at: DateTime(2026, 9, 27, 21, 21),
          teamStopped: team,
        );
        final starting = appExitMessage(l10n, notice, 'at 9:21 PM');
        expect(starting, startsWith('OpenCode Mobile was closed at 9:21 PM'));
        expect(starting, isNot(contains('Android')));
        final back = appExitMessage(
          l10n,
          notice,
          'at 9:21 PM',
          serverBack: true,
        );
        expect(back, contains('is running again'));
        expect(back, isNot(contains('starting again')));
      }
    });

    testWidgets('F10: once the server is back, a notice with nothing left '
        'to offer resolves; one with Keep it running stays, reworded', (
      tester,
    ) async {
      Future<KitStatus?> status(AppExitRecovery recovery) async {
        KitStatus? result;
        await tester.pumpWidget(
          MaterialApp(
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            home: Builder(
              builder: (context) {
                result = appExitKitStatus(context, recovery, serverBack: true);
                return const SizedBox.shrink();
              },
            ),
          ),
        );
        return result;
      }

      Future<AppExitRecovery> after(Map<String, Object?> record) async {
        SharedPreferences.setMockInitialValues({});
        final prefs = await SharedPreferences.getInstance();
        final recovery = AppExitRecovery(
          bridge: _FakeBridge(_report(record, ['server'])),
        );
        final starter = BuiltinServerStarter(linux: _FakeLinux());
        addTearDown(starter.dispose);
        await recovery.runOnce(
          store: _Store(prefs: prefs, all: const []),
          active: null,
          starter: starter,
        );
        return recovery;
      }

      final crash = await after(_record(4));
      expect(crash.notice, isNotNull);
      expect(await status(crash), isNull);

      final stopped = await after(_ownerRecord);
      final shown = await status(stopped);
      expect(shown, isNotNull);
      expect(shown!.message, contains('is running again'));
      expect(shown.action?.label, 'Keep it running');
    });

    testWidgets('once the server is back, the notice folds away by itself '
        'instead of staying on every screen', (tester) async {
      SharedPreferences.setMockInitialValues({});
      final prefs = await SharedPreferences.getInstance();
      final recovery = AppExitRecovery(
        bridge: _FakeBridge(_report(_ownerRecord, ['server'])),
      );
      addTearDown(recovery.dispose);
      final starter = BuiltinServerStarter(linux: _FakeLinux());
      addTearDown(starter.dispose);
      await recovery.runOnce(
        store: _Store(prefs: prefs, all: const []),
        active: null,
        starter: starter,
      );
      expect(recovery.notice, isNotNull);
      recovery.noteServerBack();
      await tester.pump(const Duration(seconds: 10));
      // Repeated calls (every rebuild) do not restart the clock.
      recovery.noteServerBack();
      expect(recovery.notice, isNotNull);
      await tester.pump(const Duration(seconds: 6));
      expect(recovery.notice, isNull);
    });

    testWidgets('Keep it running opens the guidance', (tester) async {
      const channel = MethodChannel(AppLifecycleBridge.channelName);
      final messenger = tester.binding.defaultBinaryMessenger;
      messenger.setMockMethodCallHandler(
        channel,
        (call) async => {'manufacturer': 'nubia', 'brand': 'RedMagic'},
      );
      addTearDown(() => messenger.setMockMethodCallHandler(channel, null));
      await mountNotice(tester, _report(_ownerRecord, ['server']));
      expect(
        find.textContaining("Your phone's OpenCode stopped with it"),
        findsOneWidget,
      );
      await tester.tap(find.text('Keep it running'));
      await tester.pumpAndSettle();
      // Keep running is a section of Notifications and background.
      expect(find.byType(NotificationsSettingsScreen), findsOneWidget);
    });

    testWidgets('an exit on an earlier day names the day', (tester) async {
      await mountNotice(
        tester,
        _report(
          {
            ..._ownerRecord,
            'timestamp': DateTime(
              2026,
              9,
              25,
              20,
              19,
              5,
            ).millisecondsSinceEpoch,
          },
          ['server'],
        ),
      );
      expect(
        find.textContaining('OpenCode Mobile was closed on Sep 25 at 20:19'),
        findsOneWidget,
      );
    });

    testWidgets('a crash does not send the person to phone settings', (
      tester,
    ) async {
      await mountNotice(tester, _report(_record(4), ['server']));
      expect(
        find.textContaining('OpenCode Mobile stopped unexpectedly at 00:06'),
        findsOneWidget,
      );
      expect(find.text('Keep it running'), findsNothing);
    });

    testWidgets('nothing after a normal exit or an update', (tester) async {
      for (final exit in [_record(1), _record(16), _record(10, sub: 25)]) {
        await mountNotice(tester, _report(exit, ['server', 'aiteam']));
        expect(find.byKey(const ValueKey('app-exit-notice')), findsNothing);
      }
    });
  });

  group('the guidance screen', () {
    const channel = MethodChannel(AppLifecycleBridge.channelName);
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    tearDown(() => messenger.setMockMethodCallHandler(channel, null));

    Future<List<String>> mountScreen(
      WidgetTester tester, {
      required String manufacturer,
      bool battery = false,
      Object? Function(String setting)? open,
    }) async {
      final opened = <String>[];
      messenger.setMockMethodCallHandler(channel, (call) async {
        switch (call.method) {
          case 'keepAliveInfo':
            return {
              'manufacturer': manufacturer,
              'brand': manufacturer,
              'batteryOptimizationIgnored': battery,
            };
          case 'openKeepAliveSetting':
            final setting = (call.arguments as Map)['setting'] as String;
            opened.add(setting);
            return open?.call(setting) ?? true;
        }
        return null;
      });
      tester.view
        ..physicalSize = const Size(412, 915)
        ..devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      await tester.pumpWidget(
        const ProviderScope(
          child: MaterialApp(
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            home: Scaffold(
              body: SingleChildScrollView(child: KeepRunningSection()),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      return opened;
    }

    testWidgets('on a RedMagic: the swipe warning, the lock, auto-start', (
      tester,
    ) async {
      final opened = await mountScreen(tester, manufacturer: 'nubia');
      final l10n = lookupAppLocalizations(const Locale('en'));
      expect(
        find.text(l10n.keepRunningIntro(KitBidi.auto('nubia'))),
        findsOneWidget,
      );
      expect(find.text(l10n.keepRunningSwipeWarning), findsOneWidget);
      expect(find.text(l10n.keepRunningLockNubia), findsOneWidget);
      await tester.tap(find.byKey(const ValueKey('keep-running-autostart')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('keep-running-battery')));
      await tester.pumpAndSettle();
      // The lock is done by hand in Recents: no screen to open.
      await tester.tap(
        find.byKey(const ValueKey('keep-running-lockInRecents')),
      );
      await tester.pumpAndSettle();
      expect(opened, ['autostart', 'battery']);
    });

    testWidgets('on a Pixel: no swipe warning, no lock, battery allowed', (
      tester,
    ) async {
      await mountScreen(tester, manufacturer: 'Google', battery: true);
      final l10n = lookupAppLocalizations(const Locale('en'));
      expect(find.text(l10n.keepRunningSwipeWarning), findsNothing);
      expect(
        find.byKey(const ValueKey('keep-running-lockInRecents')),
        findsNothing,
      );
      expect(find.text(l10n.keepRunningBatteryDone), findsOneWidget);
    });

    testWidgets('a screen this phone lacks says so and never crashes', (
      tester,
    ) async {
      final l10n = lookupAppLocalizations(const Locale('en'));
      await mountScreen(
        tester,
        manufacturer: 'Xiaomi',
        open: (setting) => setting == 'autostart'
            ? false
            : throw PlatformException(code: 'lifecycle'),
      );
      await tester.tap(find.byKey(const ValueKey('keep-running-autostart')));
      await tester.pumpAndSettle();
      expect(find.text(l10n.keepRunningOpenFailed), findsOneWidget);
      await tester.tap(find.byKey(const ValueKey('keep-running-background')));
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      expect(find.text(l10n.keepRunningOpenFailed), findsOneWidget);
    });

    testWidgets('without the channel (not Android) it still shows advice', (
      tester,
    ) async {
      // A handler that answers nothing is a missing plugin to the caller;
      // an unmocked channel would never answer under the fake clock.
      messenger.setMockMethodCallHandler(channel, (call) async => null);
      tester.view
        ..physicalSize = const Size(412, 915)
        ..devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      await tester.pumpWidget(
        const ProviderScope(
          child: MaterialApp(
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            home: Scaffold(
              body: SingleChildScrollView(child: KeepRunningSection()),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      final l10n = lookupAppLocalizations(const Locale('en'));
      expect(
        find.text(
          l10n.keepRunningIntro(KitBidi.auto(l10n.keepRunningThisPhone)),
        ),
        findsOneWidget,
      );
      expect(find.text(l10n.keepRunningBatteryTitle), findsOneWidget);
    });
  });
}
