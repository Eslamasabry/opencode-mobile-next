import 'dart:async';
import 'dart:math' as math;
import 'dart:ui' as ui;

import 'package:flutter/foundation.dart';
import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_svg/flutter_svg.dart';
import 'package:intl/intl.dart';

import '../../l10n/app_localizations.dart';
import '../../l10n/app_localizations_en.dart';
import '../app_iconography.dart';
import '../app_theme.dart' show AppStatusTone;
import '../theme_roles.dart';
import 'kit_icon_button.dart';
import 'kit_layout.dart';
import 'kit_motion.dart';
import 'kit_surface_look.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';

part 'kit_vector_logo.dart';

/// The kit's words: the app's bound [AppLocalizations], else the locale's
/// lookup, else English, so the part never throws in a bare harness with no
/// localization delegates (R8).
AppLocalizations _l10n(BuildContext context) {
  final bound = Localizations.of<AppLocalizations>(context, AppLocalizations);
  if (bound != null) return bound;
  final locale = Localizations.maybeLocaleOf(context);
  if (locale != null && AppLocalizations.delegate.isSupported(locale)) {
    return lookupAppLocalizations(locale);
  }
  return AppLocalizationsEn();
}

/// docs/ux-system/kit-api/KitImage.md: unbounded constraints (no [KitImage]
/// width/height and no bounded incoming constraint) cap decoding at this
/// many device px on the longer side, so an unconstrained huge source never
/// decodes at its full native resolution.
const int _kitImageUnboundedCap = 4096;

/// KitImage.md "failed": from this many logical px wide the failure state
/// also shows its words (when they fit in two lines).
const double _kitImageWordsMinWidth = 120;

/// Where a [KitImage] or [KitAvatar]'s pixels come from. There is never a
/// raw URL string: a network image arrives as an [ImageProvider] built by a
/// service that authored its URL (the favicon service) or that fetches
/// through the gateway (attachments) — AGENTS.md's link rules, SEC-1.
sealed class KitImageSource {
  const factory KitImageSource.memory(Uint8List bytes) = _MemorySource;
  const factory KitImageSource.asset(String name) = _AssetSource;
  const factory KitImageSource.provider(ImageProvider provider) =
      _ProviderSource;

  /// A monochrome vector logo (KitAvatar only): [id] names it, [load]
  /// yields its SVG text or null for "none". Drawn tinted `text1`.
  const factory KitImageSource.vectorLogo({
    required String id,
    required KitVectorLogoLoad load,
  }) = _VectorLogoSource;
}

class _VectorLogoSource implements KitImageSource {
  const _VectorLogoSource({required this.id, required this.load});
  final String id;
  final KitVectorLogoLoad load;

  @override
  bool operator ==(Object other) =>
      other is _VectorLogoSource && other.id == id;

  @override
  int get hashCode => Object.hash(_VectorLogoSource, id);
}

class _MemorySource implements KitImageSource {
  const _MemorySource(this.bytes);
  final Uint8List bytes;
}

class _AssetSource implements KitImageSource {
  const _AssetSource(this.name);
  final String name;
}

class _ProviderSource implements KitImageSource {
  const _ProviderSource(this.provider);
  final ImageProvider provider;
}

ImageProvider _providerOf(KitImageSource source) => switch (source) {
  _MemorySource(:final bytes) => MemoryImage(bytes),
  _AssetSource(:final name) => AssetImage(name),
  _ProviderSource(:final provider) => provider,
  // KitAvatar draws vector logos itself; anywhere else it is a failure.
  _VectorLogoSource() => MemoryImage(Uint8List(0)),
};

/// The decode target for a source drawn with [fit] in a box of [boxWidth]
/// by [boxHeight] logical px (either may be null when that axis is
/// unbounded), at [dpr] device pixels per logical px (KitImage.md "Decode
/// size").
///
/// With both axes bounded the binding axis is chosen from the source's own
/// aspect ratio at decode time: `contain` decodes to fit inside the box
/// ([ResizeImagePolicy.fit]), `cover` decodes so the shorter side still
/// covers the box ([_KitCoverResizeImage]). Neither ever upscales.
ImageProvider _decodeProvider(
  ImageProvider base,
  double? boxWidth,
  double? boxHeight,
  double dpr,
  KitImageFit fit,
) {
  int px(double logical) => math.max(1, (logical * dpr).round());
  if (boxWidth != null && boxHeight != null) {
    final width = px(boxWidth);
    final height = px(boxHeight);
    return fit == KitImageFit.cover
        ? _KitCoverResizeImage(base, width: width, height: height)
        : ResizeImage(
            base,
            width: width,
            height: height,
            policy: ResizeImagePolicy.fit,
          );
  }
  if (boxWidth != null) {
    return ResizeImage.resizeIfNeeded(px(boxWidth), null, base);
  }
  if (boxHeight != null) {
    return ResizeImage.resizeIfNeeded(null, px(boxHeight), base);
  }
  return ResizeImage(
    base,
    width: _kitImageUnboundedCap,
    height: _kitImageUnboundedCap,
    policy: ResizeImagePolicy.fit,
  );
}

@immutable
class _KitCoverKey {
  const _KitCoverKey(this.base, this.width, this.height);
  final Object base;
  final int width;
  final int height;

  @override
  bool operator ==(Object other) =>
      other is _KitCoverKey &&
      other.base == base &&
      other.width == width &&
      other.height == height;

  @override
  int get hashCode => Object.hash(base, width, height);
}

/// Decodes [base] at the smallest size whose shorter side still covers a
/// [width] × [height] device-px box (BoxFit.cover), keeping the source's
/// aspect ratio and never upscaling. [ResizeImagePolicy] has no cover
/// policy, so this is ResizeImage's own decode hook with a cover target.
class _KitCoverResizeImage extends ImageProvider<_KitCoverKey> {
  const _KitCoverResizeImage(
    this.base, {
    required this.width,
    required this.height,
  });

  final ImageProvider base;
  final int width;
  final int height;

  @override
  Future<_KitCoverKey> obtainKey(ImageConfiguration configuration) {
    // Keeps a synchronously resolved base key synchronous, so a cached
    // image still reports wasSynchronouslyLoaded (no cross-fade).
    Completer<_KitCoverKey>? completer;
    SynchronousFuture<_KitCoverKey>? result;
    base.obtainKey(configuration).then((Object key) {
      final cover = _KitCoverKey(key, width, height);
      if (completer == null) {
        result = SynchronousFuture<_KitCoverKey>(cover);
      } else {
        completer.complete(cover);
      }
    });
    if (result != null) return result!;
    completer = Completer<_KitCoverKey>();
    return completer.future;
  }

  @override
  ImageStreamCompleter loadImage(
    _KitCoverKey key,
    ImageDecoderCallback decode,
  ) {
    Future<ui.Codec> decodeCover(
      ui.ImmutableBuffer buffer, {
      ui.TargetImageSizeCallback? getTargetSize,
    }) => decode(
      buffer,
      getTargetSize: (intrinsicWidth, intrinsicHeight) {
        if (intrinsicWidth <= 0 || intrinsicHeight <= 0) {
          return ui.TargetImageSize(
            width: intrinsicWidth,
            height: intrinsicHeight,
          );
        }
        final scale = math.min(
          1.0,
          math.max(width / intrinsicWidth, height / intrinsicHeight),
        );
        return ui.TargetImageSize(
          width: math.max(1, (intrinsicWidth * scale).round()),
          height: math.max(1, (intrinsicHeight * scale).round()),
        );
      },
    );
    return base.loadImage(key.base, decodeCover);
  }

  @override
  bool operator ==(Object other) =>
      other is _KitCoverResizeImage &&
      other.base == base &&
      other.width == width &&
      other.height == height;

  @override
  int get hashCode => Object.hash(base, width, height);
}

/// The loaded cross-fade shared by [KitImage] and [KitAvatar]: the first
/// frame fades in on [KitMotion.quick], unless it loaded synchronously or
/// motion is reduced (MOT-2: no fade-scale).
Widget _crossFade(
  BuildContext context,
  Widget child,
  bool wasSynchronouslyLoaded,
) {
  if (wasSynchronouslyLoaded || KitMotion.reduced(context)) return child;
  return TweenAnimationBuilder<double>(
    tween: Tween(begin: 0, end: 1),
    duration: KitMotion.quick,
    curve: KitMotion.enter,
    child: child,
    builder: (context, opacity, child) =>
        Opacity(opacity: opacity, child: child),
  );
}

/// How a [KitImage] fills its box.
enum KitImageFit { contain, cover }

/// Draws a raster image sharply (docs/ux-system/kit-api/KitImage.md):
/// decoded at the device pixel ratio for its laid-out size, filtered at
/// [FilterQuality.high], clipped to a token [KitShape], with honest loading
/// and failure states.
///
/// States: loading, error.
class KitImage extends StatelessWidget {
  /// [semanticsLabel] is required and may be null, so every call site
  /// decides between a named image and a decorative one.
  const KitImage({
    super.key,
    required this.source,
    required this.semanticsLabel,
    this.fit = KitImageFit.contain,
    this.shape = KitShape.square,
    this.width,
    this.height,
    this.fallback,
    this.imageKey,
  });

  final KitImageSource source;
  final String? semanticsLabel;
  final KitImageFit fit;

  /// Clips the image and its states; resolved through [KitTokens.shapeOf].
  final KitShape shape;

  /// The layout size; null takes the incoming constraints.
  final double? width;
  final double? height;

  /// Shown instead of the failure state (for example, an avatar's
  /// initials); when null the failure state draws its own notice.
  final Widget? fallback;
  final Key? imageKey;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final shapeBorder = tokens.shapeOf(shape);
    // The frame behind the image (seen while it loads, and through a clear
    // one) is a `content` surface in the style; the image is clipped to it.
    final content = SizedBox(
      width: width,
      height: height,
      child: kitSurfaceBox(
        style: KitSurfaceLook.styleOf(context),
        decoration: KitSurfaceLook.decorate(
          context,
          solid: ShapeDecoration(
            color: tokens.roles.surface2,
            shape: shapeBorder,
          ),
          level: KitSurfaceLevel.surface2,
        ),
        child: ClipPath(
          clipper: ShapeBorderClipper(shape: shapeBorder),
          child: LayoutBuilder(
            builder: (context, constraints) {
              final dpr = MediaQuery.devicePixelRatioOf(context);
              final boxWidth = constraints.maxWidth.isFinite
                  ? constraints.maxWidth
                  : null;
              final boxHeight = constraints.maxHeight.isFinite
                  ? constraints.maxHeight
                  : null;
              final provider = _decodeProvider(
                _providerOf(source),
                boxWidth,
                boxHeight,
                dpr,
                fit,
              );
              return Image(
                key: imageKey,
                image: provider,
                fit: fit == KitImageFit.cover ? BoxFit.cover : BoxFit.contain,
                gaplessPlayback: true,
                excludeFromSemantics: true,
                filterQuality: FilterQuality.high,
                frameBuilder: (context, child, frame, wasSynchronouslyLoaded) {
                  // Flutter's own Image state keeps the previous frame's
                  // pixels visible (gaplessPlayback) while a new source
                  // loads, so `child` already shows the old frame here —
                  // this only adds the loaded-frame cross-fade.
                  if (frame == null) return child;
                  return _crossFade(context, child, wasSynchronouslyLoaded);
                },
                errorBuilder: (context, error, stack) =>
                    fallback ?? _KitImageFailure(tokens: tokens),
              );
            },
          ),
        ),
      ),
    );
    final label = semanticsLabel;
    if (label == null) return ExcludeSemantics(child: content);
    // One image node. The loaded image adds nothing to it; the failure
    // state merges its words into it ("A photo, Can't show this image"),
    // so a screen reader can tell a broken image from a loaded one
    // (A11Y: "The failure state's words are read").
    return Semantics(
      container: true,
      image: true,
      label: label,
      child: content,
    );
  }
}

class _KitImageFailure extends StatelessWidget {
  const _KitImageFailure({required this.tokens});

  final KitTokens tokens;

  /// Whether [words] fit in two lines of the secondary role across
  /// [maxWidth] at the ambient text scale (KitImage.md "200 % text": the
  /// words wrap to two lines, then the glyph alone shows).
  static bool _fitsTwoLines(
    BuildContext context,
    String words,
    double maxWidth,
  ) {
    if (maxWidth <= 0) return false;
    final painter = TextPainter(
      text: TextSpan(
        text: words,
        style: KitText.styleOf(context, KitTextRole.secondary),
      ),
      textAlign: TextAlign.center,
      textDirection: Directionality.of(context),
      textScaler: MediaQuery.textScalerOf(context),
      locale: Localizations.maybeLocaleOf(context),
      maxLines: 2,
    )..layout(maxWidth: maxWidth);
    final fits = !painter.didExceedMaxLines;
    painter.dispose();
    return fits;
  }

  @override
  Widget build(BuildContext context) {
    final words = _l10n(context).kitImageUnavailable;
    return Semantics(
      label: words,
      child: ExcludeSemantics(
        child: ColoredBox(
          color: tokens.roles.surface2,
          child: LayoutBuilder(
            builder: (context, constraints) {
              final inset = tokens.space2;
              final showWords =
                  constraints.maxWidth >= _kitImageWordsMinWidth &&
                  _fitsTwoLines(
                    context,
                    words,
                    constraints.maxWidth - 2 * inset,
                  );
              return Center(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(
                      AppIconography.imageBroken,
                      size: tokens.smallIconSize,
                      color: tokens.roles.text2,
                    ),
                    if (showWords) ...[
                      SizedBox(height: tokens.space1),
                      Padding(
                        padding: EdgeInsetsDirectional.symmetric(
                          horizontal: inset,
                        ),
                        child: KitText(
                          words,
                          role: KitTextRole.secondary,
                          textAlign: TextAlign.center,
                          maxLines: 2,
                        ),
                      ),
                    ],
                  ],
                ),
              );
            },
          ),
        ),
      ),
    );
  }
}

/// [KitAvatar]'s fixed sizes (KitImage.md): [tile] is
/// [KitTokens.iconTileSize] (28), [mark] is [KitTokens.markSize] (44).
enum KitAvatarSize { tile, mark }

/// The one identity mark (docs/ux-system/kit-api/KitImage.md): an image or
/// icon when given, otherwise the initials of [name] on `surface3`, always
/// named, never coloured by identity (STATE-9).
///
/// States: loading, error.
///
/// Both fall back to the initials, so the slot is never empty. Error also
/// shows the failure glyph ([KitTokens.glyphFor], in its neutral tone) on a
/// `ground` ring on the bottom-end corner, clear of the initials, and says
/// "Can't show this image" after the name: a shape and words, never a red
/// dot (LOOK-5, STATE-9).
class KitAvatar extends StatefulWidget {
  const KitAvatar({
    super.key,
    required this.name,
    this.image,
    this.icon,
    this.size = KitAvatarSize.tile,
    this.decorative = false,
    this.optionalImage = false,
  });

  /// Always the semantic label, whatever is drawn.
  final String name;

  /// Falls back to the initials while loading and on failure.
  final KitImageSource? image;

  /// An [AppIconography] glyph instead of initials (a server, the phone).
  final IconData? icon;
  final KitAvatarSize size;

  /// True when the row beside it already says [name].
  final bool decorative;

  /// True when [image] is a nice-to-have (a provider's logo from a catalog
  /// that lacks many): a failure just keeps the initials, with no error
  /// badge or words, since nothing is missing for the person.
  final bool optionalImage;

  @override
  State<KitAvatar> createState() => _KitAvatarState();
}

class _KitAvatarState extends State<KitAvatar> {
  /// The image failed to load: the initials stay, and the error badge and
  /// words join them.
  bool _failed = false;

  @override
  void didUpdateWidget(KitAvatar oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.image != widget.image) _failed = false;
  }

  /// Called from the image's error builder, which runs during build: the
  /// badge lands on the next frame.
  void _markFailed() {
    if (_failed) return;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted && !_failed) setState(() => _failed = true);
    });
  }

  @override
  Widget build(BuildContext context) {
    final KitAvatar(:name, :icon, :size, :decorative) = widget;
    final tokens = KitTokens.of(context);
    final diameter = size == KitAvatarSize.tile
        ? tokens.iconTileSize
        : tokens.markSize;
    final role = size == KitAvatarSize.tile
        ? KitTextRole.label
        : KitTextRole.headline;
    final source = widget.image;
    final failed = _failed && source != null && !widget.optionalImage;
    final identityMark = icon != null
        ? Icon(icon, size: tokens.smallIconSize, color: tokens.roles.text1)
        : MediaQuery.withClampedTextScaling(
            maxScaleFactor: KitTokens.monogramMaxTextScale,
            child: KitText(
              _avatarInitials(name),
              role: role,
              tone: KitTextTone.primary,
              maxLines: 1,
              softWrap: false,
              overflow: TextOverflow.clip,
            ),
          );
    // A failed image's badge sits on the square's bottom-end corner; the
    // initials step a ring's width towards the top start, so the badge
    // never covers the second initial (R8).
    const badgeStep = 2 * KitTokens.avatarBadgeRing;
    final identity = failed
        ? Padding(
            padding: const EdgeInsetsDirectional.only(
              end: badgeStep,
              bottom: badgeStep,
            ),
            child: identityMark,
          )
        : identityMark;
    final content = ClipOval(
      child: ColoredBox(
        color: tokens.roles.surface3,
        child: SizedBox(
          width: diameter,
          height: diameter,
          child: Stack(
            alignment: Alignment.center,
            children: [
              identity,
              if (source is _VectorLogoSource)
                PositionedDirectional(
                  start: 0,
                  end: 0,
                  top: 0,
                  bottom: 0,
                  child: _KitVectorLogo(
                    key: ValueKey('kit-vector-logo-${source.id}'),
                    id: source.id,
                    load: source.load,
                  ),
                )
              else if (source != null)
                PositionedDirectional(
                  start: 0,
                  end: 0,
                  top: 0,
                  bottom: 0,
                  child: Image(
                    // The circle is always filled (BoxFit.cover), so the
                    // decode keeps the shorter side at the diameter: a
                    // wide favicon is never drawn from a thinner decode.
                    image: _decodeProvider(
                      _providerOf(source),
                      diameter,
                      diameter,
                      MediaQuery.devicePixelRatioOf(context),
                      KitImageFit.cover,
                    ),
                    fit: BoxFit.cover,
                    gaplessPlayback: true,
                    excludeFromSemantics: true,
                    filterQuality: FilterQuality.high,
                    frameBuilder:
                        (context, child, frame, wasSynchronouslyLoaded) {
                          if (frame == null) {
                            // Loading: transparent, so the identity mark
                            // beneath keeps the slot from ever reading
                            // empty.
                            return const SizedBox.shrink();
                          }
                          return _crossFade(
                            context,
                            child,
                            wasSynchronouslyLoaded,
                          );
                        },
                    errorBuilder: (context, error, stack) {
                      _markFailed();
                      return const SizedBox.shrink();
                    },
                  ),
                ),
            ],
          ),
        ),
      ),
    );
    // The badge sits on the bottom-end diagonal, its ring overlapping the
    // circle's edge by one ring width: far enough out that it never covers
    // the second initial (R8), close enough to read as the avatar's.
    final badgeInset = _avatarBadgeInset(diameter);
    final mark = failed
        ? SizedBox(
            width: diameter,
            height: diameter,
            child: Stack(
              clipBehavior: Clip.none,
              children: [
                content,
                PositionedDirectional(
                  end: badgeInset,
                  bottom: badgeInset,
                  child: _KitAvatarErrorBadge(roles: tokens.roles),
                ),
              ],
            ),
          )
        : content;
    if (decorative) return ExcludeSemantics(child: mark);
    return Semantics(
      image: true,
      label: name,
      value: failed ? _l10n(context).kitImageUnavailable : null,
      excludeSemantics: true,
      child: mark,
    );
  }
}

/// [KitAvatar]'s error badge: the failure glyph ([KitTokens.glyphFor]) in
/// its tone ([KitTokens.toneFor], `text1`, never `danger`: LOOK-5) on a
/// `ground` disc that rings it off the avatar.
class _KitAvatarErrorBadge extends StatelessWidget {
  const _KitAvatarErrorBadge({required this.roles});

  final ThemeRoles roles;

  @override
  Widget build(BuildContext context) => Container(
    width: KitTokens.avatarBadgeSize,
    height: KitTokens.avatarBadgeSize,
    alignment: Alignment.center,
    decoration: BoxDecoration(color: roles.ground, shape: BoxShape.circle),
    child: Icon(
      KitTokens.glyphFor(AppStatusTone.failure),
      size: KitTokens.avatarBadgeSize - 2 * KitTokens.avatarBadgeRing,
      color: KitTokens.toneColor(roles, AppStatusTone.failure),
    ),
  );
}

/// [KitAvatar]'s error-badge offset from the bottom-end corner (negative:
/// outside the box), in whole logical pixels: the badge's centre on the
/// circle's bottom-end diagonal, [KitTokens.avatarBadgeRing] inside the
/// point where the badge would only touch the circle.
double _avatarBadgeInset(double diameter) {
  const badge = KitTokens.avatarBadgeSize;
  final radius = diameter / 2;
  final centre =
      radius + (radius + badge / 2 - KitTokens.avatarBadgeRing) * math.sqrt1_2;
  return (diameter - centre - badge / 2).roundToDouble();
}

/// The first grapheme of [name]'s first two words, folded to upper case —
/// a no-op for a script without case (Arabic, CJK), so this one rule gives
/// both KitImage.md behaviours: "upper-cased where the script has case"
/// and "Arabic initials use the first grapheme of each word, without
/// case".
String _avatarInitials(String name) {
  final words = name
      .trim()
      .split(RegExp(r'\s+'))
      .where((w) => w.isNotEmpty)
      .take(2);
  final buffer = StringBuffer();
  for (final word in words) {
    final chars = word.characters;
    if (chars.isEmpty) continue;
    buffer.write(chars.first.toUpperCase());
  }
  return buffer.toString();
}

/// What [KitZoom] does with a child smaller, or larger, than its viewport.
enum KitZoomMode {
  /// The child fits the view at 1×; zoom in to [KitZoom.maxScale]; pan only
  /// while zoomed (images, pages).
  fit,

  /// The child is larger than the view; pan freely within it, zoom out to
  /// fit (the work graph).
  canvas,
}

/// Drives a [KitZoom] from its host (the work graph's Fit on open and its
/// keyboard zoom). [value] is the current transform, for tests.
class KitZoomController extends ChangeNotifier {
  /// Wraps a host's [transformation] controller when it has one.
  KitZoomController({TransformationController? transformation})
    : _transformation = transformation ?? TransformationController(),
      _ownsTransformation = transformation == null {
    _transformation.addListener(notifyListeners);
  }

  final TransformationController _transformation;
  final bool _ownsTransformation;
  _KitZoomState? _state;

  Matrix4 get value => _transformation.value;

  void _attach(_KitZoomState state) => _state = state;

  void _detach(_KitZoomState state) {
    if (identical(_state, state)) _state = null;
  }

  /// The start view: 1× in fit mode; the whole child fitted in canvas
  /// mode.
  void reset() {
    final state = _state;
    if (state == null) {
      _transformation.value = Matrix4.identity();
    } else {
      state.resetView();
    }
  }

  void zoomIn() => _step(true);

  void zoomOut() => _step(false);

  void _step(bool inward) {
    final state = _state;
    if (state != null) {
      state.stepZoom(inward);
      return;
    }
    final current = _transformation.value.getMaxScaleOnAxis();
    final factor = inward
        ? _KitZoomState._zoomStep
        : 1 / _KitZoomState._zoomStep;
    final target = (current * factor).clamp(KitZoom.minScale, KitZoom.maxScale);
    _transformation.value = Matrix4.identity()
      ..scaleByDouble(target, target, target, 1);
  }

  @override
  void dispose() {
    _transformation.removeListener(notifyListeners);
    if (_ownsTransformation) _transformation.dispose();
    super.dispose();
  }
}

/// The one pinch, pan and zoom viewer (docs/ux-system/kit-api/KitImage.md),
/// with visible zoom controls and keyboard zoom. Replaces
/// [InteractiveViewer] at every call site.
///
/// States: none — a viewer shows what [child] gives it.
class KitZoom extends StatefulWidget {
  const KitZoom({
    super.key,
    required this.child,
    required this.label,
    this.mode = KitZoomMode.fit,
    this.controls = true,
    this.resetKey,
    this.controller,
    this.zoomKey,
    this.resetControlKey,
  });

  final Widget child;

  /// What is being viewed: "Screenshot.png", "Work graph".
  final String label;
  final KitZoomMode mode;

  /// The visible twin of pinch: zoom out, reset ("Fit to screen" in canvas
  /// mode), zoom in (A11Y-5).
  final bool controls;

  /// A change resets to the start view (a new page, a new file).
  final Object? resetKey;
  final KitZoomController? controller;
  final Key? zoomKey;

  /// The reset / Fit control (the work graph keeps team-work-graph-fit).
  final Key? resetControlKey;

  /// Behaviour constants, not look tokens.
  static const double maxScale = 5;
  static const double minScale = 0.2;

  @override
  State<KitZoom> createState() => _KitZoomState();
}

enum _ZoomCommand { zoomIn, zoomOut, reset }

class _ZoomIntent extends Intent {
  const _ZoomIntent(this.command);
  final _ZoomCommand command;
}

class _PanIntent extends Intent {
  const _PanIntent(this.delta);

  /// Where the view moves over the content, in logical px.
  final Offset delta;
}

/// An action that is only enabled while [enabled] says so, so a key it
/// cannot use (an arrow at rest, Ctrl+0 at the start view) propagates to
/// the host's shortcuts instead of being swallowed.
class _GuardedAction<T extends Intent> extends Action<T> {
  _GuardedAction({required this.enabled, required this.onInvoke});

  final bool Function(T intent) enabled;
  final void Function(T intent) onInvoke;

  @override
  bool isEnabled(T intent) => enabled(intent);

  @override
  Object? invoke(T intent) {
    onInvoke(intent);
    return null;
  }
}

class _KitZoomState extends State<KitZoom> with SingleTickerProviderStateMixin {
  /// One step of the controls, the keys and one Ctrl+wheel notch.
  static const double _zoomStep = 1.5;
  static const double _doubleTapScale = 2;
  static const double _epsilon = 0.01;

  /// How far one arrow key moves the view, in logical px: a behaviour
  /// constant (like [KitZoom.maxScale]), not a look token.
  static const double _panStep = 32;

  final GlobalKey _viewportKey = GlobalKey(debugLabel: 'kit-zoom-viewport');
  final GlobalKey _childKey = GlobalKey(debugLabel: 'kit-zoom-child');
  final FocusNode _focusNode = FocusNode(debugLabel: 'kit-zoom');
  late final AnimationController _anim = AnimationController(
    vsync: this,
    duration: KitMotion.standard,
  );

  KitZoomController? _owned;
  KitZoomController get _controller =>
      widget.controller ?? (_owned ??= KitZoomController());
  TransformationController get _t => _controller._transformation;

  Animation<Matrix4>? _matrixAnim;

  /// The start view: identity in fit mode; the fitted child in canvas mode
  /// (set by the first post-frame fit).
  Matrix4 _start = Matrix4.identity();

  // Double-tap is read from the raw pointer events, with a window this state
  // owns and cancels: the framework's multi-tap recognizers start a 40 ms
  // countdown on every tap that nothing can cancel, so a zoom that settled at
  // once (reduced motion) still left a timer running behind it.
  Timer? _tapWindow;
  int? _tapPointer;
  Offset? _tapDown;
  Offset? _firstTap;
  final Set<int> _pointersDown = {};
  Size _viewportSize = Size.zero;

  Matrix4? _gestureStart;
  Offset _gestureFocal = Offset.zero;

  @override
  void initState() {
    super.initState();
    _controller._attach(this);
    _t.addListener(_onTransformChanged);
    _anim.addListener(_onAnimTick);
    if (widget.mode == KitZoomMode.canvas) {
      WidgetsBinding.instance.addPostFrameCallback((_) => _fitCanvas());
    }
  }

  @override
  void didUpdateWidget(KitZoom oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.controller != oldWidget.controller) {
      final old = oldWidget.controller ?? _owned;
      old?._detach(this);
      old?._transformation.removeListener(_onTransformChanged);
      if (oldWidget.controller == null) {
        _owned?.dispose();
        _owned = null;
      }
      _controller._attach(this);
      _t.addListener(_onTransformChanged);
    }
    if (widget.resetKey != oldWidget.resetKey) {
      if (widget.mode == KitZoomMode.canvas) {
        WidgetsBinding.instance.addPostFrameCallback((_) => _fitCanvas());
      } else {
        resetView();
      }
    }
  }

  @override
  void dispose() {
    _controller._detach(this);
    _t.removeListener(_onTransformChanged);
    _anim.removeListener(_onAnimTick);
    _anim.dispose();
    _endTapSeries();
    _owned?.dispose();
    _focusNode.dispose();
    super.dispose();
  }

  void _onTransformChanged() {
    if (mounted) setState(() {});
  }

  void _onAnimTick() {
    final matrix = _matrixAnim?.value;
    if (matrix != null) _t.value = matrix;
  }

  // --- geometry -------------------------------------------------------------
  //
  // Every matrix KitZoom makes is a uniform scale s plus a translation t
  // (scene point x is drawn at s·x + t), in the viewport's own physical
  // coordinates: an image is never mirrored under RTL.

  static double _scaleOf(Matrix4 m) => m.storage[0];
  static Offset _translationOf(Matrix4 m) =>
      Offset(m.storage[12], m.storage[13]);
  static Matrix4 _matrix(double scale, Offset translation) => Matrix4.identity()
    ..translateByDouble(translation.dx, translation.dy, 0, 1)
    ..scaleByDouble(scale, scale, scale, 1);

  Size get _viewport =>
      (_viewportKey.currentContext?.findRenderObject() as RenderBox?)?.size ??
      _viewportSize;

  Size get _childSize {
    final box = _childKey.currentContext?.findRenderObject() as RenderBox?;
    return box != null && box.hasSize ? box.size : _viewport;
  }

  double get _floor =>
      widget.mode == KitZoomMode.canvas ? KitZoom.minScale : 1.0;

  /// Keeps the child over the view: a child larger than the view cannot
  /// leave a gap at an edge, and a smaller one (canvas mode, zoomed out)
  /// stays wholly inside, free to sit anywhere up to centred and beyond.
  Matrix4 _clamped(Matrix4 m) {
    final scale = _scaleOf(m).clamp(_floor, KitZoom.maxScale);
    final t = _translationOf(m);
    final view = _viewport;
    final child = _childSize;
    double axis(double value, double viewExtent, double childExtent) {
      final slack = viewExtent - childExtent * scale;
      return value.clamp(math.min(0.0, slack), math.max(0.0, slack));
    }

    return _matrix(
      scale,
      Offset(
        axis(t.dx, view.width, child.width),
        axis(t.dy, view.height, child.height),
      ),
    );
  }

  /// [current] zoomed to [targetScale] (clamped to this mode's range) with
  /// the scene point under viewport point [p] kept under it.
  Matrix4 _zoomedTo(Matrix4 current, Offset p, double targetScale) {
    final scale = _scaleOf(current);
    final t = _translationOf(current);
    final scene = (p - t) / scale;
    final target = targetScale.clamp(_floor, KitZoom.maxScale);
    return _clamped(_matrix(target, p - scene * target));
  }

  static bool _same(Matrix4 a, Matrix4 b) =>
      (_scaleOf(a) - _scaleOf(b)).abs() < _epsilon &&
      (_translationOf(a) - _translationOf(b)).distance < 0.5;

  double get _scale => _scaleOf(_t.value);
  bool get _atRest => _same(_t.value, _start);
  bool get _atMax => _scale >= KitZoom.maxScale - _epsilon;
  bool get _canZoomOut => _scale > _floor + _epsilon;

  /// The view can move in the direction of [delta].
  bool _canPan(Offset delta) =>
      !_same(_clamped(_panned(_t.value, delta)), _t.value);

  bool get _pannable =>
      _canPan(const Offset(1, 0)) ||
      _canPan(const Offset(-1, 0)) ||
      _canPan(const Offset(0, 1)) ||
      _canPan(const Offset(0, -1));

  /// The view moves by [delta] over the content, so the content moves the
  /// other way.
  static Matrix4 _panned(Matrix4 m, Offset delta) =>
      _matrix(_scaleOf(m), _translationOf(m) - delta);

  void _fitCanvas() {
    if (!mounted) return;
    final viewportBox =
        _viewportKey.currentContext?.findRenderObject() as RenderBox?;
    final childBox = _childKey.currentContext?.findRenderObject() as RenderBox?;
    if (viewportBox == null ||
        childBox == null ||
        !viewportBox.hasSize ||
        !childBox.hasSize) {
      return;
    }
    final matrix = _canvasFitMatrix(viewportBox.size, childBox.size);
    if (matrix != null) {
      _anim.stop();
      setState(() {
        _start = matrix;
        _t.value = matrix;
      });
    }
  }

  static Matrix4? _canvasFitMatrix(Size viewport, Size child) {
    if (child.width <= 0 || child.height <= 0) return null;
    final scale = math
        .min(viewport.width / child.width, viewport.height / child.height)
        .clamp(KitZoom.minScale, 1.0);
    return _matrix(
      scale,
      Offset(
        (viewport.width - child.width * scale) / 2,
        (viewport.height - child.height * scale) / 2,
      ),
    );
  }

  // --- commands -------------------------------------------------------------

  void _animateTo(Matrix4 target) {
    _anim.stop();
    if (KitMotion.reduced(context)) {
      _t.value = target;
      return;
    }
    _matrixAnim = Matrix4Tween(
      begin: _t.value,
      end: target,
    ).animate(CurvedAnimation(parent: _anim, curve: KitMotion.enter));
    _anim.forward(from: 0);
  }

  void resetView() {
    if (widget.mode == KitZoomMode.canvas) {
      final fitted = _canvasFitMatrix(_viewport, _childSize);
      if (fitted != null) _start = fitted;
    }
    _animateTo(_start.clone());
  }

  Offset get _viewCentre {
    final view = _viewport;
    return view.isEmpty ? Offset.zero : view.center(Offset.zero);
  }

  void stepZoom(bool inward) {
    final factor = inward ? _zoomStep : 1 / _zoomStep;
    _animateTo(_zoomedTo(_t.value, _viewCentre, _scale * factor));
  }

  void _panBy(Offset delta) {
    _anim.stop();
    _t.value = _clamped(_panned(_t.value, delta));
  }

  // --- input ----------------------------------------------------------------

  void _handleScaleStart(ScaleStartDetails details) {
    _anim.stop();
    _gestureStart = _t.value.clone();
    _gestureFocal = details.localFocalPoint;
  }

  void _handleScaleUpdate(ScaleUpdateDetails details) {
    final start = _gestureStart;
    if (start == null) return;
    // Pinch follows the fingers: the scene point under the gesture's first
    // focal point stays under the current one.
    final startScale = _scaleOf(start);
    final scene = (_gestureFocal - _translationOf(start)) / startScale;
    final target = (startScale * details.scale).clamp(_floor, KitZoom.maxScale);
    _t.value = _clamped(
      _matrix(target, details.localFocalPoint - scene * target),
    );
  }

  void _handleScaleEnd(ScaleEndDetails details) => _gestureStart = null;

  /// One primary-button pointer starts or continues a tap series; a second
  /// finger (a pinch) or another button ends it.
  void _handlePointerDown(PointerDownEvent event) {
    _pointersDown.add(event.pointer);
    final first = _firstTap;
    if (_pointersDown.length > 1 ||
        event.buttons != kPrimaryButton ||
        (first != null &&
            (event.localPosition - first).distance > kDoubleTapSlop)) {
      _endTapSeries();
      if (_pointersDown.length > 1 || event.buttons != kPrimaryButton) return;
    }
    _tapPointer = event.pointer;
    _tapDown = event.localPosition;
  }

  void _handlePointerMove(PointerMoveEvent event) {
    final down = _tapDown;
    if (event.pointer != _tapPointer || down == null) return;
    if ((event.localPosition - down).distance > kDoubleTapTouchSlop) {
      _endTapSeries();
    }
  }

  void _handlePointerUp(PointerUpEvent event) {
    _pointersDown.remove(event.pointer);
    final down = _tapDown;
    if (event.pointer != _tapPointer || down == null) return;
    _tapPointer = null;
    _tapDown = null;
    if (_firstTap != null) {
      // The second tap of the series, at its own down position.
      _endTapSeries();
      _handleDoubleTap(down);
      return;
    }
    _firstTap = down;
    _tapWindow = Timer(kDoubleTapTimeout, _endTapSeries);
  }

  void _handlePointerCancel(PointerCancelEvent event) {
    _pointersDown.remove(event.pointer);
    if (event.pointer == _tapPointer) _endTapSeries();
  }

  void _endTapSeries() {
    _tapWindow?.cancel();
    _tapWindow = null;
    _firstTap = null;
    _tapPointer = null;
    _tapDown = null;
  }

  void _handleDoubleTap(Offset p) {
    if (_atRest) {
      // An absolute 2×, whatever the start scale (a fitted canvas is
      // often well under 1×).
      _animateTo(_zoomedTo(_t.value, p, _doubleTapScale));
    } else {
      resetView();
    }
  }

  /// Ctrl (or Cmd) + wheel zooms one step per notch at the pointer. The
  /// pointer-signal resolver lets exactly one handler take the event, and a
  /// plain wheel is left alone so the host can scroll.
  void _handlePointerSignal(PointerSignalEvent event) {
    if (event is PointerScrollEvent) {
      final keys = HardwareKeyboard.instance;
      if (!keys.isControlPressed && !keys.isMetaPressed) return;
      if (event.scrollDelta.dy == 0) return;
      final at = event.localPosition;
      final factor = event.scrollDelta.dy < 0 ? _zoomStep : 1 / _zoomStep;
      GestureBinding.instance.pointerSignalResolver.register(event, (_) {
        _anim.stop();
        _t.value = _zoomedTo(_t.value, at, _scale * factor);
      });
    } else if (event is PointerScaleEvent) {
      final at = event.localPosition;
      final scale = event.scale;
      GestureBinding.instance.pointerSignalResolver.register(event, (_) {
        _anim.stop();
        _t.value = _zoomedTo(_t.value, at, _scale * scale);
      });
    }
  }

  static const _ctrlKeys = <LogicalKeyboardKey>[
    LogicalKeyboardKey.control,
    LogicalKeyboardKey.meta,
  ];

  Map<ShortcutActivator, Intent> get _shortcuts => {
    for (final modifier in _ctrlKeys) ...{
      LogicalKeySet(modifier, LogicalKeyboardKey.equal): const _ZoomIntent(
        _ZoomCommand.zoomIn,
      ),
      LogicalKeySet(modifier, LogicalKeyboardKey.minus): const _ZoomIntent(
        _ZoomCommand.zoomOut,
      ),
      LogicalKeySet(modifier, LogicalKeyboardKey.digit0): const _ZoomIntent(
        _ZoomCommand.reset,
      ),
    },
    const SingleActivator(LogicalKeyboardKey.arrowUp): const _PanIntent(
      Offset(0, -_panStep),
    ),
    const SingleActivator(LogicalKeyboardKey.arrowDown): const _PanIntent(
      Offset(0, _panStep),
    ),
    const SingleActivator(LogicalKeyboardKey.arrowLeft): const _PanIntent(
      Offset(-_panStep, 0),
    ),
    const SingleActivator(LogicalKeyboardKey.arrowRight): const _PanIntent(
      Offset(_panStep, 0),
    ),
  };

  late final Map<Type, Action<Intent>> _actions = {
    _ZoomIntent: _GuardedAction<_ZoomIntent>(
      enabled: (intent) => switch (intent.command) {
        _ZoomCommand.zoomIn => !_atMax,
        _ZoomCommand.zoomOut => _canZoomOut,
        _ZoomCommand.reset => !_atRest,
      },
      onInvoke: (intent) => switch (intent.command) {
        _ZoomCommand.zoomIn => stepZoom(true),
        _ZoomCommand.zoomOut => stepZoom(false),
        _ZoomCommand.reset => resetView(),
      },
    ),
    _PanIntent: _GuardedAction<_PanIntent>(
      enabled: (intent) => _canPan(intent.delta),
      onInvoke: (intent) => _panBy(intent.delta),
    ),
  };

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final finePointer = KitLayout.finePointer(context);
    Widget viewer = ClipRect(
      child: LayoutBuilder(
        key: _viewportKey,
        builder: (context, constraints) {
          _viewportSize = constraints.biggest;
          final child = KeyedSubtree(key: _childKey, child: widget.child);
          return Transform(
            key: widget.zoomKey,
            transform: _t.value,
            child: widget.mode == KitZoomMode.canvas
                // The canvas child keeps its own (larger) size. The
                // transform's origin is the viewport's physical top-left,
                // so this alignment is physical on purpose.
                ? OverflowBox(
                    alignment: Alignment.topLeft,
                    minWidth: 0,
                    minHeight: 0,
                    maxWidth: double.infinity,
                    maxHeight: double.infinity,
                    child: child,
                  )
                : child,
          );
        },
      ),
    );
    viewer = GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTap: _focusNode.requestFocus,
      onScaleStart: _handleScaleStart,
      onScaleUpdate: _handleScaleUpdate,
      onScaleEnd: _handleScaleEnd,
      child: viewer,
    );
    viewer = Listener(
      onPointerDown: _handlePointerDown,
      onPointerMove: _handlePointerMove,
      onPointerUp: _handlePointerUp,
      onPointerCancel: _handlePointerCancel,
      onPointerSignal: _handlePointerSignal,
      child: viewer,
    );
    if (finePointer) {
      viewer = MouseRegion(
        cursor: _pannable ? SystemMouseCursors.grab : MouseCursor.defer,
        child: viewer,
      );
    }
    viewer = Focus(focusNode: _focusNode, child: viewer);
    if (finePointer) {
      viewer = Shortcuts(
        shortcuts: _shortcuts,
        child: Actions(actions: _actions, child: viewer),
      );
    }
    final l10n = _l10n(context);
    final localeName = Localizations.localeOf(context).toString();
    final percent = NumberFormat.decimalPattern(
      localeName,
    ).format((_scale * 100).round());
    viewer = Semantics(
      label: widget.label,
      value: l10n.kitZoomLevel(percent),
      child: Semantics(
        customSemanticsActions: {
          if (!_atMax)
            CustomSemanticsAction(label: l10n.kitZoomIn): () => stepZoom(true),
          if (_canZoomOut)
            CustomSemanticsAction(label: l10n.kitZoomOut): () =>
                stepZoom(false),
          if (!_atRest)
            CustomSemanticsAction(label: l10n.kitZoomReset): resetView,
        },
        child: viewer,
      ),
    );
    // A fine pointer adds each control's shortcut to its tooltip (Adaptive:
    // "Zoom in · Ctrl+="); a disabled control keeps its reason alone.
    String withKeys(String action, String key) =>
        finePointer ? l10n.kitZoomShortcut(action, key) : action;
    final controlsLook = KitSurfaceLook.decorate(
      context,
      solid: ShapeDecoration(
        color: tokens.roles.surface2,
        shape: const StadiumBorder(),
      ),
      role: KitSurfaceRole.floating,
      level: KitSurfaceLevel.surface2,
      borderRadius: KitSurfaceLook.stadium,
    );
    return Stack(
      children: [
        PositionedDirectional(
          start: 0,
          end: 0,
          top: 0,
          bottom: 0,
          child: viewer,
        ),
        if (widget.controls)
          PositionedDirectional(
            end: tokens.space4,
            bottom: tokens.space4,
            // The zoom controls float over the image: the style's floating
            // piece.
            child: kitSurfaceBox(
              style: KitSurfaceLook.styleOf(context),
              decoration: controlsLook,
              child: Padding(
                padding: EdgeInsetsDirectional.symmetric(
                  horizontal: tokens.space2,
                  vertical: tokens.space1,
                ),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    KitIconButton(
                      icon: AppIconography.collapse,
                      tooltip: _canZoomOut
                          ? withKeys(l10n.kitZoomOut, '−')
                          : l10n.kitZoomOut,
                      onPressed: _canZoomOut ? () => stepZoom(false) : null,
                    ),
                    SizedBox(width: tokens.space2),
                    KitIconButton(
                      key: widget.resetControlKey,
                      icon: AppIconography.retry,
                      tooltip: _atRest
                          ? l10n.kitZoomAtStart
                          : withKeys(
                              widget.mode == KitZoomMode.canvas
                                  ? l10n.kitZoomFit
                                  : l10n.kitZoomReset,
                              '0',
                            ),
                      onPressed: _atRest ? null : resetView,
                    ),
                    SizedBox(width: tokens.space2),
                    KitIconButton(
                      icon: AppIconography.expand,
                      tooltip: _atMax
                          ? l10n.kitZoomAtMax
                          : withKeys(l10n.kitZoomIn, '='),
                      onPressed: _atMax ? null : () => stepZoom(true),
                    ),
                  ],
                ),
              ),
            ),
          ),
      ],
    );
  }
}
