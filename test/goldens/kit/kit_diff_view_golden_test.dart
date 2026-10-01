// Gallery (gate G4) for KitDiffView, docs/ux-system/kit-api/KitDiffView.md:
// the declared states (unified with two files, a gap and three changes;
// selecting; binary and renamed; too big; loading; empty; error) at the
// two sizes the owner's 2026-09-27 decision keeps (412x915 phone, 1280x800
// wide), light and dark, and the default (unified) at text 2.0 at both
// sizes (TEST-9, G4). Arabic galleries are dropped by that same decision.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/goldens/kit/kit_diff_view_golden_test.dart
// and look at every changed image before committing it.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/kit/kit_diff_view.dart';
import 'package:opencode_mobile/ui/kit/kit_tokens.dart';

import 'kit_gallery.dart';

/// The diff filling the page body on `ground`, as a reader page shows it.
Widget _page(Widget child) => Builder(
  builder: (context) => ColoredBox(
    color: KitTokens.of(context).roles.ground,
    child: SafeArea(child: child),
  ),
);

const _before = r'''
import 'package:flutter/widgets.dart';

class Greeting extends StatelessWidget {
  const Greeting({super.key, required this.name});

  final String name;

  @override
  Widget build(BuildContext context) {
    final text = 'Hello, ' + name;
    return Text(text);
  }
}

String shout(String value) => value.toUpperCase();

int count(List<String> items) {
  var total = 0;
  for (final item in items) {
    total += item.length;
  }
  return total;
}''';

const _after = r'''
import 'package:flutter/widgets.dart';

class Greeting extends StatelessWidget {
  const Greeting({super.key, required this.name, this.emphasis = false});

  final String name;
  final bool emphasis;

  @override
  Widget build(BuildContext context) {
    final text = 'Welcome aboard, $name';
    return Text(text);
  }
}

String shout(String value) => value.toUpperCase();

int count(List<String> items) {
  var total = 0;
  for (final item in items) {
    total += item.length;
  }
  return total + 1;
}''';

List<KitDiffFile> _files() => [
  KitDiffFile.fromTexts('lib/ui/greeting.dart', before: _before, after: _after),
  KitDiffFile.fromPatch(
    'README.md',
    '@@ -1,4 +1,4 @@\n'
        ' # Greeting\n'
        '-Says hello.\n'
        '+Says welcome aboard.\n'
        ' \n'
        ' Run `flutter test` to check it.\n',
  ),
];

Widget _unified() => _page(KitDiffView(files: _files()));

/// A JSON file's diff: syntax colour on a language that is not code
/// (issue #26).
Widget _json() => _page(
  KitDiffView(
    files: [
      KitDiffFile.fromTexts(
        'package.json',
        before: _jsonBefore,
        after: _jsonAfter,
      ),
    ],
  ),
);

const _jsonBefore = '''
{
  "name": "greeting",
  "version": "1.2.0",
  "private": true,
  "scripts": {
    "build": "tsc -p .",
    "test": "jest --ci"
  },
  "files": ["dist"]
}''';

const _jsonAfter = '''
{
  "name": "greeting",
  "version": "1.3.0",
  "private": true,
  "scripts": {
    "build": "tsc -p .",
    "test": "jest --ci --coverage",
    "lint": "eslint src"
  },
  "files": ["dist"]
}''';

Widget _selecting() => _page(
  KitDiffView(
    files: _files(),
    readOnly: false,
    onComment: (_) {},
    onAddToPrompt: (_) {},
  ),
);

/// The binary file open; the renamed one's "Renamed from" shows in the
/// wide file list (and the phone's picker sheet).
Widget _binaryRenamed() => _page(
  KitDiffView(
    initialFile: 1,
    files: [
      const KitDiffFile(
        path: 'lib/ui/greeting_card.dart',
        segments: [
          KitDiffLine(
            'class GreetingCard {}',
            KitDiffLineKind.context,
            oldNo: 1,
            newNo: 1,
          ),
        ],
        added: 0,
        removed: 0,
        status: KitDiffFileStatus.renamed,
        oldPath: 'lib/ui/card.dart',
      ),
      const KitDiffFile(
        path: 'assets/logo.png',
        segments: [],
        added: 0,
        removed: 0,
        binary: true,
      ),
    ],
  ),
);

Widget _tooBig() => _page(
  KitDiffView(
    files: [
      KitDiffFile.fromTexts(
        'lib/generated/strings.dart',
        after: [
          for (var i = 1; i <= 3200; i++) "const s$i = 'string $i';",
        ].join('\n'),
      ),
    ],
    maxLines: 400,
    onOpenAll: () {},
  ),
);

Widget _loading() => _page(const KitDiffView(files: [], loading: true));

Widget _empty() => _page(const KitDiffView(files: []));

Widget _error() => _page(
  KitDiffView(
    files: const [],
    error: 'The server stopped answering.',
    onRetry: () {},
  ),
);

Future<void> Function(BuildContext) _push(Widget scene) =>
    (context) => Navigator.of(context).push<void>(
      PageRouteBuilder<void>(
        transitionDuration: Duration.zero,
        reverseTransitionDuration: Duration.zero,
        pageBuilder: (_, _, _) => Scaffold(body: scene),
      ),
    );

void main() {
  setUpAll(loadKitGalleryFonts);

  const sizes = [Size(412, 915), Size(1280, 800)];

  for (final light in [false, true]) {
    final mode = light ? 'light' : 'dark';
    for (final size in sizes) {
      final at = kitGallerySize(size);
      for (final MapEntry(key: state, value: scene) in <String, Widget>{
        'unified': _unified(),
        'json': _json(),
        'binary_renamed': _binaryRenamed(),
        'too_big': _tooBig(),
        'loading': _loading(),
        'empty': _empty(),
        'error': _error(),
      }.entries) {
        testWidgets('kit_diff_view $state · $at · $mode', (tester) async {
          await kitGalleryShot(
            tester,
            name: kitGalleryName('kit_diff_view_$state', size, light: light),
            size: size,
            light: light,
            open: _push(scene),
          );
        });
      }

      testWidgets('kit_diff_view selecting · $at · $mode', (tester) async {
        await kitGalleryShot(
          tester,
          name: kitGalleryName('kit_diff_view_selecting', size, light: light),
          size: size,
          light: light,
          open: _push(_selecting()),
          then: (tester) async {
            final from = tester.getCenter(
              find.byKey(const ValueKey('kit-diff-line-0-current-3')),
            );
            final to = tester.getCenter(
              find.byKey(const ValueKey('kit-diff-line-0-current-5')),
            );
            await tester.timedDragFrom(
              from,
              to - from,
              const Duration(milliseconds: 300),
            );
          },
        );
      });
    }

    for (final size in kitGalleryScaledSizes) {
      testWidgets(
        'kit_diff_view unified · text 2.0 · ${kitGallerySize(size)} · $mode',
        (tester) async {
          await kitGalleryShot(
            tester,
            name: kitGalleryName(
              'kit_diff_view_unified',
              size,
              light: light,
              text2: true,
            ),
            size: size,
            light: light,
            textScale: 2,
            open: _push(_unified()),
          );
        },
      );
    }
  }
}
