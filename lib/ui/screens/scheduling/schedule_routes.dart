import 'dart:async';

import 'package:flutter/material.dart';

import '../../../domain/models.dart' show ModelRef, SessionSelection;
import '../../../l10n/app_localizations.dart';
import '../../../scheduling/agent_connection.dart';
import '../../../scheduling/controller.dart';
import '../../../scheduling/models.dart';
import '../../../scheduling/store.dart';
import '../../../state/connection.dart';
import '../../kit/kit.dart';
import '../../widgets/phone_server_card.dart' show serverDisplayName;
import '../chat_screen.dart';
import 'scheduled_prompts_screen.dart';

bool canSchedulePrompts(ConnectionController connection) =>
    connection.capabilities.scheduledPrompts &&
    connection.profile != null &&
    !connection.profile!.transientTransport &&
    connection.directory?.isNotEmpty == true;

/// What a new schedule starts with. A new conversation takes the app's
/// defaults. An existing conversation keeps its own agent and model: the
/// editor has no model row, so a schedule must not quietly switch that
/// conversation to the app-wide default (which may not even be signed in).
({String agent, ModelRef? model}) scheduleSelectionSeed({
  required SessionSelection? sessionSelection,
  required ModelRef? defaultModel,
  required String defaultAgent,
}) {
  if (sessionSelection == null) {
    return (agent: defaultAgent, model: defaultModel);
  }
  final agent = sessionSelection.agentKnown ? sessionSelection.agent : null;
  return (
    agent: agent == null || agent.isEmpty ? defaultAgent : agent,
    model: null,
  );
}

Future<void> openScheduledPrompts(
  BuildContext context,
  ConnectionController connection, {
  String? sessionId,
}) async {
  final profile = connection.profile;
  final directory = connection.directory;
  if (profile == null || directory == null || !canSchedulePrompts(connection)) {
    return;
  }
  final controller = ScheduledPromptsController(profileId: profile.id);
  final seed = scheduleSelectionSeed(
    sessionSelection: sessionId == null
        ? null
        : connection.selectionForSession(sessionId),
    defaultModel: connection.selectedModel,
    defaultAgent: connection.selectedAgent,
  );
  final target = ScheduledPrompt(
    id: DateTime.now().microsecondsSinceEpoch.toString(),
    profileId: profile.id,
    profileFingerprint: scheduledProfileFingerprint(profile),
    prompt: '',
    projectPath: directory,
    workspace: connection.workspace,
    sessionId: sessionId,
    agent: seed.agent,
    modelProviderId: seed.model?.providerID,
    modelId: seed.model?.modelID,
    recurrence: const ScheduleRecurrence.weekdays(hour: 9),
    createdAt: DateTime.now(),
  );
  try {
    await pushKitPage<void>(
      context,
      (_) => ScheduledPromptsScreen(
        controller: controller,
        target: target,
        serverName: serverDisplayName(
          profile,
          AppLocalizations.of(context),
          among: connection.store.profiles,
        ),
        onOpenRun: (run) async {
          final schedule = controller.schedules
              .where((s) => s.id == run.scheduleId)
              .firstOrNull;
          if (schedule != null && run.sessionId != null && context.mounted) {
            await openScheduledRun(
              context,
              connection,
              schedule,
              run.sessionId!,
            );
          }
        },
      ),
    );
  } finally {
    controller.dispose();
  }
}

Future<void> openStoredScheduledPrompts(
  BuildContext context,
  ConnectionController connection,
  ScheduledPrompt target,
) async {
  final controller = ScheduledPromptsController(profileId: target.profileId);
  try {
    await pushKitPage<void>(
      context,
      (_) => ScheduledPromptsScreen(
        controller: controller,
        target: target,
        serverName: connection.profile?.id == target.profileId
            ? serverDisplayName(
                connection.profile,
                AppLocalizations.of(context),
                among: connection.store.profiles,
              )
            : null,
        onOpenRun: (run) async {
          final schedule = controller.schedules
              .where((s) => s.id == run.scheduleId)
              .firstOrNull;
          if (schedule != null && run.sessionId != null && context.mounted) {
            await openScheduledRun(
              context,
              connection,
              schedule,
              run.sessionId!,
            );
          }
        },
      ),
    );
  } finally {
    controller.dispose();
  }
}

/// Opens the conversation a scheduled run used. False when it cannot be
/// opened now (schedule or saved server changed, server unreachable), so
/// the caller can show the schedule's history instead.
Future<bool> openScheduledRun(
  BuildContext context,
  ConnectionController connection,
  ScheduledPrompt schedule,
  String sessionId,
) async {
  final profile = await connection.store.loadForWidgetAction(
    schedule.profileId,
  );
  if (profile == null ||
      !context.mounted ||
      scheduledProfileFingerprint(profile) != schedule.profileFingerprint) {
    return false;
  }
  final live = (await ScheduledPromptStore().load(
    profile.id,
  )).where((s) => s.id == schedule.id).firstOrNull;
  if (live == null ||
      live.profileFingerprint != schedule.profileFingerprint ||
      !context.mounted) {
    return false;
  }
  if (connection.profile?.id != profile.id || !connection.hasConnectedServer) {
    await connection.connect(profile);
  }
  if (!context.mounted ||
      connection.profile?.id != profile.id ||
      !connection.hasConnectedServer) {
    return false;
  }
  await connection.selectLocationForExistingSession(
    directory: schedule.projectPath,
    workspace: schedule.workspace,
  );
  final revision = connection.locationRevision;
  final history = await ScheduledPromptStore().history(
    profile.id,
    scheduleId: schedule.id,
  );
  if (!context.mounted ||
      connection.profile?.id != profile.id ||
      connection.locationRevision != revision ||
      connection.directory != schedule.projectPath ||
      connection.workspace != schedule.workspace ||
      scheduledProfileFingerprint(connection.profile!) !=
          schedule.profileFingerprint ||
      !history.any((run) => run.sessionId == sessionId)) {
    return false;
  }
  unawaited(
    pushKitPage<void>(context, (_) => ChatScreen(sessionID: sessionId)),
  );
  return true;
}
