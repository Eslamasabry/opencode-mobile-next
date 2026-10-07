import 'dart:async';

import 'package:clock/clock.dart';
import 'package:flutter/material.dart';

import '../../../api/models.dart' show ApiException;
import '../../../domain/form_request.dart';
import '../../../l10n/app_localizations.dart';
import '../../../state/connection.dart';
import '../../kit/kit_dialog.dart';
import '../../kit/kit_needs_you.dart';
import '../../kit/kit_receipt.dart';
import '../../kit/kit_request_card.dart';
import '../../widgets/form_renderer.dart';
import '../../widgets/product_states.dart' show productErrorText;
import '../../widgets/request_routes.dart';

AppLocalizations _chatL10n(BuildContext context) =>
    lookupAppLocalizations(Localizations.localeOf(context));

/// Answers a form's card shows while they are on their way (STATE-10), or
/// the server's refusal once they were not accepted.
@immutable
class FormAnswerReceipt {
  const FormAnswerReceipt({required this.since, this.error});

  final DateTime since;

  /// The refusal in words; null while the answers are on their way.
  final String? error;

  bool get failed => error != null;
}

/// The one ledger of form answers in flight, keyed by captured identity: the form's
/// sheet writes it, the form's card in the conversation reads it, so the
/// receipt shows where the request lives (K2 §4.8).
class FormAnswerReceipts extends ChangeNotifier {
  FormAnswerReceipts._();

  static final instance = FormAnswerReceipts._();

  final Map<String, FormAnswerReceipt> _receipts = {};

  FormAnswerReceipt? receiptFor(String requestKey) => _receipts[requestKey];

  void _set(String requestKey, FormAnswerReceipt? receipt) {
    if (receipt == null) {
      if (_receipts.remove(requestKey) == null) return;
    } else {
      _receipts[requestKey] = receipt;
    }
    notifyListeners();
  }
}

/// One captured request uses the same card and receipt in the list and chat
/// ([inList] under a Conversations row, placed as the list places it).
Widget capturedFormRequestCard(
  BuildContext context,
  ConnectionController connection,
  CapturedFormRequest request, {
  Key? key,
  Key? answerKey,
  String? who,
  DateTime? since,
  bool secondary = false,
  bool inList = false,
  VoidCallback? onAnswer,
}) => ListenableBuilder(
  key: key,
  listenable: FormAnswerReceipts.instance,
  builder: (cardContext, _) {
    final l10n = _chatL10n(cardContext);
    final form = request.form;
    final title = form.title ?? l10n.chatUiInputRequested;
    final sent = FormAnswerReceipts.instance.receiptFor(request.identity.key);
    final sending = sent != null && !sent.failed;
    return KitRequestCard.ask(
      kind: KitRequestKind.form,
      title: title,
      who: who ?? l10n.chatRequestWho,
      reason: KitNeedsYouReason.decision,
      ifIgnored: l10n.chatRequestIfIgnored,
      announcement: l10n.chatUiQuestionLabel(title),
      since: since,
      detail: l10n.chatUiQuestionCount(form.fields.length),
      phase: sending ? KitRequestPhase.sending : KitRequestPhase.waiting,
      answer: sending ? l10n.kitRequestSendAnswers : null,
      receipt: sent == null
          ? null
          : sent.failed
          ? KitReceipt(state: KitReceiptState.refused, reason: sent.error)
          : KitReceipt(state: KitReceiptState.sending, since: sent.since),
      answers: KitRequestInSheet(
        key:
            answerKey ??
            ValueKey('form-request-answer-${request.identity.key}'),
        secondary: secondary,
      ),
      onDetails:
          onAnswer ??
          () => unawaited(presentCapturedForm(context, connection, request)),
      inList: inList,
    );
  },
);

/// Presents a pending form through the shared form presenter and routes its
/// reply/cancel through the connection's form state, applying the locked
/// error contract (design doc §2):
///
/// - a 400 `FormInvalidAnswerError` (or any other failure) rethrows into the
///   form, which stays open with the message in its error notice, and the
///   card in the conversation shows the refusal;
/// - a 409 `FormAlreadySettledError` closes the form and says, in one alert,
///   that it was answered on another device (nothing was sent from here).
///
/// Draft carry (P7.1): answers are kept per form until they are sent or the
/// form is dismissed, so swipe, back and reopening bring them back; with the
/// connection's profile, typed answers also survive a restart.
Future<void> presentConnectionForm(
  BuildContext context,
  ConnectionController connection,
  Api2FormInfo form,
) async {
  final captured = connection.formRequestForForm(form);
  if (captured == null) return;
  await presentCapturedForm(context, connection, captured);
}

Future<void> presentCapturedForm(
  BuildContext context,
  ConnectionController connection,
  CapturedFormRequest captured,
) async {
  final current = captured.isPending;
  if (!current()) return;
  final routes = RequestRoutes(changes: connection, isPending: current);
  final receipts = FormAnswerReceipts.instance;
  final requestKey = captured.identity.key;
  final profileId = captured.identity.profileID;
  var settledElsewhere = false;
  bool answeredElsewhere(Object error) =>
      error is ApiException &&
      (error.errorTag == 'FormAlreadySettledError' ||
          error.errorTag == 'FormNotFoundError');

  try {
    await presentForm(
      context,
      form: captured.form,
      routes: routes,
      profileId: profileId,
      requestKey: requestKey,
      onSubmit: (answer) async {
        if (!current()) {
          throw StateError(
            _chatL10n(context).chatUiTheFormOrProjectChangedReopenThe,
          );
        }
        receipts._set(requestKey, FormAnswerReceipt(since: clock.now()));
        try {
          await captured.reply(answer);
          receipts._set(requestKey, null);
        } catch (error) {
          if (answeredElsewhere(error)) {
            receipts._set(requestKey, null);
            settledElsewhere = true;
            return;
          }
          receipts._set(
            requestKey,
            FormAnswerReceipt(
              since: clock.now(),
              error: productErrorText(error),
            ),
          );
          rethrow;
        }
      },
      onCancel: () async {
        if (!current()) {
          throw StateError(
            _chatL10n(context).chatUiTheFormOrProjectChangedReopenThe,
          );
        }
        try {
          await captured.cancel();
        } catch (error) {
          if (answeredElsewhere(error)) {
            settledElsewhere = true;
            return;
          }
          rethrow;
        }
      },
    );
  } finally {
    routes.close();
  }
  if (settledElsewhere && context.mounted) {
    final l10n = _chatL10n(context);
    await showKitAlert(
      context,
      title: l10n.chatUiAlreadyAnsweredElsewhere,
      body: l10n.formFlowAnsweredElsewhereBody,
      alertKey: const Key('form-answered-elsewhere'),
    );
  }
}
