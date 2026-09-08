import 'dart:convert';

/// A bounded local table projection. Cells are data, never Markdown or formulas.
class DelimitedText {
  const DelimitedText._(this.rows, this.hasMore, this.failure);

  static const maxBytes = 256 * 1024;
  static const maxRows = 200;
  static const maxColumns = 32;
  static const maxFieldLength = 4096;

  final List<List<String>> rows;
  final bool hasMore;
  final DelimitedFailure? failure;
  int get columns =>
      rows.fold(0, (count, row) => row.length > count ? row.length : count);

  static String? separator(String name, String? mime) {
    if (mime == 'text/csv') return ',';
    if (mime == 'text/tab-separated-values') return '\t';
    // A filename must not override an explicit incompatible media type.
    if (mime != null &&
        mime != 'text/plain' &&
        mime != 'application/octet-stream') {
      return null;
    }
    final lower = name.toLowerCase();
    if (lower.endsWith('.csv')) return ',';
    if (lower.endsWith('.tsv')) return '\t';
    return null;
  }

  factory DelimitedText.parse(String source, String separator) {
    DelimitedText fail(DelimitedFailure reason) =>
        DelimitedText._(const [], false, reason);
    if (source.length > maxBytes || utf8.encode(source).length > maxBytes) {
      return fail(DelimitedFailure.tooLarge);
    }
    if (source.isEmpty || source == '\uFEFF') {
      return const DelimitedText._([], false, null);
    }
    final rows = <List<String>>[];
    var row = <String>[];
    var field = StringBuffer();
    var quoted = false;
    var closedQuote = false;
    var endedRecord = false;
    var i = source.startsWith('\uFEFF') ? 1 : 0;
    while (i < source.length) {
      final char = source[i];
      endedRecord = false;
      if (quoted) {
        if (char == '"') {
          if (i + 1 < source.length && source[i + 1] == '"') {
            field.write('"');
            i++;
          } else {
            quoted = false;
            closedQuote = true;
          }
        } else {
          field.write(char);
        }
      } else if (char == separator || char == '\r' || char == '\n') {
        row.add(field.toString());
        if (row.length > maxColumns) return fail(DelimitedFailure.tooWide);
        field = StringBuffer();
        closedQuote = false;
        if (char != separator) {
          if (char == '\r' && i + 1 < source.length && source[i + 1] == '\n') {
            i++;
          }
          rows.add(List.unmodifiable(row));
          row = [];
          endedRecord = true;
          if (rows.length == maxRows) {
            return DelimitedText._(
              List.unmodifiable(rows),
              i + 1 < source.length,
              null,
            );
          }
        }
      } else if (char == '"' && field.isEmpty && !closedQuote) {
        quoted = true;
      } else {
        if (closedQuote || char == '"') return fail(DelimitedFailure.malformed);
        field.write(char);
      }
      if (field.length > maxFieldLength) {
        return fail(DelimitedFailure.fieldTooLong);
      }
      i++;
    }
    if (quoted) return fail(DelimitedFailure.malformed);
    if (!endedRecord) {
      row.add(field.toString());
      if (row.length > maxColumns) return fail(DelimitedFailure.tooWide);
      rows.add(List.unmodifiable(row));
    }
    return DelimitedText._(List.unmodifiable(rows), false, null);
  }
}

enum DelimitedFailure { malformed, tooLarge, tooWide, fieldTooLong }
