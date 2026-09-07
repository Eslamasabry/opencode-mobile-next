import 'package:flutter/material.dart';
import '../../l10n/app_localizations.dart';

import '../../domain/web_source_selection.dart';
import '../../state/connection.dart';
import '../../state/web_sources_overview.dart';
import '../widgets/external_link.dart';

export '../../domain/web_source_selection.dart';

/// Returns `List<WebSourceSelection>` on explicit review confirmation, or null
/// on cancellation. Does not send a prompt, attach a remote file or fetch URLs.
class WebSourcesScreen extends StatefulWidget {
  const WebSourcesScreen({super.key, required this.controller});

  final ConnectionController controller;

  @override
  State<WebSourcesScreen> createState() => _WebSourcesScreenState();
}

class _WebSourcesScreenState extends State<WebSourcesScreen> {
  final _url = TextEditingController();
  final _title = TextEditingController();
  final _excerpt = TextEditingController();
  late WebSourcesOverview _overview;
  String? _error;

  @override
  void initState() {
    super.initState();
    _overview = WebSourcesOverview(controller: widget.controller);
    _overview.addListener(_changed);
  }

  void _changed() {
    if (!mounted) return;
    if (_overview.scopeChanged) {
      _url.clear();
      _title.clear();
      _excerpt.clear();
      _error = null;
    }
    setState(() {});
  }

  @override
  void didUpdateWidget(covariant WebSourcesScreen oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (identical(oldWidget.controller, widget.controller)) return;
    _overview.removeListener(_changed);
    _overview.dispose();
    _overview = WebSourcesOverview(controller: widget.controller);
    _overview.addListener(_changed);
    _url.clear();
    _title.clear();
    _excerpt.clear();
    _error = null;
  }

  void _add() {
    if (_overview.scopeChanged) return;
    final uri = safeExternalLinkUri(_url.text);
    if (uri == null) {
      setState(
        () => _error = 'Enter an HTTP or HTTPS URL without credentials.',
      );
      return;
    }
    try {
      final source = WebSourceSelection(
        title: _title.text,
        url: uri.toString(),
        excerpt: _excerpt.text,
      );
      final error = _overview.add(source);
      setState(() => _error = error);
      if (error != null) return;
      _url.clear();
      _title.clear();
      _excerpt.clear();
      FocusScope.of(context).unfocus();
    } on FormatException catch (error) {
      setState(() => _error = error.message);
    }
  }

  void _confirm() {
    final sources = _overview.reviewedSelection();
    if (sources == null || sources.isEmpty) return;
    Navigator.of(context).pop<List<WebSourceSelection>>(sources);
  }

  @override
  void dispose() {
    _overview.removeListener(_changed);
    _overview.dispose();
    _url.dispose();
    _title.dispose();
    _excerpt.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final blocked = _overview.scopeChanged;
    final sources = _overview.sources;
    return Scaffold(
      appBar: AppBar(title: Text(l10n.webSourcesTitle)),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            Text(l10n.webSourcesDisclosure),
            const SizedBox(height: 16),
            if (blocked)
              Semantics(
                liveRegion: true,
                child: Text(l10n.webSourcesScopeChanged),
              )
            else ...[
              TextField(
                key: const ValueKey('web-source-url'),
                controller: _url,
                keyboardType: TextInputType.url,
                autocorrect: false,
                enableSuggestions: false,
                maxLength: WebSourceSelection.maxUrlLength,
                decoration: InputDecoration(labelText: l10n.webSourcesUrl),
              ),
              TextField(
                key: const ValueKey('web-source-title'),
                controller: _title,
                maxLength: WebSourceSelection.maxTitleLength,
                decoration: InputDecoration(labelText: l10n.webSourcesLabel),
              ),
              TextField(
                key: const ValueKey('web-source-excerpt'),
                controller: _excerpt,
                minLines: 3,
                maxLines: 6,
                maxLength: WebSourceSelection.maxExcerptLength,
                decoration: InputDecoration(
                  labelText: l10n.webSourcesExcerpt,
                  helperText: l10n.webSourcesExcerptHint,
                  helperMaxLines: 3,
                ),
              ),
              if (_error != null)
                Semantics(liveRegion: true, child: Text(_error!)),
              const SizedBox(height: 12),
              OutlinedButton.icon(
                key: const ValueKey('web-source-add'),
                style: OutlinedButton.styleFrom(
                  minimumSize: const Size(48, 48),
                ),
                onPressed: _add,
                icon: const Icon(Icons.add),
                label: Text(l10n.webSourcesAdd),
              ),
              const SizedBox(height: 24),
              Text(
                l10n.webSourcesReviewCount(sources.length),
                style: Theme.of(context).textTheme.titleMedium,
              ),
              Text(l10n.webSourcesReviewHint),
              if (sources.isEmpty)
                Padding(
                  padding: const EdgeInsets.symmetric(vertical: 16),
                  child: Text(l10n.webSourcesEmpty),
                ),
              for (var index = 0; index < sources.length; index++)
                Card(
                  child: Padding(
                    padding: const EdgeInsets.all(8),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        CheckboxListTile(
                          key: ValueKey('web-source-select-$index'),
                          contentPadding: EdgeInsets.zero,
                          title: Text(sources[index].title),
                          subtitle: Text(sources[index].url),
                          value: _overview.isSelected(sources[index]),
                          onChanged: (value) =>
                              _overview.select(sources[index], value == true),
                        ),
                        if (sources[index].excerpt != null)
                          SelectableText(sources[index].excerpt!),
                        Wrap(
                          spacing: 8,
                          children: [
                            TextButton.icon(
                              key: ValueKey('web-source-open-$index'),
                              style: TextButton.styleFrom(
                                minimumSize: const Size(48, 48),
                              ),
                              onPressed: () {
                                if (_overview.reviewedSelection() == null) {
                                  return;
                                }
                                openExternalLink(context, sources[index].url);
                              },
                              icon: const Icon(Icons.open_in_new),
                              label: Text(l10n.webSourcesOpen),
                            ),
                            TextButton.icon(
                              key: ValueKey('web-source-remove-$index'),
                              style: TextButton.styleFrom(
                                minimumSize: const Size(48, 48),
                              ),
                              onPressed: () => _overview.remove(sources[index]),
                              icon: const Icon(Icons.delete_outline),
                              label: Text(l10n.mcpRemove),
                            ),
                          ],
                        ),
                      ],
                    ),
                  ),
                ),
              const SizedBox(height: 16),
              FilledButton(
                key: const ValueKey('web-sources-confirm'),
                style: FilledButton.styleFrom(minimumSize: const Size(48, 48)),
                onPressed: _overview.selectedCount == 0 ? null : _confirm,
                child: Text(l10n.webSourcesUseCount(_overview.selectedCount)),
              ),
            ],
          ],
        ),
      ),
    );
  }
}
