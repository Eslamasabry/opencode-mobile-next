import 'package:flutter/material.dart';

import '../../builtin/builtin_folders.dart';
import '../../builtin/builtin_linux.dart';
import '../../builtin/builtin_server.dart';
import '../../domain/server_gateway.dart' show WorkspaceProject;
import '../../domain/team_directories.dart';
import '../../domain/workspace_paths.dart';
import '../../l10n/app_localizations.dart';
import '../../state/connection.dart';
import '../../state/interaction_defaults.dart';
import '../../termux/bridge.dart';
import '../../termux/termux_folders.dart';
import '../app_iconography.dart';
import '../kit/kit.dart';
import '../widgets/folder_browser.dart';
import '../widgets/product_states.dart' show productErrorText;
import '../widgets/termux_running_server_entry.dart' show isManagedPhoneProfile;
import 'shared_storage_access_flow.dart';

/// The ways a workspace gets a project folder: create one on a server this
/// app runs (Termux, or OpenCode inside the app), pick one of its projects,
/// or open an existing folder by its path.
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

  /// Widget tests hand in a fake bridge for OpenCode inside the app.
  @visibleForTesting
  static BuiltinLinux? builtinLinuxOverride;

  static BuiltinLinux get _linux => builtinLinuxOverride ?? BuiltinLinux();

  /// Only a server this app runs can create folders: the app-managed Termux
  /// server and OpenCode inside the app. Other servers expose no
  /// folder-creation API, so the user creates the folder on that machine and
  /// opens it by path.
  static bool canCreate(ConnectionController controller) {
    final override = canCreateOverride;
    if (override != null) return override;
    final profile = controller.profile;
    return profile != null &&
        (TermuxBridge.supported &&
                TermuxBridge.managesServerUrl(profile.baseUrl) ||
            looksLikeInAppServer(profile));
  }

  /// The name a new project starts with when the server has no project of
  /// its own yet ("my-app" on first run, P6.6): the field holds it, ready to
  /// create or to type over. Null while the list is not loaded, or once
  /// there is a project (the server's root and the AI Team's folders do not
  /// count), so the field shows only its example.
  static String? suggestedName(List<WorkspaceProject>? projects) {
    if (projects == null) return null;
    final own = projects
        .where(
          (project) =>
              !isProtectedWorkspaceDirectory(project.directory) &&
              !isAiTeamDirectory(project.directory),
        )
        .toList();
    final proposal = InteractionDefaults.project(own).value;
    return proposal != null && proposal.needsCreation ? proposal.name : null;
  }

  /// Asks for a folder name, creates `/root/projects/<name>` on the managed
  /// server, and opens it. Returns the opened directory, or null when the
  /// user cancelled or the folder could not be created or opened.
  /// [suggestedName] fills the field ([ProjectFolderActions.suggestedName]).
  ///
  /// The folder is made while the dialog is open, so a failure is said
  /// under the name with the name kept (map project-folder-new-dialog,
  /// "create fails").
  static Future<String?> createFolder(
    BuildContext context,
    ConnectionController controller, {
    String? suggestedName,
  }) async {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    // Termux never serves 4097, so the address alone picks the right
    // creator here; were Ubuntu missing, the bridge says so itself.
    final inApp = looksLikeInAppServer(controller.profile);
    String? made;
    final name = await showKitInputDialog(
      context,
      title: l10n.projectFolderCreate,
      label: l10n.projectFolderNameLabel,
      confirmLabel: l10n.projectFolderCreateAction,
      initial: suggestedName,
      hint: l10n.projectFolderNameHint,
      helper: l10n.projectFolderCreateHelper(managedProjectsDirectory),
      cancelLabel: l10n.projectFolderCancel,
      validate: projectFolderNameProblem,
      fieldKey: const ValueKey('new-folder-name'),
      confirmKey: const ValueKey('new-folder-create'),
      onSubmit: (value) async {
        final name = value.trim();
        try {
          if (inApp) {
            made = (await BuiltinProjectFolders(
              _linux,
            ).create(BuiltinProjectFolders.pathFor(name))).path;
          } else {
            final create =
                createFolderOverride ?? TermuxBridge.createProjectFolder;
            made = await create(name);
          }
          return null;
        } on BuiltinLinuxException catch (error) {
          return l10n.projectFolderCreateFailed(
            productErrorText(error, l10n: l10n),
          );
        } catch (error) {
          return productErrorText(error);
        }
      },
    );
    final path = made;
    if (name == null || path == null || !context.mounted) return null;
    return _open(context, controller, path);
  }

  /// Widget tests list folders without Ubuntu's files on disk.
  @visibleForTesting
  static FolderLister? folderListerOverride;

  /// A server on this phone (OpenCode inside the app, or the one this app
  /// runs in Termux): browse its folders from the projects folder and open
  /// one, name a new project in the folder shown, or enter a path. Any other
  /// server: enter a path, which is confirmed on the server before it opens
  /// (OpenCode lists files only inside the project it is asked about, so its
  /// folders cannot be browsed from here). Returns the opened directory, or
  /// null when cancelled or refused.
  static Future<String?> openFolder(
    BuildContext context,
    ConnectionController controller,
  ) async {
    final linux = _linux;
    if (await isInAppServer(controller.profile, linux)) {
      if (!context.mounted) return null;
      final folders = BuiltinProjectFolders(linux);
      final choice = await showKitFramedSheet<FolderBrowserChoice>(
        context,
        builder: (_) => FolderBrowserSheet(
          list: folderListerOverride ?? BuiltinFolders(linux).list,
          knownProjects: () => _knownProjects(controller),
        ),
      );
      if (choice == null || !context.mounted) return null;
      return switch (choice) {
        FolderBrowserOpen(:final path) => _open(context, controller, path),
        FolderBrowserCreate(:final path) => _createInApp(
          context,
          controller,
          folders,
          path,
        ),
        FolderBrowserEnterPath(:final startPath) => _openByPath(
          context,
          controller,
          folders,
          startPath: startPath,
        ),
      };
    }
    if (!context.mounted) return null;
    if (await _termuxBrowsable(controller)) {
      if (!context.mounted) return null;
      final termux = TermuxFolders();
      final choice = await showKitFramedSheet<FolderBrowserChoice>(
        context,
        builder: (_) => FolderBrowserSheet(
          list: folderListerOverride ?? termux.list,
          knownProjects: () => _knownProjects(controller),
        ),
      );
      if (choice == null || !context.mounted) return null;
      return switch (choice) {
        FolderBrowserOpen(:final path) => _open(context, controller, path),
        FolderBrowserCreate(:final path) => _createInTermux(
          context,
          controller,
          termux,
          path,
        ),
        FolderBrowserEnterPath(:final startPath) => _openByPath(
          context,
          controller,
          null,
          startPath: startPath,
        ),
      };
    }
    if (!context.mounted) return null;
    return _openByPath(context, controller, null);
  }

  /// The server in use is the one this app runs in Termux, and Termux can
  /// run the app's commands now (installed, its service there, the app
  /// allowed to use it). Then its folders are listed through Termux; any
  /// other server has no way to list folders outside its project.
  static Future<bool> _termuxBrowsable(ConnectionController controller) async {
    final profile = controller.profile;
    if (profile == null ||
        !TermuxBridge.supported ||
        !isManagedPhoneProfile(profile)) {
      return false;
    }
    try {
      final termux = await TermuxBridge.capabilities();
      return termux.installed &&
          termux.serviceAvailable &&
          termux.protocolSupported &&
          termux.permissionGranted;
    } catch (_) {
      return false;
    }
  }

  /// Makes [path] in Termux's Ubuntu (or finds it already there) and opens
  /// it.
  static Future<String?> _createInTermux(
    BuildContext context,
    ConnectionController controller,
    TermuxFolders termux,
    String path,
  ) async {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final access = await _sharedStorage(context, controller, path);
    if (access == SharedStorageOutcome.useAppSpace) {
      return context.mounted ? openFolder(context, controller) : null;
    }
    if (access == SharedStorageOutcome.declined || !context.mounted) {
      return null;
    }
    final ({String path, bool created}) made;
    try {
      made = await termux.create(path);
    } on FolderListException catch (error) {
      if (context.mounted) {
        await _alert(
          context,
          l10n.projectFolderCreateFailedTitle,
          l10n.projectFolderCreateFailed(productErrorText(error, l10n: l10n)),
        );
      }
      return null;
    }
    if (!context.mounted) return null;
    return _open(context, controller, made.path);
  }

  /// The folders the connected OpenCode already has as projects, for the
  /// browser's marks. Asked of the connection as it is; nothing is started.
  static Future<Set<String>> _knownProjects(
    ConnectionController controller,
  ) async {
    final repository = controller.repository;
    if (repository == null) return const {};
    final projects = await repository.listProjects().timeout(
      const Duration(seconds: 5),
    );
    return {
      for (final project in projects)
        for (final directory in [project.directory, ...project.worktrees])
          ConnectionController.normalizeDirectoryPath(directory),
    };
  }

  /// Asks for a path and opens it once it is confirmed to exist: in the
  /// app's own Ubuntu for OpenCode inside the app, otherwise on the server.
  /// A missing folder inside the app is offered "Create it" (declining goes
  /// back to the path); another server's answer is said under the path.
  static Future<String?> _openByPath(
    BuildContext context,
    ConnectionController controller,
    BuiltinProjectFolders? inApp, {
    String? startPath,
  }) async {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    var initial = switch (startPath) {
      null => '',
      '/' => '/',
      final start => '$start/',
    };
    while (true) {
      var missing = false;
      final typed = await showKitInputDialog(
        context,
        title: l10n.projectFolderOpen,
        label: l10n.projectFolderPathLabel,
        confirmLabel: l10n.projectFolderOpenAction,
        initial: initial.isEmpty ? null : initial,
        kind: KitFieldKind.path,
        hint: l10n.projectFolderPathHint(managedProjectsDirectory),
        helper: l10n.projectFolderOpenMessage,
        cancelLabel: l10n.projectFolderCancel,
        validate: (value) => workspaceDirectoryProblem(value.trim()),
        fieldKey: const ValueKey('open-folder-path'),
        confirmKey: const ValueKey('open-folder-confirm'),
        onSubmit: (value) async {
          final path = value.trim();
          if (inApp == null) return controller.probeProjectFolder(path);
          // Checked in Ubuntu, not by asking OpenCode: OpenCode would
          // remember a missing folder as broken and keep failing there
          // after it is made.
          try {
            missing = !await inApp.exists(path);
          } on BuiltinLinuxException catch (error) {
            return l10n.projectFolderCheckFailed(
              productErrorText(error, l10n: l10n),
            );
          }
          return null;
        },
      );
      if (typed == null || !context.mounted) return null;
      final path = typed.trim();
      if (!missing || inApp == null) return _open(context, controller, path);
      final create = await showKitConfirm(
        context,
        title: l10n.projectFolderMissingTitle,
        body: l10n.projectFolderMissing,
        confirmLabel: l10n.projectFolderCreateIt,
        icon: AppIconography.folderAdd,
        details: [KitTechnicalValue(l10n.projectFolderPathLabel, path)],
        confirmKey: const ValueKey('open-folder-create-missing'),
      );
      if (!context.mounted) return null;
      if (create) return _createInApp(context, controller, inApp, path);
      initial = path;
    }
  }

  /// Makes [path] inside the app's Ubuntu (a new git project, or the folder
  /// that is already there) and opens it. OpenCode hears of the folder only
  /// once it exists: here folders are checked through Ubuntu, never through
  /// OpenCode, so it has no stale view of the folder to forget. (Dropping
  /// one anyway restarted OpenCode's event stream, flashing "Reconnecting".)
  static Future<String?> _createInApp(
    BuildContext context,
    ConnectionController controller,
    BuiltinProjectFolders folders,
    String path,
  ) async {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    // A folder in shared storage needs the access first: making it without
    // would fail, or look empty afterwards.
    final access = await _sharedStorage(context, controller, path);
    if (access == SharedStorageOutcome.useAppSpace) {
      return context.mounted ? openFolder(context, controller) : null;
    }
    if (access == SharedStorageOutcome.declined || !context.mounted) {
      return null;
    }
    final ({String path, bool created}) made;
    try {
      made = await folders.create(path);
    } on BuiltinLinuxException catch (error) {
      if (context.mounted) {
        await _alert(
          context,
          l10n.projectFolderCreateFailedTitle,
          l10n.projectFolderCreateFailed(productErrorText(error, l10n: l10n)),
        );
      }
      return null;
    }
    if (!context.mounted) return null;
    return _open(context, controller, made.path);
  }

  static Future<String?> _open(
    BuildContext context,
    ConnectionController controller,
    String path,
  ) async {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final access = await _sharedStorage(context, controller, path);
    if (access == SharedStorageOutcome.useAppSpace) {
      return context.mounted ? openFolder(context, controller) : null;
    }
    if (access == SharedStorageOutcome.declined || !context.mounted) {
      return null;
    }
    await controller.selectLocation(directory: path);
    final problem = controller.locationError;
    if (problem != null) {
      if (context.mounted) {
        await _alert(context, l10n.projectFolderOpenFailedTitle, problem);
      }
      return null;
    }
    return path;
  }

  /// A folder in the phone's shared storage is opened only once the host that
  /// reads it can see its files: asks first, in plain words, and never opens
  /// a folder that would list as empty. Other folders pass untouched.
  static Future<SharedStorageOutcome> _sharedStorage(
    BuildContext context,
    ConnectionController controller,
    String path,
  ) => SharedStorageAccessFlow.ensure(
    context,
    controller.profile,
    path,
    offerAppSpace: canCreate(controller),
  );

  /// A failure with nowhere else to be said: the flow has left its
  /// dialog, so a blocking alert names what did not happen and why.
  static Future<void> _alert(
    BuildContext context,
    String title,
    String message,
  ) => showKitAlert(
    context,
    title: title,
    body: message,
    icon: AppIconography.error,
  );
}
