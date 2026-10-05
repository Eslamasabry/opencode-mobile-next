import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../state/connection.dart' show connProvider;
import '../kit/kit.dart';
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
/// agent on this phone (Claude Code) talks to that agent's connection, so
/// its models, approvals and banner are never the OpenCode server's. Every
/// door into a conversation goes through [chatLandingPage], so each gets it.
class ConversationBackendScope extends ConsumerWidget {
  const ConversationBackendScope({
    super.key,
    required this.sessionID,
    required this.child,
  });

  final String sessionID;
  final Widget child;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final backend = ref.read(connProvider).backendForConversation(sessionID);
    return backend == null
        ? child
        : ProviderScope(
            overrides: [connProvider.overrideWithValue(backend)],
            child: child,
          );
  }
}

/// [chatLandingPage] as a route, for doors that push rather than name it.
Route<void> chatLandingRoute({
  required String sessionID,
  String? landOnRequestID,
  bool landOnFailure = false,
}) {
  final page = chatLandingPage(
    sessionID: sessionID,
    landOnRequestID: landOnRequestID,
    landOnFailure: landOnFailure,
  );
  return KitPageRoute<void>(builder: (_) => page);
}
