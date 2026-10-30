part of 'kit_top_bar.dart';

/// The tail of a server name that tells servers on one host apart: the
/// ":port" and whatever follows it ("(secure)", " +2").
final _kitServerNameTail = RegExp(r':\d+(?:\s.*)?$');

/// The server's name on one line. When it is too long the host is cut with an
/// ellipsis, never the port: "Computer at 10.0…:4443 (secure)". When even the
/// port leaves no room for a letter of the host and the ellipsis (the status
/// word beside it on a narrow pill), the whole name ends in one ellipsis
/// instead of overflowing.
Widget _kitServerNameLine(String server, {required KitTextRole role}) {
  final match = _kitServerNameTail.firstMatch(server);
  Widget whole() => KitText(
    KitBidi.auto(server),
    role: role,
    tone: KitTextTone.primary,
    maxLines: 1,
    overflow: TextOverflow.ellipsis,
  );
  if (match == null || match.start == 0) return whole();
  final tail = match.group(0)!;
  return LayoutBuilder(
    builder: (context, constraints) {
      final style = KitText.styleOf(context, role, tone: KitTextTone.primary);
      final painter = TextPainter(
        text: TextSpan(text: tail, style: style),
        textDirection: TextDirection.ltr,
        textScaler: MediaQuery.textScalerOf(context),
        maxLines: 1,
      )..layout();
      final room = painter.width + 2 * painter.preferredLineHeight;
      painter.dispose();
      if (constraints.maxWidth < room) return whole();
      return Row(
        key: const ValueKey('kit-shell-server-name'),
        mainAxisSize: MainAxisSize.min,
        textDirection: TextDirection.ltr,
        children: [
          Flexible(
            child: KitText(
              server.substring(0, match.start),
              role: role,
              tone: KitTextTone.primary,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
          ),
          KitText(
            tail,
            role: role,
            tone: KitTextTone.primary,
            maxLines: 1,
            softWrap: false,
          ),
        ],
      );
    },
  );
}

const _kitServerStatusKey = ValueKey('kit-shell-server-status');

/// The pill's look: the state mark, the server's name, its status word and
/// the switcher chevron (which never mirrors: it points down).
class _KitServerPillContent extends StatelessWidget {
  const _KitServerPillContent({
    required this.server,
    required this.status,
    required this.tone,
    required this.expand,
  });

  final String server;
  final String status;
  final AppStatusTone tone;
  final bool expand;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final dotColor = switch (tone) {
      AppStatusTone.neutral => roles.text3,
      AppStatusTone.progress => roles.accent,
      AppStatusTone.ok => roles.success,
      AppStatusTone.attention => roles.attention,
      AppStatusTone.failure => roles.text1,
    };
    final Widget mark = tone == AppStatusTone.progress
        ? KitStatusMark(state: KitMarkState.working, label: status)
        : SizedBox.square(
            dimension: tokens.iconSize(context, tokens.smallIconSize),
            child: Center(
              child: DecoratedBox(
                decoration: BoxDecoration(
                  color: dotColor,
                  shape: BoxShape.circle,
                ),
                child: SizedBox.square(dimension: tokens.space2),
              ),
            ),
          );
    // The PC sidebar (296 dp) never cuts the name: it wraps, and the status
    // takes a second line. "Connected" is left to the green dot there (the
    // pill's label still says it); Offline and Reconnecting keep the word.
    if (expand) {
      final word = tone == AppStatusTone.ok ? null : status;
      return ConstrainedBox(
        constraints: BoxConstraints(minHeight: tokens.minTarget),
        child: Padding(
          padding: EdgeInsetsDirectional.only(
            start: tokens.space3,
            end: tokens.space2,
            top: tokens.space1,
            bottom: tokens.space1,
          ),
          child: Row(
            children: [
              mark,
              SizedBox(width: tokens.space2),
              Expanded(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    KitText(KitBidi.auto(server), role: KitTextRole.rowTitle),
                    if (word != null)
                      KitText(
                        word,
                        role: KitTextRole.caption,
                        tone: KitTextTone.secondary,
                      ),
                  ],
                ),
              ),
              SizedBox(width: tokens.space1),
              Icon(
                AppIconography.chevronDown,
                size: tokens.iconSize(context, tokens.smallIconSize),
                color: roles.text2,
              ),
            ],
          ),
        ),
      );
    }
    // One line (the owner's picked concepts, 2026-10-22): the dot, then
    // "name · state", then the chevron. The state word stays whole on
    // screen (STATE-9): a long name is cut first, and keeps its port
    // ([_kitServerNameLine]). At large text "Connected" is left to the
    // green dot (the pill's label still says it), so the name keeps the
    // room; any other word stays.
    // 1.29, not 1.3: Android hands the font scale over as a float, so its
    // 1.3 arrives as 1.2999999523 and missed a `>= 1.3` (impeccable audit
    // 2026-10-28, #7: "On this ph… · Connected" at 1.3).
    final large = MediaQuery.textScalerOf(context).scale(1) >= 1.29;
    final showStatus = !(large && tone == AppStatusTone.ok);
    // The concepts' words: the chips' size and weight, in the primary ink.
    // When the state word and a little of the name cannot share the line
    // (a narrow phone, a long word, large text), the pill goes back to two
    // lines, the name over the word, so the word is never cut.
    const separator = ' · ';
    Widget words(String text, {Key? key}) => KitText(
      text,
      key: key,
      role: KitTextRole.secondary,
      tone: KitTextTone.primary,
      maxLines: 1,
      softWrap: false,
    );
    final label = !showStatus
        ? _kitServerNameLine(server, role: KitTextRole.secondary)
        : LayoutBuilder(
            builder: (context, constraints) {
              final painter = TextPainter(
                text: TextSpan(
                  text: '$separator$status',
                  style: KitText.styleOf(
                    context,
                    KitTextRole.secondary,
                    tone: KitTextTone.primary,
                  ),
                ),
                textDirection: Directionality.of(context),
                textScaler: MediaQuery.textScalerOf(context),
                maxLines: 1,
              )..layout();
              final room = painter.width + 2 * painter.preferredLineHeight;
              final tailWidth = painter.width;
              painter.dispose();
              // Connected, and the name would be cut beside the word on the
              // one line: the name keeps the room and the green dot says
              // Connected (the pill's label still says it). The name is
              // never cut for a word the dot already shows; any other state
              // keeps its word.
              if (tone == AppStatusTone.ok && constraints.maxWidth >= room) {
                final name = TextPainter(
                  text: TextSpan(
                    text: KitBidi.auto(server),
                    style: KitText.styleOf(
                      context,
                      KitTextRole.secondary,
                      tone: KitTextTone.primary,
                    ),
                  ),
                  textDirection: Directionality.of(context),
                  textScaler: MediaQuery.textScalerOf(context),
                  maxLines: 1,
                )..layout();
                final whole = name.width + tailWidth <= constraints.maxWidth;
                name.dispose();
                if (!whole) {
                  return _kitServerNameLine(
                    server,
                    role: KitTextRole.secondary,
                  );
                }
              }
              if (constraints.maxWidth >= room) {
                return Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Flexible(
                      child: _kitServerNameLine(
                        server,
                        role: KitTextRole.secondary,
                      ),
                    ),
                    words(separator),
                    words(status, key: _kitServerStatusKey),
                  ],
                );
              }
              return Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  _kitServerNameLine(server, role: KitTextRole.secondary),
                  KitText(
                    status,
                    key: _kitServerStatusKey,
                    role: KitTextRole.caption,
                    tone: KitTextTone.secondary,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                  ),
                ],
              );
            },
          );
    // Roomy gaps at ordinary sizes; at large text the name gets the room.
    final gap = large ? tokens.space1 : tokens.space3;
    return ConstrainedBox(
      constraints: BoxConstraints(minHeight: tokens.minTarget),
      child: Padding(
        padding: EdgeInsetsDirectional.only(
          start: gap,
          end: gap,
          top: tokens.space1,
          bottom: tokens.space1,
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            mark,
            SizedBox(width: gap),
            Flexible(child: label),
            SizedBox(width: tokens.space2),
            Icon(
              AppIconography.chevronDown,
              size: tokens.iconSize(context, tokens.smallIconSize),
              color: roles.text2,
            ),
          ],
        ),
      ),
    );
  }
}
