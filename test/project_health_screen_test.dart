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
    if (versionControlError case final error?) throw error;
    return versionControl;
  }

  @override
  Future<List<LanguageServiceHealth>> listLanguageServices() async =>
      languageServices;

  @override
  Future<List<FormatterHealth>> listFormatters() async => formatters;

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
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

  testWidgets('workspace exposes project health without compact overflow', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(320, 640);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final repository = _HealthRepository();
    final controller = await _controller(repository);
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      _app(
        Scaffold(body: WorkspaceScreen(controller: controller)),
        textScale: 2,
      ),
    );
    await tester.pumpAndSettle();

    expect(find.byKey(const ValueKey('project-health-entry')), findsOneWidget);
    await tester.ensureVisible(
      find.byKey(const ValueKey('project-health-entry')),
    );
    await tester.tap(find.byKey(const ValueKey('project-health-entry')));
    await tester.pumpAndSettle();

    expect(find.byType(ProjectHealthScreen), findsOneWidget);
    expect(find.text('feature/mobile'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}
