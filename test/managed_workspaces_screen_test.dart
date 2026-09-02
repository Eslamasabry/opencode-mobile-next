import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/managed_workspaces_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

const _project = WorkspaceProject(
  id: 'project-1',
  name: 'OpenCode Mobile',
  directory: '/work/app',
  worktrees: [],
  updatedAt: 1,
);

class _ManagedWorkspaceRepository implements ProductRepository {
  List<WorkspaceInfo> workspaces = [
    const WorkspaceInfo(
      id: 'wrk_remote',
      projectID: 'project-1',
      name: 'Phone runner',
      type: 'cloud',
      branch: 'feature/mobile',
      directory: '/remote/app',
      status: 'connected',
    ),
  ];
  List<WorkspaceAdapterInfo> adapters = const [
    WorkspaceAdapterInfo(
      type: 'cloud',
      name: 'Cloud runner',
      description: 'Create an isolated remote runner',
    ),
  ];
  int syncCalls = 0;
  String? createdType;
  String? createdBranch;
  String? removedID;

  @override
  void setLocation({String? directory, String? workspace}) {}

  @override
  Future<List<WorkspaceInfo>> listWorkspaces() async => workspaces;

  @override
  Future<List<WorkspaceInfo>> listManagedWorkspaces({
    required String projectDirectory,
  }) async => List.of(workspaces);

  @override
  Future<List<WorkspaceAdapterInfo>> listWorkspaceAdapters({
    required String projectDirectory,
  }) async => adapters;

  @override
  Future<void> syncWorkspaceList({required String projectDirectory}) async {
    syncCalls += 1;
  }

  @override
  Future<WorkspaceInfo> createManagedWorkspace({
    required String projectDirectory,
    required String type,
    String? branch,
  }) async {
    createdType = type;
    createdBranch = branch;
    final workspace = WorkspaceInfo(
      id: 'wrk_created',
      projectID: 'project-1',
      name: 'Created runner',
      type: type,
      branch: branch,
      directory: '/remote/created',
      status: 'connected',
    );
    workspaces = [...workspaces, workspace];
    return workspace;
  }

  @override
  Future<void> removeManagedWorkspace({
    required String projectDirectory,
    required String id,
  }) async {
    removedID = id;
    workspaces = workspaces.where((workspace) => workspace.id != id).toList();
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _ManagedWorkspaceController extends ConnectionController {
  _ManagedWorkspaceController(super.store, this.managedRepository) {
    repository = managedRepository;
  }

  final _ManagedWorkspaceRepository managedRepository;
  final locations = <({String? directory, String? workspace})>[];

  @override
  Future<ProductRepository?> prepareActionRepository() async =>
      managedRepository;

  @override
  Future<void> selectLocation({String? directory, String? workspace}) async {
    locations.add((directory: directory, workspace: workspace));
    this.directory = directory;
    this.workspace = workspace;
    locationError = null;
    notifyListeners();
  }
}

Future<_ManagedWorkspaceController> _controller(
  _ManagedWorkspaceRepository repository,
) async {
  SharedPreferences.setMockInitialValues({});
  return _ManagedWorkspaceController(
    ProfileStore(prefs: await SharedPreferences.getInstance()),
    repository,
  );
}

Widget _host(ManagedWorkspacesScreen screen, {double textScale = 1}) =>
    MaterialApp(
      builder: (context, child) => MediaQuery(
        data: MediaQuery.of(
          context,
        ).copyWith(textScaler: TextScaler.linear(textScale)),
        child: child!,
      ),
      home: Builder(
        builder: (context) => Scaffold(
          body: Center(
            child: FilledButton(
              key: const ValueKey('open-managed-workspaces'),
              onPressed: () => Navigator.of(
                context,
              ).push(MaterialPageRoute<void>(builder: (_) => screen)),
              child: const Text('Open'),
            ),
          ),
        ),
      ),
    );

Future<void> _open(WidgetTester tester, ManagedWorkspacesScreen screen) async {
  await tester.pumpWidget(_host(screen));
  await tester.tap(find.byKey(const ValueKey('open-managed-workspaces')));
  await tester.pumpAndSettle();
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('managed workspace screen fits a compact large-text phone', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(640, 1280);
    tester.view.devicePixelRatio = 2;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final repository = _ManagedWorkspaceRepository();
    final controller = await _controller(repository);
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      _host(
        ManagedWorkspacesScreen(controller: controller, project: _project),
        textScale: 2,
      ),
    );
    await tester.tap(find.byKey(const ValueKey('open-managed-workspaces')));
    await tester.pumpAndSettle();

    expect(find.text('Cloud environments'), findsOneWidget);
    expect(
      find.byKey(const ValueKey('managed-workspace-wrk_remote')),
      findsOneWidget,
    );
    await tester.scrollUntilVisible(
      find.text('Cloud runner'),
      240,
      scrollable: find.descendant(
        of: find.byKey(const ValueKey('managed-workspaces-list')),
        matching: find.byType(Scrollable),
      ),
    );
    expect(find.text('Cloud runner'), findsOneWidget);
    expect(tester.takeException(), isNull);

    await tester.tap(find.byKey(const ValueKey('sync-managed-workspaces')));
    await tester.pumpAndSettle();
    expect(repository.syncCalls, 1);
    expect(tester.takeException(), isNull);

    await tester.tap(find.byKey(const ValueKey('create-managed-workspace')));
    await tester.pumpAndSettle();
    expect(find.text('New managed workspace'), findsOneWidget);
    expect(tester.takeException(), isNull);
    await tester.tap(find.text('Cancel'));
    await tester.pumpAndSettle();
  });

  testWidgets('creating a workspace uses an adapter then opens exact result', (
    tester,
  ) async {
    final repository = _ManagedWorkspaceRepository();
    final controller = await _controller(repository);
    addTearDown(controller.dispose);
    await _open(
      tester,
      ManagedWorkspacesScreen(controller: controller, project: _project),
    );

    await tester.tap(find.byKey(const ValueKey('create-managed-workspace')));
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const ValueKey('workspace-branch-input')),
      'feature/phone',
    );
    await tester.tap(
      find.byKey(const ValueKey('confirm-create-managed-workspace')),
    );
    await tester.pumpAndSettle();

    expect(repository.createdType, 'cloud');
    expect(repository.createdBranch, 'feature/phone');
    expect(controller.directory, '/remote/created');
    expect(controller.workspace, 'wrk_created');
  });

  testWidgets('removing the active workspace returns local before deletion', (
    tester,
  ) async {
    final repository = _ManagedWorkspaceRepository();
    final controller = await _controller(repository);
    controller
      ..directory = '/remote/app'
      ..workspace = 'wrk_remote';
    addTearDown(controller.dispose);
    await _open(
      tester,
      ManagedWorkspacesScreen(controller: controller, project: _project),
    );

    final tile = find.byKey(const ValueKey('managed-workspace-wrk_remote'));
    await tester.tap(
      find.descendant(of: tile, matching: find.byType(PopupMenuButton<String>)),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('Remove'));
    await tester.pumpAndSettle();
    expect(
      tester
          .widget<FilledButton>(
            find.byKey(const ValueKey('confirm-remove-managed-workspace')),
          )
          .onPressed,
      isNull,
    );
    await tester.enterText(
      find.byKey(const ValueKey('remove-managed-workspace-confirmation')),
      'Phone runner',
    );
    await tester.pump();
    await tester.tap(
      find.byKey(const ValueKey('confirm-remove-managed-workspace')),
    );
    await tester.pumpAndSettle();

    expect(controller.locations.first.directory, '/work/app');
    expect(controller.locations.first.workspace, isNull);
    expect(repository.removedID, 'wrk_remote');
    expect(
      find.byKey(const ValueKey('managed-workspace-wrk_remote')),
      findsNothing,
    );
  });
}
