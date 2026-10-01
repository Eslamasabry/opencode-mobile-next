part of 'kit_code_block.dart';

const _breakAfter = {'.', ',', ';', ':', '(', '[', '{', '=', '&', '|', '/'};

/// What never starts a new wrapped line: a closing bracket or separator
/// stays with what it closes.
const _noBreakBefore = {'.', ',', ';', ':', ')', ']', '}'};

/// [line] with a zero-width break chance after punctuation inside a long
/// token, so a wrapping code line breaks at "intent." / "getStringExtra("
/// instead of mid-identifier ("getStringExt" / "ra"), the way a code editor
/// would. A run of the same mark ("::", "==", "&&", "//") stays whole,
/// nothing breaks before a closing bracket or separator, and a short ending
/// after a dot (a file extension, "test.dart") stays with its name. A token with no
/// such mark still breaks where it must. Only the display changes:
/// [KitCodeBlock]'s Copy uses the source, and selection strips the marks
/// again (`_CopiesSource`).
String kitCodeBreakable(String line) {
  if (line.length < 2) return line;
  StringBuffer? out;
  for (var i = 0; i < line.length; i++) {
    final char = line[i];
    out?.write(char);
    if (i == line.length - 1 || !_breakAfter.contains(char)) continue;
    final next = line[i + 1];
    if (next == char || next.trim().isEmpty || _noBreakBefore.contains(next)) {
      continue;
    }
    // "checkout_test.dart": a short ending after a dot (a file extension)
    // stays with its name.
    if (char == '.' && _shortTail(line, i + 1)) continue;
    out ??= StringBuffer(line.substring(0, i + 1));
    out.write(_zeroWidthSpace);
  }
  return out?.toString() ?? line;
}

const _zeroWidthSpace = '\u200B';

/// Whether the word starting at [from] is at most four letters or digits
/// and then ends (a space, the line's end or a closing mark), like a file
/// extension: "dart", "md", "json".
bool _shortTail(String line, int from) {
  var i = from;
  while (i < line.length && _alnum.hasMatch(line[i])) {
    i++;
  }
  final length = i - from;
  if (length == 0 || length > 4) return false;
  return i == line.length || !_continues.hasMatch(line[i]);
}

final _alnum = RegExp(r'[A-Za-z0-9]');

/// What makes a short word after a dot part of a longer expression
/// ("a.b.c", "x.map(", "list.first[").
final _continues = RegExp(r'[A-Za-z0-9_(\[{.]');

/// Selected code copies (and shares) as its source: the zero-width break
/// chances [kitCodeBreakable] adds to a wrapping line are taken out again.
class _CopiesSource extends StatefulWidget {
  const _CopiesSource({required this.child});

  final Widget child;

  @override
  State<_CopiesSource> createState() => _CopiesSourceState();
}

class _CopiesSourceState extends State<_CopiesSource> {
  final _delegate = _SourceSelectionDelegate();

  @override
  void dispose() {
    _delegate.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) =>
      SelectionContainer(delegate: _delegate, child: widget.child);
}

class _SourceSelectionDelegate extends StaticSelectionContainerDelegate {
  @override
  SelectedContent? getSelectedContent() {
    final content = super.getSelectedContent();
    if (content == null || !content.plainText.contains(_zeroWidthSpace)) {
      return content;
    }
    return SelectedContent(
      plainText: content.plainText.replaceAll(_zeroWidthSpace, ''),
    );
  }
}

/// The unwrapped body's sideways scroller: a visible scrollbar on a fine
/// pointer and an edge fade while more content is off-screen at the end
/// edge (K2 §1.9, `space6`).
class _CodeScroller extends StatefulWidget {
  const _CodeScroller({
    super.key,
    required this.child,
    required this.fadeColor,
    required this.fadeWidth,
  });

  final Widget child;
  final Color fadeColor;
  final double fadeWidth;

  @override
  State<_CodeScroller> createState() => _CodeScrollerState();
}

class _CodeScrollerState extends State<_CodeScroller> {
  final ScrollController _controller = ScrollController();

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  bool get _showFade =>
      _controller.hasClients &&
      _controller.position.maxScrollExtent - _controller.offset > .5;

  bool get _showStartFade => _controller.hasClients && _controller.offset > .5;

  /// More lines than fit: the scrollbar stays visible on touch too, so a
  /// clipped line always shows there is more to scroll to.
  bool get _overflowing =>
      _controller.hasClients && _controller.position.maxScrollExtent > .5;

  Widget _edge(BuildContext context, {required bool end}) {
    final direction = Directionality.of(context);
    final from = AlignmentDirectional.centerStart.resolve(direction);
    final to = AlignmentDirectional.centerEnd.resolve(direction);
    return PositionedDirectional(
      end: end ? 0 : null,
      start: end ? null : 0,
      top: 0,
      bottom: 0,
      child: IgnorePointer(
        child: Container(
          width: widget.fadeWidth,
          decoration: BoxDecoration(
            gradient: LinearGradient(
              begin: end ? from : to,
              end: end ? to : from,
              colors: [widget.fadeColor.withValues(alpha: 0), widget.fadeColor],
            ),
          ),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final finePointer = KitLayout.finePointer(context);
    return NotificationListener<ScrollMetricsNotification>(
      onNotification: (_) {
        setState(() {});
        return false;
      },
      child: NotificationListener<ScrollNotification>(
        onNotification: (_) {
          setState(() {});
          return false;
        },
        child: Stack(
          children: [
            Scrollbar(
              controller: _controller,
              thumbVisibility: finePointer || _overflowing,
              notificationPredicate: (n) => true,
              child: SingleChildScrollView(
                controller: _controller,
                scrollDirection: Axis.horizontal,
                // While the scroll cue shows, its thumb lies along the
                // bottom edge: leave that strip below the last line so the
                // thumb never sits on readable text.
                padding: (finePointer || _overflowing)
                    ? EdgeInsets.only(bottom: KitTokens.of(context).space2)
                    : EdgeInsets.zero,
                child: widget.child,
              ),
            ),
            if (_showStartFade) _edge(context, end: false),
            if (_showFade) _edge(context, end: true),
          ],
        ),
      ),
    );
  }
}

/// Where a bounded block puts Copy and whether it offers Wrap (R3).
@immutable
class _CodePlan {
  const _CodePlan({required this.inline, required this.wrapToggle});

  /// Copy (and Wrap, when offered) sit at the end of the first line; there
  /// is no header for them.
  final bool inline;

  /// A line is wider than the block, so the Wrap toggle is worth its place.
  final bool wrapToggle;
}

/// The header's Wrap toggle (R3): an accent glyph while lines wrap, the
/// plain glyph otherwise; never a filled circle. A toggle to assistive tech
/// ("Wrap lines", toggled).
class _WrapToggle extends StatelessWidget {
  const _WrapToggle({
    super.key,
    required this.on,
    required this.label,
    required this.onPressed,
  });

  final bool on;
  final String label;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    final roles = KitTokens.of(context).roles;
    return Semantics(
      container: true,
      toggled: on,
      child: KitTappable(
        onTap: onPressed,
        label: label,
        tooltip: label,
        shape: KitShape.circle,
        child: Icon(
          AppIconography.wrapText,
          size: 24,
          color: on ? roles.accent : roles.text2,
        ),
      ),
    );
  }
}
