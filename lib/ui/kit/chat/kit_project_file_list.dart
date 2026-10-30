// KitProjectFileList: the body of the project file sheet (docs/ux-system/kit-api).
// A search field and the files it finds; see kit_project_file_sheet.dart for
// the sheet that opens it.
import 'dart:async';

import 'package:flutter/material.dart';

import '../../../l10n/app_localizations.dart';
import '../../app_iconography.dart';
import '../kit_bidi.dart';
import '../kit_buttons.dart';
import '../kit_max_width.dart';
import '../kit_notice.dart';
import '../kit_row.dart';
import '../kit_search_field.dart';
import '../kit_text.dart';
import '../kit_tokens.dart';

/// The project file sheet's body, apart from the sheet so a catalog page can
/// show it: a search field and the files it finds (name, then folder). Before
/// anything is typed it lists the top level of the folder being browsed, with
/// its folders; a tap on a file returns its project-relative path.
///
/// States: empty, error.
class KitProjectFileList extends StatefulWidget {
  const KitProjectFileList({
    super.key,
    required this.search,
    required this.onPick,
    this.initial,
    this.loading,
  });

  final Future<List<String>> Function(String query) search;
  final Future<List<String>> Function(String folder)? initial;
  final ValueChanged<String> onPick;

  /// The sheet's loading bar, on while a search is out.
  final ValueNotifier<bool>? loading;

  @override
  State<KitProjectFileList> createState() => _KitProjectFileListState();
}

class _KitProjectFileListState extends State<KitProjectFileList> {
  final _field = TextEditingController();
  List<String> _paths = const [];
  String _query = '';
  // The folder being browsed, project-relative without a trailing slash;
  // empty at the top.
  String _folder = '';
  Object? _error;
  int _generation = 0;

  @override
  void initState() {
    super.initState();
    // After the first frame: the sheet's loading bar must not change while
    // the sheet is still being built.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) unawaited(_run(''));
    });
  }

  @override
  void dispose() {
    _generation++;
    _field.dispose();
    super.dispose();
  }

  Future<void> _run(String query) async {
    final generation = ++_generation;
    _query = query.trim();
    widget.loading?.value = true;
    try {
      final found = _query.isEmpty
          ? await (widget.initial?.call(_folder) ??
                Future.value(const <String>[]))
          : await widget.search(_query);
      if (!mounted || generation != _generation) return;
      setState(() {
        _paths = found;
        _error = null;
      });
    } catch (error) {
      if (!mounted || generation != _generation) return;
      setState(() => _error = error);
    } finally {
      if (mounted && generation == _generation) widget.loading?.value = false;
    }
  }

  void _openFolder(String folder) {
    _folder = folder;
    unawaited(_run(''));
  }

  @override
  Widget build(BuildContext context) =>
      KitMaxWidth.list(child: _buildContent(context));

  Widget _buildContent(BuildContext context) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final tokens = KitTokens.of(context);
    final Widget results;
    if (_error != null) {
      results = KitNotice.error(
        key: const Key('project-file-error'),
        title: l10n.mentionSearchFailedTitle,
        message: l10n.mentionSearchFailedMessage,
        retry: KitAction(
          key: const Key('project-file-retry'),
          label: l10n.refreshRetry,
          onPressed: () => unawaited(_run(_field.text)),
        ),
      );
    } else if (_paths.isEmpty && !(_query.isEmpty && _folder.isNotEmpty)) {
      results = _query.isEmpty
          ? KitText(
              l10n.mentionSearchHint,
              key: const Key('project-file-hint'),
              role: KitTextRole.secondary,
            )
          : KitSearchNoMatch(
              query: _query,
              what: l10n.mentionNoMatchWhat,
              onClear: () {
                _field.clear();
                unawaited(_run(''));
              },
            );
    } else {
      results = KitRowGroup(
        children: [
          if (_query.isEmpty && _folder.isNotEmpty)
            KitRow(
              key: const Key('project-file-back'),
              leading: KitRow.icon(context, AppIconography.back),
              title: l10n.kitTopBarBack,
              supporting: TextSpan(text: KitBidi.ltr('$_folder/')),
              onTap: () => _openFolder(
                _folder.contains('/')
                    ? _folder.substring(0, _folder.lastIndexOf('/'))
                    : '',
              ),
            ),
          for (final path in _paths)
            if (path.endsWith('/'))
              KitRow(
                key: ValueKey('project-folder-$path'),
                leading: KitRow.icon(context, AppIconography.folderOpen),
                title: KitBidi.ltr(path.split('/').reversed.skip(1).first),
                supporting: TextSpan(text: l10n.mentionProjectFolder),
                trailing: KitRow.icon(context, AppIconography.chevronRight),
                onTap: () => _openFolder(path.substring(0, path.length - 1)),
              )
            else
              KitRow(
                key: ValueKey('project-file-$path'),
                leading: KitRow.icon(context, AppIconography.fileText),
                title: path.split('/').last,
                titleIsFileName: true,
                titleMaxLines: 2,
                supporting: TextSpan(
                  text: path.contains('/')
                      ? KitBidi.ltr(path.substring(0, path.lastIndexOf('/')))
                      : l10n.mentionProjectFolder,
                ),
                onTap: () => widget.onPick(path),
              ),
        ],
      );
    }
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        KitSearchField(
          label: l10n.mentionSearchLabel,
          controller: _field,
          autofocus: true,
          onChanged: (value) => unawaited(_run(value)),
          resultCount: _query.isEmpty || _error != null ? null : _paths.length,
          fieldKey: const Key('project-file-search'),
          clearKey: const Key('project-file-search-clear'),
        ),
        SizedBox(height: tokens.space3),
        results,
      ],
    );
  }
}
