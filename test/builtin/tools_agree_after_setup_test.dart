// After an Add tools attempt ended (build 2065, emulator 2026-09-29), This
// phone listed AI Team as installed while Settings > AI Team still said Off
// with "About 125 MB to download": each page read the phone once, when it
// opened, and neither noticed the job that changed it. Both pages read again
// when a setup job ends, whatever way it ends.

import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/builtin/setup/phone_setup.dart';
import 'package:opencode_mobile/builtin/setup/setup_contract.dart';
import 'package:opencode_mobile/builtin/team/builtin_team.dart';
import 'package:opencode_mobile/builtin/team/builtin_team_job.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/this_phone_screen.dart';
import 'package:opencode_mobile/ui/widgets/builtin_team_section.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../revamp/screen_phone_1_fixtures.dart';
import '../support/fake_setup_engine.dart';

class _Store extends ProfileStore {
  _Store({required super.prefs, required this.saved});

  final List<ServerProfile> saved;

  @override
  List<ServerProfile> get profiles => List.unmodifiable(saved);

  @override
  String? get activeId => saved.first.id;
}

class _Team extends BuiltinTeam {
  _Team() : super(linux: BuiltinLinux());

  BuiltinTeamState state = const BuiltinTeamState();

  @override
  Future<BuiltinTeamState> status() async => state;

  @override
  Future<void> prepare() async {}
}

void main() {
  final en = lookupAppLocalizations(const Locale('en'));

  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (call) async => call.method == 'readAll' ? <String, String>{} : null,
        );
  });

  testWidgets('This phone lists the tool a failed job left installed', (
    tester,
  ) async {
    final engine = await pumpPhone(
      tester,
      home: const ThisPhoneScreen(),
      linux: PhoneLinux(running: true),
      profiles: [inAppProfile],
    );
    final installed = find.descendant(
      of: find.byKey(const ValueKey('this-phone-installed')),
      matching: find.textContaining('Python'),
    );
    expect(installed, findsNothing);

    // The job installed Python, then its last step failed (a job ended).
    engine.optionalInstalled = {'python'};
    setupToolsChanged.value++;
    for (var i = 0; i < 10; i++) {
      await tester.pump(const Duration(milliseconds: 100));
    }
    expect(installed, findsWidgets);
    await unmountPhone(tester);
  });

  testWidgets(
    'a slow read from before the job never overwrites the newer one',
    (tester) async {
      final late = Completer<void>();
      final engine = await pumpPhone(
        tester,
        home: const ThisPhoneScreen(),
        linux: PhoneLinux(running: true),
        profiles: [inAppProfile],
      );
      final installed = find.descendant(
        of: find.byKey(const ValueKey('this-phone-installed')),
        matching: find.textContaining('Python'),
      );
      // A read starts before the tools exist and is slow to answer.
      engine.optionalGate = late.future;
      setupToolsChanged.value++;
      await tester.pump(const Duration(milliseconds: 100));
      // The job ends with Python installed; the newer read answers at once.
      engine.optionalInstalled = {'python'};
      engine.optionalGate = null;
      setupToolsChanged.value++;
      for (var i = 0; i < 10; i++) {
        await tester.pump(const Duration(milliseconds: 100));
      }
      expect(installed, findsWidgets);
      late.complete();
      for (var i = 0; i < 5; i++) {
        await tester.pump(const Duration(milliseconds: 100));
      }
      expect(installed, findsWidgets);
      await unmountPhone(tester);
    },
  );

  testWidgets('a failed refresh keeps Python until removal is confirmed', (
    tester,
  ) async {
    final engine = await pumpPhone(
      tester,
      home: const ThisPhoneScreen(),
      linux: PhoneLinux(running: true),
      profiles: [inAppProfile],
      optionalInstalled: {'python'},
    );
    final installed = find.descendant(
      of: find.byKey(const ValueKey('this-phone-installed')),
      matching: find.textContaining('Python'),
    );
    expect(installed, findsWidgets);
    final failed = Completer<void>();
    engine.optionalGate = failed.future;
    setupToolsChanged.value++;
    await tester.pump(const Duration(milliseconds: 100));
    failed.completeError(
      const BuiltinLinuxException('Another task is running'),
    );
    for (var i = 0; i < 10; i++) {
      await tester.pump(const Duration(milliseconds: 100));
    }
    expect(installed, findsWidgets);

    engine.optionalGate = null;
    engine.optionalInstalled = {};
    setupToolsChanged.value++;
    for (var i = 0; i < 10; i++) {
      await tester.pump(const Duration(milliseconds: 100));
    }
    expect(installed, findsNothing);
    await unmountPhone(tester);
  });

  testWidgets('Settings > AI Team reads the phone again when a job ends', (
    tester,
  ) async {
    debugPlatformCapabilities = const PlatformCapabilities.android();
    SharedPreferences.setMockInitialValues({});
    final profile = ServerProfile(
      id: 'phone',
      name: 'This phone',
      baseUrl: BuiltinLinux.serverUrl,
      username: BuiltinLinux.serverUsername,
      password: 'secret',
    );
    final connection = ConnectionController(
      _Store(prefs: await SharedPreferences.getInstance(), saved: [profile]),
    )..directory = '/root/projects/my-app';
    final team = _Team();
    debugBuiltinTeam = team;
    BuiltinTeamJob.debugShared = BuiltinTeamJob();
    final engine = FakeSetupEngine();
    final previous = PhoneSetup.engine;
    PhoneSetup.engine = engine;
    addTearDown(() {
      PhoneSetup.engine = previous;
      debugBuiltinTeam = null;
      BuiltinTeamJob.debugShared = null;
      debugPlatformCapabilities = null;
      connection.dispose();
    });

    await tester.pumpWidget(
      MaterialApp(
        locale: const Locale('en'),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: Scaffold(
          body: SingleChildScrollView(
            child: BuiltinTeamSection(connection: connection, profile: profile),
          ),
        ),
      ),
    );
    await tester.pump();
    await tester.pump();
    // Not installed: the page offers the download.
    expect(find.byKey(const ValueKey('builtin-team-offer')), findsOneWidget);

    // Add tools installs AI Team, then its last step fails.
    team.state = const BuiltinTeamState(installed: true);
    setupToolsChanged.value++;
    await tester.pump();
    await tester.pump();
    expect(find.byKey(const ValueKey('builtin-team-offer')), findsNothing);
    expect(
      find.byKey(const ValueKey('builtin-team-turn-on-body')),
      findsOneWidget,
    );
    expect(find.text(en.aiteamComponentAdd), findsNothing);
    await tester.pumpWidget(const SizedBox());
  });
}
