import 'dart:io';
import 'package:flutter_test/flutter_test.dart';
import '../tool/qa/fq3/common.dart';
import '../tool/qa/fq3_certify.dart' as runner;

void main() {
  test(
    'FQ3 refuses missing, standard-stream and unrelated lock descriptors',
    () {
      for (final descriptor in ['bad', '-1', '0', '1', '2', '999999']) {
        expect(
          () => runner.adoptReservation(descriptor),
          throwsA(anyOf(isA<ProbeFailure>(), isA<FileSystemException>())),
        );
      }
      expect(runner.inheritedReservation, isFalse);
    },
  );
}
