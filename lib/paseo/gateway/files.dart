part of '../gateway.dart';

/// Biggest file the viewer asks for; the daemon refuses larger ones and a
/// frame over the transport limit would drop the connection.
const _paseoMaxFileBytes = 2 * 1024 * 1024;

/// Files of the open folder: browse, read, find by name
/// (`file_explorer_request`, `directory_suggestions_request`).
mixin _PaseoFilesApi on _PaseoWorkspaceBase {
  Future<List<FileNode>> listFiles(String path) async {
    final payload = await _workspaceRequest('file_explorer_request', {
      'cwd': _scope,
      'path': _relativeToScope(path),
      'mode': 'list',
    });
    final directory = payload['directory'];
    if (directory is! Map<String, dynamic>) {
      throw PaseoFailure(PaseoFailureKind.invalidResponse);
    }
    final nodes = <FileNode>[
      for (final raw in paseoList(directory['entries'], max: 50000))
        _fileNode(paseoObject(raw)),
    ];
    nodes.sort((a, b) {
      if (a.isDir != b.isDir) return a.isDir ? -1 : 1;
      return a.name.toLowerCase().compareTo(b.name.toLowerCase());
    });
    return nodes;
  }

  FileNode _fileNode(Map<String, dynamic> entry) => FileNode(
    name: paseoString(entry['name'], max: 1024),
    path: paseoString(entry['path']),
    isDir: entry['kind'] == 'directory',
  );

  Future<FileContent> fileContent(String path) async {
    final payload = await _workspaceRequest('file_explorer_request', {
      'cwd': _scope,
      'path': _relativeToScope(path),
      'mode': 'file',
      'maxBytes': _paseoMaxFileBytes,
    });
    final file = paseoObject(payload['file']);
    final kind = file['kind'];
    final mime = file['mimeType'];
    final content = file['content'];
    return FileContent(
      content is String ? content : '',
      type: kind == 'binary' ? 'binary' : (kind == 'image' ? 'image' : 'text'),
      encoding: file['encoding'] == 'base64' ? 'base64' : null,
      mimeType: mime is String ? mime : null,
    );
  }

  Future<List<String>> findFile(String query) async {
    final text = query.trim();
    if (text.isEmpty) return const [];
    final payload = await _workspaceRequest('directory_suggestions_request', {
      'query': text,
      'cwd': _scope,
      'includeFiles': true,
      'includeDirectories': false,
      'limit': 100,
    });
    return [
      for (final raw in paseoList(payload['entries'], max: 1000))
        if (raw is Map<String, dynamic> && raw['kind'] == 'file')
          paseoString(raw['path']),
    ];
  }
}
