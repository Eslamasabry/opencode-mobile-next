import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../state/connection.dart';
import '../../domain/session_command_handoff.dart';
import '../app_theme.dart';
import '../../l10n/app_localizations.dart';
import 'product_states.dart';

/// A short-lived guard, never a persisted profile or a transport credential.
class SessionNavigationScope {
  SessionNavigationScope(ConnectionController controller)
    : profileID = controller.profile?.id,
      connectionRevision = controller.connectionRevision,
      revision = controller.locationRevision;

  final String? profileID;
  final int connectionRevision;
  final int revision;

  bool matches(ConnectionController controller) =>
      profileID != null &&
      controller.profile?.id == profileID &&
      controller.connectionRevision == connectionRevision &&
      controller.locationRevision == revision;

  void check(ConnectionController controller) {
    if (!matches(controller)) {
      throw StateError('The session location changed. Return and try again.');
    }
  }
}

/// Previews a supported resume command, or metadata when attach is unavailable.
/// Clipboard writes revalidate the same server location and session directory.
Future<void> showSessionHandoff(
  BuildContext context, {
  required ConnectionController controller,
  required String sessionID,
  required String? projectID,
}) async {
  final scope = SessionNavigationScope(controller);
  final l10n = lookupAppLocalizations(Localizations.localeOf(context));
  // Metadata fallback exports opaque identifiers. A supported command previews
  // its validated server address and session directory separately below.
  final identifier = RegExp(r'^[A-Za-z0-9_-]+$');
  if (!identifier.hasMatch(sessionID) ||
      projectID == null ||
      !identifier.hasMatch(projectID)) {
    showProductError(
      context,
      'A safe project reference is unavailable. Return and refresh the session.',
    );
    return;
  }
  final reference = const JsonEncoder.withIndent('  ').convert({
    'type': 'OpenCode session metadata reference',
    'sessionID': sessionID,
    'projectID': projectID,
  });
  try {
    scope.check(controller);
    final initialRepository = await controller.prepareActionRepository();
    scope.check(controller);
    if (initialRepository == null) throw StateError('Session unavailable');
    final initialSession = await initialRepository.getSessionDetails(sessionID);
    scope.check(controller);
    if (initialSession.id != sessionID ||
        initialSession.projectID != projectID) {
      throw StateError('Session project changed');
    }
    final handoff = initialRepository is SessionCommandHandoffGateway
        ? (initialRepository as SessionCommandHandoffGateway)
              .createSessionCommandHandoff(
                sessionID: sessionID,
                directory: initialSession.directory,
                workspaceID: initialSession.workspaceID,
                username: controller.profile?.username ?? '',
              )
        : null;
    final command = handoff?.command;
    if (!context.mounted) return;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(
          command == null ? l10n.handoffTitle : l10n.handoffCommandTitle,
        ),
        content: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                command == null
                    ? l10n.handoffCommandUnavailable
                    : l10n.handoffCommandDisclosure,
              ),
              if (command == null) ...[
                const SizedBox(height: 12),
                Text(l10n.handoffDisclosure),
              ],
              const SizedBox(height: 16),
              Text(
                command ?? reference,
                style: Theme.of(context).textTheme.bodySmall?.copyWith(
                  fontFamily: AppTheme.monoFamily,
                ),
              ),
            ],
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: Text(MaterialLocalizations.of(context).cancelButtonLabel),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: Text(
              command == null ? l10n.handoffCopy : l10n.handoffCopyCommand,
            ),
          ),
        ],
      ),
    );
    if (confirmed != true || !context.mounted) return;
    scope.check(controller);
    final repository = await controller.prepareActionRepository();
    scope.check(controller);
    if (repository == null) throw StateError('Session unavailable');
    final session = await repository.getSessionDetails(sessionID);
    scope.check(controller);
    if (session.id != sessionID ||
        session.projectID != projectID ||
        session.directory != initialSession.directory ||
        session.workspaceID != initialSession.workspaceID) {
      throw StateError('Session location changed');
    }
    if (command != null) {
      if (repository is! SessionCommandHandoffGateway ||
          (repository as SessionCommandHandoffGateway)
                  .createSessionCommandHandoff(
                    sessionID: sessionID,
                    directory: session.directory,
                    workspaceID: session.workspaceID,
                    username: controller.profile?.username ?? '',
                  )
                  .command !=
              command) {
        throw StateError('Server command changed');
      }
    }
    try {
      await Clipboard.setData(ClipboardData(text: command ?? reference));
    } catch (_) {
      if (context.mounted) {
        showProductError(context, l10n.handoffCopyFailed);
      }
      return;
    }
    if (context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            command == null ? l10n.handoffCopied : l10n.handoffCommandCopied,
          ),
        ),
      );
    }
  } catch (_) {
    if (context.mounted) {
      showProductError(
        context,
        'Session unavailable or location changed. Return and try again.',
      );
    }
  }
}
