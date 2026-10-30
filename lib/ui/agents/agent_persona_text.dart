import '../../domain/agent_persona.dart';
import '../../l10n/app_localizations.dart';

/// The words of an agent persona, from the ARB files (the copy is edited
/// there). [AgentPersonaId.other] has no words of its own: callers show the
/// agent's own name and no line.
extension AgentPersonaText on AgentPersona {
  /// The agent's name as its maker writes it. Brand names are not
  /// translated, so they live here and not in the ARB files;
  /// [AgentPersonaId.other] has none (callers show the agent's own name).
  String name(AppLocalizations l) => switch (id) {
    AgentPersonaId.claudeCode => 'Claude Code',
    AgentPersonaId.codex => 'Codex',
    AgentPersonaId.gemini => 'Gemini',
    AgentPersonaId.kilo => 'Kilo',
    AgentPersonaId.cline => 'Cline',
    AgentPersonaId.openCode => 'OpenCode',
    AgentPersonaId.other => l.agentPersonaOtherName,
  };

  /// "by Anthropic" / "open source": who makes it.
  String makerLine(AppLocalizations l) => switch (maker) {
    AgentMaker.anthropic => l.agentMakerAnthropic,
    AgentMaker.openai => l.agentMakerOpenAI,
    AgentMaker.google => l.agentMakerGoogle,
    AgentMaker.openSource => l.agentMakerOpenSource,
    AgentMaker.unknown => '',
  };

  /// The card's one line, at most 28 characters; empty for [AgentPersonaId.other].
  String strength(AppLocalizations l) => switch (id) {
    AgentPersonaId.claudeCode => l.agentPersonaClaudeCodeStrength,
    AgentPersonaId.codex => l.agentPersonaCodexStrength,
    AgentPersonaId.gemini => l.agentPersonaGeminiStrength,
    AgentPersonaId.kilo => l.agentPersonaKiloStrength,
    AgentPersonaId.cline => l.agentPersonaClineStrength,
    AgentPersonaId.openCode => l.agentPersonaOpenCodeStrength,
    AgentPersonaId.other => '',
  };

  /// One sentence for sheets and settings.
  String about(AppLocalizations l) => switch (id) {
    AgentPersonaId.claudeCode => l.agentPersonaClaudeCodeAbout,
    AgentPersonaId.codex => l.agentPersonaCodexAbout,
    AgentPersonaId.gemini => l.agentPersonaGeminiAbout,
    AgentPersonaId.kilo => l.agentPersonaKiloAbout,
    AgentPersonaId.cline => l.agentPersonaClineAbout,
    AgentPersonaId.openCode => l.agentPersonaOpenCodeAbout,
    AgentPersonaId.other => '',
  };

  /// What a person needs to use it.
  String needs(AppLocalizations l) => switch (id) {
    AgentPersonaId.claudeCode => l.agentPersonaClaudeCodeNeeds,
    AgentPersonaId.codex => l.agentPersonaCodexNeeds,
    AgentPersonaId.gemini => l.agentPersonaGeminiNeeds,
    AgentPersonaId.kilo => l.agentPersonaKiloNeeds,
    AgentPersonaId.cline => l.agentPersonaClineNeeds,
    AgentPersonaId.openCode => l.agentPersonaOpenCodeNeeds,
    AgentPersonaId.other => '',
  };
}
