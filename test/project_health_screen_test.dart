import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/project_health_screen.dart';
import 'package:opencode_mobile/ui/screens/workspace_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _HealthRepository implements ProductRepository {
  VersionControlHealth versionControl = const VersionControlHealth(
    branch: 'feature/mobile',
    defaultBranch: 'main',
    changes: [
      VersionControlFile(
        path: 'lib/ui/screens/project_health_screen.dart',
        status: 'modified',
        additions: 24,
        deletions: 3,
      ),
    ],
  );
  List<LanguageServiceHealth> languageServices = const [
    LanguageServiceHealth(
      id: 'dart',
      name: 'Dart analysis server',
      root: '/work/app',
      status: 'connected',
    ),
    LanguageServiceHealth(
      id: 'yaml',
      name: 'YAML language server',
      root: '/work/app',
      status: 'error',
    ),
  ];
  List<FormatterHealth> formatters = const [
    FormatterHealth(name: 'dart format', extensions: ['.dart'], enabled: true),
  ];
  Object? versionControlError;
  Object? gitInitializationError;
  int versionControlCalls = 0;
  int gitInitializationCalls = 0;
  int languageServiceCalls = 0;
  int formatterCalls = 0;

  @override
  void setLocation({String? directory, String? workspace}) {}

  @override
  Future<List<WorkspaceProject>> listProjects() async => [
    const WorkspaceProject(
      id: 'project-1',
      name: 'OpenCode Mobile',
      directory: '/work/app',
      worktrees: [],
      updatedAt: 1,
    ),
  ];

  @override
  Future<List<WorkspaceInfo>> listWorkspaces() async => const [];

  @override
  Future<VersionControlHealth> loadVersionControlHealth() async {
    versionControlCalls += 1;
    if (versionControlError case final error?) throw error;
    return versionControl;
  }

  @override
  Future<void> initializeGitRepository() async {
    gitInitializationCalls += 1;
    if (gitInitializationError case final error?) throw error;
    versionControl = const VersionControlHealth(
      branch: 'master',
      setupState: VersionControlSetupState.git,
      changes: [],
    );
  }

  @override
  Future<List<LanguageServiceHealth>> listLanguageServices() async {
    languageServiceCalls += 1;
    return languageServices;
  }

  @override
  Future<List<FormatterHealth>> listFormatters() async {
    formatterCalls += 1;
    return formatters;
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _WorkspaceLocationController extends ConnectionController {
  _WorkspaceLocationController(super.store);

  final locations = <({String? directory, String? workspace})>[];

  @override
  Future<ProductRepository?> prepareActionRepository() async => repository;

  @override
  Future<void> selectLocation({String? directory, String? workspace}) async {
    if (this.directory == directory && this.workspace == workspace) return;
    locations.add((directory: directory, workspace: workspace));
    this.directory = directory;
    this.workspace = workspace;
    dataRefreshRevision += 1;
    notifyListeners();
  }

  @override
  Future<void> selectInitialLocation({String? directory, String? workspace}) =>
      selectLocation(directory: directory, workspace: workspace);
}

Widget _app(Widget home, {double textScale = 1}) => MaterialApp(
  home: Builder(
    builder: (context) => MediaQuery(
      data: MediaQuery.of(
        context,
      ).copyWith(textScaler: TextScaler.linear(textScale)),
      child: home,
    ),
  ),
);

Future<ConnectionController> _controller(ProductRepository repository) async {
  SharedPreferences.setMockInitialValues({});
  final preferences = await SharedPreferences.getInstance();
  return ConnectionController(ProfileStore(prefs: preferences))
    ..repository = repository
    ..status = StreamStatus.connected;
}

Future<_WorkspaceLocationController> _workspaceController(
  ProductRepository repository,
) async {
  SharedPreferences.setMockInitialValues({});
  final preferences = await SharedPreferences.getInstance();
  return _WorkspaceLocationController(ProfileStore(prefs: preferences))
    ..repository = repository
    ..directory = '/work/app'
    ..status = StreamStatus.connected;
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('project health renders server truth as flat native sections', (
    tester,
  ) async {
    final repository = _HealthRepository();

    await tester.pumpWidget(_app(ProjectHealthScreen(repository: repository)));
    await tester.pumpAndSettle();

    expect(find.text('feature/mobile'), findsOneWidget);
    expect(find.text('Default branch: main'), findsOneWidget);
    expect(find.text('+24'), findsWidgets);
    expect(find.text('-3'), findsWidgets);
    expect(find.text('Dart analysis server'), findsOneWidget);
    expect(find.text('YAML language server'), findsOneWidget);
    expect(find.text('dart format'), findsOneWidget);
    expect(find.byType(Card), findsNothing);
  });

  testWidgets('one unavailable health API does not hide working sections', (
    tester,
  ) async {
    final repository = _HealthRepository()
      ..versionControlError = const ProductException(
        'Version control status is unavailable on this server',
      )
      ..formatters = const [];

    await tester.pumpWidget(_app(ProjectHealthScreen(repository: repository)));
    await tester.pumpAndSettle();

    expect(find.text('Status unavailable'), findsOneWidget);
    expect(
      find.text('Version control status is unavailable on this server'),
      findsOneWidget,
    );
    expect(find.text('Dart analysis server'), findsOneWidget);
    expect(find.text('No formatters configured'), findsOneWidget);
  });

  testWidgets('empty language status explains OpenCode activation truth', (
    tester,
  ) async {
    final repository = _HealthRepository()..languageServices = const [];

    await tester.pumpWidget(_app(ProjectHealthScreen(repository: repository)));
    await tester.pumpAndSettle();

    expect(find.text('No active language services'), findsOneWidget);
    expect(
      find.textContaining('OpenCode activates them while it inspects'),
      findsOneWidget,
    );
    expect(find.textContaining('Open a supported source file'), findsNothing);
  });

  testWidgets('non-Git project initializes only after explicit confirmation', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(320, 640);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final repository = _HealthRepository()
      ..versionControl = const VersionControlHealth(
        setupState: VersionControlSetupState.absent,
        changes: [],
      );

    await tester.pumpWidget(
      _app(ProjectHealthScreen(repository: repository), textScale: 2),
    );
    await tester.pumpAndSettle();

    expect(find.text('Git is not initialized'), findsOneWidget);
    expect(find.text('Working tree is clean'), findsNothing);
    expect(find.text('No uncommitted changes'), findsNothing);

    final initializeButton = find.byKey(
      const ValueKey('initialize-git-repository'),
    );
    await tester.scrollUntilVisible(
      initializeButton,
      80,
      scrollable: find.byType(Scrollable).first,
    );
    await tester.tap(initializeButton);
    await tester.pumpAndSettle();
    expect(find.text('Initialize Git repository?'), findsOneWidget);
    expect(repository.gitInitializationCalls, 0);
    expect(tester.takeException(), isNull);

    await tester.tap(find.text('Cancel'));
    await tester.pumpAndSettle();
    expect(repository.gitInitializationCalls, 0);

    await tester.scrollUntilVisible(
      initializeButton,
      80,
      scrollable: find.byType(Scrollable).first,
    );
    await tester.tap(initializeButton);
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('confirm-git-initialization')));
    await tester.pumpAndSettle();

    expect(repository.gitInitializationCalls, 1);
    expect(find.text('Git repository initialized'), findsOneWidget);
    await tester.scrollUntilVisible(
      find.text('master'),
      -80,
      scrollable: find.byType(Scrollable).first,
    );
    expect(find.text('master'), findsOneWidget);
    expect(find.text('No uncommitted changes'), findsOneWidget);
    expect(find.text('Git is not initialized'), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('failed Git initialization remains visible and retryable', (
    tester,
  ) async {
    final repository = _HealthRepository()
      ..versionControl = const VersionControlHealth(
        setupState: VersionControlSetupState.absent,
        changes: [],
      )
      ..gitInitializationError = const ProductException(
        'The project directory is read-only',
      );

    await tester.pumpWidget(_app(ProjectHealthScreen(repository: repository)));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('initialize-git-repository')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('confirm-git-initialization')));
    await tester.pumpAndSettle();

    expect(repository.gitInitializationCalls, 1);
    expect(find.text('Git is not initialized'), findsOneWidget);
    expect(find.text('Git initialization failed'), findsOneWidget);
    expect(find.textContaining('read-only'), findsOneWidget);
    expect(find.text('Retry'), findsOneWidget);
  });

  testWidgets('Git initialization resolves the repository again at tap time', (
    tester,
  ) async {
    final retainedRepository = _HealthRepository()
      ..versionControl = const VersionControlHealth(
        setupState: VersionControlSetupState.absent,
        changes: [],
      );
    final replacementRepository = _HealthRepository()
      ..versionControl = const VersionControlHealth(
        setupState: VersionControlSetupState.absent,
        changes: [],
      );
    var resolutions = 0;

    await tester.pumpWidget(
      _app(
        ProjectHealthScreen(
          repository: retainedRepository,
          repositoryResolver: () async {
            resolutions += 1;
            return resolutions == 1
                ? retainedRepository
                : replacementRepository;
          },
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('initialize-git-repository')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('confirm-git-initialization')));
    await tester.pumpAndSettle();

    expect(retainedRepository.gitInitializationCalls, 0);
    expect(replacementRepository.gitInitializationCalls, 1);
    expect(find.text('master'), findsOneWidget);
  });

  testWidgets('project health waits for the wake-time repository', (
    tester,
  ) async {
    final retainedRepository = _HealthRepository();
    final replacementRepository = _HealthRepository()
      ..versionControl = const VersionControlHealth(
        branch: 'feature/after-wake',
        changes: [],
      );
    final readyRepository = Completer<ProductRepository?>();

    await tester.pumpWidget(
      _app(
        ProjectHealthScreen(
          repository: retainedRepository,
          repositoryResolver: () => readyRepository.future,
        ),
      ),
    );
    await tester.pump();

    expect(retainedRepository.versionControlCalls, 0);
    expect(replacementRepository.versionControlCalls, 0);

    readyRepository.complete(replacementRepository);
    await tester.pumpAndSettle();

    expect(retainedRepository.versionControlCalls, 0);
    expect(retainedRepository.languageServiceCalls, 0);
    expect(retainedRepository.formatterCalls, 0);
    expect(replacementRepository.versionControlCalls, 1);
    expect(replacementRepository.languageServiceCalls, 1);
    expect(replacementRepository.formatterCalls, 1);
    expect(find.text('feature/after-wake'), findsOneWidget);
  });

  testWidgets('workspace exposes project health without compact overflow', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(320, 640);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final repository = _HealthRepository();
    final controller = await _controller(repository);
    controller.locationNotice =
        'The last project is no longer available. Opened the server workspace.';
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      _app(
        Scaffold(body: WorkspaceScreen(controller: controller)),
        textScale: 2,
      ),
    );
    await tester.pumpAndSettle();

    expect(
      find.byKey(const ValueKey('location-recovery-notice')),
      findsOneWidget,
    );
    expect(find.byKey(const ValueKey('project-health-entry')), findsOneWidget);
    await tester.scrollUntilVisible(
      find.byKey(const ValueKey('search-all-sessions')),
      160,
      scrollable: find.byType(Scrollable).first,
    );
    expect(find.byKey(const ValueKey('search-all-sessions')), findsOneWidget);
    await tester.ensureVisible(
      find.byKey(const ValueKey('project-health-entry')),
    );
    await tester.tap(find.byKey(const ValueKey('project-health-entry')));
    await tester.pumpAndSettle();

    expect(find.byType(ProjectHealthScreen), findsOneWidget);
    expect(find.text('feature/mobile'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('workspace cannot overwrite a session directory refresh', (
    tester,
  ) async {
    final repository = _HealthRepository();
    final controller = await _workspaceController(repository);
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      _app(Scaffold(body: WorkspaceScreen(controller: controller))),
    );
    await tester.pumpAndSettle();

    await controller.selectLocation(directory: '/tmp/runtime-probe');
    await tester.pumpAndSettle();

    expect(controller.directory, '/tmp/runtime-probe');
    expect(controller.locations, [
      (directory: '/tmp/runtime-probe', workspace: null),
    ]);
    expect(
      find.byKey(const ValueKey('active-session-directory')),
      findsOneWidget,
    );
    expect(find.text('runtime-probe'), findsOneWidget);
    expect(find.textContaining('/tmp/runtime-probe'), findsOneWidget);
  });
}
