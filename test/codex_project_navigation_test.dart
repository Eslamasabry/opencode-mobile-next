import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/codex/gateway.dart'
    show codexServerCapabilities;
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/manage_project_screen.dart';
import 'package:opencode_mobile/ui/screens/projects_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _CodexCapabilitiesApi extends OpenCodeApi {
  _CodexCapabilitiesApi() : super(baseUrl: 'http://localhost');

  @override
  ServerCapabilities get capabilities => codexServerCapabilities;
}

class _NoProjectCallsRepository implements ProductRepository {
  int listCalls = 0;
  int renameCalls = 0;

  @override
  Future<List<WorkspaceProject>> listProjects() async {
    listCalls++;
    return const [];
  }

  @override
  Future<WorkspaceProject> renameProject({
    required String projectID,
    required String projectDirectory,
    required String name,
  }) async {
    renameCalls++;
    throw StateError('project management should be gated');
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

Future<ConnectionController> _controller(
  _NoProjectCallsRepository repository,
) async {
  SharedPreferences.setMockInitialValues({});
  final controller =
      ConnectionController(
          ProfileStore(prefs: await SharedPreferences.getInstance()),
        )
        ..repository = repository
        ..directory = '/work/codex-project'
        ..api = _CodexCapabilitiesApi();
  return controller;
}

void main() {
  testWidgets(
    'Codex project management shows configured folder without unsupported rows',
    (tester) async {
      final repository = _NoProjectCallsRepository();
      final controller = await _controller(repository);
      addTearDown(controller.dispose);

      await tester.pumpWidget(
        MaterialApp(
          home: ManageProjectScreen(
            controller: controller,
            project: const WorkspaceProject(
              id: 'codex-project',
              name: 'Codex project',
              directory: '/work/codex-project',
              worktrees: [],
              updatedAt: 0,
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(
        find.byKey(const ValueKey('manage-project-context')),
        findsOneWidget,
      );
      expect(find.text('/work/codex-project'), findsOneWidget);
      for (final key in const [
        'switch-project-entry',
        'worktrees-entry',
        'managed-workspaces-entry',
        'project-health-entry',
      ]) {
        expect(find.byKey(ValueKey(key)), findsNothing, reason: key);
      }
      expect(repository.listCalls, 0);
      expect(repository.renameCalls, 0);
    },
  );

  testWidgets(
    'Codex ProjectsScreen does not list projects or expose project actions',
    (tester) async {
      final repository = _NoProjectCallsRepository();
      final controller = await _controller(repository);
      addTearDown(controller.dispose);

      await tester.pumpWidget(
        MaterialApp(
          home: ProjectsScreen(controller: controller, selectedProjectID: null),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Project context'), findsOneWidget);
      expect(
        find.byKey(const ValueKey('projects-configured-folder')),
        findsOneWidget,
      );
      expect(find.text('/work/codex-project'), findsOneWidget);
      expect(find.byKey(const ValueKey('project-search')), findsNothing);
      expect(repository.listCalls, 0);
      expect(repository.renameCalls, 0);
    },
  );
}
