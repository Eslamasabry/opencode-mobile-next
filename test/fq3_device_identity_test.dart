import 'package:flutter_test/flutter_test.dart';
import '../tool/qa/fq3/device.dart';

void main() {
  test('owned process identity uses kernel start time with spaces in comm', () {
    String stat(String name, String started) =>
        '42 ($name) S ${List.filled(18, '0').join(' ')} $started 0';
    expect(PhoneRuntime.startIdentity(stat('worker (child)', '987')), '987');
    expect(PhoneRuntime.startIdentity(stat('renamed worker', '987')), '987');
    expect(PhoneRuntime.startIdentity(stat('worker', '988')), isNot('987'));
    expect(PhoneRuntime.startIdentity(''), isNull);
    expect(PhoneRuntime.startIdentity('42 (worker) S 1'), isNull);
  });
  test(
    'shell quoting keeps metacharacters literal through nested sh commands',
    () {
      expect(quote("a'b\n\$(id)"), "'a'\\''b\n\$(id)'");
    },
  );
}
