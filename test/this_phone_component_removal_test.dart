// This phone: each optional tool setup installed can be removed on its own
// (slice P1.4). Each "Remove <tool>" names what it deletes; its question
// says what goes, what stays and the space measured now; a tool something
// else still uses says what and cannot be removed; the parts OpenCode needs
// go only with OpenCode. Removing OpenCode itself keeps the projects and
// says so line by line, and a removal that fails keeps its question open
// with Try again (P0.7). The page lays out every row at once, so a search
// result further down is always built (P9.4).
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/setup/component_removal.dart';
import 'package:opencode_mobile/builtin/team/builtin_team.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/this_phone_screen.dart';

import 'revamp/screen_phone_1_fixtures.dart';
import 'support/this_phone_tools_fakes.dart';

final _l10n = lookupAppLocalizations(const Locale('en'));

Future<void> _settle(WidgetTester tester, {int frames = 10}) async {
  for (var i = 0; i < frames; i++) {
    await tester.pump(const Duration(milliseconds: 100));
  }
}

Future<void> _scrollTo(WidgetTester tester, Finder finder) async {
  await tester.scrollUntilVisible(
    finder,
    200,
    scrollable: find.byType(Scrollable).first,
  );
  await tester.pump();
}

Future<void> _tap(WidgetTester tester, Finder finder) async {
  await _scrollTo(tester, finder);
  await tester.tap(finder);
  await _settle(tester);
}

void main() {
  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (call) async => call.method == 'readAll' ? <String, String>{} : null,
        );
  });

  Future<({ToolsLinux linux, ToolsTeam team})> inApp(
    WidgetTester tester, {
    ToolsLinux? linux,
    List<ServerProfile>? profiles,
  }) async {
    final phone = linux ?? ToolsLinux();
    final team = ToolsTeam(phone);
    await pumpPhone(
      tester,
      home: ThisPhoneScreen(
        removal: ComponentRemovalService(
          linux: phone,
          registry: toolsRegistry,
          team: team,
        ),
      ),
      linux: phone,
      profiles: profiles ?? [inAppProfile],
    );
    await _settle(tester);
    return (linux: phone, team: team);
  }

  testWidgets('a check that meets a busy moment is tried again before '
      '"Could not check" shows', (tester) async {
    // After Reset Linux's setup ended, This phone said "Could not check the
    // installed environment." on a working Linux; Try again then worked.
    final phone = ToolsLinux();
    await pumpPhone(
      tester,
      home: ThisPhoneScreen(
        removal: ComponentRemovalService(
          linux: phone,
          registry: toolsRegistry,
          team: ToolsTeam(phone),
        ),
      ),
      linux: phone,
      profiles: [inAppProfile],
      optionalInstalled: {'python'},
      optionalFailures: 1,
    );
    await _settle(tester, frames: 30);
    expect(find.text(_l10n.e7SetupCheckInstallFailed), findsNothing);
    await _scrollTo(
      tester,
      find.byKey(const ValueKey('this-phone-remove-python')),
    );
    await unmountPhone(tester);
  });

  testWidgets('each optional tool has its own Remove, saying what it '
      'deletes; the parts OpenCode needs have none', (tester) async {
    await inApp(tester);
    final python = find.byKey(const ValueKey('this-phone-remove-python'));
    await _scrollTo(tester, python);
    expect(find.text(_l10n.thisPhoneRemoveTool('Python')), findsOneWidget);
    expect(find.text(_l10n.thisPhoneRemovePythonDetail), findsOneWidget);
    expect(find.text(_l10n.thisPhoneRemoveTool('AI Team')), findsOneWidget);
    expect(find.text(_l10n.thisPhoneRemoveTeamDetail), findsOneWidget);
    // Not installed: nothing to remove.
    expect(
      find.byKey(const ValueKey('this-phone-remove-notebooks')),
      findsNothing,
    );
    // Required: they go only with OpenCode itself.
    expect(find.byKey(const ValueKey('this-phone-remove-node')), findsNothing);
    expect(
      find.byKey(const ValueKey('this-phone-remove-opencode')),
      findsNothing,
    );
    expect(find.byKey(const ValueKey('this-phone-remove')), findsOneWidget);
    await unmountPhone(tester);
  });

  testWidgets('Remove Python asks first with what goes, what stays and the '
      'space, then removes it and the row leaves', (tester) async {
    final (:linux, team: _) = await inApp(tester);
    await _tap(tester, find.byKey(const ValueKey('this-phone-remove-python')));
    expect(
      find.byKey(const ValueKey('this-phone-remove-python-sheet')),
      findsOneWidget,
    );
    expect(find.text(_l10n.thisPhoneRemoveToolTitle('Python')), findsOneWidget);
    expect(find.text(_l10n.thisPhoneRemoveToolBody('25.0 MB')), findsOneWidget);
    expect(find.text(_l10n.thisPhoneRemovePythonLost), findsOneWidget);
    expect(find.text(_l10n.thisPhoneRemovePythonKept), findsOneWidget);
    expect(linux.ran, isNot(contains('remove:python')));

    await tester.tap(
      find.byKey(const ValueKey('this-phone-remove-python-confirm')),
    );
    await _settle(tester);
    expect(linux.ran, contains('remove:python'));
    expect(
      find.byKey(const ValueKey('this-phone-remove-python-sheet')),
      findsNothing,
    );
    expect(
      find.byKey(const ValueKey('this-phone-remove-python')),
      findsNothing,
    );
    await unmountPhone(tester);
  });

  testWidgets('a failed removal keeps the question open with Try again', (
    tester,
  ) async {
    final linux = ToolsLinux()..removeExit = 1;
    await inApp(tester, linux: linux);
    await _tap(tester, find.byKey(const ValueKey('this-phone-remove-python')));
    await tester.tap(
      find.byKey(const ValueKey('this-phone-remove-python-confirm')),
    );
    await _settle(tester);
    expect(
      find.byKey(const ValueKey('this-phone-remove-python-sheet')),
      findsOneWidget,
    );
    expect(find.byKey(const ValueKey('kit-confirm-failed')), findsOneWidget);
    // No script or native words reach the page.
    expect(find.textContaining('exit'), findsNothing);

    linux.removeExit = 0;
    await tester.tap(find.text(_l10n.kitTryAgain));
    await _settle(tester);
    expect(
      find.byKey(const ValueKey('this-phone-remove-python-sheet')),
      findsNothing,
    );
    expect(linux.installedTools, isNot(contains('python')));
    await unmountPhone(tester);
  });

  testWidgets('a tool something still uses says what, and stays', (
    tester,
  ) async {
    final linux = ToolsLinux(
      present: const {'node', 'opencode', 'python', 'notebooks'},
    );
    await inApp(tester, linux: linux);
    final python = find.byKey(const ValueKey('this-phone-remove-python'));
    await _scrollTo(tester, python);
    expect(
      find.text(_l10n.thisPhoneRemoveToolNeededBy('Notebooks')),
      findsOneWidget,
    );
    // The row is off: a tap does nothing.
    await tester.tap(python, warnIfMissed: false);
    await _settle(tester);
    expect(
      find.byKey(const ValueKey('this-phone-remove-python-sheet')),
      findsNothing,
    );
    expect(linux.ran, isNot(contains('remove:python')));
    // The tool that uses it can go first.
    expect(
      find.byKey(const ValueKey('this-phone-remove-notebooks')),
      findsOneWidget,
    );
    await unmountPhone(tester);
  });

  testWidgets('Remove AI Team names the lost team work, removes the team '
      'and takes its config off the in-app profile', (tester) async {
    final profile = ServerProfile(
      id: inAppProfile.id,
      name: inAppProfile.name,
      baseUrl: inAppProfile.baseUrl,
      username: inAppProfile.username,
      password: 'secret',
      serverVersion: '1.18.29',
    )..orchestration = BuiltinTeam.config();
    final (linux: _, :team) = await inApp(tester, profiles: [profile]);
    await _tap(tester, find.byKey(const ValueKey('this-phone-remove-aiteam')));
    expect(find.text(_l10n.thisPhoneRemoveTeamLost), findsOneWidget);
    expect(find.text(_l10n.thisPhoneRemoveTeamLostWork), findsOneWidget);
    expect(find.text(_l10n.thisPhoneRemoveTeamKept), findsOneWidget);
    await tester.tap(
      find.byKey(const ValueKey('this-phone-remove-aiteam-confirm')),
    );
    await _settle(tester);
    expect(team.removes, 1);
    expect(profile.orchestration, isNull);
    expect(
      find.byKey(const ValueKey('this-phone-remove-aiteam')),
      findsNothing,
    );
    await unmountPhone(tester);
  });

  testWidgets('Remove OpenCode: the default keeps projects and says so line '
      'by line; a failure keeps the question open', (tester) async {
    final linux = ToolsLinux()..uninstallFailures = 1;
    await inApp(tester, linux: linux);
    await _tap(tester, find.byKey(const ValueKey('this-phone-remove')));
    expect(
      find.byKey(const ValueKey('phone-server-remove-sheet')),
      findsOneWidget,
    );
    // PhoneLinux measures 700.0 MB of runtime and 50.0 MB of projects.
    expect(find.text(_l10n.removeFromPhoneKeepBody('700.0 MB')), findsOne);
    expect(find.text(_l10n.removeFromPhoneKeepLost), findsOneWidget);
    expect(
      find.text(_l10n.removeFromPhoneKeepKeptSize('50.0 MB')),
      findsOneWidget,
    );
    await tester.tap(find.byKey(const ValueKey('phone-server-remove-confirm')));
    await _settle(tester);
    expect(
      find.byKey(const ValueKey('phone-server-remove-sheet')),
      findsOneWidget,
    );
    expect(find.byKey(const ValueKey('kit-confirm-failed')), findsOneWidget);
    expect(find.textContaining('permission denied'), findsNothing);
    expect(linux.installed, isTrue);

    await tester.tap(find.text(_l10n.kitTryAgain));
    await _settle(tester);
    expect(
      find.byKey(const ValueKey('phone-server-remove-sheet')),
      findsNothing,
    );
    expect(linux.installed, isFalse);
    await unmountPhone(tester);
  });

  testWidgets('every row is laid out at once, even on a small screen at '
      'large text (a search result lands on a row far down)', (tester) async {
    tester.platformDispatcher.textScaleFactorTestValue = 3;
    addTearDown(tester.platformDispatcher.clearTextScaleFactorTestValue);
    await pumpPhone(
      tester,
      size: const Size(320, 200),
      home: const ThisPhoneScreen(),
      linux: ToolsLinux(),
      profiles: [inAppProfile],
    );
    await _settle(tester);
    expect(
      find.byKey(const ValueKey('this-phone-remove'), skipOffstage: false),
      findsOneWidget,
    );
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pump(const Duration(seconds: 2));
    await unmountPhone(tester);
  });
}
