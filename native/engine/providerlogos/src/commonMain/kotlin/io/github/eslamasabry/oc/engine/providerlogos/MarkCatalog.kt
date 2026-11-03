package io.github.eslamasabry.oc.engine.providerlogos

/*
 * Port of lib/domain/mark_catalog.dart (B-AGENT-207). Which bundled single-colour mark (`assets/marks/<slug>.svg`)
 * names an agent or a model provider. Pure lookup: no I/O, no network. A null answer means "no mark", and the caller
 * shows its generic glyph or initials.
 */

/** Marks that exist in the app bundle (`assets/marks/`). A test keeps this list and the folder in step. */
public val BundledMarkSlugs: Set<String> = setOf(
    "ai21", "ai302", "aihubmix", "alibaba", "amdradeoncloud", "antgroup", "anthropic", "arcee", "azure", "baseten",
    "bedrock", "cerebras", "chutes", "claude", "cline", "cloudflare", "codex", "cohere", "commandcode", "copilot",
    "crusoe", "deepinfra", "deepseek", "digitalocean", "fireworks", "friendli", "gemini", "gmicloud", "google",
    "goose", "grok", "groq", "huggingface", "ibm", "inception", "inceptron", "inference", "ionet", "kilocode", "kimi",
    "lmstudio", "longcat", "meta", "minimax", "mistral", "modelscope", "moonshot", "morph", "nebius", "nova",
    "novita", "nvidia", "ollama", "openai", "opencode", "openrouter", "perplexity", "pi", "poe", "qiniu", "qwen",
    "sakana", "sambanova", "sensenova", "siliconcloud", "snowflake", "stepfun", "submodel", "tencent", "together",
    "upstage", "venice", "vercel", "volcengine", "wandb", "xai", "xiaomimimo", "zai", "zenmux",
)

/** Agent catalog / Paseo ids to a mark. Oh My Pi is a Pi build, so it wears Pi's mark; fx has none. */
private val agentMarks: Map<String, String> = mapOf(
    "opencode" to "opencode",
    "opencode2" to "opencode",
    "claude" to "claude",
    "codex" to "codex",
    "gemini" to "gemini",
    "qwen" to "qwen",
    "kilo" to "kilocode",
    "cline" to "cline",
    "goose" to "goose",
    "pi" to "pi",
    "omp-acp" to "pi",
    "copilot" to "copilot",
)

/**
 * Models.dev provider ids (and close variants) to a mark: every id on models.dev (2026-10-18) whose logo lobe-icons
 * has. Anything else falls back to the models.dev download, then to initials.
 */
private val providerMarks: Map<String, String> = mapOf(
    "302ai" to "ai302",
    "ai21" to "ai21",
    "aihubmix" to "aihubmix",
    "alibaba" to "alibaba",
    "alibaba-cn" to "alibaba",
    "alibaba-coding-plan" to "alibaba",
    "alibaba-coding-plan-cn" to "alibaba",
    "alibaba-token-plan" to "alibaba",
    "alibaba-token-plan-cn" to "alibaba",
    "amazon-bedrock" to "bedrock",
    "amd" to "amdradeoncloud",
    "anthropic" to "anthropic",
    "arcee" to "arcee",
    "azure" to "azure",
    "azure-cognitive-services" to "azure",
    "bailing" to "antgroup",
    "baseten" to "baseten",
    "cerebras" to "cerebras",
    "chutes" to "chutes",
    "cline-pass" to "cline",
    "cloudflare-ai-gateway" to "cloudflare",
    "cloudflare-workers-ai" to "cloudflare",
    "cohere" to "cohere",
    "commandcode" to "commandcode",
    "crusoe" to "crusoe",
    "deepinfra" to "deepinfra",
    "deepseek" to "deepseek",
    "digitalocean" to "digitalocean",
    "fireworks-ai" to "fireworks",
    "friendli" to "friendli",
    "github-copilot" to "copilot",
    "gmicloud" to "gmicloud",
    "google" to "google",
    "google-vertex" to "google",
    "google-vertex-anthropic" to "anthropic",
    "grok" to "grok",
    "groq" to "groq",
    "huggingface" to "huggingface",
    "inception" to "inception",
    "inceptron" to "inceptron",
    "inference" to "inference",
    "io-net" to "ionet",
    "kilo" to "kilocode",
    "kimi-code-plan-cn" to "kimi",
    "kimi-code-plan-global" to "kimi",
    "kimi-for-coding" to "kimi",
    "llama" to "meta",
    "lmstudio" to "lmstudio",
    "longcat" to "longcat",
    "meta" to "meta",
    "minimax" to "minimax",
    "minimax-cn" to "minimax",
    "minimax-cn-coding-plan" to "minimax",
    "minimax-coding-plan" to "minimax",
    "mistral" to "mistral",
    "modelscope" to "modelscope",
    "moonshotai" to "moonshot",
    "moonshotai-cn" to "moonshot",
    "morph" to "morph",
    "nebius" to "nebius",
    "nova" to "nova",
    "novita-ai" to "novita",
    "nvidia" to "nvidia",
    "ollama" to "ollama",
    "ollama-cloud" to "ollama",
    "openai" to "openai",
    "opencode" to "opencode",
    "opencode-go" to "opencode",
    "openrouter" to "openrouter",
    "perplexity" to "perplexity",
    "perplexity-agent" to "perplexity",
    "poe" to "poe",
    "qiniu-ai" to "qiniu",
    "qwen" to "qwen",
    "sakana" to "sakana",
    "sambanova" to "sambanova",
    "sensenova" to "sensenova",
    "siliconflow" to "siliconcloud",
    "siliconflow-cn" to "siliconcloud",
    "snowflake-cortex" to "snowflake",
    "stepfun" to "stepfun",
    "stepfun-ai" to "stepfun",
    "stepfun-ai-step-plan" to "stepfun",
    "stepfun-step-plan" to "stepfun",
    "submodel" to "submodel",
    "tencent-coding-plan" to "tencent",
    "tencent-token-plan" to "tencent",
    "tencent-tokenhub" to "tencent",
    "togetherai" to "together",
    "upstage" to "upstage",
    "venice" to "venice",
    "vercel" to "vercel",
    "volcengine" to "volcengine",
    "volcengine-coding-plan" to "volcengine",
    "wandb" to "wandb",
    "watsonx" to "ibm",
    "xai" to "xai",
    "xiaomi" to "xiaomimimo",
    "xiaomi-token-plan-ams" to "xiaomimimo",
    "xiaomi-token-plan-cn" to "xiaomimimo",
    "xiaomi-token-plan-sgp" to "xiaomimimo",
    "zai" to "zai",
    "zai-coding-plan" to "zai",
    "zenmux" to "zenmux",
    "zhipuai" to "zai",
    "zhipuai-coding-plan" to "zai",
)

/** The mark slug for an agent's icon key (the agent catalog's `iconKey`, which is the agent id), or null. */
public fun agentMarkSlug(iconKey: String): String? = agentMarks[iconKey.lowercase()]

/** The mark slug for a provider id, or null. */
public fun providerMarkSlug(providerId: String): String? = providerMarks[providerId.trim().lowercase()]

/** The bundle path of a mark, exactly as the Flutter app names it. */
public fun markAssetPath(slug: String): String = "assets/marks/$slug.svg"
