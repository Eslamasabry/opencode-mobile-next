import 'package:flutter/widgets.dart';

import '../../l10n/app_localizations.dart';
import '../../platform/phone_project_scan.dart';
import '../app_iconography.dart';

/// The glyph of a project's kind; a plain folder when the kind is not known
/// (or the folder is only a repository). Shared by the Open a project sheet
/// and the Projects screen so a project looks the same in both.
IconData phoneProjectKindIcon(PhoneProjectKind? kind) => switch (kind) {
  PhoneProjectKind.dart => AppIconography.layers,
  PhoneProjectKind.node => AppIconography.package,
  PhoneProjectKind.python => AppIconography.dataObject,
  PhoneProjectKind.rust => AppIconography.processor,
  PhoneProjectKind.go => AppIconography.terminal,
  PhoneProjectKind.java => AppIconography.code,
  PhoneProjectKind.ruby => AppIconography.database,
  PhoneProjectKind.php => AppIconography.globe,
  PhoneProjectKind.dotnet => AppIconography.category,
  PhoneProjectKind.cpp => AppIconography.function,
  PhoneProjectKind.git || null => AppIconography.folderOpen,
};

/// The kind's word ("Node"), or "Folder" when it is not known.
String phoneProjectKindLabel(AppLocalizations l10n, PhoneProjectKind? kind) =>
    switch (kind) {
      null => l10n.phoneScanKindGit,
      PhoneProjectKind.dart => l10n.phoneScanKindDart,
      PhoneProjectKind.node => l10n.phoneScanKindNode,
      PhoneProjectKind.python => l10n.phoneScanKindPython,
      PhoneProjectKind.rust => l10n.phoneScanKindRust,
      PhoneProjectKind.go => l10n.phoneScanKindGo,
      PhoneProjectKind.java => l10n.phoneScanKindJava,
      PhoneProjectKind.ruby => l10n.phoneScanKindRuby,
      PhoneProjectKind.php => l10n.phoneScanKindPhp,
      PhoneProjectKind.dotnet => l10n.phoneScanKindDotnet,
      PhoneProjectKind.cpp => l10n.phoneScanKindCpp,
      PhoneProjectKind.git => l10n.phoneScanKindGit,
    };
