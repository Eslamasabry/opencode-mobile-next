import 'dart:async';

import 'package:flutter/material.dart';

import '../../builtin/builtin_server.dart' show looksLikeInAppServer;
import '../../domain/chat_feed.dart';
import '../../domain/connection_status.dart' show ConnectionStatusPhase;
import '../../domain/models.dart' show PermissionRequest;
import '../../domain/profile_monitor.dart'
    show ProfileAttentionSnapshot, ProfileMonitorStatus;
import '../../l10n/app_localizations.dart';
import '../../state/connection.dart';
import '../../state/local_server_controls.dart';
import '../../state/profile_monitor.dart' show ProfileMonitor;
import '../../state/profiles.dart';
import '../app_theme.dart';
import '../kit/kit.dart';
import '../screens/chat/permission_sheet.dart'
    show PermissionAnswers, answerPermissionRequest, permissionSummary;
import '../screens/servers_screen.dart' show ServersRouteRequest;
import 'safety_confirms.dart';
import 'local_agent_server_entry.dart';
import 'phone_server_card.dart';
import 'termux_running_server_entry.dart';

/// What the person chose in the server switcher. The sheet only chooses; the
/// shell acts after it has closed, so navigation never runs from a context
/// that is being torn down.
sealed class ServerSwitcherChoice {
  const ServerSwitcherChoice();
}

/// Hand [request] to the Servers screen, which owns connecting, credentials,
/// adding and forgetting.
class ServerSwitcherOpenServers extends ServerSwitcherChoice {
  const ServerSwitcherOpenServers([this.request]);
  final ServersRouteRequest? request;
}

class ServerSwitcherOpenPhoneSetup extends ServerSwitcherChoice {
  const ServerSwitcherOpenPhoneSetup();
}

/// Open [item]'s conversation where it runs (a question with options or
/// free text, or a form, is answered in the chat: owner decision 1).
class ServerSwitcherOpenChat extends ServerSwitcherChoice {
  const ServerSwitcherOpenChat(this.item);
  final ChatFeedItem item;
}

/// A "This phone" menu action. It leaves for another screen (or removes the
/// server), so the shell runs it with [runPhoneServerAction] once the sheet
/// is gone.
class ServerSwitcherPhoneAction extends ServerSwitcherChoice {
  const ServerSwitcherPhoneAction(this.action, this.profileID, this.bytesUsed);
  final PhoneServerAction action;
  final String profileID;
  final int? bytesUsed;
}

/// The person confirmed leaving this server; [alreadyDisconnected] is true
/// when the phone card did it in place.
class ServerSwitcherLeave extends ServerSwitcherChoice {
  const ServerSwitcherLeave({this.alreadyDisconnected = false});
  final bool alreadyDisconnected;
}

/// The single door to servers while connected, opened from the home's
/// server pill: a sheet near the thumb (vision pick "A + B's question
/// line", docs/qa/vision-server-switcher-2026-10-20/README.md).
///
/// Kit only: one [showKitSheet] titled "Servers", every server on one
/// [KitRowGroup] panel, "Add a server" on its own quiet panel under it. A
/// confirmation raised inside it (Disconnect, Restart, Stop) replaces the
/// content in place (KIT-16).
Future<ServerSwitcherChoice?> showServerSwitcher(
  BuildContext context,
  ConnectionController controller,
) => showKitSheet<ServerSwitcherChoice>(
  context,
  title: _l10n(context).serverSwitcherTitle,
  body: (_) => ServerSwitcherSheet(controller: controller),
);

/// The switcher's body. Each saved server is one row that says its state in
/// plain words, from what the app really holds for it (see the audit in
/// docs/qa/vision-server-switcher-2026-10-20/README.md):
///
/// - the server in use: "Connected" in mint with the accent check, how many
///   conversations work there, or Offline / Reconnecting with Try again;
/// - a server the Conversations list keeps open beside it (its own live
///   connection): the same, and a permission it waits on is answered on the
///   row, the question, the exact command, Reject and Allow once;
/// - a server only the attention monitor watches: "Needs you" with Open
///   (the monitor keeps no command and no way to reply), how many work
///   there, or "Isn't answering · checks again in 4 min";
/// - any other: its name, and that its password must be entered again.
///
/// A question with options or free text, or a form, is "Needs you" with
/// Open, which opens that conversation. The phone's own servers (in the
/// app, in Termux, Claude Code) are rows of the same group. Most urgent
/// first: waiting on the person, then working, then the rest as saved.
class ServerSwitcherSheet extends StatelessWidget {
  const ServerSwitcherSheet({super.key, required this.controller});

  final ConnectionController controller;

  @override
  Widget build(BuildContext context) {
    // The other servers' words come from the one attention source, which an
    // isolated profile never reads.
    final monitor = controller.isIsolated ? null : controller.profileMonitor;
    final sides = _sides(controller);
    return ListenableBuilder(
      listenable: Listenable.merge([
        controller,
        ?monitor,
        PermissionAnswers.of(controller),
        controller.delayedAnswers,
        for (final side in sides.values) ...[
          side,
          PermissionAnswers.of(side),
          side.delayedAnswers,
        ],
      ]),
      // The row width decides whether a command fits beside Allow once.
      builder: (context, _) => LayoutBuilder(
        builder: (context, box) => _build(context, monitor, box.maxWidth),
      ),
    );
  }

  Widget _build(BuildContext context, ProfileMonitor? monitor, double width) {
    final l10n = _l10n(context);
    final tokens = KitTokens.of(context);
    final navigator = Navigator.of(context);
    final current = controller.profile;
    final profiles = controller.store.profiles;
    final sides = _sides(controller);
    final rows = _feedRows(controller);
    final unreachable = {
      for (final source in controller.chatListSources)
        if (source.unreachable) source.id,
    };
    // OpenCode inside this app is one "This phone" row, never rows named
    // after its address or the runtime it was saved with.
    final phone = phoneServerProfile(profiles, current?.id);
    final currentIsPhone = current != null && current.id == phone?.id;
    // The phone's own servers in Termux are their own rows, "Connected"
    // when in use: their saved sign-ins are never listed again.
    final connectedForRows = controller.api == null ? null : current?.id;
    final currentIsPhoneRow =
        current != null &&
        shownAsPhoneRow(current, among: profiles, preferID: connectedForRows);
    ProfileAttentionSnapshot? watchedOf(ServerProfile profile) =>
        monitor != null && controller.isProfileReadable(profile.id)
        ? monitor.snapshotFor(profile.id)
        : null;

    _ServerState stateOf(ServerProfile profile) {
      final side = sides[profile.id];
      return _ServerState(
        side: side,
        unreachable: unreachable.contains(profile.id),
        waiting: side == null ? null : _waitingOn(side, rows),
        watched: side == null ? watchedOf(profile) : null,
      );
    }

    final others = [
      for (final profile in profiles)
        if (profile.id != current?.id &&
            !looksLikeInAppServer(profile) &&
            !shownAsPhoneRow(
              profile,
              among: profiles,
              preferID: connectedForRows,
            ))
          profile,
    ];
    final states = {for (final p in others) p.id: stateOf(p)};
    final order = {for (final (i, p) in others.indexed) p.id: i};
    others.sort((a, b) {
      final byRank = states[a.id]!.rank.compareTo(states[b.id]!.rank);
      return byRank != 0 ? byRank : order[a.id]!.compareTo(order[b.id]!);
    });

    // A phone row leaves in place and keeps its server running.
    Future<void> disconnectInPlace() async {
      if (!await confirmDisconnectServer(context, controller)) return;
      await controller.disconnect(keepActive: true);
      if (navigator.mounted) {
        navigator.pop(const ServerSwitcherLeave(alreadyDisconnected: true));
      }
    }

    // Any other server: the shell leaves once the sheet has closed.
    Future<void> leave() async {
      if (!await confirmDisconnectServer(context, controller)) return;
      if (navigator.mounted) navigator.pop(const ServerSwitcherLeave());
    }

    void connect(ServerProfile profile) => navigator.pop(
      ServerSwitcherOpenServers(ServersRouteRequest.connect(profile.id)),
    );

    // "Open" on the server already behind this shell has nowhere to go but
    // back to it; reconnecting would drop live state.
    void openPhoneRow(ServerProfile profile) => navigator.pop(
      controller.api != null && profile.id == current?.id
          ? null
          : ServerSwitcherOpenServers(
              ServersRouteRequest.connect(profile.id, detectedRunning: true),
            ),
    );

    void openChat(ChatFeedItem item) =>
        navigator.pop(ServerSwitcherOpenChat(item));

    // What a waiting server's row says and offers, in one line and one act
    // (see [_ask]); Open goes to the waiting conversation, else the server.
    _Ask ask(_Waiting waiting, String name, VoidCallback openServer) => _ask(
      context,
      waiting,
      name: name,
      width: width,
      onOpen: () {
        final item = waiting.item;
        item == null ? openServer() : openChat(item);
      },
    );

    Widget phoneRow(ServerProfile profile) {
      final connected = controller.api != null && profile.id == current?.id;
      final waiting = connected ? _waitingOn(controller, rows) : null;
      final working = connected ? _workingCount(controller) : 0;
      final name = serverDisplayName(profile, l10n);
      final asked = waiting == null ? null : ask(waiting, name, () {});
      final act = asked?.act;
      return PhoneServerCard.switcherRow(
        key: ValueKey('server-switcher-phone-${profile.id}'),
        connection: controller,
        profile: profile,
        connected: connected,
        needsYou: asked != null && asked.needsYou,
        askLine: asked == null ? null : _line(asked.line),
        askAction: act == null
            ? null
            : _pill(context, act, primary: asked!.primary),
        stateDetail: working > 0 ? l10n.otherServerWorking(working) : null,
        onOpen: () => connect(profile),
        onAction: (action, bytesUsed) => navigator.pop(
          ServerSwitcherPhoneAction(action, profile.id, bytesUsed),
        ),
        // "Disconnect from this phone" lives in the row's own menu.
        onDisconnect: connected ? () => unawaited(leave()) : null,
      );
    }

    final connectedID = controller.api == null ? null : current?.id;
    // A live server the app found on this phone, and the Claude Code daemon
    // on it, are controlled where they are shown (plan 5.7). Each decides on
    // its own whether there is anything to show and draws its own hairline.
    final phoneEntries = <Widget>[
      TermuxRunningServerEntry(
        dividerAbove: true,
        profiles: profiles,
        busy: false,
        revision: 0,
        connectedProfileID: connectedID,
        busyConversations: controller.busySessions.length,
        actions: () {
          final controls = LocalServerControls(
            store: controller.store,
            connection: controller,
          );
          return LocalServerCardActions(
            restart: () async => controls.restart(),
            stop: controls.stop,
          );
        }(),
        onDisconnect: disconnectInPlace,
        onForget: (profile) => navigator.pop(
          ServerSwitcherOpenServers(ServersRouteRequest.forget(profile.id)),
        ),
        onManage: () => navigator.pop(const ServerSwitcherOpenPhoneSetup()),
        onConnect: openPhoneRow,
        onEnterCredentials: (server, existing) => navigator.pop(
          ServerSwitcherOpenServers(
            ServersRouteRequest.enterPhoneCredentials(
              profileID: existing?.id,
              openCode2: true,
            ),
          ),
        ),
      ),
      LocalAgentServerEntry(
        dividerAbove: true,
        profiles: profiles,
        busy: false,
        revision: 0,
        connectedProfileID: connectedID,
        busyConversations: controller.busySessions.length,
        onDisconnect: disconnectInPlace,
        onForget: (profile) => navigator.pop(
          ServerSwitcherOpenServers(ServersRouteRequest.forget(profile.id)),
        ),
        onManage: () => navigator.pop(const ServerSwitcherOpenPhoneSetup()),
        onConnect: openPhoneRow,
      ),
    ];

    final showCurrent =
        current != null && !currentIsPhone && !currentIsPhoneRow;
    final entries = <Widget>[
      if (currentIsPhone) phoneRow(current),
      if (showCurrent)
        () {
          final name = serverDisplayName(current, l10n, among: profiles);
          final waiting = _waitingOn(controller, rows);
          return _CurrentServerRow(
            key: const ValueKey('server-switcher-current-entry'),
            profile: current,
            name: name,
            status: controller.connectionStatus.phase,
            working: _workingCount(controller),
            ask: waiting == null ? null : ask(waiting, name, () {}),
            onRetry: () => unawaited(controller.retryConnection()),
            onDisconnect: () => unawaited(leave()),
          );
        }(),
      for (final profile in others)
        () {
          final name = serverDisplayName(profile, l10n, among: profiles);
          final state = states[profile.id]!;
          final waiting = state.unreachable ? null : state.waiting;
          return _SavedServerRow(
            key: ValueKey('server-switcher-profile-${profile.id}'),
            profile: profile,
            name: name,
            state: state,
            ask: waiting == null
                ? null
                : ask(waiting, name, () => connect(profile)),
            onTap: () => connect(profile),
            onRetry: () =>
                unawaited(controller.retryChatListSource(profile.id)),
          );
        }(),
      if (phone != null && !currentIsPhone) phoneRow(phone),
    ];

    return Column(
      key: const ValueKey('server-switcher-sheet'),
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        // Every server on one panel (R1), hairlines between them, every row
        // the same calm height: icon, name, one state line, one act.
        KitRowGroup(
          key: const ValueKey('server-switcher-saved'),
          margin: EdgeInsets.zero,
          children: [
            Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                KitAnimatedRows(
                  children: [
                    for (final (i, entry) in entries.indexed)
                      KeyedSubtree(
                        key: ValueKey('server-switcher-entry-${entry.key}'),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.stretch,
                          children: [
                            if (i > 0)
                              const KitDivider(inset: KitDividerInset.text),
                            entry,
                          ],
                        ),
                      ),
                  ],
                ),
                ...phoneEntries,
              ],
            ),
          ],
        ),
        SizedBox(height: tokens.space4),
        KitRowGroup(
          margin: EdgeInsets.zero,
          children: [
            KitRow(
              key: const ValueKey('server-switcher-add'),
              leading: KitRow.glyph(context, AppIconography.add),
              title: l10n.serverSwitcherAddServer,
              roomy: true,
              onTap: () => navigator.pop(
                const ServerSwitcherOpenServers(ServersRouteRequest.add()),
              ),
            ),
          ],
        ),
      ],
    );
  }
}

/// What is waiting on the person on one live connection: the first
/// permission ([permission]) and its conversation ([item]), or only a
/// question or form ([question] is its words). [count] counts every request.
class _Waiting {
  const _Waiting({
    required this.owner,
    required this.count,
    this.permission,
    this.item,
    this.question,
  });
  final ConnectionController owner;
  final int count;
  final PermissionRequest? permission;
  final ChatFeedItem? item;
  final String? question;
}

/// What the app holds for a saved server other than the one in use.
class _ServerState {
  const _ServerState({
    required this.side,
    required this.unreachable,
    required this.waiting,
    required this.watched,
  });

  /// Its own live connection, when the Conversations list keeps one.
  final ConnectionController? side;

  /// The list keeps it but cannot reach it now.
  final bool unreachable;
  final _Waiting? waiting;

  /// The monitor's view, read only when there is no live connection.
  final ProfileAttentionSnapshot? watched;

  ProfileAttentionSnapshot? get _current {
    final watched = this.watched;
    return watched != null && watched.isCurrent ? watched : null;
  }

  /// Requests waiting that the sheet cannot show (the monitor's count).
  int get watchedWaiting => _current?.requests.length ?? 0;

  int get working => side != null && !unreachable
      ? _workingCount(side!)
      : _current?.runningCount ?? 0;

  /// The monitor's last read failed; [nextCheck] is when it reads again.
  bool get watchedDown => watched?.status == ProfileMonitorStatus.unavailable;
  DateTime? get nextCheck => watched?.nextCheckAt;

  /// Most urgent first (R1): waiting on the person, then working, then not
  /// answering (it has a way forward), then the rest.
  int get rank => (!unreachable && waiting != null) || watchedWaiting > 0
      ? 0
      : working > 0
      ? 1
      : unreachable || watchedDown
      ? 2
      : 3;
}

/// Each other saved server's own live connection (the Conversations list
/// keeps one beside the main connection), found through the list's rows,
/// by profile id.
Map<String, ConnectionController> _sides(ConnectionController controller) {
  final found = <String, ConnectionController>{};
  for (final item in _feedRows(controller)) {
    final owner = controller.connectionForRow(item);
    final id = owner?.profile?.id;
    if (owner == null || identical(owner, controller) || id == null) continue;
    found.putIfAbsent(id, () => owner);
  }
  return found;
}

List<ChatFeedItem> _feedRows(ConnectionController controller) =>
    controller.chatFeed(const ChatFeedFilter(includeSubagents: true)).items;

/// The first permission [owner] waits on (gated on the server asking at
/// all), else its first question or form, each with the list row that opens
/// its conversation.
_Waiting? _waitingOn(ConnectionController owner, List<ChatFeedItem> rows) {
  final permissions = owner.capabilities.permissionRequests
      ? owner.awaitingPermissions.toList()
      : const <PermissionRequest>[];
  final count =
      permissions.length + owner.questions.length + owner.forms.length;
  if (count == 0) return null;
  ChatFeedItem? rowOf(String? sessionID) => rows
      .where(
        (row) =>
            row.sessionID == sessionID &&
            identical(owner.connectionForRow(row) ?? owner, owner),
      )
      .firstOrNull;
  if (permissions.isNotEmpty) {
    final permission = permissions.first;
    return _Waiting(
      owner: owner,
      count: count,
      permission: permission,
      item: rowOf(permission.sessionID),
    );
  }
  final question = owner.questions.values.firstOrNull;
  final form = owner.forms.values.firstOrNull;
  final words = question?.prompts.firstOrNull?.question.trim();
  return _Waiting(
    owner: owner,
    count: count,
    item: rowOf(question?.sessionID ?? form?.sessionID),
    question: words != null && words.isNotEmpty ? words : form?.title,
  );
}

/// Conversations working on [owner] now (subagents are part of their
/// parent's work).
int _workingCount(ConnectionController owner) => owner.busySessions
    .where((id) => owner.sessionsById[id]?.parentID == null)
    .length;

/// A waiting server's row: its one line ("Needs you · npm install") and its
/// one act, Allow once ([primary]: the mint pill), Open or Undo.
class _Ask {
  const _Ask({
    required this.line,
    required this.act,
    this.primary = false,
    this.needsYou = true,
  });
  final List<TextSpan> line;
  final KitAction? act;
  final bool primary;

  /// Still waiting (false once Allow once was tapped).
  final bool needsYou;
}

/// What the row of a server that waits on the person says and offers.
///
/// - Allow once (owner decision 1: permission asks only, always with the
///   exact command) only when one request waits, it is a shell command the
///   server lets the app answer, and "Needs you · `command`" fits the line
///   whole beside the pill: never allow what the row cuts off.
/// - Otherwise Open ([onOpen]): the waiting conversation, where the full
///   card with Reject and Always allow lives. Reject is not offered here.
/// - Once allowed: "Allowed · `command`" with Undo while the answer is held,
///   then "Allowed" until the server takes it.
_Ask _ask(
  BuildContext context,
  _Waiting waiting, {
  required String name,
  required double width,
  required VoidCallback onOpen,
}) {
  final l10n = _l10n(context);
  final owner = waiting.owner;
  final permission = waiting.permission;
  final mono = KitText.styleOf(
    context,
    KitTextRole.mono,
    tone: KitTextTone.primary,
  );
  final summary = permission == null ? null : permissionSummary(permission);
  final what = permission != null
      ? (summary == null
            ? null
            : TextSpan(text: KitBidi.ltr(summary), style: mono))
      : waiting.question == null
      ? null
      : TextSpan(text: KitBidi.auto(waiting.question!));
  if (permission != null) {
    final held = owner.delayedAnswers.heldLabel(permission.id);
    final sent = PermissionAnswers.of(owner).answerFor(permission.id);
    if (held != null) {
      return _Ask(
        line: [
          TextSpan(text: held),
          ?what,
        ],
        needsYou: false,
        act: KitAction(
          key: const ValueKey('server-switcher-undo'),
          label: l10n.kitUndoAction,
          semanticsLabel: l10n.serverSwitcherUndoLabel(name),
          onPressed: () => owner.delayedAnswers.undo(permission.id),
        ),
      );
    }
    if (sent != null && !sent.failed) {
      return _Ask(
        line: [
          TextSpan(text: l10n.chatRequestAnswerAllowed),
          ?what,
        ],
        needsYou: false,
        act: null,
      );
    }
  }
  final line = [_needsYou(context, waiting.count), ?what];
  final command = permission?.commandPreview;
  if (permission != null &&
      command != null &&
      waiting.count == 1 &&
      owner.capabilities.permissionRequests) {
    final allow = KitAction(
      key: ValueKey('server-switcher-allow-${permission.id}'),
      label: l10n.kitRequestAllowOnce,
      semanticsLabel: l10n.serverSwitcherAllowLabel(KitBidi.ltr(command), name),
      onPressed: () =>
          unawaited(answerPermissionRequest(owner, permission, 'once')),
    );
    if (_fitsBeside(context, _line(line), allow.label, width)) {
      return _Ask(line: line, act: allow, primary: true);
    }
  }
  return _Ask(
    line: line,
    act: KitAction(
      key: ValueKey('server-switcher-open-${owner.profile?.id ?? name}'),
      label: l10n.serverSwitcherOpenRequest,
      semanticsLabel: l10n.serverSwitcherOpenRequestLabel(
        waiting.item?.title ?? name,
      ),
      onPressed: onOpen,
    ),
  );
}

/// Whether [line] reads whole on one line of a switcher row [width] wide
/// with a pill saying [label] at its end (from 1.3× text the pill sits
/// under the words, so the line has the row's whole text width).
bool _fitsBeside(
  BuildContext context,
  InlineSpan line,
  String label,
  double width,
) {
  final tokens = KitTokens.of(context);
  final scaler = MediaQuery.textScalerOf(context);
  final direction = Directionality.of(context);
  final glyph = tokens.iconSize(context, KitRow.glyphSize);
  final lead = glyph > tokens.iconTileSize ? glyph : tokens.iconTileSize;
  var room = width - tokens.space4 - lead - tokens.space3;
  if (scaler.scale(1) < 1.3) {
    final pill = TextPainter(
      text: TextSpan(
        text: label,
        style: KitText.styleOf(
          context,
          KitTextRole.secondary,
          tone: KitTextTone.primary,
        ),
      ),
      textDirection: direction,
      textScaler: scaler,
      maxLines: 1,
    )..layout();
    room -=
        tokens.space2 +
        pill.width +
        2 * tokens.space3 +
        tokens.space2 +
        tokens.space1;
    pill.dispose();
  } else {
    room -= tokens.space4;
  }
  if (room <= 0) return false;
  final painter = TextPainter(
    text: TextSpan(style: tokens.rowSupporting, children: [line]),
    textDirection: direction,
    textScaler: scaler,
    maxLines: 1,
  )..layout(maxWidth: room);
  final fits = !painter.didExceedMaxLines;
  painter.dispose();
  return fits;
}

/// A row's one act (Allow once, Open, Try again, Undo) as a short kit pill
/// at its end; Allow once in the accent tint, the rest neutral. Screen
/// readers hear its target ("Try Home PC again").
Widget _pill(BuildContext context, KitAction action, {bool primary = false}) =>
    Padding(
      padding: EdgeInsetsDirectional.only(end: KitTokens.of(context).space2),
      child: Semantics(
        container: true,
        button: true,
        label: action.semanticsLabel,
        onTap: action.onPressed,
        excludeSemantics: true,
        child: KitChip.action(
          key: action.key,
          label: action.label,
          tone: primary ? KitChipTone.active : KitChipTone.neutral,
          onPressed: action.onPressed!,
        ),
      ),
    );

/// [_pill] at the row's end, or under the words from 1.3× text so the name
/// keeps its width (A11Y-8).
({Widget? trailing, Widget? below}) _act(
  BuildContext context,
  KitAction? action, {
  bool primary = false,
}) {
  if (action == null) return (trailing: null, below: null);
  final pill = _pill(context, action, primary: primary);
  if (MediaQuery.textScalerOf(context).scale(1) >= 1.3) {
    return (
      trailing: null,
      below: Padding(
        padding: EdgeInsetsDirectional.only(top: KitTokens.of(context).space2),
        child: Align(alignment: AlignmentDirectional.centerStart, child: pill),
      ),
    );
  }
  return (trailing: pill, below: null);
}

/// The words of a row's line, joined with " · ". The first may carry its
/// own tone (mint for the server in use, amber for "Needs you"). Each part
/// keeps its words together ("1 working" never breaks between "1" and
/// "working"); the line wraps between parts, or ends in an ellipsis.
TextSpan _line(List<TextSpan> parts) => TextSpan(
  children: [
    for (final (i, part) in parts.indexed) ...[
      if (i > 0) const TextSpan(text: ' · '),
      TextSpan(text: part.text?.replaceAll(' ', ' '), style: part.style),
    ],
  ],
);

TextSpan _needsYou(BuildContext context, int count) =>
    _bare(KitNeedsYou.span(context, count: count));

/// A kit span that ends in " · " (it leads a line), without it: here the
/// line joins its own parts.
TextSpan _bare(TextSpan span) => TextSpan(
  text: span.text?.replaceFirst(RegExp(r'\s*·\s*$'), ''),
  style: span.style,
);

/// The server the app is connected through: "Connected" in mint, how many
/// conversations work there, the accent check at the end. While something
/// waits there, its one line and act take the line and the check's place.
/// A tap opens its menu, where Disconnect is.
class _CurrentServerRow extends StatelessWidget {
  const _CurrentServerRow({
    super.key,
    required this.profile,
    required this.name,
    required this.status,
    required this.working,
    required this.ask,
    required this.onRetry,
    required this.onDisconnect,
  });

  final ServerProfile profile;

  /// The name told apart from any saved server that would read the same.
  final String name;

  /// The shared connection status (one grace period app-wide), so the
  /// word here always matches the shell's server pill.
  final ConnectionStatusPhase status;
  final int working;
  final _Ask? ask;
  final VoidCallback onRetry;
  final VoidCallback onDisconnect;

  @override
  Widget build(BuildContext context) {
    final l10n = _l10n(context);
    final connected = status == ConnectionStatusPhase.connected;
    final down = !connected && status != ConnectionStatusPhase.connecting;
    final ask = connected ? this.ask : null;
    final word = _statusLabel(l10n, status);
    final act = _act(
      context,
      down
          ? KitAction(
              key: const ValueKey('server-switcher-current-retry'),
              label: l10n.commonRetry,
              semanticsLabel: l10n.serverSwitcherTryAgainLabel(name),
              onPressed: onRetry,
            )
          : ask?.act,
      primary: ask?.primary ?? false,
    );
    return Semantics(
      selected: true,
      child: KitRow(
        key: const ValueKey('server-switcher-current'),
        leading: KitRow.glyph(
          context,
          _serverIcon(profile),
          needsYou: ask?.needsYou ?? false,
        ),
        title: name,
        supporting: _line(
          ask?.line ??
              [
                // Mint "Connected" through the kit's current-mark words.
                if (connected)
                  _bare(kitCurrentSpan(context, word))
                else
                  TextSpan(text: word),
                if (connected && working > 0)
                  TextSpan(text: l10n.otherServerWorking(working)),
              ],
        ),
        supportingKey: const ValueKey('server-switcher-current-status'),
        below: act.below,
        trailing:
            act.trailing ??
            (act.below == null && ask == null
                ? KitRow.check(
                    context,
                    key: const ValueKey('server-switcher-check'),
                  )
                : null),
        roomy: true,
        menuLabel: l10n.serverSwitcherCurrentMenu,
        menu: [
          KitMenuItem(
            key: const ValueKey('server-switcher-disconnect'),
            label: l10n.serverDisconnectFrom(name),
            icon: AppIconography.unlink,
            onSelected: onDisconnect,
          ),
        ],
      ),
    );
  }
}

/// A saved server other than the one in use: what it needs or does first
/// ("Needs you · npm install", "Isn't answering", "2 working"), from a live
/// connection or the monitor's current snapshot only, so an old or
/// unreadable one says nothing rather than something stale. Its tap
/// switches to it.
class _SavedServerRow extends StatelessWidget {
  const _SavedServerRow({
    super.key,
    required this.profile,
    required this.name,
    required this.state,
    required this.ask,
    required this.onTap,
    required this.onRetry,
  });

  final ServerProfile profile;
  final String name;
  final _ServerState state;

  /// The waiting request on its live connection, when there is one.
  final _Ask? ask;
  final VoidCallback onTap;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    final l10n = _l10n(context);
    final state = this.state;
    final ask = this.ask;
    final live = state.side != null || state.unreachable;
    final down = state.unreachable;
    final watchedWaiting = state.watchedWaiting;
    final working = down ? 0 : state.working;
    final reentry = profile.requiresPasswordReentry
        ? l10n.e7SetupPasswordRequired
        : profile.requiresCodexTokenReentry
        ? l10n.e7SetupTokenRequired
        : null;
    final strong = KitText.styleOf(
      context,
      KitTextRole.label,
      tone: KitTextTone.primary,
    );

    // Not answering: a live connection retries now; the monitor only says
    // when it reads again (it has no "now").
    final next = state.nextCheck;
    final minutes = next == null
        ? null
        : (next.difference(DateTime.now()).inSeconds / 60).ceil();
    final parts = <TextSpan>[
      if (ask != null)
        ...ask.line
      else ...[
        // Muted: nobody has to act, the app tries again by itself.
        if (down || (!live && state.watchedDown))
          TextSpan(text: l10n.serverSwitcherNotAnswering),
        if (!live && state.watchedDown && minutes != null && minutes > 0)
          TextSpan(text: l10n.serverSwitcherChecksAgain(minutes)),
        // The monitor counts requests but keeps no command: "Needs you".
        if (watchedWaiting > 0) _needsYou(context, watchedWaiting),
        if (working > 0) TextSpan(text: l10n.otherServerWorking(working)),
        if (live && !down && watchedWaiting == 0 && working == 0)
          TextSpan(text: l10n.e7WorkspaceConnected),
      ],
      if (reentry != null) TextSpan(text: reentry, style: strong),
    ];

    // The one act the row needs now, short, at its end: Try again on a
    // live connection that isn't answering; Allow once, Open or Undo for
    // what waits there; Open (switch to it) for what the monitor counted.
    final act = _act(
      context,
      down
          ? KitAction(
              key: ValueKey('server-switcher-retry-${profile.id}'),
              label: l10n.commonRetry,
              semanticsLabel: l10n.serverSwitcherTryAgainLabel(name),
              onPressed: onRetry,
            )
          : ask != null
          ? ask.act
          : watchedWaiting > 0
          ? KitAction(
              key: ValueKey('server-switcher-open-${profile.id}'),
              label: l10n.serverSwitcherOpenRequest,
              semanticsLabel: l10n.serverSwitcherOpenRequestLabel(name),
              onPressed: onTap,
            )
          : null,
      primary: ask?.primary ?? false,
    );

    return KitRow(
      leading: KitRow.glyph(
        context,
        _serverIcon(profile),
        needsYou: (ask?.needsYou ?? false) || watchedWaiting > 0,
      ),
      title: name,
      supporting: parts.isEmpty ? null : _line(parts),
      supportingKey: ValueKey('server-switcher-profile-${profile.id}-status'),
      below: act.below,
      trailing: act.trailing,
      roomy: true,
      onTap: onTap,
    );
  }
}

IconData _serverIcon(ServerProfile profile) =>
    isPhoneOwnServer(profile) ? AppIconography.phone : AppIconography.server;

/// The same words as the shell's server pill for the same phase.
String _statusLabel(AppLocalizations l10n, ConnectionStatusPhase status) =>
    switch (status) {
      ConnectionStatusPhase.connected => l10n.e7WorkspaceConnected,
      ConnectionStatusPhase.connecting => l10n.e7WorkspaceConnecting,
      ConnectionStatusPhase.reconnecting => l10n.mcpReconnecting,
      _ => l10n.e7WorkspaceOffline,
    };

AppLocalizations _l10n(BuildContext context) =>
    Localizations.of<AppLocalizations>(context, AppLocalizations) ??
    lookupAppLocalizations(Localizations.localeOf(context));
