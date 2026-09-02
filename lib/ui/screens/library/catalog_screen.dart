part of '../library_screen.dart';

/// "Deprecated" / "Preview" lifecycle pill for a catalog model; null when
/// the model is plainly active.
class ModelStatusPill extends StatelessWidget {
  const ModelStatusPill._(this.label, this.tone, {super.key});

  static ModelStatusPill? forModel(CatalogModel model, {Key? key}) {
    if (model.deprecated) {
      return ModelStatusPill._('Deprecated', AppStatusTone.neutral, key: key);
    }
    if (model.preview) {
      return ModelStatusPill._('Preview', AppStatusTone.attention, key: key);
    }
    return null;
  }

  final String label;
  final AppStatusTone tone;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final color = AppTheme.statusColor(theme, tone);
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 1),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(6),
        border: Border.all(color: color.withValues(alpha: .6)),
      ),
      child: Text(
        label,
        style: theme.textTheme.labelSmall?.copyWith(color: color),
      ),
    );
  }
}

class CatalogScreen extends StatefulWidget {
  final ConnectionController controller;
  const CatalogScreen({super.key, required this.controller});

  @override
  State<CatalogScreen> createState() => _CatalogScreenState();
}

class _CatalogScreenState extends State<CatalogScreen> {
  CatalogSnapshot? _catalog;
  // ignore: unused_field
  String? _error;
  String _query = '';
  int _loadGeneration = 0;

  @override
  void initState() {
    super.initState();
    if (widget.controller.catalog == null) {
      widget.controller.refreshCatalog();
    }
  }

  Future<void> _load() async {
    final generation = ++_loadGeneration;
    setState(() => _error = null);
    try {
      final repository = await widget.controller.prepareActionRepository();
      if (repository == null) {
        throw const ProductException('OpenCode is reconnecting.');
      }
      final value = await repository.loadCatalog();
      if (mounted && generation == _loadGeneration) {
        setState(() => _catalog = value);
      }
    } catch (error) {
      if (mounted && generation == _loadGeneration) {
        setState(() => _error = productErrorText(error));
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Models and agents')),
      body: ModelCatalogView(controller: widget.controller, showHeader: false),
    );
  }

  // ignore: unused_element
  Widget _models() {
    final models = _catalog!.models.where((model) {
      final query = _query.toLowerCase();
      return query.isEmpty ||
          model.name.toLowerCase().contains(query) ||
          model.id.toLowerCase().contains(query) ||
          model.providerID.toLowerCase().contains(query);
    }).toList();
    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.all(12),
          child: TextField(
            decoration: const InputDecoration(
              hintText: 'Search models',
              prefixIcon: Icon(Icons.search_rounded),
              border: OutlineInputBorder(),
              isDense: true,
            ),
            onChanged: (value) => setState(() => _query = value),
          ),
        ),
        Expanded(
          child: models.isEmpty
              ? RefreshIndicator(
                  onRefresh: _load,
                  child: const ProductEmptyState(
                    icon: Icons.search_off_rounded,
                    title: 'No matching models',
                    message: 'Try another provider or model name.',
                  ),
                )
              : RefreshIndicator(
                  onRefresh: _load,
                  child: ListView.separated(
                    physics: const AlwaysScrollableScrollPhysics(),
                    itemCount: models.length,
                    separatorBuilder: (_, _) => const Divider(height: 1),
                    itemBuilder: (context, index) {
                      final model = models[index];
                      final selected =
                          widget.controller.selectedModel?.providerID ==
                              model.providerID &&
                          widget.controller.selectedModel?.modelID == model.id;
                      final pill = ModelStatusPill.forModel(model);
                      final cost = modelCostLabel(model);
                      return ListTile(
                        enabled: model.enabled,
                        title: Row(
                          children: [
                            Flexible(
                              child: Text(
                                model.name,
                                overflow: TextOverflow.ellipsis,
                              ),
                            ),
                            if (pill != null) ...[
                              const SizedBox(width: 8),
                              pill,
                            ],
                          ],
                        ),
                        subtitle: Text(
                          [
                            '${model.providerID}/${model.id}',
                            '${_compactNumber(model.contextLimit)} context - ${_compactNumber(model.outputLimit)} output',
                            ?cost,
                          ].join('\n'),
                          maxLines: 3,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                            fontSize: AppTheme.codeFontSize,
                          ),
                        ),
                        isThreeLine: true,
                        trailing: selected
                            ? Icon(
                                Icons.check_circle_rounded,
                                color: Theme.of(context).colorScheme.primary,
                              )
                            : null,
                        onTap: () => _modelDetails(model),
                      );
                    },
                  ),
                ),
        ),
      ],
    );
  }

  // ignore: unused_element
  Widget _providers() {
    if (_catalog!.providers.isEmpty) {
      return RefreshIndicator(
        onRefresh: _load,
        child: const ProductEmptyState(
          icon: Icons.cloud_off_outlined,
          title: 'No providers connected',
          message: 'Connect a provider on the OpenCode server to use models.',
        ),
      );
    }
    return RefreshIndicator(
      onRefresh: _load,
      child: ListView.separated(
        physics: const AlwaysScrollableScrollPhysics(),
        itemCount: _catalog!.providers.length,
        separatorBuilder: (_, _) => const Divider(height: 1),
        itemBuilder: (context, index) {
          final provider = _catalog!.providers[index];
          final count = _catalog!.models
              .where((model) => model.providerID == provider.id)
              .length;
          return ListTile(
            leading: Icon(
              provider.enabled
                  ? Icons.cloud_done_outlined
                  : Icons.cloud_off_outlined,
            ),
            title: Text(provider.name),
            subtitle: Text(
              '$count available models\nAuthentication is managed under MCP and integrations.',
              maxLines: 2,
            ),
            trailing: const Icon(Icons.chevron_right_rounded),
            onTap: () => Navigator.of(context).push(
              MaterialPageRoute<void>(
                builder: (_) =>
                    IntegrationsScreen(controller: widget.controller),
              ),
            ),
          );
        },
      ),
    );
  }

  // ignore: unused_element
  Widget _agents() {
    final agents = _catalog!.agents.where((agent) => !agent.hidden).toList();
    if (agents.isEmpty) {
      return RefreshIndicator(
        onRefresh: _load,
        child: const ProductEmptyState(
          icon: Icons.support_agent_outlined,
          title: 'No agents available',
          message: 'No visible agents were returned for this workspace.',
        ),
      );
    }
    return RefreshIndicator(
      onRefresh: _load,
      child: ListView.separated(
        physics: const AlwaysScrollableScrollPhysics(),
        itemCount: agents.length,
        separatorBuilder: (_, _) => const Divider(height: 1),
        itemBuilder: (context, index) {
          final agent = agents[index];
          final selected = widget.controller.selectedAgent == agent.id;
          return ListTile(
            leading: const Icon(Icons.support_agent_outlined),
            title: Text(agent.id),
            subtitle: Text(
              [
                agent.mode,
                if (agent.description?.isNotEmpty == true) agent.description!,
              ].join(' - '),
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
            ),
            trailing: selected ? const Icon(Icons.check_rounded) : null,
            onTap: () => widget.controller.selectAgent(agent.id).then((_) {
              if (mounted) setState(() {});
            }),
          );
        },
      ),
    );
  }

  void _modelDetails(CatalogModel model) {
    showModalBottomSheet<void>(
      context: context,
      showDragHandle: true,
      isScrollControlled: true,
      builder: (context) => SafeArea(
        child: SizedBox(
          height: MediaQuery.sizeOf(context).height * .82,
          child: SingleChildScrollView(
            padding: const EdgeInsets.fromLTRB(20, 0, 20, 20),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Flexible(
                      child: Text(
                        model.name,
                        style: Theme.of(context).textTheme.titleLarge,
                      ),
                    ),
                    if (ModelStatusPill.forModel(model) case final pill?) ...[
                      const SizedBox(width: 8),
                      pill,
                    ],
                  ],
                ),
                const SizedBox(height: 4),
                SelectableText(
                  '${model.providerID}/${model.id}',
                  style: const TextStyle(fontFamily: AppTheme.monoFamily),
                ),
                if (modelCostLabel(model) case final cost?) ...[
                  const SizedBox(height: 6),
                  Text(
                    cost,
                    style: Theme.of(context).textTheme.bodySmall?.copyWith(
                      color: AppTheme.mutedOf(Theme.of(context)),
                    ),
                  ),
                ],
                const SizedBox(height: 16),
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: [
                    _CapabilityChip(
                      label: '${_compactNumber(model.contextLimit)} context',
                    ),
                    _CapabilityChip(
                      label: '${_compactNumber(model.outputLimit)} output',
                    ),
                    if (model.reasoning)
                      const _CapabilityChip(label: 'Reasoning'),
                    if (model.attachments)
                      const _CapabilityChip(label: 'Attachments'),
                    if (model.tools) const _CapabilityChip(label: 'Tools'),
                    for (final variant in model.variants)
                      _CapabilityChip(label: variant.id),
                  ],
                ),
                const SizedBox(height: 20),
                SizedBox(
                  width: double.infinity,
                  child: FilledButton(
                    onPressed: model.enabled
                        ? () async {
                            await widget.controller.selectModel(
                              ModelRef(
                                providerID: model.providerID,
                                modelID: model.id,
                              ),
                            );
                            if (context.mounted) Navigator.pop(context);
                            if (mounted) setState(() {});
                          }
                        : null,
                    child: const Text('Use this model'),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  static String _compactNumber(int value) {
    if (value >= 1000000) return '${(value / 1000000).toStringAsFixed(1)}M';
    if (value >= 1000) return '${(value / 1000).toStringAsFixed(0)}k';
    return '$value';
  }
}

class _CapabilityChip extends StatelessWidget {
  final String label;
  const _CapabilityChip({required this.label});

  @override
  Widget build(BuildContext context) =>
      Chip(label: Text(label), visualDensity: VisualDensity.compact);
}
