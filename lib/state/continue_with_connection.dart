import 'package:flutter/foundation.dart';

import '../domain/models.dart';
import '../builtin/builtin_linux.dart';
import '../domain/agent_catalog.dart';
import '../domain/agent_handoff.dart';
import '../domain/agent_tools/agent_certification.dart';
import '../domain/phone_agents.dart';
import '../domain/phone_agents_source.dart' show AgentModelChoice;
import '../domain/server_gateway.dart' show ServerGateway;
import 'agent_handoff_controller.dart';
import 'agent_handoff_links.dart';
import 'connection.dart';
import 'model_library.dart';
import 'profiles.dart';
import 'continue_with.dart';

/// [ContinueWithHost] over the app's connections: models of the open
/// conversation's own server (a switch) and the agents on this phone (a new
/// conversation with a record). Reads only; sending happens in the
/// [AgentHandoffController] the sheet drives.
class ConnectionContinueWithHost implements ContinueWithHost {
  ConnectionContinueWithHost(
    this.connection, {
    required this.sessionId,
    this.untitled = 'Untitled conversation',
  }) : _profileId = connection.profile?.id,
       _revision = connection.locationRevision,
       _directory = connection.directory,
       _workspace = connection.workspace;

  final ConnectionController connection;
  final String sessionId;

  /// The words for a conversation with no title yet.
  final String untitled;
  final String? _profileId;
  final int _revision;
  final String? _directory;
  final String? _workspace;

  /// The agent folder this sheet holds open (see [release]).
  String? _held;

  /// The app's side of "still the same source": same saved server, same
  /// location generation, same project.
  bool get _sourceUnchanged =>
      connection.profile?.id == _profileId &&
      connection.locationRevision == _revision &&
      connection.directory == _directory &&
      connection.workspace == _workspace;

  ConnectionController get _hub => connection.agentHandoffHub;

  /// The saved parent profile and project the source lives in. A managed
  /// phone server is the one place the app can prove two connections share a
  /// filesystem: its agent owner is the shared home.
  HandoffScope _scope({required HandoffOrigin origin}) {
    final profile = _hub.profile;
    final managed =
        profile != null && BuiltinLinux.managesServerUrl(profile.baseUrl);
    return HandoffScope(
      profileId: profile?.id ?? '',
      origin: origin,
      directory: connection.directory ?? '',
      workspace: connection.workspace,
      filesystemId: managed
          ? connection.store.phoneAgentOwnerId(profile.id)
          : null,
    );
  }

  HandoffScope get _sourceScope => _scope(
    origin: connection.isAgentBackend
        ? HandoffOrigin.phoneAgent
        : HandoffOrigin.server,
  );

  @override
  String get lastState => connection.agentHandoffLastState(sessionId);

  @override
  Future<ContinueWithPlan> load() async {
    final source = connection.api;
    final directory = connection.directory;
    if (source == null || directory == null || directory.isEmpty) {
      throw StateError('No conversation to continue');
    }
    try {
      await connection.ensureCatalog();
    } catch (_) {}
    final current = connection.modelForSession(sessionId);
    final scope = _sourceScope;
    final models = _models(source, scope, current);
    final agents = await _agents(scope);
    final title = connection.sessionsById[sessionId]?.title?.trim();
    final segments = directory.split('/').where((p) => p.isNotEmpty);
    return ContinueWithPlan(
      sourceTitle: title == null || title.isEmpty ? untitled : title,
      project: segments.isEmpty ? directory : segments.last,
      models: models,
      agents: agents,
      currentModel: current,
    );
  }

  List<ContinueWithChoice> _models(
    ServerGateway source,
    HandoffScope scope,
    ModelRef? current,
  ) {
    final catalog = connection.catalog;
    if (catalog == null) return const [];
    final library = connection.modelLibrary;
    final picks = <ModelRef>[
      for (final ref in [...library.favorites, ...library.recent])
        if (!ModelLibrary.sameModel(ref, current) &&
            connection.modelAvailable(ref))
          ref,
    ];
    final seen = <String>{};
    final choices = <ContinueWithChoice>[];
    for (final ref in picks) {
      final model = ref.normalized;
      if (!seen.add(model.wireName)) continue;
      final entry = catalog.models.where(
        (m) =>
            m.providerID == model.providerID &&
            bareModelID(m.providerID, m.id) == model.modelID,
      );
      final provider = catalog.providers.where((p) => p.id == model.providerID);
      final name = entry.isEmpty ? model.modelID : entry.first.name;
      choices.add(
        ContinueWithChoice(
          key: 'model:${model.wireName}',
          agentName: provider.isEmpty ? model.providerID : provider.first.name,
          modelName: name,
          target: HandoffTarget(
            gateway: source,
            scope: scope,
            model: model,
            label: name,
            startNewAgent: false,
          ),
        ),
      );
      if (choices.length >= 6) break;
    }
    return choices;
  }

  Future<List<ContinueWithChoice>> _agents(HandoffScope source) async {
    final hub = _hub;
    if (!hub.phoneAgentsAvailable) return const [];
    if (hub.agentRows.isEmpty) {
      try {
        await hub.refreshAgentRows();
      } catch (_) {}
    }
    final choices = <ContinueWithChoice>[];
    for (final row in hub.agentRows) {
      final descriptor = AgentCatalog.builtIn.byId(row.id);
      if (descriptor == null) continue;
      // The same agents New conversation offers: one no version of which is
      // certified for chat has nothing to continue in.
      if (!AgentCertificationMatrix.bundled.certifiedForChat(row.id)) continue;
      ContinueWithChoice blocked(ContinueWithBlock why) => ContinueWithChoice(
        key: 'agent:${row.id}',
        agentName: row.name,
        modelName: row.name,
        iconKey: row.iconKey,
        agentId: row.id,
        block: why,
      );
      switch (row.status) {
        case PhoneAgentStatus.needsInstall:
        case PhoneAgentStatus.unavailable:
          continue;
        case PhoneAgentStatus.signedOut:
          choices.add(blocked(ContinueWithBlock.needsSignIn));
          continue;
        case PhoneAgentStatus.limitReached:
          choices.add(blocked(ContinueWithBlock.limitReached));
          continue;
        case PhoneAgentStatus.needsQualification:
          choices.add(blocked(ContinueWithBlock.notReady));
          continue;
        // Android or an app update stopped the helper: reaching the folder
        // below starts it again (up to 30 s), so it is not a dead end.
        case PhoneAgentStatus.stoppedInBackground:
        case PhoneAgentStatus.ready:
          break;
      }
      final directory = source.directory;
      if (!directory.startsWith('/root/projects/') ||
          source.filesystemId == null) {
        choices.add(blocked(ContinueWithBlock.notSameProject));
        continue;
      }
      final ServerGateway gateway;
      final List<AgentModelChoice> available;
      if (_held == null) {
        hub.holdAgentHandoffTarget(directory);
        _held = directory;
      }
      try {
        gateway = await hub.agentHandoffTargetGateway(directory);
        available = await hub.agentModels(row.id);
      } catch (_) {
        choices.add(blocked(ContinueWithBlock.notReady));
        continue;
      }
      if (!gateway.capabilities.agentHandoffFiles ||
          gateway is! AgentHandoffTargetGateway) {
        choices.add(blocked(ContinueWithBlock.noRecordWriter));
        continue;
      }
      // The helper lists an agent's runtime only once it has asked the CLI
      // for its models (slow the first time): not ready yet, try again.
      if (available.isEmpty) {
        choices.add(blocked(ContinueWithBlock.notReady));
        continue;
      }
      final scope = HandoffScope(
        profileId: source.profileId,
        origin: HandoffOrigin.phoneAgent,
        directory: directory,
        workspace: gateway.workspace,
        filesystemId: source.filesystemId,
      );
      final ordered = [
        ...available.where((m) => m.isDefault),
        ...available.where((m) => !m.isDefault),
      ];
      for (final model in ordered) {
        choices.add(
          ContinueWithChoice(
            key: 'agent:${row.id}:${model.id}',
            agentName: row.name,
            modelName: model.name,
            iconKey: row.iconKey,
            agentId: row.id,
            target: HandoffTarget(
              gateway: gateway,
              scope: scope,
              model: ModelRef(
                providerID: descriptor.providerId,
                modelID: model.id,
              ),
              label: row.name,
            ),
          ),
        );
      }
    }
    return choices;
  }

  /// Lets the folder sync close the agent folder again. Called once the
  /// sheet has closed; a started conversation has its own backend by then.
  void release() {
    final held = _held;
    if (held == null) return;
    _held = null;
    _hub.releaseAgentHandoffTarget(held);
  }

  @override
  AgentHandoffController controllerFor(
    ContinueWithChoice choice, {
    required bool Function() isCurrent,
    required VoidCallback onChanged,
  }) {
    final source = connection.api;
    final target = choice.target;
    if (source == null || target == null) {
      throw const AgentHandoffFailure(HandoffFailureKind.staleScope);
    }
    return AgentHandoffController(
      source: source,
      sourceScope: _sourceScope,
      sourceSessionId: sessionId,
      target: target,
      links: AgentHandoffLinks.forPreferences(connection.store.prefs),
      isCurrent: () =>
          isCurrent() &&
          _sourceUnchanged &&
          identical(connection.api, source) &&
          connection.sessionsById.containsKey(sessionId),
      staleReason: () => !isCurrent()
          ? 'sheet closed or choice changed'
          : connection.profile?.id != _profileId
          ? 'another server was opened'
          : connection.locationRevision != _revision ||
                connection.directory != _directory ||
                connection.workspace != _workspace
          ? 'the conversation\'s project was reopened'
          : !identical(connection.api, source)
          ? 'the server connection was replaced'
          : !connection.sessionsById.containsKey(sessionId)
          ? 'the conversation left the list'
          : null,
      onChanged: onChanged,
    );
  }

  @override
  Future<void> switchModel(ContinueWithChoice choice) async {
    final target = choice.target;
    if (target == null) throw StateError('No model');
    await connection.selectModelForSession(
      sessionId,
      target.model,
      variant: target.variant.isEmpty ? null : target.variant,
    );
    final now = connection.modelForSession(sessionId);
    if (!ModelLibrary.sameModel(now, target.model)) {
      throw StateError('The model was not changed');
    }
  }

  @override
  Future<String> adopt(ContinueWithChoice choice, String navigationSessionId) {
    final target = choice.target;
    if (target == null || choice.agentId == null) {
      return Future.value(navigationSessionId);
    }
    return connection.agentHandoffAdopt(
      directory: target.scope.directory,
      agentId: choice.agentId!,
      sessionId: navigationSessionId,
    );
  }
}
