// The rules of docs/design/design-standard.md that code can check (§8).
//
// A migrated screen draws its buttons, states, progress and status lines
// with the kit in lib/ui/kit/, never with raw Material parts: no
// LinearProgressIndicator or CircularProgressIndicator, no Card(, no raw
// FilledButton. The list of migrated files only grows. An exception needs
// an entry in [_allowed] with its reason.
//
// Each migrated screen also has golden renders at 412x915, dark and light,
// in test/goldens/ (made by the *_golden_test.dart files there).
//
// A file too mixed to list whole (the chat's transcript file, where only the
// error card is a state) lists its migrated classes in [_migratedClasses]:
// the scan then covers each class's source. A file that gave up a raw part
// for a kit one lists it in [_retired] so it cannot come back.
//
// G3x (docs/ux-system/revamp/STANDARDS.md §18.2, TEST-10): [_forbidden] also
// holds every G1, G2, G7, G17 and G21 pattern (test/support/kit_patterns.dart),
// each counted where its scope says it applies. A screen migrated on or after
// 2026-09-26 holds all of them at zero and also has a `<golden>_ar_dark.png`.
// The screens migrated before that date are listed in [_grandfathered] and
// carry their counts in test/design_standard_baseline.json, which only
// shrinks: a count may fall, never rise, and no label outside
// [_grandfathered] may appear in it. An entry leaves `_migrated` only when its
// file is deleted (TEST-10). When counts fall the test prints the smaller
// baseline to commit;
//   DESIGN_STANDARD_WRITE=1 flutter test test/design_standard_test.dart
// writes it (lowering counts and dropping entries whose file is gone, never
// adding one). Write mode refuses to run when the baseline file is missing:
// restore it with `git checkout -- test/design_standard_baseline.json`. Then
// run it again without the variable and commit the file.
import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

import 'support/kit_patterns.dart';

/// Screen files built on the kit, with the golden renders that show them.
/// Only grows (§9 migration order: connection states, the Work tab, phone
/// setup, the AI Team, the chat's states).
const _migrated = <String, List<String>>{
  // §9 step 1: connecting, starting, not answering, stopped, failed.
  'lib/ui/widgets/saved_server_connection_card.dart': [
    'connection_connecting',
    'connection_not_answering',
    'connection_stopped',
    'connection_starting',
    'connection_failed',
  ],
  // §9 step 2: the Work tab and its own parts.
  'lib/ui/screens/workspace_screen.dart': [
    'work_restoring',
    'work_loading',
    'work_empty',
    'work_loaded',
    'work_not_answering',
    'work_runaway',
    'work_chooser',
    // Step 2 leftovers: conversation rows on KitRow, the parts below.
    'work_team',
    'work_nudge',
    'work_other_servers',
  ],
  // The Work tab's part files (split from workspace_screen.dart).
  'lib/ui/screens/workspace/workspace_actions.dart': ['work_loaded'],
  'lib/ui/screens/workspace/workspace_build.dart': ['work_loaded'],
  'lib/ui/screens/workspace/workspace_load.dart': ['work_loaded'],
  'lib/ui/screens/workspace/workspace_sheets.dart': ['work_loaded'],
  'lib/ui/screens/workspace/workspace_widgets.dart': ['work_loaded'],
  'lib/ui/widgets/other_projects_panel.dart': ['work_loaded'],
  'lib/ui/widgets/work_status_line.dart': [
    'work_not_answering',
    'work_runaway',
  ],
  // Step 2 leftovers (test/goldens/work_parts_golden_test.dart): the other
  // servers and the shell's connection line on the other tabs.
  'lib/ui/widgets/other_servers_panel.dart': ['work_other_servers'],
  'lib/ui/widgets/connection_status_banner.dart': ['shell_reconnecting'],
  // §9 step 3: phone setup (start, customize, progress, ready, the welcome's
  // setup line) and the "This phone" card.
  'lib/ui/screens/phone_setup/phone_setup_start_screen.dart': [
    'setup_start',
    'setup_start_progress',
    'setup_start_stopped',
    'setup_start_ready',
    'setup_start_termux',
  ],
  'lib/ui/screens/phone_setup/phone_setup_customize_sheet.dart': [
    'setup_customize',
  ],
  'lib/ui/screens/phone_setup/phone_setup_progress_screen.dart': [
    'setup_progress_running',
    'setup_progress_failed',
    'setup_progress_log',
  ],
  'lib/ui/widgets/setup_progress_view.dart': [
    'setup_progress_running',
    'setup_progress_failed',
    'setup_progress_log',
  ],
  // Motion and illustration, slice A (docs/design/motion-and-illustration-
  // 2026-09-25.md): the setup hero page (start and ready).
  'lib/ui/screens/phone_setup/phone_setup_hero.dart': [
    'setup_start',
    'setup_ready',
  ],
  'lib/ui/screens/phone_setup/phone_setup_ready_screen.dart': ['setup_ready'],
  'lib/ui/screens/phone_setup/phone_setup_welcome_entry.dart': [
    'setup_welcome_entry',
  ],
  'lib/ui/widgets/phone_server_card.dart': [
    'phone_card_running',
    'phone_card_stopped',
    'phone_card_setting_up',
  ],
  // §9 step 4: AI Team home and run (test/goldens/team_golden_test.dart,
  // team_agent_golden_test.dart, team_sheets_golden_test.dart).
  'lib/ui/screens/team/team_home_screen.dart': [
    'team_home_loaded',
    'team_home_loaded_phone',
    'team_home_empty',
    'team_home_error',
    'team_home_not_answering',
    'team_home_starting',
  ],
  'lib/ui/screens/team/team_states.dart': [
    'team_home_error',
    'team_home_not_answering',
    'team_home_starting',
  ],
  'lib/ui/screens/team/start_run_sheet.dart': ['team_start_run'],
  // The separate controls scene was removed with the details sheet in
  // screen-team-1 (docs/qa/revamp-screen-team-1-2026-09-27/README.md).
  'lib/ui/screens/team/agent_screen.dart': ['team_agent_top'],
  'lib/ui/screens/team/gate_sheet.dart': ['team_gate_sheet'],
  'lib/ui/screens/team/work_sheet.dart': ['team_work_sheet'],
  'lib/ui/screens/team/merge_section.dart': ['team_merge'],
  // The AI Team redesign (docs/design/aiteam-redesign-2026-09-24.md): the
  // agents list, the shared needs-you block, the plain agent row.
  'lib/ui/screens/team/team_agents_screen.dart': ['team_agents'],
  'lib/ui/screens/team/team_needs_you.dart': [
    'team_home_loaded',
    'team_run_overview',
  ],
  'lib/ui/widgets/team_agent_row.dart': ['team_agents'],
  // Motion slice D (docs/design/motion-and-illustration-2026-09-25.md):
  // the merged celebration and the Needs-you nudge.
  'lib/ui/widgets/team_moments.dart': [
    'team_run_merged',
    'team_home_loaded',
    'team_run_overview',
  ],
  // Finding the AI Team while it is off (docs/qa/team-discover-2026-09-25):
  // Work without the offer (it moved to Settings › AI Team), and the intro
  // (test/goldens/team_discover_golden_test.dart).
  'lib/ui/widgets/team_discover.dart': ['team_discover_work'],
  'lib/ui/screens/team/team_intro_screen.dart': [
    'team_intro_phone',
    'team_intro_computer',
  ],
  'lib/ui/kit/scenes/team_discover_scenes.dart': [
    'team_discover_scene_teaser',
    'team_discover_scene_relay',
  ],
  // §9 step 5: the chat's states and banners (not the transcript's
  // messages). Loading, could not load, the one status line (connection,
  // a message not sent, a prompt error, staged revert, subagent, sharing).
  'lib/ui/screens/chat/chat_states.dart': [
    'chat_loading',
    'chat_load_error',
    'chat_send_error',
    'chat_disconnected',
  ],
  // The request cards above the composer: permission, question, form, retry.
  'lib/ui/screens/chat/attention_card.dart': ['chat_permission'],
  'lib/ui/screens/chat/permission_sheet.dart': ['chat_permission_sheet'],
  'lib/ui/screens/chat/empty_chat.dart': ['chat_empty'],
  // §9 step 6: Settings (goldens: test/goldens/settings_golden_test.dart).
  'lib/ui/screens/settings_screen.dart': ['settings_hub'],
  'lib/ui/screens/settings/default_shell_row.dart': ['settings_hub'],
  'lib/ui/screens/settings/server_settings_screen.dart': [
    'settings_this_server',
  ],
  'lib/ui/screens/settings/notifications_settings_screen.dart': [
    'settings_notifications',
  ],
  'lib/ui/screens/settings/personal_settings_screens.dart': [
    'settings_appearance',
    'settings_privacy',
  ],
  'lib/ui/screens/app_diagnostics_screen.dart': [
    'settings_diagnostics',
    'settings_diagnostics_empty',
  ],
  'lib/ui/screens/perf_trace_section.dart': ['settings_diagnostics'],
  'lib/ui/screens/about_screen.dart': ['settings_about'],
  'lib/ui/screens/servers_screen.dart': [
    'servers_list',
    'servers_add',
    'servers_add_failed',
    'servers_phone',
    // The motion pass, slice B (test/goldens/servers_motion_golden_test.dart):
    // the welcome's hero, Add server rebuilt (ledger row 15) and its
    // connection moments.
    'servers_welcome',
    'add_server_manual',
    'add_server_codex',
    'add_server_paseo',
    'add_server_testing',
    'add_server_paired',
    'add_server_failed',
    'first_run_connect',
  ],
  // The Servers, On this phone and Plugins cleanup
  // (docs/design/phone-server-screens-cleanup-2026-09-24.md; goldens:
  // test/goldens/phone_server_screens_golden_test.dart): the phone's server
  // as one row, its options on On this phone, the server's plugins.
  'lib/ui/widgets/local_server_row.dart': ['servers_phone'],
  'lib/ui/widgets/termux_running_server_entry.dart': ['servers_phone'],
  'lib/ui/widgets/managed_server_recovery_option.dart': ['phone_running'],
  'lib/ui/widgets/termux_phone_tools.dart': ['phone_running'],
  'lib/ui/screens/settings/server_plugins_section.dart': ['plugins_server'],
  // Open a project for OpenCode inside the app: the folder browser
  // (test/goldens/folder_browser_golden_test.dart).
  'lib/ui/widgets/folder_browser.dart': [
    'folder_browser_projects',
    'folder_browser_inside',
    'folder_browser_loading',
    'folder_browser_empty',
    'folder_browser_error',
  ],
  // §10 slice C: the empty, quiet and failure drawings, one per kind of
  // state (test/kit_states_scenes_test.dart holds their goldens).
  'lib/ui/kit/scenes/states_scenes.dart': [
    'states_sheet',
    'states_folder',
    'states_tray',
    'states_search',
    'states_terminal',
    'states_terminal_ended',
    'states_unplugged',
  ],
  'lib/ui/kit/scenes/states_working_scene.dart': ['states_working'],
};

/// file -> classes migrated inside a file too mixed to list whole, with the
/// golden renders that show them. Only grows.
const _migratedClasses = <String, Map<String, List<String>>>{
  // The transcript file: only the error a reply carries is a state; the
  // messages themselves are not part of the standard's step 5.
  'lib/ui/screens/chat/message_view.dart': {
    // _ErrorActionCard folded into _AssistantErrorRow (one KitNotice,
    // revamp chat-1, 2026-09-27); its label left with the class.
    '_AssistantErrorRow': ['chat_model_error'],
  },
};

/// file -> (pattern, reason) of raw parts a migrated screen gave up. They
/// must not come back.
const _retired = <String, Map<String, String>>{
  'lib/ui/screens/chat_screen.dart': {
    'LoadingList(': 'first load is KitSkeletonTranscript + the loading bar',
    'ProductErrorState(': 'a conversation that could not load is KitStateView',
    'ConnectionStatusBanner(': 'the connection is the one KitStatusLine',
  },
  // §10 slice C: these screens' empty and failure states are KitStateView
  // with the state drawings.
  'lib/ui/screens/global_sessions_screen.dart': {
    'ProductErrorState(':
        'could not load is KitStateView + the unplugged cable',
    'ProductEmptyState(': 'none yet / no match are KitStateView + a drawing',
  },
  'lib/ui/screens/terminal_screen.dart': {
    'ProductErrorState(':
        'could not list is KitStateView + the unplugged cable',
    'ProductEmptyState(': 'no terminal is KitStateView + the terminal window',
  },
  'lib/ui/screens/chat/message_view.dart': {
    '_PromptErrorBanner': 'a prompt error is the one KitStatusLine',
    '_SubagentContextBanner': 'the subagent context is the one KitStatusLine',
    '_SharedSessionBanner': 'sharing is the one KitStatusLine',
  },
};

/// file -> (pattern, reason) exceptions. Keep it short.
const _allowed = <String, Map<String, String>>{
  'lib/ui/screens/workspace_screen.dart': {
    // Not raw progress: the conversation row's breathing "working" dot is a
    // state mark, and pull to refresh is the kit's (KitRefresh).
  },
};

/// A pattern of the design standard itself (§8), counted in every migrated
/// file.
KitPattern _standard(String name, String pattern) {
  final re = RegExp(pattern);
  return KitPattern(
    gate: 'DS',
    name: name,
    rules: const ['TEST-10', 'STATE-1'],
    scope: (_) => true,
    count: (code) => re.allMatches(code).length,
  );
}

/// pattern id -> pattern. A migrated file holds each one that applies to its
/// path at zero (or, for a file migrated before [_g3xSince], at or below its
/// baseline count).
final _forbidden = <String, KitPattern>{
  for (final pattern in [
    _standard('LinearProgressIndicator', r'\bLinearProgressIndicator\b'),
    _standard('CircularProgressIndicator', r'\bCircularProgressIndicator\b'),
    _standard('Card(', r'\bCard\('),
    _standard('FilledButton', r'\bFilledButton\b'),
    ...kitGatePatterns,
  ])
    pattern.id: pattern,
};

const _baselinePath = 'test/design_standard_baseline.json';

/// The 57 labels (path, or path#Class) migrated before [_g3xSince]: the
/// only ones the baseline may hold. Never grows; a label leaves only when its
/// file is deleted (TEST-10). Every other migrated entry is absolute.
const _grandfathered = <String>{
  'lib/ui/kit/scenes/states_scenes.dart',
  'lib/ui/kit/scenes/states_working_scene.dart',
  'lib/ui/kit/scenes/team_discover_scenes.dart',
  'lib/ui/screens/about_screen.dart',
  'lib/ui/screens/app_diagnostics_screen.dart',
  'lib/ui/screens/chat/attention_card.dart',
  'lib/ui/screens/chat/chat_states.dart',
  'lib/ui/screens/chat/empty_chat.dart',
  'lib/ui/screens/chat/message_view.dart#_AssistantErrorRow',
  'lib/ui/screens/chat/permission_sheet.dart',
  'lib/ui/screens/perf_trace_section.dart',
  'lib/ui/screens/phone_setup/phone_setup_customize_sheet.dart',
  'lib/ui/screens/phone_setup/phone_setup_hero.dart',
  'lib/ui/screens/phone_setup/phone_setup_progress_screen.dart',
  'lib/ui/screens/phone_setup/phone_setup_ready_screen.dart',
  'lib/ui/screens/phone_setup/phone_setup_start_screen.dart',
  'lib/ui/screens/phone_setup/phone_setup_welcome_entry.dart',
  'lib/ui/screens/servers_screen.dart',
  'lib/ui/screens/settings/default_shell_row.dart',
  'lib/ui/screens/settings/notifications_settings_screen.dart',
  'lib/ui/screens/settings/personal_settings_screens.dart',
  'lib/ui/screens/settings/server_plugins_section.dart',
  'lib/ui/screens/settings/server_settings_screen.dart',
  'lib/ui/screens/settings_screen.dart',
  'lib/ui/screens/team/agent_screen.dart',
  'lib/ui/screens/team/gate_sheet.dart',
  'lib/ui/screens/team/merge_section.dart',
  'lib/ui/screens/team/start_run_sheet.dart',
  'lib/ui/screens/team/team_agents_screen.dart',
  'lib/ui/screens/team/team_home_screen.dart',
  'lib/ui/screens/team/team_intro_screen.dart',
  'lib/ui/screens/team/team_needs_you.dart',
  'lib/ui/screens/team/team_states.dart',
  'lib/ui/screens/team/work_sheet.dart',
  'lib/ui/screens/workspace_screen.dart',
  'lib/ui/screens/workspace/workspace_actions.dart',
  'lib/ui/screens/workspace/workspace_build.dart',
  'lib/ui/screens/workspace/workspace_load.dart',
  'lib/ui/screens/workspace/workspace_sheets.dart',
  'lib/ui/screens/workspace/workspace_widgets.dart',
  'lib/ui/widgets/connection_status_banner.dart',
  'lib/ui/widgets/folder_browser.dart',
  'lib/ui/widgets/local_server_row.dart',
  'lib/ui/widgets/managed_server_recovery_option.dart',
  'lib/ui/widgets/other_projects_panel.dart',
  'lib/ui/widgets/other_servers_panel.dart',
  'lib/ui/widgets/phone_server_card.dart',
  'lib/ui/widgets/saved_server_connection_card.dart',
  'lib/ui/widgets/setup_progress_view.dart',
  'lib/ui/widgets/team_agent_row.dart',
  'lib/ui/widgets/team_discover.dart',
  'lib/ui/widgets/team_moments.dart',
  'lib/ui/widgets/termux_phone_tools.dart',
  'lib/ui/widgets/termux_running_server_entry.dart',
  'lib/ui/widgets/work_status_line.dart',
};

/// The date G3x took effect: a migrated entry absent from the baseline was
/// added on or after it.
const _g3xSince = '2026-09-26';

String _code(String path) => kitCodeOf(path);

/// The source of top-level class [name] in [code]: from its declaration to
/// the next top-level declaration (a line starting with a letter or `@`).
String _classCode(String code, String name) {
  final lines = code.split('\n');
  final start = lines.indexWhere(
    (line) => RegExp(
      '^(abstract |final )?class ${RegExp.escape(name)}\\b',
    ).hasMatch(line),
  );
  if (start < 0) return '';
  var end = start + 1;
  while (end < lines.length && !RegExp(r'^[A-Za-z@]').hasMatch(lines[end])) {
    end++;
  }
  return lines.sublist(start, end).join('\n');
}

/// pattern id -> count of every [_forbidden] pattern that applies to [path]
/// in [code], minus the [allow]ed ones; zero counts are left out.
Map<String, int> _counts(String path, String code, Map<String, String>? allow) {
  final counts = <String, int>{};
  for (final MapEntry(key: id, value: pattern) in _forbidden.entries) {
    if (allow?.containsKey(id) ?? false) continue;
    if (!pattern.appliesTo(path)) continue;
    final count = pattern.count(code);
    if (count > 0) counts[id] = count;
  }
  return counts;
}

/// label -> pattern id -> count, for every migrated file (label = path) and
/// migrated class (label = path#Class).
Map<String, Map<String, int>> _currentCounts() => {
  for (final path in _migrated.keys)
    path: _counts(path, _code(path), _allowed[path]),
  for (final MapEntry(key: path, value: classes) in _migratedClasses.entries)
    for (final name in classes.keys)
      '$path#$name': _counts(path, _classCode(_code(path), name), null),
};

/// label -> pattern id -> count, or null when there is no baseline file.
Map<String, Map<String, int>>? _loadBaseline() {
  final file = File(_baselinePath);
  if (!file.existsSync()) return null;
  final raw = jsonDecode(file.readAsStringSync()) as Map<String, dynamic>;
  final entries = raw['entries'] as Map<String, dynamic>;
  return {
    for (final MapEntry(key: label, value: counts) in entries.entries)
      label: {
        for (final MapEntry(key: id, value: n)
            in (counts as Map<String, dynamic>).entries)
          id: (n as num).toInt(),
      },
  };
}

String _encodeBaseline(Map<String, Map<String, int>> entries) {
  final labels = entries.keys.toList()..sort();
  final json = {
    'about':
        'G3x baseline (docs/ux-system/revamp/STANDARDS.md §18.2, TEST-10): '
        'forbidden-pattern counts of the screens migrated before '
        '$_g3xSince. Counts only fall; entries are never added. '
        'See test/design_standard_test.dart.',
    'entries': {
      for (final label in labels)
        label: {
          for (final id in (entries[label]!.keys.toList()..sort()))
            id: entries[label]![id],
        },
    },
  };
  return '${const JsonEncoder.withIndent('  ').convert(json)}\n';
}

/// Failures of [current] against [baseline]: a count above its baseline, a
/// pattern the baseline lacks, or any count at all in an entry the baseline
/// lacks (migrated on or after [_g3xSince], so absolute).
List<String> _ratchetProblems(
  Map<String, Map<String, int>> current,
  Map<String, Map<String, int>> baseline,
) {
  final problems = <String>[];
  for (final MapEntry(key: label, value: counts) in current.entries) {
    final base = baseline[label];
    for (final MapEntry(key: id, value: n) in counts.entries) {
      final rules = _forbidden[id]!.rules.join(', ');
      if (base == null) {
        problems.add(
          '$label: $id x$n (migrated on or after $_g3xSince, so it must be '
          'zero; $rules)',
        );
      } else if (n > (base[id] ?? 0)) {
        problems.add(
          '$label: $id rose from ${base[id] ?? 0} to $n (the baseline only '
          'shrinks; $rules)',
        );
      }
    }
  }
  return problems;
}

/// The file part of a baseline label (`path` or `path#Class`).
String _labelPath(String label) => label.split('#').first;

/// The smaller baseline when [current] is below [baseline] somewhere, else
/// null. Never adds an entry; drops an entry only when its file is gone (an
/// entry that left `_migrated` while its file exists is kept, and the test
/// fails on it).
Map<String, Map<String, int>>? _shrunk(
  Map<String, Map<String, int>> current,
  Map<String, Map<String, int>> baseline,
) {
  var changed = false;
  final next = <String, Map<String, int>>{};
  for (final MapEntry(key: label, value: base) in baseline.entries) {
    final counts = current[label];
    if (counts == null) {
      if (File(_labelPath(label)).existsSync()) {
        next[label] = base;
      } else {
        changed = true;
      }
      continue;
    }
    final kept = <String, int>{};
    for (final MapEntry(key: id, value: n) in base.entries) {
      final now = counts[id] ?? 0;
      final value = now < n ? now : n;
      if (value != n) changed = true;
      if (value > 0) kept[id] = value;
    }
    next[label] = kept;
  }
  return changed ? next : null;
}

void main() {
  final writeMode = Platform.environment['DESIGN_STANDARD_WRITE'] == '1';
  final current = _currentCounts();
  var baseline = _loadBaseline();
  if (writeMode) {
    // Only lowers counts and drops entries whose file is gone; never adds
    // one, and never recreates a missing file (that would baseline entries
    // migrated after [_g3xSince]).
    if (baseline == null) {
      stdout.writeln(
        'DESIGN_STANDARD_WRITE=1: refusing to write, $_baselinePath is '
        'missing (restore it from git; write mode only lowers counts)',
      );
    } else {
      final next = _shrunk(current, baseline) ?? baseline;
      File(_baselinePath).writeAsStringSync(_encodeBaseline(next));
      baseline = next;
      stdout.writeln(
        'DESIGN_STANDARD_WRITE=1: wrote $_baselinePath (${next.length})',
      );
    }
  }

  /// The baseline, or a failure that says how to get it back.
  Map<String, Map<String, int>> requireBaseline() {
    expect(
      baseline,
      isNotNull,
      reason:
          '$_baselinePath is missing: restore it from git '
          '(it is never regenerated)',
    );
    return baseline!;
  }

  test('G3x: migrated screens hold every forbidden pattern at zero '
      '(or below their baseline)', () {
    final fileCounts = {
      for (final path in _migrated.keys) path: current[path]!,
    };
    final problems = _ratchetProblems(fileCounts, requireBaseline());
    expect(problems, isEmpty, reason: 'use lib/ui/kit/ (design standard §8)');
  });

  test('G3x: migrated classes in mixed files use the kit', () {
    for (final MapEntry(key: path, value: classes)
        in _migratedClasses.entries) {
      final code = _code(path);
      for (final name in classes.keys) {
        expect(
          _classCode(code, name),
          isNotEmpty,
          reason: '$path has no class $name',
        );
      }
    }
    final classCounts = {
      for (final MapEntry(key: label, value: counts) in current.entries)
        if (label.contains('#')) label: counts,
    };
    final problems = _ratchetProblems(classCounts, requireBaseline());
    expect(problems, isEmpty, reason: 'use lib/ui/kit/ (design standard §8)');
  });

  test('G3x: the baseline only shrinks and names only migrated entries', () {
    expect(
      _grandfathered.length,
      lessThanOrEqualTo(57),
      reason: '_grandfathered never grows',
    );
    final problems = <String>[];
    for (final MapEntry(key: label, value: counts)
        in requireBaseline().entries) {
      if (!_grandfathered.contains(label)) {
        problems.add(
          '$label is in the baseline but was not migrated before '
          '$_g3xSince: remove it (a new entry starts at zero; TEST-10, '
          'PROC-13)',
        );
      }
      if (!current.containsKey(label) && File(_labelPath(label)).existsSync()) {
        problems.add(
          '$label left _migrated but its file still exists: put it back in '
          '_migrated (TEST-10: an entry leaves only when its file is deleted)',
        );
      }
      for (final id in counts.keys) {
        if (!_forbidden.containsKey(id)) problems.add('$label: unknown $id');
      }
    }
    expect(problems, isEmpty);
    final smaller = _shrunk(current, requireBaseline());
    if (smaller != null) {
      stdout.writeln(
        '--- G3x: counts fell; commit this as $_baselinePath '
        '(or run with DESIGN_STANDARD_WRITE=1) ---\n'
        '${_encodeBaseline(smaller)}--- end baseline ---',
      );
    }
  });

  test('G3x: a screen migrated on or after $_g3xSince has an _ar_dark '
      'golden', () {
    final entries = <String, List<String>>{
      ..._migrated,
      for (final MapEntry(key: path, value: classes)
          in _migratedClasses.entries)
        for (final MapEntry(key: name, value: goldens) in classes.entries)
          '$path#$name': goldens,
    };
    final missing = <String>[];
    for (final MapEntry(key: label, value: names) in entries.entries) {
      if (requireBaseline().containsKey(label)) continue;
      final found = names.any(
        (name) => File('test/goldens/${name}_ar_dark.png').existsSync(),
      );
      if (!found) {
        missing.add(
          '$label: none of ${names.map((n) => '${n}_ar_dark.png').join(', ')}',
        );
      }
    }
    expect(missing, isEmpty, reason: 'TEST-10: the loaded state in Arabic');
  });

  test('raw parts a migrated screen gave up do not come back', () {
    final problems = <String>[];
    for (final MapEntry(key: path, value: patterns) in _retired.entries) {
      final code = _code(path);
      for (final MapEntry(key: pattern, value: reason) in patterns.entries) {
        expect(reason.trim().length, greaterThan(10), reason: pattern);
        if (code.contains(pattern)) problems.add('$path: $pattern ($reason)');
      }
    }
    expect(problems, isEmpty);
  });

  test('every allowlist entry names a migrated file and gives a reason', () {
    for (final MapEntry(key: path, value: entries) in _allowed.entries) {
      expect(_migrated.containsKey(path), isTrue, reason: path);
      for (final MapEntry(key: pattern, value: reason) in entries.entries) {
        expect(_forbidden.containsKey(pattern), isTrue, reason: pattern);
        expect(reason.trim().length, greaterThan(10), reason: pattern);
      }
    }
  });

  test('each migrated screen has dark and light goldens at 412x915', () {
    final missing = <String>[];
    final goldens = [
      ..._migrated.values,
      for (final classes in _migratedClasses.values) ...classes.values,
    ];
    for (final names in goldens) {
      for (final name in names) {
        for (final mode in ['dark', 'light']) {
          final file = File('test/goldens/${name}_$mode.png');
          if (!file.existsSync()) missing.add(file.path);
        }
      }
    }
    expect(missing, isEmpty);
  });

  test('scenes take their time only from KitIllustration (§10)', () {
    // A drawing paints one frame from the frame it is given; the widget
    // owns the clock, so reduced motion and the test switch reach every
    // scene. A scene with its own ticker or timer would escape both.
    final problems = <String>[];
    for (final file in Directory('lib/ui/kit/scenes').listSync()) {
      if (file is! File || !file.path.endsWith('.dart')) continue;
      final code = _code(file.path);
      for (final pattern in [
        'AnimationController',
        'Ticker',
        'Timer',
        'flutter_animate',
      ]) {
        if (code.contains(pattern)) problems.add('${file.path}: $pattern');
      }
    }
    expect(problems, isEmpty);
  });

  test('the kit is the one place raw progress and filled buttons live', () {
    // The rule only means something if the kit really is where they went:
    // the kit's own files may use them, and say so.
    final kit = Directory('lib/ui/kit')
        .listSync()
        .whereType<File>()
        .where((file) => file.path.endsWith('.dart'))
        .map((file) => _code(file.path))
        .join('\n');
    expect(kit, contains('LinearProgressIndicator'));
    expect(kit, contains('FilledButton'));
  });

  group('G3x counters on fixture strings (proves the patterns)', () {
    const screen = 'lib/ui/screens/fixture_only.dart';
    const kitPart = 'lib/ui/kit/kit_fixture_only.dart';
    Map<String, int> count(String path, String source) =>
        kitCountPatterns(path, kitStripLineComments(source));

    test('every G1, G2, G7, G17 and G21 pattern is forbidden', () {
      expect(kitGatePatterns.map((p) => p.gate).toSet(), {
        'G1',
        'G2',
        'G7',
        'G17',
        'G21',
      });
      for (final pattern in kitGatePatterns) {
        expect(_forbidden[pattern.id], same(pattern), reason: pattern.id);
        expect(pattern.rules, isNotEmpty, reason: pattern.id);
      }
      final ids = kitGatePatterns.map((p) => p.id).toList();
      expect(ids.toSet().length, ids.length, reason: 'ids are unique');
    });

    test('a screen that breaks one rule of each gate fails each', () {
      const source = '''
showDialog<bool>(context: context, builder: (_) => const SizedBox());
Clipboard.setData(ClipboardData(text: 'x'));
AnimatedOpacity(duration: const Duration(milliseconds: 200), opacity: 1);
const EdgeInsets.only(left: 8);
const EdgeInsets.fromLTRB(16, 0, 8, 0);
const EdgeInsets.fromLTRB(16, 0, 16, 0);
const Text('a', textAlign: TextAlign.left);
const Color(0xFF000000);
Colors.red;
Colors.transparent;
Theme.of(context).colorScheme.primary;
roles.hairline.withValues(alpha: 0.5);
ThemeRoles.of(context).attentionFill;
KitStatusMark(tone: AppStatusTone.attention);
const TextStyle(fontSize: 13);
BorderRadius.circular(12);
const SizedBox(height: 8);
SizedBox(
  key: const Key('k'),
  width: 16,
  height: 16,
  child: const Icon(AppIcons.check),
);
SizedBox(child: SizedBox(height: 4));
const Icon(AppIcons.check, size: 18);
const Icon(AppIcons.check, size: 20);
// const TextStyle(fontSize: 99);
''';
      expect(count(screen, source), {
        'G1 showDialog(': 1,
        'G2 Clipboard.setData(': 1,
        'G2 duration: Duration(': 1,
        'G7 EdgeInsets.only(left|right:)': 1,
        'G7 EdgeInsets.fromLTRB asymmetric': 1,
        'G7 TextAlign.left|right': 1,
        'G17 Color(0x': 1,
        'G17 Colors.*': 1,
        'G17 .colorScheme.': 1,
        'G17 hairline.withValues(': 1,
        'G17 attention role': 1,
        'G21 fontSize:': 1,
        'G21 TextStyle(': 1,
        'G21 BorderRadius.circular(<n>': 1,
        'G21 EdgeInsets(<n>)': 3,
        // One line, split across lines after `key:`, and only the inner
        // call of a nested pair.
        'G21 SizedBox(width|height: <n>)': 3,
        'G21 Icon size not 20/22/24': 1,
      });
    });

    test(
      'scopes: the kit and the theme files are judged by their own rules',
      () {
        const source = '''
Theme.of(context).colorScheme.primary;
const TextStyle(fontSize: 13);
showDialog<bool>(context: context, builder: (_) => const SizedBox());
''';
        // Inside the kit: dialogs, TextStyle and colorScheme are its job, but
        // a numeric fontSize is not.
        expect(count(kitPart, source), {'G21 kit fontSize: <n>': 1});
        // The theme files define colour and type; only G1 looks there.
        expect(count('lib/ui/app_theme.dart', source), {'G1 showDialog(': 1});
        // A token file may hold the numbers.
        expect(count('lib/ui/kit/kit_tokens.dart', source), isEmpty);
      },
    );

    test('directional and themed forms pass', () {
      const source = '''
const EdgeInsetsDirectional.only(start: KitTokens.space2);
const EdgeInsets.symmetric(horizontal: KitTokens.gutter);
const Text('a', textAlign: TextAlign.start);
AlignmentDirectional.centerStart;
roles.text1;
KitText.of(context).body;
const PositionedDirectional(start: 0, child: SizedBox.shrink());
BorderRadius.circular(KitTokens.panelRadius);
''';
      expect(count(screen, source), isEmpty);
    });

    test('bidi literals count outside the kit, as characters or escapes', () {
      const source =
          "final a = '\u2068name\u2069';\n"
          r"final b = '\u2066path\u2069';";
      expect(count(screen, source), {'G7 bidi literal': 4});
      expect(count(kitPart, source), isEmpty);
    });

    test('an argument list is read to its closing parenthesis', () {
      final calls = kitCallArguments(
        "Positioned(top: f(1), child: Text(')'), right: 0)",
        RegExp(r'\bPositioned\('),
      );
      expect(calls, ["top: f(1), child: Text(')'), right: 0"]);
      expect(kitSplitArguments('a(b, c), d, [e, f]'), [
        'a(b, c)',
        'd',
        '[e, f]',
      ]);
    });
  });
}
