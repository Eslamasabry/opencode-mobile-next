/// What a tool id means to a person, before any wording: the one place that
/// knows which internal tool ids have words of their own and how any other id
/// becomes words. Agents name their tools in their own vocabulary
/// (`oc-ui_show`, `mcp__oc-ui__show`, `task_notification`,
/// `render_mermaid_diagram`); none of those ids is ever copy. The UI words
/// them through `toolLabel` (lib/ui/widgets/tool_card_contract.dart), which
/// builds on these facts; the live notification's English sentence uses
/// [toolIdWords] directly.
library;

import 'agent_tools/agent_tool_adapter.dart';

/// The agent card tool (`oc-ui_show`, `mcp__oc-ui__show`), by any agent's
/// name for it.
bool isAgentCardTool(String id) =>
    AgentToolAdapters.cardShowNames.contains(id.trim());

/// Claude Code's notice that a background task it started has ended.
bool isBackgroundTaskNotice(String id) =>
    id.trim().toLowerCase() == 'task_notification';

/// How a background task ended, by its notice's `status`.
enum BackgroundTaskEnd { finished, failed, stopped }

BackgroundTaskEnd backgroundTaskEnd(Map<String, dynamic> input) =>
    switch (input['status']?.toString().trim().toLowerCase()) {
      'failed' || 'error' => BackgroundTaskEnd.failed,
      'killed' ||
      'stopped' ||
      'cancelled' ||
      'canceled' => BackgroundTaskEnd.stopped,
      _ => BackgroundTaskEnd.finished,
    };

/// A tool id as lower-case words: "render_mermaid_diagram" → "render mermaid
/// diagram", "ExitWorktree" → "exit worktree", "mcp__github__create_issue" →
/// "create issue". The caller decides the case its sentence needs. Empty
/// when the id has no letters or digits.
String toolIdWords(String id) {
  var name = id.trim();
  final mcp = RegExp(r'^mcp__(.+?)__(.+)$').firstMatch(name);
  if (mcp != null) name = mcp.group(2)!;
  return name
      .replaceAllMapped(
        RegExp(r'([a-z0-9])([A-Z])'),
        (match) => '${match[1]} ${match[2]}',
      )
      .replaceAll(RegExp(r'[_\-.\s]+'), ' ')
      .trim()
      .toLowerCase();
}
