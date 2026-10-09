/// What a gate is asking of the person. Total: unrecognised provider kinds
/// land on [unknown] and still render as a generic gate.
enum GateKind {
  choice,
  confirmation,
  freeText,
  gateBead,
  runFailed,
  reviewReady,
  unknown;

  /// Maps a provider interaction kind without throwing.
  ///
  /// Gas City pending interaction `kind` (`choice`/`select`, `confirm`,
  /// `text`/`free_text`), gate beads (`gate`/`gate_bead`), a failed run
  /// (`run_failed`) and the `needs-review` label (`review_ready`).
  static GateKind fromProvider(String? value) {
    switch (_normalize(value)) {
      case 'choice':
      case 'select':
      case 'option':
        return choice;
      case 'confirmation':
      case 'confirm':
      case 'yes_no':
      case 'approval':
      case 'tool_approval':
        return confirmation;
      case 'free_text':
      case 'freetext':
      case 'text':
      case 'input':
        return freeText;
      case 'gate_bead':
      case 'gate':
      case 'bead':
        return gateBead;
      case 'run_failed':
      case 'failed':
        return runFailed;
      case 'review_ready':
      case 'review':
      case 'needs_review':
        return reviewReady;
      default:
        return unknown;
    }
  }
}

/// Something waiting on the person: a pending interaction, a gate bead, a
/// failed run or a review. [rawKind] keeps the provider kind string.
class OrchestrationGate {
  const OrchestrationGate({
    required this.id,
    required this.kind,
    required this.title,
    this.rawKind,
    this.prompt,
    this.workId,
    this.runId,
    this.agentId,
    this.choices = const [],
    this.createdAt,
    this.raw = const {},
  });

  final String id;
  final GateKind kind;

  /// Provider kind string exactly as received; null when absent.
  final String? rawKind;
  final String title;

  /// Longer question or context shown in the gate sheet.
  final String? prompt;
  final String? workId;
  final String? runId;
  final String? agentId;

  /// Options for [GateKind.choice]; empty otherwise.
  final List<String> choices;
  final DateTime? createdAt;

  /// Untouched provider payload.
  final Map<String, Object?> raw;

  @override
  String toString() => 'OrchestrationGate($id, $kind)';
}

/// Lower snake_case of a provider string: trims, splits camelCase, and
/// folds spaces and dashes to underscores, so `needsInput`, `needs-input`
/// and `Needs Input` all compare equal.
String _normalize(String? value) => value == null
    ? ''
    : value
          .trim()
          .replaceAllMapped(
            RegExp(r'([a-z0-9])([A-Z])'),
            (m) => '${m[1]}_${m[2]}',
          )
          .toLowerCase()
          .replaceAll(RegExp(r'[\s-]+'), '_');
