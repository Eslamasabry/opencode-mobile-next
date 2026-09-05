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
import 'package:opencode_mobile/ui/screens/chat/permission_sheet.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/ui/screens/home_screen.dart';
import 'package:opencode_mobile/ui/screens/library_screen.dart';
import 'package:opencode_mobile/ui/screens/servers_screen.dart';
import 'package:opencode_mobile/ui/screens/settings_screen.dart';
import 'package:opencode_mobile/ui/screens/workspace_screen.dart';
import 'package:opencode_mobile/ui/widgets/form_renderer.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// UX-P0-05 / audit rec #5: the global clamp no longer caps accessibility at
/// 2.0x, so the critical flows have to survive the new 2.5x ceiling. Each
/// case renders a flagship surface on a small phone at 2.5x and fails on any
/// layout exception — RenderFlex overflow included.
const _textScale = AppTheme.maxTextScale;

/// A 360x740 logical phone, the narrowest shape the product supports.
const _phone = Size(360, 740);

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
  @override
  void setLocation({String? directory, String? workspace}) {}

  @override
  Future<List<WorkspaceProject>> listProjects() async => [];

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

Future<ConnectionController> _controller({
  bool withProfile = true,
  bool withRepository = true,
}) async {
  SharedPreferences.setMockInitialValues({
    if (withProfile) ...{
      'oc.profiles': jsonEncode([
        {
          'id': 'profile-1',
          'name': 'Workstation on the LAN',
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
  if (withRepository) controller.repository = _Repository();
  return controller;
}

/// Pumps [child] at 2.5x on a 360dp phone and returns once settled.
Future<void> _pumpScaled(WidgetTester tester, Widget child) async {
  tester.view.physicalSize = _phone * tester.view.devicePixelRatio;
  tester.view.devicePixelRatio = 1;
  tester.view.physicalSize = _phone;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    MediaQuery(
      data: const MediaQueryData(
        size: _phone,
        textScaler: TextScaler.linear(_textScale),
      ),
      child: child,
    ),
  );
  // Localization delegates resolve asynchronously and several screens load
  // through a future, so give the tree a handful of frames to reach its
  // steady state before the layout is judged.
  for (var i = 0; i < 6; i++) {
    await tester.pump(const Duration(milliseconds: 120));
  }
}

Widget _app(Widget home) => MaterialApp(
  theme: AppTheme.light(),
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  home: home,
);

Widget _scoped(ConnectionController conn, Widget home) => ProviderScope(
  overrides: [
    connProvider.overrideWithValue(conn),
    bootstrapProvider.overrideWithValue(AppBootstrap(conn.store)),
  ],
  child: _app(home),
);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    // ProfileStore reads passwords through flutter_secure_storage, whose
    // unmocked channel never answers inside testWidgets.
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (_) async => null,
        );
  });

  testWidgets('chat transcript and composer lay out at 2.5x', (tester) async {
    final conn = await _controller(withRepository: false);
    addTearDown(conn.dispose);
    await _pumpScaled(
      tester,
      _scoped(conn, const ChatScreen(sessionID: 'session-1')),
    );

    expect(tester.takeException(), isNull);
    expect(find.byKey(const Key('chat-composer-field')), findsOneWidget);
  });

  testWidgets('the busy composer lays out at 2.5x', (tester) async {
    final conn = await _controller(withRepository: false);
    addTearDown(conn.dispose);
    conn.busySessions = {'session-1'};
    await _pumpScaled(
      tester,
      _scoped(conn, const ChatScreen(sessionID: 'session-1')),
    );

    expect(tester.takeException(), isNull);
    expect(find.byKey(const Key('chat-composer-field')), findsOneWidget);
  });

  testWidgets('workspace lays out at 2.5x', (tester) async {
    final conn = await _controller();
    addTearDown(conn.dispose);
    await _pumpScaled(tester, _scoped(conn, WorkspaceScreen(controller: conn)));

    expect(tester.takeException(), isNull);
  });

  testWidgets('the home shell lays out at 2.5x', (tester) async {
    final conn = await _controller();
    addTearDown(conn.dispose);
    await _pumpScaled(tester, _scoped(conn, const HomeScreen()));

    expect(tester.takeException(), isNull);
  });

  testWidgets('the More hub lays out at 2.5x', (tester) async {
    final conn = await _controller();
    addTearDown(conn.dispose);
    await _pumpScaled(
      tester,
      _scoped(conn, Scaffold(body: LibraryScreen(controller: conn))),
    );

    expect(tester.takeException(), isNull);
  });

  testWidgets('settings lays out at 2.5x', (tester) async {
    final conn = await _controller();
    addTearDown(conn.dispose);
    await _pumpScaled(tester, _scoped(conn, SettingsScreen(controller: conn)));

    expect(tester.takeException(), isNull);
  });

  testWidgets('the servers first-run screen lays out at 2.5x', (tester) async {
    final conn = await _controller(withProfile: false);
    addTearDown(conn.dispose);
    await _pumpScaled(tester, _scoped(conn, const ServersScreen()));

    expect(tester.takeException(), isNull);
  });

  testWidgets('the permission sheet lays out at 2.5x', (tester) async {
    await _pumpScaled(
      tester,
      _app(
        Scaffold(
          body: PermissionSheet(
            permission: PermissionRequest(
              id: 'per_1',
              sessionID: 'ses_1',
              permission: 'bash',
              patterns: const [
                'git push origin main --force-with-lease --no-verify',
              ],
              always: const [],
              message: 'Pushing the release branch to the shared remote',
              tool: null,
            ),
            onReply: (reply, {String? message}) async {},
            supportsRejectMessage: true,
          ),
        ),
      ),
    );

    expect(find.byKey(const Key('permission-sheet')), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('the form renderer lays out at 2.5x', (tester) async {
    await _pumpScaled(
      tester,
      _app(
        Scaffold(
          body: FormRenderer(
            form: Api2FormInfo(
              id: 'frm_1',
              sessionID: 'ses_1',
              title: 'Connect this workspace to Sentry',
              fields: [
                Api2FormField(
                  key: 'org',
                  type: Api2FormFieldType.string,
                  title: 'Organization slug',
                  description:
                      'The slug shown in your Sentry organization settings.',
                  required: true,
                ),
                Api2FormField(
                  key: 'retention',
                  type: Api2FormFieldType.integer,
                  title: 'Retention in days',
                  required: true,
                ),
                Api2FormField(
                  key: 'env',
                  type: Api2FormFieldType.multiselect,
                  title: 'Environments to watch',
                  options: [
                    Api2FormOption(value: 'prod', label: 'Production'),
                    Api2FormOption(value: 'stage', label: 'Staging'),
                  ],
                ),
                Api2FormField(
                  key: 'notify',
                  type: Api2FormFieldType.boolean,
                  title: 'Notify this session on new issues',
                ),
              ],
            ),
            onSubmit: (_) async {},
            onCancel: () async {},
          ),
        ),
      ),
    );

    expect(find.byKey(const Key('form-submit')), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}
