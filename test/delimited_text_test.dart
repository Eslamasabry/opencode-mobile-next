import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/delimited_text.dart';

void main() {
  test(
    'CSV preserves quoted separators, quotes, multiline fields and trailing cells',
    () {
      final data = DelimitedText.parse(
        '\uFEFFname,value,\r\n"a,b","say ""hi""",\r\n"line\r\nbreak",2,',
        ',',
      );
      expect(data.failure, isNull);
      expect(data.rows, [
        ['name', 'value', ''],
        ['a,b', 'say "hi"', ''],
        ['line\r\nbreak', '2', ''],
      ]);
      expect(data.hasMore, isFalse);
    },
  );
  test(
    'TSV retains ragged rows and treats formulas and markup as literal data',
    () {
      final data = DelimitedText.parse(
        '=SUM(A1)\t<script>\n# Heading\n"a\tb"\t',
        '\t',
      );
      expect(data.rows, [
        ['=SUM(A1)', '<script>'],
        ['# Heading'],
        ['a\tb', ''],
      ]);
      expect(data.columns, 2);
    },
  );
  test('blank input and trailing record terminator do not invent rows', () {
    expect(DelimitedText.parse('', ',').rows, isEmpty);
    expect(DelimitedText.parse('\uFEFF', ',').rows, isEmpty);
    expect(DelimitedText.parse('a\n', ',').rows, [
      ['a'],
    ]);
    expect(DelimitedText.parse('\n\n', ',').rows, [
      [''],
      [''],
    ]);
  });
  test('malformed quotes fall back without fabricated cells', () {
    for (final text in ['"open', 'a"b,c', '"a"suffix,b']) {
      final data = DelimitedText.parse(text, ',');
      expect(data.failure, DelimitedFailure.malformed);
      expect(data.rows, isEmpty);
    }
  });
  test('bounds distinguish row prefix from unavailable table', () {
    final prefix = DelimitedText.parse(List.filled(201, 'a,b').join('\n'), ',');
    expect(prefix.rows.length, 200);
    expect(prefix.hasMore, isTrue);
    expect(
      DelimitedText.parse(List.filled(200, 'a,b').join('\n'), ',').hasMore,
      isFalse,
    );
    expect(
      DelimitedText.parse('x' * (256 * 1024 + 1), ',').failure,
      DelimitedFailure.tooLarge,
    );
    expect(
      DelimitedText.parse('é' * (128 * 1024 + 1), ',').failure,
      DelimitedFailure.tooLarge,
    );
    expect(
      DelimitedText.parse('x' * 4097, ',').failure,
      DelimitedFailure.fieldTooLong,
    );
    expect(
      DelimitedText.parse(List.filled(33, 'x').join(','), ',').failure,
      DelimitedFailure.tooWide,
    );
  });
  test(
    'explicit incompatible MIME cannot become a table from its filename',
    () {
      expect(DelimitedText.separator('report.CSV', 'text/plain'), ',');
      expect(
        DelimitedText.separator('report', 'text/tab-separated-values'),
        '\t',
      );
      expect(DelimitedText.separator('report.csv', 'application/pdf'), isNull);
      expect(DelimitedText.separator('report.txt', 'text/plain'), isNull);
    },
  );
}
