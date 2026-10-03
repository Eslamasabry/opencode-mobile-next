// Revamp slice P6.6a "Defaults instead of questions": the app picks what is
// knowable instead of asking (the only or most recent project, "my-app" on
// first run, the server's default model, the review view with changes),
// says so once where it matters, and the thing's own place keeps the way to
// change it.
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/interaction_defaults.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/screens/project_folder_actions.dart';
import 'package:opencode_mobile/ui/screens/review_workspace.dart';
import 'package:opencode_mobile/ui/screens/settings_screen.dart';
import 'package:opencode_mobile/ui/widgets/default_notices.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../tool/capture/fixtures.dart'
    show CaptureApi, CaptureController, SeededProfileStore;

final _en = lookupAppLocalizations(const Locale('en'));

FileDiff _diff(String file) => FileDiff(
  file: file,
  patch: '@@ -1 +1 @@\n-old\n+$file',
  additions: 1,
  deletions: 1,
);

WorkspaceProject _project(String id, String directory, {int updatedAt = 1}) =>
    WorkspaceProject(
      id: id,
      name: directory.split('/').last,
      directory: directory,
      worktrees: const [],
      updatedAt: updatedAt,
    );

CatalogModel _model(String provider, String id, String name) => CatalogModel(
  id: id,
  providerID: provider,
  name: name,
  enabled: true,
  status: 'active',
  contextLimit: 200000,
  outputLimit: 8000,
  reasoning: true,
  attachments: true,
  tools: true,
  variants: const [],
);

Future<void> _pumpReview(
  WidgetTester tester, {
  required List<FileDiff> session,
  required List<FileDiff> workingTree,
  List<FileDiff>? branch,
  ReviewDiffScope? initialScope,
  String? profileId = 'phone',
}) async {
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    MaterialApp(
      theme: AppTheme.dark(),
      home: ReviewWorkspace(
        initialScope: initialScope,
        profileId: profileId,
        loadDiffs: () async => session,
        loadWorkingTreeDiffs: () async => workingTree,
        loadBranchDiffs: branch == null ? null : () async => branch,
      ),
    ),
  );
  await tester.pumpAndSettle();
}

Finder _header(String path) => find.byKey(ValueKey('review-file-header-$path'));

final _reviewNotice = find.byKey(const Key('review-default-scope'));

void main() {
  setUp(() {
    ReviewWorkspace.clearCache();
    resetDefaultNoticesForTest();
    // The saved server row: defaults are remembered (and said) only for a
    // server that is saved, the admission profile deletion drains
    // (7c6d009c). The seeded stores below read their profiles from memory.
    SharedPreferences.setMockInitialValues({
      'oc.profiles': jsonEncode([
        {'id': 'phone', 'name': 'Laptop', 'baseUrl': 'http://127.0.0.1:4096'},
      ]),
    });
  });

  group('review opens the view that has changes', () {
    testWidgets('an empty conversation gives way to Uncommitted, said once', (
      tester,
    ) async {
      await _pumpReview(
        tester,
        session: const [],
        workingTree: [_diff('lib/a.dart')],
      );
      expect(_header('lib/a.dart'), findsOneWidget);
      expect(find.byKey(const Key('review-empty')), findsNothing);
      expect(
        find.text(_en.defaultReviewScopeNotice(_en.readerUiWorkingTree)),
        findsOneWidget,
      );
      // The picker still offers every view, with Uncommitted chosen; its
      // meaning is said only when a view is empty.
      expect(find.byKey(const Key('review-scope-picker')), findsOneWidget);
      expect(find.text(_en.readerUiWorkingScopeHint), findsNothing);

      // Opened again for the same server: the same default, not said again.
      ReviewWorkspace.clearCache();
      await tester.pumpWidget(const SizedBox.shrink());
      await _pumpReview(
        tester,
        session: const [],
        workingTree: [_diff('lib/a.dart')],
      );
      expect(_header('lib/a.dart'), findsOneWidget);
      expect(_reviewNotice, findsNothing);
    });

    testWidgets('the whole branch when it is the only view with changes', (
      tester,
    ) async {
      await _pumpReview(
        tester,
        session: const [],
        workingTree: const [],
        branch: [_diff('lib/b.dart')],
      );
      expect(_header('lib/b.dart'), findsOneWidget);
      expect(
        find.text(_en.defaultReviewScopeNotice(_en.readerUiBranch)),
        findsOneWidget,
      );
    });

    testWidgets('a conversation with changes stays first, nothing said', (
      tester,
    ) async {
      await _pumpReview(
        tester,
        session: [_diff('lib/mine.dart')],
        workingTree: [_diff('lib/other.dart')],
      );
      expect(_header('lib/mine.dart'), findsOneWidget);
      expect(_reviewNotice, findsNothing);
    });

    testWidgets('a view the caller names is kept even when empty', (
      tester,
    ) async {
      await _pumpReview(
        tester,
        initialScope: ReviewDiffScope.session,
        session: const [],
        workingTree: [_diff('lib/a.dart')],
      );
      expect(find.byKey(const Key('review-empty')), findsOneWidget);
      expect(_reviewNotice, findsNothing);
    });

    testWidgets('nothing changed anywhere: the empty state, no notice', (
      tester,
    ) async {
      await _pumpReview(tester, session: const [], workingTree: const []);
      expect(find.byKey(const Key('review-empty')), findsOneWidget);
      expect(_reviewNotice, findsNothing);
    });
  });

  group('"my-app" on first run', () {
    test('proposed only when the server has no project of its own', () {
      expect(ProjectFolderActions.suggestedName(null), isNull);
      expect(ProjectFolderActions.suggestedName(const []), 'my-app');
      // The server's root is not a project of the person's.
      expect(
        ProjectFolderActions.suggestedName([_project('root', '/')]),
        'my-app',
      );
      expect(
        ProjectFolderActions.suggestedName([
          _project('p1', '/root/projects/shop'),
        ]),
        isNull,
      );
    });
  });

  group('the model is the server default by name', () {
    Future<CaptureController> connected({bool explicit = false}) async {
      final prefs = await SharedPreferences.getInstance();
      final store = SeededProfileStore(
        prefs: prefs,
        seeded: [
          ServerProfile(id: 'phone', name: 'Laptop', baseUrl: 'http://x:1'),
        ],
      );
      if (explicit) {
        await store.setModel('phone', 'openai', 'gpt', explicit: true);
      }
      return CaptureController(store)
        ..catalog = CatalogSnapshot(
          providers: const [],
          models: [
            _model('anthropic', 'sonnet', 'Claude Sonnet 4'),
            _model('openai', 'gpt', 'GPT-6'),
          ],
          agents: const [],
        )
        ..selectedModel = explicit
            ? ModelRef(providerID: 'openai', modelID: 'gpt')
            : ModelRef(providerID: 'anthropic', modelID: 'sonnet');
    }

    test('an unpicked model is the server default, named', () async {
      final controller = await connected();
      final choice = modelDefaultOf(controller);
      expect(choice.reason, DefaultReason.serverDefault);
      expect(choice.label, 'Claude Sonnet 4');
      expect(choice.canChange, isTrue);
      expect(
        await claimDefaultNotice(
          kind: DefaultKind.model,
          choice: choice,
          profileId: 'phone',
        ),
        'Claude Sonnet 4',
      );
      // Once per server.
      expect(
        await claimDefaultNotice(
          kind: DefaultKind.model,
          choice: choice,
          profileId: 'phone',
        ),
        isNull,
      );
    });

    testWidgets('Settings shows the default by name and why', (tester) async {
      tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
        const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
        (call) async => call.method == 'readAll' ? <String, String>{} : null,
      );
      final controller = await connected();
      controller
        ..api = CaptureApi()
        ..status = StreamStatus.connected;
      await tester.pumpWidget(
        MaterialApp(
          theme: AppTheme.light(),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: SettingsScreen(controller: controller),
        ),
      );
      await tester.pumpAndSettle();
      final row = find.byKey(const ValueKey('settings-model-and-mode'));
      expect(row, findsOneWidget);
      expect(
        find.descendant(of: row, matching: find.text('Claude Sonnet 4')),
        findsOneWidget,
      );
      expect(
        find.descendant(of: row, matching: find.text(_en.modelServerDefault)),
        findsOneWidget,
      );
    });

    test('a model the person picked is theirs: nothing to say', () async {
      final controller = await connected(explicit: true);
      final choice = modelDefaultOf(controller);
      expect(choice.reason, DefaultReason.explicitChoice);
      expect(
        await claimDefaultNotice(
          kind: DefaultKind.model,
          choice: choice,
          profileId: 'phone',
        ),
        isNull,
      );
    });
  });
}
