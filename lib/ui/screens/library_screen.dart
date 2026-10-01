import 'dart:async';

import 'package:flutter/material.dart';

import '../../api/mcp_oauth.dart';
import '../../l10n/app_localizations.dart';
import '../../domain/server_gateway.dart'
    show ProviderRuntimeBusyException, StreamStatus;
import '../../api/provider_presentation.dart';
import '../../api/product_repository.dart';
import '../../state/connection.dart';
import '../../state/pending_auth.dart';
import '../app_theme.dart';
import '../kit/kit.dart';
import '../widgets/external_link.dart';
import '../widgets/connect_methods.dart';
import '../widgets/provider_logo.dart';
import '../widgets/safety_confirms.dart';
import '../widgets/product_states.dart';
import '../widgets/command_sheet.dart';
import '../widgets/run_command_dialog.dart';
import 'mcp_catalog_screen.dart';
import 'mcp_setup_screen.dart';

part 'library/integrations_screen.dart';
part 'library/integrations_mcp.dart';
part 'library/integrations_page.dart';
part 'library/integrations_signin.dart';
part 'library/integrations_connect.dart';
part 'library/integration_tiles.dart';
part 'library/credential_sheet.dart';
part 'library/command_auth_sheet.dart';
part 'library/pending_auth_recovery.dart';
part 'library/commands_screen.dart';
part 'library/skills_screen.dart';
part 'library/skill_activation.dart';
part 'library/references_screen.dart';

/// Keep the default visible in its destination, using the catalog display name.
String defaultModelLabel(
  ConnectionController controller,
  AppLocalizations l10n,
) {
  final model = controller.selectedModel;
  if (model == null) return l10n.libraryNoModel;
  final catalogModel = controller.catalog?.models
      .where(
        (candidate) =>
            candidate.providerID == model.providerID &&
            candidate.id == model.modelID,
      )
      .firstOrNull;
  return catalogModel?.name.trim().isNotEmpty == true
      ? catalogModel!.name
      : presentedModelLabel(model.providerID, model.modelID);
}
