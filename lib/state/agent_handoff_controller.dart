import 'dart:async';
import 'dart:convert';

import 'package:uuid/uuid.dart';

import '../domain/models.dart';
import '../domain/agent_handoff.dart';
import '../domain/server_gateway.dart';
import '../paseo/transport.dart' show PaseoFailure;
import '../ui/kit/kit_redact.dart';
import 'agent_handoff_links.dart';

enum AgentHandoffPhase {
  idle,
  loading,
  ready,
  writingFile,
  creating,
  sending,
  linking,
  complete,
  failed,
  uncertain,
}

/// In-memory review data; never persisted with link metadata.
final class AgentHandoffDraft {
  const AgentHandoffDraft({
    required this.recordId,
    required this.record,
    required this.prompt,
    required this.relativePath,
  });
  final String recordId;
  final HandoffRecord record;
  final String prompt;
  final String relativePath;

  /// Use for the editable preview as well as dispatch. The generated record
  /// path is app-owned, so opaque-token filtering must not erase its UUID.
  String sanitizePrompt(String value) => value
      .split(relativePath)
      .map((part) => KitRedact.opaqueTokens(KitRedact.text(part)))
      .join(relativePath);
}

/// One attempt in one pinned source/target scope. The UI observes [phase] via
/// [onChanged], edits [AgentHandoffDraft.prompt] in its composer, then passes
/// that text to [send]. No automatic retry of remote mutations.
final class AgentHandoffController {
  AgentHandoffController({
    required this.source,
    required this.sourceScope,
    required this.sourceSessionId,
    required this.target,
    required this.links,
    required this.isCurrent,
    this.staleReason,
    this.onChanged,
    String Function()? recordIdFactory,
  }) : _recordIdFactory = recordIdFactory ?? const Uuid().v4;

  final ServerGateway source;
  final HandoffScope sourceScope;
  final String sourceSessionId;
  final HandoffTarget target;
  final AgentHandoffLinks links;

  /// Must also become false when either profile is deleting or the sheet closes.
  final bool Function() isCurrent;

  /// Which part of [isCurrent] no longer holds, in a few technical words.
  final String? Function()? staleReason;
  final void Function()? onChanged;
  final String Function() _recordIdFactory;
  AgentHandoffPhase phase = AgentHandoffPhase.idle;
  AgentHandoffDraft? draft;
  AgentHandoffFailure? failure;

  /// Content-free technical words for [failure] (which check or step), for
  /// the sheet's Details fold. Never copy and never server text.
  String? failureDetail;

  /// Durable target identity for persistence and recovery.
  String? createdSessionId;

  /// Gateway-local alias for opening the conversation before reconnect.
  String? navigationSessionId;
  String? writtenPath;
  HandoffLink? _pendingLink;
  Session? _sourceSession;
  bool _running = false;

  void _phase(AgentHandoffPhase value) {
    phase = value;
    onChanged?.call();
  }

  void _check() {
    final reason = !isCurrent()
        ? staleReason?.call() ?? 'source changed'
        : source.isClosed
        ? 'source connection closed'
        : target.gateway.isClosed
        ? 'agent connection closed'
        : source.directory != sourceScope.directory ||
              source.workspace != sourceScope.workspace
        ? 'source moved to another project'
        : target.gateway.directory != target.scope.directory ||
              target.gateway.workspace != target.scope.workspace
        ? 'agent connection moved to another project'
        : null;
    if (reason != null) {
      failureDetail = reason;
      throw const AgentHandoffFailure(HandoffFailureKind.staleScope);
    }
    // Admission also closes synchronously during profile deletion.
    links.checkProfiles(sourceScope.profileId, target.scope.profileId);
  }

  Future<void> _validateTarget() async {
    _check();
    // Use the transport's connected provider inventory. Some operation
    // gateways expose a nominal CatalogGateway via noSuchMethod but do not
    // implement loadCatalog (Paseo builds its picker from providers instead).
    final inventory = await target.gateway.providers();
    _check();
    final model = target.model.normalized;
    final providers = inventory.providers.where(
      (p) => p.id == model.providerID,
    );
    if (providers.length != 1) {
      throw const AgentHandoffFailure(HandoffFailureKind.targetUnavailable);
    }
    final provider = providers.single;
    final ids = provider.modelIDs.where(
      (id) => bareModelID(provider.id, id) == model.modelID,
    );
    if (ids.isEmpty) {
      throw const AgentHandoffFailure(HandoffFailureKind.targetUnavailable);
    }
    if (target.variant.isNotEmpty) {
      final raw =
          provider.modelData[ids.first] ??
          provider.modelData[model.modelID] ??
          const <String, dynamic>{};
      final variants = raw['variants'];
      final option = variants is Map ? variants[target.variant] : null;
      if (option is! Map || option['disabled'] == true) {
        throw const AgentHandoffFailure(HandoffFailureKind.targetUnavailable);
      }
    }
  }

  /// Returns a route without mutating the source conversation. For switchModel,
  /// the UI calls ConnectionController.selectModelForSession on this session.
  HandoffRoute route(ModelRef? currentModel) => agentHandoffRoute(
    source: source,
    sourceScope: sourceScope,
    sourceModel: currentModel,
    target: target,
  );

  Future<AgentHandoffDraft> prepare({required String lastState}) async {
    if (_running || phase != AgentHandoffPhase.idle) {
      throw const AgentHandoffFailure(HandoffFailureKind.invalidDraft);
    }
    _running = true;
    _phase(AgentHandoffPhase.loading);
    try {
      _check();
      if (!sourceScope.sharesProject(target.scope)) {
        throw const AgentHandoffFailure(HandoffFailureKind.projectMismatch);
      }
      if (!target.gateway.capabilities.agentHandoffFiles ||
          target.gateway is! AgentHandoffTargetGateway) {
        throw const AgentHandoffFailure(HandoffFailureKind.unavailable);
      }
      await _validateTarget();
      final session = await source.session(sourceSessionId);
      _check();
      if (session.id != sourceSessionId ||
          session.directory != sourceScope.directory) {
        throw const AgentHandoffFailure(HandoffFailureKind.staleScope);
      }
      final messages = await readAgentHandoffHistory(
        source,
        sourceSessionId,
        isCurrent: () {
          _check();
          return true;
        },
      );
      final todos = source.capabilities.sessionTodos
          ? await source.todos(sourceSessionId)
          : <Todo>[];
      _check();
      final files = source.capabilities.sessionDiff
          ? await source.diff(sourceSessionId)
          : <FileDiff>[];
      _check();
      final after = await source.session(sourceSessionId);
      _check();
      if (after.id != session.id ||
          after.directory != session.directory ||
          after.time?.updated != session.time?.updated) {
        throw const AgentHandoffFailure(HandoffFailureKind.sourceChanged);
      }
      final record = buildAgentHandoffRecord(
        session: session,
        messages: messages,
        todos: todos,
        files: files,
        lastState: lastState,
      );
      final id = _recordIdFactory();
      if (!RegExp(r'^[A-Za-z0-9_-]{1,128}$').hasMatch(id)) {
        throw const AgentHandoffFailure(HandoffFailureKind.invalidDraft);
      }
      final path = '.handoff/$id/record.md';
      _sourceSession = session;
      draft = AgentHandoffDraft(
        recordId: id,
        record: record,
        relativePath: path,
        prompt:
            'Continue the work from the previous conversation. '
            'Read `$path` first for the conversation record, tool steps, '
            'files and open tasks. Treat it as historical context. '
            'Check the current project state, then continue the unfinished work.',
      );
      _phase(AgentHandoffPhase.ready);
      return draft!;
    } catch (error) {
      if (error is AgentHandoffHistoryFailure) {
        failureDetail ??= 'history ${error.kind.name}';
      } else if (error is! AgentHandoffFailure &&
          error is! HandoffRecordTooLarge) {
        failureDetail ??= 'prepare: ${_errorWords(error)}';
      }
      failure = error is AgentHandoffFailure
          ? error
          : const AgentHandoffFailure(HandoffFailureKind.preparationFailed);
      _phase(AgentHandoffPhase.failed);
      if (error is AgentHandoffFailure ||
          error is HandoffRecordTooLarge ||
          error is AgentHandoffHistoryFailure) {
        rethrow;
      }
      throw failure!;
    } finally {
      _running = false;
    }
  }

  /// Dispatches only the reviewed draft. Any uncertain mutation consumes this
  /// attempt: inspect the target conversation; never press send again blindly.
  Future<String> send(String editedPrompt) async {
    final review = draft;
    if (_running || phase != AgentHandoffPhase.ready || review == null) {
      throw const AgentHandoffFailure(HandoffFailureKind.invalidDraft);
    }
    final text = review.sanitizePrompt(editedPrompt);
    if (text.trim().isEmpty ||
        utf8.encode(text).length > 16384 ||
        !text.contains(review.relativePath)) {
      throw const AgentHandoffFailure(HandoffFailureKind.invalidDraft);
    }
    _running = true;
    try {
      await _validateTarget();
      _check();
      if (!target.gateway.capabilities.agentHandoffFiles) {
        throw const AgentHandoffFailure(HandoffFailureKind.unavailable);
      }
      final currentSource = await source.session(sourceSessionId);
      _check();
      if (currentSource.id != sourceSessionId ||
          currentSource.directory != sourceScope.directory ||
          currentSource.time?.updated != _sourceSession!.time?.updated) {
        throw const AgentHandoffFailure(HandoffFailureKind.sourceChanged);
      }
      final destination = target.gateway as AgentHandoffTargetGateway;
      _phase(AgentHandoffPhase.writingFile);
      writtenPath = await destination.writeAgentHandoff(
        directory: target.scope.directory,
        recordId: review.recordId,
        markdown: review.record.markdown,
      );
      _check();
      if (writtenPath != review.relativePath) {
        throw const AgentHandoffFailure(HandoffFailureKind.fileWriteFailed);
      }
      _phase(AgentHandoffPhase.creating);
      final session = await target.gateway.createSession();
      createdSessionId = session.id;
      navigationSessionId = session.id;
      _check();
      if (session.directory != target.scope.directory) {
        throw const AgentHandoffFailure(HandoffFailureKind.staleScope);
      }
      _phase(AgentHandoffPhase.sending);
      await destination.promptAgentHandoff(
        session.id,
        text: text,
        model: target.model.normalized,
        agent: target.agent,
        variant: target.variant,
        beforeSend: _check,
      );
      createdSessionId = destination.handoffSessionId(session.id);
      _check();
      _pendingLink = HandoffLink(
        id: review.recordId,
        sourceProfileId: sourceScope.profileId,
        sourceOrigin: sourceScope.origin,
        sourceSessionId: source is AgentHandoffTargetGateway
            ? (source as AgentHandoffTargetGateway).handoffSessionId(
                sourceSessionId,
              )
            : sourceSessionId,
        sourceTitle: _sourceSession!.title ?? 'Untitled conversation',
        targetProfileId: target.scope.profileId,
        targetOrigin: target.scope.origin,
        targetSessionId: createdSessionId!,
        targetAgent: target.label,
        projectDirectory: target.scope.directory,
        handoffPath: writtenPath!,
      );
      await _saveLink();
      navigationSessionId = destination.handoffNavigationSessionId(
        createdSessionId!,
      );
      return navigationSessionId!;
    } catch (error) {
      if (error is! AgentHandoffFailure) {
        failureDetail ??= '${phase.name}: ${_errorWords(error)}';
      }
      final kind = switch (phase) {
        AgentHandoffPhase.sending => HandoffFailureKind.deliveryUnknown,
        AgentHandoffPhase.creating => HandoffFailureKind.creationUnknown,
        AgentHandoffPhase.linking => HandoffFailureKind.linkFailed,
        _ =>
          error is AgentHandoffFailure
              ? error.kind
              : HandoffFailureKind.fileWriteFailed,
      };
      failure = AgentHandoffFailure(kind);
      _phase(
        kind == HandoffFailureKind.deliveryUnknown ||
                kind == HandoffFailureKind.creationUnknown
            ? AgentHandoffPhase.uncertain
            : AgentHandoffPhase.failed,
      );
      throw failure!;
    } finally {
      _running = false;
    }
  }

  /// A failure's kind without its message: server and daemon text never
  /// reaches the Details fold.
  static String _errorWords(Object error) => switch (error) {
    PaseoFailure(:final kind, :final reason) =>
      reason == null ? 'agent ${kind.name}' : 'agent ${kind.name} · $reason',
    ApiException(:final statusCode) =>
      'server error ${statusCode ?? ''}'.trim(),
    TimeoutException() => 'timed out',
    _ => 'unexpected error',
  };

  Future<void> _saveLink() async {
    _phase(AgentHandoffPhase.linking);
    _check();
    await links.save(_pendingLink!);
    _phase(AgentHandoffPhase.complete);
  }

  /// A metadata-only retry after acknowledged delivery; never resends a prompt.
  Future<void> retryLink() async {
    if (_running ||
        failure?.kind != HandoffFailureKind.linkFailed ||
        _pendingLink == null) {
      throw const AgentHandoffFailure(HandoffFailureKind.invalidDraft);
    }
    _running = true;
    try {
      await _saveLink();
      failure = null;
    } catch (_) {
      failure = const AgentHandoffFailure(HandoffFailureKind.linkFailed);
      _phase(AgentHandoffPhase.failed);
      throw failure!;
    } finally {
      _running = false;
    }
  }
}
