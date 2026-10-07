import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';
import 'package:opencode_mobile/domain/genui/gen_ui.dart';
import 'package:opencode_mobile/ui/widgets/agent_card_photos.dart';

/// A GenUiController the tests drive: states and deliveries are set by the
/// test, answers and undos are recorded.
class FakeGenUi extends ChangeNotifier implements GenUiController {
  final states = <String, GenUiCardState>{};
  final deliveries = <String, GenUiDeliveryState>{};
  final summaries = <String, String>{};
  final answers = <({GenUiCard card, GenUiAnswer answer, int attachments})>[];
  final undone = <GenUiCard>[];
  Object? answerError;
  List<GenUiCard> waiting = const [];
  bool enabled = true;
  GenUiSetupStatus status = const GenUiSetupOff();
  Object? enableError;
  final enableCalls = <bool>[];

  void set(
    GenUiCard card, {
    GenUiCardState? state,
    GenUiDeliveryState? delivery,
    String? summary,
  }) {
    if (state != null) states[card.callID] = state;
    if (delivery != null) deliveries[card.callID] = delivery;
    if (summary != null) summaries[card.callID] = summary;
    notifyListeners();
  }

  @override
  GenUiParse? genUiCardForPart(String sessionID, String messageID, Part part) =>
      cards[part.callID];

  /// Cards by tool call id, for the transcript.
  final cards = <String, GenUiParse>{};

  @override
  GenUiCardState genUiStateForCard(GenUiCard card) =>
      states[card.callID] ??
      (card.ask == null ? GenUiCardState.report : GenUiCardState.waiting);

  @override
  GenUiDeliveryState genUiDeliveryFor(GenUiCard card) =>
      deliveries[card.callID] ?? GenUiDeliveryState.idle;

  @override
  String? genUiAnswerSummary(GenUiCard card) => summaries[card.callID];

  @override
  void undoGenUiAnswer(GenUiCard card) => undone.add(card);

  @override
  List<GenUiCard> waitingCardsForSession(String sessionID) => waiting;

  @override
  List<GenUiCard> waitingCardsForFeedItem(ChatFeedItem item) => waiting;

  @override
  Future<void> answerGenUi(
    GenUiCard card,
    GenUiAnswer answer, {
    List<PromptAttachment> attachments = const [],
  }) async {
    final error = answerError;
    if (error != null) throw error;
    answers.add((card: card, answer: answer, attachments: attachments.length));
  }

  @override
  bool get genUiEnabled => enabled;

  @override
  Future<void> setGenUiEnabled(bool on) async {
    enableCalls.add(on);
    final error = enableError;
    if (error != null) throw error;
    enabled = on;
    notifyListeners();
  }

  @override
  GenUiSetupStatus get genUiStatus => status;
}

class FakeCardPhotos implements AgentCardPhotos {
  FakeCardPhotos({this.canTake = true});

  @override
  final bool canTake;
  int takes = 0;
  int chooses = 0;
  Object? error;

  /// A one-pixel PNG, as a data URL.
  static PromptAttachment photo(String name) => PromptAttachment(
    mime: 'image/png',
    filename: name,
    url:
        'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==',
  );

  @override
  Future<List<PromptAttachment>> choose(int limit) async {
    chooses += 1;
    final e = error;
    if (e != null) throw e;
    return [for (var i = 0; i < limit; i++) photo('gallery-$i.png')];
  }

  @override
  Future<PromptAttachment?> take() async {
    takes += 1;
    return photo('camera.png');
  }
}

const _scope = GenUiScope(
  profileID: 'p1',
  sourceId: 's1',
  directory: '/root/projects/alpha',
);

GenUiCard agentCard({
  String callID = 'call-1',
  String title = 'Pick a database',
  List<GenUiNode> body = const [GenUiText(text: 'Two fit this project.')],
  GenUiAsk? ask,
}) => GenUiCard(
  scope: _scope,
  id: 'db-choice',
  title: title,
  sessionID: 'ses-1',
  callID: callID,
  messageID: 'msg-1',
  revision: 'r1',
  body: body,
  ask: ask,
);

GenUiChoiceAsk choiceAsk({bool multi = false}) => GenUiChoiceAsk(
  multi: multi,
  options: const [
    GenUiOption(id: 'pg', label: 'Postgres', detail: 'Relational'),
    GenUiOption(id: 'sqlite', label: 'SQLite'),
    GenUiOption(id: 'mongo', label: 'MongoDB'),
  ],
);

/// The tool-part JSON-less stand-in the fake controller keys on.
Part toolPart(String callID) => Part(
  id: 'part-$callID',
  messageID: 'm2',
  type: 'tool',
  callID: callID,
  toolName: 'oc-ui_show',
);
