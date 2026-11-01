// KitRedact remembers recent results (launcher surfaces mask the same titles
// on every change). A remembered result must never outlive the secrets it
// was computed with.
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/kit/kit_redact.dart';

void main() {
  setUp(KitRedact.clearKnownSecrets);
  tearDown(KitRedact.clearKnownSecrets);

  test('a secret registered after a read is masked on the next read', () {
    const line = 'deploy with hunter2-password-xyz today';
    expect(KitRedact.text(line), line);
    KitRedact.registerKnownSecret('hunter2-password-xyz');
    expect(KitRedact.text(line), 'deploy with ${KitRedact.mask} today');
  });

  test('clearing the secrets clears what was remembered with them', () {
    const line = 'token-value-123456 in a title';
    KitRedact.registerKnownSecret('token-value-123456');
    expect(KitRedact.text(line), '${KitRedact.mask} in a title');
    KitRedact.clearKnownSecrets();
    expect(KitRedact.text(line), line);
  });

  test('repeated and long inputs mask exactly as a first read does', () {
    const key = 'sk-ant-api03-abcdefghijklmnopqrstuvwxyz0123456789';
    const short = 'Fix login with $key';
    final first = KitRedact.text(short);
    expect(first, isNot(contains(key)));
    for (var i = 0; i < 3; i++) {
      expect(KitRedact.text(short), first);
    }
    final long = '${'x' * 3000} $key';
    expect(KitRedact.text(long), isNot(contains(key)));
    // More distinct inputs than are remembered still mask correctly.
    for (var i = 0; i < 700; i++) {
      expect(KitRedact.text('row $i $key'), isNot(contains(key)));
    }
    expect(KitRedact.text(short), first);
  });
}
