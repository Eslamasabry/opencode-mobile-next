import 'dart:async';

import 'package:flutter/material.dart';
import 'package:intl/intl.dart' show NumberFormat;
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:opencode_mobile/ui/kit/kit_buttons.dart';
import 'package:opencode_mobile/ui/kit/kit_chip.dart';
import 'package:opencode_mobile/ui/kit/kit_icon_button.dart';
import 'package:opencode_mobile/ui/kit/kit_menu.dart';
import 'package:opencode_mobile/ui/kit/kit_motion.dart';
import 'package:opencode_mobile/ui/kit/kit_notice.dart';
import 'package:opencode_mobile/ui/kit/kit_page_route.dart';
import 'package:opencode_mobile/ui/kit/kit_progress.dart';
import 'package:opencode_mobile/ui/kit/kit_row.dart';
import 'package:opencode_mobile/ui/kit/kit_row_parts.dart';
import 'package:opencode_mobile/ui/kit/kit_search_field.dart';
import 'package:opencode_mobile/ui/kit/kit_segmented.dart';
import 'package:opencode_mobile/ui/kit/kit_sheet.dart';
import 'package:opencode_mobile/ui/kit/kit_state_view.dart';
import 'package:opencode_mobile/ui/kit/kit_text.dart';
import 'package:opencode_mobile/ui/kit/kit_tokens.dart';

import '../../api/models.dart';
import '../../api/provider_presentation.dart';
import '../../api/product_repository.dart';
import '../../state/connection.dart';
import '../../domain/free_model.dart' show openCodeFreeProviderID;
import '../../domain/model_display_name.dart' show modelNameFromId;
import '../widgets/connect_methods.dart';
import 'product_states.dart' show productErrorDetails;
import '../../state/model_library.dart';
import '../../l10n/app_localizations.dart';
import '../app_iconography.dart';
import '../app_theme.dart' show AppStatusTone;
import '../screens/library_screen.dart'
    show IntegrationsMode, IntegrationsScreen;

part 'pickers_catalog_sections.dart';
part 'pickers_catalog_footer.dart';
part 'pickers_catalog_models.dart';

AppLocalizations _pickerStrings(BuildContext context) =>
    Localizations.of<AppLocalizations>(context, AppLocalizations) ??
    lookupAppLocalizations(const Locale('en'));

/// How applying a model/agent selection is scoped and labeled.
///
/// [classic] keeps the v1 wording ("Use model and mode") — selection is
/// client-side state attached to each prompt. On OpenCode 2 servers the
/// selection is session state instead: [session] labels the apply action
/// "Use for this conversation" (picker opened with an active session), and
/// [newSessions] labels it "Use for new conversations" (no session yet — the
/// choice becomes the default for `POST /api/session`).
enum ModelPickerApplyScope { classic, session, newSessions }

/// A reasoning-effort variant as a person reads it: "Extra high" for
/// `xhigh`, "No thinking" for `none`. Unknown ids are shown as they are.
String presentedEffort(String id, AppLocalizations strings) =>
    switch (id.trim().toLowerCase()) {
      'none' => strings.modelEffortNone,
      'minimal' => strings.modelEffortMinimal,
      'low' => strings.modelEffortLow,
      'medium' => strings.modelEffortMedium,
      'high' => strings.modelEffortHigh,
      'xhigh' => strings.modelEffortExtraHigh,
      'max' => strings.modelEffortMax,
      _ => id,
    };

/// Whether [model] was released in the last 30 days (by the server's
/// catalog date). [now] is for tests.
@visibleForTesting
bool isNewModel(CatalogModel model, {DateTime? now}) {
  final released = model.released;
  if (released == null) return false;
  final age = (now ?? DateTime.now()).difference(released);
  return !age.isNegative && age.inDays < 30;
}

String _applyLabel(AppLocalizations strings, ModelPickerApplyScope scope) =>
    switch (scope) {
      ModelPickerApplyScope.classic => strings.modelPickerUseChosenModel,
      ModelPickerApplyScope.session => strings.e7ModelUiUseSession,
      ModelPickerApplyScope.newSessions => strings.e7ModelUiUseNewSessions,
    };

/// Opens the one model sheet, from every door (the composer chip, `/model`,
/// a chat error, Settings › Model, search): every model to pick from, with
/// the thinking level and the agent pinned in its footer beside "Use for
/// this conversation" (or the scope's wording). With [sessionID] and
/// [ModelPickerApplyScope.session] the choice applies to that session only;
/// otherwise it becomes the default for new conversations (Settings).
/// [focusAgent] opens the sheet with the agent menu open.
Future<void> showModelPicker(
  BuildContext context, {
  ModelPickerApplyScope applyScope = ModelPickerApplyScope.classic,
  String? sessionID,
  bool focusAgent = false,
}) {
  final controller = ProviderScope.containerOf(
    context,
    listen: false,
  ).read(connProvider);
  // Catalog membership is server-owned and can change while the app remains
  // connected. Refresh on open once the list is stale (change events and
  // Reload invalidate it at once), behind the list already shown.
  unawaited(controller.ensureCatalog());
  if (sessionID != null && controller.serverOwnsSessionSelection) {
    unawaited(controller.ensureSession(sessionID));
  }
  if (applyScope == ModelPickerApplyScope.classic &&
      controller.serverOwnsSessionSelection) {
    applyScope = ModelPickerApplyScope.newSessions;
  }
  final strings = _pickerStrings(context);
  final apply = _SheetApply();
  final scope = applyScope;
  return showKitSheet<void>(
    context,
    title: strings.modelChooseTitle,
    subtitle: scope == ModelPickerApplyScope.session
        ? strings.modelSessionScopeNote
        : null,
    icon: AppIconography.model,
    height: KitSheetHeight.full,
    loading: apply.applying,
    footer: (_) => ValueListenableBuilder<int>(
      valueListenable: apply.footerTick,
      builder: (footerContext, _, _) =>
          apply.footer?.call(footerContext) ?? const SizedBox.shrink(),
    ),
    // Until the view has built: the scope's wording, or nothing to apply
    // while there is no catalog to choose from.
    primaryListenable: apply.primary
      ..value = controller.catalog?.models.isNotEmpty == true
          ? KitAction(
              key: const Key('model-picker-apply'),
              label: _applyLabel(strings, scope),
              onPressed: apply.run,
            )
          : null,
    body: (sheetContext) => ModelCatalogView._sheet(
      controller: controller,
      apply: apply,
      // Applied: the sheet closes. Not maybePop, which a search field's
      // "back clears the query first" would intercept and keep it open.
      onApplied: () {
        final route = ModalRoute.of(sheetContext);
        if (route != null && route.isCurrent) {
          Navigator.of(sheetContext).pop();
        }
      },
      applyScope: scope,
      sessionID: sessionID,
      focusAgent: focusAgent,
    ),
  ).whenComplete(apply.dispose);
}

/// The sheet's pinned primary runs the view's apply through this; the view
/// reports its in-flight save back as the sheet's loading bar, and draws
/// the pinned footer (thinking level, agent) through [footer].
class _SheetApply {
  final applying = ValueNotifier<bool>(false);

  /// The sheet's pinned primary; its label names what it applies ("Use
  /// Claude Opus 5.5 · Build"), so the view renames it as the draft changes.
  final primary = ValueNotifier<KitAction?>(null);

  /// Bumped after the view builds, so the pinned footer redraws with it.
  final footerTick = ValueNotifier<int>(0);

  /// The view's footer; null while no view is attached.
  WidgetBuilder? footer;
  Future<void> Function()? _handler;
  (String?, String?)? _offered;
  bool _footerQueued = false;

  void run() {
    final handler = _handler;
    if (handler != null) unawaited(handler());
  }

  /// Renames the primary after the frame (called from the view's build);
  /// [blockedBy] says why it cannot apply yet. A null [label] takes the
  /// primary away (no catalog: nothing to apply).
  void offerPrimary(String? label, {String? blockedBy}) {
    final offer = (label, blockedBy);
    if (offer == _offered) return;
    _offered = offer;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_disposed) return;
      primary.value = label == null
          ? null
          : KitAction(
              key: const Key('model-picker-apply'),
              label: label,
              onPressed: blockedBy == null ? run : null,
              disabledReason: blockedBy,
            );
    });
  }

  /// Redraws the footer after the frame (called from the view's build).
  void footerChanged() {
    if (_footerQueued) return;
    _footerQueued = true;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _footerQueued = false;
      if (_disposed) return;
      footerTick.value++;
    });
  }

  bool _disposed = false;

  void dispose() {
    _disposed = true;
    applying.dispose();
    primary.dispose();
    footerTick.dispose();
  }
}

enum _ModelIntent { all, fast, reasoning, context }

enum _ModelCollection { all, favorites, recent }

/// How many model rows show before "Show more": the sheet body is not
/// virtualised, so a catalog of hundreds of models grows on request.
const _modelPage = 60;

/// The single model, thinking level and agent selector used throughout the
/// app: the body of the model sheet ([showModelPicker]); tests and
/// captures also host it on its own.
///
/// Top to bottom: notices about this catalog (only when they apply), a
/// "Reload providers" row when a signed-in provider is not loaded, then
/// search with its filter menu, All / Favorites / Recent with Refresh, and
/// the models; the chosen one is the checked row, with its details under
/// it. The footer, pinned with the apply action, holds the thinking level
/// and the agent as two chips that each open a menu (no dialog over the
/// sheet). With a bounded height the list scrolls and the footer and apply
/// action are pinned under it; inside the sheet the sheet pins them.
class ModelCatalogView extends StatefulWidget {
  const ModelCatalogView({
    super.key,
    required this.controller,
    this.scrollController,
    this.onApplied,
    this.onClose,
    this.showHeader = true,
    this.applyScope = ModelPickerApplyScope.classic,
    this.sessionID,
    this.focusAgent = false,
  }) : _apply = null;

  const ModelCatalogView._sheet({
    required this.controller,
    required _SheetApply apply,
    this.onApplied,
    this.applyScope = ModelPickerApplyScope.classic,
    this.sessionID,
    this.focusAgent = false,
  }) : _apply = apply,
       scrollController = null,
       onClose = null,
       showHeader = false;

  final ConnectionController controller;
  final ScrollController? scrollController;
  final VoidCallback? onApplied;
  final VoidCallback? onClose;
  final bool showHeader;
  final ModelPickerApplyScope applyScope;
  final String? sessionID;

  /// Opens with the agent choice unfolded.
  final bool focusAgent;

  final _SheetApply? _apply;

  @override
  State<ModelCatalogView> createState() => _ModelCatalogViewState();
}

class _ModelCatalogViewState extends State<ModelCatalogView>
    with WidgetsBindingObserver {
  AppLocalizations get _strings => _pickerStrings(context);

  /// Extensions in the part files cannot call [setState] directly.
  void _set(VoidCallback fn) => setState(fn);

  // 1,048,576 reads "1M", not "1.0M"; 1,050,000 too.
  static String _compactNumber(int value) => value >= 1000000
      ? '${(value / 1000000).toStringAsFixed(1).replaceFirst(RegExp(r'\.0$'), '')}M'
      : value >= 1000
      ? '${(value / 1000).round()}K'
      : '$value';

  static String _money(double value) => value >= 1
      ? '\$${value.toStringAsFixed(2)}'
      : '\$${value.toStringAsFixed(3)}';

  final _search = TextEditingController();

  /// The search row: brought to the top of the scrolling body whenever the
  /// keyboard is up, so the results sit right under it, not below the fold.
  final _searchRow = GlobalKey();
  String _query = '';
  String _provider = '*';
  _ModelIntent _intent = _ModelIntent.all;
  _ModelCollection _collection = _ModelCollection.all;
  int _visible = _modelPage;
  ModelRef? _draftModel;
  String _draftVariant = '';
  bool _applying = false;
  String? _saveError;
  ModelRef? _observedModel;
  String _observedVariant = '';
  String _draftAgent = '';
  String _observedAgent = '';

  /// [ModelCatalogView.focusAgent]: the agent menu opens once, as soon as
  /// the catalog has agents to offer.
  late bool _agentPending = widget.focusAgent;
  String? _scopeProfile;
  int _scopeLocation = 0;

  bool get _sameScope =>
      widget.controller.profile?.id == _scopeProfile &&
      widget.controller.locationRevision == _scopeLocation;

  /// What the server offers to connect (its methods), for the provider
  /// actions; null until loaded or when this server has none.
  List<IntegrationInfo>? _integrations;
  String? _connectStatus;

  ScrollController? _ownedScroll;

  ScrollController get _listScroll =>
      widget.scrollController ?? (_ownedScroll ??= ScrollController());

  @override
  void initState() {
    super.initState();
    _scopeProfile = widget.controller.profile?.id;
    _scopeLocation = widget.controller.locationRevision;
    _syncDraft();
    _observedModel = _currentModel;
    _observedVariant = _currentVariant;
    _observedAgent = _currentAgent;
    widget.controller.addListener(_selectionChanged);
    WidgetsBinding.instance.addObserver(this);
    unawaited(_loadIntegrations());
    widget._apply?._handler = _applyDraft;
    widget._apply?.footer = _footer;
  }

  @override
  void didUpdateWidget(covariant ModelCatalogView oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget._apply != widget._apply) {
      oldWidget._apply?._handler = null;
      oldWidget._apply?.footer = null;
      widget._apply?._handler = _applyDraft;
      widget._apply?.footer = _footer;
    }
    if (oldWidget.controller != widget.controller) {
      oldWidget.controller.removeListener(_selectionChanged);
      widget.controller.addListener(_selectionChanged);
    }
    if (oldWidget.controller != widget.controller ||
        oldWidget.sessionID != widget.sessionID ||
        oldWidget.applyScope != widget.applyScope) {
      _scopeProfile = widget.controller.profile?.id;
      _scopeLocation = widget.controller.locationRevision;
      _syncDraft();
      _observedModel = _currentModel;
      _observedVariant = _currentVariant;
      _observedAgent = _currentAgent;
    }
  }

  /// The session this picker edits, when the apply scope is per-session.
  String? get _scopedSessionID =>
      widget.applyScope == ModelPickerApplyScope.session
      ? widget.sessionID
      : null;

  CatalogSnapshot? get _catalog =>
      widget.controller.catalogForSession(_scopedSessionID);

  ModelRef? get _currentModel => _scopedSessionID == null
      ? widget.controller.selectedModel
      : widget.controller.modelForSession(_scopedSessionID!);

  String get _currentVariant => _scopedSessionID == null
      ? widget.controller.selectedVariant
      : widget.controller.variantForSession(_scopedSessionID!);

  String get _currentAgent => _scopedSessionID == null
      ? widget.controller.selectedAgent
      : widget.controller.agentForSession(_scopedSessionID!);

  /// A save for this session is in flight elsewhere.
  bool get _sessionSaving =>
      _scopedSessionID != null &&
      widget.controller.sessionSelectionSaving(_scopedSessionID!);

  /// Why the choice cannot change now; null when it can.
  String? get _lockedReason => !_sameScope
      ? _strings.modelScopeChanged
      : _applying || _sessionSaving
      ? _strings.modelSelectionSaving
      : null;

  void _syncDraft() {
    _draftAgent = _currentAgent;
    final sessionID = _scopedSessionID;
    if (sessionID != null) {
      _draftModel = widget.controller.modelForSession(sessionID);
      _draftVariant = widget.controller.variantForSession(sessionID);
    } else {
      _draftModel = widget.controller.selectedModel;
      _draftVariant = widget.controller.selectedVariant;
    }
  }

  void _selectionChanged() {
    if (!mounted) return;
    final untouched =
        _draftModel?.wireName == _observedModel?.wireName &&
        _draftVariant == _observedVariant &&
        _draftAgent == _observedAgent;
    _observedModel = _currentModel;
    _observedVariant = _currentVariant;
    _observedAgent = _currentAgent;
    if (untouched && !_applying) setState(_syncDraft);
  }

  @override
  void dispose() {
    if (widget._apply?._handler == _applyDraft) {
      widget._apply?._handler = null;
      widget._apply?.footer = null;
    }
    WidgetsBinding.instance.removeObserver(this);
    widget.controller.removeListener(_selectionChanged);
    _ownedScroll?.dispose();
    _search.dispose();
    super.dispose();
  }

  @override
  void didChangeMetrics() => _revealSearch();

  /// While the keyboard is up, the search row goes to the top of the body:
  /// the sheet shrinks above the keyboard and the banner, notices and
  /// filters above the search would otherwise leave the results no room.
  void _revealSearch() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      final row = _searchRow.currentContext;
      if (row == null) return;
      if (MediaQuery.viewInsetsOf(context).bottom <= 0) return;
      unawaited(
        Scrollable.ensureVisible(
          row,
          alignment: 0,
          duration: KitMotion.reduced(context)
              ? Duration.zero
              : KitMotion.quick,
          curve: KitMotion.enter,
        ),
      );
    });
  }

  void _setApplying(bool value) {
    _applying = value;
    // The sheet disposes its notifiers when it closes, while this body can
    // still be finishing a save during the exit transition.
    final apply = widget._apply;
    if (apply != null && !apply._disposed) apply.applying.value = value;
  }

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: widget.controller,
    builder: (context, _) => LayoutBuilder(
      builder: (context, constraints) {
        final tokens = KitTokens.of(context);
        final catalog = _catalog;
        final drafted = catalog == null ? null : _draftedModel(catalog);
        final items = _items(context, catalog, drafted);
        final inSheet = widget._apply != null;
        widget._apply
          ?..offerPrimary(
            catalog == null || catalog.models.isEmpty
                ? null
                : _applyText(drafted),
            blockedBy: _applying ? null : _applyBlockedBy(drafted),
          )
          ..footerChanged();
        final apply = inSheet ? null : _applyBlock(context, drafted);
        if (!constraints.hasBoundedHeight) {
          // Inside a scrolling host (the sheet): the host scrolls.
          return Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              for (final item in items) item,
              if (apply != null) ...[SizedBox(height: tokens.space3), apply],
            ],
          );
        }
        return Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Expanded(
              child: ListView(
                controller: _listScroll,
                keyboardDismissBehavior:
                    ScrollViewKeyboardDismissBehavior.onDrag,
                padding: EdgeInsetsDirectional.fromSTEB(
                  tokens.gutter,
                  tokens.space2,
                  tokens.gutter,
                  tokens.space4,
                ),
                children: items,
              ),
            ),
            if (apply != null)
              SafeArea(
                top: false,
                child: Padding(
                  padding: EdgeInsetsDirectional.fromSTEB(
                    tokens.gutter,
                    tokens.space2,
                    tokens.gutter,
                    tokens.space3,
                  ),
                  child: apply,
                ),
              ),
          ],
        );
      },
    ),
  );

  String _variantLabel(CatalogVariant variant) {
    final label = variant.options['label'];
    if (label is String && label.isNotEmpty) return label;
    final effort = variant.reasoningEffort;
    if (effort == null || variant.id.toLowerCase() == effort.toLowerCase()) {
      return presentedEffort(variant.id, _strings);
    }
    // Inside a phrase ("fast · low effort") the level stays lower case.
    return _strings.e7ModelUiEffort(
      presentedEffort(variant.id, _strings),
      presentedEffort(effort, _strings).toLowerCase(),
    );
  }

  String _number(int value) =>
      NumberFormat.decimalPattern(_strings.localeName).format(value);
}

/// Copy for the picker notice about providers the server has signed in to
/// but not loaded; [names] are already presented for display. [unusable]
/// says a reload already ran and could not load them; [waitingOnReplies]
/// counts running replies a reload is waiting for.
String unloadedProvidersNotice(
  List<String> names, {
  AppLocalizations? strings,
  bool unusable = false,
  int waitingOnReplies = 0,
}) {
  final l10n = strings ?? lookupAppLocalizations(const Locale('en'));
  final sorted = [...names]..sort();
  final list = switch (sorted.length) {
    0 => l10n.e7ModelUiProviderFallback,
    1 => sorted.single,
    2 => l10n.e7ModelUiProviderPair(sorted[0], sorted[1]),
    _ => l10n.e7ModelUiProviderMany(
      sorted.sublist(0, sorted.length - 1).join(l10n.e7ModelUiListSeparator),
      sorted.last,
    ),
  };
  final count = sorted.length > 1 ? sorted.length : 1;
  final state = unusable
      ? l10n.e7ModelUiUnusableProviders(count, list)
      : l10n.e7ModelUiUnloadedProviders(count, list);
  if (waitingOnReplies <= 0) return state;
  return '$state ${l10n.e7ModelUiProviderReloadWaits(waitingOnReplies)}';
}

/// "\$3.00 in · \$15.00 out /1M" for a model with published pricing; null when
/// the catalog carries no cost.
String? modelCostLabel(CatalogModel model, {AppLocalizations? strings}) {
  final cost = model.cost;
  if (cost == null) return null;
  final input = cost.inputPerMillion;
  final output = cost.outputPerMillion;
  if (input <= 0 && output <= 0) return null;
  String money(double value) => value >= 1
      ? '\$${value.toStringAsFixed(2)}'
      : '\$${value.toStringAsFixed(3)}';
  return (strings ?? lookupAppLocalizations(const Locale('en'))).e7ModelUiCost(
    money(input),
    money(output),
  );
}
