import 'package:flutter/material.dart';

import '../../domain/delimited_text.dart';
import '../../l10n/app_localizations.dart';
import 'markdown.dart';

/// Local, inert table reading with an independent original-source view.
class DelimitedFilePreview extends StatefulWidget {
  const DelimitedFilePreview({
    super.key,
    required this.text,
    required this.original,
    required this.separator,
    this.truncated = false,
  });
  final String text;
  final String original;
  final String separator;
  final bool truncated;

  @override
  State<DelimitedFilePreview> createState() => _DelimitedFilePreviewState();
}

class _DelimitedFilePreviewState extends State<DelimitedFilePreview> {
  bool _source = false;
  late DelimitedText _table = DelimitedText.parse(
    widget.text,
    widget.separator,
  );
  final _horizontal = ScrollController();

  @override
  void didUpdateWidget(DelimitedFilePreview oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.text != widget.text ||
        oldWidget.separator != widget.separator) {
      _table = DelimitedText.parse(widget.text, widget.separator);
    }
  }

  @override
  void dispose() {
    _horizontal.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final enabled = MarkdownInteractionScope.enabledOf(context);
    final failure = _table.failure;
    final available = !widget.truncated && failure == null;
    var source = widget.text;
    if (source.length > 200000) {
      var end = 200000;
      final unit = source.codeUnitAt(end - 1);
      if (unit >= 0xD800 && unit <= 0xDBFF) end--;
      source = source.substring(0, end);
    }
    final notice = widget.truncated
        ? l10n.filePreviewPartialSource
        : switch (failure) {
            DelimitedFailure.malformed => l10n.fileTableMalformed,
            DelimitedFailure.tooLarge => l10n.fileTableTooLarge,
            DelimitedFailure.tooWide => l10n.fileTableTooWide,
            DelimitedFailure.fieldTooLong => l10n.fileTableFieldTooLong,
            null => _table.hasMore ? l10n.fileTableMoreRows : null,
          };
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 8),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Wrap(
                spacing: 8,
                children: [
                  TextButton(
                    onPressed: enabled && available && _source
                        ? () => setState(() => _source = false)
                        : null,
                    style: TextButton.styleFrom(
                      minimumSize: const Size(48, 48),
                    ),
                    child: Text(l10n.fileTable),
                  ),
                  TextButton(
                    onPressed: enabled && available && !_source
                        ? () => setState(() => _source = true)
                        : null,
                    style: TextButton.styleFrom(
                      minimumSize: const Size(48, 48),
                    ),
                    child: Text(l10n.fileSource),
                  ),
                ],
              ),
              if (notice != null)
                Padding(
                  padding: const EdgeInsets.only(top: 8),
                  child: Text(notice),
                ),
              if (source.length < widget.text.length && (_source || !available))
                Text(l10n.fileSourceExcerpt),
              if (available && !_source)
                Text(l10n.fileTableRows(_table.rows.length, _table.columns)),
            ],
          ),
        ),
        Expanded(
          child: _source || !available
              ? SingleChildScrollView(
                  padding: const EdgeInsets.all(16),
                  child: CodeBlock(
                    code: source,
                    originalSource: widget.original,
                    initialWrap: true,
                  ),
                )
              : _table.rows.isEmpty
              ? Center(child: Text(l10n.fileTableEmpty))
              : Directionality(
                  textDirection: TextDirection.ltr,
                  child: Scrollbar(
                    controller: _horizontal,
                    thumbVisibility: true,
                    child: SingleChildScrollView(
                      controller: _horizontal,
                      scrollDirection: Axis.horizontal,
                      child: SizedBox(
                        width: 56 + _table.columns * 200.0,
                        child: Column(
                          children: [
                            _row(
                              context,
                              [
                                for (var i = 0; i < _table.columns; i++)
                                  l10n.fileTableColumn(i + 1),
                              ],
                              0,
                              header: true,
                            ),
                            Expanded(
                              child: SelectionArea(
                                child: ListView.builder(
                                  itemCount: _table.rows.length,
                                  itemBuilder: (context, index) => _row(
                                    context,
                                    _table.rows[index],
                                    index + 1,
                                  ),
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ),
                ),
        ),
      ],
    );
  }

  Widget _row(
    BuildContext context,
    List<String> cells,
    int number, {
    bool header = false,
  }) {
    final theme = Theme.of(context);
    return Container(
      decoration: BoxDecoration(
        color: header ? theme.colorScheme.surfaceContainerHigh : null,
        border: Border(
          bottom: BorderSide(color: theme.colorScheme.outlineVariant),
        ),
      ),
      child: IntrinsicHeight(
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            SizedBox(
              width: 56,
              child: Padding(
                padding: const EdgeInsets.all(8),
                child: Text(
                  header ? '' : '$number',
                  textAlign: TextAlign.right,
                ),
              ),
            ),
            for (var i = 0; i < _table.columns; i++)
              SizedBox(
                width: 200,
                child: Container(
                  constraints: const BoxConstraints(minHeight: 48),
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    border: Border(
                      left: BorderSide(color: theme.colorScheme.outlineVariant),
                    ),
                  ),
                  child: Semantics(
                    header: header,
                    child: Text(
                      i < cells.length ? cells[i] : '',
                      style: header
                          ? theme.textTheme.labelLarge
                          : theme.textTheme.bodyMedium,
                    ),
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }
}
