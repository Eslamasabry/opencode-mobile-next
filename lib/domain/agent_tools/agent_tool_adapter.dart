/// Phone tools and the per-agent adapters that carry them.
///
/// A phone tool (agent cards today, the agent browser next) is defined once:
/// an app-owned MCP server and its tools. Each agent differs only in a few
/// facts: how it names an MCP tool in its transcript, where it registers MCP
/// servers, which Linux user runs it, how its own question tool is answered,
/// and whether the app has device evidence that the tools work with it.
/// Those facts live in one [AgentToolAdapter] per agent, in
/// [AgentToolAdapters.all]; every caller asks the registry instead of naming
/// an agent. Adding an agent means adding one adapter (and, only for a new
/// [McpConfigFormat], one installer writer). See
/// docs/design/agent-tool-adapters-2026-10-07.md.
library;

/// An app-owned MCP server and the tools it offers agents.
final class PhoneTool {
  const PhoneTool({required this.server, required this.tools});

  /// The MCP server name every agent registers ("oc-ui").
  final String server;

  /// Its tool names, as the server lists them ("show").
  final List<String> tools;

  /// Agent cards: native choice, form, confirm, report and photo cards.
  static const cards = PhoneTool(server: 'oc-ui', tools: ['show']);
}

/// How an agent shows the tool `tool` of MCP server `server` in its
/// transcript and permission requests. Taken from captured runtime fixtures,
/// never guessed.
enum McpToolNaming {
  /// `mcp__oc-ui__show` (Claude Code).
  mcpPrefixed,

  /// `oc-ui_show` (OpenCode 1 and 2).
  serverUnderscore,
}

/// Where and how an agent keeps its MCP server list. The installer has one
/// writer per format; a new format needs a new writer and its tests.
enum McpConfigFormat {
  /// `claude mcp add --scope user` in the profile's CLAUDE_CONFIG_DIR.
  claudeCli,

  /// OpenCode 1: `mcp.<name>` in /root/.config/opencode/opencode.json.
  openCodeV1,

  /// OpenCode 2: `mcp.servers.<name>` in its isolated config directory.
  openCodeV2,
}

/// Which Linux user inside the in-app Ubuntu runs the agent, and so which
/// bridge the installer uses and which files it may touch.
enum AgentRunUser {
  /// uid 1000 with the profile's own HOME (Paseo agents).
  agentUser,

  /// The shared root OpenCode server.
  root,
}

/// How an agent's own multi-question tool expects its answers keyed.
enum NativeAnswerKeys {
  /// By the question text (Claude Code's AskUserQuestion).
  questionText,

  /// By the prompt header (Pi and Oh My Pi's ask_user).
  header,
}

/// Everything the app needs to know to give one agent the phone tools.
final class AgentToolAdapter {
  const AgentToolAdapter._({
    required this.id,
    required this.displayName,
    this.paseoProvider,
    this.naming,
    this.config,
    this.runsAs = AgentRunUser.agentUser,
    this.cardsQualified = false,
    this.preAllowsCards = false,
    this.answerKeys,
    this.questionToolName,
    this.planToolName,
  });

  /// Stable id. It names the agent's helper directory on disk
  /// (`.oc-genui/<id>`), so it never changes once shipped.
  final String id;

  /// The agent's product name, as cards and settings say it.
  final String displayName;

  /// The Paseo provider id of this agent's chats; null for OpenCode.
  final String? paseoProvider;

  /// Null when the agent cannot call MCP tools at all.
  final McpToolNaming? naming;

  /// Null when the app cannot register MCP servers for this agent yet.
  final McpConfigFormat? config;

  final AgentRunUser runsAs;

  /// Recorded runtime evidence qualifies the card transport under the
  /// accepted trust model. Live verification still gates readiness; full
  /// journey evidence is recorded separately (docs/design/BA6-contract.md).
  /// Registration alone never sets this.
  final bool cardsQualified;

  /// The display-only card tool is allowed in the agent's own permission
  /// settings, so "Asks first" does not ask about showing a card.
  final bool preAllowsCards;

  /// Null when the agent has no native question tool the app answers.
  final NativeAnswerKeys? answerKeys;

  /// The agent's own question tool, when its requests name one
  /// ("AskUserQuestion"); null accepts the agent's question requests as sent.
  final String? questionToolName;

  /// The agent's plan-approval tool ("ExitPlanMode"); null when the agent
  /// has none the app answers.
  final String? planToolName;

  /// True when the app answers this agent's own questions with its sheet.
  bool get answersNativeQuestions => answerKeys != null;

  /// Back-compat with the former `GenUiAgent` enum.
  String get name => id;

  /// True when the app can register [PhoneTool]s for this agent.
  bool get supportsTools => naming != null && config != null;

  /// The name this agent gives [tool] of [phoneTool]; null without MCP.
  String? toolName(PhoneTool phoneTool, String tool) => switch (naming) {
    McpToolNaming.mcpPrefixed => 'mcp__${phoneTool.server}__$tool',
    McpToolNaming.serverUnderscore => '${phoneTool.server}_$tool',
    null => null,
  };

  /// The card `show` tool's name in this agent's transcript.
  String? get cardShowName => toolName(PhoneTool.cards, 'show');

  static const claude = AgentToolAdapter._(
    id: 'claude',
    displayName: 'Claude Code',
    paseoProvider: 'claude',
    naming: McpToolNaming.mcpPrefixed,
    config: McpConfigFormat.claudeCli,
    cardsQualified: true,
    preAllowsCards: true,
    answerKeys: NativeAnswerKeys.questionText,
    questionToolName: 'AskUserQuestion',
    planToolName: 'ExitPlanMode',
  );

  /// OC1 1.18.32 calls `oc-ui_show` (captured device fixture, 2026-10-07).
  /// The owner accepts one in-app Ubuntu trust zone; live readiness still
  /// requires the owned helper and a connected MCP server. See BA6 contract.
  static const openCode1 = AgentToolAdapter._(
    id: 'openCode1',
    displayName: 'OpenCode',
    naming: McpToolNaming.serverUnderscore,
    config: McpConfigFormat.openCodeV1,
    runsAs: AgentRunUser.root,
    cardsQualified: true,
  );

  static const openCode2 = AgentToolAdapter._(
    id: 'openCode2',
    displayName: 'OpenCode',
    naming: McpToolNaming.serverUnderscore,
    config: McpConfigFormat.openCodeV2,
    runsAs: AgentRunUser.root,
  );

  /// Pi: no MCP; its ask_user questions are answered by header.
  static const pi = AgentToolAdapter._(
    id: 'pi',
    displayName: 'Pi',
    paseoProvider: 'pi',
    answerKeys: NativeAnswerKeys.header,
  );

  static const ohMyPi = AgentToolAdapter._(
    id: 'omp',
    displayName: 'Oh My Pi',
    paseoProvider: 'omp',
    answerKeys: NativeAnswerKeys.header,
  );

  /// Agents the phone tools can be registered for (former enum values).
  static const List<AgentToolAdapter> values = [claude, openCode1, openCode2];

  @override
  String toString() => 'AgentToolAdapter($id)';
}

/// The registry every caller asks; no other file names an agent.
abstract final class AgentToolAdapters {
  static const List<AgentToolAdapter> all = [
    AgentToolAdapter.claude,
    AgentToolAdapter.openCode1,
    AgentToolAdapter.openCode2,
    AgentToolAdapter.pi,
    AgentToolAdapter.ohMyPi,
  ];

  /// Agents the app registers phone tools for.
  static Iterable<AgentToolAdapter> get withTools =>
      all.where((agent) => agent.supportsTools);

  static AgentToolAdapter? byId(String id) {
    for (final agent in all) {
      if (agent.id == id) return agent;
    }
    return null;
  }

  /// The adapter of a Paseo chat's provider; null for an unknown provider or
  /// a value that is not a string (daemon data is untrusted).
  static AgentToolAdapter? forPaseoProvider(Object? provider) {
    if (provider is! String) return null;
    for (final agent in all) {
      if (agent.paseoProvider == provider) {
        return agent;
      }
    }
    return null;
  }

  /// The OpenCode adapter for a server generation.
  static AgentToolAdapter forOpenCode({required bool v2}) =>
      v2 ? AgentToolAdapter.openCode2 : AgentToolAdapter.openCode1;

  /// Every transcript name of the card `show` tool, across agents.
  static final Set<String> cardShowNames = {
    for (final agent in all) ?agent.cardShowName,
  };
}
