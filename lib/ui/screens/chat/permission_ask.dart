import 'package:flutter/foundation.dart';

import '../../../domain/models.dart';
import '../../../l10n/app_localizations.dart';
import '../../kit/kit.dart';

// What a permission request asks, worded from the request alone (vision
// slice "Chat with a question", 2026-10-20; the audit is in
// docs/qa/vision-chat-approval-2026-10-20/README.md).

const _diffMetadataKeys = ['diff', 'patch', 'preview'];

/// The request's command, file or patterns in one line for the card: the
/// full value lives in the details sheet. An edit of several files names
/// every one of them, never only the first.
String? permissionSummary(PermissionRequest permission) {
  final paths = _editPaths(permission);
  if (paths.length > 1) return paths.join(' · ');
  return permission.commandPreview ??
      permission.filePath ??
      (permission.patterns.isEmpty ? null : permission.patterns.join(' · '));
}

const _shellActions = {'bash', 'shell'};
const _editActions = {'edit', 'write', 'multiedit', 'patch', 'apply_patch'};

/// A command this short and on one line reads inside the question.
const _commandInTitle = 32;

/// Every path an edit names, in its order, without wildcards: OpenCode's
/// `resources`/`patterns`, else the one path in its metadata.
List<String> _editPaths(PermissionRequest permission) {
  if (!_editActions.contains(permission.permission.toLowerCase())) {
    return const [];
  }
  final paths = <String>[
    for (final pattern in permission.patterns)
      if (pattern.trim().isNotEmpty && !pattern.contains('*')) pattern.trim(),
  ];
  final path = permission.filePath;
  if (paths.isEmpty && path != null) paths.add(path);
  return paths.toSet().toList();
}

/// [path] inside [directory] as the project names it ("src/game.js"); any
/// other path as given.
String _projectPath(String path, String? directory) {
  final root = directory?.trim();
  if (root == null || root.isEmpty) return path;
  final prefix = root.endsWith('/') ? root : '$root/';
  return path.startsWith(prefix) ? path.substring(prefix.length) : path;
}

/// A name that never breaks at its hyphens ("neon-breaker" stays whole at
/// a line end): word joiners around each one.
String _whole(String name) => name.replaceAll('-', '\u2060-\u2060');

String _baseName(String path) {
  final parts = path.split('/').where((part) => part.isNotEmpty);
  return parts.isEmpty ? path : parts.last;
}

/// OpenCode 1's apply_patch says per file what it does and how much
/// (`metadata.files`: `relativePath`, `type`, `additions`, `deletions`).
Map<String, Map<dynamic, dynamic>> _fileFacts(PermissionRequest permission) {
  final raw = permission.metadata['files'];
  if (raw is! List) return const {};
  return {
    for (final file in raw.whereType<Map>())
      if (file['relativePath'] case final String path when path.isNotEmpty)
        path: file,
  };
}

/// Lines added and removed in one file's unified diff; null without one.
(int, int)? _diffSize(String? diff) {
  if (diff == null || diff.trim().isEmpty) return null;
  var added = 0;
  var removed = 0;
  for (final line in diff.split('\n')) {
    if (line.startsWith('+++') || line.startsWith('---')) continue;
    if (line.startsWith('+')) added++;
    if (line.startsWith('-')) removed++;
  }
  return (added, removed);
}

/// What a permission request asks, worded from the request itself: the
/// question naming the action and its target, what it will run or touch
/// (B's list), and the standing grant's name. Nothing here is guessed: a
/// shell command shows only its own words, a file's change size only when
/// the request carries its diff, "Create" and "Delete" only when the
/// request says so (OpenCode 1's apply_patch).
@immutable
class PermissionAsk {
  const PermissionAsk({
    required this.title,
    this.targets = const [],
    this.alwaysLabel,
    this.alwaysWhat,
  });

  final String title;
  final List<KitRequestTarget> targets;

  /// "Always allow npm install"; null keeps the card's own words.
  final String? alwaysLabel;

  /// What the grant's confirm names ("npm install", "edits").
  final String? alwaysWhat;
}

/// [PermissionAsk] for [permission]. [project] is the folder the chat works
/// in (its name ends the shell question); [directory] its path, so an
/// absolute path the agent names reads from the project. Without
/// [wholeCommandInTitle] a short command is not repeated word for word in
/// the question (the card's one mono line right under it shows it).
PermissionAsk permissionAsk(
  PermissionRequest permission, {
  required AppLocalizations l10n,
  required String fallback,
  String? project,
  String? directory,
  bool wholeCommandInTitle = true,
}) {
  final action = permission.permission.toLowerCase();
  final always = permission.always.isNotEmpty
      ? permission.always
      : const <String>[];
  if (_shellActions.contains(action)) {
    final command = permission.commandPreview?.trim();
    final cwd = permission.metadata['cwd'];
    // A Paseo agent says where it runs; OpenCode runs in the project.
    final where = cwd is String && cwd.trim().isNotEmpty
        ? _baseName(cwd.trim())
        : project;
    String? name;
    if (command != null &&
        command.isNotEmpty &&
        !command.contains('\n') &&
        command.length <= _commandInTitle) {
      if (wholeCommandInTitle) name = command;
    } else if (command != null) {
      // A long command reads by the family its own "always" pattern names
      // ("npm install *"), only when the command starts with it.
      for (final pattern in always) {
        final trimmed = pattern.trim();
        if (!trimmed.endsWith(' *')) continue;
        final prefix = trimmed.substring(0, trimmed.length - 2).trim();
        if (prefix.isEmpty ||
            prefix.contains('*') ||
            prefix.length > _commandInTitle ||
            !command.startsWith(prefix)) {
          continue;
        }
        name = prefix;
        break;
      }
    }
    final title = name != null && where != null && where.isNotEmpty
        ? l10n.chatRequestAskRunIn(
            KitBidi.ltr(name),
            KitBidi.auto(_whole(where)),
          )
        : name != null
        ? l10n.chatRequestAskRunNamed(KitBidi.ltr(name))
        : where != null && where.isNotEmpty
        ? l10n.chatRequestAskRunAnyIn(KitBidi.auto(_whole(where)))
        : l10n.chatRequestAskCommand;
    String? label;
    String? what;
    if (always.length == 1) {
      final saved = always.single.trim();
      if (saved == '*') {
        label = l10n.chatRequestAlwaysAllowAnyCommand;
        what = l10n.chatRequestAlwaysAnyCommandWhat;
      } else {
        final family = saved.endsWith(' *')
            ? saved.substring(0, saved.length - 2).trim()
            : saved;
        if (family.isNotEmpty &&
            !family.contains('*') &&
            !family.contains('\n') &&
            family.length <= _commandInTitle) {
          label = l10n.chatRequestAlwaysAllowNamed(KitBidi.ltr(family));
          what = family;
        }
      }
    }
    return PermissionAsk(
      title: title,
      targets: [
        if (command != null && command.isNotEmpty) KitRequestTarget(command),
      ],
      alwaysLabel: label ?? l10n.chatRequestAlwaysAllowCommands,
      alwaysWhat: what,
    );
  }
  final paths = _editPaths(permission);
  if (paths.isNotEmpty) {
    final facts = _fileFacts(permission);
    final single = _diffSize(_diffText(permission));
    final kinds = {
      for (final path in paths) (facts[path]?['type'] as Object?)?.toString(),
    };
    String? change(String path) {
      final file = facts[path];
      if (file != null &&
          file['additions'] is num &&
          file['deletions'] is num) {
        return l10n.kitRequestChangeSize(
          (file['additions'] as num).round(),
          (file['deletions'] as num).round(),
        );
      }
      if (paths.length == 1 && single != null) {
        return l10n.kitRequestChangeSize(single.$1, single.$2);
      }
      return null;
    }

    final kind = kinds.length == 1 ? kinds.single : null;
    final name = KitBidi.ltr(_baseName(paths.first));
    final count = paths.length;
    final title = switch (kind) {
      'add' when count == 1 => l10n.chatRequestAskCreateNamed(name),
      'add' => l10n.chatRequestAskCreateFiles(count),
      'delete' when count == 1 => l10n.chatRequestAskDeleteNamed(name),
      'delete' => l10n.chatRequestAskDeleteFiles(count),
      _ when count == 1 => l10n.chatRequestAskEditNamed(name),
      _ => l10n.chatRequestAskEditFiles(count),
    };
    final everyEdit = always.length == 1 && always.single.trim() == '*';
    return PermissionAsk(
      title: title,
      targets: [
        for (final path in paths)
          KitRequestTarget(
            _projectPath(path, directory),
            change: change(path),
            file: true,
          ),
      ],
      alwaysLabel: everyEdit ? l10n.chatRequestAlwaysAllowEdits : null,
      alwaysWhat: everyEdit ? l10n.chatRequestAlwaysEditsWhat : null,
    );
  }
  final read = action == 'read' ? permission.filePath : null;
  if (read != null) {
    return PermissionAsk(
      title: l10n.chatRequestAskReadNamed(KitBidi.ltr(_baseName(read))),
      targets: [KitRequestTarget(_projectPath(read, directory), file: true)],
    );
  }
  return PermissionAsk(title: fallback);
}

/// The diff an edit's request carries, from the common spellings.
String? _diffText(PermissionRequest permission) {
  for (final key in _diffMetadataKeys) {
    final value = permission.metadata[key];
    if (value is String && value.trim().isNotEmpty) return value;
  }
  return null;
}
