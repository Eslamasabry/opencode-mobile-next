import '../../domain/server_gateway.dart' show PendingQuestion;

/// True when [question] is too big to answer inline above the composer and
/// the chat should show a compact card whose Answer button opens the full
/// sheet: more than two prompts, or any choice description past ~140
/// characters, or any optional prompt (it may be sent empty). Mirrors `formPrefersFullScreen` so the two request surfaces
/// grow the same way.
///
/// The choice rows and the free-text answer this file used to forward are
/// gone (kit-hygiene): use `KitChoiceRow` and `KitField` from the kit.
bool questionPrefersSheet(PendingQuestion question) =>
    question.prompts.length > 2 ||
    // An empty answer is sent from the sheet, never from a one-tap option.
    question.prompts.any((prompt) => prompt.optional) ||
    question.prompts.any(
      (prompt) =>
          prompt.choices.any((choice) => choice.description.length > 140),
    );
