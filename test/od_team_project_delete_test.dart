// OD1 team lane, feature 3 (the phone's own AI Team): a project's page has a
// visible "Delete project <name>" row. It asks first, naming the project and
// saying what goes and what stays, and only then sends deleteProject with the
// confirmation the engine requires.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/team_project_controller.dart';
import 'package:opencode_mobile/ui/screens/team/projects/team_projects_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../tool/capture/fixtures.dart' show captureTheme, loadCaptureFonts;
import 'coverage/team_support.dart' show teamSettle;
import 'goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;
import 'od_team_support.dart' show odBoundary, odCapture;

class _Gateway implements OrchestrationProjectGateway {
  final commands = <TeamProjectCommand>[];
  bool refuse = false;

  @override
  Future<TeamWorkspace> teamWorkspace() async => const TeamWorkspace(
    servers: [TeamServer(id: 'phone', name: 'This phone', laneCap: 2)],
    roles: [],
    projects: [
      TeamProject(
        id: 'site',
        name: 'Launch site',
        revision: 4,
        status: 'running',
        simulated: false,
        settings: TeamProjectSettings(
          mode: 'single',
          maxLanes: 1,
          budget: TeamBudget(chosen: true, unlimited: true),
        ),
        repos: [TeamRepo(id: 'r', name: 'web', serverId: 'phone')],
        specDraft: TeamSpec(goal: 'Make our site accessible'),
      ),
    ],
  );
  @override
  Stream<TeamWorkspace> watchTeamWorkspace() => const Stream.empty();
  @override
  Future<TeamCommandResult> executeProject(TeamProjectCommand c) async {
    commands.add(c);
    return refuse
        ? const TeamCommandResult(accepted: false, code: 'projectBusy')
        : const TeamCommandResult(accepted: true);
  }

  @override
  Future<void> close() async {}
  @override
  Future<void> deleteLocalData() async {}
}

Future<_Gateway> _open(WidgetTester tester, {bool refuse = false}) async {
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  final gateway = _Gateway()..refuse = refuse;
  final controller = TeamProjectController(gateway);
  await controller.load();
  addTearDown(controller.dispose);
  await tester.pumpWidget(
    RepaintBoundary(
      key: odBoundary,
      child: MaterialApp(
        debugShowCheckedModeBanner: false,
        theme: captureTheme(),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: Builder(
          builder: (context) => TextButton(
            onPressed: () => Navigator.of(context).push(
              MaterialPageRoute<void>(
                builder: (_) => TeamProjectOverview(
                  controller: controller,
                  projectId: 'site',
                ),
              ),
            ),
            child: const Text('open'),
          ),
        ),
      ),
    ),
  );
  await tester.tap(find.text('open'));
  await teamSettle(tester);
  return gateway;
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(() async {
    await loadKitGalleryFonts();
    await loadCaptureFonts();
  });
  setUp(() => SharedPreferences.setMockInitialValues({}));

  testWidgets('delete asks first and then sends deleteProject, confirmed', (
    tester,
  ) async {
    final gateway = await _open(tester);
    final row = find.byKey(const ValueKey('team-project-delete'));
    await tester.scrollUntilVisible(
      row,
      300,
      scrollable: find.byType(Scrollable).first,
    );
    expect(find.text('Delete project Launch site'), findsOneWidget);
    await odCapture(tester, 'project-delete-row');
    await tester.tap(row);
    await teamSettle(tester);
    expect(find.text('Delete Launch site?'), findsOneWidget);
    expect(find.textContaining("can't be undone"), findsOneWidget);
    expect(
      find.text('Its plan, tasks and history are deleted'),
      findsOneWidget,
    );
    expect(
      find.text('The folder you added it from stays as it is'),
      findsOneWidget,
    );
    expect(gateway.commands, isEmpty, reason: 'nothing before the yes');
    await odCapture(tester, 'project-delete-confirm');

    await tester.tap(find.text('Cancel'));
    await teamSettle(tester);
    expect(gateway.commands, isEmpty);

    await tester.tap(row);
    await teamSettle(tester);
    await tester.tap(
      find.byKey(const ValueKey('team-project-delete-confirm-action')),
    );
    await teamSettle(tester);
    final command = gateway.commands.single;
    expect(command.action, TeamProjectAction.deleteProject);
    expect(command.projectId, 'site');
    expect(command.expectedRevision, 4);
    expect(command.confirmed, isTrue);
    // The page that held the project closes with it.
    expect(find.byKey(const ValueKey('team-project-delete')), findsNothing);
  });

  testWidgets('a refused delete leaves the page and the project', (
    tester,
  ) async {
    final gateway = await _open(tester, refuse: true);
    final row = find.byKey(const ValueKey('team-project-delete'));
    await tester.scrollUntilVisible(
      row,
      300,
      scrollable: find.byType(Scrollable).first,
    );
    await tester.tap(row);
    await teamSettle(tester);
    await tester.tap(
      find.byKey(const ValueKey('team-project-delete-confirm-action')),
    );
    await teamSettle(tester);
    expect(gateway.commands.single.action, TeamProjectAction.deleteProject);
    expect(find.byKey(const ValueKey('team-project-delete')), findsOneWidget);
  });
}
