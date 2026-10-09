import 'dart:async';

import 'package:flutter/widgets.dart';

import '../../l10n/app_localizations.dart';
import '../../state/consent_owners.dart';
import '../../state/connection.dart';
import '../../state/repeated_permission_consent.dart';
import '../app_iconography.dart';
import '../app_theme.dart' show AppStatusTone;
import '../kit/kit.dart';

/// "Always allow this" on the third identical ask (P6.7, consent once, in
/// flow): one line under a permission request's card, shown once per server
/// and scope, and only where the server keeps saved grants.
///
/// It counts the request when it first builds (a replayed request is not
/// counted again) and shows only when [RepeatedPermissionConsent] hands out
/// the one-time invitation. Accepting states the grant's scope first, then
/// answers the request with `always` through the same path as the card; the
/// invitation is recorded as accepted only once the server took that answer,
/// and the grant is then on the server's Always allowed actions. "Keep
/// asking" is remembered, explained on What runs by itself, and the card's
/// Allow once and Reject stay as they were. Nothing here answers by itself.
///
/// The conversation owns where it sits: directly under the request's card,
/// keyed by the request id. It reads the request from the connection's
/// pending list, so it needs only the ids; the exact scope it is remembered
/// by is the request's own patterns and the standing grant the server
/// proposes (`always`), never display text.
class AlwaysAllowInvitation extends StatefulWidget {
  const AlwaysAllowInvitation({
    super.key,
    required this.controller,
    required this.sessionID,
    required this.requestID,
    this.contextLabel,
  });

  final ConnectionController controller;
  final String sessionID;
  final String requestID;

  /// Where the grant applies, as the request sheet says it ("in this
  /// conversation" by default).
  final String? contextLabel;

  @override
  State<AlwaysAllowInvitation> createState() => _AlwaysAllowInvitationState();
}

class _AlwaysAllowInvitationState extends State<AlwaysAllowInvitation> {
  bool _invited = false;
  bool _working = false;
  bool _failed = false;

  /// The request's exact scope and words, read once from the pending list.
  PermissionConsentScope? _scope;
  List<String> _broader = const [];
  String _what = '';

  String? get _profileId => widget.controller.profile?.id;

  String get _requestKey => '${widget.sessionID}/${widget.requestID}';

  @override
  void initState() {
    super.initState();
    final profileId = _profileId;
    final controller = widget.controller;
    final request = controller
        .permissionsForSession(widget.sessionID)
        .where((pending) => pending.id == widget.requestID)
        .firstOrNull;
    if (profileId == null ||
        request == null ||
        !request.canAlwaysAllow ||
        !controller.capabilities.persistentPermissionGrants) {
      return;
    }
    _scope = PermissionConsentScope(
      sessionID: request.sessionID,
      permission: request.permission,
      patterns: request.patterns,
      alwaysPatterns: request.always,
    );
    _broader = request.always.isNotEmpty ? request.always : request.patterns;
    _what = _broader.isNotEmpty ? _broader.join(', ') : request.permission;
    // Rebuilt for a request already invited: the history now answers
    // "offered", but the invitation still belongs under this card.
    _invited = ConsentOwners.invitedRequests(
      controller.store.prefs,
      profileId,
    ).contains(_requestKey);
    if (!_invited) unawaited(_observe(profileId));
  }

  Future<void> _observe(String profileId) async {
    final prefs = widget.controller.store.prefs;
    final invited = ConsentOwners.invitedRequests(prefs, profileId);
    final status = await ConsentOwners.repeated(prefs, profileId).observe(
      scope: _scope!,
      requestID: widget.requestID,
      supportsPersistentGrants: true,
    );
    if (!status.offerAlwaysAllow) return;
    invited.add(_requestKey);
    if (mounted) setState(() => _invited = true);
  }

  void _done() {
    final profileId = _profileId;
    if (profileId != null) {
      ConsentOwners.invitedRequests(
        widget.controller.store.prefs,
        profileId,
      ).remove(_requestKey);
    }
    if (mounted) setState(() => _invited = false);
  }

  Future<void> _accept() async {
    final profileId = _profileId;
    final scope = _scope;
    if (_working || profileId == null || scope == null) return;
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final confirmed = await showKitConfirm(
      context,
      title: l10n.consentAlwaysAllowTitle,
      body: l10n.chatRequestAlwaysScope(
        _broader.isEmpty
            ? l10n.chatUiAllMatchingRequests
            : KitBidi.ltr(_broader.join(', ')),
        widget.contextLabel ?? l10n.chatUiInThisChat,
      ),
      confirmLabel: l10n.chatUiAlwaysAllow,
      icon: AppIconography.privacy,
      sheetKey: const ValueKey('always-allow-invite-confirm'),
      confirmKey: const ValueKey('always-allow-invite-confirm-allow'),
    );
    if (!confirmed || !mounted) return;
    final controller = widget.controller;
    final pending = controller
        .permissionsForSession(widget.sessionID)
        .where((request) => request.id == widget.requestID)
        .firstOrNull;
    if (pending == null) return;
    final request = controller.permissionIdentity(pending);
    if (!controller.isRequestPending(request)) return;
    setState(() {
      _working = true;
      _failed = false;
    });
    try {
      await controller.answerPermission(
        widget.requestID,
        'always',
        expectedRequest: request,
      );
    } catch (_) {
      // Recorded only once the server took the answer: a refusal is said
      // here and the invitation stays for another try.
      if (mounted) {
        setState(() {
          _working = false;
          _failed = true;
        });
      }
      return;
    }
    if (mounted) setState(() => _working = false);
    await ConsentOwners.repeated(
      controller.store.prefs,
      profileId,
    ).recordDecision(scope, accepted: true);
    _done();
  }

  Future<void> _decline() async {
    final profileId = _profileId;
    final scope = _scope;
    if (_working || profileId == null || scope == null) return;
    _done();
    await ConsentOwners.repeated(
      widget.controller.store.prefs,
      profileId,
    ).recordDecision(scope, accepted: false);
  }

  @override
  Widget build(BuildContext context) {
    if (!_invited) return const SizedBox.shrink();
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final ask = KitAskLine(
      key: const ValueKey('always-allow-invite'),
      icon: AppIconography.privacy,
      question: l10n.consentAlwaysAllowQuestion(KitBidi.ltr(_what)),
      decline: KitAction(
        key: const ValueKey('always-allow-invite-decline'),
        label: l10n.consentAlwaysAllowDecline,
        onPressed: _working ? null : () => unawaited(_decline()),
      ),
      accept: KitAction(
        key: const ValueKey('always-allow-invite-accept'),
        label: l10n.chatUiAlwaysAllow,
        onPressed: _working ? null : () => unawaited(_accept()),
      ),
    );
    if (!_failed) return ask;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        KitNotice(
          key: const ValueKey('always-allow-invite-failed'),
          tone: AppStatusTone.failure,
          message: l10n.consentAlwaysAllowFailed,
        ),
        ask,
      ],
    );
  }
}
