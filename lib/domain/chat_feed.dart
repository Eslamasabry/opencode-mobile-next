/// Contract for "Chats first, project as a setting".
///
/// Home lists every conversation on the connected server across all projects;
/// the project is a property of a row, not a place the person must enter
/// first. This file is pure types plus one interface: no Flutter, no
/// transport. `ConnectionController` implements [ChatFeedSource]; the UI reads
/// only this contract and never touches `lib/api/` or `lib/api2/`.
///
/// Listening: the implementer is a `ChangeNotifier`, so UI rebuilds through
/// the controller it already watches. Every getter here is a cheap, pure
/// projection of state already held; reading never starts a request. Call
/// [ChatFeedSource.refreshChatFeed] to ask for a fresh server read.
library;

/// What a conversation needs from the person right now.
enum ChatStatus {
  /// Stopped on a pending permission, question or form.
  needsYou,

  /// The agent is working (session status busy or retrying).
  running,

  /// The last run ended in an error the person has not looked at.
  failed,

  /// Nothing is happening.
  idle,
}

/// One conversation row on Home. Immutable; compare by [sessionID].
class ChatFeedItem {
  const ChatFeedItem({
    required this.sessionID,
    required this.title,
    required this.directory,
    required this.projectName,
    required this.isGit,
    required this.status,
    required this.lastActivity,
    this.preview = '',
    this.parentID,
  });

  /// The server's session id; pass it to `selectLocationForExistingSession`
  /// (with [directory]) and then open the chat.
  final String sessionID;

  /// The session title, or 'New chat' when the server has none.
  final String title;

  /// Absolute folder of the project this chat lives in (never temporary).
  final String directory;

  /// The project's name as the server gives it, else its folder's name.
  final String projectName;

  /// True when the project is a Git repository (drives the Git badge).
  final bool isGit;

  final ChatStatus status;

  /// Last time the session changed, as the server reports it.
  final DateTime lastActivity;

  /// Plain-text last line of the conversation, whitespace collapsed and cut
  /// to a short length. Never an error text, a stack trace or a credential;
  /// empty when nothing is known yet (the UI shows nothing for it).
  final String preview;

  /// Set when this is a subagent's child session. Home excludes these unless
  /// [ChatFeedFilter.includeSubagents] is true.
  final String? parentID;

  bool get isSubagent => parentID != null;
}

/// Which rows [ChatFeedSource.chatFeed] returns. Value equality, so it can be
/// a map key or a provider argument.
class ChatFeedFilter {
  const ChatFeedFilter({
    this.projectDirectory,
    this.needsYou = false,
    this.running = false,
    this.includeSubagents = false,
  });

  /// Only this project's chats; null means every project.
  final String? projectDirectory;

  /// Only [ChatStatus.needsYou] rows.
  final bool needsYou;

  /// Only [ChatStatus.running] rows. With [needsYou] also set, rows matching
  /// either are kept (the chips are additive).
  final bool running;

  /// Include subagent child sessions (default: excluded).
  final bool includeSubagents;

  static const all = ChatFeedFilter();

  ChatFeedFilter copyWith({
    String? projectDirectory,
    bool clearProject = false,
    bool? needsYou,
    bool? running,
    bool? includeSubagents,
  }) => ChatFeedFilter(
    projectDirectory: clearProject
        ? null
        : projectDirectory ?? this.projectDirectory,
    needsYou: needsYou ?? this.needsYou,
    running: running ?? this.running,
    includeSubagents: includeSubagents ?? this.includeSubagents,
  );

  @override
  bool operator ==(Object other) =>
      other is ChatFeedFilter &&
      other.projectDirectory == projectDirectory &&
      other.needsYou == needsYou &&
      other.running == running &&
      other.includeSubagents == includeSubagents;

  @override
  int get hashCode =>
      Object.hash(projectDirectory, needsYou, running, includeSubagents);
}

/// The feed for one [ChatFeedFilter].
class ChatFeedSnapshot {
  const ChatFeedSnapshot({
    required this.items,
    this.acrossProjects = true,
    this.loading = false,
    this.complete = true,
  });

  /// Needs-you rows first, then running, then the rest, each group newest
  /// first. Failed rows sort with the rest by time. Stable and unmodifiable.
  final List<ChatFeedItem> items;

  /// False when the server cannot list across projects and [items] holds the
  /// current project's chats only. The UI then shows one quiet line such as
  /// "Showing this project's chats". Mirrors
  /// [ChatFeedSource.chatFeedAcrossProjects].
  final bool acrossProjects;

  /// A read is in flight and no rows are known yet (show a skeleton).
  final bool loading;

  /// False while only the first page is loaded or the last read failed:
  /// rows are real but older chats may be missing. Never an error string;
  /// the UI may offer a refresh.
  final bool complete;
}

/// One real project, for the "All projects" sheet and the start screen.
class ProjectSummary {
  const ProjectSummary({
    required this.directory,
    required this.name,
    required this.isGit,
    required this.chatCount,
    required this.runningCount,
    required this.needsYouCount,
    this.lastActivity,
    this.kind,
  });

  /// Absolute folder; the stable id of the project in this contract.
  final String directory;

  /// The server's project name, else the folder's name.
  final String name;
  final bool isGit;

  /// Top-level chats (subagents not counted).
  final int chatCount;
  final int runningCount;
  final int needsYouCount;

  /// Latest chat activity in the project; null when it has no chat yet.
  final DateTime? lastActivity;

  /// `PhoneProjectKind.name` ('dart', 'node', 'git', ...) when the scanner
  /// knows it (phone-hosted projects); otherwise null. A name rather than the
  /// enum because the scanner lives in `lib/platform/` and imports Flutter.
  final String? kind;
}

/// The Home / start-screen data source. `ConnectionController` implements it.
abstract interface class ChatFeedSource {
  /// True when the connected server can list sessions across projects
  /// (`ServerCapabilities.globalSessionSearch`). When false, [chatFeed] and
  /// [projectSummaries] describe the current project only.
  bool get chatFeedAcrossProjects;

  /// The rows for [filter]; see [ChatFeedSnapshot.items] for the order.
  /// Pure and cheap: reads held state, starts no request.
  ChatFeedSnapshot chatFeed([ChatFeedFilter filter = ChatFeedFilter.all]);

  /// Real projects with chat counts, most recently active first. Temporary
  /// folders are omitted. The last-used project is always present once it
  /// exists on the server, even with no chat yet.
  List<ProjectSummary> get projectSummaries;

  /// Asks the server for a fresh list (all projects when supported).
  /// Safe to call repeatedly; concurrent calls share one read. Completes
  /// quietly when offline: the feed keeps what it had.
  Future<void> refreshChatFeed();

  /// The project the person last started or opened a chat in on this server,
  /// persisted per profile under `oc.lastProject.<profileId>` and removed with
  /// the profile. Null on first use, or when the saved folder is temporary or
  /// was found missing. Never a temporary folder.
  String? get lastUsedProjectDirectory;

  /// Records [directory] as last used (startChatIn and opening a chat do this
  /// themselves). Ignored for temporary folders.
  Future<void> rememberLastUsedProject(String directory);

  /// Creates a session in [directory] and returns its id. Rescopes the
  /// connection to that project first, remembers it as last used, and, when
  /// [firstPrompt] is non-empty, sends it as the first message. Throws a
  /// product exception with a plain sentence on failure (offline, folder
  /// refused); the UI shows that sentence, never a raw error.
  /// A temporary or protected folder is refused.
  Future<String> startChatIn(String directory, {String? firstPrompt});

  /// True for folders that are never shown as projects; see
  /// [isTemporaryProjectDirectory].
  bool isTemporaryProject(String? directory);
}

/// Pure rule behind [ChatFeedSource.isTemporaryProject]: `/tmp`, `tmp`,
/// `/var/tmp`, `/private/tmp`, `/var/folders/...`, Android and Termux temp
/// dirs, Windows `Temp` shapes, anything under them, the server's global
/// project (`/`, `global`) and an empty or missing directory.
bool isTemporaryProjectDirectory(String? directory) {
  if (directory == null) return true;
  var value = directory.trim().replaceAll('\\', '/');
  while (value.length > 1 && value.endsWith('/')) {
    value = value.substring(0, value.length - 1);
  }
  if (value.isEmpty || value == '/' || value == 'global' || value == '.') {
    return true;
  }
  final lower = value.toLowerCase();
  const roots = [
    '/tmp',
    'tmp',
    '/var/tmp',
    '/private/tmp',
    '/private/var/tmp',
    '/var/folders',
    '/private/var/folders',
    '/data/local/tmp',
    '/data/data/com.termux/files/usr/tmp',
  ];
  for (final root in roots) {
    if (lower == root || lower.startsWith('$root/')) return true;
  }
  return RegExp(r'^[a-z]:/windows/temp(/|$)').hasMatch(lower) ||
      RegExp(r'/appdata/local/temp(/|$)').hasMatch(lower);
}
