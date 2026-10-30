import 'dart:async';

import 'package:clock/clock.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../agents/agents_text.dart' show agentMark;
import '../../../domain/chat_feed.dart';
import '../../../domain/relative_age.dart';
import '../../../l10n/app_localizations.dart';
import '../../../state/factsbook/factsbook_flag.dart';
import '../../../state/session_inventory_cache.dart' show SessionPreview;
import '../../app_iconography.dart';
import '../../kit/kit.dart';
import '../../widgets/session_title.dart';
import '../agents/agent_notices.dart';
import '../factsbook/memory_section.dart';
import '../home_opening.dart';
import '../servers_screen.dart' show ServersRouteRequest;
import 'chats_host.dart';
import 'chats_project_sheet.dart';
import 'chats_sources_sheet.dart' show showChatsSourcesSheet;
import 'new_chat_screen.dart';

/// Chats home ("chats first, project as a setting"): every conversation on
/// the connected server in one list, grouped Needs you, Today, Earlier, with
/// the project as a small label on each row and as a filter, never a place
/// to enter first.
///
/// [initialFilter] lets old entry points land with Running already on; a
/// new value arriving later replaces the filter. "Needs you" is not a filter
/// any more: its chip opens the Inbox (vision, 2026-10-20). The server pill and search belong to the shell's
/// own bar; this screen draws none.
class ChatsHomeScreen extends ConsumerStatefulWidget {
  const ChatsHomeScreen({super.key, this.initialFilter, this.opening});

  final ChatFeedFilter? initialFilter;

  /// Set while the saved server is still starting or connecting on a cold
  /// start: last time's titles stand in for the list, read-only.
  final HomeOpening? opening;

  @override
  ConsumerState<ChatsHomeScreen> createState() => _ChatsHomeScreenState();
}

final _noChanges = ValueNotifier<int>(0);

class _ChatsHomeScreenState extends ConsumerState<ChatsHomeScreen> {
  late ChatFeedFilter _filter = widget.initialFilter ?? ChatFeedFilter.all;

  /// Conversations already on screen: one that shows up later (read from
  /// another connection a moment after) arrives instead of popping in.
  final _seenRows = <String>{};
  String? _openingID;

  /// The row whose conversation is opening now (not while a question
  /// about it is asked): it says Opening… and the bar runs.
  String? _openingShown;
  String? _notice;

  /// The servers whose "isn't answering" line the person hid with ×: hidden
  /// until the set of servers not answering changes.
  Set<String>? _hiddenUnreachable;

  /// This list's page, and the page above it while that opens, stays or
  /// closes (the route's secondary animation; dismissed when nothing is
  /// above, and always on Android, where the page underneath never moves).
  ModalRoute<Object?>? _route;
  Animation<double>? _above;

  /// The feed as the list last showed it, with the filter it was read for:
  /// kept while a page above moves, so the row a chat closes back into is
  /// still where it was.
  ({ChatFeedFilter filter, ChatFeedSnapshot feed})? _shownFeed;

  /// The rows shown while the server opens ([ChatsHomeScreen.opening]),
  /// read-only. They stay after it answers until its own list has rows or
  /// has been read and holds none (or [_standInsHold] passed), so the rows
  /// never blink to an empty list or placeholders in between and nothing
  /// above them moves (jitter J2, 2026-10-29).
  List<ChatFeedItem>? _standIns;

  /// The server's list was seen loading since the opening ended.
  bool _standInsSawLoading = false;
  Timer? _standInsLimit;
  static const _standInsHold = Duration(seconds: 10);

  /// A page above is opening, open or closing: a conversation that grew out
  /// of one of these rows (KitPageOrigin), or any page that moves this one.
  bool get _held {
    final route = _route;
    if (route != null && KitPageOrigin.covers(route)) return true;
    return !(_above?.isDismissed ?? true);
  }

  @override
  void initState() {
    super.initState();
    KitPageOrigin.changes.addListener(_aboveChanged);
    if (widget.opening == null) _refreshAgentRowsAfterFrame();
  }

  /// Phone agents' conversations (Claude Code, …) come from their helper:
  /// reading the rows starts it if Android stopped it, so they show here.
  /// Once the server has answered: the opening leaves the phone to it.
  void _refreshAgentRowsAfterFrame() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      final agents = ref.read(chatsHostProvider).agents;
      if (agents != null && agents.phoneAgentsAvailable) {
        unawaited(agents.refreshAgentRows());
      }
    });
  }

  @override
  void didUpdateWidget(ChatsHomeScreen oldWidget) {
    super.didUpdateWidget(oldWidget);
    final next = widget.initialFilter;
    if (next != null && next != oldWidget.initialFilter) _filter = next;
    if (oldWidget.opening != null && widget.opening == null) {
      _refreshAgentRowsAfterFrame();
    }
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _route = ModalRoute.of(context);
    final above = _route?.secondaryAnimation;
    if (identical(above, _above)) return;
    _above?.removeStatusListener(_aboveMoved);
    _above = above?..addStatusListener(_aboveMoved);
  }

  void _aboveMoved(AnimationStatus status) {
    if (status == AnimationStatus.dismissed) _aboveChanged();
  }

  void _aboveChanged() {
    // The page above has gone: show the feed as it is now.
    if (mounted && !_held) setState(() {});
  }

  @override
  void dispose() {
    KitPageOrigin.changes.removeListener(_aboveChanged);
    _above?.removeStatusListener(_aboveMoved);
    _standInsLimit?.cancel();
    super.dispose();
  }

  bool get _statusFiltered => _filter.needsYou || _filter.running;

  void _toggleRunning() =>
      setState(() => _filter = _filter.copyWith(running: !_filter.running));

  void _clearFilters() => setState(() => _filter = ChatFeedFilter.all);

  Future<void> _pickProject(ChatsHost host) async {
    final source = host.source;
    final choice = await showChatsProjectSheet(
      context,
      projects: source.projectSummaries,
      allCount: source.chatFeed().items.length,
      selectedDirectory: _filter.projectDirectory,
      otherFoldersCount: source
          .chatFeed(const ChatFeedFilter(otherFolders: true))
          .items
          .length,
      otherFoldersSelected: _filter.otherFolders,
    );
    if (!mounted || choice == null) return;
    switch (choice) {
      case ChatsAllProjects():
        setState(
          () => _filter = _filter.copyWith(
            clearProject: true,
            otherFolders: false,
          ),
        );
      case ChatsOtherFolders():
        setState(
          () => _filter = _filter.copyWith(
            clearProject: true,
            otherFolders: true,
          ),
        );
      case ChatsOneProject(:final directory):
        setState(
          () => _filter = _filter.copyWith(
            projectDirectory: directory,
            otherFolders: false,
          ),
        );
      case ChatsOpenProject():
        // The first sheet is closed; this is a second, never on top of it.
        final directory = await host.openProject(context);
        if (!mounted || directory == null) return;
        if (source.isTemporaryProject(directory)) return;
        setState(
          () => _filter = _filter.copyWith(
            projectDirectory: directory,
            otherFolders: false,
          ),
        );
    }
  }

  Future<void> _open(
    ChatsHost host,
    ChatFeedItem item, {
    BuildContext? row,
  }) async {
    if (_openingID != null) return;
    // The conversation grows out of its row and shrinks back into it.
    if (row != null) KitPageOrigin.mark(row);
    setState(() {
      _openingID = item.sessionID;
      _notice = null;
    });
    String? problem;
    final notice = host.agents?.agentResumeNotice(item);
    if (notice != null && notice.requiresAcknowledgement) {
      // The old conversation cannot reopen: say what happens first, and
      // change nothing until the person agrees.
      final l10n = AppLocalizations.of(context);
      final agree = await showKitConfirm(
        context,
        title: l10n.agentsResumeNoticeTitle,
        body: l10n.agentsResumeNoticeBody(
          KitBidi.auto(item.agentLabel ?? item.agentId),
        ),
        confirmLabel: l10n.agentsStartNew,
        cancelLabel: l10n.agentsCancel,
        confirmKey: const ValueKey('agents-start-new'),
      );
      if (!mounted) return;
      if (agree) setState(() => _openingShown = item.sessionID);
      problem = agree ? await host.openNewChatReplacing(context, item) : null;
      if (agree) unawaited(host.source.refreshChatFeed());
    } else {
      setState(() => _openingShown = item.sessionID);
      problem = await host.openChat(context, item);
    }
    // Nothing opened (declined, or it failed): no later page grows from
    // the row.
    if (problem != null || _openingShown == null) KitPageOrigin.forget();
    if (!mounted) return;
    setState(() {
      _openingID = null;
      _openingShown = null;
      _notice = problem;
    });
  }

  @override
  Widget build(BuildContext context) {
    final host = ref.watch(chatsHostProvider);
    // Under a page that covers it (an open chat), the list would build the
    // whole feed again on every change of the connection without anyone
    // seeing it. It stops following then, and also while a page above it
    // opens or closes: a chat growing out of its row shrinks back into the
    // same row, so the rows hold still until it has landed. Uncovered and
    // still, this builds again and shows the feed as it is now.
    final follows = TickerMode.valuesOf(context).enabled && !_held;
    return ListenableBuilder(
      listenable: follows ? host.listenable ?? _noChanges : _noChanges,
      builder: (context, _) => _body(context, host),
    );
  }

  /// The feed to show: as it is now, or, while a page above this list
  /// moves, as the list last showed it for the same filter.
  ChatFeedSnapshot _feedOf(ChatFeedSource source) {
    final shown = _shownFeed;
    if (_held && shown != null && shown.filter == _filter) return shown.feed;
    final feed = source.chatFeed(_filter);
    _shownFeed = (filter: _filter, feed: feed);
    return feed;
  }

  /// A saved title as a row: no project, agent or state is known yet.
  static ChatFeedItem _standIn(SessionPreview row) => ChatFeedItem(
    sessionID: row.id,
    title: row.title,
    directory: '',
    projectName: '',
    isGit: false,
    status: ChatStatus.idle,
    lastActivity: DateTime.fromMillisecondsSinceEpoch(row.updated),
  );

  /// The feed to show, and whether its rows stand in, read-only, for a
  /// server that has not answered yet. While it opens these are the rows
  /// the list keeps from last time (the same rows its first live paint
  /// shows), else the titles saved with the opening. A filter on titles
  /// whose state and project are not known yet shows placeholders.
  (ChatFeedSnapshot, bool) _shownOf(ChatFeedSource source) {
    final live = _feedOf(source);
    final unfiltered = _filter == ChatFeedFilter.all;
    final opening = widget.opening;
    if (opening != null) {
      _standInsSawLoading = false;
      if (live.items.isNotEmpty) {
        _standIns = live.items;
        return (live, true);
      }
      _standIns = [for (final row in opening.rows) _standIn(row)];
    } else if (_standIns != null) {
      if (live.loading) _standInsSawLoading = true;
      if (live.items.isNotEmpty || (_standInsSawLoading && !live.loading)) {
        _dropStandIns();
        return (live, false);
      }
      // A list that never says it is loading still lets them go.
      _standInsLimit ??= Timer(_standInsHold, () {
        if (mounted) setState(_dropStandIns);
      });
    }
    final standIns = _standIns;
    if (standIns == null) return (live, false);
    return (
      ChatFeedSnapshot(
        items: unfiltered ? standIns : const [],
        loading: !unfiltered,
      ),
      true,
    );
  }

  void _dropStandIns() {
    _standIns = null;
    _standInsLimit?.cancel();
    _standInsLimit = null;
  }

  Widget _body(BuildContext context, ChatsHost host) {
    final l10n = AppLocalizations.of(context);
    final source = host.source;
    final tokens = KitTokens.of(context);
    final (snapshot, standIn) = _shownOf(source);
    final projects = source.projectSummaries;
    final projectDirectory = _filter.projectDirectory;
    final projectName = projectDirectory == null
        ? null
        : _nameOf(projectDirectory, projects);
    final listSources = host.listSources;
    final connections = listSources?.chatListSources ?? const [];
    final shownConnections = connections
        .where((connection) => connection.shown)
        .length;
    // The connections chip is only there when there is a choice to make:
    // more than one connection answering. One not answering says so in the
    // status line, with its Try again.
    final liveConnections = connections
        .where((connection) => !connection.unreachable)
        .length;
    final needsCount = host.inboxCount;

    // The one project a server that cannot list across projects shows.
    final onlyName =
        projectName ??
        (snapshot.items.isNotEmpty
            ? snapshot.items.first.projectName
            : projects.isNotEmpty
            ? projects.first.name
            : null);

    final unfiltered =
        _filter.projectDirectory == null &&
        !_filter.running &&
        !_filter.needsYou &&
        !_filter.otherFolders &&
        _filter.agentId == null;
    final nothingAtAll =
        unfiltered && snapshot.items.isEmpty && !snapshot.loading;
    final header = <Widget>[
      Padding(
        // Room under the bar's pill and gear before the page's big title
        // (concept 1): the home has no other header.
        // The needs-you line's own target gives the air under it.
        padding: needsCount > 0
            ? EdgeInsetsDirectional.only(top: tokens.space2)
            : EdgeInsetsDirectional.symmetric(vertical: tokens.space2),
        // A page title never breaks mid-word: past 1.5x it stops growing.
        child: KitText.capScale(
          max: 1.5,
          // "2 need you", said once under the title: the door to the Inbox
          // (vision gaps, 2026-10-21), everything that waits on the person
          // across projects and servers. It counts what the Inbox lists.
          child: KitPageHeading(
            title: l10n.chatsHomeTitle,
            line: needsCount > 0 ? _needsYouWords(l10n, needsCount) : null,
            lineNeedsYou: true,
            onLineTap: needsCount > 0
                ? () => unawaited(host.openInbox(context))
                : null,
            lineTapLabel: needsCount > 0
                ? '${_needsYouWords(l10n, needsCount)}, ${l10n.inboxTitle}'
                : null,
            lineTapKey: const ValueKey('chats-needs-you'),
          ),
        ),
      ),
      // Nothing to filter on a list that holds nothing yet: the calm state
      // alone (vision 2026-10-20). A filter that emptied the list keeps its
      // chips, so it can be changed back.
      if (!nothingAtAll)
        KitFilterChips(
          chips: [
            KitChip.summary(
              key: const ValueKey('chats-filter-project'),
              label: _filter.otherFolders
                  ? l10n.chatsHomeOtherFolders
                  : projectName == null
                  ? l10n.chatsHomeAllProjects
                  : KitBidi.auto(projectName),
              expanded: false,
              tone: projectName == null && !_filter.otherFolders
                  ? KitChipTone.neutral
                  : KitChipTone.active,
              // Projects are known once the server answers.
              onPressed: standIn ? () {} : () => unawaited(_pickProject(host)),
            ),
            // Which connections' conversations show, once there is more than
            // one (Termux beside the in-app Ubuntu, the agents).
            if (listSources != null && liveConnections > 1)
              KitChip.summary(
                key: const ValueKey('chats-filter-sources'),
                label: shownConnections == connections.length
                    ? l10n.chatsSourcesAll
                    : l10n.chatsSourcesSome(
                        shownConnections,
                        connections.length,
                      ),
                expanded: false,
                tone: shownConnections == connections.length
                    ? KitChipTone.neutral
                    : KitChipTone.active,
                onPressed: () => unawaited(
                  showChatsSourcesSheet(
                    context,
                    sources: listSources,
                    nameOf: (source) =>
                        host.connectionName(context, source.id, source.name),
                  ),
                ),
              ),
            KitFilterChip(
              key: const ValueKey('chats-filter-running'),
              label: l10n.chatsHomeRunning,
              selected: _filter.running,
              onPressed: _toggleRunning,
            ),
          ],
        ),
    ];

    // The quiet lines and notices scroll with the feed: they never take the
    // fixed header, so a small or landscape screen keeps its rows.
    final quietLines = <Widget>[
      // Rows standing in for a server that has not answered say nothing
      // about its reach yet: the line would come and go, moving the rows.
      if (!standIn && !snapshot.acrossProjects && onlyName != null)
        _QuietLine(
          key: const ValueKey('chats-only-project'),
          text: l10n.chatsHomeOnlyProject(KitBidi.auto(onlyName)),
        ),
      // A server the list shows but can't reach is the status line's
      // ([_unreachableStatus]); a list missing rows for another reason says
      // so here.
      if (snapshot.unreachableServers.isEmpty && !snapshot.complete)
        _incompleteLine(context, host, snapshot),
      if (_notice != null)
        _QuietLine(key: const ValueKey('chats-notice'), text: _notice!),
    ];
    final notices = <Widget>[
      // The saved server's card, when it needs the person on the way in.
      ?widget.opening?.notice,
      ...quietLines,
      // Room between the notes and the first section label.
      if (quietLines.isNotEmpty) SizedBox(height: tokens.space2),
      // The leftover-process notice: a quiet status with its Stop, drawn
      // only while the phone's watcher reports a helper.
      host.leftoverNotice(context),
      // Quiet lines about the agents on this phone, each with its action.
      AgentStatusNotices(host: host),
    ];
    // Rows show while an agent's own conversations are still being read:
    // the loading bar under the filters says more are on the way, and its
    // label names them. No line of its own: one that came and went above
    // the rows moved the whole list twice (jitter J6, 2026-10-28).
    final stillLoading =
        snapshot.stillLoading.isNotEmpty ||
        snapshot.stillLoadingServers.isNotEmpty;
    final loadingLabel = stillLoading
        ? l10n.chatsHomeStillLoading(
            KitBidi.auto(
              [
                for (final id in snapshot.stillLoadingServers)
                  host.connectionName(context, id, 'OpenCode'),
                ...snapshot.stillLoading,
              ].join(', '),
            ),
          )
        : l10n.chatsHomeTitle;
    final list = _list(
      context,
      host,
      snapshot,
      projectName,
      notices,
      standIn: standIn,
    );
    final screen = KitScreen(
      // A wide window (a tablet, a foldable's inner screen, the desktop)
      // keeps the list at a reading width, centred: each title stays close
      // to its project and time (audit P2-7).
      width: KitScreenWidth.reading,
      header: header,
      status: _unreachableStatus(context, host, snapshot),
      // The bar keeps moving while more conversations are on the way.
      loading:
          (snapshot.loading && snapshot.items.isEmpty) ||
          _openingShown != null ||
          stillLoading ||
          // Rows standing in for a server on its way; not while its card
          // says it needs the person.
          (standIn && widget.opening?.notice == null),
      loadingLabel: standIn
          ? widget.opening?.status ?? l10n.chatsHomeTitle
          : loadingLabel,
      body: KitRefresh(
        // Last time's titles are refreshed by the server answering.
        onRefresh: standIn ? () async {} : source.refreshChatFeed,
        child: list,
      ),
    );
    // Every empty state already offers its one primary action (Start a
    // conversation, Clear filters, Start in this project): no second one.
    final plainEmpty = snapshot.items.isEmpty && !snapshot.loading;
    if (plainEmpty) return screen;
    return KitFloatingAction(
      buttonKey: const ValueKey('chats-new-chat'),
      label: l10n.chatsHomeNewChat,
      icon: AppIconography.add,
      // Not floated until the server answers: it could not start one yet.
      onPressed: standIn
          ? null
          : () => unawaited(
              showNewChat(
                context,
                directory: projectDirectory ?? source.lastUsedProjectDirectory,
              ),
            ),
      child: screen,
    );
  }

  /// "Kilo couldn't load · Try again": the quiet line for a list missing
  /// rows. It names the connection when it is known and retries the list.
  Widget _incompleteLine(
    BuildContext context,
    ChatsHost host,
    ChatFeedSnapshot snapshot,
  ) {
    final l10n = AppLocalizations.of(context);
    // Several sources can share one label (each phone agent's list is
    // "Phone agents"): name each once.
    final names = {...snapshot.incompleteSources}.toList();
    final sources = host.listSources;
    void retry() {
      if (sources != null) {
        for (final source in sources.chatListSources) {
          if (source.shown && !source.main) {
            unawaited(sources.retryChatListSource(source.id));
          }
        }
      }
      unawaited(host.source.refreshChatFeed());
    }

    return KitStatusLine.of(
      KitStatus(
        kind: KitStatusKind.info,
        id: 'chats-incomplete',
        key: const ValueKey('chats-incomplete'),
        icon: AppIconography.cloudOff,
        message: names.isEmpty
            ? l10n.chatsHomeIncomplete
            : l10n.chatsHomeIncompleteNamed(KitBidi.auto(names.join(', '))),
        quiet: true,
        action: KitAction(
          key: const ValueKey('chats-incomplete-retry'),
          label: l10n.commonRetry,
          onPressed: retry,
        ),
      ),
    );
  }

  /// "Termux isn't answering · Try again", the one quiet status line under
  /// the bar, with × to hide it until another server stops (or starts)
  /// answering. Its full sentence is what a screen reader hears.
  KitStatus? _unreachableStatus(
    BuildContext context,
    ChatsHost host,
    ChatFeedSnapshot snapshot,
  ) {
    final ids = snapshot.unreachableServers.toSet();
    if (ids.isEmpty) return null;
    final hidden = _hiddenUnreachable;
    if (hidden != null &&
        hidden.length == ids.length &&
        hidden.containsAll(ids)) {
      return null;
    }
    final l10n = AppLocalizations.of(context);
    final names = KitBidi.auto(
      [
        for (final id in snapshot.unreachableServers)
          host.connectionName(context, id, 'OpenCode'),
      ].join(', '),
    );
    final sources = host.listSources;
    return KitStatus(
      kind: KitStatusKind.info,
      id: 'chats-unreachable',
      key: const ValueKey('chats-unreachable'),
      icon: AppIconography.cloudOff,
      message: l10n.chatsHomeUnreachableShort(names),
      semanticsLabel: l10n.chatsHomeUnreachable(names),
      quiet: true,
      action: sources == null
          ? null
          : KitAction(
              key: const ValueKey('chats-unreachable-retry'),
              label: l10n.commonRetry,
              onPressed: () {
                for (final id in ids) {
                  unawaited(sources.retryChatListSource(id));
                }
              },
            ),
      onDismiss: () => setState(() => _hiddenUnreachable = ids),
    );
  }

  Widget _list(
    BuildContext context,
    ChatsHost host,
    ChatFeedSnapshot snapshot,
    String? projectName,
    List<Widget> notices, {
    bool standIn = false,
  }) {
    final l10n = AppLocalizations.of(context);
    final tokens = KitTokens.of(context);
    final items = snapshot.items;
    final end = KitScreen.endPadding(context) + KitFloatingAction.clearance;
    // The developer flag shows FactsBook's Memory section under Needs you.
    final memory = ref.watch(factsBookEnabledProvider);
    if (items.isEmpty) {
      if (snapshot.loading) {
        return ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: EdgeInsets.only(top: tokens.space3),
          children: [...notices, const KitSkeletonRows()],
        );
      }
      // The calm empty states (vision 2026-10-20, "Both, toned down"): one
      // big calm line that says what the list holds, one plain next step,
      // and the main action at the bottom, where the floating + would be.
      // The screen's notes stay above, in the same scroll.
      void newChat(String? directory) =>
          unawaited(showNewChat(context, directory: directory));
      final header = Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [...notices, if (memory) const FactsBookMemorySection()],
      );
      final KitStateView state;
      if (_filter.running && !_filter.needsYou) {
        state = KitStateView.calm(
          key: const ValueKey('chats-empty-running'),
          icon: AppIconography.play,
          header: header,
          title: l10n.chatsHomeNothingRunning,
          body: l10n.chatsHomeNothingRunningBody,
          primary: KitAction(
            key: const ValueKey('chats-start-chat'),
            label: l10n.chatsHomeNewChat,
            icon: AppIconography.add,
            onPressed: () => newChat(
              _filter.projectDirectory ?? host.source.lastUsedProjectDirectory,
            ),
          ),
          tertiary: [
            KitAction(
              key: const ValueKey('chats-show-all'),
              label: l10n.chatsHomeShowAll,
              onPressed: _toggleRunning,
            ),
          ],
        );
      } else if (_statusFiltered) {
        state = KitStateView.calm(
          key: const ValueKey('chats-empty-filtered'),
          icon: AppIconography.search,
          header: header,
          title: l10n.chatsHomeNoMatchTitle,
          body: l10n.chatsHomeNoMatchBody,
          primary: KitAction(
            key: const ValueKey('chats-clear-filters'),
            label: l10n.chatsHomeClearFilters,
            onPressed: _clearFilters,
          ),
        );
      } else if (projectName != null) {
        state = KitStateView.calm(
          key: const ValueKey('chats-empty-project'),
          icon: AppIconography.chat,
          header: header,
          title: l10n.chatsHomeProjectEmptyTitle(KitBidi.auto(projectName)),
          body: l10n.chatsHomeProjectEmptyBody,
          primary: KitAction(
            key: const ValueKey('chats-start-in-project'),
            label: l10n.chatsHomeStartChatIn(KitBidi.auto(projectName)),
            onPressed: () => newChat(_filter.projectDirectory),
          ),
        );
      } else {
        // A person with no computer saved may have come for one: the quiet
        // second way in, only then.
        final computers = (host.listSources?.chatListSources ?? const []).where(
          (source) => !source.onThisPhone,
        );
        state = KitStateView.calm(
          key: const ValueKey('chats-empty'),
          icon: AppIconography.chat,
          header: header,
          title: l10n.chatsHomeEmptyTitle,
          body: l10n.chatsHomeEmptyCalmBody,
          primary: KitAction(
            key: const ValueKey('chats-start-chat'),
            label: l10n.chatsHomeNewChat,
            icon: AppIconography.add,
            onPressed: () => newChat(host.source.lastUsedProjectDirectory),
          ),
          tertiary: [
            if (computers.isEmpty)
              KitAction(
                key: const ValueKey('chats-connect-computer'),
                label: l10n.chatsHomeConnectComputer,
                icon: AppIconography.computer,
                onPressed: () => unawaited(
                  Navigator.of(context).pushNamed(
                    '/servers',
                    arguments: const ServersRouteRequest.add(),
                  ),
                ),
              ),
          ],
        );
      }
      return state;
    }

    final now = clock.now();
    bool today(ChatFeedItem item) {
      final at = item.lastActivity.toLocal();
      final n = now.toLocal();
      return at.year == n.year && at.month == n.month && at.day == n.day;
    }

    // The first rows painted are simply there; only later ones arrive.
    // Last time's titles are not counted: the server's first rows are the
    // first painted from it, so none of them plays an entrance.
    if (!standIn && _seenRows.isEmpty && items.isNotEmpty) {
      _seenRows.addAll(
        host.source
            .chatFeed(const ChatFeedFilter(includeSubagents: true))
            .items
            .map(_seenKey),
      );
    }
    // The agent's name shows only when the feed mixes agents or servers;
    // beside another server, OpenCode's rows name theirs.
    final servers = items.any(
      (item) => item.sourceId?.startsWith('profile:') ?? false,
    );
    final showAgent =
        servers || items.map((item) => item.agentId).toSet().length > 1;

    // A chat that needs you sorts first inside its time group; its amber
    // word and the chip already say it, so it has no group of its own.
    List<ChatFeedItem> needsFirst(Iterable<ChatFeedItem> group) => [
      for (final item in group)
        if (item.status == ChatStatus.needsYou) item,
      for (final item in group)
        if (item.status != ChatStatus.needsYou) item,
    ];
    final todays = needsFirst([
      for (final item in items)
        if (today(item) || item.lastActivity.isAfter(now)) item,
    ]);
    final earlier = needsFirst([
      for (final item in items)
        if (!(today(item) || item.lastActivity.isAfter(now))) item,
    ]);

    // The notices above it are not a section to separate from: the first
    // label keeps the zero gap it had when they sat in the fixed header.
    Widget section(
      String label,
      List<ChatFeedItem> group, {
      bool first = false,
    }) => KitSliverRowGroup(
      label: label,
      gapBefore: first ? 0 : null,
      leadingIcons: false,
      itemCount: group.length,
      itemBuilder: (context, index) {
        final item = group[index];
        // A conversation waiting on the person answers here: its request
        // card under the row (with Undo), no need to open it.
        final request = !standIn && item.status == ChatStatus.needsYou
            ? host.listRequest(context, item)
            : null;
        final shown = _row(
          context,
          host,
          item,
          now,
          showAgent: showAgent,
          servers: servers,
          standIn: standIn,
          request: request,
        );
        return KeyedSubtree(
          key: ValueKey('chats-row-${item.sessionID}'),
          child: !standIn && _seenRows.add(_seenKey(item))
              ? KitEntrance(child: shown)
              : shown,
        );
      },
    );

    return CustomScrollView(
      physics: const AlwaysScrollableScrollPhysics(),
      slivers: [
        SliverList.list(children: notices),
        if (memory) const SliverToBoxAdapter(child: FactsBookMemorySection()),
        if (todays.isNotEmpty)
          section(l10n.chatsHomeToday, todays, first: true),
        if (earlier.isNotEmpty)
          section(l10n.chatsHomeEarlier, earlier, first: todays.isEmpty),
        SliverToBoxAdapter(child: SizedBox(height: end)),
      ],
    );
  }

  Widget _row(
    BuildContext context,
    ChatsHost host,
    ChatFeedItem item,
    DateTime now, {
    required bool showAgent,
    bool servers = false,
    bool standIn = false,
    Widget? request,
  }) {
    final l10n = AppLocalizations.of(context);
    // The row being opened says so (an old Claude conversation takes a
    // few seconds to reopen on its helper).
    final opening = _openingShown == item.sessionID;
    final cantReopen =
        !standIn &&
        (host.agents?.agentResumeNotice(item).requiresAcknowledgement ?? false);
    // A state shows only when it matters; an ordinary conversation is just
    // its project and time.
    final (String? state, KitFeedStateTone tone) = opening
        ? (l10n.chatsHomeOpening, KitFeedStateTone.running)
        : switch (item.status) {
            ChatStatus.needsYou => (
              l10n.chatsHomeNeedsYou,
              KitFeedStateTone.needsYou,
            ),
            ChatStatus.running => (
              l10n.chatsHomeRowWorking,
              KitFeedStateTone.running,
            ),
            ChatStatus.failed => (l10n.chatsHomeFailed, KitFeedStateTone.quiet),
            _ when cantReopen => (
              l10n.agentsStateCantReopen,
              KitFeedStateTone.quiet,
            ),
            _ when item.finishedUnseen && item.stoppedByPerson => (
              l10n.chatsHomeStopped,
              KitFeedStateTone.quiet,
            ),
            _ when item.finishedUnseen => (
              l10n.chatsHomeDone,
              KitFeedStateTone.done,
            ),
            _ => (null, KitFeedStateTone.quiet),
          };
    // The row's own context: the conversation opens out of its box.
    return Builder(
      builder: (row) => KitFeedItem(
        project: item.projectName,
        // The agent's name leads the meta line when it is not the default
        // OpenCode; beside another server, OpenCode's rows name theirs.
        agentMark: item.agentLabel == null ? null : agentMark(item.agentId),
        agent:
            item.agentLabel ??
            (showAgent && servers && item.sourceLabel != null
                ? _serverOf(context, host, item)
                : null),
        // The server's dated placeholder reads "New conversation" (F3).
        title: presentedSessionTitleText(item.title, l10n: l10n),
        state: state,
        stateTone: tone,
        time: relativeAgeLabel(
          now.difference(item.lastActivity),
          at: item.lastActivity,
          l10n: l10n,
        ),
        tappableKey: opening ? const ValueKey('chats-row-opening') : null,
        request: request,
        running: !standIn && item.status == ChatStatus.running
            ? host.runningOf(item)
            : null,
        // Last time's titles open nothing until the server answers.
        onTap: standIn ? null : () => unawaited(_open(host, item, row: row)),
      ),
    );
  }

  /// A row as "already on screen" knows it: its conversation and folder.
  /// The source is left out, so a row that keeps its place while its
  /// connection's id is settled (this server's saved row, then its live
  /// one beside the agents) does not play an entrance again.
  static String _seenKey(ChatFeedItem item) =>
      '${item.sessionID}\u0000${item.directory}';

  /// The server an OpenCode row runs on, named as the switcher names it.
  /// "2 need you" ("Needs you" for one): KitNeedsYou's own words, without
  /// the separator a row's line puts after them.
  static String _needsYouWords(AppLocalizations l10n, int count) =>
      l10n.kitNeedsYouSpan(count).replaceFirst(RegExp(r'\s*·\s*$'), '');

  static String _serverOf(
    BuildContext context,
    ChatsHost host,
    ChatFeedItem item,
  ) {
    final source = item.sourceId ?? '';
    final id = source.startsWith('profile:')
        ? source.substring('profile:'.length)
        : host.listSources?.chatListSources
                  .where((connection) => connection.main)
                  .firstOrNull
                  ?.id ??
              '';
    return host.connectionName(context, id, item.sourceLabel ?? 'OpenCode');
  }

  static String _nameOf(String directory, List<ProjectSummary> projects) {
    for (final project in projects) {
      if (project.directory == directory) return project.name;
    }
    final parts = directory.split('/').where((part) => part.isNotEmpty);
    return parts.isEmpty ? directory : parts.last;
  }
}

/// One quiet line under the filters (partial data, a note).
class _QuietLine extends StatelessWidget {
  const _QuietLine({super.key, required this.text});

  final String text;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    return Padding(
      padding: EdgeInsetsDirectional.fromSTEB(
        tokens.gutter,
        tokens.space1,
        tokens.gutter,
        tokens.space1,
      ),
      child: KitText(
        text,
        role: KitTextRole.caption,
        tone: KitTextTone.secondary,
      ),
    );
  }
}
