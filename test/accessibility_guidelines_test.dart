import 'support/complete_message_history.dart';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/api2/models.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/screens/activity_screen.dart';
import 'package:opencode_mobile/ui/screens/chat/permission_sheet.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/ui/screens/home_screen.dart';
import 'package:opencode_mobile/ui/screens/library_screen.dart';
import 'package:opencode_mobile/ui/screens/servers_screen.dart';
import 'package:opencode_mobile/ui/screens/settings_screen.dart';
import 'package:opencode_mobile/ui/screens/workspace_screen.dart';
import 'package:opencode_mobile/ui/widgets/form_renderer.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:opencode_mobile/ui/screens/manage_project_screen.dart';

/// Audit rec UX-007: an automated gate for Android tap targets, labelled
/// tap targets, and text contrast across the critical flows, so a shrinking
/// affordance or an unlabelled icon button fails here rather than on a
/// device. Runs in both brightnesses.
class _Api extends OpenCodeApi with CompleteMessageHistory {
  _Api() : super(baseUrl: 'http://localhost');

  @override
  Future<List<Session>> sessions() async => [];

  @override
  Future<Map<String, String>> sessionStatuses() async => const {};

  @override
  Future<List<MessageWithParts>> messages(String id) async => [];

  @override
  Future<Session> session(String id) async => Session(id: id);

  @override
  Future<List<FileNode>> listFiles([String path = '']) async => [];

  @override
  Future<Health> health() async => Health(healthy: true, version: '1.18.23');
}

class _Repository implements ProductRepository {
  _Repository({this.projects = const []});

  final List<WorkspaceProject> projects;

  @override
  void setLocation({String? directory, String? workspace}) {}

  @override
  Future<List<WorkspaceProject>> listProjects() async => [...projects];

  @override
  Future<List<WorkspaceInfo>> listWorkspaces() async => [];

  @override
  Future<List<TerminalProcess>> listTerminals() async => [];

  @override
  Future<CatalogSnapshot> loadCatalog() async =>
      const CatalogSnapshot(providers: [], models: [], agents: []);

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

Future<ConnectionController> _controller({
  bool withProfile = true,
  bool withRepository = true,
  List<WorkspaceProject> projects = const [],
}) async {
  SharedPreferences.setMockInitialValues({
    if (withProfile) ...{
      'oc.profiles': jsonEncode([
        {
          'id': 'profile-1',
          'name': 'Workstation',
          'baseUrl': 'http://localhost:4096',
          'username': '',
        },
      ]),
      'oc.activeProfile': 'profile-1',
    },
  });
  final prefs = await SharedPreferences.getInstance();
  final store = ProfileStore(prefs: prefs);
  await store.load();
  final controller = ConnectionController(store)
    ..api = _Api()
    ..status = StreamStatus.connected;
  if (withRepository) controller.repository = _Repository(projects: projects);
  return controller;
}

Widget _app(Widget home, Brightness brightness) => MaterialApp(
  theme: brightness == Brightness.dark ? AppTheme.dark() : AppTheme.light(),
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  home: home,
);

Widget _scoped(ConnectionController conn, Widget home, Brightness brightness) =>
    ProviderScope(
      overrides: [
        connProvider.overrideWithValue(conn),
        bootstrapProvider.overrideWithValue(AppBootstrap(conn.store)),
      ],
      child: _app(home, brightness),
    );

Future<void> _settle(WidgetTester tester) async {
  for (var i = 0; i < 6; i++) {
    await tester.pump(const Duration(milliseconds: 120));
  }
}

/// The three guidelines the audit names, checked together.
Future<void> _expectAccessible(WidgetTester tester) async {
  final handle = tester.ensureSemantics();
  await expectLater(tester, meetsGuideline(androidTapTargetGuideline));
  await expectLater(tester, meetsGuideline(labeledTapTargetGuideline));
  await expectLater(tester, meetsGuideline(textContrastGuideline));
  handle.dispose();
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (_) async => null,
        );
  });

  for (final brightness in Brightness.values) {
    final label = brightness == Brightness.dark ? 'dark' : 'light';

    testWidgets('$label: chat meets the accessibility guidelines', (
      tester,
    ) async {
      final conn = await _controller(withRepository: false);
      addTearDown(conn.dispose);
      await tester.pumpWidget(
        _scoped(conn, const ChatScreen(sessionID: 'session-1'), brightness),
      );
      await _settle(tester);
      await _expectAccessible(tester);
    });

    testWidgets('$label: the busy chat composer meets the guidelines', (
      tester,
    ) async {
      final conn = await _controller(withRepository: false);
      addTearDown(conn.dispose);
      await tester.pumpWidget(
        _scoped(conn, const ChatScreen(sessionID: 'session-1'), brightness),
      );
      await _settle(tester);
      conn.busySessions = {'session-1'};
      conn.notifyListeners();
      await _settle(tester);
      await _expectAccessible(tester);
    });

    testWidgets('$label: the home shell meets the guidelines', (tester) async {
      final conn = await _controller();
      addTearDown(conn.dispose);
      await tester.pumpWidget(_scoped(conn, const HomeScreen(), brightness));
      await _settle(tester);
      await _expectAccessible(tester);
    });

    testWidgets('$label: workspace meets the guidelines', (tester) async {
      final conn = await _controller();
      addTearDown(conn.dispose);
      await tester.pumpWidget(
        _scoped(
          conn,
          Scaffold(body: WorkspaceScreen(controller: conn)),
          brightness,
        ),
      );
      await _settle(tester);
      await _expectAccessible(tester);
    });

    testWidgets('$label: Activity meets the guidelines', (tester) async {
      final conn = await _controller();
      addTearDown(conn.dispose);
      conn.sessionsById = {
        'ses_run': Session(
          id: 'ses_run',
          title: 'Build feature',
          directory: '/work/oc_app',
          time: SessionTime(
            created: 0,
            updated: DateTime.now().millisecondsSinceEpoch,
          ),
        ),
      };
      conn.busySessions = {'ses_run'};
      conn.permissions = {
        'per_1': PermissionRequest(
          id: 'per_1',
          sessionID: 'ses_run',
          permission: 'bash',
          patterns: const ['git push origin main'],
        ),
      };
      conn.questions = {
        'q_1': const PendingQuestion(
          id: 'q_1',
          sessionID: 'ses_run',
          prompts: [
            QuestionPrompt(
              title: 'Direction',
              question: 'Proceed with the release?',
              multiple: false,
              custom: true,
              choices: [],
            ),
          ],
        ),
      };
      await tester.pumpWidget(
        _scoped(
          conn,
          Scaffold(body: ActivityScreen(controller: conn, embedded: true)),
          brightness,
        ),
      );
      await _settle(tester);
      await _expectAccessible(tester);
    });

    testWidgets('$label: the More hub meets the guidelines', (tester) async {
      final conn = await _controller();
      addTearDown(conn.dispose);
      await tester.pumpWidget(
        _scoped(
          conn,
          Scaffold(body: LibraryScreen(controller: conn)),
          brightness,
        ),
      );
      await _settle(tester);
      await _expectAccessible(tester);
    });

    testWidgets('$label: the session-first workspace meets the guidelines', (
      tester,
    ) async {
      final conn = await _controller(projects: const [_project]);
      addTearDown(conn.dispose);
      await tester.pumpWidget(
        _scoped(
          conn,
          Scaffold(body: WorkspaceScreen(controller: conn)),
          brightness,
        ),
      );
      await _settle(tester);
      expect(find.byKey(const ValueKey('current-project-entry')), findsOneWidget);
      expect(find.byKey(const ValueKey('manage-project-entry')), findsOneWidget);
      await _expectAccessible(tester);
    });

    testWidgets('$label: manage project meets the guidelines', (tester) async {
      final conn = await _controller(projects: const [_project]);
      addTearDown(conn.dispose);
      await tester.pumpWidget(
        _scoped(
          conn,
          ManageProjectScreen(controller: conn, project: _project),
          brightness,
        ),
      );
      await _settle(tester);
      await _expectAccessible(tester);
    });

    testWidgets('$label: the More hub meets the guidelines', (tester) async {
      final conn = await _controller();
      addTearDown(conn.dispose);
      await tester.pumpWidget(
        _scoped(
          conn,
          Scaffold(body: LibraryScreen(controller: conn)),
          brightness,
        ),
      );
      await _settle(tester);
      await _expectAccessible(tester);
    });

    testWidgets('$label: settings meets the guidelines', (tester) async {
      final conn = await _controller();
      addTearDown(conn.dispose);
      await tester.pumpWidget(
        _scoped(conn, SettingsScreen(controller: conn), brightness),
      );
      await _settle(tester);
      await _expectAccessible(tester);
    });

    testWidgets('$label: servers first run meets the guidelines', (
      tester,
    ) async {
      final conn = await _controller(withProfile: false);
      addTearDown(conn.dispose);
      await tester.pumpWidget(_scoped(conn, const ServersScreen(), brightness));
      await _settle(tester);
      await _expectAccessible(tester);
    });

    testWidgets('$label: the permission sheet meets the guidelines', (
      tester,
    ) async {
      await tester.pumpWidget(
        _app(
          Scaffold(
            body: PermissionSheet(
              permission: PermissionRequest(
                id: 'per_1',
                sessionID: 'ses_1',
                permission: 'bash',
                patterns: const ['git push origin main'],
                always: const [],
                message: 'Pushing the release branch',
              ),
              onReply: (reply, {String? message}) async {},
              supportsRejectMessage: true,
            ),
          ),
          brightness,
        ),
      );
      await _settle(tester);
      await _expectAccessible(tester);
    });

    testWidgets('$label: the form renderer meets the guidelines', (
      tester,
    ) async {
      await tester.pumpWidget(
        _app(
          Scaffold(
            body: FormRenderer(
              form: Api2FormInfo(
                id: 'frm_1',
                sessionID: 'ses_1',
                title: 'Connect to Sentry',
                fields: [
                  Api2FormField(
                    key: 'org',
                    type: Api2FormFieldType.string,
                    title: 'Organization slug',
                    required: true,
                  ),
                  Api2FormField(
                    key: 'notify',
                    type: Api2FormFieldType.boolean,
                    title: 'Notify this session',
                  ),
                ],
              ),
              onSubmit: (_) async {},
              onCancel: () async {},
            ),
          ),
          brightness,
        ),
      );
      await _settle(tester);
      await _expectAccessible(tester);
    });
  }
}
