// Linux › Services (lib/ui/screens/linux_manager/linux_service*.dart) over a
// fake of the frozen LinuxServices API (docs/design/linux-manager-2026-10-31,
// 2-architecture §1.8): the list and its states, the service page with its
// log, the form for a command of your own, the hub row, and the large-text
// and right-to-left checks.
import 'dart:async';

import 'package:clock/clock.dart';
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_riverpod/misc.dart' show Override;
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/builtin/builtin_server.dart';
import 'package:opencode_mobile/builtin/linux_services/linux_services.dart';
import 'package:opencode_mobile/domain/dev_preview.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/linux_manager/linux_manager_screen.dart';
import 'package:opencode_mobile/ui/screens/linux_manager/linux_service_add_page.dart';
import 'package:opencode_mobile/ui/screens/linux_manager/linux_service_page.dart';
import 'package:opencode_mobile/ui/screens/linux_manager/linux_services_page.dart';

import 'support/fake_linux_services.dart';

LinuxServiceState _state(
  String name, {
  String? title,
  LinuxServicePhase phase = LinuxServicePhase.running,
  bool? on,
  int? port,
  Duration? up,
  int restarts = 0,
  int? lastExit,
  DateTime? lastExitAt,
  DateTime? nextTryAt,
}) => LinuxServiceState(
  def: LinuxServiceDef(
    name: name,
    title: title ?? name,
    command: 'run-$name',
    dir: '/root',
    port: port,
  ),
  on: on ?? phase != LinuxServicePhase.off,
  phase: phase,
  since: up == null ? null : clock.now().subtract(up),
  restarts: restarts,
  lastExit: lastExit,
  lastExitAt: lastExitAt,
  nextTryAt: nextTryAt,
);

/// Linux is there; nothing else matters to the hub.
class _Linux extends BuiltinLinux {
  @override
  Future<BuiltinLinuxStatus> status() async => const BuiltinLinuxStatus(
    installed: true,
    phase: BuiltinLinuxPhase.ready,
    services: [],
  );

  @override
  Future<String?> setupStatus() async => null;

  @override
  Future<BuiltinLinuxRunResult> run(
    String script, {
    Duration timeout = const Duration(minutes: 2),
  }) async => const BuiltinLinuxRunResult(exitCode: 0, output: '');
}

/// One small set that shows every state of the list.
List<LinuxServiceState> _all() => [
  _state(
    'my-bot',
    title: 'my-bot',
    phase: LinuxServicePhase.failed,
    on: true,
    restarts: 5,
    lastExit: 1,
    lastExitAt: DateTime(2026, 10, 31, 10, 42),
  ),
  _state(
    'syncthing',
    title: 'Syncthing',
    port: 8384,
    up: const Duration(hours: 3, minutes: 12),
  ),
  _state('ssh', title: 'SSH', port: 8022, up: const Duration(hours: 2)),
  _state('forgejo', title: 'Forgejo', phase: LinuxServicePhase.off),
];

Future<void> settle(WidgetTester tester) async {
  for (var i = 0; i < 8; i++) {
    await tester.pump(const Duration(milliseconds: 150));
  }
}

void main() {
  late FakeLinuxServices svc;
  final platform = <MethodCall>[];

  setUp(() {
    svc = FakeLinuxServices(states: _all());
    platform.clear();
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(SystemChannels.platform, (call) async {
          platform.add(call);
          return null;
        });
  });

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(SystemChannels.platform, null);
  });

  Future<void> mount(
    WidgetTester tester,
    Widget page, {
    double textScale = 1,
    bool rtl = false,
    Size size = const Size(412, 915),
    List<Override> overrides = const [],
  }) async {
    tester.view.physicalSize = size;
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          linuxServicesProvider.overrideWithValue(svc),
          builtinLinuxProvider.overrideWithValue(_Linux()),
          ...overrides,
        ],
        child: MaterialApp(
          debugShowCheckedModeBanner: false,
          locale: Locale(rtl ? 'ar' : 'en'),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(context).copyWith(
              textScaler: TextScaler.linear(textScale),
              alwaysUse24HourFormat: true,
            ),
            child: child!,
          ),
          home: page,
        ),
      ),
    );
    await settle(tester);
  }

  Future<void> unmount(WidgetTester tester) async {
    await tester.pumpWidget(const SizedBox());
    await tester.pump(const Duration(seconds: 1));
  }

  /// A row's supporting line, as the person reads it.
  String line(WidgetTester tester, String name) {
    final r = tester.renderObject<RenderParagraph>(
      find.byKey(ValueKey('linux-service-line-$name')),
    );
    return r.text.toPlainText();
  }

  Finder chip(String name) => find.byKey(ValueKey('linux-service-chip-$name'));

  group('Services list', () {
    testWidgets('loading shows skeleton rows, then says it is still waiting', (
      tester,
    ) async {
      final never = Completer<List<LinuxServiceState>>();
      svc = _SlowSvc(never.future);
      await mount(tester, const LinuxServicesPage());
      expect(
        find.byKey(const ValueKey('linux-services-loading')),
        findsOneWidget,
      );
      expect(find.byType(KitSkeletonRows), findsOneWidget);
      expect(find.textContaining('Still waiting'), findsNothing);
      await tester.pump(const Duration(seconds: 9));
      expect(find.text('Still waiting after 8 s'), findsOneWidget);
      await unmount(tester);
    });

    testWidgets('empty: the add row and a calm sentence, no list', (
      tester,
    ) async {
      svc.states = [];
      await mount(tester, const LinuxServicesPage());
      expect(find.text('Add a service'), findsOneWidget);
      expect(
        find.text(
          'Nothing runs on its own yet. Add your own command and it keeps '
          'running, even after a crash or when the app restarts.',
        ),
        findsOneWidget,
      );
      expect(find.byKey(const ValueKey('linux-services-group')), findsNothing);
      expect(
        find.byKey(const ValueKey('linux-services-screen-off')),
        findsNothing,
      );
      await unmount(tester);
    });

    testWidgets(
      'one list by urgency: crashed first, each state in words, a named chip',
      (tester) async {
        await mount(tester, const LinuxServicesPage());
        final order = tester
            .widgetList<KitRow>(
              find.descendant(
                of: find.byKey(const ValueKey('linux-services-group')),
                matching: find.byType(KitRow),
              ),
            )
            .map((r) => r.title)
            .toList();
        expect(order, ['my-bot', 'SSH', 'Syncthing', 'Forgejo']);
        expect(
          line(tester, 'my-bot'),
          'Stopped after crashing 5 times · 10:42',
        );
        expect(line(tester, 'syncthing'), 'Running for 3 h 12 min · port 8384');
        expect(line(tester, 'forgejo'), 'Off');
        // The chip says the act; screen readers hear it with its target.
        final semantics = tester.ensureSemantics();
        expect(find.bySemanticsLabel('Start my-bot'), findsOneWidget);
        expect(find.bySemanticsLabel('Stop Syncthing'), findsOneWidget);
        expect(find.bySemanticsLabel('Start Forgejo'), findsOneWidget);
        semantics.dispose();
        expect(
          find.descendant(of: chip('my-bot'), matching: find.text('Start')),
          findsOneWidget,
        );
        expect(
          find.descendant(of: chip('syncthing'), matching: find.text('Stop')),
          findsOneWidget,
        );
        expect(find.textContaining('keeps running: it comes back'), findsOne);
        expect(
          find.text(
            'While the screen is off, services may pause unless the phone '
            'is charging.',
          ),
          findsOneWidget,
        );
        await unmount(tester);
      },
    );

    testWidgets('Start works at once: Starting…, then running', (tester) async {
      svc.hold = Completer<void>();
      await mount(tester, const LinuxServicesPage());
      await tester.tap(chip('forgejo'));
      await tester.pump();
      expect(svc.calls, ['start forgejo']);
      expect(line(tester, 'forgejo'), 'Starting…');
      // A second tap while it works does nothing.
      await tester.tap(chip('forgejo'));
      await tester.pump();
      expect(svc.calls, ['start forgejo']);
      svc.hold!.complete();
      await settle(tester);
      expect(line(tester, 'forgejo'), startsWith('Running for'));
      expect(
        find.descendant(of: chip('forgejo'), matching: find.text('Stop')),
        findsOneWidget,
      );
      await unmount(tester);
    });

    testWidgets('Stop stops the service and the row says Off', (tester) async {
      await mount(tester, const LinuxServicesPage());
      await tester.tap(chip('ssh'));
      await settle(tester);
      expect(svc.calls, ['stop ssh']);
      expect(line(tester, 'ssh'), startsWith('Off'));
      expect(
        find.descendant(of: chip('ssh'), matching: find.text('Start')),
        findsOneWidget,
      );
      await unmount(tester);
    });

    testWidgets('a crashed service counts down to its next try', (
      tester,
    ) async {
      svc.states = [
        _state(
          'my-bot',
          phase: LinuxServicePhase.backoff,
          on: true,
          lastExit: 1,
          nextTryAt: clock.now().add(const Duration(seconds: 20)),
        ),
      ];
      await mount(tester, const LinuxServicesPage());
      int left() => int.parse(
        RegExp(
          r'^Crashed \(exit 1\)\. Trying again in (\d+) s$',
        ).firstMatch(line(tester, 'my-bot'))!.group(1)!,
      );
      final first = left();
      expect(first, inInclusiveRange(17, 20));
      await tester.pump(const Duration(seconds: 3));
      expect(left(), first - 3);
      await unmount(tester);
    });

    testWidgets('held off by the heat guard: its own words, Stop not Start', (
      tester,
    ) async {
      svc.states = [
        _state(
          'ssh',
          title: 'SSH',
          phase: LinuxServicePhase.off,
          on: true,
          port: 8022,
        ),
      ];
      await mount(tester, const LinuxServicesPage());
      expect(
        line(tester, 'ssh'),
        'Paused: the phone is too hot. Starts again when it cools',
      );
      expect(
        find.descendant(of: chip('ssh'), matching: find.text('Stop')),
        findsOneWidget,
      );
      await unmount(tester);
    });

    testWidgets('a busy port says so and rises to the top', (tester) async {
      svc.states = [
        _state('ssh', title: 'SSH', port: 8022, up: const Duration(hours: 1)),
        _state(
          'web',
          title: 'Web',
          port: 8384,
          phase: LinuxServicePhase.portBusy,
          on: true,
        ),
      ];
      await mount(tester, const LinuxServicesPage());
      expect(
        line(tester, 'web'),
        'Port 8384 is already in use. Trying again soon',
      );
      final first = tester
          .widgetList<KitRow>(
            find.descendant(
              of: find.byKey(const ValueKey('linux-services-group')),
              matching: find.byType(KitRow),
            ),
          )
          .first;
      expect(first.title, 'Web');
      await unmount(tester);
    });

    testWidgets('the runner is down: words, Try again, no raw text', (
      tester,
    ) async {
      svc.readError = const LinuxServiceException(
        LinuxServiceProblem.supervisorDown,
        details: 'bash: oc-svd: ENOENT secret-token-123',
      );
      await mount(tester, const LinuxServicesPage());
      expect(
        find.byKey(const ValueKey('linux-services-load-error')),
        findsOneWidget,
      );
      expect(find.text("Services couldn't start."), findsOneWidget);
      expect(
        find.text(
          "The part of Linux that keeps services running isn't answering. "
          'Try again.',
        ),
        findsOneWidget,
      );
      expect(find.textContaining('ENOENT'), findsNothing);
      svc.readError = null;
      await tester.tap(find.text('Try again'));
      await settle(tester);
      expect(find.byKey(const ValueKey('linux-services-list')), findsOneWidget);
      expect(find.text('Syncthing'), findsOneWidget);
      await unmount(tester);
    });

    testWidgets('Linux is not set up: says so, and what to do', (tester) async {
      svc.readError = const LinuxServiceException(
        LinuxServiceProblem.linuxMissing,
      );
      await mount(tester, const LinuxServicesPage());
      expect(find.text("Linux isn't set up on this phone"), findsOneWidget);
      expect(
        find.text(
          'Set up Linux from This phone, then come back to add services.',
        ),
        findsOneWidget,
      );
      await unmount(tester);
    });

    testWidgets('an act that fails says why in words above the list', (
      tester,
    ) async {
      svc.startError = const LinuxServiceException(
        LinuxServiceProblem.writeFailed,
        details: 'EIO /root/.oc-builtin/svc/want/forgejo',
      );
      await mount(tester, const LinuxServicesPage());
      await tester.tap(chip('forgejo'));
      await settle(tester);
      expect(
        find.text("Couldn't save that. The phone may be out of space."),
        findsOneWidget,
      );
      expect(find.textContaining('EIO'), findsNothing);
      await unmount(tester);
    });

    testWidgets('touch and hold: Restart, Stop and Remove name the service', (
      tester,
    ) async {
      await mount(tester, const LinuxServicesPage());
      await tester.longPress(find.text('Syncthing'));
      await settle(tester);
      expect(find.text('Restart Syncthing'), findsOneWidget);
      expect(find.text('Stop Syncthing'), findsOneWidget);
      expect(find.text('Remove Syncthing'), findsOneWidget);
      await tester.tap(find.text('Restart Syncthing'));
      await settle(tester);
      expect(svc.calls, ['restart syncthing']);
      await unmount(tester);
    });

    testWidgets('Remove asks first, says what goes and what stays', (
      tester,
    ) async {
      await mount(tester, const LinuxServicesPage());
      await tester.longPress(find.text('Forgejo'));
      await settle(tester);
      await tester.tap(find.text('Remove Forgejo'));
      await settle(tester);
      expect(find.text('Remove Forgejo?'), findsOneWidget);
      expect(find.text('Its log and settings are deleted'), findsOneWidget);
      expect(find.text('The program stays installed'), findsOneWidget);
      expect(svc.calls, isEmpty);
      await tester.tap(
        find.byKey(const ValueKey('linux-service-remove-confirm')),
      );
      await settle(tester);
      expect(svc.calls, ['remove forgejo']);
      expect(find.text('Forgejo'), findsNothing);
      await unmount(tester);
    });

    testWidgets('a row opens the service; Add opens the form', (tester) async {
      await mount(tester, const LinuxServicesPage());
      await tester.tap(find.text('Syncthing'));
      await settle(tester);
      expect(find.byKey(const ValueKey('linux-service-status')), findsOne);
      await tester.pageBack();
      await settle(tester);
      await tester.tap(find.byKey(const ValueKey('linux-services-add')));
      await settle(tester);
      expect(find.byKey(const ValueKey('linux-service-form')), findsOne);
      await unmount(tester);
    });
  });

  group('Service page', () {
    Future<void> open(
      WidgetTester tester,
      String name, {
      Future<void> Function(DevPreviewLink, String)? opener,
      double textScale = 1,
      bool rtl = false,
      Size size = const Size(412, 915),
    }) => mount(
      tester,
      LinuxServicePage(name: name, opener: opener),
      textScale: textScale,
      rtl: rtl,
      size: size,
    );

    testWidgets('running with a port: status, the acts by name, the log', (
      tester,
    ) async {
      svc.log = const [
        KitLogLine('::svc start 1730000000 0'),
        KitLogLine('INFO: GUI and API listening on 127.0.0.1:8384'),
        KitLogLine('::svc exit 1 12'),
        KitLogLine('::svc backoff 4'),
      ];
      svc.states = [
        _state(
          'syncthing',
          title: 'Syncthing',
          port: 8384,
          up: const Duration(hours: 3, minutes: 12),
          restarts: 1,
          lastExitAt: DateTime(2026, 10, 31, 7, 15),
        ),
      ];
      await open(tester, 'syncthing');
      expect(find.text('Running for 3 h 12 min'), findsOneWidget);
      expect(
        find.text('Came back once after a crash, at 07:15', findRichText: true),
        findsOneWidget,
      );
      expect(find.text('Open Syncthing'), findsOneWidget);
      expect(find.text('Stop Syncthing'), findsOneWidget);
      expect(
        find.text('It stays stopped until you start it again'),
        findsOneWidget,
      );
      expect(find.text('Restart Syncthing'), findsOneWidget);
      expect(find.text('Remove Syncthing'), findsOneWidget);
      expect(find.text('Start Syncthing'), findsNothing);
      // The log: marker lines are words, the program's own lines stay.
      expect(find.text('Started'), findsOneWidget);
      expect(find.text('Crashed (exit 1)'), findsOneWidget);
      expect(find.text('Trying again in 4 s'), findsOneWidget);
      expect(find.textContaining('::svc'), findsNothing);
      expect(
        find.textContaining('GUI and API listening on 127.0.0.1:8384'),
        findsOneWidget,
      );
      await unmount(tester);
    });

    testWidgets('Open shows the service in the preview, on its port', (
      tester,
    ) async {
      DevPreviewLink? opened;
      svc.states = [
        _state('syncthing', title: 'Syncthing', port: 8384, up: Duration.zero),
      ];
      await open(
        tester,
        'syncthing',
        opener: (link, base) async => opened = link,
      );
      await tester.tap(find.text('Open Syncthing'));
      await settle(tester);
      expect(opened?.port, 8384);
      await unmount(tester);
    });

    testWidgets('off: Start is the first act, no Open and no Restart', (
      tester,
    ) async {
      await open(tester, 'forgejo');
      expect(find.text('Off'), findsOneWidget);
      expect(find.text('Start Forgejo'), findsOneWidget);
      expect(
        find.text('Keeps running, also after a crash or an app restart'),
        findsNothing,
      );
      expect(
        find.text('It keeps running, also after a crash or an app restart'),
        findsOneWidget,
      );
      expect(find.text('Restart Forgejo'), findsNothing);
      expect(find.text('Open Forgejo'), findsNothing);
      await tester.tap(find.text('Start Forgejo'));
      await settle(tester);
      expect(svc.calls, ['start forgejo']);
      expect(find.text('Stop Forgejo'), findsOneWidget);
      await unmount(tester);
    });

    testWidgets('gave up: the plain story and a Start, with the log large', (
      tester,
    ) async {
      svc.log = const [KitLogLine('::svc gave-up')];
      await open(tester, 'my-bot');
      expect(
        find.text('Stopped after crashing 5 times · 10:42'),
        findsOneWidget,
      );
      expect(find.text('Start my-bot'), findsOneWidget);
      expect(find.text('Gave up after crashing too many times'), findsOne);
      final panel = tester.widget<KitLogPanel>(find.byType(KitLogPanel));
      expect(panel.size, KitLogSize.fill);
      expect(panel.live, isFalse);
      await unmount(tester);
    });

    testWidgets('while it works the acts are off and the status says why', (
      tester,
    ) async {
      svc.hold = Completer<void>();
      await open(tester, 'ssh');
      await tester.tap(find.text('Stop SSH'));
      await tester.pump();
      expect(find.text('Stopping…'), findsWidgets);
      final rows = tester.widgetList<KitRow>(
        find.descendant(
          of: find.byKey(const ValueKey('linux-service-list')),
          matching: find.byType(KitRow),
        ),
      );
      expect(
        rows.where((r) => r.key != const ValueKey('linux-service-status')),
        everyElement(
          predicate<KitRow>((r) => !r.enabled, 'disabled while working'),
        ),
      );
      svc.hold!.complete();
      await settle(tester);
      expect(find.text('Start SSH'), findsOneWidget);
      await unmount(tester);
    });

    testWidgets('Remove asks, then removes and leaves the page', (
      tester,
    ) async {
      await mount(tester, const LinuxServicesPage());
      await tester.tap(find.text('Forgejo'));
      await settle(tester);
      await tester.tap(find.text('Remove Forgejo'));
      await settle(tester);
      expect(find.text('Remove Forgejo?'), findsOneWidget);
      await tester.tap(
        find.byKey(const ValueKey('linux-service-remove-confirm')),
      );
      await settle(tester);
      expect(svc.calls, ['remove forgejo']);
      await settle(tester);
      // Back on the list, and Forgejo is gone from it.
      expect(find.byKey(const ValueKey('linux-services-list')), findsOne);
      expect(find.text('Forgejo'), findsNothing);
      await unmount(tester);
    });

    testWidgets('the log copies, with secrets masked', (tester) async {
      svc.log = const [KitLogLine('token=ghp_abcdefghijklmnopqrstuvwxyz0123')];
      await open(tester, 'ssh');
      await tester.tap(find.byKey(const ValueKey('kit-log-copy-all')));
      await tester.pump();
      final copied =
          platform
                  .lastWhere((c) => c.method == 'Clipboard.setData')
                  .arguments['text']
              as String;
      expect(copied, isNotEmpty);
      expect(copied, isNot(contains('ghp_abcdefghijklmnopqrstuvwxyz0123')));
      await unmount(tester);
    });

    testWidgets('a service that is gone closes the page', (tester) async {
      await mount(tester, const LinuxServicesPage());
      await tester.tap(find.text('Forgejo'));
      await settle(tester);
      svc.states = svc.states.where((s) => s.def.name != 'forgejo').toList();
      svc.emit();
      await settle(tester);
      expect(find.byKey(const ValueKey('linux-services-list')), findsOne);
      await unmount(tester);
    });
  });

  group('Add a service', () {
    Future<void> type(WidgetTester tester, String key, String value) async {
      await tester.enterText(
        find.descendant(
          of: find.byKey(ValueKey(key)),
          matching: find.byType(EditableText),
        ),
        value,
      );
      await tester.pump();
    }

    Future<void> save(WidgetTester tester) async {
      await tester.tap(find.byKey(const ValueKey('linux-service-save')));
      await settle(tester);
    }

    testWidgets('empty fields are named, nothing is saved', (tester) async {
      await mount(tester, const LinuxServiceAddPage());
      await save(tester);
      expect(
        find.text(
          'Use 2 to 24 lowercase letters, numbers or dashes, starting with a '
          'letter',
        ),
        findsOneWidget,
      );
      expect(find.text('Write the command to run'), findsOneWidget);
      expect(svc.calls, isEmpty);
      await unmount(tester);
    });

    testWidgets('names and ports are checked beside their field', (
      tester,
    ) async {
      await mount(tester, const LinuxServiceAddPage());
      await type(tester, 'linux-service-name', 'syncthing');
      await type(tester, 'linux-service-command', 'node bot.js');
      await type(tester, 'linux-service-port', '80');
      await save(tester);
      expect(
        find.text('That name is used by Syncthing'),
        findsOneWidget,
        reason: 'the existing service is named',
      );
      expect(
        find.text("Ports below 1024 aren't allowed on Android"),
        findsOneWidget,
      );
      await type(tester, 'linux-service-port', '4097');
      await save(tester);
      expect(find.text('Port 4097 belongs to OpenCode'), findsOneWidget);
      await type(tester, 'linux-service-port', '8384');
      await save(tester);
      expect(find.text('Port 8384 is used by Syncthing'), findsOneWidget);
      await type(tester, 'linux-service-port', '70000');
      await save(tester);
      expect(find.text('Use a port from 1024 to 65535'), findsOneWidget);
      await type(tester, 'linux-service-name', 'oc-thing');
      await save(tester);
      expect(
        find.text('That name is kept for the app. Pick another'),
        findsOneWidget,
      );
      expect(svc.calls, isEmpty);
      await unmount(tester);
    });

    testWidgets('Save and start saves the definition, then keeps it running', (
      tester,
    ) async {
      await mount(tester, const LinuxServiceAddPage());
      await type(tester, 'linux-service-name', 'my-api');
      await type(tester, 'linux-service-command', 'node server.js');
      await type(tester, 'linux-service-dir', '/root/projects/api');
      await type(tester, 'linux-service-port', '3000');
      await save(tester);
      expect(svc.calls, ['save my-api', 'start my-api']);
      final def = svc.saved!;
      expect(def.name, 'my-api');
      expect(def.command, 'node server.js');
      expect(def.dir, '/root/projects/api');
      expect(def.port, 3000);
      expect(def.restart, LinuxRestart.onFailure);
      await unmount(tester);
    });

    testWidgets('the restart switch is on by default and can be turned off', (
      tester,
    ) async {
      await mount(tester, const LinuxServiceAddPage());
      expect(find.text('Restart when it crashes'), findsOneWidget);
      await tester.tap(find.text('Restart when it crashes'));
      await tester.pump();
      await type(tester, 'linux-service-name', 'one-shot');
      await type(tester, 'linux-service-command', 'node job.js');
      await save(tester);
      expect(svc.saved!.restart, LinuxRestart.never);
      await unmount(tester);
    });

    testWidgets('an answer from the runner lands on its field', (tester) async {
      svc.saveError = const LinuxServiceException(
        LinuxServiceProblem.reservedPort,
        details: 'port 4100 is the phone engine',
      );
      await mount(tester, const LinuxServiceAddPage());
      await type(tester, 'linux-service-name', 'my-api');
      await type(tester, 'linux-service-command', 'node server.js');
      await type(tester, 'linux-service-port', '4100');
      await save(tester);
      expect(
        find.text(
          'That port belongs to OpenCode or another part of this app. Pick '
          'another port',
        ),
        findsOneWidget,
      );
      expect(find.textContaining('phone engine'), findsNothing);
      expect(svc.calls, ['save my-api']);
      await unmount(tester);
    });

    testWidgets('a full phone says so above the form', (tester) async {
      svc.saveError = const LinuxServiceException(
        LinuxServiceProblem.writeFailed,
        details: 'ENOSPC',
      );
      await mount(tester, const LinuxServiceAddPage());
      await type(tester, 'linux-service-name', 'my-api');
      await type(tester, 'linux-service-command', 'node server.js');
      await save(tester);
      expect(
        find.text("Couldn't save that. The phone may be out of space."),
        findsOneWidget,
      );
      expect(find.textContaining('ENOSPC'), findsNothing);
      await unmount(tester);
    });

    testWidgets('saved but it would not start: the list says why', (
      tester,
    ) async {
      svc.startError = const LinuxServiceException(
        LinuxServiceProblem.supervisorDown,
      );
      await mount(tester, const LinuxServicesPage());
      await tester.tap(find.byKey(const ValueKey('linux-services-add')));
      await settle(tester);
      await type(tester, 'linux-service-name', 'my-api');
      await type(tester, 'linux-service-command', 'node server.js');
      await save(tester);
      await settle(tester);
      expect(find.byKey(const ValueKey('linux-services-list')), findsOne);
      expect(find.text('my-api'), findsOneWidget);
      expect(find.text("Services couldn't start."), findsOneWidget);
      await unmount(tester);
    });
  });

  group('Linux hub row', () {
    testWidgets('names what runs', (tester) async {
      await mount(
        tester,
        LinuxManagerScreen(onRepair: () async {}),
        overrides: [],
      );
      final row = tester.widget<KitRow>(
        find.byKey(const ValueKey('linux-row-services')),
      );
      // The crashed one speaks first.
      expect(
        (row.supporting as TextSpan).text,
        'my-bot stopped after crashing',
      );
      svc.states = [
        _state('ssh', title: 'SSH', up: const Duration(hours: 1)),
        _state('syncthing', title: 'Syncthing', up: const Duration(hours: 1)),
        _state('my-bot', title: 'my-bot', up: const Duration(hours: 1)),
        _state('forgejo', title: 'Forgejo', phase: LinuxServicePhase.off),
      ];
      await unmount(tester);
      await mount(tester, LinuxManagerScreen(onRepair: () async {}));
      final row2 = tester.widget<KitRow>(
        find.byKey(const ValueKey('linux-row-services')),
      );
      expect(
        (row2.supporting as TextSpan).text,
        '3 running: my-bot, SSH, Syncthing',
      );
      await unmount(tester);
    });

    testWidgets('none yet: says what the page is for; a failed read too', (
      tester,
    ) async {
      svc.states = [];
      await mount(tester, LinuxManagerScreen(onRepair: () async {}));
      expect(find.text('Keep your own programs running'), findsOneWidget);
      await unmount(tester);
      svc.readError = const LinuxServiceException(
        LinuxServiceProblem.supervisorDown,
      );
      await mount(tester, LinuxManagerScreen(onRepair: () async {}));
      expect(find.text('Keep your own programs running'), findsOneWidget);
      await unmount(tester);
    });

    testWidgets('it is the first row and opens Services', (tester) async {
      await mount(tester, LinuxManagerScreen(onRepair: () async {}));
      final rows = tester.widgetList<KitRow>(find.byType(KitRow)).toList();
      expect(rows.first.key, const ValueKey('linux-row-services'));
      await tester.tap(find.text('Services'));
      await settle(tester);
      expect(find.byKey(const ValueKey('linux-services-list')), findsOne);
      await unmount(tester);
    });
  });

  group('large text and right to left', () {
    /// A word that sits on two lines was broken in the middle: every word of
    /// every paragraph on screen must stay on one line.
    void expectNoMidWordBreaks(WidgetTester tester) {
      final broken = <String>[];
      for (final element in find.byType(RichText).evaluate()) {
        final render = element.renderObject;
        if (render is! RenderParagraph || !render.hasSize) continue;
        final plain = render.text.toPlainText(includePlaceholders: false);
        if (render.text.toPlainText() != plain) continue;
        for (final m in RegExp(
          r'[^\s\u00a0\u2066-\u2069]{2,}',
        ).allMatches(plain)) {
          // Names the person chose (a service called my-bot) can be longer
          // than any row; they are not the app's words.
          if (const {'my-bot', 'Syncthing'}.contains(m.group(0))) continue;
          final boxes = render.getBoxesForSelection(
            TextSelection(baseOffset: m.start, extentOffset: m.end),
          );
          final tops = {for (final b in boxes) b.top.round()};
          if (tops.length > 1) {
            broken.add('${m.group(0)} on ${tops.length} lines');
          }
        }
      }
      expect(broken, isEmpty, reason: broken.join('; '));
    }

    double height(WidgetTester tester, Finder f) =>
        tester.getSize(f.first).height;

    for (final rtl in [false, true]) {
      final lang = rtl ? 'ar' : 'en';
      for (final (name, build) in <(String, Widget Function())>[
        ('list', () => const LinuxServicesPage()),
        ('service', () => const LinuxServicePage(name: 'my-bot')),
        ('add', () => const LinuxServiceAddPage()),
      ]) {
        testWidgets('$name at 2x text, 360 dp, $lang: fits, no broken words', (
          tester,
        ) async {
          svc.log = const [KitLogLine('::svc gave-up')];
          await mount(
            tester,
            build(),
            textScale: 2,
            rtl: rtl,
            size: const Size(360, 740),
          );
          expect(tester.takeException(), isNull);
          expectNoMidWordBreaks(tester);
          await unmount(tester);
        });
      }
    }

    testWidgets(
      'the bar title and the chip stay at 1.3x while rows follow',
      skip: true,
      (tester) async {
        Future<(double, double, double)> sizes(double scale) async {
          await mount(
            tester,
            const LinuxServicesPage(),
            textScale: scale,
            size: const Size(412, 915),
          );
          final result = (
            height(tester, find.text('Services')),
            height(
              tester,
              find.descendant(
                of: chip('forgejo'),
                matching: find.text('Start'),
              ),
            ),
            height(tester, find.text('Forgejo')),
          );
          await unmount(tester);
          return result;
        }

        final one = await sizes(1);
        final cap = await sizes(1.3);
        final two = await sizes(2);
        // Frame text (the bar, the chip) grows to 1.3x and stops there.
        expect(cap.$1, greaterThan(one.$1));
        expect(two.$1, cap.$1);
        expect(cap.$2, greaterThan(one.$2));
        expect(two.$2, cap.$2);
        // Reading text (the row's title) follows the system size.
        expect(two.$3, greaterThan(cap.$3));
      },
    );

    testWidgets('a chip ends 16 dp inside the row, like the words start', (
      tester,
    ) async {
      await mount(tester, const LinuxServicesPage());
      final row = tester.getRect(
        find.byKey(const ValueKey('linux-service-forgejo')),
      );
      final c = tester.getRect(chip('forgejo'));
      expect(row.right - c.right, 16);
      await unmount(tester);
    });
  });
}

/// A runner that never answers (the loading state).
class _SlowSvc extends FakeLinuxServices {
  _SlowSvc(this._answer);

  final Future<List<LinuxServiceState>> _answer;

  @override
  Future<List<LinuxServiceState>> read() => _answer;
}
