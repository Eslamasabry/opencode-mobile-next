import '../../../domain/chat_feed.dart';
import '../../../domain/models.dart' show PermissionRequest;
import '../../../domain/profile_monitor.dart';
import '../../../state/connection.dart';
import '../../../state/profiles.dart' show ServerProfile;

/// One thing that waits on the person, as the Inbox lists it
/// (docs/qa/vision-inbox-2026-10-20/README.md §1).
sealed class InboxEntry {
  const InboxEntry();

  /// Stable across rebuilds, for the row's entrance and exit.
  String get key;
}

/// A conversation in the feed (the server in use, its other projects, a
/// side connection, an agent on this phone) that waits on the person.
class InboxFeedEntry extends InboxEntry {
  const InboxFeedEntry({
    required this.item,
    this.permission,
    this.owner,
    this.question,
    this.formTitle,
  });

  final ChatFeedItem item;

  /// The permission it waits on, answerable here through [owner]; null
  /// when what waits is a question, a form, a card or only known by id.
  final PermissionRequest? permission;
  final ConnectionController? owner;

  /// A question's own words, when the app holds them (not a plan).
  final String? question;

  /// A form's title, when the app holds it.
  final String? formTitle;

  @override
  String get key => 'feed:${item.identity}';
}

/// A request on a watched server the app has no live connection to: the
/// monitor keeps its kind and conversation, never its command, and there is
/// no way to answer it from here.
class InboxWatchedEntry extends InboxEntry {
  const InboxWatchedEntry({
    required this.profile,
    required this.request,
    required this.checkedAt,
  });

  final ServerProfile profile;
  final MonitoredRequest request;
  final DateTime checkedAt;

  @override
  String get key => 'watched:${profile.id}:${request.identity}';
}

/// Everything that waits on the person across connections, oldest first.
///
/// No request carries the time it was asked (OC1, OC2, Paseo, Codex), so
/// "oldest" is the conversation's last activity: a conversation that waits
/// last changed when it stopped to ask. Requests on watched servers follow,
/// in the monitor's order. Nothing here starts a read.
List<InboxEntry> inboxEntries(ConnectionController controller) {
  final rows =
      [
        ...controller
            .chatFeed(
              const ChatFeedFilter(needsYou: true, includeSubagents: true),
            )
            .items,
      ]..sort((a, b) {
        final byTime = a.lastActivity.compareTo(b.lastActivity);
        return byTime != 0 ? byTime : a.identity.compareTo(b.identity);
      });
  final entries = <InboxEntry>[
    for (final item in rows) _feedEntry(controller, item),
  ];

  // Watched servers: only the ones with no live connection here (the server
  // in use and the side connections already speak for themselves above).
  final monitor = controller.isIsolated ? null : controller.profileMonitor;
  if (monitor == null) return entries;
  final live = {
    ?controller.profile?.id,
    for (final source in controller.chatListSources)
      if (source.shown) source.id,
  };
  for (final profile in controller.store.profiles) {
    if (live.contains(profile.id) ||
        !controller.isProfileReadable(profile.id)) {
      continue;
    }
    final snapshot = monitor.snapshotFor(profile.id);
    final checkedAt = snapshot.checkedAt;
    if (!snapshot.isCurrent || checkedAt == null) continue;
    for (final request in snapshot.requests) {
      if (request.kind == MonitoredRequestKind.checkIn) continue;
      entries.add(
        InboxWatchedEntry(
          profile: profile,
          request: request,
          checkedAt: checkedAt,
        ),
      );
    }
  }
  return entries;
}

/// How many things the Inbox lists: the count on its doors.
int inboxCount(ConnectionController controller) =>
    inboxEntries(controller).length;

/// Conversations whose last run finished after the person last opened
/// them, newest first: what the empty Inbox shows under its one line.
List<ChatFeedItem> inboxFinished(ConnectionController controller) => [
  for (final item in controller.chatFeed().items)
    if (item.finishedUnseen && item.status == ChatStatus.idle) item,
]..sort((a, b) => b.lastActivity.compareTo(a.lastActivity));

InboxFeedEntry _feedEntry(ConnectionController controller, ChatFeedItem item) {
  // The same owners the Conversations list answers through: an agent on
  // this phone answers on the main connection's own feed, a side server on
  // its own connection.
  final native = item.sourceId?.startsWith('paseo:') ?? false;
  final owner = controller.connectionForRow(item);
  final PermissionRequest? permission;
  final ConnectionController? answerer;
  if (native) {
    permission = controller.permissionForFeedItem(item);
    answerer = controller;
  } else if (owner != null && owner.capabilities.permissionRequests) {
    permission = owner.permissionsForSession(item.sessionID).firstOrNull;
    answerer = owner;
  } else {
    permission = null;
    answerer = null;
  }
  if (permission != null) {
    return InboxFeedEntry(item: item, permission: permission, owner: answerer);
  }
  final question =
      controller.questionForFeedItem(item) ??
      owner?.questions.values
          .where((question) => question.sessionID == item.sessionID)
          .firstOrNull;
  final prompt = question?.prompts.firstOrNull;
  final words = prompt == null || prompt.markdown
      ? null
      : prompt.question.trim().isNotEmpty
      ? prompt.question.trim()
      : prompt.title.trim();
  final form = controller.formRequestForFeedItem(item)?.form.title?.trim();
  return InboxFeedEntry(
    item: item,
    question: words == null || words.isEmpty ? null : words,
    formTitle: form == null || form.isEmpty ? null : form,
  );
}
