part of '../gateway.dart';

/// Changes of a checkout the daemon runs in: the uncommitted work, the work
/// since the base branch, per-file counts and the branch (`checkout.diff.get`,
/// `checkout_status_request`). Read-only: nothing here changes the checkout.
mixin _PaseoChangesApi on _PaseoWorkspaceBase {
  Map<String, Map<String, dynamic>> get _agents;

  /// Checkout answers carry their failure as `{code, message}`; the words
  /// never reach the person (the daemon's text stays out of the app).
  void _requireCheckoutOk(Map<String, dynamic> payload) {
    final error = payload['error'];
    if (error != null) throw PaseoFailure(PaseoFailureKind.unavailable);
  }

  /// The parsed diff of [cwd]; `base` compares with the branch it grew from.
  Future<List<Map<String, dynamic>>> _checkoutDiff(
    String cwd,
    VcsDiffMode mode,
  ) async {
    final payload = await _workspaceRequest('checkout.diff.get.request', {
      'cwd': cwd,
      'compare': {
        'mode': mode == VcsDiffMode.workingTree ? 'uncommitted' : 'base',
        'ignoreWhitespace': false,
      },
    });
    _requireCheckoutOk(payload);
    return [
      for (final raw in paseoList(payload['files'], max: 5000))
        paseoObject(raw),
    ];
  }

  Future<List<FileDiff>> listVcsDiffs(VcsDiffMode mode) async => [
    for (final file in await _checkoutDiff(_scope, mode)) _fileDiff(file),
  ];

  /// What this conversation's agent changed: the uncommitted work in the
  /// folder it runs in (a Paseo agent has no diff of its own).
  Future<List<FileDiff>> diff(String id) async {
    final cwd = _agents[id]?['cwd'];
    return [
      for (final file in await _checkoutDiff(
        cwd is String && cwd.isNotEmpty ? cwd : _scope,
        VcsDiffMode.workingTree,
      ))
        _fileDiff(file),
    ];
  }

  Future<List<VersionControlFile>> listFileStatuses() async =>
      _fileStatuses(await _checkoutDiff(_scope, VcsDiffMode.workingTree));

  /// The changed files of a worktree folder, for the check before a removal.
  Future<List<VersionControlFile>> listWorktreeFileStatuses(
    String directory,
  ) async => _fileStatuses(
    await _checkoutDiff(
      paseoString(directory, max: 4096),
      VcsDiffMode.workingTree,
    ),
  );

  Future<VersionControlHealth> loadVersionControlHealth() async {
    final payload = await _workspaceRequest('checkout_status_request', {
      'cwd': _scope,
    });
    _requireCheckoutOk(payload);
    if (payload['isGit'] != true) {
      return const VersionControlHealth(
        changes: [],
        setupState: VersionControlSetupState.absent,
      );
    }
    final branch = payload['currentBranch'];
    final base = payload['baseRef'];
    var changes = const <VersionControlFile>[];
    if (payload['isDirty'] == true) {
      changes = await listFileStatuses();
    }
    return VersionControlHealth(
      branch: branch is String ? branch : null,
      defaultBranch: base is String ? base : null,
      changes: changes,
      setupState: VersionControlSetupState.git,
    );
  }

  List<VersionControlFile> _fileStatuses(List<Map<String, dynamic>> files) => [
    for (final file in files)
      VersionControlFile(
        path: paseoString(file['path']),
        status: file['isNew'] == true
            ? 'added'
            : (file['isDeleted'] == true ? 'deleted' : 'modified'),
        additions: _count(file['additions']),
        deletions: _count(file['deletions']),
      ),
  ];

  int _count(Object? value) => value is int && value >= 0 ? value : 0;

  /// A daemon file diff as the unified patch the review screens read.
  FileDiff _fileDiff(Map<String, dynamic> file) {
    final path = paseoString(file['path']);
    final status = file['status'];
    final isNew = file['isNew'] == true;
    final isDeleted = file['isDeleted'] == true;
    final patch = StringBuffer();
    if (status == 'binary') {
      patch.write('Binary files a/$path and b/$path differ');
    } else {
      final hunks = file['hunks'];
      if (hunks is List && hunks.isNotEmpty) {
        patch.writeln('--- ${isNew ? '/dev/null' : 'a/$path'}');
        patch.writeln('+++ ${isDeleted ? '/dev/null' : 'b/$path'}');
        for (final hunk in hunks) {
          final lines = hunk is Map ? hunk['lines'] : null;
          if (lines is! List) continue;
          for (final line in lines) {
            if (line is! Map) continue;
            final text = line['content'] is String ? line['content'] : '';
            patch.writeln(switch (line['type']) {
              'header' => text,
              'add' => '+$text',
              'remove' => '-$text',
              _ => ' $text',
            });
          }
        }
      }
    }
    return FileDiff(
      file: path,
      patch: patch.isEmpty ? null : patch.toString(),
      additions: _count(file['additions']),
      deletions: _count(file['deletions']),
      status: isNew ? 'added' : (isDeleted ? 'deleted' : 'modified'),
    );
  }
}
