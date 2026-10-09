// The keys OpenCode's own tools send in a tool step's `input` and `metadata`
// (the contracts type both as free objects, so these come from the tool
// definitions), as one group per tool. Shared by oc1_parts and oc2_messages.
const str = (...paths) => Object.fromEntries(paths.map((p) => [p, { kind: 'string' }]));
const toolGroup = (fields) => ({ extra: fields });

export const toolGroupSpecs = [
    { name: 'tool:bash', ...toolGroup({ ...str('input.command', 'input.description', 'input.workdir'), 'input.timeout': { kind: 'number' }, ...str('output', 'metadata.output'), 'metadata.exit': { kind: 'number' } }) },
    { name: 'tool:read', ...toolGroup({ ...str('input.filePath', 'output'), 'input.offset': { kind: 'number' }, 'input.limit': { kind: 'number' }, 'metadata.truncated': { kind: 'boolean' } }) },
    { name: 'tool:edit', ...toolGroup({ ...str('input.filePath', 'input.oldString', 'input.newString', 'metadata.filediff.file', 'metadata.filediff.before', 'metadata.filediff.after', 'metadata.diff'), 'input.replaceAll': { kind: 'boolean' }, 'metadata.filediff.additions': { kind: 'number' }, 'metadata.filediff.deletions': { kind: 'number' }, 'metadata.diagnostics': { kind: 'any' } }) },
    { name: 'tool:write', ...toolGroup({ ...str('input.filePath', 'input.content', 'metadata.filepath'), 'metadata.exists': { kind: 'boolean' } }) },
    { name: 'tool:grep', ...toolGroup({ ...str('input.pattern', 'input.path', 'input.include', 'output'), 'metadata.matches': { kind: 'number' }, 'metadata.truncated': { kind: 'boolean' } }) },
    { name: 'tool:glob', ...toolGroup({ ...str('input.pattern', 'input.path', 'output'), 'metadata.count': { kind: 'number' }, 'metadata.truncated': { kind: 'boolean' } }) },
    { name: 'tool:webfetch', ...toolGroup({ ...str('input.url', 'input.format', 'output'), 'input.timeout': { kind: 'number' } }) },
    { name: 'tool:task', ...toolGroup({ ...str('input.description', 'input.prompt', 'input.subagent_type', 'input.command', 'output', 'metadata.sessionId', 'metadata.model.providerID', 'metadata.model.modelID') }) },
    { name: 'tool:todowrite', ...toolGroup({ ...str('input.todos[].content', 'input.todos[].status', 'input.todos[].priority', 'output') }) }
];
