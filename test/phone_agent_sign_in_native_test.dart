import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/agent_catalog.dart';

/// Issue #95: Codex, Kilo and Cline never worked on the phone. The native
/// side refused Kilo's and Cline's sign-in terminals (they were missing from
/// its list of sign-in programs), and Codex refused to start at all, sign-in
/// and chat included, because the folder its `CODEX_HOME` names was never
/// made.
void main() {
  const native =
      'android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile';
  final linux = File('$native/BuiltinLinux.kt').readAsStringSync();

  String body(String signature) {
    final start = linux.indexOf(signature);
    expect(start, isNonNegative, reason: signature);
    return linux.substring(start, linux.indexOf('\n    }\n', start));
  }

  test('every phone agent can open its sign-in terminal', () {
    final declared = RegExp(
      r'val SIGN_IN_PROGRAMS = setOf\(([^)]*)\)',
    ).firstMatch(linux);
    expect(declared, isNotNull);
    final programs = RegExp(
      r'"([^"]+)"',
    ).allMatches(declared!.group(1)!).map((match) => match.group(1)).toSet();
    final phoneAgents = AgentCatalog.builtIn.agents.where(
      (agent) =>
          agent.recipe != null &&
          (agent.route == AgentRoute.paseoNative ||
              agent.route == AgentRoute.acpPaseo),
    );
    expect(
      phoneAgents.map((agent) => agent.id),
      containsAll(['kilo', 'cline']),
    );
    for (final agent in phoneAgents) {
      expect(
        programs,
        contains(agent.recipe!.executable),
        reason: '${agent.name} sign-in would be refused',
      );
    }
  });

  test('Codex\'s folder exists before any agent process starts', () {
    final prepare = body('private fun prepareAgentProfileHome(');
    expect(prepare, contains(r'.oc-profiles/$profileId/codex"'));
    for (final launch in [
      'fun agentSignInCommand(',
      'private fun startPrivateAgentProcess(',
    ]) {
      final code = body(launch);
      expect(code, contains('prepareAgentProfileHome(profileId)'));
      expect(code, contains(r'"CODEX_HOME=$guestHome/codex"'));
    }
  });
}
