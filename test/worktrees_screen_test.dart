import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/worktrees_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

const _project = WorkspaceProject(
  id: 'project-1',
  name: 'OpenCode Mobile',
  directory: '/work/app',
  worktrees: ['/data/worktree/project-1/mobile-review'],
  updatedAt: 1,
);

const _aliasedProject = WorkspaceProject(
  id: 'project-1',
  name: 'OpenCode Mobile',
  directory: '/work/app',
  worktrees: [
    '/legacy/data/project-1/mobile-review',
    '/data/worktree/project-1/mobile-review',
  ],
  updatedAt: 1,
);

class _WorktreeRepository implements ProductRepository {
  List<WorktreeInfo> worktrees = const [
    WorktreeInfo(
      name: 'mobile-review',
      directory: '/data/worktree/project-1/mobile-review',
      branch: 'opencode/mobile-review',
    ),
  ];
  List<VersionControlFile> statuses = const [
    VersionControlFile(
      path: 'lib/main.dart',
      status: 'modified',
      additions: 4,
      deletions: 1,
    ),
  ];
  final resetCalls = <String>[];
  final removeCalls = <String>[];
  String? createName;

  @override
  void setLocation({String? directory, String? workspace}) {}

  @override
  Future<List<WorktreeInfo>> listWorktrees({
    required String projectDirectory,
    String? projectID,
  }) async => List.of(worktrees);

  @override
  Future<WorktreeInfo> createWorktree({
    required String projectDirectory,
    String? name,
  }) async {
    createName = name;
    final created = WorktreeInfo(
      name: name?.isNotEmpty == true ? name! : 'fresh-tree',
      directory: '/data/worktree/project-1/${name ?? 'fresh-tree'}',
      branch: 'opencode/${name ?? 'fresh-tree'}',
    );
    worktrees = [...worktrees, created];
    return created;
  }

  @override
  Future<List<VersionControlFile>> listWorktreeFileStatuses(
    String directory,
  ) async => statuses;

  @override
  Future<void> resetWorktree({
    required String projectDirectory,
    required String directory,
  }) async => resetCalls.add(directory);

  @override
  Future<void> removeWorktree({
    required String projectDirectory,
    required String directory,
  }) async {
    removeCalls.add(directory);
    worktrees = worktrees
        .where((worktree) => worktree.directory != directory)
        .toList();
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _WorktreeController extends ConnectionController {
  _WorktreeController(super.store);

  final locations = <String?>[];

  @override
  Future<ProductRepository?> prepareActionRepository() async => repository;

  @override
  Future<void> selectLocation({String? directory, String? workspace}) async {
    if (this.directory == directory && this.workspace == workspace) return;
    locations.add(directory);
    this.directory = directory;
    this.workspace = workspace;
    dataRefreshRevision += 1;
    notifyListeners();
  }
}

Future<_WorktreeController> _controller(
  ProductRepository repository, {
  String directory = '/work/app',
}) async {
  SharedPreferences.setMockInitialValues({});
  final preferences = await SharedPreferences.getInstance();
  return _WorktreeController(ProfileStore(prefs: preferences))
    ..repository = repository
    ..directory = directory
    ..status = StreamStatus.connected;
}

Widget _app(
  ConnectionController controller, {
  double textScale = 1,
  WorkspaceProject project = _project,
}) => MaterialApp(
  home: Builder(
    builder: (context) => MediaQuery(
      data: MediaQuery.of(
        context,
      ).copyWith(textScaler: TextScaler.linear(textScale)),
      child: WorktreesScreen(controller: controller, project: project),
    ),
  ),
);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('compact worktree creation grows into global ready state', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(320, 640);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final repository = _WorktreeRepository();
    final controller = await _controller(repository);
    addTearDown(controller.dispose);

    await tester.pumpWidget(_app(controller, textScale: 2));
    await tester.pumpAndSettle();

    expect(find.byKey(const ValueKey('primary-worktree')), findsOneWidget);
    await tester.scrollUntilVisible(
      find.byKey(
        const ValueKey('worktree-/data/worktree/project-1/mobile-review'),
      ),
      120,
      scrollable: find.byType(Scrollable).first,
    );
    expect(find.text('mobile-review'), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('create-worktree')));
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const ValueKey('worktree-name-field')),
      'wake-fix',
    );
    await tester.tap(find.byKey(const ValueKey('confirm-create-worktree')));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));

    expect(repository.createName, 'wake-fix');
    await tester.scrollUntilVisible(
      find.byKey(const ValueKey('worktree-/data/worktree/project-1/wake-fix')),
      120,
      scrollable: find.byType(Scrollable).first,
    );
    expect(find.text('wake-fix'), findsOneWidget);
    expect(
      find.descendant(
        of: find.byKey(
          const ValueKey('worktree-/data/worktree/project-1/wake-fix'),
        ),
        matching: find.byType(CircularProgressIndicator),
      ),
      findsOneWidget,
    );

    controller.handleEventForTesting(
      EventEnvelope(
        type: 'worktree.ready',
        directory: '/data/worktree/project-1/wake-fix',
        project: 'project-1',
        properties: const {'name': 'wake-fix', 'branch': 'opencode/wake-fix'},
      ),
    );
    await tester.pump();

    expect(find.text('opencode/wake-fix'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('reset explains and confirms every destructive file class', (
    tester,
  ) async {
    final repository = _WorktreeRepository();
    final controller = await _controller(repository);
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(controller));
    await tester.pumpAndSettle();

    await tester.tap(find.byTooltip('Worktree actions'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Reset'));
    await tester.pumpAndSettle();

    expect(find.text('Reset mobile-review?'), findsOneWidget);
    expect(find.textContaining('1 changed file was detected'), findsOneWidget);
    expect(find.textContaining('untracked and ignored files'), findsOneWidget);
    expect(find.textContaining('Submodules are also reset'), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('confirm-reset-worktree')));
    await tester.pumpAndSettle();

    expect(repository.resetCalls, ['/data/worktree/project-1/mobile-review']);
  });

  testWidgets('removing the current worktree switches to primary first', (
    tester,
  ) async {
    final repository = _WorktreeRepository();
    final controller = await _controller(
      repository,
      directory: '/legacy/data/project-1/mobile-review',
    );
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(controller, project: _aliasedProject));
    await tester.pumpAndSettle();

    await tester.tap(find.byTooltip('Worktree actions'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Remove'));
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const ValueKey('remove-worktree-confirmation')),
      'mobile-review',
    );
    await tester.pump();
    await tester.tap(find.byKey(const ValueKey('confirm-remove-worktree')));
    await tester.pumpAndSettle();

    expect(controller.locations, ['/work/app']);
    expect(repository.removeCalls, ['/data/worktree/project-1/mobile-review']);
    expect(find.text('No isolated worktrees yet'), findsOneWidget);
  });
}
