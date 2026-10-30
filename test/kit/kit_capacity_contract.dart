// The kit capacity contract (docs/ux-system/kit-v2.md §8.6) and its
// ratchet: test/kit/kit_capacity.json declares, for every exported kit part,
// how wide it may grow, the overflow rule of each text slot and what each
// list-like slot does with more items than fit; test/kit/
// kit_capacity_allowlist.json holds what breaks that contract today, with
// the measured numbers, and may only shrink.
import 'dart:convert';
import 'dart:io';

import 'package:opencode_mobile/ui/kit/kit_layout.dart';

const kitCapacityContractPath = 'test/kit/kit_capacity.json';
const kitCapacityAllowlistPath = 'test/kit/kit_capacity_allowlist.json';

/// The widths a role or part may name as its cap. `KitLayout.*` are the
/// kit's own tokens, read from the code so the gate follows them. `null`
/// is a part that fills the region its caller gives it by design (a screen,
/// a bar, a full-screen viewer), wraps a child that has its own cap, or
/// draws nothing.
final Map<String, double?> kitWidthTokens = {
  'KitLayout.paneDetailMaxWidth': KitLayout.paneDetailMaxWidth,
  'KitLayout.readingWidth': KitLayout.readingWidth,
  'KitLayout.listWidth': KitLayout.listWidth,
  'KitLayout.sheetMaxWidth': KitLayout.sheetMaxWidth,
  'KitLayout.dialogPanelWidth': KitLayout.dialogPanelWidth,
  'KitLayout.confirmMediumWidth': KitLayout.confirmMediumWidth,
  'KitLayout.confirmDialogWidth': KitLayout.confirmDialogWidth,
  'KitLayout.popoverMaxWidth': KitLayout.popoverMaxWidth,
  'KitLayout.undoMaxWidth': KitLayout.undoMaxWidth,
  'KitLayout.stateMaxWidth': KitLayout.stateMaxWidth,
  'KitLayout.laneMaxWidth': KitLayout.laneMaxWidth,
  'KitLayout.controlMaxWidth': KitLayout.controlMaxWidth,
  'KitLayout.chipMaxWidth': KitLayout.chipMaxWidth,
  'KitLayout.glyphMaxWidth': KitLayout.glyphMaxWidth,
  'fill': null,
  'wrapper': null,
  'scope': null,
};

/// What a list-like slot does with more items than fit (§8.6).
const kitListBeyond = {
  // Every item, stacked; the page or sheet around it scrolls.
  'grows',
  // Every item, flowing onto more lines.
  'wraps',
  // The slot scrolls inside its own box.
  'scroll',
  // As many as fit (at most `shows` when set), then an "N more" control.
  'more',
  // At most `shows` items per page, with paging.
  'page',
  // The part takes only a fixed count (asserted); more is a caller bug.
  'fixed',
  // Data the part draws as one mark (chart values, colours), not items.
  'not-items',
  // Nobody has declared it yet: allowlisted under `list`, never valid for
  // a new slot.
  'unknown',
};

/// The `beyond` rules that draw items, so the slot needs a many-item run.
const kitListDrawsItems = {'grows', 'wraps', 'scroll', 'more', 'page', 'fixed'};

class KitListRule {
  const KitListRule({required this.beyond, this.shows, this.via});

  /// The most items drawn at once, or null for no limit of its own.
  final int? shows;
  final String beyond;

  /// Another slot (`Part.slot`) whose 50-item stress run proves this one:
  /// a part's `menu` is drawn by KitMenuPanel.items.
  final String? via;

  Map<String, Object?> toJson() => {
    'beyond': beyond,
    if (shows != null) 'shows': shows,
    if (via != null) 'via': via,
  };
}

class KitPartContract {
  KitPartContract({
    required this.role,
    this.maxWidth,
    Map<String, String>? text,
    Map<String, KitListRule>? lists,
  }) : text = text ?? {},
       lists = lists ?? {};

  final String role;

  /// A token from [kitWidthTokens] that overrides the role's cap.
  final String? maxWidth;

  /// Slot key ([slotKey]) -> overflow rule ([textRule]).
  final Map<String, String> text;

  /// List-like field name -> what it does beyond what fits.
  final Map<String, KitListRule> lists;
}

class KitCapacityContract {
  KitCapacityContract({
    required this.raw,
    required this.minTarget,
    required this.roles,
    required this.parts,
  });

  /// The decoded file, kept so a write changes only what it must.
  final Map<String, Object?> raw;
  final double minTarget;

  /// Role -> width token.
  final Map<String, String> roles;
  final Map<String, KitPartContract> parts;

  static KitCapacityContract load([String path = kitCapacityContractPath]) {
    final raw =
        jsonDecode(File(path).readAsStringSync()) as Map<String, Object?>;
    final roles = <String, String>{
      for (final MapEntry(:key, :value)
          in (raw['roles']! as Map<String, Object?>).entries)
        key: (value! as Map<String, Object?>)['maxWidth']! as String,
    };
    final parts = <String, KitPartContract>{};
    for (final MapEntry(:key, :value)
        in (raw['parts']! as Map<String, Object?>).entries) {
      final p = value! as Map<String, Object?>;
      parts[key] = KitPartContract(
        role: p['role']! as String,
        maxWidth: p['maxWidth'] as String?,
        text: {
          for (final MapEntry(:key, :value)
              in ((p['text'] as Map<String, Object?>?) ?? const {}).entries)
            key: value! as String,
        },
        lists: {
          for (final MapEntry(:key, :value)
              in ((p['lists'] as Map<String, Object?>?) ?? const {}).entries)
            key: _listRule(value! as Map<String, Object?>),
        },
      );
    }
    return KitCapacityContract(
      raw: raw,
      minTarget: (raw['minTarget']! as num).toDouble(),
      roles: roles,
      parts: parts,
    );
  }

  /// The width token [part] is capped by, or null when it has no contract.
  String? tokenOf(String part) {
    final p = parts[part];
    if (p == null) return null;
    return p.maxWidth ?? roles[p.role];
  }

  /// The cap in dp, or null for a part that fills, wraps or draws nothing.
  double? capOf(String part) {
    final token = tokenOf(part);
    return token == null ? null : kitWidthTokens[token];
  }
}

/// One check of the allowlist: subject -> the measured value (a width in
/// dp, or how many combinations failed) and a note for the reader.
typedef KitCapacityEntries = Map<String, ({num value, String note})>;

/// The checks a scene's observation is held to, keyed by scene id.
const kitCapacityChecks = {
  // A FlutterError or exception at a size, scale, direction and fixture.
  'error',
  // Words cut without an ellipsis, fade or scroller.
  'cut',
  // Painted wider than its cap at 1,600 dp.
  'width',
  // A tap target under the contract's minTarget (androidTapTargetGuideline).
  'target',
  // A text slot set up to clip (clip:N, visible:N), not a valid rule.
  'rule',
  // More list items drawn than the slot's `shows`.
  'items',
  // Static, keyed `Part.field` (kit_capacity_test.dart): a list slot whose
  // contract says `unknown`, and one that draws items but has no many-item
  // run in kit_capacity_lists.dart.
  'list',
  'run',
};

Map<String, KitCapacityEntries> loadKitCapacityAllowlist([
  String path = kitCapacityAllowlistPath,
]) {
  final file = File(path);
  if (!file.existsSync()) return {for (final c in kitCapacityChecks) c: {}};
  final raw = jsonDecode(file.readAsStringSync()) as Map<String, Object?>;
  return {
    for (final check in kitCapacityChecks)
      check: {
        for (final MapEntry(:key, :value)
            in ((raw[check] as Map<String, Object?>?) ?? const {}).entries)
          key: _entry(value! as Map<String, Object?>),
      },
  };
}

KitListRule _listRule(Map<String, Object?> json) => KitListRule(
  beyond: json['beyond']! as String,
  shows: json['shows'] as int?,
  via: json['via'] as String?,
);

({num value, String note}) _entry(Map<String, Object?> json) =>
    (value: json['value']! as num, note: (json['note'] as String?) ?? '');

const _about =
    'The kit capacity ratchet (docs/ux-system/kit-v2.md §8.6, '
    'test/kit/kit_capacity_test.dart). Each entry is a scene that breaks the '
    'contract in test/kit/kit_capacity.json today, with the measured value: '
    'error/cut/target/items count the failing combinations (or nodes, or '
    'items drawn), width is dp painted at 1,600 dp. The file only shrinks: '
    'a new violation fails, a worse value fails, a better or fixed one fails '
    'until this file is tightened (KIT_CAPACITY_WRITE=1 rewrites it, never '
    'adding an entry).';

String encodeKitCapacityAllowlist(Map<String, KitCapacityEntries> allowlist) {
  final out = <String, Object?>{'_about': _about};
  for (final check in kitCapacityChecks.toList()..sort()) {
    final entries = allowlist[check] ?? const {};
    out[check] = {
      for (final key in entries.keys.toList()..sort())
        key: {'value': entries[key]!.value, 'note': entries[key]!.note},
    };
  }
  return '${const JsonEncoder.withIndent('  ').convert(out)}\n';
}

/// The contract with its keys sorted, so a write gives a readable diff.
String encodeKitCapacityContract(Map<String, Object?> raw) {
  Object? sorted(Object? value) => switch (value) {
    Map<String, Object?>() => {
      for (final key in value.keys.toList()..sort()) key: sorted(value[key]),
    },
    List<Object?>() => [for (final v in value) sorted(v)],
    _ => value,
  };
  final top = <String, Object?>{
    for (final key in ['_about', 'minTarget', 'roles'])
      if (raw.containsKey(key)) key: sorted(raw[key]),
    'parts': sorted(raw['parts']),
  };
  return '${const JsonEncoder.withIndent('  ').convert(top)}\n';
}

/// Runs [update] with exclusive use of the contract and allowlist files, so
/// the shards that run side by side never overwrite each other's writes.
T withKitCapacityLock<T>(T Function() update) {
  final lock = File('build/kit_capacity.lock')..createSync(recursive: true);
  final handle = lock.openSync(mode: FileMode.write);
  handle.lockSync(FileLock.blockingExclusive);
  try {
    return update();
  } finally {
    handle.unlockSync();
    handle.closeSync();
  }
}
