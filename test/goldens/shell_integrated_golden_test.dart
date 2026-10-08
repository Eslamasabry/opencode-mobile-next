// Golden renders of the real chats-first shell (Conversations, Files,
// Settings in one KitNav, one top bar): the Conversations tab with its list,
// the project filter sheet, New conversation, and the Files tab with the
// Open a project sheet open. 412x915, dark and light, with the app's real
// fonts. The conversation header's project menu is chat_project_menu_*.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/goldens/shell_integrated_golden_test.dart
// and look at every changed image before committing it.
import 'package:clock/clock.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/builtin/builtin_folders.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/domain/chat_feed.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/chats/chats_host.dart';
import 'package:opencode_mobile/ui/screens/home_screen.dart';
import 'package:opencode_mobile/ui/screens/project_folder_actions.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../tool/capture/fixtures.dart' show captureTheme, loadCaptureFonts;
import '../support/chats_fakes.dart';

final _now = DateTime(2026, 10, 3, 12);

class _Api extends OpenCodeApi {
  _Api() : super(baseUrl: 'http://localhost');
}

class _Repository implements ProductRepository {
  @override
  void setLocation({String? directory, String? workspace}) {}

  @override
  Future<List<WorkspaceProject>> listProjects() async => [];

  @override
  Future<List<TerminalProcess>> listTerminals() async => [];

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _Store extends ProfileStore {
  _Store({required super.prefs});

  final profile = ServerProfile(
    id: 'builtin',
    name: 'This phone',
    baseUrl: BuiltinLinux.serverUrl,
    username: BuiltinLinux.serverUsername,
  );

  @override
  List<ServerProfile> get profiles => [profile];

  @override
  String? get activeId => profile.id;
}

class _Linux extends BuiltinLinux {
  @override
  Future<BuiltinLinuxStatus> status() async =>
      const BuiltinLinuxStatus(installed: true, phase: BuiltinLinuxPhase.ready);
}

const _tree = <String, List<(String, bool)>>{
  '/': [('root', true)],
  '/root': [('projects', false)],
  '/root/projects': [('alpha', true), ('beta', false), ('gamma', false)],
};

FakeChatFeedSource _source() => FakeChatFeedSource(
  items: [
    chat(
      'a',
      'Fix the login bug',
      status: ChatStatus.needsYou,
      at: _now.subtract(const Duration(minutes: 2)),
      preview: 'Allow running the tests?',
    ),
    chat(
      'b',
      'Add dark mode',
      dir: '/root/projects/beta',
      project: 'beta',
      git: false,
      status: ChatStatus.running,
      at: _now.subtract(const Duration(minutes: 9)),
      preview: 'Editing lib/ui/theme.dart',
    ),
    chat(
      'c',
      'Explain the build',
      at: _now.subtract(const Duration(hours: 3)),
      preview: 'It runs gradle first, then signs the bundle.',
    ),
  ],
  projects: [
    project('alpha', chats: 2, needs: 1, kind: 'dart'),
    project('beta', chats: 1, running: 1, git: false, kind: 'node'),
  ],
  lastUsed: '/root/projects/alpha',
);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);

  for (final light in [false, true]) {
    final mode = light ? 'light' : 'dark';

    for (final (name, tab, then) in <(String, int, String?)>[
      ('shell_conversations_list', 0, null),
      ('shell_conversations_filter', 0, 'chats-filter-project'),
      ('shell_conversations_new', 0, 'chats-new-chat'),
      ('shell_files_project_sheet', 1, 'project-hub-context'),
    ]) {
      testWidgets('$name · $mode', (tester) async {
        tester.view.physicalSize = const Size(412, 915);
        tester.view.devicePixelRatio = 1;
        addTearDown(tester.view.reset);
        debugPlatformCapabilities = const PlatformCapabilities.android();
        addTearDown(() => debugPlatformCapabilities = null);
        SharedPreferences.setMockInitialValues({});
        final prefs = await SharedPreferences.getInstance();
        final controller = ConnectionController(_Store(prefs: prefs))
          ..api = _Api()
          ..repository = _Repository()
          ..status = StreamStatus.connected
          ..directory = '/root/projects/alpha';
        ProjectFolderActions.builtinLinuxOverride = _Linux();
        ProjectFolderActions.folderListerOverride = (path) async => [
          for (final (n, git) in _tree[path] ?? const <(String, bool)>[])
            FolderEntry(
              name: n,
              path: path == '/' ? '/$n' : '$path/$n',
              isGit: git,
            ),
        ];
        addTearDown(() {
          ProjectFolderActions.builtinLinuxOverride = null;
          ProjectFolderActions.folderListerOverride = null;
          controller.dispose();
        });
        final boundary = GlobalKey();
        await withClock(Clock.fixed(_now), () async {
          await tester.pumpWidget(
            RepaintBoundary(
              key: boundary,
              child: ProviderScope(
                overrides: [
                  connProvider.overrideWithValue(controller),
                  chatsHostProvider.overrideWithValue(FakeChatsHost(_source())),
                ],
                child: MaterialApp(
                  debugShowCheckedModeBanner: false,
                  theme: captureTheme(light: light),
                  supportedLocales: AppLocalizations.supportedLocales,
                  localizationsDelegates:
                      AppLocalizations.localizationsDelegates,
                  home: HomeScreen(initialTab: tab),
                ),
              ),
            ),
          );
          await tester.pump(const Duration(milliseconds: 600));
          if (then != null) {
            await tester.tap(find.byKey(ValueKey(then)));
          }
          await tester.pumpAndSettle();
        });
        expect(tester.takeException(), isNull);
        await expectLater(
          find.byKey(boundary),
          matchesGoldenFile('${name}_$mode.png'),
        );
        await tester.pumpWidget(const SizedBox.shrink());
      }, variant: TargetPlatformVariant.only(TargetPlatform.android));
    }
  }
}
