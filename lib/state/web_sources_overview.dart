import 'dart:async';

import 'package:flutter/foundation.dart';

import '../domain/server_gateway.dart';
import '../domain/web_source_selection.dart';
import 'connection.dart';

/// Search/review is transient. Only explicit confirmation returns draft context.
class WebSourcesOverview extends ChangeNotifier {
  static const maxSources = 10;
  final ConnectionController controller;
  final Object? _gateway;
  final Object? _repository;
  final String? _profileID;
  final int _locationRevision;
  final String? _directory;
  final String? _workspace;
  final List<WebSourceSelection> _sources = [];
  final Set<String> _selected = {};
  bool _scopeChanged = false;
  bool _disposed = false;
  int _searchRevision = 0;
  int _providerRevision = 0;
  StreamSubscription<dynamic>? _events;
  List<WebSearchProvider> providers = const [];
  List<WebSourceSelection> results = const [];
  String? providerID;
  bool discovering = false;
  bool _discoverAgain = false;
  bool searching = false;
  bool searched = false;
  int omittedResults = 0;
  WebSearchFailureKind? searchFailure;
  bool get supportsSearch =>
      _gateway is WebSearchGateway &&
      controller.api?.capabilities.webSearch == true;

  WebSourcesOverview({required this.controller})
    : _gateway = controller.api,
      _repository = controller.repository,
      _profileID = controller.profile?.id,
      _locationRevision = controller.locationRevision,
      _directory = controller.directory,
      _workspace = controller.workspace {
    controller.addListener(_checkScope);
    controller.profileDataChanges.addListener(_checkScope);
    _checkScope();
    _events = controller.events.listen((event) {
      if (event.type == 'websearch.updated' ||
          event.type == 'server.connected') {
        unawaited(discoverProviders());
      }
    });
  }

  bool get scopeChanged => _scopeChanged;
  List<WebSourceSelection> get sources => List.unmodifiable(_sources);
  bool isSelected(WebSourceSelection source) => _selected.contains(source.url);
  int get selectedCount => _selected.length;

  void _checkScope() {
    if (_scopeChanged || _disposed) {
      return;
    }
    if (_profileID == null ||
        !controller.isProfileReadable(_profileID) ||
        _profileID != controller.profile?.id ||
        _locationRevision != controller.locationRevision ||
        _directory != controller.directory ||
        _workspace != controller.workspace ||
        !identical(_gateway, controller.api) ||
        !identical(_repository, controller.repository)) {
      _scopeChanged = true;
      _searchRevision++;
      _providerRevision++;
      providers = const [];
      results = const [];
      searching = false;
      discovering = false;
      _sources.clear();
      _selected.clear();
      notifyListeners();
    }
  }

  bool _canEdit() {
    _checkScope();
    return !_scopeChanged && !_disposed;
  }

  Future<void> discoverProviders() async {
    if (!_canEdit() || !supportsSearch) {
      return;
    }
    if (discovering) {
      // Coalesce invalidation bursts into one subsequent discovery. Invalidate
      // the active result immediately, without creating parallel requests.
      ++_providerRevision;
      ++_searchRevision;
      _discoverAgain = true;
      return;
    }
    final revision = ++_providerRevision;
    ++_searchRevision;
    searching = false;
    discovering = true;
    results = const [];
    providers = const [];
    searched = false;
    searchFailure = null;
    notifyListeners();
    try {
      final found = await (_gateway as WebSearchGateway).webSearchProviders();
      if (!_canEdit() || revision != _providerRevision) {
        return;
      }
      providers = found;
      if (!providers.any((item) => item.id == providerID)) {
        providerID = providers.length == 1 ? providers.single.id : null;
      }
    } on WebSearchFailure catch (error) {
      if (!_canEdit() || revision != _providerRevision) {
        return;
      }
      searchFailure = error.kind;
      providerID = null;
    } catch (_) {
      if (!_canEdit() || revision != _providerRevision) {
        return;
      }
      searchFailure = WebSearchFailureKind.failed;
      providerID = null;
    } finally {
      if (_canEdit()) {
        discovering = false;
        notifyListeners();
        if (_discoverAgain) {
          _discoverAgain = false;
          unawaited(discoverProviders());
        }
      }
    }
  }

  void chooseProvider(String? id) {
    if (!_canEdit() || !providers.any((item) => item.id == id)) {
      return;
    }
    ++_searchRevision;
    providerID = id;
    searching = false;
    searched = false;
    results = const [];
    searchFailure = null;
    notifyListeners();
  }

  Future<void> search(String query) async {
    if (!_canEdit() ||
        !supportsSearch ||
        discovering ||
        searching ||
        query.trim().isEmpty ||
        query.length > 1000 ||
        !providers.any((item) => item.id == providerID)) {
      return;
    }
    final revision = ++_searchRevision;
    final chosen = providerID!;
    searching = true;
    searched = false;
    results = const [];
    omittedResults = 0;
    searchFailure = null;
    notifyListeners();
    try {
      final response = await (_gateway as WebSearchGateway).searchWeb(
        query,
        providerID: chosen,
      );
      if (!_canEdit() || revision != _searchRevision) {
        return;
      }
      if (response.providerID != chosen) {
        throw const WebSearchFailure(WebSearchFailureKind.invalidResponse);
      }
      final safe = <WebSourceSelection>[];
      final seen = <String>{};
      for (final result in response.results.take(100)) {
        try {
          final source = WebSourceSelection(
            title: result.title ?? '',
            url: result.url,
            excerpt: result.content,
          );
          if (seen.add(source.url)) {
            safe.add(source);
          }
        } on FormatException {
          omittedResults++;
        }
      }
      results = List.unmodifiable(safe);
      searched = true;
    } on WebSearchFailure catch (error) {
      if (!_canEdit() || revision != _searchRevision) {
        return;
      }
      searchFailure = error.kind;
      if (error.kind == WebSearchFailureKind.unavailable) {
        providers = const [];
        providerID = null;
      }
    } catch (_) {
      if (!_canEdit() || revision != _searchRevision) {
        return;
      }
      searchFailure = WebSearchFailureKind.failed;
    } finally {
      if (_canEdit() && revision == _searchRevision) {
        searching = false;
        notifyListeners();
      }
    }
  }

  /// Returns safe, app-authored validation copy; failures retain all sources.
  String? add(WebSourceSelection source) {
    if (!_canEdit()) {
      return 'Connection changed. Close and reopen Add web source.';
    }
    if (_sources.any((item) => item.url == source.url)) {
      return 'This URL is already in your review list.';
    }
    if (_sources.length >= maxSources) {
      return 'Review at most 10 sources at a time.';
    }
    _sources.add(source);
    _selected.add(source.url);
    notifyListeners();
    return null;
  }

  void select(WebSourceSelection source, bool selected) {
    if (!_canEdit() || !_sources.contains(source)) {
      return;
    }
    if (selected) {
      _selected.add(source.url);
    } else {
      _selected.remove(source.url);
    }
    notifyListeners();
  }

  void remove(WebSourceSelection source) {
    if (!_canEdit()) {
      return;
    }
    _sources.remove(source);
    _selected.remove(source.url);
    notifyListeners();
  }

  /// Null means the original source/profile/location is no longer valid.
  List<WebSourceSelection>? reviewedSelection() {
    if (!_canEdit()) {
      return null;
    }
    return List.unmodifiable(_sources.where(isSelected));
  }

  @override
  void dispose() {
    _disposed = true;
    _events?.cancel();
    controller.removeListener(_checkScope);
    controller.profileDataChanges.removeListener(_checkScope);
    _sources.clear();
    _selected.clear();
    super.dispose();
  }
}
