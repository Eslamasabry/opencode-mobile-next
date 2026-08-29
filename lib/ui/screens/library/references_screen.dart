part of '../library_screen.dart';

class ReferencesScreen extends StatefulWidget {
  final ConnectionController controller;
  final ValueChanged<ReferenceInfo>? onSelected;

  /// Embedded mode renders the body only, for the Commands & tools tabs.
  final bool embedded;

  const ReferencesScreen({
    super.key,
    required this.controller,
    this.onSelected,
    this.embedded = false,
  });

  @override
  State<ReferencesScreen> createState() => _ReferencesScreenState();
}

class _ReferencesScreenState extends State<ReferencesScreen> {
  List<ReferenceInfo>? _references;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() => _error = null);
    try {
      final repository = await widget.controller.prepareActionRepository();
      if (repository == null) {
        throw const ProductException('OpenCode is reconnecting.');
      }
      final references = await repository.listReferences();
      if (mounted) setState(() => _references = references);
    } catch (error) {
      if (mounted) setState(() => _error = productErrorText(error));
    }
  }

  @override
  Widget build(BuildContext context) => widget.embedded
      ? _body()
      : Scaffold(
          appBar: AppBar(title: const Text('References')),
          body: _body(),
        );

  Widget _body() => _references == null && _error == null
      ? const LoadingList()
      : _error != null && _references == null
      ? ProductErrorState(message: _error!, onRetry: _load)
      : _references!.isEmpty
      ? RefreshIndicator(
          onRefresh: _load,
          child: const ProductEmptyState(
            icon: Icons.bookmarks_outlined,
            title: 'No references configured',
            message: 'References attached to this project appear here.',
          ),
        )
      : RefreshIndicator(
          onRefresh: _load,
          child: ListView.separated(
            physics: const AlwaysScrollableScrollPhysics(),
            itemCount: _references!.length,
            separatorBuilder: (_, _) => const Divider(height: 1),
            itemBuilder: (context, index) {
              final reference = _references![index];
              return ListTile(
                key: ValueKey('reference-${reference.name}'),
                leading: const Icon(Icons.bookmark_outline_rounded),
                title: Text(reference.name),
                subtitle: Text(
                  reference.description?.isNotEmpty == true
                      ? '${reference.description}\n${reference.path}'
                      : reference.path,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(fontSize: AppTheme.codeFontSize),
                ),
                trailing: Icon(
                  widget.onSelected == null
                      ? AppIcons.copy
                      : Icons.add_comment_outlined,
                ),
                onTap: () => _useReference(reference),
              );
            },
          ),
        );

  Future<void> _useReference(ReferenceInfo reference) async {
    final onSelected = widget.onSelected;
    if (onSelected != null) {
      onSelected(reference);
      if (mounted) Navigator.of(context).pop();
      return;
    }
    await Clipboard.setData(ClipboardData(text: '@${reference.name}'));
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text('@${reference.name} copied'),
        duration: const Duration(seconds: 2),
      ),
    );
  }
}
