plugins { id("oc.kmp.library") }

// :core:markdown (plan 09 §9.1, S2-MD). Pure Kotlin: the plan's documented fallback parser (a port of Flutter's
// kit_markdown.dart block parser) behind the StreamingMarkdown API. No library beyond the convention defaults
// (coroutines, collections-immutable), so the module compiles while the catalog has no jetbrains-markdown alias.
