// Half-written Markdown at the end of a reply that is still streaming.
//
// `**bol`, `` `cod ``, `~~stri`, `[label](htt` and a table row cut short are
// closed or hidden before the tail block parses, so raw marks never show:
// the words arrive already bold, in code, or as a link's label. Only the
// last line is read (inline marks never span lines in the renderer), so the
// cost is the tail's and the blocks before it parse exactly as before. The
// drawn words are the same ones the finished syntax draws, so the word fade
// (`_KitMdFresh`) never sees them grow or shrink when the syntax completes.
//
// Fences keep their own rule (an open fence stays unhighlighted) and are
// never touched. A reply that is no longer streaming is never passed here:
// its exact text is drawn, so one that truly ends with a lone `*` keeps it.

final _ukFence = RegExp(r'^ {0,3}(`{3,}|~{3,})(.*)$');
final _ukPrefix = RegExp(r'^\s*(?:(?:#{1,6}|>|[-*+]|\d{1,9}[.)])\s+|>)*');

/// [source] with the unfinished inline syntax of its last line closed or
/// hidden. Equal to [source] when nothing is unfinished.
String kitMarkdownCloseTail(String source) {
  final cut = source.lastIndexOf('\n') + 1;
  final last = source.substring(cut);
  if (last.trim().isEmpty) return source;
  if (_inOpenFence(source.substring(0, cut), last)) return source;
  final lines = source.substring(0, cut).split('\n');
  // A table row with one cell is a paragraph to the parser: hide it until
  // its second cell starts.
  if (last.trimLeft().startsWith('|') && lines.length >= 2) {
    final before = lines[lines.length - 2].trimLeft();
    if (before.startsWith('|') && '|'.allMatches(last).length < 2) {
      return source.substring(0, cut);
    }
  }
  final closed = _closeLine(last);
  return closed == last ? source : source.substring(0, cut) + closed;
}

bool _inOpenFence(String before, String last) {
  if (!before.contains('```') && !before.contains('~~~')) {
    return _ukFence.hasMatch(last);
  }
  String? fence;
  for (final line in before.split('\n')) {
    final m = _ukFence.firstMatch(line);
    if (m == null) continue;
    final marker = m.group(1)!;
    if (fence == null) {
      if (!(marker[0] == '`' && m.group(2)!.contains('`'))) fence = marker;
    } else if (marker[0] == fence[0] &&
        marker.length >= fence.length &&
        m.group(2)!.trim().isEmpty) {
      fence = null;
    }
  }
  return fence != null || _ukFence.hasMatch(last);
}

String _closeLine(String line) {
  if (!line.contains(RegExp(r'[*_~`\[]'))) return line;
  var prefix = _ukPrefix.firstMatch(line)!.group(0)!;
  var body = line.substring(prefix.length);
  // A table row: only its last cell is still being written.
  if (line.trimLeft().startsWith('|')) {
    final at = line.lastIndexOf('|') + 1;
    prefix = line.substring(0, at);
    body = line.substring(at);
  }
  return prefix + _closeBody(body);
}

String _closeBody(String body) {
  var text = _closeLink(body);
  final tick = _openTick(text);
  var code = '';
  if (tick >= 0) {
    code = text.substring(tick);
    text = text.substring(0, tick);
    // A lone backtick has no words to show yet.
    code = code.length == 1 ? '' : '$code`';
  }
  final stack = <String>[];
  final cleaned = _closeEmphasis(text, stack);
  // The closers go right after the words, before trailing spaces.
  final out = StringBuffer(cleaned);
  if (code.isNotEmpty) out.write(code);
  if (stack.isNotEmpty) {
    var joined = out.toString();
    final trimmed = joined.trimRight();
    final spaces = joined.substring(trimmed.length);
    joined = trimmed + stack.reversed.join() + spaces;
    return joined;
  }
  return out.toString();
}

/// Where an unmatched single backtick opens a code span, or -1.
int _openTick(String s) {
  var i = 0;
  while (i < s.length) {
    final c = s[i];
    if (c == r'\') {
      i += 2;
      continue;
    }
    if (c != '`') {
      i++;
      continue;
    }
    var run = i;
    while (run < s.length && s[run] == '`') {
      run++;
    }
    if (run - i > 1) {
      i = run;
      continue;
    }
    final close = s.indexOf('`', i + 1);
    if (close < 0) return i;
    i = close + 1;
  }
  return -1;
}

/// [s] with a link being written reduced to its label.
String _closeLink(String s) {
  final at = s.lastIndexOf('[');
  if (at < 0) return s;
  if (at > 0 && (s[at - 1] == r'\' || s[at - 1] == '!')) return s;
  if (_openTick(s.substring(0, at)) >= 0) return s;
  // `a[0` is an index, not a link.
  if (at > 0 && RegExp(r'[A-Za-z0-9_\])]').hasMatch(s[at - 1])) return s;
  final rest = s.substring(at + 1);
  final close = rest.indexOf(']');
  final String label;
  if (close < 0) {
    label = rest;
  } else {
    label = rest.substring(0, close);
    final after = rest.substring(close + 1);
    // Finished, or not a link at all: leave it be.
    if (after.isNotEmpty && !after.startsWith('(')) return s;
    if (after.startsWith('(') &&
        (after.contains(')') || after.contains(RegExp(r'\s')))) {
      return s;
    }
  }
  final t = label.trim();
  // `[ ]` and `[x]` are a task list's box, `[` alone has no label yet.
  if (t.isEmpty || t == 'x' || t == 'X' || int.tryParse(t) != null) {
    return t.isEmpty && close < 0 && s.substring(0, at).trim().isNotEmpty
        ? s.substring(0, at)
        : s;
  }
  return s.substring(0, at) + label;
}

/// [s] without a trailing mark that opens nothing; the marks left open at
/// the end are pushed on [stack] (outermost first).
String _closeEmphasis(String s, List<String> stack) {
  var out = s;
  final openAt = <int>[];
  var i = 0;
  while (i < out.length) {
    final c = out[i];
    if (c == r'\') {
      i += 2;
      continue;
    }
    if (c == '`') {
      final close = out.indexOf('`', i + 1);
      i = close < 0 ? out.length : close + 1;
      continue;
    }
    String? mark;
    if (c == '*') {
      mark = out.startsWith('***', i)
          ? '***'
          : out.startsWith('**', i)
          ? '**'
          : '*';
    } else if (c == '_' && out.startsWith('__', i)) {
      mark = '__';
    } else if (c == '~' && out.startsWith('~~', i)) {
      mark = '~~';
    } else if ((c == '~' || c == '_') && i == out.length - 1) {
      // The first half of `~~` or `__`: a lone one at a word start.
      if (i == 0 || out[i - 1].trim().isEmpty) {
        out = out.substring(0, i);
      }
      break;
    }
    if (mark == null) {
      i++;
      continue;
    }
    final end = i + mark.length;
    final spaceBefore = i == 0 || out[i - 1].trim().isEmpty;
    final spaceAfter = end >= out.length || out[end].trim().isEmpty;
    final top = stack.lastIndexOf(mark);
    if (top >= 0 && !spaceBefore) {
      stack.removeRange(top, stack.length);
      openAt.removeRange(top, openAt.length);
    } else if (!spaceAfter) {
      stack.add(mark);
      openAt.add(i);
    } else if (end >= out.length) {
      // A mark at the very end that closes nothing: nothing to show.
      out = out.substring(0, i);
      break;
    }
    i = end;
  }
  // A mark opened right at the end has no words yet.
  while (stack.isNotEmpty && openAt.last + stack.last.length >= out.length) {
    out = out.substring(0, openAt.last);
    stack.removeLast();
    openAt.removeLast();
  }
  return out;
}
