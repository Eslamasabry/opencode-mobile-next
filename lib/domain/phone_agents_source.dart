/// What the UI reads and calls to run other agents on this phone (Claude Code
/// and friends through the phone's Paseo host) inside the current profile.
///
/// `ConnectionController` implements [PhoneAgentsSource] and
/// [AgentChatFeedSource]; the UI never touches `lib/api/`, `lib/api2/`,
/// `lib/paseo/` or `lib/builtin/`. There is one phone agent host and one auth
/// owner per profile; no second profile and no second "This phone" server.
/// Every getter is a cheap projection of held state; the controller notifies
/// its listeners when any of it changes. Errors reach the UI as
/// `ProductException` plain sentences or as the typed failures already in
/// `phone_agent_host.dart` / `agent_sign_in.dart`, never raw text.
library;

import 'agent_sign_in.dart';
import 'chat_feed.dart';
import 'merged_chat_feed.dart';
import 'phone_agent_host.dart';
import 'phone_agents.dart';

/// Stable id of OpenCode in [ChatAgentChoice.agentId] (the chat feed's
/// [defaultChatAgentId]).
const openCodeChatAgentId = defaultChatAgentId;

/// One entry of the agent chip's sheet.
final class ChatAgentChoice {
  const ChatAgentChoice({
    required this.agentId,
    required this.name,
    required this.iconKey,
    required this.selected,
    this.row,
  });

  /// [openCodeChatAgentId] or a catalog id ('claude', ...).
  final String agentId;
  final String name;
  final String iconKey;
  final bool selected;

  /// Null for OpenCode. For phone agents it carries readiness, the fix action
  /// and the resume label/note (see [AgentRow]).
  final AgentRow? row;
}

/// A line to show under the agent chip or in the feed header.
enum PhoneAgentStatusLineKind { limitReached, signedOut, stopped }

final class PhoneAgentStatusLine {
  const PhoneAgentStatusLine({
    required this.agentId,
    required this.agentName,
    required this.kind,
    this.resetAt,
  });
  final String agentId, agentName;
  final PhoneAgentStatusLineKind kind;

  /// Host-supplied only; null means "try again later". Never guessed.
  final DateTime? resetAt;
}

/// What to say before reopening an old chat row.
final class AgentResumeNotice {
  const AgentResumeNotice({
    required this.canReopen,
    this.label,
    this.note,
    this.requiresAcknowledgement = false,
  });

  /// True when the row opens in place (OpenCode, or a verified native route).
  final bool canReopen;

  /// "Can't reopen old chats" when [canReopen] is false.
  final String? label;

  /// "Starts a new chat"; show it with a Start new chat action.
  final String? note;

  /// True when reopening means [PhoneAgentsSource.startNewChatReplacing] and
  /// the person must tap Start new chat first.
  final bool requiresAcknowledgement;
}

abstract interface class PhoneAgentsSource {
  /// True when the current profile is this phone's built-in server on a
  /// platform that can run the agent host. False hides every agent surface.
  bool get phoneAgentsAvailable;

  /// True after "clear all sign-ins" erased the phone agent homes: the
  /// controller closed its clients and the owners stay blocked until the app
  /// process restarts. Show a restart offer; never retry.
  bool get phoneAgentsNeedRestart;

  /// Setup inventory: every catalog candidate with `setupVisible`, including
  /// uninstalled ones (they carry an Install fix action). Empty when
  /// [phoneAgentsAvailable] is false or before the first
  /// [refreshAgentRows].
  List<AgentRow> get agentRows;

  /// The agent chip's choices: OpenCode first, then rows with `chatVisible`.
  List<ChatAgentChoice> get chatAgentChoices;

  /// The agent a new chat starts with. [openCodeChatAgentId] by default;
  /// persisted per profile under `oc.chatAgent.<profileId>`. Falls back to
  /// OpenCode when the stored agent is no longer selectable.
  String get selectedChatAgentId;

  /// Commits the chip. Throws a `ProductException` unless the row is
  /// `chatSelectable`. Changes no connection and no gateway: routing happens
  /// when a chat starts or opens.
  Future<void> selectChatAgent(String agentId);

  /// Re-reads installation, host, phone-check and sign-in truth for every
  /// catalog agent. Quiet on failure; rows keep their last facts.
  Future<void> refreshAgentRows();

  /// Install progress of the one owned setup job (idle when none). Only the
  /// agent payload size is known from the recipe; see the frontend contract.
  AgentSetupProgress get agentSetupProgress;

  /// Last phone-check result per agent in this process, or null.
  AgentPhoneCheckResult? agentPhoneCheck(String agentId);

  /// Starts (or joins) the durable component job. Returns after handover.
  Future<void> installAgent(String agentId);

  /// Cancels only the setup job this profile owns.
  Future<void> cancelAgentInstall();

  /// Install -> Version -> Connection -> Ready. Never throws for a failed
  /// step: the result says which step and why.
  Future<AgentPhoneCheckResult> runAgentPhoneCheck(String agentId);

  /// Starts the same loopback host again after "Stopped in the background",
  /// rechecks sign-in and refreshes rows and the feed.
  Future<void> resumeAgentHost();

  /// Sign-in state for [agentId]; null until inspected or started. A state
  /// with `inspected == false` means "Checking sign-in", not signed out.
  AgentSignInState? agentSignInState(String agentId);

  /// The authorization page to hand to `openExternalLink`, only while the
  /// state is urlReady or awaitingCode. Ephemeral; never store or log it.
  Uri? agentSignInUrl(String agentId);

  /// Starts the host's browser sign-in (or finds an existing signed-in
  /// subscription). Inspects first when not yet inspected.
  Future<void> startAgentSignIn(String agentId);

  /// Submits the one-time browser code once; the code is consumed.
  Future<void> submitAgentSignInCode(String agentId, AgentSignInCode code);

  /// Cancels the sign-in flow and awaits the native drain. Closing the sheet
  /// calls this. Throws [AgentSignInException] if the drain is unconfirmed.
  Future<void> cancelAgentSignIn(String agentId);

  /// Rows needing a status line: limit reached, signed out, stopped.
  List<PhoneAgentStatusLine> get agentStatusLines;

  /// Opens a merged feed row through the gateway that owns it, found by
  /// [ChatFeedItem.identity], never by session id alone, and returns the
  /// route. After it completes the connection is scoped to that row's agent
  /// and project, and `/chat/<sessionID>` can open. Throws a `ProductException`
  /// ("Refresh the conversation before opening it.") when the row is gone.
  Future<ChatFeedRoute> openChatFeedItem(ChatFeedItem item);

  /// What to show before reopening [item].
  AgentResumeNotice agentResumeNotice(ChatFeedItem item);

  /// After the person taps Start new chat on the notice: creates a fresh draft
  /// with the same agent and project and returns its session id. With
  /// `newChatAcknowledged == false` it refuses without creating anything.
  /// The old row stays untouched; the first prompt is whatever the person
  /// submits next.
  Future<String> startNewChatReplacing(
    ChatFeedItem old, {
    required bool newChatAcknowledged,
  });

  /// Call before the app's "Clear all saved sign-ins": closes auth, cancels
  /// owned setup, stops and disposes the host and feed sources. Afterwards
  /// [phoneAgentsNeedRestart] is true.
  Future<void> closePhoneAgentsForSignInReset();
}
