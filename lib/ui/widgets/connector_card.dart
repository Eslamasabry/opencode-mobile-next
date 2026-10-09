import 'dart:async';

import 'package:flutter/widgets.dart';

import '../../domain/genui/gen_ui.dart';
import '../../domain/mcp_catalog.dart';
import '../../domain/mcp_chat.dart';
import '../../l10n/app_localizations.dart';
import '../../state/connector_card_host.dart';
import '../app_iconography.dart';
import '../app_theme.dart' show AppStatusTone;
import '../kit/kit.dart';
import 'external_link.dart';

/// The card an agent draws when it recommends a catalogue connector
/// (docs/design/BD3-mcp-chat-contract.md): the connector's name, the agent's
/// reason, where it runs and that it lasts while this server runs, and one
/// primary Connect. Connect shows its progress in place, opens sign-in through
/// [openExternalLink] when the server asks for it, then says Connected, and
/// "Tools ready" only when the runtime confirmed it (the contract's wording
/// otherwise). Every failure is one fixed sentence with a way forward; the
/// stable failure name sits under Details.
///
/// The card never reads a gateway or a server message. [host] resolves the
/// listing and hands the conversation's [ConnectorChat]; without a host, a
/// runtime that cannot connect from chat, or a card that is no longer current,
/// no Connect is offered.
class ConnectorCardView extends StatefulWidget {
  const ConnectorCardView({
    super.key,
    required this.card,
    required this.agentLabel,
    required this.body,
    this.host,
    this.reportable = true,
    this.inList = false,
    this.onOpenTools,
    this.cardKey,
  });

  final GenUiCard card;

  /// The conversation's agent, as its other cards say it.
  final String agentLabel;

  /// The agent's own card parts, already drawn from the kit.
  final List<Widget> body;

  final ConnectorCardHost? host;

  /// The card is the current report of its conversation. A stale card shows
  /// its words and no Connect.
  final bool reportable;
  final bool inList;

  /// Opens the connector catalogue in Tools; null hides that way forward.
  final VoidCallback? onOpenTools;
  final Key? cardKey;

  @override
  State<ConnectorCardView> createState() => _ConnectorCardViewState();
}

class _ConnectorCardViewState extends State<ConnectorCardView> {
  ConnectorResolution? _resolution;
  int _generation = 0;
  bool _signingIn = false;
  final _code = TextEditingController();

  @override
  void initState() {
    super.initState();
    _resolve();
  }

  @override
  void didUpdateWidget(covariant ConnectorCardView oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.card.identity != widget.card.identity ||
        oldWidget.card.revision != widget.card.revision ||
        oldWidget.reportable != widget.reportable ||
        (oldWidget.host == null) != (widget.host == null)) {
      _code.clear();
      _resolve();
    }
  }

  @override
  void dispose() {
    _generation++;
    _code.dispose();
    super.dispose();
  }

  void _resolve() {
    final generation = ++_generation;
    final host = widget.host;
    _resolution = null;
    if (host == null || !widget.reportable) return;
    host
        .resolve(widget.card)
        .then(
          (value) {
            if (mounted && generation == _generation) {
              setState(() => _resolution = value);
            }
          },
          onError: (Object _) {
            if (mounted && generation == _generation) {
              setState(() => _resolution = const ConnectorNotListed());
            }
          },
        );
  }

  void _reopen() {
    setState(_resolve);
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final tokens = KitTokens.of(context);
    final resolution = _resolution;
    final item = resolution?.item;
    final name = KitBidi.auto(item?.title ?? widget.card.title);
    final agent = KitBidi.auto(widget.agentLabel);
    final reason = widget.card.connector?.reason ?? '';
    final zone = _zone(context, l10n, resolution);
    return KitAgentCard(
      key: widget.cardKey,
      eyebrow: l10n.connectorCardSuggests(agent),
      title: name,
      icon: AppIconography.tools,
      inList: widget.inList,
      announcement: l10n.connectorCardAnnouncement(agent, name),
      body: [
        if (reason.isNotEmpty)
          KitText(
            KitBidi.auto(reason),
            key: const Key('connector-card-reason'),
            role: KitTextRole.body,
          ),
        ...widget.body,
        if (item != null) _facts(l10n, item),
        ?(zone == null
            ? null
            : Padding(
                padding: EdgeInsetsDirectional.only(top: tokens.space1),
                child: zone,
              )),
      ],
    );
  }

  // ── What and where ─────────────────────────────────────────────────────

  Widget _facts(AppLocalizations l10n, McpCatalogItem item) => KitKeyValue(
    key: const Key('connector-card-facts'),
    rows: [
      KitKeyValueRow(
        label: l10n.connectorCardRuns,
        value: switch (item.runtime) {
          McpCatalogRuntime.hosted =>
            (item.hostedBy ?? '').isEmpty
                ? l10n.connectorCardRunsHosted
                : l10n.mcpCatalogHostedBy(KitBidi.ltr(item.hostedBy!)),
          McpCatalogRuntime.node => l10n.connectorCardRunsNode,
          McpCatalogRuntime.python => l10n.connectorCardRunsPython,
          McpCatalogRuntime.docker => l10n.connectorCardRunsDocker,
          McpCatalogRuntime.none => l10n.connectorCardRunsNone,
        },
      ),
      KitKeyValueRow(
        label: l10n.connectorCardLasts,
        value: l10n.connectorCardLastsValue,
      ),
    ],
  );

  // ── The state zone ─────────────────────────────────────────────────────

  Widget? _zone(
    BuildContext context,
    AppLocalizations l10n,
    ConnectorResolution? resolution,
  ) => switch (resolution) {
    null => null,
    ConnectorUnsupported() => _notice(
      l10n,
      McpChatFailure.unavailable,
      tone: AppStatusTone.neutral,
      actions: [_toolsAction(l10n)],
    ),
    ConnectorNotListed() => _notice(
      l10n,
      McpChatFailure.invalidSuggestion,
      actions: [_toolsAction(l10n)],
    ),
    ConnectorNeedsSetup() => _notice(
      l10n,
      McpChatFailure.setupRequired,
      tone: AppStatusTone.neutral,
      actions: [_toolsAction(l10n)],
    ),
    ConnectorAlreadyConnected() => _connected(l10n, ready: false),
    final ConnectorReady ready => ListenableBuilder(
      listenable: ready.chat,
      builder: (context, _) =>
          _chatZone(context, l10n, ready, ready.chat.snapshot),
    ),
  };

  Widget _chatZone(
    BuildContext context,
    AppLocalizations l10n,
    ConnectorReady ready,
    McpChatSnapshot snapshot,
  ) {
    final chat = ready.chat;
    final tokens = KitTokens.of(context);
    switch (snapshot.phase) {
      case McpChatPhase.suggested:
        return KitButton.primary(
          key: const Key('connector-card-connect'),
          label: l10n.connectorCardConnect,
          onPressed: () => unawaited(chat.connect(chat.serverName)),
        );
      case McpChatPhase.connecting:
        return KitButton.primary(
          key: const Key('connector-card-connecting'),
          label: l10n.connectorCardConnecting,
          working: true,
          onPressed: () {},
        );
      case McpChatPhase.checkingTools:
        return KitButton.primary(
          key: const Key('connector-card-checking'),
          label: l10n.connectorCardChecking,
          working: true,
          onPressed: () {},
        );
      case McpChatPhase.needsAuthentication:
        if (!ready.canSignIn) {
          return _notice(
            l10n,
            McpChatFailure.oauthUnavailable,
            tone: AppStatusTone.neutral,
            actions: [_toolsAction(l10n)],
          );
        }
        return Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            KitNotice(
              message: l10n.connectorCardSignInNeeded,
              messageKey: const Key('connector-card-sign-in-needed'),
            ),
            SizedBox(height: tokens.space3),
            KitButton.primary(
              key: const Key('connector-card-sign-in'),
              label: l10n.connectorCardSignIn,
              working: _signingIn,
              onPressed: () => unawaited(_signIn(ready)),
            ),
          ],
        );
      case McpChatPhase.authorizing:
        return _authorizing(context, l10n, ready, snapshot);
      case McpChatPhase.toolsReady:
        return _connected(l10n, ready: true);
      case McpChatPhase.connectedReadinessUnknown:
        return _connected(l10n, ready: false, unconfirmed: true);
      case McpChatPhase.failed:
      case McpChatPhase.unavailable:
        final failure = snapshot.failure ?? McpChatFailure.connectFailed;
        return _notice(
          l10n,
          failure,
          actions: switch (failure) {
            McpChatFailure.connectFailed => [
              KitAction(
                key: const Key('connector-card-try-again'),
                label: l10n.connectorCardTryAgain,
                onPressed: () => unawaited(chat.connect(chat.serverName)),
              ),
              _toolsAction(l10n),
            ],
            McpChatFailure.authenticationFailed ||
            McpChatFailure.notConnected => [
              KitAction(
                key: const Key('connector-card-check-status'),
                label: l10n.connectorCardCheckStatus,
                onPressed: () => unawaited(chat.refresh()),
              ),
              _toolsAction(l10n),
            ],
            McpChatFailure.sourceChanged => [
              KitAction(
                key: const Key('connector-card-reopen'),
                label: l10n.connectorCardReopen,
                onPressed: _reopen,
              ),
            ],
            _ => [_toolsAction(l10n)],
          },
        );
    }
  }

  Widget _authorizing(
    BuildContext context,
    AppLocalizations l10n,
    ConnectorReady ready,
    McpChatSnapshot snapshot,
  ) {
    final tokens = KitTokens.of(context);
    final chat = ready.chat;
    final url = snapshot.authorizationUrl;
    final manual = snapshot.manualCodeRequired;
    final open = KitAction(
      key: const Key('connector-card-open-sign-in'),
      label: l10n.connectorCardOpenSignIn,
      onPressed: url == null ? null : () => unawaited(_openSignIn(url)),
    );
    return ListenableBuilder(
      listenable: _code,
      builder: (context, _) {
        final hasCode = _code.text.trim().isNotEmpty;
        final finish = KitAction(
          key: const Key('connector-card-finish'),
          label: l10n.connectorCardFinishSignIn,
          onPressed: hasCode
              ? () {
                  final text = _code.text;
                  _code.clear();
                  unawaited(chat.completeOAuth(text));
                }
              : null,
          disabledReason: hasCode ? null : l10n.connectorCardCodeRequired,
        );
        return Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            KitNotice(
              tone: manual ? AppStatusTone.neutral : AppStatusTone.progress,
              message: manual
                  ? l10n.connectorCardManualCode
                  : l10n.connectorCardSignInWaiting,
              messageKey: const Key('connector-card-authorizing'),
            ),
            if (manual) ...[
              SizedBox(height: tokens.space3),
              KitField.secret(
                fieldKey: const Key('connector-card-code'),
                label: l10n.connectorCardCodeLabel,
                controller: _code,
              ),
            ],
            SizedBox(height: tokens.space3),
            KitActionBlock(
              primary: manual ? finish : open,
              secondary: manual ? open : null,
              tertiary: [
                KitAction(
                  key: const Key('connector-card-check-status'),
                  label: l10n.connectorCardCheckStatus,
                  onPressed: () => unawaited(chat.refresh()),
                ),
                KitAction(
                  key: const Key('connector-card-cancel-sign-in'),
                  label: l10n.connectorCardCancelSignIn,
                  onPressed: () => unawaited(_cancelSignIn(ready)),
                ),
              ],
            ),
          ],
        );
      },
    );
  }

  Widget _connected(
    AppLocalizations l10n, {
    required bool ready,
    bool unconfirmed = false,
  }) {
    final notice = KitNotice(
      tone: AppStatusTone.ok,
      message: unconfirmed
          ? l10n.connectorCardConnectedUnconfirmed
          : l10n.connectorCardConnected,
      notes: ready ? [l10n.connectorCardLoadedTools] : const [],
      messageKey: const Key('connector-card-connected'),
    );
    if (!ready) return notice;
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Expanded(child: notice),
        KitStatusTag(
          key: const Key('connector-card-tools-ready'),
          label: l10n.connectorCardToolsReady,
          tone: KitStatusTagTone.done,
        ),
      ],
    );
  }

  /// One fixed sentence, a way forward, and the stable failure name under
  /// Details (the card is never given a server's own text).
  Widget _notice(
    AppLocalizations l10n,
    McpChatFailure failure, {
    AppStatusTone tone = AppStatusTone.failure,
    List<KitAction> actions = const [],
  }) => Column(
    mainAxisSize: MainAxisSize.min,
    crossAxisAlignment: CrossAxisAlignment.stretch,
    children: [
      KitNotice(
        tone: tone,
        message: _failureText(l10n, failure),
        actions: actions,
        messageKey: Key('connector-card-failure-${failure.name}'),
      ),
      if (tone == AppStatusTone.failure)
        KitDetailsFold(
          key: const Key('connector-card-details'),
          label: l10n.agentCardDetails,
          text: failure.name,
        ),
    ],
  );

  KitAction _toolsAction(AppLocalizations l10n) => KitAction(
    key: const Key('connector-card-open-tools'),
    label: l10n.connectorCardOpenTools,
    onPressed: widget.onOpenTools,
  );

  static String _failureText(AppLocalizations l10n, McpChatFailure failure) =>
      switch (failure) {
        McpChatFailure.unavailable => l10n.connectorCardFailureUnavailable,
        McpChatFailure.invalidSuggestion =>
          l10n.connectorCardFailureInvalidSuggestion,
        McpChatFailure.setupRequired => l10n.connectorCardFailureSetupRequired,
        McpChatFailure.nameConflict => l10n.connectorCardFailureNameConflict,
        McpChatFailure.sourceChanged => l10n.connectorCardFailureSourceChanged,
        McpChatFailure.connectFailed => l10n.connectorCardFailureConnectFailed,
        McpChatFailure.authenticationFailed =>
          l10n.connectorCardFailureAuthenticationFailed,
        McpChatFailure.oauthUnavailable =>
          l10n.connectorCardFailureOauthUnavailable,
        McpChatFailure.notConnected => l10n.connectorCardFailureNotConnected,
      };

  // ── Sign-in ────────────────────────────────────────────────────────────

  Future<void> _signIn(ConnectorReady ready) async {
    if (_signingIn) return;
    setState(() => _signingIn = true);
    try {
      await ready.chat.startOAuth();
    } finally {
      if (mounted) setState(() => _signingIn = false);
    }
    if (!mounted) return;
    final snapshot = ready.chat.snapshot;
    final url = snapshot.authorizationUrl;
    if (snapshot.phase == McpChatPhase.authorizing && url != null) {
      await _openSignIn(url);
    }
  }

  /// The one way a sign-in address opens: the app's external-link gate, with
  /// its origin confirmation. The address is the server's, never the card's.
  Future<void> _openSignIn(Uri url) async {
    if (!mounted) return;
    await openExternalLink(context, url.toString());
  }

  Future<void> _cancelSignIn(ConnectorReady ready) async {
    final l10n = AppLocalizations.of(context);
    final name = KitBidi.auto(ready.item?.title ?? widget.card.title);
    final confirmed = await showKitConfirm(
      context,
      title: l10n.connectorCardCancelTitle,
      body: l10n.connectorCardCancelBody(name),
      confirmLabel: l10n.connectorCardCancelSignIn,
      cancelLabel: l10n.connectorCardKeepWaiting,
      kind: KitConfirmKind.stop,
      confirmKey: const Key('connector-card-cancel-confirm'),
    );
    if (!confirmed || !mounted) return;
    _code.clear();
    await ready.chat.cancelOAuth();
  }
}
