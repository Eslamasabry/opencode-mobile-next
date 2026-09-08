import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../l10n/app_localizations.dart';
import '../../platform/local_pdf.dart';
import '../../platform/platform_capabilities.dart';
import 'markdown.dart';
import '../app_iconography.dart';

class PdfFilePreview extends StatefulWidget {
  const PdfFilePreview({super.key, required this.bytes});
  final Uint8List bytes;
  @override
  State<PdfFilePreview> createState() => _PdfFilePreviewState();
}

class _PdfFilePreviewState extends State<PdfFilePreview>
    with WidgetsBindingObserver {
  PdfPageImage? _image;
  String? _error;
  String? _request;
  int _wanted = 0;
  bool _enabled = true, _started = false;
  late bool _foreground;
  @override
  void initState() {
    super.initState();
    _foreground =
        WidgetsBinding.instance.lifecycleState == AppLifecycleState.resumed;
    WidgetsBinding.instance.addObserver(this);
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _enabled = MarkdownInteractionScope.enabledOf(context);
    if (!_enabled || !_foreground) {
      _cancel();
      _error = 'cancelled';
      _started = true;
    } else if (!_started) {
      _started = true;
      _load(0);
    }
  }

  @override
  void didUpdateWidget(PdfFilePreview oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (!identical(oldWidget.bytes, widget.bytes)) {
      _cancel();
      _image = null;
      _wanted = 0;
      if (_enabled && _foreground) {
        _load(0);
      } else {
        _error = 'cancelled';
      }
    }
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    final foreground = state == AppLifecycleState.resumed;
    if (_foreground == foreground) return;
    setState(() {
      _foreground = foreground;
      if (!foreground && _request != null) {
        _cancel();
        _error = 'cancelled';
      }
    });
  }

  void _cancel() {
    final id = _request;
    _request = null;
    if (id != null) unawaited(LocalPdf.cancel(id));
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _cancel();
    super.dispose();
  }

  Future<void> _load(int page) async {
    if (!_enabled || !_foreground || _request != null) return;
    if (!platformCapabilities.supportsLocalPdf) {
      _error = 'unavailable';
      return;
    }
    _wanted = page;
    final id = LocalPdf.requestID();
    _request = id;
    _error = null;
    try {
      final image = await LocalPdf.render(widget.bytes, page, id);
      if (!mounted || _request != id) return;
      setState(() {
        _request = null;
        _image = image;
      });
    } catch (error) {
      if (!mounted || _request != id) return;
      setState(() {
        _request = null;
        _error = error is PlatformException ? error.code : 'unavailable';
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final image = _image;
    final loading = _request != null;
    final error = _error;
    final notice = switch (error) {
      'encrypted' => l10n.filePdfEncrypted,
      'limit' => l10n.filePdfLimit,
      'unavailable' => l10n.filePdfUnavailable,
      'cancelled' => l10n.filePdfCancelled,
      null => null,
      _ => l10n.filePdfFailed,
    };
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Padding(
          padding: const EdgeInsets.all(12),
          child: Wrap(
            spacing: 8,
            runSpacing: 8,
            crossAxisAlignment: WrapCrossAlignment.center,
            children: [
              if (image != null) ...[
                Text(l10n.filePdfPage(image.page + 1, image.pageCount)),
                TextButton.icon(
                  onPressed:
                      _enabled && _foreground && !loading && image.page > 0
                      ? () => setState(() => unawaited(_load(image.page - 1)))
                      : null,
                  icon: const Icon(AppIconography.chevronLeft),
                  label: Text(l10n.filePrevious),
                  style: TextButton.styleFrom(minimumSize: const Size(48, 48)),
                ),
                TextButton.icon(
                  onPressed:
                      _enabled &&
                          _foreground &&
                          !loading &&
                          image.page + 1 < image.pageCount &&
                          image.page + 1 < LocalPdf.maxPages
                      ? () => setState(() => unawaited(_load(image.page + 1)))
                      : null,
                  icon: const Icon(AppIconography.chevronRight),
                  label: Text(l10n.fileNext),
                  style: TextButton.styleFrom(minimumSize: const Size(48, 48)),
                ),
              ],
              if (loading)
                TextButton(
                  onPressed: () => setState(() {
                    _cancel();
                    _error = 'cancelled';
                  }),
                  style: TextButton.styleFrom(minimumSize: const Size(48, 48)),
                  child: Text(l10n.fileCancel),
                ),
              if (_enabled &&
                  _foreground &&
                  error != null &&
                  error != 'unavailable' &&
                  error != 'encrypted' &&
                  error != 'limit')
                TextButton(
                  onPressed: () => setState(() => unawaited(_load(_wanted))),
                  style: TextButton.styleFrom(minimumSize: const Size(48, 48)),
                  child: Text(l10n.markdownCopyRetry),
                ),
            ],
          ),
        ),
        if (notice != null)
          Padding(padding: const EdgeInsets.all(16), child: Text(notice)),
        if (image != null && image.pageCount > LocalPdf.maxPages)
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Text(l10n.filePdfPageLimit),
          ),
        Expanded(
          child: loading
              ? const Center(child: CircularProgressIndicator())
              : image == null
              ? const SizedBox.shrink()
              : InteractiveViewer(
                  key: ValueKey(image.page),
                  minScale: .5,
                  maxScale: 5,
                  child: Center(
                    child: Image.memory(
                      image.png,
                      fit: BoxFit.contain,
                      gaplessPlayback: false,
                      errorBuilder: (_, _, _) => Text(l10n.filePdfFailed),
                    ),
                  ),
                ),
        ),
      ],
    );
  }
}
