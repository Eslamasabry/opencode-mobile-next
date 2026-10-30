import 'dart:async';

import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../state/connection.dart' show ConnectionController, connProvider;
import '../kit/kit.dart';
import '../widgets/conversation_status_scope.dart';
import '../screens/chat_screen.dart' show ChatScreen;
import '../widgets/session_menu.dart' show SessionMenuAction;
import 'chat_route.dart';

/// The chat page for [sessionID], landing on one request card
/// ([landOnRequestID]) or on the newest failed turn ([landOnFailure])
/// (P4.2a "Inbox rows land on the card"). The landing scope is built here,
/// once per opened page, so a rebuild never lands a second time.
///
/// Doors: the `/chat/<id>` route with [ChatRouteArguments.landOnRequestID]
/// / [ChatRouteArguments.landOnFailure], [chatLandingRoute] for a door that
/// pushes, `openMonitoredRequest` for a notification or another server's
/// row, and `TeamConversation.route(landOnGateId:)` for a team gate.
Widget chatLandingPage({
  required String sessionID,
  String? landOnRequestID,
  bool landOnFailure = false,
  bool discardIfUntouched = false,
  bool focusComposer = false,
  SessionMenuAction? menuAction,
}) {
  final chat = ChatScreen(
    sessionID: sessionID,
    discardIfUntouched: discardIfUntouched,
    focusComposer: focusComposer,
    landOnRequestID: landOnRequestID,
    landOnFailure: landOnFailure,
    menuAction: menuAction,
  );
  return ConversationBackendScope(
    sessionID: sessionID,
    child: landOnRequestID == null
        ? chat
        : KitArrivalScope(
            rowId: chatRequestArrivalId(landOnRequestID),
            child: chat,
          ),
  );
}

/// The conversation's own backend under [child]: a conversation with an
/// agent on this phone (Claude Code, Codex) talks to that agent's
/// connection, so its models, approvals and connection line are never the
/// OpenCode server's. Every door into a conversation goes through
/// [chatLandingPage], so each gets it.
///
/// An agent's conversation opened before its connection exists (a
/// notification, a shortcut or a link on a cold start) waits for that
/// connection instead of opening on OpenCode's: the page would otherwise
/// say "Ask OpenCode" and show OpenCode's problems for another agent's work.
class ConversationBackendScope extends ConsumerStatefulWidget {
  const ConversationBackendScope({
    super.key,
    required this.sessionID,
    required this.child,
  });

  final String sessionID;
  final Widget child;

  @override
  ConsumerState<ConversationBackendScope> createState() =>
      _ConversationBackendScopeState();
}

class _ConversationBackendScopeState
    extends ConsumerState<ConversationBackendScope> {
  ConnectionController? _backend;
  bool _waiting = false;

  @override
  void initState() {
    super.initState();
    final main = ref.read(connProvider);
    _backend = main.backendForConversation(widget.sessionID);
    if (_backend == null &&
        main.agentConversationNeedsBackend(widget.sessionID)) {
      _waiting = true;
      unawaited(_open(main));
    }
  }

  Future<void> _open(ConnectionController main) async {
    ConnectionController? backend;
    try {
      backend = await main.openBackendForConversation(widget.sessionID);
    } catch (_) {}
    if (!mounted) return;
    setState(() {
      _backend = backend;
      _waiting = false;
    });
  }

  @override
  Widget build(BuildContext context) {
    if (_waiting) {
      return const KitScreen(
        page: true,
        loading: true,
        body: KitSkeletonTranscript(),
      );
    }
    final backend = _backend;
    return backend == null
        ? widget.child
        : ProviderScope(
            overrides: [connProvider.overrideWithValue(backend)],
            child: ConversationStatusScope(
              connection: backend,
              child: widget.child,
            ),
          );
  }
}

/// [chatLandingPage] as a route, for doors that push rather than name it.
Route<void> chatLandingRoute({
  required String sessionID,
  String? landOnRequestID,
  bool landOnFailure = false,
  bool focusComposer = false,
}) {
  final page = chatLandingPage(
    sessionID: sessionID,
    landOnRequestID: landOnRequestID,
    landOnFailure: landOnFailure,
    focusComposer: focusComposer,
  );
  return KitPageRoute<void>(
    // Named so the app can tell this chat is already showing.
    settings: RouteSettings(name: '/chat/$sessionID'),
    builder: (_) => page,
  );
}
