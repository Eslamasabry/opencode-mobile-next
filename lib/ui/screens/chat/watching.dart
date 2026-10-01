part of '../chat_screen.dart';

/// How the chat page shows a conversation someone else drives: an AI Team
/// worker's OpenCode session, which the team's own driver (Gas City's
/// `opencode acp`) owns. The page is the ordinary chat, read-only, with a
/// composer that writes to the worker through the caller's path:
///
/// - one slim status line says who is being watched and how it is doing,
///   from its session ([banner], read again when [changes] notifies);
/// - the composer addresses the worker ([hint]) and hands the
///   words to [onSend] (the team's message control), so nothing is ever
///   typed into the watched session; the words are kept as a draft per
///   worker and server ([draftId]) until the team takes them, and the
///   message's receipt ([receipt]) sits above the composer;
/// - nothing that changes the session is offered: no conversation menu
///   (share, fork, revert, rename, delete, compact), no message actions
///   beyond copy, no approvals, questions or forms answered here, no read
///   receipts, no model or agent choice;
/// - the worker's own page ([onDetails]: its state, controls and technical
///   details) is the top bar's one action;
/// - it reads the transcript again every [pollInterval]: the watched
///   session runs in another OpenCode process on the same store, so the
///   connected server's event stream never carries its updates.
///
/// When the worker's session cannot be read at all, [TeamWatchLiveScreen]
/// is the same watching page drawn from the team's live output.
class ChatWatch {
  const ChatWatch({
    required this.banner,
    required this.hint,
    this.hintOf,
    this.title,
    this.empty,
    this.sessionTitle,
    this.readOnlyReason,
    this.onSend,
    this.draftId,
    this.receipt,
    this.changes,
    this.detailsLabel,
    this.onDetails,
    this.pollInterval = const Duration(seconds: 4),
  });

  /// The status line and its tone: "Watching furiosa · Worker · Working".
  final (String, AppStatusTone) Function() banner;

  /// The composer's hint and accessible name: "Message furiosa…".
  final String hint;

  /// The hint read again when [changes] notifies (the role names load late);
  /// [hint] when null.
  final String Function()? hintOf;

  /// The top bar's title: the task the worker is on, not the worker's
  /// internal session title; null (or a null result) keeps the session's.
  final String? Function()? title;

  /// What the agent is doing while its conversation has no messages yet
  /// (title, body), from the team's state; null keeps the plain words.
  final (String, String) Function()? empty;

  /// The chat writes the watched session's own (internal) title here, for
  /// the worker's own page to show under its technical details.
  final ValueNotifier<String?>? sessionTitle;

  /// Why nothing can be typed, shown in the composer when [onSend] is null:
  /// "This team can't be messaged from here."
  final String? readOnlyReason;

  /// Sends the person's words the caller's way (the team's message
  /// control); true when they were taken, so the field and its draft are
  /// cleared. The chat never sends into the watched session.
  final Future<bool> Function(String text)? onSend;

  /// Keeps the unsent words per worker and server
  /// (`oc.draft.team-message.<draftId>.<profileId>`); null keeps none.
  final String? draftId;

  /// The newest message's receipt, above the composer; null for none.
  final Widget? Function(BuildContext context)? receipt;

  /// Notifies when [banner] or [receipt] may read differently.
  final Listenable? changes;

  /// The top bar's action to the worker's own page ("About furiosa"),
  /// with [onDetails].
  final String? detailsLabel;
  final void Function(BuildContext context)? onDetails;

  /// How often the transcript is read again.
  final Duration pollInterval;
}

extension _ChatWatching on _ChatScreenState {
  bool get _watching => widget.watch != null;

  /// Reads the watched transcript again every [ChatWatch.pollInterval]
  /// while the app is in front. The history merge keeps the reading
  /// position; a read already in flight is not doubled.
  void _startWatchPolling() {
    final watch = widget.watch;
    if (watch == null) return;
    _watchPoll?.cancel();
    _watchPoll = Timer.periodic(watch.pollInterval, (_) {
      if (!mounted) return;
      final lifecycle = WidgetsBinding.instance.lifecycleState;
      if (lifecycle != null && lifecycle != AppLifecycleState.resumed) return;
      unawaited(_conn.ensureSession(widget.sessionID));
      if (_loading || _loadingOlder) return;
      _scheduleRecentHistoryRefresh();
    });
    watch.changes?.addListener(_watchChanged);
  }

  void _stopWatchPolling() {
    _watchPoll?.cancel();
    _watchPoll = null;
    widget.watch?.changes?.removeListener(_watchChanged);
  }

  /// The worker's state moved (its session, from the team): the status
  /// line reads it again.
  void _watchChanged() {
    if (mounted) _setChatState(() {});
  }

  /// The one status line while watching.
  _ChatStatus _watchingStatus(ChatWatch watch) {
    final (message, tone) = watch.banner();
    return _ChatStatus(
      id: 'watching',
      key: const ValueKey('chat-watching-banner'),
      icon: AppIconography.agent,
      tone: tone,
      message: message,
    );
  }

  /// The worker's own page, the top bar's one action while watching.
  KitAction? _watchDetailsAction(ChatWatch watch) {
    final open = watch.onDetails;
    final label = watch.detailsLabel;
    if (open == null || label == null) return null;
    return KitAction(
      key: const ValueKey('chat-watching-details'),
      icon: AppIconography.info,
      label: label,
      onPressed: () => open(context),
    );
  }

  /// A child session the watched one delegated to: watched the same way.
  void _openWatchedChild(String id) {
    if (!RegExp(r'^[A-Za-z0-9_-]+$').hasMatch(id)) return;
    unawaited(
      pushKitPage<void>(
        context,
        (_) => ChatScreen(sessionID: id, watch: widget.watch),
      ),
    );
  }
}

/// A watched conversation with nothing in it yet: the worker has not said
/// anything, or its first turn is still being written.
class _WatchingEmpty extends StatelessWidget {
  const _WatchingEmpty({this.words});

  /// The agent's own state in words (title, body); null: the plain words.
  final (String, String)? words;

  @override
  Widget build(BuildContext context) {
    final l10n = _chatL10n(context);
    return KitStateView(
      key: const ValueKey('chat-watching-empty'),
      icon: AppIconography.agent,
      title: words?.$1 ?? l10n.chatWatchEmptyTitle,
      body: words?.$2 ?? l10n.chatWatchEmptyBody,
      size: KitStateSize.inline,
      liveRegion: false,
    );
  }
}

/// The watching page's floating layer: [body] under the composer that
/// writes to the worker, with the newest message's receipt above it.
Widget _watchLayer({required ChatWatch watch, required Widget body}) {
  final receipt = watch.receipt;
  return KitComposer.layer(
    body: body,
    above: receipt == null
        ? null
        : ListenableBuilder(
            listenable: watch.changes ?? const AlwaysStoppedAnimation(0),
            builder: (context, _) => receipt(context) ?? const SizedBox(),
          ),
    composer: _WatchComposer(watch: watch),
  );
}

/// The composer while watching: the person's words for the worker, sent
/// through [ChatWatch.onSend]. The words are kept as a draft per worker
/// and server until the team takes them (DATA-1); a refused message stays
/// in the field, with the refusal in its receipt.
class _WatchComposer extends StatefulWidget {
  const _WatchComposer({required this.watch});

  final ChatWatch watch;

  @override
  State<_WatchComposer> createState() => _WatchComposerState();
}

class _WatchComposerState extends State<_WatchComposer> {
  final _text = TextEditingController();
  final _focus = FocusNode();
  KitDraft? _draft;
  Timer? _save;
  bool _sending = false;

  @override
  void initState() {
    super.initState();
    final id = widget.watch.draftId;
    String? profileId;
    try {
      profileId = ProviderScope.containerOf(
        context,
        listen: false,
      ).read(connProvider).profile?.id;
    } catch (_) {
      profileId = null;
    }
    if (id != null && profileId != null && profileId.isNotEmpty) {
      _draft = KitDraft(
        target: 'team-message.$id',
        profileId: profileId,
        controller: _text,
      );
      unawaited(_draft!.restore());
    }
    _text.addListener(_changed);
  }

  @override
  void dispose() {
    _save?.cancel();
    _text.removeListener(_changed);
    // What was typed and not sent outlives the page.
    unawaited(_draft?.save());
    // The field may still read the controller while the page leaves.
    final text = _text;
    WidgetsBinding.instance.addPostFrameCallback((_) => text.dispose());
    _focus.dispose();
    super.dispose();
  }

  /// Counts edits to the words (not cursor moves), so a send can tell
  /// whether the person typed on while the host acknowledged it.
  int _revision = 0;
  String _lastText = '';

  void _changed() {
    if (!mounted) return;
    if (_text.text != _lastText) {
      _lastText = _text.text;
      _revision++;
    }
    setState(() {});
    final draft = _draft;
    if (draft == null) return;
    _save?.cancel();
    _save = Timer(const Duration(milliseconds: 500), () {
      unawaited(draft.save());
    });
  }

  Future<void> _send() async {
    final send = widget.watch.onSend;
    final text = _text.text.trim();
    if (send == null || text.isEmpty || _sending) return;
    // The field stays editable while the host answers: only the words
    // that were sent are cleared, and only if nothing was typed since
    // (audit2 A4). Newer words stay, and so does their saved draft.
    final sentRevision = _revision;
    setState(() => _sending = true);
    try {
      final taken = await send(text);
      if (!mounted || !taken) return;
      if (_revision != sentRevision) {
        unawaited(_draft?.save());
        return;
      }
      _save?.cancel();
      _text.clear();
      unawaited(_draft?.clear());
    } finally {
      if (mounted) setState(() => _sending = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final watch = widget.watch;
    final canSend = watch.onSend != null;
    return KitComposer(
      composerKey: const ValueKey('chat-watching-composer'),
      fieldKey: const ValueKey('chat-watching-message-field'),
      sendKey: const ValueKey('chat-watching-message-send'),
      controller: _text,
      focusNode: _focus,
      hint: watch.hintOf?.call() ?? watch.hint,
      fieldLabel: watch.hintOf?.call() ?? watch.hint,
      readOnlyReason: canSend ? null : watch.readOnlyReason,
      sending: _sending,
      canSendWhileBusy: true,
      onSend: () => unawaited(_send()),
    );
  }
}

mixin _ChatWatchingFields {
  /// Re-reads a watched transcript ([ChatWatch.pollInterval]).
  Timer? _watchPoll;
}
