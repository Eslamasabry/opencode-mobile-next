// The in-app Linux manager's pages (lib/ui/screens/linux_manager) over a fake
// of the Android side: what runs and stopping it, ports and the preview,
// storage and cleanup, packages and the background job, repair and reset.
// Every page also fits 320 dp at 2.5x text, left to right and right to left.

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/builtin/builtin_server.dart';
import 'package:opencode_mobile/builtin/linux_manager/linux_manager.dart';
import 'package:opencode_mobile/builtin/linux_manager/linux_packages.dart';
import 'package:opencode_mobile/builtin/linux_manager/linux_reset_flow.dart';
import 'package:opencode_mobile/builtin/setup/components.dart';
import 'package:opencode_mobile/builtin/setup/phone_setup.dart';
import 'package:opencode_mobile/builtin/setup/setup_contract.dart';
import 'package:opencode_mobile/builtin/linux_manager/linux_storage.dart';
import 'package:opencode_mobile/domain/dev_preview.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/linux_manager/linux_manager_screen.dart';
import 'package:opencode_mobile/ui/screens/linux_manager/linux_packages_page.dart';
import 'package:opencode_mobile/ui/screens/linux_manager/linux_ports_page.dart';
import 'package:opencode_mobile/ui/screens/linux_manager/linux_processes_page.dart';
import 'package:opencode_mobile/ui/screens/linux_manager/linux_storage_page.dart';
import 'package:opencode_mobile/ui/widgets/phone_server_card.dart';
import 'package:shared_preferences/shared_preferences.dart';

String _stat(int pid, String comm, int ppid, int start, {int rss = 5000}) =>
    '$pid ($comm) S $ppid 1 1 0 -1 4194560 100 0 0 0 0 0 0 0 '
    '20 0 1 0 $start 1000000 $rss 18446744073709551615';

String _proc(int pid, String comm, int ppid, int start, String cmd) =>
    '::p $pid\t${_stat(pid, comm, ppid, start)}\t$cmd';

final _scan = [
  '::self 900',
  '::uptime 5000.00',
  _proc(20, 'node', 10, 2000, '/opt/opencode2/bin/opencode2 serve --port 4097'),
  _proc(30, 'node', 10, 2100, 'node /home/oc/oc-paseo/bin/paseo supervisor'),
  _proc(31, 'node', 10, 2110, 'node /home/oc/oc-paseo/bin/paseo daemon start'),
  _proc(32, 'node', 31, 2120, 'node /home/oc/oc-paseo/lib/worker.js'),
  _proc(40, 'claude', 10, 2200, '/usr/local/bin/claude --resume abc'),
  _proc(50, 'sh', 10, 3000, 'sh -c vite --port 5173'),
  _proc(
    51,
    'node',
    50,
    3010,
    'node /root/projects/app/node_modules/.bin/vite --port 5173',
  ),
  _proc(60, 'bash', 10, 4000, '-bash'),
].join('\n');

const _packageList =
    '::pkg bash\t5.2\t1400\tyes\trequired\tii \n'
    '::pkg curl\t8.5\t500\t\toptional\tii \n'
    '::pkg htop\t3.3\t300\t\toptional\tii \n'
    '::pkg tmux\t3.4\t900\t\toptional\tii \n';

class _Linux extends BuiltinLinux {
  final runs = <String>[];
  final stops = <String>[];
  final services = <String, String>{};
  final removed = <String>[];
  bool jobRunning = false;
  String jobLog = '';
  BuiltinLinuxException? runError;
  String? setupJson;
  final kb = <LinuxCleanupId, int>{
    LinuxCleanupId.aptCache: 40960,
    LinuxCleanupId.toolCaches: 20480,
    LinuxCleanupId.tempFiles: 0,
    LinuxCleanupId.oldAgentVersions: 0,
  };
  String removePlan = '::remv htop';
  Set<String> known = {'htop', 'tmux', 'git', 'ncdu'};

  bool installed = true;

  /// What Reset's erase does: null erases; otherwise it throws this, after
  /// erasing when [eraseBeforeFailing].
  BuiltinLinuxException? uninstallError;
  bool eraseBeforeFailing = false;

  @override
  Future<BuiltinLinuxStatus> status() async => BuiltinLinuxStatus(
    installed: installed,
    phase: installed ? BuiltinLinuxPhase.ready : BuiltinLinuxPhase.idle,
    services: [if (jobRunning) linuxJobService],
  );

  @override
  Future<String?> setupStatus() async => setupJson;

  @override
  Future<BuiltinLinuxRunResult> run(
    String script, {
    Duration timeout = const Duration(minutes: 2),
    String? input,
  }) async {
    runs.add(script);
    if (runError case final error?) throw error;
    BuiltinLinuxRunResult ok(String out) =>
        BuiltinLinuxRunResult(exitCode: 0, output: out);
    if (script.startsWith('# oc-linux-scan')) return ok(_scan);
    if (script.startsWith('# oc-linux-stop')) {
      stops.add(script);
      return ok('::stop stopped 51:3010\n::stop stopped 50:3000\n');
    }
    if (script.startsWith('# oc-linux-ports')) {
      return ok('::open 3000\n::open 4097\n::open 5173\n');
    }
    if (script == linuxStorageScript) {
      return ok(
        '::du 800000 /usr\n::du 20000 /etc\n::du 30000 /var/cache/apt\n'
        '::du 25000 /root/.npm\n::du 150000 /opt/opencode2\n'
        '::du 300000 /root/projects\n::du 4000 /tmp\n',
      );
    }
    for (final action in LinuxCleanupAction.all) {
      if (script == action.measureScript) {
        final n = kb[action.id] ?? 0;
        return ok(n == 0 ? '' : '::du $n ${action.paths.first}\n');
      }
      if (script == action.script) {
        removed.add(action.id.name);
        kb[action.id] = 0;
        return ok('');
      }
    }
    if (script == linuxPackageListScript) return ok(_packageList);
    if (script.startsWith('# oc-linux-package-check')) {
      return ok(
        [
          for (final n in ['htop', 'tmux', 'git', 'ncdu', 'nope'])
            if (script.contains(' $n') || script.contains('for p in $n'))
              '::cand $n\t${known.contains(n) ? '1.0' : ''}\t'
                  '${n == 'tmux' ? 'ii ' : ''}\n',
        ].join(),
      );
    }
    if (script.startsWith('# oc-linux-remove-plan')) return ok('$removePlan\n');
    return ok('');
  }

  @override
  Future<void> startService(
    String name,
    String script, {
    int? port,
    String? notice,
  }) async {
    services[name] = script;
    jobRunning = true;
    final kind = RegExp(r"::oc job (\w+)").firstMatch(script)!.group(1);
    final names =
        RegExp(r"::oc names %s\\n' '([^']*)'").firstMatch(script)?.group(1) ??
        '';
    jobLog = '::oc job $kind\n::oc names $names\n';
  }

  @override
  Future<void> stopService(String name) async {
    jobRunning = false;
  }

  @override
  Future<String> serviceLog(String name, {int tailBytes = 16384}) async =>
      jobLog;

  @override
  Future<void> uninstall() async {
    final error = uninstallError;
    if (error == null || eraseBeforeFailing) installed = false;
    if (error != null) throw error;
    removed.add('uninstall');
  }
}

/// Phone setup, faked: records the jobs it is asked to run, and forgets a
/// finished job when it reads no job (as the real engine does now).
class _Engine implements SetupEngine {
  final runs = <Set<String>>[];
  var restores = 0;
  final _progress = ValueNotifier<SetupProgress>(
    const SetupProgress(
      state: SetupState.done,
      components: [],
      overall: 1,
      jobId: 'old-repair',
    ),
  );

  @override
  List<SetupComponent> get registry =>
      setupComponents(lookupAppLocalizations(const Locale('en')));

  @override
  ValueListenable<SetupProgress> get progress => _progress;

  @override
  Future<void> run(
    Set<String> ids, {
    Map<String, Map<String, String>> params = const {},
  }) async {
    runs.add(ids);
    _progress.value = SetupProgress(
      state: SetupState.running,
      components: const [],
      overall: 0,
      jobId: 'reset-${runs.length}',
    );
  }

  @override
  Future<void> restore({SetupReadScope? scope}) async {
    restores++;
    _progress.value = SetupProgress.idle;
  }

  @override
  Future<void> cancel() async {}

  @override
  Future<Set<String>> installedOptional() async => {};
}

class _Store extends ProfileStore {
  _Store({required super.prefs});
  final saved = <ServerProfile>[];

  @override
  List<ServerProfile> get profiles => List.unmodifiable(saved);

  @override
  String? get activeId => null;
}

class _Connection extends ConnectionController {
  _Connection(_Store super.store);
}

void main() {
  late _Linux linux;
  late _Connection connection;
  late int setupOpened;
  late _Engine engine;

  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    linux = _Linux();
    connection = _Connection(
      _Store(prefs: await SharedPreferences.getInstance()),
    );
    setupOpened = 0;
    engine = _Engine();
    PhoneSetup.engine = engine;
    PhoneServerCardRoutes.openStartOverride = (_) async => setupOpened++;
    PhoneServerCardRoutes.openProgressOverride = (_) async => setupOpened++;
  });

  tearDown(() {
    PhoneServerCardRoutes.openStartOverride = null;
    PhoneServerCardRoutes.openProgressOverride = null;
    connection.dispose();
  });

  Future<void> mount(
    WidgetTester tester,
    Widget page, {
    double textScale = 1,
    bool rtl = false,
    bool phone = false,
  }) async {
    tester.view.physicalSize = phone
        ? const Size(320, 640)
        : const Size(800, 2400);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          builtinLinuxProvider.overrideWithValue(linux),
          connProvider.overrideWithValue(connection),
        ],
        child: MaterialApp(
          locale: Locale(rtl ? 'ar' : 'en'),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(
              context,
            ).copyWith(textScaler: TextScaler.linear(textScale)),
            child: child!,
          ),
          home: page,
        ),
      ),
    );
    await tester.pump();
    await tester.pump();
  }

  Future<void> unmount(WidgetTester tester) async {
    await tester.pumpWidget(const SizedBox());
  }

  Future<void> settle(WidgetTester tester) async {
    for (var i = 0; i < 6; i++) {
      await tester.pump(const Duration(milliseconds: 150));
    }
  }

  Future<void> back(WidgetTester tester) async {
    final navigator = tester.state<NavigatorState>(
      find.byType(Navigator).first,
    );
    navigator.pop();
    await settle(tester);
  }

  group('hub', () {
    testWidgets('lists the five areas, Repair and Reset last', (tester) async {
      await mount(tester, LinuxManagerScreen(onRepair: () async {}));
      for (final key in [
        'linux-row-running',
        'linux-row-ports',
        'linux-row-storage',
        'linux-row-packages',
        'linux-row-repair',
        'linux-row-reset',
      ]) {
        expect(find.byKey(ValueKey(key)), findsOneWidget, reason: key);
      }
      expect(find.text('Repair Linux'), findsOneWidget);
      expect(find.text('Reset Linux'), findsOneWidget);
      await unmount(tester);
    });

    testWidgets('nothing on the hub is red; Reset still asks first', (
      tester,
    ) async {
      // Owner rule: never red. Reset is a plain row, last, and its typed
      // question is what guards it (N1-5 found the label red).
      await mount(tester, LinuxManagerScreen(onRepair: () async {}));
      expect(
        find.byWidgetPredicate((w) => w is KitRow && w.destructive),
        findsNothing,
      );
      final rows = tester
          .widgetList<KitRow>(find.byType(KitRow))
          .map((r) => r.key)
          .toList();
      expect(rows.last, const ValueKey('linux-row-reset'));
      await tester.tap(find.byKey(const ValueKey('linux-row-reset')));
      await settle(tester);
      expect(find.byKey(const ValueKey('linux-reset-sheet')), findsOneWidget);
      await unmount(tester);
    });

    testWidgets('Repair asks, then runs setup repair', (tester) async {
      var repaired = 0;
      await mount(tester, LinuxManagerScreen(onRepair: () async => repaired++));
      await tester.tap(find.byKey(const ValueKey('linux-row-repair')));
      await settle(tester);
      expect(find.text('Repair Linux?'), findsOneWidget);
      expect(find.textContaining('Your projects and sign-ins'), findsOneWidget);
      await tester.tap(find.byKey(const ValueKey('linux-repair-confirm')));
      await settle(tester);
      expect(repaired, 1);
      await unmount(tester);
    });

    testWidgets('Repair is refused while setup runs', (tester) async {
      var repaired = 0;
      linux.setupJson = '{"state":"running"}';
      await mount(tester, LinuxManagerScreen(onRepair: () async => repaired++));
      await tester.tap(find.byKey(const ValueKey('linux-row-repair')));
      await settle(tester);
      expect(
        find.byKey(const ValueKey('linux-repair-blocked')),
        findsOneWidget,
      );
      expect(
        find.text('Setup is running. Wait until it finishes.'),
        findsOneWidget,
      );
      expect(repaired, 0);
      await unmount(tester);
    });

    testWidgets('Reset needs the typed word and keeps projects', (
      tester,
    ) async {
      await mount(tester, LinuxManagerScreen(onRepair: () async {}));
      await tester.tap(find.byKey(const ValueKey('linux-row-reset')));
      await settle(tester);
      expect(find.byKey(const ValueKey('linux-reset-sheet')), findsOneWidget);
      expect(find.text('Your projects stay on the phone'), findsOneWidget);
      expect(find.textContaining('Agent sign-ins'), findsOneWidget);
      // Disabled until "Reset" is typed.
      await tester.tap(find.byKey(const ValueKey('linux-reset-confirm')));
      await settle(tester);
      expect(linux.removed, isEmpty);
      await tester.enterText(find.byType(EditableText).last, 'Reset');
      await settle(tester);
      await tester.tap(find.byKey(const ValueKey('linux-reset-confirm')));
      await settle(tester);
      await tester.pump(const Duration(seconds: 1));
      expect(linux.removed, ['uninstall']);
      // Setup starts at once on the fresh Linux, with the first-setup
      // selection, and its progress is shown (N1-5: nothing started).
      expect(engine.restores, 1);
      expect(engine.runs, hasLength(1));
      expect(engine.runs.single, containsAll(['linux', 'opencode']));
      expect(setupOpened, 1);
      expect(find.byKey(const ValueKey('linux-reset-partial')), findsNothing);
      expect(LinuxResetFlow.pending(connection.store.prefs), isFalse);
      await unmount(tester);
    });

    Future<void> confirmReset(WidgetTester tester) async {
      await tester.tap(find.byKey(const ValueKey('linux-row-reset')));
      await settle(tester);
      await tester.enterText(find.byType(EditableText).last, 'Reset');
      await settle(tester);
      await tester.tap(find.byKey(const ValueKey('linux-reset-confirm')));
      await settle(tester);
      await tester.pump(const Duration(seconds: 1));
    }

    testWidgets('a reset refused before erasing says nothing was erased', (
      tester,
    ) async {
      linux.uninstallError = const BuiltinLinuxException(
        'The runtime could not be removed.',
        code: 'removal_failed',
      );
      await mount(tester, LinuxManagerScreen(onRepair: () async {}));
      await confirmReset(tester);
      expect(linux.installed, isTrue);
      expect(engine.runs, isEmpty, reason: 'nothing to set up again');
      expect(setupOpened, 0);
      expect(
        find.byKey(const ValueKey('linux-reset-not-erased')),
        findsOneWidget,
      );
      expect(find.text('Linux was not reset'), findsOneWidget);
      expect(find.textContaining('Nothing was erased'), findsOneWidget);
      expect(find.textContaining("didn't work"), findsNothing);
      expect(LinuxResetFlow.pending(connection.store.prefs), isFalse);
      await unmount(tester);
    });

    testWidgets('an erase that stopped part way still sets Linux up again', (
      tester,
    ) async {
      linux
        ..uninstallError = const BuiltinLinuxException(
          'The runtime could not be removed.',
          code: 'removal_failed',
        )
        ..eraseBeforeFailing = true;
      await mount(tester, LinuxManagerScreen(onRepair: () async {}));
      await confirmReset(tester);
      expect(linux.installed, isFalse);
      expect(engine.runs, hasLength(1));
      expect(setupOpened, 1);
      expect(
        find.byKey(const ValueKey('linux-reset-not-erased')),
        findsNothing,
      );
      await unmount(tester);
    });

    testWidgets('Reset is refused while a package job runs', (tester) async {
      linux.jobRunning = true;
      await mount(tester, LinuxManagerScreen(onRepair: () async {}));
      await tester.tap(find.byKey(const ValueKey('linux-row-reset')));
      await settle(tester);
      expect(find.byKey(const ValueKey('linux-reset-blocked')), findsOneWidget);
      expect(linux.removed, isEmpty);
      await unmount(tester);
    });
  });

  group('running', () {
    testWidgets('lists what runs by kind, the server protected', (
      tester,
    ) async {
      await mount(tester, const LinuxProcessesPage());
      expect(find.byKey(const Key('linux-procs-list')), findsOneWidget);
      expect(find.text('OpenCode server'), findsWidgets);
      expect(find.byKey(const Key('linux-proc-protected-20')), findsOneWidget);
      // The dev server and its shell are one row, led by the shell.
      expect(find.byKey(const Key('linux-proc-50')), findsOneWidget);
      expect(find.byKey(const Key('linux-proc-51')), findsNothing);
      expect(find.textContaining('Dev server'), findsWidgets);
      expect(find.textContaining('Agent'), findsWidgets);
      expect(find.byKey(const Key('linux-procs-summary')), findsOneWidget);
      await unmount(tester);
    });

    testWidgets('Paseo is one row that says how many processes it is', (
      tester,
    ) async {
      await mount(tester, const LinuxProcessesPage());
      expect(find.text('Paseo'), findsOneWidget);
      expect(find.byKey(const Key('linux-proc-30')), findsOneWidget);
      expect(find.byKey(const Key('linux-proc-31')), findsNothing);
      expect(find.byKey(const Key('linux-proc-32')), findsNothing);
      expect(
        find.descendant(
          of: find.byKey(const Key('linux-proc-30')),
          matching: find.textContaining('3 processes'),
        ),
        findsOneWidget,
      );
      await unmount(tester);
    });

    testWidgets('Stop names the target, asks, and sends exact identities', (
      tester,
    ) async {
      await mount(tester, const LinuxProcessesPage());
      await tester.tap(find.byKey(const Key('linux-proc-50')));
      await settle(tester);
      expect(find.byKey(const Key('linux-procs-details')), findsOneWidget);
      await tester.tap(find.byKey(const Key('linux-procs-details-stop')));
      await settle(tester);
      expect(find.text('Stop npm?'), findsNothing);
      expect(find.byKey(const Key('linux-procs-confirm')), findsOneWidget);
      expect(
        find.textContaining('1 program it started stops with it'),
        findsOneWidget,
      );
      expect(linux.stops, isEmpty, reason: 'nothing stops before the yes');
      await tester.tap(find.byKey(const Key('linux-procs-confirm-stop')));
      await settle(tester);
      expect(linux.stops, hasLength(1));
      expect(linux.stops.single, contains('51:3010'));
      expect(linux.stops.single, contains('50:3000'));
      expect(linux.stops.single, isNot(contains('20:2000')));
      expect(find.byKey(const Key('linux-procs-result')), findsOneWidget);
      expect(find.text('Stopped 2 programs'), findsOneWidget);
      await unmount(tester);
    });

    testWidgets('the server has no Stop here', (tester) async {
      await mount(tester, const LinuxProcessesPage());
      await tester.tap(find.byKey(const Key('linux-proc-20')));
      await settle(tester);
      expect(find.byKey(const Key('linux-procs-details-stop')), findsNothing);
      expect(find.textContaining('Stop it from This phone'), findsOneWidget);
      await unmount(tester);
    });

    testWidgets('a busy installer slot is said in words', (tester) async {
      linux.runError = const BuiltinLinuxException(
        'Another phone task is running. Wait a moment and try again.',
        details: 'SECRET-DETAIL',
      );
      await mount(tester, const LinuxProcessesPage());
      // A busy slot is tried twice more before the page says so.
      await tester.pump(const Duration(seconds: 2));
      expect(
        find.byKey(const ValueKey('linux-procs-load-error')),
        findsOneWidget,
      );
      expect(
        find.textContaining('Another task is using Linux right now'),
        findsOneWidget,
      );
      expect(find.textContaining('Another phone task'), findsNothing);
      await unmount(tester);
    });

    for (final rtl in [false, true]) {
      testWidgets('fits 320 dp at 2.5x text ${rtl ? 'RTL' : 'LTR'}', (
        tester,
      ) async {
        await mount(
          tester,
          const LinuxProcessesPage(),
          textScale: 2.5,
          rtl: rtl,
          phone: true,
        );
        await tester.scrollUntilVisible(
          find.byKey(const Key('linux-proc-60')),
          200,
        );
        await tester.pumpAndSettle();
        expect(tester.takeException(), isNull);
        await unmount(tester);
      });
    }
  });

  group('ports', () {
    testWidgets('lists ports with what uses them and opens the preview', (
      tester,
    ) async {
      DevPreviewLink? opened;
      String? against;
      await mount(
        tester,
        LinuxPortsPage(
          opener: (link, base) async {
            opened = link;
            against = base;
          },
        ),
      );
      expect(find.byKey(const Key('linux-port-5173')), findsOneWidget);
      expect(find.text('Port 5173'), findsOneWidget);
      expect(find.textContaining('Used by vite'), findsOneWidget);
      expect(find.textContaining('Used by OpenCode server'), findsOneWidget);
      expect(find.textContaining('Program not known'), findsOneWidget);
      expect(find.textContaining('Not a web page'), findsOneWidget);
      await tester.tap(find.byKey(const Key('linux-port-5173')));
      await settle(tester);
      expect(opened?.port, 5173);
      expect(against, BuiltinLinux.serverUrl);
      // The server's own port cannot be previewed.
      opened = null;
      await tester.tap(find.byKey(const Key('linux-port-4097')));
      await settle(tester);
      expect(opened, isNull);
      await unmount(tester);
    });

    for (final rtl in [false, true]) {
      testWidgets('fits 320 dp at 2.5x text ${rtl ? 'RTL' : 'LTR'}', (
        tester,
      ) async {
        await mount(
          tester,
          const LinuxPortsPage(),
          textScale: 2.5,
          rtl: rtl,
          phone: true,
        );
        await tester.scrollUntilVisible(
          find.byKey(const Key('linux-port-5173')),
          200,
        );
        await tester.pumpAndSettle();
        expect(tester.takeException(), isNull);
        await unmount(tester);
      });
    }
  });

  group('storage', () {
    testWidgets('shows the breakdown and the cleanups that have something', (
      tester,
    ) async {
      await mount(tester, const LinuxStoragePage());
      await settle(tester);
      for (final c in LinuxStorageCategory.values) {
        expect(find.byKey(Key('linux-storage-${c.name}')), findsOneWidget);
      }
      expect(find.byKey(const Key('linux-clean-aptCache')), findsOneWidget);
      expect(find.byKey(const Key('linux-clean-toolCaches')), findsOneWidget);
      // Nothing to give back: not offered.
      expect(find.byKey(const Key('linux-clean-tempFiles')), findsNothing);
      // Projects are measured and never offered for clearing.
      expect(find.text('Your work. Never cleared from here.'), findsOneWidget);
      await unmount(tester);
    });

    testWidgets('a cleanup asks, runs, and reports what it freed', (
      tester,
    ) async {
      await mount(tester, const LinuxStoragePage());
      await settle(tester);
      await tester.tap(find.byKey(const Key('linux-clean-aptCache')));
      await settle(tester);
      expect(find.text('Clear package downloads?'), findsOneWidget);
      expect(find.text('Projects, sign-ins and settings stay'), findsOneWidget);
      expect(linux.removed, isEmpty);
      await tester.tap(find.byKey(const Key('linux-clean-confirm')));
      await settle(tester);
      await tester.pump(const Duration(seconds: 1));
      expect(linux.removed, ['aptCache']);
      expect(find.byKey(const Key('linux-storage-result')), findsOneWidget);
      expect(find.text('Freed 40.0 MB'), findsOneWidget);
      await unmount(tester);
    });

    testWidgets('a cleanup is refused while setup runs', (tester) async {
      await mount(tester, const LinuxStoragePage());
      await settle(tester);
      linux.setupJson = '{"state":"running"}';
      await tester.tap(find.byKey(const Key('linux-clean-aptCache')));
      await settle(tester);
      await tester.tap(find.byKey(const Key('linux-clean-confirm')));
      await settle(tester);
      expect(linux.removed, isEmpty);
      await unmount(tester);
    });

    for (final rtl in [false, true]) {
      testWidgets('fits 320 dp at 2.5x text ${rtl ? 'RTL' : 'LTR'}', (
        tester,
      ) async {
        await mount(
          tester,
          const LinuxStoragePage(),
          textScale: 2.5,
          rtl: rtl,
          phone: true,
        );
        await settle(tester);
        await tester.scrollUntilVisible(
          find.byKey(const Key('linux-clean-aptCache')),
          200,
        );
        await tester.pumpAndSettle();
        expect(tester.takeException(), isNull);
        await unmount(tester);
      });
    }
  });

  group('packages', () {
    testWidgets('lists, searches, and marks what Linux needs', (tester) async {
      await mount(tester, const LinuxPackagesPage());
      expect(find.byKey(const Key('linux-pkg-htop')), findsOneWidget);
      expect(find.text('4 packages installed'), findsOneWidget);
      await tester.enterText(find.byType(EditableText).first, 'tm');
      await tester.pump(const Duration(milliseconds: 600));
      await settle(tester);
      expect(find.byKey(const Key('linux-pkg-tmux')), findsOneWidget);
      expect(find.byKey(const Key('linux-pkg-htop')), findsNothing);
      await tester.enterText(find.byType(EditableText).first, 'zzz');
      await tester.pump(const Duration(milliseconds: 600));
      await settle(tester);
      expect(find.byKey(const Key('linux-pkg-no-match')), findsOneWidget);
      await unmount(tester);
    });

    testWidgets('a package Linux needs cannot be removed', (tester) async {
      await mount(tester, const LinuxPackagesPage());
      await tester.tap(find.byKey(const Key('linux-pkg-bash')));
      await settle(tester);
      expect(find.byKey(const Key('linux-pkg-details-locked')), findsOneWidget);
      expect(find.byKey(const Key('linux-pkg-details-remove')), findsNothing);
      await back(tester);
      await settle(tester);
      await unmount(tester);
    });

    testWidgets('install checks the name, asks, then runs as a job', (
      tester,
    ) async {
      await mount(tester, const LinuxPackagesPage());
      await tester.tap(find.byKey(const ValueKey('linux-pkg-install')));
      await settle(tester);
      await tester.enterText(
        find.byKey(const Key('linux-install-field')),
        'ncdu',
      );
      await settle(tester);
      await tester.tap(find.byKey(const Key('linux-install-check')));
      await settle(tester);
      expect(find.text('Install ncdu?'), findsOneWidget);
      expect(linux.services, isEmpty, reason: 'asks before it runs');
      await tester.tap(find.byKey(const Key('linux-install-confirm')));
      await settle(tester);
      expect(linux.services.keys, [linuxJobService]);
      expect(linux.services[linuxJobService], contains('oc_apt_install ncdu'));
      expect(find.byKey(const Key('linux-job-running')), findsOneWidget);
      expect(find.text('Installing ncdu'), findsOneWidget);
      await unmount(tester);
    });

    testWidgets('install says so for an unknown or installed name', (
      tester,
    ) async {
      await mount(tester, const LinuxPackagesPage());
      await tester.tap(find.byKey(const ValueKey('linux-pkg-install')));
      await settle(tester);
      await tester.enterText(
        find.byKey(const Key('linux-install-field')),
        'nope',
      );
      await settle(tester);
      await tester.tap(find.byKey(const Key('linux-install-check')));
      await settle(tester);
      expect(find.textContaining('No package named nope'), findsOneWidget);
      await tester.enterText(
        find.byKey(const Key('linux-install-field')),
        'tmux',
      );
      await settle(tester);
      await tester.tap(find.byKey(const Key('linux-install-check')));
      await settle(tester);
      expect(find.text('tmux is already installed'), findsOneWidget);
      expect(linux.services, isEmpty);
      await unmount(tester);
    });

    testWidgets('install refuses text that is not a name', (tester) async {
      await mount(tester, const LinuxPackagesPage());
      await tester.tap(find.byKey(const ValueKey('linux-pkg-install')));
      await settle(tester);
      await tester.enterText(
        find.byKey(const Key('linux-install-field')),
        'htop; rm -rf /',
      );
      await settle(tester);
      await tester.tap(find.byKey(const Key('linux-install-check')));
      await settle(tester);
      expect(find.textContaining('Use only lower case letters'), findsWidgets);
      expect(linux.services, isEmpty);
      expect(linux.runs.where((s) => s.contains('rm -rf')), isEmpty);
      await unmount(tester);
    });

    testWidgets('remove shows what else goes, asks, and runs the job', (
      tester,
    ) async {
      linux.removePlan = '::remv htop\n::remv libnl-3-200';
      await mount(tester, const LinuxPackagesPage());
      await tester.tap(find.byKey(const Key('linux-pkg-htop')));
      await settle(tester);
      await tester.tap(find.byKey(const Key('linux-pkg-details-remove')));
      await settle(tester);
      expect(find.text('Remove htop?'), findsOneWidget);
      expect(find.text('Also removed: libnl-3-200'), findsOneWidget);
      expect(linux.services, isEmpty);
      await tester.tap(find.byKey(const Key('linux-remove-confirm')));
      await settle(tester);
      expect(
        linux.services[linuxJobService],
        contains('remove -y htop libnl-3-200'),
      );
      await unmount(tester);
    });

    testWidgets('remove is refused when it would take something Linux needs', (
      tester,
    ) async {
      linux.removePlan = '::remv htop\n::remv curl';
      await mount(tester, const LinuxPackagesPage());
      await tester.tap(find.byKey(const Key('linux-pkg-htop')));
      await settle(tester);
      await tester.tap(find.byKey(const Key('linux-pkg-details-remove')));
      await settle(tester);
      expect(
        find.byKey(const ValueKey('linux-remove-blocked')),
        findsOneWidget,
      );
      expect(linux.services, isEmpty);
      await unmount(tester);
    });

    testWidgets('Update system asks and runs the job', (tester) async {
      await mount(tester, const LinuxPackagesPage());
      await tester.tap(find.byKey(const ValueKey('linux-pkg-update')));
      await settle(tester);
      expect(find.text('Update Linux packages?'), findsOneWidget);
      expect(find.textContaining('45 minutes'), findsOneWidget);
      await tester.tap(find.byKey(const Key('linux-update-confirm')));
      await settle(tester);
      expect(linux.services[linuxJobService], contains('upgrade'));
      expect(find.text('Updating Linux packages'), findsWidgets);
      await unmount(tester);
    });

    testWidgets('a task holding the slot is said in words, nothing starts', (
      tester,
    ) async {
      await mount(tester, const LinuxPackagesPage());
      linux.runError = const BuiltinLinuxException(
        'Another phone task is running. Wait a moment and try again.',
      );
      await tester.tap(find.byKey(const ValueKey('linux-pkg-update')));
      await settle(tester);
      await tester.tap(find.byKey(const Key('linux-update-confirm')));
      await settle(tester);
      await tester.pump(const Duration(seconds: 2));
      await settle(tester);
      expect(
        find.byKey(const ValueKey('linux-packages-problem')),
        findsOneWidget,
      );
      expect(
        find.textContaining('Another task is using Linux'),
        findsOneWidget,
      );
      expect(linux.services, isEmpty);
      await unmount(tester);
    });

    testWidgets('the job reports progress and survives leaving the page', (
      tester,
    ) async {
      await mount(tester, const LinuxManagerScreen(onRepair: _noRepair));
      await tester.tap(find.byKey(const ValueKey('linux-row-packages')));
      await settle(tester);
      await tester.tap(find.byKey(const ValueKey('linux-pkg-update')));
      await settle(tester);
      await tester.tap(find.byKey(const Key('linux-update-confirm')));
      await settle(tester);
      linux.jobLog =
          '::oc job update\n::oc names \n::oc stage Unpacking x\n::oc percent 42\n';
      await tester.pump(const Duration(seconds: 2));
      await settle(tester);
      expect(find.text('42%'), findsOneWidget);
      // Leave the page: the hub shows the same job, still going.
      await back(tester);
      await settle(tester);
      expect(find.byKey(const Key('linux-job-running')), findsOneWidget);
      expect(find.text('42%'), findsOneWidget);
      expect(linux.jobRunning, isTrue);
      await unmount(tester);
    });

    testWidgets('a finished job says how it ended and can be dismissed', (
      tester,
    ) async {
      linux.jobRunning = true;
      linux.jobLog = '::oc job install\n::oc names ncdu\n::oc percent 80\n';
      await mount(tester, const LinuxPackagesPage());
      expect(find.byKey(const Key('linux-job-running')), findsOneWidget);
      linux.jobRunning = false;
      linux.jobLog += '::oc job-exit 0\n';
      await tester.pump(const Duration(seconds: 2));
      await settle(tester);
      expect(find.byKey(const Key('linux-job-outcome')), findsOneWidget);
      expect(find.text('Installed ncdu'), findsOneWidget);
      await tester.tap(find.byKey(const Key('linux-job-dismiss')));
      await settle(tester);
      expect(find.byKey(const Key('linux-job-outcome')), findsNothing);
      await unmount(tester);
    });

    testWidgets('a failed job says what happened without the phone text', (
      tester,
    ) async {
      linux.jobRunning = true;
      linux.jobLog = '::oc job update\n';
      await mount(tester, const LinuxPackagesPage());
      linux.jobRunning = false;
      linux.jobLog +=
          'E: Could not get lock /var/lib/dpkg/lock\n::oc job-exit 100\n';
      await tester.pump(const Duration(seconds: 2));
      await settle(tester);
      expect(find.byKey(const Key('linux-job-outcome')), findsOneWidget);
      expect(find.textContaining('That did not finish'), findsOneWidget);
      expect(find.textContaining('Could not get lock'), findsNothing);
      await unmount(tester);
    });

    testWidgets('Stop is named, asks, and stops the job', (tester) async {
      linux.jobRunning = true;
      linux.jobLog = '::oc job install\n::oc names ncdu\n';
      await mount(tester, const LinuxPackagesPage());
      expect(find.text('Stop installing ncdu'), findsWidgets);
      await tester.tap(find.byKey(const Key('linux-job-stop')));
      await settle(tester);
      expect(find.byKey(const Key('linux-job-stop-sheet')), findsOneWidget);
      expect(linux.jobRunning, isTrue);
      await tester.tap(find.byKey(const Key('linux-job-stop-confirm')));
      await settle(tester);
      expect(linux.jobRunning, isFalse);
      await unmount(tester);
    });

    testWidgets('actions are off while a job runs, with the reason', (
      tester,
    ) async {
      linux.jobRunning = true;
      linux.jobLog = '::oc job update\n';
      await mount(tester, const LinuxPackagesPage());
      await tester.tap(find.byKey(const ValueKey('linux-pkg-update')));
      await settle(tester);
      expect(find.byKey(const Key('linux-update-sheet')), findsNothing);
      await unmount(tester);
    });

    for (final rtl in [false, true]) {
      testWidgets('fits 320 dp at 2.5x text ${rtl ? 'RTL' : 'LTR'}', (
        tester,
      ) async {
        linux.jobRunning = true;
        linux.jobLog = '::oc job install\n::oc names ncdu\n::oc percent 30\n';
        await mount(
          tester,
          const LinuxPackagesPage(),
          textScale: 2.5,
          rtl: rtl,
          phone: true,
        );
        await tester.scrollUntilVisible(
          find.byKey(const Key('linux-pkg-tmux')),
          300,
          scrollable: find
              .descendant(
                of: find.byKey(const ValueKey('linux-packages-list')),
                matching: find.byType(Scrollable),
              )
              .first,
        );
        await tester.pumpAndSettle(const Duration(milliseconds: 100));
        expect(tester.takeException(), isNull);
        await unmount(tester);
      });
    }
  });
}

Future<void> _noRepair() async {}
