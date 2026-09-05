part of '../library_screen.dart';

class SkillsScreen extends StatefulWidget {
  final ConnectionController controller;

  /// Embedded mode renders the body only, for the Commands & tools tabs.
  final bool embedded;
  const SkillsScreen({
    super.key,
    required this.controller,
    this.embedded = false,
  });

  @override
  State<SkillsScreen> createState() => _SkillsScreenState();
}

class _SkillsScreenState extends State<SkillsScreen> {
  List<SkillInfo>? _skills;
  String? _error;
  int _loadGeneration = 0;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final generation = ++_loadGeneration;
    if (_skills == null) setState(() => _error = null);
    try {
      final repository = await widget.controller.prepareActionRepository();
      if (!mounted || generation != _loadGeneration) return;
      if (repository == null) {
        throw const ProductException('OpenCode is reconnecting.');
      }
      final skills = await repository.listSkills();
      if (!mounted || generation != _loadGeneration) return;
      setState(() {
        _skills = skills;
        _error = null;
      });
    } catch (error) {
      if (mounted && generation == _loadGeneration) {
        setState(() => _error = productErrorText(error));
      }
    }
  }

  @override
  Widget build(BuildContext context) => widget.embedded
      ? _body()
      : Scaffold(
          appBar: AppBar(title: const Text('Skills')),
          body: _body(),
        );

  Widget _body() => ProductRefreshBody(
    message: _skills == null ? null : _error,
    onRetry: _load,
    child: _content(),
  );

  Widget _content() => _skills == null && _error == null
      ? const LoadingList()
      : _error != null && _skills == null
      ? ProductErrorState(message: _error!, onRetry: _load)
      : _skills!.isEmpty
      ? RefreshIndicator(
          onRefresh: _load,
          child: const ProductEmptyState(
            icon: Icons.extension_off_outlined,
            title: 'No skills available',
            message: 'Project and global OpenCode skills appear here.',
          ),
        )
      : RefreshIndicator(
          onRefresh: _load,
          child: ListView.separated(
            physics: const AlwaysScrollableScrollPhysics(),
            itemCount: _skills!.length,
            separatorBuilder: (_, _) => const Divider(height: 1),
            itemBuilder: (context, index) {
              final skill = _skills![index];
              return ListTile(
                leading: const Icon(Icons.extension_outlined),
                title: Text(skill.name),
                subtitle: Text(
                  skill.description ?? skill.location,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                ),
                trailing: const Icon(Icons.chevron_right_rounded),
                onTap: () => _showSkill(skill),
              );
            },
          ),
        );

  void _showSkill(SkillInfo skill) {
    showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      builder: (context) => SafeArea(
        child: SizedBox(
          height: MediaQuery.sizeOf(context).height * .82,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 20),
                child: Text(
                  skill.name,
                  style: Theme.of(context).textTheme.titleLarge,
                ),
              ),
              Padding(
                padding: const EdgeInsets.fromLTRB(20, 4, 20, 12),
                child: SelectableText(
                  skill.location,
                  style: TextStyle(
                    color: AppTheme.mutedOf(Theme.of(context)),
                    fontFamily: AppTheme.monoFamily,
                    fontSize: AppTheme.captionFontSize,
                  ),
                ),
              ),
              const Divider(height: 1),
              Expanded(
                child: FilePreviewBody(
                  key: const Key('skill-content-preview'),
                  data: FilePreviewData(
                    name: 'SKILL.md',
                    mimeType: 'text/markdown',
                    text: skill.content,
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
