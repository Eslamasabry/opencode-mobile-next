import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../state/connection.dart';
import '../../l10n/app_localizations.dart';
import 'product_states.dart';

/// A short-lived guard, never a persisted profile or a transport credential.
class SessionNavigationScope {
  SessionNavigationScope(ConnectionController controller)
    : profileID = controller.profile?.id,
      revision = controller.locationRevision;

  final String? profileID;
  final int revision;

  bool matches(ConnectionController controller) =>
      profileID != null &&
      controller.profile?.id == profileID &&
      controller.locationRevision == revision;

  void check(ConnectionController controller) {
    if (!matches(controller)) {
      throw StateError('The session location changed. Return and try again.');
    }
  }
}

/// Copies metadata only. This is deliberately not a CLI command or deep link.
Future<void> showSessionHandoff(
  BuildContext context, {
  required ConnectionController controller,
  required String sessionID,
  required String? projectID,
}) async {
  final scope = SessionNavigationScope(controller);
  final l10n = lookupAppLocalizations(Localizations.localeOf(context));
  // Only opaque identifiers are exported; never names, paths or server URLs.
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
  final confirmed = await showDialog<bool>(
    context: context,
    builder: (context) => AlertDialog(
      title: Text(l10n.handoffTitle),
      content: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(l10n.handoffDisclosure),
            const SizedBox(height: 16),
            Text(reference),
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
          child: Text(l10n.handoffCopy),
        ),
      ],
    ),
  );
  if (confirmed != true || !context.mounted) return;
  try {
    scope.check(controller);
    final repository = await controller.prepareActionRepository();
    scope.check(controller);
    if (repository == null) {
      throw StateError('OpenCode is reconnecting. Try again.');
    }
    final session = await repository.getSessionDetails(sessionID);
    scope.check(controller);
    if (session.id != sessionID || session.projectID != projectID) {
      throw StateError(
        'The session project changed. Return and refresh the session.',
      );
    }
    await Clipboard.setData(ClipboardData(text: reference));
    if (context.mounted) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(l10n.handoffCopied)));
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
