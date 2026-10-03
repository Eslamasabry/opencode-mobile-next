import 'package:flutter/material.dart';

import '../../domain/workspace_paths.dart';
import '../../l10n/app_localizations.dart';
import '../app_iconography.dart';
import '../kit/kit_bidi.dart';
import '../kit/kit_buttons.dart';
import '../kit/kit_field.dart';
import '../kit/kit_row.dart';
import '../kit/kit_row_parts.dart';
import '../kit/kit_section_label.dart';
import '../kit/kit_sheet.dart';
import '../kit/kit_text.dart';
import '../kit/kit_tokens.dart';

/// "Open a project folder" for a server that is not on this phone. OpenCode
/// lists files only inside a project, so folders cannot be browsed here:
/// the folders opened before and the server's own projects are tappable
/// rows, and the path field starts from the folder last used. Closes with
/// the path to open, or null.
class RemoteFolderSheet extends StatefulWidget {
  const RemoteFolderSheet({
    super.key,
    required this.recent,
    required this.projects,
    required this.probe,
  });

  /// Folders opened on this server before, most recent first.
  final List<String> recent;

  /// The server's projects (loaded lazily; a failure leaves them out).
  final Future<List<String>> Function() projects;

  /// Checks a typed path on the server; a plain sentence when it is wrong.
  final Future<String?> Function(String path) probe;

  /// The parent of the folder used last, with its slash: where the field
  /// starts. Empty when nothing was opened, or it was the server's root.
  static String startFor(List<String> recent) {
    for (final path in recent) {
      final cut = path.lastIndexOf('/');
      if (cut <= 0) continue;
      final parent = path.substring(0, cut);
      if (workspaceDirectoryProblem(parent) == null) return '$parent/';
    }
    return '';
  }

  @override
  State<RemoteFolderSheet> createState() => _RemoteFolderSheetState();
}

class _RemoteFolderSheetState extends State<RemoteFolderSheet> {
  late final TextEditingController _path = TextEditingController(
    text: RemoteFolderSheet.startFor(widget.recent),
  );
  List<String> _known = const [];
  String? _problem;
  bool _checking = false;

  @override
  void initState() {
    super.initState();
    _loadKnown();
  }

  @override
  void dispose() {
    _path.dispose();
    super.dispose();
  }

  Future<void> _loadKnown() async {
    try {
      final known = await widget.projects();
      if (mounted) {
        setState(
          () => _known = [
            for (final path in known)
              if (!widget.recent.contains(path) &&
                  workspaceDirectoryProblem(path) == null)
                path,
          ],
        );
      }
    } catch (_) {
      // Only the "Projects on this server" rows depend on it.
    }
  }

  Future<void> _submit(AppLocalizations l10n) async {
    final path = _path.text.trim();
    final problem = workspaceDirectoryProblem(path);
    if (problem != null) {
      setState(() => _problem = problem);
      return;
    }
    setState(() {
      _checking = true;
      _problem = null;
    });
    final answer = await widget.probe(path);
    if (!mounted) return;
    if (answer != null) {
      setState(() {
        _checking = false;
        _problem = answer;
      });
      return;
    }
    KitSheet.close<String>(context, path);
  }

  /// Block one: recent folders, then the server's projects, on one panel.
  List<Widget> _rows(BuildContext context, AppLocalizations l10n) {
    final rows = <(String, String, IconData)>[
      for (final (index, path) in widget.recent.indexed)
        (path, 'remote-recent-$index', AppIconography.history),
      for (final (index, path) in _known.indexed)
        (path, 'remote-project-$index', AppIconography.projects),
    ];
    if (rows.isEmpty) return const [];
    return [
      KitSectionLabel(
        l10n.folderBrowserFoldersLabel,
        margin: EdgeInsets.zero,
        gapBefore: 0,
      ),
      KitRowGroup(
        margin: EdgeInsetsDirectional.only(
          bottom: KitTokens.of(context).space4,
        ),
        children: [
          for (final (path, key, icon) in rows)
            KitRow(
              key: ValueKey(key),
              leading: KitRow.icon(context, icon),
              title: path.substring(path.lastIndexOf('/') + 1),
              supporting: TextSpan(
                text: KitBidi.ltr(path),
                style: KitText.styleFor(KitTextRole.mono),
              ),
              trailing: const KitChevron(),
              onTap: () => KitSheet.close<String>(context, path),
            ),
        ],
      ),
    ];
  }

  @override
  Widget build(BuildContext context) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final media = MediaQuery.of(context);
    final maxHeight =
        (media.size.height - media.viewInsets.bottom - media.padding.top) * .9;
    return SafeArea(
      child: Padding(
        padding: EdgeInsetsDirectional.only(bottom: media.viewInsets.bottom),
        child: ConstrainedBox(
          constraints: BoxConstraints(maxHeight: maxHeight),
          child: KitSheet(
            key: const ValueKey('remote-folder-sheet'),
            title: l10n.projectFolderOpen,
            handle: false,
            onClose: () => KitSheet.close<String>(context),
            primary: KitAction(
              key: const ValueKey('open-folder-confirm'),
              label: l10n.projectFolderOpenAction,
              icon: AppIconography.folderOpen,
              working: _checking,
              onPressed: () => _submit(l10n),
            ),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                ..._rows(context, l10n),
                KitField(
                  fieldKey: const ValueKey('open-folder-path'),
                  controller: _path,
                  kind: KitFieldKind.path,
                  label: l10n.projectFolderPathLabel,
                  hint: l10n.projectFolderPathHint(managedProjectsDirectory),
                  helper: l10n.projectFolderOpenMessage,
                  error: _problem,
                  textInputAction: TextInputAction.done,
                  onChanged: (_) {
                    if (_problem != null) setState(() => _problem = null);
                  },
                  onSubmitted: (_) => _submit(l10n),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
