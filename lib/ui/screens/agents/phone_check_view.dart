import 'package:flutter/material.dart';

import '../../../domain/phone_agent_host.dart';
import '../../../l10n/app_localizations.dart';
import '../../kit/kit.dart';
import 'agents_text.dart';

/// The phone check as four steps, each passed, failed or waiting, with a
/// plain sentence under them. [result] null means it is running now: the
/// first step shows as working.
class AgentPhoneCheckView extends StatelessWidget {
  const AgentPhoneCheckView({
    super.key,
    required this.agent,
    required this.result,
  });

  /// The agent's name.
  final String agent;
  final AgentPhoneCheckResult? result;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final tokens = KitTokens.of(context);
    final result = this.result;
    final passed = result == null
        ? const <AgentPhoneCheckStep>{}
        : result.completed.toSet();
    final failedAt = result == null || result.passed
        ? null
        : AgentPhoneCheckStep.values
              .where((step) => !passed.contains(step))
              .firstOrNull;
    final workingAt = result == null ? AgentPhoneCheckStep.values.first : null;
    final words = result == null
        ? l10n.agentsCheckRunning(KitBidi.auto(agent))
        : result.passed
        ? l10n.agentsCheckPassed(KitBidi.auto(agent))
        : '${l10n.agentsCheckFailed(KitBidi.auto(agent))} '
              '${agentHostFailureText(l10n, result.failure)}';
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        KitRowGroup(
          margin: EdgeInsets.zero,
          leadingIcons: false,
          children: [
            for (final step in AgentPhoneCheckStep.values)
              KitRow(
                key: ValueKey('agents-check-${step.name}'),
                title: agentCheckStepName(l10n, step),
                leading: KitTaskMark(
                  state: passed.contains(step)
                      ? KitTaskState.done
                      : step == failedAt
                      ? KitTaskState.failed
                      : step == workingAt
                      ? KitTaskState.working
                      : KitTaskState.waiting,
                ),
              ),
          ],
        ),
        SizedBox(height: tokens.space3),
        KitText(
          words,
          key: const ValueKey('agents-check-words'),
          role: KitTextRole.secondary,
          tone: KitTextTone.secondary,
        ),
      ],
    );
  }
}
