// Builds test/kit_ratchet_flutter_widgets.json: the set of Flutter framework
// widget classes (from the pinned Flutter SDK's material/widgets/cupertino
// sources) and each one's public named constructors, for the G16 kit-only
// allowlist gate (docs/ux-system/kit-v2.md §7 G16, "no UI component to be
// used should remain outside our kit").
//
// Usage: dart run tool/kit/flutter_widget_names.dart [flutterRoot]
// flutterRoot defaults to the AGENTS.md-pinned Shorebird Flutter cache dir.
// Re-run this whenever the pinned Flutter revision changes.
//
// Method: scan every .dart file under
// <flutterRoot>/packages/flutter/lib/src/{material,widgets,cupertino} for
// public top-level class declarations, record each class's `extends`
// superclass, then compute the transitive closure of classes whose
// superclass chain reaches `Widget` (seed). For every class in that closure,
// collect its public named constructors (`X.name(`, including `const` and
// `factory`, but not static methods, which never repeat the class name in
// their declaration).
import 'dart:convert';
import 'dart:io';

const _defaultFlutterRoot = '91f8bd75076e9c740aa13cf67eb9ec1a093f68f5';

String _expandUser(String path) {
  if (!path.startsWith('~')) return path;
  final home = Platform.environment['HOME'];
  if (home == null) return path;
  return path.replaceFirst('~', home);
}

/// Drops full-line `//` (and `///`) comments, then strips any remaining
/// `/* ... */` block comments (non-nested; the Flutter SDK barely uses them
/// and never inside a class header or constructor signature).
String _stripComments(String source) {
  final withoutLineComments = source
      .split('\n')
      .map((line) => line.trimLeft().startsWith('//') ? '' : line)
      .join('\n');
  final buffer = StringBuffer();
  var i = 0;
  while (i < withoutLineComments.length) {
    final start = withoutLineComments.indexOf('/*', i);
    if (start < 0) {
      buffer.write(withoutLineComments.substring(i));
      break;
    }
    buffer.write(withoutLineComments.substring(i, start));
    final end = withoutLineComments.indexOf('*/', start + 2);
    if (end < 0) {
      i = withoutLineComments.length;
      break;
    }
    // Preserve line breaks inside the removed block so later line/column
    // reasoning (not used here, but kept for safety) stays sane.
    buffer.write(
      withoutLineComments
          .substring(start, end + 2)
          .split('\n')
          .map((_) => '')
          .join('\n'),
    );
    i = end + 2;
  }
  return buffer.toString();
}

class _ClassInfo {
  _ClassInfo(this.name, this.superclass);

  final String name;
  final String? superclass;
  final Set<String> namedConstructors = <String>{};
}

/// Finds the index just past the matching '>' for a generic parameter list
/// that starts at [openIndex] (which must point at '<'). Returns -1 if no
/// generics start there.
int _skipGenerics(String text, int openIndex) {
  if (openIndex >= text.length || text[openIndex] != '<') return openIndex;
  var depth = 0;
  var i = openIndex;
  while (i < text.length) {
    final c = text[i];
    if (c == '<') depth++;
    if (c == '>') {
      depth--;
      if (depth == 0) return i + 1;
    }
    i++;
  }
  return text.length;
}

int _skipWhitespace(String text, int i) {
  while (i < text.length && text[i].trim().isEmpty) {
    i++;
  }
  return i;
}

/// Naive brace matcher: Dart string interpolation braces are always
/// self-balanced, so counting every '{' and '}' from [openIndex] (must be
/// '{') finds the real matching close for a class body reliably enough for
/// this seed generator.
int _matchBrace(String text, int openIndex) {
  var depth = 0;
  var i = openIndex;
  while (i < text.length) {
    final c = text[i];
    if (c == '{') depth++;
    if (c == '}') {
      depth--;
      if (depth == 0) return i;
    }
    i++;
  }
  return text.length - 1;
}

final _classDeclRe = RegExp(r'\bclass\s+([A-Za-z_]\w*)');
final _extendsRe = RegExp(r'\bextends\s+([A-Za-z_]\w*)');
final _mixinAppEqRe = RegExp(r'=\s*([A-Za-z_]\w*)');

Map<String, _ClassInfo> _parseFile(String source) {
  final classes = <String, _ClassInfo>{};
  final code = _stripComments(source);
  for (final match in _classDeclRe.allMatches(code)) {
    final name = match.group(1)!;
    // Private classes are kept in the graph (a public widget like
    // Directionality can extend a private base like
    // _UbiquitousInheritedWidget) but filtered out of the final widget set
    // below, since app code can never construct them by name.
    var i = match.end;
    i = _skipWhitespace(code, i);
    if (i < code.length && code[i] == '<') {
      i = _skipGenerics(code, i);
    }
    // Header clause runs to the first top-level '{' or ';'.
    final braceIdx = code.indexOf('{', i);
    final semiIdx = code.indexOf(';', i);
    final headerEnd = (semiIdx >= 0 && (braceIdx < 0 || semiIdx < braceIdx))
        ? semiIdx
        : braceIdx;
    if (headerEnd < 0) continue;
    final header = code.substring(i, headerEnd);
    String? superclass;
    final ext = _extendsRe.firstMatch(header);
    if (ext != null) {
      superclass = ext.group(1);
    } else if (semiIdx >= 0 && (braceIdx < 0 || semiIdx < braceIdx)) {
      // Mixin application: `class A = B with C;`
      final eq = _mixinAppEqRe.firstMatch(header);
      superclass = eq?.group(1);
    }
    final info = classes.putIfAbsent(name, () => _ClassInfo(name, superclass));
    if (braceIdx >= 0 && (semiIdx < 0 || braceIdx < semiIdx)) {
      final bodyEnd = _matchBrace(code, braceIdx);
      final body = code.substring(braceIdx, bodyEnd + 1);
      final ctorRe = RegExp(
        '(?<![\\w.])(?:const\\s+)?(?:factory\\s+)?'
        '${RegExp.escape(name)}\\.([A-Za-z_]\\w*)\\s*\\(',
      );
      for (final ctorMatch in ctorRe.allMatches(body)) {
        final ctorName = ctorMatch.group(1)!;
        if (ctorName.startsWith('_')) continue;
        // A real constructor declaration is formatted as its own
        // statement — `const Foo.bar(` / `factory Foo.bar(` / `Foo.bar(`
        // starts the line (modulo indentation) — unlike a call to a
        // same-named static method from inside another member (e.g.
        // `data = MediaQuery.of(context)` in an initializer list, or
        // `Navigator.of(context).pop(...)` as a statement of its own), and
        // unlike the class name appearing inside an error-message string
        // like `'Scaffold.of() called with...'`.
        final lineStart = body.lastIndexOf('\n', ctorMatch.start) + 1;
        final prefix = body.substring(lineStart, ctorMatch.start);
        if (prefix.trim().isNotEmpty) continue;
        // A declaration's parameter list is followed by `:` (initializer
        // list), `{` (body), `;` (no body) or `=>`/`=` (arrow body or a
        // redirecting `= Foo.other`). A call instead chains (`.method(...)`)
        // or is itself an argument, so this rejects `Navigator.of(context)
        // .pop(...)`, which the line-start check alone lets through.
        final openParen = ctorMatch.end - 1;
        var depth = 0;
        var closeParen = -1;
        for (var j = openParen; j < body.length; j++) {
          final c = body[j];
          if (c == '(' || c == '{' || c == '[') depth++;
          if (c == ')' || c == '}' || c == ']') {
            depth--;
            if (depth == 0) {
              closeParen = j;
              break;
            }
          }
        }
        if (closeParen < 0) continue;
        var k = closeParen + 1;
        while (k < body.length && body[k].trim().isEmpty) {
          k++;
        }
        final next = k < body.length ? body[k] : '';
        if (next != ':' && next != '{' && next != ';' && next != '=') {
          continue;
        }
        info.namedConstructors.add(ctorName);
      }
    }
  }
  return classes;
}

void main(List<String> args) {
  final flutterRoot = _expandUser(
    args.isNotEmpty
        ? args[0]
        : '~/.shorebird/bin/cache/flutter/$_defaultFlutterRoot',
  );
  final revision = flutterRoot.split(Platform.pathSeparator).last;

  final dirs = ['material', 'widgets', 'cupertino']
      .map((d) => Directory('$flutterRoot/packages/flutter/lib/src/$d'))
      .where((d) => d.existsSync());

  final allClasses = <String, _ClassInfo>{};
  for (final dir in dirs) {
    final files = dir
        .listSync(recursive: true)
        .whereType<File>()
        .where((f) => f.path.endsWith('.dart'));
    for (final file in files) {
      final parsed = _parseFile(file.readAsStringSync());
      for (final entry in parsed.entries) {
        final existing = allClasses[entry.key];
        if (existing == null) {
          allClasses[entry.key] = entry.value;
        } else {
          existing.namedConstructors.addAll(entry.value.namedConstructors);
          // Keep the first-seen superclass; classes are not expected to be
          // declared twice with different superclasses in the SDK.
        }
      }
    }
  }

  // Transitive closure of classes whose `extends` chain reaches Widget.
  bool reachesWidget(String name, Set<String> seen) {
    if (name == 'Widget') return true;
    if (!seen.add(name)) return false; // cycle guard
    final info = allClasses[name];
    if (info == null || info.superclass == null) return false;
    return reachesWidget(info.superclass!, seen);
  }

  final widgetNames = <String>{};
  for (final name in allClasses.keys) {
    if (name.startsWith('_')) continue; // not constructible by app code
    if (reachesWidget(name, <String>{})) widgetNames.add(name);
  }

  final widgets = <String, List<String>>{};
  for (final name in widgetNames) {
    final ctors = allClasses[name]!.namedConstructors.toList()..sort();
    widgets[name] = ctors;
  }

  final sortedKeys = widgets.keys.toList()..sort();
  final orderedWidgets = <String, List<String>>{
    for (final key in sortedKeys) key: widgets[key]!,
  };

  final out = <String, dynamic>{
    'flutterRevision': revision,
    'widgets': orderedWidgets,
  };

  const encoder = JsonEncoder.withIndent('  ');
  final outFile = File('test/kit_ratchet_flutter_widgets.json');
  outFile.writeAsStringSync('${encoder.convert(out)}\n');
  stdout.writeln(
    'Wrote ${outFile.path}: ${orderedWidgets.length} widget names '
    '(flutterRevision=$revision)',
  );
}
