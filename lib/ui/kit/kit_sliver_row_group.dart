import 'dart:math' as math;

import 'package:flutter/material.dart';

import 'kit_divider.dart';
import 'kit_layout.dart';
import 'kit_max_width.dart';
import 'kit_row.dart';
import 'kit_section_label.dart';
import 'kit_surface_look.dart';
import 'kit_tokens.dart';
import 'style/kit_signal.dart';

/// Rows on one panel in a lazy list (visual language §4, §5; slice-R4):
/// the sliver twin of [KitRowGroup], for a list too long to build at once.
/// `surface1` with [KitTokens.panelCornerRadius] corners, a hairline of one
/// physical pixel between rows inset to where their words start, and an
/// optional [label] above on the same inset as a [KitRowGroup]'s.
///
/// The panel holds [children] first (a list's head: the few rows that are
/// always there), then [itemCount] rows from [itemBuilder], built only when
/// they scroll into view. So Work's head rows and its recent conversations
/// are one panel, not two.
///
/// Place it among a [CustomScrollView]'s slivers. [margin] is the gutter at
/// the sides by default; the label keeps [KitTokens.sectionGap] from the
/// sliver before it ([gapBefore], as [KitSectionLabel]) and none as the
/// first sliver.
///
/// Unlike [KitRowGroup] it does not move destructive rows last: a lazy list
/// holds the person's things, and an act that ends one lives on the row's
/// menu (`KitRow.menu`), not on a row of its own.
///
/// States: none — each row carries its own states; an empty group (no
/// children, no items) draws nothing, not a label over an empty panel.
class KitSliverRowGroup extends StatelessWidget {
  const KitSliverRowGroup({
    super.key,
    this.children = const [],
    this.itemCount = 0,
    this.itemBuilder,
    this.findChildIndexCallback,
    this.label,
    this.labelTrailing,
    this.labelTerm,
    this.leadingIcons,
    this.margin,
    this.gapBefore,
  }) : assert(itemCount >= 0),
       assert(
         itemCount == 0 || itemBuilder != null,
         'KitSliverRowGroup: itemCount rows need an itemBuilder',
       );

  /// The rows always at the top of the panel, built with it.
  final List<Widget> children;

  /// How many rows [itemBuilder] builds after [children].
  final int itemCount;

  /// Builds row [index] (0 to [itemCount] − 1, counted after [children])
  /// when it scrolls into view.
  final NullableIndexedWidgetBuilder? itemBuilder;

  /// For keyed rows that move: the [itemBuilder] index of the row with
  /// [key], or null when it is gone (SliverChildBuilderDelegate).
  final int? Function(Key key)? findChildIndexCallback;

  /// The section's name above the panel (never uppercase).
  final String? label;

  /// A small status or action at the end of the label's line.
  final Widget? labelTrailing;

  /// What [label] means, shown by a [KitTerm] on its words.
  final String? labelTerm;

  /// Whether the rows lead with an icon tile: the hairlines then start
  /// where the words do. Null (the default) reads it off [children] when
  /// they are the whole list ([KitRowGroup.rowsLead]); rows built later
  /// cannot be read ahead, so a list with [itemCount] rows assumes icons
  /// unless told.
  final bool? leadingIcons;

  /// Around the group; the screen gutter at the sides by default.
  final EdgeInsetsGeometry? margin;

  /// The space above the label; see [KitSectionLabel.gapBefore]. Without a
  /// label, a number here is a plain gap above the panel.
  final double? gapBefore;

  @override
  Widget build(BuildContext context) => KitMaxWidth.sliver(
    maxWidth: KitLayout.listWidth,
    sliver: _buildContent(context),
  );

  Widget _buildContent(BuildContext context) {
    final tokens = KitTokens.of(context);
    final total = children.length + itemCount;
    if (total == 0) return const SliverToBoxAdapter(child: SizedBox.shrink());
    final label = this.label;
    // Signal: the rows run on the track, their words on the list's text
    // line, so the rules start there and the label stands over the words.
    final track = KitTrack.on(context);
    final leads =
        track ||
        (leadingIcons ?? (itemCount > 0 || KitRowGroup.rowsLead(children)));
    final inset = leads ? KitDividerInset.text : KitDividerInset.gutter;
    final radius = Radius.circular(tokens.panelCornerRadius);
    final head = children.length;
    final find = findChildIndexCallback;
    final margin =
        this.margin ?? EdgeInsets.symmetric(horizontal: tokens.gutter);
    final sides = margin.resolve(Directionality.of(context));
    // Flat lists (Solid): the rows sit on the page itself, with square
    // ends.
    final flat = KitSurfaceLook.flatLists(context);
    final panel = flat
        ? KitSurfaceLook.bare
        : KitSurfaceLook.decorate(
            context,
            solid: BoxDecoration(
              color: tokens.roles.surface1,
              borderRadius: BorderRadius.all(radius),
            ),
          );
    // As a KitRowGroup: a panel the style sets straight on the page (no
    // fill, edge or shadow: Paper) lets its rows reach out into the margin
    // by their own inset, so their words start flush with the label above
    // and the row inset stays inside the press highlight.
    final reach = KitSurfaceDecoration.isBare(panel)
        ? math.min(tokens.space4, math.min(sides.left, sides.right))
        : 0.0;
    final back = EdgeInsets.symmetric(horizontal: reach);
    // The rules between the rows stop at the margin at the end too, as the
    // section rule above them does.
    final rule = KitDivider(inset: inset, endInset: reach);

    Widget? item(BuildContext context, int index) {
      final Widget? row = index < head
          ? children[index]
          : itemBuilder!(context, index - head);
      if (row == null) return null;
      final first = index == 0;
      final last = index == total - 1;
      Widget cell = first
          ? row
          : Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [rule, row],
            );
      // A surface for anything inside that paints ink; the panel itself is
      // painted once, under the whole list, by the DecoratedSliver.
      cell = Material(type: MaterialType.transparency, child: cell);
      if ((first || last) && !flat) {
        // Only the ends touch the panel's corners: their hover and pressed
        // fills are cut to them.
        cell = ClipRRect(
          borderRadius: BorderRadius.vertical(
            top: first ? radius : Radius.zero,
            bottom: last ? radius : Radius.zero,
          ),
          child: cell,
        );
      }
      return cell;
    }

    final labelMargin = track
        ? EdgeInsetsDirectional.only(
            start: reach + tokens.leadColumn,
            end: reach,
          )
        : back;
    Widget group = SliverMainAxisGroup(
      slivers: [
        if (label != null)
          SliverToBoxAdapter(
            child: KitSectionLabel(
              label,
              trailing: labelTrailing,
              explanation: labelTerm,
              margin: labelMargin,
              gapBefore: gapBefore,
            ),
          )
        else if (gapBefore case final gap? when gap > 0)
          SliverToBoxAdapter(child: SizedBox(height: gap)),
        kitSurfaceSliver(
          style: KitSurfaceLook.styleOf(context),
          decoration: panel,
          sliver: SliverList(
            delegate: SliverChildBuilderDelegate(
              item,
              childCount: total,
              findChildIndexCallback: find == null
                  ? null
                  : (key) {
                      final index = find(key);
                      return index == null ? null : index + head;
                    },
            ),
          ),
        ),
      ],
    );
    if (track) {
      // The dashed siding down the whole group, label and gaps included;
      // a live row lays the solid track over it (KitFeedItem).
      group = DecoratedSliver(
        decoration: KitTrackDecoration(
          colors: kitSignalColors(context),
          centre: KitTrack.rowCentre(tokens),
        ),
        sliver: group,
      );
    }
    return SliverPadding(
      // Resolved: a sliver's padding is checked side by side.
      padding: reach > 0 ? sides - back : margin,
      sliver: group,
    );
  }
}
