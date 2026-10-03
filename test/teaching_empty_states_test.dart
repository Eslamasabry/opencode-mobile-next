// UX plan 5.8, item 3: an empty list says what belongs there and offers the
// one action that fills it; a list that could not load keeps "Try again" and
// never shows the teaching copy, because "nothing yet" would then be a guess.
//
// The AI Team runs list is covered next to its harness, in
// team_controls_test.dart (empty + Start a run) and team_home_test.dart
// (failed, and a host that cannot start runs).
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/screens/activity_screen.dart';
import 'package:opencode_mobile/ui/screens/library_screen.dart';
import 'package:opencode_mobile/ui/screens/review_workspace.dart';
import 'package:opencode_mobile/ui/screens/saved_permissions_screen.dart';
import 'package:opencode_mobile/ui/screens/tools_screen.dart';
import 'package:opencode_mobile/ui/screens/worktrees_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

const _inboxTeaching =
    'Nothing needs you. Approvals and questions from running work appear '
    'here.';
const _changesTeaching = 'Edits the agent makes show up here to review.';
const _worktreesTeaching =
    'A worktree is a separate copy of this project on its own branch, so '
    'parallel work does not mix. Worktrees of this project appear here.';
const _allowedTeaching =
    'When you choose Always allow on an approval in this project, it is '
    'listed here so you can take it back.';
const _skillsTeaching =
    'Skills are reusable instructions the agent can follow. Skills from this '
    'project and this server appear here.';
const _toolsTeaching =
    'Tools the agent can call with this model appear here. This model has '
    'none.';

class _Api extends OpenCodeApi {
  _Api(this._capabilities) : super(baseUrl: 'http://localhost');

  final ServerCapabilities _capabilities;

  @override
  ServerCapabilities get capabilities => _capabilities;

  @override
  Future<List<Session>> sessions() async => const [];

  @override
  Future<Map<String, String>> sessionStatuses() async => const {};

  @override
  Future<List<PermissionRequest>> pendingPermissions() async => const [];

  @override
  Future<List<PermissionRequest>> pendingPermissionsV2() =>
      Future.error(ApiException('V2 unavailable', statusCode: 404));
}

/// One repository for every screen here. A set `*Error` makes that list fail
/// to load; otherwise every list comes back empty.
class _Repository extends ProductRepository {
  Object? worktreesError;
  Object? savedPermissionsError;
  Object? skillsError;
  Object? toolsError;

  @override
  void setLocation({String? directory, String? workspace}) {}

  @override
  Future<List<WorkspaceProject>> listProjects() async => const [_project];

  @override
  Future<List<WorkspaceInfo>> listWorkspaces() async => const [];

  @override
  Future<List<WorktreeInfo>> listWorktrees({
    required String projectDirectory,
    String? projectID,
  }) async {
    if (worktreesError case final error?) throw error;
    return const [];
  }

  @override
  Future<List<SavedPermission>> listSavedPermissions() async {
    if (savedPermissionsError case final error?) throw error;
    return const [];
  }

  @override
  Future<List<SkillInfo>> listSkills() async {
    if (skillsError case final error?) throw error;
    return const [];
  }

  @override
  Future<ExperimentalServerCapabilities> loadExperimentalCapabilities() async =>
      const ExperimentalServerCapabilities(backgroundSubagents: false);

  @override
  Future<List<String>> listCodingToolIDs() async => const [];

  @override
  Future<List<CodingToolInfo>> listCodingTools({
    required String providerID,
    required String modelID,
  }) async {
    if (toolsError case final error?) throw error;
    return const [];
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

const _project = WorkspaceProject(
  id: 'project-1',
  name: 'OpenCode Mobile',
  directory: '/work/app',
  worktrees: [],
  updatedAt: 1,
);

class _Controller extends ConnectionController {
  _Controller(super.store);

  int createCalls = 0;

  @override
  bool get isConnected => status == StreamStatus.connected;

  @override
  int get unknownAttentionProfileCount => 0;

  @override
  ServerProfile get profile =>
      ServerProfile(id: 'server-a', name: 'A', baseUrl: 'http://localhost');

  @override
  Future<Session> createSession() async {
    createCalls++;
    return Session(id: 'created', directory: directory);
  }

  @override
  Future<ServerOperationsGateway?> prepareActionRepository() async =>
      repository;

  // The screens refresh on open; a test that seeds a load failure needs it
  // to still be there when the first frame is built.
  @override
  Future<void> refreshSessions() async {}

  @override
  Future<void> refreshPendingPermissions() async {}

  @override
  Future<void> refreshPendingQuestions() async {}

  @override
  Future<void> refreshPendingForms() async {}
}

Future<_Controller> _controller({
  _Repository? repository,
  ServerCapabilities? capabilities,
}) async {
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  final controller = _Controller(ProfileStore(prefs: prefs))
    ..repository = repository ?? _Repository()
    ..directory = '/work/app'
    ..status = StreamStatus.connected;
  if (capabilities != null) controller.api = _Api(capabilities);
  return controller;
}

Widget _app(
  Widget home, {
  Locale locale = const Locale('en'),
  double textScale = 1,
  Map<String, WidgetBuilder> routes = const {},
}) => MaterialApp(
  theme: AppTheme.light(),
  locale: locale,
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  routes: routes,
  builder: (context, child) => MediaQuery(
    data: MediaQuery.of(
      context,
    ).copyWith(textScaler: TextScaler.linear(textScale)),
    child: child!,
  ),
  home: home,
);

/// Work has rows that animate forever once a conversation is busy, and the
/// screens under test load asynchronously; a few frames settle both.
Future<void> _frames(WidgetTester tester) async {
  for (var i = 0; i < 8; i++) {
    await tester.pump(const Duration(milliseconds: 100));
  }
}

void _phone(WidgetTester tester, [Size size = const Size(400, 800)]) {
  tester.view.devicePixelRatio = 1;
  tester.view.physicalSize = size;
  addTearDown(tester.view.reset);
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const storage = MethodChannel('plugins.it_nomads.com/flutter_secure_storage');
  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(storage, (_) async => null);
  });
  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(storage, null);
  });

  group('Inbox', () {
    testWidgets('empty: says what will appear here', (tester) async {
      _phone(tester);
      final controller = await _controller();
      addTearDown(controller.dispose);
      await tester.pumpWidget(_app(ActivityScreen(controller: controller)));
      await _frames(tester);

      expect(find.byKey(const ValueKey('activity-all-clear')), findsOneWidget);
      expect(find.text(_inboxTeaching), findsOneWidget);
      expect(find.text('Try again'), findsNothing);
    });

    testWidgets('failed: Try again, and no claim that nothing needs you', (
      tester,
    ) async {
      _phone(tester);
      final controller = await _controller()
        ..permissionsError = 'The server did not answer.';
      addTearDown(controller.dispose);
      await tester.pumpWidget(_app(ActivityScreen(controller: controller)));
      await _frames(tester);

      expect(find.text('The server did not answer.'), findsOneWidget);
      expect(find.text('Try again'), findsOneWidget);
      expect(find.text(_inboxTeaching), findsNothing);
      expect(find.byKey(const ValueKey('activity-all-clear')), findsNothing);
    });
  });

  group('Changes', () {
    Widget review(Future<List<FileDiff>> Function() loader) =>
        ReviewWorkspace(loadWorkingTreeDiffs: loader);

    testWidgets('empty: says what shows up here, with no backend name', (
      tester,
    ) async {
      _phone(tester);
      await tester.pumpWidget(_app(review(() async => const [])));
      await tester.pumpAndSettle();

      expect(find.byKey(const Key('review-empty')), findsOneWidget);
      expect(find.text('No changes yet'), findsOneWidget);
      expect(find.text(_changesTeaching), findsOneWidget);
      expect(find.textContaining('OpenCode'), findsNothing);
      expect(find.text('Try again'), findsNothing);
    });

    testWidgets('failed: Try again loads, teaching only once it is empty', (
      tester,
    ) async {
      _phone(tester);
      var attempts = 0;
      await tester.pumpWidget(
        _app(
          review(() async {
            attempts++;
            if (attempts == 1) {
              throw ProductException('The server did not answer.');
            }
            return const [];
          }),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.byKey(const Key('review-error')), findsOneWidget);
      expect(find.byKey(const Key('review-empty')), findsNothing);
      expect(find.text(_changesTeaching), findsNothing);

      await tester.tap(find.text('Try again'));
      await tester.pumpAndSettle();
      expect(attempts, 2);
      expect(find.text(_changesTeaching), findsOneWidget);
    });
  });

  group('Worktrees', () {
    testWidgets('empty: teaches and New worktree opens the create step', (
      tester,
    ) async {
      _phone(tester);
      final controller = await _controller();
      addTearDown(controller.dispose);
      await tester.pumpWidget(
        _app(WorktreesScreen(controller: controller, project: _project)),
      );
      await tester.pumpAndSettle();

      final empty = find.byKey(const ValueKey('no-worktrees'));
      expect(empty, findsOneWidget);
      expect(find.text(_worktreesTeaching), findsOneWidget);
      expect(find.textContaining('OpenCode'), findsNothing);

      // The one create action is now pinned below the list.
      expect(find.text('New worktree'), findsOneWidget);
      await tester.tap(find.byKey(const ValueKey('create-worktree')));
      await tester.pumpAndSettle();
      expect(
        find.byKey(const ValueKey('confirm-create-worktree')),
        findsOneWidget,
      );
    });

    testWidgets('a server that cannot create one gets no button', (
      tester,
    ) async {
      _phone(tester);
      final controller = await _controller(
        capabilities: const ServerCapabilities(worktreeCreate: false),
      );
      addTearDown(controller.dispose);
      await tester.pumpWidget(
        _app(WorktreesScreen(controller: controller, project: _project)),
      );
      await tester.pumpAndSettle();

      expect(find.text(_worktreesTeaching), findsOneWidget);
      expect(find.text('New worktree'), findsNothing);
    });

    testWidgets('failed: Try again, teaching absent', (tester) async {
      _phone(tester);
      final repository = _Repository()
        ..worktreesError = ProductException('The server did not answer.');
      final controller = await _controller(repository: repository);
      addTearDown(controller.dispose);
      await tester.pumpWidget(
        _app(WorktreesScreen(controller: controller, project: _project)),
      );
      await tester.pumpAndSettle();

      expect(find.text('The server did not answer.'), findsOneWidget);
      expect(find.text('Try again'), findsOneWidget);
      expect(find.byKey(const ValueKey('no-worktrees')), findsNothing);
      expect(find.text(_worktreesTeaching), findsNothing);

      repository.worktreesError = null;
      await tester.tap(find.text('Try again'));
      await tester.pumpAndSettle();
      expect(find.text(_worktreesTeaching), findsOneWidget);
    });
  });

  group('Always allowed actions', () {
    testWidgets('empty: says how an entry gets here', (tester) async {
      _phone(tester);
      final controller = await _controller();
      addTearDown(controller.dispose);
      await tester.pumpWidget(
        _app(SavedPermissionsScreen(controller: controller)),
      );
      await tester.pumpAndSettle();

      expect(find.text('No always allowed actions'), findsOneWidget);
      expect(find.text(_allowedTeaching), findsOneWidget);
      expect(find.text('Try again'), findsNothing);
    });

    testWidgets('failed: Try again, teaching absent', (tester) async {
      _phone(tester);
      final repository = _Repository()
        ..savedPermissionsError = ProductException(
          'The server did not answer.',
        );
      final controller = await _controller(repository: repository);
      addTearDown(controller.dispose);
      await tester.pumpWidget(
        _app(SavedPermissionsScreen(controller: controller)),
      );
      await tester.pumpAndSettle();

      expect(find.text('The server did not answer.'), findsOneWidget);
      expect(find.text(_allowedTeaching), findsNothing);
      expect(find.text('No always allowed actions'), findsNothing);

      repository.savedPermissionsError = null;
      await tester.tap(find.text('Try again'));
      await tester.pumpAndSettle();
      expect(find.text(_allowedTeaching), findsOneWidget);
    });
  });

  group('Skills', () {
    testWidgets('empty: says what a skill is, with no backend name', (
      tester,
    ) async {
      _phone(tester);
      final controller = await _controller();
      addTearDown(controller.dispose);
      await tester.pumpWidget(_app(SkillsScreen(controller: controller)));
      await tester.pumpAndSettle();

      expect(find.text('No skills available'), findsOneWidget);
      expect(find.text(_skillsTeaching), findsOneWidget);
      expect(find.textContaining('OpenCode'), findsNothing);
    });

    testWidgets('failed: Try again, teaching absent', (tester) async {
      _phone(tester);
      final repository = _Repository()
        ..skillsError = ProductException('The server did not answer.');
      final controller = await _controller(repository: repository);
      addTearDown(controller.dispose);
      await tester.pumpWidget(_app(SkillsScreen(controller: controller)));
      await tester.pumpAndSettle();

      expect(find.text('The server did not answer.'), findsOneWidget);
      expect(find.text(_skillsTeaching), findsNothing);

      repository.skillsError = null;
      await tester.tap(find.text('Try again'));
      await tester.pumpAndSettle();
      expect(find.text(_skillsTeaching), findsOneWidget);
    });
  });

  group('Tools', () {
    Future<_Controller> withModel(_Repository repository) async {
      final controller = await _controller(repository: repository);
      controller.selectedModel = ModelRef(
        providerID: 'openai',
        modelID: 'gpt-5.6-sol',
      );
      controller.catalog = const CatalogSnapshot(
        providers: [],
        models: [
          CatalogModel(
            id: 'gpt-5.6-sol',
            providerID: 'openai',
            name: 'GPT-5.6 Sol',
            enabled: true,
            status: 'active',
            contextLimit: 400000,
            outputLimit: 128000,
            reasoning: true,
            attachments: true,
            tools: true,
            variants: [],
          ),
        ],
        agents: [],
      );
      return controller;
    }

    testWidgets('empty: says what appears here, with no backend name', (
      tester,
    ) async {
      _phone(tester);
      final controller = await withModel(_Repository());
      addTearDown(controller.dispose);
      await tester.pumpWidget(_app(ToolsScreen(controller: controller)));
      await tester.pumpAndSettle();

      expect(find.text('No tools for this model'), findsOneWidget);
      expect(find.text(_toolsTeaching), findsOneWidget);
      expect(find.textContaining('OpenCode returned'), findsNothing);
    });

    testWidgets('failed: Try again, teaching absent', (tester) async {
      _phone(tester);
      final repository = _Repository()
        ..toolsError = ProductException('The server did not answer.');
      final controller = await withModel(repository);
      addTearDown(controller.dispose);
      await tester.pumpWidget(_app(ToolsScreen(controller: controller)));
      await tester.pumpAndSettle();

      expect(find.text('The server did not answer.'), findsOneWidget);
      expect(find.text(_toolsTeaching), findsNothing);

      repository.toolsError = null;
      await tester.tap(find.text('Try again'));
      await tester.pumpAndSettle();
      expect(find.text(_toolsTeaching), findsOneWidget);
    });
  });

  group('layout at 320 dp and 2.5x text', () {
    const phone = Size(320, 640);

    for (final locale in const [Locale('en'), Locale('ar')]) {
      final code = locale.languageCode;
      final copy = lookupAppLocalizations(locale);

      testWidgets('Inbox empty state, $code', (tester) async {
        _phone(tester, phone);
        final controller = await _controller();
        addTearDown(controller.dispose);
        await tester.pumpWidget(
          _app(
            ActivityScreen(controller: controller),
            locale: locale,
            textScale: AppTheme.maxTextScale,
          ),
        );
        await _frames(tester);

        expect(find.text(copy.emptyTeachInboxMessage), findsOneWidget);
        expect(tester.takeException(), isNull);
      });

      testWidgets('Changes empty state, $code', (tester) async {
        _phone(tester, phone);
        await tester.pumpWidget(
          _app(
            ReviewWorkspace(loadWorkingTreeDiffs: () async => const []),
            locale: locale,
            textScale: AppTheme.maxTextScale,
          ),
        );
        await tester.pumpAndSettle();

        expect(find.text(copy.emptyTeachChangesTitle), findsOneWidget);
        expect(find.text(copy.emptyTeachChangesMessage), findsOneWidget);
        expect(tester.takeException(), isNull);
      });
    }
  });
}
