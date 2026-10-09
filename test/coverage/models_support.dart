// Shared by the models, providers, usage and quota coverage ratchets: fakes
// that hand the screens what the real gateways parsed from wire payloads.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/ui/widgets/pickers.dart';

import 'lists_support.dart';
import 'paseo_coverage_support.dart' show screenText, writeCasePng;

/// The transport the picker reads its catalog from.
class ModelsApi extends ListsApi {
  ModelsApi([super.caps]);

  ProvidersResponse? providerList;
  List<AgentInfo> agentList = const [];

  @override
  Future<ProvidersResponse> providers() async =>
      providerList ?? ProvidersResponse(providers: const []);

  @override
  Future<ProvidersResponse> configuredProviders() => providers();

  @override
  Future<List<AgentInfo>> agents() async => agentList;
}

/// The repository the picker and the Usage page read from.
class ModelsRepository extends ListsRepository
    implements UsageStatisticsGateway {
  CatalogSnapshot? catalogSnapshot;
  ChatDefaults? defaults;
  UsageStatistics? stats;

  @override
  Future<CatalogSnapshot> loadCatalog() async =>
      catalogSnapshot ?? (throw const ProductException('No detailed catalog'));

  @override
  Future<ChatDefaults> loadChatDefaults() async =>
      defaults ?? const ChatDefaults();

  @override
  Future<List<IntegrationInfo>> listIntegrations() async => const [];

  @override
  bool get usageStatisticsSupported => true;

  @override
  Future<UsageStatistics> loadUsageStatistics(UsageQuery query) async => stats!;
}

extension ModelsScreens on ListsScreens {
  /// The model picker, open over a blank page.
  Future<void> modelPicker({Future<void> Function()? then}) async {
    await tester.runAsync(controller.ensureCatalog);
    await show(
      Builder(
        builder: (context) => TextButton(
          onPressed: () => showModelPicker(context),
          child: const SizedBox(width: 8, height: 8),
        ),
      ),
      then: () async {
        await tester.tap(find.byType(TextButton));
        await settle(tester);
        await then?.call();
      },
    );
    // The rows that open the effort and agent choices.
    for (final key in ['model-picker-thinking', 'model-picker-agent']) {
      final chip = find.byKey(Key(key));
      if (chip.evaluate().isEmpty) continue;
      await tester.tap(chip);
      await settle(tester);
      seen.add(screenText(tester).join('\n'));
      await writeCasePng(tester, boundary, 'lists-$name-${seen.length}');
      // Both open a menu over the picker; a tap outside closes it.
      await tester.tapAt(const Offset(4, 4));
      await settle(tester);
    }
  }
}
