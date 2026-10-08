// Gate G4 (docs/ux-system/revamp/STANDARDS.md §18): the kit manifest.
//
// A pure-Dart source scan, no widget pumping. It reads the exports of
// `lib/ui/kit/kit.dart` (following `show`/`hide` combinators, re-exports
// and `part` files, with either quote style) and also scans every `.dart`
// file under `lib/ui/kit/`, so a kit file that screens import by path is
// seen too. It collects every public widget class, every `KitScene`
// subclass and every top-level `showKit…` function. Comments are stripped
// before any code is read. Each part must then have what the rulebook asks
// for:
//
// - exported (KIT-14, NAME-1): reachable from `kit.dart`, not only by path.
// - name (NAME-1): class `Kit<Name>` in `lib/ui/kit/kit_<snake>.dart`
//   (chat parts in `lib/ui/kit/chat/`, scenes in `lib/ui/kit/scenes/`).
//   A part may share its file with the file's own part when that part's
//   frozen spec (`docs/ux-system/kit-api/<FilePart>.md`, FilePart being the
//   file name in PascalCase) declares `class <Name>` (KitAvatar and KitZoom
//   in kit_image.dart, KitSwap in motion/kit_motion_parts.dart). Such a
//   part's gallery and unit test are its file's.
// - states (KIT-12): a `States: …` line in its doc comment naming the states
//   it has from {loading, empty, error, disabled, working, answered}, for
//   example `/// States: loading, empty, error.` The part's own fields add
//   required states: a nullable `on…` callback needs `disabled`; a
//   `working`/`busy`/`sending` flag or an `onSubmit…`/`onSend…` callback
//   needs `working`; a Future, Stream, AsyncSnapshot or a List/Iterable/Map
//   of non-UI values (server data) needs loading, empty and error. A part
//   that needs none may write `/// States: none — <why, three words or
//   more>.`; a bare `States: none.` fails.
// - stateScenes (KIT-12, TEST-9): per declared state, dark and light
//   goldens at 412×915 in its gallery, named `<snake>_<state>…` in the
//   `name:` of a `kitGalleryShot` call or in a `matchesGoldenFile` literal
//   (the mode is a literal `dark`/`light` or `$mode` with both literals in
//   the file; 412×915 is the `size:` argument or `412x915` in the name).
// - gallery (TEST-9, TEST-14, LAY-4, KIT-32): `test/goldens/kit/
//   <snake>_golden_test.dart` with a golden at phone 412×915 and one at
//   1280×800, and a `…text2…` golden shot at `textScale: 2`; for a scene,
//   dark and light goldens (TEST-14). Owner decision (2026-09-27): Arabic
//   is dropped (no `…_ar_…` shot required) and galleries are required only
//   at those two sizes, not every `kitGallerySizes` size. Shots are read
//   from `kitGalleryShot(…)` and `kitGalleryPart(…)` calls and from any
//   call whose `name:` is a `kitGalleryName(…)` (a gallery's own helper).
// - test (TEST-15, NAME-1): `test/kit/<snake>_test.dart`.
// - docRow (KIT-14): a row in the `kit.dart` doc table naming `[<Name>]`.
// - motion (TEST-15, G8): named in the code of `test/kit_motion_test.dart`
//   (by class or by its `showKit…` opener), or that file calls
//   `readKitManifest(`.
// - keyboard (TEST-15, G14): modal parts and rows, likewise in
//   `test/kit/kit_keyboard_test.dart`.
// - overflow (TEST-15, G6): likewise in `test/text_scale_overflow_test.dart`.
// - openerReturn (KIT-11): a `showKit…` returns `Future<…>` (`showKitSheet`
//   `Future<T?>`, `showKitConfirm` `Future<bool>`, `showKitInputDialog`
//   `Future<String?>`); only `showKitUndo` returns `void`.
// - openerKey (KIT-10): a `showKit…` declares at least one optional
//   `Key? …Key` parameter.
// - harness (LAY-4, TEST-9): `kitGallerySizes` and `kitGalleryScaledSizes`
//   in the gallery harness both hold the two gallery sizes (412×915,
//   1280×800).
//
// InheritedWidget scopes (KitEffectsScope and the like) draw nothing, so
// they need only the exported, name, test and docRow checks. A class whose
// doc comment starts `Retired by kit-…` is a forwarding wrapper a unit moved
// into the kit unchanged (KIT-43, R12); the G2 ratchet counts its callers
// down, so it is not a part here. A KitScene
// (drawn by KitIllustration, TEST-14) needs exported, name (in
// `lib/ui/kit/scenes/`), a docRow and a gallery with dark and light shots.
//
// Parts that predate the gate sit in `kit_manifest_allowlist.json`
// (check -> subjects). The allowlist only shrinks:
// - a violation not on it fails;
// - an entry that no longer fails (stale) fails too, and the message
//   prints the smaller allowlist to commit; shrink it in place with
//     KIT_MANIFEST_WRITE=1 flutter test test/kit/kit_manifest_test.dart
//   (the pinned Flutter from AGENTS.md), which never adds an entry;
// - an entry outside [_creationAllowlist] (the allowlist when the gate was
//   made) and [_deferredAtKitMerge] (the gaps left when the last kit unit
//   merged, each with who closes it) fails, so a new part cannot be
//   added. Changing that ceiling is a gate change (KIT-44: a
//   `ratchet-tighten: G4 <check>` commit).
//
// The motion, keyboard, overflow and gallery-guideline gates (G8x, G14x,
// G6, G5) read the same manifest instead of hand lists:
//   import 'kit_manifest_test.dart' show readKitManifest, KitManifestPart;
// A consumer whose code calls `readKitManifest(` counts as covering every
// part for its check.
import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

part 'kit_manifest_data.dart';
part 'kit_manifest_source.dart';
part 'kit_manifest_checks.dart';

void main() {
  final manifest = readKitManifest();

  test('G4: every deferral names who closes it', () {
    for (final MapEntry(key: check, value: parts)
        in _deferredAtKitMerge.entries) {
      expect(kitManifestChecks, contains(check));
      for (final MapEntry(key: part, value: owner) in parts.entries) {
        expect(owner.length, greaterThan(10), reason: '$check · $part');
      }
    }
  });

  test('G4: the manifest reads the kit library and every kit file', () {
    final names = {for (final p in manifest.parts) p.name};
    // Loud failures if the scan silently finds nothing (a vacuous pass).
    expect(names, containsAll(['KitSheet', 'KitConfirmSheet', 'KitRow']));
    expect(
      manifest.parts.where((p) => !p.exported).map((p) => p.name),
      containsAll(['StatesSheetScene', 'TerminalKeyBar']),
      reason: 'kit files imported by path must be in the manifest',
    );
    expect(
      manifest.openers.map((o) => o.name),
      containsAll(['showKitSheet', 'showKitConfirm']),
    );
    expect(
      manifest.parts.firstWhere((p) => p.name == 'KitSheet').openers,
      contains('showKitSheet'),
    );
    expect(
      manifest.unresolved,
      isEmpty,
      reason: 'kit.dart `show` names no declaration was found for',
    );
    expect(
      manifest.problems,
      isEmpty,
      reason: 'kit declarations the scan could not read',
    );
  });

  test('G4: every kit part and opener meets the manifest '
      '(allowlist only shrinks)', () {
    final raw =
        jsonDecode(File(_allowlistPath).readAsStringSync())
            as Map<String, Object?>;
    final unknownChecks = raw.keys
        .where((k) => !k.startsWith('_') && !kitManifestChecks.contains(k))
        .toList();
    expect(
      unknownChecks,
      isEmpty,
      reason: '$_allowlistPath names checks this gate does not run',
    );
    final allowlist = _readAllowlist(raw);
    final violations = kitManifestViolations(manifest);

    final grown = <String>[];
    final fresh = <String>[];
    final shrunk = <String, List<String>>{};
    final stale = <String>[];
    for (final check in kitManifestChecks) {
      final allowed = allowlist[check] ?? const <String>[];
      final ceiling = [
        ...?_creationAllowlist[check],
        ...?_deferredAtKitMerge[check]?.keys,
      ];
      final failing = violations[check]!;
      for (final subject in allowed) {
        if (!ceiling.contains(subject)) grown.add('$check · $subject');
      }
      for (final MapEntry(key: subject, value: why) in failing.entries) {
        if (!allowed.contains(subject) || !ceiling.contains(subject)) {
          fresh.add('$check · $subject: $why');
        }
      }
      shrunk[check] = [
        for (final subject in allowed)
          if (failing.containsKey(subject) && ceiling.contains(subject))
            subject,
      ];
      for (final subject in allowed) {
        if (!failing.containsKey(subject)) stale.add('$check · $subject');
      }
    }

    final lingering = [
      for (final MapEntry(key: check, value: parts)
          in _deferredAtKitMerge.entries)
        for (final part in parts.keys)
          if (!(allowlist[check] ?? const <String>[]).contains(part))
            '$check · $part',
    ];
    expect(
      lingering,
      isEmpty,
      reason:
          'these deferrals are closed: delete their lines from '
          '_deferredAtKitMerge in test/kit/kit_manifest_test.dart',
    );

    expect(
      grown,
      isEmpty,
      reason:
          '$_allowlistPath grew past the allowlist the gate was made with; '
          'fix the part instead (KIT-12, TEST-15; §18.2 "only shrinks")',
    );

    if (fresh.isNotEmpty) {
      fail(
        '${fresh.length} kit manifest violation(s) break STANDARDS.md G4 '
        '(NAME-1, KIT-10–KIT-14, TEST-9, TEST-14, TEST-15). Fix the part; '
        'the allowlist only shrinks:\n'
        '${fresh.map((f) => '  - $f').join('\n')}',
      );
    }

    if (stale.isNotEmpty) {
      final about = raw['_about'] as String? ?? '';
      final encoded = _encodeAllowlist(shrunk, about);
      if (Platform.environment['KIT_MANIFEST_WRITE'] == '1') {
        File(_allowlistPath).writeAsStringSync(encoded);
        stdout.writeln(
          'G4: wrote the smaller $_allowlistPath without:\n'
          '${stale.map((s) => '  - $s').join('\n')}',
        );
      } else {
        fail(
          'G4: ${stale.length} allowlist '
          '${stale.length == 1 ? 'entry now passes' : 'entries now pass'}; '
          'commit the smaller $_allowlistPath (or rerun with '
          'KIT_MANIFEST_WRITE=1):\n'
          '${stale.map((s) => '  - $s').join('\n')}\n$encoded',
        );
      }
    }
  });
}
