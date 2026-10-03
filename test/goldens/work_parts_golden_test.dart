// Golden renders of the shell (chats-first: Chats, Files, Settings): the
// other servers panel, the shell's connection line on the other tabs, the
// PC sidebar at 1280x800 on Files, the explanation when Files goes away on a
// server without project tools, the command launcher and the keyboard
// shortcuts sheet at 412x915 and 1280x800. Dark and light, with the app's
// real fonts. The Chats tab itself is rendered by its own slice's goldens.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/goldens/work_parts_golden_test.dart
// and look at every changed image before committing it.
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/domain/server_gateway.dart'
    show ServerCapabilities;
import 'package:opencode_mobile/state/profile_monitor.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_iconography.dart';
import 'package:opencode_mobile/ui/desktop/shortcuts.dart';
import 'package:opencode_mobile/ui/kit/kit_motion.dart';
import 'package:opencode_mobile/ui/screens/home_screen.dart';
import 'package:opencode_mobile/ui/widgets/other_servers_panel.dart';
import 'package:opencode_mobile/ui/widgets/app_connection_status.dart';

import '../../tool/capture/fixtures.dart'
    show CaptureApi, captureApp, loadCaptureFonts;
import '../support/work_tab_fixture.dart';

void _mockSecureStorage(WidgetTester tester) {
  const secure = MethodChannel('plugins.it_nomads.com/flutter_secure_storage');
  tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
    secure,
    (call) async => call.method == 'readAll' ? <String, String>{} : null,
  );
  addTearDown(
    () => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
      secure,
      null,
    ),
  );
}

Future<void> _golden(
  WidgetTester tester,
  String name, {
  required bool light,
  required WorkController controller,
  Widget home = const HomeScreen(initialTab: 0),
  VoidCallback? dispose,
  Duration settle = Duration.zero,
  Size size = const Size(412, 915),
  Future<void> Function(WidgetTester tester)? then,
}) async {
  _mockSecureStorage(tester);
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  final boundary = GlobalKey();
  final navigatorKey = GlobalKey<NavigatorState>();
  try {
    await tester.pumpWidget(
      captureApp(
        navigatorKey: navigatorKey,
        // The shortcut layer as main.dart installs it, so the shell shows
        // its search button (it opens the launcher).
        // The app's status slot as main.dart hosts it above every page:
        // since the one controller-owned connection status (3d64653c) the
        // shell's connection line comes from here, not from the page.
        home: AppConnectionStatusScope(
          controller: controller,
          navigatorKey: navigatorKey,
          child: AppShortcuts(
            navigatorKey: navigatorKey,
            signals: AppShortcutSignals(),
            handlers: AppShortcutHandlers(
              onNewSession: () {},
              onOpenSettings: () {},
              paletteCommands: (_) => _commands(),
            ),
            child: home,
          ),
        ),
        boundaryKey: boundary,
        controller: controller,
        light: light,
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 500));
    await tester.pump(settle);
    if (then != null) {
      await then(tester);
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 600));
    }
    expect(tester.takeException(), isNull);
    await expectLater(
      find.byKey(boundary),
      matchesGoldenFile('${name}_${light ? 'light' : 'dark'}.png'),
    );
  } finally {
    await tester.pumpWidget(const SizedBox.shrink());
    dispose?.call();
    controller.dispose();
    await tester.pump();
  }
}

/// The other servers' watcher, answering with fixed snapshots: Claude Code
/// on this phone waits on an approval, the laptop has two runs going.
class _Monitor extends ProfileMonitor {
  _Monitor(ProfileStore store)
    : super(
        store: store,
        createGateway: (_) => throw UnimplementedError(),
        isReadable: (_) => true,
        networkWifi: () async => null,
        alert: (_, _, _, _) async => false,
        dismiss: (_) async => false,
      );

  @override
  ProfileAttentionSnapshot snapshotFor(String id) => switch (id) {
    // A real check has a time: the Inbox badge counts only requests a
    // check actually saw (P4.2b attention feed).
    'claude' => ProfileAttentionSnapshot(
      profileID: id,
      status: ProfileMonitorStatus.current,
      checkedAt: DateTime.now(),
      complete: true,
      attentionComplete: true,
      runningCount: 1,
      requests: const [
        MonitoredRequest(
          id: 'perm-1',
          sessionID: 's-1',
          kind: MonitoredRequestKind.permission,
        ),
      ],
    ),
    'laptop' => ProfileAttentionSnapshot(
      profileID: id,
      status: ProfileMonitorStatus.current,
      checkedAt: DateTime.now(),
      complete: true,
      attentionComplete: true,
      runningCount: 2,
    ),
    _ => ProfileAttentionSnapshot(
      profileID: id,
      status: ProfileMonitorStatus.disabled,
    ),
  };
}

/// A server without project tools (Codex, Paseo today).
class _NoProjectApi extends CaptureApi {
  @override
  ServerCapabilities get capabilities => const ServerCapabilities(
    fileBrowsing: false,
    terminal: false,
    projectManagement: false,
    globalSessionSearch: false,
    sessionImportExport: false,
    serverCatalog: false,
  );
}

/// The launcher's commands as main.dart lists them, fixed for the render.
List<DesktopCommand> _commands() => [
  DesktopCommand(
    label: 'New conversation',
    icon: AppIconography.add,
    hint: 'Start a conversation in the active project',
    keys: 'Ctrl + N',
    onInvoke: () {},
  ),
  DesktopCommand(
    label: 'Chats',
    icon: AppIconography.chat,
    hint: 'Every conversation, across projects',
    keys: 'Ctrl + 1',
    onInvoke: () {},
  ),
  DesktopCommand(
    label: 'Files',
    icon: AppIconography.files,
    hint: 'Files, changes, terminal and other project tools',
    keys: 'Ctrl + 2',
    onInvoke: () {},
  ),
  DesktopCommand(
    label: 'Settings',
    icon: AppIconography.settings,
    hint: 'Models, providers, notifications, settings',
    keys: 'Ctrl + ,',
    onInvoke: () {},
  ),
  DesktopCommand(
    label: 'Keyboard shortcuts',
    icon: AppIconography.keyboard,
    keys: 'Ctrl + /',
    onInvoke: () {},
  ),
];

BuildContext _shellContext(WidgetTester tester) =>
    tester.element(find.byType(HomeScreen));

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);

  for (final light in [false, true]) {
    final mode = light ? 'light' : 'dark';

    testWidgets('work · other servers · $mode', (tester) async {
      final controller = await workController(
        sessions: workLoadedSessions(),
        otherServers: [
          ServerProfile(
            id: 'claude',
            name: 'Claude Code (this phone)',
            baseUrl: 'http://127.0.0.1:6767',
          ),
          ServerProfile(
            id: 'laptop',
            name: 'Laptop',
            baseUrl: 'http://100.64.0.7:4096',
          ),
        ],
      );
      final monitor = _Monitor(controller.store);
      controller.monitor = monitor;
      await _golden(
        tester,
        'work_other_servers',
        light: light,
        controller: controller,
        home: Scaffold(
          body: SafeArea(
            child: ListView(
              children: [OtherServersPanel(controller: controller)],
            ),
          ),
        ),
        dispose: monitor.dispose,
      );
    });

    testWidgets('shell · connection lost on Settings · $mode', (tester) async {
      final controller = await workController(status: StreamStatus.disconnected)
        ..lastError = 'Cannot reach http://127.0.0.1:4096: timed out';
      await _golden(
        tester,
        'shell_reconnecting',
        light: light,
        controller: controller,
        home: const HomeScreen(initialTab: 2),
        // Settings' drawing finishes its entrance.
        settle: KitMotion.entrance,
      );
    });

    const wide = Size(1280, 800);

    testWidgets('shell · PC sidebar · $mode', (tester) async {
      final controller = await workController(sessions: workLoadedSessions());
      await _golden(
        tester,
        'shell_home_shell_files_1280x800',
        light: light,
        controller: controller,
        home: const HomeScreen(initialTab: 1),
        size: wide,
      );
    });

    testWidgets('shell · Files went away · $mode', (tester) async {
      final controller = await workController(sessions: workLoadedSessions());
      await _golden(
        tester,
        'shell_home_shell_files_unavailable',
        light: light,
        controller: controller,
        home: const HomeScreen(initialTab: 1),
        then: (tester) async {
          controller.api = _NoProjectApi();
          controller.notifyListeners();
        },
      );
    });

    for (final (suffix, size) in [
      ('', const Size(412, 915)),
      ('_1280x800', wide),
    ]) {
      testWidgets('shell · command launcher$suffix · $mode', (tester) async {
        final controller = await workController(sessions: workLoadedSessions());
        await _golden(
          tester,
          'shell_command_palette_open$suffix',
          light: light,
          controller: controller,
          home: const HomeScreen(initialTab: 1),
          size: size,
          then: (tester) async {
            showCommandPalette(_shellContext(tester), _commands());
          },
        );
      });

      testWidgets('shell · keyboard shortcuts$suffix · $mode', (tester) async {
        final controller = await workController(sessions: workLoadedSessions());
        await _golden(
          tester,
          'shell_shortcuts_help_open$suffix',
          light: light,
          controller: controller,
          home: const HomeScreen(initialTab: 1),
          size: size,
          then: (tester) async {
            showShortcutsHelp(_shellContext(tester));
          },
        );
      });
    }
  }
}
