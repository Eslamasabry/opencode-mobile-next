import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// The four chips of Settings › Appearance, from the biggest change to the
/// smallest (docs/design: guided Appearance, 2026-10-23).
enum AppearanceGroup {
  colours([
    AppearanceQuestion.mode,
    AppearanceQuestion.theme,
    AppearanceQuestion.accentColour,
  ]),
  style([AppearanceQuestion.style, AppearanceQuestion.shape]),
  background([AppearanceQuestion.look, AppearanceQuestion.strength]),
  comfort([
    AppearanceQuestion.text,
    AppearanceQuestion.motion,
    AppearanceQuestion.glow,
  ]);

  const AppearanceGroup(this.questions);

  /// Its smaller chips, each one question, in order.
  final List<AppearanceQuestion> questions;
}

/// One decision of Appearance, asked on its own.
enum AppearanceQuestion {
  mode,
  theme,
  accentColour,
  style,
  shape,
  look,
  strength,
  text,
  motion,
  glow;

  /// The chip it belongs to.
  AppearanceGroup get group =>
      AppearanceGroup.values.firstWhere((g) => g.questions.contains(this));

  /// Saver keeps surfaces solid and the background still, so these wait.
  bool get heldBySaver => switch (this) {
    style || shape || look || strength => true,
    _ => false,
  };

  /// Every question, in the guide's order.
  static final order = [
    for (final group in AppearanceGroup.values) ...group.questions,
  ];

  /// The question after [from] (null: the first) the guide goes to, past the
  /// ones Saver holds; null when [from] is the last.
  static AppearanceQuestion? after(
    AppearanceQuestion? from, {
    required bool saver,
  }) {
    final start = from == null ? 0 : order.indexOf(from) + 1;
    for (final question in order.skip(start)) {
      if (saver && question.heldBySaver) continue;
      return question;
    }
    return null;
  }

  static AppearanceQuestion? byName(Object? name) =>
      values.where((q) => q.name == name).firstOrNull;
}

/// What this device remembers about the guided Appearance page, app-wide
/// (not per server) as `oc.appearanceGuide`: whether the guide was finished
/// or skipped, the questions seen on the way, the question last used in
/// each chip, and the question changed last. Plain data.
@immutable
class AppearanceGuideRecord {
  const AppearanceGuideRecord({
    this.done = false,
    this.seen = const [],
    this.lastIn = const {},
    this.last,
  });

  /// The guide was finished or skipped: the page shows no Next bar, no dots
  /// and no checks until "Guide me again".
  final bool done;

  /// The questions opened while guided, in the order they were opened.
  final List<AppearanceQuestion> seen;

  /// The question last opened in each chip.
  final Map<AppearanceGroup, AppearanceQuestion> lastIn;

  /// The question whose answer changed last ("Changed last").
  final AppearanceQuestion? last;

  AppearanceGuideRecord copyWith({
    bool? done,
    List<AppearanceQuestion>? seen,
    Map<AppearanceGroup, AppearanceQuestion>? lastIn,
    AppearanceQuestion? last,
  }) => AppearanceGuideRecord(
    done: done ?? this.done,
    seen: seen ?? this.seen,
    lastIn: lastIn ?? this.lastIn,
    last: last ?? this.last,
  );

  Map<String, Object?> toJson() => {
    if (done) 'done': true,
    'seen': [for (final q in seen) q.name],
    'lastIn': {for (final e in lastIn.entries) e.key.name: e.value.name},
    if (last != null) 'last': last!.name,
  };

  static AppearanceGuideRecord fromJson(Object? value) {
    if (value is! Map) return const AppearanceGuideRecord();
    final seen = value['seen'];
    final lastIn = value['lastIn'];
    return AppearanceGuideRecord(
      done: value['done'] == true,
      seen: [
        if (seen is List)
          for (final name in seen) ?AppearanceQuestion.byName(name),
      ],
      lastIn: {
        if (lastIn is Map)
          for (final MapEntry(:key, :value) in lastIn.entries)
            if (AppearanceGroup.values.where((g) => g.name == key).firstOrNull
                case final group?)
              if (AppearanceQuestion.byName(value) case final q?
                  when q.group == group)
                group: q,
      },
      last: AppearanceQuestion.byName(value['last']),
    );
  }
}

/// Reads and writes [AppearanceGuideRecord] on this device. A write that
/// fails leaves the page working: the record is a convenience.
class AppearanceGuideStore {
  AppearanceGuideStore(this.preferences);

  final SharedPreferences preferences;

  /// The device-wide storage name (not per server: no profile owns it).
  static const key = 'oc.appearanceGuide';

  AppearanceGuideRecord read() {
    final raw = preferences.getString(key);
    if (raw == null) return const AppearanceGuideRecord();
    try {
      return AppearanceGuideRecord.fromJson(jsonDecode(raw));
    } on FormatException {
      return const AppearanceGuideRecord();
    }
  }

  Future<void> write(AppearanceGuideRecord record) async {
    try {
      await preferences.setString(key, jsonEncode(record.toJson()));
    } catch (_) {
      // Kept for this visit only.
    }
  }
}
