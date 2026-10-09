// Shared by the files / changes / worktrees coverage ratchets: fakes that
// hand the screens what the real gateways parsed from wire payloads.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/ui/screens/files_screen.dart';
import 'package:opencode_mobile/ui/screens/managed_workspaces_screen.dart';
import 'package:opencode_mobile/ui/screens/project_health_screen.dart';
import 'package:opencode_mobile/ui/screens/review_workspace.dart';
import 'package:opencode_mobile/ui/screens/staged_revert_screen.dart';
import 'package:opencode_mobile/ui/screens/worktrees_screen.dart';

import 'lists_support.dart';
import 'paseo_coverage_support.dart' show screenText, writeCasePng;

/// The file transport the Files page reads from.
class FilesApi extends ListsApi {
  FilesApi([super.caps]);

  List<FileNode> nodes = const [];
  Map<String, FileContent> contents = {};
  List<String> found = const [];
  List<FileDiff> sessionDiffs = const [];

  @override
  Future<List<FileNode>> listFiles([String path = '']) async => nodes;

  @override
  Future<FileContent> fileContent(String path) async =>
      contents[path] ?? const FileContent('');

  @override
  Future<List<String>> findFile(String query) async => found;

  @override
  Future<List<FileDiff>> diff(String id) async => sessionDiffs;
}

/// The repository the changes, worktree and workspace pages read from.
class FilesRepository extends ListsRepository implements StagedRevertGateway {
  SessionRevert? staged;

  @override
  Future<String?> sessionRevertPrompt(String id, String messageID) async =>
      'Update the settings screen';

  @override
  Future<SessionRevert> stageSessionRevert(
    String id,
    String messageID, {
    required bool applyFiles,
  }) async => staged!;

  @override
  Future<void> clearSessionRevert(String id) async =>
      calls.add('clearSessionRevert');

  @override
  Future<void> commitSessionRevert(String id) async =>
      calls.add('commitSessionRevert');

  List<VersionControlFile> statuses = const [];
  List<FileDiff> workingDiffs = const [];
  VersionControlHealth health = const VersionControlHealth(changes: []);
  List<WorkspaceSymbol> symbols = const [];
  List<WorktreeInfo> worktrees = const [];
  WorktreeInfo? created;
  List<WorkspaceInfo> managed = const [];
  List<WorkspaceAdapterInfo> adapters = const [];

  @override
  Future<List<VersionControlFile>> listFileStatuses() async => statuses;

  @override
  Future<List<VersionControlFile>> listWorktreeFileStatuses(
    String directory,
  ) async => statuses;

  @override
  Future<List<FileDiff>> listVcsDiffs(VcsDiffMode mode) async => workingDiffs;

  @override
  Future<VersionControlHealth> loadVersionControlHealth() async => health;

  @override
  Future<List<LanguageServiceHealth>> listLanguageServices() async => const [];

  @override
  Future<List<FormatterHealth>> listFormatters() async => const [];

  @override
  Future<List<WorkspaceSymbol>> findWorkspaceSymbols(String query) async =>
      symbols;

  @override
  Future<List<WorktreeInfo>> listWorktrees({
    required String projectDirectory,
    String? projectID,
  }) async => worktrees;

  @override
  Future<WorktreeInfo> createWorktree({
    required String projectDirectory,
    String? name,
  }) async {
    calls.add('createWorktree');
    return created ??
        WorktreeInfo(name: name ?? 'new', directory: '/work/new-copy');
  }

  @override
  Future<void> resetWorktree({
    required String projectDirectory,
    required String directory,
  }) async => calls.add('resetWorktree');

  @override
  Future<void> removeWorktree({
    required String projectDirectory,
    required String directory,
  }) async => calls.add('removeWorktree');

  @override
  Future<List<WorkspaceInfo>> listManagedWorkspaces({
    required String projectDirectory,
  }) async => managed;

  @override
  Future<List<WorkspaceAdapterInfo>> listWorkspaceAdapters({
    required String projectDirectory,
  }) async => adapters;

  @override
  Future<void> syncWorkspaceList({required String projectDirectory}) async =>
      calls.add('syncWorkspaceList');

  @override
  Future<void> removeManagedWorkspace({
    required String projectDirectory,
    required String id,
  }) async => calls.add('removeManagedWorkspace');
}

const shopfront = WorkspaceProject(
  id: 'prj_shopfront',
  name: 'Shopfront',
  directory: '/work/shopfront',
  worktrees: [],
  updatedAt: 1,
);

extension FilesScreens on ListsScreens {
  Future<void> filesPage({Future<void> Function()? then}) => show(
    Scaffold(body: FilesScreen(controller: controller)),
    then: then,
  );

  Future<void> review({
    List<FileDiff> session = const [],
    List<FileDiff> working = const [],
  }) => show(
    ReviewWorkspace(
      loadDiffs: session.isEmpty ? null : () async => session,
      loadWorkingTreeDiffs: working.isEmpty ? null : () async => working,
      initialScope: session.isEmpty
          ? ReviewDiffScope.workingTree
          : ReviewDiffScope.session,
    ),
  );

  Future<void> worktreesPage({Future<void> Function()? then}) => show(
    WorktreesScreen(controller: controller, project: shopfront),
    then: then,
  );

  Future<void> healthPage() =>
      show(ProjectHealthScreen(repository: controller.operations));

  Future<void> undoPage(String sessionID) =>
      show(StagedRevertScreen(controller: controller, sessionID: sessionID));

  Future<void> workspacesPage() =>
      show(ManagedWorkspacesScreen(controller: controller, project: shopfront));
}

Future<void> tapText(WidgetTester tester, String text) async {
  final target = find.text(text);
  if (target.evaluate().isEmpty) return;
  await tester.tap(target.first);
  await settle(tester);
}

String readText(WidgetTester tester) => screenText(tester).join('\n');

Future<void> shot(WidgetTester tester, GlobalKey boundary, String name) =>
    writeCasePng(tester, boundary, name);
