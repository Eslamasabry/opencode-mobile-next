import 'dart:async';

import 'package:clock/clock.dart';
import 'package:flutter/material.dart';

import '../../../domain/chat_feed.dart';
import '../../../domain/profile_monitor.dart';
import '../../../domain/relative_age.dart';
import '../../../domain/session_title_text.dart';
import '../../../l10n/app_localizations.dart';
import '../../../state/connection.dart';
import '../../../state/profile_monitor.dart' show ProfileMonitor;
import '../../app_iconography.dart';
import '../../kit/kit.dart';
import '../../widgets/phone_server_card.dart' show serverDisplayName;
import '../../widgets/session_title.dart';
import '../agents/agents_text.dart' show agentMark;
import '../chat/permission_sheet.dart'
    show
        PermissionAnswers,
        answerPermissionRequest,
        permissionRequestCard,
        requestFirstSeen;
import '../chats/chats_host.dart' show ConnectionChatsHost;
import '../profile_monitor_screen.dart' show openMonitoredRequest;
import 'inbox_items.dart';

/// Opens the Inbox over whatever shows: the home's "Needs you" chip, Search's
/// Go to, a notification, the Quick Settings tile and the widgets all land
/// here.
Future<void> openInbox(BuildContext context, ConnectionController controller) =>
    pushKitPage<void>(context, (_) => InboxScreen(controller: controller));

/// Inbox, one calm list (vision pick, 2026-10-20;
/// docs/qa/vision-inbox-2026-10-20/README.md): everything that waits on the
/// person across connections, oldest first, cleared top to bottom. Each row
/// says the question, where it waits (server · project · conversation) and,
/// for a permission, the exact command with Allow once and Reject, held for
/// the same Undo window as the chat. Questions and forms open their
/// conversation (owner decision 1). When nothing waits: one calm line, and
/// the conversations that finished and were not opened yet.
class InboxScreen extends StatefulWidget {
  const InboxScreen({super.key, required this.controller});

  final ConnectionController controller;

  @override
  State<InboxScreen> createState() => _InboxScreenState();
}

class _InboxScreenState extends State<InboxScreen> {
  ConnectionController get _c => widget.controller;

  /// Plain words when a conversation could not open; null otherwise.
  String? _notice;
  bool _opening = false;

  /// The side connections the rows answer through, so their changes rebuild
  /// the list.
  List<ConnectionController> _owners() => {
    for (final item in _c.chatFeed(_waitingFilter).items)
      ?_c.connectionForRow(item),
  }.where((owner) => !identical(owner, _c)).toList();

  static const _waitingFilter = ChatFeedFilter(
    needsYou: true,
    includeSubagents: true,
  );

  @override
  Widget build(BuildContext context) {
    final monitor = _c.isIsolated ? null : _c.profileMonitor;
    final owners = _owners();
    return ListenableBuilder(
      listenable: Listenable.merge([
        _c,
        ?monitor,
        PermissionAnswers.of(_c),
        _c.delayedAnswers,
        for (final owner in owners) ...[
          owner,
          PermissionAnswers.of(owner),
          owner.delayedAnswers,
        ],
      ]),
      builder: (context, _) => _page(context),
    );
  }

  Widget _page(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final tokens = KitTokens.of(context);
    final entries = inboxEntries(_c);
    final finished = entries.isEmpty
        ? inboxFinished(_c).take(_finishedShown).toList()
        : const <ChatFeedItem>[];
    final notice = _notice;
    // One main action on the screen: the oldest permission's Allow once.
    // The count under the title is what is left to clear: an answer held
    // for Undo, or on its way, no longer counts.
    bool answered(InboxEntry entry) {
      if (entry is! InboxFeedEntry) return false;
      final permission = entry.permission;
      final owner = entry.owner;
      if (permission == null || owner == null) return false;
      return owner.delayedAnswers.isHeld(permission.id) ||
          PermissionAnswers.of(owner).answerFor(permission.id) != null;
    }

    // One main action on the screen: the oldest permission's Allow once. It
    // stays with that row while its answer is held (Undo can bring the
    // buttons back) and moves on once the row has left.
    final primary = entries.indexWhere(
      (entry) => entry is InboxFeedEntry && entry.permission != null,
    );
    final left = entries.where((entry) => !answered(entry)).length;
    // The calm line never claims a server it could not read: under it, the
    // one that isn't answering, with Try again.
    final down = _unreachableNames(context);
    final calm = Padding(
      padding: EdgeInsetsDirectional.symmetric(
        horizontal: tokens.gutter,
        vertical: tokens.space6,
      ),
      child: KitStatusAnswer(
        key: const ValueKey('inbox-empty'),
        tone: down == null ? KitAnswerTone.ok : KitAnswerTone.off,
        answer: l10n.inboxEmptyTitle,
        line: down == null ? null : l10n.inboxEmptyPartialLine(down),
        lineKey: const ValueKey('inbox-empty-partial'),
        action: down == null ? null : _retryAction(context),
      ),
    );
    final children = <Widget>[
      if (notice != null)
        Padding(
          padding: EdgeInsetsDirectional.fromSTEB(
            tokens.gutter,
            tokens.space2,
            tokens.gutter,
            tokens.space2,
          ),
          child: KitText(
            notice,
            key: const ValueKey('inbox-notice'),
            role: KitTextRole.secondary,
            tone: KitTextTone.secondary,
          ),
        ),
      if (entries.isNotEmpty)
        KitRowGroup(
          key: const ValueKey('inbox-list'),
          gapBefore: tokens.space2,
          leadingIcons: false,
          children: [
            KitAnimatedRows(
              children: [
                for (final (index, entry) in entries.indexed)
                  KeyedSubtree(
                    key: ValueKey(entry.key),
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        if (index > 0)
                          const KitDivider(inset: KitDividerInset.gutter),
                        Padding(
                          padding: EdgeInsetsDirectional.fromSTEB(
                            tokens.gutter,
                            tokens.space4,
                            tokens.gutter,
                            tokens.space4,
                          ),
                          child: _row(
                            context,
                            entry,
                            primary: index == primary,
                          ),
                        ),
                      ],
                    ),
                  ),
              ],
            ),
          ],
        )
      else ...[
        // The answer fills the top of the page; what finished sits under it.
        ConstrainedBox(
          constraints: BoxConstraints(
            minHeight: MediaQuery.sizeOf(context).height * _calmShare,
          ),
          child: Center(child: calm),
        ),
        if (finished.isNotEmpty)
          KitRowGroup(
            key: const ValueKey('inbox-finished'),
            label: l10n.inboxFinishedLabel,
            leadingIcons: false,
            children: [
              for (final item in finished) _finishedRow(context, item),
            ],
          ),
      ],
      SizedBox(height: KitScreen.endPadding(context)),
    ];
    final list = entries.isEmpty && finished.isEmpty
        // The calm line sits in the middle of the page, as a page state.
        ? LayoutBuilder(
            builder: (context, constraints) => ListView(
              key: const ValueKey('inbox-scroll'),
              physics: const AlwaysScrollableScrollPhysics(),
              children: [
                ConstrainedBox(
                  constraints: BoxConstraints(minHeight: constraints.maxHeight),
                  child: Center(child: calm),
                ),
              ],
            ),
          )
        : ListView(
            key: const ValueKey('inbox-scroll'),
            physics: const AlwaysScrollableScrollPhysics(),
            children: children,
          );
    return KitScreen(
      topBar: KitTopBar(
        title: l10n.inboxTitle,
        // "4 waiting": how much is left to clear, quiet under the title
        // (each row already carries its amber dot).
        subtitle: left == 0 ? null : l10n.goToNeedsYouHint(left),
        exitKey: const ValueKey('inbox-back'),
      ),
      width: KitScreenWidth.reading,
      // Empty, the calm answer says it itself.
      status: entries.isEmpty ? null : _unreachableStatus(context),
      loading: _opening,
      loadingLabel: l10n.inboxTitle,
      body: KitRefresh(onRefresh: _c.refreshChatFeed, child: list),
    );
  }

  /// The share of the page the calm answer takes above what finished.
  static const _calmShare = 0.4;

  /// How many finished conversations the empty Inbox lists.
  static const _finishedShown = 5;

  Widget _row(BuildContext context, InboxEntry entry, {required bool primary}) {
    final l10n = AppLocalizations.of(context);
    switch (entry) {
      case InboxFeedEntry(:final item, :final permission, :final owner):
        final place = _placeOf(context, item);
        final conversation = presentedSessionTitleText(item.title, l10n: l10n);
        final openLabel = l10n.inboxOpenLabel(KitBidi.auto(conversation));
        void open() => unawaited(_openChat(item));
        if (permission != null && owner != null) {
          final answers = PermissionAnswers.of(owner);
          void answer(String reply) =>
              unawaited(answerPermissionRequest(owner, permission, reply));
          final call = owner.permissionToolCall(permission);
          return permissionRequestCard(
            context,
            key: ValueKey('inbox-request-${permission.id}'),
            // One main action on the screen: the oldest row's Allow once.
            inList: !primary,
            permission: permission,
            // "waiting 4 min", counted from when this phone first showed it.
            since: requestFirstSeen(owner, permission.id),
            // The chat card's own question, without the project: the place
            // line above already names it ("Run npm install?").
            toolName: call?.tool,
            target: permission.permission == 'external_directory'
                ? call?.target
                : null,
            who: item.agentLabel ?? _serverOf(context, item),
            answered: answers.answerFor(permission.id),
            heldLabel: owner.delayedAnswers.heldLabel(permission.id),
            onUndo: () => owner.delayedAnswers.undo(permission.id),
            onAllow: () => answer('once'),
            onReject: () => answer('reject'),
          ).asRow(place: place, onOpen: open, openLabel: openLabel);
        }
        final feed = entry;
        // The agent's own words are isolated; the app's are not.
        final asked = feed.question ?? feed.formTitle;
        final title = asked == null
            ? l10n.inboxWaitingForAnswer
            : KitBidi.auto(asked);
        return KitRequestCard.ask(
          key: ValueKey('inbox-open-${item.identity}'),
          kind: feed.formTitle != null && feed.question == null
              ? KitRequestKind.form
              : KitRequestKind.question,
          title: title,
          who: item.agentLabel ?? _serverOf(context, item),
          reason: KitNeedsYouReason.decision,
          ifIgnored: l10n.chatRequestIfIgnored,
          announcement: title,
        ).asRow(
          place: place,
          onOpen: open,
          openLabel: openLabel,
          openKey: ValueKey('inbox-open-button-${item.identity}'),
        );
      case InboxWatchedEntry(:final profile, :final request):
        final server = serverDisplayName(
          profile,
          l10n,
          among: _c.store.profiles,
        );
        final shown = displaySessionTitleText(request.title);
        final title = request.kind == MonitoredRequestKind.permission
            ? l10n.inboxAsksPermission
            : l10n.inboxAsksQuestion;
        final folder = _folderName(request.directory);
        final place = _joinPlace([
          server,
          ?folder,
          if (shown.isNotEmpty) shown,
        ]);
        void open() => unawaited(_openWatched(entry));
        return KitRequestCard.ask(
          key: ValueKey('inbox-watched-${entry.key}'),
          kind: request.kind == MonitoredRequestKind.form
              ? KitRequestKind.form
              : KitRequestKind.question,
          title: title,
          who: server,
          reason: request.kind == MonitoredRequestKind.permission
              ? KitNeedsYouReason.consent
              : KitNeedsYouReason.decision,
          ifIgnored: l10n.profileMonitorIfIgnored,
          announcement: title,
        ).asRow(
          place: place,
          onOpen: open,
          openLabel: l10n.inboxOpenOnServer(KitBidi.auto(server)),
          openKey: ValueKey('inbox-open-button-${entry.key}'),
        );
    }
  }

  Widget _finishedRow(BuildContext context, ChatFeedItem item) {
    final l10n = AppLocalizations.of(context);
    final now = clock.now();
    return KitFeedItem(
      key: ValueKey('inbox-finished-${item.identity}'),
      project: item.projectName,
      agentMark: item.agentLabel == null ? null : agentMark(item.agentId),
      agent: item.agentLabel,
      title: presentedSessionTitleText(item.title, l10n: l10n),
      state: item.stoppedByPerson ? l10n.chatsHomeStopped : l10n.chatsHomeDone,
      stateTone: item.stoppedByPerson
          ? KitFeedStateTone.quiet
          : KitFeedStateTone.done,
      time: relativeAgeLabel(
        now.difference(item.lastActivity),
        at: item.lastActivity,
        l10n: l10n,
      ),
      onTap: () => unawaited(_openChat(item)),
    );
  }

  /// "Laptop · shopfront · Fix flaky checkout test": where the request
  /// waits, the agent's name first for an agent on this phone.
  String _placeOf(BuildContext context, ChatFeedItem item) {
    final l10n = AppLocalizations.of(context);
    return _joinPlace([
      item.agentLabel ?? _serverOf(context, item),
      if (item.projectName.trim().isNotEmpty) item.projectName,
      presentedSessionTitleText(item.title, l10n: l10n),
    ]);
  }

  /// The server a row runs on, named as the server switcher names it.
  String _serverOf(BuildContext context, ChatFeedItem item) {
    final l10n = AppLocalizations.of(context);
    final source = item.sourceId ?? '';
    final id = source.startsWith('profile:')
        ? source.substring('profile:'.length)
        : _c.profile?.id;
    final profiles = _c.store.profiles;
    final profile = profiles.where((p) => p.id == id).firstOrNull;
    if (profile == null) return item.sourceLabel ?? 'OpenCode';
    return serverDisplayName(profile, l10n, among: profiles);
  }

  /// Names the server or the person chose, each isolated (COPY-30), so the
  /// line reads in the page's direction with every name intact.
  static String _joinPlace(List<String> parts) =>
      parts.map(KitBidi.auto).join(' · ');

  static String? _folderName(String? directory) {
    if (directory == null) return null;
    final parts = directory.split('/').where((part) => part.isNotEmpty);
    return parts.isEmpty ? null : parts.last;
  }

  Future<void> _openChat(ChatFeedItem item) async {
    if (_opening) return;
    setState(() {
      _opening = true;
      _notice = null;
    });
    final problem = await ConnectionChatsHost(_c).openChat(context, item);
    if (!mounted) return;
    setState(() {
      _opening = false;
      _notice = problem;
    });
  }

  Future<void> _openWatched(InboxWatchedEntry entry) async {
    if (_opening) return;
    final profile = entry.profile;
    final request = entry.request;
    await openMonitoredRequest(
      context,
      _c,
      MonitoredRoute(
        profileID: profile.id,
        requestID: request.id,
        sessionID: request.sessionID,
        kind: request.kind,
        createdAt: entry.checkedAt,
        serverUrl: profile.baseUrl,
        sourceIdentity: ProfileMonitor.routeSourceIdentity(profile),
        directory: request.directory,
        workspace: request.workspace,
      ),
    );
  }

  /// The servers the list keeps but could not read, named and isolated;
  /// null when every one answered.
  String? _unreachableNames(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final ids = _c.chatFeed(_waitingFilter).unreachableServers;
    if (ids.isEmpty) return null;
    final profiles = _c.store.profiles;
    return KitBidi.auto(
      [
        for (final id in ids)
          serverDisplayName(
            profiles.where((p) => p.id == id).firstOrNull,
            l10n,
            among: profiles,
          ),
      ].join(', '),
    );
  }

  KitAction _retryAction(BuildContext context) => KitAction(
    key: const ValueKey('inbox-unreachable-retry'),
    label: AppLocalizations.of(context).commonRetry,
    onPressed: () {
      for (final id in _c.chatFeed(_waitingFilter).unreachableServers) {
        unawaited(_c.retryChatListSource(id));
      }
    },
  );

  /// "Studio Mac isn't answering · Try again" over a list: the Inbox never
  /// claims a server it could not read.
  KitStatus? _unreachableStatus(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final names = _unreachableNames(context);
    if (names == null) return null;
    return KitStatus(
      kind: KitStatusKind.info,
      id: 'inbox-unreachable',
      key: const ValueKey('inbox-unreachable'),
      icon: AppIconography.cloudOff,
      message: l10n.chatsHomeUnreachableShort(names),
      semanticsLabel: l10n.inboxUnreachable(names),
      quiet: true,
      action: _retryAction(context),
    );
  }
}
