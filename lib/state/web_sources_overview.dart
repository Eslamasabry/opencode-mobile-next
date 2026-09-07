import 'package:flutter/foundation.dart';

import '../domain/web_source_selection.dart';
import 'connection.dart';

/// In-memory review only: no search adapter, network request or persistence.
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
  }

  bool get scopeChanged => _scopeChanged;
  List<WebSourceSelection> get sources => List.unmodifiable(_sources);
  bool isSelected(WebSourceSelection source) => _selected.contains(source.url);
  int get selectedCount => _selected.length;

  void _checkScope() {
    if (_scopeChanged) return;
    if (_profileID == null ||
        !controller.isProfileReadable(_profileID) ||
        _profileID != controller.profile?.id ||
        _locationRevision != controller.locationRevision ||
        _directory != controller.directory ||
        _workspace != controller.workspace ||
        !identical(_gateway, controller.api) ||
        !identical(_repository, controller.repository)) {
      _scopeChanged = true;
      _sources.clear();
      _selected.clear();
      notifyListeners();
    }
  }

  bool _canEdit() {
    _checkScope();
    return !_scopeChanged;
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
    if (!_canEdit() || !_sources.contains(source)) return;
    if (selected) {
      _selected.add(source.url);
    } else {
      _selected.remove(source.url);
    }
    notifyListeners();
  }

  void remove(WebSourceSelection source) {
    if (!_canEdit()) return;
    _sources.remove(source);
    _selected.remove(source.url);
    notifyListeners();
  }

  /// Null means the original source/profile/location is no longer valid.
  List<WebSourceSelection>? reviewedSelection() {
    if (!_canEdit()) return null;
    return List.unmodifiable(_sources.where(isSelected));
  }

  @override
  void dispose() {
    controller.removeListener(_checkScope);
    controller.profileDataChanges.removeListener(_checkScope);
    _sources.clear();
    _selected.clear();
    super.dispose();
  }
}
