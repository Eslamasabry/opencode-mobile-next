// The G4 kit manifest, part of kit_manifest_test.dart: the manifest types and
// the source scanner.
part of 'kit_manifest_test.dart';

/// A drawn widget part, a scope widget that draws nothing, or a drawn
/// [KitScene] (painted by KitIllustration; TEST-14).
enum KitManifestKind { part, scope, scene }

/// One public widget class or KitScene: exported by `kit.dart` or declared
/// under `lib/ui/kit/`.
class KitManifestPart {
  const KitManifestPart({
    required this.name,
    required this.file,
    required this.kind,
    required this.exported,
    required this.states,
    required this.statesProblem,
    required this.requiredStates,
    required this.openers,
  });

  final String name;

  /// The file (relative to the package root) that declares the class.
  final String file;
  final KitManifestKind kind;

  /// Whether `kit.dart` exports it; false for a kit file that screens
  /// import by path.
  final bool exported;

  /// The states its doc comment declares (KIT-12); empty for a reasoned
  /// `States: none — …`; null when it declares none or the line is
  /// malformed ([statesProblem] says which).
  final List<String>? states;
  final String? statesProblem;

  /// The states its fields require (KIT-12 second sentence): state -> why.
  final Map<String, String> requiredStates;

  /// The `showKit…` functions declared in the same file: the part is modal.
  final List<String> openers;

  String get snake => kitSnake(name);

  /// The snake of the file that declares it: `kit_image` for KitAvatar.
  String get fileSnake =>
      file.substring(file.lastIndexOf('/') + 1).replaceAll('.dart', '');

  /// Whether it shares [file] with that file's own part because the file
  /// part's frozen spec declares it there (NAME-1 exception, see above).
  bool get coLocated {
    if (fileSnake == snake) return false;
    final spec = File('docs/ux-system/kit-api/${_pascal(fileSnake)}.md');
    return spec.existsSync() &&
        RegExp('\\bclass\\s+$name\\b').hasMatch(spec.readAsStringSync());
  }

  /// The snake its gallery and unit test are named after: its own, or
  /// (co-located, with no files of its own) its file's.
  String get homeSnake =>
      coLocated &&
          !File('test/kit/${snake}_test.dart').existsSync() &&
          !File('test/goldens/kit/${snake}_golden_test.dart').existsSync()
      ? fileSnake
      : snake;
  bool get isModal => openers.isNotEmpty;
  bool get isRow => name.endsWith('Row');

  /// The names a hand-listed consumer test may use for this part.
  List<String> get aliases => [name, ...openers];

  @override
  String toString() => '$name ($file)';
}

/// One top-level `showKit…` function under `lib/ui/kit/` or exported by
/// `kit.dart`.
class KitManifestOpener {
  const KitManifestOpener({
    required this.name,
    required this.file,
    required this.exported,
    required this.returnType,
    required this.parameters,
  });

  final String name;
  final String file;
  final bool exported;

  /// The declared return type, '' when it declares none.
  final String returnType;

  /// The source between the parameter list's parentheses.
  final String parameters;

  /// Whether it declares an optional `Key? …Key` parameter (KIT-10).
  bool get hasOptionalKey => RegExp(
    r'\bKey\?\s+\w*Key\b',
  ).hasMatch(parameters.replaceAll(RegExp(r'required\s+Key\?\s+\w+'), ''));
}

class KitManifest {
  const KitManifest({
    required this.parts,
    required this.openers,
    required this.unresolved,
    required this.problems,
  });

  final List<KitManifestPart> parts;
  final List<KitManifestOpener> openers;

  /// Names in a `show` combinator that no declaration matched.
  final List<String> unresolved;

  /// Declarations the scan found but could not read (fail loudly).
  final List<String> problems;

  Iterable<KitManifestPart> get drawn =>
      parts.where((p) => p.kind == KitManifestKind.part);
}

/// `kit_motion_parts` -> `KitMotionParts`.
String _pascal(String snake) => [
  for (final w in snake.split('_'))
    if (w.isNotEmpty) '${w[0].toUpperCase()}${w.substring(1)}',
].join();

/// `KitConfirmSheet` -> `kit_confirm_sheet`.
String kitSnake(String name) => name
    .replaceAllMapped(RegExp(r'([a-z0-9])([A-Z])'), (m) => '${m[1]}_${m[2]}')
    .replaceAllMapped(RegExp(r'([A-Z])([A-Z][a-z])'), (m) => '${m[1]}_${m[2]}')
    .toLowerCase();

/// A Dart source with its comments blanked ([code]) and, in [shape], its
/// string literals blanked too (quotes of the outermost literal kept). All
/// three keep every offset and newline of the file.
class KitSource {
  KitSource._(this.text, this.code, this.shape);

  factory KitSource.read(String path) =>
      KitSource.parse(File(path).readAsStringSync());

  factory KitSource.parse(String s) {
    final n = s.length;
    final code = s.split('');
    final shape = s.split('');
    void blank(int i, List<String> out) {
      if (s[i] != '\n') out[i] = ' ';
    }

    void hide(int i) {
      if (s[i] != '\n') shape[i] = '_';
    }

    // Frames: a string literal (quote, raw) or an interpolation (depth).
    final quotes = <String?>[];
    final raws = <bool>[];
    final depths = <int>[];
    var i = 0;
    while (i < n) {
      final c = s[i];
      final inString = quotes.isNotEmpty && quotes.last != null;
      if (inString) {
        final quote = quotes.last!;
        if (!raws.last && c == r'\' && i + 1 < n) {
          hide(i);
          hide(i + 1);
          i += 2;
          continue;
        }
        if (!raws.last && s.startsWith(r'${', i)) {
          hide(i);
          hide(i + 1);
          quotes.add(null);
          raws.add(false);
          depths.add(0);
          i += 2;
          continue;
        }
        if (s.startsWith(quote, i)) {
          quotes.removeLast();
          raws.removeLast();
          depths.removeLast();
          if (quotes.isNotEmpty) {
            for (var k = 0; k < quote.length; k++) {
              hide(i + k);
            }
          }
          i += quote.length;
          continue;
        }
        hide(i);
        i++;
        continue;
      }
      final nested = quotes.isNotEmpty; // inside an interpolation
      if (s.startsWith('//', i)) {
        while (i < n && s[i] != '\n') {
          blank(i, code);
          blank(i, shape);
          i++;
        }
        continue;
      }
      if (s.startsWith('/*', i)) {
        var depth = 0;
        do {
          if (s.startsWith('/*', i)) {
            depth++;
            blank(i, code);
            blank(i, shape);
            blank(i + 1, code);
            blank(i + 1, shape);
            i += 2;
          } else if (s.startsWith('*/', i)) {
            depth--;
            blank(i, code);
            blank(i, shape);
            blank(i + 1, code);
            blank(i + 1, shape);
            i += 2;
          } else {
            blank(i, code);
            blank(i, shape);
            i++;
          }
        } while (depth > 0 && i < n);
        continue;
      }
      final rawStart =
          c == 'r' &&
          i + 1 < n &&
          (s[i + 1] == "'" || s[i + 1] == '"') &&
          (i == 0 || !_identChar.hasMatch(s[i - 1]));
      if (c == "'" || c == '"' || rawStart) {
        final start = rawStart ? i + 1 : i;
        final q = s[start];
        final quote = s.startsWith('$q$q$q', start) ? '$q$q$q' : q;
        final end = start + quote.length;
        if (nested) {
          for (var k = i; k < end; k++) {
            hide(k);
          }
        }
        quotes.add(quote);
        raws.add(rawStart);
        depths.add(0);
        i = end;
        continue;
      }
      if (nested) {
        if (c == '{') depths[depths.length - 1]++;
        if (c == '}') {
          if (depths.last == 0) {
            quotes.removeLast();
            raws.removeLast();
            depths.removeLast();
          } else {
            depths[depths.length - 1]--;
          }
        }
        hide(i);
      }
      i++;
    }
    return KitSource._(s, code.join(), shape.join());
  }

  /// The raw text (comments included; the doc table lives in comments).
  final String text;

  /// Comments blanked.
  final String code;

  /// Comments and string literals blanked: brackets here are real.
  final String shape;

  /// The index of the bracket closing the one at [open], or -1.
  int close(int open) {
    const pairs = {'(': ')', '[': ']', '{': '}'};
    final stack = <String>[];
    for (var i = open; i < shape.length; i++) {
      final c = shape[i];
      if (pairs.containsKey(c)) stack.add(pairs[c]!);
      if (pairs.containsValue(c)) {
        if (stack.isEmpty || stack.removeLast() != c) return -1;
        if (stack.isEmpty) return i;
      }
    }
    return -1;
  }

  /// The bracket depth at each offset, and where the top-level declaration
  /// holding it starts (just after the previous top-level `;` or `}`).
  late final (List<int>, List<int>) _structure = () {
    final depthAt = List<int>.filled(shape.length + 1, 0);
    final boundary = List<int>.filled(shape.length + 1, 0);
    var depth = 0;
    var last = 0;
    for (var i = 0; i < shape.length; i++) {
      depthAt[i] = depth;
      boundary[i] = last;
      final c = shape[i];
      if (c == '(' || c == '[' || c == '{') depth++;
      if (c == ')' || c == ']' || c == '}') depth--;
      if (depth == 0 && (c == ';' || c == '}')) last = i + 1;
    }
    depthAt[shape.length] = depth;
    boundary[shape.length] = last;
    return (depthAt, boundary);
  }();

  /// Whether offset [at] is outside every bracket.
  bool topLevel(int at) => _structure.$1[at] == 0;

  /// The code of the top-level declaration before offset [at], with
  /// annotations removed: a function's return type and modifiers.
  String headBefore(int at) => code
      .substring(_structure.$2[at], at)
      .replaceAll(RegExp(r'@[\w.]+(?:\s*\([^()]*\))?'), ' ');

  /// The top-level (depth 0) comma-separated pieces of the code between
  /// [open] and its closing bracket.
  List<String> arguments(int open) {
    final end = close(open);
    if (end < 0) return const [];
    final out = <String>[];
    var depth = 0;
    var from = open + 1;
    for (var i = open + 1; i < end; i++) {
      final c = shape[i];
      if (c == '(' || c == '[' || c == '{') depth++;
      if (c == ')' || c == ']' || c == '}') depth--;
      if (c == ',' && depth == 0) {
        out.add(code.substring(from, i));
        from = i + 1;
      }
    }
    out.add(code.substring(from, end));
    return [
      for (final a in out)
        if (a.trim().isNotEmpty) a.trim(),
    ];
  }
}

final _identChar = RegExp(r'[\w$]');

final _sources = <String, KitSource>{};
KitSource _source(String path) =>
    _sources.putIfAbsent(path, () => KitSource.read(path));

String _normalize(String path) {
  final out = <String>[];
  for (final segment in path.split('/')) {
    if (segment == '..') {
      out.removeLast();
    } else if (segment != '.' && segment.isNotEmpty) {
      out.add(segment);
    }
  }
  return out.join('/');
}

String _resolve(String from, String uri) {
  if (uri.startsWith('package:opencode_mobile/')) {
    return 'lib/${uri.substring('package:opencode_mobile/'.length)}';
  }
  final dir = from.substring(0, from.lastIndexOf('/'));
  return _normalize('$dir/$uri');
}

/// `export`/`part` directives, with either quote style (read from code, so
/// a commented-out directive does not count).
final _directive = RegExp(
  r'''^(export|part)\s+(['"])([^'"]+)\2\s*([^;]*);''',
  multiLine: true,
);

Set<String> _names(String combinator, String keyword) {
  final m = RegExp(
    '\\b$keyword\\s+([\\w\\s,]+?)(?=\\s+(?:show|hide)\\b|\$)',
  ).firstMatch(combinator.trim());
  if (m == null) return {};
  return m[1]!
      .split(',')
      .map((s) => s.trim())
      .where((s) => s.isNotEmpty)
      .toSet();
}

class _Decl {
  _Decl(this.name, this.file, this.line, this.kind, {this.offset = 0});
  final String name;
  final String file;
  final int line;
  final String kind; // class, function, other
  final int offset;
}

final _classHeader = RegExp(
  r'^(?:(?:abstract|base|final|interface|sealed|mixin)\s+)*class\s+(\w+)',
  multiLine: true,
);
final _otherHeader = RegExp(
  r'^(?:enum|mixin|typedef|extension\s+type)\s+(\w+)',
  multiLine: true,
);

/// A name followed by optional type parameters and `(`; filtered to
/// top-level declarations by [_topLevelFunctions].
final _callLike = RegExp(
  r'(?<![\w$.@])([A-Za-z_$][\w$]*)\s*'
  r'(?:<(?:[^<>()]|<(?:[^<>()]|<[^<>()]*>)*>)*>)?\s*\(',
);

int _lineOf(String source, int offset) =>
    '\n'.allMatches(source.substring(0, offset)).length;

/// The top-level function declarations of [source]: name -> (offset of
/// the name, offset of its `(`, the text before the name back to the
/// previous top-level `;` or `}`).
List<(String, int, int, String)> _topLevelFunctions(KitSource source) {
  final out = <(String, int, int, String)>[];
  for (final m in _callLike.allMatches(source.shape)) {
    if (!source.topLevel(m.start)) continue;
    final name = m[1]!;
    if (const {'Function', 'if', 'for', 'while', 'switch'}.contains(name)) {
      continue;
    }
    final head = source.headBefore(m.start);
    if (_notDeclaration(head)) continue;
    out.add((name, m.start, m.end - 1, head.trim()));
  }
  return out;
}

/// Whether the code before a top-level name shows it is not being
/// declared (an initializer, an expression body, a directive).
bool _notDeclaration(String head) =>
    head.contains('=') ||
    RegExp(r'^\s*(?:typedef|import|export|part|library)\b').hasMatch(head);

/// The files of a library: the file and its `part`s.
List<String> _unitsOf(String path) {
  final code = _source(path).code;
  return [
    path,
    for (final m in _directive.allMatches(code))
      if (m[1] == 'part') _resolve(path, m[3]!),
  ];
}

List<_Decl> _declarationsIn(String file) {
  final source = _source(file);
  return [
    for (final m in _classHeader.allMatches(source.shape))
      _Decl(
        m[1]!,
        file,
        _lineOf(source.text, m.start),
        'class',
        offset: m.start,
      ),
    for (final m in _otherHeader.allMatches(source.shape))
      _Decl(m[1]!, file, _lineOf(source.text, m.start), 'other'),
    for (final (name, at, _, _) in _topLevelFunctions(source))
      _Decl(name, file, _lineOf(source.text, at), 'function', offset: at),
  ];
}

/// Every public declaration [path] exports, through re-exports.
void _collectExports(
  String path,
  Set<String>? show,
  Set<String> hide,
  Map<String, _Decl> out,
  Set<String> wanted,
  Set<String> visiting,
) {
  if (!visiting.add('$path|$show|$hide')) return;
  bool visible(String name) =>
      !name.startsWith('_') &&
      (show == null || show.contains(name)) &&
      !hide.contains(name);
  for (final unit in _unitsOf(path)) {
    for (final decl in _declarationsIn(unit)) {
      if (visible(decl.name)) out.putIfAbsent(decl.name, () => decl);
    }
  }
  for (final m in _directive.allMatches(_source(path).code)) {
    if (m[1] != 'export') continue;
    final innerShow = _names(m[4]!, 'show');
    final innerHide = _names(m[4]!, 'hide');
    Set<String>? nextShow = innerShow.isEmpty ? show : innerShow;
    if (show != null && innerShow.isNotEmpty) {
      nextShow = innerShow.intersection(show);
    }
    wanted.addAll(innerShow);
    _collectExports(
      _resolve(path, m[3]!),
      nextShow,
      {...hide, ...innerHide},
      out,
      wanted,
      visiting,
    );
  }
}

/// Every `.dart` file under `lib/ui/kit/`, sorted.
List<String> _kitFiles() => [
  for (final entity in Directory(_kitDirectory).listSync(recursive: true))
    if (entity is File && entity.path.endsWith('.dart'))
      entity.path.replaceAll(r'\', '/'),
]..sort();

/// Superclass of every class declared under lib/.
Map<String, String> _superclasses() {
  final supers = <String, String>{};
  final header = RegExp(
    r'^(?:(?:abstract|base|final|interface|sealed|mixin)\s+)*class\s+(\w+)'
    r'(?:\s*<[^{]*?>)?\s+extends\s+(\w+)',
    multiLine: true,
  );
  for (final entity in Directory('lib').listSync(recursive: true)) {
    if (entity is! File || !entity.path.endsWith('.dart')) continue;
    for (final m in header.allMatches(_source(entity.path).shape)) {
      supers.putIfAbsent(m[1]!, () => m[2]!);
    }
  }
  return supers;
}

Set<String> _frameworkWidgets() {
  final json =
      jsonDecode(
            File('test/kit_ratchet_flutter_widgets.json').readAsStringSync(),
          )
          as Map<String, Object?>;
  return (json['widgets']! as Map<String, Object?>).keys.toSet();
}

/// The doc comment above line [line] of [file] (annotations skipped).
List<String> _docAbove(String file, int line) {
  final lines = _source(file).text.split('\n');
  final doc = <String>[];
  for (var i = line - 1; i >= 0; i--) {
    final text = lines[i].trim();
    if (text.startsWith('@')) continue;
    if (!text.startsWith('///')) break;
    doc.insert(0, text.substring(3).trim());
  }
  return doc;
}

(List<String>?, String?) _statesFrom(List<String> doc) {
  final lines = doc.where((l) => l.startsWith('States:')).toList();
  if (lines.isEmpty) return (null, 'no "States: …" line in its doc comment');
  if (lines.length > 1) return (null, 'more than one "States:" line');
  final body = lines.single.substring('States:'.length).trim();
  final none = RegExp(r'^none\b\s*[—–:;,(-]*\s*(.*)$').firstMatch(body);
  if (none != null) {
    final reason = none[1]!.replaceAll(RegExp(r'[.)]+$'), '').trim();
    if (reason.split(RegExp(r'\s+')).where((w) => w.isNotEmpty).length < 3) {
      return (
        null,
        '"States: none" without a reason; write '
            '"States: none — <why it has no states>."',
      );
    }
    return (const [], null);
  }
  final states = body
      .replaceAll(RegExp(r'\.$'), '')
      .split(',')
      .map((s) => s.trim())
      .toList();
  final unknown = states.where((s) => !kitManifestStates.contains(s));
  if (unknown.isNotEmpty) {
    return (null, 'unknown states ${unknown.join(', ')}');
  }
  return (states, null);
}

/// The fields (`final <Type> <name>;`) of the class body at [offset].
Map<String, String> _fieldsOf(KitSource source, int offset) {
  final open = source.shape.indexOf('{', offset);
  if (open < 0) return const {};
  final end = source.close(open);
  if (end < 0) return const {};
  final inner = KitSource.parse(source.text.substring(open + 1, end));
  return {
    for (final m in RegExp(
      r'\bfinal\s+([^;=]+?)\s+(\w+)\s*;',
    ).allMatches(inner.shape))
      if (inner.topLevel(m.start))
        m[2]!: inner.code
            .substring(m.start, m.end)
            .replaceFirst(RegExp(r'^final\s+'), '')
            .replaceFirst(RegExp(r'\s+\w+\s*;$'), '')
            .replaceAll(RegExp(r'\s+'), ' ')
            .trim(),
  };
}

/// KIT-12's second sentence, read from the part's fields: state -> why.
Map<String, String> _requiredStates(Map<String, String> fields) {
  final out = <String, String>{};
  for (final MapEntry(key: name, value: type) in fields.entries) {
    // An optional close affordance is absent when null, not disabled; an
    // event the part reports (KitSince's onEscalated) is not something the
    // person can do, so null does not disable anything either.
    final closes =
        name == 'onDismiss' || name == 'onClose' || name == 'onEscalated';
    if (RegExp(r'^on[A-Z]').hasMatch(name) && type.endsWith('?') && !closes) {
      out.putIfAbsent('disabled', () => 'nullable callback $name');
    }
    if (RegExp(
          r'^(?:working|busy|sending|submitting|isWorking|isBusy|isSending)$',
        ).hasMatch(name) ||
        RegExp(r'^on(?:Submit|Send)').hasMatch(name)) {
      out.putIfAbsent('working', () => 'sends through $name');
    }
    if (type.contains('Function(')) continue; // a callback, not data
    final data = RegExp(
      r'^(?:Future|Stream|AsyncSnapshot|ValueListenable<(?:List|Iterable))\b',
    ).hasMatch(type);
    final collection = RegExp(
      r'^(?:List|Iterable|Map)<\s*(?:[\w<>?, ]+,\s*)?(\w+)',
    ).firstMatch(type);
    final element = collection?[1];
    final serverList =
        element != null &&
        !_uiElementTypes.contains(element) &&
        !element.startsWith('Kit') &&
        !element.endsWith('Widget');
    if (data || serverList) {
      for (final state in ['loading', 'empty', 'error']) {
        out.putIfAbsent(state, () => 'shows server data $name ($type)');
      }
    }
  }
  return out;
}
