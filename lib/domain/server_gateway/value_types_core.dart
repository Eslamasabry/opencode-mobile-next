import '../../api/models.dart';

/// Connection lifecycle surfaced to the UI.
enum StreamStatus { connecting, connected, reconnecting, disconnected }

/// One live server event subscription. Implementations own reconnect and
/// backoff; callers only start and dispose the channel.
abstract class LiveEventChannel {
  void start();
  Future<void> dispose();
}

enum VcsDiffMode { workingTree, branch }

/// A server-owned continuation token. Consumers must not derive or order it.
class ServerPage<T> {
  const ServerPage({required this.items, this.nextCursor});
  final List<T> items;
  final String? nextCursor;
  bool get hasMore => nextCursor != null && nextCursor!.isNotEmpty;
}

/// Servers whose model and agent are persistent session state rather than
/// fields on each prompt. Defaults are applied only at creation.
abstract interface class SessionSelectionGateway {
  Future<Session> createSelectedSession(SessionSelection defaults);
  Future<void> setSessionModel(
    String sessionID,
    ModelRef model,
    String variant,
  );
  Future<void> setSessionAgent(String sessionID, String agent);
}

/// V2's explicit stage/review/commit lifecycle; v1 retains revert/restore.
abstract interface class StagedRevertGateway {
  /// Null for a non-user message; empty text is a valid attachment-only prompt.
  Future<String?> sessionRevertPrompt(String sessionID, String messageID);
  Future<SessionRevert> stageSessionRevert(
    String sessionID,
    String messageID, {
    required bool applyFiles,
  });
  Future<void> clearSessionRevert(String sessionID);
  Future<void> commitSessionRevert(String sessionID);
}

class WorkspaceProject {
  final String id;
  final String name;
  final String directory;
  final List<String> worktrees;
  final int updatedAt;

  const WorkspaceProject({
    required this.id,
    required this.name,
    required this.directory,
    required this.worktrees,
    required this.updatedAt,
  });
}

class WorkspaceInfo {
  final String id;
  final String projectID;
  final String name;
  final String type;
  final String? branch;
  final String? directory;
  final String? status;

  const WorkspaceInfo({
    required this.id,
    required this.projectID,
    required this.name,
    required this.type,
    this.branch,
    this.directory,
    this.status,
  });
}

class WorkspaceAdapterInfo {
  final String type;
  final String name;
  final String description;

  const WorkspaceAdapterInfo({
    required this.type,
    required this.name,
    required this.description,
  });
}

class WorktreeInfo {
  final String name;
  final String directory;
  final String? branch;

  const WorktreeInfo({
    required this.name,
    required this.directory,
    this.branch,
  });
}

class ProjectDirectoryInfo {
  final String directory;
  final String? strategy;

  const ProjectDirectoryInfo({required this.directory, this.strategy});
}

class GlobalSessionResult {
  final Session session;
  final String? projectName;
  final String? projectDirectory;

  const GlobalSessionResult({
    required this.session,
    this.projectName,
    this.projectDirectory,
  });
}

class ConsoleOrganization {
  final String accountID;
  final String accountEmail;
  final String accountUrl;
  final String orgID;
  final String orgName;
  final bool active;

  const ConsoleOrganization({
    required this.accountID,
    required this.accountEmail,
    required this.accountUrl,
    required this.orgID,
    required this.orgName,
    required this.active,
  });
}

enum VersionControlSetupState { git, absent, unknown }

class VersionControlHealth {
  final String? branch;
  final String? defaultBranch;
  final List<VersionControlFile> changes;
  final VersionControlSetupState setupState;

  const VersionControlHealth({
    this.branch,
    this.defaultBranch,
    required this.changes,
    this.setupState = VersionControlSetupState.unknown,
  });

  int get additions => changes.fold(0, (total, file) => total + file.additions);
  int get deletions => changes.fold(0, (total, file) => total + file.deletions);
}

class VersionControlFile {
  final String path;
  final String status;
  final int additions;
  final int deletions;

  const VersionControlFile({
    required this.path,
    required this.status,
    required this.additions,
    required this.deletions,
  });
}

class LanguageServiceHealth {
  final String id;
  final String name;
  final String root;
  final String status;

  const LanguageServiceHealth({
    required this.id,
    required this.name,
    required this.root,
    required this.status,
  });

  bool get connected => status == 'connected';
}

class FormatterHealth {
  final String name;
  final List<String> extensions;
  final bool enabled;

  const FormatterHealth({
    required this.name,
    required this.extensions,
    required this.enabled,
  });
}

class SavedPermission {
  final String id;
  final String projectID;
  final String action;
  final String resource;

  const SavedPermission({
    required this.id,
    required this.projectID,
    required this.action,
    required this.resource,
  });
}

class WorkspaceSymbol {
  final String name;
  final int kind;
  final String path;
  final int line;
  final int column;

  const WorkspaceSymbol({
    required this.name,
    required this.kind,
    required this.path,
    required this.line,
    required this.column,
  });
}

class TerminalProcess {
  final String id;
  final String title;
  final String command;
  final List<String> arguments;
  final String directory;
  final bool running;
  final int pid;
  final int? exitCode;

  const TerminalProcess({
    required this.id,
    required this.title,
    required this.command,
    required this.arguments,
    required this.directory,
    required this.running,
    required this.pid,
    this.exitCode,
  });
}

class TerminalShellOption {
  final String path;
  final String name;
  final bool acceptable;

  const TerminalShellOption({
    required this.path,
    required this.name,
    required this.acceptable,
  });
}

class TerminalShellSettings {
  final String selected;
  final List<TerminalShellOption> options;

  const TerminalShellSettings({required this.selected, required this.options});
}

class CatalogVariant {
  final String id;
  final bool disabled;
  final Map<String, dynamic> options;

  const CatalogVariant({
    required this.id,
    this.disabled = false,
    this.options = const {},
  });

  String? get reasoningEffort {
    final value = options['reasoningEffort'] ?? options['reasoning_effort'];
    return value?.toString();
  }

  bool get isFast {
    final normalizedID = id.toLowerCase();
    final effort = reasoningEffort?.toLowerCase();
    return effort == 'low' ||
        normalizedID == 'fast' ||
        normalizedID.contains('turbo');
  }
}

/// Model pricing. Every figure is **USD per one million tokens**, which is
/// the unit both servers use natively (v1 `Model.cost` mirrors models.dev's
/// per-million prices; v2 `Model.Cost` is typed `Money.USDPerMillionTokens`),
/// so no conversion happens on the way in.
class ModelCost {
  final double inputPerMillion;
  final double outputPerMillion;
  final double cacheReadPerMillion;
  final double cacheWritePerMillion;

  const ModelCost({
    required this.inputPerMillion,
    required this.outputPerMillion,
    this.cacheReadPerMillion = 0,
    this.cacheWritePerMillion = 0,
  });

  /// Parses the `{input, output, cache: {read, write}}` object both servers
  /// send; null for anything that is not a map.
  static ModelCost? fromJson(dynamic v) {
    if (v is! Map) return null;
    final cache = v['cache'] is Map ? v['cache'] as Map : const {};
    return ModelCost(
      inputPerMillion: _toDouble(v['input']),
      outputPerMillion: _toDouble(v['output']),
      cacheReadPerMillion: _toDouble(cache['read']),
      cacheWritePerMillion: _toDouble(cache['write']),
    );
  }

  /// True when every rate is zero (free / self-hosted models).
  bool get isFree =>
      inputPerMillion == 0 &&
      outputPerMillion == 0 &&
      cacheReadPerMillion == 0 &&
      cacheWritePerMillion == 0;

  static double _toDouble(dynamic v) =>
      v is num ? v.toDouble() : (v is String ? double.tryParse(v) ?? 0 : 0);
}

class CatalogModel {
  final String id;
  final String providerID;
  final String name;
  final String? family;
  final bool enabled;

  /// Lifecycle status as the server reports it: `active`, `beta`, `alpha`,
  /// `deprecated`, or `unknown` when the server sent none.
  final String status;
  final int contextLimit;
  final int outputLimit;
  final bool reasoning;
  final bool attachments;
  final bool tools;
  final List<CatalogVariant> variants;

  /// Base pricing (USD per million tokens); null when the server sent none.
  final ModelCost? cost;

  /// Model release date (v1 `release_date` `YYYY-MM-DD`, v2 `time.released`
  /// epoch millis); null when unknown.
  final DateTime? released;

  const CatalogModel({
    required this.id,
    required this.providerID,
    required this.name,
    this.family,
    required this.enabled,
    required this.status,
    required this.contextLimit,
    required this.outputLimit,
    required this.reasoning,
    required this.attachments,
    required this.tools,
    required this.variants,
    this.cost,
    this.released,
  });

  /// True for `deprecated` models.
  bool get deprecated => status.toLowerCase() == 'deprecated';

  /// True for `alpha`/`beta` models.
  bool get preview {
    final s = status.toLowerCase();
    return s == 'alpha' || s == 'beta';
  }
}

class CatalogProvider {
  final String id;
  final String name;
  final bool enabled;
  final String? integrationID;

  const CatalogProvider({
    required this.id,
    required this.name,
    required this.enabled,
    this.integrationID,
  });
}

class CatalogAgent {
  final String id;
  final String mode;
  final String? description;
  final bool hidden;
  final int? maxSteps;

  /// Raw colour string as the server gives it: `#rrggbb` or a theme token
  /// (`primary`, `accent`, ...). Null when the agent has no colour.
  final String? color;

  /// Configured model as `providerID/modelID`; null when the agent inherits
  /// the session's model.
  final String? model;

  const CatalogAgent({
    required this.id,
    required this.mode,
    this.description,
    required this.hidden,
    this.maxSteps,
    this.color,
    this.model,
  });
}

class CatalogSnapshot {
  final List<CatalogProvider> providers;
  final List<CatalogModel> models;
  final List<CatalogAgent> agents;

  const CatalogSnapshot({
    required this.providers,
    required this.models,
    required this.agents,
  });
}
