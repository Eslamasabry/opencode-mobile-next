import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/genui/gen_ui.dart';

void main() {
  test('frontend can construct every public card variant', () {
    const scope = GenUiScope(profileID: 'p', sourceId: 's', directory: '/work');
    final nodes = <GenUiNode>[
      const GenUiText(text: 'Report'),
      GenUiKeyValue(
        rows: [const GenUiKeyValueRow(key: 'State', value: 'Ready')],
      ),
      GenUiList(
        items: [const GenUiListItem(text: 'Done', done: true)],
        style: GenUiListStyle.check,
      ),
      GenUiTable(
        columns: ['Name'],
        rows: [
          ['Example'],
        ],
      ),
      GenUiChart(
        kind: GenUiChartKind.bar,
        labels: ['A'],
        series: [
          GenUiChartSeries(name: 'Count', values: [1]),
        ],
      ),
      const GenUiCode(language: 'dart', text: 'void main() {}'),
      GenUiDiffStat(
        files: [const GenUiDiffFile(path: 'main.dart', added: 1, removed: 0)],
      ),
      const GenUiProgress(label: 'Progress', value: 0.5),
      const GenUiCallout(tone: GenUiCalloutTone.info, text: 'Ready'),
      const GenUiLink(label: 'Docs', url: 'https://example.com'),
    ];
    const option = GenUiOption(id: 'a', label: 'A', detail: 'First');
    final asks = <GenUiAsk>[
      GenUiChoiceAsk(options: [option]),
      GenUiFormAsk(
        fields: [
          for (final type in GenUiFieldType.values)
            GenUiField(id: type.name, label: type.name, type: type),
        ],
      ),
      const GenUiConfirmAsk(tone: GenUiConfirmTone.danger),
      const GenUiPhotoAsk(purpose: 'Show the screen'),
    ];
    final card = GenUiCard(
      scope: scope,
      id: 'card',
      title: 'Pick',
      sessionID: 'session',
      callID: 'call',
      messageID: 'message',
      revision: 'revision',
      body: nodes,
      ask: asks.first,
    );
    final parses = <GenUiParse>[
      GenUiParsed(card),
      const GenUiUnreadable(reason: GenUiProblem.badValue),
    ];
    final answers = <GenUiAnswer>[
      GenUiChoiceAnswer(['a']),
      GenUiFormAnswer({'name': 'A'}),
      const GenUiConfirmAnswer(false),
      const GenUiPhotoAnswer(1),
    ];
    final statuses = <GenUiSetupStatus>[
      const GenUiSetupOff(),
      const GenUiSetupUnavailable(reason: GenUiSetupProblem.notQualified),
      const GenUiSetupInstalling(),
      GenUiSetupOn(agents: [GenUiAgent.claude]),
      GenUiSetupPartial(
        agents: [GenUiAgent.openCode1],
        reason: GenUiSetupProblem.runtimeMissing,
      ),
      GenUiSetupRestartRequired(agents: []),
      const GenUiSetupFailed(reason: GenUiSetupProblem.storageFailed),
    ];
    expect(nodes, hasLength(10));
    expect(asks, hasLength(4));
    expect(answers, hasLength(4));
    expect(parses, hasLength(2));
    expect(statuses, hasLength(7));
    expect(GenUiCardState.values, hasLength(5));
    expect(GenUiDeliveryState.values, hasLength(5));
    expect(card.identity, contains('session'));
    nodes.clear();
    expect(card.body, hasLength(10));
    expect(() => card.body.clear(), throwsUnsupportedError);
    expect(
      () => (card.body[3] as GenUiTable).rows.first.clear(),
      throwsUnsupportedError,
    );
    expect(
      scope,
      const GenUiScope(profileID: 'p', sourceId: 's', directory: '/work'),
    );
  });
}
