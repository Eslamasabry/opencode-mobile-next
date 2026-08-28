import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/background/widget_snapshot.dart';
import 'package:shared_preferences/shared_preferences.dart';

Session _session(String id, {String? title, int? updated}) => Session(
  id: id,
  title: title,
  time: SessionTime(created: 1, updated: updated),
);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  Future<(WidgetSessionSnapshot, SharedPreferences, List<int>)> harness({
    bool isAndroid = true,
  }) async {
    SharedPreferences.setMockInitialValues({});
    final prefs = await SharedPreferences.getInstance();
    final refreshes = <int>[];
    final snapshot = WidgetSessionSnapshot(
      prefs: prefs,
      isAndroid: isAndroid,
      refreshNative: () async => refreshes.add(1),
    );
    return (snapshot, prefs, refreshes);
  }

  test('writes at most four sessions with busy flags and title fallback', () async {
    final (snapshot, prefs, refreshes) = await harness();
    await snapshot.update(
      sessions: [
        _session('s1', title: 'Fix auth', updated: 50),
        _session('s2', updated: 40),
        _session('s3', title: 'Refactor', updated: 30),
        _session('s4', title: 'Docs', updated: 20),
        _session('s5', title: 'Never shown', updated: 10),
      ],
      busySessions: {'s1', 's3'},
      connected: true,
    );

    final decoded =
        jsonDecode(prefs.getString(WidgetSessionSnapshot.prefsKey)!)
            as Map<String, dynamic>;
    expect(decoded['connected'], isTrue);
    final sessions = decoded['sessions'] as List;
    expect(sessions, hasLength(4));
    expect(sessions[0], {
      'id': 's1',
      'title': 'Fix auth',
      'busy': true,
      'updatedAt': 50,
    });
    expect((sessions[1] as Map)['title'], 'Untitled session');
    expect((sessions[1] as Map)['busy'], isFalse);
    // The snapshot never carries prompt text, tool input, or file paths:
    // exactly these four keys per session.
    for (final entry in sessions) {
      expect(
        (entry as Map).keys.toSet(),
        {'id', 'title', 'busy', 'updatedAt'},
      );
    }
    expect(refreshes, hasLength(1));
  });

  test('unchanged payloads neither rewrite nor redraw', () async {
    final (snapshot, _, refreshes) = await harness();
    final sessions = [_session('s1', title: 'Fix auth', updated: 50)];
    await snapshot.update(
      sessions: sessions,
      busySessions: const {},
      connected: true,
    );
    await snapshot.update(
      sessions: sessions,
      busySessions: const {},
      connected: true,
    );
    expect(refreshes, hasLength(1));

    await snapshot.update(
      sessions: sessions,
      busySessions: {'s1'},
      connected: true,
    );
    expect(refreshes, hasLength(2));
  });

  test('non-Android platforms write nothing', () async {
    final (snapshot, prefs, refreshes) = await harness(isAndroid: false);
    await snapshot.update(
      sessions: [_session('s1', title: 'Fix auth', updated: 50)],
      busySessions: const {},
      connected: true,
    );
    expect(prefs.getString(WidgetSessionSnapshot.prefsKey), isNull);
    expect(refreshes, isEmpty);
  });
}
