import 'dart:async';
import 'dart:ui' show PlatformDispatcher;

import 'package:clock/clock.dart';
import 'package:flutter/material.dart';

import '../../../api/models.dart';
import '../../../domain/server_gateway.dart' show PendingQuestion;
import '../../../l10n/app_localizations.dart';
import '../../../state/connection.dart';
import '../../app_iconography.dart';
import '../../kit/kit.dart';
import '../../permission_presentation.dart';
import '../../widgets/product_states.dart' show productErrorText;
import '../../widgets/request_routes.dart';
import '../../widgets/tool_card.dart' show toolLabel;

AppLocalizations _l10n(BuildContext context) =>
    lookupAppLocalizations(Localizations.localeOf(context));

/// The glyph identifies the requested action rather than a generic admin role.
IconData permissionActionIcon(String permission) =>
    switch (permission.toLowerCase()) {
      'bash' => AppIconography.terminal,
      'edit' || 'write' || 'multiedit' || 'patch' => AppIconography.editNote,
      'read' => AppIconography.fileText,
      'webfetch' || 'websearch' => AppIconography.globe,
      'external_directory' => AppIconography.folderOpen,
      _ => AppIconography.permissions,
    };

/// When a request card first saw its request: the start of its age words
/// ("waiting 4 min"). Read from the app's clock so tests can pin it.
DateTime requestSeenNow() => clock.now();

/// One answer this phone sent for a permission request and has not yet
/// seen land (STATE-10: "Sent" is never "Done").
@immutable
class PermissionAnswerState {
  const PermissionAnswerState({
    required this.reply,
    required this.since,
    this.message,
    this.error,
  });

  /// `once`, `reject` or `always`.
  final String reply;
  final DateTime since;

  /// The note sent with a rejection.
  final String? message;

  /// The server's refusal in words; null while the answer is on its way.
  final String? error;

  bool get failed => error != null;

  PermissionAnswerState refused(String reason) => PermissionAnswerState(
    reply: reply,
    since: since,
    message: message,
    error: reason,
  );
}

/// The answers in flight for one connection's permission requests, shared by
/// the card in the conversation and the details sheet, so an answer given in
/// either shows its receipt on the card where the request lives (K2 §4.8).
class PermissionAnswers extends ChangeNotifier {
  PermissionAnswers._();

  static final _byController = Expando<PermissionAnswers>('permission-answers');

  /// The ledger of [controller]'s requests.
  static PermissionAnswers of(ConnectionController controller) =>
      _byController[controller] ??= PermissionAnswers._();

  final Map<String, PermissionAnswerState> _answers = {};

  /// What was sent for [requestID], or null when nothing is in flight and
  /// nothing was refused.
  PermissionAnswerState? answerFor(String requestID) => _answers[requestID];

  void _set(String requestID, PermissionAnswerState? state) {
    if (state == null) {
      if (_answers.remove(requestID) == null) return;
    } else {
      _answers[requestID] = state;
    }
    notifyListeners();
  }
}

/// Answers [permission] with [reply]. The answer is held for the undo window
/// ([DelayedAnswers]: the card shows it collapsed with Undo) and then sent;
/// leaving the chat sends it at once. A second answer while one is held is
/// ignored (Undo first).
///
/// The send records itself in [PermissionAnswers]: sending while in flight,
/// cleared once the server took it (the request then leaves the pending list
/// and its card goes), refused with the server's words otherwise. A [note]
/// draft is cleared only once the answer is confirmed (DATA-1).
Future<void> answerPermissionRequest(
  ConnectionController controller,
  PermissionRequest permission,
  String reply, {
  String? message,
  KitDraft? note,
}) async {
  final request = controller.permissionIdentity(permission);
  if (!controller.isRequestPending(request)) return;
  final delayed = controller.delayedAnswers;
  if (delayed.isHeld(permission.id)) return;
  delayed.hold(
    permission.id,
    label: _heldAnswerWords(reply),
    send: () => _sendPermissionAnswer(
      controller,
      permission,
      request,
      reply,
      message: message,
      note: note,
    ),
  );
}

/// "Allowed", "Rejected" or "Always allowed" for a held answer; this has no
/// context, so it follows the phone's language.
String _heldAnswerWords(String reply) {
  final code = PlatformDispatcher.instance.locale.languageCode;
  final l10n =
      AppLocalizations.supportedLocales.any(
        (supported) => supported.languageCode == code,
      )
      ? lookupAppLocalizations(Locale(code))
      : lookupAppLocalizations(const Locale('en'));
  return switch (reply) {
    'reject' => l10n.chatRequestAnswerRejected,
    'always' => l10n.chatRequestAlwaysOn,
    _ => l10n.chatRequestAnswerAllowed,
  };
}

Future<void> _sendPermissionAnswer(
  ConnectionController controller,
  PermissionRequest permission,
  PendingRequestIdentity request,
  String reply, {
  String? message,
  KitDraft? note,
}) async {
  if (!controller.isRequestPending(request)) return;
  final answers = PermissionAnswers.of(controller);
  final sent = PermissionAnswerState(
    reply: reply,
    since: clock.now(),
    message: message,
  );
  answers._set(permission.id, sent);
  try {
    await controller.answerPermission(
      permission.id,
      reply,
      message: message,
      expectedRequest: request,
    );
    answers._set(permission.id, null);
    unawaited(note?.clear());
  } catch (error) {
    if (!controller.isRequestPending(request)) {
      answers._set(permission.id, null);
      return;
    }
    answers._set(permission.id, sent.refused(productErrorText(error)));
  }
}

/// The request's command, file or patterns in one line for the card: the
/// full value lives in the details sheet.
String? permissionSummary(PermissionRequest permission) =>
    permission.commandPreview ??
    permission.filePath ??
    (permission.patterns.isEmpty ? null : permission.patterns.join(' · '));

String _answerWords(AppLocalizations l10n, String reply) => switch (reply) {
  'reject' => l10n.kitRequestReject,
  'always' => l10n.chatUiAlwaysAllow,
  _ => l10n.kitRequestAllowOnce,
};

/// The card's "Always allow" (13B), or null where the server keeps no
/// standing grants. [onConfirmed] runs only after the person confirmed
/// "Always allow `<command or tool>` in this project?"; the confirm states
/// what the grant covers and where to take it back.
KitRequestAlwaysAllowStep? permissionAlwaysStep(
  BuildContext context, {
  required PermissionRequest permission,
  required bool supported,
  required VoidCallback onConfirmed,
}) {
  if (!supported) return null;
  final l10n = _l10n(context);
  final broader = permission.always.isNotEmpty
      ? permission.always
      : permission.patterns;
  final what =
      permission.commandPreview ??
      permission.filePath ??
      (permission.permission.isEmpty
          ? l10n.chatUiAllMatchingRequests
          : toolLabel(permission.permission, l10n: l10n));
  return KitRequestAlwaysAllowStep(
    what: what,
    covers: broader.isEmpty ? null : broader.join(', '),
    onConfirmed: onConfirmed,
    buttonKey: const Key('permission-card-always'),
    confirmKey: const Key('permission-card-always-confirm'),
  );
}

/// The one card for a permission request ([KitRequestCard.ask]): the ask in
/// plain words, the command or file, and Allow once / Reject in place.
/// [answered] turns it into the sending line with its receipt, or puts a
/// refusal above the answers again. [heldLabel] with [onUndo] shows the
/// answer collapsed ("Allowed") with Undo while it is held, before it is sent.
KitRequestCard permissionRequestCard(
  BuildContext context, {
  required PermissionRequest permission,
  required String who,
  required VoidCallback? onAllow,
  required VoidCallback? onReject,
  String? disabledReason,
  VoidCallback? onDetails,
  PermissionAnswerState? answered,
  KitRequestAlwaysAllowStep? alwaysAllow,
  VoidCallback? onRetry,
  String? heldLabel,
  VoidCallback? onUndo,
  String? detail,
  String? ifIgnored,
  DateTime? since,
  Key? key,
  Key? detailsKey,
  bool inList = false,
}) {
  final l10n = _l10n(context);
  final title = permissionRequestTitle(permission.permission, l10n: l10n);
  final held = heldLabel != null && onUndo != null;
  final sending = !held && answered != null && !answered.failed;
  return KitRequestCard.ask(
    key: key,
    kind: KitRequestKind.permission,
    title: title,
    who: who,
    reason: KitNeedsYouReason.decision,
    ifIgnored: ifIgnored ?? l10n.chatRequestIfIgnored,
    announcement: l10n.chatUiPermissionNeeded(title),
    icon: permissionActionIcon(permission.permission),
    detail: detail ?? permission.message,
    summary: permissionSummary(permission),
    since: since,
    phase: held
        ? KitRequestPhase.answered
        : sending
        ? KitRequestPhase.sending
        : KitRequestPhase.waiting,
    answer: held
        ? heldLabel
        : answered == null
        ? null
        : _answerWords(l10n, answered.reply),
    receipt: held
        ? KitReceipt(
            state: KitReceiptState.confirmed,
            label: heldLabel,
            onUndo: onUndo,
            undoKey: const Key('permission-card-undo'),
          )
        : answered == null
        ? null
        : answered.failed
        ? KitReceipt(state: KitReceiptState.refused, reason: answered.error)
        : KitReceipt(
            state: KitReceiptState.sending,
            since: answered.since,
            onRetry: onRetry,
          ),
    answers: KitRequestDecide(
      onAllow: onAllow,
      onReject: onReject,
      disabledReason: disabledReason,
      allowKey: const Key('permission-card-allow'),
      rejectKey: const Key('permission-card-reject'),
      alwaysAllow: alwaysAllow,
      // Among other rows, the screen keeps its own one primary.
      secondary: inList,
    ),
    onDetails: onDetails,
    detailsKey: detailsKey,
    inList: inList,
  );
}

const _diffPermissions = {'edit', 'write', 'multiedit', 'patch'};
const _diffMetadataKeys = ['diff', 'patch', 'preview'];

/// A unified diff the server attached to an edit/write ask, from the common
/// metadata spellings; null for other tools or when absent.
String? _diffPreview(PermissionRequest permission) {
  if (!_diffPermissions.contains(permission.permission.toLowerCase())) {
    return null;
  }
  for (final key in _diffMetadataKeys) {
    final value = permission.metadata[key];
    if (value is String && value.trim().isNotEmpty) return value;
  }
  return null;
}

/// Opens the details of a permission [card] in the one request sheet
/// ([showKitRequestSheet]): the whole command, file or pattern list, the
/// change as a read-only diff, "Always allow" as a risky switch that states
/// its scope first (it replaces the old "Confirm broader access" dialog),
/// the note sent with Reject, and the tool's technical names last under
/// Details. Answering here calls the card's own answers and closes.
Future<KitRequestSheetOutcome> showPermissionDetails(
  BuildContext context, {
  required PermissionRequest permission,
  required KitRequestCard card,
  required RequestRoutes routes,
  ValueChanged<KitUntil>? onAllowAlways,
  KitRequestMessage? message,
  String? contextLabel,
}) {
  final l10n = _l10n(context);
  final diff = _diffPreview(permission);
  final path = permission.filePath;
  final command = permission.commandPreview;
  final shown = {?command, ?path};
  final others = permission.patterns
      .where((pattern) => !shown.contains(pattern))
      .toList();
  final fullText =
      command ??
      path ??
      (others.isEmpty ? l10n.chatUiAllMatchingRequests : others.join('\n'));
  final broader = permission.always.isNotEmpty
      ? permission.always
      : permission.patterns;
  return showKitRequestSheet(
    context,
    card: card,
    routes: routes,
    fullText: fullText,
    change: diff == null
        ? null
        : KitDiffView(
            files: [
              KitDiffFile.fromPatch(path ?? l10n.chatUiPendingChange, diff),
            ],
          ),
    alwaysAllow: onAllowAlways == null
        ? null
        : KitRequestAlwaysAllow(
            title: l10n.chatRequestAlwaysTitle,
            scope: l10n.chatRequestAlwaysScope(
              broader.isEmpty
                  ? l10n.chatUiAllMatchingRequests
                  : KitBidi.ltr(broader.join(', ')),
              contextLabel ?? l10n.chatRequestAlwaysInProject,
            ),
            onLabel: l10n.chatRequestAlwaysOn,
            until: const [KitUntil.off],
            onAllowAlways: onAllowAlways,
            switchKey: const Key('permission-allow-always'),
          ),
    message: message,
    details: [
      if (permission.permission.isNotEmpty)
        KitTechnicalValue(l10n.chatRequestDetailTool, permission.permission),
      if (command != null && others.isNotEmpty)
        KitTechnicalValue(l10n.chatRequestDetailPatterns, others.join('\n')),
      if (permission.always.isNotEmpty)
        KitTechnicalValue(
          l10n.chatUiAlwaysAllowWouldAlsoCover,
          permission.always.join('\n'),
        ),
    ],
    sheetKey: const Key('permission-sheet'),
  );
}

/// Presents a permission request's details (design doc §3) from any entry
/// point: the chat card's Details, the Requests tile, notification taps.
///
/// Every answer goes through [answerPermissionRequest], so its receipt shows
/// on the conversation's card (Sending, Not confirmed yet, Not accepted);
/// the sheet itself closes on answer and closes itself when the request is
/// answered elsewhere. OpenCode 2 takes a note with Reject (kept as a draft
/// until the answer lands); OpenCode 1 rejects without one. "Always allow"
/// is offered only where the server keeps saved grants.
///
/// [onShowSource] is kept for its callers and no longer drawn: the card
/// sits in the conversation under the tool call it asks about.
Future<void> showPermissionSheet(
  BuildContext context, {
  required PermissionRequest permission,
  required ConnectionController controller,
  String? contextLabel,
  VoidCallback? onShowSource,
}) async {
  final request = controller.permissionIdentity(permission);
  if (!controller.isRequestPending(request)) return;
  final l10n = _l10n(context);
  final routes = RequestRoutes(
    changes: controller,
    isPending: () => controller.isRequestPending(request),
  );
  final profileId = controller.profile?.id ?? controller.store.activeId ?? '';
  final noteText = TextEditingController();
  final note =
      profileId.isEmpty ||
          !controller.permissionSupportsRejectMessage(permission.id)
      ? null
      : KitDraft(
          target: 'request.${permission.id}.note',
          profileId: profileId,
          controller: noteText,
        );
  void answer(String reply) {
    final text = noteText.text.trim();
    unawaited(
      answerPermissionRequest(
        controller,
        permission,
        reply,
        message: reply == 'reject' && text.isNotEmpty ? text : null,
        note: reply == 'reject' ? note : null,
      ),
    );
  }

  final answered = PermissionAnswers.of(controller).answerFor(permission.id);
  final card = permissionRequestCard(
    context,
    permission: permission,
    who: contextLabel ?? l10n.chatRequestWho,
    onAllow: () => answer('once'),
    onReject: () => answer('reject'),
    answered: answered != null && answered.failed ? answered : null,
  );
  try {
    await showPermissionDetails(
      context,
      permission: permission,
      card: card,
      routes: routes,
      contextLabel: contextLabel,
      onAllowAlways: controller.capabilities.persistentPermissionGrants
          ? (_) => answer('always')
          : null,
      message: note == null
          ? null
          : KitRequestMessage(
              fieldLabel: l10n.chatUiTellTheAgentWhyOrWhatTo,
              draft: note,
              fieldKey: const Key('permission-reject-message'),
            ),
    );
  } finally {
    routes.close();
    // The frame hands the controller back after its exit; a note being
    // typed is already saved in its draft.
    WidgetsBinding.instance.addPostFrameCallback((_) => noteText.dispose());
  }
}

/// Opens the details of a one-prompt question [card] in the one request
/// sheet: the question as sent and every option. It closes itself when the
/// question is answered elsewhere.
Future<KitRequestSheetOutcome> showQuestionDetails(
  BuildContext context, {
  required ConnectionController controller,
  required PendingQuestion question,
  required KitRequestCard card,
  PendingRequestIdentity? request,
}) async {
  final routes = RequestRoutes(
    changes: controller,
    isPending: () => request != null
        ? controller.isRequestPending(request)
        : controller.questions.containsKey(question.id),
  );
  try {
    return await showKitRequestSheet(
      context,
      card: card,
      routes: routes,
      fullText: question.prompts.firstOrNull?.question,
      sheetKey: const Key('question-sheet'),
    );
  } finally {
    routes.close();
  }
}

/// Retired by chat-5: use [showPermissionSheet], or [permissionRequestCard]
/// in a conversation. Kept working (KIT-43): it now draws the request as the
/// one card, with Allow once and Reject in place and Details opening the
/// request sheet, and replies through [onReply].
class PermissionSheet extends StatefulWidget {
  const PermissionSheet({
    super.key,
    required this.permission,
    required this.onReply,
    required this.supportsRejectMessage,
    this.allowPersistentPermission = true,
    this.contextLabel,
    this.onShowSource,
    this.routes,
    this.allowDeviceActions = true,
  });

  final PermissionRequest permission;
  final Future<void> Function(String reply, {String? message}) onReply;

  /// OpenCode 2 takes a note with Reject; without a profile to keep it as a
  /// draft, this wrapper rejects without one.
  final bool supportsRejectMessage;
  final bool allowPersistentPermission;
  final String? contextLabel;

  /// Kept for its callers; no longer drawn.
  final VoidCallback? onShowSource;
  final RequestRoutes? routes;

  /// Kept for its callers; copying is the kit's own (KIT-23).
  final bool allowDeviceActions;

  @override
  State<PermissionSheet> createState() => _PermissionSheetState();
}

class _PermissionSheetState extends State<PermissionSheet> {
  late final RequestRoutes _routes = widget.routes ?? RequestRoutes();
  late final DateTime _seen = requestSeenNow();
  PermissionAnswerState? _answered;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    // The host that shows this wrapper goes once the request is answered.
    _routes.own(ModalRoute.of(context));
  }

  @override
  void dispose() {
    _routes.close();
    super.dispose();
  }

  Future<void> _reply(String reply) async {
    if (reply == 'always' && !widget.allowPersistentPermission) return;
    if (!_routes.isPending || (_answered != null && !_answered!.failed)) {
      return;
    }
    final sent = PermissionAnswerState(reply: reply, since: clock.now());
    setState(() => _answered = sent);
    try {
      await widget.onReply(reply);
      if (!mounted) return;
      _routes.close();
    } catch (error) {
      if (!mounted || !_routes.isPending) return;
      setState(() => _answered = sent.refused(productErrorText(error)));
    }
  }

  KitRequestCard _card(BuildContext context) => permissionRequestCard(
    context,
    permission: widget.permission,
    who: widget.contextLabel ?? _l10n(context).chatRequestWho,
    since: _seen,
    answered: _answered,
    onAllow: () => unawaited(_reply('once')),
    onReject: () => unawaited(_reply('reject')),
    onRetry: () {
      final reply = _answered?.reply;
      if (reply == null) return;
      setState(() => _answered = null);
      unawaited(_reply(reply));
    },
    onDetails: _openDetails,
    detailsKey: const Key('permission-card-review'),
  );

  void _openDetails() {
    if (!_routes.isPending) return;
    unawaited(
      showPermissionDetails(
        context,
        permission: widget.permission,
        card: _card(context),
        routes: _routes,
        contextLabel: widget.contextLabel,
        onAllowAlways: widget.allowPersistentPermission
            ? (_) => unawaited(_reply('always'))
            : null,
      ),
    );
  }

  @override
  Widget build(BuildContext context) => _card(context);
}
