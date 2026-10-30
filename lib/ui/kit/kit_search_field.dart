import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter/scheduler.dart';
import 'package:flutter/semantics.dart';
import 'package:flutter/services.dart';

import '../../l10n/app_localizations.dart';
import '../../l10n/app_localizations_en.dart';
import '../app_iconography.dart';
import 'kit_bidi.dart';
import 'kit_buttons.dart';
import 'kit_chip.dart';
import 'kit_field.dart' show kitFieldFrame, kitFieldRing;
import 'kit_icon_button.dart';
import 'kit_layout.dart';
import 'kit_max_width.dart';
import 'kit_menu.dart';
import 'kit_motion.dart';
import 'kit_state_view.dart';
import 'kit_surface_look.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';
import 'motion/kit_reveal.dart';

/// The one search field for lists, sheets and viewers
/// (docs/ux-system/kit-api/KitSearchField.md, kit-v2 §1.5, §2.12, KIT-20).
/// It draws the field, its count line and its active filter; finding things
/// is the host's job.
///
/// States: `empty` (no query: no clear button, no count, no announcement),
/// `typing` (a query, with the clear button), `results` ([resultCount]
/// known), `partial` ([partial]: "12 loaded · searching the server…"),
/// `no-match` (the host shows [KitSearchNoMatch] in place of the list),
/// `filtered` ([activeFilter] as a removable chip in words), `disabled`
/// ([disabledReason] in a line under the field).
///
/// - Loading: none of its own; remote results use the screen's one loading
///   bar, and [partial] says the count is not final (STATE-11).
/// - Error: the host's notice on the list; the field keeps the query.
///
/// [onChanged] runs once typing settles ([KitMotion.typingSettle]) and at
/// once on clear. The count is announced once after settling, never on
/// each keystroke (A11Y-3). Esc and the back gesture clear the query first;
/// with no query they are left to the screen. Enter settles the query at
/// once (a pending [onChanged] runs now, once) and calls [onSubmitted];
/// Arrow Down moves focus to the first thing after the field.
class KitSearchField extends StatefulWidget {
  const KitSearchField({
    super.key,
    required this.label,
    required this.onChanged,
    this.controller,
    this.onSubmitted,
    this.resultCount,
    this.partial = false,
    this.filters = const [],
    this.activeFilter,
    this.onClearFilter,
    this.enabled = true,
    this.disabledReason,
    this.autofocus = false,
    this.focusNode,
    this.fieldKey,
    this.clearKey,
    this.filterKey,
    this.prominent = false,
  }) : assert(
         activeFilter == null || onClearFilter != null,
         'an active filter needs onClearFilter (honest filters)',
       ),
       assert(enabled || disabledReason != null, 'STATE-8: say why');

  /// "Search settings": the hint and the semantic label.
  final String label;

  /// The settled query; at once with '' on clear.
  final ValueChanged<String> onChanged;
  final TextEditingController? controller;

  /// Enter / IME search: for example open the first result.
  final ValueChanged<String>? onSubmitted;

  /// How many results the query found; announced once typing settles.
  final int? resultCount;

  /// Results are still coming: the count says so.
  final bool partial;

  /// A labelled "Filter" menu at the end of the field.
  final List<KitMenuItem> filters;

  /// The active filter's name, shown as a removable chip in words.
  final String? activeFilter;
  final VoidCallback? onClearFilter;
  final bool enabled;
  final String? disabledReason;
  final bool autofocus;

  /// For the shell's Ctrl/Cmd+F shortcut.
  final FocusNode? focusNode;
  final Key? fieldKey;
  final Key? clearKey;
  final Key? filterKey;

  /// The page's own search, at the top of a short index (the Settings
  /// hub): a pill on `surface1` with no hairline until it has focus, so it
  /// reads as the way in rather than a form field. Lists and sheets keep
  /// the plain field.
  final bool prominent;

  /// A search earns its place when the list can pass about 8 items
  /// (kit-v2.md §1.5); hosts ask this instead of comparing to a literal.
  static bool worthShowing(int itemCount) => itemCount > showAbove;
  static const int showAbove = 8;

  @override
  State<KitSearchField> createState() => _KitSearchFieldState();
}

class _KitSearchFieldState extends State<KitSearchField> {
  TextEditingController? _ownController;
  FocusNode? _ownFocus;
  Timer? _settle;

  /// A settled query waits for its count to be announced.
  bool _announcePending = false;
  bool _hovered = false;

  TextEditingController get _controller =>
      widget.controller ?? (_ownController ??= TextEditingController());
  FocusNode get _focus => widget.focusNode ?? (_ownFocus ??= FocusNode());

  bool get _hasQuery => _controller.text.isNotEmpty;

  @override
  void initState() {
    super.initState();
    _controller.addListener(_onText);
  }

  @override
  void didUpdateWidget(KitSearchField old) {
    super.didUpdateWidget(old);
    if (old.controller != widget.controller) {
      (old.controller ?? _ownController)?.removeListener(_onText);
      _controller.addListener(_onText);
    }
    // A count that arrives after the query settled (a remote search), or a
    // partial count that completes, is announced once.
    if (_settle == null &&
        _hasQuery &&
        widget.resultCount != null &&
        (_announcePending ||
            (old.partial && !widget.partial) ||
            (old.resultCount == null))) {
      _scheduleAnnounce();
    }
  }

  @override
  void dispose() {
    _settle?.cancel();
    _controller.removeListener(_onText);
    _ownController?.dispose();
    _ownFocus?.dispose();
    super.dispose();
  }

  String _lastText = '';

  void _onText() {
    final text = _controller.text;
    if (text == _lastText) return; // a selection change
    final wasEmpty = _lastText.isEmpty;
    _lastText = text;
    if (wasEmpty != text.isEmpty) setState(() {});
    _settle?.cancel();
    if (text.isEmpty) {
      // Clearing is immediate: no wait, no announcement.
      _settle = null;
      _announcePending = false;
      widget.onChanged('');
      return;
    }
    _settle = Timer(KitMotion.typingSettle, () {
      _settle = null;
      if (!mounted) return;
      _announcePending = true;
      widget.onChanged(_controller.text);
      // The host may answer with a count in the same frame.
      if (widget.resultCount != null) _scheduleAnnounce();
    });
  }

  /// Enter settles the query now: a pending [onChanged] runs at once (not
  /// again when the wait ends), so the host acts on what was typed and
  /// hears it once.
  void _submitted(String text) {
    final pending = _settle;
    if (pending != null) {
      pending.cancel();
      _settle = null;
      _announcePending = true;
      widget.onChanged(text);
      if (widget.resultCount != null) _scheduleAnnounce();
    }
    widget.onSubmitted?.call(text);
  }

  void _scheduleAnnounce() {
    _announcePending = false;
    SchedulerBinding.instance.addPostFrameCallback((_) {
      if (!mounted || _settle != null || !_hasQuery) return;
      final count = widget.resultCount;
      if (count == null) return;
      final view = View.maybeOf(context);
      if (view == null) return;
      unawaited(
        SemanticsService.sendAnnouncement(
          view,
          _countText(_kitSearchWords(context), count),
          Directionality.of(context),
        ),
      );
    });
    SchedulerBinding.instance.ensureVisualUpdate();
  }

  String _countText(AppLocalizations l10n, int count) => widget.partial
      ? l10n.kitSearchPartial(count)
      : l10n.kitSearchResults(count);

  void _clear() {
    _controller.clear();
    if (widget.enabled) _focus.requestFocus();
  }

  /// Arrow Down: the first focusable below the field (the first result;
  /// the active filter's remove when it sits between). The field's own
  /// buttons are beside it, so they are not in the way.
  void _focusAfter() => _focus.focusInDirection(TraversalDirection.down);

  KeyEventResult _onKey(FocusNode node, KeyEvent event) {
    if (event is! KeyDownEvent && event is! KeyRepeatEvent) {
      return KeyEventResult.ignored;
    }
    final key = event.logicalKey;
    if (key == LogicalKeyboardKey.escape) {
      if (!_hasQuery) return KeyEventResult.ignored;
      _clear();
      return KeyEventResult.handled;
    }
    if (key == LogicalKeyboardKey.arrowDown && _focus.hasPrimaryFocus) {
      _focusAfter();
      return KeyEventResult.handled;
    }
    return KeyEventResult.ignored;
  }

  Future<void> _openFilters(BuildContext anchor) async {
    await showKitMenu(
      anchor,
      items: widget.filters,
      semanticsLabel: _kitSearchWords(context).kitSearchFilter,
    );
  }

  @override
  Widget build(BuildContext context) =>
      KitMaxWidth.list(child: _buildContent(context));

  Widget _buildContent(BuildContext context) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final l10n = _kitSearchWords(context);
    final reduced = KitMotion.reduced(context);
    final wide = KitLayout.windowOf(context) != KitWindow.compact;
    final hasQuery = _hasQuery;
    final focused = _focus.hasFocus && widget.enabled;
    final disabledReason = widget.enabled ? null : widget.disabledReason;

    final style = KitText.styleOf(
      context,
      KitTextRole.body,
      tone: widget.enabled ? KitTextTone.primary : KitTextTone.tertiary,
    );
    final hintStyle = KitText.styleOf(
      context,
      KitTextRole.body,
      tone: KitTextTone.tertiary,
    );
    final scaler = MediaQuery.textScalerOf(context);
    final scaledLine = scaler.scale(style.fontSize! * style.height!);
    final lineBox = scaledLine > tokens.minTarget
        ? scaledLine
        : tokens.minTarget;
    final vertical = ((tokens.buttonHeight - tokens.minTarget) / 2)
        .floorToDouble();

    Widget editable = TextField(
      key: widget.fieldKey,
      controller: _controller,
      focusNode: _focus,
      enabled: widget.enabled,
      autofocus: widget.autofocus,
      textInputAction: TextInputAction.search,
      keyboardType: TextInputType.text,
      autocorrect: false,
      maxLines: 1,
      style: style,
      strutStyle: StrutStyle(
        fontFamily: style.fontFamily,
        fontFamilyFallback: style.fontFamilyFallback,
        fontSize: style.fontSize,
        height: lineBox / scaler.scale(style.fontSize!),
        forceStrutHeight: true,
        leadingDistribution: TextLeadingDistribution.even,
      ),
      cursorHeight: scaledLine,
      cursorColor: roles.accent,
      onSubmitted: _submitted,
      decoration: InputDecoration(
        isCollapsed: true,
        // The hint equals the label, so nothing is lost when it goes; the
        // label names the field once (below), so the hint is not read.
        // Only while empty: a hidden hint would sit under the query.
        hint: hasQuery
            ? null
            : ExcludeSemantics(
                child: Text(
                  widget.label,
                  style: hintStyle,
                  // Large text wraps the hint rather than cutting it to a
                  // few words ("Find settings, tools, …", emulator QA F8).
                  maxLines: scaler.scale(1) >= 1.3 ? 2 : 1,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
        hintFadeDuration: reduced ? Duration.zero : null,
        floatingLabelBehavior: FloatingLabelBehavior.never,
        filled: false,
        hoverColor: Colors.transparent,
        border: InputBorder.none,
        enabledBorder: InputBorder.none,
        focusedBorder: InputBorder.none,
        disabledBorder: InputBorder.none,
        errorBorder: InputBorder.none,
        focusedErrorBorder: InputBorder.none,
        contentPadding: EdgeInsetsDirectional.only(
          end: tokens.space2,
          top: vertical,
          bottom: vertical,
        ),
      ),
    );
    editable = Semantics(
      label: widget.label,
      hint: disabledReason,
      child: editable,
    );

    final filterLabel = widget.activeFilter == null
        ? l10n.kitSearchFilter
        : l10n.kitSearchFilterActive(widget.activeFilter!);
    Widget? filter;
    if (widget.filters.isNotEmpty) {
      filter = Builder(
        builder: (anchor) {
          final onPressed = widget.enabled
              ? () => unawaited(_openFilters(anchor))
              : null;
          if (!wide) {
            return KitIconButton(
              key: widget.filterKey,
              icon: AppIconography.filter,
              size: 20,
              tooltip: filterLabel,
              selected: widget.activeFilter != null,
              disabledReason: disabledReason,
              onPressed: onPressed,
            );
          }
          // From medium up the word "Filter" shows; its name still says
          // the state ("Filter: Symbols"), which the chip shows in words.
          return Semantics(
            container: true,
            button: true,
            enabled: onPressed != null,
            label: filterLabel,
            hint: disabledReason,
            onTap: onPressed,
            excludeSemantics: true,
            child: KitButton.fromAction(
              KitAction(
                key: widget.filterKey,
                label: l10n.kitSearchFilter,
                icon: AppIconography.filter,
                onPressed: onPressed,
                disabledReason: disabledReason,
              ),
              role: KitButtonRole.tertiary,
              expand: false,
            ),
          );
        },
      );
    }

    final clear = AnimatedSwitcher(
      duration: reduced ? Duration.zero : KitMotion.quick,
      child: hasQuery
          ? KitIconButton(
              key: widget.clearKey ?? const ValueKey('kit-search-clear'),
              icon: AppIconography.close,
              size: 20,
              tooltip: l10n.kitSearchClear,
              disabledReason: disabledReason,
              onPressed: widget.enabled ? _clear : null,
            )
          : const SizedBox.shrink(),
    );

    final iconSize = tokens.iconSize(context, tokens.smallIconSize);
    final row = Row(
      children: [
        // Decorative: the label already says what the field is.
        ExcludeSemantics(
          child: Padding(
            padding: EdgeInsetsDirectional.only(
              start: tokens.space4,
              end: tokens.space3,
            ),
            child: Icon(
              AppIconography.search,
              size: iconSize,
              color: roles.text3,
            ),
          ),
        ),
        Expanded(child: editable),
        ?filter,
        clear,
      ],
    );

    // The prominent field is a pill: a stadium, or the style's square once
    // Corners › Sharp squares the pills (hook H4).
    final radius = BorderRadius.circular(
      widget.prominent
          ? math.min(tokens.pillRadius, tokens.buttonHeight / 2)
          : tokens.fieldCorner,
    );
    final own = kitFieldFrame(
      context,
      fill: roles.surface1,
      edge: focused
          ? roles.accent
          : _hovered && widget.enabled
          ? roles.text3
          : widget.prominent
          ? roles.surface1
          : roles.hairline,
      thick: focused,
      radius: radius,
    );
    // Solid's frame is the field's own; every other style makes it that
    // style's field (a tint and rim, a well, an ink box) with a boundary
    // that reads at 3:1 at rest, and rings it outside while focused.
    final look = KitSurfaceLook.decorate(
      context,
      solid: own,
      role: KitSurfaceRole.field,
      borderRadius: radius,
      selected: focused,
    );
    final frame = MouseRegion(
      onEnter: (_) => setState(() => _hovered = true),
      onExit: (_) => setState(() => _hovered = false),
      child: kitFieldRing(
        context,
        own: own,
        look: look,
        color: focused ? roles.accent : null,
        child: kitSurfaceBox(
          style: KitSurfaceLook.styleOf(context),
          decoration: look,
          child: ConstrainedBox(
            constraints: BoxConstraints(minHeight: tokens.buttonHeight),
            child: row,
          ),
        ),
      ),
    );

    final count = widget.resultCount;
    final countLine = hasQuery && count != null
        ? KitText(
            _countText(l10n, count),
            role: KitTextRole.secondary,
            tone: KitTextTone.secondary,
            tabular: true,
          )
        : null;
    final chip = widget.activeFilter == null
        ? null
        : KitChip.removable(
            label: widget.activeFilter!,
            onRemove: widget.onClearFilter!,
          );

    final Widget? under;
    if (countLine == null && chip == null) {
      under = null;
    } else {
      under = Padding(
        padding: EdgeInsets.only(top: tokens.space2),
        child: Wrap(
          spacing: tokens.space3,
          runSpacing: tokens.space2,
          crossAxisAlignment: WrapCrossAlignment.center,
          children: [?chip, ?countLine],
        ),
      );
    }

    return PopScope(
      canPop: !hasQuery,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop && _hasQuery) _clear();
      },
      child: Focus(
        canRequestFocus: false,
        skipTraversal: true,
        onKeyEvent: _onKey,
        onFocusChange: (_) => setState(() {}),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            frame,
            KitReveal(child: under),
            if (disabledReason != null)
              Padding(
                padding: EdgeInsets.only(top: tokens.space2),
                child: KitText(
                  disabledReason,
                  role: KitTextRole.secondary,
                  tone: KitTextTone.secondary,
                ),
              ),
          ],
        ),
      ),
    );
  }
}

/// The inline "nothing matches" state under a search: a [KitStateView]
/// (inline) with "Clear search" and at most one further way forward.
///
/// States: one (no match). The query the person typed is isolated
/// ([KitBidi.auto]) and quoted. It is announced once when it replaces the
/// list. The host never shows it while its search is partial (STATE-11).
class KitSearchNoMatch extends StatelessWidget {
  const KitSearchNoMatch({
    super.key,
    required this.query,
    required this.onClear,
    this.what,
    this.action,
    this.titleKey,
    this.calm = false,
  });

  /// The calm form (vision 2026-10-20): the words centred, larger, with no
  /// icon tile, and Clear search as one quiet text action under them
  /// ([KitStateView.calm] inline). [action] then waits as a second text
  /// action. For a screen whose other empty states are calm.
  final bool calm;

  /// Shown isolated: Nothing matches "opus".
  final String query;

  /// "Clear search".
  final VoidCallback onClear;

  /// "models": Nothing in models matches "opus".
  final String? what;

  /// One more way forward, e.g. "Search all projects".
  final KitAction? action;
  final Key? titleKey;

  @override
  Widget build(BuildContext context) => KitMaxWidth.cap(
    maxWidth: KitLayout.stateMaxWidth,
    alignment: AlignmentDirectional.centerStart,
    child: _buildContent(context),
  );

  Widget _buildContent(BuildContext context) {
    final l10n = _kitSearchWords(context);
    final quoted = '“${KitBidi.auto(query)}”';
    final title = what == null
        ? l10n.kitSearchNoMatch(quoted)
        : l10n.kitSearchNoMatchIn(KitBidi.auto(what!), quoted);
    final clear = KitAction(label: l10n.kitSearchClear, onPressed: onClear);
    if (calm) {
      return KitStateView.calm(
        icon: AppIconography.search,
        title: title,
        size: KitStateSize.inline,
        titleKey: titleKey,
        tertiary: [clear, ?action],
      );
    }
    return KitStateView(
      icon: AppIconography.search,
      title: title,
      size: KitStateSize.inline,
      titleKey: titleKey,
      primary: clear,
      secondary: action,
    );
  }
}

/// The kit's words: the app's bound [AppLocalizations], else the locale's
/// lookup, else English, so the part never throws in a bare harness with no
/// localization delegates (R8).
AppLocalizations _kitSearchWords(BuildContext context) {
  final bound = Localizations.of<AppLocalizations>(context, AppLocalizations);
  if (bound != null) return bound;
  final locale = Localizations.maybeLocaleOf(context);
  if (locale != null && AppLocalizations.delegate.isSupported(locale)) {
    return lookupAppLocalizations(locale);
  }
  return AppLocalizationsEn();
}
