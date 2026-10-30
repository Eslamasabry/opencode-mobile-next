import 'models.dart' show ModelRef;
import 'server_gateway.dart' show CatalogModel;

/// Why a model is offered on the model sheet's first view. Each reason is a
/// fact the server or this app knows, never a judgement
/// (docs/qa/vision-model-picker-2026-10-20/README.md):
///
/// - [inUse]: the model chosen now, when no other reason already shows it.
/// - [serverDefault]: the server's default (OpenCode 2 `/model/default`,
///   OpenCode 1's `default`, a phone agent's `isDefault`).
/// - [recent]: chosen before on this server (the app's own record).
/// - [cheapest]: the lowest published input + output price, only where
///   prices differ.
///
/// There is no "fastest": no catalog sends a speed.
enum ModelReason { inUse, serverDefault, recent, cheapest }

/// One recommended model and why.
class ModelRecommendation {
  const ModelRecommendation({
    required this.model,
    required this.reason,
    this.alsoCheapest = false,
    this.free = false,
  });

  final CatalogModel model;
  final ModelReason reason;

  /// The cheapest model is this row's model too (one model, one row).
  final bool alsoCheapest;

  /// Its published price is zero.
  final bool free;

  ModelRef get ref => ModelRef(providerID: model.providerID, modelID: model.id);
}

bool _same(CatalogModel model, ModelRef? ref) {
  if (ref == null) return false;
  final a = ref.normalized;
  final b = ModelRef(
    providerID: model.providerID,
    modelID: model.id,
  ).normalized;
  return a.providerID == b.providerID && a.modelID == b.modelID;
}

/// A published price of zero for input and output.
bool modelIsFree(CatalogModel model) {
  final cost = model.cost;
  return cost != null &&
      cost.inputPerMillion == 0 &&
      cost.outputPerMillion == 0;
}

/// At most [limit] recommended models from [models] (the list this scope
/// may use, in the catalog's display order), top to bottom: the one in use
/// when nothing else shows it, the server default, a recent choice, the
/// cheapest. Fewer when fewer are true; one model is never two rows.
///
/// [defaults] are the server's default refs, most authoritative first.
/// [recent] is newest first; [favorites] only break a price tie.
List<ModelRecommendation> recommendModels(
  List<CatalogModel> models, {
  ModelRef? current,
  List<ModelRef> defaults = const [],
  List<ModelRef> recent = const [],
  List<ModelRef> favorites = const [],
  int limit = 3,
}) {
  final usable = models.where((model) => model.enabled).toList();
  if (usable.isEmpty || limit <= 0) return const [];
  CatalogModel? find(ModelRef? ref) {
    for (final model in usable) {
      if (_same(model, ref)) return model;
    }
    return null;
  }

  CatalogModel? serverDefault;
  for (final ref in defaults) {
    serverDefault = find(ref);
    if (serverDefault != null) break;
  }

  // Cheapest: only among models with a published price, and only when the
  // prices differ (all free, or all the same price, names no cheapest).
  final priced = [
    for (final model in usable)
      if (model.cost != null) model,
  ];
  double price(CatalogModel model) =>
      model.cost!.inputPerMillion + model.cost!.outputPerMillion;
  CatalogModel? cheapest;
  if (priced.length > 1) {
    final low = priced.map(price).reduce((a, b) => a < b ? a : b);
    final high = priced.map(price).reduce((a, b) => a > b ? a : b);
    if (low < high) {
      final tied = priced.where((model) => price(model) == low).toList();
      int rank(CatalogModel model) {
        if (_same(model, current)) return 0;
        if (identical(model, serverDefault)) return 1;
        final recentAt = recent.indexWhere((ref) => _same(model, ref));
        if (recentAt >= 0) return 2 + recentAt;
        final favoriteAt = favorites.indexWhere((ref) => _same(model, ref));
        if (favoriteAt >= 0) return 100 + favoriteAt;
        return 1000 + tied.indexOf(model);
      }

      tied.sort((a, b) => rank(a).compareTo(rank(b)));
      cheapest = tied.first;
    }
  }

  final inUse = find(current);
  final rows = <ModelRecommendation>[
    if (inUse != null &&
        !identical(inUse, serverDefault) &&
        !identical(inUse, cheapest))
      ModelRecommendation(
        model: inUse,
        reason: ModelReason.inUse,
        free: modelIsFree(inUse),
      ),
    if (serverDefault case final model?)
      ModelRecommendation(
        model: model,
        reason: ModelReason.serverDefault,
        alsoCheapest: identical(model, cheapest),
        free: modelIsFree(model),
      ),
  ];
  final cheapestRow = cheapest == null || identical(cheapest, serverDefault)
      ? null
      : ModelRecommendation(
          model: cheapest,
          reason: ModelReason.cheapest,
          free: modelIsFree(cheapest),
        );
  final room = limit - rows.length - (cheapestRow == null ? 0 : 1);
  if (room > 0) {
    for (final ref in recent) {
      final model = find(ref);
      if (model == null ||
          identical(model, inUse) ||
          identical(model, serverDefault) ||
          identical(model, cheapest)) {
        continue;
      }
      rows.add(
        ModelRecommendation(
          model: model,
          reason: ModelReason.recent,
          free: modelIsFree(model),
        ),
      );
      break;
    }
  }
  if (cheapestRow != null) rows.add(cheapestRow);
  return rows.take(limit).toList();
}
