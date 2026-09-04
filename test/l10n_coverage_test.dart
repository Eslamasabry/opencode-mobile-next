// Ratchet against new hardcoded UI strings while localization is in progress.
//
// docs/localization-todo.md: the app is English-only and ~700 literals in
// lib/ui/**, lib/voice/** and lib/main.dart bypass AppLocalizations. This test
// counts the ones a translator would need, per file, and fails when a file
// grows past its recorded baseline. Shrink the baseline as strings move into
// lib/l10n/app_en.arb; never raise it to make a PR pass.
//
// To refresh the baseline after moving strings out:
//   L10N_BASELINE_PRINT=1 flutter test test/l10n_coverage_test.dart
// and paste the printed map over `_baseline`.
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

const _roots = ['lib/ui', 'lib/voice'];
const _files = ['lib/main.dart'];

// Text('...') / Text("...") with an optional raw prefix; whitespace and line
// breaks between the paren and the literal are allowed.
final _textLiteral = RegExp(r'''\bText\(\s*r?(['"])(.*?)\1''', dotAll: true);

// Named string parameters that always end up on screen.
final _namedLiteral = RegExp(
  r'''\b(?:hintText|labelText|helperText|tooltip|semanticsLabel|semanticLabel|errorText):\s*(?:const\s+)?r?(['"])(.*?)\1''',
  dotAll: true,
);

final _hasLetter = RegExp('[A-Za-z]');
// Identifiers, paths, model ids, shortcuts: one token containing a separator.
final _identifierLike = RegExp(r'^[^\s]*[/._+:][^\s]*$');
// Dart interpolation-only literals like '$count' or '${a.b}'.
final _interpolationOnly = RegExp(r'^\$\{?[A-Za-z_][A-Za-z0-9_.]*\}?$');

bool _translatable(String literal) {
  if (!_hasLetter.hasMatch(literal)) return false;
  if (_identifierLike.hasMatch(literal)) return false;
  if (_interpolationOnly.hasMatch(literal)) return false;
  return true;
}

String _stripComments(String source) => source
    .split('\n')
    .where((line) => !line.trimLeft().startsWith('//'))
    .join('\n');

int countHardcodedStrings(String source) {
  final code = _stripComments(source);
  var count = 0;
  for (final m in _textLiteral.allMatches(code)) {
    if (_translatable(m.group(2)!)) count++;
  }
  for (final m in _namedLiteral.allMatches(code)) {
    if (_translatable(m.group(2)!)) count++;
  }
  return count;
}

Map<String, int> scan() {
  final results = <String, int>{};
  final paths = <String>[
    for (final root in _roots)
      ...Directory(root)
          .listSync(recursive: true)
          .whereType<File>()
          .where((f) => f.path.endsWith('.dart'))
          .map((f) => f.path),
    ..._files,
  ]..sort();
  for (final path in paths) {
    final n = countHardcodedStrings(File(path).readAsStringSync());
    if (n > 0) results[path.replaceAll('\\', '/')] = n;
  }
  return results;
}

// Recorded 2026-09-03. Only decrease these numbers.
const _baseline = <String, int>{
  'lib/main.dart': 4,
  'lib/ui/desktop/file_drop.dart': 1,
  'lib/ui/desktop/shortcuts.dart': 4,
  'lib/ui/screens/about_screen.dart': 5,
  'lib/ui/screens/activity_screen.dart': 11,
  'lib/ui/screens/app_diagnostics_screen.dart': 12,
  'lib/ui/screens/capabilities_screen.dart': 1,
  'lib/ui/screens/chat/attention_card.dart': 7,
  'lib/ui/screens/chat/command_launcher.dart': 9,
  'lib/ui/screens/chat/composer.dart': 16,
  'lib/ui/screens/chat/form_flow.dart': 1,
  'lib/ui/screens/chat/message_view.dart': 30,
  'lib/ui/screens/chat/permission_sheet.dart': 17,
  'lib/ui/screens/chat/prompt_editor.dart': 4,
  'lib/ui/screens/chat/sessions_tab.dart': 9,
  'lib/ui/screens/chat/timeline_sheet.dart': 4,
  'lib/ui/screens/chat_screen.dart': 29,
  'lib/ui/screens/files_screen.dart': 23,
  'lib/ui/screens/global_sessions_screen.dart': 10,
  'lib/ui/screens/guide_screen.dart': 15,
  'lib/ui/screens/home_screen.dart': 4,
  'lib/ui/screens/host_management_screen.dart': 6,
  'lib/ui/screens/library/catalog_screen.dart': 4,
  'lib/ui/screens/library/commands_screen.dart': 8,
  'lib/ui/screens/library/integration_tiles.dart': 23,
  'lib/ui/screens/library/integrations_screen.dart': 16,
  'lib/ui/screens/library/references_screen.dart': 2,
  'lib/ui/screens/library/skills_screen.dart': 1,
  'lib/ui/screens/manage_project_screen.dart': 7,
  'lib/ui/screens/managed_workspaces_screen.dart': 25,
  'lib/ui/screens/mcp_setup_screen.dart': 26,
  'lib/ui/screens/pairing_scanner_screen.dart': 3,
  'lib/ui/screens/project_health_screen.dart': 23,
  'lib/ui/screens/projects_screen.dart': 13,
  'lib/ui/screens/review_workspace.dart': 41,
  'lib/ui/screens/saved_permissions_screen.dart': 13,
  'lib/ui/screens/servers_screen.dart': 35,
  'lib/ui/screens/session_context_screen.dart': 8,
  'lib/ui/screens/session_destination_sheet.dart': 17,
  'lib/ui/screens/session_relations_screen.dart': 3,
  'lib/ui/screens/settings/background_settings_screen.dart': 6,
  'lib/ui/screens/settings/coding_settings_screen.dart': 10,
  'lib/ui/screens/settings/personal_settings_screens.dart': 21,
  'lib/ui/screens/settings/server_settings_screen.dart': 14,
  'lib/ui/screens/settings_screen.dart': 4,
  'lib/ui/screens/terminal_screen.dart': 21,
  'lib/ui/screens/termux_setup_screen.dart': 25,
  'lib/ui/screens/tools_screen.dart': 8,
  'lib/ui/screens/workspace_screen.dart': 24,
  'lib/ui/screens/worktrees_screen.dart': 23,
  'lib/ui/widgets/agent_blocks.dart': 4,
  'lib/ui/widgets/appearance_picker.dart': 2,
  'lib/ui/widgets/connection_status_banner.dart': 3,
  'lib/ui/widgets/diff_view.dart': 2,
  'lib/ui/widgets/external_link.dart': 6,
  'lib/ui/widgets/file_preview.dart': 10,
  'lib/ui/widgets/form_renderer.dart': 7,
  'lib/ui/widgets/info_label.dart': 1,
  'lib/ui/widgets/markdown.dart': 2,
  'lib/ui/widgets/pickers.dart': 12,
  'lib/ui/widgets/product_states.dart': 3,
  'lib/ui/widgets/question_options.dart': 2,
  'lib/ui/widgets/saved_server_connection_card.dart': 7,
  'lib/ui/widgets/tool_card.dart': 9,
  'lib/ui/widgets/transcript_display_toggles.dart': 2,
  'lib/voice/notices.dart': 1,
  'lib/voice/voice_ui.dart': 29,
};

void main() {
  test('hardcoded string detector', () {
    expect(countHardcodedStrings("Text('Send')"), 1);
    expect(countHardcodedStrings('Text("Send now")'), 1);
    expect(countHardcodedStrings("Text(\n  'Send',\n)"), 1);
    expect(countHardcodedStrings("hintText: 'Ask anything'"), 1);
    expect(countHardcodedStrings("tooltip: const 'Attach'"), 1);
    expect(countHardcodedStrings("Text('·')"), 0, reason: 'no letters');
    expect(countHardcodedStrings("Text('Ctrl+Enter')"), 0, reason: 'shortcut');
    expect(countHardcodedStrings("Text('anthropic/claude')"), 0, reason: 'id');
    expect(countHardcodedStrings(r"Text('$count')"), 0, reason: 'interp');
    expect(countHardcodedStrings(r"Text('$count files')"), 1);
    expect(countHardcodedStrings("// Text('comment')"), 0);
    expect(countHardcodedStrings('Text(l10n.send)'), 0);
  });

  test('no file gained hardcoded UI strings', () {
    final current = scan();

    if (Platform.environment['L10N_BASELINE_PRINT'] == '1') {
      final buffer = StringBuffer();
      for (final entry in current.entries) {
        buffer.writeln("  '${entry.key}': ${entry.value},");
      }
      // ignore: avoid_print
      print(buffer);
    }

    final regressions = <String>[];
    final improvements = <String>[];
    for (final entry in current.entries) {
      final allowed = _baseline[entry.key] ?? 0;
      if (entry.value > allowed) {
        regressions.add(
          '${entry.key}: ${entry.value} hardcoded strings, baseline $allowed',
        );
      } else if (entry.value < allowed) {
        improvements.add('${entry.key}: ${entry.value} (baseline $allowed)');
      }
    }
    for (final path in _baseline.keys) {
      if (!current.containsKey(path)) {
        improvements.add('$path: 0 (baseline ${_baseline[path]})');
      }
    }

    if (improvements.isNotEmpty) {
      // ignore: avoid_print
      print(
        'l10n: these files improved; lower their baseline in '
        'test/l10n_coverage_test.dart:\n  ${improvements.join('\n  ')}',
      );
    }

    expect(
      regressions,
      isEmpty,
      reason:
          'New user-visible literals must go through AppLocalizations '
          '(lib/l10n/app_en.arb). See docs/localization-todo.md.\n'
          '${regressions.join('\n')}',
    );
  });
}
