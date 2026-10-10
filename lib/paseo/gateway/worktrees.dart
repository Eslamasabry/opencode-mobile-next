part of '../gateway.dart';

/// Paseo's own worktrees of the open project: list, create and remove
/// (`paseo_worktree_list_request`, `create_paseo_worktree_request`,
/// `paseo_worktree_archive_request`). Reset is not offered: the daemon has
/// no call that returns a worktree to its base branch.
mixin _PaseoWorktreesApi on _PaseoWorkspaceBase {
  Future<List<WorktreeInfo>> listWorktrees({
    required String projectDirectory,
    String? projectID,
  }) async {
    final payload = await _workspaceRequest('paseo_worktree_list_request', {
      'cwd': projectDirectory,
    });
    _requireCheckoutOk(payload);
    return [
      for (final raw in paseoList(payload['worktrees'], max: 5000))
        _worktreeInfo(paseoObject(raw)),
    ];
  }

  WorktreeInfo _worktreeInfo(Map<String, dynamic> entry) {
    final directory = paseoString(entry['worktreePath']);
    final branch = entry['branchName'];
    return WorktreeInfo(
      name: _folderName(directory),
      directory: directory,
      branch: branch is String && branch.isNotEmpty ? branch : null,
    );
  }

  String _folderName(String directory) {
    final parts = directory.split('/').where((part) => part.isNotEmpty);
    return parts.isEmpty ? directory : parts.last;
  }

  Future<WorktreeInfo> createWorktree({
    required String projectDirectory,
    String? name,
  }) async {
    final slug = name?.trim() ?? '';
    final payload = await _workspaceRequest('create_paseo_worktree_request', {
      'cwd': projectDirectory,
      if (slug.isNotEmpty) 'worktreeSlug': slug,
    }, mutation: true);
    final workspace = paseoObject(payload['workspace']);
    final directory = paseoString(
      workspace['workspaceDirectory'] ?? workspace['projectRootPath'],
    );
    final git = workspace['gitRuntime'];
    final branch = git is Map ? git['currentBranch'] : null;
    final created = WorktreeInfo(
      name: _folderName(directory),
      directory: directory,
      branch: branch is String && branch.isNotEmpty ? branch : null,
      // The daemon answers once the folder exists: no readiness event follows.
      ready: true,
    );
    return created;
  }

  Future<void> removeWorktree({
    required String projectDirectory,
    required String directory,
  }) async {
    final payload = await _workspaceRequest('paseo_worktree_archive_request', {
      'worktreePath': paseoString(directory),
      'repoRoot': projectDirectory,
      'scope': 'worktree',
    }, mutation: true);
    _requireCheckoutOk(payload);
    if (payload['success'] != true) {
      throw PaseoFailure(PaseoFailureKind.unavailable);
    }
  }
}
