// The G4 kit manifest, part of kit_manifest_test.dart: readKitManifest, the
// gallery scan and kitManifestViolations.
part of 'kit_manifest_test.dart';

/// Reads the kit manifest: `lib/ui/kit/kit.dart`'s exports plus every
/// widget, scene and opener declared under `lib/ui/kit/`.
/// [includeRetired] lets regression gates retain coverage of exported
/// forwarding widgets; G4 itself keeps excluding them per KIT-43.
KitManifest readKitManifest({bool includeRetired = false}) {
  final exportedDecls = <String, _Decl>{};
  final wanted = <String>{};
  _collectExports(_kitLibrary, null, {}, exportedDecls, wanted, {});
  final decls = <String, (_Decl, bool)>{
    for (final d in exportedDecls.values) d.name: (d, true),
  };
  for (final file in _kitFiles()) {
    for (final d in _declarationsIn(file)) {
      if (d.name.startsWith('_')) continue;
      decls.putIfAbsent(d.name, () => (d, false));
    }
  }
  final supers = _superclasses();
  final framework = _frameworkWidgets();
  final problems = <String>[];

  List<String> chain(String name) {
    final out = <String>[name];
    final seen = <String>{name};
    var current = name;
    for (
      var next = supers[current];
      next != null && seen.add(next);
      next = supers[current]
    ) {
      out.add(next);
      current = next;
    }
    return out;
  }

  bool isWidget(List<String> chain) =>
      chain.length > 1 &&
      (chain.skip(1).any(_widgetBases.contains) ||
          framework.contains(chain.last));

  final openers = <KitManifestOpener>[];
  for (final (decl, exported) in decls.values) {
    if (decl.kind != 'function' || !decl.name.startsWith('showKit')) continue;
    final source = _source(decl.file);
    final found = _topLevelFunctions(
      source,
    ).where((f) => f.$1 == decl.name).firstOrNull;
    final end = found == null ? -1 : source.close(found.$3);
    if (found == null || end < 0) {
      problems.add(
        '${decl.file}:${decl.line + 1} ${decl.name}: '
        'cannot read its return type and parameter list',
      );
      continue;
    }
    openers.add(
      KitManifestOpener(
        name: decl.name,
        file: decl.file,
        exported: exported,
        returnType: found.$4.replaceAll(RegExp(r'\s+'), ' '),
        parameters: source.code.substring(found.$3 + 1, end),
      ),
    );
  }
  // Every top-level `showKit…` in a kit file must have been read above.
  for (final file in _kitFiles()) {
    final source = _source(file);
    for (final m in RegExp(
      r'(?<![\w$.@])(showKit\w+)\b',
    ).allMatches(source.shape)) {
      if (!source.topLevel(m.start)) continue;
      if (_notDeclaration(source.headBefore(m.start))) continue;
      if (!openers.any((o) => o.name == m[1] && o.file == file) &&
          !problems.any((p) => p.contains(' ${m[1]}:'))) {
        problems.add(
          '$file:${_lineOf(source.text, m.start) + 1} ${m[1]}: '
          'a top-level showKit… the scan could not read',
        );
      }
    }
  }
  openers.sort((a, b) => a.name.compareTo(b.name));

  final parts = <KitManifestPart>[];
  for (final (decl, exported) in decls.values) {
    if (decl.kind != 'class') continue;
    final supersOf = chain(decl.name);
    final isScene = supersOf.skip(1).contains('KitScene');
    if (!isScene && !isWidget(supersOf)) continue;
    final doc = _docAbove(decl.file, decl.line);
    if (!includeRetired &&
        doc.isNotEmpty &&
        doc.first.startsWith('Retired by kit-')) {
      continue;
    }
    final (states, problem) = _statesFrom(doc);
    parts.add(
      KitManifestPart(
        name: decl.name,
        file: decl.file,
        kind: isScene
            ? KitManifestKind.scene
            : supersOf.any(_scopeBases.contains)
            ? KitManifestKind.scope
            : KitManifestKind.part,
        exported: exported,
        states: states,
        statesProblem: problem,
        requiredStates: _requiredStates(
          Map.of(_fieldsOf(_source(decl.file), decl.offset))..removeWhere(
            (field, _) => _nullNotDisabled.containsKey('${decl.name}.$field'),
          ),
        ),
        openers: [
          for (final o in openers)
            if (o.file == decl.file) o.name,
        ],
      ),
    );
  }
  parts.sort((a, b) => a.name.compareTo(b.name));

  return KitManifest(
    parts: parts,
    openers: openers,
    unresolved: (wanted.difference(exportedDecls.keys.toSet()).toList()
      ..sort()),
    problems: problems,
  );
}

/// One golden a gallery file records.
/// The golden name of a `name:` argument: a string literal, or a
/// `kitGalleryName('<shot>', size, …)` call (G23's naming helper), read as
/// `<shot>[_ar][_text2]_$size_$mode`.
String? _galleryName(String? expression) {
  if (expression == null) return null;
  final literal = _Gallery._literal(expression);
  if (literal != null) return literal;
  final call = RegExp(
    r'''^kitGalleryName\(\s*(['"])((?:[\w$]|\$\{[^}]*\})+)\1([\s\S]*)\)$''',
  ).firstMatch(expression.trim());
  if (call == null) return null;
  final rest = call[3]!;
  return [
    call[2]!,
    if (RegExp(r'\bar:\s*true\b').hasMatch(rest)) 'ar',
    if (RegExp(r'\btext2:\s*true\b').hasMatch(rest)) 'text2',
    r'$size',
    r'$mode',
  ].join('_');
}

class _Shot {
  _Shot(this.name, this.arguments, {this.sizeArgument});

  /// The golden name's literal source, quotes and `.png` removed.
  final String name;

  /// The named arguments of its gallery call ({} for a
  /// `matchesGoldenFile`).
  final Map<String, String> arguments;

  /// The `size:` argument, else the size passed to its `kitGalleryName`.
  final String? sizeArgument;
}

/// The size [kitGalleryName]'s call in [expression] was given (its second
/// positional argument), or null.
String? _galleryNameSize(KitSource source, int at, String? expression) {
  if (expression == null || !expression.startsWith('kitGalleryName')) {
    return null;
  }
  final open = source.code.indexOf('(', at);
  if (open < 0) return null;
  final args = source.arguments(open);
  return args.length > 1 && !args[1].contains(':') ? args[1] : null;
}

/// The `WxH` sizes a list expression holds: `Size(w, h)` literals, the
/// gallery harness lists, and named `<Size>[…]` lists declared in [code].
Set<String> _sizesIn(String expression, String code, [int depth = 0]) {
  final out = <String>{
    for (final m in RegExp(
      r'Size\(\s*(\d+)(?:\.0)?\s*,\s*(\d+)(?:\.0)?\s*\)',
    ).allMatches(expression))
      '${m[1]}x${m[2]}',
  };
  if (RegExp(r'\bkitGallerySizes\b').hasMatch(expression)) {
    out.addAll(_harnessSizes('kitGallerySizes'));
  }
  if (RegExp(r'\bkitGalleryScaledSizes\b').hasMatch(expression)) {
    out.addAll(_harnessSizes('kitGalleryScaledSizes'));
  }
  if (depth < 2) {
    for (final m in RegExp(r'\b(_?[a-z]\w*)\b').allMatches(expression)) {
      final list = RegExp(
        '\\b${RegExp.escape(m[1]!)}\\s*=\\s*(?:const\\s+)?(?:<Size>)?\\[([^\\]]*)\\]',
      ).firstMatch(code);
      if (list != null) out.addAll(_sizesIn(list[1]!, code, depth + 1));
    }
  }
  return out;
}

/// The sizes of a `const <name> = <Size>[…]` list in the gallery harness.
List<String> _harnessSizes(String name) {
  final m = RegExp(
    'const\\s+$name\\s*=\\s*<Size>\\[([^\\]]*)\\]',
  ).firstMatch(_source(_galleryHarness).code);
  if (m == null) return const [];
  return [
    for (final s in RegExp(
      r'Size\(\s*(\d+)\s*,\s*(\d+)\s*\)',
    ).allMatches(m[1]!))
      '${s[1]}x${s[2]}',
  ];
}

/// The goldens of a gallery file, read from its code.
class _Gallery {
  _Gallery(this.source) {
    final code = source.code;
    // kitGalleryShot, kitGalleryPart, and a gallery's own helper whose
    // `name:` is a kitGalleryName(…) call.
    for (final m in RegExp(r'(?<![\w$.])(\w+)\s*\(').allMatches(code)) {
      final callee = m[1]!;
      if (callee == 'kitGalleryName') continue;
      final gallery = callee == 'kitGalleryShot' || callee == 'kitGalleryPart';
      final close = source.close(m.end - 1);
      if (close < 0) continue;
      if (!gallery &&
          !RegExp(
            r'\bname\s*:\s*kitGalleryName\s*\(',
          ).hasMatch(code.substring(m.end, close))) {
        continue;
      }
      final named = <String, String>{};
      final at = <String, int>{};
      final open = m.end - 1;
      final end = source.close(open);
      if (end < 0) continue;
      var depth = 0;
      var from = open + 1;
      for (var i = open + 1; i <= end; i++) {
        final c = source.shape[i];
        if (i == end || (c == ',' && depth == 0)) {
          final a = code.substring(from, i);
          final n = RegExp(r'^\s*(\w+)\s*:\s*([\s\S]*)$').firstMatch(a);
          if (n != null) {
            named[n[1]!] = n[2]!.trim();
            at[n[1]!] = from + a.indexOf(n[2]!);
          }
          from = i + 1;
          continue;
        }
        if (c == '(' || c == '[' || c == '{') depth++;
        if (c == ')' || c == ']' || c == '}') depth--;
      }
      final name = _galleryName(named['name']);
      if (name == null) continue;
      if (!gallery && !named['name']!.startsWith('kitGalleryName')) continue;
      shots.add(
        _Shot(
          name,
          named,
          sizeArgument:
              named['size'] ??
              _galleryNameSize(source, at['name'] ?? 0, named['name']),
        ),
      );
    }
    for (final m in RegExp(r'\bmatchesGoldenFile\s*\(').allMatches(code)) {
      final args = source.arguments(m.end - 1);
      final name = args.isEmpty ? null : _literal(args.first);
      if (name != null) {
        shots.add(
          _Shot(
            name.replaceAll(RegExp(r'\.png$'), '').split('/').last,
            const {},
          ),
        );
      }
    }
    final literal = RegExp(r'''(['"])(dark|light)\1''');
    final modes = {for (final m in literal.allMatches(code)) m[2]!};
    _modeVariable = modes.containsAll(['dark', 'light']);
  }

  final KitSource source;
  final shots = <_Shot>[];
  late final bool _modeVariable;

  static String? _literal(String? expression) {
    if (expression == null) return null;
    final m = RegExp(r'''^(['"])(.*)\1$''').firstMatch(expression.trim());
    return m?[2];
  }

  /// The modes a golden name covers.
  Set<String> modesOf(_Shot shot) => {
    if (shot.name.contains('dark')) 'dark',
    if (shot.name.contains('light')) 'light',
    if (_modeVariable && RegExp(r'\$\{?mode\b').hasMatch(shot.name)) ...[
      'dark',
      'light',
    ],
  };

  /// The `WxH` sizes [shot] is recorded at: from its name, a `Size(…)`
  /// literal, a variable set to one, or the list a `for` loop over the
  /// variable walks.
  Set<String> sizesOf(_Shot shot) {
    final named = RegExp(r'(\d+)x(\d+)').firstMatch(shot.name);
    if (named != null) return {'${named[1]}x${named[2]}'};
    final size = shot.sizeArgument;
    if (size == null) return const {};
    final code = source.code;
    final literal = _sizesIn(size, code, 2);
    if (literal.isNotEmpty) return literal;
    if (!RegExp(r'^\w+$').hasMatch(size)) return const {};
    final out = <String>{};
    for (final m in RegExp(
      '\\b$size\\s*=\\s*(?:const\\s+)?(Size\\([^()]*\\))',
    ).allMatches(code)) {
      out.addAll(_sizesIn(m[1]!, code, 2));
    }
    for (final m in RegExp(
      '\\bfor\\s*\\(\\s*(?:final|var|const)?\\s*(?:Size\\s+)?$size\\s+in\\b',
    ).allMatches(code)) {
      final open = code.indexOf('(', m.start);
      final end = source.close(open);
      if (end < 0) continue;
      out.addAll(_sizesIn(code.substring(m.end, end), code));
    }
    return out;
  }

  /// Whether [shot] is at 412×915.
  bool atPhone(_Shot shot) => sizesOf(shot).contains('412x915');

  /// Whether some shot is at [size] (`WxH`).
  bool covers(String size) => shots.any((s) => sizesOf(s).contains(size));

  bool uses(String identifier) =>
      RegExp('\\b$identifier\\b').hasMatch(source.code);

  bool get hasText2 => shots.any(
    (s) =>
        s.name.contains('text2') &&
        (RegExp(r'^2(?:\.0)?$').hasMatch(s.arguments['textScale'] ?? '') ||
            (s.arguments.isEmpty &&
                RegExp(
                  r'TextScaler\.linear\(\s*2(?:\.0)?\s*\)',
                ).hasMatch(source.code))),
  );

  /// The modes of the 412×915 goldens named `<snake>_<state>…`.
  Set<String> stateModes(String snake, String state) {
    final prefix = RegExp('^${RegExp.escape('${snake}_$state')}(?:\$|_|\\\$)');
    return {
      for (final s in shots)
        if (prefix.hasMatch(s.name) && atPhone(s)) ...modesOf(s),
    };
  }

  Set<String> get allModes => {for (final s in shots) ...modesOf(s)};
}

/// check -> subject -> why it fails.
Map<String, Map<String, String>> kitManifestViolations(KitManifest manifest) {
  final out = {for (final c in kitManifestChecks) c: <String, String>{}};
  final docRows = _source(
    _kitLibrary,
  ).text.split('\n').where((l) => l.startsWith('/// |')).join('\n');
  bool inDocTable(String name) => docRows.contains('[$name]');

  String? consumer(String path) =>
      File(path).existsSync() ? _source(path).code : null;

  final motion = consumer(_motionTest);
  final keyboard = consumer(_keyboardTest);
  final overflow = consumer(_overflowTest);
  bool covers(String? code, KitManifestPart part) =>
      code != null &&
      (RegExp(r'\breadKitManifest\s*\(').hasMatch(code) ||
          part.aliases.any(
            (a) => RegExp('\\b${RegExp.escape(a)}\\b').hasMatch(code),
          ));

  for (final part in manifest.parts) {
    final snake = part.snake;
    final home = part.homeSnake;
    if (!part.exported) {
      out['exported']![part.name] =
          '${part.file} is not reachable from $_kitLibrary';
    }
    final allowedFiles = part.kind == KitManifestKind.scene
        ? ['lib/ui/kit/scenes/$snake.dart']
        : [
            'lib/ui/kit/$snake.dart',
            'lib/ui/kit/chat/$snake.dart',
            'lib/ui/kit/team/$snake.dart',
          ];
    if (!part.name.startsWith('Kit')) {
      out['name']![part.name] = 'not named Kit<Name> (${part.file})';
    } else if (!allowedFiles.contains(part.file) && !part.coLocated) {
      out['name']![part.name] =
          'declared in ${part.file}, not ${allowedFiles.join(' or ')}';
    }
    if (!inDocTable(part.name)) {
      out['docRow']![part.name] = 'no [${part.name}] row in the kit.dart table';
    }
    final galleryPath = 'test/goldens/kit/${home}_golden_test.dart';
    final gallery = File(galleryPath).existsSync()
        ? _Gallery(_source(galleryPath))
        : null;
    if (part.kind == KitManifestKind.scene) {
      // TEST-14: dark and light goldens of the finished frame.
      if (gallery == null) {
        out['gallery']![part.name] = 'no $galleryPath';
      } else {
        final missing = {'dark', 'light'}.difference(gallery.allModes);
        if (missing.isNotEmpty) {
          out['gallery']![part.name] =
              '$galleryPath has no ${missing.join(' or ')} golden';
        }
      }
      continue;
    }
    final unitTest = 'test/kit/${home}_test.dart';
    if (!File(unitTest).existsSync()) {
      out['test']![part.name] = 'no $unitTest';
    }
    if (part.kind == KitManifestKind.scope) continue;

    if (part.statesProblem case final problem?) {
      out['states']![part.name] = problem;
    } else {
      final declared = part.states!;
      final missing = [
        for (final MapEntry(key: state, value: why)
            in part.requiredStates.entries)
          if (!declared.contains(state)) '$state ($why)',
      ];
      if (missing.isNotEmpty) {
        out['states']![part.name] =
            'declares ${declared.isEmpty ? 'none' : declared.join(', ')} '
            'but needs ${missing.join(', ')}';
      }
    }
    if (gallery == null) {
      out['gallery']![part.name] = 'no $galleryPath';
    } else {
      final missing = [
        for (final size in _gallerySizes)
          if (!gallery.covers(size)) 'a $size golden',
        if (!gallery.hasText2 && !_text2Pending.containsKey(part.name))
          'a …text2… golden at textScale: 2',
      ];
      if (missing.isNotEmpty) {
        out['gallery']![part.name] = '$galleryPath lacks ${missing.join(', ')}';
      }
    }
    final missingScenes = [
      for (final state in part.states ?? const <String>[])
        for (final mode in ['dark', 'light'])
          if (gallery == null ||
              !{
                ...gallery.stateModes(snake, state),
                if (home != snake)
                  ...gallery.stateModes(
                    '${home}_${snake.replaceFirst('kit_', '')}',
                    state,
                  ),
              }.contains(mode))
            '${snake}_${state}_…$mode',
    ];
    if (missingScenes.isNotEmpty) {
      out['stateScenes']![part.name] =
          'no 412x915 golden ${missingScenes.join(', ')}';
    }
    if (!covers(motion, part)) {
      out['motion']![part.name] = 'not in $_motionTest';
    }
    if ((part.isModal || part.isRow) && !covers(keyboard, part)) {
      out['keyboard']![part.name] = 'not in $_keyboardTest';
    }
    if (!covers(overflow, part)) {
      out['overflow']![part.name] = 'not in $_overflowTest';
    }
  }

  const exactReturns = {
    'showKitSheet': 'Future<T?>',
    'showKitConfirm': 'Future<bool>',
    'showKitInputDialog': 'Future<String?>',
    'showKitUndo': 'void',
  };
  for (final opener in manifest.openers) {
    if (!opener.exported) {
      out['exported']![opener.name] =
          '${opener.file} is not reachable from $_kitLibrary';
    }
    final type = opener.returnType.replaceAll(RegExp(r'\s+'), '');
    final exact = exactReturns[opener.name];
    if (exact != null ? type != exact : !type.startsWith('Future<')) {
      out['openerReturn']![opener.name] =
          '${type.isEmpty ? 'declares no return type' : 'returns ${opener.returnType}'}, '
          'expected ${exact ?? 'Future<…>'}';
    }
    if (opener.name != 'showKitUndo' && !opener.hasOptionalKey) {
      out['openerKey']![opener.name] = 'no optional Key? …Key parameter';
    }
    if (!inDocTable(opener.name)) {
      out['docRow']![opener.name] =
          'no [${opener.name}] row in the kit.dart table';
    }
  }

  final gallerySizes = _harnessSizes('kitGallerySizes');
  for (final size in _gallerySizes) {
    if (!gallerySizes.contains(size)) {
      out['harness']!['kitGallerySizes $size'] =
          '$_galleryHarness kitGallerySizes lacks the gallery size $size';
    }
  }
  final scaledSizes = _harnessSizes('kitGalleryScaledSizes');
  for (final size in _test9ScaledSizes) {
    if (!scaledSizes.contains(size)) {
      out['harness']!['kitGalleryScaledSizes $size'] =
          '$_galleryHarness kitGalleryScaledSizes lacks the TEST-9 size $size';
    }
  }
  return out;
}

Map<String, List<String>> _readAllowlist(Map<String, Object?> json) => {
  for (final MapEntry(:key, :value) in json.entries)
    if (!key.startsWith('_'))
      key: [for (final v in value! as List<Object?>) v! as String],
};

String _encodeAllowlist(Map<String, List<String>> allowlist, String about) {
  final ordered = <String, Object>{'_about': about};
  for (final check in kitManifestChecks) {
    final names = [...?allowlist[check]]..sort();
    if (names.isNotEmpty) ordered[check] = names;
  }
  return '${const JsonEncoder.withIndent('  ').convert(ordered)}\n';
}
