import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  test('native share delivery waits for Dart readiness acknowledgment', () {
    final source = File(
      'android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/'
      'MainActivity.kt',
    ).readAsStringSync();

    expect(source, contains('private var shareDartReady = false'));
    expect(source, contains('shareChannel = null'));
    expect(source, contains('shareDartReady = false'));

    final consume = source.indexOf('"consumeSharedText"');
    final ready = source.indexOf('shareDartReady = true', consume);
    final consumeClear = source.indexOf('pendingSharedText = null', consume);
    expect(consume, greaterThanOrEqualTo(0));
    expect(ready, greaterThan(consume));
    expect(consumeClear, greaterThan(ready));

    final newIntent = source.indexOf('override fun onNewIntent');
    final dispatch = source.indexOf(
      'if (shareDartReady && channel != null && text != null)',
      newIntent,
    );
    final invoke = source.indexOf(
      'channel.invokeMethod("shared", text)',
      dispatch,
    );
    expect(newIntent, greaterThanOrEqualTo(0));
    expect(dispatch, greaterThan(newIntent));
    expect(invoke, greaterThan(dispatch));
  });
}
