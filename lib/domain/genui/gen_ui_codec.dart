import '../../api/models.dart';
import 'gen_ui_answers.dart';
import 'gen_ui_types.dart';

GenUiParse? genUiFromPart(
  Part part, {
  required GenUiScope scope,
  required String sessionID,
  required String messageID,
}) => throw UnimplementedError();

GenUiCardState genUiStateFor(
  GenUiCard card,
  List<MessageWithParts> transcript, {
  required bool tailComplete,
}) => throw UnimplementedError();

({String summary, Object? value})? genUiAnswerIn(
  MessageWithParts message,
  GenUiCard card,
) => throw UnimplementedError();

String genUiAnswerText(GenUiCard card, GenUiAnswer answer) =>
    throw UnimplementedError();
