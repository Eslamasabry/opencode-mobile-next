/// Immutable presentation values; constructors do not validate agent input.
sealed class GenUiNode {
  const GenUiNode();
}

final class GenUiText extends GenUiNode {
  const GenUiText({required this.text});
  final String text;
}

final class GenUiKeyValueRow {
  const GenUiKeyValueRow({required this.key, required this.value});
  final String key;
  final String value;
}

final class GenUiKeyValue extends GenUiNode {
  GenUiKeyValue({required List<GenUiKeyValueRow> rows})
    : rows = List.unmodifiable(rows);
  final List<GenUiKeyValueRow> rows;
}

enum GenUiListStyle { bullet, check }

final class GenUiListItem {
  const GenUiListItem({required this.text, this.done = false});
  final String text;
  final bool done;
}

final class GenUiList extends GenUiNode {
  GenUiList({required List<GenUiListItem> items, required this.style})
    : items = List.unmodifiable(items);
  final List<GenUiListItem> items;
  final GenUiListStyle style;
}

final class GenUiTable extends GenUiNode {
  GenUiTable({required List<String> columns, required List<List<String>> rows})
    : columns = List.unmodifiable(columns),
      rows = List.unmodifiable(
        rows.map((row) => List<String>.unmodifiable(row)),
      );
  final List<String> columns;
  final List<List<String>> rows;
}

enum GenUiChartKind { bar, line }

final class GenUiChartSeries {
  GenUiChartSeries({required this.name, required List<double> values})
    : values = List.unmodifiable(values);
  final String name;
  final List<double> values;
}

final class GenUiChart extends GenUiNode {
  GenUiChart({
    required this.kind,
    this.unit,
    required List<String> labels,
    required List<GenUiChartSeries> series,
  }) : labels = List.unmodifiable(labels),
       series = List.unmodifiable(series);
  final GenUiChartKind kind;
  final String? unit;
  final List<String> labels;
  final List<GenUiChartSeries> series;
}

final class GenUiCode extends GenUiNode {
  const GenUiCode({this.language, required this.text});
  final String? language;
  final String text;
}

final class GenUiDiffFile {
  const GenUiDiffFile({
    required this.path,
    required this.added,
    required this.removed,
  });
  final String path;
  final int added;
  final int removed;
}

final class GenUiDiffStat extends GenUiNode {
  GenUiDiffStat({required List<GenUiDiffFile> files})
    : files = List.unmodifiable(files);
  final List<GenUiDiffFile> files;
}

final class GenUiProgress extends GenUiNode {
  const GenUiProgress({required this.label, required this.value});
  final String label;
  final double value;
}

enum GenUiCalloutTone { info, warning, success }

final class GenUiCallout extends GenUiNode {
  const GenUiCallout({required this.tone, required this.text});
  final GenUiCalloutTone tone;
  final String text;
}

final class GenUiLink extends GenUiNode {
  const GenUiLink({required this.label, required this.url});
  final String label;

  /// Inert text. Open only through the app's external-link gate.
  final String url;
}
