import 'dart:math' as math;

import 'package:flutter/rendering.dart';
import 'package:flutter/widgets.dart';

import '../desktop/desktop_interaction.dart';

/// Material 3 window size classes (docs/ux-system/kit-v2.md §8.1), measured
/// on the window, never the device: a phone in landscape is [medium] by
/// width, a tablet in split screen can be [compact].
///
/// compact < 600 dp · medium 600–839 · expanded 840–1199 · large ≥ 1200.
enum KitWindow {
  compact,
  medium,
  expanded,
  large;

  /// A tablet in landscape, a PC or the web: room for a centred panel.
  bool get isWide => index >= KitWindow.expanded.index;
}

/// The one answer to "how big is this window, and how is it used?" for
/// every kit part (§8.1). A part reads only this; nothing in `lib/ui/`
/// compares a width to a literal (gate G15, `test/kit_ratchet_test.dart`).
abstract final class KitLayout {
  /// Where each window class starts, in dp.
  static const double mediumFrom = 600;
  static const double expandedFrom = 840;
  static const double largeFrom = 1200;

  /// Below this height a window keeps the compact arrangement of a part
  /// that stacks vertically, whatever its width (a phone in landscape).
  static const double shortHeight = 480;

  /// Forms, settings and a sheet's content.
  static const double readingWidth = 720;

  /// A list on its own.
  static const double listWidth = 960;

  /// The list pane when a screen shows two or three panes (KitScreen.md,
  /// KitNav.md; LAY-5: 296, changed from 360).
  static const double paneListWidth = 296;

  /// The detail pane's maximum width, and the conversation's cap
  /// (KitScreen.md, KitMarkdown.md, KitAskLine.md; LAY-5).
  static const double paneDetailMaxWidth = 700;

  /// The side pane of a three-pane PC window (KitScreen.md).
  static const double paneSideWidth = 340;

  /// The PC three panes, list · detail · side (KitScreen.md: 296/700/340).
  static const double pcListPane = paneListWidth;
  static const double pcDetailPane = paneDetailMaxWidth;
  static const double pcSidePane = paneSideWidth;

  /// The PC sidebar's widest, and its largest share of the window
  /// ([sidebarWidth]).
  static const double sidebarMaxWidth = 400;
  static const double sidebarMaxShare = 1 / 3;

  /// The PC sidebar's width (KitNav.md): [paneListWidth] at 1.0 text, and
  /// wider with larger text — the list width times the text scale, up to
  /// [sidebarMaxWidth] and never past [sidebarMaxShare] of the window — so
  /// its header, rows and pinned primary keep their words whole at 2.0
  /// instead of cutting or breaking them (A11Y-2, A11Y-8).
  static double sidebarWidth(BuildContext context) {
    final scale = MediaQuery.textScalerOf(context).scale(1);
    final cap = math.min(
      sidebarMaxWidth,
      MediaQuery.sizeOf(context).width * sidebarMaxShare,
    );
    return (paneListWidth * scale).clamp(
      paneListWidth,
      math.max(paneListWidth, cap),
    );
  }

  /// The navigation rail on a medium window (KitNav.md, KitBottomInset.md).
  static const double railWidth = 80;

  /// An undo bar's maximum width (KitUndo.md).
  static const double undoMaxWidth = 480;

  /// The popover widths the KitMenu panel and the KitTerm bubble share
  /// (KitMenu.md, KitTerm.md; README.md decision D2).
  static const double popoverMinWidth = 200;
  static const double popoverMaxWidth = 320;

  /// A prompt bubble's maximum share of the available width, used by the
  /// `compact` option only (KitMessage.md).
  static const double bubbleMaxShare = .85;

  /// The default (auto) prompt bubble hugs its words up to the full
  /// transcript width minus this fixed start inset: the bubble's end edge
  /// sits on the page gutter, and prose always keeps a visible start margin.
  static const double bubbleStartInset = 48;

  /// A prompt bubble's inner padding: symmetric and small so the words get
  /// the width (the horizontal is `KitTokens.space3`, 12).
  static const double bubblePaddingVertical = 10;

  /// A sent photo's square thumbnail inside a prompt bubble (KitMessage.md):
  /// three fit a phone's bubble side by side.
  static const double promptPhotoSize = 96;

  /// The composer field's height cap as a share of the window height
  /// (KitComposer.md).
  static const double composerMaxShare = .4;

  /// A board lane: its cap on a phone or medium window, how much of each
  /// neighbour peeks on compact, and the narrowest side by side
  /// (KitBoardLane.md; LAY-2).
  static const double laneMaxWidth = 400;
  static const double lanePeek = 20;
  static const double laneMinWidth = 296;

  /// A page state's width (KitStateView.md).
  static const double stateMaxWidth = 440;

  /// A bottom sheet on a medium window: capped and centred (§8.2).
  static const double sheetMaxWidth = 640;

  /// A sheet shown as a centred panel on an expanded or large window.
  static const double dialogPanelWidth = 560;

  /// A confirmation: capped on a medium window, a centred dialog wider up.
  static const double confirmMediumWidth = 560;
  static const double confirmDialogWidth = 480;

  /// A full-height sheet becomes an end-side sheet this wide (§8.2).
  static const double sideSheetMinWidth = 400;
  static const double sideSheetMaxWidth = 480;

  /// A modal part is at most this share of the window's height.
  static const double modalMaxHeight = .9;

  /// A half-height sheet; a full-height bottom sheet (the scrim
  /// still shows above it).
  static const double sheetHalfHeight = .5;
  static const double sheetFullHeight = .95;

  /// An end-side sheet's share of the window's width, within its min and
  /// max.
  static const double sideSheetShare = .4;

  /// The class for a window [width] dp wide.
  static KitWindow windowFor(double width) {
    if (width >= largeFrom) return KitWindow.large;
    if (width >= expandedFrom) return KitWindow.expanded;
    if (width >= mediumFrom) return KitWindow.medium;
    return KitWindow.compact;
  }

  /// The class of the window [context] is shown in.
  static KitWindow windowOf(BuildContext context) =>
      windowFor(MediaQuery.sizeOf(context).width);

  /// Whether the window is too short for a part to leave its compact,
  /// vertically stacked arrangement (a phone in landscape).
  static bool isShort(BuildContext context) =>
      MediaQuery.sizeOf(context).height < shortHeight;

  /// The class a modal part lays itself out for: a short window keeps the
  /// bottom sheet at most at [KitWindow.medium], so a phone in landscape
  /// never gets a floating panel it cannot fit.
  static KitWindow modalWindowOf(BuildContext context) {
    final window = windowOf(context);
    if (window.isWide && isShort(context)) return KitWindow.medium;
    return window;
  }

  /// A fine pointer is in use: a desktop build, or a mouse the framework
  /// has seen. It adds hover and tooltips, never smaller targets (§8.3).
  static bool finePointer(BuildContext context) =>
      desktopInteractions ||
      RendererBinding.instance.mouseTracker.mouseIsConnected;
}
