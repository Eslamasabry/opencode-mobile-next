import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

/// One word per action (UX reorganization plan, section 6 "Glossary").
///
/// A person should meet one label for re-attempting something that failed
/// ("Try again") and one for ending running work ("Stop"). This scans the
/// English source strings for the banned synonyms on *action labels* so a new
/// "Retry" button cannot drift back in unnoticed.
///
/// Only short labels are checked: status sentences such as "Free some space
/// and retry." are prose, not a control, and stay free to use the verb.

/// Terms an action label may not equal or start with.
const _bannedTerms = <String>['Retry', 'Check again', 'Check status', 'Abort'];

/// Keys allowed to keep a banned term. Keep this list small; every entry needs
/// its reason.
const _allowedKeys = <String>{
  // "Retry last prompt" is a named conversation action in the session menu:
  // it sends the previous prompt again on purpose. It is not the recovery
  // button of an error state, which is what "Try again" names.
  'chatUiRetryLastPrompt',
};

/// One word per noun (plan section 3, decision 1): a piece of work with the
/// agent is a "conversation". A short label may not carry these standalone
/// words. Only labels are scanned, so a sentence that quotes the `--session`
/// flag is not the concern here.
const _bannedNouns = <String>['session', 'sessions', 'chat', 'chats'];

/// Labels allowed to keep a banned noun. Every entry names a different object
/// from a conversation.
const _allowedNounKeys = <String>{
  // Chats first (owner decision 2026-10-03, mockup UxsoCXcQfjeVXxpEoWJb7Y):
  // the first tab is named "Chats", the one place the owner chose the short
  // word over "Conversations" for a tab label.
  'shellTabChats',
  'e7LocaleUiWorkspace',
  // AI Team (Gas City): an agent's *host session* is the long-lived runtime
  // process on the team host. It has an age, a name and an id, it is stopped
  // and restarted from the controls, and it is not a conversation the person
  // opens from the conversation list.
  'teamUiAgentLabelSessionAge',
  'teamUiAgentLabelSessionId',
  'teamUiAgentLabelSessionName',
  'teamUiAgentSessionAge',
  'teamUiAgentTermSession',
  'teamUiWorkLabelSession',
  'teamUiWorkLabelSessionName',
  'teamUiWorkSheetOpenSession',
  'teamUiGateLabelSessionId',
};

/// The AI Team's list rows, card and run Overview speak the person's words
/// (docs/design/aiteam-redesign-2026-09-24.md): a run is a "task", agents
/// are named by role, the host is "this phone" or the computer's name.
/// Gas City's insides, its loopback address and version numbers stay under
/// Technical details. Every string of these surfaces is scanned, labels and
/// sentences alike.
const _teamSurfacePrefixes = <String>[
  'teamUiCard',
  'teamUiHome',
  'teamUiRun',
  'teamUiTask',
  'teamUiAgentRole',
  'teamUiAgentsList',
  'teamUiHostPhrase',
  // The board (docs/design/team-board-2026-09-26.md): tasks, not beads.
  'teamBoard',
];

final _engineWords = RegExp(
  r'\b(convoys?|formulas?|beads?|rigs?|city|polecats?|refinery|sling|'
  r'wisps?|mayor|gastown)\b|127\.0\.0\.1|\{version\}|\b\d+\.\d+\.\d+\b',
  caseSensitive: false,
);

/// Strings of those surfaces shown only under Technical details, where the
/// engine's own words belong. Every entry names where it is shown.
const _allowedEngineKeys = <String>{
  // The run's Technical details sheet: the formula id's label.
  'teamUiRunLabelFormula',
  // The run's Technical details sheet: the product word beside the Gas
  // City term ("Task · convoy").
  'teamUiRunTermBatch',
  'teamUiRunTermFormula',
};

/// At most this many words makes a value a label rather than a sentence.
const _maxLabelWords = 4;

final _sentencePunctuation = RegExp(r'[.!?:;,…]');

bool _isShortActionLabel(String value) {
  final trimmed = value.trim();
  if (trimmed.isEmpty) return false;
  if (_sentencePunctuation.hasMatch(trimmed)) return false;
  return trimmed.split(RegExp(r'\s+')).length <= _maxLabelWords;
}

/// The banned term [value] equals or starts with, or null. "Retrying" is a
/// status, not the action "Retry", so the term must end at a word boundary.
String? _bannedTermIn(String value) {
  final trimmed = value.trim();
  for (final term in _bannedTerms) {
    if (RegExp('^${RegExp.escape(term)}\\b').hasMatch(trimmed)) return term;
  }
  return null;
}

/// The banned noun [value] contains as a standalone word, or null.
String? _bannedNounIn(String value) {
  for (final noun in _bannedNouns) {
    final pattern = RegExp(
      '\\b${RegExp.escape(noun)}\\b',
      caseSensitive: false,
    );
    if (pattern.hasMatch(value)) return noun;
  }
  return null;
}

Map<String, String> _englishStrings() {
  final decoded =
      jsonDecode(File('lib/l10n/app_en.arb').readAsStringSync())
          as Map<String, dynamic>;
  return {
    for (final entry in decoded.entries)
      if (!entry.key.startsWith('@') && entry.value is String)
        entry.key: entry.value as String,
  };
}

void main() {
  test('the label rule separates labels from sentences', () {
    expect(_isShortActionLabel('Retry'), isTrue);
    expect(_isShortActionLabel('Retry saving draft'), isTrue);
    expect(_isShortActionLabel('Retry {runtime}'), isTrue);
    expect(_isShortActionLabel('Free some space and retry.'), isFalse);
    expect(
      _isShortActionLabel('Retry disabling recovery on this phone'),
      isFalse,
    );
    expect(_bannedTermIn('Retry'), 'Retry');
    expect(_bannedTermIn('Retry projects'), 'Retry');
    expect(_bannedTermIn('Check status'), 'Check status');
    expect(_bannedTermIn('Retrying'), isNull);
    expect(_bannedTermIn('Try again'), isNull);
    expect(_bannedTermIn('Aborted'), isNull);
  });

  test('action labels use the glossary word, not a banned synonym', () {
    final offenders = <String>[];
    for (final entry in _englishStrings().entries) {
      if (_allowedKeys.contains(entry.key)) continue;
      if (!_isShortActionLabel(entry.value)) continue;
      final term = _bannedTermIn(entry.value);
      if (term == null) continue;
      offenders.add('${entry.key}: "${entry.value}" starts with "$term"');
    }
    expect(
      offenders,
      isEmpty,
      reason:
          'Use "Try again" to re-attempt a failure, "Refresh" to re-read '
          'current data and "Stop" to end running work '
          '(docs/design/ux-reorganization-plan-2026-09-19.md, section 6).\n'
          '${offenders.join('\n')}',
    );
  });

  test('every allow-listed key still needs its exemption', () {
    final strings = _englishStrings();
    for (final key in _allowedKeys) {
      final value = strings[key];
      expect(value, isNotNull, reason: '$key is allow-listed but gone');
      expect(
        _isShortActionLabel(value!) && _bannedTermIn(value) != null,
        isTrue,
        reason: '$key no longer uses a banned term; drop it from the list',
      );
    }
  });

  test('the noun rule matches standalone words only', () {
    expect(_bannedNounIn('New session'), 'session');
    expect(_bannedNounIn('All sessions'), 'sessions');
    expect(_bannedNounIn('Untitled chat'), 'chat');
    expect(_bannedNounIn('New chats'), 'chats');
    expect(_bannedNounIn('SESSION'), 'session');
    expect(_bannedNounIn('New conversation'), isNull);
    expect(_bannedNounIn('Chatty'), isNull);
    expect(_bannedNounIn('Obsession'), isNull);
  });

  test('labels say conversation, not session or chat', () {
    final offenders = <String>[];
    for (final entry in _englishStrings().entries) {
      if (_allowedNounKeys.contains(entry.key)) continue;
      if (!_isShortActionLabel(entry.value)) continue;
      final noun = _bannedNounIn(entry.value);
      if (noun == null) continue;
      offenders.add('${entry.key}: "${entry.value}" contains "$noun"');
    }
    expect(
      offenders,
      isEmpty,
      reason:
          'A piece of work with the agent is a "conversation" '
          '(docs/design/ux-reorganization-plan-2026-09-19.md, sections 3 '
          'and 6). Typed slash commands such as /sessions keep their names; '
          'the visible label does not.\n'
          '${offenders.join('\n')}',
    );
  });

  test('the engine-word rule catches Gas City terms, not plain words', () {
    expect(_engineWords.hasMatch('Batch · convoy · 1 of 5 done'), isTrue);
    expect(_engineWords.hasMatch('Run · formula mol-upgrade'), isTrue);
    expect(_engineWords.hasMatch('city phone'), isTrue);
    expect(_engineWords.hasMatch('127.0.0.1 · This phone'), isTrue);
    expect(_engineWords.hasMatch('Gas City {version}'), isTrue);
    expect(_engineWords.hasMatch('Gas City 1.4.1'), isTrue);
    expect(_engineWords.hasMatch('gastown.polecat'), isTrue);
    expect(_engineWords.hasMatch('Working · 3 of 5 steps done'), isFalse);
    expect(_engineWords.hasMatch('On this phone · Not answering'), isFalse);
    expect(_engineWords.hasMatch('Reviewer (merges)'), isFalse);
    expect(_engineWords.hasMatch('Electricity'), isFalse);
  });

  test('AI Team rows and cards use the person\'s words, not the engine\'s', () {
    final offenders = <String>[];
    for (final entry in _englishStrings().entries) {
      if (!_teamSurfacePrefixes.any(entry.key.startsWith)) continue;
      if (_allowedEngineKeys.contains(entry.key)) continue;
      if (_engineWords.firstMatch(entry.value) case final match?) {
        offenders.add(
          '${entry.key}: "${entry.value}" contains "${match.group(0)}"',
        );
      }
    }
    expect(
      offenders,
      isEmpty,
      reason:
          'A task row, the Work tab card and a task\'s Overview never show '
          'Gas City\'s insides (convoy, formula, bead, rig, city, polecat, '
          'refinery, sling, wisp), 127.0.0.1 or a version; those go under '
          'Technical details (docs/design/aiteam-redesign-2026-09-24.md).\n'
          '${offenders.join('\n')}',
    );
  });

  test('every allow-listed engine key is still there and still needs it', () {
    final strings = _englishStrings();
    for (final key in _allowedEngineKeys) {
      final value = strings[key];
      expect(value, isNotNull, reason: '$key is allow-listed but gone');
      expect(
        _engineWords.hasMatch(value!),
        isTrue,
        reason: '$key no longer uses an engine word; drop it from the list',
      );
    }
  });

  test('every allow-listed noun key still needs its exemption', () {
    final strings = _englishStrings();
    for (final key in _allowedNounKeys) {
      final value = strings[key];
      expect(value, isNotNull, reason: '$key is allow-listed but gone');
      expect(
        _isShortActionLabel(value!) && _bannedNounIn(value) != null,
        isTrue,
        reason: '$key no longer uses a banned noun; drop it from the list',
      );
    }
  });

  _copyGates();
}

// ===========================================================================
// G11 and G28: the copy gates (docs/ux-system/revamp/STANDARDS.md §18).
//
// G11 (COPY-9, COPY-11, COPY-17): confirmation titles and labels,
// contradiction pairs, and engine words, ports, paths and versions.
// G28 (COPY-6–COPY-8, COPY-10, COPY-12, COPY-21, LOOK-15): the glossary
// extension over app_en.arb.
//
// Both are pure file scans. Where today's copy breaks a rule, the offenders
// sit in a committed baseline (test/ui_glossary_baseline.json) that may only
// shrink: the gate fails when a subject (a file or an ARB key) gains a
// pattern or a count rises, and when counts drop it passes and prints the
// smaller baseline to commit. The contradiction check is absolute.
//
// Regenerate the baseline after copy is fixed:
//   UI_GLOSSARY_WRITE=1 flutter test test/ui_glossary_test.dart
// then run it again without the variable and commit the smaller numbers.
// ===========================================================================

const _baselinePath = 'test/ui_glossary_baseline.json';

/// Stands in for a placeholder in a plain variant of an ICU message. A
/// placeholder counts as one word whatever it holds (COPY-10).
const _slot = '§';

Map<String, dynamic> _arbFile(String path) =>
    jsonDecode(File(path).readAsStringSync()) as Map<String, dynamic>;

Map<String, String> _valuesOf(Map<String, dynamic> arb) => {
  for (final entry in arb.entries)
    if (!entry.key.startsWith('@') && entry.value is String)
      entry.key: entry.value as String,
};

String _descriptionOf(Map<String, dynamic> arb, String key) {
  final meta = arb['@$key'];
  if (meta is Map && meta['description'] is String) {
    return meta['description'] as String;
  }
  return '';
}

// --- ICU variants ----------------------------------------------------------

/// Every plain wording of an ICU [message]: each plural or select branch
/// becomes its own variant, and every placeholder (and a plural's `#`)
/// becomes [_slot]. Capped so a message with many selects stays cheap.
List<String> _variants(String message) {
  final (variants, _) = _parseIcu(message, 0, inBranch: false);
  return variants.map((v) => v.trim()).toList();
}

const _maxVariants = 64;

(List<String>, int) _parseIcu(String s, int start, {required bool inBranch}) {
  var variants = <String>[''];
  void append(String text) => variants = [for (final v in variants) v + text];
  var i = start;
  while (i < s.length) {
    final c = s[i];
    if (c == '}' && inBranch) return (variants, i);
    if (c == '#' && inBranch) {
      append(_slot);
      i++;
      continue;
    }
    if (c != '{') {
      append(c);
      i++;
      continue;
    }
    // An argument: {name} or {name, plural|select|selectordinal, ...}.
    final close = _icuArgumentEnd(s, i);
    final inner = s.substring(i + 1, close);
    final comma = inner.indexOf(',');
    if (comma < 0) {
      append(_slot);
      i = close + 1;
      continue;
    }
    final rest = inner.substring(comma + 1);
    final typeComma = rest.indexOf(',');
    final type = (typeComma < 0 ? rest : rest.substring(0, typeComma)).trim();
    if (typeComma < 0 ||
        !const {'plural', 'select', 'selectordinal'}.contains(type)) {
      append(_slot);
      i = close + 1;
      continue;
    }
    // Branches start after the second comma of the argument.
    var j = i + 1 + comma + 1 + typeComma + 1;
    final branches = <String>[];
    while (j < close) {
      final open = s.indexOf('{', j);
      if (open < 0 || open > close) break;
      final (branch, end) = _parseIcu(s, open + 1, inBranch: true);
      branches.addAll(branch);
      j = end + 1;
    }
    if (branches.isEmpty) branches.add(_slot);
    variants = [
      for (final v in variants)
        for (final b in branches) v + b,
    ].take(_maxVariants).toList();
    i = close + 1;
  }
  return (variants, i);
}

/// Index of the `}` closing the ICU argument that opens at [open].
int _icuArgumentEnd(String s, int open) {
  var depth = 0;
  for (var i = open; i < s.length; i++) {
    if (s[i] == '{') depth++;
    if (s[i] == '}') {
      depth--;
      if (depth == 0) return i;
    }
  }
  return s.length - 1;
}

/// The words of a plain variant: tokens with a letter, a digit or a slot.
List<String> _wordsOf(String variant) => variant
    .split(RegExp(r'\s+'))
    .where((t) => RegExp('[\\p{L}\\p{N}$_slot]', unicode: true).hasMatch(t))
    .toList();

/// A G28 label: at most four words and no sentence punctuation. A trailing
/// `…` ("Ask {agent}…", a button that opens more) or `:` (a field's label)
/// does not make a sentence, so it is stripped first. The older glossary
/// tests keep their own stricter [_isShortActionLabel].
bool _isLabelVariant(String variant) {
  final trimmed = variant.trim().replaceFirst(RegExp(r'[…:]+$'), '').trim();
  if (trimmed.isEmpty) return false;
  if (_sentencePunctuation.hasMatch(trimmed)) return false;
  return _wordsOf(trimmed).length <= _maxLabelWords;
}

// --- Dart source scanning ---------------------------------------------------

bool _isIdentChar(String c) => RegExp(r'[A-Za-z0-9_$]').hasMatch(c);

/// Index just past the string literal that starts at [i] (a quote, or the
/// `r` of a raw string).
int _endOfString(String s, int i) {
  var raw = false;
  if (s[i] == 'r') {
    raw = true;
    i++;
  }
  final q = s[i];
  final triple = s.startsWith(q * 3, i);
  final close = triple ? q * 3 : q;
  i += close.length;
  while (i < s.length) {
    if (s.startsWith(close, i)) return i + close.length;
    final c = s[i];
    if (!raw && c == r'\') {
      i += 2;
      continue;
    }
    if (!raw && c == r'$' && i + 1 < s.length && s[i + 1] == '{') {
      i = _matchClose(s, i + 1) + 1;
      continue;
    }
    if (!triple && c == '\n') return i;
    i++;
  }
  return i;
}

bool _startsString(String s, int i) {
  final c = s[i];
  if (c == "'" || c == '"') return true;
  return c == 'r' &&
      i + 1 < s.length &&
      (s[i + 1] == "'" || s[i + 1] == '"') &&
      (i == 0 || !_isIdentChar(s[i - 1]));
}

const _closers = {'(': ')', '[': ']', '{': '}'};

/// Index of the bracket that closes the one at [open], skipping strings.
/// Run on source whose comments are already blanked.
int _matchClose(String s, int open) {
  final stack = <String>[_closers[s[open]]!];
  var i = open + 1;
  while (i < s.length) {
    if (_startsString(s, i)) {
      i = _endOfString(s, i);
      continue;
    }
    final c = s[i];
    if (_closers.containsKey(c)) {
      stack.add(_closers[c]!);
    } else if (c == stack.last) {
      stack.removeLast();
      if (stack.isEmpty) return i;
    }
    i++;
  }
  return s.length - 1;
}

/// [src] with every comment replaced by spaces, so offsets stay put.
String _blankComments(String src) {
  final out = StringBuffer();
  var i = 0;
  while (i < src.length) {
    if (_startsString(src, i)) {
      final end = _endOfString(src, i);
      out.write(src.substring(i, end));
      i = end;
      continue;
    }
    if (src.startsWith('//', i)) {
      var end = src.indexOf('\n', i);
      if (end < 0) end = src.length;
      out.write(' ' * (end - i));
      i = end;
      continue;
    }
    if (src.startsWith('/*', i)) {
      var end = src.indexOf('*/', i + 2);
      end = end < 0 ? src.length : end + 2;
      out.write(src.substring(i, end).replaceAll(RegExp(r'[^\n]'), ' '));
      i = end;
      continue;
    }
    out.write(src[i]);
    i++;
  }
  return out.toString();
}

/// The start and end offsets of the top-level, comma-separated pieces
/// between [open] and its closer.
List<(int, int)> _topLevelArgRanges(String s, int open, int close) {
  final pieces = <(int, int)>[];
  var startOfPiece = open + 1;
  var i = open + 1;
  while (i < close) {
    if (_startsString(s, i)) {
      i = _endOfString(s, i);
      continue;
    }
    final c = s[i];
    if (_closers.containsKey(c)) {
      i = _matchClose(s, i) + 1;
      continue;
    }
    if (c == ',') {
      pieces.add((startOfPiece, i));
      startOfPiece = i + 1;
    }
    i++;
  }
  pieces.add((startOfPiece, close));
  return pieces
      .where((p) => s.substring(p.$1, p.$2).trim().isNotEmpty)
      .toList();
}

/// The top-level, comma-separated pieces between [open] and its closer.
List<String> _topLevelArgs(String s, int open, int close) => [
  for (final (start, end) in _topLevelArgRanges(s, open, close))
    s.substring(start, end),
];

/// [s] with every string literal and every bracketed group replaced by
/// spaces: only what sits at the top level of the expression is left.
String _topLevelOnly(String s) {
  final out = StringBuffer();
  var i = 0;
  while (i < s.length) {
    final end = _startsString(s, i)
        ? _endOfString(s, i)
        : _closers.containsKey(s[i])
        ? _matchClose(s, i) + 1
        : null;
    if (end != null) {
      out.write(' ' * (end - i));
      i = end;
      continue;
    }
    out.write(s[i]);
    i++;
  }
  return out.toString();
}

/// The branches an expression can show: both sides of a top-level `?:`
/// (not its condition) and every side of a top-level `??`, recursively,
/// with parentheses around the whole expression removed.
List<String> _branchesOf(String expression) {
  var s = expression.trim();
  while (s.startsWith('(') && _matchClose(s, 0) == s.length - 1) {
    s = s.substring(1, s.length - 1).trim();
  }
  final top = _topLevelOnly(s);
  // Operator positions: a conditional `?` (not `??`, `?.` or `?[`), a `:`,
  // and a `??`.
  int? question;
  final nullish = <int>[];
  for (var i = 0; i < top.length; i++) {
    if (top[i] != '?') continue;
    final next = i + 1 < top.length ? top[i + 1] : '';
    if (next == '?') {
      nullish.add(i);
      i++;
    } else if (next != '.' && next != '[') {
      question = i;
      break;
    }
  }
  if (question != null) {
    // The `:` that closes this conditional, past any nested ones.
    var nested = 0;
    for (var i = question + 1; i < top.length; i++) {
      final c = top[i];
      final next = i + 1 < top.length ? top[i + 1] : '';
      if (c == '?' && next == '?') {
        i++;
      } else if (c == '?' && next != '.' && next != '[') {
        nested++;
      } else if (c == ':') {
        if (nested == 0) {
          return [
            ..._branchesOf(s.substring(question + 1, i)),
            ..._branchesOf(s.substring(i + 1)),
          ];
        }
        nested--;
      }
    }
    return [s];
  }
  if (nullish.isEmpty) return [s];
  final pieces = <String>[];
  var start = 0;
  for (final at in nullish) {
    pieces.add(s.substring(start, at));
    start = at + 2;
  }
  pieces.add(s.substring(start));
  return [for (final piece in pieces) ..._branchesOf(piece)];
}

final _namedArg = RegExp(r'^\s*([A-Za-z_]\w*)\s*:(?!:)');

/// Where a wrapper takes the title or the confirm label: a named argument
/// or a positional index.
typedef _Slot = ({String? name, int? index});

/// The expression passed for [slot] among a call's [args], or null.
String? _argFor(List<String> args, _Slot slot) {
  var position = 0;
  for (final arg in args) {
    final named = _namedArg.firstMatch(arg);
    if (named != null) {
      if (named.group(1) == slot.name) return arg.substring(named.end).trim();
      continue;
    }
    if (position == slot.index) return arg.trim();
    position++;
  }
  return null;
}

/// The slot of the parameter called [name] in a declaration's parameter
/// list, or null when there is none.
_Slot? _paramSlot(String params, String name) {
  final open = params.indexOf('(');
  final close = _matchClose(params, open);
  var position = 0;
  for (final piece in _topLevelArgs(params, open, close)) {
    final trimmed = piece.trim();
    if (trimmed.startsWith('{') || trimmed.startsWith('[')) {
      final named = trimmed.startsWith('{');
      final inner = trimmed.substring(1, trimmed.length - 1);
      for (final part in inner.split(',')) {
        final id = RegExp(r'(\w+)\s*(=.*)?$').firstMatch(part.trim());
        if (id?.group(1) == name) {
          return named
              ? (name: name, index: null)
              : (name: null, index: position);
        }
        if (!named) position++;
      }
      continue;
    }
    final id = RegExp(r'(\w+)\s*$').firstMatch(trimmed);
    if (id?.group(1) == name) return (name: null, index: position);
    position++;
  }
  return null;
}

/// A function that shows a confirmation: [title] and [label] say where its
/// title and confirm label come in. [file] limits a private wrapper to the
/// library that declares it.
typedef _Confirmer = ({_Slot title, _Slot label, String? file});

final _confirmDeclaration = RegExp(r'Future<bool\??>\s+([A-Za-z_]\w*)\s*\(');

/// One place where a confirmation's title or label gets its words.
typedef _ConfirmSite = ({String file, String role, String expression});

/// Every call of a confirmation (showKitConfirm, the older showConfirmSheet,
/// and every wrapper that only forwards its own title or label to one),
/// with the expression given for the title and the confirm label. A wrapper
/// is found by following a bare parameter back to the function that
/// declares it, so its callers are checked instead of the wrapper.
List<_ConfirmSite> _confirmSites() {
  final sources = <String, String>{
    for (final file in Directory('lib').listSync(recursive: true))
      if (file is File &&
          file.path.endsWith('.dart') &&
          !file.path.startsWith('lib/l10n/'))
        file.path: _blankComments(file.readAsStringSync()),
  };
  const kitSlots = (
    title: (name: 'title', index: null),
    label: (name: 'confirmLabel', index: null),
    file: null,
  );
  final confirmers = <String, _Confirmer>{
    'showKitConfirm': kitSlots,
    'showConfirmSheet': kitSlots,
  };
  while (true) {
    final sites = <_ConfirmSite>[];
    final found = <String, _Confirmer>{};
    final names = confirmers.keys.map(RegExp.escape).join('|');
    final call = RegExp('(?<![\\w\$])($names)\\s*\\(');
    for (final MapEntry(key: path, value: src) in sources.entries) {
      for (final match in call.allMatches(src)) {
        final name = match.group(1)!;
        final confirmer = confirmers[name]!;
        if (confirmer.file != null && confirmer.file != path) continue;
        final before = src.substring(0, match.start);
        if (RegExp(r'Future<bool\??>\s*$').hasMatch(before)) continue;
        final open = match.end - 1;
        final close = _matchClose(src, open);
        final args = _topLevelArgs(src, open, close);
        final forwards = <String, _Slot>{};
        for (final (role, slot) in [
          ('title', confirmer.title),
          ('label', confirmer.label),
        ]) {
          final expression = _argFor(args, slot);
          if (expression == null) continue;
          final wrapper = RegExp(r'^[A-Za-z_]\w*$').hasMatch(expression)
              ? _enclosingWrapper(src, match.start, expression)
              : null;
          if (wrapper != null) {
            forwards[role] = wrapper.slot;
            forwards['#name:${wrapper.name}'] = wrapper.slot;
            continue;
          }
          sites.add((file: path, role: role, expression: expression));
        }
        final wrapperName = forwards.keys
            .where((k) => k.startsWith('#name:'))
            .map((k) => k.substring(6))
            .firstOrNull;
        if (wrapperName != null && !confirmers.containsKey(wrapperName)) {
          final title = forwards['title'];
          final label = forwards['label'];
          found[wrapperName] = (
            title: title ?? (name: '\u0000', index: null),
            label: label ?? (name: '\u0000', index: null),
            file: wrapperName.startsWith('_') ? path : null,
          );
        }
      }
    }
    if (found.isEmpty) return sites;
    confirmers.addAll(found);
  }
}

/// The confirmation wrapper whose body holds the call at [at] and which
/// declares a parameter called [parameter], with that parameter's slot.
({String name, _Slot slot})? _enclosingWrapper(
  String src,
  int at,
  String parameter,
) {
  for (final decl in _confirmDeclaration.allMatches(src).toList().reversed) {
    if (decl.start >= at) continue;
    final open = decl.end - 1;
    final close = _matchClose(src, open);
    var i = close + 1;
    final after = src.substring(i);
    final lead = RegExp(r'^\s*(async\s*)?').firstMatch(after)!;
    i += lead.end;
    int bodyEnd;
    if (src.startsWith('=>', i)) {
      bodyEnd = src.indexOf(';', i);
      var j = i;
      while (j < src.length && src[j] != ';') {
        if (_startsString(src, j)) {
          j = _endOfString(src, j);
          continue;
        }
        if (_closers.containsKey(src[j])) {
          j = _matchClose(src, j) + 1;
          continue;
        }
        j++;
      }
      bodyEnd = j;
    } else if (i < src.length && src[i] == '{') {
      bodyEnd = _matchClose(src, i);
    } else {
      continue;
    }
    if (at > bodyEnd) return null;
    final slot = _paramSlot(src.substring(open, close + 1), parameter);
    if (slot == null) return null;
    return (name: decl.group(1)!, slot: slot);
  }
  return null;
}

/// The ARB keys an expression shows as its words: in each branch of a
/// top-level `?:` or `??`, the key read at the top level (`l10n.someKey`,
/// `copy.someKey(x)`, `lookupAppLocalizations(…).someKey`). A key inside
/// that key's argument list is a placeholder value ("this server" in
/// `l10n.disconnectTitle(name ?? l10n.thisServer)`), not the title or label.
List<String> _arbKeysIn(String expression, Map<String, String> english) => [
  for (final branch in _branchesOf(expression))
    for (final m in RegExp(
      r'\.([a-z][A-Za-z0-9]*)\b',
    ).allMatches(_topLevelOnly(branch)))
      if (english.containsKey(m.group(1))) m.group(1)!,
];

/// Words a confirm label may never be on its own (COPY-8, COPY-9).
const _bareConfirmWords = {'ok', 'yes', 'continue', 'confirm', 'delete'};

/// The COPY-9 problems of one confirmation title or label.
List<String> _confirmProblems(
  _ConfirmSite site,
  Map<String, String> english,
  Map<String, String> arabic,
) {
  final keys = _arbKeysIn(site.expression, english);
  if (keys.isEmpty) return ['${site.role} not from app_en.arb'];
  final problems = <String>[];
  for (final key in keys) {
    final en = _variants(english[key]!);
    if (site.role == 'title') {
      if (en.any((v) => !v.trimRight().endsWith('?'))) {
        problems.add('title $key: English does not end with "?"');
      }
      final ar = arabic[key];
      if (ar != null &&
          _variants(ar).any((v) {
            final t = v.trimRight();
            return !t.endsWith('؟') && !t.endsWith('?');
          })) {
        problems.add('title $key: Arabic does not end with "؟"');
      }
    } else {
      if (en.any((v) => _bareConfirmWords.contains(v.trim().toLowerCase()))) {
        problems.add('label $key: a bare OK/Yes/Continue/Confirm/Delete');
      } else if (en.any((v) => _wordsOf(v).length < 2)) {
        problems.add('label $key: fewer than two words');
      }
    }
  }
  return problems;
}

// --- G11: contradiction pairs (COPY-17) ------------------------------------

/// Words that contradict each other when one text shows both.
const _contradictions = <(String, String)>[
  ('working', 'stopped'),
  ('paused', 'idle'),
  ('connected', 'reconnecting'),
];

/// ARB keys that may name both halves of a pair, each with its reason.
const _allowedContradictionKeys = <String>{
  // A count per state ("{working} working, … {stopped} stopped"): each word
  // labels its own number of agents, so no agent is said to be both.
  'teamUiCardAgentsSummary',
};

(String, String)? _contradictionIn(String text) {
  for (final (a, b) in _contradictions) {
    final hasA = RegExp('\\b$a\\b', caseSensitive: false).hasMatch(text);
    final hasB = RegExp('\\b$b\\b', caseSensitive: false).hasMatch(text);
    if (hasA && hasB) return (a, b);
  }
  return null;
}

final _stringLiteral = RegExp(
  r"'(?:[^'\\\n]|\\.)*'"
  r'|"(?:[^"\\\n]|\\.)*"',
);

/// Arguments that are never rendered: a test's or group's description, a
/// census shot's page name, state tag and reviewer note, and a gallery
/// shot's golden name.
const _unrenderedSlots = <String, List<_Slot>>{
  'test': [(name: null, index: 0)],
  'testWidgets': [(name: null, index: 0)],
  'group': [(name: null, index: 0)],
  'CensusShot': [
    (name: null, index: 0),
    (name: 'state', index: null),
    (name: 'note', index: null),
  ],
  'kitGalleryShot': [(name: 'name', index: null)],
};

/// Calls none of whose arguments are rendered: widget keys and golden file
/// names.
final _unrenderedCalls = RegExp(
  r'(?<![\w$])(?:Key|ValueKey|ObjectKey|GlobalKey|matchesGoldenFile)\s*'
  r'(?:<[^>()]*>)?\s*\(',
);

/// A literal that is a file name ('goldens/x.png', "$name.png").
final _fileNameLiteral = RegExp(
  r"""^['"][\w./${}-]+\.(?:png|jpe?g|svg|dart|json|txt|md|arb|ttf|otf)['"]$""",
);

/// [src] (comments already blanked) with every literal that is never
/// rendered replaced by spaces, so offsets stay put.
String _renderedOnly(String src) {
  final blank = <(int, int)>[];
  final calls = RegExp(
    '(?<![\\w\$])(${_unrenderedSlots.keys.join('|')})\\s*\\(',
  );
  for (final call in calls.allMatches(src)) {
    final open = call.end - 1;
    final ranges = _topLevelArgRanges(src, open, _matchClose(src, open));
    for (final slot in _unrenderedSlots[call.group(1)]!) {
      var position = 0;
      for (final (start, end) in ranges) {
        final named = _namedArg.firstMatch(src.substring(start, end));
        final hit = named == null
            ? position++ == slot.index
            : named.group(1) == slot.name;
        if (hit) blank.add((start, end));
      }
    }
  }
  for (final call in _unrenderedCalls.allMatches(src)) {
    final open = call.end - 1;
    blank.add((open, _matchClose(src, open) + 1));
  }
  for (final literal in _stringLiteral.allMatches(src)) {
    if (_fileNameLiteral.hasMatch(literal.group(0)!)) {
      blank.add((literal.start, literal.end));
    }
  }
  final chars = src.split('');
  for (final (start, end) in blank) {
    for (var i = start; i < end; i++) {
      if (chars[i] != '\n') chars[i] = ' ';
    }
  }
  return chars.join();
}

/// One shown scene: a census shot or a kit gallery shot, or a test that
/// holds neither.
final _sceneCall = RegExp(r'(?<![\w$])(CensusShot|kitGalleryShot)\s*\(');
final _testCall = RegExp(r'(?<![\w$.])(testWidgets|test)\s*\(');

/// The text of every golden and census fixture, without the literals that
/// are never rendered (descriptions, names, state tags, notes, keys and file
/// names): each string literal on its own, and each scene as one text of
/// all its literals.
///
/// This is a static proxy for the rendered text G11 asks for (a PROC-20
/// contract problem in the QA record): text built at runtime, or kept in a
/// helper outside the scene's call, is not joined.
Map<String, String> _fixtureTexts() => {
  for (final root in ['test/goldens', 'tool/capture/census'])
    if (Directory(root).existsSync())
      for (final file in Directory(root).listSync(recursive: true))
        if (file is File && file.path.endsWith('.dart'))
          ..._sceneTexts(file.path, file.readAsStringSync()),
};

/// The rendered-text proxy of one fixture source file [source] at [path].
Map<String, String> _sceneTexts(String path, String source) {
  final texts = <String, String>{};
  final src = _renderedOnly(_blankComments(source));
  for (final literal in _stringLiteral.allMatches(src)) {
    texts['$path literal at ${literal.start}'] = literal.group(0)!;
  }
  for (final pattern in [_sceneCall, _testCall]) {
    for (final block in pattern.allMatches(src)) {
      final open = block.end - 1;
      final body = src.substring(open, _matchClose(src, open) + 1);
      if (pattern == _testCall && _sceneCall.hasMatch(body)) continue;
      texts['$path ${block.group(1)} at ${block.start}'] = _stringLiteral
          .allMatches(body)
          .map((m) => m.group(0))
          .join(' | ');
    }
  }
  return texts;
}

// --- G11: technical words (COPY-11) ----------------------------------------

/// Named patterns for text that belongs only in a Details fold, a log, a
/// code block, a technical value or a field the person types into.
final _technicalPatterns = <String, RegExp>{
  'engine word': RegExp(
    r'\b(convoys?|formulas?|beads?|rigs?|city|polecats?|refinery|sling|'
    r'wisps?|mayor|gastown|PTY|SSE)\b',
    caseSensitive: false,
  ),
  // Any IPv4 address or CIDR range (127.0.0.1, 0.0.0.0, 100.64.0.0/10),
  // and localhost.
  'address': RegExp(
    r'(?<![\w.])\d{1,3}(?:\.\d{1,3}){3}(?:/\d{1,2})?(?![\w.])|\blocalhost\b',
  ),
  'port': RegExp(r':\d{4,5}\b|\bport \d{2,5}\b', caseSensitive: false),
  // Absolute and home paths of two or more segments, and a relative path
  // that ends in a file name (docs/ai-team-host.md).
  'path': RegExp(
    r'''(?:^|[\s("'`])(?:~|\.{1,2})?/[\w.-]+/[\w./-]*|~/'''
    r'''|(?<![\w/:.-])[\w.-]+/[\w./-]*\.[A-Za-z]{1,5}\b''',
  ),
  // x.y.z anywhere; x.y after "v", "version" or a name ("A2A 1.0",
  // "Ubuntu Base 24.04") unless a unit follows ("Free 1.5 GB"); and a
  // {version} placeholder.
  'version': RegExp(
    r'(?<![\d.])v?\d+\.\d+\.\d+\b(?!\.\d)'
    r'|(?:\bv|\b[Vv]ersion\s+|\b[A-Z][\w-]*\s)\d+\.\d+(?![.\d])'
    r'(?!\s*(?:%|[KMGT]i?B\b|(?:mins?|minutes?|secs?|seconds?|hours?|days?|'
    r'weeks?|times)\b))'
    r'|\{\w*[vV]ersion\w*\}',
  ),
  'status code': RegExp(
    r'\b(?:HTTP|status|code|error)\s*[1-5]\d{2}\b',
    caseSensitive: false,
  ),
};

/// Every COPY-11 hit in [value], one entry per match, so a second hit of
/// the same kind raises the count. "Gas City" is a product name (COPY-12),
/// so its "City" is not the engine word.
List<String> _technicalIn(String value) {
  final text = value.replaceAll(RegExp(r'Gas City', caseSensitive: false), '');
  return [
    for (final MapEntry(key: name, value: pattern)
        in _technicalPatterns.entries)
      for (final m in pattern.allMatches(text)) '$name "${m.group(0)!.trim()}"',
  ];
}

bool _isTechnicalKey(Map<String, dynamic> english, String key) {
  final description = _descriptionOf(english, key);
  return description.startsWith('Technical:') ||
      description.startsWith('Field example:');
}

// --- G28: the glossary extension -------------------------------------------

/// Nouns the glossary replaces (COPY-6), matched as whole words. A word
/// right after `/`, `-` or `` ` `` is a slash command, flag or identifier.
final _glossaryNouns = <String, RegExp>{
  for (final noun in [
    'workspace',
    'workspaces',
    'location',
    'locations',
    'profile',
    'profiles',
    'activity',
    'activities',
    'attention',
    'host',
    'hosts',
    'Termux setup',
    'On-device setup',
    'Local server',
    'Local servers',
  ])
    noun: RegExp(
      '(?<![/`\\-\\w])${RegExp.escape(noun)}\\b',
      caseSensitive: false,
    ),
};

/// Nouns checked in sentences too (the label rule above is absolute).
final _conversationNouns = <String, RegExp>{
  for (final noun in _bannedNouns)
    noun: RegExp('(?<![/`\\-\\w])$noun\\b', caseSensitive: false),
};

final _productNames = RegExp(r'\b(OpenCode|Codex|Paseo|Gas City)\b');

/// Keys that may carry a backend or product name in a short label (COPY-12),
/// each with its reason.
const _allowedProductKeys = <String>{
  // Choices between backends: the name is what the person picks.
  'addServerTypeCodex',
  'addServerTypeOpenCode',
  'firstRunAgentCodex',
  'firstRunAgentOpenCode',
  'builtinServerChooseRuntime',
  'setupRuntimeOne',
  'setupRuntimeTwo',
  'quotaCodex',
  // A capability that needs one backend (KitCapabilityExplainer): the name
  // is what the person has to connect, switch to or run, and where the
  // part is available ("Available on computers with OpenCode 2").
  'kitCapServerOc1Title',
  'kitCapServerOc2Title',
  'kitCapServerOc2Enable',
  'kitCapServerCodexTitle',
  'kitCapServerCodexEnable',
  'kitCapServerPaseoEnable',
  'kitHostOpenCode',
  'kitHostOpenCode1',
  'kitHostOpenCode2',
  'kitHostCodex',
  'kitHostPaseo',
  // This phone lists OpenCode beside the other things it runs (Paseo, the
  // optional tools such as Claude Code); the name says which one the
  // action acts on.
  'thisPhoneSetUp',
  'thisPhoneUpdate',
  'thisPhoneRemove',
  'phoneServerCardStartOpenCode',
  // The app's own name, not a backend label.
  'appTitle',
  'aboutBuildVersion',
};

/// Values no button may be (COPY-8).
const _bareVerbs = {'OK', 'Ok', 'Yes', 'No', 'Continue', 'Confirm', 'Submit'};

final _allCaps = RegExp(r'^[A-Z\s]{4,}$');

final _modes = RegExp(
  r'\b(expert|simple|advanced|basic) mode\b',
  caseSensitive: false,
);

/// Words a sentence-case title may capitalise after its first word: names
/// of products, places and languages. All-capital acronyms (MCP, URL, QR)
/// are always allowed.
const _properNouns = <String>{
  'OpenCode',
  'Codex',
  'Paseo',
  'Gas',
  'City',
  'Claude',
  'Pi',
  'Termux',
  'Android',
  'Tailscale',
  'GitHub',
  'Git',
  'Ubuntu',
  'Linux',
  'Node',
  'Python',
  'Arabic',
  'English',
  'Google',
  'Wi-Fi',
  'Shorebird',
  'Markdown',
  'JSON',
  'Anthropic',
  'OpenAI',
  'Gemini',
};

/// Names of more than one word, read as one proper noun.
const _properPhrases = <String>[
  'AI Team',
  'Claude Code',
  'OpenCode Mobile',
  'Gas City',
  'Gas Town',
];

/// Tokens after which a new phrase may start with a capital.
final _phraseBreak = RegExp(r'[.!?:·—–|]$');

/// The first word of [variant] after its first that breaks sentence case.
String? _titleCaseWord(String variant) {
  var text = variant;
  for (final phrase in _properPhrases) {
    text = text.replaceAll(phrase, 'OpenCode');
  }
  final tokens = text.split(RegExp(r'\s+'));
  var first = true;
  var afterBreak = false;
  for (final token in tokens) {
    if (token.isEmpty) continue;
    final word = token.replaceAll(
      RegExp(r'''^[("'“‘«]+|[)"'”’»?!.,:;…]+$'''),
      '',
    );
    final isWord = RegExp(r'\p{L}', unicode: true).hasMatch(word);
    if (!isWord || word.contains(_slot)) {
      afterBreak = _phraseBreak.hasMatch(token) || token == '·';
      if (isWord) first = false;
      continue;
    }
    if (!first &&
        !afterBreak &&
        RegExp(r'^\p{Lu}', unicode: true).hasMatch(word) &&
        !_properNouns.contains(word) &&
        !(word.length > 1 && word == word.toUpperCase())) {
      return word;
    }
    first = false;
    afterBreak = _phraseBreak.hasMatch(token);
  }
  return null;
}

/// The fixed words of a title [variant], each of [_properPhrases] counted
/// as one word.
int _titleWords(String variant) {
  var text = variant;
  for (final phrase in _properPhrases) {
    text = text.replaceAll(phrase, _slot);
  }
  return _wordsOf(text).length;
}

int _sentencesIn(String variant) => variant
    .split(RegExp(r'(?<=[.!?])\s+'))
    .where((s) => s.trim().isNotEmpty)
    .length;

/// Every G28 pattern [key]'s English [value] breaks.
List<String> _g28Problems(String key, String value) {
  final problems = <String>{};
  final variants = _variants(value);
  final labels = variants.where(_isLabelVariant).toList();
  for (final label in labels) {
    // COPY-7: "Try again" recovers; "Stop" ends running work.
    if (RegExp(r'^Reload\b').hasMatch(label)) problems.add('verb Reload');
    if (RegExp(
      r'^Cancel\s+(run|task|job|work|install|download)\b',
      caseSensitive: false,
    ).hasMatch(label)) {
      problems.add('verb Cancel running work');
    }
    // COPY-7: "Disconnect" leaves a server.
    if (RegExp(
          r'server|profile|host|connection',
          caseSensitive: false,
        ).hasMatch(key) &&
        RegExp(r'^(Sign out|Close)\b').hasMatch(label)) {
      problems.add('verb Sign out/Close on a server');
    }
    // COPY-12: product names only where the person chooses between them.
    if (_allowedProductKeys.contains(key)) continue;
    if (_productNames.firstMatch(label) case final m?) {
      problems.add('product name ${m.group(1)}');
    }
  }
  // COPY-8.
  if (_bareVerbs.contains(value.trim())) problems.add('bare ${value.trim()}');
  // LOOK-15.
  if (_allCaps.hasMatch(value)) problems.add('uppercase');
  // COPY-21.
  if (_modes.hasMatch(value)) problems.add('mode');
  // COPY-6: the glossary nouns, in labels and sentences alike. Search
  // keywords (`…Aliases`) are never shown: they keep the retired words so a
  // person who types "workspace" or "session" still finds the row.
  final shown = !key.endsWith('Aliases');
  for (final MapEntry(key: noun, value: pattern) in _glossaryNouns.entries) {
    if (!shown) break;
    if (noun.startsWith('host') && key.startsWith('teamUi')) continue;
    if (variants.any(pattern.hasMatch)) {
      final kind = labels.any(pattern.hasMatch) ? 'label' : 'sentence';
      problems.add('noun $kind ${noun.toLowerCase()}');
    }
  }
  // COPY-6: session and chat outside the labels the absolute test above
  // checks. That test uses [_isShortActionLabel], so a label ending in "…"
  // ("New session…") is counted here rather than slipping between the two.
  if (shown && !_allowedNounKeys.contains(key)) {
    for (final MapEntry(key: noun, value: pattern)
        in _conversationNouns.entries) {
      if (variants.any((v) => !_isShortActionLabel(v) && pattern.hasMatch(v))) {
        problems.add('noun sentence $noun');
      }
    }
  }
  // COPY-10: a title's fixed words. A name of more than one word ("AI
  // Team", "Claude Code") is one word, as a placeholder is.
  if (key.endsWith('Title')) {
    if (variants.any((v) => _titleWords(v) > _maxLabelWords)) {
      problems.add('title over four words');
    }
    for (final v in variants) {
      if (_titleCaseWord(v) case final word?) {
        problems.add('title capital "$word"');
        break;
      }
    }
  }
  if (key.endsWith('Body') && variants.any((v) => _sentencesIn(v) > 2)) {
    problems.add('body over two sentences');
  }
  return problems.toList()..sort();
}

// --- Ratchet mechanics -----------------------------------------------------

typedef _GateCounts = Map<String, Map<String, int>>;

void _count(_GateCounts gate, String subject, String pattern) {
  final patterns = gate.putIfAbsent(subject, () => {});
  patterns[pattern] = (patterns[pattern] ?? 0) + 1;
}

Map<String, dynamic> _sortedGates(Map<String, _GateCounts> gates) => {
  for (final g in gates.keys.toList()..sort())
    g: {
      for (final s in gates[g]!.keys.toList()..sort())
        if (gates[g]![s]!.isNotEmpty)
          s: {
            for (final p in gates[g]![s]!.keys.toList()..sort())
              p: gates[g]![s]![p],
          },
    },
};

/// Problems where [current] gains a subject or pattern, or a count rises,
/// against [baseline] for one gate.
List<String> _ratchetProblems(
  String gate,
  _GateCounts current,
  Map<String, dynamic> baseline,
  String advice,
) {
  final problems = <String>[];
  for (final MapEntry(key: subject, value: patterns) in current.entries) {
    final base = baseline[subject] as Map<String, dynamic>? ?? const {};
    for (final MapEntry(key: pattern, value: count) in patterns.entries) {
      final baseCount = (base[pattern] as num?)?.toInt();
      if (baseCount == null) {
        problems.add('$gate: $subject: $pattern (new) — $advice');
      } else if (count > baseCount) {
        problems.add(
          '$gate: $subject: $pattern rose from $baseCount to $count — $advice',
        );
      }
    }
  }
  return problems;
}

void _copyGates() {
  final english = _arbFile('lib/l10n/app_en.arb');
  final arabic = _arbFile('lib/l10n/app_ar.arb');
  final en = _valuesOf(english);
  final ar = _valuesOf(arabic);

  final confirm = <String, Map<String, int>>{};
  for (final site in _confirmSites()) {
    for (final problem in _confirmProblems(site, en, ar)) {
      _count(confirm, site.file, problem);
    }
  }

  final technical = <String, Map<String, int>>{};
  for (final (locale, values) in [('en', en), ('ar', ar)]) {
    for (final MapEntry(key: key, value: value) in values.entries) {
      if (_isTechnicalKey(english, key)) continue;
      for (final hit in _technicalIn(value)) {
        _count(technical, key, '$locale $hit');
      }
    }
  }

  final glossary = <String, Map<String, int>>{};
  for (final MapEntry(key: key, value: value) in en.entries) {
    for (final problem in _g28Problems(key, value)) {
      _count(glossary, key, problem);
    }
  }

  final current = <String, _GateCounts>{
    'G11 confirm': confirm,
    'G11 technical': technical,
    'G28': glossary,
  };
  final baselineFile = File(_baselinePath);
  final writeMode = Platform.environment['UI_GLOSSARY_WRITE'] == '1';
  if (writeMode) {
    const encoder = JsonEncoder.withIndent('  ');
    baselineFile.writeAsStringSync(
      '${encoder.convert(_sortedGates(current))}\n',
    );
  }
  final baseline = baselineFile.existsSync()
      ? jsonDecode(baselineFile.readAsStringSync()) as Map<String, dynamic>
      : <String, dynamic>{};

  void ratchet(String gate, String advice) {
    final problems = _ratchetProblems(
      gate,
      current[gate]!,
      baseline[gate] as Map<String, dynamic>? ?? const {},
      advice,
    );
    expect(problems, isEmpty, reason: problems.join('\n'));
    // Counts dropped or subjects went away: print this gate's smaller
    // section to commit, so the numbers only ever go down.
    final base = _sortedGates({
      gate: {
        for (final MapEntry(key: s, value: p)
            in (baseline[gate] as Map<String, dynamic>? ?? const {}).entries)
          s: {
            for (final MapEntry(key: k, value: v)
                in (p as Map<String, dynamic>).entries)
              k: (v as num).toInt(),
          },
      },
    });
    final now = _sortedGates({gate: current[gate]!});
    if (jsonEncode(now) != jsonEncode(base)) {
      const encoder = JsonEncoder.withIndent('  ');
      stdout.writeln(
        '--- ui_glossary baseline for "$gate" shrank: commit this section '
        'in $_baselinePath (or run with UI_GLOSSARY_WRITE=1) ---\n'
        '${encoder.convert(now)}\n--- end baseline ---',
      );
    }
  }

  group('G11', () {
    test('ICU messages expand to their plain wordings', () {
      expect(_variants('Stop {name}?'), ['Stop $_slot?']);
      expect(
        _variants(
          '{count, plural, =1{Stop 1 process?} other{Stop # processes?}}',
        ),
        ['Stop 1 process?', 'Stop $_slot processes?'],
      );
      expect(_wordsOf('Delete $_slot · now'), ['Delete', _slot, 'now']);
    });

    test('confirm titles and labels are checked through wrappers', () {
      const src = '''
Future<bool> _ask(String title, {required String label}) =>
    showConfirmSheet(context, title: title, message: m, confirmLabel: label);
void f() {
  // showKitConfirm(context, title: 'in a comment');
  _ask(l10n.aTitle, label: l10n.aLabel);
}
''';
      final blanked = _blankComments(src);
      expect(blanked.contains('in a comment'), isFalse);
      final wrapper = _enclosingWrapper(
        blanked,
        blanked.indexOf('showConfirmSheet'),
        'title',
      );
      expect(wrapper?.name, '_ask');
      expect(wrapper?.slot.index, 0);
      expect(
        _enclosingWrapper(
          blanked,
          blanked.indexOf('showConfirmSheet'),
          'label',
        )?.slot.name,
        'label',
      );
      final english = {'aTitle': 'Remove server', 'aLabel': 'Remove'};
      final arabic = {'aTitle': 'إزالة الخادم'};
      expect(
        _confirmProblems(
          (file: 'f', role: 'title', expression: 'l10n.aTitle'),
          english,
          arabic,
        ),
        [
          'title aTitle: English does not end with "?"',
          'title aTitle: Arabic does not end with "؟"',
        ],
      );
      expect(
        _confirmProblems(
          (file: 'f', role: 'label', expression: 'l10n.aLabel'),
          english,
          arabic,
        ),
        ['label aLabel: fewer than two words'],
      );
      // Only the outermost key is the title or label: a key passed as a
      // placeholder argument is a value, not the words being checked.
      final placeholders = {
        'aTitle': 'Remove {name}?',
        'thisServer': 'this server',
        'deleteThing': 'Delete {thing}',
        'conversation': 'Conversation',
        'other': 'Stop it?',
      };
      expect(_arbKeysIn('l10n.deleteThing(l10n.conversation)', placeholders), [
        'deleteThing',
      ]);
      expect(
        _arbKeysIn(
          'l10n.aTitle(controller.profile?.name ?? l10n.thisServer)',
          placeholders,
        ),
        ['aTitle'],
      );
      expect(
        _arbKeysIn(
          'running ? lookupAppLocalizations(Localizations.localeOf(c)).other '
          ': (a ? l10n.aTitle : name ?? copy.deleteThing(l10n.conversation))',
          placeholders,
        ),
        ['other', 'aTitle', 'deleteThing'],
      );
      expect(_arbKeysIn("'\${l10n.aTitle} now'", placeholders), isEmpty);
      expect(
        _confirmProblems(
          (
            file: 'f',
            role: 'label',
            expression: 'l10n.deleteThing(l10n.conversation)',
          ),
          placeholders,
          const {},
        ),
        isEmpty,
      );
      expect(
        _confirmProblems(
          (
            file: 'f',
            role: 'title',
            expression: 'l10n.aTitle(p?.name ?? l10n.thisServer)',
          ),
          placeholders,
          const {},
        ),
        isEmpty,
      );
      expect(
        _confirmProblems(
          (file: 'f', role: 'label', expression: "'Delete'"),
          english,
          arabic,
        ),
        ['label not from app_en.arb'],
      );
    });

    test('confirmation titles ask and labels name the act (COPY-9)', () {
      ratchet(
        'G11 confirm',
        'a confirm title is a question ending in "?" (Arabic "؟") and '
            'its label names the act and the thing in two or more words '
            '(STANDARDS.md COPY-9)',
      );
    });

    test('the contradiction rule finds both halves of a pair', () {
      expect(_contradictionIn('Working · stopped'), ('working', 'stopped'));
      expect(_contradictionIn('Paused, idle'), ('paused', 'idle'));
      expect(_contradictionIn('Connected — reconnecting…'), (
        'connected',
        'reconnecting',
      ));
      expect(_contradictionIn('Working · 3 of 5 done'), isNull);
      expect(_contradictionIn('Stopped'), isNull);
    });

    test('the contradiction scan skips text that is never rendered', () {
      const src = r'''
testWidgets('KitTaskMark working → stopped', (tester) async {
  await kitGalleryShot(
    tester,
    name: 'kit_task_mark_working_stopped',
    open: (c) => KitTaskMark(key: const ValueKey('working-stopped')),
  );
  await kitGalleryShot(tester, name: 'b', open: (c) => Text('Working'));
  await kitGalleryShot(tester, name: 'c', open: (c) => Text('Stopped'));
  await expectLater(f, matchesGoldenFile('goldens/working_stopped.png'));
});
group('working and stopped', () {});
final a = CensusShot('agent-working', state: 'stopped', (kit) async {});
final b = CensusShot('agent', note: 'working, then stopped', (kit) async {});
''';
      final quiet = _sceneTexts('f', src).entries
          .where((e) => _contradictionIn(e.value) != null)
          .map((e) => '${e.key}: ${e.value}');
      expect(quiet, isEmpty);
      const shown = r'''
final c = CensusShot('agent', (kit) async {
  kit.show(Text('Working'), Text('stopped'));
});
testWidgets('plain', (tester) async {
  await tester.pumpWidget(Column(children: [Text('Paused'), Text('Idle')]));
});
''';
      expect(
        _sceneTexts('f', shown).entries
            .where((e) => _contradictionIn(e.value) != null)
            .map((e) => e.key.split(' ')[1]),
        ['CensusShot', 'testWidgets'],
      );
    });

    test('no text says two contradicting states at once (COPY-17)', () {
      final offenders = <String>[];
      for (final MapEntry(key: key, value: value) in en.entries) {
        if (_allowedContradictionKeys.contains(key)) continue;
        if (_contradictionIn(value) case (final a, final b)?) {
          offenders.add('app_en.arb $key: "$value" says $a and $b');
        }
      }
      for (final MapEntry(key: where, value: text) in _fixtureTexts().entries) {
        if (_contradictionIn(text) case (final a, final b)?) {
          offenders.add('$where says $a and $b');
        }
      }
      expect(
        offenders,
        isEmpty,
        reason:
            'Words never contradict the state: no Working with stopped, '
            'Paused with idle or Connected with reconnecting '
            '(STANDARDS.md COPY-17).\n${offenders.join('\n')}',
      );
    });

    test('every allow-listed contradiction key still needs it', () {
      for (final key in _allowedContradictionKeys) {
        expect(en[key], isNotNull, reason: '$key is allow-listed but gone');
        expect(
          _contradictionIn(en[key]!),
          isNotNull,
          reason: '$key no longer names both states; drop it from the list',
        );
      }
    });

    test('the technical-word rule finds engine words, ports, paths and '
        'versions', () {
      expect(_technicalIn('Batch · convoy'), ['engine word "convoy"']);
      expect(_technicalIn('Gas City on this phone'), isEmpty);
      expect(_technicalIn('Open 127.0.0.1:4096'), [
        'address "127.0.0.1"',
        'port ":4096"',
      ]);
      expect(_technicalIn('Blocks 100.64.0.0/10 and 10.0.0.1'), [
        'address "100.64.0.0/10"',
        'address "10.0.0.1"',
      ]);
      expect(_technicalIn('Read docs/ai-team-host.md first'), [
        'path "docs/ai-team-host.md"',
      ]);
      expect(_technicalIn('Send and/or save'), isEmpty);
      expect(_technicalIn('A2A 1.0 · JSON-RPC'), ['version "A2A 1.0"']);
      expect(_technicalIn('Ubuntu Base 24.04'), ['version "Base 24.04"']);
      expect(_technicalIn('Needs version 2.1 or v3.0'), [
        'version "version 2.1"',
        'version "v3.0"',
      ]);
      expect(_technicalIn('Gas City 1.4.1, then 1.4.1 again'), [
        'version "1.4.1"',
        'version "1.4.1"',
      ]);
      expect(_technicalIn('Takes 2.5 minutes'), isEmpty);
      expect(_technicalIn('Free 1.5 GB'), isEmpty);
      expect(_technicalIn('Saved in ~/.config/opencode'), [
        'path "~/.config/opencode"',
      ]);
      expect(_technicalIn('Files in /data/local/tmp'), [
        'path "/data/local/tmp"',
      ]);
      expect(_technicalIn('Type /sessions to list them'), isEmpty);
      expect(_technicalIn('Update to 1.4.2'), ['version "1.4.2"']);
      expect(_technicalIn('Update to {version}'), ['version "{version}"']);
      expect(_technicalIn('Server said HTTP 401'), ['status code "HTTP 401"']);
      expect(_technicalIn('Working · 3 of 5 steps done'), isEmpty);
      expect(_technicalIn('Starts at 10:30'), isEmpty);
    });

    test('technical words only in keys described as Technical: or Field '
        'example: (COPY-11)', () {
      ratchet(
        'G11 technical',
        'paths, ports, ids, versions and engine words go inside a Details '
            'fold, log, code block, technical value or typed field, and the '
            'key\'s @description starts with "Technical:" or "Field '
            'example:" (STANDARDS.md COPY-11)',
      );
    });
  });

  group('G28', () {
    test('the glossary extension finds each pattern', () {
      expect(_g28Problems('xReload', 'Reload'), ['verb Reload']);
      expect(_g28Problems('xCancel', 'Cancel install'), [
        'verb Cancel running work',
      ]);
      expect(_g28Problems('serverLeave', 'Sign out'), [
        'verb Sign out/Close on a server',
      ]);
      expect(_g28Problems('xOpen', 'Open workspace'), ['noun label workspace']);
      expect(_g28Problems('xHint', 'Pick a workspace to start from.'), [
        'noun sentence workspace',
      ]);
      expect(_g28Problems('teamUiHostName', 'Host'), isEmpty);
      expect(_g28Problems('xAliases', 'Pick a workspace or host.'), isEmpty);
      expect(_g28Problems('xHint', 'Pick a workspace or host.'), [
        'noun sentence host',
        'noun sentence workspace',
      ]);
      expect(_g28Problems('xSetup', 'Termux setup'), [
        'noun label termux setup',
      ]);
      expect(_g28Problems('xCmd', 'Type /sessions to see them.'), isEmpty);
      expect(_g28Problems('xHint', 'Your chats stay here.'), [
        'noun sentence chats',
      ]);
      expect(_g28Problems('xAsk', 'Ask Codex'), ['product name Codex']);
      expect(_g28Problems('xAsk', 'Ask Codex…'), ['product name Codex']);
      expect(_g28Problems('xReload', 'Reload…'), ['verb Reload']);
      expect(_g28Problems('xField', 'OpenCode server:'), [
        'product name OpenCode',
      ]);
      expect(_g28Problems('xNew', 'New session…'), ['noun sentence session']);
      expect(_isLabelVariant('Starting OpenCode…'), isTrue);
      expect(_isLabelVariant('Saved. Open it…'), isFalse);
      expect(_g28Problems('xOk', 'OK'), ['bare OK']);
      expect(_g28Problems('xHeader', 'RECENT'), ['uppercase']);
      expect(_g28Problems('xHint', 'Turn on expert mode.'), ['mode']);
      expect(_g28Problems('xTitle', 'Remove this server from here now?'), [
        'title over four words',
      ]);
      expect(_g28Problems('xTitle', 'Remove {name}?'), isEmpty);
      expect(_g28Problems('xTitle', 'Connect to Tailscale'), isEmpty);
      expect(_g28Problems('xTitle', 'Turn off AI Team?'), isEmpty);
      expect(_g28Problems('xTitle', 'AI Team on this phone'), isEmpty);
      expect(_g28Problems('xTitle', 'Claude Code on this phone now'), [
        'title over four words',
      ]);
      expect(_g28Problems('addServerTypeCodex', 'Codex'), isEmpty);
      expect(_g28Problems('xTitle', 'Open MCP servers'), isEmpty);
      expect(_g28Problems('xTitle', 'Server Settings'), [
        'title capital "Settings"',
      ]);
      expect(_g28Problems('xBody', 'One. Two. Three.'), [
        'body over two sentences',
      ]);
      expect(_g28Problems('xBody', 'One. Two.'), isEmpty);
    });

    test('every allow-listed product key still needs it', () {
      for (final key in _allowedProductKeys) {
        expect(en[key], isNotNull, reason: '$key is allow-listed but gone');
        expect(
          _variants(
            en[key]!,
          ).any((v) => _isLabelVariant(v) && _productNames.hasMatch(v)),
          isTrue,
          reason: '$key no longer names a product in a label; drop it',
        );
      }
    });

    test('labels and copy follow the glossary (COPY-6–8, COPY-10, COPY-12, '
        'COPY-21, LOOK-15)', () {
      ratchet(
        'G28',
        'use the glossary words: Conversation, Project, Server, Inbox, "On '
            'this phone"; Try again, Delete, Remove, Stop, Disconnect; verbs '
            'that say what happens; titles of four sentence-case words; '
            'bodies of two sentences (STANDARDS.md §8.2, LOOK-15)',
      );
    });
  });
}
