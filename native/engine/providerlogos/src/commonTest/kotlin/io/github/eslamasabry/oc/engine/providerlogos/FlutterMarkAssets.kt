package io.github.eslamasabry.oc.engine.providerlogos

/**
 * Fixture copied from the Flutter app on 2026-11-03: the `.svg` files in `assets/marks/` (declared in pubspec.yaml,
 * lobe-icons MIT) and the ids of `AgentCatalog.builtIn` in lib/domain/agent_catalog.dart (iconKey = id). Every file
 * holds `currentColor` and no `<image` or `<script` (checked in the Flutter test against the real files).
 */
internal object FlutterMarkAssets {
    val svgFiles: List<String> = listOf(
        "ai21.svg", "ai302.svg", "aihubmix.svg", "alibaba.svg", "amdradeoncloud.svg", "antgroup.svg",
        "anthropic.svg", "arcee.svg", "azure.svg", "baseten.svg", "bedrock.svg", "cerebras.svg", "chutes.svg",
        "claude.svg", "cline.svg", "cloudflare.svg", "codex.svg", "cohere.svg", "commandcode.svg", "copilot.svg",
        "crusoe.svg", "deepinfra.svg", "deepseek.svg", "digitalocean.svg", "fireworks.svg", "friendli.svg",
        "gemini.svg", "gmicloud.svg", "google.svg", "goose.svg", "grok.svg", "groq.svg", "huggingface.svg",
        "ibm.svg", "inception.svg", "inceptron.svg", "inference.svg", "ionet.svg", "kilocode.svg", "kimi.svg",
        "lmstudio.svg", "longcat.svg", "meta.svg", "minimax.svg", "mistral.svg", "modelscope.svg", "moonshot.svg",
        "morph.svg", "nebius.svg", "nova.svg", "novita.svg", "nvidia.svg", "ollama.svg", "openai.svg",
        "opencode.svg", "openrouter.svg", "perplexity.svg", "pi.svg", "poe.svg", "qiniu.svg", "qwen.svg",
        "sakana.svg", "sambanova.svg", "sensenova.svg", "siliconcloud.svg", "snowflake.svg", "stepfun.svg",
        "submodel.svg", "tencent.svg", "together.svg", "upstage.svg", "venice.svg", "vercel.svg",
        "volcengine.svg", "wandb.svg", "xai.svg", "xiaomimimo.svg", "zai.svg", "zenmux.svg",
    )

    val catalogAgentIds: List<String> = listOf(
        "opencode2", "claude", "codex", "gemini", "qwen", "kilo", "cline", "goose", "omp-acp", "fx",
    )
}
