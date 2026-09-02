import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/ui/screens/workspace_screen.dart';
import 'package:opencode_mobile/ui/widgets/pickers.dart';

void main() {
  test(
    'session usage labels show cost and diff only when the server sent them',
    () {
      expect(sessionUsageLabels(Session(id: 's', title: 't')), isEmpty);
      expect(
        sessionUsageLabels(Session(id: 's', title: 't', cost: 0.001)),
        isEmpty,
      );
      expect(
        sessionUsageLabels(
          Session(
            id: 's',
            title: 't',
            cost: 0.4249,
            summary: const SessionDiffSummary(
              additions: 120,
              deletions: 34,
              files: 6,
            ),
          ),
        ),
        ['\$0.42', '+120 −34', '6 files'],
      );
      expect(
        sessionUsageLabels(
          Session(
            id: 's',
            title: 't',
            summary: const SessionDiffSummary(
              additions: 1,
              deletions: 0,
              files: 1,
            ),
          ),
        ),
        ['+1 −0', '1 file'],
      );
    },
  );

  test('model cost label prices per million tokens', () {
    CatalogModel model({ModelCost? cost}) => CatalogModel(
      id: 'm',
      providerID: 'p',
      name: 'Model',
      enabled: true,
      status: 'active',
      contextLimit: 0,
      outputLimit: 0,
      reasoning: false,
      attachments: false,
      tools: true,
      variants: const [],
      cost: cost,
    );
    expect(modelCostLabel(model()), isNull);
    expect(
      modelCostLabel(
        model(
          cost: const ModelCost(
            inputPerMillion: 0,
            outputPerMillion: 0,
            cacheReadPerMillion: 0,
            cacheWritePerMillion: 0,
          ),
        ),
      ),
      isNull,
    );
    expect(
      modelCostLabel(
        model(
          cost: const ModelCost(
            inputPerMillion: 3,
            outputPerMillion: 15,
            cacheReadPerMillion: 0.3,
            cacheWritePerMillion: 3.75,
          ),
        ),
      ),
      '\$3.00 in · \$15.00 out /1M',
    );
    expect(
      modelCostLabel(
        model(
          cost: const ModelCost(
            inputPerMillion: 0.25,
            outputPerMillion: 1.25,
            cacheReadPerMillion: 0,
            cacheWritePerMillion: 0,
          ),
        ),
      ),
      '\$0.250 in · \$1.25 out /1M',
    );
  });
}
