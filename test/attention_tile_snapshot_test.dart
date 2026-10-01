import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/background/attention_tile_snapshot.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  Future<(AttentionTileSnapshot, SharedPreferences)> harness({
    bool isAndroid = true,
    Map<String, Object> initial = const {},
    Future<void> Function()? refreshWidget,
  }) async {
    SharedPreferences.setMockInitialValues(initial);
    final prefs = await SharedPreferences.getInstance();
    return (
      AttentionTileSnapshot(
        prefs: prefs,
        isAndroid: isAndroid,
        refreshWidget: refreshWidget,
        now: () => DateTime.fromMillisecondsSinceEpoch(1_700_000_000_000),
      ),
      prefs,
    );
  }

  Map<String, dynamic> stored(SharedPreferences prefs) =>
      jsonDecode(prefs.getString(AttentionTileSnapshot.prefsKey)!)
          as Map<String, dynamic>;

  test('writes the count, its owner and a timestamp — nothing else', () async {
    final (tile, prefs) = await harness();
    await tile.update(pendingCount: 2, profileID: 'server-1');
    expect(stored(prefs), {
      'pendingCount': 2,
      'profileID': 'server-1',
      'updatedAt': 1_700_000_000_000,
    });
  });

  test('redraws the home widget after a write and after a clear', () async {
    var redraws = 0;
    final (tile, _) = await harness(refreshWidget: () async => redraws++);
    await tile.update(pendingCount: 2, profileID: 'server-1');
    expect(redraws, 1);
    await tile.update(pendingCount: 2, profileID: 'server-1');
    expect(redraws, 1);
    await tile.clear();
    expect(redraws, 2);
  });

  test('rewrites only when the count or owner changes', () async {
    final (tile, prefs) = await harness();
    await tile.update(pendingCount: 2, profileID: 'server-1');
    await prefs.remove(AttentionTileSnapshot.prefsKey);
    // Same count, same owner: the writer believes its own last write.
    await tile.update(pendingCount: 2, profileID: 'server-1');
    expect(prefs.getString(AttentionTileSnapshot.prefsKey), isNull);

    await tile.update(pendingCount: 0, profileID: 'server-1');
    expect(stored(prefs)['pendingCount'], 0);

    await tile.update(pendingCount: 0, profileID: 'server-2');
    expect(stored(prefs)['profileID'], 'server-2');
  });

  test('a negative count is stored as zero', () async {
    final (tile, prefs) = await harness();
    await tile.update(pendingCount: -3, profileID: 'server-1');
    expect(stored(prefs)['pendingCount'], 0);
  });

  test('clear drops the cache so the tile shows no count', () async {
    final (tile, prefs) = await harness();
    await tile.update(pendingCount: 2, profileID: 'server-1');
    await tile.clear();
    expect(prefs.getString(AttentionTileSnapshot.prefsKey), isNull);
    // A later identical count is written again: the cache was really gone.
    await tile.update(pendingCount: 2, profileID: 'server-1');
    expect(stored(prefs)['pendingCount'], 2);
  });

  group('clearForProfile', () {
    final record = jsonEncode({
      'pendingCount': 2,
      'profileID': 'server-1',
      'updatedAt': 1,
    });

    test('drops the deleted profile\'s count', () async {
      final (tile, prefs) = await harness(
        initial: {AttentionTileSnapshot.prefsKey: record},
      );
      expect(
        await tile.clearForProfile('server-1'),
        AttentionTileClear.cleared,
      );
      expect(prefs.getString(AttentionTileSnapshot.prefsKey), isNull);
    });

    test('keeps another profile\'s count', () async {
      final (tile, prefs) = await harness(
        initial: {AttentionTileSnapshot.prefsKey: record},
      );
      expect(
        await tile.clearForProfile('server-2'),
        AttentionTileClear.nothingToClear,
      );
      expect(prefs.getString(AttentionTileSnapshot.prefsKey), record);
    });

    test('reports nothing to clear when nothing is cached', () async {
      final (tile, _) = await harness();
      expect(
        await tile.clearForProfile('server-1'),
        AttentionTileClear.nothingToClear,
      );
    });

    test('runs off Android too: the cached value is the artifact', () async {
      final (tile, prefs) = await harness(
        isAndroid: false,
        initial: {AttentionTileSnapshot.prefsKey: record},
      );
      expect(
        await tile.clearForProfile('server-1'),
        AttentionTileClear.cleared,
      );
      expect(prefs.getString(AttentionTileSnapshot.prefsKey), isNull);
    });
  });

  test('non-Android platforms write nothing', () async {
    final (tile, prefs) = await harness(isAndroid: false);
    await tile.update(pendingCount: 2, profileID: 'server-1');
    expect(prefs.getString(AttentionTileSnapshot.prefsKey), isNull);
  });
}
