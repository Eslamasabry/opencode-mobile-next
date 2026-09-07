import '../api/models.dart' show PromptAttachment;

export '../api/models.dart' show PromptAttachment;

/// A reviewed, local-only addition to a prompt. Persistence uses the ordinary
/// session draft; the capsule introduces no separate store or send operation.
class ContextCapsule {
  ContextCapsule({required this.text, List<PromptAttachment> images = const []})
    : images = List.unmodifiable(images);

  final String text;
  final List<PromptAttachment> images;

  String appendTo(String draft) => text.isEmpty
      ? draft
      : draft.isEmpty
      ? text
      : '$draft\n\n$text';
}
