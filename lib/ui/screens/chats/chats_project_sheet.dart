import 'package:flutter/material.dart';

import '../../../domain/chat_feed.dart';
import '../../../l10n/app_localizations.dart';
import '../../../platform/phone_project_scan.dart' show PhoneProjectKind;
import '../../app_iconography.dart';
import '../../kit/kit.dart';
import '../../widgets/phone_project_kind.dart';

/// What the person picked in the project filter sheet.
sealed class ChatsProjectChoice {
  const ChatsProjectChoice();
}

/// Every project.
class ChatsAllProjects extends ChatsProjectChoice {
  const ChatsAllProjects();
}

/// One project, by its folder.
class ChatsOneProject extends ChatsProjectChoice {
  const ChatsOneProject(this.directory);
  final String directory;
}

/// "Open a project…": the caller closes this sheet first (one sheet only),
/// then opens the Open a project sheet and uses its folder as the filter.
class ChatsOpenProject extends ChatsProjectChoice {
  const ChatsOpenProject();
}

/// The kind glyph the scanner uses for a project's kind name.
IconData chatsProjectIcon(String? kind) => phoneProjectKindIcon(
  kind == null ? null : PhoneProjectKind.values.asNameMap()[kind],
);

/// "Show chats from": All projects with the total, then each project, then
/// "Open a project…". A check marks the current choice. One sheet; the
/// caller handles [ChatsOpenProject] after it has closed.
Future<ChatsProjectChoice?> showChatsProjectSheet(
  BuildContext context, {
  required List<ProjectSummary> projects,
  required int allCount,
  required String? selectedDirectory,
}) {
  final l10n = AppLocalizations.of(context);
  return showKitSheet<ChatsProjectChoice>(
    context,
    sheetKey: const ValueKey('chats-project-sheet'),
    title: l10n.chatsFilterSheetTitle,
    body: (sheetContext) {
      final tokens = KitTokens.of(sheetContext);
      Widget chosen(bool on) => on
          ? const KitIcon(AppIconography.check, size: KitIconSize.small)
          : const SizedBox.shrink();
      return Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          KitRowGroup(
            margin: EdgeInsets.zero,
            children: [
              KitRow(
                key: const ValueKey('chats-project-all'),
                title: l10n.chatsHomeAllProjects,
                leading: KitRowIcon(
                  AppIconography.folderOpen,
                  current: selectedDirectory == null,
                ),
                supporting: TextSpan(text: l10n.chatsFilterChatCount(allCount)),
                trailing: chosen(selectedDirectory == null),
                selected: selectedDirectory == null,
                onTap: () =>
                    KitSheet.close(sheetContext, const ChatsAllProjects()),
              ),
              for (final project in projects)
                KitRow(
                  key: ValueKey('chats-project-${project.directory}'),
                  title: KitBidi.auto(project.name),
                  leading: KitRowIcon(
                    chatsProjectIcon(project.kind),
                    current: selectedDirectory == project.directory,
                  ),
                  supporting: TextSpan(text: _counts(l10n, project)),
                  supportingMaxLines: 2,
                  trailing: Row(
                    mainAxisSize: MainAxisSize.min,
                    spacing: tokens.space2,
                    children: [
                      if (project.isGit)
                        KitChip(
                          label: l10n.phoneScanGit,
                          icon: AppIconography.branch,
                        ),
                      chosen(selectedDirectory == project.directory),
                    ],
                  ),
                  selected: selectedDirectory == project.directory,
                  onTap: () => KitSheet.close(
                    sheetContext,
                    ChatsOneProject(project.directory),
                  ),
                ),
            ],
          ),
          KitRowGroup(
            margin: EdgeInsets.zero,
            gapBefore: tokens.space3,
            children: [
              KitRow(
                key: const ValueKey('chats-project-open'),
                title: l10n.chatsFilterOpenProject,
                leading: KitRowIcon(AppIconography.folderOpen),
                titleAccent: true,
                trailing: const KitChevron(),
                onTap: () =>
                    KitSheet.close(sheetContext, const ChatsOpenProject()),
              ),
            ],
          ),
        ],
      );
    },
  );
}

/// "3 chats · 1 running · needs you".
String _counts(AppLocalizations l10n, ProjectSummary project) => [
  l10n.chatsFilterChatCount(project.chatCount),
  if (project.runningCount > 0)
    l10n.chatsFilterRunningCount(project.runningCount),
  if (project.needsYouCount > 0) l10n.chatsFilterNeedsYouWord,
].join(' · ');
