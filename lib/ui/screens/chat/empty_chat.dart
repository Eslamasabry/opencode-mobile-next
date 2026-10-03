part of '../chat_screen.dart';

/// What the app can learn cheaply about the folder a new conversation runs
/// in: one file listing and the Git status the server already keeps.
///
/// Every field is optional because each answer may never come (a backend
/// without file browsing, an offline server, an isolated task); the empty
/// state then says less instead of guessing.
@immutable
class ChatStartFacts {
  const ChatStartFacts({
    this.directory,
    this.entries,
    this.git,
    this.hasHistory = false,
    this.changes,
    this.settled = true,
  });

  /// The folder is being looked at; nothing is known about it yet.
  const ChatStartFacts.pending(this.directory)
    : entries = null,
      git = null,
      hasHistory = false,
      changes = null,
      settled = false;

  final String? directory;

  /// Top-level entries other than `.git`, or null when the listing is not
  /// available.
  final int? entries;

  /// True for a Git repository, false when the server says it is not one,
  /// null when unknown.
  final bool? git;

  /// The repository has at least one commit, so "what changed recently" has
  /// something to answer with.
  final bool hasHistory;

  /// Uncommitted changes, or null when Git status is not available.
  final int? changes;

  /// False while the listing and Git status are still on their way.
  final bool settled;

  String get _normalized => (directory?.trim() ?? '').replaceAll('\\', '/');

  /// The last path segment: what a person calls the project.
  String? get projectName {
    final parts = _normalized.split('/').where((part) => part.isNotEmpty);
    return parts.isEmpty ? null : parts.last;
  }

  /// The phone server's folder of projects is where projects live, not a
  /// project itself.
  bool get isProjectsRoot => _normalized.endsWith('/root/projects');

  /// A real project folder the starters and facts can talk about.
  bool get hasProject => projectName != null && !isProjectsRoot;
}

/// One way to start: the words on the chip and what it puts in the composer.
@immutable
class ChatStarter {
  const ChatStarter(this.label);

  final String label;

  /// A label ending in an ellipsis is the start of a sentence; the composer
  /// gets it with a trailing space so the person just keeps typing.
  String get text => label.endsWith('…')
      ? '${label.substring(0, label.length - 1).trimRight()} '
      : label;

  @override
  bool operator ==(Object other) =>
      other is ChatStarter && other.label == label;

  @override
  int get hashCode => label.hashCode;
}

/// The starters for [facts], most useful first.
///
/// A folder created a second ago has no bugs and no history, so it gets
/// ways to make something; a folder with files gets ways to work on them,
/// and "what changed recently" only when Git has commits to read. Nothing
/// is offered while the folder is still being looked at, so the row never
/// shows one set and swaps to another.
@visibleForTesting
List<ChatStarter> chatStartSuggestions(
  ChatStartFacts facts, {
  AppLocalizations? l10n,
}) {
  final strings = l10n ?? lookupAppLocalizations(const Locale('en'));
  if (!facts.settled) return const [];
  if (!facts.hasProject) {
    // No project (the server's own default folder) or the folder that holds
    // the projects: asking about "this project" would ask about nothing.
    return [
      ChatStarter(strings.chatStartListFolder),
      ChatStarter(strings.chatStartFindBug),
    ];
  }
  if (facts.entries == 0) {
    return [
      ChatStarter(strings.chatStartBuildWebPage),
      ChatStarter(strings.chatStartPythonScript),
      ChatStarter(strings.chatStartNodeProject),
      ChatStarter(strings.chatStartReadme),
    ];
  }
  // Files, or a listing the server could not give: work on what is there.
  return [
    ChatStarter(strings.chatStartExplainProject),
    if (facts.hasHistory) ChatStarter(strings.chatStartWhatChanged),
    ChatStarter(strings.chatStartFindBug),
    ChatStarter(strings.chatStartAddTests),
  ];
}

/// The muted line under the project name, such as "Empty folder · Git" or
/// "24 items · Git · 3 changes"; null when there is nothing to say.
@visibleForTesting
String? chatStartFactsLine(ChatStartFacts facts, {AppLocalizations? l10n}) {
  final strings = l10n ?? lookupAppLocalizations(const Locale('en'));
  if (!facts.settled) return strings.chatStartLooking;
  final entries = facts.entries;
  final changes = facts.changes;
  final parts = [
    if (entries == 0)
      strings.chatStartEmptyFolder
    else if (entries != null)
      strings.chatStartItemCount(entries),
    if (facts.git == true) strings.chatStartGit,
    if (facts.git == true && changes != null && changes > 0)
      strings.chatStartChangeCount(changes),
  ];
  return parts.isEmpty ? null : parts.join(' · ');
}

/// The top of a new conversation: where you are and what is there.
///
/// It sits at the bottom of the empty transcript, right above the starters
/// and the composer, so the first message later appears exactly where it
/// was. With the keyboard up (or in a short window) it shrinks to one line
/// and drops the drawing and the tip, and when even that line would not fit
/// it steps aside rather than be cut. The drawing's own caret says the
/// conversation is ready; nothing blinks.
class _ChatStartHeader extends StatelessWidget {
  const _ChatStartHeader({required this.facts, required this.compact});

  final ChatStartFacts facts;
  final bool compact;

  /// The headline role's line, in logical pixels (VL §2: 17/22).
  static const double _headlineLine = 22;

  @override
  Widget build(BuildContext context) {
    final strings = _chatL10n(context);
    final tokens = KitTokens.of(context);
    final textScaler = MediaQuery.textScalerOf(context);
    final factsLine = chatStartFactsLine(facts, l10n: strings);
    // The header's project chip already names the project, so it is not
    // said again here. Only a folder with no project name (the server's own)
    // has no chip, and then its name stays.
    final nameText = facts.projectName != null
        ? null
        : KitText(
            KitBidi.auto(strings.chatStartServerFolder),
            key: const ValueKey('chat-start-name'),
            role: KitTextRole.headline,
            maxLines: compact ? 1 : 2,
            overflow: TextOverflow.ellipsis,
          );
    final Widget content = compact
        ? Row(
            key: const ValueKey('chat-start-header-compact'),
            children: [
              if (nameText != null) Flexible(child: nameText),
              if (factsLine != null) ...[
                if (nameText != null) SizedBox(width: tokens.space3),
                Flexible(
                  child: KitText(
                    factsLine,
                    key: const ValueKey('chat-start-facts'),
                    role: KitTextRole.secondary,
                    tone: KitTextTone.secondary,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
              ],
            ],
          )
        : Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              // A fresh folded sheet, its caret waiting: the drawing every
              // "no conversations yet" shares (design standard §10). Only
              // with room; the compact line keeps the space for the
              // composer.
              const KitIllustration(
                key: ValueKey('chat-start-drawing'),
                scene: StatesSheetScene(),
                width: KitTokens.illustrationInline,
              ),
              SizedBox(height: tokens.space3),
              ?nameText,
              if (factsLine != null) ...[
                if (nameText != null) SizedBox(height: tokens.space1),
                KitText(
                  factsLine,
                  key: const ValueKey('chat-start-facts'),
                  role: KitTextRole.secondary,
                  tone: KitTextTone.secondary,
                ),
              ],
              SizedBox(height: tokens.space4),
              KitText(
                strings.chatStartTip,
                key: const ValueKey('chat-start-tip'),
                role: KitTextRole.caption,
                tone: KitTextTone.tertiary,
              ),
            ],
          );
    // One line of the name plus its padding: below this the compact header
    // would be clipped, so it is left out instead.
    final compactHeight = textScaler.scale(_headlineLine) + tokens.space1 * 2;
    return LayoutBuilder(
      builder: (context, constraints) {
        if (compact &&
            constraints.hasBoundedHeight &&
            constraints.maxHeight < compactHeight) {
          return const SizedBox.shrink();
        }
        // Reversed, so the lines nearest the composer stay in view and the
        // rest scroll when taller than the space (large text, short window).
        return ListView(
          reverse: true,
          padding: EdgeInsets.zero,
          children: [
            Center(
              child: ConstrainedBox(
                constraints: const BoxConstraints(
                  maxWidth: KitLayout.readingWidth,
                ),
                child: Padding(
                  padding: EdgeInsetsDirectional.symmetric(
                    horizontal: tokens.gutter,
                    vertical: compact ? tokens.space1 : tokens.space4,
                  ),
                  child: Align(
                    alignment: AlignmentDirectional.bottomStart,
                    child: Semantics(
                      container: true,
                      child: KitEntrance(child: content),
                    ),
                  ),
                ),
              ),
            ),
          ],
        );
      },
    );
  }
}

/// The tallest the starter row gets at [textScaler]: one line of chip label,
/// the chip's own padding and border, never under the 48dp touch target.
@visibleForTesting
double chatStartersHeight(TextScaler textScaler) =>
    math.max(48.0, textScaler.scale(20) + 18) + 2;

/// The empty transcript: the header fills the space and the starters sit at
/// its foot, which is the top edge of the composer.
///
/// The row lives inside the flexible area rather than beside the composer
/// so a short window (large text, keyboard up, landscape) takes it away
/// before the composer loses a pixel: the composer is what the person
/// needs, the starters are a shortcut.
class _ChatStartArea extends StatelessWidget {
  const _ChatStartArea({required this.header, required this.starters});

  final Widget header;
  final Widget? starters;

  @override
  Widget build(BuildContext context) {
    final rowHeight = chatStartersHeight(MediaQuery.textScalerOf(context));
    return LayoutBuilder(
      builder: (context, constraints) {
        final row = starters;
        final fits =
            !constraints.hasBoundedHeight || constraints.maxHeight >= rowHeight;
        return Column(
          children: [
            Expanded(child: header),
            if (row != null && fits) row,
          ],
        );
      },
    );
  }
}

/// The ways to start, as one row right above the composer, so they stay
/// next to where you type with the keyboard up. A tap fills the composer;
/// nothing is sent until the person sends it.
class _ChatStarters extends StatelessWidget {
  const _ChatStarters({required this.starters, required this.onPick});

  final List<ChatStarter> starters;
  final ValueChanged<ChatStarter> onPick;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final set = starters.map((starter) => starter.label).join('|');
    return Semantics(
      container: true,
      label: _chatL10n(context).chatStartSuggestionsLabel,
      explicitChildNodes: true,
      child: SizedBox(
        height: chatStartersHeight(MediaQuery.textScalerOf(context)),
        // A new set (the folder's facts arrived) arrives again instead of
        // silently swapping words.
        child: KitEntrance(
          trigger: set,
          child: ListView(
            key: const ValueKey('chat-starters'),
            scrollDirection: Axis.horizontal,
            padding: EdgeInsetsDirectional.symmetric(horizontal: tokens.gutter),
            children: [
              for (final starter in starters)
                Padding(
                  padding: EdgeInsetsDirectional.only(end: tokens.space2),
                  child: Center(
                    child: KitChip.action(
                      key: ValueKey('chat-starter-${starter.label}'),
                      label: starter.label,
                      onPressed: () => onPick(starter),
                    ),
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }
}
