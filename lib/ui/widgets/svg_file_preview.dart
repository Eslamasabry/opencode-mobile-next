import 'package:flutter/material.dart';
import 'package:flutter_svg/flutter_svg.dart';

import '../../domain/static_svg.dart';
import '../../l10n/app_localizations.dart';
import 'markdown.dart';

class SvgFilePreview extends StatefulWidget {
  const SvgFilePreview({
    super.key,
    required this.source,
    required this.original,
    this.truncated = false,
  });
  final String source;
  final String original;
  final bool truncated;
  @override
  State<SvgFilePreview> createState() => _SvgFilePreviewState();
}

class _SvgFilePreviewState extends State<SvgFilePreview> {
  late StaticSvg? _svg = widget.truncated
      ? null
      : StaticSvg.parse(widget.source);
  bool _source = false;
  @override
  void didUpdateWidget(SvgFilePreview oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.source != widget.source ||
        oldWidget.truncated != widget.truncated) {
      _svg = widget.truncated ? null : StaticSvg.parse(widget.source);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final enabled = MarkdownInteractionScope.enabledOf(context);
    final svg = _svg;
    var source = widget.source;
    if (source.length > 200000) {
      var end = 200000;
      final unit = source.codeUnitAt(end - 1);
      if (unit >= 0xD800 && unit <= 0xDBFF) end--;
      source = source.substring(0, end);
    }
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Padding(
          padding: const EdgeInsets.all(12),
          child: Wrap(
            spacing: 8,
            children: [
              TextButton(
                onPressed: enabled && svg != null && _source
                    ? () => setState(() => _source = false)
                    : null,
                style: TextButton.styleFrom(minimumSize: const Size(48, 48)),
                child: Text(l10n.fileImage),
              ),
              TextButton(
                onPressed: enabled && svg != null && !_source
                    ? () => setState(() => _source = true)
                    : null,
                style: TextButton.styleFrom(minimumSize: const Size(48, 48)),
                child: Text(l10n.fileSource),
              ),
            ],
          ),
        ),
        if (svg == null)
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Text(l10n.fileSvgUnsupported),
          ),
        if (widget.truncated)
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Text(l10n.filePreviewPartialSource),
          ),
        if (source.length < widget.source.length && (svg == null || _source))
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Text(l10n.fileSourceExcerpt),
          ),
        Expanded(
          child: svg == null || _source
              ? SingleChildScrollView(
                  padding: const EdgeInsets.all(16),
                  child: CodeBlock(
                    code: source,
                    originalSource: widget.original,
                    language: 'xml',
                    initialWrap: true,
                  ),
                )
              : ColoredBox(
                  color: Colors.white,
                  child: InteractiveViewer(
                    minScale: .5,
                    maxScale: 5,
                    child: Center(
                      child: AspectRatio(
                        aspectRatio: svg.width / svg.height,
                        // SVG coordinates and its default text direction are
                        // authored content, independent of the app chrome.
                        child: Directionality(
                          textDirection: TextDirection.ltr,
                          child: SvgPicture.string(
                            svg.source,
                            fit: BoxFit.contain,
                            semanticsLabel: l10n.fileImage,
                            errorBuilder: (_, _, _) =>
                                Center(child: Text(l10n.fileSvgUnsupported)),
                          ),
                        ),
                      ),
                    ),
                  ),
                ),
        ),
      ],
    );
  }
}
