import 'package:flutter/material.dart';

import '../../domain/workspace_paths.dart';
import '../../l10n/app_localizations.dart';
import '../../state/connection.dart';
import '../../termux/bridge.dart';
import '../widgets/product_states.dart';

/// The two ways a workspace gets a project folder: create one on the
/// app-managed Termux server, or open an existing folder by its path.
///
/// Shared by Workspace (which blocks sessions until a folder is chosen) and
/// the project picker. The server's home folder is never an option; see
/// `workspace_paths.dart`.
class ProjectFolderActions {
  ProjectFolderActions._();

  /// Widget tests cannot reach the Termux bridge; they inject the creator.
  @visibleForTesting
  static Future<String> Function(String name)? createFolderOverride;

  @visibleForTesting
  static bool? canCreateOverride;

  /// Only the app-managed Termux server can create folders. Other servers
  /// expose no folder-creation API, so the user creates the folder on that
  /// machine and opens it by path.
  static bool canCreate(ConnectionController controller) {
    final override = canCreateOverride;
    if (override != null) return override;
    final profile = controller.profile;
    return profile != null &&
        TermuxBridge.supported &&
        TermuxBridge.managesServerUrl(profile.baseUrl);
  }

  /// Asks for a folder name, creates `/root/projects/<name>` on the managed
  /// server, and opens it. Returns the opened directory, or null when the
  /// user cancelled or the folder could not be created or opened.
  static Future<String?> createFolder(
    BuildContext context,
    ConnectionController controller,
  ) async {
    final name = await showDialog<String>(
      context: context,
      builder: (_) => const _NewFolderDialog(),
    );
    if (name == null || !context.mounted) return null;
    final create = createFolderOverride ?? TermuxBridge.createProjectFolder;
    String path;
    try {
      path = await create(name);
    } catch (error) {
      if (context.mounted) _notify(context, productErrorText(error));
      return null;
    }
    if (!context.mounted) return null;
    return _open(context, controller, path);
  }

  /// Asks for an absolute path, confirms it exists on the server, and opens
  /// it. Returns the opened directory, or null when cancelled or refused.
  static Future<String?> openFolder(
    BuildContext context,
    ConnectionController controller,
  ) async {
    final path = await showDialog<String>(
      context: context,
      builder: (_) => _OpenFolderDialog(controller: controller),
    );
    if (path == null || !context.mounted) return null;
    return _open(context, controller, path);
  }

  static Future<String?> _open(
    BuildContext context,
    ConnectionController controller,
    String path,
  ) async {
    await controller.selectLocation(directory: path);
    final problem = controller.locationError;
    if (problem != null) {
      if (context.mounted) _notify(context, problem);
      return null;
    }
    return path;
  }

  static void _notify(BuildContext context, String message) =>
      ScaffoldMessenger.maybeOf(
        context,
      )?.showSnackBar(SnackBar(content: Text(message)));
}

class _NewFolderDialog extends StatefulWidget {
  const _NewFolderDialog();

  @override
  State<_NewFolderDialog> createState() => _NewFolderDialogState();
}

class _NewFolderDialogState extends State<_NewFolderDialog> {
  final _name = TextEditingController();
  String? _problem;

  @override
  void dispose() {
    _name.dispose();
    super.dispose();
  }

  void _submit() {
    final problem = projectFolderNameProblem(_name.text);
    if (problem != null) {
      setState(() => _problem = problem);
      return;
    }
    Navigator.of(context).pop(_name.text.trim());
  }

  @override
  Widget build(BuildContext context) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    return AlertDialog(
      title: Text(l10n.projectFolderCreate),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(l10n.projectFolderCreateMessage(managedProjectsDirectory)),
          const SizedBox(height: 12),
          TextField(
            key: const ValueKey('new-folder-name'),
            controller: _name,
            autofocus: true,
            textInputAction: TextInputAction.done,
            onChanged: (_) {
              if (_problem != null) setState(() => _problem = null);
            },
            onSubmitted: (_) => _submit(),
            decoration: InputDecoration(
              labelText: l10n.projectFolderNameLabel,
              hintText: l10n.projectFolderNameHint,
              errorText: _problem,
              errorMaxLines: 3,
            ),
          ),
        ],
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(context).pop(),
          child: Text(l10n.projectFolderCancel),
        ),
        FilledButton(
          key: const ValueKey('new-folder-create'),
          onPressed: _submit,
          child: Text(l10n.projectFolderCreateAction),
        ),
      ],
    );
  }
}

class _OpenFolderDialog extends StatefulWidget {
  const _OpenFolderDialog({required this.controller});

  final ConnectionController controller;

  @override
  State<_OpenFolderDialog> createState() => _OpenFolderDialogState();
}

class _OpenFolderDialogState extends State<_OpenFolderDialog> {
  final _path = TextEditingController();
  String? _problem;
  bool _checking = false;

  @override
  void dispose() {
    _path.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (_checking) return;
    final path = _path.text.trim();
    final problem = workspaceDirectoryProblem(path);
    if (problem != null) {
      setState(() => _problem = problem);
      return;
    }
    setState(() {
      _checking = true;
      _problem = null;
    });
    final serverProblem = await widget.controller.probeProjectFolder(path);
    if (!mounted) return;
    if (serverProblem != null) {
      setState(() {
        _checking = false;
        _problem = serverProblem;
      });
      return;
    }
    Navigator.of(context).pop(path);
  }

  @override
  Widget build(BuildContext context) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    return AlertDialog(
      title: Text(l10n.projectFolderOpen),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(l10n.projectFolderOpenMessage),
          const SizedBox(height: 12),
          TextField(
            key: const ValueKey('open-folder-path'),
            controller: _path,
            autofocus: true,
            enabled: !_checking,
            keyboardType: TextInputType.url,
            autocorrect: false,
            textInputAction: TextInputAction.done,
            onChanged: (_) {
              if (_problem != null) setState(() => _problem = null);
            },
            onSubmitted: (_) => _submit(),
            decoration: InputDecoration(
              labelText: l10n.projectFolderPathLabel,
              hintText: l10n.projectFolderPathHint(managedProjectsDirectory),
              errorText: _problem,
              errorMaxLines: 4,
            ),
          ),
          if (_checking) ...[
            const SizedBox(height: 12),
            const LinearProgressIndicator(minHeight: 2),
          ],
        ],
      ),
      actions: [
        TextButton(
          onPressed: _checking ? null : () => Navigator.of(context).pop(),
          child: Text(l10n.projectFolderCancel),
        ),
        FilledButton(
          key: const ValueKey('open-folder-confirm'),
          onPressed: _checking ? null : _submit,
          child: Text(l10n.projectFolderOpenAction),
        ),
      ],
    );
  }
}
