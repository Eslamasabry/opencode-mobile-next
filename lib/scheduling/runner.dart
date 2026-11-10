import 'dart:async';

import '../domain/models.dart';
import '../domain/server_gateway.dart' show SessionSelectionGateway;
import '../domain/server_trust.dart'
    show
        serverCertificateChangedTag,
        serverCertificateInvalidTag,
        serverCleartextConsentTag;
import '../state/profiles.dart';
import 'agent_connection.dart';
import 'models.dart';
import 'recurrence.dart';

typedef ScheduledConnectionFactory =
    Future<ScheduledAgentConnection> Function({
      required ServerProfile profile,
      required String projectPath,
      String? workspace,
      required int deadlineEpochMs,
    });

/// One claimed occurrence, with no reconnect/resend loop. The native worker
/// serializes claims and supplies a deadline that includes engine startup.
class ScheduledPromptRunner {
  ScheduledPromptRunner({
    required this.loadProfile,
    ScheduledConnectionFactory? openConnection,
    DateTime Function()? now,
    Future<void> Function(Duration)? delay,
  }) : openConnection = openConnection ?? ScheduledAgentConnection.open,
       now = now ?? DateTime.now,
       delay = delay ?? Future<void>.delayed;

  final Future<ServerProfile?> Function(String) loadProfile;
  final ScheduledConnectionFactory openConnection;
  final DateTime Function() now;
  final Future<void> Function(Duration) delay;

  Future<ScheduledPromptRun> execute(
    ScheduledPrompt schedule, {
    required int deadlineEpochMs,
    required Future<bool> Function() isCurrent,
    Future<void> Function(String sessionId)? onSessionCreated,
  }) async {
    final startedAt = now();
    final scheduledAt = schedule.nextRunAt ?? startedAt;
    final late =
        startedAt.difference(scheduledAt) > const Duration(minutes: 15);
    ScheduledAgentConnection? connection;
    String? sessionId = schedule.sessionId;
    var dispatched = false;
    var accepted = false;

    ScheduledPromptRun result(
      ScheduledPromptRunStatus status, [
      String? error,
    ]) => ScheduledPromptRun(
      id: scheduledOccurrenceId(schedule, scheduledAt),
      scheduleId: schedule.id,
      scheduledAt: scheduledAt,
      startedAt: startedAt,
      finishedAt: now(),
      status: status,
      sessionId: sessionId,
      late: late,
      errorCode: error,
    );

    Duration remaining() {
      final ms = deadlineEpochMs - now().millisecondsSinceEpoch;
      if (ms <= 0) throw const ScheduledRunFailure('timeout');
      return Duration(milliseconds: ms);
    }

    Future<void> guard() async {
      remaining();
      if (!await isCurrent().timeout(remaining())) {
        throw const ScheduledRunFailure('stale_target');
      }
      remaining();
    }

    Future<T> read<T>(Future<T> Function() action) async {
      await guard();
      final value = await action().timeout(remaining());
      await guard();
      return value;
    }

    Future<void> checkProfile() async {
      final current = await read(() => loadProfile(schedule.profileId));
      if (current == null ||
          scheduledProfileFingerprint(current) != schedule.profileFingerprint) {
        throw const ScheduledRunFailure('stale_target');
      }
    }

    Future<void> recordSession() async {
      if (onSessionCreated == null) return;
      await guard();
      // Persist routing before crossing the prompt boundary. A lost isolate
      // can then retain the conversation in its interrupted run receipt.
      await onSessionCreated(sessionId!).timeout(remaining());
      await guard();
    }

    try {
      schedule.validate();
      if (!schedule.enabled) throw const ScheduledRunFailure('stale_target');
      final profile = await read(() => loadProfile(schedule.profileId));
      if (profile == null ||
          scheduledProfileFingerprint(profile) != schedule.profileFingerprint) {
        throw const ScheduledRunFailure('stale_target');
      }
      // A timed-out open may eventually return. Close that late connection;
      // the native lease also has an independent monotonic watchdog.
      var abandonedOpen = false;
      final opening =
          openConnection(
            profile: profile,
            projectPath: schedule.projectPath,
            workspace: schedule.workspace,
            deadlineEpochMs: deadlineEpochMs,
          ).then((value) async {
            if (abandonedOpen) await value.close();
            return value;
          });
      try {
        connection = await opening.timeout(remaining());
      } catch (_) {
        abandonedOpen = true;
        rethrow;
      }
      final gateway = connection.gateway;
      final readyUntil = now().add(const Duration(seconds: 90));
      while (true) {
        await guard();
        var healthy = false;
        try {
          healthy = (await gateway.health().timeout(remaining())).healthy;
        } on ApiException catch (error) {
          if (error.unauthorized || !connection.startedPhoneServer) rethrow;
        } on TimeoutException {
          rethrow;
        } catch (_) {
          if (!connection.startedPhoneServer) rethrow;
        }
        await guard();
        if (healthy) break;
        if (!connection.startedPhoneServer || !now().isBefore(readyUntil)) {
          throw const ScheduledRunFailure('unavailable');
        }
        await delay(const Duration(seconds: 2)).timeout(remaining());
      }
      if (!gateway.capabilities.scheduledPrompts) {
        throw const ScheduledRunFailure('unsupported');
      }
      final selectedModel = schedule.modelProviderId == null
          ? null
          : ModelRef(
              providerID: schedule.modelProviderId!,
              modelID: schedule.modelId!,
            );
      var beforeMessages = <String>{};
      if (sessionId != null) {
        final session = await read(() => gateway.session(sessionId!));
        if (session.directory != schedule.projectPath) {
          throw const ScheduledRunFailure('wrong_project');
        }
        await recordSession();
        beforeMessages = (await read(
          () => gateway.messages(sessionId!),
        )).map((message) => message.info.id).toSet();
        final statuses = await read(gateway.sessionStatuses);
        if (statuses[sessionId] != null && statuses[sessionId] != 'idle') {
          return result(ScheduledPromptRunStatus.skipped, 'busy');
        }
      } else {
        await checkProfile();
        // Session creation itself is a mutation and is never repeated after an
        // ambiguous response, even when the prompt has not been sent yet.
        dispatched = true;
        sessionId =
            (await (gateway is SessionSelectionGateway
                        ? (gateway as SessionSelectionGateway)
                              .createSelectedSession(
                                SessionSelection(
                                  model: selectedModel,
                                  agent: schedule.agent,
                                ),
                              )
                        : gateway.createSession())
                    .timeout(remaining()))
                .id;
        dispatched = false;
        await recordSession();
      }
      await checkProfile();
      // Hydrate socket gateway caches again immediately before the final idle
      // read. Server protocols have no atomic send-only-if-idle operation.
      final session = await read(() => gateway.session(sessionId!));
      final statuses = await read(gateway.sessionStatuses);
      if (statuses[sessionId] != null && statuses[sessionId] != 'idle') {
        return result(ScheduledPromptRunStatus.skipped, 'busy');
      }
      if (schedule.sessionId != null && gateway is SessionSelectionGateway) {
        final selectionGateway = gateway as SessionSelectionGateway;
        // These APIs patch only the named field. Do not write back a whole
        // stale session, reset unspecified choices, or undo concurrent edits.
        Future<void> select(Future<void> Function() action) async {
          await checkProfile();
          await read(() => gateway.session(sessionId!));
          final currentStatuses = await read(gateway.sessionStatuses);
          if (currentStatuses[sessionId] != null &&
              currentStatuses[sessionId] != 'idle') {
            throw const ScheduledRunFailure('busy');
          }
          await guard();
          dispatched = true;
          await action().timeout(remaining());
          dispatched = false;
        }

        final currentAgent = session.selection?.agent ?? session.agent;
        if (schedule.agent != null && schedule.agent != currentAgent) {
          await select(
            () => selectionGateway.setSessionAgent(sessionId!, schedule.agent!),
          );
        }
        final currentModel = (await read(
          () => gateway.session(sessionId!),
        )).selection?.model;
        if (selectedModel != null &&
            (currentModel?.providerID != selectedModel.providerID ||
                currentModel?.normalized.modelID !=
                    selectedModel.normalized.modelID)) {
          await select(
            () =>
                selectionGateway.setSessionModel(sessionId!, selectedModel, ''),
          );
        }
        // Applying a selection is not an idle reservation: a person may have
        // started a turn in the meantime. Never steer or interrupt that turn.
        await read(() => gateway.session(sessionId!));
        final selectedStatuses = await read(gateway.sessionStatuses);
        if (selectedStatuses[sessionId] != null &&
            selectedStatuses[sessionId] != 'idle') {
          return result(ScheduledPromptRunStatus.skipped, 'busy');
        }
      }
      await guard();
      dispatched = true;
      await gateway
          .promptAsync(
            sessionId,
            text: schedule.prompt,
            agent: schedule.agent,
            model: selectedModel,
          )
          .timeout(remaining());
      accepted = true;
      // Remote servers own their run after acknowledgement. A runtime started
      // just for this occurrence must finish before its bounded lease releases.
      if (!connection.startedPhoneServer) {
        return result(ScheduledPromptRunStatus.ran);
      }
      while (true) {
        final session = await read(() => gateway.session(sessionId!));
        final messages = await read(() => gateway.messages(sessionId!));
        final statuses = await read(gateway.sessionStatuses);
        final replies = messages.where(
          (m) =>
              m.info.role == 'assistant' && !beforeMessages.contains(m.info.id),
        );
        if (session.lastRunFailed ||
            replies.any((m) => m.info.errorText != null)) {
          return result(ScheduledPromptRunStatus.failed, 'agent_failed');
        }
        final permissions = await read(gateway.pendingPermissions);
        if (permissions.any((p) => p.sessionID == sessionId)) {
          return result(ScheduledPromptRunStatus.failed, 'needs_you');
        }
        if (gateway.capabilities.forms &&
            (await read(() => gateway.sessionForms(sessionId!))).isNotEmpty) {
          return result(ScheduledPromptRunStatus.failed, 'needs_you');
        }
        if (replies.any((message) => message.info.time?.isDone == true) &&
            (statuses[sessionId] == null || statuses[sessionId] == 'idle')) {
          return result(ScheduledPromptRunStatus.ran);
        }
        await delay(const Duration(seconds: 2)).timeout(remaining());
      }
    } on ScheduledRunFailure catch (error) {
      return result(
        dispatched
            ? ScheduledPromptRunStatus.failed
            : ScheduledPromptRunStatus.skipped,
        dispatched && !accepted ? 'uncertain_delivery' : error.reason,
      );
    } on TimeoutException {
      return result(
        ScheduledPromptRunStatus.failed,
        dispatched && !accepted ? 'uncertain_delivery' : 'timeout',
      );
    } on ApiException catch (error) {
      return result(
        ScheduledPromptRunStatus.failed,
        error.unauthorized
            ? 'auth_required'
            : dispatched && !accepted
            ? 'uncertain_delivery'
            : switch (error.errorTag) {
                serverCertificateChangedTag => 'certificate_changed',
                serverCertificateInvalidTag => 'certificate_invalid',
                serverCleartextConsentTag => 'cleartext_unconfirmed',
                _ => 'unavailable',
              },
      );
    } catch (_) {
      return result(
        ScheduledPromptRunStatus.failed,
        dispatched && !accepted ? 'uncertain_delivery' : 'unavailable',
      );
    } finally {
      try {
        await connection?.close().timeout(const Duration(seconds: 3));
      } catch (_) {
        // Native watchdog cleanup remains authoritative after isolate failure.
      }
    }
  }
}
