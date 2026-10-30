/// Who an agent is, defined once: its identity, maker and mark. Names and
/// words come from `lib/ui/agents/agent_persona_text.dart` (the ARB files),
/// so the copy is easy to change. Pure lookup, no Flutter, no I/O.
///
/// [AgentPersona.of] takes any id, alias or icon key the app sees (phone
/// agent catalog ids, Paseo provider ids, OpenCode 1 and 2, ACP ids) and
/// returns [AgentPersonaId.other] with the agent's own mark (or a neutral
/// one) for anything it does not know. docs/design/agent-personas.md mirrors
/// this file.
library;

import 'mark_catalog.dart';

/// The agents the app speaks of by name.
enum AgentPersonaId { claudeCode, codex, gemini, kilo, cline, openCode, other }

/// Who makes the agent, for the "by ..." line.
enum AgentMaker { anthropic, openai, google, openSource, unknown }

class AgentPersona {
  const AgentPersona({
    required this.id,
    required this.markSlug,
    required this.maker,
  });

  final AgentPersonaId id;

  /// The bundled single-colour mark (`assets/marks/<slug>.svg`); empty for
  /// the neutral mark (the caller draws its generic glyph).
  final String markSlug;
  final AgentMaker maker;

  bool get hasMark => markSlug.isNotEmpty;

  static const claudeCode = AgentPersona(
    id: AgentPersonaId.claudeCode,
    markSlug: 'claude',
    maker: AgentMaker.anthropic,
  );
  static const codex = AgentPersona(
    id: AgentPersonaId.codex,
    markSlug: 'codex',
    maker: AgentMaker.openai,
  );
  static const gemini = AgentPersona(
    id: AgentPersonaId.gemini,
    markSlug: 'gemini',
    maker: AgentMaker.google,
  );
  static const kilo = AgentPersona(
    id: AgentPersonaId.kilo,
    markSlug: 'kilocode',
    maker: AgentMaker.openSource,
  );
  static const cline = AgentPersona(
    id: AgentPersonaId.cline,
    markSlug: 'cline',
    maker: AgentMaker.openSource,
  );
  static const openCode = AgentPersona(
    id: AgentPersonaId.openCode,
    markSlug: 'opencode',
    maker: AgentMaker.openSource,
  );

  /// Anything else: no words of its own, a neutral mark.
  static const other = AgentPersona(
    id: AgentPersonaId.other,
    markSlug: '',
    maker: AgentMaker.unknown,
  );

  /// The six named personas, in the order a picker shows them.
  static const List<AgentPersona> all = [
    claudeCode,
    codex,
    gemini,
    kilo,
    cline,
    openCode,
  ];

  static const Map<String, AgentPersona> _aliases = {
    'claude': claudeCode,
    'claude code': claudeCode,
    'claude-code': claudeCode,
    'claude_code': claudeCode,
    'claudecode': claudeCode,
    'codex': codex,
    'openai-codex': codex,
    'gemini': gemini,
    'gemini cli': gemini,
    'gemini-cli': gemini,
    'kilo': kilo,
    'kilocode': kilo,
    'kilo-code': kilo,
    'cline': cline,
    'opencode': openCode,
    'opencode2': openCode,
    'opencode1': openCode,
    'open-code': openCode,
  };

  /// The persona for [agentIdOrKey]; [other] (with the agent's own mark when
  /// the app bundles one: Qwen, Goose, Pi, Copilot) for anything unknown.
  static AgentPersona of(String agentIdOrKey) {
    final key = agentIdOrKey.trim().toLowerCase();
    final named = _aliases[key];
    if (named != null) return named;
    final mark = agentMarkSlug(key);
    return mark == null
        ? other
        : AgentPersona(
            id: AgentPersonaId.other,
            markSlug: mark,
            maker: AgentMaker.unknown,
          );
  }
}
