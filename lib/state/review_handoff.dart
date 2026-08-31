import 'package:flutter/foundation.dart';

/// Where a staged reference was picked up, so the composer chip and the
/// formatted prompt can say what the user actually pointed at.
enum ReviewReferenceKind {
  /// A project file browsed in Files — path only, no content.
  file,

  /// A file listed as changed by version control.
  changedFile,

  /// One diff hunk selected in the review workspace.
  hunk,

  /// An arbitrary line range selected in the review workspace.
  selection,

  /// A written review comment about a file, hunk, or selection.
  comment,
}

/// Which diff the reference was read from. `session` diffs are what the
/// server attributes to one session; on OpenCode 2 `session.diff` actually
/// returns the working-tree diff, so the label stays honest rather than
/// claiming per-session precision the server does not give.
enum ReviewReferenceScope { none, session, workingTree, branch }

/// A structured pointer from Files/Changes/Review back into a chat prompt.
///
/// Deliberately *not* pre-rendered text: the composer decides how several
/// staged references read together, and the user can drop one before
/// sending. A reference is also not a [PromptAttachment] — nothing is
/// uploaded, the file bytes are never read, and the reference becomes plain
/// text in the prompt the agent receives.
@immutable
class ReviewReference {
  const ReviewReference({
    required this.id,
    required this.kind,
    required this.path,
    this.scope = ReviewReferenceScope.none,
    this.lineLabel,
    this.snippet,
    this.comment,
    this.added,
    this.removed,
    this.status,
  });

  /// Stable identity for removal from the composer.
  final String id;
  final ReviewReferenceKind kind;

  /// Project-relative path, e.g. `lib/ui/screens/chat_screen.dart`.
  final String path;
  final ReviewReferenceScope scope;

  /// Human line description produced by the review workspace, e.g.
  /// `old lines 3–8 · new lines 4–9`. Null for whole-file references.
  final String? lineLabel;

  /// Diff or source text the reference points at, already trimmed. Null when
  /// the reference is only a path.
  final String? snippet;

  /// The reviewer's own words about this change.
  final String? comment;

  final int? added;
  final int? removed;

  /// Version-control status word (`modified`, `added`, `deleted`, …).
  final String? status;

  String get basename {
    final parts = path.split('/').where((part) => part.isNotEmpty).toList();
    return parts.isEmpty ? path : parts.last;
  }

  /// Short chip label: the file, plus the line range when there is one.
  String get label {
    final line = lineLabel;
    if (line == null || line.isEmpty) return basename;
    return '$basename · $line';
  }

  /// One-line description of what the reference is, for tooltips and
  /// screen readers.
  String get description {
    final kindLabel = switch (kind) {
      ReviewReferenceKind.file => 'File',
      ReviewReferenceKind.changedFile => 'Changed file',
      ReviewReferenceKind.hunk => 'Diff hunk',
      ReviewReferenceKind.selection => 'Selected lines',
      ReviewReferenceKind.comment => 'Review comment',
    };
    final parts = <String>[kindLabel, path];
    final line = lineLabel;
    if (line != null && line.isNotEmpty) parts.add(line);
    final scopeLabel = scopeName(scope);
    if (scopeLabel != null) parts.add(scopeLabel);
    return parts.join(' · ');
  }

  /// Signature used to collapse duplicate stages of the same thing. A
  /// comment always stages separately: two comments on one range are two
  /// distinct notes.
  String get signature => kind == ReviewReferenceKind.comment
      ? id
      : '$kind|$path|${lineLabel ?? ''}|${scope.name}';

  /// The block this reference contributes to the sent prompt. Structured
  /// markdown so the agent can act on it without guessing what the user
  /// was looking at.
  ///
  /// [snippetBudget] caps this reference's share of the send-wide snippet
  /// budget (see [ReviewReference.maxSnippetChars]); null means unbounded.
  /// Anything cut is *stated in the rendered text*: a diff the agent
  /// believes is complete when it is not is worse than no diff at all.
  String toPromptText({int? snippetBudget}) {
    final out = StringBuffer('`$path`');
    final detail = <String>[
      if (lineLabel != null && lineLabel!.isNotEmpty) lineLabel!,
      if (status != null && status!.isNotEmpty) status!,
      if (added != null || removed != null) '+${added ?? 0} −${removed ?? 0}',
      ?scopeName(scope),
    ];
    if (detail.isNotEmpty) out.write(' (${detail.join(' · ')})');
    final note = comment?.trim();
    if (note != null && note.isNotEmpty) out.write('\n$note');
    final body = snippet?.trimRight();
    if (body != null && body.isNotEmpty) {
      final trimmed = _truncateSnippet(body, snippetBudget);
      final fence = _fenceFor(trimmed.text);
      if (trimmed.text.isNotEmpty) {
        out.write('\n\n${fence}diff\n${trimmed.text}\n$fence');
      }
      if (trimmed.omitted > 0) {
        final lines = '\n'.allMatches(body).length + 1;
        final shownLines = trimmed.text.isEmpty
            ? 0
            : '\n'.allMatches(trimmed.text).length + 1;
        out.write(
          '\n[Snippet truncated to fit the prompt: showing $shownLines of '
          '$lines lines; ${trimmed.omitted} characters omitted. Read '
          '`$path` for the rest.]',
        );
      }
    }
    return out.toString();
  }

  /// Keeps whole lines and reports what it dropped.
  static ({String text, int omitted}) _truncateSnippet(
    String body,
    int? budget,
  ) {
    if (budget == null || body.length <= budget) {
      return (text: body, omitted: 0);
    }
    if (budget <= 0) return (text: '', omitted: body.length);
    final breakAt = body.lastIndexOf('\n', budget);
    final kept = body.substring(0, breakAt > 0 ? breakAt : budget).trimRight();
    return (text: kept, omitted: body.length - kept.length);
  }

  /// A fence long enough that the snippet's own backticks cannot close it.
  /// Diff hunks of markdown routinely contain ``` runs, and a three-backtick
  /// fence around one breaks the block in half — the tail of the diff then
  /// reads as prose the agent may act on.
  static String _fenceFor(String body) {
    var longest = 0;
    var run = 0;
    for (final unit in body.codeUnits) {
      run = unit == 0x60 ? run + 1 : 0;
      if (run > longest) longest = run;
    }
    return '`' * (longest < 3 ? 3 : longest + 1);
  }

  static String? scopeName(ReviewReferenceScope scope) => switch (scope) {
    ReviewReferenceScope.none => null,
    ReviewReferenceScope.session => 'session changes',
    ReviewReferenceScope.workingTree => 'working tree',
    ReviewReferenceScope.branch => 'branch changes',
  };

  /// Characters of snippet body one send may carry across every staged
  /// reference.
  ///
  /// [ReviewHandoffStore.maxPerSession] bounds how *many* references a send
  /// carries but not how large they are: ten whole-file diffs is a prompt
  /// the model never gets to answer, and the user is given no hint that the
  /// context window is where their question went. Roughly 24k characters is
  /// several screens of diff — far more than a review pass needs — while
  /// still leaving room for the conversation around it. Comments and paths
  /// are not counted: they are typed by hand and bounded in practice.
  static const maxSnippetChars = 24000;

  /// Renders a staged set as the prompt preamble the composer sends.
  ///
  /// Snippet bodies share [budget] max-min fairly: every reference that fits
  /// its equal share is sent whole, and what they leave unused is
  /// redistributed to the large ones. So one huge diff is trimmed rather
  /// than starving the nine small references staged beside it.
  static String format(
    Iterable<ReviewReference> references, {
    int budget = maxSnippetChars,
  }) {
    final staged = references.toList();
    final budgets = _snippetBudgets(staged, budget);
    final blocks = <String>[];
    for (var i = 0; i < staged.length; i++) {
      final text = staged[i].toPromptText(snippetBudget: budgets[i]);
      if (text.trim().isNotEmpty) blocks.add(text);
    }
    if (blocks.isEmpty) return '';
    final heading = blocks.length == 1 ? 'Reference:' : 'References:';
    return '$heading\n\n${blocks.join('\n\n')}';
  }

  /// Per-reference snippet allowances; null means "send it whole".
  static List<int?> _snippetBudgets(
    List<ReviewReference> references,
    int total,
  ) {
    final budgets = List<int?>.filled(references.length, null);
    final sizes = [
      for (final reference in references)
        reference.snippet?.trimRight().length ?? 0,
    ];
    final pending = [
      for (var i = 0; i < sizes.length; i++)
        if (sizes[i] > 0) i,
    ];
    if (pending.isEmpty) return budgets;
    var remaining = total < 0 ? 0 : total;
    while (pending.isNotEmpty) {
      final share = remaining ~/ pending.length;
      final fits = [
        for (final index in pending)
          if (sizes[index] <= share) index,
      ];
      if (fits.isEmpty) {
        for (final index in pending) {
          budgets[index] = share;
        }
        break;
      }
      for (final index in fits) {
        budgets[index] = sizes[index];
        remaining -= sizes[index];
      }
      pending.removeWhere(fits.contains);
    }
    return budgets;
  }
}

/// What happened when a surface tried to stage a reference, so the caller
/// can confirm non-modally instead of guessing.
enum ReviewStageOutcome { staged, duplicate, full }

/// Shared Files → Changes → Review → Prompt handoff.
///
/// Every add-to-prompt affordance writes here and the originating chat's
/// composer reads from here, so a review finding reaches the prompt without
/// a round trip through the clipboard.
///
/// References are held per session and **only in memory**: they are pointers
/// into a diff the server still holds, cheap to restage, and stale the
/// moment the working tree moves — persisting them would resurrect chips
/// aimed at line ranges that no longer exist. That choice is not left for
/// the user to discover: the composer's reference note says the chips are
/// not saved with the draft, matching the note attachments already carry.
class ReviewHandoffStore extends ChangeNotifier {
  ReviewHandoffStore();

  /// App-wide instance used by the chat screen and the surfaces it opens.
  static final ReviewHandoffStore instance = ReviewHandoffStore();

  /// Enough to collect a review pass without turning the composer into a
  /// list; beyond this the prompt stops being readable anyway.
  static const maxPerSession = 10;

  final Map<String, List<ReviewReference>> _staged = {};
  int _ids = 0;

  /// Monotonic id for a new reference, unique within the app run.
  String nextID(String prefix) => '$prefix-${++_ids}';

  List<ReviewReference> referencesFor(String sessionID) =>
      List.unmodifiable(_staged[sessionID] ?? const <ReviewReference>[]);

  bool hasReferences(String sessionID) =>
      (_staged[sessionID]?.isNotEmpty ?? false);

  ReviewStageOutcome stage(String sessionID, ReviewReference reference) {
    final list = _staged.putIfAbsent(sessionID, () => <ReviewReference>[]);
    if (list.any((staged) => staged.signature == reference.signature)) {
      return ReviewStageOutcome.duplicate;
    }
    if (list.length >= maxPerSession) return ReviewStageOutcome.full;
    list.add(reference);
    notifyListeners();
    return ReviewStageOutcome.staged;
  }

  void remove(String sessionID, String id) {
    final list = _staged[sessionID];
    if (list == null) return;
    final before = list.length;
    list.removeWhere((reference) => reference.id == id);
    if (list.isEmpty) _staged.remove(sessionID);
    if (list.length != before) notifyListeners();
  }

  /// Drops everything staged for [sessionID], e.g. once the references have
  /// been folded into a sent prompt.
  List<ReviewReference> take(String sessionID) {
    final list = _staged.remove(sessionID);
    if (list == null || list.isEmpty) return const [];
    notifyListeners();
    return List.unmodifiable(list);
  }

  void clear(String sessionID) => take(sessionID);
}

/// [ReviewHandoffStore] bound to one chat session, so Files and Review only
/// carry a single handle rather than a store plus an id.
@immutable
class ReviewHandoffSession {
  const ReviewHandoffSession({required this.store, required this.sessionID});

  final ReviewHandoffStore store;
  final String sessionID;

  List<ReviewReference> get references => store.referencesFor(sessionID);

  ReviewStageOutcome stage(ReviewReference reference) =>
      store.stage(sessionID, reference);

  String nextID(String prefix) => store.nextID(prefix);
}
