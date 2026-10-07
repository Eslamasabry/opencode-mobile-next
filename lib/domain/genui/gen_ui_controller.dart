import '../../api/models.dart';
import '../chat_feed.dart';
import 'gen_ui_answers.dart';
import 'gen_ui_status.dart';
import 'gen_ui_types.dart';

/// Public controller surface. Implemented by ConnectionController integration.
abstract interface class GenUiController {
  List<GenUiCard> waitingCardsForSession(String sessionID);
  List<GenUiCard> waitingCardsForFeedItem(ChatFeedItem item);
  Future<void> answerGenUi(
    GenUiCard card,
    GenUiAnswer answer, {
    List<PromptAttachment> attachments = const [],
  });
  bool get genUiEnabled;
  Future<void> setGenUiEnabled(bool on);
  GenUiSetupStatus get genUiStatus;
  GenUiDeliveryState genUiDeliveryFor(GenUiCard card);
}
