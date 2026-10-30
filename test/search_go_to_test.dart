// Search's own empty state (owner, 2026-10-20, mockup 6): a "Go to" group of
// the places the bottom bar and other screens gave one tap to, then the last
// three conversations; gated on what the connection supports; typing
// filters the same rows with the rest of the launcher.
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/desktop/shortcuts.dart';
import 'package:opencode_mobile/ui/search/go_to_commands.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'support/fake_server_gateway.dart';

class _Api extends FakeServerGateway {
  _Api(this.caps) : super(baseUrl: 'http://localhost');
  final ServerCapabilities caps;

  @override
  ServerCapabilities get capabilities => caps;
}

class _Store extends ProfileStore {
  _Store({required super.prefs, this.saved = true});
  final bool saved;

  @override
  List<ServerProfile> get profiles => [
    if (saved)
      ServerProfile(id: 'local', name: 'Laptop', baseUrl: 'http://localhost'),
  ];

  @override
  String? get activeId => saved ? 'local' : null;
}

final _now = DateTime.now().millisecondsSinceEpoch;

Session _session(String id, String title, int minutesAgo) => Session(
  id: id,
  title: title,
  directory: '/srv/shopfront',
  time: SessionTime(
    created: _now - (minutesAgo + 5) * 60000,
    updated: _now - minutesAgo * 60000,
  ),
);

Future<ConnectionController> _controller({
  ServerCapabilities caps = const ServerCapabilities(),
  bool saved = true,
}) async {
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  return ConnectionController(_Store(prefs: prefs, saved: saved))
    ..api = _Api(caps)
    ..status = StreamStatus.connected
    ..directory = '/srv/shopfront'
    ..sessionsById = {
      for (final session in [
        _session('a', 'Fix the login bug', 2),
        _session('b', 'Add dark mode', 30),
        _session('c', 'Explain the build', 90),
        _session('d', 'Old work', 600),
      ])
        session.id: session,
    };
}

List<DesktopCommand> _commands(ConnectionController controller) => [
  DesktopCommand(label: 'New conversation', icon: Icons.add, onInvoke: () {}),
  ...goToCommands(
    lookupAppLocalizations(const Locale('en')),
    controller: controller,
    target: () => null,
    atShell: (_) {},
  ),
];

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (call) async => call.method == 'readAll' ? <String, String>{} : null,
        );
  });

  test(
    'empty: Go to places first, then the last three conversations',
    () async {
      final controller = await _controller();
      addTearDown(controller.dispose);
      final shown = matchCommands(_commands(controller), '');
      final goTo = [
        for (final command in shown)
          if (command.section == DesktopCommandSection.goTo) command.label,
      ];
      expect(goTo, [
        'Needs you',
        'Files',
        'Projects',
        'Settings',
        'Servers',
        'Terminal',
        'Help',
      ]);
      // What waits on the person comes first, with how many.
      expect(shown.first.hint, 'Nothing waits');
      expect(
        shown.firstWhere((command) => command.label == 'Files').detail,
        'shopfront',
      );
      final recent = [
        for (final command in shown)
          if (command.section == DesktopCommandSection.recent) command.label,
      ];
      expect(recent, [
        'Fix the login bug',
        'Add dark mode',
        'Explain the build',
      ]);
      // Not a place: found once typed.
      expect(
        shown.map((command) => command.label),
        isNot(contains('New conversation')),
      );
    },
  );

  test('rows follow what the connection supports, never its flavor', () async {
    final controller = await _controller(
      caps: const ServerCapabilities(
        fileBrowsing: false,
        terminal: false,
        projectManagement: false,
      ),
      saved: false,
    );
    addTearDown(controller.dispose);
    final labels = [
      for (final command in matchCommands(_commands(controller), ''))
        if (command.section == DesktopCommandSection.goTo) command.label,
    ];
    expect(labels, ['Needs you', 'Settings', 'Add a server', 'Help']);
  });

  test('typing filters the same rows with the rest of the launcher', () async {
    final controller = await _controller();
    addTearDown(controller.dispose);
    final commands = _commands(controller);
    expect(
      matchCommands(commands, 'term').map((command) => command.label),
      contains('Terminal'),
    );
    expect(
      matchCommands(commands, 'sett').map((command) => command.label),
      contains('Settings'),
    );
    // The Inbox's name finds the Needs you row.
    expect(
      matchCommands(commands, 'inbox').map((command) => command.label),
      contains('Needs you'),
    );
    // The project's name finds its Files.
    expect(
      matchCommands(commands, 'shopfront').map((command) => command.label),
      contains('Files'),
    );
    expect(
      matchCommands(commands, 'new conv').map((command) => command.label),
      contains('New conversation'),
    );
    // The Recent rows step aside: conversations are searched as themselves.
    expect(
      matchCommands(
        commands,
        'login',
      ).where((command) => command.section == DesktopCommandSection.recent),
      isEmpty,
    );
  });

  testWidgets('Search opens on the Go to menu and typing narrows it', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(412, 1400);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final controller = await _controller();
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: Builder(
          builder: (context) => Scaffold(
            body: TextButton(
              onPressed: () =>
                  showCommandPalette(context, _commands(controller)),
              child: const Text('open'),
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.text('open'));
    await tester.pumpAndSettle();
    expect(find.text('Go to'), findsOneWidget);
    expect(find.text('Recent'), findsOneWidget);
    expect(find.byKey(const ValueKey('go-to-settings')), findsOneWidget);
    expect(find.byKey(const ValueKey('go-to-terminal')), findsOneWidget);
    expect(find.text('New conversation'), findsNothing);

    await tester.enterText(
      find.byKey(const ValueKey('command-palette-query')),
      'sett',
    );
    await tester.pumpAndSettle();
    expect(find.text('Go to'), findsNothing);
    expect(find.byKey(const ValueKey('go-to-settings')), findsOneWidget);
    expect(find.byKey(const ValueKey('go-to-terminal')), findsNothing);
  });
}
