part of 'gen_ui_codec.dart';

GenUiOption _toOption(Map<String, dynamic> o) => GenUiOption(
  id: o['id'] as String,
  label: o['label'] as String,
  detail: o['detail'] as String?,
);
GenUiField _toField(Map<String, dynamic> f) => GenUiField(
  id: f['id'] as String,
  label: f['label'] as String,
  type: GenUiFieldType.values.byName(f['type'] as String),
  required: f['required'] as bool,
  placeholder: f['placeholder'] as String?,
  defaultValue: f['default'],
  options: ((f['options'] as List?) ?? [])
      .map((o) => _toOption(o as Map<String, dynamic>))
      .toList(),
  min: (f['min'] as num?)?.toDouble(),
  max: (f['max'] as num?)?.toDouble(),
);
GenUiAsk _toAsk(Map<String, dynamic> a) => switch (a['kind']) {
  'choice' => GenUiChoiceAsk(
    options: (a['options'] as List)
        .map((o) => _toOption(o as Map<String, dynamic>))
        .toList(),
    multi: a['multi'] as bool,
  ),
  'form' => GenUiFormAsk(
    fields: (a['fields'] as List)
        .map((f) => _toField(f as Map<String, dynamic>))
        .toList(),
    submitLabel: a['submitLabel'] as String?,
  ),
  'confirm' => GenUiConfirmAsk(
    confirmLabel: a['confirmLabel'] as String?,
    cancelLabel: a['cancelLabel'] as String?,
    tone: GenUiConfirmTone.values.byName(a['tone'] as String),
  ),
  'photo' => GenUiPhotoAsk(
    purpose: a['purpose'] as String,
    max: a['max'] as int,
  ),
  _ => throw const _Invalid(),
};
GenUiNode _toNode(Map<String, dynamic> n) => switch (n['type']) {
  'text' => GenUiText(text: n['text'] as String),
  'keyValue' => GenUiKeyValue(
    rows: (n['rows'] as List)
        .map(
          (r) => GenUiKeyValueRow(
            key: r['key'] as String,
            value: r['value'] as String,
          ),
        )
        .toList(),
  ),
  'list' => GenUiList(
    items: (n['items'] as List)
        .map(
          (r) =>
              GenUiListItem(text: r['text'] as String, done: r['done'] as bool),
        )
        .toList(),
    style: GenUiListStyle.values.byName(n['style'] as String),
  ),
  'table' => GenUiTable(
    columns: (n['columns'] as List).cast<String>(),
    rows: (n['rows'] as List).map((r) => (r as List).cast<String>()).toList(),
  ),
  'chart' => GenUiChart(
    kind: GenUiChartKind.values.byName(n['kind'] as String),
    unit: n['unit'] as String?,
    labels: (n['labels'] as List).cast<String>(),
    series: (n['series'] as List)
        .map(
          (r) => GenUiChartSeries(
            name: r['name'] as String,
            values: (r['values'] as List)
                .map((v) => (v as num).toDouble())
                .toList(),
          ),
        )
        .toList(),
  ),
  'code' => GenUiCode(
    language: n['language'] as String?,
    text: n['text'] as String,
  ),
  'diffStat' => GenUiDiffStat(
    files: (n['files'] as List)
        .map(
          (r) => GenUiDiffFile(
            path: r['path'] as String,
            added: r['added'] as int,
            removed: r['removed'] as int,
          ),
        )
        .toList(),
  ),
  'progress' => GenUiProgress(
    label: n['label'] as String,
    value: (n['value'] as num).toDouble(),
  ),
  'callout' => GenUiCallout(
    tone: GenUiCalloutTone.values.byName(n['tone'] as String),
    text: n['text'] as String,
  ),
  'link' => GenUiLink(label: n['label'] as String, url: n['url'] as String),
  _ => throw const _Invalid(),
};
