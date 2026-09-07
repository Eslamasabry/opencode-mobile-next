import 'package:flutter/material.dart';

import '../../../api/models.dart' show ApiException;
import '../../../api2/models.dart' show Api2FormInfo;
import '../../../state/connection.dart';
import '../../widgets/form_renderer.dart';
import '../../widgets/request_routes.dart';

/// Presents a pending form through the shared [FormRenderer] presenter and
/// routes its reply/cancel through the connection's [FormGateway] state,
/// applying the locked error contract (design doc §2):
///
/// - a 400 `FormInvalidAnswerError` (or any other failure) rethrows into the
///   renderer, which keeps the form open with the message in its pinned
///   error banner;
/// - a 409 `FormAlreadySettledError` toasts "Already answered elsewhere"
///   and lets the form close (the connection already settled it locally).
Future<void> presentConnectionForm(
  BuildContext context,
  ConnectionController connection,
  Api2FormInfo form,
) async {
  final scope = connection.returnBriefScope;
  bool current() =>
      connection.returnBriefScope == scope &&
      identical(connection.forms[form.id], form);
  if (!current()) return;
  final routes = RequestRoutes(changes: connection, isPending: current);
  final messenger = ScaffoldMessenger.maybeOf(context);
  bool settledElsewhere(Object error) =>
      error is ApiException &&
      (error.errorTag == 'FormAlreadySettledError' ||
          error.errorTag == 'FormNotFoundError');
  void toastSettled() {
    messenger?.showSnackBar(
      const SnackBar(content: Text('Already answered elsewhere')),
    );
  }

  try {
    await presentForm(
      context,
      form: form,
      routes: routes,
      onSubmit: (answer) async {
        if (!current()) {
          throw StateError(
            'The form or project changed. Reopen the current request.',
          );
        }
        try {
          await connection.replyForm(form.id, answer);
        } catch (error) {
          if (settledElsewhere(error)) {
            toastSettled();
            return;
          }
          rethrow;
        }
      },
      onCancel: () async {
        if (!current()) {
          throw StateError(
            'The form or project changed. Reopen the current request.',
          );
        }
        try {
          await connection.cancelForm(form.id);
        } catch (error) {
          if (settledElsewhere(error)) {
            toastSettled();
            return;
          }
          rethrow;
        }
      },
    );
  } finally {
    routes.close();
  }
}
