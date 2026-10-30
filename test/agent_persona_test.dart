// The agent persona registry: every agent id the app knows maps to a
// persona, unknown ids map to `other`, and the words fit their places.
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/agent_catalog.dart';
import 'package:opencode_mobile/domain/agent_persona.dart';
import 'package:opencode_mobile/domain/chat_feed.dart';
import 'package:opencode_mobile/domain/mark_catalog.dart';
import 'package:opencode_mobile/l10n/app_localizations_ar.dart';
import 'package:opencode_mobile/l10n/app_localizations_en.dart';
import 'package:opencode_mobile/ui/agents/agent_persona_text.dart';

void main() {
  test('every catalog agent id and the OpenCode id map to a persona', () {
    for (final agent in AgentCatalog.builtIn.agents) {
      final persona = AgentPersona.of(agent.id);
      expect(persona, isNotNull, reason: agent.id);
      expect(AgentPersona.of(agent.iconKey).id, persona.id, reason: agent.id);
    }
    expect(AgentPersona.of(defaultChatAgentId).id, AgentPersonaId.openCode);
  });

  test('the named agents and their aliases', () {
    const expected = {
      'claude': AgentPersonaId.claudeCode,
      'Claude-Code': AgentPersonaId.claudeCode,
      'codex': AgentPersonaId.codex,
      'gemini': AgentPersonaId.gemini,
      'gemini-cli': AgentPersonaId.gemini,
      'kilo': AgentPersonaId.kilo,
      'kilocode': AgentPersonaId.kilo,
      'cline': AgentPersonaId.cline,
      'opencode': AgentPersonaId.openCode,
      'opencode2': AgentPersonaId.openCode,
    };
    expected.forEach((key, id) => expect(AgentPersona.of(key).id, id));
  });

  test('unknown ids are other, with a neutral mark', () {
    final persona = AgentPersona.of('totally-new-agent');
    expect(persona.id, AgentPersonaId.other);
    expect(persona.hasMark, isFalse);
    expect(AgentPersona.of('').id, AgentPersonaId.other);
  });

  test('agents with a bundled mark but no persona keep their mark', () {
    final pi = AgentPersona.of('pi');
    expect(pi.id, AgentPersonaId.other);
    expect(pi.markSlug, 'pi');
  });

  test('every persona has a bundled mark and words in both languages', () {
    expect(AgentPersona.all.map((p) => p.id).toSet().length, 6);
    for (final l in [AppLocalizationsEn(), AppLocalizationsAr()]) {
      for (final persona in AgentPersona.all) {
        expect(bundledMarkSlugs, contains(persona.markSlug));
        for (final words in [
          persona.name(l),
          persona.strength(l),
          persona.about(l),
          persona.needs(l),
        ]) {
          expect(
            words.trim(),
            isNotEmpty,
            reason: '${persona.id} ${l.localeName}',
          );
        }
        expect(
          persona.strength(l).length,
          lessThanOrEqualTo(28),
          reason: '${persona.id} ${l.localeName}',
        );
      }
    }
  });
}
