import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../domain/context_capsule.dart';
import '../../l10n/app_localizations.dart';
import '../widgets/file_preview.dart';

/// Edits a private working copy. Only Apply returns content to the caller.
class ContextCapsuleScreen extends StatefulWidget {
  const ContextCapsuleScreen({
    super.key,
    required this.sessionTitle,
    required this.scopeChanges,
    required this.isCurrent,
    this.pickImage,
  });

  final String sessionTitle;
  final Listenable scopeChanges;
  final bool Function() isCurrent;
  final Future<PromptAttachment?> Function(List<PromptAttachment>)? pickImage;

  @override
  State<ContextCapsuleScreen> createState() => _ContextCapsuleScreenState();
}

class _Excerpt {
  _Excerpt(String name) : label = TextEditingController(text: name);
  final TextEditingController label;
  final text = TextEditingController();
  void dispose() {
    label.dispose();
    text.dispose();
  }
}

class _ContextCapsuleScreenState extends State<ContextCapsuleScreen> {
  final _excerpts = <_Excerpt>[];
  final _images = <PromptAttachment>[];
  bool _invalid = false;
  bool _busy = false;
  String? _error;
  AppLocalizations get l10n =>
      lookupAppLocalizations(Localizations.localeOf(context));

  @override
  void initState() {
    super.initState();
    _invalid = !widget.isCurrent();
    widget.scopeChanges.addListener(_scopeChanged);
  }

  @override
  void didUpdateWidget(covariant ContextCapsuleScreen oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (!identical(widget.scopeChanges, oldWidget.scopeChanges)) {
      oldWidget.scopeChanges.removeListener(_scopeChanged);
      widget.scopeChanges.addListener(_scopeChanged);
      _invalid = true;
    }
    if (oldWidget.sessionTitle != widget.sessionTitle) _invalid = true;
    _scopeChanged();
  }

  void _scopeChanged() {
    if (!mounted || _invalid || widget.isCurrent()) return;
    setState(() {
      _invalid = true;
      _images.clear();
    });
  }

  bool _check() {
    _scopeChanged();
    return mounted && !_invalid;
  }

  @override
  void dispose() {
    widget.scopeChanges.removeListener(_scopeChanged);
    for (final excerpt in _excerpts) {
      excerpt.dispose();
    }
    super.dispose();
  }

  void _add(String label) {
    if (!_check() || _busy || _excerpts.length >= 8) return;
    setState(() => _excerpts.add(_Excerpt(label)));
  }

  Future<void> _paste(_Excerpt excerpt) async {
    if (!_check()) return;
    final original = excerpt.text.value;
    try {
      final data = await Clipboard.getData(Clipboard.kTextPlain);
      if (!_check() || !_excerpts.contains(excerpt) || data?.text == null) {
        return;
      }
      if (excerpt.text.value != original) return;
      final incoming = data!.text!;
      if (excerpt.text.text.length + incoming.length > 16000) {
        setState(() => _error = l10n.capsuleTextLimit);
        return;
      }
      final value = excerpt.text.value;
      final selection = value.selection;
      final start = selection.isValid ? selection.start : value.text.length;
      final end = selection.isValid ? selection.end : value.text.length;
      excerpt.text.value = TextEditingValue(
        text: value.text.replaceRange(start, end, incoming),
        selection: TextSelection.collapsed(offset: start + incoming.length),
      );
      setState(() => _error = null);
    } catch (_) {
      if (_check()) setState(() => _error = l10n.capsulePasteFailed);
    }
  }

  Future<void> _pick() async {
    if (!_check() || _busy || widget.pickImage == null) return;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final image = await widget.pickImage!(List.unmodifiable(_images));
      if (!_check() || image == null) return;
      if (!image.mime.startsWith('image/')) {
        setState(() => _error = l10n.capsuleImagesOnly);
        return;
      }
      setState(() => _images.add(image));
    } catch (_) {
      if (_check()) setState(() => _error = l10n.capsuleImageFailed);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  void _apply() {
    if (!_check() || _busy) return;
    final sections = <String>[];
    for (final excerpt in _excerpts) {
      if (excerpt.text.text.trim().isEmpty) continue;
      final label = excerpt.label.text.trim();
      sections.add(
        '${label.isEmpty ? l10n.capsuleNote : label}\n${excerpt.text.text}',
      );
    }
    if (sections.isEmpty && _images.isEmpty) return;
    final text = sections.isEmpty
        ? ''
        : '${l10n.capsuleTitle}\n\n${sections.join('\n\n')}';
    if (text.length > 32000) {
      setState(() => _error = l10n.capsuleTextLimit);
      return;
    }
    Navigator.of(context).pop(ContextCapsule(text: text, images: _images));
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final hasContent =
        _images.isNotEmpty ||
        _excerpts.any((e) => e.text.text.trim().isNotEmpty);
    return Scaffold(
      appBar: AppBar(title: Text(l10n.capsuleTitle)),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(20),
          children: [
            Icon(
              Icons.layers_outlined,
              size: 36,
              color: theme.colorScheme.primary,
            ),
            const SizedBox(height: 12),
            Text(widget.sessionTitle, style: theme.textTheme.titleLarge),
            const SizedBox(height: 8),
            Text(l10n.capsuleDescription),
            const SizedBox(height: 20),
            if (_invalid)
              Text(
                l10n.capsuleScopeChanged,
                style: TextStyle(color: theme.colorScheme.error),
              ),
            if (!_invalid) ...[
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: [
                  for (final label in [
                    l10n.capsuleNote,
                    l10n.capsuleError,
                    l10n.capsuleCode,
                  ])
                    OutlinedButton.icon(
                      onPressed: _busy || _excerpts.length >= 8
                          ? null
                          : () => _add(label),
                      icon: const Icon(Icons.add, size: 18),
                      label: Text(label),
                    ),
                ],
              ),
              const SizedBox(height: 12),
              for (final excerpt in _excerpts)
                Card(
                  margin: const EdgeInsets.only(bottom: 16),
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        TextField(
                          controller: excerpt.label,
                          maxLength: 80,
                          decoration: InputDecoration(
                            labelText: l10n.capsuleLabel,
                            counterText: '',
                          ),
                          enabled: !_busy,
                        ),
                        const SizedBox(height: 12),
                        TextField(
                          controller: excerpt.text,
                          minLines: 3,
                          maxLines: 8,
                          maxLength: 16000,
                          decoration: InputDecoration(
                            labelText: l10n.capsuleExcerpt,
                            alignLabelWithHint: true,
                            counterText: '',
                          ),
                          enabled: !_busy,
                          onChanged: (_) => setState(() {}),
                        ),
                        Wrap(
                          alignment: WrapAlignment.end,
                          spacing: 8,
                          children: [
                            TextButton.icon(
                              onPressed: _busy ? null : () => _paste(excerpt),
                              icon: const Icon(Icons.content_paste, size: 18),
                              label: Text(l10n.capsulePaste),
                            ),
                            TextButton.icon(
                              onPressed: _busy
                                  ? null
                                  : () {
                                      setState(() => _excerpts.remove(excerpt));
                                      excerpt.dispose();
                                    },
                              icon: const Icon(Icons.close, size: 18),
                              label: Text(l10n.capsuleRemove),
                            ),
                          ],
                        ),
                      ],
                    ),
                  ),
                ),
              if (widget.pickImage != null)
                OutlinedButton.icon(
                  onPressed: _busy ? null : _pick,
                  icon: const Icon(Icons.add_photo_alternate_outlined),
                  label: Text(l10n.capsuleAddImage),
                )
              else
                Text(l10n.capsuleTextOnly),
              for (final image in _images)
                Card(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      ListTile(
                        leading: const Icon(Icons.image_outlined),
                        title: Text(image.filename),
                        subtitle: Text(l10n.capsulePreview),
                        onTap: () => showFilePreviewSheet(
                          context,
                          FilePreviewData.fromDataUrl(
                            name: image.filename,
                            mimeType: image.mime,
                            url: image.url,
                          ),
                        ),
                      ),
                      Align(
                        alignment: AlignmentDirectional.centerEnd,
                        child: TextButton(
                          onPressed: _busy
                              ? null
                              : () => setState(() => _images.remove(image)),
                          child: Text(l10n.capsuleRemove),
                        ),
                      ),
                    ],
                  ),
                ),
              if (_busy)
                const Padding(
                  padding: EdgeInsets.all(16),
                  child: Center(child: CircularProgressIndicator()),
                ),
              if (_error != null)
                Padding(
                  padding: const EdgeInsets.symmetric(vertical: 12),
                  child: Text(
                    _error!,
                    style: TextStyle(color: theme.colorScheme.error),
                  ),
                ),
              const SizedBox(height: 24),
              FilledButton.icon(
                onPressed: _busy || !hasContent ? null : _apply,
                icon: const Icon(Icons.playlist_add),
                label: Text(l10n.capsuleApply),
              ),
            ],
            TextButton(
              onPressed: () => Navigator.of(context).pop(),
              child: Text(MaterialLocalizations.of(context).cancelButtonLabel),
            ),
          ],
        ),
      ),
    );
  }
}
